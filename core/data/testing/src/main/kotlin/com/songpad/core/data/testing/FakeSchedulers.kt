package com.songpad.core.data.testing

import com.songpad.core.model.usecase.StorageProbe
import com.songpad.core.model.usecase.TranscodeScheduler

class FakeTranscodeScheduler : TranscodeScheduler {
    val enqueued = mutableListOf<String>()
    override fun enqueue(takeId: String) { enqueued += takeId }
}

class FakeStorageProbe(var freeBytes: Long = 10L * 1024 * 1024 * 1024) : StorageProbe {
    override fun freeBytes(): Long = freeBytes
}
