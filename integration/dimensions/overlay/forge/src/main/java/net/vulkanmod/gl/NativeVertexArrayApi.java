package net.vulkanmod.gl;
import com.mojang.blaze3d.systems.RenderSystem;
import net.vulkanmod.compat.UniversalRendererGate;
import net.vulkanmod.vulkan.device.*;
import net.vulkanmod.vulkan.shader.*;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import java.nio.IntBuffer;
import java.util.*;
import static org.lwjgl.vulkan.VK10.*;

/** Native VAO state and actual input layouts. No fabricated OpenGL context/capabilities. */
public final class NativeVertexArrayApi {
    static final class Binding {NativePixelBufferApi.Entry buffer;int name,stride=16,divisor;long offset;}
    private record Format(int vk,int bytes,boolean integer){}
    static final class Attribute {int binding,offset;Format format=new Format(VK_FORMAT_R32G32B32A32_SFLOAT,16,false);boolean enabled;Attribute(int index){binding=index;}}
    static final class Array {final Map<Integer,Binding> bindings=new TreeMap<>();final Map<Integer,Attribute> attributes=new TreeMap<>();NativePixelBufferApi.Entry element;int elementName;}
    private static final Map<Integer,Array> ARRAYS=new HashMap<>();private static int bound;
    private NativeVertexArrayApi(){}
    private static void owner(){if(!UniversalRendererGate.vulkanRendererEnabled()||DeviceManager.vkDevice==null)throw new IllegalStateException("Native vertex device unavailable");RenderSystem.assertOnRenderThread();}
    private static Array array(){owner();Array a=ARRAYS.get(bound);if(a==null)throw new IllegalStateException("No owned native vertex array bound");return a;}
    private static boolean slot(int index,boolean attribute){int max=attribute?DeviceManager.deviceProperties.limits().maxVertexInputAttributes():DeviceManager.deviceProperties.limits().maxVertexInputBindings();if(index>=0&&index<max)return true;NativeGlErrors.record(1281);return false;}
    private static Binding binding(Array a,int index){return a.bindings.computeIfAbsent(index,x->new Binding());}
    private static Attribute attribute(Array a,int index){return a.attributes.computeIfAbsent(index,Attribute::new);}
    public static int glGenVertexArrays(){owner();int id=GlVertexArray.genVertexArray();ARRAYS.put(id,new Array());return id;}
    public static void glBindVertexArray(int id){owner();if(id!=0&&!ARRAYS.containsKey(id))throw new UnsupportedOperationException("Unknown native vertex array");bound=id;GlVertexArray.bindVertexArray(id);}
    public static void glDeleteVertexArrays(int id){owner();var a=ARRAYS.remove(id);if(a!=null){for(var b:a.bindings.values())release(b.buffer);release(a.element);}if(bound==id)bound=0;GlVertexArray.deleteVertexArray(id);}
    private static NativePixelBufferApi.Entry borrow(int name){if(name==0)return null;var e=NativePixelBufferApi.ENTRIES.get(name);if(e==null)return null;e.vertexReferences++;return e;}
    private static void release(NativePixelBufferApi.Entry e){if(e==null)return;if(--e.vertexReferences<0)throw new IllegalStateException("Native vertex reference retired twice");if(e.deleted&&e.vertexReferences==0&&e.store!=null)e.store.delete();}
    static void nameDeleted(int name,NativePixelBufferApi.Entry e){var a=ARRAYS.get(bound);if(a==null)return;for(var b:a.bindings.values())if(b.buffer==e){release(b.buffer);b.buffer=null;b.name=0;b.offset=0;b.stride=16;}if(a.element==e){release(a.element);a.element=null;a.elementName=0;}}
    static void elementBound(int name){var a=ARRAYS.get(bound);if(a==null)return;var e=borrow(name);if(name!=0&&e==null)throw new UnsupportedOperationException("Foreign native index name");release(a.element);a.element=e;a.elementName=name;}
    public static void glEnableVertexAttribArray(int index){var a=array();if(slot(index,true))attribute(a,index).enabled=true;}
    public static void glDisableVertexAttribArray(int index){var a=array();if(slot(index,true))attribute(a,index).enabled=false;}
    public static void glBindVertexBuffer(int index,int name,long offset,int stride){
        var a=array();if(!slot(index,false))return;if(offset<0||stride<0||stride>DeviceManager.deviceProperties.limits().maxVertexInputBindingStride()){NativeGlErrors.record(1281);return;}
        var e=borrow(name);if(name!=0&&e==null){NativeGlErrors.record(1282);return;}var b=binding(a,index);release(b.buffer);b.buffer=e;b.name=name;b.offset=name==0?0:offset;b.stride=name==0?16:stride;
    }
    public static void glVertexAttribBinding(int attribute,int bind){var a=array();if(slot(attribute,true)&&slot(bind,false))attribute(a,attribute).binding=bind;}
    public static void glVertexBindingDivisor(int index,int divisor){var a=array();if(!slot(index,false))return;NativeVertexDivisors.validate(divisor);binding(a,index).divisor=divisor;}
    public static void glVertexAttribDivisor(int index,int divisor){var a=array();if(!slot(index,true)||!slot(index,false))return;glVertexBindingDivisor(index,divisor);attribute(a,index).binding=index;}
    public static void glVertexAttribDivisorARB(int index,int divisor){glVertexAttribDivisor(index,divisor);}
    private static Format format(int count,int type,boolean normalized,boolean integer){
        if(count<1||count>4)throw new IllegalArgumentException("Native attribute component count");int[] formats;int width;
        if(type==0x1406&&!integer){formats=new int[]{VK_FORMAT_R32_SFLOAT,VK_FORMAT_R32G32_SFLOAT,VK_FORMAT_R32G32B32_SFLOAT,VK_FORMAT_R32G32B32A32_SFLOAT};width=4;}
        else if(type==0x1404&&integer){formats=new int[]{VK_FORMAT_R32_SINT,VK_FORMAT_R32G32_SINT,VK_FORMAT_R32G32B32_SINT,VK_FORMAT_R32G32B32A32_SINT};width=4;}
        else if(type==0x1401&&!integer){formats=normalized?new int[]{VK_FORMAT_R8_UNORM,VK_FORMAT_R8G8_UNORM,VK_FORMAT_R8G8B8_UNORM,VK_FORMAT_R8G8B8A8_UNORM}:new int[]{VK_FORMAT_R8_USCALED,VK_FORMAT_R8G8_USCALED,VK_FORMAT_R8G8B8_USCALED,VK_FORMAT_R8G8B8A8_USCALED};width=1;}
        else if(type==0x1400){formats=integer?new int[]{VK_FORMAT_R8_SINT,VK_FORMAT_R8G8_SINT,VK_FORMAT_R8G8B8_SINT,VK_FORMAT_R8G8B8A8_SINT}:normalized?new int[]{VK_FORMAT_R8_SNORM,VK_FORMAT_R8G8_SNORM,VK_FORMAT_R8G8B8_SNORM,VK_FORMAT_R8G8B8A8_SNORM}:new int[]{VK_FORMAT_R8_SSCALED,VK_FORMAT_R8G8_SSCALED,VK_FORMAT_R8G8B8_SSCALED,VK_FORMAT_R8G8B8A8_SSCALED};width=1;}
        else if(type==0x1402){formats=integer?new int[]{VK_FORMAT_R16_SINT,VK_FORMAT_R16G16_SINT,VK_FORMAT_R16G16B16_SINT,VK_FORMAT_R16G16B16A16_SINT}:normalized?new int[]{VK_FORMAT_R16_SNORM,VK_FORMAT_R16G16_SNORM,VK_FORMAT_R16G16B16_SNORM,VK_FORMAT_R16G16B16A16_SNORM}:new int[]{VK_FORMAT_R16_SSCALED,VK_FORMAT_R16G16_SSCALED,VK_FORMAT_R16G16B16_SSCALED,VK_FORMAT_R16G16B16A16_SSCALED};width=2;}
        else if(type==0x1403&&!integer){formats=normalized?new int[]{VK_FORMAT_R16_UNORM,VK_FORMAT_R16G16_UNORM,VK_FORMAT_R16G16B16_UNORM,VK_FORMAT_R16G16B16A16_UNORM}:new int[]{VK_FORMAT_R16_USCALED,VK_FORMAT_R16G16_USCALED,VK_FORMAT_R16G16B16_USCALED,VK_FORMAT_R16G16B16A16_USCALED};width=2;}
        else if(type==0x140B&&!integer){formats=new int[]{VK_FORMAT_R16_SFLOAT,VK_FORMAT_R16G16_SFLOAT,VK_FORMAT_R16G16B16_SFLOAT,VK_FORMAT_R16G16B16A16_SFLOAT};width=2;}
        else throw new UnsupportedOperationException("Native vertex conversion/precision needs another input variant: "+type);
        int vk=formats[count-1];try(MemoryStack stack=MemoryStack.stackPush()){var props=VkFormatProperties.calloc(stack);vkGetPhysicalDeviceFormatProperties(DeviceManager.physicalDevice,vk,props);if((props.bufferFeatures()&VK_FORMAT_FEATURE_VERTEX_BUFFER_BIT)==0)throw new UnsupportedOperationException("Native vertex format not supported by device");}return new Format(vk,width*count,integer);
    }
    private static void attributeFormat(int index,int count,int type,boolean norm,int offset,boolean integer){var a=array();if(!slot(index,true))return;if(offset<0||offset>DeviceManager.deviceProperties.limits().maxVertexInputAttributeOffset()){NativeGlErrors.record(1281);return;}var f=format(count,type,norm,integer);var at=attribute(a,index);at.format=f;at.offset=offset;}
    public static void glVertexAttribFormat(int index,int count,int type,boolean normalized,int offset){attributeFormat(index,count,type,normalized,offset,false);}
    public static void glVertexAttribIFormat(int index,int count,int type,int offset){attributeFormat(index,count,type,false,offset,true);}
    public static void glBindVertexBuffers(int first,IntBuffer buffers,PointerBuffer offsets,IntBuffer strides){glBindVertexBuffers(first,buffers==null?0:buffers.remaining(),buffers,offsets,strides);}
    public static void glBindVertexBuffers(int first,int count,IntBuffer buffers,PointerBuffer offsets,IntBuffer strides){
        array();if(count<0){NativeGlErrors.record(1281);return;}if(first<0||(long)first+count>DeviceManager.deviceProperties.limits().maxVertexInputBindings()){NativeGlErrors.record(1282);return;}
        if(buffers!=null&&(buffers.remaining()<count||offsets==null||offsets.remaining()<count||strides==null||strides.remaining()<count))throw new IllegalArgumentException("Native bulk binding input extent");
        for(int i=0;i<count;i++)glBindVertexBuffer(first+i,buffers==null?0:buffers.get(buffers.position()+i),buffers==null?0:offsets.get(offsets.position()+i),buffers==null?16:strides.get(strides.position()+i));
    }
    public static NativeVertexLayout layout(){var a=array();List<NativeVertexLayout.Attribute> attrs=new ArrayList<>();Set<Integer> used=new TreeSet<>();for(var entry:a.attributes.entrySet()){var v=entry.getValue();if(!v.enabled)continue;used.add(v.binding);attrs.add(new NativeVertexLayout.Attribute(entry.getKey(),v.binding,v.format.vk,v.offset,v.format.bytes,v.format.integer));}List<NativeVertexLayout.Binding> binds=new ArrayList<>();for(int index:used){var b=binding(a,index);binds.add(new NativeVertexLayout.Binding(index,b.stride,b.divisor));}return new NativeVertexLayout(binds,attrs);}
    public static void glDrawArrays(int mode,int first,int count){glDrawArraysInstanced(mode,first,count,1);}
    public static void glDrawArraysInstanced(int mode,int first,int count,int instances){var a=array();if(first<0||count<0||instances<0){NativeGlErrors.record(1281);return;}if(count==0||instances==0)return;var layout=layout();Map<Integer,NativePixelBufferApi.Store> stores=new TreeMap<>();Map<Integer,Long> offsets=new TreeMap<>();for(var b:layout.bindings()){
        var state=binding(a,b.index());long divisor=Integer.toUnsignedLong(b.divisor());long last=divisor==0?(long)first+count-1:(instances-1)/divisor;int end=layout.attributes().stream().filter(x->x.binding()==b.index()).mapToInt(x->x.offset()+x.bytes()).max().orElse(0);long bytes=last*b.stride()+end;stores.put(b.index(),NativeBufferObjectApi.drawEntry(state.buffer,state.offset,bytes,VK_BUFFER_USAGE_VERTEX_BUFFER_BIT));offsets.put(b.index(),state.offset);
    }NativeUiRenderBridge.drawNativeVertexLayout(layout,stores,offsets,mode,first,count,instances);}
    public static Map<String,Long> snapshot(){owner();var a=ARRAYS.get(bound);return Map.of("arrays",(long)ARRAYS.size(),"bound",(long)bound,"bindings",a==null?0L:(long)a.bindings.size(),"attributes",a==null?0L:(long)a.attributes.size(),"nativeDivisorEnabled",NativeVertexDivisors.enabled()?1L:0L,"nativeMaximumDivisor",NativeVertexDivisors.maximum());}
}
