# Baseline Profile

- APK: `Nagmedar-1.0.2-benchmark-all.apk`
- rules: 44260 (12624 of Nağmedar's and VLC's own code), startup profile: 21604
- tests: OK (3 tests) 
- screens missing an awaited element: 

Steps reached (logcat NagmedarProfile):

```
      6 NagmedarProfile: phone: music: the phone player
      6 NagmedarProfile: phone: music: the table
      6 NagmedarProfile: phone: podcasts: the episode's page
      6 NagmedarProfile: phone: podcasts: the podcast's page
      6 NagmedarProfile: phone: radio: the stations
      6 NagmedarProfile: tablet: music: the phone player
      6 NagmedarProfile: tablet: music: the table
      6 NagmedarProfile: tablet: podcasts: the episode's page
      6 NagmedarProfile: tablet: podcasts: the podcast's page
      6 NagmedarProfile: tablet: radio: the stations
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
