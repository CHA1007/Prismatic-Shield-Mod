package com.chadate.funeralmagic.command;

import com.chadate.funeralmagic.capability.ShieldCapabilities;
import com.chadate.funeralmagic.capability.ShieldCapability;
import com.chadate.funeralmagic.network.ShieldDataSyncPacket;
import com.chadate.funeralmagic.util.ShieldManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Collection;

/**
 * 护盾命令
 * 提供游戏内命令来控制护盾
 */
public class ShieldCommand {
    
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("shield")
            .requires(source -> source.hasPermission(2)) // 需要管理员权限
            // 给实体添加护盾（简化版，支持默认值）
            .then(Commands.literal("give")
                .then(Commands.argument("targets", EntityArgument.entities())
                    .executes(ShieldCommand::giveShieldDefault) // 默认值：半径3.0，强度100
                    .then(Commands.argument("radius", DoubleArgumentType.doubleArg(0.5, 20.0))
                        .executes(ShieldCommand::giveShieldWithRadius) // 自定义半径，强度100
                        .then(Commands.argument("strength", IntegerArgumentType.integer(1, 10000))
                            .executes(ShieldCommand::giveShield))))) // 完全自定义
            // 移除实体护盾
            .then(Commands.literal("remove")
                .then(Commands.argument("targets", EntityArgument.entities())
                    .executes(ShieldCommand::removeShield)))
            // 自己的护盾控制（简化为一个命令）
            .then(Commands.literal("toggle")
                .executes(ShieldCommand::toggleShield))
        );
    }
    
    private static int toggleShield(CommandContext<CommandSourceStack> context) {
        if (!(context.getSource().getEntity() instanceof ServerPlayer player)) {
            context.getSource().sendFailure(Component.literal("只有玩家可以使用此命令"));
            return 0;
        }
        
        ShieldManager.toggleShield(player);
        boolean active = ShieldManager.hasActiveShield(player);
        context.getSource().sendSuccess(
            () -> Component.literal("护盾已" + (active ? "激活" : "关闭")),
            true
        );
        return 1;
    }
    
    /**
     * 给指定实体添加护盾（使用默认值）
     * 默认半径：3.0，默认强度：100
     */
    private static int giveShieldDefault(CommandContext<CommandSourceStack> context) {
        return giveShieldInternal(context, 3.0, 100);
    }
    
    /**
     * 给指定实体添加护盾（自定义半径）
     * 默认强度：100
     */
    private static int giveShieldWithRadius(CommandContext<CommandSourceStack> context) {
        double radius = DoubleArgumentType.getDouble(context, "radius");
        return giveShieldInternal(context, radius, 100);
    }
    
    /**
     * 给指定实体添加护盾（完全自定义）
     */
    private static int giveShield(CommandContext<CommandSourceStack> context) {
        double radius = DoubleArgumentType.getDouble(context, "radius");
        int strength = IntegerArgumentType.getInteger(context, "strength");
        return giveShieldInternal(context, radius, strength);
    }
    
    /**
     * 内部方法：给指定实体添加护盾
     */
    private static int giveShieldInternal(CommandContext<CommandSourceStack> context, double radius, int strength) {
        try {
            Collection<? extends Entity> targets = EntityArgument.getEntities(context, "targets");
            
            int successCount = 0;
            for (Entity entity : targets) {
                // 创建新的护盾数据
                ShieldCapability newShield = new ShieldCapability(true, radius, strength);
                entity.setData(ShieldCapabilities.SHIELD_ATTACHMENT, newShield);
                
                // 同步到所有客户端（让所有玩家都能看到这个实体的护盾）
                ShieldDataSyncPacket packet = new ShieldDataSyncPacket(entity.getId(), true, radius, strength);
                PacketDistributor.sendToAllPlayers(packet);
                
                successCount++;
            }
            
            final int count = successCount;
            context.getSource().sendSuccess(
                () -> Component.literal("已为 " + count + " 个实体添加护盾（半径: " + 
                    String.format("%.1f", radius) + ", 强度: " + strength + ")"),
                true
            );
            return successCount;
            
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("命令执行失败: " + e.getMessage()));
            return 0;
        }
    }
    
    /**
     * 移除指定实体的护盾
     */
    private static int removeShield(CommandContext<CommandSourceStack> context) {
        try {
            Collection<? extends Entity> targets = EntityArgument.getEntities(context, "targets");
            
            int successCount = 0;
            for (Entity entity : targets) {
                ShieldCapability currentShield = entity.getData(ShieldCapabilities.SHIELD_ATTACHMENT);
                
                if (currentShield != null && currentShield.isShieldActive()) {
                    // 关闭护盾
                    ShieldCapability newShield = new ShieldCapability(
                        false, 
                        currentShield.getShieldRadius(), 
                        currentShield.getShieldStrength()
                    );
                    entity.setData(ShieldCapabilities.SHIELD_ATTACHMENT, newShield);
                    
                    // 同步到所有客户端（让所有玩家都能看到护盾被移除）
                    ShieldDataSyncPacket packet = new ShieldDataSyncPacket(
                        entity.getId(),
                        false, 
                        currentShield.getShieldRadius(), 
                        currentShield.getShieldStrength()
                    );
                    PacketDistributor.sendToAllPlayers(packet);
                    
                    successCount++;
                }
            }
            
            final int count = successCount;
            context.getSource().sendSuccess(
                () -> Component.literal("已移除 " + count + " 个实体的护盾"),
                true
            );
            return successCount;
            
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("命令执行失败: " + e.getMessage()));
            return 0;
        }
    }
}
