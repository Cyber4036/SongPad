package com.songpad.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Layer 2: semantic roles (§14). This is the ONLY color vocabulary components may use.
 * The seven contract roles — bg, surface, text, accent, accent2, record, divider — match the §14 table
 * exactly (asserted by `SemanticColorsTest`). Everything else is derived through [Tone] so all four
 * themes share one derivation, and the waveform/piano canvases never need per-theme drawing code.
 */
@Immutable
data class SemanticColors(
    val bg: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val text: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val accent: Color,
    val onAccent: Color,
    val accentSoft: Color,
    val accent2: Color,
    val accent2Soft: Color,
    val record: Color,
    val recordSoft: Color,
    val divider: Color,
    val success: Color,
    val warning: Color,
    val destructive: Color,
    val glassFill: Color,
    val glassStroke: Color,
    val isDark: Boolean,
) {
    companion object {
        fun forVariant(variant: ThemeVariant): SemanticColors = when (variant) {
            ThemeVariant.SONGPAD_LIGHT -> Palette.SongPadLight.let {
                build(it.bg, it.surface, it.text, it.accent.base, it.accent2.base, it.record, it.dividerBase, it.dividerAlpha, it.neutral, isDark = false)
            }
            ThemeVariant.SONGPAD_DARK -> Palette.SongPadDark.let {
                build(it.bg, it.surface, it.text, it.accent.base, it.accent2.base, it.record, it.dividerBase, it.dividerAlpha, it.neutral, isDark = true)
            }
            ThemeVariant.MELODY_LIGHT -> Palette.MelodyLight.let {
                build(it.bg, it.surface, it.text, it.accent.base, it.accent2.base, it.record, it.dividerBase, it.dividerAlpha, it.neutral, isDark = false)
            }
            ThemeVariant.MELODY_DARK -> Palette.MelodyDark.let {
                build(it.bg, it.surface, it.text, it.accent.base, it.accent2.base, it.record, it.dividerBase, it.dividerAlpha, it.neutral, isDark = true)
            }
        }

        private fun build(
            bg: Color, surface: Color, text: Color, accent: Color, accent2: Color, record: Color,
            dividerBase: Color, dividerAlpha: Float, neutral: Palette.Ramp, isDark: Boolean,
        ): SemanticColors {
            val onAccent = if (Oklch.fromColor(accent).l > 0.68) Color(0xFF1A1410) else Color.White
            return SemanticColors(
                bg = bg,
                surface = surface,
                surfaceVariant = if (isDark) neutral.c200 else neutral.c200,
                text = text,
                textSecondary = neutral.c600,
                textTertiary = neutral.c400,
                accent = accent,
                onAccent = onAccent,
                accentSoft = Tone.soft(accent, 0.18),
                accent2 = accent2,
                accent2Soft = Tone.soft(accent2, 0.22),
                record = record,
                recordSoft = Tone.soft(record, if (isDark) 0.34 else 0.26),
                divider = dividerBase.copy(alpha = dividerAlpha),
                success = Tone.muted(Palette.success, isDark),
                warning = Tone.muted(Palette.warning, isDark),
                destructive = Tone.muted(Palette.destructive, isDark),
                glassFill = surface.copy(alpha = if (isDark) 0.72f else 0.78f),
                glassStroke = text.copy(alpha = if (isDark) 0.14f else 0.10f),
                isDark = isDark,
            )
        }
    }
}

val LocalSemanticColors = staticCompositionLocalOf<SemanticColors> { SemanticColors.forVariant(ThemeVariant.SONGPAD_LIGHT) }
