package com.jarvis.ai.control

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Autonomous Native Gesture Dispatcher for Project JARVIS.
 * Dispatches simulated human touch gestures directly to the Android OS.
 */
class GestureExecutor(
    private val service: AccessibilityService,
    private val touchFeedbackOverlay: VisualTouchFeedbackOverlay? = null
) {

    /**
     * Dispatches a single click gesture at specific screen coordinates (x, y).
     */
    fun tap(x: Float, y: Float, onComplete: ((Boolean) -> Unit)? = null): Boolean {
        // 1. Flash laser ripple pointer on screen
        touchFeedbackOverlay?.flashTapIndicator(x, y)

        // 2. Build gesture stroke path
        val path = Path().apply {
            moveTo(x, y)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, 80) // 80ms human-like tap
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        return service.dispatchGesture(
            gesture,
            object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    onComplete?.invoke(true)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    onComplete?.invoke(false)
                }
            },
            null
        )
    }

    /**
     * Long presses at (x, y) for 600ms.
     */
    fun longPress(x: Float, y: Float, onComplete: ((Boolean) -> Unit)? = null): Boolean {
        touchFeedbackOverlay?.flashTapIndicator(x, y)

        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 600)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        return service.dispatchGesture(
            gesture,
            object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    onComplete?.invoke(true)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    onComplete?.invoke(false)
                }
            },
            null
        )
    }

    /**
     * Dispatches a smooth human-like scroll/swipe gesture from (fromX, fromY) to (toX, toY).
     */
    fun swipe(
        fromX: Float,
        fromY: Float,
        toX: Float,
        toY: Float,
        durationMs: Long = 300,
        onComplete: ((Boolean) -> Unit)? = null
    ): Boolean {
        val path = Path().apply {
            moveTo(fromX, fromY)
            lineTo(toX, toY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        return service.dispatchGesture(
            gesture,
            object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    onComplete?.invoke(true)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    onComplete?.invoke(false)
                }
            },
            null
        )
    }

    /**
     * Injects text into an editable input node (e.g. YouTube Search Bar, WhatsApp message input).
     */
    fun typeTextIntoNode(node: AccessibilityNodeInfo, text: String): Boolean {
        if (!node.isEditable && !node.isFocusable) {
            return false
        }

        // Set focus
        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)

        // Set text directly
        val arguments = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
    }

    // Global System Navigation Gestures
    fun pressBack(): Boolean = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
    fun pressHome(): Boolean = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
    fun pressRecents(): Boolean = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
    fun openNotifications(): Boolean = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
}
