package com.jarvis.ai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jarvis.ai.ui.theme.GlassBackground
import com.jarvis.ai.ui.theme.GlassBorderBottomRight
import com.jarvis.ai.ui.theme.GlassBorderTopLeft

/**
 * Production-ready Glassmorphic Modifier implementing Apple iOS/VisionOS
 * frosted glass texture with specular gradient borders.
 */
fun Modifier.glassmorphic(
    shape: Shape = RoundedCornerShape(24.dp),
    backgroundColor: Color = GlassBackground,
    borderWidth: Dp = 1.dp,
    borderTopLeft: Color = GlassBorderTopLeft,
    borderBottomRight: Color = GlassBorderBottomRight
): Modifier = this
    .clip(shape)
    .background(backgroundColor, shape)
    .border(
        width = borderWidth,
        brush = Brush.linearGradient(
            colors = listOf(borderTopLeft, borderBottomRight)
        ),
        shape = shape
    )
