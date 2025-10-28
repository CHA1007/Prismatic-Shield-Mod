package com.chadate.funeralmagic.api;

import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;

import com.chadate.funeralmagic.capability.ShieldCapabilities;
import com.chadate.funeralmagic.capability.ShieldCapability;
import com.chadate.funeralmagic.network.ShieldDataSyncPacket;

/**
 * 护盾系统公开API
 * 
 * 提供给其他MOD调用的护盾功能接口。
 * 所有方法都是线程安全的，可以在服务器端安全调用。
 * 
 * 使用示例：
 * <pre>{@code
 * // 给实体添加护盾
 * ShieldAPI.giveShield(entity, 5.0, 200);
 * 
 * // 检查实体是否有激活的护盾
 * if (ShieldAPI.hasActiveShield(entity)) {
 *     // 消耗护盾强度
 *     ShieldAPI.consumeShieldStrength(entity, 10);
 * }
 * 
 * // 获取护盾信息
 * ShieldInfo info = ShieldAPI.getShieldInfo(entity);
 * if (info != null && info.isActive()) {
 *     System.out.println("护盾半径: " + info.getRadius());
 * }
 * }</pre>
 * 
 * @author SomeFunStuff Team
 * @version 1.0
 */
public class ShieldAPI {

    /**
     * 检查实体是否拥有激活的护盾
     * 
     * @param entity 要检查的实体
     * @return 如果实体有激活的护盾返回true，否则返回false
     */
    public static boolean hasActiveShield(Entity entity) {
        if (entity == null) {
            return false;
        }
        ShieldCapability shield = entity.getData(ShieldCapabilities.SHIELD_ATTACHMENT);
        return shield != null && shield.isShieldActive();
    }

    /**
     * 检查实体是否拥有护盾数据（无论是否激活）
     * 
     * @param entity 要检查的实体
     * @return 如果实体有护盾数据返回true，否则返回false
     */
    public static boolean hasShield(Entity entity) {
        if (entity == null) {
            return false;
        }
        ShieldCapability shield = entity.getData(ShieldCapabilities.SHIELD_ATTACHMENT);
        return shield != null;
    }

    /**
     * 获取实体的护盾信息
     * 
     * @param entity 要查询的实体
     * @return 护盾信息对象，如果实体没有护盾则返回null
     */
    @Nullable
    public static ShieldInfo getShieldInfo(Entity entity) {
        if (entity == null) {
            return null;
        }
        ShieldCapability shield = entity.getData(ShieldCapabilities.SHIELD_ATTACHMENT);
        if (shield == null) {
            return null;
        }
        return new ShieldInfo(shield.isShieldActive(), shield.radius(), shield.strength());
    }

    /**
     * 给实体添加护盾
     * 
     * 如果实体已经有护盾，会覆盖原有的护盾数据。
     * 在多人服务器上会自动同步给所有玩家。
     * 
     * @param entity 目标实体
     * @param radius 护盾半径（建议范围：0.5 ~ 20.0）
     * @param strength 护盾强度（建议范围：1 ~ 10000）
     * @return 操作是否成功
     */
    public static boolean giveShield(Entity entity, double radius, int strength) {
        return giveShield(entity, radius, strength, true);
    }

