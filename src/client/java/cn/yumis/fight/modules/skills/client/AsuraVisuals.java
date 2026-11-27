package cn.yumis.fight.modules.skills.client;

import cn.yumis.fight.modules.presentation.api.client.CameraShake;
import cn.yumis.fight.modules.presentation.api.client.EffectSprites;
import cn.yumis.fight.modules.presentation.api.client.EffectSprites.Blend;
import cn.yumis.fight.modules.presentation.api.client.EffectSprites.SpriteQuad;
import cn.yumis.fight.modules.presentation.api.client.LightningBolt;
import cn.yumis.fight.modules.presentation.api.client.RibbonTrail;
import cn.yumis.fight.modules.presentation.api.client.WorldEffects;
import cn.yumis.fight.modules.presentation.api.client.WorldQuads.Quad;
import cn.yumis.fight.modules.skills.api.AsuraRules;
import cn.yumis.fight.modules.skills.api.CareerRules;
import cn.yumis.fight.modules.skills.api.CareerSkills;
import cn.yumis.fight.modules.skills.api.SkillResourceRules;
import cn.yumis.fight.modules.skills.api.SkillVisualEvent;
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
 * Dedicated DNF-inspired Asura / Indra / Dark Sky Wave Eye (阿修罗 / 大暗黑天) VFX renderer.
 * Zero legacy untextured WorldQuads: 100% custom high-contrast multi-tone pixel-sharp +
 * soft-feathered sprite sheets (source: tools/build_asura_effect_textures.py), dual-layer
 * translucent silhouette + additive core rendering, persistent orbiting Wave Brands,
 * brand-scaled Ghost Orbs, X-Crescent Shura waves, lingering shattering Ice Spike trails,
 * capture-vortex Fire Wave orbs with towering DNF lava pillar eruptions, Agni Pentacle
 * converging flame beads with blazing Lotus finisher, full Dark Sky Wave Eye abyssal domain +
 * floating golden-pupil eye + Wings of Light / Piercing Wheel / Flashing Spear variants +
 * awakening finisher 3rd-person camera.
 * Pure client presentation: never grants combat authority, hitboxes, or physics changes.
 */
final class AsuraVisuals {
    private static final Vec3 UP = new Vec3(0, 1, 0);

    // Custom pre-baked multi-tone Asura sprite sheets (source: tools/build_asura_effect_textures.py)
    static final Identifier TEX_BRAND_RUNE = tex("wave_brand_rune.png");
    static final Identifier TEX_GHOST_ORB = tex("ghost_orb_core.png");
    static final Identifier TEX_CRESCENT_WAVE = tex("spirit_crescent_wave.png");
    static final Identifier TEX_ICE_SPIKE = tex("ice_wave_spike.png");
    static final Identifier TEX_ICE_FROST = tex("ice_frost_trail.png");
    static final Identifier TEX_FIRE_ORB = tex("fire_wave_orb.png");
    static final Identifier TEX_FIRE_PILLAR = tex("fire_pillar_eruption.png");
    static final Identifier TEX_FIRE_CRATER = tex("fire_scorch_crater.png");
    static final Identifier TEX_AGNI_MANDALA = tex("agni_mandala.png");
    static final Identifier TEX_AGNI_ORB = tex("agni_flame_orb.png");
    static final Identifier TEX_AGNI_LOTUS = tex("agni_lotus_burst.png");
    static final Identifier TEX_EYE_IRIS = tex("wave_eye_iris.png");
    static final Identifier TEX_EYE_DOMAIN = tex("wave_eye_domain.png");
    static final Identifier TEX_VOID_MIST = tex("dark_void_mist.png");
    static final Identifier TEX_EYE_GEAR = tex("wave_eye_gear.png");
    static final Identifier TEX_EYE_WING = tex("wave_eye_wing.png");
    static final Identifier TEX_EYE_SPEAR = tex("wave_eye_spear.png");

    // Classic DNF Asura palette accents for additive boosts, ribbons, bolts, and shockwaves
    private static final int COL_WHITE = 0xFFFFFF;
    private static final int COL_GOLD = 0xF5C249;
    private static final int COL_GOLD_HOT = 0xFFF3BF;
    private static final int COL_ORB_PURPLE = 0xA86BFF;
    private static final int COL_ORB_CYAN = 0x9CF0FF;
    private static final int COL_CRESCENT_GOLD = 0xF2E29C;
    private static final int COL_CRESCENT_SHURA = 0xB8FAFF;
    private static final int COL_ICE_MAIN = 0x6FD8FF;
    private static final int COL_ICE_BRIGHT = 0xDFF8FF;
    private static final int COL_FIRE_MAIN = 0xFF7E24;
    private static final int COL_FIRE_HOT = 0xFFDA78;
    private static final int COL_AGNI_GOLD = 0xFFB838;
    private static final int COL_EYE_DARK = 0x260D4A;
    private static final int COL_EYE_VIOLET = 0xB675FF;
    private static final int COL_EYE_LILAC = 0xE2C4FF;
    private static final int COL_EYE_GOLD = 0xFFD452;
    private static final int COL_WING_CYAN = 0xC6E6FF;

    private static final List<String> ASURA_SKILLS = List.of(
        CareerSkills.BRANDS,
        CareerSkills.ORB,
        CareerSkills.CRESCENT,
        CareerSkills.ICE,
        CareerSkills.FIRE,
        CareerSkills.AGNI,
        CareerSkills.EYE
    );

    // Pure-client transient visual state (never authoritative)
    private record IceNode(Vec3 pos, Vec3 dir, long spawnedAt, double yaw, double scale, boolean eyeVariant, long seed) {}

    private static final long ICE_NODE_LIFE_MS = 520L;
    private static final int MAX_ICE_NODES = 320;

    private static final Map<UUID, Integer> LAST_BRANDS = new HashMap<>();
    private static final Map<UUID, Long> BRAND_GAIN_FLASH_AT = new HashMap<>();
    private static final Map<UUID, Long> EYE_OPENED_AT = new HashMap<>();
    private static final Set<UUID> CRESCENT_FULL_CHIMED = new HashSet<>();
    private static final Map<UUID, Long> ORB_LAST_BEAT = new HashMap<>();
    private static final Map<UUID, Vec3> LAST_ICE_PLANTED = new HashMap<>();
    private static final Map<UUID, Long> LAST_EYE_WING_AUDIO = new HashMap<>();
    private static final Map<UUID, Long> LAST_AGNI_HIT_AUDIO = new HashMap<>();
    private static final List<IceNode> ICE_NODES = new ArrayList<>();

    private AsuraVisuals() {}

    private static Identifier tex(String name) {
        return Identifier.fromNamespaceAndPath("yumis_dungeon_and_fight", "textures/effect/asura/" + name);
    }

    static void clear() {
        LAST_BRANDS.clear();
        BRAND_GAIN_FLASH_AT.clear();
        EYE_OPENED_AT.clear();
        CRESCENT_FULL_CHIMED.clear();
        ORB_LAST_BEAT.clear();
        LAST_ICE_PLANTED.clear();
        LAST_EYE_WING_AUDIO.clear();
        LAST_AGNI_HIT_AUDIO.clear();
        ICE_NODES.clear();
    }

    static void install() {
        for (String id : ASURA_SKILLS) {
            var content = CareerSkills.content(id);
            boolean isEye = CareerSkills.EYE.equals(id);
            SkillVisualRegistry.register(id, new SkillVisualRegistry.Handler() {
                @Override public boolean persistentEnd(SkillVisualEvent event) {
                    return isPersistentEnd(id, event);
                }

                @Override public boolean refreshable() {
                    return CareerSkills.ORB.equals(id);
                }

                @Override public boolean cinematic(SkillVisualEvent event) {
                    return isCinematic(id, event);
                }

                @Override public boolean cinematicFromFirstPerson() {
                    return isEye;
                }

                @Override public boolean restoreCamera() {
                    return isEye;
                }

                @Override public CameraType cameraType(SkillVfx.Track track, long time) {
                    return CameraType.THIRD_PERSON_BACK;
                }

                @Override public SkillVfx.CameraPose camera(SkillVfx.Track track, long time) {
                    return isEye && track.start.sweep() ? eyeFinisherCamera(track, time) : null;
                }

                @Override public int defaultColor() {
                    return content.color();
                }

                @Override public long defaultDuration() {
                    return (long) (content.skill().parameters(1).getOrDefault("duration", 1d) * 1000);
                }

                @Override public long finishedUntil(SkillVfx.Track track, SkillVisualEvent event) {
                    return computeFinishedUntil(id, track, event);
                }

                @Override public void play(SkillVfx.Track track, SkillVisualEvent event) {
                    AsuraVisuals.playCue(id, track, event);
                }

                @Override public void render(SkillVfx.LevelRenderContext ctx, SkillVfx.Track track, long age, long now, List<Quad> quads, Vec3 camera) {
                    AsuraVisuals.renderSkill(id, track, age, now, ctx.sprites(), camera);
                }
            });
        }

        // 暗天波动眼普攻旋刃车轮 (Wave Eye Basic Gear Projectile)
        SkillVisualRegistry.register(AsuraRules.GEAR_VISUAL, new SkillVisualRegistry.Handler() {
            @Override public long defaultDuration() {
                return 4000L;
            }

            @Override public int defaultColor() {
                return CareerRules.EYE_COLOR;
            }

            @Override public long finishedUntil(SkillVfx.Track track, SkillVisualEvent event) {
                return event.at() + 260L;
            }

            @Override public void play(SkillVfx.Track track, SkillVisualEvent event) {
                if (event.phase() == START) {
                    SkillSound.playAt(event.position(), SoundEvents.BREEZE_DEFLECT, 0.55f, 1.20f);
                    SkillSound.playAt(event.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 0.42f, 1.38f);
                }
            }

            @Override public void render(SkillVfx.LevelRenderContext ctx, SkillVfx.Track track, long age, long now, List<Quad> quads, Vec3 camera) {
                renderEyeGear(track, age, now, ctx.sprites(), camera);
            }
        });
    }

    static boolean isPersistentEnd(String id, SkillVisualEvent event) {
        return CareerSkills.persistent(id) && !event.sweep();
    }

    static boolean isCinematic(String id, SkillVisualEvent event) {
        return CareerSkills.EYE.equals(id) && event.sweep();
    }

    static long computeFinishedUntil(String id, SkillVfx.Track track, SkillVisualEvent event) {
        if (CareerSkills.FIRE.equals(id)) return event.at() + 920L;
        if (CareerSkills.AGNI.equals(id)) return event.at() + 850L;
        if (CareerSkills.EYE.equals(id)) {
            return event.at() + Math.max(780L, Math.round(780.0 / Math.max(0.01, track.speed)) + 80L);
        }
        if (CareerSkills.ORB.equals(id)) return event.at() + 450L;
        return event.at() + 550L;
    }

    // --- Persistent Actor Visuals: Orbiting Wave Brands, Heavy Dark Sky Wave Eye Domain, Lingering Ice Spikes ---

    static void actors(List<SpriteQuad> sp, Vec3 camera, long serverNow, long renderNow) {
        // 1. Render lingering shattering Ice Wave / Wave Eye Piercing Wheel ground spikes
        renderLingeringIceNodes(sp, camera, renderNow);

        var mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) return;

