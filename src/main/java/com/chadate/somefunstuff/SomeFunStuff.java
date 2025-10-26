package com.chadate.somefunstuff;

import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.slf4j.Logger;

import com.chadate.somefunstuff.capability.ShieldCapabilities;
import com.chadate.somefunstuff.command.ShieldCommand;
import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

@Mod(SomeFunStuff.MODID)
public class SomeFunStuff {

    public static final String MODID = "somefunstuff";

    public static final Logger LOGGER = LogUtils.getLogger();
  
    public SomeFunStuff(IEventBus modEventBus) {
        LOGGER.info("SomeFunStuff 正在初始化护盾系统...");
        
        // 注册护盾附件类型
        ShieldCapabilities.register(modEventBus);
        
        // 注册命令
        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);
        
        LOGGER.info("SomeFunStuff 护盾系统初始化完成！");
    }
    

    /**
     * 注册命令
     */
    private void onRegisterCommands(RegisterCommandsEvent event) {
        ShieldCommand.register(event.getDispatcher());
        LOGGER.info("护盾命令已注册");
    }

}
