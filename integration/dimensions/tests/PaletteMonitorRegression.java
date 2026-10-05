import com.axalotl.async.common.mixin.world.PalettedContainerMixin;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Real-thread exclusion and exception tests against the production wrappers. */
public final class PaletteMonitorRegression {
    static final PalettedContainerMixin<Object> target = new PalettedContainerMixin<>() {};
    static final Map<String, Method> methods = new HashMap<>();
    static final long[] values = new long[2];
    static Object call(String name, Operation<?> op) {
        Method m=methods.get(name);
        Object[] args=new Object[m.getParameterCount()];
        Class<?>[] types=m.getParameterTypes();
        for(int i=0;i<args.length-1;i++) if(types[i]==int.class) args[i]=0;
        args[args.length-1]=op;
        try {return m.invoke(target,args);}
        catch(InvocationTargetException e) {
            if(e.getCause() instanceof RuntimeException r) throw r;
            if(e.getCause() instanceof Error error) throw error;
            throw new AssertionError(e.getCause());
        } catch(ReflectiveOperationException e) {throw new AssertionError(e);}
    }
    static void check(boolean value,String message) {if(!value)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception {
        for(Method m:PalettedContainerMixin.class.getDeclaredMethods()) {
            m.setAccessible(true);methods.put(m.getName(),m);
        }
        check(methods.size()==11,"wrapper coverage changed");
        CountDownLatch reading=new CountDownLatch(1), release=new CountDownLatch(1), trying=new CountDownLatch(1);
        AtomicBoolean mutation=new AtomicBoolean();AtomicReference<Throwable> failure=new AtomicReference<>();
        Thread reader=new Thread(()->{
            try {call("harimt$pack",a->{
                long first=values[0];reading.countDown();
                try {check(release.await(5,TimeUnit.SECONDS),"snapshot timeout");}catch(InterruptedException e){throw new AssertionError(e);}
                check(first==values[1],"torn snapshot");return null;
            });}catch(Throwable t){failure.set(t);}
        });
        Thread writer=new Thread(()->{
            trying.countDown();call("harimt$set",a->{values[0]=1;values[1]=1;mutation.set(true);return null;});
        });
        reader.start();check(reading.await(5,TimeUnit.SECONDS),"reader did not start");writer.start();
        check(trying.await(5,TimeUnit.SECONDS),"writer did not start");Thread.sleep(100);
        boolean protectedSnapshot=!mutation.get();release.countDown();reader.join(5000);writer.join(5000);
        check(protectedSnapshot,"snapshot writer exclusion");check(failure.get()==null,"snapshot failure: "+failure.get());
        check(!reader.isAlive()&&!writer.isAlive(),"deadlock");
        Object token=new Object();
        for(String name:methods.keySet()) {
            Object answer=call(name,a->{check(Thread.holdsLock(target),"unprotected "+name);return name.equals("harimt$maybeHas")?Boolean.FALSE:(name.equals("harimt$get")||name.contains("getAndSet")?token:null);});
            if(name.equals("harimt$get")||name.contains("getAndSet"))check(answer==token,"return value changed");
            RuntimeException expected=new RuntimeException("expected");
            try {call(name,a->{throw expected;});throw new AssertionError("exception swallowed");}
            catch(RuntimeException actual){check(actual==expected,"exception identity changed");}
        }
        call("harimt$getAndSet",a->call("harimt$getAndSetUnchecked",b->call("harimt$get",c->token)));
        AtomicReference<Throwable> bad=new AtomicReference<>();ExecutorService pool=Executors.newFixedThreadPool(24);
        List<Future<?>> jobs=new ArrayList<>();
        for(int thread=0;thread<24;thread++) {
            final int id=thread;jobs.add(pool.submit(()->{
                try {for(int i=0;i<5000;i++) {
                    if(id%3==0)call("harimt$set",a->{long next=values[0]+1;values[0]=next;Thread.yield();values[1]=next;return null;});
                    else call("harimt$copy",a->{check(values[0]==values[1],"inconsistent concurrent copy");return null;});
                }}catch(Throwable t){bad.compareAndSet(null,t);}
            }));
        }
        for(Future<?> job:jobs)job.get(20,TimeUnit.SECONDS);pool.shutdown();
        check(pool.awaitTermination(5,TimeUnit.SECONDS)&&bad.get()==null,"concurrent failure: "+bad.get());
        check(values[0]==40001&&values[1]==40001,"mutation lost");
        System.out.println("PALETTE_MONITOR_11_WRAPPERS_REENTRANT_EXCEPTION_SNAPSHOT_120000_OPERATIONS_PASS");
    }
}
