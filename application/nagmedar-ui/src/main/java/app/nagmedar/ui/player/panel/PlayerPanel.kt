package app.nagmedar.ui.player.panel

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.nagmedar.ui.R
import app.nagmedar.ui.components.NagIconButton
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.components.collectState
import app.nagmedar.ui.components.stateBackground
import app.nagmedar.ui.player.keepsWheel
import app.nagmedar.ui.theme.NagTheme

/** The player panel's tabs, in the mockups' order; a tab shows once its story fills it (S-603: effects). */
enum class PanelTab(@StringRes val label: Int) {
    Subtitles(R.string.nag_panel_subtitles),
    Sync(R.string.nag_panel_sync),
    Effects(R.string.nag_panel_effects),
    Info(R.string.nag_panel_info),

    /** S-608: the film's bookmarks. */
    Bookmarks(R.string.nag_panel_bookmarks),
}

/**
 * Where the panel docks (S-603): beside the video in a wide window, under it in a narrow one,
 * where the video keeps its width ("Panel: side → bottom (narrow window)" in the mockups).
 */
enum class PanelDock {
    Side,
    Bottom,
    ;

    companion object {
        val SideWidth: Dp = 360.dp

        fun of(width: Dp, height: Dp): PanelDock = if (width >= 840.dp || (width > height && width >= 600.dp)) Side else Bottom

        /** Under the video, the panel takes a little under half the window's height. */
        fun bottomHeight(height: Dp): Dp = (height * 0.45f).coerceIn(260.dp, 440.dp)

        /** Under the video and wide enough, the panel's sections go side by side. */
        fun bottomColumns(width: Dp): Int = if (width >= 640.dp) 2 else 1
    }
}

/** Whether the panel is open and on which tab; picture in picture puts it away for a while. */
@Stable
class PanelState(tab: PanelTab = PanelTab.Effects, open: Boolean = false) {
    var open by mutableStateOf(open)
        private set
    var tab by mutableStateOf(tab)
        private set

    /** Picture in picture: the panel waits out of sight. */
    var suspended by mutableStateOf(false)

    /** Open and in sight: the window gives it room. */
    val shown: Boolean get() = open && !suspended

    fun show(tab: PanelTab) {
        this.tab = tab
        open = true
    }

    /** Ctrl+E and the control bar's button: opens on [tab], or closes when it shows that tab. */
    fun toggle(tab: PanelTab) = if (open && this.tab == tab) close() else show(tab)

    fun close() {
        open = false
    }
}

/**
 * The player's tool panel: its tabs along the top with a close button, the chosen tab's content
 * under them; a border on the side it docks to the video by. The wheel over it scrolls its tab,
 * and goes no further.
 */
@Composable
fun PlayerPanel(
    state: PanelState,
    tabs: List<PanelTab>,
    dock: PanelDock,
    modifier: Modifier = Modifier,
    content: @Composable (PanelTab) -> Unit,
) {
    val colors = NagTheme.colors
    val line = colors.border
    Column(
        modifier
            .background(colors.surface)
            .drawBehind {
                val stroke = 1.dp.toPx()
                if (dock == PanelDock.Side) {
                    drawLine(line, Offset(stroke / 2, 0f), Offset(stroke / 2, size.height), stroke)
                } else {
                    drawLine(line, Offset(0f, stroke / 2), Offset(size.width, stroke / 2), stroke)
                }
            }
            .keepsWheel()
            .testTag("player-panel"),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .drawBehind {
                    val stroke = 1.dp.toPx()
                    drawLine(line, Offset(0f, size.height - stroke / 2), Offset(size.width, size.height - stroke / 2), stroke)
                }
                .padding(start = 12.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // more tabs than fit scroll sideways
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                tabs.forEach { tab -> PanelTabButton(stringResource(tab.label), selected = tab == state.tab) { state.show(tab) } }
            }
            NagIconButton(NagIcons.Close, stringResource(R.string.nag_panel_close), state::close, size = 32.dp, iconSize = 14.dp)
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            content(state.tab)
        }
    }
}

@Composable
private fun PanelTabButton(label: String, selected: Boolean, onSelect: () -> Unit) {
    val colors = NagTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val shape = RoundedCornerShape(6.dp)
    Box(
        Modifier
            .heightIn(min = 30.dp)
            .background(if (selected) colors.surfaceRaised else stateBackground(state), shape)
            .pointerHoverIcon(PointerIcon.Hand)
            .hoverable(interaction)
            .selectable(selected = selected, interactionSource = interaction, indication = null, role = Role.Tab, onClick = onSelect)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = NagTheme.type.label.copy(fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal),
            color = if (selected) colors.text else colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
