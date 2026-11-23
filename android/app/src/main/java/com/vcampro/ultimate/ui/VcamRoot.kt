package com.vcampro.ultimate.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.vcampro.ultimate.core.di.AppGraph
import com.vcampro.ultimate.core.i18n.LocalStrings
import com.vcampro.ultimate.core.ui.components.VcamPrimaryButton
import com.vcampro.ultimate.core.ui.theme.VcamPalette
import com.vcampro.ultimate.data.settings.VcamSettings
import com.vcampro.ultimate.ui.screens.AboutScreen
import com.vcampro.ultimate.ui.screens.AiVisionScreen
import com.vcampro.ultimate.ui.screens.CameraStudioScreen
import com.vcampro.ultimate.ui.screens.DashboardScreen
import com.vcampro.ultimate.ui.screens.DeviceCenterScreen
import com.vcampro.ultimate.ui.screens.MediaLibraryScreen
import com.vcampro.ultimate.ui.screens.OnboardingScreen
import com.vcampro.ultimate.ui.screens.PhotoStudioScreen
import com.vcampro.ultimate.ui.screens.SecurityCenterScreen
import com.vcampro.ultimate.ui.screens.SettingsScreen
import com.vcampro.ultimate.ui.screens.VideoStudioScreen
import com.vcampro.ultimate.ui.screens.VirtualCameraStudioScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object Routes {
    const val ONBOARDING = "onboarding"
    const val DASHBOARD = "dashboard"
    const val CAMERA = "camera"
    const val PHOTO = "photo"
    const val VIDEO = "video"
    const val VIRTUAL_CAMERA = "virtual_camera"
    const val MEDIA = "media"
    const val AI = "ai"
    const val DEVICE = "device"
    const val SECURITY = "security"
    const val SETTINGS = "settings"
    const val ABOUT = "about"
}

@Composable
fun VcamRoot(settings: VcamSettings) {
    var showSplash by remember { mutableStateOf(true) }
    var unlocked by rememberSaveable { mutableStateOf(false) }
    val navController = rememberNavController()

    LaunchedEffect(Unit) {
        delay(1100)
        showSplash = false
    }

    if (showSplash) {
        SplashScreen()
        return
    }

    val requiresUnlock = settings.appLockEnabled && settings.appLockPinHash.isNotBlank()
    if (requiresUnlock && !unlocked) {
        LockScreen(
            storedHash = settings.appLockPinHash,
            onUnlocked = { unlocked = true }
        )
        return
    }

    NavHost(
        navController = navController,
        startDestination = if (settings.onboardingCompleted) Routes.DASHBOARD else Routes.ONBOARDING
    ) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                onFinished = {
                    navController.navigate(Routes.DASHBOARD) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.DASHBOARD) {
            DashboardScreen(
                onOpen = { route -> navController.navigate(route) }
            )
        }
        composable(Routes.CAMERA) { CameraStudioScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.PHOTO) { PhotoStudioScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.VIDEO) { VideoStudioScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.VIRTUAL_CAMERA) {
            VirtualCameraStudioScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.MEDIA) { MediaLibraryScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.AI) { AiVisionScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.DEVICE) { DeviceCenterScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.SECURITY) { SecurityCenterScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenAbout = { navController.navigate(Routes.ABOUT) }
            )
        }
        composable(Routes.ABOUT) { AboutScreen(onBack = { navController.popBackStack() }) }
    }
}

@Composable
private fun SplashScreen() {
    val strings = LocalStrings.current
    var visible by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0.2f,
        animationSpec = tween(durationMillis = 700),
        label = "splashAlpha"
    )
    LaunchedEffect(Unit) { visible = true }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        VcamPalette.SurfaceDark,
                        VcamPalette.SurfaceDarkElevated,
                        VcamPalette.SurfaceDark
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .alpha(alpha)
                .padding(24.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .background(VcamPalette.Accent.copy(alpha = 0.16f), RoundedCornerShape(28.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = null,
                    tint = VcamPalette.Accent,
                    modifier = Modifier.size(44.dp)
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = strings.appName,
                style = MaterialTheme.typography.displaySmall,
                color = VcamPalette.TextPrimaryDark,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = strings.appFullName,
                style = MaterialTheme.typography.labelMedium,
                color = VcamPalette.AccentBright,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = strings.tagline,
                style = MaterialTheme.typography.bodyMedium,
                color = VcamPalette.TextSecondaryDark,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(28.dp))
            CircularProgressIndicator(
                color = VcamPalette.Accent,
                strokeWidth = 3.dp,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
private fun LockScreen(storedHash: String, onUnlocked: () -> Unit) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(28.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(52.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = strings.enterPin, style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = pin,
                onValueChange = { value ->
                    pin = value.filter { it.isDigit() }.take(8)
                    error = null
                },
                label = { Text(strings.setPin) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth()
            )
            AnimatedVisibility(visible = error != null) {
                Text(
                    text = error ?: "",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            Spacer(modifier = Modifier.height(18.dp))
            VcamPrimaryButton(
                text = if (checking) strings.loading else strings.unlock,
                enabled = pin.length >= 4 && !checking,
                onClick = {
                    checking = true
                    scope.launch {
                        val ok = AppGraph.security.verifyPin(pin, storedHash)
                        checking = false
                        if (ok) {
                            onUnlocked()
                        } else {
                            error = strings.wrongPin
                            pin = ""
                        }
                    }
                }
            )
        }
    }
}
