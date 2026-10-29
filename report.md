# Baseline Profile

- APK: `Nagmedar-1.0.0-benchmark-all.apk`
- rules: 0 (0 of Nağmedar's and VLC's own code), startup profile: 0
- tests: OK (1 test) 
- screens missing an awaited element: missing-tablet-podcast-subscribed 

Steps reached (logcat NagmedarProfile):

```
     10 NagmedarProfile: tablet: diag: add clicked
      9 NagmedarProfile: tablet: diag: attempt 1, add=true
      1 NagmedarProfile: tablet: diag: attempt 2, add=false
     10 NagmedarProfile: tablet: diag: field res=null focused=true pkg=app.nagmedar.benchmark
      1 NagmedarProfile: tablet: diag: round 0
      1 NagmedarProfile: tablet: diag: round 1
      1 NagmedarProfile: tablet: diag: round 2
      1 NagmedarProfile: tablet: diag: round 3
      1 NagmedarProfile: tablet: diag: round 4
      1 NagmedarProfile: tablet: diag: round 5
      1 NagmedarProfile: tablet: diag: round 6
      1 NagmedarProfile: tablet: diag: round 7
     10 NagmedarProfile: tablet: diag: submit button false
     10 NagmedarProfile: tablet: diag: submitted
      9 NagmedarProfile: tablet: diag: text set: 
      1 NagmedarProfile: tablet: diag: text set: http://10.0.2.2:8765/feed.xml
      1 NagmedarProfile: tablet: missing: podcast-subscribed
      9 NagmedarProfile: tablet: subscribed to http://10.0.2.2:8765/feed.xml
```

```
INSTRUMENTATION_STATUS: class=app.nagmedar.macrobenchmark.BaselineProfileGenerator
INSTRUMENTATION_STATUS: test=diagSubscribeLoop
INSTRUMENTATION_STATUS: class=app.nagmedar.macrobenchmark.BaselineProfileGenerator
INSTRUMENTATION_STATUS: test=diagSubscribeLoop
```
