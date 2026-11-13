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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.hongguo.account.AccountViewModel

/* ---------------------------------------------------------------------------------------------
 * D · 红果场刊: 纸面 (paper) 上排版。报头开篇、今晚首演置顶、编号剧目目录；排片厅按厅排片；
 * 剧目单是剧目列表；详情是一页场刊内页。衬线标题 + 双栏线 + 朱砂印章感。
 * ------------------------------------------------------------------------------------------- */

private val PlaybillNumerals = listOf("壹", "贰", "叁", "肆", "伍", "陆", "柒", "捌", "玖", "拾")

private fun playbillNumeral(index: Int): String =
    PlaybillNumerals.getOrElse(index) { "${index + 1}" }

/** 场刊的双栏线：粗线 + 细线，报头与段落之间的纸面装饰。 */
@Composable
private fun DoubleRule(modifier: Modifier = Modifier, color: Color = Ink) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Box(Modifier.fillMaxWidth().height(2.dp).background(color))
        Box(Modifier.fillMaxWidth().height(.5.dp).background(color))
    }
}

@Composable
private fun PlaybillSectionRule(title: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Serif)
        Box(Modifier.weight(1f).height(1.dp).background(Line))
    }
}

/** 报头：场刊名 + 期号行，压在双栏线上。 */
@Composable
private fun PlaybillMasthead(issueLine: String) {
    Column {
        Text("红果场刊", color = Ink, fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Serif, modifier = Modifier.testTag("playbill_masthead"))
        Row(Modifier.fillMaxWidth().padding(top = 3.dp, bottom = 7.dp),
            horizontalArrangement = Arrangement.SpaceBetween) {
            Text(issueLine, color = Muted, fontSize = 11.sp)
            Text("红果剧场 · 每晚更新", color = Muted, fontSize = 11.sp)
        }
        DoubleRule()
    }
}

