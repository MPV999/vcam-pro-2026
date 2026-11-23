package com.vcampro.ultimate.data.device

import android.app.ActivityManager
import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaRecorder
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.util.Range
import android.util.Size
import com.vcampro.ultimate.domain.model.DeviceProfile
import com.vcampro.ultimate.domain.model.PerformanceMode

/**
 * Reads the real capabilities of the device: CPU, memory, storage, camera
 * characteristics and codecs, then derives a recommended capture profile.
 */
class DeviceCapabilityAnalyzer(private val context: Context) {

    fun analyze(): DeviceProfile {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo().also { activityManager.getMemoryInfo(it) }
        val totalRamMb = memoryInfo.totalMem / (1024L * 1024L)
        val availableRamMb = memoryInfo.availMem / (1024L * 1024L)

        val stat = StatFs(Environment.getDataDirectory().absolutePath)
        val totalStorageMb = stat.totalBytes / (1024L * 1024L)
        val freeStorageMb = stat.availableBytes / (1024L * 1024L)

        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraIds = runCatching { cameraManager.cameraIdList.toList() }.getOrDefault(emptyList())

        var hasFront = false
        var hasBack = false
        var hasFlash = false
        var maxPhotoWidth = 0
        var maxPhotoHeight = 0
        var supportsHdr = false
        val photoSizes = linkedSetOf<String>()
        val videoSizes = linkedSetOf<String>()
        val fpsRanges = linkedSetOf<String>()

        cameraIds.forEach { id ->
            runCatching {
                val characteristics = cameraManager.getCameraCharacteristics(id)
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                if (facing == CameraCharacteristics.LENS_FACING_FRONT) hasFront = true
                if (facing == CameraCharacteristics.LENS_FACING_BACK) hasBack = true
                if (characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true) hasFlash = true

                val capabilities = characteristics.get(
                    CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES
                )?.toList() ?: emptyList()
                if (capabilities.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE)) {
                    // A backward compatible camera can always stream YUV to the app.
                }

                val configs = characteristics.get(
                    CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP
                )
                configs?.let { map ->
                    map.getOutputSizes(ImageFormat.JPEG)?.forEach { size ->
                        photoSizes.add("${size.width}x${size.height}")
                        if (size.width * size.height > maxPhotoWidth * maxPhotoHeight) {
                            maxPhotoWidth = size.width
                            maxPhotoHeight = size.height
                        }
                    }
                    map.getOutputSizes(MediaRecorder::class.java)?.forEach { size ->
                        videoSizes.add("${size.width}x${size.height}")
                    }
                }

                characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
                    ?.forEach { range: Range<Int> -> fpsRanges.add("${range.lower}-${range.upper}") }

                val hdr = characteristics.get(
                    CameraCharacteristics.CONTROL_AVAILABLE_SCENE_MODES
                )?.contains(18) ?: false // SCENE_MODE_HDR
                if (hdr) supportsHdr = true
            }
        }

        val codecs = hardwareCodecs()

        val recommendedMode = when {
            totalRamMb >= 6144 -> PerformanceMode.QUALITY
            totalRamMb >= 4096 -> PerformanceMode.BALANCED
            else -> PerformanceMode.BATTERY
        }

        val recommendation = recommendationFor(recommendedMode, totalRamMb)

