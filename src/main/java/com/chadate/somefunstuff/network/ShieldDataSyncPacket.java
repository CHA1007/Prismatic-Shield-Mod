package com.chadate.somefunstuff.network;

import com.chadate.somefunstuff.SomeFunStuff;
import com.chadate.somefunstuff.capability.ShieldCapabilities;
import com.chadate.somefunstuff.capability.ShieldCapability;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 护盾数据同步网络包
 * 用于将服务端的护盾数据同步到客户端
 */
public record ShieldDataSyncPacket(boolean active, double radius, int strength) implements CustomPacketPayload {
    
    public static final Type<ShieldDataSyncPacket> TYPE = 
        new Type<>(ResourceLocation.fromNamespaceAndPath(SomeFunStuff.MODID, "shield_data_sync"));
    
    // 定义如何序列化和反序列化这个包
    public static final StreamCodec<ByteBuf, ShieldDataSyncPacket> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.BOOL,
        ShieldDataSyncPacket::active,
        ByteBufCodecs.DOUBLE,
        ShieldDataSyncPacket::radius,
        ByteBufCodecs.INT,
        ShieldDataSyncPacket::strength,
        ShieldDataSyncPacket::new
    );
    
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
    
    /**
     * 客户端处理接收到的包
     */
    public static void handleClient(ShieldDataSyncPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = Minecraft.getInstance().player;
            if (player == null) {
                return;
            }
            
            // 创建新的护盾数据并应用到玩家
            ShieldCapability newShield = new ShieldCapability(
                packet.active,
                packet.radius,
                packet.strength
            );
            
            player.setData(ShieldCapabilities.SHIELD_ATTACHMENT, newShield);
        });
    }
}
