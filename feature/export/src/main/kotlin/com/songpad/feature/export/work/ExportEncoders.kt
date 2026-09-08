package com.songpad.feature.export.work

import com.songpad.core.audio.encode.AudioExportEncoder
import com.songpad.core.audio.encode.LameMp3Encoder
import com.songpad.core.audio.encode.StoredCopyEncoder
import com.songpad.core.audio.encode.WavEncoder
import com.songpad.core.model.error.ExportError
import com.songpad.core.model.export.ExportAudioFormat
import com.songpad.core.model.export.ExportSource
import com.songpad.core.model.take.AudioFormat
import javax.inject.Inject

/** Picks the §12.4 encoder for a request. A raw-PCM source under STORED becomes WAV (there is no "stored" container yet). */
class ExportEncoders @Inject constructor(
    private val wav: WavEncoder,
    private val stored: StoredCopyEncoder,
    private val mp3: LameMp3Encoder,
) {
    fun availability(format: ExportAudioFormat): ExportError? = when (format) {
        ExportAudioFormat.MP3 -> if (mp3.isAvailable) null else ExportError.EncoderUnavailable("MP3")
        else -> null
    }

    fun forSource(format: ExportAudioFormat, source: ExportSource): AudioExportEncoder = when (format) {
        ExportAudioFormat.STORED -> if (source.mimeType == AudioFormat.MIME_RAW_PCM) wav else stored
        ExportAudioFormat.WAV -> wav
        ExportAudioFormat.MP3 -> mp3
    }

    /** WAV zips well and streams straight into a DEFLATED entry; everything else is staged and STORED (§12.2). */
    fun isStreamedDeflated(format: ExportAudioFormat, source: ExportSource): Boolean =
        format == ExportAudioFormat.WAV || (format == ExportAudioFormat.STORED && source.mimeType == AudioFormat.MIME_RAW_PCM)
}
