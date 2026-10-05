package mvhshadercompat;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;

/**
 * Reads a bounded, explicit Vulkan uniform-block ABI from the compiled module.
 * It does not guess declaration order from GLSL or depend on optional debug names.
 * Supports 32-bit scalar/vector, column-major float matrices and one-dimensional
 * fixed arrays. Unsupported layouts fail before a descriptor or write is made.
 * Original implementation, GPL-3.0-only. Opcode/decorations: Khronos SPIR-V 1.6.
 */
public final class SpirvUniformBlock {
    public record Field(int ordinal, int offset, boolean floating, int rows, int columns,
                        int arrayLength, int arrayStride, int matrixStride, int extent) {
        public int components() { return Math.multiplyExact(Math.multiplyExact(rows,columns),arrayLength); }
    }
    private record Type(int opcode,int[] arguments) {}
    private record Variable(int pointerType,int id,int storage) {}
    private record Member(int type,int ordinal) {}
    private static final int MAX_BYTES=16*1024*1024, MAX_IDS=1_000_000;
    private final List<Field> fields;
    private final int bytes;
    private SpirvUniformBlock(List<Field> fields,int bytes) {this.fields=List.copyOf(fields);this.bytes=bytes;}
    public List<Field> fields(){return fields;}
    public int bytes(){return bytes;}
    public ByteBuffer allocate(){return ByteBuffer.allocateDirect(bytes).order(ByteOrder.LITTLE_ENDIAN);}

