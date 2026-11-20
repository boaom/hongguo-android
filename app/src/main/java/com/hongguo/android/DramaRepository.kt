package com.hongguo.android

import com.hongguo.account.protocol.RankBoard
import com.hongguo.account.protocol.RankPage
import com.hongguo.account.protocol.RelatedWork
import com.hongguo.account.protocol.WebsiteCatalogClient
import com.hongguo.account.protocol.RelatedWorksClient
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import com.hongguo.api.service.Client
import com.hongguo.api.service.Stream
import com.hongguo.api.service.ProgressiveMedia
import com.hongguo.api.util.Json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.CancellationException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

data class DramaCard(
    val id: String,
    val title: String,
    val cover: String,
    val episodeCount: Int,
    val summary: String,
)

data class Episode(val id: String, val index: Int, val title: String)

data class DramaDetail(
    val id: String,
    val title: String,
    val cover: String,
    val summary: String,
    val episodes: List<Episode>,
)

data class DramaPage(val items: List<DramaCard>, val hasMore: Boolean, val nextOffset: Int)
data class PreparedVideo(
    val episodeId: String,
    val path: String,
    val requestId: Long = 0,
    val session: ProgressiveMedia.Session? = null,
)

/** UI uses the on-device Java core directly; no localhost service is involved. */
class DramaRepository {
    suspend fun discover(feed: HomeFeed, offset: Int, seenIds: List<String>): DramaPage =
        runInterruptible(Dispatchers.IO) {
            val start = offset.coerceAtLeast(0)
            val seen = DiscoveryMapper.seenTail(seenIds)
            when (feed) {
                HomeFeed.RECOMMEND -> {
                    val page = Client.browsePage("short_play", mapOf("sort" to "hot_score", "limit" to "18"), seen, start)
                    DiscoveryMapper.page(page.items, page.hasMore, page.nextSkip, start, seen)
                }
                HomeFeed.GUESS -> throw IllegalStateException("猜你喜欢由账号推荐流分页加载")
                HomeFeed.LATEST -> {
                    val page = Client.latestPage("short_play", false, DiscoveryMapper.PAGE_SIZE, start == 0, seen, start)
                    DiscoveryMapper.page(page.items, page.hasMore, page.nextSkip, start, seen)
                }
                HomeFeed.COMIC -> {
                    val page = Client.rankPage("recommend", DiscoveryMapper.PAGE_SIZE, start)
                    DiscoveryMapper.page(page.items, page.hasMore, page.nextOffset, start, seen)
                }
            }
        }

    suspend fun filters(genre: String): List<FilterGroup> = runInterruptible(Dispatchers.IO) {
        DiscoveryMapper.filterGroups(Client.filters(genre))
    }

    suspend fun browse(selection: BrowseSelection, offset: Int, seenIds: List<String>): DramaPage =
        runInterruptible(Dispatchers.IO) {
            val start = offset.coerceAtLeast(0)
            val seen = DiscoveryMapper.seenTail(seenIds)
            val page = Client.browsePage(selection.genre, DiscoveryMapper.options(selection), seen, start)
            DiscoveryMapper.page(page.items, page.hasMore, page.nextSkip, start, seen)
        }

    suspend fun search(query: String, offset: Int): DramaPage = runInterruptible(Dispatchers.IO) {
        val page = Client.searchPage(query, 20, offset)
        val cards = page.items.mapNotNull { raw ->
            val item = Json.optObj(raw) ?: return@mapNotNull null
            val id = Json.optStr(item["series_id"], "")
            if (id.isBlank()) return@mapNotNull null
            DramaCard(
                id = id,
                title = Json.optStr(item["title"], "未命名短剧"),
                cover = Json.optStr(item["cover"], ""),
                episodeCount = Json.optInt(item["episode_cnt"], 0),
                summary = Json.optStr(item["abstract"], ""),
            )
        }
        DramaPage(cards, page.hasMore, page.nextSkip)
    }

    private val publicHttp by lazy { OkHttpClient.Builder().callTimeout(25, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false).followRedirects(false).followSslRedirects(false).build() }
    private val relatedClient by lazy { RelatedWorksClient(publicHttp) }
    private val websiteClient by lazy { WebsiteCatalogClient(publicHttp) }

    suspend fun rank(board: RankBoard, page: Int): RankPage = runInterruptible(Dispatchers.IO) { websiteClient.rank(board, page) }

