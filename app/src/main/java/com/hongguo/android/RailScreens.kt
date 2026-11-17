package com.hongguo.android

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.ceil
import kotlin.math.roundToInt
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest

/* ---------------------------------------------------------------------------------------------
 * F · 集刻度: 中性冷灰底，唯一重音是集刻度。灰 = 未看，红 = 已看，最高的一根 = 当前集。
 * 色值唯一真相源是 AppTheme.kt 的 RailPalette / RailDarkPalette，本文件只做字段名桥接。
 * ------------------------------------------------------------------------------------------- */

@Immutable
internal data class RailColors(
    val surface: Color,
    val card: Color,
    val ink: Color,
    val muted: Color,
    val hair: Color,
    val brand: Color,
)

/** 由 AppTheme 的 RailPalette / RailDarkPalette 桥接：色值只在那两处定义，这里不再复制一份。 */
internal val RailLightColors: RailColors = railColorsFrom(com.hongguo.android.RailPalette)
internal val RailDarkColors: RailColors = railColorsFrom(com.hongguo.android.RailDarkPalette)

private fun railColorsFrom(p: HongguoPalette) = RailColors(
    surface = p.canvas,
    card = p.card,
    ink = p.ink,
    muted = p.muted,
    hair = p.line,
    brand = p.accent,
)

internal val LocalRailColors = staticCompositionLocalOf { RailLightColors }

@Composable
private fun RailTheme(dark: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalRailColors provides if (dark) RailDarkColors else RailLightColors) {
        content()
    }
}

enum class RailRailStyle(
    internal val capacity: Int,
    internal val unreadHeight: Dp,
    internal val watchedHeight: Dp,
    internal val currentHeight: Dp,
) {
    Normal(capacity = 100, unreadHeight = 8.dp, watchedHeight = 13.dp, currentHeight = 18.dp),
    Small(capacity = 60, unreadHeight = 4.dp, watchedHeight = 6.dp, currentHeight = 10.dp),
    Large(capacity = 140, unreadHeight = 10.dp, watchedHeight = 17.dp, currentHeight = 26.dp),
}

/**
 * 一部剧的观看位置尺。超容量时每格代表 ceil(total / shown) 集，红色根数按观看比例抽样；
 * 当前集是最后一个红色刻度。宽度完全等分，不为单根设置最小宽度，避免窄屏溢出。
 */
/**
 * 抽样后该点亮几根刻度。
 *
 * <p>向下取整并至少留 1 根：「看过」不能显示成「一格都没看」；同时避免进位把「差 1 集看完」
 * 画成满格红（1000 集看 999 集时 59/60 才是实况）。
 *
 * <p>抽稀时每格代表 `ceil(total/shown)` 集，视觉比例与真实进度最多差一格。唯一的例外是
 * 「只看了极少数集」——此时点亮 1 根会让视觉比例略高于真实值（1/1000 显示成 1/60），
 * 这是有意的：一根灰都没有会读成「完全没开始」，比轻微的提前更误导。
 */
/** 刻度标尺的 5 个锚点，随总集数等分。total<=0 时只给 "1"。 */
internal fun railScaleLabels(total: Int): List<String> {
    if (total <= 1) return listOf("1")
    val steps = 4
    val labels = mutableListOf<String>()
    labels += "1"
    for (i in 1..steps) {
        val v = (total.toLong() * i / steps).toInt().coerceIn(1, total)
        val text = if (i == steps) total.toString() else v.toString()
        if (labels.last() != text) labels += text
    }
    return labels
}

internal fun railWatchedTicks(total: Int, watched: Int, shown: Int): Int = when {
    shown <= 0 || total <= 0 || watched <= 0 -> 0
    watched >= total -> shown
    else -> ((watched.toDouble() / total) * shown).toInt().coerceIn(1, shown - 1)
}

