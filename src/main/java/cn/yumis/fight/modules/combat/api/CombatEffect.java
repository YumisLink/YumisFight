package cn.yumis.fight.modules.combat.api;

import com.google.gson.JsonObject;
import java.util.List;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/** Declarative combat properties on native effects; state/lifetime remains in MobEffectInstance. */
public interface CombatEffect {
    String NO_TRIGGERS="noCombatTriggers";
    String FULL_RECOVERY="fullRecovery";
    String END_ELEMENTAL="endElemental";

    static void recoverNativeState(Player player) {
        for (var effect : List.copyOf(player.getActiveEffects())) {
            if (effect.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) player.removeEffect(effect.getEffect());
        }
        player.clearFire();
        player.setTicksFrozen(0);
        player.getFoodData().setFoodLevel(20);
        player.getFoodData().setSaturation(20);
    }
    static boolean triggers(JsonObject report){
        return report==null||!report.has(NO_TRIGGERS)||!report.get(NO_TRIGGERS).getAsBoolean();
    }
    default boolean preventsDamage(){return false;}
    default double outgoingDamageScale(){return 1;}
    default double receivedHealingScale(){return 1;}
    default boolean externalHitEffects(){return true;}
    default boolean waivesMovementStamina(){return false;}
    static boolean protectedFromDamage(LivingEntity entity){
        if(entity==null)return false;
        return entity.getActiveEffects().stream().anyMatch(i->i.getEffect().value() instanceof CombatEffect e&&e.preventsDamage());
    }
    /** Owner-judged healing submitted by the local client is scaled by every active declarative effect. */
    static double healingScale(LivingEntity entity){
        if(entity==null)return 1;double scale=1;
        for(var i:entity.getActiveEffects())if(i.getEffect().value() instanceof CombatEffect e)scale*=e.receivedHealingScale();
        return scale;
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
    static boolean waivesMovementStamina(LivingEntity entity){
        if(entity==null)return false;
        return entity.getActiveEffects().stream().anyMatch(i->i.getEffect().value() instanceof CombatEffect e&&e.waivesMovementStamina());
    }
    static boolean permitsExternalHitEffects(LivingEntity owner,LivingEntity target){
        return permitsExternalHitEffects(owner)&&(!(target instanceof WorldVitalEntity world)||world.acceptsPlayerEffects());
    }
}
