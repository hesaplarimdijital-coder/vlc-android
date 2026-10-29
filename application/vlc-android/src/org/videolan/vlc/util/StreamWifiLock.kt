package org.videolan.vlc.util

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build

/**
 * Nağmedar: the Wi-Fi kept awake while a network stream plays (a podcast episode, a radio station), so that its
 * power saving does not starve the stream ([hold]). Low latency mode from Android 10, where the high performance one is
 * deprecated; high performance before. A device without Wi-Fi, or a Wi-Fi service that refuses, plays on without it.
 * Needs WAKE_LOCK, which the playback service has.
 *
 * Not counted on with the screen off: Android keeps a low latency lock only while the screen is on and an activity of
 * the app is in front (a foreground service is not enough), and from Android 14 (API 34) a high performance lock is
 * taken as a low latency one. With the screen off, on the S24 and the Tab S11 alike, the lock does nothing; what keeps a
 * stream going through Wi-Fi's power saving, a hand-over to mobile data or a connection that stalls is the reconnect
 * logic ([org.videolan.vlc.media.DropoutGuard]). The lock is kept as it is, harmless, for the screen-on case. libVLC's
 * network caching is not raised for it: it would delay every start of a stream.
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
