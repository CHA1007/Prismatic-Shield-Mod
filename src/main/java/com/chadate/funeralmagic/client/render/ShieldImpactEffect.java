package com.chadate.funeralmagic.client.render;

import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 护盾受击效果管理器
 * 管理所有护盾击中点的视觉效果
 */
public class ShieldImpactEffect {

    // 存储所有活跃的击中效果
    private static final List<ImpactPoint> activeImpacts = new ArrayList<>();

    // 击中效果持续时间（游戏刻）
    private static final int IMPACT_DURATION = 40; // 2秒

    /**
     * 击中点数据结构
     */
    public static class ImpactPoint {
        public final int entityId; // 实体ID（用于区分不同实体的护盾）
        public final Vec3 directionFromCenter; // 击中点相对于护盾中心的方向向量（单位向量）
        public final long startTime; // 开始时间（游戏刻）
        public float intensity; // 强度（逐渐衰减）

        /**
         * 创建击中点（存储相对方向而非世界坐标）
         * 
         * @param entityId            实体ID
         * @param directionFromCenter 从护盾中心指向击中点的单位方向向量
         * @param startTime           开始时间
         */
        public ImpactPoint(int entityId, Vec3 directionFromCenter, long startTime) {
            this.entityId = entityId;
            this.directionFromCenter = directionFromCenter.normalize();
            this.startTime = startTime;
            this.intensity = 1.0f;
        }

        /**
         * 获取击中效果的归一化时间进度（0.0-1.0）
         */
        public float getProgress(long currentTime) {
            long elapsed = currentTime - startTime;
            return Math.min(1.0f, elapsed / (float) IMPACT_DURATION);
        }

        /**
         * 判断效果是否已过期
         */
        public boolean isExpired(long currentTime) {
            return (currentTime - startTime) > IMPACT_DURATION;
        }
    }

    /**
     * 注册一个新的击中效果
     * 
     * @param entityId     实体ID（用于区分不同实体的护盾）
     * @param hitPosition  击中位置（世界坐标）
     * @param shieldCenter 护盾中心（世界坐标）
     */
    public static void registerImpact(int entityId, Vec3 hitPosition, Vec3 shieldCenter) {
        long currentTime = System.currentTimeMillis() / 50; // 转换为游戏刻

        // 计算相对方向向量（从护盾中心指向击中点）
        Vec3 direction = hitPosition.subtract(shieldCenter).normalize();
        activeImpacts.add(new ImpactPoint(entityId, direction, currentTime));

    }

    /**
     * 更新并清理过期的击中效果
     */
    public static void update() {
        long currentTime = System.currentTimeMillis() / 50;

        Iterator<ImpactPoint> iterator = activeImpacts.iterator();
        while (iterator.hasNext()) {
            ImpactPoint impact = iterator.next();

            // 更新强度（衰减）
            float progress = impact.getProgress(currentTime);
            impact.intensity = 1.0f - progress;

            // 移除过期的效果
            if (impact.isExpired(currentTime)) {
                iterator.remove();
            }
        }

        // 定期输出活跃击中数（仅当有效果时）
        if (!activeImpacts.isEmpty() && currentTime % 20 == 0) {
        }
    }

    /**
     * 获取所有活跃的击中效果
     */
    public static List<ImpactPoint> getActiveImpacts() {
        return activeImpacts;
    }

    /**
     * 获取指定实体的活跃击中效果
     * 
     * @param entityId 实体ID
     * @return 属于该实体的击中点列表
     */
    public static List<ImpactPoint> getActiveImpactsForEntity(int entityId) {
        return activeImpacts.stream()
                .filter(impact -> impact.entityId == entityId)
                .toList();
    }

    /**
     * 清空所有击中效果
     */
    public static void clear() {
        activeImpacts.clear();
    }

    /**
     * 计算某个位置受击中效果的影响强度
     * 
     * @param position     要检查的位置
     * @param shieldCenter 护盾中心
     * @param shieldRadius 护盾半径
     * @return 影响强度（0.0-1.0）
     */
    public static float getImpactInfluence(Vec3 position, Vec3 shieldCenter, double shieldRadius) {
        long currentTime = System.currentTimeMillis() / 50;
        float maxInfluence = 0.0f;

        for (ImpactPoint impact : activeImpacts) {
            // 使用相对方向向量计算击中点在护盾表面的实际位置
            Vec3 impactPositionOnShield = shieldCenter.add(impact.directionFromCenter.scale(shieldRadius));

            // 计算距离
            double distance = position.distanceTo(impactPositionOnShield);

            // 获取进度
            float progress = impact.getProgress(currentTime);

            // 冲击波半径（从0扩散到护盾半径）
            double waveRadius = progress * shieldRadius * 0.8;

            // 冲击波厚度
            double waveThickness = shieldRadius * 0.15;

            // 判断是否在冲击波范围内
            double distanceToWave = Math.abs(distance - waveRadius);

            if (distanceToWave < waveThickness) {
                // 计算影响强度
                float waveFalloff = 1.0f - (float) (distanceToWave / waveThickness);
                float timeDecay = 1.0f - progress; // 随时间衰减
                float influence = waveFalloff * timeDecay * impact.intensity;

                maxInfluence = Math.max(maxInfluence, influence);
            }
        }

        return maxInfluence;
    }

    /**
     * 获取击中点闪光效果强度
     * 
     * @param position 要检查的位置
     * @return 闪光强度（0.0-1.0）
     */
    public static float getFlashIntensity(Vec3 position, Vec3 shieldCenter, double shieldRadius) {
        long currentTime = System.currentTimeMillis() / 50;
        float maxFlash = 0.0f;

        for (ImpactPoint impact : activeImpacts) {
            // 使用相对方向向量计算击中点在护盾表面的实际位置
            Vec3 impactPositionOnShield = shieldCenter.add(impact.directionFromCenter.scale(shieldRadius));
            double distance = position.distanceTo(impactPositionOnShield);
            float progress = impact.getProgress(currentTime);

            // 击中点附近的强烈闪光（快速衰减）
            if (distance < 0.5) {
                float flashIntensity = (1.0f - progress * 3.0f); // 快速衰减
                if (flashIntensity > 0) {
                    maxFlash = Math.max(maxFlash, flashIntensity * (1.0f - (float) distance / 0.5f));
                }
            }
        }

        return Math.min(1.0f, maxFlash);
    }
}
