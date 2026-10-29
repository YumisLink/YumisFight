package cn.yumis.fight.modules.equipment.api;
import cn.yumis.fight.modules.affixes.api.*;
import cn.yumis.fight.modules.attributes.api.*;
import java.util.*;
/** Equipment-owned fixed mechanics. These values deliberately do not use AttributeValue. */
public final class EquipmentEffects {
    public static final String BLINK="yumis_dungeon_and_fight:blink",CHASER="yumis_dungeon_and_fight:chaser_possession";
    public static final class Snapshot {
        public double gemAmplification,fireAfterDamage,fireConversion,manaShare,lifeCostRatio,killRecovery,magicCooldownReduction,heroDuration;
        public double resistanceCap=Double.MAX_VALUE;
        public int magicSkillRanks;
        public boolean fireOnly,blockElemental,inverseResists;
        public String goldenSource="",imprintSource="",heroSource="";
        public Map<String,Integer> grants=new LinkedHashMap<>();
        public Map<String,String> grantSources=new LinkedHashMap<>();
        public Snapshot copy(){
            Snapshot copy=new Snapshot();copy.gemAmplification=gemAmplification;copy.fireAfterDamage=fireAfterDamage;copy.fireConversion=fireConversion;copy.manaShare=manaShare;copy.lifeCostRatio=lifeCostRatio;copy.killRecovery=killRecovery;copy.magicCooldownReduction=magicCooldownReduction;copy.heroDuration=heroDuration;copy.resistanceCap=resistanceCap;copy.magicSkillRanks=magicSkillRanks;copy.fireOnly=fireOnly;copy.blockElemental=blockElemental;copy.inverseResists=inverseResists;copy.goldenSource=goldenSource;copy.imprintSource=imprintSource;copy.heroSource=heroSource;copy.grants.putAll(grants);copy.grantSources.putAll(grantSources);return copy;
        }
    }
    @FunctionalInterface public interface Rule {void apply(Snapshot target,Affix affix,Gear source);}
    private static final Map<String,Rule> RULES=new LinkedHashMap<>();
    static {
        register("gem_amplification",(s,a,g)->s.gemAmplification=Math.max(s.gemAmplification,a.value));
        register("fire_after_damage",(s,a,g)->s.fireAfterDamage+=a.value);
        register("fire_conversion",(s,a,g)->s.fireConversion=Math.max(s.fireConversion,a.value));
        register("fire_only",(s,a,g)->s.fireOnly=true);
        register("block_elemental",(s,a,g)->s.blockElemental=true);
        register("inverse_resists",(s,a,g)->s.inverseResists=true);
        register("resistance_cap",(s,a,g)->s.resistanceCap=Math.min(s.resistanceCap,a.value));
        register("mana_share",(s,a,g)->s.manaShare+=a.value);
        register("life_cost",(s,a,g)->s.lifeCostRatio=Math.max(s.lifeCostRatio,a.value));
        register("kill_recovery",(s,a,g)->s.killRecovery+=a.value);
        register("magic_cooldown",(s,a,g)->s.magicCooldownReduction+=a.value);
        register("magic_ranks",(s,a,g)->s.magicSkillRanks+=(int)Math.round(a.value));
        register("golden_power",(s,a,g)->s.goldenSource=g.instance.toString());
        register("imprint_power",(s,a,g)->s.imprintSource=g.instance.toString());
        register("hero_power",(s,a,g)->{s.heroSource=g.instance.toString();s.heroDuration=Math.max(s.heroDuration,a.value);});
        register("grant_blink",(s,a,g)->{s.grants.merge(BLINK,Math.max(1,g.level/5),Math::max);s.grantSources.put(BLINK,g.instance.toString());});
    }
    private EquipmentEffects(){}
    public static void register(String key,Rule rule){if(RULES.putIfAbsent(key,rule)!=null)throw new IllegalArgumentException("重复装备效果："+key);}
    public static Snapshot resolve(cn.yumis.fight.modules.character.api.Progress progress,List<Gear> gear,int allowance){
        Snapshot result=new Snapshot();
        for(Gear input:gear){Gear g=EquipmentResolution.resolve(input);if(!g.valid(progress,allowance))continue;
            for(Affix a:effectiveAffixes(g))if(a.operation.equals("EQUIPMENT_RULE")){var rule=RULES.get(a.parameter);if(rule!=null)rule.apply(result,a,g);}
        }
        return result;
    }
    public static double gemMultiplier(Gear gear){return 1+gear.affixes.stream().filter(a->a.operation.equals("EQUIPMENT_RULE")&&a.parameter.equals("gem_amplification")).mapToDouble(a->a.value).max().orElse(0);}
    public static List<Affix> effectiveAffixes(Gear gear){
        List<Affix> result=new ArrayList<>(gear.affixes);double factor=gemMultiplier(gear);
        for(Gem gem:gear.gems)for(Affix original:gem.affixes){Affix a=EquipmentJson.copy(original,Affix.class);
            if(Set.of("FLAT","PERCENT","CHARACTER_PERCENT","ALL_RESIST_FLAT","EXTRA").contains(a.operation)){a.value*=factor;a.maximum*=factor;}
            result.add(a);
        }
        return result;
    }
    public static void finish(Stats stats,Snapshot effects){
        if(effects.fireConversion!=0){double converted=stats.get(Stat.LIGHT)+stats.get(Stat.ICE)+stats.get(Stat.DARK);stats.values.put(Stat.FIRE,stats.get(Stat.FIRE)+converted*effects.fireConversion);for(Stat s:List.of(Stat.LIGHT,Stat.ICE,Stat.DARK))stats.values.put(s,0d);}
        if(effects.resistanceCap!=Double.MAX_VALUE)for(Stat s:List.of(Stat.LIGHT_RESIST,Stat.FIRE_RESIST,Stat.ICE_RESIST,Stat.DARK_RESIST))stats.values.put(s,Math.min(stats.get(s),effects.resistanceCap));
        if(effects.fireOnly)stats.extras.removeIf(a->a.element!=1);
        if(effects.blockElemental)stats.extras.clear();
        effects.grants.forEach((id,rank)->stats.ranks.merge("grant/"+id,rank,Math::max));
        if(effects.magicSkillRanks!=0)stats.ranks.put("tag/magic",effects.magicSkillRanks);
    }
}
