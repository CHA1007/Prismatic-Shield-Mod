package com.chadate.somefunstuff.client;

import com.chadate.somefunstuff.SomeFunStuff;
import com.chadate.somefunstuff.capability.ShieldCapabilities;
import com.chadate.somefunstuff.capability.ShieldCapability;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/**
 * 客户端护盾渲染处理器
 * 使用真正的3D渲染来绘制半透明护盾球体
 */
public class ShieldRenderHandler {
    
    private static int debugCounter = 0;
    
    /**
     * 注册渲染事件
     */
    public static void register() {
        SomeFunStuff.LOGGER.info("[ShieldRender] 注册护盾渲染事件...");
    }
    
    /**
     * 在世界渲染阶段绘制护盾球体
     */
    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        // 只在半透明渲染阶段绘制
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        
        Player player = mc.player;
        @SuppressWarnings("null")
        ShieldCapability shield = player.getData(ShieldCapabilities.SHIELD_ATTACHMENT);
        
        if (shield == null) {
            return;
        }
        
        if (!shield.isShieldActive()) {
            return;
        }
        
        // 调试日志 - 每60帧输出一次
        debugCounter++;
        if (debugCounter % 60 == 0) {
            SomeFunStuff.LOGGER.info("[ShieldRender] 正在渲染护盾! 强度: {}, 半径: {}, 激活: {}",
                shield.getShieldStrength(), shield.getShieldRadius(), shield.isShieldActive());
        }
        
