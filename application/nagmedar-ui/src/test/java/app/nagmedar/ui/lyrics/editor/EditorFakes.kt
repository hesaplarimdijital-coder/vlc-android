package app.nagmedar.ui.lyrics.editor

import android.net.Uri
import app.nagmedar.audio.TagWrite
import app.nagmedar.data.LyricsEntry
import app.nagmedar.data.LyricsSource
import app.nagmedar.ui.lyrics.LyricLine
import app.nagmedar.ui.lyrics.Lyrics
import app.nagmedar.ui.lyrics.LyricsCache
import app.nagmedar.ui.lyrics.LyricsOrigin
import app.nagmedar.ui.lyrics.LyricsTrack

/** The cache in memory. */
class MemoryLyricsCache : LyricsCache {
    val rows = LinkedHashMap<Pair<String, String>, LyricsEntry>()

    override fun forTrack(key: String) = rows.values.filter { it.trackKey == key }

    override fun save(entry: LyricsEntry) {
        rows[entry.trackKey to entry.source] = entry
    }

    override fun clearDownloaded(): Int = 0

    override fun downloadedCount(): Int = 0

    fun text(track: LyricsTrack, source: String): String? = rows[track.key to source]?.text
}

/** The .lrc beside a track as a string. */
class FakeSidecar(var text: String? = null, var result: (Uri?) -> SidecarResult = { SidecarResult.Written("Parça.lrc") }) : SidecarStore {
    var written: String? = null
    var folder: Uri? = null

    override fun read(track: LyricsTrack): String? = text

    override fun write(track: LyricsTrack, text: String, folder: Uri?): SidecarResult {
        this.folder = folder
        val outcome = result(folder)
        if (outcome is SidecarResult.Written) {
            written = text
            this.text = text
        }
        return outcome
    }
}

/** The tags as lyrics. */
class FakeTags(var lyrics: Lyrics? = null, var writable: Boolean = true) : TagStore {
    var written: List<LyricLine>? = null

    override fun read(track: LyricsTrack): Lyrics? = lyrics

    override fun write(track: LyricsTrack, lines: List<LyricLine>): TagWrite {
        if (!writable) return TagWrite.Failed
        written = lines
        lyrics = Lyrics(lines, LyricsOrigin.Embedded)
        return TagWrite.Written
    }
}

/** A player that stands at [positionMs]. */
class FakePlayer(override var positionMs: Long = 0, override var playing: Boolean = true, override var onTrack: Boolean = true) : EditorPlayer {
    val seeks = ArrayList<Long>()
    var toggles = 0

    override fun seekTo(ms: Long) {
        seeks += ms
        positionMs = ms
    }

    override fun togglePlay() {
        toggles++
        playing = !playing
    }
}

val SAMPLE_TRACK = LyricsTrack("file:///m/Parça.flac", "Parça", "Sanatçı", "Albüm", 215_400)

const val SAMPLE_TEXT = "Birinci satır\nİkinci satır\nÜçüncü satır\nDördüncü satır"

/** A source constant for the tests' readability. */
const val USER_SOURCE = LyricsSource.USER
