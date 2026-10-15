package app.nagmedar.ui.network

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import org.videolan.libvlc.FactoryManager
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.libvlc.interfaces.IMediaFactory
import org.videolan.libvlc.interfaces.IMediaList
import org.videolan.libvlc.util.MediaBrowser
import org.videolan.resources.VLCInstance
import java.util.concurrent.atomic.AtomicInteger

/**
 * A thread of one flow's own, where its libVLC objects are made, listened to and released: never
 * the main one, and never another flow's, which a release waiting for its parse would hold up.
 */
private fun flowHandler(name: String): Handler = Handler(HandlerThread(name).apply { start() }.looper)

/** [handler]'s last work: [release], then its thread ends. */
private fun finish(handler: Handler, release: () -> Unit) {
    handler.post {
        runCatching(release)
        handler.looper.quitSafely()
    }
}

/**
 * How a folder's reading ends: libVLC's parse status, then the end of the files queued before it,
 * then [end] once. A parse that could not even start ends it at once.
 */
internal class ReadingEnd(private val found: () -> Int, private val end: (done: Boolean) -> Unit) {
    private var over = false

    /** libVLC's [status] for the parse: [PARSED_DONE] when it read the folder. */
    @Synchronized
    fun parsed(status: Int?) {
        if (over) return
        over = true
        end(read(status) || found() > 0)
    }

    companion object {
        /**
         * libvlc_media_parsed_status_done of libVLC 4 (libvlc_media.h, eap29: none, pending, skipped,
         * failed, timeout, cancelled, done): the binding hands the native value on, not
         * IMedia.ParsedStatus, which keeps libVLC 3's numbering (done 4 is libVLC 4's timeout).
         */
        const val PARSED_DONE = 6

        /** Whether libVLC's native parse [status] says the folder was read. */
        fun read(status: Int?): Boolean = status == PARSED_DONE
    }
}

/**
 * S-1301: the network as libVLC sees it: its LAN discoverers (SMB, UPnP/DLNA, mDNS: SFTP, NFS,
 * FTP…) through [MediaBrowser], and a folder read as VLC's browser reads it, with its parse
 * status kept to tell an unreachable server from an empty folder. Every libVLC object is made,
 * listened to and released on a thread of each flow's own (a listener taken away clears its
 * handler's messages, and a release that waits holds up no other flow); releasing waits for a running parse, which
 * has a time limit and whose questions are dismissed first, so the main thread never waits.
 */
class LibVlcNetworkSource(context: Context) : NetworkSource {
    private val app = context.applicationContext

    override fun discover(): Flow<DiscoveryEvent> = callbackFlow {
        val handler = flowHandler("nagmedar-discovery")
        var browser: MediaBrowser? = null
        try {
            val libVlc = withContext(Dispatchers.IO) { VLCInstance.getInstance(app) }
            val listener = object : MediaBrowser.EventListener {
                override fun onMediaAdded(index: Int, media: IMedia) {
                    serverOf(media)?.let { trySend(DiscoveryEvent.Found(it)) }
                }

                override fun onMediaRemoved(index: Int, media: IMedia) {
                    media.uri?.toString()?.let { trySend(DiscoveryEvent.Lost(it)) }
                }

                override fun onBrowseEnd() = Unit
            }
            withContext(handler.asCoroutineDispatcher()) {
                browser = MediaBrowser(libVlc, listener, handler).also { it.discoverNetworkShares() }
            }
            awaitClose()
        } finally {
            val made = browser
            finish(handler) { made?.release() }
        }
    }.buffer(Channel.UNLIMITED)

    override fun browse(url: String, credentials: Credentials?): Flow<BrowseEvent> = callbackFlow {
        val handler = flowHandler("nagmedar-browse")
        val uri = Uri.parse(url)
        val options = credentials?.let { signInOptions(NetworkProtocol.of(uri.scheme), it) }.orEmpty()
        val claim = DialogClaim.browsing(url, credentials, options.isNotEmpty(), SystemClock.elapsedRealtime()) { prompt ->
            trySend(BrowseEvent.Asked(prompt))
        }
        val found = AtomicInteger()
        val ending = ReadingEnd(found::get) { done ->
            trySend(BrowseEvent.Ended(claim.result(done)))
            close()
        }
        var media: IMedia? = null
        var list: IMediaList? = null
        NetworkDialogs.install(app)
        NetworkDialogs.router.begin(claim)
        try {
            val libVlc = withContext(Dispatchers.IO) { VLCInstance.getInstance(app) }
            val factory = FactoryManager.getFactory(IMediaFactory.factoryId) as IMediaFactory
            withContext(handler.asCoroutineDispatcher()) {
                val made = factory.getFromUri(libVlc, uri)
                media = made
                made.addOption(IGNORED_TYPES)
                options.forEach(made::addOption)
                val items = made.subItems()
                list = items
                items.setEventListener({ event ->
                    if (event.type == IMediaList.Event.ItemAdded) {
                        event.media?.let { entryOf(it) }?.let {
                            found.incrementAndGet()
                            trySend(BrowseEvent.Found(it))
                        }
                    }
                }, handler)
                // the status comes on the main thread: the end waits behind the files already queued
                made.setEventListener { event ->
                    if (event.type == IMedia.Event.ParsedChanged) {
                        val status = event.parsedStatus
                        handler.post { ending.parsed(status) }
                    }
                }
                // the page offers to give up long before this
                if (!made.parseAsync(IMedia.Parse.ParseNetwork or IMedia.Parse.DoInteract, PARSE_TIMEOUT_MS)) ending.parsed(null)
            }
            awaitClose()
        } finally {
            // a question still waiting is dismissed, so that the parse can stop
            NetworkDialogs.router.end(claim)
            val made = media
            val items = list
            finish(handler) {
                items?.setEventListener(null, handler)
                made?.setEventListener(null)
                // libVLC waits here for the parse to stop (its time limit at most), on this flow's thread only
                items?.release()
                made?.release()
            }
        }
    }.buffer(Channel.UNLIMITED)

    private fun serverOf(media: IMedia): DiscoveredServer? {
        val url = media.uri?.toString() ?: return null
        val name = media.getMeta(IMedia.Meta.Title)?.takeIf { it.isNotBlank() } ?: displayHost(url)
        return DiscoveredServer(url, name)
    }

    private fun entryOf(media: IMedia): NetworkEntry? {
        val uri = media.uri ?: return null
        val name = media.getMeta(IMedia.Meta.Title)?.takeIf { it.isNotBlank() } ?: uri.lastPathSegment ?: return null
        return NetworkEntry(uri.toString(), name, EntryKind.of(name, media.type == IMedia.Type.Directory))
    }

    private companion object {
        /** How long libVLC may take to read a folder before it gives up (it reports a time-out). */
        const val PARSE_TIMEOUT_MS = 30_000

        /** What VLC's browser leaves out of a folder (MediaBrowser's list): pictures, texts, subtitles kept with films. */
        const val IGNORED_TYPES = ":ignore-filetypes=db,nfo,ini,jpg,jpeg,ljpg,gif,png,pgm,pgmyuv,pbm,pam,tga,bmp,pnm,xpm,xcf,pcx,tif,tiff,lbm,sfv,txt," +
            "sub,idx,srt,ssa,ass,smi,utf,utf-8,rt,aqt,usf,jss,cdg,psb,mpsub,mpl2,pjs,dks,stl,vtt,ttml"
    }
}
