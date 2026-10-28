package cn.yumis.fight.modules.combat.api;
import cn.yumis.fight.modules.attributes.api.*;
import cn.yumis.fight.modules.equipment.api.EquipmentEffects;
/** Applies to the same mutable actor draft as all other receipt effects, before the one WAL commit. */
public final class EquipmentHitEffects {
    private EquipmentHitEffects(){}
    public static void apply(CombatTarget target,Receipt receipt,boolean direct,Stats attacker,double currentMaximum,EquipmentEffects.Snapshot effects,Life actor,Runtime resources,double targetMaximum,long now){
        if(target==null||!receipt.accepted()||receipt.actual()<=0||actor.dead)return;
        double maximum=currentMaximum,healing=0;
        if(direct&&maximum>0)healing=receipt.actual()*targetMaximum*EquipmentDamage.lifeSteal(attacker.get(Stat.LIFE_STEAL))/maximum;
        if(!receipt.deathId().isEmpty()&&target.rewardsEligible()){
            healing+=effects.killRecovery;EquipmentBuffs.killed(resources,effects,target.power(),now);
        }
        if(healing>0)LifeRules.apply(actor,healing,true);
    }
}
