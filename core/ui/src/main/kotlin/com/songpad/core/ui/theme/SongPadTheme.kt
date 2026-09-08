package com.songpad.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf

val LocalThemeVariant = staticCompositionLocalOf { ThemeVariant.SONGPAD_LIGHT }

/**
 * Theme wrapper. Material 3 is the component base, so its ColorScheme is *mapped from* the semantic
 * tokens — never the other way round. Components in this module read [SongPadTheme.colors].
 */
@Composable
fun SongPadTheme(
    variant: ThemeVariant? = null,
    personality: ThemeVariant.Personality = ThemeVariant.Personality.SONGPAD,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val resolved = variant ?: ThemeVariant.of(personality, darkTheme)
    val colors = remember(resolved) { SemanticColors.forVariant(resolved) }
    val scheme = remember(resolved) { colors.toMaterialScheme() }
    CompositionLocalProvider(LocalSemanticColors provides colors, LocalThemeVariant provides resolved) {
        MaterialTheme(colorScheme = scheme, typography = SongPadTypography, shapes = SongPadShapes, content = content)
    }
}

object SongPadTheme {
    val colors: SemanticColors
        @Composable @ReadOnlyComposable get() = LocalSemanticColors.current
    val variant: ThemeVariant
        @Composable @ReadOnlyComposable get() = LocalThemeVariant.current
}

private fun SemanticColors.toMaterialScheme(): ColorScheme {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = accentSoft,
        onPrimaryContainer = text,
        secondary = accent2,
        onSecondary = onAccent,
        secondaryContainer = accent2Soft,
        onSecondaryContainer = text,
        tertiary = record,
        onTertiary = onAccent,
        background = bg,
        onBackground = text,
        surface = surface,
        onSurface = text,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = textSecondary,
        surfaceContainer = surface,
        surfaceContainerLow = bg,
        surfaceContainerHigh = surfaceVariant,
        surfaceContainerHighest = surfaceVariant,
        outline = divider,
        outlineVariant = divider,
        error = destructive,
        onError = onAccent,
        scrim = text.copy(alpha = 0.4f),
    )
}
