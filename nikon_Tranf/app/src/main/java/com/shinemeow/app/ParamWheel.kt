package com.shinemeow.app

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.commandiron.wheel_picker_compose.core.WheelPickerDefaults
import com.commandiron.wheel_picker_compose.core.WheelTextPicker
import kotlin.math.roundToInt

/* =====================================================================
 *  摄影参数调值组件（任务 6 体验优化；grilling 确认 2026-10-17）
 *  ---------------------------------------------------------------------
 *  参数条（ParamNumbersBar）：ISO / 光圈 / 快门三大数字横排，两种调值方式：
 *    ① 按住数字上下拖动 → 值随手指滚动（上滑=更亮），松手写挡
 *    ② 单击数字 → 单列滚轮弹窗（上下各一候选值），选定后确认按钮才写
 *  UI 序：index 0 = 最亮端（ISO 高 / 快门快 / 光圈开大），上滑 = index 减。
 *  Wi-Fi 只读时 enabled=false：数字灰置、手势禁用、显示只读提示（上层给）。
 * ===================================================================== */

/** 横向参数条：三个 ParamNumCell；拍后相机读回自动更新显示。
 *  MainActivity 扩展：毛玻璃弹窗需要访问成员 glassBackdropModifier（工程惯例同 GridParts）。 */
@Composable
internal fun MainActivity.ParamNumbersBar(
    vm: MainViewModel,
    fg: Color,
    dim: Color,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    var wheelWhich by remember { mutableStateOf<Int?>(null) }
    Row(modifier.fillMaxWidth()) {
        ParamNumCell(vm, 0, "ISO", vm.paramIso.value?.toString() ?: "—", fg, dim, enabled, Modifier.weight(1f)) { wheelWhich = 0 }
        ParamNumCell(vm, 1, "光圈", vm.paramAperture.value?.let {
            if (it % 100 == 0) "f/${it / 100}" else "f/${it / 100.0}"
        } ?: "—", fg, dim, enabled, Modifier.weight(1f)) { wheelWhich = 1 }
        ParamNumCell(vm, 2, "快门", vm.paramShutter.value?.let { vm.shutterLabel(it) } ?: "—", fg, dim, enabled, Modifier.weight(1f)) { wheelWhich = 2 }
    }
    wheelWhich?.let { which ->
        ParamWheelDialog(vm, which, enabled, onDismiss = { wheelWhich = null })
    }
}

/** 单个参数格：标签 + 大数字；按住左右拖动滚值（右滑=更亮）/ 单击弹滚轮。
 *  闪变修复：松手后保持显示目标挡，直到相机读回同步（VM 值的 UI 序 == 目标序）
 *  才切回真实值——否则 dragIdx 清零瞬间会闪回旧值再跳新值。 */
