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
old_field = '''    @Unique private final Object async$storageLock = new Object();
'''
new_field = '''    @Unique private final Object async$storageLock = new Object();
    // Query readers consume a stable point-in-time view without allocating/copying
    // the entire section on every spatial query. Writers publish a fresh list while
    // holding the same storage lock used by the verified thread-safety layer.
    @Unique private volatile List<T> async$storageSnapshot = List.of();
'''
if text.count(old_field) != 1:
    raise SystemExit("source drift: storage lock field not found exactly once")
text = text.replace(old_field, new_field, 1)

old_init = '''    private void async$init(Class<?> clazz, Visibility status, CallbackInfo ci) {
        async$atomicStatus.set(status != null ? status : Visibility.HIDDEN);
    }
'''
new_init = '''    private void async$init(Class<?> clazz, Visibility status, CallbackInfo ci) {
        async$atomicStatus.set(status != null ? status : Visibility.HIDDEN);
        synchronized (async$storageLock) {
            async$publishStorageSnapshot();
        }
    }
'''
if text.count(old_init) != 1:
    raise SystemExit("source drift: init method not found exactly once")
text = text.replace(old_init, new_init, 1)

old_mutators = '''    @Overwrite
    public void add(T entity) {
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
'''
new_mutators = '''    @Overwrite
    public void add(T entity) {
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
'''
if text.count(old_mutators) != 1:
    raise SystemExit("source drift: EntitySection mutator block not found exactly once")
text = text.replace(old_mutators, new_mutators, 1)

# Deliberately leave isEmpty() exactly as the verified locked implementation. It
# participates in section lifecycle decisions and is not the allocation hotspot.
old_empty = '''    @Overwrite
    public boolean isEmpty() {
        synchronized (async$storageLock) {
            return storage.isEmpty();
        }
    }
'''
if text.count(old_empty) != 1:
    raise SystemExit("source drift: verified locked isEmpty implementation missing")

old_stream = '''    @WrapMethod(method = "getEntities()Ljava/util/stream/Stream;")
    private Stream<T> async$snapshotEntities(Operation<Stream<T>> original) {
        List<T> snapshot;
        synchronized (async$storageLock) {
            snapshot = new ArrayList<>(storage);
        }
        return snapshot.stream().filter(Objects::nonNull);
    }
'''
new_stream = '''    @WrapMethod(method = "getEntities()Ljava/util/stream/Stream;")
    private Stream<T> async$snapshotEntities(Operation<Stream<T>> original) {
        return async$storageSnapshot.stream().filter(Objects::nonNull);
    }
'''
if text.count(old_stream) != 1:
    raise SystemExit("source drift: untyped EntitySection query wrapper not found exactly once")
text = text.replace(old_stream, new_stream, 1)

old_typed = '''        List<T> snapshot;
        synchronized (async$storageLock) {
            snapshot = new ArrayList<>(storage);
        }
        for (T entity : snapshot) {
'''
new_typed = '''        List<T> snapshot = async$storageSnapshot;
        for (T entity : snapshot) {
'''
if text.count(old_typed) != 1:
    raise SystemExit("source drift: typed EntitySection copy-on-query block not found exactly once")
text = text.replace(old_typed, new_typed, 1)

helper_anchor = '''    @Overwrite
    public Visibility getStatus() {
'''
helper = '''    @Unique
    private void async$publishStorageSnapshot() {
        // ArrayList is never mutated after this volatile publication; readers may
        // safely finish iterating an older snapshot while a writer publishes a new
        // one. This preserves the prior point-in-time query snapshot semantics.
        async$storageSnapshot = new ArrayList<>(storage);
    }

'''
if text.count(helper_anchor) != 1:
    raise SystemExit("source drift: helper insertion anchor missing")
text = text.replace(helper_anchor, helper + helper_anchor, 1)

required = (
    "volatile List<T> async$storageSnapshot = List.of()",
    "async$publishStorageSnapshot();",
    "List<T> snapshot = async$storageSnapshot;",
    "return async$storageSnapshot.stream()",
    "synchronized (async$storageLock) {\n            return storage.isEmpty();",
)
for needle in required:
    if needle not in text:
        raise SystemExit(f"snapshot-on-write invariant missing: {needle}")
if "snapshot = new ArrayList<>(storage);" in text:
    raise SystemExit("copy-on-query EntitySection allocation survived performance patch")

path.write_text(text, encoding="utf-8")
print("HariMultiThread Ultimate EntitySection snapshot-on-write optimization applied")
