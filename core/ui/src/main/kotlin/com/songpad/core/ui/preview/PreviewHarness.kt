package com.songpad.core.ui.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.songpad.core.ui.components.EmptyState
import com.songpad.core.ui.components.FilterPill
import com.songpad.core.ui.components.GlassPill
import com.songpad.core.ui.components.IconAction
import com.songpad.core.ui.components.InlineDisclosure
import com.songpad.core.ui.components.PrimaryButton
import com.songpad.core.ui.components.RecordButton
import com.songpad.core.ui.components.RibbonPlayer
import com.songpad.core.ui.components.SecondaryButton
import com.songpad.core.ui.components.SeekableWaveform
import com.songpad.core.ui.components.SongListItem
import com.songpad.core.ui.components.TagChip
import com.songpad.core.ui.components.TakeListItem
import com.songpad.core.ui.icons.SongPadIcons
import com.songpad.core.ui.theme.SongPadTheme
import com.songpad.core.ui.theme.Spacing
import com.songpad.core.ui.theme.ThemeVariant

/**
 * Preview harness (Prompt 0-D "tokens compile and theme-switch cleanly in a preview harness"; Prompt 1-D
 * "every component renders correctly in all four themes, at default and large type").
 */
@Preview(name = "SongPad Light", group = "themes", showBackground = true)
@Preview(name = "SongPad Dark", group = "themes", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Melody Light", group = "themes", showBackground = true, fontScale = 1f)
@Preview(name = "Large type 1.3x", group = "type", showBackground = true, fontScale = 1.3f)
annotation class FourThemePreview

@Composable
fun ComponentGallery(variant: ThemeVariant) {
    SongPadTheme(variant = variant) {
        val c = SongPadTheme.colors
        Column(Modifier.background(c.bg).padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                PrimaryButton("Save", {})
                SecondaryButton("Discard", {}, destructive = true)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                FilterPill("All", true, {}); FilterPill("Voice", false, {}, icon = SongPadIcons.Mic); FilterPill("Piano", false, {}, icon = SongPadIcons.Piano)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) { TagChip("Ongoing"); TagChip("Voice + Piano"); TagChip("Transcoding", tone = com.songpad.core.ui.components.TagTone.Warning) }
            RecordButton(isRecording = false, onClick = {})
            GlassPill(Modifier.fillMaxWidth()) {
                IconAction(SongPadIcons.Stop, "Discard", {}, tint = c.destructive)
                SeekableWaveform(FloatArray(120) { ((it * 37) % 100) / 100f }, 0.35f, {}, Modifier.weight(1f))
                IconAction(SongPadIcons.Pause, "Pause", {}, tint = c.record)
            }
            SongListItem("Midnight Riff", "Ongoing", "3 takes · edited today", true, {}, {})
            TakeListItem("Voice take", "Voice", SongPadIcons.Mic, "0:15", "today", null, false, {}, {})
            RibbonPlayer("Voice take", "Midnight Riff", true, 0.4f, "0:06 / 0:15", FloatArray(80) { (it % 10) / 10f }, {}, {}, {})
            InlineDisclosure("Recording in Standard quality. Lossless uses ~3× the space and exports without quality loss.", "Change", {}, {})
            EmptyState(Icons.Default.Search, "No results", "Try a lyric, a take name, or a note.")
        }
    }
}

@FourThemePreview @Composable private fun SongPadLightGallery() = ComponentGallery(ThemeVariant.SONGPAD_LIGHT)
@Preview(showBackground = true) @Composable private fun SongPadDarkGallery() = ComponentGallery(ThemeVariant.SONGPAD_DARK)
@Preview(showBackground = true) @Composable private fun MelodyLightGallery() = ComponentGallery(ThemeVariant.MELODY_LIGHT)
@Preview(showBackground = true) @Composable private fun MelodyDarkGallery() = ComponentGallery(ThemeVariant.MELODY_DARK)
@Preview(showBackground = true, fontScale = 1.5f) @Composable private fun LargeTypeGallery() = ComponentGallery(ThemeVariant.MELODY_DARK)