    suspend fun suggestions(query: String): List<String> = runInterruptible(Dispatchers.IO) { websiteClient.suggestions(query) }

    suspend fun related(id: String): List<RelatedWork> = runInterruptible(Dispatchers.IO) { relatedClient.related(id) }

    /** 待上线作品的预计上线文案；查不到时为空。 */
    suspend fun upcomingRelease(work: RelatedWork): String = runInterruptible(Dispatchers.IO) {
        val release = Client.upcomingRelease(work.series.id, work.series.title) ?: return@runInterruptible ""
        releaseLabel(Json.optStr(release["text"], ""), Json.optLong(release["publish_at"], 0))
    }

    suspend fun details(card: DramaCard): DramaDetail = runInterruptible(Dispatchers.IO) {
        val result = Client.getEpisodes(card.id)
        val meta = Json.optObj(result["meta"]) ?: emptyMap<String, Any>()
        val episodes = Json.optArr(result["episodes"]).mapNotNull { raw ->
            val item = Json.optObj(raw) ?: return@mapNotNull null
            val id = Json.optStr(item["vid"], "")
            if (id.isBlank()) return@mapNotNull null
            Episode(id, Json.optInt(item["index"], 0), Json.optStr(item["title"], ""))
        }.sortedBy { it.index }
        if (episodes.isEmpty()) throw IOException("EMPTY_EPISODES")
        DramaDetail(
            id = card.id,
            title = Json.optStr(meta["title"], card.title),
            cover = Json.optStr(meta["cover"], card.cover),
            summary = Json.optStr(meta["abstract"], card.summary),
            episodes = episodes,
        )
    }

