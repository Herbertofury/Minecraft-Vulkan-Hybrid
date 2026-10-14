import net.vulkanmod.gl.NativeShaderApi;
import net.vulkanmod.compat.UniversalRendererGate;
import org.lwjgl.opengl.*;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryUtil;
import java.nio.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import static org.lwjgl.opengl.GL11.*;

/** Actual hidden NVIDIA OpenGL context, packaged production facade, original private shader inputs. */
public final class ShaderCallerOpenGlRegression {
    private static int controls;
    private static boolean arb;
    private static void require(boolean pass,String reason){controls++;if(!pass)throw new AssertionError(reason);}
    private static String hash(byte[] bytes)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
    private static String source(Path path,String digest)throws Exception{byte[] bytes=Files.readAllBytes(path);require(hash(bytes).equals(digest),"Original shader changed");return new String(bytes,java.nio.charset.StandardCharsets.UTF_8);}
    private static int compile(String vs,String fs)throws Exception {
        int v=arb?NativeShaderApi.glCreateShaderObjectARB(GL20C.GL_VERTEX_SHADER):NativeShaderApi.glCreateShader(GL20C.GL_VERTEX_SHADER);
        int f=arb?NativeShaderApi.glCreateShader(GL20C.GL_FRAGMENT_SHADER):NativeShaderApi.glCreateShaderObjectARB(GL20C.GL_FRAGMENT_SHADER);
        int p=arb?NativeShaderApi.glCreateProgramObjectARB():NativeShaderApi.glCreateProgram();require(v!=f&&v!=p&&f!=p,"Actual driver shared namespace collision");
        NativeShaderApi.glShaderSource(v,vs);NativeShaderApi.glShaderSourceARB(f,fs);NativeShaderApi.glCompileShaderARB(v);NativeShaderApi.glCompileShader(f);
        require(NativeShaderApi.glGetObjectParameteriARB(v,GL20C.GL_COMPILE_STATUS)==1,NativeShaderApi.glGetInfoLogARB(v));require(NativeShaderApi.glGetShaderi(f,GL20C.GL_COMPILE_STATUS)==1,NativeShaderApi.glGetShaderInfoLog(f));require(NativeShaderApi.glGetShaderSourceARB(v).equals(vs),"Actual driver source query");
        NativeShaderApi.glAttachShader(p,v);NativeShaderApi.glAttachObjectARB(p,f);NativeShaderApi.glLinkProgramARB(p);require(NativeShaderApi.glGetProgrami(p,GL20C.GL_LINK_STATUS)==1,NativeShaderApi.glGetProgramInfoLog(p));
        NativeShaderApi.glDeleteObjectARB(v);NativeShaderApi.glDeleteShader(f);require(NativeShaderApi.glGetShaderi(v,GL20C.GL_DELETE_STATUS)==1,"Actual attached shader lifetime");NativeShaderApi.glDetachShader(p,v);NativeShaderApi.glDetachObjectARB(p,f);require(!NativeShaderApi.glIsShader(v)&&!NativeShaderApi.glIsShader(f),"Actual driver shader deletion");return p;
    }
    private static int location(int p,String name){ByteBuffer bytes=ByteBuffer.allocateDirect(name.length()+4);bytes.position(2);bytes.put(name.getBytes(java.nio.charset.StandardCharsets.UTF_8)).put((byte)0);bytes.flip().position(2);int cursor=bytes.position();int loc=arb?NativeShaderApi.glGetUniformLocationARB(p,bytes):NativeShaderApi.glGetUniformLocation(p,bytes);require((loc>=0||name.equals("subpixel_amount")&&loc==-1)&&bytes.position()==cursor,"Original active uniform name/cursor: "+name+" location="+loc);require(loc==NativeShaderApi.glGetUniformLocation(p,name)&&loc==NativeShaderApi.glGetUniformLocationARB(p,name),"Actual core/ARB uniform location");return loc;}
    private static void uniform(int p,String name,float...v){int l=location(p,name);switch(v.length){case 1->{if(arb)NativeShaderApi.glUniform1fARB(l,v[0]);else NativeShaderApi.glUniform1f(l,v[0]);}case 2->{if(arb)NativeShaderApi.glUniform2fARB(l,v[0],v[1]);else NativeShaderApi.glUniform2f(l,v[0],v[1]);}case 4->{if(arb)NativeShaderApi.glUniform4fARB(l,v[0],v[1],v[2],v[3]);else NativeShaderApi.glUniform4f(l,v[0],v[1],v[2],v[3]);}default->throw new AssertionError("Unreviewed uniform");}}
    private static void use(int p){if(arb)NativeShaderApi.glUseProgramObjectARB(p);else NativeShaderApi.glUseProgram(p);}
    private static double smooth(double lo,double hi,double x){double t=Math.max(0,Math.min(1,(x-lo)/(hi-lo)));return t*t*(3-2*t);}
    private static String pixels(String kind,int radius,int iteration,Path output)throws Exception{
        ByteBuffer read=MemoryUtil.memAlloc(4096);try{glFinish();glReadPixels(0,0,32,32,GL_RGBA,GL_UNSIGNED_BYTE,read);int bad=0,max=0;
            for(int y=0;y<32;y++)for(int x=0;x<32;x++){double alpha;if(kind.equals("circle"))alpha=(1-smooth(radius-1,radius,Math.hypot(x+0.5-16,y+0.5-16)))*204/255.0;else if(kind.equals("rounded_rect")){double dx=Math.max(x+0.5-24,8-x-0.5),dy=Math.max(y+0.5-24,8-y-0.5);alpha=(1-smooth(0,1,Math.hypot(Math.max(0,dx),Math.max(0,dy))-radius))*204/255.0;}else alpha=1;
                double[] rgb=kind.equals("font")?new double[]{0.1,0.7,0.2}:kind.equals("vanilla-restored")?new double[]{1,0,0}:new double[]{64/255.0,128/255.0,1};boolean mismatch=false;for(int c=0;c<4;c++){int expected=(int)Math.round(255*alpha*(c==3?1:rgb[c]));int error=Math.abs(Byte.toUnsignedInt(read.get((y*32+x)*4+c))-expected);max=Math.max(max,error);if(error>1)mismatch=true;}if(mismatch)bad++;}
            byte[] bytes=new byte[4096];read.get(0,bytes);Files.write(output.resolve(kind+"-"+iteration+".rgba"),bytes);require(bad==0,"Original OpenGL pixels mismatch "+kind+" count="+bad+" max="+max);
            return "{\"case\":\""+kind+"-"+iteration+"\",\"compared_pixels\":1024,\"mismatched_pixels\":"+bad+",\"max_channel_difference\":"+max+",\"channel_tolerance\":1,\"actual_rgba_sha256\":\""+hash(bytes)+"\"}";
        }finally{MemoryUtil.memFree(read);}
    }
    public static void main(String[] args)throws Exception{
        Path inputs=Path.of(args[0]),output=Path.of(args[1]);Files.createDirectories(output);require(!UniversalRendererGate.vulkanRendererEnabled(),"Actual OpenGL fallback branch required");require(Path.of(NativeShaderApi.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath().equals(Path.of(args[2]).toRealPath()),"Facade is not owned by frozen production artifact");
        String rect=source(inputs.resolve("003-rect.vsh"),"716c2484e10c915b45f45c3bdf34e7209b490d9d1fbd28885262eb44e49fe01d"),font=source(inputs.resolve("002-font.vsh"),"24fb624551cb215701d44750a9a9bc224df429f3fa1832644af2948d1d453952");
        Map<String,String> fragments=Map.of("circle",source(inputs.resolve("000-circle.fsh"),"259cff0e9e440212db1714c3e28ca39e2d632d9c328a7f4e3275f81a2739f9b7"),"rounded_rect",source(inputs.resolve("004-rounded_rect.fsh"),"62998adcadedb6287ffbaa94d139c8786388bd5c57b64b288a2fadf243cc5d62"),"font",source(inputs.resolve("001-font.fsh"),"47efc60d7827351f0627c8191cffc5fba9d0c419c336a62702a380ff64c99ade"));
        require(GLFW.glfwInit(),"GLFW init");long window=0;int target=0,framebuffer=0,white=0;List<String> cases=new ArrayList<>();
        try{
            GLFW.glfwDefaultWindowHints();GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);window=GLFW.glfwCreateWindow(32,32,"MVH private shader transport proof",0,0);require(window!=0,"Hidden actual OpenGL context creation");GLFW.glfwMakeContextCurrent(window);GL.createCapabilities();String vendor=glGetString(GL_VENDOR),renderer=glGetString(GL_RENDERER),version=glGetString(GL_VERSION);require(vendor.contains("NVIDIA")&&renderer.contains("RTX 4090"),"Actual NVIDIA hardware required");
            target=glGenTextures();glBindTexture(GL_TEXTURE_2D,target);glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA8,32,32,0,GL_RGBA,GL_UNSIGNED_BYTE,(ByteBuffer)null);framebuffer=GL30C.glGenFramebuffers();GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER,framebuffer);GL30C.glFramebufferTexture2D(GL30C.GL_FRAMEBUFFER,GL30C.GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,target,0);require(GL30C.glCheckFramebufferStatus(GL30C.GL_FRAMEBUFFER)==GL30C.GL_FRAMEBUFFER_COMPLETE,"Actual framebuffer incomplete");
            white=glGenTextures();glBindTexture(GL_TEXTURE_2D,white);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_NEAREST);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_NEAREST);ByteBuffer texel=ByteBuffer.allocateDirect(4);texel.putInt(0xffffffff).flip();glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA8,1,1,0,GL_RGBA,GL_UNSIGNED_BYTE,texel);
            glViewport(0,0,32,32);glMatrixMode(GL_PROJECTION);glLoadIdentity();glOrtho(0,32,32,0,-1,1);glMatrixMode(GL_MODELVIEW);glLoadIdentity();glDisable(GL_DEPTH_TEST);glDisable(GL_CULL_FACE);glEnable(GL_BLEND);GL14C.glBlendFuncSeparate(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA,GL_ONE,GL_ONE_MINUS_SRC_ALPHA);
            for(String kind:List.of("circle","rounded_rect","font"))for(int iteration=0;iteration<2;iteration++){
                arb=iteration==0;int p=compile(kind.equals("font")?font:rect,fragments.get(kind));use(p);boolean deletionRequested=false;
                try{
                    if(kind.equals("font")){uniform(p,"fgColor",0.1f,0.7f,0.2f,1);uniform(p,"doffset",0.1f);uniform(p,"hint_amount",0.5f);uniform(p,"subpixel_amount",0);uniform(p,"sdf_texel",1f/32,1f/32);int l=location(p,"msdf");if(arb)NativeShaderApi.glUniform1iARB(l,0);else NativeShaderApi.glUniform1i(l,0);}else{uniform(p,"u_Radius",iteration==0?8:4);if(kind.equals("circle"))uniform(p,"u_CenterPos",16,16);else uniform(p,"u_InnerRect",8,8,24,24);}
                    if(arb)NativeShaderApi.glValidateProgramARB(p);else NativeShaderApi.glValidateProgram(p);require(NativeShaderApi.glGetProgrami(p,GL20C.GL_VALIDATE_STATUS)==1,"Actual driver validation");NativeShaderApi.glDeleteObjectARB(p);deletionRequested=true;require(NativeShaderApi.glIsProgram(p)&&NativeShaderApi.glGetProgrami(p,GL20C.GL_DELETE_STATUS)==1,"Actual deferred current-program delete");
                    glClearColor(0,0,0,0);glClear(GL_COLOR_BUFFER_BIT);glColor4ub((byte)64,(byte)128,(byte)255,(byte)204);glBegin(GL_QUADS);for(int[] point:new int[][]{{0,32},{32,32},{32,0},{0,0}}){glTexCoord2f(point[0]/32f,point[1]/32f);glVertex3f(point[0],point[1],0);}glEnd();cases.add(pixels(kind,iteration==0?8:4,iteration,output));
                }finally{use(0);if(!deletionRequested)NativeShaderApi.glDeleteProgram(p);int current=glGetInteger(GL20C.GL_CURRENT_PROGRAM);int driverError=glGetError();glFinish();boolean retained=NativeShaderApi.glIsProgram(p);System.out.println("OPENGL_LIFETIME_OBSERVATION program="+p+" current="+current+" error="+driverError+" retained="+retained);require(current==0&&driverError==GL_NO_ERROR,"Actual driver unbind/error");require(!retained,"Actual driver program survived unbind");}
            }
            glDisable(GL_BLEND);glClearColor(0,0,0,0);glClear(GL_COLOR_BUFFER_BIT);glColor4f(1,0,0,1);glBegin(GL_QUADS);glVertex3f(0,32,0);glVertex3f(32,32,0);glVertex3f(32,0,0);glVertex3f(0,0,0);glEnd();cases.add(pixels("vanilla-restored",0,0,output));require(glGetError()==GL_NO_ERROR,"Actual GL error");
            String report="{\"passed\":true,\"controls\":"+controls+",\"gpu\":\""+renderer+"\",\"vendor\":\""+vendor+"\",\"version\":\""+version+"\",\"production_facade_opengl_branch\":true,\"native_kernel_initialization_checked_separately\":true,\"cases\":["+String.join(",",cases)+"],\"scope\":\"Actual standalone NVIDIA OpenGL context and original shader pixels via frozen production shader facade. No Minecraft/Embeddium/fullpack/FPS/startup acceptance; existing native-required game profiles unchanged.\"}";
            Files.writeString(output.resolve("REPORT.json"),report);System.out.println("PRODUCTION_CALLER_OPENGL_HARDWARE_PASS cases="+cases.size()+" controls="+controls+" gpu="+renderer);
        }finally{if(window!=0){if(framebuffer!=0)GL30C.glDeleteFramebuffers(framebuffer);if(target!=0)glDeleteTextures(target);if(white!=0)glDeleteTextures(white);GLFW.glfwDestroyWindow(window);}GLFW.glfwTerminate();}
    }
}
