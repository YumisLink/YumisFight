package cn.yumis.fight.modules.professions.api;
import cn.yumis.fight.modules.attributes.api.AttributeSources;
import cn.yumis.fight.modules.attributes.api.Stat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
/** 药剂配方表与增益实现；攻击/防御各只保留一个合剂，后饮覆盖前一个。 */
public final class AlchemyPotions {
    private AlchemyPotions(){}
    // 初级
    public static final String LIFE="potion_life",MANA="potion_mana",WISDOM="potion_wisdom",ENERGY="potion_energy";
    public static final String RESISTANCE="potion_resistance",TITAN="potion_titan",DEFENSE="potion_defense",GIANT="potion_giant";
    // 中级
    public static final String LIFE2="potion_life_2",MANA2="potion_mana_2",WISDOM2="potion_wisdom_2",ENERGY2="potion_energy_2";
    public static final String RESISTANCE2="potion_resistance_2",TITAN2="potion_titan_2",DEFENSE2="potion_defense_2",GIANT2="potion_giant_2";
    // 高级
    public static final String LIFE3="potion_life_3",MANA3="potion_mana_3",WISDOM3="potion_wisdom_3",ENERGY3="potion_energy_3";
    public static final String RESISTANCE3="potion_resistance_3",TITAN3="potion_titan_3",DEFENSE3="potion_defense_3",GIANT3="potion_giant_3";
    // 传说 / 史诗 / 神话
    public static final String LIFE4="potion_life_4",MANA4="potion_mana_4",WISDOM4="potion_wisdom_4",ENERGY4="potion_energy_4";
    public static final String RESISTANCE4="potion_resistance_4",TITAN4="potion_titan_4",DEFENSE4="potion_defense_4",GIANT4="potion_giant_4";
    public static final String LIFE5="potion_life_5",MANA5="potion_mana_5",WISDOM5="potion_wisdom_5",ENERGY5="potion_energy_5";
    public static final String RESISTANCE5="potion_resistance_5",TITAN5="potion_titan_5",DEFENSE5="potion_defense_5",GIANT5="potion_giant_5";
    public static final String LIFE6="potion_life_6",MANA6="potion_mana_6",WISDOM6="potion_wisdom_6",ENERGY6="potion_energy_6";
    public static final String RESISTANCE6="potion_resistance_6",TITAN6="potion_titan_6",DEFENSE6="potion_defense_6",GIANT6="potion_giant_6";
    public static final String BOTTLE_GLASS="minecraft:glass_bottle",BOTTLE_EMERALD="emerald_bottle",BOTTLE_AMETHYST="amethyst_bottle",BOTTLE_DIAMOND="diamond_bottle";
    private static final Map<String,PotionTier> TIERS=buildTiers();
    private static Map<String,PotionTier> buildTiers(){
        var result=new LinkedHashMap<String,PotionTier>();
        var groups=List.of(
            List.of(LIFE,MANA,WISDOM,ENERGY,RESISTANCE,TITAN,DEFENSE,GIANT),
            List.of(LIFE2,MANA2,WISDOM2,ENERGY2,RESISTANCE2,TITAN2,DEFENSE2,GIANT2),
            List.of(LIFE3,MANA3,WISDOM3,ENERGY3,RESISTANCE3,TITAN3,DEFENSE3,GIANT3),
            List.of(LIFE4,MANA4,WISDOM4,ENERGY4,RESISTANCE4,TITAN4,DEFENSE4,GIANT4),
            List.of(LIFE5,MANA5,WISDOM5,ENERGY5,RESISTANCE5,TITAN5,DEFENSE5,GIANT5),
            List.of(LIFE6,MANA6,WISDOM6,ENERGY6,RESISTANCE6,TITAN6,DEFENSE6,GIANT6));
        for(var tier:PotionTier.values())for(String id:groups.get(tier.ordinal()))result.put(id,tier);
        return java.util.Collections.unmodifiableMap(result);
    }
    public static Map<String,PotionTier> tiers(){return TIERS;}
    public static final int BOTTLE_MB=250;
    public static final long BUFF_MILLIS=3_600_000L;
    public static final int COOLDOWN_TICKS=1200;
    private static final String SOURCE="yumis_dungeon_and_fight:alchemy/";
    /** 规范化的投料组合 → 药剂 ID。 */
    private static final Map<String,String> RECIPES=new LinkedHashMap<>();
    /** 即时恢复：药剂 ID → 恢复量（生命或魔力）。 */
    private static final Map<String,Double> RESTORE=new LinkedHashMap<>();
    /** 百分比恢复：史诗 0.5、神话 1.0。 */
    private static final Map<String,Double> RESTORE_PCT=new LinkedHashMap<>();
    /** 合剂增益：药剂 ID → 属性加成。 */
    private static final Map<String,List<AttributeSources.Value>> BUFFS=new LinkedHashMap<>();
    /** 合剂类别：攻击类 true，防御类 false。 */
    private static final Map<String,Boolean> ATTACK=new LinkedHashMap<>();
    static{
        // 初级：普通草药
        for(int c=1;c<=4;c++){
            recipe(Map.of("herb_bruiseweed",c),LIFE);
            recipe(Map.of("herb_peacebloom",c),MANA);
        }
        recipe(Map.of("herb_peacebloom",2,"herb_silverleaf",2),WISDOM);
        recipe(Map.of("herb_peacebloom",2,"herb_silverleaf",1,"herb_kingsblood",1),ENERGY);
        recipe(Map.of("herb_peacebloom",1,"herb_silverleaf",1,"herb_earthroot",2),RESISTANCE);
        recipe(Map.of("herb_heather",2,"herb_bruiseweed",2),TITAN);
        recipe(Map.of("herb_heather",1,"herb_bruiseweed",2,"herb_earthroot",1),DEFENSE);
        recipe(Map.of("herb_heather",1,"herb_bruiseweed",1,"herb_earthroot",1,"herb_kingsblood",1),GIANT);
        // 中级：稀有草药
        for(int c=1;c<=4;c++){
            recipe(Map.of("herb_goldthorn",c),LIFE2);
            recipe(Map.of("herb_manathistle",c),MANA2);
        }
        recipe(Map.of("herb_manathistle",2,"herb_icebloom",2),WISDOM2);
        recipe(Map.of("herb_manathistle",2,"herb_icebloom",1,"herb_mushroom",1),ENERGY2);
        recipe(Map.of("herb_manathistle",1,"herb_icebloom",1,"herb_gravemoss",2),RESISTANCE2);
        recipe(Map.of("herb_goldthorn",2,"herb_blindweed",2),TITAN2);
        recipe(Map.of("herb_goldthorn",1,"herb_blindweed",2,"herb_mushroom",1),DEFENSE2);
        recipe(Map.of("herb_goldthorn",1,"herb_blindweed",1,"herb_mushroom",1,"herb_gravemoss",1),GIANT2);
        // 高级：神器草药
        for(int c=1;c<=4;c++){
            recipe(Map.of("herb_golden_lotus",c),LIFE3);
            recipe(Map.of("herb_snow_lily",c),MANA3);
        }
        recipe(Map.of("herb_snow_lily",2,"herb_snow_lotus",2),WISDOM3);
        recipe(Map.of("herb_snow_lily",2,"herb_snow_lotus",1,"herb_terocone",1),ENERGY3);
        recipe(Map.of("herb_snow_lily",1,"herb_snow_lotus",1,"herb_fire_thistle",2),RESISTANCE3);
        recipe(Map.of("herb_golden_lotus",2,"herb_terocone",2),TITAN3);
        recipe(Map.of("herb_golden_lotus",1,"herb_terocone",2,"herb_nightmare_vine",1),DEFENSE3);
        recipe(Map.of("herb_golden_lotus",1,"herb_nightmare_vine",1,"herb_fire_thistle",1,"herb_terocone",1),GIANT3);
        // 即时恢复
        RESTORE.put(LIFE,100d);RESTORE.put(MANA,100d);
        RESTORE.put(LIFE2,200d);RESTORE.put(MANA2,200d);
        RESTORE.put(LIFE3,400d);RESTORE.put(MANA3,400d);
        // 合剂
        buff(WISDOM,true,Stat.INTELLIGENCE,5);
        buff(ENERGY,true,Stat.MAGICAL_ATTACK,10);
        buff(TITAN,true,Stat.PHYSICAL_ATTACK,10);
        buff(GIANT,true,Stat.STRENGTH,5);
        buff(DEFENSE,false,Stat.PHYSICAL_DEFENSE,10);
        resist(RESISTANCE,5);
        buff(WISDOM2,true,Stat.INTELLIGENCE,10);
        buff(ENERGY2,true,Stat.MAGICAL_ATTACK,20);
        buff(TITAN2,true,Stat.PHYSICAL_ATTACK,20);
        buff(GIANT2,true,Stat.STRENGTH,10);
        buff(DEFENSE2,false,Stat.PHYSICAL_DEFENSE,20);
        resist(RESISTANCE2,10);
        buff(WISDOM3,true,Stat.INTELLIGENCE,20);
        buff(ENERGY3,true,Stat.MAGICAL_ATTACK,40);
        buff(TITAN3,true,Stat.PHYSICAL_ATTACK,40);
        buff(GIANT3,true,Stat.STRENGTH,20);
        buff(DEFENSE3,false,Stat.PHYSICAL_DEFENSE,40);
        resist(RESISTANCE3,40);
        // 传说（固定值）
        tier("herb_starlight_wood","herb_moonflower","herb_sirenflower","herb_anchor_coral","herb_saxifrage","herb_elemental_flower",
            LIFE4,MANA4,WISDOM4,ENERGY4,RESISTANCE4,TITAN4,DEFENSE4,GIANT4,1.0);
        // 史诗 +5% / 神话 +10%（百分比增益，抗性护甲为固定值）
        percentTier("herb_glowgrass","herb_rockmoss","herb_warped_bark","herb_deathbloom","herb_dragonblood_bamboo","herb_blackstone_grass",
            LIFE5,MANA5,WISDOM5,ENERGY5,RESISTANCE5,TITAN5,DEFENSE5,GIANT5,0.05,160);
        percentTier("herb_dreamflower","herb_stormvine","herb_voidflower","herb_doom_poppy","herb_fool_mushroom","herb_vortex_grass",
            LIFE6,MANA6,WISDOM6,ENERGY6,RESISTANCE6,TITAN6,DEFENSE6,GIANT6,0.10,320);
    }
    private static void recipe(Map<String,Integer> herbs,String potion){RECIPES.put(key(herbs),potion);}
    /** 一整档（8 种）药剂：a 生命 / b 魔力 / c 智慧 / d 泰坦 / e 抗性 / f 巨人。 */
    private static void tier(String a,String b,String c,String d,String e,String f,
            String life,String mana,String wisdom,String energy,String resist,String titan,String defense,String giant,double factor){
        for(int n=1;n<=4;n++){
            recipe(Map.of(a,n),life);
            recipe(Map.of(b,n),mana);
        }
        recipe(Map.of(b,2,c,2),wisdom);
        recipe(Map.of(b,2,c,1,d,1),energy);
        recipe(Map.of(b,1,c,1,e,2),resist);
        recipe(Map.of(a,2,d,2),titan);
        recipe(Map.of(a,1,d,2,f,1),defense);
        recipe(Map.of(a,1,f,1,e,1,d,1),giant);
        RESTORE.put(life,800*factor);
        RESTORE.put(mana,800*factor);
        buff(wisdom,true,Stat.INTELLIGENCE,40*factor);
        buff(energy,true,Stat.MAGICAL_ATTACK,80*factor);
        buff(titan,true,Stat.PHYSICAL_ATTACK,80*factor);
        buff(giant,true,Stat.STRENGTH,40*factor);
        buff(defense,false,Stat.PHYSICAL_DEFENSE,80*factor);
        resist(resist,80*factor);
    }
    /** 百分比档位（史诗/神话）：生命魔力按上限百分比恢复，属性按百分比提升，抗性护甲为固定值。 */
    private static void percentTier(String a,String b,String c,String d,String e,String f,
            String life,String mana,String wisdom,String energy,String resist,String titan,String defense,String giant,double pct,double flat){
        for(int n=1;n<=4;n++){
            recipe(Map.of(a,n),life);
            recipe(Map.of(b,n),mana);
        }
        recipe(Map.of(b,2,c,2),wisdom);
        recipe(Map.of(b,2,c,1,d,1),energy);
        recipe(Map.of(b,1,c,1,e,2),resist);
        recipe(Map.of(a,2,d,2),titan);
        recipe(Map.of(a,1,d,2,f,1),defense);
        recipe(Map.of(a,1,f,1,e,1,d,1),giant);
        RESTORE_PCT.put(life,pct);
        RESTORE_PCT.put(mana,pct);
        ratioBuff(wisdom,true,Stat.INTELLIGENCE,pct);
        ratioBuff(energy,true,Stat.MAGICAL_ATTACK,pct);
        ratioBuff(titan,true,Stat.PHYSICAL_ATTACK,pct);
        ratioBuff(giant,true,Stat.STRENGTH,pct);
        flatBuff(defense,false,Stat.PHYSICAL_DEFENSE,flat);
        flatResist(resist,flat);
    }
    private static void ratioBuff(String potion,boolean attack,Stat stat,double ratio){
        BUFFS.put(potion,List.of(new AttributeSources.Value(stat,ratio,AttributeSources.Operation.BASE_RATIO,AttributeSources.Stage.CHARACTER)));
        ATTACK.put(potion,attack);
    }
    private static void flatBuff(String potion,boolean attack,Stat stat,double amount){
        BUFFS.put(potion,List.of(value(stat,amount)));
        ATTACK.put(potion,attack);
    }
    private static void flatResist(String potion,double amount){
        BUFFS.put(potion,List.of(value(Stat.LIGHT_RESIST,amount),value(Stat.FIRE_RESIST,amount),value(Stat.ICE_RESIST,amount),value(Stat.DARK_RESIST,amount)));
        ATTACK.put(potion,false);
    }
    /** 药液需要哪种瓶子取药。 */
    public static String bottleFor(String potion){
        var tier=TIERS.get(potion);if(tier==null)return BOTTLE_GLASS;
        return switch(tier){case LEGENDARY->BOTTLE_EMERALD;case EPIC->BOTTLE_AMETHYST;case MYTHIC->BOTTLE_DIAMOND;default->BOTTLE_GLASS;};
    }
    private static void buff(String potion,boolean attack,Stat stat,double amount){
        BUFFS.put(potion,List.of(value(stat,amount)));
        ATTACK.put(potion,attack);
    }
    private static void resist(String potion,double amount){
        BUFFS.put(potion,List.of(value(Stat.LIGHT_RESIST,amount),value(Stat.FIRE_RESIST,amount),value(Stat.ICE_RESIST,amount),value(Stat.DARK_RESIST,amount)));
        ATTACK.put(potion,false);
    }
    public static String key(Map<String,Integer> herbs){
        List<String> parts=new ArrayList<>();
        herbs.forEach((id,count)->{if(count>0)parts.add(id+"*"+count);});
        parts.sort(String::compareTo);
        return String.join(",",parts);
    }
    public static String find(Map<String,Integer> herbs){return RECIPES.get(key(herbs));}
    public static boolean isRestore(String potion){return RESTORE.containsKey(potion)||RESTORE_PCT.containsKey(potion);}
    public static boolean isPercentRestore(String potion){return RESTORE_PCT.containsKey(potion);}
    public static double percentRestore(String potion){return RESTORE_PCT.getOrDefault(potion,0d);}
    public static double restoreAmount(String potion){return RESTORE.getOrDefault(potion,0d);}
    public static boolean isMana(String potion){return potion.startsWith("potion_mana");}
    public static boolean isBuff(String potion){return BUFFS.containsKey(potion);}
    /** 写入或覆盖该类增益。 */
    public static void applyBuff(AttributeSources sources,String potion){
        List<AttributeSources.Value> values=BUFFS.get(potion);
        if(values==null)return;
        String category=ATTACK.getOrDefault(potion,true)?"attack":"defense";
        String id=SOURCE+category;
        synchronized(sources){
            var before=sources.snapshot();
            long revision=Math.incrementExact(sources.revision());
            var next=new ArrayList<>(before.stream().filter(s->!s.id().startsWith(SOURCE)).toList());
            next.add(new AttributeSources.Source(id,revision,List.copyOf(values)));
            sources.replace(revision,next);
        }
    }
    public static String category(String potion){return ATTACK.getOrDefault(potion,true)?"attack":"defense";}
    /** 沿用现有整组清除规则。 */
    public static void clearBuffs(AttributeSources sources){
        synchronized(sources){
            var before=sources.snapshot();
            if(before.stream().noneMatch(s->s.id().startsWith(SOURCE)))return;
            long revision=Math.incrementExact(sources.revision());
            sources.replace(revision,new ArrayList<>(before.stream().filter(s->!s.id().startsWith(SOURCE)).toList()));
        }
    }
    private static AttributeSources.Value value(Stat stat,double amount){
        return new AttributeSources.Value(stat,amount,AttributeSources.Operation.ADD,AttributeSources.Stage.CHARACTER);
    }
}
