package org.regnant.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = Coral,
    onPrimary = Candle,
    secondary = Leaf,
    onSecondary = Candle,
    tertiary = Juniper,
    onTertiary = Candle,
    background = MainBackground,
    onBackground = Candle,
    surface = SurfaceDark,
    onSurface = Candle,
    surfaceVariant = SurfaceCard,
    onSurfaceVariant = Candle
)

@Composable
fun RegnantTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
