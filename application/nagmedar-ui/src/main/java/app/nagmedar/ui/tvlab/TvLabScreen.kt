package app.nagmedar.ui.tvlab

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.core.content.FileProvider
import app.nagmedar.lab.displayName
import app.nagmedar.ui.NagmedarActivity
import app.nagmedar.ui.R
import app.nagmedar.ui.audio.locale
import app.nagmedar.ui.lab.CreateFile
import app.nagmedar.ui.lab.ReportDialog
import app.nagmedar.ui.library.LibrarySection
import app.nagmedar.ui.library.currentLocale
import app.nagmedar.ui.onboarding.FolderGrants
import app.nagmedar.ui.onboarding.GrantHolder
import app.nagmedar.ui.rememberNagmedarPreferences
import app.nagmedar.ui.shell.Destination
import app.nagmedar.ui.tvlab.remux.BatchFolder
import app.nagmedar.ui.tvlab.remux.BatchItem
import app.nagmedar.ui.tvlab.remux.PlanDraft
import app.nagmedar.ui.tvlab.remux.RemuxBatchActions
import app.nagmedar.ui.tvlab.remux.RemuxBatchPage
import app.nagmedar.ui.tvlab.remux.RemuxBatchSession
import app.nagmedar.ui.tvlab.remux.RemuxHistoryActions
import app.nagmedar.ui.tvlab.remux.RemuxHistoryPage
import app.nagmedar.ui.tvlab.remux.RemuxJobView
import app.nagmedar.ui.tvlab.remux.RemuxNight
import app.nagmedar.ui.tvlab.remux.RemuxPlannerDialog
import app.nagmedar.ui.tvlab.remux.RemuxQueue
import app.nagmedar.ui.tvlab.remux.RemuxQueueActions
import app.nagmedar.ui.tvlab.remux.RemuxQueueControl
import app.nagmedar.ui.tvlab.remux.RemuxQueuePage
import app.nagmedar.ui.tvlab.remux.RemuxReport
import app.nagmedar.ui.tvlab.remux.activeJobs
import app.nagmedar.ui.tvlab.remux.compatibilityTitle
import app.nagmedar.ui.tvlab.remux.finishedJobs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videolan.tools.NAGMEDAR_EXTRA_DESTINATION
import java.io.File

/**
 * The TV Lab screen (S-1201 … S-1204) over the app's one [TvLabController]: the file chosen with
 * the system's picker (kept readable, so that a long analysis survives the screen), the profile
 * editor, the report, the chart saved as PNG or CSV, profiles imported, exported and shared;
 * and the film planned in the remux planner (S-1205) and queued into a file the picker names
 * (S-1206), its jobs watched.
 */
