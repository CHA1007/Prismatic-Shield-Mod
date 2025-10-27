package com.chadate.somefunstuff.capability;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * 护盾数据类
 * 使用 Record 和 Codec 来存储护盾状态
 */
public record ShieldCapability(boolean active, double radius, int strength) {
    
    // 默认护盾数据
    public static final ShieldCapability DEFAULT = new ShieldCapability(false, 3.0, 100);
    
    // Codec 用于序列化和反序列化
    public static final Codec<ShieldCapability> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            Codec.BOOL.fieldOf("active").forGetter(ShieldCapability::active),
            Codec.DOUBLE.fieldOf("radius").forGetter(ShieldCapability::radius),
            Codec.INT.fieldOf("strength").forGetter(ShieldCapability::strength)
        ).apply(instance, ShieldCapability::new)
    );
    
    public boolean isShieldActive() {
        return active && strength > 0;
    }
    
    public void setShieldActive(boolean active) {
        // Record 是不可变的，这个方法在 ShieldManager 中会创建新实例
    }
    
    public double getShieldRadius() {
        return radius;
    }
    
    public void setShieldRadius(double radius) {
        // Record 是不可变的，这个方法在 ShieldManager 中会创建新实例
    }
    
    public int getShieldStrength() {
        return strength;
    }
    
    public void setShieldStrength(int strength) {
        // Record 是不可变的，这个方法在 ShieldManager 中会创建新实例
    }
    
    public boolean consumeStrength(int amount) {
        // Record 是不可变的，这个方法在 ShieldManager 中会创建新实例
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
        return new ShieldCapability(this.active, Math.max(0, newRadius), this.strength);
    }
    
    /**
     * 创建一个强度改变的新实例
     */
    public ShieldCapability withStrength(int newStrength) {
        int validStrength = Math.max(0, newStrength);
        // 保持原有的激活状态，只有强度为 0 时才强制关闭
        boolean newActive = validStrength > 0 ? this.active : false;
        return new ShieldCapability(newActive, this.radius, validStrength);
    }
    
    /**
     * 消耗强度并返回新实例
     */
    public ShieldCapability withConsumedStrength(int amount) {
        if (strength >= amount) {
            int newStrength = strength - amount;
            boolean newActive = newStrength > 0 && this.active;
            return new ShieldCapability(newActive, this.radius, newStrength);
        }
        return this;
    }
}