        for (var actor : CareerClient.INSTANCE.view.actors) {
            boolean hasBrands = actor.brands > 0;
            boolean hasEye = actor.eyeUntil > serverNow;

            UUID ownerId;
            try {
                ownerId = UUID.fromString(actor.owner);
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            if (!hasBrands) LAST_BRANDS.remove(ownerId);
            if (!hasEye) EYE_OPENED_AT.remove(ownerId);
            if (!hasBrands && !hasEye) continue;

            Player p = mc.level.getPlayerByUUID(ownerId);
            if (p == null || !p.isAlive()) continue;

            Vec3 base = PlayerVisualAnchor.position(p);
            Vec3 forward = horizontalDir(PlayerVisualAnchor.forward(p));
            Vec3 right = rightOf(forward);
            boolean ownFp = isOwnFirstPerson(ownerId);

            // 2. 波动刻印 (Wave Manifestation Brands: supports 1..24 body-orbiting golden rune beads)
            if (hasBrands) {
                int brands = Math.clamp(actor.brands, 1, SkillResourceRules.AGNI_MAX_ORBS);
                Integer prev = LAST_BRANDS.put(ownerId, brands);
                if (prev != null && brands > prev) {
                    BRAND_GAIN_FLASH_AT.put(ownerId, renderNow);
                    WorldEffects.playShockwave(base.add(0, 1.05, 0), 0.35, 1.65, COL_GOLD, 210, 280L);
                    SkillSound.playAt(base.x, base.y + 1.1, base.z, SoundEvents.AMETHYST_BLOCK_CHIME, 0.45f, 1.15f + Math.min(8, brands) * 0.08f);
                }

                long sinceGain = renderNow - BRAND_GAIN_FLASH_AT.getOrDefault(ownerId, 0L);
                double gainFlash = sinceGain >= 0 && sinceGain < 380L ? (1.0 - sinceGain / 380.0) : 0.0;
                double orbitR = ownFp ? 1.08 : (brands > 5 ? 0.92 : 0.78);
                double baseH = ownFp ? 1.05 : 1.18;
                double alphaScale = ownFp ? 0.38 : 1.0;
                double sizeFactor = brands > 6 ? Math.max(0.75, 1.0 - (brands - 6) * 0.035) : 1.0;
                int brandFrame = (int) Math.floorMod(renderNow / 140L, 4L);
                Vec3 orbitCenter = base.subtract(forward.scale(ownFp ? 0.32 : 0.16)).add(0, baseH, 0);

                Vec3[] orbPositions = new Vec3[brands];
                for (int i = 0; i < brands; i++) {
                    double ang = renderNow * 0.0022 + i * (Math.TAU / brands);
                    double bob = Math.sin(renderNow * 0.0045 + i * 1.3) * 0.12;
                    Vec3 pos = orbitCenter.add(Math.cos(ang) * orbitR, bob, Math.sin(ang) * orbitR);
                    orbPositions[i] = pos;

                    boolean isNewest = i == brands - 1 && gainFlash > 0.02;
                    double rad = (brands >= 5 ? 0.25 : 0.22) * sizeFactor + (isNewest ? 0.11 * gainFlash : 0.0);
                    int a = (int) Math.round((isNewest ? 250 : 225) * alphaScale);
                    emitFramedBillboard(sp, TEX_BRAND_RUNE, 4, (brandFrame + i) & 3, pos, camera, rad, COL_WHITE, COL_GOLD, a, 0.72);
                    EffectSprites.glow(sp, pos, camera, rad * 1.45, isNewest ? COL_GOLD_HOT : COL_GOLD, (int) Math.round(140 * alphaScale));
                }

                // 5+ brand sacred resonance halo & golden connecting threads
                if (brands >= 5 && !ownFp) {
                    double pulse = 0.5 + 0.5 * Math.sin(renderNow * 0.007);
                    emitGroundSprite(sp, TEX_AGNI_MANDALA, base.add(0, 0.04, 0), 1.15 + 0.08 * pulse, renderNow * 0.0014, COL_GOLD, COL_GOLD_HOT, (int) (105 + 35 * pulse), 0.48);
                    long arcTick = renderNow / 130L;
                    int idxA = (int) Math.floorMod(arcTick, brands);
                    int idxB = (idxA + 1) % brands;
                    LightningBolt.bolt(sp, orbPositions[idxA], orbPositions[idxB], camera, arcTick ^ ownerId.getMostSignificantBits(), 4, 0.14, 0.028, COL_GOLD_HOT, 165, false);
                }
            }

            // 3. 暗天波动眼 20 秒重度暗黑覆地结界 + 杀意波动 8 格脉动 + 悬浮鎏金竖瞳巨眼
            if (hasEye) {
                long openedAt = EYE_OPENED_AT.computeIfAbsent(ownerId, k -> renderNow);
                long openAge = Math.max(0L, renderNow - openedAt);
                double openEase = Math.clamp(openAge / 420.0, 0.0, 1.0);
                double pulse = 0.5 + 0.5 * Math.sin(renderNow * 0.006);

                // (a) Heavy 16-block Abyssal Dark Sky Ground Shroud (dual counter-rotating dark violet domain!)
                double domainR = 16.0 * (0.35 + 0.65 * EffectSprites.easeOutCubic(openEase));
                EffectSprites.emitGround(sp, TEX_EYE_DOMAIN, Blend.TRANSLUCENT, base.add(0, 0.04, 0), domainR, renderNow * 0.00028, 0f, 0f, 1f, 1f, COL_WHITE, 232);
                EffectSprites.emitGround(sp, TEX_EYE_DOMAIN, Blend.TRANSLUCENT, base.add(0, 0.052, 0), domainR * 0.96, -renderNow * 0.00042, 0f, 0f, 1f, 1f, COL_EYE_DARK, 155);
                EffectSprites.emitGround(sp, TEX_EYE_DOMAIN, Blend.ADDITIVE, base.add(0, 0.062, 0), domainR, renderNow * 0.00035, 0f, 0f, 1f, 1f, COL_EYE_VIOLET, (int) (118 + 24 * pulse));

                // (b) Heavy drifting dark-purple Void Mist clouds enveloping the 16-block dark domain!
                int mistFrame = (int) Math.floorMod(renderNow / 135L, 4L);
                for (int i = 0; i < 10; i++) {
                    double ang = i * (Math.TAU / 10.0) + renderNow * (i % 2 == 0 ? 0.00055 : -0.00045);
                    double dist = (i % 2 == 0 ? 6.5 : 12.2) + 1.4 * Math.sin(renderNow * 0.0012 + i);
                    double mh = 0.85 + 0.95 * (0.5 + 0.5 * Math.sin(renderNow * 0.0018 + i * 1.7));
                    Vec3 mp = base.add(Math.cos(ang) * dist, mh, Math.sin(ang) * dist);
                    emitFramedBillboard(sp, TEX_VOID_MIST, 4, (mistFrame + i) & 3, mp, camera, 2.9 + (i % 3) * 0.55, COL_WHITE, COL_EYE_VIOLET, 168, 0.22);
                }
                // Swirling dark mist around the Asura's own silhouette (toned down in own 1st person)
                if (!ownFp) {
                    for (int i = 0; i < 3; i++) {
                        double ang = renderNow * 0.0024 + i * (Math.TAU / 3.0);
                        Vec3 mp = base.add(Math.cos(ang) * 0.85, 0.65 + 0.45 * Math.sin(ang * 2.0), Math.sin(ang) * 0.85);
                        emitFramedBillboard(sp, TEX_VOID_MIST, 4, (mistFrame + i) & 3, mp, camera, 0.92, COL_WHITE, COL_EYE_VIOLET, 150, 0.30);
                    }
                }

                // (c) 杀意波动 (Murderous Wave): crisp 1s expanding 8-block purple-violet pulse ring
                double wavePulse = Math.floorMod(serverNow - actor.eyeUntil, 1000L) / 1000.0;
                double waveR = AsuraRules.AURA_RADIUS * EffectSprites.easeOutCubic(wavePulse);
                int waveAlpha = (int) Math.round(215 * (1.0 - wavePulse));
                if (waveAlpha > 6) {
                    EffectSprites.shockwave(sp, base.add(0, 0.085, 0), waveR, wavePulse * 1.4, COL_EYE_VIOLET, waveAlpha);
                    EffectSprites.shockwave(sp, base.add(0, 0.14, 0), waveR * 0.92, -wavePulse * 1.1, COL_EYE_LILAC, (int) (waveAlpha * 0.65));
                }

                // (d) Towering Floating Dark Sky Wave Eye (大暗黑天巨眼) suspended behind/above the Asura!
                int eyeFrame = openAge < 110L ? 0 : openAge < 220L ? 1 : openAge < 340L ? 2 : (3 + (int) Math.floorMod(renderNow / 240L, 3L));
                Vec3 eyePos = ownFp
                    ? base.subtract(forward.scale(0.95)).add(0, 3.25 + 0.08 * pulse, 0)
                    : base.subtract(forward.scale(0.45)).add(0, 2.75 + 0.12 * pulse, 0);
                double eyeScale = ownFp ? 1.15 : 1.48 + 0.06 * pulse;
                int eyeAlpha = ownFp ? 115 : 242;

                // Subtle rotating arcane purple halo behind the floating eye
                if (!ownFp) {
                    double spin = renderNow * 0.0018;
                    double c = Math.cos(spin) * (eyeScale * 0.88);
                    double s = Math.sin(spin) * (eyeScale * 0.88);
                    Vec3 hu = right.scale(c).add(UP.scale(s));
                    Vec3 hv = UP.scale(c).subtract(right.scale(s));
                    EffectSprites.emitPlane(sp, TEX_EYE_DOMAIN, Blend.ADDITIVE, eyePos.subtract(forward.scale(0.06)), hu, hv, 0f, 0f, 1f, 1f, COL_EYE_VIOLET, 95);
                }
                emitFramedBillboard(sp, TEX_EYE_IRIS, 8, eyeFrame, eyePos, camera, eyeScale, COL_WHITE, COL_EYE_LILAC, eyeAlpha, 0.50);
                EffectSprites.glow(sp, eyePos, camera, eyeScale * 1.05, COL_EYE_GOLD, (int) (85 * (ownFp ? 0.35 : 1.0)));

                // Occasional menacing violet crackle snapping off the suspended Wave Eye
                if (!ownFp && Math.floorMod(renderNow / 140L, 3L) == 0) {
                    long boltSeed = (renderNow / 70L) ^ ownerId.getMostSignificantBits();
                    SplittableRandom rng = new SplittableRandom(boltSeed);
                    double a0 = rng.nextDouble() * Math.TAU;
                    Vec3 tip = eyePos.add(right.scale(Math.cos(a0) * 1.65)).add(0, Math.sin(a0) * 0.85, 0);
                    LightningBolt.bolt(sp, eyePos, tip, camera, boltSeed, 5, 0.24, 0.042, COL_EYE_LILAC, 185, false);
                }
            }
        }
    }

    // --- Wave Eye Transformed Shared Skills (地裂波动剑 -> 光翼, 变身普攻兜底) ---

    static boolean isEyeWave(SkillVisualEvent event) {
        return event != null && event.color() == CareerRules.EYE_COLOR;
    }

    static void playEyeLightWingAudio(SkillVisualEvent e) {
        if (e.phase() == START || e.phase() == RELEASE) {
            long prev = LAST_EYE_WING_AUDIO.getOrDefault(e.caster(), 0L);
            if (e.at() - prev < 80L) return;
            LAST_EYE_WING_AUDIO.put(e.caster(), e.at());
            SkillSound.playAt(e.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 0.78f, 1.15f);
            SkillSound.playAt(e.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 0.65f, 1.35f);
            SkillSound.playAt(e.position(), SoundEvents.BREEZE_SHOOT, 0.62f, 1.10f);
            CameraShake.impulse(vector(e.position()), 14, 0.18f, 55L, 115L);
        }
    }

