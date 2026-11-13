package com.hongguo.android

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest

@Composable
internal fun MyScreen(state: HongguoUiState, model: HongguoViewModel,
                      updateState: UpdateUiState, updateModel: UpdateViewModel,
                      accountName: String? = null, accountStatus: String = "登录后查看手机片单",
                      accountAvatar: String? = null,
                      style: AppThemeStyle = AppThemeStyle.WARM,
                      onAccount: () -> Unit = { model.selectTab(AppTab.ACCOUNT) }) {
    // 设置 and 主题样式 are pages of their own (AppTab.SETTINGS / THEME_STYLE), not states of 我的: leaving and coming
    // back to 我的 always shows 我的.
    val openSettings = { model.selectTab(AppTab.SETTINGS) }
    when (style) {
        AppThemeStyle.IMMERSIVE -> {
            ImmersiveMine(state, model, updateState, updateModel, accountName, accountStatus, accountAvatar, onAccount, openSettings) { model.selectTab(AppTab.THEME_STYLE) }
            return
        }
        AppThemeStyle.CARDS -> {
            CardsMine(state, model, updateState, updateModel, accountName, accountStatus, accountAvatar, onAccount, openSettings)
            return
        }
        // 红果场刊 gets its own 订阅人 page.
        AppThemeStyle.PLAYBILL -> {
            PlaybillMine(state, model, updateState, updateModel, accountName, accountStatus, accountAvatar, onAccount, openSettings)
            return
        }
        AppThemeStyle.WARM -> Unit
    }
    // Each destination has one entry: the stat cards open 收藏 / 历史, the account card opens the account page, and the
    // grouped list below holds the rest. The former header gear and 播放历史 row repeated entries already on screen.
    LazyColumn(Modifier.fillMaxSize().testTag("my_screen").padding(horizontal = 20.dp),
        contentPadding = PaddingValues(0.dp).plusNavClearance(),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("我的", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Ink,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 12.dp))
        }
        item {
            Surface(shape = RoundedCornerShape(16.dp), color = CardSurface) {
                Row(Modifier.fillMaxWidth().testTag("my_account_card").clickable(onClick = onAccount).padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(Cream), Alignment.Center) {
                        Icon(Icons.Outlined.PersonOutline, null, Modifier.size(28.dp), tint = Muted)
                        AccountAvatarImage(accountAvatar)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(accountName ?: "登录红果账号", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(accountStatus, color = Muted, fontSize = 14.sp, lineHeight = 21.sp)
                    }
                    Icon(Icons.Outlined.ChevronRight, null, Modifier.size(20.dp), tint = Muted)
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MyStat("本机收藏", state.favorites.size, Icons.Outlined.StarBorder, Modifier.weight(1f).testTag("my_favorites")) {
                    model.selectTab(AppTab.LOCAL_FAVORITES)
                }
                MyStat("本机看过", state.history.size, Icons.Outlined.History, Modifier.weight(1f).testTag("my_history")) {
                    model.openPlayHistory(local = true)
                }
            }
        }
        item {
            MyServiceMenu("主题样式、自动连播、预加载与更新", state.themeStyle.label, updateState, updateModel, onAccount, openSettings)
        }
        item {
            Text("本机收藏与观看记录仅保存在这台设备。", color = Muted, fontSize = 12.sp, lineHeight = 20.sp,
                modifier = Modifier.padding(top = 8.dp))
            Text("红果独立版 ${BuildConfig.VERSION_NAME}", color = Muted, fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 24.dp))
        }
    }
}

@Composable
private fun MyStat(label: String, count: Int, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Surface(modifier = modifier.clickable(onClick = onClick), shape = RoundedCornerShape(16.dp), color = CardSurface) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(icon, null, Modifier.size(16.dp), tint = Muted)
                Text(label, color = Muted, fontSize = 14.sp)
            }
            Text(count.toString(), color = Ink, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** 账号与隐私 · 设置 · 检查更新, the same three rows (with the same tags) in every theme. */
@Composable
private fun MyServiceMenu(settingsHint: String, themeLabel: String, updateState: UpdateUiState, updateModel: UpdateViewModel,
                          onAccount: () -> Unit, openSettings: () -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = CardSurface) {
        Column {
            MyMenuRow("账号与隐私", "登录方式、云片单与账号管理", "my_account", Icons.Outlined.PrivacyTip, onClick = onAccount)
            MenuDivider()
            MyMenuRow("设置", settingsHint, "my_settings", Icons.Outlined.Settings, trailing = themeLabel, onClick = openSettings)
            MenuDivider()
            MyMenuRow("检查更新", if (updateState.checking) "正在检查新版本…" else "当前版本 ${BuildConfig.VERSION_NAME}",
                "update_check", Icons.Outlined.SystemUpdate, busy = updateState.checking) { updateModel.checkForUpdates() }
        }
    }
}

