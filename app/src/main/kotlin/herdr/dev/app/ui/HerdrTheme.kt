package herdr.dev.app.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

import androidx.compose.ui.graphics.Color

// Claude Design System (CDS) color tokens extracted directly from Claude Desktop
// Light: warm paper #fcfcfb, surface-0 #f9f9f7, surface-1 #fcfcfb, text #0b0b0b
// Dark: #151515, surface-0 #0b0b0b, surface-1 #151515, surface-2 #1a1a19, text #fcfcfb
// Brand clay: #d97757 / #c6613f
val ClaudeClay = Color(0xFFD97757)
val ClaudeClayEmphasized = Color(0xFFC6613F)
val ClaudePaperBg = Color(0xFFFCFCFB)
val ClaudePaperSurface = Color(0xFFF9F9F7)
val ClaudePaperBorder = Color(0xFFE7E6E1)
val ClaudeDarkBg = Color(0xFF151515)
val ClaudeDarkSurface0 = Color(0xFF0B0B0B)
val ClaudeDarkSurface2 = Color(0xFF1A1A19)
val ClaudeDarkBorder = Color(0xFF20201F)

private val ClaudeLightColorScheme = lightColorScheme(
    primary = ClaudeClay,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFDF4F0),
    onPrimaryContainer = ClaudeClayEmphasized,
    secondary = Color(0xFF6D6B67),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF0EFEC),
    onSecondaryContainer = Color(0xFF20201F),
    background = ClaudePaperBg,
    onBackground = Color(0xFF0B0B0B),
    surface = ClaudePaperBg,
    onSurface = Color(0xFF0B0B0B),
    surfaceContainer = ClaudePaperSurface,
    surfaceContainerLow = Color(0xFFF6F6F4),
    surfaceContainerHigh = Color(0xFFF0EFEC),
    outlineVariant = ClaudePaperBorder,
)

private val ClaudeDarkColorScheme = darkColorScheme(
    primary = ClaudeClay,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF341307),
    onPrimaryContainer = Color(0xFFF7D8CB),
    secondary = Color(0xFFA5A49A),
    onSecondary = Color(0xFF151515),
    secondaryContainer = Color(0xFF20201F),
    onSecondaryContainer = Color(0xFFF0EFEC),
    background = ClaudeDarkBg,
    onBackground = Color(0xFFF9F9F7),
    surface = ClaudeDarkBg,
    onSurface = Color(0xFFF9F9F7),
    surfaceContainer = ClaudeDarkSurface2,
    surfaceContainerLow = ClaudeDarkSurface0,
    surfaceContainerHigh = Color(0xFF252524),
    outlineVariant = ClaudeDarkBorder,
)

@Composable
fun HerdrTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colorScheme = if (dark) ClaudeDarkColorScheme else ClaudeLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}

