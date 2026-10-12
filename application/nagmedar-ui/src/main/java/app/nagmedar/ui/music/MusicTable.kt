package app.nagmedar.ui.music

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.nagmedar.ui.R
import app.nagmedar.ui.audio.formatName
import app.nagmedar.ui.audio.kiloHertz
import app.nagmedar.ui.audio.locale
import app.nagmedar.ui.components.ColumnWidth
import app.nagmedar.ui.components.RatingStars
import app.nagmedar.ui.components.TableColumn
import app.nagmedar.ui.library.formatDuration
import app.nagmedar.ui.stats.TrackStats
import app.nagmedar.ui.theme.NagTheme
import java.text.Collator
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/**
 * S-1001: the columns the music table can show; [key] is how the settings keep the choice, and
 * [Title] is always there.
 */
enum class MusicColumn(val key: String, @StringRes val label: Int, val width: ColumnWidth, val alignEnd: Boolean = false, val mono: Boolean = false) {
    Title("title", R.string.nag_column_title, ColumnWidth.Weight(1.6f)),
    Artist("artist", R.string.nag_column_artist, ColumnWidth.Weight(1f)),
    Album("album", R.string.nag_column_album, ColumnWidth.Weight(1f)),
    AlbumArtist("album_artist", R.string.nag_column_album_artist, ColumnWidth.Weight(1f)),
    Genre("genre", R.string.nag_column_genre, ColumnWidth.Weight(0.7f)),
    Composer("composer", R.string.nag_column_composer, ColumnWidth.Weight(1f)),
    Year("year", R.string.nag_column_year, ColumnWidth.Fixed(64.dp), alignEnd = true, mono = true),
    Number("number", R.string.nag_column_track_number, ColumnWidth.Fixed(48.dp), alignEnd = true, mono = true),
    Duration("duration", R.string.nag_column_duration, ColumnWidth.Fixed(68.dp), alignEnd = true, mono = true),
    /** S-808: the stars, set with a click. */
    Rating("rating", R.string.nag_column_rating, ColumnWidth.Fixed(108.dp)),
    Format("format", R.string.nag_column_format, ColumnWidth.Fixed(108.dp), mono = true),
    Depth("depth", R.string.nag_column_depth, ColumnWidth.Fixed(72.dp), alignEnd = true, mono = true),
    Rate("rate", R.string.nag_column_rate, ColumnWidth.Fixed(88.dp), alignEnd = true, mono = true),
    Bitrate("bitrate", R.string.nag_column_bitrate, ColumnWidth.Fixed(96.dp), alignEnd = true, mono = true),
    /** S-808: Nağmedar's own counts: a play once half the track (or four minutes) is heard. */
    Plays("plays", R.string.nag_column_plays, ColumnWidth.Fixed(72.dp), alignEnd = true, mono = true),
    Skips("skips", R.string.nag_column_skips, ColumnWidth.Fixed(72.dp), alignEnd = true, mono = true),
    LastPlayed("last_played", R.string.nag_column_last_played, ColumnWidth.Fixed(112.dp), alignEnd = true, mono = true),
    Added("added", R.string.nag_column_added, ColumnWidth.Fixed(104.dp), alignEnd = true, mono = true);

    companion object {
        /** What a new table shows. */
        val DEFAULT = listOf(Title, Artist, Album, Year, Format, Duration, Rating)

        /** The columns kept in the settings, Title first; the default for nothing kept. */
        fun parse(text: String?): List<MusicColumn> {
            val chosen = text?.split(',')?.mapNotNull { key -> entries.firstOrNull { it.key == key.trim() } }?.distinct().orEmpty()
            return if (chosen.isEmpty()) DEFAULT else listOf(Title) + (chosen - Title)
        }

        fun format(columns: List<MusicColumn>): String = columns.joinToString(",") { it.key }
    }
}

/** A sort of the music table: a column, either way. */
data class MusicSort(val column: MusicColumn, val ascending: Boolean)

