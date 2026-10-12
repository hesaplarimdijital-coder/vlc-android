package app.nagmedar.ui.music

import androidx.compose.runtime.Immutable
import app.nagmedar.ui.search.foldForSearch
import app.nagmedar.ui.stats.TrackStats
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Locale

/** S-805: what a rule's value is: text, a number, stars, a depth, days. */
enum class SmartKind { Text, Number, Stars, Depth, Date }

/** S-805: what a smart list's rule asks of a track. */
enum class SmartOp(val key: String) {
    Contains("contains"),
    NotContains("not_contains"),
    Is("is"),
    IsNot("is_not"),
    StartsWith("starts_with"),
    AtLeast("at_least"),
    AtMost("at_most"),

    /** In the last N days. */
    InLast("in_last"),

    /** Not in the last N days, or never. */
    NotInLast("not_in_last"),

    /** Never: a track not played yet. */
    Never("never"),
}

/** S-805: what a smart list's rule looks at; numbers in [SmartKind.Number] are kHz, minutes and counts. */
enum class SmartField(val key: String, val kind: SmartKind) {
    Title("title", SmartKind.Text),
    Artist("artist", SmartKind.Text),
    AlbumArtist("album_artist", SmartKind.Text),
    Album("album", SmartKind.Text),
    Genre("genre", SmartKind.Text),
    Composer("composer", SmartKind.Text),
    Folder("folder", SmartKind.Text),

    /** The format as the table writes it: "FLAC 24/96", "MP3 320 kb/s", "DSD64". */
    Format("format", SmartKind.Text),
    Depth("depth", SmartKind.Depth),

    /** The sample rate, kHz. */
    Rate("rate", SmartKind.Number),
    Year("year", SmartKind.Number),

    /** The length, minutes. */
    Duration("duration", SmartKind.Number),
    Rating("rating", SmartKind.Stars),
    Plays("plays", SmartKind.Number),
    Skips("skips", SmartKind.Number),
    LastPlayed("last_played", SmartKind.Date),
    Added("added", SmartKind.Date),
    ;

    /** The operators the field takes, the first the one a new rule starts with. */
    val ops: List<SmartOp>
        get() = when (kind) {
            SmartKind.Text -> listOf(SmartOp.Contains, SmartOp.NotContains, SmartOp.Is, SmartOp.IsNot, SmartOp.StartsWith)
            SmartKind.Number, SmartKind.Stars -> listOf(SmartOp.AtLeast, SmartOp.AtMost, SmartOp.Is, SmartOp.IsNot)
            SmartKind.Depth -> listOf(SmartOp.Is, SmartOp.IsNot)
            SmartKind.Date -> if (this == LastPlayed) listOf(SmartOp.InLast, SmartOp.NotInLast, SmartOp.Never) else listOf(SmartOp.InLast, SmartOp.NotInLast)
        }
}

/** S-805: one rule: [field] [op] [value] ("Puan en az 4", "Tür içerir caz", "Eklenme son 30 gün"). */
@Immutable
data class SmartRule(val field: SmartField, val op: SmartOp = field.ops.first(), val value: String = "") {
    /** A rule whose value is not given yet asks nothing: the list being made shows what it will. */
    val complete: Boolean
        // "this.field": in an accessor "field" is the property's own
        get() = when {
            op == SmartOp.Never -> true
            this.field.kind == SmartKind.Text -> value.isNotBlank()
            this.field.kind == SmartKind.Depth -> runCatching { Depth.valueOf(value) }.isSuccess
            else -> value.replace(',', '.').toDoubleOrNull() != null
        }

    /** Whether [track] (with its [stats]) is what the rule asks for, at [now]. */
    fun matches(track: Track, stats: TrackStats?, now: Long): Boolean {
        if (!complete) return true
        return when (field.kind) {
            SmartKind.Text -> {
                val text = foldForSearch(textOf(track).trim())
                val wanted = foldForSearch(value.trim())
                when (op) {
                    SmartOp.Contains -> wanted in text
                    SmartOp.NotContains -> wanted !in text
                    SmartOp.Is -> text == wanted
                    SmartOp.IsNot -> text != wanted
                    SmartOp.StartsWith -> text.startsWith(wanted)
                    else -> true
                }
            }
            SmartKind.Number, SmartKind.Stars -> {
                val wanted = value.replace(',', '.').toDouble()
                val have = numberOf(track, stats) ?: return op == SmartOp.IsNot
                when (op) {
                    SmartOp.AtLeast -> have >= wanted - EPSILON
                    SmartOp.AtMost -> have <= wanted + EPSILON
                    SmartOp.Is -> kotlin.math.abs(have - wanted) < EPSILON
                    SmartOp.IsNot -> kotlin.math.abs(have - wanted) >= EPSILON
                    else -> true
                }
            }
            SmartKind.Depth -> {
                val depth = track.tech?.depth
                when (op) {
                    SmartOp.Is -> depth?.name == value
                    SmartOp.IsNot -> depth?.name != value
                    else -> true
                }
            }
            SmartKind.Date -> {
                val at = dateOf(track, stats)
                val days = value.replace(',', '.').toDoubleOrNull() ?: 0.0
                val since = now - (days * DAY_MS).toLong()
                when (op) {
                    SmartOp.InLast -> at > 0 && at >= since
                    SmartOp.NotInLast -> at <= 0 || at < since
                    SmartOp.Never -> at <= 0
                    else -> true
                }
            }
        }
    }

