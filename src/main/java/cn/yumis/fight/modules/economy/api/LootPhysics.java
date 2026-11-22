package cn.yumis.fight.modules.economy.api;
import net.minecraft.world.phys.Vec3;

/** 原版物品实体的散落物理常数，供私人掉落等非 ItemEntity 实体复刻原版死亡散落。 */
public final class LootPhysics {
    private LootPhysics(){}
    /** 与原版 ItemEntity 相同的移动后摩擦：空气阻力 0.98，落地时水平按脚下方块摩擦衰减，垂直下落反弹 -0.5。 */
    public static Vec3 friction(Vec3 velocity,boolean onGround,float blockFriction){
        float horizontal=onGround?.98F*blockFriction:.98F;
        Vec3 next=velocity.multiply(horizontal,.98F,horizontal);
        return onGround&&next.y<0?next.multiply(1,-.5,1):next;
    }
}
