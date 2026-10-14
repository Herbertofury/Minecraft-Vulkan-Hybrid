import me.srrapero720.dimthread.ParallelThreadSwap;
import me.srrapero720.dimthread.thread.IMutableMainThread;
import me.srrapero720.dimthread.thread.ThreadPool;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public final class OwnershipRegression {
    static class Owner implements IMutableMainThread {
        volatile Thread owner = Thread.currentThread();
        int sets;
        public Thread dimThreads$getMainThread(){return owner;}
        public void dimThreads$setMainThread(Thread t){owner=t;sets++;}
    }
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception {
        Owner a=new Owner(), b=new Owner();Thread original=a.owner;
        RuntimeException expected=new IllegalArgumentException("dimension fault");
        try { ParallelThreadSwap.swapThreadsAndRun(()->{check(a.owner==Thread.currentThread(),"wrong owner");throw expected;},a,a,b);throw new AssertionError(); }
        catch(RuntimeException e){check(e==expected,"changed failure");}
        check(a.owner==original&&b.owner==original&&a.sets==2,"restoration/deduplication");
        CountDownLatch first=new CountDownLatch(1),second=new CountDownLatch(1);
        AtomicReference<Throwable> threadFailure=new AtomicReference<>();
        Thread x=new Thread(()->{try{ParallelThreadSwap.swapThreadsAndRun(()->{first.countDown();try{check(second.await(2,TimeUnit.SECONDS),"unrelated worlds serialized");}catch(InterruptedException e){throw new RuntimeException(e);}},a);}catch(Throwable e){threadFailure.set(e);}});
        Thread y=new Thread(()->{try{first.await();ParallelThreadSwap.swapThreadsAndRun(second::countDown,b);}catch(Throwable e){threadFailure.set(e);}});
        x.start();y.start();x.join(3000);y.join(3000);check(!x.isAlive()&&!y.isAlive()&&threadFailure.get()==null,"ownership deadlock/failure: "+threadFailure.get());
        AtomicInteger ticks=new AtomicInteger();ThreadPool pool=new ThreadPool(3);
        pool.execute(java.util.stream.IntStream.range(0,24),i->ticks.incrementAndGet());pool.awaitCompletion();check(ticks.get()==24,"dimension tick lost/duplicated");
        CountDownLatch tail=new CountDownLatch(1);pool.execute(()->{throw expected;});pool.execute(()->{try{Thread.sleep(25);}catch(InterruptedException e){throw new RuntimeException(e);}tail.countDown();});
        try{pool.awaitCompletion();throw new AssertionError("worker failure hidden");}catch(RuntimeException e){check(e==expected&&tail.getCount()==0,"failure escaped barrier");}
        pool.shutdown();try{pool.execute(()->{});throw new AssertionError("rejected task accepted");}catch(RejectedExecutionException expectedRejection){}
        check(pool.getActiveCount()==0,"rejected task stranded latch");ParallelThreadSwap.clear();
        System.out.println("OWNERSHIP_RESTORATION_DEDUPLICATION_PARALLELISM_TICKS_FAILURE_BARRIER_PASS");
    }
}