    /** Wave Eye transformed Earth Wave -> Wings of Light (暗天波动眼·光翼). */
    static void renderEyeLightWing(SkillVfx.Track t, long now, List<SpriteQuad> sp, Vec3 camera) {
        SkillVisualEvent start = t.start;
        Vec3 dir = horizontalDir(vector(t.last.direction()));
        Vec3 right = rightOf(dir);
        Vec3 pos = vector(t.last.position());
        double hScale = Math.max(0.75, start.horizontalScale());
        int frame = (int) Math.floorMod(now / 50L, 8L);
        double wingR = 2.30 * hScale;

        Vec3 ground = pos.add(0, 0.14, 0);
        // Primary wide sweeping spectral light-wings skimming the earth
        emitDirectionalWavePlane(sp, TEX_EYE_WING, frame, ground.add(0, 0.18, 0), dir, 0.0, wingR, COL_WHITE, COL_WING_CYAN, 248, 0.85);
        // Twin angled 3D wing blades fanning slightly upward so the wings look dramatic from every angle!
        emitDirectionalWavePlane(sp, TEX_EYE_WING, (frame + 2) & 7, ground.add(0, 0.42, 0), dir, 26.0, wingR * 0.88, COL_WHITE, COL_EYE_VIOLET, 215, 0.72);
        emitDirectionalWavePlane(sp, TEX_EYE_WING, (frame + 5) & 7, ground.add(0, 0.42, 0), dir, -26.0, wingR * 0.88, COL_WHITE, COL_EYE_VIOLET, 215, 0.72);

        // Luminous wingtip ribbons & violet shock wake
        Vec3 leftTip = ground.add(right.scale(-wingR * 0.62)).add(0, 0.30, 0);
        Vec3 rightTip = ground.add(right.scale(wingR * 0.62)).add(0, 0.30, 0);
        RibbonTrail.push("eyewing_l:" + start.cast(), leftTip, 0.24, COL_EYE_VIOLET, 215, 220L, now);
        RibbonTrail.push("eyewing_r:" + start.cast(), rightTip, 0.24, COL_WING_CYAN, 215, 220L, now);
        EffectSprites.shockwave(sp, ground, wingR * 0.62, now * 0.008, COL_EYE_LILAC, 175);
        LightningBolt.bolt(sp, leftTip, rightTip, camera, (now / 55L) ^ start.seed(), 6, 0.32, 0.045, COL_WING_CYAN, 190, false);
    }

    /** Clean fallback if any melee ATTACK carries EYE_COLOR (zero legacy boxes). */
    static void renderEyeBasic(SkillVfx.Track track, long age, List<SpriteQuad> sp, Vec3 camera) {
        if (track.start.color() != CareerRules.EYE_COLOR || !"ATTACK".equals(track.start.skill())) return;
        var sample = BuiltinSkillVisuals.basicSlashSample(age, 360L);
        if (sample == null) return;
        Vec3 p = vector(track.last.position()).add(0, 1.12, 0);
        Vec3 d = horizontalDir(vector(track.start.direction()));
        int a = (int) Math.round(235 * sample.alphaScale());
        if (a > 4) {
            EffectSprites.slashArc(sp, p.add(d.scale(0.8)), d, 0, 3.0, sample.progress(), COL_EYE_VIOLET, a);
            EffectSprites.glow(sp, p.add(d.scale(1.2)), camera, 0.78, COL_EYE_GOLD, (int) (a * 0.75));
        }
    }

    // --- Layered Audio, Afterimages & Camera Shake Choreography --------------

    private static void playCue(String id, SkillVfx.Track track, SkillVisualEvent e) {
        Vec3 pos = vector(e.position());
        var mc = Minecraft.getInstance();
        Player actor = mc == null || mc.level == null ? null : mc.level.getPlayerByUUID(e.caster());
        boolean ownFp = isOwnFirstPerson(e.caster());
        boolean eyeMode = e.color() == CareerRules.EYE_COLOR;

        switch (id) {
            case CareerSkills.BRANDS -> {
                if (e.phase() == START) {
                    SkillSound.playAt(e.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 0.68f, 1.25f);
                    SkillSound.playAt(e.position(), SoundEvents.BEACON_POWER_SELECT, 0.45f, 1.45f);
                }
            }
            case CareerSkills.ORB -> {
                if (e.phase() == START || e.phase() == RELEASE) {
                    SkillSound.playAt(e.position(), SoundEvents.BREEZE_SHOOT, 0.68f, 0.92f);
                    SkillSound.playAt(e.position(), SoundEvents.TRIDENT_THUNDER.value(), 0.36f, 1.48f);
                    CameraShake.impulse(pos, 12, 0.14f, 45L, 95L);
                } else if (e.phase() == FINISH) {
                    SkillSound.playAt(e.position(), SoundEvents.TRIDENT_THUNDER.value(), 0.48f, 1.32f);
                    SkillSound.playAt(e.position(), SoundEvents.AMETHYST_CLUSTER_BREAK, 0.55f, 1.05f);
                    CameraShake.impulse(pos, 12, 0.16f, 50L, 100L);
                }
            }
            case CareerSkills.CRESCENT -> {
                if (e.phase() == START) {
                    CRESCENT_FULL_CHIMED.remove(e.cast());
                    SkillSound.playAt(e.position(), SoundEvents.ENCHANTMENT_TABLE_USE, 0.45f, 1.35f);
                } else if (e.phase() == RELEASE) {
                    CRESCENT_FULL_CHIMED.remove(e.cast());
                    boolean full = e.charge() >= 0.85;
                    SkillSound.playAt(e.position(), SoundEvents.PLAYER_ATTACK_STRONG, full ? 0.95f : 0.72f, full ? 0.82f : 1.02f);
                    SkillSound.playAt(e.position(), SoundEvents.BREEZE_SHOOT, full ? 0.85f : 0.62f, full ? 0.92f : 1.15f);
                    if (full) {
                        SkillSound.playAt(e.position(), SoundEvents.TRIDENT_THUNDER.value(), 0.55f, 1.40f);
                        if (actor != null && !ownFp) PlayerAfterimages.capture(actor, COL_CRESCENT_SHURA, 200, 360L, true);
                    }
                    CameraShake.impulse(pos, full ? 18 : 13, full ? 0.32f : 0.18f, 75L, 165L);
                }
            }
            case CareerSkills.ICE -> {
                if (e.phase() == START || e.phase() == RELEASE || e.phase() == HIT) {
                    if (eyeMode) {
                        SkillSound.playAt(e.position(), SoundEvents.TRIDENT_THROW.value(), 0.62f, 1.25f);
                        SkillSound.playAt(e.position(), SoundEvents.AMETHYST_BLOCK_BREAK, 0.60f, 1.38f);
                    } else {
                        SkillSound.playAt(e.position(), SoundEvents.GLASS_BREAK, 0.65f, 1.18f);
                        SkillSound.playAt(e.position(), SoundEvents.PLAYER_HURT_FREEZE, 0.58f, 1.12f);
                    }
                    if (e.phase() == START) {
                        CameraShake.impulse(pos, 14, 0.18f, 55L, 120L);
                    }
                } else if (e.phase() == SEGMENT) {
                    SkillSound.playAt(e.position(), eyeMode ? SoundEvents.AMETHYST_BLOCK_CHIME : SoundEvents.GLASS_BREAK, 0.45f, 1.35f);
                }
            }
            case CareerSkills.FIRE -> {
                if (e.phase() == RELEASE) {
                    SkillSound.playAt(e.position(), eyeMode ? SoundEvents.TRIDENT_THROW.value() : SoundEvents.BLAZE_SHOOT, 0.78f, eyeMode ? 1.12f : 0.88f);
                    SkillSound.playAt(e.position(), SoundEvents.FIRECHARGE_USE, 0.65f, 0.95f);
                    CameraShake.impulse(pos, 14, 0.18f, 55L, 110L);
                } else if (e.phase() == FINISH) {
                    SkillSound.playAt(e.position(), SoundEvents.GENERIC_EXPLODE.value(), 0.96f, eyeMode ? 0.82f : 0.68f);
                    SkillSound.playAt(e.position(), eyeMode ? SoundEvents.TRIDENT_THUNDER.value() : SoundEvents.MACE_SMASH_GROUND_HEAVY, 0.85f, eyeMode ? 1.18f : 0.74f);
                    CameraShake.impulse(pos, 20, eyeMode ? 0.44f : 0.40f, 105L, 220L);
                }
            }
            case CareerSkills.AGNI -> {
                if (e.phase() == START) {
                    SkillSound.playAt(e.position(), SoundEvents.BELL_RESONATE, 0.88f, 0.86f);
                    SkillSound.playAt(e.position(), SoundEvents.BEACON_ACTIVATE, 0.78f, 0.92f);
                    CameraShake.impulse(pos, 18, 0.28f, 85L, 175L);
                    if (actor != null && !ownFp) PlayerAfterimages.capture(actor, COL_AGNI_GOLD, 205, 420L, true);
                } else if (e.phase() == HIT) {
                    long prevHit = LAST_AGNI_HIT_AUDIO.getOrDefault(e.cast(), 0L);
                    if (e.at() - prevHit >= 110L) {
                        LAST_AGNI_HIT_AUDIO.put(e.cast(), e.at());
                        float pitch = 0.82f + (e.sequence() % 6) * 0.08f;
                        SkillSound.playAt(e.position(), SoundEvents.FIRECHARGE_USE, 0.56f, pitch);
                        SkillSound.playAt(e.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 0.38f, pitch + 0.25f);
                        CameraShake.impulse(pos, 15, 0.14f, 45L, 95L);
                    }
                } else if (e.phase() == FINISH) {
                    LAST_AGNI_HIT_AUDIO.remove(e.cast());
                    SkillSound.playAt(e.position(), SoundEvents.GENERIC_EXPLODE.value(), 1.0f, 0.62f);
                    SkillSound.playAt(e.position(), SoundEvents.MACE_SMASH_GROUND_HEAVY, 0.95f, 0.68f);
                    SkillSound.playAt(e.position(), SoundEvents.BELL_RESONATE, 0.92f, 0.68f);
                    CameraShake.impulse(pos, 24, 0.58f, 145L, 290L);
                }
            }
            case CareerSkills.EYE -> {
                if (!e.sweep()) {
                    if (e.phase() == START) {
                        SkillSound.playAt(e.position(), SoundEvents.WITHER_SPAWN, 0.52f, 0.92f);
                        SkillSound.playAt(e.position(), SoundEvents.TRIDENT_THUNDER.value(), 0.88f, 0.78f);
                        SkillSound.playAt(e.position(), SoundEvents.BEACON_ACTIVATE, 0.92f, 0.64f);
                        CameraShake.impulse(pos, 26, 0.42f, 130L, 260L);
                        if (actor != null && !ownFp) PlayerAfterimages.capture(actor, COL_EYE_VIOLET, 220, 480L, true);
                    }
                    return;
                }
                if (e.phase() == START) {
                    SkillSound.playAt(e.position(), SoundEvents.BEACON_ACTIVATE, 0.95f, 0.68f);
                    SkillSound.playAt(e.position(), SoundEvents.ENDER_DRAGON_GROWL, 0.55f, 0.78f);
                    CameraShake.impulse(pos, 26, 0.34f, 110L, 220L);
                    if (actor != null) PlayerAfterimages.capture(actor, COL_EYE_GOLD, 225, 520L, true);
                } else if (e.phase() == FINISH) {
                    SkillSound.playAt(e.position(), SoundEvents.GENERIC_EXPLODE.value(), 1.0f, 0.50f);
                    SkillSound.playAt(e.position(), SoundEvents.TRIDENT_THUNDER.value(), 1.0f, 0.62f);
                    SkillSound.playAt(e.position(), SoundEvents.MACE_SMASH_GROUND_HEAVY, 0.95f, 0.56f);
                    CameraShake.impulse(pos, 34, 0.78f, 200L, 390L);
                }
            }
            default -> {}
        }
    }

    // --- Main Skill Render Router --------------------------------------------

    private static void renderSkill(String id, SkillVfx.Track t, long age, long now, List<SpriteQuad> sp, Vec3 camera) {
        switch (id) {
            case CareerSkills.BRANDS -> renderBrandsCast(t, age, now, sp, camera);
            case CareerSkills.ORB -> renderGhostOrb(t, age, now, sp, camera);
            case CareerSkills.CRESCENT -> renderSpiritCrescent(t, age, now, sp, camera);
            case CareerSkills.ICE -> renderIceWave(t, age, now, sp, camera);
            case CareerSkills.FIRE -> renderFireWave(t, age, now, sp, camera);
            case CareerSkills.AGNI -> renderAgni(t, age, now, sp, camera);
            case CareerSkills.EYE -> renderWaveEye(t, age, now, sp, camera);
            default -> {}
        }
    }

