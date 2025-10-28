package com.chadate.funeralmagic.capability;

import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

import com.chadate.funeralmagic.SomeFunStuff;

/**
 * 护盾附件类型注册
 */
public class ShieldCapabilities {
    
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES = 
        DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, SomeFunStuff.MODID);
    
    public static final Supplier<AttachmentType<ShieldCapability>> SHIELD_ATTACHMENT = 
        ATTACHMENT_TYPES.register("shield_capability", () -> 
            AttachmentType.builder(() -> ShieldCapability.DEFAULT)
                .serialize(ShieldCapability.CODEC)
                .build()
        );

    public static void register(IEventBus modEventBus) {
        ATTACHMENT_TYPES.register(modEventBus);
    }
}
