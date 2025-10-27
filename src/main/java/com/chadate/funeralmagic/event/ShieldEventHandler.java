package com.chadate.funeralmagic.event;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.chadate.funeralmagic.SomeFunStuff;
import com.chadate.funeralmagic.capability.ShieldCapabilities;
import com.chadate.funeralmagic.capability.ShieldCapability;
import com.chadate.funeralmagic.network.ShieldDataSyncPacket;
import com.chadate.funeralmagic.network.ShieldImpactPacket;

/**
 * 护盾事件处理器
 * 负责拦截弹射物
 */
@EventBusSubscriber(modid = SomeFunStuff.MODID)
public class ShieldEventHandler {

    /**
     * 存储弹射物的上一次位置，用于路径检测
     * Key: 弹射物UUID, Value: 上一次的位置
     */
    private static final Map<UUID, Vec3> projectileLastPositions = new HashMap<>();

    /**
     * 存储已经被拦截过的弹射物，避免在同一个tick内重复拦截
     * Key: 弹射物UUID, Value: 拦截时的游戏时间
     */
    private static final Map<UUID, Long> deflectedProjectiles = new HashMap<>();

    /**
     * 清理计数器，每N个tick清理一次历史数据
     */
    private static int cleanupCounter = 0;
    private static final int CLEANUP_INTERVAL = 100; // 每100个tick清理一次

