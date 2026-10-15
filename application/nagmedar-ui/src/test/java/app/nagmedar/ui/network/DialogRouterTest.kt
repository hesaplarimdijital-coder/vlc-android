package app.nagmedar.ui.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** What a request of libVLC's got: "login user/password store", "action 1", "dismiss". */
class RecordingReply(private val log: MutableList<String>) : PromptReply {
    override fun login(user: String, password: String, store: Boolean) {
        log += "login $user/$password $store"
    }

    override fun action(action: Int) {
        log += "action $action"
    }

    override fun dismiss() {
        log += "dismiss"
    }
}

/** The window's requests, kept. */
class RecordingHost : PromptHost {
    val shown = mutableListOf<LibVlcPrompt>()

    override fun show(prompt: LibVlcPrompt): Boolean = shown.add(prompt)

    override fun gone(prompt: LibVlcPrompt): Boolean = shown.remove(prompt)
}

/**
 * S-1301: libVLC's requests sent where they belong: a folder's sign-in by the host it names, never
 * another server's; WebDAV's realm-only one to the one WebDAV server opened; a saved server's for
 * the library's scan; a folder's question to its page; the rest to the window, or VLC's.
 */
class DialogRouterTest {

    private val log = mutableListOf<String>()
    private var clock = 0L
    private val saved = mutableMapOf<String, Credentials>()
    private val handedBack = mutableListOf<LibVlcPrompt>()
    private val router = DialogRouter(
        savedSignIn = { words, reply -> reply(saved.entries.firstOrNull { hostNamed(words, it.key) }?.value) },
        now = { clock },
        handBack = { handedBack += it },
    )

    private fun login(text: String, title: String = "SMB authentication") =
        LibVlcPrompt(PromptKind.Login, title, text, reply = RecordingReply(log))

    private fun question(text: String) =
        LibVlcPrompt(PromptKind.Question, "Insecure site", text, action1 = "Accept permanently", action2 = "Accept once", reply = RecordingReply(log))

    private val ali = Credentials("ali", "pw")

    @Test
    fun aFoldersSignInIsGivenOnceThenTurnedDown() {
        val claim = DialogClaim.browsing("smb://nas.local/Filmler", ali, triedInOptions = false, since = 0) {}
        router.begin(claim)
        assertTrue(router.route(login("Please enter a valid login name and password for NAS.LOCAL")))
        assertEquals(listOf("login ali/pw false"), log)
        // asked again: turned down, the page asks
        assertTrue(router.route(login("Please enter a valid login name and password for nas.local")))
        assertEquals("dismiss", log.last())
        assertEquals(BrowseResult.SignInRejected, claim.result(done = false))
        // given as the module's options, the first request already means they were turned down
        val options = DialogClaim.browsing("ftp://arsiv/", Credentials("a", "b"), triedInOptions = true, since = 0) {}
        router.begin(options)
        router.route(login("for the ftp connexion to arsiv", title = "FTP authentication"))
        assertEquals(BrowseResult.SignInRejected, options.result(done = false))
    }

    @Test
    fun howAReadingEnds() {
        assertEquals(BrowseResult.Done, DialogClaim("nas", NetworkProtocol.Smb, null).result(done = true))
        assertEquals(BrowseResult.Unreachable, DialogClaim("nas", NetworkProtocol.Smb, null).result(done = false))
        val needed = DialogClaim.browsing("smb://nas/", null, triedInOptions = false, since = 0) {}
        router.begin(needed)
        router.route(login("password for nas"))
        assertEquals(listOf("dismiss"), log)
        assertEquals(BrowseResult.SignInNeeded, needed.result(done = false))
        // libvlc_media_parsed_status_t of libVLC 4, in libvlc_media.h's order: only "done" is a folder read
        val libVlc4 = listOf("none", "pending", "skipped", "failed", "timeout", "cancelled", "done")
        assertEquals(listOf("done"), libVlc4.indices.filter { ReadingEnd.read(it) }.map { libVlc4[it] })
        assertFalse("libVLC 3's done (4) is libVLC 4's timeout", ReadingEnd.read(4))
        assertFalse(ReadingEnd.read(null))
    }

    @Test
    fun aHostIsNamedWholeOnly() {
        val host = RecordingHost()
        router.host = host
        router.begin(DialogClaim.browsing("smb://nas/", ali, triedInOptions = false, since = 0) {})
        router.begin(DialogClaim.browsing("ftp://10.0.0.1/", Credentials("ip", "pw"), triedInOptions = false, since = 0) {})
        router.route(login("Please enter a valid login name and password for nas-backup"))
        router.route(login("Please enter a valid login name and password for nas.local"))
        router.route(login("for the ftp connexion to 10.0.0.12", title = "FTP authentication"))
        assertTrue("no password sent to a near name", log.isEmpty())
        assertEquals(3, host.shown.size)
        router.route(login("for the ftp connexion to 10.0.0.1:2121", title = "FTP authentication"))
        assertEquals(listOf("login ip/pw false"), log)
        // a question for a near name is not the folder's either
        router.route(question("You attempted to reach nas2."))
        assertEquals(4, host.shown.size)
    }

