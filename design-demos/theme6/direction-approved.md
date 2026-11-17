# 定稿：红果 · 集刻度（RAIL）

用户 2026-11-17 指令：「再设计个主题」→ 指定方法论 https://github.com/dickwu/apple-design-skill →「定稿后分下工 直接开工」。

## 方向定稿

**骨架从题材里长出来**：短剧的资料单元不是「剧」，是**集**——80 / 81 / 1000 集，每集 1′48″–2′10″。
现有五套（暖白精修 / 沉浸暗场 / 内容卡片 / 红果场刊 / 红果小报）全部按「剧」组织，没有一套把「集」当骨架。

**签名元素（唯一重音）**：集刻度尺。一条发丝线，M 集 = M 根刻度，已看的 N 集填红，当前集最高。
三个尺度复用：首页主卡 80 根、更新行微刻度、详情页满宽。超过刻度容量时按比例抽样（`每格 N 集`只写进 title，不上屏）。

**Apple craft lens 检查**：skill 点名三种生成式模板脸——暖奶油+衬线+陶土、近黑+单一酸绿/朱红、报纸发丝线密栏。现有五套撞了其中三种，故本套刻意走**中性冷灰底 + 单一品牌红**，红只用于「你在哪 / 现在做什么」。

## Design tokens（已实测算对比度）

| Token | 亮色 | 暗色 | 实测对比度 |
|---|---|---|---|
| surface | `#F3F4F6` | `#0F1113` | — |
| card | `#FFFFFF` | `#17191C` | — |
| ink | `#16181B` | `#F3F4F6` | 16.16:1 / 17.19:1 |
| muted | `#63676D` | `#A0A4AB` | 5.17:1（surface）/ 5.69:1（card）/ 暗 7.56:1 |
| hair | `#E2E4E8` | `#262A2E` | 分隔线，非文本 |
| brand | `#C8102E` | `#E2263A` | 5.35:1 / 6.25:1；白字 on brand 5.88 / 4.59:1 |

字号阶梯（Compose 用 sp，与 CSS px 1:1）：title 17 / listTitle 15 / body 13 / label 12 / button 15 / navLabel 12。
触控：主 CTA 48dp 高、选集格 44×48、底栏项 98×63 —— 均满足 HIG 44×44 与 Android 48dp。

## 四屏结构

1. **今晚**（首页）：继续观看主卡（封面+剧名+进度+刻度+CTA）→ 今晚更新三行（时间/待播 + 微刻度）→ 热度精选三列。
2. **详情**：封面+剧名+元信息 → 满宽刻度（1/20/40/60/80 标尺）→ 选集 6 列 → 简介 → 底部播放 CTA。
3. **追更**：4 部在追，每行微刻度 + 「还剩 N 集 / 约 X 小时 Y 分」。
4. **暗色**：同骨架，只换 palette，不换布局。

真实内容全部取自 App 现有素材（`docs/images/home.png` + `design-demos/posters/*.jpg`）：
《装穷提亲，娶到全家嫌弃的她》80 集 /《灵葫仙缘：葫中藏尽世间米》81 集 /《和离后我带女儿横扫魔域》1000 集 /
《读心嫡女捡了个吃瓜夫君》76 集 /《末世轮回：我以躯化神明》102 集 /《秦医生，搭个伙》64 集 /《装疯嫡女，权倾世子心》90 集。

## 交付物

- `design-demos/theme6/rail.html` —— 四屏总览（含 title 说明）
- `design-demos/theme6/preview-0{1..4}-*.html` —— 1:1 独立预览页（390×844）
- 截图见对话记录

## 落地映射（Compose）

| HTML | Compose |
|---|---|
| `.rail` | `EpisodeRail(total, watched, style)` —— Canvas 绘制，gap 1dp，`flex:1` 等价 `weight(1f)` |
| `.cont` 主卡 | `ContinueWatchingCard` |
| `.upd` 行 | `UpdateRow` |
| `.eps .grid` | 6 列 `LazyVerticalGrid` / 手排 `Row` |
| `.nav` | `AppNavigation` 新增 RAIL 分支 |
| palette | `RailPalette` / `RailDarkPalette` |

新增 `AppThemeStyle.RAIL("rail", "集刻度", "…")`，`palette()` when 加分支，`AppThemeStyleTest` 断言 key 列表补一项。