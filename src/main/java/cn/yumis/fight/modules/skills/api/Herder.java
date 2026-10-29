package cn.yumis.fight.modules.skills.api;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.Level;
import net.minecraft.network.syncher.*;
/** Friendly summons never implement Enemy and never use vanilla health for combat arithmetic. */
public final class Herder extends PathfinderMob {
    private static final EntityDataAccessor<Integer> ACTION=SynchedEntityData.defineId(Herder.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> SHIELD=SynchedEntityData.defineId(Herder.class,EntityDataSerializers.FLOAT);
    public java.util.UUID owner;
    public Herder(EntityType<? extends Herder> type,Level level){super(type,level);setPersistenceRequired();setCanPickUpLoot(false);xpReward=0;}
    public static AttributeSupplier.Builder attributes(){return Mob.createMobAttributes().add(Attributes.MAX_HEALTH,100).add(Attributes.MOVEMENT_SPEED,.28).add(Attributes.FOLLOW_RANGE,24);}
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder){super.defineSynchedData(builder);builder.define(ACTION,0);builder.define(SHIELD,0f);}
    public int action(){return entityData.get(ACTION);}
    public void action(int value){entityData.set(ACTION,value);}
    public void project(double healthFraction,double shieldFraction){setHealth((float)(getMaxHealth()*Math.clamp(healthFraction,.00001,1)));entityData.set(SHIELD,(float)Math.clamp(shieldFraction,0,1));}
    @Override protected void customServerAiStep(ServerLevel level){HerderActions.tick(this);}
    @Override public boolean hurtServer(ServerLevel level,DamageSource source,float amount){return HerderActions.incoming(this,level,source,amount);}
    @Override public boolean removeWhenFarAway(double distance){return false;}
    @Override public boolean canBeLeashed(){return false;}
    @Override public boolean canUsePortal(boolean ignorePassenger){return false;}
}
