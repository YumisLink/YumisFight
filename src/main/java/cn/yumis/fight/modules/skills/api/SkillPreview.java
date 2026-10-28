package cn.yumis.fight.modules.skills.api;
import cn.yumis.fight.modules.attributes.api.Stat;
import cn.yumis.fight.modules.combat.api.CombatRules;


import cn.yumis.fight.modules.character.api.Progress;
import cn.yumis.fight.modules.combat.api.Runtime;
import cn.yumis.fight.modules.attributes.api.Stats;
/** Zero-defense, zero-resistance, non-critical panel damage; never rolls combat RNG. */
public final class SkillPreview {
    private static final com.google.gson.Gson JSON=new com.google.gson.GsonBuilder().disableHtmlEscaping().create();
    private static <T>T copy(T value,Class<T> type){return JSON.fromJson(JSON.toJson(value),type);}

    private SkillPreview(){}
    public static Stats currentStats(Stats equipment,cn.yumis.fight.modules.combat.api.Runtime resources,long now){
        Stats stats=copy(equipment,Stats.class);
        if(resources.buffs.getOrDefault("yumis_dungeon_and_fight:overdrive",0L)>now){
            double power=resources.absorption.getOrDefault("overdrivePower",0d);
            stats.mut(Stat.PHYSICAL_ATTACK,power);
        }
        cn.yumis.fight.modules.combat.api.EquipmentBuffs.apply(stats,resources,now);
        cn.yumis.fight.modules.equipment.api.EquipmentEffects.finish(stats,resources.equipment);
        return stats;
    }
    public record Damage(double minimum,double maximum){
        public Damage times(double count){return new Damage(minimum*count,maximum*count);}
        public Damage plus(Damage other){return new Damage(minimum+other.minimum,maximum+other.maximum);}
    }
    public record Preview(int rank,int slashes,Damage hit,Damage finisher,Damage total,Damage shortSwordWave,int mp,double cooldown,SkillParameters.Snapshot parameters,cn.yumis.fight.modules.equipment.api.EquipmentEffects.Snapshot effects){}
    public static double mastery(Progress progress,Stats stats){
        int rank=SkillLearning.effective(stats,progress,SkillCatalog.MASTERY);
        return rank<=0?0:Math.pow(1.12,rank-1);
    }
    public static Damage damage(Progress progress,Stats stats,SkillDefinition skill,double coefficient,boolean passive,cn.yumis.fight.modules.equipment.api.EquipmentEffects.Snapshot effects){
        boolean magic=CareerRules.magic(skill,progress);
        int[] elements=SkillPreviewRules.elements(skill);boolean allowed=cn.yumis.fight.modules.combat.api.EquipmentDamage.allowed(effects,elements);elements=cn.yumis.fight.modules.combat.api.EquipmentDamage.elements(effects,elements);
        double mastery=mastery(progress,stats);
        var low=CombatRules.panel(stats,magic,elements,coefficient,!passive,false);
        var high=CombatRules.panel(stats,magic,elements,coefficient,!passive,true);
        if(!allowed){low=new CombatRules.Damage(0,low.extra(),low.element());high=new CombatRules.Damage(0,high.extra(),high.element());}
        double minimum=cn.yumis.fight.modules.skills.api.SkillDamageRules.skillDamage(low,stats,skill!=null&&skill.basicSkill(),mastery,false,passive,1).total(),maximum=cn.yumis.fight.modules.skills.api.SkillDamageRules.skillDamage(high,stats,skill!=null&&skill.basicSkill(),mastery,false,passive,1).total();
        return new Damage(minimum+cn.yumis.fight.modules.combat.api.EquipmentDamage.append(minimum,stats,effects,0,new double[4]),maximum+cn.yumis.fight.modules.combat.api.EquipmentDamage.append(maximum,stats,effects,0,new double[4]));
    }
    public static Preview resolve(Progress progress,Stats stats,SkillDefinition skill,SkillParameters.Snapshot parameters,cn.yumis.fight.modules.equipment.api.EquipmentEffects.Snapshot effects){
        stats=CareerRules.castDamage(stats,parameters);
        int rank=SkillLearning.effective(stats,progress,skill);
        var definition=SkillPreviewRules.require(skill);
        int count=definition.hits().applyAsInt(parameters);
        double coefficient=parameters.get("previewDamage",parameters.get("damage",0));
        if(skill.id.equals(CareerSkills.CRESCENT))coefficient/=3;
        Damage hit=count==0?new Damage(0,0):damage(progress,stats,skill,coefficient,false,effects);
        Damage finisher=definition.finisher()?damage(progress,stats,skill,parameters.get("finisher",0),false,effects):new Damage(0,0);
        Damage wave=new Damage(0,0);
        if(stats.weaponType.equals("SHORTSWORD")&&mastery(progress,stats)>0&&definition.shortSwordWave())
            wave=damage(progress,stats,skill,parameters.get("damage",0)*.2*mastery(progress,stats),true,effects);
        return new Preview(rank,count,hit,finisher,hit.times(count).plus(finisher),wave,skill.mp(Math.max(1,rank)),SkillCosts.resolve(skill,rank,stats,effects,CareerRules.magic(skill,progress)).cooldown(),parameters,effects);
    }
}
