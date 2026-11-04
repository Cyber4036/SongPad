package com.songpad.core.model.lyrics

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Represents a local, immediately-persisted draft of a lyric [Section]'s text as it is being typed.
 */
data class SectionDraft(
    val sectionId: String,
    val bodyText: String,
    val updatedAtMillis: Long,
)

/**
 * Synchronous local draft store that persists in-flight lyric edits immediately on every keystroke,
 * preventing data loss if the app process is interrupted before debounced database writes complete.
 */
interface LyricsDraftStore {
    val drafts: StateFlow<Map<String, SectionDraft>>

    /** Immediately persists [bodyText] for [sectionId] in local draft state. */
    fun putDraft(sectionId: String, bodyText: String, updatedAtMillis: Long)

    /** Returns the active local draft for [sectionId], or null if none is pending. */
    fun getDraft(sectionId: String): SectionDraft?

    /** Removes the local draft for [sectionId] once committed or deleted. */
    fun removeDraft(sectionId: String)

    /** Removes local drafts for all [sectionIds]. */
    fun removeDrafts(sectionIds: Collection<String>)

    /** Clears all local drafts. */
    fun clear()
}

/**
 * Fast in-memory implementation of [LyricsDraftStore] suitable for unit tests and fallback wiring.
 */
class InMemoryLyricsDraftStore(
    private val backing: MutableStateFlow<Map<String, SectionDraft>> = MutableStateFlow(emptyMap()),
) : LyricsDraftStore {
    override val drafts: StateFlow<Map<String, SectionDraft>> = backing.asStateFlow()

    override fun putDraft(sectionId: String, bodyText: String, updatedAtMillis: Long) {
        backing.update { current ->
            current + (sectionId to SectionDraft(sectionId, bodyText, updatedAtMillis))
        }
    }

    override fun getDraft(sectionId: String): SectionDraft? = backing.value[sectionId]

    override fun removeDraft(sectionId: String) {
        backing.update { current -> current - sectionId }
    }

    override fun removeDrafts(sectionIds: Collection<String>) {
        if (sectionIds.isEmpty()) return
        val toRemove = sectionIds.toSet()
        backing.update { current -> current - toRemove }
    }

    override fun clear() {
        backing.value = emptyMap()
    }
}

/**
 * Disk-backed [LyricsDraftStore] that atomically writes every keystroke draft to [rootDir]/lyrics_drafts.bin.
 * Survives abrupt process death (`kill -9`, crash, or system eviction) and reloads pending drafts on cold start.
 */
class FileLyricsDraftStore(
    private val rootDir: File,
) : LyricsDraftStore {
    private val lock = Any()
    private val file = File(rootDir, FILE_NAME)
    private val tmpFile = File(rootDir, "$FILE_NAME.tmp")
    private val _drafts = MutableStateFlow<Map<String, SectionDraft>>(emptyMap())
    override val drafts: StateFlow<Map<String, SectionDraft>> = _drafts.asStateFlow()

    init {
        synchronized(lock) {
            _drafts.value = readFromDisk()
        }
    }

    override fun putDraft(sectionId: String, bodyText: String, updatedAtMillis: Long) {
        synchronized(lock) {
            val next = _drafts.value + (sectionId to SectionDraft(sectionId, bodyText, updatedAtMillis))
            _drafts.value = next
            writeToDisk(next)
        }
    }

    override fun getDraft(sectionId: String): SectionDraft? = _drafts.value[sectionId]

    override fun removeDraft(sectionId: String) {
        synchronized(lock) {
            if (sectionId !in _drafts.value) return
            val next = _drafts.value - sectionId
            _drafts.value = next
            writeToDisk(next)
        }
    }

    override fun removeDrafts(sectionIds: Collection<String>) {
        if (sectionIds.isEmpty()) return
        synchronized(lock) {
            val toRemove = sectionIds.toSet()
            if (_drafts.value.keys.none { it in toRemove }) return
            val next = _drafts.value - toRemove
            _drafts.value = next
            writeToDisk(next)
        }
    }

    override fun clear() {
        synchronized(lock) {
            _drafts.value = emptyMap()
            runCatching {
                if (tmpFile.exists()) tmpFile.delete()
                if (file.exists()) file.delete()
            }
        }
    }

    private fun readFromDisk(): Map<String, SectionDraft> {
        val source = when {
            file.exists() -> file
            tmpFile.exists() -> tmpFile
            else -> return emptyMap()
        }
        return runCatching {
            DataInputStream(FileInputStream(source).buffered()).use { input ->
                val magic = input.readInt()
                if (magic != MAGIC_HEADER) return emptyMap()
                val count = input.readInt().coerceIn(0, MAX_DRAFTS)
                val map = LinkedHashMap<String, SectionDraft>(count)
                repeat(count) {
                    val sectionId = input.readUTF()
                    val updatedAt = input.readLong()
                    val bytesLen = input.readInt().coerceIn(0, MAX_TEXT_BYTES)
                    val bytes = ByteArray(bytesLen)
                    input.readFully(bytes)
                    val bodyText = bytes.decodeToString()
                    map[sectionId] = SectionDraft(sectionId, bodyText, updatedAt)
                }
                map
            }
        }.getOrDefault(emptyMap())
    }

    private fun writeToDisk(map: Map<String, SectionDraft>) {
        runCatching {
            if (!rootDir.exists()) rootDir.mkdirs()
            if (map.isEmpty()) {
                if (tmpFile.exists()) tmpFile.delete()
                if (file.exists()) file.delete()
                return
            }
            DataOutputStream(FileOutputStream(tmpFile).buffered()).use { output ->
                output.writeInt(MAGIC_HEADER)
                output.writeInt(map.size)
                for (draft in map.values) {
                    output.writeUTF(draft.sectionId)
                    output.writeLong(draft.updatedAtMillis)
                    val bytes = draft.bodyText.encodeToByteArray()
                    output.writeInt(bytes.size)
                    output.write(bytes)
                }
                output.flush()
            }
            if (!tmpFile.renameTo(file)) {
                tmpFile.copyTo(file, overwrite = true)
                tmpFile.delete()
            }
        }
    }

    companion object {
        private const val FILE_NAME = "lyrics_drafts.bin"
        private const val MAGIC_HEADER = 0x53504452 // "SPDR"
        private const val MAX_DRAFTS = 10_000
        private const val MAX_TEXT_BYTES = 2 * 1024 * 1024
    }
}

/**
 * Overlays any uncommitted local [SectionDraft]s onto this [LyricsDocument] so observers and UI
 * immediately see the latest typed characters even before the database write completes or after a process restart.
 */
fun LyricsDocument.withDrafts(drafts: Map<String, SectionDraft>): LyricsDocument {
    if (drafts.isEmpty() || sections.isEmpty()) return this
    var changed = false
    var latestUpdate = meta.updatedAt
    val mergedSections = sections.map { sec ->
        val draft = drafts[sec.id]
        if (draft != null && draft.bodyText != sec.bodyText) {
            changed = true
            val ts = maxOf(sec.meta.updatedAt, draft.updatedAtMillis)
            if (ts > latestUpdate) latestUpdate = ts
            sec.copy(bodyText = draft.bodyText, meta = sec.meta.copy(updatedAt = ts))
        } else {
            sec
        }
    }
    return if (!changed) this else copy(
        sections = mergedSections,
        meta = meta.copy(updatedAt = latestUpdate),
    )
}
