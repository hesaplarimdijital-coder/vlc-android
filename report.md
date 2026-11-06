# Emulator smoke test: FAILED

- app: `app.nagmedar.debug`, APK `debug`
- installed: versionCode=14010002 minSdk=34 targetSdk=36; versionName=1.0.2; pkgFlags=[ DEBUGGABLE HAS_CODE ALLOW_CLEAR_USER_DATA ALLOW_BACKUP LARGE_HEAP ]
- device: model=sdk_gphone64_x86_64, sdk=35, abi=x86_64, build=sdk_gphone64_x86_64-userdebug 15 AE3A.240806.043 12960925 dev-keys, size=Physical size: 2560x1600, density=Physical density: 320
- source: 3ab6f2ec13c00b4f62dc5e97bc571d6a5e86e6c0 (story/glass-b, debug build, API 35, glass-b), run 42132013799
- passes: glass-b
- 66 steps in 14 min 43 s; 1 crash/ANR finding(s), 0 driver error(s), 0 StrictMode violation(s), 1 of 53 1.0 checks failed; 0 ANR/crash of the system (the emulator's, not failures)

## 1.0 checks

| pass | step | check | result | seen |
|------|------|-------|--------|------|
| glass-b | 7 | glass B: music, cold start's first frames (dark): the glass blurs, nothing sharp beneath it | ok | bar {'lo': (18, 22, 28), 'hi': (19, 23, 29), 'mean': (18.4, 22.3, 28.0), 'spread': 1}; band {'lo': (17, 21, 27), 'hi': (20, 24, 29), 'mean': (18.3, 22.2, 28.1), 'spread': 3} |
| glass-b | 7 | glass B: music, cold start (dark): the glass blurs, nothing sharp beneath it | ok | bar {'lo': (18, 22, 28), 'hi': (19, 23, 29), 'mean': (18.4, 22.3, 28.0), 'spread': 1}; band {'lo': (17, 21, 27), 'hi': (20, 24, 29), 'mean': (18.3, 22.2, 28.1), 'spread': 3} |
| glass-b | 8 | glass B: podcasts, cold start's first frames (dark): the glass blurs, nothing sharp beneath it | ok | bar {'lo': (17, 21, 27), 'hi': (17, 21, 27), 'mean': (17.0, 21.0, 27.0), 'spread': 0}; band {'lo': (17, 21, 27), 'hi': (17, 21, 27), 'mean': (17.0, 21.0, 27.0), 'spread': 0} |
| glass-b | 8 | glass B: podcasts, cold start (dark): the glass blurs, nothing sharp beneath it | ok | bar {'lo': (17, 21, 27), 'hi': (17, 21, 27), 'mean': (17.0, 21.0, 27.0), 'spread': 0}; band {'lo': (17, 21, 27), 'hi': (17, 21, 27), 'mean': (17.0, 21.0, 27.0), 'spread': 0} |
| glass-b | 9 | glass B: radio, cold start's first frames (dark): the glass blurs, nothing sharp beneath it | ok | bar {'lo': (17, 21, 27), 'hi': (17, 21, 27), 'mean': (17.0, 21.0, 27.0), 'spread': 0}; band {'lo': (17, 21, 27), 'hi': (17, 21, 27), 'mean': (17.0, 21.0, 27.0), 'spread': 0} |
| glass-b | 9 | glass B: radio, cold start (dark): the glass blurs, nothing sharp beneath it | ok | bar {'lo': (19, 23, 29), 'hi': (20, 24, 30), 'mean': (19.6, 23.6, 29.5), 'spread': 1}; band {'lo': (20, 24, 28), 'hi': (25, 30, 33), 'mean': (21.9, 27.6, 30.5), 'spread': 6} |
| glass-b | 10 | glass B: playlists, cold start's first frames (dark): the glass blurs, nothing sharp beneath it | ok | bar {'lo': (17, 20, 24), 'hi': (32, 38, 45), 'mean': (17.1, 20.7, 25.9), 'spread': 21} |
| glass-b | 10 | glass B: playlists, cold start (dark): the glass blurs, nothing sharp beneath it | **FAILED** | bar {'lo': (23, 27, 0), 'hi': (255, 251, 254), 'mean': (86.3, 69.7, 61.7), 'spread': 254} |
| glass-b | 12 | glass B: search, cold start's first frames (dark): the glass blurs, nothing sharp beneath it | ok | bar {'lo': (17, 21, 27), 'hi': (17, 21, 27), 'mean': (17.0, 21.0, 27.0), 'spread': 0}; band {'lo': (17, 21, 27), 'hi': (17, 21, 27), 'mean': (17.0, 21.0, 27.0), 'spread': 0} |
| glass-b | 12 | glass B: search, cold start (dark): the glass blurs, nothing sharp beneath it | ok | bar {'lo': (18, 22, 28), 'hi': (20, 24, 30), 'mean': (18.8, 22.8, 28.6), 'spread': 2}; band {'lo': (19, 22, 27), 'hi': (33, 31, 31), 'mean': (27.2, 27.1, 28.8), 'spread': 14} |
| glass-b | 15 | glass B: more, cold start's first frames (dark): the glass blurs, nothing sharp beneath it | ok | bar {'lo': (17, 21, 27), 'hi': (17, 21, 27), 'mean': (17.0, 21.0, 27.0), 'spread': 0}; band {'lo': (17, 21, 27), 'hi': (17, 21, 27), 'mean': (17.0, 21.0, 27.0), 'spread': 0} |
| glass-b | 15 | glass B: more, cold start (dark): the glass blurs, nothing sharp beneath it | ok | bar {'lo': (17, 21, 27), 'hi': (17, 21, 27), 'mean': (17.0, 21.0, 27.0), 'spread': 0}; band {'lo': (17, 21, 27), 'hi': (17, 21, 27), 'mean': (17.0, 21.0, 27.0), 'spread': 0} |
| glass-b | 16 | a track in the Music's list | ok |  |
| glass-b | 17 | the mini player shows the track | ok |  |
| glass-b | 18 | glass B: music with the mini player (dark): the glass blurs, nothing sharp beneath it | ok | card {'lo': (17, 21, 27), 'hi': (18, 22, 28), 'mean': (17.9, 21.8, 27.3), 'spread': 1}; bar {'lo': (18, 22, 28), 'hi': (19, 23, 29), 'mean': (18.3, 22.1, 28.0), 'spread': 1}; band {'lo': (17, 21, 27), 'hi': (19, 23, 29), 'mean': (18.3, 22.2, 28.0), 'spread': 2} |
| glass-b | 19 | glass B: music, its list under the bars (dark): the glass blurs, nothing sharp beneath it | ok | card {'lo': (17, 21, 27), 'hi': (18, 22, 28), 'mean': (18.0, 21.8, 27.4), 'spread': 1}; bar {'lo': (18, 22, 28), 'hi': (19, 23, 29), 'mean': (18.5, 22.3, 28.0), 'spread': 1}; band {'lo': (18, 22, 28), 'hi': (19, 23, 29), 'mean': (18.6, 22.5, 28.4), 'spread': 1} |
| glass-b | 20 | glass B: podcasts with the mini player (dark): the glass blurs, nothing sharp beneath it | ok | card {'lo': (17, 21, 27), 'hi': (17, 21, 27), 'mean': (17.0, 21.0, 27.0), 'spread': 0}; bar {'lo': (17, 21, 27), 'hi': (17, 21, 27), 'mean': (17.0, 21.0, 27.0), 'spread': 0}; band {'lo': (17, 21, 27), 'hi': (17, 21, 27), 'mean': (17.0, 21.0, 27.0), 'spread': 0} |
| glass-b | 21 | glass B: podcasts, its list under the bars (dark): the glass blurs, nothing sharp beneath it | ok | card {'lo': (17, 21, 27), 'hi': (17, 21, 27), 'mean': (17.0, 21.0, 27.0), 'spread': 0}; bar {'lo': (17, 21, 27), 'hi': (17, 21, 27), 'mean': (17.0, 21.0, 27.0), 'spread': 0}; band {'lo': (17, 21, 27), 'hi': (17, 21, 27), 'mean': (17.0, 21.0, 27.0), 'spread': 0} |
| glass-b | 22 | glass B: radio with the mini player (dark): the glass blurs, nothing sharp beneath it | ok | card {'lo': (19, 24, 28), 'hi': (23, 28, 35), 'mean': (20.6, 25.4, 31.4), 'spread': 7}; bar {'lo': (19, 23, 29), 'hi': (20, 24, 30), 'mean': (19.6, 23.6, 29.4), 'spread': 1}; band {'lo': (20, 24, 28), 'hi': (25, 30, 33), 'mean': (21.9, 27.8, 30.6), 'spread': 6} |
| glass-b | 23 | glass B: radio, its list under the bars (dark): the glass blurs, nothing sharp beneath it | ok | card {'lo': (18, 21, 27), 'hi': (26, 22, 28), 'mean': (22.1, 21.5, 27.4), 'spread': 8}; bar {'lo': (19, 23, 29), 'hi': (20, 24, 30), 'mean': (19.6, 23.5, 29.2), 'spread': 1}; band {'lo': (20, 23, 29), 'hi': (25, 28, 34), 'mean': (23.3, 26.2, 32.3), 'spread': 5} |
| glass-b | 24 | glass B: playlists with the mini player (dark): the glass blurs, nothing sharp beneath it | ok | card {'lo': (18, 21, 27), 'hi': (21, 23, 28), 'mean': (19.5, 22.3, 27.2), 'spread': 3}; bar {'lo': (32, 30, 32), 'hi': (43, 44, 47), 'mean': (39.0, 38.9, 42.2), 'spread': 15}; band {'lo': (22, 24, 28), 'hi': (30, 30, 36), 'mean': (26.3, 26.8, 30.5), 'spread': 8} |
| glass-b | 25 | glass B: playlists, its list under the bars (dark): the glass blurs, nothing sharp beneath it | ok | card {'lo': (18, 22, 28), 'hi': (21, 24, 31), 'mean': (20.0, 23.2, 29.3), 'spread': 3}; bar {'lo': (17, 21, 27), 'hi': (20, 24, 30), 'mean': (17.8, 21.8, 27.7), 'spread': 3}; band {'lo': (17, 21, 27), 'hi': (17, 21, 27), 'mean': (17.0, 21.0, 27.0), 'spread': 0} |
| glass-b | 27 | glass B: search with the mini player (dark): the glass blurs, nothing sharp beneath it | ok | card {'lo': (17, 22, 28), 'hi': (18, 25, 31), 'mean': (17.8, 23.5, 29.4), 'spread': 3}; bar {'lo': (18, 22, 28), 'hi': (20, 24, 30), 'mean': (18.7, 22.7, 28.5), 'spread': 2}; band {'lo': (19, 22, 27), 'hi': (33, 31, 31), 'mean': (27.2, 27.0, 28.8), 'spread': 14} |
| glass-b | 28 | glass B: search, its list under the bars (dark): the glass blurs, nothing sharp beneath it | ok | card {'lo': (19, 22, 27), 'hi': (23, 24, 28), 'mean': (20.8, 23.0, 27.1), 'spread': 4}; bar {'lo': (18, 22, 28), 'hi': (20, 24, 30), 'mean': (19.1, 22.9, 28.7), 'spread': 2}; band {'lo': (23, 25, 28), 'hi': (33, 31, 31), 'mean': (29.2, 28.3, 29.3), 'spread': 10} |
| glass-b | 32 | glass B: more with the mini player (dark): the glass blurs, nothing sharp beneath it | ok | card {'lo': (17, 21, 27), 'hi': (18, 22, 28), 'mean': (17.3, 21.2, 27.1), 'spread': 1}; bar {'lo': (17, 21, 27), 'hi': (17, 21, 27), 'mean': (17.0, 21.0, 27.0), 'spread': 0}; band {'lo': (17, 21, 27), 'hi': (17, 21, 27), 'mean': (17.0, 21.0, 27.0), 'spread': 0} |
| glass-b | 33 | glass B: more, its list under the bars (dark): the glass blurs, nothing sharp beneath it | ok | card {'lo': (17, 21, 27), 'hi': (18, 22, 28), 'mean': (17.3, 21.2, 27.1), 'spread': 1}; bar {'lo': (17, 21, 27), 'hi': (17, 21, 27), 'mean': (17.0, 21.0, 27.0), 'spread': 0}; band {'lo': (17, 21, 27), 'hi': (17, 21, 27), 'mean': (17.0, 21.0, 27.0), 'spread': 0} |
| glass-b | 37 | glass B: music, cold start's first frames (light): the glass blurs, nothing sharp beneath it | ok | bar {'lo': (247, 248, 249), 'hi': (247, 248, 249), 'mean': (247.0, 248.0, 249.0), 'spread': 0}; band {'lo': (246, 247, 248), 'hi': (248, 249, 250), 'mean': (247.0, 248.0, 249.1), 'spread': 2} |
| glass-b | 37 | glass B: music, cold start (light): the glass blurs, nothing sharp beneath it | ok | bar {'lo': (247, 248, 249), 'hi': (247, 248, 249), 'mean': (247.0, 248.0, 249.0), 'spread': 0}; band {'lo': (246, 247, 248), 'hi': (248, 249, 250), 'mean': (247.0, 248.0, 249.1), 'spread': 2} |
| glass-b | 38 | glass B: podcasts, cold start's first frames (light): the glass blurs, nothing sharp beneath it | ok | bar {'lo': (248, 249, 250), 'hi': (248, 249, 250), 'mean': (248.0, 249.0, 250.0), 'spread': 0}; band {'lo': (248, 249, 250), 'hi': (248, 249, 250), 'mean': (248.0, 249.0, 250.0), 'spread': 0} |
| glass-b | 38 | glass B: podcasts, cold start (light): the glass blurs, nothing sharp beneath it | ok | bar {'lo': (248, 249, 250), 'hi': (248, 249, 250), 'mean': (248.0, 249.0, 250.0), 'spread': 0}; band {'lo': (248, 249, 250), 'hi': (248, 249, 250), 'mean': (248.0, 249.0, 250.0), 'spread': 0} |
| glass-b | 39 | glass B: radio, cold start's first frames (light): the glass blurs, nothing sharp beneath it | ok | bar {'lo': (248, 249, 250), 'hi': (248, 249, 250), 'mean': (248.0, 249.0, 250.0), 'spread': 0}; band {'lo': (248, 249, 250), 'hi': (248, 249, 250), 'mean': (248.0, 249.0, 250.0), 'spread': 0} |
| glass-b | 39 | glass B: radio, cold start (light): the glass blurs, nothing sharp beneath it | ok | bar {'lo': (246, 247, 248), 'hi': (247, 248, 249), 'mean': (246.1, 247.2, 248.3), 'spread': 1}; band {'lo': (236, 238, 237), 'hi': (245, 246, 246), 'mean': (239.4, 242.0, 240.9), 'spread': 9} |
| glass-b | 42 | glass B: search, cold start's first frames (light): the glass blurs, nothing sharp beneath it | ok | bar {'lo': (248, 249, 250), 'hi': (248, 249, 250), 'mean': (248.0, 249.0, 250.0), 'spread': 0}; band {'lo': (248, 249, 250), 'hi': (248, 249, 250), 'mean': (248.0, 249.0, 250.0), 'spread': 0} |
| glass-b | 42 | glass B: search, cold start (light): the glass blurs, nothing sharp beneath it | ok | bar {'lo': (246, 247, 248), 'hi': (247, 248, 249), 'mean': (246.6, 247.6, 248.7), 'spread': 1}; band {'lo': (247, 244, 240), 'hi': (248, 248, 248), 'mean': (247.8, 245.6, 243.4), 'spread': 8} |
| glass-b | 45 | glass B: more, cold start's first frames (light): the glass blurs, nothing sharp beneath it | ok | bar {'lo': (248, 249, 250), 'hi': (248, 249, 250), 'mean': (248.0, 249.0, 250.0), 'spread': 0}; band {'lo': (248, 249, 250), 'hi': (248, 249, 250), 'mean': (248.0, 249.0, 250.0), 'spread': 0} |
| glass-b | 45 | glass B: more, cold start (light): the glass blurs, nothing sharp beneath it | ok | bar {'lo': (248, 249, 250), 'hi': (248, 249, 250), 'mean': (248.0, 249.0, 250.0), 'spread': 0}; band {'lo': (248, 249, 250), 'hi': (248, 249, 250), 'mean': (248.0, 249.0, 250.0), 'spread': 0} |
| glass-b | 46 | a track in the Music's list | ok |  |
| glass-b | 47 | the mini player shows the track | ok |  |
| glass-b | 48 | glass B: music with the mini player (light): the glass blurs, nothing sharp beneath it | ok | card {'lo': (247, 248, 249), 'hi': (248, 249, 250), 'mean': (247.7, 248.8, 249.8), 'spread': 1}; bar {'lo': (247, 248, 249), 'hi': (247, 248, 249), 'mean': (247.0, 248.0, 249.0), 'spread': 0}; band {'lo': (246, 247, 248), 'hi': (248, 249, 250), 'mean': (247.2, 248.2, 249.3), 'spread': 2} |
| glass-b | 49 | glass B: music, its list under the bars (light): the glass blurs, nothing sharp beneath it | ok | card {'lo': (247, 248, 249), 'hi': (248, 249, 250), 'mean': (247.7, 248.7, 249.7), 'spread': 1}; bar {'lo': (247, 248, 249), 'hi': (247, 248, 249), 'mean': (247.0, 248.0, 249.0), 'spread': 0}; band {'lo': (246, 247, 248), 'hi': (247, 248, 249), 'mean': (246.9, 247.9, 248.9), 'spread': 1} |
| glass-b | 50 | glass B: podcasts with the mini player (light): the glass blurs, nothing sharp beneath it | ok | card {'lo': (248, 249, 250), 'hi': (248, 249, 250), 'mean': (248.0, 249.0, 250.0), 'spread': 0}; bar {'lo': (248, 249, 250), 'hi': (248, 249, 250), 'mean': (248.0, 249.0, 250.0), 'spread': 0}; band {'lo': (248, 249, 250), 'hi': (248, 249, 250), 'mean': (248.0, 249.0, 250.0), 'spread': 0} |
| glass-b | 51 | glass B: podcasts, its list under the bars (light): the glass blurs, nothing sharp beneath it | ok | card {'lo': (248, 249, 250), 'hi': (248, 249, 250), 'mean': (248.0, 249.0, 250.0), 'spread': 0}; bar {'lo': (248, 249, 250), 'hi': (248, 249, 250), 'mean': (248.0, 249.0, 250.0), 'spread': 0}; band {'lo': (248, 249, 250), 'hi': (248, 249, 250), 'mean': (248.0, 249.0, 250.0), 'spread': 0} |
| glass-b | 52 | glass B: radio with the mini player (light): the glass blurs, nothing sharp beneath it | ok | card {'lo': (243, 246, 246), 'hi': (247, 248, 250), 'mean': (245.7, 247.6, 248.7), 'spread': 4}; bar {'lo': (246, 247, 248), 'hi': (247, 248, 249), 'mean': (246.2, 247.2, 248.3), 'spread': 1}; band {'lo': (236, 238, 237), 'hi': (245, 246, 246), 'mean': (239.4, 242.1, 240.9), 'spread': 9} |
| glass-b | 53 | glass B: radio, its list under the bars (light): the glass blurs, nothing sharp beneath it | ok | card {'lo': (247, 242, 243), 'hi': (248, 248, 249), 'mean': (247.8, 245.2, 246.3), 'spread': 6}; bar {'lo': (246, 247, 248), 'hi': (246, 248, 249), 'mean': (246.0, 247.0, 248.2), 'spread': 1}; band {'lo': (240, 240, 241), 'hi': (245, 245, 247), 'mean': (241.7, 242.1, 243.4), 'spread': 6} |
| glass-b | 54 | glass B: playlists with the mini player (light): the glass blurs, nothing sharp beneath it | ok | card {'lo': (245, 244, 245), 'hi': (248, 248, 249), 'mean': (246.3, 246.4, 246.8), 'spread': 4}; bar {'lo': (238, 234, 233), 'hi': (246, 245, 244), 'mean': (243.1, 241.3, 240.4), 'spread': 11}; band {'lo': (237, 233, 231), 'hi': (244, 245, 245), 'mean': (240.6, 238.9, 238.4), 'spread': 14} |
| glass-b | 55 | glass B: playlists, its list under the bars (light): the glass blurs, nothing sharp beneath it | ok | card {'lo': (245, 245, 247), 'hi': (247, 248, 249), 'mean': (246.3, 246.8, 248.1), 'spread': 3}; bar {'lo': (247, 248, 249), 'hi': (248, 249, 250), 'mean': (247.8, 248.8, 249.8), 'spread': 1}; band {'lo': (248, 249, 250), 'hi': (248, 249, 250), 'mean': (248.0, 249.0, 250.0), 'spread': 0} |
| glass-b | 57 | glass B: search with the mini player (light): the glass blurs, nothing sharp beneath it | ok | card {'lo': (244, 247, 249), 'hi': (247, 249, 250), 'mean': (245.4, 248.0, 249.1), 'spread': 3}; bar {'lo': (246, 247, 248), 'hi': (247, 248, 249), 'mean': (246.7, 247.7, 248.8), 'spread': 1}; band {'lo': (247, 244, 240), 'hi': (248, 248, 248), 'mean': (247.8, 245.5, 243.4), 'spread': 8} |
| glass-b | 58 | glass B: search, its list under the bars (light): the glass blurs, nothing sharp beneath it | ok | card {'lo': (248, 247, 246), 'hi': (248, 248, 249), 'mean': (248.0, 247.8, 247.5), 'spread': 3}; bar {'lo': (246, 247, 248), 'hi': (247, 248, 249), 'mean': (246.5, 247.5, 248.5), 'spread': 1}; band {'lo': (247, 243, 240), 'hi': (248, 247, 246), 'mean': (247.7, 244.8, 242.0), 'spread': 6} |
| glass-b | 62 | glass B: more with the mini player (light): the glass blurs, nothing sharp beneath it | ok | card {'lo': (247, 248, 249), 'hi': (248, 249, 250), 'mean': (248.0, 249.0, 250.0), 'spread': 1}; bar {'lo': (248, 249, 250), 'hi': (248, 249, 250), 'mean': (248.0, 249.0, 250.0), 'spread': 0}; band {'lo': (248, 249, 250), 'hi': (248, 249, 250), 'mean': (248.0, 249.0, 250.0), 'spread': 0} |
| glass-b | 63 | glass B: more, its list under the bars (light): the glass blurs, nothing sharp beneath it | ok | card {'lo': (247, 248, 249), 'hi': (248, 249, 250), 'mean': (248.0, 249.0, 250.0), 'spread': 1}; bar {'lo': (248, 249, 250), 'hi': (248, 249, 250), 'mean': (248.0, 249.0, 250.0), 'spread': 0}; band {'lo': (248, 249, 250), 'hi': (248, 249, 250), 'mean': (248.0, 249.0, 250.0), 'spread': 0} |
| glass-b | 66 | the glass B screenshots were taken | ok | 62 screenshots |
| all | 66 | no film, series, video, Home, TV Lab or Whisper words on the app's screens | ok |  |
| all | 66 | no StrictMode violation of the app (debug build) | ok | 0 in the log, 0 in the dropbox |

## Frames (dumpsys gfxinfo; informational on an emulator, over 40 % janky is flagged)

| pass | step | gesture | frames | janky | 50th | 90th | 95th | 99th |
|------|------|---------|--------|-------|------|------|------|------|

## Crashes and ANRs

### 1. FATAL EXCEPTION (pass glass-b, step 40)

```
FATAL EXCEPTION: main
Process: app.nagmedar.debug, PID: 10354
android.app.RemoteServiceException$ForegroundServiceDidNotStartInTimeException: Context.startForegroundService() did not then call Service.startForeground(): ServiceRecord{aceeea5 u0 app.nagmedar.debug/org.videolan.vlc.MediaParsingService c:app.nagmedar.debug}
	at android.app.ActivityThread.generateForegroundServiceDidNotStartInTimeException(ActivityThread.java:2271)
	at android.app.ActivityThread.throwRemoteServiceException(ActivityThread.java:2239)
	at android.app.ActivityThread.-$$Nest$mthrowRemoteServiceException(Unknown Source:0)
	at android.app.ActivityThread$H.handleMessage(ActivityThread.java:2557)
	at android.os.Handler.dispatchMessage(Handler.java:107)
	at android.os.Looper.loopOnce(Looper.java:232)
	at android.os.Looper.loop(Looper.java:317)
	at android.app.ActivityThread.main(ActivityThread.java:8705)
	at java.lang.reflect.Method.invoke(Native Method)
	at com.android.internal.os.RuntimeInit$MethodAndArgsCaller.run(RuntimeInit.java:580)
	at com.android.internal.os.ZygoteInit.main(ZygoteInit.java:886)
```

## The system's ANRs and crashes (the emulator's own: recorded, the step tried once more)

None.

## Steps

| # | pass | step | result | screenshot | note |
|---|------|------|--------|------------|------|
| 1 | glass-b | Launch (glass B) | ok | [001-glass-b-launch-glass-b.png](shots/001-glass-b-launch-glass-b.png) |  |
| 2 | glass-b | First run (glass B) step 1 (Continue) | ok | [002-glass-b-first-run-glass-b-step-1-continue.png](shots/002-glass-b-first-run-glass-b-step-1-continue.png) |  |
| 3 | glass-b | First run (glass B) step 2 (Continue · 2 sources) | ok | [003-glass-b-first-run-glass-b-step-2-continue-2-sources.png](shots/003-glass-b-first-run-glass-b-step-2-continue-2-sources.png) |  |
| 4 | glass-b | First run (glass B) step 3 (Continue) | ok | [004-glass-b-first-run-glass-b-step-3-continue.png](shots/004-glass-b-first-run-glass-b-step-3-continue.png) |  |
| 5 | glass-b | First run (glass B) step 4 (Start) | ok | [005-glass-b-first-run-glass-b-step-4-start.png](shots/005-glass-b-first-run-glass-b-step-4-start.png) |  |
| 6 | glass-b | After the scan (glass B) | ok | [006-glass-b-after-the-scan-glass-b.png](shots/006-glass-b-after-the-scan-glass-b.png) |  |
| 7 | glass-b | Glass B cold start: music (dark) | ok | [007-glass-b-glass-b-cold-start-music-dark.png](shots/007-glass-b-glass-b-cold-start-music-dark.png) |  |
| 8 | glass-b | Glass B cold start: podcasts (dark) | ok | [008-glass-b-glass-b-cold-start-podcasts-dark.png](shots/008-glass-b-glass-b-cold-start-podcasts-dark.png) |  |
| 9 | glass-b | Glass B cold start: radio (dark) | ok | [009-glass-b-glass-b-cold-start-radio-dark.png](shots/009-glass-b-glass-b-cold-start-radio-dark.png) |  |
| 10 | glass-b | Glass B cold start: playlists (dark) | ok | [010-glass-b-glass-b-cold-start-playlists-dark.png](shots/010-glass-b-glass-b-cold-start-playlists-dark.png) | check failed: glass B: playlists, cold start (dark): the glass blurs, nothing sharp beneath it |
| 11 | glass-b | Glass B cold start: search (dark) | ok | [011-glass-b-glass-b-cold-start-search-dark.png](shots/011-glass-b-glass-b-cold-start-search-dark.png) |  |
| 12 | glass-b | Glass B cold start: search words (dark) | ok | [012-glass-b-glass-b-cold-start-search-words-dark.png](shots/012-glass-b-glass-b-cold-start-search-words-dark.png) |  |
| 13 | glass-b | Glass B cold start: settings (dark) | ok | [013-glass-b-glass-b-cold-start-settings-dark.png](shots/013-glass-b-glass-b-cold-start-settings-dark.png) |  |
| 14 | glass-b | Glass B cold start: about (dark) | ok | [014-glass-b-glass-b-cold-start-about-dark.png](shots/014-glass-b-glass-b-cold-start-about-dark.png) |  |
| 15 | glass-b | Glass B cold start: more (dark) | ok | [015-glass-b-glass-b-cold-start-more-dark.png](shots/015-glass-b-glass-b-cold-start-more-dark.png) |  |
| 16 | glass-b | Music | ok | [016-glass-b-music.png](shots/016-glass-b-music.png) |  |
| 17 | glass-b | Play Profile track 1.1 | ok | [017-glass-b-play-profile-track-1-1.png](shots/017-glass-b-play-profile-track-1-1.png) |  |
| 18 | glass-b | Music | ok | [018-glass-b-music.png](shots/018-glass-b-music.png) |  |
| 19 | glass-b | Glass B: music, its list under the bars (dark) | ok | [019-glass-b-glass-b-music-its-list-under-the-bars-dark.png](shots/019-glass-b-glass-b-music-its-list-under-the-bars-dark.png) |  |
| 20 | glass-b | Glass B: podcasts (dark) | ok | [020-glass-b-glass-b-podcasts-dark.png](shots/020-glass-b-glass-b-podcasts-dark.png) |  |
| 21 | glass-b | Glass B: podcasts, its list under the bars (dark) | ok | [021-glass-b-glass-b-podcasts-its-list-under-the-bars-dark.png](shots/021-glass-b-glass-b-podcasts-its-list-under-the-bars-dark.png) |  |
| 22 | glass-b | Glass B: radio (dark) | ok | [022-glass-b-glass-b-radio-dark.png](shots/022-glass-b-glass-b-radio-dark.png) |  |
| 23 | glass-b | Glass B: radio, its list under the bars (dark) | ok | [023-glass-b-glass-b-radio-its-list-under-the-bars-dark.png](shots/023-glass-b-glass-b-radio-its-list-under-the-bars-dark.png) |  |
| 24 | glass-b | Glass B: playlists (dark) | ok | [024-glass-b-glass-b-playlists-dark.png](shots/024-glass-b-glass-b-playlists-dark.png) |  |
| 25 | glass-b | Glass B: playlists, its list under the bars (dark) | ok | [025-glass-b-glass-b-playlists-its-list-under-the-bars-dark.png](shots/025-glass-b-glass-b-playlists-its-list-under-the-bars-dark.png) |  |
| 26 | glass-b | Music | ok | [026-glass-b-music.png](shots/026-glass-b-music.png) |  |
| 27 | glass-b | Search: track | ok | [027-glass-b-search-track.png](shots/027-glass-b-search-track.png) |  |
| 28 | glass-b | Glass B: search, its list under the bars (dark) | ok | [028-glass-b-glass-b-search-its-list-under-the-bars-dark.png](shots/028-glass-b-glass-b-search-its-list-under-the-bars-dark.png) |  |
| 29 | glass-b | Back from the search page | ok | [029-glass-b-back-from-the-search-page.png](shots/029-glass-b-back-from-the-search-page.png) |  |
| 30 | glass-b | Glass B: settings (dark) | ok | [030-glass-b-glass-b-settings-dark.png](shots/030-glass-b-glass-b-settings-dark.png) |  |
| 31 | glass-b | Glass B: settings, its list under the bars (dark) | ok | [031-glass-b-glass-b-settings-its-list-under-the-bars-dark.png](shots/031-glass-b-glass-b-settings-its-list-under-the-bars-dark.png) |  |
| 32 | glass-b | Glass B: more (dark) | ok | [032-glass-b-glass-b-more-dark.png](shots/032-glass-b-glass-b-more-dark.png) |  |
| 33 | glass-b | Glass B: more, its list under the bars (dark) | ok | [033-glass-b-glass-b-more-its-list-under-the-bars-dark.png](shots/033-glass-b-glass-b-more-its-list-under-the-bars-dark.png) |  |
| 34 | glass-b | Music | ok | [034-glass-b-music.png](shots/034-glass-b-music.png) |  |
| 35 | glass-b | Glass B: the full player (dark) | ok | [035-glass-b-glass-b-the-full-player-dark.png](shots/035-glass-b-glass-b-the-full-player-dark.png) |  |
| 36 | glass-b | Back from the full player | ok | [036-glass-b-back-from-the-full-player.png](shots/036-glass-b-back-from-the-full-player.png) |  |
| 37 | glass-b | Glass B cold start: music (light) | ok | [037-glass-b-glass-b-cold-start-music-light.png](shots/037-glass-b-glass-b-cold-start-music-light.png) |  |
| 38 | glass-b | Glass B cold start: podcasts (light) | ok | [038-glass-b-glass-b-cold-start-podcasts-light.png](shots/038-glass-b-glass-b-cold-start-podcasts-light.png) |  |
| 39 | glass-b | Glass B cold start: radio (light) | ok | [039-glass-b-glass-b-cold-start-radio-light.png](shots/039-glass-b-glass-b-cold-start-radio-light.png) |  |
| 40 | glass-b | Glass B cold start: playlists (light) | FAIL | [040-glass-b-glass-b-cold-start-playlists-light.png](shots/040-glass-b-glass-b-cold-start-playlists-light.png) | app relaunched after the failure |
| 41 | glass-b | Glass B cold start: search (light) | ok | [041-glass-b-glass-b-cold-start-search-light.png](shots/041-glass-b-glass-b-cold-start-search-light.png) |  |
| 42 | glass-b | Glass B cold start: search words (light) | ok | [042-glass-b-glass-b-cold-start-search-words-light.png](shots/042-glass-b-glass-b-cold-start-search-words-light.png) |  |
| 43 | glass-b | Glass B cold start: settings (light) | ok | [043-glass-b-glass-b-cold-start-settings-light.png](shots/043-glass-b-glass-b-cold-start-settings-light.png) |  |
| 44 | glass-b | Glass B cold start: about (light) | ok | [044-glass-b-glass-b-cold-start-about-light.png](shots/044-glass-b-glass-b-cold-start-about-light.png) |  |
| 45 | glass-b | Glass B cold start: more (light) | ok | [045-glass-b-glass-b-cold-start-more-light.png](shots/045-glass-b-glass-b-cold-start-more-light.png) |  |
| 46 | glass-b | Music | ok | [046-glass-b-music.png](shots/046-glass-b-music.png) |  |
| 47 | glass-b | Play Profile track 1.1 | ok | [047-glass-b-play-profile-track-1-1.png](shots/047-glass-b-play-profile-track-1-1.png) |  |
| 48 | glass-b | Music | ok | [048-glass-b-music.png](shots/048-glass-b-music.png) |  |
| 49 | glass-b | Glass B: music, its list under the bars (light) | ok | [049-glass-b-glass-b-music-its-list-under-the-bars-light.png](shots/049-glass-b-glass-b-music-its-list-under-the-bars-light.png) |  |
| 50 | glass-b | Glass B: podcasts (light) | ok | [050-glass-b-glass-b-podcasts-light.png](shots/050-glass-b-glass-b-podcasts-light.png) |  |
| 51 | glass-b | Glass B: podcasts, its list under the bars (light) | ok | [051-glass-b-glass-b-podcasts-its-list-under-the-bars-light.png](shots/051-glass-b-glass-b-podcasts-its-list-under-the-bars-light.png) |  |
| 52 | glass-b | Glass B: radio (light) | ok | [052-glass-b-glass-b-radio-light.png](shots/052-glass-b-glass-b-radio-light.png) |  |
| 53 | glass-b | Glass B: radio, its list under the bars (light) | ok | [053-glass-b-glass-b-radio-its-list-under-the-bars-light.png](shots/053-glass-b-glass-b-radio-its-list-under-the-bars-light.png) |  |
| 54 | glass-b | Glass B: playlists (light) | ok | [054-glass-b-glass-b-playlists-light.png](shots/054-glass-b-glass-b-playlists-light.png) |  |
| 55 | glass-b | Glass B: playlists, its list under the bars (light) | ok | [055-glass-b-glass-b-playlists-its-list-under-the-bars-light.png](shots/055-glass-b-glass-b-playlists-its-list-under-the-bars-light.png) |  |
| 56 | glass-b | Music | ok | [056-glass-b-music.png](shots/056-glass-b-music.png) |  |
| 57 | glass-b | Search: track | ok | [057-glass-b-search-track.png](shots/057-glass-b-search-track.png) |  |
| 58 | glass-b | Glass B: search, its list under the bars (light) | ok | [058-glass-b-glass-b-search-its-list-under-the-bars-light.png](shots/058-glass-b-glass-b-search-its-list-under-the-bars-light.png) |  |
| 59 | glass-b | Back from the search page | ok | [059-glass-b-back-from-the-search-page.png](shots/059-glass-b-back-from-the-search-page.png) |  |
| 60 | glass-b | Glass B: settings (light) | ok | [060-glass-b-glass-b-settings-light.png](shots/060-glass-b-glass-b-settings-light.png) |  |
| 61 | glass-b | Glass B: settings, its list under the bars (light) | ok | [061-glass-b-glass-b-settings-its-list-under-the-bars-light.png](shots/061-glass-b-glass-b-settings-its-list-under-the-bars-light.png) |  |
| 62 | glass-b | Glass B: more (light) | ok | [062-glass-b-glass-b-more-light.png](shots/062-glass-b-glass-b-more-light.png) |  |
| 63 | glass-b | Glass B: more, its list under the bars (light) | ok | [063-glass-b-glass-b-more-its-list-under-the-bars-light.png](shots/063-glass-b-glass-b-more-its-list-under-the-bars-light.png) |  |
| 64 | glass-b | Music | ok | [064-glass-b-music.png](shots/064-glass-b-music.png) |  |
| 65 | glass-b | Glass B: the full player (light) | ok | [065-glass-b-glass-b-the-full-player-light.png](shots/065-glass-b-glass-b-the-full-player-light.png) |  |
| 66 | glass-b | Back from the full player | ok | [066-glass-b-back-from-the-full-player.png](shots/066-glass-b-back-from-the-full-player.png) |  |

## Warnings

None.

### Missing native libraries (arm64-only libraries on an x86_64 emulator)

No link errors were logged.

### StrictMode violations: 0 (the app's; by the system's policy for a debuggable app)


### Error-level log lines of the app (top 25)

- 208x `VLC-std: s_glBindAttribLocation: bind attrib 0 name position`
- 118x `VLC-std: s_glBindAttribLocation: bind attrib 1 name color`
- 83x `VLC-std: s_glBindAttribLocation: bind attrib 2 name localCoord`
- 79x `VLC-std: s_glBindAttribLocation: bind attrib 0 name inPosition`
- 79x `VLC-std: s_glBindAttribLocation: bind attrib 1 name inColor`
- 67x `VLC-std: s_glBindAttribLocation: bind attrib 1 name localCoord`
- 61x `VLC-std: s_glBindAttribLocation: bind attrib 2 name inCircleEdge`
- 23x `VLC-std: s_glBindAttribLocation: bind attrib 1 name coverage`
- 16x `VLC/LibVLC/Util: WARNING: Can't find shared library`
- 16x `VLC-std: s_glBindAttribLocation: bind attrib 2 name inTextureCoords`
- 16x `VLC-std: s_glBindAttribLocation: bind attrib 2 name color`
- 16x `VLC-std: s_glBindAttribLocation: bind attrib 3 name localCoord`
- 16x `VLC-std: s_glBindAttribLocation: bind attrib 0 name a_vertex`
- 8x `VLC-std: s_glBindAttribLocation: bind attrib 3 name texSubset`
- 2x `FrameTracker: force finish cuj, time out: J<IME_INSETS_SHOW_ANIMATION::0@1@app.nagmedar.debug>`
- 2x `ActivityThread: Failed to find provider info for androidx.car.app.connection`
- 2x `VLC-std: s_glBindAttribLocation: bind attrib 0 name radii_selector`
- 2x `VLC-std: s_glBindAttribLocation: bind attrib 1 name corner_and_radius_outsets`
- 2x `VLC-std: s_glBindAttribLocation: bind attrib 2 name aa_bloat_and_coverage`
- 2x `VLC-std: s_glBindAttribLocation: bind attrib 3 name radii_x`
- 2x `VLC-std: s_glBindAttribLocation: bind attrib 4 name radii_y`
- 2x `VLC-std: s_glBindAttribLocation: bind attrib 5 name skew`
- 2x `VLC-std: s_glBindAttribLocation: bind attrib 6 name translate_and_localrotate`
- 2x `VLC-std: s_glBindAttribLocation: bind attrib 7 name color`
- 2x `FrameTracker: force finish cuj, time out: J<IME_INSETS_HIDE_ANIMATION::1@1@app.nagmedar.debug>`
