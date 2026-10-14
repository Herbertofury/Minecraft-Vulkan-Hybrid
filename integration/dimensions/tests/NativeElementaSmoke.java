package mvhpackbench;

import com.google.gson.*;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.vulkanmod.gl.*;
import net.vulkanmod.vulkan.framebuffer.*;
import net.minecraft.client.Minecraft;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.VRenderSystem;
import net.vulkanmod.vulkan.texture.*;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryUtil;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.nio.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import static org.lwjgl.vulkan.VK10.*;

/** Original widget coordinates and glyph UV/order on diagnostic quads, installed atlas and original uniforms; no FPS. */
final class NativeElementaSmoke {
    static void run(JsonArray cases)throws Exception {
        var renderer=Renderer.getInstance();var previous=renderer.getBoundRenderPass();
        var state=new NativeStencilSmoke.State();var projection=new Matrix4f(RenderSystem.getProjectionMatrix());
        var model=RenderSystem.getModelViewStack();model.pushPose();model.setIdentity();RenderSystem.applyModelViewMatrix();
        var oldTexture=VTextureSelector.getImage(0);VulkanImage target=null;Framebuffer fb=null;
        try {
            target=VulkanImage.builder(32,32).setFormat(VK_FORMAT_R8G8B8A8_UNORM).setUsage(VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT|VK_IMAGE_USAGE_TRANSFER_SRC_BIT|VK_IMAGE_USAGE_SAMPLED_BIT).setLinearFiltering(false).createVulkanImage();
            fb=Framebuffer.builder(target,null).build();
            if(!renderer.beginRendering(RenderPass.builder(fb).build(),fb))throw new IllegalStateException("Original atlas native pass rejected");
            NativeViewportApi.glViewport(0,0,32,32);Renderer.setScissorEnabled(false);
            RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0,32,32,0,-1,1),VertexSorting.ORTHOGRAPHIC_Z);
            RenderSystem.disableBlend();RenderSystem.disableDepthTest();RenderSystem.depthMask(false);RenderSystem.disableCull();
            VRenderSystem.disableStencilTest();VRenderSystem.colorMask(true,true,true,true);
            drawOriginal(target,cases);
        } finally {
            GlProgram.useProgram(0);renderer.endRenderPass();renderer.beginRendering(previous,previous.getFramebuffer());
            Renderer.setInvertedViewport(0,0,previous.getFramebuffer().getWidth(),previous.getFramebuffer().getHeight());
            Renderer.setScissor(0,0,previous.getFramebuffer().getWidth(),previous.getFramebuffer().getHeight());
            VTextureSelector.bindTexture(0,oldTexture);state.restore();
            RenderSystem.setProjectionMatrix(projection,VertexSorting.ORTHOGRAPHIC_Z);model.popPose();RenderSystem.applyModelViewMatrix();
            if(fb!=null)fb.cleanUp();else if(target!=null)target.free();
        }
    }
    private static void quad(boolean font,float left,float top,float right,float bottom,int r,int g,int b,int a,float u0,float v0,float u1,float v1) {
        var vertices=Tesselator.getInstance().getBuilder();vertices.begin(VertexFormat.Mode.QUADS,font?DefaultVertexFormat.POSITION_TEX:DefaultVertexFormat.POSITION_COLOR);
        float[][] points={{left,bottom,u0,v1},{right,bottom,u1,v1},{right,top,u1,v0},{left,top,u0,v0}};
        for(var point:points){var vertex=vertices.vertex(point[0],point[1],0);if(font)vertex.uv(point[2],point[3]);else vertex.color(r,g,b,a);vertex.endVertex();}
        BufferUploader.drawWithShader(vertices.end());
    }
    private static void release(OriginalElementaAtlasProof original,int program)throws Exception {
        original.unbind();GlProgram.deleteProgram(program);if(GlProgram.isProgram(program))throw new IllegalStateException("Original atlas native program retirement failed");
    }
    private static void drawOriginal(VulkanImage target,JsonArray cases)throws Exception {
        Path root=Minecraft.getInstance().gameDirectory.toPath().resolve("mvh-elementa-native-fixture");
        String fontPng="d53e2485934208c15a98c5987b53c0bdce4b3e909619a0548f43848d5d355ce8",fontJson="be85583216e8a3a82c73b6be26feca680b135dcc3b3c2077e3ce541a6f03f140";
        byte[] png=Files.readAllBytes(root.resolve("JetBrainsMono-Regular.png")),json=Files.readAllBytes(root.resolve("JetBrainsMono-Regular.json"));
        if(!digest(png).equals(fontPng)||!digest(json).equals(fontJson))throw new IllegalStateException("Original installed font resources changed");
        BufferedImage image=ImageIO.read(new java.io.ByteArrayInputStream(png));if(image==null||image.getWidth()!=836||image.getHeight()!=836)throw new IllegalStateException("Original font atlas dimensions");
        JsonObject data=JsonParser.parseString(new String(json,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        JsonObject glyph=null;for(var g:data.getAsJsonArray("glyphs"))if(g.getAsJsonObject().get("unicode").getAsInt()==65)glyph=g.getAsJsonObject();if(glyph==null)throw new IllegalStateException("Original A glyph metadata absent");
        var bounds=glyph.getAsJsonObject("atlasBounds");float u0=bounds.get("left").getAsFloat()/836,u1=bounds.get("right").getAsFloat()/836,v0=1-bounds.get("top").getAsFloat()/836,v1=1-bounds.get("bottom").getAsFloat()/836;
        try(var original=new OriginalElementaAtlasProof()) {
            int circle=original.compile("circle");
            try {
                original.uniform("u_Radius",11);original.uniform("u_CenterPos",16,16);clear();quad(false,5,5,27,27,51,204,102,255,0,0,1,1);
                compare(target,cases,"original_circle_geometry_and_uniforms",(x,y)->inside(x,y,5,5,27,27)?rgba(.2f,.8f,.4f,1-smooth(10,11,(float)Math.hypot(x+.5-16,y+.5-16))):blue());
                original.uniform("u_Radius",6);clear();quad(false,10,10,22,22,51,204,102,255,0,0,1,1);
                compare(target,cases,"original_circle_live_radius_update",(x,y)->inside(x,y,10,10,22,22)?rgba(.2f,.8f,.4f,1-smooth(5,6,(float)Math.hypot(x+.5-16,y+.5-16))):blue());
                RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0,32,32,0,-1,1).translate(3,0,0),VertexSorting.ORTHOGRAPHIC_Z);
                original.uniform("u_CenterPos",16,16);clear();quad(false,10,10,22,22,51,204,102,255,0,0,1,1);
                compare(target,cases,"original_circle_live_matrix_update",(x,y)->inside(x,y,13,10,25,22)?rgba(.2f,.8f,.4f,1-smooth(5,6,(float)Math.hypot(x+.5-19,y+.5-16))):blue());
            }finally{release(original,circle);}
            RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0,32,32,0,-1,1),VertexSorting.ORTHOGRAPHIC_Z);
            int rounded=original.compile("rounded_rect");
            try {
                original.uniform("u_Radius",4);original.uniform("u_InnerRect",8,10,24,22);clear();quad(false,4,6,28,26,204,51,153,255,0,0,1,1);
                compare(target,cases,"original_rounded_rectangle_geometry_and_uniforms",(x,y)->{if(!inside(x,y,4,6,28,26))return blue();float dx=Math.max(0,Math.max(8-(x+.5f),(x+.5f)-24)),dy=Math.max(0,Math.max(10-(y+.5f),(y+.5f)-22));return rgba(.8f,.2f,.6f,1-smooth(0,1,(float)Math.hypot(dx,dy)-4));});
            }finally{release(original,rounded);}
        VulkanImage texture=null;ByteBuffer pixels=MemoryUtil.memAlloc(836*836*4);
        try{
            for(int y=0;y<836;y++)for(int x=0;x<836;x++){int c=image.getRGB(x,y);pixels.put((byte)(c>>16)).put((byte)(c>>8)).put((byte)c).put((byte)(c>>24));}pixels.flip();
            texture=VulkanImage.builder(836,836).setFormat(VK_FORMAT_R8G8B8A8_UNORM).setUsage(VK_IMAGE_USAGE_SAMPLED_BIT|VK_IMAGE_USAGE_TRANSFER_DST_BIT).setLinearFiltering(true).createVulkanImage();
            texture.uploadSubTextureAsync(0,836,836,0,0,0,0,836,pixels);
            VTextureSelector.bindTexture(0,texture);
            int font=original.compile("font");
            try {
                original.uniform("fgColor",.2f,.8f,.4f,1);original.uniform("doffset",3.5f/32);original.uniform("hint_amount",0);original.uniform("subpixel_amount",0);original.uniform("sdf_texel",1f/836,1f/836);original.integer("msdf",0);
                clear();quad(true,4,2,28,30,255,255,255,255,u0,v0,u1,v1);
                compare(target,cases,"original_msdf_A_glyph_atlas_and_geometry",(x,y)->fontExpected(image,x,y,u0,v0,u1,v1,.2f,.8f,.4f,32,0));
                original.uniform("fgColor",.9f,.1f,.6f,1);clear();quad(true,4,2,28,30,255,255,255,255,u0,v0,u1,v1);
                compare(target,cases,"original_msdf_live_foreground_uniform",(x,y)->fontExpected(image,x,y,u0,v0,u1,v1,.9f,.1f,.6f,32,0));
                original.uniform("fgColor",.2f,.8f,.4f,1);original.uniform("hint_amount",1);clear();quad(true,4,2,28,30,255,255,255,255,u0,v0,u1,v1);
                compare(target,cases,"original_msdf_live_hinting_uniform",(x,y)->fontExpected(image,x,y,u0,v0,u1,v1,.2f,.8f,.4f,32,1));
            }finally{release(original,font);}
            original.complete();
        }finally{MemoryUtil.memFree(pixels);if(texture!=null)texture.free();}
        }
    }
    private static String digest(byte[] raw)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw));}
    private static void clear(){VRenderSystem.setClearColor(0,0,1,1);Renderer.clearAttachments(16384);}
    private static boolean inside(int x,int y,int l,int t,int r,int b){return x>=l&&x<r&&y>=t&&y<b;}
    private static int[] blue(){return new int[]{0,0,255,255};}
    private static float smooth(float a,float b,float x){float t=Math.max(0,Math.min(1,(x-a)/(b-a)));return t*t*(3-2*t);}
    private static int[] rgba(float r,float g,float b,float a){return new int[]{Math.round(r*255),Math.round(g*255),Math.round(b*255),Math.round(a*255)};}
    private static float sdf(BufferedImage image,float u,float v){float fx=u*836-.5f,fy=v*836-.5f;int x=(int)Math.floor(fx),y=(int)Math.floor(fy);float a=fx-x,b=fy-y;float[] c=new float[3];
        for(int k=0;k<3;k++){int shift=16-k*8;float t0=(((image.getRGB(Math.floorMod(x,836),Math.floorMod(y,836))>>shift)&255)*(1-a)+((image.getRGB(Math.floorMod(x+1,836),Math.floorMod(y,836))>>shift)&255)*a)/255f;float t1=(((image.getRGB(Math.floorMod(x,836),Math.floorMod(y+1,836))>>shift)&255)*(1-a)+((image.getRGB(Math.floorMod(x+1,836),Math.floorMod(y+1,836))>>shift)&255)*a)/255f;c[k]=t0*(1-b)+t1*b;}return Math.max(Math.min(c[0],c[1]),Math.min(Math.max(c[0],c[1]),c[2]));}
    private static int[] fontExpected(BufferedImage image,int x,int y,float u0,float v0,float u1,float v1,float r,float g,float b,float pointSize,float hint){
        if(!inside(x,y,4,2,28,30))return blue();float u=u0+(u1-u0)*(x+.5f-4)/24,v=v0+(v1-v0)*(y+.5f-2)/28;
        float s=sdf(image,u,v),north=sdf(image,u,v+1f/836),east=sdf(image,u+1f/836,v);float dx=east-s,dy=north-s,length=Math.max((float)Math.hypot(dx,dy),1f/128);float vertical=Math.abs(dy/length);float d=3.5f/pointSize,scaled=d*(1.1f+(0.8f-1.1f)*vertical),offset=d+(scaled-d)*hint;float alpha=smooth(.5f-offset,.5f+offset,s);return alpha<20f/256?blue():rgba(r,g,b,alpha);
    }
    private interface Expected{int[] at(int x,int y);}
    private static void compare(VulkanImage target,JsonArray cases,String label,Expected expected)throws Exception{
        ByteBuffer pixels=MemoryUtil.memAlloc(32*32*4);try{ImageUtil.downloadTexture(target,MemoryUtil.memAddress(pixels));int mismatches=0,maxError=0,ink=0;JsonArray samples=new JsonArray();
            for(int y=0;y<32;y++)for(int x=0;x<32;x++){int[] color=expected.at(x,y);boolean bad=false;for(int k=0;k<4;k++){int error=Math.abs(Byte.toUnsignedInt(pixels.get((y*32+x)*4+k))-color[k]);maxError=Math.max(maxError,error);bad|=error>2;}if(bad)mismatches++;if(color[0]!=0||color[1]!=0||color[2]!=255)ink++;
                if((x==16&&y==16)||(x==8&&y==8)){JsonArray sample=new JsonArray();sample.add(x);sample.add(y);for(int k=0;k<4;k++)sample.add(Byte.toUnsignedInt(pixels.get((y*32+x)*4+k)));samples.add(sample);}}
            byte[] bytes=new byte[pixels.capacity()];pixels.get(0,bytes);JsonObject row=new JsonObject();row.addProperty("case",label);row.addProperty("matched",mismatches==0&&ink>0);row.addProperty("compared_pixels",1024);row.addProperty("mismatched_pixels",mismatches);row.addProperty("maximum_channel_error",maxError);row.addProperty("tolerance_channels",2);row.addProperty("reference_ink_pixels",ink);row.addProperty("actual_rgba_sha256",digest(bytes));row.add("actual_samples_xy_rgba",samples);cases.add(row);
            Path dir=Minecraft.getInstance().gameDirectory.toPath().resolve("mvh-elementa-readback");Files.createDirectories(dir);Files.write(dir.resolve(label+".rgba"),bytes);
        }finally{MemoryUtil.memFree(pixels);}
    }
}