    @Test
    fun anotherServersRequestIsNeverAnsweredWithThisOnesSignIn() {
        val host = RecordingHost()
        router.host = host
        router.begin(DialogClaim.browsing("smb://nas.local/", ali, triedInOptions = false, since = 0) {})
        val other = login("Please enter a valid login name and password for medya-pc")
        assertTrue(router.route(other))
        assertTrue("nothing sent", log.isEmpty())
        assertSame(other, host.shown.single())
        // with the window behind, VLC's own windows take it
        router.host = null
        assertFalse(router.route(login("password for medya-pc")))
    }

    @Test
    fun aWebDavRealmGoesToTheOneWebDavServerOpened() {
        val host = RecordingHost()
        router.host = host
        val bulut = DialogClaim.browsing("davs://bulut.example.org/dav", ali, triedInOptions = false, since = 0) {}
        router.begin(bulut)
        router.route(login("Please enter a valid login name and a password for realm Nextcloud.", title = "HTTP authentication"))
        assertEquals(listOf("login ali/pw false"), log)
        // two WebDAV servers at once: neither guessed
        router.begin(DialogClaim.browsing("dav://baska/", Credentials("x", "y"), triedInOptions = false, since = 0) {})
        val realm = login("for realm Files.", title = "HTTP authentication")
        router.route(realm)
        assertEquals(1, log.size)
        assertSame(realm, host.shown.single())
    }

    @Test
    fun aFilesClaimEndsAfterAMinuteEvenWithNothingElseBegun() {
        val host = RecordingHost()
        router.host = host
        router.begin(DialogClaim("bulut.example.org", NetworkProtocol.WebDavs, ali, browse = false, since = 0))
        clock = DialogRouter.PLAY_CLAIM_MS + 1
        // another server's realm-only request, long after the file started: not the file's
        val later = login("for realm Files.", title = "HTTP authentication")
        router.route(later)
        assertTrue(log.isEmpty())
        assertSame(later, host.shown.single())
    }

    @Test
    fun aFileStartingToPlaySignsInFromItsSavedServer() {
        val host = RecordingHost()
        router.host = host
        router.begin(DialogClaim("bulut.example.org", NetworkProtocol.WebDavs, ali, browse = false, since = 0))
        router.route(login("for realm Nextcloud.", title = "HTTP authentication"))
        assertEquals(listOf("login ali/pw false"), log)
        // turned down: the person is asked
        val again = login("for realm Nextcloud.", title = "HTTP authentication")
        router.route(again)
        assertSame(again, host.shown.single())
        // a minute later the file's claim is over
        clock = DialogRouter.PLAY_CLAIM_MS + 1
        router.begin(DialogClaim.browsing("smb://nas/", null, triedInOptions = false, since = clock) {})
        router.route(login("for realm Nextcloud.", title = "HTTP authentication"))
        assertEquals(2, host.shown.size)
    }

    @Test
    fun aSavedServerSignsInForTheLibrarysScanOnceUntilTypedAgain() {
        val host = RecordingHost()
        router.host = host
        saved["nas.local"] = ali
        router.savedHosts = setOf("nas.local")
        assertTrue(router.route(login("password for nas.local")))
        assertEquals(listOf("login ali/pw false"), log)
        // asked again: the kept one was turned down; the person is asked, now and later (no lockout)
        router.route(login("password for nas.local"))
        clock = 3_600_000
        router.route(login("password for nas.local"))
        assertEquals(1, log.size)
        assertEquals(2, host.shown.size)
        // typed anew: used again
        router.renewed("NAS.local")
        router.route(login("password for nas.local"))
        assertEquals(2, log.size)
        // a near name is not the saved server's
        router.route(login("password for nas.local2"))
        assertEquals(2, log.size)
    }

    @Test
    fun aRequestNobodyHereCanAnswerGoesToVlcsWindows() {
        router.savedHosts = setOf("nas.local")
        // the saved sign-in cannot be opened and no window is in front
        assertTrue(router.route(login("password for nas.local")))
        assertEquals(1, handedBack.size)
        assertTrue("not dismissed", log.isEmpty())
    }

    @Test
    fun aFoldersQuestionWaitsOnItsPageAndGoesWithIt() {
        val asked = mutableListOf<LibVlcPrompt?>()
        val claim = DialogClaim.browsing("davs://bulut.example.org/", null, triedInOptions = false, since = 0) { asked += it }
        router.begin(claim)
        val certificate = question("You attempted to reach bulut.example.org. However the security certificate presented by the server is unknown")
        assertTrue(router.route(certificate))
        assertSame(certificate, asked.single())
        // libVLC took it back
        assertTrue(router.canceled(certificate))
        assertNull(asked.last())
        assertTrue(certificate.settled)
        // one left waiting is dismissed when the reading ends, and the claim answers no more
        val again = question("You attempted to reach bulut.example.org.")
        router.route(again)
        router.end(claim)
        assertEquals("dismiss", log.last())
        assertFalse(router.active(claim))
        // another site's question is the window's
        val host = RecordingHost()
        router.host = host
        val other = question("You attempted to reach example.com.")
        assertTrue(router.route(other))
        assertSame(other, host.shown.single())
        assertTrue(router.canceled(other))
        assertTrue(host.shown.isEmpty())
    }

    @Test
    fun aRequestIsAnsweredOnce() {
        val prompt = login("x")
        prompt.login("a", "b", false)
        prompt.dismiss()
        prompt.action(1)
        assertEquals(listOf("login a/b false"), log)
    }
}
