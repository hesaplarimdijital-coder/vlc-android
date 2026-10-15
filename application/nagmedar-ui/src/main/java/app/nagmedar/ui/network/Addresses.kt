package app.nagmedar.ui.network

import java.util.Locale

/**
 * S-1301: the protocols libVLC reads network folders with, as the network browser offers them.
 * [scheme] is libVLC's; [defaultPort] is left out of an address; [signIn] says whether a user
 * name and a password are asked for; [port] whether the port can be changed. UPnP/DLNA servers
 * are only found on the network, never typed in ([typed]). [library] marks those the
 * medialibrary scans, so a folder of theirs can join the library (VLC's own list).
 */
enum class NetworkProtocol(
    val scheme: String,
    val label: String,
    val defaultPort: Int?,
    val signIn: Boolean = true,
    val port: Boolean = true,
    val typed: Boolean = true,
    val library: Boolean = false,
) {
    Smb("smb", "SMB", 445, library = true),
    Ftp("ftp", "FTP", 21, library = true),
    Ftps("ftps", "FTPS", 990, library = true),
    Ftpes("ftpes", "FTPES", 21, library = true),
    Sftp("sftp", "SFTP", 22),
    Nfs("nfs", "NFS", 2049, signIn = false, port = false, library = true),
    WebDav("dav", "WebDAV", 80),
    WebDavs("davs", "WebDAVS", 443),
    Upnp("upnp", "UPnP/DLNA", null, signIn = false, port = false, typed = false),
    ;

    /** The form's choice this protocol belongs to: FTP with or without TLS, WebDAV over HTTP or HTTPS. */
    val family: ProtocolFamily
        get() = when (this) {
            Smb -> ProtocolFamily.Smb
            Ftp, Ftps, Ftpes -> ProtocolFamily.Ftp
            Sftp -> ProtocolFamily.Sftp
            Nfs -> ProtocolFamily.Nfs
            WebDav, WebDavs -> ProtocolFamily.WebDav
            Upnp -> ProtocolFamily.Upnp
        }

    companion object {
        /** The protocol of [scheme] ("smb", "SMB", "davs", "webdav"); null for any other. */
        fun of(scheme: String?): NetworkProtocol? {
            val s = scheme?.lowercase(Locale.ROOT) ?: return null
            return when (s) {
                "smb2" -> Smb
                "webdav" -> WebDav
                "webdavs" -> WebDavs
                else -> entries.firstOrNull { it.scheme == s }
            }
        }
    }
}

/** What the add-server form offers first; FTP's TLS and WebDAV's HTTPS are chosen under it. */
enum class ProtocolFamily(val label: String) {
    Smb("SMB"),
    Ftp("FTP"),
    Sftp("SFTP"),
    Nfs("NFS"),
    WebDav("WebDAV"),
    Upnp("UPnP/DLNA"),
    ;

    companion object {
        /** In the form's order. */
        val typed = listOf(Smb, Ftp, Sftp, Nfs, WebDav)
    }
}

/** What is wrong with an address being typed. */
enum class AddressError { HostMissing, HostInvalid, PortInvalid, PathInvalid }

/**
 * S-1301: a server's address: its protocol, host, port (null: the protocol's own) and the path
 * of the folder it opens on, as typed (not encoded: "Filmler/Yeni klasör"; an SMB path starts
 * with the share). [url] is the address libVLC reads; it never holds a user or a password.
 */
