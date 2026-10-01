package com.openinglab.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

val Ink = Color(0xFF0C110E)
val DeepMoss = Color(0xFF111813)
val Moss = Color(0xFF18221B)
val Raised = Color(0xFF202C23)
val Cream = Color(0xFFF2EEDF)
val MutedCream = Color(0xFFAAAFA4)
val Leaf = Color(0xFFB8D890)
val LeafDeep = Color(0xFF49623E)
val Gold = Color(0xFFE7B86B)
val Coral = Color(0xFFE89588)
val Divider = Color(0xFF2B372E)
val BoardLight = Color(0xFFE7D6B4)
val BoardDark = Color(0xFF6C8867)
val BoardSelect = Color(0xFFE8C65E)
val BoardLastMove = Color(0x66DCE879)

private val OpeningLabColorScheme = darkColorScheme(
    primary = Leaf,
    onPrimary = Ink,
    primaryContainer = LeafDeep,
    onPrimaryContainer = Cream,
    secondary = Gold,
    onSecondary = Ink,
    tertiary = Coral,
    background = Ink,
    onBackground = Cream,
    surface = DeepMoss,
    onSurface = Cream,
    surfaceVariant = Raised,
    onSurfaceVariant = MutedCream,
    outline = Divider,
    error = Coral,
)

@Composable
fun OpeningLabTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) window.isNavigationBarContrastEnforced = false
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }
    MaterialTheme(
        colorScheme = OpeningLabColorScheme,
        typography = OpeningLabTypography,
        content = content,
    )
}
