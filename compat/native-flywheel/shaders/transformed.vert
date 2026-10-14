#version 450
layout(location=0) in vec3 Position;
layout(location=1) in vec2 UV;
layout(location=2) in vec3 Normal;
layout(location=3) in uint TextureIndex;
layout(push_constant) uniform Projection { mat4 projection; };
layout(set=0,binding=1,std430) readonly buffer InstanceBuffer { uint words[]; };
layout(set=0,binding=2,std430) readonly buffer TargetBuffer { uint targets[]; };
struct FlwInstance { vec4 color; ivec2 overlay; vec2 light; mat4 pose; };
vec4 flw_vertexPos; vec3 flw_vertexNormal; vec4 flw_vertexColor; ivec2 flw_vertexOverlay; vec2 flw_vertexLight;
layout(location=0) out vec2 texCoord;
layout(location=1) out vec4 tint;
layout(location=2) out vec3 normal;
layout(location=3) out vec2 light;
layout(location=4) flat out ivec2 overlay;
layout(location=5) flat out uint textureIndex;
void flw_instanceVertex(in FlwInstance i) {
    flw_vertexPos = i.pose * flw_vertexPos;
    flw_vertexNormal = mat3(transpose(inverse(i.pose))) * flw_vertexNormal;
    flw_vertexColor *= i.color;
    flw_vertexOverlay = i.overlay;
    // Some drivers have a bug where uint over float division is invalid, so use an explicit cast.
    flw_vertexLight = max(vec2(i.light) / 256.0, flw_vertexLight);
}

void main() {
    // Vulkan's gl_InstanceIndex already includes VkDrawIndexedIndirectCommand.firstInstance.
    uint o=targets[uint(gl_InstanceIndex)]*19u;
    uint c=words[o]; uint ov=words[o+1u]; uint li=words[o+2u];
    FlwInstance i;
    i.color=vec4(c&255u,(c>>8u)&255u,(c>>16u)&255u,(c>>24u)&255u)/255.0;
    i.overlay=ivec2(int(ov<<16u)>>16,int(ov)>>16);
    i.light=vec2(li&65535u,li>>16u);
    i.pose=mat4(uintBitsToFloat(uvec4(words[o+3u],words[o+4u],words[o+5u],words[o+6u])),
                uintBitsToFloat(uvec4(words[o+7u],words[o+8u],words[o+9u],words[o+10u])),
                uintBitsToFloat(uvec4(words[o+11u],words[o+12u],words[o+13u],words[o+14u])),
                uintBitsToFloat(uvec4(words[o+15u],words[o+16u],words[o+17u],words[o+18u])));
    flw_vertexPos=vec4(Position,1.0);flw_vertexNormal=Normal;flw_vertexColor=vec4(1.0);flw_vertexOverlay=ivec2(0);flw_vertexLight=vec2(0.0);
    flw_instanceVertex(i);
    gl_Position=projection*flw_vertexPos;texCoord=UV;tint=flw_vertexColor;normal=flw_vertexNormal;light=flw_vertexLight;overlay=flw_vertexOverlay;textureIndex=TextureIndex;
}