    private fun textOf(track: Track): String = when (field) {
        SmartField.Title -> track.title
        SmartField.Artist -> track.artist
        SmartField.AlbumArtist -> track.albumArtist
        SmartField.Album -> track.album
        SmartField.Genre -> track.genre
        SmartField.Composer -> track.composer
        SmartField.Folder -> track.folder
        SmartField.Format -> track.tech?.let(::formatLabel)
        else -> null
    }.orEmpty()

    private fun numberOf(track: Track, stats: TrackStats?): Double? = when (field) {
        SmartField.Rate -> track.tech?.let { (if (it.dsdRate > 0) it.dsdRate else it.rate) / 1000.0 }
        SmartField.Year -> track.year?.takeIf { it > 0 }?.toDouble()
        SmartField.Duration -> track.durationMs.takeIf { it > 0 }?.let { it / 60_000.0 }
        SmartField.Rating -> (stats?.rating ?: 0).toDouble()
        SmartField.Plays -> (stats?.plays ?: 0).toDouble()
        SmartField.Skips -> (stats?.skips ?: 0).toDouble()
        else -> null
    }

    private fun dateOf(track: Track, stats: TrackStats?): Long = when (field) {
        SmartField.LastPlayed -> stats?.lastPlayedAt ?: 0
        SmartField.Added -> if (track.addedAt > 0) epochMillis(track.addedAt) else 0
        else -> 0
    }

    private companion object {
        const val EPSILON = 1e-6
        const val DAY_MS = 86_400_000.0
    }
}

/**
 * S-805: a smart list: the library's tracks its [rules] take (all of them, or any with
 * [matchAll] false), in [sort]'s order (the library's without), at most [limit]. It is read
 * again from the library each time, so it follows the library, the ratings and the plays.
 */
@Immutable
data class SmartList(
    val id: Long = 0,
    val name: String,
    val rules: List<SmartRule>,
    val matchAll: Boolean = true,
    val sort: MusicSort? = null,
    val limit: Int? = null,
) {
    /** The tracks of [tracks] the list takes, [stats] giving the ratings and the plays, at [now]. */
    fun select(tracks: List<Track>, stats: Map<String, TrackStats>, now: Long, locale: Locale): List<Track> {
        val asked = rules.filter { it.complete }
        val taken = if (asked.isEmpty()) {
            tracks
        } else {
            tracks.filter { track ->
                val trackStats = stats[track.statsKey]
                if (matchAll) asked.all { it.matches(track, trackStats, now) } else asked.any { it.matches(track, trackStats, now) }
            }
        }
        val ordered = sortTracks(taken, sort, locale, stats)
        return limit?.takeIf { it > 0 }?.let { ordered.take(it) } ?: ordered
    }

    /** The rules as the database keeps them: a line each, "field|op|value". */
    fun encodeRules(): String = rules.joinToString("\n") { "${it.field.key}|${it.op.key}|${URLEncoder.encode(it.value, "UTF-8")}" }

    /** The sort as the database keeps it: "column:true"; null for the library's order. */
    fun encodeSort(): String? = sort?.let { "${it.column.key}:${it.ascending}" }

    companion object {
        fun decodeRules(text: String?): List<SmartRule> = text.orEmpty().lines().mapNotNull { line ->
            val parts = line.split('|', limit = 3)
            if (parts.size < 2) return@mapNotNull null
            val field = SmartField.entries.firstOrNull { it.key == parts[0] } ?: return@mapNotNull null
            val op = SmartOp.entries.firstOrNull { it.key == parts[1] }?.takeIf { it in field.ops } ?: field.ops.first()
            SmartRule(field, op, URLDecoder.decode(parts.getOrElse(2) { "" }, "UTF-8"))
        }

        fun decodeSort(text: String?): MusicSort? {
            val key = text?.substringBefore(':') ?: return null
            val column = MusicColumn.entries.firstOrNull { it.key == key } ?: return null
            return MusicSort(column, text.substringAfter(':') == "true")
        }
    }
}

/** S-805: the smart lists a new one can start from, each with its name's string. */
enum class SmartTemplate(val build: (name: String) -> SmartList) {
    Favourites({ name -> SmartList(name = name, rules = listOf(SmartRule(SmartField.Rating, SmartOp.AtLeast, "4")), sort = MusicSort(MusicColumn.Rating, false)) }),
    MostPlayed({ name ->
        SmartList(name = name, rules = listOf(SmartRule(SmartField.Plays, SmartOp.AtLeast, "1")), sort = MusicSort(MusicColumn.Plays, false), limit = 50)
    }),
    NeverPlayed({ name -> SmartList(name = name, rules = listOf(SmartRule(SmartField.LastPlayed, SmartOp.Never))) }),
    RecentlyAdded({ name -> SmartList(name = name, rules = listOf(SmartRule(SmartField.Added, SmartOp.InLast, "30")), sort = MusicSort(MusicColumn.Added, false)) }),
    HiRes({ name ->
        SmartList(
            name = name,
            rules = listOf(
                SmartRule(SmartField.Depth, SmartOp.Is, Depth.Bits24.name),
                SmartRule(SmartField.Depth, SmartOp.Is, Depth.Bits32.name),
                SmartRule(SmartField.Depth, SmartOp.Is, Depth.Dsd.name),
                SmartRule(SmartField.Rate, SmartOp.AtLeast, "88.2"),
            ),
            matchAll = false,
        )
    }),
    Forgotten({ name ->
        SmartList(
            name = name,
            rules = listOf(SmartRule(SmartField.Rating, SmartOp.AtLeast, "4"), SmartRule(SmartField.LastPlayed, SmartOp.NotInLast, "90")),
            sort = MusicSort(MusicColumn.LastPlayed, true),
        )
    }),
}
