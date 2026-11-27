package cn.yumis.fight.modules.skills.client;

import cn.yumis.fight.modules.presentation.api.client.CameraShake;
import cn.yumis.fight.modules.presentation.api.client.EffectSprites;
import cn.yumis.fight.modules.presentation.api.client.EffectSprites.Blend;
import cn.yumis.fight.modules.presentation.api.client.EffectSprites.SpriteQuad;
import cn.yumis.fight.modules.presentation.api.client.LightningBolt;
import cn.yumis.fight.modules.presentation.api.client.RibbonTrail;
import cn.yumis.fight.modules.presentation.api.client.WorldEffects;
import cn.yumis.fight.modules.presentation.api.client.WorldQuads.Quad;
import cn.yumis.fight.modules.skills.api.CareerSkills;
import cn.yumis.fight.modules.skills.api.SkillVisualEvent;
import cn.yumis.fight.modules.skills.api.SkillVisualRules;
import cn.yumis.fight.modules.skills.api.StormChoreography;
import cn.yumis.fight.modules.skills.api.client.SkillsClient;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.*;

import static cn.yumis.fight.modules.skills.api.SkillVisualEvent.Phase.*;
import static cn.yumis.fight.modules.skills.client.SkillVfx.vector;

/**
 * Hellblood Demon (狱血魔神 / Berserker) dedicated DNF-inspired VFX renderer.
 * Pure client presentation using custom multi-tone pixel-art sprite sheets (dual-layer translucent dark-crimson
 * backing + additive fiery highlights), sculpted demonic blood greatsword geometry, camera-aligned ribbons,
 * blood lightning, frozen-pose player afterimages, layered audio, and awakening cinematic camera.
 * Never alters combat authority, hitboxes, cooldowns, or bone animation timings.
 */
final class BerserkerVisuals {
    private static final Vec3 UP = new Vec3(0, 1, 0);

    // Custom pixel-art Berserker VFX sprite sheets (source: tools/build_berserker_effect_textures.py)
    static final Identifier TEX_SLASH = tex("blood_slash.png");
    static final Identifier TEX_CLAW = tex("blood_claw.png");
    static final Identifier TEX_GEYSER = tex("blood_geyser.png");
    static final Identifier TEX_CRATER = tex("blood_crater.png");
    static final Identifier TEX_SIGIL = tex("blood_sigil.png");
    static final Identifier TEX_BURST = tex("blood_burst.png");
    static final Identifier TEX_WAVE = tex("blood_wave.png");
    static final Identifier TEX_MIST = tex("blood_mist.png");
    static final Identifier TEX_SWORD_AURA = tex("blood_sword_aura.png");
    static final Identifier TEX_CROSS_REND = tex("blood_cross_rend.png");

    // Classic DNF Blood Evil Remastered palette
    private static final int COL_WHITE = 0xFFFFFF;
    private static final int COL_DARK_BLOOD = 0x3D0612;
    private static final int COL_CRIMSON = 0xDC1836;
    private static final int COL_SCARLET = 0xFF354A;
    private static final int COL_MAGMA = 0xFF6B4A;
    private static final int COL_HOT = 0xFFD8C2;
    private static final int COL_FRENZY_BASIC = 0xE63951;

    private static final List<String> ALL_SKILLS = List.of(
        CareerSkills.FRENZY,
        CareerSkills.CRASH,
        CareerSkills.HAND,
        CareerSkills.RAGE,
        CareerSkills.BLADE,
        CareerSkills.ERUPTION,
        CareerSkills.SWORD
    );

    // Pure-client transient visual state (never authoritative)
    private static final Map<UUID, Integer> LAST_BLOOD_STACKS = new HashMap<>();
    private static final Map<UUID, Long> BLOOD_GAIN_FLASH_AT = new HashMap<>();
    private static final Map<UUID, Integer> LEAP_AFTERIMAGE_STAGE = new HashMap<>();

    private BerserkerVisuals() {}

    private static Identifier tex(String name) {
        return Identifier.fromNamespaceAndPath("yumis_dungeon_and_fight", "textures/effect/berserker/" + name);
    }

    static void clear() {
        LAST_BLOOD_STACKS.clear();
        BLOOD_GAIN_FLASH_AT.clear();
        LEAP_AFTERIMAGE_STAGE.clear();
    }

    static void install() {
        for (String id : ALL_SKILLS) {
            var content = CareerSkills.content(id);
            boolean isSword = CareerSkills.SWORD.equals(id);
            SkillVisualRegistry.register(id, new SkillVisualRegistry.Handler() {
                @Override public boolean persistentEnd(SkillVisualEvent event) {
                    return isPersistentEnd(id, event);
                }

                @Override public boolean cinematic(SkillVisualEvent event) {
                    return isCinematic(id, event);
                }

                @Override public boolean cinematicFromFirstPerson() {
                    return isSword;
                }

                @Override public boolean restoreCamera() {
                    return isSword;
                }

                @Override public CameraType cameraType(SkillVfx.Track track, long time) {
                    return CameraType.THIRD_PERSON_BACK;
                }

                @Override public SkillVfx.CameraPose camera(SkillVfx.Track track, long time) {
                    return isSword && track.start.sweep() ? swordFinisherCamera(track, time) : null;
                }

                @Override public int defaultColor() {
                    return content.color();
                }

                @Override public long defaultDuration() {
                    return (long) (content.skill().parameters(1).getOrDefault("duration", 1d) * 1000);
                }

                @Override public long finishedUntil(SkillVfx.Track track, SkillVisualEvent event) {
                    return computeFinishedUntil(id, event);
                }

                @Override public void play(SkillVfx.Track track, SkillVisualEvent event) {
                    BerserkerVisuals.playCue(id, track, event);
                }

                @Override public void render(SkillVfx.LevelRenderContext ctx, SkillVfx.Track track, long age, long now, List<Quad> quads, Vec3 camera) {
                    BerserkerVisuals.renderSkill(id, track, age, now, quads, ctx.sprites(), camera);
                }
            });
        }
        registerDetachedVisual(SkillVisualRules.BERSERKER_BLOOD_CLAW, 0xc92f43, 1100L);
        registerDetachedVisual(SkillVisualRules.BERSERKER_FLYING_BLADE, 0xf14955, 1600L);
    }

    private static void registerDetachedVisual(String id, int color, long defaultDur) {
        SkillVisualRegistry.register(id, new SkillVisualRegistry.Handler() {
            @Override public int defaultColor() { return color; }
            @Override public long defaultDuration() { return defaultDur; }
            @Override public long finishedUntil(SkillVfx.Track track, SkillVisualEvent event) {
                return computeFinishedUntil(id, event);
            }
            @Override public void play(SkillVfx.Track track, SkillVisualEvent event) {
                BerserkerVisuals.playCue(id, track, event);
            }
            @Override public void render(SkillVfx.LevelRenderContext ctx, SkillVfx.Track track, long age, long now, List<Quad> quads, Vec3 camera) {
                BerserkerVisuals.renderSkill(id, track, age, now, quads, ctx.sprites(), camera);
            }
        });
    }

    // --- Frenzy Dual-Blade Basic Attack Detection & Rendering ----------------

    static boolean isFrenzyBasic(SkillVisualEvent event) {
        if (event == null || !"ATTACK".equals(event.skill()) || event.color() != COL_FRENZY_BASIC) return false;
        String owner = event.caster().toString();
        for (var actor : CareerClient.INSTANCE.view.actors) {
            if (owner.equals(actor.owner)) return actor.frenzy;
        }
        return true;
    }

    static void playFrenzyBasicAudio(SkillVisualEvent event) {
        if (event.phase() != START) return;
        boolean offhandStrike = BuiltinSkillVisuals.isDualOffhandStrike(event);
        float pitch = (offhandStrike ? 0.96f : 0.88f) + (float) (new SplittableRandom(event.seed()).nextDouble() * 0.22);
        SkillSound.playAt(event.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 0.72f, pitch);
        SkillSound.playAt(event.position(), SoundEvents.PLAYER_ATTACK_CRIT, 0.32f, pitch + 0.12f);
        if (isLocalCaster(event.caster())) {
            CameraShake.local(0.065f, 25L, 65L);
        }
    }

    static void renderFrenzyBasic(List<Quad> quads, List<SpriteQuad> sp, Vec3 camera, SkillVisualEvent e, long age, long baseDuration) {
        if (e.phase() != START) return;
        Vec3 dir = horizontalDir(vector(e.direction()));
        Vec3 right = rightOf(dir);
        Vec3 pos = vector(e.position()).add(dir.scale(e.forwardOffset()));
        double hScale = Math.max(0.8, e.horizontalScale());

        var mc = Minecraft.getInstance();
        Player caster = mc == null || mc.level == null ? null : mc.level.getPlayerByUUID(e.caster());
        double hp = caster != null ? hpFraction(caster) : 1.0;
        boolean lowTier2 = hp <= 0.40;
        boolean lowTier1 = hp <= 0.70;
        double rageScale = lowTier2 ? 1.24 : lowTier1 ? 1.12 : 1.0;

        SplittableRandom rng = new SplittableRandom(e.seed());
        boolean flip = rng.nextBoolean();
        boolean offhandStrike = BuiltinSkillVisuals.isDualOffhandStrike(e);

        if (e.sweep()) {
            var sample = BuiltinSkillVisuals.basicSlashSample(age, baseDuration);
            if (sample == null) return;
            double progress = sample.progress();
            double aScale = sample.alphaScale();
            double rawProgress = Math.clamp(age / (double) Math.max(1L, baseDuration), 0.0, 0.999);
            // Greatsword mastery frenzy giant crimson cleave
            double r = 3.55 * hScale * rageScale;
            Vec3 center = pos.add(dir.scale(1.6)).add(0, 1.15, 0);
            int a1 = (int) Math.round(248 * aScale);
            int a2 = (int) Math.round(220 * aScale);
            if (a1 > 4) emitBloodSlash(sp, center, dir, flip ? -12 : 12, r, progress, flip, a1, true);
            if (a2 > 4) emitBloodSlash(sp, center.add(0, 0.18, 0), dir, flip ? 20 : -20, r * 0.88, Math.min(0.999, progress * 1.06), !flip, a2, true);
            if (rawProgress < 0.72) {
                int aRend = (int) Math.round(230 * (0.35 + 0.65 * aScale));
                if (aRend > 4) emitCrossRend(sp, center.add(dir.scale(1.2)), camera, 1.95 * rageScale, rawProgress / 0.72, aRend);
            }
            emitBloodMist(sp, center.add(dir.scale(0.8)), camera, 1.9 * rageScale, e.at() + age, (int) Math.round(145 * aScale), 1);
        } else if (e.dual()) {
            // Alternating single crimson slash per dual-blade strike:
            // Strike 0 (main hand / right): upper-right -> lower-left; Strike 1 (off-hand / left): upper-left -> lower-right.
            var sample = BuiltinSkillVisuals.basicSlashSample(age, baseDuration);
            if (sample == null) return;
            boolean leftMainArm = caster != null && caster.getMainArm() == net.minecraft.world.entity.HumanoidArm.LEFT;
            boolean leftCut = offhandStrike ^ leftMainArm;
            double tiltMag = 28.0 + rng.nextDouble() * 14.0;
            double tilt = leftCut ? -tiltMag : tiltMag;
            boolean mirror = !leftCut;
            Vec3 c = pos.add(dir.scale(0.70)).add(0, leftCut ? 1.09 : 1.19, 0);
            double rad = (leftCut ? 2.10 : 2.05) * hScale * rageScale;
            int alpha = (int) Math.round(248 * sample.alphaScale());
            if (alpha > 4) {
                emitBloodSlash(sp, c, dir, tilt, rad, sample.progress(), mirror, alpha, true);
            }
            long rendWin = Math.max(1L, Math.round(baseDuration * 0.72));
            if (lowTier2 && offhandStrike && age >= 0 && age < rendWin) {
                int aRend = (int) Math.round(220 * (0.35 + 0.65 * sample.alphaScale()));
                if (aRend > 4) emitCrossRend(sp, c.add(dir.scale(0.95)), camera, 1.55, age / (double) rendWin, aRend);
            }
            if (offhandStrike && caster != null && !isOwnFirstPerson(e.caster()) && age < 220L) {
                var pose = Effects.VFX.playerPose(caster.getUUID(), e.at() + age);
                Vec3 offHand = PlayerBoneAnchor.hand(caster, pose == null ? null : pose.frame(), false);
                double glowScale = Math.max(0.25, 0.85 * (1.0 - age / 220.0));
                int glowAlpha = (int) Math.round(220 * Math.min(1.0, glowScale));
                if (glowAlpha > 4) {
                    EffectSprites.glow(sp, offHand, camera, 0.48 * glowScale, lowTier2 ? COL_MAGMA : COL_SCARLET, glowAlpha);
                }
                Vec3 offTip = offHand.add(dir.scale(1.18)).add(right.scale(leftMainArm ? 0.22 : -0.22));
                RibbonTrail.push("frenzy_off_atk:" + e.cast(), offTip, 0.20 * rageScale, lowTier2 ? COL_MAGMA : COL_SCARLET, 220, 175L, e.at() + age);
            }
        } else {
            var sample = BuiltinSkillVisuals.basicSlashSample(age, baseDuration);
            if (sample == null) return;
            double progress = sample.progress();
            double aScale = sample.alphaScale();
            double rawProgress = Math.clamp(age / (double) Math.max(1L, baseDuration), 0.0, 0.999);
            // Single brutal diagonal crimson slash + thick dark-blood echo smear
            double roll = (flip ? 1 : -1) * (26.0 + rng.nextDouble() * 34.0);
            Vec3 c = pos.add(dir.scale(0.65)).add(0, 1.14, 0);
            double rad = 2.00 * hScale * rageScale;
            int a1 = (int) Math.round(248 * aScale);
            int a2 = (int) Math.round(180 * aScale);
            if (a1 > 4) emitBloodSlash(sp, c, dir, roll, rad, progress, flip, a1, true);
            if (a2 > 4) emitBloodSlash(sp, c.add(0, -0.06, 0), dir, roll * 0.65, rad * 0.88, Math.min(0.999, progress * 0.92), flip, a2, false);
            if (lowTier2 && rawProgress < 0.75) {
                int aRend = (int) Math.round(210 * (0.35 + 0.65 * aScale));
                if (aRend > 4) emitCrossRend(sp, c.add(dir.scale(1.0)), camera, 1.40, rawProgress / 0.75, aRend);
            }
        }

        // Dynamic blood ribbon from animated main-hand weapon tip during main-hand slashes
        if (!offhandStrike && caster != null && age < 220) {
            var pose = Effects.VFX.playerPose(caster.getUUID(), e.at() + age);
            Vec3 tip = PlayerBoneAnchor.weaponTip(caster, pose == null ? null : pose.frame(), 1.25);
            RibbonTrail.push("frenzy_atk:" + e.cast(), tip, 0.22 * rageScale, lowTier2 ? COL_MAGMA : COL_SCARLET, 225, 190L, e.at() + age);
        }
    }

    // --- Persistent Actor Visuals: Frenzy Dual-Blade, Low-HP Awakening, Back Blood Greatsword ---