@Composable
private fun ParamNumCell(
    vm: MainViewModel, which: Int, label: String, curText: String,
    fg: Color, dim: Color, enabled: Boolean,
    modifier: Modifier = Modifier, onOpen: () -> Unit
) {
    val labels = remember(which) { vm.paramWheelLabels(which) }
    var dragIdx by remember(which) { mutableIntStateOf(-1) }        // -1 = 未拖动
    var pendingIdx by remember(which) { mutableIntStateOf(-1) }     // 松手后等待读回的目标挡
    val density = LocalDensity.current
    val stepPx = with(density) { UiSpec.DRAG_STEP.toPx() }   // 滑动切档阈值（独立令牌，灵敏度单独调）
    // 一次手势只变一档（用户定 2026-10-17：三参数统一，划多远都 ±1，来回划可预览）。
    // 滑动方向：右滑=更亮=UI 序减（ISO/快门）；光圈反转（用户实测反馈）：右滑=f 增大=更暗。
    val dragDir = if (which == 1) 1 else -1
    // 相机读回到位（当前值的 UI 序 == 目标序）→ 清除保持态
    LaunchedEffect(curText) {
        if (pendingIdx >= 0 && vm.paramWheelIndex(which) == pendingIdx) pendingIdx = -1
    }
    // 显示序：拖动中 > 松手待读回 > 当前值（相机值不在阶梯上为 -1，显示 curText）
    val dispIdx = when {
        dragIdx >= 0 -> dragIdx
        pendingIdx >= 0 -> pendingIdx
        else -> vm.paramWheelIndex(which)
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = dim
        )
        Box(
            Modifier
                .fillMaxWidth()                                     // 热区随格子宽（weight 均分）
                .height(UiSpec.WHEEL_ITEM_H + 16.dp)               // 热区：文字上下各加 8dp
                .pointerInput(which, labels.size, enabled) {
                    if (!enabled) return@pointerInput
                    detectTapGestures { onOpen() }
                }
                .pointerInput(which, labels.size, enabled) {
                    if (!enabled) return@pointerInput
                    var base = -1
                    var acc = 0f
                    detectHorizontalDragGestures(
                        onDragStart = {
                            base = vm.paramWheelIndex(which)
                            acc = 0f
                            dragIdx = if (base < 0) -1 else base
                        },
                        onHorizontalDrag = { change, amount ->
                            change.consume()
                            if (base < 0) return@detectHorizontalDragGestures
                            acc += amount
                            // 一次手势只 ±1 档：滑过阈值（WHEEL_ITEM_H）才切，来回划可预览
                            val dir = if (acc > stepPx) 1 else if (acc < -stepPx) -1 else 0
                            if (dir != 0) {
                                dragIdx = (dragIdx + dragDir * dir).coerceIn(0, labels.size - 1)
                                acc = 0f
                            }
                        },
                        onDragEnd = {
                            if (dragIdx >= 0 && dragIdx != base) {
                                pendingIdx = dragIdx
                                vm.applyParamWheel(which, dragIdx)
                            }
                            dragIdx = -1
                        },
                        onDragCancel = { dragIdx = -1 }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            // 数值变化动画（水平，跟手指）：内容移动方向 = dragDir × 序变化——
            // 右滑一律内容右移（新值左进旧值右出），三参数与手指方向统一
            AnimatedContent(
                targetState = dispIdx,
                transitionSpec = {
                    val move = dragDir * (targetState - initialState)
                    val d = if (move > 0) 1 else -1
                    (slideInHorizontally(tween(120)) { -it / 3 * d } + fadeIn(tween(120)))
                        .togetherWith(slideOutHorizontally(tween(120)) { it / 3 * d } + fadeOut(tween(120)))
                },
                label = "paramCell$which"
            ) { i ->
                Text(
                    if (i in labels.indices) labels[i] else curText,
                    style = MaterialTheme.typography.titleLarge,
                    color = if (enabled) fg else fg.copy(alpha = 0.45f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/** 单列滚轮弹窗：WheelPickerCompose 库 WheelTextPicker（iOS 风格：中间选框+边框、
 *  上下行透视渐隐、fling 惯性 + snap 对齐）。**滚停即写**（onScrollFinished →
 *  applyParamWheel 后台写挡，无确认按钮，连续滚挡流畅）；弹窗为工程统一毛玻璃
 *  （全屏 Dialog + glassBackdropModifier 底材回绘，同 DownloadsQueue 范式）。
 *  Wi-Fi 只读：滚轮上盖透明层拦截手势 + 灰置 + 只读提示。
 *  切回 material3-picker 版本：gradle 恢复 A 依赖 + 本文件 git 历史取回。 */
@Composable
internal fun MainActivity.ParamWheelDialog(vm: MainViewModel, which: Int, enabled: Boolean, onDismiss: () -> Unit) {
    val labels = remember(which) { vm.paramWheelLabels(which) }
    var idx by remember(which) { mutableIntStateOf(vm.paramWheelIndex(which).coerceAtLeast(0)) }
    val title = when (which) { 0 -> "ISO"; 1 -> "光圈"; else -> "快门" }
    val dark = vm.darkModeOn
    val txtColor = (if (dark) Color.White else Color(0xFF1C1C1E)).let {
        if (enabled) it else it.copy(alpha = 0.45f)
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        // 全屏窗口：点弹窗外空白 = 返回（onDismissRequest 只管窗口外，全屏窗口需自拦）
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) { detectTapGestures { onDismiss() } }
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                Modifier
                    .width(280.dp)
                    .then(
                        glassBackdropModifier(
                            shape = RoundedCornerShape(UiSpec.ROUND_LARGE),
                            tint = UiSpec.glassTint(dark),
                            border = UiSpec.glassBorder(dark),
                            blur = UiSpec.GLASS_BLUR,
                            shadow = UiSpec.GLASS_SHADOW
                        )
                    )
                    .pointerInput(Unit) { detectTapGestures { } }   // 消费弹窗内点击，不透传关闭
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(title, style = MaterialTheme.typography.labelMedium,
                    color = if (dark) Color.White.copy(alpha = 0.65f) else Color.Black.copy(alpha = 0.55f))
                Spacer(Modifier.height(4.dp))
                // 滚轮区（库组件：上下拖 + fling 惯性 + 松手 snap；中间选框 + 3D 透视）
                Box(Modifier.fillMaxWidth()) {
                    WheelTextPicker(
                        modifier = Modifier.fillMaxWidth(),
                        startIndex = idx.coerceIn(0, labels.lastIndex),
                        size = androidx.compose.ui.unit.DpSize(240.dp, UiSpec.WHEEL_ITEM_H * 3),
                        texts = labels,
                        rowCount = 3,
                        style = MaterialTheme.typography.headlineSmall,
                        color = txtColor,
                        selectorProperties = WheelPickerDefaults.selectorProperties(
                            enabled = true,
                            shape = RoundedCornerShape(UiSpec.ROUND_SMALL),
                            color = if (dark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f),
                            border = BorderStroke(1.dp, UiSpec.accent(dark).copy(alpha = 0.6f))
                        ),
                        onScrollFinished = { snapped ->
                            idx = snapped
                            if (enabled) vm.applyParamWheel(which, snapped)   // 滚停即写
                            null                                              // 不强改落点
                        }
                    )
                    if (!enabled) {
                        // 只读覆盖层：吃掉所有指针事件，滚轮不可动
                        Box(
                            Modifier
                                .matchParentSize()
                                .pointerInput(Unit) {
                                    awaitEachGesture {
                                        awaitFirstDown(requireUnconsumed = false)
                                        do {
                                            val ev = awaitPointerEvent()
                                            ev.changes.forEach { it.consume() }
                                        } while (ev.changes.any { it.pressed })
                                    }
                                }
                        )
                    }
                }
                if (!enabled) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        vm.paramHint.value ?: "Wi-Fi 连接下相机参数只读，请用 USB 连接调节",
                        style = MaterialTheme.typography.labelSmall,
                        color = UiSpec.error(dark),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
