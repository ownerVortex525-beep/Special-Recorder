package com.ownervortex.nyxrecorder.core.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Matrix
import android.graphics.PixelFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.ownervortex.nyxrecorder.R
import kotlin.math.abs
import kotlin.math.max

/**
 * Floating recording controls.
 *
 * The bubble window used to be [WindowManager.LayoutParams.FLAG_SECURE], which makes
 * MediaProjection render it as a **black rectangle** in the recording. It is now
 * drawn without FLAG_SECURE and kept at ~20% window alpha so it is almost
 * invisible in footage; when opened (clicked) it animates to full contrast for a
 * comfortable tap target, then fades back to 20%.
 *
 * FaceCam is a separate window, always visible in recordings, with rounded
 * corners and a corrected mirror/rotation.
 */
class BubbleController(
    private val context: Context,
    private val faceCamEnabled: Boolean,
    private val musicSelected: Boolean,
    private val onMusicToggle: (() -> Unit)? = null,
    private val onMusicStop: (() -> Unit)? = null,
    private val onMusicForward: (() -> Unit)? = null
) {

    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var bubbleView: View? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var panelView: View? = null
    private var panelParams: WindowManager.LayoutParams? = null
    private var faceCamView: FaceCamView? = null

    private var onPauseToggle: (() -> Unit)? = null
    private var onStop: (() -> Unit)? = null
    private var onHide: (() -> Unit)? = null

    private var paused = false
    private var panelOpen = false
    private var shown = false

    private var timeText: TextView? = null

    private fun Int.dp(): Int = (this * context.resources.displayMetrics.density).toInt()

    fun show(
        onPauseToggle: () -> Unit,
        onStop: () -> Unit,
        onHide: () -> Unit
    ) {
        if (shown) return
        if (!Settings.canDrawOverlays(context)) return
        this.onPauseToggle = onPauseToggle
        this.onStop = onStop
        this.onHide = onHide
        shown = true
        addBubble()
        if (faceCamEnabled) showFaceCam()
    }

    @SuppressLint("ClickableViewAccessibility", "InflateParams")
    private fun addBubble() {
        val view = LayoutInflater.from(context).inflate(R.layout.view_bubble, null)
        bubbleView = view
        timeText = view.findViewById(R.id.bubbleTime)

        val params = overlayParams(view.width.takeIf { it > 0 } ?: 64.dp(), 64.dp()).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 16.dp()
            y = 220.dp()
            alpha = DIMMED_ALPHA
        }
        bubbleParams = params

        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var dragging = false

        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    setDim(false)
                    downX = event.rawX
                    downY = event.rawY
                    startX = params.x
                    startY = params.y
                    dragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (!dragging && (abs(dx) > 8 || abs(dy) > 8)) dragging = true
                    if (dragging) {
                        params.x = max(0, startX + dx.toInt())
                        params.y = max(0, startY + dy.toInt())
                        try {
                            windowManager.updateViewLayout(view, params)
                        } catch (_: Exception) {
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!dragging) {
                        togglePanel()
                    } else {
                        snapToEdge(view, params)
                        setDim(true)
                    }
                    true
                }
                else -> false
            }
        }

        try {
            windowManager.addView(view, params)
        } catch (_: Exception) {
            shown = false
        }
    }

    private fun snapToEdge(view: View, params: WindowManager.LayoutParams) {
        val screenW = context.resources.displayMetrics.widthPixels
        val bubbleW = params.width
        params.x = if (params.x + bubbleW / 2 < screenW / 2) 0 else screenW - bubbleW
        try {
            windowManager.updateViewLayout(view, params)
        } catch (_: Exception) {
        }
    }

    @SuppressLint("InflateParams")
    private fun togglePanel() {
        if (panelOpen) {
            removePanel()
            return
        }
        setDim(false)
        panelOpen = true
        val panel = LayoutInflater.from(context).inflate(R.layout.view_bubble_panel, null)
        panelView = panel

        val pauseBtn = panel.findViewById<TextView>(R.id.panelPause)
        val stopBtn = panel.findViewById<View>(R.id.panelStop)
        val hideBtn = panel.findViewById<View>(R.id.panelHide)

        pauseBtn.setOnClickListener { togglePause() }

        if (musicSelected && onMusicToggle != null) {
            val musicRow = LayoutInflater.from(context).inflate(R.layout.view_bubble_music_row, null)
            musicRow.findViewById<View>(R.id.musicPlayPause).setOnClickListener { onMusicToggle?.invoke() }
            musicRow.findViewById<View>(R.id.musicStop).setOnClickListener { onMusicStop?.invoke() }
            musicRow.findViewById<View>(R.id.musicForward).setOnClickListener { onMusicForward?.invoke() }
            val container = panel.findViewById<LinearLayout>(R.id.panelMusicSlot)
            container.visibility = View.VISIBLE
            container.addView(musicRow, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ))
        }

        stopBtn.setOnClickListener {
            removePanel()
            onStop?.invoke()
        }
        hideBtn.setOnClickListener {
            removePanel()
            onHide?.invoke()
        }
        updatePauseLabel(pauseBtn)

        val bp = bubbleParams ?: return
        val estWidth = ViewGroupLayoutParams(panel)
        val screenW = context.resources.displayMetrics.widthPixels
        val p = overlayParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = bp.x.coerceAtMost((screenW - estWidth).coerceAtLeast(0))
            // Place below the bubble; if not enough room, place above.
            val bubbleBottom = bp.y + 64.dp()
            val screenH = context.resources.displayMetrics.heightPixels
            y = if (bubbleBottom + 180.dp() > screenH) max(0, bp.y - 180.dp()) else bubbleBottom + 4.dp()
            alpha = FULL_ALPHA
        }
        panelParams = p
        try {
            windowManager.addView(panel, p)
        } catch (_: Exception) {
            panelOpen = false
            return
        }
    }

    private fun ViewGroupLayoutParams(view: View): Int {
        view.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED),
            android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED)
        )
        return view.measuredWidth.takeIf { it > 0 } ?: 280.dp()
    }

    private fun removePanel() {
        panelOpen = false
        panelView?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {
            }
        }
        panelView = null
        setDim(true)
    }

    private fun togglePause() {
        onPauseToggle?.invoke()
    }

    private fun updatePauseLabel(btn: TextView) {
        btn.text = if (paused) "▶ Resume" else "❚❚ Pause"
    }

    private fun setDim(dimmed: Boolean) {
        bubbleParams?.let { p ->
            val target = if (dimmed) DIMMED_ALPHA else FULL_ALPHA
            if (p.alpha != target) {
                p.alpha = target
                bubbleView?.let { windowManager.updateViewLayout(it, p) }
            }
        }
    }

    fun setPaused(value: Boolean) {
        paused = value
        postToMain {
            panelView?.let { panel ->
                val btn = panel.findViewById<TextView>(R.id.panelPause)
                if (btn != null) updatePauseLabel(btn)
            }
        }
    }

    fun updateTime(value: String) {
        postToMain {
            timeText?.text = value
        }
    }

    private fun showFaceCam() {
        try {
            if (!Settings.canDrawOverlays(context)) return
            val cameraManager =
                context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val lens = cameraManager.cameraIdList.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING) ==
                    CameraCharacteristics.LENS_FACING_FRONT
            } ?: return

            val size = 140.dp()
            val view = FaceCamView(context, lens, cameraManager)
            faceCamView = view
            val params = overlayParams(size, size).apply {
                gravity = Gravity.TOP or Gravity.END
                x = 8.dp()
                y = 220.dp()
                alpha = FULL_ALPHA
                // Rounded corners via outline clipping.
                flags = WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            }
            windowManager.addView(view, params)
            view.applyOutline()
        } catch (_: Exception) {
            faceCamView = null
        }
    }

    fun hide() {
        shown = false
        removePanel()
        bubbleView?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {
            }
        }
        bubbleView = null
        timeText = null
        faceCamView?.let {
            try {
                it.releaseCamera()
                windowManager.removeView(it)
            } catch (_: Exception) {
            }
        }
        faceCamView = null
    }

    private val Int.dp: Int get() = dp()

    private fun overlayParams(width: Int, height: Int): WindowManager.LayoutParams =
        WindowManager.LayoutParams(width, height, overlayType(), PixelFormat.TRANSLUCENT)
            .apply {
                flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            }

    private fun overlayType(): Int =
        if (android.os.Build.VERSION.SDK_INT >= 26)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else 2002

    private inline fun postToMain(crossinline block: () -> Unit) {
        android.os.Handler(context.mainLooper).post {
            try {
                block()
            } catch (_: Exception) {
            }
        }
    }

    companion object {
        const val DIMMED_ALPHA = 0.20f
        const val FULL_ALPHA = 1.0f
    }
}

