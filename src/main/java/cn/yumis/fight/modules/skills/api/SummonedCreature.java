package cn.yumis.fight.modules.skills.api;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.Level;
import net.minecraft.network.syncher.*;
/** Friendly summons never implement Enemy and never use vanilla health for combat arithmetic. */
public final class SummonedCreature extends PathfinderMob {
    private static final EntityDataAccessor<Float> SHIELD=SynchedEntityData.defineId(SummonedCreature.class,EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Long> ACTION_AT=SynchedEntityData.defineId(SummonedCreature.class,EntityDataSerializers.LONG);
    private static final EntityDataAccessor<String> MOVE=SynchedEntityData.defineId(SummonedCreature.class,EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Long> DURATION=SynchedEntityData.defineId(SummonedCreature.class,EntityDataSerializers.LONG);
    public java.util.UUID owner;
    public SummonKind kind(){return SkillContentTypes.kind(getType());}
    public long actionAt(){return entityData.get(ACTION_AT);}
    @Override public boolean isPushable(){return false;}
    public SummonedCreature(EntityType<? extends SummonedCreature> type,Level level){super(type,level);setPersistenceRequired();setCanPickUpLoot(false);xpReward=0;}
    public static AttributeSupplier.Builder attributes(){return Mob.createMobAttributes().add(Attributes.MAX_HEALTH,100).add(Attributes.MOVEMENT_SPEED,.28).add(Attributes.FOLLOW_RANGE,24);}
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder){super.defineSynchedData(builder);builder.define(ACTION_AT,0L);builder.define(SHIELD,0f);builder.define(MOVE,"");builder.define(DURATION,1000L);}
    public String move(){return entityData.get(MOVE);}
    public long actionDuration(){return entityData.get(DURATION);}
    public void play(String move,long duration){entityData.set(MOVE,move);entityData.set(DURATION,duration);entityData.set(ACTION_AT,System.currentTimeMillis());}
    public void project(double healthFraction,double shieldFraction){setHealth((float)(getMaxHealth()*Math.clamp(healthFraction,.00001,1)));entityData.set(SHIELD,(float)Math.clamp(shieldFraction,0,1));}
    @Override protected void customServerAiStep(ServerLevel level){SummonActions.tick(this);}
    @Override public boolean hurtServer(ServerLevel level,DamageSource source,float amount){return SummonActions.incoming(this,level,source,amount);}
    @Override public boolean removeWhenFarAway(double distance){return false;}
    @Override public boolean canBeLeashed(){return false;}
    @Override public boolean canUsePortal(boolean ignorePassenger){return false;}
}
