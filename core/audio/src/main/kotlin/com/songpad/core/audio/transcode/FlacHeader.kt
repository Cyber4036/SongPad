package com.songpad.core.audio.transcode

import com.songpad.core.model.take.AudioFormat
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Canonical 42-byte FLAC stream header (`"fLaC"` magic + 34-byte `STREAMINFO` metadata block) utility.
 *
 * Why this exists:
 * 1. Android's `MediaCodec` FLAC encoder (`c2.android.flac.encoder` / `OMX.google.flac.encoder`) emits the
 *    42-byte `fLaC` + `STREAMINFO` header in a buffer flagged with `BUFFER_FLAG_CODEC_CONFIG` (with
 *    `totalSamples = 0`). If that buffer was skipped or if `totalSamples` was left 0, Media3's `FlacExtractor`
 *    either fails with `ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED` or reports `C.TIME_UNSET` duration.
 * 2. Self-heals existing headerless `.flac` takes recorded on devices before this fix by prepending the
 *    42-byte `fLaC` + `STREAMINFO` header in-place.
 */
object FlacHeader {

    const val HEADER_SIZE = 42

    /**
     * Reads up to [maxBytes] from this [InputStream] using standard API 1+ `read(...)` calls.
     * Avoids `InputStream.readNBytes(int)` which was only added in Android 13 (API 33) and throws
     * `NoSuchMethodError` on Android 8–12 (API 26–32).
     */
    fun InputStream.readUpToNBytes(maxBytes: Int): ByteArray {
        if (maxBytes <= 0) return ByteArray(0)
        val buffer = ByteArray(maxBytes)
        var offset = 0
        while (offset < maxBytes) {
            val read = read(buffer, offset, maxBytes - offset)
            if (read <= 0) break
            offset += read
        }
        return if (offset == maxBytes) buffer else buffer.copyOf(offset)
    }

    fun hasFlacMagic(file: File): Boolean {
        if (!file.exists() || file.length() < 4L) return false
        val magic = runCatching { file.inputStream().use { it.readUpToNBytes(4) } }.getOrDefault(ByteArray(0))
        return hasFlacMagic(magic)
    }

    fun hasFlacMagic(bytes: ByteArray): Boolean =
        bytes.size >= 4 &&
            bytes[0] == 'f'.code.toByte() &&
            bytes[1] == 'L'.code.toByte() &&
            bytes[2] == 'a'.code.toByte() &&
            bytes[3] == 'C'.code.toByte()

    /**
     * Builds the 42-byte `"fLaC"` + `STREAMINFO` header (RFC 9639 §7 & §8.2).
     */
    fun buildStreamInfoHeader(
        totalSamples: Long,
        sampleRate: Int = AudioFormat.SAMPLE_RATE,
        channels: Int = AudioFormat.CHANNELS,
        bitDepth: Int = AudioFormat.BIT_DEPTH,
        minBlockSize: Int = 4096,
        maxBlockSize: Int = 4096,
    ): ByteArray {
        val safeMinBlock = minBlockSize.coerceIn(16, 65535)
        val safeMaxBlock = maxOf(safeMinBlock, maxBlockSize).coerceIn(16, 65535)
        val srBits = (sampleRate.toLong() and 0xFFFFFL) shl 44
        val chBits = ((channels - 1).coerceIn(0, 7).toLong() and 0x7L) shl 41
        val bpsBits = ((bitDepth - 1).coerceIn(0, 31).toLong() and 0x1FL) shl 36
        val sampleBits = totalSamples.coerceIn(0L, 0xFFFFFFFFFL)
        val packed64 = srBits or chBits or bpsBits or sampleBits

        return ByteBuffer.allocate(HEADER_SIZE).order(ByteOrder.BIG_ENDIAN).apply {
            // "fLaC" stream marker (4 bytes)
            put('f'.code.toByte())
            put('L'.code.toByte())
            put('a'.code.toByte())
            put('C'.code.toByte())
            // Metadata block header (4 bytes): isLast = 1 (0x80), type = 0 (STREAMINFO), length = 34 (0x000022)
            put(0x80.toByte())
            put(0x00.toByte())
            put(0x00.toByte())
            put(34.toByte())
            // STREAMINFO body (34 bytes)
            putShort(safeMinBlock.toShort())
            putShort(safeMaxBlock.toShort())
            // 24-bit minFrameSize = 0, 24-bit maxFrameSize = 0 (6 bytes)
            put(ByteArray(6))
            // 64-bit packed sampleRate (20) | channels-1 (3) | bps-1 (5) | totalSamples (36)
            putLong(packed64)
            // 128-bit MD5 signature = 0 (16 bytes)
            put(ByteArray(16))
        }.array()
    }

