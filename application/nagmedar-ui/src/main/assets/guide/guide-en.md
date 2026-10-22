# Nağmedar user guide

<!--
  The English text of the guide (S-1504). Format: "## Title {short-name}" starts a section; "### " is a
  small heading, "- " a bullet, "1. " a step, "> " a tip, **bold**. {key:ShortcutName} writes that
  shortcut's keys as the table has them now (the name of the ShortcutId); if the user changes a key the
  guide changes with it. Spelling and terms: application/nagmedar-ui/TERIMLER.md. The Turkish text is
  guide-tr.md; the two files keep their sections in the same order under the same short names.
-->

## First run and the library {first-run}

Nağmedar builds its library from the videos and music on this device and on connected drives. No file is uploaded, and your files are not touched unless you ask.

### The first time you open it
1. **Media access:** grant the “Media files” permission, or use “Choose folder” to show only the folders you choose.
2. **Sources:** media on this device, an external drive, the folders you chose, network shares, podcasts and radio. Tick the ones you want.
3. **Audio output:** press “Play test sound”, then choose “I heard it” or “I didn't”.
4. **Notifications:** allow them for the playback controls on the lock screen and for scan and download progress.

Every step can be skipped with “Skip”. To see the steps again, go to Settings › Start-up › “Show the first-run steps again”.

### In the library
- **Home** shows the “Continue watching” shelf. **Films** and **Series** open as a grid or a dense table; series are recognised from names (S01E02, 1x02, “1. Sezon 2. Bölüm”). There are filters for 4K, HDR, Dolby Vision and Atmos.
- **Music** shows tracks, artists, albums, genres, composers, years and folders.
- If a page is empty, use “Add folder” to add a folder.
- The search box and {key:CommandPalette} search the library, the screens and the commands in one place, and the search looks inside subtitles too.

> On a phone, the “More” page of the bottom bar lists the screens that do not fit in it.

## Network sources {network}

Browse the media on SMB, FTP, SFTP, NFS, UPnP/DLNA and WebDAV servers like local files. Where: Sources › Network (on a phone, More › Network).

1. Servers that announce themselves (SMB, UPnP/DLNA, mDNS) appear by themselves under “On this network”.
2. For a server that does not, use “Add server…”: protocol, server, port, folder, display name, user name and password. If you paste an address, the form fills itself in.
3. Browse the folders like a table: Enter or a double click opens one, {key:FolderUp} goes up a folder, {key:Refresh} reads it again; there is a right-click menu too.
4. Play a file or add it to the queue. A folder can be played with its subfolders, and on the protocols VLC scans, “Add to the library” has it scanned into the library.

> If you keep a password with “Remember on this device (encrypted)”, it is encrypted with an Android Keystore key. If you do not, library scans cannot sign in after the app restarts.

> For an HTTPS or WebDAV server with a self-signed certificate, libVLC asks whether to accept the certificate (in the video player too): “View certificate”, then “Accept for 24 hours” or “Accept permanently”; if in doubt, “Cancel”.

## The video player {video}

Films and series play with libVLC 4. On a touch screen you get VLC's gestures; with a mouse (DeX) you get the desktop control bar and a side panel. Touch the screen with a finger and the video goes back to the classic controls.

### Touch gestures
- Swiping up or down on the left of the screen changes the brightness, on the right the volume.
- Swiping sideways seeks backward or forward.
- A double tap on the left or right edge jumps 10 s back or forward; a double tap in the middle plays or pauses.
- Tapping and holding plays faster; pinching with two fingers zooms the picture.
- You choose which gestures are on under Settings › “All settings (classic interface)”.

