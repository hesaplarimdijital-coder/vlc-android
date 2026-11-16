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

/**
 * 1.0.3: a music file's ReplayGain tags (an Opus file's R128 gains as ReplayGain's) as the probe read
 * them ([TrackTechIndex.gains], beside its [TrackTech]): the gains in dB, the peaks at full scale 1.0;
 * null where the file has no such tag.
 */
@Immutable
data class ReplayGainTags(
    val trackGainDb: Double? = null,
    val trackPeak: Double? = null,
    val albumGainDb: Double? = null,
    val albumPeak: Double? = null,
)

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
enum class MusicView {
    Tracks, Artists, Albums, Genres, Composers, Years, Folders,

    /** 1.0: the tracks played last, newest first ("Son çalınanlar"); a phone has them as the shelf above the library instead. */
    Recent,
    ;

    /** Whether the view lists groups to open; the tracks and the recently played are one list. */
    val grouped: Boolean get() = this != Tracks && this != Recent
}

/**
 * One artist, album, genre, composer, year or folder, with its tracks in album order. [key] is
 * stable across reloads; [title] is null for the group of tracks without that tag.
 */
@Immutable
data class MusicGroup(val key: String, val title: String?, val artist: String?, val year: Int?, val tracks: List<Track>) {
    val durationMs: Long get() = tracks.sumOf { it.durationMs }

    /** The albums of an artist's group, or the artists of an album's. */
    val albumCount: Int get() = tracks.mapTo(HashSet()) { it.album.orEmpty() }.size

    /** 1.1.1: when its newest track was added (the "Eklenme" order); 0 for none known. */
    val addedAt: Long get() = tracks.maxOfOrNull { epochMillis(it.addedAt) } ?: 0L
}

/** 1.1.1 (the design's "A–Z" button): what a way in's groups are in order by. */
enum class GroupOrder { Name, Artist, Year, Added }

/** 1.1.1: a way in's groups' order and its direction; null where the view's own ([GroupSort.default]). */
@Immutable
data class GroupSort(val order: GroupOrder, val ascending: Boolean) {
    companion object {
        /** A way in's own order: the years newest first, every other one by name, A–Z. */
        fun default(view: MusicView): GroupSort = if (view == MusicView.Years) GroupSort(GroupOrder.Year, false) else GroupSort(GroupOrder.Name, true)

        /** The orders [view]'s button offers, its own first. */
        fun choices(view: MusicView): List<GroupOrder> = when (view) {
            MusicView.Albums -> listOf(GroupOrder.Name, GroupOrder.Artist, GroupOrder.Year, GroupOrder.Added)
            MusicView.Years -> listOf(GroupOrder.Year, GroupOrder.Added)
            else -> listOf(GroupOrder.Name, GroupOrder.Added)
        }
    }
}

/**
 * S-803: the bit depth and sample rate filter; empty sets let everything through. 1.4.0: with the library's [checks]
 * (Metamatik's lists), which the tracks do not carry: [rememberCheckedLibrary] lets through what they found.
 */
@Immutable
data class TechFilter(
    val depths: Set<Depth> = emptySet(),
    val rates: Set<RateBand> = emptySet(),
    val checks: Set<LibraryCheck> = emptySet(),
) {
    val active: Boolean get() = depths.isNotEmpty() || rates.isNotEmpty() || checks.isNotEmpty()

    /** A track the probe has not read yet passes only an empty filter. */
    fun matches(track: Track): Boolean {
        if (depths.isEmpty() && rates.isEmpty()) return true
        val tech = track.tech ?: return false
        if (depths.isNotEmpty() && tech.depth !in depths) return false
        if (rates.isNotEmpty() && tech.rateBand !in rates) return false
        return true
    }
}

/**
 * S-803: the library's groupings over one list of tracks, the medialibrary's and the picked
 * folders' alike: artists by album artist, albums by name (1.1.1, the design's A–Z; by their artists
 * before), genres, composers, years (newest first) and folders, each sorted as people read them
 * ([locale]: "Çağatay" after "Cem"); in another [GroupSort] on asking ([orderedGroups]).
 * The Recent view's list is the playback history's, which changes as tracks play: it is not part of
 * the library (1.0: a new history does not make the library again), see [recentGroups].
 */
