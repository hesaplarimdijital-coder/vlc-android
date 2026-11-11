# Baseline Profile

- APK: `Nagmedar-1.0.3-benchmark-all.apk`
- rules: 44195 (11343 of Nağmedar's and VLC's own code), startup profile: 22872
- tests: OK (3 tests) 
- screens missing an awaited element:  (each with its screenshot and the system's media sessions beside it; app-log.txt has the app's lines)

Steps reached (logcat NagmedarProfile):

```
      4 NagmedarProfile: phone: music: a tap at 184,1331
      1 NagmedarProfile: phone: music: a tap at 184,1444
      1 NagmedarProfile: phone: music: a tap at 184,1686
      6 NagmedarProfile: phone: music: the phone player
      6 NagmedarProfile: phone: music: the table
      6 NagmedarProfile: phone: podcasts: the episode's page
      6 NagmedarProfile: phone: podcasts: the podcast's page
      6 NagmedarProfile: phone: radio: the stations
      6 NagmedarProfile: tablet: music: a tap at 711,1006
      6 NagmedarProfile: tablet: music: the phone player
      6 NagmedarProfile: tablet: music: the table
      6 NagmedarProfile: tablet: podcasts: the episode's page
      6 NagmedarProfile: tablet: podcasts: the podcast's page
      6 NagmedarProfile: tablet: radio: the stations
      1 NagmedarProfile: tablet: subscribed to http://10.0.2.2:8765/feed.xml
```

```
INSTRUMENTATION_STATUS: class=app.nagmedar.macrobenchmark.BaselineProfileGenerator
INSTRUMENTATION_STATUS: test=startup
INSTRUMENTATION_STATUS: class=app.nagmedar.macrobenchmark.BaselineProfileGenerator
INSTRUMENTATION_STATUS: test=startup
INSTRUMENTATION_STATUS: class=app.nagmedar.macrobenchmark.BaselineProfileGenerator
INSTRUMENTATION_STATUS: test=tablet
INSTRUMENTATION_STATUS: class=app.nagmedar.macrobenchmark.BaselineProfileGenerator
INSTRUMENTATION_STATUS: test=tablet
INSTRUMENTATION_STATUS: class=app.nagmedar.macrobenchmark.BaselineProfileGenerator
INSTRUMENTATION_STATUS: test=phone
INSTRUMENTATION_STATUS: class=app.nagmedar.macrobenchmark.BaselineProfileGenerator
INSTRUMENTATION_STATUS: test=phone
```
