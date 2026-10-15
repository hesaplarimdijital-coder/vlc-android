package app.nagmedar.ui.radio

import app.nagmedar.playback.RadioMetadata
import app.nagmedar.ui.music.PlaylistFiles
import app.nagmedar.ui.music.PlaylistFormat
import java.util.Locale

/** A station a playlist file names: its stream address, the name it gives, and the logo and group an M3U8 may carry. */
data class PlaylistStation(val name: String, val url: String, val logoUrl: String? = null, val group: String? = null)

/** What reading a playlist file for stations gave. */
sealed interface StationImport {
    /** The stations found, and how many entries were left out (not a stream address, a repeat, over the limit). */
    data class Found(val stations: List<PlaylistStation>, val skipped: Int) : StationImport

    /** Not an M3U, M3U8 or PLS file. */
    data object NotAPlaylist : StationImport

    /** A playlist, but with no stream address in it. */
    data object Empty : StationImport

    /** Too large to be a station list. */
    data object TooLarge : StationImport
}

/**
 * S-1106: stations from M3U, M3U8 and PLS files. An M3U8's `#EXTINF` line gives the name and,
 * as IPTV lists write them, `tvg-logo` and `group-title`; a PLS gives `FileN` and `TitleN`. Only
 * stream addresses are taken (http, https, rtsp, rtmp, mms): a path on the device or a relative
 * entry is no station and is counted as left out. A name is never split into artist and title.
 */
object StationPlaylists {

    /** A file larger than this is not a station list. */
    const val MAX_BYTES = 2 * 1024 * 1024

    /** At most this many stations are taken from one file. */
    const val MAX_STATIONS = 5_000

    private const val MAX_NAME = 120

    private val SCHEMES = setOf("http", "https", "rtsp", "rtmp", "mms", "mmsh")

    private val ATTRIBUTE = Regex("""([A-Za-z0-9_-]+)\s*=\s*"([^"]*)"""")

    private val PLS_KEY = Regex("""^(File|Title)(\d{1,6})\s*=\s*(.*)$""", RegexOption.IGNORE_CASE)

    /** The stations in [bytes], a file called [fileName]: by its name's extension, else by its first bytes. */
    fun read(bytes: ByteArray, fileName: String): StationImport {
        if (bytes.size > MAX_BYTES) return StationImport.TooLarge
        val byName = PlaylistFormat.of(fileName)
        val format = (byName ?: PlaylistFiles.sniff(bytes)) ?: return StationImport.NotAPlaylist
        if (format == PlaylistFormat.XSPF) return StationImport.NotAPlaylist
        return parse(PlaylistFiles.decode(bytes, format), format)
    }

    /** The stations in a playlist's [text] of [format] (M3U, M3U8 or PLS). */
    fun parse(text: String, format: PlaylistFormat): StationImport {
        val entries = when (format) {
            PlaylistFormat.M3U, PlaylistFormat.M3U8 -> m3u(text)
            PlaylistFormat.PLS -> pls(text)
            PlaylistFormat.XSPF -> return StationImport.NotAPlaylist
        }
        val seen = HashSet<String>()
        val stations = ArrayList<PlaylistStation>()
        var skipped = 0
        for (entry in entries) {
            val url = entry.url.trim()
            if (!isStream(url) || !seen.add(url) || stations.size >= MAX_STATIONS) {
                skipped++
                continue
            }
            val name = RadioMetadata.clean(entry.name).take(MAX_NAME).trim().ifEmpty { host(url) }
            val logo = entry.logo?.trim()?.takeIf { (it.startsWith("http://", true) || it.startsWith("https://", true)) && it.none(Char::isWhitespace) }
            val group = RadioMetadata.clean(entry.group).take(MAX_NAME).trim().ifEmpty { null }
            stations += PlaylistStation(name, url, logo, group)
        }
        return if (stations.isEmpty()) StationImport.Empty else StationImport.Found(stations, skipped)
    }

    /** One entry as the file wrote it, before it is checked. */
    private class Entry(val url: String, val name: String?, val logo: String?, val group: String?)

    private fun m3u(text: String): List<Entry> {
        val entries = ArrayList<Entry>()
        var info: Entry? = null
        var group: String? = null
        for (raw in text.lineSequence()) {
            val line = raw.trim().removePrefix("﻿")
            when {
                line.isEmpty() -> Unit
                line.startsWith("#EXTINF:", ignoreCase = true) -> info = extinf(line.substring(EXTINF.length))
                line.startsWith("#EXTGRP:", ignoreCase = true) -> group = line.substring(EXTGRP.length).trim()
                line.startsWith("#") -> Unit
                else -> {
                    entries += Entry(line, info?.name, info?.logo, info?.group ?: group)
                    info = null
                }
            }
        }
        return entries
    }

    /** `-1 tvg-logo="…" group-title="…",Name`: the attributes, then the name after the first comma outside quotes. */
    private fun extinf(body: String): Entry {
        var quoted = false
        var comma = -1
        for (i in body.indices) {
            when (body[i]) {
                '"' -> quoted = !quoted
                ',' -> if (!quoted) {
                    comma = i
                    break
                }
            }
        }
        val head = if (comma >= 0) body.substring(0, comma) else body
        val attributes = ATTRIBUTE.findAll(head).associate { it.groupValues[1].lowercase(Locale.ROOT) to it.groupValues[2] }
        val name = (if (comma >= 0) body.substring(comma + 1) else null)?.trim()?.takeIf { it.isNotEmpty() } ?: attributes["tvg-name"]
        return Entry("", name, attributes["tvg-logo"], attributes["group-title"])
    }

    private fun pls(text: String): List<Entry> {
        val files = sortedMapOf<Int, String>()
        val titles = HashMap<Int, String>()
        for (raw in text.lineSequence()) {
            val match = PLS_KEY.find(raw.trim().removePrefix("﻿")) ?: continue
            val index = match.groupValues[2].toIntOrNull() ?: continue
            val value = match.groupValues[3].trim()
            if (match.groupValues[1].equals("File", ignoreCase = true)) files[index] = value else titles[index] = value
        }
        return files.map { (index, url) -> Entry(url, titles[index], null, null) }
    }

    /** Whether [url] is a stream address: a known scheme, a host, and no spaces. */
    fun isStream(url: String): Boolean {
        val scheme = url.substringBefore("://", "").lowercase(Locale.ROOT)
        if (scheme !in SCHEMES) return false
        val rest = url.substring(scheme.length + 3)
        return rest.isNotEmpty() && !rest.startsWith("/") && url.none { it.isWhitespace() || it.isISOControl() }
    }

    private fun host(url: String): String = url.substringAfter("://").substringBefore('/').substringBefore('?').substringBefore('#').substringAfterLast('@')

    private const val EXTINF = "#EXTINF:"
    private const val EXTGRP = "#EXTGRP:"
}
