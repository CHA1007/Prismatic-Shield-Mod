package com.chadate.somefunstuff.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * 六边形蜂巢护盾网格生成器
 * 生成覆盖球面的六边形网格结构
 */
public class HexagonalShieldMesh {
    
    private static final float HEX_SIZE = 0.15f; // 六边形大小
    private static final float HEX_THICKNESS = 0.02f; // 六边形边框厚度
    
    /** 动态六边形数量（根据性能等级调整） */
    private static int hexagonCount = 100;
    
    /**
     * 更新六边形数量（用于性能调整）
     */
    public static void updateHexagonCount(int count) {
        hexagonCount = Math.max(50, Math.min(200, count)); // 限制在50-200之间
    }
    
    /**
     * 生成并渲染六边形网格护盾
     * 
     * @param consumer 顶点消费者
     * @param matrix 变换矩阵
     * @param radius 护盾半径
     * @param r 红色分量
     * @param g 绿色分量
     * @param b 蓝色分量
     * @param alpha 透明度
     * @param time 动画时间
     * @param shieldCenter 护盾中心位置（用于计算受击效果）
     */
    public static void renderHexagonalShield(VertexConsumer consumer, Matrix4f matrix, 
                                            double radius, float r, float g, float b, 
                                            float alpha, float time, Vec3 shieldCenter) {
        // 使用黄金分割螺旋均匀分布六边形
        int hexCount = hexagonCount; // 使用动态配置的数量
        float goldenRatio = (1.0f + Mth.sqrt(5.0f)) / 2.0f;
        
        for (int i = 0; i < hexCount; i++) {
            // 黄金分割螺旋算法 - 在球面上均匀分布点
            float theta = (float)(2.0f * Math.PI * i / goldenRatio);
            float phi = (float)(Math.acos(1.0f - 2.0f * (i + 0.5f) / hexCount));
            
            // 球面坐标转笛卡尔坐标
            float x = (float)(radius * Math.sin(phi) * Math.cos(theta));
            float y = (float)(radius * Math.cos(phi));
            float z = (float)(radius * Math.sin(phi) * Math.sin(theta));
            
            Vector3f center = new Vector3f(x, y, z);
            Vector3f normal = new Vector3f(center).normalize();
            
            // 能量流动效果 - 六边形随时间闪烁
            float distanceFromTop = (float)Math.abs(y);
            float energyFlow = (Mth.sin(time * 0.5f + distanceFromTop * 2.0f + i * 0.1f) + 1.0f) * 0.5f;
            
            // 计算受击影响（冲击波效果）- 优化版：减少计算
            float impactInfluence = 0.0f;
            float flashIntensity = 0.0f;
            
            // 仅在需要时计算受击效果（每5个六边形采样一次）
            if (i % 5 == 0) {
                Vec3 hexWorldPos = shieldCenter.add(x, y, z);
                impactInfluence = ShieldImpactEffect.getImpactInfluence(hexWorldPos, shieldCenter, radius);
                flashIntensity = ShieldImpactEffect.getFlashIntensity(hexWorldPos, shieldCenter, radius);
            }
            
            // 组合所有效果（大幅增强影响系数）
            float hexAlpha = alpha * (0.4f + energyFlow * 0.6f + impactInfluence * 3.0f);
            float hexBrightness = 0.8f + energyFlow * 0.4f + impactInfluence * 5.0f + flashIntensity * 8.0f;
            
            // 渲染六边形
            renderHexagon(consumer, matrix, center, normal, HEX_SIZE, 
                         r * hexBrightness, g * hexBrightness, b * hexBrightness, hexAlpha);
        }
    }
    
    /**
     * 渲染单个六边形
     */
    private static void renderHexagon(VertexConsumer consumer, Matrix4f matrix, 
                                     Vector3f center, Vector3f normal, float size,
                                     float r, float g, float b, float alpha) {
        // 构建局部坐标系
        Vector3f tangent = getTangent(normal);
        Vector3f bitangent = new Vector3f(normal).cross(tangent).normalize();
        
        // 六边形的6个顶点
        Vector3f[] vertices = new Vector3f[6];
        for (int i = 0; i < 6; i++) {
            float angle = (float)(i * Math.PI / 3.0f);
            float offsetX = Mth.cos(angle) * size;
            float offsetY = Mth.sin(angle) * size;
            
            vertices[i] = new Vector3f(center)
                .add(tangent.x * offsetX + bitangent.x * offsetY,
                     tangent.y * offsetX + bitangent.y * offsetY,
                     tangent.z * offsetX + bitangent.z * offsetY);
        }
        
        // 渲染六边形内部（三角扇）- 反转顶点顺序让正面朝外
        for (int i = 0; i < 6; i++) {
            Vector3f v1 = vertices[i];
            Vector3f v2 = vertices[(i + 1) % 6];
            
            // 中心点
            consumer.addVertex(matrix, center.x, center.y, center.z)
                   .setColor(r, g, b, alpha * 0.3f);
            
            // 边缘点2（反转顺序：center -> v2 -> v1）
            consumer.addVertex(matrix, v2.x, v2.y, v2.z)
                   .setColor(r, g, b, alpha);
            
            // 边缘点1
            consumer.addVertex(matrix, v1.x, v1.y, v1.z)
                   .setColor(r, g, b, alpha);
        }
        
        // 渲染六边形边框（更亮）
        for (int i = 0; i < 6; i++) {
            Vector3f v1 = vertices[i];
            Vector3f v2 = vertices[(i + 1) % 6];
            
            // 向外稍微偏移，形成边框效果
            Vector3f edgeNormal = new Vector3f(v1).add(v2).mul(0.5f).sub(center).normalize();
            float edgeOffset = HEX_THICKNESS;
            
            Vector3f v1Out = new Vector3f(v1).add(edgeNormal.x * edgeOffset, 
                                                  edgeNormal.y * edgeOffset, 
                                                  edgeNormal.z * edgeOffset);
            Vector3f v2Out = new Vector3f(v2).add(edgeNormal.x * edgeOffset, 
                                                  edgeNormal.y * edgeOffset, 
                                                  edgeNormal.z * edgeOffset);
            
            // 绘制边框四边形（2个三角形）
            renderQuad(consumer, matrix, v1, v2, v2Out, v1Out, 
                      r * 1.5f, g * 1.5f, b * 1.5f, alpha, normal);
        }
    }
    
    /**
     * 渲染四边形（用于边框）
     */
    private static void renderQuad(VertexConsumer consumer, Matrix4f matrix,
                                  Vector3f v1, Vector3f v2, Vector3f v3, Vector3f v4,
                                  float r, float g, float b, float alpha, Vector3f normal) {
        // 三角形1（反转顶点顺序：v1->v3->v2，让正面朝外）
        consumer.addVertex(matrix, v1.x, v1.y, v1.z)
               .setColor(r, g, b, alpha);
        consumer.addVertex(matrix, v3.x, v3.y, v3.z)
               .setColor(r, g, b, alpha);
        consumer.addVertex(matrix, v2.x, v2.y, v2.z)
               .setColor(r, g, b, alpha);
        
        // 三角形2（反转顶点顺序：v1->v4->v3，让正面朝外）
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
        // 选择一个不平行于法向量的向量
        Vector3f up = Math.abs(normal.y) < 0.9f ? new Vector3f(0, 1, 0) : new Vector3f(1, 0, 0);
        return new Vector3f(normal).cross(up).normalize();
    }
}
