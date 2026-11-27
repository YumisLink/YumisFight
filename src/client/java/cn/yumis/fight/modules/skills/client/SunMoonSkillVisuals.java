package cn.yumis.fight.modules.skills.client;

import cn.yumis.fight.modules.presentation.api.client.CameraShake;
import cn.yumis.fight.modules.presentation.api.client.EffectSprites;
import cn.yumis.fight.modules.presentation.api.client.EffectSprites.Blend;
import cn.yumis.fight.modules.presentation.api.client.EffectSprites.SpriteQuad;
import cn.yumis.fight.modules.presentation.api.client.LightningBolt;
import cn.yumis.fight.modules.presentation.api.client.RibbonTrail;
import cn.yumis.fight.modules.presentation.api.client.WorldEffects;
import cn.yumis.fight.modules.presentation.api.client.WorldQuads.Quad;
import cn.yumis.fight.modules.skills.api.*;
import cn.yumis.fight.modules.skills.api.client.SkillsClient;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.*;

import static cn.yumis.fight.modules.skills.api.SkillVisualEvent.Phase.*;
import static cn.yumis.fight.modules.skills.client.SkillVfx.vector;

/**
 * Dedicated FF14 Black Mage-inspired Sun & Moon Mage (日月魔法师) VFX renderer.
 * Zero legacy untextured WorldQuads: 100% custom high-contrast pixel-sharp + soft-feathered
 * sprite sheets (source: tools/build_sunmoon_effect_textures.py), dual-layer translucent dark-rim +
 * incandescent additive rendering, Ley Lines casting astrolabes, orbiting Astral Fire wisps &
 * Umbral Ice crystals, Paradox-ready eclipse orbit, exact FF14 skill mapping:
 *   - 日炎 (Sunfire)         -> FF14 Fire I (火炎)
 *   - 烈日爆炎 (Solar Flare) -> FF14 Fire III (爆炎)
 *   - 日冕 (Corona)          -> FF14 Fire IV (炽炎) + CoronaBlur
 *   - 月流 (Moon Flow)       -> FF14 Blizzard I/III (冰结/冰封) × Moon Phantom
 *   - 月辰回流 (Moon Reflux) -> FF14 Umbral Soul (灵极魂)
 *   - 月见星辉 (Starfall)    -> FF14 Blizzard IV × Freeze (冰澈/玄冰) with overlap alpha safety
 *   - 悖论 (Paradox)         -> FF14 Paradox (悖论) dual Ice-Cyan × Crimson-Magenta supernova
 * Pure client presentation: never grants combat authority, hitboxes, or physics changes.
 */
final class SunMoonSkillVisuals {
    private static final Vec3 UP = new Vec3(0, 1, 0);

    // Custom pre-baked multi-tone SunMoon sprite sheets (source: tools/build_sunmoon_effect_textures.py)
    static final Identifier TEX_LEY_LINES = tex("ley_lines_sigil.png");
    static final Identifier TEX_ASTRAL_WISP = tex("astral_fire_wisp.png");
    static final Identifier TEX_UMBRAL_CRYSTAL = tex("umbral_ice_crystal.png");
    static final Identifier TEX_SUN_ORB = tex("sun_orb.png");
    static final Identifier TEX_FIRE1_BURST = tex("fire1_burst.png");
    static final Identifier TEX_FIRE3_VORTEX = tex("fire3_vortex.png");
    static final Identifier TEX_CORONA_SUN = tex("corona_sun.png");
    static final Identifier TEX_CORONA_RING = tex("corona_ring.png");
    static final Identifier TEX_CORONA_NOVA = tex("corona_nova.png");
    static final Identifier TEX_SOLAR_CRATER = tex("solar_crater.png");
    static final Identifier TEX_MOON_DISC = tex("moon_disc.png");
    static final Identifier TEX_FROST_SPIKE = tex("frost_crystal_spike.png");
    static final Identifier TEX_FROST_BURST = tex("frost_burst.png");
    static final Identifier TEX_MOON_AURORA = tex("moon_aurora_pillar.png");
    static final Identifier TEX_STAR_METEOR = tex("star_meteor.png");
    static final Identifier TEX_PARADOX_SIGIL = tex("paradox_eclipse_sigil.png");
    static final Identifier TEX_PARADOX_BURST = tex("paradox_burst.png");

    // High-contrast FF14 Black Mage palette accents
    private static final int COL_WHITE = 0xFFFFFF;
    private static final int COL_SUN_DARK = 0x2B0B0A;
    private static final int COL_SUN_MAIN = 0xFF9632;
    private static final int COL_SUN_GOLD = 0xFFD46B;
    private static final int COL_SUN_HOT = 0xFFF4CC;

    private static final int COL_MOON_DARK = 0x0C1A3D;
    private static final int COL_MOON_MAIN = 0x76D4FF;
    private static final int COL_MOON_BRIGHT = 0xCFEFFF;
    private static final int COL_MOON_WHITE = 0xF0FAFF;

    private static final int COL_PARADOX_ICE = 0x6EEBFF;
    private static final int COL_PARADOX_FIRE = 0xFF4278;
    private static final int COL_PARADOX_VOID = 0x9F64FF;
    private static final int COL_PARADOX_HOT = 0xFCE8FF;

    private static final class CastMeta {
        double radius;
        long impactDelayMs;
        double liftHeight;

        CastMeta(double radius, long impactDelayMs, double liftHeight) {
            this.radius = radius;
            this.impactDelayMs = impactDelayMs;
            this.liftHeight = liftHeight;
        }
    }

    // Pure-client transient visual metadata & state (never authoritative)
    private static final Map<UUID, CastMeta> METAS = new LinkedHashMap<>();
    private static final Map<UUID, Integer> LAST_HEART = new HashMap<>();
    private static final Map<UUID, Integer> LAST_STACKS = new HashMap<>();
    private static final Map<UUID, Long> HEART_GAIN_FLASH_AT = new HashMap<>();
    private static final List<Vec3> FRAME_STARFALLS = new ArrayList<>();

    private SunMoonSkillVisuals() {}

    private static Identifier tex(String name) {
        return Identifier.fromNamespaceAndPath("yumis_dungeon_and_fight", "textures/effect/sunmoon/" + name);
    }

    static void clear() {
        METAS.clear();
        LAST_HEART.clear();
        LAST_STACKS.clear();
        HEART_GAIN_FLASH_AT.clear();
        FRAME_STARFALLS.clear();
    }

    static void install() {
        for (String id : SunMoonSkills.ALL) {
            SkillVisualRegistry.register(id, new SkillVisualRegistry.Handler() {
                @Override public long defaultDuration() {
                    return 2800L;
                }

                @Override public int defaultColor() {
                    if (SunMoonSkills.PARADOX.equals(id)) return COL_PARADOX_FIRE;
                    if (SunMoonSkills.MOON.equals(id) || SunMoonSkills.REFLUX.equals(id) || SunMoonSkills.STARFALL.equals(id)) {
                        return COL_MOON_MAIN;
                    }
                    return COL_SUN_MAIN;
                }

                @Override public int handOrbColor() {
                    if (SunMoonSkills.SUN.equals(id) || SunMoonSkills.FLARE.equals(id) || SunMoonSkills.CORONA.equals(id)) {
                        return COL_SUN_MAIN;
                    }
                    if (SunMoonSkills.MOON.equals(id) || SunMoonSkills.STARFALL.equals(id)) {
                        return COL_MOON_MAIN;
                    }
                    return 0;
                }

                @Override public long finishedUntil(SkillVfx.Track track, SkillVisualEvent event) {
                    if (SunMoonSkills.MOON.equals(id)) return event.at() + 2000L;
                    if (SunMoonSkills.FLARE.equals(id)) return event.at() + 1200L;
                    if (SunMoonSkills.CORONA.equals(id)) return event.at() + 1250L;
                    if (SunMoonSkills.PARADOX.equals(id)) return event.at() + 1050L;
                    if (SunMoonSkills.STARFALL.equals(id)) return event.at() + 700L;
                    return event.at() + 920L;
                }

                @Override public boolean motion(SkillVfx.Track track, JsonObject value) {
                    if (value == null) return false;
                    boolean hasMeta = value.has("sunMoonRadius") || value.has("impactDelayMs") || value.has("liftHeight");
                    if (hasMeta) {
                        CastMeta def = defaultMeta(id);
                        double r = value.has("sunMoonRadius") ? Math.max(0.5, value.get("sunMoonRadius").getAsDouble()) : def.radius;
                        long delay = value.has("impactDelayMs") ? Math.max(0L, value.get("impactDelayMs").getAsLong()) : def.impactDelayMs;
                        double lift = value.has("liftHeight") ? Math.max(0.2, value.get("liftHeight").getAsDouble()) : def.liftHeight;
                        if (METAS.size() >= 256) METAS.remove(METAS.keySet().iterator().next());
                        METAS.put(track.start.cast(), new CastMeta(r, delay, lift));
                    }
                    // Let SkillVfx build standard Motion when projectile/target tracking fields are present!
                    boolean hasStandardMotion = value.has("target") || value.has("velocity") || value.has("range");
                    return hasMeta && !hasStandardMotion;
                }

                @Override public void play(SkillVfx.Track track, SkillVisualEvent event) {
                    playCue(id, event);
                }

                @Override public void render(SkillVfx.LevelRenderContext ctx, SkillVfx.Track track, long age, long time, List<Quad> quads, Vec3 camera) {
                    draw(id, track, age, time, ctx.sprites(), camera);
                }
            });
        }
    }

    private static CastMeta defaultMeta(String id) {
        if (SunMoonSkills.MOON.equals(id)) return new CastMeta(3.0, 0L, 0.0);
        if (SunMoonSkills.SUN.equals(id)) return new CastMeta(3.0, 350L, 0.0);
        if (SunMoonSkills.FLARE.equals(id)) return new CastMeta(4.0, 0L, 0.0);
        if (SunMoonSkills.CORONA.equals(id)) return new CastMeta(4.0, 800L, 2.0);
        if (SunMoonSkills.STARFALL.equals(id)) return new CastMeta(4.0, 250L, 0.0);
        if (SunMoonSkills.PARADOX.equals(id)) return new CastMeta(2.5, 600L, 0.0);
        return new CastMeta(3.0, 0L, 0.0);
    }

