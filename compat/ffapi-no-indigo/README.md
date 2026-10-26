# Native session without Indigo

The user explicitly removed Oculus and Indigo and requires native Vulkan rather than an automatic OpenGL fallback. ForgifiedFabricAPI0.92.6+1.11.15 contains Indigo as one of45 bundled modules. Removing the entire API would remove hooks that other gameplay mods need. This exact-input recipe removes only the Indigo JAR and its Jar-in-Jar metadata record, retaining the other44 modules byte for byte, all top-level code/assets/notices and the Fabric Rendering API. Output resource metadata uses Minecraft1.20.1 format15. Original source and private local output stay recoverable.

The [official Indigo plugin](https://github.com/Sinytra/ForgifiedFabricAPI/blob/1.20.1/fabric-renderer-indigo/src/client/java/net/fabricmc/fabric/impl/client/indigo/IndigoMixinConfigPlugin.java) selects its renderer from static mod metadata during mixin configuration. Hari already implements its own Fabric Rendering API terrain/mesh provider; this recipe avoids the competing renderer without falsely claiming that all remaining native API use or arbitrary content has passed. [Official API project/license](https://github.com/Sinytra/ForgifiedFabricAPI/tree/1.20.1). Upstream Apache-2.0 notices are preserved; new recipe code is GPL-3.0-only.

```powershell
python compat/ffapi-no-indigo/recipe.py ORIGINAL_PINNED_FFAPI.jar FRESH_PRIVATE_NO_INDIGO.jar
```

Oculus is disabled recoverably, together with only its dependent shader-integration addons; original gameplay cores remain. Native-required preflight must succeed before a new game launch. Untranslated calls remain blockers rather than authorization to force the renderer or silently remove gameplay features. [Current full-pack state and exact removal evidence](../../benchmarks/results/fullpack-20261005/no-aux-performance/REPORT.md).
