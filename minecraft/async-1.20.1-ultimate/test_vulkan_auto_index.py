#!/usr/bin/env python3
"""Check actual AutoIndexBuffer index bytes across the unsigned-short boundary.

Only GPU allocation/upload and logging are doubled. Every generated index,
buffer width, winding, and resource replacement is checked on the CPU.
"""
from __future__ import annotations
import argparse
import json
from pathlib import Path
import shutil
import subprocess
import tempfile

STUBS = {
    "net/vulkanmod/Initializer.java": """package net.vulkanmod;
public class Initializer {
 public static final Logger LOGGER=new Logger();
 public static class Logger { public void info(String s,Object... args) {} }
}""",
    "org/lwjgl/system/MemoryUtil.java": """package org.lwjgl.system;
public class MemoryUtil {
 public static java.nio.ByteBuffer memAlloc(int n) { return java.nio.ByteBuffer.allocate(n).order(java.nio.ByteOrder.nativeOrder()); }
 public static void memFree(java.nio.ByteBuffer b) {}
}""",
    "net/vulkanmod/vulkan/memory/MemoryTypes.java": """package net.vulkanmod.vulkan.memory;
public class MemoryTypes { public static final Object GPU_MEM=new Object(); }""",
    "net/vulkanmod/vulkan/memory/IndexBuffer.java": """package net.vulkanmod.vulkan.memory;
public class IndexBuffer {
 public enum IndexType { SHORT, INT }
 public final IndexType indexType; public java.nio.ByteBuffer bytes; public boolean freed;
 public IndexBuffer(int n,Object memory,IndexType type) { indexType=type; bytes=java.nio.ByteBuffer.allocate(n).order(java.nio.ByteOrder.nativeOrder()); }
 public void copyBuffer(java.nio.ByteBuffer src) { bytes.put(src.duplicate()).clear(); }
 public void freeBuffer() { if(freed) throw new AssertionError("double free"); freed=true; }
}""",
}
PROBE = """import net.vulkanmod.vulkan.memory.*;
public class AutoIndexProbe {
 static void require(boolean ok,String msg) { if(!ok) throw new AssertionError(msg); }
 static int read(IndexBuffer b,int n) { return b.indexType==IndexBuffer.IndexType.INT ? b.bytes.getInt(n*4) : Short.toUnsignedInt(b.bytes.getShort(n*2)); }
 static void verify(AutoIndexBuffer a,AutoIndexBuffer.DrawType type,int vertices) {
  IndexBuffer b=a.getIndexBuffer();
  require(!b.freed,"freed active buffer");
  require(b.indexType==(vertices>65536?IndexBuffer.IndexType.INT:IndexBuffer.IndexType.SHORT),"wrong width for "+vertices);
  int count=switch(type) {
   case QUADS,LINES -> vertices/4*6;
   case TRIANGLE_FAN,TRIANGLE_STRIP -> (vertices-2)*3;
   case DEBUG_LINE_STRIP -> (vertices-1)*2;
   default -> throw new AssertionError();
  };
  require(b.bytes.capacity()==count*(vertices>65536?4:2),"wrong byte capacity");
  int max=-1;
  for(int j=0;j<count;j++) {
   int primitive=j/3, corner=j%3, base=(j/6)*4;
   int expected=switch(type) {
    case QUADS -> base+new int[]{0,1,2,0,2,3}[j%6];
    case LINES -> base+new int[]{0,1,2,3,2,1}[j%6];
    case TRIANGLE_FAN -> corner==0?0:primitive+corner;
    case TRIANGLE_STRIP -> corner==2?primitive+2:primitive+(corner^(primitive&1));
    case DEBUG_LINE_STRIP -> j/2+j%2;
    default -> throw new AssertionError();
   };
   int actual=read(b,j);
   require(actual==expected,"index "+j+" expected "+expected+" got "+actual);
   max=Math.max(max,actual);
  }
  require(max==vertices-1,"last vertex unreachable");
 }
 public static void main(String[] args) {
  var type=AutoIndexBuffer.DrawType.valueOf(args[0]);
  String mode=args[1];
  int n=mode.equals("wide")?65540:65536;
  var a=new AutoIndexBuffer(n,type);
  verify(a,type,n);
  if(mode.equals("growth")) {
   var old=a.getIndexBuffer();
   a.checkCapacity(65540);
   require(old.freed,"old shared allocation not freed");
   require(a.getIndexBuffer()!=old,"shared allocation not replaced");
   verify(a,type,131072);
  }
  a.freeBuffer();
  require(a.getIndexBuffer().freed,"final allocation leaked");
  System.out.println("PASS "+type+" "+mode);
 }
}
"""
TYPES = ("QUADS", "LINES", "TRIANGLE_FAN", "TRIANGLE_STRIP", "DEBUG_LINE_STRIP")
MODES = ("boundary", "wide", "growth")


def exercise(source: Path) -> dict:
    with tempfile.TemporaryDirectory(prefix="hari-auto-index-") as tmp:
        root = Path(tmp)
        for name, body in STUBS.items():
            path = root / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(body, encoding="utf-8")
        shutil.copyfile(source, root / "net/vulkanmod/vulkan/memory/AutoIndexBuffer.java")
        (root / "AutoIndexProbe.java").write_text(PROBE, encoding="utf-8")
        compiler = ["javac"] if shutil.which("javac") else ["java", "com.sun.tools.javac.Main"]
        subprocess.run([*compiler, "-d", str(root / "classes"), *map(str, root.rglob("*.java"))], check=True)
        results = {}
        for topology in TYPES:
            for mode in MODES:
                run = subprocess.run(["java", "-ea", "-cp", str(root / "classes"), "AutoIndexProbe", topology, mode], capture_output=True, text=True)
                results[f"{topology}_{mode}"] = {"pass": run.returncode == 0, "output": (run.stdout + run.stderr).strip()}
        return results


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path)
    parser.add_argument("--baseline", type=Path)
    parser.add_argument("--report", type=Path)
    args = parser.parse_args()
    report = {"scope": "actual AutoIndexBuffer Java; CPU byte/ownership checks, not native Vulkan proof", "candidate": exercise(args.source)}
    ok = all(x["pass"] for x in report["candidate"].values())
    if args.baseline:
        report["baseline"] = exercise(args.baseline)
        for case, result in report["baseline"].items():
            # Original quads happen to widen after doubling to 131072; other
            # topologies never widen. All five fail just above 65536.
            expected = case.endswith("_boundary") or case == "QUADS_growth"
            ok &= result["pass"] == expected
    report["pass"] = bool(ok)
    output = json.dumps(report, indent=2)
    if args.report:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(output + "\n", encoding="utf-8")
    print(output)
    return 0 if ok else 1


if __name__ == "__main__":
    raise SystemExit(main())