class MusicLibrary(val tracks: List<Track>, val locale: Locale = Locale.getDefault()) {

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
                .sortedWith(byTitle.then(compareBy(nullsLast(collator)) { it.artist }).then(compareBy(nullsLast()) { it.year }))
            MusicView.Genres -> group(shown, collator, { it.genre?.takeIf(String::isNotBlank)?.let { fold(it) } }, { it.genre }).sortedWith(byTitle)
            MusicView.Composers -> group(shown, collator, { it.composer?.let { fold(it) } }, { it.composer }).sortedWith(byTitle)
            MusicView.Years -> shown.groupBy { it.year?.takeIf { y -> y > 0 } }
                .map { (year, list) -> MusicGroup("year:${year ?: ""}", year?.toString(), null, year, sortedTracks(list, collator)) }
                .sortedWith(compareByDescending<MusicGroup> { it.year ?: Int.MIN_VALUE })
            MusicView.Folders -> group(shown, collator, { it.folder.takeIf(String::isNotEmpty) }, { it.folder.takeIf(String::isNotEmpty) }).sortedWith(byTitle)
            // the history is the caller's: recentGroups
            MusicView.Recent -> listOf(MusicGroup(RECENT_KEY, null, null, null, emptyList()))
        }
    }

    /** 1.1.1: the groupings in another order than their own, made so far ([orderedGroups]). */
    private val groupOrders = HashMap<Triple<MusicView, TechFilter, GroupSort>, List<MusicGroup>>()

    /**
     * 1.1.1 (the design's "A–Z" button): the groups of [view] with [filter] in [sort]; null or the view's own order is
     * [groups]' list. Another order is made once from it and kept. Safe from any thread.
     */
    fun orderedGroups(view: MusicView, filter: TechFilter = TechFilter(), sort: GroupSort?): List<MusicGroup> {
        val base = groups(view, filter)
        if (sort == null || sort == GroupSort.default(view)) return base
        val key = Triple(view, filter, sort)
        synchronized(this) { groupOrders[key] }?.let { return it }
        val collator = collator()
        val byTitle = byTitle(collator)
        val (named, nameless) = base.partition { it.title != null }
        val ordered = when (sort.order) {
            GroupOrder.Name -> if (sort.ascending) base else named.reversed() + nameless
            GroupOrder.Artist -> {
                val byArtist = compareBy<MusicGroup, String?>(nullsLast(collator)) { it.artist }.then(compareBy(nullsLast()) { it.year }).then(byTitle)
                base.sortedWith(if (sort.ascending) byArtist else compareBy<MusicGroup> { it.artist == null }.then(byArtist.reversed()))
            }
            GroupOrder.Year -> base.sortedWith(
                compareBy<MusicGroup> { it.year == null }
                    .then(if (sort.ascending) compareBy { it.year ?: 0 } else compareByDescending { it.year ?: 0 })
                    .then(byTitle),
            )
            GroupOrder.Added -> base.sortedWith(
                (if (sort.ascending) compareBy<MusicGroup> { it.addedAt } else compareByDescending { it.addedAt }).then(byTitle),
            )
        }
        return synchronized(this) { groupOrders.getOrPut(key) { ordered } }
    }

    /** The tracks by uri, made once (by whichever thread asks first). */
    private val byUri: Map<String, Track> by lazy { tracks.associateBy { it.uri } }

    /**
     * The Recent view's one group: the tracks of [recent], the uris of the tracks played last (newest first, the
     * playback history's order, not the album's), those of this library that [filter] lets through.
     */
    fun recentGroups(recent: List<String>, filter: TechFilter = TechFilter()): List<MusicGroup> {
        val played = recent.mapNotNull { byUri[it] }
        return listOf(MusicGroup(RECENT_KEY, null, null, null, if (filter.active) played.filter(filter::matches) else played))
    }

    /** Groups by [key] (case and accents folded), titled by the most common spelling of [title]. */
    private fun group(tracks: List<Track>, collator: Collator, key: (Track) -> String?, title: (Track) -> String?): List<MusicGroup> =
        tracks.groupBy(key).map { (k, list) ->
            val spelling = list.mapNotNull(title).groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
            MusicGroup("${k ?: ""}", if (k == null) null else spelling, null, null, sortedTracks(list, collator))
        }

    /** Album, disc and track number, then title: as an album plays. */
    fun sortTracks(list: List<Track>): List<Track> = sortedTracks(list, collator())

    /** 1.0.3: every track by its title in the reader's order (Android Auto's every track, A–Z), made once ([ordered]). */
    fun tracksByTitle(): List<Track> = ordered(TITLE_ORDER) {
        val collator = collator()
        tracks.sortedWith(compareBy(collator) { it.title })
    }

    private fun sortedTracks(list: List<Track>, collator: Collator): List<Track> = list.sortedWith(
        compareBy<Track, String?>(nullsLast(collator)) { it.album }
            .thenBy { it.discNumber }
            .thenBy { it.trackNumber }
            .thenBy(collator) { it.title },
    )

    companion object {
        /** The Recent view's one group. */
        const val RECENT_KEY = "recent"

        /** The key of [tracksByTitle]'s order among the tables' orders. */
        private const val TITLE_ORDER = "title-order"

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
