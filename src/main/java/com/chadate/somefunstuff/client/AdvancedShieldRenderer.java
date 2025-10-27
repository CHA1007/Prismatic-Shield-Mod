package com.chadate.somefunstuff.client;

import com.chadate.somefunstuff.capability.ShieldCapabilities;
import com.chadate.somefunstuff.capability.ShieldCapability;
import com.chadate.somefunstuff.client.render.HexagonalShieldMesh;
import com.chadate.somefunstuff.client.render.ShieldParticleSystem;
import com.chadate.somefunstuff.client.render.ShieldImpactEffect;
import com.mojang.blaze3d.systems.RenderSystem;
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
        int strength = shield.getShieldStrength();
        double radius = shield.getShieldRadius();
        float[] color = getShieldColor(strength);
        float time = (entity.tickCount + partialTick) * 0.05f;
        
        // 更新受击效果
        ShieldImpactEffect.update();
        
        // === 多层渲染 ===
        
        // 第1层：内层能量场（半透明球体，菲涅尔效果）
        renderInnerEnergyField(poseStack, radius * 0.95, color, time);
        
        // 第2层：六边形蜂巢网格（带受击效果）
        renderHexagonalLayer(poseStack, radius, color, time, strength, shieldCenter);
        
        // 第3层：受击脉冲圆环（扩散并渐隐）
        renderImpactRings(poseStack, radius, color, time, shieldCenter, entity.getId());
        
        // 第4层：GPU粒子系统
        renderParticleLayer(poseStack, radius * 1.05, color, time);
        
        // 第5层：外层光晕
        renderOuterGlow(poseStack, radius * 1.1, color, time);
        
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
    
    /**
     * 第3层：GPU粒子系统
     */
    /**
     * 受击脉冲圆环：在受击点沿球面生成一个随时间扩散并淡出的细环
     */
    private static void renderImpactRings(PoseStack poseStack, double radius, float[] color, float time, Vec3 shieldCenter, int entityId) {
        // 渲染设置
        RenderSystem.enableBlend();
        // 加法混合，增强亮度但不增加遮挡
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        
        // *** 关键修复 ***
        // 启用深度测试，使扩散环能够被前方物体（如角色）正确遮挡
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(515); // GL_LEQUAL - 小于等于深度值的片段通过测试
        
        // 关闭深度写入（半透明物体不应修改深度缓冲区）
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        boolean wroteAnyVertex = false;
        Matrix4f matrix = poseStack.last().pose();
        
        // 圆环参数
        final int SEGMENTS = 96;                // 圆环离散段数
        final float THICKNESS_FRAC = 0.04f;     // 环厚（按球半径的弧长比例）
        final float BASE_ALPHA = 1.5f * GLOW_LAYER_ALPHA_MULTIPLIER; // 基础透明度，随时间再淡出
        final float COLOR_BOOST = 1.5f;         // 颜色提升
        // 边缘噪声（细小扰动让边界更生动）
        final float NOISE_AMP = 0.015f;         // 角度扰动幅度（弧度）
        final float NOISE_FREQ = 7.0f;          // 随 theta 频率
        final float NOISE_TIME_SPEED = 3.0f;    // 随时间流速
        // 闪爆内环参数
        final float FLASH_PHASE = 0.2f;         // 受击初期阶段（0..FLASH_PHASE）
        final float FLASH_THICK_SCALE = 0.5f;   // 内环厚度比例
        final float FLASH_ALPHA_BOOST = 1.5f;   // 内环额外亮度
        
        // 只遍历当前实体的活跃受击
        var impacts = com.chadate.somefunstuff.client.render.ShieldImpactEffect.getActiveImpactsForEntity(entityId);
        for (var impact : impacts) {
            // 进度与淡出
            float progress = impact.getProgress((long)(System.currentTimeMillis() / 50)); // 0..1
            float fade = (1.0f - progress);
            if (fade <= 0.01f) continue;
            
            // 波半径（球面弧长 = progress * 0.8 * radius） -> 对应夹角g（弧度）
            double waveRadius = progress * radius * 0.8; // 物理半径上的目标弧长
            float g = (float)(waveRadius / radius);      // 球心夹角（弧度），近似等于弧长/半径
            float dg = THICKNESS_FRAC;                   // 厚度对应的角度
            float gInner = Math.max(0.0f, g - dg * 0.5f);
            float gOuter = g + dg * 0.5f;
            
            // 环中心方向（单位向量）- 直接使用存储的相对方向
            Vec3 cVec = impact.directionFromCenter;
            // 选取参考向量避免与cVec平行
            Vec3 up = Math.abs(cVec.y) > 0.9 ? new Vec3(1,0,0) : new Vec3(0,1,0);
            Vec3 t1v = cVec.cross(up).normalize();
            Vec3 t2v = cVec.cross(t1v).normalize();
            
            // 颜色（稍微提亮）
            float cr = Math.min(1.0f, color[0] * COLOR_BOOST);
            float cg = Math.min(1.0f, color[1] * COLOR_BOOST);
            float cb = Math.min(1.0f, color[2] * COLOR_BOOST);
            float alpha = BASE_ALPHA * fade;
            
            // 生成圆环（三角带：外边一圈 + 内边一圈）
            for (int i = 0; i <= SEGMENTS; i++) {
                float theta = (float)(i * (Math.PI * 2.0) / SEGMENTS);
                // 环方向（切向旋转）
                Vec3 dir = new Vec3(
                    t1v.x * Math.cos(theta) + t2v.x * Math.sin(theta),
                    t1v.y * Math.cos(theta) + t2v.y * Math.sin(theta),
                    t1v.z * Math.cos(theta) + t2v.z * Math.sin(theta)
                );
                // 边缘噪声扰动（随角度与时间变化）
                float edgeNoise = (float)Math.sin(theta * NOISE_FREQ + time * NOISE_TIME_SPEED + i * 0.37f) * NOISE_AMP;
                float gOuterN = Math.max(0.0f, gOuter + edgeNoise * 0.7f);
                float gInnerN = Math.max(0.0f, gInner + edgeNoise * 1.0f);
                
                // 外圈点（角度 gOuterN）
                Vec3 onSphereOuter = cVec.scale(Math.cos(gOuterN)).add(dir.scale(Math.sin(gOuterN))).normalize().scale(radius);
                // 内圈点（角度 gInnerN）
                Vec3 onSphereInner = cVec.scale(Math.cos(gInnerN)).add(dir.scale(Math.sin(gInnerN))).normalize().scale(radius);
                
                // 写入两点形成条带
                buffer.addVertex(matrix, (float)onSphereOuter.x, (float)onSphereOuter.y, (float)onSphereOuter.z)
                      .setColor(cr, cg, cb, alpha);
                buffer.addVertex(matrix, (float)onSphereInner.x, (float)onSphereInner.y, (float)onSphereInner.z)
                      .setColor(cr, cg, cb, alpha * 0.8f);
                wroteAnyVertex = true;
            }
            
            // 受击初期叠加极细高亮内环（闪爆点）
            if (progress < FLASH_PHASE) {
                float flashK = 1.0f - (progress / FLASH_PHASE); // 0..1
                float flashAlpha = Math.min(1.0f, alpha * FLASH_ALPHA_BOOST * flashK);
                float dgFlash = dg * FLASH_THICK_SCALE;
                float gInnerFlash = Math.max(0.0f, g - dgFlash * 0.5f);
                float gOuterFlash = g + dgFlash * 0.5f;
                for (int i = 0; i <= SEGMENTS; i++) {
                    float theta = (float)(i * (Math.PI * 2.0) / SEGMENTS);
                    Vec3 dir = new Vec3(
                        t1v.x * Math.cos(theta) + t2v.x * Math.sin(theta),
                        t1v.y * Math.cos(theta) + t2v.y * Math.sin(theta),
                        t1v.z * Math.cos(theta) + t2v.z * Math.sin(theta)
                    );
                    // 更少的噪声，保证更“干净”的高亮环
                    float edgeNoise2 = (float)Math.sin(theta * (NOISE_FREQ * 0.5f) + time * (NOISE_TIME_SPEED * 0.5f)) * (NOISE_AMP * 0.3f);
                    float go = Math.max(0.0f, gOuterFlash + edgeNoise2 * 0.5f);
                    float gi = Math.max(0.0f, gInnerFlash + edgeNoise2 * 0.5f);
                    Vec3 pOuter = cVec.scale(Math.cos(go)).add(dir.scale(Math.sin(go))).normalize().scale(radius);
                    Vec3 pInner = cVec.scale(Math.cos(gi)).add(dir.scale(Math.sin(gi))).normalize().scale(radius);
                    // 用更亮的颜色（白色倾向）
                    float wr = Math.min(1.0f, (cr + 1.0f) * 0.5f);
                    float wg = Math.min(1.0f, (cg + 1.0f) * 0.5f);
                    float wb = Math.min(1.0f, (cb + 1.0f) * 0.5f);
                    buffer.addVertex(matrix, (float)pOuter.x, (float)pOuter.y, (float)pOuter.z)
                          .setColor(wr, wg, wb, flashAlpha);
                    buffer.addVertex(matrix, (float)pInner.x, (float)pInner.y, (float)pInner.z)
                          .setColor(wr, wg, wb, flashAlpha * 0.9f);
                    wroteAnyVertex = true;
                }
            }
        }
        
        if (wroteAnyVertex) {
            BufferUploader.drawWithShader(buffer.buildOrThrow());
        }
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        // 恢复默认混合
        RenderSystem.defaultBlendFunc();
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
}
