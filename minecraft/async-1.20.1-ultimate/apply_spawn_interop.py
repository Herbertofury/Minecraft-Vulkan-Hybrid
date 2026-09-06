#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: apply_spawn_interop.py <merged-upstream-root>")

root = Path(sys.argv[1]).resolve()


def file(rel: str) -> Path:
    p = root / rel
    if not p.is_file():
        raise SystemExit(f"missing merged file: {rel}")
    return p


def replace(rel: str, old: str, new: str, count: int = 1) -> None:
    p = file(rel)
    text = p.read_text(encoding="utf-8")
    found = text.count(old)
    if found != count:
        raise SystemExit(f"source drift in {rel}: expected {count}, found {found}: {old[:180]!r}")
    p.write_text(text.replace(old, new, count), encoding="utf-8")

# Latest Async (July 28, 2026) kept asynchronous spawning but rolled back broad
# worker-thread chunk lookup handling. Each spawn worker instead carries the exact
# LevelChunk that ServerChunkCache already scheduled. Port that model to 1.20.1.
server_chunk = "common/src/main/java/com/axalotl/async/common/mixin/server/ServerChunkCacheMixin.java"
replace(
    server_chunk,
    "import com.axalotl.async.common.config.AsyncConfig;",
    "import com.axalotl.async.common.config.AsyncConfig;\nimport com.axalotl.async.common.spawn.SpawnChunkContext;"
)
replace(
    server_chunk,
'''        harimt$spawnTasks.add(() -> NaturalSpawner.spawnForChunk(
                level, chunk, spawnState, spawnAnimals, spawnMonsters, rareSpawn));''',
'''        harimt$spawnTasks.add(() -> SpawnChunkContext.runWith(chunk, () ->
                NaturalSpawner.spawnForChunk(
                        level, chunk, spawnState, spawnAnimals, spawnMonsters, rareSpawn)));'''
)

mixins = "common/src/main/resources/harimt.common.mixins.json"
replace(
    mixins,
    '    "spawn.NaturalSpawnerMixin",',
    '    "spawn.NaturalSpawnerMixin",\n    "spawn.NaturalSpawnerScheduledChunkMixin",'
)

# Mixin 0.8.5's annotation processor correctly rejects @Inject handlers declared
# in an interface mixin ("Injector in interface is unsupported"). The target is
# itself a Java interface default method, so use the supported interface @Overwrite
# form instead. Preserve vanilla 1.20.1 exactly when async spawn is disabled and
# preserve HMT's existing synchronized entity-add behavior when it is enabled.
server_level_accessor = "common/src/main/java/com/axalotl/async/common/mixin/spawn/ServerLevelAccessorMixin.java"
replace(
    server_level_accessor,
'''import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;''',
'''import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;'''
)
replace(
    server_level_accessor,
'''    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @Inject(method={"addFreshEntityWithPassengers"}, at={@At(value="HEAD")}, cancellable=true)
    default public void async$syncAddFreshEntityWithPassengers(Entity entity, CallbackInfo ci) {
        if (AsyncConfig.disabled.getValue().booleanValue() || !AsyncConfig.enableAsyncSpawn.getValue().booleanValue()) {
            return;
        }
        ci.cancel();
        Object object = ParallelProcessor.getEntityAddLock();
        synchronized (object) {
            entity.getSelfAndPassengers().forEach(e -> ((ServerLevelAccessor)this).addFreshEntity(e));
        }
    }''',
'''    /**
     * Replaces the vanilla interface default method so Forge's Mixin AP can
     * generate a production refmap. The disabled path is vanilla 1.20.1's exact
     * self-and-passenger add loop; async-spawn mode retains HMT's add lock.
     */
    @Overwrite
    default void addFreshEntityWithPassengers(Entity entity) {
        if (AsyncConfig.disabled.getValue().booleanValue() || !AsyncConfig.enableAsyncSpawn.getValue().booleanValue()) {
            entity.getSelfAndPassengers().forEach(e -> ((ServerLevelAccessor)this).addFreshEntity(e));
            return;
        }
        Object object = ParallelProcessor.getEntityAddLock();
        synchronized (object) {
            entity.getSelfAndPassengers().forEach(e -> ((ServerLevelAccessor)this).addFreshEntity(e));
        }
    }'''
)

