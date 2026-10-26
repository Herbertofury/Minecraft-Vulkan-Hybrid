import mvhgrasscompat.DistanceSort;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongToDoubleFunction;
public final class DistanceSortRegression {
 static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 public static void main(String[] args){
  Random random=new Random(1729);DistanceSort sorter=new DistanceSort();int cases=0;
  for(int trial=0;trial<10000;trial++){
   int size=random.nextInt(256);long[] values=new long[size+5];Long[] reference=new Long[size];
   for(int i=0;i<values.length;i++){values[i]=random.nextInt(10000)-5000;if(i<size)reference[i]=values[i];}
   long[] tail=Arrays.copyOfRange(values,size,values.length);double camera=random.nextDouble()*100;
   LongToDoubleFunction distance=v->{if(v%31==0)return Double.NaN;if(v%29==0)return Double.POSITIVE_INFINITY;if(v%23==0)return v>0?0.0:-0.0;double x=(v%101)*16+8-camera;return x*x;};
   Arrays.sort(reference,(a,b)->Double.compare(distance.applyAsDouble(a),distance.applyAsDouble(b)));
   AtomicInteger calls=new AtomicInteger();sorter.sort(values,size,v->{calls.incrementAndGet();return distance.applyAsDouble(v);});
   for(int i=0;i<size;i++)check(values[i]==reference[i],"stable ordering mismatch "+trial+" "+i);
   check(Arrays.equals(tail,Arrays.copyOfRange(values,size,values.length)),"unused list capacity changed");
   check(calls.get()==(size<2?0:size),"distance recomputed during comparisons");cases++;
  }
  long[] large=new long[65537];for(int i=0;i<large.length;i++)large[i]=large.length-i;
  sorter.sort(large,large.length,v->v);for(int i=0;i<large.length;i++)check(large[i]==i+1,"large ordering mismatch");
  check(sorter.retainedCapacity()<=65536,"unbounded retained scratch");
  System.out.println("EXACT_STABLE_DISTANCE_ORDER_PREFIX_NAN_INFINITY_SIGNED_ZERO_BOUNDED_SCRATCH_PASS cases="+cases);
 }
}
