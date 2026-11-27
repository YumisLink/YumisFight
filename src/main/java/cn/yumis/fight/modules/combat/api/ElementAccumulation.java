package cn.yumis.fight.modules.combat.api;

import cn.yumis.fight.modules.attributes.api.Stats;
import java.util.*;
import static cn.yumis.fight.modules.attributes.api.AttributeNumbers.finite;

/** Target-owned, life-scoped buildup. Element indices match CombatRules damage components. */
public final class ElementAccumulation {
    public static final double THRESHOLD=.10;
    public static final long SETTLEMENT_GRACE=5000;
    private static final long RESISTANCE_MILLIS=10_000;
    private static final double RESISTANCE_BUILDUP=.1;
    public enum Kind {
        SHOCK("ELEMENT_SHOCK","感电",10000,5000,0xffed8d),
        IGNITE("ELEMENT_IGNITE","点燃",15000,15000,0xff8a58),
        FREEZE("ELEMENT_FREEZE","冻结",5000,5000,0x83dfff),
        EROSION("ELEMENT_EROSION","侵蚀",5000,5000,0xc783ff);
        public final String id,title;
        public final long duration,strongDuration;
        public final int color;
        Kind(String id,String title,long duration,long strongDuration,int color){this.id=id;this.title=title;this.duration=duration;this.strongDuration=strongDuration;this.color=color;}
        public long duration(boolean strong){
            var e=CombatBalanceRules.current().combat.elemental();
            return switch(this){
                case SHOCK->strong?e.shockStrongDurationMs():e.shockDurationMs();
                case IGNITE->strong?e.igniteStrongDurationMs():e.igniteDurationMs();
                case FREEZE->strong?e.freezeStrongDurationMs():e.freezeDurationMs();
                case EROSION->strong?e.erosionStrongDurationMs():e.erosionDurationMs();
            };
        }
        public boolean damaging(){return this==IGNITE||this==EROSION;}
        public int ticks(){return this==IGNITE?1:this==EROSION?5:0;}
    }
    public static final class State {
        public long generation=1;
        public double[] buildup=new double[4];
        public long[] until=new long[4];
        /** Boss/elite trigger for the active effect; freeze uses this to choose chill over hard control. */
        public boolean[] strong=new boolean[4];
        private long[] resistanceUntil=new long[4];
        public double defenseMultiplier=1;
        public State copy(){
            var s=new State();
            s.generation=generation;
            s.buildup=buildup.clone();
            s.until=until.clone();
            s.strong=strong.clone();
            s.resistanceUntil=resistanceUntil.clone();
            s.defenseMultiplier=defenseMultiplier;
            return s;
        }
        public boolean matches(Life life){return generation==life.generation;}
        public boolean active(Kind kind,Life life,long now){return matches(life)&&until[kind.ordinal()]>now;}
        public void endEarly(Kind kind,long now){
            int index=kind.ordinal();
            if(until[index]<=now)return;
            long window=CombatBalanceRules.current().combat.elemental().resistanceWindowMs();
            if(resistanceUntil[index]-until[index]==window)
                resistanceUntil[index]=Math.addExact(now,window);
            until[index]=now;
        }
    }
    public record Change(State state,List<Kind> triggered){public Change{triggered=List.copyOf(triggered);}}
    private ElementAccumulation(){}
    public static Kind kind(String id){for(var kind:Kind.values())if(kind.id.equals(id))return kind;return null;}
    public static double defense(State state,Life life){return state.generation==life.generation?state.defenseMultiplier:1;}
    public static double magicTaken(State state,Life life,boolean strong,long now){
        var e=CombatBalanceRules.current().combat.elemental();
        return state.active(Kind.IGNITE,life,now)?(strong?e.igniteStrongMagicTaken():e.igniteMagicTaken()):1;
    }
    /** Hard freeze only applies to ordinary targets; boss/elite freeze is a cast-speed slow. */
    public static boolean controls(Life life,long now){return life.elemental.active(Kind.FREEZE,life,now)&&!life.elemental.strong[Kind.FREEZE.ordinal()];}
    public static boolean chilled(Life life,long now){return life.elemental.active(Kind.FREEZE,life,now)&&life.elemental.strong[Kind.FREEZE.ordinal()];}
    /** Boss/elite freeze halves cast speed, so action and windup times take twice as long. Cooldowns are unchanged. */
    public static double castTimeScale(Life life,long now){return chilled(life,now)?CombatBalanceRules.current().combat.elemental().chillCastTimeScale():1;}
    /** A content source may additionally feed another buildup without converting or duplicating damage. */
    public static void addConversion(Stats stats,int damageElement,Kind buildup,double ratio){
        if(damageElement< -1||damageElement>3)throw new IllegalArgumentException("积累来源元素无效");
        finite(ratio);if(ratio<0)throw new IllegalArgumentException("积累比例不可为负");
        String key=conversion(damageElement,buildup);stats.modifiers.put(key,stats.modifiers.getOrDefault(key,0d)+ratio);
    }
    /** Scales elemental buildup rate for a single hit without altering damage components. */
    public static void scaleBuildup(Stats stats,double factor){
        finite(factor);if(factor<0)throw new IllegalArgumentException("积累倍率不可为负");
        stats.modifiers.put(SCALE_KEY,stats.modifiers.getOrDefault(SCALE_KEY,1d)*factor);
    }
    private static final String SCALE_KEY="elementAccumulation/scale";
    private static String conversion(int element,Kind kind){return "elementAccumulation/"+element+"/"+kind.id;}
    /** Fractions include untyped, light, fire, ice and dark, all normalized to the same target maximum. */
    public static double[] fractions(double[] components,double maximum){
        finite(maximum);if(maximum<=0||components.length!=5)throw new IllegalArgumentException("元素伤害分量无效");
        double[] fractions=components.clone();for(int i=0;i<fractions.length;i++){finite(fractions[i]);if(fractions[i]<0)throw new IllegalArgumentException("元素伤害分量不可为负");fractions[i]/=maximum;}return fractions;
    }
    public static Change advance(State previous,Life life,double[] components,double acceptedScale,Stats source,boolean strong,boolean enemy,long now){
        if(components.length!=5)throw new IllegalArgumentException("元素伤害分量数量无效");
        finite(acceptedScale);if(acceptedScale<0||acceptedScale>1+Math.ulp(1d)*8)throw new IllegalArgumentException("有效伤害比例无效");
        for(double value:components){finite(value);if(value<0)throw new IllegalArgumentException("元素伤害分量不可为负");}
        State next=previous.matches(life)?previous.copy():new State();
        next.generation=life.generation;
        var triggered=new ArrayList<Kind>();
        var e=CombatBalanceRules.current().combat.elemental();
        double threshold=e.threshold();
        long resistanceWindow=e.resistanceWindowMs();
        double resistanceBuildup=e.resistanceBuildupMultiplier();
        double buildupScale=source==null?1d:source.modifiers.getOrDefault(SCALE_KEY,1d);
        finite(buildupScale);if(buildupScale<0)throw new IllegalArgumentException("积累倍率不可为负");
        for(Kind kind:Kind.values()){
            int index=kind.ordinal();if(next.until[index]>now)continue;
            double amount=components[index+1];
            if(source!=null)for(int element=-1;element<4;element++){
                double ratio=source.modifiers.getOrDefault(conversion(element,kind),0d);finite(ratio);
                if(ratio<0)throw new IllegalArgumentException("积累比例不可为负");amount+=components[element+1]*ratio;
            }
            amount*=buildupScale;
            if(enemy&&now<next.resistanceUntil[index])amount*=resistanceBuildup;
            next.buildup[index]+=amount*acceptedScale;finite(next.buildup[index]);
            if(next.buildup[index]+Math.ulp(threshold)*8<threshold)continue;
            next.buildup[index]=0;next.until[index]=Math.addExact(now,kind.duration(strong));next.strong[index]=strong;
            if(enemy)next.resistanceUntil[index]=Math.addExact(next.until[index],resistanceWindow);
            if(kind==Kind.EROSION)next.defenseMultiplier*=strong?e.erosionStrongDefenseFactor():e.erosionDefenseFactor();
            triggered.add(kind);
        }
        return new Change(next,triggered);
    }
    /** Fixed-health fire magic uses magical defense and the normal elemental strength/resistance term. */
    public static double igniteFraction(boolean strong,double defense,double fireStrength,double fireResist,double magicTaken){
        var c=CombatBalanceRules.current().combat;var e=c.elemental();
        return (strong?e.igniteStrongFraction():e.igniteFraction())*c.defenseDivisor()/(c.defenseDivisor()+Math.max(0,defense))*Math.max(0,1+(fireStrength-fireResist)/c.elementalDivisor())*magicTaken;
    }
    public static double erosionFraction(boolean strong){
        var e=CombatBalanceRules.current().combat.elemental();
        return strong?e.erosionStrongFraction():e.erosionFraction();
    }
    public static double shockDamage(double damage,double defense,double lightStrength,double lightResist){
        var c=CombatBalanceRules.current().combat;var e=c.elemental();
        return damage*e.shockDamageRatio()*c.defenseDivisor()/(c.defenseDivisor()+Math.max(0,defense))*Math.max(0,1+(lightStrength-lightResist)/c.elementalDivisor());
    }
}
