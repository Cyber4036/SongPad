package com.songpad.feature.export.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
import com.songpad.core.ui.components.SettingsGroupCard
import com.songpad.core.ui.components.SongPadTopBar
import com.songpad.core.ui.components.TagChip
import com.songpad.core.ui.components.TagTone
import com.songpad.core.ui.icons.SongPadIcons
import com.songpad.core.ui.theme.SongPadTheme
import com.songpad.core.ui.theme.Spacing
import com.songpad.feature.export.ExportViewModel
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Dynamic SAF `ACTION_CREATE_DOCUMENT` contract that takes `(suggestedFileName, mimeType)` at launch time,
 * preventing Android from appending `.bin` or `.zip` when saving direct `.txt`, `.mp3`, `.wav`, `.m4a`, or `.flac` files.
 */
private object CreateDocumentWithMime : ActivityResultContract<Pair<String, String>, Uri?>() {
    override fun createIntent(context: Context, input: Pair<String, String>): Intent =
        Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = input.second.ifBlank { "application/octet-stream" }
            putExtra(Intent.EXTRA_TITLE, input.first)
        }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        if (resultCode == Activity.RESULT_OK) intent?.data else null
}

/** Phase 0 §13 export: mode, audio format, summary of what's included, then two delivery paths (§12.3). */
@Composable
fun ExportScreen(
    songId: String,
    takeId: String? = null,
    onBack: () -> Unit,
    viewModel: ExportViewModel = hiltViewModel(),
) {
    LaunchedEffect(songId, takeId) { viewModel.bind(songId, takeId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val disclosureSeen by viewModel.disclosureSeen.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val c = SongPadTheme.colors

    val saveLauncher = rememberLauncherForActivityResult(CreateDocumentWithMime) { uri ->
        uri?.let { viewModel.pendingSave = it }
    }
    LaunchedEffect(viewModel.pendingSave, viewModel.bundlePath) {
        val uri = viewModel.pendingSave ?: return@LaunchedEffect
        val path = viewModel.bundlePath ?: return@LaunchedEffect
        val file = File(path)
        withContext(Dispatchers.IO) { copyToUri(context, file, uri) }
        viewModel.savedFileConfirmation = "Saved ${file.name} to device files."
        viewModel.pendingSave = null
    }

    Column(Modifier.fillMaxSize()) {
        SongPadTopBar(title = if (takeId != null) "Export take" else "Export", onBack = onBack)
        when (val s = state) {
            is ExportState.Selecting -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                if (s.request.takeId != null) {
                    SectionHeader("Scope")
                    SettingsGroupCard {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                TagChip(text = "Single take", tone = TagTone.Accent)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = s.summary?.selectedTakeName ?: "Selected take",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = c.text,
                                )
                                Text(
                                    text = "Exports directly as a standalone audio file",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = c.textSecondary,
                                )
                            }
                            SecondaryButton(
                                text = "Full song",
                                onClick = viewModel::clearTakeScope,
                            )
                        }
                    }
                } else {
                    SectionHeader("What to include")
                    Row(
                        modifier = Modifier.padding(horizontal = Spacing.screen),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        ExportMode.entries.forEach { m ->
                            FilterPill(m.label(), selected = s.request.mode == m, onClick = { viewModel.setMode(m) })
                        }
                    }
                }
                if (s.request.mode != ExportMode.LYRICS_ONLY) {
                    SectionHeader("Audio format")
                    val lossless = s.summary?.let { it.losslessTakeCount > 0 && !it.hasLossyTakes } ?: false
                    Column(
                        modifier = Modifier.padding(horizontal = Spacing.screen),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        ExportAudioFormat.entries.forEach { f ->
                            FilterPill(f.label(lossless), selected = s.request.audioFormat == f, onClick = { viewModel.setFormat(f) })
                        }
                    }
                    if (!disclosureSeen) InlineDisclosure(
                        text = "Takes recorded in Standard quality are stored compressed. Exporting them as WAV makes a bigger file, not a better one — only Lossless-mode takes export bit-exact.",
                        actionLabel = null, onAction = null, onDismiss = viewModel::markDisclosureSeen,
                        modifier = Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.sm),
                    )
                }
                SectionHeader("Summary")
                SettingsGroupCard {
                    Column(Modifier.padding(16.dp)) {
                        s.summary?.let { Summary(it, s.request) } ?: Text("Counting…", color = c.textSecondary)
                    }
                }
                Spacer(Modifier.height(Spacing.xl))
                PrimaryButton(
                    text = "Prepare export",
                    onClick = viewModel::start,
                    enabled = s.summary != null,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.screen),
                )
                Spacer(Modifier.height(Spacing.xxxl))
            }
            is ExportState.Preparing -> Column(Modifier.fillMaxSize().padding(Spacing.screen), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Preparing…", style = MaterialTheme.typography.titleMedium, color = c.text)
                s.currentItem?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = c.textSecondary) }
                Spacer(Modifier.height(Spacing.lg))
                LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth(), color = c.accent)
                Spacer(Modifier.height(Spacing.xl))
                SecondaryButton("Cancel", onClick = viewModel::cancel)
            }
            is ExportState.Success -> Column(Modifier.fillMaxSize().padding(Spacing.screen)) {
                SettingsGroupCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TagChip(text = mimeBadge(s.mimeType, s.bundleName), tone = TagTone.Accent)
                            Text("Ready to export", style = MaterialTheme.typography.titleLarge, color = c.success)
                        }
                        Text("${s.bundleName} · ${s.byteSize.mb()}", style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
                        viewModel.savedFileConfirmation?.let { msg ->
                            Text(msg, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold), color = c.accent)
                        }
                    }
                }
                Spacer(Modifier.height(Spacing.xl))
                PrimaryButton(
                    text = "Save to files",
                    onClick = { saveLauncher.launch(s.bundleName to s.mimeType) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(Spacing.sm))
                SecondaryButton(
                    text = "Send to…",
                    onClick = { viewModel.bundlePath?.let { share(context, File(it), s.mimeType) } },
                    modifier = Modifier.fillMaxWidth(),
                )
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
        if (request.mode != ExportMode.AUDIO_TAKES && request.takeId == null) {
            Text("${s.sectionCount} lyric section${if (s.sectionCount == 1) "" else "s"} · ${s.noteCount} note${if (s.noteCount == 1) "" else "s"}", color = c.text)
        }
        if (s.outputFileName.isNotBlank()) {
            Text(
                text = "Output: ${s.outputFileName} (${mimeLabel(s.outputMimeType)})",
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                color = c.accent,
            )
        }
        Text("About ${s.estimatedBytes.mb()}", style = MaterialTheme.typography.bodySmall, color = c.textTertiary)
    }
}

