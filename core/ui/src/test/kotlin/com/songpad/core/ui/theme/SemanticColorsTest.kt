package com.songpad.core.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The seven §14 contract roles must match the spec table byte-for-byte across all four themes. */
class SemanticColorsTest {
    private fun hex(c: Color) = "#%06X".format(c.toArgb() and 0xFFFFFF)

    private data class Row(val variant: ThemeVariant, val bg: String, val surface: String, val text: String, val accent: String, val accent2: String, val record: String)

    private val table = listOf(
        Row(ThemeVariant.SONGPAD_LIGHT, "#F5EAD8", "#EBDDC5", "#201E1D", "#C67139", "#7A8A5E", "#C67139"),
        Row(ThemeVariant.SONGPAD_DARK, "#1B1815", "#2A2621", "#F4EBDD", "#E08A4C", "#2B3122", "#E08A4C"),
        Row(ThemeVariant.MELODY_LIGHT, "#E9E0FA", "#FDFAFF", "#1C1233", "#7B2FF7", "#00B3C8", "#FF3355"),
        Row(ThemeVariant.MELODY_DARK, "#0D0718", "#1A0F2E", "#F3EBFF", "#B46BFF", "#2EE6F5", "#FF5A7A"),
    )

    @Test
    fun `semantic roles match the section 14 table`() {
        table.forEach { row ->
            val c = SemanticColors.forVariant(row.variant)
            assertThat(hex(c.bg)).isEqualTo(row.bg)
            assertThat(hex(c.surface)).isEqualTo(row.surface)
            assertThat(hex(c.text)).isEqualTo(row.text)
            assertThat(hex(c.accent)).isEqualTo(row.accent)
            assertThat(hex(c.accent2)).isEqualTo(row.accent2)
            assertThat(hex(c.record)).isEqualTo(row.record)
        }
    }

    @Test
    fun `divider alphas follow the table`() {
        assertThat(SemanticColors.forVariant(ThemeVariant.SONGPAD_LIGHT).divider.alpha).isWithin(0.005f).of(0.16f)
        assertThat(SemanticColors.forVariant(ThemeVariant.MELODY_LIGHT).divider.alpha).isWithin(0.005f).of(0.20f)
        assertThat(SemanticColors.forVariant(ThemeVariant.MELODY_DARK).divider.alpha).isWithin(0.005f).of(0.22f)
    }

    @Test
    fun `oklch round trips within one step of 8 bit`() {
        listOf(Color(0xFFC67139), Color(0xFF7B2FF7), Color(0xFF0D0718), Color(0xFFF5EAD8), Color(0xFF2EE6F5)).forEach { c ->
            val back = Oklch.fromColor(c).toColor()
            assertThat(Math.abs(back.red - c.red)).isLessThan(1.5f / 255)
            assertThat(Math.abs(back.green - c.green)).isLessThan(1.5f / 255)
            assertThat(Math.abs(back.blue - c.blue)).isLessThan(1.5f / 255)
        }
    }

    @Test
    fun `dark themes derive on-accent for legibility`() {
        assertThat(SemanticColors.forVariant(ThemeVariant.SONGPAD_DARK).isDark).isTrue()
        assertThat(SemanticColors.forVariant(ThemeVariant.MELODY_LIGHT).onAccent).isEqualTo(Color.White)
    }
}
