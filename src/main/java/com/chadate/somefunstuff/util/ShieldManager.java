package com.chadate.somefunstuff.util;

import com.chadate.somefunstuff.capability.ShieldCapabilities;
import com.chadate.somefunstuff.capability.ShieldCapability;
import com.chadate.somefunstuff.network.ShieldDataSyncPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 护盾管理器工具类
 * 提供便捷的方法来操作玩家的护盾
 */
public class ShieldManager {
    
    /**
     * 激活玩家的护盾
     */
    public static void activateShield(Player player) {
        ShieldCapability shield = player.getData(ShieldCapabilities.SHIELD_ATTACHMENT);
        ShieldCapability newShield = shield.withActive(true);
        player.setData(ShieldCapabilities.SHIELD_ATTACHMENT, newShield);
        syncToClient(player, newShield);
    }
    
    /**
     * 停用玩家的护盾
     */
    public static void deactivateShield(Player player) {
        ShieldCapability shield = player.getData(ShieldCapabilities.SHIELD_ATTACHMENT);
        ShieldCapability newShield = shield.withActive(false);
        player.setData(ShieldCapabilities.SHIELD_ATTACHMENT, newShield);
        syncToClient(player, newShield);
    }
    
    /**
     * 切换玩家的护盾状态
     */
    public static void toggleShield(Player player) {
        ShieldCapability shield = player.getData(ShieldCapabilities.SHIELD_ATTACHMENT);
        ShieldCapability newShield = shield.withActive(!shield.isShieldActive());
        player.setData(ShieldCapabilities.SHIELD_ATTACHMENT, newShield);
        syncToClient(player, newShield);
    }
    
    /**
     * 设置护盾半径
     */
    public static void setShieldRadius(Player player, double radius) {
        ShieldCapability shield = player.getData(ShieldCapabilities.SHIELD_ATTACHMENT);
        ShieldCapability newShield = shield.withRadius(radius);
        player.setData(ShieldCapabilities.SHIELD_ATTACHMENT, newShield);
        syncToClient(player, newShield);
    }
    
    /**
     * 设置护盾强度
     */
    public static void setShieldStrength(Player player, int strength) {
        ShieldCapability shield = player.getData(ShieldCapabilities.SHIELD_ATTACHMENT);
        ShieldCapability newShield = shield.withStrength(strength);
        player.setData(ShieldCapabilities.SHIELD_ATTACHMENT, newShield);
        syncToClient(player, newShield);
    }
    
    /**
     * 获取护盾信息
     */
    public static String getShieldInfo(Player player) {
        ShieldCapability shield = player.getData(ShieldCapabilities.SHIELD_ATTACHMENT);
        return String.format("护盾状态: %s, 半径: %.1f, 强度: %d",
            shield.isShieldActive() ? "激活" : "未激活",
            shield.getShieldRadius(),
            shield.getShieldStrength());
    }
    
    /**
     * 检查玩家是否拥有激活的护盾
     */
    public static boolean hasActiveShield(Player player) {
        ShieldCapability shield = player.getData(ShieldCapabilities.SHIELD_ATTACHMENT);
        return shield.isShieldActive();
    }
    
    /**
     * 同步护盾数据到客户端
     */
    private static void syncToClient(Player player, ShieldCapability shield) {
        if (player instanceof ServerPlayer serverPlayer) {
            ShieldDataSyncPacket packet = new ShieldDataSyncPacket(
                shield.active(),
                shield.radius(),
                shield.strength()
            );
            PacketDistributor.sendToPlayer(serverPlayer, packet);
        }
    }
}
