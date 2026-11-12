package app.nagmedar.ui.shell

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent

/**
 * EXPERIMENT, never merged (the owner's video of 12 Nov: a podcast's page gone blank the moment its opening ends).
 * How a phone's depth is hosted and whether the chrome is glass, asked by the walk through the activity's extra
 * `nagmedar_exp` ("host=swap|stl|box|redraw|redraw-host|redraw-detail,glass=solid").
 */
object DepthExperiment {
    const val EXTRA = "nagmedar_exp"

    @Volatile
    var host: String = "swap"

    @Volatile
    var glassSolid: Boolean = false

    /** swipes=const: [app.nagmedar.ui.components.rememberBackSwipes] reads no window insets (as 1.0.3's handlers read none). */
    @Volatile
    var swipesConst: Boolean = false

    /** inner=plain: a depth without shared elements is hosted in a plain box, with no movable content. */
    @Volatile
    var plainUnshared: Boolean = false

    /** Bumped two frames after a depth's host changed, in the redraw modes: what reads it while drawing draws again. */
    val tick = mutableIntStateOf(0)

    fun follow(intent: Intent?) {
        val asked = intent?.getStringExtra(EXTRA) ?: return
        var newHost = "swap"
        var solid = false
        var const = false
        var plain = false
        for (part in asked.split(',')) {
            val key = part.substringBefore('=').trim()
            val value = part.substringAfter('=', "").trim()
            when (key) {
                "host" -> newHost = value
                "glass" -> solid = value == "solid"
                "swipes" -> const = value == "const"
                "inner" -> plain = value == "plain"
            }
        }
        host = newHost
        glassSolid = solid
        swipesConst = const
        plainUnshared = plain
        android.util.Log.w(TAG, "asked: " + asked)
    }

    const val TAG = "NagExp"

    val redrawsHost: Boolean get() = host == "redraw" || host == "redraw-host"
    val redrawsDetail: Boolean get() = host == "redraw" || host == "redraw-detail"
}

/** In the redraw modes for the host: drawn again when [DepthExperiment.tick] changes (the layer above this). */
internal fun Modifier.expRedrawHost(): Modifier =
    if (DepthExperiment.redrawsHost) drawWithContent { if (DepthExperiment.tick.intValue >= 0) drawContent() } else this

/** In the redraw modes for the sides: drawn again when [DepthExperiment.tick] changes (the side's own layer). */
internal fun Modifier.expRedrawSide(): Modifier =
    if (DepthExperiment.redrawsDetail) drawWithContent { if (DepthExperiment.tick.intValue >= 0) drawContent() } else this

/** A trace of [what] in the log: when it enters and leaves the composition, and each time it is composed. */
@Composable
internal fun ExpTrace(what: String) {
    DisposableEffect(Unit) {
        android.util.Log.w(DepthExperiment.TAG, what + ": enters")
        onDispose { android.util.Log.w(DepthExperiment.TAG, what + ": leaves") }
    }
    SideEffect { android.util.Log.w(DepthExperiment.TAG, what + ": composed") }
}

internal fun expLog(what: String) {
    android.util.Log.w(DepthExperiment.TAG, what)
}
