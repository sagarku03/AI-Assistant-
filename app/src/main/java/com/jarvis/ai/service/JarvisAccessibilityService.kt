package com.jarvis.ai.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.jarvis.ai.control.GestureExecutor
import com.jarvis.ai.control.HybridElementFinder
import com.jarvis.ai.control.ScreenNodeParser
import com.jarvis.ai.control.VisualTouchFeedbackOverlay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Autonomous Android Accessibility Service for Project JARVIS.
 * Provides the hands and eyes to inspect UI hierarchies and dispatch native click/swipe gestures.
 */
class JarvisAccessibilityService : AccessibilityService() {

    companion object {
        var instance: JarvisAccessibilityService? = null
            private set
    }

    private val scope = CoroutineScope(Dispatchers.Main)
    private var currentPackageName: String? = null

    lateinit var gestureExecutor: GestureExecutor
        private set

    lateinit var hybridFinder: HybridElementFinder
        private set

    private lateinit var touchFeedbackOverlay: VisualTouchFeedbackOverlay

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this

        touchFeedbackOverlay = VisualTouchFeedbackOverlay(this)
        gestureExecutor = GestureExecutor(this, touchFeedbackOverlay)
        hybridFinder = HybridElementFinder(this, gestureExecutor, ScreenNodeParser())
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event?.let {
            if (it.packageName != null) {
                currentPackageName = it.packageName.toString()
            }
        }
    }

    override fun onInterrupt() {}

    /**
     * Public method invoked by the Voice Coordinator to search and click an element.
     */
    fun performAutonomousClick(query: String, onResult: (Boolean, String) -> Unit) {
        scope.launch {
            val result = hybridFinder.findAndClickElement(
                rootNode = rootInActiveWindow,
                activePackageName = currentPackageName,
                query = query
            )
            onResult(result.success, result.message)
        }
    }

    /**
     * Public method to type text into an active search bar or input field.
     */
    fun performAutonomousType(text: String): Boolean {
        return hybridFinder.typeIntoInputField(rootInActiveWindow, text)
    }

    fun getActivePackage(): String? = currentPackageName

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }
}