data class ServerAddress(
    val protocol: NetworkProtocol,
    val host: String,
    val port: Int? = null,
    val path: String = "",
) {
    /** "smb://nas.local/Filmler/Yeni%20klas%C3%B6r", "ftp://[fe80::1]:2121/", "dav://nas/dav/". */
    val url: String
        get() = buildString {
            append(protocol.scheme).append("://")
            val h = host.trim()
            append(if (':' in h && !h.startsWith("[")) "[$h]" else h)
            port?.takeIf { protocol.port && it != protocol.defaultPort }?.let { append(':').append(it) }
            append('/')
            append(segments(path).joinToString("/") { encodeSegment(it) })
        }

    /** What is wrong with it; empty when libVLC can be given it. */
    val errors: Set<AddressError>
        get() = buildSet {
            val h = host.trim()
            when {
                h.isEmpty() -> add(AddressError.HostMissing)
                !validHost(h) -> add(AddressError.HostInvalid)
            }
            if (port != null && port !in 1..65535) add(AddressError.PortInvalid)
            if (path.any { it.isISOControl() }) add(AddressError.PathInvalid)
        }

    companion object {
        /**
         * The address [url] holds; null when it is no network folder's. A user or a password in it
         * is left out; [ServerAddress.url] gives the address back in its canonical form. UPnP
         * addresses ("upnp://http://…") are libVLC's own and kept as found: what is read of them
         * is the device's host and port, for showing.
         */
        fun parse(url: String): ServerAddress? {
            val text = url.trim()
            val schemeEnd = text.indexOf("://")
            if (schemeEnd <= 0) return null
            val protocol = NetworkProtocol.of(text.substring(0, schemeEnd)) ?: return null
            var rest = text.substring(schemeEnd + 3)
            if (protocol == NetworkProtocol.Upnp) {
                val inner = parseAuthority(rest.substringAfter("://", rest)) ?: return null
                return ServerAddress(protocol, inner.first, inner.second, "")
            }
            rest = rest.substringBefore('?').substringBefore('#')
            val slash = rest.indexOf('/')
            val authority = if (slash >= 0) rest.substring(0, slash) else rest
            val path = if (slash >= 0) rest.substring(slash + 1) else ""
            val (host, port) = parseAuthority(authority) ?: return null
            val decoded = segments(path).joinToString("/") { decodeSegment(it) }
            return ServerAddress(protocol, host, port, decoded)
        }

        /** "user:pw@host:port", "[fe80::1]:21", "nas": the host and the port; null when the port is no number. */
        private fun parseAuthority(authority: String): Pair<String, Int?>? {
            val hostPort = authority.substringAfterLast('@').substringBefore('/')
            if (hostPort.startsWith("[")) {
                val close = hostPort.indexOf(']')
                if (close < 0) return null
                val host = hostPort.substring(1, close)
                val after = hostPort.substring(close + 1)
                val port = if (after.startsWith(":")) after.substring(1).toIntOrNull() ?: return null else null
                return host to port
            }
            // a bare IPv6 address has no port
            if (hostPort.count { it == ':' } > 1) return hostPort to null
            val host = hostPort.substringBefore(':')
            val portText = hostPort.substringAfter(':', "")
            val port = if (portText.isEmpty()) null else portText.toIntOrNull() ?: return null
            return host to port
        }

        /** The path's folders: back slashes as slashes, no empty ones. */
        internal fun segments(path: String): List<String> = path.replace('\\', '/').split('/').map { it.trim() }.filter { it.isNotEmpty() }

        private const val UNRESERVED = "-._~"

        /** A folder's name as an address writes it: UTF-8, every byte but the unreserved ones as %XX. */
        internal fun encodeSegment(segment: String): String = buildString {
            for (byte in segment.toByteArray(Charsets.UTF_8)) {
                val c = byte.toInt() and 0xFF
                val ch = c.toChar()
                if (c < 0x80 && (ch.isLetterOrDigit() || ch in UNRESERVED)) append(ch) else append('%').append("%02X".format(Locale.ROOT, c))
            }
        }

        /** A folder's name from an address: %XX read as UTF-8 bytes; a lone % stays. */
        internal fun decodeSegment(segment: String): String {
            if ('%' !in segment) return segment
            val out = java.io.ByteArrayOutputStream()
            var i = 0
            while (i < segment.length) {
                val ch = segment[i]
                val hex = if (ch == '%' && i + 2 < segment.length && isHex(segment[i + 1]) && isHex(segment[i + 2])) segment.substring(i + 1, i + 3).toInt(16) else null
                if (hex != null) {
                    out.write(hex)
                    i += 3
                } else {
                    out.write(ch.toString().toByteArray(Charsets.UTF_8))
                    i++
                }
            }
            return out.toString(Charsets.UTF_8.name())
        }

        private fun isHex(c: Char) = c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F'

        private val hostname = Regex("""^(?=.{1,253}$)[A-Za-z0-9_]([A-Za-z0-9_-]{0,62})(\.[A-Za-z0-9_]([A-Za-z0-9_-]{0,62}))*\.?$""")
        private val ipv6 = Regex("""^\[?[0-9A-Fa-f:.]+(%[A-Za-z0-9]+)?]?$""")

        /** A host name ("nas.local", "NAS-SALON"), an IPv4 address or an IPv6 one (bracketed or not). */
        internal fun validHost(host: String): Boolean = when {
            ':' in host -> ipv6.matches(host) && host.count { it == ':' } >= 2
            else -> hostname.matches(host) && !host.startsWith("-")
        }
    }
}

