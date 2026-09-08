package com.songpad.core.model.sync

/** §6.3. In V1 every row is LOCAL_ONLY; the other values exist so the schema never needs a migration to grow sync. */
enum class SyncState { LOCAL_ONLY, PENDING_UPLOAD, SYNCED, PENDING_DELETE }

/** The five-column sync block carried by every user-data row (§6.3). Written in V1, read by nothing. */
data class SyncMeta(
    val createdAt: Long,
    val updatedAt: Long,
    val deleted: Boolean = false,
    val deletedAt: Long? = null,
    val syncState: SyncState = SyncState.LOCAL_ONLY,
    val revision: Long = 1,
) {
    fun touched(now: Long): SyncMeta = copy(updatedAt = now, revision = revision + 1)
    fun softDeleted(now: Long): SyncMeta = copy(updatedAt = now, deleted = true, deletedAt = now, revision = revision + 1)

    companion object {
        fun create(now: Long) = SyncMeta(createdAt = now, updatedAt = now)
    }
}