@Composable
fun TvLabScreen(controller: TvLabController, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val res = resources()
    val locale = currentLocale()
    var draft by remember { mutableStateOf<ProfileDraft?>(null) }
    var reportShown by rememberSaveable { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    var pendingChart by remember { mutableStateOf<ChartFormat?>(null) }
    var exporting by remember { mutableStateOf<DeviceProfile?>(null) }
    val queue = remember(context) { RemuxQueue.get(context) }
    val inputUri = controller.file?.uri
    // the rows' JSON (plan, stats, verdict) read off the main thread: a running job writes its row every second
    val jobs by produceState(emptyList<RemuxJobView>(), inputUri, queue) {
        value = emptyList()
        if (inputUri != null) queue.observe(inputUri).map { rows -> rows.map(RemuxJobView::of) }.flowOn(Dispatchers.Default).collect { value = it }
    }
    val allJobs by produceState(emptyList<RemuxJobView>(), queue) {
        queue.observe().map { rows -> rows.map(RemuxJobView::of) }.flowOn(Dispatchers.Default).collect { value = it }
    }
    val view = controller.view().copy(jobs = jobs)
    val palette = chartPalette()
    val measurer = rememberTextMeasurer()
    val names = remember(res, locale) { LocalizedStreamNames(res, locale) }
    // S-1210: the pages, the queue as a whole, the batch
    var tab by rememberSaveable { mutableStateOf(TvLabTab.Analysis) }
    val control by remember(context) { RemuxQueueControl.get(context).state }.collectAsState()
    val batch = remember(context) { RemuxBatchSession.get(context) }
    val prefs = rememberNagmedarPreferences()
    val libraryFolders = remember(prefs.librarySources) {
        LibrarySection.entries.filter { it.video }.flatMap { prefs.librarySources.of(it) }.filter { !it.exclude }.map { it.location }.distinct().map(BatchFolder::of)
    }
    var batchEditing by remember { mutableStateOf<Pair<BatchItem, String>?>(null) }
    // the night run's "starts at" said against a clock that moves on
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(CLOCK_MS)
        }
    }

    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            // the analysis may outlast this screen, and the drive may be measured again later
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            controller.open(uri)
        }
    }
    val chartLauncher = rememberLauncherForActivityResult(CreateFile()) { uri ->
        val format = pendingChart
        pendingChart = null
        val analysis = controller.bitrate
        if (uri == null || format == null || analysis == null) return@rememberLauncherForActivityResult
        scope.launch {
            val written = when (format) {
                ChartFormat.Png -> {
                    val model = ChartModel.of(analysis, controller.profile.limitKbps, controller.profile.source)
                    val title = listOfNotNull(controller.file?.name, controller.profile.label()).joinToString(" · ")
                    val image = renderChart(model, palette, measurer, title).asAndroidBitmap()
                    withContext(Dispatchers.IO) { write(context, uri) { image.compress(Bitmap.CompressFormat.PNG, 100, it) } }
                }
                ChartFormat.Csv -> withContext(Dispatchers.IO) { write(context, uri) { it.write(chartCsv(analysis).toByteArray()) } }
            }
            notice = res.getString(
                when {
                    !written -> R.string.nag_tvlab_save_failed
                    format == ChartFormat.Png -> R.string.nag_tvlab_chart_saved
                    else -> R.string.nag_tvlab_data_saved
                },
            )
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } }.getOrNull()
            }
            val profile = text?.takeIf { it.length < MAX_PROFILE_BYTES }?.let { DeviceProfileCodec.decode(it, builtIn = BuiltInProfiles.of(it.idOf()) != null) }
            if (profile == null) {
                notice = res.getString(R.string.nag_tvlab_not_a_profile)
            } else {
                controller.saveProfile(profile)
                notice = res.getString(R.string.nag_tvlab_profile_imported, profile.name)
            }
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(CreateFile()) { uri ->
        val profile = exporting
        exporting = null
        if (uri == null || profile == null) return@rememberLauncherForActivityResult
        scope.launch {
            val written = withContext(Dispatchers.IO) { write(context, uri) { it.write(DeviceProfileCodec.encode(profile).toByteArray()) } }
            notice = res.getString(if (written) R.string.nag_tvlab_profile_exported else R.string.nag_tvlab_save_failed)
        }
    }

    // S-1205: the plan as the planner left it; queued once the system's picker has made its new, empty file
    var planner by remember { mutableStateOf<PlanDraft?>(null) }
    var queuing by remember { mutableStateOf<PlanDraft?>(null) }
    val remuxLauncher = rememberLauncherForActivityResult(CreateFile()) { uri ->
        val draft = queuing
        queuing = null
        val file = controller.file
        if (draft == null || file == null) return@rememberLauncherForActivityResult
        if (uri == null) {
            // no place chosen: the plan comes back as it was
            planner = draft
            return@rememberLauncherForActivityResult
        }
        // the output stays writable for the queue's worker, which may run after this screen is gone
        val grant = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching { context.contentResolver.takePersistableUriPermission(uri, grant) }
        val plan = draft.plan()
        scope.launch {
            val name = withContext(Dispatchers.IO) { context.displayName(uri) }
            val id = queue.enqueue(Uri.parse(file.uri), file.name, file.sizeBytes, uri, name, plan)
            val refusal = refusalOf(id)
            // a refused output is not the queue's: its write grant goes back, unless another job of the queue (or anything
            // else) writes into it; the read grant stays, as the file may be one the app reads for something else (the source)
            if (refusal != null && id != RemuxQueue.REFUSED_IN_USE) FolderGrants.release(context, uri.toString(), FolderGrants.WRITE)
            notice = res.getString(refusal ?: R.string.nag_tvlab_remux_queued_notice)
        }
    }

    val batchFolderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        // the films are read again by the queue's worker, later: the other settings leave the folder's permission alone
        runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        FolderGrants.note(context, GrantHolder.TvLab, uri.toString())
        batch.open(BatchFolder.of(uri.toString()), controller.profile, names) { compatibilityTitle(res, it) }
    }
    val batchOutputLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        // the new files are made now and written by the worker later
        val grant = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching { context.contentResolver.takePersistableUriPermission(uri, grant) }
        FolderGrants.note(context, GrantHolder.TvLab, uri.toString())
        batch.chooseOutput(BatchFolder.of(uri.toString()))
    }
    val batchActions = remember(batch) {
        object : RemuxBatchActions {
            override fun chooseFolder() = batchFolderLauncher.launch(null)

            override fun open(folder: BatchFolder) {
                // the batch outlives this window: it keeps the window's resources, never the window itself
                val resources = context.resources
                batch.open(folder, controller.profile, LocalizedStreamNames(resources, resources.locale())) { compatibilityTitle(resources, it) }
            }

            override fun chooseOutput() = batchOutputLauncher.launch(null)

            override fun editPlan(item: BatchItem) {
                val plan = item.draft?.plan() ?: return
                batchEditing = item to plan.toJson()
            }

            override fun queue() = batch.queue()
        }
    }
    // S-208: a finished job run again into a new file, its report saved where the picker says
    var rerunning by remember { mutableStateOf<RemuxJobView?>(null) }
    var savingReport by remember { mutableStateOf<Pair<RemuxJobView, Boolean>?>(null) }
    val rerunLauncher = rememberLauncherForActivityResult(CreateFile()) { uri ->
        val job = rerunning
        rerunning = null
        val plan = job?.plan
        if (uri == null || job == null || plan == null) return@rememberLauncherForActivityResult
        val grant = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching { context.contentResolver.takePersistableUriPermission(uri, grant) }
        scope.launch {
            val name = withContext(Dispatchers.IO) { context.displayName(uri) }
            val id = queue.enqueue(Uri.parse(job.inputUri), job.inputName, job.inputSize, uri, name, plan)
            val refusal = refusalOf(id)
            if (refusal != null && id != RemuxQueue.REFUSED_IN_USE) FolderGrants.release(context, uri.toString(), FolderGrants.WRITE)
            notice = res.getString(refusal ?: R.string.nag_tvlab_remux_queued_notice)
        }
    }
    val reportLauncher = rememberLauncherForActivityResult(CreateFile()) { uri ->
        val (job, json) = savingReport ?: return@rememberLauncherForActivityResult
        savingReport = null
        if (uri == null) return@rememberLauncherForActivityResult
        val text = if (json) RemuxReport.json(job) else RemuxReport.text(res, job, locale)
        scope.launch {
            val written = withContext(Dispatchers.IO) { write(context, uri) { it.write(text.toByteArray()) } }
            notice = res.getString(if (written) R.string.nag_tvlab_history_saved else R.string.nag_tvlab_save_failed)
        }
    }
    val historyActions = remember(queue) {
        object : RemuxHistoryActions {
            override fun rerun(job: RemuxJobView) {
                rerunning = job
                rerunLauncher.launch(MKV_MIME to job.outputName)
            }

            override fun open(job: RemuxJobView) {
                val view = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(job.outputUri), MKV_MIME).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (runCatching { context.startActivity(view) }.isFailure) notice = res.getString(R.string.nag_tvlab_history_no_app)
            }

            override fun share(job: RemuxJobView) {
                val send = Intent(Intent.ACTION_SEND).setType(MKV_MIME).putExtra(Intent.EXTRA_STREAM, Uri.parse(job.outputUri)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                runCatching { context.startActivity(Intent.createChooser(send, job.outputName)) }
            }

            override fun shareReport(job: RemuxJobView, json: Boolean) {
                if (!json) {
                    val send = Intent(Intent.ACTION_SEND)
                        .setType("text/plain")
                        .putExtra(Intent.EXTRA_SUBJECT, res.getString(R.string.nag_tvlab_history_report_title))
                        .putExtra(Intent.EXTRA_TEXT, RemuxReport.text(res, job, locale))
                    runCatching { context.startActivity(Intent.createChooser(send, job.outputName)) }
                    return
                }
                scope.launch {
                    val shared = withContext(Dispatchers.IO) { shareFile(context, RemuxReport.fileName(job, JSON_EXTENSION), RemuxReport.json(job)) }
                    if (shared == null) {
                        notice = res.getString(R.string.nag_tvlab_save_failed)
                        return@launch
                    }
                    val send = Intent(Intent.ACTION_SEND).setType(JSON_MIME).putExtra(Intent.EXTRA_STREAM, shared).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    runCatching { context.startActivity(Intent.createChooser(send, job.outputName)) }
                }
            }

            override fun saveReport(job: RemuxJobView, json: Boolean) {
                savingReport = job to json
                reportLauncher.launch(
                    if (json) JSON_MIME to RemuxReport.fileName(job, JSON_EXTENSION) else "text/plain" to RemuxReport.fileName(job, context.getString(R.string.nag_tvlab_history_report_extension)),
                )
            }

            override fun forget(job: RemuxJobView) {
                scope.launch { queue.remove(job.id) }
            }
        }
    }
    val queueActions = remember(queue) {
        object : RemuxQueueActions {
            override fun cancel(id: Long) {
                scope.launch { queue.cancel(id) }
            }

            override fun remove(id: Long) {
                scope.launch { queue.remove(id) }
            }

            override fun reorder(ids: List<Long>) {
                scope.launch { queue.reorder(ids) }
            }

            override fun hold(on: Boolean) = queue.hold(on)

            override fun night(at: Long, charging: Boolean) = RemuxNight.set(context, at, charging)

            override fun cancelNight() = RemuxNight.cancel(context)
        }
    }

    val actions = remember(controller) {
        object : TvLabActions {
            override fun chooseFile() = fileLauncher.launch(arrayOf("video/*", "audio/*", "application/octet-stream"))

            override fun chooseProfile(id: String) = controller.choose(id)

            override fun editProfile() {
                draft = ProfileDraft(controller.profile, isNew = false)
            }

            override fun newProfile() {
                draft = ProfileDraft(controller.profile.copy(builtIn = false), isNew = true)
            }

            override fun importProfile() = importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))

            override fun measureDiskAgain() = controller.measureDiskAgain()

            override fun analyseBitrate() = controller.analyseBitrate()

            override fun stop() = controller.cancel()

            override fun showReport() {
                reportShown = true
            }

            override fun exportChart(format: ChartFormat) {
                val base = controller.file?.name?.substringBeforeLast('.') ?: return
                pendingChart = format
                chartLauncher.launch(format.mime to context.getString(R.string.nag_tvlab_chart_file, base) + "." + format.extension)
            }

            override fun remux() {
                val base = controller.file?.name?.substringBeforeLast('.') ?: return
                val tech = controller.tech ?: return
                val names = LocalizedStreamNames(context.resources, context.resources.locale())
                val report = checkOf(controller.view(), names)
                planner = PlanDraft.of(
                    tech,
                    report,
                    controller.profile,
                    names,
                    context.getString(R.string.nag_tvlab_remux_file, base),
                    title = { compatibilityTitle(context.resources, it) },
                )
            }

            override fun cancelRemux(id: Long) {
                scope.launch { queue.cancel(id) }
            }

            override fun removeRemux(id: Long) {
                scope.launch { queue.remove(id) }
            }
        }
    }

    TvLabContent(
        view,
        actions,
        modifier,
        notice = notice,
        onDismissNotice = { notice = null },
        tabs = TABS,
        tab = tab,
        onTab = { tab = it },
        counts = mapOf(TvLabTab.Queue to activeJobs(allJobs).size, TvLabTab.History to finishedJobs(allJobs).size),
        page = { shown ->
            when (shown) {
                TvLabTab.Batch -> RemuxBatchPage(batch, controller.profile.name, libraryFolders, batchActions)
                TvLabTab.Queue -> RemuxQueuePage(allJobs, control, now, queueActions)
                TvLabTab.History -> RemuxHistoryPage(allJobs, historyActions)
                TvLabTab.Analysis -> Unit
            }
        },
    )

    batchEditing?.let { (item, before) ->
        val editing = item.draft
        val close = {
            // the plan changed is the film's own from now on; looked at and left as it was, it stays the default one
            if (editing != null && editing.plan().toJson() != before) batch.edited(item)
            batchEditing = null
        }
        if (editing != null) {
            RemuxPlannerDialog(editing, item.name, onQueue = close, onCancel = close, confirm = stringResource(R.string.nag_tvlab_plan_done), cancellable = false)
        }
    }

    draft?.let { editing ->
        ProfileEditorDialog(
            draft = editing,
            saved = !editing.isNew && controller.saved(editing.original.id),
            onSave = {
                val id = if (editing.isNew) controller.newId(editing.name) else editing.original.id
                controller.saveProfile(editing.build(id))
                draft = null
            },
            onCancel = { draft = null },
            onShare = { shareProfile(context, editing.build(editing.original.id)) },
            onExport = {
                val profile = editing.build(editing.original.id)
                exporting = profile
                exportLauncher.launch("application/json" to FileProfileStore.fileName(profile.id))
            },
            onRemove = {
                controller.removeProfile(editing.original.id)
                draft = null
            },
        )
    }
    planner?.let { draft ->
        RemuxPlannerDialog(
            draft,
            fileName = controller.file?.name.orEmpty(),
            onQueue = {
                planner = null
                queuing = draft
                remuxLauncher.launch(MKV_MIME to draft.outputFileName)
            },
            onCancel = { planner = null },
        )
    }
    if (reportShown) {
        val commentary = stringResource(R.string.nag_tech_commentary)
        val text = remember(view, names) { tvLabReport(res, view, checkOf(view, names), locale, commentary) }
        ReportDialog(stringResource(R.string.nag_tvlab_report_title), text) { reportShown = false }
    }
}

