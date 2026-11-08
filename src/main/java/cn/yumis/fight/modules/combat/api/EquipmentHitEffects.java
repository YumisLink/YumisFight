package cn.yumis.fight.modules.combat.api;
import cn.yumis.fight.modules.attributes.api.*;
import cn.yumis.fight.modules.equipment.api.EquipmentEffects;
/** Owner-side equipment hit effects, using the shared life and equipment buff rules. */
public final class EquipmentHitEffects {
    private EquipmentHitEffects(){}
    public static void apply(CombatTarget target,Receipt receipt,boolean direct,Stats attacker,double currentMaximum,EquipmentEffects.Snapshot effects,Life actor,Runtime resources,double targetMaximum,long now){
        if(target==null||!receipt.accepted()||receipt.actual()<=0||actor.dead)return;
        double maximum=currentMaximum,healing=0;
        if(direct&&maximum>0)healing=receipt.actual()*targetMaximum*Math.clamp(EquipmentDamage.lifeSteal(attacker.get(Stat.LIFE_STEAL))+attacker.modifiers.getOrDefault("lifeStealRateAdd",0d),0,.9)/maximum;
        if(!receipt.deathId().isEmpty()&&target.rewardsEligible()){
            healing+=effects.killRecovery;EquipmentBuffs.killed(resources,effects,target.power(),now);
        }
        if(healing>0)LifeRules.apply(actor,healing,true);
    }
}