    static void actors(List<Quad> quads, List<SpriteQuad> sp, Vec3 camera, long serverNow, long renderNow) {
        var mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) return;

        for (var actor : CareerClient.INSTANCE.view.actors) {
            boolean frenzy = actor.frenzy;
            boolean chargingSword = actor.bloodUntil > serverNow;
            if (!frenzy && !chargingSword) continue;

            UUID ownerId;
            try {
                ownerId = UUID.fromString(actor.owner);
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            Player p = mc.level.getPlayerByUUID(ownerId);
            if (p == null || !p.isAlive()) continue;

            Vec3 base = PlayerVisualAnchor.position(p);
            Vec3 forward = horizontalDir(PlayerVisualAnchor.forward(p));
            Vec3 right = rightOf(forward);
            var poseSample = Effects.VFX.playerPose(ownerId, renderNow);
            PlayerPoseAnimation.Frame frame = poseSample == null ? null : poseSample.frame();

            // 1. 血之狂暴常驻双刀 + 血气唤醒低血狂化 (<=70% / <=40%)
            if (frenzy) {
                double hp = hpFraction(p);
                int lowTier = hp <= 0.40 ? 2 : hp <= 0.70 ? 1 : 0;

                // Offhand condensed serrated demonic blood-blade anchored to left hand
                // (paused while the animated left-hand blood sword item is actively slashing during basic attacks)
                Vec3 offHand = PlayerBoneAnchor.hand(p, frame, false);
                Vec3 bladeDir = forward.scale(0.86).add(0, -0.18 + 0.08 * Math.sin(renderNow * 0.006), 0).add(right.scale(-0.18)).normalize();
                double offLen = lowTier == 2 ? 1.55 : lowTier == 1 ? 1.38 : 1.25;
                Vec3 offTip = offHand.add(bladeDir.scale(offLen));
                if (!Effects.VFX.hasActiveOffhandBlade(ownerId, renderNow)) {
                    sculptedBloodSword(quads, sp, offHand.subtract(bladeDir.scale(0.18)), bladeDir, offLen, 0.14, 0.16, renderNow, lowTier == 2 ? 1.0 : 0.65, 220);
                }

                // Trailing blood ribbon off the offhand blood blade tip when moving or swinging
                if (p.getDeltaMovement().horizontalDistanceSqr() > 0.0012 || frame != null) {
                    RibbonTrail.push("frenzy_off:" + ownerId, offTip, 0.15 + 0.04 * lowTier, lowTier == 2 ? COL_MAGMA : COL_SCARLET, 200, 210L, renderNow);
                }

                // Swirling demonic blood-mist rising around the Berserker
                int mistCount = lowTier == 2 ? 4 : lowTier == 1 ? 3 : 2;
                for (int i = 0; i < mistCount; i++) {
                    double a = renderNow * (0.0024 + lowTier * 0.0008) + i * (Math.TAU / mistCount);
                    double rise = ((renderNow * (0.0009 + lowTier * 0.0003) + i * 0.37) % 1.0);
                    double rad = 0.58 + 0.16 * Math.sin(a * 2.0) - rise * 0.15;
                    Vec3 mp = base.add(Math.cos(a) * rad, 0.25 + rise * 1.55, Math.sin(a) * rad);
                    int ma = (int) ((lowTier == 2 ? 185 : lowTier == 1 ? 145 : 105) * Math.sin(rise * Math.PI));
                    emitBloodMist(sp, mp, camera, 0.58 + lowTier * 0.14, renderNow + i * 190L, ma, i);
                }

                // 血气唤醒 Tier 1 (<=70%) & Tier 2 (<=40%) dramatic aura escalation!
                if (lowTier >= 1) {
                    double pulse = 0.5 + 0.5 * Math.sin(renderNow * (lowTier == 2 ? 0.012 : 0.007));
                    double sigilR = (lowTier == 2 ? 1.55 : 1.15) + 0.12 * pulse;
                    emitBloodSigil(sp, base.add(0, 0.05, 0), sigilR, renderNow * (lowTier == 2 ? 0.0022 : 0.0012), lowTier == 2 ? 195 : 120, lowTier == 2);

                    // Flickering crimson blood-arcs snapping around shoulders & chest
                    long sparkStep = renderNow / (lowTier == 2 ? 70L : 115L);
                    int bolts = lowTier == 2 ? 2 : 1;
                    for (int b = 0; b < bolts; b++) {
                        SplittableRandom rng = new SplittableRandom(sparkStep * 0x9E3779B97F4A7C15L ^ ownerId.getMostSignificantBits() ^ (b * 4099L));
                        double a0 = rng.nextDouble() * Math.TAU;
                        double a1 = a0 + 1.4 + rng.nextDouble() * 1.6;
                        Vec3 p0 = base.add(Math.cos(a0) * 0.65, 0.35 + rng.nextDouble() * 1.25, Math.sin(a0) * 0.65);
                        Vec3 p1 = base.add(Math.cos(a1) * 0.65, 0.45 + rng.nextDouble() * 1.30, Math.sin(a1) * 0.65);
                        LightningBolt.bolt(sp, p0, p1, camera, sparkStep ^ b, 5, 0.22, lowTier == 2 ? 0.055 : 0.036, lowTier == 2 ? COL_MAGMA : COL_SCARLET, lowTier == 2 ? 225 : 170, false);
                    }
                    if (lowTier == 2) {
                        EffectSprites.glow(sp, base.add(0, 1.05, 0), camera, 1.35 + 0.22 * pulse, COL_SCARLET, 130);
                    }
                }
            }

            // 2. 魔狱血刹蓄血背后悬浮巨剑 (0 -> 20 层震撼成长进化!)
            if (chargingSword) {
                int stacks = Math.clamp(actor.blood, 0, 20);
                Integer prevStacks = LAST_BLOOD_STACKS.put(ownerId, stacks);
                if (prevStacks != null && stacks > prevStacks) {
                    BLOOD_GAIN_FLASH_AT.put(ownerId, renderNow);
                    WorldEffects.playShockwave(base.add(0, 1.3, 0), 0.3, 1.6 + stacks * 0.06, COL_SCARLET, 210, 280L);
                    SkillSound.playAt(base.x, base.y + 1.2, base.z, SoundEvents.SOUL_ESCAPE.value(), 0.48f, 0.95f + stacks * 0.03f);
                }

                double ratio = stacks / 20.0;
                double hoverBob = Math.sin(renderNow * 0.0042) * 0.14;
                long sinceGain = renderNow - BLOOD_GAIN_FLASH_AT.getOrDefault(ownerId, 0L);
                double gainFlash = sinceGain >= 0 && sinceGain < 350L ? (1.0 - sinceGain / 350.0) : 0.0;

                // Iconic DNF diagonal suspension high behind the Berserker's back!
                double swordLen = 1.95 + ratio * 2.65 + gainFlash * 0.28;
                double swordWidth = 0.24 + ratio * 0.36;
                Vec3 hiltBase = base.subtract(forward.scale(0.85 + ratio * 0.35))
                    .add(right.scale(-0.45 - ratio * 0.25))
                    .add(0, 0.72 + hoverBob, 0);
                // Tilts heroically upward & diagonally across the back like DNF Hellblood Demon!
                Vec3 swordAxis = UP.scale(0.90).add(right.scale(0.36)).subtract(forward.scale(0.24)).normalize();
                Vec3 swordMid = hiltBase.add(swordAxis.scale(swordLen * 0.52));
                Vec3 swordTip = hiltBase.add(swordAxis.scale(swordLen));

                sculptedBloodSword(quads, sp, hiltBase, swordAxis, swordLen, swordWidth, 0.30 + ratio * 0.24, renderNow, 0.45 + 0.55 * ratio + gainFlash * 0.4, 245);

                // Blood-gain siphon burst converging into the sword when stacking!
                if (gainFlash > 0.02) {
                    double u = 1.0 - gainFlash;
                    emitBloodBurst(sp, swordMid, camera, 1.45 + ratio * 1.1, u, 230, false);
                    EffectSprites.glow(sp, swordMid, camera, 1.8 + ratio * 1.4, COL_MAGMA, (int) (235 * gainFlash));
                }

                // Mid-to-high stack demonic halo & orbiting blood ribbons behind hilt
                if (stacks >= 6) {
                    double haloScale = 0.95 + ratio * 1.35;
                    Vec3 haloCenter = hiltBase.add(swordAxis.scale(swordLen * 0.34));
                    Vec3 n = forward;
                    Vec3 hu = right;
                    Vec3 hv = UP;
                    double spin = renderNow * (0.0020 + ratio * 0.0025);
                    double c = Math.cos(spin) * haloScale, s = Math.sin(spin) * haloScale;
                    Vec3 ru = hu.scale(c).add(hv.scale(s));
                    Vec3 rv = hv.scale(c).subtract(hu.scale(s));
                    EffectSprites.emitPlane(sp, TEX_SIGIL, Blend.TRANSLUCENT, haloCenter, ru, rv, 0f, 0f, 1f, 1f, COL_WHITE, (int) (135 + 80 * ratio));
                    EffectSprites.emitPlane(sp, TEX_SIGIL, Blend.ADDITIVE, haloCenter.add(n.scale(0.02)), ru.scale(0.96), rv.scale(0.96), 0f, 0f, 1f, 1f, COL_SCARLET, (int) (110 + 100 * ratio));
                }

                // High-stack (12+) crackling blood lightning wrapping the giant sword!
                if (stacks >= 12) {
                    int arcs = stacks >= 20 ? 3 : 2;
                    LightningBolt.helixArcs(sp, hiltBase.add(swordAxis.scale(swordLen * 0.15)), swordTip, camera, renderNow, 0.38 + ratio * 0.32, arcs, stacks >= 20 ? COL_MAGMA : COL_SCARLET, stacks >= 20 ? 235 : 190);
                }

                // Max-stack (20) blazing abyssal blood-mist crown
                if (stacks >= 20) {
                    emitBloodMist(sp, swordMid, camera, 1.95, renderNow, 175, 0);
                    EffectSprites.glow(sp, swordTip, camera, 1.45 + 0.25 * Math.sin(renderNow * 0.015), COL_HOT, 215);
                }
            } else {
                LAST_BLOOD_STACKS.remove(ownerId);
            }
        }
    }

    // --- Skill Audio & Camera Shake Choreography -----------------------------

