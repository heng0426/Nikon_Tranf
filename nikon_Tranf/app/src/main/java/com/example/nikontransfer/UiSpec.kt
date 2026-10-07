package com.example.nikontransfer

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

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
 * ===================================================================== */

object UiSpec {

    /* ---------- 动画三档（ms） ---------- */
    const val QUICK = 160   // 快速动画：消失/收起/退出/跟手回弹
    const val STANDARD = 220 // 标准动画：出现/展开/状态切换/颜色过渡
    const val EMPHASIS = 320 // 强调动画：卡片变形/大位移/页面级转场

    /* ---------- 圆角双档 ---------- */
    val ROUND_SMALL = 10.dp // 按钮/胶囊/徽章/内框/列表项
    val ROUND_LARGE = 14.dp // 卡片/操作条/信息卡/容器

    /* ---------- 主色（浅/深） ---------- */
    /** 主青绿：文字/图标/进度/描边/下载按钮 */
    fun accent(dark: Boolean) = if (dark) Color(0xFF4DB6AC) else Color(0xFF00695C)  

    /** 深一档青绿：实底徽章/选中 chip/下载完成底 */
    fun accentDeep(dark: Boolean) = if (dark) Color(0xFF00796B) else Color(0xFF00695C)

    /** 滚动条拖块选中色 */
    fun thumbSel(dark: Boolean) = if (dark) Color(0xFF80CBC4) else Color(0xFF00897B)

    /* ---------- 状态色（浅/深） ---------- */
    /** 断开/失败 */
    fun error(dark: Boolean) = if (dark) Color(0xFFEF9A9A) else Color(0xFFB71C1C)

    /** 连接中/警示 */
    fun amber(dark: Boolean) = if (dark) Color(0xFFFFD54F) else Color(0xFF8D6E00)

    /** USB 通道蓝 */
    fun usbBlue(dark: Boolean) = if (dark) Color(0xFF64B5F6) else Color(0xFF1565C0)

    /* ---------- 顶部连接状态胶囊（已连接：型号/断开按钮那条） ---------- */
    /** 胶囊底色：深色=暗蓝/暗青，浅色=淡蓝/淡青 */
    fun connChipBg(dark: Boolean, usb: Boolean) = when {
        dark && usb -> Color(0xFF101838)    // 深色模式 USB 卡（蓝）
        dark -> Color(0xFF10312D)   // 深色模式 Wi-Fi 卡（青）
        usb -> Color(0xFFE3F2FD)    // USB 卡（蓝）
        else -> Color(0xFFE0F2F1)    // Wi-Fi 卡（青）
    }

    /** 胶囊文字色（同上四态） */
    fun connChipText(dark: Boolean, usb: Boolean) = when {
        dark && usb -> Color(0xFF64B5F6)    // 深色模式 USB 卡（蓝）
        dark -> Color(0xFF4DB6AC)   // 深色模式 Wi-Fi 卡（青）
        usb -> Color(0xFF1565C0)    // USB 卡（蓝）
        else -> Color(0xFF00695C)    // Wi-Fi 卡（青）
    }

    /* ---------- 连接卡片（双卡/重连卡）底与文字 ---------- */
    /** USB 卡（蓝） */
    fun usbSurface(dark: Boolean) = if (dark) Color(0xFF101838) else Color(0xFFE3F2FD)
    fun usbOnSurface(dark: Boolean) = if (dark) Color(0xFFB3D6F7) else Color(0xFF0D47A1)

    /** Wi-Fi 卡（青） */
    fun wifiSurface(dark: Boolean) = if (dark) Color(0xFF10312D) else Color(0xFFE0F2F1)
    fun wifiOnSurface(dark: Boolean) = if (dark) Color(0xFF80CBC4) else Color(0xFF004D40)

    /* ---------- 网格/预览固定色 ---------- */
    /** 多选选中遮罩（叠加在缩略图上） */
    fun selOverlay(dark: Boolean) = if (dark) Color(0x334DB6AC) else Color(0x3300695C)

    /** 已下载圆标底（右下角 ✓ / ✓J / ✓N） */
    fun dlMark(dark: Boolean) = if (dark) Color(0xCC4DB6AC) else Color(0xCC00695C)

    /** 格式徽章：JPG 青 / NEF 紫 */
    val JPG_TEAL = Color(0xFF00695C)
    val NEF_PURPLE = Color(0xFF6A1B9A)

    /* ---------- 中性固定色 ---------- */
    /** 侧板/设置抽屉蒙层 */
    val MASK = Color(0x66000000)

    /** 排队中等中性灰字 */
    val GREY_TEXT = Color(0xFF999999)

    /** 预览缩略图占位：图标 / 文字 */
    val PLACEHOLDER_ICON = Color(0xFFDDDDDD)
    val PLACEHOLDER_TEXT = Color(0xFF777777)

    /* ---------- 布局尺寸 ---------- */
    val PILL_SLOT = 20.dp        // 置顶日期胶囊槽位高（数字越小胶囊下方留白越小）
    val CONN_INFO_H = 48.dp      // 连接信息卡片内容行高（锁高，容两行）
    val QUEUE_ROW_H = 60.dp      // 下载队列行内容行高（锁高，状态切换不跳变）
    val PREVIEW_SLOT_H = 40.dp   // 预览页下载槽行高（锁高，四态垂直居中）
    val CHECK_ICON = 40.dp       // 多选选中态中央勾尺寸
    val QUEUE_THUMB = 44.dp      // 下载队列行左侧缩略图尺寸
    val DRAG_STRIP_W = 24.dp     // 滚动条拖动层宽度（右缘热区）
    val SCROLLBAR_THICKNESS = 9.dp // 滚动条拖块厚度
    val SEL_BORDER_W = 3.dp      // 多选选中态/高亮描边宽

    /* ---------- 行为参数 ---------- */
    const val CHUNK_TRIGGER = 24 // 分块加载：距已加载末尾该项数内自动追加（≈8 行格子）
}
