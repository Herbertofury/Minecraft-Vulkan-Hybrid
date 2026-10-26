package mvhflywheelbackend;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.engine_room.flywheel.api.backend.*;
import dev.engine_room.flywheel.api.instance.*;
import dev.engine_room.flywheel.api.model.Model;
import dev.engine_room.flywheel.api.task.Plan;
import dev.engine_room.flywheel.api.visualization.*;
import dev.engine_room.flywheel.backend.engine.LightStorage;
import dev.engine_room.flywheel.backend.engine.TextureBinder;
import dev.engine_room.flywheel.backend.engine.uniform.Uniforms;
import dev.engine_room.flywheel.lib.task.SimplePlan;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.client.Camera;
import net.minecraft.core.*;
import net.minecraft.world.level.*;
import org.joml.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/** Real Flywheel backend entrypoint, preserving original visual/model/instance writers. GPL-3.0-only. */
public final class NativeEngine implements Engine {
    static final Object MUTATIONS=new Object();
    private final Map<Key,NativeInstancer<?>> instancers=new LinkedHashMap<>();
    private final LightStorage light;
    private NativeWorldRenderer renderer;
    private BlockPos origin=BlockPos.ZERO;
    private boolean deleted;
    private boolean meshReap;
    private final Context root=new Context(null,null);
    public static final AtomicLong ENGINES=new AtomicLong(),WORLD_FRAMES=new AtomicLong(),MODEL_DRAWS=new AtomicLong(),CRUMBLING_DRAWS=new AtomicLong();
    public static final AtomicLong CHUNK_VISUAL_REGISTRATIONS=new AtomicLong(),RELOAD_EVENTS=new AtomicLong();
    public static final AtomicLong GPU_CULL_DISPATCHES=new AtomicLong(),GPU_APPLY_DISPATCHES=new AtomicLong(),GPU_LAST_VISIBLE=new AtomicLong(),GPU_LAST_SUBMITTED=new AtomicLong();
    public static final AtomicLong LIGHT_UPLOADS=new AtomicLong(),LIGHT_UPLOAD_BYTES=new AtomicLong(),LIGHT_CACHE_HITS=new AtomicLong();
    public static final AtomicLong EMBEDDED_MODEL_DRAWS=new AtomicLong();
    public static final AtomicLong DIRECT_INSTANCE_DRAWS=new AtomicLong(),DIRECT_MODEL_SNAPSHOTS=new AtomicLong();
    private static boolean diagnosticComputeOnly;
    static boolean useDirectInstances(int count){return count<32&&!diagnosticComputeOnly;}
    /** Owned diagnostics exercise the retained native compute path independently of adaptive small batches. */
    public static void useComputeForDiagnostics(boolean value){RenderSystem.assertOnRenderThread();diagnosticComputeOnly=value;}

