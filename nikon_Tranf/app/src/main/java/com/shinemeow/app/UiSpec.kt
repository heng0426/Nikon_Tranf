package com.shinemeow.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/* =====================================================================
 *  配色主题：设置·外观可实时切换，切换后所有取色处立即重组生效
 *  （UiSpec 各取色函数/属性内部读 currentTheme，本身是 Compose 状态）
 * ===================================================================== */

/** 主题标识；DEFAULT = 原版青绿（初始值，保证升级后外观不变） */
enum class ThemeKey(val label: String) {
    DEFAULT("青绿（默认）"),
    CAT_ORANGE("橘猫橙"),
    NIKON_YELLOW("尼康黄"),
    GLACIER_BLUE("冰川蓝"),
    LAVENDER("薰衣草紫")
}

/** 一套主题的全部品牌色（浅/深两套；L=浅色模式，D=深色模式） */
data class UiTheme(
    val accentL: Color, val accentD: Color,     // 主色：文字/图标/进度/描边/下载按钮
    val deepL: Color, val deepD: Color,         // 深一档主色：实底徽章/选中 chip/下载完成底
    val thumbL: Color, val thumbD: Color,       // 滚动条拖块选中色
    val errorL: Color, val errorD: Color,       // 断开/失败
    val amberL: Color, val amberD: Color,       // 连接中/警示
    val usbL: Color, val usbD: Color,           // USB 通道色（与主色并置区分双通道）
    val wifiSurfL: Color, val wifiSurfD: Color, // Wi-Fi 卡底（浅/深）
    val wifiOnL: Color, val wifiOnD: Color,     // Wi-Fi 卡文字
    val usbSurfL: Color, val usbSurfD: Color,   // USB 卡底
    val usbOnL: Color, val usbOnD: Color,       // USB 卡文字
    val jpg: Color, val nef: Color              // 格式徽章 JPG / NEF
)

