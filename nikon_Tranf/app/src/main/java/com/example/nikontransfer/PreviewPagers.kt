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


internal class PreviewZoomState {
        var scale by mutableStateOf(1f)
        var offset by mutableStateOf(Offset.Zero)
        var container by mutableStateOf(IntSize.Zero)

        fun clampOffset() {
            val maxX = (scale - 1f) * container.width / 2f
            val maxY = (scale - 1f) * container.height / 2f
            offset = Offset(offset.x.coerceIn(-maxX, maxX), offset.y.coerceIn(-maxY, maxY))
        }

        /** 松手吸附回 1x */
        suspend fun settle() {
            val s0 = scale
            val o0 = offset
            val anim = Animatable(0f)
            anim.animateTo(1f, tween(UiSpec.QUICK)) {
                val t = value
                scale = s0 + (1f - s0) * t
                offset = Offset(o0.x * (1f - t), o0.y * (1f - t))
            }
            scale = 1f
            offset = Offset.Zero
        }

        /** 以 tapPoint 为锚点动画缩放到目标倍率（双击定位放大/还原） */
        suspend fun zoomTo(targetScale: Float, tapPoint: Offset) {
            val s0 = scale
            val o0 = offset
            val center = Offset(container.width / 2f, container.height / 2f)
            val targetOffset = (tapPoint - center) * (1f - targetScale / s0) + o0 * (targetScale / s0)
            val anim = Animatable(0f)
            anim.animateTo(1f, tween(UiSpec.STANDARD)) {
                val t = value
                scale = s0 + (targetScale - s0) * t
                offset = Offset(
                    o0.x + (targetOffset.x - o0.x) * t,
                    o0.y + (targetOffset.y - o0.y) * t
                )
            }
            scale = targetScale
            clampOffset()
        }
    }

    /** 预览缩放手势：1x 纯滑动不消费（翻页处理）；放大后/捏合时消费（归图片），松手 ≤1.2 吸附回 1x。
     *  单击回调用于沉浸模式切换（任何缩放状态均响应）。 */