/** The host of [url] as a person reads it: "nas.local", "192.168.1.20"; the address itself when it has none. */
fun displayHost(url: String): String = ServerAddress.parse(url)?.host?.takeIf { it.isNotEmpty() } ?: url

/**
 * Whether [media] lies under [base]: the same protocol, host and port, and [base]'s folder or one
 * below it. Compared as addresses are written, letters of the host in either case.
 */
fun isUnder(media: String, base: String): Boolean {
    val m = ServerAddress.parse(media) ?: return false
    val b = ServerAddress.parse(base) ?: return false
    if (m.protocol != b.protocol || !m.host.equals(b.host, ignoreCase = true)) return false
    val defaultPort = m.protocol.defaultPort
    if ((m.port ?: defaultPort) != (b.port ?: defaultPort)) return false
    val baseSegments = ServerAddress.segments(b.path)
    val mediaSegments = ServerAddress.segments(m.path)
    return mediaSegments.size >= baseSegments.size && mediaSegments.subList(0, baseSegments.size) == baseSegments
}

/**
 * The libVLC options that sign in to a [protocol] server, as its access modules read them; empty
 * for those that take a sign-in only from libVLC's login request (WebDAV) or need none. An SMB
 * user written "DOMAIN\user" goes as the domain and the user.
 */
fun signInOptions(protocol: NetworkProtocol?, credentials: Credentials): List<String> {
    val (user, password) = credentials.user to credentials.password
    return when (protocol) {
        NetworkProtocol.Smb -> {
            val domain = user.substringBefore('\\', "").takeIf { it.isNotEmpty() }
            listOfNotNull(":smb-user=${user.substringAfter('\\')}", ":smb-pwd=$password", domain?.let { ":smb-domain=$it" })
        }
        NetworkProtocol.Ftp, NetworkProtocol.Ftps, NetworkProtocol.Ftpes -> listOf(":ftp-user=$user", ":ftp-pwd=$password")
        NetworkProtocol.Sftp -> listOf(":sftp-user=$user", ":sftp-pwd=$password")
        else -> emptyList()
    }
}

/**
 * Whether libVLC's request [words] name [host] as a whole: "nas" in "for nas." or "nas:445", not
 * in "nas-backup" or "nas.local"; "10.0.0.1" not in "10.0.0.12"; an IPv6 address bare or in
 * brackets. Letters in either case.
 */
fun hostNamed(words: String, host: String): Boolean {
    val name = host.trim().removePrefix("[").removeSuffix("]")
    if (name.isEmpty()) return false
    val ipv6 = ':' in name
    fun hostChar(c: Char) = c.isLetterOrDigit() || c == '-' || c == '_' || (ipv6 && c == ':')
    var from = 0
    while (true) {
        val at = words.indexOf(name, from, ignoreCase = true)
        if (at < 0) return false
        val end = at + name.length
        val before = words.getOrNull(at - 1)
        val after = words.getOrNull(end)
        val startsClean = before == null || !(hostChar(before) || before == '.')
        // a dot ends a sentence ("for nas."), but not a longer name ("nas.local")
        val endsClean = after == null || (!hostChar(after) && !(after == '.' && words.getOrNull(end + 1)?.let(::hostChar) == true))
        if (startsClean && endsClean) return true
        from = at + 1
    }
}
