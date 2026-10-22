package app.nagmedar.audio

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.system.Os
import android.util.Log
import app.nagmedar.audio.TagLeftovers.Fate
import org.json.JSONObject
import java.io.File
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.util.UUID
import kotlin.math.abs

/** S-1005: what writing a file's tags came to. */
enum class TagWrite {
    /** Written, and the file read back with its audio as it was. */
    Written,

    /** The app's storage has no room for the copy kept while the file is written: nothing was written. */
    NoRoom,

    /** Not written; the file is as it was (a CUE track, no engine, not writable, or the write failed and the copy went back). */
    Failed,
}

/**
 * S-1005: a tag write that an interruption cannot leave half done. TagLib rewrites the file in
 * place (a longer tag moves the audio after it), so the file is first copied into the app's own
 * storage (never into backups); after the write TagLib reads the file again and its audio
 * (length, sample rate, channels) must be as it was, and when the file's size changed (its audio
 * was moved, which those values, read from headers, cannot vouch for) its decoded waveform must be
 * the copy's too. A failure puts the copy back. A note beside the copy names the file, its size
 * and last change before the write and when the write began: a process that died while writing
 * leaves both, and [recover] (at the next start) deletes the copy when the file reads as it was,
 * puts it back when that write was the last to change the file, and otherwise leaves the file
 * alone and moves the copy into a folder the user sees, as it does with the copy of a file not
 * found for a week ([TagLeftovers]).
 */
internal object TagBackups {
    private const val TAG = "NagmedarAudio"
    private const val DIR = "tag-backup"
    private const val COPY = ".audio"
    private const val NOTE = ".json"

    /** A note being replaced, beside it until it is whole. */
    private const val NEXT = ".next"

    /** Room left over beside the copy, for the app and the device. */
    private const val SPARE_BYTES = 32L * 1024 * 1024

    /** The waveform's points the checks compare: enough to tell moved audio. */
    private const val POINTS = 2048

    private const val BUFFER = 1024 * 1024

    /** One write or recovery at a time, so that the recovery never takes a copy in use. */
    private val lock = Any()

    /** The audio TagLib reads in a file: what a tag write must not change. */
    data class Audio(val lengthMs: Long, val rate: Int, val channels: Int) {
        fun same(other: Audio?): Boolean = other != null && rate == other.rate && channels == other.channels && abs(lengthMs - other.lengthMs) <= 1

        companion object {
            fun of(values: LongArray?): Audio? = values?.takeIf { it.size >= 3 }?.let { Audio(it[0], it[1].toInt(), it[2].toInt()) }
        }
    }

    /**
     * [write] on [descriptor] (read-write, [track]'s, [name] its display name), with a copy of the file kept until it is
     * read back as it was. Writes the file and may decode it twice: off the main thread.
     */
    fun guarded(context: Context, track: Uri, descriptor: ParcelFileDescriptor, name: String, write: (fd: Int, name: String) -> Boolean): TagWrite =
        synchronized(lock) {
            val fd = descriptor.fd
            // a file TagLib does not read takes no tags
            val before = audioOf(fd, name) ?: return TagWrite.Failed
            val size = descriptor.statSize
            if (size <= 0) return TagWrite.Failed
            val dir = dir(context) ?: return TagWrite.Failed
            if (dir.usableSpace < size + SPARE_BYTES) return TagWrite.NoRoom
            val stem = UUID.randomUUID().toString()
            val copy = File(dir, stem + COPY)
            val note = File(dir, stem + NOTE)
            try {
                copyOut(descriptor.fileDescriptor, size, copy)
                writeNote(note, track, name, size, statOf(descriptor.fileDescriptor), before)
            } catch (e: Exception) {
                // IOException, ErrnoException
                Log.w(TAG, "no copy of $name to write its tags: $e")
                copy.delete()
                note.delete()
                return if (dir.usableSpace < size + SPARE_BYTES) TagWrite.NoRoom else TagWrite.Failed
            }
            val written = try {
                write(fd, name)
            } catch (e: Exception) {
                Log.w(TAG, "writing the tags of $name failed: $e")
                false
            }
            runCatching { Os.fsync(descriptor.fileDescriptor) }
            if (written && readsAsBefore(context, track, before, copy, moved = descriptor.statSize != size)) {
                note.delete()
                copy.delete()
                return TagWrite.Written
            }
            if (sameBytes(descriptor, copy)) {
                // refused before anything was written (a tag the format cannot hold)
                note.delete()
                copy.delete()
            } else {
                Log.w(TAG, "the tags of $name did not take: the file goes back as it was")
                putBack(descriptor.fileDescriptor, copy, size, before, fd, name, note)
            }
            TagWrite.Failed
        }

