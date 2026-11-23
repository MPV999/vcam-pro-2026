package com.vcampro.sdk

import android.content.Context
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.Executors

/**
 * Wraps a physical camera (front or back) as a virtual camera source, delivering
 * YUV frames through the same pipeline as file based sources.
 *
 * The camera is bound to the host [LifecycleOwner]; nothing is opened before
 * [prepare] and everything is unbound in [release].
 */
class PhysicalCameraSource(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val lensFacing: Int = CameraSelector.LENS_FACING_BACK,
    override val displayName: String = if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
        "Camera trước"
    } else {
        "Camera sau"
    }
) : BaseCameraSource() {

    override val id: String = "camera:${if (lensFacing == CameraSelector.LENS_FACING_FRONT) "front" else "back"}"

    private val analysisExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "vcam-camera-analysis").also { it.priority = Thread.NORM_PRIORITY }
    }

    private var cameraProvider: ProcessCameraProvider? = null
    private var imageAnalysis: ImageAnalysis? = null

    @Volatile
    private var released = false

    override val capabilities: SourceCapabilities
        get() = SourceCapabilities(
            canSeek = false,
            canLoop = false,
            canChangeSpeed = false,
            hasDuration = false,
            isLive = true
        )

    override fun prepare(configuration: FrameConfiguration) {
        if (released) return
        clearError()
        this.configuration = configuration
        setState(SourceState.PREPARING)
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            try {
                val provider = future.get()
                cameraProvider = provider
                val analysis = ImageAnalysis.Builder()
                    .setResolutionSelector(
                        ResolutionSelector.Builder()
                            .setResolutionStrategy(
                                ResolutionStrategy(
                                    Size(configuration.width, configuration.height),
                                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                                )
                            )
                            .build()
                    )
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                    .build()
                analysis.setAnalyzer(analysisExecutor) { proxy -> analyse(proxy) }
                imageAnalysis = analysis

                val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, selector, analysis)
                setState(SourceState.READY)
            } catch (t: Throwable) {
                reportError("Không mở được camera vật lý", t)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun analyse(proxy: ImageProxy) {
        try {
            if (released) return
            if (currentState() == SourceState.PAUSED) return
            val image = proxy.image
            if (image == null) {
                proxy.close()
                return
            }
            var buffer = ImageConverter.imageToNv21(image)
            val rotation = proxy.imageInfo.rotationDegrees
            if (rotation != 0) {
                buffer = YuvConverter.rotateNv21(buffer, image.width, image.height, rotation)
            }
            if (configuration.mirrorHorizontal) {
                buffer = YuvConverter.mirrorNv21(buffer, configuration.width, configuration.height)
            }
            if (image.width != configuration.width || image.height != configuration.height) {
                buffer = YuvConverter.scaleNv21(
                    buffer,
                    image.width,
                    image.height,
                    configuration.width,
                    configuration.height
                )
            }
            if (currentState() == SourceState.PREPARING || currentState() == SourceState.READY) {
                setState(SourceState.PLAYING)
            }
            emitFrame(buffer)
        } catch (t: Throwable) {
            reportError("Lỗi đọc khung hình camera", t)
        } finally {
            proxy.close()
        }
    }

    override fun start() {
        if (released) return
        if (currentState() == SourceState.PAUSED) {
            setState(SourceState.PLAYING)
            return
        }
        setState(SourceState.PLAYING)
    }

    override fun pause() {
        if (currentState() == SourceState.PLAYING) setState(SourceState.PAUSED)
    }

    override fun resume() {
        if (released) return
        setState(SourceState.PLAYING)
    }

    override fun stop() {
        if (currentState() != SourceState.ERROR) setState(SourceState.READY)
    }

    override fun release() {
        released = true
        try {
            imageAnalysis?.clearAnalyzer()
        } catch (ignored: Throwable) {
        }
        try {
            cameraProvider?.unbindAll()
        } catch (ignored: Throwable) {
        }
        analysisExecutor.shutdown()
        imageAnalysis = null
        cameraProvider = null
        setState(SourceState.RELEASED)
    }
}
