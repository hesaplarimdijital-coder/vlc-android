package app.nagmedar.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.nagmedar.ui.NagmedarPreferences
import app.nagmedar.ui.R
import app.nagmedar.ui.library.LibraryScopes
import app.nagmedar.ui.library.LibrarySection
import app.nagmedar.ui.library.SectionSourcesEditor
import app.nagmedar.ui.library.SourceActions
import app.nagmedar.ui.library.rememberSourceActions
import app.nagmedar.ui.theme.NagTheme

/**
 * The folders the music shows, chosen from the Sources step: the very
 * rows of Settings › Library, with the same picker, suggestions and store, one card for each
 * section. A section without folders shows all of the library, so nothing here has to be chosen.
 */
@Composable
internal fun SectionFolders(prefs: NagmedarPreferences, actions: SourceActions = rememberSourceActions(prefs)) {
    SectionFolders(prefs.librarySources, actions)
}

@Composable
internal fun SectionFolders(scopes: LibraryScopes, actions: SourceActions) {
    val colors = NagTheme.colors
    val shape = RoundedCornerShape(14.dp)
    Column(Modifier.fillMaxWidth().testTag("first-run-sections"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                stringResource(R.string.nag_fr_sources_sections),
                style = NagTheme.type.bodyStrong,
                color = colors.text,
                modifier = Modifier.semantics { heading() },
            )
            Text(stringResource(R.string.nag_fr_sources_sections_desc), style = NagTheme.type.labelSmall, color = colors.textSecondary)
        }
        LibrarySection.entries.forEach { section ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(colors.surface, shape)
                    .border(1.dp, colors.borderStrong, shape)
                    .padding(horizontal = 14.dp, vertical = 2.dp),
            ) {
                SectionSourcesEditor(section, scopes, actions)
            }
        }
    }
}
