package app.nagmedar.ui.onboarding

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.compose.runtime.Immutable
import app.nagmedar.ui.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/** Where the test sound of the first-run flow went: a readable output name and the stream's rate. */
@Immutable
data class AudioRoute(val name: String, val sampleRate: Int)

/**
 * S-309's audio output test: a soft two-note chime (880 Hz, then 1 318 Hz, 0.5 s each, faded in
 * and out) through the media stream, so it follows the output the player would use.
 */
object AudioCheck {

    private const val RATE = 48_000
    private const val NOTE_SECONDS = 0.5

    /** Plays the chime and reports the device the system routed it to. */
    suspend fun play(context: Context): AudioRoute = withContext(Dispatchers.Default) {
        val samples = chime()
        val track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(samples.size * 2)
            .build()
        try {
            track.write(samples, 0, samples.size)
            track.play()
            // the route is known once the track runs
            delay(150)
            val route = AudioRoute(deviceName(context, track.routedDevice), track.sampleRate)
            delay((NOTE_SECONDS * 2 * 1000).toLong())
            route
        } finally {
            track.release()
        }
    }

    /** 16-bit mono samples of the chime, -12 dBFS, with 20 ms fades so nothing clicks. */
    fun chime(): ShortArray {
        val perNote = (RATE * NOTE_SECONDS).toInt()
        val fade = RATE / 50
        val out = ShortArray(perNote * 2)
        for ((index, frequency) in listOf(880.0, 1318.5).withIndex()) {
            for (i in 0 until perNote) {
                val envelope = min(1.0, min(i.toDouble() / fade, (perNote - i).toDouble() / fade))
                val value = sin(2 * PI * frequency * i / RATE) * 0.25 * envelope
                out[index * perNote + i] = (value * Short.MAX_VALUE).toInt().toShort()
            }
        }
        return out
    }

    fun deviceName(context: Context, device: AudioDeviceInfo?): String {
        val res = context.resources
        if (device == null) return res.getString(R.string.nag_audio_default)
        val product = device.productName?.toString()?.takeIf { it.isNotBlank() } ?: ""
        return when (device.type) {
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE -> res.getString(R.string.nag_audio_speaker)
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET -> res.getString(R.string.nag_audio_headphones)
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO, AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_BLE_SPEAKER -> res.getString(R.string.nag_audio_bluetooth, product)
            AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_ACCESSORY ->
                res.getString(R.string.nag_audio_usb, product)
            AudioDeviceInfo.TYPE_HDMI, AudioDeviceInfo.TYPE_HDMI_ARC, AudioDeviceInfo.TYPE_HDMI_EARC -> res.getString(R.string.nag_audio_hdmi, product)
            else -> product.ifEmpty { res.getString(R.string.nag_audio_default) }
        }
    }
}
