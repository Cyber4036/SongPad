package com.songpad.core.ui.theme

/** Four themes from two personalities (Phase 0 §17, spec §14). One theme architecture, not four designs. */
enum class ThemeVariant(val personality: Personality, val isDark: Boolean, val displayName: String) {
    SONGPAD_LIGHT(Personality.SONGPAD, false, "SongPad Light"),
    SONGPAD_DARK(Personality.SONGPAD, true, "SongPad Dark"),
    MELODY_LIGHT(Personality.MELODY_STUDIO, false, "MelodyStudio Light"),
    MELODY_DARK(Personality.MELODY_STUDIO, true, "MelodyStudio Dark"),
    ;

    enum class Personality(val displayName: String) { SONGPAD("SongPad"), MELODY_STUDIO("MelodyStudio") }

    companion object {
        fun of(personality: Personality, dark: Boolean): ThemeVariant = entries.first { it.personality == personality && it.isDark == dark }
        fun fromName(name: String?): ThemeVariant? = entries.firstOrNull { it.name == name }
    }
}