    // --- Layered Audio & Calibrated Camera Shake -----------------------------

    private static void playCue(String id, SkillVisualEvent event) {
        var phase = event.phase();
        Vec3 pos = vector(event.position());
        double x = pos.x, y = pos.y, z = pos.z;

        switch (id) {
            case SunMoonSkills.MOON -> {
                if (phase == START) {
                    SkillSound.playAt(x, y, z, SoundEvents.BREEZE_SHOOT, 0.54f, 1.24f);
                    SkillSound.playAt(x, y, z, SoundEvents.AMETHYST_BLOCK_CHIME, 0.42f, 1.48f);
                } else if (phase == FINISH) {
                    SkillSound.playAt(x, y, z, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.72f, 0.92f);
                    SkillSound.playAt(x, y, z, SoundEvents.GLASS_BREAK, 0.44f, 1.32f);
                    CameraShake.impulse(pos, 14, 0.20f, 60L, 140L);
                }
            }
            case SunMoonSkills.SUN -> {
                if (phase == START) {
                    SkillSound.playAt(x, y, z, SoundEvents.FIRECHARGE_USE, 0.56f, 1.16f);
                } else if (phase == FINISH) {
                    SkillSound.playAt(x, y, z, SoundEvents.FIREWORK_ROCKET_BLAST, 0.68f, 1.18f);
                    SkillSound.playAt(x, y, z, SoundEvents.BLAZE_SHOOT, 0.48f, 1.04f);
                    CameraShake.impulse(pos, 14, 0.22f, 65L, 145L);
                }
            }
            case SunMoonSkills.REFLUX -> {
                if (phase == START) {
                    SkillSound.playAt(x, y, z, SoundEvents.BEACON_POWER_SELECT, 0.58f, 1.44f);
                    SkillSound.playAt(x, y, z, SoundEvents.AMETHYST_BLOCK_CHIME, 0.52f, 1.62f);
                }
            }
            case SunMoonSkills.FLARE -> {
                if (phase == START) {
                    SkillSound.playAt(x, y, z, SoundEvents.BLAZE_SHOOT, 0.62f, 0.84f);
                    SkillSound.playAt(x, y, z, SoundEvents.FIRECHARGE_USE, 0.50f, 0.76f);
                } else if (phase == FINISH) {
                    SkillSound.playAt(x, y, z, SoundEvents.GENERIC_EXPLODE.value(), 0.78f, 0.96f);
                    SkillSound.playAt(x, y, z, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, 0.66f, 0.86f);
                    CameraShake.impulse(pos, 18, 0.38f, 95L, 210L);
                }
            }
            case SunMoonSkills.CORONA -> {
                if (phase == START) {
                    SkillSound.playAt(x, y, z, SoundEvents.BEACON_ACTIVATE, 0.64f, 1.24f);
                    SkillSound.playAt(x, y, z, SoundEvents.RESPAWN_ANCHOR_CHARGE, 0.46f, 1.15f);
                } else if (phase == FINISH) {
                    SkillSound.playAt(x, y, z, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, 0.88f, 0.72f);
                    SkillSound.playAt(x, y, z, SoundEvents.GENERIC_EXPLODE.value(), 0.76f, 0.78f);
                    SkillSound.playAt(x, y, z, SoundEvents.LIGHTNING_BOLT_THUNDER, 0.40f, 1.05f);
                    CoronaBlur.flash(pos);
                    CameraShake.impulse(pos, 22, 0.52f, 130L, 290L);
                }
            }
            case SunMoonSkills.STARFALL -> {
                if (phase == START) {
                    SkillSound.playAt(x, y, z, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.60f, 1.38f);
                    SkillSound.playAt(x, y, z, SoundEvents.BEACON_POWER_SELECT, 0.40f, 1.52f);
                } else if (phase == SEGMENT) {
                    float pitch = 1.22f + (event.sequence() % 4) * 0.08f;
                    SkillSound.playAt(x, y, z, SoundEvents.AMETHYST_CLUSTER_HIT, 0.42f, pitch);
                    SkillSound.playAt(x, y, z, SoundEvents.GLASS_BREAK, 0.22f, pitch + 0.25f);
                    CameraShake.impulse(pos, 14, 0.07f, 40L, 85L);
                } else if (phase == FINISH) {
                    SkillSound.playAt(x, y, z, SoundEvents.AMETHYST_BLOCK_CHIME, 0.58f, 0.96f);
                }
            }
            case SunMoonSkills.PARADOX -> {
                if (phase == START) {
                    SkillSound.playAt(x, y, z, SoundEvents.BEACON_POWER_SELECT, 0.68f, 0.76f);
                    SkillSound.playAt(x, y, z, SoundEvents.END_PORTAL_FRAME_FILL, 0.54f, 1.24f);
                } else if (phase == FINISH) {
                    SkillSound.playAt(x, y, z, SoundEvents.GENERIC_EXPLODE.value(), 0.80f, 0.88f);
                    SkillSound.playAt(x, y, z, SoundEvents.AMETHYST_BLOCK_BREAK, 0.68f, 0.92f);
                    SkillSound.playAt(x, y, z, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.52f, 1.28f);
                    CameraShake.impulse(pos, 18, 0.42f, 105L, 225L);
                }
            }
            default -> {}
        }
    }

    // --- Casting Hand / Staff Convergence Orb (100% Sprite, Zero WorldQuads) -

    static void handOrb(SkillVfx.Track track, Player player, long now, List<SpriteQuad> sp, Vec3 camera) {
        double age = now - track.start.startedAt();
        double duration = Math.max(1.0, track.start.duration());
        if (age < 0.0 || age >= duration) return;

        boolean ownFp = isOwnFirstPerson(player.getUUID());
        boolean chant = track.poseLead > 0L;
        double progress = age / duration;
        double push = chant ? Math.clamp((age - duration + track.poseLead) / (double) track.poseLead, 0.0, 1.0) : 1.0;

        var poseSample = Effects.VFX.playerPose(player.getUUID(), now);
        PlayerPoseAnimation.Frame frame = poseSample == null ? null : poseSample.frame();
        Vec3 aim = track.poseFollow ? player.getLookAngle() : vector(track.last.direction()).normalize();
        if (aim.lengthSqr() < 1e-6) aim = new Vec3(0, 0, 1);

        // Anchor directly onto the animated staff tip in 3rd-person, gently offset in 1st-person so crosshair stays clean
        Vec3 point;
        if (ownFp) {
            Vec3 right = rightOf(horizontalDir(aim));
            point = PlayerVisualAnchor.position(player)
                .add(0, player.getEyeHeight() - 0.24 + 0.14 * (1.0 - push), 0)
                .add(aim.scale(chant ? 0.95 + 0.15 * push : 1.15 + 1.35 * progress))
                .add(right.scale(0.34));
        } else {
            Vec3 tip = PlayerBoneAnchor.weaponTip(player, frame, 0.92);
            point = tip.lengthSqr() > 1e-4 && chant
                ? tip.add(aim.scale(0.18 * push))
                : PlayerVisualAnchor.position(player)
                    .add(0, player.getEyeHeight() - 0.14 + 0.34 * (1.0 - push), 0)
                    .add(aim.scale(chant ? 0.82 + 0.18 * push : 0.95 + 1.55 * progress));
        }

        double fpScale = ownFp ? 0.56 : 1.0;
        double radius = (chant ? 0.22 + 0.34 * Math.min(1.0, progress * 1.9) : 0.46 + 0.22 * progress) * fpScale;
        double alphaFactor = (chant ? Math.min(1.0, progress * 3.2) : (1.0 - progress)) * (ownFp ? 0.62 : 1.0);
        int alpha = Math.clamp((int) Math.round(235 * alphaFactor), 0, 255);
        if (alpha <= 4) return;

        int color = track.poseOrbColor;
        boolean isMoon = isMoonClip(track.poseClip) || color == COL_MOON_MAIN || color == 0x79D7FF;
        boolean isSun = isSunClip(track.poseClip) || color == COL_SUN_MAIN || color == 0xFF963E;

        int orbFrame = (int) Math.floorMod(now / 75L, 4L);
        if (isSun) {
            emitFramedBillboard(sp, TEX_SUN_ORB, 4, orbFrame, point, camera, radius, COL_WHITE, COL_SUN_GOLD, alpha, 0.72);
            emitTiltedRing(sp, TEX_LEY_LINES, 1, 0, point, radius * 1.28, now * 0.0055, 22.0, -18.0, COL_SUN_DARK, COL_SUN_GOLD, (int) (alpha * 0.76));
            EffectSprites.glow(sp, point, camera, radius * 1.65, COL_SUN_HOT, (int) (alpha * 0.68));
        } else if (isMoon) {
            emitFramedBillboard(sp, TEX_MOON_DISC, 4, orbFrame, point, camera, radius, COL_WHITE, COL_MOON_BRIGHT, alpha, 0.68);
            emitTiltedRing(sp, TEX_LEY_LINES, 1, 0, point, radius * 1.28, -now * 0.0052, -20.0, 22.0, COL_MOON_DARK, COL_MOON_MAIN, (int) (alpha * 0.76));
            EffectSprites.glow(sp, point, camera, radius * 1.60, COL_MOON_WHITE, (int) (alpha * 0.68));
        } else {
            // Non-SunMoon pose orb fallback (e.g., Elementalist) — crisp textured arcane ring + glow, zero WorldQuads
            emitTiltedRing(sp, TEX_LEY_LINES, 1, 0, point, radius * 1.18, now * 0.005, 18.0, 14.0, EffectSprites.darken(color, 0.35), color, (int) (alpha * 0.80));
            EffectSprites.glow(sp, point, camera, radius * 1.55, color, alpha);
            EffectSprites.glow(sp, point, camera, radius * 0.85, EffectSprites.brighten(color, 0.65), (int) (alpha * 0.88));
        }
    }

