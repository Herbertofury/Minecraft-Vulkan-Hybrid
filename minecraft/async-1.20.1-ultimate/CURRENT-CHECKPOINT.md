# Hari 2.4.1 current checkpoint — 2026-10-02

Target: Minecraft 1.20.1 / Forge 47.4.23 / Java 17.
Product: 6594c6cc652db554213c52ae02f7acd8f439644c.
Exact JAR: HariMultiThread-Ultimate-1.20.1-2.4.1-vulkan-hybrid.jar; 30,730,495 bytes; SHA-256 58ceda73c9328c78fc3d82eb652475cb4c7ddd91f3078d6d31717eb0ea9d432f.

## Completed product and current proof

Recommendations 1/2/5: indexed entity scheduling and shared CPU budgets; race-free mesh worker wakeups; reusable batched Vulkan transfers with original dependency/fence/resource ownership; pinned upstream cancelled-sort improvement and researched/adapted upload principles. C2ME uses its original chunk/RNG/entity ownership, with Hari worker handoffs before locks and managed worker barriers. Sensor comparator decompiler throw stubs and complete section-graph reachability were repaired. Tick/worker faults propagate into real crash reports and emergency saves.

The OpenAL reload race is repaired by ordering original channel clear behind queued sound operations on the sound executor and waiting before context cleanup. Live sounds and original failures remain intact. Imported empty GL overwrite stubs are not advertised as translations; reviewed resource/state overloads have exact JVM descriptors and invalidated old caches. Shader source boundaries retain every string, byte length and buffer position. Minecraft rendering uses its dedicated Vulkan pipelines; incompatible external GL paths retain the complete compatibility renderer before Vulkan starts.

All four exact-binary gates passed:
- Compile/reproducible rebuild and 17 focused suites: 36899664277 / 110495374267.
- Native server, strict C2ME, persistence and deliberate failure/recovery: 36899664082 / 110495373312.
- Seven installed Forge production clients: 36899664091 / 110495373227.
- Khronos sync validation, three acknowledged live-sound reloads, 49-chunk far/return readiness, sustained terrain and two-worker restart: 36900474237 / 110498069526.

Fresh immutable 23-step reconstruction matches all 989 complete-source files, including all 44 legitimate builder-package files. The latest actual queue component benchmark is 63,338,047ns to 26,159,995ns (58.7% less time); allocation is 82.9% lower. This is not an overall Windows/RTX 4090 FPS measurement.

## Final visual challenge and delivery

Additional view/simulation-distance-eight QA passed: 36906027597 / 110516668516, QA commit 384eb2122c7f490a29caf4c3601bf2307a705191. It used the exact same immutable JAR, confirmed all 169 entity-ticking chunks with loaded entity data at each destination, and retained three consecutive central captures above 90% terrain coverage. Final far/return coverage was 95.1%/96.7%; full-window scenes and crops were manually reviewed. The genuine Khronos layer loaded, synchronization validation remained clean, three active-sound reloads passed, and both explicit two-worker server boots retained strict C2ME RNG and saved block-entity data. Finite-distance scene frontiers remain visible; there is no universal full-window terrain claim. Two disposable fixture mistakes (a stale distance-four assertion and insufficient simulation radius) remain fully preserved.

The exact tested JAR is delivered to Drive as 1IHdqge9h6a8OZprWP6HAdoMO3ekXqLzV. Full raw Drive readback matches the tested SHA-256, size and ZIP CRC.

Historical diagnostics preserve all 1,168 files across 29 immutable provider runs in two verified independent ZIPs below the 100 MiB upload limit. SHA/CRC/entry-union checks passed. Automatic approval review rejected uploading the first historical-diagnostics ZIP to existing Hari Drive folder 1vq6hWD_QqVdxMlgYrupWbhVdlNDa8K-B twice: it requires explicit approval for this payload and destination after the risk notice. The audit found no real tokens, private keys, signed URLs or personal email addresses; matches were setup-java's GITHUB_TOKEN variable name and Java stack-trace module versions. Do not bypass the rejection. A separate complete-source ZIP upload was also rejected: the automatic reviewer classified it as sensitive code and required payload/destination approval. No source or historical archive was created on Drive. The complete source and full proof collection are now packaged with every ZIP CRC and manifest entry verified. Core release is 33,000,692 bytes; source-only is 2,648,291 bytes; current proof is 74,461,126 bytes; historical volumes are 93,316,700 and 81,962,768 bytes. No archive exceeds the connector limit. Source and blocked historical diagnostic uploads were not retried or rerouted. Provide the concrete collection, then ask for specific archive/destination approval.

Machine-readable state: PERFORMANCE-RELEASE-CHECKPOINT.json. Detailed implementation and pins: VULKAN-PERFORMANCE-2.4.1.md. Build/reproduction: REPRODUCE-2.4.1.md. No previous candidate or historical success is promoted by filename. Preserve the other 63 projects.

Final archive bytes and SHA-256 values: PERFORMANCE-DELIVERY-RECEIPT.json. All code, full source hash proof and companion indexes are versioned here. Packaged CHECKPOINT.json is the frozen accepted product checkpoint; the separate delivery receipt records the completed collection without a self-hash cycle.

## October 2 continuation

All five archive byte counts and SHA-256 values exactly match the frozen October 1 receipt. All 989 source files, 338 current evidence files and 1,168 historical diagnostic files were restored and verified. The tested product and its accepted runtime proof are unchanged.

The user replied Cotinue after the concrete upload question. Automatic approval review rejected the source ZIP upload again, explicitly saying continue does not authorize this sensitive payload and Drive destination. No file was created. Do not retry, rename or reroute the blocked uploads. The exact remaining user action is: Yes, upload all five ZIPs to my existing Hari Drive folder 1vq6hWD_QqVdxMlgYrupWbhVdlNDa8K-B.

The actual GitHub Wiki source page and navigation are committed on main at 76613a1d1cfd68c5d5935221d01118fe6fc40625. Actual publication passed in run 37065625868 / job 111032602469. The source commit has wiki-publication=success with fresh-clone-verified Wiki commit 1417b9f13925903b480a32d6afbc6e89272b0514. Native Git readback of the actual Wiki page matches the intended bytes. All 63 project records remain unchanged.
