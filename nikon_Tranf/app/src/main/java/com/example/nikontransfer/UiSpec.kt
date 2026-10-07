package com.example.nikontransfer

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** 全局 UI 规范：动画三档节奏 + 圆角双档。新代码一律取档引用，不写散值。
 *
 *  动画语义：「消失/收起」用 QUICK、「出现/展开/状态切换」用 STANDARD、
 *  「卡片变形/大位移/转场」用 EMPHASIS —— 保证消失比出现快的直觉。
 *  tween 默认曲线即 FastOutSlowInEasing，无需显式传 easing。 */
object UiSpec {
    /* ---------- 动画三档（ms） ---------- */
    const val QUICK = 160        // 消失/收起/退出/跟手回弹
    const val STANDARD = 220     // 出现/展开/状态切换/颜色过渡
    const val EMPHASIS = 320     // 卡片变形/大位移/页面级过渡

    /* ---------- 圆角双档 ---------- */
    val ROUND_SMALL = 10.dp      // 按钮/胶囊/徽章/内框/列表项
    val ROUND_LARGE = 14.dp      // 卡片/操作条/信息卡/容器

    /* ---------- 主题色（浅/深成对，按 vm.darkModeOn 取值）---------- */
    fun accent(dark: Boolean) = if (dark) Color(0xFF4DB6AC) else Color(0xFF00695C)      // 主青绿：文字/图标/进度/描边
    fun accentDeep(dark: Boolean) = if (dark) Color(0xFF00796B) else Color(0xFF00695C)  // 实底：角标/选中 chip/下载完成
    fun error(dark: Boolean) = if (dark) Color(0xFFEF9A9A) else Color(0xFFB71C1C)       // 断开/失败
    fun amber(dark: Boolean) = if (dark) Color(0xFFFFD54F) else Color(0xFF8D6E00)       // 连接中/警示
    fun usbBlue(dark: Boolean) = if (dark) Color(0xFF64B5F6) else Color(0xFF1565C0)     // USB 通道
    fun thumbSel(dark: Boolean) = if (dark) Color(0xFF80CBC4) else Color(0xFF00897B)    // 滚动条拖块选中
}
