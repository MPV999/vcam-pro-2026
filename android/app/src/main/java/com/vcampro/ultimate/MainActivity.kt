package com.vcampro.ultimate

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vcampro.ultimate.core.di.AppGraph
import com.vcampro.ultimate.core.i18n.LocalStrings
import com.vcampro.ultimate.core.i18n.stringsFor
import com.vcampro.ultimate.core.ui.theme.VcamTheme
import com.vcampro.ultimate.data.settings.VcamSettings
import com.vcampro.ultimate.ui.VcamRoot

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by AppGraph.settings.settings
                .collectAsStateWithLifecycle(initialValue = VcamSettings())
            val darkTheme = when (settings.themeMode) {
                "light" -> false
                "dark" -> true
                else -> isSystemInDarkTheme()
            }
            val strings = remember(settings.language) { stringsFor(settings.language) }
            VcamTheme(darkTheme = darkTheme, dynamicColor = settings.dynamicColor) {
                CompositionLocalProvider(LocalStrings provides strings) {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        VcamRoot(settings = settings)
                    }
                }
            }
        }
    }
}