    private static void playCue(String id, SkillVfx.Track track, SkillVisualEvent e) {
        Vec3 pos = vector(e.position());
        var mc = Minecraft.getInstance();
        Player actor = mc == null || mc.level == null ? null : mc.level.getPlayerByUUID(e.caster());

        switch (id) {
            case CareerSkills.FRENZY -> {
                if (e.phase() != START) return;
                if (!e.sweep()) {
                    // Opening Frenzy: demonic blood roar + metallic draw
                    SkillSound.playAt(e.position(), SoundEvents.BLAZE_SHOOT, 0.78f, 0.62f);
                    SkillSound.playAt(e.position(), SoundEvents.SOUL_ESCAPE.value(), 0.90f, 0.68f);
                    SkillSound.playAt(e.position(), SoundEvents.PLAYER_ATTACK_STRONG, 0.75f, 0.78f);
                    CameraShake.impulse(pos, 14, 0.28f, 80L, 170L);
                    if (actor != null) PlayerAfterimages.capture(actor, COL_SCARLET, 215, 420L, true);
                    WorldEffects.playShockwave(pos.add(0, 0.10, 0), 0.4, 3.2, COL_SCARLET, 235, 360L);
                } else {
                    SkillSound.playAt(e.position(), SoundEvents.SOUL_ESCAPE.value(), 0.55f, 1.15f);
                }
            }
            case CareerSkills.CRASH -> {
                if (e.phase() == START) {
                    LEAP_AFTERIMAGE_STAGE.put(e.cast(), 0);
                    SkillSound.playAt(e.position(), SoundEvents.BREEZE_JUMP, 0.70f, 0.72f);
                    SkillSound.playAt(e.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 0.75f, 0.85f);
                    if (actor != null) PlayerAfterimages.capture(actor, COL_CRIMSON, 195, 340L, true);
                } else if (e.phase() == FINISH) {
                    LEAP_AFTERIMAGE_STAGE.remove(e.cast());
                    SkillSound.playAt(e.position(), SoundEvents.MACE_SMASH_GROUND_HEAVY, 0.95f, 0.76f);
                    SkillSound.playAt(e.position(), SoundEvents.GENERIC_EXPLODE.value(), 0.72f, 0.68f);
                    CameraShake.impulse(pos, 18, 0.42f, 100L, 210L);
                }
            }
            case CareerSkills.HAND -> {
                if (e.dual()) return;
                if (e.phase() == START) {
                    SkillSound.playAt(e.position(), SoundEvents.SOUL_ESCAPE.value(), 0.80f, 0.75f);
                    SkillSound.playAt(e.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 0.65f, 0.68f);
                } else if (e.phase() == RELEASE) {
                    SkillSound.playAt(e.position(), SoundEvents.MACE_SMASH_GROUND, 0.88f, 0.82f);
                    SkillSound.playAt(e.position(), SoundEvents.GENERIC_EXPLODE.value(), 0.65f, 0.92f);
                    CameraShake.impulse(pos, 16, 0.36f, 85L, 180L);
                }
            }
            case SkillVisualRules.BERSERKER_BLOOD_CLAW -> {
                if (e.phase() == START) {
                    SkillSound.playAt(e.origin(), SoundEvents.SOUL_ESCAPE.value(), 0.90f, 0.66f);
                    SkillSound.playAt(e.origin(), SoundEvents.PLAYER_ATTACK_SWEEP, 0.82f, 0.62f);
                } else if (e.phase() == RELEASE) {
                    SkillSound.playAt(e.position(), SoundEvents.MACE_SMASH_GROUND_HEAVY, 0.96f, 0.74f);
                    SkillSound.playAt(e.position(), SoundEvents.GENERIC_EXPLODE.value(), 0.82f, 0.82f);
                    CameraShake.impulse(pos, 20, 0.44f, 100L, 210L);
                }
            }
            case CareerSkills.RAGE -> {
                if (e.phase() == START) {
                    SkillSound.playAt(e.position(), SoundEvents.MACE_SMASH_GROUND_HEAVY, 0.85f, 0.68f);
                    SkillSound.playAt(e.position(), SoundEvents.BLAZE_SHOOT, 0.75f, 0.58f);
                    CameraShake.impulse(pos, 18, 0.34f, 90L, 170L);
                    if (actor != null) PlayerAfterimages.capture(actor, COL_SCARLET, 190, 380L, true);
                } else if (e.phase() == HIT) {
                    float p = 0.72f + (e.sequence() % 5) * 0.06f;
                    SkillSound.playAt(e.position(), SoundEvents.GENERIC_EXPLODE.value(), 0.58f, p);
                    SkillSound.playAt(e.position(), SoundEvents.FIRECHARGE_USE, 0.62f, p + 0.12f);
                    CameraShake.impulse(pos, 16, 0.22f, 55L, 110L);
                }
            }
            case CareerSkills.BLADE -> {
                if (e.dual() && !e.sweep()) return;
                if (e.phase() == START) {
                    SkillSound.playAt(e.position(), SoundEvents.TRIDENT_THROW.value(), 0.78f, 0.78f);
                    SkillSound.playAt(e.position(), SoundEvents.SOUL_ESCAPE.value(), 0.72f, 0.85f);
                    if (actor != null) PlayerAfterimages.capture(actor, COL_SCARLET, 200, 360L, true);
                } else if (e.phase() == FINISH) {
                    SkillSound.playAt(e.position(), SoundEvents.MACE_SMASH_GROUND_HEAVY, 0.92f, 0.72f);
                    SkillSound.playAt(e.position(), SoundEvents.GENERIC_EXPLODE.value(), 0.88f, 0.70f);
                    SkillSound.playAt(e.position(), SoundEvents.PLAYER_ATTACK_CRIT, 0.75f, 0.78f);
                    CameraShake.impulse(pos, 20, 0.46f, 110L, 230L);
                }
            }
            case SkillVisualRules.BERSERKER_FLYING_BLADE -> {
                if (e.phase() == START) {
                    SkillSound.playAt(e.origin(), SoundEvents.TRIDENT_THROW.value(), 0.95f, 0.68f);
                    SkillSound.playAt(e.origin(), SoundEvents.BLAZE_SHOOT, 0.76f, 0.72f);
                } else if (e.phase() == FINISH) {
                    SkillSound.playAt(e.position(), SoundEvents.MACE_SMASH_GROUND_HEAVY, 0.95f, 0.68f);
                    SkillSound.playAt(e.position(), SoundEvents.GENERIC_EXPLODE.value(), 0.92f, 0.66f);
                    SkillSound.playAt(e.position(), SoundEvents.PLAYER_ATTACK_CRIT, 0.78f, 0.75f);
                    CameraShake.impulse(pos, 22, 0.50f, 115L, 240L);
                }
            }
            case CareerSkills.ERUPTION -> {
                if (e.phase() == START) {
                    LEAP_AFTERIMAGE_STAGE.put(e.cast(), 0);
                    SkillSound.playAt(e.position(), SoundEvents.SOUL_ESCAPE.value(), 0.85f, 0.62f);
                    SkillSound.playAt(e.position(), SoundEvents.BLAZE_SHOOT, 0.70f, 0.60f);
                } else if (e.phase() == RELEASE) {
                    LEAP_AFTERIMAGE_STAGE.remove(e.cast());
                    SkillSound.playAt(e.position(), SoundEvents.MACE_SMASH_GROUND_HEAVY, 1.0f, 0.58f);
                    SkillSound.playAt(e.position(), SoundEvents.GENERIC_EXPLODE.value(), 0.95f, 0.58f);
                    CameraShake.impulse(pos, 24, 0.56f, 140L, 280L);
                } else if (e.phase() == HIT) {
                    float pitch = 0.62f + (e.sequence() % 4) * 0.08f;
                    SkillSound.playAt(e.position(), SoundEvents.GENERIC_EXPLODE.value(), 0.72f, pitch);
                    SkillSound.playAt(e.position(), SoundEvents.FIRECHARGE_USE, 0.70f, pitch + 0.10f);
                    CameraShake.impulse(pos, 22, 0.30f, 70L, 150L);
                }
            }
            case CareerSkills.SWORD -> {
                if (!e.sweep()) {
                    if (e.phase() == START) {
                        SkillSound.playAt(e.position(), SoundEvents.BEACON_ACTIVATE, 0.85f, 0.62f);
                        SkillSound.playAt(e.position(), SoundEvents.SOUL_ESCAPE.value(), 0.95f, 0.56f);
                        CameraShake.impulse(pos, 20, 0.34f, 110L, 220L);
                        if (actor != null) PlayerAfterimages.capture(actor, COL_SCARLET, 220, 460L, true);
                    }
                    return;
                }
                if (e.phase() == START) {
                    SkillSound.playAt(e.position(), SoundEvents.BEACON_ACTIVATE, 0.95f, 0.55f);
                    SkillSound.playAt(e.position(), SoundEvents.TRIDENT_THUNDER.value(), 0.82f, 0.66f);
                    CameraShake.impulse(pos, 26, 0.36f, 100L, 200L);
                    if (actor != null) PlayerAfterimages.capture(actor, COL_MAGMA, 230, 520L, true);
                } else if (e.phase() == RELEASE) {
                    SkillSound.playAt(e.position(), SoundEvents.MACE_SMASH_GROUND_HEAVY, 1.0f, 0.52f);
                    SkillSound.playAt(e.position(), SoundEvents.GENERIC_EXPLODE.value(), 0.95f, 0.56f);
                    CameraShake.impulse(pos, 30, 0.62f, 160L, 300L);
                } else if (e.phase() == HIT) {
                    float pitch = 0.62f + (e.sequence() % 5) * 0.07f;
                    SkillSound.playAt(e.position(), SoundEvents.GENERIC_EXPLODE.value(), 0.78f, pitch);
                    SkillSound.playAt(e.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 0.72f, pitch + 0.15f);
                    CameraShake.impulse(pos, 26, 0.34f, 75L, 150L);
                } else if (e.phase() == FINISH) {
                    SkillSound.playAt(e.position(), SoundEvents.GENERIC_EXPLODE.value(), 1.0f, 0.48f);
                    SkillSound.playAt(e.position(), SoundEvents.TRIDENT_THUNDER.value(), 0.95f, 0.58f);
                    SkillSound.playAt(e.position(), SoundEvents.MACE_SMASH_GROUND_HEAVY, 1.0f, 0.55f);
                    CameraShake.impulse(pos, 34, 0.78f, 200L, 380L);
                }
            }
            default -> {}
        }
    }

    static boolean isPersistentEnd(String id, SkillVisualEvent event) {
        return CareerSkills.persistent(id) && !event.sweep();
    }

    static boolean isCinematic(String id, SkillVisualEvent event) {
        return CareerSkills.SWORD.equals(id) && event.sweep();
    }

    static long computeFinishedUntil(String id, SkillVisualEvent event) {
        if (CareerSkills.CRASH.equals(id)) return event.at() + 800L;
        if (CareerSkills.BLADE.equals(id) || SkillVisualRules.BERSERKER_FLYING_BLADE.equals(id)) return event.at() + 750L;
        if (SkillVisualRules.BERSERKER_BLOOD_CLAW.equals(id)) return event.at() + 720L;
        if (CareerSkills.SWORD.equals(id)) return event.at() + 920L;
        return event.at() + 650L;
    }

    // --- Main Skill Rendering Router -----------------------------------------

    static void renderSkill(String id, SkillVfx.Track t, long age, long now, List<Quad> quads, List<SpriteQuad> sp, Vec3 camera) {
        switch (id) {
            case CareerSkills.FRENZY -> renderFrenzy(t, age, now, sp, camera);
            case CareerSkills.CRASH -> renderMountainCrash(t, age, now, sp, camera);
            case CareerSkills.HAND -> renderBloodHand(t, age, now, sp, camera);
            case SkillVisualRules.BERSERKER_BLOOD_CLAW -> renderGiantBloodClaw(t, age, now, sp, camera);
            case CareerSkills.RAGE -> renderBloodRage(t, age, now, sp, camera);
            case CareerSkills.BLADE -> renderBloodBlade(t, age, now, quads, sp, camera);
            case SkillVisualRules.BERSERKER_FLYING_BLADE -> renderFlyingBloodBlade(t, age, now, quads, sp, camera);
            case CareerSkills.ERUPTION -> renderBloodEruption(t, age, now, quads, sp, camera);
            case CareerSkills.SWORD -> renderBloodSword(t, age, now, quads, sp, camera);
            default -> {}
        }
    }

    // --- 1. 血之狂暴 (Frenzy Toggle Activation / Deactivation) ---------------

    private static void renderFrenzy(SkillVfx.Track t, long age, long now, List<SpriteQuad> sp, Vec3 camera) {
        Vec3 p = liveCasterPos(t.start.caster(), vector(t.start.origin()));
        if (t.start.sweep()) {
            // Toggle OFF: inward-dissipating blood mist
            double u = Math.clamp(age / 420.0, 0.0, 0.999);
            double fade = 1.0 - u;
            emitBloodMist(sp, p.add(0, 1.0, 0), camera, 1.9 * (1.0 - 0.35 * u), now, (int) (180 * fade), 0);
            EffectSprites.shockwave(sp, p.add(0, 0.08, 0), 1.8 * (1.0 - 0.65 * u), -u * 2.0, COL_CRIMSON, (int) (190 * fade));
            return;
        }
        // Toggle ON: DNF explosive crimson blood-aura awakening burst!
        double u = Math.clamp(age / 500.0, 0.0, 0.999);
        double fade = 1.0 - u;
        emitBloodSigil(sp, p.add(0, 0.06, 0), 2.8 * (0.45 + 0.70 * EffectSprites.easeOutCubic(u)), now * 0.004, (int) (235 * fade), true);
        emitBloodBurst(sp, p.add(0, 1.05, 0), camera, 3.1, u, 245, false);
        emitCrossRend(sp, p.add(0, 1.15, 0), camera, 2.8, u, 230);
        EffectSprites.shockwave(sp, p.add(0, 0.10, 0), 3.4 * EffectSprites.easeOutCubic(u), u * 1.8, COL_SCARLET, (int) (240 * fade));
        EffectSprites.shockwave(sp, p.add(0, 0.65, 0), 2.5 * EffectSprites.easeOutCubic(u), -u * 1.5, COL_MAGMA, (int) (200 * fade));

        // 6 erupting blood-geyser plumes ringing the Berserker
        for (int i = 0; i < 6; i++) {
            double ang = i * (Math.TAU / 6.0) + u * 0.5;
            double r = 0.75 + 1.15 * EffectSprites.easeOutCubic(u);
            Vec3 bp = p.add(Math.cos(ang) * r, 0.04, Math.sin(ang) * r);
            emitBloodGeyser(sp, bp, 0.65, 2.9, u, ang, 235);
        }
        // Crackling crimson blood-lightning pillars
        for (int i = 0; i < 3; i++) {
            double ang = i * (Math.TAU / 3.0) + now * 0.008;
            Vec3 basePt = p.add(Math.cos(ang) * 1.1, 0.1, Math.sin(ang) * 1.1);
            LightningBolt.bolt(sp, basePt, basePt.add(Math.cos(ang) * 0.4, 2.8, Math.sin(ang) * 0.4), camera, (now / 45L) ^ (i * 97L), 6, 0.32, 0.065, COL_SCARLET, (int) (235 * fade), false);
        }
        emitBloodMist(sp, p.add(0, 1.1, 0), camera, 2.6, now, (int) (210 * fade), 0);
    }

    // --- 2. 崩山击 (Mountain Crash: Leap + Ground Shatter + 4-Way X Blood Waves) ---

    private static void renderMountainCrash(SkillVfx.Track t, long age, long now, List<SpriteQuad> sp, Vec3 camera) {
        Vec3 dir = horizontalDir(vector(t.start.direction()));
        var mc = Minecraft.getInstance();
        Player caster = mc == null || mc.level == null ? null : mc.level.getPlayerByUUID(t.start.caster());

        // Phase A: Airborne leap before landing FINISH
        if (t.last.phase() != FINISH) {
            Vec3 pos = caster != null ? PlayerVisualAnchor.position(caster) : vector(t.last.position());
            var pose = caster == null ? null : Effects.VFX.playerPose(caster.getUUID(), now);
            Vec3 tip = caster != null ? PlayerBoneAnchor.weaponTip(caster, pose == null ? null : pose.frame(), 1.35) : pos.add(0, 1.8, 0);

            // Blazing thick overhead crimson weapon ribbon tracing the jump arc!
            RibbonTrail.push("crash_tip:" + t.start.cast(), tip, 0.34, COL_SCARLET, 240, 290L, now);
            RibbonTrail.push("crash_body:" + t.start.cast(), pos.add(0, 0.9, 0), 0.24, COL_CRIMSON, 175, 240L, now);
            EffectSprites.glow(sp, tip, camera, 0.95, COL_MAGMA, 220);

            // Staggered mid-air crimson afterimage
            int stage = LEAP_AFTERIMAGE_STAGE.getOrDefault(t.start.cast(), 0);
            if (caster != null && stage < 2 && age >= (stage == 0 ? 150L : 310L)) {
                LEAP_AFTERIMAGE_STAGE.put(t.start.cast(), stage + 1);
                PlayerAfterimages.capture(caster, stage == 0 ? COL_CRIMSON : COL_SCARLET, 195, 340L, true);
            }

            // Building overhead vertical blood crescent as Berserker plunges down
            if (age >= 240L) {
                double dropU = Math.clamp((age - 240L) / 280.0, 0.0, 0.999);
                emitBloodSlash(sp, pos.add(0, 1.15, 0).add(dir.scale(0.6)), dir, 86, 2.35, dropU, false, 230, true);
            }
            return;
        }

        // Phase B: Impact & DNF Iconic X-Shaped Surging Blood Waves + Shattered Crater!
        long impAge = Math.max(0L, now - t.last.at());
        if (impAge >= 800L) return;
        double scale = Math.max(0.5, t.start.horizontalScale());
        double u = Math.clamp(impAge / 800.0, 0.0, 0.999);
        double fastU = Math.clamp(impAge / 420.0, 0.0, 0.999);
        double fade = 1.0 - u;
        Vec3 land = vector(t.last.position());
        Vec3 side = rightOf(dir);

        // Shattered lava-blood ground crater (scaled to actual range)
        double craterFade = u < 0.65 ? 1.0 : (1.0 - u) / 0.35;
        emitBloodCrater(sp, land.add(0, 0.04, 0), 3.45 * scale * (0.55 + 0.45 * EffectSprites.easeOutCubic(Math.min(1.0, u * 3.5))), t.start.seed() * 0.001, (int) (245 * craterFade));

        // Immediate overhead vertical crimson slam cleave & cross-rend flash
        if (impAge < 420L) {
            emitBloodSlash(sp, land.add(0, 1.15, 0).add(dir.scale(0.65 * scale)), dir, 88, 3.3 * scale, fastU, false, 250, true);
            emitCrossRend(sp, land.add(0, 0.95, 0).add(dir.scale(0.5 * scale)), camera, 3.2 * scale, fastU, 245);
            emitBloodBurst(sp, land.add(0, 0.35, 0), camera, 3.5 * scale, fastU, 245, true);
        }

        // Twin expanding ground shockwaves
        EffectSprites.shockwave(sp, land.add(0, 0.09, 0), 3.8 * scale * EffectSprites.easeOutCubic(u), u * 1.5, COL_SCARLET, (int) (240 * fade));
        EffectSprites.shockwave(sp, land.add(0, 0.28, 0), 2.9 * scale * EffectSprites.easeOutCubic(u), -u * 1.1, COL_MAGMA, (int) (200 * fade));

        // 4 diagonal X-axis surging blood-waves & erupting tilted splash geysers!
        double waveDist = (0.6 + 2.9 * EffectSprites.easeOutCubic(Math.min(1.0, u * 1.35))) * scale;
        for (int i = 0; i < 4; i++) {
            double ang = Math.PI * 0.25 + i * Math.PI * 0.5;
            Vec3 ray = dir.scale(Math.cos(ang)).add(side.scale(Math.sin(ang))).normalize();
            Vec3 wavePos = land.add(ray.scale(waveDist));
            // Towering directional blood-wave wall surging outward along each X-arm
            emitBloodWave(sp, wavePos.add(0, 0.04, 0), ray, 1.65 * Math.sqrt(scale), 2.05 * Math.sqrt(scale) * (1.0 - 0.25 * u), u, 245);
            // Trailing blood geyser plume erupting right behind the wave crest
            Vec3 plumePos = land.add(ray.scale(waveDist * 0.62));
            emitBloodGeyser(sp, plumePos.add(0, 0.03, 0), 0.72 * Math.sqrt(scale), 2.75 * Math.sqrt(scale), u, ang, 225);
        }

        // Thick churning ground blood-mist
        for (int i = 0; i < 4; i++) {
            double a = i * Math.PI * 0.5 + u * 0.8;
            emitBloodMist(sp, land.add(Math.cos(a) * 1.5 * scale, 0.65 + u * 0.7, Math.sin(a) * 1.5 * scale), camera, 1.85 * Math.sqrt(scale), now + i * 160L, (int) (185 * fade), i);
        }
    }