    /**
     * What a process that died while writing tags left. A file that reads as it did loses its copy; one that does not
     * gets the copy back when the interrupted write was the last to change it. A file changed elsewhere since is left
     * as it is, and its copy, like that of a file not found for a week, is moved into [keepIn]: the copies moved there
     * are returned, for the user to be told. Reads, decodes and writes files: off the main thread.
     */
    fun recover(context: Context, keepIn: File?): List<File> {
        if (!NativeAudio.available) return emptyList()
        synchronized(lock) {
            val dir = File(context.applicationContext.noBackupFilesDir, DIR)
            val files = dir.listFiles() ?: return emptyList()
            val stems = files.filter { it.name.endsWith(NOTE) }.map { it.name.removeSuffix(NOTE) }.toSet()
            // a copy without its note: the write had not begun; a note's replacement left half written
            files.filter { (it.name.endsWith(COPY) && it.name.removeSuffix(COPY) !in stems) || it.name.endsWith(NEXT) }.forEach { it.delete() }
            return stems.mapNotNull { stem -> recoverOne(context, File(dir, stem + NOTE), File(dir, stem + COPY), keepIn) }
        }
    }

    /** One leftover copy deleted, put back, left waiting for its file or moved into [keepIn]; the copy there when it was. */
    private fun recoverOne(context: Context, note: File, copy: File, keepIn: File?): File? {
        val saved = runCatching { JSONObject(note.readText()) }.getOrNull() ?: JSONObject()
        val track = saved.optString("uri").takeIf { it.isNotEmpty() }?.let(Uri::parse)
        val size = saved.optLong("size", -1L)
        val before = Audio(saved.optLong("length_ms"), saved.optInt("rate"), saved.optInt("channels"))
        if (track == null || before.rate <= 0 || !copy.isFile || copy.length() != size) {
            note.delete()
            copy.delete()
            return null
        }
        val name = saved.optString("name")
        // a note of an older version knows neither: the note's own time is when the write began
        val started = saved.optLong("started_at", note.lastModified())
        val stat = if (saved.has("modified_s")) TagLeftovers.Stat(size, saved.optLong("modified_s")) else null
        val descriptor = runCatching { FileTags.openWritable(context.applicationContext, track) }.getOrNull()
        val fate = if (descriptor == null) {
            // the file is not there now: its copy waits a while for it
            TagLeftovers.missing(started, System.currentTimeMillis())
        } else {
            descriptor.use { pfd ->
                if (sameBytes(pfd, copy) || intact(pfd.fd, name, before, copy)) {
                    note.delete()
                    copy.delete()
                    return null
                }
                TagLeftovers.damaged(statOf(pfd.fileDescriptor), stat, started).also {
                    if (it == Fate.PutBack) {
                        Log.w(TAG, "$name was left half written: it goes back as it was")
                        // the putting back is the file's last change now: a next start takes it for this write's
                        runCatching { replaceNote(note, saved.put("started_at", System.currentTimeMillis())) }
                        putBack(pfd.fileDescriptor, copy, size, before, pfd.fd, name, note)
                    } else {
                        Log.w(TAG, "$name changed after its tag write was cut short: the file stays, its copy is kept aside")
                    }
                }
            }
        }
        return if (fate == Fate.KeepAside) keepAside(copy, note, name, keepIn) else null
    }

    /** Whether the file behind [fd] has the audio of [copy]: as TagLib reads it, and decoded. */
    private fun intact(fd: Int, name: String, before: Audio, copy: File): Boolean = before.same(audioOf(fd, name)) && sameWaveform(fd, copy)

    /**
     * Whether [track], read through a descriptor of its own as any reader will, has the audio it had: as TagLib reads it
     * and, when [moved] (its size changed: the audio after the tag was moved), decoded the same as [copy]. That decodes
     * both whole, about 0.7 s for a 100 MB FLAC on a desktop core.
     */
    private fun readsAsBefore(context: Context, track: Uri, before: Audio, copy: File, moved: Boolean): Boolean =
        FileTags.read(context, track) { fd, name -> before.same(audioOf(fd, name)) && (!moved || sameWaveform(fd, copy)) } == true

    /** Whether the audio decoded from [fd] is [copy]'s: the peak and RMS of each of [POINTS] buckets the same. */
    private fun sameWaveform(fd: Int, copy: File): Boolean {
        val now = runCatching { NativeAudio.waveform("/proc/self/fd/$fd", POINTS) }.getOrNull() ?: return false
        val was = runCatching { NativeAudio.waveform(copy.path, POINTS) }.getOrNull() ?: return false
        return now.contentEquals(was)
    }

