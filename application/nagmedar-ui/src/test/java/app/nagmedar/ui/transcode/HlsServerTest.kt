package app.nagmedar.ui.transcode

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.net.InetAddress
import java.net.Socket
import java.nio.file.Files

/**
 * S-1303: the HLS server serves the session's playlist and segments and nothing else: no path out
 * of its folder (dots, slashes, escapes, a link), no other file in it, no other method; and only
 * to the local network's devices.
 */
class HlsServerTest {

    private lateinit var root: File
    private lateinit var outside: File

    @Before
    fun setUp() {
        val base = Files.createTempDirectory("hls").toFile()
        root = File(base, "session").apply { mkdirs() }
        outside = File(base, "secret.txt").apply { writeText("secret") }
        File(root, "stream.m3u8").writeText("#EXTM3U\n#EXT-X-TARGETDURATION:6\n#EXTINF:6,\nseg-00000001.ts\n")
        File(root, "seg-00000001.ts").writeBytes(ByteArray(188 * 4) { 0x47 })
        File(root, "notes.txt").writeText("not served")
        File(root, "seg-00000009.ts").mkdirs()
    }

    @After
    fun tearDown() {
        root.parentFile?.deleteRecursively()
    }

    @Test
    fun onlyTheSessionsFilesResolve() {
        assertEquals(File(root, "stream.m3u8").canonicalFile, HlsFiles.resolve(root, "/stream.m3u8"))
        assertEquals(File(root, "seg-00000001.ts").canonicalFile, HlsFiles.resolve(root, "/seg-00000001.ts?t=3"))
        val refused = listOf(
            "/../secret.txt", "/..%2fsecret.txt", "/%2e%2e/secret.txt", "/session/../../secret.txt", "//etc/passwd", "/etc/passwd",
            "stream.m3u8", "/notes.txt", "/seg-00000009.ts", "/seg-00000002.ts", "/seg-1.ts/../stream.m3u8", "/seg-1.ts\u0000",
            "/STREAM.M3U8", "/stream.m3u8/", "/./stream.m3u8", "\\stream.m3u8", "/seg-00000001.ts\\..\\..\\secret.txt", "",
        )
        refused.forEach { assertNull(it, HlsFiles.resolve(root, it)) }
    }

    @Test
    fun aLinkOutOfTheFolderIsRefused() {
        val link = File(root, "seg-00000005.ts").toPath()
        val made = runCatching { Files.createSymbolicLink(link, outside.toPath()) }.isSuccess
        if (made) assertNull(HlsFiles.resolve(root, "/seg-00000005.ts"))
    }

    @Test
    fun theLanGuardKeepsToTheSubnet() {
        val local = InetAddress.getByName("192.168.1.20")
        assertTrue(LanGuard.allowed(InetAddress.getByName("192.168.1.77"), local, 24))
        assertFalse(LanGuard.allowed(InetAddress.getByName("192.168.2.77"), local, 24))
        assertTrue(LanGuard.allowed(InetAddress.getByName("192.168.2.77"), local, 16))
        assertFalse(LanGuard.allowed(InetAddress.getByName("8.8.8.8"), local, 24))
        assertTrue(LanGuard.allowed(InetAddress.getByName("127.0.0.1"), local, 24))
        assertFalse(LanGuard.allowed(InetAddress.getByName("fe80::1"), local, 24))
        assertTrue(LanGuard.isLanAddress(local))
        assertTrue(LanGuard.isLanAddress(InetAddress.getByName("10.0.0.5")))
        assertFalse(LanGuard.isLanAddress(InetAddress.getByName("100.64.0.1")))
        assertFalse(LanGuard.isLanAddress(InetAddress.getByName("8.8.4.4")))
    }

    private fun request(port: Int, line: String): String =
        Socket(InetAddress.getLoopbackAddress(), port).use { socket ->
            socket.getOutputStream().write("$line\r\nHost: x\r\n\r\n".toByteArray())
            socket.getInputStream().readBytes().toString(Charsets.ISO_8859_1)
        }

    @Test
    fun theServerAnswers() {
        HlsServer(root, InetAddress.getLoopbackAddress(), allowed = { true }).start().use { server ->
            val playlist = request(server.port, "GET /stream.m3u8 HTTP/1.1")
            assertTrue(playlist, playlist.startsWith("HTTP/1.1 200 OK\r\n"))
            assertTrue(playlist.contains("Content-Type: application/vnd.apple.mpegurl\r\n"))
            assertTrue(playlist.contains("Cache-Control: no-cache\r\n"))
            assertTrue(playlist.endsWith("seg-00000001.ts\n"))
            val segment = request(server.port, "GET /seg-00000001.ts HTTP/1.1")
            assertTrue(segment.contains("Content-Type: video/mp2t\r\nContent-Length: 752\r\n"))
            assertEquals(752, segment.substringAfter("\r\n\r\n").length)
            val head = request(server.port, "HEAD /seg-00000001.ts HTTP/1.1")
            assertTrue(head.startsWith("HTTP/1.1 200 OK") && head.endsWith("\r\n\r\n"))
            assertTrue(request(server.port, "GET /../secret.txt HTTP/1.1").startsWith("HTTP/1.1 404"))
            assertTrue(request(server.port, "GET /notes.txt HTTP/1.1").startsWith("HTTP/1.1 404"))
            assertTrue(request(server.port, "POST /stream.m3u8 HTTP/1.1").startsWith("HTTP/1.1 405"))
            assertTrue(request(server.port, "GET /stream.m3u8").startsWith("HTTP/1.1 400"))
            assertEquals(3, server.served)
        }
        HlsServer(root, InetAddress.getLoopbackAddress(), allowed = { false }).start().use { server ->
            assertTrue(request(server.port, "GET /stream.m3u8 HTTP/1.1").startsWith("HTTP/1.1 403"))
        }
    }
}
