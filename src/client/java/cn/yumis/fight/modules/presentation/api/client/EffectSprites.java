package cn.yumis.fight.modules.presentation.api.client;

import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import java.util.*;

/**
 * Shared textured, UV-mapped, additive/translucent world-space sprite geometry primitives.
 * Pure client presentation helpers: modules own lifetimes and never grant combat authority here.
 */
public final class EffectSprites {
    public enum Blend { TRANSLUCENT, ADDITIVE }

    public record SpriteQuad(
        Identifier texture,
        Blend blend,
        Vec3 a, Vec3 b, Vec3 c, Vec3 d,
        float u0, float v0, float u1, float v1,
        int rgb, int alpha
    ) {}

    public static final Identifier SLASH_ARC = id("textures/effect/slash_arc.png");
    public static final Identifier IMPACT_RING = id("textures/effect/impact_ring.png");
    public static final Identifier MAGIC_SIGIL = id("textures/effect/magic_sigil.png");
    public static final Identifier WARNING_SIGIL = id("textures/effect/warning_sigil.png");
    public static final Identifier BEAM_SHEET = id("textures/effect/beam_sheet.png");
    public static final Identifier RIBBON_TRAIL = id("textures/effect/ribbon_trail.png");
    public static final Identifier RADIAL_GLOW = id("textures/effect/radial_glow.png");
    public static final Identifier SHOCKWAVE_RING = id("textures/effect/shockwave_ring.png");

    private static final int SLASH_FRAMES = 8;
    private static final int IMPACT_FRAMES = 8;
    private static final int BEAM_FLARE_FRAMES = 8;