/** Starts under the row text, not under the icon tile. */
@Composable
private fun MenuDivider() = HorizontalDivider(color = Canvas, modifier = Modifier.padding(start = 64.dp, end = 18.dp))

@Composable
private fun MyMenuRow(title: String, subtitle: String, tag: String, icon: ImageVector? = null, trailing: String? = null,
                      busy: Boolean = false, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().testTag(tag).clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (icon != null) Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(SoftOrange), Alignment.Center) {
            Icon(icon, null, Modifier.size(20.dp), tint = AccentInk)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = Muted, fontSize = 12.sp, lineHeight = 19.sp)
        }
        if (trailing != null) Text(trailing, color = Muted, fontSize = 13.sp, maxLines = 1)
        if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Muted)
        else Icon(Icons.Outlined.ChevronRight, null, Modifier.size(20.dp), tint = Muted)
    }
}

@Composable
private fun PageHeader(title: String, backLabel: String, backTag: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack, modifier = Modifier.testTag(backTag)) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, backLabel, tint = Ink)
        }
        Text(title, color = Ink, fontSize = 24.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun SystemSettingsScreen(state: HongguoUiState, model: HongguoViewModel,
                                 updateState: UpdateUiState, updateModel: UpdateViewModel,
                                 onTheme: () -> Unit, onBack: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize().testTag("settings_screen").padding(horizontal = 20.dp),
        contentPadding = PaddingValues(0.dp).plusNavClearance(),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { PageHeader("设置", "返回我的", "settings_back", onBack) }
        item {
            Text("外观", color = Muted, fontSize = 14.sp)
            Surface(shape = RoundedCornerShape(16.dp), color = CardSurface, modifier = Modifier.padding(top = 12.dp)) {
                Column {
                    MyMenuRow("主题样式", "暖白精修、沉浸暗场、内容卡片", "settings_theme",
                        trailing = state.themeStyle.label, onClick = onTheme)
                    HorizontalDivider(color = Canvas, modifier = Modifier.padding(horizontal = 18.dp))
                    NavBarStyleSetting(state.navBarStyle, model::setNavBarStyle)
                }
            }
        }
        item {
            Text("播放偏好", color = Muted, fontSize = 14.sp)
            Surface(shape = RoundedCornerShape(16.dp), color = CardSurface, modifier = Modifier.padding(top = 12.dp)) {
                Column {
                    SettingSwitch("自动连播下一集", "本集结束后继续播放，也可上下滑动切集", state.autoPlayNext,
                        "settings_autoplay", model::setAutoPlayNext)
                    HorizontalDivider(color = Canvas, modifier = Modifier.padding(horizontal = 18.dp))
                    SettingSwitch("下一集预加载", "提前准备少量内容，关闭可节省流量", state.preloadNext,
                        "settings_preload", model::setPreloadNext)
                }
            }
        }
        item {
            Text("软件更新", color = Muted, fontSize = 14.sp)
            Surface(shape = RoundedCornerShape(16.dp), color = CardSurface, modifier = Modifier.padding(top = 12.dp)) {
                SettingSwitch("启动时检查更新", "每次重新启动应用时检查，下载和安装仍需确认", updateState.automaticChecksEnabled,
                    "settings_auto_update", updateModel::setAutomaticChecksEnabled)
            }
        }
        item {
            Text("收藏、播放记录和设置保存在本机。卸载应用或清除应用数据后，这些记录会被移除。",
                color = Muted, fontSize = 12.sp, lineHeight = 21.sp, modifier = Modifier.padding(bottom = 28.dp))
        }
    }
}

