package com.jarvis.ai.storage

import android.content.Context
import android.content.SharedPreferences

/**
 * Permanent SharedPreferences Manager for Project JARVIS.
 * Retains Gemini API key, selected voice model, wake words, and chat settings across app restarts.
 */
class PreferencesManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "jarvis_preferences"
        private const val KEY_GEMINI_API_KEY = "gemini_api_key"
        private const val KEY_WAKE_WORD_ENABLED = "wake_word_enabled"
        private const val KEY_SELECTED_VOICE = "selected_voice"
        private const val KEY_VOICE_RATE = "voice_rate"
        private const val KEY_VOICE_PITCH = "voice_pitch"
        private const val KEY_PROJECTS_DIR = "projects_dir"
        private const val KEY_AUTONOMOUS_ACTIONS = "autonomous_actions"

        @Volatile
        private var instance: PreferencesManager? = null

        fun getInstance(context: Context): PreferencesManager {
            return instance ?: synchronized(this) {
                instance ?: PreferencesManager(context.applicationContext).also { instance = it }
            }
        }
    }

    var geminiApiKey: String
        get() = prefs.getString(KEY_GEMINI_API_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_GEMINI_API_KEY, value.trim()).apply()

    var isWakeWordEnabled: Boolean
        get() = prefs.getBoolean(KEY_WAKE_WORD_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_WAKE_WORD_ENABLED, value).apply()

    var selectedVoice: String
        get() = prefs.getString(KEY_SELECTED_VOICE, "hi-IN-MadhurNeural") ?: "hi-IN-MadhurNeural"
        set(value) = prefs.edit().putString(KEY_SELECTED_VOICE, value).apply()

    var voiceRate: String
        get() = prefs.getString(KEY_VOICE_RATE, "+0%") ?: "+0%"
        set(value) = prefs.edit().putString(KEY_VOICE_RATE, value).apply()

    var voicePitch: String
        get() = prefs.getString(KEY_VOICE_PITCH, "+0Hz") ?: "+0Hz"
        set(value) = prefs.edit().putString(KEY_VOICE_PITCH, value).apply()

    var projectsDir: String
        get() = prefs.getString(KEY_PROJECTS_DIR, "/sdcard/JarvisProjects") ?: "/sdcard/JarvisProjects"
        set(value) = prefs.edit().putString(KEY_PROJECTS_DIR, value).apply()

    var isAutonomousActionsEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTONOMOUS_ACTIONS, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTONOMOUS_ACTIONS, value).apply()

    fun clearAll() {
        prefs.edit().clear().apply()
    }
}
