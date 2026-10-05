package dev.engine_room.flywheel.backend.engine.uniform;
import dev.engine_room.flywheel.lib.memory.MemoryBlock;
import dev.engine_room.flywheel.lib.math.MoreMath;
import mvhflywheelbackend.NativeWorldRenderer;
import org.lwjgl.system.MemoryUtil;
import java.nio.ByteBuffer;
/** CPU side of the original uniform writer; actual native upload/bind uses the frame arena. */
public final class UniformBuffer {
 private final int index; private final MemoryBlock clientBuffer; private long revision=1;
 public UniformBuffer(int index,int size){this.index=index;clientBuffer=MemoryBlock.malloc(MoreMath.align16(size));clientBuffer.clear();}
 public long ptr(){return clientBuffer.ptr();}
 public void markDirty(){revision++;}
 public void clear(){clientBuffer.clear();markDirty();}
 public int index(){return index;}
 public ByteBuffer nativeBytes(){return MemoryUtil.memByteBuffer(ptr(),Math.toIntExact(clientBuffer.size()));}
 public long revision(){return revision;}
 public void bind(){NativeWorldRenderer.bindOriginalUniform(this);}
 public void delete(){NativeWorldRenderer.invalidateOriginalUniform(this);}
}