    public NativeEngine(LevelAccessor level){light=new LightStorage(level);ENGINES.incrementAndGet();}
    public static boolean selectedForCurrentSession(){return dev.engine_room.flywheel.impl.BackendManagerImpl.currentBackend()==dev.engine_room.flywheel.backend.Backends.NATIVE;}
    public LightStorage lightStorage(){return light;}
    public int nativeEmbeddedInstancerCount(){synchronized(MUTATIONS){int count=0;for(var i:instancers.values())if(i.context.embedded()&&i.hasLive())count++;return count;}}
    public int nativeInstancerCount(){synchronized(MUTATIONS){return instancers.size();}}
    private void live(){if(deleted)throw new IllegalStateException("Flywheel engine used after deletion");}
    @Override public VisualizationContext createVisualizationContext(){live();return root;}
    @Override public Plan<RenderContext> createFramePlan(){live();return light.<RenderContext>createFramePlan().and(SimplePlan.<RenderContext>of(()->{synchronized(MUTATIONS){for(var it=instancers.values().iterator();it.hasNext();){var i=it.next();i.prune();if(i.context.closedChain()&&!i.hasLive()){i.retired=true;it.remove();meshReap=true;}}}}));}
    @Override public Vec3i renderOrigin(){return origin;}
    @Override public boolean updateRenderOrigin(Camera camera){live();var p=camera.getPosition();double x=p.x-origin.getX(),y=p.y-origin.getY(),z=p.z-origin.getZ();if(x*x+y*y+z*z<=256*256)return false;origin=BlockPos.containing(p);synchronized(MUTATIONS){for(var i:instancers.values())i.clear();}return true;}
    @Override public void lightSections(LongSet sections){live();light.sections(sections);}
    @Override public void onLightUpdate(SectionPos section,LightLayer layer){live();light.onLightUpdate(section.asLong());}
    @Override public void render(RenderContext context){
        RenderSystem.assertOnRenderThread();live();if(renderer==null)renderer=new NativeWorldRenderer();
        Uniforms.update(context);renderer.beginFrame(light);
        TextureBinder.bindLightAndOverlay();try{synchronized(MUTATIONS){
            if(meshReap){renderer.retireUnusedMeshes(instancers.values());meshReap=false;}
            ArrayList<Map.Entry<Key,NativeInstancer<?>>> order=new ArrayList<>(instancers.entrySet());order.sort(Comparator.comparingInt(e->e.getKey().bias));
            ArrayList<NativeInstancer<?>> nativeOrder=new ArrayList<>();for(var entry:order)if(entry.getValue().count()>0)nativeOrder.add(entry.getValue());renderer.prepare(nativeOrder,null);
            for(var entry:order){NativeInstancer<?> i=entry.getValue();if(i.count()>0){renderer.render(entry.getKey().context,i,null,-1);}}
        }}finally{TextureBinder.resetLightAndOverlay();}
        WORLD_FRAMES.incrementAndGet();
    }
    @Override public void renderCrumbling(RenderContext context,List<CrumblingBlock> blocks){
        RenderSystem.assertOnRenderThread();live();if(renderer==null)throw new IllegalStateException("Crumbling before frame");
        TextureBinder.bindLightAndOverlay();try{synchronized(MUTATIONS){for(var block:blocks){if(block.progress()<0||block.progress()>9)continue;Map<NativeInstancer<?>,List<Integer>> groups=new IdentityHashMap<>();
            for(Instance instance:block.instances())if(instance.handle() instanceof Handle h&&h.owner.engine==this&&!h.deleted&&h.visible){groups.computeIfAbsent(h.owner,$->new ArrayList<>()).add(h.index);}
            for(var e:groups.entrySet())renderer.render(e.getKey().context,e.getKey(),e.getValue(),block.progress());
        }}}finally{TextureBinder.resetLightAndOverlay();}
    }
    @Override public void delete(){RenderSystem.assertOnRenderThread();live();deleted=true;synchronized(MUTATIONS){for(var i:instancers.values())i.clear();instancers.clear();}if(renderer!=null)renderer.delete();light.delete();}
    private record Key(Context context,InstanceType<?> type,Model model,int bias){}
    @SuppressWarnings("unchecked") private <I extends Instance> NativeInstancer<I> instancer(Context context,InstanceType<I> type,Model model,int bias){synchronized(MUTATIONS){live();Key key=new Key(context,type,model,bias);NativeInstancer<?> existing=instancers.get(key);if(existing!=null)return (NativeInstancer<I>)existing;context.canCreate();NativeInstancer<I> created=new NativeInstancer<>(this,context,type,model);instancers.put(key,created);return created;}}
    class Context implements VisualizationContext {
        final Context parent;final Vec3i embeddedOrigin;final Matrix4f pose=new Matrix4f();final Matrix3f normal=new Matrix3f();boolean closed;
        private final InstancerProvider provider=new InstancerProvider(){@Override public <I extends Instance> Instancer<I> instancer(InstanceType<I> type,Model model,int bias){return NativeEngine.this.instancer(Context.this,type,model,bias);}};
        Context(Context parent,Vec3i origin){this.parent=parent;embeddedOrigin=origin;}
        boolean embedded(){return embeddedOrigin!=null;}
        boolean closedChain(){return closed||(parent!=null&&parent.closedChain());}
        void canCreate(){if(closed)throw new IllegalStateException("New instance in deleted embedding");if(parent!=null)parent.canCreate();}
        @Override public InstancerProvider instancerProvider(){return provider;}
        @Override public Vec3i renderOrigin(){return embeddedOrigin==null?origin:embeddedOrigin;}
        @Override public VisualEmbedding createEmbedding(Vec3i origin){synchronized(MUTATIONS){live();canCreate();return new Embedding(this,origin);}}
        public void transforms(Matrix4fc pose,Matrix3fc normal){synchronized(MUTATIONS){this.pose.set(pose);this.normal.set(normal);}}
        void compose(Matrix4f pose,Matrix3f normal){if(parent!=null)parent.compose(pose,normal);pose.mul(this.pose);normal.mul(this.normal);}
        public void delete(){synchronized(MUTATIONS){closed=true;}}
    }
    final class Embedding extends Context implements VisualEmbedding {Embedding(Context parent,Vec3i origin){super(parent,origin);}}
    static final class NativeInstancer<I extends Instance> implements Instancer<I> {
        final NativeEngine engine;final Context context;final InstanceType<I> type;final Model model;final ArrayList<Handle> handles=new ArrayList<>();final ArrayDeque<Integer> free=new ArrayDeque<>();long revision;boolean retired;
        NativeInstancer(NativeEngine engine,Context context,InstanceType<I> type,Model model){this.engine=engine;this.context=context;this.type=Objects.requireNonNull(type);this.model=Objects.requireNonNull(model);if(type.layout().byteSize()<4||(type.layout().byteSize()&3)!=0)throw new IllegalArgumentException("Instance storage ABI alignment");}
        @Override public I createInstance(){synchronized(MUTATIONS){engine.live();if(retired)throw new IllegalStateException("Retired embedding instancer");int index=free.isEmpty()?handles.size():free.removeFirst();Handle h=new Handle(this,index);I instance=type.create(h);h.instance=instance;if(index==handles.size())handles.add(h);else handles.set(index,h);revision++;return instance;}}
        @Override public void stealInstance(I instance){if(instance==null)return;synchronized(MUTATIONS){engine.live();if(retired)throw new IllegalStateException("Retired embedding instancer");if(!(instance.handle() instanceof Handle h)||h.owner.type!=type)throw new IllegalArgumentException("Instance belongs to another backend/type");if(h.owner==this)return;h.owner.remove(h);int index=free.isEmpty()?handles.size():free.removeFirst();h.owner=this;h.index=index;h.deleted=false;if(index==handles.size())handles.add(h);else handles.set(index,h);revision++;}}
        void remove(Handle h){if(h.index>=0&&h.index<handles.size()&&handles.get(h.index)==h){handles.set(h.index,null);free.add(h.index);revision++;}}
        void prune(){for(int i=0;i<handles.size();i++){Handle h=handles.get(i);if(h!=null&&h.deleted)remove(h);}}
        void clear(){for(Handle h:handles)if(h!=null){h.deleted=true;h.visible=false;}handles.clear();free.clear();revision++;}
        boolean hasLive(){for(Handle h:handles)if(h!=null&&!h.deleted)return true;return false;}
        int count(){int count=0;for(Handle h:handles)if(h!=null&&!h.deleted&&h.visible)count++;return count;}
        @SuppressWarnings("unchecked") void write(int index,long pointer){type.writer().write(pointer,(I)handles.get(index).instance);}
    }
    static final class Handle implements InstanceHandle {
        NativeInstancer<?> owner;int index;Instance instance;volatile boolean visible=true,deleted;final AtomicLong generation=new AtomicLong(1);
        Handle(NativeInstancer<?> owner,int index){this.owner=owner;this.index=index;}
        @Override public void setChanged(){generation.incrementAndGet();}
        @Override public void setDeleted(){deleted=true;generation.incrementAndGet();}
        @Override public void setVisible(boolean visible){if(!deleted)this.visible=visible;generation.incrementAndGet();}
        @Override public boolean isVisible(){return visible&&!deleted;}
    }
}
