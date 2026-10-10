package app.nagmedar.ui.input

import android.content.res.Resources
import android.view.KeyEvent as AndroidKeyEvent
import android.view.KeyboardShortcutGroup
import android.view.KeyboardShortcutInfo
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import app.nagmedar.ui.R

/** A key with its modifiers, e.g. Ctrl+K. */
@Immutable
data class KeyChord(
    val key: Key,
    val ctrl: Boolean = false,
    val shift: Boolean = false,
    val alt: Boolean = false,
    val meta: Boolean = false,
) {
    val hasModifier: Boolean get() = ctrl || alt || meta

    fun matches(event: KeyEvent): Boolean = event.key == key && event.isCtrlPressed == ctrl &&
        event.isShiftPressed == shift && event.isAltPressed == alt && event.isMetaPressed == meta

    /** "Ctrl+Shift+N", "Space", "Alt+→" — the same text in menus, tooltips and the shortcut list. */
    fun label(resources: Resources? = null): String = buildString {
        if (ctrl) append("Ctrl+")
        if (alt) append("Alt+")
        if (shift) append("Shift+")
        if (meta) append("Meta+")
        append(keyName(key, resources))
    }

    /** Android meta state for [KeyboardShortcutInfo]. */
    internal val androidModifiers: Int
        get() = (if (ctrl) AndroidKeyEvent.META_CTRL_ON else 0) or
            (if (shift) AndroidKeyEvent.META_SHIFT_ON else 0) or
            (if (alt) AndroidKeyEvent.META_ALT_ON else 0) or
            (if (meta) AndroidKeyEvent.META_META_ON else 0)
}

private fun keyName(key: Key, resources: Resources?): String = when (key) {
    Key.Spacebar -> resources?.getString(R.string.nag_key_space) ?: "Space"
    Key.DirectionLeft -> "←"
    Key.DirectionRight -> "→"
    Key.DirectionUp -> "↑"
    Key.DirectionDown -> "↓"
    Key.Enter -> "Enter"
    Key.Escape -> "Esc"
    Key.Delete -> "Del"
    Key.Comma -> ","
    Key.Slash -> "/"
    Key.LeftBracket -> "["
    Key.RightBracket -> "]"
    Key.Equals -> "="
    Key.F1 -> "F1"
    Key.F11 -> "F11"
    else -> {
        val code = key.nativeKeyCode
        when (code) {
            in AndroidKeyEvent.KEYCODE_A..AndroidKeyEvent.KEYCODE_Z -> ('A' + (code - AndroidKeyEvent.KEYCODE_A)).toString()
            in AndroidKeyEvent.KEYCODE_0..AndroidKeyEvent.KEYCODE_9 -> ('0' + (code - AndroidKeyEvent.KEYCODE_0)).toString()
            else -> AndroidKeyEvent.keyCodeToString(code).removePrefix("KEYCODE_")
        }
    }
}

/** Groups follow the menu bar of the mockups; each group is one menu and one section of the shortcut list. */
enum class ShortcutGroup(@StringRes val title: Int) {
    Media(R.string.nag_menu_media),
    Playback(R.string.nag_menu_playback),
    Audio(R.string.nag_menu_audio),
    Video(R.string.nag_menu_video),
    Subtitle(R.string.nag_menu_subtitle),
    Tools(R.string.nag_menu_tools),
    View(R.string.nag_menu_view),
    Help(R.string.nag_menu_help),
}

/** Where a shortcut works: everywhere in the app, or only while the player is in front. */
enum class ShortcutScope { Global, Player }

enum class ShortcutId {
    OpenFile, OpenNetwork, OpenInNewWindow, MediaInfo,
    PlayPause, Stop, Previous, Next, JumpBackShort, JumpForwardShort, JumpBack, JumpForward,
    JumpBackLong, JumpForwardLong, NextFrame, PreviousFrame, Slower, Faster, NormalSpeed, Repeat, Shuffle,
    VolumeUp, VolumeDown, Mute, AudioDelayDown, AudioDelayUp, NextAudioTrack,
    Fullscreen, Snapshot, Effects,
    NextSubtitle, SubtitleDelayDown, SubtitleDelayUp, AddSubtitleFile,
    Settings, CommandPalette, ToggleSidebar, PlayQueue,
    ShortcutList,
}

@Immutable
data class Shortcut(
    val id: ShortcutId,
    val group: ShortcutGroup,
    @StringRes val label: Int,
    val chord: KeyChord,
    val scope: ShortcutScope = ShortcutScope.Global,
)

