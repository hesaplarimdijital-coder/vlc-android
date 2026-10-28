package app.nagmedar.ui.player.equalizer

import androidx.annotation.StringRes
import app.nagmedar.ui.R

/**
 * VLC's equaliser presets, in libVLC's order (an index into them is the preset libVLC makes), by
 * VLC's own names for them.
 */
enum class EqualizerPreset(val key: String, @StringRes val label: Int) {
    Flat("flat", R.string.nag_eq_flat),
    Classical("classical", R.string.nag_eq_classical),
    Club("club", R.string.nag_eq_club),
    Dance("dance", R.string.nag_eq_dance),
    FullBass("fullbass", R.string.nag_eq_full_bass),
    FullBassTreble("fullbasstreble", R.string.nag_eq_full_bass_treble),
    FullTreble("fulltreble", R.string.nag_eq_full_treble),
    Headphones("headphones", R.string.nag_eq_headphones),
    LargeHall("largehall", R.string.nag_eq_large_hall),
    Live("live", R.string.nag_eq_live),
    Party("party", R.string.nag_eq_party),
    Pop("pop", R.string.nag_eq_pop),
    Reggae("reggae", R.string.nag_eq_reggae),
    Rock("rock", R.string.nag_eq_rock),
    Ska("ska", R.string.nag_eq_ska),
    Soft("soft", R.string.nag_eq_soft),
    SoftRock("softrock", R.string.nag_eq_soft_rock),
    Techno("techno", R.string.nag_eq_techno),
    ;

    companion object {
        fun of(key: String?): EqualizerPreset? = entries.firstOrNull { it.key == key }
    }
}

/** A film's equaliser choice that turns libVLC's equaliser off (the others are an [EqualizerPreset]'s key, or none: the player's own). */
const val EQUALIZER_OFF = "off"
