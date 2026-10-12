package com.songpad.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.RoomRawQuery
import androidx.room.Update
import com.songpad.core.data.db.entity.AudioAssetEntity
import com.songpad.core.data.db.entity.LyricsDocumentEntity
import com.songpad.core.data.db.entity.MidiAssetEntity
import com.songpad.core.data.db.entity.NoteEntity
import com.songpad.core.data.db.entity.NudgeEntity
import com.songpad.core.data.db.entity.SectionEntity
import com.songpad.core.data.db.entity.SongEntity
import com.songpad.core.data.db.entity.TakeEntity
import kotlinx.coroutines.flow.Flow

// DAO signatures frozen alongside §15 (Prompt 0-B). Every read filters `deleted = 0` (§6.3).

@Dao
interface SongDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(song: SongEntity)
    @Update suspend fun update(song: SongEntity)

    @Query("SELECT * FROM song WHERE id = :id AND deleted = 0") suspend fun get(id: String): SongEntity?
    @Query("SELECT * FROM song WHERE id = :id AND deleted = 0") fun observe(id: String): Flow<SongEntity?>

    @Query("SELECT * FROM song WHERE deleted = 0 ORDER BY updated_at DESC") fun observeAll(): Flow<List<SongEntity>>
    @Query("SELECT * FROM song WHERE deleted = 0 AND status = :status ORDER BY updated_at DESC") fun observeByStatus(status: String): Flow<List<SongEntity>>
    @Query("SELECT * FROM song WHERE deleted = 0 AND is_favorite = 1 ORDER BY updated_at DESC") fun observeFavorites(): Flow<List<SongEntity>>
    @Query("SELECT * FROM song WHERE deleted = 0 AND status != 'ARCHIVED' ORDER BY updated_at DESC") fun observeNotArchived(): Flow<List<SongEntity>>

    @Query("SELECT * FROM song WHERE deleted = 0 AND status = 'ONGOING'") suspend fun ongoing(): List<SongEntity>
    @Query("SELECT id FROM song WHERE deleted = 1 AND deleted_at < :cutoff") suspend fun purgeCandidates(cutoff: Long): List<String>
    @Query("DELETE FROM song WHERE id = :id") suspend fun hardDelete(id: String)
    @Query("SELECT COUNT(*) FROM song WHERE deleted = 0") suspend fun count(): Int
    @Query("SELECT COUNT(*) FROM song WHERE deleted = 0 AND LOWER(name) = LOWER(:name) AND (:excludeId IS NULL OR id != :excludeId)")
    suspend fun nameCount(name: String, excludeId: String? = null): Int
}

@Dao
interface TakeDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(take: TakeEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(take: TakeEntity)
    @Update suspend fun update(take: TakeEntity)

    @Query("SELECT * FROM take WHERE id = :id AND deleted = 0") suspend fun get(id: String): TakeEntity?
    @Query("SELECT * FROM take WHERE id = :id") suspend fun getIncludingDeleted(id: String): TakeEntity?
    @Query("SELECT * FROM take WHERE id = :id AND deleted = 0") fun observe(id: String): Flow<TakeEntity?>
    @Query("SELECT * FROM take WHERE song_id = :songId AND deleted = 0 ORDER BY created_at DESC") fun observeForSong(songId: String): Flow<List<TakeEntity>>
    @Query("SELECT * FROM take WHERE song_id IS NULL AND deleted = 0 ORDER BY created_at DESC") fun observeUnassigned(): Flow<List<TakeEntity>>
    @Query("SELECT * FROM take WHERE song_id = :songId AND deleted = 0 ORDER BY created_at DESC") suspend fun forSong(songId: String): List<TakeEntity>
    @Query("SELECT * FROM take WHERE song_id IS NULL AND deleted = 0") suspend fun unassigned(): List<TakeEntity>

    /** §6.2/§9.4: a RECORDING row at cold start is orphaned by definition. */
    @Query("SELECT * FROM take WHERE status = 'RECORDING' AND deleted = 0") suspend fun inFlight(): List<TakeEntity>
    @Query("SELECT * FROM take WHERE status = 'ORPHANED' AND deleted = 0") suspend fun orphaned(): List<TakeEntity>
    @Query("SELECT id FROM take") suspend fun allIdsIncludingDeleted(): List<String>

    @Query("SELECT id FROM take WHERE deleted = 1 AND deleted_at < :cutoff") suspend fun purgeCandidates(cutoff: Long): List<String>
    @Query("DELETE FROM take WHERE id = :id") suspend fun hardDelete(id: String)
    @Query("SELECT COUNT(*) FROM take WHERE deleted = 0") suspend fun count(): Int
    @Query("SELECT COUNT(*) FROM take WHERE song_id = :songId AND deleted = 0") suspend fun countForSong(songId: String): Int
}

@Dao
interface AudioAssetDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(asset: AudioAssetEntity)
    @Update suspend fun update(asset: AudioAssetEntity)
    @Query("SELECT * FROM audio_asset WHERE take_id = :takeId AND deleted = 0") suspend fun forTake(takeId: String): AudioAssetEntity?
    @Query("SELECT * FROM audio_asset WHERE take_id IN (:takeIds) AND deleted = 0") suspend fun forTakes(takeIds: List<String>): List<AudioAssetEntity>
    @Query("SELECT * FROM audio_asset WHERE take_id = :takeId AND deleted = 0") fun observeForTake(takeId: String): Flow<AudioAssetEntity?>
    @Query("SELECT DISTINCT take_id FROM audio_asset") suspend fun allTakeIdsIncludingDeleted(): List<String>
}

