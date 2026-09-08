package com.songpad.core.audio.capture

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.ShortBuffer

/**
 * §7.3 durability: headerless raw PCM appended to `audio.pcm.tmp`; `flush()` + `FileDescriptor.sync()` on
 * roughly every second of audio, not every buffer. Pure JVM so the cadence and byte accounting are unit-tested.
 * Runs on the writer thread only.
 */
class PcmWriter(
    private val file: File,
    private val syncEverySamples: Int = SYNC_EVERY_SAMPLES_DEFAULT,
) {
    private var fileOut: FileOutputStream? = null
    private var out: BufferedOutputStream? = null
    private var sinceSync = 0
    private val scratch = ByteBuffer.allocate(SCRATCH_SAMPLES * 2).order(ByteOrder.LITTLE_ENDIAN)

    /** Total samples durably written (through the buffered stream; synced count is [samplesSynced]). */
    var samplesWritten: Long = 0; private set
    var samplesSynced: Long = 0; private set
    var syncCount: Int = 0; private set

    fun open(append: Boolean = true) {
        file.parentFile?.mkdirs()
        fileOut = FileOutputStream(file, append)
        out = BufferedOutputStream(fileOut, 64 * 1024)
        samplesWritten = if (append) file.length() / 2 else 0
        samplesSynced = samplesWritten
        sinceSync = 0
    }

    /** Writes [count] samples from [src] (position 0). Little-endian 16-bit, the raw PCM layout Media3 expects. */
    fun write(src: ShortBuffer, count: Int): Boolean {
        val o = out ?: return false
        var remaining = count
        var offset = 0
        while (remaining > 0) {
            val n = minOf(remaining, SCRATCH_SAMPLES)
            scratch.clear()
            for (i in 0 until n) scratch.putShort(src.get(offset + i))
            o.write(scratch.array(), 0, n * 2)
            offset += n; remaining -= n
        }
        samplesWritten += count
        sinceSync += count
        var synced = false
        if (sinceSync >= syncEverySamples) { sync(); synced = true }
        return synced
    }

    /** flush + fsync. Called on cadence, on pause, on interruption and on stop — never on the capture thread. */
    fun sync() {
        out?.flush()
        fileOut?.fd?.sync()
        samplesSynced = samplesWritten
        sinceSync = 0
        syncCount++
    }

    fun close() {
        sync()
        out?.close()
        out = null; fileOut = null
    }

    companion object {
        /** ~1 s of audio at 48 kHz mono. */
        const val SYNC_EVERY_SAMPLES_DEFAULT = 48_000
        private const val SCRATCH_SAMPLES = 4_096
    }
}
