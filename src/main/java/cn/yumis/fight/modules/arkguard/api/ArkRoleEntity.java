package cn.yumis.fight.modules.arkguard.api;

import cn.yumis.fight.modules.npc.api.NpcEntity;
import cn.yumis.fight.modules.dungeons.api.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;

/** The card identity survives projections; each world entity has its own formal life. */
public final class ArkRoleEntity extends NpcEntity implements WorldTargetEntity {
    private static final EntityDataAccessor<String> ROLE=SynchedEntityData.defineId(ArkRoleEntity.class,EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> OWNER=SynchedEntityData.defineId(ArkRoleEntity.class,EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> CARD=SynchedEntityData.defineId(ArkRoleEntity.class,EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> STAR=SynchedEntityData.defineId(ArkRoleEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> CARRIED=SynchedEntityData.defineId(ArkRoleEntity.class,EntityDataSerializers.BOOLEAN);
    private Target initial;
    private double maximum=1;
    public ArkRoleEntity(EntityType<? extends NpcEntity> type,Level level){super(type,level);goalSelector.removeAllGoals(goal->true);targetSelector.removeAllGoals(goal->true);}
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder){super.defineSynchedData(builder);builder.define(ROLE,"");builder.define(OWNER,"");builder.define(CARD,"");builder.define(STAR,1);builder.define(CARRIED,false);}
    public String roleId(){return entityData.get(ROLE);}
    public String ownerId(){return entityData.get(OWNER);}
    public String cardId(){return entityData.get(CARD);}
    public int star(){return entityData.get(STAR);}
    public void configure(String owner,ArkRoster.Card card,ArkRules.Unit unit,Target target){
        entityData.set(ROLE,unit.id());entityData.set(OWNER,owner);entityData.set(CARD,card.token);entityData.set(STAR,card.star);
        entityData.set(CARRIED,card.held);
        appearance(unit.skin());setCustomName(Component.literal(unit.name()+" "+"★".repeat(card.star)));setCustomNameVisible(true);initial=target;maximum=target.health(1);
    }
    public void core(Target target){initial=target;maximum=target.health(1);entityData.set(ROLE,"core");setCustomName(Component.literal("方舟核心"));setCustomNameVisible(false);setInvisible(true);setNoAi(true);setNoGravity(true);}
    public void maximum(double value){maximum=value;}
    @Override public Target initialCombatTarget(){if(initial==null){initial=new Target();initial.id=getUUID();initial.life.target=getUUID();initial.fixedLevel=true;initial.originalLevel=1;initial.noRewards=true;initial.damageImmune=true;}return initial;}
    @Override public int combatLevel(){return 1;}
    @Override public double combatMaximumHealth(){return maximum;}
    @Override public boolean rejectsNativeDamage(DamageSource source){return true;}
    @Override public boolean acceptsPlayerEffects(){return false;}
    @Override public boolean canBeAffected(net.minecraft.world.effect.MobEffectInstance effect){return ArkContentTypes.actorEffect(effect)&&super.canBeAffected(effect);}
    @Override public boolean shouldBeSaved(){return false;}
    @Override public boolean isPickable(){return !entityData.get(CARRIED)&&super.isPickable();}
    @Override public void tick(){super.tick();if(level() instanceof ServerLevel&&tickCount>100&&roleId().isBlank())discard();}
}
