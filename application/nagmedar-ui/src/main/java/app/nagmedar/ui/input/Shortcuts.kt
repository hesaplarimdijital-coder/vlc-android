package app.nagmedar.ui.input

import android.content.res.Resources
import android.view.KeyEvent as AndroidKeyEvent
import android.view.KeyboardShortcutGroup
import android.view.KeyboardShortcutInfo
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
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

    /** Whether [event] is these keys: the key by its place (S-410), see [PhysicalKeys]. */
    fun matches(event: KeyEvent): Boolean = matches(PhysicalKeys.keyOf(event), event)

    /** Whether [key] (the table's key of [event]) and the modifiers of [event] are these keys. */
    internal fun matches(key: Key, event: KeyEvent): Boolean = key == this.key && event.isCtrlPressed == ctrl &&
        event.isShiftPressed == shift && event.isAltPressed == alt && event.isMetaPressed == meta

    /**
     * "Ctrl+Shift+N", "Space", "Alt+→" — the same text in menus, tooltips and the shortcut list;
     * with a hardware keyboard, its keycap: "Ctrl+Ö" for Ctrl+, on Türkçe Q (S-410).
     */
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

/** What the key is called on a keycap: what the keyboard in use prints on it, else its US name. */
internal fun keyName(key: Key, resources: Resources?): String {
    if (key == Key.Spacebar) return resources?.getString(R.string.nag_key_space) ?: "Space"
    val code = key.nativeKeyCode
    PhysicalKeys.printed(code)?.let { return it }
    return usKeyName(code)
}

/** The key's name on a US keyboard. */
internal fun usKeyName(code: Int): String {
    return when (code) {
        in AndroidKeyEvent.KEYCODE_A..AndroidKeyEvent.KEYCODE_Z -> ('A' + (code - AndroidKeyEvent.KEYCODE_A)).toString()
        in AndroidKeyEvent.KEYCODE_0..AndroidKeyEvent.KEYCODE_9 -> ('0' + (code - AndroidKeyEvent.KEYCODE_0)).toString()
        in AndroidKeyEvent.KEYCODE_F1..AndroidKeyEvent.KEYCODE_F12 -> "F${code - AndroidKeyEvent.KEYCODE_F1 + 1}"
        in AndroidKeyEvent.KEYCODE_NUMPAD_0..AndroidKeyEvent.KEYCODE_NUMPAD_9 -> "Num ${code - AndroidKeyEvent.KEYCODE_NUMPAD_0}"
        else -> keySymbols[code] ?: AndroidKeyEvent.keyCodeToString(code).removePrefix("KEYCODE_")
    }
}

