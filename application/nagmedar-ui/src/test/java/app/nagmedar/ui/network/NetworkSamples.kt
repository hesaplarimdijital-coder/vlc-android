package app.nagmedar.ui.network

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import app.nagmedar.ui.library.currentLocale

/** S-206: a home network for the network browser's tests and screenshots. */
object NetworkSamples {
    const val NAS = "smb://nas.local/Filmler"
    const val FTP = "ftp://arsiv.example.org/"
    const val DAV = "davs://bulut.example.org/remote.php/dav"

    private fun entry(base: String, name: String, folder: Boolean = false) =
        NetworkEntry("$base/${ServerAddress.encodeSegment(name)}", name, EntryKind.of(name, folder))

    val films = listOf(
        entry(NAS, "Belgeseller", folder = true),
        entry(NAS, "Diziler", folder = true),
        entry(NAS, "Yerli", folder = true),
        entry(NAS, "Kuzey Işığı (2023).mkv"),
        entry(NAS, "Bozkırda Bir Gün.mp4"),
        entry(NAS, "Çay Bahçesi.mkv"),
        entry(NAS, "Ağustos Böceği.mkv"),
        entry(NAS, "Ağustos Böceği.tr.srt"),
        entry(NAS, "İstanbul Hatırası.mkv"),
        entry(NAS, "Konser — Neva Kâr.flac"),
        entry(NAS, "Yol Listesi.m3u8"),
    )

    val series = (1..8).map { entry("$NAS/Diziler", "Sahil S01E0$it.mkv") }

    /** What each address holds; the FTP archive asks for a sign-in, the empty share holds nothing. */
    fun source() = FakeNetworkSource(
        mapOf(
            NAS to (films to BrowseResult.Done),
            "$NAS/Diziler" to (series to BrowseResult.Done),
            "smb://nas.local/Bos" to (emptyList<NetworkEntry>() to BrowseResult.Done),
            FTP to (films.take(3) to BrowseResult.Done),
        ),
        signIns = mapOf(FTP to Credentials("arsiv", "doğru")),
    )

    fun store() = FakeServerStore().apply {
        rows.value = listOf(
            SavedServer(1, "Arşiv (FTP)", FTP, signedIn = true),
            SavedServer(2, "Bulut", DAV, signedIn = true),
            SavedServer(3, "Salondaki NAS", NAS, signedIn = true),
        )
        signIns[1] = Credentials("arsiv", "eski")
        signIns[2] = Credentials("ben", "x")
        signIns[3] = Credentials("ali", "x")
    }

    val found = listOf(
        DiscoveredServer("smb://MEDYA-PC/", "MEDYA-PC"),
        DiscoveredServer("upnp://http://192.168.1.10:8200/rootDesc.xml", "Salon TV · DLNA"),
        DiscoveredServer("smb://nas.local/Bos", "Boş paylaşım"),
    )
}

/**
 * The network browser on the samples: [setUp] puts the model in the state to show (a folder open,
 * a sign-in asked for…) once composed; [clock] is the slow network's.
 */
@Composable
fun SampleNetworkBrowser(
    lan: LanState = LanState.Available,
    source: FakeNetworkSource = remember { NetworkSamples.source() },
    store: FakeServerStore = remember { NetworkSamples.store() },
    actions: NetworkActions = remember { RecordingActions() },
    clock: () -> Long = { 1_000L },
    /** Whether the network is still being searched. */
    searching: Boolean = false,
    draft: ServerDraft? = null,
    setUp: (NetworkModel) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val locale = currentLocale()
    val model = remember { NetworkModel(source, store, scope, locale, now = { 1_000L }, searchMs = if (searching) 600_000L else 0L) }
    LaunchedEffect(model) { model.followSaved() }
    LaunchedEffect(model) { setUp(model) }
    NetworkBrowser(model, actions, lan, clock = clock, initialDraft = draft)
}

/** The samples' servers found on the network. */
fun NetworkModel.found(source: FakeNetworkSource) {
    NetworkSamples.found.forEach { source.discovered.tryEmit(DiscoveryEvent.Found(it)) }
    discover()
}
