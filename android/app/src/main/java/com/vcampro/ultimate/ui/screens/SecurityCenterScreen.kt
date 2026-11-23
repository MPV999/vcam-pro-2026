package com.vcampro.ultimate.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vcampro.ultimate.core.di.AppGraph
import com.vcampro.ultimate.core.i18n.LocalStrings
import com.vcampro.ultimate.core.ui.components.InfoRow
import com.vcampro.ultimate.core.ui.components.SectionTitle
import com.vcampro.ultimate.core.ui.components.StatusPill
import com.vcampro.ultimate.core.ui.components.ToggleRow
import com.vcampro.ultimate.core.ui.components.VcamCard
import com.vcampro.ultimate.core.ui.components.VcamPrimaryButton
import com.vcampro.ultimate.core.ui.components.VcamScreen
import com.vcampro.ultimate.core.ui.theme.VcamPalette
import com.vcampro.ultimate.core.ui.util.openAppSettings
import com.vcampro.ultimate.core.ui.util.shareUri
import com.vcampro.ultimate.data.settings.VcamSettings
import kotlinx.coroutines.launch

@Composable
fun SecurityCenterScreen(onBack: () -> Unit) {
    val strings = LocalStrings.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val security = remember { AppGraph.security }
    val settings by AppGraph.settings.settings
        .collectAsStateWithLifecycle(initialValue = VcamSettings())

    var showPinDialog by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }

    val cameraGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED
    val micGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED
    val mediaPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    val mediaGranted = ContextCompat.checkSelfPermission(context, mediaPermission) ==
        PackageManager.PERMISSION_GRANTED

    VcamScreen(
        title = strings.securityCenter,
        subtitle = strings.privacyFirst,
        onBack = onBack
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            SectionTitle(strings.appLock)
            VcamCard {
                ToggleRow(
                    title = strings.appLock,
                    checked = settings.appLockEnabled,
                    description = strings.appLockDesc,
                    onCheckedChange = { enabled ->
                        if (enabled) {
                            showPinDialog = true
                        } else {
                            scope.launch {
                                security.disableLock()
                                status = strings.lockDisabled
                            }
                        }
                    }
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AssistChip(
                        onClick = { showPinDialog = true },
                        label = { Text(strings.setPin) }
                    )
                    AssistChip(
                        onClick = {
                            scope.launch {
                                security.disableLock()
                                status = strings.lockDisabled
                            }
                        },
                        label = { Text(strings.lockDisabled) }
                    )
                }
            }

            SectionTitle(strings.permissions)
            VcamCard {
                InfoRow(
                    label = Manifest.permission.CAMERA,
                    value = if (cameraGranted) strings.granted else strings.denied
                )
                InfoRow(
                    label = Manifest.permission.RECORD_AUDIO,
                    value = if (micGranted) strings.granted else strings.denied
                )
                InfoRow(
                    label = mediaPermission,
                    value = if (mediaGranted) strings.granted else strings.denied
                )
                AssistChip(
                    onClick = { context.openAppSettings() },
                    label = { Text(strings.openSystemSettings) }
                )
            }

            SectionTitle(strings.privacyDashboard)
            VcamCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatusPill(text = strings.noNetworkUpload, color = VcamPalette.Mint)
                    StatusPill(text = strings.dataStaysLocal, color = VcamPalette.AccentBright)
                    StatusPill(text = strings.noAccount, color = VcamPalette.Amber)
                }
                Spacer(modifier = Modifier.height(10.dp))
                InfoRow(label = strings.dataStaysLocal, value = strings.yes)
                InfoRow(label = "Backup", value = strings.off)
                InfoRow(label = strings.noAccount, value = strings.yes)
            }

            SectionTitle(strings.securityCenter)
            VcamCard {
                VcamPrimaryButton(
                    text = strings.exportPersonalData,
                    onClick = {
                        scope.launch {
                            val summary = "media=${AppGraph.media.storageUsage().appMediaLabel}"
                            val file = security.exportPersonalData(summary)
                            val uri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                file
                            )
                            context.shareUri(uri, "application/json")
                            status = strings.exportDataDone
                        }
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
                VcamPrimaryButton(
                    text = strings.cleanTemp,
                    onClick = {
                        val freed = security.cleanTemporaryFiles()
                        status = "${strings.tempCleaned}: ${freed / 1024} KB"
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
                VcamPrimaryButton(
                    text = strings.deletePersonalData,
                    onClick = {
                        scope.launch {
                            security.deletePersonalData()
                            AppGraph.media.clearAppRecords()
                            status = strings.deleteDataDone
                        }
                    }
                )
            }

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

    if (showPinDialog) {
        var pin by remember { mutableStateOf("") }
        var confirmation by remember { mutableStateOf("") }
        var error by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { showPinDialog = false },
            title = { Text(strings.setPin) },
            text = {
                Column {
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { pin = it.filter { ch -> ch.isDigit() }.take(8) },
                        label = { Text(strings.setPin) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = confirmation,
                        onValueChange = { confirmation = it.filter { ch -> ch.isDigit() }.take(8) },
                        label = { Text(strings.confirmPin) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation()
                    )
                    if (error != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = error ?: "",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    when {
                        pin.length < 4 -> error = strings.pinTooShort
                        pin != confirmation -> error = strings.pinMismatch
                        else -> scope.launch {
                            val result = security.setPin(pin)
                            status = result.fold(
                                onSuccess = { strings.lockEnabled },
                                onFailure = { "${strings.failed}: ${it.message}" }
                            )
                            showPinDialog = false
                        }
                    }
                }) { Text(strings.save) }
            },
            dismissButton = {
                TextButton(onClick = { showPinDialog = false }) { Text(strings.cancel) }
            }
        )
    }
}