@Composable
fun EpisodeRail(total: Int, watched: Int, style: RailRailStyle = RailRailStyle.Normal) {
    val colors = LocalRailColors.current
    val safeTotal = total.coerceAtLeast(0)
    val safeWatched = if (safeTotal == 0) 0 else watched.coerceIn(0, safeTotal)
    val shown = minOf(safeTotal, style.capacity)
    val episodesPerTick = if (shown == 0) 0 else ceil(safeTotal.toDouble() / shown).toInt()
    val watchedTicks = railWatchedTicks(safeTotal, safeWatched, shown)
    val currentIndex = if (watchedTicks == 0) -1 else watchedTicks - 1
    val description = when {
        safeTotal == 0 -> "暂无剧集刻度"
        shown < safeTotal -> "共 $safeTotal 集，看到第 $safeWatched 集，每格约 $episodesPerTick 集"
        else -> "共 $safeTotal 集，看到第 $safeWatched 集"
    }
    Canvas(
        Modifier.fillMaxWidth().height(style.currentHeight).semantics { contentDescription = description },
    ) {
        if (shown == 0 || size.width <= 0f) return@Canvas
        val gap = 1.dp.toPx()
        val tickWidth = ((size.width - gap * (shown - 1)) / shown).coerceAtLeast(0f)
        if (tickWidth == 0f) return@Canvas
        repeat(shown) { index ->
            val isWatched = index < watchedTicks
            val tickHeight = when {
                index == currentIndex -> style.currentHeight
                isWatched -> style.watchedHeight
                else -> style.unreadHeight
            }.toPx()
            val left = index * (tickWidth + gap)
            val right = if (index == shown - 1) size.width else left + tickWidth
            drawRect(
                color = if (isWatched) colors.brand else colors.hair,
                topLeft = Offset(left, size.height - tickHeight),
                size = Size((right - left).coerceAtLeast(0f), tickHeight),
            )
        }
    }
}

/**
 * 刻度要的两个数：总集数与已看集数。
 *
 * <p>[watched] 来自 {@link LibraryEntry.episodeIndex}（收藏/历史里按 card.id 查），没有记录就是 0 ——
 * 也就是「一集都没看」，刻度全灰。这正是这套主题的语义，不需要额外状态。
 */
private data class RailProgress(val total: Int, val watched: Int) {
    val remaining: Int get() = (total - watched).coerceAtLeast(0)
    val nextEpisode: Int get() = (watched + 1).coerceIn(1, total.coerceAtLeast(1))
}

/** 在收藏与历史里找这部剧的进度；两处都没有返回 watched = 0。 */
private fun railProgressOf(card: DramaCard, state: HongguoUiState): RailProgress {
    val entry = state.favorites.firstOrNull { it.card.id == card.id }
        ?: state.history.firstOrNull { it.card.id == card.id }
    return RailProgress(total = card.episodeCount, watched = entry?.episodeIndex ?: 0)
}

/** 从详情算进度：总集数用实际返回的集数（比列表页的 episodeCount 更可信）。 */
private fun railProgressOfDetail(card: DramaCard, detail: DramaDetail?, state: HongguoUiState): RailProgress {
    val entry = state.favorites.firstOrNull { it.card.id == card.id }
        ?: state.history.firstOrNull { it.card.id == card.id }
    val total = detail?.episodes?.size?.takeIf { it > 0 } ?: card.episodeCount
    return RailProgress(total = total, watched = entry?.episodeIndex ?: 0)
}

private fun railDuration(episodes: Int): String {
    val minutes = episodes.coerceAtLeast(0) * 2
    val hours = minutes / 60
    val rest = minutes % 60
    return when {
        hours > 0 && rest > 0 -> "约 $hours 小时 $rest 分"
        hours > 0 -> "约 $hours 小时"
        else -> "约 $rest 分钟"
    }
}

private val RailPosterData: Map<String, String> = mapOf(
    "zhuangqiong" to
        "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"
        + "c0Se4luN46Ly9QAA",
    "lingmeng" to
        "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",
    "heli" to
        "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"
        + "UBAAAAA=",
    "duxin" to
        "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"
        + "VlIZaTLkvYeRwphDsZNwxfQqtmPl4jwAAAAAAA==",
    "moshi-big" to
        "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",
    "qinyisheng" to
        "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",
    "zhuangfeng" to
        "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"
        + "jry4EKdU07ZHI8JeAlueA+8N26hEYAA=",
)

private object RailPosterDecoder {
    private val cache = mutableMapOf<String, Bitmap?>()

    @Synchronized
    fun bitmap(key: String): Bitmap? = cache.getOrPut(key) {
        val encoded = RailPosterData[key] ?: return@getOrPut null
        val bytes = Base64.decode(encoded, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }
}

@Composable
private fun RailPoster(
    card: DramaCard,
    modifier: Modifier,
    corner: Dp = 10.dp,
) {
    // 封面走 Coil（与其它五套主题同一条链路），不再内嵌 base64。
    Cover(card.cover, card.title, modifier.clip(RoundedCornerShape(corner)))
}

@Composable
private fun RailTopBar(title: String, trailing: String) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, color = LocalRailColors.current.ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Text(trailing, color = LocalRailColors.current.muted, fontSize = 12.sp)
    }
}

