package com.vcampro.ultimate.domain.model

import android.net.Uri

enum class MediaKind { PHOTO, VIDEO }

enum class MediaFilter { ALL, PHOTOS, VIDEOS, FAVORITES }

enum class MediaSort { NEWEST, OLDEST, LARGEST, NAME }

data class MediaItem(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val kind: MediaKind,
    val mimeType: String,
    val sizeBytes: Long,
    val dateAddedSeconds: Long,
    val durationMs: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
    val isFavorite: Boolean = false,
    val album: String? = null
) {
    val isVideo: Boolean get() = kind == MediaKind.VIDEO

    val sizeLabel: String
        get() = when {
            sizeBytes >= 1024L * 1024L * 1024L -> "%.2f GB".format(sizeBytes / (1024f * 1024f * 1024f))
            sizeBytes >= 1024L * 1024L -> "%.1f MB".format(sizeBytes / (1024f * 1024f))
            sizeBytes >= 1024L -> "%.0f KB".format(sizeBytes / 1024f)
            else -> "$sizeBytes B"
        }

    val durationLabel: String
        get() {
            if (durationMs <= 0L) return ""
            val totalSeconds = durationMs / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return "%d:%02d".format(minutes, seconds)
        }

    val resolutionLabel: String
        get() = if (width > 0 && height > 0) "${width}x$height" else ""
}

data class StorageUsage(
    val totalBytes: Long = 0L,
    val freeBytes: Long = 0L,
    val appMediaBytes: Long = 0L,
    val photoCount: Int = 0,
    val videoCount: Int = 0
) {
    val usedBytes: Long get() = totalBytes - freeBytes

    val usedLabel: String
        get() = when {
            usedBytes >= 1024L * 1024L * 1024L -> "%.1f GB".format(usedBytes / (1024f * 1024f * 1024f))
            else -> "%.0f MB".format(usedBytes / (1024f * 1024f))
        }

    val totalLabel: String
        get() = when {
            totalBytes >= 1024L * 1024L * 1024L -> "%.1f GB".format(totalBytes / (1024f * 1024f * 1024f))
            else -> "%.0f MB".format(totalBytes / (1024f * 1024f))
        }

    val appMediaLabel: String
        get() = when {
            appMediaBytes >= 1024L * 1024L * 1024L -> "%.2f GB".format(appMediaBytes / (1024f * 1024f * 1024f))
            appMediaBytes >= 1024L * 1024L -> "%.1f MB".format(appMediaBytes / (1024f * 1024f))
            else -> "%.0f KB".format(appMediaBytes / 1024f)
        }
}