    // --- 1. 波动刻印 (Wave Manifestation Brand Activation Flash) -------------

    private static void renderBrandsCast(SkillVfx.Track t, long age, long now, List<SpriteQuad> sp, Vec3 camera) {
        if (age >= 900L) return;
        double u = Math.clamp(age / 900.0, 0.0, 0.999);
        double fade = 1.0 - u;
        Vec3 pos = liveCasterPos(t.start.caster(), vector(t.start.origin()));
        emitGroundSprite(sp, TEX_AGNI_MANDALA, pos.add(0, 0.05, 0), 1.65 * (0.5 + 0.55 * EffectSprites.easeOutCubic(u)), now * 0.003, COL_GOLD, COL_GOLD_HOT, (int) (215 * fade), 0.72);
        EffectSprites.shockwave(sp, pos.add(0, 0.08, 0), 2.1 * EffectSprites.easeOutCubic(u), u * 1.6, COL_GOLD_HOT, (int) (215 * fade));
        emitFramedBillboard(sp, TEX_BRAND_RUNE, 4, (int) Math.floorMod(now / 100L, 4L), pos.add(0, 1.35 + 0.45 * u, 0), camera, 0.48 * (1.0 - 0.3 * u), COL_WHITE, COL_GOLD_HOT, (int) (235 * fade), 0.80);
    }

    // --- 2. 鬼印珠 (Ghost Orb: 1..5 Brand Scaling + Latched Thunder Bursts) --

    private static void renderGhostOrb(SkillVfx.Track t, long age, long now, List<SpriteQuad> sp, Vec3 camera) {
        Vec3 p = vector(t.last.position());
        int brands = Math.clamp((int) Math.round(Math.max(0.2, Math.max(t.start.charge(), t.last.charge())) * 5.0), 1, 5);
        double baseR = 0.64 + 0.16 * brands; // 1 brand = 0.80, 5 brands = 1.44!

        if (t.last.phase() == FINISH) {
            long endAge = Math.max(0L, now - t.last.at());
            if (endAge >= 450L) return;
            double u = Math.clamp(endAge / 450.0, 0.0, 0.999);
            double fade = 1.0 - u;
            int frame = Math.clamp((int) (u * 8), 0, 7);
            emitFramedBillboard(sp, TEX_GHOST_ORB, 8, frame, p, camera, baseR * (1.05 + 0.55 * u), COL_WHITE, COL_ORB_CYAN, (int) (240 * fade), 0.90);
            EffectSprites.impactBurst(sp, p, camera, baseR * 1.9, u, COL_ORB_PURPLE, (int) (235 * fade), false);
            EffectSprites.shockwave(sp, p.add(0, -0.45, 0), baseR * 2.1 * EffectSprites.easeOutCubic(u), u * 1.8, COL_ORB_CYAN, (int) (225 * fade));
            ORB_LAST_BEAT.remove(t.start.cast());
            return;
        }

        boolean latched = t.last.dual();
        int frame = (int) Math.floorMod(now / (latched ? 42L : 58L), 8L);
        double beat = latched ? ((now % 350L) / 350.0) : 0.0;
        double beatPunch = latched ? (1.0 + 0.18 * Math.exp(-beat * 5.0)) : 1.0;
        double r = baseR * beatPunch;

        // Core 8-frame swirling dark-purple thunder orb billboard (dual layer)
        emitFramedBillboard(sp, TEX_GHOST_ORB, 8, frame, p, camera, r, COL_WHITE, COL_ORB_PURPLE, 248, 0.82);
        EffectSprites.glow(sp, p, camera, r * 1.28, COL_ORB_CYAN, latched ? 195 : 155);

        // Tilted spinning wave-rune orbit ring (and 2nd counter-ring when 3+ brands!)
        double spin1 = now * (latched ? 0.0085 : 0.0055);
        emitTiltedRing(sp, TEX_BRAND_RUNE, 4, (int) Math.floorMod(now / 110L, 4L), p, r * 1.18, spin1, 28.0, 14.0, COL_ORB_PURPLE, COL_ORB_CYAN, 195);
        if (brands >= 3) {
            emitTiltedRing(sp, TEX_BRAND_RUNE, 4, (int) Math.floorMod(now / 90L + 2, 4L), p, r * 1.32, -spin1 * 1.25, -42.0, -24.0, COL_ORB_CYAN, COL_WHITE, 185);
        }

        // Dynamic crackling 3D lightning arcs scaled by consumed brands (1..5)
        long boltTick = now / (latched ? 48L : 65L);
        int boltCount = latched ? (1 + (brands + 1) / 2) : Math.max(1, brands / 2);
        for (int i = 0; i < boltCount; i++) {
            SplittableRandom rng = new SplittableRandom(boltTick * 0x9E3779B97F4A7C15L ^ t.start.seed() ^ (i * 3571L));
            double a0 = rng.nextDouble() * Math.TAU;
            double e0 = (rng.nextDouble() - 0.5) * 1.4;
            double span = r * (latched ? 1.35 : 0.95);
            Vec3 from = p.add(Math.cos(a0) * span, Math.sin(e0) * span * 0.75, Math.sin(a0) * span);
            Vec3 to = latched ? p : p.subtract(Math.cos(a0) * span, Math.sin(e0) * span * 0.75, Math.sin(a0) * span);
            LightningBolt.bolt(sp, from, to, camera, boltTick ^ (i * 97L), 5, 0.24 * (0.8 + 0.1 * brands), 0.042 + 0.006 * brands, i == 0 ? COL_ORB_CYAN : COL_ORB_PURPLE, 225, false);
        }

        if (latched) {
            // 350ms rhythmic shockwave & impact ripple while grinding an enemy
            EffectSprites.shockwave(sp, p.add(0, -0.55, 0), r * (0.65 + 1.15 * EffectSprites.easeOutCubic(beat)), beat * 1.4, COL_ORB_CYAN, (int) (195 * (1.0 - beat)));
            long beatStep = now / 350L;
            Long prevBeat = ORB_LAST_BEAT.put(t.start.cast(), beatStep);
            if (prevBeat != null && beatStep > prevBeat) {
                SkillSound.playAt(p.x, p.y, p.z, SoundEvents.AMETHYST_BLOCK_CHIME, 0.26f, 1.35f + (beatStep % 3) * 0.14f);
            }
        } else {
            // Traveling plasma ribbon tail
            RibbonTrail.push("asura_orb:" + t.start.cast(), p, r * 0.42, COL_ORB_PURPLE, 215, 260L, now);
            RibbonTrail.push("asura_orb_core:" + t.start.cast(), p, r * 0.20, COL_ORB_CYAN, 200, 180L, now);
        }
    }

    // --- 3. 邪光斩 (Spirit Crescent: Charge + Twin-Crossed X-Shura Crescent Wave) ---

    private static void renderSpiritCrescent(SkillVfx.Track t, long age, long now, List<SpriteQuad> sp, Vec3 camera) {
        Vec3 dir = horizontalDir(vector(t.start.direction()));
        var mc = Minecraft.getInstance();
        Player actor = mc == null || mc.level == null ? null : mc.level.getPlayerByUUID(t.start.caster());
        boolean ownFp = isOwnFirstPerson(t.start.caster());

        if (!t.released) {
            // Charging phase (0 -> 1000ms -> Shura Full Charge!)
            double charge = Math.clamp(age / 1000.0, 0.0, 1.0);
            boolean full = charge >= 0.99;
            Vec3 aim = horizontalDir(PlayerVisualAnchor.chargeAim(actor, dir));
            Vec3 base = actor == null ? vector(t.start.origin()) : PlayerVisualAnchor.position(actor);
            var pose = actor == null ? null : Effects.VFX.playerPose(actor.getUUID(), now);
            Vec3 tip = (actor != null && pose != null && !ownFp)
                ? PlayerBoneAnchor.weaponTip(actor, pose.frame(), 1.05)
                : base.add(aim.scale(1.05)).add(0, 1.15, 0);

            // Ground Shura charge seal + contracting energy ring
            double footR = 1.25 + 0.85 * charge;
            emitGroundSprite(sp, TEX_AGNI_MANDALA, base.add(0, 0.045, 0), footR, now * (full ? 0.0040 : 0.0022), full ? COL_CRESCENT_SHURA : COL_CRESCENT_GOLD, COL_WHITE, (int) ((125 + 95 * charge) * (ownFp ? 0.55 : 1.0)), 0.65);
            double contract = 1.0 - ((now % 380L) / 380.0);
            EffectSprites.shockwave(sp, base.add(0, 0.08, 0), footR * (0.25 + 0.90 * contract), -now * 0.004, full ? COL_CRESCENT_SHURA : COL_CRESCENT_GOLD, (int) (185 * contract));

            // Mini pulsing X-crescent nucleus gathering at the blade tip!
            int frame = (int) Math.floorMod(now / 65L, 8L);
            double tipScale = (0.48 + 0.62 * charge) * (ownFp ? 0.68 : 1.0);
            emitDirectionalWavePlane(sp, TEX_CRESCENT_WAVE, frame, tip, aim, 38.0, tipScale, COL_WHITE, full ? COL_CRESCENT_SHURA : COL_CRESCENT_GOLD, 225, 0.80);
            emitDirectionalWavePlane(sp, TEX_CRESCENT_WAVE, (frame + 4) & 7, tip, aim, -38.0, tipScale, COL_WHITE, full ? COL_CRESCENT_SHURA : COL_CRESCENT_GOLD, 225, 0.80);
            EffectSprites.glow(sp, tip, camera, tipScale * 1.15, full ? COL_CRESCENT_SHURA : COL_CRESCENT_GOLD, full ? 240 : 195);

            if (full) {
                if (CRESCENT_FULL_CHIMED.add(t.start.cast())) {
                    SkillSound.playAt(tip.x, tip.y, tip.z, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.72f, 1.55f);
                    WorldEffects.playShockwave(base.add(0, 0.12, 0), 0.4, 2.6, COL_CRESCENT_SHURA, 235, 300L);
                }
                if (!ownFp) {
                    Vec3 side = rightOf(aim);
                    LightningBolt.bolt(sp, tip.add(side.scale(-0.7)).add(0, 0.45, 0), tip.add(side.scale(0.7)).add(0, -0.45, 0), camera, now / 55L, 5, 0.22, 0.042, COL_CRESCENT_SHURA, 220, false);
                }
            }
            return;
        }

        // Released advancing X-Shura Giant Crescent Wave!
        double charge = Math.clamp(t.last.charge(), 0.0, 1.0);
        boolean shura = charge >= 0.85;
        Vec3 pos = vector(t.last.position());
        Vec3 right = rightOf(dir);
        int frame = (int) Math.floorMod(now / 45L, 8L);
        double sizeScale = Math.max(0.5, Math.max(t.start.horizontalScale(), t.last.horizontalScale()));
        double waveR = (1.25 + 1.15 * charge) * sizeScale; // Uncharged ~2.5 diameter -> Full charge ~4.8 -> +70% card ~8.16!
        int boostCol = shura ? COL_CRESCENT_SHURA : COL_CRESCENT_GOLD;

        // Twin crossed 3D razor crescent planes (±38 deg roll) -> unmistakable Shura X-Crescent Giant Wave!
        emitDirectionalWavePlane(sp, TEX_CRESCENT_WAVE, frame, pos, dir, 38.0, waveR, COL_WHITE, boostCol, 252, 0.90);
        emitDirectionalWavePlane(sp, TEX_CRESCENT_WAVE, (frame + 3) & 7, pos, dir, -38.0, waveR, COL_WHITE, boostCol, 252, 0.90);
        // Horizontal ground-skimming cutting echo crescent for strong top-down & side readability
        emitDirectionalWavePlane(sp, TEX_CRESCENT_WAVE, (frame + 5) & 7, pos.add(0, -waveR * 0.12, 0), dir, 0.0, waveR * 0.94, COL_WHITE, COL_CRESCENT_GOLD, 195, 0.65);

        // Luminous leading X-heart & ground pressure wake
        EffectSprites.glow(sp, pos.add(dir.scale(waveR * 0.42)), camera, waveR * 0.85, boostCol, 215);
        EffectSprites.shockwave(sp, new Vec3(pos.x, vector(t.start.origin()).y + 0.08, pos.z), waveR * 0.72, now * 0.007, boostCol, 155);

        // Twin sweeping wingtip ribbons
        Vec3 wingA = pos.add(right.scale(waveR * 0.56)).add(0, waveR * 0.44, 0);
        Vec3 wingB = pos.add(right.scale(-waveR * 0.56)).add(0, -waveR * 0.36, 0);
        RibbonTrail.push("crescent_a:" + t.start.cast(), wingA, 0.24 + 0.16 * charge, boostCol, 215, 240L, now);
        RibbonTrail.push("crescent_b:" + t.start.cast(), wingB, 0.24 + 0.16 * charge, COL_CRESCENT_GOLD, 215, 240L, now);
        RibbonTrail.push("crescent_c:" + t.start.cast(), pos, 0.34 + 0.24 * charge, COL_WHITE, 195, 200L, now);

        // Full-charge 修罗邪光斩 crackling lightning arcs bridging the giant X wings!
        if (shura) {
            Vec3 topL = pos.add(right.scale(-waveR * 0.65)).add(0, waveR * 0.58, 0);
            Vec3 botR = pos.add(right.scale(waveR * 0.65)).add(0, -waveR * 0.48, 0);
            LightningBolt.bolt(sp, topL, botR, camera, (now / 50L) ^ t.start.seed(), 6, 0.36, 0.055, COL_CRESCENT_SHURA, 225, false);
        }
    }

