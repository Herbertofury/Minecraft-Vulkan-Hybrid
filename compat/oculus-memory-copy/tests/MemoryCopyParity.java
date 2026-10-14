/* SPDX-License-Identifier: LGPL-3.0-only */
import java.lang.reflect.*;
import java.util.Random;
import sun.misc.Unsafe;

/** Actual original API bytecode versus replacement on bounded real native buffers. */
public final class MemoryCopyParity {
    public static void main(String[] args)throws Exception {
        Field field=Unsafe.class.getDeclaredField("theUnsafe");field.setAccessible(true);Unsafe memory=(Unsafe)field.get(null);
        Random random=new Random(1729);int trials=0;
        for(int length:new int[]{0,1,2,3,4,7,8,15,16,31,32,63,64,127,128,255,256,511,512,4096,65537})
            for(int offset=0;offset<8;offset++) {
                int capacity=length+32;long source=memory.allocateMemory(capacity),old=memory.allocateMemory(capacity),fixed=memory.allocateMemory(capacity);
                try {
                    for(int i=0;i<capacity;i++){memory.putByte(source+i,(byte)random.nextInt());memory.putByte(old+i,(byte)0x5a);memory.putByte(fixed+i,(byte)0x5a);}
                    net.caffeinemc.mods.sodium.api.memory.MemoryIntrinsics.copyMemory(source+offset,old+offset,length);
                    mvhoculuscompat.MemoryCopy.copyMemory(source+offset,fixed+offset,length);
                    for(int i=0;i<capacity;i++) {
                        byte a=memory.getByte(old+i),b=memory.getByte(fixed+i);
                        if(a!=b||((i<offset||i>=offset+length)&&b!=(byte)0x5a))throw new AssertionError("copy mismatch or guard changed");
                    }
                    Class<?> originalFailure=null,fixedFailure=null;
                    try {net.caffeinemc.mods.sodium.api.memory.MemoryIntrinsics.copyMemory(source,old,-1);}catch(Throwable t){originalFailure=t.getClass();}
                    try {mvhoculuscompat.MemoryCopy.copyMemory(source,fixed,-1);}catch(Throwable t){fixedFailure=t.getClass();}
                    if(originalFailure!=IllegalArgumentException.class||fixedFailure!=originalFailure)throw new AssertionError("failure behavior changed");
                    trials++;
                } finally {memory.freeMemory(source);memory.freeMemory(old);memory.freeMemory(fixed);}
            }
        System.out.println("ORIGINAL_MEMORY_API_PARITY_"+trials+"_SIZES_OFFSETS_GUARDS_NEGATIVE_COUNTS_PASS");
    }
}