@Composable
internal fun ThemeStyleScreen(state: HongguoUiState, model: HongguoViewModel, onBack: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize().testTag("theme_screen").padding(horizontal = 20.dp),
        contentPadding = PaddingValues(0.dp).plusNavClearance(),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { PageHeader("主题样式", "返回设置", "theme_back", onBack) }
        item {
            Text("选择喜欢的界面风格，点选后立即生效。收藏、历史与播放设置不受影响。",
                color = Muted, fontSize = 13.sp, lineHeight = 20.sp)
        }
        item {
            Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AppThemeStyle.entries.forEach { style ->
                    ThemeOption(style, selected = state.themeStyle == style, modifier = Modifier.weight(1f), previewDark = LocalHongguoPalette.current.isDark) {
                        model.setThemeStyle(style)
                    }
                }
            }
        }
        item {
            Surface(shape = RoundedCornerShape(16.dp), color = CardSurface) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(Orange))
                        Text(state.themeStyle.label, color = Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Text(state.themeStyle.summary, color = Muted, fontSize = 13.sp, lineHeight = 20.sp)
                }
            }
        }
        item {
            Surface(shape = RoundedCornerShape(16.dp), color = CardSurface) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("外观模式", color = Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    val current = state.themeMode.orDefaultFor(state.themeStyle)
                    Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeMode.entries.forEach { mode ->
                            val selected = current == mode
                            Box(Modifier.weight(1f).heightIn(min = 44.dp).clip(RoundedCornerShape(12.dp))
                                .background(if (selected) SoftOrange else Cream)
                                .selectable(selected = selected, role = Role.RadioButton) { model.setThemeMode(mode) }
                                .testTag("theme_mode_${mode.key}"), contentAlignment = Alignment.Center) {
                                Text(mode.label, color = if (selected) AccentInk else Muted, fontSize = 14.sp,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                            }
                        }
                    }
                    Text("四套主题都有浅色和深色。选择“跟随系统”后，会随系统自动切换。沉浸暗场的刷剧画面始终保持深色。",
                        color = Muted, fontSize = 12.sp, lineHeight = 19.sp)
                }
            }
        }
        item { Spacer(Modifier.height(28.dp)) }
    }
}

@Composable
internal fun ThemeOption(style: AppThemeStyle, selected: Boolean, modifier: Modifier, previewDark: Boolean? = null, onSelect: () -> Unit) {
    val ring = if (selected) Orange else Color.Transparent
    Column(modifier.clip(RoundedCornerShape(16.dp))
        .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
        .semantics { contentDescription = "${style.label}主题" }
        .testTag("theme_option_${style.key}")
        .border(2.dp, ring, RoundedCornerShape(16.dp))
        .background(CardSurface)
        .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ThemePreview(style, previewDark)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally)) {
            if (selected) Box(Modifier.size(18.dp).clip(CircleShape).background(Orange), Alignment.Center) {
                Icon(Icons.Outlined.Check, null, Modifier.size(13.dp), tint = OnOrange)
            } else Box(Modifier.size(18.dp).padding(1.dp).border(2.dp, Muted, CircleShape))
            // Wraps instead of clipping on narrow screens or large font scales.
            Text(style.label, color = Ink, fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 2, textAlign = TextAlign.Center, modifier = Modifier.weight(1f, fill = false))
        }
    }
}

private val PreviewTones = listOf(Color(0xFF7B3B2C), Color(0xFF8A4F62), Color(0xFF5B4636), Color(0xFF3E4A61),
    Color(0xFF2F5D50), Color(0xFF7A5C2E), Color(0xFF4B3F6B))

