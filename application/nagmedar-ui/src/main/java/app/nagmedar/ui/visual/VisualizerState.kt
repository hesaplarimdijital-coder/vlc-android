package app.nagmedar.ui.visual

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import app.nagmedar.audio.ScopeFrame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/**
 * S-611: what a visualiser shows: the meter's and the spectrum's ballistics and the last frame's
 * waveform, with why it does not draw now. [version] moves at each update: the drawing reads it,
 * so only the drawing runs again, not the composition.
 */
@Stable
class VisualizerState(bands: Int = BANDS) {
    val meter = LevelMeter()
    val spectrum = SpectrumBallistics(bands)

    /** The last frame read; null for silence (a flat line). */
    var frame: ScopeFrame? = null
        private set

    var version by mutableLongStateOf(0L)
        private set

    var problem by mutableStateOf<ScopeProblem?>(null)
    var pause by mutableStateOf<VisualizerPause?>(null)

    val atRest: Boolean get() = meter.atRest && spectrum.atRest

    /** A read ([frame] null: nothing new was heard), [seconds] after the one before. */
    fun update(frame: ScopeFrame?, seconds: Float) {
        meter.update(frame, seconds)
        spectrum.update(frame?.let { SpectrumBands.bands(it, spectrum.count) }, seconds)
        this.frame = frame
        version++
    }

    /** Straight to [frame], without ballistics: a still picture. */
    fun show(frame: ScopeFrame) {
        meter.set(frame)
        spectrum.set(SpectrumBands.bands(frame, spectrum.count))
        this.frame = frame
        version++
    }

    companion object {
        /** The spectrum's bands: a third of an octave each from 25 Hz to 20 kHz, near enough. */
        const val BANDS = 30
    }
}

/**
 * S-611: the device's side of the battery rules, as a pane sees it: whether it is in front (its
 * lifecycle started), the screen lit and Battery Saver on; [playing] is the playback's.
 */
@Composable
fun rememberVisualizerConditions(playing: Boolean): VisualizerConditions {
    val context = LocalContext.current
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val power = remember(context) { context.getSystemService<PowerManager>() }
    var screenOn by remember { mutableStateOf(power?.isInteractive != false) }
    var powerSave by remember { mutableStateOf(power?.isPowerSaveMode == true) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                screenOn = power?.isInteractive != false
                powerSave = power?.isPowerSaveMode == true
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
    return VisualizerConditions(lifecycle.isAtLeast(Lifecycle.State.STARTED), screenOn, powerSave, playing)
}

/**
 * S-611: the visualiser's loop while its pane is composed: the policy decides; running, the source
 * is opened, read at the frame rate on a worker thread and closed when it stops; when the playing
 * stops the bars fall to rest and the loop ends. [key] opens the source again (another track).
 */
@Composable
fun rememberVisualizer(source: ScopeSource?, settings: VisualizerSettings, conditions: VisualizerConditions, key: Any? = null): VisualizerState {
    val state = remember { VisualizerState() }
    val run = VisualizerPolicy.decide(settings, conditions)
    SideEffect { state.pause = (run as? VisualizerRun.Paused)?.reason }
    LaunchedEffect(source, run, key) {
        val periodMs = 1000L / settings.fps.fps
        when {
            source == null -> return@LaunchedEffect
            run is VisualizerRun.Paused -> {
                // nothing more is heard: what was drawn falls to rest, then nothing is drawn
                if (run.reason != VisualizerPause.NotPlaying) return@LaunchedEffect
                var last = System.nanoTime()
                while (isActive && !state.atRest) {
                    delay(periodMs)
                    val now = System.nanoTime()
                    state.update(null, (now - last) / 1e9f)
                    last = now
                }
            }
            run is VisualizerRun.Running -> {
                val problem = withContext(Dispatchers.Default) { source.open() }
                state.problem = problem
                if (problem != null) return@LaunchedEffect
                try {
                    var last = System.nanoTime()
                    var lastFrames = -1L
                    while (isActive) {
                        val frame = withContext(Dispatchers.Default) { source.read() }
                        val now = System.nanoTime()
                        // the engine's count stands still while it renders nothing: silence
                        val fresh = frame?.takeIf { it.frames != lastFrames }
                        lastFrames = frame?.frames ?: -1L
                        state.update(fresh, (now - last) / 1e9f)
                        last = now
                        val spent = (System.nanoTime() - now) / 1_000_000L
                        delay((1000L / run.fps - spent).coerceAtLeast(1L))
                    }
                } finally {
                    withContext(NonCancellable + Dispatchers.Default) { source.close() }
                }
            }
        }
    }
    return state
}
