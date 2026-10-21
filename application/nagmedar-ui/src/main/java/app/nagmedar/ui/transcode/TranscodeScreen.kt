package app.nagmedar.ui.transcode

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import app.nagmedar.ui.R
import app.nagmedar.ui.components.NagButton
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.playback.rememberNowPlaying
import app.nagmedar.ui.screens.PageHeader
import app.nagmedar.ui.theme.NagTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** S-1303: the page's windows: the convert window, the HLS sheet, the profile editor. */
private sealed interface TranscodeWindow {
    data object Convert : TranscodeWindow

    data object Stream : TranscodeWindow

    data class Editor(val profile: TranscodeProfile) : TranscodeWindow
}

/**
 * S-1303: the "Dönüştür" page: the conversions' queue, the HLS stream, the profiles; "Dönüştür/Kaydet…" (Ctrl+R) and "Ağda yayınla…" (Ctrl+Shift+H) open their
 * windows here, as the player and the library's menus do with their media.
 */
@Composable
fun TranscodeScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val store = remember(context) { TranscodeProfileStore.get(context) }
    val profiles by remember(store) { store.observe() }.collectAsState(TranscodeProfiles.presets)
    val jobs by remember(context) { TranscodeQueue.observe(context).map { rows -> rows.map(TranscodeJobView::of) } }.collectAsState(emptyList())
    val hls by HlsStream.state.collectAsState()
    val nowPlaying = rememberNowPlaying()
    var window by remember { mutableStateOf<TranscodeWindow?>(null) }
    var media by remember { mutableStateOf<TranscodeMedia?>(null) }
    var selectedId by rememberSaveable { mutableStateOf(DEFAULT_PROFILE) }
    var quality by rememberSaveable { mutableStateOf(HlsQuality.P720) }
    val selected = profiles.firstOrNull { it.id == selectedId } ?: profiles.first { it.id == DEFAULT_PROFILE }
    // the media picked with the system's picker: kept readable for the queue's worker
    val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                media = withContext(Dispatchers.IO) {
                    keepReadable(context, uri)
                    TranscodeMedia.ofDocument(context, uri)
                }
            }
        }
    }
    // the new file, named with the system's picker, then the job queued
    val mime = remember { MimeHolder() }
    val createOutput = rememberLauncherForActivityResult(CreateDocumentFor(mime)) { output ->
        val input = media ?: return@rememberLauncherForActivityResult
        if (output == null) return@rememberLauncherForActivityResult
        scope.launch {
            val profile = selected
            val id = withContext(Dispatchers.IO) {
                val outputName = TranscodeMedia.ofDocument(context, output).name
                TranscodeQueue.enqueue(context, input.uri, input.name, input.size, output, outputName, profile)
            }
            val message = if (id == TranscodeQueue.REFUSED) R.string.nag_jobs_same_file else R.string.nag_conv_queued
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            window = null
        }
    }
    // a media asked for by the player, the library's menu or a key
    val pending = TranscodeRequests.pending
    LaunchedEffect(pending) {
        val request = pending ?: return@LaunchedEffect
        TranscodeRequests.pending = null
        media = request.media ?: media ?: nowPlaying.media?.let { TranscodeMedia(it.uri, it.title ?: it.uri.lastPathSegment.orEmpty()) }
        window = if (request.kind == TranscodeRequests.Kind.Stream) TranscodeWindow.Stream else TranscodeWindow.Convert
    }
    val openConvert = {
        if (media == null) media = nowPlaying.media?.let { TranscodeMedia(it.uri, it.title ?: it.uri.lastPathSegment.orEmpty()) }
        window = TranscodeWindow.Convert
    }
    val openStream = {
        if (media == null) media = nowPlaying.media?.let { TranscodeMedia(it.uri, it.title ?: it.uri.lastPathSegment.orEmpty()) }
        window = TranscodeWindow.Stream
    }
    TranscodePage(
        jobs = jobs,
        profiles = profiles,
        hls = hls,
        onConvert = openConvert,
        onStream = openStream,
        onCancel = { id -> scope.launch(Dispatchers.IO) { TranscodeQueue.cancel(context, id) } },
        onRemove = { id -> scope.launch(Dispatchers.IO) { TranscodeQueue.remove(context, id) } },
        onNewProfile = { window = TranscodeWindow.Editor(newProfile(context)) },
        onEditProfile = { window = TranscodeWindow.Editor(it) },
        onDeleteProfile = { profile -> scope.launch { store.delete(profile.id) } },
        onStopStream = { HlsStream.stop(context) },
        onCopy = { url ->
            clipboard.setText(AnnotatedString(url))
            Toast.makeText(context, R.string.nag_hls_copied, Toast.LENGTH_SHORT).show()
        },
        onDismissFailure = HlsStream::dismiss,
        modifier = modifier,
    )
    when (val shown = window) {
        TranscodeWindow.Convert -> TranscodeDialog(
            media = media,
            profiles = profiles,
            selected = selected,
            onSelect = { selectedId = it.id },
            onPickMedia = { runCatching { pickMedia.launch(arrayOf("video/*", "audio/*")) } },
            onNewProfile = { window = TranscodeWindow.Editor(newProfile(context)) },
            onStart = {
                val input = media
                if (input != null) {
                    mime.value = selected.container.mime
                    runCatching { createOutput.launch(input.baseName + "." + selected.container.extension) }
                }
            },
            onDismiss = { window = null },
        )
        TranscodeWindow.Stream -> HlsDialog(
            media = media,
            state = hls,
            quality = quality,
            onQuality = { quality = it },
            onPickMedia = { runCatching { pickMedia.launch(arrayOf("video/*", "audio/*")) } },
            onStart = { media?.let { HlsStream.start(context, it.uri, it.name, quality) } },
            onStop = { HlsStream.stop(context) },
            onCopy = { url ->
                clipboard.setText(AnnotatedString(url))
                Toast.makeText(context, R.string.nag_hls_copied, Toast.LENGTH_SHORT).show()
            },
            onDismiss = {
                HlsStream.dismiss()
                window = null
            },
        )
        is TranscodeWindow.Editor -> ProfileEditorDialog(
            initial = shown.profile,
            problemsOf = { draft -> TranscodeProfiles.validate(draft, profiles.filter { it.id != draft.id }.map { it.name }) },
            onSave = { profile ->
                scope.launch {
                    store.save(profile).onSuccess { id ->
                        selectedId = id
                        window = if (media != null) TranscodeWindow.Convert else null
                    }
                }
            },
            onDismiss = { window = null },
        )
        null -> Unit
    }
}

