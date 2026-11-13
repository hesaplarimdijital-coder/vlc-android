# 1.0.8 = Desktop Kit 2.8.0 port. Scope: ALL (user 19:44 "Vazgeçtim hepsini taşı"); FAB only for Playlists "Yeni liste" (user's answer); no TokenField.
# status per kit commit (2.5.0..2.8.0)
52c293d shell padding ................ DONE 44cd5149
af35916 2e119c8 split pane ........... DONE abbf22df: rememberSplitState(initial, key) kept in PaneWidths; HorizontalSplitPane now used (music groups, playlists)
b05b6fc detail list state ............ N/A (list stays composed under detail)
64fd601 e445a66 top bar .............. DONE 319a24b7 (LocalScreenLeaving part N/A: bar from route)
444834f glass WebView pause .......... N/A
0a04973 b7115b6 motion ............... DONE 8bbc93e3
3c36f52 banner 2nd action, row preview, row ⋯ .. banner wraps (4e72); preview N/A (no shared list row); touch ⋯ DONE bcba2c85 (subscriptions, servers, queue; episodes/recordings/search/radio had it)
06efb85 setting row button, danger button, empty screen primary .. DONE 4e7211bf (danger on 5 buttons; StatePanel already had primary+heading)
4966743 toast duration, 48dp close ... N/A: notices & undo bars already pause on hover/focus + TalkBack timeout; no caller needs own duration; no close button (swipe)
6730c46 inspector may start hidden ... DONE 8b0ddb80
0182e5b punctuation chords ........... N/A (S-410 PhysicalKeys)
890ddb2 nav icon badges .............. DONE 220b7e6b (Podcasts: new episodes count)
94ea1af top bar title on rail ........ N/A (route title + back on rail already)
23c6431 table emphasis/cell/ellipsis/empty/sort read ... DONE (API; no caller uses emphasis/empty yet — see later)
6e70608 110e63a(sheet) 71aa61c(sheet) sheet ... DONE (this commit)
af0a8dc OnReselect ................... DONE abca07a5
d0df9f0 grid empty, palette by kind, strings .. strings DONE 307d8f1a; palette by kind was 1.0.7; grid empty N/A (screens show their StatePanel in the grid's place)
30b7198 7824cf6 tooltips ............. DONE 4f2f046c
b543aea cover cell ................... N/A-ish: playlist card shows ⋯ on hover/focus/touch already; podcast tile no buttons
3cefb08 text field cursor ............ N/A: no field where the app places the cursor or reads the selection
c46fe0e token field .................. SKIP (no address field)
7d51d30 table keys/frame/drag ........ DONE (table commit, local)
254531f b6607e6 ListDetailLayout ..... DONE abbf22df: ListDetailPanes room check + LocalDetailBeside; music groups movable+saved, MovableDetails per group, fade on new group; split kept (music, playlists); placeholder N/A (wide music always shows a group, podcasts list fills)
9203796 scrollbars ................... DONE (table: tables, menus, sheets, palette, More; grids in grid commit)
b2c662e 07fd541 grid zoom ............ DONE (podcast + playlist grids, PaneWidths.gridCellWidth)
d0a5a9b c0e8b43 navigation changes ... N/A: fixed destinations (no dynamic folders/accounts, parents, showIn, nav menus, collapsible sections); More lists by section already
c2ae3bb pull to refresh .............. DONE 69f9ae38: subscriptions (refresher round + notice), show page (repository.refresh), network folder
7222f3f swipe actions ................ DONE 9cd529bd: into SwipeToRemove (armed colour/icon/haptics, dp fling, stays out 1 s); no second action (no natural one)
e16d524 main action FAB .............. DONE a10dd087 (Playlists Yeni liste, user's answer)
110e63a 71aa61c tab stops ............ DONE (table, GridStops for podcast+playlist grids, inspector tabs)
6956e48 controlBorder ................ DONE c78836c9
f1cbc75 visual fixes ................. DONE c78836c9 + table commit (header/selection bar/checks); tab close 48dp N/A
fa1c37d inspector sheet narrow ....... DONE 8b0ddb80
344b0e3 480263d height class ......... DONE 5ae51083
b1cb40e menus on rail ≡ .............. DONE d274d410
44e3c53 baseline profile ............. DONE 64ba19a2: nagmedar-ui/src/main/baseline-prof.txt wide rules (components, shell, theme, input)
f5ec6cf review fixes (main action, zoom, pull) .. with those
066ead8 2491c74 e65b56b fb3dced c420b7d e463233 43eb4f5 ... N/A (sample, docs, release)

CI 43476870500 on 66c2fd8e (tooltips+sheet+table) running at 20:5x
Table commit + grid commit local after 66c2fd8e: grid=HEAD

Local commits after 301817d2 (CI 43478542332 running): 8b0ddb80 inspector, 5ae51083 height, abca07a5 reselect, d274d410 rail menus

Pushed 8ac37d65 (incl. test fixes for CI 43478542332: IconsTest Minus, ComponentStatesTest parkMouse on root[0]); CI dispatched 13 Nov ~21:3x
CI 43481331539 (8ac37d65): compile error Alignment (NagmedarShell) -> 432e39ea. CI 43483461673 (432e39ea): 2245 tests, 3 failed (MusicPanesTest x2 selector, ReselectTest 'Podcast' short label) -> 9e55522e; CI 43486533108 running.
Local: d844ba08 version WIP (amend with CHANGELOG before push).

## Final (14 Nov ~00:57 Istanbul)
Every line above landed; the local hashes in the older notes were rebased before the first push and are superseded by
the commits on master c2918042..cb0f20a2 (37). After the review: 0b8e77e2 nested sheets, 39bcc664 main action covered,
a754249e one refresh round, 58e37890 swipe stillThere, 43e4d57c scrollbar idle + tests; the walk's bug: 627633e6
table order; notes b1c0a9da, cb0f20a2. Delivered: personal APK 1.0.8 / 14010014, SHA-256 57fe542b…557fd1. Kit: PR #17.
