package com.example.monisom.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF1B6B5A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFA7F2DC),
    onPrimaryContainer = Color(0xFF00201A),
    secondary = Color(0xFF4C635C),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCEE9DE),
    onSecondaryContainer = Color(0xFF08201A),
    tertiary = Color(0xFF406277),
    background = Color(0xFFF5FBF7),
    surface = Color(0xFFF5FBF7),
    surfaceVariant = Color(0xFFDBE5DF),
    error = Color(0xFFBA1A1A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8CD5C0),
    onPrimary = Color(0xFF00382E),
    primaryContainer = Color(0xFF005142),
    onPrimaryContainer = Color(0xFFA7F2DC),
    secondary = Color(0xFFB2CCC2),
    onSecondary = Color(0xFF1D352E),
    secondaryContainer = Color(0xFF344B44),
    onSecondaryContainer = Color(0xFFCEE9DE),
    tertiary = Color(0xFFAECBDC),
    background = Color(0xFF0F1512),
    surface = Color(0xFF0F1512),
    surfaceVariant = Color(0xFF3F4945),
    error = Color(0xFFFFB4AB),
)

@Composable
fun MoniSomTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
