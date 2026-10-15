package app.nagmedar.ui.audio.latency

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTimestamp
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.nagmedar.audio.latency.LatencyDsp
import app.nagmedar.audio.latency.LatencyProfiles
import app.nagmedar.audio.latency.Measurement
import app.nagmedar.audio.latency.TestSignal
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * S-613: what the microphone heard of the test signal: [samples] mono at [rate], the signal's
 * frame 0 presented at [origin] (a sample of the recording, fractional) by the streams'
 * timestamps when the device gives them ([exact]), by the moment play started otherwise.
 */
class Recording(val samples: FloatArray, val rate: Int, val origin: Double, val exact: Boolean)

/** S-613: the sound in and out of the device during a measurement; the real streams in the app, a room of the tests' making in them. */
interface MeasureIo {
    /** Plays [signal] through the output in use while recording for its length and [tailMs] more; [onProgress] 0..1 as it goes. */
    suspend fun playAndRecord(signal: TestSignal, tailMs: Int, onProgress: (Float) -> Unit): Recording
}

/** S-613: why a measurement gave nothing. */
enum class MeasureFailure {
    /** The microphone heard none of the chirps: the volume down, the microphone covered, too far. */
    NotHeard,
    /** The chirps were heard but did not agree, or too few did. */
    Inconsistent,
    /** The microphone or the output could not be opened. */
    Device,
}

/**
 * S-613: what a measurement found: the round trip's median [rawMs] over [valid] of [total]
 * chirps and their [spreadMs]; [exact] when the streams' timestamps gave the clocks. Without
 * them the figure holds the output's own buffer, which libVLC and the engine already take off,
 * and the recorder's start-up, which wanders: such a result is shown but not [saveable].
 * [referenceMs] is the built-in speaker's own (exact) round trip where there is one, taken off
 * for another device: [delayMs] is what a profile would keep.
 */
@Immutable
data class LatencyResult(val rawMs: Int, val spreadMs: Int, val valid: Int, val total: Int, val exact: Boolean, val referenceMs: Int?) {
    val delayMs: Int get() = LatencyProfiles.snap(rawMs - (referenceMs ?: 0))

    /** Only a result on the streams' clocks is worth a profile. */
    val saveable: Boolean get() = exact

    /** The chirps did not land within the story's tolerance of each other: a noisy room, or a wandering codec. */
    val uncertain: Boolean get() = spreadMs > TOLERANCE_MS

    companion object {
        /** The acceptance's tolerance: a result within it is a measurement. */
        const val TOLERANCE_MS = 20
    }
}

/**
 * S-613: one measurement of the output's latency, step by step: the explanation, the recording
 * with its progress, then the result or why there is none. [scope] is the screen's; the
 * states are Compose state for it.
 */
