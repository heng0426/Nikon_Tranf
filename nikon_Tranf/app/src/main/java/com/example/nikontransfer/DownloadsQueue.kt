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


    /** 下载管理页：队列条目（状态/进度/重试/取消）+ 清空已完成 */
    @Composable
internal fun MainActivity.DownloadsScreen(onBack: () -> Unit) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                }
                Text("下载队列", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                val hasActive = vm.downloadQueue.any {
                    it.status.value in setOf(QStatus.QUEUED, QStatus.RUNNING)
                }
                if (hasActive) {
                    TextButton(onClick = { vm.cancelAllDownloads() }) { Text("全部取消") }
                }
                val hasFinished = vm.downloadQueue.any {
                    it.status.value in setOf(QStatus.DONE, QStatus.CANCELED)
                }
                if (hasFinished) {
                    TextButton(onClick = { vm.clearFinished() }) { Text("清空已完成") }
                }
            }
            // 汇总胶囊 + 总进度条：进度 = (已完成数 + 当前任务字节比例) / 未完成任务总数。
            // 不按字节累加——排队任务在开始下载前 total=0，按字节累加会让分母失真。
            if (vm.downloadQueue.isNotEmpty()) {
                val running = vm.downloadQueue.count { it.status.value == QStatus.RUNNING }
                val queued = vm.downloadQueue.count { it.status.value == QStatus.QUEUED }
                val done = vm.downloadQueue.count { it.status.value == QStatus.DONE }
                val failed = vm.downloadQueue.count { it.status.value == QStatus.FAILED }
                val denom = done + running + queued
                // 统计胶囊：只保留「排队中」「已完成」两项（高度固定，不随任务状态变化）
                val green = UiSpec.accent(vm.darkModeOn)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (queued > 0) {
                        Surface(shape = RoundedCornerShape(UiSpec.ROUND_SMALL), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)) {
                            RollingText(
                                "排队中 $queued",
                                Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    if (done > 0) {
                        Surface(shape = RoundedCornerShape(UiSpec.ROUND_SMALL), color = green.copy(alpha = 0.14f)) {
                            RollingText(
                                "已完成 $done",
                                Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                color = green
                            )
                        }
                    }
                }
                if (denom > 0) {
                    val runningItem = vm.downloadQueue.firstOrNull {
                        it.status.value == QStatus.RUNNING && it.total.value > 0
                    }
                    val runningFrac = runningItem?.let { it.got.value.toFloat() / it.total.value } ?: 0f
                    val overall = ((done + runningFrac) / denom).coerceIn(0f, 1f)
                    val green = UiSpec.accent(vm.darkModeOn)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LinearProgressIndicator(
                            progress = { overall },
                            modifier = Modifier.weight(1f),
                            color = green
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "$done/$denom",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            if (vm.downloadQueue.isEmpty()) {
                Text(
                    "暂无下载任务\n\n选择照片下载后，任务将显示在这里",
                    Modifier.fillMaxWidth().padding(vertical = 48.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
            LazyColumn(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(vm.downloadQueue, key = { it.handle }) { item ->
                    QueueRow(item)
                }
            }
        }
    }

    /** 队列行缩略图：handle → photoRows 既有预览位图（网格加载过即命中），无位图时兜底加载 */
    @Composable
internal fun MainActivity.QueueThumb(handle: Int) {
        val row = vm.photoRows.firstOrNull { it.handle == handle }
        LaunchedEffect(handle) { row?.let { vm.ensureThumb(it) } }
        val bmp = row?.preview?.value
        Box(
            Modifier
                .size(UiSpec.QUEUE_THUMB)
                .clip(RoundedCornerShape(UiSpec.ROUND_SMALL))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                DownloadIcon(MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    @Composable
internal fun MainActivity.QueueRow(item: QueueItem) {
        val st = item.status.value
        // 进度填充直接铺在卡片背景上：下载中按比例推进（浅绿），完成后整卡铺满（绿加深），
        // 与未下载行一眼区分；排队/失败/取消不铺色
        val fillFrac: Float
        val fillAlpha: Float
        when {
            st == QStatus.RUNNING && item.total.value > 0 -> {
                fillFrac = (item.got.value.toFloat() / item.total.value).coerceIn(0f, 1f)
                fillAlpha = 0.20f
            }
            st == QStatus.DONE -> {
                fillFrac = 1f
                fillAlpha = 0.30f
            }
            else -> {
                fillFrac = 0f
                fillAlpha = 0f
            }
        }
        Surface(
            shape = RoundedCornerShape(UiSpec.ROUND_LARGE),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ) {
            Box(Modifier.fillMaxWidth()) {
                if (fillFrac > 0f) {
                    val green = UiSpec.accent(vm.darkModeOn)
                    // matchParentSize 与 fillMaxWidth 直接链式组合会互相覆盖（填充恒满宽），
                    // 经 BoxWithConstraints 按比例换算宽度，填充层才能精确停在进度位置
                    BoxWithConstraints(Modifier.matchParentSize()) {
                        Box(
                            Modifier
                                .fillMaxHeight()
                                .width(maxWidth * fillFrac)
                                .background(green.copy(alpha = fillAlpha))
                        )
                    }
                }
                Row(
                    // 高度锁定 60dp（原实际高度）：下载中（有取消按钮）与已完成（无按钮）
                    // 卡片总高一致，状态切换不跳变；各元素在行内垂直居中
                    Modifier.fillMaxWidth().height(UiSpec.QUEUE_ROW_H).padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    QueueThumb(item.handle)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                item.type,
                                Modifier
                                    .background(badgeColor(item.type), RoundedCornerShape(UiSpec.ROUND_SMALL))
                                    .padding(horizontal = 5.dp, vertical = 2.dp),
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                item.name,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(Modifier.height(2.dp))
                        // 状态行固定 20dp 高：下载中胶囊/完成后胶囊/纯文字高度一致，卡片不因状态切换变高
                        Row(Modifier.height(20.dp), verticalAlignment = Alignment.CenterVertically) {
                            when (st) {
                                QStatus.QUEUED -> Text(
                                    "排队中",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelSmall
                                )
                                QStatus.RUNNING -> {
                                    // 胶囊显示进度/速度；tnum 等宽数字——数值每帧变化时文字不抖动
                                    val green = UiSpec.accent(vm.darkModeOn)
                                    Surface(
                                        shape = RoundedCornerShape(UiSpec.ROUND_SMALL),
                                        color = green.copy(alpha = 0.14f)
                                    ) {
                                        Text(
                                            "${humanSize(item.got.value)} / ${humanSize(item.total.value)} · ${item.speed.value}",
                                            Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                            color = MaterialTheme.colorScheme.primary,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontFeatureSettings = "tnum"
                                            )
                                        )
                                    }
                                }
                                QStatus.DONE -> {
                                    // 已完成胶囊 + 下载耗时胶囊（精确到 0.1s，tnum 等宽不抖动）
                                    val green = UiSpec.accent(vm.darkModeOn)
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(UiSpec.ROUND_SMALL),
                                            color = green.copy(alpha = 0.14f)
                                        ) {
                                            Text(
                                                "已完成 ✓",
                                                Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                                color = green,
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        }
                                        if (item.elapsedMs > 0) {
                                            Surface(
                                                shape = RoundedCornerShape(UiSpec.ROUND_SMALL),
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                            ) {
                                                Text(
                                                    "%.1fs".format(item.elapsedMs / 1000.0),
                                                    Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                                    color = MaterialTheme.colorScheme.primary,
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontFeatureSettings = "tnum"
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }
                                QStatus.FAILED -> Text(
                                    "失败",
                                    color = UiSpec.error(vm.darkModeOn),
                                    style = MaterialTheme.typography.labelSmall
                                )
                                QStatus.CANCELED -> Text(
                                    "已取消",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                    when (st) {
                        QStatus.QUEUED, QStatus.RUNNING -> IconButton(
                            onClick = { vm.cancelDownload(item) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "取消",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        QStatus.FAILED -> TextButton(onClick = { vm.retryDownload(item) }) { Text("重试") }
                        else -> {}
                    }
                }
            }
        }
    }

    /** 预览页下载槽位：按队列状态渲染（排队/按钮内进度/重试/已下载），与队列单一真相源联动。
     *  未下载=实心主色按钮；下载中=进度直接填充在按钮内（点按取消）；
     *  已下载=绿色描边空心按钮（与实心未下载态一眼区分），点按弹重新下载确认窗。 */
    @Composable
internal fun MainActivity.DownloadStateSlot(
        downloaded: Boolean,
        qItem: QueueItem?,
        label: String,
        enabled: Boolean = true,
        onDownload: () -> Unit,
        onOpenInGallery: (() -> Unit)? = null,
        modifier: Modifier = Modifier
    ) {
        val st = qItem?.status?.value
        when {
            // 已下载：实心绿胶囊 + ✓（未下载=主色实心胶囊），颜色与内容双重区分，实心底在照片背景上清晰
            downloaded || (qItem != null && st == QStatus.DONE) -> {
                var confirmRedownload by remember { mutableStateOf(false) }
                val green = UiSpec.accentDeep(vm.darkModeOn)
                Button(
                    onClick = { confirmRedownload = true },
                    modifier = modifier,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = green,
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        Icons.Filled.Check, contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("已下载")
                }
                if (confirmRedownload) {
                    AlertDialog(
                        onDismissRequest = { confirmRedownload = false },
                        title = { Text("已下载") },
                        text = { Text("该照片已下载，可打开相册查看，或重新下载。") },
                        confirmButton = {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (onOpenInGallery != null) {
                                    TextButton(onClick = {
                                        confirmRedownload = false
                                        onOpenInGallery.invoke()
                                    }) { Text("打开相册") }
                                }
                                TextButton(onClick = {
                                    confirmRedownload = false
                                    onDownload()
                                }) { Text("重新下载") }
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { confirmRedownload = false }) { Text("取消") }
                        }
                    )
                }
            }
            qItem != null && st == QStatus.RUNNING -> {
                // 进度条直接长在按钮上：胶囊外形不变，浅绿填充随进度推进（与下载队列同款配色），点按取消
                val frac = if (qItem.total.value > 0)
                    (qItem.got.value.toFloat() / qItem.total.value).coerceIn(0f, 1f) else 0f
                val green = UiSpec.accent(vm.darkModeOn)
                Box(
                    modifier
                        .height(40.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { vm.cancelDownload(qItem) }
                ) {
                    Box(
                        Modifier.fillMaxHeight().fillMaxWidth(frac)
                            .background(green.copy(alpha = 0.20f))
                    )
                    Row(
                        Modifier.matchParentSize().padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            // tnum 等宽数字：数值刷新时文字宽度稳定不抖动
                            "${humanSize(qItem.got.value)} / ${humanSize(qItem.total.value)}",
                            color = green,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFeatureSettings = "tnum"
                            ),
                            maxLines = 1
                        )
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            Icons.Filled.Close, contentDescription = "取消下载",
                            tint = green, modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
            qItem != null && st == QStatus.QUEUED -> {
                // 排队中：与下载中同款 40dp 胶囊（旧实现是无高度的文字盒，Row 顶对齐下视觉偏上）
                Box(
                    modifier
                        .height(40.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Text("排队中…", color = UiSpec.GREY_TEXT, style = MaterialTheme.typography.labelMedium)
                }
            }
            qItem != null && st == QStatus.FAILED -> Button(
                onClick = { vm.retryDownload(qItem) }, modifier = modifier
            ) { Text("下载失败") }
            qItem != null && st == QStatus.CANCELED -> Button(
                onClick = { vm.retryDownload(qItem) }, modifier = modifier
            ) { Text("重新下载") }
            else -> Button(onClick = onDownload, enabled = enabled, modifier = modifier) { Text(label) }
        }
    }