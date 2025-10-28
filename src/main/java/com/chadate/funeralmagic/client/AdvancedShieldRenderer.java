package com.chadate.funeralmagic.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.chadate.funeralmagic.capability.ShieldCapabilities;
import com.chadate.funeralmagic.capability.ShieldCapability;
import com.chadate.funeralmagic.client.render.HexagonalShieldMesh;
import com.chadate.funeralmagic.client.render.ShieldImpactEffect;
import com.chadate.funeralmagic.client.render.ShieldParticleSystem;
import com.chadate.funeralmagic.client.render.ShieldShatterEffect;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.util.List;

/**
 * 顶级护盾渲染器 - 多层渲染系统
 * 
 * 包含以下效果：
 * 1. 六边形蜂巢网格（科技感）
 * 2. 菲涅尔边缘发光（自定义着色器）
 * 3. GPU粒子系统（500+粒子）
 * 4. 动态能量纹理（Simplex噪声）
 * 5. 多层渲染（内层+外层）
 * 6. 受击反馈效果
 */
public class AdvancedShieldRenderer {
    
    // === 透明度控制常量 ===
    // 可以调整这些值来控制护盾的可见度（0.0 = 完全透明，1.0 = 完全不透明）
    
    /** 内层能量场透明度倍率 */
    private static final float INNER_LAYER_ALPHA_MULTIPLIER = 0.3f;
    
    /** 六边形网格透明度倍率 */
    private static final float HEX_LAYER_ALPHA_MULTIPLIER = 0.4f;
    
    /** 粒子层透明度倍率 */
    private static final float PARTICLE_LAYER_ALPHA_MULTIPLIER = 0.5f;
    
    /** 外层光晕透明度倍率 */
    private static final float GLOW_LAYER_ALPHA_MULTIPLIER = 0.25f;  // 降低到25%
    
    /**
     * 注册渲染事件
     */
    public static void register() {
        ShieldParticleSystem.initialize();
    }
    
