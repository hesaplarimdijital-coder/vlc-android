package app.nagmedar.ui.player.subtitles.tools

import app.nagmedar.ui.player.subtitles.SavedSubtitle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

/** A Turkish SRT as a release for the hard of hearing has it: sound descriptions, speaker labels, music. */
internal const val SDH_SRT = """1
00:00:02,400 --> 00:00:04,900
[kapı çarpılır]

2
00:00:05,000 --> 00:00:07,600
KEMAL: Yine mi geç kaldın?

3
00:00:08,100 --> 00:00:10,300
- Trafik vardı.
- (iç çeker) Hep aynı bahane.

4
00:00:11,000 --> 00:00:13,200
♪ yağmur yağar ♪

5
00:00:14,000 --> 00:00:16,400
<i>Dışarıda fırtına var.</i>

6
00:00:16,900 --> 00:00:19,500
AYŞE: Eve gitmeliyiz.
[telefon çalar]

7
00:00:21,000 --> 00:00:23,800
Yarın sabah erkenden çıkarız.

8
00:00:24,100 --> 00:00:26,000
(MÜZİK DEVAM EDER)

9
00:00:27,300 --> 00:00:30,100
Gelmeyecek misin?
Seni bekliyorum.
"""

/** The same kind of file as a film's editor sees it: one line ends before it starts, two overlap. */
internal const val FAULTY_SRT = """1
00:00:02,400 --> 00:00:04,900
Kapıyı kim çaldı?

2
00:00:05,000 --> 00:00:07,600
Bilmiyorum, bakmadım.

3
00:00:07,200 --> 00:00:09,000
Bakmalısın.

4
00:00:10,000 --> 00:00:09,000
Neden ben?

5
00:00:11,000 --> 00:00:13,200
Çünkü sen kapıdasın.
"""

internal val SAMPLE_FILE = ToolsFile("content://subs/Kuzey%20Isigi.tr.srt", "Kuzey Işığı.tr.srt")

/** Files by their address, and what was written. */
internal class FakeToolsIo(
    val files: MutableMap<String, ByteArray> = mutableMapOf(),
    /** Whether the folder beside the original takes a new file. */
    var beside: Boolean = true,
    var writes: Boolean = true,
    /** The document the system's "save as" is taken to be: the original's own address makes it [WriteOutcome.SameFile]. */
    var sameFile: Boolean = false,
) : SubtitleToolsIo {
    class Saved(val original: ToolsFile, val name: String, val bytes: ByteArray)

    val saved = mutableListOf<Saved>()
    val written = mutableListOf<Pair<String, ByteArray>>()

    override suspend fun read(file: ToolsFile): ByteArray? = files[file.uri]

    override suspend fun saveBeside(file: ToolsFile, name: String, bytes: ByteArray): SavedSubtitle? {
        if (!beside) return null
        saved += Saved(file, name, bytes)
        return SavedSubtitle("content://subs/$name", name, beside = true)
    }

    override suspend fun write(uri: String, source: ToolsFile, bytes: ByteArray): WriteOutcome {
        if (sameFile || uri == source.uri) return WriteOutcome.SameFile
        if (!writes) return WriteOutcome.Failed
        written += uri to bytes
        return WriteOutcome.Written
    }
}

/** The film: where it is, and what was asked of it. */
internal class FakePlayback(var position: Long = 0, var loads: Boolean = true) : ToolsPlayback {
    val seeks = mutableListOf<Long>()
    val loaded = mutableListOf<SavedSubtitle>()

    override fun positionMs(): Long = position

    override fun seekTo(ms: Long) {
        seeks += ms
    }

    override suspend fun load(saved: SavedSubtitle): Boolean {
        loaded += saved
        return loads
    }
}

/** A controller that works at once (its coroutines run where they are launched), on [text] as [file]. */
internal fun toolsController(
    text: String = SDH_SRT,
    file: ToolsFile = SAMPLE_FILE,
    io: FakeToolsIo = FakeToolsIo(mutableMapOf(file.uri to text.toByteArray(Charsets.UTF_8))),
    playback: ToolsPlayback? = null,
    word: String = "duzenlendi",
): SubtitleToolsController {
    val controller = SubtitleToolsController(io, CoroutineScope(Dispatchers.Unconfined), word, cpu = Dispatchers.Unconfined, debounceMs = 0)
    controller.open(listOf(file), playback)
    return controller
}