        return DeviceProfile(
            model = Build.MODEL ?: "Android",
            manufacturer = Build.MANUFACTURER ?: "Android",
            androidRelease = Build.VERSION.RELEASE ?: Build.VERSION.SDK_INT.toString(),
            sdkInt = Build.VERSION.SDK_INT,
            cpuArchitectures = Build.SUPPORTED_ABIS?.toList() ?: listOf(Build.CPU_ABI ?: "arm64-v8a"),
            cpuCores = Runtime.getRuntime().availableProcessors(),
            totalRamMb = totalRamMb,
            availableRamMb = availableRamMb,
            totalStorageMb = totalStorageMb,
            freeStorageMb = freeStorageMb,
            cameraIds = cameraIds,
            hasFrontCamera = hasFront,
            hasBackCamera = hasBack,
            hasFlash = hasFlash,
            maxPhotoWidth = maxPhotoWidth,
            maxPhotoHeight = maxPhotoHeight,
            supportedPhotoSizes = photoSizes.toList(),
            supportedVideoSizes = videoSizes.toList(),
            supportedFpsRanges = fpsRanges.toList(),
            hardwareCodecs = codecs,
            hardwareAcceleration = codecs.isNotEmpty(),
            recommendedMode = recommendedMode,
            recommendedWidth = recommendation.first,
            recommendedHeight = recommendation.second,
            recommendedFps = recommendation.third,
            supportsHdr = supportsHdr
        )
    }

    /** Width/height/fps triple that a mode should actually use on this device. */
    fun recommendationFor(
        mode: PerformanceMode,
        totalRamMb: Long = totalRamMb()
    ): Triple<Int, Int, Int> = when (mode) {
        PerformanceMode.PERFORMANCE -> Triple(1280, 720, 30)
        PerformanceMode.BALANCED -> Triple(1920, 1080, 30)
        PerformanceMode.QUALITY -> Triple(1920, 1080, if (totalRamMb >= 6144) 60 else 30)
        PerformanceMode.BATTERY -> Triple(960, 540, 24)
    }

    /** Bitrate used by the video recorder and exporter for a given mode. */
    fun bitrateFor(mode: PerformanceMode, width: Int, height: Int): Int {
        val base = width * height * 4
        return when (mode) {
            PerformanceMode.PERFORMANCE -> (base * 0.6f).toInt()
            PerformanceMode.BALANCED -> base
            PerformanceMode.QUALITY -> (base * 1.6f).toInt()
            PerformanceMode.BATTERY -> (base * 0.4f).toInt()
        }
    }

    fun totalRamMb(): Long {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo().also { activityManager.getMemoryInfo(it) }
        return info.totalMem / (1024L * 1024L)
    }

    fun supportedResolutionList(): List<Size> {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val sizes = linkedSetOf<Size>()
        runCatching {
            cameraManager.cameraIdList.forEach { id ->
                cameraManager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                    ?.getOutputSizes(ImageFormat.JPEG)
                    ?.forEach { sizes.add(it) }
            }
        }
        return sizes.sortedByDescending { it.width * it.height }
    }

    private fun hardwareCodecs(): List<String> {
        val result = mutableListOf<String>()
        runCatching {
            val list = MediaCodecList(MediaCodecList.ALL_CODECS)
            list.codecInfos.forEach { info ->
                if (info.isEncoder && !info.isSoftwareOnly) {
                    info.supportedTypes
                        .filter { it.startsWith("video/") }
                        .forEach { mime ->
                            val label = "${mime.substringAfter('/').uppercase()} (${info.name})"
                            if (!result.contains(label)) result.add(label)
                        }
                }
            }
        }
        return result
    }

    /** True when the device exposes at least one hardware H.264 encoder. */
    fun hasHardwareEncoder(mime: String = "video/avc"): Boolean {
        var found = false
        runCatching {
            val list = MediaCodecList(MediaCodecList.ALL_CODECS)
            list.codecInfos.forEach { info ->
                if (info.isEncoder && !info.isSoftwareOnly && info.supportedTypes.contains(mime)) {
                    found = true
                }
            }
        }
        return found
    }

    fun colorFormatsOf(mime: String = "video/avc"): List<String> {
        val formats = mutableListOf<String>()
        runCatching {
            val list = MediaCodecList(MediaCodecList.ALL_CODECS)
            list.codecInfos.filter { it.isEncoder && it.supportedTypes.contains(mime) }.forEach { info ->
                info.getCapabilitiesForType(mime).colorFormats.forEach { format ->
                    val label = when (format) {
                        MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface -> "Surface"
                        MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible -> "YUV420Flexible"
                        MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar -> "YUV420SemiPlanar"
                        else -> format.toString()
                    }
                    if (!formats.contains(label)) formats.add(label)
                }
            }
        }
        return formats
    }
}
