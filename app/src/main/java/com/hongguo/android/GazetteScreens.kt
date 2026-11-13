package com.hongguo.android

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.hongguo.account.AccountViewModel

/* ---------------------------------------------------------------------------------------------
 * E · 红果小报: 新闻纸版面。报头 + 双栏头条 + 栏目快讯 + 黑底剧目榜；检索是分类版面，
 * 剪存是折子目录，详情是报版内页。衬线标题 + 朱砂眉线 + 细栏线。
 * ------------------------------------------------------------------------------------------- */

private val GazetteNumerals = listOf("一", "二", "三", "四", "五", "六", "七", "八", "九", "十")

private fun gazetteNumeral(index: Int): String =
    GazetteNumerals.getOrElse(index) { "${index + 1}" }

private fun gazetteIssue(): String = "第 ${LocalDate.now().dayOfYear} 期"

private fun gazetteDate(): String =
    LocalDate.now().format(DateTimeFormatter.ofPattern("M月d日", Locale.CHINA))

/** 报头下的双栏线：粗线 + 细线。 */
@Composable
private fun GazetteRule(modifier: Modifier = Modifier, color: Color = Ink) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Box(Modifier.fillMaxWidth().height(2.dp).background(color))
        Box(Modifier.fillMaxWidth().height(1.dp).background(color))
    }
}

/** 栏目小标：朱砂眉线 + 字距。 */
@Composable
private fun GazetteKicker(text: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text, color = AccentText, fontSize = 11.sp, fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp, fontFamily = FontFamily.Serif)
        Box(Modifier.weight(1f).height(1.dp).background(Line))
    }
}

