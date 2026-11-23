package com.vcampro.ultimate.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vcampro.ultimate.core.di.AppGraph
import com.vcampro.ultimate.core.i18n.LocalStrings
import com.vcampro.ultimate.core.ui.components.InfoRow
import com.vcampro.ultimate.core.ui.components.SectionTitle
import com.vcampro.ultimate.core.ui.components.StatTile
import com.vcampro.ultimate.core.ui.components.StatusPill
import com.vcampro.ultimate.core.ui.components.VcamCard
import com.vcampro.ultimate.core.ui.components.VcamScreen
import com.vcampro.ultimate.core.ui.theme.VcamPalette
import com.vcampro.ultimate.data.settings.VcamSettings
import com.vcampro.ultimate.domain.model.DeviceProfile
import com.vcampro.ultimate.domain.model.PerformanceMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DeviceCenterScreen(onBack: () -> Unit) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    val profile by produceState<DeviceProfile?>(initialValue = null) {
        value = withContext(Dispatchers.IO) { AppGraph.device.analyze() }
    }
    val settings by AppGraph.settings.settings
        .collectAsStateWithLifecycle(initialValue = VcamSettings())

    VcamScreen(
        title = strings.deviceCenter,
        subtitle = profile?.let { "${it.manufacturer} ${it.model}" } ?: strings.analyzing,
        onBack = onBack
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            val current = profile
            if (current == null) {
                Text(text = strings.analyzing, style = MaterialTheme.typography.bodyMedium)
                return@Column
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(
                    label = strings.ram,
                    value = current.ramLabel,
                    modifier = Modifier.weight(1f)
                )
                StatTile(
                    label = strings.cpuCores,
                    value = "${current.cpuCores}",
                    modifier = Modifier.weight(1f),
                    accent = VcamPalette.Mint
                )
                StatTile(
                    label = strings.hardwareAcceleration,
                    value = if (current.hardwareAcceleration) strings.on else strings.off,
                    modifier = Modifier.weight(1f),
                    accent = VcamPalette.Amber
                )
            }

            SectionTitle(strings.deviceInfo)
            VcamCard {
                InfoRow(label = strings.deviceModel, value = "${current.manufacturer} ${current.model}")
                InfoRow(
                    label = strings.androidVersion,
                    value = "${current.androidRelease} (API ${current.sdkInt})"
                )
                InfoRow(label = strings.cpuArch, value = current.cpuArchitectures.joinToString(", "))
                InfoRow(label = strings.cpuCores, value = "${current.cpuCores} ${strings.cores}")
                InfoRow(label = strings.ram, value = "${current.ramLabel} (${current.tierLabel})")
                InfoRow(label = strings.storage, value = current.storageLabel)
            }

            SectionTitle(strings.performanceMode)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PerformanceMode.entries.forEach { mode ->
                    FilterChip(
                        selected = PerformanceMode.fromKey(settings.performanceMode) == mode,
                        onClick = {
                            scope.launch { AppGraph.settings.setPerformanceMode(mode.key) }
                        },
                        label = {
                            Text(
                                text = when (mode) {
                                    PerformanceMode.PERFORMANCE -> strings.modePerformance
                                    PerformanceMode.BALANCED -> strings.modeBalanced
                                    PerformanceMode.QUALITY -> strings.modeQuality
                                    PerformanceMode.BATTERY -> strings.modeBattery
                                },
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            VcamCard {
                InfoRow(label = strings.recommendedConfig, value = current.recommendedResolutionLabel)
                val active = AppGraph.device.recommendationFor(
                    PerformanceMode.fromKey(settings.performanceMode)
                )
                InfoRow(
                    label = strings.performanceMode,
                    value = "${active.first}x${active.second} @ ${active.third}fps"
                )
                Text(
                    text = strings.performanceModeDesc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            SectionTitle(strings.cameraCapabilities)
            VcamCard {
                InfoRow(
                    label = strings.backCameraAvailable,
                    value = if (current.hasBackCamera) strings.yes else strings.no
                )
                InfoRow(
                    label = strings.frontCameraAvailable,
                    value = if (current.hasFrontCamera) strings.yes else strings.no
                )
                InfoRow(
                    label = strings.flashAvailable,
                    value = if (current.hasFlash) strings.yes else strings.no
                )
                InfoRow(
                    label = strings.hdr,
                    value = if (current.supportsHdr) strings.on else strings.notSupported
                )
                InfoRow(
                    label = strings.supportedFps,
                    value = current.supportedFpsRanges.take(6).joinToString(", ").ifBlank { "-" }
                )
            }

            SectionTitle(strings.supportedResolutions)
            VcamCard {
                val resolutions = current.supportedPhotoSizes.take(12)
                if (resolutions.isEmpty()) {
                    Text(strings.notSupported, style = MaterialTheme.typography.bodySmall)
                } else {
                    resolutions.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { size ->
                                StatusPill(text = size, color = VcamPalette.AccentBright)
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }
                if (current.supportedVideoSizes.isNotEmpty()) {
                    InfoRow(
                        label = strings.videos,
                        value = current.supportedVideoSizes.take(5).joinToString(", ")
                    )
                }
            }

            SectionTitle(strings.codecCapabilities)
            VcamCard {
                if (current.hardwareCodecs.isEmpty()) {
                    Text(strings.notSupported, style = MaterialTheme.typography.bodySmall)
                } else {
                    current.hardwareCodecs.take(10).forEach { codec ->
                        InfoRow(label = codec.substringBefore(" ("), value = codec.substringAfter("(").removeSuffix(")"))
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
