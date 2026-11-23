package com.vcampro.sdk

import android.content.Context
import android.net.Uri
import androidx.camera.core.CameraSelector
import androidx.lifecycle.LifecycleOwner

/**
 * Entry point of the VCAM PRO Virtual Camera SDK.
 *
 * ```kotlin
 * val session = VcamSdk.createSession(context)
 * val source = VcamSdk.createVideoFileSource(context, uri, "Mẫu quảng cáo")
 * session.setConfiguration(FrameConfiguration.landscape720p(fps = 30))
 * session.addConsumer("recorder") { frame ->
 *     // frame.data is NV21, frame.width x frame.height, ~30 fps
 * }
 * session.setSource(source)
 * session.start()
 * ```
 *
 * The SDK produces frames for applications that integrate it. It does not, and
 * cannot, replace the system camera for third party applications: Android has no
 * public API for a device wide virtual camera, and VCAM PRO does not use root,
 * hooks or camera HAL modification.
 */
object VcamSdk {

    const val VERSION: String = "1.0.0"

    /** Creates a session that owns one source and fans frames out to consumers. */
    fun createSession(context: Context): VirtualCameraSession = VirtualCameraSession(context)

    /** A source backed by a physical camera through CameraX. */
    fun createPhysicalCameraSource(
        context: Context,
        lifecycleOwner: LifecycleOwner,
        lensFacing: Int = CameraSelector.LENS_FACING_BACK,
        displayName: String? = null
    ): CameraSource = if (displayName == null) {
        PhysicalCameraSource(context, lifecycleOwner, lensFacing)
    } else {
        PhysicalCameraSource(context, lifecycleOwner, lensFacing, displayName)
    }

    /** A source that decodes a video file, with pacing, looping and seeking. */
    fun createVideoFileSource(
        context: Context,
        uri: Uri,
        displayName: String = "Video: ${uri.lastPathSegment ?: "nguồn"}"
    ): CameraSource = VideoFileSource(context, uri, displayName)

    /** A source that repeats a still image. */
    fun createImageFileSource(
        context: Context,
        uri: Uri,
        displayName: String = "Ảnh: ${uri.lastPathSegment ?: "nguồn"}"
    ): CameraSource = ImageFileSource(context, uri, displayName)

    /** A source fed by the host application. */
    fun createProcessedFrameSource(displayName: String = "Khung hình đã xử lý"): ProcessedFrameSource =
        ProcessedFrameSource(displayName)

    fun defaultConfiguration(): FrameConfiguration = FrameConfiguration()
}
