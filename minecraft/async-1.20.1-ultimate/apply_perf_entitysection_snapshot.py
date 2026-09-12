#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: apply_perf_entitysection_snapshot.py <merged-upstream-root>")

root = Path(sys.argv[1]).resolve()
path = root / "common/src/main/java/com/axalotl/async/common/mixin/entity/movement/EntitySectionMixin.java"
if not path.is_file():
    raise SystemExit(f"missing transformed source: {path}")

text = path.read_text(encoding="utf-8")


def replace_once(old: str, new: str, label: str) -> None:
    global text
    found = text.count(old)
    if found != 1:
        raise SystemExit(f"{label}: expected one source marker, found {found}")
    text = text.replace(old, new, 1)

replace_once("import java.util.ArrayList;\n", "", "remove per-query ArrayList import")
replace_once("import java.util.Objects;\n", "", "remove null filter import")
replace_once(
'''    @Unique private final Object async$storageLock = new Object();
''',
'''    @Unique private final Object async$storageLock = new Object();
    // Immutable publication snapshot: readers never iterate the mutable
    // ClassInstanceMultiMap. Mutations are much rarer than spatial queries, so
    // pay the copy cost at add/remove instead of on every collision/entity lookup.
    @Unique private volatile List<T> async$storageSnapshot = List.of();
''',
    "snapshot field",
)
replace_once(
'''    private void async$init(Class<?> clazz, Visibility status, CallbackInfo ci) {
        async$atomicStatus.set(status != null ? status : Visibility.HIDDEN);
    }
''',
'''    private void async$init(Class<?> clazz, Visibility status, CallbackInfo ci) {
        async$atomicStatus.set(status != null ? status : Visibility.HIDDEN);
        async$storageSnapshot = List.copyOf(storage);
    }

    @Unique
    private void async$publishStorageSnapshot() {
        // Called only while holding async$storageLock. The volatile assignment is
        // the linearization/publication point for lock-free readers.
        async$storageSnapshot = List.copyOf(storage);
    }
''',
    "constructor snapshot initialization",
)
replace_once(
'''    public void add(T entity) {
        synchronized (async$storageLock) {
            storage.add(entity);
        }
    }

    @Overwrite
    public boolean remove(T entity) {
        synchronized (async$storageLock) {
            return storage.remove(entity);
        }
    }

    @Overwrite
    public boolean isEmpty() {
        synchronized (async$storageLock) {
            return storage.isEmpty();
        }
    }
''',
'''    public void add(T entity) {
        synchronized (async$storageLock) {
            storage.add(entity);
            async$publishStorageSnapshot();
        }
    }

    @Overwrite
    public boolean remove(T entity) {
        synchronized (async$storageLock) {
            boolean removed = storage.remove(entity);
            if (removed) async$publishStorageSnapshot();
            return removed;
        }
    }

    @Overwrite
    public boolean isEmpty() {
        return async$storageSnapshot.isEmpty();
    }
''',
    "mutation publication and lock-free isEmpty",
)
replace_once(
'''    @WrapMethod(method = "getEntities()Ljava/util/stream/Stream;")
    private Stream<T> async$snapshotEntities(Operation<Stream<T>> original) {
        List<T> snapshot;
        synchronized (async$storageLock) {
            snapshot = new ArrayList<>(storage);
        }
        return snapshot.stream().filter(Objects::nonNull);
    }
''',
'''    @WrapMethod(method = "getEntities()Ljava/util/stream/Stream;")
    private Stream<T> async$snapshotEntities(Operation<Stream<T>> original) {
        return async$storageSnapshot.stream();
    }
''',
    "lock-free untyped query",
)
replace_once(
'''        List<T> snapshot;
        synchronized (async$storageLock) {
            snapshot = new ArrayList<>(storage);
        }
        for (T entity : snapshot) {
''',
'''        List<T> snapshot = async$storageSnapshot;
        for (T entity : snapshot) {
''',
    "lock-free typed query",
)

if "new ArrayList<>(storage)" in text:
    raise SystemExit("per-query storage copy survived EntitySection snapshot patch")
if text.count("async$publishStorageSnapshot()") != 3:
    raise SystemExit("unexpected snapshot publication count")
if "volatile List<T> async$storageSnapshot" not in text:
    raise SystemExit("volatile EntitySection snapshot missing")

path.write_text(text, encoding="utf-8")
print("HariMultiThread Ultimate copy-on-write EntitySection snapshot optimization applied")