/** 编号条目：场刊目录里的一出剧目。 */
@Composable
private fun PlaybillEntry(number: Int, card: DramaCard, tag: String, modifier: Modifier = Modifier,
                          posterWidth: Int = 54, posterHeight: Int = 72, trailing: (@Composable () -> Unit)? = null,
                          onClick: () -> Unit) {
    Row(modifier.fillMaxWidth().clickable { onClick() }.heightIn(min = 72.dp).testTag(tag)
        .padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(playbillNumeral(number), color = if (number < 3) AccentInk else Muted,
            fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif,
            modifier = Modifier.width(22.dp))
        Cover(card.cover, card.title, Modifier.width(posterWidth.dp).height(posterHeight.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(card.title, color = Ink, fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Serif, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (card.episodeCount > 0) Text("全 ${card.episodeCount} 集", color = Muted, fontSize = 12.sp)
            card.summary.takeIf { it.isNotBlank() }?.let {
                Text(it, color = Muted, fontSize = 12.sp, lineHeight = 17.sp, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
            }
        }
        trailing?.invoke()
    }
}

@Composable
internal fun PlaybillHome(state: HongguoUiState, model: HongguoViewModel) {
    val items = state.home.items
    val featured = items.firstOrNull()
    val program = if (items.size > 1) items.drop(1) else emptyList()
    val list = rememberLazyListState()
    LoadAheadEffect(state.home, { list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 },
        { list.layoutInfo.totalItemsCount }, onLoad = model::loadMoreHome)
    LazyColumn(Modifier.fillMaxSize().testTag("playbill_home"), state = list,
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 20.dp).plusNavClearance(),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            PlaybillMasthead("今日剧目 · " +
                LocalDate.now().format(DateTimeFormatter.ofPattern("M月d日", Locale.CHINA)))
        }
        item {
            // 频道即场刊的栏目：热度 / 新剧 / 漫剧 / 推荐，衬线大字 + 滑动栏线。
            val tabs = rememberTabIndicator()
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState(Int.MAX_VALUE))
                .slidingIndicator(tabs, state.homeFeed, Orange, width = 22.dp, height = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(22.dp), verticalAlignment = Alignment.Bottom) {
                listOf(HomeFeed.RECOMMEND to "热度", HomeFeed.LATEST to "新剧", HomeFeed.COMIC to "漫剧",
                    HomeFeed.GUESS to "推荐").forEach { (feed, label) ->
                    val selected = state.homeFeed == feed
                    val color by animatedTabColor(selected, Ink, Muted)
                    Text(label, color = color, fontSize = animatedTabSize(selected, 19.sp, 15.sp),
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        fontFamily = FontFamily.Serif,
                        modifier = Modifier.tabAnchor(tabs, feed).quietSelectable(selected) { model.selectHomeFeed(feed) }
                            .heightIn(min = 44.dp).testTag("playbill_feed_${feed.name.lowercase()}"))
                }
            }
        }
        state.history.firstOrNull()?.let { entry ->
            item {
                // 连续放映：昨晚看到一半的那一出，压在目录前面。
                Surface(onClick = { model.openLibrary(entry) }, color = Cream, shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Line),
                    modifier = Modifier.testTag("playbill_continue")) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Outlined.PlayArrow, null, tint = AccentInk, modifier = Modifier.size(20.dp))
                        Text("连续放映", color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Serif)
                        Text("${entry.card.title} · 第 ${entry.episodeIndex.coerceAtLeast(1)} 集", color = Muted,
                            fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Icon(Icons.Outlined.ChevronRight, null, tint = Muted, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
        if (state.home.loading && items.isEmpty()) item {
            Row(Modifier.fillMaxWidth().padding(vertical = 18.dp), horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Text("正在排片…", Modifier.padding(start = 12.dp), color = Muted, fontSize = 14.sp)
            }
        }
        if (state.home.loaded && !state.home.loading && items.isEmpty() && state.home.error == null)
            item { EmptyPrompt("今晚暂时没有剧目", "可以刷新，或切换上方栏目。") }
        featured?.let { card ->
            item {
                // 今晚首演：目录的头牌，海报 + 衬线剧名 + 开演。
                Surface(onClick = { model.openDrama(card) }, color = CardSurface, shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Line),
                    modifier = Modifier.testTag("playbill_featured")) {
                    Column {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("今晚首演", color = AccentText, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Serif)
                            Text("· 第 一 出 ·", color = Muted, fontSize = 11.sp)
                        }
                        DoubleRule(color = Line)
                        Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Cover(card.cover, card.title, Modifier.width(112.dp).height(150.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(card.title, color = Ink, fontSize = 18.sp, lineHeight = 25.sp,
                                    fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif,
                                    maxLines = 3, overflow = TextOverflow.Ellipsis)
                                if (card.episodeCount > 0) Text("全 ${card.episodeCount} 集", color = Muted, fontSize = 12.sp)
                                card.summary.takeIf { it.isNotBlank() }?.let {
                                    Text(it, color = Muted, fontSize = 12.sp, lineHeight = 18.sp,
                                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                                Surface(onClick = { model.playDrama(card) }, color = Orange, shape = RoundedCornerShape(50),
                                    modifier = Modifier.heightIn(min = 36.dp).testTag("playbill_featured_play")) {
                                    Row(Modifier.padding(horizontal = 18.dp, vertical = 7.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Icon(Icons.Outlined.PlayArrow, null, tint = OnOrange, modifier = Modifier.size(16.dp))
                                        Text("开演", color = OnOrange, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Serif)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (program.isNotEmpty()) item { PlaybillSectionRule("今日剧目") }
        itemsIndexed(program, key = { _, card -> card.id }) { index, card ->
            PlaybillEntry(index + 1, card, "playbill_program_${index + 1}",
                modifier = Modifier.animateItem()) { model.openDrama(card) }
        }
        state.home.error?.let { error -> item {
            FailurePanel(error, if (items.isNotEmpty() && state.home.hasMore) model::loadMoreHome else model::refreshHome,
                "playbill_retry")
        } }
        if (state.home.hasMore && items.isNotEmpty() && state.home.error == null) item {
            LoadAheadFooter("playbill_load_more")
        }
        if (!state.home.loading && !state.home.hasMore && items.isNotEmpty()) item {
            Text("—— 场 刊 终 ——", Modifier.fillMaxWidth().padding(vertical = 6.dp),
                color = Muted, fontSize = 12.sp, fontFamily = FontFamily.Serif)
        }
    }
}

@Composable
private fun PlaybillChip(label: String, selected: Boolean, tag: String, onClick: () -> Unit) {
    Text(label, color = if (selected) AccentText else Muted,
        fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        modifier = Modifier.clip(RoundedCornerShape(50))
            .border(1.dp, if (selected) Orange else Line, RoundedCornerShape(50))
            .clickable { onClick() }.padding(horizontal = 12.dp, vertical = 7.dp)
            .testTag(tag))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlaybillTheater(state: HongguoUiState, model: HongguoViewModel) {
    var showFilters by rememberSaveable { mutableStateOf(false) }
    val items = state.category.items
    val list = rememberLazyListState()
    LoadAheadEffect(state.category, { list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 },
        { list.layoutInfo.totalItemsCount }, state.browseDraft == state.browseApplied, model::loadMoreBrowse)
    LazyColumn(Modifier.fillMaxSize().testTag("playbill_theater"), state = list,
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 20.dp).plusNavClearance(),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("排片厅", color = Ink, fontSize = 26.sp, fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Serif, modifier = Modifier.testTag("playbill_theater_title"))
                    Text("按厅入座 · 各有各的好戏", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
                }
                RankEntryButton(model, "playbill_theater_rank")
                IconButton(onClick = { model.selectTab(AppTab.SEARCH) }, modifier = Modifier.testTag("playbill_theater_search")) {
                    Icon(Icons.Outlined.Search, "搜索", tint = Ink)
                }
            }
        }
        item { DoubleRule() }
        item {
            // 剧种分厅：短剧 / 漫剧 / AI 剧。
            val tabs = rememberTabIndicator()
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Cream).padding(horizontal = 4.dp)
                .slidingPill(tabs, state.browseDraft.genre, CardSurface, corner = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("short_play" to "短剧", "comic_series" to "漫剧", "ai_series" to "AI 剧").forEach { (genre, label) ->
                    val selected = state.browseDraft.genre == genre
                    val color by animatedTabColor(selected, Ink, Muted)
                    Box(Modifier.weight(1f).tabAnchor(tabs, genre).quietSelectable(selected) { model.changeGenre(genre) }
                        .heightIn(min = 38.dp).testTag("playbill_genre_$genre"), contentAlignment = Alignment.Center) {
                        Text(label, color = color, fontSize = 14.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
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
                PlaybillChip(if (count > 0) "筛选 · $count" else "全部筛选", count > 0, "playbill_filters") { showFilters = true }
                group?.options?.filter { it.id.isNotBlank() }?.take(12)?.forEach { option ->
                    val on = state.browseApplied.options[group.key] == option.id
                    PlaybillChip(option.label, on, "playbill_quick_${group.key}_${option.id}") {
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
                Text("正在排片…", Modifier.padding(start = 12.dp), color = Muted, fontSize = 14.sp)
            }
        }
        if (state.category.loaded && !state.category.loading && items.isEmpty() && state.category.error == null)
            item { EmptyPrompt("这一厅暂时没有剧目", "试试减少筛选条件，或换一厅。") }
        itemsIndexed(items, key = { _, card -> card.id }) { index, card ->
            PlaybillEntry(index, card, "playbill_theater_$index", posterWidth = 78, posterHeight = 104,
                modifier = Modifier.animateItem(),
                trailing = {
                    Surface(onClick = { model.playDrama(card) }, shape = RoundedCornerShape(50), color = Orange,
                        modifier = Modifier.heightIn(min = 32.dp).testTag("playbill_theater_play_$index")) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            Icon(Icons.Outlined.PlayArrow, null, tint = OnOrange, modifier = Modifier.size(15.dp))
                            Text("开演", color = OnOrange, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Serif)
                        }
                    }
                }) { model.openDrama(card) }
        }
        state.category.error?.let { error -> item {
            FailurePanel(error, if (items.isNotEmpty() && state.category.hasMore) model::loadMoreBrowse else model::applyBrowse,
                "playbill_theater_retry")
        } }
        if (state.category.hasMore && items.isNotEmpty() && state.category.error == null) item {
            LoadAheadFooter("playbill_theater_more")
        }
        if (!state.category.loading && !state.category.hasMore && items.isNotEmpty()) item {
            Text("—— 本 厅 售 罄 ——", Modifier.fillMaxWidth().padding(vertical = 6.dp),
                color = Muted, fontSize = 12.sp, fontFamily = FontFamily.Serif)
        }
    }
    if (showFilters) ModalBottomSheet(onDismissRequest = { model.discardBrowseDraft(); showFilters = false },
        containerColor = CardSurface) {
        FilterSheetContent(state, model) { showFilters = false }
    }
}

@Composable
internal fun PlaybillLibrary(state: HongguoUiState, model: HongguoViewModel, accountModel: AccountViewModel) {
    val history = state.tab == AppTab.HISTORY
    var editing by rememberSaveable(state.tab, state.libraryCloud) { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().testTag("playbill_library")) {
        Column(Modifier.padding(horizontal = 18.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text("剧目单", color = Ink, fontSize = 26.sp, fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Serif, modifier = Modifier.testTag("playbill_library_title"))
                Text("我 的 戏 折 子", color = Muted, fontSize = 11.sp)
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
                listOf(Triple("观演记录", !state.libraryCloud && history, "playbill_tab_history"),
                    Triple("收藏剧目", !state.libraryCloud && !history, "playbill_tab_favorites"),
                    Triple("账号云片单", state.libraryCloud, "playbill_tab_account")).forEachIndexed { index, (label, selected, tag) ->
                    val color by animatedTabColor(selected, Ink, Muted)
                    Text(label, color = color, fontSize = animatedTabSize(selected, 15.sp, 13.sp),
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
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
                if (!state.libraryCloud && entries.isNotEmpty()) TextButton(onClick = { editing = !editing },
                    modifier = Modifier.testTag("playbill_library_edit")) { Text(if (editing) "收好" else "整理", color = Muted) }
            }
            DoubleRule()
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
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Cream)
                    .clickable { model.selectLibrarySource(true) }.padding(horizontal = 12.dp, vertical = 10.dp)
                    .testTag("playbill_library_cloud_hint"),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Outlined.CloudQueue, null, tint = AccentInk, modifier = Modifier.size(18.dp))
                    Text("登录红果账号，手机上的追剧记录也能接着看", color = Muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    Icon(Icons.Outlined.ChevronRight, null, tint = Muted, modifier = Modifier.size(16.dp))
                }
            }
            if (entries.isEmpty()) item {
                EmptyPrompt(if (history) "场刊上还没有你的观演记录" else "剧目单还空着",
                    if (history) "开始播放后，这里会记住集数和进度。" else "在场刊内页点收藏，喜欢的故事就会写进折子。")
            }
            itemsIndexed(entries, key = { _, entry -> entry.card.id }) { index, entry ->
                Box(Modifier.fillMaxWidth()) {
                    PlaybillEntry(index, entry.card, "playbill_entry_${entry.card.id}", posterWidth = 62, posterHeight = 83) {
                        model.openLibrary(entry)
                    }
                    if (history) {
                        Column(Modifier.align(Alignment.BottomStart).padding(start = 36.dp, bottom = 10.dp)) {
                            Text("第 ${entry.episodeIndex.coerceAtLeast(1)} 集", color = Muted, fontSize = 11.sp)
                            WatchProgress(entry, track = Line, fill = Orange, modifier = Modifier.width(120.dp))
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
internal fun PlaybillDetail(state: HongguoUiState, model: HongguoViewModel, accountModel: AccountViewModel) {
    val card = state.selectedCard ?: return
    val detail = state.detail
    val favorite = state.favorites.any { it.card.id == card.id }
    Column(Modifier.fillMaxSize().testTag("playbill_detail").background(Canvas)) {
        Row(Modifier.fillMaxWidth().padding(start = 6.dp, end = 18.dp, top = 6.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IconButton(onClick = model::closeDetail, modifier = Modifier.testTag("detail_back")) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回", tint = Ink)
            }
            Text("场刊内页", color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Serif, modifier = Modifier.weight(1f))
            Text("红果场刊 · 第 " + LocalDate.now().format(DateTimeFormatter.ofPattern("M月d日", Locale.CHINA)) + " 期",
                color = Muted, fontSize = 10.sp)
        }
        LazyColumn(Modifier.fillMaxWidth().weight(1f).testTag("playbill_detail_list"),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Column {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Cover(detail?.cover ?: card.cover, card.title, Modifier.width(118.dp).height(157.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("今晚剧目", color = AccentText, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Serif)
                            Text(detail?.title ?: card.title, color = Ink, fontSize = 20.sp, lineHeight = 28.sp,
                                fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif,
                                maxLines = 3, overflow = TextOverflow.Ellipsis)
                            Text(detail?.let { "全 ${it.episodes.size} 集" }
                                ?: if (card.episodeCount > 0) "全 ${card.episodeCount} 集" else "正在读取剧集信息",
                                color = Muted, fontSize = 12.sp)
                        }
                    }
                    DoubleRule(Modifier.padding(top = 12.dp))
                }
            }
            item {
                FavoritePair(card, favorite, { model.toggleFavorite(card) }, accountModel, onSignIn = { model.selectTab(AppTab.ACCOUNT) })
            }
            if (state.loadingDetail) item {
                Row(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text("正在印制场刊…", Modifier.padding(start = 12.dp), color = Muted, fontSize = 14.sp)
                }
            }
            if (state.detailError != null) item { FailurePanel(state.detailError, model::retryDetails) }
            if (detail != null) {
                if (detail.summary.isNotBlank()) item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("剧情提要", color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Serif)
                        Text(detail.summary, color = Muted, fontSize = 13.sp, lineHeight = 22.sp)
                    }
                }
                item { RelatedWorksSection(state, model, accountModel) }
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("场次 · 选集", color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Serif)
                        Text(if (state.autoPlayNext) "自动连播已开启" else "自动连播已关闭", color = Muted, fontSize = 11.sp)
                    }
                }
                items((detail.episodes.size + 4) / 5, key = { "playbill_episodes_$it" }) { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        repeat(5) { column ->
                            val index = row * 5 + column
                            val episode = detail.episodes.getOrNull(index)
                            if (episode == null) Spacer(Modifier.weight(1f))
                            else {
                                val selected = state.episodePosition == index
                                Surface(onClick = { model.playEpisode(index) }, modifier = Modifier.weight(1f)
                                    .heightIn(min = 42.dp).testTag("episode_$index"),
                                    shape = RoundedCornerShape(6.dp), color = if (selected) Orange else CardSurface,
                                    border = if (selected) null else androidx.compose.foundation.BorderStroke(1.dp, Line)) {
                                    Box(Modifier.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                                        Text("${episode.index}", fontSize = 13.sp, fontFamily = FontFamily.Serif,
                                            color = if (selected) OnOrange else Ink,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item { Text("—— 场 刊 终 ——", Modifier.fillMaxWidth().padding(vertical = 8.dp),
                color = Muted, fontSize = 11.sp, fontFamily = FontFamily.Serif) }
        }
        if (detail != null && detail.episodes.isNotEmpty()) {
            val episode = detail.episodes.getOrNull(state.episodePosition)?.index ?: detail.episodes.first().index
            Box(Modifier.padding(horizontal = 18.dp, vertical = 10.dp)) {
                Button(onClick = model::openPlayer, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)
                    .testTag("open_player"), shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Orange, contentColor = OnOrange)) {
                    Icon(Icons.Outlined.PlayArrow, contentDescription = null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("开演 · 第 $episode 集", fontSize = 15.sp, fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Serif)
                }
            }
        }
    }
}

/* ---------------------------------------------------------------------------------------------
 * 肆 · 订阅人（我的）: 观演统计、观演记录、偏好折子——同一份 我的 数据，按场刊的排版重述。
 * ------------------------------------------------------------------------------------------- */

private val PlaybillIssueFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA)

/** 订阅人页的期号 = 本月序号 + 当天日期，只做展示，不参与持久化。 */
private fun playbillIssueLine(): String {
    val today = LocalDate.now()
    val issue = today.monthValue
    return "第 $issue 号订阅人 · ${today.format(PlaybillIssueFormatter)}"
}

@Composable
private fun PlaybillStat(label: String, value: String, tag: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val base = Modifier.weight(1f).heightIn(min = 84.dp)
    Surface(onClick = onClick ?: {}, enabled = onClick != null, color = CardSurface,
        shape = RoundedCornerShape(8.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Line),
        modifier = if (onClick != null) base.testTag(tag) else base) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(value, color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif)
            Text(label, color = Muted, fontSize = 11.sp)
        }
    }
}

/** 观演记录里的一天：昨天 / 前天 / 具体日期。 */
private fun playbillDayLine(updatedAt: Long): String {
    if (updatedAt <= 0) return "近日"
    val day = Instant.ofEpochMilli(updatedAt).atZone(ZoneId.systemDefault()).toLocalDate()
    val today = LocalDate.now()
    return when (day) {
        today -> "今天"
        today.minusDays(1) -> "昨天"
        today.minusDays(2) -> "前天"
        else -> day.format(DateTimeFormatter.ofPattern("M月d日", Locale.CHINA))
    }
}

@Composable
private fun PlaybillMine(state: HongguoUiState, model: HongguoViewModel, updateState: UpdateUiState,
                         updateModel: UpdateViewModel, accountName: String?, accountStatus: String,
                         accountAvatar: String?, onAccount: () -> Unit, openSettings: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize().testTag("my_screen").padding(horizontal = 18.dp),
        contentPadding = PaddingValues(0.dp).plusNavClearance(),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            // 报头：订阅人名号 + 期号行，压双栏线。
            Column(Modifier.padding(top = 16.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("订阅人", color = Ink, fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Serif, modifier = Modifier.testTag("playbill_mine_title"))
                    Text("我的 · 观 演 手 册", color = Muted, fontSize = 11.sp)
                }
                Row(Modifier.fillMaxWidth().padding(top = 3.dp, bottom = 7.dp),
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(playbillIssueLine(), color = Muted, fontSize = 11.sp)
                    Text("红果场刊", color = Muted, fontSize = 11.sp)
                }
                DoubleRule()
            }
        }
        item {
            // 订阅人卡：名号 + 状态 + 观演统计。
            Surface(onClick = onAccount, color = CardSurface, shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Line),
                modifier = Modifier.fillMaxWidth().testTag("my_account_card")) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(Modifier.size(56.dp).clip(CircleShape).background(Cream)
                        .border(1.5.dp, Orange, CircleShape), Alignment.Center) {
                        androidx.compose.material3.Text("常", color = AccentText, fontSize = 22.sp,
                            fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif)
                        AccountAvatarImage(accountAvatar)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(accountName ?: "常看场的果友", color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Serif, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(accountStatus, color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
                    }
                    Icon(Icons.Outlined.ChevronRight, null, Modifier.size(20.dp), tint = Muted)
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PlaybillStat("收藏剧目", "${state.favorites.size}", Modifier.weight(1f).testTag("my_favorites")) {
                    model.selectTab(AppTab.LOCAL_FAVORITES)
                }
                PlaybillStat("观演记录", "${state.history.size}", Modifier.weight(1f).testTag("my_history")) {
                    model.openPlayHistory(local = true)
                }
                PlaybillStat("账号云片单", "查阅", Modifier.weight(1f).testTag("my_cloud")) {
                    model.selectTab(AppTab.FAVORITES); model.selectLibrarySource(true)
                }
            }
        }
        state.history.take(3).takeIf { it.isNotEmpty() }?.let { recent ->
            item { PlaybillSectionRule("观演记录") }
            itemsIndexed(recent, key = { _, entry -> "mine_${entry.card.id}" }) { index, entry ->
                PlaybillEntry(index, entry.card, "playbill_mine_history_${entry.card.id}",
                    posterWidth = 46, posterHeight = 61) { model.openLibrary(entry) }
                Text("看到第 ${entry.episodeIndex.coerceAtLeast(1)} 集 · ${playbillDayLine(entry.updatedAt)}",
                    color = Muted, fontSize = 11.sp, modifier = Modifier.padding(start = 34.dp, bottom = 4.dp))
            }
        }
        item { PlaybillSectionRule("场刊偏好") }
        item {
            Surface(color = CardSurface, shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Line)) {
                Column {
                    PlaybillMenuRow("主题样式", "场刊、排片厅与页面风格", "my_settings",
                        trailing = state.themeStyle.label, onClick = openSettings)
                    Box(Modifier.padding(start = 46.dp).height(1.dp).background(Line))
                    PlaybillMenuRow("账号与隐私", "登录方式、云片单与账号管理", "my_account", onClick = onAccount)
                    Box(Modifier.padding(start = 46.dp).height(1.dp).background(Line))
                    PlaybillMenuRow("检查更新",
                        if (updateState.checking) "正在检查新版本…" else "当前版本 ${BuildConfig.VERSION_NAME}",
                        "update_check", busy = updateState.checking) { updateModel.checkForUpdates() }
                }
            }
        }
        item {
            Text("本机收藏与观看记录仅保存在这台设备。", color = Muted, fontSize = 12.sp, lineHeight = 20.sp,
                modifier = Modifier.padding(top = 6.dp))
            Text("红果独立版 ${BuildConfig.VERSION_NAME} · 场刊订阅人 ${playbillIssueLine().substringBefore(" · ")}",
                color = Muted, fontSize = 12.sp, modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 24.dp))
        }
    }
}

/** 偏好折子的一行：编号列 + 标题/副题 + 尾注，压细线分隔。 */
@Composable
private fun PlaybillMenuRow(title: String, subtitle: String, tag: String, icon: ImageVector? = null,
                            trailing: String? = null, busy: Boolean = false, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().testTag(tag).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (icon != null) Box(Modifier.size(32.dp).clip(RoundedCornerShape(6.dp)).background(Cream), Alignment.Center) {
            Icon(icon, null, Modifier.size(18.dp), tint = AccentInk)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif)
            Text(subtitle, color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
        }
        if (trailing != null) Text(trailing, color = Muted, fontSize = 12.sp, maxLines = 1)
        if (busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Muted)
        else Icon(Icons.Outlined.ChevronRight, null, Modifier.size(18.dp), tint = Muted)
    }
}
