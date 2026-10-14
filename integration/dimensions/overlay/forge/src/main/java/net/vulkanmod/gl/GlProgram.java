package net.vulkanmod.gl;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceOpenHashMap;
import org.lwjgl.opengl.GL20;
import net.vulkanmod.vulkan.shader.NativeUiProgram;
import java.nio.ByteBuffer;

import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class GlProgram {
    // Core and ARB shader APIs address the same shader/program object namespace.
    private static int nextObjectId = 1;
    private static int nextUniformLocation = 1;

    private static final Int2ReferenceOpenHashMap<Shader> SHADERS = new Int2ReferenceOpenHashMap<>();
    private static final Int2ReferenceOpenHashMap<Program> PROGRAMS = new Int2ReferenceOpenHashMap<>();
    private static final Int2ObjectOpenHashMap<Location> UNIFORM_NAMES_BY_LOCATION = new Int2ObjectOpenHashMap<>();

    private static int currentProgramId;
    private static java.util.function.Consumer<NativeUiProgram> nativeRetirer = ignored -> {};

    public static void setNativeProgramRetirer(java.util.function.Consumer<NativeUiProgram> retire) {
        nativeRetirer = java.util.Objects.requireNonNull(retire);
    }

    private GlProgram() {
    }

    public static int createShader(int type) {
        int id = allocateObjectId();
        SHADERS.put(id, new Shader(type));
        return id;
    }

    public static void shaderSource(int shader, CharSequence source) {
        Shader shaderObj = SHADERS.get(shader);
        if (shaderObj != null) {
            shaderObj.source = source != null ? source.toString() : "";
        }
    }

    public static void compileShader(int shader) {
        Shader shaderObj = SHADERS.get(shader);
        if (shaderObj == null) return;
        shaderObj.compiled = false; shaderObj.log = "";
        try {
            if (shaderObj.type != GL20.GL_VERTEX_SHADER && shaderObj.type != GL20.GL_FRAGMENT_SHADER)
                throw new IllegalArgumentException("Native UI frontend supports vertex/fragment stages; this stage needs another native backend");
            NativeUiProgram.validateStage("gl-shader-" + shader, shaderObj.source, shaderObj.type == GL20.GL_VERTEX_SHADER);
            shaderObj.compiledSource = shaderObj.source;
            shaderObj.compiled = true;
        } catch (RuntimeException failure) { shaderObj.log = failure.getMessage(); }
    }

    public static int getShaderi(int shader, int pname) {
        Shader value = SHADERS.get(shader); if (value == null) return 0;
        return switch (pname) {
            case GL20.GL_COMPILE_STATUS -> value.compiled ? GL20.GL_TRUE : GL20.GL_FALSE;
            case GL20.GL_SHADER_TYPE -> value.type;
            case GL20.GL_SHADER_SOURCE_LENGTH -> value.source.length() + 1;
            case GL20.GL_INFO_LOG_LENGTH -> value.log.isEmpty() ? 0 : value.log.length() + 1;
            case GL20.GL_DELETE_STATUS -> value.deleted ? GL20.GL_TRUE : GL20.GL_FALSE;
            default -> 0;
        };
    }

    public static String getShaderInfoLog(int shader) {
        Shader value = SHADERS.get(shader); return value == null ? "" : value.log;
    }

    public static String getShaderSource(int shader) {
        Shader value = SHADERS.get(shader); return value == null ? "" : value.source;
    }

    public static boolean isShader(int shader) { return SHADERS.containsKey(shader); }

    private static int allocateObjectId() {
        int id = nextObjectId;
        nextObjectId = Math.incrementExact(nextObjectId);
        return id;
    }

    public static void deleteShader(int shader) {
        Shader value = SHADERS.get(shader); if (value == null) return;
        value.deleted = true;
        if (PROGRAMS.values().stream().noneMatch(p -> p.attachedShaders.containsKey(shader))) SHADERS.remove(shader);
    }

    public static int createProgram() {
        int id = allocateObjectId();
        PROGRAMS.put(id, new Program());
        return id;
    }

    public static void attachShader(int program, int shader) {
        Program programObj = PROGRAMS.get(program);
        if (programObj != null) {
            Shader value = SHADERS.get(shader);
            if (value == null) throw new IllegalArgumentException("Unknown shader " + shader);
            programObj.attachedShaders.put(shader, value);
        }
    }

    public static void bindAttribLocation(int program, int index, CharSequence name) {
        Program programObj = PROGRAMS.get(program);
        if (programObj != null && name != null) {
            programObj.attributeLocations.put(name.toString(), index);
        }
    }

    public static void linkProgram(int program) {
        Program programObj = PROGRAMS.get(program);
        if (programObj == null) return;
        programObj.linked = false; programObj.validated = false; programObj.log = "";
        try {
            Shader vertex = null, fragment = null;
            for (Shader stage : programObj.attachedShaders.values()) {
                if (!stage.compiled) throw new IllegalArgumentException("Attached shader is not compiled");
                if (stage.type == GL20.GL_VERTEX_SHADER && vertex == null) vertex = stage;
                else if (stage.type == GL20.GL_FRAGMENT_SHADER && fragment == null) fragment = stage;
                else throw new IllegalArgumentException("Duplicate/unsupported shader stage");
            }
            if (vertex == null || fragment == null) throw new IllegalArgumentException("A compiled vertex and fragment pair is required");
            NativeUiProgram next = NativeUiProgram.compile("gl-program-" + program, vertex.compiledSource, fragment.compiledSource);
            if (programObj.nativeProgram != null) nativeRetirer.accept(programObj.nativeProgram);
            invalidateLocations(programObj); programObj.nativeProgram = next;
            programObj.linked = true;
        } catch (RuntimeException failure) { programObj.log = failure.getMessage(); }
    }

    public static int getProgrami(int program, int pname) {
        Program value = PROGRAMS.get(program); if (value == null) return 0;
        return switch (pname) {
            case GL20.GL_LINK_STATUS -> value.linked ? GL20.GL_TRUE : GL20.GL_FALSE;
            // A successful link is not validation of bound vertex/texture/framebuffer state.
            case GL20.GL_VALIDATE_STATUS -> value.validated ? GL20.GL_TRUE : GL20.GL_FALSE;
            case GL20.GL_ATTACHED_SHADERS -> value.attachedShaders.size();
            case GL20.GL_INFO_LOG_LENGTH -> value.log.isEmpty() ? 0 : value.log.length() + 1;
            case GL20.GL_ACTIVE_UNIFORMS -> value.nativeProgram == null ? 0 : value.nativeProgram.uniformNames().size() + value.nativeProgram.samplers().size();
            case GL20.GL_ACTIVE_ATTRIBUTES -> value.nativeProgram == null ? 0 : value.nativeProgram.attributes().size();
            case GL20.GL_DELETE_STATUS -> value.deleted ? GL20.GL_TRUE : GL20.GL_FALSE;
            case GL_ACTIVE_UNIFORM_BLOCKS -> value.nativeProgram == null || value.nativeProgram.uniformBytes() == 0 ? 0 : 1;
            default -> 0;
        };
    }

    private static final int GL_ACTIVE_UNIFORM_BLOCKS = 0x8A36;
    private static final int GL_ACTIVE_UNIFORM_BLOCK_MAX_NAME_LENGTH = 0x8A35;
    private static final int GL_PROGRAM_BINARY_LENGTH = 0x8741;

    public static String getProgramInfoLog(int program) {
        Program value = PROGRAMS.get(program); return value == null ? "" : value.log;
    }

    public static void useProgram(int program) {
        Program value = program == 0 ? null : PROGRAMS.get(program);
        if (program != 0 && (value == null || !value.linked)) throw new IllegalArgumentException("Unlinked native program " + program);
        int previous = currentProgramId;
        currentProgramId = program;
        Program old = PROGRAMS.get(previous);
        if (previous != program && old != null && old.deleted) destroyProgram(previous, old);
    }

    public static boolean isProgram(int program) {
        return PROGRAMS.containsKey(program);
    }

    public static void deleteProgram(int program) {
        Program value = PROGRAMS.get(program);
        if (value == null) return;
        value.deleted = true;
        // A current program's executable, attachments and uniforms survive deletion
        // until it is no longer current, just as attached deleted shaders do.
        if (currentProgramId != program) destroyProgram(program, value);
    }

    public static void deleteObject(int object) {
        if (isProgram(object)) deleteProgram(object);
        else if (isShader(object)) deleteShader(object);
    }

    private static void destroyProgram(int program, Program value) {
        PROGRAMS.remove(program);
        if (value != null && value.nativeProgram != null) nativeRetirer.accept(value.nativeProgram);
        if (value != null) { invalidateLocations(value); for (int shader : value.attachedShaders.keySet()) { Shader stage = SHADERS.get(shader); if (stage != null && stage.deleted && PROGRAMS.values().stream().noneMatch(p -> p.attachedShaders.containsKey(shader))) SHADERS.remove(shader); } }
    }

    public static int currentProgramId() {
        return currentProgramId;
    }

    public static int getAttribLocation(int program, CharSequence name) {
        Program programObj = PROGRAMS.get(program);
        if (programObj == null || name == null) {
            return -1;
        }

        if (programObj.nativeProgram == null) return -1;
        return programObj.nativeProgram.attributes().stream().filter(a -> a.name().equals(name.toString())).mapToInt(NativeUiProgram.Attribute::location).findFirst().orElse(-1);
    }

    public static int getUniformLocation(int program, CharSequence name) {
        Program programObj = PROGRAMS.get(program);
        if (programObj == null || name == null) {
            return -1;
        }

        if (programObj.nativeProgram == null || (!programObj.nativeProgram.uniformNames().contains(name.toString())
                && programObj.nativeProgram.samplers().stream().noneMatch(a -> a.name().equals(name.toString())))) return -1;
        return programObj.uniformLocations.computeIfAbsent(name.toString(), uniformName -> {
            int location = nextUniformLocation++;
            UNIFORM_NAMES_BY_LOCATION.put(location, new Location(program, uniformName));
            return location;
        });
    }

    public static void uniform1i(int location, int value) {
        UniformValue uniformValue = getOrCreateUniform(location, 1, 1);
        if (uniformValue != null) {
            uniformValue.floats[0] = value;
            uniformValue.ints[0] = value;
        }
    }

    public static void uniform1f(int location, float value) {
        UniformValue uniformValue = getOrCreateUniform(location, 1, 0);
        if (uniformValue != null) {
            uniformValue.floats[0] = value;
        }
    }

    public static void uniform2f(int location, float x, float y) {
        UniformValue uniformValue = getOrCreateUniform(location, 2, 0);
        if (uniformValue != null) {
            uniformValue.floats[0] = x;
            uniformValue.floats[1] = y;
        }
    }

    public static void uniform3f(int location, float x, float y, float z) {
        UniformValue uniformValue = getOrCreateUniform(location, 3, 0);
        if (uniformValue != null) {
            uniformValue.floats[0] = x;
            uniformValue.floats[1] = y;
            uniformValue.floats[2] = z;
        }
    }

    public static void uniform3i(int location, int x, int y, int z) {
        UniformValue uniformValue = getOrCreateUniform(location, 3, 3);
        if (uniformValue != null) {
            uniformValue.floats[0] = x;
            uniformValue.floats[1] = y;
            uniformValue.floats[2] = z;
            uniformValue.ints[0] = x;
            uniformValue.ints[1] = y;
            uniformValue.ints[2] = z;
        }
    }

    public static void uniform4f(int location, float x, float y, float z, float w) {
        UniformValue uniformValue = getOrCreateUniform(location, 4, 0);
        if (uniformValue != null) {
            uniformValue.floats[0] = x;
            uniformValue.floats[1] = y;
            uniformValue.floats[2] = z;
            uniformValue.floats[3] = w;
        }
    }

    public static void uniformMatrix4fv(int location, boolean transpose, FloatBuffer value) {
        if (value == null) {
            return;
        }

        UniformValue uniformValue = getOrCreateUniform(location, 16, 0);
        if (uniformValue == null) {
            return;
        }

        float[] matrix = uniformValue.floats;
        FloatBuffer src = value.duplicate();
        if (src.remaining() < 16) throw new IllegalArgumentException("A complete matrix is required");
        int start = src.position();
        for (int i = 0; i < matrix.length; i++) matrix[i] = src.get(start + (transpose ? (i % 4) * 4 + i / 4 : i));
    }

    public static float[] getFloatUniform(String name) {
        Program programObj = PROGRAMS.get(currentProgramId);
        if (programObj == null) {
            return null;
        }

        UniformValue value = programObj.uniformValues.get(name);
        return value != null ? value.floats : null;
    }

    public static int[] getIntUniform(String name) {
        Program programObj = PROGRAMS.get(currentProgramId);
        if (programObj == null) {
            return null;
        }

        UniformValue value = programObj.uniformValues.get(name);
        return value != null ? value.ints : null;
    }

    private static UniformValue getOrCreateUniform(int location, int floatCount, int intCount) {
        Location uniform = UNIFORM_NAMES_BY_LOCATION.get(location);
        Program programObj = PROGRAMS.get(currentProgramId);
        if (uniform == null || uniform.program != currentProgramId || programObj == null) {
            return null;
        }

        String name = uniform.name;
        UniformValue value = programObj.uniformValues.get(name);
        if (value == null || !hasShape(value, floatCount, intCount)) {
            value = new UniformValue(new float[floatCount], intCount > 0 ? new int[intCount] : null);
            programObj.uniformValues.put(name, value);
        }

        return value;
    }

    private static boolean hasShape(UniformValue value, int floatCount, int intCount) {
        return value.floats.length == floatCount
                && (intCount == 0 ? value.ints == null : value.ints != null && value.ints.length == intCount);
    }

    public static NativeUiProgram currentNativeProgram() {
        Program p = PROGRAMS.get(currentProgramId); return p == null ? null : p.nativeProgram;
    }
    public static NativeUiProgram nativeProgram(int program) {
        Program p = PROGRAMS.get(program); return p == null || !p.linked ? null : p.nativeProgram;
    }
    public static void nativeValidationResult(int program, boolean valid, String log) {
        Program p = PROGRAMS.get(program); if (p == null) return;
        p.validated = valid && p.linked && p.nativeProgram != null; p.log = log;
    }
    public static ByteBuffer currentNativeUniforms() {
        Program p = PROGRAMS.get(currentProgramId); if (p == null || p.nativeProgram == null) throw new IllegalStateException("No linked native program");
        ByteBuffer bytes = p.nativeProgram.allocateUniforms();
        for (String name : p.nativeProgram.uniformNames()) {
            UniformValue value = p.uniformValues.get(name); if (value == null) continue;
            if (value.ints != null) p.nativeProgram.writeInts(bytes, name, value.ints);
            else p.nativeProgram.writeFloats(bytes, name, value.floats);
        }
        return bytes;
    }
    public static void detachShader(int program, int shader) {
        Program p = PROGRAMS.get(program); if (p == null) return;
        Shader value = p.attachedShaders.remove(shader);
        if (value != null && value.deleted && PROGRAMS.values().stream().noneMatch(q -> q.attachedShaders.containsKey(shader))) SHADERS.remove(shader);
    }
    private static void invalidateLocations(Program p) {
        for (int location : p.uniformLocations.values()) UNIFORM_NAMES_BY_LOCATION.remove(location);
        p.uniformLocations.clear(); p.uniformValues.clear();
    }
    private record Location(int program, String name) {}

    private static final class Shader {
        final int type;
        String source = "";
        String compiledSource = "";
        boolean compiled, deleted;
        String log = "";

        Shader(int type) {
            this.type = type;
        }
    }

    private static final class Program {
        final Int2ReferenceOpenHashMap<Shader> attachedShaders = new Int2ReferenceOpenHashMap<>();
        final Map<String, Integer> attributeLocations = new HashMap<>();
        final Map<String, Integer> uniformLocations = new HashMap<>();
        final Map<String, UniformValue> uniformValues = new HashMap<>();
        boolean linked, validated, deleted;
        String log = "";
        NativeUiProgram nativeProgram;
    }

    private record UniformValue(float[] floats, int[] ints) {
    }
}