    // --- 3. 嗜魂之手 (Blood Hand: Demonic Claw Seize -> Crush -> Blood Fountain + Heal Siphon) ---

    private static void renderBloodHand(SkillVfx.Track t, long age, long now, List<SpriteQuad> sp, Vec3 camera) {
        if (t.start.dual()) return;
        double hScale = Math.max(0.5, t.start.horizontalScale());
        Vec3 origin = vector(t.start.origin());
        Vec3 dir = horizontalDir(vector(t.start.direction()));
        Vec3 right = rightOf(dir);

        var releaseEvent = findPhase(t.flashes, RELEASE);
        if (releaseEvent == null) {
            // Phase A (0..350ms): Monstrous pixel-art demonic crimson claw reaching & clamping shut!
            double u = Math.clamp(age / 350.0, 0.0, 1.0);
            double lunge = (1.25 + 0.95 * EffectSprites.easeOutCubic(Math.min(1.0, u * 1.5)) - 0.25 * Math.max(0.0, (u - 0.65) / 0.35)) * hScale;
            Vec3 clawPos = origin.add(dir.scale(lunge)).add(0, 1.15, 0);

            // Frames 0..4 of blood_claw.png (reach -> snap -> crushing grip!)
            int frameIdx = Math.clamp((int) Math.floor(u * 4.99), 0, 4);
            double scale = (1.55 + 0.45 * Math.sin(u * Math.PI * 0.75)) * Math.sqrt(hScale);
            // Dual orientation: camera-facing dominant claw + horizontal forward-reaching claw plane for full 3D depth!
            emitFramedBillboard(sp, TEX_CLAW, 8, frameIdx, clawPos, camera, scale, 250, true);
            emitClawForwardPlane(sp, frameIdx, clawPos, dir, right, scale * 0.95, 215);

            // Swirling demonic sigil & converging blood strands pulling prey into the palm
            emitBloodSigil(sp, clawPos.add(0, -1.08, 0), 2.1 * hScale * (1.2 - 0.35 * u), now * 0.005, (int) (185 + 50 * u), true);
            emitBloodMist(sp, clawPos, camera, 1.75 * Math.sqrt(hScale), now, 185, 0);
            for (int i = 0; i < 3; i++) {
                double a = now * 0.012 + i * (Math.TAU / 3.0);
                double r = 1.35 * hScale * (1.0 - 0.55 * u);
                Vec3 outer = clawPos.add(right.scale(Math.cos(a) * r)).add(0, Math.sin(a) * r * 0.7, 0).add(dir.scale(0.5));
                LightningBolt.bolt(sp, outer, clawPos, camera, (now / 45L) ^ (i * 53L), 5, 0.25, 0.055, COL_SCARLET, 215, false);
            }
            if (u > 0.62) {
                EffectSprites.glow(sp, clawPos, camera, 1.45 * Math.sqrt(hScale) * ((u - 0.62) / 0.38), COL_HOT, 235);
            }
            return;
        }

        // Phase B (RELEASE + 0..650ms): Claw rips open -> erupting blood fountain + blood-orb heal siphon back to caster!
        long relAge = Math.max(0L, now - releaseEvent.at());
        if (relAge >= 680L) return;
        double u = Math.clamp(relAge / 680.0, 0.0, 0.999);
        double fastU = Math.clamp(relAge / 420.0, 0.0, 0.999);
        double fade = 1.0 - u;
        Vec3 burstPos = origin.add(dir.scale(2.0 * hScale)).add(0, 1.10, 0);
        Vec3 ground = origin.add(dir.scale(2.0 * hScale)).add(0, 0.05, 0);

        // Claw shred frames 5..7 tearing across the prey
        if (relAge < 440L) {
            int shredFrame = 5 + Math.clamp((int) Math.floor(fastU * 2.99), 0, 2);
            emitFramedBillboard(sp, TEX_CLAW, 8, shredFrame, burstPos, camera, 2.35 * Math.sqrt(hScale) * (0.85 + 0.30 * fastU), (int) (250 * (1.0 - 0.3 * fastU)), true);
            emitCrossRend(sp, burstPos, camera, 2.6 * hScale, fastU, 240);
        }

        // Violent upward blood fountain & splash burst
        emitBloodGeyser(sp, ground, 1.25 * Math.sqrt(hScale), 4.4 * Math.sqrt(hScale), u, 0.0, 250);
        emitBloodGeyser(sp, ground, 0.95 * Math.sqrt(hScale), 3.8 * Math.sqrt(hScale), Math.min(0.999, u * 1.15), Math.PI * 0.25, 220);
        emitBloodBurst(sp, burstPos, camera, 3.1 * hScale, u, 245, false);
        emitBloodCrater(sp, ground, 2.4 * hScale, t.start.seed() * 0.01, (int) (215 * fade));
        EffectSprites.shockwave(sp, ground.add(0, 0.05, 0), 3.2 * hScale * EffectSprites.easeOutCubic(u), u * 1.6, COL_SCARLET, (int) (235 * fade));

        // DNF Blood-Siphon Heal Stream: 4 glowing crimson ribbons curving violently back into the Berserker's chest!
        Vec3 casterChest = liveCasterPos(t.start.caster(), origin).add(0, 1.15, 0);
        double siphonU = Math.clamp(relAge / 520.0, 0.0, 1.0);
        if (siphonU < 1.0) {
            for (int i = 0; i < 4; i++) {
                double arcSide = (i - 1.5) * 0.85;
                double arcUp = 0.55 + (i % 2) * 0.75;
                Vec3 ctrl = burstPos.lerp(casterChest, 0.5).add(right.scale(arcSide)).add(0, arcUp, 0);
                List<Vec3> curve = new ArrayList<>(7);
                for (int s = 0; s <= 6; s++) {
                    double frac = Math.clamp(siphonU - s * 0.055, 0.0, 1.0);
                    curve.add(bezier(burstPos, ctrl, casterChest, frac));
                }
                Vec3 head = curve.getFirst();
                RibbonTrail.ribbon(sp, curve, camera, 0.16 * (1.0 - 0.4 * siphonU), i % 2 == 0 ? COL_SCARLET : COL_MAGMA, (int) (240 * (1.0 - siphonU * 0.6)));
                EffectSprites.glow(sp, head, camera, 0.48, COL_HOT, (int) (235 * (1.0 - siphonU * 0.5)));
            }
            if (siphonU > 0.65) {
                double absorb = (siphonU - 0.65) / 0.35;
                EffectSprites.glow(sp, casterChest, camera, 1.45 * Math.sin(absorb * Math.PI), COL_SCARLET, 220);
            }
        }
    }

    // --- 3B. 血魔巨爪 (Detached 10-Block Giant Gathering Blood Claw & Endpoint Crush) ---

    private static void renderGiantBloodClaw(SkillVfx.Track t, long age, long now, List<SpriteQuad> sp, Vec3 camera) {
        Vec3 origin = vector(t.start.origin());
        Vec3 dest = vector(t.start.position());
        Vec3 dir = horizontalDir(vector(t.start.direction()));
        Vec3 right = rightOf(dir);
        double scale = Math.max(1.0, t.start.horizontalScale());

        var releaseEvent = findPhase(t.flashes, RELEASE);
        if (releaseEvent == null) {
            double u = Math.clamp(age / 350.0, 0.0, 1.0);
            Vec3 groundPos = origin.lerp(dest, EffectSprites.easeOutCubic(u));
            Vec3 clawPos = groundPos.add(0, 1.25, 0);
            int frameIdx = Math.clamp((int) Math.floor(u * 4.99), 0, 4);
            double clawSize = (2.15 + 0.55 * Math.sin(u * Math.PI * 0.8)) * Math.sqrt(scale);

            emitFramedBillboard(sp, TEX_CLAW, 8, frameIdx, clawPos, camera, clawSize, 252, true);
            emitClawForwardPlane(sp, frameIdx, clawPos, dir, right, clawSize * 1.15, 235);
            emitBloodSigil(sp, groundPos.add(0, 0.07, 0), 2.8 * scale, now * 0.006, 220, true);
            emitBloodMist(sp, clawPos, camera, 2.2 * Math.sqrt(scale), now, 195, 0);

            RibbonTrail.push("blood_claw_l:" + t.start.cast(), clawPos.add(right.scale(-0.9 * scale)), 0.34, COL_SCARLET, 230, 260L, now);
            RibbonTrail.push("blood_claw_r:" + t.start.cast(), clawPos.add(right.scale(0.9 * scale)), 0.34, COL_MAGMA, 230, 260L, now);

            for (int i = 0; i < 4; i++) {
                double a = now * 0.014 + i * (Math.TAU / 4.0);
                double r = 2.2 * scale * (1.1 - 0.5 * u);
                Vec3 outer = clawPos.add(right.scale(Math.cos(a) * r)).add(0, Math.sin(a) * r * 0.6, 0).add(dir.scale(0.6));
                LightningBolt.bolt(sp, outer, clawPos, camera, (now / 45L) ^ (i * 61L), 5, 0.32, 0.065, COL_SCARLET, 225, false);
            }
            return;
        }

        long relAge = Math.max(0L, now - releaseEvent.at());
        if (relAge >= 720L) return;
        double u = Math.clamp(relAge / 720.0, 0.0, 0.999);
        double fastU = Math.clamp(relAge / 420.0, 0.0, 0.999);
        double fade = 1.0 - u;
        Vec3 ground = vector(releaseEvent.position()).add(0, 0.05, 0);
        Vec3 burstPos = ground.add(0, 1.15, 0);

        if (relAge < 440L) {
            int shredFrame = 5 + Math.clamp((int) Math.floor(fastU * 2.99), 0, 2);
            emitFramedBillboard(sp, TEX_CLAW, 8, shredFrame, burstPos, camera, 3.0 * Math.sqrt(scale) * (0.85 + 0.35 * fastU), (int) (255 * (1.0 - 0.28 * fastU)), true);
            emitCrossRend(sp, burstPos, camera, 3.6 * scale, fastU, 250);
        }

        emitBloodGeyser(sp, ground, 1.65 * Math.sqrt(scale), 5.4 * Math.sqrt(scale), u, 0.0, 255);
        emitBloodGeyser(sp, ground, 1.25 * Math.sqrt(scale), 4.6 * Math.sqrt(scale), Math.min(0.999, u * 1.15), Math.PI * 0.25, 230);
        emitBloodBurst(sp, burstPos, camera, 3.8 * scale, u, 250, false);
        emitBloodCrater(sp, ground, 3.1 * scale, t.start.seed() * 0.01, (int) (235 * fade));
        emitBloodSigil(sp, ground.add(0, 0.03, 0), 3.3 * scale, now * 0.004, (int) (220 * fade), true);
        EffectSprites.shockwave(sp, ground.add(0, 0.06, 0), 4.0 * scale * EffectSprites.easeOutCubic(u), u * 1.7, COL_SCARLET, (int) (245 * fade));

        Vec3 casterChest = liveCasterPos(t.start.caster(), origin).add(0, 1.15, 0);
        double siphonU = Math.clamp(relAge / 540.0, 0.0, 1.0);
        if (siphonU < 1.0) {
            for (int i = 0; i < 4; i++) {
                double arcSide = (i - 1.5) * 1.15;
                double arcUp = 0.75 + (i % 2) * 0.9;
                Vec3 ctrl = burstPos.lerp(casterChest, 0.5).add(right.scale(arcSide)).add(0, arcUp, 0);
                List<Vec3> curve = new ArrayList<>(8);
                for (int s = 0; s <= 7; s++) {
                    double frac = Math.clamp(siphonU - s * 0.05, 0.0, 1.0);
                    curve.add(bezier(burstPos, ctrl, casterChest, frac));
                }
                RibbonTrail.ribbon(sp, curve, camera, 0.20 * (1.0 - 0.35 * siphonU), i % 2 == 0 ? COL_SCARLET : COL_MAGMA, (int) (245 * (1.0 - siphonU * 0.55)));
                EffectSprites.glow(sp, curve.getFirst(), camera, 0.56, COL_HOT, (int) (240 * (1.0 - siphonU * 0.5)));
            }
        }
    }

    // --- 4. 怒气爆发 (Outrage Break: Shattered Crater + Roaring Blood-Geyser Waves) ---

