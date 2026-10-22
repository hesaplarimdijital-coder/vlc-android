package app.nagmedar.ui.player.snapshot

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nagmedar.ui.R
import app.nagmedar.ui.components.NagIconButton
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.player.panel.TextAction
import app.nagmedar.ui.theme.NagTheme
import kotlinx.coroutines.delay

/**
 * The card a screenshot leaves over the video (S-606), at its bottom left: its picture, its file
 * and folder, whether HDR was kept or tone mapped and the subtitles went in, "Open" and "Share";
 * or why it was not taken, with "Choose a folder…" when the folder is the cause. It goes after
 * six seconds, later while the pointer rests on it.
 */
@Composable
fun SnapshotCard(
    outcome: SnapshotOutcome?,
    saving: Boolean,
    onOpen: (SnapshotOutcome.Saved) -> Unit,
    onShare: (SnapshotOutcome.Saved) -> Unit,
    onChooseFolder: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    /** For tests and screenshots: the card stays. */
    stays: Boolean = false,
) {
    var hovered by remember { mutableStateOf(false) }
    val dismiss by rememberUpdatedState(onDismiss)
    LaunchedEffect(outcome, hovered, stays) {
        if (outcome == null || hovered || stays) return@LaunchedEffect
        delay(SHOWN_MS)
        dismiss()
    }
    if (outcome == null && !saving) return
    val colors = NagTheme.colors
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier
            .widthIn(max = 380.dp)
            .shadow(12.dp, shape)
            .background(colors.surfaceRaised, shape)
            .border(1.dp, colors.borderStrong, shape)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        when (awaitPointerEvent().type) {
                            PointerEventType.Enter -> hovered = true
                            PointerEventType.Exit -> hovered = false
                        }
                    }
                }
            }
            .padding(10.dp)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .testTag("snapshot-card"),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        when (outcome) {
            null -> Text(stringResource(R.string.nag_snap_saving), style = NagTheme.type.label, color = colors.textSecondary)
            is SnapshotOutcome.Saved -> Saved(outcome, onOpen, onShare)
            is SnapshotOutcome.Failed -> Failed(outcome, onChooseFolder)
        }
        if (outcome != null) {
            NagIconButton(NagIcons.Close, stringResource(R.string.nag_snap_dismiss), onDismiss, size = 24.dp, iconSize = 14.dp)
        }
    }
}

@Composable
private fun Saved(saved: SnapshotOutcome.Saved, onOpen: (SnapshotOutcome.Saved) -> Unit, onShare: (SnapshotOutcome.Saved) -> Unit) {
    val colors = NagTheme.colors
    saved.thumbnail?.let { picture ->
        // as wide as a film's poster frame, as tall as the picture asks, within reason
        val height = (112f * picture.height / picture.width.coerceAtLeast(1)).coerceIn(40f, 84f)
        Image(
            picture,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(width = 112.dp, height = height.dp)
                .clip(RoundedCornerShape(6.dp))
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                .testTag("snapshot-thumbnail"),
        )
    }
    Column(Modifier.widthIn(min = 160.dp, max = 220.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(stringResource(R.string.nag_snap_saved), style = NagTheme.type.label, color = colors.text, maxLines = 1)
        Text(
            saved.fileName,
            style = NagTheme.type.labelSmall,
            color = colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag("snapshot-file"),
        )
        Text(
            listOf(saved.folder, saved.format.label, "${saved.width}×${saved.height}").joinToString(" · "),
            style = NagTheme.type.labelSmall,
            color = colors.textTertiary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        val badges = listOfNotNull(
            when (saved.hdr) {
                HdrOutcome.None -> null
                HdrOutcome.ToneMapped -> stringResource(R.string.nag_snap_tone_mapped)
                HdrOutcome.Kept -> stringResource(R.string.nag_snap_hdr_kept)
            },
            stringResource(R.string.nag_snap_with_subtitles).takeIf { saved.subtitles },
        )
        if (badges.isNotEmpty()) {
            Row(Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                badges.forEach { Badge(it) }
            }
        }
        if (saved.uri != null) {
            // the buttons' words in line with the lines above
            Row(Modifier.padding(top = 2.dp).offset(x = (-6).dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                TextAction(stringResource(R.string.nag_snap_open), enabled = true) { onOpen(saved) }
                TextAction(stringResource(R.string.nag_snap_share), enabled = true) { onShare(saved) }
            }
        }
    }
}

/** "HDR → SDR", "with subtitles": a small label on the card's own darker ground. */
@Composable
private fun Badge(text: String) {
    Text(
        text,
        style = NagTheme.type.labelSmall,
        color = NagTheme.colors.textSecondary,
        maxLines = 1,
        modifier = Modifier
            .background(NagTheme.colors.surface, RoundedCornerShape(9.dp))
            .border(1.dp, NagTheme.colors.border, RoundedCornerShape(9.dp))
            .padding(horizontal = 8.dp, vertical = 1.dp),
    )
}

@Composable
private fun Failed(failed: SnapshotOutcome.Failed, onChooseFolder: () -> Unit) {
    val colors = NagTheme.colors
    Icon(NagIcons.Warning, contentDescription = null, tint = colors.warning, modifier = Modifier.padding(top = 1.dp).size(18.dp))
    Column(Modifier.widthIn(min = 160.dp, max = 280.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(stringResource(R.string.nag_snap_failed), style = NagTheme.type.label, color = colors.text, maxLines = 1)
        Text(
            stringResource(
                when (failed.reason) {
                    SnapshotFailure.NoVideo -> R.string.nag_snap_no_video
                    SnapshotFailure.Capture -> R.string.nag_snap_capture_failed
                    SnapshotFailure.Folder -> R.string.nag_snap_folder_failed
                    SnapshotFailure.Write -> R.string.nag_snap_write_failed
                    SnapshotFailure.Unsupported -> R.string.nag_snap_unsupported
                    SnapshotFailure.Permission -> R.string.nag_snap_permission
                },
            ),
            style = NagTheme.type.labelSmall,
            color = colors.textSecondary,
            modifier = Modifier.testTag("snapshot-reason"),
        )
        if (failed.reason == SnapshotFailure.Folder) {
            Box(Modifier.padding(top = 2.dp)) {
                TextAction(stringResource(R.string.nag_snap_choose_folder), enabled = true, onClick = onChooseFolder)
            }
        }
    }
}

/** A picture taken: the video flashes white a moment, as a camera does. */
@Composable
fun SnapshotFlash(flashes: Int, modifier: Modifier = Modifier) {
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(flashes) {
        if (flashes == 0) return@LaunchedEffect
        alpha.snapTo(FLASH_ALPHA)
        alpha.animateTo(0f, tween(FLASH_MS))
    }
    if (alpha.value > 0f) Box(modifier.fillMaxSize().background(Color.White.copy(alpha = alpha.value)))
}

private const val SHOWN_MS = 6_000L
private const val FLASH_ALPHA = 0.45f
private const val FLASH_MS = 260
