#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit('usage: apply_source_pairs_fastpath_exact_verifier.py <source-pairs-fastpath-candidate-root>')
root = Path(sys.argv[1]).resolve()
p = root / 'common/src/main/java/com/axalotl/async/common/commands/GpuCommand.java'
if not p.is_file():
    raise SystemExit(f'missing {p}')
s = p.read_text(encoding='utf-8')


def once(old, new, label):
    global s
    n = s.count(old)
    if n != 1:
        raise SystemExit(f'{label}: expected 1 marker, found {n}')
    s = s.replace(old, new, 1)


if 'Commands.literal("source-test")' in s or 'HMT_SOURCE_PAIRS_VERIFY PASS' in s:
    raise SystemExit('source-pairs exact verifier already present; refusing reapplication')

once('''                .then(Commands.literal("test")
                        .requires(Permission.require("command.gpu", 2))
                        .executes(ctx -> { runLiveVerification(ctx.getSource()); return 1; }))
                .then(Commands.literal("toggle")
''', '''                .then(Commands.literal("test")
                        .requires(Permission.require("command.gpu", 2))
                        .executes(ctx -> { runLiveVerification(ctx.getSource()); return 1; }))
                .then(Commands.literal("source-test")
                        .requires(Permission.require("command.gpu", 2))
                        .executes(ctx -> { runSourcePairVerification(ctx.getSource()); return 1; }))
                .then(Commands.literal("toggle")
''', 'command registration')

insert = '''
    /** Exact diagnostic for the source-restricted deferred-push broad phase. */
    private static void runSourcePairVerification(CommandSourceStack source) {
        if (!GpuEntityModule.isGpuAvailable()) {
            source.sendFailure(AsyncCommand.prefix.copy().append(
                    Component.literal("Vulkan backend is not operational; vanilla fallback remains active.")
                            .withStyle(ChatFormatting.RED)));
            return;
        }

        ServerLevel level = source.getLevel();
        List<Entity> population = new ArrayList<>();
        List<Entity> sources = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (entity == null || entity.isRemoved() || !entity.isAlive()) continue;
            boolean isSource = entity.getTags().contains("harimt_perf");
            boolean isLocalNoise = entity.getTags().contains("harimt_local_noise");
            if (!isSource && !isLocalNoise) continue;
            population.add(entity);
            if (isSource) sources.add(entity);
        }
        if (sources.size() != 256 || population.size() != 768) {
            System.out.println("HMT_SOURCE_PAIRS_VERIFY FAIL population=" + population.size()
                    + " sources=" + sources.size() + " expectedPopulation=768 expectedSources=256");
            source.sendFailure(AsyncCommand.prefix.copy().append(
                    Component.literal("Source-pairs fixture mismatch: population=" + population.size()
                            + " sources=" + sources.size()).withStyle(ChatFormatting.RED)));
            return;
        }

        IdentityHashMap<Entity, Integer> indexes = new IdentityHashMap<>();
        IdentityHashMap<Entity, Boolean> sourceSet = new IdentityHashMap<>();
        for (int i = 0; i < population.size(); i++) indexes.put(population.get(i), i);
        for (Entity entity : sources) sourceSet.put(entity, Boolean.TRUE);

        Set<Long> exact = new HashSet<>();
        for (int i = 0; i < population.size(); i++) {
            Entity a = population.get(i);
            for (int j = i + 1; j < population.size(); j++) {
                Entity b = population.get(j);
                if (!sourceSet.containsKey(a) && !sourceSet.containsKey(b)) continue;
                if (a.getBoundingBox().intersects(b.getBoundingBox())) exact.add(key(i, j));
            }
        }

        GpuCollisionDispatcher d = GpuEntityModule.getCollisionDispatcher();
        Optional<List<GpuCollisionDispatcher.CollisionPair>> result =
                d.computeGpuOnlyForSources(population, sourceSet);
        if (result.isEmpty()) {
            System.out.println("HMT_SOURCE_PAIRS_VERIFY FAIL noCompleteGpuResult population="
                    + population.size() + " sources=" + sources.size());
            source.sendFailure(AsyncCommand.prefix.copy().append(
                    Component.literal("Source-pairs GPU verifier fell back to vanilla.")
                            .withStyle(ChatFormatting.RED)));
            return;
        }

        Set<Long> gpu = new HashSet<>();
        for (GpuCollisionDispatcher.CollisionPair pair : result.get()) {
            Integer ia = indexes.get(pair.a());
            Integer ib = indexes.get(pair.b());
            if (ia == null || ib == null || ia.equals(ib)) continue;
            gpu.add(key(Math.min(ia, ib), Math.max(ia, ib)));
        }
        Set<Long> missing = new HashSet<>(exact);
        missing.removeAll(gpu);
        Set<Long> extras = new HashSet<>(gpu);
        extras.removeAll(exact);
        if (!missing.isEmpty()) {
            AsyncConfig.enableGpuCollision.setValue(false);
            PlatformUtils.saveConfig();
            System.out.println("HMT_SOURCE_PAIRS_VERIFY FAIL missing=" + missing.size()
                    + " exact=" + exact.size() + " gpu=" + gpu.size()
                    + " extras=" + extras.size() + " population=" + population.size()
                    + " sources=" + sources.size());
            source.sendFailure(AsyncCommand.prefix.copy().append(
                    Component.literal("SOURCE-PAIRS GPU VERIFICATION FAILED: " + missing.size()
                            + " exact source overlap(s) missing; GPU disabled.")
                            .withStyle(ChatFormatting.RED)));
            return;
        }
        System.out.println("HMT_SOURCE_PAIRS_VERIFY PASS exact=" + exact.size()
                + " gpu=" + gpu.size() + " extras=" + extras.size()
                + " population=" + population.size() + " sources=" + sources.size()
                + " dispatchMs=" + d.getLastDispatchMillis());
        source.sendSuccess(() -> AsyncCommand.prefix.copy().append(
                Component.literal("Source-pairs exact verification PASS")
                        .withStyle(ChatFormatting.GREEN)), false);
    }

'''
marker = '''    private static Set<Long> exactPairs(List<Entity> entities) {
'''
if s.count(marker) != 1:
    raise SystemExit('exactPairs insertion marker drift')
s = s.replace(marker, insert + marker, 1)
for m in (
    'Commands.literal("source-test")',
    'computeGpuOnlyForSources(population, sourceSet)',
    'HMT_SOURCE_PAIRS_VERIFY PASS',
    'expectedPopulation=768 expectedSources=256',
):
    if s.count(m) != 1:
        raise SystemExit(f'verifier invariant failed: {m} count={s.count(m)}')
p.write_text(s, encoding='utf-8')
print('source-pairs fastpath exact verifier instrumentation applied')
