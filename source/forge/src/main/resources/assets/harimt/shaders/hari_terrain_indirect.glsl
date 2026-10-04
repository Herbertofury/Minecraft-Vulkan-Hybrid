#version 430
layout(local_size_x = 64) in;

// Hari 26.3 bridge: GPU section/frustum visibility + indirect-command generation only.
// Embeddium remains authoritative for vertex data, UVs, lighting, materials and chunk meshes.
struct MeshletHeader {
    uint vertexOffset;
    uint vertexCount;
    uint primitiveOffset;
    uint primitiveCount;
    uint sectionIndex;
    uint facingMask;
    uint reserved0;
    uint reserved1;
    vec4 boundingSphere;
    vec4 normalCone;
};
struct DrawElementsIndirectCommand {
    uint count;
    uint instanceCount;
    uint firstIndex;
    int baseVertex;
    uint baseInstance;
};
layout(std430,binding=0) readonly buffer Meshlets { MeshletHeader meshlets[]; };
layout(std430,binding=1) readonly buffer SectionMasks { uint sectionMasks[]; };
layout(std430,binding=2) buffer Counters { uint visibleCount; uint frustumCulled; uint sectionCulled; uint reservedCounter; };
layout(std430,binding=3) writeonly buffer OutCommands { DrawElementsIndirectCommand cmds[]; };
uniform vec4 uFrustumPlanes[6];
uniform vec3 uCameraPos;
uniform uint uMeshletCount;
uniform uint uUseSectionMasks;
shared uint flags[64];
shared uint groupBase;

bool outsideFrustum(vec4 sphere){
    vec3 center=sphere.xyz-uCameraPos;
    for(int i=0;i<6;i++) if(dot(uFrustumPlanes[i].xyz,center)+uFrustumPlanes[i].w < -sphere.w) return true;
    return false;
}
void main(){
    uint tid=gl_LocalInvocationID.x;
    uint id=gl_GlobalInvocationID.x;
    bool visible=false;
    MeshletHeader m;
    if(id<uMeshletCount){
        m=meshlets[id];
        bool sectionVisible = uUseSectionMasks == 0u || (sectionMasks[m.sectionIndex] & m.facingMask) != 0u;
        if(!sectionVisible){
            atomicAdd(sectionCulled,1u);
        }else{
            visible=!outsideFrustum(m.boundingSphere);
            if(!visible) atomicAdd(frustumCulled,1u);
        }
    }
    flags[tid]=visible?1u:0u;
    barrier();
    for(uint offset=1u;offset<64u;offset<<=1u){
        uint v=(tid>=offset)?flags[tid-offset]:0u;
        barrier(); flags[tid]+=v; barrier();
    }
    if(tid==63u) groupBase=atomicAdd(visibleCount,flags[63u]);
    barrier();
    if(visible){
        uint outIdx=groupBase+flags[tid]-1u;
        cmds[outIdx].count=m.primitiveCount*3u;
        cmds[outIdx].instanceCount=1u;
        cmds[outIdx].firstIndex=m.primitiveOffset;
        cmds[outIdx].baseVertex=int(m.vertexOffset);
        cmds[outIdx].baseInstance=0u;
    }
}