/**
 * Single source of keyboard shortcuts (S-401, ADR-04). Defaults follow VLC desktop, plus the
 * Nağmedar additions of the mockups (Ctrl+K command palette, Ctrl+, settings). Menus, tooltips,
 * the Meta+/ system list and the settings screen all read this table.
 */
object ShortcutRegistry {

    val defaults: List<Shortcut> = listOf(
        // Media
        Shortcut(ShortcutId.OpenFile, ShortcutGroup.Media, R.string.nag_sc_open_file, KeyChord(Key.O, ctrl = true)),
        Shortcut(ShortcutId.OpenNetwork, ShortcutGroup.Media, R.string.nag_sc_open_network, KeyChord(Key.N, ctrl = true)),
        Shortcut(ShortcutId.OpenInNewWindow, ShortcutGroup.Media, R.string.nag_sc_new_window, KeyChord(Key.N, ctrl = true, shift = true)),
        Shortcut(ShortcutId.MediaInfo, ShortcutGroup.Media, R.string.nag_sc_media_info, KeyChord(Key.I, ctrl = true)),
        // Playback
        Shortcut(ShortcutId.PlayPause, ShortcutGroup.Playback, R.string.nag_sc_play_pause, KeyChord(Key.Spacebar)),
        Shortcut(ShortcutId.Stop, ShortcutGroup.Playback, R.string.nag_sc_stop, KeyChord(Key.S)),
        Shortcut(ShortcutId.Previous, ShortcutGroup.Playback, R.string.nag_sc_previous, KeyChord(Key.P)),
        Shortcut(ShortcutId.Next, ShortcutGroup.Playback, R.string.nag_sc_next, KeyChord(Key.N)),
        Shortcut(ShortcutId.JumpBackShort, ShortcutGroup.Playback, R.string.nag_sc_back_3s, KeyChord(Key.DirectionLeft, shift = true)),
        Shortcut(ShortcutId.JumpForwardShort, ShortcutGroup.Playback, R.string.nag_sc_forward_3s, KeyChord(Key.DirectionRight, shift = true)),
        Shortcut(ShortcutId.JumpBack, ShortcutGroup.Playback, R.string.nag_sc_back_10s, KeyChord(Key.DirectionLeft, alt = true)),
        Shortcut(ShortcutId.JumpForward, ShortcutGroup.Playback, R.string.nag_sc_forward_10s, KeyChord(Key.DirectionRight, alt = true)),
        Shortcut(ShortcutId.JumpBackLong, ShortcutGroup.Playback, R.string.nag_sc_back_1m, KeyChord(Key.DirectionLeft, ctrl = true)),
        Shortcut(ShortcutId.JumpForwardLong, ShortcutGroup.Playback, R.string.nag_sc_forward_1m, KeyChord(Key.DirectionRight, ctrl = true)),
        Shortcut(ShortcutId.NextFrame, ShortcutGroup.Playback, R.string.nag_sc_next_frame, KeyChord(Key.E), ShortcutScope.Player),
        Shortcut(ShortcutId.PreviousFrame, ShortcutGroup.Playback, R.string.nag_sc_previous_frame, KeyChord(Key.E, shift = true), ShortcutScope.Player),
        Shortcut(ShortcutId.Slower, ShortcutGroup.Playback, R.string.nag_sc_slower, KeyChord(Key.LeftBracket)),
        Shortcut(ShortcutId.Faster, ShortcutGroup.Playback, R.string.nag_sc_faster, KeyChord(Key.RightBracket)),
        Shortcut(ShortcutId.NormalSpeed, ShortcutGroup.Playback, R.string.nag_sc_normal_speed, KeyChord(Key.Equals)),
        Shortcut(ShortcutId.Repeat, ShortcutGroup.Playback, R.string.nag_repeat, KeyChord(Key.L)),
        Shortcut(ShortcutId.Shuffle, ShortcutGroup.Playback, R.string.nag_shuffle, KeyChord(Key.R)),
        // Audio
        Shortcut(ShortcutId.VolumeUp, ShortcutGroup.Audio, R.string.nag_sc_volume_up, KeyChord(Key.DirectionUp, ctrl = true)),
        Shortcut(ShortcutId.VolumeDown, ShortcutGroup.Audio, R.string.nag_sc_volume_down, KeyChord(Key.DirectionDown, ctrl = true)),
        Shortcut(ShortcutId.Mute, ShortcutGroup.Audio, R.string.nag_sc_mute, KeyChord(Key.M)),
        Shortcut(ShortcutId.AudioDelayDown, ShortcutGroup.Audio, R.string.nag_sc_audio_delay_down, KeyChord(Key.J), ShortcutScope.Player),
        Shortcut(ShortcutId.AudioDelayUp, ShortcutGroup.Audio, R.string.nag_sc_audio_delay_up, KeyChord(Key.K), ShortcutScope.Player),
        Shortcut(ShortcutId.NextAudioTrack, ShortcutGroup.Audio, R.string.nag_sc_next_audio_track, KeyChord(Key.B), ShortcutScope.Player),
        // Video
        Shortcut(ShortcutId.Fullscreen, ShortcutGroup.Video, R.string.nag_sc_fullscreen, KeyChord(Key.F), ShortcutScope.Player),
        Shortcut(ShortcutId.Snapshot, ShortcutGroup.Video, R.string.nag_sc_snapshot, KeyChord(Key.S, shift = true), ShortcutScope.Player),
        Shortcut(ShortcutId.Effects, ShortcutGroup.Video, R.string.nag_sc_effects, KeyChord(Key.E, ctrl = true)),
        // Subtitle
        Shortcut(ShortcutId.NextSubtitle, ShortcutGroup.Subtitle, R.string.nag_sc_next_subtitle, KeyChord(Key.V), ShortcutScope.Player),
        Shortcut(ShortcutId.SubtitleDelayDown, ShortcutGroup.Subtitle, R.string.nag_sc_subtitle_delay_down, KeyChord(Key.G), ShortcutScope.Player),
        Shortcut(ShortcutId.SubtitleDelayUp, ShortcutGroup.Subtitle, R.string.nag_sc_subtitle_delay_up, KeyChord(Key.H), ShortcutScope.Player),
        Shortcut(ShortcutId.AddSubtitleFile, ShortcutGroup.Subtitle, R.string.nag_sc_add_subtitle, KeyChord(Key.A, ctrl = true, shift = true), ShortcutScope.Player),
        // Tools
        Shortcut(ShortcutId.Settings, ShortcutGroup.Tools, R.string.nag_nav_settings, KeyChord(Key.Comma, ctrl = true)),
        // View
        Shortcut(ShortcutId.CommandPalette, ShortcutGroup.View, R.string.nag_search, KeyChord(Key.K, ctrl = true)),
        Shortcut(ShortcutId.ToggleSidebar, ShortcutGroup.View, R.string.nag_sc_toggle_sidebar, KeyChord(Key.B, ctrl = true)),
        Shortcut(ShortcutId.PlayQueue, ShortcutGroup.View, R.string.nag_sc_play_queue, KeyChord(Key.L, ctrl = true)),
        // Help
        Shortcut(ShortcutId.ShortcutList, ShortcutGroup.Help, R.string.nag_sc_shortcut_list, KeyChord(Key.Slash, ctrl = true)),
    )

