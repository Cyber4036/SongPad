package com.songpad.core.audio.transcode

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import com.songpad.core.model.take.AudioFormat
import com.songpad.core.model.take.QualityMode
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer

/**
 * §4.1 "Transcode": raw PCM → AAC-LC/M4A (Standard) or FLAC (Lossless) via `MediaCodec` + `MediaMuxer`.
 * Both encoders share one drain loop; only the output format and container differ. FLAC is written as a
 * raw `.flac` stream (MediaMuxer has no FLAC container), M4A through the MP4 muxer.
 *
 * Verification (§4.3 "written and verified") = output exists, is non-trivial, and the encoded duration is
 * within 250 ms of the PCM duration; raw PCM is deleted by the worker only after this returns true.
 */
class PcmTranscoder {

    data class Output(val file: File, val mimeType: String, val byteSize: Long, val durationUs: Long)

    fun transcode(pcm: File, mode: QualityMode, target: File): Output = when (mode) {
        QualityMode.STANDARD -> encodeAacM4a(pcm, target)
        QualityMode.LOSSLESS -> encodeFlac(pcm, target)
    }

    private fun encodeAacM4a(pcm: File, target: File): Output {
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, AudioFormat.SAMPLE_RATE, AudioFormat.CHANNELS).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, AudioFormat.STANDARD_AAC_BITRATE)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, INPUT_CHUNK)
        }
        val tmp = File(target.path + ".part")
        val muxer = MediaMuxer(tmp.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var track = -1
        val durationUs = drive(pcm, format, onFormat = { f -> track = muxer.addTrack(f); muxer.start() }) { buf, info -> muxer.writeSampleData(track, buf, info) }
        runCatching { muxer.stop() }; muxer.release()
        if (!tmp.renameTo(target)) throw java.io.IOException("rename failed: ${target.name}")
        return Output(target, AudioFormat.MIME_M4A, target.length(), durationUs)
    }

    private fun encodeFlac(pcm: File, target: File): Output {
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_FLAC, AudioFormat.SAMPLE_RATE, AudioFormat.CHANNELS).apply {
            setInteger(MediaFormat.KEY_FLAC_COMPRESSION_LEVEL, 5)
            setInteger(MediaFormat.KEY_BIT_RATE, AudioFormat.SAMPLE_RATE * AudioFormat.BIT_DEPTH * AudioFormat.CHANNELS)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, INPUT_CHUNK)
        }
        val tmp = File(target.path + ".part")
        val durationUs = FileOutputStream(tmp).use { out ->
            val scratch = ByteArray(INPUT_CHUNK * 4)
            drive(pcm, format, onFormat = {}) { buf, info ->
                val n = info.size
                if (n > scratch.size) { val big = ByteArray(n); buf.get(big); out.write(big) } else { buf.get(scratch, 0, n); out.write(scratch, 0, n) }
            }
        }
        if (!tmp.renameTo(target)) throw java.io.IOException("rename failed: ${target.name}")
        return Output(target, AudioFormat.MIME_FLAC, target.length(), durationUs)
    }

    /** Feeds PCM in, drains encoded buffers out. Returns the presentation time of the last output buffer. */
    private fun drive(pcm: File, format: MediaFormat, onFormat: (MediaFormat) -> Unit, onSample: (ByteBuffer, MediaCodec.BufferInfo) -> Unit): Long {
        val codec = MediaCodec.createEncoderByType(format.getString(MediaFormat.KEY_MIME)!!)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()
        val info = MediaCodec.BufferInfo()
        var lastPtsUs = 0L
        var inputDone = false
        var outputDone = false
        var samplesFed = 0L
        FileInputStream(pcm).use { input ->
            val chunk = ByteArray(INPUT_CHUNK)
            while (!outputDone) {
                if (!inputDone) {
                    val idx = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (idx >= 0) {
                        val buf = codec.getInputBuffer(idx)!!
                        val n = input.read(chunk, 0, minOf(chunk.size, buf.capacity()))
                        if (n <= 0) {
                            codec.queueInputBuffer(idx, 0, 0, ptsUs(samplesFed), MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputDone = true
                        } else {
                            buf.clear(); buf.put(chunk, 0, n)
                            codec.queueInputBuffer(idx, 0, n, ptsUs(samplesFed), 0)
                            samplesFed += n / AudioFormat.BYTES_PER_FRAME
                        }
                    }
                }
                val out = codec.dequeueOutputBuffer(info, TIMEOUT_US)
                when {
                    out == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> onFormat(codec.outputFormat)
                    out >= 0 -> {
                        if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0 && info.size > 0) {
                            val buf = codec.getOutputBuffer(out)!!
                            buf.position(info.offset); buf.limit(info.offset + info.size)
                            onSample(buf, info)
                            lastPtsUs = info.presentationTimeUs
                        }
                        codec.releaseOutputBuffer(out, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    }
                }
            }
        }
        codec.stop(); codec.release()
        return maxOf(lastPtsUs, ptsUs(samplesFed))
    }

    private fun ptsUs(samples: Long) = samples * 1_000_000L / AudioFormat.SAMPLE_RATE

    companion object {
        private const val INPUT_CHUNK = 8_192
        private const val TIMEOUT_US = 10_000L

        /** §4.3: raw PCM is deleted only after the stored asset is verified. */
        fun verify(output: Output, pcmBytes: Long): Boolean {
            if (!output.file.exists() || output.byteSize < 64) return false
            val expectedUs = pcmBytes / AudioFormat.BYTES_PER_FRAME * 1_000_000L / AudioFormat.SAMPLE_RATE
            return kotlin.math.abs(output.durationUs - expectedUs) <= 250_000L
        }
    }
}
