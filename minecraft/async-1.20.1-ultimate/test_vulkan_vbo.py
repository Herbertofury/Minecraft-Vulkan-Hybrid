#!/usr/bin/env python3
"""Execute the actual reconstructed VBO class against checked resource doubles.

This is a focused CPU ownership regression test, not Vulkan/Minecraft runtime
proof. GPU and Minecraft dependencies are doubles; VBO.java is never rewritten.
Use --baseline with the pinned renderer to prove the regressions fail there.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import shutil
import subprocess
import tempfile

STUBS = {
    "org/joml/Matrix4f.java": """package org.joml;
public class Matrix4f {
 public Matrix4f() {} public Matrix4f(Matrix4f x) {}
 public Matrix4f set(java.nio.FloatBuffer x) { return this; }
 public Matrix4f translate(float x,float y,float z) { return this; }
} """,
    "net/minecraftforge/api/distmarker/Dist.java": """package net.minecraftforge.api.distmarker;
public enum Dist { CLIENT }""",
    "net/minecraftforge/api/distmarker/OnlyIn.java": """package net.minecraftforge.api.distmarker;
public @interface OnlyIn { Dist value(); }""",
    "com/mojang/blaze3d/shaders/Uniform.java": """package com.mojang.blaze3d.shaders;
public class Uniform { public java.nio.FloatBuffer getFloatBuffer() { return java.nio.FloatBuffer.allocate(16); } }""",
    "com/mojang/blaze3d/vertex/VertexFormat.java": """package com.mojang.blaze3d.vertex;
public class VertexFormat {
 public int getVertexSize() { return 4; }
 public enum IndexType { SHORT, INT }
 public enum Mode { TRIANGLE_FAN, TRIANGLE_STRIP, LINE_STRIP, QUADS, LINES,
  DEBUG_LINE_STRIP, TRIANGLES, DEBUG_LINES; public final int asGLMode=0; }
}""",
    "com/mojang/blaze3d/vertex/BufferBuilder.java": """package com.mojang.blaze3d.vertex;
import java.nio.ByteBuffer;
public class BufferBuilder {
 public record DrawState(VertexFormat format, int vertexCount, int indexCount,
   VertexFormat.Mode mode, VertexFormat.IndexType indexType, boolean indexOnly, boolean sequentialIndex) {}
 public static class RenderedBuffer {
  private final DrawState state; public int releases;
  public RenderedBuffer(DrawState s) { state=s; }
  public DrawState drawState() { return state; }
  public ByteBuffer vertexBuffer() { return ByteBuffer.allocate(state.vertexCount()*4); }
  public ByteBuffer indexBuffer() { return ByteBuffer.allocate(state.indexCount()*4); }
  public void release() { if (++releases!=1) throw new AssertionError("double release"); }
 }
}""",
    "net/vulkanmod/vulkan/shader/GraphicsPipeline.java": """package net.vulkanmod.vulkan.shader;
public class GraphicsPipeline {}""",
    "net/vulkanmod/interfaces/ShaderMixed.java": """package net.vulkanmod.interfaces;
public interface ShaderMixed { net.vulkanmod.vulkan.shader.GraphicsPipeline getPipeline(); }""",
    "net/minecraft/client/renderer/ShaderInstance.java": """package net.minecraft.client.renderer;
public class ShaderInstance implements net.vulkanmod.interfaces.ShaderMixed {
 public com.mojang.blaze3d.shaders.Uniform MODEL_VIEW_MATRIX,CHUNK_OFFSET,PROJECTION_MATRIX;
 public net.vulkanmod.vulkan.shader.GraphicsPipeline getPipeline() { return null; }
}""",
    "com/mojang/blaze3d/systems/RenderSystem.java": """package com.mojang.blaze3d.systems;
public class RenderSystem {
 public static void assertOnRenderThread() {}
 public static void setShader(java.util.function.Supplier<net.minecraft.client.renderer.ShaderInstance> s) {}
 public static net.minecraft.client.renderer.ShaderInstance getShader() { return null; }
 public static org.joml.Matrix4f getModelViewMatrix() { return new org.joml.Matrix4f(); }
 public static org.joml.Matrix4f getProjectionMatrix() { return new org.joml.Matrix4f(); }
}""",
    "net/vulkanmod/vulkan/VRenderSystem.java": """package net.vulkanmod.vulkan;
public class VRenderSystem {
 public static void applyMVP(org.joml.Matrix4f a,org.joml.Matrix4f b) {}
 public static void setPrimitiveTopologyGL(int x) {}
}""",
    "net/vulkanmod/vulkan/texture/VTextureSelector.java": """package net.vulkanmod.vulkan.texture;