    // --- 4. 冰刃·波动剑 & 暗天波动眼·穿云刺轮 (Ice Wave + Lingering Spike Trails) ---

    private static void renderIceWave(SkillVfx.Track t, long age, long now, List<SpriteQuad> sp, Vec3 camera) {
        boolean eyeMode = t.start.color() == CareerRules.EYE_COLOR;
        Vec3 dir = horizontalDir(vector(t.last.direction()));

        // (a) Caster's wave-launch burst track (non-moving parent cast with 3 staggered HIT flashes)
        if (!t.released && t.last.phase() != TRAIL) {
            Vec3 origin = vector(t.start.origin());
            for (SkillVisualEvent flash : t.flashes) {
                if (flash.phase() != HIT && flash.phase() != START) continue;
                long el = now - flash.at();
                if (el < 0 || el >= 360L) continue;
                double u = el / 360.0;
                double fade = 1.0 - u;
                Vec3 burstPos = origin.add(dir.scale(1.35)).add(0, 0.12, 0);
                if (eyeMode) {
                    emitDirectionalWavePlane(sp, TEX_EYE_WING, Math.clamp((int) (u * 8), 0, 7), burstPos.add(0, 0.55, 0), dir, 0.0, 1.85, COL_WHITE, COL_EYE_VIOLET, (int) (225 * fade), 0.78);
                    EffectSprites.shockwave(sp, burstPos, 2.2 * EffectSprites.easeOutCubic(u), u * 1.4, COL_EYE_LILAC, (int) (205 * fade));
                } else {
                    emitGroundSprite(sp, TEX_ICE_FROST, burstPos, 2.1 * (0.45 + 0.65 * EffectSprites.easeOutCubic(u)), u * 0.5, COL_WHITE, COL_ICE_BRIGHT, (int) (215 * fade), 0.72);
                    EffectSprites.shockwave(sp, burstPos, 2.3 * EffectSprites.easeOutCubic(u), u * 1.4, COL_ICE_MAIN, (int) (210 * fade));
                }
            }
            return;
        }

        // (b) Advancing ground wave track (each of the 3 staggered 12-block waves!)
        if (t.terminal > 0 && now > t.terminal + 120L) return;
        Vec3 pos = vector(t.last.position());
        Vec3 right = rightOf(dir);

        // Plant a persistent shattering spike cluster every ~0.85 blocks traveled along the 12-block channel!
        Vec3 lastPlanted = LAST_ICE_PLANTED.get(t.start.cast());
        if (lastPlanted == null || lastPlanted.distanceToSqr(pos) >= 0.72) {
            LAST_ICE_PLANTED.put(t.start.cast(), pos);
            long nodeSeed = t.start.seed() ^ (now * 31L) ^ Double.doubleToLongBits(pos.x + pos.z);
            SplittableRandom rng = new SplittableRandom(nodeSeed);
            // Plant center spire + slight staggered flank offset across the 3-block wave width
            double lateral = (rng.nextDouble() - 0.5) * 1.35;
            double scale = 1.30 + rng.nextDouble() * 0.62;
            double yaw = Math.atan2(dir.z, dir.x) + (rng.nextDouble() - 0.5) * 0.9;
            plantIceNode(new IceNode(pos.add(right.scale(lateral)), dir, now, yaw, scale, eyeMode, nodeSeed));
            // Also plant an opposite flank spike so the 3-block lane looks dense & jagged like DNF!
            double lateral2 = -lateral * 0.85 + (rng.nextDouble() - 0.5) * 0.45;
            plantIceNode(new IceNode(pos.add(right.scale(lateral2)).subtract(dir.scale(0.25)), dir, now + 25L, yaw + 1.1, scale * 0.82, eyeMode, nodeSeed ^ 0x55AA55L));
        }

        // Intense surging wave-head leading edge + frost/dark-lightning wake
        if (eyeMode) {
            int frame = (int) Math.floorMod(now / 45L, 8L);
            emitDirectionalWavePlane(sp, TEX_EYE_WING, frame, pos.add(0, 0.35, 0), dir, 0.0, 1.85, COL_WHITE, COL_EYE_VIOLET, 235, 0.82);
            EffectSprites.glow(sp, pos.add(0, 0.55, 0), camera, 1.35, COL_EYE_LILAC, 205);
            RibbonTrail.push("eye_pierce:" + t.start.cast(), pos.add(0, 0.38, 0), 0.52, COL_EYE_VIOLET, 215, 260L, now);
            LightningBolt.bolt(sp, pos.add(right.scale(-1.1)).add(0, 0.35, 0), pos.add(right.scale(1.1)).add(0, 0.95, 0), camera, (now / 50L) ^ t.start.seed(), 5, 0.28, 0.048, COL_EYE_LILAC, 210, false);
        } else {
            EffectSprites.glow(sp, pos.add(0, 0.55, 0), camera, 1.45, COL_ICE_BRIGHT, 215);
            EffectSprites.shockwave(sp, pos.add(0, 0.06, 0), 1.45, now * 0.008, COL_ICE_MAIN, 180);
            RibbonTrail.push("asura_ice:" + t.start.cast(), pos.add(0, 0.35, 0), 0.56, COL_ICE_MAIN, 210, 260L, now);
            RibbonTrail.push("asura_ice_core:" + t.start.cast(), pos.add(0, 0.55, 0), 0.24, COL_ICE_BRIGHT, 195, 190L, now);
        }
    }

    private static void plantIceNode(IceNode node) {
        if (ICE_NODES.size() >= MAX_ICE_NODES) {
            ICE_NODES.removeFirst();
        }
        ICE_NODES.add(node);
    }

    private static void renderLingeringIceNodes(List<SpriteQuad> sp, Vec3 camera, long now) {
        if (ICE_NODES.isEmpty()) return;
        for (var it = ICE_NODES.iterator(); it.hasNext(); ) {
            IceNode node = it.next();
            long age = now - node.spawnedAt();
            if (age < 0) continue;
            if (age >= ICE_NODE_LIFE_MS) {
                it.remove();
                continue;
            }
            double u = age / (double) ICE_NODE_LIFE_MS;
            int frame = Math.clamp((int) (u * 8), 0, 7);
            double fade = u < 0.72 ? 1.0 : (1.0 - u) / 0.28;
            int alpha = Math.clamp((int) Math.round(248 * fade), 0, 255);
            Vec3 ground = node.pos();

            if (node.eyeVariant()) {
                // Wave Eye Piercing Wheel (穿云/刺轮): angled violet lightning thorns thrusting from earth!
                double spearH = node.scale() * 1.65;
                double spearW = node.scale() * 0.58;
                Vec3 tipDir = UP.scale(0.86).add(node.dir().scale(0.50)).normalize();
                emitAxialSpike(sp, TEX_EYE_SPEAR, frame, ground, tipDir, spearW, spearH, node.yaw(), COL_WHITE, COL_EYE_VIOLET, alpha, 0.80);
                emitGroundSprite(sp, TEX_EYE_DOMAIN, ground.add(0, 0.04, 0), node.scale() * 0.78, node.yaw(), COL_WHITE, COL_EYE_VIOLET, (int) (185 * fade), 0.55);
                if (frame <= 3) {
                    EffectSprites.glow(sp, ground.add(tipDir.scale(spearH * 0.55)), camera, node.scale() * 0.58, COL_EYE_GOLD, (int) (175 * fade));
                }
            } else {
                // Classic DNF Ice Wave Sword: 2 crossed multi-faceted crystal spike planes + ground frost crack!
                double spikeH = node.scale() * 1.48;
                double spikeW = node.scale() * 0.78;
                emitVerticalCrossed(sp, TEX_ICE_SPIKE, frame, ground, spikeW, spikeH, node.yaw(), 2, COL_WHITE, COL_ICE_BRIGHT, alpha, 0.65);
                emitGroundSprite(sp, TEX_ICE_FROST, ground.add(0, 0.04, 0), node.scale() * 0.85, node.yaw(), COL_WHITE, COL_ICE_BRIGHT, (int) (215 * fade), 0.58);
                if (frame == 4 || frame == 5) {
                    // Brief cold shatter flash as crystal fractures!
                    EffectSprites.glow(sp, ground.add(0, spikeH * 0.52, 0), camera, node.scale() * 0.65, COL_ICE_BRIGHT, (int) (155 * fade));
                }
            }
        }
        if (LAST_ICE_PLANTED.size() > 64) {
            LAST_ICE_PLANTED.clear();
        }
    }

    // --- 5. 爆炎·波动剑 & 暗天波动眼·天照闪枪 (Fire Wave Orb + Towering Lava Pillar / Flashing Spears) ---

