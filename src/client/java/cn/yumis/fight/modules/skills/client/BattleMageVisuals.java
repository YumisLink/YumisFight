package cn.yumis.fight.modules.skills.client;

import cn.yumis.fight.modules.presentation.api.client.CameraShake;
import cn.yumis.fight.modules.presentation.api.client.ClientUi;
import cn.yumis.fight.modules.presentation.api.client.EffectSprites;
import cn.yumis.fight.modules.presentation.api.client.EffectSprites.Blend;
import cn.yumis.fight.modules.presentation.api.client.EffectSprites.SpriteQuad;
import cn.yumis.fight.modules.presentation.api.client.LightningBolt;
import cn.yumis.fight.modules.presentation.api.client.RibbonTrail;
import cn.yumis.fight.modules.presentation.api.client.WorldEffects;
import cn.yumis.fight.modules.presentation.api.client.WorldQuads.Quad;
import cn.yumis.fight.modules.skills.api.*;
import cn.yumis.fight.modules.skills.api.client.BasicAttackVisuals;
import com.google.gson.JsonObject;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import java.util.*;

import static cn.yumis.fight.modules.skills.api.SkillVisualEvent.Phase.*;
import static cn.yumis.fight.modules.skills.client.SkillVfx.vector;

/**
 * Dedicated DNF-inspired Battle Mage / Bellatrix / Teana (战斗法师 / 贝亚娜斗神) VFX renderer.
 * Zero legacy untextured WorldQuads: 100% custom high-contrast multi-tone pixel-sharp + soft-feathered
 * sprite sheets (source: tools/build_battle_mage_effect_textures.py), dual-layer translucent silhouette
 * + additive luminous core rendering, orbiting 5-color & Teana rune-ring Chaser orbs, Battle Groove
 * stage 4-6 martial aura escalation, Teana Battle Goddess seraphic multi-feather wings & halo crest,
 * Niu 3-hit & Teana golden spear basic attacks, Dragon Fang linkage, Morph A/B differentiation,
 * Golden Eastern Dragon Crescent Halberd, and Apostle Spear conditional 3rd-person awakening camera.
 * Pure client presentation: never grants combat authority, hitboxes, cooldowns, or physics changes.
 */
final class BattleMageVisuals {
    private static final Vec3 UP = new Vec3(0, 1, 0);

    // Custom pre-baked multi-tone Battle Mage sprite sheets (source: tools/build_battle_mage_effect_textures.py)
    static final Identifier TEX_CHASER_ORB = tex("chaser_orb.png");
    static final Identifier TEX_PALM_BLOSSOM = tex("palm_blossom_burst.png");
    static final Identifier TEX_SPEAR_THRUST = tex("spear_thrust.png");
    static final Identifier TEX_MARTIAL_SLASH = tex("martial_slash.png");
    static final Identifier TEX_METEOR_DRILL = tex("meteor_drill.png");
    static final Identifier TEX_CHASER_NOVA = tex("chaser_nova.png");
    static final Identifier TEX_MARTIAL_CRATER = tex("martial_crater.png");
    static final Identifier TEX_CHASER_SIGIL = tex("chaser_sigil.png");
    static final Identifier TEX_DRAGON_HALBERD = tex("dragon_halberd_aura.png");
    static final Identifier TEX_DRAGON_PILLAR = tex("dragon_pillar.png");
    static final Identifier TEX_TEANA_WING = tex("teana_wing.png");
    static final Identifier TEX_TEANA_HALO = tex("teana_halo_crest.png");
    static final Identifier TEX_FEATHERS_MIST = tex("teana_feathers_mist.png");
    static final Identifier TEX_APOSTLE_SPEAR = tex("apostle_spear_lance.png");

    // Classic DNF Battle Mage / Bellatrix palette accents
    private static final int COL_WHITE = 0xFFFFFF;
    private static final int COL_SAKURA = 0xFF6B8A;
    private static final int COL_SAKURA_HOT = 0xFFD8E2;
    private static final int COL_MARTIAL = BattleMageRules.COLOR_MARTIAL;
    private static final int COL_MARTIAL_HOT = 0xFFE2B8;
    private static final int COL_FANG_JADE = 0x9FFFD4;
    private static final int COL_METEOR = 0xFF5A2E;
    private static final int COL_METEOR_HOT = 0xFFE699;
    private static final int COL_DRAGON_GOLD = 0xFFB833;
    private static final int COL_DRAGON_HOT = 0xFFF3B0;
    private static final int COL_TEANA = BattleMageRules.COLOR_TEANA;
    private static final int COL_TEANA_HOT = 0xFFF7CC;
    private static final int COL_GRAVITY = 0xA855FF;

    private static final class Meta {
        int color = BattleMageRules.COLOR_MARTIAL;
        int mode = 0;
        int count = 1;
        int balls = 8;
        int totalHits = 12;
        int hitIndex = 0;
        int chaserType = 0;
        int targetEntity = -1;
        double speed = 28.0;
        double radius = 3.5;
        double pullRadius = 0.0;
        double blastRadius = 5.0;
        double finisherRadius = 5.5;
        double range = 6.0;
        double width = 3.0;
        double arc = 210.0;
        double charge = 0.0;
        boolean teana;
        boolean finisher;
        boolean chaserWave;
        boolean twinFang;
        boolean waveFang;
        boolean arcWave;
        long dashMs = 280L;
        long swingMs = 420L;
        long finishMs = 820L;
        long lastHitAt = 0L;
        long updatedAt;
    }

    private static final Meta DEFAULT_META = new Meta();
    private static final Map<UUID, Meta> METAS = new LinkedHashMap<>();
    private static final Map<UUID, Integer> LAST_CHASER_COUNT = new HashMap<>();
    private static final Map<UUID, Long> CHASER_FLASH_AT = new HashMap<>();
    private static final Map<UUID, Integer> LAST_GROOVE_STAGE = new HashMap<>();
    private static String lastFullChargeCastId = "";

    private BattleMageVisuals() {}

    private static Identifier tex(String name) {
        return Identifier.fromNamespaceAndPath("yumis_dungeon_and_fight", "textures/effect/battle_mage/" + name);
    }

    static void clear() {
        METAS.clear();
        LAST_CHASER_COUNT.clear();
        CHASER_FLASH_AT.clear();
        LAST_GROOVE_STAGE.clear();
        lastFullChargeCastId = "";
    }

    static void install() {
        for (String id : BattleMageSkills.ALL) {
            boolean isTeana = BattleMageSkills.TEANA_TRANSFORMATION.equals(id);
            SkillVisualRegistry.register(id, new SkillVisualRegistry.Handler() {
                @Override public long defaultDuration() { return isTeana ? 2400L : 1400L; }

                @Override public long finishedUntil(SkillVfx.Track track, SkillVisualEvent event) {
                    return computeFinishedUntil(id, track.start.cast(), event);
                }

                @Override public int defaultColor() {
                    return switch (id) {
                        case BattleMageSkills.TEANA_TRANSFORMATION -> BattleMageRules.COLOR_TEANA;
                        case BattleMageSkills.DRAGON_SPEAR -> COL_DRAGON_GOLD;
                        case BattleMageSkills.DRAGON_STRIKE -> COL_METEOR;
                        case BattleMageSkills.CHASER_FUSION, BattleMageSkills.CHASER_PRESS -> BattleMageRules.COLOR_DARK;
                        default -> BattleMageRules.COLOR_MARTIAL;
                    };
                }

                @Override public boolean cinematic(SkillVisualEvent event) {
                    return isCinematic(id, event);
                }

                @Override public boolean cinematicFromFirstPerson() { return isTeana; }
                @Override public boolean restoreCamera() { return isTeana; }

                @Override public CameraType cameraType(SkillVfx.Track track, long time) {
                    return CameraType.THIRD_PERSON_BACK;
                }

                @Override public SkillVfx.CameraPose camera(SkillVfx.Track track, long time) {
                    if (!isTeana) return null;
                    Meta m = METAS.get(track.start.cast());
                    if ((m == null || !m.finisher) && !track.start.sweep()) return null;
                    return teanaFinisherCamera(track, time);
                }

                @Override public boolean motion(SkillVfx.Track track, JsonObject j) {
                    recordMotion(track.start.cast(), j, track.last.phase() == HIT ? track.last.at() : 0L);
                    return true;
                }

                @Override public void play(SkillVfx.Track track, SkillVisualEvent event) {
                    playCue(id, track, event);
                }

                @Override public void render(SkillVfx.LevelRenderContext ctx, SkillVfx.Track track, long age, long time, List<Quad> out, Vec3 camera) {
                    renderSkill(id, track, age, time, ctx.sprites(), camera);
                }
            });
        }

        // Register SPEAR & CLUB basic visual decorators so Battle Mage 3-hit step syncs via slashAngle
        BasicAttackVisuals.Style bmStepDecorator = (visual, slash, interval) -> {
            if (BattleMageClient.isBattleMage()) {
                visual.addProperty("slashAngle", (double) BattleMageClient.lastBasicStep());
            }
        };
        BasicAttackVisuals.register("SPEAR", false, false, bmStepDecorator);
        BasicAttackVisuals.register("CLUB", false, false, bmStepDecorator);
    }

    static void recordMotion(UUID cast, JsonObject j) {
        recordMotion(cast, j, 0L);
    }

    static void recordMotion(UUID cast, JsonObject j, long hitAt) {
        if (cast == null || j == null) return;
        Meta m = METAS.computeIfAbsent(cast, k -> new Meta());
        m.updatedAt = safeNow();
        if (j.has("color")) m.color = j.get("color").getAsInt();
        if (j.has("mode")) m.mode = j.get("mode").getAsInt();
        if (j.has("count")) m.count = Math.max(1, j.get("count").getAsInt());
        if (j.has("balls")) m.balls = Math.max(0, j.get("balls").getAsInt());
        if (j.has("totalHits")) m.totalHits = Math.max(1, j.get("totalHits").getAsInt());
        if (j.has("hitIndex")) m.hitIndex = j.get("hitIndex").getAsInt();
        if (j.has("ballIndex")) m.hitIndex = j.get("ballIndex").getAsInt();
        if (j.has("chaserType")) m.chaserType = Math.clamp(j.get("chaserType").getAsInt(), 0, 4);
        if (j.has("targetEntity")) m.targetEntity = j.get("targetEntity").getAsInt();
        if (j.has("speed")) m.speed = Math.max(4.0, j.get("speed").getAsDouble());
        if (j.has("radius")) m.radius = j.get("radius").getAsDouble();
        if (j.has("pullRadius")) m.pullRadius = j.get("pullRadius").getAsDouble();
        if (j.has("blastRadius")) m.blastRadius = j.get("blastRadius").getAsDouble();
        if (j.has("finisherRadius")) m.finisherRadius = j.get("finisherRadius").getAsDouble();
        if (j.has("range")) m.range = j.get("range").getAsDouble();
        if (j.has("width")) m.width = j.get("width").getAsDouble();
        if (j.has("arc")) m.arc = j.get("arc").getAsDouble();
        if (j.has("charge")) m.charge = Math.clamp(j.get("charge").getAsDouble(), 0.0, 1.0);
        if (j.has("teana")) m.teana = j.get("teana").getAsBoolean();
        if (j.has("finisher")) m.finisher = j.get("finisher").getAsBoolean();
        if (j.has("chaserWave")) m.chaserWave = j.get("chaserWave").getAsBoolean();
        if (j.has("twinFang")) m.twinFang = j.get("twinFang").getAsBoolean();
        if (j.has("waveFang")) m.waveFang = j.get("waveFang").getAsBoolean();
        if (j.has("arcWave")) m.arcWave = j.get("arcWave").getAsBoolean();
        if (j.has("dashMs")) m.dashMs = Math.max(100L, j.get("dashMs").getAsLong());
        if (j.has("swingMs")) m.swingMs = Math.max(200L, j.get("swingMs").getAsLong());
        if (j.has("finishMs")) m.finishMs = Math.max(400L, j.get("finishMs").getAsLong());
        if (hitAt > 0L) m.lastHitAt = hitAt;
        if (METAS.size() > 256) METAS.remove(METAS.keySet().iterator().next());
    }

    static boolean isCinematic(String id, SkillVisualEvent event) {
        if (!BattleMageSkills.TEANA_TRANSFORMATION.equals(id) || event == null) return false;
        Meta m = METAS.get(event.cast());
        return (m != null && m.finisher) || event.sweep();
    }

    static long computeFinishedUntil(String id, UUID castId, SkillVisualEvent event) {
        Meta m = castId == null ? DEFAULT_META : METAS.getOrDefault(castId, DEFAULT_META);
        long linger = switch (id) {
            case BattleMageSkills.CHASER_LAUNCHER -> 420L;
            case BattleMageSkills.CHASER_FUSION -> 600L;
            case BattleMageSkills.CHASER_PRESS -> 740L;
            case BattleMageSkills.PUNTO_WAVE -> 480L;
            case BattleMageSkills.DRAGON_STRIKE -> 620L;
            case BattleMageSkills.DRAGON_SPEAR -> 780L;
            case BattleMageSkills.TEANA_TRANSFORMATION -> Math.max(920L, m.finishMs);
            default -> 520L;
        };
        return event.at() + linger;
    }

    // --- Battle Mage Basic Attack Detection, Audio & Rendering (Niu 3-Hit & Teana Golden Lance) ---

    static boolean isBattleMageBasic(SkillVisualEvent event) {
        if (event == null || !"ATTACK".equals(event.skill())) return false;
        int c = event.color();
        return c == BattleMageRules.COLOR_TEANA || c == BattleMageRules.COLOR_MARTIAL;
    }

    static void playBasicAudio(SkillVisualEvent e) {
        if (e.phase() != START) return;
        boolean teana = e.color() == BattleMageRules.COLOR_TEANA;
        int step = Math.floorMod((int) Math.round(e.slashAngle()), 3);
        float pitch = (teana ? 1.26f : 1.08f) + step * 0.12f;
        SkillSound.playAt(e.position(), SoundEvents.PLAYER_ATTACK_SWEEP, teana ? 0.74f : 0.62f, pitch);
        if (step == 1 || teana) {
            SkillSound.playAt(e.position(), SoundEvents.SPEAR_ATTACK.value(), teana ? 0.62f : 0.48f, pitch + 0.12f);
        }
        if (teana && step == 2) {
            SkillSound.playAt(e.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 0.48f, 1.55f);
        }
        if (isLocalCaster(e.caster())) {
            CameraShake.local(teana ? 0.075f : 0.045f, 25L, 65L);
        }
    }

