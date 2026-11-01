package com.jarvis.ai.control

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator

/**
 * Visual Laser Tap Feedback Overlay for Project JARVIS.
 * Displays a glowing neon-green ripple pulse at exact (x, y) coordinates
 * whenever an autonomous click gesture is performed on screen.
 */
class VisualTouchFeedbackOverlay(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())

    fun flashTapIndicator(x: Float, y: Float) {
        mainHandler.post {
            try {
                val rippleView = LaserRippleView(context, x, y) { view ->
                    try {
                        windowManager.removeView(view)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                }

                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    layoutFlag,
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.START
                }

                windowManager.addView(rippleView, params)
                rippleView.startAnimation()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private class LaserRippleView(
        context: Context,
        private val targetX: Float,
        private val targetY: Float,
        private val onFinished: (View) -> Unit
    ) : View(context) {

        private var currentRadius = 10f
        private var currentAlpha = 255

        private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#00E676") // JARVIS Neon Green
            style = Paint.Style.STROKE
            strokeWidth = 6f
        }

        private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#00E676")
            style = Paint.Style.FILL
        }

        fun startAnimation() {
            val animator = ValueAnimator.ofFloat(10f, 75f).apply {
                duration = 450
                interpolator = DecelerateInterpolator()
                addUpdateListener { animation ->
                    currentRadius = animation.animatedValue as Float
                    currentAlpha = ((1f - animation.animatedFraction) * 255).toInt()
                    ringPaint.alpha = currentAlpha
                    corePaint.alpha = (currentAlpha * 0.4f).toInt()
                    invalidate()
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        onFinished(this@LaserRippleView)
                    }
                })
            }
            animator.start()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            // 1. Draw glowing inner core
            canvas.drawCircle(targetX, targetY, currentRadius * 0.4f, corePaint)
            // 2. Draw expanding laser ring
            canvas.drawCircle(targetX, targetY, currentRadius, ringPaint)
        }
    }
}
