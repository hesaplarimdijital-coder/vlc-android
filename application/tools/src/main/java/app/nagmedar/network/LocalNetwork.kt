package app.nagmedar.network

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.util.Locale

/**
 * Android 17: the local network permission ([PERMISSION], in the "Nearby devices" group). An app that targets Android
 * 17 reaches the devices of its Wi-Fi or Ethernet network only with it: the servers and shares of Sources › Network,
 * the DLNA and Chromecast devices it casts to, a browser that opens remote access, a radio or a podcast feed served at
 * home. Without it a connection waits until it times out and a multicast or a broadcast is refused. An app that
 * targets 36 or lower keeps that access (the system grants the permission with INTERNET) and must neither declare nor
 * ask for it (developer.android.com, "Local network permission"): the build that targets 37 declares it
 * (application/app/src/targetSdk37/AndroidManifest.xml).
 */
object LocalNetwork {
    const val PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"

    /** Android 17's API level: the permission is asked for from there, by the apps that target it. */
    const val API = 37

    /** Whether the permission has to be given: Android 17 or later ([sdk]), and an app that targets it ([targetSdk]). */
    fun required(sdk: Int, targetSdk: Int): Boolean = sdk >= API && targetSdk >= API

    fun required(context: Context): Boolean = required(Build.VERSION.SDK_INT, context.applicationInfo.targetSdkVersion)

    /** Whether the local network can be reached: the permission is not asked for here, or it was given. */
    fun granted(context: Context): Boolean =
        !required(context) || context.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED

    /**
     * Where the permission stands: given (or not asked for), never asked, refused once (Android asks again) or
     * refused for good ([rationale] is Android's word that it would still ask; [asked]: the app asked before).
     */
    fun state(required: Boolean, granted: Boolean, asked: Boolean, rationale: Boolean): LocalNetworkState = when {
        !required || granted -> LocalNetworkState.Granted
        rationale -> LocalNetworkState.Denied
        asked -> LocalNetworkState.Blocked
        else -> LocalNetworkState.Ask
    }

    /**
     * Told when a media on the local network is opened without the permission, with its host (the window says it,
     * with the way to give it); set by the new interface. Any thread.
     */
    @Volatile
    var onBlocked: ((host: String) -> Unit)? = null

    /** A media at [url] is about to be opened: [onBlocked] hears of it when its host is on the local network and the permission is missing. */
    fun opening(context: Context, url: String?) {
        val host = hostOf(url) ?: return
        if (isLocalHost(host) && !granted(context)) onBlocked?.invoke(host)
    }

    /**
     * The host of [url], VLC's addresses too: "upnp://http://192.168.1.10:8200/rootDesc.xml" is 192.168.1.10's,
     * "smb://user@nas.local/Music" nas.local's, "http://[fe80::1]:8080/" fe80::1's; null for a file or a content address.
     */
    fun hostOf(url: String?): String? {
        if (url == null) return null
        val scheme = url.indexOf("://")
        if (scheme <= 0) return null
        var rest = url.substring(scheme + 3)
        // VLC's UPnP addresses carry the device's own address after their scheme
        if (rest.indexOf("://").let { it > 0 && rest.substring(0, it).all { c -> c.isLetter() } }) return hostOf(rest)
        rest = rest.substringBefore('/').substringBefore('?').substringBefore('#')
        rest = rest.substringAfterLast('@')
        val host = if (rest.startsWith("[")) rest.substring(1).substringBefore(']') else rest.substringBefore(':')
        return host.trim().trimEnd('.').takeIf { it.isNotEmpty() }
    }

    /**
     * Whether [host] is on the local network as Android 17 counts it: a private, link-local or multicast address
     * (10/8, 172.16/12, 192.168/16, 169.254/16, 224/4, 255.255.255.255; IPv6 fe80::/10, fc00::/7, ff00::/8), a name
     * served there (".local", ".lan", ".home", ".home.arpa", ".internal", ".localdomain", or a single word: "MEDYA-PC").
     * The loopback is not the local network; nor is 100.64/10 here, which Android counts only on a Wi-Fi or Ethernet
     * network and which is mostly a VPN's (Tailscale) or the carrier's.
     */
    fun isLocalHost(host: String?): Boolean {
        val name = host?.trim()?.trimEnd('.')?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() } ?: return false
        ipv4(name)?.let { return localIpv4(it) }
        if (':' in name) return localIpv6(name)
        if (name == "localhost" || name.endsWith(".localhost")) return false
        if ('.' !in name) return true
        return LOCAL_SUFFIXES.any { name.endsWith(it) }
    }

    /** Whether the media at [url] is on the local network. */
    fun isLocalUrl(url: String?): Boolean = isLocalHost(hostOf(url))

    private val LOCAL_SUFFIXES = listOf(".local", ".lan", ".home", ".home.arpa", ".internal", ".localdomain")

    private fun ipv4(name: String): IntArray? {
        val parts = name.split('.')
        if (parts.size != 4) return null
        val octets = IntArray(4)
        for (i in 0 until 4) {
            val part = parts[i]
            if (part.isEmpty() || part.length > 3 || !part.all { it in '0'..'9' }) return null
            octets[i] = part.toInt().takeIf { it <= 255 } ?: return null
        }
        return octets
    }

    private fun localIpv4(o: IntArray): Boolean = when {
        o[0] == 10 -> true
        o[0] == 172 && o[1] in 16..31 -> true
        o[0] == 192 && o[1] == 168 -> true
        o[0] == 169 && o[1] == 254 -> true
        o[0] in 224..239 -> true
        o.all { it == 255 } -> true
        else -> false
    }

    private fun localIpv6(name: String): Boolean {
        val address = name.substringBefore('%')
        if (address == "::1") return false
        val first = address.substringBefore(':').takeIf { it.isNotEmpty() && it.length <= 4 }?.toIntOrNull(16) ?: return false
        return when {
            // fe80::/10 link-local
            first and 0xffc0 == 0xfe80 -> true
            // fc00::/7 unique local
            first and 0xfe00 == 0xfc00 -> true
            // ff00::/8 multicast
            first and 0xff00 == 0xff00 -> true
            else -> false
        }
    }
}

/** Where the local network permission stands, as the app's pages say it. */
enum class LocalNetworkState {
    /** Given, or not asked for here (Android 16 or earlier, or an app that targets 36 or lower). */
    Granted,

    /** Never asked: the system's question comes at the first need. */
    Ask,

    /** Refused once: Android asks again. */
    Denied,

    /** Refused for good: only the system settings give it now. */
    Blocked,
}
