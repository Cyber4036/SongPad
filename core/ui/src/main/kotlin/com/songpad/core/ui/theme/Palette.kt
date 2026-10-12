package com.songpad.core.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Layer 1 primitives: the two personalities' ramps as authored in the design prototype (Capture.html).
 * Nothing outside `theme/` may reference these — components use [SemanticColors] only.
 */
internal object Palette {
    /** Numbered ramp: 100 (lightest on light themes) … 900. `base` is the brand anchor. */
    class Ramp(val base: Color, val c100: Color, val c200: Color, val c400: Color, val c600: Color, val c700: Color, val c800: Color, val c900: Color)

    object SongPadLight {
        val bg = Color(0xFFF5EAD8)
        val surface = Color(0xFFEBDDC5)
        val text = Color(0xFF201E1D)
        val neutral = Ramp(Color(0xFF2E2B25), Color(0xFFF9F4ED), Color(0xFFEEE7DB), Color(0xFFC0B6A5), Color(0xFF82796A), Color(0xFF645C50), Color(0xFF474238), Color(0xFF2E2B25))
        val accent = Ramp(Color(0xFFC67139), Color(0xFFFFF2EB), Color(0xFFFFE1D0), Color(0xFFF6A06B), Color(0xFFB2622D), Color(0xFF8C491A), Color(0xFF643312), Color(0xFF402310))
        val accent2 = Ramp(Color(0xFF7A8A5E), Color(0xFFF0FAE1), Color(0xFFE1EECC), Color(0xFFAEBF92), Color(0xFF728157), Color(0xFF56633F), Color(0xFF3D472B), Color(0xFF272E1B))
        val record = accent.base
        val dividerBase = Color(0xFF201E1D)
        const val dividerAlpha = 0.16f
    }

    object SongPadDark {
        val bg = Color(0xFF1B1815)
        val surface = Color(0xFF38322B)
        val text = Color(0xFFF4EBDD)
        val neutral = Ramp(Color(0xFFE6DCCD), Color(0xFF332E28), Color(0xFF3D372F), Color(0xFF5D5449), Color(0xFFA79B8A), Color(0xFFC7BBA8), Color(0xFFE6DCCD), Color(0xFFF4EBDD))
        val accent = Ramp(Color(0xFFE08A4C), Color(0xFF3A2517), Color(0xFF4A2E1B), Color(0xFFF0A468), Color(0xFFF0A468), Color(0xFFF6BD8D), Color(0xFFF7CBA4), Color(0xFFFBE0C8))
        val accent2 = Ramp(Color(0xFF2B3122), Color(0xFF2B3122), Color(0xFF3D472B), Color(0xFF8FA073), Color(0xFFAEBF92), Color(0xFFCCDBB2), Color(0xFFD3DFBA), Color(0xFFE1EECC))
        val record = accent.base
        val dividerBase = Color(0xFFF4EBDD)
        const val dividerAlpha = 0.16f
    }

    object MelodyLight {
        val bg = Color(0xFFE9E0FA)
        val surface = Color(0xFFFDFAFF)
        val text = Color(0xFF1C1233)
        val neutral = Ramp(Color(0xFF2E1D4F), Color(0xFFF1EAFD), Color(0xFFE0D4F8), Color(0xFFAB98D9), Color(0xFF6B5E8E), Color(0xFF4E3480), Color(0xFF332552), Color(0xFF241640))
        val accent = Ramp(Color(0xFF7B2FF7), Color(0xFFE2D2FF), Color(0xFFCFB4FF), Color(0xFF8F45E0), Color(0xFF6620DD), Color(0xFF54159F), Color(0xFF3D0E78), Color(0xFF2A0A55))
        val accent2 = Ramp(Color(0xFF00B3C8), Color(0xFFD8F6FA), Color(0xFFB6F4FB), Color(0xFF2EE6F5), Color(0xFF00B3C8), Color(0xFF0B8FA0), Color(0xFF075D6B), Color(0xFF0B3B45))
        val record = Color(0xFFFF3355)
        val dividerBase = Color(0xFF4A2A8A)
        const val dividerAlpha = 0.20f
    }

    object MelodyDark {
        val bg = Color(0xFF0D0718)
        val surface = Color(0xFF2A1D47)
        val text = Color(0xFFF3EBFF)
        val neutral = Ramp(Color(0xFFE6D9FF), Color(0xFF241640), Color(0xFF2E1D4F), Color(0xFF4E3480), Color(0xFFA992D6), Color(0xFFCDBCF0), Color(0xFFE6D9FF), Color(0xFFF3EBFF))
        val accent = Ramp(Color(0xFFB46BFF), Color(0xFF2A1550), Color(0xFF3A1D6E), Color(0xFFA06BFA), Color(0xFFC98CFF), Color(0xFFDCB0FF), Color(0xFFEAD0FF), Color(0xFFF5E8FF))
        val accent2 = Ramp(Color(0xFF2EE6F5), Color(0xFF0B3B45), Color(0xFF075D6B), Color(0xFF00B3C8), Color(0xFF2EE6F5), Color(0xFF8AF0F8), Color(0xFFB6F4FB), Color(0xFFD8F6FA))
        val record = Color(0xFFFF5A7A)
        val dividerBase = Color(0xFFC9A6FF)
        const val dividerAlpha = 0.22f
    }

    /** Status colors shared by all four themes; chroma adjusted per personality via [Tone]. */
    val success = Color(0xFF3E8E5A)
    val warning = Color(0xFFD69A2B)
    val destructive = Color(0xFFD64545)
}
