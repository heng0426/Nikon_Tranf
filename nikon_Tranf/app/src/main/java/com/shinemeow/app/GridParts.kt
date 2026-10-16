package com.shinemeow.app

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.Surface
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.zIndex
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.shinemeow.app.ui.theme.ShineMeowTheme
import kotlinx.coroutines.Dispatchers
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
/* [SCROLLBAR-SEALED] 快速滚动条封存：重启时随标记恢复
import my.nanihadesuka.compose.InternalLazyVerticalGridScrollbar
import my.nanihadesuka.compose.LazyVerticalGridScrollbar
import my.nanihadesuka.compose.ScrollbarSelectionMode
import my.nanihadesuka.compose.ScrollbarSettings
[/SCROLLBAR-SEALED] */
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


    @OptIn(ExperimentalFoundationApi::class)
    @Composable
internal fun MainActivity.PairCell(
        pair: PairRow,
        highlight: Boolean,
        pulseArmed: Boolean,
        onPulsePlayed: () -> Unit,
        onTap: () -> Unit,
        onLongPress: () -> Unit
    ) {
        val haptic = LocalHapticFeedback.current
        val selected = pair.key in vm.pairSelection.value
        // 进入可视区兜底：位图被内存 LRU 逐出后，滑回时从磁盘缓存自动恢复。
        // onScreen 标志供 VM 判断"读回时是否触发显示重组"（滑动中滚出屏的只进缓存）。
        DisposableEffect(Unit) {
            pair.jpg?.let { it.onScreen = true; it.enteredOnce = true }
            pair.nef?.let { it.onScreen = true; it.enteredOnce = true }
            onDispose {
                pair.jpg?.onScreen = false
                pair.nef?.onScreen = false
            }
        }
        LaunchedEffect(Unit) { (pair.jpg ?: pair.nef)?.let { vm.ensureThumb(it) } }
        // 返回指示：缩放脉冲（小-大-小-大-小，1s，精确归位）+ 持续青色描边标出刚预览的照片
        val pulse = remember { Animatable(0f) }
        LaunchedEffect(highlight, pulseArmed) {
            if (highlight && pulseArmed) {
                pulse.snapTo(0f)
                pulse.animateTo(1f, tween(UiSpec.STANDARD))
                pulse.animateTo(0f, tween(UiSpec.STANDARD))
                pulse.animateTo(1f, tween(UiSpec.STANDARD))
                pulse.animateTo(0f, tween(UiSpec.STANDARD))
                onPulsePlayed()   // 消费闸门：格子滑出屏重建后不重播
            } else pulse.snapTo(0f)
        }
        Box(
            Modifier
                .then(
                    // 高亮时才挂独立渲染层（无条件挂会让每个格子常驻一个 layer，滑动多开销）
                    if (highlight) Modifier.graphicsLayer {
                        val s = 1f + 0.06f * pulse.value
                        scaleX = s
                        scaleY = s
                    } else Modifier
                )
                .then(
                    if (highlight) Modifier.border(UiSpec.SEL_BORDER_W, UiSpec.accent(vm.darkModeOn), RoundedCornerShape(UiSpec.ROUND_SMALL))
                    else Modifier
                )
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(UiSpec.ROUND_SMALL))
                .combinedClickable(
                    // 短/长按反转（vm.swapPressOn.value，设置页开关）：开 = 短按选中、长按进预览；
                    // 关 = 默认短按预览、长按选中。触感反馈跟随"选中"动作，两种模式一致。
                    onClick = {
                        if (vm.swapPressOn.value) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onLongPress()
                        } else onTap()
                    },
                    onLongClick = {
                        if (vm.swapPressOn.value) onTap()
                        else {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onLongPress()
                        }
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            val bmp = pair.previewBmp
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = pair.stamp,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant))
                Text("RAW+JPG", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
            }
            // 左上格式徽章：JPG(青) + NEF(紫) 相邻
            Row(Modifier.align(Alignment.TopStart)) {
                if (pair.hasJpg) MiniBadge("JPG", badgeColor("JPG"))
                if (pair.hasNef) MiniBadge("NEF", badgeColor("NEF"))
            }
            // 右下已下载标记：✓J / ✓N 各自独立点亮
            Row(
                Modifier.align(Alignment.BottomEnd).padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                if (pair.jpgDownloaded) DLBadge("J", UiSpec.JPG_TEAL)
                if (pair.nefDownloaded) DLBadge("N", UiSpec.NEF_PURPLE)
            }
            // 右上保护标：相机端写保护（机内删除会被拒绝；合并格任一格式受保护即亮）
            if (pair.jpg?.isProtected?.value == true || pair.nef?.isProtected?.value == true) {
                Box(Modifier.align(Alignment.TopEnd).padding(4.dp)) { ProtBadge() }
            }
            if (selected) {
                Box(Modifier.fillMaxSize().background(UiSpec.selOverlay(vm.darkModeOn)))
                Box(Modifier.fillMaxSize().border(UiSpec.SEL_BORDER_W, UiSpec.accent(vm.darkModeOn), RoundedCornerShape(UiSpec.ROUND_SMALL)))
                Icon(
                    Icons.Filled.Check,
                    contentDescription = "已选择",
                    modifier = Modifier.align(Alignment.Center).size(UiSpec.CHECK_ICON),
                    tint = Color.White
                )
            }
        }
    }

    @Composable