public class VTextureSelector { public static void bindShaderTextures(net.vulkanmod.vulkan.shader.GraphicsPipeline p) {} }""",
    "net/vulkanmod/vulkan/memory/MemoryTypes.java": """package net.vulkanmod.vulkan.memory;
public class MemoryTypes { public static final Object GPU_MEM=new Object(); }""",
    "net/vulkanmod/vulkan/memory/IndexBuffer.java": """package net.vulkanmod.vulkan.memory;
public class IndexBuffer {
 public enum IndexType { SHORT, INT }
 public final IndexType indexType; public boolean freed; public static int frees;
 public IndexBuffer(int size,Object type) { this(size,type,IndexType.SHORT); }
 public IndexBuffer(int size,Object type,IndexType it) { indexType=it; }
 public void copyBuffer(java.nio.ByteBuffer b) { check(); }
 public void check() { if (freed) throw new AssertionError("use after free: index buffer"); }
 public void freeBuffer() { check(); freed=true; frees++; }
}""",
    "net/vulkanmod/vulkan/memory/VertexBuffer.java": """package net.vulkanmod.vulkan.memory;
public class VertexBuffer {
 public boolean freed; public static int frees;
 public VertexBuffer(int n,Object t) {}
 public void copyToVertexBuffer(int a,int b,java.nio.ByteBuffer c) { check(); }
 public void check() { if (freed) throw new AssertionError("use after free: vertex buffer"); }
 public void freeBuffer() { check(); freed=true; frees++; }
}""",
    "net/vulkanmod/vulkan/memory/AutoIndexBuffer.java": """package net.vulkanmod.vulkan.memory;
public class AutoIndexBuffer {
 private IndexBuffer buffer=new IndexBuffer(8,MemoryTypes.GPU_MEM); private int capacity=4;
 public void checkCapacity(int n) { if(n>capacity) { buffer.freeBuffer(); buffer=new IndexBuffer(n,MemoryTypes.GPU_MEM); capacity=n; } }
 public IndexBuffer getIndexBuffer() { return buffer; }
 public static class DrawType { public static int getTriangleStripIndexCount(int n) { return (n-2)*3; } }
}""",
    "net/vulkanmod/vulkan/Renderer.java": """package net.vulkanmod.vulkan;