/** 主题注册表：新增主题只需在此加一项，设置页自动出现 */
val UI_THEMES: Map<ThemeKey, UiTheme> = mapOf(
    ThemeKey.DEFAULT to UiTheme(
        accentL = Color(0xFF00695C), accentD = Color(0xFF4DB6AC),
        deepL = Color(0xFF00695C), deepD = Color(0xFF00796B),
        thumbL = Color(0xFF00897B), thumbD = Color(0xFF80CBC4),
        errorL = Color(0xFFB71C1C), errorD = Color(0xFFEF9A9A),
        amberL = Color(0xFF8D6E00), amberD = Color(0xFFFFD54F),
        usbL = Color(0xFF1565C0), usbD = Color(0xFF64B5F6),
        wifiSurfL = Color(0xFFE0F2F1), wifiSurfD = Color(0xFF10312D),
        wifiOnL = Color(0xFF004D40), wifiOnD = Color(0xFF80CBC4),
        usbSurfL = Color(0xFFE3F2FD), usbSurfD = Color(0xFF101838),
        usbOnL = Color(0xFF0D47A1), usbOnD = Color(0xFFB3D6F7),
        jpg = Color(0xFF00695C), nef = Color(0xFF6A1B9A)
    ),
    // 与 logo 橘猫同源：蓝橙互补区分双通道，JPG 徽章转橙
    ThemeKey.CAT_ORANGE to UiTheme(
        accentL = Color(0xFFBF5F17), accentD = Color(0xFFFFA85C),
        deepL = Color(0xFF9A4A0F), deepD = Color(0xFFE8873A),
        thumbL = Color(0xFFD2691E), thumbD = Color(0xFFFFB77A),
        errorL = Color(0xFFB71C1C), errorD = Color(0xFFEF9A9A),
        amberL = Color(0xFF8D6E00), amberD = Color(0xFFFFD54F),
        usbL = Color(0xFF1565C0), usbD = Color(0xFF64B5F6),
        wifiSurfL = Color(0xFFFDEDDC), wifiSurfD = Color(0xFF2E1B0D),
        wifiOnL = Color(0xFF7A3E0A), wifiOnD = Color(0xFFFFC08A),
        usbSurfL = Color(0xFFE3F2FD), usbSurfD = Color(0xFF101838),
        usbOnL = Color(0xFF0D47A1), usbOnD = Color(0xFFB3D6F7),
        jpg = Color(0xFFC96A1B), nef = Color(0xFF6A1B9A)
    ),
    // 尼康黄黑铭牌；「连接中」改橙避让主黄；浅色模式主色压暗保可读
    ThemeKey.NIKON_YELLOW to UiTheme(
        accentL = Color(0xFF8F7100), accentD = Color(0xFFFFE100),
        deepL = Color(0xFF6E5700), deepD = Color(0xFFF0C400),
        thumbL = Color(0xFFB28900), thumbD = Color(0xFFFFE95C),
        errorL = Color(0xFFB71C1C), errorD = Color(0xFFEF9A9A),
        amberL = Color(0xFFC43E00), amberD = Color(0xFFFF8A50),
        usbL = Color(0xFF1565C0), usbD = Color(0xFF64B5F6),
        wifiSurfL = Color(0xFFFAF3CD), wifiSurfD = Color(0xFF26200A),
        wifiOnL = Color(0xFF5C4800), wifiOnD = Color(0xFFFFE95C),
        usbSurfL = Color(0xFFE3F2FD), usbSurfD = Color(0xFF101838),
        usbOnL = Color(0xFF0D47A1), usbOnD = Color(0xFFB3D6F7),
        jpg = Color(0xFFA88500), nef = Color(0xFF6A1B9A)
    ),
    // 冷调清透；USB 改青绿与主蓝互换，双通道区分度不变
    ThemeKey.GLACIER_BLUE to UiTheme(
        accentL = Color(0xFF0277BD), accentD = Color(0xFF4FC3F7),
        deepL = Color(0xFF01579B), deepD = Color(0xFF29B6F6),
        thumbL = Color(0xFF0288D1), thumbD = Color(0xFF81D4FA),
        errorL = Color(0xFFB71C1C), errorD = Color(0xFFEF9A9A),
        amberL = Color(0xFF8D6E00), amberD = Color(0xFFFFD54F),
        usbL = Color(0xFF00695C), usbD = Color(0xFF4DB6AC),
        wifiSurfL = Color(0xFFE1F0FA), wifiSurfD = Color(0xFF0D1E2E),
        wifiOnL = Color(0xFF01426B), wifiOnD = Color(0xFF81D4FA),
        usbSurfL = Color(0xFFE0F2F1), usbSurfD = Color(0xFF10312D),
        usbOnL = Color(0xFF004D40), usbOnD = Color(0xFF80CBC4),
        jpg = Color(0xFF0277BD), nef = Color(0xFF6A1B9A)
    ),
    // 尼康 Z 系宣传色；USB 改青区分，JPG 徽章转青
    ThemeKey.LAVENDER to UiTheme(
        accentL = Color(0xFF5E35B1), accentD = Color(0xFFB39DDB),
        deepL = Color(0xFF4527A0), deepD = Color(0xFF9575CD),
        thumbL = Color(0xFF6A4FB6), thumbD = Color(0xFFC7B3E8),
        errorL = Color(0xFFB71C1C), errorD = Color(0xFFEF9A9A),
        amberL = Color(0xFF8D6E00), amberD = Color(0xFFFFD54F),
        usbL = Color(0xFF00695C), usbD = Color(0xFF4DB6AC),
        wifiSurfL = Color(0xFFEDE7F8), wifiSurfD = Color(0xFF221A38),
        wifiOnL = Color(0xFF3E2A72), wifiOnD = Color(0xFFC7B3E8),
        usbSurfL = Color(0xFFE0F2F1), usbSurfD = Color(0xFF10312D),
        usbOnL = Color(0xFF004D40), usbOnD = Color(0xFF80CBC4),
        jpg = Color(0xFF00897B), nef = Color(0xFF6A1B9A)
    )
)