internal fun Modifier.previewZoomGestures(
        zoom: PreviewZoomState,
        onTap: () -> Unit = {}
    ): Modifier = this
        .onSizeChanged { zoom.container = it }
        .pointerInput(zoom) {
            coroutineScope {
                launch {
                    detectTapGestures(
                        onTap = { onTap() },
                        onDoubleTap = { tapPoint ->
                            launch {
                                if (zoom.scale > 1.01f) zoom.settle() else zoom.zoomTo(2.5f, tapPoint)
                            }
                        }
                    )
                }
                launch {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        var consuming = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.any { it.pressed }
                            if (!pressed) break
                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()
                            // 放大状态或出现捏合 → 手势归图片；1x 纯滑动不消费（让翻页处理）
                            if (zoom.scale > 1.01f || zoomChange != 1f) consuming = true
                            if (consuming) {
                                val newScale = (zoom.scale * zoomChange).coerceIn(1f, 5f)
                                val centroid = event.calculateCentroid(useCurrent = false)
                                val center = Offset(zoom.container.width / 2f, zoom.container.height / 2f)
                                val ratio = if (zoom.scale > 0f) newScale / zoom.scale else 1f
                                var newOffset = (centroid - center) * (1f - ratio) +
                                    zoom.offset * ratio + panChange
                                val maxX = (newScale - 1f) * zoom.container.width / 2f
                                val maxY = (newScale - 1f) * zoom.container.height / 2f
                                newOffset = Offset(
                                    newOffset.x.coerceIn(-maxX, maxX),
                                    newOffset.y.coerceIn(-maxY, maxY)
                                )
                                zoom.scale = newScale
                                zoom.offset = newOffset
                                event.changes.forEach { it.consume() }
                            }
                        }
                        if (zoom.scale in 1.01f..1.2f) launch { zoom.settle() }
                    }
                }
            }
        }

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
internal fun MainActivity.PairPager(
        initialIndex: Int,
        rows: List<PairRow>,
        onClose: (Int) -> Unit,
        onDownloadJpg: (PairRow) -> Unit,
        onDownloadNef: (PairRow) -> Unit
    ) {
        val pagerState = rememberPagerState(initialPage = initialIndex) { rows.size }
        // 高清预览：开关开启时，当前页 ±1 自动预取（合并模式取显示中的那张：JPG 优先）
        LaunchedEffect(pagerState.currentPage, vm.hiresOn) {
            if (vm.hiresOn) {
                val idx = pagerState.currentPage
                val targets = (maxOf(0, idx - 1)..minOf(rows.lastIndex, idx + 1)).mapNotNull { pi ->
                    rows.getOrNull(pi)?.let { p -> p.jpg ?: p.nef }
                }
                vm.requestHires(targets)
            }
        }
        DisposableEffect(Unit) { onDispose { vm.clearHires() } }
        DisposableEffect(Unit) {
            onDispose { onClose(pagerState.currentPage) }
        }
        // 沉浸模式：单击图片切换，翻页保持状态
        var immersive by remember { mutableStateOf(false) }
        Surface(Modifier.fillMaxSize(), color = Color(0x66000000)) {
            Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                // 图片层铺满整屏垫底，顶/底信息栏浮层叠加——EXIF/按钮高度变化不再挤压图片
                Column(Modifier.fillMaxWidth().zIndex(1f)) {
                AnimatedVisibility(
                    visible = !immersive,
                    enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
                    exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top)
                ) {
                // 顶部细进度条：当前页高清加载中（-1=结构解析期不定长，0..1=定向读取）。
                // 固定 3dp 占位：进度条出现/消失不改变布局高度，图片区域不被推挤跳动。
                val topKey = rows.getOrNull(pagerState.currentPage)?.let { p -> (p.jpg ?: p.nef)?.handle }
                val topProg = topKey?.let { vm.hiresProgress[it] }
                Box(Modifier.fillMaxWidth().height(3.dp)) {
                    if (vm.hiresOn && topProg != null) {
                        if (topProg < 0f) {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxSize(),
                                color = Color(0xFF4DB6AC),
                                trackColor = Color(0x334DB6AC)
                            )
                        } else {
                            LinearProgressIndicator(
                                progress = { topProg.coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxSize(),
                                color = Color(0xFF4DB6AC),
                                trackColor = Color(0x334DB6AC)
                            )
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { onClose(pagerState.currentPage) }) {
                        Icon(Icons.Filled.Close, contentDescription = "关闭", tint = Color.White)
                    }
                    Text(
                        "${pagerState.currentPage + 1} / ${rows.size}",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    // 右上角选择框：加入/移出多选（选中打勾）
                    val curPair = rows.getOrNull(pagerState.currentPage)
                    val selNow = curPair != null && curPair.key in vm.pairSelection.value
                    Box(
                        Modifier
                            .padding(end = 8.dp)
                            .size(24.dp)
                            .clip(RoundedCornerShape(UiSpec.ROUND_SMALL))
                            .background(if (selNow) Color(0xFF00695C) else Color.Transparent)
                            .border(
                                2.dp,
                                if (selNow) Color(0xFF00695C) else Color.White,
                                RoundedCornerShape(UiSpec.ROUND_SMALL)
                            )
                            .clickable { curPair?.let { vm.togglePair(it.key) } },
                        contentAlignment = Alignment.Center
                    ) {
                        if (selNow) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = "已选择",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
                }
                }
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    val pair = rows[page]
                    val zoom = remember { PreviewZoomState() }
                    Box(
                        Modifier
                            .fillMaxSize()
                            .onSizeChanged { zoom.container = it }
                            .previewZoomGestures(zoom, onTap = { immersive = !immersive }),
                        contentAlignment = Alignment.Center
                    ) {
                        val bmp = (pair.jpg ?: pair.nef)?.handle?.let { vm.hiresBitmap(it) }
                            ?: pair.previewBmp
                        if (bmp != null) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = pair.stamp,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        scaleX = zoom.scale
                                        scaleY = zoom.scale
                                        translationX = zoom.offset.x
                                        translationY = zoom.offset.y
                                    },
                                contentScale = ContentScale.Fit
                            )
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "RAW+JPG",
                                    color = Color(0xFF9E9E9E),
                                    style = MaterialTheme.typography.titleLarge
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "缩略图加载中…",
                                    color = Color(0xFF777777),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
                // 底部信息 + 双格式下载（沉浸模式隐藏）
                Column(Modifier.fillMaxSize().zIndex(1f)) {
                Spacer(Modifier.weight(1f))
                AnimatedVisibility(
                    visible = !immersive,
                    enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom),
                    exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom)
                ) {
                Column(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val pair = rows[pagerState.currentPage]
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (pair.hasJpg) MiniBadge("JPG", badgeColor("JPG"))
                        if (pair.hasNef) {
                            Spacer(Modifier.width(4.dp))
                            MiniBadge("NEF", badgeColor("NEF"))
                        }
                        Spacer(Modifier.width(8.dp))
                        // 原拍摄日期位置改为显示当前图片名称
                        Text(
                            (pair.jpg ?: pair.nef)?.name ?: "",
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (pair.jpgDownloaded || pair.nefDownloaded) {
                            val marks = buildString {
                                if (pair.jpgDownloaded) append("✓J ")
                                if (pair.nefDownloaded) append("✓N")
                            }.trim()
                            Text(marks, color = Color(0xFF4DB6AC), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    // 拍摄日期胶囊：与 EXIF 参数同风格（原位置下移，常显）
                    Text(
                        prettyStamp(pair.stamp),
                        Modifier
                            .clip(RoundedCornerShape(UiSpec.ROUND_SMALL))
                            .background(Color.White.copy(alpha = 0.12f))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                        color = Color(0xFFDDDDDD),
                        style = MaterialTheme.typography.labelSmall
                    )
                    // EXIF 参数胶囊（高清加载时解析；末项=镜头型号）：淡入+向下展开，出现不生硬
                    val exifParts = vm.exifLines[(pair.jpg ?: pair.nef)?.handle]
                    AnimatedVisibility(
                        visible = exifParts != null,
                        enter = fadeIn(tween(UiSpec.STANDARD)) + expandVertically(expandFrom = Alignment.Top),
                        exit = fadeOut(tween(UiSpec.QUICK)) + shrinkVertically(shrinkTowards = Alignment.Top)
                    ) {
                        if (exifParts != null) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                exifParts.forEach { p ->
                                    Text(
                                        p,
                                        Modifier
                                            .clip(RoundedCornerShape(UiSpec.ROUND_SMALL))
                                            .background(Color.White.copy(alpha = 0.12f))
                                            .padding(horizontal = 8.dp, vertical = 3.dp),
                                        color = Color(0xFFDDDDDD),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }
                    }
                    // 高清加载失败提示（静默回退缩略图，翻回该页自动重试）
                    val hiKey = (pair.jpg ?: pair.nef)?.handle
                    if (vm.hiresOn && hiKey != null && vm.hiresFailed.containsKey(hiKey)) {
                        Text(
                            "高清加载失败 · 翻回此页自动重试",
                            color = Color(0xFF777777),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    // 行高锁定 40dp：下载槽在按钮/进度胶囊间切换时信息区高度恒定不跳动
                    Row(
                        Modifier.fillMaxWidth().height(40.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 单格式成对：唯一按钮占满整排；双格式：各占一半
                        if (pair.hasJpg) DownloadStateSlot(
                            downloaded = pair.jpgDownloaded,
                            qItem = pair.jpg?.let { j ->
                                vm.downloadQueue.firstOrNull { it.handle == j.handle }
                            },
                            label = "下载 JPG",
                            onDownload = { onDownloadJpg(pair) },
                            onOpenInGallery = {
                                pair.jpg?.let { p -> openInGallery(p.name, p.type) }
                            },
                            modifier = Modifier.weight(1f)
                        )
                        if (pair.hasNef) DownloadStateSlot(
                            downloaded = pair.nefDownloaded,
                            qItem = pair.nef?.let { n ->
                                vm.downloadQueue.firstOrNull { it.handle == n.handle }
                            },
                            label = "下载 NEF",
                            onDownload = { onDownloadNef(pair) },
                            onOpenInGallery = {
                                pair.nef?.let { n -> openInGallery(n.name, n.type) }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                }
                }
            }
        }
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
internal fun MainActivity.PreviewPager(
        initialIndex: Int,
        rows: List<PhotoRow>,
        onClose: (Int) -> Unit,
        onDownload: (PhotoRow) -> Unit
    ) {
        val pagerState = rememberPagerState(initialPage = initialIndex) { rows.size }
        // 高清预览：开关开启时，当前页 ±1 自动预取
        LaunchedEffect(pagerState.currentPage, vm.hiresOn) {
            if (vm.hiresOn) {
                val idx = pagerState.currentPage
                val targets = (maxOf(0, idx - 1)..minOf(rows.lastIndex, idx + 1)).mapNotNull { rows.getOrNull(it) }
                vm.requestHires(targets)
            }
        }
        DisposableEffect(Unit) { onDispose { vm.clearHires() } }
        DisposableEffect(Unit) {
            onDispose { onClose(pagerState.currentPage) }
        }
        // 沉浸模式：单击图片切换，翻页保持状态
        var immersive by remember { mutableStateOf(false) }
        Surface(Modifier.fillMaxSize(), color = Color(0x66000000)) {
            Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                // 图片层铺满整屏垫底，顶/底信息栏浮层叠加——EXIF/按钮高度变化不再挤压图片
                Column(Modifier.fillMaxWidth().zIndex(1f)) {
                AnimatedVisibility(
                    visible = !immersive,
                    enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
                    exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top)
                ) {
                // 顶部细进度条：固定 3dp 占位，出现/消失不推挤图片区域（防跳动）
                val topRow = rows.getOrNull(pagerState.currentPage)
                val topProg = topRow?.let { vm.hiresProgress[it.handle] }
                Box(Modifier.fillMaxWidth().height(3.dp)) {
                    if (vm.hiresOn && topProg != null) {
                        if (topProg < 0f) {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxSize(),
                                color = Color(0xFF4DB6AC),
                                trackColor = Color(0x334DB6AC)
                            )
                        } else {
                            LinearProgressIndicator(
                                progress = { topProg.coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxSize(),
                                color = Color(0xFF4DB6AC),
                                trackColor = Color(0x334DB6AC)
                            )
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { onClose(pagerState.currentPage) }) {
                        Icon(Icons.Filled.Close, contentDescription = "关闭", tint = Color.White)
                    }
                    Text(
                        "${pagerState.currentPage + 1} / ${rows.size}",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    // 右上角选择框：加入/移出多选（选中打勾）
                    val curRow = rows.getOrNull(pagerState.currentPage)
                    val selNow = curRow != null && curRow.selected.value
                    Box(
                        Modifier
                            .padding(end = 8.dp)
                            .size(24.dp)
                            .clip(RoundedCornerShape(UiSpec.ROUND_SMALL))
                            .background(if (selNow) Color(0xFF00695C) else Color.Transparent)
                            .border(
                                2.dp,
                                if (selNow) Color(0xFF00695C) else Color.White,
                                RoundedCornerShape(UiSpec.ROUND_SMALL)
                            )
                            .clickable { curRow?.let { it.selected.value = !it.selected.value } },
                        contentAlignment = Alignment.Center
                    ) {
                        if (selNow) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = "已选择",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
                }
                }
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    val row = rows[page]
                    val zoom = remember { PreviewZoomState() }
                    Box(
                        Modifier
                            .fillMaxSize()
                            .onSizeChanged { zoom.container = it }
                            .previewZoomGestures(zoom, onTap = { immersive = !immersive }),
                        contentAlignment = Alignment.Center
                    ) {
                        val bmp = vm.hiresBitmap(row.handle) ?: row.preview.value
                        if (bmp != null) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = row.name,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        scaleX = zoom.scale
                                        scaleY = zoom.scale
                                        translationX = zoom.offset.x
                                        translationY = zoom.offset.y
                                    },
                                contentScale = ContentScale.Fit
                            )
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    row.type,
                                    color = Color(0xFF9E9E9E),
                                    style = MaterialTheme.typography.titleLarge
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "缩略图加载中…",
                                    color = Color(0xFF777777),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
                // 底部信息 + 操作（沉浸模式隐藏）
                Column(Modifier.fillMaxSize().zIndex(1f)) {
                Spacer(Modifier.weight(1f))
                AnimatedVisibility(
                    visible = !immersive,
                    enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom),
                    exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom)
                ) {
                Column(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val row = rows[pagerState.currentPage]
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            row.type,
                            Modifier
                                .background(badgeColor(row.type), RoundedCornerShape(UiSpec.ROUND_SMALL))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall
                        )
                        Spacer(Modifier.width(8.dp))
                        // 原拍摄日期位置改为显示当前图片名称
                        Text(
                            row.name,
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (row.downloaded.value)
                            Text(
                                "已下载 ✓",
                                color = Color(0xFF4DB6AC),
                                style = MaterialTheme.typography.labelSmall
                            )
                    }
                    // 拍摄日期胶囊：与 EXIF 参数同风格（原位置下移，常显）
                    Text(
                        prettyStamp(row.stamp),
                        Modifier
                            .clip(RoundedCornerShape(UiSpec.ROUND_SMALL))
                            .background(Color.White.copy(alpha = 0.12f))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                        color = Color(0xFFDDDDDD),
                        style = MaterialTheme.typography.labelSmall
                    )
                    // EXIF 参数胶囊（高清加载时解析；末项=镜头型号）：淡入+向下展开，出现不生硬
                    val exifParts = vm.exifLines[row.handle]
                    AnimatedVisibility(
                        visible = exifParts != null,
                        enter = fadeIn(tween(UiSpec.STANDARD)) + expandVertically(expandFrom = Alignment.Top),
                        exit = fadeOut(tween(UiSpec.QUICK)) + shrinkVertically(shrinkTowards = Alignment.Top)
                    ) {
                        if (exifParts != null) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                exifParts.forEach { p ->
                                    Text(
                                        p,
                                        Modifier
                                            .clip(RoundedCornerShape(UiSpec.ROUND_SMALL))
                                            .background(Color.White.copy(alpha = 0.12f))
                                            .padding(horizontal = 8.dp, vertical = 3.dp),
                                        color = Color(0xFFDDDDDD),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }
                    }
                    // 高清加载失败提示（静默回退缩略图，翻回该页自动重试）
                    if (vm.hiresOn && vm.hiresFailed.containsKey(row.handle)) {
                        Text(
                            "高清加载失败 · 翻回此页自动重试",
                            color = Color(0xFF777777),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    // 行高锁定 40dp：下载槽在按钮/进度胶囊间切换时信息区高度恒定不跳动
                    Row(
                        Modifier.fillMaxWidth().height(40.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DownloadStateSlot(
                            downloaded = row.downloaded.value,
                            qItem = vm.downloadQueue.firstOrNull { it.handle == row.handle },
                            label = "下载此照片",
                            onDownload = { onDownload(row) },
                            onOpenInGallery = { openInGallery(row.name, row.type) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                }
                }
            }
        }
    }