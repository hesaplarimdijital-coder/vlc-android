package app.nagmedar.ui.tvlab.remux

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.nagmedar.lab.MediaTech
import app.nagmedar.lab.NativeLab
import app.nagmedar.lab.displayName
import app.nagmedar.ui.R
import app.nagmedar.ui.jobs.JobOutputs
import app.nagmedar.ui.jobs.OutputState
import app.nagmedar.ui.library.TechRepository
import app.nagmedar.ui.tvlab.DeviceProfile
import app.nagmedar.ui.tvlab.ProfileCheck
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/** Where a film of the batch stands: waiting for its probe, read, or why it is not for a remux. */
enum class BatchStatus { Waiting, Probing, Ready, Unreadable, NoVideo, NeedsEncode }

/** Why a film of the batch was not queued. */
enum class BatchRefusal { Exists, NotCreated, SameFile, NotEmpty, NotFile, InUse }

/** A folder of the batch: a tree the system's picker gave ("content://…/tree/…") or a library folder's path. */
@Immutable
data class BatchFolder(val location: String, val label: String) {
    val isTree: Boolean get() = location.startsWith("content://")

    companion object {
        /** A folder as the screen names it: its last part. */
        fun of(location: String): BatchFolder {
            val name = if (location.startsWith("content://")) {
                runCatching { DocumentsContract.getTreeDocumentId(Uri.parse(location)) }.getOrNull()?.substringAfterLast(':')?.trimEnd('/')?.substringAfterLast('/')
            } else {
                location.trimEnd('/').substringAfterLast('/')
            }
            return BatchFolder(location, name?.takeIf { it.isNotEmpty() } ?: location)
        }
    }
}

/** A film of the batch (S-1210): its probe, its plan (the default one, or the user's own), whether it is queued. */
@Stable
class BatchItem(val uri: Uri, val name: String, val size: Long) {
    var status by mutableStateOf(BatchStatus.Waiting)
    var tech by mutableStateOf<MediaTech?>(null)
    var draft by mutableStateOf<PlanDraft?>(null)
    var included by mutableStateOf(false)

    /** Whether its plan is the user's own: the default plan's changes leave it alone. */
    var overridden by mutableStateOf(false)

    /** What came of queuing it: null before; queued; or why not. */
    var queued by mutableStateOf(false)
    var refusal by mutableStateOf<BatchRefusal?>(null)
}

/** What a queuing of the batch did. */
@Immutable
data class BatchReport(val queued: Int, val refused: List<Pair<String, BatchRefusal>>)

/**
 * The batch (S-1210): a folder's films (a tree from the system's picker, or a library folder)
 * listed and probed one after another, each checked against the TV Lab's device profile and
 * given the default plan ([options], the planner's suggestions applied as it says; a film's
 * plan can be the user's own); then every film chosen queued into its own new file
 * "<name>.remux.mkv" in the output folder, one after another into the remux queue. A file of
 * that name there already is not touched: that film is refused, as are those the queue refuses
 * (their new, still empty file removed again). Lives as long as the app does, so that a long
 * probe goes on while other screens show.
 */
@Stable
class RemuxBatchController(context: Context, private val scope: CoroutineScope) {
    private val context = context.applicationContext

    var source by mutableStateOf<BatchFolder?>(null)
        private set
    var items by mutableStateOf(emptyList<BatchItem>())
        private set
    var scanning by mutableStateOf(false)
        private set
    var probed by mutableIntStateOf(0)
        private set

    /** The folder could not be read. */
    var unreadable by mutableStateOf(false)
        private set
    var options by mutableStateOf(PlanOptions())
        private set
    var output by mutableStateOf<BatchFolder?>(null)
        private set
    var queuing by mutableStateOf(false)
        private set
    var report by mutableStateOf<BatchReport?>(null)
        private set

    /** The device profile and the names the plans were made with: the TV Lab's, in the interface's language. */
    private var profile: DeviceProfile? = null
    private var names: ProfileCheck.StreamNames? = null
    private var job: Job? = null

    val available: Boolean get() = NativeLab.available

