package mvhgrasscompat;

import java.util.Arrays;

/** Bounded pure AABB visibility cache. Every matrix and camera bit participates in invalidation. */
public final class ExactFrustumCache {
    public static long CACHE_HITS,CACHE_MISSES,STATE_CAPTURES,STATE_CHANGES;
    private static final int SIZE=16384,MASK=SIZE-1;
    private final int[] matrix=new int[16],stamp=new int[SIZE];
    private final long[] camera=new long[3],bounds=new long[SIZE*6];
    private final byte[] results=new byte[SIZE];
    private int epoch=1,entries;
    private boolean initialized;
    public void state(float[] values,double x,double y,double z){
        STATE_CAPTURES++;
        if(values.length!=16)throw new IllegalArgumentException("Frustum matrix length");
        boolean changed=!initialized;
        for(int i=0;i<16;i++)changed|=matrix[i]!=Float.floatToRawIntBits(values[i]);
        changed|=camera[0]!=Double.doubleToRawLongBits(x)||camera[1]!=Double.doubleToRawLongBits(y)||camera[2]!=Double.doubleToRawLongBits(z);
        if(!changed)return;
        STATE_CHANGES++;clear();for(int i=0;i<16;i++)matrix[i]=Float.floatToRawIntBits(values[i]);
        camera[0]=Double.doubleToRawLongBits(x);camera[1]=Double.doubleToRawLongBits(y);camera[2]=Double.doubleToRawLongBits(z);initialized=true;
    }
    public void clear(){if(++epoch==0){Arrays.fill(stamp,0);epoch=1;}entries=0;initialized=false;}
    public byte lookup(double a,double b,double c,double d,double e,double f){
        if(!initialized)return 0;
        int slot=slot(a,b,c,d,e,f);if(stamp[slot]==epoch){CACHE_HITS++;return results[slot];}CACHE_MISSES++;return 0;
    }
    public void put(double a,double b,double c,double d,double e,double f,boolean visible){
        if(!initialized)return;
        if(entries>=SIZE/2){boolean was=initialized;clear();initialized=was;}
        int slot=slot(a,b,c,d,e,f),base=slot*6;
        if(stamp[slot]!=epoch)entries++;
        bounds[base]=Double.doubleToRawLongBits(a);bounds[base+1]=Double.doubleToRawLongBits(b);bounds[base+2]=Double.doubleToRawLongBits(c);
        bounds[base+3]=Double.doubleToRawLongBits(d);bounds[base+4]=Double.doubleToRawLongBits(e);bounds[base+5]=Double.doubleToRawLongBits(f);
        results[slot]=(byte)(visible?2:1);stamp[slot]=epoch;
    }
    private int slot(double a,double b,double c,double d,double e,double f){
        long aa=Double.doubleToRawLongBits(a),bb=Double.doubleToRawLongBits(b),cc=Double.doubleToRawLongBits(c),dd=Double.doubleToRawLongBits(d),ee=Double.doubleToRawLongBits(e),ff=Double.doubleToRawLongBits(f);
        long hash=aa;hash=hash*31+bb;hash=hash*31+cc;hash=hash*31+dd;hash=hash*31+ee;hash=hash*31+ff;hash^=hash>>>33;hash*=0xff51afd7ed558ccdL;hash^=hash>>>33;
        int slot=(int)hash&MASK;
        while(stamp[slot]==epoch){int p=slot*6;if(bounds[p]==aa&&bounds[p+1]==bb&&bounds[p+2]==cc&&bounds[p+3]==dd&&bounds[p+4]==ee&&bounds[p+5]==ff)return slot;slot=(slot+1)&MASK;}
        return slot;
    }
}
