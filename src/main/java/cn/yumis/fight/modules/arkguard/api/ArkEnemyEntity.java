package cn.yumis.fight.modules.arkguard.api;

import cn.yumis.fight.modules.dungeons.api.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.Level;

/** Existing dungeon appearance and poses with scene-owned routing and life facts. */
public final class ArkEnemyEntity extends DungeonEnemy implements WorldTargetEntity {
    private final String appearance;
    private Target initial;
    private double maximum=1;
    public ArkEnemyEntity(EntityType<? extends Zombie> type,Level level,String appearance){super(type,level);this.appearance=appearance;scripted();}
    public void configure(Target target){initial=target;maximum=target.health(1);}
    public void maximum(double value){maximum=value;}
    @Override public String appearance(){return appearance;}
    @Override public boolean boss(){return appearance.equals("goblin_shaman")||appearance.equals("lucas");}
    @Override public boolean elite(){return appearance.equals("goblin_captain");}
    @Override public int dungeonLevel(){return 1;}
    @Override public String instance(){return "";}
    @Override public Target initialCombatTarget(){if(initial==null){initial=new Target();initial.id=getUUID();initial.life.target=getUUID();initial.fixedLevel=true;initial.originalLevel=1;initial.noRewards=true;initial.damageImmune=true;}return initial;}
    @Override public int combatLevel(){return 1;}
    @Override public double combatMaximumHealth(){return maximum;}
    @Override public boolean rejectsNativeDamage(DamageSource source){return true;}
    @Override public boolean acceptsPlayerEffects(){return false;}
    @Override public boolean canBeAffected(net.minecraft.world.effect.MobEffectInstance effect){return ArkContentTypes.actorEffect(effect)&&super.canBeAffected(effect);}
    @Override public boolean shouldBeSaved(){return false;}
    @Override public void checkDespawn(){} // The match owns removal, including in a peaceful host world.
    @Override public void tick(){super.tick();if(level() instanceof ServerLevel&&tickCount>100&&initial==null)discard();}
}
