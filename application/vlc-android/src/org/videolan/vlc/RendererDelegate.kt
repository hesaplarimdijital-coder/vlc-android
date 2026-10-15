/*****************************************************************************
 * RendererDelegate.java
 *
 * Copyright © 2017 VLC authors and VideoLAN
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 */
package org.videolan.vlc

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import app.nagmedar.playback.RendererKey
import app.nagmedar.playback.RendererRoster
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videolan.libvlc.RendererDiscoverer
import org.videolan.libvlc.RendererItem
import org.videolan.resources.AppContextProvider
import org.videolan.resources.VLCInstance
import org.videolan.tools.AppScope
import org.videolan.tools.NetworkMonitor
import org.videolan.tools.isAppStarted
import org.videolan.tools.livedata.LiveDataset
import org.videolan.tools.retry

/**
 * libVLC's renderer discovery (Chromecasts with microdns_renderer, DLNA renderers with
 * upnp_renderer) and the renderers it found. Nağmedar (S-1302): the discovery runs only while
 * someone wants it, on a connected network: a renderer picker open, a classic screen showing its
 * cast button, the casting itself; and a device found twice (IPv4 and IPv6, two addresses) is
 * listed once.
 */
object RendererDelegate : RendererDiscoverer.EventListener {

    private const val TAG = "VLC/RendererDelegate"
    private val discoverers = ArrayList<RendererDiscoverer>()
    val renderers : LiveDataset<RendererItem> = LiveDataset()

    /** Nağmedar (S-1302): the items found, one per device by its name and kind. */
    private val roster = RendererRoster<RendererItem> { RendererKey.of(it.displayName, it.type) }

    /** Nağmedar (S-1302): the casting itself, as one who wants the renderers found. */
    val CASTING = Any()

    /** Who wants the discovery to run (S-1302). */
    private val wanted = MutableStateFlow<Set<Any>>(emptySet())

    private val running = MutableStateFlow(false)

    /** Nağmedar (S-1302): whether the discoverers run now, for a picker's "searching". */
    val discovering: StateFlow<Boolean> = running.asStateFlow()

    @Volatile private var started = false

    init {
        val connection = NetworkMonitor.getInstance(AppContextProvider.appContext).connectionFlow
        // the network gone: casting ends as before (unless the app is away and it plays); the
        // monitor's first value is a placeholder until the system tells it the state
        var wasConnected = false
        connection.map { it.connected }.distinctUntilChanged().onEach { connected ->
            if (wasConnected && !connected) lost()
            wasConnected = connected
        }.launchIn(AppScope)
        combine(connection, wanted) { network, owners -> network.connected && owners.isNotEmpty() }
            .distinctUntilChanged()
            .onEach { if (it) start() else stop() }
            .launchIn(AppScope)
    }

    /** Nağmedar (S-1302): [owner] wants the renderers found, or no longer does. */
    fun want(owner: Any, wants: Boolean) {
        wanted.update { if (wants) it + owner else it - owner }
    }

    /** Nağmedar (S-1302): the discovery runs while [owner] is started (a classic screen with its cast button). */
    fun wantWhileStarted(owner: LifecycleOwner) {
        owner.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) = want(owner, true)

            override fun onStop(owner: LifecycleOwner) = want(owner, false)
        })
    }

    /** Nağmedar (S-1302): the discovery started again ("Search again"): DLNA renderers answer a new search at once. */
    fun restart() {
        if (!started) return
        AppScope.launch {
            stop()
            if (wanted.value.isNotEmpty()) start()
        }
    }

    suspend fun start() {
        if (started) return
        val libVlc = withContext(Dispatchers.IO) { VLCInstance.getInstance(AppContextProvider.appContext) }
        // started meanwhile, or no longer wanted
        if (started || wanted.value.isEmpty()) return
        started = true
        running.value = true
        // all made before the first wait, so a stop() meanwhile stops each of them
        val created = RendererDiscoverer.list(libVlc).map { description ->
            RendererDiscoverer(libVlc, description.name).also { it.setEventListener(this@RendererDelegate) }
        }
        discoverers.addAll(created)
        for (rd in created) retry(5, 1000L) { if (!rd.isReleased) rd.start() else false }
    }

    fun stop() {
        if (!started) return
        started = false
        running.value = false
        for (discoverer in discoverers) if (!discoverer.isReleased) discoverer.stop()
        clear()
    }

    private fun lost() {
        if (isAppStarted() || PlaybackService.instance?.run { !isPlaying } != false) {
            PlaybackService.renderer.value = null
        }
    }

    private fun clear() {
        discoverers.clear()
        roster.clear()
        renderers.clear()
    }

    override fun onEvent(event: RendererDiscoverer.Event?) {
        val item = event?.item ?: return
        val changed = when (event.type) {
            RendererDiscoverer.Event.ItemAdded -> roster.add(item)
            RendererDiscoverer.Event.ItemDeleted -> roster.remove(item)
            else -> false
        }
        if (changed) renderers.value = roster.devices.toMutableList()
    }
}
