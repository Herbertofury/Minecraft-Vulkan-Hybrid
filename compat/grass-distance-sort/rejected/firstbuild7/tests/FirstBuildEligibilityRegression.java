import mvhgrasscompat.FirstBuildEligibility;
import java.util.*;
/** Independent original scheduling trace; hidden first builds stay queued and cached dirty meshes still rebuild. */
public final class FirstBuildEligibilityRegression {
    static int checks;
    static final class Trace {
        final boolean optimized;final LinkedHashSet<Integer> queue=new LinkedHashSet<>();final Map<Integer,Integer> cache=new HashMap<>(),flight=new HashMap<>();final Set<Integer> dirty=new HashSet<>();final List<Integer> submissions=new ArrayList<>();
        Trace(boolean optimized){this.optimized=optimized;for(int i=0;i<48;i++)queue.add(i);}
        void frame(int frame,int[] world,boolean[] visible,boolean known){
            if(frame%3==0){cache.putAll(flight);flight.clear();}
            for(int i=0;i<world.length;i++)if(cache.containsKey(i)&&!cache.get(i).equals(world[i])){dirty.add(i);queue.add(i);}
            int budget=4;for(var it=queue.iterator();it.hasNext()&&budget>0;){int key=it.next();boolean inFlight=flight.containsKey(key),cached=cache.containsKey(key);
                if(optimized&&FirstBuildEligibility.skip(inFlight,cached,known,visible[key]))continue;
                if(inFlight)continue;
                if(cached&&!dirty.contains(key)){it.remove();continue;}
                if(!cached&&!visible[key])continue;
                if(frame%7==0&&key%3==0)continue; // Original region unavailable: keep its first-build candidate.
                flight.put(key,world[key]);dirty.remove(key);submissions.add(key);budget--;
            }
        }
    }
    static void check(boolean v){checks++;if(!v)throw new AssertionError("Original scheduling trace differs at "+checks);}
    public static void main(String[] args){
        for(int bits=0;bits<16;bits++){boolean in=(bits&1)!=0,cached=(bits&2)!=0,known=(bits&4)!=0,visible=(bits&8)!=0;
            if(cached&&!in)check(!FirstBuildEligibility.skip(in,cached,known,visible));if(!known&&!in)check(!FirstBuildEligibility.skip(in,cached,known,visible));}
        int[] world=new int[48];boolean[] visible=new boolean[48];Trace original=new Trace(false),fixed=new Trace(true);Random random=new Random(437294);
        for(int frame=0;frame<400;frame++){
            for(int j=0;j<3;j++)world[random.nextInt(world.length)]++;for(int j=0;j<visible.length;j++)visible[j]=(j+frame/8)%5<3;
            boolean known=frame%11!=0;original.frame(frame,world,visible,known);fixed.frame(frame,world,visible,known);
            check(original.queue.equals(fixed.queue));check(original.cache.equals(fixed.cache));check(original.flight.equals(fixed.flight));check(original.dirty.equals(fixed.dirty));check(original.submissions.equals(fixed.submissions));
        }
        Arrays.fill(visible,true);for(int frame=400;frame<460;frame++){original.frame(frame,world,visible,true);fixed.frame(frame,world,visible,true);check(original.cache.equals(fixed.cache));}
        for(int key=0;key<world.length;key++)check(Objects.equals(fixed.cache.get(key),world[key]));
        System.out.println("ORIGINAL_FIRST_BUILD_SCHEDULING_TRACE_CONTROLS "+checks);
    }
}
