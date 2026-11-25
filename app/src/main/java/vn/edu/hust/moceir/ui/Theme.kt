package vn.edu.hust.moceir.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF156C63),
    onPrimary = Color.White,
    background = Color(0xFFF5FAF8),
    surface = Color(0xFFF5FAF8),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF80D5C8),
    background = Color(0xFF101C19),
    surface = Color(0xFF101C19),
)

@Composable
fun MoceTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
