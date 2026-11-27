package cn.yumis.fight.modules.skills.api;
import cn.yumis.fight.modules.character.api.JobCatalog;

import java.util.*;
/** Skill registration is application content. New definitions do not change dispatch or architecture. */
public final class SkillCatalog {
    private static final Map<String,SkillDefinition> DEFINITIONS=new LinkedHashMap<>();
    public static final SkillDefinition GHOST=register(new SkillDefinition("yumis_dungeon_and_fight:ghost","鬼斩","GHOST","",1,15,.06,6,1.6,false,parameters(3.8,1,0,.2,.8))).formParameter("chargeSeconds",1).formParameter("chargeMultiplier",2).formParameter("maxChargeSeconds",3);
    public static final SkillDefinition TRIPLE=register(new SkillDefinition("yumis_dungeon_and_fight:triple","三段斩","SWORD","",5,15,.08,8,.8,false,parameters(3.5,3,0,.2,.8))).formParameter("dashDistance",3.0).formParameter("dashSeconds",.2);
    public static final SkillDefinition WAVE=register(new SkillDefinition("yumis_dungeon_and_fight:wave","地裂波动剑","WAVE","",10,15,.06,5,2,false,parameters(12,1,0,.2,.8)));
    public static final SkillDefinition MASTERY=register(new SkillDefinition("yumis_dungeon_and_fight:mastery","武器精通","SWORD",JobCatalog.SWORDMASTER,20,30,0,0,0,true,parameters(3,1,0,.2,.8)));
    public static final SkillDefinition OVERDRIVE=register(new SkillDefinition("yumis_dungeon_and_fight:overdrive","破极兵刃","SWORD",JobCatalog.SWORDMASTER,20,30,.10,40,.15,false,parameters(3,1,0,.2,.8)));
    public static final SkillDefinition RISING=register(new SkillDefinition("yumis_dungeon_and_fight:rising","破军升龙击","SWORD",JobCatalog.SWORDMASTER,25,40,.12,12,1.6,false,parameters(3.6,1,2.4,.2,.8))).formParameter("dashDistance",4.5).formParameter("dashSeconds",.3);
    public static final SkillDefinition DRAW=register(new SkillDefinition("yumis_dungeon_and_fight:draw","拔刀斩","SWORD",JobCatalog.SWORDMASTER,30,50,.14,15,6.5,false,parameters(6,1,0,.2,.8))).formParameter("drawSeconds",.4);
    public static final SkillDefinition DRAGON=register(new SkillDefinition("yumis_dungeon_and_fight:dragon","猛龙断空斩","SWORD",JobCatalog.SWORDMASTER,35,50,.18,22,2.2,false,parameters(3.6,4,0,.2,.8))).formParameter("dashDistance",4.8).formParameter("dashSeconds",.25);
    public static final SkillDefinition DANCE=register(new SkillDefinition("yumis_dungeon_and_fight:dance","幻影剑舞","SWORD",JobCatalog.SWORDMASTER,40,60,.22,30,.7,false,parameters(4.8,10,5,.125,1.25))).formParameter("finisherRange",10);
    public static final SkillDefinition STORM=register(new SkillDefinition("yumis_dungeon_and_fight:storm","极鬼剑术·暴风式","SWORD",JobCatalog.SWORDMASTER,50,100,.35,90,1,false,parameters(6.5,StormChoreography.SLASHES,8,StormChoreography.seconds(StormChoreography.INTERVAL),StormChoreography.seconds(StormChoreography.END))))
        .formParameter("stormInterval",StormChoreography.seconds(StormChoreography.INTERVAL))
        .formParameter("stormDeploy",StormChoreography.seconds(StormChoreography.DEPLOY));
    public static final SkillDefinition METEOR_RISE=cardGranted(new SkillDefinition("yumis_dungeon_and_fight:meteor_rise","流星·升","SWORD",JobCatalog.SWORDMASTER,20,1,.08,6,7.475,false,parameters(4.2,1,8.775,.2,.55)).formParameter("dashDistance",2.4).formParameter("dashSeconds",.22));
    public static final SkillDefinition METEOR_LEAP=cardGranted(new SkillDefinition("yumis_dungeon_and_fight:meteor_leap","流星·跃","SWORD",JobCatalog.SWORDMASTER,20,1,.09,7,5.85,false,parameters(4.4,1,12.025,.2,.65)).formParameter("dashDistance",6.5).formParameter("dashSeconds",.28));
    public static final SkillDefinition METEOR_PIERCE=cardGranted(new SkillDefinition("yumis_dungeon_and_fight:meteor_pierce","流星·刺","SWORD",JobCatalog.SWORDMASTER,20,1,.08,5.5,5.46,false,parameters(5.4,3,0,.12,.45)).formParameter("dashDistance",3.2).formParameter("dashSeconds",.22));
    public static final SkillDefinition BLINK=equipmentBlink();
    private static SkillDefinition cardGranted(SkillDefinition skill){skill.cardGranted=true;return register(skill);}
    private static SkillDefinition equipmentBlink(){var skill=new SkillDefinition(cn.yumis.fight.modules.equipment.api.EquipmentEffects.BLINK,"闪现","EQUIPMENT_MAGIC","",1,1,.2,8,0,false,parameters(5,0,0,0,0));skill.equipmentGranted=true;skill.magicTag=true;skill.cooldownPerRank=.1;return register(skill);}
    static {CareerSkills.install();MageSkills.install();SummonerSkills.install();SunMoonSkills.install();ElementalistSkills.install();BattleMageSkills.install();WitchSkills.install();OracleSkills.install();DemonHunterSkills.install();MageSkills.variants();SummonerSkills.variants();SunMoonSkills.variants();ElementalistSkills.variants();BattleMageSkills.variants();WitchSkills.variants();OracleSkills.variants();DemonHunterSkills.variants();AbyssChampionSkills.install();SkillBlueprints.install();}
    private SkillCatalog(){}
    private static Map<String,Double> parameters(double range,double slashes,double finisher,double interval,double duration){return Map.of("range",range,"slashes",slashes,"finisher",finisher,"interval",interval,"duration",duration);}
    public static synchronized SkillDefinition register(SkillDefinition definition){if(DEFINITIONS.putIfAbsent(definition.id,definition)!=null)throw new IllegalArgumentException("Duplicate skill: "+definition.id);return definition;}
    public static synchronized SkillDefinition require(String id){var value=DEFINITIONS.get(id);if(value==null)throw new IllegalArgumentException("未知技能："+id);return value;}
    public static synchronized Collection<SkillDefinition> all(){return List.copyOf(DEFINITIONS.values());}
    public static synchronized boolean contains(String id){return DEFINITIONS.containsKey(id);}
}
