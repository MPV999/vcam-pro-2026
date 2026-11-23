package com.vcampro.ultimate

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
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
import com.vcampro.ultimate.core.CrashReporter
import com.vcampro.ultimate.core.di.AppGraph
import com.vcampro.ultimate.core.i18n.LocalStrings
import com.vcampro.ultimate.core.i18n.stringsFor
import com.vcampro.ultimate.core.ui.theme.VcamTheme
import com.vcampro.ultimate.data.settings.VcamSettings
import com.vcampro.ultimate.ui.VcamRoot
import java.io.File

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val previousCrash = CrashReporter.file(this)
        if (previousCrash.exists()) {
            showCrashReport(previousCrash)
            return
        }

        enableEdgeToEdge()
        try {
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
        } catch (t: Throwable) {
            CrashReporter.record(this, t)
            showCrashReport(CrashReporter.file(this))
        }
    }

    /**
     * Plain View based screen: no Compose, no theme dependency, so it always
     * renders even when the failure happened inside the UI stack.
     */
    private fun showCrashReport(file: File) {
        val report = runCatching { file.readText() }
            .getOrElse { "Không đọc được tệp báo cáo lỗi." }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 72, 48, 48)
            setBackgroundColor(0xFF0B0F16.toInt())
        }

        val title = TextView(this).apply {
            text = "VCAM PRO · lỗi khi khởi động"
            setTextColor(0xFFFFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setPadding(0, 0, 0, 12)
        }

        val hint = TextView(this).apply {
            text = "Báo cáo dưới đây là lỗi thật của lần mở trước. Hãy sao chép và gửi lại để được sửa."
            setTextColor(0xFF9AA6B8.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(0, 0, 0, 16)
        }

        val body = TextView(this).apply {
            text = report
            setTextColor(0xFFC7D2E0.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
        }

        val scroll = ScrollView(this).apply { addView(body) }

        val copyButton = Button(this).apply {
            text = "Sao chép báo cáo"
            setOnClickListener {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("VCAM PRO crash", report))
                Toast.makeText(this@MainActivity, "Đã sao chép báo cáo", Toast.LENGTH_SHORT).show()
            }
        }

        val restartButton = Button(this).apply {
            text = "Xóa báo cáo và mở lại"
            setOnClickListener {
                CrashReporter.clear(this@MainActivity)
                recreate()
            }
        }

        root.addView(title)
        root.addView(hint)
        root.addView(scroll, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        root.addView(copyButton, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        root.addView(restartButton, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        setContentView(root)
    }
}
