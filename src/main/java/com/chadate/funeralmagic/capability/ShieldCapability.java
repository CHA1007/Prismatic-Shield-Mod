package com.chadate.funeralmagic.capability;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * 护盾数据类
 */
public record ShieldCapability(boolean active, double radius, int strength) {

    /**
     * Compact constructor - 在创建实例时进行数据验证
     */
    public ShieldCapability {
        if (radius < 0) {
            radius = 0;
        }
        if (strength < 0) {
            strength = 0;
        }
        if (strength == 0 && active) {
            active = false;
        }
    }

    public static final ShieldCapability DEFAULT = new ShieldCapability(false, 3.0, 100);

    // Codec 用于序列化和反序列化
    public static final Codec<ShieldCapability> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.fieldOf("active").forGetter(ShieldCapability::active),
            Codec.DOUBLE.fieldOf("radius").forGetter(ShieldCapability::radius),
            Codec.INT.fieldOf("strength").forGetter(ShieldCapability::strength))
            .apply(instance, ShieldCapability::new));

    /**
     * 检查护盾是否真正激活
     */
    public boolean isShieldActive() {
        return active && strength > 0;
    }

    /**
     * 检查是否有足够的强度可以消耗
     */
    public boolean canConsumeStrength(int amount) {
        return strength >= amount;
    }

    /**
     * 创建一个激活状态改变的新实例
     */
    public ShieldCapability withActive(boolean newActive) {
        return new ShieldCapability(newActive, this.radius, this.strength);
    }

    /**
     * 创建一个半径改变的新实例
     */
    public ShieldCapability withRadius(double newRadius) {
        return new ShieldCapability(this.active, newRadius, this.strength);
    }

    /**
     * 创建一个强度改变的新实例
     */
    public ShieldCapability withStrength(int newStrength) {
        return new ShieldCapability(this.active, this.radius, newStrength);
    }

    /**
     * 消耗强度并返回新实例
     * 
     * @param amount 要消耗的强度值
     * @return 如果强度足够，返回消耗后的新实例；否则返回当前实例
     */
    public ShieldCapability consumeStrength(int amount) {
        if (strength >= amount) {
            return new ShieldCapability(this.active, this.radius, strength - amount);
        }
        return this;
    }

    /**
     * 增加强度并返回新实例
     */
    public ShieldCapability addStrength(int amount) {
        return new ShieldCapability(this.active, this.radius, strength + amount);
    }
}
