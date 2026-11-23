package com.vcampro.ultimate.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.vcampro.ultimate.core.di.AppGraph
import com.vcampro.ultimate.core.i18n.LocalStrings
import com.vcampro.ultimate.core.ui.components.EmptyState
import com.vcampro.ultimate.core.ui.components.InfoRow
import com.vcampro.ultimate.core.ui.components.ProcessingOverlay
import com.vcampro.ultimate.core.ui.components.SectionTitle
import com.vcampro.ultimate.core.ui.components.StatTile
import com.vcampro.ultimate.core.ui.components.StatusPill
import com.vcampro.ultimate.core.ui.components.VcamCard
import com.vcampro.ultimate.core.ui.components.VcamPrimaryButton
import com.vcampro.ultimate.core.ui.components.VcamScreen
import com.vcampro.ultimate.core.ui.theme.VcamPalette
import com.vcampro.ultimate.data.media.VisionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AiVisionScreen(onBack: () -> Unit) {
    val strings = LocalStrings.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val analyzer = remember { AppGraph.vision }
    val editor = remember { AppGraph.photoEditor }

    var sourceUri by remember { mutableStateOf<Uri?>(null) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var result by remember { mutableStateOf<VisionResult?>(null) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) sourceUri = uri }

    LaunchedEffect(sourceUri) {
        val uri = sourceUri ?: return@LaunchedEffect
        busy = true
        bitmap = editor.loadBitmap(context, uri, 1024)
        result = null
        busy = false
        if (bitmap == null) status = strings.failed
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        VcamScreen(
            title = strings.aiVisionTitle,
            subtitle = strings.aiVision,
            onBack = onBack
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                if (bitmap == null) {
                    VcamCard {
                        EmptyState(
                            icon = Icons.Filled.Image,
                            title = strings.pickImageFirst,
                            message = strings.aiNote
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
                    Spacer(modifier = Modifier.height(24.dp))
                    return@Column
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Image(
                        bitmap = bitmap!!.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 340.dp)
                    )
                    val analysis = result
                    if (analysis != null) {
                        Canvas(modifier = Modifier.matchParentSize()) {
                            val scaleX = size.width / analysis.width.toFloat()
                            val scaleY = size.height / analysis.height.toFloat()
                            analysis.faces.forEach { face ->
                                drawRect(
                                    color = VcamPalette.Mint,
                                    topLeft = Offset(face.left * scaleX, face.top * scaleY),
                                    size = Size(
                                        (face.right - face.left) * scaleX,
                                        (face.bottom - face.top) * scaleY
                                    ),
                                    style = Stroke(width = 3f)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VcamPrimaryButton(
                        text = strings.analyze,
                        icon = Icons.Filled.Search,
                        onClick = {
                            val current = bitmap ?: return@VcamPrimaryButton
                            busy = true
                            scope.launch {
                                val outcome = runCatching {
                                    withContext(Dispatchers.Default) { analyzer.analyze(current) }
                                }
                                busy = false
                                outcome.fold(
                                    onSuccess = {
                                        result = it
                                        status = strings.analysisComplete
                                    },
                                    onFailure = { status = "${strings.failed}: ${it.message}" }
                                )
                            }
                        }
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                VcamPrimaryButton(
                    text = strings.selectPhoto,
                    icon = Icons.Filled.Image,
                    onClick = {
                        picker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                )

                val analysis = result
                if (analysis != null) {
                    SectionTitle(strings.aiVisionTitle)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatTile(
                            label = strings.facesDetected,
                            value = analysis.faceCount.toString(),
                            modifier = Modifier.weight(1f)
                        )
                        StatTile(
                            label = strings.qualityScore,
                            value = "%.0f".format(analysis.qualityScore),
                            modifier = Modifier.weight(1f),
                            accent = VcamPalette.Mint
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    VcamCard {
                        Text(
                            text = strings.blurScore,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        LinearProgressIndicator(
                            progress = (analysis.sharpnessScore / 100f).coerceIn(0f, 1f),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = strings.lightingScore,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        LinearProgressIndicator(
                            progress = (analysis.lightingScore / 100f).coerceIn(0f, 1f),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        InfoRow(
                            label = strings.blurScore,
                            value = "%.1f / 100".format(analysis.sharpnessScore)
                        )
                        InfoRow(
                            label = strings.lightingScore,
                            value = "%.1f / 100".format(analysis.lightingScore)
                        )
                        InfoRow(
                            label = strings.landmarks,
                            value = analysis.faces.sumOf { it.landmarkCount }.toString()
                        )
                        InfoRow(
                            label = strings.resolution,
                            value = "${analysis.width}x${analysis.height}"
                        )
                    }

                    if (analysis.faces.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        VcamCard {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(bottom = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Face,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "${strings.facesDetected}: ${analysis.faceCount}",
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                            analysis.faces.forEachIndexed { index, face ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    StatusPill(
                                        text = "#${index + 1}",
                                        color = VcamPalette.Accent
                                    )
                                    if (face.smiling) {
                                        StatusPill(text = strings.smiling, color = VcamPalette.Mint)
                                    }
                                    if (face.leftEyeOpen && face.rightEyeOpen) {
                                        StatusPill(
                                            text = strings.eyesOpen,
                                            color = VcamPalette.AccentBright
                                        )
                                    }
                                    StatusPill(
                                        text = "${face.landmarkCount} lm",
                                        color = VcamPalette.Amber
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                            }
                        }
                    }
                }

                if (status != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = status ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                VcamCard {
                    Text(
                        text = strings.aiNote,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        ProcessingOverlay(visible = busy, label = strings.processing)
    }
}
