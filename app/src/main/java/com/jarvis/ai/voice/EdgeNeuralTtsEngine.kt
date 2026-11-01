package com.jarvis.ai.voice

import android.content.Context
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * 100% FREE High-Fidelity Microsoft Edge Neural TTS Engine for Project JARVIS.
 * Produces ultra-natural human speech (not robotic) with breathing and authentic inflection.
 * Supported voices:
 * - "hi-IN-MadhurNeural" (Natural conversational Hindi Male)
 * - "hi-IN-SwaraNeural" (Natural conversational Hindi Female)
 * - "en-US-ChristopherNeural" (Deep, authoritative, calm authentic JARVIS voice)
 * - "en-IN-PrabhatNeural" (Natural Indian English)
 */
class EdgeNeuralTtsEngine(
    private val context: Context,
    private val onPlaybackStarted: () -> Unit = {},
    private val onPlaybackCompleted: () -> Unit = {},
    private val onError: (errorMsg: String) -> Unit = {}
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.IO)
    private var mediaPlayer: MediaPlayer? = null
    private var currentWebSocket: WebSocket? = null

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val EDGE_TTS_WS_URL =
            "wss://speech.platform.bing.com/consumer/speech/synthesize/readaheadwork/edge/v1?TrustedClientToken=6A5AA1D4EA65497283467882D7341F0"
        const val VOICE_JARVIS_AUTHENTIC = "en-US-ChristopherNeural"
        const val VOICE_HINDI_NATURAL = "hi-IN-MadhurNeural"
        const val VOICE_HINDI_FEMALE = "hi-IN-SwaraNeural"
        const val VOICE_INDIAN_ENGLISH = "en-IN-PrabhatNeural"
    }

    /**
     * Speaks the given text using human neural synthesis with barge-in support.
     */
    fun speak(
        text: String,
        voiceName: String = VOICE_HINDI_NATURAL,
        rate: String = "+0%",
        pitch: String = "+0Hz"
    ) {
        stop() // Stop any previous speech immediately for instant interruption

        scope.launch {
            try {
                synthesizeAndPlay(text, voiceName, rate, pitch)
            } catch (e: Exception) {
                e.printStackTrace()
                mainHandler.post { onError(e.message ?: "Edge TTS synthesis failure") }
            }
        }
    }

    private fun synthesizeAndPlay(
        text: String,
        voiceName: String,
        rate: String,
        pitch: String
    ) {
        val audioStream = ByteArrayOutputStream()
        val requestId = UUID.randomUUID().toString().replace("-", "")

        val request = Request.Builder()
            .url(EDGE_TTS_WS_URL)
            .addHeader("Pragma", "no-cache")
            .addHeader("Cache-Control", "no-cache")
            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36 Edg/120.0.0.0")
            .addHeader("Origin", "chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold")
            .build()

        currentWebSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                // 1. Send speech.config
                val timestamp = SimpleDateFormat("EEE MMM dd yyyy HH:mm:ss 'GMT'Z", Locale.US).format(Date())
                val configPayload = "X-Timestamp:$timestamp\r\n" +
                        "Content-Type:application/json; charset=utf-8\r\n" +
                        "Path:speech.config\r\n\r\n" +
                        "{\"context\":{\"synthesis\":{\"audio\":{\"metadataoptions\":{\"sentenceBoundaryEnabled\":\"false\",\"wordBoundaryEnabled\":\"false\"},\"outputFormat\":\"audio-24khz-48kbitrate-mono-mp3\"}}}}"
                webSocket.send(configPayload)

                // 2. Send SSML text payload
                val ssml = "<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xmlns:mstts='https://www.w3.org/2001/mstts' xml:lang='en-US'>" +
                        "<voice name='$voiceName'>" +
                        "<prosody pitch='$pitch' rate='$rate'>" +
                        xmlEscape(text) +
                        "</prosody></voice></speak>"

                val ssmlPayload = "X-RequestId:$requestId\r\n" +
                        "Content-Type:application/ssml+xml\r\n" +
                        "X-Timestamp:$timestamp\r\n" +
                        "Path:ssml\r\n\r\n" +
                        ssml
                webSocket.send(ssmlPayload)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                val data = bytes.toByteArray()
                // Edge TTS returns a 2-byte header length, followed by header text, followed by raw MP3 audio
                if (data.size > 2) {
                    val headerLength = ((data[0].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF)
                    val audioOffset = headerLength + 2
                    if (data.size > audioOffset) {
                        audioStream.write(data, audioOffset, data.size - audioOffset)
                    }
                }
            }

            override fun onMessage(webSocket: WebSocket, textMessage: String) {
                if (textMessage.contains("Path:turn.end")) {
                    webSocket.close(1000, "Done")
                    val audioBytes = audioStream.toByteArray()
                    if (audioBytes.isNotEmpty()) {
                        playMp3Bytes(audioBytes)
                    }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                mainHandler.post { onError("WebSocket connection error: ${t.message}") }
            }
        })
    }

    private fun playMp3Bytes(bytes: ByteArray) {
        try {
            val tempAudioFile = File.createTempFile("jarvis_speech_", ".mp3", context.cacheDir)
            FileOutputStream(tempAudioFile).use { it.write(bytes) }

            mainHandler.post {
                try {
                    mediaPlayer?.release()
                    mediaPlayer = MediaPlayer().apply {
                        setDataSource(tempAudioFile.absolutePath)
                        setOnPreparedListener {
                            start()
                            onPlaybackStarted()
                        }
                        setOnCompletionListener {
                            onPlaybackCompleted()
                            tempAudioFile.delete()
                        }
                        setOnErrorListener { _, _, _ ->
                            tempAudioFile.delete()
                            true
                        }
                        prepareAsync()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    onError("Audio playback failed: ${e.message}")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Immediately terminates audio playback (Barge-In).
     */
    fun stop() {
        try {
            currentWebSocket?.cancel()
            currentWebSocket = null
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            }
            mediaPlayer = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun isSpeaking(): Boolean {
        return mediaPlayer?.isPlaying == true
    }

    private fun xmlEscape(input: String): String {
        return input.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
