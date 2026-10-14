package app.nagmedar.audio.cue

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/**
 * S-808: a track of a CUE sheet: its [number] in the sheet, what the sheet says of it, and where
 * it is in its file: from [from] to [to] in CUE frames (1/75 s, a CD sector; [to] null: the
 * file's end). A track runs from its INDEX 01 to the next track's: a pregap (INDEX 00) plays
 * at the end of the track before, as a CD plays it.
 */
data class CueTrack(
    val number: Int,
    val title: String?,
    val performer: String?,
    val songwriter: String?,
    val isrc: String?,
    val from: Long,
    val to: Long?,
) {
    /** The track's length in milliseconds; null for the file's last track, whose end the file gives. */
    val lengthMs: Long? get() = to?.let { (it - from) * 1000 / CueSheet.FRAMES_PER_SECOND }
}

/** A FILE line of the sheet: the file's name as written, and the tracks in it. */
data class CueFile(val name: String, val tracks: List<CueTrack>)

/**
 * S-808: a CUE sheet as written for a single-file album (or a file per track): the album's
 * title and performer, its REM facts, and its files with their tracks.
 */
data class CueSheet(
    val title: String?,
    val performer: String?,
    val songwriter: String?,
    val genre: String?,
    val date: String?,
    val discNumber: Int?,
    val totalDiscs: Int?,
    val files: List<CueFile>,
) {
    val tracks: List<CueTrack> get() = files.flatMap { it.tracks }

    /** The year of REM DATE ("2026", "2026-03-01"); null when it says none. */
    val year: Int? get() = date?.trim()?.take(4)?.toIntOrNull()?.takeIf { it > 0 }

    /** The file whose name is [fileName] (case aside, the folder's part of either ignored), else the only file. */
    fun fileNamed(fileName: String): CueFile? {
        val wanted = fileName.substringAfterLast('/').substringAfterLast('\\')
        return files.firstOrNull { it.name.substringAfterLast('/').substringAfterLast('\\').equals(wanted, ignoreCase = true) }
            ?: files.singleOrNull()
    }

    companion object {
        const val FRAMES_PER_SECOND = 75L

        /**
         * The sheet in [text]. Lines it does not know are passed over; a sheet without a FILE or
         * a TRACK is null. INDEX 01 gives a track's start, the next track's its end.
         */
        fun parse(text: String): CueSheet? {
            var title: String? = null
            var performer: String? = null
            var songwriter: String? = null
            var genre: String? = null
            var date: String? = null
            var discNumber: Int? = null
            var totalDiscs: Int? = null
            val files = mutableListOf<CueFile>()
            var fileName: String? = null
            val tracks = mutableListOf<CueTrack>()
            // the track being read
            var number = 0
            var trackTitle: String? = null
            var trackPerformer: String? = null
            var trackSongwriter: String? = null
            var isrc: String? = null
            var start: Long? = null
            var inTrack = false

            fun endTrack() {
                if (inTrack && start != null) tracks += CueTrack(number, trackTitle, trackPerformer, trackSongwriter, isrc, start!!, null)
                inTrack = false
                trackTitle = null
                trackPerformer = null
                trackSongwriter = null
                isrc = null
                start = null
            }

            fun endFile() {
                endTrack()
                val name = fileName
                if (name != null && tracks.isNotEmpty()) {
                    // each track ends where the next begins
                    val closed = tracks.mapIndexed { i, track -> if (i + 1 < tracks.size) track.copy(to = tracks[i + 1].from) else track }
                        .filterIndexed { i, track -> i + 1 >= tracks.size || track.to!! >= track.from }
                    files += CueFile(name, closed)
                }
                fileName = null
                tracks.clear()
            }

            for (raw in text.lineSequence()) {
                val line = raw.trim()
                if (line.isEmpty()) continue
                val keyword = line.substringBefore(' ').uppercase()
                val rest = line.substringAfter(' ', "").trim()
                when (keyword) {
                    "REM" -> {
                        val remKey = rest.substringBefore(' ').uppercase()
                        val remValue = unquote(rest.substringAfter(' ', ""))
                        when (remKey) {
                            "GENRE" -> genre = remValue.takeIf { it.isNotBlank() }
                            "DATE" -> date = remValue.takeIf { it.isNotBlank() }
                            "DISCNUMBER" -> discNumber = remValue.trim().toIntOrNull()
                            "TOTALDISCS" -> totalDiscs = remValue.trim().toIntOrNull()
                        }
                    }
                    "TITLE" -> if (inTrack) trackTitle = unquote(rest).takeIf { it.isNotBlank() } else title = unquote(rest).takeIf { it.isNotBlank() }
                    "PERFORMER" -> if (inTrack) trackPerformer = unquote(rest).takeIf { it.isNotBlank() } else performer = unquote(rest).takeIf { it.isNotBlank() }
                    "SONGWRITER" -> if (inTrack) trackSongwriter = unquote(rest).takeIf { it.isNotBlank() } else songwriter = unquote(rest).takeIf { it.isNotBlank() }
                    "FILE" -> {
                        endFile()
                        // FILE "album.flac" WAVE: the name, quoted or not, before the type
                        fileName = if (rest.startsWith("\"")) rest.substring(1).substringBefore('"') else rest.substringBeforeLast(' ', rest)
                    }
                    "TRACK" -> {
                        endTrack()
                        val parts = rest.split(' ', '\t').filter { it.isNotEmpty() }
                        number = parts.firstOrNull()?.toIntOrNull() ?: (tracks.size + 1)
                        // only AUDIO tracks play; a data track (MODE1/2352) is passed over
                        inTrack = parts.getOrNull(1)?.uppercase()?.let { it == "AUDIO" } ?: true
                    }
                    "INDEX" -> if (inTrack) {
                        val parts = rest.split(' ', '\t').filter { it.isNotEmpty() }
                        val index = parts.getOrNull(0)?.toIntOrNull()
                        val at = parts.getOrNull(1)?.let(::frames)
                        if (at != null && (index == 1 || (index != 0 && start == null))) start = at
                    }
                    "ISRC" -> if (inTrack) isrc = unquote(rest).takeIf { it.isNotBlank() }
                    // PREGAP, POSTGAP, FLAGS, CATALOG, CDTEXTFILE: nothing to play by
                }
            }
            endFile()
            if (files.isEmpty()) return null
            return CueSheet(title, performer, songwriter, genre, date, discNumber, totalDiscs, files)
        }

        /** "mm:ss:ff" in CUE frames; null when it is not a time. */
        fun frames(time: String): Long? {
            val parts = time.trim().split(':')
            if (parts.size != 3) return null
            val minutes = parts[0].toLongOrNull() ?: return null
            val seconds = parts[1].toLongOrNull() ?: return null
            val frames = parts[2].toLongOrNull() ?: return null
            if (minutes < 0 || seconds !in 0..59 || frames !in 0..74) return null
            return (minutes * 60 + seconds) * FRAMES_PER_SECOND + frames
        }

        /** The text of a sheet's bytes: UTF-8 (with or without its mark), UTF-16 with a mark, else Windows-1254, as old Turkish sheets are. */
        fun decode(bytes: ByteArray): String {
            if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
                return String(bytes, 3, bytes.size - 3, StandardCharsets.UTF_8)
            }
            if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) return String(bytes, 2, bytes.size - 2, StandardCharsets.UTF_16LE)
            if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) return String(bytes, 2, bytes.size - 2, StandardCharsets.UTF_16BE)
            return try {
                StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString()
            } catch (e: CharacterCodingException) {
                String(bytes, Charset.forName("windows-1254"))
            }
        }

        private fun unquote(text: String): String {
            val t = text.trim()
            return if (t.length >= 2 && t.startsWith("\"") && t.endsWith("\"")) t.substring(1, t.length - 1) else t
        }
    }
}
