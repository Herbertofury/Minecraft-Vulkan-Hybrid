import mvhgrasscompat.ExactFrustumCache;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import java.util.Random;
/** Independent JOML visibility oracle, changed projections/cameras/bounds and bounded-cache turnover. */
public final class ExactFrustumCacheRegression {
    static int checks;
    static void check(boolean value){if(!value)throw new AssertionError("Visibility cache control "+checks);checks++;}
    static boolean oracle(float[] matrix,double[] camera,double[] b){return new FrustumIntersection(new Matrix4f().set(matrix)).testAab((float)(b[0]-camera[0]),(float)(b[1]-camera[1]),(float)(b[2]-camera[2]),(float)(b[3]-camera[0]),(float)(b[4]-camera[1]),(float)(b[5]-camera[2]));}
    static byte lookup(ExactFrustumCache c,double[] b){return c.lookup(b[0],b[1],b[2],b[3],b[4],b[5]);}
    static void put(ExactFrustumCache c,double[] b,boolean v){c.put(b[0],b[1],b[2],b[3],b[4],b[5],v);}
    public static void main(String[] args){
        ExactFrustumCache c=new ExactFrustumCache();float[] matrix=new Matrix4f().perspective(1.2f,16f/9f,.1f,600f).get(new float[16]);double[] camera={1,112,3},b={-1,109,-20,3,113,-16};
        c.state(matrix,camera[0],camera[1],camera[2]);boolean v=oracle(matrix,camera,b);check(lookup(c,b)==0);put(c,b,v);check(lookup(c,b)==(v?2:1));c.state(matrix.clone(),camera[0],camera[1],camera[2]);check(lookup(c,b)==(v?2:1));
        for(int i=0;i<16;i++){float[] changed=matrix.clone();changed[i]=Math.nextUp(changed[i]);c.state(changed,camera[0],camera[1],camera[2]);check(lookup(c,b)==0);put(c,b,false);c.state(matrix,camera[0],camera[1],camera[2]);check(lookup(c,b)==0);put(c,b,true);}
        for(int i=0;i<3;i++){double[] changed=camera.clone();changed[i]=Math.nextUp(changed[i]);c.state(matrix,changed[0],changed[1],changed[2]);check(lookup(c,b)==0);put(c,b,false);c.state(matrix,camera[0],camera[1],camera[2]);put(c,b,true);}
        for(int i=0;i<6;i++){double[] changed=b.clone();changed[i]=Math.nextUp(changed[i]);check(lookup(c,changed)==0);put(c,changed,false);check(lookup(c,b)==2);}
        c.clear();check(lookup(c,b)==0);c.state(matrix,0,0,0);put(c,b,true);c.state(matrix,-0.0,0,0);check(lookup(c,b)==0);
        Random random=new Random(548276);
        for(int frame=0;frame<20;frame++){
            matrix=new Matrix4f().perspective(.6f+random.nextFloat(),1.6f,.1f,600f).rotateY(random.nextFloat()*6).get(matrix);for(int j=0;j<3;j++)camera[j]=random.nextDouble()*100;
            c.state(matrix,camera[0],camera[1],camera[2]);
            for(int n=0;n<1000;n++){for(int j=0;j<3;j++){b[j]=random.nextDouble()*1000-500;b[j+3]=b[j]+16;}v=oracle(matrix,camera,b);check(lookup(c,b)==0);put(c,b,v);check(lookup(c,b)==(v?2:1));}
        }
        c.state(matrix,camera[0],camera[1],camera[2]);for(int n=0;n<20000;n++){b[0]=n;b[3]=n+16;v=oracle(matrix,camera,b);put(c,b,v);check(lookup(c,b)==(v?2:1));}
        System.out.println("EXACT_FRUSTUM_CACHE_CONTROLS "+checks);
    }
}
