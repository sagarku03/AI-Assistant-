package com.jarvis.ai.voice

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import com.jarvis.ai.ai.GeminiBrainClient
import com.jarvis.ai.ai.IntentType
import com.jarvis.ai.ai.JarvisDecision
import com.jarvis.ai.service.JarvisAccessibilityService
import com.jarvis.ai.ui.components.OrbState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Central Orchestrator for Project JARVIS.
 * Glues Wake-Word -> Speech Recognition -> Gemini Conversational Brain -> Natural Edge TTS -> System Action Execution.
 */
class JarvisVoiceCoordinator(
    private val context: Context,
    private val onOrbStateChanged: (OrbState) -> Unit = {},
    private val onAudioAmplitude: (Float) -> Unit = {},
    private val onStatusLog: (String) -> Unit = {}
) {
    private val scope = CoroutineScope(Dispatchers.Main)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val ttsEngine = EdgeNeuralTtsEngine(
        context = context,
        onPlaybackStarted = {
            onOrbStateChanged(OrbState.LISTENING)
            onStatusLog("JARVIS is speaking...")
        },
        onPlaybackCompleted = {
            onOrbStateChanged(OrbState.IDLE)
            onStatusLog("Standby • Listening for 'Hey Jarvis'")
        },
        onError = { err ->
            onStatusLog("Voice error: $err")
            onOrbStateChanged(OrbState.IDLE)
        }
    )

    private val speechToText = SpeechToTextManager(
        context = context,
        onSpeechRecognized = { recognizedText ->
            handleUserTranscript(recognizedText)
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
        onAudioAmplitude = { amp ->
            // Idle acoustic feedback
        }
    )

    private val brainClient = GeminiBrainClient(context)

    fun initialize(apiKey: String = "") {
        brainClient.setApiKey(apiKey)
        wakeWordEngine.startListening()
        onStatusLog("Wake word listener active. Say 'Hey Jarvis' or 'Suno Jarvis'")
    }

    /**
     * Triggered when hands-free wake word is spoken or when the Arc Orb is tapped.
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
     * Processes finalized speech transcript through Gemini Brain.
     */
    private fun handleUserTranscript(transcript: String) {
        onStatusLog("Query: \"$transcript\"")
        onOrbStateChanged(OrbState.THINKING)

        scope.launch {
            val decision: JarvisDecision = brainClient.processUserSpeech(transcript)

            when (decision.intentType) {
                IntentType.CHIT_CHAT -> {
                    onStatusLog("JARVIS: ${decision.spokenResponse}")
                    ttsEngine.speak(decision.spokenResponse, voiceName = EdgeNeuralTtsEngine.VOICE_HINDI_NATURAL)
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
                    ttsEngine.speak(decision.spokenResponse, voiceName = EdgeNeuralTtsEngine.VOICE_HINDI_NATURAL)
                }

                IntentType.CODE_TASK -> {
                    onOrbStateChanged(OrbState.EXECUTING)
                    onStatusLog("Coding task: ${decision.projectTitle ?: "Website"}")
                    // Saves code to /sdcard/JarvisProjects/ and announces
                    ttsEngine.speak(decision.spokenResponse, voiceName = EdgeNeuralTtsEngine.VOICE_HINDI_NATURAL)
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
                // Common app aliases
                when (appNameOrPackage.lowercase()) {
                    "youtube" -> packageManager.getLaunchIntentForPackage("com.google.android.youtube")
                    "whatsapp" -> packageManager.getLaunchIntentForPackage("com.whatsapp")
                    "chrome" -> packageManager.getLaunchIntentForPackage("com.android.chrome")
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
        ttsEngine.stop()
        speechToText.stopListening()
        wakeWordEngine.stopListening()
    }
}
