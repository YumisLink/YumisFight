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
    /** Index 0 is untyped; 1..4 are light/fire/ice/dark. Values use the very same RNG draws as total. */
    public record Damage(double main,double extra,int element,double[] components,boolean critical){
        public Damage {components=components.clone();}
        @Override public double[] components(){return components.clone();}
        public Damage withMain(double value){double[] c=components();c[element+1]+=value-main;return new Damage(value,extra,element,c,critical);}
        public Damage scaled(double factor){double[] c=components();for(int i=0;i<c.length;i++)c[i]*=factor;return new Damage(main*factor,extra*factor,element,c,critical);}
        public double total(){
            return main+extra;
        }
    }
    private static double[] component(double value,int element){double[] result=new double[5];result[element+1]=value;return result;}
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
        double[] components=component(main,element);
        if(includeExtras)for(ExtraDamage a:attacker.extras){
            double base=a.value+roll.getAsDouble()*Math.max(0,a.maximum-a.value);
            double difference=attacker.get(Stat.values()[Stat.LIGHT.ordinal()+a.element])-resist[a.element];
            double amount=base*coefficient*mitigation*Math.max(0,1+difference/120);extra+=amount;components[a.element+1]+=amount;
        }
        double multiplier=(1+attacker.modifiers.getOrDefault("final",0d))*attacker.modifiers.getOrDefault("skillProduct",1d);
        boolean critical=criticalEnabled&&roll.getAsDouble()<attacker.modifiers.getOrDefault("crit",0d);
        if(critical)multiplier*=1.5+attacker.modifiers.getOrDefault("critDamage",0d);
        for(int i=0;i<components.length;i++)components[i]*=multiplier;
        return new Damage(main*multiplier,extra*multiplier,element,components,critical);
    }
    private CombatRules(){
    }
}
