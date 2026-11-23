package com.vcampro.ultimate.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.camera.core.CameraSelector
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.vcampro.sdk.CameraSource
import com.vcampro.sdk.FrameConfiguration
import com.vcampro.sdk.FrameStats
import com.vcampro.sdk.ImageConverter
import com.vcampro.sdk.ProcessedFrameSource
import com.vcampro.sdk.VcamSdk
import com.vcampro.sdk.VideoFrame
import com.vcampro.sdk.VideoFileSource
import com.vcampro.sdk.VirtualCameraSession
import com.vcampro.sdk.YuvConverter
import com.vcampro.ultimate.MainActivity
import com.vcampro.ultimate.core.overlay.OverlayState
import com.vcampro.ultimate.data.media.FrameCompositor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/** Live state of the background pipeline, read by the studio screen. */
object VirtualCameraRuntime {
    val running = MutableStateFlow(false)
    val stats = MutableStateFlow(FrameStats())
    val preview = MutableStateFlow<android.graphics.Bitmap?>(null)
    val message = MutableStateFlow<String?>(null)
    val bubbleVisible = MutableStateFlow(false)
}

/**
 * Keeps the virtual camera pipeline alive after the user leaves the app and owns
 * the floating bubble that repositions the overlay layer.
 *
 * The pipeline is: background source (physical camera, video file or still
 * image) -> overlay compositor -> [ProcessedFrameSource] -> [VirtualCameraSession],
 * which delivers frames to consumers registered by the host application (or by
 * an app that integrates the SDK). It writes into no other app's camera.
 */