    private EffectSprites() {}

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("yumis_dungeon_and_fight", path);
    }

    private record BatchKey(Identifier texture, Blend blend) {}

    public static void submit(SubmitCustomGeometryEvent event, List<SpriteQuad> quads, boolean nearFade) {
        if (quads.isEmpty() || event.getLevelRenderState().cameraRenderState == null) return;
        Vec3 camera = event.getLevelRenderState().cameraRenderState.pos;
        Map<BatchKey, List<SpriteQuad>> batches = new LinkedHashMap<>();
        for (SpriteQuad quad : quads) {
            if (quad.alpha() <= 0) continue;
            batches.computeIfAbsent(new BatchKey(quad.texture(), quad.blend()), k -> new ArrayList<>()).add(quad);
        }
        var collector = event.getSubmitNodeCollector();
        var pose = event.getPoseStack();
        for (var entry : batches.entrySet()) {
            var key = entry.getKey();
            var list = entry.getValue();
            var renderType = NearEffectRendering.effect(key.texture(), key.blend() == Blend.ADDITIVE, nearFade);
            collector.submitCustomGeometry(pose, renderType, (matrix, vertices) -> {
                for (SpriteQuad q : list) {
                    int r = (q.rgb() >> 16) & 255;
                    int g = (q.rgb() >> 8) & 255;
                    int b = q.rgb() & 255;
                    int a = Math.clamp(q.alpha(), 0, 255);
                    addVertex(vertices, matrix, q.a(), camera, q.u0(), q.v0(), r, g, b, a);
                    addVertex(vertices, matrix, q.b(), camera, q.u1(), q.v0(), r, g, b, a);
                    addVertex(vertices, matrix, q.c(), camera, q.u1(), q.v1(), r, g, b, a);
                    addVertex(vertices, matrix, q.d(), camera, q.u0(), q.v1(), r, g, b, a);
                }
            });
        }
    }

    private static void addVertex(
        com.mojang.blaze3d.vertex.VertexConsumer vertices,
        com.mojang.blaze3d.vertex.PoseStack.Pose matrix,
        Vec3 p, Vec3 camera,
        float u, float v, int r, int g, int b, int a
    ) {
        vertices.addVertex(matrix, (float) (p.x - camera.x), (float) (p.y - camera.y), (float) (p.z - camera.z))
            .setColor(r, g, b, a)
            .setUv(u, v)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(0xF000F0)
            .setNormal(matrix, 0, 1, 0);
    }

    /** 8-frame oriented crescent slash arc with crisp core and additive outer glow. */
    public static void slashArc(
        List<SpriteQuad> out, Vec3 center, Vec3 forward,
        double rollDeg, double radius, double progress,
        int rgb, int alpha
    ) {
        if (progress < 0 || progress >= 1 || radius <= 0 || alpha <= 0) return;
        Vec3 dir = horizontalOrFallback(forward);
        Vec3 right0 = new Vec3(-dir.z, 0, dir.x).normalize();
        Vec3 up0 = dir.cross(right0).normalize();
        double roll = Math.toRadians(rollDeg);
        Vec3 uAxis = right0.scale(Math.cos(roll)).add(up0.scale(Math.sin(roll))).normalize();
        // Keep the arc spanning forward along dir and sideways along uAxis
        Vec3 vAxis = dir;
        double scale = radius * (0.82 + 0.30 * easeOutCubic(progress));
        int frame = Math.clamp((int) (progress * SLASH_FRAMES), 0, SLASH_FRAMES - 1);
        float u0 = frame / (float) SLASH_FRAMES;
        float u1 = (frame + 1) / (float) SLASH_FRAMES;
        int fadeAlpha = (int) Math.round(alpha * (1.0 - 0.55 * progress * progress));
        emitPlane(out, SLASH_ARC, Blend.ADDITIVE, center, uAxis.scale(scale), vAxis.scale(scale), u0, 0f, u1, 1f, rgb, fadeAlpha);
        // Inner white-hot core layer for stronger pixel crispness
        int coreAlpha = (int) Math.round(fadeAlpha * 0.55);
        if (coreAlpha > 4) {
            emitPlane(out, SLASH_ARC, Blend.ADDITIVE, center, uAxis.scale(scale * 0.94), vAxis.scale(scale * 0.94), u0, 0f, u1, 1f, brighten(rgb, 0.55), coreAlpha);
        }
    }

    /** 8-frame radial burst / impact ring, either flat on the ground or camera-facing. */
    public static void impactBurst(
        List<SpriteQuad> out, Vec3 center, Vec3 camera,
        double radius, double progress, int rgb, int alpha, boolean groundFlat
    ) {
        if (progress < 0 || progress >= 1 || radius <= 0 || alpha <= 0) return;
        int frame = Math.clamp((int) (progress * IMPACT_FRAMES), 0, IMPACT_FRAMES - 1);
        float u0 = frame / (float) IMPACT_FRAMES;
        float u1 = (frame + 1) / (float) IMPACT_FRAMES;
        double scale = radius * (0.55 + 0.60 * easeOutCubic(progress));
        int a = (int) Math.round(alpha * (1.0 - 0.5 * progress * progress));
        if (groundFlat) {
            emitGround(out, IMPACT_RING, Blend.ADDITIVE, center, scale, progress * 0.6, u0, 0f, u1, 1f, rgb, a);
        } else {
            emitBillboard(out, IMPACT_RING, Blend.ADDITIVE, center, camera, scale, u0, 0f, u1, 1f, rgb, a);
        }
    }

    /** Ground rotating arcane magic circle: translucent dark backing + additive glowing runes. */
    public static void sigil(
        List<SpriteQuad> out, Vec3 center, double radius,
        double rotationRad, int rgb, int alpha, boolean doubleLayer
    ) {
        if (radius <= 0 || alpha <= 0) return;
        if (doubleLayer) {
            emitGround(out, MAGIC_SIGIL, Blend.TRANSLUCENT, center, radius * 1.02, -rotationRad * 0.5, 0f, 0f, 1f, 1f, darken(rgb, 0.45), (int) (alpha * 0.62));
        }
        emitGround(out, MAGIC_SIGIL, Blend.ADDITIVE, center.add(0, 0.012, 0), radius, rotationRad, 0f, 0f, 1f, 1f, rgb, alpha);
    }

    /** Tactical enemy/boss ground AoE warning circle with charge-up expanding inner pulse. */
    public static void warningCircle(
        List<SpriteQuad> out, Vec3 center, double radius,
        double progress, int rgb, int alpha
    ) {
        if (radius <= 0 || alpha <= 0) return;
        double p = Math.clamp(progress, 0, 1);
        emitGround(out, WARNING_SIGIL, Blend.TRANSLUCENT, center, radius, p * 0.45, 0f, 0f, 1f, 1f, rgb, alpha);
        if (p > 0.02) {
            double inner = radius * Math.max(0.08, p);
            emitGround(out, SHOCKWAVE_RING, Blend.ADDITIVE, center.add(0, 0.012, 0), inner, -p * 0.8, 0f, 0f, 1f, 1f, brighten(rgb, 0.35), (int) (alpha * (0.55 + 0.45 * p)));
        }
    }

    /** Expanding ground shockwave ring (Cataclysm Shock_Wave style). */
    public static void shockwave(
        List<SpriteQuad> out, Vec3 center, double radius,
        double rotationRad, int rgb, int alpha
    ) {
        if (radius <= 0 || alpha <= 0) return;
        emitGround(out, SHOCKWAVE_RING, Blend.ADDITIVE, center, radius, rotationRad, 0f, 0f, 1f, 1f, rgb, alpha);
    }

    /** Camera-facing radial star glow for charge orbs, weapon tips, and hit flashes. */
    public static void glow(
        List<SpriteQuad> out, Vec3 center, Vec3 camera,
        double radius, int rgb, int alpha
    ) {
        if (radius <= 0 || alpha <= 0) return;
        emitBillboard(out, RADIAL_GLOW, Blend.ADDITIVE, center, camera, radius, 0f, 0f, 1f, 1f, rgb, alpha);
    }

    /**
     * Cross-plane scrolling textured beam with animated starburst flares at both endpoints.
     * Supports standard energy beam strip or twin-helix crackle strip.
     */
    public static void texturedBeam(
        List<SpriteQuad> out, Vec3 from, Vec3 to, Vec3 camera,
        double radius, long timeMs, boolean helical, int rgb, int alpha
    ) {
        Vec3 delta = to.subtract(from);
        double length = delta.length();
        if (length < 0.02 || radius <= 0 || alpha <= 0) return;
        Vec3 dir = delta.scale(1.0 / length);
        Vec3 u = dir.cross(Math.abs(dir.y) > 0.92 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0)).normalize();
        Vec3 v = dir.cross(u).normalize();

        float phaseOuter = (float) ((Math.floorMod(timeMs, 360L)) / 360.0);
        float phaseInner = (float) ((Math.floorMod(timeMs, 240L)) / 240.0);
        int tiles = Math.clamp((int) Math.ceil(length / Math.max(1.2, radius * 4.5)), 1, 8);
        float v0 = helical ? 0.75f : 0.50f;
        float v1 = helical ? 1.00f : 0.75f;

        // Three intersecting axial planes (0, 60, 120 deg) look volumetric from every camera angle
        int glowRgb = rgb;
        int coreRgb = brighten(rgb, 0.65);
        for (int i = 0; i < 3; i++) {
            double ang = i * Math.PI / 3.0;
            Vec3 side = u.scale(Math.cos(ang)).add(v.scale(Math.sin(ang)));
            Vec3 outer = side.scale(radius * 1.45);
            Vec3 inner = side.scale(radius * 0.72);
            emitScrolledBeamStrip(out, from, to, outer, tiles, phaseOuter, v0, v1, glowRgb, (int) (alpha * 0.75));
            emitScrolledBeamStrip(out, from, to, inner, tiles, phaseInner, 0.50f, 0.75f, coreRgb, alpha);
        }

        // Animated starburst flares at start and impact end
        int flareFrame = (int) (Math.floorMod(timeMs / 45L, BEAM_FLARE_FRAMES));
        float fu0 = flareFrame / (float) BEAM_FLARE_FRAMES;
        float fu1 = (flareFrame + 1) / (float) BEAM_FLARE_FRAMES;
        emitBillboard(out, BEAM_SHEET, Blend.ADDITIVE, from, camera, radius * 2.1, fu0, 0f, fu1, 0.5f, coreRgb, alpha);
        emitBillboard(out, BEAM_SHEET, Blend.ADDITIVE, to, camera, radius * 2.6, fu0, 0f, fu1, 0.5f, glowRgb, alpha);
    }

    /** Emits tiled scrolling beam strips with UVs strictly in [0, 1] so clamping samplers never streak. */
    private static void emitScrolledBeamStrip(
        List<SpriteQuad> out, Vec3 from, Vec3 to, Vec3 offset,
        int tiles, float phase, float v0, float v1, int rgb, int alpha
    ) {
        if (alpha <= 0) return;
        float wrap = 1.0f - Math.clamp(phase, 0.0f, 0.999f);
        for (int t = 0; t < tiles; t++) {
            Vec3 a = from.lerp(to, t / (double) tiles);
            Vec3 b = from.lerp(to, (t + 1) / (double) tiles);
            if (wrap > 0.01f && wrap < 0.99f) {
                Vec3 m = a.lerp(b, wrap);
                // Part 1: UV [phase -> 1]
                out.add(new SpriteQuad(BEAM_SHEET, Blend.ADDITIVE,
                    a.subtract(offset), m.subtract(offset), m.add(offset), a.add(offset),
                    phase, v0, 1.0f, v1, rgb, alpha));
                // Part 2: UV [0 -> phase]
                out.add(new SpriteQuad(BEAM_SHEET, Blend.ADDITIVE,
                    m.subtract(offset), b.subtract(offset), b.add(offset), m.add(offset),
                    0.0f, v0, phase, v1, rgb, alpha));
            } else {
                out.add(new SpriteQuad(BEAM_SHEET, Blend.ADDITIVE,
                    a.subtract(offset), b.subtract(offset), b.add(offset), a.add(offset),
                    0.0f, v0, 1.0f, v1, rgb, alpha));
            }
        }
    }

    public static void emitGround(
        List<SpriteQuad> out, Identifier texture, Blend blend,
        Vec3 center, double radius, double rotationRad,
        float u0, float v0, float u1, float v1, int rgb, int alpha
    ) {
        double c = Math.cos(rotationRad) * radius;
        double s = Math.sin(rotationRad) * radius;
        Vec3 right = new Vec3(c, 0, s);
        Vec3 forward = new Vec3(-s, 0, c);
        emitPlane(out, texture, blend, center, right, forward, u0, v0, u1, v1, rgb, alpha);
    }

    public static void emitBillboard(
        List<SpriteQuad> out, Identifier texture, Blend blend,
        Vec3 center, Vec3 camera, double radius,
        float u0, float v0, float u1, float v1, int rgb, int alpha
    ) {
        Vec3 look = camera.subtract(center);
        if (look.lengthSqr() < 1e-6) look = new Vec3(0, 0, 1);
        else look = look.normalize();
        Vec3 right = look.cross(Math.abs(look.y) > 0.95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0)).normalize();
        Vec3 up = right.cross(look).normalize();
        emitPlane(out, texture, blend, center, right.scale(radius), up.scale(radius), u0, v0, u1, v1, rgb, alpha);
    }

    public static void emitPlane(
        List<SpriteQuad> out, Identifier texture, Blend blend,
        Vec3 center, Vec3 halfU, Vec3 halfV,
        float u0, float v0, float u1, float v1, int rgb, int alpha
    ) {
        Vec3 a = center.subtract(halfU).subtract(halfV);
        Vec3 b = center.add(halfU).subtract(halfV);
        Vec3 c = center.add(halfU).add(halfV);
        Vec3 d = center.subtract(halfU).add(halfV);
        out.add(new SpriteQuad(texture, blend, a, b, c, d, u0, v0, u1, v1, rgb, alpha));
    }

    public static double easeOutCubic(double t) {
        double u = 1.0 - Math.clamp(t, 0, 1);
        return 1.0 - u * u * u;
    }

    public static int brighten(int rgb, double amount) {
        int r = (rgb >> 16) & 255;
        int g = (rgb >> 8) & 255;
        int b = rgb & 255;
        r = Math.clamp((int) Math.round(r + (255 - r) * amount), 0, 255);
        g = Math.clamp((int) Math.round(g + (255 - g) * amount), 0, 255);
        b = Math.clamp((int) Math.round(b + (255 - b) * amount), 0, 255);
        return (r << 16) | (g << 8) | b;
    }

    public static int darken(int rgb, double factor) {
        int r = Math.clamp((int) Math.round(((rgb >> 16) & 255) * factor), 0, 255);
        int g = Math.clamp((int) Math.round(((rgb >> 8) & 255) * factor), 0, 255);
        int b = Math.clamp((int) Math.round((rgb & 255) * factor), 0, 255);
        return (r << 16) | (g << 8) | b;
    }

    private static Vec3 horizontalOrFallback(Vec3 v) {
        if (v == null || v.lengthSqr() < 1e-6) return new Vec3(0, 0, 1);
        Vec3 flat = new Vec3(v.x, 0, v.z);
        return flat.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : flat.normalize();
    }
}
