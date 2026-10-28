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
2. **Sources:** media on this device, an external drive, the folders you chose, network shares, podcasts and radio. Tick the ones you want. Below them you can also choose separate folders for Films, Series, Videos and Music; a section with none shows all of the library. You can change all of it later in Settings › Library.
3. **Audio output:** press “Play test sound”, then choose “I heard it” or “I didn't”.
4. **Notifications:** allow them for the playback controls on the lock screen and for scan and download progress.

Every step can be skipped with “Skip”. To see the steps again, go to Settings › Start-up › “Show the first-run steps again”.

### In the library
- **Home** shows the “Continue watching” shelf. **Films** and **Series** open as a grid, as posters or as a list; series are recognised from names (S01E02, 1x02, “1. Sezon 2. Bölüm”). More in “Films, series and videos”.
- **Videos** gathers the videos you recorded with this device: the camera's, screen recordings and the videos of messaging apps.
- **Music** shows tracks, artists, albums, genres, composers, years and folders.
- The search box and {key:CommandPalette} search the library, the screens and the commands in one place, and the search looks inside subtitles too.

### Each section's folders
Films, Series, Videos and Music can each show their own folders, subfolders included. A section without folders shows everything the library has for it. Where: Settings › Library, or “Add folder” in a section's header and on its empty page.
1. Press the section's “Add folder” button.
2. Choose a folder with “Choose a folder…” in the system's folder chooser, or tap one of the suggested folders (one of an SD card's top folders, or the whole of a drive).
3. To leave a subfolder out, add it too and choose “Leave out” in its menu; “Remove” takes a folder off the list.

> Films and Series without folders of their own leave out the folders chosen for Videos. Videos also shows every video that Films and Series do not, such as a file in a Series folder that is not named like an episode, so no video is lost. The folders belong to this device; they are not part of a backup.

> On a phone, the “More” page of the bottom bar lists the screens that do not fit in it.

## Films, series and videos {films}

Films and Series show with posters, overviews and ratings; for a series you see at a glance which episodes you have watched. Videos gathers the videos you recorded with this device.

