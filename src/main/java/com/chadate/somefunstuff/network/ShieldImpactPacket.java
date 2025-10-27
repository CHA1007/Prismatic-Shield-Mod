package com.chadate.somefunstuff.network;

import com.chadate.somefunstuff.client.render.ShieldImpactEffect;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import static com.chadate.somefunstuff.SomeFunStuff.MODID;

/**
 * 护盾击中效果网络包
 * 从服务端发送到客户端，触发护盾表面的受击视觉效果
 */
public record ShieldImpactPacket(int entityId, Vec3 hitPosition, Vec3 shieldCenter) implements CustomPacketPayload {
    
    public static final Type<ShieldImpactPacket> TYPE = 
        new Type<>(ResourceLocation.fromNamespaceAndPath(MODID, "shield_impact"));
    
    public static final StreamCodec<ByteBuf, ShieldImpactPacket> STREAM_CODEC = 
        StreamCodec.composite(
            // 实体ID
            StreamCodec.of(
                (buf, id) -> buf.writeInt(id),
                ByteBuf::readInt
            ),
            ShieldImpactPacket::entityId,
            // 击中位置
            StreamCodec.of(
                (buf, vec) -> {
                    buf.writeDouble(vec.x);
                    buf.writeDouble(vec.y);
                    buf.writeDouble(vec.z);
                },
                buf -> new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble())
            ),
            ShieldImpactPacket::hitPosition,
            // 护盾中心
            StreamCodec.of(
                (buf, vec) -> {
                    buf.writeDouble(vec.x);
                    buf.writeDouble(vec.y);
                    buf.writeDouble(vec.z);
                },
                buf -> new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble())
            ),
            ShieldImpactPacket::shieldCenter,
            ShieldImpactPacket::new
        );
    
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
    
    /**
     * 处理客户端接收到的击中效果包
     */
    public static void handleClient(ShieldImpactPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            // 在客户端注册击中效果（传递实体ID、击中位置和护盾中心）
            ShieldImpactEffect.registerImpact(packet.entityId(), packet.hitPosition(), packet.shieldCenter());
        });
    }
}
