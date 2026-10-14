"""Use official Forge JarSelector on actual private bundles; never load mod classes."""
from pathlib import Path
import argparse, hashlib, json, os, subprocess, tempfile

JAVA = '''import java.io.*;import java.nio.file.*;import java.util.*;import java.util.zip.*;
import net.minecraftforge.jarjar.selection.JarSelector;
public final class NewerSelectionProbe {
 record Node(String name,byte[] bytes){}
 static Optional<InputStream> read(Node node,Path path){
  try(var zip=new ZipInputStream(new ByteArrayInputStream(node.bytes))){
   for(ZipEntry e;(e=zip.getNextEntry())!=null;)if(e.getName().equals(path.toString().replace('\\\\','/')))
    return Optional.of(new ByteArrayInputStream(zip.readAllBytes()));
   return Optional.empty();
  }catch(IOException failure){throw new UncheckedIOException(failure);}
 }
 static List<Node> select(List<Node> sources)throws Exception{
  return JarSelector.detectAndSelect(sources,
   NewerSelectionProbe::read,(n,p)->read(n,p).map(in->{try{return new Node(n.name+"!"+p,in.readAllBytes());}catch(IOException f){throw new UncheckedIOException(f);}}),
   Node::name,failures->new IllegalStateException("Dependency resolution failed: "+failures));
 }
 public static void main(String[] args)throws Exception{
  Node create=new Node("Create",Files.readAllBytes(Path.of(args[0]))),oldCasual=new Node("CasualSwing",Files.readAllBytes(Path.of(args[1]))),nativeCasual=new Node("CasualSwing",Files.readAllBytes(Path.of(args[2])));
  var before=select(List.of(create,oldCasual));var after=select(List.of(create,nativeCasual));
  Node original=before.stream().filter(n->n.name.contains("flywheel-forge-1.20.1-")).findFirst().orElseThrow();
  Node nativeNode=after.stream().filter(n->n.name.contains("flywheel-forge-1.20.1-")).findFirst().orElseThrow();
  Node originalOnly=select(List.of(oldCasual)).stream().filter(n->n.name.contains("flywheel-forge-1.20.1-")).findFirst().orElseThrow();
  Node nativeOnly=select(List.of(nativeCasual)).stream().filter(n->n.name.contains("flywheel-forge-1.20.1-")).findFirst().orElseThrow();
  if(!originalOnly.name.contains("1.0.6-beta-266")||!nativeOnly.name.contains("1.0.6-beta-266"))throw new AssertionError("Newer bundle/version unavailable");
  String classPath="dev/engine_room/flywheel/backend/Backends.class";
  byte[] originalBackend=read(originalOnly,Path.of(classPath)).orElseThrow().readAllBytes();
  byte[] nativeBackend=read(nativeOnly,Path.of(classPath)).orElseThrow().readAllBytes();
  if(new String(originalBackend,java.nio.charset.StandardCharsets.ISO_8859_1).contains("mvhflywheelbackend/NativeEngine"))throw new AssertionError("Original was not legacy backend control");
  if(!new String(nativeBackend,java.nio.charset.StandardCharsets.ISO_8859_1).contains("mvhflywheelbackend/NativeEngine"))throw new AssertionError("Real native factory absent");
  for(var selection:List.of(after,select(List.of(nativeCasual,create)),select(List.of(nativeCasual))))for(Node n:selection){
   if(n.name.contains("flywheel-forge-1.20.1-")){
    byte[] b=read(n,Path.of(classPath)).orElseThrow().readAllBytes();
    if(!new String(b,java.nio.charset.StandardCharsets.ISO_8859_1).contains("mvhflywheelbackend/NativeEngine"))throw new AssertionError("Selected factory is not native");
   }
   if(n.name.contains("Ponder-Forge-1.20.1-")){
    byte[] b=read(n,Path.of("net/createmod/catnip/gui/element/StencilElement.class")).orElseThrow().readAllBytes();
    if(!new String(b,java.nio.charset.StandardCharsets.ISO_8859_1).contains("mvhpondercompat/NativeStencilBridge"))throw new AssertionError("Unpatched duplicate stencil selected");
   }
  }
  System.out.println("FORGE_ORIGINAL_SELECTED="+original.name);System.out.println("FORGE_NATIVE_SELECTED="+nativeNode.name);
  System.out.println("FORGE_NEWER_NATIVE_ONLY="+nativeOnly.name);
  System.out.println("REAL_NATIVE_FACTORY_AND_SELECTED_STENCIL_PASS");
 }
}'''

def main():
    ap=argparse.ArgumentParser();ap.add_argument('--native-create',type=Path,required=True)
    ap.add_argument('--original-casual',type=Path,required=True);ap.add_argument('--native-casual',type=Path,required=True)
    ap.add_argument('--selector-classpath',required=True);ap.add_argument('--report',type=Path,required=True);a=ap.parse_args()
    assert not a.report.exists()
    sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
    assert sha(a.native_create)=='cc2ddeefa78e5eb999f2643277b4b639851b260d24171ff7fe886d15dba88b77'
    assert sha(a.original_casual)=='cb76cc4af706de4d6387b555e36fbb755579a1248a395b7b4d1da29f9535af42'
    with tempfile.TemporaryDirectory(prefix='mvh-official-jar-selector-') as tmp:
        p=Path(tmp);source=p/'NewerSelectionProbe.java';source.write_text(JAVA)
        subprocess.run(['javac','--release','17','-cp',a.selector_classpath,'-d',str(p),str(source)],check=True,capture_output=True)
        r=subprocess.run(['java','-ea','-cp',str(p)+os.pathsep+a.selector_classpath,'NewerSelectionProbe',
                          str(a.native_create.resolve()),str(a.original_casual.resolve()),str(a.native_casual.resolve())],
                         capture_output=True,text=True,timeout=30)
        assert r.returncode == 0, r.stdout+'\n'+r.stderr
    report={'passed':True,'output':r.stdout.splitlines(),
            'native_create_sha256':sha(a.native_create),'original_casual_sha256':sha(a.original_casual),
            'native_casual_sha256':sha(a.native_casual),
            'official_selector_and_dependencies':{Path(f).name:sha(Path(f)) for f in a.selector_classpath.split(os.pathsep)},
            'mod_classes_loaded':False,'minecraft_or_opengl_launched':False,
            'scope':'Actual official Forge0.3.19 JarSelector reads original/repaired archive metadata and bytes. Tests both source orders and the newer-only source. Whichever copy is selected now links real NativeEngine and accepted Ponder stencil bridge; original newer-only control remains legacy. A newer bundle is not assumed always selected. Not Minecraft/visual/FPS acceptance.'}
    a.report.parent.mkdir(parents=True,exist_ok=True);a.report.write_text(json.dumps(report,indent=2)+'\n')
    print(r.stdout.strip())

if __name__=='__main__':main()
