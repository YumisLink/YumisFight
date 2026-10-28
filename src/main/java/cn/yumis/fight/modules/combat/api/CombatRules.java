package cn.yumis.fight.modules.combat.api;
import cn.yumis.fight.modules.attributes.api.Stat;
import static cn.yumis.fight.modules.attributes.api.AttributeNumbers.finite;

import cn.yumis.fight.modules.attributes.api.ExtraDamage;
import cn.yumis.fight.modules.attributes.api.Stats;

import static cn.yumis.fight.modules.combat.api.LifeRules.*;
import java.util.*;
public final class CombatRules {
    public static double evasionChance(double rating){
        finite(rating);
        double value=Math.max(0,rating);
        if(value>30000)value=30000+(value-30000)/3;
        return Math.min(.9,value/(10000+value));
    }
    public static boolean evades(double rating,java.util.function.DoubleSupplier roll){return rating>0&&roll.getAsDouble()<evasionChance(rating);}
    public record Damage(double main,double extra,int element){
        public double total(){
            return main+extra;
        }
    }
    public static Damage calculate(Stats attacker,double defense,double[] resist,boolean magic,int[] elements,double coefficient,boolean includeExtras,Random random){
        return calculateResolved(attacker,defense,resist,magic,elements,coefficient,includeExtras,random::nextDouble,true);
    }
    /** Exact endpoints of the noncritical, zero-defense panel range; does not access combat RNG. */
    public static Damage panel(Stats attacker,boolean magic,int[] elements,double coefficient,boolean includeExtras,boolean maximum){
        return calculateResolved(attacker,0,new double[4],magic,elements,coefficient,includeExtras,()->maximum?1:0,false);
    }
    private static Damage calculateResolved(Stats attacker,double defense,double[] resist,boolean magic,int[] elements,double coefficient,boolean includeExtras,java.util.function.DoubleSupplier roll,boolean criticalEnabled){
        int element=-1;
        double delta=-Math.max(0,Arrays.stream(resist).max().orElse(0));
        if(elements.length>0){
            delta=-Double.MAX_VALUE;
            for(int e:elements){
                double difference=attacker.get(Stat.values()[Stat.LIGHT.ordinal()+e])-resist[e];
                if(difference>delta){
                    delta=difference;
                    element=e;
                }
            }
        }
        double mitigation=1000/(1000+Math.max(0,defense));
        double main=attacker.panel(magic)*coefficient*mitigation*Math.max(0,1+delta/120),extra=0;
        if(includeExtras)for(ExtraDamage a:attacker.extras){
            double base=a.value+roll.getAsDouble()*Math.max(0,a.maximum-a.value);
            double difference=attacker.get(Stat.values()[Stat.LIGHT.ordinal()+a.element])-resist[a.element];
            extra+=base*mitigation*Math.max(0,1+difference/120);
        }
        double multiplier=(1+attacker.modifiers.getOrDefault("final",0d))*attacker.modifiers.getOrDefault("skillProduct",1d);
        if(criticalEnabled&&roll.getAsDouble()<attacker.modifiers.getOrDefault("crit",0d))multiplier*=1.5+attacker.modifiers.getOrDefault("critDamage",0d);
        return new Damage(main*multiplier,extra*multiplier,element);
    }
    private CombatRules(){
    }
    public static double damage(Stats attacker,double defense,double[] resist,boolean magic,int[] elements,double coefficient,Random random){
        double max=Arrays.stream(resist).max().orElse(0);
        double delta=-Math.max(0,max);
        if(elements.length>0){
            delta=-Double.MAX_VALUE;
            for(int e:elements)delta=Math.max(delta,attacker.get(Stat.values()[Stat.LIGHT.ordinal()+e])-resist[e]);
        }
        double result=attacker.panel(magic)*coefficient*1000/(1000+Math.max(0,defense))*Math.max(0,1+delta/120);
        for(ExtraDamage a:attacker.extras){
            double base=a.value+random.nextDouble()*Math.max(0,a.maximum-a.value);
            double d=attacker.get(Stat.values()[Stat.LIGHT.ordinal()+a.element])-resist[a.element];
            result+=base*1000/(1000+Math.max(0,defense))*Math.max(0,1+d/120);
        }
        if(random.nextDouble()<attacker.modifiers.getOrDefault("crit",0d))result*=1.5+attacker.modifiers.getOrDefault("critDamage",0d);
        return result*(1+attacker.modifiers.getOrDefault("final",0d))*attacker.modifiers.getOrDefault("skillProduct",1d);
    }

}
