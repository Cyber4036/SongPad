package com.songpad.core.data.db

import androidx.room.RoomDatabase
import androidx.room.immediateTransaction
import androidx.room.useWriterConnection

/**
 * Executes [block] in an immediate transaction using Room 2.8's BundledSQLiteDriver transactor.
 * Compatible with [androidx.sqlite.driver.bundled.BundledSQLiteDriver] where legacy [androidx.room.withTransaction]
 * fails due to lack of a SupportSQLiteOpenHelper.
 */
suspend fun <R> RoomDatabase.withTransactionCompat(block: suspend () -> R): R =
    useWriterConnection { transactor ->
        transactor.immediateTransaction {
            block()
        }
    }