/** "FLAC 24/96", "DSD64", "MP3 320 kb/s"; the technical notation is dotted in every language. */
fun formatLabel(tech: TrackTech): String {
    val codec = formatName(tech.codec, tech.container)
    return when {
        tech.dsdRate > 0 -> "DSD${Math.round(tech.dsdRate / 44100.0)}"
        tech.lossless -> "$codec ${tech.bits}/${shortKiloHertz(tech.rate)}"
        tech.bitrate > 0 -> "$codec ${tech.bitrate / 1000} kb/s"
        else -> codec
    }
}

/** 44100 → "44.1", 96000 → "96". */
private fun shortKiloHertz(rate: Int): String = if (rate % 1000 == 0) "${rate / 1000}" else "%.1f".format(Locale.ROOT, rate / 1000.0)

/**
 * The [columns] as the table shows them, with their texts in the interface's language; [stats]
 * gives the ratings and counts (S-808), and [onRate] sets a rating from the stars (null: they
 * only show).
 */
@Composable
fun rememberMusicColumns(
    columns: List<MusicColumn>,
    stats: Map<String, TrackStats> = emptyMap(),
    onRate: ((Track, Int) -> Unit)? = null,
): List<TableColumn<Track>> {
    val resources = LocalContext.current.resources
    val locale = resources.locale()
    val unknownArtist = stringResource(R.string.nag_unknown_artist)
    val titles = columns.map { stringResource(it.label) }
    val latestRate by rememberUpdatedState(onRate)
    val rates = onRate != null
    return remember(columns, locale, unknownArtist, titles, stats, rates) {
        val dates = DateFormat.getDateInstance(DateFormat.SHORT, locale)
        columns.mapIndexed { i, column ->
            val stars: (@Composable (Track, Boolean) -> Unit)? = if (column != MusicColumn.Rating) null else { track, selected ->
                RatingStars(
                    rating = stats[track.statsKey]?.rating ?: 0,
                    onRate = if (rates) ({ stars -> latestRate?.invoke(track, stars) }) else null,
                    emptyTint = if (selected) NagTheme.colors.onSelection.copy(alpha = 0.45f) else NagTheme.colors.textTertiary.copy(alpha = 0.6f),
                    filledTint = if (selected) NagTheme.colors.onSelection else NagTheme.colors.accent,
                )
            }
            TableColumn<Track>(titles[i], column.width, alignEnd = column.alignEnd, mono = column.mono, content = stars) { track ->
                when (column) {
                    MusicColumn.Title -> track.title
                    MusicColumn.Artist -> track.artist?.takeIf { it.isNotBlank() } ?: unknownArtist
                    MusicColumn.Album -> track.album.orEmpty()
                    MusicColumn.AlbumArtist -> track.albumArtist.orEmpty()
                    MusicColumn.Genre -> track.genre.orEmpty()
                    MusicColumn.Composer -> track.composer.orEmpty()
                    MusicColumn.Year -> track.year?.takeIf { it > 0 }?.toString().orEmpty()
                    MusicColumn.Number -> when {
                        track.trackNumber <= 0 -> ""
                        track.discNumber > 1 -> "${track.discNumber}-${track.trackNumber}"
                        else -> "${track.trackNumber}"
                    }
                    MusicColumn.Duration -> formatDuration(track.durationMs)
                    MusicColumn.Format -> track.tech?.let(::formatLabel).orEmpty()
                    MusicColumn.Depth -> track.tech?.let { tech ->
                        when {
                            tech.dsdRate > 0 -> resources.getString(R.string.nag_sp_bits, 1)
                            tech.lossless -> resources.getString(R.string.nag_sp_bits, tech.bits)
                            else -> ""
                        }
                    }.orEmpty()
                    MusicColumn.Rate -> track.tech?.let { kiloHertz(if (it.dsdRate > 0) it.dsdRate else it.rate, locale) }.orEmpty()
                    MusicColumn.Bitrate -> track.tech?.bitrate?.takeIf { it > 0 }?.let { resources.getString(R.string.nag_sp_kbps, (it / 1000).toInt()) }.orEmpty()
                    MusicColumn.Rating -> stats[track.statsKey]?.rating?.takeIf { it > 0 }?.let { "★".repeat(it) }.orEmpty()
                    MusicColumn.Plays -> stats[track.statsKey]?.plays?.takeIf { it > 0 }?.toString().orEmpty()
                    MusicColumn.Skips -> stats[track.statsKey]?.skips?.takeIf { it > 0 }?.toString().orEmpty()
                    MusicColumn.LastPlayed -> stats[track.statsKey]?.lastPlayedAt?.takeIf { it > 0 }?.let { dates.format(Date(it)) }.orEmpty()
                    MusicColumn.Added -> if (track.addedAt > 0) dates.format(Date(epochMillis(track.addedAt))) else ""
                }
            }
        }
    }
}

