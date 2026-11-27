package cn.yumis.fight.modules.skills.api;

import cn.yumis.fight.modules.attributes.api.Stats;
import cn.yumis.fight.modules.character.api.JobCatalog;
import cn.yumis.fight.modules.character.api.Progress;
import cn.yumis.fight.modules.dungeons.api.TowerBattleCard;
import java.util.LinkedHashMap;
import java.util.List;

/** Soul-bender variants of shared ghost-swordsman skills and career mythic math. */
public final class SoulBenderRules {
    private static final double RED_FIELD_MULTIPLIER=(1+133*1.18/100)/(1+133d/100);
    private SoulBenderRules(){}
    static void install(){
        SkillParameters.registerVariant(SkillCatalog.GHOST,SoulBenderRules::ghost);
        for(var skill:List.of(SkillCatalog.TRIPLE,SkillCatalog.WAVE))
            SkillParameters.registerVariant(skill,(input,progress,stats)->damage(input,progress));
    }
    static void previews(){
        SkillDefinition moonlight=SkillCatalog.require(CareerSkills.MOONLIGHT);
        SkillPreviewRules.total(moonlight,(progress,stats,parameters,effects)->{
            int count=moonlightSegments(parameters);
            boolean wave=TowerBattleCard.SB_MOON_WAVE.held(stats);
            SkillPreview.Damage sum=new SkillPreview.Damage(0,0);
            for(int seg=1;seg<=count;seg++){
                double coeff=moonlightSegmentCoefficient(parameters,stats,seg);
                sum=sum.plus(SkillPreview.damage(progress,stats,moonlight,coeff,false,effects));
                if(wave)sum=sum.plus(SkillPreview.damage(progress,stats,moonlight,moonlightWaveCoefficient(parameters,stats,seg),false,effects));
            }
            return sum;
        });
        SkillDefinition phantom=SkillCatalog.require(CareerSkills.PHANTOM);
        SkillPreviewRules.supplement(phantom,(progress,stats,parameters,effects)->{
            if(!TowerBattleCard.SB_PHANTOM_RIFT.held(stats))return new SkillPreview.Damage(0,0);
            int slashes=(int)Math.max(1,Math.round(parameters.get("slashes",1)));
            return SkillPreview.damage(progress,stats,phantom,phantomRiftBurstCoefficient(parameters),false,effects).times(slashes);
        });
        SkillPreviewRules.supplement(SkillCatalog.GHOST,(progress,stats,parameters,effects)->{
            if(!TowerBattleCard.SB_INSTANT_SOUL.held(stats)&&!TowerBattleCard.SB_HELL_SLASH.held(stats))return new SkillPreview.Damage(0,0);
            double full=ghostFullChargeCoefficient(parameters);
            SkillPreview.Damage extra=new SkillPreview.Damage(0,0);
            if(TowerBattleCard.SB_HELL_SLASH.held(stats)&&parameters.get("soulBonus",0)>0){
                extra=extra.plus(SkillPreview.damage(progress,stats,SkillCatalog.GHOST,hellSlashHitCoefficient(full),false,effects).times(hellSlashHits()));
            }
            return extra;
        });
    }
    public static int moonlightSegments(SkillParameters.Snapshot parameters){
        return (int)Math.max(1,Math.round(parameters.get("slashes",3)));
    }
    public static boolean moonlightFullMoon(int segment){
        return segment>0&&segment%3==0;
    }
    public static double moonlightSegmentCoefficient(SkillParameters.Snapshot parameters,Stats stats,int segment){
        double base=moonlightFullMoon(segment)?parameters.get("finisher",10):parameters.get("damage",6);
        if(TowerBattleCard.SB_MOON_COMBO.held(stats)){
            base*=1d+Math.max(0,segment-1)*TowerBattleCard.SB_MOON_COMBO.ratio("ramp");
        }
        return base;
    }
    public static double moonlightWaveCoefficient(SkillParameters.Snapshot parameters,Stats stats,int segment){
        return moonlightSegmentCoefficient(parameters,stats,segment)*TowerBattleCard.SB_MOON_WAVE.ratio("damage");
    }
    public static double ghostFullChargeCoefficient(SkillParameters.Snapshot parameters){
        double base=parameters.get("damage",1.6)*Math.max(1,parameters.get("chargeMultiplier",2))+parameters.get("soulBonus",0);
        return base*parameters.get("ghostFieldMultiplier",1);
    }
    public static int hellSlashHits(){
        return Math.max(1,TowerBattleCard.SB_HELL_SLASH.count("hits"));
    }
    public static double hellSlashRange(){
        return Math.max(1d,TowerBattleCard.SB_HELL_SLASH.value("range"));
    }
    public static double hellSlashHitCoefficient(double fullChargeCoefficient){
        return fullChargeCoefficient*TowerBattleCard.SB_HELL_SLASH.ratio("damage");
    }
    public static double phantomRiftBurstCoefficient(SkillParameters.Snapshot parameters){
        return parameters.get("damage",0)*TowerBattleCard.SB_PHANTOM_RIFT.ratio("damage");
    }
    public static int blacheEffectiveFieldRank(int activeFieldRank,int learnedFieldRank,Stats stats){
        return TowerBattleCard.SB_BLACHE_VOID.held(stats)?Math.max(activeFieldRank,learnedFieldRank):activeFieldRank;
    }
    private static SkillParameters.Snapshot ghost(SkillParameters.Snapshot input,Progress progress,Stats stats){
        var values=new LinkedHashMap<>(input.values());var skill=SkillCatalog.require(CareerSkills.SOUL_SLASH);int rank=SkillLearning.effective(stats,progress,skill);
        if(rank>0){values.put("soulBonus",SkillParameters.resolve(skill,rank,stats.parameters).get("damage",0));if(stats.modifiers.getOrDefault("ownGhostField",0d)>0)values.put("ghostFieldMultiplier",2/RED_FIELD_MULTIPLIER);}
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
