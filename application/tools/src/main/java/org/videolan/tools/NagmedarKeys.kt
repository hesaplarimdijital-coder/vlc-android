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

/** S-906: the DSP chain's settings as JSON (app.nagmedar.audio.DspSettings); absent: nothing in the chain. */
const val NAGMEDAR_DSP = "nagmedar_dsp"

/** S-907: ReplayGain: "off", "track" or "album"; the preamp and the untagged tracks' gain in dB (floats); clipping prevention. */
const val NAGMEDAR_REPLAYGAIN_MODE = "nagmedar_replaygain_mode"
const val NAGMEDAR_REPLAYGAIN_PREAMP = "nagmedar_replaygain_preamp"
const val NAGMEDAR_REPLAYGAIN_UNTAGGED = "nagmedar_replaygain_untagged"
const val NAGMEDAR_REPLAYGAIN_CLIP = "nagmedar_replaygain_clip"
const val NAGMEDAR_REPLAYGAIN_OFF = "off"
const val NAGMEDAR_REPLAYGAIN_TRACK = "track"
const val NAGMEDAR_REPLAYGAIN_ALBUM = "album"

/** S-909: the music engine's pitch in semitones, balance (-1..1) and mono downmix (floats and a boolean); the pace is the player's rate. */
const val NAGMEDAR_AUDIO_PITCH = "nagmedar_audio_pitch"
const val NAGMEDAR_AUDIO_BALANCE = "nagmedar_audio_balance"
const val NAGMEDAR_AUDIO_MONO = "nagmedar_audio_mono"

/** S-908: DSD files decoded to PCM ("pcm") or passed as DoP words ("dop", for a DAC that takes them on a bit-perfect output). */
const val NAGMEDAR_DSD_MODE = "nagmedar_dsd_mode"
const val NAGMEDAR_DSD_PCM = "pcm"
const val NAGMEDAR_DSD_DOP = "dop"

/** S-902, S-903: the music engine's output: "auto" (bit-perfect where the device takes it, else AAudio), "aaudio", "bitperfect", "usb". */
const val NAGMEDAR_MUSIC_OUTPUT = "nagmedar_music_output"
const val NAGMEDAR_OUTPUT_AUTO = "auto"
const val NAGMEDAR_OUTPUT_AAUDIO = "aaudio"
const val NAGMEDAR_OUTPUT_BIT_PERFECT = "bitperfect"
const val NAGMEDAR_OUTPUT_USB = "usb"

/** S-906: the headphone profiles imported, as JSON (app.nagmedar.audio.HeadphoneProfile.listToJson). */
const val NAGMEDAR_DSP_PROFILES = "nagmedar_dsp_profiles"

/** S-803: the music library's way in ("tracks", "artists", "albums", "genres", "composers", "years", "folders"). */
const val NAGMEDAR_MUSIC_VIEW = "nagmedar_music_view"

/** S-1001: the music table's columns, in order, comma-separated (app.nagmedar.ui.music.MusicColumn's keys). */
const val NAGMEDAR_MUSIC_COLUMNS = "nagmedar_music_columns"

/** S-1007: the alarm, as app.nagmedar.ui.sleep.Alarm.encode writes it. */
const val NAGMEDAR_ALARM = "nagmedar_alarm"

/** S-1003: lyrics are looked up on LRCLIB when the device has no synced ones (default on); what is found is kept for offline use. */
const val NAGMEDAR_LYRICS_ONLINE = "nagmedar_lyrics_online"
