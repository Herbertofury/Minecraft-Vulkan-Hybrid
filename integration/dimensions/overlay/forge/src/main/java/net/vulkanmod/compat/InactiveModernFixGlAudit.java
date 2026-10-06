package net.vulkanmod.compat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipFile;

/** Narrow reachability audit. No GL capability method is added to the translation contract. */
public final class InactiveModernFixGlAudit {
    static final String CLASS = "org/embeddedt/modernfix/forge/util/AsyncLoadingScreen.class";
    private static final String MIXIN = "feature.registry_event_progress.GameDataMixin";
    private static final Set<String> PINS = Set.of(
            "954ff22f601be8de3978a0d7636a138dc93801c6ac55cfd71ef1cd2895de8e5f",
            "12b76f627e34ddf9211766ad7a1d5f2bb3de8d0807935d2d20f357b907f481b8");
    enum State { DISABLED, ENABLED, UNKNOWN }
    private final State state;
    private final Map<Path,String> hashes;
    private InactiveModernFixGlAudit(State state,Map<Path,String> hashes) {
        this.state=state;this.hashes=Map.copyOf(hashes);
    }
    public static InactiveModernFixGlAudit inspect(List<Path> jars) {
        Map<Path,String> hashes=new LinkedHashMap<>();
        for(Path jar:jars) {
            try(ZipFile zip=new ZipFile(jar.toFile())) {
                if(zip.getEntry(CLASS)!=null)hashes.put(jar,sha256(jar));
            } catch(IOException|RuntimeException unavailable) {
                // The ordinary gate still scans/rejects unreadable or unsupported code.
            }
        }
        return new InactiveModernFixGlAudit(effectiveState(),hashes);
    }
    public boolean excludes(Path jar,String classEntry) {
        return allows(hashes.get(jar),classEntry,state);
    }
    static boolean allows(String digest,String classEntry,State state) {
        return state==State.DISABLED && CLASS.equals(classEntry) && PINS.contains(digest==null?"":digest);
    }
    public String cacheKey() {
        StringBuilder out=new StringBuilder(state.name());
        hashes.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e->out.append(';').append(e.getKey().getFileName()).append('=').append(e.getValue()));
        return out.toString();
    }
    private static State effectiveState() {
        try {
            // Read the existing plugin; never construct or reconfigure ModernFix.
            Class<?> type=Class.forName("org.embeddedt.modernfix.core.ModernFixMixinPlugin",false,InactiveModernFixGlAudit.class.getClassLoader());
            Object plugin=type.getField("instance").get(null);
            if(plugin==null || type.getField("config").get(plugin)==null)return State.UNKNOWN;
            Object enabled=type.getMethod("isOptionEnabled",String.class).invoke(plugin,MIXIN);
            if(Boolean.FALSE.equals(enabled))return State.DISABLED;
            if(Boolean.TRUE.equals(enabled))return State.ENABLED;
        } catch(ReflectiveOperationException|LinkageError|RuntimeException unavailable) {
            // Unavailable effective configuration never grants an exemption.
        }
        return State.UNKNOWN;
    }
    private static String sha256(Path jar)throws IOException {
        try {
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            try(InputStream input=Files.newInputStream(jar)){byte[] bytes=new byte[65536];int count;while((count=input.read(bytes))!=-1)digest.update(bytes,0,count);}
            return HexFormat.of().formatHex(digest.digest());
        } catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
}
