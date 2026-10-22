package app.nagmedar.ui.radio.record

import app.nagmedar.playback.RadioMetadata
import java.time.Instant
import java.time.ZoneId

/** What [TrackRecorder] tells of its tracks. */
interface TrackListener {
    /** A new file was begun for the track [tag] names (null: the stream names none). */
    fun started(tag: TrackTag?)

    /** The track was finished and kept. */
    fun saved(track: SavedTrack)

    /** The track was finished and thrown away: it was shorter than the limit. */
    fun dropped(tag: TrackTag?)
}

/** A track as the recorder finished it. */
class SavedTrack(
    val tag: TrackTag?,
    val fileUri: String,
    val fileName: String,
    val startedAt: Long,
    val durationMs: Long,
    val sizeBytes: Long,
    val complete: Boolean,
    val format: AudioFormat,
)

/**
 * S-1107: writes a stream's units into one file per track. A track begins at a title change: an
 * ICY title that [title] hears at some audio byte ([splits]), or, in Ogg, the page where a
 * chained stream begins. The cut is made at the first unit (frame or page) that begins at or after
 * the byte the title arrived at, so files hold whole frames; it is not sample-accurate, since a
 * station's title arrives when its software says so, a little before or after the audio changes
 * (a pre- and post-padding of a few seconds hides that). The first title opens the first track:
 * the audio before it is the end of the song the title names, so it is held until the title is
 * heard. The first and last tracks of a recording began or ended without a title change and are
 * partial; so is a track shorter than the limit that is kept. A stream that sends no titles
 * ([splits] false) is one file.
 *
 * Padding: with [RecordingSettings.padBeforeSeconds] a new file begins with the last seconds of the
 * track before; with [RecordingSettings.padAfterSeconds] a file goes on a few seconds into the
 * next. Ogg pages are not padded.
 */
