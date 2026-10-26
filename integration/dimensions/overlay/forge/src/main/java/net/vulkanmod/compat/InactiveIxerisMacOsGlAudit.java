package net.vulkanmod.compat;

import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.zip.ZipFile;

/** Pinned macOS CGL code is unreachable on the verified LWJGL Windows platform. */
public final class InactiveIxerisMacOsGlAudit {
    static final String PIN="6cc7e1bfad6a2e82983a41535accf919a1027ca7087f1e6fcc955df22f7d91e1";
    static final Set<String> CLASSES=Set.of("me/decce/ixeris/IxerisMinecraftAccessorImpl.class","me/decce/ixeris/mixins/macos/MinecraftMixin.class");
    enum State { WINDOWS, OTHER, UNKNOWN }
    private final State state;private final Map<Path,String> hashes;
    private InactiveIxerisMacOsGlAudit(State state,Map<Path,String> hashes){this.state=state;this.hashes=Map.copyOf(hashes);}
    public static InactiveIxerisMacOsGlAudit inspect(List<Path> jars){
        Map<Path,String> hashes=new LinkedHashMap<>();
        for(Path jar:jars)try(ZipFile zip=new ZipFile(jar.toFile())){
            if(CLASSES.stream().anyMatch(c->zip.getEntry(c)!=null))hashes.put(jar,sha256(jar));
        }catch(IOException|RuntimeException unreadable){/* Ordinary gate retains unreadable/changed inputs. */}
        return new InactiveIxerisMacOsGlAudit(hashes.isEmpty()?State.UNKNOWN:platform(),hashes);
    }
    public boolean excludes(Path jar,String entry){return allows(hashes.get(jar),entry,state);}
    static boolean allows(String digest,String entry,State state){return state==State.WINDOWS&&PIN.equals(digest)&&CLASSES.contains(entry);}
    public String cacheKey(){StringBuilder key=new StringBuilder(state.name());hashes.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e->key.append(';').append(e.getKey().getFileName()).append('=').append(e.getValue()));return key.toString();}
    private static State platform(){
        try{
            // The pinned PlatformHelper stores precisely Platform.get() in a final static field.
            // Mac mixin bodies require MACOSX; the only writes to lockedContext occur there.
            // Its accessor locks therefore retain the original zero-context branch on Windows.
            Class<?> type=Class.forName("org.lwjgl.system.Platform",true,InactiveIxerisMacOsGlAudit.class.getClassLoader());
            Object value=type.getMethod("get").invoke(null);
            if(!(value instanceof Enum<?> platform))return State.UNKNOWN;
            return platform.name().equals("WINDOWS")?State.WINDOWS:State.OTHER;
        }catch(ReflectiveOperationException|LinkageError|RuntimeException unavailable){return State.UNKNOWN;}
    }
    private static String sha256(Path path)throws IOException{
        try{
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            try(InputStream input=Files.newInputStream(path)){byte[] buffer=new byte[65536];int count;while((count=input.read(buffer))!=-1)digest.update(buffer,0,count);}
            return HexFormat.of().formatHex(digest.digest());
        }catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
}
