package com.doggycredit.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val Light = lightColorScheme(
    primary = Forest, onPrimary = Ivory, primaryContainer = Color(0xFFD3EBE0), onPrimaryContainer = Pine,
    secondary = Sage, onSecondary = Ivory, secondaryContainer = Color(0xFFDBEADF), onSecondaryContainer = Pine,
    tertiary = Color(0xFF8A5719), onTertiary = Ivory, tertiaryContainer = Color(0xFFF7DDA0), onTertiaryContainer = Color(0xFF372D1C),
    background = Parchment, onBackground = Ink, surface = Ivory, onSurface = Ink,
    surfaceVariant = Color(0xFFE6EBE4), onSurfaceVariant = Color(0xFF505E56), outline = Color(0xFF737F77),
    surfaceDim = Color(0xFFD9DED5), surfaceBright = Ivory,
    surfaceContainerLowest = Ivory, surfaceContainerLow = Color(0xFFF2F1E9), surfaceContainer = Color(0xFFEEEFE6),
    surfaceContainerHigh = Color(0xFFE6EADF), surfaceContainerHighest = Color(0xFFDDE4D7),
    outlineVariant = Color(0xFFDDE1D5), surfaceTint = Forest,
    inverseSurface = Color(0xFF26332D), inverseOnSurface = Color(0xFFF2F5EF), inversePrimary = Color(0xFF81D3BA),
    error = Color(0xFFB23A2F), onError = Ivory, errorContainer = Color(0xFFFFDAD4), onErrorContainer = Color(0xFF650A03)
)
private val Dark = darkColorScheme(
    primary = Color(0xFF81D3BA), onPrimary = Pine, primaryContainer = Color(0xFF21594C), onPrimaryContainer = Color(0xFFC0F0DC),
    secondary = Color(0xFFABCDBB), onSecondary = Color(0xFF17392C), secondaryContainer = Color(0xFF304E40), onSecondaryContainer = Color(0xFFD6EFDD),
    tertiary = Color(0xFFEBC282), onTertiary = Color(0xFF432C08), tertiaryContainer = Color(0xFF604217), onTertiaryContainer = Color(0xFFF7DDA0),
    background = Color(0xFF101815), onBackground = Color(0xFFE4EAE2), surface = Color(0xFF151D19), onSurface = Color(0xFFE4EAE2),
    surfaceVariant = Color(0xFF3C4941), onSurfaceVariant = Color(0xFFBFCCC1), outline = Color(0xFF89968C),
    surfaceDim = Color(0xFF101815), surfaceBright = Color(0xFF333D37),
    surfaceContainerLowest = Color(0xFF0B120E), surfaceContainerLow = Color(0xFF151D19), surfaceContainer = Color(0xFF1A231E),
    surfaceContainerHigh = Color(0xFF25302A), surfaceContainerHighest = Color(0xFF303C34),
    outlineVariant = Color(0xFF3F4E43), surfaceTint = Color(0xFF81D3BA),
    inverseSurface = Color(0xFFE4EAE2), inverseOnSurface = Color(0xFF26332D), inversePrimary = Forest,
    error = Color(0xFFFFB4A8), onError = Color(0xFF690C04), errorContainer = Color(0xFF8F2116), onErrorContainer = Color(0xFFFFDAD4)
)
@Composable fun DoggyCreditTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) Dark else Light, typography = Typography,
        shapes = Shapes(extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(18.dp), large = RoundedCornerShape(24.dp), extraLarge = RoundedCornerShape(32.dp)),
        content = content)
}
