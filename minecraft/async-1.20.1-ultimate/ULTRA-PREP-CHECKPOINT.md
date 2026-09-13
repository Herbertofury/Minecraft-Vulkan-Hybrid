# HariMultiThread Ultimate Ultra Performance Prep

This is a non-triggering recovery checkpoint only. It does not change canonical 2.1.1 and does not start a benchmark.

- Frozen baseline: `async-1.20.1-ultimate` @ `3ff0fc39fea03921a9551ad57ee82a27b5952de0`.
- Authoritative active max flight remains run `34774605613`, job `103770295203`, max branch head `6bb9e62f65c097fc270e72e7b969f9e045da1dd3`.
- Ultra prep base is the exact fixed max head above.
- Ultra adds three isolated follow-ups on top of max: array-backed generic `forEachParallel`, allocation-free indexed `Future.isDone()` polling, and packed single-map GPU adjacency.
- `apply_perf_ultra_stack.py` is fail-closed and requires the final cumulative production delta to remain exactly the same seven files as max.
- Fresh orchestration from the exact published 2.1.1 source reproduced the independently composed ultra tree byte-for-byte.
- Hot-path guards prove `futures.stream().allMatch(Future::isDone)` and `new ConcurrentLinkedQueue<>(items)` are absent after ultra application.
- Packed single-map adjacency preserves exact deferred-source ordering/coverage and per-source GPU pair order; prior randomized property test covered 10,000 population/source/pair cases.

## Tooling hashes

- `apply_perf_array_parallel_batches.py`: `6dcaf3eba4b0631b43a1db974c575a7d1e0050ebad84fcc75f4b44ed80d3d017`
- `apply_perf_wait_loop.py`: `5775ba81fe70e280b49b8543b7588110627ce38095d901a42b78f507b4458aa4`
- `apply_perf_packed_single_map.py`: `6cd6c7ad32bde2c65796fb1af14b0ed5726a2c3661baf8f8dc87540d1e094e02`
- `apply_perf_ultra_stack.py`: `7804623dc3dde3f04e1a404ec5545c82e7c126d32a2cf61b6b0e0349c9be7c75`
- decoded `ultra-prep-bundle.tar.gz`: `b08ca53a44e8d8d961c6426ed91da7681109a244da6bcd576cdd5260f61d0ad5`
- base64 wrapper: `570d5328f09e951b275f1780c50159b2566939500eecd865aac85f09f05cea4b`

The bundle was base64-decoded, untarred, and every member compared byte-for-byte with the source scripts before this checkpoint.

## Exact next action

Do not benchmark ultra while max run `34774605613` is still active. Consume the max verdict first. If max fails or narrowly misses the fixed five-lane contract, preserve its evidence and launch exactly one isolated ultra A/B using the same five-lane telemetry, workloads, parity checks, and acceptance thresholds. Do not weaken gates or rerun max unchanged.
