package app.nagmedar.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import app.nagmedar.ui.theme.NagTheme
import app.nagmedar.ui.theme.mixSrgb
import kotlinx.coroutines.delay
import org.videolan.tools.Notices

/**
 * The window's notices ([Notices]): what the app, the playback service or the library says in a
 * word ("Added to the queue", "Copied", "Loading the playlist…"), at the bottom of the window in
 * front, as the undo bars are. Of the windows up, the one started last shows them; a plain notice
 * leaves after a few seconds (later while the pointer rests on it or it has the keyboard), a
 * progress when what it waits for is done.
 */
@Composable
fun NoticeHost(modifier: Modifier = Modifier) {
    val token = remember { Any() }
    LifecycleStartEffect(token) {
        Notices.hostStarted(token)
        onStopOrDispose { Notices.hostStopped(token) }
    }
    val host by Notices.host.collectAsState()
    val notices by Notices.shown.collectAsState()
    if (host !== token || notices.isEmpty()) return
    Column(modifier.widthIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        notices.forEach { notice ->
            key(notice.id) { NoticeBar(notice) }
        }
    }
}

@Composable
private fun NoticeBar(notice: Notices.Notice) {
    val colors = NagTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    var focused by remember { mutableStateOf(false) }
    val paused = hovered || focused
    var remaining by remember(notice.id) { mutableLongStateOf(if (notice.long) LONG_MILLIS else SHORT_MILLIS) }
    if (!notice.progress) {
        LaunchedEffect(notice.id, paused) {
            while (!paused && remaining > 0) {
                delay(TICK_MILLIS)
                remaining -= TICK_MILLIS
            }
            if (remaining <= 0) Notices.dismiss(notice.id)
        }
    }
    val shape = RoundedCornerShape(9.dp)
    Column(
        Modifier
            .shadow(10.dp, shape)
            .background(colors.inverseSurface, shape)
            .then(if (notice.error) Modifier.border(1.dp, colors.danger, shape) else Modifier)
            .hoverable(interaction)
            .onFocusChanged { focused = it.hasFocus }
            .heightIn(min = 42.dp)
            .semantics { liveRegion = if (notice.error) LiveRegionMode.Assertive else LiveRegionMode.Polite }
            .padding(start = 14.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Row(Modifier.heightIn(min = 34.dp), verticalAlignment = Alignment.CenterVertically) {
            if (notice.error) {
                Icon(NagIcons.Warning, contentDescription = null, tint = colors.onInverseSurface, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(
                notice.text,
                style = NagTheme.type.body,
                color = colors.onInverseSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false).padding(end = 8.dp),
            )
            val action = notice.action
            val actionLabel = notice.actionLabel
            if (actionLabel != null && action != null) {
                Spacer(Modifier.width(4.dp))
                NoticeButton(actionLabel) {
                    Notices.dismiss(notice.id)
                    action()
                }
            }
        }
        if (notice.progress) {
            NagProgressBar(null, notice.text, Modifier.padding(end = 8.dp, bottom = 6.dp).width(240.dp), color = colors.inverseAccent)
        }
    }
}

@Composable
private fun NoticeButton(label: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val colors = NagTheme.colors
    val shape = RoundedCornerShape(6.dp)
    val background = when {
        state.pressed -> mixSrgb(colors.onInverseSurface, colors.inverseSurface, 0.16f)
        state.hovered -> mixSrgb(colors.onInverseSurface, colors.inverseSurface, 0.08f)
        else -> Color.Transparent
    }
    Box(
        Modifier
            .heightIn(min = NagTheme.dimens.minTarget.coerceAtLeast(30.dp))
            .then(if (state.focused) Modifier.border(2.dp, colors.inverseAccent, shape) else Modifier)
            .background(background, shape)
            .nagClickable(interaction, onClick, role = Role.Button)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = NagTheme.type.label.copy(fontWeight = FontWeight.SemiBold), color = colors.inverseAccent, maxLines = 1)
    }
}

private const val SHORT_MILLIS = 4_000L
private const val LONG_MILLIS = 7_000L
private const val TICK_MILLIS = 100L
