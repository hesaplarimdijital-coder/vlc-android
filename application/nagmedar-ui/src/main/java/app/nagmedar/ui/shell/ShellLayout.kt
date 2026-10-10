package app.nagmedar.ui.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.unit.Constraints

private const val SLOT_TOP = "top"
private const val SLOT_NAV = "nav"
private const val SLOT_CONTENT = "content"
private const val SLOT_PLAYER = "player"

/**
 * Places the shell's four slots for a [NavKind]. The content slot is always the same child of
 * the same layout, so when the window is resized across width classes only the chrome around
 * it changes: the navigation back stack, scroll positions and text fields keep their state.
 *
 * - BottomBar: top bar, content, player, bottom navigation (stacked).
 * - Rail: rail on the start edge; top bar, content and player stacked beside it.
 * - Sidebar: menu bar across the top, player across the bottom, sidebar and content between.
 */
@Composable
fun ShellLayout(
    navKind: NavKind,
    topBar: @Composable () -> Unit,
    navigation: @Composable () -> Unit,
    player: @Composable () -> Unit,
    content: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Layout(
        modifier = modifier,
        content = {
            Box(Modifier.layoutId(SLOT_TOP)) { topBar() }
            Box(Modifier.layoutId(SLOT_NAV)) { navigation() }
            Box(Modifier.layoutId(SLOT_PLAYER)) { player() }
            Box(Modifier.layoutId(SLOT_CONTENT)) { content() }
        },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val topM = measurables.first { it.layoutId == SLOT_TOP }
        val navM = measurables.first { it.layoutId == SLOT_NAV }
        val playerM = measurables.first { it.layoutId == SLOT_PLAYER }
        val contentM = measurables.first { it.layoutId == SLOT_CONTENT }

        when (navKind) {
            NavKind.BottomBar -> {
                val top = topM.measure(Constraints(minWidth = width, maxWidth = width, maxHeight = height))
                val nav = navM.measure(Constraints(minWidth = width, maxWidth = width, maxHeight = height))
                val player = playerM.measure(Constraints(minWidth = width, maxWidth = width, maxHeight = height))
                val contentHeight = (height - top.height - nav.height - player.height).coerceAtLeast(0)
                val content = contentM.measure(Constraints.fixed(width, contentHeight))
                layout(width, height) {
                    top.placeRelative(0, 0)
                    content.placeRelative(0, top.height)
                    player.placeRelative(0, top.height + contentHeight)
                    nav.placeRelative(0, height - nav.height)
                }
            }

            NavKind.Rail -> {
                val nav = navM.measure(Constraints(maxWidth = width, minHeight = height, maxHeight = height))
                val rest = (width - nav.width).coerceAtLeast(0)
                val top = topM.measure(Constraints(minWidth = rest, maxWidth = rest, maxHeight = height))
                val player = playerM.measure(Constraints(minWidth = rest, maxWidth = rest, maxHeight = height))
                val contentHeight = (height - top.height - player.height).coerceAtLeast(0)
                val content = contentM.measure(Constraints.fixed(rest, contentHeight))
                layout(width, height) {
                    nav.placeRelative(0, 0)
                    top.placeRelative(nav.width, 0)
                    content.placeRelative(nav.width, top.height)
                    player.placeRelative(nav.width, height - player.height)
                }
            }

            NavKind.Sidebar -> {
                val top = topM.measure(Constraints(minWidth = width, maxWidth = width, maxHeight = height))
                val player = playerM.measure(Constraints(minWidth = width, maxWidth = width, maxHeight = height))
                val middle = (height - top.height - player.height).coerceAtLeast(0)
                val nav = navM.measure(Constraints(maxWidth = width, minHeight = middle, maxHeight = middle))
                val contentWidth = (width - nav.width).coerceAtLeast(0)
                val content = contentM.measure(Constraints.fixed(contentWidth, middle))
                layout(width, height) {
                    top.placeRelative(0, 0)
                    nav.placeRelative(0, top.height)
                    content.placeRelative(nav.width, top.height)
                    player.placeRelative(0, height - player.height)
                }
            }
        }
    }
}
