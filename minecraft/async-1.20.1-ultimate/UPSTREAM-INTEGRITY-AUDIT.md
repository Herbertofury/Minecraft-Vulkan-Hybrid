# Upstream integrity audit — 2026-09-30

Scope: Minecraft 1.20.1, Forge 47.4.23, Java 17. Original Hari source is immutable commit f381611c2d71a85192e2028f9e30c03823a6482b. The Forge Vulkan import is pinned to 0ceac5d47f84c910d2f5f6d007b0ffe266c6f736. Original Vulkan current dev head 087ef24e58e5522dec5ccb44f279768786bfb6da was checked; newer Minecraft branches cannot replace the 1.20.1 APIs wholesale.

## Defects repaired in the candidate

- The imported MinecraftMixin caught and swallowed allowlisted RuntimeExceptions around an entire client tick. Removed that redirect and the unused exception boundary and prefix list. Tick failures follow vanilla's reporting path.
- Its empty emergencySave redirect disabled vanilla integrated-world emergency saves. Removed the redirect.
- The production Vulkan log contained a real minecraft:null.vsh failure for forge:rendertype_entity_unlit_translucent. Resolve vertex/fragment programs from the active shader JSON, retain Forge namespace defaults, preprocess imports through the active ResourceProvider, and close resource streams.
- Remove approximate vertex-format-based shader substitution and fabricated zero uniforms. Missing resources, conversion failures, or missing bindings now identify the actual shader defect instead of silently changing visuals.
- Fix the QA client's invalid simulationDistance=4 to the supported minimum 5.

## Verification status

Candidate reconstruction completed. New compile, reproducibility, dedicated server, packaged Vulkan, packaged Embeddium, resource reload, resize, injected external-looking tick failure, and saved-world reopen checks are pending. The previous distributed artifact remains the previously verified build until these gates pass. No claim of universal compatibility, hardware performance, or an error-free release is made while verification is pending.

Offline PortableMC's authentication-service 401 and Embeddium's retained GPU-bridge support notice are external fixture/support diagnostics. Production authentication is not altered and third-party notices are not hidden.
