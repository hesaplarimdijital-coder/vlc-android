package org.videolan.vlc.gui.helpers

import android.annotation.SuppressLint
import android.support.v4.media.session.PlaybackStateCompat
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.widget.ViewStubCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import androidx.leanback.widget.BrowseFrameLayout
import androidx.leanback.widget.BrowseFrameLayout.OnFocusSearchListener
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.window.layout.FoldingFeature
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videolan.resources.VLCOptions
import org.videolan.tools.AppScope
import org.videolan.tools.KEY_AOUT
import org.videolan.tools.Settings
import org.videolan.vlc.PlaybackService
import org.videolan.vlc.R
import org.videolan.vlc.databinding.PlayerOptionItemBinding
import org.videolan.vlc.gui.AudioPlayerContainerActivity
import org.videolan.vlc.gui.BaseActivity
import org.videolan.vlc.gui.DiffUtilAdapter
import org.videolan.vlc.gui.dialogs.AudioControlsSettingsDialog
import org.videolan.vlc.gui.dialogs.EqualizerFragmentDialog
import org.videolan.vlc.gui.dialogs.JumpToTimeDialog
import org.videolan.vlc.gui.dialogs.PlaybackSpeedDialog
import org.videolan.vlc.gui.dialogs.SelectChapterDialog
import org.videolan.vlc.gui.dialogs.SleepTimerDialog
import org.videolan.vlc.gui.helpers.UiTools.addToPlaylist
import org.videolan.vlc.media.PlayerController
import org.videolan.vlc.util.TextUtils
import org.videolan.vlc.util.getScreenHeight
import org.videolan.vlc.util.isTalkbackIsEnabled
import org.videolan.vlc.util.share

private const val ACTION_AUDIO_DELAY = 2
private const val ACTION_SPU_DELAY = 3

private const val ID_SLEEP = 1L
private const val ID_JUMP_TO = 2L
private const val ID_BOOKMARK = 4L
private const val ID_CHAPTER_TITLE = 5L
private const val ID_PLAYBACK_SPEED = 6L
private const val ID_EQUALIZER = 7L
private const val ID_SAVE_PLAYLIST = 8L
private const val ID_PASSTHROUGH = 12L
private const val ID_ABREPEAT = 13L
private const val ID_SHOW_AUDIO_TIPS = 17L
private const val ID_SHOW_PLAYLIST_TIPS = 18L
private const val ID_AUDIO_CONTROL_SETTING = 20L
private const val ID_SHARE = 23L
@SuppressLint("ShowToast")
class PlayerOptionsDelegate(val activity: FragmentActivity, val service: PlaybackService, private val showABReapeat:Boolean = true)  {

    private lateinit var bookmarkClickedListener: () -> Unit
    private lateinit var recyclerview: RecyclerView
    private lateinit var rootView: FrameLayout
    var flags: Long = 0L
    private val toast by lazy(LazyThreadSafetyMode.NONE) { Toast.makeText(activity, "", Toast.LENGTH_SHORT) }

    private val res = activity.resources
    private val settings = Settings.getInstance(activity)
    private lateinit var abrBinding: PlayerOptionItemBinding
    private lateinit var ptBinding: PlayerOptionItemBinding
    private lateinit var sleepBinding: PlayerOptionItemBinding

    fun setup() {
        if (!this::recyclerview.isInitialized || PlayerController.playbackState == PlaybackStateCompat.STATE_STOPPED) return
        val options = mutableListOf<PlayerOption>()
        options.add(PlayerOption(ID_SLEEP, R.drawable.ic_sleep, res.getString(R.string.sleep_title)))
        options.add(PlayerOption(ID_PLAYBACK_SPEED, R.drawable.ic_speed, res.getString(R.string.playback_speed)))
        options.add(PlayerOption(ID_JUMP_TO, R.drawable.ic_jumpto, res.getString(R.string.jump_to_time)))
        options.add(PlayerOption(ID_EQUALIZER, R.drawable.ic_equalizer, res.getString(R.string.equalizer)))
        val chaptersCount = service.getChapters(-1)?.size ?: 0
        if (chaptersCount > 1) options.add(PlayerOption(ID_CHAPTER_TITLE, R.drawable.ic_chapter, res.getString(R.string.go_to_chapter)))
        if (::bookmarkClickedListener.isInitialized) options.add(PlayerOption(ID_BOOKMARK, R.drawable.ic_bookmark, res.getString(R.string.bookmarks)))
        if (showABReapeat) options.add(PlayerOption(ID_ABREPEAT, R.drawable.ic_abrepeat, res.getString(R.string.ab_repeat)))
        options.add(PlayerOption(ID_SAVE_PLAYLIST, R.drawable.ic_addtoplaylist, res.getString(R.string.playlist_save)))
        if (service.playlistManager.player.canDoPassthrough() && settings.getString(KEY_AOUT, "0") != "2")
            options.add(PlayerOption(ID_PASSTHROUGH, R.drawable.ic_passthrough, res.getString(R.string.audio_digital_title)))

        if (!Settings.showTvUi) {
            options.add(PlayerOption(ID_SHARE, R.drawable.ic_share, res.getString(R.string.share_track_info)))
            options.add(PlayerOption(ID_AUDIO_CONTROL_SETTING, R.drawable.ic_audio_controls, res.getString(R.string.control_setting)))
            options.add(PlayerOption(ID_SHOW_AUDIO_TIPS, R.drawable.ic_audiotips, res.getString(R.string.audio_player_tips)))
            options.add(PlayerOption(ID_SHOW_PLAYLIST_TIPS, R.drawable.ic_playlisttips, res.getString(R.string.playlist_tips)))
        }
        (recyclerview.adapter as OptionsAdapter).update(options)
    }