@Composable
internal fun GazetteHome(state: HongguoUiState, model: HongguoViewModel) {
    val items = state.home.items
    val featured = items.firstOrNull()
    val flashes = items.drop(1).take(3)
    val rest = if (items.size > 4) items.drop(4) else emptyList()
    val list = rememberLazyListState()
    LoadAheadEffect(state.home, { list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 },
        { list.layoutInfo.totalItemsCount }, onLoad = model::loadMoreHome)
    LazyColumn(Modifier.fillMaxSize().testTag("gazette_home"), state = list,
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 20.dp).plusNavClearance(),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            // 报头：报名 + 期号，压双栏线。
            Column(Modifier.padding(top = 8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("红果小报", color = Ink, fontSize = 24.sp, fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Serif, letterSpacing = 3.sp,
                        modifier = Modifier.testTag("gazette_masthead"))
                    Text("${gazetteIssue()} · ${gazetteDate()}", color = Muted, fontSize = 10.sp)
                }
                GazetteRule(Modifier.padding(top = 8.dp))
            }
        }
        item {
            // 栏目页签：综合 / 新剧 / 漫剧 / 推荐。
            val tabs = rememberTabIndicator()
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState(Int.MAX_VALUE))
                .slidingIndicator(tabs, state.homeFeed, Orange, width = 20.dp, height = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.Bottom) {
                listOf(HomeFeed.RECOMMEND to "综合", HomeFeed.LATEST to "新剧", HomeFeed.COMIC to "漫剧",
                    HomeFeed.GUESS to "推荐").forEach { (feed, label) ->
                    val selected = state.homeFeed == feed
                    val color by animatedTabColor(selected, Ink, Muted)
                    Text(label, color = color, fontSize = animatedTabSize(selected, 16.sp, 13.sp),
                        fontWeight = if (selected) FontWeight.Black else FontWeight.Medium,
                        fontFamily = FontFamily.Serif,
                        modifier = Modifier.tabAnchor(tabs, feed).quietSelectable(selected) { model.selectHomeFeed(feed) }
                            .heightIn(min = 40.dp).testTag("gazette_feed_${feed.name.lowercase()}"))
                }
            }
        }
        featured?.let { card ->
            item {
                // 头条：大图 + 衬线大标题 + 通讯正文。
                Column(Modifier.clickable { model.openDrama(card) }.testTag("gazette_headline")) {
                    Box(Modifier.fillMaxWidth().aspectRatio(1.5f).clip(RoundedCornerShape(6.dp))
                        .border(1.dp, Line, RoundedCornerShape(6.dp))) {
                        PosterImage(card.cover, Modifier.fillMaxSize())
                    }
                    Text(card.title, color = Ink, fontSize = 19.sp, lineHeight = 27.sp,
                        fontWeight = FontWeight.Black, fontFamily = FontFamily.Serif,
                        maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 10.dp))
                    Text(if (card.episodeCount > 0) "全 ${card.episodeCount} 集 · ${gazetteDate()} 报道"
                        else gazetteDate(),
                        color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 5.dp))
                    if (card.summary.isNotBlank()) Text(card.summary, color = Muted, fontSize = 13.sp,
                        lineHeight = 22.sp, fontFamily = FontFamily.Serif, maxLines = 4,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
        if (state.home.loading && items.isEmpty()) item {
            Row(Modifier.fillMaxWidth().padding(vertical = 18.dp), horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Text("正在排版…", Modifier.padding(start = 12.dp), color = Muted, fontSize = 14.sp)
            }
        }
        if (state.home.loaded && !state.home.loading && items.isEmpty() && state.home.error == null)
            item { EmptyPrompt("这一版暂无稿件", "可以刷新，或切换上方栏目。") }
        if (flashes.isNotEmpty()) item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                GazetteKicker("新 剧 速 递")
                flashes.forEachIndexed { index, card ->
                    Row(Modifier.fillMaxWidth().clickable { model.openDrama(card) }
                        .testTag("gazette_flash_${index + 1}").padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Cover(card.cover, card.title, Modifier.width(46.dp).height(61.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(card.title, color = Ink, fontSize = 14.sp, lineHeight = 20.sp,
                                fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif,
                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                            if (card.episodeCount > 0) Text("全 ${card.episodeCount} 集", color = Muted, fontSize = 11.sp)
                        }
                        Icon(Icons.Outlined.ChevronRight, null, Modifier.size(16.dp), tint = Muted)
                    }
                }
            }
        }
        if (rest.isNotEmpty()) item {
            // 本期剧目榜：黑底报头 + 一二三编号目录。
            Column(Modifier.border(1.5.dp, Ink, RoundedCornerShape(4.dp))) {
                Box(Modifier.fillMaxWidth().background(Ink).padding(horizontal = 14.dp, vertical = 8.dp)) {
                    Text("本 期 剧 目 榜", color = Canvas, fontSize = 13.sp, fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Serif, letterSpacing = 2.sp)
                }
                rest.take(6).forEachIndexed { index, card ->
                    Row(Modifier.fillMaxWidth().clickable { model.openDrama(card) }
                        .testTag("gazette_rank_${index + 1}").padding(horizontal = 14.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(gazetteNumeral(index), color = AccentText, fontSize = 13.sp,
                            fontWeight = FontWeight.Black, fontFamily = FontFamily.Serif, modifier = Modifier.width(18.dp))
                        Cover(card.cover, card.title, Modifier.width(32.dp).height(43.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(card.title, color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Serif, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (card.episodeCount > 0) Text("全 ${card.episodeCount} 集", color = Muted, fontSize = 10.sp)
                        }
                    }
                }
            }
        }
        state.home.error?.let { error -> item {
            FailurePanel(error, if (items.isNotEmpty() && state.home.hasMore) model::loadMoreHome else model::refreshHome,
                "gazette_retry")
        } }
        if (state.home.hasMore && items.isNotEmpty() && state.home.error == null) item {
            LoadAheadFooter("gazette_load_more")
        }
        if (!state.home.loading && !state.home.hasMore && items.isNotEmpty()) item {
            Text("—— 红果小报 · ${gazetteIssue()} ——", Modifier.fillMaxWidth().padding(vertical = 6.dp),
                color = Muted, fontSize = 11.sp, fontFamily = FontFamily.Serif, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun GazetteChip(label: String, selected: Boolean, tag: String, onClick: () -> Unit) {
    Text(label, color = if (selected) AccentText else Muted,
        fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        modifier = Modifier.clip(RoundedCornerShape(3.dp))
            .border(1.dp, if (selected) Ink else Line, RoundedCornerShape(3.dp))
            .clickable { onClick() }.padding(horizontal = 12.dp, vertical = 7.dp)
            .testTag(tag))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GazetteTheater(state: HongguoUiState, model: HongguoViewModel) {
    var showFilters by rememberSaveable { mutableStateOf(false) }
    val items = state.category.items
    val list = rememberLazyListState()
    LoadAheadEffect(state.category, { list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 },
        { list.layoutInfo.totalItemsCount }, state.browseDraft == state.browseApplied, model::loadMoreBrowse)
    LazyColumn(Modifier.fillMaxSize().testTag("gazette_theater"), state = list,
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 20.dp).plusNavClearance(),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("检索", color = Ink, fontSize = 24.sp, fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Serif, letterSpacing = 3.sp,
                        modifier = Modifier.testTag("gazette_theater_title"))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        RankEntryButton(model, "gazette_theater_rank")
                        IconButton(onClick = { model.selectTab(AppTab.SEARCH) },
                            modifier = Modifier.testTag("gazette_theater_search")) {
                            Icon(Icons.Outlined.Search, "搜索", tint = Ink)
                        }
                    }
                }
                Text("按 栏 检 索 · 各 版 各 有 好 戏", color = Muted, fontSize = 10.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
                GazetteRule()
            }
        }
        item {
            // 剧种分版：短剧 / 漫剧 / AI 剧。
            val tabs = rememberTabIndicator()
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).border(1.dp, Line, RoundedCornerShape(4.dp))
                .padding(horizontal = 4.dp).slidingPill(tabs, state.browseDraft.genre, Cream, corner = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("short_play" to "短剧", "comic_series" to "漫剧", "ai_series" to "AI 剧").forEach { (genre, label) ->
                    val selected = state.browseDraft.genre == genre
                    val color by animatedTabColor(selected, Ink, Muted)
                    Box(Modifier.weight(1f).tabAnchor(tabs, genre).quietSelectable(selected) { model.changeGenre(genre) }
                        .heightIn(min = 38.dp).testTag("gazette_genre_$genre"), contentAlignment = Alignment.Center) {
                        Text(label, color = color, fontSize = 14.sp, fontWeight = if (selected) FontWeight.Black else FontWeight.Medium,
                            fontFamily = FontFamily.Serif)
                    }
                }
            }
        }
        item {
            val group = state.filterGroups.firstOrNull { it.key == "theme" } ?: state.filterGroups.firstOrNull()
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                val count = state.browseApplied.options.values.count { it.isNotBlank() }
                GazetteChip(if (count > 0) "筛选 · $count" else "全部筛选", count > 0, "gazette_filters") { showFilters = true }
                group?.options?.filter { it.id.isNotBlank() }?.take(12)?.forEach { option ->
                    val on = state.browseApplied.options[group.key] == option.id
                    GazetteChip(option.label, on, "gazette_quick_${group.key}_${option.id}") {
                        model.selectFilter(group.key, option.id)
                        model.applyBrowse()
                    }
                }
            }
        }
        if (state.category.loading && items.isEmpty()) item {
            Row(Modifier.fillMaxWidth().padding(vertical = 18.dp), horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Text("正在排版…", Modifier.padding(start = 12.dp), color = Muted, fontSize = 14.sp)
            }
        }
        if (state.category.loaded && !state.category.loading && items.isEmpty() && state.category.error == null)
            item { EmptyPrompt("这一版暂无稿件", "试试减少筛选条件，或换一版。") }
        itemsIndexed(items, key = { _, card -> card.id }) { index, card ->
            Row(Modifier.fillMaxWidth().clickable { model.openDrama(card) }
                .testTag("gazette_theater_$index").padding(vertical = 8.dp)
                .animateItem(),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(gazetteNumeral(index), color = AccentText, fontSize = 13.sp, fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Serif, modifier = Modifier.width(20.dp))
                Cover(card.cover, card.title, Modifier.width(78.dp).height(104.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(card.title, color = Ink, fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Serif, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (card.summary.isNotBlank()) Text(card.summary, color = Muted, fontSize = 12.sp,
                        lineHeight = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (card.episodeCount > 0) Text("全 ${card.episodeCount} 集", color = Muted, fontSize = 11.sp)
                }
                Surface(onClick = { model.playDrama(card) }, shape = RoundedCornerShape(3.dp), color = Ink,
                    modifier = Modifier.heightIn(min = 32.dp).testTag("gazette_theater_play_$index")) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Icon(Icons.Outlined.PlayArrow, null, tint = Canvas, modifier = Modifier.size(15.dp))
                        Text("开演", color = Canvas, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Serif)
                    }
                }
            }
        }
        state.category.error?.let { error -> item {
            FailurePanel(error, if (items.isNotEmpty() && state.category.hasMore) model::loadMoreBrowse else model::applyBrowse,
                "gazette_theater_retry")
        } }
        if (state.category.hasMore && items.isNotEmpty() && state.category.error == null) item {
            LoadAheadFooter("gazette_theater_more")
        }
        if (!state.category.loading && !state.category.hasMore && items.isNotEmpty()) item {
            Text("—— 本 版 刊 完 ——", Modifier.fillMaxWidth().padding(vertical = 6.dp),
                color = Muted, fontSize = 11.sp, fontFamily = FontFamily.Serif, textAlign = TextAlign.Center)
        }
    }
    if (showFilters) ModalBottomSheet(onDismissRequest = { model.discardBrowseDraft(); showFilters = false },
        containerColor = CardSurface) {
        FilterSheetContent(state, model) { showFilters = false }
    }
}

@Composable
internal fun GazetteLibrary(state: HongguoUiState, model: HongguoViewModel, accountModel: AccountViewModel) {
    val history = state.tab == AppTab.HISTORY
    var editing by rememberSaveable(state.tab, state.libraryCloud) { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().testTag("gazette_library")) {
        Column(Modifier.padding(horizontal = 18.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text("剪存", color = Ink, fontSize = 24.sp, fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Serif, letterSpacing = 3.sp,
                    modifier = Modifier.testTag("gazette_library_title"))
                Text("我 的 剧 报 合 订 本", color = Muted, fontSize = 10.sp)
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
                listOf(Triple("观演记录", !state.libraryCloud && history, "gazette_tab_history"),
                    Triple("收藏剧目", !state.libraryCloud && !history, "gazette_tab_favorites"),
                    Triple("账号云片单", state.libraryCloud, "gazette_tab_account")).forEachIndexed { index, (label, selected, tag) ->
                    val color by animatedTabColor(selected, Ink, Muted)
                    Text(label, color = color, fontSize = animatedTabSize(selected, 15.sp, 13.sp),
                        fontWeight = if (selected) FontWeight.Black else FontWeight.Medium,
                        fontFamily = FontFamily.Serif,
                        modifier = Modifier.quietSelectable(selected) {
                            when (index) {
                                0 -> { model.selectLibrarySource(false); model.selectTab(AppTab.HISTORY) }
                                1 -> { model.selectLibrarySource(false); model.selectTab(AppTab.FAVORITES) }
                                else -> model.selectLibrarySource(true)
                            }
                        }.heightIn(min = 40.dp).testTag(tag))
                }
                Spacer(Modifier.weight(1f))
                val entries = if (history) state.history else state.favorites
                LaunchedEffect(entries.isEmpty()) { if (entries.isEmpty()) editing = false }
                if (!state.libraryCloud && entries.isNotEmpty()) Text("整理", color = Muted, fontSize = 12.sp,
                    modifier = Modifier.clickable { editing = !editing }.padding(4.dp).testTag("gazette_library_edit"))
            }
            GazetteRule()
        }
        if (state.libraryCloud) {
            Box(Modifier.fillMaxWidth().weight(1f)) {
                CloudLibraryContent(accountModel, model::openLibrary, onSignIn = { model.selectTab(AppTab.ACCOUNT) })
            }
            return@Column
        }
        val entries = if (history) state.history else state.favorites
        LazyColumn(Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 20.dp).plusNavClearance(),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (!state.libraryCloud) item {
                Row(Modifier.fillMaxWidth().background(Cream).clickable { model.selectLibrarySource(true) }
                    .padding(horizontal = 12.dp, vertical = 10.dp).testTag("gazette_library_cloud_hint"),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Outlined.CloudQueue, null, tint = AccentInk, modifier = Modifier.size(18.dp))
                    Text("登录红果账号，手机上的追剧记录也能接着看", color = Muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    Icon(Icons.Outlined.ChevronRight, null, tint = Muted, modifier = Modifier.size(16.dp))
                }
            }
            if (entries.isEmpty()) item {
                EmptyPrompt(if (history) "合订本还没有观演记录" else "剪存夹还空着",
                    if (history) "开始播放后，这里会记住集数和进度。" else "在内页点收藏，喜欢的故事就剪存进合订本。")
            }
            itemsIndexed(entries, key = { _, entry -> entry.card.id }) { index, entry ->
                Box(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().clickable { model.openLibrary(entry) }
                        .testTag("gazette_entry_${entry.card.id}").padding(vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(gazetteNumeral(index), color = Muted, fontSize = 13.sp, fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Serif, modifier = Modifier.width(20.dp))
                        Cover(entry.card.cover, entry.card.title, Modifier.width(62.dp).height(83.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(entry.card.title, color = Ink, fontSize = 14.sp, lineHeight = 20.sp,
                                fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif,
                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                            if (history) {
                                Text("看到第 ${entry.episodeIndex.coerceAtLeast(1)} 集", color = Muted, fontSize = 11.sp)
                                WatchProgress(entry, track = Line, fill = Orange, modifier = Modifier.width(120.dp))
                            } else if (entry.card.episodeCount > 0) {
                                Text("全 ${entry.card.episodeCount} 集", color = Muted, fontSize = 11.sp)
                            }
                        }
                    }
                    if (editing) RemoveBadge(if (history) "删除${entry.card.title}的记录" else "取消收藏${entry.card.title}",
                        Modifier.align(Alignment.CenterEnd)) {
                        if (history) model.removeHistory(entry.card.id) else model.toggleFavorite(entry.card)
                    }
                }
            }
        }
    }
}

@Composable
internal fun GazetteDetail(state: HongguoUiState, model: HongguoViewModel, accountModel: AccountViewModel) {
    val card = state.selectedCard ?: return
    val detail = state.detail
    val favorite = state.favorites.any { it.card.id == card.id }
    Column(Modifier.fillMaxSize().testTag("gazette_detail").background(Canvas)) {
        Row(Modifier.fillMaxWidth().padding(start = 6.dp, end = 18.dp, top = 6.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IconButton(onClick = model::closeDetail, modifier = Modifier.testTag("detail_back")) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回", tint = Ink)
            }
            Text("红果小报 · 内页", color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Serif, modifier = Modifier.weight(1f))
            Text("${gazetteIssue()} · ${gazetteDate()}", color = Muted, fontSize = 10.sp)
        }
        LazyColumn(Modifier.fillMaxWidth().weight(1f).testTag("gazette_detail_list"),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Column {
                    Box(Modifier.fillMaxWidth().aspectRatio(1.5f).clip(RoundedCornerShape(6.dp))
                        .border(1.dp, Line, RoundedCornerShape(6.dp))) {
                        PosterImage(detail?.cover ?: card.cover, Modifier.fillMaxSize())
                    }
                    Text(detail?.title ?: card.title, color = Ink, fontSize = 21.sp, lineHeight = 29.sp,
                        fontWeight = FontWeight.Black, fontFamily = FontFamily.Serif,
                        maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 12.dp))
                    Text(detail?.let { "全 ${it.episodes.size} 集" }
                        ?: if (card.episodeCount > 0) "全 ${card.episodeCount} 集" else "正在读取剧集信息",
                        color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp))
                    GazetteRule(Modifier.padding(top = 12.dp))
                }
            }
            item {
                FavoritePair(card, favorite, { model.toggleFavorite(card) }, accountModel, onSignIn = { model.selectTab(AppTab.ACCOUNT) })
            }
            if (state.loadingDetail) item {
                Row(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text("正在排印内页…", Modifier.padding(start = 12.dp), color = Muted, fontSize = 14.sp)
                }
            }
            if (state.detailError != null) item { FailurePanel(state.detailError, model::retryDetails) }
            if (detail != null) {
                if (detail.summary.isNotBlank()) item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        GazetteKicker("本 报 讯")
                        Text(detail.summary, color = Muted, fontSize = 13.sp, lineHeight = 23.sp,
                            fontFamily = FontFamily.Serif)
                    }
                }
                item { RelatedWorksSection(state, model, accountModel) }
                item {
                    GazetteKicker("分 期 连 载")
                }
                items((detail.episodes.size + 4) / 5, key = { "gazette_episodes_$it" }) { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        repeat(5) { column ->
                            val index = row * 5 + column
                            val episode = detail.episodes.getOrNull(index)
                            if (episode == null) Spacer(Modifier.weight(1f))
                            else {
                                val selected = state.episodePosition == index
                                Surface(onClick = { model.playEpisode(index) }, modifier = Modifier.weight(1f)
                                    .heightIn(min = 42.dp).testTag("episode_$index"),
                                    shape = RoundedCornerShape(3.dp), color = if (selected) Ink else CardSurface,
                                    border = if (selected) null else androidx.compose.foundation.BorderStroke(1.dp, Line)) {
                                    Box(Modifier.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                                        Text("${episode.index}", fontSize = 13.sp, fontFamily = FontFamily.Serif,
                                            color = if (selected) Canvas else Ink,
                                            fontWeight = if (selected) FontWeight.Black else FontWeight.Normal)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item { Text("—— 红 果 小 报 · ${gazetteIssue()} ——", Modifier.fillMaxWidth().padding(vertical = 8.dp),
                color = Muted, fontSize = 11.sp, fontFamily = FontFamily.Serif, textAlign = TextAlign.Center) }
        }
        if (detail != null && detail.episodes.isNotEmpty()) {
            val episode = detail.episodes.getOrNull(state.episodePosition)?.index ?: detail.episodes.first().index
            Box(Modifier.padding(horizontal = 18.dp, vertical = 10.dp)) {
                Button(onClick = model::openPlayer, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)
                    .testTag("open_player"), shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Ink, contentColor = Canvas)) {
                    Icon(Icons.Outlined.PlayArrow, contentDescription = null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("开演 · 第 $episode 集", fontSize = 15.sp, fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Serif)
                }
            }
        }
    }
}
