package com.chadate.somefunstuff.event;

import com.chadate.somefunstuff.SomeFunStuff;
import com.chadate.somefunstuff.capability.ShieldCapabilities;
import com.chadate.somefunstuff.capability.ShieldCapability;
import com.chadate.somefunstuff.network.ShieldDataSyncPacket;
import com.chadate.somefunstuff.network.ShieldImpactPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.List;

/**
 * 护盾事件处理器
 * 负责拦截弹射物
 */
@EventBusSubscriber(modid = SomeFunStuff.MODID)
public class ShieldEventHandler {

    /**
     * 玩家登录时同步护盾数据到客户端
     */
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer serverPlayer)) {
            return;
        }
        ShieldCapability shield = serverPlayer.getData(ShieldCapabilities.SHIELD_ATTACHMENT);
        if (shield == null) {
            return;
        }
        ShieldDataSyncPacket packet = new ShieldDataSyncPacket(
            serverPlayer.getId(),
            shield.isShieldActive(),
            shield.getShieldRadius(),
            shield.getShieldStrength()
        );
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(serverPlayer, packet);
        SomeFunStuff.LOGGER.info("[ShieldSync] 登录同步护盾: active={}, radius={}, strength={}",
            shield.isShieldActive(), shield.getShieldRadius(), shield.getShieldStrength());
    }

    /**
     * 玩家切换维度时同步护盾数据到客户端
     */
    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer serverPlayer)) {
            return;
        }
        ShieldCapability shield = serverPlayer.getData(ShieldCapabilities.SHIELD_ATTACHMENT);
        if (shield == null) {
            return;
        }
        ShieldDataSyncPacket packet = new ShieldDataSyncPacket(
            serverPlayer.getId(),
            shield.isShieldActive(),
            shield.getShieldRadius(),
            shield.getShieldStrength()
        );
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(serverPlayer, packet);
        SomeFunStuff.LOGGER.info("[ShieldSync] 维度切换同步护盾: active={}, radius={}, strength={}",
            shield.isShieldActive(), shield.getShieldRadius(), shield.getShieldStrength());
    }

    /**
     * 在每个实体Tick主动扫描护盾范围内的弹射物
     * 支持所有实体类型
     */
    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Pre event) {
        var entity = event.getEntity();
        
        // 只在服务端执行
        if (entity.level().isClientSide) {
            return;
        }

        // 检查实体是否有激活的护盾
        ShieldCapability shield = entity.getData(ShieldCapabilities.SHIELD_ATTACHMENT);
        if (shield == null || !shield.isShieldActive()) {
            return;
        }

        // 护盾中心点（实体胸部高度，与渲染一致）
        Vec3 shieldCenter = entity.position().add(0, entity.getEyeHeight() / 2, 0);
        double radius = shield.getShieldRadius();

        // 创建护盾范围的检测盒
        AABB searchBox = new AABB(
                shieldCenter.x - radius, shieldCenter.y - radius, shieldCenter.z - radius,
                shieldCenter.x + radius, shieldCenter.y + radius, shieldCenter.z + radius);

        // 扫描范围内的所有弹射物
        List<Projectile> projectiles = entity.level().getEntitiesOfClass(
                Projectile.class,
                searchBox,
                projectile -> {
                    // 过滤掉实体自己的弹射物
                    if (projectile.getOwner() == entity) {
                        return false;
                    }

                    // 检查是否在护盾半径内
                    double distance = projectile.position().distanceTo(shieldCenter);
                    return distance <= radius;
                });

        // 拦截并反弹所有弹射物
        for (Projectile projectile : projectiles) {
            deflectProjectile(projectile, entity, shield);
        }
    }

    /**
     * 偏转弹射物
     * 支持所有实体类型
     */
    private static void deflectProjectile(Projectile projectile, net.minecraft.world.entity.Entity entity, ShieldCapability shieldCap) {
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

        // 调试日志
        SomeFunStuff.LOGGER.info("[ShieldImpact] 实体 {} 护盾反弹弹射物: {}, 护盾中心: {}, 护盾半径: {}",
                entity.getName().getString(), impactPoint, shieldCenter, shieldRadius);
    }
}
