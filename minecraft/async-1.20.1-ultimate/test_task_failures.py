#!/usr/bin/env python3
"""Execute the production worker-failure barrier with real Java futures/threads."""
import argparse,json,subprocess,tempfile,shutil
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('source',type=Path);p.add_argument('--report',type=Path);args=p.parse_args()
test=r'''
import com.axalotl.async.common.TaskFailures;
import java.util.*;
import java.util.concurrent.*;
import java.io.IOException;
public final class FailureBarrierTest {
    static void check(boolean condition, String detail) { if (!condition) throw new AssertionError(detail); }
    static Throwable failure(List<? extends Future<?>> futures) {
        try { TaskFailures.joinCompleted(futures); throw new AssertionError("failure was swallowed"); }
        catch (AssertionError assertion) { throw assertion; }
        catch (Throwable failure) { return failure; }
    }
    public static void main(String[] args) throws Exception {
        RuntimeException original = new IllegalArgumentException("bad entity tick");
        CompletableFuture<Void> broken = new CompletableFuture<>(); broken.completeExceptionally(original);
        CompletableFuture<Void> active = new CompletableFuture<>();
        Thread finisher = new Thread(() -> { try { Thread.sleep(120); } catch (InterruptedException e) { throw new AssertionError(e); } active.complete(null); });
        finisher.start();
        check(failure(List.of(broken, active)) == original, "original runtime failure identity lost");
        check(active.isDone(), "failure escaped before the other worker finished"); finisher.join();
        RuntimeException second = new IllegalStateException("second worker");
        CompletableFuture<Void> other = new CompletableFuture<>(); other.completeExceptionally(second);
        check(failure(List.of(broken, other)) == original && Arrays.asList(original.getSuppressed()).contains(second), "secondary failure lost");
        Error fatal = new LinkageError("fatal worker error");
        CompletableFuture<Void> errored = new CompletableFuture<>(); errored.completeExceptionally(fatal);
        check(failure(List.of(errored)) == fatal, "fatal Error was swallowed or changed");
        IOException checked = new IOException("worker I/O");
        CompletableFuture<Void> io = new CompletableFuture<>(); io.completeExceptionally(checked);
        check(failure(List.of(io)).getCause() == checked, "checked failure cause lost");
        CompletableFuture<Void> interrupted = new CompletableFuture<>();
        Thread done = new Thread(() -> { try { Thread.sleep(120); } catch (InterruptedException e) { throw new AssertionError(e); } interrupted.complete(null); }); done.start();
        Thread.currentThread().interrupt();
        Throwable interruption = failure(List.of(interrupted));
        check(interrupted.isDone() && Thread.currentThread().isInterrupted() && interruption.getCause() instanceof InterruptedException, "interruption broke barrier or disappeared");
        Thread.interrupted(); done.join();
        CompletableFuture<Void> cancelled = new CompletableFuture<>(); cancelled.cancel(false);
        check(failure(List.of(cancelled)) instanceof CancellationException, "cancelled work counted as success");
        TaskFailures.joinCompleted(List.of(CompletableFuture.completedFuture(null)));
        TaskFailures.joinCompleted(List.of());
        System.out.println("PASS: 8 real-future failure/barrier cases");
    }
}
'''
with tempfile.TemporaryDirectory(prefix='hari-task-failures-') as temp:
    root=Path(temp);(root/'TaskFailures.java').write_bytes(args.source.read_bytes());(root/'FailureBarrierTest.java').write_text(test)
    compiler = ['javac'] if shutil.which('javac') else ['java', 'com.sun.tools.javac.Main']
    subprocess.run(compiler + ['--release','17','-d',str(root),str(root/'TaskFailures.java'),str(root/'FailureBarrierTest.java')],check=True)
    result=subprocess.run(['java','-cp',str(root),'FailureBarrierTest'],capture_output=True,text=True,check=True)
    print(result.stdout,end='')
if args.report:
    args.report.parent.mkdir(parents=True,exist_ok=True)
    args.report.write_text(json.dumps({'cases':8,'passed':8,'scope':'Actual production TaskFailures.java, real Java futures and threads; Minecraft/runtime evidence is separate'},indent=2)+'\n')
