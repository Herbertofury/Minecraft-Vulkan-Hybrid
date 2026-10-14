# Local Enhanced Celestials renderer dependency repair

The pinned original Enhanced Celestials2 shader add-on hard-requires Embeddium even though it has no direct Embeddium class references. The complete Noxviola launch without the excluded renderer stopped before mod construction. This recipe changes only that dependency to optional, preserving every code/asset/license entry and valid format15 metadata. It requires the exact original SHA256 and a fresh output; original and generated archives remain private because upstream metadata is All Rights Reserved. Recipe code is GPL-3.0-only. Retain the original disabled copy for recovery.

Use alongside the separately tested Oculus memory-copy repair, not as a shader capability claim. Full-pack active shaderpack parity and native Vulkan support remain unaccepted. No unsupported Vulkan safety-gate entry is added. Runtime failure and the subsequent full-pack result are recorded separately in the complete-pack evidence.

```text
python recipe.py ORIGINAL.jar NEW_PRIVATE_OUTPUT.jar
```
