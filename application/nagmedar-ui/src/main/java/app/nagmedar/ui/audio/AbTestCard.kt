package app.nagmedar.ui.audio

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.nagmedar.audio.AbSide
import app.nagmedar.audio.SignalPath
import app.nagmedar.ui.R
import app.nagmedar.ui.components.NagButton
import app.nagmedar.ui.components.SegmentedChoice
import app.nagmedar.ui.input.ShortcutId
import app.nagmedar.ui.input.ShortcutRegistry
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagTheme
import java.text.NumberFormat
import java.util.Locale

/**
 * S-912: the A/B listening test: A or B in the open, at the same level; the blind test's rounds,
 * X hidden; then its answers and what they say. It works while the music engine plays through
 * a DSP chain that changes the sound ([path] says so).
 */
@Composable
fun AbTestCard(ab: AbTestState, path: SignalPath?, modifier: Modifier = Modifier) {
    val chainActive = path?.dspActive == true
    // the engine's word on the side: an engine made anew has forgotten it
    LaunchedEffect(path?.abSide, chainActive) {
        val side = path?.abSide ?: return@LaunchedEffect
        ab.sync(side, chainActive)
    }
    EqCard(stringResource(R.string.nag_ab_title), modifier, hint = stringResource(R.string.nag_ab_hint)) {
        when (ab.phase) {
            AbTestState.Phase.Open -> OpenTest(ab, path)
            AbTestState.Phase.Blind -> BlindTest(ab)
            AbTestState.Phase.Results -> BlindResults(ab)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.OpenTest(ab: AbTestState, path: SignalPath?) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val locale = LocalContext.current.resources.locale()
    val ready = path != null && path.dspActive
    SegmentedChoice(
        options = listOf(AbChoice.A to stringResource(R.string.nag_ab_side_a), AbChoice.B to stringResource(R.string.nag_ab_side_b)),
        selected = ab.choice,
        onSelect = ab::listen,
        modifier = Modifier.fillMaxWidth(),
        fill = true,
        enabled = ready,
    )
    when {
        path == null -> Note(stringResource(R.string.nag_ab_idle))
        !path.dspActive -> Note(stringResource(R.string.nag_ab_identity))
        else -> {
            val onB = path.abSide == AbSide.B
            val level = if (onB) path.abGainDb else path.dspLevelDb
            if (!onB && level == 0.0) {
                Note(stringResource(R.string.nag_ab_measuring))
            } else {
                Text(stringResource(R.string.nag_ab_level, decibels(level, locale)), style = NagTheme.type.labelSmall, color = colors.textSecondary)
            }
            if (ab.choice == AbChoice.B) Text(stringResource(R.string.nag_ab_b_plays), style = NagTheme.type.labelSmall, color = colors.warning)
            KeyHint(blind = false)
        }
    }
    Spacer(Modifier.height(dimens.gapXs))
    Text(stringResource(R.string.nag_ab_blind), style = NagTheme.type.bodyStrong, color = colors.text)
    Note(stringResource(R.string.nag_ab_blind_hint))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(dimens.gapM), verticalArrangement = Arrangement.spacedBy(dimens.gapXs)) {
        Row(Modifier.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.nag_ab_rounds), style = NagTheme.type.label, color = colors.textSecondary)
            Spacer(Modifier.width(dimens.gapS))
            val integer = NumberFormat.getIntegerInstance(locale)
            SegmentedChoice(
                options = AbTestState.ROUND_COUNTS.map { it to integer.format(it.toLong()) },
                selected = ab.roundCount,
                onSelect = { ab.roundCount = it },
            )
        }
        Box(Modifier.heightIn(min = 48.dp), contentAlignment = Alignment.CenterStart) {
            NagButton(stringResource(R.string.nag_ab_start), { ab.startBlind() }, primary = true, enabled = ready)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.BlindTest(ab: AbTestState) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    Text(stringResource(R.string.nag_ab_round, ab.round + 1, ab.rounds.size), style = NagTheme.type.bodyStrong, color = colors.text)
    RoundDots(done = ab.round, count = ab.rounds.size)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.nag_ab_playing), style = NagTheme.type.label, color = colors.textSecondary)
        Spacer(Modifier.width(dimens.gapS))
        SegmentedChoice(
            options = listOf(
                AbChoice.A to stringResource(R.string.nag_ab_a),
                AbChoice.B to stringResource(R.string.nag_ab_b),
                AbChoice.X to stringResource(R.string.nag_ab_x),
            ),
            selected = ab.choice,
            onSelect = ab::listen,
        )
    }
    Text(stringResource(R.string.nag_ab_which), style = NagTheme.type.label, color = colors.text)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(dimens.gapS), verticalArrangement = Arrangement.spacedBy(dimens.gapXs)) {
        NagButton(stringResource(R.string.nag_ab_is_a), { ab.answer(AbSide.A) }, primary = true)
        NagButton(stringResource(R.string.nag_ab_is_b), { ab.answer(AbSide.B) }, primary = true)
    }
    KeyHint(blind = true)
    NagButton(stringResource(R.string.nag_ab_quit), { ab.close() })
}