    private static void renderBloodRage(SkillVfx.Track t, long age, long now, List<SpriteQuad> sp, Vec3 camera) {
        Vec3 p = vector(t.start.origin());
        double scale = Math.max(0.5, t.start.horizontalScale());
        double totalMs = Math.max(1280.0, t.start.duration() + 200.0);
        double totalU = Math.clamp(age / totalMs, 0.0, 1.0);
        double env = totalU < 0.75 ? 1.0 : Math.clamp((1.0 - totalU) / 0.25, 0.0, 1.0);
        double openScale = EffectSprites.easeOutCubic(Math.min(1.0, age / 200.0));

        // Full lava-blood fissure crater + rotating demonic blood seal underfoot!
        emitBloodCrater(sp, p.add(0, 0.04, 0), 4.45 * scale * openScale, t.start.seed() * 0.001, (int) (245 * env));
        emitBloodSigil(sp, p.add(0, 0.07, 0), 4.55 * scale * openScale, age * 0.0032, (int) (230 * env), true);

        // Swirling volcanic crimson mist enveloping the arena
        for (int i = 0; i < 6; i++) {
            double a = i * (Math.TAU / 6.0) + age * 0.0028;
            double r = (1.6 + (i % 2) * 1.45) * scale;
            double rise = ((age * 0.0018 + i * 0.21) % 1.0);
            emitBloodMist(sp, p.add(Math.cos(a) * r, 0.45 + rise * 2.2, Math.sin(a) * r), camera, 2.05 * Math.sqrt(scale), now + i * 170L, (int) (185 * env * Math.sin(rise * Math.PI)), i);
        }

        // Massive roaring central blood-fountain core pulsing continuously across all hits
        double coreCycle = (age % 320L) / 320.0;
        emitBloodGeyser(sp, p.add(0, 0.05, 0), 1.75 * Math.sqrt(scale), 5.4 * Math.sqrt(scale) * openScale, coreCycle, age * 0.004, (int) (245 * env));
        emitBloodGeyser(sp, p.add(0, 0.05, 0), 1.35 * Math.sqrt(scale), 4.6 * Math.sqrt(scale) * openScale, (coreCycle + 0.45) % 1.0, -age * 0.003 + 0.8, (int) (225 * env));

        // Each HIT flash erupts an explosive concentric ring of roaring blood geysers + waves!
        int waveIdx = 0;
        int columns = scale > 1.25 ? 10 : 8;
        for (var event : t.flashes) {
            if (event.phase() != HIT) continue;
            int idx = waveIdx++;
            long hitAge = now - event.at();
            if (hitAge < 0L || hitAge >= 640L) continue;
            double u = Math.clamp(hitAge / 640.0, 0.0, 0.999);
            double fade = 1.0 - u;

            // Ground shockwave + radial blood splash per surge
            double shockR = (1.2 + 3.4 * EffectSprites.easeOutCubic(u)) * scale;
            EffectSprites.shockwave(sp, p.add(0, 0.11 + (idx % 2) * 0.18, 0), shockR, u * 1.5 + idx, idx % 2 == 0 ? COL_SCARLET : COL_MAGMA, (int) (235 * fade));
            emitBloodBurst(sp, p.add(0, 0.30, 0), camera, 4.2 * scale, u, 235, true);

            SplittableRandom rng = new SplittableRandom(t.start.seed() ^ ((long) event.sequence() * 0x9E3779B97F4A7C15L));
            for (int k = 0; k < columns; k++) {
                double ang = k * (Math.TAU / columns) + idx * 0.42 + (rng.nextDouble() - 0.5) * 0.25;
                double dist = ((k % 2 == 0 ? 1.75 : 3.25) + (rng.nextDouble() - 0.5) * 0.65) * scale;
                double height = ((k % 2 == 0 ? 4.4 : 3.5) + rng.nextDouble() * 1.6) * Math.sqrt(scale);
                Vec3 gPos = p.add(Math.cos(ang) * dist, 0.04, Math.sin(ang) * dist);
                emitBloodGeyser(sp, gPos, (0.85 + rng.nextDouble() * 0.30) * Math.sqrt(scale), height, u, ang, 245);
                if (k < 4) {
                    emitBloodWave(sp, p.add(Math.cos(ang) * shockR * 0.85, 0.05, Math.sin(ang) * shockR * 0.85),
                        new Vec3(Math.cos(ang), 0, Math.sin(ang)), 1.75 * Math.sqrt(scale), 1.9 * Math.sqrt(scale), u, 225);
                }
            }

            // Upward shooting crimson blood-lightning bolts on peaks
            if (u < 0.55) {
                for (int b = 0; b < 3; b++) {
                    double ba = idx * 1.7 + b * (Math.TAU / 3.0);
                    Vec3 from = p.add(Math.cos(ba) * 2.2 * scale, 0.1, Math.sin(ba) * 2.2 * scale);
                    LightningBolt.bolt(sp, from, from.add(Math.cos(ba) * 0.7 * scale, 4.8 * Math.sqrt(scale), Math.sin(ba) * 0.7 * scale), camera, (now / 45L) ^ (idx * 31L + b), 7, 0.45, 0.08, COL_MAGMA, (int) (235 * (1.0 - u / 0.55)), false);
                }
            }
        }
    }

    // --- 5. 血气之刃 (Blood Sword Thrust / Assault Dash -> Single or Double Explosion) ---

    private static void renderBloodBlade(SkillVfx.Track t, long age, long now, List<Quad> quads, List<SpriteQuad> sp, Vec3 camera) {
        if (t.start.dual() && !t.start.sweep()) return;
        Vec3 origin = vector(t.start.origin());
        Vec3 dir = horizontalDir(vector(t.start.direction()));
        Vec3 chest = origin.add(0, 1.12, 0);
        double scale = Math.max(0.5, t.start.horizontalScale());

        if (t.start.sweep() && age < 260L) {
            // 血刃突袭: high-speed invulnerable crimson piercing dash!
            Vec3 live = liveCasterPos(t.start.caster(), origin);
            Vec3 liveChest = live.add(0, 1.12, 0);
            double u = Math.clamp(age / 240.0, 0.0, 1.0);
            sculptedBloodSword(quads, sp, liveChest.add(dir.scale(0.3)), dir, 3.2, 0.34, 0.40, now, 1.15, 250);
            EffectSprites.texturedBeam(sp, chest, liveChest.add(dir.scale(1.8)), camera, 0.62, now, true, COL_SCARLET, 240);
            LightningBolt.helixArcs(sp, chest, liveChest.add(dir.scale(1.6)), camera, now, 0.72, 3, COL_MAGMA, 235);
            RibbonTrail.push("blade_dash:" + t.start.cast(), liveChest.add(dir.scale(1.5)), 0.42, COL_SCARLET, 245, 260L, now);
            emitBloodSlash(sp, liveChest.add(dir.scale(1.4)), dir, 0, 2.8, u * 0.9, false, 240, true);
            EffectSprites.glow(sp, liveChest.add(dir.scale(2.0)), camera, 1.9, COL_HOT, 245);
        }

        boolean hasFinish = false;
        for (var event : t.flashes) {
            if (event.phase() != FINISH) continue;
            hasFinish = true;
            long impAge = Math.max(0L, now - event.at());
            if (impAge < 750L) {
                renderBladeExplosion(vector(event.position()), dir, chest, scale, impAge, now, t.start.seed() ^ event.sequence(), sp, camera);
            }
        }
        if (hasFinish || t.start.sweep()) return;

        if (t.last.phase() == FINISH) {
            long impAge = Math.max(0L, now - t.last.at());
            if (impAge < 750L) {
                renderBladeExplosion(vector(t.last.position()), dir, chest, scale, impAge, now, t.start.seed(), sp, camera);
            }
            return;
        }

        if (age < 200L) {
            // Phase A (0..200ms): Condensing blazing blood-blade at chest with imploding crimson ring!
            double u = Math.clamp(age / 200.0, 0.0, 1.0);
            Vec3 hilt = chest.add(dir.scale(0.35));
            double len = 1.4 + 1.8 * EffectSprites.easeOutCubic(u);
            sculptedBloodSword(quads, sp, hilt, dir, len, 0.22, 0.26, now, 0.75 + 0.25 * u, 245);
            EffectSprites.shockwave(sp, origin.add(0, 0.08, 0), 2.4 * (1.0 - 0.65 * u), -u * 3.0, COL_SCARLET, (int) (220 * u));
            EffectSprites.glow(sp, hilt.add(dir.scale(len * 0.5)), camera, 1.45 * u, COL_HOT, 235);
            emitBloodMist(sp, hilt.add(dir.scale(0.8)), camera, 1.5, now, 185, 0);
        } else {
            // Phase B (200..350ms): Violent 5-block piercing blood-blade thrust + helical storm!
            double u = Math.clamp((age - 200L) / 150.0, 0.0, 1.0);
            double thrustReach = 2.0 + 3.4 * EffectSprites.easeOutCubic(u);
            Vec3 hilt = chest.add(dir.scale(0.35 + 1.1 * u));
            Vec3 tip = chest.add(dir.scale(thrustReach));
            sculptedBloodSword(quads, sp, hilt, dir, Math.max(2.2, thrustReach - 0.35), 0.32, 0.38, now, 1.0, 250);

            EffectSprites.texturedBeam(sp, chest.add(dir.scale(0.4)), tip, camera, 0.56, now, true, COL_SCARLET, 245);
            LightningBolt.helixArcs(sp, chest.add(dir.scale(0.5)), tip, camera, now, 0.68, 3, COL_MAGMA, 235);
            emitBloodSlash(sp, chest.add(dir.scale(thrustReach * 0.55)), dir, 0, 2.5, u * 0.85, false, 235, true);
            emitBloodSlash(sp, chest.add(dir.scale(thrustReach * 0.55)), dir, 90, 2.5, u * 0.85, true, 235, true);
            EffectSprites.glow(sp, tip, camera, 1.85, COL_HOT, 245);
        }
    }

    // --- 5B. 飞血巨刃 (Detached 12-Block Flying Demonic Blood Greatsword & Scaled Detonations) ---

    private static void renderFlyingBloodBlade(SkillVfx.Track t, long age, long now, List<Quad> quads, List<SpriteQuad> sp, Vec3 camera) {
        Vec3 origin = vector(t.start.origin());
        Vec3 endpoint = vector(t.start.position());
        Vec3 dir = horizontalDir(vector(t.start.direction()));
        Vec3 chest = origin.add(0, 1.15, 0);
        double scale = Math.max(0.5, t.start.horizontalScale());

        double dist = Math.max(0.5, new Vec3(endpoint.x - origin.x, 0, endpoint.z - origin.z).length());
        long travelMs = Math.max(100L, Math.round(dist / 22.0 * 1000.0));
        if (age <= travelMs + 90L) {
            double u = Math.clamp(age / (double) travelMs, 0.0, 1.0);
            Vec3 curGround = origin.lerp(endpoint, u);
            Vec3 hilt = curGround.add(0, 1.15, 0).subtract(dir.scale(1.6));
            Vec3 tip = hilt.add(dir.scale(3.8));
            double fade = age <= travelMs ? 1.0 : Math.clamp(1.0 - (age - travelMs) / 90.0, 0.0, 1.0);
            int alpha = (int) Math.round(252 * fade);
            sculptedBloodSword(quads, sp, hilt, dir, 3.8, 0.36, 0.42, now, 1.15, alpha);
            EffectSprites.texturedBeam(sp, hilt, tip, camera, 0.62 * fade, now, true, COL_SCARLET, (int) (235 * fade));
            LightningBolt.helixArcs(sp, hilt, tip, camera, now, 0.78, 3, COL_MAGMA, (int) (235 * fade));
            RibbonTrail.push("fly_blade:" + t.start.cast(), tip, 0.44, COL_SCARLET, alpha, 280L, now);
            EffectSprites.glow(sp, tip, camera, 2.0 * fade, COL_HOT, alpha);
            emitBloodSlash(sp, hilt.add(dir.scale(1.8)), dir, (age * 0.45) % 180.0, 2.7, (age % 220L) / 220.0, false, (int) (220 * fade), true);
        }

        for (var event : t.flashes) {
            if (event.phase() != FINISH) continue;
            long impAge = Math.max(0L, now - event.at());
            if (impAge < 750L) {
                renderBladeExplosion(vector(event.position()), dir, chest, scale, impAge, now, t.start.seed() ^ event.sequence(), sp, camera);
            }
        }
    }

    private static void renderBladeExplosion(Vec3 target, Vec3 dir, Vec3 chest, double scale, long impAge, long now, long seed, List<SpriteQuad> sp, Vec3 camera) {
        double u = Math.clamp(impAge / 750.0, 0.0, 0.999);
        double fastU = Math.clamp(impAge / 450.0, 0.0, 0.999);
        double fade = 1.0 - u;
        Vec3 center = target.add(0, 1.10, 0);

        if (impAge < 300L) {
            double bf = 1.0 - impAge / 300.0;
            EffectSprites.texturedBeam(sp, chest, center, camera, 0.45 * bf, now, true, COL_SCARLET, (int) (220 * bf));
        }

        emitBloodCrater(sp, target.add(0, 0.05, 0), 3.45 * scale, seed * 0.002, (int) (240 * fade));
        emitBloodSigil(sp, target.add(0, 0.08, 0), 3.6 * scale * EffectSprites.easeOutCubic(Math.min(1.0, u * 2.5)), now * 0.003, (int) (215 * fade), true);

        if (impAge < 460L) {
            emitCrossRend(sp, center, camera, 4.4 * scale * (0.75 + 0.35 * fastU), fastU, 255);
            emitBloodSlash(sp, center, dir, 42, 3.6 * scale, fastU, false, 240, true);
            emitBloodSlash(sp, center, dir, -42, 3.6 * scale, fastU, true, 240, true);
        }
        emitBloodBurst(sp, center, camera, 4.3 * scale, u, 250, false);
        emitBloodBurst(sp, target.add(0, 0.25, 0), camera, 4.0 * scale, u, 235, true);

        emitBloodGeyser(sp, target.add(0, 0.04, 0), 1.45 * Math.sqrt(scale), 5.2 * Math.sqrt(scale), u, 0.2, 245);
        double waveR = (0.8 + 2.8 * EffectSprites.easeOutCubic(u)) * scale;
        for (int i = 0; i < 6; i++) {
            double a = i * (Math.TAU / 6.0);
            Vec3 ray = new Vec3(Math.cos(a), 0, Math.sin(a));
            emitBloodWave(sp, target.add(ray.scale(waveR)).add(0, 0.05, 0), ray, 1.55 * Math.sqrt(scale), 2.2 * Math.sqrt(scale), u, 230);
        }

        EffectSprites.shockwave(sp, target.add(0, 0.12, 0), 4.0 * scale * EffectSprites.easeOutCubic(u), u * 1.8, COL_SCARLET, (int) (245 * fade));
        EffectSprites.shockwave(sp, target.add(0, 0.85, 0), 3.2 * scale * EffectSprites.easeOutCubic(u), -u * 1.4, COL_MAGMA, (int) (215 * fade));
        if (u < 0.55) {
            for (int i = 0; i < 4; i++) {
                double a = i * Math.PI * 0.5 + 0.4;
                Vec3 end = center.add(Math.cos(a) * 3.4 * scale, (i % 2 == 0 ? 1.4 : -0.4), Math.sin(a) * 3.4 * scale);
                LightningBolt.bolt(sp, center, end, camera, (now / 45L) ^ (i * 83L), 6, 0.42, 0.08, COL_MAGMA, (int) (235 * (1.0 - u / 0.55)), true);
            }
        }
    }

    // --- 6. 崩山裂地斩 (大崩: Windup + Sky Leap + 6.5m Boiling Lava-Blood Crater + 4 Waves of Volcanic Blood Pillars) ---

