package org.videolan.vlc.util

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build

/**
 * Nağmedar: the Wi-Fi kept awake while a network stream plays (a podcast episode, a radio station), so that its
 * power saving with the screen off does not starve the stream ([hold]). Low latency mode from Android 10, where the
 * high performance one is deprecated; high performance before. A device without Wi-Fi, or a Wi-Fi service that
 * refuses, plays on without it. Needs WAKE_LOCK, which the playback service has.
 */
class StreamWifiLock(context: Context) {
    private val lock: WifiManager.WifiLock? = runCatching {
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        @Suppress("DEPRECATION")
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) WifiManager.WIFI_MODE_FULL_LOW_LATENCY else WifiManager.WIFI_MODE_FULL_HIGH_PERF
        wifi?.createWifiLock(mode, TAG)?.apply { setReferenceCounted(false) }
    }.getOrNull()

    /** Held while [on], released otherwise; cheap to ask again with the same answer. */
    fun hold(on: Boolean) {
        val lock = lock ?: return
        try {
            if (on && !lock.isHeld) lock.acquire() else if (!on && lock.isHeld) lock.release()
        } catch (e: RuntimeException) {
            // some firmwares refuse the lock (a SecurityException, an IllegalStateException): playback goes on
        }
    }

    private companion object {
        const val TAG = "Nagmedar:stream"
    }
}