    static void renderBasic(List<SpriteQuad> sp, Vec3 camera, SkillVisualEvent e, long age, long baseDuration) {
        if (e.phase() != START) return;
        var sample = BuiltinSkillVisuals.basicSlashSample(age, baseDuration);
        if (sample == null) return;
        double progress = sample.progress();
        double aScale = sample.alphaScale();
        double rawU = Math.clamp(age / (double) Math.max(1L, baseDuration), 0.0, 0.999);

        boolean teana = e.color() == BattleMageRules.COLOR_TEANA;
        int step = Math.floorMod((int) Math.round(e.slashAngle()), 3);
        Vec3 dir = horizontalDir(vector(e.direction()));
        Vec3 base = vector(e.position());
        double reachScale = teana ? 1.38 : Math.max(1.0, e.horizontalScale());

        int mainCol = teana ? COL_TEANA : COL_SAKURA;
        int hotCol = teana ? COL_TEANA_HOT : COL_MARTIAL_HOT;
        int alpha = Math.clamp((int) Math.round((teana ? 248 : 235) * aScale), 0, 255);
        if (alpha <= 4) return;

        if (teana) {
            // Teana Golden Spear 3-hit combo: 0=Golden Pierce, 1=Wide Golden Sweep, 2=360 Rising Golden Spiral!
            if (step == 0) {
                int frame = Math.clamp((int) (rawU * 8), 0, 7);
                Vec3 from = base.add(0, 1.15, 0).add(dir.scale(0.35));
                emitAxisCrossed(sp, TEX_SPEAR_THRUST, 8, frame, from, dir, 4.4 * reachScale, 0.72, 2, COL_TEANA, COL_TEANA_HOT, alpha, 0.92);
                emitFramedBillboard(sp, TEX_CHASER_NOVA, 8, frame, from.add(dir.scale(3.6)), camera, 1.15, COL_TEANA, COL_TEANA_HOT, (int) (alpha * 0.85), 0.90);
            } else if (step == 1) {
                Vec3 center = base.add(dir.scale(1.15)).add(0, 1.18, 0);
                emitSlash(sp, center, dir, -10, 3.65 * reachScale, progress, false, COL_TEANA, COL_TEANA_HOT, alpha);
                emitSlash(sp, center.add(0, 0.14, 0), dir, 14, 3.30 * reachScale, Math.min(0.999, progress * 1.06), true, COL_DRAGON_GOLD, COL_TEANA_HOT, (int) (alpha * 0.78));
            } else {
                Vec3 center = base.add(dir.scale(0.65)).add(0, 1.22, 0);
                emitSlash(sp, center, dir, 18, 3.95 * reachScale, progress, false, COL_TEANA, COL_TEANA_HOT, alpha);
                emitSlash(sp, center.add(0, 0.22, 0), dir.scale(-1.0), -18, 3.75 * reachScale, progress, true, COL_TEANA, COL_WHITE, (int) (alpha * 0.85));
                EffectSprites.shockwave(sp, base.add(0, 0.10, 0), 3.6 * EffectSprites.easeOutCubic(rawU), rawU * 2.2, COL_TEANA_HOT, (int) (alpha * 0.80));
                int mf = (int) Math.floorMod(age / 75L, 4L);
                emitFramedBillboard(sp, TEX_FEATHERS_MIST, 4, mf, center.add(dir.scale(1.2)), camera, 1.65, COL_TEANA, COL_TEANA_HOT, (int) (alpha * 0.72), 0.75);
            }
        } else {
            // Niu's Combat Art 3-hit melee combo: 0=Diagonal Polearm Slash, 1=Rapid Spear Thrust, 2=Spinning Crescent!
            if (step == 0) {
                Vec3 center = base.add(dir.scale(0.95)).add(0, 1.12, 0);
                emitSlash(sp, center, dir, 24, 2.55 * reachScale, progress, false, mainCol, hotCol, alpha);
                emitSlash(sp, center.add(0, -0.05, 0), dir, 16, 2.25 * reachScale, Math.min(0.999, progress * 0.94), false, COL_MARTIAL, hotCol, (int) (alpha * 0.65));
            } else if (step == 1) {
                int frame = Math.clamp((int) (rawU * 8), 0, 7);
                Vec3 from = base.add(0, 1.10, 0).add(dir.scale(0.30));
                emitAxisCrossed(sp, TEX_SPEAR_THRUST, 8, frame, from, dir, 3.65 * reachScale, 0.56, 2, COL_MARTIAL, hotCol, alpha, 0.88);
                EffectSprites.glow(sp, from.add(dir.scale(3.1)), camera, 0.85 * aScale, hotCol, (int) (alpha * 0.85));
            } else {
                Vec3 center = base.add(dir.scale(0.65)).add(0, 1.18, 0);
                emitSlash(sp, center, dir, -14, 2.95 * reachScale, progress, true, mainCol, hotCol, alpha);
                emitSlash(sp, center.add(0, 0.16, 0), dir.scale(-1.0), 14, 2.65 * reachScale, progress, false, COL_MARTIAL, hotCol, (int) (alpha * 0.75));
                int pf = Math.clamp((int) (rawU * 8), 0, 7);
                emitFramedBillboard(sp, TEX_PALM_BLOSSOM, 8, pf, center.add(dir.scale(1.4)), camera, 1.25, COL_WHITE, COL_SAKURA_HOT, (int) (alpha * 0.70), 0.75);
            }
        }

        // Dynamic ribbon off animated weapon tip
        var mc = Minecraft.getInstance();
        Player caster = mc == null || mc.level == null ? null : mc.level.getPlayerByUUID(e.caster());
        if (caster != null && age < 220L) {
            var pose = Effects.VFX.playerPose(caster.getUUID(), e.at() + age);
            Vec3 tip = PlayerBoneAnchor.weaponTip(caster, pose == null ? null : pose.frame(), teana ? 1.65 : 1.35);
            RibbonTrail.push("bm_basic:" + e.cast(), tip, teana ? 0.24 : 0.17, teana ? COL_TEANA : mainCol, 220, 175L, e.at() + age);
        }
    }

    // --- Base Mage Linkage: 龙牙 (Dragon Fang, replacing old 10 green cubes & star!) ---

    static void renderDragonFang(SkillVfx.Track t, long age, long now, List<SpriteQuad> sp, Vec3 camera) {
        Meta m = METAS.getOrDefault(t.start.cast(), DEFAULT_META);
        Vec3 origin = vector(t.start.origin());
        Vec3 dir = horizontalDir(vector(t.last.direction()));
        double range = m.range > 0.5 ? m.range : 5.0;
        double width = m.width > 0.3 ? m.width : 1.5;
        boolean teana = m.teana;
        int col = teana ? COL_TEANA : COL_FANG_JADE;
        int hot = teana ? COL_TEANA_HOT : 0xEEFFF7;

        long dur = Math.max(460L, t.start.duration());
        double u = Math.clamp(age / (double) dur, 0.0, 0.999);
        double fade = 1.0 - u * u;
        if (fade <= 0.02) return;
        int frame = Math.clamp((int) (u * 8), 0, 7);
        int alpha = (int) Math.round(245 * fade);

        boolean isWave = m.mode == 2 || m.waveFang;
        boolean isTwin = m.mode == 1 || m.twinFang;

        if (isWave) {
            // Morph B / 炫纹牙刃: piercing crescent spearwave surging along range!
            double surgeU = Math.clamp(age / 360.0, 0.0, 1.0);
            Vec3 head = origin.add(dir.scale(range * EffectSprites.easeOutCubic(surgeU)));
            double len = Math.max(2.4, range * (0.45 + 0.55 * surgeU));
            emitAxisCrossed(sp, TEX_SPEAR_THRUST, 8, frame, origin, dir, len, width * 0.58, 2, col, hot, alpha, 0.90);
            emitSlash(sp, head, dir, 0, width * 1.15, Math.clamp(surgeU * 0.85, 0.0, 0.999), false, col, hot, alpha);
            emitFramedBillboard(sp, TEX_CHASER_NOVA, 8, frame, head, camera, width * 0.88, col, hot, (int) (alpha * 0.85), 0.90);
            RibbonTrail.push("fang_wave:" + t.start.cast(), head, 0.34, col, alpha, 210L, now);
        } else {
            // Standard Dragon Fang & Morph A (破甲双牙: crisp twin rapid cross-angled spear pierces!)
            emitAxisCrossed(sp, TEX_SPEAR_THRUST, 8, frame, origin, dir, range, width * 0.52, 2, col, hot, alpha, 0.88);
            Vec3 tip = origin.add(dir.scale(range * (0.55 + 0.45 * EffectSprites.easeOutCubic(Math.min(1.0, u * 2.2)))));
            emitFramedBillboard(sp, TEX_CHASER_NOVA, 8, frame, tip, camera, 1.10, col, hot, (int) (alpha * 0.88), 0.90);
            EffectSprites.glow(sp, tip, camera, 1.25 * fade, hot, (int) (215 * fade));
        }

        if (isTwin && m.lastHitAt > 0L) {
            long secondAge = Math.max(0L, now - m.lastHitAt);
            if (secondAge < 380L) {
                double u2 = Math.clamp(secondAge / 380.0, 0.0, 0.999);
                double fade2 = 1.0 - u2;
                int f2 = Math.clamp((int) (u2 * 8), 0, 7);
                Vec3 right = rightOf(dir);
                Vec3 origin2 = origin.add(right.scale(0.28)).add(0, 0.16, 0);
                int a2 = (int) Math.round(252 * fade2);
                emitAxisCrossed(sp, TEX_SPEAR_THRUST, 8, f2, origin2, dir, range * 1.06, width * 0.62, 2, teana ? COL_TEANA : COL_SAKURA, teana ? COL_TEANA_HOT : COL_SAKURA_HOT, a2, 0.95);
                emitFramedBillboard(sp, TEX_PALM_BLOSSOM, 8, f2, origin2.add(dir.scale(range * 0.88)), camera, 1.45, COL_WHITE, COL_SAKURA_HOT, a2, 0.85);
            }
        }
    }

    // --- Layered Audio, Afterimages & Camera Shake Choreography --------------

