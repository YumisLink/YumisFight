package cn.yumis.fight.modules.dungeons.api;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.goal.ZombieAttackGoal;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.Level;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;
public class DungeonEnemy extends Zombie {
    private static final EntityDataAccessor<String> MOVE=SynchedEntityData.defineId(DungeonEnemy.class,EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Long> MOVE_AT=SynchedEntityData.defineId(DungeonEnemy.class,EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Long> MOVE_DURATION=SynchedEntityData.defineId(DungeonEnemy.class,EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Boolean> GUARDED=SynchedEntityData.defineId(DungeonEnemy.class,EntityDataSerializers.BOOLEAN);
    private boolean practiceAi;
    public DungeonEnemy(EntityType<? extends Zombie> type,Level level){
        super(type,level);
        setCanPickUpLoot(false);
        setPersistenceRequired();
    }
    @Override protected boolean isSunSensitive(){
        return false;
    }

    @Override protected boolean convertsInWater(){
        return PalaceEnemyKind.of(this)==null&&super.convertsInWater();
    }

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return null;
    }

    @Override
    protected @Nullable SoundEvent getHurtSound(DamageSource source) {
        return null;
    }

    @Override
    protected @Nullable SoundEvent getDeathSound() {
        return null;
    }

    public int dungeonLevel(){
        return getPersistentData().getIntOr("ydnf_level",10);
    }
    public boolean fixedLevel(){return false;}
    public String instance(){
        return getPersistentData().getStringOr("ydnf_instance","");
    }
    public boolean boss(){
        return getPersistentData().getBooleanOr("ydnf_boss",false);
    }
    public boolean elite(){
        return getPersistentData().getBooleanOr("ydnf_elite",false);
    }
    public String appearance(){return net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(getType()).getPath();}
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder){super.defineSynchedData(builder);builder.define(MOVE,"");builder.define(MOVE_AT,0L);builder.define(MOVE_DURATION,0L);builder.define(GUARDED,false);}
    public void play(String move,long duration){entityData.set(MOVE,move);entityData.set(MOVE_AT,System.currentTimeMillis());entityData.set(MOVE_DURATION,duration);}
    public String move(){return entityData.get(MOVE);}
    public long moveAt(){return entityData.get(MOVE_AT);}
    public long moveDuration(){return entityData.get(MOVE_DURATION);}
    public void guarded(boolean value){entityData.set(GUARDED,value);}
    public boolean guarded(){return entityData.get(GUARDED);}
    @Override public boolean isPickable(){var classic=ClassicEnemyKind.of(this);return (classic==null||!classic.passive)&&!move().equals("spent")&&super.isPickable();}
    @Override public boolean isPushable(){var classic=ClassicEnemyKind.of(this);return !appearance().equals(AbyssParty.Enemy.PILLAR.id)&&(classic==null||!classic.stationary&&!classic.passive)&&!move().equals("wood_cage")&&GblEnemyKind.of(this)!=GblEnemyKind.DRAGON_CANNON&&ForbiddenEnemyKind.of(this)!=ForbiddenEnemyKind.BARRIER_CORE&&SkasaEnemyKind.of(this)!=SkasaEnemyKind.ICE_DRAGON&&!move().equals("spent")&&super.isPushable();}
    public void scripted(){
        goalSelector.removeAllGoals(g->true);
        targetSelector.removeAllGoals(g->true);
    }
    /** Practice enemies use the room's target selection and the existing native melee receipt. */
    public void practice(){
        if(practiceAi)return;
        practiceAi=true;
        getPersistentData().putBoolean("ydnf_practice_enemy",true);
        scripted();
        goalSelector.addGoal(1,new ZombieAttackGoal(this,1,false));
    }
    @Override protected boolean shouldDropLoot(ServerLevel level){
        return !getPersistentData().getBooleanOr("ydnf_practice_enemy",false)&&super.shouldDropLoot(level);
    }
    @Override public boolean shouldDropExperience(){
        return AbyssParty.Enemy.find(appearance())==null&&!getPersistentData().getBooleanOr("ydnf_practice_enemy",false)&&super.shouldDropExperience();
    }
    /** Content may guide movement while leaving native melee and target goals intact. */
    public void movementGoal(net.minecraft.world.entity.ai.goal.Goal goal){goalSelector.addGoal(1,goal);}
}
