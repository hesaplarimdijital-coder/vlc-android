package app.nagmedar.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioMixerAttributes
import android.media.AudioTrack
import android.os.Build
import android.os.Process
import android.util.Log
import androidx.annotation.RequiresApi
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * S-902: the bit-perfect output of Android 14: an AudioTrack whose format matches a mixer
 * attribute of the device's with the bit-perfect behaviour, set as the preferred one for the
 * engine's audio attributes, so the framework neither mixes nor resamples nor touches the
 * samples. The engine renders into it through the pull sink from a thread of this class's;
 * the track follows the engine's output format (rate, depth) and reopens when it changes. A
 * format the device does not take bit-perfectly plays through an ordinary track, and the
 * engine is told so (the signal path then says "output").
 */
class BitPerfectOutput(context: Context, private val engine: MusicEngine) : Closeable {

    /** A device and the bit-perfect formats it takes. */
    data class Candidate(val device: AudioDeviceInfo, val formats: List<AudioFormat>) {
        val name: String get() = device.productName.toString()
        val rates: Set<Int> get() = formats.map { it.sampleRate }.toSet()
        val encodings: Set<Int> get() = formats.map { it.encoding }.toSet()
    }

    private val context = context.applicationContext
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()

    @Volatile private var candidate: Candidate? = null
    @Volatile private var running = false
    private var thread: Thread? = null
    private val lock = Object()

    /** Whether the track open now is bit-perfect for the engine's format. */
    @Volatile var bitPerfect: Boolean = false
        private set

    /** The track's format now, for the signal path and the badge. */
    @Volatile var trackFormat: OutputFormat? = null
        private set

    val supported: Boolean get() = Build.VERSION.SDK_INT >= 34

    /** Whether the feeder runs. */
    val active: Boolean get() = running

    /** The device the track goes to ("Topping D90", "HDMI"); null before [start]. */
    val deviceName: String? get() = candidate?.name

    /** Whether the device in use is still there, so a restart would change nothing. */
    fun stillValid(): Boolean = running && candidate?.let { c -> candidates().any { it.device.id == c.device.id } } == true

    /** The devices that take a bit-perfect stream: USB first, as the DAC usually is one. */
    fun candidates(): List<Candidate> {
        if (Build.VERSION.SDK_INT < 34) return emptyList()
        return audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .filter { it.type == AudioDeviceInfo.TYPE_USB_DEVICE || it.type == AudioDeviceInfo.TYPE_USB_HEADSET || it.type == AudioDeviceInfo.TYPE_USB_ACCESSORY || it.type == AudioDeviceInfo.TYPE_HDMI }
            .sortedBy { if (it.type == AudioDeviceInfo.TYPE_HDMI) 1 else 0 }
            .mapNotNull { device -> bitPerfectFormats(device).takeIf { it.isNotEmpty() }?.let { Candidate(device, it) } }
    }

    @RequiresApi(34)
    private fun bitPerfectFormats(device: AudioDeviceInfo): List<AudioFormat> = runCatching {
        audioManager.getSupportedMixerAttributes(device)
            .filter { it.mixerBehavior == AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT }
            .map { it.format }
            .filter { it.channelMask == AudioFormat.CHANNEL_OUT_STEREO && it.encoding in takenEncodings }
    }.getOrDefault(emptyList())

    /**
     * Starts rendering to [candidate] (the first found when null). The engine's depths are set
     * to what the device takes: 32-bit words when it takes them, else 24-bit packed, else 16.
     */
    fun start(candidate: Candidate? = null): Boolean {
        if (Build.VERSION.SDK_INT < 34) return false
        val chosen = candidate ?: candidates().firstOrNull() ?: return false
        stop()
        this.candidate = chosen
        val encodings = chosen.encodings
        val max = when {
            AudioFormat.ENCODING_PCM_32BIT in encodings -> NativeAudio.FORMAT_S32
            AudioFormat.ENCODING_PCM_24BIT_PACKED in encodings -> NativeAudio.FORMAT_S24_3
            else -> NativeAudio.FORMAT_S16
        }
        val min = if (AudioFormat.ENCODING_PCM_16BIT in encodings) NativeAudio.FORMAT_NONE else max
        engine.setFormatRange(min, max)
        if (!engine.usePullSink()) return false
        running = true
        thread = Thread({ feed() }, "nagmedar-bitperfect").also { it.start() }
        return true
    }

    fun stop() {
        running = false
        synchronized(lock) { lock.notifyAll() }
        thread?.join(2000)
        thread = null
        engine.setPullBitPerfect(false, 0)
        bitPerfect = false
        trackFormat = null
    }

    override fun close() {
        stop()
        if (Build.VERSION.SDK_INT >= 34) candidate?.let { runCatching { audioManager.clearPreferredMixerAttributes(attributes, it.device) } }
        candidate = null
    }

