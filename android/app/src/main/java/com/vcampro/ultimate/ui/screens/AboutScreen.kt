package com.vcampro.ultimate.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.vcampro.sdk.VcamSdk
import com.vcampro.ultimate.BuildConfig
import com.vcampro.ultimate.core.AppInfo
import com.vcampro.ultimate.core.i18n.LocalStrings
import com.vcampro.ultimate.core.ui.components.InfoRow
import com.vcampro.ultimate.core.ui.components.SectionTitle
import com.vcampro.ultimate.core.ui.components.StatusPill
import com.vcampro.ultimate.core.ui.components.VcamCard
import com.vcampro.ultimate.core.ui.components.VcamScreen
import com.vcampro.ultimate.core.ui.theme.VcamPalette
import com.vcampro.ultimate.core.ui.util.openUrl

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val strings = LocalStrings.current
    val context = LocalContext.current

    VcamScreen(
        title = strings.aboutTitle,
        subtitle = strings.appFullName,
        onBack = onBack
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusPill(text = strings.offlineReady, color = VcamPalette.Mint)
                StatusPill(text = strings.madeOnDevice, color = VcamPalette.AccentBright)
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(text = strings.aboutDesc, style = MaterialTheme.typography.bodyLarge)

            SectionTitle(strings.version)
            VcamCard {
                InfoRow(label = strings.version, value = AppInfo.versionName)
                InfoRow(label = "Version code", value = AppInfo.versionCode.toString())
                InfoRow(label = strings.sdkVersion, value = VcamSdk.VERSION)
                InfoRow(label = strings.buildType, value = BuildConfig.BUILD_TYPE)
                InfoRow(label = strings.packageName, value = BuildConfig.APPLICATION_ID)
            }

            SectionTitle(strings.privacyPolicy)
            VcamCard {
                Text(
                    text = strings.aiNote,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(
                        onClick = { context.openUrl(AppInfo.WEBSITE_URL) },
                        label = { Text(strings.downloadSite) }
                    )
                    AssistChip(
                        onClick = { context.openUrl(AppInfo.PRIVACY_URL) },
                        label = { Text(strings.privacyPolicy) }
                    )
                }
            }

            SectionTitle(strings.openSourceLicenses)
            VcamCard {
                InfoRow(label = "Jetpack Compose", value = "Apache 2.0")
                InfoRow(label = "AndroidX CameraX", value = "Apache 2.0")
                InfoRow(label = "AndroidX Media3", value = "Apache 2.0")
                InfoRow(label = "Room", value = "Apache 2.0")
                InfoRow(label = "DataStore", value = "Apache 2.0")
                InfoRow(label = "Coil", value = "Apache 2.0")
                InfoRow(label = "ML Kit Face Detection", value = "Google APIs Terms")
            }

            SectionTitle(strings.disclaimer)
            VcamCard {
                Text(
                    text = strings.disclaimerText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "${strings.contact}: ${AppInfo.GITHUB_OWNER} · ${AppInfo.GITHUB_REPO}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
