package net.vulkanmod.compat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

/** Exact original managed-state callers with a packaged, hash-gated GAME call-site repair.
 * This covers these four state implementations only; no provider-wide acceptance.
 * All other classes, API descriptors, renderer conflicts and native policy retain their gates. */
final class EssentialManagedStateCallerAudit {
    private static final Map<String,String> PINS=Map.of("gg/essential/universal/render/ManagedGlState.class","a7133abec441cf1752e84a44677af47a571437ed952d116662b646268fbafbbb","gg/essential/universal/render/ManagedGlState$Companion.class","1eac59bccdb17d7e31406d1e8503dc6e16eb27d30e94813bdb9ec72ff03a4bbf","gg/essential/universal/shader/BlendState.class","a11ec564705e82ff56312e3674d1ef80bfa3e31e881cb7f1ff6fd88f014be64b","gg/essential/universal/shader/BlendState$Companion.class","49373ed28948a81225a11fbc3123dea8c76df6e67145e223e3cda25e8d81b9ab");
    private EssentialManagedStateCallerAudit(){}
    static boolean translated(String entry,byte[] bytes) {
        String pin=PINS.get(entry);if(pin==null)return false;
        try{
            if(!pin.equals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))))return false;
            // The packaged transport and automatic production Mixin must both be present.
            try(InputStream config=EssentialManagedStateCallerAudit.class.getResourceAsStream("/vulkanmod.mixins.json")){
                if(config==null)return false;String text=new String(config.readAllBytes(),StandardCharsets.UTF_8);
                if(!text.contains("\"plugin\": \"net.vulkanmod.mixin.MixinPlugin\"")||!text.contains("\"compatibility.essential.EssentialManagedStateCallerMixin\""))return false;
            }
            for(String resource:new String[]{"/net/vulkanmod/gl/NativeEssentialStateApi.class","/net/vulkanmod/gl/EssentialManagedStateCallers.class","/net/vulkanmod/mixin/compatibility/essential/EssentialManagedStateCallerMixin.class"})
                if(EssentialManagedStateCallerAudit.class.getResource(resource)==null)return false;
            return true;
        }catch(Exception failure){return false;}
    }
}
