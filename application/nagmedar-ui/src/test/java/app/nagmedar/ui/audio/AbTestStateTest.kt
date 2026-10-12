package app.nagmedar.ui.audio

import app.nagmedar.audio.AbSide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** S-912: the A/B test's sides, the blind test's X hidden until its end, and what a score says. */
class AbTestStateTest {

    private val sent = mutableListOf<AbSide>()

    private fun state(seed: Int = 7) = AbTestState(engine = { sent += it }, random = Random(seed))

    @Test
    fun theOpenTestSwitchesTheEngine() {
        val ab = state()
        ab.listen(AbChoice.B)
        assertEquals(listOf(AbSide.B), sent)
        // Alt+B: back to A
        ab.next()
        assertEquals(AbChoice.A, ab.choice)
        assertEquals(listOf(AbSide.B, AbSide.A), sent)
        // X is the blind test's only
        ab.listen(AbChoice.X)
        assertEquals(AbChoice.A, ab.choice)
        assertEquals(2, sent.size)
    }

    @Test
    fun theBlindTestHidesXUntilItsEnd() {
        val ab = state()
        ab.startBlind(10)
        assertEquals(AbTestState.Phase.Blind, ab.phase)
        assertEquals(AbChoice.X, ab.choice)
        val xs = ab.rounds.map { it.x }
        assertTrue(ab.rounds.all { it.answer == null })
        // X plays what the round drew; A and B stay themselves
        assertEquals(xs[0], sent.last())
        ab.listen(AbChoice.A)
        assertEquals(AbSide.A, sent.last())
        ab.listen(AbChoice.B)
        assertEquals(AbSide.B, sent.last())
        ab.listen(AbChoice.X)
        assertEquals(xs[0], sent.last())
        // Alt+B goes A, B, X
        ab.next()
        assertEquals(AbChoice.A, ab.choice)
        ab.next()
        assertEquals(AbChoice.B, ab.choice)
        ab.next()
        assertEquals(AbChoice.X, ab.choice)
        // right on eight rounds, wrong on the last two; each new round starts on its X
        xs.forEachIndexed { i, x ->
            assertEquals(i, ab.round)
            assertEquals(AbTestState.Phase.Blind, ab.phase)
            ab.answer(if (i < 8) x else if (x == AbSide.A) AbSide.B else AbSide.A)
            if (i < 9) {
                assertEquals(AbChoice.X, ab.choice)
                assertEquals("round ${i + 2} plays its X", xs[i + 1], sent.last())
            }
        }
        assertEquals(AbTestState.Phase.Results, ab.phase)
        assertEquals(8, ab.score)
        assertEquals(List(10) { it < 8 }, ab.rounds.map { it.correct })
        // the chain plays again
        assertEquals(AbChoice.A, ab.choice)
        assertEquals(AbSide.A, sent.last())
        // 56 of 1024 ways to guess 8 or more right: not under 5 %
        assertEquals(56.0 / 1024, ab.chance, 1e-12)
        ab.close()
        assertEquals(AbTestState.Phase.Open, ab.phase)
        assertTrue(ab.rounds.isEmpty())
    }

    @Test
    fun bothSidesAreDrawn() {
        val ab = state(seed = 1)
        ab.startBlind(20)
        assertEquals(20, ab.rounds.size)
        assertTrue(ab.rounds.any { it.x == AbSide.A } && ab.rounds.any { it.x == AbSide.B })
        // the round count chosen
        ab.close()
        ab.roundCount = 16
        ab.startBlind()
        assertEquals(16, ab.rounds.size)
    }

    @Test
    fun theEngineIsBelieved() {
        val ab = state()
        ab.listen(AbChoice.B)
        // an engine made anew plays A: the open test follows it
        ab.sync(AbSide.A, chainActive = true)
        assertEquals(AbChoice.A, ab.choice)
        // a chain that no longer changes the sound leaves nothing to compare: A
        ab.listen(AbChoice.B)
        ab.sync(AbSide.B, chainActive = false)
        assertEquals(AbChoice.A, ab.choice)
        assertEquals(AbSide.A, sent.last())
        // the blind test puts its side back on the engine
        ab.startBlind(4)
        val x = ab.rounds[0].x
        val other = if (x == AbSide.A) AbSide.B else AbSide.A
        sent.clear()
        ab.sync(other, chainActive = true)
        assertEquals(listOf(x), sent)
        ab.sync(x, chainActive = true)
        assertEquals(listOf(x), sent)
    }

    @Test
    fun guessing() {
        assertEquals(1.0, guessingChance(16, 0), 0.0)
        assertEquals(0.5, guessingChance(1, 1), 0.0)
        assertEquals(1.0 / 65_536, guessingChance(16, 16), 1e-15)
        // 12 of 16: 2517 of 65536 ways; 11 of 16: 6885
        assertEquals(2517.0 / 65_536, guessingChance(16, 12), 1e-12)
        assertEquals(6885.0 / 65_536, guessingChance(16, 11), 1e-12)
        assertTrue(guessingChance(16, 12) < AbTestState.HEARD)
        assertTrue(guessingChance(16, 11) > AbTestState.HEARD)
        assertEquals(0.0, guessingChance(10, 11), 0.0)
    }
}
