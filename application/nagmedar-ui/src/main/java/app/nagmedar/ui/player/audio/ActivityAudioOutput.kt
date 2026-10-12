package app.nagmedar.ui.player.audio

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import org.videolan.libvlc.MediaPlayer
import org.videolan.resources.VLCOptions
import org.videolan.tools.KEY_AOUT
import org.videolan.tools.Settings
import org.videolan.vlc.gui.video.VideoPlayerActivity

/**
 * The classic player's audio output (S-508), through libVLC's Java side: the device it plays to
 * ("stereo", "pcm", or "encoded:…" for a bitstream), which it chooses itself from what is plugged
 * in unless told; and the devices Android lists, with the codecs each takes as a bitstream.
 */
internal class ActivityAudioOutput(private val activity: VideoPlayerActivity) : AudioOutputTarget {

    private val player: MediaPlayer? get() = activity.service?.mediaplayer?.takeIf { !it.isReleased }

    override fun apply(mode: AudioOutputMode, codecs: Set<PassthroughCodec>): Boolean {
        val player = player ?: return false
        val settings = Settings.getInstance(activity)
        // OpenSL ES cannot send a bitstream
        if (mode == AudioOutputMode.Passthrough || mode == AudioOutputMode.Forced) {
            if (settings.getString(KEY_AOUT, "-1") == AOUT_OPENSLES) return false
        }
        return runCatching {
            when (mode) {
                AudioOutputMode.Auto -> {
                    // the player listens to what is plugged in again, and sends PCM
                    player.setAudioOutputDevice(null)
                    VLCOptions.setAudioDigitalOutputEnabled(settings, false)
                    player.setAudioDigitalOutputEnabled(false)
                }
                AudioOutputMode.Stereo -> {
                    VLCOptions.setAudioDigitalOutputEnabled(settings, false)
                    player.setAudioOutputDevice(DEVICE_STEREO)
                }
                AudioOutputMode.Multichannel -> {
                    VLCOptions.setAudioDigitalOutputEnabled(settings, false)
                    player.setAudioOutputDevice(DEVICE_PCM)
                }
                AudioOutputMode.Passthrough -> {
                    // the bitstream of what the device says it takes, as the classic setting has it
                    player.setAudioOutputDevice(null)
                    VLCOptions.setAudioDigitalOutputEnabled(settings, true)
                    player.setAudioDigitalOutputEnabled(true)
                }
                AudioOutputMode.Forced -> {
                    VLCOptions.setAudioDigitalOutputEnabled(settings, false)
                    if (codecs.isEmpty()) player.setAudioOutputDevice(null) else player.forceAudioDigitalEncodings(codecs.map { it.encoding }.toIntArray())
                }
            }
        }.getOrDefault(false)
    }

    override fun canPassthrough(): Boolean = player?.let { runCatching { it.canDoPassthrough() }.getOrDefault(false) } ?: false

    override fun sinks(): List<AudioSink> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return emptyList()
        val manager = activity.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return emptyList()
        return manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .filter { it.isSink && it.type != AudioDeviceInfo.TYPE_TELEPHONY && it.type != AudioDeviceInfo.TYPE_BUILTIN_EARPIECE }
            .map { info ->
                AudioSink(
                    name = info.productName?.toString()?.takeIf { it.isNotBlank() } ?: activity.getString(kindOf(info.type).label),
                    kind = kindOf(info.type),
                    codecs = info.encodings.map { PassthroughCodec.of(it) }.filterNotNull().toSet(),
                )
            }
            .distinct()
    }

    private fun kindOf(type: Int): AudioSinkKind = when (type) {
        AudioDeviceInfo.TYPE_HDMI, AudioDeviceInfo.TYPE_HDMI_ARC, TYPE_HDMI_EARC -> AudioSinkKind.Hdmi
        AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_ACCESSORY, AudioDeviceInfo.TYPE_USB_HEADSET -> AudioSinkKind.Usb
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO, TYPE_BLE_HEADSET, TYPE_BLE_SPEAKER -> AudioSinkKind.Bluetooth
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET -> AudioSinkKind.Headphones
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, TYPE_BUILTIN_SPEAKER_SAFE -> AudioSinkKind.Speaker
        else -> AudioSinkKind.Other
    }

    private companion object {
        const val DEVICE_STEREO = "stereo"
        const val DEVICE_PCM = "pcm"
        const val AOUT_OPENSLES = "2"

        /** AudioDeviceInfo types newer than the minimum SDK. */
        const val TYPE_BUILTIN_SPEAKER_SAFE = 24
        const val TYPE_BLE_HEADSET = 26
        const val TYPE_BLE_SPEAKER = 27
        const val TYPE_HDMI_EARC = 29
    }
}
