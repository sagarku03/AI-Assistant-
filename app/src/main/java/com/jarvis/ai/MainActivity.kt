package com.jarvis.ai

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.jarvis.ai.storage.PreferencesManager
import com.jarvis.ai.ui.components.OrbState
import com.jarvis.ai.ui.screens.CodeSandboxScreen
import com.jarvis.ai.ui.screens.DashboardScreen
import com.jarvis.ai.ui.theme.JarvisTheme
import com.jarvis.ai.ui.theme.OledBlack
import com.jarvis.ai.voice.JarvisVoiceCoordinator

enum class Screen {
    DASHBOARD,
    CODE_SANDBOX
}

class MainActivity : ComponentActivity() {

    private var coordinator: JarvisVoiceCoordinator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Request runtime permissions if not already granted
        val requiredPermissions = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requiredPermissions.add(Manifest.permission.RECORD_AUDIO)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requiredPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (requiredPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, requiredPermissions.toTypedArray(), 101)
        }

        setContent {
            JarvisTheme {
                val prefs = remember { PreferencesManager.getInstance(this@MainActivity) }
                var currentScreen by remember { mutableStateOf(Screen.DASHBOARD) }
                var orbState by remember { mutableStateOf(OrbState.IDLE) }
                var audioLevel by remember { mutableFloatStateOf(0.15f) }
                var statusLog by remember { mutableStateOf("Standby • Ready") }

                DisposableEffect(Unit) {
                    val voiceCoordinator = JarvisVoiceCoordinator(
                        context = this@MainActivity,
                        onOrbStateChanged = { newState -> orbState = newState },
                        onAudioAmplitude = { amp -> audioLevel = amp },
                        onStatusLog = { log -> statusLog = log }
                    ).also {
                        it.initialize(prefs.geminiApiKey)
                    }
                    coordinator = voiceCoordinator

                    onDispose {
                        voiceCoordinator.shutdown()
                        coordinator = null
                    }
                }

                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(OledBlack),
                    color = OledBlack
                ) {
                    Crossfade(targetState = currentScreen, label = "ScreenTransition") { screen ->
                        when (screen) {
                            Screen.DASHBOARD -> DashboardScreen(
                                coordinator = coordinator,
                                orbState = orbState,
                                audioLevel = audioLevel,
                                statusLog = statusLog,
                                onNavigateToCodeSandbox = { currentScreen = Screen.CODE_SANDBOX }
                            )
                            Screen.CODE_SANDBOX -> CodeSandboxScreen(
                                onNavigateBack = { currentScreen = Screen.DASHBOARD }
                            )
                        }
                    }
                }
            }
        }
    }
}
