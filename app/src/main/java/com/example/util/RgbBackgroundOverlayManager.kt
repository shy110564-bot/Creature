package com.example.util

import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.sin

/**
 * Draws a non-touch-blocking (pass-through) system-wide RGB 4-corner & edge border light
 * over ALL apps (Home Screen, YouTube, WhatsApp, etc.) using WindowManager TYPE_APPLICATION_OVERLAY.
 * Stays active continuously in the background until the user explicitly turns it OFF.
 */
object RgbBackgroundOverlayManager {

    private var windowManager: WindowManager? = null
    private var overlayView: RgbSystemOverlayView? = null

    private val _isBackgroundRgbActive = MutableStateFlow(false)
    val isBackgroundRgbActive: StateFlow<Boolean> = _isBackgroundRgbActive.asStateFlow()

    fun hasOverlayPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    fun requestOverlayPermission(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            runCatching {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
        }
    }

    fun startBackgroundRgbLight(context: Context): Boolean {
        val appContext = context.applicationContext
        if (!hasOverlayPermission(appContext)) {
            requestOverlayPermission(appContext)
            return false
        }
        if (overlayView != null && _isBackgroundRgbActive.value) {
            return true
        }

        return runCatching {
            val wm = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            windowManager = wm

            val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }

            val view = RgbSystemOverlayView(appContext)
            wm.addView(view, params)
            overlayView = view
            _isBackgroundRgbActive.value = true
            true
        }.getOrDefault(false)
    }

    fun stopBackgroundRgbLight() {
        runCatching {
            overlayView?.stopAnimation()
            overlayView?.let { view ->
                windowManager?.removeView(view)
            }
        }
        overlayView = null
        _isBackgroundRgbActive.value = false
    }

    fun toggleBackgroundRgbLight(context: Context): Boolean {
        return if (_isBackgroundRgbActive.value) {
            stopBackgroundRgbLight()
            false
        } else {
            startBackgroundRgbLight(context)
        }
    }
}

private class RgbSystemOverlayView(context: Context) : View(context) {

    private var currentHue = 0f
    private var pulsePhase = 0f

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 11f
    }

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 26f
    }

    private val cornerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val rectF = RectF()
    private val innerRectF = RectF()

    private val animator = ValueAnimator.ofFloat(0f, 360f).apply {
        duration = 2200L
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.RESTART
        interpolator = LinearInterpolator()
        addUpdateListener { anim ->
            currentHue = anim.animatedValue as Float
            pulsePhase = (currentHue / 360f) * 6.28318f
            invalidate()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!animator.isStarted) {
            animator.start()
        }
    }

    override fun onDetachedFromWindow() {
        stopAnimation()
        super.onDetachedFromWindow()
    }

    fun stopAnimation() {
        runCatching { animator.cancel() }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val minDim = minOf(w, h)
        val pulse = 0.90f + 0.14f * sin(pulsePhase)
        val cornerGlowRadius = minDim * 0.45f * pulse

        // 1. 4-Corner Automatic Color-Changing RGB Spotlights
        val corners = arrayOf(
            0f to 0f,
            w to 0f,
            w to h,
            0f to h
        )
        for (i in corners.indices) {
            val (cx, cy) = corners[i]
            val c1 = Color.HSVToColor(165, floatArrayOf((currentHue + i * 90f) % 360f, 1f, 1f))
            val c2 = Color.HSVToColor(75, floatArrayOf((currentHue + i * 90f + 45f) % 360f, 1f, 1f))
            cornerPaint.shader = RadialGradient(
                cx,
                cy,
                cornerGlowRadius,
                intArrayOf(c1, c2, Color.TRANSPARENT),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(cx, cy, cornerGlowRadius, cornerPaint)
        }

        // 2. Full-Screen Edge RGB Sweep Border (Inner crisp line + Outer neon glow)
        val sweepColors = IntArray(13) { idx ->
            Color.HSVToColor(255, floatArrayOf((currentHue + idx * 30f) % 360f, 1f, 1f))
        }
        val glowColors = IntArray(13) { idx ->
            Color.HSVToColor(150, floatArrayOf((currentHue + idx * 30f) % 360f, 1f, 1f))
        }

        val cx = w / 2f
        val cy = h / 2f
        val cornerRad = 54f

        glowPaint.shader = SweepGradient(cx, cy, glowColors, null)
        rectF.set(8f, 8f, w - 8f, h - 8f)
        canvas.drawRoundRect(rectF, cornerRad, cornerRad, glowPaint)

        borderPaint.shader = SweepGradient(cx, cy, sweepColors, null)
        innerRectF.set(4f, 4f, w - 4f, h - 4f)
        canvas.drawRoundRect(innerRectF, cornerRad, cornerRad, borderPaint)
    }
}
