package com.songpad.core.audio.capture

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.ShortBuffer

/** Thin Kotlin facade over the C++ CaptureEngine (§4.1 "wrapped by a thin Kotlin facade"). One handle per instance. */
class NativeCapture : AutoCloseable {
    private var handle: Long = nativeCreate()

    val isOpen: Boolean get() = handle != 0L

    fun open(): Int = nativeOpen(handle)
    fun start(): Int = nativeStart(handle)
    fun pause(): Int = nativePause(handle)
    fun closeStream() = nativeClose(handle)

    /** Drains into a direct buffer allocated ONCE by the writer thread. Returns samples read. */
    fun drain(buffer: ShortBuffer, maxSamples: Int): Int = nativeDrain(handle, buffer, maxSamples)
    fun available(): Int = nativeAvailable(handle)
    fun overruns(): Long = nativeOverruns(handle)
    fun framesCaptured(): Long = nativeFramesCaptured(handle)
    fun timestamp(): LongArray? = nativeTimestamp(handle)
    fun errorOccurred(): Boolean = nativeErrorOccurred(handle)

    data class StreamInfo(val framesPerBurst: Int, val bufferCapacity: Int, val bufferSize: Int, val lowLatency: Boolean, val aaudio: Boolean, val xruns: Int)

    fun streamInfo(): StreamInfo = nativeStreamInfo(handle).let { StreamInfo(it[0], it[1], it[2], it[3] == 1, it[4] == 1, it[5]) }

    override fun close() {
        if (handle != 0L) { nativeDestroy(handle); handle = 0L }
    }

    private external fun nativeCreate(): Long
    private external fun nativeDestroy(handle: Long)
    private external fun nativeOpen(handle: Long): Int
    private external fun nativeStart(handle: Long): Int
    private external fun nativePause(handle: Long): Int
    private external fun nativeClose(handle: Long)
    private external fun nativeDrain(handle: Long, buffer: ShortBuffer, maxSamples: Int): Int
    private external fun nativeAvailable(handle: Long): Int
    private external fun nativeOverruns(handle: Long): Long
    private external fun nativeFramesCaptured(handle: Long): Long
    private external fun nativeTimestamp(handle: Long): LongArray?
    private external fun nativeErrorOccurred(handle: Long): Boolean
    private external fun nativeStreamInfo(handle: Long): IntArray

    companion object {
        init { System.loadLibrary("songpad_audio") }

        /** Direct, native-order buffer for the JNI drain path. */
        fun allocateDrainBuffer(samples: Int): ShortBuffer = ByteBuffer.allocateDirect(samples * 2).order(ByteOrder.nativeOrder()).asShortBuffer()
    }
}
