#!/usr/bin/env python3
"""Result-preserving scheduler/upload improvements after the integrity layer."""
from pathlib import Path
import json, shutil, sys

root = Path(sys.argv[1]).resolve()
payload = Path(__file__).parent / 'performance-src'
def edit(relative, old, new, count=1):
    path=root/relative
    text=path.read_text()
    found=text.count(old)
    if found != count: raise SystemExit(f'source drift: {relative}: expected {count}, got {found}: {old[:100]}')
    path.write_text(text.replace(old,new))

p='common/src/main/java/com/axalotl/async/common/ParallelProcessor.java'
edit(p, 'new Thread(runnable, "Async-Tick-Pool-Thread-"', 'new AsyncWorkerThread(runnable, "Async-Tick-Pool-Thread-"')
edit(p, '    private static boolean isThreadInPool(Thread thread) {', '    private static boolean isThreadInPool(Thread thread) {\n        if (thread instanceof AsyncWorkerThread) return true;')
edit(p, 'ConcurrentLinkedQueue<T> work = new ConcurrentLinkedQueue<>(items);', 'IndexedWorkQueue<T> work = new IndexedWorkQueue<>(items);')
edit(p, 'ConcurrentLinkedQueue<Entity> workQueue = new ConcurrentLinkedQueue<>(asyncEntities);', 'IndexedWorkQueue<Entity> workQueue = new IndexedWorkQueue<>(asyncEntities);')
edit(p, 'List<ConcurrentLinkedQueue<Entity>> lanes = new ArrayList<>(workerCount);', 'List<List<Entity>> laneItems = new ArrayList<>(workerCount);')
edit(p, 'lanes.add(new ConcurrentLinkedQueue<>());', 'laneItems.add(new ArrayList<>());')
edit(p, 'lanes.get(laneIndex).add(entity);', 'laneItems.get(laneIndex).add(entity);')
needle='''        List<Future<Void>> futures = new ArrayList<>(workerCount);

        for (int w = 0; w < workerCount; w++) {'''
# This spelling occurs in both dispatchers; insert only in the affinity method.
path=root/p; text=path.read_text(); start=text.index('    private static List<Future<Void>> dispatchWithAffinity(')
prefix,tail=text[:start],text[start:]
assert tail.count(needle)==1
tail=tail.replace(needle,'''        List<IndexedWorkQueue<Entity>> lanes = new ArrayList<>(workerCount);
        for (List<Entity> lane : laneItems) lanes.add(new IndexedWorkQueue<>(lane));
        List<Future<Void>> futures = new ArrayList<>(workerCount);

        for (int w = 0; w < workerCount; w++) {''',1)
path.write_text(prefix+tail)
edit(p, '            allDone = futures.stream().allMatch(Future::isDone);', '''            allDone = true;
            for (Future<?> future : futures) {
                if (!future.isDone()) { allDone = false; break; }
            }''')
edit(p, '        boolean timeoutWarned = false;', '''        if (AsyncCommon.HARICHUNK && waitWorld != null) {
            C2meTaskWaiter.await(waitWorld, futures, timeoutNs, elapsed -> {
                totalTimeoutWarnings.increment();
                LOGGER.warn("Async entity tick batch exceeded {}ms timeout ({}ms elapsed), still waiting...",
                        AsyncConfig.staleTaskTimeoutMs.getValue(), TimeUnit.NANOSECONDS.toMillis(elapsed));
            });
            return;
        }
        boolean timeoutWarned = false;''')
edit(p, '     * IMPROVED: Work stealing with shared ConcurrentLinkedQueue.', '     * Snapshot-indexed work stealing without per-entity linked nodes.')

p='common/src/main/java/com/axalotl/async/common/config/AsyncConfig.java'
edit(p, 'return Math.max(1, Runtime.getRuntime().availableProcessors() - 1);', 'return com.axalotl.async.common.CpuWorkBudget.entityWorkers();')
p='common/src/main/java/com/axalotl/async/common/AsyncCommon.java'
edit(p, '|| PlatformUtils.isModLoaded("c2meforge");', '|| PlatformUtils.isModLoaded("c2meforge") || PlatformUtils.isModLoaded("c2mef") || PlatformUtils.isModLoaded("c2me_base");')
p='forge/src/main/java/com/axalotl/async/forge/AsyncForge.java'
edit(p, '        LOGGER.info("Initializing Async...");', '''        LOGGER.info("Initializing Async...");
        com.axalotl.async.common.CpuWorkBudget.configure(
                net.minecraftforge.fml.loading.FMLEnvironment.dist == net.minecraftforge.api.distmarker.Dist.CLIENT,
                AsyncCommon.HARICHUNK);
        LOGGER.info("Hari CPU budget: entityWorkers={} meshWorkers={} externalChunks={}",
                com.axalotl.async.common.CpuWorkBudget.entityWorkers(),
                com.axalotl.async.common.CpuWorkBudget.meshWorkers(), AsyncCommon.HARICHUNK);''')

