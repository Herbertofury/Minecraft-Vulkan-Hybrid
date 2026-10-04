import java.lang.instrument.Instrumentation;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import com.sun.tools.attach.VirtualMachine;

/** QA observer: reads loaded class flags and chunk bookkeeping, never alters game state. */
public final class HariRuntimeSnapshot {
    private static volatile java.lang.ref.WeakReference<Object> observedServer = new java.lang.ref.WeakReference<>(null);
    public static void main(String[] args) throws Exception {
        VirtualMachine vm = VirtualMachine.attach(args[0]);
        try { vm.loadAgent(args[1], args[2]); }
        finally { vm.detach(); }
    }

    public static void agentmain(String output, Instrumentation instrumentation) throws Exception {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("observed_at_epoch_ms", System.currentTimeMillis());
        Class<?> minecraft = null, cache = null;
        for (Class<?> type : instrumentation.getAllLoadedClasses()) {
            if (type.getName().equals("net.minecraft.client.Minecraft")) minecraft = type;
            if (type.getName().equals("me.jellysquid.mods.sodium.client.world.cloned.ClonedChunkSectionCache")) cache = type;
        }
        report.put("rubidium_cache_loaded", cache != null);
        if (cache != null) {
            Map<String, Object> flags = new LinkedHashMap<>();
            for (Method method : cache.getDeclaredMethods()) {
                if (Set.of("cleanup", "acquire", "invalidate").contains(method.getName()))
                    flags.put(method.getName(), Modifier.isSynchronized(method.getModifiers()));
            }
            report.put("rubidium_mutation_monitors", flags);
        }
        try {
            if (minecraft == null) throw new IllegalStateException("Minecraft class not loaded");
            Object client = minecraft.getDeclaredMethod("m_91087_").invoke(null);
            Object server = fieldOfType(client, "net.minecraft.client.server.IntegratedServer");
            if (server != null) observedServer = new java.lang.ref.WeakReference<>(server);
            else server = observedServer.get();
            report.put("integrated_server_present", server != null);
            if (server != null) {
                Map<String, Object> clocks = new LinkedHashMap<>();
                for (Field field : fields(server.getClass())) {
                    if (!Modifier.isStatic(field.getModifiers()) && field.getType().isPrimitive()) {
                        field.setAccessible(true);
                        Object value = field.get(server);
                        if (value instanceof Number || value instanceof Boolean) clocks.put(field.getName(), value);
                    }
                }
                report.put("server_scalar_fields", clocks);
                List<Object> worlds = new ArrayList<>();
                for (Field field : fields(server.getClass())) {
                    if (!Map.class.isAssignableFrom(field.getType()) || Modifier.isStatic(field.getModifiers())) continue;
                    field.setAccessible(true);
                    Object value = field.get(server);
                    if (!(value instanceof Map<?, ?> map)) continue;
                    for (Object level : map.values()) {
                        if (level == null || !level.getClass().getName().equals("net.minecraft.server.level.ServerLevel")) continue;
                        Object chunks = fieldOfType(level, "net.minecraft.server.level.ServerChunkCache");
                        Object chunkMap = fieldOfType(chunks, "net.minecraft.server.level.ChunkMap");
                        Map<String, Object> world = new LinkedHashMap<>();
                        world.put("level_key", level.getClass().getMethod("m_46472_").invoke(level).toString());
                        world.put("chunk_map", summarize(chunkMap, 0, new IdentityHashMap<>()));
                        worlds.add(world);
                    }
                }
                report.put("worlds", worlds);
            }
        } catch (Exception failure) {
            report.put("world_snapshot_error", failure.toString());
        }
        Files.writeString(Path.of(output), json(report) + "\n");
    }

    private static List<Field> fields(Class<?> type) {
        List<Field> result = new ArrayList<>();
        for (; type != null && type != Object.class; type = type.getSuperclass())
            result.addAll(Arrays.asList(type.getDeclaredFields()));
        return result;
    }

