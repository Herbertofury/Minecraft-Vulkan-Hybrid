#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: apply_unique_lock_names.py <merged-upstream-root>")

root = Path(sys.argv[1]).resolve()
path = root / "common/src/main/java/com/axalotl/async/common/mixin/entity/EntityPortalMixin.java"
if not path.is_file():
    raise SystemExit(f"missing EntityPortalMixin: {path}")

text = path.read_text(encoding="utf-8")
old_decl = "    private static final Object async$lock = new Object();"
new_decl = "    private static final Object harimt$portalCreationLock = new Object();"

# EntityMixin and EntityPortalMixin both target vanilla Entity. Upstream gives
# them the same @Unique field name but incompatible shapes: EntityMixin injects
# an instance async$lock, while EntityPortalMixin injects a static async$lock.
# Production Mixin then leaves bytecode expecting a static Entity.async$lock
# while the actual target field is the instance one, causing ICCE in Entity's
# class initializer before the server can bootstrap. Give the portal cache its
# own unique symbol rather than weakening either synchronization policy.
if text.count(old_decl) != 1:
    raise SystemExit(
        f"source drift: expected one EntityPortalMixin async$lock declaration, found {text.count(old_decl)}"
    )
if text.count("synchronized (async$lock)") != 1:
    raise SystemExit("source drift: expected one EntityPortalMixin async$lock use")

text = text.replace(old_decl, new_decl, 1)
text = text.replace("synchronized (async$lock)", "synchronized (harimt$portalCreationLock)", 1)

if "async$lock" in text:
    raise SystemExit("EntityPortalMixin still contains colliding async$lock symbol")
if text.count("harimt$portalCreationLock") != 2:
    raise SystemExit("portal lock declaration/use invariant failed")

path.write_text(text, encoding="utf-8")
print("EntityPortalMixin unique static lock name applied successfully")