### Mouse and keyboard
- The control bar has a timeline with chapter marks, frame previews, A-B loop, and speed, volume and track menus. The mouse wheel changes the volume, and seeks while Ctrl is held; you can swap the two in Settings › Input.
- {key:PlayPause} plays or pauses, {key:Fullscreen} goes full screen, {key:JumpBack} and {key:JumpForward} jump 10 s, {key:JumpToTime} takes you to any moment.
- The side panel: {key:Effects} opens Effects, {key:MediaInfo} Info, {key:Bookmarks} Bookmarks; Subtitles and Sync are tabs there too.
- {key:Snapshot} takes a screenshot (Settings › Screenshots: format, folder, HDR). {key:AddBookmark} bookmarks this moment. Picture in picture and refresh-rate matching are in the player too.
- {key:Record} records what plays as it comes; see Radio.

> A film's subtitle delay, subtitle encoding and picture effects are remembered for that film; open it again and there is nothing to set.

## Effects and filters {effects}

Brightness, contrast, HDR, rotation, a logo and more: the Effects tab of the player panel ({key:Effects}) adjusts the picture while it plays.

1. **Picture:** brightness, contrast, saturation, hue and gamma; “Reset” takes them all back.
2. **Aspect ratio and crop:** the aspect ratio, “Detect black bars”, 90° rotation and flipping.
3. **HDR and scaling:** pass HDR to the screen or tone-map it to SDR; choose the scaler.
4. **Advanced video:** a logo, text over the video (with the time or date if you like), rotation by any angle, and anaglyph 3D for side-by-side 3D films.

> The picture settings are remembered for the film; the Advanced video settings apply to every film until the app is closed. Rotating by an angle and 3D load the processor, and the panel says so.

## Subtitles {subtitles}

Add subtitles from a file, a folder or the internet; clean them up and correct them with the tools. Where: the Subtitles tab of the player panel and the player's subtitle menu.

