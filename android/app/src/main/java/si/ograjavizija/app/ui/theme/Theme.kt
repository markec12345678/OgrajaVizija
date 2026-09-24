package si.ograjavizija.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Bg = Color(0xFF0F1115)
val Surface = Color(0xFF171A21)
val SurfaceAlt = Color(0xFF1E222B)
val Accent = Color(0xFF5AD1A0)
val AccentBlue = Color(0xFF7AA2FF)
val Warn = Color(0xFFFFB454)
val Bad = Color(0xFFFF7B7B)
val Muted = Color(0xFF98A0B3)
val Line = Color(0xFF272C37)

private val Dark = darkColorScheme(
    primary = Accent, secondary = AccentBlue, tertiary = Warn,
    background = Bg, surface = Surface, surfaceVariant = SurfaceAlt,
    onPrimary = Color(0xFF06231A), onSecondary = Color(0xFF0B1533),
    onBackground = Color(0xFFE8EAF0), onSurface = Color(0xFFE8EAF0),
    onSurfaceVariant = Muted, error = Bad, outline = Line,
)

@Composable
fun VizijaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Dark, content = content)
}