p='forge/src/main/java/net/vulkanmod/render/chunk/build/TaskDispatcher.java'
edit(p, 'int n = Math.max((Runtime.getRuntime().availableProcessors() - 1) / 2, 1);', 'int n = com.axalotl.async.common.CpuWorkBudget.meshWorkers();')
edit(p, '    private int idleThreads;', '    private volatile int idleThreads;')
edit(p, '''            ChunkTask task = this.pollTask();

            if(task == null)
                synchronized (this) {
                    try {
                        this.idleThreads++;
                        this.wait();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    this.idleThreads--;
                }

            if(task == null)
                continue;''', '''            ChunkTask task;
            synchronized (this) {
                while (!this.stopThreads && (task = this.pollTask()) == null) {
                    this.idleThreads++;
                    try { this.wait(); }
                    catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("Chunk worker interrupted", e);
                    } finally { this.idleThreads--; }
                }
                if (this.stopThreads) return;
                task = this.pollTask();
            }
            if (task == null) continue;''')
# Poll exactly once after waking; do not discard the task that ended the condition loop.
edit(p, '''                while (!this.stopThreads && (task = this.pollTask()) == null) {''', '''                task = this.pollTask();
                while (!this.stopThreads && task == null) {''')
edit(p, '''                    } finally { this.idleThreads--; }
                }
                if (this.stopThreads) return;
                task = this.pollTask();''', '''                    } finally { this.idleThreads--; }
                    task = this.pollTask();
                }
                if (this.stopThreads) {
                    if (task != null) task.discard();
                    return;
                }''')
edit(p, '''        if (chunkTask.highPriority) {
            this.highPriorityTasks.offer(chunkTask);
        } else {
            this.lowPriorityTasks.offer(chunkTask);
        }

        synchronized (this) {
            this.notify();
        }''', '''        synchronized (this) {
            if (this.stopThreads) { chunkTask.discard(); return; }
            if (chunkTask.highPriority) this.highPriorityTasks.offer(chunkTask);
            else this.lowPriorityTasks.offer(chunkTask);
            this.notify();
        }''')

# Backport xCollateral/VulkanMod e5dad791: a cancelled/reset section has no sort state.
p='forge/src/main/java/net/vulkanmod/render/chunk/build/task/SortTransparencyTask.java'
edit(p, '        QuadSorter.SortState transparencyState = compiledSection.transparencyState;', '''        QuadSorter.SortState transparencyState = compiledSection.transparencyState;
        if (transparencyState == null) return Result.CANCELLED;''')

# C2ME owns world RNG and entity tracking mutations. Never disable its checks.
mixins = root/'common/src/main/resources/harimt.common.mixins.json'
config = json.loads(mixins.read_text())
assert 'server.C2meChunkAccessMixin' not in config['mixins']
config['mixins'].append('server.C2meChunkAccessMixin')
mixins.write_text(json.dumps(config, indent=2)+'\n')
p='common/src/main/java/com/axalotl/async/common/mixin/server/ServerChunkCacheMixin.java'
edit(p, 'extends ChunkSource {', 'extends ChunkSource implements com.axalotl.async.common.ChunkOwnerExecutor {')
edit(p, '    @Unique private final List<LevelChunk>', '    @Override public java.util.concurrent.Executor harimt$ownerExecutor() { return this.mainThreadProcessor; }\n\n    @Unique private final List<LevelChunk>')
edit(p, '            ParallelProcessor.forEachParallel(this.level, tasks, Runnable::run);', '''            // Original Forge spawn predicates can read or create neighboring
            // chunks (Aether Swet banner scans do this). SpawnState, world RNG
            // and entity insertion also belong to the world owner, with or
            // without C2ME. Execute each original callback exactly once here.
            for (Runnable task : tasks) task.run();''')
p='common/src/main/java/com/axalotl/async/common/mixin/world/LevelMixin.java'
edit(p, '        AutoCloseable {', '        AutoCloseable, com.axalotl.async.common.WorldRandomAccess {')
edit(p, '    @Shadow\n    @Final\n    private Thread thread;', """    @Shadow
    @Final
    private Thread thread;
    @Shadow @Final @org.spongepowered.asm.mixin.Mutable public net.minecraft.util.RandomSource random;
    @Override public net.minecraft.util.RandomSource harimt$worldRandom() { return this.random; }
    @Override public void harimt$worldRandom(net.minecraft.util.RandomSource value) { this.random = value; }""")
