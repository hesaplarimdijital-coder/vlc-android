package app.nagmedar.ui.network

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale
import kotlin.coroutines.EmptyCoroutineContext

/** A network of folders: what each address holds and how reading it ends; [held] ones never end. */
class FakeNetworkSource(
    private val folders: Map<String, Pair<List<NetworkEntry>, BrowseResult>> = emptyMap(),
    /** The sign-in each address wants; another (or none) is turned down. */
    private val signIns: Map<String, Credentials> = emptyMap(),
) : NetworkSource {
    val held = mutableSetOf<String>()

    /** libVLC's question while reading these addresses; the reading waits for it. */
    val questions = mutableMapOf<String, LibVlcPrompt>()
    val reads = mutableListOf<Pair<String, Credentials?>>()
    val discovered = MutableSharedFlow<DiscoveryEvent>(replay = 32)

    override fun discover(): Flow<DiscoveryEvent> = discovered

    override fun browse(url: String, credentials: Credentials?): Flow<BrowseEvent> = flow {
        reads += url to credentials
        questions[url]?.let { emit(BrowseEvent.Asked(it)) }
        if (url in held || url in questions) awaitCancellation()
        val wanted = signIns.entries.firstOrNull { isUnder(url, it.key) }?.value
        if (wanted != null && wanted != credentials) {
            emit(BrowseEvent.Ended(if (credentials == null) BrowseResult.SignInNeeded else BrowseResult.SignInRejected))
            return@flow
        }
        val (entries, result) = folders[url] ?: (emptyList<NetworkEntry>() to BrowseResult.Unreachable)
        entries.forEach { emit(BrowseEvent.Found(it)) }
        emit(BrowseEvent.Ended(result))
    }
}

/** Saved servers in a list; sign-ins as given, or unreadable for the ids in [unreadable]. */
class FakeServerStore : ServerStore {
    val rows = MutableStateFlow<List<SavedServer>>(emptyList())
    val signIns = mutableMapOf<Long, Credentials>()
    val unreadable = mutableSetOf<Long>()
    private var next = 1L

    override val servers: Flow<List<SavedServer>> get() = rows

    /** Sealing fails while set, as a Keystore that does not answer. */
    var sealFails = false
    val unavailable = mutableSetOf<Long>()

    override suspend fun save(id: Long, name: String, url: String, signIn: SignInChange): SaveResult {
        if (rows.value.any { it.url == url && it.id != id }) return SaveResult.AddressTaken
        val saved = if (id == 0L) next++ else id
        when (signIn) {
            is SignInChange.Use -> if (signIn.remember && !sealFails) signIns[saved] = signIn.credentials
            SignInChange.Forget -> signIns -= saved
            SignInChange.Unchanged -> Unit
        }
        rows.value = rows.value.filter { it.id != saved } + SavedServer(saved, name, url, saved in signIns)
        return SaveResult.Saved(saved, signInKept = !(sealFails && signIn is SignInChange.Use && signIn.remember))
    }

    override suspend fun remove(id: Long) {
        rows.value = rows.value.filter { it.id != id }
        signIns -= id
    }

    override suspend fun signIn(id: Long): StoredSignIn = when {
        id in unavailable -> StoredSignIn.Unavailable
        id in unreadable -> StoredSignIn.Unreadable.also { unreadable -= id }
        else -> signIns[id]?.let { StoredSignIn.Kept(it) } ?: StoredSignIn.None
    }

    override suspend fun used(id: Long) = Unit

    override fun signInFor(url: String): Credentials? =
        rows.value.filter { isUnder(url, it.url) }.firstNotNullOfOrNull { signIns[it.id] } ?: SessionSignIns.forUrl(url)

    override fun signInNamed(words: String): Credentials? =
        rows.value.filter { row -> ServerAddress.parse(row.url)?.host?.let { words.contains(it, ignoreCase = true) } == true }.firstNotNullOfOrNull { signIns[it.id] }
}

/** What was played and queued. */
class RecordingActions : NetworkActions {
    val played = mutableListOf<Pair<List<String>, Int>>()
    val queued = mutableListOf<List<String>>()
    val scanned = mutableListOf<String>()

    override fun play(entries: List<NetworkEntry>, index: Int) {
        played += entries.map { it.name } to index
    }

    override fun enqueue(entries: List<NetworkEntry>) {
        queued += entries.map { it.name }
    }

    override fun canAddToLibrary(url: String) = !url.startsWith("upnp")

    override fun addToLibrary(url: String) {
        scanned += url
    }
}

