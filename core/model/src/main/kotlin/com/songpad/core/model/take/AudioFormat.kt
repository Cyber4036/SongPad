package com.songpad.core.model.take

/**
 * The one capture format (§4.1): 48 kHz · 16-bit · mono · linear PCM. These are constants, not
 * per-file metadata — which is exactly what lets a headerless `.pcm` be recovered (§7.3).
 */
object AudioFormat {
    const val SAMPLE_RATE = 48_000
    const val BIT_DEPTH = 16
    const val CHANNELS = 1
    const val BYTES_PER_SAMPLE = BIT_DEPTH / 8
    const val BYTES_PER_FRAME = BYTES_PER_SAMPLE * CHANNELS

    /** 96 bytes per millisecond at 48 kHz · 16-bit · mono (§9.4). */
    const val BYTES_PER_MS = SAMPLE_RATE * BYTES_PER_FRAME / 1000
    const val BYTES_PER_SECOND = SAMPLE_RATE * BYTES_PER_FRAME

    const val MIME_RAW_PCM = "audio/x-songpad-pcm"
    const val MIME_M4A = "audio/mp4"
    const val MIME_FLAC = "audio/flac"
    const val MIME_WAV = "audio/wav"
    const val MIME_MP3 = "audio/mpeg"

    const val STANDARD_AAC_BITRATE = 128_000
    const val EXPORT_MP3_BITRATE_KBPS = 192

    /** Recording is refused below this (§7.4) — ~17 minutes of headroom at 5.76 MB/min. */
    const val LOW_STORAGE_BYTES = 100L * 1024 * 1024
    const val CRITICAL_STORAGE_BYTES = 20L * 1024 * 1024

    /** Recovery discards candidates under this length as noise (§9.4). */
    const val MIN_RECOVERABLE_MS = 1_000L

    fun durationMsForBytes(byteLength: Long): Long = byteLength / BYTES_PER_MS
    fun framesForBytes(byteLength: Long): Long = byteLength / BYTES_PER_FRAME
    fun bytesForFrames(frames: Long): Long = frames * BYTES_PER_FRAME
    fun framesToMs(frames: Long): Long = frames * 1000 / SAMPLE_RATE
    fun msToFrames(ms: Long): Long = ms * SAMPLE_RATE / 1000
}

/** §4.3. Per-take property carried by `AudioAsset.mimeType`; the setting applies to NEW recordings only. */
enum class QualityMode(val storedMime: String, val fileName: String, val approxMbPerMin: Double) {
    STANDARD(AudioFormat.MIME_M4A, TakePaths.AUDIO_M4A, 0.96),
    LOSSLESS(AudioFormat.MIME_FLAC, TakePaths.AUDIO_FLAC, 2.9),
}