import net.vulkanmod.vulkan.memory.*;
public class Renderer {
 private static final Renderer INSTANCE=new Renderer(); private static final Drawer DRAWER=new Drawer();
 public static Drawer getDrawer() { return DRAWER; } public static Renderer getInstance() { return INSTANCE; }
 public void bindGraphicsPipeline(net.vulkanmod.vulkan.shader.GraphicsPipeline p) {}
 public void uploadAndBindUBOs(net.vulkanmod.vulkan.shader.GraphicsPipeline p) {}
 public static class Drawer {
  public boolean indexed; public IndexBuffer last; public final AutoIndexBuffer auto=new AutoIndexBuffer();
  public AutoIndexBuffer getTriangleFanIndexBuffer() { return auto; }
  public AutoIndexBuffer getTriangleStripIndexBuffer() { return auto; }
  public AutoIndexBuffer getQuadsIndexBuffer() { return auto; }
  public AutoIndexBuffer getLinesIndexBuffer() { return auto; }
  public AutoIndexBuffer getDebugLineStripIndexBuffer() { return auto; }
  public void drawIndexed(VertexBuffer v,IndexBuffer i,int count) { v.check(); i.check(); indexed=true; last=i; }
  public void draw(VertexBuffer v,int n) { v.check(); indexed=false; last=null; }
 }
}""",
}

PROBE = """import net.vulkanmod.render.VBO;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.memory.*;
import com.mojang.blaze3d.vertex.*;
public class VboProbe {
 static void require(boolean p,String s) { if(!p) throw new AssertionError(s); }
 static void upload(VBO v,boolean sequential,VertexFormat.Mode mode,int vertices,VertexFormat.IndexType type,boolean indexOnly) {
  var b=new BufferBuilder.RenderedBuffer(new BufferBuilder.DrawState(new VertexFormat(),vertices,vertices*3/2,mode,type,indexOnly,sequential));
  v.upload(b); require(b.releases==1,"rendered buffer not released exactly once");
 }
 static void auto(VBO v,int n) { upload(v,true,VertexFormat.Mode.QUADS,n,VertexFormat.IndexType.SHORT,false); }
 static void explicit(VBO v,VertexFormat.IndexType t) { upload(v,false,VertexFormat.Mode.QUADS,4,t,false); }
 static void draw(VBO v) { v.drawChunkLayer(); }
 public static void main(String[] args) {
  VBO a=new VBO(), b=new VBO(); var d=Renderer.getDrawer();
  switch(args[0]) {
   case "shared_growth" -> { auto(a,4); draw(a); auto(b,100); draw(b); draw(a); require(d.last==d.auto.getIndexBuffer(),"stale shared buffer"); }
   case "auto_to_explicit" -> { auto(a,4); draw(a); var shared=d.auto.getIndexBuffer(); explicit(a,VertexFormat.IndexType.SHORT); require(!shared.freed,"freed drawer-owned shared buffer"); }
   case "explicit_ownership" -> { auto(a,4); draw(a); explicit(a,VertexFormat.IndexType.SHORT); draw(a); var owned=d.last; a.close(); require(owned.freed,"explicit buffer leaked after mode transition"); }
   case "empty_close" -> { explicit(a,VertexFormat.IndexType.SHORT); draw(a); var owned=d.last; int before=VertexBuffer.frees; auto(a,0); a.close(); require(owned.freed && VertexBuffer.frees==before+1,"empty upload hid live buffers from close"); a.close(); }
   case "nonindexed_transition" -> { auto(a,4); draw(a); upload(a,true,VertexFormat.Mode.TRIANGLES,3,VertexFormat.IndexType.SHORT,false); draw(a); require(!d.indexed,"nonindexed draw used stale quad indices"); }
   case "index32" -> { explicit(a,VertexFormat.IndexType.INT); draw(a); require(d.last.indexType==IndexBuffer.IndexType.INT,"32-bit indices interpreted as 16-bit"); }
   case "index_only" -> { explicit(a,VertexFormat.IndexType.SHORT); int before=VertexBuffer.frees; upload(a,false,VertexFormat.Mode.QUADS,4,VertexFormat.IndexType.INT,true); draw(a); require(VertexBuffer.frees==before,"sorted index update replaced vertices"); require(d.last.indexType==IndexBuffer.IndexType.INT,"sorted index width lost"); }
   case "close_idempotent" -> { explicit(a,VertexFormat.IndexType.SHORT); a.close(); a.close(); draw(a); }
   default -> throw new AssertionError("unknown case");
  }
  System.out.println("PASS "+args[0]);
 }
}
"""
CASES = ("shared_growth", "auto_to_explicit", "explicit_ownership", "empty_close",
         "nonindexed_transition", "index32", "index_only", "close_idempotent")


def exercise(source: Path) -> dict:
    with tempfile.TemporaryDirectory(prefix="hari-vbo-test-") as tmp:
        root = Path(tmp)
        for name, body in STUBS.items():
            p = root / name
            p.parent.mkdir(parents=True, exist_ok=True)
            p.write_text(body, encoding="utf-8")
        dest = root / "net/vulkanmod/render/VBO.java"
        dest.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(source, dest)
        (root / "VboProbe.java").write_text(PROBE, encoding="utf-8")
        classes = root / "classes"
        # Some runtime images include jdk.compiler without a javac launcher.
        compiler = ["javac"] if shutil.which("javac") else ["java", "com.sun.tools.javac.Main"]
        subprocess.run([*compiler, "-d", str(classes), *map(str, root.rglob("*.java"))], check=True)
        results = {}
        for case in CASES:
            run = subprocess.run(["java", "-ea", "-cp", str(classes), "VboProbe", case], text=True, capture_output=True)
            results[case] = {"pass": run.returncode == 0, "output": (run.stdout + run.stderr).strip()}
        return results


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path, help="Actual reconstructed VBO.java")
    parser.add_argument("--baseline", type=Path, help="Original pinned VBO.java negative control")
    parser.add_argument("--report", type=Path)
    args = parser.parse_args()
    report = {"scope": "actual VBO Java logic with checked CPU resource doubles; not native Vulkan proof",
              "candidate": exercise(args.source)}
    ok = all(x["pass"] for x in report["candidate"].values())
    if args.baseline:
        report["baseline"] = exercise(args.baseline)
        # The unchanged harmless repeated-close case stays green; seven real
        # regressions must fail on the original implementation.
        ok &= all(not report["baseline"][x]["pass"] for x in CASES[:-1])
        ok &= report["baseline"]["close_idempotent"]["pass"]
    report["pass"] = bool(ok)
    output = json.dumps(report, indent=2)
    if args.report:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(output + "\n", encoding="utf-8")
    print(output)
    return 0 if ok else 1


if __name__ == "__main__":
    raise SystemExit(main())
