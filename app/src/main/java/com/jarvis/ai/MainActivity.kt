package com.jarvis.ai

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

        setContent {
            JarvisTheme {
                var currentScreen by remember { mutableStateOf(Screen.DASHBOARD) }
                var orbState by remember { mutableStateOf(OrbState.IDLE) }
                var audioLevel by remember { mutableFloatStateOf(0.15f) }
                var statusLog by remember { mutableStateOf("Standby • Listening for 'Hey Jarvis'") }

                DisposableEffect(Unit) {
                    val voiceCoordinator = JarvisVoiceCoordinator(
                        context = this@MainActivity,
                        onOrbStateChanged = { newState -> orbState = newState },
                        onAudioAmplitude = { amp -> audioLevel = amp },
                        onStatusLog = { log -> statusLog = log }
                    ).also {
                        it.initialize()
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