### Adding subtitles
1. From a file: {key:AddSubtitleFile}, or drop the subtitle file on the video. If subtitles are found in the film's folder, it asks “Which one should be shown?”.
2. From the internet: “Search for subtitles”. Check the film's name; with “The file's fingerprint first” on, results that “fit this file” come first. “Download” saves the subtitle beside the film (in the app's folder if that cannot be written).
3. Providers: in Settings › Subtitles, OpenSubtitles.com and a subtitle folder of your choice are turned on and off and put in order; a search runs in all of them at once and the results join in one list.
4. If the letters are wrong, use “Subtitle encoding…” and choose Automatic or “Turkish · Windows-1254”; it is remembered for the film.

### Subtitle tools
{key:SubtitleTools} opens a window with four tabs: **Clean** (removes notes and speaker labels meant for the hard of hearing, with a before and after preview), **Frame rate** (converts or shifts), **Lines** (splits and merges lines, takes the start and end from the playback position, checks for errors and overlaps) and **Translate**. SRT and WebVTT are read and written; ASS/SSA keeps its styles. The result is always a new file, and the original subtitle is not touched.

The appearance (font, size, outline, shadow, background, colour, height; the “Thin”, “Yellow” and “Boxed” presets) is in the Appearance section of the Subtitles tab.

> OpenSubtitles has a daily download allowance (“OpenSubtitles quota: 4/5”). If this build has no OpenSubtitles key, online search is off and says so; the folder provider still works.

## Generating, translating and dual subtitles {subtitles-ai}

Make subtitles from speech, translate a subtitle, show two subtitles together. All three run on the device; your speech, your film and your subtitles are not sent to the internet.

### Generating subtitles with Whisper
1. Press {key:GenerateSubtitles}, or “Generate subtitles…” in the panel.
2. Choose the audio track and the language (Automatic, Turkish, English); tick “Translate into English” if you like.
3. Choose the model: base is the fastest and roughest, small is recommended, large-v3-turbo is the most accurate but can take longer than the film. A model is downloaded once, with its size told and your approval, the first time it is needed.
4. Press “Generate”. The job runs in the background; follow it from its notification or cancel it there.

The subtitle is written beside the film as `film.tr.whisper.srt`; if the folder cannot be written, it goes to the app's folder and “Save as…” is offered. You can delete the models in Settings › Subtitle generation. Whisper runs on arm64 phones and tablets.

### Translating subtitles
“Translate subtitles…” (or the Translate tab of the subtitle tools) translates Turkish ↔ English. A translation model (English → Turkish 475 MB, Turkish → English 81 MB) is downloaded with its size told and your approval, and deleted in Settings › Subtitles. The result is a new file (`film.tr.srt`) and is shown together with the original as secondary subtitles.

### Dual subtitles
{key:SecondarySubtitles} turns the secondary subtitles on or off; you choose the second track in the “Secondary subtitles” part of the Subtitles tab. It suits learning a language: read the original and the translation together.

## Sync and delay {sync}

When the subtitles or the sound drift, there are three ways: small steps, the sync assistant and the output device's latency profile.

1. Move the subtitles in small steps with {key:SubtitleDelayDown} and {key:SubtitleDelayUp}; the sound with {key:AudioDelayDown} and {key:AudioDelayUp}.
2. If you do not know how far it is off, use the **sync assistant**: when you hear a line, press {key:SyncMarkAudio}; when it shows in the subtitles, press {key:SyncMarkSubtitle}; then press {key:SyncApply}. If you do not like the result, {key:SyncUndo}.
3. The **Sync** tab of the panel shows the audio and subtitle delay, the subtitle speed and refresh-rate matching (such as 23.976 Hz on HDMI). A delay you set is remembered for that file.

### Latency profiles
A Bluetooth headset, an AV receiver and a USB DAC each play the sound with a different delay. In Settings › Latency profiles, record a device's delay by hand or by measuring it with the microphone (five short test tones are played); when the device becomes the output, its profile is applied by itself and a short notice is shown. The profile takes the place of the single Bluetooth delay of VLC's classic player; the two are not added. Playback paused for a measurement goes on when it ends, even if you closed the settings.

> Measure in a quiet room. If the repeats differ by more than 20 ms, the measurement is inconsistent and says so.

## The music engine and sound quality {music-engine}

Local music files are played by Nağmedar's own **music engine**: an FFmpeg-decoding, 64-bit floating-point engine. Where: Settings › Music. If you turn the engine off, music plays with libVLC.

### Bit-perfect and USB DAC
- Settings › Music › Output: Automatic, AAudio, bit-perfect or USB DAC. Automatic uses bit-perfect if the device accepts it (Android 14 and later; USB DAC or HDMI) and AAudio if not.
- With bit-perfect, samples go through unmixed and unresampled. With USB DAC chosen, the DAC is driven directly and the DAC's own volume control is the app's volume; if the cable is pulled, the output falls back to another one.
- The **signal path** panel beside the chain on the Equaliser page shows every stage from file to output; if the output is not bit-perfect, it gives the first reason.

### Gapless, crossfade, ReplayGain
- The tracks of an album follow each other with no gap or click: the next track is decoded ahead and taken over sample-accurately.
- Crossfade (2, 4, 8 s; “Equal power” or “Linear”) is in Settings › Music; it is off by itself between consecutive tracks of an album.
- ReplayGain brings tracks to the same loudness: track or album gain, preamp, clipping prevention. “Scan the library” measures albums and writes the ReplayGain tags into the files when you start it.

### Equaliser and tests
- {key:Equalizer} (Audio › Equaliser…): a 20-band graphic equaliser, a 10-band parametric equaliser, an AutoEq headphone profile and a limiter. “Bypass the chain” turns all of it off with one key and makes the output bit-perfect.
- The **A/B test** (on the Equaliser page; {key:AbSwitch} switches the side) compares the sound with the chain (A) and without it (B) at the same level; in the blind test X is A or B at random each round and the answers are revealed at the end.
- Speed (pitch kept), pitch, balance and mono are set separately; DSD (DSF, DFF) and tracker modules (MOD, XM, IT…) play too.

> Speed, pitch, balance, mono, crossfade and the DSP chain change the samples; while they are set, the output is not bit-perfect.

## The music library and lists {music-library}

On the Music page, filter, sort and make lists. A large window has three panes (groups, track table, now playing); on a phone, touching the mini player opens the full-screen player (swipe the cover left for the next track, right for the previous).

- **Filters and columns:** bit depth, sample rate and lossy-format filters; you choose the table's columns from the right-click menu of its header.
- **Up next** ({key:PlayQueue}): “Play next” in a track's menu puts it right after the one playing, “Add to queue” at the end. Drag by the handle to reorder, or use Alt+↑/↓.
- **Playlists:** each list is a tab. M3U, M3U8, PLS and XSPF are imported and exported; files of lists made on another computer are found from their names.
- **Smart list:** make a list that fills itself by rules (artist, genre, rating, play count, date added…) with Playlists › Smart list; change it with “Edit rules…”.
- **CUE sheet:** a single-file album with a CUE sheet (.cue) of the same name beside it shows in the library with its tracks and plays sample-accurately; there is nothing to do.
- **Rating:** give 1–5 stars in the table, in the now playing pane or with “Rate…”. A track counts as played when half of it or four minutes has been heard, and as skipped if you move on before.

## Converter, sleep timer and alarm {convert-sleep}

Write music files in another format as new files; let a sleep timer stop the music by itself, or wake to music or a radio station with the alarm.

### Converting music
1. Select tracks (or press “Convert…” on the heading of an album, an artist or a folder).
2. Press “Convert…” and choose the format: Opus (128, 160, 192 kbps), MP3 (V0, 320 kbps) or AAC (256 kbps, M4A).
3. Choose the destination folder once with the system's file chooser. The new files are written as “Artist/Album/03 Title” or into one folder; the original files are not touched.
4. A multichannel source (5.1, 7.1) keeps its channel layout in Opus and AAC; to get two channels, choose “Mix down to stereo” under “Channels”. MP3 is always stereo. The tags, the cover and synced lyrics go along to the new file.

Progress and Cancel show in the notification and in the strip above the library. The job waits when the battery is low or the device is hot. Converting a lossy track again lowers its quality a little more.

### Sleep timer and alarm
- {key:SleepTimer}: from 15 min to 2 hours or until the end of the track; the volume fades over the last 30 s, “+15 min” extends it.
- Settings › Alarm: a time, days, and the last played music, a playlist or an internet radio stream; the volume rises from silence over the time you choose. It is set as a system alarm and survives a restart.

## Lyrics, the lyrics editor and scrobbling {lyrics}

Follow a track's lyrics, write your own synced lyrics and send what you listen to to Last.fm or ListenBrainz.

### Lyrics
{key:Lyrics} opens the playing track's lyrics over the whole window (the now playing pane and the phone's player have a “Lyrics” button too). Lyrics come in turn from the file's own tags, from the .lrc file beside it and, last, from LRCLIB; synced lyrics come before plain ones. The line being sung is highlighted, and clicking a line jumps the track there.

