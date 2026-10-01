#!/usr/bin/env python3
"""Result-preserving scheduler/upload improvements after the integrity layer."""
from pathlib import Path
import shutil, sys

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

edit('gradle.properties', 'version=2.4.0-noxviola.1-vulkan-hybrid', 'version=2.4.1-noxviola.1-vulkan-hybrid')

for src in payload.rglob('*'):
    if src.is_file():
        dst=root/src.relative_to(payload); dst.parent.mkdir(parents=True,exist_ok=True); shutil.copy2(src,dst)
print('Hari indexed scheduling, shared CPU budgets, wakeup repair and upstream sort-state fix applied')
