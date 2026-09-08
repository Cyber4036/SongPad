package com.songpad.feature.export.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.songpad.core.model.export.ExportAudioFormat
import com.songpad.core.model.export.ExportMode
import com.songpad.core.model.export.ExportState
import com.songpad.core.model.export.ExportSummary
import com.songpad.core.ui.components.EmptyState
import com.songpad.core.ui.components.FilterPill
import com.songpad.core.ui.components.InlineDisclosure
import com.songpad.core.ui.components.PrimaryButton
import com.songpad.core.ui.components.SecondaryButton
import com.songpad.core.ui.components.SectionHeader
import com.songpad.core.ui.components.SongPadTopBar
import com.songpad.core.ui.icons.SongPadIcons
import com.songpad.core.ui.theme.SongPadTheme
import com.songpad.core.ui.theme.Spacing
import com.songpad.feature.export.ExportViewModel
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Phase 0 §13 export: mode, audio format, summary of what's included, then two delivery paths (§12.3). */
@Composable
fun ExportScreen(songId: String, onBack: () -> Unit, viewModel: ExportViewModel = hiltViewModel()) {
    LaunchedEffect(songId) { viewModel.bind(songId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val disclosureSeen by viewModel.disclosureSeen.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val c = SongPadTheme.colors

    // "Save to my files / Drive": SAF, no permission, survives uninstall.
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri -> uri?.let { viewModel.pendingSave = it } }
    LaunchedEffect(viewModel.pendingSave, viewModel.bundlePath) {
        val uri = viewModel.pendingSave ?: return@LaunchedEffect
        val path = viewModel.bundlePath ?: return@LaunchedEffect
        withContext(Dispatchers.IO) { copyToUri(context, File(path), uri) }
        viewModel.pendingSave = null
    }

    Column(Modifier.fillMaxSize()) {
        SongPadTopBar(title = "Export", onBack = onBack)
        when (val s = state) {
            is ExportState.Selecting -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screen)) {
                SectionHeader("What to include")
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    ExportMode.entries.forEach { m -> FilterPill(m.label(), selected = s.request.mode == m, onClick = { viewModel.setMode(m) }) }
                }
                if (s.request.mode != ExportMode.LYRICS_ONLY) {
                    SectionHeader("Audio format")
                    val lossless = s.summary?.let { it.losslessTakeCount > 0 && !it.hasLossyTakes } ?: false
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        ExportAudioFormat.entries.forEach { f ->
                            FilterPill(f.label(lossless), selected = s.request.audioFormat == f, onClick = { viewModel.setFormat(f) })
                        }
                    }
                    if (!disclosureSeen) InlineDisclosure(
                        text = "Takes recorded in Standard quality are stored compressed. Exporting them as WAV makes a bigger file, not a better one — only Lossless-mode takes export bit-exact.",
                        actionLabel = null, onAction = null, onDismiss = viewModel::markDisclosureSeen, modifier = Modifier.padding(top = Spacing.sm),
                    )
                }
                SectionHeader("Summary")
                s.summary?.let { Summary(it, s.request) } ?: Text("Counting…", color = c.textSecondary)
                Spacer(Modifier.height(Spacing.xl))
                PrimaryButton("Prepare export", onClick = viewModel::start, enabled = s.summary != null, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(Spacing.xxxl))
            }
            is ExportState.Preparing -> Column(Modifier.fillMaxSize().padding(Spacing.screen), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                Text("Preparing…", style = MaterialTheme.typography.titleMedium, color = c.text)
                s.currentItem?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = c.textSecondary) }
                Spacer(Modifier.height(Spacing.lg))
                LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth(), color = c.accent)
                Spacer(Modifier.height(Spacing.xl))
                SecondaryButton("Cancel", onClick = viewModel::cancel)
            }
            is ExportState.Success -> Column(Modifier.fillMaxSize().padding(Spacing.screen)) {
                Text("Ready", style = MaterialTheme.typography.titleLarge, color = c.success)
                Text("${s.bundleName} · ${s.byteSize.mb()}", style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
                Spacer(Modifier.height(Spacing.xl))
                PrimaryButton("Save to files", onClick = { saveLauncher.launch(s.bundleName) }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(Spacing.sm))
                SecondaryButton("Send to…", onClick = { viewModel.bundlePath?.let { share(context, File(it)) } }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(Spacing.sm))
                SecondaryButton("Export again", onClick = viewModel::reset, modifier = Modifier.fillMaxWidth())
            }
            is ExportState.Failure -> EmptyState(SongPadIcons.Download, "Export didn't finish", s.error.message, actionLabel = "Back", onAction = viewModel::reset)
            ExportState.Cancelled -> EmptyState(SongPadIcons.Download, "Export cancelled", "Nothing was written.", actionLabel = "Back", onAction = viewModel::reset)
        }
    }
}

@Composable
private fun Summary(s: ExportSummary, request: com.songpad.core.model.export.ExportRequest) {
    val c = SongPadTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        if (request.mode != ExportMode.LYRICS_ONLY) {
            Text("${s.takeCount} take${if (s.takeCount == 1) "" else "s"}" + if (s.midiCount > 0) " · ${s.midiCount} MIDI" else "", color = c.text)
            Text(
                when {
                    s.takeCount == 0 -> "No audio to include."
                    !s.hasLossyTakes -> "Source: lossless (FLAC) for every take."
                    s.losslessTakeCount == 0 -> "Source: compressed (M4A) for every take."
                    else -> "Source: ${s.losslessTakeCount} lossless (FLAC), ${s.takeCount - s.losslessTakeCount} compressed (M4A)."
                },
                style = MaterialTheme.typography.bodySmall, color = c.textSecondary,
            )
        }
        if (request.mode != ExportMode.AUDIO_TAKES) Text("${s.sectionCount} lyric section${if (s.sectionCount == 1) "" else "s"} · ${s.noteCount} note${if (s.noteCount == 1) "" else "s"}", color = c.text)
        Text("About ${s.estimatedBytes.mb()}", style = MaterialTheme.typography.bodySmall, color = c.textTertiary)
    }
}

private fun ExportMode.label() = when (this) { ExportMode.EVERYTHING -> "Everything"; ExportMode.AUDIO_TAKES -> "Audio takes"; ExportMode.LYRICS_ONLY -> "Lyrics only" }
private fun Long.mb(): String = if (this < 1024 * 1024) "${this / 1024} KB" else "%.1f MB".format(this / (1024.0 * 1024.0))

private fun copyToUri(context: Context, file: File, uri: Uri) {
    context.contentResolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out, 16 * 1024) } }
}

/** "Send this to someone": FileProvider + ACTION_SEND; never a file:// URI (§12.3). */
private fun share(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "application/zip"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "Send bundle").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
