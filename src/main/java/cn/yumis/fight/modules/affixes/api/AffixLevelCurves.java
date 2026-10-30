package cn.yumis.fight.modules.affixes.api;

import java.util.*;
import java.util.function.IntToDoubleFunction;

/** Versioned affix content curves; callers supply the actual item or gem level. */
public final class AffixLevelCurves {
    private static final Map<String,IntToDoubleFunction> CURVES=new LinkedHashMap<>();
    static {register("weapon_attack_extra_alignment",level->{
        if(level<1)throw new IllegalArgumentException("Invalid affix source level");
        double original=.075*(15+4d*(level-1)),a=20+5d*(level-1);
        return Math.max(1,.0375*a*(1+a/100)/original);
    });}
    private AffixLevelCurves(){}
    public static void register(String id,IntToDoubleFunction curve){if(CURVES.putIfAbsent(id,Objects.requireNonNull(curve))!=null)throw new IllegalArgumentException("Duplicate affix curve: "+id);}
    public static IntToDoubleFunction require(String id){var curve=CURVES.get(id);if(curve==null)throw new IllegalArgumentException("Unknown affix curve: "+id);return curve;}
}
