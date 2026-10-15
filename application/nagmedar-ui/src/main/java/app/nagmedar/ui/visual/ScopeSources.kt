package app.nagmedar.ui.visual

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.audiofx.Visualizer
import androidx.core.content.ContextCompat
import app.nagmedar.audio.ScopeAnalysis
import app.nagmedar.audio.ScopeFrame
import app.nagmedar.ui.audio.MusicEngineAccess
import org.videolan.resources.VLCOptions
import org.videolan.vlc.media.MusicEngineController

/** S-611: what keeps a visualiser's source from giving frames. */
enum class ScopeProblem {
    /** The system's visualiser reads the output only with the microphone permission. */
    NeedsPermission,

    /** Nothing to read: no output, or the device refuses its visualiser. */
    Unavailable,
}

/**
 * S-611: where a visualiser's frames come from. Opened while it draws, read at its frame rate on a
 * worker thread, closed when it stops.
 */
interface ScopeSource {
    /** Null when frames can be read. */
    fun open(): ScopeProblem?

    /** What is heard now; null when nothing is. */
    fun read(): ScopeFrame?

    fun close()
}

/** The points of the oscilloscope's line. */
internal const val WAVE_POINTS = 256

/**
 * S-611: the music engine's tap: what the engine renders, after its DSP chain, analysed natively.
 * The tap stays on while any visualiser reads it and costs nothing once the last one closes.
 */
class EngineScopeSource(private val controller: () -> MusicEngineController? = { MusicEngineAccess.controller }) : ScopeSource {
    private val buffer = ScopeFrame.buffer()

    override fun open(): ScopeProblem? {
        EngineTap.acquire(controller())
        return null
    }

    override fun read(): ScopeFrame? {
        val engine = controller() ?: return null
        // the engine came after the visualiser opened (a new service): the tap goes on for it too
        if (!engine.scopeEnabled) engine.scopeEnabled = true
        return engine.scope(buffer, WAVE_POINTS)
    }

    override fun close() = EngineTap.release(controller())
}

/** The visualisers reading the engine's tap: on with the first, off with the last. */
internal object EngineTap {
    private var users = 0

    @Synchronized
    fun acquire(engine: MusicEngineController?) {
        users++
        engine?.scopeEnabled = true
    }

    @Synchronized
    fun release(engine: MusicEngineController?) {
        users = (users - 1).coerceAtLeast(0)
        if (users == 0) engine?.scopeEnabled = false
    }
}

/**
 * S-611: libVLC's output (video, radio, podcasts, music the engine does not play), through the
 * system's visualiser on libVLC's audio session: 8-bit mono samples, so the spectrum's floor is
 * about −48 dB and both channels of the meter show the mix, the levels as the system measures
 * them. It needs the microphone permission, though it reads only this app's output.
 */
class AudioFxScopeSource(private val context: Context, private val sessionId: () -> Int = { VLCOptions.audiotrackSessionId }) : ScopeSource {
    private var visualizer: Visualizer? = null
    private var bytes = ByteArray(0)
    private var reads = 0L

    override fun open(): ScopeProblem? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return ScopeProblem.NeedsPermission
        }
        val session = sessionId()
        if (session == 0) return ScopeProblem.Unavailable
        return try {
            val created = Visualizer(session)
            created.enabled = false
            val range = Visualizer.getCaptureSizeRange()
            created.captureSize = CAPTURE.coerceIn(range[0], range[1])
            created.scalingMode = Visualizer.SCALING_MODE_AS_PLAYED
            created.measurementMode = Visualizer.MEASUREMENT_MODE_PEAK_RMS
            created.enabled = true
            bytes = ByteArray(created.captureSize)
            visualizer = created
            null
        } catch (e: RuntimeException) {
            // the device's audio effects refuse it (UnsupportedOperationException, IllegalStateException)
            ScopeProblem.Unavailable
        }
    }

    override fun read(): ScopeFrame? {
        val source = visualizer ?: return null
        if (runCatching { source.getWaveForm(bytes) }.getOrDefault(Visualizer.ERROR) != Visualizer.SUCCESS) return null
        val samples = FloatArray(bytes.size) { ((bytes[it].toInt() and 0xFF) - 128) / 128f }
        val rate = (source.samplingRate / 1000).takeIf { it > 0 } ?: DEFAULT_RATE
        val frame = ScopeAnalysis.analyse(samples, samples, rate, Integer.highestOneBit(samples.size), WAVE_POINTS, frames = ++reads)
        val measured = Visualizer.MeasurementPeakRms()
        if (runCatching { source.getMeasurementPeakRms(measured) }.getOrDefault(Visualizer.ERROR) != Visualizer.SUCCESS) return frame
        // millibels; -9600 for silence
        val peak = (measured.mPeak / 100f).coerceAtLeast(ScopeFrame.FLOOR_DB)
        val rms = (measured.mRms / 100f).coerceAtLeast(ScopeFrame.FLOOR_DB)
        return ScopeFrame(frame.rate, frame.fftSize, frame.spectrumDb, floatArrayOf(peak, peak), floatArrayOf(rms, rms), frame.waveLeft, frame.waveRight, frame.frames)
    }

    override fun close() {
        visualizer?.let { runCatching { it.enabled = false; it.release() } }
        visualizer = null
    }

    private companion object {
        /** Samples read at a time: the system's largest capture on most devices. */
        const val CAPTURE = 1024
        const val DEFAULT_RATE = 48_000
    }
}

/**
 * S-611: what plays now: the engine's tap while the music engine plays, libVLC's output through
 * the system's visualiser otherwise. Chosen when it opens; a pane opens it again for another track.
 */
class PlayingScopeSource(private val context: Context) : ScopeSource {
    private var source: ScopeSource? = null

    override fun open(): ScopeProblem? {
        val chosen = if (MusicEngineAccess.active) EngineScopeSource() else AudioFxScopeSource(context.applicationContext)
        val problem = chosen.open()
        source = chosen.takeIf { problem == null }
        return problem
    }

    override fun read(): ScopeFrame? = source?.read()

    override fun close() {
        source?.close()
        source = null
    }
}
