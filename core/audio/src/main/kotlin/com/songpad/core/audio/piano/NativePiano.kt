package com.songpad.core.audio.piano

/** Thin Kotlin facade over the C++ PianoOutputStream (mirrors NativeCapture). One handle per instance. */
class NativePiano : AutoCloseable {
    private var handle: Long = nativeCreate()

    fun open(): Int = nativeOpen(handle)
    fun start(): Int = nativeStart(handle)
    fun stop(): Int = nativeStop(handle)

    fun loadRegion(lokey: Int, hikey: Int, rootNote: Int, looped: Boolean, loopStartFrame: Long, loopEndFrame: Long, pcm: ShortArray) =
        nativeLoadRegion(handle, lokey, hikey, rootNote, looped, loopStartFrame, loopEndFrame, pcm)

    fun noteOn(note: Int, velocity: Int) = nativeNoteOn(handle, note, velocity)
    fun noteOff(note: Int) = nativeNoteOff(handle, note)
    fun allNotesOff() = nativeAllNotesOff(handle)

    /** ADR-0035: mix the sampler's output into the capture stream (process-wide; see sampler_tap.h). */
    fun setCaptureMix(enabled: Boolean) = nativeSetCaptureMix(enabled)

    override fun close() {
        if (handle != 0L) { nativeClose(handle); nativeDestroy(handle); handle = 0L }
    }

    private external fun nativeCreate(): Long
    private external fun nativeDestroy(handle: Long)
    private external fun nativeOpen(handle: Long): Int
    private external fun nativeStart(handle: Long): Int
    private external fun nativeStop(handle: Long): Int
    private external fun nativeClose(handle: Long)
    private external fun nativeLoadRegion(handle: Long, lokey: Int, hikey: Int, rootNote: Int, looped: Boolean, loopStartFrame: Long, loopEndFrame: Long, pcm: ShortArray)
    private external fun nativeNoteOn(handle: Long, note: Int, velocity: Int)
    private external fun nativeNoteOff(handle: Long, note: Int)
    private external fun nativeAllNotesOff(handle: Long)
    private external fun nativeSetCaptureMix(enabled: Boolean)

    companion object {
        init { System.loadLibrary("songpad_audio") }
    }
}
