package net.vulkanmod.compat;
import java.io.InputStream;import java.nio.charset.StandardCharsets;import java.security.MessageDigest;import java.util.*;
/** Four pinned binding delegates only; outer VAO/context/full Physics stay separate. */
final class PhysicsVertexCallerAudit {
 private static final Map<String,String> PINS=Map.of("net/diebuddies/opengl/VAO$GL43VertexAttribBinding.class","29e0b931a04d53b5e8e1d13f8084a2cc034a62dac195888b02b410b131819b53","net/diebuddies/opengl/VAO$ARBVertexAttribBinding.class","517a04c2fa00dddd1b5b4a262ad3ab08a6e5b65f243ccfd966a0b28a9a677ec1","net/diebuddies/opengl/VAO$GL44MultiBind.class","fc7e90877888f34d3ebddf2966bb01a41d9e5bb2d6d2a6e063d98dcfb4d270b0","net/diebuddies/opengl/VAO$ARBMultiBind.class","8b314eaecfc522cc08a1c10a40bf689236a9aa6b9efb41ad9fe213c3ca52f635");
 static boolean translated(String name,byte[]raw){try{
  if(!Objects.equals(PINS.get(name),HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw))))return false;
  try(InputStream config=PhysicsVertexCallerAudit.class.getResourceAsStream("/vulkanmod.mixins.json")){if(config==null)return false;String s=new String(config.readAllBytes(),StandardCharsets.UTF_8);if(!s.contains("net.vulkanmod.mixin.MixinPlugin")||!s.contains("\"compatibility.physics.PhysicsVertexCallerMixin\""))return false;}
  for(String r:new String[]{"/net/vulkanmod/gl/NativeVertexArrayApi.class","/net/vulkanmod/gl/PhysicsVertexCallers.class","/net/vulkanmod/vulkan/shader/NativeVertexLayout.class","/net/vulkanmod/vulkan/device/NativeVertexDivisors.class","/net/vulkanmod/mixin/compatibility/physics/PhysicsVertexCallerMixin.class"})if(PhysicsVertexCallerAudit.class.getResource(r)==null)return false;return true;
 }catch(Exception e){return false;}}
}