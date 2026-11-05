package app.nagmedar.ui.components

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusEventModifierNode
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.launch

/**
 * 1.0.1: what lies over the page beneath it keeps that page still: no touch, drag, mouse wheel
 * or hover goes through it, even where it draws nothing (an empty state, the gap between rows).
 * Compose hands a pointer to the topmost sibling that has a pointer handler where it lands and
 * to none under it; this one is that handler for the whole of [this], and it only listens, so
 * what is in it still gets everything.
 */
fun Modifier.blockPointerBelow(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) awaitPointerEvent(PointerEventPass.Final)
    }
}

/**
 * 1.0.1: a page over the whole window (Sıradaki on a phone, the lyrics and the visualiser over the
 * window, the tablet's player) is modal: [blockPointerBelow], and the keyboard's focus comes into
 * it when it opens and stays in it (Tab does not leave it for the page under it), so the keys
 * reach nothing beneath. With a [paneTitle] a screen reader announces it, and as it covers the
 * window it reads nothing under it. 1.0.3: while it is there the window's commands of the screen
 * under it are silent ([ScreenCommands.covered]): Ctrl+A on DeX does not select the list it hides.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun Modifier.blockInputBelow(paneTitle: String? = null): Modifier {
    val focus = remember { FocusRequester() }
    // a key press goes to what has the focus: the page under it keeps nothing of it
    LaunchedEffect(focus) { runCatching { focus.requestFocus() } }
    // nor do the window's shortcuts meant for the screen (select all, up a folder, refresh, the inspector)
    val commands = LocalScreenCommands.current
    DisposableEffect(commands) {
        val layer = Any()
        commands.cover(layer)
        onDispose { commands.uncover(layer) }
    }
    return this
        .blockPointerBelow()
        .semantics {
            isTraversalGroup = true
            if (paneTitle != null) this.paneTitle = paneTitle
        }
        // the focus does not leave it ...
        .focusProperties { onExit = { cancelFocusChange() } }
        .focusGroup()
        // ... and starts on it: Tab goes on to what is in it
        .focusRequester(focus)
        .focusable()
}

/**
 * 1.0.3: what a layer over the whole window covers while [covered] (on a phone the full player
 * covers the bars, the mini player and the screen): the keyboard's focus does not go into it (Tab,
 * the arrows, a focus request: Compose 1.12's subtree blocking, a focus group whose entry is
 * cancelled), the focus it had leaves it, and the window's commands of the screen are silent
 * ([ScreenCommands.covered]). Not covered, it is a plain focus group: the order of Tab through it
 * and the arrows' search into it stay what they were.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun Modifier.coveredByLayer(covered: Boolean): Modifier = this
    .then(CoveredElement(covered))
    .focusProperties { if (covered) onEnter = { cancelFocusChange() } }
    .focusGroup()

private data class CoveredElement(val covered: Boolean) : ModifierNodeElement<CoveredNode>() {
    override fun create() = CoveredNode(covered)

    override fun update(node: CoveredNode) = node.update(covered)

    override fun InspectorInfo.inspectableProperties() {
        name = "coveredByLayer"
        properties["covered"] = covered
    }
}

/** [coveredByLayer]'s part that silences the screen's commands and takes the focus out when it becomes covered. */
private class CoveredNode(private var covered: Boolean) : Modifier.Node(), CompositionLocalConsumerModifierNode, FocusEventModifierNode {
    /** The focus is in the covered part (the focus group after this node). */
    private var hasFocus = false
    private var commands: ScreenCommands? = null

    override fun onAttach() {
        if (covered) cover()
    }

    override fun onDetach() {
        uncover()
    }

    fun update(covered: Boolean) {
        if (covered == this.covered) return
        this.covered = covered
        if (covered) cover() else uncover()
    }

    override fun onFocusEvent(focusState: FocusState) {
        hasFocus = focusState.hasFocus
    }

    private fun cover() {
        val screen = currentValueOf(LocalScreenCommands)
        commands = screen
        screen.cover(this)
        // what had the keyboard is out of sight: it lets go (after this change is applied, not while it is)
        if (hasFocus) {
            coroutineScope.launch {
                if (isAttached && covered && hasFocus) currentValueOf(LocalFocusManager).clearFocus(force = true)
            }
        }
    }

    private fun uncover() {
        commands?.uncover(this)
        commands = null
    }
}
