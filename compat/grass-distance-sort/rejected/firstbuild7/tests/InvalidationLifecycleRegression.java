import mvhgrasscompat.InvalidationRetention;
import java.util.*;

/** Independent snapshot lifecycle oracle, including completion between queue drain and draw. */
public class InvalidationLifecycleRegression {
    record State(int geometry,int light) {}
    static final class Cache {
        final boolean optimized;
        final Map<Long,State> world=new HashMap<>(), meshes=new HashMap<>(), flight=new HashMap<>();
        final Set<Long> dirty=new HashSet<>(), lightDirty=new HashSet<>(), candidates=new HashSet<>();
        Cache(boolean optimized){this.optimized=optimized;}
        void invalidate(long key,boolean light){
            if(!optimized || InvalidationRetention.retain(meshes.containsKey(key),flight.containsKey(key))) {
                if(light){if(!dirty.contains(key))lightDirty.add(key);}
                else {dirty.add(key);lightDirty.remove(key);}
            }
        }
        void change(long key,boolean light){
            State old=world.getOrDefault(key,new State(0,0));
            world.put(key,new State(old.geometry+(light?0:1),old.light+(light?1:0)));invalidate(key,light);
        }
        void scan(Set<Long> range){
            meshes.keySet().removeIf(k->!range.contains(k));
            dirty.removeIf(k->!range.contains(k)&&!meshes.containsKey(k)&&!flight.containsKey(k));
            lightDirty.removeIf(k->!range.contains(k)&&!meshes.containsKey(k)&&!flight.containsKey(k));
            candidates.retainAll(range);
            for(long key:range)if(!flight.containsKey(key)&&!meshes.containsKey(key))candidates.add(key);
        }
        void submit(long key,boolean loaded,boolean visible){
            if(flight.containsKey(key)||!loaded||!visible)return;
            if(!meshes.containsKey(key)||dirty.contains(key)){
                if(!candidates.contains(key)&&!meshes.containsKey(key))throw new AssertionError("Lost first-build candidate");
                flight.put(key,world.getOrDefault(key,new State(0,0)));dirty.remove(key);lightDirty.remove(key);
            }else if(lightDirty.remove(key)){
                State old=meshes.get(key),current=world.getOrDefault(key,new State(0,0));
                meshes.put(key,new State(old.geometry,current.light));
            }
        }
        void complete(long key){State result=flight.remove(key);if(result!=null){meshes.put(key,result);candidates.remove(key);}}
        void settle(Set<Long> range){
            scan(range);
            for(int i=0;i<3;i++)for(long key:range){submit(key,true,true);complete(key);}
            for(long key:range)if(!Objects.equals(meshes.get(key),world.getOrDefault(key,new State(0,0))))throw new AssertionError("Stale visible geometry/light at "+key);
        }
        void reload(){meshes.clear();flight.clear();dirty.clear();lightDirty.clear();candidates.clear();}
    }
    static void parity(Cache original,Cache fixed,Set<Long> range){
        original.settle(range);fixed.settle(range);
        if(!original.meshes.equals(fixed.meshes))throw new AssertionError("Visible snapshot parity failed");
    }
    public static void main(String[] args){
        for(boolean light:new boolean[]{false,true}){
            Cache original=new Cache(false),fixed=new Cache(true);Set<Long> range=Set.of(1L,2L,3L);
            // Before first scan and while hidden/unloaded: future first build must read current world.
            for(Cache c:List.of(original,fixed)){c.change(1,light);c.scan(range);c.submit(1,false,true);c.change(1,light);c.submit(1,true,false);c.change(1,light);}
            parity(original,fixed,range);
            // A cached mesh, including a formerly empty section, must receive every update.
            for(Cache c:List.of(original,fixed)){c.change(2,light);c.change(2,false);}parity(original,fixed,range);
            // First build in flight without a cached mesh; mutation after capture must survive completion.
            for(Cache c:List.of(original,fixed)){c.reload();c.scan(range);c.submit(3,true,true);c.change(3,light);c.complete(3);}parity(original,fixed,range);
            // Cached replacement in flight; preserve changes even if completion precedes the next scan.
            for(Cache c:List.of(original,fixed)){c.change(1,false);c.submit(1,true,true);c.change(1,light);c.complete(1);}parity(original,fixed,range);
            // Move away, receive changes, return: retained first-build discovery must rebuild latest state.
            for(Cache c:List.of(original,fixed)){c.scan(Set.of(8L));c.change(1,light);c.change(9,light);}parity(original,fixed,range);
            for(Cache c:List.of(original,fixed)){c.reload();c.change(1,light);}parity(original,fixed,range);
        }
        Random random=new Random(1729);
        Cache original=new Cache(false),fixed=new Cache(true);Set<Long> range=new HashSet<>();
        for(long i=0;i<16;i++)range.add(i);
        for(int i=0;i<20000;i++){
            long key=random.nextInt(32);int action=random.nextInt(6);boolean light=random.nextBoolean();
            for(Cache c:List.of(original,fixed))switch(action){
                case 0,1->c.change(key,light);
                case 2->c.scan(range);
                case 3->{if(range.contains(key)&&(c.candidates.contains(key)||c.meshes.containsKey(key)))c.submit(key,true,true);}
                case 4->c.complete(key);
                case 5->{if(i%19==0)c.reload();}
            }
            if(i%61==0)parity(original,fixed,range);
        }
        parity(original,fixed,range);
        if(!InvalidationRetention.retain(true,false)||!InvalidationRetention.retain(false,true)||InvalidationRetention.retain(false,false))throw new AssertionError("Snapshot retention violated");
        System.out.println("GRASS_INVALIDATION_LIFECYCLE_PARITY_PASS 20000 events");
    }
}
