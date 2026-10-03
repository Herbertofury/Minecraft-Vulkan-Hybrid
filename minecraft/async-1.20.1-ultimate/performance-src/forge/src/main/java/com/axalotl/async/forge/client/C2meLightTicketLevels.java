package com.axalotl.async.forge.client;

import java.util.ArrayList;
import java.util.List;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/**
 * Keep LIGHT ticket add/remove levels paired after another transformer renumbers
 * ChunkMap's release lambda. Uses C2ME's actual installed level computation.
 */
public final class C2meLightTicketLevels {
    private static final String STATUS = "net/minecraft/world/level/chunk/ChunkStatus";
    private static final String LEVEL = "net/minecraft/server/level/ChunkLevel";
    private static final String TYPE = "net/minecraft/server/level/TicketType";
    private static final String POSITION = "net/minecraft/world/level/ChunkPos";
    private static final String LEVEL_DESC = "(L" + STATUS + ";)I";
    private static final String REMOVE_DESC = "(L" + TYPE + ";L" + POSITION + ";ILjava/lang/Object;)V";

    private C2meLightTicketLevels() {}

    public static boolean apply(ClassNode target) {
        if (!target.name.equals("net/minecraft/server/level/ChunkMap"))
            throw new IllegalStateException("Unexpected C2ME light-ticket target: " + target.name);
        List<MethodNode> providers = target.methods.stream()
                .filter(m -> m.name.endsWith("$redirectAddLightTicketDistance"))
                .toList();
        // C2ME's worldgen module may be disabled. In that case the original
        // vanilla addition/removal pair requires no adjustment.
        if (providers.isEmpty()) return false;
        if (providers.size() != 1) throw drift("multiple addition-level helpers");
        MethodNode provider = providers.get(0);
        if (!provider.desc.equals(LEVEL_DESC) || (provider.access & Opcodes.ACC_STATIC) != 0)
            throw drift("addition-level helper signature");

        List<MethodNode> releases = target.methods.stream()
                .filter(m -> m.name.startsWith("lambda$releaseLightTicket$"))
                .filter(m -> m.desc.equals("(L" + POSITION + ";)V"))
                .toList();
        if (releases.size() != 1) throw drift("original LIGHT release lambda");
        MethodNode release = releases.get(0);
        if ((release.access & Opcodes.ACC_STATIC) != 0) throw drift("static release lambda");
        boolean lightType = false;
        int removals = 0;
        List<MethodInsnNode> levels = new ArrayList<>();
        for (AbstractInsnNode instruction : release.instructions) {
            if (instruction instanceof FieldInsnNode field && field.getOpcode() == Opcodes.GETSTATIC
                    && field.owner.equals(TYPE) && (field.name.equals("LIGHT") || field.name.equals("f_9446_")))
                lightType = true;
            if (instruction instanceof MethodInsnNode call) {
                if ((call.owner.equals("net/minecraft/server/level/DistanceManager")
                        || call.owner.equals(target.name + "$DistanceManager"))
                        && (call.name.equals("removeTicket") || call.name.equals("m_140823_"))
                        && call.desc.equals(REMOVE_DESC) && call.getOpcode() == Opcodes.INVOKEVIRTUAL)
                    removals++;
                if (call.desc.equals(LEVEL_DESC) && (
                        call.owner.equals(LEVEL) && (call.name.equals("byStatus") || call.name.equals("m_287141_"))
                        || call.owner.equals(target.name) && (
                                call.name.equals(provider.name)
                                || call.name.endsWith("$redirectRemoveLightTicketDistance"))))
                    levels.add(call);
            }
        }
        if (!lightType || removals != 1 || levels.size() != 1)
            throw drift("LIGHT type, original removal or level computation");
        MethodInsnNode level = levels.get(0);
        if (level.owner.equals(target.name) && level.name.equals(provider.name)) return false;
        AbstractInsnNode status = previousCode(level);
        if (level.getOpcode() == Opcodes.INVOKESTATIC) {
            if (!(status instanceof FieldInsnNode field) || field.getOpcode() != Opcodes.GETSTATIC
                    || !field.owner.equals(STATUS)
                    || !(field.name.equals("LIGHT") || field.name.equals("f_62323_")))
                throw drift("original release LIGHT status");
            // Existing stack is [distanceManager, LIGHT, position, status].
            // Supply the same ChunkMap receiver used by C2ME's addition helper.
            InsnList receiver = new InsnList();
            receiver.add(new VarInsnNode(Opcodes.ALOAD, 0));
            receiver.add(new InsnNode(Opcodes.SWAP));
            release.instructions.insertBefore(level, receiver);
            release.maxStack++;
        } else if (level.getOpcode() != Opcodes.INVOKESPECIAL) {
            throw drift("existing removal-level invocation");
        }
        release.instructions.set(level, new MethodInsnNode(Opcodes.INVOKESPECIAL,
                target.name, provider.name, LEVEL_DESC, false));
        System.out.println("[Hari/Compat] LIGHT release uses the original C2ME addition-level helper");
        return true;
    }

    private static AbstractInsnNode previousCode(AbstractInsnNode instruction) {
        AbstractInsnNode previous = instruction.getPrevious();
        while (previous != null && previous.getOpcode() < 0) previous = previous.getPrevious();
        return previous;
    }

    private static IllegalStateException drift(String detail) {
        return new IllegalStateException("C2ME LIGHT ticket source drift: " + detail);
    }
}