/**
 * S-1303: the page as it shows: the jobs; the stream and the profiles beside them on a wide
 * window, under them on a narrow one.
 */
@Composable
fun TranscodePage(
    jobs: List<TranscodeJobView>,
    profiles: List<TranscodeProfile>,
    hls: HlsState,
    onConvert: () -> Unit,
    onStream: () -> Unit,
    onCancel: (Long) -> Unit,
    onRemove: (Long) -> Unit,
    onNewProfile: () -> Unit,
    onEditProfile: (TranscodeProfile) -> Unit,
    onDeleteProfile: (TranscodeProfile) -> Unit,
    onStopStream: () -> Unit,
    onCopy: (String) -> Unit,
    onDismissFailure: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = NagTheme.dimens
    BoxWithConstraints(modifier.fillMaxSize().testTag("convert-page")) {
        val wide = maxWidth >= 900.dp
        // a phone's header has room for the title and one button: the stream's is on its card
        val narrow = maxWidth < 600.dp
        Column(Modifier.fillMaxSize()) {
            PageHeader(title = stringResource(R.string.nag_nav_convert)) {
                NagButton(stringResource(R.string.nag_conv_menu), onConvert, icon = NagIcons.Convert, primary = true, modifier = Modifier.testTag("convert-open"))
                if (!narrow) NagButton(stringResource(R.string.nag_hls_start_menu), onStream, icon = NagIcons.Streams, modifier = Modifier.testTag("stream-open"))
            }
            val padding = PaddingValues(start = dimens.pagePadding, end = dimens.pagePadding, bottom = dimens.gapXl)
            if (wide) {
                Row(Modifier.fillMaxSize().padding(padding), horizontalArrangement = Arrangement.spacedBy(dimens.gapM), verticalAlignment = Alignment.Top) {
                    LazyColumn(Modifier.weight(1.3f), verticalArrangement = Arrangement.spacedBy(dimens.gapM)) {
                        item { JobsCard(jobs, onCancel, onRemove) }
                    }
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(dimens.gapM)) {
                        item { HlsCard(hls, onStream, onStopStream, onCopy, onDismissFailure) }
                        item { ProfilesCard(profiles, onNewProfile, onEditProfile, onDeleteProfile) }
                    }
                }
            } else {
                LazyColumn(Modifier.fillMaxWidth(), contentPadding = padding, verticalArrangement = Arrangement.spacedBy(dimens.gapM)) {
                    if (hls !is HlsState.Idle) item { HlsCard(hls, onStream, onStopStream, onCopy, onDismissFailure) }
                    item { JobsCard(jobs, onCancel, onRemove) }
                    if (hls is HlsState.Idle) item { HlsCard(hls, onStream, onStopStream, onCopy, onDismissFailure) }
                    item { ProfilesCard(profiles, onNewProfile, onEditProfile, onDeleteProfile) }
                }
            }
        }
    }
}

/** The preset the convert window starts with: H.264 and AAC at 720p, which every device plays. */
private const val DEFAULT_PROFILE = -2L

/** A new profile: the 720p preset's settings under a name to change. */
private fun newProfile(context: Context): TranscodeProfile =
    TranscodeProfiles.presets.first { it.id == DEFAULT_PROFILE }.copy(id = 0, name = context.getString(R.string.nag_conv_profile_untitled), nameRes = null)

/** A picked document kept readable after the window closes: the queue's worker reads it later. */
private fun keepReadable(context: Context, uri: Uri) {
    runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
}

/** The type of the file the picker is to create, set just before it opens. */
private class MimeHolder {
    var value: String = "application/octet-stream"
}

/** The system's "create a document" with the type of the container chosen. */
private class CreateDocumentFor(private val mime: MimeHolder) : ActivityResultContracts.CreateDocument("application/octet-stream") {
    override fun createIntent(context: Context, input: String): Intent = super.createIntent(context, input).setType(mime.value)
}