If the lyrics are late or early, {key:LyricsEarlier} and {key:LyricsLater} shift them in 0.1 s steps; the delay is kept per track.

### The lyrics editor
Open it with “Edit” in the lyrics view (“Time it” for a track with no synced lyrics).
1. While the track plays, {key:LrcStamp} stamps the current position on the line; {key:LrcUnstamp} takes back the last stamp.
2. {key:LrcWordMode} switches between line and word modes; ↑ and ↓ choose a line, Space plays and pauses.
3. Write the lyrics “Beside the file (.lrc)” or “To the tag”. The earlier lyrics are never deleted.

### Scrobbling
In Settings › Scrobbling sign in with Last.fm (user name and password; the password is not kept) or ListenBrainz (user token). A track counts when half of it or four minutes has been heard; video, podcasts and streams are not sent. Offline, scrobbles wait on the device and are sent when the network is back.

## Podcasts {podcasts}

The Podcasts screen keeps your subscriptions, new episodes and downloads. No account is needed.

1. **Finding:** type in the search box ({key:PodcastSearch}); results come from Podcast Index, and from iTunes if they fall short. “Subscribe” adds one with a single tap (there is an Undo bar). If you have an RSS address, use “Add by RSS address…” in the three-dot menu.
2. **Moving:** “Import OPML…” brings in the list of another app; AntennaPod, Pocket Casts, Podcast Addict and Overcast read the same file. “Export OPML…” gives you your list of subscriptions.
3. **Listening:** tap an episode; each episode resumes where you stopped and counts as played near the end or at 95%. An episode page has chapters, a transcript (it follows playback and can be searched), notes and the download. {key:PodcastNextChapter} and {key:PodcastPreviousChapter} move between chapters.
4. **Downloading:** “Download” puts the episode on the device. In Settings › Podcasts you set how often to refresh (every 1, 3, 6, 12 or 24 hours), Wi-Fi only, notifications, the storage limit and the days after which played episodes are deleted. Each podcast's own settings have automatic download, speed (0.5–3×) and skipping the intro and the outro.