    public static SpirvUniformBlock reflect(ByteBuffer module,int descriptorSet,int binding){
        if(descriptorSet<0||binding<0)throw bad("Negative descriptor identity");
        ByteBuffer source=module.slice().order(ByteOrder.LITTLE_ENDIAN);
        if(source.remaining()<20||source.remaining()>MAX_BYTES||(source.remaining()&3)!=0||source.getInt(0)!=0x07230203)throw bad("Invalid SPIR-V size/header");
        int bound=source.getInt(12);if(bound<1||bound>MAX_IDS)throw bad("Unsupported ID bound");
        Map<Integer,Type> types=new HashMap<>();Map<Integer,Integer> constants=new HashMap<>();
        Map<Integer,Map<Integer,Integer>> decorations=new HashMap<>();
        Map<Member,Map<Integer,Integer>> members=new HashMap<>();List<Variable> variables=new ArrayList<>();
        Set<Integer> declared=new HashSet<>();
        for(int at=20;at<source.limit();){
            int header=source.getInt(at),words=header>>>16,op=header&65535;
            if(words==0||words>(source.limit()-at)/4)throw bad("Truncated or zero-length instruction");
            int[] a=new int[words-1];for(int i=0;i<a.length;i++)a[i]=source.getInt(at+4*(i+1));
            if(op>=20&&op<=32){
                require(a.length>=1,"Missing type ID");id(a[0],bound);require(declared.add(a[0]),"Duplicate result ID");
                types.put(a[0],new Type(op,Arrays.copyOfRange(a,1,a.length)));
            }else if(op==43){
                require(a.length>=3,"Short constant");id(a[1],bound);require(declared.add(a[1]),"Duplicate result ID");
                // Only ordinary 32-bit positive integer constants can size an array.
                if(a.length==3)constants.put(a[1],a[2]);
            }else if(op==59){
                require(a.length==3||a.length==4,"Short variable");id(a[1],bound);require(declared.add(a[1]),"Duplicate result ID");
                variables.add(new Variable(a[0],a[1],a[2]));
            }else if(op==71){
                require(a.length>=2,"Short decoration");id(a[0],bound);
                if(Set.of(2,6,33,34).contains(a[1])){
                    require(a.length==(a[1]==2?2:3),"Decoration operand count");
                    decorate(decorations.computeIfAbsent(a[0],x->new HashMap<>()),a[1],a.length==2?1:a[2]);
                }
            }else if(op==72){
                require(a.length>=3,"Short member decoration");id(a[0],bound);require(a[1]>=0,"Negative member ordinal");
                if(Set.of(4,5,7,35).contains(a[2])){
                    require(a.length==(a[2]==4||a[2]==5?3:4),"Member decoration operand count");
                    decorate(members.computeIfAbsent(new Member(a[0],a[1]),x->new HashMap<>()),a[2],a.length==3?1:a[3]);
                }
            }
            at+=words*4;
        }
        Integer struct=null;
        for(Variable variable:variables){
            Map<Integer,Integer> d=decorations.getOrDefault(variable.id,Map.of());
            if(variable.storage!=2||!Objects.equals(d.get(34),descriptorSet)||!Objects.equals(d.get(33),binding))continue;
            require(struct==null,"Ambiguous uniform descriptor");
            Type ptr=type(types,variable.pointerType,32);require(ptr.arguments.length==2&&ptr.arguments[0]==2,"Uniform pointer/storage mismatch");
            struct=ptr.arguments[1];type(types,struct,30);require(decorations.getOrDefault(struct,Map.of()).containsKey(2),"Uniform type lacks Block decoration");
        }
        require(struct!=null,"Uniform descriptor absent");
        Type block=type(types,struct,30);require(block.arguments.length>0&&block.arguments.length<=4096,"Unsupported member count");
        List<Field> result=new ArrayList<>();int end=0;
        for(int i=0;i<block.arguments.length;i++){
            Map<Integer,Integer> d=members.getOrDefault(new Member(struct,i),Map.of());
            require(d.containsKey(35)&&d.get(35)>=0&&(d.get(35)&3)==0,"Missing/invalid member Offset");
            int offset=d.get(35),typeId=block.arguments[i],arrayLength=1,arrayStride=0,matrixStride=0,columns=1,rows=1;
            Type t=types.get(typeId);require(t!=null,"Missing member type");
            if(t.opcode==28){
                require(t.arguments.length==2,"Array type operands");
                Integer length=constants.get(t.arguments[1]);require(length!=null&&length>0&&length<=MAX_BYTES/16,"Unsupported array length");
                arrayLength=length;Integer stride=decorations.getOrDefault(typeId,Map.of()).get(6);
                require(stride!=null&&stride>0&&(stride&15)==0,"Unsupported std140 ArrayStride");arrayStride=stride;
                t=types.get(t.arguments[0]);require(t!=null&&t.opcode!=28,"Nested arrays unsupported");
            }
            if(t.opcode==24){
                require(t.arguments.length==2&&t.arguments[1]>=2&&t.arguments[1]<=4,"Unsupported matrix dimensions");columns=t.arguments[1];
                require(d.containsKey(5)&&!d.containsKey(4),"Only explicit column-major matrices supported");
                Integer stride=d.get(7);require(stride!=null&&stride>=16&&(stride&15)==0,"Unsupported std140 MatrixStride");matrixStride=stride;
                t=type(types,t.arguments[0],23);
            }else require(!d.containsKey(4)&&!d.containsKey(5)&&!d.containsKey(7),"Matrix decorations on non-matrix");
            if(t.opcode==23){
                require(t.arguments.length==2&&t.arguments[1]>=2&&t.arguments[1]<=4,"Unsupported vector width");rows=t.arguments[1];
                t=types.get(t.arguments[0]);require(t!=null,"Missing scalar type");
            }
            boolean floating=t.opcode==22;
            require((floating&&t.arguments.length==1&&t.arguments[0]==32)||(t.opcode==21&&t.arguments.length==2&&t.arguments[0]==32&&(t.arguments[1]==0||t.arguments[1]==1)),"Only 32-bit float/int members supported");
            require(columns==1||floating,"Non-float matrix unsupported");
            int extent=Math.addExact(Math.multiplyExact(columns-1,matrixStride),Math.multiplyExact(rows,4));
            if(arrayStride!=0){require(arrayStride>=extent,"Array element overlap");extent=Math.addExact(Math.multiplyExact(arrayLength-1,arrayStride),extent);}
            require(offset>=end,"Overlapping or reordered member offsets");end=Math.addExact(offset,extent);require(end<=MAX_BYTES,"Block size limit");
            result.add(new Field(i,offset,floating,rows,columns,arrayLength,arrayStride,matrixStride,extent));
        }
        int size=Math.addExact(end,15)&~15;return new SpirvUniformBlock(result,size);
    }
    /** Values use column-major order within each matrix and array declaration order. */
    public void writeFloats(ByteBuffer target,int member,float... values){
        Field f=field(target,member,values.length,true);ByteBuffer out=target.duplicate().order(ByteOrder.LITTLE_ENDIAN);int v=0;
        for(int a=0;a<f.arrayLength;a++)for(int c=0;c<f.columns;c++)for(int r=0;r<f.rows;r++)out.putFloat(f.offset+a*f.arrayStride+c*f.matrixStride+r*4,values[v++]);
    }
    public void writeInts(ByteBuffer target,int member,int... values){
        Field f=field(target,member,values.length,false);ByteBuffer out=target.duplicate().order(ByteOrder.LITTLE_ENDIAN);int v=0;
        for(int a=0;a<f.arrayLength;a++)for(int r=0;r<f.rows;r++)out.putInt(f.offset+a*f.arrayStride+r*4,values[v++]);
    }
    private Field field(ByteBuffer target,int member,int count,boolean floating){
        require(member>=0&&member<fields.size(),"Unknown member ordinal");Field f=fields.get(member);
        require(target.capacity()>=bytes&&target.limit()>=bytes&&!target.isReadOnly(),"Short/read-only uniform destination");
        require(f.floating==floating&&count==f.components(),"Uniform write type/arity mismatch");return f;
    }
    private static Type type(Map<Integer,Type> types,int id,int opcode){Type t=types.get(id);require(t!=null&&t.opcode==opcode,"Missing or wrong type");return t;}
    private static void id(int id,int bound){require(id>0&&id<bound,"ID outside module bound");}
    private static void decorate(Map<Integer,Integer> out,int kind,int value){require(out.putIfAbsent(kind,value)==null,"Repeated layout decoration");}
    private static void require(boolean value,String why){if(!value)throw bad(why);}
    private static IllegalArgumentException bad(String why){return new IllegalArgumentException(why);}
}
