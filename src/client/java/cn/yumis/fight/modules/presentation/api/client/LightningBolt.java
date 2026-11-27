package cn.yumis.fight.modules.presentation.api.client;

import cn.yumis.fight.modules.presentation.api.client.EffectSprites.Blend;
import cn.yumis.fight.modules.presentation.api.client.EffectSprites.SpriteQuad;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/**
 * Pure-math procedural branching lightning bolt generator outputting additive camera-aligned quads.
 * Stateless and deterministic per seed so callers flicker naturally by stepping seed every 40-60ms.
 */
public final class LightningBolt {
    private LightningBolt() {}

    public static void bolt(
        List<SpriteQuad> out, Vec3 from, Vec3 to, Vec3 camera,
        long seed, int segments, double jitter, double thickness,
        int glowRgb, int alpha
    ) {
        bolt(out, from, to, camera, seed, segments, jitter, thickness, glowRgb, alpha, true);
    }

    public static void bolt(
        List<SpriteQuad> out, Vec3 from, Vec3 to, Vec3 camera,
        long seed, int segments, double jitter, double thickness,
        int glowRgb, int alpha, boolean branches
    ) {
        Vec3 span = to.subtract(from);
        double length = span.length();
        if (length < 0.05 || segments < 2 || thickness <= 0 || alpha <= 0) return;

        int segs = Math.clamp(segments, 2, 24);
        Vec3 dir = span.scale(1.0 / length);
        Vec3 u = dir.cross(Math.abs(dir.y) > 0.92 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0)).normalize();
        Vec3 v = dir.cross(u).normalize();

        SplittableRandom rng = new SplittableRandom(seed);
        Vec3[] main = new Vec3[segs + 1];
        main[0] = from;
        main[segs] = to;
        for (int i = 1; i < segs; i++) {
            double t = i / (double) segs;
            double env = Math.sin(t * Math.PI);
            double ox = (rng.nextDouble() * 2.0 - 1.0) * jitter * env;
            double oy = (rng.nextDouble() * 2.0 - 1.0) * jitter * env;
            main[i] = from.lerp(to, t).add(u.scale(ox)).add(v.scale(oy));
        }

        int coreRgb = EffectSprites.brighten(glowRgb, 0.78);
        emitPolyline(out, main, camera, thickness, coreRgb, glowRgb, alpha);

        if (branches && segs >= 4) {
            int branchCount = Math.min(3, Math.max(1, segs / 4));
            for (int b = 0; b < branchCount; b++) {
                int startIdx = 1 + rng.nextInt(segs - 2);
                Vec3 origin = main[startIdx];
                double branchLen = length * (0.22 + rng.nextDouble() * 0.22);
                Vec3 branchDir = dir.scale(0.65)
                    .add(u.scale((rng.nextDouble() * 2.0 - 1.0) * 0.9))
                    .add(v.scale((rng.nextDouble() * 2.0 - 1.0) * 0.9))
                    .normalize();
                Vec3 tip = origin.add(branchDir.scale(branchLen));
                Vec3[] fork = new Vec3[4];
                fork[0] = origin;
                fork[3] = tip;
                for (int k = 1; k <= 2; k++) {
                    double t = k / 3.0;
                    fork[k] = origin.lerp(tip, t)
                        .add(u.scale((rng.nextDouble() * 2.0 - 1.0) * jitter * 0.45))
                        .add(v.scale((rng.nextDouble() * 2.0 - 1.0) * jitter * 0.45));
                }
                emitPolyline(out, fork, camera, thickness * 0.55, coreRgb, glowRgb, (int) (alpha * 0.75));
            }
        }
    }

    /** Wraps multiple crackling helical arcs around a beam or energy column. */
    public static void helixArcs(
        List<SpriteQuad> out, Vec3 from, Vec3 to, Vec3 camera,
        long timeMs, double radius, int count, int rgb, int alpha
    ) {
        Vec3 span = to.subtract(from);
        double length = span.length();
        if (length < 0.2 || count <= 0 || alpha <= 0) return;
        Vec3 dir = span.scale(1.0 / length);
        Vec3 u = dir.cross(Math.abs(dir.y) > 0.92 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0)).normalize();
        Vec3 v = dir.cross(u).normalize();
        long step = timeMs / 55L;
        int segs = Math.clamp((int) Math.ceil(length * 1.4), 4, 14);
        int coreRgb = EffectSprites.brighten(rgb, 0.72);
        for (int a = 0; a < count; a++) {
            SplittableRandom rng = new SplittableRandom(step * 0x9E3779B97F4A7C15L ^ (a * 31337L));
            Vec3[] pts = new Vec3[segs + 1];
            double baseAngle = timeMs * 0.008 + a * (Math.PI * 2.0 / count);
            for (int i = 0; i <= segs; i++) {
                double t = i / (double) segs;
                double ang = baseAngle + t * Math.PI * 3.0 + (rng.nextDouble() - 0.5) * 0.65;
                double r = radius * (0.75 + rng.nextDouble() * 0.55) * Math.sin(Math.min(1.0, t * 4.0) * Math.PI * 0.5);
                pts[i] = from.lerp(to, t).add(u.scale(Math.cos(ang) * r)).add(v.scale(Math.sin(ang) * r));
            }
            emitPolyline(out, pts, camera, Math.max(0.035, radius * 0.14), coreRgb, rgb, alpha);
        }
    }

    private static void emitPolyline(
        List<SpriteQuad> out, Vec3[] points, Vec3 camera,
        double thickness, int coreRgb, int glowRgb, int alpha
    ) {
        for (int i = 0; i < points.length - 1; i++) {
            Vec3 a = points[i], b = points[i + 1];
            Vec3 seg = b.subtract(a);
            if (seg.lengthSqr() < 1e-6) continue;
            Vec3 mid = a.lerp(b, 0.5);
            Vec3 toCam = camera.subtract(mid);
            Vec3 side = seg.cross(toCam.lengthSqr() < 1e-6 ? new Vec3(0, 1, 0) : toCam);
            if (side.lengthSqr() < 1e-6) side = seg.cross(new Vec3(0, 1, 0));
            if (side.lengthSqr() < 1e-6) side = new Vec3(1, 0, 0);
            side = side.normalize();

            Vec3 glowOff = side.scale(thickness * 2.3);
            Vec3 coreOff = side.scale(thickness * 0.85);
            out.add(new SpriteQuad(EffectSprites.BEAM_SHEET, Blend.ADDITIVE,
                a.subtract(glowOff), b.subtract(glowOff), b.add(glowOff), a.add(glowOff),
                0f, 0.50f, 1f, 0.75f, glowRgb, (int) (alpha * 0.70)));
            out.add(new SpriteQuad(EffectSprites.BEAM_SHEET, Blend.ADDITIVE,
                a.subtract(coreOff), b.subtract(coreOff), b.add(coreOff), a.add(coreOff),
                0f, 0.50f, 1f, 0.75f, coreRgb, alpha));
        }
    }
}
