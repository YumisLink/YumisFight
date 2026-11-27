package cn.yumis.fight.modules.skills.api;
import cn.yumis.fight.modules.attributes.api.Stat;
import cn.yumis.fight.modules.dungeons.api.TowerBattleCard;
import cn.yumis.fight.modules.equipment.api.WeaponTraits;
import java.util.*;
import cn.yumis.fight.modules.character.api.Progress;
import cn.yumis.fight.modules.attributes.api.Stats;
import cn.yumis.fight.modules.combat.api.BasicAttackProfile;
public final class WeaponSkills {
    private static final double BOSS_CONTROL_RATIO = 1.0 / 12.0;
    private WeaponSkills(){}
    public static boolean mastery(Progress p,Stats s){return p.hasJob(cn.yumis.fight.modules.character.api.JobCatalog.SWORDMASTER)&&SkillLearning.effective(s,p,SkillCatalog.MASTERY)>0;}
    public static double interval(Progress p,Stats s){
        double interval=650/Math.max(.01,1+s.get(Stat.ATTACK_SPEED)/100);
        double greatScale=mastery(p,s)&&s.weaponType.equals("GREATSWORD")
            ?(TowerBattleCard.SM_MASTERY_OVERLOAD.held(s)?2.0/(1.0+TowerBattleCard.SM_MASTERY_OVERLOAD.ratio("great")):2.0)
            :1.0;
        return Math.max(50,interval*greatScale);
    }
    public static double greatswordChargeMillis(Stats s){
        return TowerBattleCard.SM_MASTERY_OVERLOAD.held(s)
            ?800.0/(1.0+TowerBattleCard.SM_MASTERY_OVERLOAD.ratio("great"))
            :800.0;
    }
    public static double bluntStunChance(Stats s){
        return TowerBattleCard.SM_MASTERY_OVERLOAD.held(s)?TowerBattleCard.SM_MASTERY_OVERLOAD.ratio("stun"):0.05;
    }
    public static int bluntStunTicks(double masteryGrowth,boolean boss,boolean elite){
        double scale=boss?BOSS_CONTROL_RATIO:elite?0.5:1.0;
        return Math.max(1,(int)Math.ceil(10.0*masteryGrowth*scale));
    }
    public static double heavyBleedTickFraction(double currentLifeFraction,boolean boss){
        double total=TowerBattleCard.SM_MASTERY_OVERLOAD.ratio("bleed")*(boss?BOSS_CONTROL_RATIO:1.0);
        int ticks=Math.max(1,TowerBattleCard.SM_MASTERY_OVERLOAD.count("sec"));
        return Math.max(0.0,currentLifeFraction)*total/ticks;
    }
    public static int heavyBleedTicks(){
        return Math.max(1,TowerBattleCard.SM_MASTERY_OVERLOAD.count("sec"));
    }
    public static double shortSwordWaveDamageFactor(Stats s){
        return TowerBattleCard.SM_MASTERY_OVERLOAD.held(s)?1.0+TowerBattleCard.SM_MASTERY_OVERLOAD.ratio("short"):1.0;
    }
    public static boolean giantShortSwordWave(Stats s){
        return TowerBattleCard.SM_MASTERY_OVERLOAD.held(s);
    }
    public static int drawBarrageCount(Stats s){
        boolean fast=s!=null&&Set.of("LIGHTSABER","KATANA").contains(s.weaponType);
        return fast?TowerBattleCard.SM_DRAW_BARRAGE.count("fast"):TowerBattleCard.SM_DRAW_BARRAGE.count("base");
    }
    public static double drawBarrageDamageRatio(){
        return TowerBattleCard.SM_DRAW_BARRAGE.ratio("damage");
    }
    public static double drawArcWaveDamageRatio(){
        return TowerBattleCard.SM_DRAW_ARC_WAVE.ratio("damage");
    }
    public static double drawArcWaveDistance(){
        return TowerBattleCard.SM_DRAW_ARC_WAVE.value("distance");
    }
    public static double dragonWhirlwindDamageRatio(){
        return TowerBattleCard.SM_DRAGON_WHIRLWIND.ratio("damage");
    }
    public static double risingDragonSlayRushCoeff(double baseDamage){return baseDamage*0.60;}
    public static double risingDragonSlayUpperCoeff(double baseDamage){return baseDamage*0.70;}
    public static double risingDragonSlaySlamCoeff(double baseFinisher){return baseFinisher*(5.0/3.0);}
    public static String skillTitle(SkillDefinition skill,Stats stats){
        if(skill==SkillCatalog.RISING&&TowerBattleCard.SM_RISING_DRAGON_SLAY.held(stats))return "破军斩龙击";
        return skill.title;
    }
    public static SkillParameters.Snapshot skill(SkillParameters.Snapshot input,Progress p,Stats s){
        Map<String,Double> values=new LinkedHashMap<>(input.values());
        values.put("meleeScale",WeaponTraits.sword(s.weaponType)&&input.skill()!=SkillCatalog.WAVE&&input.skill()!=SkillCatalog.STORM?1.5:1);
        if(mastery(p,s)){
            if(input.skill()==SkillCatalog.TRIPLE)values.merge("slashes",s.weaponType.equals("KATANA")||s.weaponType.equals("LIGHTSABER")?4d:2d,Double::sum);
            if(input.skill()==SkillCatalog.DRAGON&&s.weaponType.equals("LIGHTSABER"))values.merge("slashes",1d,Double::sum);
            if(input.skill()==SkillCatalog.DANCE&&(s.weaponType.equals("KATANA")||s.weaponType.equals("LIGHTSABER")))values.merge("slashes",10d,Double::sum);
        }
        if(input.skill()==SkillCatalog.DRAGON&&TowerBattleCard.SM_DRAGON_SINGLE.held(s))values.put("slashes",1d);
        return AsuraRules.damage(SkillParameters.applyVariant(new SkillParameters.Snapshot(input.skill(),input.rank(),values),p,s),p);
    }
    public static BasicAttackProfile basicAttack(Progress progress,Stats stats){
        int lightsaberSlashes=TowerBattleCard.SM_MASTERY_OVERLOAD.held(stats)?TowerBattleCard.SM_MASTERY_OVERLOAD.count("saber"):2;
        return new BasicAttackProfile(interval(progress,stats),Math.max((int)(double)stats.modifiers.getOrDefault("basicSlashes",1d),mastery(progress,stats)&&stats.weaponType.equals("LIGHTSABER")?lightsaberSlashes:1),SkillBalanceRules.current().basicScale(progress.jobId(),stats.weaponType,stats.modifiers.getOrDefault("careerFrenzy",0d)>0,progress.level),stats.modifiers.getOrDefault("basicContactOffset",0d));
    }
    public static Stats basicSnapshot(Progress progress,Stats stats){var profile=basicAttack(progress,stats);Stats result=CareerRules.eyeDamage(stats,null).copy();result.modifiers.put("basicDamageScale",profile.damageScale());return result;}
}
