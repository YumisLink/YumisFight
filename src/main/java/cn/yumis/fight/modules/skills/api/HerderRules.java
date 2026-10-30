package cn.yumis.fight.modules.skills.api;
import cn.yumis.fight.modules.attributes.api.*;
import cn.yumis.fight.modules.combat.api.*;
import java.util.*;
/** Summon inheritance composes the existing attribute and combat formulas. */
public final class HerderRules {
    private HerderRules(){}
    public static Stats inherit(Stats summoner){
        var result=summoner.copy();for(Stat stat:List.of(Stat.HEALTH,Stat.PHYSICAL_DEFENSE,Stat.MAGICAL_DEFENSE))result.values.put(stat,result.get(stat)*2);
        for(Stat stat:List.of(Stat.LIGHT_RESIST,Stat.FIRE_RESIST,Stat.ICE_RESIST,Stat.DARK_RESIST,Stat.EVASION))result.values.put(stat,result.get(stat)*1.5);
        result.values.put(Stat.LIFE_STEAL,0d);return result;
    }
    /** Bake the summoner panel and action coefficient once; target mitigation and crit remain per hit. */
    public static Stats attack(Stats inherited,double coefficient){var stats=inherited.copy();stats.values.put(Stat.MAGICAL_ATTACK,inherited.panel(true)*coefficient);stats.values.put(Stat.INTELLIGENCE,0d);for(var extra:stats.extras){extra.value*=coefficient;extra.maximum*=coefficient;}return stats;}
    public static double incoming(Stats snapshot,double points,boolean magic,int element,int attackerLevel,int ownerLevel,double protection){
        var attacker=new Stats();attacker.values.put(magic?Stat.MAGICAL_ATTACK:Stat.PHYSICAL_ATTACK,points);double[] resists={snapshot.get(Stat.LIGHT_RESIST),snapshot.get(Stat.FIRE_RESIST),snapshot.get(Stat.ICE_RESIST),snapshot.get(Stat.DARK_RESIST)};
        return CombatRules.calculate(attacker,snapshot.get(magic?Stat.MAGICAL_DEFENSE:Stat.PHYSICAL_DEFENSE),resists,magic,element<0?new int[0]:new int[]{element},1,false,new Random(0)).total()*LevelAdvantage.advantage(attackerLevel,ownerLevel)*protection;
    }
}
