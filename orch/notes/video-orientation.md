# video-orientation (PR #53, story/video-orientation)
- Cause: libVLC 4.0.0-eap29 (VLC c84ef1e) on Android 12+: MediaCodec direct rendering goes
  into an AImageReader; mediacodec.c gives the turn to MediaCodec (rotation-degrees) and marks
  the output upright/portrait-sized; android/display.c's ASurfaceControl path sets no buffer
  transform -> landscape buffer cropped to portrait size, unscaled = sideways square.
  The GL interop_aimage loses it too. VLC sources: /home/claude/vlc-pinned.
- Fix: StoredRotation (vlc-android media/) reads the MP4/MOV/3GP matrix via
  MediaMetadataRetriever before playback; NagmedarVlcOptions.mediaOptions(turned) adds
  :no-mediacodec-dr so libVLC's OpenGL output turns the picture (API >= 31, not with
  --vout=android_display,none, i.e. HDR "Ekrana geçir"; VLC 4 matches vouts strictly).
- On the x86_64 emulator, direct-rendered (ASurfaceControl) video is black in screencap;
  GL-rendered video is visible.
