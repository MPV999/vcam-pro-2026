package com.vcampro.ultimate.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.vcampro.ultimate.core.di.AppGraph
import com.vcampro.ultimate.core.i18n.LocalStrings
import com.vcampro.ultimate.core.ui.components.EmptyState
import com.vcampro.ultimate.core.ui.components.InfoRow
import com.vcampro.ultimate.core.ui.components.LabeledSlider
import com.vcampro.ultimate.core.ui.components.ProcessingOverlay
import com.vcampro.ultimate.core.ui.components.SectionTitle
import com.vcampro.ultimate.core.ui.components.StatusPill
import com.vcampro.ultimate.core.ui.components.VcamCard
import com.vcampro.ultimate.core.ui.components.VcamPrimaryButton
import com.vcampro.ultimate.core.ui.components.VcamScreen
import com.vcampro.ultimate.core.ui.theme.VcamPalette
import com.vcampro.ultimate.core.ui.util.formatBytes
import com.vcampro.ultimate.core.ui.util.formatDuration
import com.vcampro.ultimate.data.media.VideoEditorEngine
import kotlinx.coroutines.launch

@Composable
fun VideoStudioScreen(onBack: () -> Unit) {
    val strings = LocalStrings.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val engine = remember { AppGraph.videoEditor }

    var sourceUri by remember { mutableStateOf<Uri?>(null) }
    var info by remember { mutableStateOf<VideoEditorEngine.VideoInfo?>(null) }
    var mergeQueue by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var trimStart by remember { mutableFloatStateOf(0f) }
    var trimEnd by remember { mutableFloatStateOf(1f) }
    var speed by remember { mutableFloatStateOf(1f) }
    var rotation by remember { mutableIntStateOf(0) }
    var targetHeight by remember { mutableIntStateOf(0) }
    var verticalOutput by remember { mutableStateOf(false) }
    var muteAudio by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<Float?>(null) }
    var status by remember { mutableStateOf<String?>(null) }

    val exoPlayer = remember { ExoPlayer.Builder(context).build() }
    DisposableEffect(Unit) {
        onDispose { exoPlayer.release() }
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            sourceUri = uri
        }
    }

    LaunchedEffect(sourceUri) {
        val uri = sourceUri ?: return@LaunchedEffect
        exoPlayer.setMediaItem(MediaItem.fromUri(uri))
        exoPlayer.prepare()
        info = engine.probe(uri)
        trimStart = 0f
        trimEnd = 1f
        speed = 1f
        rotation = 0
        targetHeight = 0
        muteAudio = false
    }

    fun runExport(options: VideoEditorEngine.ExportOptions, label: String) {
        val uri = sourceUri ?: return
        busy = true
        progress = null
        status = null
        scope.launch {
            val result = engine.export(uri, options, "VCAM_VIDEO_${System.currentTimeMillis()}") {
                progress = it
            }
            busy = false
            progress = null
            status = result.fold(
                onSuccess = { "$label: ${strings.savedTo} ${it.lastPathSegment}" },
                onFailure = { "${strings.failed}: ${it.message}" }
            )
        }
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        VcamScreen(
            title = strings.videoStudio,
            subtitle = info?.let { "${it.width}x${it.height} · ${it.durationLabel}" }
                ?: strings.noVideoSelected,
            onBack = onBack
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                if (sourceUri == null) {
                    VcamCard {
                        EmptyState(
                            icon = Icons.Filled.Movie,
                            title = strings.noVideoSelected,
                            message = strings.selectVideo
                        )
                        VcamPrimaryButton(
                            text = strings.selectVideo,
                            icon = Icons.Filled.Movie,
                            onClick = {
                                picker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                )
                            }
                        )
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    return@Column
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.Black)
                ) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                player = exoPlayer
                                useController = true
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatusPill(
                        text = "${strings.duration}: ${formatDuration(info?.durationMs ?: 0L)}",
                        color = VcamPalette.Mint
                    )
                    StatusPill(
                        text = "${info?.width ?: 0}x${info?.height ?: 0}",
                        color = VcamPalette.AccentBright
                    )
                    StatusPill(
                        text = formatBytes((info?.bitrate ?: 0).toLong() / 8L) + "/s",
                        color = VcamPalette.Amber
                    )
                }

                SectionTitle(strings.timeline)
                VcamCard {
                    LabeledSlider(
                        label = strings.startTime,
                        value = trimStart,
                        valueRange = 0f..0.95f,
                        onValueChange = { trimStart = it.coerceAtMost(trimEnd - 0.02f) },
                        valueText = formatDuration(((info?.durationMs ?: 0L) * trimStart).toLong())
                    )
                    LabeledSlider(
                        label = strings.endTime,
                        value = trimEnd,
                        valueRange = 0.05f..1f,
                        onValueChange = { trimEnd = it.coerceAtLeast(trimStart + 0.02f) },
                        valueText = formatDuration(((info?.durationMs ?: 0L) * trimEnd).toLong())
                    )
                    LabeledSlider(
                        label = strings.changeSpeed,
                        value = speed,
                        valueRange = 0.5f..2f,
                        steps = 5,
                        onValueChange = { speed = it },
                        valueText = "%.1fx".format(speed)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(0, 480, 720, 1080).forEach { height ->
                            FilterChip(
                                selected = targetHeight == height && !verticalOutput,
                                onClick = {
                                    targetHeight = height
                                    verticalOutput = false
                                },
                                label = {
                                    Text(
                                        text = if (height == 0) strings.original else "${height}p",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            )
                        }
                        FilterChip(
                            selected = verticalOutput,
                            onClick = {
                                verticalOutput = true
                                targetHeight = 0
                            },
                            label = {
                                Text("1080x1920", style = MaterialTheme.typography.labelSmall)
                            }
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(0, 90, 180, 270).forEach { degrees ->
                            FilterChip(
                                selected = rotation == degrees,
                                onClick = { rotation = degrees },
                                label = {
                                    Text(
                                        text = "$degrees°",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            )
                        }
                        FilterChip(
                            selected = muteAudio,
                            onClick = { muteAudio = !muteAudio },
                            label = { Text(strings.muteAudio) },
                            leadingIcon = {
                                Icon(Icons.Filled.VolumeOff, null, Modifier.size(14.dp))
                            }
                        )
                    }
                }

                SectionTitle(strings.quickActions)
                VcamCard {
                    val durationMs = info?.durationMs ?: 0L
                    val startMs = (durationMs * trimStart).toLong()
                    val endMs = (durationMs * trimEnd).toLong()

                    AssistChip(
                        onClick = {
                            val uri = sourceUri ?: return@AssistChip
                            busy = true
                            scope.launch {
                                val result = engine.trimFast(
                                    uri,
                                    startMs,
                                    endMs,
                                    "VCAM_TRIM_${System.currentTimeMillis()}"
                                )
                                busy = false
                                status = result.fold(
                                    onSuccess = { strings.videoExported },
                                    onFailure = { "${strings.failed}: ${it.message}" }
                                )
                            }
                        },
                        label = { Text(strings.trim) },
                        leadingIcon = { Icon(Icons.Filled.ContentCut, null, Modifier.size(16.dp)) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    AssistChip(
                        onClick = {
                            val uri = sourceUri ?: return@AssistChip
                            busy = true
                            scope.launch {
                                val result = engine.extractFrame(
                                    uri,
                                    (startMs + endMs) / 2,
                                    "VCAM_FRAME_${System.currentTimeMillis()}"
                                )
                                busy = false
                                status = result.fold(
                                    onSuccess = { strings.frameSaved },
                                    onFailure = { "${strings.failed}: ${it.message}" }
                                )
                            }
                        },
                        label = { Text(strings.extractFrame) },
                        leadingIcon = { Icon(Icons.Filled.Image, null, Modifier.size(16.dp)) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    AssistChip(
                        onClick = {
                            val uri = sourceUri ?: return@AssistChip
                            mergeQueue = mergeQueue + uri
                            status = "${strings.merge}: ${mergeQueue.size}"
                        },
                        label = { Text("${strings.merge} (${mergeQueue.size})") },
                        leadingIcon = { Icon(Icons.Filled.MergeType, null, Modifier.size(16.dp)) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    AssistChip(
                        onClick = {
                            if (mergeQueue.size < 2) {
                                status = "${strings.merge}: cần ít nhất 2 video"
                                return@AssistChip
                            }
                            busy = true
                            scope.launch {
                                val result = engine.merge(
                                    mergeQueue,
                                    "VCAM_MERGE_${System.currentTimeMillis()}"
                                )
                                busy = false
                                status = result.fold(
                                    onSuccess = {
                                        mergeQueue = emptyList()
                                        strings.videoExported
                                    },
                                    onFailure = { "${strings.failed}: ${it.message}" }
                                )
                            }
                        },
                        label = { Text("${strings.merge} → ${strings.export}") },
                        leadingIcon = { Icon(Icons.Filled.MergeType, null, Modifier.size(16.dp)) }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(modifier = Modifier.weight(1f)) {
                        VcamPrimaryButton(
                            text = strings.exportVideo,
                            icon = Icons.Filled.SaveAlt,
                            onClick = {
                                runExport(
                                    VideoEditorEngine.ExportOptions(
                                        trimStartMs = ((info?.durationMs ?: 0L) * trimStart).toLong(),
                                        trimEndMs = ((info?.durationMs ?: 0L) * trimEnd).toLong(),
                                        targetWidth = when {
                                            verticalOutput -> 1080
                                            targetHeight > 0 ->
                                                ((info?.width ?: 16) * targetHeight / (info?.height ?: 9))

                                            else -> 0
                                        },
                                        targetHeight = if (verticalOutput) 1920 else targetHeight,
                                        rotationDegrees = rotation,
                                        speed = speed,
                                        muteAudio = muteAudio
                                    ),
                                    strings.exportVideo
                                )
                            }
                        )
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        VcamPrimaryButton(
                            text = strings.compress,
                            icon = Icons.Filled.Speed,
                            onClick = {
                                runExport(
                                    VideoEditorEngine.ExportOptions(
                                        trimStartMs = ((info?.durationMs ?: 0L) * trimStart).toLong(),
                                        trimEndMs = ((info?.durationMs ?: 0L) * trimEnd).toLong(),
                                        targetWidth = 1280,
                                        targetHeight = 720,
                                        muteAudio = muteAudio
                                    ),
                                    strings.compress
                                )
                            }
                        )
                    }
                }

                if (status != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = status ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                VcamCard {
                    Text(
                        text = strings.videoEditorNote,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    InfoRow(
                        label = strings.audio,
                        value = if (muteAudio) strings.off else strings.on
                    )
                    InfoRow(
                        label = strings.quality,
                        value = when {
                            verticalOutput -> "1080x1920 (dọc)"
                            targetHeight == 0 -> strings.original
                            else -> "${targetHeight}p"
                        }
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        ProcessingOverlay(
            visible = busy,
            label = if (progress != null) {
                "${strings.processing} ${(progress!! * 100).toInt()}%"
            } else {
                strings.processing
            },
            progress = progress,
            onCancel = {
                engine.cancel()
                busy = false
                progress = null
                status = strings.cancelProcessing
            },
            cancelLabel = strings.cancelProcessing
        )
    }
}
