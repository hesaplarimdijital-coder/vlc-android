package app.nagmedar.ui.player.subtitles.tools

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import app.nagmedar.ui.R
import app.nagmedar.ui.player.subtitles.SavedSubtitle
import app.nagmedar.ui.player.subtitles.SubtitleEncodings
import app.nagmedar.ui.player.subtitles.displayName
import app.nagmedar.ui.player.subtitles.extract.SubtitleExtractController
import app.nagmedar.ui.player.subtitles.extract.WorkExtractService
import app.nagmedar.ui.player.subtitles.saveBeside as saveNextToFile
import app.nagmedar.ui.player.subtitles.toolsFiles
import app.nagmedar.ui.player.subtitles.translate.SubtitleTranslateController
import app.nagmedar.ui.player.subtitles.translate.WorkTranslationService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * The tools' files as the system's documents (S-708): read whole (with the app's character set
 * detection above), written as new files only. Nothing here opens a file for writing that exists.
 */
internal class DocumentSubtitleToolsIo(private val context: Context) : SubtitleToolsIo {

    override suspend fun read(file: ToolsFile): ByteArray? = withContext(Dispatchers.IO) {
        SubtitleEncodings.bytes(context, Uri.parse(file.uri))
    }

    override suspend fun saveBeside(file: ToolsFile, name: String, bytes: ByteArray): SavedSubtitle? = withContext(Dispatchers.IO) {
        val uri = saveNextToFile(context, Uri.parse(file.uri), name, bytes) ?: return@withContext null
        SavedSubtitle(uri.toString(), displayName(context.contentResolver, uri) ?: name, beside = true)
    }

    override suspend fun write(uri: String, source: ToolsFile, bytes: ByteArray): WriteOutcome = withContext(Dispatchers.IO) {
        val target = Uri.parse(uri)
        val resolver = context.contentResolver
        // the original is never written into, whatever the picker confirmed; nor is any file that is not new and empty
        if (DocumentIdentity.same(target, Uri.parse(source.uri))) return@withContext WriteOutcome.SameFile
        val size = DocumentIdentity.sizeOf(resolver, target)
        if (size != null && size > 0L) return@withContext WriteOutcome.SameFile
        val written = runCatching { resolver.openOutputStream(target, "wt")?.use { it.write(bytes) } != null }.getOrDefault(false)
        if (written) return@withContext WriteOutcome.Written
        // what the picker made for this write (it was empty) does not stay, empty or half written;
        // a document whose size was not told is left alone rather than removed
        if (size == 0L) runCatching { DocumentsContract.deleteDocument(resolver, target) }.recoverCatching { resolver.delete(target, null, null) }
        WriteOutcome.Failed
    }
}

/** A film from the library whose subtitles the tools are asked for; [extract], to write an embedded one into a file. */
class SubtitleToolsRequest(val film: String, val name: String?, val extract: Boolean = false)

/**
 * "Subtitle tools…" in a film's menu in the library (S-708), and "Extract embedded subtitles…":
 * the request is made here and answered by [SubtitleToolsHost], which the app's window shows
 * above its screens. The library lists no subtitle files, so the film's folder is read for them
 * when the window opens, and the film's own tracks are listed beside them.
 */
object SubtitleToolsLauncher {
    // process-wide state: made in the global snapshot by ProcessState.warmUp, before any window composes
    var request by mutableStateOf<SubtitleToolsRequest?>(null)
        private set

    fun open(film: Uri, name: String? = null, extract: Boolean = false) {
        request = SubtitleToolsRequest(film.toString(), name, extract)
    }

    fun clear() {
        request = null
    }
}

/** The library's window for the tools: shown while a film's menu has asked for them. */
@Composable
fun SubtitleToolsHost() {
    val request = SubtitleToolsLauncher.request ?: return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val controller = remember(context, scope) {
        val io = DocumentSubtitleToolsIo(context.applicationContext)
        SubtitleToolsController(
            io,
            scope,
            context.getString(R.string.nag_subtools_edited_word),
            // S-710: the offline translation, into a new file beside the original
            translation = SubtitleTranslateController(WorkTranslationService(context.applicationContext), scope, Locale.getDefault().language),
            // the film's embedded subtitles, written into new files beside it
            extraction = SubtitleExtractController(WorkExtractService(context.applicationContext), io, scope),
        )
    }
    LaunchedEffect(request) {
        val film = Uri.parse(request.film)
        val app = context.applicationContext
        val (files, name) = withContext(Dispatchers.IO) {
            toolsFiles(app, film, emptyList()) to (request.name?.takeIf { it.isNotBlank() } ?: displayName(app.contentResolver, film) ?: film.lastPathSegment.orEmpty())
        }
        controller.open(files, film = ToolsFile(request.film, name), extract = request.extract)
    }
    SubtitleToolsWindow(controller, onClose = {
        controller.close()
        SubtitleToolsLauncher.clear()
    })
}

/**
 * The tools' window with the system's pickers: a subtitle file of any folder to work on, and,
 * where the app may not write beside the original, "save as" for the new file. [beforePicker] is
 * told when one opens (the player does not take it for leaving).
 */
@Composable
fun SubtitleToolsWindow(controller: SubtitleToolsController, onClose: () -> Unit, beforePicker: () -> Unit = {}) {
    val context = LocalContext.current
    val opener = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            controller.choose(ToolsFile(uri.toString(), displayName(context.contentResolver, uri) ?: "subtitles.srt"))
        }
    }
    val creator = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ANY_FILE)) { uri ->
        controller.savedTo(uri?.toString())
    }
    val pick = controller.state?.pickName
    LaunchedEffect(pick) {
        if (pick != null) {
            beforePicker()
            if (runCatching { creator.launch(pick) }.isFailure) controller.savedTo(null)
        }
    }
    // "save elsewhere" of an extracted subtitle file
    val extraction = controller.extraction
    val copier = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ANY_FILE)) { uri ->
        extraction?.savedElsewhere(uri?.toString())
    }
    val copyName = extraction?.state?.pickName
    LaunchedEffect(copyName) {
        if (copyName != null) {
            beforePicker()
            if (runCatching { copier.launch(copyName) }.isFailure) extraction?.savedElsewhere(null)
        }
    }
    // the line playing is marked in the editor as the film goes
    val following = controller.state != null && controller.playback != null
    LaunchedEffect(following) {
        while (following) {
            controller.follow()
            delay(FOLLOW_MS)
        }
    }
    if (controller.state == null) return
    SubtitleToolsDialog(
        controller,
        onOpenFile = {
            beforePicker()
            runCatching { opener.launch(OPEN_TYPES) }
        },
        onDismiss = onClose,
    )
}

/** Any file: senders call subtitle files many things. */
private val OPEN_TYPES = arrayOf("*/*")

private const val FOLLOW_MS = 300L

private const val ANY_FILE = "application/octet-stream"