/** S-1301: the network browser's model with a fake network and store: opening, signing in, going up, playing a folder. */
class NetworkModelTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private var clock = 1_000L
    private val notices = mutableListOf<NetworkNotice>()

    private fun folder(path: String) = NetworkEntry("smb://nas/Filmler/$path", path.substringAfterLast('/'), EntryKind.Folder)
    private fun file(path: String) = NetworkEntry("smb://nas/Filmler/$path", path.substringAfterLast('/'), EntryKind.of(path, false))

    private val network = mapOf(
        "smb://nas/Filmler" to (listOf(file("b.mkv"), folder("Diziler"), file("a.mkv"), file("notlar.srt")) to BrowseResult.Done),
        "smb://nas/Filmler/Diziler" to (listOf(file("Diziler/s01e02.mkv"), file("Diziler/s01e01.mkv")) to BrowseResult.Done),
        "smb://nas/Bos" to (emptyList<NetworkEntry>() to BrowseResult.Done),
    )

    private fun model(source: NetworkSource, store: ServerStore = FakeServerStore()) =
        NetworkModel(source, store, scope, Locale.forLanguageTag("tr-TR"), now = { clock }, onNotice = { notices += it }, io = EmptyCoroutineContext)

    @After
    fun stop() {
        scope.cancel()
        SessionSignIns.clear()
    }

    @Test
    fun aSavedServerOpensWithItsSignIn() {
        val store = FakeServerStore()
        val source = FakeNetworkSource(network, signIns = mapOf("smb://nas/" to Credentials("ali", "pw")))
        val model = model(source, store)
        runBlocking { store.save(0, "NAS", "smb://nas/Filmler", SignInChange.Use(Credentials("ali", "pw"), remember = true)) }
        model.open(store.rows.value.single())
        val view = model.folder!!
        assertEquals(FolderStatus.Listed, view.status)
        assertEquals(listOf("Diziler", "a.mkv", "b.mkv", "notlar.srt"), view.entries.map { it.name })
        assertEquals(Credentials("ali", "pw"), source.reads.single().second)
        // a file plays with the folder's others; a subtitle file is not one of them
        val actions = RecordingActions()
        model.open(view.entries[2], actions)
        assertEquals(listOf(listOf("a.mkv", "b.mkv") to 1), actions.played)
    }

    @Test
    fun aSignInIsAskedForGivenAndKept() {
        val store = FakeServerStore()
        val source = FakeNetworkSource(network, signIns = mapOf("smb://nas/" to Credentials("ali", "pw")))
        val model = model(source, store)
        model.open(DiscoveredServer("smb://nas/Filmler", "NAS"))
        assertEquals(FolderStatus.SignIn(SignInReason.Needed), model.folder!!.status)
        model.signIn(Credentials("ali", "yanlış"), remember = true)
        assertEquals(FolderStatus.SignIn(SignInReason.Rejected), model.folder!!.status)
        model.signIn(Credentials("ali", "pw"), remember = true)
        assertEquals(FolderStatus.Listed, model.folder!!.status)
        // remembered: the server found on the network is saved with its sign-in
        val saved = store.rows.value.single()
        assertEquals("smb://nas/Filmler", saved.url)
        assertEquals(Credentials("ali", "pw"), store.signIns[saved.id])
        assertEquals(saved.id, model.folder!!.server.savedId)
    }

    @Test
    fun aSignInForThisSessionOnlyIsNotSaved() {
        val store = FakeServerStore()
        val model = model(FakeNetworkSource(network, signIns = mapOf("smb://nas/" to Credentials("ali", "pw"))), store)
        model.open(DiscoveredServer("smb://nas/Filmler", "NAS"))
        model.signIn(Credentials("ali", "pw"), remember = false)
        assertEquals(FolderStatus.Listed, model.folder!!.status)
        assertTrue(store.rows.value.isEmpty())
        assertEquals(Credentials("ali", "pw"), SessionSignIns.forUrl("smb://nas/Filmler/Diziler/a.mkv"))
    }

    @Test
    fun aSignInThatCannotBeOpenedIsAskedForAgain() {
        val store = FakeServerStore()
        val source = FakeNetworkSource(network)
        val model = model(source, store)
        runBlocking { store.save(0, "NAS", "smb://nas/Filmler", SignInChange.Use(Credentials("ali", "pw"), remember = true)) }
        store.unreadable += store.rows.value.single().id
        model.open(store.rows.value.single())
        assertEquals(FolderStatus.SignIn(SignInReason.Forgotten), model.folder!!.status)
        assertTrue("nothing read without a sign-in", source.reads.isEmpty())
    }

    @Test
    fun foldersOpenAndCloseWithoutReadingAgain() {
        val source = FakeNetworkSource(network)
        val model = model(source)
        val actions = RecordingActions()
        model.open(DiscoveredServer("smb://nas/Filmler", "NAS"))
        model.open(model.folder!!.entries.first(), actions)
        assertEquals(listOf("s01e01.mkv", "s01e02.mkv"), model.folder!!.entries.map { it.name })
        assertEquals(listOf("NAS", "Diziler"), model.folder!!.trail.map { it.name })
        assertTrue(model.up())
        assertEquals("smb://nas/Filmler", model.folder!!.url)
        assertEquals(2, source.reads.size)
        assertTrue("at the root: nothing above", !model.up())
        model.refresh()
        assertEquals(3, source.reads.size)
        model.goTo(0)
        assertEquals(3, source.reads.size)
    }

    @Test
    fun aSlowFolderIsGivenUp() {
        val source = FakeNetworkSource(network)
        source.held += "smb://nas/Filmler/Diziler"
        val model = model(source)
        model.open(DiscoveredServer("smb://nas/Filmler", "NAS"))
        val listed = model.folder!!
        clock = 5_000
        model.open(listed.entries.first(), RecordingActions())
        assertEquals(FolderStatus.Connecting(5_000), model.folder!!.status)
        model.cancel()
        assertEquals(listed, model.folder)
        // the root given up: nothing open
        source.held += "smb://nas/Yavas"
        model.open(DiscoveredServer("smb://nas/Yavas", "Yavaş"))
        model.cancel()
        assertNull(model.folder)
    }

    @Test
    fun anUnknownServerIsUnreachableAndAnEmptyFolderIsEmpty() {
        val model = model(FakeNetworkSource(network))
        model.open(DiscoveredServer("smb://yok/", "Yok"))
        assertEquals(FolderStatus.Unreachable, model.folder!!.status)
        model.open(DiscoveredServer("smb://nas/Bos", "Boş"))
        assertEquals(FolderStatus.Listed, model.folder!!.status)
        assertTrue(model.folder!!.entries.isEmpty())
    }

    @Test
    fun aFolderPlaysWithTheFoldersUnderIt() {
        val model = model(FakeNetworkSource(network))
        val actions = RecordingActions()
        model.open(DiscoveredServer("smb://nas/Filmler", "NAS"))
        model.playFolder(null, enqueue = false, actions)
        assertEquals(listOf(listOf("a.mkv", "b.mkv", "s01e01.mkv", "s01e02.mkv") to 0), actions.played)
        model.playFolder(folder("Diziler"), enqueue = true, actions)
        assertEquals(listOf(listOf("s01e01.mkv", "s01e02.mkv")), actions.queued)
        assertEquals(NetworkNotice.Queued(2), notices.last())
        assertNull(model.gathering)
        model.playFolder(NetworkEntry("smb://nas/Bos", "Boş", EntryKind.Folder), enqueue = false, actions)
        assertEquals(NetworkNotice.NothingToPlay("Boş"), notices.last())
        model.addToLibrary(folder("Diziler"), actions)
        assertEquals(listOf("smb://nas/Filmler/Diziler"), actions.scanned)
    }

    @Test
    fun serversAreSavedRemovedAndBroughtBack() {
        val store = FakeServerStore()
        val model = model(FakeNetworkSource(network), store)
        scope.launch { model.followSaved() }
        var saved: Boolean? = null
        model.save(0, "NAS", "smb://nas/Filmler", SignInChange.Use(Credentials("ali", "pw"), remember = true)) { saved = it }
        assertEquals(true, saved)
        assertEquals("a new server opens", "smb://nas/Filmler", model.folder!!.url)
        model.save(0, "Yine", "smb://nas/Filmler", SignInChange.Forget) { saved = it }
        assertEquals(false, saved)
        assertEquals(NetworkNotice.AddressTaken, notices.last())
        val server = store.rows.value.single()
        var restore: (() -> Unit)? = null
        model.remove(server) { restore = it }
        assertNull("its folder closes", model.folder)
        assertTrue(store.rows.value.isEmpty())
        restore!!()
        assertEquals(Credentials("ali", "pw"), store.signIns[store.rows.value.single().id])
        assertEquals(listOf("NAS"), model.saved!!.map { it.name })
    }

    @Test
    fun theNetworkIsSearched() {
        val source = FakeNetworkSource()
        val model = model(source)
        model.discover()
        assertTrue(model.discovery.searching)
        source.discovered.tryEmit(DiscoveryEvent.Found(DiscoveredServer("smb://NAS/", "NAS")))
        source.discovered.tryEmit(DiscoveryEvent.Found(DiscoveredServer("upnp://http://10.0.0.5/d.xml", "Bravia")))
        source.discovered.tryEmit(DiscoveryEvent.Lost("smb://NAS/"))
        assertEquals(listOf("Bravia"), model.discovery.servers.map { it.name })
        model.stopDiscovery()
        assertTrue(model.discovery.servers.isEmpty())
    }

    @Test
    fun aFolderPlaysWithTheSignInOfItsServerOnly() {
        val source = FakeNetworkSource(network)
        source.held += "smb://nas/Filmler/Diziler"
        val store = FakeServerStore()
        val model = model(source, store)
        runBlocking { store.save(0, "NAS", "smb://nas/Filmler", SignInChange.Use(Credentials("ali", "nas"), remember = true)) }
        model.open(store.rows.value.single())
        model.playFolder(null, enqueue = false, RecordingActions())
        assertEquals("smb://nas/Filmler" to Credentials("ali", "nas"), source.reads.first())
        assertEquals(Credentials("ali", "nas"), source.reads.last().second)
        // another server opened: the gathering stops, nothing is read with the first one's sign-in
        SessionSignIns.keep("smb://other/", Credentials("veli", "other"))
        model.open(DiscoveredServer("smb://other/", "Other"))
        assertNull(model.gathering)
        assertTrue(source.reads.filter { it.first.startsWith("smb://other") }.all { it.second == Credentials("veli", "other") })
        assertTrue(source.reads.filter { it.first.startsWith("smb://nas") }.all { it.second == Credentials("ali", "nas") })
    }

    @Test
    fun aKeystoreThatDoesNotAnswerKeepsThePassword() {
        val store = FakeServerStore()
        val source = FakeNetworkSource(network)
        val model = model(source, store)
        runBlocking { store.save(0, "NAS", "smb://nas/Filmler", SignInChange.Use(Credentials("ali", "pw"), remember = true)) }
        val id = store.rows.value.single().id
        store.unavailable += id
        model.open(store.rows.value.single())
        assertEquals(FolderStatus.SignIn(SignInReason.Unavailable), model.folder!!.status)
        assertEquals(Credentials("ali", "pw"), store.signIns[id])
        store.unavailable.clear()
        model.reopen()
        assertEquals(FolderStatus.Listed, model.folder!!.status)
        // a sign-in the Keystore cannot seal serves this session, and says so
        store.sealFails = true
        model.save(0, "Arşiv", "smb://arsiv/", SignInChange.Use(Credentials("a", "b"), remember = true))
        assertEquals(NetworkNotice.SignInNotKept, notices.last())
    }

    @Test
    fun aRememberedSignInForASavedAddressJoinsItsRow() {
        val store = FakeServerStore()
        val model = model(FakeNetworkSource(network, signIns = mapOf("smb://nas/" to Credentials("ali", "pw"))), store)
        scope.launch { model.followSaved() }
        runBlocking { store.save(0, "NAS", "smb://nas/Filmler", SignInChange.Forget) }
        model.open(DiscoveredServer("smb://nas/Filmler", "NAS"))
        model.signIn(Credentials("ali", "pw"), remember = true)
        assertEquals(1, store.rows.value.size)
        assertEquals(Credentials("ali", "pw"), store.signIns[store.rows.value.single().id])
    }

    @Test
    fun aQuestionWaitsOnTheFoldersPage() {
        val replies = mutableListOf<String>()
        val prompt = LibVlcPrompt(PromptKind.Question, "Güvensiz site", "nas", action1 = "Kalıcı kabul et", reply = RecordingReply(replies))
        val source = object : NetworkSource by FakeNetworkSource(network) {
            override fun browse(url: String, credentials: Credentials?): Flow<BrowseEvent> = flow {
                emit(BrowseEvent.Asked(prompt))
                awaitCancellation()
            }
        }
        val model = model(source)
        model.open(DiscoveredServer("smb://nas/Filmler", "NAS"))
        assertEquals(FolderStatus.Question(prompt, 1_000), model.folder!!.status)
        model.answer(prompt, 1)
        assertEquals(listOf("action 1"), replies)
        assertEquals(FolderStatus.Connecting(1_000), model.folder!!.status)
    }

    @Test
    fun aFolderThatAsksIsPassedOverWhileGathering() {
        val source = FakeNetworkSource(network)
        source.questions["smb://nas/Filmler/Diziler"] = LibVlcPrompt(PromptKind.Question, "Insecure site", "nas", reply = RecordingReply(mutableListOf()))
        val model = model(source)
        val actions = RecordingActions()
        model.open(DiscoveredServer("smb://nas/Filmler", "NAS"))
        model.playFolder(null, enqueue = false, actions)
        assertEquals(listOf(listOf("a.mkv", "b.mkv") to 0), actions.played)
        assertTrue(NetworkNotice.FoldersSkipped(1) in notices)
        assertNull(model.gathering)
    }
}