    // --- Persistent Actor Visuals: Astral Fire / Umbral Ice, Paradox Ready, Ley Lines Chant ---

    static void actors(List<SpriteQuad> sp, Vec3 camera, long serverNow, long renderNow) {
        FRAME_STARFALLS.clear();
        var mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) return;

        // 1. Orbiting Astral Fire wisps (日曜心) & Umbral Ice crystals (月弦心) for all visible SunMoon mages
        for (var actor : CareerClient.INSTANCE.view.actors) {
            UUID ownerId;
            try {
                ownerId = UUID.fromString(actor.owner);
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            int heart = actor.sunMoonHeart;
            int stacks = actor.sunMoonStacks;
            if (heart == 0 || stacks <= 0) {
                LAST_HEART.remove(ownerId);
                LAST_STACKS.remove(ownerId);
                continue;
            }

            Player player = mc.level.getPlayerByUUID(ownerId);
            if (player == null || !player.isAlive()) continue;

            Integer prevHeart = LAST_HEART.put(ownerId, heart);
            Integer prevStacks = LAST_STACKS.put(ownerId, stacks);
            boolean flipped = prevHeart != null && prevHeart != heart;
            boolean gained = prevStacks != null && stacks > prevStacks;
            Vec3 base = PlayerVisualAnchor.position(player);
            if (flipped || gained) {
                HEART_GAIN_FLASH_AT.put(ownerId, renderNow);
                int popCol = heart == 1 ? COL_SUN_GOLD : COL_MOON_MAIN;
                WorldEffects.playShockwave(base.add(0, 1.05, 0), 0.32, flipped ? 1.85 : 1.35, popCol, 195, 290L);
            }

            boolean ownFp = isOwnFirstPerson(ownerId);
            boolean sun = heart == 1;
            int visibleOrbs = Math.clamp(stacks, 1, 6);
            boolean overcap = stacks > 3;
            long sinceGain = renderNow - HEART_GAIN_FLASH_AT.getOrDefault(ownerId, 0L);
            double gainFlash = sinceGain >= 0L && sinceGain < 360L ? (1.0 - sinceGain / 360.0) : 0.0;

            Vec3 forward = horizontalDir(PlayerVisualAnchor.forward(player));
            double orbitR = ownFp ? 1.12 : (overcap ? 0.90 : 0.80);
            double baseY = ownFp ? 0.96 : 1.14;
            double alphaScale = ownFp ? 0.34 : 1.0;
            Vec3 orbitCenter = base.subtract(forward.scale(ownFp ? 0.34 : 0.10)).add(0, baseY, 0);

            int animFrame = (int) Math.floorMod(renderNow / 115L, 4L);
            double speed = sun ? 0.0024 : -0.0021;
            int accent = sun ? COL_SUN_GOLD : COL_MOON_MAIN;
            int hot = sun ? COL_SUN_HOT : COL_MOON_WHITE;
            int dark = sun ? COL_SUN_DARK : COL_MOON_DARK;

            for (int i = 0; i < visibleOrbs; i++) {
                double ang = renderNow * speed + i * (Math.TAU / visibleOrbs);
                double bob = Math.sin(renderNow * 0.0048 + i * 1.7) * 0.14;
                Vec3 orbPos = orbitCenter.add(Math.cos(ang) * orbitR, bob, Math.sin(ang) * orbitR);

                boolean newest = (i == visibleOrbs - 1) && gainFlash > 0.02;
                double rad = (overcap ? 0.27 : 0.24) + (newest ? 0.10 * gainFlash : 0.0);
                int a = Math.clamp((int) Math.round((newest ? 250 : 230) * alphaScale), 0, 255);

                Identifier tex = sun ? TEX_ASTRAL_WISP : TEX_UMBRAL_CRYSTAL;
                emitFramedBillboard(sp, tex, 4, (animFrame + i) & 3, orbPos, camera, rad, COL_WHITE, accent, a, 0.72);
                EffectSprites.glow(sp, orbPos, camera, rad * 1.48, newest ? hot : accent, Math.clamp((int) Math.round(135 * alphaScale), 0, 255));

                if (!ownFp) {
                    RibbonTrail.push("sm_orb:" + ownerId + ":" + i, orbPos, 0.075, accent, 145, 220L, renderNow);
                }
            }

            // Full 3+ stack astral/umbral ground aura (intensified & ringed when equipment raises cap > 3!)
            if (stacks >= 3) {
                double pulse = 0.5 + 0.5 * Math.sin(renderNow * 0.0062);
                double ringScale = (overcap ? 1.32 : 1.05) + 0.06 * pulse;
                int auraAlpha = Math.clamp((int) Math.round((overcap ? 130 : 88) * (ownFp ? 0.40 : 1.0)), 0, 255);
                emitLeyLines(sp, base.add(0, 0.036, 0), ringScale, renderNow * (sun ? 0.0011 : -0.0010), dark, accent, hot, auraAlpha, overcap);
            }
        }

        // 2. Local player Paradox Ready (悖论就绪) subtle dual Ice-Cyan × Crimson-Magenta orbit ring
        Player self = mc.player;
        if (self != null && self.isAlive() && CareerClient.INSTANCE.view.sunMoon.paradox > 0) {
            boolean ownFp = isOwnFirstPerson(self.getUUID());
            Vec3 base = PlayerVisualAnchor.position(self);
            double pulse = 0.5 + 0.5 * Math.sin(renderNow * 0.0075);
            double aScale = ownFp ? 0.36 : 1.0;

            // Dual-polarity subtle tilted eclipse ring encircling waist / underfoot
            Vec3 waist = base.add(0, ownFp ? 0.42 : 0.96, 0);
            emitTiltedRing(sp, TEX_PARADOX_SIGIL, 1, 0, waist, ownFp ? 1.12 : 0.96, renderNow * 0.0022, 16.0, -14.0, COL_WHITE, COL_PARADOX_HOT, (int) ((115 + 35 * pulse) * aScale));
            if (!ownFp) {
                double aIce = renderNow * 0.0038;
                double aFire = aIce + Math.PI;
                Vec3 pIce = waist.add(Math.cos(aIce) * 0.96, Math.sin(aIce * 2.0) * 0.14, Math.sin(aIce) * 0.96);
                Vec3 pFire = waist.add(Math.cos(aFire) * 0.96, -Math.sin(aIce * 2.0) * 0.14, Math.sin(aFire) * 0.96);
                EffectSprites.glow(sp, pIce, camera, 0.30, COL_PARADOX_ICE, 185);
                EffectSprites.glow(sp, pFire, camera, 0.30, COL_PARADOX_FIRE, 185);
                RibbonTrail.push("sm_pdx_ice:" + self.getUUID(), pIce, 0.055, COL_PARADOX_ICE, 140, 210L, renderNow);
                RibbonTrail.push("sm_pdx_fire:" + self.getUUID(), pFire, 0.055, COL_PARADOX_FIRE, 140, 210L, renderNow);
            }
        }

        // 3. Active Chant Visuals: FF14 Black Mage Ley Lines underfoot + ground targeting astrolabe!
        var skill = ChantClient.skill();
        if (ChantClient.active() && skill != null && SunMoonSkills.contains(skill.id) && self != null && self.isAlive()) {
            boolean ownFp = isOwnFirstPerson(self.getUUID());
            boolean sun = skill.aspect == SkillDefinition.Aspect.SUN;
            int dark = sun ? COL_SUN_DARK : COL_MOON_DARK;
            int main = sun ? COL_SUN_GOLD : COL_MOON_MAIN;
            int hot = sun ? COL_SUN_HOT : COL_MOON_WHITE;
            double pulse = 0.5 + 0.5 * Math.sin(renderNow * 0.009);
            Vec3 casterFoot = PlayerVisualAnchor.position(self).add(0, 0.044, 0);

            // Underfoot rotating FF14 Black Mage Ley Lines!
            int leyAlpha = Math.clamp((int) Math.round((195 + 35 * pulse) * (ownFp ? 0.52 : 1.0)), 0, 255);
            emitLeyLines(sp, casterFoot, 1.58 + 0.08 * pulse, renderNow * (sun ? 0.0018 : -0.0016), dark, main, hot, leyAlpha, true);

            // Ground targeting astrolabe ring at aimed point (accurate to ChantClient.radius()!)
            if (skill.groundTargeted) {
                try {
                    var aim = SkillTargeting.ground(self, ChantClient.range(),
                        e -> e.isAlive() && (e instanceof net.minecraft.world.entity.monster.Enemy
                            || e instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon));
                    double targetR = Math.max(0.8, ChantClient.radius());
                    Vec3 ground = aim.point().add(0, 0.048, 0);
                    emitLeyLines(sp, ground, targetR, -renderNow * 0.0015, dark, main, hot, (int) (185 + 30 * pulse), false);
                    double contract = 1.0 - ((renderNow % 640L) / 640.0);
                    EffectSprites.shockwave(sp, ground.add(0, 0.012, 0), targetR * (0.28 + 0.72 * contract), renderNow * 0.003, hot, (int) (155 * contract));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
    }

    // --- Active Skill Choreography (All 7 SunMoon Spells, Zero WorldQuads) ---

    private static void draw(String id, SkillVfx.Track t, long age, long time, List<SpriteQuad> sp, Vec3 camera) {
        CastMeta meta = METAS.getOrDefault(t.start.cast(), defaultMeta(id));
        Vec3 origin = vector(t.start.position());
        Vec3 point = vector(t.last.position());
        boolean finished = t.last.phase() == FINISH;
        long after = finished ? Math.max(0L, time - t.last.at()) : 0L;

        switch (id) {
            case SunMoonSkills.MOON -> drawMoonFlow(t, meta, point, finished, after, time, sp, camera);
            case SunMoonSkills.SUN -> drawSunfire(t, meta, origin, point, finished, age, after, time, sp, camera);
            case SunMoonSkills.REFLUX -> drawMoonReflux(t, origin, age, time, sp, camera);
            case SunMoonSkills.FLARE -> drawSolarFlare(t, meta, origin, point, finished, age, after, time, sp, camera);
            case SunMoonSkills.CORONA -> drawCorona(t, meta, origin, point, finished, age, after, time, sp, camera);
            case SunMoonSkills.STARFALL -> drawStarfall(t, meta, origin, finished, age, after, time, sp, camera);
            case SunMoonSkills.PARADOX -> drawParadox(t, meta, origin, point, finished, age, after, time, sp, camera);
            default -> {}
        }
    }

    // --- 1. 月流 (Moon Flow) = FF14 Blizzard I/III × Moon Phantom ------------

    private static void drawMoonFlow(
        SkillVfx.Track t, CastMeta meta, Vec3 point, boolean finished, long after, long time,
        List<SpriteQuad> sp, Vec3 camera
    ) {
        int orbFrame = (int) Math.floorMod(time / 85L, 4L);
        if (!finished) {
            // In-flight frosty moonlight comet + crystal halo + glacial ribbon trail
            emitFramedBillboard(sp, TEX_MOON_DISC, 4, orbFrame, point, camera, 0.56, COL_WHITE, COL_MOON_BRIGHT, 245, 0.72);
            emitTiltedRing(sp, TEX_LEY_LINES, 1, 0, point, 0.64, time * 0.008, 28.0, -24.0, COL_MOON_DARK, COL_MOON_MAIN, 195);
            EffectSprites.glow(sp, point, camera, 1.05, COL_MOON_MAIN, 215);
            RibbonTrail.push("sm_moon:" + t.start.cast(), point, 0.24, COL_MOON_MAIN, 225, 290L, time);

            // 2 tiny orbiting frost crystal flecks trailing behind the comet
            for (int i = 0; i < 2; i++) {
                double a = time * 0.012 + i * Math.PI;
                Vec3 shardPos = point.add(Math.cos(a) * 0.34, Math.sin(a * 1.4) * 0.22, Math.sin(a) * 0.34);
                emitFramedBillboard(sp, TEX_UMBRAL_CRYSTAL, 4, (orbFrame + i) & 3, shardPos, camera, 0.16, COL_WHITE, COL_MOON_WHITE, 210, 0.65);
            }
            return;
        }

        // Deployed Moon Phantom + Blizzard III crystalline burst (2000ms linger)
        double uTotal = Math.clamp(after / 2000.0, 0.0, 1.0);
        double fade = 1.0 - uTotal * uTotal;
        if (fade <= 0.01) return;

        double deploy = EffectSprites.easeOutCubic(Math.min(1.0, after / 220.0));
        double moonR = 1.48 * (0.28 + 0.72 * deploy);
        double groundR = meta.radius * (0.35 + 0.65 * deploy);
        Vec3 ground = point.add(0, -Math.min(1.15, moonR * 0.72), 0);

        // Radiant suspended Moon Phantom + tilted silvery-azure celestial halo
        emitFramedBillboard(sp, TEX_MOON_DISC, 4, orbFrame, point, camera, moonR, COL_WHITE, COL_MOON_BRIGHT, (int) (240 * fade), 0.65);
        emitTiltedRing(sp, TEX_LEY_LINES, 1, 0, point, moonR * 1.16, time * 0.0018, 22.0, 14.0, COL_MOON_DARK, COL_MOON_MAIN, (int) (175 * fade));
        EffectSprites.glow(sp, point, camera, moonR * 1.75, COL_MOON_MAIN, (int) (165 * fade));

        // Ground Blizzard III frost astrolabe sigil
        emitLeyLines(sp, ground.add(0, 0.04, 0), groundR * 1.04, -time * 0.0010, COL_MOON_DARK, COL_MOON_MAIN, COL_MOON_WHITE, (int) (210 * fade), true);

        // Initial 6-fold hexagonal Blizzard III frost burst & glacial shockwave (0..620ms)
        if (after < 620L) {
            double bu = after / 620.0;
            int bf = Math.clamp((int) (bu * 8), 0, 7);
            emitFramedBillboard(sp, TEX_FROST_BURST, 8, bf, point, camera, meta.radius * (0.65 + 0.48 * EffectSprites.easeOutCubic(bu)), COL_WHITE, COL_MOON_WHITE, (int) (245 * (1.0 - bu * 0.65)), 0.78);
            emitGroundFrame(sp, TEX_FROST_BURST, 8, bf, ground.add(0, 0.06, 0), meta.radius * 1.18, bu * 0.6, COL_WHITE, COL_MOON_BRIGHT, (int) (230 * (1.0 - bu * 0.6)), 0.72);
            EffectSprites.shockwave(sp, ground.add(0, 0.08, 0), meta.radius * 1.24 * EffectSprites.easeOutCubic(bu), bu * 1.4, COL_MOON_BRIGHT, (int) (225 * (1.0 - bu)));
        }

        // Ring of 5 faceted Blizzard III ice crystal spires bursting & shattering (0..920ms)
        if (after < 920L) {
            double su = after / 920.0;
            int sf = Math.clamp((int) (su * 8), 0, 7);
            double seedAngle = (t.start.seed() & 0xFF) * 0.025;
            for (int i = 0; i < 5; i++) {
                double ang = seedAngle + i * (Math.TAU / 5.0);
                double dist = meta.radius * 0.62;
                Vec3 spikeBase = ground.add(Math.cos(ang) * dist, 0.02, Math.sin(ang) * dist);
                Vec3 leanDir = new Vec3(Math.cos(ang) * 0.24, 1.0, Math.sin(ang) * 0.24).normalize();
                emitAxialSpike(sp, TEX_FROST_SPIKE, sf, spikeBase, leanDir, 0.54, 1.85 + 0.35 * (i % 2), ang, COL_WHITE, COL_MOON_BRIGHT, (int) (235 * (1.0 - su * 0.35)), 0.62);
            }
        }
    }

    // --- 2. 日炎 (Sunfire) = FF14 Fire I (火炎) ------------------------------

    private static void drawSunfire(
        SkillVfx.Track t, CastMeta meta, Vec3 origin, Vec3 point, boolean finished,
        long age, long after, long time, List<SpriteQuad> sp, Vec3 camera
    ) {
        if (!finished) {
            long delay = Math.max(120L, meta.impactDelayMs);
            double u = Math.clamp(age / (double) delay, 0.0, 1.0);
            Vec3 from = origin.add(vector(t.start.direction()));
            Vec3 pos = from.lerp(origin, u).add(0, Math.sin(u * Math.PI) * 1.35, 0);
            int orbFrame = (int) Math.floorMod(time / 65L, 4L);

            // Brisk condensed FF14 Fire I blazing fireball arcing down into the target
            emitFramedBillboard(sp, TEX_SUN_ORB, 4, orbFrame, pos, camera, 0.72, COL_WHITE, COL_SUN_GOLD, 250, 0.78);
            emitTiltedRing(sp, TEX_CORONA_RING, 1, 0, pos, 0.76, time * 0.011, 30.0, -20.0, COL_WHITE, COL_SUN_GOLD, 195);
            EffectSprites.glow(sp, pos, camera, 1.35, COL_SUN_MAIN, 225);
            RibbonTrail.push("sm_sun:" + t.start.cast(), pos, 0.28, COL_SUN_MAIN, 230, 260L, time);

            // Crisp fiery landing glyph expanding at target point
            emitLeyLines(sp, origin.add(0, 0.045, 0), meta.radius * (0.55 + 0.45 * u), time * 0.0024, COL_SUN_DARK, COL_SUN_MAIN, COL_SUN_HOT, (int) (155 + 55 * u), false);
            return;
        }

        // Fire I snappy fiery detonation (0..920ms)
        double u = Math.clamp(after / 920.0, 0.0, 0.999);
        double fade = 1.0 - u;
        if (fade <= 0.01) return;

        // Lingering scorched crater & flame glyph
        emitGroundSprite(sp, TEX_SOLAR_CRATER, point.add(0, 0.04, 0), meta.radius * 0.95, (t.start.seed() & 63) * 0.1, COL_WHITE, COL_SUN_MAIN, (int) (215 * fade * fade), 0.45);

        // 8-frame Fire I flame-petal burst (camera-facing + ground ring)
        if (after < 680L) {
            double bu = after / 680.0;
            int bf = Math.clamp((int) (bu * 8), 0, 7);
            double burstR = meta.radius * (0.58 + 0.62 * EffectSprites.easeOutCubic(bu));
            emitFramedBillboard(sp, TEX_FIRE1_BURST, 8, bf, point.add(0, 0.75, 0), camera, burstR, COL_WHITE, COL_SUN_GOLD, (int) (250 * (1.0 - bu * 0.5)), 0.80);
            emitGroundFrame(sp, TEX_FIRE1_BURST, 8, bf, point.add(0, 0.07, 0), meta.radius * 1.12, bu * 1.1, COL_WHITE, COL_SUN_MAIN, (int) (235 * (1.0 - bu * 0.55)), 0.75);
            EffectSprites.shockwave(sp, point.add(0, 0.09, 0), meta.radius * 1.18 * EffectSprites.easeOutCubic(bu), bu * 1.5, COL_SUN_GOLD, (int) (235 * (1.0 - bu)));
            EffectSprites.glow(sp, point.add(0, 0.65, 0), camera, meta.radius * 0.92 * (1.0 - bu), COL_SUN_HOT, (int) (215 * (1.0 - bu)));
        }

        // 4 rising swirling ember wisps
        int wFrame = (int) Math.floorMod(time / 85L, 4L);
        for (int i = 0; i < 4; i++) {
            double ang = i * (Math.PI * 0.5) + u * 2.4 + (t.start.seed() & 15) * 0.2;
            double r = meta.radius * (0.25 + 0.55 * EffectSprites.easeOutCubic(u));
            double h = 0.35 + 2.1 * EffectSprites.easeOutCubic(u) * (0.75 + 0.25 * (i % 2));
            Vec3 ep = point.add(Math.cos(ang) * r, h, Math.sin(ang) * r);
            emitFramedBillboard(sp, TEX_ASTRAL_WISP, 4, (wFrame + i) & 3, ep, camera, 0.34 * (1.0 - 0.45 * u), COL_WHITE, COL_SUN_GOLD, (int) (215 * fade), 0.70);
        }
    }

    // --- 3. 月辰回流 (Moon Reflux) = FF14 Umbral Soul (灵极魂) ---------------

    private static void drawMoonReflux(
        SkillVfx.Track t, Vec3 origin, long age, long time, List<SpriteQuad> sp, Vec3 camera
    ) {
        double u = Math.clamp(age / 800.0, 0.0, 1.0);
        double fade = 1.0 - u * u;
        if (fade <= 0.01) return;

        Vec3 base = liveCasterPos(t.start.caster(), origin);
        boolean ownFp = isOwnFirstPerson(t.start.caster());
        double aScale = ownFp ? 0.48 : 1.0;

        // Underfoot lunar Ley Lines expanding & shimmering
        double sigilR = 1.15 + 1.10 * EffectSprites.easeOutCubic(u);
        emitLeyLines(sp, base.add(0, 0.048, 0), sigilR, -time * 0.0028, COL_MOON_DARK, COL_MOON_MAIN, COL_MOON_WHITE, (int) (225 * fade * aScale), true);
        EffectSprites.shockwave(sp, base.add(0, 0.07, 0), 0.85 + 1.85 * EffectSprites.easeOutCubic(u), u * 2.4, COL_MOON_BRIGHT, (int) (210 * (1.0 - u) * aScale));

        // 3 luminous Umbral Soul ice crystals spiraling INWARD into the mage's core!
        int frame = (int) Math.floorMod(time / 95L, 4L);
        double inward = 1.0 - EffectSprites.easeOutCubic(u);
        for (int i = 0; i < 3; i++) {
            double ang = i * (Math.TAU / 3.0) + u * 5.2;
            double rad = 0.28 + 1.85 * inward;
            double h = 0.35 + 1.05 * u + Math.sin(ang * 2.0) * 0.14;
            Vec3 p = base.add(Math.cos(ang) * rad, h, Math.sin(ang) * rad);
            if (!ownFp || rad > 0.75) {
                emitFramedBillboard(sp, TEX_UMBRAL_CRYSTAL, 4, (frame + i) & 3, p, camera, 0.34 * (0.55 + 0.45 * inward), COL_WHITE, COL_MOON_WHITE, (int) (240 * fade * aScale), 0.75);
                EffectSprites.glow(sp, p, camera, 0.62, COL_MOON_MAIN, (int) (185 * fade * aScale));
                RibbonTrail.push("sm_reflux:" + t.start.cast() + ":" + i, p, 0.11, COL_MOON_BRIGHT, (int) (190 * fade * aScale), 230L, time);
            }
        }
    }

    // --- 4. 烈日爆炎 (Solar Flare) = FF14 Fire III (爆炎) --------------------

    private static void drawSolarFlare(
        SkillVfx.Track t, CastMeta meta, Vec3 origin, Vec3 point, boolean finished,
        long age, long after, long time, List<SpriteQuad> sp, Vec3 camera
    ) {
        Vec3 center = finished ? point : origin;
        long elapsed = finished ? after : age;
        double u = Math.clamp(elapsed / 1200.0, 0.0, 0.999);
        double fade = 1.0 - u;
        if (fade <= 0.01) return;

        double r = meta.radius;

        // 1. Ornate Fire III scorching Ley Lines + smoldering volcanic crater
        emitGroundSprite(sp, TEX_SOLAR_CRATER, center.add(0, 0.036, 0), r * 1.12, (t.start.seed() & 63) * 0.1, COL_WHITE, COL_SUN_MAIN, (int) (235 * (1.0 - u * 0.85)), 0.50);
        emitLeyLines(sp, center.add(0, 0.052, 0), r * 1.06, time * 0.0018, COL_SUN_DARK, COL_SUN_MAIN, COL_SUN_HOT, (int) (220 * fade), true);

        // 2. Signature FF14 Fire III converging flame rings snapping inward (0..260ms)
        if (elapsed < 260L) {
            double cu = elapsed / 260.0;
            double convR = r * (1.35 - 0.95 * EffectSprites.easeOutCubic(cu));
            emitTiltedRing(sp, TEX_CORONA_RING, 1, 0, center.add(0, 0.45 + 0.6 * cu, 0), convR, time * 0.010, 8.0, -6.0, COL_WHITE, COL_SUN_GOLD, (int) (245 * (1.0 - cu * 0.35)));
            emitTiltedRing(sp, TEX_CORONA_RING, 1, 0, center.add(0, 0.95 + 0.8 * cu, 0), convR * 0.78, -time * 0.013, -14.0, 12.0, COL_WHITE, COL_SUN_HOT, (int) (225 * (1.0 - cu * 0.40)));
        }

        // 3. Roaring FF14 Fire III helical flame vortex tearing skyward (0..1080ms, 3 volumetric crossed planes!)
        if (elapsed < 1080L) {
            double vu = elapsed / 1080.0;
            int vf = Math.clamp((int) (vu * 8), 0, 7);
            double vortexW = r * (0.76 + 0.18 * EffectSprites.easeOutCubic(Math.min(1.0, vu * 2.0)));
            double vortexH = 6.6 * (0.45 + 0.55 * EffectSprites.easeOutCubic(Math.min(1.0, vu * 2.2)));
            emitVerticalCrossed(sp, TEX_FIRE3_VORTEX, vf, center.add(0, 0.04, 0), vortexW, vortexH, time * 0.0024, 3, COL_WHITE, COL_SUN_GOLD, (int) (245 * (1.0 - vu * 0.45)), 0.76);

            // Twin counter-rotating flame rings climbing the roaring Fire III vortex!
            double climb = EffectSprites.easeOutCubic(Math.min(1.0, vu * 1.4));
            emitTiltedRing(sp, TEX_CORONA_RING, 1, 0, center.add(0, 0.8 + 3.4 * climb, 0), r * (0.68 + 0.25 * vu), time * 0.0065, 16.0, 12.0, COL_WHITE, COL_SUN_GOLD, (int) (215 * (1.0 - vu)));
            emitTiltedRing(sp, TEX_CORONA_RING, 1, 0, center.add(0, 1.5 + 4.2 * climb, 0), r * (0.48 + 0.28 * vu), -time * 0.0082, -18.0, -10.0, COL_WHITE, COL_SUN_HOT, (int) (195 * (1.0 - vu)));
        }

        // 4. Fiery petal blast + staggered double molten shockwaves (60..840ms)
        if (elapsed >= 40L && elapsed < 840L) {
            double bu = (elapsed - 40L) / 800.0;
            int bf = Math.clamp((int) (bu * 8), 0, 7);
            emitGroundFrame(sp, TEX_FIRE1_BURST, 8, bf, center.add(0, 0.12, 0), r * (0.85 + 0.45 * EffectSprites.easeOutCubic(bu)), -bu * 1.4, COL_WHITE, COL_SUN_GOLD, (int) (245 * (1.0 - bu * 0.55)), 0.78);
            EffectSprites.shockwave(sp, center.add(0, 0.14, 0), r * 1.28 * EffectSprites.easeOutCubic(bu), bu * 1.8, COL_SUN_GOLD, (int) (240 * (1.0 - bu)));
            if (bu > 0.16) {
                double bu2 = (bu - 0.16) / 0.84;
                EffectSprites.shockwave(sp, center.add(0, 0.24, 0), r * 1.10 * EffectSprites.easeOutCubic(bu2), -bu2 * 2.2, COL_SUN_HOT, (int) (205 * (1.0 - bu2)));
            }
            EffectSprites.glow(sp, center.add(0, 1.4, 0), camera, r * 1.25 * (1.0 - bu), COL_SUN_HOT, (int) (220 * (1.0 - bu)));
        }
    }

    // --- 5. 日冕 (Corona) = FF14 Fire IV (炽炎) + CoronaBlur -----------------

    private static void drawCorona(
        SkillVfx.Track t, CastMeta meta, Vec3 origin, Vec3 point, boolean finished,
        long age, long after, long time, List<SpriteQuad> sp, Vec3 camera
    ) {
        double r = meta.radius;
        double lift = meta.liftHeight;

        if (!finished) {
            long delay = Math.max(200L, meta.impactDelayMs);
            double u = Math.clamp(age / (double) delay, 0.0, 1.0);
            double ease = EffectSprites.easeOutCubic(u);
            Vec3 sunCenter = origin.add(0, 1.25 + (lift - 1.25 + 0.35) * ease, 0);
            int sunFrame = (int) Math.floorMod(time / 85L, 4L);

            // 1. Scorching ground Ley Lines + pre-heating molten crater beneath the rising Fire IV sun
            emitGroundSprite(sp, TEX_SOLAR_CRATER, origin.add(0, 0.036, 0), r * (0.45 + 0.65 * ease), time * 0.0005, COL_WHITE, COL_SUN_MAIN, (int) (135 + 95 * u), 0.45);
            emitLeyLines(sp, origin.add(0, 0.052, 0), r * 1.08, time * 0.0016, COL_SUN_DARK, COL_SUN_GOLD, COL_SUN_HOT, (int) (185 + 60 * u), true);

            // 2. Massive boiling 256x256 FF14 Fire IV Solar Star Orb swelling & rising!
            double sunRad = (1.35 + 1.05 * ease) * (1.0 + 0.05 * Math.sin(time * 0.025));
            emitFramedBillboard(sp, TEX_CORONA_SUN, 4, sunFrame, sunCenter, camera, sunRad, COL_WHITE, COL_SUN_GOLD, 252, 0.72);
            EffectSprites.glow(sp, sunCenter, camera, sunRad * 1.68, COL_SUN_MAIN, (int) (195 + 50 * u));

            // 3. Three tilted counter-rotating fiery coronal rings encasing the rising Fire IV core!
            emitTiltedRing(sp, TEX_CORONA_RING, 1, 0, sunCenter, sunRad * 1.22, time * 0.0046, 26.0, 18.0, COL_WHITE, COL_SUN_GOLD, 235);
            emitTiltedRing(sp, TEX_CORONA_RING, 1, 0, sunCenter, sunRad * 1.38, -time * 0.0038, -32.0, 24.0, COL_WHITE, COL_SUN_MAIN, 215);
            emitTiltedRing(sp, TEX_LEY_LINES, 1, 0, sunCenter, sunRad * 1.10, time * 0.0030, 74.0, -12.0, COL_SUN_DARK, COL_SUN_HOT, (int) (150 + 75 * u));

            // 4. Inward-spiraling plasma tendrils feeding the imminent Fire IV detonation
            for (int i = 0; i < 4; i++) {
                double a = time * 0.006 + i * (Math.PI * 0.5);
                double outerR = r * (1.05 - 0.35 * u);
                Vec3 rim = origin.add(Math.cos(a) * outerR, 0.15 + 0.35 * (i % 2), Math.sin(a) * outerR);
                LightningBolt.bolt(sp, rim, sunCenter, camera, (time / 55L) + i * 29L, 5, 0.36, 0.055, COL_SUN_GOLD, (int) (145 + 85 * u), false);
            }
            return;
        }

        // Cataclysmic FF14 Fire IV Supernova Detonation (0..1250ms, paired with CoronaBlur!)
        double u = Math.clamp(after / 1250.0, 0.0, 0.999);
        double fade = 1.0 - u;
        if (fade <= 0.01) return;

        Vec3 ground = point.subtract(0, lift, 0);

        // 1. Deep scorched sun-crater & residual solar Ley Lines on the ground
        emitGroundSprite(sp, TEX_SOLAR_CRATER, ground.add(0, 0.04, 0), r * 1.28, (t.start.seed() & 63) * 0.1, COL_WHITE, COL_SUN_MAIN, (int) (240 * (1.0 - u * 0.78)), 0.52);
        emitLeyLines(sp, ground.add(0, 0.055, 0), r * 1.15, -time * 0.0014, COL_SUN_DARK, COL_SUN_GOLD, COL_SUN_HOT, (int) (195 * fade), false);

        // 2. Blinding white-gold Fire IV implosion core -> massive 8-frame Corona Supernova!
        if (after < 960L) {
            double nu = after / 960.0;
            int nf = Math.clamp((int) (nu * 8), 0, 7);
            double novaR = r * (0.85 + 0.85 * EffectSprites.easeOutCubic(nu));
            emitFramedBillboard(sp, TEX_CORONA_NOVA, 8, nf, point, camera, novaR, COL_WHITE, COL_SUN_HOT, (int) (255 * (1.0 - nu * 0.42)), 0.88);
            emitTiltedRing(sp, TEX_CORONA_NOVA, 8, nf, point, novaR * 0.92, nu * 1.1, 18.0, -14.0, COL_WHITE, COL_SUN_GOLD, (int) (235 * (1.0 - nu * 0.48)));
            emitGroundFrame(sp, TEX_CORONA_NOVA, 8, nf, ground.add(0, 0.10, 0), r * (0.95 + 0.52 * EffectSprites.easeOutCubic(nu)), -nu * 0.8, COL_WHITE, COL_SUN_MAIN, (int) (225 * (1.0 - nu * 0.52)), 0.72);
        }

        // 3. Towering central solar eruption pillar + double staggered superheated shockwaves
        if (after < 860L) {
            double pu = after / 860.0;
            int pf = Math.clamp((int) (pu * 8), 0, 7);
            emitVerticalCrossed(sp, TEX_FIRE3_VORTEX, pf, ground.add(0, 0.04, 0), r * 0.72, 7.6 * (0.55 + 0.45 * EffectSprites.easeOutCubic(pu)), time * 0.002, 3, COL_WHITE, COL_SUN_HOT, (int) (220 * (1.0 - pu * 0.55)), 0.82);

            double wave1 = EffectSprites.easeOutCubic(Math.min(1.0, pu * 1.25));
            EffectSprites.shockwave(sp, ground.add(0, 0.12, 0), r * 1.42 * wave1, pu * 1.6, COL_SUN_GOLD, (int) (245 * (1.0 - pu)));
            EffectSprites.shockwave(sp, point, r * 1.32 * wave1, -pu * 1.9, COL_SUN_HOT, (int) (230 * (1.0 - pu)));
            EffectSprites.glow(sp, point, camera, r * 1.65 * (1.0 - pu), COL_SUN_HOT, (int) (240 * (1.0 - pu)));
        }

        // 4. Ejected blazing solar arcs racing across the crater rim
        if (after < 560L) {
            double au = after / 560.0;
            for (int i = 0; i < 4; i++) {
                double ang = i * (Math.PI * 0.5) + 0.4 + au * 1.2;
                Vec3 rim = ground.add(Math.cos(ang) * r * (0.55 + 0.65 * au), 0.25, Math.sin(ang) * r * (0.55 + 0.65 * au));
                LightningBolt.bolt(sp, point, rim, camera, (after / 50L) + i * 37L, 5, 0.48, 0.065 * (1.0 - au * 0.6), COL_SUN_HOT, (int) (225 * (1.0 - au)), false);
            }
        }
    }

    // --- 6. 月见星辉 (Starfall) = FF14 Blizzard IV × Freeze (Overlap-Safe) ---

    private static void drawStarfall(
        SkillVfx.Track t, CastMeta meta, Vec3 origin, boolean finished,
        long age, long after, long time, List<SpriteQuad> sp, Vec3 camera
    ) {
        double fade = finished ? Math.clamp(1.0 - after / 700.0, 0.0, 1.0) : 1.0;
        if (fade <= 0.01) return;

        // Overlap-safe alpha scaling when player stacks multiple Starfall casts in the same zone!
        int overlaps = 1;
        for (Vec3 existing : FRAME_STARFALLS) {
            if (existing.distanceToSqr(origin) < 36.0) overlaps++;
        }
        FRAME_STARFALLS.add(origin);
        double overlapScale = 1.0 / Math.sqrt(overlaps);

        double r = meta.radius;
        Vec3 ground = origin.add(0, 0.04, 0);
        double dropU = Math.clamp(age / 250.0, 0.0, 1.0);
        double dropY = 8.5 * (1.0 - EffectSprites.easeOutCubic(dropU));
        double pillarH = Math.max(0.4, 9.2 - dropY);

        // 1. Grand rotating astrological Moon-Star Ley Lines floor seal + inner frost ring
        int sigilAlpha = Math.clamp((int) Math.round(215 * fade * overlapScale), 0, 255);
        emitLeyLines(sp, ground, r * 1.06, time * 0.0009, COL_MOON_DARK, COL_MOON_MAIN, COL_MOON_WHITE, sigilAlpha, true);
        emitTiltedRing(sp, TEX_LEY_LINES, 1, 0, ground.add(0, 0.08, 0), r * 0.56, -time * 0.0018, 0.0, 0.0, COL_MOON_DARK, COL_MOON_BRIGHT, (int) (165 * fade * overlapScale));

        // 2. Silky celestial descending Moonlight Aurora Column (3 crossed volumetric planes, soft overlap alpha!)
        int auroraFrame = (int) Math.floorMod(time / 110L, 4L);
        int auroraAlpha = Math.clamp((int) Math.round(168 * fade * overlapScale), 0, 255);
        emitVerticalCrossed4(sp, TEX_MOON_AURORA, auroraFrame, ground.add(0, dropY, 0), r * 0.42, pillarH, time * 0.0007, 3, COL_WHITE, COL_MOON_BRIGHT, auroraAlpha, 0.58 * overlapScale);

        // 3. Cascading celestial falling Starlight Meteors raining across the 4-block arena
        int starFrame = (int) Math.floorMod(time / 95L, 4L);
        int starCount = overlaps > 2 ? 9 : 14;
        for (int i = 0; i < starCount; i++) {
            double a = i * 2.399963 + (t.start.seed() & 31) * 0.1;
            double sr = Math.sqrt((i + 0.5) / starCount) * (r * 0.88);
            long cycle = Math.floorMod(age + i * 113L, 1150L);
            double sy = 8.4 * (1.0 - cycle / 1150.0);
            Vec3 starPos = ground.add(Math.cos(a) * sr, sy, Math.sin(a) * sr);
            double starFade = fade * Math.min(1.0, cycle / 120.0) * Math.min(1.0, (1150L - cycle) / 140.0);
            int sa = Math.clamp((int) Math.round(235 * starFade * (0.7 + 0.3 * overlapScale)), 0, 255);
            if (sa > 8) {
                emitFramedBillboard(sp, TEX_STAR_METEOR, 4, (starFrame + i) & 3, starPos, camera, 0.46, COL_WHITE, COL_MOON_WHITE, sa, 0.72);
            }
        }

        // 4. Every SEGMENT hit beat erupts a crisp Blizzard IV / Freeze hexagonal crystal spire & burst!
        for (var ev : t.flashes) {
            if (ev.phase() != SEGMENT) continue;
            long segAge = time - ev.at();
            if (segAge < 0L || segAge >= 560L) continue;
            double su = segAge / 560.0;
            int sf = Math.clamp((int) (su * 8), 0, 7);
            long h = t.start.seed() ^ ((long) ev.sequence() * 0x9E3779B97F4A7C15L);
            double ang = ((h & 0xFFFF) / 65535.0) * Math.TAU;
            double dist = Math.sqrt(((h >>> 16) & 0xFFFF) / 65535.0) * (r * 0.72);
            Vec3 hitBase = ground.add(Math.cos(ang) * dist, 0.02, Math.sin(ang) * dist);
            int ba = Math.clamp((int) Math.round(235 * (1.0 - su * 0.42) * fade), 0, 255);

            // Erupting faceted Blizzard IV ice crystal cluster + hexagonal floor burst
            emitVerticalCrossed(sp, TEX_FROST_SPIKE, sf, hitBase, 0.72, 2.35, ang, 2, COL_WHITE, COL_MOON_BRIGHT, ba, 0.64);
            emitGroundFrame(sp, TEX_FROST_BURST, 8, sf, hitBase.add(0, 0.05, 0), 1.45, ang, COL_WHITE, COL_MOON_WHITE, (int) (ba * 0.90), 0.72);
            if (segAge < 320L) {
                double wu = segAge / 320.0;
                EffectSprites.shockwave(sp, hitBase.add(0, 0.07, 0), 1.75 * EffectSprites.easeOutCubic(wu), ang, COL_MOON_WHITE, (int) (185 * (1.0 - wu) * fade));
            }
        }
    }

    // --- 7. 悖论 (Paradox — 50级觉醒) = FF14 Paradox (2.5-block radius) ------

    private static void drawParadox(
        SkillVfx.Track t, CastMeta meta, Vec3 origin, Vec3 point, boolean finished,
        long age, long after, long time, List<SpriteQuad> sp, Vec3 camera
    ) {
        double r = meta.radius; // 2.5 blocks (syncs with SunMoonSkills + sun_moon.json!)

        if (!finished) {
            long delay = Math.max(150L, meta.impactDelayMs);
            double u = Math.clamp(age / (double) delay, 0.0, 1.0);
            Vec3 base = t.last.phase() == TRAIL ? point : origin;
            int orbFrame = (int) Math.floorMod(time / 65L, 4L);

            // 1. Counter-rotating Sun-Moon Eclipse Ground Sigil tightening around the 2.5-block blast zone!
            double sigilR = r * (1.28 - 0.24 * EffectSprites.easeOutCubic(u));
            emitGroundSprite(sp, TEX_PARADOX_SIGIL, base.add(0, 0.048, 0), sigilR, time * 0.0032, COL_WHITE, COL_PARADOX_HOT, (int) (175 + 75 * u), 0.62);
            emitTiltedRing(sp, TEX_LEY_LINES, 1, 0, base.add(0, 0.12 + 2.4 * (1.0 - u), 0), r * (0.95 - 0.55 * u), -time * 0.005, 10.0, -8.0, COL_PARADOX_VOID, COL_PARADOX_HOT, (int) (165 + 75 * u));

            // 2. Twin violent FF14 Paradox double-helix comets: Glacial Ice-Blue × Scorching Crimson-Magenta!
            Vec3 iceHead = paradoxHelix(base, u, 0, r);
            Vec3 fireHead = paradoxHelix(base, u, 1, r);

            for (int side = 0; side < 2; side++) {
                boolean isIce = side == 0;
                int col = isIce ? COL_PARADOX_ICE : COL_PARADOX_FIRE;
                List<Vec3> pts = new ArrayList<>(26);
                for (int i = 0; i < 26; i++) {
                    double a = u - i * 0.011;
                    if (a >= 0.0) pts.add(paradoxHelix(base, a, side, r));
                }
                RibbonTrail.ribbon(sp, pts, camera, 0.22, col, 240);

                Vec3 head = isIce ? iceHead : fireHead;
                if (isIce) {
                    emitFramedBillboard(sp, TEX_UMBRAL_CRYSTAL, 4, orbFrame, head, camera, 0.44, COL_WHITE, COL_PARADOX_ICE, 250, 0.80);
                } else {
                    emitFramedBillboard(sp, TEX_SUN_ORB, 4, orbFrame, head, camera, 0.46, COL_PARADOX_FIRE, COL_PARADOX_HOT, 250, 0.85);
                }
                EffectSprites.glow(sp, head, camera, 0.92, col, 240);
            }

            // 3. Crackling eclipse arc bridging the opposing Ice & Fire comets as they converge!
            if (u > 0.25) {
                LightningBolt.bolt(sp, iceHead, fireHead, camera, (time / 45L) ^ t.start.seed(), 6, 0.32, 0.055, COL_PARADOX_HOT, (int) (160 + 90 * u), false);
            }
            return;
        }

        // FF14 Paradox 50-Level Awakening Dual-Polarity Cross-Star Supernova (0..1050ms)!
        double u = Math.clamp(after / 1050.0, 0.0, 0.999);
        double fade = 1.0 - u;
        if (fade <= 0.01) return;

        Vec3 center = point.add(0, 0.85, 0);

        // 1. Burning Sun-Moon Eclipse Ground Sigil marking the full 2.5-block detonation zone!
        emitGroundSprite(sp, TEX_PARADOX_SIGIL, point.add(0, 0.05, 0), r * 1.12, -time * 0.0020, COL_WHITE, COL_PARADOX_HOT, (int) (245 * fade), 0.72);

        // 2. Signature 8-frame Paradox Ice × Crimson-Magenta Cross-Star Supernova (billboard + tilted + ground)!
        if (after < 860L) {
            double bu = after / 860.0;
            int bf = Math.clamp((int) (bu * 8), 0, 7);
            double burstR = r * (0.72 + 0.68 * EffectSprites.easeOutCubic(bu));
            emitFramedBillboard(sp, TEX_PARADOX_BURST, 8, bf, center, camera, burstR, COL_WHITE, COL_PARADOX_HOT, (int) (255 * (1.0 - bu * 0.40)), 0.85);
            emitTiltedRing(sp, TEX_PARADOX_BURST, 8, bf, center, burstR * 0.92, bu * 1.2, 22.0, -24.0, COL_WHITE, COL_PARADOX_HOT, (int) (230 * (1.0 - bu * 0.45)));
            emitGroundFrame(sp, TEX_PARADOX_BURST, 8, bf, point.add(0, 0.10, 0), r * (0.85 + 0.42 * EffectSprites.easeOutCubic(bu)), -bu * 0.9, COL_WHITE, COL_PARADOX_HOT, (int) (235 * (1.0 - bu * 0.45)), 0.76);
        }

        // 3. Vertical ~3.8-block astral rift clash: crystalline Ice Spire + Crimson Flame Vortex simultaneously!
        if (after < 760L) {
            double vu = after / 760.0;
            int vf = Math.clamp((int) (vu * 8), 0, 7);
            emitVerticalCrossed(sp, TEX_FROST_SPIKE, vf, point.add(0, 0.04, 0), r * 0.48, 3.85, 0.35, 2, COL_WHITE, COL_PARADOX_ICE, (int) (225 * (1.0 - vu * 0.50)), 0.78);
            emitVerticalCrossed(sp, TEX_FIRE3_VORTEX, vf, point.add(0, 0.04, 0), r * 0.52, 3.65, 1.15, 2, COL_PARADOX_FIRE, COL_PARADOX_HOT, (int) (215 * (1.0 - vu * 0.50)), 0.82);

            // Staggered dual-polarity Ice-Cyan & Crimson-Magenta shockwaves expanding across the 2.5-block radius!
            double w1 = EffectSprites.easeOutCubic(Math.min(1.0, vu * 1.35));
            EffectSprites.shockwave(sp, point.add(0, 0.14, 0), r * 1.24 * w1, vu * 2.2, COL_PARADOX_ICE, (int) (245 * (1.0 - vu)));
            EffectSprites.shockwave(sp, point.add(0, 0.26, 0), r * 1.16 * w1, -vu * 2.6, COL_PARADOX_FIRE, (int) (245 * (1.0 - vu)));
            EffectSprites.glow(sp, center, camera, r * 1.22 * (1.0 - vu), COL_PARADOX_HOT, (int) (235 * (1.0 - vu)));
        }

        // 4. Opposing crackling Ice & Crimson lightning arcs lashing across the eclipse zone
        if (after < 460L) {
            double au = after / 460.0;
            for (int i = 0; i < 4; i++) {
                double ang = i * (Math.PI * 0.5) + 0.5 + au * 1.6;
                Vec3 rim = point.add(Math.cos(ang) * r * (0.45 + 0.65 * au), 0.20 + 0.75 * (i % 2), Math.sin(ang) * r * (0.45 + 0.65 * au));
                int arcCol = (i & 1) == 0 ? COL_PARADOX_ICE : COL_PARADOX_FIRE;
                LightningBolt.bolt(sp, center, rim, camera, (after / 45L) + i * 41L, 5, 0.36, 0.055 * (1.0 - au * 0.55), arcCol, (int) (230 * (1.0 - au)), false);
            }
        }
    }

    private static Vec3 paradoxHelix(Vec3 base, double u, int side, double radius) {
        double a = u * Math.PI * 5.2 + side * Math.PI;
        double spiralR = Math.max(0.95, radius * 0.64) * (1.0 - 0.82 * u);
        double y = 3.85 * (1.0 - u) + 0.42;
        return base.add(Math.cos(a) * spiralR, y, Math.sin(a) * spiralR);
    }

    // --- Shared Dual-Layer Sprite Geometry Helpers (Zero WorldQuads) ---------

    /** Dual-layer FF14 Black Mage Ley Lines ground astrolabe: dark contrast backing + luminous additive runes. */
    private static void emitLeyLines(
        List<SpriteQuad> out, Vec3 center, double radius, double rotRad,
        int darkRgb, int mainRgb, int hotRgb, int alpha, boolean doubleLayer
    ) {
        if (radius <= 0.0 || alpha <= 4) return;
        int a = Math.clamp(alpha, 0, 255);
        EffectSprites.emitGround(out, TEX_LEY_LINES, Blend.TRANSLUCENT, center, radius * 1.02, rotRad, 0f, 0f, 1f, 1f, darkRgb, (int) (a * 0.72));
        EffectSprites.emitGround(out, TEX_LEY_LINES, Blend.ADDITIVE, center.add(0, 0.012, 0), radius, rotRad, 0f, 0f, 1f, 1f, mainRgb, a);
        if (doubleLayer) {
            EffectSprites.emitGround(out, TEX_LEY_LINES, Blend.ADDITIVE, center.add(0, 0.020, 0), radius * 0.74, -rotRad * 1.35, 0f, 0f, 1f, 1f, hotRgb, (int) (a * 0.56));
        }
    }

    /** Dual-layer single-frame ground decal / crater / eclipse sigil. */
    private static void emitGroundSprite(
        List<SpriteQuad> out, Identifier texture, Vec3 center, double radius, double rotRad,
        int baseRgb, int boostRgb, int alpha, double boostScale
    ) {
        if (radius <= 0.0 || alpha <= 4) return;
        int a = Math.clamp(alpha, 0, 255);
        EffectSprites.emitGround(out, texture, Blend.TRANSLUCENT, center, radius, rotRad, 0f, 0f, 1f, 1f, baseRgb, a);
        int ba = Math.clamp((int) Math.round(a * boostScale), 0, 255);
        if (ba > 4) {
            EffectSprites.emitGround(out, texture, Blend.ADDITIVE, center.add(0, 0.012, 0), radius * 0.99, rotRad, 0f, 0f, 1f, 1f, boostRgb, ba);
        }
    }

    /** Dual-layer multi-frame ground burst. */
    private static void emitGroundFrame(
        List<SpriteQuad> out, Identifier texture, int totalFrames, int frame,
        Vec3 center, double radius, double rotRad,
        int baseRgb, int boostRgb, int alpha, double boostScale
    ) {
        if (radius <= 0.0 || alpha <= 4) return;
        float u0 = frame / (float) totalFrames;
        float u1 = (frame + 1) / (float) totalFrames;
        int a = Math.clamp(alpha, 0, 255);
        EffectSprites.emitGround(out, texture, Blend.TRANSLUCENT, center, radius, rotRad, u0, 0f, u1, 1f, baseRgb, a);
        int ba = Math.clamp((int) Math.round(a * boostScale), 0, 255);
        if (ba > 4) {
            EffectSprites.emitGround(out, texture, Blend.ADDITIVE, center.add(0, 0.014, 0), radius * 1.02, rotRad, u0, 0f, u1, 1f, boostRgb, ba);
        }
    }

    /** Ground-anchored N-plane volumetric 8-frame vertical vortex / spike (v=0 sky top, v=1 ground base). */
    private static void emitVerticalCrossed(
        List<SpriteQuad> out, Identifier texture, int frame,
        Vec3 base, double halfWidth, double height, double yawOffset, int planes,
        int baseRgb, int boostRgb, int alpha, double boostScale
    ) {
        emitVerticalStrip(out, texture, 8, frame, base, halfWidth, height, yawOffset, planes, baseRgb, boostRgb, alpha, boostScale);
    }

    /** Ground-anchored N-plane volumetric 4-frame vertical aurora column. */
    private static void emitVerticalCrossed4(
        List<SpriteQuad> out, Identifier texture, int frame,
        Vec3 base, double halfWidth, double height, double yawOffset, int planes,
        int baseRgb, int boostRgb, int alpha, double boostScale
    ) {
        emitVerticalStrip(out, texture, 4, frame, base, halfWidth, height, yawOffset, planes, baseRgb, boostRgb, alpha, boostScale);
    }

    private static void emitVerticalStrip(
        List<SpriteQuad> out, Identifier texture, int totalFrames, int frame,
        Vec3 base, double halfWidth, double height, double yawOffset, int planes,
        int baseRgb, int boostRgb, int alpha, double boostScale
    ) {
        if (halfWidth <= 0.0 || height <= 0.0 || alpha <= 4) return;
        float u0 = frame / (float) totalFrames;
        float u1 = (frame + 1) / (float) totalFrames;
        Vec3 center = base.add(0, height * 0.5, 0);
        Vec3 halfV = new Vec3(0, -height * 0.5, 0);
        int a = Math.clamp(alpha, 0, 255);
        int ba = Math.clamp((int) Math.round(a * boostScale), 0, 255);

        for (int i = 0; i < planes; i++) {
            double ang = yawOffset + i * (Math.PI / planes);
            Vec3 halfU = new Vec3(Math.cos(ang) * halfWidth, 0, Math.sin(ang) * halfWidth);
            EffectSprites.emitPlane(out, texture, Blend.TRANSLUCENT, center, halfU, halfV, u0, 0f, u1, 1f, baseRgb, a);
            if (ba > 4 && i < 2) {
                EffectSprites.emitPlane(out, texture, Blend.ADDITIVE, center, halfU.scale(0.96), halfV, u0, 0f, u1, 1f, boostRgb, ba);
            }
        }
    }

    /** Ground-anchored dual-crossed angled ice crystal spire along arbitrary axis (base -> tip). */
    private static void emitAxialSpike(
        List<SpriteQuad> out, Identifier texture, int frame,
        Vec3 base, Vec3 axisDir, double halfWidth, double length, double rollOffset,
        int baseRgb, int boostRgb, int alpha, double boostScale
    ) {
        if (halfWidth <= 0.0 || length <= 0.0 || alpha <= 4 || axisDir.lengthSqr() < 1e-6) return;
        Vec3 axis = axisDir.normalize();
        Vec3 ref = Math.abs(axis.y) > 0.92 ? new Vec3(1, 0, 0) : UP;
        Vec3 u0Vec = axis.cross(ref).normalize();
        Vec3 v0Vec = axis.cross(u0Vec).normalize();
        float u0 = frame / 8.0f;
        float u1 = (frame + 1) / 8.0f;
        Vec3 center = base.add(axis.scale(length * 0.5));
        Vec3 halfV = axis.scale(-length * 0.5);
        int a = Math.clamp(alpha, 0, 255);
        int ba = Math.clamp((int) Math.round(a * boostScale), 0, 255);

        for (int i = 0; i < 2; i++) {
            double ang = rollOffset + i * (Math.PI * 0.5);
            Vec3 halfU = u0Vec.scale(Math.cos(ang) * halfWidth).add(v0Vec.scale(Math.sin(ang) * halfWidth));
            EffectSprites.emitPlane(out, texture, Blend.TRANSLUCENT, center, halfU, halfV, u0, 0f, u1, 1f, baseRgb, a);
            if (ba > 4) {
                EffectSprites.emitPlane(out, texture, Blend.ADDITIVE, center, halfU.scale(1.03), halfV, u0, 0f, u1, 1f, boostRgb, ba);
            }
        }
    }

    /** Tilted 3D spinning arcane / coronal ring. */
    private static void emitTiltedRing(
        List<SpriteQuad> out, Identifier texture, int totalFrames, int frame,
        Vec3 center, double radius, double spinRad, double pitchDeg, double rollDeg,
        int baseRgb, int boostRgb, int alpha
    ) {
        if (radius <= 0.0 || alpha <= 4) return;
        double pitch = Math.toRadians(pitchDeg);
        double roll = Math.toRadians(rollDeg);
        Vec3 normal = new Vec3(Math.sin(roll), Math.cos(pitch) * Math.cos(roll), Math.sin(pitch)).normalize();
        Vec3 ref = Math.abs(normal.y) > 0.92 ? new Vec3(1, 0, 0) : UP;
        Vec3 uBase = normal.cross(ref).normalize();
        Vec3 vBase = normal.cross(uBase).normalize();
        double c = Math.cos(spinRad) * radius;
        double s = Math.sin(spinRad) * radius;
        Vec3 halfU = uBase.scale(c).add(vBase.scale(s));
        Vec3 halfV = vBase.scale(c).subtract(uBase.scale(s));
        float u0 = frame / (float) totalFrames;
        float u1 = (frame + 1) / (float) totalFrames;
        int a = Math.clamp(alpha, 0, 255);

        EffectSprites.emitPlane(out, texture, Blend.TRANSLUCENT, center, halfU, halfV, u0, 0f, u1, 1f, baseRgb, (int) (a * 0.65));
        EffectSprites.emitPlane(out, texture, Blend.ADDITIVE, center, halfU.scale(1.03), halfV.scale(1.03), u0, 0f, u1, 1f, boostRgb, a);
    }

    /** Upright camera-facing billboard for multi-frame horizontal sprite strips (v=0 stays top!). */
    private static void emitFramedBillboard(
        List<SpriteQuad> out, Identifier texture, int totalFrames, int frame,
        Vec3 center, Vec3 camera, double radius,
        int baseRgb, int boostRgb, int alpha, double boostScale
    ) {
        if (radius <= 0.0 || alpha <= 4) return;
        float u0 = frame / (float) totalFrames;
        float u1 = (frame + 1) / (float) totalFrames;
        Vec3 look = camera.subtract(center);
        if (look.lengthSqr() < 1e-6) look = new Vec3(0, 0, 1);
        else look = look.normalize();
        Vec3 right = look.cross(Math.abs(look.y) > 0.95 ? new Vec3(1, 0, 0) : UP).normalize();
        Vec3 up = right.cross(look).normalize();
        Vec3 halfU = right.scale(radius);
        Vec3 halfV = up.scale(-radius);
        int a = Math.clamp(alpha, 0, 255);

        EffectSprites.emitPlane(out, texture, Blend.TRANSLUCENT, center, halfU, halfV, u0, 0f, u1, 1f, baseRgb, a);
        int ba = Math.clamp((int) Math.round(a * boostScale), 0, 255);
        if (ba > 4) {
            EffectSprites.emitPlane(out, texture, Blend.ADDITIVE, center.add(look.scale(0.024)), halfU.scale(1.03), halfV.scale(1.03), u0, 0f, u1, 1f, boostRgb, ba);
        }
    }

    // --- Pure Lookup & Math Helpers ------------------------------------------

    private static boolean isSunClip(String clip) {
        if (clip == null || clip.isEmpty()) return false;
        return clip.contains("sunfire") || clip.contains("solar_flare") || clip.contains("corona");
    }

    private static boolean isMoonClip(String clip) {
        if (clip == null || clip.isEmpty()) return false;
        return clip.contains("moon_flow") || clip.contains("moon_starfall") || clip.contains("moon_reflux");
    }

    private static Vec3 liveCasterPos(UUID casterId, Vec3 fallback) {
        var mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) return fallback;
        Player p = mc.level.getPlayerByUUID(casterId);
        return p != null && p.isAlive() ? PlayerVisualAnchor.position(p) : fallback;
    }

    private static boolean isOwnFirstPerson(UUID casterId) {
        var mc = Minecraft.getInstance();
        return mc != null && mc.player != null && mc.player.getUUID().equals(casterId)
            && mc.options.getCameraType().isFirstPerson();
    }

    private static Vec3 horizontalDir(Vec3 v) {
        if (v == null || v.lengthSqr() < 1e-6) return new Vec3(0, 0, 1);
        Vec3 flat = new Vec3(v.x, 0, v.z);
        return flat.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : flat.normalize();
    }

    private static Vec3 rightOf(Vec3 forward) {
        Vec3 r = new Vec3(-forward.z, 0, forward.x);
        return r.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : r.normalize();
    }
}
