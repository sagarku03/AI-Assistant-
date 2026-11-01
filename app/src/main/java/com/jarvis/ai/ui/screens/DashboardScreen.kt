package com.jarvis.ai.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import com.jarvis.ai.service.FloatingArcOrbService
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
import com.jarvis.ai.voice.JarvisVoiceCoordinator

@Composable
fun DashboardScreen(
    coordinator: JarvisVoiceCoordinator? = null,
    orbState: OrbState = OrbState.IDLE,
    audioLevel: Float = 0.15f,
    statusLog: String = "Standby • Listening for 'Hey Jarvis'",
    onNavigateToCodeSandbox: () -> Unit = {}
) {
    val context = LocalContext.current
    var isOrbServiceRunning by remember { mutableStateOf(false) }
    var apiKeyInput by remember { mutableStateOf("") }
    var isKeySaved by remember { mutableStateOf(false) }

    // Check system overlay permission state
    val hasOverlayPermission = remember(context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OledBlack)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 1. Top Bar Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
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

            // Connection Status Pill
            Box(
                modifier = Modifier
                    .glassmorphic(shape = RoundedCornerShape(16.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
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
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeonLime
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // 2. Interactive Arc Reactor Orb Centerpiece (Tap to Talk)
        Box(
            modifier = Modifier.clickable {
                coordinator?.onWakeWordTriggered()
            },
            contentAlignment = Alignment.Center
        ) {
            ArcReactorOrb(
                size = 190.dp,
                state = orbState,
                audioLevel = audioLevel
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        // State indicator label
        Text(
            text = when (orbState) {
                OrbState.IDLE -> "Standby • Say 'Hey Jarvis' or Tap Orb"
                OrbState.LISTENING -> "Listening to Boss..."
                OrbState.THINKING -> "Thinking with Gemini 2.0..."
                OrbState.EXECUTING -> "Executing Action..."
            },
            fontSize = 14.sp,
            color = JarvisCyan,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Live Log Stream
        Text(
            text = statusLog,
            fontSize = 12.sp,
            color = TextSecondary,
            fontFamily = FontFamily.Monospace,
            maxLines = 2
        )

        Spacer(modifier = Modifier.height(24.dp))

        // 3. Free Gemini 2.0 Flash API Key Card (Optional for Unlimited Cloud Power)
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = 16.dp
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Key,
                        contentDescription = null,
                        tint = JarvisCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Gemini 2.0 Flash Free Key",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Free tier: 1,500 requests/day from Google AI Studio",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = apiKeyInput,
                        onValueChange = { apiKeyInput = it },
                        placeholder = { Text("Paste free API key (Optional)", color = TextMuted, fontSize = 12.sp) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = JarvisCyan,
                            unfocusedBorderColor = Color(0x33FFFFFF),
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(14.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    PillButton(
                        text = if (isKeySaved) "Saved" else "Apply",
                        style = PillButtonStyle.GRADIENT_CYAN,
                        onClick = {
                            if (apiKeyInput.isNotBlank()) {
                                coordinator?.initialize(apiKeyInput.trim())
                                isKeySaved = true
                            }
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 4. Quick Action Button: Floating Arc Orb Launcher
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = 18.dp
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Floating Arc Orb",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Draggable widget active over all Android apps",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

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

        Spacer(modifier = Modifier.height(16.dp))

        // 5. System Permissions Checklist (Apple iOS Style)
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = 18.dp
        ) {
            Column {
                Text(
                    text = "System Privileges",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(14.dp))

                PermissionRow(
                    icon = Icons.Rounded.Layers,
                    title = "Draw Over Other Apps",
                    subtitle = "Required for Floating Arc Orb widget",
                    isGranted = hasOverlayPermission,
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

                Spacer(modifier = Modifier.height(12.dp))

                PermissionRow(
                    icon = Icons.Rounded.TouchApp,
                    title = "Accessibility Control",
                    subtitle = "Allows reading screen & tapping buttons",
                    isGranted = false,
                    onClick = {
                        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        context.startActivity(intent)
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                PermissionRow(
                    icon = Icons.Rounded.Mic,
                    title = "Audio & Wake-Word",
                    subtitle = "Listens for 'Hey Jarvis', 'Suno Jarvis'",
                    isGranted = true,
                    onClick = {}
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 6. Mobile Code & Website Sandbox Navigation
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = 18.dp
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Mobile Code Studio",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Instant live preview of code & websites generated by JARVIS",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }

                PillButton(
                    text = "Open",
                    style = PillButtonStyle.NEON_LIME,
                    onClick = onNavigateToCodeSandbox
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
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
                    .size(40.dp)
                    .glassmorphic(shape = RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isGranted) NeonLime else JarvisCyan,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    text = title,
                    fontSize = 14.sp,
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
                modifier = Modifier.size(20.dp)
            )
        } else {
            PillButton(
                text = "Grant",
                style = PillButtonStyle.GLASS_TRANSLUCENT,
                padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                onClick = onClick
            )
        }
    }
}