    /** [folder]'s films listed and probed, against [profile]; what ran before stops. */
    fun open(folder: BatchFolder, profile: DeviceProfile, names: ProfileCheck.StreamNames) {
        job?.cancel()
        this.profile = profile
        this.names = names
        source = folder
        items = emptyList()
        report = null
        unreadable = false
        probed = 0
        scanning = true
        job = scope.launch {
            try {
                val found = withContext(Dispatchers.IO) { list(folder) }
                if (found == null) {
                    unreadable = true
                    return@launch
                }
                items = found
                found.forEach { item ->
                    ensureActive()
                    item.status = BatchStatus.Probing
                    val tech = TechRepository.get(context).probeNow(item.uri.toString())
                    plan(item, tech)
                    probed += 1
                }
            } finally {
                scanning = false
            }
        }
    }

    /** Stops the probes; the films probed so far stay, the others are left unread. */
    fun stop() {
        job?.cancel()
        items.filter { it.status == BatchStatus.Probing }.forEach { it.status = BatchStatus.Waiting }
    }

    /** The default plan changed: every film whose plan is not the user's own takes it. */
    fun changeOptions(next: PlanOptions) {
        options = next
        items.forEach { item -> if (!item.overridden) item.draft?.applyOptions(next) }
    }

    /** [item]'s plan edited by the user: its own from now on. */
    fun edited(item: BatchItem) {
        item.overridden = true
    }

    /** [item]'s plan back to the default one. */
    fun resetPlan(item: BatchItem) {
        item.overridden = false
        plan(item, item.tech)
    }

    fun include(item: BatchItem, on: Boolean) {
        if (item.status == BatchStatus.Ready) item.included = on
    }

    fun includeAll(on: Boolean) = items.forEach { include(it, on) }

    fun chooseOutput(folder: BatchFolder) {
        output = folder
    }

    /** Every film chosen queued into its own new file in the output folder; [onDone] with what came of it. */
    fun queue(onDone: (BatchReport) -> Unit = {}) {
        val folder = output ?: return
        if (queuing) return
        val chosen = items.filter { it.included && it.status == BatchStatus.Ready && it.draft?.valid == true }
        if (chosen.isEmpty()) return
        queuing = true
        scope.launch {
            val queue = RemuxQueue.get(context)
            val refused = mutableListOf<Pair<String, BatchRefusal>>()
            var queued = 0
            try {
                val tree = Uri.parse(folder.location)
                val taken = withContext(Dispatchers.IO) { childNames(tree) }?.toMutableSet()
                for (item in chosen) {
                    val draft = item.draft ?: continue
                    val name = draft.outputFileName
                    val plan = draft.plan()
                    val refusal = withContext(Dispatchers.IO) {
                        if (taken == null) return@withContext BatchRefusal.NotCreated
                        if (name.lowercase(Locale.ROOT) in taken) return@withContext BatchRefusal.Exists
                        val made = create(tree, name) ?: return@withContext BatchRefusal.NotCreated
                        taken += name.lowercase(Locale.ROOT)
                        val id = queue.enqueue(item.uri, item.name, item.size, made, context.displayName(made), plan, start = false)
                        refusalOf(id)?.also {
                            // the file made a moment ago for this film, still empty: not left behind
                            if (JobOutputs(context).state(made) == OutputState.Empty) runCatching { DocumentsContract.deleteDocument(context.contentResolver, made) }
                        }
                    }
                    if (refusal == null) {
                        queued += 1
                        item.queued = true
                        item.included = false
                        item.refusal = null
                    } else {
                        item.refusal = refusal
                        refused += item.name to refusal
                    }
                }
            } finally {
                if (queued > 0) queue.start()
                val done = BatchReport(queued, refused)
                report = done
                queuing = false
                onDone(done)
            }
        }
    }

    private fun plan(item: BatchItem, tech: MediaTech?) {
        val profile = profile ?: return
        val names = names ?: return
        item.tech = tech
        if (tech == null) {
            item.status = BatchStatus.Unreadable
            item.draft = null
            item.included = false
            return
        }
        val check = ProfileCheck(names).check(tech, profile, null)
        item.status = when {
            tech.video == null -> BatchStatus.NoVideo
            !check.videoKept -> BatchStatus.NeedsEncode
            else -> BatchStatus.Ready
        }
        item.draft = PlanDraft.of(tech, check, profile, names, context.getString(R.string.nag_tvlab_remux_file, item.name.substringBeforeLast('.')), options)
        item.included = item.status == BatchStatus.Ready && !item.queued
    }

