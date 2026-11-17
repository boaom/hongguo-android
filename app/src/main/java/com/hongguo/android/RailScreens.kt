package com.hongguo.android

import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.ceil
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.rememberLazyListState
import com.hongguo.account.AccountViewModel
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.text.style.TextAlign
import com.hongguo.account.protocol.RankEntry
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import com.hongguo.account.protocol.RankBoard

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
private fun RailSectionTitle(
    title: String,
    trailing: String,
    count: String? = null,
    onTrailing: (() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 10.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, color = LocalRailColors.current.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            count?.let { Text(it, color = LocalRailColors.current.muted, fontSize = 12.sp) }
        }
        if (onTrailing != null && trailing.isNotBlank()) {
            Text(
                trailing,
                Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onTrailing)
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                color = LocalRailColors.current.brand,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
        } else if (trailing.isNotBlank()) {
            Text(trailing, color = LocalRailColors.current.muted, fontSize = 12.sp)
        }
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
private fun ContinueWatchingCard(card: DramaCard, progress: RailProgress, positionMs: Long, onPlay: () -> Unit) {
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
                    if (progress.watched > 0) "第 ${progress.watched} 集 · ${libraryPositionLabel(positionMs)} / 全 ${progress.total} 集"
                    else "全 ${progress.total} 集 · 还没开始",
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
                    if (progress.watched > 0) "继续看 · 第 ${progress.watched} 集" else "从第 1 集开始",
                    Modifier.fillMaxWidth().padding(top = 12.dp),
                ) { onPlay() }
            }
        }
    }
}

