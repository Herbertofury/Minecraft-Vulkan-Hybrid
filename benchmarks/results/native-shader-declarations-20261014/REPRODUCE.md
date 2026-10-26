# Reproduce the shader declaration repair

Fresh verified assembly: `python integration/dimensions/assemble.py <fresh-directory>`. Java17 Forge build: `source/gradlew.bat -p <fresh-directory> :forge:build --offline --no-daemon --max-workers=2`. Hari62 SHA714106dca1ed653c1cf360863a778ff5850d12960e52e3c722062b69cf8186b3.

Run `python integration/dimensions/tests/test_glsl_comments.py` with Java17 for comment and statement-boundary controls. The executed NativeUiGameSmoke and NativeEssentialTypedPipelineSmoke are public; use the own fixture in `integration/dimensions/tests/fixtures/typed-pipeline/single-line/typed_pipeline_test.fsh` in place of the multiline fragment fixture. Exact locally owned original GAME fixtures must be sealed using the previously published original-input hashes; no original binaries are redistributed.

On the authorized computer, `python run-parser62-controller93-runtime.py --trial 2` repeats the marker-confined logged-in Modrinth MC1.20.1/Forge47.4.26/native-required/stack1024 run and restores all inputs. Trial1 is accepted; trial2 is an instruction. The matched baseline command `python run-parser61-baseline-controller93-runtime.py --trial 2` deliberately reproduces the old parser failure and is expected to return nonzero after restoration. No fullpack gameplay/FPS acceptance follows from these diagnostics.
