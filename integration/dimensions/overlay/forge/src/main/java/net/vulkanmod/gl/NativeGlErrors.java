package net.vulkanmod.gl;
/** Sticky error transport for implemented native viewport and fence operations only. */
public final class NativeGlErrors {
    private static final ThreadLocal<Integer> ERROR=ThreadLocal.withInitial(()->0);
    private NativeGlErrors(){}
    public static void record(int code){if(ERROR.get()==0)ERROR.set(code);}
    public static int consume(){int result=ERROR.get();ERROR.set(0);return result;}
}
