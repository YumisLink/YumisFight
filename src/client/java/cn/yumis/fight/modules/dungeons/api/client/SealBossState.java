package cn.yumis.fight.modules.dungeons.api.client;

import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

/** Narrow client-side query port for Seal Tower 50F Elaina controls, sky time, and soft-enrage seals. */
public final class SealBossState {
    private static BooleanSupplier frozen = () -> false;
    private static BooleanSupplier midnightSky = () -> false;
    private static Predicate<String> skillSealed = id -> false;

    private SealBossState() {}

    public static void install(BooleanSupplier frozenCheck, BooleanSupplier midnightCheck, Predicate<String> sealedCheck) {
        frozen = frozenCheck;
        midnightSky = midnightCheck;
        skillSealed = sealedCheck;
    }

    public static boolean isFrozen() {
        return frozen.getAsBoolean();
    }

    public static boolean isMidnightSky() {
        return midnightSky.getAsBoolean();
    }

    public static boolean isSkillSealed(String skillId) {
        return skillId != null && !skillId.isEmpty() && skillSealed.test(skillId);
    }
}