class VirtualCameraService : Service(), LifecycleOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val compositor = FrameCompositor()

    private var session: VirtualCameraSession? = null
    private var baseSource: CameraSource? = null
    private var processedSource: ProcessedFrameSource? = null

    private var windowManager: WindowManager? = null
    private var bubbleView: View? = null
    private var bubbleParams: WindowManager.LayoutParams? = null

    private var lastPreviewNanos = 0L

    override fun onCreate() {
        super.onCreate()
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopPipeline()
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, buildNotification())

        val sourceType = intent?.getStringExtra(EXTRA_SOURCE) ?: SOURCE_CAMERA_BACK
        val uri = intent?.getStringExtra(EXTRA_URI)
        val width = intent?.getIntExtra(EXTRA_WIDTH, 1280) ?: 1280
        val height = intent?.getIntExtra(EXTRA_HEIGHT, 720) ?: 720
        val fps = intent?.getIntExtra(EXTRA_FPS, 30) ?: 30
        val bubble = intent?.getBooleanExtra(EXTRA_BUBBLE, true) ?: true

        startPipeline(sourceType, uri, FrameConfiguration(width = width, height = height, fps = fps))
        if (bubble) showBubble()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopPipeline()
        hideBubble()
        scope.cancel()
        runCatching { lifecycleRegistry.currentState = Lifecycle.State.DESTROYED }
        VirtualCameraRuntime.running.value = false
        VirtualCameraRuntime.bubbleVisible.value = false
        super.onDestroy()
    }

    // ---------------------------------------------------------------- pipeline

    private fun startPipeline(sourceType: String, uriString: String?, config: FrameConfiguration) {
        stopPipeline()

        val processed = VcamSdk.createProcessedFrameSource("VCAM PRO (nền)")
        processedSource = processed

        val parsedUri = uriString?.let { runCatching { Uri.parse(it) }.getOrNull() }
        val base: CameraSource? = when (sourceType) {
            SOURCE_CAMERA_FRONT -> VcamSdk.createPhysicalCameraSource(
                this,
                this,
                CameraSelector.LENS_FACING_FRONT
            )

            SOURCE_CAMERA_BACK -> VcamSdk.createPhysicalCameraSource(
                this,
                this,
                CameraSelector.LENS_FACING_BACK
            )

            SOURCE_VIDEO -> parsedUri?.let { VcamSdk.createVideoFileSource(this, it) }
            SOURCE_IMAGE -> parsedUri?.let { VcamSdk.createImageFileSource(this, it) }
            else -> null
        }

        if (base == null) {
            VirtualCameraRuntime.message.value = "Chưa có nguồn dữ liệu cho phiên nền"
            VirtualCameraRuntime.running.value = false
            stopSelf()
            return
        }

        base.setConsumer { frame -> onBaseFrame(frame, processed) }
        base.prepare(config)
        base.start()
        baseSource = base

        if (base is VideoFileSource) {
            base.setLooping(true)
        }

        val newSession = VcamSdk.createSession(this)
        newSession.setConfiguration(config)
        newSession.setSource(processed)
        newSession.start()
        session = newSession

        VirtualCameraRuntime.running.value = true
        VirtualCameraRuntime.message.value = "Đang chạy nền"

        scope.launch {
            newSession.stats.collect { VirtualCameraRuntime.stats.value = it }
        }
        scope.launch {
            newSession.state.collect { state ->
                if (state.name == "ERROR") {
                    VirtualCameraRuntime.message.value = "Phiên nền gặp lỗi"
                }
            }
        }
    }

    private fun onBaseFrame(frame: VideoFrame, processed: ProcessedFrameSource) {
        val composed = compositor.compose(
            frame,
            OverlayState.layer.value,
            OverlayState.params.value
        )
        processed.submitNv21(composed, frame.width, frame.height)

        val now = System.nanoTime()
        if (now - lastPreviewNanos > 250_000_000L) {
            lastPreviewNanos = now
            runCatching {
                val smallWidth = (frame.width / 3).coerceAtLeast(2)
                val smallHeight = (frame.height / 3).coerceAtLeast(2)
                val small = YuvConverter.scaleNv21(
                    composed,
                    frame.width,
                    frame.height,
                    smallWidth,
                    smallHeight
                )
                VirtualCameraRuntime.preview.value =
                    ImageConverter.nv21ToBitmap(small, smallWidth, smallHeight)
            }
        }
    }

    private fun stopPipeline() {
        runCatching { session?.release() }
        session = null
        runCatching { baseSource?.release() }
        baseSource = null
        processedSource = null
        VirtualCameraRuntime.running.value = false
        VirtualCameraRuntime.preview.value = null
        VirtualCameraRuntime.stats.value = FrameStats()
    }

    // ------------------------------------------------------------------ bubble

    private fun showBubble() {
        if (!Settings.canDrawOverlays(this)) {
            VirtualCameraRuntime.bubbleVisible.value = false
            VirtualCameraRuntime.message.value = "Chưa cấp quyền hiển thị trên ứng dụng khác"
            return
        }
        if (bubbleView != null) return

        val manager = getSystemService(WINDOW_SERVICE) as WindowManager
        windowManager = manager

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 24
            y = 260
        }
        bubbleParams = params

        val view = createBubbleView(params)
        bubbleView = view
        runCatching { manager.addView(view, params) }
            .onFailure { VirtualCameraRuntime.message.value = it.message }
        VirtualCameraRuntime.bubbleVisible.value = true
    }

    private fun hideBubble() {
        val view = bubbleView ?: return
        runCatching { windowManager?.removeView(view) }
        bubbleView = null
        bubbleParams = null
        VirtualCameraRuntime.bubbleVisible.value = false
    }

    private fun createBubbleView(params: WindowManager.LayoutParams): View {
        val density = resources.displayMetrics.density
        fun dp(value: Int): Int = (value * density).toInt()

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val bubble = TextView(this).apply {
            text = "VC"
            setTextColor(Color.WHITE)
            textSize = 13f
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xFF4F8CFF.toInt())
                setStroke(dp(2), 0xCCFFFFFF.toInt())
            }
        }
        container.addView(bubble, LinearLayout.LayoutParams(dp(54), dp(54)))

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xE60B0F20.toInt())
            setPadding(dp(6), dp(6), dp(6), dp(6))
            visibility = View.GONE
        }

        fun actionButton(label: String, onClick: () -> Unit): TextView = TextView(this).apply {
            text = label
            setTextColor(Color.WHITE)
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(9), dp(10), dp(9))
            background = GradientDrawable().apply {
                cornerRadius = dp(8).toFloat()
                setColor(0xFF1F2937.toInt())
            }
            setOnClickListener { onClick() }
        }

        fun row(vararg children: View): LinearLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            children.forEach { addView(it, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)) }
        }

        panel.addView(row(
            actionButton("←") { OverlayState.nudge(-0.02f, 0f) },
            actionButton("→") { OverlayState.nudge(0.02f, 0f) }
        ))
        panel.addView(row(
            actionButton("↑") { OverlayState.nudge(0f, -0.02f) },
            actionButton("↓") { OverlayState.nudge(0f, 0.02f) }
        ))
        panel.addView(row(
            actionButton("Nhỏ") { OverlayState.changeScale(-0.05f) },
            actionButton("To") { OverlayState.changeScale(0.05f) }
        ))
        panel.addView(row(
            actionButton("Mờ") { OverlayState.changeAlpha(-0.1f) },
            actionButton("Rõ") { OverlayState.changeAlpha(0.1f) }
        ))
        panel.addView(row(
            actionButton("Đặt lại") { OverlayState.reset() },
            actionButton("Dừng") {
                stopPipeline()
                hideBubble()
                stopSelf()
            }
        ))

        container.addView(panel, LinearLayout.LayoutParams(dp(168), LinearLayout.LayoutParams.WRAP_CONTENT))

        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f

        bubble.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    params.x = startX + (event.rawX - touchX).toInt()
                    params.y = startY + (event.rawY - touchY).toInt()
                    runCatching { windowManager?.updateViewLayout(container, params) }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    val moved = kotlin.math.abs(event.rawX - touchX) + kotlin.math.abs(event.rawY - touchY)
                    if (moved < dp(10)) {
                        panel.visibility =
                            if (panel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                    }
                    true
                }

                else -> false
            }
        }

        return container
    }

    // ------------------------------------------------------------ notification

    private fun buildNotification(): Notification {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "VCAM PRO chạy nền",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Phiên camera ảo và bong bóng điều chỉnh lớp phủ" }
            manager.createNotificationChannel(channel)
        }

        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("VCAM PRO đang chạy nền")
            .setContentText("Bong bóng điều chỉnh vị trí lớp phủ camera")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .build()
    }

    companion object {
        const val ACTION_START = "com.vcampro.ultimate.action.START_VIRTUAL_CAMERA"
        const val ACTION_STOP = "com.vcampro.ultimate.action.STOP_VIRTUAL_CAMERA"

        const val EXTRA_SOURCE = "source"
        const val EXTRA_URI = "uri"
        const val EXTRA_WIDTH = "width"
        const val EXTRA_HEIGHT = "height"
        const val EXTRA_FPS = "fps"
        const val EXTRA_BUBBLE = "bubble"

        const val SOURCE_CAMERA_BACK = "camera_back"
        const val SOURCE_CAMERA_FRONT = "camera_front"
        const val SOURCE_VIDEO = "video"
        const val SOURCE_IMAGE = "image"

        private const val CHANNEL_ID = "vcam_background"
        private const val NOTIFICATION_ID = 4211

        fun start(
            context: Context,
            sourceType: String,
            uri: String?,
            width: Int,
            height: Int,
            fps: Int,
            bubble: Boolean
        ) {
            val intent = Intent(context, VirtualCameraService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_SOURCE, sourceType)
                putExtra(EXTRA_URI, uri)
                putExtra(EXTRA_WIDTH, width)
                putExtra(EXTRA_HEIGHT, height)
                putExtra(EXTRA_FPS, fps)
                putExtra(EXTRA_BUBBLE, bubble)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            runCatching {
                context.stopService(Intent(context, VirtualCameraService::class.java))
            }
        }
    }
}
