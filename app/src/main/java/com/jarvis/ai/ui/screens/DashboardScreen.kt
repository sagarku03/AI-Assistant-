package com.jarvis.ai.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.jarvis.ai.service.FloatingArcOrbService
import com.jarvis.ai.storage.PreferencesManager
import com.jarvis.ai.ui.components.AgentPromptBar
import com.jarvis.ai.ui.components.ArcReactorOrb
import com.jarvis.ai.ui.components.GlassCard
import com.jarvis.ai.ui.components.OrbState
import com.jarvis.ai.ui.components.PillButton
import com.jarvis.ai.ui.components.PillButtonStyle
import com.jarvis.ai.ui.components.glassmorphic
import com.jarvis.ai.ui.theme.JarvisCyan
import com.jarvis.ai.ui.theme.NeonLime
import com.jarvis.ai.ui.theme.OledBlack
import com.jarvis.ai.ui.theme.TextMuted
import com.jarvis.ai.ui.theme.TextPrimary
import com.jarvis.ai.ui.theme.TextSecondary
import com.jarvis.ai.voice.ChatMessage
import com.jarvis.ai.voice.JarvisVoiceCoordinator

@Composable
fun DashboardScreen(
    coordinator: JarvisVoiceCoordinator? = null,
    orbState: OrbState = OrbState.IDLE,
    audioLevel: Float = 0.15f,
    statusLog: String = "Standby • Ready",
    onNavigateToCodeSandbox: () -> Unit = {}
) {
    val context = LocalContext.current
    val prefs = remember { PreferencesManager.getInstance(context) }

    var isOrbServiceRunning by remember { mutableStateOf(false) }
    var apiKeyInput by remember { mutableStateOf(prefs.geminiApiKey) }
    var isKeySaved by remember { mutableStateOf(prefs.geminiApiKey.isNotBlank()) }
    var showApiKeyCard by remember { mutableStateOf(prefs.geminiApiKey.isBlank()) }

    // Runtime Permission for Microphone
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMicPermission = granted
        if (granted) {
            Toast.makeText(context, "Microphone access granted for JARVIS", Toast.LENGTH_SHORT).show()
            coordinator?.initialize(prefs.geminiApiKey)
        } else {
            Toast.makeText(context, "Microphone permission required for voice interaction", Toast.LENGTH_LONG).show()
        }
    }

    // Check system overlay permission state
    val hasOverlayPermission = remember(context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else true
    }

    val listState = rememberLazyListState()

    // Scroll to latest message automatically
    LaunchedEffect(coordinator?.messages?.size) {
        val count = coordinator?.messages?.size ?: 0
        if (count > 0) {
            listState.animateScrollToItem(count + 4)
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        containerColor = OledBlack,
        bottomBar = {
            AgentPromptBar(
                isListening = orbState == OrbState.LISTENING,
                onSendMessage = { text ->
                    coordinator?.processUserText(text)
                },
                onMicClick = {
                    if (!hasMicPermission) {
                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    } else {
                        coordinator?.onWakeWordTriggered()
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. Header Row
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "JARVIS",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "Autonomous System Agent",
                            fontSize = 13.sp,
                            color = TextSecondary
                        )
                    }

                    // Connection Status & Key Badge
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .glassmorphic(shape = RoundedCornerShape(16.dp))
                                .clickable { showApiKeyCard = !showApiKeyCard }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.Key,
                                    contentDescription = null,
                                    tint = if (isKeySaved) NeonLime else JarvisCyan,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isKeySaved) "KEY ACTIVE" else "SET KEY",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isKeySaved) NeonLime else JarvisCyan
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Box(
                            modifier = Modifier
                                .glassmorphic(shape = RoundedCornerShape(16.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(NeonLime, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "ONLINE",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = NeonLime
                                )
                            }
                        }
                    }
                }
            }

            // 2. Expandable Persistent API Key Card
            item {
                AnimatedVisibility(visible = showApiKeyCard) {
                    GlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        contentPadding = 14.dp
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.Key,
                                    contentDescription = null,
                                    tint = JarvisCyan,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Google Gemini 2.0 Flash API Key",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Free tier: 1,500 req/day from Google AI Studio. Saved permanently in SharedPreferences.",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = apiKeyInput,
                                    onValueChange = { apiKeyInput = it },
                                    placeholder = {
                                        Text("Paste free API key (Permanent)", color = TextMuted, fontSize = 12.sp)
                                    },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = JarvisCyan,
                                        unfocusedBorderColor = Color(0x33FFFFFF),
                                        focusedTextColor = TextPrimary,
                                        unfocusedTextColor = TextPrimary
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                PillButton(
                                    text = if (isKeySaved) "Save" else "Save",
                                    style = PillButtonStyle.GRADIENT_CYAN,
                                    onClick = {
                                        if (apiKeyInput.isNotBlank()) {
                                            prefs.geminiApiKey = apiKeyInput.trim()
                                            coordinator?.initialize(apiKeyInput.trim())
                                            isKeySaved = true
                                            showApiKeyCard = false
                                            Toast.makeText(context, "API Key permanently saved on device!", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // 3. Arc Reactor Orb Centerpiece
            item {
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier.clickable {
                        if (!hasMicPermission) {
                            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        } else {
                            coordinator?.onWakeWordTriggered()
                        }
                    },
                    contentAlignment = Alignment.Center
                ) {
                    ArcReactorOrb(
                        size = 170.dp,
                        state = orbState,
                        audioLevel = audioLevel
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))

                // State indicator label
                Text(
                    text = when (orbState) {
                        OrbState.IDLE -> "Standby • Tap Orb or Type Below"
                        OrbState.LISTENING -> "Listening to your voice..."
                        OrbState.THINKING -> "Thinking with Gemini 2.0..."
                        OrbState.EXECUTING -> "Executing Autonomous Action..."
                    },
                    fontSize = 13.sp,
                    color = JarvisCyan,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Live Log Stream
                Text(
                    text = statusLog,
                    fontSize = 11.sp,
                    color = TextSecondary,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 2
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // 4. Quick Actions Row (Floating Arc Orb + Code Studio)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Floating Overlay Button
                    GlassCard(
                        modifier = Modifier.weight(1f),
                        contentPadding = 12.dp
                    ) {
                        Column {
                            Text(
                                text = "Floating Orb",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Over other apps",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            PillButton(
                                text = if (isOrbServiceRunning) "Stop" else "Launch",
                                style = if (isOrbServiceRunning) PillButtonStyle.GLASS_TRANSLUCENT else PillButtonStyle.GRADIENT_CYAN,
                                onClick = {
                                    if (!hasOverlayPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                        val intent = Intent(
                                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                            Uri.parse("package:${context.packageName}")
                                        )
                                        context.startActivity(intent)
                                    } else {
                                        val serviceIntent = Intent(context, FloatingArcOrbService::class.java)
                                        if (isOrbServiceRunning) {
                                            context.stopService(serviceIntent)
                                            isOrbServiceRunning = false
                                        } else {
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                                context.startForegroundService(serviceIntent)
                                            } else {
                                                context.startService(serviceIntent)
                                            }
                                            isOrbServiceRunning = true
                                        }
                                    }
                                }
                            )
                        }
                    }

                    // Code Sandbox Studio Button
                    GlassCard(
                        modifier = Modifier.weight(1f),
                        contentPadding = 12.dp
                    ) {
                        Column {
                            Text(
                                text = "Code Studio",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Live preview",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            PillButton(
                                text = "Open",
                                style = PillButtonStyle.NEON_LIME,
                                onClick = onNavigateToCodeSandbox
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // 5. System Privileges Checklist (Only if permission missing)
            if (!hasMicPermission || !hasOverlayPermission) {
                item {
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = 14.dp
                    ) {
                        Column {
                            Text(
                                text = "System Permissions Required",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            if (!hasMicPermission) {
                                PermissionRow(
                                    icon = Icons.Rounded.Mic,
                                    title = "Microphone Access",
                                    subtitle = "Required to hear 'Hey Jarvis' & voice commands",
                                    isGranted = false,
                                    onClick = {
                                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }

                            if (!hasOverlayPermission) {
                                PermissionRow(
                                    icon = Icons.Rounded.Layers,
                                    title = "Draw Over Other Apps",
                                    subtitle = "Required for Floating Arc Orb widget",
                                    isGranted = false,
                                    onClick = {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                            val intent = Intent(
                                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                Uri.parse("package:${context.packageName}")
                                            )
                                            context.startActivity(intent)
                                        }
                                    }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            // 6. Conversational Chat Stream Header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "CONVERSATION",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(1.dp)
                            .background(Color(0x22FFFFFF))
                    )
                }
            }

            // 7. Dynamic Chat Messages List
            val messages = coordinator?.messages ?: emptyList<ChatMessage>()
            items(messages, key = { it.id }) { message ->
                ChatBubble(message = message)
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Bottom Spacer for padding above input bar
            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun ChatBubble(message: ChatMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(if (message.isUser) 0.8f else 0.9f)
                .glassmorphic(
                    shape = RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (message.isUser) 16.dp else 4.dp,
                        bottomEnd = if (message.isUser) 4.dp else 16.dp
                    ),
                    backgroundColor = if (message.isUser) Color(0x3300F0FF) else Color(0x220A1428),
                    borderColor = if (message.isUser) Color(0x5500F0FF) else Color(0x33FFFFFF)
                )
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Column {
                Text(
                    text = if (message.isUser) "Boss" else "JARVIS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (message.isUser) JarvisCyan else NeonLime
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = message.text,
                    fontSize = 14.sp,
                    color = TextPrimary,
                    lineHeight = 20.sp
                )
            }
        }
    }
}

@Composable
private fun PermissionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isGranted: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .glassmorphic(shape = RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isGranted) NeonLime else JarvisCyan,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column {
                Text(
                    text = title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary
                )
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = TextMuted
                )
            }
        }

        if (isGranted) {
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = "Granted",
                tint = NeonLime,
                modifier = Modifier.size(18.dp)
            )
        } else {
            PillButton(
                text = "Grant",
                style = PillButtonStyle.GRADIENT_CYAN,
                padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                onClick = onClick
            )
        }
    }
}
