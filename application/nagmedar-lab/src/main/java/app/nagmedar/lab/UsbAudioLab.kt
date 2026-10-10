package app.nagmedar.lab

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.coroutines.resume

/**
 * SP-02: a USB DAC driven directly (libusb), around the Android mixer. The device comes from
 * UsbManager with the user's permission; the native side detaches the kernel driver while it
 * streams, so Android's own USB audio output pauses during the test.
 */
object UsbAudioLab {

    private const val ACTION_PERMISSION = "app.nagmedar.lab.USB_PERMISSION"

    fun audioDevices(context: Context): List<UsbDevice> {
        val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        return manager.deviceList.values.filter { device ->
            (0 until device.interfaceCount).any { device.getInterface(it).interfaceClass == UsbConstants.USB_CLASS_AUDIO }
        }
    }

    private suspend fun permission(context: Context, device: UsbDevice): Boolean {
        val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        if (manager.hasPermission(device)) return true
        return suspendCancellableCoroutine { continuation ->
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context, intent: Intent) {
                    if (intent.action != ACTION_PERMISSION) return
                    runCatching { context.unregisterReceiver(this) }
                    if (continuation.isActive) continuation.resume(intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false))
                }
            }
            ContextCompat.registerReceiver(context, receiver, IntentFilter(ACTION_PERMISSION), ContextCompat.RECEIVER_NOT_EXPORTED)
            continuation.invokeOnCancellation { runCatching { context.unregisterReceiver(receiver) } }
            val intent = Intent(ACTION_PERMISSION).setPackage(context.packageName)
            val pending = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            manager.requestPermission(device, pending)
        }
    }

    private suspend fun <T> withDevice(context: Context, block: (UsbDevice, Int) -> T): Pair<UsbDevice?, T?> {
        val device = audioDevices(context).firstOrNull() ?: return null to null
        if (!permission(context, device)) return device to null
        val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        val connection = manager.openDevice(device) ?: return device to null
        return try {
            device to withContext(Dispatchers.IO) { block(device, connection.fileDescriptor) }
        } finally {
            connection.close()
        }
    }

    private fun header(device: UsbDevice) = buildString {
        appendLine("Aygıt: ${device.manufacturerName.orEmpty()} ${device.productName.orEmpty()}".trim())
        appendLine(String.format(Locale.ROOT, "USB kimliği: %04x:%04x, sürüm %s", device.vendorId, device.productId, device.version))
    }

    suspend fun describe(context: Context): String {
        if (!NativeLab.available) return "Bu sürümde yerel laboratuvar kitaplığı yok."
        val (device, json) = withDevice(context) { _, fd -> NativeLab.usbDescribe(fd) }
        if (device == null) return "USB ses aygıtı bulunamadı. DAC'ı bağlayıp yeniden deneyin."
        if (json == null) return header(device) + "İzin verilmedi ya da aygıt açılamadı."
        return header(device) + formatDescription(JSONObject(json))
    }

    suspend fun tone(context: Context, rate: Int, bits: Int, seconds: Double, progress: NativeLab.Progress): String {
        if (!NativeLab.available) return "Bu sürümde yerel laboratuvar kitaplığı yok."
        val (device, json) = withDevice(context) { _, fd -> NativeLab.usbTone(fd, rate, bits, seconds, 1000.0, progress) }
        if (device == null) return "USB ses aygıtı bulunamadı. DAC'ı bağlayıp yeniden deneyin."
        if (json == null) return header(device) + "İzin verilmedi ya da aygıt açılamadı."
        return header(device) + formatTone(JSONObject(json), rate, bits, seconds)
    }

    private fun formatFormat(f: JSONObject) = buildString {
        append("arayüz ${f.optInt("interface")}/${f.optInt("alt")}: ${f.optInt("channels")} kanal, ${f.optInt("bits")} bit ")
        append("(${f.optInt("subslot_bytes")} bayt), uç ${f.optInt("endpoint")}, paket ${f.optInt("max_packet")} bayt, ")
        append("senkron ${f.optString("sync")}")
        if (f.optInt("feedback_endpoint") != 0) append(", geri bildirim ucu ${f.optInt("feedback_endpoint")}")
        val rates = f.optJSONArray("rates") ?: JSONArray()
        if (rates.length() > 0) {
            append("\n    hızlar: ")
            append((0 until rates.length()).joinToString { i ->
                when (val r = rates.get(i)) {
                    is JSONArray -> if (r.optLong(0) == r.optLong(1)) "${r.optLong(0)}" else "${r.optLong(0)}–${r.optLong(1)}"
                    else -> r.toString()
                }
            })
        }
    }

    fun formatDescription(r: JSONObject): String = buildString {
        r.optString("error").takeIf { it.isNotEmpty() }?.let { return "Hata: $it" }
        appendLine("USB Audio Class ${r.optInt("uac")}, hız: ${r.optString("speed")}, denetim arayüzü ${r.optInt("control_interface")}")
        val playback = r.optJSONArray("playback") ?: JSONArray()
        appendLine("Çalma biçimleri (${playback.length()}):")
        for (i in 0 until playback.length()) appendLine("  • " + formatFormat(playback.getJSONObject(i)))
    }

    fun formatTone(r: JSONObject, rate: Int, bits: Int, seconds: Double): String = buildString {
        appendLine("Nağmedar USB doğrudan çıkış testi (SP-02)")
        appendLine("İstenen: $rate Hz, $bits bit, ${seconds.toInt()} sn, 1 kHz -6 dBFS")
        appendLine("Aygıtın hızı: önce ${r.optLong("rate_before")} Hz, sonra ${r.optLong("rate_after")} Hz (saat ${r.optInt("clock")})")
        r.optJSONObject("format")?.let { appendLine("Biçim: " + formatFormat(it)) }
        val frames = r.optLong("frames")
        val expected = r.optDouble("expected_frames")
        appendLine("Gönderilen: $frames kare / beklenen ${expected.toLong()} (${String.format(Locale.ROOT, "%+.3f", if (expected > 0) (frames - expected) / expected * 100 else 0.0)} %)")
        appendLine("Süre: ${String.format(Locale.ROOT, "%.1f", r.optDouble("seconds"))} sn, paket: ${r.optLong("packets")}")
        appendLine("Geri bildirim: ${String.format(Locale.ROOT, "%.2f", r.optDouble("feedback_rate"))} Hz (${r.optLong("feedback_updates")} güncelleme)")
        appendLine("Aktarım hatası: ${r.optLong("transfer_errors")}, paket hatası: ${r.optLong("packet_errors")}")
        r.optString("error").takeIf { it.isNotEmpty() }?.let { appendLine("Hata: $it") }
        appendLine()
        appendLine("Kabul için: 24 bit / 96 kHz ile 10 dakika; ton kesintisiz duyulmalı, hata sayıları sıfır kalmalı.")
    }
}
