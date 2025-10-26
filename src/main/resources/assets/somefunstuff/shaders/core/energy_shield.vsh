#version 150

in vec3 Position;
in vec4 Color;
in vec3 Normal;
in vec2 UV0;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform float GameTime;

out vec4 vertexColor;
out vec3 vertexNormal;
out vec3 viewPosition;
out vec2 texCoord;
out float fresnel;

void main() {
    // 世界空间位置
    vec4 worldPos = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * worldPos;
    
    // 传递数据到片段着色器
    vertexColor = Color;
    vertexNormal = normalize(mat3(ModelViewMat) * Normal);
    viewPosition = worldPos.xyz;
    texCoord = UV0;
    
    // 计算菲涅尔效果 (边缘越亮)
    vec3 viewDir = normalize(-viewPosition);
    float fresnelPower = 3.0;
    fresnel = pow(1.0 - max(dot(viewDir, vertexNormal), 0.0), fresnelPower);
}
