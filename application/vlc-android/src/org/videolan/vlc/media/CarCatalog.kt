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
    /** 1.0.3: the ids of the custom browse actions Auto offers on this entry ([CarCatalog.browseActions]: "Abone ol"). */
    val actions: List<String> = emptyList(),
)

/**
 * 1.0.3: a custom browse action of Android Auto (androidx.media 1.7): an icon Auto shows on the entries that name its
 * [id] ([CarItem.actions]); tapped, Auto stays on the list and the catalog does it ([CarCatalog.act]). [iconUri] is a
 * tintable vector drawable's android.resource:// address.
 */
data class CarAction(val id: String, val label: String, val iconUri: String)

/**
 * 1.0.3: what a custom browse action came to: the [message] Auto shows (that it worked, or why not) and the entry it
 * reads again ([refresh], through [CarCatalog.item]), whose look changed.
 */
data class CarActionResult(val message: String, val refresh: String? = null)

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

    /**
     * The medialibrary changed ([history]: only what was played): the music read is read again; returns the entries to
     * tell Auto about now. Those a burst of changes leaves untold are told by the catalog itself once it ends.
     */
    fun libraryChanged(history: Boolean): List<String>

    /**
     * What a line that plays nothing says when tapped ([MediaSessionBrowser.isNoMedia]: an empty list's, an offline
     * directory's), or with no [mediaId] what a voice request that found nothing says; in [context]'s language.
     */
    fun nothingText(context: Context, mediaId: String?): String

    /**
     * The tree's id of what plays from [location] (a track of a list Auto played, an episode, a station), which Auto marks
     * as playing; null when it is none of the tree's. Quick: called on the main thread at every state change.
     */
    fun playingId(location: String?): String?

    /** 1.0.3: the custom browse actions the catalog's entries may offer, in [context]'s language; none by default. */
    fun browseActions(context: Context): List<CarAction> = emptyList()

    /** 1.0.3: the entry [mediaId] alone, as a list shows it (Auto reads one again after an action changed it); null when unknown. */
    suspend fun item(context: Context, mediaId: String): CarItem? = null

    /** 1.0.3: does the browse action [action] on the entry [mediaId]; null when it is none of the catalog's. */
    suspend fun act(context: Context, action: String, mediaId: String): CarActionResult? = null
}

object CarHook {
    @Volatile
    var catalog: CarCatalog? = null
}
