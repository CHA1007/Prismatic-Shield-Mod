package com.chadate.somefunstuff.client.shader;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.io.IOException;

import static com.chadate.somefunstuff.SomeFunStuff.MODID;

/**
 * 自定义着色器管理器
 * 管理MOD的所有自定义GLSL着色器
 */
@OnlyIn(Dist.CLIENT)
public class ModShaders {
    
    private static ShaderInstance energyShieldShader;
    
    /**
     * 注册自定义着色器
     * 在游戏资源加载时调用
     */
    public static void registerShaders(ResourceProvider resourceProvider, 
                                       java.util.function.Consumer<ShaderInstance> shaderConsumer) throws IOException {
        // 注册能量护盾着色器
        shaderConsumer.accept(
            new ShaderInstance(
                resourceProvider,
                ResourceLocation.fromNamespaceAndPath(MODID, "energy_shield"),
                DefaultVertexFormat.POSITION_COLOR_NORMAL
            )
        );
    }
    
    /**
     * 获取能量护盾着色器实例
     */
    public static ShaderInstance getEnergyShieldShader() {
        return energyShieldShader;
    }
    
    /**
     * 设置能量护盾着色器实例
     * 由着色器注册系统调用
     */
    public static void setEnergyShieldShader(ShaderInstance shader) {
        energyShieldShader = shader;
    }
}
