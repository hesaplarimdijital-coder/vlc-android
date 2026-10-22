package app.nagmedar.ui.tvlab.remux

import android.content.res.Resources
import androidx.annotation.StringRes
import app.nagmedar.lab.CompatibilityTrack
import app.nagmedar.lab.CompatibilityTracks
import app.nagmedar.lab.MediaTech
import app.nagmedar.lab.RemuxPlan
import app.nagmedar.lab.StreamFidelity
import app.nagmedar.ui.R
import app.nagmedar.ui.tvlab.DeviceProfile
import app.nagmedar.ui.tvlab.audioKey

/** S-1207: the compatibility tracks [tech] needs on this device: one beside every audio track it cannot play. */
fun DeviceProfile.compatibilityTracks(tech: MediaTech): List<CompatibilityTrack> =
    CompatibilityTracks.suggest(tech) { allowsAudio(audioKey(it)) }

/** "Uyumluluk izi (E-AC-3 5.1)": the added track's title in the output, in the interface's language. */
fun compatibilityTitle(res: Resources, track: CompatibilityTrack): String =
    res.getString(R.string.nag_tvlab_remux_compat_title, track.spec.describe(track.track))

/** The plan with the tracks this device needs (S-1207), titled in the interface's language. */
fun RemuxPlan.withCompatibilityTracks(res: Resources, profile: DeviceProfile, tech: MediaTech): RemuxPlan =
    withCompatibilityTracks(profile.compatibilityTracks(tech)) { compatibilityTitle(res, it) }

/** The plan's lossless label of a stream: "Kayıpsız", "Kayıplı (eklenen iz)", "Kayıpsız (eklenen iz)". */
@get:StringRes
val StreamFidelity.label: Int
    get() = when (this) {
        StreamFidelity.Kept -> R.string.nag_tvlab_remux_fidelity_kept
        StreamFidelity.AddedLossy -> R.string.nag_tvlab_remux_fidelity_added_lossy
        StreamFidelity.AddedLossless -> R.string.nag_tvlab_remux_fidelity_added_lossless
    }