    suspend fun prepare(episode: Episode, allowFallback: Boolean = true,
                        progress: (String, Long, Long) -> Unit): PreparedVideo {
        val context = currentCoroutineContext()
        var allocated: ProgressiveMedia.Session? = null
        var handedOff = false
        try {
            val result = runInterruptible(Dispatchers.IO) {
                Stream.cachedPath(episode.id, "")?.let { return@runInterruptible PreparedVideo(episode.id, it) }
                var opening = true
                val listener = object : Stream.Progress {
                    override fun update(stage: String, done: Long, total: Long) { progress(stage, done, total) }
                    override fun isCancelled(): Boolean = Thread.currentThread().isInterrupted || (opening && !context.isActive)
                }
                try {
                    allocated = ProgressiveMedia.open(episode.id, "", listener)
                    opening = false
                    PreparedVideo(episode.id, "", session = allocated)
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (unsupported: ProgressiveMedia.UnsupportedException) {
                    if (!allowFallback) throw unsupported
                    progress("compatibility", 0, -1)
                    val fallback = object : Stream.Progress {
                        override fun update(stage: String, done: Long, total: Long) { if (context.isActive) progress(stage, done, total) }
                        override fun isCancelled(): Boolean = !context.isActive || Thread.currentThread().isInterrupted
                    }
                    // Resolution already succeeded before the transport was rejected, so reuse it instead of
                    // resolving again (identities, routes, signatures) on the slowest path. The Java fields are
                    // platform types, so bind them to non-null locals before passing them on.
                    val carried = unsupported.carriedTrack()
                    val path = if (carried == null) {
                        Stream.prepare(episode.id, "", fallback)
                    } else {
                        val url: String = carried.url
                        val spadeA: String = carried.spadeA
                        val definition: String = carried.definition
                        val encrypt: Boolean = carried.encrypt
                        val size: Long = carried.size
                        Stream.prepareWithTrack(episode.id, "", url, spadeA, encrypt, definition, size, fallback)
                    }
                    PreparedVideo(episode.id, path)
                }
            }
            handedOff = true
            return result
        } finally {
            if (!handedOff) allocated?.close()
        }
    }

    /** Warm only a bounded prefix; no player/decoder and no whole-episode speculative download. */
    suspend fun warmNext(video: PreparedVideo, onReader: (ProgressiveMedia.Reader?) -> Unit = {}) {
        val session = video.session ?: return
        runInterruptible(Dispatchers.IO) {
            session.newReader().use { reader ->
                onReader(reader)
                try {
                    val buffer = ByteArray(64 * 1024)
                    val limit = minOf(session.length(), 512L * 1024)
                    var position = 0L
                    while (position < limit && !Thread.currentThread().isInterrupted) {
                        val count = reader.read(position, buffer, 0, minOf(buffer.size.toLong(), limit - position).toInt())
                        if (count <= 0) break
                        position += count
                    }
                } finally { onReader(null) }
            }
        }
    }
}

/**
 * 播放准备失败是否值得自动重试（纯函数，供 HongguoViewModel 的自动重试与单测使用）。
 *
 * 明确拒绝不值得重试：AppSource 已经把「业务/HTTP 拒绝即停」实现好了，外层再试只会
 * 拖慢错误展示并加重上游风控。命中 [AppSource.PlaybackRejectedException]，或
 * [AppSource.PlaybackHttpException] 状态 403/429（沿 cause 链判断）时返回 false；
 * 其余（空响应、SocketTimeout、注册失败等偶发抖动）返回 true，保持「网络抖动，自动重试」。
 */
fun retryablePlaybackFailure(error: Throwable): Boolean {
    var current: Throwable? = error
    var hops = 0
    while (current != null && hops < 8) {
        if (current is com.hongguo.api.service.AppSource.PlaybackRejectedException) return false
        if (current is com.hongguo.api.service.AppSource.PlaybackBackoffException) return false
        if (current is com.hongguo.api.service.AppSource.PlaybackHttpException &&
            (current.status == 403 || current.status == 429)) return false
        current = current.cause
        hops++
    }
    return true
}

/** Never expose provider response bodies, signed URLs or device identifiers in UI. */
fun readableFailure(error: Throwable, action: String): String {
    val chain = generateSequence(error) { it.cause }.take(8).toList()
    val hint = chain.joinToString(" ") { it.message.orEmpty() }.lowercase()
    chain.firstNotNullOfOrNull { it as? NoNewRecommendationsException }?.let {
        return "推荐接口暂未返回新作品（最近已去重 ${it.remembered} 部），可稍后刷新，或切换其他分类继续发现。"
    }
    chain.firstNotNullOfOrNull { it as? com.hongguo.api.service.AppSource.PlaybackRejectedException }?.let {
        return it.message ?: "播放服务拒绝请求，请稍后重试。"
    }
    chain.firstNotNullOfOrNull { it as? com.hongguo.api.service.AppSource.PlaybackBackoffException }?.let {
        val seconds = it.retryAfter.toIntOrNull()
        return if (seconds != null && seconds > 0)
            "播放服务繁忙，请约 $seconds 秒后再试。"
        else "播放服务繁忙，请稍后再试。"
    }
    if (chain.any { it is com.hongguo.api.service.AppSource.EmptyPlaybackResponseException }) {
        return "播放服务连续返回空响应，请稍后重试或选择其他集。"
    }
    return when {
        chain.any { it is UnknownHostException } -> "网络连接不可用，请检查网络后重试。"
        chain.any { it is SocketTimeoutException } -> "连接超时，请稍后重试。"
        chain.any { it is SSLException } -> "安全连接失败，请检查网络和设备时间。"
        hint.contains("no space") || hint.contains("enospc") -> "设备存储空间不足，请清理空间后重试。"
        hint.contains("上游未返回数据") -> "内容服务暂未返回数据，请稍后重试。"
        hint.contains("empty_episodes") -> "暂时没有获取到可播放剧集，请稍后重试。"
        hint.contains("风控") || hint.contains("risk") || hint.contains("429") -> "片源服务暂时限制访问，请稍后重试。"
        hint.contains("bytevc2") -> "该集目前只有本机无法解码的 ByteVC2 片源，重试也无法播放，请选择其他集或稍后再试。"
        hint.contains("无直链") || hint.contains("video_model") || hint.contains("片源") -> "该集片源暂时不可用，可重试或选择其他集。"
        hint.contains("解密") || hint.contains("cenc") || hint.contains("decrypt") ||
            hint.contains("封装") || hint.contains("媒体校验") || hint.contains("密钥格式") ||
            hint.contains("媒体索引") || hint.contains("媒体样本") -> "该集媒体格式暂不支持，可重试或选择其他集。"
        hint.contains("register") -> "播放设备注册失败（上游拒绝或网络受限），可稍后重试，或换网络（如关闭 VPN）再试。"
        // 兜底透出最内层原因，频繁失败时便于定位（消息不含敏感信息）。
        else -> {
            val cause = generateSequence(error) { it.cause }.lastOrNull()
                ?.message.orEmpty().take(48).ifBlank { "未知原因" }
            "${action}失败（$cause），请检查网络后重试。"
        }
    }
}
