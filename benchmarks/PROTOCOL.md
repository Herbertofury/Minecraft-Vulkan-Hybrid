# Matched Windows hardware benchmark

Hardware inventory: Intel Core i9-13900K (24 cores / 32 logical processors), approximately 64 GiB RAM, NVIDIA RTX 4090 (24 GiB), driver 610.88. Use the installed Java 17 and Modrinth 0.21.6. Leave GameSync and AoA/Enderloom untouched. Record external GPU use and do not stop it.

Baseline: verified 2.4.9 instrumented JAR `4b0c07eabbf3265bf6592d4aeb607ac1a62f91c83a0f74a846c9532a2f8192ec`. Candidate: verified accepted 2.4.10 JAR `8b593bac1ac77670849ed992c808d327dd6d9f188ddfdea341ac88f16edf8c9f`. The capture class is byte-identical. Both use Minecraft 1.20.1, Forge 47.4.23 and the same helper/mod set; no separate VulkanMod.

Create an isolated deterministic test world, fully generate its observed scene, cleanly save, and freeze a pristine copy. Every trial restores that same copy. Match seed, camera position/yaw/pitch, game time/weather, resources/mod hashes, Java/heap, resolution, graphics, render/simulation distance, animation and pacing. Warm for at least 60 seconds after joining. Capture every actual frame for 30 seconds. Use six original JVM runs in balanced order A B B A A B, with at least three per build. Do not remove GC pauses or slow frames.

Report average FPS as 1000/mean(frame_ms), 1% low as 1000/mean(slowest ceil(1% of frames)), p50/p95/p99/max frame time, full raw samples, CPU and GPU observations, image evidence, and error/shutdown status. Reject invalid captures, settings/scene mismatches and rendering regressions. Treat concurrent GPU work as contamination; report its overlap and uncertainty. Use full OpenGL compatibility only if the original renderer gate selects it, and identify the route.

Keep throughput settings equal (VSync off / unlimited FPS in both); a separately paced everyday run must also match between builds. These explicit benchmark settings do not represent a visual quality reduction. Hardware FPS gains require these actual measurements; component benchmarks or Linux software tests cannot substitute.
