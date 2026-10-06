package mvhstartupcompat;
import java.util.LinkedHashMap;
import java.util.Map;
/** Bounded, owner-local reuse of read-only pipe-split arrays in Field Guide's biome search. */
public final class BiomeQuerySplitCache {
 private final int limit; private long hits;
 private final Map<String,String[]> cache=new LinkedHashMap<>(64,0.75f,true);
 public BiomeQuerySplitCache(int limit){if(limit<1)throw new IllegalArgumentException("limit");this.limit=limit;}
 public String[] split(String input,String regex){
  if(!"\\|".equals(regex))return input.split(regex);
  String[] value=cache.get(input);
  if(value!=null){hits++;return value;}
  value=input.split(regex);cache.put(input,value);
  if(cache.size()>limit)cache.remove(cache.keySet().iterator().next());
  return value;
 }
 public int size(){return cache.size();} public long hits(){return hits;}
}
