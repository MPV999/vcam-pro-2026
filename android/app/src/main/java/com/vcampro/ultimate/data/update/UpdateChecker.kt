package com.vcampro.ultimate.data.update

import android.content.Context
import com.vcampro.ultimate.core.AppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Reads the latest published release from GitHub.
 *
 * This is the only network call the app ever makes, it happens only when the
 * user taps "Check for updates", and it downloads no media and no personal data.
 * Downloading and installing an update is always the user's explicit action.
 */
class UpdateChecker(private val context: Context) {

    data class UpdateInfo(
        val latestVersion: String,
        val releaseUrl: String,
        val notes: String,
        val apkUrl: String?,
        val apkSizeBytes: Long,
        val isNewer: Boolean
    )

    suspend fun check(currentVersion: String = AppInfo.versionName): Result<UpdateInfo> =
        withContext(Dispatchers.IO) {
            runCatching {
                val connection = (URL(AppInfo.LATEST_RELEASE_API).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 10_000
                    readTimeout = 15_000
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("User-Agent", "VCAM-PRO-Android")
                }
                try {
                    val code = connection.responseCode
                    if (code != HttpURLConnection.HTTP_OK) {
                        throw IllegalStateException("GitHub trả về mã $code")
                    }
                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(body)
                    val tag = json.optString("tag_name").removePrefix("v").ifBlank {
                        json.optString("name").removePrefix("v")
                    }
                    val assets = json.optJSONArray("assets")
                    var apkUrl: String? = null
                    var apkSize = 0L
                    if (assets != null) {
                        for (index in 0 until assets.length()) {
                            val asset = assets.getJSONObject(index)
                            val name = asset.optString("name")
                            if (name.endsWith(".apk", ignoreCase = true)) {
                                apkUrl = asset.optString("browser_download_url")
                                apkSize = asset.optLong("size")
                                break
                            }
                        }
                    }
                    UpdateInfo(
                        latestVersion = tag,
                        releaseUrl = json.optString("html_url", AppInfo.RELEASES_URL),
                        notes = json.optString("body", ""),
                        apkUrl = apkUrl,
                        apkSizeBytes = apkSize,
                        isNewer = isNewerVersion(tag, currentVersion)
                    )
                } finally {
                    connection.disconnect()
                }
            }
        }

    companion object {
        /** Semantic-ish comparison of dotted version strings. */
        fun isNewerVersion(candidate: String, current: String): Boolean {
            val left = candidate.split(".", "-").mapNotNull { it.toIntOrNull() }
            val right = current.split(".", "-").mapNotNull { it.toIntOrNull() }
            val size = maxOf(left.size, right.size)
            for (index in 0 until size) {
                val leftPart = left.getOrElse(index) { 0 }
                val rightPart = right.getOrElse(index) { 0 }
                if (leftPart > rightPart) return true
                if (leftPart < rightPart) return false
            }
            return false
        }
    }
}
