import mvhstartupcompat.*;
import java.util.*;
public final class BiomeModifierIndexRegression {
    static List<String> original(List<String> rows,String id){List<String> out=new ArrayList<>();for(String row:rows){String[] p=row.split("\\|");if(p.length==2&&p[0].equals(id))out.add(row);}return out;}
    public static void main(String[] args) {
        Random random=new Random(1729);int cases=0;
        for(int round=0;round<300;round++) {
            List<String> rows=new ArrayList<>();
            for(int n=0;n<500;n++){String id="entity:"+random.nextInt(40);String row=switch(random.nextInt(6)){case 0->id+"|biome:"+random.nextInt(20);case 1->id+"|biome:x|";case 2->id+"||";case 3->id+"|biome:雪";case 4->id+"|x|y";default->"";};rows.add(row);if(n%25==0)rows.add(row);}
            BiomeQuerySplitCache cache=new BiomeQuerySplitCache(4096);BiomeModifierIndex index=new BiomeModifierIndex(cache);
            for(int n=0;n<50;n++){String id="entity:"+n;if(!original(rows,id).equals(index.matching(rows,id)))throw new AssertionError("parity/order/duplicates");cases++;}
            rows.clear();rows.add("entity:1|biome:new");index=new BiomeModifierIndex(cache);if(!original(rows,"entity:1").equals(index.matching(rows,"entity:1")))throw new AssertionError("reload stale");
        }
        System.out.println("BIOME_MODIFIER_INDEX_PARITY_ORDER_DUPLICATES_RELOAD_PASS cases="+cases);
    }
}
