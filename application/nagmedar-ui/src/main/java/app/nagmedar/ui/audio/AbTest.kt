package app.nagmedar.ui.audio

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import app.nagmedar.audio.AbSide
import kotlin.random.Random

/** What plays in the A/B test: the chain (A), the chain bypassed at its level (B), or the blind test's unknown (X). */
enum class AbChoice { A, B, X }

/** One round of the blind test: what X was, and what the listener said it was. */
data class AbRound(val x: AbSide, val answer: AbSide? = null) {
    val correct: Boolean get() = answer == x
}

/**
 * S-912: the A/B listening test. A plays the DSP chain as set; B bypasses it at the level the
 * chain gives, which the engine measures and matches. The blind test hides one of the two behind
 * X, drawn anew each round; the listener says which it is, and the answers show only at the end.
 *
 * [engine] puts a side on the engine; the app's is [AbTest.state], which the equaliser page and
 * the Alt+B shortcut share.
 */
@Stable
class AbTestState(
    private val engine: (AbSide) -> Unit,
    private val random: Random = Random.Default,
) {
    enum class Phase { Open, Blind, Results }

    var phase by mutableStateOf(Phase.Open)
        private set

    /** What plays now. */
    var choice by mutableStateOf(AbChoice.A)
        private set

    /** The blind test's rounds: the one going on, or the one just ended. */
    var rounds by mutableStateOf<List<AbRound>>(emptyList())
        private set

    /** The round being answered, from 0. */
    var round by mutableIntStateOf(0)
        private set

    /** How many rounds the next blind test has. */
    var roundCount by mutableIntStateOf(DEFAULT_ROUNDS)

    val score: Int get() = rounds.count { it.correct }

    /** The chance of [score] or better by guessing alone; under [HEARD] the difference is heard. */
    val chance: Double get() = guessingChance(rounds.size, score)

    /** The side that plays now: X's is the round's. */
    val side: AbSide
        get() = when (choice) {
            AbChoice.A -> AbSide.A
            AbChoice.B -> AbSide.B
            AbChoice.X -> rounds.getOrNull(round)?.x ?: AbSide.A
        }

    fun listen(to: AbChoice) {
        if (to == AbChoice.X && phase != Phase.Blind) return
        choice = to
        engine(side)
    }

    /** The shortcut: A ↔ B; in the blind test A → B → X → A. */
    fun next() = listen(
        when (choice) {
            AbChoice.A -> AbChoice.B
            AbChoice.B -> if (phase == Phase.Blind) AbChoice.X else AbChoice.A
            AbChoice.X -> AbChoice.A
        },
    )

    fun startBlind(count: Int = roundCount) {
        rounds = List(count.coerceAtLeast(1)) { AbRound(if (random.nextBoolean()) AbSide.B else AbSide.A) }
        round = 0
        phase = Phase.Blind
        listen(AbChoice.X)
    }

    /** X is [side], says the listener: on to the next round, or to the results after the last; X plays the new round. */
    fun answer(side: AbSide) {
        if (phase != Phase.Blind) return
        rounds = rounds.mapIndexed { i, r -> if (i == round) r.copy(answer = side) else r }
        if (round + 1 < rounds.size) {
            round++
            listen(AbChoice.X)
        } else {
            phase = Phase.Results
            listen(AbChoice.A)
        }
    }

    /** The blind test given up, or its results put away: A plays again. */
    fun close() {
        phase = Phase.Open
        rounds = emptyList()
        round = 0
        listen(AbChoice.A)
    }

    /**
     * The engine says [engineSide] plays. An engine made anew (the service restarted) has forgotten
     * the side: the open test takes the engine's word, the blind test puts its side back. A chain
     * that no longer changes anything leaves nothing to compare: A again.
     */
    fun sync(engineSide: AbSide, chainActive: Boolean) {
        when (phase) {
            Phase.Open -> when {
                !chainActive && choice == AbChoice.B -> listen(AbChoice.A)
                engineSide != side -> choice = if (engineSide == AbSide.B) AbChoice.B else AbChoice.A
            }
            Phase.Blind -> if (engineSide != side) engine(side)
            Phase.Results -> Unit
        }
    }

    companion object {
        const val DEFAULT_ROUNDS = 16
        val ROUND_COUNTS = listOf(10, 16, 20)

        /** Below this chance of guessing as well, the difference is heard. */
        const val HEARD = 0.05
    }
}

/** The app's A/B test: one, whichever screen or key reaches it. */
object AbTest {
    // made in the global snapshot: the object is often first touched inside a window's composition
    val state = Snapshot.global { AbTestState(engine = { side -> MusicEngineAccess.controller?.abSide = side }) }
}

/** The chance of [correct] or more right answers out of [rounds] by guessing: the binomial tail at one half. */
fun guessingChance(rounds: Int, correct: Int): Double {
    if (rounds <= 0 || correct <= 0) return 1.0
    if (correct > rounds) return 0.0
    var ways = 1.0 // C(rounds, k)
    var sum = 0.0
    for (k in 0..rounds) {
        if (k >= correct) sum += ways
        ways = ways * (rounds - k) / (k + 1)
    }
    return sum / Math.pow(2.0, rounds.toDouble())
}