/* =====================================================================
 *  UiSpec — 全局可调参数中心
 *  ---------------------------------------------------------------------
 *  所有跨界面复用的"设计令牌"集中在这里：改这里 = 全 App 生效。
 *  新代码一律取令牌引用，不要写散值；一次性局部 padding 可留在原地。
 *
 *  ▍动画三档（tween 默认曲线即 FastOutSlowIn，无需显式传 easing）
 *    QUICK    消失/收起/退出/跟手回弹
 *    STANDARD 出现/展开/状态切换/颜色过渡
 *    EMPHASIS 卡片变形/大位移/页面级转场
 *  ▍圆角双档
 *    ROUND_SMALL 按钮/胶囊/徽章/内框/列表项
 *    ROUND_LARGE 卡片/操作条/信息卡/容器
 *  ▍颜色分两类
 *    函数型：浅/深两套配色按 dark 参数取值（调用处传 vm.darkModeOn）
 *    常量型：不随深色模式变化的固定色（蒙层/徽章/占位符等）
 *  ▍毛玻璃（GLASS_*）：弹层模糊/着色/描边/阴影统一规格，调观感只动这一区
 * ===================================================================== */

object UiSpec {

    /** 当前主题（Compose 状态）：设置·外观切换后，所有读取取色函数/属性的
     *  组合作用域自动重组 → 实时生效，无需重启。持久化见 vm.setTheme()。 */
    var currentTheme: ThemeKey by mutableStateOf(ThemeKey.DEFAULT)

    /** 当前主题色表（读状态 → 组合内读取自动订阅） */
    private val T: UiTheme get() = UI_THEMES[currentTheme] ?: UI_THEMES.getValue(ThemeKey.DEFAULT)

    /* ---------- 动画三档（ms） ---------- */
    const val QUICK = 160   // 快速动画：消失/收起/退出/跟手回弹
    const val STANDARD = 220 // 标准动画：出现/展开/状态切换/颜色过渡
    const val EMPHASIS = 320 // 强调动画：卡片变形/大位移/页面级转场

    /* ---------- 圆角双档 ---------- */
    val ROUND_SMALL = 10.dp // 按钮/胶囊/徽章/内框/列表项
    val ROUND_LARGE = 14.dp // 卡片/操作条/信息卡/容器

    /* ---------- 主色（浅/深） ---------- */
    /** 主青绿：文字/图标/进度/描边/下载按钮 */
    fun accent(dark: Boolean) = if (dark) T.accentD else T.accentL

    /** 深一档主色：实底徽章/选中 chip/下载完成底 */
    fun accentDeep(dark: Boolean) = if (dark) T.deepD else T.deepL

    /** 滚动条拖块选中色 */
    fun thumbSel(dark: Boolean) = if (dark) T.thumbD else T.thumbL

    /* ---------- 状态色（浅/深） ---------- */
    /** 断开/失败 */
    fun error(dark: Boolean) = if (dark) T.errorD else T.errorL

    /** 连接中/警示 */
    fun amber(dark: Boolean) = if (dark) T.amberD else T.amberL

    /** USB 通道色（与主色并置，区分 USB / Wi-Fi 双通道） */
    fun usbBlue(dark: Boolean) = if (dark) T.usbD else T.usbL

    /* ---------- 顶部连接状态胶囊（已连接：型号/断开按钮那条） ---------- */
    /** 胶囊底色 = 对应通道卡底（深色=暗蓝/暗青，浅色=淡蓝/淡青） */
    fun connChipBg(dark: Boolean, usb: Boolean) = if (usb) usbSurface(dark) else wifiSurface(dark)

