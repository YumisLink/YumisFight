package cn.yumis.fight.modules.skills.api;
import cn.yumis.fight.modules.attributes.api.*;
import cn.yumis.fight.modules.equipment.api.EquipmentEffects;
import cn.yumis.fight.modules.combat.api.*;
/** One preparation rule for tooltip, ordinary/equipment starts and career starts. */
public final class SkillCosts {
    public record Cost(int mana,double life,double cooldown){}
    private SkillCosts(){}
    public static Cost resolve(SkillDefinition skill,int rank,Stats stats,EquipmentEffects.Snapshot effects,boolean magic){
        int original=skill.mp(rank);double recovery=stats.get(Stat.COOLDOWN_RECOVERY);if(recovery<=0)recovery=1;
        double cooldown=skill.cooldown(rank)/recovery/(1+stats.modifiers.getOrDefault("cooldownRecovery",0d));
        if(skill.basicSkill())cooldown*=1-stats.modifiers.getOrDefault("basicSkillCooldown",0d);
        if(magic)cooldown-=effects.magicCooldownReduction;
        return new Cost(effects.lifeCostRatio>0?0:original,effects.lifeCostRatio>0?original*effects.lifeCostRatio:0,Math.max(0,cooldown));
    }
    public static void pay(Cost cost,cn.yumis.fight.modules.combat.api.Runtime runtime,Life life,double maximum,double additionalFraction){
        if(runtime.training)return;double fraction=additionalFraction+(maximum>0?cost.life/maximum:0);
        if(runtime.mp<cost.mana)throw new IllegalArgumentException("MP不足");
        if(life.dead||fraction>0&&(maximum<=0||life.q-fraction<1/maximum))throw new IllegalArgumentException("生命不足，必须至少保留1点生命");
        runtime.mp-=cost.mana;if(fraction>0)LifeRules.apply(life,fraction,false);
    }
}
