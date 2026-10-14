package net.vulkanmod.compat;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Exact helper transport backed by original-method GPU evidence; no provider-wide waiver. */
final class PhysicsShaderCallerAudit {
    private PhysicsShaderCallerAudit(){}
    static boolean translated(String entry,byte[] bytes){
        if(!entry.equals("net/diebuddies/opengl/Shader.class"))return false;
        try {
            if(!HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)).equals("be8a6b1ae560c63c312f63e9a90f6df02ab8d27ad0728913ea8bc0a2e405b037"))return false;
            try(InputStream config=PhysicsShaderCallerAudit.class.getResourceAsStream("/vulkanmod.mixins.json")){
                if(config==null)return false;String text=new String(config.readAllBytes(),StandardCharsets.UTF_8);
                if(!text.contains("\"plugin\": \"net.vulkanmod.mixin.MixinPlugin\"") || !text.contains("\"compatibility.physics.PhysicsShaderCallerMixin\""))return false;
            }
            for(String resource:new String[]{"/net/vulkanmod/gl/NativeShaderApi.class","/net/vulkanmod/gl/PhysicsShaderCallers.class","/net/vulkanmod/vulkan/shader/NativeUniformUse.class","/net/vulkanmod/mixin/compatibility/physics/PhysicsShaderCallerMixin.class"})if(PhysicsShaderCallerAudit.class.getResource(resource)==null)return false;
            return true;
        }catch(Exception failure){return false;}
    }
}
