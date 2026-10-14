# Reproduce original sampler integration

From the repository, assemble a fresh directory: `python integration/dimensions/assemble.py <fresh-directory>`. With Java 17, build `source/gradlew.bat -p <fresh-directory> :forge:build --offline --no-daemon --max-workers=2`. Hari61 production SHA256 is 1edfd9e73622863285f2792305ab9706330dd1843a3fedacb0b65aefc4295ed6; 18 recursive archives and pack_format 15 are verified.

Executed public test sources: NativeUiGameSmoke, NativeEssentialSamplerSmoke and EssentialSamplerCallerRegression. Exact locally owned originals are sealed as ordinary GAME fixtures using ORIGINAL-CALLER-INPUTS.json and CALLER-PIN.json; original payloads are not redistributed. Native-required prevents physical GL fallback. Original constructors/configuration/copy/equality execute; the test compares 69 new actual readbacks to an independent CPU oracle and all 199 prior hashes.

On this authorized computer, `python run-sampler61-controller92-runtime.py --trial 2` from the task workspace repeats the marker-confined, inactive, logged-in Modrinth MC1.20.1/Forge47.4.26/native-required/stack1024 test. It checks pins, expects 268 cases and restores original inputs. Trial1 is accepted; trial2 is an instruction, not an additional measured run. A standalone component passing does not accept complete Essential/full327 gameplay or FPS.
