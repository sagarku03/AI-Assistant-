package com.jarvis.ai.voice

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.mutableStateListOf
import com.jarvis.ai.ai.GeminiBrainClient
import com.jarvis.ai.ai.IntentType
import com.jarvis.ai.ai.JarvisDecision
import com.jarvis.ai.service.JarvisAccessibilityService
import com.jarvis.ai.storage.PreferencesManager
import com.jarvis.ai.storage.ProjectFileManager
import com.jarvis.ai.ui.components.OrbState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Central Orchestrator for Project JARVIS.
 * Glues Dual Input (Keyboard Text + Hands-Free Voice) -> Gemini Conversational Brain -> Natural Edge TTS -> System Action Execution.
 */
class JarvisVoiceCoordinator(
    private val context: Context,
    private val onOrbStateChanged: (OrbState) -> Unit = {},
    private val onAudioAmplitude: (Float) -> Unit = {},
    private val onStatusLog: (String) -> Unit = {}
) {
    private val scope = CoroutineScope(Dispatchers.Main)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val prefs = PreferencesManager.getInstance(context)
    private val projectFileManager = ProjectFileManager(context)

    // Observable chat history for Compose UI
    val messages = mutableStateListOf<ChatMessage>()

    private val ttsEngine = EdgeNeuralTtsEngine(
        context = context,
        onPlaybackStarted = {
            onOrbStateChanged(OrbState.LISTENING)
            onStatusLog("JARVIS is speaking...")
        },
        onPlaybackCompleted = {
            onOrbStateChanged(OrbState.IDLE)
            onStatusLog("Standby • Ready for your command")
        },
        onError = { err ->
            onStatusLog("Voice notice: $err")
            onOrbStateChanged(OrbState.IDLE)
        }
    )

    private val speechToText = SpeechToTextManager(
        context = context,
        onSpeechRecognized = { recognizedText ->
            handleUserInput(recognizedText, isVoice = true)
        },
        onPartialSpeech = { partial ->
            onStatusLog("Heard: \"$partial...\"")
        },
        onAudioAmplitude = { amp ->
            onAudioAmplitude(amp)
        },
        onError = { err ->
            onStatusLog("STT: $err")
            onOrbStateChanged(OrbState.IDLE)
        }
    )

    private val wakeWordEngine = WakeWordEngine(
        context = context,
        onWakeWordDetected = { phrase ->
            onStatusLog("Wake word triggered: \"$phrase\"")
            onWakeWordTriggered()
        },
        onAudioAmplitude = {
            // Idle acoustic feedback
        }
    )

    private val brainClient = GeminiBrainClient(context)

    fun initialize(apiKey: String = "") {
        if (apiKey.isNotBlank()) {
            brainClient.setApiKey(apiKey)
        }
        if (prefs.isWakeWordEnabled) {
            wakeWordEngine.startListening()
        }
        onStatusLog("JARVIS Initialized • Ready")
        if (messages.isEmpty()) {
            messages.add(
                ChatMessage(
                    text = "Hello Boss! I am JARVIS. How may I assist you today? You can type with keyboard or tap the mic / say 'Hey Jarvis'.",
                    isUser = false
                )
            )
        }
    }

    /**
     * Triggered when hands-free wake word is spoken or when the Arc Orb / Mic is tapped.
     */
    fun onWakeWordTriggered() {
        // 1. Barge-In: Stop any active speech immediately
        ttsEngine.stop()

        // 2. Set UI to Listening state
        onOrbStateChanged(OrbState.LISTENING)
        onStatusLog("Listening to Boss...")

        // 3. Start high-accuracy speech recognition
        speechToText.startListening(languageCode = "hi-IN")
    }

    /**
     * Handles text entered directly from the keyboard.
     */
    fun processUserText(text: String) {
        if (text.isBlank()) return
        ttsEngine.stop()
        handleUserInput(text.trim(), isVoice = false)
    }

    /**
     * Unified processor for both voice transcripts and keyboard typed text.
     */
    private fun handleUserInput(inputText: String, isVoice: Boolean) {
        // Append user message to chat UI
        messages.add(ChatMessage(text = inputText, isUser = true))

        onStatusLog("Processing: \"$inputText\"")
        onOrbStateChanged(OrbState.THINKING)

        scope.launch {
            val decision: JarvisDecision = brainClient.processUserSpeech(inputText)

            // Append assistant response to chat UI
            messages.add(ChatMessage(text = decision.displayResponse, isUser = false))

            when (decision.intentType) {
                IntentType.CHIT_CHAT -> {
                    onStatusLog("JARVIS: ${decision.spokenResponse}")
                    ttsEngine.speak(
                        decision.spokenResponse,
                        voiceName = prefs.selectedVoice,
                        rate = prefs.voiceRate,
                        pitch = prefs.voicePitch
                    )
                }

                IntentType.SYSTEM_ACTION -> {
                    onOrbStateChanged(OrbState.EXECUTING)
                    onStatusLog("Executing Action: ${decision.targetApp ?: "Screen Tap"}")

                    // 1. Launch target app if specified
                    decision.targetApp?.let { appOrPackage ->
                        launchAppByIntent(appOrPackage)
                    }

                    // 2. Find and click button via AccessibilityService
                    decision.targetButtonText?.let { buttonText ->
                        mainHandler.postDelayed({
                            JarvisAccessibilityService.instance?.performAutonomousClick(buttonText) { success, msg ->
                                onStatusLog(msg)
                            }
                        }, 1200) // Delay for app window load
                    }

                    // 3. Speak natural voice confirmation
                    ttsEngine.speak(
                        decision.spokenResponse,
                        voiceName = prefs.selectedVoice,
                        rate = prefs.voiceRate,
                        pitch = prefs.voicePitch
                    )
                }

                IntentType.CODE_TASK -> {
                    onOrbStateChanged(OrbState.EXECUTING)
                    val title = decision.projectTitle ?: "New Project"
                    onStatusLog("Generated Code Project: $title")

                    // Save generated code to disk for instant live preview in Code Sandbox
                    decision.generatedCode?.let { code ->
                        projectFileManager.saveWebProject(title, code)
                    }

                    ttsEngine.speak(
                        decision.spokenResponse,
                        voiceName = prefs.selectedVoice,
                        rate = prefs.voiceRate,
                        pitch = prefs.voicePitch
                    )
                }
            }
        }
    }

    private fun launchAppByIntent(appNameOrPackage: String) {
        try {
            val packageManager = context.packageManager
            val intent = if (appNameOrPackage.contains(".")) {
                packageManager.getLaunchIntentForPackage(appNameOrPackage)
            } else {
                when (appNameOrPackage.lowercase()) {
                    "youtube" -> packageManager.getLaunchIntentForPackage("com.google.android.youtube")
                    "whatsapp" -> packageManager.getLaunchIntentForPackage("com.whatsapp")
                    "chrome" -> packageManager.getLaunchIntentForPackage("com.android.chrome")
                    "settings" -> Intent(android.provider.Settings.ACTION_SETTINGS)
                    else -> null
                }
            }

            intent?.let {
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(it)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            onStatusLog("Failed to launch app: ${e.message}")
        }
    }

    fun shutdown() {
        ttsEngine.shutdown()
        speechToText.stopListening()
        wakeWordEngine.stopListening()
    }
}