    /** Whether the file behind [descriptor] is, byte for byte, [copy]. */
    private fun sameBytes(descriptor: ParcelFileDescriptor, copy: File): Boolean = try {
        val size = copy.length()
        descriptor.statSize == size && FileInputStream(copy).use { input ->
            val mine = ByteArray(BUFFER)
            val theirs = ByteArray(BUFFER)
            var offset = 0L
            var same = true
            while (same && offset < size) {
                val n = input.read(mine)
                if (n <= 0) break
                var done = 0
                while (done < n) {
                    val read = Os.pread(descriptor.fileDescriptor, theirs, done, n - done, offset + done)
                    if (read <= 0) break
                    done += read
                }
                same = done == n && ByteBuffer.wrap(mine, 0, n) == ByteBuffer.wrap(theirs, 0, n)
                offset += n
            }
            same && offset == size
        }
    } catch (e: Exception) {
        false
    }

    /** The copy written over the file and the file cut to its length; both go once the file reads as it was, else they wait for the next start. */
    private fun putBack(target: FileDescriptor, copy: File, size: Long, before: Audio, fd: Int, name: String, note: File) {
        val back = try {
            FileInputStream(copy).use { input ->
                val buffer = ByteArray(BUFFER)
                var offset = 0L
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    var done = 0
                    while (done < n) {
                        val wrote = Os.pwrite(target, buffer, done, n - done, offset + done)
                        if (wrote <= 0) throw IOException("the file took no more")
                        done += wrote
                    }
                    offset += n
                }
                Os.ftruncate(target, size)
                runCatching { Os.fsync(target) }
                offset == size
            }
        } catch (e: Exception) {
            Log.w(TAG, "$name could not be put back: $e")
            false
        }
        if (back && before.same(audioOf(fd, name))) {
            note.delete()
            copy.delete()
        }
    }

    /**
     * [copy] moved into [folder], the folder the user sees, under its file's [name] (another beside a file of that name
     * there), and its note deleted. The copy there, or null when it cannot go there now (no folder, no room): then both
     * stay for the next start.
     */
    private fun keepAside(copy: File, note: File, name: String, folder: File?): File? {
        if (folder == null || !(folder.isDirectory || folder.mkdirs()) || folder.usableSpace < copy.length() + SPARE_BYTES) return null
        val target = File(folder, TagLeftovers.freeName(name.ifBlank { copy.name }) { File(folder, it).exists() })
        return try {
            FileInputStream(copy).use { input ->
                FileOutputStream(target).use { out ->
                    input.copyTo(out, BUFFER)
                    out.fd.sync()
                }
            }
            if (target.length() != copy.length()) throw IOException("the copy came out short")
            note.delete()
            copy.delete()
            Log.w(TAG, "the copy of $name from before its tag write is kept in $target")
            target
        } catch (e: Exception) {
            Log.w(TAG, "the copy of $name could not be kept in $folder: $e")
            target.delete()
            null
        }
    }

    private fun audioOf(fd: Int, name: String): Audio? = try {
        Audio.of(NativeAudio.tagAudio(fd, name))
    } catch (e: UnsatisfiedLinkError) {
        // an engine without the call: nothing is written
        null
    }

    /** The size and last change of the file behind [fd] as fstat sees them; null when it cannot tell. */
    private fun statOf(fd: FileDescriptor): TagLeftovers.Stat? = runCatching { Os.fstat(fd).let { TagLeftovers.Stat(it.st_size, it.st_mtime) } }.getOrNull()

    private fun dir(context: Context): File? = File(context.applicationContext.noBackupFilesDir, DIR).takeIf { it.isDirectory || it.mkdirs() }

    private fun copyOut(source: FileDescriptor, size: Long, copy: File) {
        FileOutputStream(copy).use { out ->
            val buffer = ByteArray(BUFFER)
            var offset = 0L
            while (offset < size) {
                val n = Os.pread(source, buffer, 0, minOf(BUFFER.toLong(), size - offset).toInt(), offset)
                if (n <= 0) throw IOException("the file ended before its length")
                out.write(buffer, 0, n)
                offset += n
            }
            out.fd.sync()
        }
    }

    /** The note beside a copy: the file, its size and last change before the write ([stat]), its audio and when the write began. */
    private fun writeNote(note: File, track: Uri, name: String, size: Long, stat: TagLeftovers.Stat?, audio: Audio) {
        val text = JSONObject()
            .put("uri", track.toString())
            .put("name", name)
            .put("size", size)
            .put("length_ms", audio.lengthMs)
            .put("rate", audio.rate)
            .put("channels", audio.channels)
            .put("started_at", System.currentTimeMillis())
        if (stat != null) text.put("modified_s", stat.modifiedS)
        FileOutputStream(note).use { out ->
            out.write(text.toString().toByteArray())
            out.fd.sync()
        }
    }

    /** [note] replaced by [text] whole or not at all: written beside it, then renamed over it. */
    private fun replaceNote(note: File, text: JSONObject) {
        val next = File(note.path + NEXT)
        FileOutputStream(next).use { out ->
            out.write(text.toString().toByteArray())
            out.fd.sync()
        }
        if (!next.renameTo(note)) {
            next.delete()
            throw IOException("the note was not replaced")
        }
    }
}
