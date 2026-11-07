package com.songpad.core.audio.encode

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import com.songpad.core.audio.playback.RawPcmDataSource
import com.songpad.core.audio.transcode.FlacHeader
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
        if (source.mimeType == AudioFormat.MIME_WAV || file.name.endsWith(".wav", ignoreCase = true)) {
            return readWav(file, onPcm, onProgress)
        }
        if (source.mimeType == AudioFormat.MIME_FLAC || file.name.endsWith(".flac", ignoreCase = true)) {
            FlacHeader.ensureValidFlacFile(file, fallbackDurationMs = source.durationMs)
        }
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.path)
            val trackIndex = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return
            val format = extractor.getTrackFormat(trackIndex)
            extractor.selectTrack(trackIndex)
            val codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
            codec.configure(format, null, null, 0)
            codec.start()
            val info = MediaCodec.BufferInfo()
            val totalUs = runCatching { format.getLong(MediaFormat.KEY_DURATION) }.getOrDefault(source.durationMs * 1000)
            var channels = runCatching { format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) }.getOrDefault(1).coerceAtLeast(1)
            var inputDone = false
            var outputDone = false
            while (!outputDone) {
                if (!inputDone) {
                    val idx = codec.dequeueInputBuffer(10_000)
                    if (idx >= 0) {
                        val buf = codec.getInputBuffer(idx)!!
                        val n = extractor.readSampleData(buf, 0)
                        if (n < 0) {
                            codec.queueInputBuffer(idx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(idx, 0, n, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val out = codec.dequeueOutputBuffer(info, 10_000)
                if (out == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    channels = runCatching { codec.outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT) }.getOrDefault(channels).coerceAtLeast(1)
                } else if (out >= 0) {
                    if (info.size > 0) {
                        val buf = codec.getOutputBuffer(out)!!
                        buf.position(info.offset); buf.limit(info.offset + info.size)
                        val shorts = buf.order(ByteOrder.nativeOrder()).asShortBuffer()
                        val totalShorts = info.size / 2
                        if (channels <= 1) {
                            onPcm(shorts, totalShorts)
                        } else {
                            val frames = totalShorts / channels
                            val mono = ShortArray(frames)
                            for (f in 0 until frames) {
                                var sum = 0
                                for (c in 0 until channels) sum += shorts.get(f * channels + c).toInt()
                                mono[f] = (sum / channels).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                            }
                            onPcm(ShortBuffer.wrap(mono), frames)
                        }
                        if (totalUs > 0) onProgress((info.presentationTimeUs.toFloat() / totalUs).coerceIn(0f, 1f))
                    }
                    codec.releaseOutputBuffer(out, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                }
            }
            codec.stop(); codec.release()
        } finally {
            runCatching { extractor.release() }
        }
        onProgress(1f)
    }

    private fun readRaw(file: File, onPcm: (ShortBuffer, Int) -> Unit, onProgress: (Float) -> Unit) {
        val total = file.length()
        var done = 0L
        file.inputStream().buffered().use { input ->
            val bytes = ByteArray(16_384)
            while (true) {
                val n = input.read(bytes); if (n <= 0) break
                val valid = n and 1.inv()
                if (valid > 0) {
                    onPcm(ByteBuffer.wrap(bytes, 0, valid).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer(), valid / 2)
                }
                done += n; if (total > 0) onProgress(done.toFloat() / total)
            }
        }
        onProgress(1f)
    }

    private fun readWav(file: File, onPcm: (ShortBuffer, Int) -> Unit, onProgress: (Float) -> Unit) {
        val total = file.length()
        if (total <= 44L) {
            onProgress(1f)
            return
        }
        file.inputStream().buffered().use { input ->
            val header = ByteArray(44)
            var readHeader = 0
            while (readHeader < 44) {
                val r = input.read(header, readHeader, 44 - readHeader)
                if (r <= 0) break
                readHeader += r
            }
            val channels = if (readHeader >= 24) {
                ByteBuffer.wrap(header, 22, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt().coerceAtLeast(1)
            } else 1
            var done = readHeader.toLong()
            val bytes = ByteArray(16_384)
            while (true) {
                val n = input.read(bytes); if (n <= 0) break
                val valid = n and 1.inv()
                if (valid > 0) {
                    val shorts = ByteBuffer.wrap(bytes, 0, valid).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                    val count = valid / 2
                    if (channels <= 1) {
                        onPcm(shorts, count)
                    } else {
                        val frames = count / channels
                        val mono = ShortArray(frames)
                        for (f in 0 until frames) {
                            var sum = 0
                            for (c in 0 until channels) sum += shorts.get(f * channels + c).toInt()
                            mono[f] = (sum / channels).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                        }
                        onPcm(ShortBuffer.wrap(mono), frames)
                    }
                }
                done += n
                if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
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
        val pcmBytes = if (source.mimeType == AudioFormat.MIME_RAW_PCM) {
            source.byteSize
        } else {
            AudioFormat.msToFrames(source.durationMs) * AudioFormat.BYTES_PER_FRAME
        }
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

/** Direct copy of the stored asset (M4A, FLAC, WAV, or MP3). */
class StoredCopyEncoder @Inject constructor(private val files: TakeFileStore) : AudioExportEncoder {
    override val extension = "" // decided per source
    override val mimeType = ""
    override val isAvailable = true
    fun extensionFor(source: ExportSource) = when (source.mimeType) {
        AudioFormat.MIME_FLAC -> "flac"
        AudioFormat.MIME_M4A -> "m4a"
        AudioFormat.MIME_MP3 -> "mp3"
        AudioFormat.MIME_WAV -> "wav"
        else -> "wav"
    }

    override fun encode(source: ExportSource, out: OutputStream, onProgress: (Float) -> Unit) {
        val file = files.file(source.relativePath)
        if (source.mimeType == AudioFormat.MIME_FLAC || file.name.endsWith(".flac", ignoreCase = true)) {
            FlacHeader.ensureValidFlacFile(file, fallbackDurationMs = source.durationMs)
        }
        file.inputStream().use { it.copyTo(out, 16 * 1024) }
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
