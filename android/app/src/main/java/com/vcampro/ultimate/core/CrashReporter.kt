package com.vcampro.ultimate.core

import android.content.Context
import android.os.Build
import android.util.Log
import com.vcampro.ultimate.BuildConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Writes the last fatal error to a small file and shows it on the next launch.
 *
 * A phone is not a development machine: without this, a startup crash is just a
 * closed app. With it the user can read and copy the real stack trace.
 */
object CrashReporter {

    private const val FILE_NAME = "last_crash.txt"
    private const val TAG = "VcamCrash"

    fun file(context: Context): File = File(context.filesDir, FILE_NAME)

    fun hasReport(context: Context): Boolean = file(context).exists()

    fun clear(context: Context) {
        runCatching { file(context).delete() }
    }

    fun installHandler(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { Log.e(TAG, "Uncaught exception on ${thread.name}", throwable) }
            record(app, throwable)
            previous?.uncaughtException(thread, throwable)
        }
    }

    fun record(context: Context, throwable: Throwable) {
        runCatching {
            val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            val text = buildString {
                appendLine("VCAM PRO — báo cáo lỗi")
                appendLine("Thời gian: $time")
                appendLine("Thiết bị: ${Build.MANUFACTURER} ${Build.MODEL}")
                appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                appendLine("ABI: ${Build.SUPPORTED_ABIS.joinToString()}")
                appendLine("Phiên bản: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                appendLine("Loại build: ${BuildConfig.BUILD_TYPE}")
                appendLine()
                appendLine(Log.getStackTraceString(throwable))
                var cause = throwable.cause
                var depth = 0
                while (cause != null && depth < 5) {
                    appendLine()
                    appendLine("Cause ${depth + 1}:")
                    appendLine(Log.getStackTraceString(cause))
                    cause = cause.cause
                    depth += 1
                }
            }
            file(context).writeText(text)
        }
    }
}
