package app.nagmedar.ui.shell

import android.content.Intent
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

    /** Bumped two frames after a depth's host changed, in the redraw modes: what reads it while drawing draws again. */
    val tick = mutableIntStateOf(0)

    fun follow(intent: Intent?) {
        val asked = intent?.getStringExtra(EXTRA) ?: return
        var newHost = "swap"
        var solid = false
        for (part in asked.split(',')) {
            val key = part.substringBefore('=').trim()
            val value = part.substringAfter('=', "").trim()
            when (key) {
                "host" -> newHost = value
                "glass" -> solid = value == "solid"
            }
        }
        host = newHost
        glassSolid = solid
    }

    val redrawsHost: Boolean get() = host == "redraw" || host == "redraw-host"
    val redrawsDetail: Boolean get() = host == "redraw" || host == "redraw-detail"
}

/** In the redraw modes for the host: drawn again when [DepthExperiment.tick] changes (the layer above this). */
internal fun Modifier.expRedrawHost(): Modifier =
    if (DepthExperiment.redrawsHost) drawWithContent { if (DepthExperiment.tick.intValue >= 0) drawContent() } else this

/** In the redraw modes for the sides: drawn again when [DepthExperiment.tick] changes (the side's own layer). */
internal fun Modifier.expRedrawSide(): Modifier =
    if (DepthExperiment.redrawsDetail) drawWithContent { if (DepthExperiment.tick.intValue >= 0) drawContent() } else this