> Podcast subscriptions are not part of a backup; export OPML instead.

## Radio {radio}

The Radio screen finds internet radio stations in the Radio Browser directory. Its tabs: Favourites, Recent, Search, Imported.

1. In the **Search** tab, search by name ({key:RadioSearch} goes there); narrow by country, language, tag, codec and minimum bitrate, and sort by votes, most listened, name and bitrate.
2. Play a station with Enter; in its right-click menu you can add it to favourites, “Vote for this station” (once a day) and copy the stream address.
3. Bring in M3U, M3U8 or PLS files with “Import a file…”; those already saved are counted apart.
4. While one plays, the strip at the top of the page shows the station's logo, the “Artist - Track” title being broadcast and the play/pause button; the notification and the lock screen show it too.

### Recording and schedules
- **Recording:** {key:Record} (also in the screenshot button's menu in the video player, and in the options of the classic players) records what plays as it comes; it suits streams, radio and TV best. Local files played by the music engine are not recorded. A recording is written to the app's space first and, when it ends, put into the folder you chose in Settings › Recording.
- **Schedules:** Settings › Alarm plays a radio station as a wake-up alarm at the time and on the days you choose; the new episodes of podcasts are refreshed in the background by themselves.

## Casting (Chromecast and DLNA) {cast}

Send video or music to a Chromecast or a DLNA device (a TV, a receiver, a speaker) on the same network; libVLC does the sending.

1. Have the phone and the device on the same network.
2. Press the Cast button in the bottom bar, in the mini player, in the now playing pane or in the player; {key:Cast} or the Playback menu opens it too.
3. Choose the device; found devices are updated as they come and go. “This device” ends the cast.
4. While casting, the player's controls drive the remote device.
5. If you stop casting a film from the mini player, the notification or the list, the film goes on in the video player on this device where it was; with the app in the background it is paused there, and opening the player goes on from that point.

> If the device does not show in the list, check that both are on the same Wi-Fi (not a guest network), the TV is on and DLNA or media sharing is on, the Chromecast is awake, and the router has multicast (mDNS, UPnP) on and client isolation off. A VPN on this device can hide the local network.

## Remote access from a browser {remote-access}

Look at your library, control playback and send files from the browser of a computer or phone on this network. The page comes from the server on this device; it does not pass through the internet.

1. Turn on Settings › Remote access › “Open from a browser”; the server starts.
2. “Addresses and connections…” shows the page's address; open it in the browser.
3. The browser warns once, because this device makes its certificate itself: check the address and go on.
4. Enter the 6-digit one-time code the device shows in a notification (valid for 60 s). Every new browser needs a new code.

Which parts (videos, music, playlists, search), file and network browsers, history, playback control and log files are open to the browser is chosen on the Settings › Remote access page.

> The connection is always HTTPS and there is no setting to turn that off. Turn the switch off when you are not using it; open the log files only while you are chasing a problem.

## DeX, keyboard and mouse {desktop}

In a wide window (DeX, an external display) Nağmedar works like a desktop app: a menu bar on top, a sidebar on the left, the player bar at the bottom.

- **Menu bar:** the Media, Playback, Audio, Video, Subtitle, Tools, View and Help menus, each item with its keys beside it. Pressing Alt alone, or F10, hands the keyboard to the menu bar. The Help menu has this guide and the shortcut card.
- **Command palette** ({key:CommandPalette}): commands, screens, appearance settings and the library's films and tracks are searched in one box.
- **Mouse:** a right click (or the Menu key) opens context menus; Ctrl or Shift with a click selects several items; dragging on an empty area selects with a rubber band. The mouse's back and forward buttons move between screens.
- **Drag and drop:** dropping a file from outside plays it, dropping a subtitle on the video loads it, dropping media on the player bar adds it to the queue.
- **Windows:** {key:ToggleSidebar} collapses the sidebar, {key:OpenInNewWindow} opens the same screen in a new window. The window's place and size are remembered.
- **Shortcuts:** the shortcut card ({key:ShortcutList}) lists them all; to change one, click it in Settings › Keyboard shortcuts and press the new keys. There are VLC and mpv presets, export and import, and a keyboard map that shows where the keys are on a Turkish Q keyboard.
- Settings › Input: interface density, tooltip delay, the player opening full screen, the mouse wheel and the middle click.

> Open the guide with {key:UserGuide}. In the full-screen video player too, {key:UserGuide}, {key:ShortcutList}, {key:CommandPalette} and {key:SleepTimer} open their windows over the film, which goes on underneath; {key:Settings} opens the main window.

## Backup and restore {backup}

Write your settings, lists and ratings to one ZIP file; bring them back on another device or after a reinstall. Where: Settings › Backup.

### Making a backup
1. Press “Back up…” and choose the file's place in the system's file chooser (`nagmedar-backup-<date>.zip`).
2. The file holds settings, shortcuts, playlists, smart lists, ratings and play counts, your own lyrics and lyrics delays, per-file settings, the covers you chose by hand and latency profiles.

### Restoring
1. Press “Restore…” and choose the backup file. When it was made and by which version, and its parts with their counts, are shown.
2. Tick the parts you want back and press “Restore”.
3. Nothing is deleted: settings, lists and profiles of the same name are replaced by the backup's, and ratings and play counts are merged. The files of playlists are found from their names in the library; those that are not found are counted, and restoring again after the scan is enough.

> A backup does not hold your media files, your account sign-ins, this device's folder permissions, podcast subscriptions or radio favourites. Export OPML for podcasts.

## Privacy: what goes online? {privacy}

Nağmedar has no account, no ads and no tracking. Scanning happens on the device and your media stays on it. The network is used only when you use the features below; requests carry Nağmedar's name, and neither your name nor your e-mail address is in any of them.

- **Subtitle search:** OpenSubtitles.com gets the film's name and the file's fingerprint (you can turn the provider off in Settings › Subtitles).
- **Lyrics:** if there are no synced lyrics in the file or beside it, the track's title, artist, album and length go to lrclib.net (turn it off in Settings › Lyrics). Uploading lyrics to LRCLIB happens only if you ask for it.
- **Covers:** if there is no cover in the file or the folder, the album's name and artist go to MusicBrainz, and the cover comes from the Cover Art Archive (turn it off in Settings › Covers).
- **Podcasts and radio:** the word you search for goes to Podcast Index, iTunes or Radio Browser; the shows you subscribe to are read from their own servers. Radio Browser is told of each play as a “click”, and of your vote if you vote.
- **Scrobbling:** only if you sign in, the tracks you play go to Last.fm and ListenBrainz.
- **Models:** the Whisper and translation models are downloaded once; after that, speech, films and subtitles do not leave the device.
- **Inside the same network:** casting and remote access talk only to devices on this network.
- **Passwords and tokens:** server passwords, the ListenBrainz token and the Last.fm session key are encrypted with an Android Keystore key; the Last.fm password is never kept.

> To see what is on now, look at the Subtitles, Lyrics, Covers and Scrobbling sections of Settings.
