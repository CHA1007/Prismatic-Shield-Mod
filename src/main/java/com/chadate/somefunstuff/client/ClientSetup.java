package com.chadate.somefunstuff.client;

import com.chadate.somefunstuff.SomeFunStuff;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

/**
 * 客户端初始化
 * 只在客户端执行的初始化代码
 */
@EventBusSubscriber(modid = SomeFunStuff.MODID, value = Dist.CLIENT)
public class ClientSetup {
    
    private static boolean hasCheckedData = false;
    
    /**
     * 客户端设置事件
     */
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        // 注册顶级护盾渲染器到 NeoForge 事件总线
        NeoForge.EVENT_BUS.register(AdvancedShieldRenderer.class);
        AdvancedShieldRenderer.register();
        
        // 注册测试监听器
        NeoForge.EVENT_BUS.addListener(ClientSetup::onClientTick);

        // 客户端网络事件：登录/登出时清空受击缓存
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingIn e) -> {
            com.chadate.somefunstuff.client.render.ShieldImpactEffect.clear();
            SomeFunStuff.LOGGER.info("[ShieldSync][Client] LoggingIn -> 清空受击缓存");
        });
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> {
            com.chadate.somefunstuff.client.render.ShieldImpactEffect.clear();
            SomeFunStuff.LOGGER.info("[ShieldSync][Client] LoggingOut -> 清空受击缓存");
        });
        // 客户端关卡加载（进入世界/切换维度后）清空一次
        NeoForge.EVENT_BUS.addListener((LevelEvent.Load e) -> {
            if (e.getLevel().isClientSide()) {
                com.chadate.somefunstuff.client.render.ShieldImpactEffect.clear();
                SomeFunStuff.LOGGER.info("[ShieldSync][Client] LevelLoad -> 清空受击缓存");
            }
        });
        
        SomeFunStuff.LOGGER.info("[ClientSetup] 护盾渲染系统已初始化 - 使用顶级渲染模式");
    }
    
    /**
     * 测试客户端是否能读取护盾数据
     */
    public static void onClientTick(ClientTickEvent.Post event) {
        if (hasCheckedData) {
            return;
        }
        
        var mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        
        hasCheckedData = true;
    }
}
