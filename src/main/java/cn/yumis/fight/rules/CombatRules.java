package cn.yumis.fight.rules;
import static cn.yumis.fight.rules.Rules.*;
import static cn.yumis.fight.rules.Models.*;
import java.util.*;
public final class CombatRules {
    public static double evasionChance(double rating){
        Rules.finite(rating);
        double value=Math.max(0,rating);
        return value/(4000+value);
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
        double delta=-Arrays.stream(resist).max().orElse(0);
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
        if(includeExtras)for(Affix a:attacker.extras){
            double base=a.value+roll.getAsDouble()*Math.max(0,a.maximum-a.value);
            double difference=attacker.get(Stat.values()[Stat.LIGHT.ordinal()+a.element])-resist[a.element];
            extra+=base*mitigation*Math.max(0,1+difference/120);
        }
        double multiplier=(1+attacker.modifiers.getOrDefault("final",0d))*attacker.modifiers.getOrDefault("skillProduct",1d);
        if(criticalEnabled&&roll.getAsDouble()<attacker.modifiers.getOrDefault("crit",0d))multiplier*=1.5+attacker.modifiers.getOrDefault("critDamage",0d);
        return new Damage(main*multiplier,extra*multiplier,element);
    }
    public record SkillDamage(double total,double main){}
    /** Shared post-hit bonuses, preserving combat's calculation order and main/extra distinction. */
    public static SkillDamage skillDamage(Damage parts,Stats stats,Skill skill,double mastery,boolean basic,boolean passive,double levelMultiplier){
        double damage=parts.total()*levelMultiplier,main=parts.main()*levelMultiplier;
        if(skill!=null&&skill.ordinal()<3){
            double bonus=main*stats.modifiers.getOrDefault("basicSkillMain",0d);
            damage+=bonus;main+=bonus;
        }
        if(!basic&&!passive&&stats.weaponType.equals("GREATSWORD")){
            double multiplier=1+.2*mastery;
            damage*=multiplier;main*=multiplier;
        }
        return new SkillDamage(damage,main);
    }
    private CombatRules(){
    }
    public static double damage(Stats attacker,double defense,double[] resist,boolean magic,int[] elements,double coefficient,Random random){
        double max=Arrays.stream(resist).max().orElse(0);
        double delta=-max;
        if(elements.length>0){
            delta=-Double.MAX_VALUE;
            for(int e:elements)delta=Math.max(delta,attacker.get(Stat.values()[Stat.LIGHT.ordinal()+e])-resist[e]);
        }
        double result=attacker.panel(magic)*coefficient*1000/(1000+Math.max(0,defense))*Math.max(0,1+delta/120);
        for(Affix a:attacker.extras){
            double base=a.value+random.nextDouble()*Math.max(0,a.maximum-a.value);
            double d=attacker.get(Stat.values()[Stat.LIGHT.ordinal()+a.element])-resist[a.element];
            result+=base*1000/(1000+Math.max(0,defense))*Math.max(0,1+d/120);
        }
        if(random.nextDouble()<attacker.modifiers.getOrDefault("crit",0d))result*=1.5+attacker.modifiers.getOrDefault("critDamage",0d);
        return result*(1+attacker.modifiers.getOrDefault("final",0d))*attacker.modifiers.getOrDefault("skillProduct",1d);
    }
    public static Rarity rarity(Difficulty difficulty,boolean boss,boolean nativeBoss,double luck,Random random){
        double[] low=boss?new double[]{
            45,30,17,6,1.8,.2
        }
        :new double[]{
            88,10,1.8,.2,0,0
        }
        ;
        double[] high=boss?new double[]{
            12,26,30,19,10,3
        }
        :new double[]{
            78,18,3.5,.5,0,0
        }
        ;
        double[] w=new double[6];
        double sum=0;
        for(int i=0;i<6;i++){
            w[i]=(low[i]+(high[i]-low[i])*difficulty.ordinal()/4d)*Math.pow(1+Math.max(0,luck)/DROP_RATE_BASE,i);
            if(nativeBoss&&i>=4)w[i]=0;
            sum+=w[i];
        }
        double draw=random.nextDouble()*sum;
        for(int i=0;i<6;i++){
            draw-=w[i];
            if(draw<0)return Rarity.values()[i];
        }
        return Rarity.COMMON;
    }
}