@Composable
private fun RailSummaryLine(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 16.dp, bottom = 2.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = LocalRailColors.current.muted, fontSize = 13.sp)
        Text(value, color = LocalRailColors.current.ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun RailLoadingLine() {
    val colors = LocalRailColors.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CircularProgressIndicator(Modifier.size(14.dp), color = colors.brand, strokeWidth = 1.5.dp)
        Text("正在取回数据", color = colors.muted, fontSize = 13.sp)
    }
}

@Composable
private fun RailErrorLine(message: String, onRetry: () -> Unit) {
    val colors = LocalRailColors.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(message, Modifier.weight(1f), color = colors.muted, fontSize = 13.sp, lineHeight = 19.sp)
        Text("重试", Modifier.clickable(onClick = onRetry).testTag("rail_retry"),
            color = colors.brand, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun RailEmptyLine(title: String, hint: String) {
    val colors = LocalRailColors.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 28.dp)) {
        Text(title, color = colors.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        if (hint.isNotBlank()) {
            Text(hint, Modifier.padding(top = 6.dp), color = colors.muted, fontSize = 13.sp, lineHeight = 19.sp)
        }
    }
}

@Composable
private fun RailSectionTitle(title: String, trailing: String, count: String? = null) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 10.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, color = LocalRailColors.current.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            count?.let { Text(it, color = LocalRailColors.current.muted, fontSize = 12.sp) }
        }
        Text(trailing, color = LocalRailColors.current.muted, fontSize = 12.sp)
    }
}

@Composable
private fun RailPrimaryButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = LocalRailColors.current.brand,
            contentColor = Color.White,
        ),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(4.dp))
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun ContinueWatchingCard(card: DramaCard, progress: RailProgress, position: String, onPlay: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("rail_continue"),
        shape = RoundedCornerShape(14.dp),
        color = LocalRailColors.current.card,
        shadowElevation = 1.dp,
    ) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            RailPoster(card, Modifier.width(96.dp).height(162.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    card.title,
                    color = LocalRailColors.current.ink,
                    fontSize = 17.sp,
                    lineHeight = 23.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (progress.watched > 0) "第 ${progress.watched} 集 · $position / 全 ${progress.total} 集"
                    else "全 ${progress.total} 集 · $position",
                    Modifier.padding(top = 6.dp),
                    color = LocalRailColors.current.muted,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Box(Modifier.padding(top = 14.dp)) { EpisodeRail(progress.total, progress.watched) }
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        if (progress.watched > 0) "看到第 ${progress.watched} 集" else "还没开始",
                        color = LocalRailColors.current.muted, fontSize = 12.sp,
                    )
                    Text("还剩 ${progress.remaining} 集", color = LocalRailColors.current.muted, fontSize = 12.sp)
                }
                RailPrimaryButton(
                    if (progress.watched > 0) "继续看 · 第 ${progress.nextEpisode} 集" else "从第 1 集开始",
                    Modifier.fillMaxWidth().padding(top = 12.dp),
                ) { onPlay() }
            }
        }
    }
}

@Composable
private fun UpdateRow(
    card: DramaCard,
    progress: RailProgress,
    updatedAt: String?,
    showDivider: Boolean,
    onOpen: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).clickable(onClick = onOpen)
            .testTag("rail_update_${card.id}"),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.width(44.dp)) {
                Text(updatedAt ?: "更新", color = LocalRailColors.current.ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text("待播", color = LocalRailColors.current.muted, fontSize = 10.sp)
            }
            RailPoster(card, Modifier.width(40.dp).height(54.dp), corner = 6.dp)
            Column(Modifier.weight(1f)) {
                Text(
                    card.title,
                    color = LocalRailColors.current.ink,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (progress.watched > 0) "看到第 ${progress.watched} 集 · ${progress.total} 集" else "${progress.total} 集",
                    Modifier.padding(top = 3.dp, bottom = 5.dp),
                    color = LocalRailColors.current.muted,
                    fontSize = 12.sp,
                )
                EpisodeRail(progress.total, progress.watched, RailRailStyle.Small)
            }
        }
        if (showDivider) Box(Modifier.fillMaxWidth().height(1.dp).background(LocalRailColors.current.hair))
    }
}

