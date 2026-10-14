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
            NativeUiProgram next = NativeUiProgram.compile("gl-program-" + program, vertex.compiledSource, fragment.compiledSource, programObj.attributeLocations, programObj.fragmentLocations);
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
            case GL20.GL_ACTIVE_UNIFORMS -> value.nativeProgram == null ? 0 : value.nativeProgram.uniforms().size();
            case GL20.GL_ACTIVE_UNIFORM_MAX_LENGTH -> value.nativeProgram == null ? 0 : value.nativeProgram.uniforms().stream().mapToInt(u->u.reflectedName().length()+1).max().orElse(0);
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

    public static void bindFragDataLocation(int program,int color,CharSequence name) {
        Program p=PROGRAMS.get(program);
        if(p==null) { NativeGlErrors.record(SHADERS.containsKey(program)?1282:1281); return; }
        if(color<0) { NativeGlErrors.record(1281); return; }
        if(name==null || name.toString().startsWith("gl_")) { NativeGlErrors.record(1282); return; }
        p.fragmentLocations.put(name.toString(),color);
    }
    public static NativeUiProgram.Uniform activeUniform(int program,int index) {
        Program p=PROGRAMS.get(program);
        if(p==null) { NativeGlErrors.record(SHADERS.containsKey(program)?1282:1281); return null; }
        if(p.nativeProgram==null || index<0 || index>=p.nativeProgram.uniforms().size()) { NativeGlErrors.record(1281); return null; }
        return p.nativeProgram.uniforms().get(index);
    }
    public static int uniformType(NativeUiProgram.Uniform u) {
        return switch(u.type()) {
            case "float" -> 0x1406; case "int" -> 0x1404;
            case "vec2" -> 0x8B50; case "vec3" -> 0x8B51; case "vec4" -> 0x8B52;
            case "ivec2" -> 0x8B53; case "ivec3" -> 0x8B54; case "ivec4" -> 0x8B55;
            case "mat2" -> 0x8B5A; case "mat3" -> 0x8B5B; case "mat4" -> 0x8B5C;
            case "sampler2D" -> 0x8B5E; default -> throw new IllegalArgumentException("Unimplemented native uniform type");
        };
    }
    public static int getUniformLocation(int program,CharSequence name) {
        Program p=PROGRAMS.get(program);
        if(p==null) { NativeGlErrors.record(SHADERS.containsKey(program)?1282:1281); return -1; }
        if(!p.linked || p.nativeProgram==null) { NativeGlErrors.record(1282); return -1; }
        if(name==null)return -1;
        String base=name.toString();int element=0;boolean subscript=false;int bracket=base.indexOf('[');
        if(bracket>=0) {
            if(!base.endsWith("]"))return -1;
            String index=base.substring(bracket+1,base.length()-1);
            if(index.isEmpty() || !index.chars().allMatch(Character::isDigit))return -1;
            try { element=Integer.parseInt(index); } catch(NumberFormatException invalid) { return -1; }
            base=base.substring(0,bracket);subscript=true;
        }
        NativeUiProgram.Uniform u=p.nativeProgram.uniform(base);
        if(u==null || subscript && !u.array() || element>=u.size())return -1;
        Integer first=p.uniformLocations.get(base);
        if(first==null) {
            first=nextUniformLocation;nextUniformLocation=Math.addExact(nextUniformLocation,u.size());p.uniformLocations.put(base,first);p.locationRanges.put(first,u);
        }
        int location=Math.addExact(first,element);
        UNIFORM_NAMES_BY_LOCATION.put(location,new Location(program,base,element,u));p.locationIds.add(location);
        return location;
    }
    private static Location writeLocation(int location,int rows,int columns,boolean floating,int count) {
        if(currentProgramId==0) { NativeGlErrors.record(1282); return null; }
        if(location==-1)return null;
        Location l=UNIFORM_NAMES_BY_LOCATION.get(location);
        if(l==null) {
            Program p=PROGRAMS.get(currentProgramId);var range=p.locationRanges.floorEntry(location);
            if(range!=null && location-range.getKey()<range.getValue().size()) {
                var u=range.getValue();l=new Location(currentProgramId,u.name(),location-range.getKey(),u);UNIFORM_NAMES_BY_LOCATION.put(location,l);p.locationIds.add(location);
            }
        }
        if(l==null || l.program!=currentProgramId || l.uniform.rows()!=rows || l.uniform.columns()!=columns || l.uniform.floating()!=floating || !l.uniform.array() && count>1) {
            NativeGlErrors.record(1282);return null;
        }
        return l;
    }
    private static UniformValue uniformStorage(Location l) {
        Program p=PROGRAMS.get(currentProgramId);UniformValue value=p.uniformValues.get(l.name);
        if(value==null) {
            int components=l.uniform.ordinal()<0?1:p.nativeProgram.storageComponents(l.name);
            value=new UniformValue(new float[components],l.uniform.floating()?null:new int[components]);p.uniformValues.put(l.name,value);
        }
        return value;
    }
    private static void scalar(int location,int rows,float x,float y,float z,float w) {
        Location l=writeLocation(location,rows,1,true,1);if(l==null)return;
        float[] a=uniformStorage(l).floats;int at=l.element*rows;a[at]=x;if(rows>1)a[at+1]=y;if(rows>2)a[at+2]=z;if(rows>3)a[at+3]=w;
    }
    public static void uniform1f(int location,float value) { scalar(location,1,value,0,0,0); }
    public static void uniform2f(int location,float x,float y) { scalar(location,2,x,y,0,0); }
    public static void uniform3f(int location,float x,float y,float z) { scalar(location,3,x,y,z,0); }
    public static void uniform4f(int location,float x,float y,float z,float w) { scalar(location,4,x,y,z,w); }
    public static void uniform1i(int location,int value) {
        Location l=writeLocation(location,1,1,false,1);if(l==null)return;UniformValue u=uniformStorage(l);u.ints[l.element]=value;u.floats[l.element]=value;
    }
    public static void uniform3i(int location,int x,int y,int z) {
        Location l=writeLocation(location,3,1,false,1);if(l==null)return;UniformValue u=uniformStorage(l);int at=l.element*3;u.ints[at]=x;u.ints[at+1]=y;u.ints[at+2]=z;u.floats[at]=x;u.floats[at+1]=y;u.floats[at+2]=z;
    }
    public static void uniformFloats(int location,int rows,FloatBuffer data) {
        java.util.Objects.requireNonNull(data);int count=data.remaining()/rows;Location l=writeLocation(location,rows,1,true,count);if(l==null || count==0)return;
        float[] out=uniformStorage(l).floats;int used=Math.min(count,l.uniform.size()-l.element)*rows,start=data.position(),at=l.element*rows;
        for(int i=0;i<used;i++)out[at+i]=data.get(start+i);
    }
    public static void uniformFloats(int location,int rows,float[] data) {
        java.util.Objects.requireNonNull(data);int count=data.length/rows;Location l=writeLocation(location,rows,1,true,count);if(l==null || count==0)return;
        int used=Math.min(count,l.uniform.size()-l.element)*rows;System.arraycopy(data,0,uniformStorage(l).floats,l.element*rows,used);
    }
    public static void uniformMatrix(int location,int size,boolean transpose,FloatBuffer data) {
        java.util.Objects.requireNonNull(data);int components=size*size,count=data.remaining()/components;Location l=writeLocation(location,size,size,true,count);if(l==null || count==0)return;
        float[] out=uniformStorage(l).floats;int used=Math.min(count,l.uniform.size()-l.element),start=data.position(),at=l.element*components;
        for(int a=0;a<used;a++)for(int c=0;c<size;c++)for(int r=0;r<size;r++)
            out[at+a*components+c*size+r]=data.get(start+a*components+(transpose?r*size+c:c*size+r));
    }
    public static void uniformMatrix4fv(int location,boolean transpose,FloatBuffer data) { uniformMatrix(location,4,transpose,data); }

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
        for (int location : p.locationIds) UNIFORM_NAMES_BY_LOCATION.remove(location);
        p.locationIds.clear();
        p.locationRanges.clear();
        p.uniformLocations.clear(); p.uniformValues.clear();
    }
    private record Location(int program, String name, int element, NativeUiProgram.Uniform uniform) {}

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
        final Map<String, Integer> fragmentLocations = new HashMap<>();
        final java.util.Set<Integer> locationIds = new java.util.HashSet<>();
        final java.util.TreeMap<Integer,NativeUiProgram.Uniform> locationRanges = new java.util.TreeMap<>();
        final Map<String, UniformValue> uniformValues = new HashMap<>();
        boolean linked, validated, deleted;
        String log = "";
        NativeUiProgram nativeProgram;
    }

    private record UniformValue(float[] floats, int[] ints) {
    }
}
