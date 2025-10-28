package com.chadate.funeralmagic.network;

import com.chadate.funeralmagic.SomeFunStuff;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * 网络处理器
 * 负责注册所有的网络包
 */
@EventBusSubscriber(modid = SomeFunStuff.MODID)
public class NetworkHandler {
    
    /**
     * 网络协议版本
     */
    private static final String PROTOCOL_VERSION = "1";
    
    /**
     * 注册网络包
     */
    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);
        
        // 注册护盾数据同步包（服务端 -> 客户端）
        registrar.playToClient(
            ShieldDataSyncPacket.TYPE,
            ShieldDataSyncPacket.STREAM_CODEC,
            ShieldDataSyncPacket::handleClient
        );
        
        // 注册护盾击中效果包（服务端 -> 客户端）
        registrar.playToClient(
            ShieldImpactPacket.TYPE,
            ShieldImpactPacket.STREAM_CODEC,
            ShieldImpactPacket::handleClient
        );
        
        // 注册护盾破碎效果包（服务端 -> 客户端）
        registrar.playToClient(
            ShieldShatterPacket.TYPE,
            ShieldShatterPacket.STREAM_CODEC,
            ShieldShatterPacket::handleClient
        );
    }
}
