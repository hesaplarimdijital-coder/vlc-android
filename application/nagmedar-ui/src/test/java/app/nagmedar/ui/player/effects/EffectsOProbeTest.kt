package app.nagmedar.ui.player.effects

import android.os.Looper
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.ExperimentalComposeApi
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

object ProbeInfo { @Volatile var text: String = "not collected" }

/** Temporary: what is pending when a legacy-graphics compose rule has idled once (CI diagnosis only). */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "tr-w1280dp-h800dp-mdpi")
class EffectsOProbeTest {
    @get:Rule
    val compose = createComposeRule()

    private fun queue(): String {
        val sb = StringBuilder()
        runCatching {
            val q = Looper.getMainLooper().queue
            val field = android.os.MessageQueue::class.java.getDeclaredField("mMessages").apply { isAccessible = true }
            val next = android.os.Message::class.java.getDeclaredField("next").apply { isAccessible = true }
            var m = field.get(q) as android.os.Message?
            var n = 0
            while (m != null && n++ < 10) {
                sb.append("[when=${m.`when`} target=${m.target} cb=${m.callback} what=${m.what}] ")
                m = next.get(m) as android.os.Message?
            }
        }.onFailure { sb.append("unreadable: $it") }
        return sb.toString()
    }

    @OptIn(ExperimentalComposeApi::class)
    @Test
    fun collect() {
        compose.setContent { Box {} }
        compose.waitForIdle()
        val before = queue()
        val looper = shadowOf(Looper.getMainLooper())
        looper.idle()
        val after = queue()
        val alive = Thread.getAllStackTraces().keys.filter { it.isAlive }.map { it.name + ":" + it.state }
        val busy = Thread.getAllStackTraces().filter { (t, _) -> t.isAlive && t.state == Thread.State.RUNNABLE }
            .map { (t, st) -> t.name + " @ " + st.take(6).joinToString(" < ") { "${it.className.substringAfterLast('.')}.${it.methodName}" } }
        ProbeInfo.text = "recomposers=${Recomposer.runningRecomposers.value.map { it.hasPendingWork }} snapshotPending=${Snapshot.current.hasPendingChanges()} " +
            "mainIdle=${looper.isIdle} queueBefore=$before queueAfter=$after busy=$busy alive=$alive"
    }
}
