package mvhgrasscompat;

import java.util.function.LongToDoubleFunction;

/** Stable ordering by each entry's exact original distance, computed once per call. */
public final class DistanceSort {
    private static final int RETAIN_LIMIT=65536;
    private double[] keys=new double[0],temporaryKeys=new double[0];
    private long[] temporaryValues=new long[0];
    public int retainedCapacity(){return keys.length;}
    public void sort(long[] values,int size,LongToDoubleFunction distance) {
        if(size<0||size>values.length)throw new IndexOutOfBoundsException();
        if(size<2)return;
        double[] distances,workKeys;long[] workValues;
        if(size<=RETAIN_LIMIT) {
            if(keys.length<size){int capacity=Math.min(RETAIN_LIMIT,Math.max(size,Math.max(16,keys.length*2)));keys=new double[capacity];temporaryKeys=new double[capacity];temporaryValues=new long[capacity];}
            distances=keys;workKeys=temporaryKeys;workValues=temporaryValues;
        } else {distances=new double[size];workKeys=new double[size];workValues=new long[size];}
        for(int i=0;i<size;i++)distances[i]=distance.applyAsDouble(values[i]);
        for(int width=1;width<size;width=(width>size/2?size:width*2)) {
            for(int left=0;left<size;left+=Math.min(size-left,width*2)) {
                int middle=Math.min(size,left+width),end=Math.min(size,middle+width),a=left,b=middle;
                for(int out=left;out<end;out++) {
                    int chosen=(a<middle&&(b>=end||Double.compare(distances[a],distances[b])<=0))?a++:b++;
                    workValues[out]=values[chosen];workKeys[out]=distances[chosen];
                }
            }
            System.arraycopy(workValues,0,values,0,size);System.arraycopy(workKeys,0,distances,0,size);
        }
    }
}
