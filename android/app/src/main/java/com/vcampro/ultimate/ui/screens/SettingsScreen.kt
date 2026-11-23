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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vcampro.ultimate.core.AppInfo
import com.vcampro.ultimate.core.di.AppGraph
import com.vcampro.ultimate.core.i18n.LocalStrings
import com.vcampro.ultimate.core.ui.components.InfoRow
import com.vcampro.ultimate.core.ui.components.LabeledSlider
import com.vcampro.ultimate.core.ui.components.SectionTitle
import com.vcampro.ultimate.core.ui.components.ToggleRow
import com.vcampro.ultimate.core.ui.components.VcamCard
import com.vcampro.ultimate.core.ui.components.VcamPrimaryButton
import com.vcampro.ultimate.core.ui.components.VcamScreen
import com.vcampro.ultimate.core.ui.util.openUrl
import com.vcampro.ultimate.data.settings.VcamSettings
import com.vcampro.ultimate.domain.model.PerformanceMode
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(onBack: () -> Unit, onOpenAbout: () -> Unit) {
    val strings = LocalStrings.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by AppGraph.settings.settings
        .collectAsStateWithLifecycle(initialValue = VcamSettings())

    var updateStatus by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }

    VcamScreen(
        title = strings.settings,
        subtitle = "${strings.version} ${AppInfo.versionName}",
        onBack = onBack
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            SectionTitle(strings.language)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !settings.isEnglish,
                    onClick = { scope.launch { AppGraph.settings.setLanguage("vi") } },
                    label = { Text(strings.languageVi) }
                )
                FilterChip(
                    selected = settings.isEnglish,
                    onClick = { scope.launch { AppGraph.settings.setLanguage("en") } },
                    label = { Text(strings.languageEn) }
                )
            }

            SectionTitle(strings.theme)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "dark" to strings.themeDark,
                    "light" to strings.themeLight,
                    "system" to strings.themeSystem
                ).forEach { (value, label) ->
                    FilterChip(
                        selected = settings.themeMode == value,
                        onClick = { scope.launch { AppGraph.settings.setThemeMode(value) } },
                        label = { Text(label) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            VcamCard {
                ToggleRow(
                    title = strings.dynamicColor,
                    checked = settings.dynamicColor,
                    onCheckedChange = { scope.launch { AppGraph.settings.setDynamicColor(it) } }
                )
                ToggleRow(
                    title = strings.grid,
                    checked = settings.gridEnabled,
                    onCheckedChange = { scope.launch { AppGraph.settings.setGridEnabled(it) } }
                )
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

            SectionTitle(strings.defaults)
            VcamCard {
                ToggleRow(
                    title = strings.watermarkEnabled,
                    checked = settings.watermarkEnabled,
                    onCheckedChange = { scope.launch { AppGraph.settings.setWatermarkEnabled(it) } }
                )
                OutlinedTextField(
                    value = settings.watermarkText,
                    onValueChange = { scope.launch { AppGraph.settings.setWatermarkText(it) } },
                    label = { Text(strings.watermarkText) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                LabeledSlider(
                    label = strings.jpegQuality,
                    value = settings.jpegQuality.toFloat(),
                    valueRange = 60f..100f,
                    steps = 7,
                    onValueChange = { scope.launch { AppGraph.settings.setJpegQuality(it.toInt()) } },
                    valueText = "${settings.jpegQuality}%"
                )
                LabeledSlider(
                    label = strings.defaultFps,
                    value = settings.defaultFps.toFloat(),
                    valueRange = 15f..60f,
                    steps = 8,
                    onValueChange = { scope.launch { AppGraph.settings.setDefaultFps(it.toInt()) } },
                    valueText = "${settings.defaultFps}"
                )
            }

            SectionTitle(strings.checkUpdate)
            VcamCard {
                VcamPrimaryButton(
                    text = if (checking) strings.checkingUpdate else strings.checkUpdate,
                    enabled = !checking,
                    onClick = {
                        checking = true
                        updateStatus = null
                        scope.launch {
                            val result = AppGraph.updater.check()
                            checking = false
                            updateStatus = result.fold(
                                onSuccess = { info ->
                                    if (info.isNewer) {
                                        "${strings.updateAvailable}: ${info.latestVersion}"
                                    } else {
                                        strings.upToDate
                                    }
                                },
                                onFailure = { "${strings.failed}: ${it.message}" }
                            )
                        }
                    }
                )
                if (updateStatus != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = updateStatus ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(
                        onClick = { context.openUrl(AppInfo.WEBSITE_URL) },
                        label = { Text(strings.downloadSite) }
                    )
                    AssistChip(
                        onClick = { context.openUrl(AppInfo.RELEASES_URL) },
                        label = { Text(strings.openDownloadPage) }
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                InfoRow(label = strings.version, value = AppInfo.versionName)
                InfoRow(label = strings.packageName, value = AppInfo.GITHUB_REPO)
            }

            SectionTitle(strings.about)
            VcamCard {
                VcamPrimaryButton(text = strings.aboutTitle, onClick = onOpenAbout)
                Spacer(modifier = Modifier.height(8.dp))
                VcamPrimaryButton(
                    text = strings.resetSettings,
                    onClick = { scope.launch { AppGraph.settings.resetAll() } }
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
