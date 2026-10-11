package app.nagmedar.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

// Generated from the mockup icons (16 x 16 grid, 1.6 stroke, round caps) by
// buildsystem/branding/gen_icons.py; edit the generator, not this file.
object NagIcons {
    val Home: ImageVector by lazy { icon("Home", stroke("M3 7.5 8 3l5 4.5V13H9.5V9.5h-3V13H3z")) }
    val Movies: ImageVector by lazy { icon("Movies", stroke("M4,3H12A1.5,1.5 0 0 1 13.5,4.5V11.5A1.5,1.5 0 0 1 12,13H4A1.5,1.5 0 0 1 2.5,11.5V4.5A1.5,1.5 0 0 1 4,3Z"), stroke("M5.5 3v10M10.5 3v10M2.5 6.5h3M2.5 9.5h3M10.5 6.5h3M10.5 9.5h3")) }
    val Series: ImageVector by lazy { icon("Series", stroke("M3.5,3.5H12.5A1.5,1.5 0 0 1 14,5V10A1.5,1.5 0 0 1 12.5,11.5H3.5A1.5,1.5 0 0 1 2,10V5A1.5,1.5 0 0 1 3.5,3.5Z"), stroke("M6 14h4")) }
    val Music: ImageVector by lazy { icon("Music", stroke("M6 12V4l7-1.5v8"), stroke("M3,12a1.5,1.5 0 1 0 3,0a1.5,1.5 0 1 0 -3,0Z"), stroke("M10,10.5a1.5,1.5 0 1 0 3,0a1.5,1.5 0 1 0 -3,0Z")) }
    val Podcasts: ImageVector by lazy { icon("Podcasts", stroke("M8,2H8A2,2 0 0 1 10,4V7.5A2,2 0 0 1 8,9.5H8A2,2 0 0 1 6,7.5V4A2,2 0 0 1 8,2Z"), stroke("M3.5 8a4.5 4.5 0 0 0 9 0M8 12.5v2")) }
    val Radio: ImageVector by lazy { icon("Radio", stroke("M3.5,5.5H12.5A1.5,1.5 0 0 1 14,7V12A1.5,1.5 0 0 1 12.5,13.5H3.5A1.5,1.5 0 0 1 2,12V7A1.5,1.5 0 0 1 3.5,5.5Z"), stroke("M4.5 5.5 11 2.5M4.5 8.5h3M4.5 10.5h3"), stroke("M8.7,9.5a1.8,1.8 0 1 0 3.6,0a1.8,1.8 0 1 0 -3.6,0Z")) }
    val Device: ImageVector by lazy { icon("Device", stroke("M4.5,2H11.5A1.5,1.5 0 0 1 13,3.5V12.5A1.5,1.5 0 0 1 11.5,14H4.5A1.5,1.5 0 0 1 3,12.5V3.5A1.5,1.5 0 0 1 4.5,2Z"), stroke("M7 12h2")) }
    val ExternalDrive: ImageVector by lazy { icon("ExternalDrive", stroke("M3.2,9H12.8A1.2,1.2 0 0 1 14,10.2V12.3A1.2,1.2 0 0 1 12.8,13.5H3.2A1.2,1.2 0 0 1 2,12.3V10.2A1.2,1.2 0 0 1 3.2,9Z"), stroke("M3.5 9 5 3.5h6L12.5 9")) }
    val Network: ImageVector by lazy { icon("Network", stroke("M6.8,2H9.2A0.8,0.8 0 0 1 10,2.8V4.7A0.8,0.8 0 0 1 9.2,5.5H6.8A0.8,0.8 0 0 1 6,4.7V2.8A0.8,0.8 0 0 1 6.8,2Z"), stroke("M2.8,10.5H5.2A0.8,0.8 0 0 1 6,11.3V13.2A0.8,0.8 0 0 1 5.2,14H2.8A0.8,0.8 0 0 1 2,13.2V11.3A0.8,0.8 0 0 1 2.8,10.5Z"), stroke("M10.8,10.5H13.2A0.8,0.8 0 0 1 14,11.3V13.2A0.8,0.8 0 0 1 13.2,14H10.8A0.8,0.8 0 0 1 10,13.2V11.3A0.8,0.8 0 0 1 10.8,10.5Z"), stroke("M8 5.5V8M4 10.5V8h8v2.5")) }
    val Streams: ImageVector by lazy { icon("Streams", stroke("M6.5 9.5 9.5 6.5M7 4.5l1.2-1.2a2.6 2.6 0 0 1 3.7 3.7l-1.2 1.2M9 11.5l-1.2 1.2a2.6 2.6 0 0 1-3.7-3.7l1.2-1.2")) }
    val TvLab: ImageVector by lazy { icon("TvLab", stroke("M6 2.5h4M6.8 2.5v4L3 13a1 1 0 0 0 .9 1.5h8.2A1 1 0 0 0 13 13L9.2 6.5v-4M4.6 10.5h6.8")) }
    val Convert: ImageVector by lazy { icon("Convert", stroke("M3 5.5h9l-2.5-2.5M13 10.5H4l2.5 2.5")) }
    val Downloads: ImageVector by lazy { icon("Downloads", stroke("M8 2.5v8M4.8 7.5 8 10.7l3.2-3.2M3 13.5h10")) }
    val Settings: ImageVector by lazy { icon("Settings", stroke("M5.8,8a2.2,2.2 0 1 0 4.4,0a2.2,2.2 0 1 0 -4.4,0Z"), stroke("M8 1.8v1.7M8 12.5v1.7M1.8 8h1.7M12.5 8h1.7M3.6 3.6l1.2 1.2M11.2 11.2l1.2 1.2M3.6 12.4l1.2-1.2M11.2 4.8l1.2-1.2")) }
    val Plus: ImageVector by lazy { icon("Plus", stroke("M8 3v10M3 8h10")) }
    val Grid: ImageVector by lazy { icon("Grid", stroke("M3.5,2.5H6A1,1 0 0 1 7,3.5V6A1,1 0 0 1 6,7H3.5A1,1 0 0 1 2.5,6V3.5A1,1 0 0 1 3.5,2.5Z"), stroke("M10,2.5H12.5A1,1 0 0 1 13.5,3.5V6A1,1 0 0 1 12.5,7H10A1,1 0 0 1 9,6V3.5A1,1 0 0 1 10,2.5Z"), stroke("M3.5,9H6A1,1 0 0 1 7,10V12.5A1,1 0 0 1 6,13.5H3.5A1,1 0 0 1 2.5,12.5V10A1,1 0 0 1 3.5,9Z"), stroke("M10,9H12.5A1,1 0 0 1 13.5,10V12.5A1,1 0 0 1 12.5,13.5H10A1,1 0 0 1 9,12.5V10A1,1 0 0 1 10,9Z")) }
    val ListView: ImageVector by lazy { icon("ListView", stroke("M5.5 4h8M5.5 8h8M5.5 12h8M2.5 4h.5M2.5 8h.5M2.5 12h.5")) }
    val ChevronDown: ImageVector by lazy { icon("ChevronDown", stroke("m4 6 4 4 4-4")) }
    val ChevronUp: ImageVector by lazy { icon("ChevronUp", stroke("m4 10 4-4 4 4")) }
    val ChevronLeft: ImageVector by lazy { icon("ChevronLeft", stroke("m10 4-4 4 4 4"), mirrored = true) }
    val ChevronRight: ImageVector by lazy { icon("ChevronRight", stroke("m6 4 4 4-4 4"), mirrored = true) }
    val Check: ImageVector by lazy { icon("Check", stroke("m3.5 8.5 3 3 6-7")) }
    val Play: ImageVector by lazy { icon("Play", fill("M5 3.2v9.6L13 8z")) }
    val Resume: ImageVector by lazy { icon("Resume", stroke("M3 8a5 5 0 1 0 1.6-3.7M3 2.5v2.8h2.8")) }
    val Forward: ImageVector by lazy { icon("Forward", stroke("M13 8a5 5 0 1 1-1.6-3.7M13 2.5v2.8h-2.8")) }
    val OpenWindow: ImageVector by lazy { icon("OpenWindow", stroke("M3.7,4.5H10.3A1.2,1.2 0 0 1 11.5,5.7V12.3A1.2,1.2 0 0 1 10.3,13.5H3.7A1.2,1.2 0 0 1 2.5,12.3V5.7A1.2,1.2 0 0 1 3.7,4.5Z"), stroke("M6.5 2.5h7v7M13.5 2.5 8 8")) }
    val Subtitle: ImageVector by lazy { icon("Subtitle", stroke("M3.5,3.5H12.5A1.5,1.5 0 0 1 14,5V11A1.5,1.5 0 0 1 12.5,12.5H3.5A1.5,1.5 0 0 1 2,11V5A1.5,1.5 0 0 1 3.5,3.5Z"), stroke("M4.5 8.5h3M9 8.5h2.5M4.5 10.5h5")) }
    val Info: ImageVector by lazy { icon("Info", stroke("M2.5,8a5.5,5.5 0 1 0 11,0a5.5,5.5 0 1 0 -11,0Z"), stroke("M8 7.5V11M8 5.2v.3")) }
    val PlaylistAdd: ImageVector by lazy { icon("PlaylistAdd", stroke("M2.5 4h8M2.5 7.5h8M2.5 11h5M11.5 9.5v4.5M9.25 11.75h4.5")) }
    val Folder: ImageVector by lazy { icon("Folder", stroke("M2.5 4.5a1 1 0 0 1 1-1h3L8 5h4.5a1 1 0 0 1 1 1v6.5a1 1 0 0 1-1 1h-9a1 1 0 0 1-1-1z")) }
    val Delete: ImageVector by lazy { icon("Delete", stroke("M3 4.5h10M6.5 4.5V3h3v1.5M4.5 4.5l.6 8.5a1 1 0 0 0 1 .9h3.8a1 1 0 0 0 1-.9l.6-8.5")) }
    val Close: ImageVector by lazy { icon("Close", stroke("m4 4 8 8M12 4l-8 8")) }
    val MoreHorizontal: ImageVector by lazy { icon("MoreHorizontal", fill("M2.2,8a1.3,1.3 0 1 0 2.6,0a1.3,1.3 0 1 0 -2.6,0Z"), fill("M6.7,8a1.3,1.3 0 1 0 2.6,0a1.3,1.3 0 1 0 -2.6,0Z"), fill("M11.2,8a1.3,1.3 0 1 0 2.6,0a1.3,1.3 0 1 0 -2.6,0Z")) }
    val MoreVertical: ImageVector by lazy { icon("MoreVertical", fill("M6.6,3.5a1.4,1.4 0 1 0 2.8,0a1.4,1.4 0 1 0 -2.8,0Z"), fill("M6.6,8a1.4,1.4 0 1 0 2.8,0a1.4,1.4 0 1 0 -2.8,0Z"), fill("M6.6,12.5a1.4,1.4 0 1 0 2.8,0a1.4,1.4 0 1 0 -2.8,0Z")) }
    val Shuffle: ImageVector by lazy { icon("Shuffle", stroke("M2.5 4.5h2.2c1.4 0 2.3.7 3 1.8l1.6 3.4c.7 1.1 1.6 1.8 3 1.8h1.2M11.8 9.6l1.7 1.9-1.7 1.9M2.5 11.5h2.2c.9 0 1.6-.3 2.2-.8M9.3 5.3c.6-.5 1.3-.8 2.2-.8h2M11.8 2.6l1.7 1.9-1.7 1.9")) }
    val Previous: ImageVector by lazy { icon("Previous", stroke("M4 3.5v9"), fill("M12.5 3.5 6 8l6.5 4.5z")) }
    val Next: ImageVector by lazy { icon("Next", stroke("M12 3.5v9"), fill("M3.5 3.5 10 8l-6.5 4.5z")) }
    val Pause: ImageVector by lazy { icon("Pause", stroke("M5.5 3.5v9M10.5 3.5v9")) }
    val Repeat: ImageVector by lazy { icon("Repeat", stroke("M3 7V6a2 2 0 0 1 2-2h7.5M10.5 2l2 2-2 2M13 9v1a2 2 0 0 1-2 2H3.5M5.5 14l-2-2 2-2")) }
    val Queue: ImageVector by lazy { icon("Queue", stroke("M2.5 4h11M2.5 8h11M2.5 12h7")) }
    val Artists: ImageVector by lazy { icon("Artists", stroke("M5.5,5.5a2.5,2.5 0 1 0 5,0a2.5,2.5 0 1 0 -5,0Z"), stroke("M3 13.5c.8-2.4 2.7-3.6 5-3.6s4.2 1.2 5 3.6")) }
    val Albums: ImageVector by lazy { icon("Albums", stroke("M2.5,8a5.5,5.5 0 1 0 11,0a5.5,5.5 0 1 0 -11,0Z"), stroke("M6.4,8a1.6,1.6 0 1 0 3.2,0a1.6,1.6 0 1 0 -3.2,0Z")) }
    val History: ImageVector by lazy { icon("History", stroke("M8 4.5V8l2.5 1.5"), stroke("M2.5,8a5.5,5.5 0 1 0 11,0a5.5,5.5 0 1 0 -11,0Z")) }
    val Equalizer: ImageVector by lazy { icon("Equalizer", stroke("M4 2.5v11M8 2.5v11M12 2.5v11"), stroke("M2.5 10h3M6.5 5.5h3M10.5 8.5h3")) }
    val SmartList: ImageVector by lazy { icon("SmartList", stroke("M3 4.5h10M5 8h6M7 11.5h2")) }
    val FramePrevious: ImageVector by lazy { icon("FramePrevious", stroke("M8.5,3.5H12.5A1,1 0 0 1 13.5,4.5V11.5A1,1 0 0 1 12.5,12.5H8.5A1,1 0 0 1 7.5,11.5V4.5A1,1 0 0 1 8.5,3.5Z"), stroke("M5 5 2.5 8 5 11")) }
    val FrameNext: ImageVector by lazy { icon("FrameNext", stroke("M3.5,3.5H7.5A1,1 0 0 1 8.5,4.5V11.5A1,1 0 0 1 7.5,12.5H3.5A1,1 0 0 1 2.5,11.5V4.5A1,1 0 0 1 3.5,3.5Z"), stroke("M11 5l2.5 3L11 11")) }
    val AudioTrack: ImageVector by lazy { icon("AudioTrack", stroke("M3 10V6M5.5 12V4M8 11V5M10.5 13V3M13 10V6")) }
    val Screenshot: ImageVector by lazy { icon("Screenshot", stroke("M2.5 5.5a1 1 0 0 1 1-1h1.8L6.5 3h3l1.2 1.5h1.8a1 1 0 0 1 1 1v6.5a1 1 0 0 1-1 1h-9a1 1 0 0 1-1-1z"), stroke("M5.8,8.5a2.2,2.2 0 1 0 4.4,0a2.2,2.2 0 1 0 -4.4,0Z")) }
    val Effects: ImageVector by lazy { icon("Effects", stroke("M3 4.5h10M3 8h10M3 11.5h10"), stroke("M4.6,4.5a1.4,1.4 0 1 0 2.8,0a1.4,1.4 0 1 0 -2.8,0Z"), stroke("M9.1,8a1.4,1.4 0 1 0 2.8,0a1.4,1.4 0 1 0 -2.8,0Z"), stroke("M3.6,11.5a1.4,1.4 0 1 0 2.8,0a1.4,1.4 0 1 0 -2.8,0Z")) }
    val PictureInPicture: ImageVector by lazy { icon("PictureInPicture", stroke("M3.5,3H12.5A1.5,1.5 0 0 1 14,4.5V11.5A1.5,1.5 0 0 1 12.5,13H3.5A1.5,1.5 0 0 1 2,11.5V4.5A1.5,1.5 0 0 1 3.5,3Z"), fill("M8.6,8H11.9A0.6,0.6 0 0 1 12.5,8.6V10.9A0.6,0.6 0 0 1 11.9,11.5H8.6A0.6,0.6 0 0 1 8,10.9V8.6A0.6,0.6 0 0 1 8.6,8Z")) }
    val Fullscreen: ImageVector by lazy { icon("Fullscreen", stroke("M2.5 6V2.5H6M10 2.5h3.5V6M13.5 10v3.5H10M6 13.5H2.5V10")) }
    val Search: ImageVector by lazy { icon("Search", stroke("M2.5,7a4.5,4.5 0 1 0 9,0a4.5,4.5 0 1 0 -9,0Z"), stroke("m10.5 10.5 3 3")) }
    val Sort: ImageVector by lazy { icon("Sort", stroke("M4.5 3v10M2.5 11l2 2 2-2M11.5 13V3M9.5 5l2-2 2 2")) }
    val Lyrics: ImageVector by lazy { icon("Lyrics", stroke("M2.5 4h7M2.5 7.5h11M2.5 11h8")) }
    val Volume: ImageVector by lazy { icon("Volume", stroke("M2.5 6h2.5L8.5 3v10L5 10H2.5z"), stroke("M10.8 5.6a3.4 3.4 0 0 1 0 4.8M12.6 3.8a6 6 0 0 1 0 8.4")) }
    val Menu: ImageVector by lazy { icon("Menu", stroke("M3 4.5h10M3 8h10M3 11.5h10")) }
    val Waveform: ImageVector by lazy { icon("Waveform", stroke("M2.5 7v2M5 5v6M7.5 3v10M10 5.5v5M12.5 7v2")) }
    val Keyboard: ImageVector by lazy { icon("Keyboard", stroke("M3,4H13A1.5,1.5 0 0 1 14.5,5.5V10.5A1.5,1.5 0 0 1 13,12H3A1.5,1.5 0 0 1 1.5,10.5V5.5A1.5,1.5 0 0 1 3,4Z"), stroke("M4 6.5h.5M6.5 6.5h.5M9 6.5h.5M11.5 6.5h.5M5 9.5h6")) }
    val SidebarToggle: ImageVector by lazy { icon("SidebarToggle", stroke("M3.5,2.5H12.5A1.5,1.5 0 0 1 14,4V12A1.5,1.5 0 0 1 12.5,13.5H3.5A1.5,1.5 0 0 1 2,12V4A1.5,1.5 0 0 1 3.5,2.5Z"), stroke("M6 2.5v11")) }
    val FolderAdd: ImageVector by lazy { icon("FolderAdd", stroke("M2.5 4.5a1 1 0 0 1 1-1h3L8 5h4.5a1 1 0 0 1 1 1v6.5a1 1 0 0 1-1 1h-9a1 1 0 0 1-1-1zM8 7.5v4M6 9.5h4")) }
    val Shield: ImageVector by lazy { icon("Shield", stroke("M8 1.8 13 4v4c0 3-2.2 5.2-5 6.2-2.8-1-5-3.2-5-6.2V4zM6 8l1.5 1.5L10.5 6.5")) }
    val ErrorCircle: ImageVector by lazy { icon("ErrorCircle", stroke("M2.5,8a5.5,5.5 0 1 0 11,0a5.5,5.5 0 1 0 -11,0Z"), stroke("M6 6l4 4M10 6l-4 4")) }
    val SuccessCircle: ImageVector by lazy { icon("SuccessCircle", stroke("M2.5,8a5.5,5.5 0 1 0 11,0a5.5,5.5 0 1 0 -11,0Z"), stroke("M5.5 8.2l1.8 1.8 3.3-3.6")) }
    val Warning: ImageVector by lazy { icon("Warning", stroke("M8 2.5 14 13H2z"), stroke("M8 6.5v3M8 11.2v.3")) }
    val DriveRemoved: ImageVector by lazy { icon("DriveRemoved", stroke("M2 9h12v4.5H2zM3.5 9 5 3.5h6L12.5 9M5 11.2h.01")) }
    val DiskFull: ImageVector by lazy { icon("DiskFull", stroke("M2 9h12v4.5H2zM3.5 9 5 3.5h6L12.5 9M8 5.5v2")) }
    val NetworkOff: ImageVector by lazy { icon("NetworkOff", stroke("M2 6.5a9 9 0 0 1 12 0M4.2 9a5.8 5.8 0 0 1 7.6 0M8 12.5h.01M3 3l10 10")) }
    val SearchOff: ImageVector by lazy { icon("SearchOff", stroke("M2.5,7a4.5,4.5 0 1 0 9,0a4.5,4.5 0 1 0 -9,0Z"), stroke("m10.5 10.5 3 3M5.5 7h3")) }
    val Lock: ImageVector by lazy { icon("Lock", stroke("M4.7,7H11.3A1.2,1.2 0 0 1 12.5,8.2V12.3A1.2,1.2 0 0 1 11.3,13.5H4.7A1.2,1.2 0 0 1 3.5,12.3V8.2A1.2,1.2 0 0 1 4.7,7Z"), stroke("M5.5 7V5a2.5 2.5 0 0 1 5 0v2")) }

