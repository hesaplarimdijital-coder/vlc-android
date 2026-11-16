package app.nagmedar.tags.files

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import app.nagmedar.tags.ContentRef
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

/**
 * 1.2.0: audio files opened for TagLib in Nağmedar: the library's songs by their `file://` address (written with the
 * access to all files the app has, on the device and on an SD card alike), and a file another app handed over by its
 * `content://` one (Metamatik's way: what that app allows, often reading only). The descriptors are TagLib's to close.
 */
class LocalAudioFiles(context: Context) : AudioFileOpener {
    private val resolver = context.applicationContext.contentResolver

    override fun open(ref: ContentRef, mode: AccessMode): OpenResult {
        val uri = Uri.parse(ref.uri)
        return when (uri.scheme) {
            null, "file" -> openFile(File(uri.path ?: ref.uri), mode)
            "content" -> openContent(uri, mode)
            else -> OpenResult.Failed("Not a file: ${uri.scheme}")
        }
    }

    private fun openFile(file: File, mode: AccessMode): OpenResult {
        if (!file.isFile) return OpenResult.NotFound
        return try {
            val flags = if (mode == AccessMode.Read) ParcelFileDescriptor.MODE_READ_ONLY else ParcelFileDescriptor.MODE_READ_WRITE
            OpenResult.Opened(ParcelAudioFile(ParcelFileDescriptor.open(file, flags)))
        } catch (e: FileNotFoundException) {
            // there, but not to be opened so: the access to all files not given, a card read only
            if (file.isFile) OpenResult.AccessDenied else OpenResult.NotFound
        } catch (e: SecurityException) {
            OpenResult.AccessDenied
        }
    }

    private fun openContent(uri: Uri, mode: AccessMode): OpenResult = try {
        val pfd = resolver.openFileDescriptor(uri, if (mode == AccessMode.Read) "r" else "rw")
        if (pfd == null) OpenResult.NotFound else OpenResult.Opened(ParcelAudioFile(pfd))
    } catch (e: FileNotFoundException) {
        OpenResult.NotFound
    } catch (e: SecurityException) {
        OpenResult.AccessDenied
    } catch (e: UnsupportedOperationException) {
        // an app that hands over a stream alone cannot give a descriptor to write
        if (mode == AccessMode.ReadWrite) OpenResult.AccessDenied else OpenResult.Failed(e.message ?: "Unsupported")
    } catch (e: IllegalArgumentException) {
        OpenResult.Failed(e.message ?: "Invalid location")
    }
}

/** A file open on a descriptor: TagLib gets duplicates of it, the audio check reads at positions of its own. */
internal class ParcelAudioFile(private val pfd: ParcelFileDescriptor) : OpenedAudioFile {
    override val size: Long
        get() = pfd.statSize

    override fun detachDuplicateFd(): Int = pfd.dup().detachFd()

    override fun read(position: Long, buffer: ByteArray, offset: Int, length: Int): Int = try {
        // at a position of its own: the descriptor's stays where TagLib left it
        Os.pread(pfd.fileDescriptor, buffer, offset, length, position)
    } catch (e: ErrnoException) {
        // a pipe or a socket has no positions to read at
        throw IOException(e)
    }

    override fun close() = pfd.close()
}

/** Whether [file] can be written: its folder's and its own write permission (the access to all files given). */
fun canWrite(file: File): Boolean = try {
    Os.access(file.path, OsConstants.W_OK)
} catch (e: ErrnoException) {
    false
}