/** What the keys without a letter or a number are called on a keycap. */
private val keySymbols: Map<Int, String> = mapOf(
    AndroidKeyEvent.KEYCODE_DPAD_LEFT to "←",
    AndroidKeyEvent.KEYCODE_DPAD_RIGHT to "→",
    AndroidKeyEvent.KEYCODE_DPAD_UP to "↑",
    AndroidKeyEvent.KEYCODE_DPAD_DOWN to "↓",
    AndroidKeyEvent.KEYCODE_ENTER to "Enter",
    AndroidKeyEvent.KEYCODE_ESCAPE to "Esc",
    AndroidKeyEvent.KEYCODE_TAB to "Tab",
    AndroidKeyEvent.KEYCODE_DEL to "Backspace",
    AndroidKeyEvent.KEYCODE_FORWARD_DEL to "Del",
    AndroidKeyEvent.KEYCODE_INSERT to "Ins",
    AndroidKeyEvent.KEYCODE_MOVE_HOME to "Home",
    AndroidKeyEvent.KEYCODE_MOVE_END to "End",
    AndroidKeyEvent.KEYCODE_PAGE_UP to "PgUp",
    AndroidKeyEvent.KEYCODE_PAGE_DOWN to "PgDn",
    AndroidKeyEvent.KEYCODE_COMMA to ",",
    AndroidKeyEvent.KEYCODE_PERIOD to ".",
    AndroidKeyEvent.KEYCODE_SLASH to "/",
    AndroidKeyEvent.KEYCODE_BACKSLASH to "\\",
    AndroidKeyEvent.KEYCODE_SEMICOLON to ";",
    AndroidKeyEvent.KEYCODE_APOSTROPHE to "'",
    AndroidKeyEvent.KEYCODE_GRAVE to "`",
    AndroidKeyEvent.KEYCODE_MINUS to "-",
    AndroidKeyEvent.KEYCODE_EQUALS to "=",
    AndroidKeyEvent.KEYCODE_PLUS to "+",
    AndroidKeyEvent.KEYCODE_LEFT_BRACKET to "[",
    AndroidKeyEvent.KEYCODE_RIGHT_BRACKET to "]",
    AndroidKeyEvent.KEYCODE_NUMPAD_ADD to "Num +",
    AndroidKeyEvent.KEYCODE_NUMPAD_SUBTRACT to "Num -",
    AndroidKeyEvent.KEYCODE_NUMPAD_MULTIPLY to "Num *",
    AndroidKeyEvent.KEYCODE_NUMPAD_DIVIDE to "Num /",
    AndroidKeyEvent.KEYCODE_NUMPAD_DOT to "Num .",
    AndroidKeyEvent.KEYCODE_NUMPAD_ENTER to "Num Enter",
    AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE to "⏯",
    AndroidKeyEvent.KEYCODE_MEDIA_STOP to "⏹",
    AndroidKeyEvent.KEYCODE_MEDIA_NEXT to "⏭",
    AndroidKeyEvent.KEYCODE_MEDIA_PREVIOUS to "⏮",
    // the key map's keys (S-410)
    AndroidKeyEvent.KEYCODE_CTRL_LEFT to "Ctrl",
    AndroidKeyEvent.KEYCODE_CTRL_RIGHT to "Ctrl",
    AndroidKeyEvent.KEYCODE_SHIFT_LEFT to "Shift",
    AndroidKeyEvent.KEYCODE_SHIFT_RIGHT to "Shift",
    AndroidKeyEvent.KEYCODE_ALT_LEFT to "Alt",
    AndroidKeyEvent.KEYCODE_ALT_RIGHT to "Alt",
    AndroidKeyEvent.KEYCODE_META_LEFT to "Meta",
    AndroidKeyEvent.KEYCODE_META_RIGHT to "Meta",
    AndroidKeyEvent.KEYCODE_CAPS_LOCK to "Caps Lock",
)

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

/**
 * Where a shortcut works: everywhere in the app, only while the player is in front, or (S-1005)
 * only while the lyrics editor is open, whose keys come first: a chord of the editor may be one of
 * a global shortcut too, it wins inside the editor.
 */
enum class ShortcutScope {
    Global, Player, Editor,
    ;

    /** Whether a shortcut of this scope and one of [other] can both be waiting for the same key. */
    fun overlaps(other: ShortcutScope): Boolean = when {
        this == Editor || other == Editor -> this == other
        else -> true
    }
}

enum class ShortcutId {
    OpenFile, OpenNetwork, OpenInNewWindow, MediaInfo, Undo, SelectAll,
    PlayPause, Stop, Previous, Next, JumpBackShort, JumpForwardShort, JumpBack, JumpForward,
    JumpBackLong, JumpForwardLong, NextFrame, PreviousFrame, Slower, Faster, NormalSpeed, Repeat, Shuffle,
    ShowTime, JumpToTime, NextChapter, PreviousChapter, AddBookmark, Bookmarks, SleepTimer, Record,
    VolumeUp, VolumeDown, Mute, AudioDelayDown, AudioDelayUp, NextAudioTrack, Equalizer, AbSwitch,
    Fullscreen, Snapshot, Effects, AspectRatio, Crop, Zoom,
    NextSubtitle, SubtitleDelayDown, SubtitleDelayUp, AddSubtitleFile, SecondarySubtitles,
    SyncMarkAudio, SyncMarkSubtitle, SyncApply, SyncUndo,
    SubtitleTools, CueSave, CueSplit, CueMerge, CueSetStart, CueSetEnd, CueNext, CuePrevious,
    GenerateSubtitles,
    Settings, Synchronization, CommandPalette, ToggleSidebar, PlayQueue,
    ShortcutList,
    Lyrics, LyricsEarlier, LyricsLater,
    Visualizer,
    ConvertSave, StreamHls,
    RadioSearch, RadioRecord, RadioRecordings,
    FolderUp, Refresh,
    PodcastSearch,
    Cast,
    PodcastNextChapter, PodcastPreviousChapter,
    LrcStamp, LrcUnstamp, LrcLineUp, LrcLineDown, LrcNudgeEarlier, LrcNudgeLater, LrcWordMode,
}

