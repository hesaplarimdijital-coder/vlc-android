package app.nagmedar.ui.music

import androidx.compose.runtime.Immutable
import java.text.Collator
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale

/** S-803: a music file's technical side as the engine's probe read it. */
@Immutable
data class TrackTech(
    val codec: String,
    val container: String,
    val rate: Int,
    val bits: Int,
    val channels: Int,
    val isFloat: Boolean,
    val dsdRate: Int = 0,
    val bitrate: Long = 0,
    val composer: String? = null,
) {
    /** Integer PCM or DSD; a float or lossy codec is not. */
    val lossless: Boolean get() = dsdRate > 0 || (!isFloat && bits > 0)

    /** The depth class the filter sorts it into. */
    val depth: Depth
        get() = when {
            dsdRate > 0 -> Depth.Dsd
            !lossless -> Depth.Lossy
            bits <= 16 -> Depth.Bits16
            bits <= 24 -> Depth.Bits24
            else -> Depth.Bits32
        }

    /** The rate class the filter sorts it into; DSD by the PCM it decodes to does not count. */
    val rateBand: RateBand?
        get() = if (dsdRate > 0) null else RateBand.entries.firstOrNull { rate <= it.maxHz }
}

/** The filter's depths: 16, 24 and 32 bit lossless, lossy, DSD. */
enum class Depth { Bits16, Bits24, Bits32, Lossy, Dsd }

/** The filter's rates: 44.1, 48, 88.2, 96, 176.4, 192 kHz and above. */
enum class RateBand(val maxHz: Int) {
    R44(44_100), R48(48_000), R88(88_200), R96(96_000), R176(176_400), R192(Int.MAX_VALUE);
}

/** S-803: a track as the music library shows it; [tech] once the probe has read the file. */
@Immutable
data class Track(
    val id: Long,
    val uri: String,
    val title: String,
    val artist: String?,
    val albumArtist: String?,
    val album: String?,
    val genre: String?,
    val year: Int?,
    val trackNumber: Int,
    val discNumber: Int,
    val durationMs: Long,
    /** The folder the file is in: its path, or the document tree's. */
    val folder: String,
    val addedAt: Long = 0,
    val tech: TrackTech? = null,
) {
    /** Who the album is filed under: its album artist, else the track's artist. */
    val filedArtist: String? get() = albumArtist?.takeIf { it.isNotBlank() } ?: artist?.takeIf { it.isNotBlank() }

    val composer: String? get() = tech?.composer?.takeIf { it.isNotBlank() }

    /** S-808: what its rating and plays are kept by. */
    val statsKey: String get() = uri
}

/**
 * A track's year from the medialibrary's release date: the medialibrary keeps a "date=2026" tag
 * as seconds since 1970 (UTC), a CUE sheet's track carries the year itself; 0 or less is unknown.
 */
fun releaseYearOf(value: Int): Int? = when {
    value <= 0 -> null
    value <= 9999 -> value
    else -> Instant.ofEpochSecond(value.toLong()).atZone(ZoneOffset.UTC).year
}

/** The ways through the library (S-803). */
enum class MusicView { Tracks, Artists, Albums, Genres, Composers, Years, Folders }

/**
 * One artist, album, genre, composer, year or folder, with its tracks in album order. [key] is
 * stable across reloads; [title] is null for the group of tracks without that tag.
 */
@Immutable
data class MusicGroup(val key: String, val title: String?, val artist: String?, val year: Int?, val tracks: List<Track>) {
    val durationMs: Long get() = tracks.sumOf { it.durationMs }

    /** The albums of an artist's group, or the artists of an album's. */
    val albumCount: Int get() = tracks.mapTo(HashSet()) { it.album.orEmpty() }.size
}

/** S-803: the bit depth and sample rate filter; empty sets let everything through. */
@Immutable
data class TechFilter(val depths: Set<Depth> = emptySet(), val rates: Set<RateBand> = emptySet()) {
    val active: Boolean get() = depths.isNotEmpty() || rates.isNotEmpty()

    /** A track the probe has not read yet passes only an empty filter. */
    fun matches(track: Track): Boolean {
        if (!active) return true
        val tech = track.tech ?: return false
        if (depths.isNotEmpty() && tech.depth !in depths) return false
        if (rates.isNotEmpty() && tech.rateBand !in rates) return false
        return true
    }
}

/**
 * S-803: the library's groupings over one list of tracks, the medialibrary's and the picked
 * folders' alike: artists by album artist, albums by artist and name, genres, composers, years
 * (newest first) and folders, each sorted as people read them ([locale]: "Çağatay" after "Cem").
 */
class MusicLibrary(val tracks: List<Track>, private val locale: Locale = Locale.getDefault()) {

    /** The reader's collator; one for each grouping or sort, as a collator is not for two threads at once. */
    private fun collator(): Collator = Collator.getInstance(locale).apply { strength = Collator.PRIMARY }

    /** Titles in the reader's order; the untitled group last. */
    private fun byTitle(collator: Collator) = Comparator<MusicGroup> { a, b ->
        when {
            a.title == null && b.title == null -> 0
            a.title == null -> 1
            b.title == null -> -1
            else -> collator.compare(a.title, b.title)
        }
    }