    fun show() {
        activity.findViewById<ViewStubCompat>(R.id.player_options_stub)?.let {
            rootView = it.inflate() as FrameLayout
            recyclerview = rootView.findViewById(R.id.options_list)
            val browseFrameLayout =  rootView.findViewById<BrowseFrameLayout>(R.id.options_background)
            browseFrameLayout.onFocusSearchListener = OnFocusSearchListener { focused, _ ->
                if (recyclerview.hasFocus()) focused // keep focus on recyclerview! DO NOT return recyclerview, but focused, which is a child of the recyclerview
                else null // someone else will find the next focus
            }
            if (recyclerview.layoutManager == null) recyclerview.layoutManager = LinearLayoutManager(activity, RecyclerView.VERTICAL, false)
            recyclerview.adapter = OptionsAdapter()
            recyclerview.itemAnimator = null

            rootView.setOnClickListener { hide() }
        }
        val windowInfoLayout = (activity as? BaseActivity)?.windowLayoutInfo
        val foldingFeature = windowInfoLayout?.displayFeatures?.firstOrNull() as? FoldingFeature
        if (foldingFeature?.isSeparating == true && foldingFeature.occlusionType == FoldingFeature.OcclusionType.FULL && foldingFeature.orientation == FoldingFeature.Orientation.HORIZONTAL) {
            val halfScreenSize = activity.getScreenHeight() - foldingFeature.bounds.bottom
            val lp = (rootView.layoutParams as ViewGroup.MarginLayoutParams)
            lp.height = halfScreenSize
            if (lp is FrameLayout.LayoutParams) lp.gravity = Gravity.BOTTOM
            rootView.layoutParams = lp
        } else {
             val lp = (rootView.layoutParams as ViewGroup.MarginLayoutParams)
            lp.height = FrameLayout.LayoutParams.MATCH_PARENT
            if (lp is FrameLayout.LayoutParams) lp.gravity = Gravity.BOTTOM
            rootView.layoutParams = lp
        }
        setup()
        rootView.visibility = View.VISIBLE
        if (Settings.showTvUi) AppScope.launch {
            withContext(Dispatchers.IO){ delay(100L) }
            val position = (recyclerview.layoutManager as LinearLayoutManager).findFirstVisibleItemPosition()
            (recyclerview.layoutManager as LinearLayoutManager).findViewByPosition(position)?.requestFocus()
        } else if (activity.isTalkbackIsEnabled()) {
            AppScope.launch {
                withContext(Dispatchers.IO){ delay(100L) }
                val linearLayoutManager = recyclerview.layoutManager as LinearLayoutManager
                linearLayoutManager.findViewByPosition(linearLayoutManager.findFirstVisibleItemPosition())?.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED)
            }
        }
    }

    fun hide() {
        rootView.visibility = View.GONE
    }

    fun setBookmarkClickedListener(listener:()->Unit) {
        this.bookmarkClickedListener = listener
    }

    fun onClick(option: PlayerOption) {
        when (option.id) {
            ID_SLEEP -> {
                showFragment(ID_SLEEP)
            }
            ID_PASSTHROUGH -> togglePassthrough()
            ID_ABREPEAT -> {
                hide()
                service.playlistManager.toggleABRepeat()
            }
            ID_SHOW_AUDIO_TIPS -> {
                hide()
                val audioPlayerContainerActivity = activity as AudioPlayerContainerActivity
                audioPlayerContainerActivity.findViewById<ViewStubCompat>(R.id.audio_player_tips)?.let {
                    audioPlayerContainerActivity.tipsDelegate.init(it)
                }
            }
            ID_SHOW_PLAYLIST_TIPS -> {
                hide()
                val audioPlayerContainerActivity = activity as AudioPlayerContainerActivity
                audioPlayerContainerActivity.findViewById<ViewStubCompat>(R.id.audio_playlist_tips)?.let {
                    audioPlayerContainerActivity.playlistTipsDelegate.init(it)
                }
            }
            ID_BOOKMARK -> {
                hide()
                bookmarkClickedListener.invoke()
            }
            ID_SHARE -> {
                hide()
                service.playlistManager.getCurrentMedia()?.let { media ->
                    val trackInfo = buildString {
                        var started = false
                        if (media.title.isNotBlank()) {
                            append(media.title)
                            started = true
                        }
                        if (media.albumName.isNotBlank()) {
                            if (started) append(" ${TextUtils.SEPARATOR} ")
                            started = true
                            append(media.albumName)
                        }
                        if (media.artistName.isNotBlank()) {
                            if (started) append(" ${TextUtils.SEPARATOR} ")
                            append(media.artistName)
                        }
                    }
                    activity.share("", activity.getString(R.string.share_track, trackInfo))
                }
            }
            ID_AUDIO_CONTROL_SETTING -> {
                hide()
                val audioControlsSettingsDialog = AudioControlsSettingsDialog()
                audioControlsSettingsDialog.show(activity.supportFragmentManager, "fragment_audio_controls_settings")
            }
            else -> showFragment(option.id)
        }
    }

    private fun showFragment(id: Long) {
        val newFragment: DialogFragment
        val tag: String
        when (id) {
            ID_PLAYBACK_SPEED -> {
                newFragment = PlaybackSpeedDialog.newInstance()
                tag = "playback_speed"
            }
            ID_JUMP_TO -> {
                newFragment = JumpToTimeDialog.newInstance()
                tag = "time"
            }
            ID_SLEEP -> {
                newFragment = SleepTimerDialog.newInstance()
                tag = "time"
            }
            ID_CHAPTER_TITLE -> {
                newFragment = SelectChapterDialog.newInstance()
                tag = "select_chapter"
            }
            ID_EQUALIZER -> {
                newFragment = EqualizerFragmentDialog.newInstance()
                tag = "equalizer"
            }
            ID_SAVE_PLAYLIST -> {
                activity.addToPlaylist(service.media)
                hide()
                return
            }
            else -> return
        }
        newFragment.show(activity.supportFragmentManager, tag)
        hide()
    }

    private fun togglePassthrough() {
        val enabled = !VLCOptions.isAudioDigitalOutputEnabled(settings)
        if (service.setAudioDigitalOutputEnabled(enabled)) {
            ptBinding.optionIcon.setImageResource(if (enabled) R.drawable.ic_passthrough_on
            else UiTools.getResourceFromAttribute(activity, R.attr.ic_passthrough))
            VLCOptions.setAudioDigitalOutputEnabled(settings, enabled)
            toast.setText(res.getString(if (enabled) R.string.audio_digital_output_enabled else R.string.audio_digital_output_disabled))
        } else
            toast.setText(R.string.audio_digital_failed)
        toast.show()
    }

    fun isShowing() = rootView.visibility == View.VISIBLE

    private inner class OptionsAdapter : DiffUtilAdapter<PlayerOption, OptionsAdapter.ViewHolder>() {

        private lateinit var layountInflater: LayoutInflater

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            if (!this::layountInflater.isInitialized) layountInflater = LayoutInflater.from(parent.context)
            return ViewHolder(PlayerOptionItemBinding.inflate(layountInflater, parent, false))
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val option = dataset[position]
            holder.binding.option = option
            when (option.id) {
                ID_ABREPEAT -> abrBinding = holder.binding
                ID_PASSTHROUGH -> ptBinding = holder.binding
                ID_SLEEP -> sleepBinding = holder.binding
            }
            holder.binding.optionIcon.setImageResource(option.icon)
        }

        inner class ViewHolder(val binding: PlayerOptionItemBinding) : RecyclerView.ViewHolder(binding.root) {

            init {
                itemView.setOnClickListener { onClick(dataset[layoutPosition]) }
            }
        }
    }
}

data class PlayerOption(val id: Long, val icon: Int, val title: String)
