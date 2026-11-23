package com.vcampro.ultimate.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.vcampro.sdk.CameraSource
import com.vcampro.sdk.FrameConfiguration
import com.vcampro.sdk.FrameConsumer
import com.vcampro.sdk.FrameStats
import com.vcampro.sdk.ImageConverter
import com.vcampro.sdk.ProcessedFrameSource
import com.vcampro.sdk.SessionState
import com.vcampro.sdk.VcamSdk
import com.vcampro.sdk.VideoFileSource
import com.vcampro.sdk.VirtualCameraSession
import com.vcampro.sdk.YuvConverter
import com.vcampro.ultimate.core.i18n.LocalStrings
import com.vcampro.ultimate.core.ui.components.InfoRow
import com.vcampro.ultimate.core.ui.components.LabeledSlider
import com.vcampro.ultimate.core.ui.components.SectionTitle
import com.vcampro.ultimate.core.ui.components.StatTile
import com.vcampro.ultimate.core.ui.components.StatusPill
import com.vcampro.ultimate.core.ui.components.VcamCard
import com.vcampro.ultimate.core.ui.components.VcamPrimaryButton
import com.vcampro.ultimate.core.ui.components.VcamScreen
import com.vcampro.ultimate.core.ui.theme.VcamPalette
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@Composable
fun VirtualCameraStudioScreen(onBack: () -> Unit) {
    val strings = LocalStrings.current
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val session: VirtualCameraSession = remember { VcamSdk.createSession(context) }
    var activeSource by remember { mutableStateOf<CameraSource?>(null) }
    var processedSource by remember { mutableStateOf<ProcessedFrameSource?>(null) }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result -> hasCameraPermission = result[Manifest.permission.CAMERA] == true }

    var sourceType by remember { mutableStateOf("camera_back") }
    var pickedUri by remember { mutableStateOf<Uri?>(null) }
    var resolution by remember { mutableStateOf("720p") }
    var fps by remember { mutableIntStateOf(30) }
    var looping by remember { mutableStateOf(true) }
    var speed by remember { mutableFloatStateOf(1f) }
    var sessionState by remember { mutableStateOf<SessionState>(SessionState.IDLE) }
    var stats by remember { mutableStateOf(FrameStats()) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var demoDeliveredCount by remember { mutableLongStateOf(0L) }
    val demoDelivered = remember { java.util.concurrent.atomic.AtomicLong(0L) }
    var consumerCount by remember { mutableIntStateOf(0) }
    var seekFraction by remember { mutableFloatStateOf(0f) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var sourceState by remember { mutableStateOf("") }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) pickedUri = uri }

    val previewConsumer = remember {
        object : FrameConsumer {
            private var lastPreviewNanos = 0L
            override fun onFrame(frame: com.vcampro.sdk.VideoFrame) {
                demoDelivered.incrementAndGet()
                val now = System.nanoTime()
                if (now - lastPreviewNanos < 150_000_000L) return
                lastPreviewNanos = now
                val targetWidth = (frame.width / 2).coerceAtLeast(2)
                val targetHeight = (frame.height / 2).coerceAtLeast(2)
                val scaled = YuvConverter.scaleNv21(
                    frame.data,
                    frame.width,
                    frame.height,
                    targetWidth,
                    targetHeight
                )
                previewBitmap = ImageConverter.nv21ToBitmap(scaled, targetWidth, targetHeight)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { session.release() }
        }
    }

    LaunchedEffect(session) {
        session.state.collect { sessionState = it }
    }
    LaunchedEffect(session) {
        session.stats.collect { stats = it }
    }
    LaunchedEffect(session) {
        while (true) {
            demoDeliveredCount = demoDelivered.get()
            kotlinx.coroutines.delay(500)
        }
    }
    LaunchedEffect(activeSource) {
        val source = activeSource ?: return@LaunchedEffect
        source.state.collect { sourceState = it.name }
    }

    fun startSession() {
        scope.launch {
            if (sourceType.startsWith("camera") && !hasCameraPermission) {
                permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA))
                return@launch
            }
            val config = if (resolution == "1080p") {
                FrameConfiguration(width = 1920, height = 1080, fps = fps)
            } else {
                FrameConfiguration(width = 1280, height = 720, fps = fps)
            }
            session.setConfiguration(config)
            val source: CameraSource? = when (sourceType) {
                "camera_front" -> VcamSdk.createPhysicalCameraSource(
                    context,
                    lifecycleOwner,
                    CameraSelector.LENS_FACING_FRONT
                )

                "camera_back" -> VcamSdk.createPhysicalCameraSource(
                    context,
                    lifecycleOwner,
                    CameraSelector.LENS_FACING_BACK
                )

                "video" -> {
                    val uri = pickedUri
                    if (uri == null) {
                        status = strings.selectFile
                        null
                    } else {
                        VcamSdk.createVideoFileSource(context, uri)
                    }
                }

                "image" -> {
                    val uri = pickedUri
                    if (uri == null) {
                        status = strings.selectFile
                        null
                    } else {
                        VcamSdk.createImageFileSource(context, uri)
                    }
                }

                else -> VcamSdk.createProcessedFrameSource().also { processedSource = it }
            }
            if (source == null) return@launch

            activeSource = source
            session.addConsumer("preview", previewConsumer)
            consumerCount = session.consumerIds().size
            session.setSource(source)
            session.start()
            session.setLooping(looping)
            session.setSpeed(speed)

            if (source is VideoFileSource) {
                durationMs = source.durationUs / 1000L
            }
            if (source is ProcessedFrameSource) {
                source.submitBitmap(createTestPattern(config.width, config.height))
            }
            status = null
        }
    }

    VcamScreen(
        title = strings.virtualCameraStudio,
        subtitle = strings.virtualCamera,
        onBack = onBack
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatusPill(text = sessionState.name, color = VcamPalette.Accent)
                StatusPill(text = "SDK ${VcamSdk.VERSION}", color = VcamPalette.Mint)
                StatusPill(text = "${stats.measuredFps.toInt()} fps", color = VcamPalette.Amber)
            }

            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(9f / 16f)
                    .heightIn(max = 340.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                val bitmap = previewBitmap
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = strings.framePreview,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Filled.Movie,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.size(42.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = strings.framePreview,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }
                }
            }

            SectionTitle(strings.sessionStats)
            VcamCard {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile(
                        label = strings.framesDelivered,
                        value = stats.deliveredFrames.toString(),
                        modifier = Modifier.weight(1f)
                    )
                    StatTile(
                        label = strings.droppedFrames,
                        value = stats.droppedFrames.toString(),
                        modifier = Modifier.weight(1f),
                        accent = VcamPalette.Rose
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile(
                        label = strings.fpsLabel,
                        value = "%.1f".format(stats.measuredFps),
                        modifier = Modifier.weight(1f),
                        accent = VcamPalette.Mint
                    )
                    StatTile(
                        label = strings.consumerLatency,
                        value = "%.1f ms".format(stats.averageConsumerMillis),
                        modifier = Modifier.weight(1f),
                        accent = VcamPalette.Amber
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                InfoRow(label = "Consumer", value = "$consumerCount · $demoDeliveredCount frames")
                InfoRow(label = "Source", value = sourceState.ifBlank { "-" })
                InfoRow(
                    label = strings.resolution,
                    value = if (resolution == "1080p") "1920x1080" else "1280x720"
                )
                InfoRow(label = strings.fpsLabel, value = fps.toString())
            }

            SectionTitle(strings.sourceType)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "camera_back" to strings.sourcePhysicalBack,
                    "camera_front" to strings.sourcePhysicalFront,
                    "video" to strings.sourceVideo,
                    "image" to strings.sourceImage,
                    "processed" to strings.sourceProcessed
                ).forEach { (key, label) ->
                    FilterChip(
                        selected = sourceType == key,
                        onClick = { sourceType = key },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            if (sourceType == "video" || sourceType == "image") {
                Spacer(modifier = Modifier.height(8.dp))
                VcamPrimaryButton(
                    text = if (pickedUri == null) strings.selectFile else pickedUri!!.lastPathSegment ?: strings.selectFile,
                    icon = if (sourceType == "video") Icons.Filled.Movie else Icons.Filled.Image,
                    onClick = {
                        picker.launch(
                            PickVisualMediaRequest(
                                if (sourceType == "video") {
                                    ActivityResultContracts.PickVisualMedia.VideoOnly
                                } else {
                                    ActivityResultContracts.PickVisualMedia.ImageOnly
                                }
                            )
                        )
                    }
                )
            }

            SectionTitle(strings.configuration)
            VcamCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("720p", "1080p").forEach { option ->
                        FilterChip(
                            selected = resolution == option,
                            onClick = { resolution = option },
                            label = {
                                Text(option, style = MaterialTheme.typography.labelSmall)
                            }
                        )
                    }
                }
                LabeledSlider(
                    label = strings.fpsLabel,
                    value = fps.toFloat(),
                    valueRange = 15f..60f,
                    steps = 8,
                    onValueChange = { fps = it.toInt() },
                    valueText = "$fps"
                )
                LabeledSlider(
                    label = strings.changeSpeed,
                    value = speed,
                    valueRange = 0.5f..2f,
                    steps = 5,
                    onValueChange = {
                        speed = it
                        session.setSpeed(it)
                    },
                    valueText = "%.1fx".format(speed)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = looping,
                        onClick = {
                            looping = !looping
                            session.setLooping(looping)
                        },
                        label = { Text(strings.loop) }
                    )
                }
                if (durationMs > 0L) {
                    LabeledSlider(
                        label = strings.seek,
                        value = seekFraction,
                        valueRange = 0f..1f,
                        onValueChange = {
                            seekFraction = it
                            session.seekTo((durationMs * it).toLong() * 1000L)
                        },
                        valueText = "%d:%02d".format(
                            (durationMs * seekFraction / 60000).toInt(),
                            ((durationMs * seekFraction / 1000) % 60).toInt()
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(modifier = Modifier.weight(1f)) {
                    VcamPrimaryButton(
                        text = if (sessionState == SessionState.RUNNING) {
                            strings.pause
                        } else {
                            strings.startSession
                        },
                        icon = if (sessionState == SessionState.RUNNING) {
                            Icons.Filled.Pause
                        } else {
                            Icons.Filled.PlayArrow
                        },
                        onClick = {
                            if (sessionState == SessionState.RUNNING) {
                                session.pause()
                            } else if (activeSource == null) {
                                startSession()
                            } else {
                                session.resume()
                            }
                        }
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    VcamPrimaryButton(
                        text = strings.stopSession,
                        icon = Icons.Filled.Stop,
                        onClick = {
                            session.stop()
                            activeSource = null
                            previewBitmap = null
                            status = strings.sessionError
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            VcamPrimaryButton(
                text = strings.addConsumer,
                icon = Icons.Filled.Add,
                onClick = {
                    val id = "consumer-${System.currentTimeMillis()}"
                    session.addConsumer(id, FrameConsumer { })
                    consumerCount = session.consumerIds().size
                }
            )

            if (status != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = status ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            VcamCard {
                Text(
                    text = strings.virtualCamNote,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

private fun createTestPattern(width: Int, height: Int): Bitmap {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.shader = LinearGradient(
        0f,
        0f,
        width.toFloat(),
        height.toFloat(),
        intArrayOf(0xFF0B1220.toInt(), 0xFF4F8CFF.toInt(), 0xFF39D9A0.toInt()),
        null,
        Shader.TileMode.CLAMP
    )
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    paint.shader = null
    paint.color = android.graphics.Color.WHITE
    paint.textSize = height / 9f
    paint.textAlign = Paint.Align.CENTER
    canvas.drawText("VCAM PRO", width / 2f, height / 2f, paint)
    paint.textSize = height / 24f
    canvas.drawText("${width}x$height", width / 2f, height / 2f + height / 9f, paint)
    return bitmap
}