/** A miniature of each theme's home screen, always drawn in that theme's own colors. */
@Composable
private fun ThemePreview(style: AppThemeStyle, dark: Boolean?) {
    val p = if (dark == null) style.palette() else style.palette(dark)
    Box(Modifier.fillMaxWidth().aspectRatio(9f / 16f).clip(RoundedCornerShape(10.dp)).background(p.canvas)
        .border(1.dp, p.line, RoundedCornerShape(10.dp))) {
        when (style) {
            AppThemeStyle.IMMERSIVE -> {
                Box(Modifier.fillMaxSize().background(PreviewTones[0]))
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
                    0.4f to Color.Transparent, 1f to Color.Black.copy(alpha = .85f))))
                Column(Modifier.align(Alignment.CenterEnd).padding(end = 5.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(3) { Box(Modifier.size(8.dp).clip(CircleShape).background(Color.White.copy(alpha = .85f))) }
                }
                Column(Modifier.align(Alignment.BottomStart).padding(start = 6.dp, bottom = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Box(Modifier.width(44.dp).height(5.dp).clip(CircleShape).background(Color.White))
                    Box(Modifier.width(30.dp).height(3.dp).clip(CircleShape).background(Color.White.copy(alpha = .6f)))
                }
                Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(12.dp).background(p.canvas))
            }
            AppThemeStyle.WARM -> Column(Modifier.fillMaxSize().padding(5.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.fillMaxWidth().height(7.dp).clip(CircleShape).background(p.cream))
                Box(Modifier.fillMaxWidth().height(26.dp).clip(RoundedCornerShape(4.dp)).background(PreviewTones[5]))
                repeat(2) { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        repeat(3) { col ->
                            Box(Modifier.weight(1f).aspectRatio(.75f).clip(RoundedCornerShape(2.dp))
                                .background(PreviewTones[(row * 3 + col) % PreviewTones.size]))
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                PreviewNav(p)
            }
            AppThemeStyle.CARDS -> Column(Modifier.fillMaxSize().padding(5.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.fillMaxWidth().height(7.dp).clip(CircleShape).background(p.card))
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(p.card).padding(3.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Box(Modifier.fillMaxWidth(.7f).height(3.dp).background(p.accent))
                    Box(Modifier.fillMaxWidth(.85f).height(3.dp).background(p.line))
                    Box(Modifier.fillMaxWidth(.6f).height(3.dp).background(p.line))
                }
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Box(Modifier.fillMaxWidth().weight(1.4f).clip(RoundedCornerShape(3.dp)).background(PreviewTones[5]))
                        Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(3.dp)).background(PreviewTones[2]))
                    }
                    Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(3.dp)).background(PreviewTones[1]))
                        Box(Modifier.fillMaxWidth().weight(1.4f).clip(RoundedCornerShape(3.dp)).background(PreviewTones[6]))
                    }
                }
                PreviewNav(p)
            }
            AppThemeStyle.PLAYBILL -> Column(Modifier.fillMaxSize().padding(5.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(1.dp)).background(p.accent))
                Box(Modifier.fillMaxWidth().height(1.dp).background(p.ink))
                Box(Modifier.fillMaxWidth().height(2.dp).clip(RoundedCornerShape(1.dp)).background(PreviewTones[3]))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Box(Modifier.size(width = 22.dp, height = 30.dp).clip(RoundedCornerShape(2.dp)).background(PreviewTones[5]))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Box(Modifier.fillMaxWidth(.9f).height(4.dp).clip(RoundedCornerShape(1.dp)).background(p.ink))
                        Box(Modifier.fillMaxWidth(.6f).height(3.dp).background(p.line))
                        Box(Modifier.fillMaxWidth(.75f).height(3.dp).background(p.line))
                    }
                }
                repeat(2) { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(when (row) { 0 -> "壹"; else -> "贰" }, color = p.accent, fontSize = 8.sp,
                            fontWeight = FontWeight.Bold)
                        Box(Modifier.size(width = 14.dp, height = 19.dp).clip(RoundedCornerShape(1.dp))
                            .background(PreviewTones[(row + 1) % PreviewTones.size]))
                        Box(Modifier.weight(1f).height(3.dp).background(p.line))
                    }
                }
                Spacer(Modifier.weight(1f))
                PreviewNav(p)
            }
        }
    }
}

@Composable
private fun PreviewNav(p: HongguoPalette) {
    Row(Modifier.fillMaxWidth().height(9.dp).clip(RoundedCornerShape(2.dp)).background(p.card),
        horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        repeat(4) { i -> Box(Modifier.size(width = 6.dp, height = 3.dp).clip(CircleShape)
            .background(if (i == 0) p.accent else p.line)) }
    }
}

