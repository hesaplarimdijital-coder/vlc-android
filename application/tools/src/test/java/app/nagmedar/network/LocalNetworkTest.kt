package app.nagmedar.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Android 17: when the local network permission is asked for, where it stands after the system's answers, and which
 * addresses are on the local network (the servers, devices, radios and feeds that need it).
 */
class LocalNetworkTest {

    @Test
    fun onlyAnAppThatTargets37AsksOnAndroid17() {
        assertFalse(LocalNetwork.required(sdk = 36, targetSdk = 36))
        assertFalse(LocalNetwork.required(sdk = 36, targetSdk = 37))
        // the shipped build: Android 17 grants it with INTERNET
        assertFalse(LocalNetwork.required(sdk = 37, targetSdk = 36))
        assertTrue(LocalNetwork.required(sdk = 37, targetSdk = 37))
        assertTrue(LocalNetwork.required(sdk = 38, targetSdk = 37))
    }

    @Test
    fun theStateFollowsTheAnswers() {
        assertEquals(LocalNetworkState.Granted, LocalNetwork.state(required = false, granted = false, asked = true, rationale = false))
        assertEquals(LocalNetworkState.Granted, LocalNetwork.state(required = true, granted = true, asked = true, rationale = false))
        // never asked: the question comes at the first need
        assertEquals(LocalNetworkState.Ask, LocalNetwork.state(required = true, granted = false, asked = false, rationale = false))
        // refused once: Android asks again
        assertEquals(LocalNetworkState.Denied, LocalNetwork.state(required = true, granted = false, asked = true, rationale = true))
        // refused for good: only the system settings
        assertEquals(LocalNetworkState.Blocked, LocalNetwork.state(required = true, granted = false, asked = true, rationale = false))
        // taken back in the system settings, never asked by this install: Android would ask
        assertEquals(LocalNetworkState.Denied, LocalNetwork.state(required = true, granted = false, asked = false, rationale = true))
    }

    @Test
    fun theHostOfVlcsAddresses() {
        assertEquals("nas.local", LocalNetwork.hostOf("smb://nas.local/Filmler"))
        assertEquals("nas.local", LocalNetwork.hostOf("smb://ali@nas.local/Müzik"))
        assertEquals("MEDYA-PC", LocalNetwork.hostOf("smb://MEDYA-PC/"))
        assertEquals("192.168.1.10", LocalNetwork.hostOf("upnp://http://192.168.1.10:8200/rootDesc.xml"))
        assertEquals("10.0.2.2", LocalNetwork.hostOf("http://10.0.2.2:8765/feed.xml"))
        assertEquals("fe80::1%wlan0", LocalNetwork.hostOf("http://[fe80::1%wlan0]:8080/stream"))
        assertEquals("radio.example.com", LocalNetwork.hostOf("https://radio.example.com/live?x=1#y"))
        assertNull(LocalNetwork.hostOf("file:///sdcard/Music/a.flac"))
        // a content provider's authority is no host: "media" would read as a name on the network
        assertNull(LocalNetwork.hostOf("content://media/external/audio/media/12"))
        assertNull(LocalNetwork.hostOf("content://com.android.externalstorage.documents/tree/primary%3AMusic"))
        assertNull(LocalNetwork.hostOf("android.resource://app.nagmedar/raw/tone"))
        assertNull(LocalNetwork.hostOf("/sdcard/Music/a.flac"))
        assertNull(LocalNetwork.hostOf(null))
    }

    @Test
    fun privateLinkLocalAndMulticastAddressesAreLocal() {
        listOf("10.0.0.5", "10.0.2.2", "172.16.0.1", "172.31.255.254", "192.168.1.20", "169.254.3.4", "224.0.0.251", "239.255.255.250",
            "255.255.255.255").forEach { assertTrue(it, LocalNetwork.isLocalHost(it)) }
        listOf("fe80::1", "fe80::1%wlan0", "fd12:3456::1", "fc00::2", "ff02::fb").forEach { assertTrue(it, LocalNetwork.isLocalHost(it)) }
    }

    @Test
    fun theInternetAndTheLoopbackAreNot() {
        listOf("8.8.8.8", "172.32.0.1", "172.15.255.255", "192.169.0.1", "11.0.0.1", "127.0.0.1", "0.0.0.0", "2001:db8::1", "::1",
            "localhost", "radio.example.com", "arsiv.example.org", "1.2.3", "300.1.1.1").forEach { assertFalse(it, LocalNetwork.isLocalHost(it)) }
        // a VPN's (Tailscale's) and the carrier's shared addresses: Android counts them only on a Wi-Fi or Ethernet network
        assertFalse(LocalNetwork.isLocalHost("100.101.102.103"))
        assertFalse(LocalNetwork.isLocalHost(null))
        assertFalse(LocalNetwork.isLocalHost(""))
    }

    @Test
    fun namesServedAtHomeAreLocal() {
        listOf("nas.local", "NAS.LOCAL.", "medya-pc", "MEDYA-PC", "router.lan", "printer.home", "nas.home.arpa", "box.internal",
            "pi.localdomain").forEach { assertTrue(it, LocalNetwork.isLocalHost(it)) }
    }

    @Test
    fun theWalksFeedAndAHomeRadioNeedThePermission() {
        assertTrue(LocalNetwork.isLocalUrl("http://10.0.2.2:8765/feed.xml"))
        assertTrue(LocalNetwork.isLocalUrl("http://192.168.1.30:8000/radyo.mp3"))
        assertFalse(LocalNetwork.isLocalUrl("https://stream.example.com/radyo.mp3"))
        assertFalse(LocalNetwork.isLocalUrl("content://media/external/audio/media/12"))
    }
}
