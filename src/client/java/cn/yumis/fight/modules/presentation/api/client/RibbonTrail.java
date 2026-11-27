package cn.yumis.fight.modules.presentation.api.client;

import cn.yumis.fight.modules.presentation.api.client.EffectSprites.Blend;
import cn.yumis.fight.modules.presentation.api.client.EffectSprites.SpriteQuad;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/**
 * Pure-client camera-aligned ribbon trail system inspired by ring-buffered trail particles,
 * without spawning server entities or flushing individual particle batches.
 */
public final class RibbonTrail {
    private static final int MAX_TRACKS = 256;
    private static final int MAX_SAMPLES = 48;
    private static final long MAX_FUTURE_SKEW_MS = 5000L;

    private record Sample(Vec3 pos, long at) {}

    private static final class Trail {
        final ArrayDeque<Sample> samples = new ArrayDeque<>();
        double halfWidth;
        int rgb;
        int alpha;
        long lifetimeMs;
        long lastSeen;
    }

    private static final Map<Object, Trail> TRACKS = new LinkedHashMap<>();

    private RibbonTrail() {}

    /**
     * Pushes a new sample point onto a keyed trail; automatically deduplicates tiny jitter.
     * {@code nowMs} must use the local monotonic clock ({@link ClientUi#now()}), matching {@link WorldEffects}.
     */
    public static void push(Object key, Vec3 pos, double halfWidth, int rgb, int alpha, long lifetimeMs, long nowMs) {
        if (key == null || pos == null || halfWidth <= 0 || alpha <= 0 || lifetimeMs <= 0) return;
        synchronized (TRACKS) {
            Trail trail = TRACKS.get(key);
            if (trail == null) {
                if (TRACKS.size() >= MAX_TRACKS) TRACKS.remove(TRACKS.keySet().iterator().next());
                trail = new Trail();
                TRACKS.put(key, trail);
            }
            trail.halfWidth = halfWidth;
            trail.rgb = rgb;
            trail.alpha = Math.clamp(alpha, 0, 255);
            trail.lifetimeMs = Math.clamp(lifetimeMs, 60L, 4000L);
            trail.lastSeen = nowMs;
            Sample head = trail.samples.peekFirst();
            if (head == null || head.pos().distanceToSqr(pos) >= 0.0025 || nowMs - head.at() >= 25L) {
                trail.samples.addFirst(new Sample(pos, nowMs));
                while (trail.samples.size() > MAX_SAMPLES) trail.samples.removeLast();
            }
        }
    }

    public static void clear() {
        synchronized (TRACKS) {
            TRACKS.clear();
        }
    }

    public static int activeTrackCount() {
        synchronized (TRACKS) {
            return TRACKS.size();
        }
    }

    /** Prunes expired (or clock-skewed) samples and emits camera-facing ribbon quads for all active tracks. */
    public static void extract(List<SpriteQuad> out, Vec3 camera, long nowMs) {
        synchronized (TRACKS) {
            for (var it = TRACKS.entrySet().iterator(); it.hasNext(); ) {
                Trail trail = it.next().getValue();
                long idle = nowMs - trail.lastSeen;
                if (idle > trail.lifetimeMs || idle < -MAX_FUTURE_SKEW_MS) {
                    it.remove();
                    continue;
                }
                while (!trail.samples.isEmpty()) {
                    long age = nowMs - trail.samples.peekLast().at();
                    if (age > trail.lifetimeMs || age < -MAX_FUTURE_SKEW_MS) {
                        trail.samples.removeLast();
                    } else {
                        break;
                    }
                }
                if (trail.samples.size() < 2) {
                    continue;
                }
                List<Vec3> points = new ArrayList<>(trail.samples.size());
                for (Sample s : trail.samples) points.add(s.pos());
                double ageFade = Math.clamp(1.0 - Math.max(0L, idle) / (double) trail.lifetimeMs, 0.0, 1.0);
                ribbon(out, points, camera, trail.halfWidth, trail.rgb, (int) Math.round(trail.alpha * ageFade));
            }
        }
    }

    /**
     * Stateless helper: builds a smooth camera-facing tapered ribbon along ordered points (index 0 = bright head).
     */
    public static void ribbon(
        List<SpriteQuad> out, List<Vec3> points, Vec3 camera,
        double halfWidth, int rgb, int alpha
    ) {
        int n = points.size();
        if (n < 2 || halfWidth <= 0 || alpha <= 0) return;
        Vec3[] offsets = new Vec3[n];
        for (int i = 0; i < n; i++) {
            Vec3 p = points.get(i);
            Vec3 tangent = i == 0 ? points.get(0).subtract(points.get(1))
                : i == n - 1 ? points.get(n - 2).subtract(points.get(n - 1))
                : points.get(i - 1).subtract(points.get(i + 1));
            if (tangent.lengthSqr() < 1e-8) tangent = new Vec3(0, 1, 0);
            else tangent = tangent.normalize();
            Vec3 toCam = camera.subtract(p);
            if (toCam.lengthSqr() < 1e-8) toCam = new Vec3(0, 0, 1);
            else toCam = toCam.normalize();
            Vec3 side = tangent.cross(toCam);
            if (side.lengthSqr() < 1e-6) side = tangent.cross(Math.abs(tangent.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0));
            double t = i / (double) (n - 1);
            double taper = 1.0 - 0.78 * t;
            offsets[i] = side.normalize().scale(halfWidth * taper);
        }
        int coreRgb = EffectSprites.brighten(rgb, 0.45);
        for (int i = 0; i < n - 1; i++) {
            float u0 = i / (float) (n - 1);
            float u1 = (i + 1) / (float) (n - 1);
            Vec3 p0 = points.get(i), p1 = points.get(i + 1);
            Vec3 o0 = offsets[i], o1 = offsets[i + 1];
            int segAlpha = (int) Math.round(alpha * (1.0 - 0.65 * u0));
            if (segAlpha <= 1) continue;
            out.add(new SpriteQuad(EffectSprites.RIBBON_TRAIL, Blend.ADDITIVE,
                p0.subtract(o0), p1.subtract(o1), p1.add(o1), p0.add(o0),
                u0, 0f, u1, 1f, rgb, segAlpha));
            out.add(new SpriteQuad(EffectSprites.RIBBON_TRAIL, Blend.ADDITIVE,
                p0.subtract(o0.scale(0.45)), p1.subtract(o1.scale(0.45)), p1.add(o1.scale(0.45)), p0.add(o0.scale(0.45)),
                u0, 0f, u1, 1f, coreRgb, (int) (segAlpha * 0.7)));
        }
    }
}
