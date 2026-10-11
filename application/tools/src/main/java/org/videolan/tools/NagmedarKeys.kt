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
