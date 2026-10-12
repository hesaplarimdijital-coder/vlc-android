package org.videolan.tools

/** Preference keys of Nağmedar features that live in the classic modules. */

/** S-502: publish playback through the Media3 session instead of the legacy MediaSessionCompat. */
const val NAGMEDAR_MEDIA3_SESSION = "nagmedar_media3_session"

/** S-309: the launcher icon opens the Nağmedar interface (default) instead of the classic one. */
const val NAGMEDAR_LAUNCH_NEW_UI = "nagmedar_launch_new_ui"

/** S-309: Nağmedar's first-run flow was completed or skipped. */
const val NAGMEDAR_FIRST_RUN_DONE = "nagmedar_first_run_done"

/** S-309: the first-run flow's online sources (podcast and radio directories, no account). */
const val NAGMEDAR_ONLINE_SOURCES = "nagmedar_online_sources"

/** S-309: network shares (SMB, DLNA, WebDAV) are offered in the library. */
const val NAGMEDAR_NETWORK_SHARES = "nagmedar_network_shares"

/** S-309: folders picked through the system picker (SAF tree URIs), played without media permission. */
const val NAGMEDAR_SAF_FOLDERS = "nagmedar_saf_folders"

/** Extra of the Nağmedar activity: route of the screen to open (launcher shortcuts). */
const val NAGMEDAR_EXTRA_DESTINATION = "nagmedar_destination"

/** Intent action of StartActivity that opens the classic interface even when the new one is the default. */
const val NAGMEDAR_ACTION_CLASSIC = "nagmedar.shortcut.classic"

/** S-408: the mouse wheel seeks in the video player (Ctrl+wheel sets the volume) instead of the other way round. */
const val NAGMEDAR_WHEEL_SEEKS = "nagmedar_wheel_seeks"

/** S-408: what the middle button does in the video player: [NAGMEDAR_MIDDLE_PLAY_PAUSE], [NAGMEDAR_MIDDLE_MUTE] or [NAGMEDAR_MIDDLE_NONE]. */
const val NAGMEDAR_MIDDLE_CLICK = "nagmedar_middle_click"
const val NAGMEDAR_MIDDLE_PLAY_PAUSE = "play_pause"
const val NAGMEDAR_MIDDLE_MUTE = "mute"
const val NAGMEDAR_MIDDLE_NONE = "none"

/** S-409: where Nağmedar's desktop window last was ("left top right bottom"), to open there again. */
const val NAGMEDAR_WINDOW_BOUNDS = "nagmedar_window_bounds"

/** S-409: the video player opened from a desktop window asks to fill the screen (Android 14 and later). */
const val NAGMEDAR_PLAYER_FULLSCREEN = "nagmedar_player_fullscreen"

/** S-901: the local music library plays through Nağmedar Ses Motoru (default) rather than libVLC. */
const val NAGMEDAR_MUSIC_ENGINE = "nagmedar_music_engine"

/** S-905: the crossfade between tracks in milliseconds (0: off), its curve ("equal_power", "linear"), and whether an album's tracks keep their gapless joins instead. */
const val NAGMEDAR_CROSSFADE_MS = "nagmedar_crossfade_ms"
const val NAGMEDAR_CROSSFADE_CURVE = "nagmedar_crossfade_curve"
const val NAGMEDAR_CROSSFADE_SKIP_ALBUM = "nagmedar_crossfade_skip_album"
const val NAGMEDAR_CROSSFADE_LINEAR = "linear"
const val NAGMEDAR_CROSSFADE_EQUAL_POWER = "equal_power"
