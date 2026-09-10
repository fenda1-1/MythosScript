// 文件路径: src/main/java/com/zszl/zszlScriptMod/handlers/GhostPlayer.java
package com.zszl.zszlScriptMod.handlers;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.world.World;

/**
 * 一个自定义的玩家实体，用于“极限攻速”功能。
 * 它重写了 canBeCollidedWith 方法，使其不会被玩家的准星选中或发生碰撞。
 */
public class GhostPlayer extends EntityOtherPlayerMP {

    public GhostPlayer(World worldIn, GameProfile gameProfileIn) {
        super(worldIn, gameProfileIn);
    }

    /**
     * 重写此方法使其返回 false，这样实体就不会被射线追踪（准星）选中。
     */
    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    /**
     * 重写此方法以返回0，进一步减少不必要的交互计算。
     */
    @Override
    public float getCollisionBorderSize() {
        return 0.0F;
    }

    @Override
    public boolean canBePushed() {
        return false;
    }

    @Override
    public void applyEntityCollision(net.minecraft.entity.Entity entityIn) {
        // 幽灵实体不参与任何推挤逻辑
    }
}
