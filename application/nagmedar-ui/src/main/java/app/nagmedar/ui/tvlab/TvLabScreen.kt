package app.nagmedar.ui.tvlab

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
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
import app.nagmedar.lab.MediaTech
import app.nagmedar.lab.RemuxPlan
import app.nagmedar.lab.StreamRef
import app.nagmedar.lab.StreamType
import app.nagmedar.lab.displayName
import app.nagmedar.ui.NagmedarActivity
import app.nagmedar.ui.R
import app.nagmedar.ui.lab.CreateFile
import app.nagmedar.ui.lab.ReportDialog
import app.nagmedar.ui.library.currentLocale
import app.nagmedar.ui.shell.Destination
import app.nagmedar.ui.tvlab.remux.RemuxJobView
import app.nagmedar.ui.tvlab.remux.RemuxQueue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videolan.tools.NAGMEDAR_EXTRA_DESTINATION
import java.io.File

/**
 * The TV Lab screen (S-1201 … S-1204) over the app's one [TvLabController]: the file chosen with
 * the system's picker (kept readable, so that a long analysis survives the screen), the profile
 * editor, the report, the chart saved as PNG or CSV, profiles imported, exported and shared;
 * and the film queued for a remux into a file the picker names (S-1206), its jobs watched.
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
    val jobs by produceState(emptyList<RemuxJobView>(), inputUri, queue) {
        value = emptyList()
        if (inputUri != null) queue.observe(inputUri).collect { rows -> value = rows.map(RemuxJobView::of) }
    }
    val view = controller.view().copy(jobs = jobs)
    val palette = chartPalette()
    val measurer = rememberTextMeasurer()
    val names = remember(res, locale) { LocalizedStreamNames(res, locale) }

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

    val remuxLauncher = rememberLauncherForActivityResult(CreateFile()) { uri ->
        val file = controller.file
        val tech = controller.tech
        if (uri == null || file == null || tech == null) return@rememberLauncherForActivityResult
        // the output stays writable for the queue's worker, which may run after this screen is gone
        runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
        val report = checkOf(view, names)
        val convert = report?.findings?.any { it.kind == FindingKind.DolbyVisionDualLayer } == true
        val plan = RemuxPlan.default(tech, convert) { ref -> streamLabel(tech, ref, names) }
        scope.launch {
            val id = queue.enqueue(Uri.parse(file.uri), file.name, file.sizeBytes, uri, context.displayName(uri), plan)
            notice = res.getString(
                when (id) {
                    RemuxQueue.REFUSED_SAME_FILE -> R.string.nag_tvlab_remux_same_file
                    RemuxQueue.REFUSED_NOT_EMPTY -> R.string.nag_tvlab_remux_not_empty
                    else -> R.string.nag_tvlab_remux_queued_notice
                },
            )
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
                remuxLauncher.launch(MKV_MIME to context.getString(R.string.nag_tvlab_remux_file, base))
            }

            override fun cancelRemux(id: Long) {
                scope.launch { queue.cancel(id) }
            }

            override fun removeRemux(id: Long) {
                scope.launch { queue.remove(id) }
            }
        }
    }

    TvLabContent(view, actions, modifier, notice = notice, onDismissNotice = { notice = null })

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

/** How the interface names the stream [ref] of [tech], for the plan's labels. */
private fun streamLabel(tech: MediaTech, ref: StreamRef, names: LocalizedStreamNames): String = when (ref.type) {
    StreamType.Video -> tech.video?.let(names::video).orEmpty()
    StreamType.Audio -> tech.audio.getOrNull(ref.ordinal)?.let(names::audio).orEmpty()
    StreamType.Subtitle -> tech.subtitles.getOrNull(ref.ordinal)?.let { names.subtitles(listOf(it)) }.orEmpty()
}

/** Writes to the document at [uri]; whether it could. */
private fun write(context: Context, uri: Uri, block: (java.io.OutputStream) -> Unit): Boolean = runCatching {
    context.contentResolver.openOutputStream(uri, "wt")?.use(block) ?: return false
}.isSuccess

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
