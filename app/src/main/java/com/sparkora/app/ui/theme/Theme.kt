package com.sparkora.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Sparkora brand — violet primary with a warm spark accent.
private val Violet = Color(0xFF6D28D9)
private val VioletDark = Color(0xFF241468)
private val VioletContainer = Color(0xFFE9DDFF)
private val Amber = Color(0xFFF59E0B)
private val Teal = Color(0xFF0D9488)

private val LightColors = lightColorScheme(
    primary = Violet,
    onPrimary = Color.White,
    primaryContainer = VioletContainer,
    onPrimaryContainer = Color(0xFF22005D),
    secondary = Teal,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCCF3EC),
    onSecondaryContainer = Color(0xFF00201B),
    tertiary = Amber,
    onTertiary = Color.White,
    background = Color(0xFFFCF8FF),
    surface = Color(0xFFFCF8FF),
    surfaceVariant = Color(0xFFE9E0F0),
    onSurfaceVariant = Color(0xFF49454E),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFCFBCFF),
    onPrimary = Color(0xFF381E72),
    primaryContainer = Color(0xFF4F378A),
    onPrimaryContainer = VioletContainer,
    secondary = Color(0xFF7FD8CA),
    onSecondary = Color(0xFF00382F),
    secondaryContainer = Color(0xFF005045),
    onSecondaryContainer = Color(0xFFCCF3EC),
    tertiary = Color(0xFFFFC46B),
    onTertiary = Color(0xFF442B00),
    background = Color(0xFF141218),
    surface = Color(0xFF141218),
)

@Composable
fun SparkoraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