@Composable
private fun RailDramaRow(
    card: DramaCard,
    progress: RailProgress,
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
            RailPoster(card, Modifier.width(40.dp).height(54.dp), corner = 6.dp)
            Column(Modifier.weight(1f)) {
                Text(
                    card.title, color = LocalRailColors.current.ink, fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (progress.watched > 0) "看到第 ${progress.watched} 集 · ${progress.total} 集"
                    else "${progress.total} 集",
                    Modifier.padding(top = 3.dp, bottom = 5.dp),
                    color = LocalRailColors.current.muted, fontSize = 12.sp,
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
    RailTheme(resolveDark(state.themeMode, AppThemeStyle.RAIL, isSystemInDarkTheme())) {
        val colors = LocalRailColors.current
        val items = state.home.items
        val resume = state.history.firstOrNull()
        val featured = resume?.card ?: items.firstOrNull()
        val updates = items.filter { it.id != featured?.id }.take(3)
        val list = rememberLazyListState()
        var pickOffset by rememberSaveable { mutableIntStateOf(0) }
        val pool = items.filter { it.id != featured?.id && it !in updates }.distinctBy { it.id }
        val picks = if (pool.size <= 3) pool else List(3) { pool[(pickOffset + it) % pool.size] }

        LoadAheadEffect(state.home, { list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 },
            { list.layoutInfo.totalItemsCount }, onLoad = model::loadMoreHome)

        LazyColumn(
            Modifier.fillMaxSize().background(colors.surface).testTag("rail_home"),
            state = list,
            contentPadding = PaddingValues(bottom = 24.dp).plusNavClearance(),
        ) {
            item { RailTopBar("红果", "今晚") }
            item {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(22.dp),
                ) {
                    listOf(
                        HomeFeed.RECOMMEND to "热度",
                        HomeFeed.LATEST to "新剧",
                        HomeFeed.COMIC to "漫剧",
                        HomeFeed.GUESS to "推荐",
                    ).forEach { (feed, label) ->
                        val selected = state.homeFeed == feed
                        Column(
                            Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(4.dp))
                                .clickable { model.selectHomeFeed(feed) }
                                .testTag("rail_feed_${feed.name.lowercase()}"),
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                label,
                                color = if (selected) colors.ink else colors.muted,
                                fontSize = if (selected) 16.sp else 14.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            )
                            Box(
                                Modifier.padding(top = 4.dp).width(22.dp).height(2.dp)
                                    .background(if (selected) colors.brand else Color.Transparent),
                            )
                        }
                    }
                }
            }

            when {
                featured == null && state.home.loading -> item { RailLoadingLine() }
                featured == null && state.home.error != null -> item {
                    RailErrorLine(state.home.error!!, onRetry = model::refreshHome)
                }
                featured == null -> item { RailEmptyLine("今晚还没有可看的剧", "换个频道看看") }
                else -> {
                    val progress = railProgressOf(featured, state)
                    item {
                        RailSummaryLine(
                            if (progress.watched > 0) "接着上次看" else "今晚推荐",
                            "还剩 ${progress.remaining} 集 · ${railDuration(progress.remaining)}",
                        )
                    }
                    item {
                        Box(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp)) {
                            ContinueWatchingCard(featured, progress, resume?.positionMs ?: 0L) {
                                if (resume != null && resume.card.id == featured.id) model.openLibrary(resume)
                                else model.playDrama(featured)
                            }
                        }
                    }
                }
            }

            if (updates.isNotEmpty()) {
                item {
                    Column(Modifier.padding(horizontal = 20.dp).padding(top = 24.dp)) {
                        RailSectionTitle("为你推荐", "", "${updates.size} 部")
                        updates.forEachIndexed { index, card ->
                            RailDramaRow(card, railProgressOf(card, state), index < updates.lastIndex) {
                                model.openDrama(card)
                            }
                        }
                    }
                }
            }

            if (picks.isNotEmpty()) {
                item {
                    Column(Modifier.padding(horizontal = 20.dp).padding(top = 24.dp)) {
                        RailSectionTitle(
                            "热度精选",
                            "换一批 ↻",
                            onTrailing = { if (pool.size > 3) pickOffset = (pickOffset + 3) % pool.size },
                        )
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


/**
 * 发现：分类筛选后的剧池，与其它主题的「发现/剧场/检索」同一条数据源（state.category）。
 * 网格沿用集刻度自己的语言——封面 + 剧名 + 集数，进度条只在有记录时出现。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RailDiscover(state: HongguoUiState, model: HongguoViewModel) {
    RailTheme(resolveDark(state.themeMode, AppThemeStyle.RAIL, isSystemInDarkTheme())) {
        val colors = LocalRailColors.current
        val items = state.category.items
        var showFilters by rememberSaveable { mutableStateOf(false) }
        val list = rememberLazyListState()

        LaunchedEffect(Unit) { model.loadRankPreview() }
        LoadAheadEffect(state.category, { list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 },
            { list.layoutInfo.totalItemsCount }, state.browseDraft == state.browseApplied, model::loadMoreBrowse)

        Column(Modifier.fillMaxSize().background(colors.surface)) {
            RailTopBar("发现", if (items.isEmpty()) "" else "${items.size} 部")
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    if (state.browseDraft.genre == "short_play") "全部短剧" else state.browseDraft.genre,
                    Modifier.weight(1f), color = colors.muted, fontSize = 13.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "筛选",
                    Modifier.clip(RoundedCornerShape(6.dp)).background(colors.card)
                        .clickable { showFilters = true }.testTag("rail_filters")
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    color = colors.ink, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                )
            }

            LazyColumn(
                Modifier.fillMaxSize().testTag("rail_discover"),
                state = list,
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp).plusNavClearance(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        RankBoard.entries.forEach { board ->
                            val selected = board == state.rankBoard
                            Text(
                                board.label,
                                Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(4.dp))
                                    .clickable { model.selectRankBoard(board) }
                                    .testTag("rail_rank_board_${board.route}"),
                                color = if (selected) colors.brand else colors.muted,
                                fontSize = 14.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        }
                    }
                }
                item { RailSectionTitle("排行榜", "查看全部", onTrailing = model::openRank) }
                when {
                    state.rankError != null && state.rankEntries.isEmpty() ->
                        item { RailErrorLine(state.rankError!!, onRetry = model::retryRankPreview) }
                    state.rankLoading && state.rankEntries.isEmpty() -> item { RailLoadingLine() }
                    state.rankEntries.isEmpty() ->
                        item { RailEmptyLine("榜单暂时为空", "稍后重试，或打开完整榜单查看") }
                    else -> items(state.rankEntries.take(5), key = { "rank_${it.series.id}" }) { entry ->
                        RailRankRow(entry) { model.openDrama(entry.toCard()) }
                    }
                }
                if (state.rankEntries.isNotEmpty()) {
                    item {
                        Text(
                            "完整榜单",
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                .clickable { model.openRank() }.testTag("rail_rank_more")
                                .padding(vertical = 10.dp),
                            color = colors.brand, fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                        )
                    }
                }

                item { Box(Modifier.fillMaxWidth().height(1.dp).background(colors.hair)) }
                item { RailSectionTitle("全部剧集", "", if (items.isEmpty()) null else "${items.size} 部") }
                when {
                    items.isEmpty() && state.category.loading -> item { RailLoadingLine() }
                    items.isEmpty() && state.category.error != null ->
                        item { RailErrorLine(state.category.error!!, onRetry = model::loadMoreBrowse) }
                    items.isEmpty() -> item { RailEmptyLine("这个条件下没有剧", "换个类型或清掉筛选试试") }
                    else -> itemsIndexed(items.chunked(3), key = { _, row -> row.first().id }) { _, row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            row.forEach { card ->
                                val progress = railProgressOf(card, state)
                                Column(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).clickable { model.openDrama(card) }) {
                                    RailPoster(card, Modifier.fillMaxWidth().aspectRatio(3f / 4.2f))
                                    Text(
                                        card.title, Modifier.padding(top = 7.dp), color = colors.ink,
                                        fontSize = 13.5.sp, lineHeight = 18.sp, maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        if (progress.watched > 0) "看到第 ${progress.watched} 集 · ${progress.total} 集"
                                        else "${progress.total} 集",
                                        Modifier.padding(top = 4.dp), color = colors.muted, fontSize = 12.sp,
                                    )
                                    if (progress.watched > 0) {
                                        Box(Modifier.padding(top = 6.dp)) {
                                            EpisodeRail(progress.total, progress.watched, RailRailStyle.Small)
                                        }
                                    }
                                }
                            }
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
                if (state.category.loadingMore) item { RailLoadingLine() }
            }
        }

        if (showFilters) ModalBottomSheet(
            onDismissRequest = { model.discardBrowseDraft(); showFilters = false },
        ) {
            FilterSheetContent(state, model) { showFilters = false }
        }
    }
}


@Composable
fun RailDetail(state: HongguoUiState, model: HongguoViewModel, accountModel: AccountViewModel) {
    RailTheme(resolveDark(state.themeMode, AppThemeStyle.RAIL, isSystemInDarkTheme())) {
    val card = state.selectedCard ?: return@RailTheme
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
                                            Box(Modifier.weight(1f)) {
                                                // 显示 1 基集号，播放传 0 基列表下标（playEpisode 走
                                                // episodes.getOrNull(position)，传 1 基会整体错开一集）。
                                                RailEpisodeCell(index + 1, progress.watched) {
                                                    model.playEpisode(index)
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

        if (episodes.isNotEmpty()) RailPrimaryButton(
            buildString {
                if (progress.watched > 0) append("播放第 ${progress.nextEpisode} 集")
                else append("从第 1 集开始")
            },
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp).navigationBarsPadding(),
        // nextEpisode 是 1 基集号（给人看的），playEpisode 要 0 基下标。
        ) { model.playEpisode((progress.nextEpisode - 1).coerceAtLeast(0)) }
    }
}



}

/** 发现页里的一行榜单：名次 + 封面 + 剧名 + 热度/集数，语言与集刻度一致。 */
@Composable
private fun RailRankRow(entry: RankEntry, onOpen: () -> Unit) {
    val colors = LocalRailColors.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClick = onOpen)
            .testTag("rail_rank_${entry.series.id}").padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            if (entry.rank > 0) entry.rank.toString() else "–",
            Modifier.width(22.dp),
            color = if (entry.rank in 1..3) colors.brand else colors.muted,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
        RailPoster(entry.toCard(), Modifier.width(40.dp).height(54.dp), corner = 6.dp)
        Column(Modifier.weight(1f)) {
            Text(
                entry.series.title,
                color = colors.ink,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOf(entry.heat, if (entry.episodeCount > 0) "全 ${entry.episodeCount} 集" else "")
                    .filter { it.isNotBlank() }.joinToString(" · "),
                Modifier.padding(top = 3.dp),
                color = colors.muted,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun RailLibraryRow(entry: LibraryEntry, showDivider: Boolean, onOpen: () -> Unit) {
    val colors = LocalRailColors.current
    val card = entry.card
    val progress = RailProgress(total = card.episodeCount, watched = entry.episodeIndex)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).clickable(onClick = onOpen)
            .testTag("rail_library_${card.id}"),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RailPoster(card, Modifier.width(56.dp).height(76.dp), corner = 8.dp)
            Column(Modifier.weight(1f)) {
                Text(
                    card.title,
                    color = colors.ink,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (progress.watched > 0) "看到第 ${progress.watched} 集 / ${progress.total} 集"
                    else "${progress.total} 集 · 还没开始",
                    Modifier.padding(top = 4.dp),
                    color = colors.muted,
                    fontSize = 12.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Box(Modifier.padding(top = 8.dp)) { EpisodeRail(progress.total, progress.watched, RailRailStyle.Small) }
                Row(Modifier.fillMaxWidth().padding(top = 5.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("还剩 ${progress.remaining} 集", color = colors.muted, fontSize = 12.sp)
                    Text(railDuration(progress.remaining), color = colors.muted, fontSize = 12.sp)
                }
            }
        }
        if (showDivider) Box(Modifier.fillMaxWidth().height(1.dp).background(colors.hair))
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
                // 两个页签就在页内切换:底栏只有一个「追更」入口,历史不该只能靠底栏绕进来。
                Row(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    listOf(false to "追更", true to "历史").forEach { (isHistory, label) ->
                        val active = history == isHistory
                        Column(
                            Modifier.clickable { model.selectTab(if (isHistory) AppTab.HISTORY else AppTab.FAVORITES) }
                                .testTag(if (isHistory) "rail_history" else "rail_favorites"),
                        ) {
                            Text(
                                label,
                                color = if (active) colors.ink else colors.muted,
                                fontSize = 15.sp,
                                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                            )
                            Box(
                                Modifier.padding(top = 4.dp).fillMaxWidth().height(2.dp)
                                    .background(if (active) colors.brand else Color.Transparent),
                            )
                        }
                    }
                }
            }
            item {
                RailSummaryLine(
                    if (entries.isEmpty()) "还没有记录"
                    else "共 ${entries.size} 部 · ${totalEpisodes} 集",
                    if (entries.isEmpty()) "" else "共 ${totalEpisodes} 集",
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