class TrackRecorder(
    private val station: RecordingStation,
    private val format: AudioFormat,
    private val storage: TrackStorage,
    private val settings: RecordingSettings,
    private val splits: Boolean,
    private val listener: TrackListener,
    private val clock: () -> Long,
    private val zone: ZoneId = ZoneId.systemDefault(),
    /** How much audio is held while the first title is waited for, bytes. */
    private val holdBytes: Long = DEFAULT_HOLD_BYTES,
) {
    private class Open(
        val output: TrackOutput,
        val tag: TrackTag?,
        val key: String,
        val startedAt: Long,
        val first: Boolean,
        val folder: String,
    ) {
        var micros = 0L
        var tail = 0L
    }

    private class Mark(val offset: Long, val tag: TrackTag?, val key: String)

    private var current: Open? = null
    private val lingering = ArrayList<Open>()
    private val marks = ArrayDeque<Mark>()
    private val held = ArrayList<MediaUnit>()
    private var heldBytes = 0L
    private val ring = ArrayDeque<MediaUnit>()
    private var ringMicros = 0L
    private var opened = false
    private val taken = HashMap<String, MutableSet<String>>()

    private val padBefore = if (format.isOgg) 0L else settings.padBeforeSeconds * 1_000_000L
    private val padAfter = if (format.isOgg) 0L else settings.padAfterSeconds * 1_000_000L

    /** Bytes of audio written to files so far, padding counted twice where files overlap. */
    var bytes = 0L
        private set

    /** How big the file being written is now, bytes. */
    val openSize: Long get() = current?.output?.size ?: 0L

    /** Room left where the file being written is; negative when not known or no file is open. */
    fun freeBytes(): Long = current?.output?.freeBytes() ?: -1L

    /** An ICY title ([raw] text, null: the block named none) arrived after [offset] bytes of audio. */
    fun title(offset: Long, raw: String?) {
        if (!splits || format.isOgg) return
        val tag = tagOf(raw)
        val key = keyOf(raw)
        if (!opened) {
            openFirst(tag, key)
            return
        }
        marks.addLast(Mark(offset, tag, key))
    }

    /** The next unit of the stream. */
    fun unit(unit: MediaUnit) {
        if (format.isOgg) {
            if (unit.startsTrack) {
                // a chained stream is a new track whatever its comments say
                val key = unit.tag?.let { "${it.artist}\u0000${it.title}" }.orEmpty()
                if (!opened) openFirst(unit.tag, key) else cut(unit.tag, key)
            }
        } else {
            applyMarks(unit.start)
        }
        if (!opened) {
            if (splits) {
                // waiting for the first title: the audio so far is its track's
                held += unit
                heldBytes += unit.data.size
                if (heldBytes > holdBytes) openFirst(null, "")
                return
            }
            openFirst(null, "")
        }
        write(unit)
    }

    /** The stream broke off or the recording ended: every file is finished; the one open is partial. */
    fun finish() {
        if (!opened && held.isNotEmpty()) openFirst(null, "")
        val last = current
        current = null
        if (last != null) close(last, complete = false)
        // the tracks still taking the next one's first seconds ended at a title change
        lingering.toList().forEach { close(it, complete = !it.first) }
        lingering.clear()
        marks.clear()
        ring.clear()
        ringMicros = 0
        opened = false
    }

    private fun applyMarks(start: Long) {
        var chosen: Mark? = null
        while (marks.isNotEmpty() && marks.first().offset <= start) chosen = marks.removeFirst()
        if (chosen != null && chosen.key != current?.key) cut(chosen.tag, chosen.key)
    }

    private fun openFirst(tag: TrackTag?, key: String) {
        opened = true
        current = begin(tag, key, first = true)
        val units = held.toList()
        held.clear()
        heldBytes = 0
        units.forEach { write(it) }
    }

    private fun cut(tag: TrackTag?, key: String) {
        val old = current ?: return
        val before = ring.toList()
        ring.clear()
        ringMicros = 0
        val next = begin(tag, key, first = false)
        current = next
        // the end of the track before, as the new one's beginning
        before.forEach { put(next, it) }
        if (padAfter > 0) {
            old.tail = padAfter
            lingering += old
        } else {
            close(old, complete = !old.first)
        }
    }

    private fun write(unit: MediaUnit) {
        val now = current ?: return
        put(now, unit)
        val done = ArrayList<Open>()
        for (old in lingering) {
            put(old, unit)
            old.tail -= unit.micros
            if (old.tail <= 0) done += old
        }
        if (done.isNotEmpty()) {
            lingering.removeAll(done.toSet())
            done.forEach { close(it, complete = !it.first) }
        }
        if (padBefore > 0) {
            ring.addLast(unit)
            ringMicros += unit.micros
            while (ring.size > 1 && ringMicros - ring.first().micros >= padBefore) ringMicros -= ring.removeFirst().micros
        }
    }

    private fun put(track: Open, unit: MediaUnit) {
        track.output.write(unit.data, 0, unit.data.size)
        track.micros += unit.micros
        bytes += unit.data.size
    }

    /** The file of a track that begins now: named, tagged, announced. */
    private fun begin(tag: TrackTag?, key: String, first: Boolean): Open {
        val startedAt = clock()
        val folder = TrackNames.folderName(station.name)
        val names = taken.getOrPut(folder) { storage.names(folder).toMutableSet() }
        val base = TrackNames.baseName(tag, station.name, startedAt, zone)
        val name = TrackNames.unique(base, format.extension, names)
        names += name.lowercase()
        val output = storage.create(folder, name, format.mime)
        if (format.taggable) {
            val date = Instant.ofEpochMilli(startedAt).atZone(zone).toLocalDate().toString()
            val id3 = Id3Tag.build(tag?.artist, tag?.title ?: name.substringBeforeLast('.'), station.name, date, station.name)
            output.write(id3, 0, id3.size)
        }
        listener.started(tag)
        return Open(output, tag, key, startedAt, first, folder)
    }

    private fun close(track: Open, complete: Boolean) {
        // a file that cannot be closed (the disk filled up) is still what was written
        runCatching { track.output.close() }
        val wall = clock() - track.startedAt
        val durationMs = if (track.micros > 0) track.micros / 1000 else wall
        val short = durationMs < settings.minSeconds * 1000L
        if (short && !settings.keepShort) {
            runCatching { track.output.delete() }
            listener.dropped(track.tag)
            return
        }
        listener.saved(
            SavedTrack(
                tag = track.tag,
                fileUri = track.output.uri,
                fileName = track.output.name,
                startedAt = track.startedAt,
                durationMs = durationMs,
                sizeBytes = track.output.size,
                complete = complete && !short,
                format = format,
            ),
        )
    }

    private fun tagOf(raw: String?): TrackTag? {
        val track = RadioMetadata.track(raw, station.name) ?: return null
        return TrackTag(track.artist, track.title)
    }

    /** Two titles that differ only in spacing and case are one. */
    private fun keyOf(raw: String?): String = RadioMetadata.clean(raw).lowercase()

    companion object {
        /** Audio held, bytes, while the first title is waited for when the stream's rhythm of titles is not known. */
        const val DEFAULT_HOLD_BYTES = 512L * 1024L
    }
}
