package com.chainreaction.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val PlayerColors = listOf(
    Color(0xFFFF4D4D), Color(0xFF4DDB6B), Color(0xFF4D9BFF), Color(0xFFFFD84D),
    Color(0xFFE05CFF), Color(0xFF4DE8E8), Color(0xFFFF9A3C), Color(0xFFF2F2F2),
)
val PlayerNames = listOf("Red", "Green", "Blue", "Yellow", "Purple", "Cyan", "Orange", "White")

fun playerColor(p: Int): Color = if (p < 0) Color.Gray else PlayerColors[p % PlayerColors.size]

val Bg = Color(0xFF0F1222)
val Panel = Color(0xFF1A1E33)

@Composable
fun ChainTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFFFFB347),
            onPrimary = Color(0xFF1A1200),
            secondary = Color(0xFF7C8CFF),
            background = Bg,
            surface = Panel,
            surfaceVariant = Color(0xFF242945),
        ),
        content = content,
    )
}