### Views
- Choose the view with the buttons in the header: **Grid** (cards), **Poster** (2:3 posters; in Films a film's backdrop on top), **List** (poster thumbnail, year, IMDb and Rotten Tomatoes ratings, length, video and audio; sorted by its columns) and, in Films, **Folders**. The choice is remembered per section; on a phone the views are in one menu button.
- In Films, the 4K, HDR, Dolby Vision, Atmos, Turkish subtitles and Unwatched filters narrow the list.
- **Videos** shows the resolution, length, date taken and size on its cards, and sorts by date, title, size or duration. The videos of the camera, of screen recordings and of messaging apps show here, not in Films, and so does every video that Films and Series do not show. This page deletes no file.

### Film information
Each film and series is looked up once on TMDB by the title and year its file name gives (its folder's name when the file name says nothing, such as video.mp4): the poster, backdrop, overview, year, genres, length, cast and TMDB rating come from there; OMDb gives the IMDb and Rotten Tomatoes ratings. Lookups run in the background, by default on Wi-Fi only; a refresh or search you ask for goes ahead on any connection and may use mobile data. The library works without them, and offline too.
- “Refresh information” in a film's details or menu looks it up again.
- If the wrong film comes up, use “Wrong film? Search…” (for a series “Wrong series? Search…”), correct the title, press “Search” and choose the right one; your choice is kept when it is refreshed too.
- Settings › Film information: “Look up films and series” turns the feature on or off; “Only on Wi-Fi”, “Language of the overviews” (Türkçe or English), the count of what is kept and “Clear cache” (deletes the kept information and images) are there too.

> If this build has no TMDB key, film information is off; without an OMDb key the IMDb and Rotten Tomatoes ratings are not shown. Settings › Film information says both.

### Watched episodes
- Each episode's row has a mark: watched (a tick), partly watched (a ring filled as far as you watched) or unwatched (an empty ring). The season selector shows the season's progress (“Season 1 · 3/10”), a series' card the number of unwatched episodes.
- The menus of an episode, a season, a series and a film have “Mark as watched” and “Mark as unwatched”.

## Network sources {network}

Browse the media on SMB, FTP, SFTP, NFS, UPnP/DLNA and WebDAV servers like local files. Where: Sources › Network (on a phone, More › Network).

1. Servers that announce themselves (SMB, UPnP/DLNA, mDNS) appear by themselves under “On this network”.
2. For a server that does not, use “Add server…”: protocol, server, port, folder, display name, user name and password. If you paste an address, the form fills itself in.
3. Browse the folders like a table: Enter or a double click opens one, {key:FolderUp} goes up a folder, {key:Refresh} reads it again; there is a right-click menu too.
4. Play a file or add it to the queue. A folder can be played with its subfolders, and on the protocols VLC scans, “Add to the library” has it scanned into the library.

> If you keep a password with “Remember on this device (encrypted)”, it is encrypted with an Android Keystore key. If you do not, library scans cannot sign in after the app restarts.

> For an HTTPS or WebDAV server with a self-signed certificate, libVLC asks whether to accept the certificate (in the video player too): “View certificate”, then “Accept for 24 hours” or “Accept permanently”; if in doubt, “Cancel”.

## The video player {video}

Films and series play with libVLC 4. Touch the screen with a finger or a pen and the touch controls come; move a mouse (DeX) and you get the desktop control bar and a side panel. Both are Nağmedar's controls.

### Touch controls and gestures
- A tap shows or hides the controls; while the film plays they hide by themselves after a few seconds. On top: back, the title, cast, picture in picture, the lock and the menu; in the middle: previous, 10 s back, play or pause, 10 s forward and next; at the bottom: the timeline (with frame previews while you drag) and subtitles, audio track, speed, picture size, screen orientation and effects.
- Swiping up or down on the left of the screen changes the brightness, on the right the volume.
- Swiping sideways shows where it will go, and seeks there when you let go.
- A double tap on the left or right edge jumps 10 s back or forward, and each tap that follows adds 10 s; a double tap in the middle plays or pauses.
- Pressing and holding plays fast (2×) for as long as you hold; spreading two fingers fills the screen with the picture, pinching fits it again.
- “Lock the screen” turns off taps and gestures and holds the orientation; the lock button that comes when you tap the screen unlocks it.
- The menu has jump to time, chapters, bookmarks, A-B loop, audio and subtitle sync, media information, the equaliser, the sleep timer, audio only, screenshot, recording, share, convert and stream on the network.
- You choose which gestures are on under Settings › All settings › Video › Player controls.

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

Add subtitles from a file, a folder or the internet, extract the ones inside a film into a file; clean them up and correct them with the tools. Where: the Subtitles tab of the player panel and the player's subtitle menu.

### Adding subtitles
1. From a file: {key:AddSubtitleFile}, or drop the subtitle file on the video. If subtitles are found in the film's folder, it asks “Which one should be shown?”.
2. From the internet: “Search for subtitles”. Check the film's name; with “The file's fingerprint first” on, results that “fit this file” come first. “Download” saves the subtitle beside the film (in the app's folder if that cannot be written).
3. Providers: in Settings › Subtitles, OpenSubtitles.com and a subtitle folder of your choice are turned on and off and put in order; a search runs in all of them at once and the results join in one list.
4. If the letters are wrong, use “Subtitle encoding…” and choose Automatic or “Turkish · Windows-1254”; it is remembered for the film.

### Extracting embedded subtitles into a file
To correct or translate the text subtitles inside a film (MKV, MP4, TS, AVI) with the tools, extract them into a file first.
1. Use “Extract embedded subtitles…”: it is in the player's subtitle menu and Subtitles tab, in a film's menu in the library and in the “Embedded subtitles” section of the subtitle tools.
2. Choose one of the film's subtitle tracks; each one shows its language, title and format. Picture subtitles (PGS, VobSub, DVB) need turning into text (OCR), so they are not extracted.
3. The job runs in the background, with progress and Cancel in its notification. The result is written as a new file beside the film (film.tr.srt; with ASS/SSA styles film.en.ass). If the folder cannot be written, it goes to the app's folder, and from there “Save elsewhere…”. The film does not change, and no file is written over.
4. The subtitle tools and the translation go on with the extracted file at once; the film finds it the next time it opens too.

### Subtitle tools
{key:SubtitleTools} opens a window with four tabs: **Clean** (removes notes and speaker labels meant for the hard of hearing, with a before and after preview), **Frame rate** (converts or shifts), **Lines** (splits and merges lines, takes the start and end from the playback position, checks for errors and overlaps) and **Translate**. SRT and WebVTT are read and written; ASS/SSA keeps its styles. The result is always a new file, and the original subtitle is not touched. The window's “Embedded subtitles” section extracts one of the subtitles inside the film into a file.

The appearance (font, size, outline, shadow, background, colour, height; the “Thin”, “Yellow” and “Boxed” presets) is in the Appearance section of the Subtitles tab.

> OpenSubtitles has a daily download allowance (“OpenSubtitles quota: 4/5”). If this build has no OpenSubtitles key, online search is off and says so; the folder provider still works.

## Generating, translating and dual subtitles {subtitles-ai}

Make subtitles from speech, translate a subtitle, show two subtitles together. All three run on the device; your speech, your film and your subtitles are not sent to the internet.

### Generating subtitles with Whisper
1. Press {key:GenerateSubtitles}, or “Generate subtitles…” in the panel.
2. Choose the audio track and the language (Automatic, Turkish, English); tick “Translate into English” if you like.
3. Choose the model: base is the fastest and roughest, small is recommended, large-v3-turbo is the most accurate but can take longer than the film. A model is downloaded once, with its size told and your approval, the first time it is needed.
4. Press “Generate”. The job runs in the background; follow it from its notification or cancel it there.

The subtitle is written beside the film as film.tr.whisper.srt; if the folder cannot be written, it goes to the app's folder and “Save as…” is offered. You can delete the models in Settings › Subtitle generation or in Tools › Downloads. Whisper runs on arm64 phones and tablets.

### Translating subtitles
“Translate subtitles…” (or the Translate tab of the subtitle tools) translates Turkish ↔ English; extract a subtitle inside a film into a file first (see Subtitles). A translation model (English → Turkish 475 MB, Turkish → English 81 MB) is downloaded with its size told and your approval, and deleted in Settings › Subtitles. The result is a new file (film.tr.srt) and is shown together with the original as secondary subtitles. If Android's time limit stops a translation, it does not start again by itself; the window and a notification say so, and started again it begins from the start.

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
- ReplayGain brings tracks to the same loudness: track or album gain, preamp, clipping prevention. “Scan the library” measures albums and writes the ReplayGain tags into the files when you start it; as with lyrics, a copy of each file is kept while it is written. A CUE sheet's tracks are skipped: they all share one file.

### Equaliser and tests
- {key:Equalizer} (Audio › Equaliser…): a 20-band graphic equaliser, a 10-band parametric equaliser, an AutoEq headphone profile and a limiter. “Bypass the chain” turns all of it off with one key and makes the output bit-perfect.
- The **A/B test** (on the Equaliser page; {key:AbSwitch} switches the side) compares the sound with the chain (A) and without it (B) at the same level; in the blind test X is A or B at random each round and the answers are revealed at the end.
- Speed (pitch kept), pitch, balance and mono are set separately; DSD (DSF, DFF) and tracker modules (MOD, XM, IT…) play too.

> Speed, pitch, balance, mono, crossfade and the DSP chain change the samples; while they are set, the output is not bit-perfect.

## The music library and lists {music-library}

On the Music page, filter, sort and make lists. A large window has three panes (groups, track table, now playing); on a phone, touching the mini player opens the full-screen player (swipe the cover left for the next track; swiping it right works like Previous).

- **Toolbar:** Artists, Albums, Genres… on the left (a menu showing the current view in a narrow window and on a phone), Play, Shuffle, Columns and Filter on the right. You choose the table's columns with “Columns” or from the right-click menu of its header.
- **Filters:** “Filter” opens the bit depth (16, 24, 32 bit, DSD, lossy) and sample rate filters in a panel; while a filter is on, a dot shows on the button, and “Clear the filters” turns them all off.
- **Up next** ({key:PlayQueue}): “Play next” in a track's menu puts it right after the one playing, “Add to queue” at the end. Drag by the handle to reorder, or use Alt+↑/↓.
- **Playlists:** every list, smart lists too, shows as a card: cover, name, track count, length and last play. Search the lists; sort them by name, last play or creation. Pressing the open list's name changes it in place (Enter keeps it, Esc leaves it); its menu has rename, duplicate, Play next, Add to queue, export and delete.
- **Import and export:** M3U, M3U8, PLS and XSPF are imported and exported; files of lists made on another computer are found from their names.
- **Smart list:** make a list that fills itself by rules (artist, genre, rating, play count, date added…) with Playlists › Smart list; change it with “Edit rules…”.
- **CUE sheet:** a single-file album with a CUE sheet (.cue) of the same name beside it shows in the library with its tracks and plays sample-accurately; there is nothing to do.
- **Rating:** give 1–5 stars in the table, in the now playing pane or with “Rate…”. A track counts as played when half of it or four minutes has been heard, and as skipped if you move on before.

### While it plays
- Behind the progress bar (the now playing pane, the phone's player, the bar at the bottom) the track's waveform shows faintly, with the elapsed and remaining time. While you drag the thumb a time bubble shows, and letting go seeks there; with the bar focused, ← and → seek 5 s, Home and End go to the start and the end.
- The playback buttons: shuffle ({key:Shuffle}), previous, play or pause in a filled circle, next and repeat ({key:Repeat}). Shuffle or repeat, when on, shows in the accent colour with a dot under it; the repeat button turns in turn into “Repeat all” and “Repeat this one”, and the second has a “1” on it.
- The Previous button and {key:Previous} first take the track back to its start; pressed within its first 3 seconds, or within 3 seconds of that restart, they go to the previous track. The notification, the lock screen, the widget and Bluetooth follow the same rule.
- The artist and album names are links: the artist opens that artist's tracks in Artists, the album that album's tracks in Albums.
- The volume (the bar's slider, {key:VolumeUp}, {key:VolumeDown}, {key:Mute}) is kept: it stays the same for the next track, for a film and when you open the app again; the first time it is 100%.

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
2. {key:LrcWordMode} switches between line and word modes; {key:LrcLineUp} and {key:LrcLineDown} choose a line, {key:PlayPause} plays and pauses.
3. Write the lyrics “Beside the file (.lrc)” or “To the tag”. The earlier lyrics are never deleted.

> While it writes to the tag, Nağmedar keeps a copy of the file: the written file is read back, and if its audio does not come out the same, the copy is put back; if the app closes while writing, this check happens at the next start. A file changed elsewhere meanwhile, or not found for a week, is left alone: its copy goes into “Recovered” in the app's folder on the storage, and a notice says where. If there is no room for the copy, the lyrics are not written and you are told why.

### Scrobbling
In Settings › Scrobbling sign in with Last.fm (user name and password; the password is not kept) or ListenBrainz (user token). A track counts when half of it or four minutes has been heard; video, podcasts and streams are not sent. Offline, scrobbles wait on the device and are sent when the network is back.

## Podcasts {podcasts}

The Podcasts screen keeps your subscriptions, new episodes and downloads. No account is needed.

1. **Finding:** type in the search box ({key:PodcastSearch}); results come from Podcast Index, and from iTunes if they fall short. “Subscribe” adds one with a single tap (there is an Undo bar). If you have an RSS address, use “Add by RSS address…” in the three-dot menu.
2. **Moving:** “Import OPML…” brings in the list of another app; AntennaPod, Pocket Casts, Podcast Addict and Overcast read the same file. “Export OPML…” gives you your list of subscriptions.
3. **Listening:** press the Play button on an episode's row; touching the row itself opens the episode page. Each episode resumes where you stopped (or after the intro) and counts as played near the end or at 95%; you are not asked “Resume where you stopped?”. An episode page has chapters, a transcript (it follows playback and can be searched), notes and the download.
4. **Chapters:** the playing episode's chapters are marked on the bar at the bottom, in the mini player, in the now playing pane, in the phone's player and, for a video episode, on the video player's timeline; pointing at a mark shows the chapter's name. The “Previous chapter” and “Next chapter” buttons and the list of chapters are there; {key:PodcastPreviousChapter} and {key:PodcastNextChapter} move between chapters too.
5. **Downloading:** “Download” puts the episode on the device; Tools › Downloads shows the downloads running, waiting, done and failed. In Settings › Podcasts you set how often to refresh (every 1, 3, 6, 12 or 24 hours), Wi-Fi only, notifications, the storage limit and the days after which played episodes are deleted.
6. **Show settings:** each podcast's “Podcast settings” window has automatic download, speed (0.5–3×), skipping the intro and the outro, and volume boost. Volume boost (Off, Light, Strong) plays a quietly recorded show's episodes louder than your volume; other media play as usual. While a boosted episode plays, the volume slider and keys show and change your own volume, and the episode stays boosted over it.

> Podcast subscriptions are not part of a backup; export OPML instead.

## Radio {radio}

The Radio screen finds internet radio stations in the Radio Browser directory and records them. Its tabs: Favourites, Recent, Search, Imported and Recordings.

1. In the **Search** tab, search by name ({key:RadioSearch} goes there); filter by country, language, tag, codec and minimum bitrate, and sort by votes, most listened, name and bitrate.
2. Play a station with Enter; in its right-click menu you can add it to favourites, record it, “Vote for this station” (once a day) and copy the stream address.
3. Bring in M3U, M3U8 or PLS files with “Import a file…”; those already saved are counted apart.
4. While one plays, the strip at the top of the page shows the station's logo, the “Artist - Track” title being broadcast and the play/pause button; the notification and the lock screen show it too.

### Radio recordings
- **Recording:** {key:RadioRecord} or “Record” in a station's menu records the station that plays, and {key:RadioRecord} again stops it. The recording is cut into a new file at each new title the station announces (“Artist - Title.mp3”); the announcement is not sample-accurate, so a cut can be a second or two off. At most 3 stations are recorded at once.
- **HLS and one file:** HLS stations (.m3u8; TRT's, for example) and streams that send no titles become one file per session. Encrypted HLS streams are not recorded.
- **The Recordings tab** ({key:RadioRecordings}): the recordings running (“Stop recording”), the scheduled recordings and the recorded tracks, by station and day. Play, share, rename or delete a track (deleting can be undone); “Keep only complete tracks” deletes the partial ones.
- **Scheduled recording:** with “Schedule a recording”, choose the station, the start, the length or the end, and the repeat (Once, Every day, Weekdays, Weekly); schedules that overlap are marked. If the device or the app restarts during a recording and the schedule's time has not run out, the recording goes on in a new part and its row says it was cut off; a time that passed with nothing recorded is marked missed.
- **Settings › Radio:** the recording folder (by default the app's music folder), the shortest track, how much to keep before and after a title change, the free space to keep, the total size of recordings and what happens at the limit (“Stop recording” or “Delete the oldest”).
- **Recording in the video player:** {key:Record} (also in the screenshot button's menu and in the touch menu) records the film, stream or TV that plays as it comes, into one file. Local files played by the music engine are not recorded. A recording is written to the app's space first and, when it ends, put into the folder you chose in Settings › Recording.
- **Alarm:** Settings › Alarm plays a radio station as a wake-up alarm at the time and on the days you choose.

> Scheduled recordings are part of a backup; the recorded audio is not. For a recording to start on the minute, allow Android's exact alarms; without them, the Recordings tab says “Scheduled recordings may start late”.

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
2. “Addresses and connections…” shows the server's state (Start, Stop), each address with its QR code (Copy, Share…) and the browsers connected; open one of the addresses in the browser, or scan its QR code.
3. The browser warns once, because this device makes its certificate itself: check the address and go on.
4. Enter the 6-digit one-time code the device shows in a notification (and in its window while that is in front), valid for 60 s. Every new browser needs a new code; “It is not me” turns away a connection you did not ask for.

Which parts (videos, music, playlists, search), file and network browsers, history, playback control and log files are open to the browser is chosen on the Settings › Remote access page. “How it works…” tells all this in five steps.

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

## Where are VLC's screens? {classic}

Nağmedar is based on VLC for Android, but VLC's classic screens no longer open: the app icon, the notifications and “Play with Nağmedar” in the share menu always open Nağmedar. What you knew from VLC is here now:

- **VLC's settings:** Settings › About › “All settings”, in Nağmedar's design. The search on the first page looks through every page; the command palette ({key:CommandPalette}) finds each setting by its name.
- **Network stream:** Sources › Streams. Type the address or press “Paste”, and Enter plays it; the streams played before stay below. {key:OpenNetwork} takes you there.
- **Files:** Sources › This device opens the internal storage, SD cards and USB disks as a folder table; from a folder's menu, play it, add it to the queue or make it a section's source folder.
- **Downloads:** Tools › Downloads shows podcast episodes and the Whisper and translation models; a running download can be cancelled, a failed one tried again, and a file is deleted only after you confirm.
- **Media information:** “Media information” in a menu opens Nağmedar's window: the tags, the file's location (“Copy the location”) and the technical facts.
- **The video player:** Nağmedar's controls come for touch too; the equaliser and the audio track and subtitle choices open over the film.
- **Notices and questions:** short notices such as “Copied” show at the bottom of the window for a few seconds. A server's sign-in and certificate questions come in Nağmedar's window; with no window open, tap the “Nağmedar is asking: …” notification. When a new external drive is connected, a notice with a “Library folders” button comes.
- **Resuming:** the “Resume where you stopped?” question comes in Nağmedar's window for music, radio and streams too: “Resume” or “Start over”, and if you like “The same for the rest of the list”.
- **Permissions:** Android's own window asks for the media, notification and storage permissions.
- **Widget:** there is one Nağmedar widget for the home screen: at 4×1 the cover, what plays, previous, play or pause and next; made 4×2, the cover stands beside them.

## Long jobs and Android's time limit {jobs}

Conversions, generating, translating and extracting subtitles, downloads and recordings run in the background; follow them in their notification and stop them there. Conversions wait while the battery is low or the device is hot.

Android 15 and later give work in the background a time limit of six hours a day. When it is reached, Nağmedar says so in a notification and the job goes on like this:
- **Podcast downloads** and **a translation model's download** go on later from where they stopped.
- **Music conversion** waits, then goes on from where it was.
- **Video conversion** goes back to the queue and start again from the beginning when Nağmedar is opened.
- **Generating subtitles**, **translating** and **extracting embedded subtitles** stop; started again, they begin from the start.
- **Radio recording** stops; the tracks so far are saved.
- **Copying a recording of the video player** to its folder goes on; keep Nağmedar open until it ends.
- **Streaming on the network (HLS)** stops; start it again to go on.
- **The library scan** takes its notification away and goes on.

> Follow downloads in Tools › Downloads and video conversions on the Convert page.

## Backup and restore {backup}

Write your settings, lists and ratings to one ZIP file; bring them back on another device or after a reinstall. Where: Settings › Backup.

### Making a backup
1. Press “Back up…” and choose the file's place in the system's file chooser (nagmedar-backup-<date>.zip).
2. The file holds settings, shortcuts, playlists, smart lists, ratings and play counts, your own lyrics and lyrics delays, per-file settings, the covers you chose by hand, latency profiles and scheduled radio recordings.

### Restoring
1. Press “Restore…” and choose the backup file. When it was made and by which version, and its parts with their counts, are shown.
2. Tick the parts you want back and press “Restore”.
3. Nothing is deleted: settings, lists and profiles of the same name are replaced by the backup's, and ratings and play counts are merged. The files of playlists are found from their names in the library; those that are not found are counted, and restoring again after the scan is enough. The alarms of scheduled radio recordings are set again.

> A backup does not hold your media files, the recorded audio, your account sign-ins, this device's folder permissions and the sections' folders, podcast subscriptions or radio favourites. Export OPML for podcasts.

## Privacy: what goes online? {privacy}

Nağmedar has no account, no ads and no tracking. Scanning happens on the device and your media stays on it. The network is used only when you use the features below; requests carry Nağmedar's name, and neither your name nor your e-mail address is in any of them.

- **Subtitle search:** OpenSubtitles.com gets the film's name and the file's fingerprint (you can turn the provider off in Settings › Subtitles).
- **Film information:** the title and year read from the file name (or, when it says nothing, its folder's name) of each film and series in Films and Series go to TMDB, and its IMDb id to OMDb; neither the files nor their full paths are sent (turn it off in Settings › Film information).
- **Lyrics:** if there are no synced lyrics in the file or beside it, the track's title, artist, album and length go to lrclib.net (turn it off in Settings › Lyrics). Uploading lyrics to LRCLIB happens only if you ask for it.
- **Covers:** if there is no cover in the file or the folder, the album's name and artist go to MusicBrainz, and the cover comes from the Cover Art Archive (turn it off in Settings › Covers).
- **Podcasts and radio:** the word you search for goes to Podcast Index, iTunes or Radio Browser; the shows you subscribe to are read from their own servers. Radio Browser is told of each play as a “click”, and of your vote if you vote.
- **Scrobbling:** only if you sign in, the tracks you play go to Last.fm and ListenBrainz.
- **Models:** the Whisper and translation models are downloaded once; after that, speech, films and subtitles do not leave the device.
- **Inside the same network:** casting and remote access talk only to devices on this network.
- **Passwords and tokens:** server passwords, the ListenBrainz token and the Last.fm session key are encrypted with an Android Keystore key; the Last.fm password is never kept.

> To see what is on now, look at the Subtitles, Film information, Lyrics, Covers and Scrobbling sections of Settings.
