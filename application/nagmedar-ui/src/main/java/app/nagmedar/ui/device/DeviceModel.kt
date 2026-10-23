package app.nagmedar.ui.device

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.text.Collator
import java.util.Locale

/** What a folder's list is ordered by; folders always come first. */
enum class DeviceSortKey { Name, Size, Modified }

@Immutable
data class DeviceSort(val key: DeviceSortKey = DeviceSortKey.Name, val ascending: Boolean = true)

/** One folder on the way to the open one, the place browsed from first. */
@Immutable
data class DeviceCrumb(val name: String, val location: DeviceLocation)

/** What the open folder shows. */
sealed interface FolderListing {
    data object Loading : FolderListing

    data class Listed(val entries: List<DeviceEntry>) : FolderListing

    /** The folder cannot be read: the drive went away, or the app may not read it. */
    data object Unreadable : FolderListing
}

/**
 * Sources › This device: the places of the device, the open folder (its way back as a trail of
 * folders) and its list, ordered and with or without hidden files. Reading runs in [scope]; a
 * folder opened while another is read wins.
 */
@Stable
class DeviceBrowserModel(private val source: DeviceSource, private val scope: CoroutineScope, private val locale: Locale) {
    var roots by mutableStateOf<List<DeviceRoot>?>(null)
        private set
    var trail by mutableStateOf<List<DeviceCrumb>>(emptyList())
        private set
    var listing by mutableStateOf<FolderListing>(FolderListing.Loading)
        private set
    var sort by mutableStateOf(DeviceSort())
    var showHidden by mutableStateOf(false)

    /** "Dosyada göster": the file of the open folder to select and bring into sight; null once it is, or another folder opens. */
    var pointed by mutableStateOf<DeviceLocation?>(null)
        private set

    private var job: Job? = null

    /** The open folder; null while the places show. */
    val current: DeviceCrumb? get() = trail.lastOrNull()

    fun loadRoots() {
        scope.launch { roots = runCatching { source.roots() }.getOrDefault(emptyList()) }
    }

    fun openRoot(root: DeviceRoot, name: String) = show(listOf(DeviceCrumb(name, root.location)))

    fun open(entry: DeviceEntry) {
        if (entry.folder) show(trail + DeviceCrumb(entry.name, entry.location))
    }

    /** Back to the folder at [index] of the trail. */
    fun goTo(index: Int) {
        if (index in trail.indices && index != trail.lastIndex) show(trail.subList(0, index + 1))
    }

    /** One folder up; false at the place browsed from. */
    fun up(): Boolean {
        if (trail.size <= 1) return false
        show(trail.dropLast(1))
        return true
    }

    /** Back to the places. */
    fun close() {
        job?.cancel()
        trail = emptyList()
        listing = FolderListing.Loading
        pointed = null
    }

    fun refresh() {
        if (trail.isEmpty()) loadRoots() else show(trail, pointed)
    }

    /** A library menu's "Dosyada göster": [request]'s folder opens, its file pointed at. */
    fun reveal(request: DeviceReveal) = show(request.trail, request.file)

    /** The file pointed at is selected and in sight: a new order or a refresh leaves the list where it is. */
    fun pointedShown() {
        pointed = null
    }

    /** The open folder's entries as shown: hidden ones only when asked for, folders first, then by [sort]. */
    fun visible(entries: List<DeviceEntry>): List<DeviceEntry> {
        val collator = Collator.getInstance(locale).apply { strength = Collator.PRIMARY }
        val byName = Comparator<DeviceEntry> { a, b -> collator.compare(a.name, b.name) }
        val order = when (sort.key) {
            DeviceSortKey.Name -> byName
            DeviceSortKey.Size -> compareBy<DeviceEntry> { it.sizeBytes }.then(byName)
            DeviceSortKey.Modified -> compareBy<DeviceEntry> { it.modifiedMs }.then(byName)
        }
        val directed = if (sort.ascending) order else order.reversed()
        return entries.filter { showHidden || !it.hidden }.sortedWith(compareBy<DeviceEntry> { !it.folder }.then(directed))
    }

    /** The open folder's files that play, in the order shown. */
    fun playable(): List<DeviceEntry> = (listing as? FolderListing.Listed)?.let { visible(it.entries) }.orEmpty().filter { it.kind.playable }

    /** The files of [folder] that play, read now (a folder's "Oynat" and "Sıraya ekle"). */
    suspend fun playableIn(folder: DeviceEntry): List<DeviceEntry> =
        source.list(folder.location)?.let(::visible).orEmpty().filter { it.kind.playable }

    private fun show(path: List<DeviceCrumb>, pointAt: DeviceLocation? = null) {
        val target = path.lastOrNull() ?: return
        job?.cancel()
        trail = path
        pointed = pointAt
        listing = FolderListing.Loading
        job = scope.launch {
            val entries = source.list(target.location)
            listing = if (entries == null) FolderListing.Unreadable else FolderListing.Listed(entries)
        }
    }
}