    /** 胶囊文字色 = 对应通道色（USB=通道色，Wi-Fi=主色） */
    fun connChipText(dark: Boolean, usb: Boolean) = if (usb) usbBlue(dark) else accent(dark)

    /* ---------- 连接卡片（双卡/重连卡）底与文字 ---------- */
    /** USB 卡 */
    fun usbSurface(dark: Boolean) = if (dark) T.usbSurfD else T.usbSurfL
    fun usbOnSurface(dark: Boolean) = if (dark) T.usbOnD else T.usbOnL

    /** Wi-Fi 卡 */
    fun wifiSurface(dark: Boolean) = if (dark) T.wifiSurfD else T.wifiSurfL
    fun wifiOnSurface(dark: Boolean) = if (dark) T.wifiOnD else T.wifiOnL

    /* ---------- 网格/预览固定色（由主色派生，随主题变化）---------- */
    /** 多选选中遮罩（叠加在缩略图上，主色 20% 透明） */
    fun selOverlay(dark: Boolean) = (if (dark) T.accentD else T.accentL).copy(alpha = 0.20f)

    /** 已下载圆标底（右下角 ✓ / ✓J / ✓N，主色 80% 不透明） */
    fun dlMark(dark: Boolean) = (if (dark) T.accentD else T.accentL).copy(alpha = 0.80f)

    /** 格式徽章色（属性形式保留旧调用点；内部读当前主题 → 切换即生效） */
    val JPG_TEAL: Color get() = T.jpg
    val NEF_PURPLE: Color get() = T.nef

    /* ---------- 中性固定色 ---------- */
    /** 侧板/设置抽屉蒙层 */
    val MASK = Color(0x66000000)

    /** 排队中等中性灰字 */
    val GREY_TEXT = Color(0xFF999999)

    /** 预览缩略图占位：图标 / 文字 */
    val PLACEHOLDER_ICON = Color(0xFFDDDDDD)
    val PLACEHOLDER_TEXT = Color(0xFF777777)

    /* ---------- 布局尺寸 ---------- */
    val TOPBAR_GAP = 8.dp       // 状态栏到顶栏余量的默认值：实际值 = 设置·外观「顶栏上边距」（vm.topBarGapDp，0~32 可调，三页共用；状态栏高度由 safeDrawing 自动吃掉）
    val PAGE_PAD = 16.dp         // 页面水平/底部边距：主页/下载队列/设置三页根容器共用
    val PILL_SLOT = 24.dp        // 置顶日期胶囊槽位高（数字越小胶囊下方留白越小）
    val TOPBAR_BTN = 34.dp       // 顶栏圆角按钮容器（返回/筛选/齿轮/反转/合并/下载按钮）
    val TOPBAR_BTN_GAP = 10.dp   // 顶栏按钮之间/按钮与标题之间的间距（主页图标组、下载队列与设置返回行、队列动作区共用）
    val ICON_STD = 20.dp         // 标准图标：自绘图标画布 / 双卡连接图标 / 顶栏按钮内图标
    val ICON_SM = 18.dp          // 行内小图标：胶囊下拉箭头 / 队列行重试·取消 / 预览行图标
    val PILL_HPAD = 10.dp        // 信息胶囊水平内边距（置顶日期/张数/节头胶囊共用）
    val PILL_VPAD = 4.dp         // 信息胶囊垂直内边距（同上，气泡高度 = 字高 + 2×此值）
    val PILL_GAP = 6.dp          // 置顶槽内两个气泡（日期+张数）的间距
    val ACTION_HPAD = 12.dp      // 描边动作胶囊（ActionPill/两段式确认）水平内边距
    val ACTION_VPAD = 6.dp       // 描边动作胶囊垂直内边距
    val CONN_INFO_H = 48.dp      // 连接信息卡片内容行高（锁高，容两行）
    val QUEUE_ROW_H = 60.dp      // 下载队列行内容行高（锁高，状态切换不跳变）
    val PREVIEW_SLOT_H = 40.dp   // 预览页下载槽行高（锁高，四态垂直居中）
    val CHECK_ICON = 40.dp       // 多选选中态中央勾尺寸
    val QUEUE_THUMB = 44.dp      // 下载队列行左侧缩略图尺寸
    /* [SCROLLBAR-SEALED] 快速滚动条封存：两枚尺寸令牌一并停用，重启时随标记恢复
    val DRAG_STRIP_W = 24.dp     // 滚动条拖动层宽度（右缘热区）
    val SCROLLBAR_THICKNESS = 9.dp // 滚动条拖块厚度
    [/SCROLLBAR-SEALED] */
    val SEL_BORDER_W = 3.dp      // 多选选中态/高亮描边宽

