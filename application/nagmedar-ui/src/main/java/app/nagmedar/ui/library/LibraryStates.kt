package app.nagmedar.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.nagmedar.ui.R
import app.nagmedar.ui.components.LoadingState
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.components.NagProgressBar
import app.nagmedar.ui.components.StateAction
import app.nagmedar.ui.components.StatePanel
import app.nagmedar.ui.components.StateTone
import app.nagmedar.ui.theme.NagTheme
import org.videolan.vlc.MediaParsingService
import org.videolan.vlc.ScanProgress
import kotlin.math.abs

/** The medialibrary's scan as the library screens show it. */
@Immutable
data class ScanState(
    val active: Boolean = false,
    /** Looking for media folders: no count yet. */
    val discovering: Boolean = false,
    /** 0..1 while media are parsed, null while discovering. */
    val fraction: Float? = null,
    val done: Int? = null,
    val total: Int? = null,
) {
    companion object {
        val Idle = ScanState()

        private val counts = Regex("""(\d+)\s*/\s*(\d+)""")

        /** From MediaParsingService's progress: parsing in percent and a text ending in "done/scheduled". */
        fun from(progress: ScanProgress?): ScanState {
            if (progress == null) return Idle
            val match = counts.find(progress.progressText)
            return ScanState(
                active = true,
                discovering = progress.inDiscovery,
                fraction = if (progress.inDiscovery || progress.parsing <= 0f) null else (progress.parsing / 100f).coerceIn(0f, 1f),
                done = match?.groupValues?.get(1)?.toIntOrNull(),
                total = match?.groupValues?.get(2)?.toIntOrNull(),
            )
        }
    }
}

@Composable
fun rememberScanState(): ScanState {
    val progress by MediaParsingService.progress.observeAsState()
    return ScanState.from(progress)
}

/** "3 900", "12 480": digits in threes with a no-break space, as in the mockups. */
fun groupDigits(value: Int): String {
    val grouped = abs(value).toString().reversed().chunked(3).joinToString(" ").reversed()
    return if (value < 0) "-$grouped" else grouped
}

@Composable
private fun scanCounts(scan: ScanState): String? {
    val done = scan.done ?: return null
    val total = scan.total ?: return null
    return stringResource(R.string.nag_state_progress, groupDigits(done), groupDigits(total))
}

@Composable
private fun scanTitle(scan: ScanState): String = stringResource(
    when {
        scan.discovering -> R.string.nag_state_discovering
        scan.active -> R.string.nag_state_scanning
        else -> R.string.nag_library_loading
    },
)

/**
 * What a library screen shows (S-308): placeholders while it loads or while a scan has found
 * nothing yet, the empty state with its next step, or [content], with a scan strip on top
 * while the medialibrary is still scanning.
 */
@Composable
fun LibraryContent(
    loading: Boolean,
    empty: Boolean,
    scan: ScanState,
    emptyTitle: String,
    emptyDescription: String,
    onAddFolder: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    when {
        loading || (empty && scan.active) -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            LoadingState(
                title = scanTitle(scan),
                progress = scan.fraction,
                progressText = scanCounts(scan),
                detail = if (scan.active) stringResource(R.string.nag_state_scan_detail) else null,
            )
        }
        empty -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            StatePanel(
                tone = StateTone.Neutral,
                icon = NagIcons.FolderAdd,
                title = emptyTitle,
                description = emptyDescription,
                primary = StateAction(stringResource(R.string.nag_state_add_folder), onAddFolder),
            )
        }
        else -> Column(modifier.fillMaxSize()) {
            if (scan.active) ScanStrip(scan)
            content()
        }
    }
}

/** One line above a filled library while the scan goes on: the found media can already be played. */
@Composable
fun ScanStrip(scan: ScanState, modifier: Modifier = Modifier) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val title = scanTitle(scan)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.pagePadding, vertical = dimens.gapXs)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        Text(title, style = NagTheme.type.labelSmall, color = colors.textSecondary)
        scanCounts(scan)?.let {
            Spacer(Modifier.width(dimens.gapS))
            Text(it, style = NagTheme.type.mono, color = colors.textTertiary)
        }
        Spacer(Modifier.width(dimens.gapM))
        Box(Modifier.width(160.dp)) { NagProgressBar(scan.fraction, label = title) }
    }
}
