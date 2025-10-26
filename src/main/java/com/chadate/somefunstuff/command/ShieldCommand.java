package com.chadate.somefunstuff.command;

import com.chadate.somefunstuff.util.ShieldManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * 护盾命令
 * 提供游戏内命令来控制护盾
 */
public class ShieldCommand {
    
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("shield")
            .requires(source -> source.hasPermission(2)) // 需要管理员权限
            .then(Commands.literal("toggle")
                .executes(ShieldCommand::toggleShield))
            .then(Commands.literal("on")
                .executes(ShieldCommand::activateShield))
            .then(Commands.literal("off")
                .executes(ShieldCommand::deactivateShield))
            .then(Commands.literal("radius")
                .then(Commands.argument("value", DoubleArgumentType.doubleArg(0.5, 20.0))
                    .executes(ShieldCommand::setRadius)))
            .then(Commands.literal("strength")
                .then(Commands.argument("value", IntegerArgumentType.integer(0, 10000))
                    .executes(ShieldCommand::setStrength)))
            .then(Commands.literal("info")
                .executes(ShieldCommand::showInfo))
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
    
    private static int activateShield(CommandContext<CommandSourceStack> context) {
        if (!(context.getSource().getEntity() instanceof ServerPlayer player)) {
            context.getSource().sendFailure(Component.literal("只有玩家可以使用此命令"));
            return 0;
        }
        
        ShieldManager.activateShield(player);
        context.getSource().sendSuccess(
            () -> Component.literal("护盾已激活"),
            true
        );
        return 1;
    }
    
    private static int deactivateShield(CommandContext<CommandSourceStack> context) {
        if (!(context.getSource().getEntity() instanceof ServerPlayer player)) {
            context.getSource().sendFailure(Component.literal("只有玩家可以使用此命令"));
            return 0;
        }
        
        ShieldManager.deactivateShield(player);
        context.getSource().sendSuccess(
            () -> Component.literal("护盾已关闭"),
            true
        );
        return 1;
    }
    
    private static int setRadius(CommandContext<CommandSourceStack> context) {
        if (!(context.getSource().getEntity() instanceof ServerPlayer player)) {
            context.getSource().sendFailure(Component.literal("只有玩家可以使用此命令"));
            return 0;
        }
        
        double radius = DoubleArgumentType.getDouble(context, "value");
        ShieldManager.setShieldRadius(player, radius);
        context.getSource().sendSuccess(
            () -> Component.literal("护盾半径已设置为 " + String.format("%.1f", radius) + " 格"),
            true
        );
        return 1;
    }
    
    private static int setStrength(CommandContext<CommandSourceStack> context) {
        if (!(context.getSource().getEntity() instanceof ServerPlayer player)) {
            context.getSource().sendFailure(Component.literal("只有玩家可以使用此命令"));
            return 0;
        }
        
        int strength = IntegerArgumentType.getInteger(context, "value");
        ShieldManager.setShieldStrength(player, strength);
        context.getSource().sendSuccess(
            () -> Component.literal("护盾强度已设置为 " + strength),
            true
        );
        return 1;
    }
    
    private static int showInfo(CommandContext<CommandSourceStack> context) {
        if (!(context.getSource().getEntity() instanceof ServerPlayer player)) {
            context.getSource().sendFailure(Component.literal("只有玩家可以使用此命令"));
            return 0;
        }
        
        String info = ShieldManager.getShieldInfo(player);
        context.getSource().sendSuccess(
            () -> Component.literal(info),
            false
        );
        return 1;
    }
}
