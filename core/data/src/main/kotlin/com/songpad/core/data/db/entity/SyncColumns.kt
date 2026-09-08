package com.songpad.core.data.db.entity

import androidx.room.ColumnInfo

/**
 * §6.3 sync-ready block, embedded in every user-data table. Written in V1, READ BY NOTHING. Stays
 * `LOCAL_ONLY`. Do not build sync behavior on top of it.
 */
data class SyncColumns(
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "deleted", defaultValue = "0") val deleted: Int = 0,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
    @ColumnInfo(name = "sync_state", defaultValue = "'LOCAL_ONLY'") val syncState: String = "LOCAL_ONLY",
    @ColumnInfo(name = "revision", defaultValue = "1") val revision: Long = 1,
)
