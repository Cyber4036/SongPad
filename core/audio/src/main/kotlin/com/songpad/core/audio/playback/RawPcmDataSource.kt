package com.songpad.core.audio.playback

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import com.songpad.core.model.take.AudioFormat
import com.songpad.core.model.take.TakeFileStore
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Presents a headerless `.pcm`/`.pcm.tmp` as a WAV stream by synthesizing the 44-byte RIFF header from the
 * fixed capture constants (§7.3, §7.6). Media3's WavExtractor then handles seeking. The header's length
 * fields reflect the file length at open time, which for an in-flight `.pcm.tmp` is "what exists so far" —
 * exactly the review-before-save semantics.
 */
class RawPcmDataSource(private val files: TakeFileStore, private val upstream: DataSource) : BaseDataSource(false) {
    private var header: ByteArray = ByteArray(0)
    private var raf: RandomAccessFile? = null
    private var position = 0L
    private var total = 0L
    private var uri: Uri? = null

    override fun open(dataSpec: DataSpec): Long {
        val u = dataSpec.uri
        if (u.scheme != SCHEME) return upstream.open(dataSpec)
        uri = u
        val file = files.file(u.path!!.trimStart('/'))
        val f = RandomAccessFile(file, "r")
        raf = f
        val pcmBytes = f.length()
        header = wavHeader(pcmBytes)
        total = header.size + pcmBytes
        position = dataSpec.position
        transferInitializing(dataSpec)
        transferStarted(dataSpec)
        return if (dataSpec.length != C.LENGTH_UNSET.toLong()) dataSpec.length else total - position
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (uri == null) return upstream.read(buffer, offset, length)
        if (length == 0) return 0
        if (position >= total) return C.RESULT_END_OF_INPUT
        var written = 0
        if (position < header.size) {
            val n = minOf(length, (header.size - position).toInt())
            System.arraycopy(header, position.toInt(), buffer, offset, n)
            position += n; written += n
            if (written == length) { bytesTransferred(written); return written }
        }
        val f = raf ?: return if (written > 0) written else C.RESULT_END_OF_INPUT
        f.seek(position - header.size)
        val n = f.read(buffer, offset + written, length - written)
        if (n <= 0) return if (written > 0) written else C.RESULT_END_OF_INPUT
        position += n; written += n
        bytesTransferred(written)
        return written
    }

    override fun getUri(): Uri? = uri ?: upstream.uri

    override fun close() {
        if (uri == null) { upstream.close(); return }
        raf?.close(); raf = null; uri = null
        transferEnded()
    }

    class Factory(private val upstream: DataSource.Factory, private val files: TakeFileStore) : DataSource.Factory {
        override fun createDataSource(): DataSource = RawPcmDataSource(files, upstream.createDataSource())
    }

    companion object {
        const val SCHEME = "songpad-pcm"
        fun uriFor(relativePath: String): Uri = Uri.parse("$SCHEME:///$relativePath")

        /** 44-byte canonical RIFF/WAVE header for 48 kHz · 16-bit · mono. Also used by the WAV export encoder (§12.4). */
        fun wavHeader(pcmBytes: Long, sampleRate: Int = AudioFormat.SAMPLE_RATE, channels: Int = AudioFormat.CHANNELS, bitDepth: Int = AudioFormat.BIT_DEPTH): ByteArray {
            val byteRate = sampleRate * channels * bitDepth / 8
            val blockAlign = channels * bitDepth / 8
            val data = pcmBytes.coerceAtMost(0xFFFF_FFFFL - 36).toInt()
            return ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
                put("RIFF".toByteArray(Charsets.US_ASCII)); putInt(36 + data)
                put("WAVE".toByteArray(Charsets.US_ASCII))
                put("fmt ".toByteArray(Charsets.US_ASCII)); putInt(16); putShort(1); putShort(channels.toShort())
                putInt(sampleRate); putInt(byteRate); putShort(blockAlign.toShort()); putShort(bitDepth.toShort())
                put("data".toByteArray(Charsets.US_ASCII)); putInt(data)
            }.array()
        }
    }
}
