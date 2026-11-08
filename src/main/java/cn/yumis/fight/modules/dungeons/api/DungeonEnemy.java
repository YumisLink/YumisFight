package cn.yumis.fight.modules.dungeons.api;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.Level;
import net.minecraft.network.syncher.*;
public final class DungeonEnemy extends Zombie {
    private static final EntityDataAccessor<String> MOVE=SynchedEntityData.defineId(DungeonEnemy.class,EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Long> MOVE_AT=SynchedEntityData.defineId(DungeonEnemy.class,EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Long> MOVE_DURATION=SynchedEntityData.defineId(DungeonEnemy.class,EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Boolean> GUARDED=SynchedEntityData.defineId(DungeonEnemy.class,EntityDataSerializers.BOOLEAN);
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
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder){super.defineSynchedData(builder);builder.define(MOVE,"");builder.define(MOVE_AT,0L);builder.define(MOVE_DURATION,0L);builder.define(GUARDED,false);}
    public void play(String move,long duration){entityData.set(MOVE,move);entityData.set(MOVE_AT,System.currentTimeMillis());entityData.set(MOVE_DURATION,duration);}
    public String move(){return entityData.get(MOVE);}
    public long moveAt(){return entityData.get(MOVE_AT);}
    public long moveDuration(){return entityData.get(MOVE_DURATION);}
    public void guarded(boolean value){entityData.set(GUARDED,value);}
    public boolean guarded(){return entityData.get(GUARDED);}
    @Override public boolean isPickable(){var classic=ClassicEnemyKind.of(this);return (classic==null||!classic.passive)&&!move().equals("spent")&&super.isPickable();}
    @Override public boolean isPushable(){var classic=ClassicEnemyKind.of(this);return (classic==null||!classic.stationary&&!classic.passive)&&!move().equals("wood_cage")&&GblEnemyKind.of(this)!=GblEnemyKind.DRAGON_CANNON&&ForbiddenEnemyKind.of(this)!=ForbiddenEnemyKind.BARRIER_CORE&&SkasaEnemyKind.of(this)!=SkasaEnemyKind.ICE_DRAGON&&!move().equals("spent")&&super.isPushable();}
    public void scripted(){
        goalSelector.removeAllGoals(g->true);
        targetSelector.removeAllGoals(g->true);
    }
}
