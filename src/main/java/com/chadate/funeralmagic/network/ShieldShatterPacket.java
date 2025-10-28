package com.chadate.funeralmagic.network;

import com.chadate.funeralmagic.SomeFunStuff;
import com.chadate.funeralmagic.client.render.ShieldShatterEffect;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 护盾破碎效果网络包（服务端 -> 客户端）
 */
public record ShieldShatterPacket(int entityId, double centerX, double centerY, double centerZ, double radius) implements CustomPacketPayload {
    
    public static final CustomPacketPayload.Type<ShieldShatterPacket> TYPE = 
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(SomeFunStuff.MODID, "shield_shatter"));
    
    public static final StreamCodec<ByteBuf, ShieldShatterPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ShieldShatterPacket::entityId,
            ByteBufCodecs.DOUBLE, ShieldShatterPacket::centerX,
            ByteBufCodecs.DOUBLE, ShieldShatterPacket::centerY,
            ByteBufCodecs.DOUBLE, ShieldShatterPacket::centerZ,
            ByteBufCodecs.DOUBLE, ShieldShatterPacket::radius,
            ShieldShatterPacket::new
    );
    
    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
    
    /**
     * 客户端处理破碎效果
     */
    public static void handleClient(ShieldShatterPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null) {
                Vec3 center = new Vec3(packet.centerX, packet.centerY, packet.centerZ);
                ShieldShatterEffect.triggerShatter(packet.entityId, center, packet.radius);
            }
        });
    }
}
