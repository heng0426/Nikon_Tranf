package com.example.nikontransfer

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
import androidx.compose.foundation.layout.WindowInsetsSides
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
import com.example.nikontransfer.ui.theme.NikonTransferTheme
import kotlinx.coroutines.Dispatchers
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import my.nanihadesuka.compose.InternalLazyVerticalGridScrollbar
import my.nanihadesuka.compose.LazyVerticalGridScrollbar
import my.nanihadesuka.compose.ScrollbarSelectionMode
import my.nanihadesuka.compose.ScrollbarSettings
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


    @Composable
internal fun MainActivity.FunnelIcon(color: Color, modifier: Modifier = Modifier) {
        Canvas(modifier.size(20.dp)) {
            val w = size.width
            val h = size.height
            val p = Path().apply {
                moveTo(0f, h * 0.06f)
                lineTo(w, h * 0.06f)
                lineTo(w * 0.60f, h * 0.52f)
                lineTo(w * 0.60f, h)
                lineTo(w * 0.40f, h * 0.84f)
                lineTo(w * 0.40f, h * 0.52f)
                close()
            }
            drawPath(p, color)
        }
    }

    /** 齿轮图标（描边风格：8齿外圈 + 双层同心圆孔），风格同图片示例 */
    @Composable
internal fun MainActivity.GearIcon(color: Color, modifier: Modifier = Modifier) {
        Canvas(modifier.size(20.dp)) {
            val w = size.width
            val h = size.height
            val cx = (w / 2f).toDouble()
            val cy = (h / 2f).toDouble()
            val s = minOf(w, h).toDouble()
            val stroke = (s * 0.065).toFloat()
            val style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)

            // 外圈齿轮轮廓：8 个齿（平顶），齿间为凹槽，整体成封闭多边形（描边）
            val rTooth = s * 0.46  // 齿顶半径
            val rValley = s * 0.34 // 齿根半径
            val teeth = 8
            val p = Path()
            // 每个齿占 45°，齿顶宽约 14°，齿根过渡
            val toothTop = 14.0
            val toothGap = 45.0 - toothTop
            var started = false
            for (i in 0 until teeth) {
                val base = i.toDouble() * 45.0 - 22.5
                val a0 = (base + toothGap / 2.0) * Math.PI / 180.0          // 齿根左
                val a1 = (base + toothGap / 2.0 + toothTop * 0.55) * Math.PI / 180.0  // 齿顶左
                val a2 = (base + toothGap / 2.0 + toothTop * 1.45) * Math.PI / 180.0 // 齿顶右
                val a3 = (base + 45.0 - toothGap / 2.0) * Math.PI / 180.0     // 齿根右

                val x0 = (cx + rValley * Math.cos(a0)).toFloat()
                val y0 = (cy + rValley * Math.sin(a0)).toFloat()
                val x1 = (cx + rTooth * Math.cos(a1)).toFloat()
                val y1 = (cy + rTooth * Math.sin(a1)).toFloat()
                val x2 = (cx + rTooth * Math.cos(a2)).toFloat()
                val y2 = (cy + rTooth * Math.sin(a2)).toFloat()
                val x3 = (cx + rValley * Math.cos(a3)).toFloat()
                val y3 = (cy + rValley * Math.sin(a3)).toFloat()

                if (!started) { p.moveTo(x0, y0); started = true } else p.lineTo(x0, y0)
                p.lineTo(x1, y1)
                p.lineTo(x2, y2)
                p.lineTo(x3, y3)
            }
            p.close()
            drawPath(p, color, style = style)

            // 中圆环
            drawCircle(
                color,
                radius = (s * 0.20).toFloat(),
                center = Offset(cx.toFloat(), cy.toFloat()),
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            // 内圆孔
            drawCircle(
                color,
                radius = (s * 0.09).toFloat(),
                center = Offset(cx.toFloat(), cy.toFloat()),
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }
    }

    /** Wi-Fi 形状（三条弧 + 圆点），用于连接状态指示（同漏斗图标的自绘风格）；尺寸由调用方决定 */
    @Composable
internal fun MainActivity.WifiIcon(color: Color, modifier: Modifier = Modifier) {
        Canvas(modifier) {
            val w = size.width
            val h = size.height
            val stroke = w * 0.12f
            val cx = w / 2f
            val cy = h * 0.78f
            val style = Stroke(width = stroke, cap = StrokeCap.Round)
            // 三条弧：由内到外，开口向上
            listOf(0.24f, 0.42f, 0.60f).forEach { r ->
                drawArc(
                    color = color,
                    startAngle = -135f,
                    sweepAngle = 90f,
                    useCenter = false,
                    topLeft = Offset(cx - w * r, cy - h * r),
                    size = Size(w * r * 2f, h * r * 2f),
                    style = style
                )
            }
            drawCircle(color, radius = w * 0.11f, center = Offset(cx, cy))
        }
    }

    /** 下载形状（向下箭头 + 底部托盘），用于下载队列指示（同自绘风格） */
    @Composable
    internal fun DownloadIcon(color: Color, modifier: Modifier = Modifier) {
        Canvas(modifier.size(20.dp)) {
            val w = size.width
            val h = size.height
            val stroke = w * 0.13f
            // 箭杆
            drawLine(
                color,
                start = Offset(w * 0.5f, h * 0.12f),
                end = Offset(w * 0.5f, h * 0.55f),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
            // 箭头（实心三角）
            val arrow = Path().apply {
                moveTo(w * 0.26f, h * 0.48f)
                lineTo(w * 0.74f, h * 0.48f)
                lineTo(w * 0.5f, h * 0.74f)
                close()
            }
            drawPath(arrow, color)
            // 托盘
            drawLine(
                color,
                start = Offset(w * 0.18f, h * 0.88f),
                end = Offset(w * 0.82f, h * 0.88f),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
        }
    }

    /** USB 插头形状（描边风格：外壳 + 两针），与漏斗/WiFi/齿轮同自绘风格 */
    @Composable
internal fun MainActivity.UsbIcon(color: Color, modifier: Modifier = Modifier) {
        Canvas(modifier.size(20.dp)) {
            val w = size.width
            val h = size.height
            val cx = w / 2f
            val cy = h / 2f
            val s = minOf(w, h)
            val stroke = (s * 0.07f).toFloat()
            val style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
            // 外壳：上宽下窄的梯形（开口朝下）
            val shell = Path().apply {
                moveTo(cx - s * 0.30f, cy - s * 0.22f)
                lineTo(cx + s * 0.30f, cy - s * 0.22f)
                lineTo(cx + s * 0.22f, cy + s * 0.20f)
                lineTo(cx - s * 0.22f, cy + s * 0.20f)
                close()
            }
            drawPath(shell, color, style = style)
            // 两针（外壳下方伸出）
            drawLine(color, Offset(cx - s * 0.12f, cy + s * 0.20f), Offset(cx - s * 0.12f, cy + s * 0.36f), style.width, cap = StrokeCap.Round)
            drawLine(color, Offset(cx + s * 0.12f, cy + s * 0.20f), Offset(cx + s * 0.12f, cy + s * 0.36f), style.width, cap = StrokeCap.Round)
            // 外壳内横线（细节）
            drawLine(color, Offset(cx - s * 0.18f, cy - s * 0.10f), Offset(cx + s * 0.18f, cy - s * 0.10f), style.width, cap = StrokeCap.Round)
        }
    }

    /** 双卡选择 - USB 通道卡（蓝色系）
     *  compact=false 冷启动全屏卡；compact=true 有照片断开时的顶部紧凑选择条（需调用处传 weight） */
    @Composable
internal fun MainActivity.UsbChannelCard(
        compact: Boolean = false,
        onClick: () -> Unit = {},
        progressText: String? = null,
        active: Boolean = false,
        dimmed: Boolean = false,
        modifier: Modifier = Modifier
    ) {
        val dark = vm.darkModeOn
        val primary = if (dark) UiSpec.usbBlue(dark) else UiSpec.usbBlue(dark)
        val surface = UiSpec.usbSurface(dark)
        val onSurface = UiSpec.usbOnSurface(dark)

        Surface(
            shape = RoundedCornerShape(if (compact) 12.dp else 16.dp),
            color = surface,
            modifier = modifier
                .fillMaxWidth()
                .alpha(if (dimmed) 0.45f else 1f)
                .clickable(enabled = !active) {
                    vm.clearConnFailure()
                    onClick()
                }
        ) {
            Row(
                // compact 卡高度锁定 60dp（同 Wi-Fi 卡）：状态行出现不撑高
                Modifier
                    .then(if (compact) Modifier.height(60.dp) else Modifier)
                    .padding(
                        horizontal = if (compact) 12.dp else 16.dp,
                        vertical = if (compact) 0.dp else 16.dp
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 图标色块容器（与顶栏按钮同风格）
                Box(
                    Modifier.size(if (compact) 34.dp else 46.dp)
                        .clip(RoundedCornerShape(if (compact) 9.dp else 13.dp))
                        .background(primary.copy(alpha = if (dark) 0.20f else 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    UsbIcon(primary, Modifier.size(if (compact) 20.dp else 26.dp))
                }
                Spacer(Modifier.width(if (compact) 10.dp else 14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (compact) "USB" else "USB 数据线",
                        style = if (compact) MaterialTheme.typography.titleSmall
                        else MaterialTheme.typography.titleMedium,
                        color = onSurface
                    )
                    if (!compact) {
                        Text(
                            "插线即连 · 传输快 · 更稳定",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    // 当前通道的连接/扫描状态行
                    if (progressText != null) {
                        Spacer(Modifier.height(3.dp))
                        Text(
                            progressText,
                            style = MaterialTheme.typography.labelSmall,
                            color = primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (progressText != null) {
                    CircularProgressIndicator(
                        color = primary,
                        strokeWidth = if (compact) 2.dp else 3.dp,
                        modifier = Modifier.size(if (compact) 18.dp else 24.dp)
                    )
                } else if (!compact && !active) {
                    // 连接中不显示「连接 ›」（进度状态由下方信息卡片承载），高度不受影响
                    Text("连接 ›", style = MaterialTheme.typography.labelLarge, color = primary)
                }
            }
        }
    }

    /** 双卡选择 - Wi-Fi 通道卡（绿色系） */
    @Composable
internal fun MainActivity.WifiChannelCard(
        compact: Boolean = false,
        onClick: () -> Unit = {},
        onScanClick: () -> Unit = {},
        onWifiCancel: () -> Unit = {},
        progressText: String? = null,
        active: Boolean = false,
        dimmed: Boolean = false,
        hotspotOn: Boolean = false,
        modifier: Modifier = Modifier
    ) {
        val dark = vm.darkModeOn
        val primary = if (dark) UiSpec.accent(dark) else UiSpec.accent(dark)
        val surface = UiSpec.wifiSurface(dark)
        val onSurface = UiSpec.wifiOnSurface(dark)

        Surface(
            shape = RoundedCornerShape(if (compact) 12.dp else 16.dp),
            color = surface,
            modifier = modifier
                .fillMaxWidth()
                .alpha(if (dimmed) 0.45f else 1f)
                .clickable {
                    if (active) onWifiCancel()      // 连接/扫描中再点卡片 = 取消
                    else {
                        vm.clearConnFailure()
                        onClick()
                    }
                }
        ) {
            Row(
                // compact 卡高度锁定 60dp（同 USB 卡）：状态行出现不撑高
                Modifier
                    .then(if (compact) Modifier.height(60.dp) else Modifier)
                    .padding(
                        horizontal = if (compact) 12.dp else 16.dp,
                        vertical = if (compact) 0.dp else 16.dp
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(if (compact) 34.dp else 46.dp)
                        .clip(RoundedCornerShape(if (compact) 9.dp else 13.dp))
                        .background(primary.copy(alpha = if (dark) 0.20f else 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    WifiIcon(primary, Modifier.size(if (compact) 20.dp else 26.dp))
                }
                Spacer(Modifier.width(if (compact) 10.dp else 14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (compact) "Wi-Fi" else "Wi-Fi 无线",
                        style = if (compact) MaterialTheme.typography.titleSmall
                        else MaterialTheme.typography.titleMedium,
                        color = onSurface
                    )
                    if (!compact) {
                        Text(
                            if (hotspotOn) "已开热点 · 直接连接" else "无线连接 · 手机开热点或同一路由器",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    // 当前通道的连接/扫描状态行
                    if (progressText != null) {
                        Spacer(Modifier.height(3.dp))
                        Text(
                            progressText,
                            style = MaterialTheme.typography.labelSmall,
                            color = primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (progressText != null) {
                    CircularProgressIndicator(
                        color = primary,
                        strokeWidth = if (compact) 2.dp else 3.dp,
                        modifier = Modifier.size(if (compact) 18.dp else 24.dp)
                    )
                } else if (!compact) {
                    // 描边胶囊「扫描相机」：空闲时显示；连接中隐藏（状态行占位）
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(UiSpec.ROUND_SMALL))
                            .border(1.dp, primary.copy(alpha = 0.55f), RoundedCornerShape(UiSpec.ROUND_SMALL))
                            .clickable(enabled = !dimmed && !active) { onScanClick() }
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Text("扫描相机", style = MaterialTheme.typography.labelMedium, color = primary)
                    }
                }
            }
        }
    }