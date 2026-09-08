package com.songpad.core.audio.encode

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import com.songpad.core.audio.playback.RawPcmDataSource
import com.songpad.core.model.export.ExportSource
import com.songpad.core.model.take.AudioFormat
import com.songpad.core.model.take.TakeFileStore
import java.io.File
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.ShortBuffer
import javax.inject.Inject

/**
 * §12.4 export encoders. Each consumes an [ExportSource] and never learns which quality mode produced it —
 * `ExportSourceResolver` (§12.5) already chose the best bytes. Decoding goes through one shared
 * [PcmDecoder] (MediaCodec for M4A/FLAC, a straight read for raw PCM) so WAV from FLAC is bit-exact.
 */
interface AudioExportEncoder {
    val extension: String
    val mimeType: String
    val isAvailable: Boolean
    /** Streams the encoded file into [out]; the caller owns the stream (zip entry, SAF document, temp file). */
    fun encode(source: ExportSource, out: OutputStream, onProgress: (Float) -> Unit = {})
}

/** Decodes any stored asset to 48 kHz · 16-bit · mono PCM chunks. */
class PcmDecoder @Inject constructor(private val files: TakeFileStore) {

    fun decode(source: ExportSource, onPcm: (ShortBuffer, Int) -> Unit, onProgress: (Float) -> Unit = {}) {
        val file = files.file(source.relativePath)
        if (source.mimeType == AudioFormat.MIME_RAW_PCM) return readRaw(file, onPcm, onProgress)
        val extractor = MediaExtractor()
        extractor.setDataSource(file.path)
        val trackIndex = (0 until extractor.trackCount).first { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
        val format = extractor.getTrackFormat(trackIndex)
        extractor.selectTrack(trackIndex)
        val codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
        codec.configure(format, null, null, 0)
        codec.start()
        val info = MediaCodec.BufferInfo()
        val totalUs = runCatching { format.getLong(MediaFormat.KEY_DURATION) }.getOrDefault(source.durationMs * 1000)
        var inputDone = false
        var outputDone = false
        while (!outputDone) {
            if (!inputDone) {
                val idx = codec.dequeueInputBuffer(10_000)
                if (idx >= 0) {
                    val buf = codec.getInputBuffer(idx)!!
                    val n = extractor.readSampleData(buf, 0)
                    if (n < 0) { codec.queueInputBuffer(idx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputDone = true }
                    else { codec.queueInputBuffer(idx, 0, n, extractor.sampleTime, 0); extractor.advance() }
                }
            }
            val out = codec.dequeueOutputBuffer(info, 10_000)
            if (out >= 0) {
                if (info.size > 0) {
                    val buf = codec.getOutputBuffer(out)!!
                    buf.position(info.offset); buf.limit(info.offset + info.size)
                    val shorts = buf.order(ByteOrder.nativeOrder()).asShortBuffer()
                    onPcm(shorts, info.size / 2)
                    if (totalUs > 0) onProgress((info.presentationTimeUs.toFloat() / totalUs).coerceIn(0f, 1f))
                }
                codec.releaseOutputBuffer(out, false)
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
            }
        }
        codec.stop(); codec.release(); extractor.release()
        onProgress(1f)
    }

    private fun readRaw(file: File, onPcm: (ShortBuffer, Int) -> Unit, onProgress: (Float) -> Unit) {
        val total = file.length()
        var done = 0L
        file.inputStream().buffered().use { input ->
            val bytes = ByteArray(16_384)
            while (true) {
                val n = input.read(bytes); if (n <= 0) break
                onPcm(ByteBuffer.wrap(bytes, 0, n).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer(), n / 2)
                done += n; if (total > 0) onProgress(done.toFloat() / total)
            }
        }
        onProgress(1f)
    }
}

/** "Uncompressed — for DAWs". Bit-exact from FLAC/PCM; decoded from AAC in Standard mode — never labelled lossless. */
class WavEncoder @Inject constructor(private val decoder: PcmDecoder) : AudioExportEncoder {
    override val extension = "wav"
    override val mimeType = AudioFormat.MIME_WAV
    override val isAvailable = true

    override fun encode(source: ExportSource, out: OutputStream, onProgress: (Float) -> Unit) {
        val pcmBytes = AudioFormat.msToFrames(source.durationMs) * AudioFormat.BYTES_PER_FRAME
        out.write(RawPcmDataSource.wavHeader(pcmBytes))
        val scratch = ByteBuffer.allocate(32_768).order(ByteOrder.LITTLE_ENDIAN)
        decoder.decode(source, onPcm = { shorts, n ->
            var i = 0
            while (i < n) {
                scratch.clear()
                val k = minOf(n - i, scratch.capacity() / 2)
                for (j in 0 until k) scratch.putShort(shorts.get(i + j))
                out.write(scratch.array(), 0, k * 2); i += k
            }
        }, onProgress = onProgress)
    }
}

/** Direct copy of the stored asset (M4A or FLAC). */
class StoredCopyEncoder @Inject constructor(private val files: TakeFileStore) : AudioExportEncoder {
    override val extension = "" // decided per source
    override val mimeType = ""
    override val isAvailable = true
    fun extensionFor(source: ExportSource) = when (source.mimeType) { AudioFormat.MIME_FLAC -> "flac"; AudioFormat.MIME_M4A -> "m4a"; else -> "wav" }

    override fun encode(source: ExportSource, out: OutputStream, onProgress: (Float) -> Unit) {
        files.file(source.relativePath).inputStream().use { it.copyTo(out, 16 * 1024) }
        onProgress(1f)
    }
}

/** MP3 via LAME/NDK (§12.4), 192 kbps mono. Available only when the LAME sources are vendored (docs/audio/LAME_INTEGRATION.md). */
class LameMp3Encoder @Inject constructor(private val decoder: PcmDecoder) : AudioExportEncoder {
    override val extension = "mp3"
    override val mimeType = AudioFormat.MIME_MP3
    override val isAvailable: Boolean = libraryLoaded

    override fun encode(source: ExportSource, out: OutputStream, onProgress: (Float) -> Unit) {
        check(isAvailable) { "LAME not available in this build" }
        val handle = nativeInit(AudioFormat.SAMPLE_RATE, AudioFormat.CHANNELS, AudioFormat.EXPORT_MP3_BITRATE_KBPS)
        check(handle != 0L) { "lame_init failed" }
        val mp3 = ByteArray(64 * 1024)
        val pcm = ShortArray(8_192)
        try {
            decoder.decode(source, onPcm = { shorts, n ->
                var i = 0
                while (i < n) {
                    val k = minOf(n - i, pcm.size)
                    for (j in 0 until k) pcm[j] = shorts.get(i + j)
                    val written = nativeEncode(handle, pcm, k, mp3)
                    if (written > 0) out.write(mp3, 0, written)
                    i += k
                }
            }, onProgress = onProgress)
            val tail = nativeFlush(handle, mp3)
            if (tail > 0) out.write(mp3, 0, tail)
        } finally { nativeClose(handle) }
    }

    private external fun nativeInit(sampleRate: Int, channels: Int, kbps: Int): Long
    private external fun nativeEncode(handle: Long, pcm: ShortArray, samples: Int, out: ByteArray): Int
    private external fun nativeFlush(handle: Long, out: ByteArray): Int
    private external fun nativeClose(handle: Long)

    companion object {
        val libraryLoaded: Boolean = runCatching { System.loadLibrary("songpad_lame"); true }.getOrDefault(false)
    }
}