    /** Every icon in the generator's order: the icon board and its tests. */
    val all: List<ImageVector> get() = listOf(
        Home, Movies, Series, Music, Podcasts, Radio, Device, ExternalDrive,
        Network, Streams, TvLab, Convert, Downloads, Settings, Plus, Grid,
        ListView, ChevronDown, ChevronUp, ChevronLeft, ChevronRight, Check, Play, Resume,
        Forward, OpenWindow, Subtitle, Info, PlaylistAdd, Folder, Delete, Close,
        MoreHorizontal, MoreVertical, Shuffle, Previous, Next, Pause, Repeat, Queue,
        Artists, Albums, History, Equalizer, SmartList, FramePrevious, FrameNext, AudioTrack,
        Screenshot, Effects, PictureInPicture, Fullscreen, Search, Sort, Lyrics, Volume,
        Menu, Waveform, Keyboard, SidebarToggle, FolderAdd, Shield, ErrorCircle, SuccessCircle,
        Warning, DriveRemoved, DiskFull, NetworkOff, SearchOff, Lock,
    )
}

private class IconPart(val d: String, val filled: Boolean)

private fun stroke(d: String) = IconPart(d, filled = false)

private fun fill(d: String) = IconPart(d, filled = true)

private fun icon(name: String, vararg parts: IconPart, mirrored: Boolean = false): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 16.dp,
        defaultHeight = 16.dp,
        viewportWidth = 16f,
        viewportHeight = 16f,
        autoMirror = mirrored,
    )
        .apply {
            parts.forEach { part ->
                if (part.filled) {
                    addPath(pathData = addPathNodes(part.d), fill = SolidColor(Color.Black))
                } else {
                    addPath(
                        pathData = addPathNodes(part.d),
                        fill = null,
                        stroke = SolidColor(Color.Black),
                        strokeLineWidth = 1.6f,
                        strokeLineCap = StrokeCap.Round,
                        strokeLineJoin = StrokeJoin.Round,
                    )
                }
            }
        }
        .build()
