package com.chadate.funeralmagic.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 护盾破碎效果系统
 * 当护盾强度降到0时，生成破碎动画
 */
public class ShieldShatterEffect {

    private static final List<ShatterInstance> activeShatterEffects = new ArrayList<>();
    private static final Random RANDOM = new Random();
    private static final int SHATTER_DURATION = 30; // 破碎动画持续时间（游戏刻）
    private static final int FRAGMENT_COUNT = 60; // 碎片数量

    /**
     * 破碎实例
     */
    public static class ShatterInstance {
        public final int entityId;
        public final long startTime;
        public final List<Fragment> fragments;
        public final Vec3 center;
        public final double radius;

        public ShatterInstance(int entityId, Vec3 center, double radius) {
            this.entityId = entityId;
            this.startTime = System.currentTimeMillis() / 50;
            this.center = center;
            this.radius = radius;
            this.fragments = new ArrayList<>();

            // 生成碎片
            generateFragments();
        }

        private void generateFragments() {
            // 使用测地线球的顶点作为碎片中心
            float goldenRatio = (1.0f + Mth.sqrt(5.0f)) / 2.0f;

            for (int i = 0; i < FRAGMENT_COUNT; i++) {
                float theta = (float) (2.0f * Math.PI * i / goldenRatio);
                float phi = (float) (Math.acos(1.0f - 2.0f * (i + 0.5f) / FRAGMENT_COUNT));

                float x = (float) (radius * Math.sin(phi) * Math.cos(theta));
                float y = (float) (radius * Math.cos(phi));
                float z = (float) (radius * Math.sin(phi) * Math.sin(theta));

                Vec3 position = new Vec3(x, y, z);
                Vec3 velocity = position.normalize().scale(0.1 + RANDOM.nextFloat() * 0.15);

                // 添加随机旋转
                float rotationSpeed = (RANDOM.nextFloat() - 0.5f) * 0.3f;

                fragments.add(new Fragment(position, velocity, rotationSpeed));
            }
        }

        public float getProgress() {
            long currentTime = System.currentTimeMillis() / 50;
            long elapsed = currentTime - startTime;
            return Math.min(1.0f, elapsed / (float) SHATTER_DURATION);
        }

        public boolean isExpired() {
            return getProgress() >= 1.0f;
        }
    }

    /**
     * 碎片
     */
    public static class Fragment {
        public Vec3 position;
        public Vec3 velocity;
        public float rotation;
        public float rotationSpeed;
        public float size;

        public Fragment(Vec3 position, Vec3 velocity, float rotationSpeed) {
            this.position = position;
            this.velocity = velocity;
            this.rotation = RANDOM.nextFloat() * Mth.TWO_PI;
            this.rotationSpeed = rotationSpeed;
            this.size = 0.15f + RANDOM.nextFloat() * 0.1f;
        }

        public void update(float progress) {
            // 更新位置（带重力效果）
            position = position.add(velocity);
            velocity = velocity.add(0, -0.02 * progress, 0); // 重力加速

            // 更新旋转
            rotation += rotationSpeed;
        }
    }

    /**
     * 触发护盾破碎效果
     */
    public static void triggerShatter(int entityId, Vec3 shieldCenter, double radius) {
        // 移除该实体的旧破碎效果
        activeShatterEffects.removeIf(effect -> effect.entityId == entityId);

        // 添加新的破碎效果
        activeShatterEffects.add(new ShatterInstance(entityId, shieldCenter, radius));
    }

    /**
     * 更新所有破碎效果
     */
    public static void update() {
        activeShatterEffects.removeIf(ShatterInstance::isExpired);

        for (ShatterInstance shatter : activeShatterEffects) {
            float progress = shatter.getProgress();
            for (Fragment fragment : shatter.fragments) {
                fragment.update(progress);
            }
        }
    }

    /**
     * 渲染破碎效果
     */
    public static void renderShatter(VertexConsumer consumer, Matrix4f matrix,
                                     int entityId, Vec3 shieldCenter,
                                     float r, float g, float b, float alpha) {
        for (ShatterInstance shatter : activeShatterEffects) {
            if (shatter.entityId != entityId) continue;

            float progress = shatter.getProgress();
            float fade = 1.0f - progress; // 淡出效果

            for (Fragment fragment : shatter.fragments) {
                // 计算世界位置
                Vec3 worldPos = shatter.center.add(fragment.position);
                Vec3 relativePos = worldPos.subtract(shieldCenter);

                // 渲染碎片
                renderFragment(consumer, matrix, relativePos, fragment,
                        r, g, b, alpha * fade * 1.5f);
            }
        }
    }

    /**
     * 渲染单个碎片
     */
    private static void renderFragment(VertexConsumer consumer, Matrix4f matrix,
                                       Vec3 position, Fragment fragment,
                                       float r, float g, float b, float alpha) {
        Vector3f pos = new Vector3f((float) position.x, (float) position.y, (float) position.z);
        Vector3f normal = new Vector3f(pos).normalize();

        // 构建局部坐标系
        Vector3f tangent = getTangent(normal);
        Vector3f bitangent = new Vector3f(normal).cross(tangent).normalize();

        // 应用旋转
        float cos = Mth.cos(fragment.rotation);
        float sin = Mth.sin(fragment.rotation);
        Vector3f rotatedTangent = new Vector3f(
                tangent.x * cos - bitangent.x * sin,
                tangent.y * cos - bitangent.y * sin,
                tangent.z * cos - bitangent.z * sin
        );
        Vector3f rotatedBitangent = new Vector3f(
                tangent.x * sin + bitangent.x * cos,
                tangent.y * sin + bitangent.y * cos,
                tangent.z * sin + bitangent.z * cos
        );

        // 三角形碎片（随机形状）
        float size = fragment.size;
        Vector3f v1 = new Vector3f(pos).add(rotatedTangent.mul(size, new Vector3f()));
        Vector3f v2 = new Vector3f(pos).add(rotatedBitangent.mul(size * 0.8f, new Vector3f()));
        Vector3f v3 = new Vector3f(pos).sub(rotatedTangent.mul(size * 0.6f, new Vector3f()))
                .sub(rotatedBitangent.mul(size * 0.4f, new Vector3f()));

        // 渲染三角形
        consumer.addVertex(matrix, v1.x, v1.y, v1.z)
                .setColor(r * 1.2f, g * 1.2f, b * 1.2f, alpha);
        consumer.addVertex(matrix, v2.x, v2.y, v2.z)
                .setColor(r, g, b, alpha * 0.8f);
        consumer.addVertex(matrix, v3.x, v3.y, v3.z)
                .setColor(r * 0.8f, g * 0.8f, b * 0.8f, alpha * 0.6f);
    }

    /**
     * 计算法向量的切线
     */
    private static Vector3f getTangent(Vector3f normal) {
        Vector3f up = Math.abs(normal.y) < 0.9f ? new Vector3f(0, 1, 0) : new Vector3f(1, 0, 0);
        return new Vector3f(normal).cross(up).normalize();
    }

    /**
     * 清空所有破碎效果
     */
    public static void clear() {
        activeShatterEffects.clear();
    }

    /**
     * 检查是否有活跃的破碎效果
     */
    public static boolean hasActiveShatter(int entityId) {
        return activeShatterEffects.stream()
                .anyMatch(effect -> effect.entityId == entityId);
    }
}
