package com.vcampro.ultimate.ui.screens

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vcampro.ultimate.core.di.AppGraph
import com.vcampro.ultimate.core.i18n.LocalStrings
import com.vcampro.ultimate.core.ui.components.InfoRow
import com.vcampro.ultimate.core.ui.components.LabeledSlider
import com.vcampro.ultimate.core.ui.components.SectionTitle
import com.vcampro.ultimate.core.ui.components.StatusPill
import com.vcampro.ultimate.core.ui.components.VcamCard
import com.vcampro.ultimate.core.ui.components.VcamPrimaryButton
import com.vcampro.ultimate.core.ui.components.VcamScreen
import com.vcampro.ultimate.core.ui.theme.VcamPalette
import com.vcampro.ultimate.data.settings.VcamSettings
import com.vcampro.ultimate.domain.model.DeviceProfile
import com.vcampro.ultimate.domain.model.PerformanceMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun CameraStudioScreen(onBack: () -> Unit) {
    val strings = LocalStrings.current
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val deviceProfile by produceState<DeviceProfile?>(initialValue = null) {
        value = withContext(Dispatchers.IO) { AppGraph.device.analyze() }
    }
    val settingsState by AppGraph.settings.settings
        .collectAsStateWithLifecycle(initialValue = VcamSettings())
    val performanceMode = PerformanceMode.fromKey(settingsState.performanceMode)

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        hasCameraPermission = result[Manifest.permission.CAMERA] == true
        hasAudioPermission = result[Manifest.permission.RECORD_AUDIO] == true
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
            )
        }
    }

    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var flashMode by remember { mutableIntStateOf(ImageCapture.FLASH_MODE_OFF) }
    var timerSeconds by remember { mutableIntStateOf(0) }
    var showGrid by remember { mutableStateOf(settingsState.gridEnabled) }
    var qualityLabel by remember { mutableStateOf("FHD") }
    var zoom by remember { mutableFloatStateOf(0f) }
    var isRecording by remember { mutableStateOf(false) }
    var recordingSeconds by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf<String?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var activeRecording by remember { mutableStateOf<Recording?>(null) }
    var isCapturing by remember { mutableStateOf(false) }

    val imageCapture = remember(qualityLabel) {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .build()
    }
    val videoQuality = when (qualityLabel) {
        "UHD" -> Quality.UHD
        "HD" -> Quality.HD
        "SD" -> Quality.SD
        else -> Quality.FHD
    }
    val recorder = remember(qualityLabel) {
        Recorder.Builder()
            .setQualitySelector(QualitySelector.from(videoQuality))
            .build()
    }
    val videoCapture = remember(qualityLabel) { VideoCapture.withOutput(recorder) }

    LaunchedEffect(flashMode) { imageCapture.flashMode = flashMode }
    LaunchedEffect(zoom) { runCatching { camera?.cameraControl?.setLinearZoom(zoom) } }
    LaunchedEffect(showGrid) { AppGraph.settings.setGridEnabled(showGrid) }
    LaunchedEffect(isRecording) {
        recordingSeconds = 0
        while (isRecording) {
            delay(1000)
            recordingSeconds += 1
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            runCatching { activeRecording?.stop() }
            runCatching { activeRecording?.close() }
        }
    }

    fun capturePhoto() {
        if (isCapturing) return
        isCapturing = true
        scope.launch {
            if (timerSeconds > 0) delay(timerSeconds * 1000L)
            val name = "VCAM_${System.currentTimeMillis()}"
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "$name.jpg")
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/VCAM PRO")
            }
            val options = ImageCapture.OutputFileOptions.Builder(
                context.contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                values
            ).build()
            imageCapture.takePicture(
                options,
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        status = strings.photoSaved
                        isCapturing = false
                    }

                    override fun onError(exception: ImageCaptureException) {
                        status = exception.message ?: strings.failed
                        isCapturing = false
                    }
                }
            )
        }
    }

    fun toggleRecording() {
        if (isRecording) {
            runCatching { activeRecording?.stop() }
            isRecording = false
            return
        }
        if (!hasAudioPermission) {
            permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
        }
        val name = "VCAM_VID_${System.currentTimeMillis()}"
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "$name.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/VCAM PRO")
        }
        val outputOptions = MediaStoreOutputOptions.Builder(
            context.contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        ).setContentValues(values).build()

        runCatching {
            val pending = recorder.prepareRecording(context, outputOptions)
            val prepared = if (hasAudioPermission) pending.withAudioEnabled() else pending
            activeRecording = prepared.start(ContextCompat.getMainExecutor(context)) { event ->
                when (event) {
                    is VideoRecordEvent.Start -> {
                        isRecording = true
                        status = strings.recording
                    }

                    is VideoRecordEvent.Finalize -> {
                        isRecording = false
                        status = if (event.hasError()) {
                            "${strings.failed}: ${event.error}"
                        } else {
                            strings.videoSaved
                        }
                    }

                    else -> Unit
                }
            }
        }.onFailure { status = it.message }
    }

    VcamScreen(
        title = strings.cameraStudio,
        subtitle = deviceProfile?.let { "${it.model} · ${it.recommendedResolutionLabel}" },
        onBack = onBack
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            if (!hasCameraPermission) {
                VcamCard {
                    Text(text = strings.permissionRequired, style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = strings.permissionDenied,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    VcamPrimaryButton(
                        text = strings.grantPermission,
                        onClick = {
                            permissionLauncher.launch(
                                arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
                            )
                        }
                    )
                }
                return@Column
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 4f)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color.Black)
            ) {
                key(lensFacing, qualityLabel) {
                    AndroidView(
                        factory = { ctx ->
                            val view = PreviewView(ctx).apply {
                                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                                scaleType = PreviewView.ScaleType.FILL_CENTER
                            }
                            val providerFuture = ProcessCameraProvider.getInstance(ctx)
                            providerFuture.addListener({
                                runCatching {
                                    val provider = providerFuture.get()
                                    val preview = Preview.Builder().build().also {
                                        it.setSurfaceProvider(view.surfaceProvider)
                                    }
                                    provider.unbindAll()
                                    val bound = provider.bindToLifecycle(
                                        lifecycleOwner,
                                        CameraSelector.Builder()
                                            .requireLensFacing(lensFacing)
                                            .build(),
                                        preview,
                                        imageCapture,
                                        videoCapture
                                    )
                                    camera = bound
                                    runCatching { bound.cameraControl.setLinearZoom(zoom) }
                                }.onFailure { status = it.message ?: strings.cameraError }
                            }, ContextCompat.getMainExecutor(ctx))
                            view
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(3f / 4f)
                    )
                }

                if (showGrid) {
                    Canvas(modifier = Modifier.fillMaxWidth().aspectRatio(3f / 4f)) {
                        val color = Color.White.copy(alpha = 0.35f)
                        val stroke = 1.5f
                        for (index in 1..2) {
                            val x = size.width * index / 3f
                            val y = size.height * index / 3f
                            drawLine(color, Offset(x, 0f), Offset(x, size.height), stroke)
                            drawLine(color, Offset(0f, y), Offset(size.width, y), stroke)
                        }
                    }
                }

                if (isRecording) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(12.dp)
                            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(50))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(VcamPalette.Rose, RoundedCornerShape(50))
                        )
                        Spacer(modifier = Modifier.size(6.dp))
                        Text(
                            text = "%d:%02d".format(recordingSeconds / 60, recordingSeconds % 60),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )
                    }
                }

                if (isCapturing && timerSeconds > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "$timerSeconds s",
                            color = Color.White,
                            style = MaterialTheme.typography.headlineSmall
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = lensFacing == CameraSelector.LENS_FACING_BACK,
                    onClick = {
                        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                            CameraSelector.LENS_FACING_FRONT
                        } else {
                            CameraSelector.LENS_FACING_BACK
                        }
                    },
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.Cameraswitch,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.size(4.dp))
                            Text(
                                text = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                                    strings.backCamera
                                } else {
                                    strings.frontCamera
                                },
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                )
                FilterChip(
                    selected = flashMode != ImageCapture.FLASH_MODE_OFF,
                    onClick = {
                        flashMode = when (flashMode) {
                            ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_ON
                            ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_AUTO
                            else -> ImageCapture.FLASH_MODE_OFF
                        }
                    },
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (flashMode == ImageCapture.FLASH_MODE_OFF) {
                                    Icons.Filled.FlashOff
                                } else {
                                    Icons.Filled.FlashOn
                                },
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.size(4.dp))
                            Text(
                                text = when (flashMode) {
                                    ImageCapture.FLASH_MODE_ON -> strings.flashOn
                                    ImageCapture.FLASH_MODE_AUTO -> strings.flashAuto
                                    else -> strings.flashOff
                                },
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                )
                FilterChip(
                    selected = timerSeconds > 0,
                    onClick = {
                        timerSeconds = when (timerSeconds) {
                            0 -> 3
                            3 -> 10
                            else -> 0
                        }
                    },
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.Timer,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.size(4.dp))
                            Text(
                                text = if (timerSeconds == 0) strings.timerOff else "$timerSeconds s",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                )
                FilterChip(
                    selected = showGrid,
                    onClick = { showGrid = !showGrid },
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.GridOn,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.size(4.dp))
                            Text(strings.grid, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("SD", "HD", "FHD", "UHD").forEach { option ->
                    FilterChip(
                        selected = qualityLabel == option,
                        onClick = { qualityLabel = option },
                        label = { Text(option, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            VcamCard {
                LabeledSlider(
                    label = strings.zoom,
                    value = zoom,
                    valueRange = 0f..1f,
                    onValueChange = { zoom = it },
                    valueText = "%.0f%%".format(zoom * 100)
                )
                InfoRow(label = strings.quality, value = qualityLabel)
                InfoRow(
                    label = strings.autofocus,
                    value = if (camera != null) strings.on else strings.off
                )
                InfoRow(
                    label = strings.hdr,
                    value = if (deviceProfile?.supportsHdr == true) strings.on else strings.hdrUnsupported
                )
                InfoRow(label = strings.performanceMode, value = performanceMode.key)
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(modifier = Modifier.weight(1f)) {
                    VcamPrimaryButton(
                        text = strings.capturePhoto,
                        icon = Icons.Filled.PhotoCamera,
                        enabled = !isRecording && !isCapturing,
                        onClick = { capturePhoto() }
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    VcamPrimaryButton(
                        text = if (isRecording) strings.stopRecording else strings.recordVideo,
                        icon = if (isRecording) Icons.Filled.VideocamOff else Icons.Filled.Videocam,
                        onClick = { toggleRecording() }
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

            if (!hasAudioPermission) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = strings.microphonePermission,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusPill(
                    text = deviceProfile?.recommendedResolutionLabel ?: "-",
                    color = VcamPalette.Mint
                )
                StatusPill(
                    text = if (deviceProfile?.hasFlash == true) strings.flashAvailable else strings.flash,
                    color = VcamPalette.Amber
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}


