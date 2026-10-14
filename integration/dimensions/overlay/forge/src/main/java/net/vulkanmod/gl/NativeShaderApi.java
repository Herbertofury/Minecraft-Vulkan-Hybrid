package net.vulkanmod.gl;

import net.vulkanmod.compat.UniversalRendererGate;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.ARBShaderObjects;
import java.nio.*;

/** Shader call-site transport for Forge's bootstrap-owned LWJGL APIs.
 * Native draws use the canonical kernel; OpenGL fallback calls the original API.
 * This facade does not authorize unimplemented draw/layout/provider contracts. */
public final class NativeShaderApi {
    private NativeShaderApi() {}
    private static boolean nativeRenderer() { return UniversalRendererGate.vulkanRendererEnabled(); }
    public static int glCreateShader(int type) { if (!nativeRenderer()) return GL20C.glCreateShader(type); return GlProgram.createShader(type); }
    public static int glCreateProgram() { if (!nativeRenderer()) return GL20C.glCreateProgram(); return GlProgram.createProgram(); }
    public static void glShaderSource(int shader, CharSequence source) { if (!nativeRenderer()) { GL20C.glShaderSource(shader, source); return; } GlProgram.shaderSource(shader, source); }
    public static void glCompileShader(int shader) { if (!nativeRenderer()) { GL20C.glCompileShader(shader); return; } GlProgram.compileShader(shader); }
    public static void glAttachShader(int program, int shader) { if (!nativeRenderer()) { GL20C.glAttachShader(program, shader); return; } GlProgram.attachShader(program, shader); }
    public static void glDetachShader(int program, int shader) { if (!nativeRenderer()) { GL20C.glDetachShader(program, shader); return; } GlProgram.detachShader(program, shader); }
    public static void glLinkProgram(int program) { if (!nativeRenderer()) { GL20C.glLinkProgram(program); return; } GlProgram.linkProgram(program); }
    public static void glUseProgram(int program) { if (!nativeRenderer()) { GL20C.glUseProgram(program); return; } GlProgram.useProgram(program); }
    public static void glDeleteProgram(int program) { if (!nativeRenderer()) { GL20C.glDeleteProgram(program); return; } GlProgram.deleteProgram(program); }
    public static void glDeleteShader(int shader) { if (!nativeRenderer()) { GL20C.glDeleteShader(shader); return; } GlProgram.deleteShader(shader); }
    public static void glValidateProgram(int program) { if (!nativeRenderer()) { GL20C.glValidateProgram(program); return; } NativeUiRenderBridge.validateProgram(program); }
    public static int glGetShaderi(int shader, int pname) { if (!nativeRenderer()) return GL20C.glGetShaderi(shader, pname); return GlProgram.getShaderi(shader, pname); }
    public static boolean glIsShader(int shader) { if (!nativeRenderer()) return GL20C.glIsShader(shader); return GlProgram.isShader(shader); }
    public static String glGetShaderInfoLog(int shader) { if (!nativeRenderer()) return GL20C.glGetShaderInfoLog(shader); return GlProgram.getShaderInfoLog(shader); }
    public static String glGetShaderInfoLog(int shader, int maxLength) { if (!nativeRenderer()) return GL20C.glGetShaderInfoLog(shader, maxLength); return GlShaderStrings.truncate(GlProgram.getShaderInfoLog(shader), maxLength); }
    public static void glGetShaderInfoLog(int shader, IntBuffer length, ByteBuffer result) { if (!nativeRenderer()) { GL20C.glGetShaderInfoLog(shader, length, result); return; } GlShaderStrings.write(GlProgram.getShaderInfoLog(shader), length, result); }
    public static void glGetShaderInfoLog(int shader, int[] length, ByteBuffer result) { if (!nativeRenderer()) { GL20C.glGetShaderInfoLog(shader, length, result); return; } GlShaderStrings.write(GlProgram.getShaderInfoLog(shader), length, result); }
    public static String glGetShaderSource(int shader) { if (!nativeRenderer()) return GL20C.glGetShaderSource(shader); return GlProgram.getShaderSource(shader); }
    public static String glGetShaderSource(int shader, int maxLength) { if (!nativeRenderer()) return GL20C.glGetShaderSource(shader, maxLength); return GlShaderStrings.truncate(GlProgram.getShaderSource(shader), maxLength); }
    public static void glGetShaderSource(int shader, IntBuffer length, ByteBuffer result) { if (!nativeRenderer()) { GL20C.glGetShaderSource(shader, length, result); return; } GlShaderStrings.write(GlProgram.getShaderSource(shader), length, result); }
    public static void glGetShaderSource(int shader, int[] length, ByteBuffer result) { if (!nativeRenderer()) { GL20C.glGetShaderSource(shader, length, result); return; } GlShaderStrings.write(GlProgram.getShaderSource(shader), length, result); }
    public static int glGetProgrami(int program, int pname) { if (!nativeRenderer()) return GL20C.glGetProgrami(program, pname); return GlProgram.getProgrami(program, pname); }
    public static boolean glIsProgram(int program) { if (!nativeRenderer()) return GL20C.glIsProgram(program); return GlProgram.isProgram(program); }
    public static String glGetProgramInfoLog(int program) { if (!nativeRenderer()) return GL20C.glGetProgramInfoLog(program); return GlProgram.getProgramInfoLog(program); }
    public static String glGetProgramInfoLog(int program, int maxLength) { if (!nativeRenderer()) return GL20C.glGetProgramInfoLog(program, maxLength); return GlShaderStrings.truncate(GlProgram.getProgramInfoLog(program), maxLength); }
    public static void glGetProgramInfoLog(int program, IntBuffer length, ByteBuffer result) { if (!nativeRenderer()) { GL20C.glGetProgramInfoLog(program, length, result); return; } GlShaderStrings.write(GlProgram.getProgramInfoLog(program), length, result); }
    public static void glGetProgramInfoLog(int program, int[] length, ByteBuffer result) { if (!nativeRenderer()) { GL20C.glGetProgramInfoLog(program, length, result); return; } GlShaderStrings.write(GlProgram.getProgramInfoLog(program), length, result); }
    public static int glGetUniformLocation(int program, CharSequence name) { if (!nativeRenderer()) return GL20C.glGetUniformLocation(program, name); return GlProgram.getUniformLocation(program, name); }
    public static int glGetUniformLocation(int program, ByteBuffer name) { if (!nativeRenderer()) return GL20C.glGetUniformLocation(program, name); return GlProgram.getUniformLocation(program, GlShaderStrings.readCString(name)); }
    public static int glGetAttribLocation(int program, CharSequence name) { if (!nativeRenderer()) return GL20C.glGetAttribLocation(program, name); return GlProgram.getAttribLocation(program, name); }
    public static int glGetAttribLocation(int program, ByteBuffer name) { if (!nativeRenderer()) return GL20C.glGetAttribLocation(program, name); return GlProgram.getAttribLocation(program, GlShaderStrings.readCString(name)); }
    public static void glBindAttribLocation(int program, int index, CharSequence name) { if (!nativeRenderer()) { GL20C.glBindAttribLocation(program, index, name); return; } GlProgram.bindAttribLocation(program, index, name); }
    public static void glBindAttribLocation(int program, int index, ByteBuffer name) { if (!nativeRenderer()) { GL20C.glBindAttribLocation(program, index, name); return; } GlProgram.bindAttribLocation(program, index, GlShaderStrings.readCString(name)); }
    public static void glUniform1i(int location, int value) { if (!nativeRenderer()) { GL20C.glUniform1i(location, value); return; } GlProgram.uniform1i(location, value); }
    public static void glUniform1f(int location, float value) { if (!nativeRenderer()) { GL20C.glUniform1f(location, value); return; } GlProgram.uniform1f(location, value); }
    public static void glUniform2f(int location, float x, float y) { if (!nativeRenderer()) { GL20C.glUniform2f(location, x, y); return; } GlProgram.uniform2f(location, x, y); }
    public static void glUniform3f(int location, float x, float y, float z) { if (!nativeRenderer()) { GL20C.glUniform3f(location, x, y, z); return; } GlProgram.uniform3f(location, x, y, z); }
    public static void glUniform4f(int location, float x, float y, float z, float w) { if (!nativeRenderer()) { GL20C.glUniform4f(location, x, y, z, w); return; } GlProgram.uniform4f(location, x, y, z, w); }
    public static void glUniform3i(int location, int x, int y, int z) { if (!nativeRenderer()) { GL20C.glUniform3i(location, x, y, z); return; } GlProgram.uniform3i(location, x, y, z); }
    public static void glUniformMatrix4fv(int location, boolean transpose, FloatBuffer value) { if (!nativeRenderer()) { GL20C.glUniformMatrix4fv(location, transpose, value); return; } GlProgram.uniformMatrix4fv(location, transpose, value); }
    public static int glCreateShaderObjectARB(int type) { if (!nativeRenderer()) return ARBShaderObjects.glCreateShaderObjectARB(type); return GlProgram.createShader(type); }
    public static int glCreateProgramObjectARB() { if (!nativeRenderer()) return ARBShaderObjects.glCreateProgramObjectARB(); return GlProgram.createProgram(); }
    public static void glShaderSourceARB(int shader, CharSequence source) { if (!nativeRenderer()) { ARBShaderObjects.glShaderSourceARB(shader, source); return; } GlProgram.shaderSource(shader, source); }
    public static void glCompileShaderARB(int shader) { if (!nativeRenderer()) { ARBShaderObjects.glCompileShaderARB(shader); return; } GlProgram.compileShader(shader); }
    public static void glAttachObjectARB(int program, int shader) { if (!nativeRenderer()) { ARBShaderObjects.glAttachObjectARB(program, shader); return; } GlProgram.attachShader(program, shader); }
    public static void glDetachObjectARB(int program, int shader) { if (!nativeRenderer()) { ARBShaderObjects.glDetachObjectARB(program, shader); return; } GlProgram.detachShader(program, shader); }
    public static void glLinkProgramARB(int program) { if (!nativeRenderer()) { ARBShaderObjects.glLinkProgramARB(program); return; } GlProgram.linkProgram(program); }
    public static void glUseProgramObjectARB(int program) { if (!nativeRenderer()) { ARBShaderObjects.glUseProgramObjectARB(program); return; } GlProgram.useProgram(program); }
    public static void glValidateProgramARB(int program) { if (!nativeRenderer()) { ARBShaderObjects.glValidateProgramARB(program); return; } NativeUiRenderBridge.validateProgram(program); }
    public static String glGetShaderSourceARB(int shader) { if (!nativeRenderer()) return ARBShaderObjects.glGetShaderSourceARB(shader); return GlProgram.getShaderSource(shader); }
    public static String glGetShaderSourceARB(int shader, int maxLength) { if (!nativeRenderer()) return ARBShaderObjects.glGetShaderSourceARB(shader, maxLength); return GlShaderStrings.truncate(GlProgram.getShaderSource(shader), maxLength); }
    public static void glGetShaderSourceARB(int shader, IntBuffer length, ByteBuffer result) { if (!nativeRenderer()) { ARBShaderObjects.glGetShaderSourceARB(shader, length, result); return; } GlShaderStrings.write(GlProgram.getShaderSource(shader), length, result); }
    public static void glGetShaderSourceARB(int shader, int[] length, ByteBuffer result) { if (!nativeRenderer()) { ARBShaderObjects.glGetShaderSourceARB(shader, length, result); return; } GlShaderStrings.write(GlProgram.getShaderSource(shader), length, result); }
    public static int glGetUniformLocationARB(int program, CharSequence name) { if (!nativeRenderer()) return ARBShaderObjects.glGetUniformLocationARB(program, name); return GlProgram.getUniformLocation(program, name); }
    public static int glGetUniformLocationARB(int program, ByteBuffer name) { if (!nativeRenderer()) return ARBShaderObjects.glGetUniformLocationARB(program, name); return GlProgram.getUniformLocation(program, GlShaderStrings.readCString(name)); }
    public static void glUniform1iARB(int location, int value) { if (!nativeRenderer()) { ARBShaderObjects.glUniform1iARB(location, value); return; } GlProgram.uniform1i(location, value); }
    public static void glUniform1fARB(int location, float value) { if (!nativeRenderer()) { ARBShaderObjects.glUniform1fARB(location, value); return; } GlProgram.uniform1f(location, value); }
    public static void glUniform2fARB(int location, float x, float y) { if (!nativeRenderer()) { ARBShaderObjects.glUniform2fARB(location, x, y); return; } GlProgram.uniform2f(location, x, y); }
    public static void glUniform3fARB(int location, float x, float y, float z) { if (!nativeRenderer()) { ARBShaderObjects.glUniform3fARB(location, x, y, z); return; } GlProgram.uniform3f(location, x, y, z); }
    public static void glUniform4fARB(int location, float x, float y, float z, float w) { if (!nativeRenderer()) { ARBShaderObjects.glUniform4fARB(location, x, y, z, w); return; } GlProgram.uniform4f(location, x, y, z, w); }
    public static void glUniform3iARB(int location, int x, int y, int z) { if (!nativeRenderer()) { ARBShaderObjects.glUniform3iARB(location, x, y, z); return; } GlProgram.uniform3i(location, x, y, z); }
    public static void glUniformMatrix4fvARB(int location, boolean transpose, FloatBuffer value) { if (!nativeRenderer()) { ARBShaderObjects.glUniformMatrix4fvARB(location, transpose, value); return; } GlProgram.uniformMatrix4fv(location, transpose, value); }
    public static void glDeleteObjectARB(int object) { if (!nativeRenderer()) { ARBShaderObjects.glDeleteObjectARB(object); return; } GlProgram.deleteObject(object); }
    public static int glGetHandleARB(int pname) { if (!nativeRenderer()) return ARBShaderObjects.glGetHandleARB(pname); return pname == ARBShaderObjects.GL_PROGRAM_OBJECT_ARB ? GlProgram.currentProgramId() : 0; }
    public static int glGetObjectParameteriARB(int object, int pname) { if (!nativeRenderer()) return ARBShaderObjects.glGetObjectParameteriARB(object, pname); return objectParameter(object, pname); }
    public static String glGetInfoLogARB(int object) { if (!nativeRenderer()) return ARBShaderObjects.glGetInfoLogARB(object); return objectLog(object); }
    public static String glGetInfoLogARB(int object, int maxLength) { if (!nativeRenderer()) return ARBShaderObjects.glGetInfoLogARB(object, maxLength); return GlShaderStrings.truncate(objectLog(object), maxLength); }
    public static void glGetInfoLogARB(int object, IntBuffer length, ByteBuffer result) { if (!nativeRenderer()) { ARBShaderObjects.glGetInfoLogARB(object, length, result); return; } GlShaderStrings.write(objectLog(object), length, result); }
    public static void glGetInfoLogARB(int object, int[] length, ByteBuffer result) { if (!nativeRenderer()) { ARBShaderObjects.glGetInfoLogARB(object, length, result); return; } GlShaderStrings.write(objectLog(object), length, result); }
    private static String objectLog(int object) { return GlProgram.isProgram(object) ? GlProgram.getProgramInfoLog(object) : GlProgram.getShaderInfoLog(object); }
    private static int objectParameter(int object, int pname) {
        if (pname == ARBShaderObjects.GL_OBJECT_TYPE_ARB)
            return GlProgram.isProgram(object) ? ARBShaderObjects.GL_PROGRAM_OBJECT_ARB : GlProgram.isShader(object) ? ARBShaderObjects.GL_SHADER_OBJECT_ARB : 0;
        if (pname == ARBShaderObjects.GL_OBJECT_SUBTYPE_ARB) return GlProgram.getShaderi(object, GL20C.GL_SHADER_TYPE);
        // ARB status/log/source-length enum values equal their core equivalents.
        return GlProgram.isProgram(object) ? GlProgram.getProgrami(object, pname) : GlProgram.getShaderi(object, pname);
    }
}
