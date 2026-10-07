package com.example.nikontransfer

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
}