    private val byId = defaults.associateBy { it.id }

    operator fun get(id: ShortcutId): Shortcut = byId.getValue(id)

    fun chordLabel(id: ShortcutId, resources: Resources? = null): String = get(id).chord.label(resources)

    /** Shortcut for [event] among those active in [scopes]; null when the key is not bound. */
    fun match(event: KeyEvent, scopes: Set<ShortcutScope> = setOf(ShortcutScope.Global)): Shortcut? =
        defaults.firstOrNull { it.scope in scopes && it.chord.matches(event) }

    /** Pairs of shortcuts bound to the same chord; must stay empty (ShortcutRegistryTest). */
    fun conflicts(shortcuts: List<Shortcut> = defaults): List<Pair<Shortcut, Shortcut>> =
        shortcuts.groupBy { it.chord }.values.filter { it.size > 1 }.flatMap { group ->
            group.indices.flatMap { i -> (i + 1 until group.size).map { j -> group[i] to group[j] } }
        }

    /** The keyboard shortcut helper list (Meta+/ or the system's keyboard shortcuts screen). */
    @RequiresApi(24)
    fun keyboardShortcutGroups(resources: Resources): List<KeyboardShortcutGroup> =
        ShortcutGroup.entries.mapNotNull { group ->
            val items = defaults.filter { it.group == group }.map { shortcut ->
                KeyboardShortcutInfo(resources.getString(shortcut.label), shortcut.chord.key.nativeKeyCode, shortcut.chord.androidModifiers)
            }
            if (items.isEmpty()) null else KeyboardShortcutGroup(resources.getString(group.title), items)
        }
}
