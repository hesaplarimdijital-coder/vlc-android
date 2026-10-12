package app.nagmedar.ui.lyrics

/** S-1004: the tests' lyrics, written for them: "Sabaha Kadar" of the music samples' Ada Karan. */
object LyricsSamples {

    val track = LyricsTrack("file:///sdcard/Music/Ada Karan/Sabaha Kadar.flac", "Sabaha Kadar", "Ada Karan", "Gece Mavisi", 241_600)

    private const val LRC = """[ar:Ada Karan]
[ti:Sabaha Kadar]
[00:12.30]Gece mavisi çöktü şehre
[00:17.50]Sokak lambaları tek tek yandı
[00:22.80]Bir ezgi kaldı dudaklarımda
[00:28.10]<00:28.10>Sabaha <00:28.90>kadar <00:29.60>seni <00:30.40>andım
[00:33.40]
[00:38.00]Rüzgâr taşıdı adını
[00:43.20]Denizden dağlara kadar
[00:48.60]Bir ışık yanar uzakta
[00:54.00]Sabaha kadar, sabaha kadar"""

    /** Synced, the fourth line word-timed; from LRCLIB. */
    val synced: Lyrics = Lyrics.of(LRC, LyricsOrigin.Online)!!

    /** The same lines without their times, from the file's tags. */
    val plain: Lyrics = Lyrics.of(
        """Gece mavisi çöktü şehre
Sokak lambaları tek tek yandı
Bir ezgi kaldı dudaklarımda
Sabaha kadar seni andım

Rüzgâr taşıdı adını
Denizden dağlara kadar""",
        LyricsOrigin.Embedded,
    )!!

    /** In the fourth line, after "seni" and before "andım". */
    const val WORD_TIME_MS = 29_800L
}
