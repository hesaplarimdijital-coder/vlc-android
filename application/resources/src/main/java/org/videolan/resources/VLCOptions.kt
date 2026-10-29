/*****************************************************************************
 * VLCOptions.java
 *
 * Copyright © 2015 VLC authors and VideoLAN
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

package org.videolan.resources

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioManager
import android.os.Build
import android.util.Log
import androidx.core.content.getSystemService
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.libvlc.util.AndroidUtil
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.tools.KEY_AOUT
import org.videolan.tools.KEY_AUDIO_DIGITAL_OUTPUT
import org.videolan.tools.KEY_AUDIO_REPLAY_GAIN_DEFAULT
import org.videolan.tools.KEY_AUDIO_REPLAY_GAIN_ENABLE
import org.videolan.tools.KEY_AUDIO_REPLAY_GAIN_MODE
import org.videolan.tools.KEY_AUDIO_REPLAY_GAIN_PEAK_PROTECTION
import org.videolan.tools.KEY_AUDIO_REPLAY_GAIN_PREAMP
import org.videolan.tools.KEY_CASTING_AUDIO_ONLY
import org.videolan.tools.KEY_CASTING_PASSTHROUGH
import org.videolan.tools.KEY_CASTING_QUALITY
import org.videolan.tools.KEY_CUSTOM_LIBVLC_OPTIONS
import org.videolan.tools.KEY_ENABLE_TIME_STRETCHING_AUDIO
import org.videolan.tools.KEY_ENABLE_VERBOSE_MODE
import org.videolan.tools.KEY_NETWORK_CACHING_VALUE
import org.videolan.tools.KEY_PREFER_SMBV1
import org.videolan.tools.Preferences
import org.videolan.tools.Settings
import org.videolan.tools.putSingle
import org.videolan.vlc.VlcMigrationHelper
import org.videolan.vlc.isVLC4
import java.io.File
import java.util.Collections

object VLCOptions {
    private const val TAG = "VLC/VLCConfig"

    private const val AOUT_AAUDIO = 0
    private const val AOUT_AUDIOTRACK = 1
    private const val AOUT_OPENSLES = 2

    var audiotrackSessionId = 0
        private set

    // TODO should return List<String>
    /* generate an audio session id so as to share audio output with external equalizer *//* CPU intensive plugin, setting for slow devices *//* XXX: why can't the default be fine ? #7792 *//* Configure keystore *///Chromecast
    val libOptions: ArrayList<String>
        get() {
            val context = AppContextProvider.appContext
            val pref = Settings.getInstance(context)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && audiotrackSessionId == 0) {
                val audioManager = context.getSystemService<AudioManager>()!!
                audiotrackSessionId = audioManager.generateAudioSessionId()
            }

            val options = ArrayList<String>(50)

            val timeStrechingDefault = context.resources.getBoolean(R.bool.time_stretching_default)
            val timeStreching = pref.getBoolean(KEY_ENABLE_TIME_STRETCHING_AUDIO, timeStrechingDefault)
            val verboseMode = pref.getBoolean(KEY_ENABLE_VERBOSE_MODE, true)
            val castingAudioOnly = pref.getBoolean(KEY_CASTING_AUDIO_ONLY, false)
            val networkCaching = pref.getInt(KEY_NETWORK_CACHING_VALUE, 0).coerceIn(0, 60000)

            // Nağmedar 1.0 plays sound only: libVLC never decodes nor shows a picture (a film or a
            // video podcast plays as audio), nor its subtitles
            options.add("--no-video")
            if (castingAudioOnly) options.add("--no-sout-chromecast-video")
            options.add(if (timeStreching) "--audio-time-stretch" else "--no-audio-time-stretch")
            options.add("--stats")
            if (networkCaching > 0) options.add("--network-caching=$networkCaching")
            options.add("--audio-resampler")
            options.add("soxr")
            // Nağmedar: SoX's "high quality" recipe (SOXR_HQ, 3 of 0–4; the module's default is 2, medium)
            options.add("--soxr-resampler-quality=3")
            options.add("--audiotrack-session-id=$audiotrackSessionId")

            options.add("--keystore")
            options.add(if (AndroidUtil.isMarshMallowOrLater) "file_crypt,none" else "file_plaintext,none")
            options.add("--keystore-file")
            options.add(File(context.getDir("keystore", Context.MODE_PRIVATE), "file").absolutePath)
            options.add(if (verboseMode) "-vv" else "-v")
            // fixme comment temporarily
            if (!isVLC4()) {
                if (pref.getBoolean(KEY_CASTING_PASSTHROUGH, false))
                    options.add("--sout-chromecast-audio-passthrough")
                else
                    options.add("--no-sout-chromecast-audio-passthrough")
                options.add("--sout-chromecast-conversion-quality=" + pref.getString(KEY_CASTING_QUALITY, "2")!!)
            }
            options.add("--sout-keep")

            val customOptions = pref.getString(KEY_CUSTOM_LIBVLC_OPTIONS, null)
            if (!customOptions.isNullOrEmpty()) {
                val optionsArray = customOptions.split("\\r?\\n".toRegex()).toTypedArray()
                if (!optionsArray.isNullOrEmpty()) Collections.addAll(options, *optionsArray)
            }
            if (pref.getBoolean(KEY_PREFER_SMBV1, true))
                options.add("--smb-force-v1")
            if (!Settings.showTvUi) {
                //Ambisonic
                val hstfDir = context.getDir("vlc", Context.MODE_PRIVATE)
                val hstfPath = "${hstfDir.absolutePath}/.share/hrtfs/dodeca_and_7channel_3DSL_HRTF.sofa"
                options.add("--hrtf-file")
                options.add(hstfPath)
            }
            if (pref.getBoolean(KEY_AUDIO_REPLAY_GAIN_ENABLE, false)) {
                options.add("--audio-replay-gain-mode=${pref.getString(KEY_AUDIO_REPLAY_GAIN_MODE, "track")}")
                options.add("--audio-replay-gain-preamp=${pref.getString(KEY_AUDIO_REPLAY_GAIN_PREAMP, "0.0")}")
                options.add("--audio-replay-gain-default=${pref.getString(KEY_AUDIO_REPLAY_GAIN_DEFAULT, "-7.0")}")
                if (pref.getBoolean(KEY_AUDIO_REPLAY_GAIN_PEAK_PROTECTION, true))
                    options.add("--audio-replay-gain-peak-protection")
                else
                    options.add("--no-audio-replay-gain-peak-protection")
            }
            val soundFontFile = getSoundFontFile(context)
            if (soundFontFile.exists()) {
                options.add("--soundfont=${soundFontFile.path}")
            }
            if (BuildConfig.DEBUG) Log.d(this::class.java.simpleName, "VLC Options: ${options.joinToString(" ")}")
            return options
        }

    fun isAudioDigitalOutputEnabled(pref: SharedPreferences) = pref.getBoolean(KEY_AUDIO_DIGITAL_OUTPUT, false)

    fun setAudioDigitalOutputEnabled(pref: SharedPreferences, enabled: Boolean) {
        pref.putSingle(KEY_AUDIO_DIGITAL_OUTPUT, enabled)
    }

    fun getAout(pref: SharedPreferences): String? {
        var aout = -1
        try {
            aout = Integer.parseInt(pref.getString(KEY_AOUT, "-1")!!)
        } catch (ignored: NumberFormatException) {
        }

        val hwaout = VlcMigrationHelper.getAudioOutputFromDevice()
        if (hwaout == VlcMigrationHelper.AudioOutput.OPENSLES)
            aout = AOUT_OPENSLES

        return if (aout == AOUT_OPENSLES) "opensles" else if (aout == AOUT_AUDIOTRACK) "audiotrack" else null /* aaudio is the default */
    }

    fun setMediaOptions(media: IMedia, context: Context, flags: Int, hasRenderer: Boolean) {
        val paused = flags and MediaWrapper.MEDIA_PAUSED != 0
        val prefs = Settings.getInstance(context)

        if (paused) media.addOption(":start-paused")

        if (hasRenderer) {
            media.addOption(":sout-chromecast-audio-passthrough=" + prefs.getBoolean(KEY_CASTING_PASSTHROUGH, true))
            media.addOption(":sout-chromecast-conversion-quality=" + prefs.getString(KEY_CASTING_QUALITY, "2")!!)
        }
    }

    fun getEqualizerEnabledState(context: Context): Boolean {
        return Settings.getInstance(context).getBoolean("equalizer_enabled", false)
    }

    fun getSoundFontFile(context: Context)= File( context.getDir("soundfont", Context.MODE_PRIVATE)!!.path+"/soundfont.sf2")
}