        // 渲染护盾球体
        renderShieldSphere(event, player, shield);
    }
    
    /**
     * 渲染护盾球体（真正的3D渲染）
     */
    private static void renderShieldSphere(RenderLevelStageEvent event, Player player, ShieldCapability shield) {
        PoseStack poseStack = event.getPoseStack();
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        
        // 获取相机位置
        Vec3 cameraPos = event.getCamera().getPosition();
        
        // 计算护盾中心位置（玩家胸部高度）
        Vec3 shieldCenter = player.position().add(0, player.getEyeHeight() / 2, 0);
        
        // 计算相对于相机的位置
        double relX = shieldCenter.x - cameraPos.x;
        double relY = shieldCenter.y - cameraPos.y;
        double relZ = shieldCenter.z - cameraPos.z;
        
        poseStack.pushPose();
        poseStack.translate(relX, relY, relZ);
        
        // 根据护盾强度选择颜色和透明度
        int strength = shield.getShieldStrength();
        float[] color = getShieldColor(strength);
        float alpha = 0.5f; // 增加基础透明度，使其更明显
        
        // 添加脉动效果
        float time = (player.tickCount + partialTick) * 0.05f;
        float pulse = (Mth.sin(time) + 1.0f) * 0.15f; // 0.0 到 0.3
        alpha += pulse;
        
        // 渲染球体
        double radius = shield.getShieldRadius();
        renderSphere(poseStack, radius, color[0], color[1], color[2], alpha);
        
        // 添加能量环特效
        renderEnergyRings(poseStack, radius, time, color[0], color[1], color[2]);
        
        // 添加流动光点特效
        renderFlowingParticles(poseStack, radius, time, color[0], color[1], color[2]);
        
        poseStack.popPose();
    }
    
    /**
     * 根据护盾强度返回颜色
     */
    private static float[] getShieldColor(int strength) {
        if (strength > 50) {
            // 强力护盾：青蓝色
            return new float[]{0.1f, 0.3f, 0.5f};
        } else if (strength > 20) {
            // 中等护盾：紫色
            return new float[]{0.4f, 0.1f, 0.5f};
        } else {
            // 弱化护盾：橙红色（警告）
            return new float[]{0.5f, 0.15f, 0.1f};
        }
    }
    
    /**
     * 渲染一个半透明球体
     */
    private static void renderSphere(PoseStack poseStack, double radius, float r, float g, float b, float alpha) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.depthMask(false);
        
        // 禁用背面剭除，使护盾从任何角度都可见
        RenderSystem.disableCull();
        
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        
        Matrix4f matrix = poseStack.last().pose();
        
        // 渲染球体（使用经纬度方法）
        int latitudeBands = 16; // 纬度带数
        int longitudeBands = 16; // 经度带数
        
        for (int lat = 0; lat < latitudeBands; lat++) {
            double theta1 = lat * Math.PI / latitudeBands;
            double theta2 = (lat + 1) * Math.PI / latitudeBands;
            
            for (int lon = 0; lon <= longitudeBands; lon++) {
                double phi = lon * 2 * Math.PI / longitudeBands;
                
                // 第一个顶点
                double x1 = radius * Math.sin(theta1) * Math.cos(phi);
                double y1 = radius * Math.cos(theta1);
                double z1 = radius * Math.sin(theta1) * Math.sin(phi);
                
                // 第二个顶点
                double x2 = radius * Math.sin(theta2) * Math.cos(phi);
                double y2 = radius * Math.cos(theta2);
                double z2 = radius * Math.sin(theta2) * Math.sin(phi);
                
                // 添加顶点到缓冲区
                buffer.addVertex(matrix, (float)x1, (float)y1, (float)z1)
                    .setColor(r, g, b, alpha);
                buffer.addVertex(matrix, (float)x2, (float)y2, (float)z2)
                    .setColor(r, g, b, alpha);
            }
        }
        
        BufferUploader.drawWithShader(buffer.buildOrThrow());
        
        // 恢复渲染状态
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }
    
    /**
     * 渲染旋转的能量环特效
     */
    private static void renderEnergyRings(PoseStack poseStack, double radius, float time, float r, float g, float b) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        
        Tesselator tesselator = Tesselator.getInstance();
        Matrix4f matrix = poseStack.last().pose();
        
        // 渲染3个旋转的能量环
        int ringCount = 3;
        for (int i = 0; i < ringCount; i++) {
            float ringAngle = (time * 0.5f + i * 120) % 360;
            float ringAlpha = 0.6f + Mth.sin(time + i) * 0.2f;
            
            BufferBuilder buffer = tesselator.begin(VertexFormat.Mode.DEBUG_LINE_STRIP, DefaultVertexFormat.POSITION_COLOR);
            
            int segments = 32;
            for (int j = 0; j <= segments; j++) {
                float angle = (float) (j * 2 * Math.PI / segments);
                
                // 旋转坐标系
                double x = radius * 1.05 * Math.cos(angle);
                double y = radius * 1.05 * Math.sin(angle) * Math.cos(Math.toRadians(ringAngle));
                double z = radius * 1.05 * Math.sin(angle) * Math.sin(Math.toRadians(ringAngle));
                
                buffer.addVertex(matrix, (float)x, (float)y, (float)z)
                    .setColor(r * 1.5f, g * 1.5f, b * 1.5f, ringAlpha);
            }
            
            BufferUploader.drawWithShader(buffer.buildOrThrow());
        }
        
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }
    
    /**
     * 渲染表面流动的光点
     */
    private static void renderFlowingParticles(PoseStack poseStack, double radius, float time, float r, float g, float b) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.depthMask(false);
        
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        Matrix4f matrix = poseStack.last().pose();
        
        // 在球体表面生成流动的光点
        int particleCount = 20;
        for (int i = 0; i < particleCount; i++) {
            // 使用时间和索引生成伪随机位置
            float seed = i * 0.618f;
            float theta = (float) ((time * 0.1f + seed * Math.PI * 2) % (Math.PI * 2));
            float phi = (float) ((time * 0.05f + seed * Math.PI) % Math.PI);
            
            double x = radius * Math.sin(phi) * Math.cos(theta);
            double y = radius * Math.cos(phi);
            double z = radius * Math.sin(phi) * Math.sin(theta);
            
            // 粒子大小和透明度
            float size = 0.1f;
            float alpha = (Mth.sin(time * 2 + i) + 1) * 0.3f;
            
            // 绘制一个小方块作为光点
            // 简化版：只绘制面向相机的面
            buffer.addVertex(matrix, (float)(x - size), (float)(y - size), (float)z)
                .setColor(r * 2, g * 2, b * 2, alpha);
            buffer.addVertex(matrix, (float)(x + size), (float)(y - size), (float)z)
                .setColor(r * 2, g * 2, b * 2, alpha);
            buffer.addVertex(matrix, (float)(x + size), (float)(y + size), (float)z)
                .setColor(r * 2, g * 2, b * 2, alpha);
            buffer.addVertex(matrix, (float)(x - size), (float)(y + size), (float)z)
                .setColor(r * 2, g * 2, b * 2, alpha);
        }
        
        BufferUploader.drawWithShader(buffer.buildOrThrow());
        
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }
}
