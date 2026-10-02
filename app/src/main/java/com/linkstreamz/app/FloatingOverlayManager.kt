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

        val expandBtn = ImageView(context).apply {
            setImageResource(android.R.drawable.ic_menu_revert)
            setOnClickListener { hide(); onExpand() }
        }
        val closeBtn = ImageView(context).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setOnClickListener { hide(); onClose() }
        }

        root.addView(playerView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        root.addView(borderView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        
        val topBar = FrameLayout(context)
        topBar.addView(expandBtn, FrameLayout.LayoutParams(120, 120).apply { gravity = Gravity.START; setMargins(20,20,0,0) })
        topBar.addView(closeBtn, FrameLayout.LayoutParams(120, 120).apply { gravity = Gravity.END; setMargins(0,20,20,0) })
        root.addView(topBar, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply { gravity = Gravity.TOP })

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        root.setOnTouchListener { _, event ->
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
