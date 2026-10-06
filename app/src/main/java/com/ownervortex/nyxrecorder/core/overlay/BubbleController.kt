package com.ownervortex.nyxrecorder.core.overlay

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.ownervortex.nyxrecorder.R
import kotlin.math.abs
import kotlin.math.max

/**
 * Floating recording bubble + control panel.
 *
 * The bubble window is flagged FLAG_SECURE so it never appears in the recording
 * itself. FaceCam (when enabled) is a separate window *without* FLAG_SECURE so
 * the camera preview is captured on screen.
 */
class BubbleController(
    private val context: Context,
    private val faceCamEnabled: Boolean
) {

    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var bubbleView: View? = null
    private var panelView: View? = null
    private var faceCamView: FaceCamView? = null

    private var onPauseToggle: (() -> Unit)? = null
    private var onStop: (() -> Unit)? = null
    private var onHide: (() -> Unit)? = null

    private var paused = false
    private var panelOpen = false
    private var shown = false

    private var timeText: TextView? = null
    private var pauseButton: TextView? = null

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

        val params = overlayParams(64.dp).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 300.dp
        }

        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var dragging = false

        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
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
            return
        }
    }

    private fun snapToEdge(view: View, params: WindowManager.LayoutParams) {
        val screenW = context.resources.displayMetrics.widthPixels
        val bubbleW = 64.dp
        params.x = if (params.x + bubbleW / 2 < screenW / 2) 0 else screenW - bubbleW
        try {
            windowManager.updateViewLayout(view, params)
        } catch (_: Exception) {
        }
    }

    @SuppressLint("InflateParams", "SetTextI18n")
    private fun togglePanel() {
        if (panelOpen) {
            removePanel()
            return
        }
        panelOpen = true
        val panel = LayoutInflater.from(context).inflate(R.layout.view_bubble_panel, null)
        panelView = panel
        pauseButton = panel.findViewById(R.id.panelPause)

        panel.findViewById<View>(R.id.panelStop).setOnClickListener {
            removePanel()
            onStop?.invoke()
        }
        panel.findViewById<View>(R.id.panelHide).setOnClickListener {
            removePanel()
            onHide?.invoke()
        }
        pauseButton?.setOnClickListener {
            onPauseToggle?.invoke()
        }

        val bubble = bubbleView ?: return
        val bubbleParams = bubble.layoutParams as? WindowManager.LayoutParams
        val params = overlayParams(ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (bubbleParams?.x ?: 0)
            y = (bubbleParams?.y ?: 0) + 64.dp + 8.dp
        }
        try {
            windowManager.addView(panel, params)
        } catch (_: Exception) {
            panelOpen = false
            return
        }
        updatePauseLabel()
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
        pauseButton = null
    }

    private fun updatePauseLabel() {
        pauseButton?.text = if (paused) "▶ Resume" else "❚❚ Pause"
    }

    fun setPaused(value: Boolean) {
        paused = value
        mainThread { updatePauseLabel() }
        mainThread {
            try {
                bubbleView?.findViewById<TextView>(R.id.bubbleDot)?.text =
                    if (paused) "❚❚" else "●"
            } catch (_: Exception) {
            }
        }
    }

    fun updateTime(value: String) {
        mainThread {
            timeText?.text = value
        }
    }

    private fun showFaceCam() {
        try {
            if (!Settings.canDrawOverlays(context)) return
            val cameraManager =
                context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val lens = cameraManager.cameraIdList.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.LENS_FACING) ==
                    CameraCharacteristics.LENS_FACING_FRONT
            } ?: return

            val size = 120.dp
            val view = FaceCamView(context, lens)
            faceCamView = view
            val params = overlayParams(size).apply {
                gravity = Gravity.TOP or Gravity.END
                x = 8.dp
                y = 200.dp
            }
            windowManager.addView(view, params)
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

    private fun overlayParams(size: Int): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_SECURE,
            PixelFormat.TRANSLUCENT
        )

    private inline fun mainThread(block: () -> Unit) {
        android.os.Handler(context.mainLooper).post {
            try {
                block()
            } catch (_: Exception) {
            }
        }
    }

    private val Int.dp: Int
        get() = (this * context.resources.displayMetrics.density).toInt()
}

/** Small front-camera preview window. Deliberately NOT FLAG_SECURE (captured on screen). */
@SuppressLint("ViewConstructor")
class FaceCamView(
    context: Context,
    private val cameraId: String
) : FrameLayout(context) {

    private var device: android.hardware.camera2.CameraDevice? = null
    private var session: android.hardware.camera2.CameraCaptureSession? = null
    private val preview = android.view.TextureView(context)
    private var openRequest = false

    init {
        addView(
            preview,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )
        preview.surfaceTextureListener = object : android.view.TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(
                st: android.graphics.SurfaceTexture, w: Int, h: Int
            ) {
                openCamera(st, w, h)
            }

            override fun onSurfaceTextureSizeChanged(
                st: android.graphics.SurfaceTexture, w: Int, h: Int
            ) = Unit

            override fun onSurfaceTextureDestroyed(st: android.graphics.SurfaceTexture): Boolean {
                releaseCamera()
                return true
            }

            override fun onSurfaceTextureUpdated(st: android.graphics.SurfaceTexture) = Unit
        }
    }

    @SuppressLint("MissingPermission")
    private fun openCamera(st: android.graphics.SurfaceTexture, w: Int, h: Int) {
        if (openRequest) return
        openRequest = true
        try {
            st.setDefaultBufferSize(w, h)
            val surface = android.view.Surface(st)
            val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            @Suppress("MissingPermission")
            manager.openCamera(
                cameraId,
                object : android.hardware.camera2.CameraDevice.StateCallback() {
                    override fun onOpened(camera: android.hardware.camera2.CameraDevice) {
                        device = camera
                        try {
                            @Suppress("MissingPermission")
                            camera.createCaptureSession(
                                listOf(surface),
                                object : android.hardware.camera2.CameraCaptureSession.StateCallback() {
                                    override fun onConfigured(s: android.hardware.camera2.CameraCaptureSession) {
                                        session = s
                                        try {
                                            val request =
                                                camera.createCaptureRequest(
                                                    android.hardware.camera2.CameraDevice.TEMPLATE_PREVIEW
                                                )
                                            request.addTarget(surface)
                                            request.set(
                                                android.hardware.camera2.CaptureRequest.CONTROL_AF_MODE,
                                                android.hardware.camera2.CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                                            )
                                            s.setRepeatingRequest(request.build(), null, null)
                                        } catch (_: Exception) {
                                        }
                                    }

                                    override fun onConfigureFailed(
                                        s: android.hardware.camera2.CameraCaptureSession
                                    ) = Unit
                                },
                                null
                            )
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
                },
                null
            )
        } catch (_: Exception) {
            openRequest = false
        }
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
}
