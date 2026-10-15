package app.nagmedar.ui.convert

import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * S-1303: which files of an HLS session a request may have: the playlist and the segments by
 * their names, in the session's folder itself, nothing else. A path with anything but those
 * names' letters ("..", "/", "%", "\") is refused before the disk is looked at; the file found
 * must be a plain file whose real path is in the folder (a link out of it is refused).
 */
object HlsFiles {
    private val names = Regex("""^(?:${Regex.escape(SoutChain.HLS_PLAYLIST)}|seg-\d{1,12}\.ts)$""")

    /** The file [rawPath] ("/stream.m3u8?x=1") names in [root]; null when it is not one this server serves. */
    fun resolve(root: File, rawPath: String): File? {
        val path = rawPath.substringBefore('?').substringBefore('#')
        if (!path.startsWith("/")) return null
        val name = path.substring(1)
        if (!names.matches(name)) return null
        val base = runCatching { root.canonicalFile }.getOrNull() ?: return null
        val file = runCatching { File(base, name).canonicalFile }.getOrNull() ?: return null
        return file.takeIf { it.parentFile == base && it.isFile }
    }

    fun contentType(file: File): String = if (file.name.endsWith(".m3u8")) "application/vnd.apple.mpegurl" else "video/mp2t"
}

/**
 * S-1303: whom the stream is for: devices on the same local network. A client is served when it is
 * in the subnet of the address the server listens on, or on the device itself.
 */
object LanGuard {

    /** Whether [client] is [local]'s neighbour in a /[prefix] network, or this device. */
    fun allowed(client: InetAddress, local: InetAddress, prefix: Int): Boolean {
        if (client.isLoopbackAddress) return true
        val a = client.address
        val b = local.address
        if (a.size != b.size || prefix !in 0..a.size * 8) return false
        var bits = prefix
        for (i in a.indices) {
            if (bits <= 0) break
            val mask = if (bits >= 8) 0xFF else (0xFF shl (8 - bits)) and 0xFF
            if ((a[i].toInt() and mask) != (b[i].toInt() and mask)) return false
            bits -= 8
        }
        return true
    }

    /** A private IPv4 address a local network gives (10/8, 172.16/12, 192.168/16): what the stream is offered on. */
    fun isLanAddress(address: InetAddress): Boolean = address is Inet4Address && address.isSiteLocalAddress
}

/**
 * S-1303: the HLS session's tiny web server: GET and HEAD of [HlsFiles]' files in [root], on
 * [address] (the device's local network address) only, to [allowed] clients; a fresh playlist at
 * every request. One request a connection; four at a time.
 */
class HlsServer(
    private val root: File,
    private val address: InetAddress,
    private val allowed: (InetAddress) -> Boolean,
    port: Int = 0,
) : Closeable {
    private val socket = ServerSocket().apply {
        reuseAddress = true
        bind(InetSocketAddress(address, port))
    }
    private val workers: ExecutorService = Executors.newFixedThreadPool(WORKERS)
    private val acceptor = Thread(::accept, "nagmedar-hls").apply { isDaemon = true }

    /** The port it listens on. */
    val port: Int get() = socket.localPort

    private val answered = AtomicInteger()

    /** Files served so far: the sheet shows that a device watches. */
    val served: Int get() = answered.get()

    fun start(): HlsServer {
        acceptor.start()
        return this
    }

    private fun accept() {
        while (!socket.isClosed) {
            val client = try {
                socket.accept()
            } catch (e: IOException) {
                break
            }
            runCatching { workers.execute { client.use(::answer) } }.onFailure { client.close() }
        }
    }

    private fun answer(client: Socket) {
        client.soTimeout = TIMEOUT_MS
        val out = BufferedOutputStream(client.getOutputStream())
        if (!allowed(client.inetAddress)) return respond(out, 403, "Forbidden")
        val input = client.getInputStream().buffered()
        val line = readLine(input) ?: return respond(out, 400, "Bad Request")
        // the headers are not needed: read to the blank line, within the limit
        while (true) {
            val header = readLine(input) ?: return respond(out, 400, "Bad Request")
            if (header.isEmpty()) break
        }
        val parts = line.split(' ')
        if (parts.size != 3 || !parts[2].startsWith("HTTP/1.")) return respond(out, 400, "Bad Request")
        val head = parts[0] == "HEAD"
        if (parts[0] != "GET" && !head) return respond(out, 405, "Method Not Allowed", "Allow: GET, HEAD\r\n")
        val file = HlsFiles.resolve(root, parts[1]) ?: return respond(out, 404, "Not Found")
        val length = file.length()
        val playlist = file.name.endsWith(".m3u8")
        val headers = "HTTP/1.1 200 OK\r\nContent-Type: ${HlsFiles.contentType(file)}\r\nContent-Length: $length\r\n" +
            (if (playlist) "Cache-Control: no-cache\r\n" else "Cache-Control: max-age=3600\r\n") +
            "Access-Control-Allow-Origin: *\r\nConnection: close\r\n\r\n"
        out.write(headers.toByteArray(Charsets.US_ASCII))
        if (!head) file.inputStream().use { input -> input.copyTo(out) }
        out.flush()
        answered.incrementAndGet()
    }

    private fun respond(out: OutputStream, code: Int, reason: String, extra: String = "") {
        val body = "$code $reason\n"
        out.write("HTTP/1.1 $code $reason\r\nContent-Type: text/plain\r\nContent-Length: ${body.length}\r\n${extra}Connection: close\r\n\r\n$body".toByteArray(Charsets.US_ASCII))
        out.flush()
    }

    /** A line of at most [LINE_MAX] bytes without its CR LF; null at the end of the stream or past the limit. */
    private fun readLine(input: InputStream): String? {
        val line = StringBuilder()
        while (true) {
            val c = input.read()
            if (c < 0) return null
            if (c == '\n'.code) return line.toString().trimEnd('\r')
            if (line.length >= LINE_MAX) return null
            line.append(c.toChar())
        }
    }

    override fun close() {
        runCatching { socket.close() }
        workers.shutdownNow()
        runCatching { workers.awaitTermination(2, TimeUnit.SECONDS) }
    }

    private companion object {
        const val WORKERS = 4
        const val TIMEOUT_MS = 10_000
        const val LINE_MAX = 8192
    }
}