p='common/src/main/java/com/axalotl/async/common/mixin/world/ServerLevelMixin.java'
edit(p, '        this.players = new CopyOnWriteArrayList<>();', """        this.players = new CopyOnWriteArrayList<>();
        if (com.axalotl.async.common.AsyncCommon.HARICHUNK) {
            com.axalotl.async.common.WorldRandomAccess access = (com.axalotl.async.common.WorldRandomAccess) (Object) this;
            access.harimt$worldRandom(new com.axalotl.async.common.ServerOwnedRandom(this.getLevel(), access.harimt$worldRandom()));
        }""")
edit(p, '        ParallelProcessor.forEachParallel(this.getLevel(), toDespawnCheck, Entity::checkDespawn);', """        if (com.axalotl.async.common.AsyncCommon.HARICHUNK) {
            // Despawning changes C2ME's main-thread entity tracker.
            for (Entity entity : toDespawnCheck) entity.checkDespawn();
        } else {
            ParallelProcessor.forEachParallel(this.getLevel(), toDespawnCheck, Entity::checkDespawn);
        }
        toTick.removeIf(Entity::isRemoved);""")
edit(p, '    private boolean wrapAddFreshEntity(Entity entity, Operation<Boolean> original) {', '    private boolean wrapAddFreshEntity(Entity entity, Operation<Boolean> original) {\n        if (com.axalotl.async.common.C2meThreadBoundary.mustHandoff()) {\n            return com.axalotl.async.common.C2meThreadBoundary.call(this.getLevel(), () -> wrapAddFreshEntity(entity, original));\n        }')
# Native thread dump: addFreshEntityWithPassengers must hand off before its outer dimension monitor.
p='common/src/main/java/com/axalotl/async/common/mixin/spawn/ServerLevelAccessorMixin.java'
edit(p, '    default void addFreshEntityWithPassengers(Entity entity) {', """    default void addFreshEntityWithPassengers(Entity entity) {
        ServerLevelAccessor self = (ServerLevelAccessor) this;
        if (com.axalotl.async.common.C2meThreadBoundary.mustHandoff()) {
            com.axalotl.async.common.C2meThreadBoundary.run(self.getLevel(), () -> self.addFreshEntityWithPassengers(entity));
            return;
        }""")
p='common/src/main/java/com/axalotl/async/common/mixin/entity/EntityMixin.java'
edit(p, '    private void setRemoved(Entity.RemovalReason reason, Operation<Void> original) {', """    private void setRemoved(Entity.RemovalReason reason, Operation<Void> original) {
        Entity self = (Entity) (Object) this;
        if (com.axalotl.async.common.C2meThreadBoundary.mustHandoff() && self.level() instanceof ServerLevel world) {
            com.axalotl.async.common.C2meThreadBoundary.run(world, () -> setRemoved(reason, original));
            return;
        }""")
p='common/src/main/java/com/axalotl/async/common/mixin/server/PersistentEntitySectionManagerCallbackMixin.java'
edit(p, '    @Unique\n    private final ReentrantLock', '    @Shadow @org.spongepowered.asm.mixin.Final private net.minecraft.world.level.entity.EntityAccess entity;\n    @Unique\n    private final ReentrantLock')
edit(p, '    private void onMove(Operation<Void> original) {', """    private void onMove(Operation<Void> original) {
        if (com.axalotl.async.common.C2meThreadBoundary.mustHandoff()
                && this.entity instanceof net.minecraft.world.entity.Entity value
                && value.level() instanceof net.minecraft.server.level.ServerLevel world) {
            com.axalotl.async.common.C2meThreadBoundary.run(world, () -> onMove(original));
            return;
        }""")

edit('gradle.properties', 'version=2.4.0-noxviola.1-vulkan-hybrid', 'version=2.4.8-noxviola.1-vulkan-hybrid')

# Three inherited CFR failures were executable throw stubs in mob sensor sorting.
# Freeze the observer once and each target on first comparison so movement cannot
# violate TimSort's ordering contract. Stable-world order is vanilla squared distance.
for name, generic in [('NearestLivingEntitySensorMixin', 'E'),
                      ('NearestItemSensorMixin', 'T'), ('PlayerSensorMixin', 'T')]:
    path = root / ('common/src/main/java/com/axalotl/async/common/mixin/entity/sensor/' + name + '.java')
    text = path.read_text()
    if text.count('throw new IllegalStateException("Decompilation failed");') != 1:
        raise SystemExit('sensor source drift: ' + name)
    start = text.index('        IdentityHashMap cache = new IdentityHashMap();')
    end = text.index('        };', start) + len('        };')
    replacement = '''        IdentityHashMap<GENERIC, Double> distances = new IdentityHashMap<>();
        return Comparator.comparingDouble(target -> distances.computeIfAbsent(target, value -> {
            double dx = ex - value.getX();
            double dy = ey - value.getY();
            double dz = ez - value.getZ();
            return dx * dx + dy * dy + dz * dz;
        }));'''.replace('GENERIC', generic)
    text = text[:start] + replacement + text[end:]
    # Remove the unused helper emitted next to the failed decompilation.
    text = text[:text.index('    private static /* synthetic */ double[] lambda$async$safeComparator$1')] + '}\n'
    path.write_text(text)

