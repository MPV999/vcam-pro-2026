package com.vcampro.ultimate.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vcampro.ultimate.core.AppInfo
import com.vcampro.ultimate.core.di.AppGraph
import com.vcampro.ultimate.core.i18n.LocalStrings
import com.vcampro.ultimate.core.ui.components.FeatureTile
import com.vcampro.ultimate.core.ui.components.SectionTitle
import com.vcampro.ultimate.core.ui.components.StatTile
import com.vcampro.ultimate.core.ui.components.StatusPill
import com.vcampro.ultimate.core.ui.components.VcamCard
import com.vcampro.ultimate.core.ui.theme.VcamPalette
import com.vcampro.ultimate.data.settings.VcamSettings
import com.vcampro.ultimate.domain.model.DeviceProfile
import com.vcampro.ultimate.domain.model.PerformanceMode
import com.vcampro.ultimate.ui.Routes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DashboardScreen(onOpen: (String) -> Unit) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    val deviceProfile by produceState<DeviceProfile?>(initialValue = null) {
        value = runCatching {
            withContext(Dispatchers.IO) { AppGraph.device.analyze() }
        }.getOrNull()
    }
    val settings by AppGraph.settings.settings
        .collectAsStateWithLifecycle(initialValue = VcamSettings())
    var selectedMode by remember(settings.performanceMode) {
        mutableStateOf(PerformanceMode.fromKey(settings.performanceMode))
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = strings.appName, style = MaterialTheme.typography.headlineMedium)
                    Text(
                        text = strings.appFullName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = strings.dashboardSubtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { onOpen(Routes.SETTINGS) }) {
                    Icon(Icons.Filled.Settings, contentDescription = strings.settings)
                }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatusPill(text = strings.offlineReady, color = VcamPalette.Mint)
                StatusPill(text = strings.noAccount, color = VcamPalette.AccentBright)
                StatusPill(text = strings.privacyFirst, color = VcamPalette.Amber)
            }
        }

        item {
            VcamCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .background(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                RoundedCornerShape(14.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.WifiOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.size(12.dp))
                    Column {
                        Text(text = strings.systemStatus, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = deviceProfile?.let { "${it.model} · ${it.tierLabel} · ${it.ramLabel} RAM" }
                                ?: strings.analyzing,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile(
                        label = strings.androidVersion,
                        value = deviceProfile?.androidRelease ?: "-",
                        modifier = Modifier.weight(1f)
                    )
                    StatTile(
                        label = strings.recommendedConfig,
                        value = deviceProfile?.recommendedResolutionLabel ?: "-",
                        modifier = Modifier.weight(1f),
                        accent = VcamPalette.Mint
                    )
                }
            }
        }

        item { SectionTitle(strings.performanceMode) }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PerformanceMode.entries.forEach { mode ->
                    FilterChip(
                        selected = selectedMode == mode,
                        onClick = {
                            selectedMode = mode
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
        }
        item {
            Text(
                text = strings.performanceModeDesc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item { SectionTitle(strings.quickActions) }

        val actions: List<Triple<ImageVector, String, String>> = listOf(
            Triple(Icons.Filled.CameraAlt, strings.cameraStudio, strings.capturePhoto),
            Triple(Icons.Filled.Movie, strings.photoStudio, strings.editPhoto),
            Triple(Icons.Filled.Videocam, strings.videoStudio, strings.exportVideo),
            Triple(Icons.Filled.Devices, strings.virtualCamera, strings.framePreview),
            Triple(Icons.Filled.PhotoLibrary, strings.mediaLibrary, strings.albums),
            Triple(Icons.Filled.Face, strings.aiVision, strings.analyze),
            Triple(Icons.Filled.Devices, strings.deviceCenter, strings.deviceInfo),
            Triple(Icons.Filled.Security, strings.securityCenter, strings.appLock)
        )
        val routes = listOf(
            Routes.CAMERA,
            Routes.PHOTO,
            Routes.VIDEO,
            Routes.VIRTUAL_CAMERA,
            Routes.MEDIA,
            Routes.AI,
            Routes.DEVICE,
            Routes.SECURITY
        )
        items(actions.size) { index ->
            val (icon, title, subtitle) = actions[index]
            FeatureTile(
                icon = icon,
                title = title,
                subtitle = subtitle,
                accent = if (index % 2 == 0) MaterialTheme.colorScheme.primary else VcamPalette.Mint,
                onClick = { onOpen(routes[index]) }
            )
        }

        item {
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatusPill(text = "${strings.sdkVersion} 1.0.0", color = VcamPalette.Accent)
                StatusPill(
                    text = "${strings.version} ${AppInfo.versionName}",
                    color = VcamPalette.Mint
                )
            }
        }
    }
}
