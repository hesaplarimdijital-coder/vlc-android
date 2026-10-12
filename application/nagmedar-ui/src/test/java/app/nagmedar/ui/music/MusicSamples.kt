package app.nagmedar.ui.music

import app.nagmedar.audio.Waveform
import app.nagmedar.ui.stats.TrackStats
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/** A small music library for the tests and screenshots: made-up artists, real-looking files. */
object MusicSamples {

    private val flac2496 = TrackTech("flac", "flac", 96_000, 24, 2, false, bitrate = 2_620_000, composer = "Buhûrîzâde Mustafa Itrî")
    private val flac1644 = TrackTech("flac", "flac", 44_100, 16, 2, false, bitrate = 910_000)
    private val alac24192 = TrackTech("alac", "mov,mp4,m4a,3gp,3g2,mj2", 192_000, 24, 2, false, bitrate = 5_100_000)
    private val mp3 = TrackTech("mp3", "mp3", 44_100, 0, 2, true, bitrate = 320_000)
    private val dsd64 = TrackTech("dsd_lsbf_planar", "dsf", 352_800, 0, 2, true, dsdRate = 2_822_400)
    private val opus = TrackTech("opus", "ogg", 48_000, 0, 2, true, bitrate = 160_000)

    private var ids = 0L

    private fun track(
        title: String,
        artist: String,
        album: String,
        year: Int,
        number: Int,
        durationMs: Long,
        tech: TrackTech?,
        genre: String,
        albumArtist: String? = null,
        plays: Int = 0,
        rating: Int = 0,
    ) = Track(
        ++ids, "file:///storage/emulated/0/Music/$artist/$album/$number.flac", title, artist, albumArtist, album, genre, year,
        number, 1, durationMs, "/storage/emulated/0/Music/$artist/$album", 1_760_000_000L - ids * 86_400, tech,
    ).also { track ->
        // S-808: the counts and stars kept beside the library
        if (plays > 0 || rating > 0) {
            sampleStats[track.uri] = TrackStats(rating, plays, skips = plays / 5, firstPlayedAt = 1_750_000_000_000L, lastPlayedAt = 1_760_000_000_000L - ids * 3_600_000)
        }
    }

    private val sampleStats = mutableMapOf<String, TrackStats>()

    val tracks: List<Track> = listOf(
        track("Segâh Tekbîr", "Kadri Erdem", "Itrî'nin İzinde", 1999, 1, 312_000, flac2496, "Klasik", plays = 14, rating = 5),
        track("Neva Kâr", "Kadri Erdem", "Itrî'nin İzinde", 1999, 2, 498_000, flac2496, "Klasik", plays = 9, rating = 4),
        track("Nihâvend Saz Semâîsi", "Kadri Erdem", "Itrî'nin İzinde", 1999, 3, 401_000, flac2496, "Klasik", plays = 3),
        track("Gece Yolcusu", "Cemre Kaya", "Gece Yolcusu", 2016, 1, 254_000, flac1644, "Rock", plays = 31, rating = 5),
        track("Yağmur", "Cemre Kaya", "Gece Yolcusu", 2016, 2, 231_000, flac1644, "Rock", plays = 22, rating = 3),
        track("Son Vapur", "Cemre Kaya", "Gece Yolcusu", 2016, 3, 287_000, mp3, "Rock", plays = 12),
        track("Uzak", "Çağla Akın", "Tek", 2021, 1, 198_000, alac24192, "Pop", plays = 7, rating = 2),
        track("Kuzey Işığı", "Çağla Akın", "Tek", 2021, 2, 216_000, alac24192, "Pop"),
        track("Ada", "Deniz Su", "Konuk", 2004, 4, 243_000, dsd64, "Caz", albumArtist = "Çeşitli Sanatçılar", plays = 2),
        track("Liman", "Ece Duru", "Konuk", 2004, 5, 205_000, opus, "Caz", albumArtist = "Çeşitli Sanatçılar"),
        track("İsimsiz", "Ece Duru", "Demo", 2023, 1, 97_000, null, "Caz"),
    )

    /** S-808: the samples' ratings and counts, by uri. */
    val stats: Map<String, TrackStats> get() = sampleStats

    /** A waveform that looks like music: verses, a louder chorus, a fade at the end. */
    val waveform: Waveform = Waveform(
        FloatArray(Waveforms.POINTS * 2) { i ->
            val b = i / 2
            val t = b.toFloat() / Waveforms.POINTS
            val section = if (t in 0.35f..0.6f || t in 0.78f..0.9f) 0.9f else 0.6f
            val fade = if (t > 0.92f) (1f - t) / 0.08f else if (t < 0.02f) t / 0.02f else 1f
            val beat = 0.75f + 0.25f * abs(sin(b * PI.toFloat() / 6f))
            val texture = 0.85f + 0.15f * sin(b * 1.7f) * sin(b * 0.31f)
            val peak = (section * fade * beat * texture).coerceIn(0f, 1f)
            if (i % 2 == 0) peak else peak * 0.32f
        },
    )
}
