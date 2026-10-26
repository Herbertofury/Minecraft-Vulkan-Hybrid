import mvhgrasscompat.MemoizedDistanceSort;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongToDoubleFunction;

public final class MemoizedDistanceSortRegression {
    static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    static double distance(long v, double x, double y, double z) {
        if (v % 31 == 0) return Double.NaN;
        if (v % 29 == 0) return Double.POSITIVE_INFINITY;
        if (v % 23 == 0) return v > 0 ? 0.0 : -0.0;
        double a = (v % 101) * 16 + 8 - x, b = (v % 79) * 16 + 8 - y,
               c = (v % 67) * 16 + 8 - z;
        return a*a + b*b + c*c;
    }
    static void reference(long[] values, int size, LongToDoubleFunction distance) {
        Long[] boxed = new Long[size];
        for (int i=0;i<size;i++) boxed[i]=values[i];
        Arrays.sort(boxed,(a,b)->Double.compare(distance.applyAsDouble(a),distance.applyAsDouble(b)));
        for (int i=0;i<size;i++) values[i]=boxed[i];
    }
    public static void main(String[] args) {
        Random random = new Random(1729);
        MemoizedDistanceSort sorter = new MemoizedDistanceSort();
        int checks=0;
        for (int trial=0;trial<5000;trial++) {
            int size=2+random.nextInt(256);
            long[] original=new long[size+7];
            for(int i=0;i<original.length;i++) original[i]=random.nextInt(10000)-5000;
            double x=random.nextDouble()*100,y=random.nextDouble()*100,z=random.nextDouble()*100;
            LongToDoubleFunction f=v->distance(v,x,y,z);
            long[] expected=original.clone();reference(expected,size,f);
            AtomicInteger calls=new AtomicInteger();
            for(int repeat=0;repeat<3;repeat++) {
                long[] actual=original.clone();
                sorter.sort(actual,size,x,y,z,v->{calls.incrementAndGet();return f.applyAsDouble(v);});
                check(Arrays.equals(actual,expected),"stable repeat/tail mismatch");
                check(calls.get()==size,"unchanged pure sort was recomputed or first sort omitted");
                checks++;
            }
            long[] changed=original.clone();changed[random.nextInt(size)]++;
            long[] changedExpected=changed.clone();reference(changedExpected,size,f);
            sorter.sort(changed,size,x,y,z,f);
            check(Arrays.equals(changed,changedExpected),"changed membership/order hit stale permutation");
            long[] moved=original.clone(),movedExpected=original.clone();
            LongToDoubleFunction moving=v->distance(v,x+.0000001,y,z);
            // Prime the identical input again so only camera bits change on the next call.
            sorter.sort(original.clone(),size,x,y,z,f);
            AtomicInteger movingCalls=new AtomicInteger();
            reference(movedExpected,size,moving);sorter.sort(moved,size,x+.0000001,y,z,v->{movingCalls.incrementAndGet();return moving.applyAsDouble(v);});
            check(Arrays.equals(moved,movedExpected),"camera movement reused stale distance order");
            check(movingCalls.get()==size,"changed camera skipped the original distance evaluation");
            long[] prefix=original.clone(),prefixExpected=original.clone();
            reference(prefixExpected,size-1,f);sorter.sort(prefix,size-1,x,y,z,f);
            check(Arrays.equals(prefix,prefixExpected),"prefix bounds changed");
            checks+=3;
        }
        long[] signed={9,4,1};AtomicInteger calls=new AtomicInteger();
        sorter.sort(signed.clone(),3,0.0,0,0,v->{calls.incrementAndGet();return v;});
        sorter.sort(signed.clone(),3,-0.0,0,0,v->{calls.incrementAndGet();return v;});
        check(calls.get()==6,"camera signed-zero bits not retained");
        RuntimeException failure=new RuntimeException("distance failed");
        sorter.sort(new long[]{1,2},2,3,4,5,v->-v);
        try { sorter.sort(new long[]{77,88},2,3,4,5,v->{throw failure;});throw new AssertionError("failure swallowed"); }
        catch(RuntimeException e){check(e==failure,"failure replaced");}
        long[] recovered={77,88};sorter.sort(recovered,2,3,4,5,v->-v);
        check(Arrays.equals(recovered,new long[]{88,77}),"failed sort poisoned later result");
        long[] large=new long[65537];for(int i=0;i<large.length;i++)large[i]=large.length-i;
        sorter.sort(large,large.length,0,0,0,v->v);
        for(int i=0;i<large.length;i++)check(large[i]==i+1,"large input order changed");
        check(sorter.retainedCapacity()<=65536,"unbounded retained keys");
        // Independent owners must never share their input/permutation state.
        MemoizedDistanceSort other=new MemoizedDistanceSort();long[] distinct={3,2,1};
        other.sort(distinct,3,0,0,0,v->v);check(Arrays.equals(distinct,new long[]{1,2,3}),"owner state coupled");
        System.out.println("EXACT_MEMOIZED_ORDER_CAMERA_MEMBERSHIP_PREFIX_FAILURE_OWNER_BOUNDS_PASS checks="+checks);
    }
}
