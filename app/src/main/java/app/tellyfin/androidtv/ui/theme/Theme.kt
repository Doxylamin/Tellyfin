package app.tellyfin.androidtv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

val Purple = Color(0xFF7C4DFF)
val PurpleDim = Color(0xFF4A2FBF)
val Background = Color(0xFF0D0D0D)
val Surface = Color(0xFF1A1A2E)
val OnSurface = Color(0xFFE0E0E0)
val Overlay = Color(0xCC0D0D0D)
val Red = Color(0xFFE53935)

object AppColors {
    val Purple = app.tellyfin.androidtv.ui.theme.Purple
    val PurpleDim = app.tellyfin.androidtv.ui.theme.PurpleDim
    val Background = app.tellyfin.androidtv.ui.theme.Background
    val Surface = app.tellyfin.androidtv.ui.theme.Surface
    val OnSurface = app.tellyfin.androidtv.ui.theme.OnSurface
    val Overlay = app.tellyfin.androidtv.ui.theme.Overlay
    val Red = app.tellyfin.androidtv.ui.theme.Red
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TellyfinTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Purple,
            onPrimary = Color.White,
            secondary = PurpleDim,
            background = Background,
            surface = Surface,
            onBackground = OnSurface,
            onSurface = OnSurface
        ),
        content = content
    )
}
