package net.fribbynetwork.iamhere.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Identita cromatica fissa: carta nautica + arancio segnale.
// Non usiamo il colore dinamico di sistema, cosi lo stato "attivo"
// e riconoscibile allo stesso modo su qualunque telefono.
val ChartTeal = Color(0xFF0E3B43)
val ChartTealMid = Color(0xFF1C6E7E)
val ChartTealPale = Color(0xFFCFE1E3)
val SignalOrange = Color(0xFFE4572E)
val SignalOrangeDim = Color(0xFF8C2F17)
val Sand = Color(0xFFF2F5F4)
val Ink = Color(0xFF0B1416)
val Slate = Color(0xFF7A8C8F)

private val Light = lightColorScheme(
    primary = ChartTeal,
    onPrimary = Color.White,
    primaryContainer = ChartTealPale,
    onPrimaryContainer = ChartTeal,
    secondary = ChartTealMid,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE1EDEE),
    onSecondaryContainer = ChartTeal,
    tertiary = SignalOrange,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFBDDD3),
    onTertiaryContainer = Color(0xFF5C1B08),
    background = Sand,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Color(0xFFE4EAE9),
    onSurfaceVariant = Color(0xFF41504F),
    outline = Slate,
    error = Color(0xFFB3261E),
    onError = Color.White
)

private val Dark = darkColorScheme(
    primary = Color(0xFF7CC5D2),
    onPrimary = Color(0xFF00363F),
    primaryContainer = Color(0xFF104E59),
    onPrimaryContainer = Color(0xFFB6EBF6),
    secondary = Color(0xFF9CCBD3),
    onSecondary = Color(0xFF003239),
    secondaryContainer = Color(0xFF14454E),
    onSecondaryContainer = Color(0xFFB8E9F1),
    tertiary = Color(0xFFFF8A61),
    onTertiary = Color(0xFF4E1500),
    tertiaryContainer = SignalOrangeDim,
    onTertiaryContainer = Color(0xFFFFDBCF),
    background = Ink,
    onBackground = Color(0xFFDDE4E4),
    surface = Color(0xFF121D1F),
    onSurface = Color(0xFFDDE4E4),
    surfaceVariant = Color(0xFF1D2A2C),
    onSurfaceVariant = Color(0xFFB8C6C7),
    outline = Color(0xFF7E8E90),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

// Cifre tabulari per le letture che cambiano di continuo: senza questo
// le coordinate "ballano" a ogni aggiornamento.
val Readout = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Medium,
    fontSize = 15.sp,
    letterSpacing = 0.sp
)

val ReadoutBig = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Bold,
    fontSize = 34.sp,
    letterSpacing = (-0.5).sp
)

enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Composable
fun TrackerTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (dark) Dark else Light,
        typography = Typography(),
        content = content
    )
}