    /**
     * Inspects a FLAC frame's block-size nibble (byte 2 bits 7..4) to determine the encoder's frame block size.
     */
    fun parseFirstFrameBlockSize(prefix: ByteArray): Int {
        if (prefix.size < 4) return 4096
        val b0 = prefix[0].toInt() and 0xFF
        val b1 = prefix[1].toInt() and 0xFF
        if (b0 != 0xFF || (b1 and 0xFE) != 0xF8) return 4096
        val code = (prefix[2].toInt() ushr 4) and 0x0F
        return when (code) {
            1 -> 192
            in 2..5 -> 576 shl (code - 2)
            in 8..15 -> 256 shl (code - 8)
            else -> 4096
        }
    }

    /**
     * Ensures [file] has a valid 42-byte `"fLaC"` + `STREAMINFO` header and non-zero `totalSamples`.
     * - If `"fLaC"` is missing at byte 0, prepends the 42-byte header atomically.
     * - If `"fLaC"` is present but `totalSamples` in `STREAMINFO` is 0, patches `totalSamples` in-place.
     *
     * @return true if the file is now a valid FLAC container with `"fLaC"` magic.
     */
    fun ensureValidFlacFile(
        file: File,
        totalSamples: Long = 0L,
        fallbackDurationMs: Long = 0L,
    ): Boolean {
        if (!file.exists() || file.length() < 4L) return false
        val prefix = runCatching { FileInputStream(file).use { it.readUpToNBytes(HEADER_SIZE) } }.getOrDefault(ByteArray(0))
        if (prefix.size < 4) return false

        val resolvedSamples = when {
            totalSamples > 0L -> totalSamples
            fallbackDurationMs > 0L -> (fallbackDurationMs * AudioFormat.SAMPLE_RATE) / 1000L
            // Rough estimate from compressed FLAC size (~48 bytes/ms at 48 kHz 16-bit mono)
            else -> ((file.length() * 2L) / AudioFormat.BYTES_PER_FRAME).coerceAtLeast(AudioFormat.SAMPLE_RATE.toLong())
        }

        if (hasFlacMagic(prefix)) {
            // If STREAMINFO block is at offset 4 (type 0, length 34) and totalSamples is 0, patch it in-place
            if (prefix.size >= HEADER_SIZE && (prefix[4].toInt() and 0x7F) == 0 && resolvedSamples > 0L) {
                val packed64 = ByteBuffer.wrap(prefix, 18, 8).order(ByteOrder.BIG_ENDIAN).long
                val existingSamples = packed64 and 0xFFFFFFFFFL
                if (existingSamples == 0L) {
                    val patched64 = (packed64 and 0xFFFFFFFFFL.inv()) or (resolvedSamples and 0xFFFFFFFFFL)
                    runCatching {
                        RandomAccessFile(file, "rw").use { raf ->
                            raf.seek(18L)
                            raf.writeLong(patched64)
                        }
                    }
                }
            }
            return true
        }

        // File does not start with "fLaC": prepend canonical 42-byte header atomically
        val blockSize = parseFirstFrameBlockSize(prefix)
        val header = buildStreamInfoHeader(
            totalSamples = resolvedSamples,
            minBlockSize = minOf(blockSize, 4096),
            maxBlockSize = maxOf(blockSize, 4096),
        )
        val repairedTmp = File(file.parentFile, "${file.name}.repaired.tmp")
        return runCatching {
            FileOutputStream(repairedTmp).buffered().use { out ->
                out.write(header)
                FileInputStream(file).buffered().use { input ->
                    input.copyTo(out, 16 * 1024)
                }
            }
            if (!repairedTmp.renameTo(file)) {
                repairedTmp.copyTo(file, overwrite = true)
                repairedTmp.delete()
            }
            true
        }.getOrElse {
            repairedTmp.delete()
            false
        }
    }
}