    /**
     * 给实体添加护盾（可指定是否激活）
     * 
     * @param entity 目标实体
     * @param radius 护盾半径
     * @param strength 护盾强度
     * @param active 是否立即激活
     * @return 操作是否成功
     */
    public static boolean giveShield(Entity entity, double radius, int strength, boolean active) {
        if (entity == null || entity.level().isClientSide) {
            return false;
        }
        
        try {
            ShieldCapability newShield = new ShieldCapability(active, radius, strength);
            entity.setData(ShieldCapabilities.SHIELD_ATTACHMENT, newShield);
            syncToAllPlayers(entity, newShield);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 移除实体的护盾
     * 
     * 注意：这会将护盾设为未激活状态，但不会删除护盾数据。
     * 
     * @param entity 目标实体
     * @return 操作是否成功
     */
    public static boolean removeShield(Entity entity) {
        return setShieldActive(entity, false);
    }

    /**
     * 激活实体的护盾
     * 
     * @param entity 目标实体
     * @return 操作是否成功
     */
    public static boolean activateShield(Entity entity) {
        return setShieldActive(entity, true);
    }

    /**
     * 停用实体的护盾
     * 
     * @param entity 目标实体
     * @return 操作是否成功
     */
    public static boolean deactivateShield(Entity entity) {
        return setShieldActive(entity, false);
    }

    /**
     * 设置护盾激活状态
     * 
     * @param entity 目标实体
     * @param active 是否激活
     * @return 操作是否成功
     */
    public static boolean setShieldActive(Entity entity, boolean active) {
        if (entity == null || entity.level().isClientSide) {
            return false;
        }
        
        ShieldCapability shield = entity.getData(ShieldCapabilities.SHIELD_ATTACHMENT);
        if (shield == null) {
            return false;
        }
        
        try {
            ShieldCapability newShield = shield.withActive(active);
            entity.setData(ShieldCapabilities.SHIELD_ATTACHMENT, newShield);
            syncToAllPlayers(entity, newShield);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 设置护盾半径
     * 
     * @param entity 目标实体
     * @param radius 新的半径值（建议范围：0.5 ~ 20.0）
     * @return 操作是否成功
     */
    public static boolean setShieldRadius(Entity entity, double radius) {
        if (entity == null || entity.level().isClientSide) {
            return false;
        }
        
        ShieldCapability shield = entity.getData(ShieldCapabilities.SHIELD_ATTACHMENT);
        if (shield == null) {
            return false;
        }
        
        try {
            ShieldCapability newShield = shield.withRadius(radius);
            entity.setData(ShieldCapabilities.SHIELD_ATTACHMENT, newShield);
            syncToAllPlayers(entity, newShield);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 设置护盾强度
     * 
     * @param entity 目标实体
     * @param strength 新的强度值（建议范围：1 ~ 10000）
     * @return 操作是否成功
     */
    public static boolean setShieldStrength(Entity entity, int strength) {
        if (entity == null || entity.level().isClientSide) {
            return false;
        }
        
        ShieldCapability shield = entity.getData(ShieldCapabilities.SHIELD_ATTACHMENT);
        if (shield == null) {
            return false;
        }
        
        try {
            ShieldCapability newShield = shield.withStrength(strength);
            entity.setData(ShieldCapabilities.SHIELD_ATTACHMENT, newShield);
            syncToAllPlayers(entity, newShield);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 消耗护盾强度
     * 
     * 如果强度降为0，护盾会自动关闭。
     * 
     * @param entity 目标实体
     * @param amount 消耗的强度值
     * @return 操作是否成功
     */
    public static boolean consumeShieldStrength(Entity entity, int amount) {
        if (entity == null || entity.level().isClientSide || amount <= 0) {
            return false;
        }
        
        ShieldCapability shield = entity.getData(ShieldCapabilities.SHIELD_ATTACHMENT);
        if (shield == null || !shield.isShieldActive()) {
            return false;
        }
        
        try {
            if (shield.canConsumeStrength(amount)) {
                ShieldCapability newShield = shield.consumeStrength(amount);
                entity.setData(ShieldCapabilities.SHIELD_ATTACHMENT, newShield);
                syncToAllPlayers(entity, newShield);
                return true;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 恢复护盾强度
     * 
     * @param entity 目标实体
     * @param amount 恢复的强度值
     * @return 操作是否成功
     */
    public static boolean restoreShieldStrength(Entity entity, int amount) {
        if (entity == null || entity.level().isClientSide || amount <= 0) {
            return false;
        }
        
        ShieldCapability shield = entity.getData(ShieldCapabilities.SHIELD_ATTACHMENT);
        if (shield == null) {
            return false;
        }
        
        try {
            int newStrength = Math.min(10000, shield.strength() + amount); // 上限10000
            ShieldCapability newShield = shield.withStrength(newStrength);
            entity.setData(ShieldCapabilities.SHIELD_ATTACHMENT, newShield);
            syncToAllPlayers(entity, newShield);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 切换护盾激活状态
     * 
     * @param entity 目标实体
     * @return 操作后的护盾状态（true=激活，false=未激活）
     */
    public static boolean toggleShield(Entity entity) {
        if (entity == null || entity.level().isClientSide) {
            return false;
        }
        
        ShieldCapability shield = entity.getData(ShieldCapabilities.SHIELD_ATTACHMENT);
        if (shield == null) {
            return false;
        }
        
        boolean newState = !shield.isShieldActive();
        setShieldActive(entity, newState);
        return newState;
    }

    /**
     * 获取护盾半径
     * 
     * @param entity 目标实体
     * @return 护盾半径，如果没有护盾返回0.0
     */
    public static double getShieldRadius(Entity entity) {
        if (entity == null) {
            return 0.0;
        }
        ShieldCapability shield = entity.getData(ShieldCapabilities.SHIELD_ATTACHMENT);
        return shield != null ? shield.radius() : 0.0;
    }

    /**
     * 获取护盾强度
     * 
     * @param entity 目标实体
     * @return 护盾强度，如果没有护盾返回0
     */
    public static int getShieldStrength(Entity entity) {
        if (entity == null) {
            return 0;
        }
        ShieldCapability shield = entity.getData(ShieldCapabilities.SHIELD_ATTACHMENT);
        return shield != null ? shield.strength() : 0;
    }

    /**
     * 同步护盾数据到所有客户端
     * 内部方法，自动在修改护盾时调用
     */
    private static void syncToAllPlayers(Entity entity, ShieldCapability shield) {
        if (entity.level().isClientSide) {
            return;
        }
        
        ShieldDataSyncPacket packet = new ShieldDataSyncPacket(
            entity.getId(),
            shield.active(),
            shield.radius(),
            shield.strength()
        );
        PacketDistributor.sendToAllPlayers(packet);
    }

    /**
     * 护盾信息数据类
     * 
     * 提供只读的护盾信息访问
     */
    public static class ShieldInfo {
        private final boolean active;
        private final double radius;
        private final int strength;

        public ShieldInfo(boolean active, double radius, int strength) {
            this.active = active;
            this.radius = radius;
            this.strength = strength;
        }

        /**
         * 护盾是否激活
         */
        public boolean isActive() {
            return active;
        }

        /**
         * 获取护盾半径
         */
        public double getRadius() {
            return radius;
        }

        /**
         * 获取护盾强度
         */
        public int getStrength() {
            return strength;
        }

        /**
         * 护盾是否已耗尽
         */
        public boolean isDepleted() {
            return strength <= 0;
        }

        /**
         * 获取护盾强度百分比
         * 
         * @param maxStrength 最大强度（用于计算百分比）
         * @return 0.0 到 1.0 之间的值
         */
        public double getStrengthPercentage(int maxStrength) {
            if (maxStrength <= 0) {
                return 0.0;
            }
            return Math.min(1.0, (double) strength / maxStrength);
        }

        @Override
        public String toString() {
            return String.format("ShieldInfo{active=%s, radius=%.1f, strength=%d}", 
                active, radius, strength);
        }
    }
}
