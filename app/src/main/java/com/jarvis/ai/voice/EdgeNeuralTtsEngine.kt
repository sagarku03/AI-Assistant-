package com.jarvis.ai.voice

import android.content.Context
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
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
 * 100% FREE High-Fidelity Microsoft Edge Neural TTS Engine for Project JARVIS
 * with Automatic Native Android TextToSpeech Fallback.
 *
 * Spoken Voice Rules:
 * - 0% Robotic: Authentic human intonation, natural breathing and pauses.
 * - Zero Emojis or Markdown Symbols Spoken: Strips `*`, `**`, `#`, emojis, code blocks, URLs.
 * - Dual Engine Architecture: Edge Neural Cloud WebSocket -> Fallback to On-Device Android TTS.
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
    private var nativeTts: TextToSpeech? = null
    private var isNativeTtsReady = false

    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val EDGE_TTS_WS_URL =
            "wss://speech.platform.bing.com/consumer/speech/synthesize/readaheadwork/edge/v1?TrustedClientToken=6A5AA1D4EA65497283467882D7341F0"
        const val VOICE_JARVIS_AUTHENTIC = "en-US-ChristopherNeural"
        const val VOICE_HINDI_NATURAL = "hi-IN-MadhurNeural"
        const val VOICE_HINDI_FEMALE = "hi-IN-SwaraNeural"
        const val VOICE_INDIAN_ENGLISH = "en-IN-PrabhatNeural"
    }

    init {
        // Initialize On-Device Android TextToSpeech as reliable zero-failure fallback
        try {
            nativeTts = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val result = nativeTts?.setLanguage(Locale("hi", "IN"))
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        nativeTts?.setLanguage(Locale.ENGLISH)
                    }
                    isNativeTtsReady = true
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Speaks cleaned text using Edge Neural synthesis, falling back to on-device TTS.
     */
    fun speak(
        text: String,
        voiceName: String = VOICE_HINDI_NATURAL,
        rate: String = "+0%",
        pitch: String = "+0Hz"
    ) {
        stop() // Immediate Barge-In

        val cleanText = sanitizeSpokenText(text)
        if (cleanText.isBlank()) return

        scope.launch {
            try {
                synthesizeAndPlay(cleanText, voiceName, rate, pitch)
            } catch (e: Exception) {
                e.printStackTrace()
                fallbackToNativeTts(cleanText)
            }
        }
    }

    /**
     * Cleans raw text into human-readable spoken words:
     * - Replaces code blocks with a brief spoken note.
     * - Removes Markdown symbols (*, **, #, `).
     * - Removes emojis, URLs, and bracket tags.
     */
    fun sanitizeSpokenText(raw: String): String {
        var clean = raw

        // 1. Replace multi-line code blocks
        clean = clean.replace(Regex("```[\\s\\S]*?```"), "Code generate ho gaya hai.")

        // 2. Remove inline code backticks
        clean = clean.replace(Regex("`([^`]+)`"), "$1")

        // 3. Remove URLs
        clean = clean.replace(Regex("https?://\\S+"), "link")

        // 4. Remove Markdown headings, bold, italic, bullets
        clean = clean.replace(Regex("[#*_~>]+"), " ")

        // 5. Remove emojis and non-standard symbols
        clean = clean.replace(Regex("[\\p{So}\\p{Cn}\\p{Cs}]"), "")

        // 6. Clean excess whitespace
        clean = clean.replace(Regex("\\s+"), " ").trim()

        return clean
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
                val configHeader = "X-Timestamp:$timestamp\r\nContent-Type:application/json; charset=utf-8\r\nPath:speech.config\r\n\r\n"
                val configBody = "{\"context\":{\"system\":{\"name\":\"SpeechSDK\",\"version\":\"1.26.0\",\"build\":\"JavaScript\",\"lang\":\"JavaScript\"}}}"
                webSocket.send("$configHeader$configBody")

                // 2. Send SSML request
                val ssml = """
                    <speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='en-US'>
                        <voice name='$voiceName'>
                            <prosody pitch='$pitch' rate='$rate'>
                                ${xmlEscape(text)}
                            </prosody>
                        </voice>
                    </speak>
                """.trimIndent()

                val ssmlHeader = "X-RequestId:$requestId\r\nContent-Type:application/ssml+xml\r\nX-Timestamp:$timestamp\r\nPath:ssml\r\n\r\n"
                webSocket.send("$ssmlHeader$ssml")
            }

            override fun onMessage(webSocket: WebSocket, textMessage: String) {
                if (textMessage.contains("Path:turn.end")) {
                    webSocket.close(1000, "Done")
                    val audioBytes = audioStream.toByteArray()
                    if (audioBytes.isNotEmpty()) {
                        playMp3Bytes(audioBytes)
                    } else {
                        fallbackToNativeTts(text)
                    }
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                val byteArray = bytes.toByteArray()
                val needle = "Path:audio\r\n".toByteArray(Charsets.UTF_8)
                val headerEnd = findSubArray(byteArray, needle)

                if (headerEnd != -1) {
                    val audioOffset = headerEnd + needle.size
                    if (audioOffset < byteArray.size) {
                        audioStream.write(byteArray, audioOffset, byteArray.size - audioOffset)
                    }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                t.printStackTrace()
                fallbackToNativeTts(text)
            }
        })
    }

    private fun fallbackToNativeTts(cleanText: String) {
        mainHandler.post {
            try {
                if (isNativeTtsReady && nativeTts != null) {
                    onPlaybackStarted()
                    nativeTts?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, "jarvis_fallback")
                    mainHandler.postDelayed({
                        onPlaybackCompleted()
                    }, (cleanText.length * 75L).coerceAtLeast(1500L))
                } else {
                    onError("Audio playback unavailable")
                    onPlaybackCompleted()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                onError("TTS Fallback failed: ${e.message}")
                onPlaybackCompleted()
            }
        }
    }

    private fun findSubArray(large: ByteArray, small: ByteArray): Int {
        for (i in 0..large.size - small.size) {
            var match = true
            for (j in small.indices) {
                if (large[i + j] != small[j]) {
                    match = false
                    break
                }
            }
            if (match) return i
        }
        return -1
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
                            fallbackToNativeTts(tempAudioFile.name)
                            true
                        }
                        prepareAsync()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    fallbackToNativeTts(tempAudioFile.name)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            fallbackToNativeTts("")
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
            nativeTts?.stop()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun isSpeaking(): Boolean {
        return mediaPlayer?.isPlaying == true || nativeTts?.isSpeaking == true
    }

    fun shutdown() {
        stop()
        try {
            nativeTts?.shutdown()
            nativeTts = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun xmlEscape(input: String): String {
        return input.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
