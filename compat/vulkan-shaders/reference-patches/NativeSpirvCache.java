/* SPDX-License-Identifier: LGPL-3.0-only */
package net.irisshaders.iris.vulkan.shader;
import org.lwjgl.BufferUtils;
import java.nio.ByteBuffer;
import java.util.*;

/** Bounded heap-owned bytecode. Every returned direct buffer has independent content and cursor. */
final class NativeSpirvCache<K> {
    private static final int MAX_ENTRIES=128;
    private static final long MAX_PAYLOAD_BYTES=16L*1024*1024;
    private record Entry(byte[] bytes,long weight){}
    private final LinkedHashMap<K,Entry> cache=new LinkedHashMap<>(16,.75f,true);
    private long payloadBytes;
    synchronized ByteBuffer get(K key){
        Entry found=cache.get(key);if(found==null)return null;
        ByteBuffer result=BufferUtils.createByteBuffer(found.bytes.length);result.put(found.bytes);result.flip();return result;
    }
    synchronized void put(K key,ByteBuffer source,long keyCharacters){
        Objects.requireNonNull(key);Objects.requireNonNull(source);
        if(keyCharacters<0)throw new IllegalArgumentException("Negative source identity weight");
        long weight=Math.addExact(source.remaining(),Math.multiplyExact(keyCharacters,2L));
        Entry old=cache.remove(key);if(old!=null)payloadBytes-=old.weight;
        if(weight>MAX_PAYLOAD_BYTES)return;
        byte[] bytes=new byte[source.remaining()];source.duplicate().get(bytes);
        cache.put(key,new Entry(bytes,weight));payloadBytes+=weight;
        while(cache.size()>MAX_ENTRIES||payloadBytes>MAX_PAYLOAD_BYTES){
            var first=cache.entrySet().iterator();Entry removed=first.next().getValue();first.remove();payloadBytes-=removed.weight;
        }
    }
    synchronized void clear(){cache.clear();payloadBytes=0;}
    synchronized int size(){return cache.size();}
    synchronized long payloadBytes(){return payloadBytes;}
}
