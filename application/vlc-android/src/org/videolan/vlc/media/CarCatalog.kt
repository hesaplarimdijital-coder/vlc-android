package org.videolan.vlc.media

import android.content.Context
import org.videolan.vlc.PlaybackService

/** The field a voice assistant says a request is about (MediaStore.EXTRA_MEDIA_FOCUS). */
enum class VoiceFocus { Artist, Album, Title, Genre, Playlist }

/**
 * 1.0: a request of Android Auto's voice search ("Hey Google, … çal"): the words heard, and when the assistant understood
 * them as music, the field they are about with what it heard for each field.
 */
data class VoiceRequest(
    val query: String,
    val focus: VoiceFocus? = null,
    val artist: String? = null,
    val album: String? = null,
    val title: String? = null,
    val genre: String? = null,
    val playlist: String? = null,
)

/** How the entries under a browsable [CarItem] are drawn: rows, tiles, or rows of categories. */
enum class CarStyle { List, Grid, Category }

/**
 * 1.0: one entry of Android Auto's browse tree (and of its search results), as the new interface's catalog makes it;
 * [MediaSessionBrowser] turns it into the browser's item. [iconUri] is a content:// address of the artwork provider or
 * an android.resource:// one; [group] is the heading a search result is listed under; [progress] is how far an episode
 * was heard (0 to 1), null for anything that is not an episode.
 */
data class CarItem(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val iconUri: String? = null,
    val playable: Boolean = false,
    val browsable: Boolean = false,
    /** How this entry's own entries are drawn, when it is browsable. */
    val children: CarStyle? = null,
    val group: String? = null,
    val progress: Double? = null,
    val played: Boolean = false,
    val downloaded: Boolean = false,
)

/**
 * 1.0: Android Auto's catalog: Nağmedar's music, podcasts and radio, kept by the new interface's module, which this one
 * does not see; its observer of the playback service ([PlaybackObserver]) sets [CarHook.catalog] while the service lives.
 * Every call is made off the main thread.
 */
interface CarCatalog {
    /** The entries under [parentId] (the root's: Music, Podcasts, Radio, Recently played); null when the id is none of the catalog's. */
    suspend fun children(context: Context, parentId: String): List<CarItem>?

    /** Android Auto's search: the results grouped (tracks, albums, artists, episodes, shows, stations). */
    suspend fun search(context: Context, query: String): List<CarItem>

    /** Plays the entry [mediaId] as tapping it in the app would; false when the id is none of the catalog's or plays nothing. */
    suspend fun play(service: PlaybackService, mediaId: String): Boolean

    /**
     * Plays what [request] asks for: music first (by the field the assistant names), then podcasts, then radio; a request
     * with nothing in it ("çal", "play") what played last. False when nothing answers it.
     */
    suspend fun play(service: PlaybackService, request: VoiceRequest): Boolean

    /** The medialibrary changed ([history]: only what was played): the music read is read again; returns the entries to tell Auto about. */
    fun libraryChanged(history: Boolean): List<String>
}

object CarHook {
    @Volatile
    var catalog: CarCatalog? = null
}
