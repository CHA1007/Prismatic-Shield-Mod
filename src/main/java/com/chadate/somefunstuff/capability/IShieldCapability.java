package com.chadate.somefunstuff.capability;

/**
 * 护盾能力接口
 * 定义玩家护盾的基本属性和行为
 */
public interface IShieldCapability {
    
    /**
     * 护盾是否激活
     */
    boolean isShieldActive();
    
    /**
     * 设置护盾激活状态
     */
    void setShieldActive(boolean active);
    
    /**
     * 获取护盾半径
     */
    double getShieldRadius();
    
    /**
     * 设置护盾半径
     */
    void setShieldRadius(double radius);
    
    /**
     * 获取护盾强度（可选，用于扩展功能）
     */
    int getShieldStrength();
    
    /**
     * 设置护盾强度
     */
    void setShieldStrength(int strength);
    
    /**
     * 消耗护盾强度
     * @return 是否成功消耗
     */
    boolean consumeStrength(int amount);
}
