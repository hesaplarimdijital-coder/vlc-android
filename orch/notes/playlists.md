
## Playlists redesign (PR #34, branch story/playlists)
- Overview: `music/PlaylistOverview.kt` (cards, search, sort), logic `music/PlaylistCards.kt` (PlaylistCardData, PlaylistSort, build/matching/sorted), detail head `music/PlaylistHeader.kt` (ListDetailHeader), screen `PlaylistsScreen.kt` (PlaylistsBrowser: two-pane from Expanded, else overview -> detail with Back).
- Components added in `ui/components`: Cards.kt (NagCard, GradientTile), InlineFields.kt (NagSearchField, EditableTitle), Sheets.kt (MenuSheet, ActionMenuButton). ContextMenuArea/DenseTable got `asSheet`/`rowMenuAsSheet` (touch -> bottom sheet).
- PlaylistsState: `glances` (uris + cover tracks per playlist, read in background), `duplicate`.
- Not done: F2/Ctrl shortcuts for rename/search (no ShortcutRegistry entry), dropping files onto a list, real list "created" dates (smart lists sort after playlists).
