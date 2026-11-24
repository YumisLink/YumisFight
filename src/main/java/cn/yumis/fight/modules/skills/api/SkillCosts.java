package cn.yumis.fight.modules.skills.api;
import cn.yumis.fight.modules.attributes.api.*;
import cn.yumis.fight.modules.equipment.api.EquipmentEffects;
import cn.yumis.fight.modules.combat.api.*;
import cn.yumis.fight.modules.dungeons.api.TowerBattleCard;
/** One preparation rule for tooltip, ordinary/equipment starts and career starts. */
public final class SkillCosts {
    public record Cost(int mana,double life,double cooldown){}
    private SkillCosts(){}
    public static Cost resolve(SkillDefinition skill,int rank,Stats stats,EquipmentEffects.Snapshot effects,boolean magic){
        double cooldownAdd=0d,cooldownAddMul=1d,cooldownMulAdd=0d,cooldownMul=1d,mpMulAdd=0d,mpMul=1d;
        var allParams=new java.util.ArrayList<ParameterModifier>(stats.parameters);
        allParams.addAll(ElementalistMorphs.activeModifiers(skill.id,stats.parameters));
        allParams.addAll(BattleMageMorphs.activeModifiers(skill.id,stats.parameters));
        for(ParameterModifier mod:allParams)if(mod.skill().equals(skill.id)){
            switch(mod.parameter()){
                case "cooldownAdd"->{cooldownAdd+=mod.additive();cooldownAddMul*=mod.multiplier();}
                case "cooldownMultiplier"->{cooldownMulAdd+=mod.additive();cooldownMul*=mod.multiplier();}
                case "mpMultiplier"->{mpMulAdd+=mod.additive();mpMul*=mod.multiplier();}
                default->{}
            }
        }
        double effectiveMpMul=Math.max(0d,(1d+mpMulAdd)*mpMul);
        double effectiveCdAdd=cooldownAdd*cooldownAddMul;
        double effectiveCdMul=Math.max(0d,(1d+cooldownMulAdd)*cooldownMul);
        int original=(int)Math.round(skill.mp(rank)*effectiveMpMul*stats.modifiers.getOrDefault("skillManaMultiplier",1d));
        if(SummonerSkills.contains(skill.id))original=(int)Math.round(original*Math.max(0,1-stats.modifiers.getOrDefault("summonMana",0d)));
        double recovery=stats.get(Stat.COOLDOWN_RECOVERY);if(recovery<=0)recovery=1;
        double baseCd=Math.max(0d,(skill.cooldown(rank)+effectiveCdAdd)*effectiveCdMul);
        if("CHASER".equals(skill.family))baseCd*=stats.modifiers.getOrDefault("bmChaserCdScale",1d);
        double cooldown=baseCd/recovery/(1+stats.modifiers.getOrDefault("cooldownRecovery",0d));
        if(skill.basicSkill())cooldown*=1-stats.modifiers.getOrDefault("basicSkillCooldown",0d);
        if(magic)cooldown-=effects.magicCooldownReduction;
        if(TowerSkillTraits.rotating(skill.id)&&TowerBattleCard.SPIN.held(stats))cooldown*=1-TowerBattleCard.SPIN.ratio("cooldown");
        int mana=effects.lifeCostRatio>0?0:Math.toIntExact(Math.round(original*(1+stats.modifiers.getOrDefault(Stats.SKILL_EXTRA_MANA_COST,0d))));
        return new Cost(mana,effects.lifeCostRatio>0?original*effects.lifeCostRatio:0,Math.max(0,cooldown));
    }
    public static void pay(Cost cost,cn.yumis.fight.modules.combat.api.Runtime runtime,Life life,double maximum,double additionalFraction){
        if(runtime.training)return;double fraction=additionalFraction+(maximum>0?cost.life/maximum:0);
        if(runtime.mp<cost.mana)throw new IllegalArgumentException("MP不足");
        if(life.dead||fraction>0&&(maximum<=0||life.q-fraction<1/maximum))throw new IllegalArgumentException("生命不足，必须至少保留1点生命");
        runtime.mp-=cost.mana;if(fraction>0)LifeRules.apply(life,fraction,false);
    }
}
