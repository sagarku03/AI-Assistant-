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
 * Guarantees zero mic conflict with clean audio handoff between WakeWordEngine and SpeechToTextManager.
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
            resumeWakeWordListener()
        },
        onError = { err ->
            onStatusLog("Voice notice: $err")
            onOrbStateChanged(OrbState.IDLE)
            resumeWakeWordListener()
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
            resumeWakeWordListener()
        }
    )

    private val wakeWordEngine = WakeWordEngine(
        context = context,
        onWakeWordDetected = { phrase ->
            onStatusLog("Wake word triggered: \"$phrase\"")
            onWakeWordTriggered()
        },
        onAudioAmplitude = { amp ->
            onAudioAmplitude(amp)
        }
    )

    private val brainClient = GeminiBrainClient(context)

    fun initialize(apiKey: String = "") {
        if (apiKey.isNotBlank()) {
            brainClient.setApiKey(apiKey)
        }
        resumeWakeWordListener()
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

    private fun resumeWakeWordListener() {
        if (prefs.isWakeWordEnabled) {
            wakeWordEngine.startListening()
        }
    }

    /**
     * Triggered when hands-free wake word is spoken or when the Arc Orb / Mic is tapped.
     * Stops WakeWordEngine audio recording first so the microphone is 100% free for SpeechRecognizer!
     */
    fun onWakeWordTriggered() {
        // 1. Barge-In: Stop active speech immediately
        ttsEngine.stop()

        // 2. Stop wake-word audio record so SpeechRecognizer has exclusive mic access
        wakeWordEngine.stopListening()

        // 3. Set UI to Listening state
        onOrbStateChanged(OrbState.LISTENING)
        onStatusLog("Listening to Boss...")

        // 4. Start high-accuracy speech recognition
        speechToText.startListening(languageCode = "hi-IN")
    }

    /**
     * Handles text entered directly from the keyboard.
     */
    fun processUserText(text: String) {
        if (text.isBlank()) return
        ttsEngine.stop()
        wakeWordEngine.stopListening()
        handleUserInput(text.trim(), isVoice = false)
    }

    /**
     * Unified processor for both voice transcripts and keyboard typed text.
     */
    private fun handleUserInput(inputText: String, isVoice: Boolean) {
        messages.add(ChatMessage(text = inputText, isUser = true))

        onStatusLog("Processing: \"$inputText\"")
        onOrbStateChanged(OrbState.THINKING)

        scope.launch {
            val decision: JarvisDecision = brainClient.processUserSpeech(inputText)

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
                        }, 1200)
                    }

                    // 3. Speak natural voice confirmation
                    ttsEngine.speak(
                        decision.spokenResponse,
                        voiceName = prefs.selectedVoice,
                        rate = prefs.voiceRate,
                        pitch = prefs.voicePitch
                    )
                }

                IntentType.SCREEN_READ -> {
                    onOrbStateChanged(OrbState.EXECUTING)
                    if (!prefs.isScreenVisionEnabled) {
                        val disabledMsg = "Screen Vision abhi off hai boss. Settings me jakar 'Screen Vision AI' toggle enable karein."
                        messages.add(ChatMessage(text = disabledMsg, isUser = false))
                        onStatusLog(disabledMsg)
                        ttsEngine.speak(disabledMsg, voiceName = prefs.selectedVoice, rate = prefs.voiceRate, pitch = prefs.voicePitch)
                        return@launch
                    }

                    onStatusLog("Scanning screen with Gemini 3.8 Flash...")
                    val service = JarvisAccessibilityService.instance
                    if (service != null) {
                        service.takeScreenSnapshot { bitmap ->
                            scope.launch {
                                if (bitmap != null) {
                                    val description = brainClient.analyzeScreenImage(bitmap, inputText)
                                    messages.add(ChatMessage(text = description, isUser = false))
                                    onStatusLog("Screen read complete.")
                                    ttsEngine.speak(description, voiceName = prefs.selectedVoice, rate = prefs.voiceRate, pitch = prefs.voicePitch)
                                } else {
                                    val failMsg = "Screen capture nahi ho paayi. Kripya check karein ki Accessibility permission on hai."
                                    messages.add(ChatMessage(text = failMsg, isUser = false))
                                    ttsEngine.speak(failMsg, voiceName = prefs.selectedVoice, rate = prefs.voiceRate, pitch = prefs.voicePitch)
                                }
                            }
                        }
                    } else {
                        val failMsg = "Accessibility Service active nahi hai. Settings me jakar JARVIS Accessibility enable karein."
                        messages.add(ChatMessage(text = failMsg, isUser = false))
                        ttsEngine.speak(failMsg, voiceName = prefs.selectedVoice, rate = prefs.voiceRate, pitch = prefs.voicePitch)
                    }
                }

                IntentType.SCREEN_CLICK -> {
                    onOrbStateChanged(OrbState.EXECUTING)
                    val target = decision.targetButtonText ?: inputText
                    onStatusLog("Finding '$target' on screen...")

                    JarvisAccessibilityService.instance?.performAutonomousClick(target) { success, msg ->
                        onStatusLog(msg)
                        val reply = if (success) "Maine screen par '$target' click kar diya hai boss." else "Screen par '$target' nahi mila."
                        messages.add(ChatMessage(text = msg, isUser = false))
                        ttsEngine.speak(reply, voiceName = prefs.selectedVoice, rate = prefs.voiceRate, pitch = prefs.voicePitch)
                    }
                }

                IntentType.CODE_TASK -> {
                    onOrbStateChanged(OrbState.EXECUTING)
                    val title = decision.projectTitle ?: "New Project"
                    onStatusLog("Generated Code Project: $title")

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
