# HariMultiThread Ultimate 2.2.0-noxviola.1 — MC26.3 Release Candidate

Generated: 2026-09-17

## Canonical identity

- Repository: `Herbertofury/ProjectDump`
- Release branch: `async-1.20.1-ultimate-mc263-release-20260917`
- Pinned upstream: `JustHari01/HariMultiThread@f381611c2d71a85192e2028f9e30c03823a6482b`
- Target: Minecraft 1.20.1 / Forge 47.4.23 / Java 17
- Release version: `2.2.0-noxviola.1`

## Accepted implementation state

- Minecraft 26.3 persistent-mob idle/noActionTime behavior: default ON; real Forge behavior gate passed.
- Repeated structure metadata storage-miss cache: default ON; vanilla `canCreateStructure` remains authoritative.
- Minecraft 26.3-inspired Cache2D bulk-fill experiment: included but **default OFF** after real Forge ABBA failed the >=1% median-gain promotion rule.
- Embeddium/Rubidium/Sodium own terrain rendering and the 26.3 MultiDrawIndirect lane when present.
- Oculus/Iris own shader/transparency and the 26.3 OIT/ShaderC lanes when present.
- HariChunk/C2ME disables Hari's experimental Cache2D mixin.
- Noisium remains additive rather than being replaced.
- Existing Hari 2.1.1 async/Vulkan, lifecycle, C2ME/DimThread and conservative modded-entity compatibility work remains in place.

## Verified checkpoints

### MC26.3 compile/runtime regression

- Head: `6c485f24ec60cd022177baf3b7f65bec688b0b4b`
- Actions run: `35273539972`
- Artifact: `10519689717`
- Digest: `sha256:68cebb199bd655ece9665c0ee019b0cd174c959e12c8a47a7a74d13dc49332d5`
- Result: PASS — compile/package, real Forge 47.4.23 boot, live persistent-mob proof, repeated stronghold locate, fresh chunk generation, save/clean shutdown.

Observed runtime evidence:

- persistent tagged mob `noActionTime=102`
- repeated stronghold locate X/Z: `[1376, 848]` both times

### Cache2D ABBA lab

- Actions run: `35273540019`
- Artifact: `10519780970`
- Digest: `sha256:de0bf85445d32770b0cbed3759ce0ce1e2c052181b424047b907e9c66a2f032a`
- 14 baseline + 14 candidate fresh-chunk samples
- baseline median: `3679.635061 ms`
- candidate median: `3694.9099395 ms`
- median gain: `-0.4151193868%`
- baseline mean: `3732.045654857143 ms`
- candidate mean: `3700.0724582142857 ms`
- mean gain: `0.8567204048%`
- promotion verdict: `DEFAULT_OFF`

The benchmark's numeric `Block.getId(BlockState)` hash is not accepted as cross-JVM terrain-parity proof because baseline A1/A2 themselves differed. Safety response is to keep the experiment disabled in the production/default profile.

## Exact next gate

Run `.github/workflows/async-1.20.1-ultimate-2.2.0-mc263-release.yml` on this branch and require every production gate to pass: deterministic reconstruction, compatibility audit, SPIR-V/Vulkan packaging, Forge build, packaged dedicated-server Vulkan/fallback/save/restart, packaged MC26.3 behavior gate, real Forge client/integrated-server rendered-world gate, release packaging and GitHub publication.

Do not publish or enable the Cache2D experiment by default unless newer evidence supersedes the recorded ABBA verdict.
