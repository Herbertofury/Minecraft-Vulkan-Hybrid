package net.vulkanmod.vulkan.shader;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;

/** Reads actual optimized SPIR-V accesses; declarations alone do not establish activity. */
final class NativeUniformUse {
    record Uses(Map<Integer,Integer> members, Set<Integer> samplerBindings) {}
    private record Pointer(int member, Integer arrayIndex) {}
    static Uses inspect(ByteBuffer code) {
        ByteBuffer b=code.slice().order(ByteOrder.LITTLE_ENDIAN);
        if(b.remaining()<20 || (b.remaining()&3)!=0 || b.getInt(0)!=0x07230203)
            throw new IllegalArgumentException("Invalid native uniform-use module");
        Map<Integer,Integer> constants=new HashMap<>(),sets=new HashMap<>(),bindings=new HashMap<>(),storage=new HashMap<>();
        List<int[]> instructions=new ArrayList<>();
        for(int at=20;at<b.limit();) {
            int head=b.getInt(at),words=head>>>16,op=head&65535;
            if(words==0 || words>(b.limit()-at)/4)throw new IllegalArgumentException("Invalid native uniform-use instruction");
            int[] a=new int[words];a[0]=op;for(int i=1;i<words;i++)a[i]=b.getInt(at+i*4);
            instructions.add(a);
            if(op==43 && words==4)constants.put(a[2],a[3]);
            if(op==71 && words==4) { if(a[2]==34)sets.put(a[1],a[3]);if(a[2]==33)bindings.put(a[1],a[3]); }
            if(op==59 && words>=4)storage.put(a[2],a[3]);
            at+=words*4;
        }
        Set<Integer> blocks=new HashSet<>(),samplers=new HashSet<>();
        for(var e:storage.entrySet())if(Objects.equals(sets.get(e.getKey()),0)) {
            if(e.getValue()==2 && Objects.equals(bindings.get(e.getKey()),0))blocks.add(e.getKey());
            if(e.getValue()==0 && bindings.containsKey(e.getKey()))samplers.add(bindings.get(e.getKey()));
        }
        Map<Integer,Pointer> pointers=new HashMap<>();Map<Integer,Integer> members=new HashMap<>();
        for(int[] a:instructions) {
            int op=a[0];
            if((op==65 || op==66) && a.length>=5) {
                Pointer p=pointers.get(a[3]);
                if(blocks.contains(a[3])) {
                    Integer member=constants.get(a[4]);
                    if(member==null)throw new IllegalArgumentException("Dynamic uniform structure member");
                    p=new Pointer(member,a.length>5?constants.get(a[5]):null);
                }
                if(p!=null) {
                    pointers.put(a[2],p);
                    int extent=p.arrayIndex==null?Integer.MAX_VALUE:Math.addExact(p.arrayIndex,1);
                    members.merge(p.member,extent,Math::max);
                }
            } else if(op==83 && a.length==4 && pointers.containsKey(a[3]))pointers.put(a[2],pointers.get(a[3]));
            else if(op==61 && a.length>=4 && blocks.contains(a[3]))
                throw new IllegalArgumentException("Whole uniform-block loads need structured activity reflection");
        }
        return new Uses(Map.copyOf(members),Set.copyOf(samplers));
    }
}
