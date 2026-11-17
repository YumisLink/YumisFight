package cn.yumis.fight.modules.combat.api;

import net.minecraft.world.entity.LivingEntity;

/** Declarative combat properties on native effects; state/lifetime remains in MobEffectInstance. */
public interface CombatEffect {
    default boolean preventsDamage(){return false;}
    default double outgoingDamageScale(){return 1;}
    default boolean externalHitEffects(){return true;}
    static boolean protectedFromDamage(LivingEntity entity){
        if(entity==null)return false;
        return entity.getActiveEffects().stream().anyMatch(i->i.getEffect().value() instanceof CombatEffect e&&e.preventsDamage());
    }
    static double outgoingScale(LivingEntity entity){
        if(entity==null)return 1;double scale=1;
        for(var i:entity.getActiveEffects())if(i.getEffect().value() instanceof CombatEffect e)scale*=e.outgoingDamageScale();
        return scale;
    }
    static boolean permitsExternalHitEffects(LivingEntity entity){
        if(entity==null)return true;
        return entity.getActiveEffects().stream().noneMatch(i->i.getEffect().value() instanceof CombatEffect e&&!e.externalHitEffects());
    }
    static boolean permitsExternalHitEffects(LivingEntity owner,LivingEntity target){
        return permitsExternalHitEffects(owner)&&(!(target instanceof WorldVitalEntity world)||world.acceptsPlayerEffects());
    }
}