    private static void playCue(String id, SkillVfx.Track track, SkillVisualEvent e) {
        Meta m = METAS.getOrDefault(track.start.cast(), DEFAULT_META);
        Vec3 pos = vector(e.position());
        var mc = Minecraft.getInstance();
        Player actor = mc == null || mc.level == null ? null : mc.level.getPlayerByUUID(e.caster());
        boolean ownFp = isOwnFirstPerson(e.caster());

        switch (id) {
            case BattleMageSkills.CHASER_LAUNCHER -> {
                if (e.phase() == START) {
                    float pitch = 1.42f + (m.chaserType * 0.07f);
                    SkillSound.playAt(e.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 0.50f, pitch);
                    SkillSound.playAt(e.position(), SoundEvents.BREEZE_SHOOT, 0.32f, 1.62f);
                } else if (e.phase() == FINISH) {
                    SkillSound.playAt(e.position(), SoundEvents.FIREWORK_ROCKET_BLAST, m.teana ? 0.62f : 0.48f, m.teana ? 1.28f : 1.44f);
                    CameraShake.impulse(pos, 12, m.teana ? 0.16f : 0.10f, 40L, 95L);
                }
            }
            case BattleMageSkills.PALM_BLAST -> {
                if (e.phase() == START) {
                    SkillSound.playAt(e.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 0.78f, m.teana ? 1.28f : 1.14f);
                    SkillSound.playAt(e.position(), SoundEvents.BREEZE_SHOOT, 0.56f, 1.20f);
                    if (actor != null && !ownFp) PlayerAfterimages.capture(actor, m.teana ? COL_TEANA : COL_SAKURA, 195, 340L, true);
                } else if (e.phase() == RELEASE) {
                    SkillSound.playAt(e.position(), SoundEvents.GENERIC_EXPLODE.value(), m.teana ? 0.72f : 0.58f, m.teana ? 1.10f : 1.26f);
                    SkillSound.playAt(e.position(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, 0.68f, 1.05f);
                    CameraShake.impulse(pos, 16, m.teana ? 0.34f : 0.24f, 70L, 150L);
                }
            }
            case BattleMageSkills.FULL_SWING -> {
                if (e.phase() == START) {
                    SkillSound.playAt(e.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 0.86f, m.mode == 1 ? 0.76f : 0.90f);
                    if (m.mode == 1 && actor != null && !ownFp) PlayerAfterimages.capture(actor, m.teana ? COL_TEANA : COL_SAKURA, 210, 380L, true);
                } else if (e.phase() == RELEASE) {
                    SkillSound.playAt(e.position(), SoundEvents.PLAYER_ATTACK_CRIT, 0.80f, 1.02f);
                    if (m.mode == 1 || m.teana) {
                        SkillSound.playAt(e.position(), SoundEvents.MACE_SMASH_GROUND_HEAVY, 0.72f, 0.88f);
                    }
                    if (m.chaserWave) {
                        SkillSound.playAt(e.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 0.68f, 1.32f);
                    }
                    CameraShake.impulse(pos, 18, m.mode == 1 ? 0.42f : 0.26f, 80L, 170L);
                }
            }
            case BattleMageSkills.CHASER_FUSION -> {
                if (e.phase() == START) {
                    SkillSound.playAt(e.position(), SoundEvents.BEACON_POWER_SELECT, 0.68f, 1.28f);
                    SkillSound.playAt(e.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 0.58f, 1.40f);
                } else if (e.phase() == FINISH) {
                    SkillSound.playAt(e.position(), SoundEvents.GENERIC_EXPLODE.value(), 0.78f, 1.04f);
                    SkillSound.playAt(e.position(), SoundEvents.AMETHYST_CLUSTER_BREAK, 0.65f, 1.18f);
                    CameraShake.impulse(pos, 18, 0.34f, 85L, 185L);
                }
            }
            case BattleMageSkills.CHASER_PRESS -> {
                if (e.phase() == START) {
                    SkillSound.playAt(e.position(), SoundEvents.END_PORTAL_FRAME_FILL, 0.76f, 0.84f);
                    if (m.pullRadius > 0.5) {
                        SkillSound.playAt(e.position(), SoundEvents.BEACON_ACTIVATE, 0.66f, 0.62f);
                    }
                } else if (e.phase() == FINISH) {
                    SkillSound.playAt(e.position(), SoundEvents.GENERIC_EXPLODE.value(), 0.98f, 0.68f);
                    SkillSound.playAt(e.position(), SoundEvents.MACE_SMASH_GROUND_HEAVY, 0.90f, 0.72f);
                    CameraShake.impulse(pos, 24, Math.min(0.62f, 0.36f + 0.04f * m.count), 125L, 255L);
                }
            }
            case BattleMageSkills.PUNTO_WAVE -> {
                if (e.phase() == HIT) {
                    float p = 1.38f + (m.hitIndex % 5) * 0.08f;
                    SkillSound.playAt(e.position(), SoundEvents.SPEAR_ATTACK.value(), m.mode == 1 ? 0.72f : 0.44f, p);
                    if (m.mode == 1) {
                        SkillSound.playAt(e.position(), SoundEvents.PLAYER_ATTACK_CRIT, 0.52f, 1.15f);
                        CameraShake.impulse(pos, 14, 0.18f, 45L, 100L);
                    }
                } else if (e.phase() == FINISH) {
                    SkillSound.playAt(e.position(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, 0.85f, 0.92f);
                    SkillSound.playAt(e.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 0.82f, 0.84f);
                    CameraShake.impulse(pos, 18, 0.32f, 85L, 180L);
                }
            }
            case BattleMageSkills.DRAGON_STRIKE -> {
                if (e.phase() == START) {
                    SkillSound.playAt(e.position(), SoundEvents.BREEZE_SHOOT, 0.86f, 0.78f);
                    SkillSound.playAt(e.position(), SoundEvents.FIRECHARGE_USE, 0.78f, 0.92f);
                    if (actor != null && !ownFp) PlayerAfterimages.capture(actor, COL_METEOR, 215, 380L, true);
                } else if (e.phase() == FINISH) {
                    SkillSound.playAt(e.position(), SoundEvents.GENERIC_EXPLODE.value(), 0.95f, 0.76f);
                    SkillSound.playAt(e.position(), SoundEvents.MACE_SMASH_GROUND_HEAVY, 0.84f, 0.78f);
                    CameraShake.impulse(pos, 22, 0.48f, 115L, 235L);
                }
            }
            case BattleMageSkills.DRAGON_SPEAR -> {
                if (e.phase() == START) {
                    SkillSound.playAt(e.position(), SoundEvents.TRIDENT_THUNDER.value(), 0.76f, 1.06f);
                    SkillSound.playAt(e.position(), SoundEvents.ENDER_DRAGON_GROWL, 0.38f, 1.24f);
                    if (actor != null && !ownFp) PlayerAfterimages.capture(actor, COL_DRAGON_GOLD, 215, 420L, true);
                    CameraShake.impulse(pos, 20, 0.32f, 90L, 185L);
                } else if (e.phase() == HIT) {
                    SkillSound.playAt(e.position(), SoundEvents.FIREWORK_ROCKET_BLAST, 0.48f, 1.32f + (m.hitIndex % 4) * 0.08f);
                    CameraShake.impulse(pos, 16, 0.16f, 45L, 100L);
                } else if (e.phase() == FINISH) {
                    SkillSound.playAt(e.position(), SoundEvents.GENERIC_EXPLODE.value(), 1.0f, 0.64f);
                    SkillSound.playAt(e.position(), SoundEvents.TRIDENT_THUNDER.value(), 0.92f, 0.82f);
                    SkillSound.playAt(e.position(), SoundEvents.MACE_SMASH_GROUND_HEAVY, 0.88f, 0.68f);
                    CameraShake.impulse(pos, 28, 0.62f, 150L, 300L);
                }
            }
            case BattleMageSkills.TEANA_TRANSFORMATION -> {
                if (e.phase() == START) {
                    SkillSound.playAt(e.position(), SoundEvents.BEACON_ACTIVATE, 0.96f, m.finisher ? 0.96f : 1.18f);
                    SkillSound.playAt(e.position(), SoundEvents.BELL_RESONATE, 0.86f, m.finisher ? 0.88f : 1.12f);
                    if (m.finisher) {
                        SkillSound.playAt(e.position(), SoundEvents.TRIDENT_THUNDER.value(), 0.85f, 1.12f);
                        if (actor != null) PlayerAfterimages.capture(actor, COL_TEANA, 235, 520L, true);
                        CameraShake.impulse(pos, 26, 0.42f, 120L, 240L);
                    } else {
                        CameraShake.impulse(pos, 22, 0.38f, 110L, 220L);
                    }
                } else if (e.phase() == FINISH) {
                    SkillSound.playAt(e.position(), SoundEvents.GENERIC_EXPLODE.value(), 1.0f, 0.54f);
                    SkillSound.playAt(e.position(), SoundEvents.TRIDENT_THUNDER.value(), 1.0f, 0.72f);
                    SkillSound.playAt(e.position(), SoundEvents.MACE_SMASH_GROUND_HEAVY, 0.95f, 0.60f);
                    SkillSound.playAt(e.position(), SoundEvents.BELL_RESONATE, 0.88f, 0.76f);
                    CameraShake.impulse(pos, 34, 0.78f, 190L, 380L);
                }
            }
            default -> {}
        }
    }

    // --- Persistent Actor Visuals: 5-Color Chasers, Battle Groove 4-6, Teana Wings/Halo, Charge Telegraph ---

    static void actors(List<SpriteQuad> sp, Vec3 camera, long serverNow, long renderNow) {
        var mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) return;

        for (var actor : CareerClient.INSTANCE.view.actors) {
            long packed = actor.battleMageChasers;
            boolean teana = actor.teanaUntil > serverNow;
            int groove = Math.max(0, actor.grooveStage);
            if (packed == 0L && !teana && groove < 4) continue;

            UUID ownerId;
            try {
                ownerId = UUID.fromString(actor.owner);
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            Player player = mc.level.getPlayerByUUID(ownerId);
            if (player == null || !player.isAlive()) continue;

            int count = countPackedChasers(packed);
            Integer prevCount = LAST_CHASER_COUNT.put(ownerId, count);
            if (prevCount != null && count > prevCount) {
                CHASER_FLASH_AT.put(ownerId, renderNow);
                Vec3 pPos = PlayerVisualAnchor.position(player);
                WorldEffects.playShockwave(pPos.add(0, 1.3, 0), 0.25, 1.45, teana ? COL_TEANA : COL_MARTIAL, 190, 240L);
                SkillSound.playAt(pPos.x, pPos.y + 1.3, pPos.z, SoundEvents.AMETHYST_BLOCK_CHIME, 0.36f, 1.35f + count * 0.05f);
            }
            Integer prevGroove = LAST_GROOVE_STAGE.put(ownerId, groove);
            if (prevGroove != null && groove >= 6 && prevGroove < 6) {
                Vec3 pPos = PlayerVisualAnchor.position(player);
                WorldEffects.playShockwave(pPos.add(0, 0.14, 0), 0.35, 2.55, COL_SAKURA, 220, 320L);
                SkillSound.playAt(pPos.x, pPos.y + 1.2, pPos.z, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.52f, 1.42f);
            }

            Vec3 base = PlayerVisualAnchor.position(player);
            Vec3 fwd = horizontalDir(PlayerVisualAnchor.forward(player));
            boolean ownFp = isOwnFirstPerson(ownerId);
            long flashAge = renderNow - CHASER_FLASH_AT.getOrDefault(ownerId, 0L);
            double gainPop = (flashAge >= 0L && flashAge < 320L) ? (1.0 - flashAge / 320.0) : 0.0;
            renderActorVisuals(sp, camera, base, fwd, packed, groove, teana, ownFp, renderNow, gainPop);
        }

        // Charge dash direction telegraph for Palm Blast & Dragon Strike
        if (mc.player != null && BattleMageChargeClient.active()) {
            renderOwnCharge(mc.player, sp, camera, renderNow);
        } else {
            lastFullChargeCastId = "";
        }

        // Periodic cleanup of stale metadata
        if (METAS.size() > 160) {
            long cutoff = safeNow() - 25_000L;
            METAS.values().removeIf(m -> m.updatedAt < cutoff);
        }
    }

    /** Package-private headless-testable persistent actor renderer (0 Minecraft entity dependency). */
    static void renderActorVisuals(
        List<SpriteQuad> sp, Vec3 camera, Vec3 base, Vec3 forward,
        long packedChasers, int grooveStage, boolean teana, boolean ownFp, long renderNow, double gainPop
    ) {
        Vec3 fwd = horizontalDir(forward);
        Vec3 right = rightOf(fwd);

        // 1. Orbiting 5-color / Teana Rune-Ring Chaser Orbs in an inclined Battle Mage halo!
        int[] types = new int[10];
        int count = unpackChasers(packedChasers, types);
        if (count > 0) {
            Vec3 haloCenter = ownFp
                ? base.subtract(fwd.scale(0.62)).add(0, 1.55, 0)
                : base.subtract(fwd.scale(0.28)).add(0, 1.45, 0);
            double orbitR = ownFp ? 1.05 : (teana ? 1.02 : 0.88);
            double alphaScale = ownFp ? 0.44 : 1.0;

            for (int i = 0; i < count; i++) {
                double ang = renderNow * 0.0028 + i * (Math.TAU / Math.max(1, count));
                // Inclined orbital plane tilted cleanly behind shoulders like classic DNF Battle Mage
                Vec3 offset = right.scale(Math.cos(ang) * orbitR)
                    .add(fwd.scale(Math.sin(ang) * orbitR * 0.58))
                    .add(0, Math.sin(ang) * 0.34 + Math.sin(renderNow * 0.004 + i) * 0.06, 0);
                Vec3 pos = haloCenter.add(offset);
                int orbFrame = teana ? 5 : Math.clamp(types[i], 0, 4);
                int orbCol = BattleMageRules.chaserColor(types[i], teana);
                boolean isHead = (i == 0);
                double pulse = isHead ? 0.04 * Math.sin(renderNow * 0.014) : 0.0;
                double rad = (isHead ? 0.30 : 0.22) + (isHead ? 0.10 * gainPop : 0.0) + pulse;
                int a = (int) Math.round((isHead ? 250 : 225) * alphaScale);

                emitFramedBillboard(sp, TEX_CHASER_ORB, 6, orbFrame, pos, camera, rad, COL_WHITE, EffectSprites.brighten(orbCol, 0.35), a, isHead ? 0.82 : 0.62);
                EffectSprites.glow(sp, pos, camera, rad * (isHead ? 1.78 : 1.45), orbCol, (int) Math.round((isHead ? 180 : 120) * alphaScale));
            }
        }

        // 2. Battle Groove (斗神意志) Stage 4-6 Martial Aura Escalation!
        if (grooveStage >= 4) {
            boolean maxStage = grooveStage >= 6;
            double pulse = 0.5 + 0.5 * Math.sin(renderNow * (maxStage ? 0.011 : 0.0065));
            double fpMul = ownFp ? 0.38 : 1.0;
            int auraCol = teana ? COL_TEANA : (maxStage ? COL_SAKURA : COL_MARTIAL);
            int auraHot = teana ? COL_TEANA_HOT : (maxStage ? COL_SAKURA_HOT : COL_MARTIAL_HOT);

            double footR = (maxStage ? 1.48 : 1.18) + 0.10 * pulse;
            emitGroundSprite(sp, TEX_CHASER_SIGIL, base.add(0, 0.04, 0), footR, renderNow * (maxStage ? 0.0024 : 0.0014), auraCol, auraHot, (int) Math.round((maxStage ? 175 : 115) * fpMul), 0.72);

            if (!ownFp) {
                int mistCount = maxStage ? 3 : 2;
                for (int i = 0; i < mistCount; i++) {
                    double a = renderNow * 0.0026 + i * (Math.TAU / mistCount);
                    double rise = ((renderNow * 0.0009 + i * 0.34) % 1.0);
                    Vec3 mp = base.add(Math.cos(a) * 0.74, 0.28 + rise * 1.45, Math.sin(a) * 0.74);
                    int ma = (int) Math.round((maxStage ? 165 : 115) * Math.sin(rise * Math.PI));
                    int mf = (int) Math.floorMod((renderNow + i * 160L) / 95L, 4L);
                    emitFramedBillboard(sp, TEX_FEATHERS_MIST, 4, mf, mp, camera, maxStage ? 0.58 : 0.45, auraCol, auraHot, ma, 0.72);
                }
                if (maxStage) {
                    long sparkStep = renderNow / 85L;
                    SplittableRandom rng = new SplittableRandom(sparkStep * 0x9E3779B97F4A7C15L);
                    double a0 = rng.nextDouble() * Math.TAU;
                    double a1 = a0 + 1.6 + rng.nextDouble() * 1.4;
                    Vec3 p0 = base.add(Math.cos(a0) * 0.68, 0.35 + rng.nextDouble() * 1.15, Math.sin(a0) * 0.68);
                    Vec3 p1 = base.add(Math.cos(a1) * 0.68, 0.45 + rng.nextDouble() * 1.25, Math.sin(a1) * 0.68);
                    LightningBolt.bolt(sp, p0, p1, camera, sparkStep, 5, 0.20, 0.044, auraHot, 205, false);
                }
            }
        }

        // 3. Teana Battle Goddess (变身：贝亚娜) Radiant Seraphic Wings, Behind-Head Halo, Ground Crest & Feathers!
        if (teana) {
            double pulse = 0.5 + 0.5 * Math.sin(renderNow * 0.0075);
            double fpMul = ownFp ? 0.38 : 1.0;

            // Rotating Sacred Golden Battle-Crest beneath feet
            emitGroundSprite(sp, TEX_TEANA_HALO, base.add(0, 0.055, 0), 1.62 + 0.10 * pulse, renderNow * 0.0018, COL_WHITE, COL_TEANA_HOT, (int) Math.round(215 * fpMul), 0.85);
            emitGroundSprite(sp, TEX_CHASER_SIGIL, base.add(0, 0.072, 0), 1.05, -renderNow * 0.0032, COL_TEANA, COL_TEANA_HOT, (int) Math.round(155 * fpMul), 0.80);

            // Full seraphic wings, behind-head sacred halo & drifting golden feathers (hidden in own 1st-person to protect view!)
            if (!ownFp) {
                int wingFrame = (int) Math.floorMod(renderNow / 115L, 4L);
                double flap = Math.sin(renderNow * 0.0068) * 0.12;
                Vec3 backAnchor = base.add(0, 1.38, 0).subtract(fwd.scale(0.32));

                for (int side : new int[]{-1, 1}) {
                    Vec3 shoulder = backAnchor.add(right.scale(side * 0.14));
                    Vec3 outAxis = right.scale(side * (0.86 - flap * 0.35)).subtract(fwd.scale(0.46 + flap * 0.35)).add(0, 0.14 + flap * 0.25, 0).normalize();
                    Vec3 upAxis = UP.add(fwd.scale(-0.08)).normalize();
                    emitWing(sp, shoulder, outAxis, upAxis, 1.48, 2.05, wingFrame, 240);
                }

                // Behind-head/back radiant Sacred Winged Sun Halo
                Vec3 haloPos = base.add(0, 1.68, 0).subtract(fwd.scale(0.42));
                double haloSpin = renderNow * 0.0018;
                double haloR = 0.82 + 0.06 * pulse;
                double cs = Math.cos(haloSpin) * haloR, sn = Math.sin(haloSpin) * haloR;
                Vec3 ru = right.scale(cs).add(UP.scale(sn));
                Vec3 rv = UP.scale(cs).subtract(right.scale(sn));
                EffectSprites.emitPlane(sp, TEX_TEANA_HALO, Blend.TRANSLUCENT, haloPos, ru, rv, 0f, 0f, 1f, 1f, COL_WHITE, 205);
                EffectSprites.emitPlane(sp, TEX_TEANA_HALO, Blend.ADDITIVE, haloPos.subtract(fwd.scale(0.02)), ru.scale(1.03), rv.scale(1.03), 0f, 0f, 1f, 1f, COL_TEANA_HOT, 165);

                // Gently drifting luminous golden feathers orbiting the Battle Goddess
                for (int i = 0; i < 3; i++) {
                    double a = renderNow * 0.0019 + i * (Math.TAU / 3.0);
                    double fall = 1.0 - ((renderNow * 0.00055 + i * 0.33) % 1.0);
                    Vec3 fp = base.add(Math.cos(a) * 1.12, 0.25 + fall * 1.95, Math.sin(a) * 1.12);
                    int fa = (int) Math.round(185 * Math.sin(fall * Math.PI));
                    int ff = (int) Math.floorMod((renderNow + i * 140L) / 110L, 4L);
                    emitFramedBillboard(sp, TEX_FEATHERS_MIST, 4, ff, fp, camera, 0.56, COL_TEANA, COL_TEANA_HOT, fa, 0.85);
                }
            }
        }
    }

    private static void renderOwnCharge(Player player, List<SpriteQuad> sp, Vec3 camera, long renderNow) {
        long now = ClientUi.now();
        var cast = BattleMageChargeClient.cast();
        if (cast == null) return;
        double progress = BattleMageChargeClient.chargeProgress(now);
        boolean full = BattleMageChargeClient.isFull(now);
        boolean isDragonStrike = BattleMageSkills.DRAGON_STRIKE.equals(cast.skill.id);
        double dist = BattleMageChargeClient.previewDashDistance(now);
        if (dist <= 0.3) return;

        Vec3 base = PlayerVisualAnchor.position(player);
        Vec3 look = horizontalDir(player.getLookAngle());
        boolean ownFp = Minecraft.getInstance().options.getCameraType().isFirstPerson();
        int col = full ? COL_TEANA : (isDragonStrike ? COL_METEOR : COL_SAKURA);
        int hot = full ? COL_TEANA_HOT : (isDragonStrike ? COL_METEOR_HOT : COL_SAKURA_HOT);

        if (full && !cast.id.equals(lastFullChargeCastId)) {
            lastFullChargeCastId = cast.id;
            SkillSound.playAt(base.x, base.y + 1.0, base.z, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.72f, 1.55f);
            WorldEffects.playShockwave(base.add(0, 0.12, 0), 0.35, 2.55, COL_TEANA, 235, 300L);
        }

        // (a) Rotating martial charge seal beneath feet + contracting focus ring
        double footR = 1.25 + 0.65 * progress;
        double fpMul = ownFp ? 0.48 : 1.0;
        emitGroundSprite(sp, TEX_CHASER_SIGIL, base.add(0, 0.05, 0), footR, renderNow * (full ? 0.0044 : 0.0024), col, hot, (int) Math.round((155 + 85 * progress) * fpMul), 0.85);
        double contract = 1.0 - ((renderNow % 320L) / 320.0);
        EffectSprites.shockwave(sp, base.add(0, 0.09, 0), footR * (0.25 + 0.95 * contract), -renderNow * 0.0045, hot, (int) Math.round(185 * contract * fpMul));

        // (b) Crisp textured forward dash lane chevron & target impact circle (zero rectangular blocks!)
        Vec3 from = base.add(0, 0.12, 0).add(look.scale(0.5));
        Vec3 to = base.add(look.scale(dist)).add(0, 0.08, 0);
        int cf = (int) Math.floorMod(renderNow / 65L, 8L);
        double laneW = (isDragonStrike ? 0.95 : 0.68) * (full ? 1.25 : 1.0);
        Vec3 right = rightOf(look);
        Vec3 laneCenter = from.lerp(to, 0.5);
        double laneLen = Math.max(0.5, from.distanceTo(to));
        float u0 = cf / 8.0f, u1 = (cf + 1) / 8.0f;
        int laneAlpha = full ? 215 : 145;
        EffectSprites.emitPlane(sp, TEX_SPEAR_THRUST, Blend.TRANSLUCENT, laneCenter, right.scale(laneW), look.scale(-laneLen * 0.5), u0, 0f, u1, 1f, col, (int) (laneAlpha * 0.52));
        EffectSprites.emitPlane(sp, TEX_SPEAR_THRUST, Blend.ADDITIVE, laneCenter.add(0, 0.018, 0), right.scale(laneW * 0.92), look.scale(-laneLen * 0.5), u0, 0f, u1, 1f, hot, laneAlpha);

        double targetR = (isDragonStrike ? 1.65 : 1.15) * (0.72 + 0.42 * progress);
        emitGroundSprite(sp, full ? TEX_TEANA_HALO : TEX_CHASER_SIGIL, to, targetR, renderNow * 0.0042, full ? COL_WHITE : col, hot, full ? 235 : 175, 0.88);
        if (full) {
            EffectSprites.glow(sp, to.add(0, 0.35, 0), camera, targetR * 0.72, COL_TEANA_HOT, 185);
        }
    }

    // --- Main Skill Render Router (Package-Private for Headless Verification) ---

    static void renderSkill(String id, SkillVfx.Track t, long age, long time, List<SpriteQuad> sp, Vec3 camera) {
        Meta m = METAS.getOrDefault(t.start.cast(), DEFAULT_META);
        int color = m.color != 0 ? m.color : t.start.color();
        Vec3 origin = vector(t.start.position());
        Vec3 point = vector(t.last.position());
        Vec3 dir = vector(t.last.direction());
        if (dir.lengthSqr() < 1e-4) dir = vector(t.start.direction());
        if (dir.lengthSqr() < 1e-4) dir = new Vec3(0, 0, 1);
        else dir = dir.normalize();
        Vec3 flatDir = horizontalDir(dir);
        boolean finished = t.last.phase() == FINISH;
        long after = finished ? Math.max(0L, time - t.last.at()) : 0L;

        switch (id) {
            case BattleMageSkills.CHASER_LAUNCHER -> renderChaserLauncher(t, m, origin, point, dir, age, time, finished, after, color, sp, camera);
            case BattleMageSkills.PALM_BLAST -> renderPalmBlast(t, m, origin, point, flatDir, age, time, color, sp, camera);
            case BattleMageSkills.FULL_SWING -> renderFullSwing(t, m, origin, point, flatDir, age, time, finished, after, color, sp, camera);
            case BattleMageSkills.CHASER_FUSION -> renderChaserFusion(t, m, point, age, time, finished, after, color, sp, camera);
            case BattleMageSkills.CHASER_PRESS -> renderChaserPress(t, m, origin, point, flatDir, age, time, finished, after, color, sp, camera);
            case BattleMageSkills.PUNTO_WAVE -> renderPuntoWave(t, m, point, flatDir, age, time, finished, after, color, sp, camera);
            case BattleMageSkills.DRAGON_STRIKE -> renderDragonStrike(t, m, origin, point, flatDir, age, time, finished, after, color, sp, camera);
            case BattleMageSkills.DRAGON_SPEAR -> renderDragonSpear(t, m, origin, point, flatDir, age, time, finished, after, sp, camera);
            case BattleMageSkills.TEANA_TRANSFORMATION -> renderTeana(t, m, origin, point, flatDir, age, time, finished, after, sp, camera);
            default -> {}
        }
    }

    // --- 1. 炫纹发射 (Chaser Launcher: Real In-Flight Homing Orb + Element Star-Nova!) ---

    private static void renderChaserLauncher(
        SkillVfx.Track t, Meta m, Vec3 origin, Vec3 point, Vec3 dir,
        long age, long time, boolean finished, long after, int color, List<SpriteQuad> sp, Vec3 camera
    ) {
        int orbFrame = m.teana ? 5 : Math.clamp(m.chaserType, 0, 4);
        int hot = m.teana ? COL_TEANA_HOT : EffectSprites.brighten(color, 0.55);

        if (!finished) {
            // Live in-flight position extrapolation (tracks locked target entity if present!)
            Vec3 flyPos = extrapolateChaserPos(origin, dir, m, age);
            double rad = m.teana ? 0.56 : 0.44;
            emitFramedBillboard(sp, TEX_CHASER_ORB, 6, orbFrame, flyPos, camera, rad, COL_WHITE, hot, 252, 0.85);
            EffectSprites.glow(sp, flyPos, camera, rad * 1.95, color, 225);
            RibbonTrail.push("chaser_outer:" + t.start.cast(), flyPos, m.teana ? 0.32 : 0.24, color, 235, 220L, time);
            RibbonTrail.push("chaser_core:" + t.start.cast(), flyPos, m.teana ? 0.16 : 0.11, hot, 230, 150L, time);
            return;
        }

        // Impact detonation: crisp star-nova + ground arcane sigil pulse + shockwave!
        double u = Math.clamp(after / 420.0, 0.0, 0.999);
        double fade = 1.0 - u;
        if (fade <= 0.01) return;
        int frame = Math.clamp((int) (u * 8), 0, 7);
        double r = Math.max(1.6, m.radius);

        emitGroundSprite(sp, m.teana ? TEX_TEANA_HALO : TEX_CHASER_SIGIL, point.add(0, 0.06, 0), r * (0.45 + 0.65 * EffectSprites.easeOutCubic(u)), time * 0.006, m.teana ? COL_WHITE : color, hot, (int) (215 * fade), 0.82);
        emitGroundFrame(sp, TEX_CHASER_NOVA, 8, frame, point.add(0, 0.12, 0), r * 1.08, u * 1.1, color, hot, (int) (240 * fade), 0.90);
        emitFramedBillboard(sp, TEX_CHASER_NOVA, 8, frame, point.add(0, 0.35, 0), camera, r * 0.96, color, hot, (int) (250 * fade), 0.92);
        EffectSprites.shockwave(sp, point.add(0, 0.10, 0), r * 1.18 * EffectSprites.easeOutCubic(u), u * 1.6, hot, (int) (235 * fade));
        EffectSprites.glow(sp, point.add(0, 0.35, 0), camera, r * 0.82 * fade, hot, (int) (215 * fade));
        if (m.teana && u < 0.55) {
            LightningBolt.bolt(sp, point.add(-r * 0.5, 0.2, 0), point.add(r * 0.5, 1.3, 0), camera, t.start.seed() ^ (time / 45L), 5, 0.26, 0.048, COL_TEANA_HOT, (int) (220 * fade), false);
        }
    }

    private static Vec3 extrapolateChaserPos(Vec3 origin, Vec3 dir, Meta m, long age) {
        double dist = Math.min(Math.max(2.0, m.range), m.speed * Math.max(0L, age) / 1000.0);
        var mc = Minecraft.getInstance();
        if (m.targetEntity >= 0 && mc != null && mc.level != null) {
            Entity target = mc.level.getEntity(m.targetEntity);
            if (target != null && target.isAlive()) {
                Vec3 goal = target.getBoundingBox().getCenter();
                Vec3 to = goal.subtract(origin);
                double len = to.length();
                if (len > 1e-4) {
                    return origin.add(to.scale(Math.min(1.0, dist / len)));
                }
            }
        }
        return origin.add(dir.scale(dist));
    }

    // --- 2. 落花掌 / 苍天击 (Palm Blast / Teana Sky Piercer: Sakura Petals & Golden Thrust!) ---

    private static void renderPalmBlast(
        SkillVfx.Track t, Meta m, Vec3 origin, Vec3 point, Vec3 dir,
        long age, long time, int color, List<SpriteQuad> sp, Vec3 camera
    ) {
        boolean teana = m.teana;
        int col = teana ? COL_TEANA : COL_SAKURA;
        int hot = teana ? COL_TEANA_HOT : COL_SAKURA_HOT;

        if (!t.released) {
            double u = Math.clamp(age / (double) Math.max(120L, m.dashMs), 0.0, 1.0);
            double dist = Math.max(1.4, m.range * u);
            Vec3 head = origin.add(dir.scale(dist));
            int frame = Math.clamp((int) (u * 7), 0, 7);

            emitAxisCrossed(sp, TEX_SPEAR_THRUST, 8, frame, origin, dir, dist + 0.8, teana ? 0.95 : 0.72, 2, col, hot, 240, 0.90);
            if (teana) {
                emitAxisCrossed(sp, TEX_APOSTLE_SPEAR, 4, (int) Math.floorMod(time / 70L, 4L), origin.add(dir.scale(Math.max(0, dist - 2.6))), dir, 2.9, 0.65, 2, COL_WHITE, COL_TEANA_HOT, 235, 0.85);
            }
            EffectSprites.glow(sp, head, camera, teana ? 1.25 : 0.95, hot, 230);
            RibbonTrail.push("palm_dash:" + t.start.cast(), head, teana ? 0.42 : 0.30, col, 225, 220L, time);
            return;
        }

        long relAge = Math.max(0L, time - t.last.at());
        double u = Math.clamp(relAge / 460.0, 0.0, 0.999);
        double fade = 1.0 - u;
        if (fade <= 0.01) return;
        int frame = Math.clamp((int) (u * 8), 0, 7);
        double r = Math.max(2.0, m.radius);

        // Residual dash thrust fading cleanly into impact
        emitAxisCrossed(sp, TEX_SPEAR_THRUST, 8, frame, origin, dir, Math.max(1.5, origin.distanceTo(point) + 0.6), (teana ? 0.88 : 0.64) * fade, 2, col, hot, (int) (195 * fade), 0.85);

        // Iconic DNF 落花掌 blooming cherry-blossom burst (or 苍天击 golden sky-piercer!)
        Vec3 impactCenter = point.add(dir.scale(0.65)).add(0, 0.15, 0);
        if (teana) {
            emitGroundSprite(sp, TEX_TEANA_HALO, point.add(0, 0.06, 0), r * 1.05, u * 1.4, COL_WHITE, COL_TEANA_HOT, (int) (230 * fade), 0.88);
            Vec3 skyAxis = dir.scale(0.84).add(0, 0.54, 0).normalize();
            emitAxisCrossed(sp, TEX_SPEAR_THRUST, 8, frame, point.subtract(0, 0.15, 0), skyAxis, r * 1.65, r * 0.42, 2, COL_TEANA, COL_TEANA_HOT, (int) (248 * fade), 0.94);
            emitFramedBillboard(sp, TEX_CHASER_NOVA, 8, frame, impactCenter.add(0, 0.45, 0), camera, r * 1.05, COL_TEANA, COL_TEANA_HOT, (int) (245 * fade), 0.92);
            emitFramedBillboard(sp, TEX_FEATHERS_MIST, 4, (int) Math.floorMod(relAge / 90L, 4L), impactCenter.add(0, 0.55, 0), camera, r * 0.92, COL_TEANA, COL_TEANA_HOT, (int) (210 * fade), 0.80);
        } else {
            emitGroundSprite(sp, TEX_MARTIAL_CRATER, point.add(0, 0.05, 0), r * 0.82, t.start.seed() * 0.03, COL_SAKURA, COL_SAKURA_HOT, (int) (185 * fade), 0.65);
            // Primary sakura blossom burst (pre-baked pink/crimson, so baseRgb = WHITE!)
            emitFramedBillboard(sp, TEX_PALM_BLOSSOM, 8, frame, impactCenter.add(0, 0.35, 0), camera, r * 1.18, COL_WHITE, COL_SAKURA_HOT, (int) (252 * fade), 0.86);
            emitGroundFrame(sp, TEX_PALM_BLOSSOM, 8, frame, point.add(0, 0.10, 0), r * 1.12, u * 0.8, COL_WHITE, COL_SAKURA_HOT, (int) (235 * fade), 0.80);
            if (m.mode == 1) {
                // Morph A (闪步穿心掌): crisp forward heart-piercing shock cone!
                emitAxisCrossed(sp, TEX_SPEAR_THRUST, 8, frame, point, dir, r * 1.35, r * 0.36, 2, COL_SAKURA, COL_WHITE, (int) (245 * fade), 0.95);
            }
        }
        EffectSprites.shockwave(sp, point.add(0, 0.09, 0), r * 1.15 * EffectSprites.easeOutCubic(u), u * 1.8, hot, (int) (235 * fade));
        EffectSprites.glow(sp, impactCenter.add(0, 0.3, 0), camera, r * 0.85 * fade, hot, (int) (220 * fade));
    }

    // --- 3. 碎霸 / 天地碎霸 (Full Swing: Sweeping Crescent, 360° Ultimate Empress & Chaser Slash!) ---

    private static void renderFullSwing(
        SkillVfx.Track t, Meta m, Vec3 origin, Vec3 point, Vec3 dir,
        long age, long time, boolean finished, long after, int color, List<SpriteQuad> sp, Vec3 camera
    ) {
        // 万象魔刃 traveling crescent magic wave
        if (m.arcWave) {
            int waveCol = color != 0 ? color : COL_MARTIAL;
            int waveHot = EffectSprites.brighten(waveCol, 0.55);
            double w = Math.max(2.4, m.width);
            if (!finished) {
                double dist = Math.min(Math.max(4.0, m.range), m.speed * Math.max(0L, age) / 1000.0);
                Vec3 cur = origin.add(dir.scale(dist));
                int frame = (int) Math.floorMod(time / 55L, 8L);
                emitSlash(sp, cur, dir, 0, w * 0.95, 0.42, false, waveCol, waveHot, 245);
                emitSlash(sp, cur.add(0, 0.16, 0), dir, 8, w * 0.82, 0.55, true, waveHot, COL_WHITE, 210);
                emitFramedBillboard(sp, TEX_CHASER_NOVA, 8, frame, cur, camera, w * 0.55, waveCol, waveHot, 225, 0.90);
                EffectSprites.glow(sp, cur, camera, w * 0.65, waveHot, 195);
                RibbonTrail.push("arcanum_wave:" + t.start.cast(), cur, 0.40, waveCol, 220, 210L, time);
                return;
            }
            double u = Math.clamp(after / 320.0, 0.0, 0.999);
            double fade = 1.0 - u;
            if (fade <= 0.01) return;
            int frame = Math.clamp((int) (u * 8), 0, 7);
            emitFramedBillboard(sp, TEX_CHASER_NOVA, 8, frame, point, camera, w * 0.78, waveCol, waveHot, (int) (230 * fade), 0.90);
            EffectSprites.shockwave(sp, point.add(0, -0.7, 0), w * 0.95 * EffectSprites.easeOutCubic(u), u * 1.5, waveHot, (int) (210 * fade));
            return;
        }

        double swingDur = Math.max(460.0, m.swingMs + 140.0);
        double u = Math.clamp(age / swingDur, 0.0, 0.999);
        double fade = 1.0 - u * u;
        if (fade <= 0.01) return;
        int alpha = (int) Math.round(250 * fade);
        double r = Math.max(2.5, m.radius);
        boolean teana = m.teana;

        int mainCol = teana ? COL_TEANA : COL_SAKURA;
        int hotCol = teana ? COL_TEANA_HOT : COL_MARTIAL_HOT;

        if (m.mode == 1) {
            // Morph A / 闪击碎霸·终极女皇: colossal 360° leaping slam + double rings + shattered crater!
            int frame = Math.clamp((int) (u * 8), 0, 7);
            emitSlash(sp, point.add(0, 0.25, 0), dir, -6, r * 1.08, u, false, mainCol, hotCol, alpha);
            emitSlash(sp, point.add(0, 0.42, 0), dir.scale(-1.0), 8, r * 1.08, u, true, teana ? COL_DRAGON_GOLD : COL_MARTIAL, hotCol, (int) (alpha * 0.92));
            emitGroundSprite(sp, TEX_MARTIAL_CRATER, point.add(0, -0.84, 0), r * 0.94, t.start.seed() * 0.02, mainCol, hotCol, (int) (235 * fade), 0.78);
            emitGroundSprite(sp, teana ? TEX_TEANA_HALO : TEX_CHASER_SIGIL, point.add(0, -0.82, 0), r * 0.88, u * 1.6, teana ? COL_WHITE : mainCol, hotCol, (int) (205 * fade), 0.82);
            emitVerticalCrossed(sp, TEX_DRAGON_PILLAR, 8, frame, point.add(0, -0.85, 0), r * 0.34, 4.2, u * 1.4, 3, mainCol, hotCol, (int) (215 * fade), 0.86);
            EffectSprites.shockwave(sp, point.add(0, -0.80, 0), r * 1.15 * EffectSprites.easeOutCubic(u), u * 2.1, hotCol, (int) (242 * fade));
            if (!m.chaserWave) return;
        } else {
            // Standard 210° Full Swing / Teana 360° Heaven-Earth Full Swing
            emitSlash(sp, point.add(0, 0.12, 0), dir, -4, r * 1.08, u, false, mainCol, hotCol, alpha);
            emitSlash(sp, point.add(0, 0.28, 0), dir, 12, r * 0.96, Math.min(0.999, u * 1.08), false, teana ? COL_DRAGON_GOLD : COL_MARTIAL, hotCol, (int) (alpha * 0.80));
            if (teana || m.arc >= 300.0) {
                emitSlash(sp, point.add(0, 0.22, 0), dir.scale(-1.0), -10, r * 1.06, u, true, COL_TEANA, COL_TEANA_HOT, (int) (alpha * 0.92));
                emitGroundSprite(sp, TEX_TEANA_HALO, point.add(0, -0.84, 0), r * 0.86, u * 1.8, COL_WHITE, COL_TEANA_HOT, (int) (205 * fade), 0.84);
            }
        }

        // Morph B (炫纹碎霸·魔刃附魔): additional luminous Chaser-colored magic crescent wave!
        if (m.mode == 2 || m.chaserWave) {
            int waveCol = color;
            int waveHot = EffectSprites.brighten(waveCol, 0.55);
            emitSlash(sp, point.add(dir.scale(0.45)).add(0, 0.36, 0), dir, 0, r * 1.16, u, false, waveCol, waveHot, alpha);
            int frame = Math.clamp((int) (u * 8), 0, 7);
            emitFramedBillboard(sp, TEX_CHASER_NOVA, 8, frame, point.add(dir.scale(r * 0.55)), camera, r * 0.62, waveCol, waveHot, (int) (220 * fade), 0.90);
            if (u < 0.65) {
                Vec3 right = rightOf(dir);
                LightningBolt.bolt(sp, point.add(right.scale(-r * 0.6)), point.add(right.scale(r * 0.6)).add(dir.scale(r * 0.5)), camera, t.start.seed() ^ (time / 50L), 6, 0.30, 0.052, waveHot, (int) (215 * fade), false);
            }
        }

        EffectSprites.shockwave(sp, point.add(0, -0.82, 0), r * 1.05 * EffectSprites.easeOutCubic(u), u * 1.5, hotCol, (int) (215 * fade));
    }

    // --- 4. 炫纹融合 (Chaser Fusion: Twin Orbiting Convergence -> Multi-Hue Arcane Star-Nova!) ---

    private static void renderChaserFusion(
        SkillVfx.Track t, Meta m, Vec3 point,
        long age, long time, boolean finished, long after, int color, List<SpriteQuad> sp, Vec3 camera
    ) {
        int col = m.teana ? COL_TEANA : color;
        int hot = m.teana ? COL_TEANA_HOT : EffectSprites.brighten(col, 0.55);
        double r = Math.max(2.6, m.radius);

        if (!finished) {
            double u = Math.clamp(age / (m.mode == 1 ? 120.0 : 260.0), 0.0, 1.0);
            double collapse = 1.0 - 0.72 * u;
            emitGroundSprite(sp, TEX_CHASER_SIGIL, point.add(0, -0.82, 0), r * (0.55 + 0.45 * collapse), time * 0.008, col, hot, 225, 0.85);

            // Twin Chaser orbs spiraling violently into one fusion singularity!
            double orbitR = Math.max(0.18, r * 0.42 * collapse);
            Vec3 orbA = point.add(Math.cos(time * 0.022) * orbitR, 0.15, Math.sin(time * 0.022) * orbitR);
            Vec3 orbB = point.add(-Math.cos(time * 0.022) * orbitR, -0.15, -Math.sin(time * 0.022) * orbitR);
            int frameA = m.teana ? 5 : chaserFrameFromColor(col);
            int frameB = m.teana ? 5 : ((frameA + 2) % 5);
            double orbRad = 0.38 + 0.34 * u;
            emitFramedBillboard(sp, TEX_CHASER_ORB, 6, frameA, orbA, camera, orbRad, COL_WHITE, hot, 248, 0.88);
            emitFramedBillboard(sp, TEX_CHASER_ORB, 6, frameB, orbB, camera, orbRad, COL_WHITE, hot, 248, 0.88);
            LightningBolt.bolt(sp, orbA, orbB, camera, time / 45L, 5, 0.24, 0.050, hot, 235, false);
            EffectSprites.glow(sp, point, camera, 1.0 + 0.9 * u, col, 230);
            return;
        }

        double u = Math.clamp(after / 580.0, 0.0, 0.999);
        double fade = 1.0 - u;
        if (fade <= 0.01) return;
        int frame = Math.clamp((int) (u * 8), 0, 7);

        emitGroundSprite(sp, TEX_MARTIAL_CRATER, point.add(0, -0.85, 0), r * 0.86, t.start.seed() * 0.02, col, hot, (int) (220 * fade), 0.72);
        emitGroundSprite(sp, TEX_CHASER_SIGIL, point.add(0, -0.82, 0), r * (0.65 + 0.45 * EffectSprites.easeOutCubic(u)), -u * 2.0, col, hot, (int) (235 * fade), 0.90);
        emitGroundFrame(sp, TEX_CHASER_NOVA, 8, frame, point.add(0, -0.78, 0), r * 1.12, u * 1.2, col, hot, (int) (245 * fade), 0.92);
        emitFramedBillboard(sp, TEX_CHASER_NOVA, 8, frame, point.add(0, 0.25, 0), camera, r * 1.02, col, hot, (int) (252 * fade), 0.95);
        EffectSprites.shockwave(sp, point.add(0, -0.78, 0), r * 1.22 * EffectSprites.easeOutCubic(u), u * 1.9, hot, (int) (240 * fade));
        EffectSprites.glow(sp, point, camera, r * 0.88 * fade, hot, (int) (225 * fade));
    }

    // --- 5. 炫纹强压 (Chaser Press: Compressed Mega-Chaser Slam, Gravity Collapse & Nuclear Pillar!) ---

    private static void renderChaserPress(
        SkillVfx.Track t, Meta m, Vec3 origin, Vec3 point, Vec3 dir,
        long age, long time, boolean finished, long after, int color, List<SpriteQuad> sp, Vec3 camera
    ) {
        int col = m.teana ? COL_TEANA : color;
        int hot = m.teana ? COL_TEANA_HOT : EffectSprites.brighten(col, 0.55);
        double r = Math.max(3.2, m.radius);

        if (!finished) {
            double u = Math.clamp(age / (m.mode == 1 ? 180.0 : 310.0), 0.0, 1.0);
            Vec3 ground = point.add(0, -0.14, 0);

            // Morph B (引力坍缩强压): massive outer gravity-collapse suction vortex!
            if (m.pullRadius > 0.5) {
                double pullR = m.pullRadius;
                emitGroundSprite(sp, TEX_CHASER_SIGIL, ground, pullR * (1.0 - 0.18 * u), -time * 0.006, COL_GRAVITY, 0xE8C8FF, 215, 0.88);
                double shrink = 1.0 - ((time % 260L) / 260.0);
                EffectSprites.shockwave(sp, ground.add(0, 0.04, 0), pullR * (0.25 + 0.80 * shrink), -time * 0.008, 0xE8C8FF, (int) (210 * shrink));
                for (int k = 0; k < 4; k++) {
                    double a = time * 0.006 + k * (Math.TAU / 4.0);
                    Vec3 rim = ground.add(Math.cos(a) * pullR * 0.88, 0.55, Math.sin(a) * pullR * 0.88);
                    LightningBolt.bolt(sp, rim, point.add(0, 0.7, 0), camera, (time / 55L) ^ k, 5, 0.34, 0.048, 0xE8C8FF, 200, false);
                }
            }

            // Ground target press sigil expanding smoothly to exact blast radius
            emitGroundSprite(sp, m.teana ? TEX_TEANA_HALO : TEX_CHASER_SIGIL, ground.add(0, 0.02, 0), r * (0.38 + 0.62 * u), time * 0.008, m.teana ? COL_WHITE : col, hot, 232, 0.88);

            // Giant compressed mega-Chaser plunging from overhead (or arcing forward in Morph A!)
            Vec3 corePos = m.mode == 1
                ? origin.lerp(point.add(0, 0.35, 0), u).add(0, Math.sin(u * Math.PI) * 1.45, 0)
                : point.add(0, 1.85 * (1.0 - u * u) + 0.25, 0);
            double coreRad = 0.62 + 0.10 * Math.min(7, m.count) + 0.25 * u;
            int mainFrame = m.teana ? 5 : chaserFrameFromColor(col);
            emitFramedBillboard(sp, TEX_CHASER_ORB, 6, mainFrame, corePos, camera, coreRad, COL_WHITE, hot, 254, 0.92);
            EffectSprites.glow(sp, corePos, camera, coreRad * 2.1, col, 235);

            // Consumed Chasers orbiting rapidly into the compressed star-core!
            int sat = Math.clamp(m.count, 2, 7);
            for (int i = 0; i < sat; i++) {
                double a = time * 0.018 + i * (Math.TAU / sat);
                double sr = (1.65 - 1.20 * u);
                Vec3 spPos = corePos.add(Math.cos(a) * sr, Math.sin(a * 2.0) * 0.32, Math.sin(a) * sr);
                int sf = m.teana ? 5 : (i % 5);
                emitFramedBillboard(sp, TEX_CHASER_ORB, 6, sf, spPos, camera, 0.28, COL_WHITE, hot, 230, 0.78);
            }
            return;
        }

        // Massive Compressed Chaser Nuclear Detonation!
        double u = Math.clamp(after / 720.0, 0.0, 0.999);
        double fade = 1.0 - u;
        if (fade <= 0.01) return;
        int frame = Math.clamp((int) (u * 8), 0, 7);
        Vec3 ground = point.add(0, -0.14, 0);

        emitGroundSprite(sp, TEX_MARTIAL_CRATER, ground, r * 0.98, t.start.seed() * 0.02, col, hot, (int) (245 * fade), 0.80);
        emitGroundSprite(sp, m.teana ? TEX_TEANA_HALO : TEX_CHASER_SIGIL, ground.add(0, 0.03, 0), r * 1.06, u * 1.4, m.teana ? COL_WHITE : col, hot, (int) (230 * fade), 0.88);
        emitGroundFrame(sp, TEX_CHASER_NOVA, 8, frame, ground.add(0, 0.07, 0), r * 1.15, u * 1.1, col, hot, (int) (250 * fade), 0.94);
        emitFramedBillboard(sp, TEX_CHASER_NOVA, 8, frame, point.add(0, 0.75, 0), camera, r * 1.05, col, hot, (int) (252 * fade), 0.95);
        // Skyward Chaser Press pressure pillar (3 crossed axial planes, v=0 UP!)
        double pillarH = (4.6 + 0.45 * Math.min(7, m.count)) * (0.45 + 0.55 * EffectSprites.easeOutCubic(Math.min(1.0, u * 2.2)));
        emitVerticalCrossed(sp, TEX_DRAGON_PILLAR, 8, frame, ground, r * 0.36, pillarH, u * 1.8, 3, col, hot, (int) (242 * fade), 0.92);
        EffectSprites.shockwave(sp, ground.add(0, 0.08, 0), r * 1.25 * EffectSprites.easeOutCubic(u), u * 2.0, hot, (int) (248 * fade));
        if (u < 0.65) {
            for (int k = 0; k < 3; k++) {
                double a = t.start.seed() + k * (Math.TAU / 3.0) + u * 0.9;
                Vec3 pEnd = ground.add(Math.cos(a) * r * 0.85, 0.35 + (k % 2) * 0.6, Math.sin(a) * r * 0.85);
                LightningBolt.bolt(sp, point.add(0, 0.4, 0), pEnd, camera, (time / 50L) ^ k, 6, 0.32, 0.055, hot, (int) (235 * fade), false);
            }
        }
    }

    // --- 6. 流星闪影击 / 啦啦啦 (Punto Wave: Hypersonic Multi-Spear Barrage + Sweeping Finisher!) ---

    private static void renderPuntoWave(
        SkillVfx.Track t, Meta m, Vec3 point, Vec3 dir,
        long age, long time, boolean finished, long after, int color, List<SpriteQuad> sp, Vec3 camera
    ) {
        boolean teana = m.teana || color == COL_TEANA;
        int col = teana ? COL_TEANA : COL_SAKURA;
        int secCol = teana ? COL_DRAGON_GOLD : COL_MARTIAL;
        int hot = teana ? COL_TEANA_HOT : COL_MARTIAL_HOT;

        if (!finished) {
            Vec3 right = rightOf(dir);
            Vec3 base = point.subtract(dir.scale(m.range * 0.5));
            double range = Math.max(3.5, m.range);
            long hitPulseAge = m.lastHitAt > 0L ? Math.max(0L, time - m.lastHitAt) : (time % 110L);

            if (m.mode == 1) {
                // Morph A (天穹三刀流): 3 colossal sweeping heavy pierce lances!
                double u = Math.clamp(hitPulseAge / 220.0, 0.0, 0.999);
                double fade = 1.0 - u * 0.75;
                int frame = Math.clamp((int) (u * 8), 0, 7);
                int idx = Math.max(0, m.hitIndex);
                double roll = ((idx % 3) - 1) * 28.0;
                Vec3 stabFrom = base.add(right.scale(((idx % 3) - 1) * 0.35));
                emitAxisCrossed(sp, TEX_SPEAR_THRUST, 8, frame, stabFrom, dir, range * 1.12, Math.max(1.05, m.width * 0.36), 2, col, hot, (int) (252 * fade), 0.95);
                emitSlash(sp, base.add(dir.scale(range * 0.55)), dir, roll, m.width * 0.85, u, idx % 2 == 1, secCol, hot, (int) (235 * fade));
                emitFramedBillboard(sp, TEX_CHASER_NOVA, 8, frame, stabFrom.add(dir.scale(range * 0.95)), camera, 1.55, col, hot, (int) (240 * fade), 0.92);
                return;
            }

            // Standard 12-hit / Morph B 20-hit rapid hypersonic spear phantom flurry!
            int ghosts = m.mode == 2 ? 5 : 4;
            for (int g = 0; g < ghosts; g++) {
                int idx = m.hitIndex + g;
                long phaseMs = Math.floorMod(time + g * 38L, 160L);
                double u = phaseMs / 160.0;
                int frame = Math.clamp((int) (u * 8), 0, 7);
                double lateral = (((idx * 7 + g * 3) % 7) - 3) * (m.width * 0.13);
                double vert = (((idx * 5 + g * 2) % 5) - 2) * 0.24;
                double reach = range * (0.86 + 0.18 * ((idx + g) % 3) / 2.0);
                Vec3 stabFrom = base.add(right.scale(lateral)).add(0, vert, 0);
                int c = (g % 2 == 0) ? col : secCol;
                int a = (int) Math.round(238 * (1.0 - 0.45 * u));
                emitAxisCrossed(sp, TEX_SPEAR_THRUST, 8, frame, stabFrom, dir, reach, 0.54, 2, c, hot, a, 0.90);
                if (g < 2) {
                    Vec3 tip = stabFrom.add(dir.scale(reach * 0.94));
                    EffectSprites.glow(sp, tip, camera, 0.78, hot, a);
                }
            }
            // Swirling Mach wind cone envelope around the lance barrage
            EffectSprites.shockwave(sp, base.add(dir.scale(range * 0.55)).add(0, -0.78, 0), m.width * 0.72, time * 0.012, hot, 145);
            return;
        }

        // Punto Wave Grand Sweeping Finisher!
        double u = Math.clamp(after / 460.0, 0.0, 0.999);
        double fade = 1.0 - u;
        if (fade <= 0.01) return;
        int frame = Math.clamp((int) (u * 8), 0, 7);
        double r = Math.max(3.5, m.finisherRadius);

        emitSlash(sp, point.add(0, 0.12, 0), dir, -8, r * 1.06, u, false, col, hot, (int) (252 * fade));
        emitSlash(sp, point.add(0, 0.30, 0), dir, 14, r * 0.95, Math.min(0.999, u * 1.08), true, secCol, hot, (int) (225 * fade));
        emitFramedBillboard(sp, teana ? TEX_CHASER_NOVA : TEX_PALM_BLOSSOM, 8, frame, point.add(dir.scale(1.2)).add(0, 0.25, 0), camera, r * 0.78, teana ? COL_TEANA : COL_WHITE, hot, (int) (240 * fade), 0.88);
        EffectSprites.shockwave(sp, point.add(0, -0.80, 0), r * 1.12 * EffectSprites.easeOutCubic(u), u * 1.9, hot, (int) (240 * fade));
    }

    // --- 7. 强袭流星打 (Dragon Strike: Blazing Comet-Spear Drill Dash -> Meteor Impact!) ---

    private static void renderDragonStrike(
        SkillVfx.Track t, Meta m, Vec3 origin, Vec3 point, Vec3 dir,
        long age, long time, boolean finished, long after, int color, List<SpriteQuad> sp, Vec3 camera
    ) {
        boolean teana = m.teana;
        int col = teana ? COL_TEANA : COL_METEOR;
        int hot = teana ? COL_TEANA_HOT : COL_METEOR_HOT;

        if (!finished) {
            double u = Math.clamp(age / (double) Math.max(140L, m.dashMs), 0.0, 1.0);
            double dist = Math.max(1.8, m.range * u);
            Vec3 head = origin.add(dir.scale(dist));
            int frame = (int) Math.floorMod(time / 48L, 8L);

            // Colossal 3-plane Volumetric Blazing Comet-Spear Drill tearing along dash trajectory!
            double drillLen = Math.min(dist + 1.4, 5.8 + 1.2 * m.charge);
            Vec3 drillTail = head.subtract(dir.scale(drillLen));
            double drillHalfW = Math.max(1.15, m.width * 0.44) * (1.0 + 0.16 * m.charge);
            emitAxisCrossed(sp, TEX_METEOR_DRILL, 8, frame, drillTail, dir, drillLen + 0.65, drillHalfW, 3, teana ? COL_TEANA : COL_WHITE, hot, 250, 0.88);

            // Spiral lightning & fiery wake ribbons behind meteor nose
            LightningBolt.helixArcs(sp, origin, head, camera, time, 0.78, 2, hot, 225);
            EffectSprites.glow(sp, head, camera, 1.65 + 0.35 * m.charge, hot, 240);
            RibbonTrail.push("dstrike_outer:" + t.start.cast(), head, 0.56, col, 238, 260L, time);
            RibbonTrail.push("dstrike_core:" + t.start.cast(), head, 0.24, hot, 235, 180L, time);

            // Trailing Mach shock rings along wake
            double trailR = 1.45 + 0.35 * m.charge;
            emitGroundSprite(sp, TEX_CHASER_SIGIL, head.add(0, -0.88, 0), trailR, time * 0.010, col, hot, 175, 0.80);
            return;
        }

        // Endpoint Meteor Impact Crater + Explosive Nova + Flame Pillar!
        double u = Math.clamp(after / 600.0, 0.0, 0.999);
        double fade = 1.0 - u;
        if (fade <= 0.01) return;
        int frame = Math.clamp((int) (u * 8), 0, 7);
        double r = Math.max(3.5, m.blastRadius);
        Vec3 ground = point.add(0, -0.88, 0);

        emitGroundSprite(sp, TEX_MARTIAL_CRATER, ground, r * 0.98, t.start.seed() * 0.02, col, hot, (int) (248 * fade), 0.82);
        emitGroundFrame(sp, TEX_CHASER_NOVA, 8, frame, ground.add(0, 0.06, 0), r * 1.15, u * 1.2, col, hot, (int) (248 * fade), 0.92);
        emitFramedBillboard(sp, TEX_CHASER_NOVA, 8, frame, point.add(0, 0.35, 0), camera, r * 1.02, col, hot, (int) (252 * fade), 0.94);
        emitFramedBillboard(sp, TEX_PALM_BLOSSOM, 8, frame, point.add(0, 0.45, 0), camera, r * 0.85, teana ? COL_TEANA : COL_WHITE, hot, (int) (220 * fade), 0.80);
        emitVerticalCrossed(sp, TEX_DRAGON_PILLAR, 8, frame, ground, r * 0.34, 5.4, u * 1.6, 3, col, hot, (int) (238 * fade), 0.90);
        EffectSprites.shockwave(sp, ground.add(0, 0.08, 0), r * 1.24 * EffectSprites.easeOutCubic(u), u * 1.9, hot, (int) (245 * fade));
        EffectSprites.glow(sp, point.add(0, 0.2, 0), camera, r * 0.90 * fade, hot, (int) (230 * fade));
    }

    // --- 8. 煌龙偃月 (Dragon Spear: Golden Eastern Dragon Crescent Halberd + 5-Color Dragon Chasers + Roar!) ---

    private static void renderDragonSpear(
        SkillVfx.Track t, Meta m, Vec3 origin, Vec3 point, Vec3 dir,
        long age, long time, boolean finished, long after, List<SpriteQuad> sp, Vec3 camera
    ) {
        double range = Math.max(5.5, m.range);
        Vec3 tipPos = origin.add(dir.scale(Math.min(4.8, range * 0.65)));
        Vec3 right = rightOf(dir);

        if (!finished) {
            int frame = (int) Math.floorMod(time / 62L, 8L);

            if (m.mode == 1) {
                // Morph A (煌龙天临·纯物龙劈): colossal leaping overhead Golden Dragon Halberd vertical smash!
                double u = Math.clamp(age / 420.0, 0.0, 1.0);
                double pitchRad = Math.toRadians(lerp(72.0, -6.0, EffectSprites.easeOutCubic(u)));
                Vec3 swingAxis = dir.scale(Math.cos(pitchRad)).add(0, Math.sin(pitchRad), 0).normalize();
                emitAxisCrossed(sp, TEX_DRAGON_HALBERD, 8, frame, origin.add(0, 0.35, 0), swingAxis, range * 1.05, 1.85, 2, COL_WHITE, COL_DRAGON_HOT, 252, 0.88);
                int sf = Math.clamp((int) (u * 8), 0, 7);
                emitSlash(sp, origin.add(dir.scale(range * 0.45)).add(0, 1.2, 0), dir, 86, range * 0.78, Math.min(0.999, u), false, COL_DRAGON_GOLD, COL_DRAGON_HOT, 245);
                emitGroundSprite(sp, TEX_TEANA_HALO, tipPos.add(0, -0.88, 0), m.blastRadius * (0.45 + 0.55 * u), time * 0.008, COL_WHITE, COL_DRAGON_HOT, 215, 0.85);
                emitFramedBillboard(sp, TEX_CHASER_NOVA, 8, sf, origin.add(swingAxis.scale(range * 0.85)), camera, 1.9, COL_DRAGON_GOLD, COL_DRAGON_HOT, 235, 0.90);
                return;
            }

            // Standard & Morph B (脱手龙阵): monumental Golden Eastern Dragon Crescent Halberd impaling forward!
            double thrustGrow = EffectSprites.easeOutCubic(Math.clamp(age / 180.0, 0.25, 1.0));
            double halberdLen = range * thrustGrow;
            Vec3 endPos = origin.add(dir.scale(halberdLen));

            // 2 intersecting axial planes of our roaring Golden Dragon Crescent Halberd (v=0 at forward spear tip!)
            emitAxisCrossed(sp, TEX_DRAGON_HALBERD, 8, frame, origin, dir, halberdLen, 1.95, 2, COL_WHITE, COL_DRAGON_HOT, 252, 0.82);
            // Crackling golden dragon lightning wrapping the halberd shaft
            LightningBolt.helixArcs(sp, origin, endPos, camera, time, 0.95, 2, COL_DRAGON_HOT, 235);

            // Grand golden arcane dragon-hold seal anchoring impaled enemies at tipPos
            emitGroundSprite(sp, TEX_TEANA_HALO, tipPos.add(0, -0.88, 0), 3.4, time * 0.0045, COL_WHITE, COL_DRAGON_HOT, 225, 0.86);
            emitGroundSprite(sp, TEX_CHASER_SIGIL, tipPos.add(0, -0.85, 0), 4.2, -time * 0.0065, COL_DRAGON_GOLD, COL_DRAGON_HOT, 195, 0.82);

            // 5-Color Rainbow Dragon Chasers spiraling aggressively down the halberd blade into tipPos!
            int orbStreams = m.mode == 2 ? 7 : 5;
            for (int s = 0; s < orbStreams; s++) {
                double frac = ((time * 0.0024 + s / (double) orbStreams) % 1.0);
                double ang = time * 0.013 + s * (Math.TAU / orbStreams);
                double orbitR = lerp(1.15, 0.35, frac);
                Vec3 orbPos = origin.add(dir.scale(halberdLen * (0.12 + 0.78 * frac)))
                    .add(right.scale(Math.cos(ang) * orbitR))
                    .add(0, Math.sin(ang) * orbitR * 0.75, 0);
                int cType = s % 5;
                int orbCol = BattleMageRules.chaserColor(cType, false);
                emitFramedBillboard(sp, TEX_CHASER_ORB, 6, cType, orbPos, camera, 0.44, COL_WHITE, EffectSprites.brighten(orbCol, 0.45), 248, 0.85);
                EffectSprites.glow(sp, orbPos, camera, 0.88, orbCol, 220);
            }

            // Per-ball detonation pulse burst at tipPos whenever a Dragon Chaser fires (HIT phase)!
            long sinceBall = m.lastHitAt > 0L ? Math.max(0L, time - m.lastHitAt) : Long.MAX_VALUE;
            if (sinceBall < 260L) {
                double bu = sinceBall / 260.0;
                int bf = Math.clamp((int) (bu * 8), 0, 7);
                int bCol = BattleMageRules.chaserColor(m.hitIndex % 5, m.hitIndex % 3 == 0);
                int bHot = EffectSprites.brighten(bCol, 0.55);
                emitFramedBillboard(sp, TEX_CHASER_NOVA, 8, bf, tipPos.add(0, 0.2, 0), camera, 3.4, bCol, bHot, (int) (245 * (1.0 - bu)), 0.92);
                EffectSprites.shockwave(sp, tipPos.add(0, -0.80, 0), 3.8 * EffectSprites.easeOutCubic(bu), bu * 1.8, bHot, (int) (220 * (1.0 - bu)));
            }
            return;
        }

        // Final Golden Dragon Roar Eruption (煌龙终爆)!
        double u = Math.clamp(after / 760.0, 0.0, 0.999);
        double fade = 1.0 - u;
        if (fade <= 0.01) return;
        int frame = Math.clamp((int) (u * 8), 0, 7);
        double r = Math.max(4.5, m.blastRadius);
        Vec3 ground = point.add(0, -0.92, 0);

        emitGroundSprite(sp, TEX_MARTIAL_CRATER, ground, r * 1.02, t.start.seed() * 0.02, COL_DRAGON_GOLD, COL_DRAGON_HOT, (int) (248 * fade), 0.84);
        emitGroundSprite(sp, TEX_TEANA_HALO, ground.add(0, 0.03, 0), r * 1.08, u * 1.6, COL_WHITE, COL_DRAGON_HOT, (int) (238 * fade), 0.90);
        emitGroundFrame(sp, TEX_CHASER_NOVA, 8, frame, ground.add(0, 0.08, 0), r * 1.18, u * 1.2, COL_DRAGON_GOLD, COL_DRAGON_HOT, (int) (252 * fade), 0.94);
        emitFramedBillboard(sp, TEX_CHASER_NOVA, 8, frame, point.add(0, 0.65, 0), camera, r * 1.05, COL_DRAGON_GOLD, COL_DRAGON_HOT, (int) (252 * fade), 0.95);
        // Towering Golden Dragon Roar Eruption Pillar (v=0 UP!)
        double pillarH = 8.4 * (0.40 + 0.60 * EffectSprites.easeOutCubic(Math.min(1.0, u * 2.1)));
        emitVerticalCrossed(sp, TEX_DRAGON_PILLAR, 8, frame, ground, r * 0.42, pillarH, u * 2.0, 3, COL_DRAGON_GOLD, COL_DRAGON_HOT, (int) (250 * fade), 0.94);
        // Residual fading dragon halberd soaring skyward through the pillar!
        if (u < 0.72) {
            emitVerticalCrossed(sp, TEX_DRAGON_HALBERD, 8, frame, ground.add(0, 0.5 + u * 3.2, 0), 2.1, 6.2, u * 2.6, 2, COL_WHITE, COL_DRAGON_HOT, (int) (220 * (1.0 - u / 0.72)), 0.88);
        }
        EffectSprites.shockwave(sp, ground.add(0, 0.10, 0), r * 1.28 * EffectSprites.easeOutCubic(u), u * 2.2, COL_DRAGON_HOT, (int) (248 * fade));
        if (u < 0.65) {
            LightningBolt.helixArcs(sp, ground, ground.add(0, pillarH * 0.85, 0), camera, time, r * 0.45, 2, COL_DRAGON_HOT, (int) (235 * fade));
        }
    }

    // --- 9. 变身：贝亚娜 & 二段终结「使徒穿刺」 (Teana Transformation & Apostle Spear Finisher!) ---

    private static void renderTeana(
        SkillVfx.Track t, Meta m, Vec3 origin, Vec3 point, Vec3 dir,
        long age, long time, boolean finished, long after, List<SpriteQuad> sp, Vec3 camera
    ) {
        if (m.finisher) {
            renderTeanaFinisher(t, m, origin, point, dir, age, time, finished, after, sp, camera);
            return;
        }

        // 1st Press: 变身：贝亚娜 (Radiant Battle Goddess Ascension Burst — Golden Wings & Feather Fall!)
        double u = Math.clamp(age / 1050.0, 0.0, 0.999);
        double fade = 1.0 - u;
        if (fade <= 0.01) return;
        int frame = Math.clamp((int) (u * 8), 0, 7);
        int wingFrame = (int) Math.floorMod(age / 95L, 4L);
        double r = Math.max(4.5, m.radius);
        Vec3 ground = origin.add(0, -0.94, 0);
        Vec3 right = rightOf(dir);

        // Sacred Golden Ground Crest + Expanding Sunburst Shockwaves
        emitGroundSprite(sp, TEX_TEANA_HALO, ground, r * (0.45 + 0.65 * EffectSprites.easeOutCubic(u)), time * 0.005, COL_WHITE, COL_TEANA_HOT, (int) (245 * fade), 0.90);
        emitGroundSprite(sp, TEX_CHASER_SIGIL, ground.add(0, 0.03, 0), r * 0.78, -time * 0.008, COL_TEANA, COL_TEANA_HOT, (int) (215 * fade), 0.85);
        EffectSprites.shockwave(sp, ground.add(0, 0.08, 0), r * 1.25 * EffectSprites.easeOutCubic(u), u * 2.0, COL_TEANA_HOT, (int) (245 * fade));

        // Towering Sacred Ascension Beam (v=0 UP!)
        double pillarH = 8.6 * (0.35 + 0.65 * EffectSprites.easeOutCubic(Math.min(1.0, u * 2.2)));
        emitVerticalCrossed(sp, TEX_DRAGON_PILLAR, 8, frame, ground, r * 0.32, pillarH, u * 1.8, 3, COL_TEANA, COL_TEANA_HOT, (int) (240 * fade), 0.92);

        // Monumental Unfurling Golden Seraphic Light Wings flashing open behind the Battle Goddess!
        if (u < 0.85) {
            double wingPop = EffectSprites.easeOutCubic(Math.min(1.0, u * 3.0));
            double wingFade = 1.0 - smoothstep(0.45, 0.85, u);
            for (int side : new int[]{-1, 1}) {
                Vec3 sh = origin.add(0, 0.45, 0).subtract(dir.scale(0.35)).add(right.scale(side * 0.25));
                Vec3 outAxis = right.scale(side * 0.92).subtract(dir.scale(0.34)).add(0, 0.18, 0).normalize();
                emitWing(sp, sh, outAxis, UP, 3.2 * wingPop, 4.2 * wingPop, wingFrame, (int) (235 * wingFade));
            }
        }

        // Full-area swirling sacred feather-fall burst around the Battle Goddess!
        for (int i = 0; i < 6; i++) {
            double a = i * (Math.TAU / 6.0) + u * 1.4;
            double dist = r * (0.32 + 0.62 * EffectSprites.easeOutCubic(u));
            Vec3 fp = origin.add(Math.cos(a) * dist, 1.8 * (1.0 - u) + 0.4 * Math.sin(a * 2.0), Math.sin(a) * dist);
            int ff = (int) Math.floorMod((time + i * 95L) / 90L, 4L);
            emitFramedBillboard(sp, TEX_FEATHERS_MIST, 4, ff, fp, camera, 1.35, COL_TEANA, COL_TEANA_HOT, (int) (220 * fade), 0.86);
        }
    }

    private static void renderTeanaFinisher(
        SkillVfx.Track t, Meta m, Vec3 origin, Vec3 point, Vec3 dir,
        long age, long time, boolean finished, long after, List<SpriteQuad> sp, Vec3 camera
    ) {
        double range = Math.max(7.5, m.range);
        double r = Math.max(6.5, m.radius);
        Vec3 right = rightOf(dir);

        if (!finished) {
            // Pre-impact Apostle Spear manifestation & hyper-charge dive!
            int lanceFrame = (int) Math.floorMod(time / 65L, 4L);
            double u = Math.clamp(age / 780.0, 0.0, 1.0);
            Vec3 end = origin.add(dir.scale(range));

            // Colossal Radiant Apostle Divine Spear manifesting forward along thrust axis (v=0 at tip!)
            double spearLen = range * (0.45 + 0.55 * EffectSprites.easeOutCubic(u));
            emitAxisCrossed(sp, TEX_APOSTLE_SPEAR, 4, lanceFrame, origin.subtract(dir.scale(1.2)), dir, spearLen + 1.2, 1.65, 3, COL_WHITE, COL_TEANA_HOT, 252, 0.92);
            LightningBolt.helixArcs(sp, origin, origin.add(dir.scale(spearLen)), camera, time, 1.15, 3, COL_TEANA_HOT, 240);

            // Grand Sacred Halo behind the Battle Goddess + target ground judgment seal!
            emitGroundSprite(sp, TEX_TEANA_HALO, origin.add(0, -0.96, 0), 3.6, time * 0.005, COL_WHITE, COL_TEANA_HOT, 238, 0.90);
            Vec3 targetGround = origin.add(dir.scale(Math.min(7.5, range * 0.75))).add(0, -0.96, 0);
            emitGroundSprite(sp, TEX_TEANA_HALO, targetGround, r * (0.35 + 0.65 * u), -time * 0.007, COL_WHITE, COL_TEANA_HOT, 225, 0.88);
            EffectSprites.glow(sp, end, camera, 2.2, COL_TEANA_HOT, 225);
            return;
        }

        // Earth-Shattering Apostle Spear Supernova Impact (使徒穿刺终爆)!
        long finDur = Math.max(880L, m.finishMs);
        double u = Math.clamp(after / (double) finDur, 0.0, 0.999);
        double fade = 1.0 - u;
        if (fade <= 0.01) return;
        int frame = Math.clamp((int) (u * 8), 0, 7);
        int lanceFrame = (int) Math.floorMod(time / 65L, 4L);
        Vec3 ground = point.add(0, -1.04, 0);

        // Shattered Sacred Crater + Colossal Teana Battlefield Crest
        emitGroundSprite(sp, TEX_MARTIAL_CRATER, ground, r * 1.04, t.start.seed() * 0.02, COL_TEANA, COL_TEANA_HOT, (int) (252 * fade), 0.85);
        emitGroundSprite(sp, TEX_TEANA_HALO, ground.add(0, 0.03, 0), r * 1.12, u * 1.5, COL_WHITE, COL_TEANA_HOT, (int) (248 * fade), 0.92);
        emitGroundSprite(sp, TEX_CHASER_SIGIL, ground.add(0, 0.06, 0), r * 0.72, -u * 2.2, COL_DRAGON_GOLD, COL_TEANA_HOT, (int) (220 * fade), 0.88);

        // Twin Supernova Star-Burst & Expanding Golden Shockwaves
        emitGroundFrame(sp, TEX_CHASER_NOVA, 8, frame, ground.add(0, 0.10, 0), r * 1.24, u * 1.1, COL_TEANA, COL_TEANA_HOT, (int) (254 * fade), 0.96);
        emitFramedBillboard(sp, TEX_CHASER_NOVA, 8, frame, point.add(0, 0.95, 0), camera, r * 1.12, COL_TEANA, COL_TEANA_HOT, (int) (254 * fade), 0.96);
        EffectSprites.shockwave(sp, ground.add(0, 0.12, 0), r * 1.32 * EffectSprites.easeOutCubic(u), u * 2.2, COL_TEANA_HOT, (int) (250 * fade));
        EffectSprites.shockwave(sp, ground.add(0, 0.16, 0), r * 0.95 * EffectSprites.easeOutCubic(Math.min(1.0, u * 1.45)), -u * 2.6, COL_WHITE, (int) (235 * fade));

        // Colossal 10.5-block Sky-Piercing Golden Pillar + Giant Impaled Apostle Spear Monolith!
        double pillarH = 10.8 * (0.42 + 0.58 * EffectSprites.easeOutCubic(Math.min(1.0, u * 2.0)));
        emitVerticalCrossed(sp, TEX_DRAGON_PILLAR, 8, frame, ground, r * 0.42, pillarH, u * 2.2, 3, COL_TEANA, COL_TEANA_HOT, (int) (252 * fade), 0.96);
        // Giant Apostle Divine Spear plunged straight down into the crater center (v=0 tip at bottom -> axis = (0, -1, 0)!)
        emitAxisCrossed(sp, TEX_APOSTLE_SPEAR, 4, lanceFrame, ground.add(0, 7.6, 0), new Vec3(0, -1, 0), 7.6, 1.95, 3, COL_WHITE, COL_TEANA_HOT, (int) (248 * fade), 0.92);

        // Swirling storm of radiant golden feathers & sacred lightning!
        for (int i = 0; i < 6; i++) {
            double a = i * (Math.TAU / 6.0) + u * 2.0;
            double dist = r * (0.28 + 0.68 * EffectSprites.easeOutCubic(u));
            Vec3 fp = point.add(Math.cos(a) * dist, 0.5 + 2.8 * u + Math.sin(a * 2.0) * 0.6, Math.sin(a) * dist);
            int ff = (int) Math.floorMod((time + i * 85L) / 85L, 4L);
            emitFramedBillboard(sp, TEX_FEATHERS_MIST, 4, ff, fp, camera, 1.85, COL_TEANA, COL_TEANA_HOT, (int) (230 * fade), 0.88);
        }
        if (u < 0.72) {
            LightningBolt.helixArcs(sp, ground, ground.add(0, pillarH * 0.88, 0), camera, time, r * 0.48, 3, COL_TEANA_HOT, (int) (245 * fade));
            // Side dramatic energy wings echoing the Battle Goddess crest
            int wf = (int) Math.floorMod(time / 95L, 4L);
            for (int side : new int[]{-1, 1}) {
                Vec3 sh = point.add(0, 1.2, 0).add(right.scale(side * 0.6));
                emitWing(sp, sh, right.scale(side * 0.92).add(0, 0.24, 0).normalize(), UP, r * 0.58, r * 0.72, wf, (int) (215 * fade));
            }
        }
    }

    // --- Conditional 3rd-Person Awakening Camera (Apostle Spear Finisher Only, Wall-Clip Safe) ---

    static SkillVfx.CameraPose teanaFinisherCamera(SkillVfx.Track t, long time) {
        var mc = Minecraft.getInstance();
        if (t == null || mc == null || mc.level == null) return null;
        Vec3 origin = vector(t.start.origin());
        Vec3 dir = horizontalDir(vector(t.start.direction()));
        Vec3 side = rightOf(dir);
        Meta m = METAS.getOrDefault(t.start.cast(), DEFAULT_META);

        long age = Math.max(0L, time - t.start.startedAt());
        boolean finished = t.last.phase() == FINISH;
        double u = Math.clamp(age / 1400.0, 0.0, 1.0);
        double sweep = EffectSprites.easeOutCubic(u);

        Vec3 focus = finished
            ? vector(t.last.position()).add(0, 1.6, 0)
            : origin.add(dir.scale(Math.min(5.0, m.range * 0.42 * sweep))).add(0, 1.75, 0);

        double backDist = lerp(6.8, 10.6, sweep);
        double sideDist = lerp(3.4, 5.2, sweep);
        double height = lerp(3.2, 5.4, sweep);
        Vec3 pos = focus.subtract(dir.scale(backDist)).add(side.scale(sideDist)).add(0, height, 0);

        var wall = mc.level.clip(new ClipContext(focus, pos, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
        if (wall.getType() != HitResult.Type.MISS) {
            pos = wall.getLocation().add(focus.subtract(pos).normalize().scale(0.30));
        }
        Vec3 look = focus.subtract(pos);
        float yaw = (float) Math.toDegrees(Math.atan2(-look.x, look.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(look.y, look.horizontalDistance()));
        return new SkillVfx.CameraPose(pos, yaw, pitch);
    }

    // --- Dual-Layer Sprite Geometry Emitters (Strict UV Orientation Contract) ---

    /** Oriented forward-leading martial crescent slash (arc peak at +x leads along dir). */
    static void emitSlash(
        List<SpriteQuad> sp, Vec3 center, Vec3 dir, double rollDeg, double radius,
        double progress, boolean flip, int baseRgb, int addRgb, int alpha
    ) {
        if (progress < 0.0 || progress >= 1.0 || radius <= 0.0 || alpha <= 0) return;
        Vec3 d = horizontalDir(dir);
        Vec3 right0 = rightOf(d);
        Vec3 up0 = d.cross(right0).normalize();
        double roll = Math.toRadians(rollDeg);
        Vec3 side = right0.scale(Math.cos(roll)).add(up0.scale(Math.sin(roll))).normalize();
        double scale = radius * (0.86 + 0.24 * EffectSprites.easeOutCubic(progress));
        int frame = Math.clamp((int) (progress * 8), 0, 7);
        float u0 = frame / 8.0f;
        float u1 = (frame + 1) / 8.0f;
        int a = Math.clamp((int) Math.round(alpha * (1.0 - 0.35 * progress * progress)), 0, 255);
        if (a <= 4) return;

        Vec3 halfU = d.scale(scale);
        Vec3 halfV = side.scale(flip ? -scale : scale);
        EffectSprites.emitPlane(sp, TEX_MARTIAL_SLASH, Blend.TRANSLUCENT, center, halfU, halfV, u0, 0f, u1, 1f, baseRgb, a);
        EffectSprites.emitPlane(sp, TEX_MARTIAL_SLASH, Blend.ADDITIVE, center.add(0, 0.025, 0), halfU.scale(0.98), halfV.scale(0.98), u0, 0f, u1, 1f, addRgb, Math.clamp((int) (a * 0.82), 0, 255));
    }

    /** Upright camera-facing framed billboard: halfV = -up so PNG top (v=0) is ALWAYS up! */
    static void emitFramedBillboard(
        List<SpriteQuad> sp, Identifier tex, int frames, int frame, Vec3 center, Vec3 camera,
        double radius, int baseRgb, int addRgb, int alpha, double addScale
    ) {
        if (radius <= 0.0 || alpha <= 4) return;
        Vec3 look = camera.subtract(center);
        if (look.lengthSqr() < 1e-6) look = new Vec3(0, 0, 1);
        else look = look.normalize();
        Vec3 right = look.cross(Math.abs(look.y) > 0.95 ? new Vec3(1, 0, 0) : UP).normalize();
        Vec3 up = right.cross(look).normalize();
        int f = Math.clamp(frame, 0, Math.max(1, frames) - 1);
        float u0 = f / (float) frames;
        float u1 = (f + 1) / (float) frames;
        int a = Math.clamp(alpha, 0, 255);
        Vec3 halfU = right.scale(radius);
        Vec3 halfV = up.scale(-radius);
        EffectSprites.emitPlane(sp, tex, Blend.TRANSLUCENT, center, halfU, halfV, u0, 0f, u1, 1f, baseRgb, a);
        int addA = Math.clamp((int) Math.round(a * addScale), 0, 255);
        if (addA > 4) {
            EffectSprites.emitPlane(sp, tex, Blend.ADDITIVE, center.add(look.scale(0.03)), halfU.scale(1.03), halfV.scale(1.03), u0, 0f, u1, 1f, addRgb, addA);
        }
    }

    /** Multi-plane vertical crossed column (0/60/120 deg): halfV points DOWN so v=0 is at TOP! */
    static void emitVerticalCrossed(
        List<SpriteQuad> sp, Identifier tex, int frames, int frame, Vec3 base,
        double halfWidth, double height, double yawRad, int planes,
        int baseRgb, int addRgb, int alpha, double addScale
    ) {
        if (halfWidth <= 0.0 || height <= 0.0 || alpha <= 4) return;
        int f = Math.clamp(frame, 0, Math.max(1, frames) - 1);
        float u0 = f / (float) frames;
        float u1 = (f + 1) / (float) frames;
        int a = Math.clamp(alpha, 0, 255);
        int addA = Math.clamp((int) Math.round(a * addScale), 0, 255);
        Vec3 center = base.add(0, height * 0.5, 0);
        Vec3 halfV = new Vec3(0, -height * 0.5, 0);
        int count = Math.max(1, planes);
        for (int i = 0; i < count; i++) {
            double ang = yawRad + i * (Math.PI / count);
            Vec3 halfU = new Vec3(Math.cos(ang) * halfWidth, 0, Math.sin(ang) * halfWidth);
            EffectSprites.emitPlane(sp, tex, Blend.TRANSLUCENT, center, halfU, halfV, u0, 0f, u1, 1f, baseRgb, a);
            if (addA > 4) {
                EffectSprites.emitPlane(sp, tex, Blend.ADDITIVE, center, halfU.scale(1.04), halfV, u0, 0f, u1, 1f, addRgb, addA);
            }
        }
    }

    /**
     * Multi-plane axial energy spear/drill/halberd along arbitrary axis (from base -> base + axis * length).
     * halfV = axis.scale(-length * 0.5) guarantees PNG top (v=0, spearhead/dragon head) is ALWAYS at tip!
     */
    static void emitAxisCrossed(
        List<SpriteQuad> sp, Identifier tex, int frames, int frame, Vec3 base, Vec3 axis,
        double length, double halfWidth, int planes,
        int baseRgb, int addRgb, int alpha, double addScale
    ) {
        if (length <= 0.02 || halfWidth <= 0.0 || alpha <= 4 || axis.lengthSqr() < 1e-6) return;
        Vec3 dir = axis.normalize();
        Vec3 ref = Math.abs(dir.y) > 0.94 ? new Vec3(1, 0, 0) : UP;
        Vec3 uPerp = dir.cross(ref).normalize();
        Vec3 vPerp = dir.cross(uPerp).normalize();

        int f = Math.clamp(frame, 0, Math.max(1, frames) - 1);
        float u0 = f / (float) frames;
        float u1 = (f + 1) / (float) frames;
        int a = Math.clamp(alpha, 0, 255);
        int addA = Math.clamp((int) Math.round(a * addScale), 0, 255);

        Vec3 center = base.add(dir.scale(length * 0.5));
        Vec3 halfV = dir.scale(-length * 0.5);
        int count = Math.max(1, planes);
        for (int i = 0; i < count; i++) {
            double ang = i * (Math.PI / count);
            Vec3 side = uPerp.scale(Math.cos(ang)).add(vPerp.scale(Math.sin(ang)));
            Vec3 halfU = side.scale(halfWidth);
            EffectSprites.emitPlane(sp, tex, Blend.TRANSLUCENT, center, halfU, halfV, u0, 0f, u1, 1f, baseRgb, a);
            if (addA > 4) {
                EffectSprites.emitPlane(sp, tex, Blend.ADDITIVE, center, halfU.scale(1.04), halfV, u0, 0f, u1, 1f, addRgb, addA);
            }
        }
    }

    /** Sculpted Teana Seraphic Angel Wing: u0=shoulder root, u1=feather tips, v=0 top arch, v=1 bottom plumes. */
    static void emitWing(
        List<SpriteQuad> sp, Vec3 shoulder, Vec3 outAxis, Vec3 upAxis,
        double width, double height, int frame, int alpha
    ) {
        if (width <= 0.0 || height <= 0.0 || alpha <= 4) return;
        int f = Math.clamp(frame, 0, 3);
        float u0 = f / 4.0f;
        float u1 = (f + 1) / 4.0f;
        int a = Math.clamp(alpha, 0, 255);
        Vec3 out = outAxis.normalize();
        Vec3 up = upAxis.normalize();
        Vec3 normal = out.cross(up);
        if (normal.lengthSqr() > 1e-6) normal = normal.normalize();

        Vec3 center = shoulder.add(out.scale(width * 0.5));
        Vec3 halfU = out.scale(width * 0.5);
        Vec3 halfV = up.scale(-height * 0.5);
        EffectSprites.emitPlane(sp, TEX_TEANA_WING, Blend.TRANSLUCENT, center, halfU, halfV, u0, 0f, u1, 1f, COL_WHITE, a);
        EffectSprites.emitPlane(sp, TEX_TEANA_WING, Blend.ADDITIVE, center.add(normal.scale(0.025)), halfU.scale(1.03), halfV.scale(1.03), u0, 0f, u1, 1f, COL_TEANA_HOT, Math.clamp((int) (a * 0.72), 0, 255));
    }

    /** Dual-layer single-frame ground decal (sigils, halos, shattered craters). */
    static void emitGroundSprite(
        List<SpriteQuad> sp, Identifier tex, Vec3 center, double radius, double rotRad,
        int baseRgb, int addRgb, int alpha, double addScale
    ) {
        if (radius <= 0.0 || alpha <= 4) return;
        int a = Math.clamp(alpha, 0, 255);
        EffectSprites.emitGround(sp, tex, Blend.TRANSLUCENT, center, radius, rotRad, 0f, 0f, 1f, 1f, baseRgb, a);
        int addA = Math.clamp((int) Math.round(a * addScale), 0, 255);
        if (addA > 4) {
            EffectSprites.emitGround(sp, tex, Blend.ADDITIVE, center.add(0, 0.014, 0), radius * 1.02, rotRad, 0f, 0f, 1f, 1f, addRgb, addA);
        }
    }

    /** Dual-layer multi-frame ground sprite (blossoms, novas). */
    static void emitGroundFrame(
        List<SpriteQuad> sp, Identifier tex, int frames, int frame, Vec3 center, double radius, double rotRad,
        int baseRgb, int addRgb, int alpha, double addScale
    ) {
        if (radius <= 0.0 || alpha <= 4) return;
        int f = Math.clamp(frame, 0, Math.max(1, frames) - 1);
        float u0 = f / (float) frames;
        float u1 = (f + 1) / (float) frames;
        int a = Math.clamp(alpha, 0, 255);
        EffectSprites.emitGround(sp, tex, Blend.TRANSLUCENT, center, radius, rotRad, u0, 0f, u1, 1f, baseRgb, a);
        int addA = Math.clamp((int) Math.round(a * addScale), 0, 255);
        if (addA > 4) {
            EffectSprites.emitGround(sp, tex, Blend.ADDITIVE, center.add(0, 0.015, 0), radius * 1.02, rotRad, u0, 0f, u1, 1f, addRgb, addA);
        }
    }

    // --- Pure Math & State Utilities -----------------------------------------

    private static int countPackedChasers(long packed) {
        int count = 0;
        for (int i = 0; i < 10; i++) {
            int code = (int) ((packed >>> (i * 3)) & 0x7L);
            if (code >= 1 && code <= 5) count++;
        }
        return count;
    }

    private static int unpackChasers(long packed, int[] types) {
        int count = 0;
        for (int i = 0; i < 10; i++) {
            int code = (int) ((packed >>> (i * 3)) & 0x7L);
            if (code >= 1 && code <= 5) {
                types[count++] = code - 1;
            }
        }
        return count;
    }

    private static int chaserFrameFromColor(int rgb) {
        if (rgb == BattleMageRules.COLOR_TEANA) return 5;
        if (rgb == BattleMageRules.COLOR_LIGHT) return 1;
        if (rgb == BattleMageRules.COLOR_FIRE) return 2;
        if (rgb == BattleMageRules.COLOR_ICE) return 3;
        if (rgb == BattleMageRules.COLOR_DARK) return 4;
        return 0;
    }

    private static Vec3 horizontalDir(Vec3 v) {
        if (v == null || v.lengthSqr() < 1e-6) return new Vec3(0, 0, 1);
        Vec3 flat = new Vec3(v.x, 0, v.z);
        return flat.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : flat.normalize();
    }

    private static Vec3 rightOf(Vec3 dir) {
        Vec3 r = new Vec3(-dir.z, 0, dir.x);
        return r.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : r.normalize();
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static double smoothstep(double e0, double e1, double x) {
        if (e1 == e0) return x >= e1 ? 1.0 : 0.0;
        double t = Math.clamp((x - e0) / (e1 - e0), 0.0, 1.0);
        return t * t * (3.0 - 2.0 * t);
    }

    private static long safeNow() {
        try {
            return ClientUi.now();
        } catch (Throwable ignored) {
            return System.currentTimeMillis();
        }
    }

    private static boolean isLocalCaster(UUID casterId) {
        var mc = Minecraft.getInstance();
        return mc != null && mc.player != null && mc.player.getUUID().equals(casterId);
    }

    private static boolean isOwnFirstPerson(UUID casterId) {
        var mc = Minecraft.getInstance();
        return mc != null && mc.player != null && mc.options != null
            && mc.player.getUUID().equals(casterId)
            && mc.options.getCameraType().isFirstPerson();
    }
}
