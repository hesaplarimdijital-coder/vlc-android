package app.nagmedar.audio

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.util.Log
import androidx.core.content.ContextCompat
import java.io.Closeable

/**
 * S-903: the USB DAC driven directly by the engine (libusb, around Android's mixer). The device
 * comes from UsbManager with the user's permission, as the Lab's SP-02 did; the engine takes
 * its connection as the sink and streams to it bit-perfectly, with the DAC's own volume
 * control as the volume. While it plays, Android's own USB audio output of the device pauses
 * (the kernel driver is detached). A cable pulled ends in the engine's device-lost event; the
 * owner then chooses another output and [close]s this.
 */
class UsbOutput(context: Context, private val engine: MusicEngine) : Closeable {

    private val context = context.applicationContext
    private val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private var connection: UsbDeviceConnection? = null

    /** The device in use. */
    var device: UsbDevice? = null
        private set

    val active: Boolean get() = connection != null

    /** The USB audio devices attached. */
    fun devices(): List<UsbDevice> = manager.deviceList.values.filter { device ->
        (0 until device.interfaceCount).any { device.getInterface(it).interfaceClass == UsbConstants.USB_CLASS_AUDIO }
    }

    /** Whether [device] (the first found when null) is there and the app may open it. */
    fun permitted(device: UsbDevice? = null): Boolean = (device ?: devices().firstOrNull())?.let { manager.hasPermission(it) } == true

    /**
     * Asks the user for [device]; [onResult] comes on the main thread with the answer. The
     * owner then calls [start].
     */
    fun requestPermission(device: UsbDevice, onResult: (Boolean) -> Unit) {
        if (manager.hasPermission(device)) {
            onResult(true)
            return
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                if (intent.action != ACTION_PERMISSION) return
                runCatching { context.unregisterReceiver(this) }
                onResult(intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false))
            }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(ACTION_PERMISSION), ContextCompat.RECEIVER_NOT_EXPORTED)
        val intent = Intent(ACTION_PERMISSION).setPackage(context.packageName)
        val pending = PendingIntent.getBroadcast(context, 1, intent, PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        manager.requestPermission(device, pending)
    }

    /** Opens [device] (the first permitted one when null) and makes it the engine's sink. */
    fun start(device: UsbDevice? = null): Boolean {
        val chosen = device ?: devices().firstOrNull { manager.hasPermission(it) } ?: return false
        if (!manager.hasPermission(chosen)) return false
        val opened = runCatching { manager.openDevice(chosen) }.getOrNull() ?: return false
        /* the engine lets go of the sink before (and its descriptor) as it takes this one */
        val before = connection
        if (!engine.useUsbSink(opened)) {
            Log.w(TAG, "the engine did not take ${chosen.productName}: ${engine.usbError}")
            opened.close()
            return false
        }
        before?.close()
        connection = opened
        this.device = chosen
        Log.i(TAG, "USB output on ${chosen.productName}")
        return true
    }

    /** Whether the device in use is still attached. */
    fun stillValid(): Boolean = device?.let { d -> devices().any { it.deviceId == d.deviceId } } == true

    /** The DAC's own volume, 0..1; false without a volume control (the engine's volume applies then). */
    fun setVolume(volume: Double): Boolean = active && engine.setUsbVolume(volume)

    val hasVolume: Boolean get() = active && engine.usbHasVolume

    /** Leaves the device: the owner sets the engine's next sink first, so the engine lets go of this one. */
    fun stop() {
        connection?.close()
        connection = null
        device = null
    }

    override fun close() = stop()

    companion object {
        private const val TAG = "NagmedarAudio"
        private const val ACTION_PERMISSION = "app.nagmedar.audio.USB_PERMISSION"
    }
}