    private static void renderBloodEruption(SkillVfx.Track t, long age, long now, List<Quad> quads, List<SpriteQuad> sp, Vec3 camera) {
        Vec3 dir = horizontalDir(vector(t.start.direction()));
        var mc = Minecraft.getInstance();
        Player caster = mc == null || mc.level == null ? null : mc.level.getPlayerByUUID(t.start.caster());

        var landing = findPhase(t.flashes, RELEASE);
        if (landing == null) {
            Vec3 pos = caster != null ? PlayerVisualAnchor.position(caster) : vector(t.last.position());
            var pose = caster == null ? null : Effects.VFX.playerPose(caster.getUUID(), now);
            Vec3 tip = caster != null ? PlayerBoneAnchor.weaponTip(caster, pose == null ? null : pose.frame(), 1.65) : pos.add(0, 2.0, 0);

            if (age < 250L) {
                // Phase A (0..250ms): Gathering massive dark-crimson blood aura into the held Blood Greatsword!
                double u = Math.clamp(age / 250.0, 0.0, 1.0);
                emitBloodSigil(sp, pos.add(0, 0.06, 0), 2.8 * (1.15 - 0.25 * u), -now * 0.006, (int) (230 * u), true);
                emitBloodMist(sp, pos.add(0, 1.0, 0), camera, 2.2, now, 200, 0);
                EffectSprites.glow(sp, tip, camera, 1.35 * u + 0.45, COL_MAGMA, 240);
                LightningBolt.helixArcs(sp, pos.add(0, 0.2, 0), tip, camera, now, 0.55, 2, COL_SCARLET, 225);
            } else {
                // Phase B (250..850ms): High 6m parabolic sky-cleaving leap with colossal blood-ribbon & afterimages!
                double jumpU = Math.clamp((age - 250L) / 600.0, 0.0, 1.0);
                RibbonTrail.push("eruption_tip:" + t.start.cast(), tip, 0.52, COL_SCARLET, 250, 360L, now);
                RibbonTrail.push("eruption_core:" + t.start.cast(), tip.lerp(pos.add(0, 1.2, 0), 0.35), 0.26, COL_MAGMA, 230, 290L, now);
                EffectSprites.glow(sp, tip, camera, 1.45, COL_HOT, 240);
                emitBloodMist(sp, pos.add(0, 0.9, 0), camera, 1.9, now, 185, 1);

                // 3 dramatic staggered leap afterimages
                int stage = LEAP_AFTERIMAGE_STAGE.getOrDefault(t.start.cast(), 0);
                if (caster != null && stage < 3 && (age - 250L) >= (stage + 1) * 145L) {
                    LEAP_AFTERIMAGE_STAGE.put(t.start.cast(), stage + 1);
                    PlayerAfterimages.capture(caster, stage == 2 ? COL_MAGMA : COL_SCARLET, 210, 420L, true);
                }

                // Overhead gigantic blood crescent tearing downward in final descent!
                if (jumpU > 0.45) {
                    double dropU = Math.clamp((jumpU - 0.45) / 0.55, 0.0, 0.999);
                    emitBloodSlash(sp, pos.add(0, 1.3, 0).add(dir.scale(1.0)), dir, 88, 3.8, dropU, false, 245, true);
                }
            }
            return;
        }

        // Phase C (RELEASE + HIT waves): Iconic DNF 大崩 Boiling Lava-Blood Lake & Volcanic Eruption Waves!
        Vec3 land = vector(landing.position());
        double scale = Math.max(0.5, t.start.horizontalScale());
        long landAge = Math.max(0L, now - landing.at());
        long lakeLife = Math.max(1450L, Math.max(t.start.duration(), 850L) - Math.max(0L, landing.at() - t.start.startedAt()) + 650L);
        if (landAge >= lakeLife) return;

        long fadeStart = Math.max(500L, lakeLife - 500L);
        double lakeFade = landAge < fadeStart ? 1.0 : Math.clamp(1.0 - (landAge - fadeStart) / 500.0, 0.0, 1.0);
        double openScale = EffectSprites.easeOutCubic(Math.min(1.0, landAge / 180.0));
        double craterPulse = 1.0 + 0.03 * Math.sin(now * 0.018);

        // Colossal boiling lava-blood ground crater + demonic seal scaled to actual range!
        emitBloodCrater(sp, land.add(0, 0.05, 0), 6.65 * scale * openScale * craterPulse, t.start.seed() * 0.001, (int) (255 * lakeFade));
        emitBloodSigil(sp, land.add(0, 0.08, 0), 6.45 * scale * openScale, now * 0.0016, (int) (215 * lakeFade), true);

        // Initial earth-shattering slam cleave & twin shockwaves (0..560ms)
        if (landAge < 560L) {
            double su = landAge / 560.0;
            double sf = 1.0 - su;
            emitBloodSlash(sp, land.add(0, 1.5, 0).add(dir.scale(1.2 * scale)), dir, 88, 5.4 * scale, su, false, 255, true);
            emitCrossRend(sp, land.add(0, 1.25, 0), camera, 5.8 * scale, su, 250);
            emitBloodBurst(sp, land.add(0, 0.45, 0), camera, 6.4 * scale, su, 250, true);
            EffectSprites.shockwave(sp, land.add(0, 0.14, 0), 6.9 * scale * EffectSprites.easeOutCubic(su), su * 1.4, COL_SCARLET, (int) (250 * sf));
            EffectSprites.shockwave(sp, land.add(0, 0.65, 0), 5.6 * scale * EffectSprites.easeOutCubic(su), -su * 1.2, COL_MAGMA, (int) (225 * sf));
            // 8 directional lava-blood waves blasting outward from the initial smash!
            for (int i = 0; i < 8; i++) {
                double a = i * (Math.TAU / 8.0);
                Vec3 ray = new Vec3(Math.cos(a), 0, Math.sin(a));
                emitBloodWave(sp, land.add(ray.scale((1.2 + 5.0 * EffectSprites.easeOutCubic(su)) * scale)).add(0, 0.06, 0), ray, 2.35 * Math.sqrt(scale), 2.9 * Math.sqrt(scale), su, 240);
            }
        }

        // Dense boiling volcanic blood-mist hovering over the entire lava-blood crater
        for (int i = 0; i < 8; i++) {
            double a = i * (Math.TAU / 8.0) + now * 0.0014;
            double r = (i % 2 == 0 ? 2.4 : 4.6) * scale;
            double rise = ((landAge * 0.0014 + i * 0.17) % 1.0);
            emitBloodMist(sp, land.add(Math.cos(a) * r, 0.55 + rise * 2.8, Math.sin(a) * r), camera, 2.55 * Math.sqrt(scale), now + i * 190L, (int) (195 * lakeFade * Math.sin(rise * Math.PI)), i);
        }

        // Staggered Waves of Monumental Roaring Volcanic Lava-Blood Geysers & Arcing Magma Clots!
        int waveNum = 0;
        for (var event : t.flashes) {
            if (event.phase() != HIT) continue;
            int wIdx = waveNum++;
            long hitAge = now - event.at();
            if (hitAge < 0L || hitAge >= 720L) continue;
            double u = Math.clamp(hitAge / 720.0, 0.0, 0.999);
            double fade = 1.0 - u;

            // Wave shock-pulse rippling across the crater
            EffectSprites.shockwave(sp, land.add(0, 0.12 + (wIdx % 2) * 0.2, 0), (2.2 + 4.4 * EffectSprites.easeOutCubic(u)) * scale, u * 1.4 + wIdx, COL_MAGMA, (int) (220 * fade));

            SplittableRandom rng = new SplittableRandom(t.start.seed() ^ ((long) event.sequence() * 0x9E3779B97F4A7C15L));
            // 11 towering roaring multi-plane lava-blood geyser columns per wave!
            for (int i = 0; i < 11; i++) {
                double ang = (i * (Math.TAU / 11.0)) + wIdx * 0.55 + (rng.nextDouble() - 0.5) * 0.35;
                double dist = (i == 0 ? rng.nextDouble() * 1.0 : (1.35 + Math.sqrt(rng.nextDouble()) * 4.45)) * scale;
                double width = ((i < 3 ? 1.35 : 0.95) + rng.nextDouble() * 0.35) * Math.sqrt(scale);
                double height = ((i < 3 ? 5.6 : 3.8) + rng.nextDouble() * 2.4) * Math.sqrt(scale);
                Vec3 gBase = land.add(Math.cos(ang) * dist, 0.05, Math.sin(ang) * dist);
                emitBloodGeyser(sp, gBase, width, height, u, ang, 250);

                // Splashing blood-burst crown at geyser apex on peak frames
                if (i < 5 && u > 0.14 && u < 0.85) {
                    double crownU = (u - 0.14) / 0.71;
                    emitBloodBurst(sp, gBase.add(0, height * 0.78, 0), camera, width * 2.1, crownU, 225, false);
                }
            }

            // 5 parabolic flying molten blood-magma arcs spraying out of the boiling crater!
            for (int m = 0; m < 5; m++) {
                double ma = wIdx * 1.3 + m * (Math.TAU / 5.0) + rng.nextDouble() * 0.4;
                double outR = (3.2 + rng.nextDouble() * 3.0) * scale;
                double apexY = (3.6 + rng.nextDouble() * 2.6) * Math.sqrt(scale);
                Vec3 startP = land.add(Math.cos(ma) * 0.8 * scale, 0.25, Math.sin(ma) * 0.8 * scale);
                Vec3 endP = land.add(Math.cos(ma) * outR, 0.15, Math.sin(ma) * outR);
                Vec3 ctrl = startP.lerp(endP, 0.5).add(0, apexY, 0);
                List<Vec3> trail = new ArrayList<>(6);
                for (int s = 0; s <= 5; s++) {
                    double f = Math.clamp(u - s * 0.055, 0.0, 1.0);
                    trail.add(bezier(startP, ctrl, endP, f));
                }
                RibbonTrail.ribbon(sp, trail, camera, 0.22 * (1.0 - 0.35 * u), m % 2 == 0 ? COL_MAGMA : COL_SCARLET, (int) (235 * fade));
                EffectSprites.glow(sp, trail.getFirst(), camera, 0.65 * fade, COL_HOT, (int) (235 * fade));
            }
        }
    }

    // --- 7. 魔狱血刹 (1st Awakening: Activation Burst + Cinematic Colossal Blood Greatsword Plunge, 5 Blood Tides & Overload Detonation!) ---

