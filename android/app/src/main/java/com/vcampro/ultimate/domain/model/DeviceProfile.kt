package com.vcampro.ultimate.domain.model

/**
 * Performance profiles that really change the pipeline configuration.
 */
enum class PerformanceMode(val key: String) {
    PERFORMANCE("performance"),
    BALANCED("balanced"),
    QUALITY("quality"),
    BATTERY("battery");

    companion object {
        fun fromKey(key: String?): PerformanceMode =
            entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: BALANCED
    }
}

/**
 * Snapshot of what this device can actually do. Everything in here is read from
 * the platform at runtime, never assumed.
 */
data class DeviceProfile(
    val model: String,
    val manufacturer: String,
    val androidRelease: String,
    val sdkInt: Int,
    val cpuArchitectures: List<String>,
    val cpuCores: Int,
    val totalRamMb: Long,
    val availableRamMb: Long,
    val totalStorageMb: Long,
    val freeStorageMb: Long,
    val cameraIds: List<String>,
    val hasFrontCamera: Boolean,
    val hasBackCamera: Boolean,
    val hasFlash: Boolean,
    val maxPhotoWidth: Int,
    val maxPhotoHeight: Int,
    val supportedPhotoSizes: List<String>,
    val supportedVideoSizes: List<String>,
    val supportedFpsRanges: List<String>,
    val hardwareCodecs: List<String>,
    val hardwareAcceleration: Boolean,
    val recommendedMode: PerformanceMode,
    val recommendedWidth: Int,
    val recommendedHeight: Int,
    val recommendedFps: Int,
    val supportsHdr: Boolean
) {
    val ramLabel: String get() = "%.1f GB".format(totalRamMb / 1024f)

    val storageLabel: String get() = "%.1f GB / %.1f GB".format(
        (totalStorageMb - freeStorageMb) / 1024f,
        totalStorageMb / 1024f
    )

    val recommendedResolutionLabel: String get() = "${recommendedWidth}x$recommendedHeight @ ${recommendedFps}fps"

    val tierLabel: String
        get() = when {
            totalRamMb >= 6144 -> "Flagship"
            totalRamMb >= 4096 -> "High"
            totalRamMb >= 3072 -> "Mid"
            else -> "Entry"
        }
}