@Stable
class LatencyMeasureSession(
    private val io: MeasureIo,
    private val scope: CoroutineScope,
    /** The built-in speaker's own measurement, when measuring another device; taken off the result. */
    private val referenceMs: Int? = null,
    private val signal: TestSignal = LatencyDsp.testSignal(),
    /** Where the correlation runs; the tests keep it on their thread. */
    private val cpu: CoroutineDispatcher = Dispatchers.Default,
) {
    sealed interface State {
        /** The explanation, before anything sounds. */
        data object Intro : State

        /** The chirps play: [progress] 0..1. */
        data class Measuring(val progress: Float) : State

        data class Done(val result: LatencyResult) : State

        data class Failed(val reason: MeasureFailure) : State
    }

    var state by mutableStateOf<State>(State.Intro)
        private set

    private var job: Job? = null

    /** Plays and listens; the state follows. */
    fun start() {
        if (job?.isActive == true) return
        state = State.Measuring(0f)
        job = scope.launch {
            val recording = try {
                io.playAndRecord(signal, TAIL_MS) { progress -> state = State.Measuring(progress.coerceIn(0f, 1f)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                state = State.Failed(MeasureFailure.Device)
                return@launch
            }
            state = withContext(cpu) { evaluate(recording) }
        }
    }

    /** Back to the explanation; a measurement under way is stopped. */
    fun reset() {
        job?.cancel()
        job = null
        state = State.Intro
    }

    private fun evaluate(recording: Recording): State {
        val hits = LatencyDsp.findBursts(recording.samples, signal, recording.origin)
        val delays = LatencyDsp.delaysMs(hits, signal, recording.origin)
        if (delays.isEmpty()) return State.Failed(MeasureFailure.NotHeard)
        val measurement: Measurement = LatencyDsp.combine(delays, signal.bursts) ?: return State.Failed(MeasureFailure.Inconsistent)
        return State.Done(
            LatencyResult(
                rawMs = measurement.medianMs.roundToInt(),
                spreadMs = ceil(measurement.spreadMs).toInt(),
                valid = measurement.keptMs.size,
                total = signal.bursts,
                exact = recording.exact,
                referenceMs = referenceMs,
            ),
        )
    }

    companion object {
        /** Recorded after the signal ends, so the last chirp is heard however late. */
        const val TAIL_MS = 1_500
    }
}

/**
 * S-613: the measurement on the device: the chirps out of an AudioTrack as media, so they take
 * the route music takes, and the microphone in through an AudioRecord without processing where
 * the device has such a source. The two streams' timestamps say when the signal's first frame
 * left and when each recorded frame came in; a device without them is measured from the moment
 * play started, which the result says is not exact. Nothing recorded is kept.
 */
class AndroidMeasureIo(context: Context) : MeasureIo {
    private val context = context.applicationContext

    /** The screen asks for the microphone before this is called (the system's dialog, with the reason). */
    @SuppressLint("MissingPermission")
    override suspend fun playAndRecord(signal: TestSignal, tailMs: Int, onProgress: (Float) -> Unit): Recording = withContext(Dispatchers.IO) {
        val rate = signal.rate
        val totalFrames = signal.samples.size + rate * tailMs / 1000
        var record: AudioRecord? = null
        var track: AudioTrack? = null
        var reader: Thread? = null
        try {
            val recordBuffer = maxOf(AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT), rate / 2) * 2
            val trackBuffer = maxOf(AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT), rate / 4) * 2
            val input = AudioRecord(recordSource(), rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, recordBuffer).also { record = it }
            val output = AudioTrack(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build(),
                AudioFormat.Builder().setSampleRate(rate).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build(),
                trackBuffer,
                AudioTrack.MODE_STREAM,
                AudioManager.AUDIO_SESSION_ID_GENERATE,
            ).also { track = it }
            check(input.state == AudioRecord.STATE_INITIALIZED && output.state == AudioTrack.STATE_INITIALIZED) { "the streams did not open" }
            val recorded = ShortArray(totalFrames)
            val recordedFrames = AtomicInteger(0)
            input.startRecording()
            val recordStartNs = System.nanoTime()
            check(input.recordingState == AudioRecord.RECORDSTATE_RECORDING) { "the microphone did not start" }
            reader = Thread({
                val chunk = ShortArray(rate / 10)
                while (recordedFrames.get() < totalFrames && !Thread.currentThread().isInterrupted) {
                    val read = input.read(chunk, 0, minOf(chunk.size, totalFrames - recordedFrames.get()))
                    if (read <= 0) break
                    System.arraycopy(chunk, 0, recorded, recordedFrames.get(), read)
                    recordedFrames.addAndGet(read)
                }
            }, "NagmedarLatency").also { it.start() }
            // the microphone first, so that the lead of the signal is heard too
            delay(RECORD_LEAD_MS.toLong())
            val pcm = ShortArray(signal.samples.size) { (signal.samples[it] * Short.MAX_VALUE).toInt().toShort() }
            output.play()
            val playStartNs = System.nanoTime()
            var written = 0
            while (written < pcm.size && isActive) {
                val n = output.write(pcm, written, minOf(rate / 10, pcm.size - written))
                if (n < 0) break
                written += n
                onProgress(written.toFloat() / totalFrames)
            }
            // the output's timestamp once everything is written and the tail drains: settled, and nothing left to underrun
            val trackStamp = timestamp(output)
            // the tail: the last chirp comes in however late the output is
            while (recordedFrames.get() < totalFrames && reader.isAlive && isActive) {
                onProgress(recordedFrames.get().toFloat() / totalFrames)
                delay(100)
            }
            val recordStamp = if (Build.VERSION.SDK_INT >= 24) AudioTimestamp().takeIf { input.getTimestamp(it, AudioTimestamp.TIMEBASE_MONOTONIC) == AudioRecord.SUCCESS } else null
            val frames = recordedFrames.get()
            val samples = FloatArray(frames) { recorded[it] / Short.MAX_VALUE.toFloat() }
            val exact = trackStamp != null && recordStamp != null
            // when the track's frame 0 left, and when the recording's frame 0 came in, on the same clock
            val trackZeroNs = trackStamp?.let { it.nanoTime - it.framePosition * 1_000_000_000L / rate } ?: playStartNs
            val recordZeroNs = recordStamp?.let { it.nanoTime - it.framePosition * 1_000_000_000L / rate } ?: recordStartNs
            Recording(samples, rate, (trackZeroNs - recordZeroNs) * rate / 1e9, exact)
        } finally {
            reader?.interrupt()
            runCatching { track?.pause(); track?.flush(); track?.release() }
            runCatching { record?.stop(); record?.release() }
        }
    }

    /** The output's timestamp while its tail plays; null where the device gives none. */
    private fun timestamp(track: AudioTrack): AudioTimestamp? {
        val stamp = AudioTimestamp()
        repeat(10) {
            if (track.getTimestamp(stamp) && stamp.framePosition > 0) return stamp
            SystemClock.sleep(20)
        }
        return null
    }

    /** The microphone without the device's processing where it has such a source; the recognition source otherwise, which leaves the level alone. */
    private fun recordSource(): Int {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val unprocessed = Build.VERSION.SDK_INT >= 24 && audioManager?.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"
        return if (unprocessed) MediaRecorder.AudioSource.UNPROCESSED else MediaRecorder.AudioSource.VOICE_RECOGNITION
    }

    private companion object {
        /** The microphone runs this long before the first frame plays. */
        const val RECORD_LEAD_MS = 300
    }
}