@Composable
private fun RailFeaturedItem(card: DramaCard, modifier: Modifier = Modifier, onOpen: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onOpen)) {
        RailPoster(card, Modifier.fillMaxWidth().aspectRatio(3f / 4.2f))
        Text(
            card.title,
            Modifier.padding(top = 7.dp),
            color = LocalRailColors.current.ink,
            fontSize = 13.5.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text("${card.episodeCount} 集", Modifier.padding(top = 4.dp), color = LocalRailColors.current.muted, fontSize = 12.sp)
    }
}

@Composable
fun RailHome(state: HongguoUiState, model: HongguoViewModel) {
    val colors = LocalRailColors.current
    val items = state.home.items
    val featured = items.firstOrNull()
    val updates = if (items.size > 1) items.drop(1).take(3) else emptyList()
    // 精选用同池的另一段，避免和上面两条重复；首页只有一页数据时退回全部。
    val picks = items.drop(4).take(3).ifEmpty { items.take(3) }
    val list = rememberLazyListState()
    LoadAheadEffect(state.home, { list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 },
        { list.layoutInfo.totalItemsCount }, onLoad = model::loadMoreHome)

    LazyColumn(
        Modifier.fillMaxSize().background(colors.surface).testTag("rail_home"),
        state = list,
        contentPadding = PaddingValues(bottom = 24.dp).plusNavClearance(),
    ) {
        item { RailTopBar("红果", "今晚") }

        when {
            featured == null && state.home.loading -> item { RailLoadingLine() }
            featured == null && state.home.error != null -> item {
                RailErrorLine(state.home.error!!, onRetry = model::loadMoreHome)
            }
            featured == null -> item { RailEmptyLine("今晚还没有可看的剧", "换个频道看看") }
            else -> {
                val featuredProgress = railProgressOf(featured, state)
                item {
                    RailSummaryLine(
                        if (featuredProgress.watched > 0) "今晚还能看" else "从这一部开始",
                        railDuration(featuredProgress.remaining),
                    )
                }
                item {
                    Box(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp)) {
                        ContinueWatchingCard(featured, featuredProgress, featured.summary.take(0).ifEmpty { "每集约 2 分钟" }) {
                            model.openDrama(featured)
                        }
                    }
                }
            }
        }

        if (updates.isNotEmpty()) {
            item {
                Column(Modifier.padding(horizontal = 20.dp).padding(top = 24.dp)) {
                    RailSectionTitle("今晚更新", "全部 ›", "${updates.size} 部")
                    updates.forEachIndexed { index, card ->
                        UpdateRow(
                            card = card,
                            progress = railProgressOf(card, state),
                            updatedAt = null,
                            showDivider = index < updates.lastIndex,
                            onOpen = { model.openDrama(card) },
                        )
                    }
                }
            }
        }

        if (picks.isNotEmpty()) {
            item {
                Column(Modifier.padding(horizontal = 20.dp).padding(top = 24.dp)) {
                    RailSectionTitle("热度精选", "换一批 ↻")
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        picks.forEach { card ->
                            RailFeaturedItem(card, Modifier.weight(1f)) { model.openDrama(card) }
                        }
                    }
                }
            }
        }

        if (state.home.loadingMore) item { RailLoadingLine() }
    }
}

@Composable
private fun RailEpisodeCell(episode: Int, watched: Int, onClick: () -> Unit) {
    val colors = LocalRailColors.current
    val current = episode == watched
    val seen = episode < watched
    Box(
        // fillMaxWidth 不能省:外层是 Box(weight(1f)) 的 wrapContent,只给 height 会让格子缩成文字宽度
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(8.dp))
            .background(if (current) colors.brand else colors.card)
            .border(1.dp, if (current) colors.brand else colors.hair, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick).testTag("rail_episode_$episode"),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "$episode",
            color = if (current) Color.White else if (seen) colors.muted else colors.ink,
            fontSize = 12.5.sp,
            fontWeight = if (current) FontWeight.SemiBold else FontWeight.Medium,
        )
    }
}

