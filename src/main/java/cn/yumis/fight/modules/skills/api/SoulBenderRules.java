package cn.yumis.fight.modules.skills.api;

import cn.yumis.fight.modules.attributes.api.Stats;
import cn.yumis.fight.modules.character.api.JobCatalog;
import cn.yumis.fight.modules.character.api.Progress;
import java.util.LinkedHashMap;
import java.util.List;

/** Soul-bender variants of shared ghost-swordsman skills. */
final class SoulBenderRules {
    private SoulBenderRules(){}
    static void install(){
        SkillBalance.registerVariant(SkillCatalog.GHOST,SoulBenderRules::ghost);
        for(var skill:List.of(SkillCatalog.TRIPLE,SkillCatalog.WAVE))
            SkillBalance.registerVariant(skill,(input,progress,stats)->damage(input,progress));
    }
    private static SkillParameters.Snapshot ghost(SkillParameters.Snapshot input,Progress progress,Stats stats){
        var values=new LinkedHashMap<>(input.values());var skill=SkillCatalog.require(CareerSkills.SOUL_SLASH);int rank=SkillLearning.effective(stats,progress,skill);
        if(rank>0){values.put("soulBonus",SkillParameters.resolve(skill,rank,stats.parameters).get("damage",0));if(stats.modifiers.getOrDefault("ownGhostField",0d)>0)values.put("ghostFieldMultiplier",2/BuiltinSkillBalance.redMultiplier());}
        return damage(new SkillParameters.Snapshot(input.skill(),input.rank(),values),progress);
    }
    private static SkillParameters.Snapshot damage(SkillParameters.Snapshot input,Progress progress){
        if(!progress.hasJob(JobCatalog.SOUL_BENDER))return input;
        var values=new LinkedHashMap<>(input.values());
        for(String key:input.skill().damageParameters())values.computeIfPresent(key,(k,v)->v*1.2);
        values.computeIfPresent("soulBonus",(k,v)->v*1.2);
        return new SkillParameters.Snapshot(input.skill(),input.rank(),values);
    }
}