    private static void renderBloodSword(SkillVfx.Track t, long age, long now, List<Quad> quads, List<SpriteQuad> sp, Vec3 camera) {
        Vec3 casterOrigin = vector(t.start.origin());

        // Activation cast (!sweep): 1200ms demonic possession burst while the back sword manifests
        if (!t.start.sweep()) {
            if (age >= 1200L) return;
            Vec3 p = liveCasterPos(t.start.caster(), casterOrigin);
            double u = Math.clamp(age / 1200.0, 0.0, 0.999);
            double fade = 1.0 - u;
            emitBloodSigil(sp, p.add(0, 0.06, 0), 3.4 * EffectSprites.easeOutCubic(Math.min(1.0, u * 2.4)), now * 0.0028, (int) (240 * fade), true);
            EffectSprites.shockwave(sp, p.add(0, 0.10, 0), 3.8 * EffectSprites.easeOutCubic(u), u * 1.6, COL_SCARLET, (int) (235 * fade));
            emitBloodGeyser(sp, p.add(0, 0.04, 0), 1.45, 5.2, u, age * 0.004, (int) (230 * fade));
            LightningBolt.helixArcs(sp, p.add(0, 0.1, 0), p.add(0, 4.6, 0), camera, now, 1.15, 3, COL_MAGMA, (int) (235 * fade));
            emitBloodMist(sp, p.add(0, 1.3, 0), camera, 2.8, now, (int) (215 * fade), 0);
            return;
        }

        // Terminal Awakening Finisher (sweep == true): Center = origin + dir*3, Radius = 5.0
        Vec3 center = vector(t.start.position());
        Vec3 dir = horizontalDir(vector(t.start.direction()));
        double radius = StormChoreography.RADIUS; // 5.0
        double charge = Math.clamp(t.start.charge(), 0.0, 1.0); // blood / 20.0
        double swordScale = 1.0 + 0.28 * charge; // 0 stacks -> 7.4m tall; 20 stacks -> 9.5m sky-cleaving colossal blade!
        double swordLen = 7.4 * swordScale;
        double swordWidth = 0.88 * swordScale;

        var finishEvent = findPhase(t.flashes, FINISH);
        boolean overloaded = finishEvent != null;
        long overloadAge = overloaded ? Math.max(0L, now - finishEvent.at()) : 0L;
        if (overloaded && overloadAge >= 920L) return;

        double globalFade = overloaded ? Math.clamp(1.0 - overloadAge / 920.0, 0.0, 1.0) : 1.0;

        // Sky-high spinning demonic blood-abyss seal hovering overhead throughout the awakening!
        double skyAlpha = age < 250L ? (age / 250.0) : globalFade;
        emitBloodSigil(sp, center.add(0, 11.5, 0), (radius + 2.4) * (0.7 + 0.3 * Math.min(1.0, age / 300.0)), -now * 0.0022, (int) (215 * skyAlpha), true);

        // --- Phase 1: Colossal Blood Greatsword Plunging from Heaven (0..300ms) ---
        if (age < 300L) {
            double p = Math.clamp(age / 300.0, 0.0, 1.0);
            double easeFall = p * p * p; // accelerating gravity slam!
            double tipY = 10.5 * (1.0 - easeFall) + 0.05;
            Vec3 swordTip = center.add(0, tipY, 0);
            Vec3 swordHilt = swordTip.add(0, swordLen, 0);

            // Ground warning seal expanding violently before impact
            emitBloodSigil(sp, center.add(0, 0.07, 0), radius * (0.45 + 0.65 * p), now * 0.006, 240, true);
            EffectSprites.shockwave(sp, center.add(0, 0.10, 0), radius * (1.15 - 0.75 * p), -p * 3.0, COL_MAGMA, 235);

            // Colossal 3D demonic greatsword plunging point-down into the earth!
            sculptedBloodSword(quads, sp, swordHilt, new Vec3(0, -1, 0), swordLen, swordWidth, 0.72 * swordScale, now, 1.0, 255);
            // Blazing sky-piercing fall pillar & crackling helix blood-lightning
            EffectSprites.texturedBeam(sp, swordHilt.add(0, 4.5, 0), swordTip, camera, 1.05 * swordScale, now, true, COL_SCARLET, 240);
            LightningBolt.helixArcs(sp, swordHilt.add(0, 3.0, 0), swordTip, camera, now, 1.55, 3, COL_MAGMA, 245);
            RibbonTrail.ribbon(sp, List.of(swordTip, swordTip.add(0, 4.5, 0), swordTip.add(0, 10.5, 0)), camera, 1.15, COL_SCARLET, 230);
            return;
        }

        // --- Phase 2 & 3: Embedded Colossal Sword + Shattered Blood Abyss + 5 Surging Blood-Tides (300..1800ms) ---
        double slamU = Math.clamp((age - 300L) / 550.0, 0.0, 0.999);
        double quake = overloaded ? Math.sin(now * 0.09) * 0.16 : Math.sin(now * 0.045) * 0.06;

        // Colossal 6.2m shattered lava-blood crater + double counter-rotating demonic blood seals!
        emitBloodCrater(sp, center.add(0, 0.05, 0), (radius + 1.45), t.start.seed() * 0.001, (int) (255 * globalFade));
        emitBloodSigil(sp, center.add(0, 0.08, 0), (radius + 1.15), now * 0.0022, (int) (235 * globalFade), true);
        emitBloodSigil(sp, center.add(0, 0.12, 0), (radius * 0.68), -now * 0.0036, (int) (215 * globalFade), false);

        // Initial colossal slam explosion at 300ms
        if (age < 850L) {
            double sf = 1.0 - slamU;
            emitCrossRend(sp, center.add(0, 1.6, 0), camera, 6.8, slamU, 255);
            emitBloodBurst(sp, center.add(0, 0.5, 0), camera, 6.6, slamU, 250, true);
            EffectSprites.shockwave(sp, center.add(0, 0.15, 0), (radius + 1.8) * EffectSprites.easeOutCubic(slamU), slamU * 1.6, COL_SCARLET, (int) (250 * sf));
            EffectSprites.shockwave(sp, center.add(0, 0.9, 0), (radius + 1.2) * EffectSprites.easeOutCubic(slamU), -slamU * 1.3, COL_MAGMA, (int) (225 * sf));
        }

        // Planted colossal 3D demonic greatsword vibrating fiercely at center (dissolves cleanly after overload shatter!)
        if (!overloaded || overloadAge < 260L) {
            double swordFade = overloaded ? (1.0 - overloadAge / 260.0) : 1.0;
            Vec3 embeddedHilt = center.add(quake, swordLen * 0.86, -quake);
            sculptedBloodSword(quads, sp, embeddedHilt, new Vec3(0, -1, 0), swordLen, swordWidth * (overloaded ? 1.18 : 1.0), 0.72 * swordScale, now, overloaded ? 1.35 : 1.0, (int) (255 * swordFade));
            EffectSprites.glow(sp, center.add(0, 2.2, 0), camera, 2.8 * swordScale * swordFade, overloaded ? COL_HOT : COL_SCARLET, (int) (235 * swordFade));
            LightningBolt.helixArcs(sp, center.add(0, 0.2, 0), embeddedHilt, camera, now, 1.35 * swordScale, 3, overloaded ? COL_HOT : COL_MAGMA, (int) (235 * swordFade));
        }

        // 4 roaring black-crimson vortex ribbons spiraling continuously around the planted sword!
        double vortexSpin = now * 0.0058;
        for (int strand = 0; strand < 4; strand++) {
            List<Vec3> pts = new ArrayList<>(18);
            double baseA = vortexSpin + strand * (Math.TAU / 4.0);
            for (int seg = 0; seg <= 16; seg++) {
                double f = seg / 16.0;
                double ang = baseA + f * Math.PI * 2.8;
                double rad = (1.35 + f * (radius - 0.65));
                double y = 0.25 + f * 6.8;
                pts.add(center.add(Math.cos(ang) * rad, y, Math.sin(ang) * rad));
            }
            RibbonTrail.ribbon(sp, pts, camera, 0.42, strand % 2 == 0 ? COL_SCARLET : COL_CRIMSON, (int) (225 * globalFade));
        }

        // Dense swirling abyssal blood-mist storm clouds around the perimeter
        for (int i = 0; i < 8; i++) {
            double a = i * (Math.TAU / 8.0) - now * 0.0018;
            double r = 2.6 + (i % 2) * 2.1;
            double y = 0.8 + ((now * 0.0016 + i * 0.23) % 1.0) * 3.8;
            emitBloodMist(sp, center.add(Math.cos(a) * r, y, Math.sin(a) * r), camera, 2.8, now + i * 150L, (int) (205 * globalFade), i);
        }

        // 5 Violent Blood-Tide Surges (each HIT & FINISH flash at 600, 900, 1200, 1500, 1800ms)!
        int surgeIdx = 0;
        for (var event : t.flashes) {
            if (event.phase() != HIT && event.phase() != FINISH) continue;
            int idx = surgeIdx++;
            long sAge = now - event.at();
            if (sAge < 0L || sAge >= 650L) continue;
            double u = Math.clamp(sAge / 650.0, 0.0, 0.999);
            double sf = 1.0 - u;
            double tideR = 1.0 + (radius + 0.6) * EffectSprites.easeOutCubic(u);

            // 6-way radial surging blood-wave tides sweeping outward!
            for (int k = 0; k < 6; k++) {
                double a = k * (Math.TAU / 6.0) + idx * 0.52;
                Vec3 ray = new Vec3(Math.cos(a), 0, Math.sin(a));
                emitBloodWave(sp, center.add(ray.scale(tideR)).add(0, 0.06, 0), ray, 2.35, 3.0, u, 245);
                // Roaring perimeter blood-geyser pillars erupting in the tide's wake!
                emitBloodGeyser(sp, center.add(ray.scale(tideR * 0.72)).add(0, 0.05, 0), 1.15, 5.2 + (k % 2) * 1.4, u, a, 245);
            }

            // Sweeping giant crimson cross-slashes tearing across the arena on every surge!
            double slashYaw = idx * 1.25;
            Vec3 slashDir = new Vec3(Math.cos(slashYaw), 0, Math.sin(slashYaw));
            emitBloodSlash(sp, center.add(0, 1.45 + (idx % 3) * 0.45, 0), slashDir, (idx % 2 == 0 ? 18 : -18), radius * 1.18, u, idx % 2 == 1, 250, true);
            EffectSprites.shockwave(sp, center.add(0, 0.14 + idx * 0.12, 0), tideR * 1.05, u * 1.7 + idx, idx % 2 == 0 ? COL_SCARLET : COL_MAGMA, (int) (240 * sf));

            // Crackling crimson blood-lightning discharging from the giant sword to the perimeter!
            for (int b = 0; b < 4; b++) {
                double ba = idx * 0.9 + b * Math.PI * 0.5;
                Vec3 rimPt = center.add(Math.cos(ba) * radius, 0.4 + (b % 2) * 1.4, Math.sin(ba) * radius);
                LightningBolt.bolt(sp, center.add(0, 2.2, 0), rimPt, camera, (now / 45L) ^ (idx * 71L + b * 19L), 7, 0.55, 0.095, COL_MAGMA, (int) (245 * sf), true);
            }
        }

        // --- Phase 4: Colossal Sword Overload Cataclysmic Detonation (FINISH + 0..920ms)! ---
        if (overloaded) {
            double fu = Math.clamp(overloadAge / 920.0, 0.0, 0.999);
            double ff = 1.0 - fu;
            // Sky-piercing monumental central blood-pillar blast!
            emitBloodGeyser(sp, center.add(0, 0.05, 0), 2.85, 11.5 * swordScale, fu, 0.0, 255);
            emitBloodGeyser(sp, center.add(0, 0.05, 0), 2.25, 10.2 * swordScale, Math.min(0.999, fu * 1.15), Math.PI * 0.25, 240);
            EffectSprites.texturedBeam(sp, center.add(0, 0.1, 0), center.add(0, 14.0 * ff + 2.0, 0), camera, 1.65 * ff + 0.25, now, true, COL_SCARLET, (int) (245 * ff));

            // Blinding colossal cross-rend & multi-plane blood explosion!
            emitCrossRend(sp, center.add(0, 2.2, 0), camera, 8.8 * (0.75 + 0.35 * fu), Math.min(0.999, fu * 1.35), 255);
            emitBloodBurst(sp, center.add(0, 1.8, 0), camera, 8.5, fu, 255, false);
            emitBloodBurst(sp, center.add(0, 0.35, 0), camera, 8.2, fu, 245, true);

            // Triple stacked cataclysmic shockwave rings!
            double expR = (radius + 2.8) * EffectSprites.easeOutCubic(fu);
            EffectSprites.shockwave(sp, center.add(0, 0.16, 0), expR, fu * 1.9, COL_SCARLET, (int) (255 * ff));
            EffectSprites.shockwave(sp, center.add(0, 1.35, 0), expR * 0.88, -fu * 1.5, COL_MAGMA, (int) (235 * ff));
            EffectSprites.shockwave(sp, center.add(0, 2.75, 0), expR * 0.72, fu * 2.2, COL_HOT, (int) (210 * ff));
            EffectSprites.glow(sp, center.add(0, 2.4, 0), camera, 6.2 * ff, COL_HOT, (int) (245 * ff));
        }
    }

    // --- 魔狱血刹 Cinematic 3rd-Person Camera --------------------------------