    /* ---------- 透明度 ---------- */
    val OUTLINE_ALPHA = 0.45f    // 描边胶囊描边强度（ActionPill / 两段式确认未armed态）
    val PILL_SOLID = 0.95f       // 信息胶囊正常底透明度（surfaceVariant 上）
    val PILL_DIM = 0.55f         // 信息胶囊折叠/淡化态底透明度
    val PILL_TINT = 0.14f        // 状态胶囊淡底强度（已完成绿/总数量amber/排队中primary 的浅色填充）
    val DONE_FILL_ALPHA = 0.20f  // 队列行/总览卡下载完成绿色填充

    /* ---------- 毛玻璃（弹层玻璃统一规格，glassBackdropModifier / 删除气泡 / 预览背景消费） ----------
     *  调玻璃观感只动这一区：模糊半径、着色、描边、阴影。
     *  结构 = 模糊像（主内容离屏录制 + BlurEffect）→ outline 裁剪 → 着色 → 描边 → 系统阴影。 */
    /** 模糊半径 px（BlurEffect 横纵同值）：弹窗/筛选面板/多选卡片标准档。调大更雾、文字更稳 */
    const val GLASS_BLUR = 40f
    /** 模糊半径轻档：删除成功气泡 / 预览页背景虚化（小面积、需保留内容轮廓辨识） */
    const val GLASS_BLUR_SOFT = 24f
    /** 弹层着色（叠在模糊像上的半透明色，统一明暗保证文字对比）：深=黑48%，浅=白70% */
    fun glassTint(dark: Boolean) = if (dark) Color(0x7A000000) else Color(0xB3FFFFFF)
    /** 弹层描边（边缘 1dp 一圈，玻璃边界的视觉暗示）：深=白14%，浅=黑10% */
    fun glassBorder(dark: Boolean) = if (dark) Color(0x24FFFFFF) else Color(0x1A000000)
    /** 弹层投影高度（Modifier.shadow，玻璃卡片悬浮感；多选浮层卡片按用户要求不带阴影） */
    val GLASS_SHADOW = 8.dp
    /** 删除成功气泡着色（小面积轻一档：深=黑33%，浅=白55%） */
    fun bubbleTint(dark: Boolean) = if (dark) Color(0x54000000) else Color(0x8CFFFFFF)

    /* ---------- 迷你徽章字号 ---------- */
    val BADGE_FONT = 8.sp        // 文字徽章：顶栏「短⇆长」反转徽章 / 设置页类型小徽章
    val TYPE_BADGE_FONT = 7.sp   // 顶栏合并按钮 JPG/NEF 类型徽章（更小一档）

    /* ---------- 行为参数 ---------- */
    const val CHUNK_TRIGGER = 24 // 分块加载：距已加载末尾该项数内自动追加（≈8 行格子）

    /** 下载进度写入节流（ms）：got 是可观察状态，写入即唤醒观察者。
     *  USB 整包模式每 64KB 回调一次（50MB ≈ 800 次），不节流会让下载页整页重组。
     *  250ms 与速度刷新同频，配合 500ms 线性补间，进度条能完整跑完动画。 */
    var PROGRESS_THROTTLE_MS = 250L
}
