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
        renderHexagonalLayer(poseStack, radius, color, time, strength, shieldCenter);
        
        // 第3层：受击脉冲圆环
        // renderImpactRings(poseStack, radius, color, time, shieldCenter, entity.getId());
        
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
                
                // 脉动效果
                float pulse = (Mth.sin(time) + 1.0f) * 0.1f + 0.4f;
                // 应用内层透明度控制
                float finalAlpha = pulse * INNER_LAYER_ALPHA_MULTIPLIER;
                
                // 三角形1（反转顶点顺序：v1->v3->v2，让正面朝外）
                buffer.addVertex(matrix, v1.x, v1.y, v1.z)
                      .setColor(color[0], color[1], color[2], finalAlpha);
                buffer.addVertex(matrix, v3.x, v3.y, v3.z)
                      .setColor(color[0], color[1], color[2], finalAlpha);
                buffer.addVertex(matrix, v2.x, v2.y, v2.z)
                      .setColor(color[0], color[1], color[2], finalAlpha);
                
                // 三角形2（反转顶点顺序：v1->v4->v3，让正面朝外）
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
    private static void renderHexagonalLayer(PoseStack poseStack, double radius, float[] color, float time, int strength, Vec3 shieldCenter) {
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
        
        // 护盾强度影响透明度
        float baseAlpha = 0.6f * (strength / 100.0f);
        // 应用六边形层透明度控制
        float hexAlpha = baseAlpha * HEX_LAYER_ALPHA_MULTIPLIER;
        
        // 渲染六边形网格（传递护盾中心用于受击效果）
        HexagonalShieldMesh.renderHexagonalShield(buffer, matrix, radius, 
            color[0], color[1], color[2], hexAlpha, time, shieldCenter);
        
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
                
                // === 多重动态效果 ===
                
                // 1. 基础脉动（整体呼吸）
                float basePulse = (Mth.sin(time * 1.5f) + 1.0f) * 0.5f;
                
                // 2. 从顶部到底部的波纹流动
                float waveFlow = Mth.sin(time * 2.0f + (float)theta1 * 3.0f);
                float waveAlpha = (waveFlow + 1.0f) * 0.15f;
                
                // 3. 旋转流光效果（围绕护盾旋转）
                float rotatingGlow = Mth.sin(time * 3.0f + (float)phi1 * 2.0f);
                float rotateAlpha = (rotatingGlow + 1.0f) * 0.1f;
                
                // 4. 高频闪烁（增加细节）
                float shimmer = Mth.sin(time * 8.0f + (float)(theta1 + phi1)) * 0.05f;
                
                // 组合所有效果
                float glowPulse = (basePulse * 0.4f + waveAlpha + rotateAlpha + shimmer) * 0.5f;
                // 应用外层光晕透明度控制
                float finalGlowAlpha = glowPulse * GLOW_LAYER_ALPHA_MULTIPLIER;
                
                // 颜色强度随位置变化（顶部和底部更亮）
                float heightFactor = Math.abs(Mth.cos((float)theta1));
                float colorBoost = 1.3f + heightFactor * 0.5f;
                
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
     * 根据护盾强度返回颜色
     */
    private static float[] getShieldColor(int strength) {
        if (strength > 50) {
            // 强力护盾：柔和青蓝色（降低饱和度）
            return new float[]{0.15f, 0.4f, 0.6f};
        } else if (strength > 20) {
            // 中等护盾：柔和紫色（降低饱和度）
            return new float[]{0.5f, 0.2f, 0.6f};
        } else {
            // 弱化护盾：柔和橙红色（降低饱和度）
            return new float[]{0.6f, 0.2f, 0.15f};
        }
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
