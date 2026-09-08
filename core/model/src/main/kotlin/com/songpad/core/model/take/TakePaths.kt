package com.songpad.core.model.take

/**
 * §9.1. Files are addressed by the same UUIDv7 as the database row. Every path in the app is derived
 * here; absolute paths are never stored. Relative to the app's `files/` directory.
 */
object TakePaths {
    const val TAKES_DIR = "takes"
    const val NOTES_DIR = "notes"
    const val TMP_DIR = "tmp"

    const val AUDIO_PCM_TMP = "audio.pcm.tmp"
    const val AUDIO_PCM = "audio.pcm"
    const val AUDIO_M4A = "audio.m4a"
    const val AUDIO_FLAC = "audio.flac"
    const val PEAKS = "peaks.wave"
    const val EVENTS_LOG = "events.log"
    const val PERFORMANCE_MID = "performance.mid"
    const val NOTE_IMAGE = "image.jpg"

    fun takeDir(takeId: String) = "$TAKES_DIR/$takeId"
    fun pcmTmp(takeId: String) = "${takeDir(takeId)}/$AUDIO_PCM_TMP"
    fun pcm(takeId: String) = "${takeDir(takeId)}/$AUDIO_PCM"
    fun m4a(takeId: String) = "${takeDir(takeId)}/$AUDIO_M4A"
    fun flac(takeId: String) = "${takeDir(takeId)}/$AUDIO_FLAC"
    fun stored(takeId: String, mode: QualityMode) = "${takeDir(takeId)}/${mode.fileName}"
    fun peaks(takeId: String) = "${takeDir(takeId)}/$PEAKS"
    fun eventsLog(takeId: String) = "${takeDir(takeId)}/$EVENTS_LOG"
    fun midi(takeId: String) = "${takeDir(takeId)}/$PERFORMANCE_MID"
    fun noteDir(noteId: String) = "$NOTES_DIR/$noteId"
    fun noteImage(noteId: String) = "${noteDir(noteId)}/$NOTE_IMAGE"

    /** Inverse of [takeDir]: the take id a relative path belongs to, or null. */
    fun takeIdOf(relativePath: String): String? {
        val parts = relativePath.split('/')
        return if (parts.size >= 2 && parts[0] == TAKES_DIR) parts[1] else null
    }
}
