package com.songpad.core.data.repo

import com.songpad.core.data.db.withTransactionCompat
import com.songpad.core.data.db.SongPadDatabase
import com.songpad.core.data.db.entity.AudioAssetEntity
import com.songpad.core.data.db.entity.MidiAssetEntity
import com.songpad.core.data.db.entity.SongEntity
import com.songpad.core.data.db.entity.TakeEntity
import com.songpad.core.model.take.TakeFileStore
import com.songpad.core.model.data.TakeRepository
import com.songpad.core.model.error.DataError
import com.songpad.core.model.error.domainFailure
import com.songpad.core.model.error.runDomain
import com.songpad.core.model.id.IdGenerator
import com.songpad.core.model.song.SongStatus
import com.songpad.core.model.take.AudioFormat
import com.songpad.core.model.take.OrphanedTake
import com.songpad.core.model.take.Take
import com.songpad.core.model.take.TakeDraft
import com.songpad.core.model.take.TakePaths
import com.songpad.core.model.take.TakeStatus
import com.songpad.core.model.take.TakeType
import com.songpad.core.model.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TakeRepositoryImpl @Inject constructor(
    private val db: SongPadDatabase,
    private val files: TakeFileStore,
    private val ids: IdGenerator,
    private val clock: Clock,
) : TakeRepository {

    private val takes get() = db.takeDao()
    private val audio get() = db.audioAssetDao()
    private val midi get() = db.midiAssetDao()

    override fun observeForSong(songId: String): Flow<List<Take>> = takes.observeForSong(songId).withAssets()
    override fun observeUnassigned(): Flow<List<Take>> = takes.observeUnassigned().withAssets()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    override fun observeTake(takeId: String): Flow<Take?> = takes.observe(takeId).flatMapLatest { t ->
        if (t == null) flowOf(null)
        else audio.observeForTake(takeId).map { a -> t.toDomain(a, midi.forTake(takeId)) }
    }

    override suspend fun getTake(takeId: String): Take? = takes.get(takeId)?.let { it.toDomain(audio.forTake(takeId), midi.forTake(takeId)) }

    /** §16 / Prompt 2-B: Song + Take + AudioAsset (+ MidiAsset) as ONE transaction, status = TRANSCODING. */
    override suspend fun commit(draft: TakeDraft): Result<Take> = runDomain({ DataError.Io(it.message ?: "commit failed") }) {
        val now = clock.nowMillis()
        db.withTransactionCompat {
            var songId = draft.songId
            if (draft.newSongName != null) {
                val song = SongEntity(ids.newId(), draft.newSongName!!, SongStatus.ONGOING.name, 0, newSync(now))
                db.songDao().insert(song)
                songId = song.id
            } else if (songId != null) {
                db.songDao().get(songId) ?: throw com.songpad.core.model.error.DomainException(DataError.NotFound("Song", songId))
                db.songDao().get(songId)?.let { db.songDao().update(it.copy(sync = it.sync.touched(now))) }
            }
            val audioEntity = AudioAssetEntity(
                id = ids.newId(), takeId = draft.takeId, relativePath = draft.audio.relativePath, mimeType = AudioFormat.MIME_RAW_PCM,
                sampleRate = AudioFormat.SAMPLE_RATE, bitDepth = AudioFormat.BIT_DEPTH, byteSize = draft.audio.byteSize,
                peaksPath = draft.audio.peaksPath, sync = newSync(now),
            )
            val midiEntity = draft.midi?.let { MidiAssetEntity(it.id, draft.takeId, it.relativePath, it.ppq, it.byteSize, it.eventCount, newSync(now)) }
            val take = TakeEntity(
                id = draft.takeId, songId = songId, name = draft.name, type = draft.type.name, status = TakeStatus.TRANSCODING.name,
                durationMs = draft.audio.durationMs, audioAssetId = audioEntity.id, midiAssetId = midiEntity?.id, sync = newSync(now),
            )
            takes.upsert(take) // upsert: a RECORDING placeholder row may already exist for this id
            audio.upsert(audioEntity)
            midiEntity?.let { midi.upsert(it) }
            take.toDomain(audioEntity, midiEntity)
        }
    }

    override suspend fun assignToSong(takeId: String, songId: String): Result<Unit> = mutate(takeId) { it.copy(songId = songId) }
    override suspend fun rename(takeId: String, name: String): Result<Unit> = mutate(takeId) { it.copy(name = name) }

    override suspend fun softDelete(takeId: String): Result<Unit> = runDomain({ DataError.Io(it.message ?: "") }) {
        val t = takes.get(takeId) ?: throw com.songpad.core.model.error.DomainException(DataError.NotFound("Take", takeId))
        takes.update(t.copy(sync = t.sync.softDeleted(clock.nowMillis())))
    }

    /** §9.4 steps 1–4: RECORDING rows plus rowless `.pcm.tmp` directories, noise discarded. */
    override suspend fun findOrphaned(): List<OrphanedTake> {
        val known = mutableMapOf<String, OrphanedTake>()
        (takes.inFlight() + takes.orphaned()).forEach { t ->
            val tmp = files.file(TakePaths.pcmTmp(t.id))
            val pcm = files.file(TakePaths.pcm(t.id))
            val f = if (tmp.exists()) tmp else if (pcm.exists()) pcm else return@forEach
            known[t.id] = OrphanedTake(t.id, TakePaths.pcmTmp(t.id), f.length(), hasDbRow = true, hasEventsLog = files.exists(TakePaths.eventsLog(t.id)), songId = t.songId)
        }
        val rowIds = takes.allIdsIncludingDeleted().toSet()
        files.listTakeDirs().forEach { dir ->
            val id = dir.name
            if (id in rowIds || id in known) return@forEach
            val tmp = File(dir, TakePaths.AUDIO_PCM_TMP)
            if (tmp.exists()) {
                known[id] = OrphanedTake(id, TakePaths.pcmTmp(id), tmp.length(), hasDbRow = false, hasEventsLog = File(dir, TakePaths.EVENTS_LOG).exists(), songId = null)
            }
        }
        return known.values.filter { !it.isNoise }
    }

    override suspend fun markTranscoded(takeId: String, storedRelativePath: String, mimeType: String, byteSize: Long): Result<Unit> =
        runDomain({ DataError.Io(it.message ?: "") }) {
            val now = clock.nowMillis()
            db.withTransactionCompat {
                val t = takes.get(takeId) ?: throw com.songpad.core.model.error.DomainException(DataError.NotFound("Take", takeId))
                takes.update(t.copy(status = TakeStatus.READY.name, sync = t.sync.touched(now)))
                audio.forTake(takeId)?.let { audio.update(it.copy(relativePath = storedRelativePath, mimeType = mimeType, byteSize = byteSize, sync = it.sync.touched(now))) }
            }
        }

    override suspend fun markFailed(takeId: String): Result<Unit> = mutate(takeId) { it.copy(status = TakeStatus.FAILED.name) }
    override suspend fun markOrphaned(takeId: String): Result<Unit> = mutate(takeId) { it.copy(status = TakeStatus.ORPHANED.name) }

    /** §9.4 step 5 "Keep": rename `.tmp` → `.pcm`, write/repair rows. The MIDI sidecar is converted by :core:midi's recovery. */
    override suspend fun recoverOrphan(orphan: OrphanedTake, name: String): Result<Take> = runDomain({ DataError.Io(it.message ?: "recovery failed") }) {
        if (!files.commitPcm(orphan.takeId)) throw com.songpad.core.model.error.DomainException(DataError.Io("Could not commit the recovered audio file."))
        val pcm = files.file(TakePaths.pcm(orphan.takeId))
        val now = clock.nowMillis()
        db.withTransactionCompat {
            val existing = takes.getIncludingDeleted(orphan.takeId)
            val frames = AudioFormat.framesForBytes(pcm.length())
            val audioEntity = audio.forTake(orphan.takeId)?.copy(relativePath = TakePaths.pcm(orphan.takeId), byteSize = pcm.length(), mimeType = AudioFormat.MIME_RAW_PCM, sync = newSync(now))
                ?: AudioAssetEntity(ids.newId(), orphan.takeId, TakePaths.pcm(orphan.takeId), AudioFormat.MIME_RAW_PCM, AudioFormat.SAMPLE_RATE, AudioFormat.BIT_DEPTH, pcm.length(), null, newSync(now))
            val take = (existing ?: TakeEntity(orphan.takeId, orphan.songId, name, TakeType.VOICE.name, TakeStatus.TRANSCODING.name, 0, null, null, newSync(now)))
                .copy(status = TakeStatus.TRANSCODING.name, durationMs = AudioFormat.framesToMs(frames), audioAssetId = audioEntity.id, sync = (existing?.sync ?: newSync(now)).copy(deleted = 0, deletedAt = null).touched(now))
            takes.upsert(take)
            audio.upsert(audioEntity)
            take.toDomain(audioEntity, midi.forTake(orphan.takeId))
        }
    }

    override suspend fun hardDeleteOrphan(takeId: String): Result<Unit> = runDomain({ DataError.Io(it.message ?: "") }) {
        db.withTransactionCompat { takes.hardDelete(takeId) }
        files.deleteTakeFiles(takeId)
        Unit
    }

    override suspend fun duplicate(takeId: String): Result<Take> = runDomain({ DataError.Io(it.message ?: "duplicate failed") }) {
        val src = takes.get(takeId) ?: throw com.songpad.core.model.error.DomainException(DataError.NotFound("Take", takeId))
        val srcAudio = audio.forTake(takeId)
        val srcMidi = midi.forTake(takeId)
        val newTakeId = ids.newId()
        val now = clock.nowMillis()
        files.takeDir(newTakeId)

        val newAudio = srcAudio?.let { a ->
            val srcFile = files.file(a.relativePath)
            val extFileName = srcFile.name
            val dstRel = "${TakePaths.takeDir(newTakeId)}/$extFileName"
            if (srcFile.exists()) srcFile.copyTo(files.file(dstRel), overwrite = true)
            val dstPeaksRel = a.peaksPath?.let { p ->
                val pf = files.file(p)
                if (pf.exists()) {
                    val np = TakePaths.peaks(newTakeId)
                    pf.copyTo(files.file(np), overwrite = true)
                    np
                } else null
            }
            AudioAssetEntity(ids.newId(), newTakeId, dstRel, a.mimeType, a.sampleRate, a.bitDepth, a.byteSize, dstPeaksRel, newSync(now))
        }
        val newMidi = srcMidi?.let { m ->
            val srcFile = files.file(m.relativePath)
            val dstRel = TakePaths.midi(newTakeId)
            if (srcFile.exists()) srcFile.copyTo(files.file(dstRel), overwrite = true)
            MidiAssetEntity(ids.newId(), newTakeId, dstRel, m.ppq, m.byteSize, m.eventCount, newSync(now))
        }
        val copyTake = TakeEntity(
            id = newTakeId, songId = src.songId, name = "${src.name} (copy)", type = src.type, status = src.status,
            durationMs = src.durationMs, audioAssetId = newAudio?.id, midiAssetId = newMidi?.id, sync = newSync(now),
        )
        db.withTransactionCompat {
            takes.insert(copyTake)
            newAudio?.let { audio.upsert(it) }
            newMidi?.let { midi.upsert(it) }
            src.songId?.let { sId -> db.songDao().get(sId)?.let { db.songDao().update(it.copy(sync = it.sync.touched(now))) } }
        }
        copyTake.toDomain(newAudio, newMidi)
    }

    override suspend fun nextDefaultName(type: TakeType): String =
        com.songpad.core.model.usecase.AutoIncrementNaming.nextName(
            com.songpad.core.model.usecase.AutoIncrementNaming.takePrefix(type),
            takes.allActiveNames(),
        )

    override suspend fun nextDefaultNameForPrefix(prefix: String): String =
        com.songpad.core.model.usecase.AutoIncrementNaming.nextName(prefix, takes.allActiveNames())

    private suspend fun mutate(takeId: String, f: (TakeEntity) -> TakeEntity): Result<Unit> = runDomain({ DataError.Io(it.message ?: "") }) {
        val t = takes.get(takeId) ?: throw com.songpad.core.model.error.DomainException(DataError.NotFound("Take", takeId))
        takes.update(f(t).copy(sync = t.sync.touched(clock.nowMillis())))
    }

    private fun Flow<List<TakeEntity>>.withAssets(): Flow<List<Take>> = map { list ->
        if (list.isEmpty()) return@map emptyList()
        val idsList = list.map { it.id }
        val audios = audio.forTakes(idsList).associateBy { it.takeId }
        val midis = midi.forTakes(idsList).associateBy { it.takeId }
        list.map { it.toDomain(audios[it.id], midis[it.id]) }
    }

    @Suppress("unused")
    private fun combineSanity() = combine(flowOf(1), flowOf(2)) { a, b -> a + b }
}