/** The rounds as dots: those answered filled, this one ringed; what any of them was stays hidden. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RoundDots(done: Int, count: Int) {
    val colors = NagTheme.colors
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(count) { i ->
            val dot = Modifier.size(8.dp)
            Box(
                when {
                    i < done -> dot.background(colors.accent, CircleShape)
                    i == done -> dot.border(1.5.dp, colors.accent, CircleShape)
                    else -> dot.border(1.dp, colors.borderStrong, CircleShape)
                },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.BlindResults(ab: AbTestState) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val locale = LocalContext.current.resources.locale()
    val heard = ab.chance < AbTestState.HEARD
    Text(stringResource(R.string.nag_ab_score, ab.score, ab.rounds.size), style = NagTheme.type.title, color = colors.text)
    Text(stringResource(R.string.nag_ab_chance, chanceText(ab.chance, locale)), style = NagTheme.type.labelSmall, color = colors.textSecondary)
    Text(
        stringResource(if (heard) R.string.nag_ab_heard else R.string.nag_ab_not_heard),
        style = NagTheme.type.bodyStrong,
        color = if (heard) colors.success else colors.textSecondary,
    )
    // the answers, round by round
    val sideA = stringResource(R.string.nag_ab_a)
    val sideB = stringResource(R.string.nag_ab_b)
    fun name(side: AbSide?) = when (side) {
        AbSide.A -> sideA
        AbSide.B -> sideB
        null -> ""
    }
    val right = stringResource(R.string.nag_ab_right)
    val wrong = stringResource(R.string.nag_ab_wrong)
    Column(Modifier.fillMaxWidth().padding(top = dimens.gapXs)) {
        ResultRow(stringResource(R.string.nag_ab_col_round), stringResource(R.string.nag_ab_col_x), stringResource(R.string.nag_ab_col_answer), "", header = true)
        ab.rounds.forEachIndexed { i, r ->
            ResultRow(
                NumberFormat.getIntegerInstance(locale).format((i + 1).toLong()), name(r.x), name(r.answer),
                if (r.correct) right else wrong, correct = r.correct,
            )
        }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(dimens.gapS), verticalArrangement = Arrangement.spacedBy(dimens.gapXs)) {
        NagButton(stringResource(R.string.nag_ab_again), { ab.startBlind(ab.rounds.size) }, primary = true)
        NagButton(stringResource(R.string.nag_ab_close), { ab.close() })
    }
}

@Composable
private fun ResultRow(round: String, x: String, answer: String, verdict: String, header: Boolean = false, correct: Boolean = false) {
    val colors = NagTheme.colors
    val style = if (header) NagTheme.type.labelSmall else NagTheme.type.label
    val tone = if (header) colors.textTertiary else colors.textSecondary
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 22.dp)
            // a round is read as one: "3, B, A, wrong"
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(round, style = style, color = tone, modifier = Modifier.weight(0.6f))
        Text(x, style = style, color = if (header) tone else colors.text, modifier = Modifier.weight(1f))
        Text(answer, style = style, color = if (header) tone else colors.text, modifier = Modifier.weight(1.2f))
        Text(verdict, style = style, color = if (correct) colors.success else colors.danger, modifier = Modifier.weight(1f))
    }
}

/** "3.8%", "%3,8"; a chance too small to round, "< 0.1%". */
private fun chanceText(chance: Double, locale: Locale): String {
    val format = NumberFormat.getPercentInstance(locale).apply { maximumFractionDigits = 1 }
    return if (chance < 0.001) "< " + format.format(0.001) else format.format(chance)
}

/** The key that switches what plays, for a keyboard and a mouse; none by touch or when it was taken away. */
@Composable
private fun KeyHint(blind: Boolean) {
    if (NagTheme.inputMode != InputMode.Pointer) return
    val keys = ShortcutRegistry.chordLabel(ShortcutId.AbSwitch, LocalContext.current.resources) ?: return
    Text(
        stringResource(if (blind) R.string.nag_ab_keys_blind else R.string.nag_ab_keys, keys),
        style = NagTheme.type.labelSmall,
        color = NagTheme.colors.textTertiary,
    )
}

@Composable
private fun Note(text: String) = Text(text, style = NagTheme.type.labelSmall, color = NagTheme.colors.textTertiary)