@Immutable
data class Shortcut(
    val id: ShortcutId,
    val group: ShortcutGroup,
    @StringRes val label: Int,
    /** The keys; null when the user took them away (S-402). */
    val chord: KeyChord?,
    val scope: ShortcutScope = ShortcutScope.Global,
    /** A focused text field uses the keys first (Ctrl+A, Ctrl+Z): the shortcut only gets them when it did not. */
    val textFirst: Boolean = false,
)

/**
 * Single source of keyboard shortcuts (S-401, ADR-04). Defaults follow VLC desktop, plus the
 * Nağmedar additions of the mockups (Ctrl+K command palette, Ctrl+, settings); the user's keys
 * (S-402) lie over them. Menus, tooltips, the Meta+/ system list and the settings screen all read
 * this table.
 */
object ShortcutRegistry {

    val defaults: List<Shortcut> = listOf(
        // Media
        Shortcut(ShortcutId.OpenFile, ShortcutGroup.Media, R.string.nag_sc_open_file, KeyChord(Key.O, ctrl = true)),
        Shortcut(ShortcutId.OpenNetwork, ShortcutGroup.Media, R.string.nag_sc_open_network, KeyChord(Key.N, ctrl = true)),
        Shortcut(ShortcutId.OpenInNewWindow, ShortcutGroup.Media, R.string.nag_sc_new_window, KeyChord(Key.N, ctrl = true, shift = true)),
        // S-605: the video player's Info tab
        Shortcut(ShortcutId.MediaInfo, ShortcutGroup.Media, R.string.nag_sc_media_info, KeyChord(Key.I, ctrl = true), ShortcutScope.Player),
        Shortcut(ShortcutId.Undo, ShortcutGroup.Media, R.string.nag_sc_undo, KeyChord(Key.Z, ctrl = true), textFirst = true),
        Shortcut(ShortcutId.SelectAll, ShortcutGroup.Media, R.string.nag_sc_select_all, KeyChord(Key.A, ctrl = true), textFirst = true),
        // S-1303: VLC desktop's Media › Convert/Save and Stream
        Shortcut(ShortcutId.ConvertSave, ShortcutGroup.Media, R.string.nag_sc_convert, KeyChord(Key.R, ctrl = true)),
        Shortcut(ShortcutId.StreamHls, ShortcutGroup.Media, R.string.nag_sc_stream, KeyChord(Key.H, ctrl = true, shift = true)),
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
        // S-602: the rest of VLC's player keys
        Shortcut(ShortcutId.ShowTime, ShortcutGroup.Playback, R.string.nag_sc_show_time, KeyChord(Key.T), ShortcutScope.Player),
        Shortcut(ShortcutId.JumpToTime, ShortcutGroup.Playback, R.string.nag_sc_jump_to_time, KeyChord(Key.T, ctrl = true), ShortcutScope.Player),
        // S-608: chapters as VLC desktop steps through them, and bookmarks
        Shortcut(ShortcutId.NextChapter, ShortcutGroup.Playback, R.string.nag_sc_next_chapter, KeyChord(Key.N, shift = true), ShortcutScope.Player),
        Shortcut(ShortcutId.PreviousChapter, ShortcutGroup.Playback, R.string.nag_sc_previous_chapter, KeyChord(Key.P, shift = true), ShortcutScope.Player),
        // S-1104: the chapters of a podcast episode (ID3 or Podcasting 2.0) are not libVLC's, so they have keys of their own, from anywhere
        Shortcut(ShortcutId.PodcastNextChapter, ShortcutGroup.Playback, R.string.nag_sc_podcast_next_chapter, KeyChord(Key.DirectionRight, ctrl = true, alt = true)),
        Shortcut(ShortcutId.PodcastPreviousChapter, ShortcutGroup.Playback, R.string.nag_sc_podcast_previous_chapter, KeyChord(Key.DirectionLeft, ctrl = true, alt = true)),
        Shortcut(ShortcutId.AddBookmark, ShortcutGroup.Playback, R.string.nag_sc_add_bookmark, KeyChord(Key.D, ctrl = true), ShortcutScope.Player),
        Shortcut(ShortcutId.Bookmarks, ShortcutGroup.Playback, R.string.nag_sc_bookmarks, KeyChord(Key.B, ctrl = true, shift = true), ShortcutScope.Player),
        // S-1007: the sleep timer's window (S for sleep; VLC has none)
        Shortcut(ShortcutId.SleepTimer, ShortcutGroup.Playback, R.string.nag_sc_sleep_timer, KeyChord(Key.S, alt = true)),
        // S-607: what plays recorded, as VLC desktop's Playback › Record
        Shortcut(ShortcutId.Record, ShortcutGroup.Playback, R.string.nag_sc_record, KeyChord(Key.R, shift = true), ShortcutScope.Player),
        // S-1302: the device picker, as VLC desktop's Playback › Renderer; C for cast
        Shortcut(ShortcutId.Cast, ShortcutGroup.Playback, R.string.nag_sc_cast, KeyChord(Key.C, ctrl = true, shift = true)),
        // Audio
        Shortcut(ShortcutId.VolumeUp, ShortcutGroup.Audio, R.string.nag_sc_volume_up, KeyChord(Key.DirectionUp, ctrl = true)),
        Shortcut(ShortcutId.VolumeDown, ShortcutGroup.Audio, R.string.nag_sc_volume_down, KeyChord(Key.DirectionDown, ctrl = true)),
        Shortcut(ShortcutId.Mute, ShortcutGroup.Audio, R.string.nag_sc_mute, KeyChord(Key.M)),
        Shortcut(ShortcutId.AudioDelayDown, ShortcutGroup.Audio, R.string.nag_sc_audio_delay_down, KeyChord(Key.J), ShortcutScope.Player),
        Shortcut(ShortcutId.AudioDelayUp, ShortcutGroup.Audio, R.string.nag_sc_audio_delay_up, KeyChord(Key.K), ShortcutScope.Player),
        Shortcut(ShortcutId.NextAudioTrack, ShortcutGroup.Audio, R.string.nag_sc_next_audio_track, KeyChord(Key.B), ShortcutScope.Player),
        // S-603: the equaliser: libVLC's window in the video player, the music engine's page in the library (S-906)
        Shortcut(ShortcutId.Equalizer, ShortcutGroup.Audio, R.string.nag_sc_equalizer, KeyChord(Key.E, ctrl = true, shift = true)),
        // S-912: the A/B listening test's side (A, B; X in the blind test), wherever the music is chosen
        Shortcut(ShortcutId.AbSwitch, ShortcutGroup.Audio, R.string.nag_sc_ab_switch, KeyChord(Key.B, alt = true)),
        // S-1004: the lyrics' delay in tenths of a second, as G and H move the subtitles' in the player
        Shortcut(ShortcutId.LyricsEarlier, ShortcutGroup.Audio, R.string.nag_sc_lyrics_earlier, KeyChord(Key.G, alt = true)),
        Shortcut(ShortcutId.LyricsLater, ShortcutGroup.Audio, R.string.nag_sc_lyrics_later, KeyChord(Key.H, alt = true)),
        // S-1005: the lyrics editor's keys, while it is open: Enter stamps the line (the next word in word mode), Space stays play/pause
        Shortcut(ShortcutId.LrcStamp, ShortcutGroup.Audio, R.string.nag_sc_lrc_stamp, KeyChord(Key.Enter), ShortcutScope.Editor),
        Shortcut(ShortcutId.LrcUnstamp, ShortcutGroup.Audio, R.string.nag_sc_lrc_unstamp, KeyChord(Key.Backspace), ShortcutScope.Editor),
        Shortcut(ShortcutId.LrcLineUp, ShortcutGroup.Audio, R.string.nag_sc_lrc_line_up, KeyChord(Key.DirectionUp), ShortcutScope.Editor),
        Shortcut(ShortcutId.LrcLineDown, ShortcutGroup.Audio, R.string.nag_sc_lrc_line_down, KeyChord(Key.DirectionDown), ShortcutScope.Editor),
        Shortcut(ShortcutId.LrcNudgeEarlier, ShortcutGroup.Audio, R.string.nag_sc_lrc_nudge_earlier, KeyChord(Key.Comma), ShortcutScope.Editor),
        Shortcut(ShortcutId.LrcNudgeLater, ShortcutGroup.Audio, R.string.nag_sc_lrc_nudge_later, KeyChord(Key.Period), ShortcutScope.Editor),
        Shortcut(ShortcutId.LrcWordMode, ShortcutGroup.Audio, R.string.nag_sc_lrc_word_mode, KeyChord(Key.W), ShortcutScope.Editor),
        // Video
        Shortcut(ShortcutId.Fullscreen, ShortcutGroup.Video, R.string.nag_sc_fullscreen, KeyChord(Key.F), ShortcutScope.Player),
        Shortcut(ShortcutId.Snapshot, ShortcutGroup.Video, R.string.nag_sc_snapshot, KeyChord(Key.S, shift = true), ShortcutScope.Player),
        // S-603: the video player's effects panel
        Shortcut(ShortcutId.Effects, ShortcutGroup.Video, R.string.nag_sc_effects, KeyChord(Key.E, ctrl = true), ShortcutScope.Player),
        Shortcut(ShortcutId.AspectRatio, ShortcutGroup.Video, R.string.nag_sc_aspect_ratio, KeyChord(Key.A), ShortcutScope.Player),
        Shortcut(ShortcutId.Crop, ShortcutGroup.Video, R.string.nag_sc_crop, KeyChord(Key.C), ShortcutScope.Player),
        Shortcut(ShortcutId.Zoom, ShortcutGroup.Video, R.string.nag_sc_zoom, KeyChord(Key.Z), ShortcutScope.Player),
        // Subtitle
        Shortcut(ShortcutId.NextSubtitle, ShortcutGroup.Subtitle, R.string.nag_sc_next_subtitle, KeyChord(Key.V), ShortcutScope.Player),
        Shortcut(ShortcutId.SubtitleDelayDown, ShortcutGroup.Subtitle, R.string.nag_sc_subtitle_delay_down, KeyChord(Key.G), ShortcutScope.Player),
        Shortcut(ShortcutId.SubtitleDelayUp, ShortcutGroup.Subtitle, R.string.nag_sc_subtitle_delay_up, KeyChord(Key.H), ShortcutScope.Player),
        Shortcut(ShortcutId.AddSubtitleFile, ShortcutGroup.Subtitle, R.string.nag_sc_add_subtitle, KeyChord(Key.A, ctrl = true, shift = true), ShortcutScope.Player),
        // S-703: the secondary track on and off, as VLC 4's own key for it
        Shortcut(ShortcutId.SecondarySubtitles, ShortcutGroup.Subtitle, R.string.nag_sc_secondary_subtitles, KeyChord(Key.V, alt = true), ShortcutScope.Player),
        // S-704: the sync assistant, as VLC desktop's: the sound, the subtitle, their difference; and back
        Shortcut(ShortcutId.SyncMarkAudio, ShortcutGroup.Subtitle, R.string.nag_sc_sync_mark_audio, KeyChord(Key.H, shift = true), ShortcutScope.Player),
        Shortcut(ShortcutId.SyncMarkSubtitle, ShortcutGroup.Subtitle, R.string.nag_sc_sync_mark_subtitle, KeyChord(Key.J, shift = true), ShortcutScope.Player),
        Shortcut(ShortcutId.SyncApply, ShortcutGroup.Subtitle, R.string.nag_sc_sync_apply, KeyChord(Key.K, shift = true), ShortcutScope.Player),
        Shortcut(ShortcutId.SyncUndo, ShortcutGroup.Subtitle, R.string.nag_sc_sync_undo, KeyChord(Key.K, ctrl = true, shift = true), ShortcutScope.Player),
        // S-708: the subtitle tools, and the line timing editor's keys while it is open
        Shortcut(ShortcutId.SubtitleTools, ShortcutGroup.Subtitle, R.string.nag_sc_subtitle_tools, KeyChord(Key.E, alt = true), ShortcutScope.Player),
        Shortcut(ShortcutId.CueSave, ShortcutGroup.Subtitle, R.string.nag_sc_cue_save, KeyChord(Key.S, ctrl = true), ShortcutScope.Player),
        Shortcut(ShortcutId.CueSplit, ShortcutGroup.Subtitle, R.string.nag_sc_cue_split, KeyChord(Key.S, ctrl = true, alt = true), ShortcutScope.Player),
        Shortcut(ShortcutId.CueMerge, ShortcutGroup.Subtitle, R.string.nag_sc_cue_merge, KeyChord(Key.M, ctrl = true, alt = true), ShortcutScope.Player),
        Shortcut(ShortcutId.CueSetStart, ShortcutGroup.Subtitle, R.string.nag_sc_cue_set_start, KeyChord(Key.LeftBracket, ctrl = true), ShortcutScope.Player),
        Shortcut(ShortcutId.CueSetEnd, ShortcutGroup.Subtitle, R.string.nag_sc_cue_set_end, KeyChord(Key.RightBracket, ctrl = true), ShortcutScope.Player),
        Shortcut(ShortcutId.CueNext, ShortcutGroup.Subtitle, R.string.nag_sc_cue_next, KeyChord(Key.PageDown, alt = true), ShortcutScope.Player),
        Shortcut(ShortcutId.CuePrevious, ShortcutGroup.Subtitle, R.string.nag_sc_cue_previous, KeyChord(Key.PageUp, alt = true), ShortcutScope.Player),
        // S-709: subtitles from the film's speech, with whisper on the device
        Shortcut(ShortcutId.GenerateSubtitles, ShortcutGroup.Subtitle, R.string.nag_sc_generate_subtitles, KeyChord(Key.W, alt = true), ShortcutScope.Player),
        // Tools
        Shortcut(ShortcutId.Settings, ShortcutGroup.Tools, R.string.nag_nav_settings, KeyChord(Key.Comma, ctrl = true)),
        // S-604: the player panel's sync tab, as VLC's Tools › Track Synchronization
        Shortcut(ShortcutId.Synchronization, ShortcutGroup.Tools, R.string.nag_sc_sync, KeyChord(Key.S, ctrl = true, shift = true), ShortcutScope.Player),
        // S-1105: the radio directory's search, from anywhere
        Shortcut(ShortcutId.RadioSearch, ShortcutGroup.Tools, R.string.nag_sc_radio_search, KeyChord(Key.R, ctrl = true, shift = true)),
        // S-1107: the station on the air recorded (again: stopped), and the radio recordings
        Shortcut(ShortcutId.RadioRecord, ShortcutGroup.Tools, R.string.nag_sc_radio_record, KeyChord(Key.R, alt = true)),
        Shortcut(ShortcutId.RadioRecordings, ShortcutGroup.Tools, R.string.nag_sc_radio_recordings, KeyChord(Key.R, alt = true, shift = true)),
        // View
        Shortcut(ShortcutId.CommandPalette, ShortcutGroup.View, R.string.nag_search, KeyChord(Key.K, ctrl = true)),
        Shortcut(ShortcutId.ToggleSidebar, ShortcutGroup.View, R.string.nag_sc_toggle_sidebar, KeyChord(Key.B, ctrl = true)),
        Shortcut(ShortcutId.PlayQueue, ShortcutGroup.View, R.string.nag_sc_play_queue, KeyChord(Key.L, ctrl = true)),
        // S-1004: the playing track's lyrics over the whole window
        Shortcut(ShortcutId.Lyrics, ShortcutGroup.View, R.string.nag_sc_lyrics, KeyChord(Key.L, ctrl = true, shift = true)),
        // S-1301: a browser's folders, as a file manager's: up a folder, and the folder read again
        Shortcut(ShortcutId.FolderUp, ShortcutGroup.View, R.string.nag_sc_folder_up, KeyChord(Key.Backspace)),
        Shortcut(ShortcutId.Refresh, ShortcutGroup.View, R.string.nag_sc_refresh, KeyChord(Key.F5)),
        // S-1101: the podcast search field, from anywhere
        Shortcut(ShortcutId.PodcastSearch, ShortcutGroup.View, R.string.nag_sc_podcast_search, KeyChord(Key.P, ctrl = true, shift = true)),
        // S-611: what plays drawn over the whole window; a text field's Ctrl+Shift+V pastes first
        Shortcut(ShortcutId.Visualizer, ShortcutGroup.View, R.string.nag_sc_visualizer, KeyChord(Key.V, ctrl = true, shift = true), textFirst = true),
        // Help
        Shortcut(ShortcutId.ShortcutList, ShortcutGroup.Help, R.string.nag_sc_shortcut_list, KeyChord(Key.Slash, ctrl = true)),
    )

