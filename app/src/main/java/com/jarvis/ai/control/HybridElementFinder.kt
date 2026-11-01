package com.jarvis.ai.control

import android.content.Context
import android.graphics.Bitmap
import android.view.accessibility.AccessibilityNodeInfo
import com.jarvis.ai.ai.GeminiBrainClient
import com.jarvis.ai.service.JarvisAccessibilityService
import com.jarvis.ai.storage.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

data class ExecutionResult(
    val success: Boolean,
    val message: String,
    val tappedX: Float? = null,
    val tappedY: Float? = null
)

/**
 * Dual-Engine Hybrid Element Finder for Project JARVIS.
 * Engine 1: Fast local Accessibility Node tree search (10ms, ₹0 cost).
 * Engine 2: Gemini 3.8 Flash Multimodal Vision Grounding fallback (analyzes screenshot & finds pixel coordinates).
 */
class HybridElementFinder(
    private val context: Context,
    private val gestureExecutor: GestureExecutor,
    private val screenNodeParser: ScreenNodeParser = ScreenNodeParser(),
    private val brainClient: GeminiBrainClient = GeminiBrainClient(context)
) {
    private val prefs = PreferencesManager.getInstance(context)

    /**
     * Finds and clicks an element on the screen matching the query description.
     */
    suspend fun findAndClickElement(
        rootNode: AccessibilityNodeInfo?,
        activePackageName: String?,
        query: String
    ): ExecutionResult = withContext(Dispatchers.Default) {

        // 1. Safety Guardrail Check
        if (!SecurityGuardrail.isPackageSafe(activePackageName)) {
            return@withContext ExecutionResult(
                success = false,
                message = "Blocked: Autonomous actions are disabled in financial and banking apps for your security."
            )
        }

        if (SecurityGuardrail.isSensitiveElement(query)) {
            return@withContext ExecutionResult(
                success = false,
                message = "Blocked: Autonomous entry of PINs and Passwords is strictly prohibited."
            )
        }

        // 2. Fast Engine: Search local accessibility hierarchy (0ms lag, 0 API cost)
        val nodeElement = screenNodeParser.findTargetElement(rootNode, query)

        if (nodeElement != null) {
            var clicked = false
            withContext(Dispatchers.Main) {
                clicked = gestureExecutor.tap(nodeElement.centerX, nodeElement.centerY)
            }
            return@withContext ExecutionResult(
                success = clicked,
                message = if (clicked) "Tapped '${nodeElement.text.ifBlank { query }}' at (${nodeElement.centerX.toInt()}, ${nodeElement.centerY.toInt()})"
                else "Gesture dispatch failed",
                tappedX = nodeElement.centerX,
                tappedY = nodeElement.centerY
            )
        }

        // 3. Visual AI Engine (Gemini 3.8 Flash Vision Grounding):
        // If not found in native accessibility tree (e.g. Flutter Canvas / WebViews / Games / Custom Views)
        if (prefs.isScreenVisionEnabled) {
            val screenshot = captureScreenSnapshot()
            if (screenshot != null) {
                val coordinates = brainClient.locateElementOnScreen(screenshot, query)
                if (coordinates != null) {
                    val (x, y) = coordinates
                    var clicked = false
                    withContext(Dispatchers.Main) {
                        clicked = gestureExecutor.tap(x, y)
                    }
                    return@withContext ExecutionResult(
                        success = clicked,
                        message = if (clicked) "Vision AI: Located & clicked '$query' at (${x.toInt()}, ${y.toInt()})"
                        else "Vision AI found '$query' but tap gesture failed",
                        tappedX = x,
                        tappedY = y
                    )
                }
            }
        }

        return@withContext ExecutionResult(
            success = false,
            message = "Element '$query' was not detected on active screen."
        )
    }

    /**
     * Captures current screen snapshot asynchronously from JarvisAccessibilityService.
     */
    private suspend fun captureScreenSnapshot(): Bitmap? = suspendCancellableCoroutine { cont ->
        val service = JarvisAccessibilityService.instance
        if (service == null) {
            cont.resume(null)
            return@suspendCancellableCoroutine
        }

        service.takeScreenSnapshot { bitmap ->
            if (cont.isActive) {
                cont.resume(bitmap)
            }
        }
    }

    /**
     * Types text into the search bar or message field on the current screen.
     */
    fun typeIntoInputField(rootNode: AccessibilityNodeInfo?, textToType: String): Boolean {
        val inputElement = screenNodeParser.findEditableInput(rootNode) ?: return false
        return gestureExecutor.typeTextIntoNode(inputElement.rawNode, textToType)
    }
}
