package com.jarvis.ai.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.ai.ui.theme.JarvisCyan
import com.jarvis.ai.ui.theme.JarvisBlue
import com.jarvis.ai.ui.theme.NeonLime
import com.jarvis.ai.ui.theme.TextMuted
import com.jarvis.ai.ui.theme.TextPrimary
import com.jarvis.ai.ui.theme.TextSecondary

/**
 * Dual Interaction Agent Prompt Bar (Keyboard Typing + Hands-Free Voice Mic).
 * Replicated and elevated from Bypass IDE / Maria reference architecture with Apple VisionOS aesthetics.
 */
@Composable
fun AgentPromptBar(
    modifier: Modifier = Modifier,
    isListening: Boolean = false,
    onSendMessage: (String) -> Unit,
    onMicClick: () -> Unit,
    onChipSelected: (String) -> Unit = {}
) {
    var textInput by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    val suggestionChips = listOf(
        "Open YouTube",
        "Open WhatsApp",
        "Create HTML Website",
        "Open Settings",
        "Who are you?",
        "Tell me a joke"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // 1. Suggestion Chips Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            suggestionChips.forEach { chip ->
                Box(
                    modifier = Modifier
                        .glassmorphic(shape = RoundedCornerShape(16.dp))
                        .clickable {
                            onChipSelected(chip)
                            onSendMessage(chip)
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = chip,
                        fontSize = 12.sp,
                        color = JarvisCyan,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // 2. Main Glass Input Prompt Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .glassmorphic(
                    shape = RoundedCornerShape(28.dp),
                    backgroundColor = Color(0x330A1428),
                    borderTopLeft = Color(0x4400F0FF),
                    borderBottomRight = Color(0x1100F0FF)
                )
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Text Input Field
                OutlinedTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    placeholder = {
                        Text(
                            text = if (isListening) "Listening to your voice..." else "Ask JARVIS or type command...",
                            color = TextMuted,
                            fontSize = 14.sp
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 4.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = JarvisCyan
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (textInput.isNotBlank()) {
                                onSendMessage(textInput.trim())
                                textInput = ""
                                focusManager.clearFocus()
                            }
                        }
                    )
                )

                // Voice Mic Button or Send Button
                if (textInput.isNotBlank()) {
                    // Send Button
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .background(
                                brush = Brush.linearGradient(listOf(JarvisCyan, JarvisBlue)),
                                shape = CircleShape
                            )
                            .clickable {
                                onSendMessage(textInput.trim())
                                textInput = ""
                                focusManager.clearFocus()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Send,
                            contentDescription = "Send",
                            tint = Color.Black,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                } else {
                    // Mic Voice Pulse Button
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .background(
                                color = if (isListening) NeonLime else Color(0x2200F0FF),
                                shape = CircleShape
                            )
                            .clickable { onMicClick() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Mic,
                            contentDescription = "Mic",
                            tint = if (isListening) Color.Black else JarvisCyan,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}
