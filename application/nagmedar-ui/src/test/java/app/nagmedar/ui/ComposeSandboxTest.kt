package app.nagmedar.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Every Compose test runs in Robolectric's native graphics sandbox (robolectric.properties says
 * why). Compose's snapshot manager, once a Compose test starts it in a sandbox, posts its work on
 * the main looper; a plain test of that sandbox that changes Compose state leaves the work behind,
 * Robolectric drops it between tests, and every Compose test after it waits a minute for an idle
 * that never comes. With no Compose test in the legacy sandbox, its plain tests start nothing.
 */
class ComposeSandboxTest {

    private val root = File("src/test/java")

    private val tests: Map<String, List<String>> by lazy {
        root.walkTopDown().filter { it.isFile && it.extension == "kt" }
            .associate { it.relativeTo(root).invariantSeparatorsPath to it.readLines() }
    }

    private val composeRule = Regex("""\bcreate(?:Android)?ComposeRule\b""")

    /** The annotations of each Robolectric test class of [lines]: the lines from @RunWith to its class. */
    private fun testClasses(lines: List<String>): List<List<String>> =
        lines.indices.filter { lines[it].trimStart().startsWith("@RunWith(") }.map { start ->
            var end = start
            while (end + 1 < lines.size && lines[end + 1].trimStart().startsWith("@")) end++
            lines.subList(start, end + 1)
        }

    @Test
    fun everyComposeTestRunsInTheNativeSandbox() {
        val compose = tests.filterValues { lines -> lines.any { composeRule.containsMatchIn(it) } }
        assertTrue("${compose.size} Compose tests", compose.size > 50)
        val legacy = compose.filter { (_, lines) ->
            val classes = testClasses(lines)
            classes.isEmpty() || classes.any { annotations -> annotations.none { "@GraphicsMode(GraphicsMode.Mode.NATIVE)" in it } }
        }.keys.sorted()
        assertEquals(emptyList<String>(), legacy)
    }
}
