package cn.yumis.fight.modules.skills.api;

import java.util.*;
import java.util.function.Function;
import static cn.yumis.fight.modules.skills.api.SkillBalanceProfile.*;

/** Reference math is supplied by career content, not selected by IDs in the generic calibrator. */
public record SkillBalanceStyle(String defaultWeapon,boolean mastery,Multiplier multiplier,BasicFactory basic,Function<String,Intrinsic> intrinsic,double intrinsicGrowth) {
    public enum Intrinsic { NONE, BLEED, SHOCK }
    @FunctionalInterface public interface Multiplier {double apply(SkillBalanceProfile profile,Context context,boolean passive);}
    @FunctionalInterface public interface BasicFactory {Basic create(Context context,boolean frenzy);}
    public record Basic(double weight,double interval,List<Hit> hits){public Basic{hits=List.copyOf(hits);}}
    public SkillBalanceStyle {Objects.requireNonNull(defaultWeapon);Objects.requireNonNull(multiplier);Objects.requireNonNull(basic);Objects.requireNonNull(intrinsic);}
}