/** The medialibrary keeps seconds; picked folders, milliseconds. */
internal fun epochMillis(time: Long): Long = if (time < 100_000_000_000L) time * 1000 else time

/** [tracks] in the order [sort] asks, [stats] giving the ratings and counts; ties stay in album order. */
fun sortTracks(tracks: List<Track>, sort: MusicSort?, locale: Locale, stats: Map<String, TrackStats> = emptyMap()): List<Track> {
    if (sort == null) return tracks
    val collator = Collator.getInstance(locale).apply { strength = Collator.PRIMARY }
    val text: (Track) -> String? = when (sort.column) {
        MusicColumn.Title -> { t -> t.title }
        MusicColumn.Artist -> { t -> t.artist?.takeIf { it.isNotBlank() } }
        MusicColumn.Album -> { t -> t.album?.takeIf { it.isNotBlank() } }
        MusicColumn.AlbumArtist -> { t -> t.albumArtist?.takeIf { it.isNotBlank() } }
        MusicColumn.Genre -> { t -> t.genre?.takeIf { it.isNotBlank() } }
        MusicColumn.Composer -> { t -> t.composer }
        else -> { _ -> null }
    }
    val number: (Track) -> Double? = when (sort.column) {
        MusicColumn.Year -> { t -> t.year?.takeIf { it > 0 }?.toDouble() }
        MusicColumn.Number -> { t -> (t.discNumber * 10_000 + t.trackNumber).toDouble() }
        MusicColumn.Duration -> { t -> t.durationMs.toDouble() }
        MusicColumn.Format -> { t -> t.tech?.let { it.depth.ordinal * 1e9 + it.bits * 1e6 + it.rate } }
        MusicColumn.Depth -> { t -> t.tech?.let { if (it.dsdRate > 0) 1.0 else it.bits.toDouble() } }
        MusicColumn.Rate -> { t -> t.tech?.let { (if (it.dsdRate > 0) it.dsdRate else it.rate).toDouble() } }
        MusicColumn.Bitrate -> { t -> t.tech?.bitrate?.toDouble() }
        // unrated, never played: last either way
        MusicColumn.Rating -> { t -> stats[t.statsKey]?.rating?.takeIf { it > 0 }?.toDouble() }
        MusicColumn.Plays -> { t -> (stats[t.statsKey]?.plays ?: 0).toDouble() }
        MusicColumn.Skips -> { t -> (stats[t.statsKey]?.skips ?: 0).toDouble() }
        MusicColumn.LastPlayed -> { t -> stats[t.statsKey]?.lastPlayedAt?.takeIf { it > 0 }?.toDouble() }
        MusicColumn.Added -> { t -> epochMillis(t.addedAt).toDouble() }
        else -> { _ -> null }
    }
    val comparator: Comparator<Track> = when (sort.column) {
        MusicColumn.Title, MusicColumn.Artist, MusicColumn.Album, MusicColumn.AlbumArtist, MusicColumn.Genre, MusicColumn.Composer ->
            compareBy(nullsLast(collator), text)
        else -> compareBy(nullsLast(), number)
    }
    // the empty ones stay last either way
    val ordered = tracks.sortedWith(if (sort.ascending) comparator else Comparator { a, b ->
        val ka = text(a) ?: number(a)
        val kb = text(b) ?: number(b)
        when {
            ka == null && kb == null -> 0
            ka == null -> 1
            kb == null -> -1
            else -> comparator.compare(b, a)
        }
    })
    return ordered
}
