package app.nagmedar.ui.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** S-1301: addresses written and read per protocol, never with a password; what a typed one gets wrong. */
class ServerAddressTest {

    @Test
    fun addressesAreWrittenAsLibVlcReadsThem() {
        assertEquals("smb://nas.local/Filmler", ServerAddress(NetworkProtocol.Smb, "nas.local", path = "Filmler").url)
        // the protocol's own port is left out, another is written
        assertEquals("smb://nas.local/Filmler", ServerAddress(NetworkProtocol.Smb, "nas.local", 445, "Filmler").url)
        assertEquals("ftp://192.168.1.20:2121/", ServerAddress(NetworkProtocol.Ftp, "192.168.1.20", 2121).url)
        assertEquals("ftps://ftp.example.org/", ServerAddress(NetworkProtocol.Ftps, "ftp.example.org", 990).url)
        assertEquals("ftpes://ftp.example.org/arsiv", ServerAddress(NetworkProtocol.Ftpes, "ftp.example.org", 21, "/arsiv/").url)
        assertEquals("sftp://pi.local:2222/home/pi/M%C3%BCzik", ServerAddress(NetworkProtocol.Sftp, "pi.local", 2222, "home/pi/Müzik").url)
        // NFS takes no port
        assertEquals("nfs://nas/volume1/video", ServerAddress(NetworkProtocol.Nfs, "nas", 2049, "volume1/video").url)
        assertEquals("dav://cloud.local/remote.php/dav", ServerAddress(NetworkProtocol.WebDav, "cloud.local", 80, "remote.php/dav").url)
        assertEquals("davs://cloud.example.org:8443/", ServerAddress(NetworkProtocol.WebDavs, "cloud.example.org", 8443).url)
        // IPv6 in brackets
        assertEquals("smb://[fe80::1]/Ortak", ServerAddress(NetworkProtocol.Smb, "fe80::1", path = "Ortak").url)
    }

    @Test
    fun foldersAreEncodedAndBackslashesAreSlashes() {
        assertEquals(
            "smb://nas/Filmler/Yeni%20klas%C3%B6r/%C4%B0stanbul%20%26%20%C3%87ay",
            ServerAddress(NetworkProtocol.Smb, "nas", path = "Filmler\\Yeni klasör/ /İstanbul & Çay//").url,
        )
        assertEquals("smb://nas/a%25b", ServerAddress(NetworkProtocol.Smb, "nas", path = "a%b").url)
    }

    @Test
    fun addressesAreReadBackWithoutTheSignIn() {
        val read = ServerAddress.parse("smb://ali:gizli@nas.local:4455/Filmler/Yeni%20klas%C3%B6r")!!
        assertEquals(ServerAddress(NetworkProtocol.Smb, "nas.local", 4455, "Filmler/Yeni klasör"), read)
        assertFalse("gizli" in read.url)
        assertEquals("smb://nas.local:4455/Filmler/Yeni%20klas%C3%B6r", read.url)
        assertEquals(ServerAddress(NetworkProtocol.Ftp, "fe80::1", 21, ""), ServerAddress.parse("ftp://[fe80::1]:21"))
        assertEquals(ServerAddress(NetworkProtocol.WebDavs, "cloud", null, "dav"), ServerAddress.parse("webdavs://cloud/dav?x=1#y"))
        assertEquals(NetworkProtocol.Smb, ServerAddress.parse("SMB2://NAS/")!!.protocol)
        // a lone % stays as it is
        assertEquals("100%", ServerAddress.parse("smb://nas/100%")!!.path)
        assertNull(ServerAddress.parse("http://example.org/"))
        assertNull(ServerAddress.parse("nas.local/Filmler"))
        assertNull(ServerAddress.parse("smb://nas:port/"))
        // each typed protocol round-trips
        listOf(
            ServerAddress(NetworkProtocol.Smb, "nas", 4450, "a/b c"),
            ServerAddress(NetworkProtocol.Ftps, "10.0.0.2", 2990, "x"),
            ServerAddress(NetworkProtocol.Sftp, "host", 22, "home/şule"),
            ServerAddress(NetworkProtocol.WebDav, "cloud", 8080, "dav/ğ"),
        ).forEach { address ->
            val back = ServerAddress.parse(address.url)!!
            assertEquals(address.url, back.url)
        }
    }

    @Test
    fun upnpAddressesKeepTheirDevicesHost() {
        val read = ServerAddress.parse("upnp://http://192.168.1.10:8200/rootDesc.xml")!!
        assertEquals(NetworkProtocol.Upnp, read.protocol)
        assertEquals("192.168.1.10", read.host)
        assertEquals("192.168.1.10", displayHost("upnp://http://192.168.1.10:8200/rootDesc.xml"))
        assertFalse(NetworkProtocol.Upnp.typed)
    }