/** 底栏样式: two-way choice, applied immediately so the bar below changes while the user watches. */
@Composable
private fun NavBarStyleSetting(selected: NavBarStyle, onSelect: (NavBarStyle) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("底栏样式", color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text("悬浮玻璃：页面在半透明胶囊下滚动，Android 12 及以上有模糊效果", color = Muted, fontSize = 12.sp, lineHeight = 19.sp)
        }
        val pill = rememberTabIndicator()
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Cream).padding(3.dp)
            .slidingPill(pill, selected, CardSurface, corner = 10.dp).selectableGroup()) {
            NavBarStyle.entries.forEach { style ->
                val isSelected = style == selected
                Box(Modifier.weight(1f).tabAnchor(pill, style).heightIn(min = 40.dp).clip(RoundedCornerShape(10.dp))
                    .selectable(selected = isSelected, role = Role.RadioButton) { onSelect(style) }
                    .testTag("settings_navbar_${style.key}"), contentAlignment = Alignment.Center) {
                    Text(style.label, color = if (isSelected) Ink else Muted, fontSize = 14.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun SettingSwitch(title: String, subtitle: String, checked: Boolean, tag: String, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = Muted, fontSize = 12.sp, lineHeight = 20.sp)
        }
        // Off state uses the secondary text color so the control stays visible on every theme.
        Switch(checked = checked, onCheckedChange = onChange, modifier = Modifier.testTag(tag),
            colors = SwitchDefaults.colors(uncheckedThumbColor = Muted, uncheckedBorderColor = Muted,
                uncheckedTrackColor = Cream))
    }
}

/** B · 沉浸暗场: avatar header, four shortcuts and one grouped list. */
@Composable
private fun ImmersiveMine(state: HongguoUiState, model: HongguoViewModel, updateState: UpdateUiState, updateModel: UpdateViewModel,
                          accountName: String?, accountStatus: String, accountAvatar: String?, onAccount: () -> Unit,
                          openSettings: () -> Unit, openTheme: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize().testTag("my_screen").padding(horizontal = 16.dp),
        contentPadding = PaddingValues(0.dp).plusNavClearance(),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 20.dp).clip(RoundedCornerShape(16.dp)).clickable(onClick = onAccount)
                .testTag("my_account_card").padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.size(64.dp).clip(CircleShape).background(Cream), Alignment.Center) {
                    Icon(Icons.Outlined.PersonOutline, null, Modifier.size(32.dp), tint = Ink)
                    AccountAvatarImage(accountAvatar)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(accountName ?: "登录红果账号", color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(accountStatus, color = Muted, fontSize = 13.sp)
                }
                Icon(Icons.Outlined.ChevronRight, null, Modifier.size(22.dp), tint = Muted)
            }
        }
        item {
            Surface(color = CardSurface, shape = RoundedCornerShape(16.dp)) {
                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                    listOf(
                        Triple(Icons.Outlined.History, "历史 ${state.history.size}") { model.openPlayHistory(local = true) },                        Triple(Icons.Outlined.StarBorder, "追剧 ${state.favorites.size}") { model.selectTab(AppTab.LOCAL_FAVORITES) },
                        Triple(Icons.Outlined.CloudQueue, "云片单") { model.selectTab(AppTab.FAVORITES); model.selectLibrarySource(true) },
                        Triple(Icons.Outlined.Palette, "主题", openTheme),
                    ).forEachIndexed { index, (icon, label, action) ->
                        Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable(onClick = action)
                            .heightIn(min = 56.dp).testTag(if (index == 0) "my_history" else "my_shortcut_$index"),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(icon, null, tint = Ink, modifier = Modifier.size(24.dp))
                            Text(label, color = Ink, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
        item { MyServiceMenu("主题、播放与更新", state.themeStyle.label, updateState, updateModel, onAccount, openSettings) }
        item {
            Text("红果独立版 ${BuildConfig.VERSION_NAME} · 本机数据仅保存在这台设备", color = Muted, fontSize = 12.sp,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp))
        }
    }
}

/** C · 内容卡片: compact header, two stat cards and two grouped cards. */
@Composable
private fun CardsMine(state: HongguoUiState, model: HongguoViewModel, updateState: UpdateUiState, updateModel: UpdateViewModel,
                      accountName: String?, accountStatus: String, accountAvatar: String?, onAccount: () -> Unit, openSettings: () -> Unit) {
    val palette = LocalHongguoPalette.current
    // 深色台头在浅色主题用墨色块，深色主题用卡片色：文字/头像颜色跟着切换。
    val headBg = if (palette.isDark) CardSurface else Color(0xFF17181B)
    val headInk = if (palette.isDark) Ink else Color.White
    val headMuted = if (palette.isDark) Muted else Color(0xFF9CA0A8)
    LazyColumn(Modifier.fillMaxSize().testTag("my_screen"),
        contentPadding = PaddingValues(0.dp).plusNavClearance(),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Box(Modifier.fillMaxWidth().background(headBg,
                RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
                .padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 38.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(Modifier.size(56.dp).clip(CircleShape)
                        .background(if (palette.isDark) Cream else Color(0xFF2A2B30))
                        .border(2.dp, if (palette.isDark) Line else Color(0xFF3A3B42), CircleShape), Alignment.Center) {
                        Icon(Icons.Outlined.PersonOutline, null, Modifier.size(26.dp), tint = headMuted)
                        AccountAvatarImage(accountAvatar)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(accountName ?: "未登录", color = headInk, fontSize = 19.sp, fontWeight = FontWeight.Black,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(accountStatus, color = headMuted, fontSize = 12.sp)
                    }
                    Surface(onClick = onAccount, shape = RoundedCornerShape(18.dp), color = Orange,
                        modifier = Modifier.heightIn(min = 36.dp).testTag("my_account_card")) {
                        Text(if (accountName == null) "登录" else "账号", color = OnOrange, fontSize = 13.sp,
                            fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp))
                    }
                }
            }
        }
        item {
            // 磁贴三联浮出台头下沿；上移后下一节自然顶上来，间距由 offset 补偿。
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).offset(y = (-20).dp),
                horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                MineTile("看过", "${state.history.size}", "my_history", Modifier.weight(1f)) {
                    model.openPlayHistory(local = true)
                }
                MineTile("收藏", "${state.favorites.size}", "my_favorites", Modifier.weight(1f)) {
                    model.selectTab(AppTab.LOCAL_FAVORITES)
                }
                MineTile("云片单", "云", "my_cloud", Modifier.weight(1f)) {
                    model.selectTab(AppTab.FAVORITES); model.selectLibrarySource(true)
                }
            }
        }
        if (state.history.isNotEmpty()) item {
            Column(Modifier.offset(y = (-8).dp)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("接着看", color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Black)
                    Text("全部 ›", color = Muted, fontSize = 11.sp,
                        modifier = Modifier.clickable { model.openPlayHistory(local = true) }.padding(4.dp))
                }
                LazyRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(9.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp)) {
                    items(state.history.take(8), key = { "cards_mine_${it.card.id}" }) { entry ->
                        Column(Modifier.width(92.dp).clickable { model.openLibrary(entry) }
                            .testTag("cards_mine_entry_${entry.card.id}")) {
                            Box(Modifier.fillMaxWidth().aspectRatio(.72f).clip(RoundedCornerShape(8.dp))) {
                                PosterImage(entry.card.cover, Modifier.fillMaxSize())
                            }
                            Text(entry.card.title, color = Ink, fontSize = 10.sp, maxLines = 1,
                                overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                            WatchProgress(entry, track = Cream, fill = Orange,
                                modifier = Modifier.fillMaxWidth().padding(top = 5.dp))
                        }
                    }
                }
            }
        }
        item {
            Surface(onClick = openSettings, color = CardSurface, shape = RoundedCornerShape(14.dp),
                shadowElevation = 4.dp, modifier = Modifier.padding(horizontal = 16.dp)
                    .offset(y = if (state.history.isEmpty()) 0.dp else (-8).dp)) {
                MyMenuRow("设置", "主题样式、播放与更新", "my_settings", trailing = state.themeStyle.label, onClick = openSettings)
            }
        }
        item {
            Surface(color = CardSurface, shape = RoundedCornerShape(14.dp), shadowElevation = 4.dp,
                modifier = Modifier.padding(horizontal = 16.dp)) {
                Column {
                    MyMenuRow("账号与隐私", "登录方式、云片单与账号管理", "my_account", onClick = onAccount)
                    MenuDivider()
                    MyMenuRow("检查更新", if (updateState.checking) "正在检查新版本…" else "当前版本 ${BuildConfig.VERSION_NAME}",
                        "update_check", busy = updateState.checking) { updateModel.checkForUpdates() }
                }
            }
        }
        item {
            Text("本机收藏与观看记录仅保存在这台设备。", color = Muted, fontSize = 12.sp,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(start = 0.dp, bottom = 24.dp))
        }
    }
}

/** Account avatar over its placeholder; the placeholder stays while loading, without an avatar, or when the image fails. */
@Composable
private fun MineTile(label: String, value: String, tag: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, color = CardSurface, shape = RoundedCornerShape(14.dp),
        shadowElevation = 4.dp, modifier = modifier.testTag(tag)) {
        Column(Modifier.fillMaxWidth().padding(vertical = 13.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(value, color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Black)
            Text(label, color = Muted, fontSize = 11.sp)
        }
    }
}

@Composable
internal fun AccountAvatarImage(url: String?) {
    if (url.isNullOrBlank()) return
    AsyncImage(model = ImageRequest.Builder(LocalContext.current).data(url).crossfade(true).build(),
        contentDescription = "账号头像", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
}
