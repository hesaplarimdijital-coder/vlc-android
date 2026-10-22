package app.nagmedar.audio

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.system.Os
import android.util.Log
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
 * (length, sample rate, channels) must be as it was. A failure puts the copy back. A note beside
 * the copy names the file: a process that died while writing leaves both, and [recover] (at the
 * next start) puts the copy back when the file's audio is not as it was, else just deletes it.
 * A copy lives no longer than its write, but for a file that cannot be opened again (a card taken
 * out), which is waited for [KEEP_MS].
 */
internal object TagBackups {
    private const val TAG = "NagmedarAudio"
    private const val DIR = "tag-backup"
    private const val COPY = ".audio"
    private const val NOTE = ".json"

    /** Room left over beside the copy, for the app and the device. */
    private const val SPARE_BYTES = 32L * 1024 * 1024

    /** A copy whose file is not there (a card taken out) is kept this long for it. */
    private const val KEEP_MS = 7L * 24 * 60 * 60 * 1000

    /** The waveform's points the recovery compares: enough to tell moved audio. */
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
     * read back as it was. Writes the file: off the main thread.
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
                writeNote(note, track, name, size, before)
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
            // read back through a descriptor of its own, as any reader will
            if (written && before.same(FileTags.read(context, track) { readFd, readName -> Audio.of(NativeAudio.tagAudio(readFd, readName)) })) {
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
     * What a process that died while writing tags left: each file whose audio is not as it was (or cannot be told) gets
     * its copy back; the copies go. Reads and writes files: off the main thread.
     */
    fun recover(context: Context) {
        if (!NativeAudio.available) return
        synchronized(lock) {
            val dir = File(context.applicationContext.noBackupFilesDir, DIR)
            val files = dir.listFiles() ?: return
            val stems = files.filter { it.name.endsWith(NOTE) }.map { it.name.removeSuffix(NOTE) }.toSet()
            // a copy without its note: the write had not begun
            files.filter { it.name.endsWith(COPY) && it.name.removeSuffix(COPY) !in stems }.forEach { it.delete() }
            for (stem in stems) recoverOne(context, File(dir, stem + NOTE), File(dir, stem + COPY))
        }
    }

    private fun recoverOne(context: Context, note: File, copy: File) {
        val saved = runCatching { JSONObject(note.readText()) }.getOrNull() ?: JSONObject()
        val track = saved.optString("uri").takeIf { it.isNotEmpty() }?.let(Uri::parse)
        val size = saved.optLong("size", -1L)
        val before = Audio(saved.optLong("length_ms"), saved.optInt("rate"), saved.optInt("channels"))
        if (track == null || before.rate <= 0 || !copy.isFile || copy.length() != size) {
            note.delete()
            copy.delete()
            return
        }
        val name = saved.optString("name")
        val descriptor = runCatching { FileTags.openWritable(context.applicationContext, track) }.getOrNull()
        if (descriptor == null) {
            // the file is not there now: its copy waits a while for it
            if (System.currentTimeMillis() - note.lastModified() > KEEP_MS) {
                note.delete()
                copy.delete()
            }
            return
        }
        descriptor.use { pfd ->
            if (sameBytes(pfd, copy) || intact(pfd.fd, name, before, copy)) {
                note.delete()
                copy.delete()
            } else {
                Log.w(TAG, "$name was left half written: it goes back as it was")
                putBack(pfd.fileDescriptor, copy, size, before, pfd.fd, name, note)
            }
        }
    }

    /** Whether the file behind [fd] has the audio of [copy]: as TagLib reads it, and decoded. */
    private fun intact(fd: Int, name: String, before: Audio, copy: File): Boolean {
        if (!before.same(audioOf(fd, name))) return false
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

    private fun audioOf(fd: Int, name: String): Audio? = try {
        Audio.of(NativeAudio.tagAudio(fd, name))
    } catch (e: UnsatisfiedLinkError) {
        // an engine without the call: nothing is written
        null
    }

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

    private fun writeNote(note: File, track: Uri, name: String, size: Long, audio: Audio) {
        val text = JSONObject()
            .put("uri", track.toString())
            .put("name", name)
            .put("size", size)
            .put("length_ms", audio.lengthMs)
            .put("rate", audio.rate)
            .put("channels", audio.channels)
            .toString()
        FileOutputStream(note).use { out ->
            out.write(text.toByteArray())
            out.fd.sync()
        }
    }
}
