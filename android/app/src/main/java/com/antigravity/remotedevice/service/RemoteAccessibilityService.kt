package com.antigravity.remotedevice.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.antigravity.remotedevice.domain.model.GlobalNavTarget
import com.antigravity.remotedevice.domain.model.InputCommand
import com.antigravity.remotedevice.domain.model.InputActionType

class RemoteAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "RemoteA11yService"
        var instance: RemoteAccessibilityService? = null
            private set

        val isServiceConnected: Boolean
            get() = instance != null
    }

    private lateinit var inputDispatcher: InputDispatcher

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        inputDispatcher = InputDispatcher(this)
        Log.i(TAG, "RemoteAccessibilityService connected. Ready for remote input dispatch.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Can monitor window state or focus transitions transparently
    }

    override fun onInterrupt() {
        Log.w(TAG, "RemoteAccessibilityService interrupted.")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        Log.i(TAG, "RemoteAccessibilityService destroyed.")
    }

    /**
     * Executes an incoming remote input command received from the WebRTC DataChannel.
     */
    fun executeCommand(command: InputCommand, callback: ((Boolean) -> Unit)? = null) {
        if (!::inputDispatcher.isInitialized) {
            inputDispatcher = InputDispatcher(this)
        }

        if (inputDispatcher.shouldThrottle()) {
            Log.w(TAG, "Throttling high-frequency gesture")
            callback?.invoke(false)
            return
        }

        val metrics = resources.displayMetrics

        when (command.action) {
            InputActionType.TAP -> {
                val point = inputDispatcher.mapCoordinates(command.x, command.y, metrics)
                dispatchTap(point.x, point.y, command.durationMs.coerceIn(30L, 100L), callback)
            }
            InputActionType.LONG_PRESS -> {
                val point = inputDispatcher.mapCoordinates(command.x, command.y, metrics)
                val duration = maxOf(command.durationMs, 750L)
                dispatchTap(point.x, point.y, duration, callback)
            }
            InputActionType.SWIPE -> {
                val start = inputDispatcher.mapCoordinates(command.x, command.y, metrics)
                val end = inputDispatcher.mapCoordinates(command.endX, command.endY, metrics)
                val duration = command.durationMs.coerceIn(100L, 1000L)
                dispatchSwipe(start.x, start.y, end.x, end.y, duration, callback)
            }
            InputActionType.SCROLL -> {
                val start = inputDispatcher.mapCoordinates(command.x, command.y, metrics)
                val end = inputDispatcher.mapCoordinates(command.endX, command.endY, metrics)
                val duration = command.durationMs.coerceIn(80L, 300L)
                dispatchSwipe(start.x, start.y, end.x, end.y, duration, callback)
            }
            InputActionType.GLOBAL_NAV -> {
                val success = when (command.navTarget) {
                    GlobalNavTarget.BACK -> performGlobalAction(GLOBAL_ACTION_BACK)
                    GlobalNavTarget.HOME -> performGlobalAction(GLOBAL_ACTION_HOME)
                    GlobalNavTarget.RECENTS -> performGlobalAction(GLOBAL_ACTION_RECENTS)
                    GlobalNavTarget.NOTIFICATIONS -> performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
                    GlobalNavTarget.QUICK_SETTINGS -> performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
                    GlobalNavTarget.LOCK_SCREEN -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
                        } else {
                            false
                        }
                    }
                    null -> false
                }
                callback?.invoke(success)
            }
            InputActionType.INPUT_TEXT -> {
                val textToInject = command.text ?: ""
                val success = setFocusedText(textToInject)
                callback?.invoke(success)
            }
        }
    }

    private fun dispatchTap(x: Float, y: Float, durationMs: Long, callback: ((Boolean) -> Unit)?) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            callback?.invoke(false)
            return
        }

        val path = Path().apply {
            moveTo(x, y)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                super.onCompleted(gestureDescription)
                callback?.invoke(true)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                super.onCancelled(gestureDescription)
                callback?.invoke(false)
            }
        }, null)
    }

    private fun dispatchSwipe(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        durationMs: Long,
        callback: ((Boolean) -> Unit)?
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            callback?.invoke(false)
            return
        }

        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                super.onCompleted(gestureDescription)
                callback?.invoke(true)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                super.onCancelled(gestureDescription)
                callback?.invoke(false)
            }
        }, null)
    }

    private fun setFocusedText(text: String): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        val focusedNode = rootNode.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)

        if (focusedNode != null && focusedNode.isEditable) {
            val arguments = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
            val success = focusedNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
            focusedNode.recycle()
            rootNode.recycle()
            return success
        }

        rootNode.recycle()
        return false
    }
}
