package cn.yumis.fight.modules.combat.api;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.monster.*;
public final class ManagedEntities {private ManagedEntities(){}
    public static boolean includes(Entity e){
        return e instanceof Player||e instanceof Monster||e instanceof Enemy||e instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon;
    }
}