    /** The groupings made so far, by view and filter: made once for a library, by whichever thread asks first. */
    private val made = HashMap<Pair<MusicView, TechFilter>, List<MusicGroup>>()

    /** The tables' orders made so far (see [ordered]). */
    private val orders = HashMap<Any, List<Track>>()

    /**
     * A table's order of this library's tracks, [compute]d once for [key] (the view, the filter, the group, the sort, the locale): a
     * screen that comes back to the same library does not sort it again. Safe from any thread.
     */
    fun ordered(key: Any, compute: () -> List<Track>): List<Track> {
        madeOrder(key)?.let { return it }
        val order = compute()
        return synchronized(this) {
            orders[key] ?: order.also {
                if (orders.size >= MAX_ORDERS) orders.clear()
                orders[key] = it
            }
        }
    }

    /** The order made for [key] already, or null. */
    fun madeOrder(key: Any): List<Track>? = synchronized(this) { orders[key] }

    /** The grouping of [view] and [filter] when it has been made already; null when [groups] would make it now. */
    fun madeGroups(view: MusicView, filter: TechFilter = TechFilter()): List<MusicGroup>? = synchronized(this) { made[view to filter] }

    /**
     * The groups of [view] with [filter], sorted with the reader's collator: a hundred milliseconds or so for a large
     * library the first time, then kept. Safe from any thread; nothing is held while they are made.
     */
    fun groups(view: MusicView, filter: TechFilter = TechFilter()): List<MusicGroup> {
        madeGroups(view, filter)?.let { return it }
        val grouping = group(view, filter, collator())
        return synchronized(this) { made.getOrPut(view to filter) { grouping } }
    }

    private fun group(view: MusicView, filter: TechFilter, collator: Collator): List<MusicGroup> {
        val shown = if (filter.active) tracks.filter(filter::matches) else tracks
        val byTitle = byTitle(collator)
        return when (view) {
            MusicView.Tracks -> listOf(MusicGroup("", null, null, null, sortedTracks(shown, collator)))
            MusicView.Artists -> group(shown, collator, { it.filedArtist?.let { artistKey(it) } }, { it.filedArtist }).sortedWith(byTitle)
            MusicView.Albums -> shown.groupBy { albumKey(it.filedArtist, it.album) }
                .map { (key, list) ->
                    val first = list.first()
                    MusicGroup(key, first.album?.takeIf { it.isNotBlank() }, first.filedArtist, list.mapNotNull { it.year }.maxOrNull(), sortedTracks(list, collator))
                }
                .sortedWith(compareBy<MusicGroup, String?>(nullsLast(collator)) { it.artist }.then(compareBy(nullsLast()) { it.year }).then(byTitle))
            MusicView.Genres -> group(shown, collator, { it.genre?.takeIf(String::isNotBlank)?.let { fold(it) } }, { it.genre }).sortedWith(byTitle)
            MusicView.Composers -> group(shown, collator, { it.composer?.let { fold(it) } }, { it.composer }).sortedWith(byTitle)
            MusicView.Years -> shown.groupBy { it.year?.takeIf { y -> y > 0 } }
                .map { (year, list) -> MusicGroup("year:${year ?: ""}", year?.toString(), null, year, sortedTracks(list, collator)) }
                .sortedWith(compareByDescending<MusicGroup> { it.year ?: Int.MIN_VALUE })
            MusicView.Folders -> group(shown, collator, { it.folder.takeIf(String::isNotEmpty) }, { it.folder.takeIf(String::isNotEmpty) }).sortedWith(byTitle)
        }
    }

    /** Groups by [key] (case and accents folded), titled by the most common spelling of [title]. */
    private fun group(tracks: List<Track>, collator: Collator, key: (Track) -> String?, title: (Track) -> String?): List<MusicGroup> =
        tracks.groupBy(key).map { (k, list) ->
            val spelling = list.mapNotNull(title).groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
            MusicGroup("${k ?: ""}", if (k == null) null else spelling, null, null, sortedTracks(list, collator))
        }

    /** Album, disc and track number, then title: as an album plays. */
    fun sortTracks(list: List<Track>): List<Track> = sortedTracks(list, collator())

    private fun sortedTracks(list: List<Track>, collator: Collator): List<Track> = list.sortedWith(
        compareBy<Track, String?>(nullsLast(collator)) { it.album }
            .thenBy { it.discNumber }
            .thenBy { it.trackNumber }
            .thenBy(collator) { it.title },
    )

    companion object {
        /** The orders kept per library: the groups a session opens. */
        private const val MAX_ORDERS = 24

        /** The key of the artists' group of [filedArtist] (an album artist, else the track's): case and spaces folded. */
        fun artistKey(filedArtist: String): String = fold(filedArtist)

        /** The key of the albums' group of [album] filed under [filedArtist]. */
        fun albumKey(filedArtist: String?, album: String?): String =
            "album:" + (filedArtist?.let { fold(it) } ?: "") + "\u0000" + (album?.let { fold(it) } ?: "")

        private fun fold(text: String): String = text.trim().lowercase(Locale.ROOT)
    }
}
