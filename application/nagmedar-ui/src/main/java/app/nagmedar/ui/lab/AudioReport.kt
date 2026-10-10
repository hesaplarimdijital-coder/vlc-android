package app.nagmedar.ui.lab

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioMixerAttributes
import android.media.AudioTrack
import android.os.Build
import androidx.annotation.RequiresApi

/**
 * SP-01: what the Android audio stack offers on this device for each output, above all whether
 * USB DACs get a bit-perfect mixer (Android 14 AudioMixerAttributes). The report is plain text
 * so it can be copied into the spike's decision note.
 */
object AudioReport {

    fun build(context: Context): String = buildString {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        appendLine("Nağmedar ses çıkışı raporu (SP-01)")
        appendLine(deviceLine())
        appendLine("Çıkış örnekleme hızı: ${audio.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)} Hz, tampon: ${audio.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)} kare")
        appendLine("AudioTrack yerel hız: ${AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC)} Hz")
        appendLine("Bit-perfect API (Android 14+): ${if (Build.VERSION.SDK_INT >= 34) "var" else "yok (SDK ${Build.VERSION.SDK_INT})"}")
        appendLine()
        val outputs = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        if (outputs.isEmpty()) appendLine("Çıkış aygıtı bulunamadı.")
        outputs.sortedBy { if (isUsb(it)) 0 else 1 }.forEach { device ->
            appendLine("■ ${typeName(device.type)} — ${device.productName} (id ${device.id})")
            appendLine("  Örnekleme hızları: ${device.sampleRates.joinToString().ifEmpty { "herhangi" }}")
            appendLine("  Kanal sayıları: ${device.channelCounts.joinToString().ifEmpty { "herhangi" }}")
            appendLine("  Kodlamalar: ${device.encodings.joinToString { encodingName(it) }.ifEmpty { "herhangi" }}")
            if (Build.VERSION.SDK_INT >= 31) appendProfiles(device)
            if (Build.VERSION.SDK_INT >= 34) appendMixers(audio, device)
            appendLine()
        }
        appendLine("Not: bit-perfect çıkış için USB DAC bağlıyken raporu yeniden oluşturun; iki farklı DAC ile tekrarlayın.")
    }

    @RequiresApi(31)
    private fun StringBuilder.appendProfiles(device: AudioDeviceInfo) {
        val profiles = device.audioProfiles
        if (profiles.isEmpty()) return
        appendLine("  Ses profilleri:")
        profiles.forEach { profile ->
            appendLine("    ${encodingName(profile.format)}: ${profile.sampleRates.joinToString().ifEmpty { "herhangi" }} Hz, kanal maskeleri ${profile.channelMasks.size}")
        }
    }

    @RequiresApi(34)
    private fun StringBuilder.appendMixers(audio: AudioManager, device: AudioDeviceInfo) {
        val mixers = audio.getSupportedMixerAttributes(device)
        if (mixers.isEmpty()) {
            appendLine("  Mikser seçenekleri: yok")
            return
        }
        appendLine("  Mikser seçenekleri:")
        mixers.forEach { mixer -> appendLine("    ${mixerLine(mixer)}") }
        val bitPerfect = mixers.count { it.mixerBehavior == AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT }
        appendLine("  BIT_PERFECT: ${if (bitPerfect > 0) "VAR ($bitPerfect biçim)" else "yok"}")
        val preferred = audio.getPreferredMixerAttributes(mediaAttributes(), device)
        appendLine("  Tercih edilen mikser: ${preferred?.let { mixerLine(it) } ?: "varsayılan"}")
    }

    /**
     * Tries the first bit-perfect format of a USB output: sets it as the preferred mixer for media,
     * reads it back and clears it again. Returns the outcome as text.
     */
    fun tryBitPerfect(context: Context): String {
        if (Build.VERSION.SDK_INT < 34) return "Bu Android sürümünde bit-perfect API yok (SDK ${Build.VERSION.SDK_INT})."
        return tryBitPerfect34(context)
    }

    @RequiresApi(34)
    private fun tryBitPerfect34(context: Context): String {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val usb = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).filter { isUsb(it) }
        if (usb.isEmpty()) return "USB ses aygıtı bağlı değil."
        return buildString {
            usb.forEach { device ->
                val mixer = audio.getSupportedMixerAttributes(device).firstOrNull { it.mixerBehavior == AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT }
                if (mixer == null) {
                    appendLine("${device.productName}: bit-perfect biçim sunulmuyor.")
                    return@forEach
                }
                val set = audio.setPreferredMixerAttributes(mediaAttributes(), device, mixer)
                val readBack = audio.getPreferredMixerAttributes(mediaAttributes(), device)
                appendLine("${device.productName}: ${mixerLine(mixer)} → ayarlandı: $set, okunan: ${readBack?.let { mixerLine(it) } ?: "varsayılan"}")
                audio.clearPreferredMixerAttributes(mediaAttributes(), device)
            }
        }.trim()
    }

    @RequiresApi(34)
    private fun mixerLine(mixer: AudioMixerAttributes): String {
        val format = mixer.format
        val behaviour = if (mixer.mixerBehavior == AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT) "BIT_PERFECT" else "DEFAULT"
        return "$behaviour ${encodingName(format.encoding)} ${format.sampleRate} Hz, kanal maskesi 0x${Integer.toHexString(format.channelMask)}"
    }

    private fun mediaAttributes(): AudioAttributes =
        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()

    private fun isUsb(device: AudioDeviceInfo) =
        device.type == AudioDeviceInfo.TYPE_USB_DEVICE || device.type == AudioDeviceInfo.TYPE_USB_HEADSET || device.type == AudioDeviceInfo.TYPE_USB_ACCESSORY

    internal fun deviceLine() = "Cihaz: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE}), Android ${Build.VERSION.RELEASE} / SDK ${Build.VERSION.SDK_INT}"

    private fun typeName(type: Int) = when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Dahili hoparlör"
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "Ahize"
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Kablolu kulaklık"
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Kablolu kulaklık (mikrofonlu)"
        AudioDeviceInfo.TYPE_USB_DEVICE -> "USB aygıtı"
        AudioDeviceInfo.TYPE_USB_HEADSET -> "USB kulaklık"
        AudioDeviceInfo.TYPE_USB_ACCESSORY -> "USB aksesuar"
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth A2DP"
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth SCO"
        AudioDeviceInfo.TYPE_HDMI -> "HDMI"
        AudioDeviceInfo.TYPE_HDMI_ARC -> "HDMI ARC"
        27 -> "BLE kulaklık"
        29 -> "HDMI eARC"
        30 -> "BLE yayın"
        else -> "Tür $type"
    }

    internal fun encodingName(encoding: Int) = when (encoding) {
        AudioFormat.ENCODING_PCM_16BIT -> "PCM 16"
        AudioFormat.ENCODING_PCM_8BIT -> "PCM 8"
        AudioFormat.ENCODING_PCM_FLOAT -> "PCM float"
        AudioFormat.ENCODING_AC3 -> "AC-3"
        AudioFormat.ENCODING_E_AC3 -> "E-AC-3"
        AudioFormat.ENCODING_DTS -> "DTS"
        AudioFormat.ENCODING_DTS_HD -> "DTS-HD"
        AudioFormat.ENCODING_DOLBY_TRUEHD -> "TrueHD"
        AudioFormat.ENCODING_IEC61937 -> "IEC 61937"
        21 -> "PCM 24 packed"
        22 -> "PCM 32"
        else -> "kodlama $encoding"
    }
}