internal fun MainActivity.MiniBadge(text: String, bg: Color) {
        Text(
            text,
            Modifier
                .background(bg, RoundedCornerShape(UiSpec.ROUND_SMALL))
                .padding(horizontal = 4.dp, vertical = 2.dp),
            color = Color.White,
            style = MaterialTheme.typography.labelSmall
        )
    }

    /** 已下载小圆标（✓J / ✓N） */
    @Composable
    internal fun MainActivity.DLBadge(label: String, bg: Color) {
        Box(
            Modifier
                .background(bg, CircleShape)
                .padding(horizontal = 6.dp, vertical = 1.dp)
        ) {
            Text("✓$label", color = Color.White, style = MaterialTheme.typography.labelSmall)
        }
    }

    /** 保护小锁标：琥珀圆底 + 白锁（相机端写保护，机内删除会被相机拒绝）。
     *  用警示琥珀而非 error 红：红色在本 App 专属破坏性/出错语义（删除、失败），
     *  保护是"删除将被拒绝"的预警状态 —— 全局保护视觉统一琥珀（胶囊锁标同步）。
     *  锁形与置顶胶囊共用同一 LockIcon（CardsIcons）。 */
    @Composable
    internal fun MainActivity.ProtBadge() {
        Box(
            Modifier
                .size(18.dp)
                .background(UiSpec.amber(vm.darkModeOn), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            LockIcon(Color.White, Modifier.size(10.dp))
        }
    }

    /** 网格格子：预览图 + 左上格式角标 + 右下已下载标记 + 选中态。
     *  点按=进全屏预览；长按=切换选中。 */
    @OptIn(ExperimentalFoundationApi::class)
    @Composable
internal fun MainActivity.GridCell(
        row: PhotoRow,
        highlight: Boolean,
        pulseArmed: Boolean,
        onPulsePlayed: () -> Unit,
        onTap: () -> Unit,
        onLongPress: () -> Unit
    ) {
        val haptic = LocalHapticFeedback.current
        // 进入可视区兜底：位图被内存 LRU 逐出后，滑回时从磁盘缓存自动恢复。
        // onScreen 标志供 VM 判断"读回时是否触发显示重组"（滑动中滚出屏的只进缓存）。
        DisposableEffect(Unit) {
            row.onScreen = true
            row.enteredOnce = true
            onDispose { row.onScreen = false }
        }
        LaunchedEffect(Unit) { vm.ensureThumb(row) }
        // 返回指示：缩放脉冲（小-大-小-大-小，1s，精确归位）+ 持续青色描边标出刚预览的照片
        val pulse = remember { Animatable(0f) }
        LaunchedEffect(highlight, pulseArmed) {
            if (highlight && pulseArmed) {
                pulse.snapTo(0f)
                pulse.animateTo(1f, tween(UiSpec.STANDARD))
                pulse.animateTo(0f, tween(UiSpec.STANDARD))
                pulse.animateTo(1f, tween(UiSpec.STANDARD))
                pulse.animateTo(0f, tween(UiSpec.STANDARD))
                onPulsePlayed()   // 消费闸门：格子滑出屏重建后不重播
            } else pulse.snapTo(0f)
        }
        Box(
            Modifier
                .then(
                    // 高亮时才挂独立渲染层（无条件挂会让每个格子常驻一个 layer，滑动多开销）
                    if (highlight) Modifier.graphicsLayer {
                        val s = 1f + 0.06f * pulse.value
                        scaleX = s
                        scaleY = s
                    } else Modifier
                )
                .then(
                    if (highlight) Modifier.border(UiSpec.SEL_BORDER_W, UiSpec.accent(vm.darkModeOn), RoundedCornerShape(UiSpec.ROUND_SMALL))
                    else Modifier
                )
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(UiSpec.ROUND_SMALL))
                .combinedClickable(
                    // 短/长按反转（vm.swapPressOn.value，设置页开关）：开 = 短按选中、长按进预览；
                    // 关 = 默认短按预览、长按选中。触感反馈跟随"选中"动作，两种模式一致。
                    onClick = {
                        if (vm.swapPressOn.value) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onLongPress()
                        } else onTap()
                    },
                    onLongClick = {
                        if (vm.swapPressOn.value) onTap()
                        else {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onLongPress()
                        }
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            val bmp = row.preview.value
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = row.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant))
                Text(row.type, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
            }
            // 左上格式角标
            Text(
                row.type,
                Modifier
                    .align(Alignment.TopStart)
                    .background(badgeColor(row.type), RoundedCornerShape(UiSpec.ROUND_SMALL))
                    .padding(horizontal = 5.dp, vertical = 2.dp),
                color = Color.White,
                style = MaterialTheme.typography.labelSmall
            )
            // 右下已下载标记
            if (row.downloaded.value) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .background(UiSpec.dlMark(vm.darkModeOn), CircleShape)
                ) {
                    Text(
                        "✓",
                        Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            // 右上保护标：相机端写保护（机内删除会被拒绝）
            if (row.isProtected.value) {
                Box(Modifier.align(Alignment.TopEnd).padding(4.dp)) { ProtBadge() }
            }
            // 选中态：绿色边框 + 中央勾
            if (row.selected.value) {
                Box(Modifier.fillMaxSize().background(UiSpec.selOverlay(vm.darkModeOn)))
                Box(Modifier.fillMaxSize().border(UiSpec.SEL_BORDER_W, UiSpec.accent(vm.darkModeOn), RoundedCornerShape(UiSpec.ROUND_SMALL)))
                Icon(
                    Icons.Filled.Check,
                    contentDescription = "已选择",
                    modifier = Modifier.align(Alignment.Center).size(UiSpec.CHECK_ICON),
                    tint = Color.White
                )
            }
        }
    }

    @Composable
