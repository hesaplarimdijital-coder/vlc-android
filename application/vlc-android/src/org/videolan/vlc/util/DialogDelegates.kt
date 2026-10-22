package org.videolan.vlc.util

import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.LifecycleOwner
import org.videolan.libvlc.Dialog
import org.videolan.tools.Settings
import org.videolan.vlc.gui.dialogs.VlcLoginDialog
import org.videolan.vlc.gui.dialogs.VlcProgressDialog
import org.videolan.vlc.gui.dialogs.VlcQuestionDialog
import videolan.org.commontools.LiveEvent

private const val TAG = "DialogDelegate"

interface IDialogDelegate {
    fun observeDialogs(lco: LifecycleOwner, manager: IDialogManager)
}

interface IDialogManager {
    fun fireDialog(dialog: Dialog)
    fun dialogCanceled(dialog: Dialog?)
}

class DialogDelegate : IDialogDelegate {

    override fun observeDialogs(lco: LifecycleOwner, manager: IDialogManager) {
        dialogEvt.observe(lco) {
            when (it) {
                is Show -> manager.fireDialog(it.dialog)
                is Cancel -> manager.dialogCanceled(it.dialog)
            }
        }
    }

    companion object DialogsListener : Dialog.Callbacks {
        private val dialogEvt: LiveEvent<DialogEvt> = LiveEvent()
        var dialogCounter = 0

        override fun onProgressUpdate(dialog: Dialog.ProgressDialog) {
            if (LibVlcDialogHook.progress(dialog)) return
            val vlcProgressDialog = dialog.context as? VlcProgressDialog ?: return
            if (vlcProgressDialog.isVisible) vlcProgressDialog.updateProgress()
        }

        override fun onDisplay(dialog: Dialog.ErrorMessage) {
            dialogEvt.value = Cancel(dialog)
        }

        override fun onDisplay(dialog: Dialog.LoginDialog) = show(dialog)

        override fun onDisplay(dialog: Dialog.QuestionDialog) = show(dialog)

        override fun onDisplay(dialog: Dialog.ProgressDialog) = show(dialog)

        /**
         * Nağmedar (S-1301): the new interface answers or shows it, or it is kept for the new
         * interface until that is installed (a start by a drive or a file); VLC's window
         * (DialogActivity) shows only on a TV what the new interface does not take.
         */
        private fun show(dialog: Dialog) {
            if (LibVlcDialogHook.display(dialog)) return
            if (Settings.showTvUi) dialogEvt.value = Show(dialog) else dialog.dismiss()
        }

        override fun onCanceled(dialog: Dialog?) {
            if (dialog != null && LibVlcDialogHook.canceled(dialog)) return
            (dialog?.context as? DialogFragment)?.dismiss()
            dialogEvt.value = Cancel(dialog)
        }
    }
}

fun Fragment.showVlcDialog(dialog: Dialog) {
    activity?.showVlcDialog(dialog)
}

@Suppress("INACCESSIBLE_TYPE")
fun FragmentActivity.showVlcDialog(dialog: Dialog) {
    val dialogFragment = when (dialog) {
        is Dialog.LoginDialog -> VlcLoginDialog().apply {
            vlcDialog = dialog
        }
        is Dialog.QuestionDialog -> VlcQuestionDialog().apply {
            vlcDialog = dialog
        }
        is Dialog.ProgressDialog -> VlcProgressDialog().apply {
            vlcDialog = dialog
        }
        else -> null
    } ?: return
    val fm = supportFragmentManager
    dialogFragment.show(fm, "vlc_dialog_${++DialogDelegate.dialogCounter}")
}

private sealed class DialogEvt
private class Show(val dialog: Dialog) : DialogEvt()
private class Cancel(val dialog: Dialog?) : DialogEvt()