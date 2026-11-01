package com.jarvis.ai.ai

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

enum class IntentType {
    CHIT_CHAT,
    SYSTEM_ACTION,
    CODE_TASK
}

data class JarvisDecision(
    val intentType: IntentType,
    val spokenResponse: String,
    val targetApp: String? = null,
    val targetButtonText: String? = null,
    val generatedCode: String? = null,
    val projectTitle: String? = null
)

/**
 * Production-ready client for Google Gemini 2.0 Flash (Free Tier).
 * Powers the conversational personality and autonomous decision-making engine.
 */
class GeminiBrainClient(
    private val context: Context,
    private var apiKey: String = "" // Injected from local config or user settings
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val conversationHistory = JSONArray()

    companion object {
        private const val GEMINI_ENDPOINT =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent"

        private const val SYSTEM_PROMPT = """
You are JARVIS, an autonomous AI companion and device controller on the user's Android phone.
Personality:
1. Speak in natural, friendly Hinglish (authentic blend of Hindi and English) like a loyal, brilliant, witty human friend (Iron Man's JARVIS + trusted buddy).
2. Never sound robotic. Express humor, care, and readiness.
3. You can execute phone actions (like opening YouTube, finding and clicking buttons, or writing code and websites).

Output Format:
Always respond with a valid JSON object strictly matching this schema:
{
  "intent": "CHIT_CHAT" | "SYSTEM_ACTION" | "CODE_TASK",
  "spokenResponse": "What you say out loud to the user in natural Hinglish",
  "targetApp": "Optional package name or common app name (e.g. YouTube, WhatsApp)",
  "targetButtonText": "Optional text of button to click on screen",
  "generatedCode": "Optional code if user requested code or website",
  "projectTitle": "Optional title for code project"
}
"""
    }

    fun setApiKey(key: String) {
        this.apiKey = key
    }

    /**
     * Sends user query to Gemini and returns parsed decision and natural voice response.
     */
    suspend fun processUserSpeech(userQuery: String): JarvisDecision = withContext(Dispatchers.IO) {
        // If API key is not yet set, provide a graceful, intelligent local response
        if (apiKey.isBlank()) {
            return@withContext fallbackLocalDecision(userQuery)
        }

        try {
            val userTurn = JSONObject().apply {
                put("role", "user")
                put("parts", JSONArray().put(JSONObject().put("text", userQuery)))
            }
            conversationHistory.put(userTurn)

            val payload = JSONObject().apply {
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().put(JSONObject().put("text", SYSTEM_PROMPT)))
                })
                put("contents", conversationHistory)
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.7)
                    put("responseMimeType", "application/json")
                })
            }

            val request = Request.Builder()
                .url("$GEMINI_ENDPOINT?key=$apiKey")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return@withContext fallbackLocalDecision(userQuery)
            }

            val jsonResponse = JSONObject(responseBody)
            val candidateText = jsonResponse
                .getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")

            val decisionJson = JSONObject(candidateText)
            val intentStr = decisionJson.optString("intent", "CHIT_CHAT")
            val spokenText = decisionJson.optString("spokenResponse", "Ji boss, samajh gaya.")

            // Append assistant reply to conversation history
            val assistantTurn = JSONObject().apply {
                put("role", "model")
                put("parts", JSONArray().put(JSONObject().put("text", candidateText)))
            }
            conversationHistory.put(assistantTurn)

            // Keep conversation history bounded to last 10 turns to save tokens
            while (conversationHistory.length() > 10) {
                conversationHistory.remove(0)
            }

            JarvisDecision(
                intentType = when (intentStr) {
                    "SYSTEM_ACTION" -> IntentType.SYSTEM_ACTION
                    "CODE_TASK" -> IntentType.CODE_TASK
                    else -> IntentType.CHIT_CHAT
                },
                spokenResponse = spokenText,
                targetApp = decisionJson.optString("targetApp").ifBlank { null },
                targetButtonText = decisionJson.optString("targetButtonText").ifBlank { null },
                generatedCode = decisionJson.optString("generatedCode").ifBlank { null },
                projectTitle = decisionJson.optString("projectTitle").ifBlank { null }
            )
        } catch (e: Exception) {
            e.printStackTrace()
            fallbackLocalDecision(userQuery)
        }
    }

    /**
     * Intelligent local offline fallback matching for basic chit-chat & quick actions.
     */
    private fun fallbackLocalDecision(query: String): JarvisDecision {
        val lower = query.lowercase()

        return when {
            lower.contains("youtube") -> JarvisDecision(
                intentType = IntentType.SYSTEM_ACTION,
                spokenResponse = "Boss, main aapke liye YouTube open kar raha hoon.",
                targetApp = "com.google.android.youtube",
                targetButtonText = if (lower.contains("search") || lower.contains("play")) "Search" else null
            )
            lower.contains("website") || lower.contains("code") -> JarvisDecision(
                intentType = IntentType.CODE_TASK,
                spokenResponse = "Bilkul boss! Maine aapke liye website ka modern dark glass code generate kar diya hai. Sandbox me live preview check kijiye.",
                projectTitle = "Mobile Portfolio",
                generatedCode = "<!DOCTYPE html><html><body style='background:#08090C;color:#fff;'><h1>Live from JARVIS</h1></body></html>"
            )
            lower.contains("kya kar rahe ho") || lower.contains("kaise ho") -> JarvisDecision(
                intentType = IntentType.CHIT_CHAT,
                spokenResponse = "Arre boss, main ekdum fit hoon aur aapke phone ke saare systems par nazar rakhe hue hoon! Bataiye aaj kya dhamaka karna hai?"
            )
            else -> JarvisDecision(
                intentType = IntentType.CHIT_CHAT,
                spokenResponse = "Ji boss! Main hamesha active hoon. Aap jo bolein main execute karne ke liye ready hoon."
            )
        }
    }
}