# ---------------------------------------------------------------------------
# Production Forge shadow hardening.
#
# The real packaged server proved the refmap is loaded, then failed because CFR's
# decompiled EntityMixin kept ordinary target members as @Shadow declarations.
# Those shadows were not emitted into the refmap, so SRG runtime attachment looked
# for literal Mojmap names. Route the private passengers field through a mapped
# @Accessor and call public Entity methods directly so Forge reobfuscation owns
# their names instead of Mixin shadow attachment.
# ---------------------------------------------------------------------------
entity_accessor = "common/src/main/java/com/axalotl/async/common/mixin/accessor/EntityAccessor.java"
replace(
    entity_accessor,
    "import net.minecraft.world.entity.Entity;",
    "import com.google.common.collect.ImmutableList;\nimport net.minecraft.world.entity.Entity;"
)
replace(
    entity_accessor,
'''    @Accessor(value="boardingCooldown")
    public void setBoardingCooldown(int var1);
}''',
'''    @Accessor(value="boardingCooldown")
    public void setBoardingCooldown(int var1);

    @Accessor(value="passengers")
    public ImmutableList<Entity> getPassengersField();

    @Accessor(value="passengers")
    public void setPassengersField(ImmutableList<Entity> passengers);
}'''
)

entity_mixin = "common/src/main/java/com/axalotl/async/common/mixin/entity/EntityMixin.java"
replace(
    entity_mixin,
'''import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;''',
'''import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;'''
)
replace(
    entity_mixin,
'''public abstract class EntityMixin {
    @Shadow
    private ImmutableList<Entity> passengers;
    @Unique
    private final Object async$lock = new Object();

    @Shadow
    public abstract BlockPos blockPosition();

    @Shadow
    public abstract Level level();''',
'''public abstract class EntityMixin {
    @Unique
    private final Object async$lock = new Object();'''
)
replace(
    entity_mixin,
'''        if (blockState == null) {
            Level level = this.level();
            if (level instanceof ServerLevel) {
                ServerLevel serverLevel = (ServerLevel)level;
                BlockPos pos = this.blockPosition();''',
'''        if (blockState == null) {
            Entity self = (Entity)(Object)this;
            Level level = self.level();
            if (level instanceof ServerLevel) {
                ServerLevel serverLevel = (ServerLevel)level;
                BlockPos pos = self.blockPosition();'''
)
replace(
    entity_mixin,
'''        synchronized (object) {
            ArrayList list = Lists.newArrayList(this.passengers);
            if (!this.level().isClientSide() && passenger instanceof Player && !list.isEmpty() && !(list.get(0) instanceof Player)) {
                list.add(0, passenger);
            } else {
                list.add(passenger);
            }
            this.passengers = ImmutableList.copyOf((Collection)list);
        }''',
'''        synchronized (object) {
            EntityAccessor accessor = (EntityAccessor)(Object)this;
            ArrayList list = Lists.newArrayList(accessor.getPassengersField());
            if (!self.level().isClientSide() && passenger instanceof Player && !list.isEmpty() && !(list.get(0) instanceof Player)) {
                list.add(0, passenger);
            } else {
                list.add(passenger);
            }
            accessor.setPassengersField(ImmutableList.copyOf((Collection)list));
        }'''
)
replace(
    entity_mixin,
'''        synchronized (object) {
            ArrayList<Entity> list = new ArrayList<Entity>((Collection<Entity>)this.passengers);
            list.remove(passenger);
            this.passengers = ImmutableList.copyOf(list);
            ((EntityAccessor)passenger).setBoardingCooldown(60);
        }''',
'''        synchronized (object) {
            EntityAccessor accessor = (EntityAccessor)(Object)this;
            ArrayList<Entity> list = new ArrayList<Entity>((Collection<Entity>)accessor.getPassengersField());
            list.remove(passenger);
            accessor.setPassengersField(ImmutableList.copyOf(list));
            ((EntityAccessor)passenger).setBoardingCooldown(60);
        }'''
)
replace(
    entity_mixin,
'''        synchronized (object) {
            snapshot = this.passengers;
        }''',
'''        synchronized (object) {
            snapshot = ((EntityAccessor)(Object)this).getPassengersField();
        }'''
)
replace(
    entity_mixin,
'''        synchronized (object) {
            cir.setReturnValue(this.passengers);
        }''',
'''        synchronized (object) {
            cir.setReturnValue(((EntityAccessor)(Object)this).getPassengersField());
        }'''
)
replace(
    entity_mixin,
'''        synchronized (object) {
            cir.setReturnValue(this.passengers.isEmpty() ? null : (Entity)(Object)this.passengers.get(0));
        }''',
'''        synchronized (object) {
            ImmutableList<Entity> snapshot = ((EntityAccessor)(Object)this).getPassengersField();
            cir.setReturnValue(snapshot.isEmpty() ? null : snapshot.get(0));
        }'''
)

