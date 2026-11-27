package cn.yumis.fight.modules.presentation.api.client;

import cn.yumis.fight.modules.presentation.api.client.EffectSprites.SpriteQuad;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.common.NeoForge;
import java.util.*;

/**
 * Shared client-only transient VFX scheduler (slashes, impacts, shockwaves, lightning, and active ribbon trails).
 * Any module can trigger one-shot visual bursts without managing its own render event lifecycle.
 */
public final class WorldEffects {
    private enum Kind { SLASH, IMPACT_FLAT, IMPACT_BILLBOARD, SHOCKWAVE, LIGHTNING, SIGIL, GLOW }

    private record Burst(
        Kind kind, Vec3 pos, Vec3 aux,
        double paramA, double paramB, double rollDeg,
        int rgb, int alpha, long startedAt, long durationMs, long seed
    ) {}

    private record Frame(List<SpriteQuad> sprites, boolean nearFade) {}

    private static final int MAX_BURSTS = 512;
    private static final ContextKey<Frame> FRAME = new ContextKey<>(
        Identifier.fromNamespaceAndPath("yumis_dungeon_and_fight", "world_effects"));
    private static final List<Burst> BURSTS = new ArrayList<>();
    private static Object currentLevel;

    private WorldEffects() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(WorldEffects::extract);
        NeoForge.EVENT_BUS.addListener(WorldEffects::submit);
    }

    public static void clear() {
        synchronized (BURSTS) {
            BURSTS.clear();
        }
        RibbonTrail.clear();
        CameraShake.clear();
    }

    public static void playSlash(Vec3 center, Vec3 forward, double rollDeg, double radius, int rgb, int alpha, long durationMs) {
        add(new Burst(Kind.SLASH, center, forward, radius, 0, rollDeg, rgb, alpha, ClientUi.now(), Math.clamp(durationMs, 80L, 1200L), 0L));
    }

    public static void playImpact(Vec3 center, double radius, int rgb, int alpha, long durationMs, boolean groundFlat) {
        add(new Burst(groundFlat ? Kind.IMPACT_FLAT : Kind.IMPACT_BILLBOARD, center, Vec3.ZERO, radius, 0, 0, rgb, alpha, ClientUi.now(), Math.clamp(durationMs, 100L, 1600L), 0L));
    }

    public static void playShockwave(Vec3 center, double startRadius, double endRadius, int rgb, int alpha, long durationMs) {
        add(new Burst(Kind.SHOCKWAVE, center, Vec3.ZERO, startRadius, endRadius, 0, rgb, alpha, ClientUi.now(), Math.clamp(durationMs, 120L, 2000L), 0L));
    }

    public static void playLightning(Vec3 from, Vec3 to, double thickness, double jitter, int rgb, int alpha, long durationMs, long seed) {
        add(new Burst(Kind.LIGHTNING, from, to, thickness, jitter, 0, rgb, alpha, ClientUi.now(), Math.clamp(durationMs, 80L, 1500L), seed));
    }

    public static void playSigil(Vec3 center, double radius, int rgb, int alpha, long durationMs) {
        add(new Burst(Kind.SIGIL, center, Vec3.ZERO, radius, 0, 0, rgb, alpha, ClientUi.now(), Math.clamp(durationMs, 150L, 4000L), 0L));
    }

    public static void playGlow(Vec3 center, double radius, int rgb, int alpha, long durationMs) {
        add(new Burst(Kind.GLOW, center, Vec3.ZERO, radius, 0, 0, rgb, alpha, ClientUi.now(), Math.clamp(durationMs, 80L, 1500L), 0L));
    }

    private static void add(Burst burst) {
        synchronized (BURSTS) {
            if (BURSTS.size() >= MAX_BURSTS) BURSTS.removeFirst();
            BURSTS.add(burst);
        }
    }

    public static int activeBurstCount() {
        synchronized (BURSTS) {
            return BURSTS.size();
        }
    }

    private static void extract(ExtractLevelRenderStateEvent event) {
        var mc = Minecraft.getInstance();
        if (mc.level != currentLevel) {
            currentLevel = mc.level;
            clear();
        }
        var state = event.getRenderState();
        if (state.cameraRenderState == null) return;
        Vec3 camera = state.cameraRenderState.pos;
        long now = ClientUi.now();
        List<SpriteQuad> sprites = new ArrayList<>();

        RibbonTrail.extract(sprites, camera, now);

        synchronized (BURSTS) {
            for (var it = BURSTS.iterator(); it.hasNext(); ) {
                Burst b = it.next();
                long age = now - b.startedAt();
                if (age < 0) continue;
                if (age >= b.durationMs()) {
                    it.remove();
                    continue;
                }
                double t = age / (double) b.durationMs();
                switch (b.kind()) {
                    case SLASH -> EffectSprites.slashArc(sprites, b.pos(), b.aux(), b.rollDeg(), b.paramA(), t, b.rgb(), b.alpha());
                    case IMPACT_FLAT -> EffectSprites.impactBurst(sprites, b.pos(), camera, b.paramA(), t, b.rgb(), b.alpha(), true);
                    case IMPACT_BILLBOARD -> EffectSprites.impactBurst(sprites, b.pos(), camera, b.paramA(), t, b.rgb(), b.alpha(), false);
                    case SHOCKWAVE -> {
                        double r = b.paramA() + (b.paramB() - b.paramA()) * EffectSprites.easeOutCubic(t);
                        int a = (int) Math.round(b.alpha() * (1.0 - t * t));
                        EffectSprites.shockwave(sprites, b.pos(), r, t * 0.9, b.rgb(), a);
                    }
                    case LIGHTNING -> {
                        long flickerSeed = b.seed() ^ ((age / 45L) * 0x9E3779B97F4A7C15L);
                        int a = (int) Math.round(b.alpha() * (1.0 - 0.7 * t * t));
                        int segs = Math.clamp((int) Math.ceil(b.pos().distanceTo(b.aux()) * 1.3), 4, 16);
                        LightningBolt.bolt(sprites, b.pos(), b.aux(), camera, flickerSeed, segs, b.paramB(), b.paramA(), b.rgb(), a);
                    }
                    case SIGIL -> {
                        double fade = t < 0.15 ? t / 0.15 : t > 0.75 ? (1.0 - t) / 0.25 : 1.0;
                        EffectSprites.sigil(sprites, b.pos(), b.paramA() * (0.85 + 0.15 * EffectSprites.easeOutCubic(Math.min(1, t * 3))), age / 900.0, b.rgb(), (int) Math.round(b.alpha() * fade), true);
                    }
                    case GLOW -> {
                        double scale = b.paramA() * (0.6 + 0.6 * EffectSprites.easeOutCubic(t));
                        EffectSprites.glow(sprites, b.pos(), camera, scale, b.rgb(), (int) Math.round(b.alpha() * (1.0 - t)));
                    }
                }
            }
        }
        state.setRenderData(FRAME, new Frame(List.copyOf(sprites), NearEffectRendering.enabled(event.getCamera())));
    }

    private static void submit(SubmitCustomGeometryEvent event) {
        Frame frame = event.getLevelRenderState().getRenderData(FRAME);
        if (frame != null && !frame.sprites().isEmpty()) {
            EffectSprites.submit(event, frame.sprites(), frame.nearFade());
        }
    }
}
