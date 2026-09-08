package com.songpad.core.audio.storage

import android.os.StatFs
import com.songpad.core.model.take.TakeFileStore
import com.songpad.core.model.usecase.StorageProbe
import javax.inject.Inject

/** Free bytes on the app-private files volume (§7.4 low-storage row). */
class AndroidStorageProbe @Inject constructor(private val files: TakeFileStore) : StorageProbe {
    override fun freeBytes(): Long = runCatching { StatFs(files.root.path).availableBytes }.getOrDefault(Long.MAX_VALUE)
}
