package net.vulkanmod.vulkan.shader;
import net.vulkanmod.vulkan.device.*;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import java.util.*;
import static org.lwjgl.vulkan.VK10.*;

/** Immutable real Vulkan input layout; buffer identities/offsets remain draw-time state. */
public record NativeVertexLayout(List<Binding> bindings,List<Attribute> attributes) {
    public record Binding(int index,int stride,int divisor){}
    public record Attribute(int location,int binding,int format,int offset,int bytes,boolean integer){}
    public NativeVertexLayout {
        bindings=List.copyOf(bindings);attributes=List.copyOf(attributes);Set<Integer> seen=new HashSet<>();
        for(var b:bindings){if(b.index<0||b.stride<0||!seen.add(b.index))throw new IllegalArgumentException("Invalid native vertex binding");NativeVertexDivisors.validate(b.divisor);}
        Set<Integer> locations=new HashSet<>();for(var a:attributes)if(!seen.contains(a.binding)||a.location<0||a.offset<0||a.bytes<=0||!locations.add(a.location))throw new IllegalArgumentException("Invalid native attribute layout");
    }
    public void shader(NativeUiProgram program){for(var input:program.attributes()){
        var a=attributes.stream().filter(x->x.location==input.location()).findFirst().orElseThrow(()->new IllegalArgumentException("Native shader attribute is disabled/absent: "+input.name()));
        boolean integer=input.type().equals("int")||input.type().startsWith("ivec");if(integer!=a.integer)throw new IllegalArgumentException("Native shader integer/float input mismatch: "+input.name());
    }}
    public void apply(VkPipelineVertexInputStateCreateInfo info,MemoryStack stack){
        var b=VkVertexInputBindingDescription.calloc(bindings.size(),stack);for(int i=0;i<bindings.size();i++){var x=bindings.get(i);b.get(i).binding(x.index).stride(x.stride).inputRate(x.divisor==0?VK_VERTEX_INPUT_RATE_VERTEX:VK_VERTEX_INPUT_RATE_INSTANCE);}info.pVertexBindingDescriptions(b);
        var a=VkVertexInputAttributeDescription.calloc(attributes.size(),stack);for(int i=0;i<attributes.size();i++){var x=attributes.get(i);a.get(i).location(x.location).binding(x.binding).format(x.format).offset(x.offset);}info.pVertexAttributeDescriptions(a);
        var special=bindings.stream().filter(x->Integer.toUnsignedLong(x.divisor)>1).toList();if(!special.isEmpty()){
            var d=VkVertexInputBindingDivisorDescriptionEXT.calloc(special.size(),stack);for(int i=0;i<special.size();i++){var x=special.get(i);d.get(i).binding(x.index).divisor(x.divisor);}
            var state=VkPipelineVertexInputDivisorStateCreateInfoEXT.calloc(stack).sType$Default().pVertexBindingDivisors(d);info.pNext(state.address());
        }
    }
}
