package app.nagmedar.playback

/** What the session shows of a radio stream: the title line, the line under it and the album line. */
data class StreamDisplay(
    val title: String,
    val artist: String,
    val album: String,
    /** Whether the stream said what plays; false: only the station's name is known. */
    val hasTrack: Boolean,
)

/** The artist and title of a stream's "now playing" text; [artist] is null when the text has no artist in it. */
data class StreamTrack(val artist: String?, val title: String)

/**
 * S-1106: the "now playing" text a radio stream sends (ICY's StreamTitle, which libVLC reports as
 * the media's now playing) turned into what the notification, the lock screen and the player bars
 * show. Most stations send "Artist - Title"; the station's name goes under it.
 */
object RadioMetadata {

    /**
     * The logo a stream's notification and session show as artwork: its artwork address when it is
     * a stream (whether or not it sends a title) and the address is a web one; null otherwise.
     */
    fun logo(isStream: Boolean, artworkMrl: String?): String? {
        if (!isStream) return null
        val address = artworkMrl?.trim().orEmpty()
        val web = address.startsWith("http://", ignoreCase = true) || address.startsWith("https://", ignoreCase = true)
        return address.takeIf { web && it.none(Char::isWhitespace) }
    }

    private val SEPARATORS = listOf(" - ", " – ", " — ")

    private val NOTHING = setOf("unknown", "n/a", "null", "none", "bilinmiyor")

    /** The text cleaned up: spaces and control characters collapsed, one pair of wrapping quotes taken off. */
    fun clean(raw: String?): String {
        if (raw == null) return ""
        val spaced = buildString {
            var space = false
            for (c in raw) {
                if (c.isWhitespace() || c.isISOControl()) {
                    space = true
                } else {
                    if (space && isNotEmpty()) append(' ')
                    space = false
                    append(c)
                }
            }
        }
        val quoted = spaced.length >= 2 && ((spaced.first() == '\'' && spaced.last() == '\'') || (spaced.first() == '"' && spaced.last() == '"'))
        return if (quoted) spaced.substring(1, spaced.length - 1).trim() else spaced
    }

    /**
     * The track [nowPlaying] names, or null when it names none: nothing, only dashes and dots, a
     * web address, "unknown", or the station's own name (what stations send between songs).
     */
    fun track(nowPlaying: String?, station: String?): StreamTrack? {
        val text = clean(nowPlaying)
        if (text.isEmpty() || text.none { it.isLetterOrDigit() }) return null
        val lower = text.lowercase()
        if (lower in NOTHING || lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("www.")) return null
        val name = clean(station)
        if (name.isNotEmpty() && text.equals(name, ignoreCase = true)) return null
        val cut = SEPARATORS.map { text.indexOf(it) to it.length }.filter { it.first > 0 }.minByOrNull { it.first }
        if (cut != null) {
            val artist = text.substring(0, cut.first).trim()
            val title = text.substring(cut.first + cut.second).trim()
            // "Station - Bulletin": the station's name before the dash is no artist
            if (artist.isNotEmpty() && title.isNotEmpty() && !(name.isNotEmpty() && artist.equals(name, ignoreCase = true))) {
                return StreamTrack(artist, title)
            }
            if (title.isNotEmpty() && artist.isNotEmpty()) return StreamTrack(null, title)
        }
        return StreamTrack(null, text)
    }

    /**
     * What to show for [station] sending [nowPlaying]: an "Artist - Title" text is split, with the
     * station as the album; another text is the title, with the station under it; no text at all,
     * the station's name.
     */
    fun display(station: String?, nowPlaying: String?): StreamDisplay {
        val name = clean(station)
        val track = track(nowPlaying, station) ?: return StreamDisplay(name, "", "", hasTrack = false)
        return if (track.artist != null) {
            StreamDisplay(track.title, track.artist, name, hasTrack = true)
        } else {
            StreamDisplay(track.title, name, "", hasTrack = true)
        }
    }
}