    private static SkillVfx.CameraPose swordFinisherCamera(SkillVfx.Track t, long time) {
        var mc = Minecraft.getInstance();
        if (t == null || mc == null || mc.level == null) return null;
        long age = Math.max(0L, time - t.start.startedAt());
        double push = Math.clamp(age / 1800.0, 0.0, 1.0);
        Vec3 origin = vector(t.start.origin());
        Vec3 dir = horizontalDir(vector(t.start.direction()));
        Vec3 side = rightOf(dir);

        // Frame both the Berserker and the full 5-block hellblood arena ahead with subtle dramatic orbit & zoom
        Vec3 target = origin.add(dir.scale(2.8)).add(0, 2.1, 0);
        double backDist = 9.4 - 1.1 * Math.sin(push * Math.PI * 0.5);
        double sideDist = 4.6 + 1.0 * push;
        double height = 5.8 - 0.5 * push;
        Vec3 camPos = origin.subtract(dir.scale(backDist)).add(side.scale(sideDist)).add(0, height, 0);

        if (mc.player != null) {
            var wall = mc.level.clip(new ClipContext(target, camPos, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
            if (wall.getType() != HitResult.Type.MISS) {
                camPos = wall.getLocation().add(target.subtract(camPos).normalize().scale(0.30));
            }
        }
        Vec3 look = target.subtract(camPos);
        float yaw = (float) Math.toDegrees(Math.atan2(-look.x, look.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(look.y, look.horizontalDistance()));
        return new SkillVfx.CameraPose(camPos, yaw, pitch);
    }

    // --- Sculpted 3D Demonic Jagged Blood Greatsword (Geometry + Animated Aura Sheath) ---

    /**
     * Builds a beveled, multi-faceted demonic serrated blood greatsword along `axis` (hilt -> tip)
     * with horned upward-swept blood fangs (no iron crossguard!) and wraps it in dual cross-planes
     * of animated `blood_sword_aura.png` flames for full 3D volumetric demonic weight from every angle.
     */
    static void sculptedBloodSword(
        List<Quad> quads, List<SpriteQuad> sp,
        Vec3 hiltBase, Vec3 axisDir, double length, double halfWidth, double thickness,
        long timeMs, double intensity, int alpha
    ) {
        if (alpha <= 4 || length <= 0.1 || axisDir.lengthSqr() < 1e-6) return;
        Vec3 axis = axisDir.normalize();
        Vec3 ref = Math.abs(axis.y) > 0.92 ? new Vec3(1, 0, 0) : UP;
        Vec3 widthVec = axis.cross(ref).normalize();
        Vec3 thickVec = axis.cross(widthVec).normalize();

        int solidAlpha = Math.clamp(alpha, 0, 255);
        int glowAlpha = Math.clamp((int) Math.round(alpha * Math.min(1.0, intensity)), 0, 255);
        int edgeCol = intensity > 1.1 ? COL_HOT : COL_SCARLET;
        int bodyCol = intensity > 1.1 ? COL_MAGMA : COL_CRIMSON;

        // 1. Sculpted 3D Beveled Serrated Blade Core (6 longitudinal segments -> razor spear tip!)
        // Profile normalized along [0..1]: grip (0..0.16), horned fangs (0.16..0.28), serrated blade (0.28..1.0)
        double[] stations = {0.16, 0.28, 0.44, 0.60, 0.76, 0.90, 1.00};
        double[] widths   = {0.42, 1.48, 0.96, 1.12, 0.86, 0.56, 0.00};
        double[] depths   = {0.45, 0.95, 0.82, 0.72, 0.58, 0.36, 0.00};

        // Condensed dark-blood grip shaft (0.0 .. 0.18)
        Vec3 g0 = hiltBase;
        Vec3 g1 = hiltBase.add(axis.scale(length * 0.18));
        emitDiamondSegment(quads, g0, g1, widthVec, thickVec, halfWidth * 0.26, thickness * 0.26, halfWidth * 0.38, thickness * 0.38, COL_DARK_BLOOD, COL_CRIMSON, solidAlpha);

        // Beveled diamond-section serrated demonic blade segments
        for (int i = 0; i < stations.length - 1; i++) {
            Vec3 p0 = hiltBase.add(axis.scale(length * stations[i]));
            Vec3 p1 = hiltBase.add(axis.scale(length * stations[i + 1]));
            double w0 = halfWidth * widths[i];
            double w1 = halfWidth * widths[i + 1];
            double d0 = thickness * depths[i] * 0.5;
            double d1 = thickness * depths[i + 1] * 0.5;
            int faceCol = (i % 2 == 0) ? bodyCol : COL_DARK_BLOOD;
            emitDiamondSegment(quads, p0, p1, widthVec, thickVec, w0, d0, w1, d1, faceCol, edgeCol, solidAlpha);
        }

        // Upward-swept horned demonic blood-fang wings flaring off the collar (NO iron guard!)
        Vec3 collar = hiltBase.add(axis.scale(length * 0.22));
        for (int sign : new int[]{-1, 1}) {
            Vec3 fangInner = collar.add(widthVec.scale(sign * halfWidth * 0.55));
            Vec3 fangOuter = collar.add(widthVec.scale(sign * halfWidth * 1.95)).add(axis.scale(length * 0.13));
            Vec3 fangBase = hiltBase.add(axis.scale(length * 0.16)).add(widthVec.scale(sign * halfWidth * 0.35));
            Vec3 ridge = collar.add(widthVec.scale(sign * halfWidth * 0.95)).add(axis.scale(length * 0.05));
            Vec3 dOff = thickVec.scale(thickness * 0.34);
            quads.add(new Quad(fangBase, ridge.add(dOff), fangOuter, fangInner, edgeCol, solidAlpha));
            quads.add(new Quad(fangInner, fangOuter, ridge.subtract(dOff), fangBase, COL_DARK_BLOOD, solidAlpha));
        }

        // 2. Dual Cross-Planes of Animated Demonic `blood_sword_aura.png` (4 looping flame frames!)
        int frame = (int) Math.floorMod(timeMs / 75L, 4L);
        float u0 = frame / 4.0f;
        float u1 = (frame + 1) / 4.0f;
        Vec3 center = hiltBase.add(axis.scale(length * 0.50));
        // v=0 is blade tip (+axis), v=1 is hilt (-axis) -> halfV = -axis * (length * 0.54)
        Vec3 halfV = axis.scale(-length * 0.54);
        Vec3 halfU1 = widthVec.scale(halfWidth * 2.55);
        Vec3 halfU2 = thickVec.scale(halfWidth * 2.25);

        // Translucent dark-blood flame mass + additive blazing crimson aura on both crossed planes
        EffectSprites.emitPlane(sp, TEX_SWORD_AURA, Blend.TRANSLUCENT, center, halfU1, halfV, u0, 0f, u1, 1f, COL_WHITE, solidAlpha);
        EffectSprites.emitPlane(sp, TEX_SWORD_AURA, Blend.ADDITIVE, center, halfU1.scale(1.06), halfV.scale(1.02), u0, 0f, u1, 1f, intensity > 1.1 ? COL_HOT : COL_WHITE, (int) (glowAlpha * 0.85));
        EffectSprites.emitPlane(sp, TEX_SWORD_AURA, Blend.TRANSLUCENT, center, halfU2, halfV, u0, 0f, u1, 1f, COL_WHITE, (int) (solidAlpha * 0.82));
        EffectSprites.emitPlane(sp, TEX_SWORD_AURA, Blend.ADDITIVE, center, halfU2.scale(1.06), halfV.scale(1.02), u0, 0f, u1, 1f, COL_SCARLET, (int) (glowAlpha * 0.72));
    }

    private static void emitDiamondSegment(
        List<Quad> out, Vec3 p0, Vec3 p1, Vec3 widthVec, Vec3 thickVec,
        double w0, double d0, double w1, double d1,
        int spineColor, int edgeColor, int alpha
    ) {
        Vec3 aL = p0.subtract(widthVec.scale(w0)), aR = p0.add(widthVec.scale(w0));
        Vec3 aF = p0.add(thickVec.scale(d0)),      aB = p0.subtract(thickVec.scale(d0));
        Vec3 bL = p1.subtract(widthVec.scale(w1)), bR = p1.add(widthVec.scale(w1));
        Vec3 bF = p1.add(thickVec.scale(d1)),      bB = p1.subtract(thickVec.scale(d1));

        // 4 beveled diamond blade facets (alternating dark spine & bright razor edge contrast)
        out.add(new Quad(aF, aR, bR, bF, edgeColor, alpha));
        out.add(new Quad(aR, aB, bB, bR, spineColor, alpha));
        out.add(new Quad(aB, aL, bL, bB, edgeColor, alpha));
        out.add(new Quad(aL, aF, bF, bL, spineColor, alpha));
    }

    // --- Dedicated Dual-Layer Sprite Sheet Emitters --------------------------

    /**
     * Wide brutal blood-slash crescent facing FORWARD along `forward`, tilted by `rollDeg`.
     * Dual-layer: thick dark-crimson translucent body + blazing additive scarlet highlight!
     */
    private static void emitBloodSlash(
        List<SpriteQuad> out, Vec3 center, Vec3 forward, double rollDeg,
        double radius, double progress, boolean mirror, int alpha, boolean additiveBoost
    ) {
        if (progress < 0.0 || progress >= 1.0 || radius <= 0.0 || alpha <= 0) return;
        Vec3 dir = horizontalDir(forward);
        Vec3 right0 = rightOf(dir);
        Vec3 up0 = UP;
        double roll = Math.toRadians(rollDeg);
        Vec3 side = right0.scale(Math.cos(roll)).add(up0.scale(Math.sin(roll))).normalize();
        if (mirror) side = side.scale(-1.0);

        double scale = radius * (0.86 + 0.24 * EffectSprites.easeOutCubic(progress));
        int frame = Math.clamp((int) (progress * 8), 0, 7);
        float u0 = frame / 8.0f;
        float u1 = (frame + 1) / 8.0f;
        int a = Math.clamp((int) Math.round(alpha * (1.0 - 0.35 * progress * progress)), 0, 255);

        // In blood_slash.png, peak arc faces +qx (+halfU), sweeping from -qy to +qy (-halfV -> +halfV)
        Vec3 halfU = dir.scale(scale);
        Vec3 halfV = side.scale(scale);
        EffectSprites.emitPlane(out, TEX_SLASH, Blend.TRANSLUCENT, center, halfU, halfV, u0, 0f, u1, 1f, COL_WHITE, a);
        if (additiveBoost) {
            EffectSprites.emitPlane(out, TEX_SLASH, Blend.ADDITIVE, center, halfU.scale(1.03), halfV.scale(1.03), u0, 0f, u1, 1f, COL_SCARLET, (int) (a * 0.78));
        }
    }

    /** Roaring 3-cross-plane volumetric blood/magma geyser anchored flush to ground (`base`). */
    private static void emitBloodGeyser(
        List<SpriteQuad> out, Vec3 base, double halfWidth, double height,
        double progress, double yawOffset, int alpha
    ) {
        if (progress < 0.0 || progress >= 1.0 || halfWidth <= 0.0 || height <= 0.0 || alpha <= 0) return;
        int frame = Math.clamp((int) (progress * 8), 0, 7);
        float u0 = frame / 8.0f;
        float u1 = (frame + 1) / 8.0f;
        Vec3 center = base.add(0, height * 0.5, 0);
        // v=0 is sky top (+Y), v=1 is ground base (-Y) -> halfV points DOWN
        Vec3 halfV = new Vec3(0, -height * 0.5, 0);
        int a = Math.clamp(alpha, 0, 255);

        // 3 axial planes at 0, 60, 120 deg look thick and volcanic from EVERY angle!
        for (int i = 0; i < 3; i++) {
            double ang = yawOffset + i * (Math.PI / 3.0);
            Vec3 halfU = new Vec3(Math.cos(ang) * halfWidth, 0, Math.sin(ang) * halfWidth);
            EffectSprites.emitPlane(out, TEX_GEYSER, Blend.TRANSLUCENT, center, halfU, halfV, u0, 0f, u1, 1f, COL_WHITE, a);
            if (i < 2) {
                EffectSprites.emitPlane(out, TEX_GEYSER, Blend.ADDITIVE, center, halfU.scale(0.92), halfV, u0, 0f, u1, 1f, COL_WHITE, (int) (a * 0.65));
            }
        }
    }

    /** Surging directional blood-wave crest wall anchored to ground, facing `forward`. */
    private static void emitBloodWave(
        List<SpriteQuad> out, Vec3 groundPos, Vec3 forward,
        double halfWidth, double height, double progress, int alpha
    ) {
        if (progress < 0.0 || progress >= 1.0 || halfWidth <= 0.0 || height <= 0.0 || alpha <= 0) return;
        Vec3 dir = horizontalDir(forward);
        Vec3 side = rightOf(dir);
        int frame = Math.clamp((int) (progress * 8), 0, 7);
        float u0 = frame / 8.0f;
        float u1 = (frame + 1) / 8.0f;

        Vec3 center = groundPos.add(0, height * 0.46, 0);
        // Lean crest slightly forward in travel direction for aggressive surging motion!
        Vec3 halfU = side.scale(halfWidth);
        Vec3 halfV = new Vec3(0, -height * 0.5, 0).subtract(dir.scale(height * 0.14));
        int a = Math.clamp((int) Math.round(alpha * (1.0 - 0.35 * progress * progress)), 0, 255);

        EffectSprites.emitPlane(out, TEX_WAVE, Blend.TRANSLUCENT, center, halfU, halfV, u0, 0f, u1, 1f, COL_WHITE, a);
        EffectSprites.emitPlane(out, TEX_WAVE, Blend.ADDITIVE, center.add(dir.scale(0.05)), halfU.scale(1.04), halfV, u0, 0f, u1, 1f, COL_SCARLET, (int) (a * 0.70));
        // Secondary side-profile longitudinal wave plane so wave stays thick even when viewed from the side
        EffectSprites.emitPlane(out, TEX_WAVE, Blend.TRANSLUCENT, center.subtract(dir.scale(halfWidth * 0.25)), dir.scale(halfWidth * 0.85), halfV.scale(0.90), u0, 0f, u1, 1f, COL_WHITE, (int) (a * 0.72));
    }

    /** Shattered obsidian + boiling crimson magma-blood crater ground decal (translucent rock + additive magma glow). */
    private static void emitBloodCrater(List<SpriteQuad> out, Vec3 center, double radius, double rotRad, int alpha) {
        if (radius <= 0.0 || alpha <= 0) return;
        int a = Math.clamp(alpha, 0, 255);
        EffectSprites.emitGround(out, TEX_CRATER, Blend.TRANSLUCENT, center, radius, rotRad, 0f, 0f, 1f, 1f, COL_WHITE, a);
        EffectSprites.emitGround(out, TEX_CRATER, Blend.ADDITIVE, center.add(0, 0.015, 0), radius * 0.96, rotRad, 0f, 0f, 1f, 1f, COL_MAGMA, (int) (a * 0.48));
    }

    /** Rotating demonic Hellblood Demon thorny blood-rune seal on ground. */
    private static void emitBloodSigil(List<SpriteQuad> out, Vec3 center, double radius, double rotRad, int alpha, boolean doubleLayer) {
        if (radius <= 0.0 || alpha <= 0) return;
        int a = Math.clamp(alpha, 0, 255);
        if (doubleLayer) {
            EffectSprites.emitGround(out, TEX_SIGIL, Blend.TRANSLUCENT, center, radius * 1.03, -rotRad * 0.45, 0f, 0f, 1f, 1f, COL_WHITE, (int) (a * 0.85));
        }
        EffectSprites.emitGround(out, TEX_SIGIL, Blend.ADDITIVE, center.add(0, 0.015, 0), radius, rotRad, 0f, 0f, 1f, 1f, COL_SCARLET, (int) (a * 0.82));
    }

    /** Visceral asymmetrical blood-splatter detonation burst (flat ground or camera billboard). */
    private static void emitBloodBurst(List<SpriteQuad> out, Vec3 center, Vec3 camera, double radius, double progress, int alpha, boolean groundFlat) {
        if (progress < 0.0 || progress >= 1.0 || radius <= 0.0 || alpha <= 0) return;
        int frame = Math.clamp((int) (progress * 8), 0, 7);
        float u0 = frame / 8.0f;
        float u1 = (frame + 1) / 8.0f;
        double scale = radius * (0.62 + 0.54 * EffectSprites.easeOutCubic(progress));
        int a = Math.clamp((int) Math.round(alpha * (1.0 - 0.40 * progress * progress)), 0, 255);

        if (groundFlat) {
            EffectSprites.emitGround(out, TEX_BURST, Blend.TRANSLUCENT, center, scale, progress * 0.55, u0, 0f, u1, 1f, COL_WHITE, a);
            EffectSprites.emitGround(out, TEX_BURST, Blend.ADDITIVE, center.add(0, 0.02, 0), scale * 0.95, progress * 0.55, u0, 0f, u1, 1f, COL_SCARLET, (int) (a * 0.74));
        } else {
            emitFramedBillboard(out, TEX_BURST, 8, frame, center, camera, scale, a, true);
        }
    }

    /** Blinding X-shaped crimson blood cross-rend billboard flash. */
    private static void emitCrossRend(List<SpriteQuad> out, Vec3 center, Vec3 camera, double radius, double progress, int alpha) {
        if (progress < 0.0 || progress >= 1.0 || radius <= 0.0 || alpha <= 0) return;
        int frame = Math.clamp((int) (progress * 8), 0, 7);
        double scale = radius * (0.78 + 0.34 * EffectSprites.easeOutCubic(progress));
        int a = Math.clamp((int) Math.round(alpha * (1.0 - 0.35 * progress * progress)), 0, 255);
        emitFramedBillboard(out, TEX_CROSS_REND, 8, frame, center, camera, scale, a, true);
    }

    /** Churning 4-frame demonic dark-crimson blood mist cloud billboard. */
    private static void emitBloodMist(List<SpriteQuad> out, Vec3 center, Vec3 camera, double radius, long timeMs, int alpha, int seedOffset) {
        if (radius <= 0.0 || alpha <= 4) return;
        int frame = (int) Math.floorMod((timeMs / 110L) + seedOffset, 4L);
        emitFramedBillboard(out, TEX_MIST, 4, frame, center, camera, radius, Math.clamp(alpha, 0, 255), false);
    }

    /** Horizontal/angled forward-reaching claw plane so 嗜魂之手 looks truly 3D even from the side or top! */
    private static void emitClawForwardPlane(List<SpriteQuad> out, int frame, Vec3 center, Vec3 dir, Vec3 right, double scale, int alpha) {
        float u0 = frame / 8.0f;
        float u1 = (frame + 1) / 8.0f;
        // v=0 is talon tips (+dir), v=1 is wrist (-dir) -> halfV = -dir * scale
        Vec3 halfU = right.scale(scale);
        Vec3 halfV = dir.scale(-scale);
        EffectSprites.emitPlane(out, TEX_CLAW, Blend.TRANSLUCENT, center, halfU, halfV, u0, 0f, u1, 1f, COL_WHITE, alpha);
        EffectSprites.emitPlane(out, TEX_CLAW, Blend.ADDITIVE, center.add(0, 0.03, 0), halfU.scale(1.03), halfV.scale(1.03), u0, 0f, u1, 1f, COL_SCARLET, (int) (alpha * 0.72));
    }

    /** Upright camera-facing billboard for multi-frame horizontal strips (ensures v=0 stays at top!). */
    private static void emitFramedBillboard(
        List<SpriteQuad> out, Identifier texture, int totalFrames, int frame,
        Vec3 center, Vec3 camera, double radius, int alpha, boolean additiveBoost
    ) {
        if (radius <= 0.0 || alpha <= 2) return;
        float u0 = frame / (float) totalFrames;
        float u1 = (frame + 1) / (float) totalFrames;
        Vec3 look = camera.subtract(center);
        if (look.lengthSqr() < 1e-6) look = new Vec3(0, 0, 1);
        else look = look.normalize();
        Vec3 right = look.cross(Math.abs(look.y) > 0.95 ? new Vec3(1, 0, 0) : UP).normalize();
        Vec3 up = right.cross(look).normalize();
        // emitPlane places v0 at center - halfV -> pass halfV = -up * radius so image top (v=0) is UP!
        Vec3 halfU = right.scale(radius);
        Vec3 halfV = up.scale(-radius);

        EffectSprites.emitPlane(out, texture, Blend.TRANSLUCENT, center, halfU, halfV, u0, 0f, u1, 1f, COL_WHITE, alpha);
        if (additiveBoost) {
            EffectSprites.emitPlane(out, texture, Blend.ADDITIVE, center.add(look.scale(0.03)), halfU.scale(1.04), halfV.scale(1.04), u0, 0f, u1, 1f, COL_SCARLET, (int) (alpha * 0.72));
        }
    }

    // --- Pure Math & Lookup Utilities ----------------------------------------

    private static SkillVisualEvent findPhase(List<SkillVisualEvent> flashes, SkillVisualEvent.Phase phase) {
        for (var e : flashes) {
            if (e.phase() == phase) return e;
        }
        return null;
    }

    private static Vec3 liveCasterPos(UUID casterId, Vec3 fallback) {
        var mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) return fallback;
        Player p = mc.level.getPlayerByUUID(casterId);
        return p != null && p.isAlive() ? PlayerVisualAnchor.position(p) : fallback;
    }

    private static boolean isLocalCaster(UUID casterId) {
        var mc = Minecraft.getInstance();
        return mc != null && mc.player != null && mc.player.getUUID().equals(casterId);
    }

    private static boolean isOwnFirstPerson(UUID casterId) {
        var mc = Minecraft.getInstance();
        return mc != null && mc.player != null && mc.player.getUUID().equals(casterId) && mc.options.getCameraType().isFirstPerson();
    }

    private static double hpFraction(Player player) {
        var mc = Minecraft.getInstance();
        if (mc != null && mc.player != null && player.getUUID().equals(mc.player.getUUID())) {
            var own = SkillsClient.current().own();
            if (own != null) return Math.clamp(SkillsClient.current().predicted(own), 0.0, 1.0);
        }
        return Math.clamp(player.getHealth() / Math.max(1.0f, player.getMaxHealth()), 0.0, 1.0);
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

    private static Vec3 bezier(Vec3 a, Vec3 ctrl, Vec3 b, double t) {
        double u = 1.0 - t;
        return a.scale(u * u).add(ctrl.scale(2.0 * u * t)).add(b.scale(t * t));
    }
}
