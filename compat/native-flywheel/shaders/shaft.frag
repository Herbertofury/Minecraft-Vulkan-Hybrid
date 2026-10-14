#version 450
layout(set=1,binding=0) uniform sampler2D axisTexture;
layout(set=1,binding=1) uniform sampler2D topTexture;
layout(location=0) in vec2 texCoord;
layout(location=1) in vec4 tint;
layout(location=2) in vec3 normal;
layout(location=3) in vec2 light;
layout(location=4) flat in ivec2 overlay;
layout(location=5) flat in uint textureIndex;
layout(location=0) out vec4 color;
void main(){
    vec4 texel=textureIndex==0u?texture(axisTexture,texCoord):texture(topTexture,texCoord);
    float directional=0.55+0.45*abs(dot(normalize(normal),normalize(vec3(0.3,0.8,0.5))));
    // Bounded opaque shaft fixture. Full Flywheel material/light LUT/overlay pipeline is separate.
    color=vec4(texel.rgb*tint.rgb*directional*max(light.x,light.y),texel.a*tint.a);
}
