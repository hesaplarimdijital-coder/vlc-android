package app.nagmedar.ui.music

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.nagmedar.ui.R
import app.nagmedar.ui.components.EditableTitle
import app.nagmedar.ui.components.NagIconButton
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.covers.CoverTrack
import app.nagmedar.ui.theme.NagTheme

/** The width of the header from which the buttons sit beside the name; narrower, under it. */
private val BESIDE_FROM: Dp = 560.dp

/**
 * S-1601: the head of an open list: its cover, its name (changed where it stands, [editing]), the
 * lines under it ([lines]: how many tracks and how long, for a smart list also its rules), and the
 * buttons: [actions] hears whether they go under the name (a narrow pane, a phone). With [onBack]
 * (a pane that shows the lists or one list, not both) a button leads back to the lists.
 */
@Composable
internal fun ListDetailHeader(
    name: String,
    onRename: (String) -> Unit,
    editing: Boolean,
    onEditing: (Boolean) -> Unit,
    covers: List<CoverTrack>,
    seed: String,
    smart: Boolean,
    lines: List<String>,
    onBack: (() -> Unit)?,
    actions: @Composable RowScope.(stacked: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = NagTheme.dimens
    val colors = NagTheme.colors
    BoxWithConstraints(modifier.fillMaxWidth().padding(horizontal = dimens.pagePadding - 4.dp, vertical = dimens.gapS).testTag("list-header")) {
        val stacked = maxWidth < BESIDE_FROM
        val coverSize = if (stacked) 72.dp else 104.dp
        Column(verticalArrangement = Arrangement.spacedBy(dimens.gapS)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dimens.gapS)) {
                if (onBack != null) {
                    NagIconButton(NagIcons.ChevronLeft, stringResource(R.string.nag_pl_back), onBack, modifier = Modifier.testTag("list-back"))
                }
                PlaylistCover(covers, seed, smart, Modifier.size(coverSize))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    EditableTitle(
                        name,
                        onCommit = onRename,
                        editing = editing,
                        onEditingChange = onEditing,
                        renameLabel = stringResource(R.string.nag_pl_rename_action),
                        modifier = Modifier.testTag("list-title"),
                    )
                    lines.forEach { line ->
                        Text(
                            line,
                            style = NagTheme.type.labelSmall,
                            color = colors.textSecondary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 6.dp),
                        )
                    }
                }
                if (!stacked) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dimens.gapS)) { actions(false) }
            }
            if (stacked) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dimens.gapS)) { actions(true) }
        }
    }
}