    /** The feeder: the engine's frames into the track, the track following the engine's format. */
    private fun feed() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        var track: AudioTrack? = null
        var format: OutputFormat? = null
        var buffer: ByteBuffer? = null
        var packed: ByteBuffer? = null
        var frames = 0
        try {
            while (running) {
                val now = engine.outputFormat
                if (now == null) {
                    pause(track)
                    waitAWhile()
                    continue
                }
                if (now != format || track == null) {
                    track?.release()
                    track = open(now)
                    format = now
                    trackFormat = now
                    if (track == null) {
                        waitAWhile(200)
                        continue
                    }
                    frames = now.rate / 50 /* 20 ms a write */
                    buffer = ByteBuffer.allocateDirect(frames * now.bytesPerFrame).order(ByteOrder.nativeOrder())
                    packed = if (now.sampleFormat == NativeAudio.FORMAT_S32 && track.format.encoding == AudioFormat.ENCODING_PCM_24BIT_PACKED) ByteBuffer.allocateDirect(frames * 6).order(ByteOrder.nativeOrder()) else null
                }
                if (engine.state != EngineState.Playing) {
                    pause(track)
                    waitAWhile()
                    continue
                }
                if (track.playState != AudioTrack.PLAYSTATE_PLAYING) track.play()
                val buf = buffer!!
                buf.clear()
                engine.render(buf, frames) /* silence where no voice fills */
                buf.limit(frames * now.bytesPerFrame)
                val out = packed?.let { p ->
                    /* S32 words into 24-bit packed: the low byte goes */
                    p.clear()
                    for (i in 0 until frames * now.channels) {
                        val v = buf.getInt(i * 4)
                        p.put((v ushr 8).toByte()).put((v ushr 16).toByte()).put((v ushr 24).toByte())
                    }
                    p.flip()
                    p
                } ?: buf
                val written = track.write(out, out.remaining(), AudioTrack.WRITE_BLOCKING)
                if (written < 0) {
                    Log.w(TAG, "write failed: $written")
                    track.release()
                    track = null
                    waitAWhile(200)
                }
            }
        } finally {
            track?.let { runCatching { it.pause(); it.flush(); it.release() } }
        }
    }

    private fun pause(track: AudioTrack?) {
        if (track != null && track.playState == AudioTrack.PLAYSTATE_PLAYING) track.pause()
    }

    private fun waitAWhile(ms: Long = 20) {
        synchronized(lock) { if (running) lock.wait(ms) }
    }

    /** A track for [format]: bit-perfect through the preferred mixer attributes when the device takes it, ordinary otherwise. */
    private fun open(format: OutputFormat): AudioTrack? {
        val candidate = candidate ?: return null
        val encoding = when (format.sampleFormat) {
            NativeAudio.FORMAT_S16 -> AudioFormat.ENCODING_PCM_16BIT
            NativeAudio.FORMAT_S24_3 -> AudioFormat.ENCODING_PCM_24BIT_PACKED
            NativeAudio.FORMAT_S32 -> if (AudioFormat.ENCODING_PCM_32BIT in candidate.encodings) AudioFormat.ENCODING_PCM_32BIT else AudioFormat.ENCODING_PCM_24BIT_PACKED
            else -> AudioFormat.ENCODING_PCM_FLOAT
        }
        if (format.channels != 2) return null
        val audioFormat = AudioFormat.Builder().setEncoding(encoding).setSampleRate(format.rate).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build()
        val exact = candidate.formats.any { it.encoding == encoding && it.sampleRate == format.rate }
        var perfect = false
        if (exact && Build.VERSION.SDK_INT >= 34) {
            val mixer = AudioMixerAttributes.Builder(audioFormat).setMixerBehavior(AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT).build()
            perfect = runCatching { audioManager.setPreferredMixerAttributes(attributes, candidate.device, mixer) }.getOrDefault(false)
            if (!perfect) Log.w(TAG, "the device did not take the bit-perfect mixer attributes for ${format.rate} Hz")
        }
        val minimum = AudioTrack.getMinBufferSize(format.rate, AudioFormat.CHANNEL_OUT_STEREO, encoding)
        if (minimum <= 0) return null
        val track = runCatching {
            AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(audioFormat)
                .setBufferSizeInBytes(maxOf(minimum * 2, format.rate / 5 * audioFormat.frameSizeInBytes()))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_NONE)
                .build()
        }.onFailure { Log.w(TAG, "cannot open the track: $it") }.getOrNull() ?: return null
        if (perfect && Build.VERSION.SDK_INT >= 34) {
            track.preferredDevice = candidate.device
        }
        bitPerfect = perfect
        engine.setPullBitPerfect(perfect, (track.bufferSizeInFrames).toLong())
        Log.i(TAG, "track ${format.rate} Hz enc $encoding ${if (perfect) "bit-perfect" else "ordinary"} on ${candidate.name}")
        return track
    }

    private fun AudioFormat.frameSizeInBytes(): Int = when (encoding) {
        AudioFormat.ENCODING_PCM_16BIT -> 4
        AudioFormat.ENCODING_PCM_24BIT_PACKED -> 6
        else -> 8
    }

    companion object {
        private const val TAG = "NagmedarAudio"
        private val takenEncodings = setOf(AudioFormat.ENCODING_PCM_16BIT, AudioFormat.ENCODING_PCM_24BIT_PACKED, AudioFormat.ENCODING_PCM_32BIT)
    }
}