@Dao
interface MidiAssetDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(asset: MidiAssetEntity)
    @Query("SELECT * FROM midi_asset WHERE take_id = :takeId AND deleted = 0") suspend fun forTake(takeId: String): MidiAssetEntity?
    @Query("SELECT * FROM midi_asset WHERE take_id IN (:takeIds) AND deleted = 0") suspend fun forTakes(takeIds: List<String>): List<MidiAssetEntity>
    @Query("SELECT DISTINCT take_id FROM midi_asset") suspend fun allTakeIdsIncludingDeleted(): List<String>
}

@Dao
interface LyricsDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertDocument(doc: LyricsDocumentEntity)
    @Update suspend fun updateDocument(doc: LyricsDocumentEntity)
    @Query("SELECT * FROM lyrics_document WHERE song_id = :songId AND deleted = 0") suspend fun documentForSong(songId: String): LyricsDocumentEntity?
    @Query("SELECT * FROM lyrics_document WHERE song_id = :songId AND deleted = 0") fun observeDocument(songId: String): Flow<LyricsDocumentEntity?>

    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertSection(section: SectionEntity)
    @Update suspend fun updateSection(section: SectionEntity)
    @Update suspend fun updateSections(sections: List<SectionEntity>)
    /** Touches only `sort_order`/`number` — used by reorder so it can never clobber a concurrent bodyText
     * autosave that's still in its debounce window (§21 no-destructive-editing). `number` must move with
     * `sort_order`: [SectionOrdering.renumber] recomputes display numbering ("Verse 1"/"Verse 2") from position. */
    @Query("UPDATE section SET sort_order = :sortOrder, number = :number WHERE id = :id") suspend fun updateSortOrder(id: String, sortOrder: Int, number: Int?)
    @Query("SELECT * FROM section WHERE id = :id AND deleted = 0") suspend fun section(id: String): SectionEntity?
    @Query("SELECT * FROM section WHERE document_id = :documentId AND deleted = 0 ORDER BY sort_order ASC") suspend fun sections(documentId: String): List<SectionEntity>
    @Query("SELECT * FROM section WHERE document_id = :documentId AND deleted = 0 ORDER BY sort_order ASC") fun observeSections(documentId: String): Flow<List<SectionEntity>>

    /** §11.2 rule: ongoing songs with at least one empty section. */
    @Query(
        """SELECT DISTINCT s.* FROM song s
           JOIN lyrics_document d ON d.song_id = s.id AND d.deleted = 0
           JOIN section sec ON sec.document_id = d.id AND sec.deleted = 0
           WHERE s.deleted = 0 AND s.status = 'ONGOING' AND TRIM(sec.body_text) = ''""",
    )
    suspend fun ongoingSongsWithEmptySection(): List<SongEntity>

    @Query("SELECT COUNT(*) FROM section sec JOIN lyrics_document d ON d.id = sec.document_id WHERE d.song_id = :songId AND sec.deleted = 0 AND TRIM(sec.body_text) != ''")
    suspend fun nonEmptySectionCount(songId: String): Int
}

@Dao
interface NoteDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(note: NoteEntity)
    @Update suspend fun update(note: NoteEntity)
    @Query("SELECT * FROM note WHERE id = :id AND deleted = 0") suspend fun get(id: String): NoteEntity?
    @Query("SELECT * FROM note WHERE song_id = :songId AND deleted = 0 ORDER BY created_at ASC") fun observeForSong(songId: String): Flow<List<NoteEntity>>
    @Query("SELECT * FROM note WHERE song_id = :songId AND deleted = 0 ORDER BY created_at ASC") suspend fun forSong(songId: String): List<NoteEntity>
    @Query("SELECT id FROM note WHERE deleted = 1 AND deleted_at < :cutoff") suspend fun purgeCandidates(cutoff: Long): List<String>
    @Query("DELETE FROM note WHERE id = :id") suspend fun hardDelete(id: String)
    @Query("SELECT id FROM note") suspend fun allIdsIncludingDeleted(): List<String>
}

@Dao
interface NudgeDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(nudge: NudgeEntity)
    @Update suspend fun update(nudge: NudgeEntity)
    @Query("SELECT * FROM nudge WHERE id = :id AND deleted = 0") suspend fun get(id: String): NudgeEntity?
    @Query("SELECT * FROM nudge WHERE deleted = 0 ORDER BY created_at DESC LIMIT 200") fun observeHistory(): Flow<List<NudgeEntity>>
    @Query("SELECT DISTINCT target_song_id FROM nudge WHERE deleted = 0 AND target_song_id IS NOT NULL AND created_at > :since") suspend fun recentlyNudgedSongIds(since: Long): List<String>
    @Query("SELECT MAX(created_at) FROM nudge WHERE deleted = 0") suspend fun lastNudgeAt(): Long?
}

/** One row of the §10.1 query. */
data class SearchRow(
    val entity_id: String,
    val song_id: String?,
    val entity_type: String,
    val search_title: String,
    val snippet_text: String,
    val rank: Double,
)

@Dao
interface SearchDao {
    /** The FTS virtual table is unknown to Room's verifier, hence @RawQuery; observedEntities keep the Flow reactive. */
    @RawQuery(observedEntities = [SongEntity::class, SectionEntity::class, NoteEntity::class, TakeEntity::class])
    fun search(query: RoomRawQuery): Flow<List<SearchRow>>

    @RawQuery(observedEntities = [SongEntity::class, SectionEntity::class, NoteEntity::class, TakeEntity::class])
    suspend fun searchOnce(query: RoomRawQuery): List<SearchRow>
}