# AttributeInstanceMixin was decompiled from a later attribute layout. Forge
# 1.20.1 uses UUID keys and a Set for permanent modifiers; the old declaration
# already triggered a Mixin AP "Cannot find target for @Shadow field" warning.
# Match the 1.20.1 field descriptors exactly and keep all three collections truly
# concurrent, including per-operation sets created after construction.
attribute_mixin = "common/src/main/java/com/axalotl/async/common/mixin/entity/AttributeInstanceMixin.java"
replace(
    attribute_mixin,
'''import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;''',
'''import java.util.Map;
import java.util.Set;
import java.util.UUID;'''
)
replace(
    attribute_mixin,
'''import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;''',
'''import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;'''
)
replace(
    attribute_mixin,
'''    @Shadow
    @Final
    @Mutable
    private Map<ResourceLocation, AttributeModifier> modifierById;
    @Shadow
    @Final
    @Mutable
    private Map<ResourceLocation, AttributeModifier> permanentModifiers;
    @Shadow
    private final Map<AttributeModifier.Operation, Map<ResourceLocation, AttributeModifier>> modifiersByOperation = ConcurrentCollections.newHashMap();

    @Inject(method={"<init>"}, at={@At(value="RETURN")})
    private void makeThreadSafe(CallbackInfo ci) {
        this.modifierById = new ConcurrentHashMap<ResourceLocation, AttributeModifier>(this.modifierById);
        this.permanentModifiers = new ConcurrentHashMap<ResourceLocation, AttributeModifier>(this.permanentModifiers);
    }''',
'''    @Shadow
    @Final
    @Mutable
    private Map<UUID, AttributeModifier> modifierById;
    @Shadow
    @Final
    @Mutable
    private Set<AttributeModifier> permanentModifiers;
    @Shadow
    @Final
    @Mutable
    private Map<AttributeModifier.Operation, Set<AttributeModifier>> modifiersByOperation;

    @Inject(method={"<init>"}, at={@At(value="RETURN")})
    private void makeThreadSafe(CallbackInfo ci) {
        Map<UUID, AttributeModifier> ids = ConcurrentCollections.newHashMap();
        ids.putAll(this.modifierById);
        this.modifierById = ids;

        Set<AttributeModifier> permanent = ConcurrentCollections.newHashSet();
        permanent.addAll(this.permanentModifiers);
        this.permanentModifiers = permanent;

        Map<AttributeModifier.Operation, Set<AttributeModifier>> byOperation = ConcurrentCollections.newHashMap();
        this.modifiersByOperation.forEach((operation, modifiers) -> {
            Set<AttributeModifier> concurrent = ConcurrentCollections.newHashSet();
            concurrent.addAll(modifiers);
            byOperation.put(operation, concurrent);
        });
        this.modifiersByOperation = byOperation;
    }

    /**
     * @author HariMultiThread Ultimate
     * @reason Preserve vanilla semantics while ensuring newly-created operation
     *         buckets remain safe under HMT's concurrent entity ticking.
     */
    @Overwrite
    public Set<AttributeModifier> getModifiers(AttributeModifier.Operation operation) {
        return this.modifiersByOperation.computeIfAbsent(operation, key -> ConcurrentCollections.newHashSet());
    }'''
)

