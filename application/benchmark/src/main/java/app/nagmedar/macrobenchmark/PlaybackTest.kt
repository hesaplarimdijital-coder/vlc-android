package app.nagmedar.macrobenchmark

import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Locale

/**
 * S-1404: the films given (-e nagmedar.videos; run.sh passes the test set's 4K HDR10 samples)
 * played as another app's "Open with" plays them, to their end or for nagmedar.seconds; each
 * playback's NagmedarPerf line, which the player writes as it closes, then says the frames it
 * showed and dropped. At most one frame in a thousand may drop.
 */
@RunWith(AndroidJUnit4::class)
class PlaybackTest {

    private val device = uiDevice()

    @Before
    fun firstRun() = device.pastFirstRun()

    @Test
    fun filmsDropNoFrames() {
        val videos = Arguments.videos
        assumeTrue("no films to play: buildsystem/benchmark/run.sh --video PATH", videos.isNotEmpty())
        val wrong = mutableListOf<String>()
        for (video in videos) {
            val name = video.substringAfterLast('/')
            val before = records(name).size
            val uri = Uri.fromFile(File(video))
            device.executeShellCommand("am start -W -a android.intent.action.VIEW -d $uri -t ${mimeType(name)} -n $TARGET/$START_ACTIVITY")
            val record = waitForRecord(name, before)
            if (record == null) {
                wrong += "$name: no record (did it play?)"
                continue
            }
            val fields = fields(record)
            val shown = fields["shown"]?.toLongOrNull() ?: 0
            val dropped = fields["dropped_pct"]?.toDoubleOrNull() ?: 100.0
            Log.i(TAG, "$name: ${fields["shown"]} shown, ${fields["dropped"]} dropped (${fields["dropped_pct"]} %) in ${fields["seconds"]} s")
            if (shown == 0L) wrong += "$name: no frame shown"
            if (dropped > MAX_DROPPED_PERCENT) wrong += "$name: ${fields["dropped_pct"]} % of the frames dropped, over $MAX_DROPPED_PERCENT %"
            device.pressHome()
        }
        assertTrue(wrong.joinToString("\n"), wrong.isEmpty())
    }

    /** The film's lines in the log so far. */
    private fun records(name: String): List<String> =
        device.executeShellCommand("logcat -d -s $PERF_TAG:I").lineSequence().filter { "playback file=\"$name\"" in it }.toList()

    /**
     * The film's new line, once the player closed: by itself at the film's end, or by Back after
     * nagmedar.seconds; null if none came.
     */
    private fun waitForRecord(name: String, before: Int): String? {
        val playUntil = SystemClock.uptimeMillis() + Arguments.playSeconds * 1_000L
        var closedAt = 0L
        while (true) {
            SystemClock.sleep(1_000)
            records(name).takeIf { it.size > before }?.let { return it.last() }
            val now = SystemClock.uptimeMillis()
            if (closedAt == 0L && now > playUntil) {
                device.pressBack()
                closedAt = now
            }
            if (closedAt != 0L && now > closedAt + CLOSE_MS) return null
        }
    }

    private fun fields(line: String): Map<String, String> =
        Regex("""(\w+)=("[^"]*"|\S+)""").findAll(line.substringAfter("playback ")).associate { it.groupValues[1] to it.groupValues[2] }

    private fun mimeType(name: String) = when (name.substringAfterLast('.').lowercase(Locale.ROOT)) {
        "mkv" -> "video/x-matroska"
        "mp4", "m4v" -> "video/mp4"
        "ts", "m2ts" -> "video/mp2t"
        "webm" -> "video/webm"
        else -> "video/*"
    }

    private companion object {
        const val TAG = "NagmedarBench"

        /** The player closed, its record comes within this. */
        const val CLOSE_MS = 15_000L
    }
}
