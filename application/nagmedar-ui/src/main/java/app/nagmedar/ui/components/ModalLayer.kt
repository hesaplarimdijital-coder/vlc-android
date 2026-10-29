package app.nagmedar.ui.components

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics

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
 * window it reads nothing under it.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun Modifier.blockInputBelow(paneTitle: String? = null): Modifier {
    val focus = remember { FocusRequester() }
    // a key press goes to what has the focus: the page under it keeps nothing of it
    LaunchedEffect(focus) { runCatching { focus.requestFocus() } }
    return this
        .blockPointerBelow()
        .semantics {
            isTraversalGroup = true
            if (paneTitle != null) this.paneTitle = paneTitle
        }
        // the focus does not leave it ...
        .focusProperties { exit = { FocusRequester.Cancel } }
        .focusGroup()
        // ... and starts on it: Tab goes on to what is in it
        .focusRequester(focus)
        .focusable()
}
