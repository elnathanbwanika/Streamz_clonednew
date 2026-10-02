package com.linkstreamz.app

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Outline
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.*
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.compose.runtime.mutableStateOf
import androidx.media3.ui.PlayerView

object FloatingOverlayManager {
    val isFloating = mutableStateOf(false)

    private var windowManager: WindowManager? = null
    private var overlayView: FrameLayout? = null

    @SuppressLint("ClickableViewAccessibility")
    fun show(
        context: Context,
        engine: PlayerEngine,
        onClose: () -> Unit,
        onExpand: () -> Unit
    ) {
        if (!Settings.canDrawOverlays(context)) {
            Toast.makeText(context, "Please allow 'Display over other apps' to float the video", Toast.LENGTH_LONG).show()
            context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
            return
        }

        if (isFloating.value) return
        
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager = wm

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        layoutParams.gravity = Gravity.TOP or Gravity.START
        layoutParams.x = 100
        layoutParams.y = 100

        // Match 16:9 aspect roughly
        val width = (context.resources.displayMetrics.widthPixels * 0.7f).toInt()
        val height = (width * 9f / 16f).toInt()
        layoutParams.width = width
        layoutParams.height = height

        val root = FrameLayout(context)
        
        val playerView = PlayerView(context).apply {
            useController = false
            resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
            player = engine.player
            setShutterBackgroundColor(Color.BLACK)
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    val radius = 16f * context.resources.displayMetrics.density
                    outline.setRoundRect(0, 0, view.width, view.height, radius)
                }
            }
        }

        val borderView = View(context).apply {
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
                setStroke((1f * context.resources.displayMetrics.density).toInt(), android.graphics.Color.parseColor("#4CFF6600"))
                cornerRadius = 16f * context.resources.displayMetrics.density
            }
        }

        fun createBtn(iconRes: Int): ImageView {
            return ImageView(context).apply {
                setImageResource(iconRes)
                setBackgroundColor(android.graphics.Color.parseColor("#66000000"))
                setPadding(16, 16, 16, 16)
                this.layoutParams = FrameLayout.LayoutParams(110, 110)
            }
        }

        // 1. Expand / back to app (top-left)
        val expandBtn = createBtn(android.R.drawable.ic_menu_revert).apply {
            setOnClickListener { hide(); onExpand() }
            (layoutParams as FrameLayout.LayoutParams).apply {
                gravity = Gravity.TOP or Gravity.START
                setMargins(20, 20, 0, 0)
            }
        }

        // 2. Close (top-right)
        val closeBtn = createBtn(android.R.drawable.ic_menu_close_clear_cancel).apply {
            setOnClickListener { hide(); onClose() }
            (layoutParams as FrameLayout.LayoutParams).apply {
                gravity = Gravity.TOP or Gravity.END
                setMargins(0, 20, 20, 0)
            }
        }

        // 3. Play / Pause (bottom-centered)
        val playPauseBtn = createBtn(
            if (engine.player.playWhenReady) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        ).apply {
            setOnClickListener { 
                if (engine.player.playWhenReady) engine.player.pause() else engine.player.play()
                setImageResource(if (engine.player.playWhenReady) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
            }
            (layoutParams as FrameLayout.LayoutParams).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                setMargins(0, 0, 0, 20)
            }
        }

        // Update play/pause button state dynamically
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                playPauseBtn.setImageResource(if (playWhenReady) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
            }
        }
        engine.player.addListener(listener)

        // 4. Drag to resize (bottom-right)
        val resizeBtn = createBtn(android.R.drawable.ic_menu_zoom).apply {
            (layoutParams as FrameLayout.LayoutParams).apply {
                gravity = Gravity.BOTTOM or Gravity.END
                setMargins(0, 0, 20, 20)
            }
        }

        var resizeInitialX = 0f
        var resizeInitialY = 0f
        var initialWidth = 0
        var initialHeight = 0
        resizeBtn.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    resizeInitialX = event.rawX
                    resizeInitialY = event.rawY
                    initialWidth = layoutParams.width
                    initialHeight = layoutParams.height
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - resizeInitialX
                    val newWidth = (initialWidth + dx).toInt().coerceIn(300, 1500)
                    val newHeight = (newWidth * 9f / 16f).toInt()
                    layoutParams.width = newWidth
                    layoutParams.height = newHeight
                    wm.updateViewLayout(root, layoutParams)
                    true
                }
                else -> false
            }
        }

        root.addView(playerView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        root.addView(borderView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        root.addView(expandBtn)
        root.addView(closeBtn)
        root.addView(playPauseBtn)
        root.addView(resizeBtn)

        // Dragging logic for the main window (attached to root)
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        root.setOnTouchListener { v, event ->
            // Skip dragging if touching the resize button
            if (event.x > root.width - 150 && event.y > root.height - 150) {
                return@setOnTouchListener false
            }
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    layoutParams.x = initialX + (event.rawX - initialTouchX).toInt()
                    layoutParams.y = initialY + (event.rawY - initialTouchY).toInt()
                    wm.updateViewLayout(root, layoutParams)
                    true
                }
                else -> false
            }
        }
        
        // Remove listener on hide
        root.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {}
            override fun onViewDetachedFromWindow(v: View) {
                engine.player.removeListener(listener)
            }
        })

        wm.addView(root, layoutParams)
        overlayView = root
        isFloating.value = true
    }

    fun hide() {
        if (!isFloating.value) return
        overlayView?.let { windowManager?.removeView(it) }
        overlayView = null
        isFloating.value = false
    }
}
