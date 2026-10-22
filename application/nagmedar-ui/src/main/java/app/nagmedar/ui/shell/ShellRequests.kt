package app.nagmedar.ui.shell

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** What the window is asked for from outside it (the system's search, a preference): the shell takes it up and clears it. */
object ShellRequests {
    /** The words of a search to run on the search page. */
    var search by mutableStateOf<String?>(null)

    /** Settings › Library, the sections' folders (a new drive's notification). */
    var librarySources by mutableStateOf(false)

    /** A preference key whose page "All settings" opens (the player's metered connection question, the palette). */
    var preference by mutableStateOf<String?>(null)
}
