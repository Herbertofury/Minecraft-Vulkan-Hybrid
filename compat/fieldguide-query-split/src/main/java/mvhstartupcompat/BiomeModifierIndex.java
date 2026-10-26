package mvhstartupcompat;

import java.util.*;

/** One query's exact modifier lists, grouped by entry ID without reordering or deduplication. */
public final class BiomeModifierIndex {
    private final IdentityHashMap<List<String>, Map<String,List<String>>> indexes = new IdentityHashMap<>();
    private final BiomeQuerySplitCache parsing;
    public BiomeModifierIndex(BiomeQuerySplitCache parsing) { this.parsing=parsing; }
    public List<String> matching(List<String> original,String entryId) {
        Map<String,List<String>> grouped=indexes.computeIfAbsent(original, input -> {
            Map<String,List<String>> built=new HashMap<>();
            for(String row:input) {
                String[] parts=parsing.split(row,"\\|");
                if(parts.length==2) built.computeIfAbsent(parts[0],key->new ArrayList<>()).add(row);
            }
            return built;
        });
        return grouped.getOrDefault(entryId,Collections.emptyList());
    }
}