    private static void renderFireWave(SkillVfx.Track t, long age, long now, List<SpriteQuad> sp, Vec3 camera) {
        if (!t.released) return;
        boolean eyeMode = t.start.color() == CareerRules.EYE_COLOR;
        Vec3 p = vector(t.last.position());

        if (t.last.phase() == FINISH) {
            long impAge = Math.max(0L, now - t.last.at());
            if (impAge >= 920L) return;
            double u = Math.clamp(impAge / 920.0, 0.0, 0.999);
            double fastU = Math.clamp(impAge / 520.0, 0.0, 0.999);
            double fade = 1.0 - u;
            double craterFade = u < 0.62 ? 1.0 : (1.0 - u) / 0.38;
            int pillarFrame = Math.clamp((int) (u * 8), 0, 7);
            Vec3 ground = new Vec3(p.x, p.y - 0.10, p.z);
            double blastScale = Math.max(0.5, Math.max(t.start.horizontalScale(), t.last.horizontalScale()));
            double heightScale = Math.min(1.85, 0.70 + 0.30 * blastScale);

            if (eyeMode) {
                // Wave Eye Variant — 天照 / 闪枪贯地大爆 (Converging Violet-Gold Flashing Spears + Abyssal Storm!)
                emitGroundSprite(sp, TEX_EYE_DOMAIN, ground.add(0, 0.05, 0), 3.35 * blastScale * (0.5 + 0.55 * EffectSprites.easeOutCubic(fastU)), now * 0.002, COL_WHITE, COL_EYE_VIOLET, (int) (235 * craterFade), 0.72);
                emitGroundSprite(sp, TEX_FIRE_CRATER, ground.add(0, 0.04, 0), 2.85 * blastScale, t.start.seed() * 0.01, COL_EYE_DARK, COL_EYE_VIOLET, (int) (220 * craterFade), 0.55);

                // Central vertical towering Flashing Spear + diagonally converging spears skewering the blast point!
                emitAxialSpike(sp, TEX_EYE_SPEAR, pillarFrame, ground, UP, 1.35 * Math.min(1.75, blastScale), 5.8 * heightScale, now * 0.002, COL_WHITE, COL_EYE_GOLD, (int) (252 * fade), 0.92);
                int spears = blastScale > 1.5 ? 8 : 4;
                for (int i = 0; i < spears; i++) {
                    double ang = i * (Math.TAU / spears) + (t.start.seed() & 7) * 0.25;
                    Vec3 tiltAxis = new Vec3(Math.cos(ang) * 0.44, 0.90, Math.sin(ang) * 0.44).normalize();
                    Vec3 basePt = ground.add(Math.cos(ang) * 0.65 * blastScale, 0.02, Math.sin(ang) * 0.65 * blastScale);
                    emitAxialSpike(sp, TEX_EYE_SPEAR, pillarFrame, basePt, tiltAxis, 0.95 * Math.min(1.5, blastScale), 4.9 * heightScale, ang, COL_WHITE, COL_EYE_LILAC, (int) (238 * fade), 0.85);
                }

                // Blinding thunder-lotus detonation & radial lightning arcs
                if (impAge < 540L) {
                    emitFramedBillboard(sp, TEX_AGNI_LOTUS, 8, Math.clamp((int) (fastU * 8), 0, 7), p.add(0, 1.1 * heightScale, 0), camera, 3.6 * blastScale, COL_EYE_VIOLET, COL_EYE_GOLD, (int) (240 * (1.0 - fastU * 0.4)), 0.85);
                }
                EffectSprites.shockwave(sp, ground.add(0, 0.12, 0), 3.9 * blastScale * EffectSprites.easeOutCubic(u), u * 1.8, COL_EYE_LILAC, (int) (245 * fade));
                EffectSprites.shockwave(sp, ground.add(0, 1.35, 0), 3.1 * blastScale * EffectSprites.easeOutCubic(u), -u * 1.5, COL_EYE_GOLD, (int) (215 * fade));
                int bolts = blastScale > 1.5 ? 6 : 4;
                for (int i = 0; i < bolts; i++) {
                    double ang = i * (Math.TAU / bolts) + 0.4;
                    Vec3 edge = ground.add(Math.cos(ang) * 3.1 * blastScale, 0.25, Math.sin(ang) * 3.1 * blastScale);
                    LightningBolt.bolt(sp, p.add(0, 2.8 * heightScale, 0), edge, camera, (now / 50L) ^ t.start.seed() ^ (i * 79L), 6, 0.42 * Math.min(1.6, blastScale), 0.068, i % 2 == 0 ? COL_EYE_GOLD : COL_EYE_LILAC, (int) (230 * fade), false);
                }
            } else {
                // Classic DNF Fire Wave Explosion: Scorched Lava Crater + Towering 3-Plane Molten Eruption Pillar!
                emitGroundSprite(sp, TEX_FIRE_CRATER, ground.add(0, 0.05, 0), 3.1 * blastScale * (0.55 + 0.48 * EffectSprites.easeOutCubic(fastU)), t.start.seed() * 0.01, COL_WHITE, COL_FIRE_MAIN, (int) (245 * craterFade), 0.62);

                // Dominant central volcanic lava column (scaled to match blastRadius)
                emitVerticalCrossed(sp, TEX_FIRE_PILLAR, pillarFrame, ground, 1.75 * Math.min(1.85, blastScale), 5.4 * heightScale, t.start.seed() * 0.02, 3, COL_WHITE, COL_FIRE_HOT, (int) (252 * fade), 0.85);

                // Surrounding secondary blast geysers filling out the blast radius
                int geysers = blastScale > 1.5 ? 6 : 3;
                for (int i = 0; i < geysers; i++) {
                    double ang = i * (Math.TAU / geysers) + (t.start.seed() & 15) * 0.35;
                    Vec3 sub = ground.add(Math.cos(ang) * 1.45 * blastScale, 0.02, Math.sin(ang) * 1.45 * blastScale);
                    emitVerticalCrossed(sp, TEX_FIRE_PILLAR, Math.min(7, pillarFrame + (i & 1)), sub, 0.95 * Math.min(1.55, blastScale), 3.6 * heightScale, ang, 2, COL_WHITE, COL_FIRE_MAIN, (int) (225 * fade), 0.72);
                }

                // Immediate burst flash & twin fiery shockwaves
                if (impAge < 520L) {
                    EffectSprites.impactBurst(sp, p.add(0, 1.0 * heightScale, 0), camera, 3.4 * blastScale, fastU, COL_FIRE_MAIN, 245, false);
                    EffectSprites.glow(sp, p.add(0, 1.2 * heightScale, 0), camera, 3.2 * blastScale * (1.0 - fastU), COL_FIRE_HOT, 235);
                }
                EffectSprites.shockwave(sp, ground.add(0, 0.12, 0), 3.6 * blastScale * EffectSprites.easeOutCubic(u), u * 1.5, COL_FIRE_HOT, (int) (240 * fade));
                EffectSprites.shockwave(sp, ground.add(0, 1.15, 0), 2.8 * blastScale * EffectSprites.easeOutCubic(u), -u * 1.2, COL_FIRE_MAIN, (int) (205 * fade));
            }
            return;
        }

        // Parabola flight phase (5-block-wide enemy-capturing vortex orb!)
        int frame = (int) Math.floorMod(now / 48L, 8L);
        if (eyeMode) {
            emitFramedBillboard(sp, TEX_GHOST_ORB, 8, frame, p, camera, 1.08, COL_WHITE, COL_EYE_GOLD, 248, 0.88);
            emitTiltedRing(sp, TEX_EYE_DOMAIN, 1, 0, p, 1.55, now * 0.008, 22.0, 18.0, COL_EYE_VIOLET, COL_EYE_GOLD, 215);
            EffectSprites.glow(sp, p, camera, 1.55, COL_EYE_LILAC, 205);
            RibbonTrail.push("eyefire:" + t.start.cast(), p, 0.44, COL_EYE_VIOLET, 230, 270L, now);
            RibbonTrail.push("eyefire_core:" + t.start.cast(), p, 0.22, COL_EYE_GOLD, 220, 190L, now);
            double a = now * 0.015;
            Vec3 pull = p.add(Math.cos(a) * 1.65, 0.45 * Math.sin(a * 1.7), Math.sin(a) * 1.65);
            LightningBolt.bolt(sp, pull, p, camera, (now / 50L) ^ t.start.seed(), 5, 0.26, 0.048, COL_EYE_GOLD, 215, false);
        } else {
            // Roaring inward-pulling magma vortex orb + swirling capture ring visually expressing the 5-block grab!
            emitFramedBillboard(sp, TEX_FIRE_ORB, 8, frame, p, camera, 1.15, COL_WHITE, COL_FIRE_HOT, 250, 0.85);
            emitTiltedRing(sp, TEX_AGNI_MANDALA, 1, 0, p, 1.62, -now * 0.0075, 18.0, -14.0, COL_FIRE_MAIN, COL_FIRE_HOT, 185);
            EffectSprites.glow(sp, p, camera, 1.65, COL_FIRE_MAIN, 215);
            RibbonTrail.push("asura_fire:" + t.start.cast(), p, 0.48, COL_FIRE_MAIN, 235, 280L, now);
            RibbonTrail.push("asura_fire_core:" + t.start.cast(), p, 0.24, COL_FIRE_HOT, 225, 195L, now);
        }
    }

    // --- 6. 不动明王阵 (Agni Pentacle: Sacred Mandala + Converging Flame Beads + Blazing Lotus Finisher!) ---

