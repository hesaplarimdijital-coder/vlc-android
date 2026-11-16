/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 * Nağmedar 1.2.0: from Metamatik (library/api/src/main/kotlin/com/bobbyesp/metadator/library/api/FileAccess.kt), a fork of Metadator, under the AGPL-3.0.
 */
package app.nagmedar.tags.files

import app.nagmedar.tags.ContentRef

/** Opens audio files for native code, which reads and writes through a file descriptor. */
interface AudioFileOpener {
    fun open(ref: ContentRef, mode: AccessMode): OpenResult
}

enum class AccessMode {
    Read,
    ReadWrite,
}

sealed interface OpenResult {
    data class Opened(val file: OpenedAudioFile) : OpenResult

    data object NotFound : OpenResult

    /** Writing needs the user's permission first (Nağmedar: access to all files). */
    data object AccessDenied : OpenResult

    data class Failed(val message: String) : OpenResult
}

/** An open file. Close it when done; the descriptors it hands out are the caller's to close. */
interface OpenedAudioFile : AutoCloseable {
    /**
     * A new descriptor for the same file, detached from this object: native code takes ownership
     * and closes it.
     */
    fun detachDuplicateFd(): Int

    /** How long the file is, in bytes, or -1 when whoever serves it does not say. */
    val size: Long

    /**
     * Reads up to [length] bytes found at [position] of the file into [buffer], from [offset] on,
     * for code that parses the audio itself. Returns how many were read, and 0 or less at the end
     * of the file.
     *
     * @throws java.io.IOException when the file cannot be read at a position of one's choosing, as
     *   one served through a pipe cannot
     */
    fun read(position: Long, buffer: ByteArray, offset: Int, length: Int): Int
}

/** Makes the system index files again after they were written, so every app sees the new tags. */
interface MediaIndexer {
    suspend fun rescan(refs: List<ContentRef>)
}
