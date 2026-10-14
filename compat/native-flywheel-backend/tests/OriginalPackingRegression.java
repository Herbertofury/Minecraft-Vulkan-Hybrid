import mvhflywheelbackend.*;
import org.lwjgl.system.MemoryUtil;
import java.util.*;

/** Sparse writers, visibility and duplicate selections against independently generated expected storage. */
public final class OriginalPackingRegression {
    static int checks;
    static void check(boolean value){checks++;if(!value)throw new AssertionError("Original native packing mismatch "+checks);}
    public static void main(String[] args){
        Random random=new Random(167134);
        for(int trial=0;trial<600;trial++){
            NativeEngine.NativeInstancer<Object> instances=new NativeEngine.NativeInstancer<>();
            int slots=trial%67,stride=4*(1+trial%31);
            List<Integer> expected=new ArrayList<>();Set<Integer> written=new HashSet<>();
            for(int i=0;i<slots;i++){
                int state=random.nextInt(5);NativeEngine.Handle h=state==0?null:new NativeEngine.Handle();
                if(h!=null){h.deleted=state==1;h.visible=state>=3;h.payload=trial*1000+i;}
                instances.handles.add(h);if(h!=null&&!h.deleted)written.add(i);
            }
            List<Integer> selection=trial%3==0?null:new ArrayList<>();
            Set<Integer> selected=new HashSet<>();
            if(selection!=null&&slots>0)for(int j=0;j<slots*2;j++){int i=random.nextInt(slots);selection.add(i);selected.add(i);}
            for(int i=0;i<slots;i++){var h=instances.handles.get(i);if(h!=null&&!h.deleted&&h.visible&&(selection==null||selected.contains(i)))expected.add(i);}
            for(boolean direct:new boolean[]{false,true}){
                MemoryUtil.reset();instances.written.clear();
                int count=NativeWorldRenderer.packOriginalInstances(instances,selection,10000,20000,30000,stride,direct);
                check(count==expected.size());check(instances.written.keySet().equals(written));
                for(int i:written)check(instances.written.get(i)==10000+i*(long)stride);
                if(direct){
                    for(int i=0;i<expected.size();i++)check(MemoryUtil.memGetInt(30000+i*4L)==expected.get(i));
                    check(MemoryUtil.memGetInt(30000+expected.size()*4L)==0);
                }else{
                    for(int i=0;i<slots;i++)check(((MemoryUtil.memGetInt(20000+(i/32)*8L+4) >>> (i&31))&1)==(expected.contains(i)?1:0));
                }
            }
            if(selection!=null){
                selection.add(slots);
                boolean rejected=false;try{NativeWorldRenderer.packOriginalInstances(instances,selection,10000,20000,30000,stride,true);}catch(IllegalArgumentException correct){rejected=true;}
                check(rejected);
            }
        }
        System.out.println("ORIGINAL_NATIVE_PACKING_CONTROLS "+checks);
    }
}
