# video-motion (PR #65, branch story/video-motion) — notes for the next story
- Motion: NagMotion.sheetEnter/sheetExit (SHEET_EXIT = MEDIUM+50) and detailEnter/detailExit(rtl) (DETAIL_SHIFT, DETAIL_FADE_DELAY) in theme/Motion.kt; NagmedarApp overlays use AnimatedVisibility on the existing flags. Phone player Back = DetailBackHandler + rememberDetailMotion(open=true) + detailLayer in a graphicsLayer.
- View-world animation: PanelHost slides with a ValueAnimator (NagMotion durations, TimeInterpolator over NagMotion easings), margin updated each frame; detach cancels.
- TouchControls/DesktopControls have `underControls` slot (above the gesture layer, below the bars); DesktopPlayerChrome passes the visualiser as movableContentWithReceiverOf<BoxScope, Dp>.
- DialogFrame provides LocalDialogDismiss; DialogCard shows KeyCap only with a QWERTY keyboard, else × (tag "dialog-close").
- Mini player hides a video unless videoInBars(...) (Playback.kt): cast, or background (MEDIA_FORCE_AUDIO / videoBackground) with the player not shown and no picture out; re-read every 250 ms. Sidebar transport bar unchanged.
- Artwork(crossfade = true) fades a new bitmap over the old; PhonePlayer Stage records the artwork in a GraphicsLayer while swiping and fades the picture out on a committed swipe.
- Open: the visualiser notice is not keyboard-focusable in the video player (ChromeFrame blocks descendant focus by design).
