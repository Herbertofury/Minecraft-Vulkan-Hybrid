import mvhstartupcompat.BiomeQuerySplitCache;
import java.util.*;
public class BiomeQuerySplitRegression {
 public static void main(String[] args){
  BiomeQuerySplitCache cache=new BiomeQuerySplitCache(32);
  String[] cases={"","|","||","x|","|x","x||y","minecraft:pig|minecraft:forest","mód:🐖|日本語:森","a|b|c","a\nb|c"};
  for(String s:cases){String[] a=cache.split(s,"\\|");check(Arrays.equals(a,s.split("\\|")),"split semantics "+s);check(a==cache.split(new String(s),"\\|"),"content-key reuse");}
  check(Arrays.equals(cache.split("a.b.c","\\."),"a.b.c".split("\\.")),"other regex fallback");
  Random r=new Random(1729);for(int n=0;n<5000;n++){StringBuilder b=new StringBuilder();for(int j=0;j<r.nextInt(100);j++)b.append("ab|:é\n".charAt(r.nextInt(6)));String s=b.toString();check(Arrays.equals(cache.split(s,"\\|"),s.split("\\|")),"random parity");check(cache.size()<=32,"bounded residency");}
  String old="old|biome",updated="new|biome";check(!Arrays.equals(cache.split(old,"\\|"),cache.split(updated,"\\|")),"reload content changes preserved");
  try{cache.split("value","[");throw new AssertionError("invalid regex must fail");}catch(java.util.regex.PatternSyntaxException expected){}
  System.out.println("PASS: 5000 randomized parity cases, Unicode/trailing empties, content identity, reload changes, bounded eviction and exception propagation");
 }
 static void check(boolean ok,String msg){if(!ok)throw new AssertionError(msg);}
}
