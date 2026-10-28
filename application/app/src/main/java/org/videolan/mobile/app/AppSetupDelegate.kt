/*****************************************************************************
 * AppSetupDelegate.ki
 *
 * Copyright © 2020 VLC authors and VideoLAN
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
package org.videolan.mobile.app

import android.annotation.TargetApi
import android.app.Activity
import android.app.Application
import android.app.Application.ActivityLifecycleCallbacks
import android.content.Context
import android.os.Build
import android.os.StrictMode
import android.os.Bundle
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.DEBUG_PROPERTY_NAME
import kotlinx.coroutines.DEBUG_PROPERTY_VALUE_ON
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import app.nagmedar.ui.ProcessState
import app.nagmedar.ui.cast.Casting
import app.nagmedar.ui.library.NewDrives
import app.nagmedar.ui.network.NetworkDialogs
import app.nagmedar.ui.radio.record.RecordingAlarms
import app.nagmedar.ui.work.RetiredWork
import org.videolan.libvlc.Dialog
import org.videolan.libvlc.FactoryManager
import org.videolan.libvlc.LibVLCFactory
import org.videolan.libvlc.MediaFactory
import org.videolan.libvlc.interfaces.ILibVLCFactory
import org.videolan.libvlc.interfaces.IMediaFactory
import org.videolan.mobile.app.delegates.IIndexersDelegate
import org.videolan.mobile.app.delegates.IMediaContentDelegate
import org.videolan.mobile.app.delegates.IndexersDelegate
import org.videolan.mobile.app.delegates.MediaContentDelegate
import org.videolan.resources.AppContextProvider
import org.videolan.resources.VLCInstance
import org.videolan.resources.util.startRemoteAccess
import org.videolan.tools.AppScope
import org.videolan.tools.KEY_ENABLE_REMOTE_ACCESS
import org.videolan.tools.KEY_INCOGNITO
import org.videolan.tools.KEY_PERSISTENT_INCOGNITO
import org.videolan.tools.Settings
import org.videolan.tools.putSingle
import org.videolan.vlc.BuildConfig
import org.videolan.vlc.gui.helpers.NotificationHelper
import org.videolan.vlc.util.DialogDelegate
import org.videolan.vlc.util.FontCache
import org.videolan.vlc.util.NetworkConnectionManager
import org.videolan.vlc.util.NewStorageHook
import org.videolan.vlc.util.VersionMigration
import java.io.File
import java.io.IOException
import kotlin.concurrent.thread

private const val TAG = "VLC/AppSetupDelegate"

interface AppDelegate {
    val appContextProvider : AppContextProvider
    fun Application.setupApplication()
}

class AppSetupDelegate : AppDelegate,
        IMediaContentDelegate by MediaContentDelegate(),
        IIndexersDelegate by IndexersDelegate() {

    // Store AppContextProvider to prevent GC
    override val appContextProvider = AppContextProvider

    @TargetApi(Build.VERSION_CODES.O)
    override fun Application.setupApplication() {
        // Nağmedar: a debug build names every disk or network access on the main thread and every leak in
        // logcat (tag StrictMode, with the stack), where the emulator walk and a developer read them
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog().build())
            StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().detectLeakedClosableObjects().detectLeakedSqlLiteObjects().detectActivityLeaks().penaltyLog().build())
        }
        // Nağmedar: the preferences' file is read on a background thread while the setup below goes
        // on; the main thread takes them once read and does not read the disk for them (a read that
        // failed there is tried again on the main thread, which then fails as it always did)
        val preferences = thread(name = "nagmedar-preferences") { runCatching { Settings.getInstance(this@setupApplication) } }
        // Nağmedar: the process-wide Compose state is made here, in the global snapshot, before any window composes
        ProcessState.warmUp()
        appContextProvider.init(this)
        NotificationHelper.createNotificationChannels(this)

        // Service loaders
        FactoryManager.registerFactory(IMediaFactory.factoryId, MediaFactory())
        FactoryManager.registerFactory(ILibVLCFactory.factoryId, LibVLCFactory())
        System.setProperty(DEBUG_PROPERTY_NAME, DEBUG_PROPERTY_VALUE_ON)

        preferences.join()
        val settings = Settings.getInstance(this)
        if (BuildConfig.DEBUG) {
            if (Settings.showTvUi) {
                // Register movipedia to resume tv shows/movies
                setupContentResolvers()

                // Setup Moviepedia indexing after Medialibrary scan
                setupIndexers()
            }
        }
        //App restarted, leave the incognito mode
        if (!settings.getBoolean(KEY_PERSISTENT_INCOGNITO, true))
            settings.putSingle(KEY_INCOGNITO, false)


        AppContextProvider.setLocale(Settings.appLocale(settings))

        //Initiate Kotlinx Dispatchers in a thread to prevent ANR
        backgroundInit()
        if (settings.getBoolean(KEY_ENABLE_REMOTE_ACCESS, false))
            startRemoteAccess()

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                AppContextProvider.currentActivity = activity
            }

            override fun onActivityPaused(activity: Activity) {
                AppContextProvider.currentActivity = null
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    /**
     * The application is also created in the secondary processes (the logger one), which don't
     * have to take part in the app setup.
     */
    private fun Context.isMainProcess(): Boolean {
        val processName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) Application.getProcessName()
        else try {
            // The arguments are separated by null characters, the first one is the process name
            File("/proc/self/cmdline").readText().substringBefore('\u0000')
        } catch (e: IOException) {
            Log.w(TAG, "Can't read the process name", e)
            return true
        }
        return processName == packageName
    }

    // init operations executed in background threads
    private fun Context.backgroundInit() = AppScope.launch outerLaunch@ {
        VersionMigration.migrateVersion(this@backgroundInit)
        Settings.initPostMigration(this@backgroundInit)
        // Nağmedar (S-1302): the new interface's device picker and "Play on …"
        Casting.install()
        // Nağmedar: a new drive is said by the new interface, not by VLC's DialogActivity
        NewStorageHook.handler = NewDrives
        // Nağmedar (S-1107): a scheduled radio recording a restart cut off is taken up again, and the runs that passed unrecorded are marked missed
        if (isMainProcess()) launch(Dispatchers.IO) {
            try {
                RecordingAlarms.onProcessStart(this@backgroundInit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "the scheduled recordings could not be looked over", e)
            }
            // Nağmedar 1.0: the removed TV Lab's queued work, night alarm and queue state, once
            RetiredWork.onProcessStart(this@backgroundInit)
        }
        launch(Dispatchers.IO) innerLaunch@ {
            if (!VLCInstance.testCompatibleCPU(AppContextProvider.appContext)) return@innerLaunch
            Dialog.setCallbacks(VLCInstance.getInstance(this@backgroundInit), DialogDelegate)
            // Nağmedar (S-1301): saved network servers sign in for the library's scan and for playback
            NetworkDialogs.install(this@backgroundInit)
            VersionMigration.migrateVersionAfterLibVLC(this@backgroundInit)
            // Scan the system fonts now, so that the first playback doesn't have to
            if (isMainProcess()) FontCache.prepare(this@backgroundInit)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            NetworkConnectionManager.start(AppContextProvider.appContext)
        }

    }
}
