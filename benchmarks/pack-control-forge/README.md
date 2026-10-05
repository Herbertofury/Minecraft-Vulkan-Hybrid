# Disposable pack control

Source-built Forge 1.20.1 / Java 17 recorder and native grass play test, GPL-3.0-only. Build with `source/gradlew -p benchmarks/pack-control-forge build`. Resource metadata is format 15. This controller moves the camera, records, saves and exits automatically; use only marker-owned disposable test worlds, never the personal Noxviola profile.

Default mode retains all RenderTick END CPU intervals after 60 seconds of warmup and captures 30 seconds. These are not GPU timestamps or presentation timings. `-Dmvh.pack.grass.smoke=true` selects an ownership-checked bounded native play test instead: resource reload, original mesh/shader state, animation phase, real movement/trail state, stationary FOV widening/restoration, Nether and return. Smoke tests accept no FPS. Screenshots require visual review; all-style and shader-provider parity remain separate requirements. The caller archives worlds and restores the checksum-verified pristine copy before every run.
