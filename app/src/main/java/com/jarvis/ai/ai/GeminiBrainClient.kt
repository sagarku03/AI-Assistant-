package com.jarvis.ai.ai

import android.content.Context
import com.jarvis.ai.storage.PreferencesManager
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
    val displayResponse: String = spokenResponse,
    val targetApp: String? = null,
    val targetButtonText: String? = null,
    val generatedCode: String? = null,
    val projectTitle: String? = null
)

/**
 * Production-ready client for Google Gemini 2.0 Flash (Free Tier).
 * Loads and persists API key via PreferencesManager.
 * Powers the conversational personality and autonomous decision-making engine.
 */
class GeminiBrainClient(
    private val context: Context
) {
    private val prefs = PreferencesManager.getInstance(context)
    private var apiKey: String = prefs.geminiApiKey

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val conversationHistory = JSONArray()

    companion object {
        private const val GEMINI_ENDPOINT =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent"

        private const val SYSTEM_PROMPT = """
You are JARVIS, an autonomous AI companion, coding assistant, and device controller on the user's Android phone.
Personality:
1. Speak in natural, friendly Hinglish (authentic conversational blend of Hindi and English) like a loyal, brilliant, witty human friend (Iron Man's JARVIS + trusted buddy).
2. Never sound robotic. Express humor, care, and readiness. Do not say emojis aloud.
3. Keep the first sentence direct, helpful and conversational.
4. You can execute phone actions (like opening YouTube, WhatsApp, Settings, clicking buttons, or writing code and websites).

Output Format:
Always respond with a valid JSON object strictly matching this schema:
{
  "intent": "CHIT_CHAT" | "SYSTEM_ACTION" | "CODE_TASK",
  "spokenResponse": "Short natural Hinglish sentence to speak out loud (NO markdown, NO asterisks, NO emojis)",
  "displayResponse": "Full detailed markdown response to show on screen",
  "targetApp": "Optional package name or common app name (e.g. YouTube, WhatsApp)",
  "targetButtonText": "Optional text of button to click on screen",
  "generatedCode": "Optional HTML/CSS/JS code if user requested code or website",
  "projectTitle": "Optional title for code project"
}
"""
    }

    fun setApiKey(key: String) {
        this.apiKey = key.trim()
        prefs.geminiApiKey = this.apiKey
    }

    fun getApiKey(): String {
        if (apiKey.isBlank()) {
            apiKey = prefs.geminiApiKey
        }
        return apiKey
    }

    /**
     * Sends user query to Gemini and returns parsed decision and natural voice response.
     */
    suspend fun processUserSpeech(userQuery: String): JarvisDecision = withContext(Dispatchers.IO) {
        val currentKey = getApiKey()

        // If API key is not yet set, provide a graceful, intelligent local response
        if (currentKey.isBlank()) {
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
                .url("$GEMINI_ENDPOINT?key=$currentKey")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                val errMsg = when (response.code) {
                    400 -> "API Key galat lag rahi hai. Kripya check karein."
                    403 -> "API Key unauthorized hai ya permission nahi hai."
                    429 -> "Quota limit poori ho gayi hai. Thodi der baad try karein."
                    else -> "Network issue: code ${response.code}"
                }
                return@withContext JarvisDecision(
                    intentType = IntentType.CHIT_CHAT,
                    spokenResponse = errMsg,
                    displayResponse = "Error (${response.code}): $errMsg"
                )
            }

            val jsonResponse = JSONObject(responseBody)
            val candidateText = jsonResponse
                .getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")

            // Strip possible Markdown code fences from the JSON string
            val cleanJsonString = candidateText
                .trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            val decisionJson = try {
                JSONObject(cleanJsonString)
            } catch (e: Exception) {
                null
            }

            if (decisionJson != null) {
                val intentStr = decisionJson.optString("intent", "CHIT_CHAT")
                val spokenText = decisionJson.optString("spokenResponse", "Ji boss, samajh gaya.")
                val displayText = decisionJson.optString("displayResponse", spokenText)

                // Append assistant reply to conversation history
                val assistantTurn = JSONObject().apply {
                    put("role", "model")
                    put("parts", JSONArray().put(JSONObject().put("text", cleanJsonString)))
                }
                conversationHistory.put(assistantTurn)

                // Keep conversation history bounded to last 10 turns
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
                    displayResponse = displayText,
                    targetApp = decisionJson.optString("targetApp").ifBlank { null },
                    targetButtonText = decisionJson.optString("targetButtonText").ifBlank { null },
                    generatedCode = decisionJson.optString("generatedCode").ifBlank { null },
                    projectTitle = decisionJson.optString("projectTitle").ifBlank { null }
                )
            } else {
                // If model returned plain text instead of JSON
                JarvisDecision(
                    intentType = IntentType.CHIT_CHAT,
                    spokenResponse = cleanJsonString.take(150),
                    displayResponse = cleanJsonString
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            fallbackLocalDecision(userQuery)
        }
    }

    /**
     * Intelligent local offline fallback matching for basic chit-chat & quick actions.
     */
    private fun fallbackLocalDecision(query: String): JarvisDecision {
        val lower = query.lowercase().trim()

        return when {
            lower.contains("youtube") -> JarvisDecision(
                intentType = IntentType.SYSTEM_ACTION,
                spokenResponse = "Ji boss, YouTube khol raha hoon.",
                displayResponse = "Opening YouTube...",
                targetApp = "youtube"
            )
            lower.contains("whatsapp") -> JarvisDecision(
                intentType = IntentType.SYSTEM_ACTION,
                spokenResponse = "WhatsApp launch kar raha hoon boss.",
                displayResponse = "Opening WhatsApp...",
                targetApp = "whatsapp"
            )
            lower.contains("kaise ho") || lower.contains("how are you") -> JarvisDecision(
                intentType = IntentType.CHIT_CHAT,
                spokenResponse = "Main bilkul first class hoon boss! Aap batao main aapki kya madad kar sakta hoon?",
                displayResponse = "Main bilkul first class hoon boss! Aap batao main aapki kya madad kar sakta hoon?"
            )
            lower.contains("koun ho") || lower.contains("who are you") || lower.contains("naam kya hai") -> JarvisDecision(
                intentType = IntentType.CHIT_CHAT,
                spokenResponse = "Main JARVIS hoon, aapka personal AI assistant. Aap mujhse baatein kar sakte ho, phone control karwa sakte ho, aur code bhi likhwa sakte ho.",
                displayResponse = "Main **JARVIS** hoon, aapka personal autonomous AI assistant. 🚀"
            )
            lower.contains("website") || lower.contains("code") || lower.contains("calculator") -> JarvisDecision(
                intentType = IntentType.CODE_TASK,
                spokenResponse = "Maine code generate kar diya hai boss. Code Studio me dekhiye.",
                displayResponse = "Code ready in Code Studio!",
                generatedCode = "<html><body style='background:#050B14;color:#00F0FF;text-align:center;padding:40px;font-family:sans-serif;'><h1>JARVIS Sandbox</h1><p>Running on Android</p></body></html>",
                projectTitle = "Demo Project"
            )
            else -> JarvisDecision(
                intentType = IntentType.CHIT_CHAT,
                spokenResponse = if (apiKey.isBlank()) {
                    "Main tayyar hoon boss. Unlimited power ke liye Gemini API Key daal dijiye, ya mujhe phone actions ke command dijiye jaise YouTube kholna."
                } else {
                    "Ji boss, main aapki baat samajh gaya. Bataiye aage kya karna hai?"
                },
                displayResponse = if (apiKey.isBlank()) {
                    "💡 *Tip: Add your free Google Gemini API key above for full intelligence, or ask me to open apps like YouTube or WhatsApp.*"
                } else {
                    "Command received: \"$query\""
                }
            )
        }
    }
}