    /**
     * 在世界渲染阶段绘制护盾
     * 支持所有实体的护盾渲染
     */
    @SuppressWarnings("null")
    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        // 只在半透明渲染阶段绘制
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        
        // 遍历客户端世界中的所有实体
        mc.level.entitiesForRendering().forEach(entity -> {
            // 检查实体是否有护盾capability
            ShieldCapability shield = entity.getData(ShieldCapabilities.SHIELD_ATTACHMENT);
            
            // 如果护盾存在且激活，则渲染
            if (shield != null && shield.isShieldActive()) {
                renderAdvancedShield(event, entity, shield);
            }
            // 即使护盾不活跃，如果有破碎效果也要渲染
            else if (ShieldShatterEffect.hasActiveShatter(entity.getId())) {
                renderShatterEffectOnly(event, entity);
            }
        });
    }
    
    /**
     * 顶级多层护盾渲染
     * 支持所有实体类型
     */
    private static void renderAdvancedShield(RenderLevelStageEvent event, Entity entity, ShieldCapability shield) {
        PoseStack poseStack = event.getPoseStack();
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        
        // 获取相机位置
        Vec3 cameraPos = event.getCamera().getPosition();
        
        // 使用 partialTick 插值实体位置，确保护盾平滑跟随实体移动（修复延迟感）
        Vec3 shieldCenter = new Vec3(
            Mth.lerp(partialTick, entity.xOld, entity.getX()),
            Mth.lerp(partialTick, entity.yOld, entity.getY()) + entity.getEyeHeight() / 2,
            Mth.lerp(partialTick, entity.zOld, entity.getZ())
        );
        
        // 计算相对位置
        double relX = shieldCenter.x - cameraPos.x;
        double relY = shieldCenter.y - cameraPos.y;
        double relZ = shieldCenter.z - cameraPos.z;
        
        poseStack.pushPose();
        poseStack.translate(relX, relY, relZ);
        
        // 获取护盾参数
        int strength = shield.strength();
        double radius = shield.radius();
        float[] color = getShieldColor(strength);
        float time = (entity.tickCount + partialTick) * 0.05f;
        
        // 更新受击效果
        ShieldImpactEffect.update();
        
        // 更新破碎效果
        ShieldShatterEffect.update();
        
        // === 多层渲染 ===
        
        // 第1层：内层能量场
        renderInnerEnergyField(poseStack, radius * 0.97, color, time);
        
        // 第2层：六边形蜂巢网格
        renderHexagonalLayer(poseStack, radius, color, time, strength, shieldCenter, entity.getId());
        
        // 第3层：受击脉冲圆环
        renderImpactRings(poseStack, radius, color, time, shieldCenter, entity.getId());
        
        // 第4层：GPU粒子系统
        renderParticleLayer(poseStack, radius * 1.02, color, time);
        
        // 第5层：外层光晕
        renderOuterGlow(poseStack, radius * 1.05, color, time);
        
        // 第6层：破碎效果（如果存在）
        if (ShieldShatterEffect.hasActiveShatter(entity.getId())) {
            renderShatterLayer(poseStack, radius, color, shieldCenter, entity.getId());
        }
        
        poseStack.popPose();
    }
    
    /**
     * 第1层：内层能量场（带菲涅尔效果的球体）
     */
    private static void renderInnerEnergyField(PoseStack poseStack, double radius, float[] color, float time) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableDepthTest();  // 启用深度测试，让护盾被地形遮挡
        RenderSystem.depthFunc(515);  // GL_LESS
        RenderSystem.depthMask(false);  // 禁用深度写入
        RenderSystem.disableCull();  // 禁用面剔除以确保双面可见
        
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        
        Matrix4f matrix = poseStack.last().pose();
        
        // 渲染球体，带法线数据（用于着色器计算菲涅尔效果）
        int latBands = 24;
        int lonBands = 24;
        
        for (int lat = 0; lat < latBands; lat++) {
            double theta1 = lat * Math.PI / latBands;
            double theta2 = (lat + 1) * Math.PI / latBands;
            
            for (int lon = 0; lon < lonBands; lon++) {
                double phi1 = lon * 2 * Math.PI / lonBands;
                double phi2 = (lon + 1) * 2 * Math.PI / lonBands;
                
                // 四个顶点形成一个四边形（2个三角形）
                Vector3f v1 = sphereVertex(radius, theta1, phi1);
                Vector3f v2 = sphereVertex(radius, theta1, phi2);
                Vector3f v3 = sphereVertex(radius, theta2, phi2);
                Vector3f v4 = sphereVertex(radius, theta2, phi1);
                
                float finalAlpha = 0.5f * INNER_LAYER_ALPHA_MULTIPLIER;
                
                buffer.addVertex(matrix, v1.x, v1.y, v1.z)
                      .setColor(color[0], color[1], color[2], finalAlpha);
                buffer.addVertex(matrix, v3.x, v3.y, v3.z)
                      .setColor(color[0], color[1], color[2], finalAlpha);
                buffer.addVertex(matrix, v2.x, v2.y, v2.z)
                      .setColor(color[0], color[1], color[2], finalAlpha);
                
                buffer.addVertex(matrix, v1.x, v1.y, v1.z)
                      .setColor(color[0], color[1], color[2], finalAlpha);
                buffer.addVertex(matrix, v4.x, v4.y, v4.z)
                      .setColor(color[0], color[1], color[2], finalAlpha);
                buffer.addVertex(matrix, v3.x, v3.y, v3.z)
                      .setColor(color[0], color[1], color[2], finalAlpha);
            }
        }
        
        BufferUploader.drawWithShader(buffer.buildOrThrow());
        
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }
    
    /**
     * 第2层：六边形蜂巢网格
     */
    private static void renderHexagonalLayer(PoseStack poseStack, double radius, float[] color, float time, int strength, Vec3 shieldCenter, int entityId) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableDepthTest();  // 启用深度测试，让护盾被地形遮挡
        RenderSystem.depthFunc(515);  // GL_LESS
        RenderSystem.depthMask(false);  // 禁用深度写入
        RenderSystem.disableCull();  // 禁用面剭除，确保双面可见
        
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        
        Matrix4f matrix = poseStack.last().pose();
        
        // 统一透明度（不再根据强度变化）
        float hexAlpha = 0.6f * HEX_LAYER_ALPHA_MULTIPLIER;
        
        // 渲染六边形网格（传递护盾中心和实体ID用于受击效果）
        HexagonalShieldMesh.renderHexagonalShield(buffer, matrix, radius, 
            color[0], color[1], color[2], hexAlpha, time, shieldCenter, entityId);
        
        BufferUploader.drawWithShader(buffer.buildOrThrow());
        
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }
    
    private static void renderParticleLayer(PoseStack poseStack, double radius, float[] color, float time) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(515);  // GL_LESS
        RenderSystem.depthMask(false);
        
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        
        Matrix4f matrix = poseStack.last().pose();
        
        // 应用粒子层透明度控制
        float particleAlpha = 0.8f * PARTICLE_LAYER_ALPHA_MULTIPLIER;
        
        // 渲染粒子
        ShieldParticleSystem.renderParticles(buffer, matrix, radius,
            color[0], color[1], color[2], particleAlpha, time);
        
        BufferUploader.drawWithShader(buffer.buildOrThrow());
        
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }
    
    /**
     * 第4层：外层光晕（加法混合，强烈发光）
     */
    private static void renderOuterGlow(PoseStack poseStack, double radius, float[] color, float time) {
        RenderSystem.enableBlend();
        // 加法混合模式 - 产生发光效果
        RenderSystem.blendFunc(org.lwjgl.opengl.GL11.GL_SRC_ALPHA, org.lwjgl.opengl.GL11.GL_ONE);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(515);  // GL_LESS
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        
        Matrix4f matrix = poseStack.last().pose();
        
        // 低细节度的光晕球体
        int latBands = 12;
        int lonBands = 12;
        
        for (int lat = 0; lat < latBands; lat++) {
            double theta1 = lat * Math.PI / latBands;
            double theta2 = (lat + 1) * Math.PI / latBands;
            
            for (int lon = 0; lon < lonBands; lon++) {
                double phi1 = lon * 2 * Math.PI / lonBands;
                double phi2 = (lon + 1) * 2 * Math.PI / lonBands;
                
                Vector3f v1 = sphereVertex(radius, theta1, phi1);
                Vector3f v2 = sphereVertex(radius, theta1, phi2);
                Vector3f v3 = sphereVertex(radius, theta2, phi2);
                Vector3f v4 = sphereVertex(radius, theta2, phi1);
                
                // 统一光晕效果（不再动态变化）
                float finalGlowAlpha = 0.3f * GLOW_LAYER_ALPHA_MULTIPLIER;
                
                // 统一颜色强度
                float colorBoost = 1.3f;
                
                // 三角形1（反转顶点顺序：v1->v3->v2，让正面朝外）
                buffer.addVertex(matrix, v1.x, v1.y, v1.z)
                      .setColor(color[0] * colorBoost, color[1] * colorBoost, color[2] * colorBoost, finalGlowAlpha);
                buffer.addVertex(matrix, v3.x, v3.y, v3.z)
                      .setColor(color[0] * colorBoost, color[1] * colorBoost, color[2] * colorBoost, finalGlowAlpha);
                buffer.addVertex(matrix, v2.x, v2.y, v2.z)
                      .setColor(color[0] * colorBoost, color[1] * colorBoost, color[2] * colorBoost, finalGlowAlpha);
                
                // 三角形2（反转顶点顺序：v1->v4->v3，让正面朝外）
                buffer.addVertex(matrix, v1.x, v1.y, v1.z)
                      .setColor(color[0] * colorBoost, color[1] * colorBoost, color[2] * colorBoost, finalGlowAlpha);
                buffer.addVertex(matrix, v4.x, v4.y, v4.z)
                      .setColor(color[0] * colorBoost, color[1] * colorBoost, color[2] * colorBoost, finalGlowAlpha);
                buffer.addVertex(matrix, v3.x, v3.y, v3.z)
                      .setColor(color[0] * colorBoost, color[1] * colorBoost, color[2] * colorBoost, finalGlowAlpha);
            }
        }
        
        BufferUploader.drawWithShader(buffer.buildOrThrow());
        
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc(); // 恢复默认混合模式
        RenderSystem.disableBlend();
    }
    
    /**
     * 计算球面上的顶点
     */
    private static Vector3f sphereVertex(double radius, double theta, double phi) {
        float x = (float)(radius * Math.sin(theta) * Math.cos(phi));
        float y = (float)(radius * Math.cos(theta));
        float z = (float)(radius * Math.sin(theta) * Math.sin(phi));
        return new Vector3f(x, y, z);
    }
    
    /**
     * 获取护盾颜色
     */
    private static float[] getShieldColor(int strength) {
        return new float[]{0.15f, 0.4f, 0.6f};
    }
    
    /**
     * 第6层：破碎效果渲染
     */
    private static void renderShatterLayer(PoseStack poseStack, double radius, float[] color, Vec3 shieldCenter, int entityId) {
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(515);
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        
        Matrix4f matrix = poseStack.last().pose();
        
        // 渲染破碎碎片
        ShieldShatterEffect.renderShatter(buffer, matrix, entityId, shieldCenter,
                color[0], color[1], color[2], 0.8f);
        
        // 尝试渲染，如果buffer为空则忽略
        try {
            BufferUploader.drawWithShader(buffer.buildOrThrow());
        } catch (IllegalStateException ignored) {
            // Buffer为空时忽略
        }
        
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }
    
    /**
     * 第3层：受击冲击环效果
     * 从击中点沿球面向外扩散的能量环
     */
    private static void renderImpactRings(PoseStack poseStack, double radius, float[] color, float time, Vec3 shieldCenter, int entityId) {
        List<ShieldImpactEffect.ImpactPoint> impacts = ShieldImpactEffect.getActiveImpactsForEntity(entityId);
        if (impacts.isEmpty()) {
            return;
        }
        
        RenderSystem.enableBlend();
        // 使用加法混合让冲击环更明显
        RenderSystem.blendFunc(org.lwjgl.opengl.GL11.GL_SRC_ALPHA, org.lwjgl.opengl.GL11.GL_ONE);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(515);
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        Matrix4f matrix = poseStack.last().pose();
        
        long currentTime = System.currentTimeMillis() / 50;
        
        // 为每个击中点渲染冲击环
        for (ShieldImpactEffect.ImpactPoint impact : impacts) {
            float progress = impact.getProgress(currentTime);
            
            // 冲击环参数（减小扩散范围）
            float ringAngle = (float)(progress * Math.PI * 0.25); // 最大扩散角度45度（原来更大）
            float ringThickness = 0.05f * (1.0f - progress * 0.5f); // 环厚度（角度单位）
            float ringAlpha = (1.0f - progress) * 0.9f; // 透明度衰减
            
            // 使用击中方向作为中心方向
            Vec3 centerDir = impact.directionFromCenter;
            
            // 创建两个垂直于中心方向的正交基向量
            Vec3 tangent1 = getTangent(centerDir);
            Vec3 tangent2 = centerDir.cross(tangent1).normalize();
            
            // 渲染球面圆环（使用三角形条带）
            int segments = 32;
            for (int i = 0; i < segments; i++) {
                float azimuth1 = (float)(i * 2 * Math.PI / segments);
                float azimuth2 = (float)((i + 1) * 2 * Math.PI / segments);
                
                // 内圈和外圈的四个顶点（球面坐标）
                Vec3 inner1 = getSphericalPoint(centerDir, tangent1, tangent2, radius, ringAngle - ringThickness, azimuth1);
                Vec3 outer1 = getSphericalPoint(centerDir, tangent1, tangent2, radius, ringAngle + ringThickness, azimuth1);
                Vec3 inner2 = getSphericalPoint(centerDir, tangent1, tangent2, radius, ringAngle - ringThickness, azimuth2);
                Vec3 outer2 = getSphericalPoint(centerDir, tangent1, tangent2, radius, ringAngle + ringThickness, azimuth2);
                
                // 颜色：中心更亮，边缘更暗
                float centerBrightness = 1.8f;
                float edgeBrightness = 1.0f;
                
                // 第一个三角形 (inner1, outer1, inner2)
                buffer.addVertex(matrix, (float)inner1.x, (float)inner1.y, (float)inner1.z)
                      .setColor(color[0] * centerBrightness, color[1] * centerBrightness, color[2] * centerBrightness, ringAlpha);
                buffer.addVertex(matrix, (float)outer1.x, (float)outer1.y, (float)outer1.z)
                      .setColor(color[0] * edgeBrightness, color[1] * edgeBrightness, color[2] * edgeBrightness, ringAlpha * 0.6f);
                buffer.addVertex(matrix, (float)inner2.x, (float)inner2.y, (float)inner2.z)
                      .setColor(color[0] * centerBrightness, color[1] * centerBrightness, color[2] * centerBrightness, ringAlpha);
                
                // 第二个三角形 (inner2, outer1, outer2)
                buffer.addVertex(matrix, (float)inner2.x, (float)inner2.y, (float)inner2.z)
                      .setColor(color[0] * centerBrightness, color[1] * centerBrightness, color[2] * centerBrightness, ringAlpha);
                buffer.addVertex(matrix, (float)outer1.x, (float)outer1.y, (float)outer1.z)
                      .setColor(color[0] * edgeBrightness, color[1] * edgeBrightness, color[2] * edgeBrightness, ringAlpha * 0.6f);
                buffer.addVertex(matrix, (float)outer2.x, (float)outer2.y, (float)outer2.z)
                      .setColor(color[0] * edgeBrightness, color[1] * edgeBrightness, color[2] * edgeBrightness, ringAlpha * 0.6f);
            }
        }
        
        BufferUploader.drawWithShader(buffer.buildOrThrow());
        
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }
    
    /**
     * 获取垂直于给定向量的切向量
     */
    private static Vec3 getTangent(Vec3 normal) {
        // 选择一个不平行于法向量的向量
        Vec3 arbitrary = Math.abs(normal.y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        return normal.cross(arbitrary).normalize();
    }
    
    /**
     * 在球面上获取一个点（使用球面坐标系统）
     * @param centerDir 中心方向（击中点方向）
     * @param tangent1 第一个切向量
     * @param tangent2 第二个切向量
     * @param sphereRadius 球面半径
     * @param polarAngle 极角（从中心方向的偏离角度）
     * @param azimuthAngle 方位角（围绕中心方向的旋转角度）
     * @return 球面上的点坐标
     */
    private static Vec3 getSphericalPoint(Vec3 centerDir, Vec3 tangent1, Vec3 tangent2, double sphereRadius, float polarAngle, float azimuthAngle) {
        // 在局部坐标系中计算偏移方向
        double x = Math.sin(polarAngle) * Math.cos(azimuthAngle);
        double y = Math.sin(polarAngle) * Math.sin(azimuthAngle);
        double z = Math.cos(polarAngle);
        
        // 组合三个基向量得到最终方向
        Vec3 direction = tangent1.scale(x)
                        .add(tangent2.scale(y))
                        .add(centerDir.scale(z))
                        .normalize();
        
        // 返回球面上的点
        return direction.scale(sphereRadius);
    }
    
    /**
     * 独立渲染破碎效果（当护盾已关闭但破碎动画还在播放时）
     */
    private static void renderShatterEffectOnly(RenderLevelStageEvent event, Entity entity) {
        PoseStack poseStack = event.getPoseStack();
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        
        // 获取相机位置
        Vec3 cameraPos = event.getCamera().getPosition();
        
        // 使用 partialTick 插值实体位置
        Vec3 shieldCenter = new Vec3(
            Mth.lerp(partialTick, entity.xOld, entity.getX()),
            Mth.lerp(partialTick, entity.yOld, entity.getY()) + entity.getEyeHeight() / 2,
            Mth.lerp(partialTick, entity.zOld, entity.getZ())
        );
        
        // 计算相对位置
        double relX = shieldCenter.x - cameraPos.x;
        double relY = shieldCenter.y - cameraPos.y;
        double relZ = shieldCenter.z - cameraPos.z;
        
        poseStack.pushPose();
        poseStack.translate(relX, relY, relZ);
        
        // 更新破碎效果
        ShieldShatterEffect.update();
        
        // 渲染破碎效果
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(515);
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        
        Matrix4f matrix = poseStack.last().pose();
        
        // 使用默认颜色（蓝色）
        float[] color = new float[]{0.3f, 0.7f, 1.0f};
        
        // 渲染破碎碎片
        ShieldShatterEffect.renderShatter(buffer, matrix, entity.getId(), shieldCenter,
                color[0], color[1], color[2], 0.8f);
        
        // 尝试渲染，如果buffer为空则忽略
        try {
            BufferUploader.drawWithShader(buffer.buildOrThrow());
        } catch (IllegalStateException ignored) {
            // Buffer为空时忽略
        }
        
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        
        poseStack.popPose();
    }
}
