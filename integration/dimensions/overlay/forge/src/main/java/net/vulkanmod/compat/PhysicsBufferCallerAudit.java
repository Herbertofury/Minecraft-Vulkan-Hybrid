package net.vulkanmod.compat;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Pinned storage helpers backed by native storage/transfer/GPU consumption evidence. */
final class PhysicsBufferCallerAudit {
    private static final Map<String,String> PINS=Map.of("net/diebuddies/opengl/BufferObject.class","2232a0ff00515584a97cd8393dcbd2d8c5ebf41e3084355174d56daa1931c331","net/diebuddies/opengl/ArenaBuffer.class","c16bd327077ab70871280d565752c765aab4d2d6c62792caf7b62876531fb57f");
    private PhysicsBufferCallerAudit(){}
    static boolean translated(String entry,byte[] bytes){
        try{if(!Objects.equals(PINS.get(entry),HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))))return false;
            try(InputStream config=PhysicsBufferCallerAudit.class.getResourceAsStream("/vulkanmod.mixins.json")){if(config==null)return false;String s=new String(config.readAllBytes(),StandardCharsets.UTF_8);if(!s.contains("\"plugin\": \"net.vulkanmod.mixin.MixinPlugin\"")||!s.contains("\"compatibility.physics.PhysicsBufferCallerMixin\""))return false;}
            for(String resource:new String[]{"/net/vulkanmod/gl/NativeBufferObjectApi.class","/net/vulkanmod/gl/NativePixelBufferApi.class","/net/vulkanmod/gl/PhysicsBufferCallers.class","/net/vulkanmod/mixin/compatibility/physics/PhysicsBufferCallerMixin.class"})if(PhysicsBufferCallerAudit.class.getResource(resource)==null)return false;return true;
        }catch(Exception failure){return false;}
    }
}
