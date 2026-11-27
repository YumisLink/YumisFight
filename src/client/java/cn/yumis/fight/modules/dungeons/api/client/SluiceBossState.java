package cn.yumis.fight.modules.dungeons.api.client;

import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;

/** Narrow client-side query port for Sluice Tower 20F Roxy local storm weather and darkened sky. */
public final class SluiceBossState {
    private static DoubleSupplier stormStrength = () -> 0.0;
    private static DoubleSupplier thunderStrength = () -> 0.0;
    private static BooleanSupplier darkSky = () -> false;

    private SluiceBossState() {}

    public static void install(DoubleSupplier storm, DoubleSupplier thunder, BooleanSupplier dark) {
        stormStrength = storm;
        thunderStrength = thunder;
        darkSky = dark;
    }

    public static float stormStrength() {
        return (float) Math.clamp(stormStrength.getAsDouble(), 0.0, 1.0);
    }

    public static float thunderStrength() {
        return (float) Math.clamp(thunderStrength.getAsDouble(), 0.0, 1.0);
    }

    public static boolean darkSkyActive() {
        return darkSky.getAsBoolean();
    }
}
