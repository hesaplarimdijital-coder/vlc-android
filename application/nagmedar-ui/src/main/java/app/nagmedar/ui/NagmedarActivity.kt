package app.nagmedar.ui

import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.KeyboardShortcutGroup
import android.view.Menu
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import app.nagmedar.ui.input.ShortcutRegistry
import org.videolan.vlc.media.MediaUtils
import androidx.compose.ui.input.key.KeyEvent as ComposeKeyEvent

/**
 * Entry point of the new Nağmedar interface (S-006). Playback still runs in the existing
 * PlaybackService; this activity only hosts the Compose shell.
 *
 * Keyboard shortcuts come from [ShortcutRegistry]: chords with Ctrl/Alt/Meta are handled before
 * the views see the key, plain keys (Space, N, P, M …) only when no focused element used them.
 * The same table feeds the system's keyboard shortcut list (Meta+/).
 */
class NagmedarActivity : ComponentActivity() {

    private var actions: AppActions? = null

    private val openDocument = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) MediaUtils.openUri(this, uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            NagmedarApp(
                onOpenFile = { openDocument.launch(arrayOf("video/*", "audio/*", "application/x-mpegurl", "application/octet-stream")) },
                onActionsReady = { actions = it },
            )
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && (event.isCtrlPressed || event.isAltPressed || event.isMetaPressed)) {
            val shortcut = ShortcutRegistry.match(ComposeKeyEvent(event))
            if (shortcut != null && actions?.perform(shortcut.id) == true) return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val shortcut = ShortcutRegistry.match(ComposeKeyEvent(event))
        if (shortcut != null && !shortcut.chord.hasModifier && actions?.perform(shortcut.id) == true) return true
        return super.onKeyDown(keyCode, event)
    }

    @RequiresApi(Build.VERSION_CODES.N)
    override fun onProvideKeyboardShortcuts(data: MutableList<KeyboardShortcutGroup>, menu: Menu?, deviceId: Int) {
        super.onProvideKeyboardShortcuts(data, menu, deviceId)
        data.addAll(ShortcutRegistry.keyboardShortcutGroups(resources))
    }
}
