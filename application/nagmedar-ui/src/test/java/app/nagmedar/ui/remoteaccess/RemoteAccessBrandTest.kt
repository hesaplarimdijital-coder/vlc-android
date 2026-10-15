package app.nagmedar.ui.remoteaccess

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * S-1304: the remote access web page is Nağmedar's by patches and files laid over VLC's pinned
 * source, and nothing that keeps it safe was touched. These tests read the sources (tests run in
 * the module's directory): what the patches may touch, that the page's texts the app serves are in
 * both languages, and that the server's safeguards are still in the server.
 */
class RemoteAccessBrandTest {

    private val repo = File("../..")
    private val build = File(repo, "buildsystem/remoteaccess")
    private val server = File(repo, "application/remote-access-server/src/main/java/org/videolan/vlc/remoteaccessserver")

    private val patches: List<File> = File(build, "patches").listFiles { f -> f.extension == "patch" }.orEmpty().sortedBy { it.name }

    /** The web app's presentation files: the page, its theme, its header and About page. */
    private val presentation = setOf(
        "public/index.html",
        "src/main.js",
        "src/scss/app.scss",
        "src/stores/AppStore.js",
        "src/components/AppHeader.vue",
        "src/pages/AboutPage.vue",
    )

    private fun touched(patch: File): List<String> =
        patch.readLines().filter { it.startsWith("diff --git ") }.map { it.substringAfter(" a/").substringBefore(" b/") }

    @Test
    fun thePatchesAreNumberedInOrder() {
        assertTrue("${patches.size} patches", patches.size >= 3)
        patches.forEachIndexed { index, patch ->
            assertTrue(patch.name, patch.name.startsWith("%04d-".format(index + 1)))
            assertTrue(patch.name, patch.readText().startsWith("diff --git "))
        }
    }

    @Test
    fun thePatchesTouchOnlyPresentation() {
        val files = patches.flatMap { touched(it) }
        assertTrue(files.isNotEmpty())
        assertEquals("outside the presentation files", emptyList<String>(), files.filter { it !in presentation })
    }

    @Test
    fun thePatchesLeaveTheSignInAlone() {
        val sensitive = Regex("""challenge|sha256|authoriz|withCredentials|cookie|document\.cookie|verify-?code|/code|otp""", RegexOption.IGNORE_CASE)
        val changed = patches.flatMap { it.readLines() }.filter { (it.startsWith("+") || it.startsWith("-")) && !it.startsWith("+++") && !it.startsWith("---") }
        assertEquals(emptyList<String>(), changed.filter { sensitive.containsMatchIn(it) })
    }

    @Test
    fun theOwnFilesAreThere() {
        val assets = File(build, "assets")
        listOf(
            "public/logo.svg",
            "public/favicon.svg",
            "public/favicon.ico",
            "public/apple-touch-icon.png",
            "public/site.webmanifest",
            "src/scss/colors.scss",
        ).forEach { assertTrue(it, File(assets, it).length() > 0) }
        assertEquals(3, File(assets, "src/assets/fonts").listFiles { f -> f.extension == "woff2" }.orEmpty().size)
        assertTrue(File(build, "check-dist.sh").canRead())
        assertTrue(File(repo, "buildsystem/compile-remoteaccess.sh").readText().contains("check-dist.sh"))
    }

    @Test
    fun theManifestAndTheMarkNameNagmedar() {
        val assets = File(build, "assets/public")
        val manifest = File(assets, "site.webmanifest").readText()
        assertTrue(manifest.contains("\"name\": \"Nağmedar\""))
        assertTrue(!manifest.contains("VLC"))
        assertTrue(File(assets, "favicon.svg").readText().contains("Nağmedar"))
    }

    @Test
    fun theColoursAreTheThemes() {
        val colours = File(build, "assets/src/scss/colors.scss").readText()
        // NagTheme's dark accent and the light one, and the dark surface
        listOf("#8AB0FF", "#4C618C", "#111418").forEach { assertTrue(it, colours.contains(it, ignoreCase = true)) }
        assertTrue(!colours.contains("#ff8800", ignoreCase = true))
    }

    @Test
    fun theTextsTheServerGivesAreInBothLanguages() {
        val mapping = File(server, "TranslationMapping.kt").readText()
        val keys = Regex("""\b(APP_NAME|ABOUT_APP|ABOUT_COPYRIGHT|ABOUT_LICENSE|SOURCE_CODE|SOURCE_URL)\(R\.string\.(\w+)\)""").findAll(mapping).map { it.groupValues[2] }.toList()
        assertEquals(6, keys.size)
        val classic = "../resources/src/main/res"
        val english = File("$classic/values/strings.xml").readText()
        val turkish = File("$classic/values-tr/strings.xml").readText()
        fun has(file: String, key: String) = file.contains("name=\"$key\"")
        assertEquals("not in English", emptyList<String>(), keys.filter { !has(english, it) })
        // what is not translated (the copyright line, the address of the source) is said once
        val once = setOf("about_copyright", "ra_source_url")
        assertEquals("not in Turkish", emptyList<String>(), keys.filter { it !in once && !has(turkish, it) })
        assertTrue(english.contains("GNU General Public License"))
        assertTrue(english.contains("based on VLC for Android"))
    }

    @Test
    fun theServerKeepsItsSafeguards() {
        val routing = File(server, "routing")
        assertTrue(File(routing, "RemoteAccessRouting.kt").readText().contains("authenticate(\"user_session\""))
        assertTrue(File(routing, "RemoteAccessRoutingAuth.kt").readText().contains("isFlooding("))
        val engine = File(server, "RemoteAccessServer.kt").readText()
        assertTrue(engine.contains("sslConnector("))
        assertTrue(engine.contains("cookie<UserSession>(\"user_session\""))
        assertTrue(File(server, "RemoteAccessOTP.kt").readText().contains("sha256"))
    }

    @Test
    fun theWebAppIsBuiltInTheContinuousIntegration() {
        val ci = File(repo, ".github/workflows/ci.yml").readText()
        assertTrue(ci.contains("./.github/actions/remoteaccess-web"))
        val action = File(repo, ".github/actions/remoteaccess-web/action.yml").readText()
        assertTrue(action.contains("hashFiles("))
        assertTrue(action.contains("check-dist.sh"))
        assertTrue(action.contains("::error"))
    }
}