    private static void renderAgni(SkillVfx.Track t, long age, long now, List<SpriteQuad> sp, Vec3 camera) {
        Vec3 center = vector(t.start.origin());
        int count = Math.clamp((int) Math.round(t.start.charge() * SkillResourceRules.AGNI_MAX_ORBS), 1, SkillResourceRules.AGNI_MAX_ORBS);

        // Phase B: Iconic DNF Agni Finisher Blazing Lotus Explosion (850ms)!
        if (t.last.phase() == FINISH) {
            long finAge = Math.max(0L, now - t.last.at());
            if (finAge >= 850L) return;
            double u = Math.clamp(finAge / 850.0, 0.0, 0.999);
            double fastU = Math.clamp(finAge / 480.0, 0.0, 0.999);
            double fade = 1.0 - u;
            double craterFade = u < 0.60 ? 1.0 : (1.0 - u) / 0.40;
            int burstFrame = Math.clamp((int) (u * 8), 0, 7);

            // Scorched crater + fading blazing Agni Mandala across the full 5-block zone
            emitGroundSprite(sp, TEX_FIRE_CRATER, center.add(0, 0.04, 0), 5.35, t.start.seed() * 0.01, COL_WHITE, COL_AGNI_GOLD, (int) (235 * craterFade), 0.65);
            emitGroundSprite(sp, TEX_AGNI_MANDALA, center.add(0, 0.06, 0), 5.45 * (1.0 + 0.14 * u), now * 0.0025, COL_WHITE, COL_GOLD_HOT, (int) (245 * fade), 0.90);

            // Giant blooming 8-petal Immovable King Lotus burst (both ground flat & towering upright billboard!)
            emitGroundFrame(sp, TEX_AGNI_LOTUS, 8, burstFrame, center.add(0, 0.16, 0), 6.1, u * 0.7, COL_WHITE, COL_GOLD_HOT, (int) (252 * fade), 0.92);
            emitFramedBillboard(sp, TEX_AGNI_LOTUS, 8, burstFrame, center.add(0, 2.2, 0), camera, 4.9, COL_WHITE, COL_GOLD_HOT, (int) (248 * fade), 0.90);

            // Towering central sacred golden flame pillar (6.8 blocks tall!) + radial orb explosion columns
            emitVerticalCrossed(sp, TEX_FIRE_PILLAR, burstFrame, center.add(0, 0.04, 0), 2.25, 6.8, now * 0.002, 3, COL_GOLD_HOT, COL_AGNI_GOLD, (int) (250 * fade), 0.90);
            int pillars = Math.min(count, 12);
            int boltStep = Math.max(1, (pillars + 5) / 6);
            for (int i = 0; i < pillars; i++) {
                double ang = i * (Math.TAU / pillars) + u * 0.6;
                double dist = 1.2 + 3.4 * EffectSprites.easeOutCubic(u);
                Vec3 pillarPos = center.add(Math.cos(ang) * dist, 0.04, Math.sin(ang) * dist);
                emitVerticalCrossed(sp, TEX_FIRE_PILLAR, Math.min(7, burstFrame + (i & 1)), pillarPos, pillars > 6 ? 0.85 : 1.05, 4.2, ang, 2, COL_WHITE, COL_AGNI_GOLD, (int) (215 * fade), 0.75);
                if (finAge < 480L && i % boltStep == 0) {
                    LightningBolt.bolt(sp, center.add(0, 2.4, 0), pillarPos.add(0, 1.6, 0), camera, (now / 50L) ^ (i * 113L), 6, 0.45, 0.072, COL_GOLD_HOT, (int) (235 * (1.0 - fastU)), false);
                }
            }

            // Triple staggered fiery golden shockwaves
            EffectSprites.shockwave(sp, center.add(0, 0.14, 0), 6.2 * EffectSprites.easeOutCubic(u), u * 1.8, COL_GOLD_HOT, (int) (248 * fade));
            EffectSprites.shockwave(sp, center.add(0, 1.45, 0), 5.2 * EffectSprites.easeOutCubic(u), -u * 1.4, COL_AGNI_GOLD, (int) (220 * fade));
            EffectSprites.shockwave(sp, center.add(0, 2.85, 0), 4.1 * EffectSprites.easeOutCubic(u), u * 2.1, COL_FIRE_MAIN, (int) (195 * fade));
            if (finAge < 450L) {
                EffectSprites.glow(sp, center.add(0, 2.2, 0), camera, 5.6 * (1.0 - fastU), COL_GOLD_HOT, 245);
            }
            return;
        }

        // Phase A: 3s Channel — Sacred Mandala + Control Pillars + Accelerating & Converging Agni Flame Beads!
        double dur = Math.max(800.0, t.start.duration() > 0 ? t.start.duration() : 3000.0);
        double chanU = Math.clamp(age / dur, 0.0, 1.0);
        double openEase = Math.clamp(age / 280.0, 0.0, 1.0);

        // Dual counter-rotating 5-block Immovable King Sacred Mandala!
        double mandalaR = 5.25 * (0.45 + 0.55 * EffectSprites.easeOutCubic(openEase));
        emitGroundSprite(sp, TEX_AGNI_MANDALA, center.add(0, 0.05, 0), mandalaR, now * (0.0012 + 0.0018 * chanU), COL_WHITE, COL_AGNI_GOLD, 240, 0.78);
        emitGroundSprite(sp, TEX_AGNI_MANDALA, center.add(0, 0.068, 0), mandalaR * 0.62, -now * (0.0024 + 0.0024 * chanU), COL_GOLD, COL_GOLD_HOT, 180, 0.72);

        // Soft golden restraint aura pillars around the 5-block control perimeter
        int orbFrame = (int) Math.floorMod(now / 75L, 4L);
        for (int i = 0; i < 4; i++) {
            double ang = i * (Math.PI * 0.5) + now * 0.0010;
            Vec3 pBase = center.add(Math.cos(ang) * 4.65, 0.05, Math.sin(ang) * 4.65);
            EffectSprites.glow(sp, pBase.add(0, 1.1, 0), camera, 0.95, COL_AGNI_GOLD, 115);
        }

        // Orbiting 1..24 Agni Flame Beads: accelerate smoothly and converge inward in final 18% before blasting!
        double converge = chanU > 0.82 ? ((chanU - 0.82) / 0.18) : 0.0;
        double orbitR = lerp(count > 8 ? 3.75 : 3.35, count > 8 ? 2.70 : 2.45, chanU) * (1.0 - 0.78 * converge * converge);
        double orbH = 1.10 + 0.65 * chanU + 0.45 * converge;
        // Smooth quadratic phase integral so beads accelerate cleanly without jittering backward
        double theta = (age * 0.0042) + (age * chanU * 0.0048);
        double densityScale = count <= 6 ? 1.0 : Math.max(0.56, 1.0 - (count - 6) * 0.024);
        int tetherStep = Math.max(1, (count + 7) / 8);

        for (int i = 0; i < count; i++) {
            double ang = theta + i * (Math.TAU / count);
            Vec3 orbP = center.add(Math.cos(ang) * orbitR, orbH + 0.14 * Math.sin(ang * 2.0), Math.sin(ang) * orbitR);
            double orbScale = (0.68 + 0.18 * chanU + 0.22 * converge) * densityScale;
            emitFramedBillboard(sp, TEX_AGNI_ORB, 4, (orbFrame + i) & 3, orbP, camera, orbScale, COL_WHITE, COL_GOLD_HOT, 248, 0.88);
            EffectSprites.glow(sp, orbP, camera, orbScale * 1.45, COL_AGNI_GOLD, 210);
            RibbonTrail.push("agni_bead:" + t.start.cast() + ":" + i, orbP, (0.30 + 0.12 * chanU) * densityScale, COL_AGNI_GOLD, 235, 310L, now);
            if (count <= 12) {
                RibbonTrail.push("agni_bead_hot:" + t.start.cast() + ":" + i, orbP, 0.14 * densityScale, COL_GOLD_HOT, 215, 200L, now);
            }

            // Fiery golden energy tether linking beads to the rising convergence heart
            if (i % tetherStep == 0 || (converge > 0.1 && i % Math.max(1, tetherStep / 2) == 0)) {
                LightningBolt.bolt(sp, center.add(0, orbH * 0.9, 0), orbP, camera, (now / 60L) ^ (i * 53L), 4, 0.18, 0.036, COL_GOLD_HOT, (int) (155 + 85 * converge), false);
            }
        }

        // Rhythmic golden pulse rings on each HIT flash during the spin
        for (SkillVisualEvent flash : t.flashes) {
            if (flash.phase() != HIT) continue;
            long el = now - flash.at();
            if (el < 0 || el >= 340L) continue;
            double hu = el / 340.0;
            EffectSprites.shockwave(sp, center.add(0, 0.10, 0), 5.2 * EffectSprites.easeOutCubic(hu), hu * 1.2, COL_GOLD_HOT, (int) (195 * (1.0 - hu)));
        }

        // Building central convergence flare right before detonation
        if (converge > 0.05) {
            EffectSprites.glow(sp, center.add(0, orbH, 0), camera, 2.4 * converge, COL_GOLD_HOT, (int) (240 * converge));
        }
    }

    // --- 7. 暗天波动眼 (Wave Eye: Opening Takeover + 16-Block Cataclysmic Finisher!) ---

    private static void renderWaveEye(SkillVfx.Track t, long age, long now, List<SpriteQuad> sp, Vec3 camera) {
        Vec3 origin = vector(t.start.origin());
        boolean ownFp = isOwnFirstPerson(t.start.caster());

        // (a) Activation Burst (!sweep): Dark Sky Takeover + Giant Eye Opening!
        if (!t.start.sweep()) {
            if (age >= 900L) return;
            double u = Math.clamp(age / 900.0, 0.0, 0.999);
            double fade = 1.0 - u;
            Vec3 pos = liveCasterPos(t.start.caster(), origin);
            int openFrame = Math.clamp((int) (u * 5.2), 0, 4);

            // Explosive dark-sky domain expansion sweeping out to 16 blocks!
            double expR = 16.5 * EffectSprites.easeOutCubic(Math.min(1.0, u * 1.45));
            EffectSprites.shockwave(sp, pos.add(0, 0.12, 0), expR, u * 1.8, COL_EYE_VIOLET, (int) (245 * fade));
            EffectSprites.shockwave(sp, pos.add(0, 0.90, 0), expR * 0.72, -u * 1.4, COL_EYE_GOLD, (int) (210 * fade));

            // Dramatic giant eye opening right overhead on activation
            Vec3 eyePos = pos.add(0, ownFp ? 3.5 : 3.0, 0);
            emitFramedBillboard(sp, TEX_EYE_IRIS, 8, openFrame, eyePos, camera, (ownFp ? 1.8 : 2.8) * (0.75 + 0.35 * u), COL_WHITE, COL_EYE_LILAC, (int) (245 * fade), 0.75);

            // 6 erupting dark void mist bursts & purple lightning pillars ringing the caster
            int mistFrame = (int) Math.floorMod(now / 95L, 4L);
            for (int i = 0; i < 6; i++) {
                double ang = i * (Math.TAU / 6.0) + u * 0.5;
                double dist = 1.8 + 5.5 * EffectSprites.easeOutCubic(u);
                Vec3 pt = pos.add(Math.cos(ang) * dist, 1.0 + 1.4 * u, Math.sin(ang) * dist);
                emitFramedBillboard(sp, TEX_VOID_MIST, 4, (mistFrame + i) & 3, pt, camera, 2.4, COL_WHITE, COL_EYE_VIOLET, (int) (200 * fade), 0.30);
                if (i % 2 == 0) {
                    LightningBolt.bolt(sp, pt.add(0, -1.0, 0), pt.add(0, 3.8, 0), camera, (now / 50L) ^ (i * 89L), 6, 0.40, 0.068, COL_EYE_LILAC, (int) (225 * fade), false);
                }
            }
            return;
        }

        // (b) Finisher — 炸眼 Phase A: 0.8s Giant Eye Overcharge & 16-Block Convergence!
        if (t.last.phase() != FINISH) {
            double chargeU = Math.clamp(age / 800.0, 0.0, 1.0);
            Vec3 eyePos = origin.add(0, 3.45 + 0.35 * chargeU, 0);
            double eyeScale = 2.45 + 1.75 * EffectSprites.easeOutCubic(chargeU);
            int eyeFrame = chargeU < 0.25 ? 3 : chargeU < 0.55 ? 4 : chargeU < 0.78 ? 5 : 6;

            // Heavy pulsing 16-block abyssal domain + contracting convergence shockwaves
            EffectSprites.emitGround(sp, TEX_EYE_DOMAIN, Blend.TRANSLUCENT, origin.add(0, 0.05, 0), 16.2, now * 0.0014, 0f, 0f, 1f, 1f, COL_WHITE, 238);
            EffectSprites.emitGround(sp, TEX_EYE_DOMAIN, Blend.ADDITIVE, origin.add(0, 0.07, 0), 16.2, -now * 0.0018, 0f, 0f, 1f, 1f, COL_EYE_VIOLET, (int) (155 + 85 * chargeU));

            double pull = 1.0 - ((age % 320L) / 320.0);
            EffectSprites.shockwave(sp, origin.add(0, 0.14, 0), 16.0 * pull, -chargeU * 2.2, COL_EYE_LILAC, (int) (215 * (1.0 - pull * 0.4)));
            EffectSprites.shockwave(sp, origin.add(0, 0.22, 0), 9.5 * pull, chargeU * 2.0, COL_EYE_GOLD, (int) (195 * (1.0 - pull * 0.4)));

            // Towering Giant Dark Sky Wave Eye charging directly overhead!
            emitFramedBillboard(sp, TEX_EYE_IRIS, 8, eyeFrame, eyePos, camera, eyeScale, COL_WHITE, chargeU > 0.72 ? COL_EYE_GOLD : COL_EYE_LILAC, 255, 0.85);
            EffectSprites.glow(sp, eyePos, camera, eyeScale * (1.1 + 0.45 * chargeU), chargeU > 0.72 ? COL_EYE_GOLD : COL_EYE_VIOLET, (int) (185 + 65 * chargeU));

            // 6 high-voltage violet-gold lightning arcs siphoning from the 16-block perimeter UP into the Giant Eye!
            for (int i = 0; i < 6; i++) {
                double ang = i * (Math.TAU / 6.0) + now * 0.0022;
                double rEdge = 15.0 * (1.0 - 0.35 * chargeU);
                Vec3 edge = origin.add(Math.cos(ang) * rEdge, 0.25, Math.sin(ang) * rEdge);
                LightningBolt.bolt(sp, edge, eyePos, camera, (now / 48L) ^ t.start.seed() ^ (i * 131L), 8, 0.75, 0.085, i % 2 == 0 ? COL_EYE_GOLD : COL_EYE_LILAC, 225, false);
            }
            return;
        }

        // (c) Finisher — 炸眼 Phase B: Cataclysmic 16-Block Eye Shatter & Thunder-Void Detonation!
        long finAge = (long) Math.max(0L, (now - t.last.at()) * t.speed);
        if (finAge >= 780L) return;
        double u = Math.clamp(finAge / 780.0, 0.0, 0.999);
        double fastU = Math.clamp(finAge / 420.0, 0.0, 0.999);
        double fade = 1.0 - u;
        double blastR = 16.5 * EffectSprites.easeOutCubic(Math.min(1.0, u * 1.35));
        Vec3 eyePos = origin.add(0, 3.7, 0);

        // Giant Shattering Wave Eye (Frame 7) bursting apart overhead!
        if (finAge < 520L) {
            double eyeFade = 1.0 - finAge / 520.0;
            emitFramedBillboard(sp, TEX_EYE_IRIS, 8, 7, eyePos, camera, 4.4 + 2.8 * EffectSprites.easeOutCubic(fastU), COL_WHITE, COL_EYE_GOLD, (int) (255 * eyeFade), 0.98);
            EffectSprites.glow(sp, eyePos, camera, 7.5 * eyeFade, COL_EYE_GOLD, (int) (245 * eyeFade));
        }

        // Full 16-block blazing dark-sky domain + gigantic violet-gold lotus detonation
        EffectSprites.emitGround(sp, TEX_EYE_DOMAIN, Blend.TRANSLUCENT, origin.add(0, 0.05, 0), 16.4, u * 0.8, 0f, 0f, 1f, 1f, COL_WHITE, (int) (235 * fade));
        EffectSprites.emitGround(sp, TEX_EYE_DOMAIN, Blend.ADDITIVE, origin.add(0, 0.08, 0), 16.4, -u * 1.1, 0f, 0f, 1f, 1f, COL_EYE_LILAC, (int) (235 * fade));
        int burstFrame = Math.clamp((int) (u * 8), 0, 7);
        emitGroundFrame(sp, TEX_AGNI_LOTUS, 8, burstFrame, origin.add(0, 0.14, 0), blastR * 0.98, u * 0.9, COL_EYE_VIOLET, COL_EYE_GOLD, (int) (245 * fade), 0.92);

        // Triple colossal expanding 16-block shockwaves (ground, mid-air, high sky)
        EffectSprites.shockwave(sp, origin.add(0, 0.16, 0), blastR, u * 1.8, COL_EYE_LILAC, (int) (252 * fade));
        EffectSprites.shockwave(sp, origin.add(0, 1.65, 0), blastR * 0.88, -u * 1.5, COL_EYE_GOLD, (int) (235 * fade));
        EffectSprites.shockwave(sp, origin.add(0, 3.40, 0), blastR * 0.72, u * 2.2, COL_EYE_VIOLET, (int) (215 * fade));

        // 12 radial sky-to-earth thunder storm strikes + 6 erupting violet lightning spires across the 16-block arena!
        for (int i = 0; i < 12; i++) {
            double ang = i * (Math.TAU / 12.0) + u * 0.25;
            Vec3 edge = origin.add(Math.cos(ang) * blastR, 0.35 + 1.1 * Math.sin(i * 2.1), Math.sin(ang) * blastR);
            LightningBolt.bolt(sp, eyePos, edge, camera, t.start.seed() ^ (i * 199L) ^ (finAge / 45L), 8, 0.85, 0.095, i % 3 == 0 ? COL_EYE_GOLD : COL_EYE_LILAC, (int) (235 * fade), false);
            if ((i & 1) == 0) {
                Vec3 spireBase = origin.add(Math.cos(ang) * (blastR * 0.65), 0.04, Math.sin(ang) * (blastR * 0.65));
                emitAxialSpike(sp, TEX_EYE_SPEAR, burstFrame, spireBase, UP, 1.25, 6.2, ang, COL_WHITE, COL_EYE_GOLD, (int) (235 * fade), 0.85);
            }
        }
    }