    @Test
    fun whatATypedAddressGetsWrong() {
        fun errors(host: String, port: Int? = null, path: String = "") = ServerAddress(NetworkProtocol.Smb, host, port, path).errors
        assertEquals(setOf(AddressError.HostMissing), errors("  "))
        assertEquals(setOf(AddressError.HostInvalid), errors("nas local"))
        assertEquals(setOf(AddressError.HostInvalid), errors("nas/Filmler"))
        assertEquals(setOf(AddressError.HostInvalid), errors("-nas"))
        assertEquals(setOf(AddressError.HostInvalid), errors("ağ-diski"))
        assertEquals(setOf(AddressError.PortInvalid), errors("nas", 0))
        assertEquals(setOf(AddressError.PortInvalid), errors("nas", 70000))
        assertEquals(setOf(AddressError.PathInvalid), errors("nas", path = "a\u0007b"))
        listOf("nas", "NAS-SALON", "nas.local", "192.168.1.20", "fe80::1", "[fe80::1]", "fe80::1%wlan0", "my_server", "nas.local.").forEach {
            assertTrue(it, errors(it).isEmpty())
        }
    }

    @Test
    fun mediaLieUnderTheirServersFolder() {
        assertTrue(isUnder("smb://NAS.local/Filmler/2024/a.mkv", "smb://nas.local/Filmler"))
        assertTrue(isUnder("smb://nas.local:445/Filmler/a.mkv", "smb://nas.local/"))
        assertTrue(isUnder("smb://nas.local/Filmler", "smb://nas.local/Filmler/"))
        assertFalse(isUnder("smb://nas.local/Filmler2/a.mkv", "smb://nas.local/Filmler"))
        assertFalse(isUnder("smb://nas.local:4455/Filmler/a.mkv", "smb://nas.local/Filmler"))
        assertFalse(isUnder("ftp://nas.local/Filmler/a.mkv", "smb://nas.local/Filmler"))
        assertFalse(isUnder("smb://other/Filmler/a.mkv", "smb://nas.local/Filmler"))
    }

    @Test
    fun signInsGoAsTheModulesOptions() {
        val credentials = Credentials("ali", "p@ss word")
        assertEquals(listOf(":smb-user=ali", ":smb-pwd=p@ss word"), signInOptions(NetworkProtocol.Smb, credentials))
        assertEquals(
            listOf(":smb-user=ali", ":smb-pwd=x", ":smb-domain=EV"),
            signInOptions(NetworkProtocol.Smb, Credentials("EV\\ali", "x")),
        )
        listOf(NetworkProtocol.Ftp, NetworkProtocol.Ftps, NetworkProtocol.Ftpes).forEach {
            assertEquals(listOf(":ftp-user=ali", ":ftp-pwd=p@ss word"), signInOptions(it, credentials))
        }
        assertEquals(listOf(":sftp-user=ali", ":sftp-pwd=p@ss word"), signInOptions(NetworkProtocol.Sftp, credentials))
        // WebDAV signs in through libVLC's login request only; NFS and UPnP need none
        assertEquals(emptyList<String>(), signInOptions(NetworkProtocol.WebDav, credentials))
        assertEquals(emptyList<String>(), signInOptions(NetworkProtocol.Nfs, credentials))
        assertEquals(emptyList<String>(), signInOptions(null, credentials))
    }

    @Test
    fun filesAreKnownByTheirNames() {
        assertEquals(EntryKind.Folder, EntryKind.of("Filmler.mkv", folder = true))
        assertEquals(EntryKind.Video, EntryKind.of("Kuzey Işığı.MKV", folder = false))
        assertEquals(EntryKind.Audio, EntryKind.of("01 Neva.flac", folder = false))
        assertEquals(EntryKind.Playlist, EntryKind.of("yol.m3u8", folder = false))
        assertEquals(EntryKind.Subtitle, EntryKind.of("film.tr.srt", folder = false))
        assertEquals(EntryKind.Other, EntryKind.of("README", folder = false))
        assertTrue(EntryKind.Audio.playable)
        assertFalse(EntryKind.Subtitle.playable)
    }

    @Test
    fun hostsAreNamedWhole() {
        assertTrue(hostNamed("Please enter a valid login name and password for NAS.", "nas"))
        assertTrue(hostNamed("for nas:445", "nas"))
        assertTrue(hostNamed("nas", "NAS"))
        assertFalse(hostNamed("password for nas-backup", "nas"))
        assertFalse(hostNamed("password for nas.local", "nas"))
        assertFalse(hostNamed("password for mynas", "nas"))
        assertFalse(hostNamed("password for nas_2", "nas"))
        assertTrue(hostNamed("connexion to 10.0.0.1.", "10.0.0.1"))
        assertFalse(hostNamed("connexion to 10.0.0.12", "10.0.0.1"))
        assertFalse(hostNamed("connexion to 110.0.0.1", "10.0.0.1"))
        assertTrue(hostNamed("reach [fe80::1]:443", "fe80::1"))
        assertTrue(hostNamed("reach fe80::1", "[fe80::1]"))
        assertFalse(hostNamed("reach fe80::12", "fe80::1"))
        assertFalse(hostNamed("anything", ""))
        // the second mention is whole
        assertTrue(hostNamed("nas-backup or nas", "nas"))
    }
}
