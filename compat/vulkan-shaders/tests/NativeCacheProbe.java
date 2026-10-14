package net.irisshaders.iris.vulkan.shader;
import java.nio.*;
public final class NativeCacheProbe {
    static void require(boolean value,String why){if(!value)throw new AssertionError(why);}
    public static void main(String[] ignored){
        NativeSpirvCache<String> cache=new NativeSpirvCache<>();ByteBuffer input=ByteBuffer.allocate(16).order(ByteOrder.nativeOrder());input.putInt(7);input.flip();
        cache.put("a",input,1);require(input.position()==0,"put preserves source cursor");input.putInt(0,9);
        ByteBuffer first=cache.get("a");require(first.isDirect()&&first.getInt()==7,"owned independent input");first.putInt(0,23);first.position(first.limit());
        ByteBuffer second=cache.get("a");require(second.remaining()==4&&second.getInt()==7,"owned independent output");
        for(int i=0;i<200;i++)cache.put("key"+i,input,20);require(cache.size()==128,"entry bound");require(cache.get("a")==null,"LRU eviction");
        cache.get("key72");cache.put("new",input,3);require(cache.get("key72")!=null&&cache.get("key73")==null,"access order");
        cache.clear();require(cache.size()==0&&cache.payloadBytes()==0,"clear ownership");
        ByteBuffer large=ByteBuffer.allocate(9*1024*1024);cache.put("one",large,1);cache.put("two",large,1);require(cache.size()==1&&cache.payloadBytes()<=16*1024*1024L,"byte bound");
        cache.put("oversize",large,9*1024*1024L);require(cache.size()==1&&cache.get("oversize")==null,"oversize not retained");
        try{cache.put("bad",input,-1);throw new AssertionError("negative weight accepted");}catch(IllegalArgumentException expected){}
        System.out.println("NATIVE_SPIRV_CACHE_OWNERSHIP_BOUNDS_PASS");
    }
}
