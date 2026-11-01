package com.jarvis.ai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.ai.ui.theme.JarvisBlue
import com.jarvis.ai.ui.theme.JarvisCyan
import com.jarvis.ai.ui.theme.OledBlack

enum class PillButtonStyle {
    PRIMARY_WHITE,
    GRADIENT_CYAN,
    NEON_LIME,
    GLASS_TRANSLUCENT
}

@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: PillButtonStyle = PillButtonStyle.PRIMARY_WHITE,
    icon: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    padding: PaddingValues = PaddingValues(horizontal = 24.dp, vertical = 14.dp)
) {
    val shape = RoundedCornerShape(28.dp)

    val backgroundModifier = when (style) {
        PillButtonStyle.PRIMARY_WHITE -> Modifier.background(Color.White, shape)
        PillButtonStyle.GRADIENT_CYAN -> Modifier.background(
            Brush.horizontalGradient(listOf(JarvisCyan, JarvisBlue)),
            shape
        )
        PillButtonStyle.NEON_LIME -> Modifier.background(
            Color(0xFF00E676),
            shape
        )
        PillButtonStyle.GLASS_TRANSLUCENT -> Modifier.glassmorphic(shape = shape)
    }

    val textColor = when (style) {
        PillButtonStyle.PRIMARY_WHITE -> OledBlack
        PillButtonStyle.GRADIENT_CYAN -> OledBlack
        PillButtonStyle.NEON_LIME -> OledBlack
        PillButtonStyle.GLASS_TRANSLUCENT -> Color.White
    }

    Box(
        modifier = modifier
            .clip(shape)
            .then(backgroundModifier)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = rememberRipple(bounded = true),
                enabled = enabled,
                onClick = onClick
            )
            .padding(padding),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                icon()
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = text,
                color = textColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