private fun mimeBadge(mimeType: String, fileName: String): String {
    val ext = fileName.substringAfterLast('.', "").uppercase()
    if (ext.isNotEmpty() && ext.length <= 5) return ext
    return when (mimeType) {
        "text/plain" -> "TXT"
        "audio/mpeg" -> "MP3"
        "audio/wav" -> "WAV"
        "audio/mp4" -> "M4A"
        "audio/flac" -> "FLAC"
        else -> "ZIP"
    }
}

private fun mimeLabel(mimeType: String): String = when (mimeType) {
    "text/plain" -> "Plain text file"
    "audio/mpeg" -> "Direct MP3 audio"
    "audio/wav" -> "Direct WAV audio"
    "audio/mp4" -> "Direct M4A audio"
    "audio/flac" -> "Direct FLAC audio"
    else -> "ZIP archive"
}

private fun ExportMode.label() = when (this) { ExportMode.EVERYTHING -> "Everything"; ExportMode.AUDIO_TAKES -> "Audio takes"; ExportMode.LYRICS_ONLY -> "Lyrics only" }
private fun Long.mb(): String = if (this < 1024) "$this B" else if (this < 1024 * 1024) "${this / 1024} KB" else "%.1f MB".format(this / (1024.0 * 1024.0))

private fun copyToUri(context: Context, file: File, uri: Uri) {
    context.contentResolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out, 16 * 1024) } }
}

private fun share(context: Context, file: File, mimeType: String) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = mimeType.ifBlank { "application/octet-stream" }
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "Share ${file.name}").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