/**
 * Analyses [uri] in the TV Lab and shows it (S-1201): from a film's menu in the library. The
 * screen is asked for as the launcher asks for one, so the window that asks shows it.
 */
fun openInTvLab(context: Context, uri: Uri) {
    TvLabSession.get(context).open(uri)
    val show = Intent(context, NagmedarActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        .putExtra(NAGMEDAR_EXTRA_DESTINATION, Destination.TvLab.route)
    runCatching { context.startActivity(show) }
}

/** The queue's answer to an output it refused, in words; null for a job queued. */
internal fun refusalOf(id: Long): Int? = when (id) {
    RemuxQueue.REFUSED_SAME_FILE -> R.string.nag_tvlab_remux_same_file
    RemuxQueue.REFUSED_NOT_EMPTY -> R.string.nag_tvlab_remux_not_empty
    RemuxQueue.REFUSED_NOT_FILE -> R.string.nag_tvlab_remux_not_file
    RemuxQueue.REFUSED_IN_USE -> R.string.nag_tvlab_remux_in_use
    else -> null
}

/** Writes to the document at [uri]; whether it could. */
private fun write(context: Context, uri: Uri, block: (java.io.OutputStream) -> Unit): Boolean = runCatching {
    context.contentResolver.openOutputStream(uri, "wt")?.use(block) ?: return false
}.isSuccess

/** [text] in a file [name] the app's file provider serves, to send; null when it cannot be written. Blocking. */
private fun shareFile(context: Context, name: String, text: String): Uri? = runCatching {
    // under the files the provider serves (res/xml/provider_paths.xml: files-path)
    val dir = File(context.filesDir, "tvlab/share").apply { mkdirs() }
    val file = File(dir, name.replace('/', '_')).apply { writeText(text) }
    FileProvider.getUriForFile(context, context.packageName + ".provider", file)
}.getOrNull()

/** The profile as a JSON file to send (S-1203), through the app's file provider. */
private fun shareProfile(context: Context, profile: DeviceProfile) {
    runCatching {
        // under the files the provider serves (res/xml/provider_paths.xml: files-path)
        val dir = File(context.filesDir, "tvlab/share").apply { mkdirs() }
        val file = File(dir, FileProfileStore.fileName(profile.id)).apply { writeText(DeviceProfileCodec.encode(profile)) }
        val uri = FileProvider.getUriForFile(context, context.packageName + ".provider", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("application/json")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, profile.label())
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, profile.label()))
    }
}

/** The "id" of a profile's JSON, read loosely: whether it is one of the app's. */
private fun String.idOf(): String = Regex("\"id\"\\s*:\\s*\"([^\"]*)\"").find(this)?.groupValues?.get(1).orEmpty()

/** No profile is that large: a bigger file is something else. */
private const val MAX_PROFILE_BYTES = 256 * 1024

/** What the remux writes (S-1206). */
private const val MKV_MIME = "video/x-matroska"

/** The TV Lab's pages, in the order of their tabs. */
private val TABS = listOf(TvLabTab.Analysis, TvLabTab.Batch, TvLabTab.Queue, TvLabTab.History)

/** A report as JSON (S-208). */
private const val JSON_MIME = "application/json"
private const val JSON_EXTENSION = "json"

/** How often the queue's page looks at the clock. */
private const val CLOCK_MS = 30_000L