graph = root / 'forge/src/main/java/net/vulkanmod/render/chunk/graph/SectionGraph.java'
if graph.read_text().count('if (renderSection.directionChanges > maxDirectionsChanges)') != 1:
    raise SystemExit('section graph source drift before complete-traversal repair')
edit('forge/src/main/resources/assets/vulkanmod/lang/en_us.json',
     'Use a culling algorithm that might improve performance by reducing the number of non visible chunk sections rendered.',
     'Prioritize simpler visibility paths while terrain loads. Every reachable visible section is rendered, including in aggressive mode.')

# Renderer.bindGraphicsPipeline already emits blend/stencil dynamic-state commands.
# Every bound graphics pipeline must declare those states; Aether/Midnight menus
# exposed VUID-vkCmdDrawIndexed-None-08608 before world entry under validation.
edit('forge/src/main/java/net/vulkanmod/vulkan/shader/GraphicsPipeline.java',
     'dynamicStates.pDynamicStates(stack.ints(VK_DYNAMIC_STATE_DEPTH_BIAS,',
     'dynamicStates.pDynamicStates(stack.ints(VK_DYNAMIC_STATE_BLEND_CONSTANTS, '
     'VK_DYNAMIC_STATE_STENCIL_COMPARE_MASK, VK_DYNAMIC_STATE_STENCIL_WRITE_MASK, '
     'VK_DYNAMIC_STATE_STENCIL_REFERENCE, VK_DYNAMIC_STATE_DEPTH_BIAS,', 2)

# Imported GL20/ARB shader boundaries dropped all but the first source string,
# and discarded pointer-based sources entirely. Preserve bytes and exact query values.
for family, handle in [('GL20M', 'shader'), ('ARBShaderObjectsM', 'vulkanmod$coreShader(shaderObj)')]:
    relative = 'forge/src/main/java/net/vulkanmod/mixin/compatibility/gl/' + family + '.java'
    edit(relative, 'strings != null && strings.length > 0 ? strings[0] : ""',
         'net.vulkanmod.gl.GlShaderSource.concatenate(strings)')
    edit(relative, 'GlProgram.shaderSource(' + handle + ', "");',
         'GlProgram.shaderSource(' + handle + ', net.vulkanmod.gl.GlShaderSource.concatenate(strings, length));', 2)
relative = 'forge/src/main/java/net/vulkanmod/mixin/compatibility/gl/GL20M.java'
path = root / relative
text = path.read_text()
assert text.count('params.put(params.position(), GL20.GL_TRUE);') == 2
text = text.replace('params.put(params.position(), GL20.GL_TRUE);',
                    'params.put(params.position(), GlProgram.getShaderi(shader, pname));', 1)
text = text.replace('params.put(params.position(), GL20.GL_TRUE);',
                    'params.put(params.position(), GlProgram.getProgrami(program, pname));', 1)
path.write_text(text)

# Vanilla stopAll queues stops after restarting the sound executor, then clears
# channels on the render thread. Serialize the original clear before context cleanup.
forge_mixins = root / 'forge/src/main/resources/harimt.forge.mixins.json'
forge_config = json.loads(forge_mixins.read_text())
assert 'client.ChannelAccessMixin' not in forge_config['client']
forge_config['client'].append('client.ChannelAccessMixin')
forge_mixins.write_text(json.dumps(forge_config, indent=2) + '\n')

for src in payload.rglob('*'):
    if src.is_file():
        dst=root/src.relative_to(payload); dst.parent.mkdir(parents=True,exist_ok=True); shutil.copy2(src,dst)
for source_root in ['common/src/main/java', 'forge/src/main/java', 'fabric/src/main/java']:
    for path in (root/source_root).rglob('*.java'):
        if 'Decompilation failed' in path.read_text() or 'This method has failed to decompile' in path.read_text():
            raise SystemExit('Unrepaired decompiler failure: ' + str(path.relative_to(root)))
from apply_swapchain_sync import apply as apply_swapchain_sync
apply_swapchain_sync(root)
from apply_modpack_render_integrity import apply as apply_modpack_render_integrity
apply_modpack_render_integrity(root)
from build_gl_contract import generate
generate(root)
print('Hari indexed scheduling, shared CPU budgets, wakeup repair and upstream sort-state fix applied')
