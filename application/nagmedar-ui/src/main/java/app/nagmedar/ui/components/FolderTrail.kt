package app.nagmedar.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow

/**
 * 1.0.3: the scroll of a folder's trail (Sources › This device, Network), which keeps its end in view: the open
 * folder is the trail's last name, and on a phone a way of three folders is already longer than the bar. Whenever
 * [trail] changes or the bar gets another width the row goes to its end; the folders before it are a swipe back.
 */
@Composable
fun rememberTrailScroll(trail: Any?): ScrollState {
    val scroll = rememberScrollState()
    LaunchedEffect(scroll, trail) {
        snapshotFlow { scroll.maxValue }.collect { max ->
            // not measured yet: the state's first maxValue is Int.MAX_VALUE
            if (max != Int.MAX_VALUE && scroll.value != max) scroll.dispatchRawDelta((max - scroll.value).toFloat())
        }
    }
    return scroll
}