internal fun DateBubble(text: String) {
        Surface(
            shape = RoundedCornerShape(UiSpec.ROUND_LARGE),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.97f),
            shadowElevation = 4.dp
        ) {
            Text(
                text,
                Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }

    /** 日期胶囊槽位高度：只改这个数字——数字越小，胶囊下方留白越小（建议 24~30） */

    /** 折叠切换后应对齐到视口首行的网格 item index（含节头；折叠节按 1 个 item 计，
     *  与置顶胶囊 pillSec / 滚动条气泡同一套映射口径）：
     *  收起 → 下一分节节头（让"下个日期的气泡"顶到置顶槽正下方；无下一节则回落本节头）；
     *  展开 → 本分节节头。sections = (dateKey, 行数) 按显示顺序。 */
    internal fun hdrIndexAfterToggle(
        sections: List<Pair<String, Int>>,
        collapsed: Set<String>,
        key: String,
        nowCollapsed: Boolean
    ): Int {
        var acc = 0
        for ((i, s) in sections.withIndex()) {
            if (s.first == key) {
                return when {
                    !nowCollapsed -> acc                 // 展开：本节节头
                    i + 1 < sections.size -> acc + 1     // 收起：下一节节头
                    else -> acc                          // 收起末节：回落本节头
                }
            }
            acc += 1 + if (s.first in collapsed) 0 else s.second
        }
        return 0
    }

    /** 置顶信息胶囊槽（合并/文件共用）：左=当前分节日期，右=张数（分块开启时为已加载/总量），
     *  末尾=受保护张数胶囊（小锁+数字，protCount=0 时整个胶囊不显示）。
     *  固定槽位随滚动更新，不与照片重叠。
     *  onToggle 非空时日期胶囊可点击 → 折叠/展开当前日期（与节头胶囊同视觉语言：
     *  折叠态箭头转 -90° + 胶囊变淡）。 */
    @Composable
internal fun MainActivity.PinnedPillSlot(
        dateText: String?,
        countText: String,
        protCount: Int = 0,
        collapsed: Boolean = false,
        onToggle: (() -> Unit)? = null
    ) {
        Box(Modifier.fillMaxWidth().height(UiSpec.PILL_SLOT), contentAlignment = Alignment.CenterStart) {
            if (dateText != null) {
                val arrow by animateFloatAsState(
                    if (collapsed) -90f else 0f,
                    animationSpec = tween(UiSpec.STANDARD),
                    label = "pinFoldArrow"
                )
                Row(horizontalArrangement = Arrangement.spacedBy(UiSpec.PILL_GAP)) {
                    Surface(
                        shape = RoundedCornerShape(UiSpec.ROUND_LARGE),
                        color = if (collapsed) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = UiSpec.PILL_DIM)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = UiSpec.PILL_SOLID),
                        modifier = if (onToggle != null)
                            Modifier.clip(RoundedCornerShape(UiSpec.ROUND_LARGE)).clickable(onClick = onToggle)
                        else Modifier
                    ) {
                        Row(
                            Modifier.padding(
                                start = UiSpec.PILL_HPAD,
                                end = if (onToggle != null) 5.dp else UiSpec.PILL_HPAD,
                                top = UiSpec.PILL_VPAD, bottom = UiSpec.PILL_VPAD
                            ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                dateText,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (onToggle != null) {
                                Icon(
                                    Icons.Filled.ArrowDropDown,
                                    contentDescription = if (collapsed) "展开当前日期照片" else "收起当前日期照片",
                                    modifier = Modifier.size(UiSpec.ICON_SM).graphicsLayer { rotationZ = arrow },
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(UiSpec.ROUND_LARGE),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = UiSpec.PILL_SOLID)
                    ) {
                        Text(
                            countText,
                            Modifier.padding(horizontal = UiSpec.PILL_HPAD, vertical = UiSpec.PILL_VPAD),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (protCount > 0) {
                        Surface(
                            shape = RoundedCornerShape(UiSpec.ROUND_LARGE),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = UiSpec.PILL_SOLID)
                        ) {
                            Row(
                                Modifier.padding(
                                    start = 7.dp, end = UiSpec.PILL_HPAD,
                                    top = UiSpec.PILL_VPAD, bottom = UiSpec.PILL_VPAD
                                ),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                LockIcon(UiSpec.amber(vm.darkModeOn), Modifier.size(11.dp))
                                Text(
                                    "$protCount",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = UiSpec.amber(vm.darkModeOn)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    /* [SCROLLBAR-SEALED] 快速滚动条封存：覆盖层（显示+拖动）整体停用，重启时随标记恢复
    /** 快速滚动条覆盖层（合并/文件共用）：阈值判断 + 库显示层（拖块/日期气泡）+ 右缘像素级拖动层。
     *  sections = (dateKey, 行数) 按显示顺序；折叠节只渲染节头 1 个 item，气泡映射需同步。
     *  拖动：onDragStart 算一次缩放比，事件用 dispatchRawDelta 同步滚动（无协程排队，快拖不卡）。 */
    @Composable
internal fun BoxScope.GridScrollbarOverlay(
    vm: MainViewModel,
    state: LazyGridState,
        sections: List<Pair<String, Int>>,
        collapsedDates: Set<String>,
        settings: ScrollbarSettings,
        onDragging: (Boolean) -> Unit
    ) {
        val unfolded = sections.filter { it.first !in collapsedDates }.sumOf { it.second }
        if (vm.scrollbarThreshold.value < 0 || unfolded < vm.scrollbarThreshold.value) return
        InternalLazyVerticalGridScrollbar(
            state = state,
            modifier = Modifier.fillMaxSize(),
            settings = settings,
            indicatorContent = { idx, _ ->
                var acc = 0
                var d: String? = null
                for ((dateKey, rows) in sections) {
                    val cnt = 1 + if (dateKey in collapsedDates) 0 else rows
                    if (idx >= acc && idx <= acc + cnt - 1) {
                        d = vm.dateLabel(dateKey, rows)
                        break
                    }
                    acc += cnt
                }
                if (d != null) DateBubble(d)
            }
        )
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(UiSpec.DRAG_STRIP_W)
                .pointerInput(state) {
                    var scale = 0f
                    detectVerticalDragGestures(
                        onDragStart = {
                            onDragging(true)
                            val info = state.layoutInfo
                            val vis = info.visibleItemsInfo
                            scale = if (vis.isEmpty()) 0f else
                                ((vis.sumOf { it.size.height.toDouble() } / vis.size) *
                                    info.totalItemsCount / 3.0 / size.height).toFloat()
                        },
                        onDragEnd = { onDragging(false) },
                        onDragCancel = { onDragging(false) },
                        onVerticalDrag = { change, dy ->
                            change.consume()
                            if (scale > 0f) state.dispatchRawDelta(dy * scale)
                        }
                    )
                }
        )
    }
    [/SCROLLBAR-SEALED] */

    /** 节头日期胶囊：点击折叠/展开当天照片（合并/文件模式共用）。
     *  折叠态：箭头旋转 -90° + 胶囊变淡；
     *  当天有已选中照片时，日期胶囊旁独立显示"已选 N"计数胶囊
     *  （不用 ✓ —— 避免与已下载标记混淆）。
     *  折叠语义 = 只藏不见：totalCount 始终显示当天原始张数（Q4=B）。 */
    @Composable
internal fun MainActivity.DateHeaderPill(
        dateKey: String,
        totalCount: Int,
        collapsed: Boolean,
        selectedInDay: Int,
        onToggle: () -> Unit
    ) {
        val arrow by animateFloatAsState(
            if (collapsed) -90f else 0f,
            animationSpec = tween(UiSpec.STANDARD),
            label = "foldArrow"
        )
        Row(
            Modifier.padding(top = 8.dp, bottom = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(UiSpec.ROUND_LARGE),
                color = if (collapsed) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = UiSpec.PILL_DIM)
                else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.clip(RoundedCornerShape(UiSpec.ROUND_LARGE)).clickable(onClick = onToggle)
            ) {
                Row(
                    Modifier.padding(start = UiSpec.PILL_HPAD, end = 5.dp, top = UiSpec.PILL_VPAD, bottom = UiSpec.PILL_VPAD),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        vm.dateLabel(dateKey, totalCount),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Icon(
                        Icons.Filled.ArrowDropDown,
                        contentDescription = if (collapsed) "展开当天照片" else "折叠当天照片",
                        modifier = Modifier.size(UiSpec.ICON_SM).graphicsLayer { rotationZ = arrow },
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            // 选中计数胶囊：与日期胶囊独立，避免挤占日期/箭头空间；"已选"前缀消除歧义
            if (collapsed && selectedInDay > 0) {
                Surface(
                    shape = RoundedCornerShape(UiSpec.ROUND_SMALL),
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Text(
                        "已选 $selectedInDay",
                        Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }

    /** 筛选卡片（毛玻璃底部弹层）：文件类型(单选) / 下载状态(单选：全部/未下载/已下载) / 拍摄日期(起止)。
     *  实时生效，右上角重置；样式对照用户提供的参考图（去掉连拍与保护）。
     *  玻璃面：背景保持清晰，仅弹层自身范围内透出主内容的模糊像（glassBackdropModifier）。
     *  其窗口本就全屏 edge-to-edge（坐标与主窗口对齐），无需额外 properties。 */
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
internal fun MainActivity.FilterSheet(onDismiss: () -> Unit) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            modifier = glassBackdropModifier(),
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f)
        ) {
            Column(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 28.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FunnelIcon(MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "筛选",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f)
                    )
                    ActionPill("重置") { vm.resetFilter() }
                }
                Spacer(Modifier.height(6.dp))
                // 合并模式下每个格子已同时呈现 JPG+NEF，格式筛选行隐藏（①b）
                if (!vm.mergePairs.value) {
                    Text("文件类型", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FilterBigButton("全部", vm.filterFormat.value == "全部", Modifier.weight(1f)) {
                            vm.filterFormat.value = "全部"
                        }
                        FilterBigButton("JPG", vm.filterFormat.value == "JPG", Modifier.weight(1f)) {
                            vm.filterFormat.value = "JPG"
                        }
                        FilterBigButton("NEF", vm.filterFormat.value == "NEF", Modifier.weight(1f)) {
                            vm.filterFormat.value = "NEF"
                        }
                    }
                    HorizontalDivider(
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.padding(vertical = 14.dp)
                    )
                }
                Text("下载状态", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                // 与文件类型同款单选三按钮：全部 / 未下载 / 已下载
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilterBigButton("全部", vm.filterDl.value == "全部", Modifier.weight(1f)) {
                        vm.filterDl.value = "全部"
                    }
                    FilterBigButton("未下载", vm.filterDl.value == "未下载", Modifier.weight(1f)) {
                        vm.filterDl.value = "未下载"
                    }
                    FilterBigButton("已下载", vm.filterDl.value == "已下载", Modifier.weight(1f)) {
                        vm.filterDl.value = "已下载"
                    }
                }
                HorizontalDivider(
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.padding(vertical = 14.dp)
                )
                Text("拍摄日期", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DateField(
                        label = "开始",
                        value = vm.filterStart.value,
                        onSet = { vm.filterStart.value = it },
                        onClear = { vm.filterStart.value = null },
                        modifier = Modifier.weight(1f)
                    )
                    DateField(
                        label = "结束",
                        value = vm.filterEnd.value,
                        onSet = { vm.filterEnd.value = it },
                        onClear = { vm.filterEnd.value = null },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }

    /** 带标签的勾选框（多选下载格式选择） */
    @Composable
internal fun MainActivity.LabeledCheckbox(label: String, state: androidx.compose.runtime.MutableState<Boolean>) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable { state.value = !state.value }
        ) {
            Checkbox(checked = state.value, onCheckedChange = { state.value = it })
            Text(label, style = MaterialTheme.typography.bodyMedium)
        }
    }

    /** 文件类型大按钮（选中=主题色实底白字，未选=浅灰底） */
    @Composable
internal fun MainActivity.FilterBigButton(
        text: String,
        selected: Boolean,
        modifier: Modifier = Modifier,
        onClick: () -> Unit
    ) {
        Box(
            modifier
                .clip(RoundedCornerShape(UiSpec.ROUND_LARGE))
                .background(if (selected) UiSpec.JPG_TEAL else MaterialTheme.colorScheme.surfaceVariant)
                .clickable(onClick = onClick)
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text,
                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }

    /** 日期字段：显示标签 + 当前值（"不限"或 yyyy-MM-dd），点按弹日历，有值时带清除 */
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
internal fun MainActivity.DateField(
        label: String,
        value: String?,
        onSet: (String) -> Unit,
        onClear: () -> Unit,
        modifier: Modifier = Modifier
    ) {
        var open by remember { mutableStateOf(false) }
        Box(
            modifier
                .clip(RoundedCornerShape(UiSpec.ROUND_LARGE))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable { open = true }
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        if (value != null && value.length == 8)
                            "${value.substring(0, 4)}-${value.substring(4, 6)}-${value.substring(6, 8)}"
                        else "不限",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (value != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (value != null) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "清除$label",
                        modifier = Modifier
                            .size(16.dp)
                            .clickable { onClear() },
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        if (open) {
            val init = value?.let { keyToMillis(it) }
            val st = rememberDatePickerState(initialSelectedDateMillis = init)
            DatePickerDialog(
                onDismissRequest = { open = false },
                // 全屏 edge-to-edge 窗口：内容坐标与主窗口对齐，毛玻璃底材可精确回绘
                properties = DialogProperties(
                    usePlatformDefaultWidth = false,
                    decorFitsSystemWindows = false
                ),
                modifier = glassBackdropModifier(),
                colors = DatePickerDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f)
                ),
                confirmButton = {
                    TextButton(onClick = {
                        st.selectedDateMillis?.let { onSet(millisToKey(it)) }
                        open = false
                    }) { Text("确定") }
                },
                dismissButton = {
                    TextButton(onClick = { open = false }) { Text("取消") }
                }
            ) {
                DatePicker(state = st)
            }
        }
    }

internal fun millisToKey(ms: Long): String {
    val d = java.time.Instant.ofEpochMilli(ms)
        .atZone(java.time.ZoneId.systemDefault()).toLocalDate()
    return "%04d%02d%02d".format(d.year, d.monthValue, d.dayOfMonth)
}

/** "YYYYMMDD" → DatePicker 毫秒（当天 0 点） */
internal fun keyToMillis(key: String): Long? = try {
    java.time.LocalDate.of(
        key.substring(0, 4).toInt(),
        key.substring(4, 6).toInt(),
        key.substring(6, 8).toInt()
    ).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
} catch (_: Exception) {
    null
}