    private val byId = defaults.associateBy { it.id }

    /**
     * The user's keys over [defaults] (S-402): a chord, or null for none. Compose state, so menus,
     * tooltips, the palette and the shortcut list follow a change at once.
     */
    var overrides: Map<ShortcutId, KeyChord?> by mutableStateOf(emptyMap())

    /** What keeps keys from being shortcuts: the shortcut editor recording them, the key map trying them (S-410). */
    private val pausedBy = HashSet<Any>()

    /** While the shortcut editor records keys or the key map tries them, they are not shortcuts. */
    val recording: Boolean get() = synchronized(pausedBy) { pausedBy.isNotEmpty() }

    /** [owner] takes the keys ([paused]) or gives them back. */
    fun pause(owner: Any, paused: Boolean) {
        synchronized(pausedBy) { if (paused) pausedBy += owner else pausedBy -= owner }
    }

    /** Tests: nothing takes the keys. */
    @VisibleForTesting
    internal fun resume() {
        synchronized(pausedBy) { pausedBy.clear() }
    }

    /** The table in use: [defaults] with [overrides]. */
    val shortcuts: List<Shortcut>
        get() {
            val user = overrides
            return if (user.isEmpty()) defaults else defaults.map { if (it.id in user) it.copy(chord = user[it.id]) else it }
        }

