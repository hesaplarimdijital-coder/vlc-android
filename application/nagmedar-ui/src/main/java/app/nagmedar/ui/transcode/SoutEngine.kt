package app.nagmedar.ui.transcode

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IMedia
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/** S-1303: how far a stream output has come: 0..1 of the input (−1 while unknown, a live stream), its time and length. */
data class SoutProgress(val position: Float, val timeMs: Long, val lengthMs: Long)

/** S-1303: what the steering asks of a run at each step. */
enum class SoutSteer { Go, Pause, Stop }

/** S-1303: how a run of the stream output ended. */
sealed interface SoutOutcome {
    /** The input was read to its end. */
    data object Ended : SoutOutcome

    /** The steering stopped it. */
    data object Stopped : SoutOutcome

    /** libVLC could not do it: an input it cannot open, an encoder or a muxer this build lacks. */
    data class Failed(val reason: String) : SoutOutcome
}

/** S-1303: libVLC's stream output, run once: the tests' fake stands in for it. */
fun interface SoutEngine {
    /**
     * Plays [input] with [options] (SoutChain's) until it ends, fails or [steer] says stop;
     * [steer] is asked about twice a second. Blocking: a worker's thread.
     */
    fun run(input: Uri, options: List<String>, steer: (SoutProgress) -> SoutSteer): SoutOutcome
}

/**
 * S-1303: the stream output on a libVLC of its own (the playback's is not touched): the input
 * opened by its file descriptor when it is a document, by its address otherwise; libVLC's
 * events counted on the main thread, the run steered from the caller's.
 */
class LibVlcSoutEngine(private val context: Context) : SoutEngine {

    override fun run(input: Uri, options: List<String>, steer: (SoutProgress) -> SoutSteer): SoutOutcome {
        val descriptor = if (input.scheme == "content" || input.scheme == "file") {
            runCatching { context.contentResolver.openFileDescriptor(input, "r") }.getOrNull() ?: return SoutOutcome.Failed(OPEN_INPUT)
        } else {
            null
        }
        val libVlc = try {
            LibVLC(context.applicationContext, arrayListOf("--no-stats", "--no-sub-autodetect-file"))
        } catch (e: IllegalStateException) {
            descriptor?.close()
            return SoutOutcome.Failed(e.message ?: "libvlc")
        }
        val media = if (descriptor != null) Media(libVlc, descriptor.fileDescriptor) else Media(libVlc, input)
        options.forEach(media::addOption)
        val player = MediaPlayer(libVlc)
        val state = RunState()
        player.setEventListener { event ->
            when (event.type) {
                MediaPlayer.Event.PositionChanged -> state.position.set(java.lang.Float.floatToIntBits(event.positionChanged))
                MediaPlayer.Event.TimeChanged -> state.time.set(event.timeChanged)
                MediaPlayer.Event.LengthChanged -> state.length.set(event.lengthChanged)
                MediaPlayer.Event.Playing -> state.playing.countDown()
                MediaPlayer.Event.EndReached -> state.finish(ENDED)
                MediaPlayer.Event.EncounteredError -> state.finish(ERROR)
            }
        }
        player.media = media
        media.release()
        player.play()
        val outcome = try {
            steerUntilDone(player, state, steer)
        } finally {
            player.setEventListener(null)
            player.stop()
            player.release()
            libVlc.release()
            descriptor?.closeQuietly()
        }
        return outcome
    }

    private fun steerUntilDone(player: MediaPlayer, state: RunState, steer: (SoutProgress) -> SoutSteer): SoutOutcome {
        var paused = false
        var waited = 0L
        while (true) {
            if (state.done.await(STEP_MS, TimeUnit.MILLISECONDS)) {
                return when (state.how.get()) {
                    ENDED -> SoutOutcome.Ended
                    else -> SoutOutcome.Failed(if (state.playing.count > 0) OPEN_INPUT else ENCODE)
                }
            }
            waited += STEP_MS
            // never playing: an input libVLC cannot open, or a chain it cannot build
            if (state.playing.count > 0 && waited > OPEN_TIMEOUT_MS) return SoutOutcome.Failed(OPEN_INPUT)
            val position = java.lang.Float.intBitsToFloat(state.position.get())
            val progress = SoutProgress(if (state.length.get() > 0) position.coerceIn(0f, 1f) else -1f, state.time.get(), state.length.get())
            when (steer(progress)) {
                SoutSteer.Stop -> return SoutOutcome.Stopped
                SoutSteer.Pause -> if (!paused) {
                    player.pause()
                    paused = true
                }
                SoutSteer.Go -> if (paused) {
                    player.play()
                    paused = false
                }
            }
        }
    }

    private class RunState {
        val position = AtomicInteger(java.lang.Float.floatToIntBits(0f))
        val time = AtomicLong()
        val length = AtomicLong()
        val playing = CountDownLatch(1)
        val done = CountDownLatch(1)
        val how = AtomicInteger()

        fun finish(kind: Int) {
            how.compareAndSet(0, kind)
            done.countDown()
        }
    }

    private fun ParcelFileDescriptor.closeQuietly() {
        runCatching { close() }
    }

    companion object {
        /** Why a run failed, as a job keeps it after "engine:". */
        const val OPEN_INPUT = "open-input"
        const val ENCODE = "encode"

        private const val ENDED = 1
        private const val ERROR = 2
        private const val STEP_MS = 500L
        private const val OPEN_TIMEOUT_MS = 30_000L
    }
}
