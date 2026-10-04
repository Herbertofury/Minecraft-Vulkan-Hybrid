package com.axalotl.async.forge.client.hari263.render;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;

/** Zero-to-one clip-depth frustum extraction matching Minecraft/Sodium 1.20.x. */
public final class FrustumUtil {
    private FrustumUtil() {}
    public static void extractPlanes(Matrix4fc clip, float[] out24) {
        write(out24,0,clip.m03()+clip.m00(),clip.m13()+clip.m10(),clip.m23()+clip.m20(),clip.m33()+clip.m30());
        write(out24,1,clip.m03()-clip.m00(),clip.m13()-clip.m10(),clip.m23()-clip.m20(),clip.m33()-clip.m30());
        write(out24,2,clip.m03()+clip.m01(),clip.m13()+clip.m11(),clip.m23()+clip.m21(),clip.m33()+clip.m31());
        write(out24,3,clip.m03()-clip.m01(),clip.m13()-clip.m11(),clip.m23()-clip.m21(),clip.m33()-clip.m31());
        write(out24,4,clip.m02(),clip.m12(),clip.m22(),clip.m32());
        write(out24,5,clip.m03()-clip.m02(),clip.m13()-clip.m12(),clip.m23()-clip.m22(),clip.m33()-clip.m32());
    }
    public static void extractPlanes(Matrix4f clip, float[] out24) { extractPlanes((Matrix4fc) clip, out24); }
    private static void write(float[] out,int i,float x,float y,float z,float w) {
        float len=(float)Math.sqrt(x*x+y*y+z*z);
        if(len>1e-8f){x/=len;y/=len;z/=len;w/=len;}
        int o=i*4; out[o]=x; out[o+1]=y; out[o+2]=z; out[o+3]=w;
    }
}
