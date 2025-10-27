package com.chadate.funeralmagic.capability;

import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

import com.chadate.funeralmagic.SomeFunStuff;

/**
 * 护盾附件类型注册
 * 使用新的 AttachmentType 系统替代旧的 Capability
 */
public class ShieldCapabilities {
    
    // 创建 AttachmentType 的 DeferredRegister
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES = 
        DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, SomeFunStuff.MODID);
    
    /**
     * 护盾附件类型
     */
    public static final Supplier<AttachmentType<ShieldCapability>> SHIELD_ATTACHMENT = 
        ATTACHMENT_TYPES.register("shield_capability", () -> 
            AttachmentType.builder(() -> ShieldCapability.DEFAULT)
                .serialize(ShieldCapability.CODEC)
                .build()
        );
    
    /**
     * 注册 AttachmentType
     */
    public static void register(IEventBus modEventBus) {
        ATTACHMENT_TYPES.register(modEventBus);
    }
}
