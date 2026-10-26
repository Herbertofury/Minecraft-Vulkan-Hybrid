#!/usr/bin/env python3
"""Execute production atomic TOML saves with the real Minecraft-pinned NightConfig 3.6.3."""
import argparse,json,shutil,subprocess,tempfile
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('source',type=Path);p.add_argument('core',type=Path);p.add_argument('toml',type=Path);p.add_argument('--report',type=Path);args=p.parse_args()
test=r'''
import com.axalotl.async.forge.config.AtomicConfigPersistence;
import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.toml.TomlParser;
import java.nio.file.*;import java.util.*;import java.util.concurrent.atomic.*;
public final class ConfigSaveTest {
 static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
 public static void main(String[] args)throws Exception {
  Path dir=Files.createTempDirectory("hari-save-test-");Path file=dir.resolve("harimt.toml");
  Files.writeString(file,"# retain user comment\nother = \"Å user value\"\n[\"Async Config\"]\n# GPU comment\nenableGpuCollision = true\nmaxThreads = 4\nunknown = 123\n");
  AtomicReference<Throwable> readerFailure=new AtomicReference<>();AtomicBoolean done=new AtomicBoolean();AtomicInteger reads=new AtomicInteger();
  Thread reader=new Thread(()->{try{while(!done.get()){CommentedConfig c=new TomlParser().parse(Files.readString(file));boolean flag=c.get(List.of("Async Config","enableGpuCollision"));int threads=c.get(List.of("Async Config","maxThreads"));check(threads==(flag?4:2),"reader saw half-updated settings");reads.incrementAndGet();}}catch(Throwable failure){readerFailure.set(failure);}});reader.start();
  for(int i=0;i<100;i++){boolean enabled=(i%2)!=0;AtomicConfigPersistence.save(file,Map.of("enableGpuCollision",enabled,"maxThreads",enabled?4:2));}
  AtomicConfigPersistence.save(file,Map.of("enableGpuCollision",false,"maxThreads",2,"synchronizedEntities",List.of("minecraft:cow","example:custom")));
  done.set(true);reader.join();check(readerFailure.get()==null,"concurrent parse failed: "+readerFailure.get());check(reads.get()>0,"reader did not observe config");
  CommentedConfig saved=new TomlParser().parse(Files.readString(file));check(Boolean.FALSE.equals(saved.get(List.of("Async Config","enableGpuCollision"))),"toggle not durable");
  check(Integer.valueOf(123).equals(saved.get(List.of("Async Config","unknown"))),"unknown user setting lost");check("Å user value".equals(saved.get("other")),"Unicode setting lost");
  check(Files.readString(file).contains("GPU comment")&&Files.readString(file).contains("retain user comment"),"comments lost");
  check(((List<?>)saved.get(List.of("Async Config","synchronizedEntities"))).size()==2,"entity synchronization list lost");
  try(var paths=Files.list(dir)){check(paths.count()==1,"temporary files leaked");}
  System.out.println("PASS: 101 atomic settings saves, concurrent full-snapshot reads, comments, Unicode, unknown keys, lists, and cleanup");
 }
}
'''
with tempfile.TemporaryDirectory(prefix='hari-config-compile-') as temp:
 root=Path(temp);(root/'AtomicConfigPersistence.java').write_bytes(args.source.read_bytes());(root/'ConfigSaveTest.java').write_text(test)
 cp=str(args.core.resolve())+':'+str(args.toml.resolve());compiler=['javac'] if shutil.which('javac') else ['java','com.sun.tools.javac.Main']
 subprocess.run(compiler+['--release','17','-cp',cp,'-d',str(root),str(root/'AtomicConfigPersistence.java'),str(root/'ConfigSaveTest.java')],check=True)
 result=subprocess.run(['java','-cp',str(root)+':'+cp,'ConfigSaveTest'],capture_output=True,text=True,check=True);print(result.stdout,end='')
if args.report:
 args.report.parent.mkdir(parents=True,exist_ok=True);args.report.write_text(json.dumps({'atomic_saves':101,'passed':True,'scope':'Actual production writer and real NightConfig 3.6.3 with concurrent file reads; native command/restart gate is separate.'},indent=2)+'\n')