    private static Object fieldOfType(Object owner, String name) throws Exception {
        if (owner == null) return null;
        for (Field field : fields(owner.getClass())) {
            if (field.getType().getName().equals(name) && !Modifier.isStatic(field.getModifiers())) {
                field.setAccessible(true);
                return field.get(owner);
            }
        }
        return null;
    }

    private static Object summarize(Object owner, int depth, IdentityHashMap<Object, Boolean> seen) throws Exception {
        if (owner == null || depth > 4 || seen.put(owner, true) != null) return null;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("class", owner.getClass().getName());
        for (Field field : fields(owner.getClass())) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            field.setAccessible(true);
            Object value = field.get(owner);
            if (value == null) continue;
            String key = field.getName();
            if (value instanceof Map<?, ?> map) {
                Map<String, Object> counts = new LinkedHashMap<>();
                counts.put("size", map.size());
                Map<String, Integer> pending = new TreeMap<>(), tickets = new TreeMap<>();
                for (Object entry : new ArrayList<>(map.values())) {
                    if (entry != null && entry.getClass().getName().equals("net.minecraft.server.level.ChunkHolder")) {
                        for (Field futureField : fields(entry.getClass())) {
                            if (CompletableFuture.class.isAssignableFrom(futureField.getType())) {
                                futureField.setAccessible(true);
                                CompletableFuture<?> future = (CompletableFuture<?>) futureField.get(entry);
                                if (future != null && !future.isDone()) pending.merge(futureField.getName(), 1, Integer::sum);
                            } else if (java.util.concurrent.atomic.AtomicReferenceArray.class.isAssignableFrom(futureField.getType())) {
                                futureField.setAccessible(true);
                                java.util.concurrent.atomic.AtomicReferenceArray<?> futures = (java.util.concurrent.atomic.AtomicReferenceArray<?>) futureField.get(entry);
                                for (int i = 0; futures != null && i < futures.length(); i++) {
                                    Object future = futures.get(i);
                                    if (future instanceof CompletableFuture<?> task && !task.isDone())
                                        pending.merge(futureField.getName() + "[" + i + "]", 1, Integer::sum);
                                }
                            }
                        }
                    }
                    if (entry instanceof Iterable<?> iterable) for (Object ticket : iterable) {
                        if (ticket != null && ticket.getClass().getName().equals("net.minecraft.server.level.Ticket")) {
                            Object type = ticket.getClass().getMethod("m_9428_").invoke(ticket);
                            tickets.merge(type.toString(), 1, Integer::sum);
                        }
                    }
                }
                if (!pending.isEmpty()) counts.put("pending_holder_futures", pending);
                if (!tickets.isEmpty()) counts.put("ticket_types", tickets);
                result.put(key, counts);
            } else if (value instanceof Collection<?> collection) {
                result.put(key, Map.of("size", collection.size()));
            } else if (value instanceof Boolean || value instanceof Number) {
                result.put(key, value);
            } else {
                String name = value.getClass().getName();
                if (name.startsWith("com.ishland.c2me.notickvd.") || name.contains("DistanceManager")
                        || name.contains("ThreadedLevelLightEngine") || name.contains("ChunkTaskPriorityQueueSorter"))
                    result.put(key, summarize(value, depth + 1, seen));
            }
        }
        return result;
    }

    private static String json(Object value) {
        if (value == null) return "null";
        if (value instanceof Boolean || value instanceof Number) return value.toString();
        if (value instanceof Map<?, ?> map) {
            List<String> parts = new ArrayList<>();
            for (var entry : map.entrySet()) parts.add(json(entry.getKey().toString()) + ":" + json(entry.getValue()));
            return "{" + String.join(",", parts) + "}";
        }
        if (value instanceof Iterable<?> list) {
            List<String> parts = new ArrayList<>();
            for (Object entry : list) parts.add(json(entry));
            return "[" + String.join(",", parts) + "]";
        }
        return "\"" + value.toString().replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + "\"";
    }
}
