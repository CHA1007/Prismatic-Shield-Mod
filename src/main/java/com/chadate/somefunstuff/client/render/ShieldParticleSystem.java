package com.chadate.somefunstuff.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.Random;

/**
 * GPU加速的护盾粒子系统
 * 渲染数千个能量粒子环绕护盾
 */
public class ShieldParticleSystem {
    
    private static final int PARTICLE_COUNT = 500; // 粒子数量
    private static final Random RANDOM = new Random();
    
    // 粒子数据缓存（避免每帧重新生成）
    private static class Particle {
        float baseTheta;    // 基础经度
        float basePhi;      // 基础纬度
        float speed;        // 移动速度
        float size;         // 粒子大小
        float phaseOffset;  // 相位偏移（用于闪烁）
        
        Particle() {
            baseTheta = RANDOM.nextFloat() * Mth.TWO_PI;
            basePhi = (float)(Math.acos(2.0f * RANDOM.nextFloat() - 1.0f));
            speed = 0.02f + RANDOM.nextFloat() * 0.05f;
            size = 0.03f + RANDOM.nextFloat() * 0.05f;
            phaseOffset = RANDOM.nextFloat() * Mth.TWO_PI;
        }
    }
    
    private static Particle[] particles;
    
    /**
     * 初始化粒子系统
     */
    public static void initialize() {
        if (particles == null) {
            particles = new Particle[PARTICLE_COUNT];
            for (int i = 0; i < PARTICLE_COUNT; i++) {
                particles[i] = new Particle();
            }
        }
    }
    
    /**
     * 渲染粒子系统
     * 
     * @param consumer 顶点消费者
     * @param matrix 变换矩阵
     * @param radius 护盾半径
     * @param r 红色分量
     * @param g 绿色分量
     * @param b 蓝色分量
     * @param alpha 透明度
     * @param time 动画时间
     */
    public static void renderParticles(VertexConsumer consumer, Matrix4f matrix,
                                      double radius, float r, float g, float b,
                                      float alpha, float time) {
        initialize();
        
        for (int i = 0; i < PARTICLE_COUNT; i++) {
            Particle p = particles[i];
            
            // 计算粒子当前位置（螺旋轨迹）
            float theta = p.baseTheta + time * p.speed;
            float phi = p.basePhi + Mth.sin(time * p.speed * 0.5f) * 0.3f;
            
            // 球面坐标转换
            float x = (float)(radius * Math.sin(phi) * Math.cos(theta));
            float y = (float)(radius * Math.cos(phi));
            float z = (float)(radius * Math.sin(phi) * Math.sin(theta));
            
            // 粒子闪烁效果
            float brightness = (Mth.sin(time * 2.0f + p.phaseOffset) + 1.0f) * 0.5f;
            brightness = 0.3f + brightness * 0.7f;
            
            // 粒子透明度（远离相机的粒子更透明）
            float particleAlpha = alpha * brightness;
            
            // 渲染粒子（公告板技术 - 始终面向相机）
            renderBillboardParticle(consumer, matrix, x, y, z, p.size,
                                   r * 2.0f, g * 2.0f, b * 2.0f, particleAlpha);
        }
    }
    
    /**
     * 渲染单个公告板粒子（始终面向相机）
     */
    private static void renderBillboardParticle(VertexConsumer consumer, Matrix4f matrix,
                                               float x, float y, float z, float size,
                                               float r, float g, float b, float alpha) {
        // 简化版公告板：使用XY平面的四边形
        // 注意：真正的公告板需要相机方向，这里使用简化方案
        
        Vector3f center = new Vector3f(x, y, z);
        Vector3f normal = new Vector3f(center).normalize();
        
        // 四个顶点（形成一个面向外的正方形）
        float halfSize = size / 2.0f;
        
        // 构建局部坐标系
        Vector3f tangent = getTangent(normal);
        Vector3f bitangent = new Vector3f(normal).cross(tangent).normalize();
        
        Vector3f v1 = new Vector3f(center).add(
            tangent.x * -halfSize + bitangent.x * -halfSize,
            tangent.y * -halfSize + bitangent.y * -halfSize,
            tangent.z * -halfSize + bitangent.z * -halfSize
        );
        
        Vector3f v2 = new Vector3f(center).add(
            tangent.x * halfSize + bitangent.x * -halfSize,
            tangent.y * halfSize + bitangent.y * -halfSize,
            tangent.z * halfSize + bitangent.z * -halfSize
        );
        
        Vector3f v3 = new Vector3f(center).add(
            tangent.x * halfSize + bitangent.x * halfSize,
            tangent.y * halfSize + bitangent.y * halfSize,
            tangent.z * halfSize + bitangent.z * halfSize
        );
        
        Vector3f v4 = new Vector3f(center).add(
            tangent.x * -halfSize + bitangent.x * halfSize,
            tangent.y * -halfSize + bitangent.y * halfSize,
            tangent.z * -halfSize + bitangent.z * halfSize
        );
        
        // 渲染四边形（两个三角形）- 反转顶点顺序让正面朝外
        // 三角形1（反转：v1->v3->v2）
        consumer.addVertex(matrix, v1.x, v1.y, v1.z)
               .setColor(r, g, b, alpha);
        consumer.addVertex(matrix, v3.x, v3.y, v3.z)
               .setColor(r, g, b, alpha);
        consumer.addVertex(matrix, v2.x, v2.y, v2.z)
               .setColor(r, g, b, alpha);
        
        // 三角形2（反转：v1->v4->v3）
        consumer.addVertex(matrix, v1.x, v1.y, v1.z)
               .setColor(r, g, b, alpha);
        consumer.addVertex(matrix, v4.x, v4.y, v4.z)
               .setColor(r, g, b, alpha);
        consumer.addVertex(matrix, v3.x, v3.y, v3.z)
               .setColor(r, g, b, alpha);
    }
    
    /**
     * 计算法向量的切线
     */
    private static Vector3f getTangent(Vector3f normal) {
        Vector3f up = Math.abs(normal.y) < 0.9f ? new Vector3f(0, 1, 0) : new Vector3f(1, 0, 0);
        return new Vector3f(normal).cross(up).normalize();
    }
}
