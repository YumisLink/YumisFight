package cn.yumis.fight.modules.presentation.api.client;

import net.minecraft.world.phys.Vec3;
import java.util.*;

/**
 * Pure-client camera shake controller with quadratic distance and time falloff.
 * Never spawns server entities; respects ClientSettings.cameraShakePercent().
 */
public final class CameraShake {
    public record Offset(float yaw, float pitch, float roll) {
        public static final Offset ZERO = new Offset(0f, 0f, 0f);
        public boolean active() {
            return yaw != 0f || pitch != 0f || roll != 0f;
        }
    }

    private record Impulse(Vec3 origin, double radius, float intensity, long startedAt, long durationMs, long fadeMs) {}

    private static final int MAX_IMPULSES = 32;
    private static final List<Impulse> IMPULSES = new ArrayList<>();

    private CameraShake() {}

    /** Spatial shake decaying quadratically with distance from origin. */
    public static void impulse(Vec3 origin, double radius, float intensity, long durationMs, long fadeMs) {
        if (origin == null || radius <= 0 || intensity <= 0 || (durationMs <= 0 && fadeMs <= 0)) return;
        add(new Impulse(origin, radius, Math.clamp(intensity, 0f, 1.2f), ClientUi.now(), Math.max(0L, durationMs), Math.max(1L, fadeMs)));
    }

    /** Local non-spatial micro-shake for player strikes, parries, and heavy releases. */
    public static void local(float intensity, long durationMs, long fadeMs) {
        if (intensity <= 0 || (durationMs <= 0 && fadeMs <= 0)) return;
        add(new Impulse(null, 0, Math.clamp(intensity, 0f, 1.0f), ClientUi.now(), Math.max(0L, durationMs), Math.max(1L, fadeMs)));
    }

    private static void add(Impulse impulse) {
        if (ClientSettings.cameraShakePercent() <= 0) return;
        synchronized (IMPULSES) {
            if (IMPULSES.size() >= MAX_IMPULSES) IMPULSES.removeFirst();
            IMPULSES.add(impulse);
        }
    }

    public static void clear() {
        synchronized (IMPULSES) {
            IMPULSES.clear();
        }
    }

    public static int activeCount() {
        synchronized (IMPULSES) {
            return IMPULSES.size();
        }
    }

    /** Samples cumulative camera angle offsets (degrees) for the current frame. */
    public static Offset sample(Vec3 cameraPos, long nowMs) {
        int scalePercent = ClientSettings.cameraShakePercent();
        if (scalePercent <= 0) return Offset.ZERO;
        double amplitude = 0.0;
        synchronized (IMPULSES) {
            if (IMPULSES.isEmpty()) return Offset.ZERO;
            for (var it = IMPULSES.iterator(); it.hasNext(); ) {
                Impulse imp = it.next();
                long elapsed = nowMs - imp.startedAt();
                if (elapsed < 0) continue;
                long total = imp.durationMs() + imp.fadeMs();
                if (elapsed >= total) {
                    it.remove();
                    continue;
                }
                double timeGain = elapsed < imp.durationMs()
                    ? 1.0
                    : Math.pow(1.0 - (elapsed - imp.durationMs()) / (double) imp.fadeMs(), 2.0);
                double distGain = 1.0;
                if (imp.origin() != null && cameraPos != null) {
                    double frac = Math.clamp(1.0 - imp.origin().distanceTo(cameraPos) / imp.radius(), 0.0, 1.0);
                    distGain = frac * frac;
                }
                amplitude += imp.intensity() * timeGain * distGain;
            }
        }
        amplitude = Math.min(1.0, amplitude) * (scalePercent / 100.0);
        if (amplitude < 1e-4) return Offset.ZERO;
        // Smooth multi-frequency oscillation in seconds (~25-42 rad/s feels punchy without nausea)
        double t = nowMs / 1000.0;
        float pitch = (float) (amplitude * Math.cos(t * 34.0 + 2.0) * 3.8);
        float yaw = (float) (amplitude * Math.cos(t * 43.0 + 1.0) * 3.2);
        float roll = (float) (amplitude * Math.cos(t * 29.0) * 4.5);
        return new Offset(yaw, pitch, roll);
    }
}
