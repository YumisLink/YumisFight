package cn.yumis.fight.modules.affixes.api;

import java.util.*;
import java.util.function.IntToDoubleFunction;

/** Versioned affix content curves; callers supply the actual item or gem level. */
public final class AffixLevelCurves {
    private static final Map<String,IntToDoubleFunction> CURVES=new LinkedHashMap<>();
    private static final Map<String,IntToDoubleFunction[]> INTERVALS=new LinkedHashMap<>();
    static {register("weapon_attack_extra_alignment",level->{
        if(level<1)throw new IllegalArgumentException("Invalid affix source level");
        double original=.075*(15+4d*(level-1)),a=20+5d*(level-1);
        return Math.max(1,.0375*a*(1+a/100)/original);
    });
        interval("stamina_capacity",l->100+200d*(l-1)/79,l->200+300d*(l-1)/79);
        interval("shield_wait_recovery",l->.035+.215*(l-1)/79,l->.175+1.075*(l-1)/79);
        interval("shield_regeneration",l->.2+.4*(l-1)/79,l->.4+.6*(l-1)/79);
    }
    private AffixLevelCurves(){}
    public static void register(String id,IntToDoubleFunction curve){if(CURVES.putIfAbsent(id,Objects.requireNonNull(curve))!=null)throw new IllegalArgumentException("Duplicate affix curve: "+id);}
    public static IntToDoubleFunction require(String id){var curve=CURVES.get(id);if(curve==null)throw new IllegalArgumentException("Unknown affix curve: "+id);return curve;}
    private static void interval(String id,IntToDoubleFunction low,IntToDoubleFunction high){register(id,l->1);INTERVALS.put(id,new IntToDoubleFunction[]{low,high});}
    public static boolean interval(String id){return INTERVALS.containsKey(id);}
    public static AffixDefinition atLevel(AffixDefinition d,int level){
        var range=INTERVALS.get(d.levelCurve());if(range==null)return d;
        return new AffixDefinition(d.id(),d.name(),d.operation(),d.group(),d.stat(),d.element(),d.revision(),d.retired(),range[0].applyAsDouble(level),range[1].applyAsDouble(level),d.upperMinimum(),d.upperMaximum(),d.balanceScale(),d.fixedRank(),d.parameterMultiplier(),d.description(),d.fixedValue(),d.levelCurve());
    }
}
