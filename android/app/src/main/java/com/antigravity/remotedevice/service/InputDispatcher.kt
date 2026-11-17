package com.antigravity.remotedevice.service

import android.content.Context
import android.graphics.PointF
import android.util.DisplayMetrics
import android.util.Log
import android.view.Surface
import android.view.WindowManager
import com.antigravity.remotedevice.domain.model.InputCommand

class InputDispatcher(private val context: Context) {

    companion object {
        private const val TAG = "InputDispatcher"
        private const val MIN_GESTURE_INTERVAL_MS = 25L // Max 40 gestures per second
    }

    private var lastDispatchedTime = 0L

    /**
     * Translates normalized [0.0, 1.0] coordinates to absolute screen pixels,
     * taking into account dynamic screen orientation (Portrait vs Landscape).
     */
    fun mapCoordinates(normX: Float, normY: Float, metrics: DisplayMetrics): PointF {
        // Clamp input bounds
        val clampedX = normX.coerceIn(0.0f, 1.0f)
        val clampedY = normY.coerceIn(0.0f, 1.0f)

        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        val rotation = windowManager?.defaultDisplay?.rotation ?: Surface.ROTATION_0

        val screenW = metrics.widthPixels.toFloat()
        val screenH = metrics.heightPixels.toFloat()

        return when (rotation) {
            Surface.ROTATION_0 -> {
                PointF(clampedX * screenW, clampedY * screenH)
            }
            Surface.ROTATION_90 -> {
                // Rotated 90 degrees clockwise
                PointF((1.0f - clampedY) * screenW, clampedX * screenH)
            }
            Surface.ROTATION_180 -> {
                // Inverted portrait
                PointF((1.0f - clampedX) * screenW, (1.0f - clampedY) * screenH)
            }
            Surface.ROTATION_270 -> {
                // Rotated 270 degrees clockwise (90 counter-clockwise)
                PointF(clampedY * screenW, (1.0f - clampedX) * screenH)
            }
            else -> {
                PointF(clampedX * screenW, clampedY * screenH)
            }
        }
    }

    /**
     * Enforces rate-limiting to prevent dispatch buffer saturation.
     */
    fun shouldThrottle(): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastDispatchedTime < MIN_GESTURE_INTERVAL_MS) {
            return true
        }
        lastDispatchedTime = now
        return false
    }
}