/**
 * Small front-camera preview. Deliberately NOT FLAG_SECURE (captured on screen).
 * Applies a mirror/rotation matrix so the preview looks natural in the corner.
 */
@SuppressLint("ViewConstructor", "MissingPermission")
class FaceCamView(
    private val ctx: Context,
    private val cameraId: String,
    private val cameraManager: CameraManager
) : FrameLayout(ctx) {

    private var device: android.hardware.camera2.CameraDevice? = null
    private var session: android.hardware.camera2.CameraCaptureSession? = null
    private val preview: TextureView = TextureView(ctx).apply {
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        addView(this)
    }
    private val cornerRadius = 24f // dp
    private var openRequest = false

    init {
        setBackgroundColor(0xFF000000.toInt())
        clipToOutline = true
        preview.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(
                st: SurfaceTexture, w: Int, h: Int
            ) {
                st.setDefaultBufferSize(BUFFER_W, BUFFER_H)
                openCamera(st)
            }

            override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) = Unit

            override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean = true

            override fun onSurfaceTextureUpdated(st: SurfaceTexture) = Unit
        }
    }

    fun applyOutline() {
        outlineProvider = object : OutlineProvider() {
            override fun getOutline(view: View, outline: android.graphics.Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, dp(cornerRadius))
            }
        }
        post { invalidateOutline() }
    }

    private fun dp(dp: Float): Float = dp * resources.displayMetrics.density

    private fun openCamera(st: SurfaceTexture) {
        if (openRequest) return
        openRequest = true
        try {
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            val sensorOrientation =
                characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
            val display = (ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay
            val rotation = display?.rotation ?: Surface.ROTATION_0
            val displayDeg = when (rotation) {
                Surface.ROTATION_0 -> 0
                Surface.ROTATION_90 -> 90
                Surface.ROTATION_180 -> 180
                Surface.ROTATION_270 -> 270
                else -> 0
            }

            val surface = Surface(st)
            cameraManager.openCamera(
                cameraId,
                object : android.hardware.camera2.CameraDevice.StateCallback() {
                    override fun onOpened(camera: android.hardware.camera2.CameraDevice) {
                        device = camera
                        try {
                            camera.createCaptureSession(listOf(surface),
                                object : android.hardware.camera2.CameraCaptureSession.StateCallback() {
                                    override fun onConfigured(s: android.hardware.camera2.CameraCaptureSession) {
                                        session = s
                                        // Transform preview so it fills the rounded window correctly.
                                        post {
                                            applyTransform(displayDeg, sensorOrientation, front = true)
                                        }
                                        try {
                                            val request =
                                                camera.createCaptureRequest(
                                                    android.hardware.camera2.CameraDevice.TEMPLATE_PREVIEW
                                                ).apply {
                                                    addTarget(surface)
                                                    set(
                                                        android.hardware.camera2.CaptureRequest.CONTROL_AF_MODE,
                                                        android.hardware.camera2.CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                                                    )
                                                }
                                            s.setRepeatingRequest(request, null, null)
                                        } catch (_: Exception) {
                                        }
                                    }

                                    override fun onConfigureFailed(s: android.hardware.camera2.CameraCaptureSession) =
                                        Unit
                                }, null)
                        } catch (_: Exception) {
                        }
                    }

                    override fun onDisconnected(camera: android.hardware.camera2.CameraDevice) {
                        camera.close()
                        device = null
                    }

                    override fun onError(camera: android.hardware.camera2.CameraDevice, error: Int) {
                        camera.close()
                        device = null
                    }
                }, null)
        } catch (_: Exception) {
            openRequest = false
        }
    }

    /**
     * Maps the (landscape) camera buffer into the square window: un-stretch to
     * buffer aspect, cover-scale, rotate upright and mirror for the front cam.
     */
    @SuppressLint("NewApi")
    private fun applyTransform(displayDeg: Int, sensorOrientation: Int, front: Boolean) {
        val w = preview.width
        val h = preview.height
        if (w == 0 || h == 0) return

        val rotation = if (front) {
            (360 - (sensorOrientation + displayDeg) % 360) % 360
        } else {
            (sensorOrientation - displayDeg + 360) % 360
        }

        val bw = BUFFER_W.toFloat()
        val bh = BUFFER_H.toFloat()
        val cx = w / 2f
        val cy = h / 2f
        // Cover-scale once the buffer has been rotated into view space.
        val cover = if (rotation % 180 == 0) {
            maxOf(w / bw, h / bh)
        } else {
            maxOf(w / bh, h / bw)
        }
        val scaleX = cover * bw / w
        val scaleY = cover * bh / h

        val m = Matrix()
        m.setScale(scaleX, scaleY, cx, cy)
        m.postRotate(rotation.toFloat(), cx, cy)
        if (front) {
            m.postScale(-1f, 1f, cx, cy)
        }
        preview.setTransform(m)
    }

    fun releaseCamera() {
        try {
            session?.close()
        } catch (_: Exception) {
        }
        session = null
        try {
            device?.close()
        } catch (_: Exception) {
        }
        device = null
        openRequest = false
    }

    companion object {
        private const val BUFFER_W = 640
        private const val BUFFER_H = 480
    }
}