    // --- 8. 波动眼普攻旋刃车轮 (Wave Eye Basic Attack Spinning Scythe-Wheel) ---

    private static void renderEyeGear(SkillVfx.Track t, long age, long now, List<SpriteQuad> sp, Vec3 camera) {
        Vec3 p = vector(t.last.position());
        Vec3 dir = horizontalDir(vector(t.start.direction()));
        Vec3 right = rightOf(dir);

        double fade = 1.0;
        if (t.last.phase() == FINISH) {
            long endAge = Math.max(0L, now - t.last.at());
            if (endAge >= 260L) return;
            fade = 1.0 - endAge / 260.0;
        }
        int frame = (int) Math.floorMod(now / 42L, 4L);
        double spin = age * 0.016;
        double rad = 0.96 * (0.85 + 0.15 * fade);
        int alpha = Math.clamp((int) Math.round(248 * fade), 0, 255);

        // Primary vertical spinning scythe-wheel aligned along flight direction (sawblade slicing forward!)
        double c = Math.cos(spin) * rad;
        double s = Math.sin(spin) * rad;
        Vec3 hu1 = dir.scale(c).add(UP.scale(s));
        Vec3 hv1 = UP.scale(c).subtract(dir.scale(s));
        float u0 = frame / 4.0f;
        float u1 = (frame + 1) / 4.0f;
        EffectSprites.emitPlane(sp, TEX_EYE_GEAR, Blend.TRANSLUCENT, p, hu1, hv1, u0, 0f, u1, 1f, COL_WHITE, alpha);
        EffectSprites.emitPlane(sp, TEX_EYE_GEAR, Blend.ADDITIVE, p.add(right.scale(0.02)), hu1.scale(1.04), hv1.scale(1.04), u0, 0f, u1, 1f, COL_EYE_LILAC, (int) (alpha * 0.78));

        // Secondary tilted horizontal chakram disc so the wheel stays bold & razor-sharp from front, rear & top!
        Vec3 tiltUp = UP.scale(0.32).add(right.scale(0.95)).normalize();
        Vec3 hu2 = dir.scale(c * 0.88).add(tiltUp.scale(s * 0.88));
        Vec3 hv2 = tiltUp.scale(c * 0.88).subtract(dir.scale(s * 0.88));
        EffectSprites.emitPlane(sp, TEX_EYE_GEAR, Blend.TRANSLUCENT, p, hu2, hv2, u0, 0f, u1, 1f, COL_WHITE, (int) (alpha * 0.82));
        EffectSprites.emitPlane(sp, TEX_EYE_GEAR, Blend.ADDITIVE, p, hu2.scale(1.04), hv2.scale(1.04), u0, 0f, u1, 1f, COL_EYE_VIOLET, (int) (alpha * 0.65));

        EffectSprites.glow(sp, p, camera, 0.82 * fade, COL_EYE_GOLD, (int) (180 * fade));
        RibbonTrail.push("asura_gear:" + t.start.cast(), p, 0.30 * fade, COL_EYE_VIOLET, (int) (215 * fade), 220L, now);
    }

    // --- 暗天波动眼终结「炸眼」 Cinematic 3rd-Person Camera --------------------

    private static SkillVfx.CameraPose eyeFinisherCamera(SkillVfx.Track t, long time) {
        var mc = Minecraft.getInstance();
        if (t == null || mc == null || mc.level == null) return null;
        long age = Math.max(0L, time - t.start.startedAt());
        double chargePush = Math.clamp(age / 800.0, 0.0, 1.0);
        double blastPull = Math.clamp((age - 780.0) / 550.0, 0.0, 1.0);
        Vec3 origin = vector(t.start.origin());
        Vec3 dir = horizontalDir(vector(t.start.direction()));
        Vec3 side = rightOf(dir);

        // Start dramatic hero low-medium angle framing Asura + Giant Eye overhead, then sweep wide & high for 16-block blast!
        Vec3 target = origin.add(0, 2.55 + 0.65 * chargePush, 0);
        double backDist = 8.4 - 0.8 * chargePush + 5.6 * EffectSprites.easeOutCubic(blastPull);
        double sideDist = 4.2 + 2.4 * blastPull;
        double height = 4.4 + 0.6 * chargePush + 3.8 * EffectSprites.easeOutCubic(blastPull);
        Vec3 camPos = origin.subtract(dir.scale(backDist)).add(side.scale(sideDist)).add(0, height, 0);

        if (mc.player != null) {
            var wall = mc.level.clip(new ClipContext(target, camPos, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
            if (wall.getType() != HitResult.Type.MISS) {
                camPos = wall.getLocation().add(target.subtract(camPos).normalize().scale(0.32));
            }
        }
        Vec3 look = target.subtract(camPos);
        float yaw = (float) Math.toDegrees(Math.atan2(-look.x, look.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(look.y, look.horizontalDistance()));
        return new SkillVfx.CameraPose(camPos, yaw, pitch);
    }

    // --- Reusable Dual-Layer Sprite Emitters ---------------------------------

    /** Forward-facing directional wave plane (u1=+forward, v spans ±rolled-side). */
    private static void emitDirectionalWavePlane(
        List<SpriteQuad> out, Identifier texture, int frame,
        Vec3 center, Vec3 forward, double rollDeg, double radius,
        int baseRgb, int boostRgb, int alpha, double boostScale
    ) {
        if (radius <= 0.0 || alpha <= 4) return;
        Vec3 dir = horizontalDir(forward);
        Vec3 right0 = rightOf(dir);
        double roll = Math.toRadians(rollDeg);
        Vec3 side = right0.scale(Math.cos(roll)).add(UP.scale(Math.sin(roll))).normalize();
        float u0 = frame / 8.0f;
        float u1 = (frame + 1) / 8.0f;
        Vec3 halfU = dir.scale(radius);
        Vec3 halfV = side.scale(radius);
        int a = Math.clamp(alpha, 0, 255);

        EffectSprites.emitPlane(out, texture, Blend.TRANSLUCENT, center, halfU, halfV, u0, 0f, u1, 1f, baseRgb, a);
        int ba = Math.clamp((int) Math.round(a * boostScale), 0, 255);
        if (ba > 4) {
            EffectSprites.emitPlane(out, texture, Blend.ADDITIVE, center, halfU.scale(1.03), halfV.scale(1.03), u0, 0f, u1, 1f, boostRgb, ba);
        }
    }

    /** Ground-anchored N-plane volumetric vertical spike / lava pillar (v=0 sky top, v=1 ground base). */
    private static void emitVerticalCrossed(
        List<SpriteQuad> out, Identifier texture, int frame,
        Vec3 base, double halfWidth, double height, double yawOffset, int planes,
        int baseRgb, int boostRgb, int alpha, double boostScale
    ) {
        if (halfWidth <= 0.0 || height <= 0.0 || alpha <= 4) return;
        float u0 = frame / 8.0f;
        float u1 = (frame + 1) / 8.0f;
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

    /** Ground-anchored dual-crossed angled spear / piercing thorn along arbitrary `axis` (base -> tip). */
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
                EffectSprites.emitPlane(out, texture, Blend.ADDITIVE, center, halfU.scale(1.04), halfV, u0, 0f, u1, 1f, boostRgb, ba);
            }
        }
    }

    /** Dual-layer single-frame ground decal / mandala / crater. */
    private static void emitGroundSprite(
        List<SpriteQuad> out, Identifier texture, Vec3 center, double radius, double rotRad,
        int baseRgb, int boostRgb, int alpha, double boostScale
    ) {
        if (radius <= 0.0 || alpha <= 4) return;
        int a = Math.clamp(alpha, 0, 255);
        EffectSprites.emitGround(out, texture, Blend.TRANSLUCENT, center, radius, rotRad, 0f, 0f, 1f, 1f, baseRgb, a);
        int ba = Math.clamp((int) Math.round(a * boostScale), 0, 255);
        if (ba > 4) {
            EffectSprites.emitGround(out, texture, Blend.ADDITIVE, center.add(0, 0.012, 0), radius * 0.98, rotRad, 0f, 0f, 1f, 1f, boostRgb, ba);
        }
    }

    /** Dual-layer multi-frame ground burst (e.g. Agni Lotus horizontal shock ring). */
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
            EffectSprites.emitGround(out, texture, Blend.ADDITIVE, center.add(0, 0.015, 0), radius * 1.02, rotRad, u0, 0f, u1, 1f, boostRgb, ba);
        }
    }

    /** Tilted 3D spinning energy ring surrounding orbs (Ghost Orb / Fire Wave capture vortex). */
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

    /** Upright camera-facing billboard for multi-frame horizontal strips (ensuring v=0 stays at top!). */
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
            EffectSprites.emitPlane(out, texture, Blend.ADDITIVE, center.add(look.scale(0.025)), halfU.scale(1.03), halfV.scale(1.03), u0, 0f, u1, 1f, boostRgb, ba);
        }
    }

    // --- Pure Math & Lookup Utilities ----------------------------------------

    private static Vec3 liveCasterPos(UUID casterId, Vec3 fallback) {
        var mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) return fallback;
        Player p = mc.level.getPlayerByUUID(casterId);
        return p != null && p.isAlive() ? PlayerVisualAnchor.position(p) : fallback;
    }

    private static boolean isOwnFirstPerson(UUID casterId) {
        var mc = Minecraft.getInstance();
        return mc != null && mc.player != null && mc.player.getUUID().equals(casterId) && mc.options.getCameraType().isFirstPerson();
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

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }
}
