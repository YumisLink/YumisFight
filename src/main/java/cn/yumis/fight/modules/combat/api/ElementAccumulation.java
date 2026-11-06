package cn.yumis.fight.modules.combat.api;

import cn.yumis.fight.modules.attributes.api.Stats;
import java.util.*;
import static cn.yumis.fight.modules.attributes.api.AttributeNumbers.finite;

/** Target-owned, life-scoped buildup. Element indices match CombatRules damage components. */
public final class ElementAccumulation {
    public static final double THRESHOLD=.10;
    public static final long SETTLEMENT_GRACE=5000;
    public enum Kind {
        SHOCK("ELEMENT_SHOCK","感电",10000,5000,0xffed8d),
        IGNITE("ELEMENT_IGNITE","点燃",15000,15000,0xff8a58),
        FREEZE("ELEMENT_FREEZE","冻结",5000,2000,0x83dfff),
        EROSION("ELEMENT_EROSION","侵蚀",5000,5000,0xc783ff);
        public final String id,title;
        public final long duration,strongDuration;
        public final int color;
        Kind(String id,String title,long duration,long strongDuration,int color){this.id=id;this.title=title;this.duration=duration;this.strongDuration=strongDuration;this.color=color;}
        public long duration(boolean strong){return strong?strongDuration:duration;}
        public boolean damaging(){return this==IGNITE||this==EROSION;}
        public int ticks(){return this==IGNITE?1:this==EROSION?5:0;}
    }
    public static final class State {
        public long generation=1;
        public double[] buildup=new double[4];
        public long[] until=new long[4];
        public double defenseMultiplier=1;
        public State copy(){var s=new State();s.generation=generation;s.buildup=buildup.clone();s.until=until.clone();s.defenseMultiplier=defenseMultiplier;return s;}
        public boolean matches(Life life){return generation==life.generation;}
        public boolean active(Kind kind,Life life,long now){return matches(life)&&until[kind.ordinal()]>now;}
    }
    public record Change(State state,List<Kind> triggered){public Change{triggered=List.copyOf(triggered);}}
    private ElementAccumulation(){}
    public static Kind kind(String id){for(var kind:Kind.values())if(kind.id.equals(id))return kind;return null;}
    public static double defense(State state,Life life){return state.generation==life.generation?state.defenseMultiplier:1;}
    public static double magicTaken(State state,Life life,boolean strong,long now){return state.active(Kind.IGNITE,life,now)?strong?1.10:1.20:1;}
    /** A content source may additionally feed another buildup without converting or duplicating damage. */
    public static void addConversion(Stats stats,int damageElement,Kind buildup,double ratio){
        if(damageElement< -1||damageElement>3)throw new IllegalArgumentException("积累来源元素无效");
        finite(ratio);if(ratio<0)throw new IllegalArgumentException("积累比例不可为负");
        String key=conversion(damageElement,buildup);stats.modifiers.put(key,stats.modifiers.getOrDefault(key,0d)+ratio);
    }
    private static String conversion(int element,Kind kind){return "elementAccumulation/"+element+"/"+kind.id;}
    /** Fractions include untyped, light, fire, ice and dark, all normalized to the same target maximum. */
    public static double[] fractions(double[] components,double maximum){
        finite(maximum);if(maximum<=0||components.length!=5)throw new IllegalArgumentException("元素伤害分量无效");
        double[] fractions=components.clone();for(int i=0;i<fractions.length;i++){finite(fractions[i]);if(fractions[i]<0)throw new IllegalArgumentException("元素伤害分量不可为负");fractions[i]/=maximum;}return fractions;
    }
    public static Change advance(State previous,Life life,double[] components,double acceptedScale,Stats source,boolean strong,long now){
        if(components.length!=5)throw new IllegalArgumentException("元素伤害分量数量无效");
        finite(acceptedScale);if(acceptedScale<0||acceptedScale>1+Math.ulp(1d)*8)throw new IllegalArgumentException("有效伤害比例无效");
        for(double value:components){finite(value);if(value<0)throw new IllegalArgumentException("元素伤害分量不可为负");}
        State next=previous.matches(life)?previous.copy():new State();
        next.generation=life.generation;
        var triggered=new ArrayList<Kind>();
        for(Kind kind:Kind.values()){
            int index=kind.ordinal();if(next.until[index]>now)continue;
            double amount=components[index+1];
            if(source!=null)for(int element=-1;element<4;element++){
                double ratio=source.modifiers.getOrDefault(conversion(element,kind),0d);finite(ratio);
                if(ratio<0)throw new IllegalArgumentException("积累比例不可为负");amount+=components[element+1]*ratio;
            }
            next.buildup[index]+=amount*acceptedScale;finite(next.buildup[index]);
            if(next.buildup[index]+Math.ulp(THRESHOLD)*8<THRESHOLD)continue;
            next.buildup[index]=0;next.until[index]=Math.addExact(now,kind.duration(strong));
            if(kind==Kind.EROSION)next.defenseMultiplier*=strong?.95:.90;
            triggered.add(kind);
        }
        return new Change(next,triggered);
    }
    /** Fixed-health fire magic uses magical defense and the normal elemental strength/resistance term. */
    public static double igniteFraction(boolean strong,double defense,double fireStrength,double fireResist,double magicTaken){
        return (strong?.10:.30)*1000/(1000+Math.max(0,defense))*Math.max(0,1+(fireStrength-fireResist)/120)*magicTaken;
    }
    public static double erosionFraction(boolean strong){return strong?.01:.05;}
    public static double shockDamage(double damage,double defense,double lightStrength,double lightResist){
        return damage*.20*1000/(1000+Math.max(0,defense))*Math.max(0,1+(lightStrength-lightResist)/120);
    }
}