# Negative/positive assertions so future HMT source changes cannot silently drop
# the rollback semantics or reintroduce AP-invalid / production-unmapped members.
server_text = file(server_chunk).read_text(encoding="utf-8")
if "SpawnChunkContext.runWith(chunk" not in server_text:
    raise SystemExit("scheduled spawn chunk context was not wired into ServerChunkCacheMixin")
if "CompletableFuture.runAsync" in server_text:
    raise SystemExit("fire-and-forget spawn/random tick work reappeared")

mixins_text = file(mixins).read_text(encoding="utf-8")
if mixins_text.count('"spawn.NaturalSpawnerScheduledChunkMixin"') != 1:
    raise SystemExit("scheduled-chunk NaturalSpawner mixin registration missing/duplicated")

accessor_text = file(server_level_accessor).read_text(encoding="utf-8")
# CFR's file-header comment still names classes it could not load, so search for
# live Java syntax instead of bare words which can appear only inside comments.
if "import org.spongepowered.asm.mixin.injection.Inject;" in accessor_text:
    raise SystemExit("AP-invalid Inject import remains in ServerLevelAccessorMixin")
if "@Inject(" in accessor_text or "CallbackInfo ci" in accessor_text:
    raise SystemExit("AP-invalid injector handler remains in ServerLevelAccessorMixin")
if accessor_text.count("@Overwrite") != 1:
    raise SystemExit("ServerLevelAccessorMixin must contain exactly one supported @Overwrite")
if "default void addFreshEntityWithPassengers(Entity entity)" not in accessor_text:
    raise SystemExit("ServerLevelAccessor vanilla default-method overwrite missing")
if accessor_text.count("entity.getSelfAndPassengers().forEach(e -> ((ServerLevelAccessor)this).addFreshEntity(e));") != 2:
    raise SystemExit("ServerLevelAccessor vanilla/HMT spawn bodies drifted")

entity_accessor_text = file(entity_accessor).read_text(encoding="utf-8")
if entity_accessor_text.count('@Accessor(value="passengers")') != 2:
    raise SystemExit("Entity passengers getter/setter accessors missing or duplicated")
entity_text = file(entity_mixin).read_text(encoding="utf-8")
if "\n    @Shadow\n" in entity_text:
    raise SystemExit("production-unmapped EntityMixin @Shadow member remains")
for stale in ("this.passengers", "this.level()", "this.blockPosition()"):
    if stale in entity_text:
        raise SystemExit(f"production-unmapped EntityMixin access remains: {stale}")
if entity_text.count("getPassengersField()") < 4 or entity_text.count("setPassengersField(") != 2:
    raise SystemExit("EntityMixin passenger accessor routing incomplete")

attribute_text = file(attribute_mixin).read_text(encoding="utf-8")
if "ResourceLocation" in attribute_text or "ConcurrentHashMap" in attribute_text:
    raise SystemExit("newer-version AttributeInstance collection shape remains")
for expected in (
    "private Map<UUID, AttributeModifier> modifierById;",
    "private Set<AttributeModifier> permanentModifiers;",
    "private Map<AttributeModifier.Operation, Set<AttributeModifier>> modifiersByOperation;",
    "public Set<AttributeModifier> getModifiers(AttributeModifier.Operation operation)",
):
    if expected not in attribute_text:
        raise SystemExit(f"Forge 1.20.1 AttributeInstance hardening missing: {expected}")

print("Latest Async scheduled-chunk spawn interop + AP-safe production Mixin shadow hardening applied successfully")