    operator fun get(id: ShortcutId): Shortcut {
        val user = overrides
        val shortcut = byId.getValue(id)
        return if (id in user) shortcut.copy(chord = user[id]) else shortcut
    }

    /** The shipped shortcut of [id], whatever the user changed. */
    fun default(id: ShortcutId): Shortcut = byId.getValue(id)

    fun chordLabel(id: ShortcutId, resources: Resources? = null): String? = get(id).chord?.label(resources)

    /** Shortcut for [event] among those active in [scopes]; null when the key is not bound. */
    fun match(event: KeyEvent, scopes: Set<ShortcutScope> = setOf(ShortcutScope.Global)): Shortcut? {
        val key = PhysicalKeys.keyOf(event)
        return shortcuts.firstOrNull { it.scope in scopes && it.chord?.matches(key, event) == true }
    }

    /**
     * Pairs of shortcuts bound to the same chord where both can be waiting for it ([ShortcutScope.overlaps]);
     * the shipped table has none (ShortcutRegistryTest).
     */
    fun conflicts(shortcuts: List<Shortcut> = this.shortcuts): List<Pair<Shortcut, Shortcut>> =
        shortcuts.filter { it.chord != null }.groupBy { it.chord }.values.filter { it.size > 1 }.flatMap { group ->
            group.indices.flatMap { i -> (i + 1 until group.size).filter { j -> group[i].scope.overlaps(group[j].scope) }.map { j -> group[i] to group[j] } }
        }

    /** Whether two of [group], bound to one chord, can both be waiting for it ([ShortcutScope.overlaps]). */
    fun clash(group: List<Shortcut>): Boolean =
        group.indices.any { i -> (i + 1 until group.size).any { j -> group[i].scope.overlaps(group[j].scope) } }

    /**
     * The keyboard shortcut helper list (Meta+/ or the system's keyboard shortcuts screen). Each key
     * goes as the key code it is on [deviceId]'s keyboard (S-410), so the list shows its keycap.
     */
    @RequiresApi(24)
    fun keyboardShortcutGroups(resources: Resources, deviceId: Int? = null): List<KeyboardShortcutGroup> =
        ShortcutGroup.entries.mapNotNull { group ->
            val items = shortcuts.filter { it.group == group }.mapNotNull { shortcut ->
                val chord = shortcut.chord ?: return@mapNotNull null
                val code = PhysicalKeys.codeFor(chord.key.nativeKeyCode, deviceId)
                KeyboardShortcutInfo(resources.getString(shortcut.label), code, chord.androidModifiers)
            }
            if (items.isEmpty()) null else KeyboardShortcutGroup(resources.getString(group.title), items)
        }
}
