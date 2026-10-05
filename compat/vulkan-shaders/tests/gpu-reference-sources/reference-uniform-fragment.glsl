#version 450
layout(std140,set=0,binding=0) uniform Data {
    vec3 color;
    float alpha;
    mat3 transform;
    float samples[3];
    mat4 projection;
    ivec2 pair;
    int toggled;
};
layout(location=0) out vec4 result;
void main(){
    bool valid=all(equal(color,vec3(0.25,0.5,0.75))) && alpha==1.0;
    for(int c=0;c<3;c++)for(int r=0;r<3;r++)valid=valid && transform[c][r]==float(c*3+r+1);
    valid=valid && samples[0]==0.125 && samples[1]==0.25 && samples[2]==0.5;
    for(int c=0;c<4;c++)for(int r=0;r<4;r++)valid=valid && projection[c][r]==float(c*4+r+1);
    valid=valid && all(equal(pair,ivec2(17,23))) && toggled==31;
    result=valid?vec4(0.0,1.0,0.0,1.0):vec4(1.0,0.0,0.0,1.0);
}