@Composable
fun RailDetail(state: HongguoUiState, model: HongguoViewModel, accountModel: AccountViewModel) {
    val card = state.selectedCard ?: return
    val colors = LocalRailColors.current
    val detail = state.detail
    val progress = railProgressOfDetail(card, detail, state)
    val episodes = detail?.episodes.orEmpty()

    Column(Modifier.fillMaxSize().background(colors.surface).testTag("rail_detail")) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(start = 6.dp, end = 12.dp, top = 4.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = model::closeDetail, modifier = Modifier.testTag("rail_detail_back")) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回", tint = colors.ink)
            }
            Text(
                card.title,
                Modifier.weight(1f),
                color = colors.muted,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 28.dp).plusNavClearance(),
        ) {
            item {
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    RailPoster(card, Modifier.width(104.dp).height(147.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            card.title,
                            color = colors.ink,
                            fontSize = 21.sp,
                            lineHeight = 27.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            buildString {
                                append("全 ${progress.total} 集")
                                if (progress.watched > 0) append(" · 看到第 ${progress.watched} 集")
                                append(" · 每集约 2 分钟")
                            },
                            Modifier.padding(top = 7.dp),
                            color = colors.muted,
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                        )
                        if (episodes.isNotEmpty()) {
                            Surface(
                                Modifier.padding(top = 8.dp),
                                shape = RoundedCornerShape(4.dp),
                                color = Color.Transparent,
                                border = BorderStroke(1.dp, colors.brand),
                            ) {
                                Text(
                                    "已更新 ${episodes.size} 集",
                                    Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                    color = colors.brand,
                                    fontSize = 11.sp,
                                )
                            }
                        }
                    }
                }
            }

            item {
                Column(Modifier.padding(top = 20.dp)) {
                    Text(
                        buildString {
                            if (progress.watched > 0) append("看到第 ${progress.watched} 集")
                            else append("还没开始")
                            append(" · 还剩 ${progress.remaining} 集，${railDuration(progress.remaining)}")
                        },
                        color = colors.muted,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Box(Modifier.padding(top = 8.dp)) {
                        EpisodeRail(progress.total, progress.watched, RailRailStyle.Large)
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        railScaleLabels(progress.total).forEach {
                            Text(it, color = colors.muted, fontSize = 12.sp)
                        }
                    }
                }
            }

            item {
                Column(Modifier.padding(top = 18.dp)) {
                    RailSectionTitle("选集", if (episodes.isNotEmpty()) "已更新 ${episodes.size} 集" else "")
                    when {
                        state.loadingDetail && episodes.isEmpty() -> RailLoadingLine()
                        state.detailError != null && episodes.isEmpty() ->
                            RailErrorLine(state.detailError, onRetry = { model.retryDetails() })
                        episodes.isEmpty() -> RailEmptyLine("暂无可用集数", "稍后再试")
                        else -> {
                            // 真实集数决定网格：每行 6 格，行数 = ceil(total/6)。
                            val perRow = 6
                            val rows = (episodes.size + perRow - 1) / perRow
                            repeat(rows) { row ->
                                Row(
                                    Modifier.fillMaxWidth().padding(bottom = if (row == rows - 1) 0.dp else 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    repeat(perRow) { column ->
                                        val index = row * perRow + column
                                        if (index < episodes.size) {
                                            val episode = episodes[index]
                                            Box(Modifier.weight(1f)) {
                                                RailEpisodeCell(episode.index, progress.watched) {
                                                    model.playEpisode(episode.index)
                                                }
                                            }
                                        } else {
                                            Spacer(Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (card.summary.isNotBlank()) {
                item {
                    Column(Modifier.padding(top = 20.dp)) {
                        Text("简介", color = colors.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text(
                            card.summary,
                            Modifier.padding(top = 6.dp),
                            color = colors.muted,
                            fontSize = 15.sp,
                            lineHeight = 23.sp,
                        )
                    }
                }
            }
        }

        RailPrimaryButton(
            buildString {
                if (progress.watched > 0) append("播放第 ${progress.nextEpisode} 集")
                else append("从第 1 集开始")
            },
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp).navigationBarsPadding(),
        ) { model.playEpisode(progress.nextEpisode) }
    }
}

@Composable
fun RailLibrary(state: HongguoUiState, model: HongguoViewModel, accountModel: AccountViewModel) {
    RailTheme(resolveDark(state.themeMode, AppThemeStyle.RAIL, isSystemInDarkTheme())) {
        val colors = LocalRailColors.current
        val history = state.tab == AppTab.HISTORY
        val entries = if (history) state.history else state.favorites
        val totalEpisodes = entries.sumOf { it.card.episodeCount }

        LazyColumn(
            Modifier.fillMaxSize().background(colors.surface).testTag("rail_library"),
            contentPadding = PaddingValues(bottom = 24.dp).plusNavClearance(),
        ) {
            item { RailTopBar(if (history) "历史" else "追更", "${entries.size} 部") }
            item {
                RailSummaryLine(
                    if (entries.isEmpty()) "还没有记录"
                    else "共 ${entries.size} 部 · ${totalEpisodes} 集",
                    if (entries.isEmpty()) "" else "每集约 2 分钟",
                )
            }
            if (entries.isEmpty()) {
                item {
                    RailEmptyLine(
                        if (history) "还没有播放记录" else "还没有追更的剧",
                        if (history) "看过的剧会自动出现在这里" else "在首页点开一部剧，收藏后会出现在这里",
                    )
                }
            } else {
                itemsIndexed(entries, key = { _, entry -> entry.card.id }) { index, entry ->
                    RailLibraryRow(entry, index < entries.lastIndex) { model.openLibrary(entry) }
                }
            }
        }
    }
}

@Composable
fun RailMine(
    state: HongguoUiState,
    model: HongguoViewModel,
    updateState: UpdateUiState,
    updateModel: UpdateViewModel,
    accountName: String? = null,
    accountStatus: String = "登录后查看手机片单",
    accountAvatar: String? = null,
    onAccount: () -> Unit = { model.selectTab(AppTab.ACCOUNT) },
    openSettings: () -> Unit = { model.selectTab(AppTab.SETTINGS) },
) {
    RailTheme(resolveDark(state.themeMode, AppThemeStyle.RAIL, isSystemInDarkTheme())) {
        val colors = LocalRailColors.current
        LazyColumn(
            Modifier.fillMaxSize().background(colors.surface).testTag("rail_mine"),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp).plusNavClearance(),
        ) {
            item { RailTopBar("我的", accountName ?: "本机用户") }
            item {
                Surface(
                    Modifier.fillMaxWidth().padding(top = 16.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = colors.card,
                ) {
                    Row(
                        Modifier.padding(16.dp).clickable(onClick = onAccount),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(56.dp).clip(CircleShape).background(colors.hair),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (accountAvatar.isNullOrBlank()) {
                                Icon(Icons.Outlined.PersonOutline, accountName ?: "本机用户",
                                    tint = colors.muted, modifier = Modifier.size(30.dp))
                            } else {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current).data(accountAvatar).crossfade(true).build(),
                                    contentDescription = accountName,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop,
                                )
                            }
                        }
                        Column(Modifier.padding(start = 14.dp).weight(1f)) {
                            Text(accountName ?: "本机用户", color = colors.ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                            Text(accountStatus, Modifier.padding(top = 4.dp), color = colors.muted, fontSize = 12.sp)
                        }
                        Icon(Icons.Outlined.ChevronRight, "账号", tint = colors.muted, modifier = Modifier.size(18.dp))
                    }
                }
            }
            item {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RailMineStat("${state.favorites.size}", "追更") { model.selectTab(AppTab.FAVORITES) }
                    Box(Modifier.width(1.dp).height(30.dp).background(colors.hair))
                    RailMineStat("${state.history.size}", "历史") { model.selectTab(AppTab.PLAY_HISTORY) }
                }
            }
            item {
                Column(Modifier.fillMaxWidth()) {
                    RailMenuRow(Icons.Outlined.BookmarkBorder, "我的追更", "收藏的剧与进度", "my_favorites") {
                        model.selectTab(AppTab.FAVORITES)
                    }
                    RailMenuRow(Icons.Outlined.History, "播放历史", "本机的观看记录", "my_history") {
                        model.selectTab(AppTab.PLAY_HISTORY)
                    }
                    RailMenuRow(Icons.Outlined.Settings, "设置", "主题与播放偏好", "my_settings", openSettings)
                }
            }
        }
    }
}

@Composable
private fun RailMineStat(value: String, label: String, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, color = LocalRailColors.current.ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Text(label, Modifier.padding(top = 3.dp), color = LocalRailColors.current.muted, fontSize = 12.sp)
    }
}

@Composable
private fun RailMenuRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    tag: String,
    onClick: () -> Unit,
) {
    val colors = LocalRailColors.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick)
            .testTag(tag).padding(horizontal = 4.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, title, tint = colors.muted, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = colors.ink, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, Modifier.padding(top = 2.dp), color = colors.muted, fontSize = 12.sp)
        }
        Icon(Icons.Outlined.ChevronRight, null, tint = colors.muted, modifier = Modifier.size(16.dp))
    }
}
