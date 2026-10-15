package app.nagmedar.ui.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/** S-206, S-1301: the network browser's reducer, step by step, and what the servers' list shows of the network. */
class NetworkStateTest {

    private val nas = OpenServer("NAS", "smb://nas/Filmler", savedId = 1)
    private val turkish = Locale.forLanguageTag("tr-TR")

    private fun entry(name: String, kind: EntryKind = EntryKind.of(name, false)) = NetworkEntry("smb://nas/Filmler/$name", name, kind)

    @Test
    fun aFolderFillsFoldersFirstInItsLanguagesOrder() {
        val opening = FolderView.opening(nas, now = 10)
        assertEquals(FolderStatus.Connecting(10), opening.status)
        assertEquals(listOf(Crumb("NAS", "smb://nas/Filmler")), opening.trail)
        val order = FolderView.order(nas, turkish)
        val view = opening
            .found(listOf(entry("Zafer.mkv"), entry("Bölüm 10.mkv"), entry("Çay.mkv")), order)
            .found(listOf(entry("Bölüm 2.mkv"), entry("Ağaç", EntryKind.Folder), entry("bahçe.mkv")), order)
            .ended(BrowseResult.Done)
        assertEquals(listOf("Ağaç", "bahçe.mkv", "Bölüm 2.mkv", "Bölüm 10.mkv", "Çay.mkv", "Zafer.mkv"), view.entries.map { it.name })
        assertEquals(FolderStatus.Listed, view.status)
        assertEquals(5, view.playable.size)
    }

    @Test
    fun upnpServersKeepTheirOwnOrder() {
        val upnp = OpenServer("Medya sunucusu", "upnp://http://192.168.1.10:8200/rootDesc.xml")
        assertNull(FolderView.order(upnp, turkish))
        val view = FolderView.opening(upnp, 0).found(listOf(entry("Video"), entry("Müzik")), null)
        assertEquals(listOf("Video", "Müzik"), view.entries.map { it.name })
    }

    @Test
    fun howAReadingEnds() {
        val opening = FolderView.opening(nas, 0).found(listOf(entry("a.mkv")), null)
        assertEquals(FolderStatus.Unreachable, opening.ended(BrowseResult.Unreachable).status)
        assertEquals(FolderStatus.SignIn(SignInReason.Needed), opening.ended(BrowseResult.SignInNeeded).status)
        val rejected = opening.ended(BrowseResult.SignInRejected)
        assertEquals(FolderStatus.SignIn(SignInReason.Rejected), rejected.status)
        assertTrue("nothing of a refused folder shows", rejected.entries.isEmpty())
        assertEquals(FolderStatus.SignIn(SignInReason.Forgotten), opening.signIn(SignInReason.Forgotten).status)
        val again = opening.ended(BrowseResult.Done).reread(now = 99)
        assertEquals(FolderStatus.Connecting(99), again.status)
        assertTrue(again.entries.isEmpty())
    }

    @Test
    fun goingDownAndUpKeepsWhatWasListed() {
        val root = FolderView.opening(nas, 0).found(listOf(entry("2024", EntryKind.Folder)), null).ended(BrowseResult.Done)
        val year = root.into(NetworkEntry("smb://nas/Filmler/2024", "2024", EntryKind.Folder), now = 5)
        assertEquals(listOf("NAS", "2024"), year.trail.map { it.name })
        assertEquals("smb://nas/Filmler/2024", year.url)
        val month = year.ended(BrowseResult.Done).into(NetworkEntry("smb://nas/Filmler/2024/Ocak", "Ocak", EntryKind.Folder), now = 6)
        assertEquals(root, month.upTo(0))
        assertEquals(listOf("NAS", "2024"), month.upTo(1).trail.map { it.name })
        assertEquals(month, month.upTo(5))
        assertEquals(root, year.up())
        assertNull(root.up())
        assertTrue(root.atRoot)
        // given up while reading: back to the folder above as it was, or nothing open at the root
        assertEquals(root, year.cancelled())
        assertNull(FolderView.opening(nas, 0).cancelled())
    }

    @Test
    fun theNetworksServersComeAndGo() {
        val nasFound = DiscoveredServer("smb://NAS/", "NAS")
        val tv = DiscoveredServer("upnp://http://192.168.1.10:8200/rootDesc.xml", "Bravia")
        var discovery = Discovery(searching = true).found(nasFound).found(tv).found(nasFound.copy(name = "NAS"))
        assertEquals(listOf("Bravia", "NAS"), discovery.servers.map { it.name })
        assertEquals(NetworkProtocol.Upnp, discovery.servers.first().protocol)
        // a saved server shows once, among the saved
        assertEquals(listOf(tv), discovery.unsaved(listOf(SavedServer(1, "NAS", "smb://NAS", signedIn = false))))
        discovery = discovery.lost(tv.url)
        assertEquals(listOf(nasFound), discovery.servers)
    }

    @Test
    fun numbersInNamesSortAsNumbers() {
        val collator = java.text.Collator.getInstance(turkish)
        assertTrue(FolderView.naturalCompare("Bölüm 2", "Bölüm 10", collator) < 0)
        assertTrue(FolderView.naturalCompare("a007", "a7", collator) == 0)
        assertTrue(FolderView.naturalCompare("ılık", "inek", collator) < 0)
    }
}
