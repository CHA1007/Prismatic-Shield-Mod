#version 150

uniform sampler2D Sampler0;
uniform float GameTime;

in vec4 vertexColor;
in vec3 vertexNormal;
in vec3 viewPosition;
in vec2 texCoord;
in float fresnel;

out vec4 fragColor;

// Simplex 3D噪声函数 (程序化能量纹理)
vec3 mod289(vec3 x) { return x - floor(x * (1.0 / 289.0)) * 289.0; }
vec4 mod289(vec4 x) { return x - floor(x * (1.0 / 289.0)) * 289.0; }
vec4 permute(vec4 x) { return mod289(((x*34.0)+1.0)*x); }
vec4 taylorInvSqrt(vec4 r) { return 1.79284291400159 - 0.85373472095314 * r; }

float snoise(vec3 v) {
    const vec2 C = vec2(1.0/6.0, 1.0/3.0);
    const vec4 D = vec4(0.0, 0.5, 1.0, 2.0);
    
    vec3 i  = floor(v + dot(v, C.yyy));
    vec3 x0 = v - i + dot(i, C.xxx);
    
    vec3 g = step(x0.yzx, x0.xyz);
    vec3 l = 1.0 - g;
    vec3 i1 = min(g.xyz, l.zxy);
    vec3 i2 = max(g.xyz, l.zxy);
    
    vec3 x1 = x0 - i1 + C.xxx;
    vec3 x2 = x0 - i2 + C.yyy;
    vec3 x3 = x0 - D.yyy;
    
    i = mod289(i);
    vec4 p = permute(permute(permute(
        i.z + vec4(0.0, i1.z, i2.z, 1.0))
        + i.y + vec4(0.0, i1.y, i2.y, 1.0))
        + i.x + vec4(0.0, i1.x, i2.x, 1.0));
    
    float n_ = 0.142857142857;
    vec3 ns = n_ * D.wyz - D.xzx;
    
    vec4 j = p - 49.0 * floor(p * ns.z * ns.z);
    
    vec4 x_ = floor(j * ns.z);
    vec4 y_ = floor(j - 7.0 * x_);
    
    vec4 x = x_ *ns.x + ns.yyyy;
    vec4 y = y_ *ns.x + ns.yyyy;
    vec4 h = 1.0 - abs(x) - abs(y);
    
    vec4 b0 = vec4(x.xy, y.xy);
    vec4 b1 = vec4(x.zw, y.zw);
    
    vec4 s0 = floor(b0)*2.0 + 1.0;
    vec4 s1 = floor(b1)*2.0 + 1.0;
    vec4 sh = -step(h, vec4(0.0));
    
    vec4 a0 = b0.xzyw + s0.xzyw*sh.xxyy;
    vec4 a1 = b1.xzyw + s1.xzyw*sh.zzww;
    
    vec3 p0 = vec3(a0.xy, h.x);
    vec3 p1 = vec3(a0.zw, h.y);
    vec3 p2 = vec3(a1.xy, h.z);
    vec3 p3 = vec3(a1.zw, h.w);
    
    vec4 norm = taylorInvSqrt(vec4(dot(p0,p0), dot(p1,p1), dot(p2,p2), dot(p3,p3)));
    p0 *= norm.x;
    p1 *= norm.y;
    p2 *= norm.z;
    p3 *= norm.w;
    
    vec4 m = max(0.6 - vec4(dot(x0,x0), dot(x1,x1), dot(x2,x2), dot(x3,x3)), 0.0);
    m = m * m;
    return 42.0 * dot(m*m, vec4(dot(p0,x0), dot(p1,x1), dot(p2,x2), dot(p3,x3)));
}

void main() {
    // 基础颜色
    vec3 baseColor = vertexColor.rgb;
    
    // 动态能量纹理 (多层噪声)
    float time = GameTime * 2000.0; // 游戏时间转换
    vec3 noisePos = viewPosition * 2.0;
    
    float noise1 = snoise(noisePos + vec3(time * 0.1, 0.0, 0.0));
    float noise2 = snoise(noisePos * 2.0 + vec3(0.0, time * 0.15, 0.0));
    float noise3 = snoise(noisePos * 4.0 + vec3(time * 0.2, time * 0.2, 0.0));
    
    float energyPattern = (noise1 + noise2 * 0.5 + noise3 * 0.25) * 0.5 + 0.5;
    
    // 菲涅尔边缘光 (边缘更亮)
    vec3 fresnelColor = baseColor * 2.0;
    float fresnelIntensity = fresnel * 0.8;
    
    // 脉动效果
    float pulse = sin(time * 0.05) * 0.15 + 0.85;
    
    // 组合所有效果
    vec3 finalColor = baseColor * (0.6 + energyPattern * 0.4) * pulse;
    finalColor += fresnelColor * fresnelIntensity;
    
    // 透明度: 基础透明 + 菲涅尔边缘不透明
    float alpha = vertexColor.a * (0.3 + fresnel * 0.5 + energyPattern * 0.2);
    
    fragColor = vec4(finalColor, alpha);
}
