package app.nagmedar.ui

import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.DragEvent
import android.view.KeyEvent
import android.view.KeyboardShortcutGroup
import android.view.Menu
import android.view.MotionEvent
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.nagmedar.ui.input.DroppedFile
import app.nagmedar.ui.input.MenuBarKey
import app.nagmedar.ui.input.NAGMEDAR_DRAG
import app.nagmedar.ui.input.isMediaOrSubtitleType
import app.nagmedar.ui.input.ShortcutRegistry
import org.videolan.tools.NAGMEDAR_EXTRA_DESTINATION
import org.videolan.tools.NAGMEDAR_WINDOW_BOUNDS
import org.videolan.tools.Settings
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

    /** Alt alone or F10 takes the keyboard to the menu bar and back (S-302). */
    private val menuBarKey = MenuBarKey()

    /** Screen asked for by a launcher shortcut (S-309), until the shell has shown it. */
    private var destination by mutableStateOf<String?>(null)

    private val openDocument = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) MediaUtils.openUri(this, uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) destination = intent?.getStringExtra(NAGMEDAR_EXTRA_DESTINATION)
        setContent {
            NagmedarApp(
                onOpenFile = { openDocument.launch(arrayOf("video/*", "audio/*", "application/x-mpegurl", "application/octet-stream")) },
                onActionsReady = { actions = it },
                requestedDestination = destination,
                onDestinationShown = { destination = null },
            )
        }
        // files dragged in from another app or window (S-406)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) window.decorView.setOnDragListener { _, event -> onWindowDrag(event) }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun onWindowDrag(event: DragEvent): Boolean {
        val actions = actions ?: return false
        val targets = actions.dropTargets
        val description = event.clipDescription
        val types = description?.let { d -> (0 until d.mimeTypeCount).map { d.getMimeType(it) } }.orEmpty()
        // a film dragged from one of our lists counts only over a target
        val own = event.localState == NAGMEDAR_DRAG
        return when (event.action) {
            DragEvent.ACTION_DRAG_STARTED -> own || types.any(::isMediaOrSubtitleType)
            DragEvent.ACTION_DRAG_ENTERED, DragEvent.ACTION_DRAG_LOCATION -> {
                targets.moved(event.x, event.y, types, outside = !own)
                true
            }
            DragEvent.ACTION_DRAG_EXITED, DragEvent.ACTION_DRAG_ENDED -> {
                targets.ended()
                true
            }
            DragEvent.ACTION_DROP -> {
                targets.ended()
                val clip = event.clipData ?: return false
                // the files stay readable while the window lives; a SAF document's access is kept for good
                requestDragAndDropPermissions(event)
                val files = (0 until clip.itemCount).mapNotNull { index ->
                    val item = clip.getItemAt(index)
                    item.uri ?: item.text?.toString()?.trim()?.takeIf { it.contains("://") }?.let(Uri::parse)
                }.map { uri ->
                    keepAccess(uri)
                    DroppedFile(uri.toString(), displayName(uri), contentResolver.getType(uri))
                }
                if (files.isEmpty()) return false
                val target = targets.at(event.x, event.y, types)
                when {
                    target != null && target.second.onDrop(files) -> true
                    own -> false
                    else -> actions.dropFiles(files)
                }
            }
            else -> false
        }
    }

    private fun displayName(uri: Uri): String? {
        if (uri.scheme == "content") {
            runCatching {
                contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) return cursor.getString(0)
                }
            }
        }
        return uri.lastPathSegment
    }

    private fun keepAccess(uri: Uri) {
        if (uri.scheme != "content") return
        runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    }

    override fun onStop() {
        super.onStop()
        saveWindowBounds()
    }

    /** S-409: a desktop window's place and size, for the launcher to open it there again. */
    private fun saveWindowBounds() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N || !isInMultiWindowMode) return
        val bounds = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowManager.currentWindowMetrics.bounds
        } else {
            val at = IntArray(2)
            window.decorView.getLocationOnScreen(at)
            Rect(at[0], at[1], at[0] + window.decorView.width, at[1] + window.decorView.height)
        }
        // a window folded to nothing is not a size to come back to
        if (bounds.width() < 320 || bounds.height() < 240) return
        Settings.getInstance(this).edit().putString(NAGMEDAR_WINDOW_BOUNDS, bounds.flattenToString()).apply()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(NAGMEDAR_EXTRA_DESTINATION)?.let { destination = it }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // the shortcut editor is recording keys (S-402)
        if (ShortcutRegistry.recording) return super.dispatchKeyEvent(event)
        val down = event.action == KeyEvent.ACTION_DOWN
        if (menuBarKey.onKey(event.keyCode, down) && actions?.toggleMenuBar() == true) return true
        val rtl = resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
        if (actions?.menuBarKey(event.keyCode, down, rtl) == true) return true
        if (event.action == KeyEvent.ACTION_DOWN && (event.isCtrlPressed || event.isAltPressed || event.isMetaPressed)) {
            val shortcut = ShortcutRegistry.match(ComposeKeyEvent(event))
            // Ctrl+A and Ctrl+Z go to a focused text field first (onKeyDown)
            if (shortcut != null && !shortcut.textFirst && actions?.perform(shortcut.id) == true) return true
        }
        return super.dispatchKeyEvent(event)
    }

    /** Back inside the app (never out of it), remembered for the forward button. */
    private fun goBack(): Boolean {
        if (!onBackPressedDispatcher.hasEnabledCallbacks()) return false
        val from = actions?.currentRoute()
        onBackPressedDispatcher.onBackPressed()
        actions?.wentBack(from)
        return true
    }

    /** The mouse's back and forward buttons move through the screens seen (S-408). */
    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_BUTTON_PRESS) {
            when (event.actionButton) {
                MotionEvent.BUTTON_BACK -> if (goBack()) return true
                MotionEvent.BUTTON_FORWARD -> if (actions?.forward() == true) return true
            }
        }
        return super.dispatchGenericMotionEvent(event)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        // a click elsewhere takes the keyboard from the menu bar
        if (event.actionMasked == MotionEvent.ACTION_DOWN) actions?.leaveMenuBar()
        return super.dispatchTouchEvent(event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        // the plain arrows move the focus in the shell; bound to a shortcut (mpv's seek) they act in the player
        if (!ShortcutRegistry.recording && !(isArrow(keyCode) && event.hasNoModifiers())) {
            val shortcut = ShortcutRegistry.match(ComposeKeyEvent(event))
            if (shortcut != null && (shortcut.chord?.hasModifier != true || shortcut.textFirst) && actions?.perform(shortcut.id) == true) return true
        }
        if (keyCode == KeyEvent.KEYCODE_ESCAPE && event.hasNoModifiers()) {
            // an Esc nothing else used lets go of the screen's selection (S-405), else goes back (S-407):
            // closes the details, leaves a folder, returns to the screen before; never leaves the app
            if (actions?.escape() == true || goBack()) return true
        }
        // the forward key of some keyboards, as the mouse's forward button (S-408)
        if (keyCode == KeyEvent.KEYCODE_FORWARD && actions?.forward() == true) return true
        return super.onKeyDown(keyCode, event)
    }

    private fun isArrow(keyCode: Int) = keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ||
        keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN

    @RequiresApi(Build.VERSION_CODES.N)
    override fun onProvideKeyboardShortcuts(data: MutableList<KeyboardShortcutGroup>, menu: Menu?, deviceId: Int) {
        super.onProvideKeyboardShortcuts(data, menu, deviceId)
        data.addAll(ShortcutRegistry.keyboardShortcutGroups(resources))
    }
}