    /**
     * 玩家登录时同步所有实体的护盾数据到客户端
     * 这样玩家登录后可以立即看到世界中所有实体的护盾状态
     */
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer serverPlayer)) {
            return;
        }

        for (var entity : serverPlayer.serverLevel().getAllEntities()) {
            ShieldCapability shield = entity.getData(ShieldCapabilities.SHIELD_ATTACHMENT);
            if (shield != null && shield.isShieldActive()) {
                // 向登录的玩家发送该实体的护盾数据
                ShieldDataSyncPacket packet = new ShieldDataSyncPacket(
                        entity.getId(),
                        shield.isShieldActive(),
                        shield.getShieldRadius(),
                        shield.getShieldStrength());
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(serverPlayer, packet);
            }
        }

    }

    /**
     * 玩家切换维度时同步所有实体的护盾数据到客户端
     * 这样玩家切换维度后可以立即看到新维度中所有实体的护盾状态
     */
    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer serverPlayer)) {
            return;
        }

        for (var entity : serverPlayer.serverLevel().getAllEntities()) {
            ShieldCapability shield = entity.getData(ShieldCapabilities.SHIELD_ATTACHMENT);
            if (shield != null && shield.isShieldActive()) {
                // 向切换维度的玩家发送该实体的护盾数据
                ShieldDataSyncPacket packet = new ShieldDataSyncPacket(
                        entity.getId(),
                        shield.isShieldActive(),
                        shield.getShieldRadius(),
                        shield.getShieldStrength());
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(serverPlayer, packet);
            }
        }

    }

    /**
     * 监听弹射物自身的Tick事件（在它移动之前）
     * 这是最可靠的拦截时机
     */
    @SubscribeEvent
    public static void onProjectileTick(EntityTickEvent.Pre event) {
        // 只处理弹射物
        if (!(event.getEntity() instanceof Projectile projectile)) {
            return;
        }

        // 只在服务端执行
        if (projectile.level().isClientSide) {
            return;
        }

        // 检查弹射物附近是否有护盾
        checkNearbyShields(projectile);

        // 定期清理历史数据
        cleanupCounter++;
        if (cleanupCounter >= CLEANUP_INTERVAL) {
            cleanupHistoricalData(projectile.level());
            cleanupCounter = 0;
        }
    }

    /**
     * 检查弹射物附近是否有护盾，并尝试拦截
     * 这个方法在弹射物tick时被调用，在它移动之前就能检测到
     */
    private static void checkNearbyShields(Projectile projectile) {
        UUID projId = projectile.getUUID();
        long currentTime = projectile.level().getGameTime();

        // 检查是否在同一个tick内已经被拦截过
        Long lastDeflectTime = deflectedProjectiles.get(projId);
        if (lastDeflectTime != null && lastDeflectTime == currentTime) {
            return; // 已经在这个tick拦截过了，跳过
        }

        Vec3 projectilePos = projectile.position();
        Vec3 velocity = projectile.getDeltaMovement();
        double speed = velocity.length();

        // 根据弹射物速度动态调整搜索范围（更快的弹射物需要更大的搜索范围）
        double searchRadius = Math.max(10.0, speed * 2.0 + 5.0);

        // 搜索附近的所有实体
        AABB searchBox = new AABB(
                projectilePos.x - searchRadius, projectilePos.y - searchRadius, projectilePos.z - searchRadius,
                projectilePos.x + searchRadius, projectilePos.y + searchRadius, projectilePos.z + searchRadius);

        List<Entity> nearbyEntities = projectile.level().getEntities(
                projectile,
                searchBox,
                entity -> {
                    // 过滤掉自己的主人
                    if (entity == projectile.getOwner()) {
                        return false;
                    }
                    // 只关注有护盾的实体
                    ShieldCapability shield = entity.getData(ShieldCapabilities.SHIELD_ATTACHMENT);
                    return shield != null && shield.isShieldActive();
                });

        // 对每个有护盾的实体进行检测
        for (Entity entity : nearbyEntities) {
            ShieldCapability shield = entity.getData(ShieldCapabilities.SHIELD_ATTACHMENT);
            if (shield == null || !shield.isShieldActive()) {
                continue;
            }

            Vec3 shieldCenter = entity.position().add(0, entity.getEyeHeight() / 2, 0);
            double radius = shield.getShieldRadius();

            // 检查弹射物是否会与该护盾相交
            if (willProjectileHitShield(projectile, projectilePos, velocity, shieldCenter, radius)) {
                deflectProjectile(projectile, entity, shield);
                deflectedProjectiles.put(projId, currentTime);
                return; // 已经被一个护盾拦截，不需要继续检查
            }
        }

        // 更新弹射物位置记录
        projectileLastPositions.put(projId, projectilePos);
    }

    /**
     * 判断弹射物是否会命中护盾
     * 综合使用多种检测方法
     */
    private static boolean willProjectileHitShield(Projectile projectile, Vec3 currentPos,
            Vec3 velocity, Vec3 shieldCenter, double radius) {
        // 方法1：检查当前位置
        double currentDistance = currentPos.distanceTo(shieldCenter);
        if (currentDistance <= radius) {
            return true;
        }

        // 方法2：检查下一帧位置（预测）
        if (velocity.lengthSqr() > 0.001) {
            Vec3 nextPos = currentPos.add(velocity);
            double nextDistance = nextPos.distanceTo(shieldCenter);
            if (nextDistance <= radius) {
                return true;
            }

            // 方法3：检查路径是否穿过护盾
            if (doesPathIntersectShield(currentPos, nextPos, shieldCenter, radius)) {
                return true;
            }

            // 方法4：子刺度检测（将下一帧分成10个步骤检查）
            for (int i = 1; i <= 10; i++) {
                double fraction = (double) i / 10.0;
                Vec3 interpolatedPos = currentPos.add(velocity.scale(fraction));
                double distance = interpolatedPos.distanceTo(shieldCenter);
                if (distance <= radius) {
                    return true;
                }
            }
        }

        // 方法5：检查历史路径
        Vec3 lastPos = projectileLastPositions.get(projectile.getUUID());
        if (lastPos != null && doesPathIntersectShield(lastPos, currentPos, shieldCenter, radius)) {
            return true;
        }

        return false;
    }

    /**
     * 检查线段路径是否与球体（护盾）相交
     * 使用射线-球体相交算法
     * 
     * @param start  路径起点（弹射物上一次位置）
     * @param end    路径终点（弹射物当前位置）
     * @param center 球体中心（护盾中心）
     * @param radius 球体半径（护盾半径）
     * @return 是否相交
     */
    private static boolean doesPathIntersectShield(Vec3 start, Vec3 end, Vec3 center, double radius) {
        // 线段方向向量
        Vec3 d = end.subtract(start);
        double segmentLength = d.length();

        // 线段太短，忽略
        if (segmentLength < 0.001) {
            return start.distanceTo(center) <= radius;
        }

        // 从球心到线段起点的向量
        Vec3 f = start.subtract(center);

        // 二次方程系数: at^2 + bt + c = 0
        double a = d.dot(d);
        double b = 2 * f.dot(d);
        double c = f.dot(f) - radius * radius;

        double discriminant = b * b - 4 * a * c;

        // 无交点
        if (discriminant < 0) {
            return false;
        }

        // 计算交点参数 t (t ∈ [0, 1] 表示交点在线段上)
        discriminant = Math.sqrt(discriminant);
        double t1 = (-b - discriminant) / (2 * a);
        double t2 = (-b + discriminant) / (2 * a);

        // 检查是否有交点在线段范围内 [0, 1]
        // 或者线段完全在球体内部 (t1 < 0 && t2 > 1)
        return (t1 >= 0 && t1 <= 1) || (t2 >= 0 && t2 <= 1) || (t1 < 0 && t2 > 1);
    }

    /**
     * 清理已经不存在的弹射物的历史数据
     * 使用简单的策略：定期清理过期数据或限制数据量
     */
    private static void cleanupHistoricalData(net.minecraft.world.level.Level level) {
        long currentTime = level.getGameTime();

        // 清理拦截记录（保留最近100个tick的记录）
        deflectedProjectiles.entrySet().removeIf(entry -> currentTime - entry.getValue() > 100);

        // 如果位置记录超过200个，清空一半（保留最近的一半）
        if (projectileLastPositions.size() > 200) {
            int toRemove = projectileLastPositions.size() / 2;
            projectileLastPositions.keySet().stream()
                    .limit(toRemove)
                    .toList()
                    .forEach(projectileLastPositions::remove);
        }

    }

    /**
     * 偏转弹射物
     * 支持所有实体类型
     */
    private static void deflectProjectile(Projectile projectile, Entity entity, ShieldCapability shieldCap) {
        // 计算反射方向（从实体到弹射物的方向）
        Vec3 shieldCenter = entity.position().add(0, entity.getEyeHeight() / 2, 0);
        Vec3 projectilePos = projectile.position();
        Vec3 deflectDirection = projectilePos.subtract(shieldCenter).normalize();

        // 计算弹射物与护盾表面的交点（准确的击中位置）
        double shieldRadius = shieldCap.getShieldRadius();
        Vec3 impactPoint = shieldCenter.add(deflectDirection.scale(shieldRadius));

        // 设置弹射物的新速度（反射）
        double speed = projectile.getDeltaMovement().length();
        Vec3 newVelocity = deflectDirection.scale(speed * 0.8);
        projectile.setDeltaMovement(newVelocity);

        // 改变弹射物的所有者，避免伤害实体
        if (projectile.getOwner() != entity) {
            projectile.setOwner(entity);
        }

        // 消耗护盾强度
        if (shieldCap.consumeStrength(1)) {
            ShieldCapability newShield = shieldCap.withConsumedStrength(1);
            entity.setData(ShieldCapabilities.SHIELD_ATTACHMENT, newShield);

            // 同步到所有客户端
            ShieldDataSyncPacket packet = new ShieldDataSyncPacket(
                    entity.getId(),
                    newShield.active(),
                    newShield.radius(),
                    newShield.strength());
            net.neoforged.neoforge.network.PacketDistributor.sendToAllPlayers(packet);
        }

        // 发送击中效果包到所有玩家（包含实体ID）
        ShieldImpactPacket impactPacket = new ShieldImpactPacket(entity.getId(), impactPoint, shieldCenter);
        net.neoforged.neoforge.network.PacketDistributor.sendToAllPlayers(impactPacket);

        // 播放世界音效（所有附近的玩家都能听到）
        entity.level().playSound(
                null, // 不指定玩家，让所有附近的人都能听到
                impactPoint.x, // 音效位置：击中点
                impactPoint.y,
                impactPoint.z,
                SoundEvents.RESPAWN_ANCHOR_DEPLETE, // 音效：能量护盾被击中的声音
                SoundSource.BLOCKS, // 音效类型
                0.8f, // 音量
                1.2f + (float) (Math.random() * 0.2f) // 音调（随机变化，更有动态感）
        );

    }
}
