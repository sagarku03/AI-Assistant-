package com.jarvis.ai.ai

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Base64
import com.jarvis.ai.storage.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Real-Time Bidirectional Streaming Client for Gemini 3.8 Live API.
 * Uses WebSockets with BidiGenerateContent to stream raw audio in both directions.
 * Low-latency (<800ms) human-like voice conversation with interruption handling.
 */
class GeminiLiveClient(
    private val context: Context,
    private val onConnected: () -> Unit = {},
    private val onDisconnected: () -> Unit = {},
    private val onAssistantTranscript: (String) -> Unit = {},
    private val onError: (String) -> Unit = {}
) {
    private val prefs = PreferencesManager.getInstance(context)
    private val scope = CoroutineScope(Dispatchers.IO)

    private val client = OkHttpClient.Builder()
        .pingInterval(10, TimeUnit.SECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // Infinite read timeout for long streaming
        .build()

    private var webSocket: WebSocket? = null
    private var audioTrack: AudioTrack? = null
    private var isPlaying = false

    companion object {
        private const val LIVE_WS_URL = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent"
        private const val PLAYBACK_SAMPLE_RATE = 24000 // Gemini Live outputs 24kHz PCM
    }

    fun connect() {
        val apiKey = prefs.geminiApiKey
        if (apiKey.isBlank()) {
            onError("Gemini API key is required for Live Voice Conversation.")
            return
        }

        initAudioTrack()

        val url = "$LIVE_WS_URL?key=$apiKey"
        val request = Request.Builder().url(url).build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                sendInitialSetupFrame()
                onConnected()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleServerMessage(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                onError("Live API connection error: ${t.localizedMessage ?: "Unknown error"}")
                disconnect()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                onDisconnected()
            }
        })
    }

    private fun initAudioTrack() {
        try {
            val minBufferSize = AudioTrack.getMinBufferSize(
                PLAYBACK_SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(PLAYBACK_SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(maxOf(minBufferSize, 8192))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack?.play()
            isPlaying = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun sendInitialSetupFrame() {
        val setupPayload = JSONObject().apply {
            put("setup", JSONObject().apply {
                put("model", "models/gemini-3.8-live")
                put("generationConfig", JSONObject().apply {
                    put("responseModalities", JSONArray().put("AUDIO"))
                    put("speechConfig", JSONObject().apply {
                        put("voiceConfig", JSONObject().apply {
                            put("prebuiltVoiceConfig", JSONObject().apply {
                                put("voiceName", "Puck") // Natural, dynamic voice
                            })
                        })
                    })
                })
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().put(JSONObject().apply {
                        put("text", "You are JARVIS, a helpful, intelligent personal AI companion on Android. Speak in warm, natural conversational Hinglish. Respond quickly and assist the user with tasks.")
                    }))
                })
            })
        }
        webSocket?.send(setupPayload.toString())
    }

    /**
     * Streams raw 16kHz PCM audio buffer from device microphone into Gemini Live API.
     */
    fun sendAudioChunk(pcmData: ByteArray) {
        val base64Data = Base64.encodeToString(pcmData, Base64.NO_WRAP)
        val payload = JSONObject().apply {
            put("realtimeInput", JSONObject().apply {
                put("mediaChunks", JSONArray().put(JSONObject().apply {
                    put("mimeType", "audio/pcm;rate=16000")
                    put("data", base64Data)
                }))
            })
        }
        webSocket?.send(payload.toString())
    }

    private fun handleServerMessage(message: String) {
        try {
            val json = JSONObject(message)
            val serverContent = json.optJSONObject("serverContent") ?: return
            val modelTurn = serverContent.optJSONObject("modelTurn") ?: return
            val parts = modelTurn.optJSONArray("parts") ?: return

            for (i in 0 until parts.length()) {
                val part = parts.getJSONObject(i)

                // 1. Play received audio
                val inlineData = part.optJSONObject("inlineData")
                if (inlineData != null) {
                    val base64Audio = inlineData.optString("data", "")
                    if (base64Audio.isNotBlank()) {
                        val pcmBytes = Base64.decode(base64Audio, Base64.NO_WRAP)
                        audioTrack?.write(pcmBytes, 0, pcmBytes.size)
                    }
                }

                // 2. Transcript logging
                val text = part.optString("text", "")
                if (text.isNotBlank()) {
                    onAssistantTranscript(text)
                }
            }

            // Check if user interrupted the model
            if (serverContent.optBoolean("interrupted", false)) {
                audioTrack?.pause()
                audioTrack?.flush()
                audioTrack?.play()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stopAudioPlayback() {
        try {
            audioTrack?.pause()
            audioTrack?.flush()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun disconnect() {
        stopAudioPlayback()
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        audioTrack = null
        isPlaying = false

        webSocket?.close(1000, "Normal closure")
        webSocket = null
    }
}
