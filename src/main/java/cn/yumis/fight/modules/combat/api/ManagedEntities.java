package cn.yumis.fight.modules.combat.api;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.monster.*;
public final class ManagedEntities {private ManagedEntities(){}
    public static boolean includes(Entity e){
        return e instanceof Player||hostile(e)||e instanceof WorldVitalEntity;
    }
    public static boolean hostile(Entity e){
        return e instanceof Enemy||e instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon;
    }
    public static boolean neutralCreature(Entity e){return e instanceof Mob&&e.isAttackable()&&!includes(e);}
    public static boolean attackable(Entity e){return includes(e)||neutralCreature(e);}
}
