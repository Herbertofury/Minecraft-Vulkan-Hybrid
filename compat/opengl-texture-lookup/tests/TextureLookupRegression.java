import mvhglcompat.TextureLookup;
import java.util.concurrent.*;
import java.util.function.*;
import java.util.*;
public final class TextureLookupRegression {
 public static void main(String[] args)throws Exception{
  Thread owner=Thread.currentThread();ArrayDeque<Runnable> queue=new ArrayDeque<>();Object expected=new Object();int[] lookups={0};
  Supplier<Object> work=()->{if(Thread.currentThread()!=owner)throw new AssertionError("texture lookup left owner");lookups[0]++;return expected;};
  Function<Supplier<Object>,CompletableFuture<Object>> submit=w->{CompletableFuture<Object> f=new CompletableFuture<>();queue.add(()->{try{f.complete(w.get());}catch(Throwable t){f.completeExceptionally(t);}});return f;};
  CompletableFuture<Object> broken=submit.apply(work);try{broken.get(5,TimeUnit.MILLISECONDS);throw new AssertionError("negative control unexpectedly progressed");}catch(TimeoutException wanted){}queue.remove().run();
  CompletableFuture<Object> fixed=TextureLookup.submit(true,work,submit);if(fixed.get()!=expected||!queue.isEmpty())throw new AssertionError("owner self-queued or result changed");
  CompletableFuture<Object> other=TextureLookup.submit(false,work,submit);if(other.isDone()||queue.size()!=1)throw new AssertionError("off-owner path changed");queue.remove().run();if(other.get()!=expected)throw new AssertionError("off-owner result changed");
  RuntimeException failure=new RuntimeException("intentional");try{TextureLookup.submit(true,()->{throw failure;},submit).get();throw new AssertionError("failure swallowed");}catch(ExecutionException e){if(e.getCause()!=failure)throw new AssertionError("cause changed");}
  if(lookups[0]!=3)throw new AssertionError("lookup duplicated");System.out.println("PASS: stalled original queue control; owner identity; single execution; ordinary queue; original exception cause");
 }
}
