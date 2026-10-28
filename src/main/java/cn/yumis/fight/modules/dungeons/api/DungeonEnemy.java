package cn.yumis.fight.modules.dungeons.api;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.Level;
public final class DungeonEnemy extends Zombie {
    public DungeonEnemy(EntityType<? extends Zombie> type,Level level){
        super(type,level);
        setCanPickUpLoot(false);
        setPersistenceRequired();
    }
    @Override protected boolean isSunSensitive(){
        return false;
    }
    public int dungeonLevel(){
        return getPersistentData().getIntOr("ydnf_level",10);
    }
    public String instance(){
        return getPersistentData().getStringOr("ydnf_instance","");
    }
    public boolean boss(){
        return getPersistentData().getBooleanOr("ydnf_boss",false);
    }
    public boolean elite(){
        return getPersistentData().getBooleanOr("ydnf_elite",false);
    }
    public void scripted(){
        goalSelector.removeAllGoals(g->true);
        targetSelector.removeAllGoals(g->true);
    }
}