    /** The folder's films, by name; null when it cannot be read. Blocking. */
    private fun list(folder: BatchFolder): List<BatchItem>? {
        val found = mutableListOf<BatchItem>()
        if (folder.isTree) {
            val tree = Uri.parse(folder.location)
            val root = runCatching { DocumentsContract.getTreeDocumentId(tree) }.getOrNull() ?: return null
            val pending = ArrayDeque(listOf(root to 0))
            var readAny = false
            while (pending.isNotEmpty() && found.size < MAX_FILES) {
                val (id, depth) = pending.removeFirst()
                val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, id)
                val cursor = runCatching { context.contentResolver.query(children, PROJECTION, null, null, null) }.getOrNull() ?: continue
                readAny = true
                cursor.use { c ->
                    while (c.moveToNext() && found.size < MAX_FILES) {
                        val child = c.getString(0) ?: continue
                        val name = c.getString(1) ?: continue
                        if (c.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR) {
                            if (depth < MAX_DEPTH && !name.startsWith(".")) pending.add(child to depth + 1)
                        } else if (isFilm(name)) {
                            found += BatchItem(DocumentsContract.buildDocumentUriUsingTree(tree, child), name, if (c.isNull(3)) 0 else c.getLong(3))
                        }
                    }
                }
            }
            if (!readAny) return null
        } else {
            val dir = File(folder.location)
            if (!dir.isDirectory || dir.listFiles() == null) return null
            dir.walkTopDown().maxDepth(MAX_DEPTH + 1).onEnter { !it.name.startsWith(".") || it == dir }
                .filter { it.isFile && isFilm(it.name) }
                .take(MAX_FILES)
                .forEach { found += BatchItem(Uri.fromFile(it), it.name, it.length()) }
        }
        return found.sortedBy { it.name.lowercase(Locale.ROOT) }
    }

    /** The names in the output folder, lower-cased; null when it cannot be read. */
    private fun childNames(tree: Uri): Set<String>? {
        val id = runCatching { DocumentsContract.getTreeDocumentId(tree) }.getOrNull() ?: return null
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, id)
        return runCatching {
            context.contentResolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
                buildSet { while (c.moveToNext()) c.getString(0)?.let { add(it.lowercase(Locale.ROOT)) } }
            }
        }.getOrNull()
    }

    /** A new, empty document [name] in the output folder; null when the provider makes none. */
    private fun create(tree: Uri, name: String): Uri? = runCatching {
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        DocumentsContract.createDocument(context.contentResolver, parent, MKV_MIME, name)
    }.getOrNull()

    /** The queue's answer to an output it refused; null for a job queued. */
    private fun refusalOf(id: Long): BatchRefusal? = when (id) {
        RemuxQueue.REFUSED_SAME_FILE -> BatchRefusal.SameFile
        RemuxQueue.REFUSED_NOT_EMPTY -> BatchRefusal.NotEmpty
        RemuxQueue.REFUSED_NOT_FILE -> BatchRefusal.NotFile
        RemuxQueue.REFUSED_IN_USE -> BatchRefusal.InUse
        else -> null
    }

    private companion object {
        const val MAX_FILES = 500
        const val MAX_DEPTH = 4
        const val MKV_MIME = "video/x-matroska"
        val PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
        )
        val FILM_EXTENSIONS = setOf("mkv", "mp4", "m4v", "mov", "ts", "m2ts", "mts", "webm", "avi")

        /** A film the engine reads; a remux's own output ("….remux.mkv") is not one to remux again. */
        fun isFilm(name: String): Boolean {
            val lower = name.lowercase(Locale.ROOT)
            return !lower.startsWith(".") && !lower.endsWith(".remux.mkv") && lower.substringAfterLast('.', "") in FILM_EXTENSIONS
        }
    }
}

/** The app's one batch, which outlives its screen. */
object RemuxBatchSession {
    @Volatile
    private var controller: RemuxBatchController? = null

    fun get(context: Context): RemuxBatchController = controller ?: synchronized(this) {
        controller ?: RemuxBatchController(context.applicationContext, CoroutineScope(SupervisorJob() + Dispatchers.Main)).also { controller = it }
    }
}
