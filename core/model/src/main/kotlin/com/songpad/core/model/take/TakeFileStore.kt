package com.songpad.core.model.take

import java.io.File

/**
 * §9.1: the app's `files/` root, addressed by relative paths derived from [TakePaths]. This is the one
 * place that turns a relative path into a `File`; nothing above it holds absolute paths.
 */
class TakeFileStore(val root: File) {
    fun file(relativePath: String): File = File(root, relativePath)
    fun takeDir(takeId: String): File = File(root, TakePaths.takeDir(takeId)).also { it.mkdirs() }
    fun noteDir(noteId: String): File = File(root, TakePaths.noteDir(noteId)).also { it.mkdirs() }
    fun tmpDir(): File = File(root, TakePaths.TMP_DIR).also { it.mkdirs() }

    fun takesRoot(): File = File(root, TakePaths.TAKES_DIR)
    fun notesRoot(): File = File(root, TakePaths.NOTES_DIR)

    /** Every take directory on disk, whether or not the database knows about it. */
    fun listTakeDirs(): List<File> = takesRoot().listFiles()?.filter { it.isDirectory }.orEmpty()

    fun deleteTakeFiles(takeId: String): Boolean = File(root, TakePaths.takeDir(takeId)).deleteRecursively()
    fun deleteNoteFiles(noteId: String): Boolean = File(root, TakePaths.noteDir(noteId)).deleteRecursively()

    /** §7.3 commit marker: `rename()` within the same filesystem is atomic. */
    fun commitPcm(takeId: String): Boolean {
        val tmp = file(TakePaths.pcmTmp(takeId))
        val dst = file(TakePaths.pcm(takeId))
        if (!tmp.exists()) return dst.exists()
        return tmp.renameTo(dst)
    }

    fun exists(relativePath: String) = file(relativePath).exists()
    fun size(relativePath: String) = file(relativePath).length()
}
