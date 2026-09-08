package com.songpad.feature.export.bundle

import java.io.File
import java.io.OutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * §12.2 mechanics: one `ZipOutputStream` streaming straight into the destination, 16 KB copies, flat peak
 * memory. Already-compressed media (M4A/FLAC/MP3/MIDI) goes in STORED; text/JSON — and WAV, which zips well —
 * DEFLATED. STORED needs size + CRC up front, so those entries come from files on disk (a staged encode or the
 * stored asset itself), never from an in-memory buffer.
 */
class SongBundleWriter {

    sealed interface Entry {
        val path: String

        /** Text/JSON: small, DEFLATED, written from memory. */
        data class Text(override val path: String, val content: String) : Entry

        /** A file already on disk. STORED when [stored], else DEFLATED. */
        data class FromFile(override val path: String, val file: File, val stored: Boolean) : Entry

        /** Produced by an encoder straight into the DEFLATED entry (WAV). */
        data class Streamed(override val path: String, val write: (OutputStream, onProgress: (Float) -> Unit) -> Unit) : Entry
    }

    /** Called before each entry with (index, entry) and during long copies with a 0..1 fraction. Return false to abort. */
    fun interface Progress {
        fun onProgress(entryIndex: Int, entryCount: Int, path: String, fraction: Float): Boolean
    }

    class AbortedException : RuntimeException("export aborted")

    fun write(entries: List<Entry>, destination: OutputStream, progress: Progress = Progress { _, _, _, _ -> true }) {
        ZipOutputStream(destination.buffered(BUFFER)).use { zip ->
            zip.setLevel(6)
            entries.forEachIndexed { index, entry ->
                if (!progress.onProgress(index, entries.size, entry.path, 0f)) throw AbortedException()
                when (entry) {
                    is Entry.Text -> {
                        zip.putNextEntry(ZipEntry(entry.path).apply { method = ZipEntry.DEFLATED })
                        zip.write(entry.content.toByteArray(Charsets.UTF_8))
                        zip.closeEntry()
                    }
                    is Entry.FromFile -> {
                        val ze = ZipEntry(entry.path)
                        if (entry.stored) {
                            ze.method = ZipEntry.STORED
                            ze.size = entry.file.length()
                            ze.compressedSize = entry.file.length()
                            ze.crc = crcOf(entry.file)
                        } else ze.method = ZipEntry.DEFLATED
                        zip.putNextEntry(ze)
                        copy(entry.file, zip) { f -> if (!progress.onProgress(index, entries.size, entry.path, f)) throw AbortedException() }
                        zip.closeEntry()
                    }
                    is Entry.Streamed -> {
                        zip.putNextEntry(ZipEntry(entry.path).apply { method = ZipEntry.DEFLATED })
                        entry.write(NonClosing(zip)) { f -> if (!progress.onProgress(index, entries.size, entry.path, f)) throw AbortedException() }
                        zip.closeEntry()
                    }
                }
            }
            if (!progress.onProgress(entries.size, entries.size, "", 1f)) throw AbortedException()
        }
    }

    private fun copy(file: File, out: OutputStream, onProgress: (Float) -> Unit) {
        val total = file.length().coerceAtLeast(1)
        var done = 0L
        val buf = ByteArray(BUFFER)
        file.inputStream().use { input ->
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
                done += n
                onProgress((done.toDouble() / total).toFloat())
            }
        }
    }

    private fun crcOf(file: File): Long {
        val crc = CRC32()
        val buf = ByteArray(BUFFER)
        file.inputStream().use { input ->
            while (true) { val n = input.read(buf); if (n < 0) break; crc.update(buf, 0, n) }
        }
        return crc.value
    }

    /** Encoders `use {}` their stream; the zip must survive that. */
    private class NonClosing(private val delegate: OutputStream) : OutputStream() {
        override fun write(b: Int) = delegate.write(b)
        override fun write(b: ByteArray, off: Int, len: Int) = delegate.write(b, off, len)
        override fun flush() = delegate.flush()
        override fun close() = delegate.flush()
    }

    companion object {
        const val BUFFER = 16 * 1024

        /** `Export_<SongName>_<yyyyMMdd-HHmm>.songpad` (§12.1); the name is made filesystem-safe. */
        fun bundleFileName(songName: String, atMillis: Long): String {
            val safe = songName.trim().replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").replace(Regex("\\s+"), " ").take(60).ifBlank { "Song" }
            val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmm", java.util.Locale.US).format(java.util.Date(atMillis))
            return "Export_${safe}_$stamp.songpad"
        }

        fun safeEntryName(name: String): String = name.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").trim().take(80).ifBlank { "untitled" }
    }
}
