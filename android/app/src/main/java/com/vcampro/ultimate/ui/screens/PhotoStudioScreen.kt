package com.vcampro.ultimate.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Filter
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Rotate90DegreesCcw
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vcampro.ultimate.core.di.AppGraph
import com.vcampro.ultimate.core.i18n.LocalStrings
import com.vcampro.ultimate.core.ui.components.EmptyState
import com.vcampro.ultimate.core.ui.components.InfoRow
import com.vcampro.ultimate.core.ui.components.LabeledSlider
import com.vcampro.ultimate.core.ui.components.ProcessingOverlay
import com.vcampro.ultimate.core.ui.components.SectionTitle
import com.vcampro.ultimate.core.ui.components.VcamCard
import com.vcampro.ultimate.core.ui.components.VcamPrimaryButton
import com.vcampro.ultimate.core.ui.components.VcamScreen
import com.vcampro.ultimate.data.media.PhotoOp
import com.vcampro.ultimate.data.settings.VcamSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun PhotoStudioScreen(onBack: () -> Unit) {
    val strings = LocalStrings.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val engine = remember { AppGraph.photoEditor }
    val settings by AppGraph.settings.settings
        .collectAsStateWithLifecycle(initialValue = VcamSettings())

    var sourceUri by remember { mutableStateOf<Uri?>(null) }
    var previewSource by remember { mutableStateOf<Bitmap?>(null) }
    var fullSource by remember { mutableStateOf<Bitmap?>(null) }
    var rendered by remember { mutableStateOf<Bitmap?>(null) }
    val history = remember { mutableStateListOf<PhotoOp>() }
    val redoStack = remember { mutableStateListOf<PhotoOp>() }

    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var showCropDialog by remember { mutableStateOf(false) }
    var showResizeDialog by remember { mutableStateOf(false) }
    var showMetadataDialog by remember { mutableStateOf(false) }
    var metadata by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }

    var brightness by remember { mutableFloatStateOf(0f) }
    var contrast by remember { mutableFloatStateOf(1f) }
    var saturation by remember { mutableFloatStateOf(1f) }
    var temperature by remember { mutableFloatStateOf(0f) }
    var exposure by remember { mutableFloatStateOf(0f) }
    var filterId by remember { mutableStateOf("none") }
    var watermarkEnabled by remember { mutableStateOf(settings.watermarkEnabled) }
    var watermarkText by remember { mutableStateOf(settings.watermarkText) }
    var quality by remember { mutableIntStateOf(settings.jpegQuality) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) sourceUri = uri }

    LaunchedEffect(sourceUri) {
        val uri = sourceUri ?: return@LaunchedEffect
        busy = true
        val loaded = engine.loadBitmap(context, uri, 2048)
        fullSource = loaded
        previewSource = loaded?.let { bitmap ->
            if (bitmap.width > 1200 || bitmap.height > 1200) {
                val ratio = minOf(1200f / bitmap.width, 1200f / bitmap.height)
                Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * ratio).toInt().coerceAtLeast(1),
                    (bitmap.height * ratio).toInt().coerceAtLeast(1),
                    true
                )
            } else {
                bitmap
            }
        }
        history.clear()
        redoStack.clear()
        brightness = 0f
        contrast = 1f
        saturation = 1f
        temperature = 0f
        exposure = 0f
        filterId = "none"
        metadata = engine.metadata(context, uri)
        busy = false
        if (loaded == null) status = strings.failed
    }

    val currentOps: List<PhotoOp> = remember(
        history.size,
        brightness,
        contrast,
        saturation,
        temperature,
        exposure,
        filterId,
        watermarkEnabled,
        watermarkText
    ) {
        buildList {
            addAll(history)
            if (brightness != 0f || contrast != 1f || saturation != 1f ||
                temperature != 0f || exposure != 0f
            ) {
                add(
                    PhotoOp.Adjust(
                        brightness = brightness,
                        contrast = contrast,
                        saturation = saturation,
                        temperature = temperature,
                        exposure = exposure
                    )
                )
            }
            if (filterId != "none") add(PhotoOp.Filter(filterId))
            if (watermarkEnabled && watermarkText.isNotBlank()) {
                add(PhotoOp.Watermark(watermarkText))
            }
        }
    }

    LaunchedEffect(currentOps, previewSource) {
        val source = previewSource ?: return@LaunchedEffect
        rendered = withContext(Dispatchers.Default) { engine.render(source, currentOps) }
    }

    fun export() {
        val full = fullSource ?: return
        busy = true
        scope.launch {
            val result = withContext(Dispatchers.Default) { engine.render(full, currentOps) }
            val uri = engine.export(
                context,
                result,
                "VCAM_EDIT_${System.currentTimeMillis()}",
                quality
            )
            busy = false
            status = if (uri != null) strings.photoExported else strings.failed
        }
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        VcamScreen(
            title = strings.photoStudio,
            subtitle = previewSource?.let { "${it.width}x${it.height}" } ?: strings.noImageSelected,
            onBack = onBack
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                if (rendered == null) {
                    VcamCard {
                        EmptyState(
                            icon = Icons.Filled.Image,
                            title = strings.noImageSelected,
                            message = strings.selectPhoto
                        )
                        VcamPrimaryButton(
                            text = strings.selectPhoto,
                            icon = Icons.Filled.Image,
                            onClick = {
                                picker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                        )
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    return@Column
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Image(
                        bitmap = rendered!!.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AssistChip(
                        onClick = {
                            picker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        label = { Text(strings.selectPhoto) },
                        leadingIcon = { Icon(Icons.Filled.Image, null, Modifier.size(16.dp)) }
                    )
                    AssistChip(
                        onClick = {
                            if (history.isNotEmpty()) {
                                redoStack.add(history.removeAt(history.lastIndex))
                            }
                        },
                        label = { Text(strings.undo) },
                        leadingIcon = { Icon(Icons.Filled.Undo, null, Modifier.size(16.dp)) }
                    )
                    AssistChip(
                        onClick = {
                            if (redoStack.isNotEmpty()) {
                                history.add(redoStack.removeAt(redoStack.lastIndex))
                            }
                        },
                        label = { Text(strings.redo) },
                        leadingIcon = { Icon(Icons.Filled.Redo, null, Modifier.size(16.dp)) }
                    )
                    AssistChip(
                        onClick = { showMetadataDialog = true },
                        label = { Text(strings.metadata) },
                        leadingIcon = { Icon(Icons.Filled.Info, null, Modifier.size(16.dp)) }
                    )
                }

                SectionTitle(strings.timeline)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AssistChip(
                        onClick = { history.add(PhotoOp.Rotate(90)) },
                        label = { Text("${strings.rotate} 90°") },
                        leadingIcon = {
                            Icon(Icons.Filled.Rotate90DegreesCcw, null, Modifier.size(16.dp))
                        }
                    )
                    AssistChip(
                        onClick = { history.add(PhotoOp.Flip(horizontal = true)) },
                        label = { Text(strings.flip) }
                    )
                    AssistChip(
                        onClick = { showCropDialog = true },
                        label = { Text(strings.crop) },
                        leadingIcon = { Icon(Icons.Filled.Crop, null, Modifier.size(16.dp)) }
                    )
                    AssistChip(
                        onClick = { showResizeDialog = true },
                        label = { Text(strings.resize) }
                    )
                    AssistChip(
                        onClick = { history.add(PhotoOp.Sharpen(0.6f)) },
                        label = { Text(strings.sharpness) },
                        leadingIcon = { Icon(Icons.Filled.Tune, null, Modifier.size(16.dp)) }
                    )
                    AssistChip(
                        onClick = { history.add(PhotoOp.Blur(4)) },
                        label = { Text(strings.blur) },
                        leadingIcon = { Icon(Icons.Filled.BlurOn, null, Modifier.size(16.dp)) }
                    )
                    AssistChip(
                        onClick = {
                            busy = true
                            scope.launch {
                                val source = previewSource
                                if (source != null) {
                                    val enhanced = withContext(Dispatchers.Default) {
                                        engine.enhance(source)
                                    }
                                    rendered = enhanced
                                }
                                busy = false
                            }
                        },
                        label = { Text(strings.enhancement) }
                    )
                }

                SectionTitle(strings.apply)
                VcamCard {
                    LabeledSlider(
                        label = strings.brightness,
                        value = brightness,
                        valueRange = -0.5f..0.5f,
                        onValueChange = { brightness = it }
                    )
                    LabeledSlider(
                        label = strings.contrast,
                        value = contrast,
                        valueRange = 0.5f..2f,
                        onValueChange = { contrast = it }
                    )
                    LabeledSlider(
                        label = strings.saturation,
                        value = saturation,
                        valueRange = 0f..2f,
                        onValueChange = { saturation = it }
                    )
                    LabeledSlider(
                        label = strings.temperature,
                        value = temperature,
                        valueRange = -1f..1f,
                        onValueChange = { temperature = it }
                    )
                    LabeledSlider(
                        label = strings.exposureAdj,
                        value = exposure,
                        valueRange = -0.4f..0.4f,
                        onValueChange = { exposure = it }
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(onClick = {
                            brightness = 0f
                            contrast = 1f
                            saturation = 1f
                            temperature = 0f
                            exposure = 0f
                        }) { Text(strings.reset) }
                        TextButton(onClick = {
                            history.add(
                                PhotoOp.Adjust(brightness, contrast, saturation, temperature, exposure)
                            )
                            brightness = 0f
                            contrast = 1f
                            saturation = 1f
                            temperature = 0f
                            exposure = 0f
                        }) { Text(strings.apply) }
                    }
                }

                SectionTitle(strings.filters)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val filters = listOf(
                        "none" to strings.filterNone,
                        "vivid" to strings.filterVivid,
                        "mono" to strings.filterMono,
                        "warm" to strings.filterWarm,
                        "cool" to strings.filterCool,
                        "vintage" to strings.filterVintage,
                        "cinema" to strings.filterCinema
                    )
                    filters.forEach { (id, label) ->
                        FilterChip(
                            selected = filterId == id,
                            onClick = { filterId = id },
                            label = {
                                Text(label, style = MaterialTheme.typography.labelSmall)
                            },
                            leadingIcon = {
                                Icon(Icons.Filled.Filter, null, Modifier.size(14.dp))
                            }
                        )
                    }
                }

                SectionTitle(strings.watermark)
                VcamCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FilterChip(
                            selected = watermarkEnabled,
                            onClick = { watermarkEnabled = !watermarkEnabled },
                            label = { Text(strings.watermark) },
                            leadingIcon = {
                                Icon(Icons.Filled.Opacity, null, Modifier.size(14.dp))
                            }
                        )
                    }
                    OutlinedTextField(
                        value = watermarkText,
                        onValueChange = { watermarkText = it },
                        label = { Text(strings.watermarkText) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                SectionTitle(strings.exportQuality)
                VcamCard {
                    LabeledSlider(
                        label = strings.jpegQuality,
                        value = quality.toFloat(),
                        valueRange = 60f..100f,
                        steps = 7,
                        onValueChange = { quality = it.toInt() },
                        valueText = "$quality%"
                    )
                    InfoRow(
                        label = strings.fullResolution,
                        value = fullSource?.let { "${it.width}x${it.height}" } ?: "-"
                    )
                    InfoRow(label = strings.original, value = sourceUri?.lastPathSegment ?: "-")
                }

                Spacer(modifier = Modifier.height(10.dp))
                VcamPrimaryButton(
                    text = strings.export,
                    icon = Icons.Filled.SaveAlt,
                    onClick = { export() }
                )

                if (status != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = status ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        ProcessingOverlay(visible = busy, label = strings.processing)
    }

    if (showCropDialog) {
        CropDialog(
            onDismiss = { showCropDialog = false },
            onConfirm = { left, top, right, bottom ->
                history.add(PhotoOp.Crop(left, top, right, bottom))
                showCropDialog = false
            },
            title = strings.crop,
            confirmLabel = strings.apply,
            cancelLabel = strings.cancel
        )
    }

    if (showResizeDialog) {
        ResizeDialog(
            initialWidth = fullSource?.width ?: 1080,
            initialHeight = fullSource?.height ?: 1080,
            title = strings.resize,
            widthLabel = strings.width,
            heightLabel = strings.height,
            confirmLabel = strings.apply,
            cancelLabel = strings.cancel,
            onDismiss = { showResizeDialog = false },
            onConfirm = { width, height ->
                history.add(PhotoOp.Resize(width, height))
                showResizeDialog = false
            }
        )
    }

    if (showMetadataDialog) {
        AlertDialog(
            onDismissRequest = { showMetadataDialog = false },
            title = { Text(strings.metadata) },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (metadata.isEmpty()) {
                        Text(strings.none, style = MaterialTheme.typography.bodySmall)
                    } else {
                        metadata.forEach { (label, value) -> InfoRow(label = label, value = value) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMetadataDialog = false }) { Text(strings.close) }
            }
        )
    }
}

@Composable
private fun CropDialog(
    onDismiss: () -> Unit,
    onConfirm: (Float, Float, Float, Float) -> Unit,
    title: String,
    confirmLabel: String,
    cancelLabel: String
) {
    var left by remember { mutableFloatStateOf(0.05f) }
    var top by remember { mutableFloatStateOf(0.05f) }
    var right by remember { mutableFloatStateOf(0.95f) }
    var bottom by remember { mutableFloatStateOf(0.95f) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                LabeledSlider(
                    label = "Left",
                    value = left,
                    valueRange = 0f..0.9f,
                    onValueChange = { left = it.coerceAtMost(right - 0.05f) }
                )
                LabeledSlider(
                    label = "Top",
                    value = top,
                    valueRange = 0f..0.9f,
                    onValueChange = { top = it.coerceAtMost(bottom - 0.05f) }
                )
                LabeledSlider(
                    label = "Right",
                    value = right,
                    valueRange = 0.1f..1f,
                    onValueChange = { right = it.coerceAtLeast(left + 0.05f) }
                )
                LabeledSlider(
                    label = "Bottom",
                    value = bottom,
                    valueRange = 0.1f..1f,
                    onValueChange = { bottom = it.coerceAtLeast(top + 0.05f) }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(left, top, right, bottom) }) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(cancelLabel) } }
    )
}

@Composable
private fun ResizeDialog(
    initialWidth: Int,
    initialHeight: Int,
    title: String,
    widthLabel: String,
    heightLabel: String,
    confirmLabel: String,
    cancelLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit
) {
    var width by remember { mutableStateOf(initialWidth.toString()) }
    var height by remember { mutableStateOf(initialHeight.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = width,
                    onValueChange = { width = it.filter { ch -> ch.isDigit() } },
                    label = { Text(widthLabel) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = height,
                    onValueChange = { height = it.filter { ch -> ch.isDigit() } },
                    label = { Text(heightLabel) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val w = width.toIntOrNull() ?: initialWidth
                val h = height.toIntOrNull() ?: initialHeight
                if (w > 0 && h > 0) onConfirm(w, h)
            }) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(cancelLabel) } }
    )
}
