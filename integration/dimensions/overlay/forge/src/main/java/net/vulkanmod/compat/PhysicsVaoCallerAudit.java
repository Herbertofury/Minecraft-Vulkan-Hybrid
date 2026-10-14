package net.vulkanmod.compat;
import java.io.InputStream;import java.nio.charset.StandardCharsets;import java.security.MessageDigest;import java.util.*;
/** Three exact original outer VAO/format/state algorithms; full Physics remains separate. */
final class PhysicsVaoCallerAudit {
 private static final Map<String,String> PINS=Map.of("net/diebuddies/opengl/VAO.class","146aaff67d44c59cfdec8f325465026d676818cc0dad3091ea2e6fcdbf3b5ea5","net/diebuddies/opengl/VertexFormat.class","149370b2b838ab6bc0f064259c19e9b90419b799c63c91a1d4fadafbdf74cbee","net/diebuddies/opengl/StateTracker.class","46ec2088eb51d0d751a3146fb04f483fc1f6ed5d6e1d8cb7cee5f533c3e6fcaf");
 static boolean translated(String name,byte[]raw){try{
  if(!Objects.equals(PINS.get(name),HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw))))return false;
  try(InputStream config=PhysicsVaoCallerAudit.class.getResourceAsStream("/vulkanmod.mixins.json")){if(config==null)return false;String s=new String(config.readAllBytes(),StandardCharsets.UTF_8);if(!s.contains("net.vulkanmod.mixin.MixinPlugin")||!s.contains("\"compatibility.physics.PhysicsVaoCallerMixin\""))return false;}
  for(String r:new String[]{"/net/vulkanmod/gl/NativeVertexArrayApi.class","/net/vulkanmod/gl/PhysicsVaoCallers.class","/net/vulkanmod/gl/NativePhysicsVaoRuntime.class","/net/vulkanmod/vulkan/device/NativeVertexDivisors.class","/net/vulkanmod/mixin/compatibility/physics/PhysicsVaoCallerMixin.class"})if(PhysicsVaoCallerAudit.class.getResource(r)==null)return false;return true;
 }catch(Exception e){return false;}}
}