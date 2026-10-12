package com.songpad.core.model.data

import com.songpad.core.model.export.ExportSource
import com.songpad.core.model.lyrics.LyricsDocument
import com.songpad.core.model.lyrics.Section
import com.songpad.core.model.lyrics.SectionTag
import com.songpad.core.model.note.Note
import com.songpad.core.model.note.NoteDraft
import com.songpad.core.model.nudge.Nudge
import com.songpad.core.model.nudge.NudgeCandidate
import com.songpad.core.model.nudge.NudgeSnapshot
import com.songpad.core.model.song.Song
import com.songpad.core.model.song.SongDetail
import com.songpad.core.model.song.SongFilter
import com.songpad.core.model.song.SongStatus
import com.songpad.core.model.take.OrphanedTake
import com.songpad.core.model.take.QualityMode
import com.songpad.core.model.take.Take
import com.songpad.core.model.take.TakeDraft
import kotlinx.coroutines.flow.Flow

// ── §15 FROZEN CONTRACTS — implemented in :core:data. ──

interface TakeRepository {
    fun observeForSong(songId: String): Flow<List<Take>>
    fun observeUnassigned(): Flow<List<Take>>
    suspend fun commit(draft: TakeDraft): Result<Take>
    suspend fun assignToSong(takeId: String, songId: String): Result<Unit>
    suspend fun rename(takeId: String, name: String): Result<Unit>
    suspend fun softDelete(takeId: String): Result<Unit>
    suspend fun findOrphaned(): List<OrphanedTake>

    // ── Non-frozen additions used by the slice and workers. Kept below the frozen block on purpose. ──
    fun observeTake(takeId: String): Flow<Take?>
    suspend fun getTake(takeId: String): Take?
    suspend fun markTranscoded(takeId: String, storedRelativePath: String, mimeType: String, byteSize: Long): Result<Unit>
    suspend fun markFailed(takeId: String): Result<Unit>
    suspend fun markOrphaned(takeId: String): Result<Unit>
    suspend fun recoverOrphan(orphan: OrphanedTake, name: String): Result<Take>
    suspend fun hardDeleteOrphan(takeId: String): Result<Unit>
}

interface SongRepository {
    fun observeSongs(filter: SongFilter): Flow<List<Song>>
    fun observeSong(songId: String): Flow<SongDetail?>
    suspend fun create(name: String, adoptTakeId: String? = null): Result<Song>
    suspend fun setStatus(songId: String, status: SongStatus): Result<Unit>
    suspend fun toggleFavorite(songId: String): Result<Unit>
    suspend fun softDelete(songId: String): Result<Unit>

    // ── Non-frozen additions. ──
    suspend fun rename(songId: String, name: String): Result<Unit>
    suspend fun getSong(songId: String): Song?
    suspend fun nameExists(name: String, excludeId: String? = null): Boolean
}

interface SearchEngine {
    fun searchGlobal(query: String): Flow<SearchResults>
    fun searchInSong(songId: String, query: String): Flow<SearchResults>
}

data class SearchResults(
    val songs: List<SongMatch>,
    val lyrics: List<SectionMatch>,
    val notes: List<NoteMatch>,
    val takes: List<TakeMatch>,
) {
    val isEmpty: Boolean get() = songs.isEmpty() && lyrics.isEmpty() && notes.isEmpty() && takes.isEmpty()
    val total: Int get() = songs.size + lyrics.size + notes.size + takes.size

    companion object {
        val EMPTY = SearchResults(emptyList(), emptyList(), emptyList(), emptyList())
    }
}

/** `snippet` carries `<b>…</b>` markers from FTS5 `snippet()`; the UI turns them into highlight spans. */
data class SongMatch(val songId: String, val name: String, val snippet: String, val rank: Double)
data class SectionMatch(val sectionId: String, val songId: String, val label: String, val snippet: String, val rank: Double)
data class NoteMatch(val noteId: String, val songId: String, val snippet: String, val rank: Double)
data class TakeMatch(val takeId: String, val songId: String?, val name: String, val snippet: String, val rank: Double)

// ── Not in §15 but required by Phase 0 §8/§10/§12. Same freeze discipline applies. ──

interface LyricsRepository {
    fun observeDocument(songId: String): Flow<LyricsDocument?>
    suspend fun ensureDocument(songId: String): Result<LyricsDocument>
    suspend fun insertSection(songId: String, tag: SectionTag, customLabel: String?, atIndex: Int?): Result<Section>
    suspend fun updateSectionText(sectionId: String, bodyText: String): Result<Unit>
    suspend fun moveSection(sectionId: String, toIndex: Int): Result<Unit>
    suspend fun deleteSection(sectionId: String): Result<Unit>
}

interface NoteRepository {
    fun observeForSong(songId: String): Flow<List<Note>>
    suspend fun create(draft: NoteDraft): Result<Note>
    suspend fun update(noteId: String, bodyText: String): Result<Unit>
    suspend fun softDelete(noteId: String): Result<Unit>
}

interface NudgeRepository {
    fun observeHistory(): Flow<List<Nudge>>
    suspend fun record(candidate: NudgeCandidate, title: String, body: String): Result<Nudge>
    suspend fun markRead(nudgeId: String): Result<Unit>
    suspend fun snapshot(): NudgeSnapshot
    suspend fun targetExists(songId: String?, takeId: String?): Boolean
}

/** §12.5: reads `AudioAsset.mimeType`, hands every encoder the best available source. */
interface ExportSourceResolver {
    /** Best available source for a take: FLAC if stored lossless, else M4A. Raw PCM if the take is still transcoding. */
    suspend fun resolve(takeId: String): Result<ExportSource>
}

/** §4.3/§4.4 quality mode and its disclosure bookkeeping; §11 nudge toggle; Phase 0 §16 appearance. */
interface SettingsRepository {
    val qualityMode: Flow<QualityMode>
    suspend fun setQualityMode(mode: QualityMode)

    val firstSaveDisclosureDismissed: Flow<Boolean>
    suspend fun dismissFirstSaveDisclosure()
    val exportDisclosureSeen: Flow<Boolean>
    suspend fun markExportDisclosureSeen()

    val nudgesEnabled: Flow<Boolean>
    suspend fun setNudgesEnabled(enabled: Boolean)
    val lastNudgeAtMillis: Flow<Long?>
    suspend fun setLastNudgeAt(millis: Long)

    val themeVariant: Flow<String?>
    suspend fun setThemeVariant(name: String?)
    val hasSavedFirstTake: Flow<Boolean>
    suspend fun markFirstTakeSaved()
}
