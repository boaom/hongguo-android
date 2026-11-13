package com.hongguo.android

import android.content.Context
import com.hongguo.api.util.Json

/**
 * 上次成功拉到的首页内容，按频道落盘。冷启动先用它渲染（stale-while-revalidate）：
 * requestHome 把缓存种进 FeedState，各屏因此直接显示上次的内容而不出转圈，
 * 网络回来后 mergePage 用新数据整页替换。首次安装无缓存时行为与从前一致。
 */
internal object HomeCache {
    private const val FILE = "home_cache"
    private const val MAX_ITEMS = 24

    private val memory = LinkedHashMap<String, List<DramaCard>>()

    /** 进程内只读一次盘；调用点在 ViewModel init，早于任何首页请求。 */
    fun warm(context: Context) {
        synchronized(memory) {
            if (memory.isNotEmpty()) return
            val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            for (entry in prefs.all) {
                val items = decode(entry.value as? String ?: continue)
                if (items.isNotEmpty()) memory[entry.key] = items
            }
        }
    }

    fun cached(feed: HomeFeed): List<DramaCard> = synchronized(memory) { memory[feed.name.lowercase()].orEmpty() }

    /** 失败可容忍：落盘失败只影响下次启动没有首帧缓存。apply 自带异步写。 */
    fun store(feed: HomeFeed, items: List<DramaCard>, context: Context) {
        if (items.isEmpty()) return
        val trimmed = items.take(MAX_ITEMS)
        synchronized(memory) { memory[feed.name.lowercase()] = trimmed }
        val json = Json.stringify(trimmed.map { card -> linkedMapOf<String, Any>(
            "id" to card.id, "title" to card.title, "cover" to card.cover,
            "episodeCount" to card.episodeCount, "summary" to card.summary) })
        try {
            context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
                .edit().putString(feed.name.lowercase(), json).apply()
        } catch (_: RuntimeException) { }
    }

    private fun decode(json: String): List<DramaCard> = try {
        Json.optArr(Json.parse(json)).mapNotNull { raw ->
            val row = Json.optObj(raw) ?: return@mapNotNull null
            val card = DramaCard(
                id = Json.optStr(row["id"]),
                title = Json.optStr(row["title"]),
                cover = Json.optStr(row["cover"]),
                episodeCount = Json.optLong(row["episodeCount"], 0).toInt(),
                summary = Json.optStr(row["summary"]),
            )
            if (card.id.isNotBlank() && card.title.isNotBlank()) card else null
        }
    } catch (_: Exception) { emptyList() }
}
