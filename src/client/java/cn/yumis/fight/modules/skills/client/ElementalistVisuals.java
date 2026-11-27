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
import cn.yumis.fight.modules.skills.api.client.SkillsClient;
import com.google.gson.JsonObject;
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
 * Dedicated DNF-inspired Elementalist / Grand Archmage (元素师 / 大魔导师) ultra-spectacle VFX renderer.
 * Zero legacy untextured WorldQuads: 100% custom high-contrast multi-tone pixel-sharp + soft-feathered
 * sprite sheets (source: tools/build_elementalist_effect_textures.py), dual-layer translucent silhouette
 * + additive luminous core rendering, ornate orbiting 4-element Elemental Burn medallions, staff-tip
 * Moving Cast charge convergence, DNF flaming laughing Jack-O'-Lanterns (Jack Bomb & Halloween Buster),
 * crystalline shattering Ice Walls, high-voltage Lightning Vortex webs, abyssal Void Spheres, towering
 * Flame Pillars, crystal-eruption Arctic Feast, tilted galactic-accretion Black Holes, sky-splitting
 * Lightning Strikes, and full Astral Storm Awakening with overhead Celestial Rift, 4-element meteors,
 * descending Chaotic Main Star Core, prismatic supernova, and multi-stage cinematic camera.
 * Pure client presentation: never grants combat authority, hitboxes, or physics changes.
 */
final class ElementalistVisuals {
    private static final Vec3 UP = new Vec3(0, 1, 0);

    static final long FLAME_ERUPTION_MS = 860L;
    static final long ARCTIC_FINISH_MS = 820L;
    static final long ASTRAL_FINISH_MS = 1250L;
    private static final long ICE_PILLAR_LIFETIME_MS = 560L;
    private static final long METEOR_IMPACT_MS = 680L;
    private static final long CHAOS_CORE_DESCENT_MS = 650L;

    // Custom pre-baked multi-tone Elementalist sprite sheets (source: tools/build_elementalist_effect_textures.py)
    static final Identifier TEX_RUNE_LAMP = tex("rune_lamp_orb.png");
    static final Identifier TEX_CHARGE_CIRCLE = tex("charge_arcane_circle.png");
    static final Identifier TEX_ICE_CIRCLE = tex("ice_hexagram_circle.png");
    static final Identifier TEX_THUNDER_CIRCLE = tex("thunder_reticle_circle.png");
    static final Identifier TEX_VOLCANO_CRATER = tex("volcano_magma_crater.png");
    static final Identifier TEX_ASTRAL_CIRCLE = tex("astral_star_circle.png");
    static final Identifier TEX_ICE_WALL = tex("ice_wall_pillar.png");
    static final Identifier TEX_VORTEX_ORB = tex("lightning_vortex_orb.png");
    static final Identifier TEX_VOID_ORB = tex("void_sphere_orb.png");
    static final Identifier TEX_FLAME_PILLAR = tex("flame_pillar_column.png");
    static final Identifier TEX_JACK_PUMPKIN = tex("jack_pumpkin_head.png");
    static final Identifier TEX_JACK_BURST = tex("jack_lava_burst.png");
    static final Identifier TEX_ARCTIC_SPIRE = tex("arctic_ice_spire.png");
    static final Identifier TEX_BLACK_HOLE_CORE = tex("black_hole_core.png");
    static final Identifier TEX_BLACK_HOLE_DISK = tex("black_hole_accretion.png");
    static final Identifier TEX_SKY_LIGHTNING = tex("sky_lightning_pillar.png");
    static final Identifier TEX_ASTRAL_RIFT = tex("astral_sky_rift.png");
    static final Identifier TEX_ASTRAL_METEOR = tex("astral_meteor_rock.png");
    static final Identifier TEX_ASTRAL_CORE = tex("astral_chaos_core.png");
    static final Identifier TEX_ASTRAL_SUPERNOVA = tex("astral_supernova_burst.png");
    static final Identifier TEX_FROST_MIST = tex("frost_blizzard_mist.png");

    // Classic DNF Elementalist palette accents (preserving authentic innate elemental hues)
    private static final int COL_WHITE = 0xFFFFFF;
    private static final int COL_LIGHT = 0xFFE052;
    private static final int COL_LIGHT_HOT = 0xFFFBE0;
    private static final int COL_LIGHT_CYAN = 0xB8F4FF;
    private static final int COL_FIRE = 0xFF741E;
    private static final int COL_FIRE_HOT = 0xFFE27A;
    private static final int COL_FIRE_DEEP = 0xC8280A;
    private static final int COL_ICE = 0x66D9FF;
    private static final int COL_ICE_HOT = 0xE6FAFF;
    private static final int COL_ICE_DEEP = 0x1E68C8;
    private static final int COL_DARK = 0xA855FF;
    private static final int COL_DARK_HOT = 0xE8C8FF;
    private static final int COL_DARK_DEEP = 0x260A4A;
    private static final int COL_PRISM = 0xE4C8FF;
    private static final int COL_ASTRAL_GOLD = 0xFFD96B;
    private static final int COL_CLOUD_DARK = 0x242A42;

    private record FallingMeteor(Vec3 impact, long startedAt, int element, int sequence) {}
    private record ChainArc(Vec3 from, Vec3 to, long at, int sequence) {}

    private static final class Meta {
        int element = -1;
        int morphMode = 0;
        double radius = 3.5;
        double blastRadius = 5.0;
        double pullRadius = 7.5;
        double range = 12.0;
        double width = 4.0;
        double speed = 3.0;
        double wallHeight = 2.2;
        int wallSegments = 12;
        double wallShieldRatio = 1.0;
        long wallHitAt = 0L;
        int orbCount = 4;
        boolean follow;
        boolean stationary;
        boolean cloud;
        boolean volcano;
        boolean prism;
        boolean mini;
        boolean homing;
        boolean cinematic = true;
        long fallMs = 550L;
        long emberMs = 0L;
        long volcanoMs = 5000L;
        long travelMs = 650L;
        long subHoleMs = 0L;
        long impactDelayMs = 200L;
        long firstWaveMs = 120L;
        long waveIntervalMs = 220L;
        int waveCount = 8;
        long nextWaveSound;
        double fieldRadius = 10.0;
        double meteorRadius = 4.2;
        double finisherRadius = 11.0;
        final List<FallingMeteor> meteors = new ArrayList<>();
        final List<ChainArc> chainArcs = new ArrayList<>();
        final List<Long> resonanceFlashes = new ArrayList<>();
        long updatedAt;
    }

    private static boolean activePrism;
    private static long activeNow;

    private static final Meta DEFAULT_META = new Meta();
    private static final Map<UUID, Meta> METAS = new LinkedHashMap<>();
    private static final Map<UUID, Integer> LAST_LAMP_MASK = new HashMap<>();
    private static final Map<UUID, long[]> LAMP_LIT_AT = new HashMap<>();
    private static String lastFullChargeCastId = "";

    private ElementalistVisuals() {}

    private static Identifier tex(String name) {
        return Identifier.fromNamespaceAndPath("yumis_dungeon_and_fight", "textures/effect/elementalist/" + name);
    }

    static void clear() {
        METAS.clear();
        LAST_LAMP_MASK.clear();
        LAMP_LIT_AT.clear();
        lastFullChargeCastId = "";
        activePrism = false;
    }

    static void recordJackMotion(UUID castId, JsonObject j) {
        if (castId == null || j == null) return;
        Meta m = METAS.computeIfAbsent(castId, k -> new Meta());
        m.updatedAt = ClientUi.now();
        if (j.has("prism")) m.prism = j.get("prism").getAsBoolean();
        if (j.has("mini")) m.mini = j.get("mini").getAsBoolean();
        if (j.has("homing")) m.homing = j.get("homing").getAsBoolean();
        if (j.has("radius")) m.radius = j.get("radius").getAsDouble();
        if (METAS.size() > 512) METAS.remove(METAS.keySet().iterator().next());
    }

    static void install() {
        for (String id : ElementalistSkills.ALL) {
            boolean isStorm = ElementalistSkills.ASTRAL_STORM.equals(id);
            boolean isArctic = ElementalistSkills.ARCTIC_FEAST.equals(id);
            SkillVisualRegistry.register(id, new SkillVisualRegistry.Handler() {
                @Override public long defaultDuration() { return isStorm ? 5250L : 2600L; }

                @Override public long finishedUntil(SkillVfx.Track track, SkillVisualEvent event) {
                    if (isStorm) return event.at() + ASTRAL_FINISH_MS;
                    if (isArctic) return event.at() + ARCTIC_FINISH_MS;
                    if (ElementalistSkills.ICE_WALL.equals(id) || ElementalistSkills.VOID_SPHERE.equals(id)) {
                        return event.at() + 680L;
                    }
                    if (ElementalistSkills.LIGHTNING_STRIKE.equals(id)) {
                        return event.at() + 720L;
                    }
                    if (ElementalistSkills.LIGHTNING_VORTEX.equals(id)) {
                        return event.at() + 450L;
                    }
                    if (ElementalistSkills.BLACK_HOLE.equals(id)) {
                        Meta m = METAS.get(track.start.cast());
                        return event.at() + Math.max(850L, m == null ? 0L : m.subHoleMs);
                    }
                    return SkillVisualRegistry.Handler.super.finishedUntil(track, event);
                }

                @Override public int defaultColor() {
                    return ElementalistRules.elementColor(ElementalistSkills.innateElement(id));
                }

                @Override public int handOrbColor() {
                    int e = ElementalistSkills.innateElement(id);
                    return e >= 0 ? ElementalistRules.elementColor(e) : ElementalistRules.COLOR_PRISM;
                }

                @Override public boolean cinematic() { return isStorm; }
                @Override public boolean cinematicFromFirstPerson() { return isStorm; }
                @Override public boolean restoreCamera() { return isStorm; }

                @Override public CameraType cameraType(SkillVfx.Track track, long time) {
                    return CameraType.THIRD_PERSON_BACK;
                }

                @Override public SkillVfx.CameraPose camera(SkillVfx.Track track, long time) {
                    if (!isStorm) return null;
                    Meta m = METAS.get(track.start.cast());
                    if (m != null && !m.cinematic) return null;
                    return astralStormCamera(track, time);
                }

                @Override public boolean motion(SkillVfx.Track track, JsonObject j) {
                    Meta m = METAS.computeIfAbsent(track.start.cast(), k -> new Meta());
                    m.updatedAt = ClientUi.now();
                    if (j.has("prism")) m.prism = j.get("prism").getAsBoolean();
                    if (isStorm && track.last.phase() == HIT) {
                        long startedAt = track.last.at();
                        int elem = j.has("element") ? j.get("element").getAsInt() : Math.floorMod(track.last.sequence(), 4);
                        if (elem < 0 || elem > 3) elem = Math.floorMod(track.last.sequence(), 4);
                        m.meteors.removeIf(meteor -> startedAt - meteor.startedAt() >= m.fallMs + METEOR_IMPACT_MS);
                        if (m.meteors.size() >= 36) m.meteors.removeFirst();
                        m.meteors.add(new FallingMeteor(vector(track.last.position()), startedAt, elem, track.last.sequence()));
                        return true;
                    }
                    if (ElementalistSkills.LIGHTNING_VORTEX.equals(id) && j.has("chainArc") && j.get("chainArc").getAsBoolean()) {
                        long at = track.last.at();
                        m.chainArcs.removeIf(a -> at - a.at() >= 320L);
                        if (m.chainArcs.size() >= 24) m.chainArcs.removeFirst();
                        Vec3 from = vector(track.last.position());
                        Vec3 to = new Vec3(j.get("tx").getAsDouble(), j.get("ty").getAsDouble(), j.get("tz").getAsDouble());
                        m.chainArcs.add(new ChainArc(from, to, at, track.last.sequence()));
                        return true;
                    }
                    if (ElementalistSkills.BLACK_HOLE.equals(id) && j.has("resonance") && j.get("resonance").getAsBoolean()) {
                        long at = track.last.at();
                        m.resonanceFlashes.removeIf(tAt -> at - tAt >= 520L);
                        if (m.resonanceFlashes.size() >= 12) m.resonanceFlashes.removeFirst();
                        m.resonanceFlashes.add(at);
                        if (j.has("blastRadius")) m.blastRadius = j.get("blastRadius").getAsDouble();
                        return true;
                    }
                    if (j.has("element")) m.element = j.get("element").getAsInt();
                    if (j.has("morphMode")) m.morphMode = j.get("morphMode").getAsInt();
                    if (j.has("radius")) m.radius = j.get("radius").getAsDouble();
                    if (j.has("blastRadius")) m.blastRadius = j.get("blastRadius").getAsDouble();
                    if (j.has("pullRadius")) m.pullRadius = j.get("pullRadius").getAsDouble();
                    if (j.has("range")) m.range = j.get("range").getAsDouble();
                    if (j.has("width")) m.width = j.get("width").getAsDouble();
                    if (j.has("speed")) m.speed = Math.max(0.0, j.get("speed").getAsDouble());
                    if (j.has("wallHeight")) m.wallHeight = j.get("wallHeight").getAsDouble();
                    if (j.has("wallSegments")) m.wallSegments = Math.max(6, (int) Math.round(j.get("wallSegments").getAsDouble()));
                    if (j.has("wallShieldRatio")) m.wallShieldRatio = Math.clamp(j.get("wallShieldRatio").getAsDouble(), 0.0, 1.0);
                    if (ElementalistSkills.ICE_WALL.equals(id) && track.last.phase() == HIT) m.wallHitAt = track.last.at();
                    if (j.has("orbCount")) m.orbCount = Math.max(2, (int) Math.round(j.get("orbCount").getAsDouble()));
                    if (j.has("follow")) m.follow = j.get("follow").getAsBoolean();
                    if (j.has("stationary")) m.stationary = j.get("stationary").getAsBoolean();
                    if (j.has("cloud")) m.cloud = j.get("cloud").getAsBoolean();
                    if (j.has("volcano")) m.volcano = j.get("volcano").getAsBoolean();
                    if (j.has("volcanoMs")) m.volcanoMs = Math.max(500L, j.get("volcanoMs").getAsLong());
                    if (j.has("cinematic")) m.cinematic = j.get("cinematic").getAsBoolean();
                    if (j.has("fallMs")) m.fallMs = Math.max(80L, j.get("fallMs").getAsLong());
                    if (j.has("emberMs")) m.emberMs = Math.max(0L, j.get("emberMs").getAsLong());
                    if (j.has("travelMs")) m.travelMs = Math.max(150L, j.get("travelMs").getAsLong());
                    if (j.has("subHoleMs")) m.subHoleMs = Math.max(0L, j.get("subHoleMs").getAsLong());
                    if (j.has("shockwaveRadius")) m.blastRadius = j.get("shockwaveRadius").getAsDouble();
                    if (j.has("impactDelayMs")) m.impactDelayMs = Math.max(50L, j.get("impactDelayMs").getAsLong());
                    if (j.has("firstWaveMs")) m.firstWaveMs = Math.max(0L, j.get("firstWaveMs").getAsLong());
                    if (j.has("waveIntervalMs")) m.waveIntervalMs = Math.max(50L, j.get("waveIntervalMs").getAsLong());
                    if (j.has("waveCount")) m.waveCount = Math.max(0, j.get("waveCount").getAsInt());
                    if (j.has("fieldRadius")) m.fieldRadius = j.get("fieldRadius").getAsDouble();
                    if (j.has("meteorRadius")) m.meteorRadius = j.get("meteorRadius").getAsDouble();
                    if (j.has("finisherRadius")) m.finisherRadius = j.get("finisherRadius").getAsDouble();
                    if (METAS.size() > 512) METAS.remove(METAS.keySet().iterator().next());
                    return true;
                }

                @Override public void play(SkillVfx.Track track, SkillVisualEvent event) {
                    playCue(id, track, event);
                }

                @Override public void tick(SkillVfx.Track track, long time) {
                    if (isArctic) playArcticWaves(track, time);
                }

                @Override public void render(SkillVfx.LevelRenderContext ctx, SkillVfx.Track track, long age, long time, List<Quad> out, Vec3 camera) {
                    draw(id, track, age, time, ctx.sprites(), camera);
                }
            });
        }
    }

    // --- Layered Audio, Afterimages & Camera Shake Choreography --------------

    private static void playCue(String id, SkillVfx.Track track, SkillVisualEvent e) {
        Vec3 pos = vector(e.position());
        var mc = Minecraft.getInstance();
        Player actor = mc == null || mc.level == null ? null : mc.level.getPlayerByUUID(e.caster());
        boolean ownFp = isOwnFirstPerson(e.caster());

        switch (id) {
            case ElementalistSkills.ICE_WALL -> {
                if (e.phase() == START) {
                    SkillSound.playAt(e.position(), SoundEvents.GLASS_PLACE, 0.78f, 1.24f);
                    SkillSound.playAt(e.position(), SoundEvents.PLAYER_HURT_FREEZE, 0.65f, 1.15f);
                    CameraShake.impulse(pos, 14, 0.18f, 55L, 120L);
                } else if (e.phase() == HIT) {
                    SkillSound.playAt(e.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 0.58f, 1.52f);
                } else if (e.phase() == FINISH) {
                    SkillSound.playAt(e.position(), SoundEvents.GLASS_BREAK, 0.92f, 0.86f);
                    SkillSound.playAt(e.position(), SoundEvents.AMETHYST_CLUSTER_BREAK, 0.72f, 1.10f);
                    CameraShake.impulse(pos, 18, 0.30f, 80L, 170L);
                }
            }
            case ElementalistSkills.LIGHTNING_VORTEX -> {
                if (e.phase() == START) {
                    SkillSound.playAt(e.position(), SoundEvents.BREEZE_SHOOT, 0.68f, 1.42f);
                    SkillSound.playAt(e.position(), SoundEvents.TRIDENT_THUNDER.value(), 0.48f, 1.48f);
                    CameraShake.impulse(pos, 15, 0.22f, 55L, 120L);
                } else if (e.phase() == FINISH) {
                    SkillSound.playAt(e.position(), SoundEvents.LIGHTNING_BOLT_IMPACT, 0.62f, 1.45f);
                    CameraShake.impulse(pos, 15, 0.20f, 50L, 110L);
                }
            }
            case ElementalistSkills.VOID_SPHERE -> {
                if (e.phase() == START) {
                    SkillSound.playAt(e.position(), SoundEvents.END_PORTAL_FRAME_FILL, 0.78f, 0.78f);
                    SkillSound.playAt(e.position(), SoundEvents.BREEZE_SHOOT, 0.55f, 0.72f);
                    CameraShake.impulse(pos, 14, 0.18f, 50L, 110L);
                } else if (e.phase() == FINISH) {
                    SkillSound.playAt(e.position(), SoundEvents.GENERIC_EXPLODE.value(), 0.72f, 0.88f);
                    SkillSound.playAt(e.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 0.62f, 0.72f);
                    CameraShake.impulse(pos, 18, 0.34f, 85L, 185L);
                }
            }
            case ElementalistSkills.FLAME_PILLAR -> {
                if (e.phase() == START) {
                    SkillSound.playAt(e.position(), SoundEvents.FIRECHARGE_USE, 0.72f, 0.92f);
                } else if (e.phase() == HIT) {
                    SkillSound.playAt(e.position(), SoundEvents.BLAZE_SHOOT, 0.88f, 0.78f);
                    SkillSound.playAt(e.position(), SoundEvents.GENERIC_EXPLODE.value(), 0.75f, 0.82f);
                    CameraShake.impulse(pos, 20, 0.38f, 95L, 200L);
                }
            }
            case ElementalistSkills.HALLOWEEN_BUSTER -> {
                if (e.phase() == START) {
                    SkillSound.playAt(e.position(), SoundEvents.FIRECHARGE_USE, 0.85f, 0.68f);
                    SkillSound.playAt(e.position(), SoundEvents.BLAZE_SHOOT, 0.72f, 0.76f);
                    if (actor != null && !ownFp) PlayerAfterimages.capture(actor, COL_FIRE, 195, 380L, true);
                } else if (e.phase() == HIT) {
                    SkillSound.playAt(e.position(), SoundEvents.GENERIC_EXPLODE.value(), 1.0f, 0.68f);
                    SkillSound.playAt(e.position(), SoundEvents.MACE_SMASH_GROUND_HEAVY, 0.88f, 0.72f);
                    CameraShake.impulse(pos, 26, 0.56f, 130L, 270L);
                }
            }
            case ElementalistSkills.ARCTIC_FEAST -> {
                if (e.phase() == START) {
                    SkillSound.playAt(e.position(), SoundEvents.PLAYER_HURT_FREEZE, 0.82f, 0.92f);
                    SkillSound.playAt(e.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 0.72f, 1.28f);
                    CameraShake.impulse(pos, 18, 0.26f, 80L, 165L);
                    if (actor != null && !ownFp) PlayerAfterimages.capture(actor, COL_ICE, 195, 380L, true);
                } else if (e.phase() == HIT) {
                    SkillSound.playAt(e.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 0.65f, 1.35f);
                    CameraShake.impulse(pos, 18, 0.22f, 60L, 130L);
                } else if (e.phase() == FINISH) {
                    SkillSound.playAt(e.position(), SoundEvents.GLASS_BREAK, 1.0f, 0.72f);
                    SkillSound.playAt(e.position(), SoundEvents.GENERIC_EXPLODE.value(), 0.82f, 0.88f);
                    CameraShake.impulse(pos, 26, 0.52f, 130L, 260L);
                }
            }
            case ElementalistSkills.BLACK_HOLE -> {
                if (e.phase() == START) {
                    SkillSound.playAt(e.position(), SoundEvents.BEACON_ACTIVATE, 0.88f, 0.58f);
                    SkillSound.playAt(e.position(), SoundEvents.WITHER_SPAWN, 0.42f, 0.85f);
                    CameraShake.impulse(pos, 22, 0.34f, 100L, 210L);
                    if (actor != null && !ownFp) PlayerAfterimages.capture(actor, COL_DARK, 205, 420L, true);
                } else if (e.phase() == HIT) {
                    SkillSound.playAt(e.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 0.85f, 0.72f);
                    SkillSound.playAt(e.position(), SoundEvents.GENERIC_EXPLODE.value(), 0.65f, 0.92f);
                    CameraShake.impulse(pos, 22, 0.34f, 80L, 165L);
                } else if (e.phase() == FINISH) {
                    SkillSound.playAt(e.position(), SoundEvents.GENERIC_EXPLODE.value(), 1.0f, 0.58f);
                    SkillSound.playAt(e.position(), SoundEvents.MACE_SMASH_GROUND_HEAVY, 0.90f, 0.65f);
                    CameraShake.impulse(pos, 28, 0.62f, 150L, 300L);
                }
            }
            case ElementalistSkills.LIGHTNING_STRIKE -> {
                if (e.phase() == START) {
                    SkillSound.playAt(e.position(), SoundEvents.BEACON_POWER_SELECT, 0.78f, 1.38f);
                    if (actor != null && !ownFp) PlayerAfterimages.capture(actor, COL_LIGHT, 200, 380L, true);
                } else if (e.phase() == HIT) {
                    SkillSound.playAt(e.position(), SoundEvents.LIGHTNING_BOLT_THUNDER, 0.88f, 1.12f);
                    SkillSound.playAt(e.position(), SoundEvents.TRIDENT_THUNDER.value(), 0.78f, 1.08f);
                    CameraShake.impulse(pos, 24, 0.46f, 105L, 220L);
                }
            }
            case ElementalistSkills.ASTRAL_STORM -> {
                if (e.phase() == START) {
                    SkillSound.playAt(e.position(), SoundEvents.BEACON_ACTIVATE, 1.0f, 0.76f);
                    SkillSound.playAt(e.position(), SoundEvents.ENDER_DRAGON_GROWL, 0.45f, 0.88f);
                    SkillSound.playAt(e.position(), SoundEvents.BELL_RESONATE, 0.85f, 1.05f);
                    CameraShake.impulse(pos, 30, 0.42f, 130L, 260L);
                    if (actor != null) PlayerAfterimages.capture(actor, COL_ASTRAL_GOLD, 225, 560L, true);
                } else if (e.phase() == HIT) {
                    SkillSound.playAt(e.position(), SoundEvents.FIREWORK_ROCKET_BLAST, 0.68f, 0.84f);
                    SkillSound.playAt(e.position(), SoundEvents.BLAZE_SHOOT, 0.42f, 1.12f);
                    CameraShake.impulse(pos, 24, 0.28f, 65L, 145L);
                } else if (e.phase() == FINISH) {
                    SkillSound.playAt(e.position(), SoundEvents.GENERIC_EXPLODE.value(), 1.0f, 0.50f);
                    SkillSound.playAt(e.position(), SoundEvents.TRIDENT_THUNDER.value(), 1.0f, 0.64f);
                    SkillSound.playAt(e.position(), SoundEvents.MACE_SMASH_GROUND_HEAVY, 1.0f, 0.56f);
                    SkillSound.playAt(e.position(), SoundEvents.BELL_RESONATE, 0.88f, 0.72f);
                    CameraShake.impulse(pos, 38, 0.86f, 220L, 420L);
                }
            }
            default -> {}
        }
    }

    // --- Persistent Actor Visuals: 4-Element Medallions, Staff-Tip Charge, Aiming Rings ---

    static void actors(List<SpriteQuad> sp, Vec3 camera, long serverNow, long renderNow) {
        var mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) return;

        // 1. 圣灵符文 (Elemental Burn: 4 ornate elemental rune medallions orbiting crown/shoulders + 4-lamp resonance)
        for (var actor : CareerClient.INSTANCE.view.actors) {
            UUID ownerId;
            try {
                ownerId = UUID.fromString(actor.owner);
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            int mask = actor.elementLampsMask & 0xF;
            Integer prevMaskObj = LAST_LAMP_MASK.put(ownerId, mask);
            int prevMask = prevMaskObj == null ? mask : prevMaskObj;
            if (mask == 0) {
                LAMP_LIT_AT.remove(ownerId);
                continue;
            }
            Player player = mc.level.getPlayerByUUID(ownerId);
            if (player == null || !player.isAlive()) continue;

            long[] litTimes = LAMP_LIT_AT.computeIfAbsent(ownerId, k -> new long[4]);
            int newlyLit = mask & ~prevMask;
            if (newlyLit != 0) {
                Vec3 pPos = PlayerVisualAnchor.position(player);
                for (int e = 0; e < 4; e++) {
                    if ((newlyLit & (1 << e)) != 0) {
                        litTimes[e] = renderNow;
                        WorldEffects.playShockwave(pPos.add(0, 1.95, 0), 0.30, 1.90, elemColor(e), 220, 320L);
                    }
                }
                if (mask == 0xF && prevMask != 0xF) {
                    WorldEffects.playShockwave(pPos.add(0, 0.14, 0), 0.50, 3.40, COL_ASTRAL_GOLD, 245, 420L);
                    SkillSound.playAt(pPos.x, pPos.y + 1.4, pPos.z, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.65f, 1.45f);
                } else {
                    SkillSound.playAt(pPos.x, pPos.y + 1.5, pPos.z, SoundEvents.AMETHYST_BLOCK_CHIME, 0.45f, 1.25f + Integer.bitCount(mask) * 0.08f);
                }
            }

            boolean ownFp = isOwnFirstPerson(ownerId);
            boolean ownerPrism = mc.player != null && ownerId.equals(mc.player.getUUID())
                && SkillsClient.current().ready()
                && ElementalistRules.isPrismActive(cn.yumis.fight.modules.skills.api.client.SkillController.INSTANCE.snapshot());
            activePrism = ownerPrism;
            activeNow = renderNow;
            Vec3 base = PlayerVisualAnchor.position(player);
            Vec3 fwd = horizontalDir(PlayerVisualAnchor.forward(player));
            Vec3 center = ownFp
                ? base.subtract(fwd.scale(0.55)).add(0, player.getBbHeight() + 0.55, 0)
                : base.add(0, player.getBbHeight() + 0.58, 0);
            boolean full4 = mask == 0xF;
            int count = Integer.bitCount(mask);
            double orbitR = ownFp ? 0.92 : (full4 ? 0.72 : 0.64);
            double alphaScale = ownFp ? 0.42 : 1.0;

            Vec3[] orbPos = new Vec3[count];
            int[] orbElem = new int[count];
            int idx = 0;
            for (int e = 0; e < 4; e++) {
                if ((mask & (1 << e)) == 0) continue;
                double ang = renderNow * 0.0024 + idx * (Math.TAU / Math.max(1, count));
                double bob = Math.sin(renderNow * 0.0046 + e * 1.5) * 0.10;
                Vec3 pos = center.add(Math.cos(ang) * orbitR, bob, Math.sin(ang) * orbitR);
                orbPos[idx] = pos;
                orbElem[idx] = e;

                long sinceLit = renderNow - litTimes[e];
                double gainPop = (sinceLit >= 0 && sinceLit < 420L) ? (1.0 - sinceLit / 420.0) : 0.0;
                double rad = (full4 ? 0.29 : 0.24) + 0.14 * gainPop;
                int a = (int) Math.round((full4 ? 248 : 230) * alphaScale);
                emitFramedBillboard(sp, TEX_RUNE_LAMP, 4, e, pos, camera, rad, COL_WHITE, elemHot(e), a, 0.82);
                EffectSprites.glow(sp, pos, camera, rad * 1.70, pCol(elemColor(e), e * 0.25), (int) Math.round((165 + 75 * gainPop) * alphaScale));
                idx++;
            }

            if (full4 && !ownFp) {
                double pulse = 0.5 + 0.5 * Math.sin(renderNow * 0.0065);
                // Grand 4-element resonance halo under Archmage's feet + crown star ring
                emitGroundSprite(sp, TEX_ASTRAL_CIRCLE, base.add(0, 0.04, 0), 1.45 + 0.10 * pulse, renderNow * 0.0014, COL_WHITE, COL_ASTRAL_GOLD, (int) (135 + 45 * pulse), 0.62);
                EffectSprites.emitGround(sp, TEX_CHARGE_CIRCLE, Blend.ADDITIVE, center.add(0, -0.06, 0), orbitR * 1.12, -renderNow * 0.0028, 0f, 0f, 1f, 1f, pHot(COL_ASTRAL_GOLD, 0.5), 125);
                // Periodic elemental resonance arcs bridging the 4 lit medallions
                long arcStep = renderNow / 110L;
                int aIdx = (int) Math.floorMod(arcStep, 4L);
                int bIdx = (aIdx + 1) & 3;
                LightningBolt.bolt(sp, orbPos[aIdx], orbPos[bIdx], camera, arcStep ^ ownerId.getMostSignificantBits(), 4, 0.14, 0.032, pHot(elemHot(orbElem[aIdx]), aIdx * 0.25), 185, false);
            }
            activePrism = false;
        }

        // 2. 移动施法：先咏唱预览，再蓄力汇聚 (Moving Cast: chant preview -> charge convergence + ground aim circle)
        if (mc.player != null && ElementalistChargeClient.active()) {
            renderOwnCharge(mc.player, sp, camera, renderNow);
        } else {
            lastFullChargeCastId = "";
            if (mc.player != null && ChantClient.active()) {
                var chantCast = ChantClient.cast();
                if (chantCast != null && ElementalistRules.usesCastTime(chantCast.skill, SkillsClient.current().progress())) {
                    renderOwnChant(mc.player, chantCast, sp, camera, renderNow);
                }
            }
        }

        // 3. 天雷引导准星 (Lightning Strike live golden sky-judgment circle!)
        var ch = ElementalistClient.activeLightningChannel();
        if (ch != null) {
            double pulse = 0.5 + 0.5 * Math.sin(renderNow * 0.012);
            Vec3 ret = ch.reticle.add(0, 0.06, 0);
            double r = ch.radius * 1.15;
            emitGroundSprite(sp, TEX_THUNDER_CIRCLE, ret, r, renderNow * 0.0032, COL_WHITE, COL_LIGHT_HOT, 240, 0.90);
            emitGroundSprite(sp, TEX_CHARGE_CIRCLE, ret.add(0, 0.02, 0), r * 0.62, -renderNow * 0.0055, COL_LIGHT, COL_LIGHT_CYAN, 200, 0.85);
            EffectSprites.glow(sp, ret.add(0, 0.45, 0), camera, r * (0.45 + 0.12 * pulse), COL_LIGHT_HOT, 185);
            // Crackling sky-lock anticipation spark pointing upward
            long tick = renderNow / 65L;
            if ((tick & 1L) == 0L) {
                LightningBolt.bolt(sp, ret.add(0, 0.1, 0), ret.add(0, 4.8, 0), camera, tick, 6, 0.35, 0.048, COL_LIGHT_CYAN, 175, false);
            }
        }
    }

    private static void renderOwnChant(Player player, SkillCast cast, List<SpriteQuad> sp, Vec3 camera, long renderNow) {
        long now = ClientUi.now();
        double progress = ChantClient.progress(now);
        boolean prism = ElementalistRules.isPrismActive(cast.stats);
        activePrism = prism;
        activeNow = renderNow;
        try {
            int innate = ElementalistSkills.innateElement(cast.skill.id);
            int elem = innate >= 0 ? innate : 0;
            int col = prism ? prismMain(renderNow, 0.0) : elemColor(elem);
            int hot = prism ? prismHot(renderNow, 0.4) : elemHot(elem);
            boolean ownFp = Minecraft.getInstance().options.getCameraType().isFirstPerson();

            Vec3 base = PlayerVisualAnchor.position(player);
            Vec3 aimDir = horizontalDir(PlayerVisualAnchor.chargeAim(player, player.getLookAngle()));
            var pose = Effects.VFX.playerPose(player.getUUID(), renderNow);
            Vec3 tip = !ownFp && pose != null
                ? PlayerBoneAnchor.weaponTip(player, pose.frame(), 1.10)
                : base.add(aimDir.scale(ownFp ? 1.15 : 0.95)).add(0, ownFp ? 1.12 : 1.35, 0);

            double footR = 1.05 + 0.40 * progress;
            double fpMul = ownFp ? 0.48 : 1.0;
            emitGroundSprite(sp, TEX_CHARGE_CIRCLE, base.add(0, 0.05, 0), footR, renderNow * 0.0020, col, hot, (int) ((110 + 45 * progress) * fpMul), 0.72);

            double orbScale = (0.18 + 0.16 * progress) * (ownFp ? 0.55 : 1.0);
            int orbAlpha = (int) Math.round((155 + 60 * progress) * (ownFp ? 0.45 : 1.0));
            emitFramedBillboard(sp, TEX_RUNE_LAMP, 4, elem, tip, camera, orbScale, COL_WHITE, hot, orbAlpha, 0.85);
            EffectSprites.glow(sp, tip, camera, orbScale * 1.70, col, (int) Math.round((125 + 50 * progress) * (ownFp ? 0.42 : 1.0)));

            if (ElementalistChargeClient.showsGroundRing(cast)) {
                double r = ElementalistChargeClient.previewRadius(cast, 0.0);
                if (r > 0.2) {
                    Vec3 aim = ElementalistClient.aimGround(Minecraft.getInstance().player, ElementalistChargeClient.previewRange(cast), 5.0);
                    Vec3 gPos = aim.add(0, 0.06, 0);
                    Identifier circleTex = circleForSkill(cast.skill.id);
                    emitGroundSprite(sp, circleTex, gPos, r * 1.06, renderNow * 0.0018, circleTex == TEX_CHARGE_CIRCLE ? col : COL_WHITE, hot, (int) (145 + 45 * progress), 0.58);
                    emitGroundSprite(sp, TEX_CHARGE_CIRCLE, gPos.add(0, 0.02, 0), r * (0.28 + 0.20 * progress), -renderNow * 0.0030, col, hot, (int) (115 + 40 * progress), 0.78);
                }
            }
        } finally {
            activePrism = false;
        }
    }

    private static void renderOwnCharge(Player player, List<SpriteQuad> sp, Vec3 camera, long renderNow) {
        long now = ClientUi.now();
        var cast = ElementalistChargeClient.cast();
        if (cast == null) return;
        boolean prism = ElementalistRules.isPrismActive(cast.stats);
        activePrism = prism;
        activeNow = renderNow;
        try {
            double progress = ElementalistChargeClient.chargeProgress(now);
            boolean full = ElementalistChargeClient.isFull(now);
            int innate = ElementalistSkills.innateElement(cast.skill.id);
            int elem = innate >= 0 ? innate : 0;
            int col = prism ? prismMain(renderNow, 0.2) : (full ? COL_ASTRAL_GOLD : elemColor(elem));
            int hot = prism ? prismHot(renderNow, 0.6) : (full ? COL_LIGHT_HOT : elemHot(elem));
            boolean ownFp = Minecraft.getInstance().options.getCameraType().isFirstPerson();

            Vec3 base = PlayerVisualAnchor.position(player);
            Vec3 aimDir = horizontalDir(PlayerVisualAnchor.chargeAim(player, player.getLookAngle()));
            var pose = Effects.VFX.playerPose(player.getUUID(), renderNow);
            Vec3 tip = !ownFp && pose != null
                ? PlayerBoneAnchor.weaponTip(player, pose.frame(), 1.10)
                : base.add(aimDir.scale(ownFp ? 1.15 : 0.95)).add(0, ownFp ? 1.12 : 1.35, 0);

            if (full && !cast.id.equals(lastFullChargeCastId)) {
                lastFullChargeCastId = cast.id;
                SkillSound.playAt(tip.x, tip.y, tip.z, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.72f, 1.55f);
                WorldEffects.playShockwave(base.add(0, 0.12, 0), 0.40, 2.80, prism ? COL_PRISM : COL_ASTRAL_GOLD, 235, 320L);
            }

            // (a) Under-feet rotating charge magic seal + contracting convergence pulse
            double footR = 1.45 + 0.95 * progress;
            double fpMul = ownFp ? 0.48 : 1.0;
            emitGroundSprite(sp, TEX_CHARGE_CIRCLE, base.add(0, 0.05, 0), footR, renderNow * (full ? 0.0042 : 0.0024), col, hot, (int) ((155 + 85 * progress) * fpMul), 0.85);
            double contract = 1.0 - ((renderNow % 340L) / 340.0);
            EffectSprites.shockwave(sp, base.add(0, 0.09, 0), footR * (0.20 + 1.05 * contract), -renderNow * 0.0045, hot, (int) (195 * contract * fpMul));

            // (b) Staff-tip gathering elemental nucleus (subtle in 1st-person so crosshair stays clean!)
            double orbScale = (0.34 + 0.44 * progress) * (ownFp ? 0.55 : 1.0);
            int orbAlpha = (int) Math.round((full ? 248 : 215) * (ownFp ? 0.45 : 1.0));
            emitFramedBillboard(sp, TEX_RUNE_LAMP, 4, elem, tip, camera, orbScale, COL_WHITE, hot, orbAlpha, 0.90);
            EffectSprites.glow(sp, tip, camera, orbScale * 1.85, col, (int) Math.round((full ? 235 : 175) * (ownFp ? 0.42 : 1.0)));
            if (full && !ownFp) {
                Vec3 side = rightOf(aimDir);
                LightningBolt.bolt(sp, tip.add(side.scale(-0.55)).add(0, 0.35, 0), tip.add(side.scale(0.55)).add(0, -0.35, 0), camera, renderNow / 55L, 5, 0.22, 0.042, hot, 215, false);
            }

            // (c) Ground aiming circle for ground-targeted / AoE skills
            if (ElementalistChargeClient.showsGroundRing()) {
                double r = ElementalistChargeClient.previewRadius(now);
                if (r > 0.2) {
                    Vec3 aim = ElementalistClient.aimGround(Minecraft.getInstance().player, ElementalistChargeClient.previewRange(), 5.0);
                    Vec3 gPos = aim.add(0, 0.06, 0);
                    Identifier circleTex = circleForSkill(cast.skill.id);
                    emitGroundSprite(sp, circleTex, gPos, r * 1.06, renderNow * 0.0022, circleTex == TEX_CHARGE_CIRCLE ? col : COL_WHITE, hot, full ? 238 : 190, full ? 0.92 : 0.65);
                    emitGroundSprite(sp, TEX_CHARGE_CIRCLE, gPos.add(0, 0.02, 0), r * (0.48 + 0.42 * progress), -renderNow * 0.0036, col, hot, full ? 220 : 155, 0.85);
                }
            }
        } finally {
            activePrism = false;
        }
    }

    private static Identifier circleForSkill(String skillId) {
        if (ElementalistSkills.ARCTIC_FEAST.equals(skillId) || ElementalistSkills.ICE_WALL.equals(skillId)) return TEX_ICE_CIRCLE;
        if (ElementalistSkills.LIGHTNING_STRIKE.equals(skillId) || ElementalistSkills.LIGHTNING_VORTEX.equals(skillId)) return TEX_THUNDER_CIRCLE;
        if (ElementalistSkills.FLAME_PILLAR.equals(skillId) || ElementalistSkills.HALLOWEEN_BUSTER.equals(skillId)) return TEX_VOLCANO_CRATER;
        return TEX_CHARGE_CIRCLE;
    }

    // --- 0. 杰克爆弹 (Mage Base Skill — Flaming Laughing Jack-O'-Lantern!) ---

    static void renderJackBomb(SkillVfx.Track t, long age, long now, List<SpriteQuad> sp, Vec3 camera) {
        Meta m = METAS.getOrDefault(t.start.cast(), DEFAULT_META);
        boolean finished = t.last.phase() == FINISH;
        Vec3 livePos = finished ? null : ElementalistClient.liveJackPosition(t.start.cast());
        Vec3 p = livePos != null ? livePos : vector(t.last.position());
        double charge = Math.clamp(t.start.charge(), 0.0, 1.0);
        double sizeScale = (1.0 + 0.32 * charge) * (m.mini ? 0.58 : 1.0);
        activePrism = m.prism;
        activeNow = now;
        try {
            int colFire = pCol(COL_FIRE, 0.0);
            int colHot = pHot(COL_FIRE_HOT, 0.4);
            if (finished) {
                long after = Math.max(0L, now - t.last.at());
                if (after >= 560L) return;
                double u = Math.clamp(after / 560.0, 0.0, 0.999);
                double fade = 1.0 - u;
                int frame = Math.clamp((int) (u * 8), 0, 7);
                double blastR = (m.radius > 0.5 ? m.radius * 1.15 : 3.6) * (m.mini ? 0.85 : sizeScale);

                emitGroundSprite(sp, TEX_VOLCANO_CRATER, p.add(0, 0.06, 0), blastR * 0.96, t.start.seed() * 0.02, COL_WHITE, colFire, (int) (240 * fade), 0.72);
                emitGroundFrame(sp, TEX_JACK_BURST, 8, frame, p.add(0, 0.14, 0), blastR * 1.15, u * 0.9, COL_WHITE, colHot, (int) (252 * fade), 0.92);
                emitFramedBillboard(sp, TEX_JACK_BURST, 8, frame, p.add(0, 0.85, 0), camera, blastR * 0.95, COL_WHITE, colHot, (int) (248 * fade), 0.88);
                emitVerticalCrossed(sp, TEX_FLAME_PILLAR, frame, p.add(0, 0.04, 0), 1.35 * sizeScale, 4.6 * sizeScale, t.start.seed() * 0.01, 2, COL_WHITE, colHot, (int) (240 * fade), 0.85);
                EffectSprites.shockwave(sp, p.add(0, 0.14, 0), blastR * 1.25 * EffectSprites.easeOutCubic(u), u * 1.6, colHot, (int) (245 * fade));
                EffectSprites.glow(sp, p.add(0, 0.75, 0), camera, blastR * 0.90 * fade, colHot, (int) (235 * fade));
                return;
            }

            int frame = (int) Math.floorMod(now / 52L, 8L);
            double rad = 0.82 * sizeScale;
            emitFramedBillboard(sp, TEX_JACK_PUMPKIN, 8, frame, p, camera, rad, COL_WHITE, colHot, 254, 0.65);
            EffectSprites.glow(sp, p, camera, rad * 1.85, colFire, 215);
            RibbonTrail.push("jack_outer:" + t.start.cast(), p, 0.42 * sizeScale, colFire, 240, 300L, now);
            RibbonTrail.push("jack_core:" + t.start.cast(), p, 0.20 * sizeScale, colHot, 230, 210L, now);
        } finally {
            activePrism = false;
        }
    }

    // --- Main Skill Render Router --------------------------------------------

    static void draw(String id, SkillVfx.Track t, long age, long now, List<SpriteQuad> sp, Vec3 camera) {
        Meta m = METAS.getOrDefault(t.start.cast(), DEFAULT_META);
        Vec3 origin = vector(t.start.position());
        Vec3 point = vector(t.last.position());
        Vec3 dir = horizontalDir(vector(t.start.direction()));
        boolean finished = t.last.phase() == FINISH;
        long after = finished ? Math.max(0L, now - t.last.at()) : 0L;
        activePrism = m.prism;
        activeNow = now;
        try {
            switch (id) {
                case ElementalistSkills.ICE_WALL -> renderIceWall(t, m, origin, point, dir, age, now, finished, after, sp, camera);
                case ElementalistSkills.LIGHTNING_VORTEX -> renderLightningVortex(t, m, origin, dir, age, now, finished, after, sp, camera);
                case ElementalistSkills.VOID_SPHERE -> renderVoidSphere(t, m, origin, point, dir, age, now, finished, after, sp, camera);
                case ElementalistSkills.FLAME_PILLAR -> renderFlamePillar(t, m, origin, age, now, sp, camera);
                case ElementalistSkills.HALLOWEEN_BUSTER -> renderHalloweenBuster(t, m, origin, point, age, now, sp, camera);
                case ElementalistSkills.ARCTIC_FEAST -> renderArcticFeast(t, m, origin, age, now, finished, after, sp, camera);
                case ElementalistSkills.BLACK_HOLE -> renderBlackHole(t, m, origin, point, age, now, finished, after, sp, camera);
                case ElementalistSkills.LIGHTNING_STRIKE -> renderLightningStrike(t, m, point, now, finished, after, sp, camera);
                case ElementalistSkills.ASTRAL_STORM -> renderAstralStorm(t, m, origin, age, now, finished, after, sp, camera);
                default -> {}
            }
        } finally {
            activePrism = false;
        }
    }

    // --- 1. 冰墙 (Ice Wall: Faceted Crystalline Ring Barrier + Frost Mist + Explosive Shatter) ---

    private static void renderIceWall(
        SkillVfx.Track t, Meta m, Vec3 origin, Vec3 point, Vec3 dir,
        long age, long now, boolean finished, long after, List<SpriteQuad> sp, Vec3 camera
    ) {
        if (m.morphMode == 1) {
            // 极寒推进 (clean new-texture reuse; bespoke wave rework tracked in TODO)
            double u = Math.clamp(age / (double) Math.max(150L, m.travelMs), 0.0, 1.0);
            Vec3 head = origin.add(dir.scale(m.range * u));
            Vec3 right = rightOf(dir);
            double fade = 1.0 - u * 0.55;
            int frame = Math.clamp((int) (u * 5), 0, 4);
            emitGroundSprite(sp, TEX_ICE_CIRCLE, head.add(0, 0.06, 0), m.width * 0.72, now * 0.004, COL_WHITE, COL_ICE_HOT, (int) (220 * fade), 0.78);
            for (int i = -2; i <= 2; i++) {
                Vec3 pt = head.add(right.scale(i * m.width * 0.20)).subtract(dir.scale(Math.abs(i) * 0.35));
                emitVerticalCrossed(sp, TEX_ICE_WALL, frame, pt.add(0, 0.04, 0), 0.85, 2.85 * fade, i * 0.4, 2, COL_WHITE, COL_ICE_HOT, (int) (242 * fade), 0.72);
            }
            emitFramedBillboard(sp, TEX_FROST_MIST, 4, (int) Math.floorMod(now / 90L, 4L), head.add(0, 1.1, 0), camera, m.width * 0.62, COL_WHITE, COL_ICE_HOT, (int) (185 * fade), 0.45);
            EffectSprites.shockwave(sp, head.add(0, 0.14, 0), m.width * 0.82, now * 0.005, pHot(COL_ICE_HOT, 0.2), (int) (230 * fade));
            RibbonTrail.push("ice_wave:" + t.start.cast(), head.add(0, 0.65, 0), m.width * 0.36, pCol(COL_ICE, 0.0), (int) (215 * fade), 240L, now);
            return;
        }

        Vec3 wallOrigin = origin;
        if (!finished && m.follow) {
            var mc = Minecraft.getInstance();
            if (mc != null && mc.level != null) {
                var caster = mc.level.getEntity(t.start.caster());
                if (caster != null) wallOrigin = caster instanceof Player p ? PlayerVisualAnchor.position(p) : caster.position();
            }
        }
        boolean ownFpInside = isOwnFirstPerson(t.start.caster())
            && camera.subtract(wallOrigin).horizontalDistanceSqr() <= (m.radius + 0.8) * (m.radius + 0.8);

        if (!finished) {
            double grow = Math.clamp(age / 180.0, 0.18, 1.0);
            int segs = m.wallSegments;
            double h = m.wallHeight * 1.40 * grow;
            long sinceHit = m.wallHitAt > 0L ? Math.max(0L, now - m.wallHitAt) : Long.MAX_VALUE;
            double flash = sinceHit < 220L ? 1.0 - sinceHit / 220.0 : 0.0;
            int standFrame = age < 90L ? 0 : age < 180L ? 1 : (2 + (int) Math.floorMod(now / 260L, 3L));
            int alpha = Math.clamp((int) Math.round((ownFpInside ? 155 : 238) + 35 * flash), 120, 255);
            double boost = 0.68 + 0.35 * flash;

            // Ornate Frost Hexagram Magic Circle anchoring the wall on the ground
            emitGroundSprite(sp, TEX_ICE_CIRCLE, wallOrigin.add(0, 0.05, 0), m.radius * 1.18, now * 0.0010, COL_WHITE, COL_ICE_HOT, ownFpInside ? 175 : 235, 0.82);

            // Ring of towering razor-faceted translucent crystal barrier monoliths
            double circumference = Math.TAU * m.radius;
            double slabHalfW = Math.max(0.68, (circumference / segs) * 0.62);
            int mistFrame = (int) Math.floorMod(now / 130L, 4L);
            for (int i = 0; i < segs; i++) {
                double a = i * (Math.TAU / segs);
                Vec3 pillar = wallOrigin.add(Math.cos(a) * m.radius, 0.02, Math.sin(a) * m.radius);
                // Tangent + slight angled facet so the ring looks dense & crystalline from every angle!
                emitVerticalCrossed(sp, TEX_ICE_WALL, standFrame, pillar, slabHalfW, h * (0.94 + 0.12 * ((i & 1) == 0 ? 1 : 0)), a + Math.PI * 0.5, 2, COL_WHITE, flash > 0.2 ? COL_WHITE : COL_ICE_HOT, alpha, boost);
                if ((i & 1) == 0 && !ownFpInside) {
                    emitFramedBillboard(sp, TEX_FROST_MIST, 4, (mistFrame + i) & 3, pillar.add(0, 0.72, 0), camera, 1.18, COL_WHITE, COL_ICE, 135, 0.32);
                }
            }

            if (flash > 0.05) {
                EffectSprites.shockwave(sp, wallOrigin.add(0, 0.95, 0), m.radius * (1.05 + 0.25 * (1.0 - flash)), now * 0.008, pHot(COL_ICE_HOT, 0.3), (int) (235 * flash));
            }
            if (m.morphMode == 2) {
                emitGroundSprite(sp, TEX_THUNDER_CIRCLE, wallOrigin.add(0, 0.08, 0), m.radius * 1.12, -now * 0.0030, COL_WHITE, COL_LIGHT_HOT, 185, 0.85);
                for (int i = 0; i < 4; i++) {
                    double a0 = i * (Math.PI * 0.5) + now * 0.003;
                    double a1 = a0 + Math.PI * 0.5;
                    Vec3 p0 = wallOrigin.add(Math.cos(a0) * m.radius, h * 0.52, Math.sin(a0) * m.radius);
                    Vec3 p1 = wallOrigin.add(Math.cos(a1) * m.radius, h * 0.52, Math.sin(a1) * m.radius);
                    LightningBolt.bolt(sp, p0, p1, camera, (now / 55L) ^ t.start.seed() ^ (i * 37L), 5, 0.30, 0.055, pHot(COL_LIGHT_CYAN, i * 0.2), 215, false);
                }
            }
            return;
        }

        // Explosive Crystal Shatter Finisher!
        if (after >= 680L) return;
        double u = Math.clamp(after / 680.0, 0.0, 0.999);
        double fade = 1.0 - u;
        int shatterFrame = 5 + Math.clamp((int) (u * 3), 0, 2);
        int burstFrame = Math.clamp((int) (u * 8), 0, 7);
        double blastR = Math.max(m.radius, m.blastRadius) * 1.20;

        emitGroundSprite(sp, TEX_ICE_CIRCLE, point.add(0, 0.05, 0), blastR, now * 0.002, COL_WHITE, COL_ICE_HOT, (int) (220 * fade), 0.85);
        for (int i = 0; i < Math.min(12, m.wallSegments); i++) {
            double a = i * (Math.TAU / Math.min(12, m.wallSegments));
            double expand = m.radius + 1.35 * EffectSprites.easeOutCubic(u);
            Vec3 pt = point.add(Math.cos(a) * expand, 0.02, Math.sin(a) * expand);
            emitVerticalCrossed(sp, TEX_ICE_WALL, shatterFrame, pt, 0.85, m.wallHeight * 1.35, a + Math.PI * 0.5, 2, COL_WHITE, COL_ICE_HOT, (int) (245 * fade), 0.88);
        }
        emitGroundFrame(sp, TEX_ASTRAL_SUPERNOVA, 8, burstFrame, point.add(0, 0.18, 0), blastR * 1.15, u * 1.0, COL_ICE, COL_ICE_HOT, (int) (245 * fade), 0.95);
        EffectSprites.shockwave(sp, point.add(0, 0.16, 0), blastR * 1.22 * EffectSprites.easeOutCubic(u), u * 1.8, pHot(COL_ICE_HOT, 0.1), (int) (248 * fade));
        EffectSprites.shockwave(sp, point.add(0, 1.35, 0), blastR * 0.95 * EffectSprites.easeOutCubic(u), -u * 1.5, pCol(COL_ICE, 0.5), (int) (215 * fade));
        EffectSprites.glow(sp, point.add(0, 1.2, 0), camera, blastR * 0.95 * fade, pHot(COL_ICE_HOT, 0.3), (int) (235 * fade));
    }

    // --- 2. 雷旋 (Lightning Vortex: High-Voltage Orbiting Thunder Orbs + Crackle Web) ---

    private static void renderLightningVortex(
        SkillVfx.Track t, Meta m, Vec3 origin, Vec3 dir,
        long age, long now, boolean finished, long after, List<SpriteQuad> sp, Vec3 camera
    ) {
        if (m.morphMode == 1) {
            // 超导电磁炮 (clean new-texture railgun)
            double u = Math.clamp(age / 700.0, 0.0, 0.999);
            double fade = 1.0 - u;
            Vec3 end = origin.add(dir.scale(m.range));
            int orbFrame = (int) Math.floorMod(now / 42L, 8L);
            int burstFrame = Math.clamp((int) (u * 8), 0, 7);
            emitFramedBillboard(sp, TEX_VORTEX_ORB, 8, orbFrame, origin, camera, 1.15 * fade + 0.35, COL_WHITE, COL_LIGHT_HOT, (int) (252 * fade), 0.92);
            EffectSprites.texturedBeam(sp, origin, end, camera, 0.56 * fade + 0.10, now, true, pCol(COL_LIGHT, 0.0), (int) (250 * fade));
            LightningBolt.bolt(sp, origin, end, camera, (now / 45L) ^ t.start.seed(), 12, 0.58, 0.11 * fade + 0.03, pHot(COL_LIGHT_HOT, 0.3), (int) (252 * fade), true);
            LightningBolt.helixArcs(sp, origin, end, camera, now, 0.65 * fade + 0.18, 2, pHot(COL_LIGHT_CYAN, 0.6), (int) (235 * fade));
            emitFramedBillboard(sp, TEX_ASTRAL_SUPERNOVA, 8, burstFrame, end, camera, m.radius * 1.35, COL_LIGHT, COL_LIGHT_HOT, (int) (248 * fade), 0.95);
            EffectSprites.shockwave(sp, end.add(0, 0.12, 0), m.radius * 1.45 * EffectSprites.easeOutCubic(u), u * 1.8, pHot(COL_LIGHT_HOT, 0.2), (int) (240 * fade));
            renderVortexChainArcs(m, now, sp, camera);
            return;
        }

        Vec3 center = origin;
        boolean ownFpFollow = false;
        var mc = Minecraft.getInstance();
        if (m.follow && mc != null && mc.level != null) {
            var caster = mc.level.getEntity(t.start.caster());
            if (caster != null) center = caster instanceof Player p ? PlayerVisualAnchor.position(p) : caster.position();
            ownFpFollow = isOwnFirstPerson(t.start.caster());
        }
        double fade = finished ? Math.clamp(1.0 - after / 450.0, 0.0, 1.0) : 1.0;
        if (fade <= 0.01) return;

        double orbitR = m.radius * 0.80;
        double fpMul = ownFpFollow ? 0.50 : 1.0;
        int orbFrame = (int) Math.floorMod(now / 40L, 8L);

        // Spinning Golden Thunder Magic Circle anchoring the vortex!
        emitGroundSprite(sp, TEX_THUNDER_CIRCLE, center.add(0, 0.06, 0), m.radius * 1.15, now * 0.0055, COL_WHITE, COL_LIGHT_HOT, (int) (232 * fade * fpMul), 0.92);
        emitGroundSprite(sp, TEX_CHARGE_CIRCLE, center.add(0, 0.09, 0), m.radius * 0.68, -now * 0.0085, COL_LIGHT, COL_LIGHT_CYAN, (int) (195 * fade * fpMul), 0.88);

        // Rapid rhythmic ground electric pulse
        double pulseU = (age % 250L) / 250.0;
        EffectSprites.shockwave(sp, center.add(0, 0.14, 0), m.radius * (0.35 + 0.92 * EffectSprites.easeOutCubic(pulseU)), pulseU * 1.6, pHot(COL_LIGHT_CYAN, 0.2), (int) (205 * (1.0 - pulseU) * fade * fpMul));

        Vec3[] orbs = new Vec3[m.orbCount];
        for (int i = 0; i < m.orbCount; i++) {
            double a = now * 0.0125 + i * (Math.TAU / m.orbCount);
            double h = (ownFpFollow ? 0.82 : 1.08) + Math.sin(a * 2.0) * 0.24;
            Vec3 orb = center.add(Math.cos(a) * orbitR, h, Math.sin(a) * orbitR);
            orbs[i] = orb;

            double orbR = (ownFpFollow ? 0.58 : 0.78) * (0.85 + 0.15 * fade);
            emitFramedBillboard(sp, TEX_VORTEX_ORB, 8, (orbFrame + i * 2) & 7, orb, camera, orbR, COL_WHITE, COL_LIGHT_HOT, (int) (252 * fade * (ownFpFollow ? 0.65 : 1.0)), 0.95);
            EffectSprites.glow(sp, orb, camera, orbR * 1.75, pCol(COL_LIGHT, i * 0.2), (int) (225 * fade * fpMul));
            if (!finished) {
                RibbonTrail.push("vortex_orb:" + t.start.cast() + ":" + i, orb, 0.28 * (ownFpFollow ? 0.65 : 1.0), pCol(COL_LIGHT, i * 0.2), (int) (230 * fpMul), 230L, now);
                RibbonTrail.push("vortex_core:" + t.start.cast() + ":" + i, orb, 0.13 * (ownFpFollow ? 0.65 : 1.0), pHot(COL_LIGHT_CYAN, i * 0.2), (int) (220 * fpMul), 160L, now);
            }
        }

        // High-voltage lightning ring web connecting every orb + radial snaps to center!
        long step = now / 45L;
        for (int i = 0; i < m.orbCount; i++) {
            Vec3 a = orbs[i];
            Vec3 b = orbs[(i + 1) % m.orbCount];
            LightningBolt.bolt(sp, a, b, camera, step ^ t.start.seed() ^ (i * 31L), 6, 0.34, 0.068, i % 2 == 0 ? pHot(COL_LIGHT_HOT, i * 0.2) : pHot(COL_LIGHT_CYAN, i * 0.25), (int) (235 * fade * fpMul), false);
            if (!ownFpFollow && (i % 2 == 0)) {
                LightningBolt.bolt(sp, center.add(0, 1.05, 0), a, camera, step ^ (i * 97L), 5, 0.28, 0.052, pCol(COL_LIGHT, i * 0.2), (int) (195 * fade), false);
            }
        }

        if (finished) {
            double fu = Math.clamp(after / 450.0, 0.0, 0.999);
            int burstFrame = Math.clamp((int) (fu * 8), 0, 7);
            emitFramedBillboard(sp, TEX_ASTRAL_SUPERNOVA, 8, burstFrame, center.add(0, 1.1, 0), camera, m.radius * 1.25, COL_LIGHT, COL_LIGHT_HOT, (int) (245 * fade), 0.95);
            EffectSprites.shockwave(sp, center.add(0, 0.15, 0), m.radius * 1.38 * EffectSprites.easeOutCubic(fu), fu * 2.0, pHot(COL_LIGHT_HOT, 0.2), (int) (240 * fade));
        }
        renderVortexChainArcs(m, now, sp, camera);
    }

    private static void renderVortexChainArcs(Meta m, long now, List<SpriteQuad> sp, Vec3 camera) {
        if (m.chainArcs.isEmpty()) return;
        for (var arc : m.chainArcs) {
            long age = now - arc.at();
            if (age < 0L || age >= 280L) continue;
            double fade = 1.0 - age / 280.0;
            long seed = (now / 40L) ^ ((long) arc.sequence() * 67L);
            LightningBolt.bolt(sp, arc.from(), arc.to(), camera, seed, 7, 0.42, 0.085 * fade + 0.025, pHot(COL_LIGHT_HOT, 0.2), (int) (250 * fade), true);
            LightningBolt.bolt(sp, arc.from(), arc.to(), camera, seed + 17L, 6, 0.30, 0.048 * fade + 0.015, pCol(COL_LIGHT_CYAN, 0.6), (int) (230 * fade), false);
            EffectSprites.glow(sp, arc.to(), camera, 1.45 * fade, pHot(COL_LIGHT_HOT, 0.4), (int) (220 * fade));
        }
    }

    // --- 3. 虚无之球 (Void Sphere: Pitch-Black Abyssal Core + Tilted Void Disks + Collapse Blast) ---

    private static void renderVoidSphere(
        SkillVfx.Track t, Meta m, Vec3 origin, Vec3 point, Vec3 dir,
        long age, long now, boolean finished, long after, List<SpriteQuad> sp, Vec3 camera
    ) {
        if (finished) {
            if (after >= 680L) return;
            double u = Math.clamp(after / 680.0, 0.0, 0.999);
            double fade = 1.0 - u;
            int burstFrame = Math.clamp((int) (u * 8), 0, 7);
            double blastR = Math.max(m.radius * 1.4, m.blastRadius) * 1.30;

            emitGroundSprite(sp, TEX_BLACK_HOLE_DISK, point.add(0, -0.75, 0), blastR * 1.15, now * 0.005, COL_WHITE, COL_DARK_HOT, (int) (240 * fade), 0.85);
            emitGroundFrame(sp, TEX_ASTRAL_SUPERNOVA, 8, burstFrame, point.add(0, -0.68, 0), blastR * 1.25, u * 1.1, COL_DARK, COL_DARK_HOT, (int) (252 * fade), 0.95);
            emitFramedBillboard(sp, TEX_ASTRAL_SUPERNOVA, 8, burstFrame, point, camera, blastR * 1.05, COL_DARK, COL_DARK_HOT, (int) (248 * fade), 0.95);
            EffectSprites.shockwave(sp, point.add(0, -0.65, 0), blastR * 1.30 * EffectSprites.easeOutCubic(u), u * 2.0, pHot(COL_DARK_HOT, 0.1), (int) (245 * fade));
            EffectSprites.shockwave(sp, point, blastR * 0.98 * EffectSprites.easeOutCubic(u), -u * 1.6, pCol(COL_DARK, 0.5), (int) (220 * fade));
            EffectSprites.glow(sp, point, camera, blastR * 0.95 * fade, pHot(COL_DARK_HOT, 0.3), (int) (235 * fade));
            for (int i = 0; i < 6; i++) {
                double ang = i * (Math.TAU / 6.0) + u * 0.5;
                Vec3 edge = point.add(Math.cos(ang) * blastR * 0.92, Math.sin(i * 1.9) * 0.8, Math.sin(ang) * blastR * 0.92);
                LightningBolt.bolt(sp, point, edge, camera, (now / 50L) ^ t.start.seed() ^ (i * 73L), 6, 0.38, 0.068, i % 2 == 0 ? pHot(COL_DARK_HOT, i * 0.16) : pCol(COL_DARK, i * 0.16), (int) (235 * fade), false);
            }
            return;
        }

        double speed = m.stationary ? 0.0 : (m.speed > 0.05 ? m.speed : 3.0);
        double maxRange = Math.max(4.0, m.range);
        double travel = Math.min(maxRange, (age / 1000.0) * speed);
        Vec3 pos = origin.add(dir.scale(travel));
        var mc = Minecraft.getInstance();
        if (!m.stationary && travel > 0.05 && mc != null && mc.level != null && mc.player != null) {
            var wall = mc.level.clip(new ClipContext(origin, pos, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
            if (wall.getType() != HitResult.Type.MISS) {
                pos = wall.getLocation().subtract(dir.scale(0.35));
            }
        }

        double r = Math.max(0.75, m.radius * 0.96);
        int orbFrame = (int) Math.floorMod(now / 52L, 8L);
        int diskFrame = (int) Math.floorMod(now / 75L, 4L);

        // Pitch-black abyssal sphere + blazing violet photon rim!
        emitFramedBillboard(sp, TEX_VOID_ORB, 8, orbFrame, pos, camera, r, COL_WHITE, COL_DARK_HOT, 254, 0.88);
        // Dual tilted counter-rotating galactic shadow vortex rings wrapping the orb!
        emitTiltedRing(sp, TEX_BLACK_HOLE_DISK, 4, diskFrame, pos, r * 1.48, now * 0.0085, 24.0, 16.0, COL_WHITE, COL_DARK_HOT, 225);
        emitTiltedRing(sp, TEX_BLACK_HOLE_DISK, 4, (diskFrame + 2) & 3, pos, r * 1.28, -now * 0.0110, -34.0, -22.0, COL_DARK, COL_DARK_HOT, 195);
        // Dark arcane ground projection gliding below
        emitGroundSprite(sp, TEX_CHARGE_CIRCLE, pos.add(0, -0.92, 0), r * 1.22, now * 0.0045, COL_DARK_DEEP, COL_DARK, 185, 0.72);
        EffectSprites.glow(sp, pos, camera, r * 1.55, pCol(COL_DARK, 0.2), 205);

        // Crackling shadow arcs whipping around the void perimeter
        long step = now / 55L;
        for (int i = 0; i < 3; i++) {
            double ang = now * 0.009 + i * (Math.TAU / 3.0);
            Vec3 rim = pos.add(Math.cos(ang) * r * 1.42, Math.sin(ang * 1.7) * r * 0.55, Math.sin(ang) * r * 1.42);
            LightningBolt.bolt(sp, rim, pos, camera, step ^ t.start.seed() ^ (i * 41L), 5, 0.28, 0.052, pHot(COL_DARK_HOT, i * 0.3), 215, false);
        }

        if (!m.stationary) {
            RibbonTrail.push("void_outer:" + t.start.cast(), pos, r * 0.56, pCol(COL_DARK, 0.0), 225, 320L, now);
            RibbonTrail.push("void_core:" + t.start.cast(), pos, r * 0.24, pHot(COL_DARK_HOT, 0.4), 210, 220L, now);
        }
    }

    // --- 4. 烈焰冲击 (Flame Pillar: Volcanic Fissure Pre-Warning -> Towering 10.5-Block Magma Geyser!) ---

    private static void renderFlamePillar(
        SkillVfx.Track t, Meta m, Vec3 origin, long age, long now, List<SpriteQuad> sp, Vec3 camera
    ) {
        if (m.volcano) {
            // 虚无火山: persistent dark-fire magma caldera with periodic volcanic eruptions
            boolean finished = t.last.phase() == FINISH;
            long after = finished ? Math.max(0L, now - t.last.at()) : 0L;
            double fade = finished ? Math.clamp(1.0 - after / 500.0, 0.0, 1.0) : 1.0;
            if (fade <= 0.01) return;
            Vec3 ground = origin.add(0, 0.05, 0);
            int flameFrame = (int) Math.floorMod(now / 68L, 8L);
            int diskFrame = (int) Math.floorMod(now / 80L, 4L);
            double pulseU = (age % 400L) / 400.0;
            double burst = 1.0 - pulseU;
            emitGroundSprite(sp, TEX_VOLCANO_CRATER, ground, m.radius * 1.25, now * 0.0014, COL_WHITE, COL_FIRE_HOT, (int) (245 * fade), 0.85);
            emitGroundSprite(sp, TEX_BLACK_HOLE_DISK, ground.add(0, 0.03, 0), m.radius * 1.08, -now * 0.0038, COL_DARK, COL_DARK_HOT, (int) (215 * fade), 0.82);
            EffectSprites.shockwave(sp, ground.add(0, 0.10, 0), m.radius * (1.35 - 0.85 * pulseU), -now * 0.006, pCol(COL_DARK_HOT, 0.3), (int) (195 * fade));
            emitVerticalCrossed(sp, TEX_FLAME_PILLAR, flameFrame, ground, m.radius * 0.56, (3.8 + 2.4 * burst) * fade, now * 0.003, 3, COL_WHITE, COL_FIRE_HOT, (int) (235 * fade), 0.90);
            emitGroundFrame(sp, TEX_JACK_BURST, 8, Math.clamp((int) (pulseU * 8), 0, 7), ground.add(0, 0.12, 0), m.radius * 1.15, pulseU * 1.1, COL_WHITE, COL_FIRE_HOT, (int) (225 * burst * fade), 0.88);
            emitTiltedRing(sp, TEX_BLACK_HOLE_DISK, 4, diskFrame, origin.add(0, 0.85, 0), m.radius * 0.72, now * 0.009, 18.0, -14.0, COL_DARK, COL_FIRE_HOT, (int) (190 * fade));
            for (int i = 0; i < 4; i++) {
                double ang = i * (Math.PI * 0.5) + now * 0.0024;
                Vec3 rim = origin.add(Math.cos(ang) * m.radius * 0.78, 0.25 + 1.8 * burst, Math.sin(ang) * m.radius * 0.78);
                LightningBolt.bolt(sp, origin.add(0, 0.4, 0), rim, camera, (now / 60L) ^ t.start.seed() ^ (i * 41L), 5, 0.35, 0.055, i % 2 == 0 ? pCol(COL_FIRE_HOT, 0.2) : pCol(COL_DARK_HOT, 0.6), (int) (215 * fade), false);
            }
            return;
        }
        // Pre-eruption volcanic caldera fissure warning
        if (age < m.impactDelayMs + 80L && t.last.phase() == START) {
            double u = Math.clamp(age / (double) Math.max(1L, m.impactDelayMs), 0.0, 1.0);
            Identifier warnTex = m.morphMode == 1 ? TEX_ICE_CIRCLE : TEX_VOLCANO_CRATER;
            int warnBoost = m.morphMode == 1 ? COL_ICE_HOT : COL_FIRE_HOT;
            emitGroundSprite(sp, warnTex, origin.add(0, 0.05, 0), m.radius * 1.25 * (0.55 + 0.50 * EffectSprites.easeOutCubic(u)), now * 0.004, COL_WHITE, warnBoost, 240, 0.85);
            EffectSprites.shockwave(sp, origin.add(0, 0.08, 0), m.radius * 1.28 * (1.0 - 0.65 * u), -now * 0.006, warnBoost, 225);
        }

        // Massive towering volcanic eruption per HIT flash!
        for (var event : t.flashes) {
            if (event.phase() != HIT) continue;
            long eAge = now - event.at();
            if (eAge < 0L || eAge >= FLAME_ERUPTION_MS) continue;
            double u = Math.clamp(eAge / (double) FLAME_ERUPTION_MS, 0.0, 0.999);
            double fastU = Math.clamp(eAge / 460.0, 0.0, 0.999);
            double fade = 1.0 - u;
            double craterFade = u < 0.62 ? 1.0 : (1.0 - u) / 0.38;
            int frame = Math.clamp((int) (u * 8), 0, 7);
            Vec3 base = vector(event.position()).add(0, 0.04, 0);

            // Scorched glowing lava crater + fiery sunburst shock on ground!
            emitGroundSprite(sp, TEX_VOLCANO_CRATER, base, m.radius * 1.35 * (0.60 + 0.45 * EffectSprites.easeOutCubic(fastU)), event.sequence() * 1.3, COL_WHITE, COL_FIRE_HOT, (int) (248 * craterFade), 0.80);
            emitGroundFrame(sp, TEX_JACK_BURST, 8, frame, base.add(0, 0.10, 0), m.radius * 1.48, u * 0.9, COL_WHITE, COL_FIRE_HOT, (int) (252 * fade), 0.92);

            // COLOSSAL 10.5-block roaring spiral magma column (3 axial planes!) + incandescent inner core beam!
            double colH = 10.5 * (0.45 + 0.55 * EffectSprites.easeOutCubic(Math.min(1.0, u * 2.4)));
            emitVerticalCrossed(sp, TEX_FLAME_PILLAR, frame, base, m.radius * 0.92, colH, now * 0.0022 + event.sequence(), 3, COL_WHITE, COL_FIRE_HOT, (int) (254 * fade), 0.95);
            EffectSprites.texturedBeam(sp, base, base.add(0, colH * 0.92, 0), camera, m.radius * 0.42 * fade + 0.12, now, true, pHot(COL_FIRE_HOT, 0.2), (int) (230 * fade));

            // 3 surrounding secondary magma geysers filling out the volcanic caldera!
            for (int i = 0; i < 3; i++) {
                double ang = i * (Math.TAU / 3.0) + event.sequence() * 0.85;
                Vec3 sub = base.add(Math.cos(ang) * m.radius * 0.54, 0.02, Math.sin(ang) * m.radius * 0.54);
                emitVerticalCrossed(sp, TEX_FLAME_PILLAR, Math.min(7, frame + (i & 1)), sub, m.radius * 0.48, colH * 0.62, ang, 2, COL_WHITE, COL_FIRE, (int) (228 * fade), 0.80);
            }

            // Triple rising fiery shockwave rings
            EffectSprites.shockwave(sp, base.add(0, 0.14, 0), m.radius * 1.52 * EffectSprites.easeOutCubic(u), u * 1.7, pHot(COL_FIRE_HOT, 0.1), (int) (248 * fade));
            EffectSprites.shockwave(sp, base.add(0, 2.60, 0), m.radius * 1.18 * EffectSprites.easeOutCubic(u), -u * 1.4, pCol(COL_FIRE, 0.4), (int) (220 * fade));
            EffectSprites.shockwave(sp, base.add(0, 5.40, 0), m.radius * 0.90 * EffectSprites.easeOutCubic(u), u * 2.0, pHot(COL_FIRE_HOT, 0.7), (int) (195 * fade));
            if (eAge < 460L) {
                EffectSprites.glow(sp, base.add(0, 2.2, 0), camera, m.radius * 1.65 * (1.0 - fastU), pHot(COL_FIRE_HOT, 0.2), 245);
            }
        }
    }

    // --- 5. 杰克降临 (Halloween Buster: Colossal Sky-Crushing Flaming Jack-O'-Lantern + Lava Cataclysm!) ---

    private static void renderHalloweenBuster(
        SkillVfx.Track t, Meta m, Vec3 origin, Vec3 point, long age, long now, List<SpriteQuad> sp, Vec3 camera
    ) {
        if (age < m.fallMs) {
            double u = Math.clamp(age / (double) Math.max(1L, m.fallMs), 0.0, 1.0);
            double easeFall = u * u * (0.35 + 0.65 * u);
            Vec3 startPos = origin.add(-4.8, 19.5, -3.6);
            Vec3 pos = startPos.lerp(origin.add(0, 1.8, 0), easeFall);
            double r = m.morphMode == 1 ? Math.max(3.35, m.radius * 0.52) : 2.45;
            int frame = (int) Math.floorMod(now / 45L, 8L);

            // Blazing impact warning caldera + contracting shockwave ring on target ground
            emitGroundSprite(sp, TEX_VOLCANO_CRATER, origin.add(0, 0.05, 0), m.radius * 1.15 * (0.45 + 0.60 * u), now * 0.0025, COL_WHITE, COL_FIRE_HOT, 235, 0.82);
            emitGroundSprite(sp, TEX_CHARGE_CIRCLE, origin.add(0, 0.08, 0), m.blastRadius * 1.02, -now * 0.0038, COL_FIRE, COL_FIRE_HOT, 215, 0.85);
            EffectSprites.shockwave(sp, origin.add(0, 0.12, 0), m.blastRadius * (1.10 - 0.80 * u), now * 0.006, pHot(COL_FIRE_HOT, 0.1), (int) (220 * u));

            // COLOSSAL DNF Flaming Laughing Jack-O'-Lantern plunging from the heavens!
            emitFramedBillboard(sp, TEX_JACK_PUMPKIN, 8, frame, pos, camera, r, COL_WHITE, COL_FIRE_HOT, 255, 0.68);
            EffectSprites.glow(sp, pos, camera, r * 2.15, pCol(COL_FIRE, 0.0), 240);
            EffectSprites.glow(sp, pos, camera, r * 1.25, pHot(COL_FIRE_HOT, 0.4), 215);

            // Massive roaring atmospheric reentry fire tail!
            RibbonTrail.push("buster_outer:" + t.start.cast(), pos, r * 0.82, pCol(COL_FIRE, 0.0), 248, 360L, now);
            RibbonTrail.push("buster_core:" + t.start.cast(), pos, r * 0.42, pHot(COL_FIRE_HOT, 0.4), 240, 260L, now);
            return;
        }

        // Earth-Shattering Pumpkin Detonation + Colossal Outer Lava Shockwave!
        long impAge = age - m.fallMs;
        double blastDur = 950.0;
        if (impAge < blastDur) {
            double u = Math.clamp(impAge / blastDur, 0.0, 0.999);
            double fastU = Math.clamp(impAge / 520.0, 0.0, 0.999);
            double fade = 1.0 - u;
            double craterFade = u < 0.65 ? 1.0 : (1.0 - u) / 0.35;
            int burstFrame = Math.clamp((int) (u * 8), 0, 7);
            Vec3 ground = point.add(0, 0.05, 0);
            double outerR = Math.max(m.radius * 1.35, m.blastRadius) * 1.18;

            // Scorched lava caldera + giant ground & billboard sunburst!
            emitGroundSprite(sp, TEX_VOLCANO_CRATER, ground, outerR * 0.98, t.start.seed() * 0.02, COL_WHITE, COL_FIRE_HOT, (int) (250 * craterFade), 0.82);
            emitGroundFrame(sp, TEX_JACK_BURST, 8, burstFrame, ground.add(0, 0.14, 0), outerR * 1.12, u * 0.85, COL_WHITE, COL_FIRE_HOT, (int) (255 * fade), 0.95);
            emitFramedBillboard(sp, TEX_JACK_BURST, 8, burstFrame, point.add(0, 2.2, 0), camera, m.radius * 1.55, COL_WHITE, COL_FIRE_HOT, (int) (252 * fade), 0.95);

            // Towering central molten blast plume (10 blocks tall!) + 4 surrounding lava fountains!
            emitVerticalCrossed(sp, TEX_FLAME_PILLAR, burstFrame, ground, m.radius * 0.78, 10.2, now * 0.002, 3, COL_WHITE, COL_FIRE_HOT, (int) (252 * fade), 0.92);
            for (int i = 0; i < 4; i++) {
                double ang = i * (Math.PI * 0.5) + (t.start.seed() & 7) * 0.3;
                double dist = m.radius * 0.65 + (outerR - m.radius * 0.65) * 0.45 * EffectSprites.easeOutCubic(u);
                Vec3 sub = ground.add(Math.cos(ang) * dist, 0.02, Math.sin(ang) * dist);
                emitVerticalCrossed(sp, TEX_FLAME_PILLAR, Math.min(7, burstFrame + (i & 1)), sub, m.radius * 0.40, 6.2, ang, 2, COL_WHITE, COL_FIRE, (int) (232 * fade), 0.82);
            }

            // Triple colossal molten shockwave rings sweeping out past shockwaveRadius!
            double expand = m.radius * 0.65 + (outerR - m.radius * 0.65) * EffectSprites.easeOutCubic(Math.min(1.0, impAge / 420.0));
            EffectSprites.shockwave(sp, ground.add(0, 0.18, 0), expand * 1.15, now * 0.004, pHot(COL_FIRE_HOT, 0.1), (int) (252 * fade));
            EffectSprites.shockwave(sp, ground.add(0, 1.85, 0), expand * 0.92, -now * 0.003, pCol(COL_FIRE, 0.4), (int) (228 * fade));
            EffectSprites.shockwave(sp, ground.add(0, 3.80, 0), expand * 0.68, now * 0.005, pHot(COL_FIRE_HOT, 0.7), (int) (198 * fade));
            if (impAge < 480L) {
                EffectSprites.glow(sp, point.add(0, 2.0, 0), camera, outerR * 1.10 * (1.0 - fastU), pHot(COL_FIRE_HOT, 0.2), 250);
            }
        }

        // Mode 2 lingering molten ember sea
        if (m.emberMs > 0L && impAge < m.emberMs) {
            double emberFade = Math.clamp(1.0 - impAge / (double) m.emberMs, 0.0, 1.0);
            int flameFrame = (int) Math.floorMod(now / 85L, 8L);
            emitGroundSprite(sp, TEX_VOLCANO_CRATER, point.add(0, 0.05, 0), m.radius * 1.18, -now * 0.0012, COL_WHITE, COL_FIRE, (int) (225 * emberFade), 0.78);
            for (int i = 0; i < 4; i++) {
                double ang = i * (Math.PI * 0.5) + now * 0.0018;
                Vec3 pt = point.add(Math.cos(ang) * m.radius * 0.52, 0.04, Math.sin(ang) * m.radius * 0.52);
                emitVerticalCrossed(sp, TEX_FLAME_PILLAR, (flameFrame + i * 2) & 7, pt, 0.78, 2.4 * emberFade + 0.6, ang, 2, COL_WHITE, COL_FIRE_HOT, (int) (185 * emberFade), 0.75);
            }
        }
    }

    // --- 6. 极冰盛宴 (Arctic Feast: Grand Frost Hexagram + Crystal Eruption + Freeze Shatter!) ---

    private static void renderArcticFeast(
        SkillVfx.Track t, Meta m, Vec3 origin, long age, long now,
        boolean finished, long after, List<SpriteQuad> sp, Vec3 camera
    ) {
        double fade = finished ? Math.clamp(1.0 - after / (double) ARCTIC_FINISH_MS, 0.0, 1.0) : 1.0;
        if (fade <= 0.01) return;

        double openEase = Math.clamp(age / 240.0, 0.0, 1.0);
        double fieldR = m.radius * 1.18 * (0.45 + 0.55 * EffectSprites.easeOutCubic(openEase));

        // 1. Colossal Dual Counter-Rotating 6-Point Frost Hexagram Magic Circle!
        emitGroundSprite(sp, TEX_ICE_CIRCLE, origin.add(0, 0.05, 0), fieldR, now * 0.0016, COL_WHITE, COL_ICE_HOT, (int) (245 * fade), 0.88);
        emitGroundSprite(sp, TEX_CHARGE_CIRCLE, origin.add(0, 0.08, 0), fieldR * 0.72, -now * 0.0026, COL_ICE, COL_ICE_HOT, (int) (190 * fade), 0.82);
        if (!finished && m.pullRadius > 0.5) {
            double pullWave = 1.0 - ((age % 360L) / 360.0);
            EffectSprites.shockwave(sp, origin.add(0, 0.12, 0), m.pullRadius * pullWave, -now * 0.0055, pHot(COL_ICE_HOT, 0.3), (int) (210 * (1.0 - pullWave * 0.35)));
        }

        // 2. Swirling Blizzard Snowstorm Mist blanketing the frozen arena!
        int mistFrame = (int) Math.floorMod(now / 110L, 4L);
        for (int i = 0; i < 8; i++) {
            double ang = i * (Math.TAU / 8.0) + now * (i % 2 == 0 ? 0.0018 : -0.0015);
            double dist = m.radius * (i % 2 == 0 ? 0.42 : 0.82);
            double h = 0.85 + 1.35 * (0.5 + 0.5 * Math.sin(now * 0.003 + i * 1.4));
            Vec3 mp = origin.add(Math.cos(ang) * dist, h, Math.sin(ang) * dist);
            emitFramedBillboard(sp, TEX_FROST_MIST, 4, (mistFrame + i) & 3, mp, camera, 2.35 + (i % 3) * 0.45, COL_WHITE, COL_ICE_HOT, (int) (185 * fade), 0.48);
        }

        if (m.morphMode == 1) {
            // 冰封王座·瞬碎: Colossal central 9.5-block ice monolith piercing & shattering!
            for (var event : t.flashes) {
                if (event.phase() != HIT) continue;
                long sAge = now - event.at();
                double su = Math.clamp(sAge / 420.0, 0.0, 0.999);
                int sFrame = finished ? (5 + Math.clamp((int) ((after / (double) ARCTIC_FINISH_MS) * 3), 0, 2)) : Math.clamp((int) (su * 5), 0, 4);
                emitVerticalCrossed(sp, TEX_ARCTIC_SPIRE, sFrame, origin.add(0, 0.04, 0), m.radius * 0.62, 9.8 * EffectSprites.easeOutCubic(Math.min(1.0, su * 2.2)), now * 0.001, 3, COL_WHITE, COL_ICE_HOT, (int) (252 * fade), 0.92);
                break;
            }
        } else {
            // 3. Multi-Wave Faceted Crystal Spire Clusters erupting & shattering across the circle!
            renderArcticSpires(sp, camera, origin, m, t.start.seed(), age, arcticWaveAge(t, age));
        }

        // 4. Grand Freeze Shatter Finisher!
        if (finished) {
            double fu = Math.clamp(after / (double) ARCTIC_FINISH_MS, 0.0, 0.999);
            int burstFrame = Math.clamp((int) (fu * 8), 0, 7);
            int shatterFrame = 5 + Math.clamp((int) (fu * 3), 0, 2);
            double blastR = m.radius * 1.35;

            emitGroundFrame(sp, TEX_ASTRAL_SUPERNOVA, 8, burstFrame, origin.add(0, 0.16, 0), blastR * 1.15, fu * 1.1, COL_ICE, COL_ICE_HOT, (int) (254 * fade), 0.96);
            emitFramedBillboard(sp, TEX_ASTRAL_SUPERNOVA, 8, burstFrame, origin.add(0, 2.1, 0), camera, blastR * 0.92, COL_ICE, COL_ICE_HOT, (int) (248 * fade), 0.95);
            for (int i = 0; i < 6; i++) {
                double ang = i * (Math.TAU / 6.0) + (t.start.seed() & 7) * 0.3;
                Vec3 pt = origin.add(Math.cos(ang) * m.radius * 0.68, 0.04, Math.sin(ang) * m.radius * 0.68);
                emitVerticalCrossed(sp, TEX_ARCTIC_SPIRE, shatterFrame, pt, 1.35, 5.8, ang, 2, COL_WHITE, COL_ICE_HOT, (int) (248 * fade), 0.90);
            }
            EffectSprites.shockwave(sp, origin.add(0, 0.16, 0), blastR * 1.28 * EffectSprites.easeOutCubic(fu), fu * 2.0, pHot(COL_ICE_HOT, 0.1), (int) (252 * fade));
            EffectSprites.shockwave(sp, origin.add(0, 1.85, 0), blastR * 1.02 * EffectSprites.easeOutCubic(fu), -fu * 1.6, pCol(COL_ICE, 0.4), (int) (228 * fade));
            EffectSprites.shockwave(sp, origin.add(0, 3.60, 0), blastR * 0.78 * EffectSprites.easeOutCubic(fu), fu * 2.2, pHot(COL_ICE_HOT, 0.7), (int) (200 * fade));
            EffectSprites.glow(sp, origin.add(0, 1.8, 0), camera, blastR * 1.05 * fade, pHot(COL_ICE_HOT, 0.2), (int) (245 * fade));
        }
    }

    private static void renderArcticSpires(List<SpriteQuad> sp, Vec3 camera, Vec3 origin, Meta meta, long seed, long age, long lastWaveAge) {
        if (!(meta.radius > 0.0)) return;
        long elapsed = age - meta.firstWaveMs;
        long first = Math.max(0L, Math.floorDiv(elapsed - ICE_PILLAR_LIFETIME_MS, meta.waveIntervalMs) + 1L);
        long last = lastArcticWave(meta, lastWaveAge);
        for (long wave = first; wave <= last; wave++) {
            long waveAge = elapsed - wave * meta.waveIntervalMs;
            if (waveAge < 0L || waveAge >= ICE_PILLAR_LIFETIME_MS) continue;
            double u = waveAge / (double) ICE_PILLAR_LIFETIME_MS;
            int frame = Math.clamp((int) (u * 8), 0, 7);
            double fade = u < 0.72 ? 1.0 : (1.0 - u) / 0.28;
            int alpha = Math.clamp((int) Math.round(250 * fade), 0, 255);
            var random = new SplittableRandom(seed + wave * 997L);

            for (int i = 0; i < 4; i++) {
                double angle = random.nextDouble(Math.TAU);
                double distance = Math.sqrt(random.nextDouble()) * (meta.radius * 0.90);
                Vec3 base = origin.add(Math.cos(angle) * distance, 0.04, Math.sin(angle) * distance);
                double height = random.nextDouble(2.7, 5.4);
                double halfW = random.nextDouble(0.82, 1.38);
                emitVerticalCrossed(sp, TEX_ARCTIC_SPIRE, frame, base, halfW, height, angle, 2, COL_WHITE, COL_ICE_HOT, alpha, 0.82);
                emitGroundSprite(sp, TEX_ICE_CIRCLE, base, halfW * 1.15, angle, COL_WHITE, COL_ICE_HOT, (int) (195 * fade), 0.72);
                if (frame == 4 || frame == 5) {
                    EffectSprites.glow(sp, base.add(0, height * 0.45, 0), camera, halfW * 1.25, COL_ICE_HOT, (int) (175 * fade));
                }
            }
        }
    }

    // --- 7. 湮灭黑洞 (Black Hole: Pitch-Black Singularity + 3 Tilted Accretion Disks + Gravitational Collapse!) ---

    private static void renderBlackHole(
        SkillVfx.Track t, Meta m, Vec3 origin, Vec3 point, long age, long now,
        boolean finished, long after, List<SpriteQuad> sp, Vec3 camera
    ) {
        if (!finished) {
            double open = Math.clamp(age / 260.0, 0.0, 1.0);
            double scale = 0.45 + 0.55 * EffectSprites.easeOutCubic(open);
            double pullR = Math.max(m.radius * 1.4, m.pullRadius);
            int coreFrame = (int) Math.floorMod(now / 52L, 8L);
            int diskFrame = (int) Math.floorMod(now / 65L, 4L);

            // Massive gravitational pull distortion field on ground below!
            Vec3 ground = origin.add(0, -1.72, 0);
            emitGroundSprite(sp, TEX_BLACK_HOLE_DISK, ground, pullR * 0.95 * scale, now * 0.0032, COL_WHITE, COL_DARK_HOT, 228, 0.80);
            emitGroundSprite(sp, TEX_CHARGE_CIRCLE, ground.add(0, 0.03, 0), pullR * 0.85 * scale, -now * 0.0024, COL_DARK_DEEP, COL_DARK, 195, 0.75);

            // Inward-contracting suction shockwaves showing gravity pull!
            double pullWave = 1.0 - ((age % 420L) / 420.0);
            EffectSprites.shockwave(sp, ground.add(0, 0.08, 0), pullR * pullWave, -now * 0.005, pHot(COL_DARK_HOT, 0.2), (int) (215 * (1.0 - pullWave * 0.35)));

            // COLOSSAL Pitch-Black Singularity Core + 3 Intersecting Tilted Galactic Accretion Vortex Disks!
            emitTiltedRing(sp, TEX_BLACK_HOLE_DISK, 4, diskFrame, origin, 4.75 * scale, now * 0.0075, 16.0, 10.0, COL_WHITE, COL_DARK_HOT, 240);
            emitTiltedRing(sp, TEX_BLACK_HOLE_DISK, 4, (diskFrame + 1) & 3, origin, 3.65 * scale, -now * 0.0105, -36.0, 26.0, COL_DARK, COL_DARK_HOT, 215);
            emitTiltedRing(sp, TEX_BLACK_HOLE_DISK, 4, (diskFrame + 2) & 3, origin, 2.85 * scale, now * 0.0130, 52.0, -30.0, COL_WHITE, COL_DARK, 195);
            emitFramedBillboard(sp, TEX_BLACK_HOLE_CORE, 8, coreFrame, origin, camera, 2.45 * scale, COL_WHITE, COL_DARK_HOT, 255, 0.90);
            EffectSprites.glow(sp, origin, camera, 4.4 * scale, pCol(COL_DARK, 0.1), 210);

            // 6 High-voltage gravitational void tendrils siphoning inward from the pull perimeter!
            long step = now / 52L;
            for (int i = 0; i < 6; i++) {
                double a = now * 0.0055 + i * (Math.TAU / 6.0);
                double rimDist = pullR * (0.78 + 0.16 * Math.sin(now * 0.008 + i));
                Vec3 rim = origin.add(Math.cos(a) * rimDist, Math.sin(a * 1.8) * 1.45 - 0.35, Math.sin(a) * rimDist);
                LightningBolt.bolt(sp, rim, origin, camera, step ^ t.start.seed() ^ (i * 53L), 6, 0.45, 0.068, i % 2 == 0 ? pHot(COL_DARK_HOT, i * 0.16) : pCol(COL_DARK, i * 0.16), 228, false);
            }

            // Mode 2 volcano bonus underneath black hole
            if (m.volcano) {
                emitGroundSprite(sp, TEX_VOLCANO_CRATER, ground.add(0, 0.05, 0), m.radius * 1.15, now * 0.002, COL_WHITE, COL_FIRE_HOT, 230, 0.85);
                int fFrame = (int) Math.floorMod(now / 70L, 8L);
                emitVerticalCrossed(sp, TEX_FLAME_PILLAR, fFrame, ground, 1.35, 3.8, now * 0.003, 2, COL_WHITE, COL_FIRE_HOT, 210, 0.80);
            }
            renderBlackHoleResonance(t, m, origin, now, sp, camera);
            return;
        }

        // Violent Gravitational Collapse -> Cataclysmic Dark Supernova!
        if (after < 850L) {
            double u = Math.clamp(after / 850.0, 0.0, 0.999);
            double fade = 1.0 - u;
            double blastR = Math.max(m.radius * 1.35, m.blastRadius) * 1.32;
            int burstFrame = Math.clamp((int) (u * 8), 0, 7);

            if (after < 130L) {
                // Brief blinding singularity implosion pinch!
                double pinch = 1.0 - after / 130.0;
                emitFramedBillboard(sp, TEX_BLACK_HOLE_CORE, 8, 0, point, camera, 2.4 * pinch + 0.4, COL_WHITE, COL_WHITE, 255, 1.0);
                EffectSprites.glow(sp, point, camera, 6.2 * (1.0 - pinch * 0.4), COL_DARK_HOT, 252);
            }

            Vec3 ground = point.add(0, -1.68, 0);
            emitGroundSprite(sp, TEX_BLACK_HOLE_DISK, ground, blastR * 1.12, u * 2.2, COL_WHITE, COL_DARK_HOT, (int) (245 * fade), 0.90);
            emitGroundFrame(sp, TEX_ASTRAL_SUPERNOVA, 8, burstFrame, ground.add(0, 0.08, 0), blastR * 1.25, u * 1.2, COL_DARK, COL_DARK_HOT, (int) (254 * fade), 0.96);
            emitFramedBillboard(sp, TEX_ASTRAL_SUPERNOVA, 8, burstFrame, point, camera, blastR * 1.10, COL_DARK, COL_DARK_HOT, (int) (252 * fade), 0.96);

            EffectSprites.shockwave(sp, ground.add(0, 0.14, 0), blastR * 1.32 * EffectSprites.easeOutCubic(u), u * 2.2, pHot(COL_DARK_HOT, 0.1), (int) (252 * fade));
            EffectSprites.shockwave(sp, point, blastR * 1.12 * EffectSprites.easeOutCubic(u), -u * 1.8, pCol(COL_DARK, 0.4), (int) (235 * fade));
            EffectSprites.shockwave(sp, point.add(0, 1.8, 0), blastR * 0.85 * EffectSprites.easeOutCubic(u), u * 2.4, pHot(COL_DARK_HOT, 0.7), (int) (205 * fade));
            EffectSprites.glow(sp, point, camera, blastR * 1.05 * fade, pHot(COL_DARK_HOT, 0.2), (int) (245 * fade));

            for (int i = 0; i < 8; i++) {
                double ang = i * (Math.TAU / 8.0) + u * 0.4;
                Vec3 edge = point.add(Math.cos(ang) * blastR, Math.sin(i * 2.1) * 1.2, Math.sin(ang) * blastR);
                LightningBolt.bolt(sp, point, edge, camera, (now / 48L) ^ t.start.seed() ^ (i * 89L), 7, 0.55, 0.082, i % 2 == 0 ? pHot(COL_DARK_HOT, i * 0.12) : pCol(COL_DARK, i * 0.12), (int) (240 * fade), false);
            }
        }

        // Mode 1 lingering gravity sub-hole
        if (m.subHoleMs > 0L && after < m.subHoleMs) {
            double subFade = Math.clamp(1.0 - after / (double) m.subHoleMs, 0.0, 1.0);
            int coreFrame = (int) Math.floorMod(now / 58L, 8L);
            int diskFrame = (int) Math.floorMod(now / 72L, 4L);
            emitTiltedRing(sp, TEX_BLACK_HOLE_DISK, 4, diskFrame, point, 2.8 * (0.65 + 0.35 * subFade), now * 0.011, 18.0, 12.0, COL_WHITE, COL_DARK_HOT, (int) (220 * subFade));
            emitFramedBillboard(sp, TEX_BLACK_HOLE_CORE, 8, coreFrame, point, camera, 1.35 * (0.65 + 0.35 * subFade), COL_WHITE, COL_DARK_HOT, (int) (245 * subFade), 0.88);
        }
        renderBlackHoleResonance(t, m, point, now, sp, camera);
    }

    private static void renderBlackHoleResonance(SkillVfx.Track t, Meta m, Vec3 center, long now, List<SpriteQuad> sp, Vec3 camera) {
        if (m.resonanceFlashes.isEmpty()) return;
        double blastR = Math.max(m.radius * 1.35, m.blastRadius) * 1.22;
        for (long at : m.resonanceFlashes) {
            long rAge = now - at;
            if (rAge < 0L || rAge >= 480L) continue;
            double u = Math.clamp(rAge / 480.0, 0.0, 0.999);
            double fade = 1.0 - u;
            int burstFrame = Math.clamp((int) (u * 8), 0, 7);
            emitGroundFrame(sp, TEX_ASTRAL_SUPERNOVA, 8, burstFrame, center.add(0, -1.62, 0), blastR * 1.10, u * 1.4, COL_DARK, COL_DARK_HOT, (int) (240 * fade), 0.92);
            EffectSprites.shockwave(sp, center.add(0, -1.55, 0), blastR * 1.25 * EffectSprites.easeOutCubic(u), u * 2.1, pHot(COL_DARK_HOT, 0.2), (int) (245 * fade));
            EffectSprites.shockwave(sp, center, blastR * 0.95 * EffectSprites.easeOutCubic(u), -u * 1.7, pCol(COL_DARK, 0.6), (int) (220 * fade));
            for (int i = 0; i < 6; i++) {
                double ang = i * (Math.TAU / 6.0) + u * 0.6;
                Vec3 edge = center.add(Math.cos(ang) * blastR * 0.90, Math.sin(i * 1.7) * 0.9, Math.sin(ang) * blastR * 0.90);
                LightningBolt.bolt(sp, center, edge, camera, (now / 45L) ^ t.start.seed() ^ (i * 59L) ^ at, 6, 0.48, 0.070, pHot(COL_DARK_HOT, i * 0.16), (int) (230 * fade), false);
            }
        }
    }

    // --- 8. 天雷 (Lightning Strike: 24-Block Sky-Splitting Heavenly Thunder Pillars + Branching Storm!) ---

    private static void renderLightningStrike(
        SkillVfx.Track t, Meta m, Vec3 point, long now,
        boolean finished, long after, List<SpriteQuad> sp, Vec3 camera
    ) {
        if (m.cloud) {
            // Mode 2 Storm Cloud built from layered dark storm mist + crackling orb core!
            double fade = finished ? Math.clamp(1.0 - after / 450.0, 0.0, 1.0) : 1.0;
            Vec3 cloudPos = finished ? point : (t.last.phase() == HIT ? point.add(0, 5.8, 0) : point);
            int mFrame = (int) Math.floorMod(now / 120L, 4L);
            for (int i = 0; i < 5; i++) {
                double ang = i * (Math.TAU / 5.0) + now * 0.0014;
                double d = i == 0 ? 0.0 : 1.65;
                Vec3 mp = cloudPos.add(Math.cos(ang) * d, (i & 1) * 0.35, Math.sin(ang) * d);
                emitFramedBillboard(sp, TEX_FROST_MIST, 4, (mFrame + i) & 3, mp, camera, i == 0 ? 2.85 : 2.15, COL_CLOUD_DARK, COL_DARK, (int) (235 * fade), 0.35);
            }
            emitFramedBillboard(sp, TEX_VORTEX_ORB, 8, (int) Math.floorMod(now / 45L, 8L), cloudPos.add(0, -0.35, 0), camera, 1.35, COL_WHITE, COL_LIGHT_HOT, (int) (225 * fade), 0.90);
            EffectSprites.glow(sp, cloudPos, camera, 3.8, COL_LIGHT, (int) (185 * fade));
        }

        for (var event : t.flashes) {
            if (event.phase() != HIT) continue;
            long boltAge = now - event.at();
            if (boltAge < 0L || boltAge >= 720L) continue;
            double u = Math.clamp(boltAge / 720.0, 0.0, 0.999);
            double fastU = Math.clamp(boltAge / 420.0, 0.0, 0.999);
            double fade = 1.0 - u;
            int pillarFrame = Math.clamp((int) (u * 8), 0, 7);
            Vec3 ground = vector(event.position()).add(0, 0.05, 0);
            Vec3 sky = ground.add(0, 24.0, 0);
            long seed = t.start.seed() ^ ((long) event.sequence() * 0x9E3779B97F4A7C15L);
            double r = m.radius * (m.morphMode == 1 ? 1.25 : 1.08);

            // Blazing golden sky-judgment seal + scorched impact flash!
            emitGroundSprite(sp, TEX_THUNDER_CIRCLE, ground, r * 1.28, now * 0.004 + event.sequence(), COL_WHITE, COL_LIGHT_HOT, (int) (252 * fade), 0.95);
            emitGroundFrame(sp, TEX_ASTRAL_SUPERNOVA, 8, pillarFrame, ground.add(0, 0.14, 0), r * 1.42, u * 1.3, COL_LIGHT, COL_LIGHT_HOT, (int) (254 * fade), 0.98);

            // COLOSSAL 24-block Sky-Splitting Heavenly Thunder Column (3 axial planes!)
            emitVerticalCrossed(sp, TEX_SKY_LIGHTNING, pillarFrame, ground, r * 0.68, 24.0, now * 0.003 + event.sequence(), 3, COL_WHITE, COL_LIGHT_HOT, (int) (255 * fade), 0.98);
            EffectSprites.texturedBeam(sp, sky, ground, camera, r * 0.30 * fade + 0.08, now, true, pHot(COL_LIGHT_HOT, 0.2), (int) (245 * fade));

            // Multi-strand jagged branching sky lightning + spiraling high-voltage helix coils!
            long flick = (boltAge / 42L) ^ seed;
            LightningBolt.bolt(sp, sky, ground, camera, flick, 16, 1.45, 0.24 * fade + 0.06, pCol(COL_LIGHT, 0.0), (int) (255 * fade), true);
            LightningBolt.bolt(sp, sky.add(0.7, 0, -0.7), ground, camera, flick + 13L, 14, 1.05, 0.14 * fade + 0.04, pHot(COL_LIGHT_CYAN, 0.35), (int) (248 * fade), true);
            LightningBolt.bolt(sp, sky.add(-0.6, 0, 0.6), ground, camera, flick + 29L, 12, 0.90, 0.10 * fade + 0.03, pHot(COL_WHITE, 0.7), (int) (240 * fade), false);
            LightningBolt.helixArcs(sp, ground, sky, camera, now, r * 0.42, 2, pHot(COL_LIGHT_CYAN, 0.5), (int) (235 * fade));

            // Triple stacked thunder shockwaves + blinding impact bloom!
            EffectSprites.shockwave(sp, ground.add(0, 0.14, 0), r * 1.55 * EffectSprites.easeOutCubic(Math.min(1.0, u * 1.45)), u * 1.9, pHot(COL_LIGHT_HOT, 0.1), (int) (252 * fade));
            EffectSprites.shockwave(sp, ground.add(0, 1.90, 0), r * 1.15 * EffectSprites.easeOutCubic(u), -u * 1.5, pHot(COL_LIGHT_CYAN, 0.4), (int) (228 * fade));
            EffectSprites.shockwave(sp, ground.add(0, 4.20, 0), r * 0.85 * EffectSprites.easeOutCubic(u), u * 2.1, pCol(COL_LIGHT, 0.7), (int) (198 * fade));
            if (boltAge < 420L) {
                EffectSprites.glow(sp, ground.add(0, 1.4, 0), camera, r * 1.65 * (1.0 - fastU), pHot(COL_LIGHT_HOT, 0.2), 252);
            }
        }
    }

    // --- 9. 陨星幻灭 (Astral Storm Awakening: Overhead Celestial Rift + 4-Element Meteors + Chaotic Main Star Core!) ---

    private static void renderAstralStorm(
        SkillVfx.Track t, Meta m, Vec3 origin, long age, long now,
        boolean finished, long after, List<SpriteQuad> sp, Vec3 camera
    ) {
        double fade = finished ? Math.clamp(1.0 - after / (double) ASTRAL_FINISH_MS, 0.0, 1.0) : 1.0;
        if (fade <= 0.01) return;

        double open = Math.clamp(age / 360.0, 0.0, 1.0);
        double fieldR = m.fieldRadius * 1.14 * (0.42 + 0.58 * EffectSprites.easeOutCubic(open));

        // 1. Colossal Dual Counter-Rotating 10+ Block Grand 4-Element Astral Circle!
        emitGroundSprite(sp, TEX_ASTRAL_CIRCLE, origin.add(0, 0.06, 0), fieldR, now * 0.0011, COL_WHITE, COL_ASTRAL_GOLD, (int) (248 * fade), 0.88);
        emitGroundSprite(sp, TEX_CHARGE_CIRCLE, origin.add(0, 0.10, 0), fieldR * 0.70, -now * 0.0020, COL_PRISM, COL_ASTRAL_GOLD, (int) (200 * fade), 0.82);

        // 4 Towering Cardinal Elemental Beacons at N/E/S/W perimeter of the Grand Astral Circle!
        for (int e = 0; e < 4; e++) {
            double ang = e * (Math.PI * 0.5) + now * 0.0011;
            Vec3 bp = origin.add(Math.cos(ang) * fieldR * 0.90, 2.2 + 0.35 * Math.sin(now * 0.005 + e), Math.sin(ang) * fieldR * 0.90);
            emitFramedBillboard(sp, TEX_RUNE_LAMP, 4, e, bp, camera, 1.15, COL_WHITE, elemHot(e), (int) (240 * fade), 0.88);
            EffectSprites.glow(sp, bp, camera, 2.1, elemColor(e), (int) (185 * fade));
            EffectSprites.texturedBeam(sp, new Vec3(bp.x, origin.y + 0.08, bp.z), bp.add(0, 4.5, 0), camera, 0.24, now, false, elemColor(e), (int) (145 * fade));
        }

        // 2. Colossal Overhead Celestial Rift / Star Portal (y + 14.2)!
        Vec3 skyCenter = origin.add(0, 14.2, 0);
        double riftR = m.fieldRadius * 0.96 * (0.45 + 0.55 * EffectSprites.easeOutCubic(open));
        emitGroundSprite(sp, TEX_ASTRAL_RIFT, skyCenter, riftR, -now * 0.0018, COL_WHITE, COL_PRISM, (int) (245 * fade), 0.85);
        emitGroundSprite(sp, TEX_ASTRAL_CIRCLE, skyCenter.add(0, -0.18, 0), riftR * 0.72, now * 0.0026, COL_WHITE, COL_ASTRAL_GOLD, (int) (190 * fade), 0.80);

        // 3. Levitating Archmage Ascendant Starlight Pillar!
        var mc = Minecraft.getInstance();
        Player caster = mc == null || mc.level == null ? null : mc.level.getPlayerByUUID(t.start.caster());
        if (caster != null && caster.isAlive() && !finished) {
            Vec3 cp = PlayerVisualAnchor.position(caster);
            emitGroundSprite(sp, TEX_ASTRAL_CIRCLE, cp.add(0, -0.10, 0), 2.35, now * 0.0038, COL_WHITE, COL_ASTRAL_GOLD, 220, 0.88);
            EffectSprites.glow(sp, cp.add(0, 1.2, 0), camera, 2.6, COL_PRISM, 165);
        }

        // 4. 4-Element Meteor Barrage plunging from the Overhead Celestial Rift!
        for (var meteor : m.meteors) {
            drawAstralMeteor(sp, camera, meteor, m, skyCenter, now);
        }

        // 5. Pre-FINISH Descending Colossal Chaotic Main Star Core (final 650ms before detonation!)
        long stormDurMs = Math.max(1200L, t.start.duration() - ASTRAL_FINISH_MS);
        if (!finished && age >= stormDurMs - CHAOS_CORE_DESCENT_MS) {
            double cu = Math.clamp((age - (stormDurMs - CHAOS_CORE_DESCENT_MS)) / (double) CHAOS_CORE_DESCENT_MS, 0.0, 1.0);
            double easeDrop = cu * cu * (0.30 + 0.70 * cu);
            Vec3 corePos = skyCenter.lerp(origin.add(0, 2.4, 0), easeDrop);
            int coreFrame = (int) Math.floorMod(now / 42L, 8L);
            double coreR = 2.4 + 2.1 * cu;

            // Blinding 4-element Chaotic Main Star Core descending straight from the Star Portal!
            emitTiltedRing(sp, TEX_BLACK_HOLE_DISK, 4, (int) Math.floorMod(now / 60L, 4L), corePos, coreR * 1.55, now * 0.010, 22.0, 14.0, COL_ASTRAL_GOLD, COL_PRISM, 235);
            emitTiltedRing(sp, TEX_CHARGE_CIRCLE, 1, 0, corePos, coreR * 1.35, -now * 0.012, -34.0, -20.0, COL_ICE_HOT, COL_FIRE_HOT, 225);
            emitFramedBillboard(sp, TEX_ASTRAL_CORE, 8, coreFrame, corePos, camera, coreR, COL_WHITE, COL_ASTRAL_GOLD, 255, 0.92);
            EffectSprites.glow(sp, corePos, camera, coreR * 2.2, COL_ASTRAL_GOLD, 245);
            EffectSprites.texturedBeam(sp, skyCenter, corePos, camera, 0.95 * cu + 0.25, now, true, COL_PRISM, 235);
            LightningBolt.helixArcs(sp, corePos, skyCenter, camera, now, coreR * 0.65, 3, COL_ASTRAL_GOLD, 235);
            EffectSprites.shockwave(sp, origin.add(0, 0.14, 0), m.finisherRadius * (1.05 - 0.85 * cu), -now * 0.008, COL_ASTRAL_GOLD, (int) (240 * cu));
        }

        // 6. Grand Finale: Colossal 4-Element Chaotic Main Star Core Supernova Detonation!
        if (finished) {
            double fu = Math.clamp(after / (double) ASTRAL_FINISH_MS, 0.0, 0.999);
            double fastU = Math.clamp(after / 520.0, 0.0, 0.999);
            int burstFrame = Math.clamp((int) (fu * 8), 0, 7);
            double blastR = m.finisherRadius * 1.24;
            Vec3 burstCenter = origin.add(0, 2.2, 0);

            // Expanding blinding Chaotic Core shatter in first 520ms!
            if (after < 520L) {
                double cFade = 1.0 - fastU;
                emitFramedBillboard(sp, TEX_ASTRAL_CORE, 8, Math.clamp((int) (fastU * 8), 0, 7), burstCenter, camera, 4.6 + 4.4 * EffectSprites.easeOutCubic(fastU), COL_WHITE, COL_ASTRAL_GOLD, (int) (255 * cFade), 0.98);
                EffectSprites.glow(sp, burstCenter, camera, 10.5 * cFade, COL_ASTRAL_GOLD, (int) (252 * cFade));
            }

            // Colossal prismatic Supernova bursts (ground flat + upright billboard + blazing sky pillar!)
            emitGroundFrame(sp, TEX_ASTRAL_SUPERNOVA, 8, burstFrame, origin.add(0, 0.18, 0), blastR * 1.18, fu * 1.1, COL_PRISM, COL_ASTRAL_GOLD, (int) (255 * fade), 0.98);
            emitFramedBillboard(sp, TEX_ASTRAL_SUPERNOVA, 8, burstFrame, burstCenter, camera, blastR * 0.95, COL_ASTRAL_GOLD, COL_WHITE, (int) (252 * fade), 0.98);
            emitVerticalCrossed(sp, TEX_SKY_LIGHTNING, burstFrame, origin.add(0, 0.06, 0), 3.4, 22.0, now * 0.003, 3, COL_PRISM, COL_ASTRAL_GOLD, (int) (245 * fade), 0.95);
            emitVerticalCrossed(sp, TEX_FLAME_PILLAR, burstFrame, origin.add(0, 0.06, 0), 4.2, 13.5, -now * 0.002, 3, COL_WHITE, COL_FIRE_HOT, (int) (220 * fade), 0.85);

            // 4 Staggered Elemental Shockwave Rings (Light / Fire / Ice / Dark) sweeping across entire arena!
            for (int e = 0; e < 4; e++) {
                double expand = blastR * Math.min(1.18, EffectSprites.easeOutCubic(Math.min(1.0, after / 520.0 + 0.08 * e)));
                EffectSprites.shockwave(sp, origin.add(0, 0.22 + e * 1.25, 0), expand, (e % 2 == 0 ? 1 : -1) * (now * 0.0045 + e), elemHot(e), (int) (250 * fade));
            }

            // 12 Radial 4-Element Cosmic Thunder Discharges tearing across the 11+ block battlefield!
            for (int i = 0; i < 12; i++) {
                int e = i & 3;
                double ang = i * (Math.TAU / 12.0) + fu * 0.30;
                double rad = blastR * (0.65 + 0.35 * EffectSprites.easeOutCubic(fu));
                Vec3 edge = origin.add(Math.cos(ang) * rad, 0.35 + 1.5 * Math.sin(i * 1.9), Math.sin(ang) * rad);
                LightningBolt.bolt(sp, burstCenter, edge, camera, t.start.seed() ^ (i * 199L) ^ (after / 45L), 8, 0.92, 0.11, elemHot(e), (int) (242 * fade), false);
            }
        }
    }

    private static void drawAstralMeteor(List<SpriteQuad> sp, Vec3 camera, FallingMeteor meteor, Meta meta, Vec3 skyCenter, long now) {
        long age = now - meteor.startedAt();
        if (age < 0L || age >= meta.fallMs + METEOR_IMPACT_MS) return;
        Vec3 impact = meteor.impact();
        int e = Math.clamp(meteor.element(), 0, 3);
        int col = elemColor(e);
        int hot = elemHot(e);

        if (age >= meta.fallMs) {
            // Element-Specific Dramatic Meteor Impact Eruption!
            long impAge = age - meta.fallMs;
            double u = Math.clamp(impAge / (double) METEOR_IMPACT_MS, 0.0, 0.999);
            double fade = 1.0 - u;
            int frame = Math.clamp((int) (u * 8), 0, 7);
            double r = meta.meteorRadius * 1.15;
            Vec3 g = impact.add(0, 0.06, 0);

            EffectSprites.shockwave(sp, g.add(0, 0.08, 0), r * 1.25 * EffectSprites.easeOutCubic(u), u * 1.8, hot, (int) (248 * fade));
            EffectSprites.glow(sp, impact.add(0, 1.3, 0), camera, r * 1.15 * fade, hot, (int) (235 * fade));

            switch (e) {
                case 0 -> {
                    // Light Meteor: golden thunder column + branching bolt!
                    emitGroundSprite(sp, TEX_THUNDER_CIRCLE, g, r, meteor.sequence() * 0.9, COL_WHITE, COL_LIGHT_HOT, (int) (235 * fade), 0.90);
                    emitVerticalCrossed(sp, TEX_SKY_LIGHTNING, frame, g, r * 0.45, 11.5, meteor.sequence(), 2, COL_WHITE, COL_LIGHT_HOT, (int) (248 * fade), 0.95);
                    LightningBolt.bolt(sp, g.add(0, 12.0, 0), g, camera, (impAge / 48L) ^ meteor.sequence(), 8, 0.75, 0.12 * fade + 0.03, COL_LIGHT_HOT, (int) (240 * fade), true);
                }
                case 1 -> {
                    // Fire Meteor: roaring volcanic flame pillar + magma sunburst!
                    emitGroundSprite(sp, TEX_VOLCANO_CRATER, g, r, meteor.sequence() * 0.9, COL_WHITE, COL_FIRE_HOT, (int) (240 * fade), 0.80);
                    emitGroundFrame(sp, TEX_JACK_BURST, 8, frame, g.add(0, 0.08, 0), r * 1.22, u * 0.9, COL_WHITE, COL_FIRE_HOT, (int) (250 * fade), 0.92);
                    emitVerticalCrossed(sp, TEX_FLAME_PILLAR, frame, g, r * 0.62, 7.6, meteor.sequence(), 2, COL_WHITE, COL_FIRE_HOT, (int) (248 * fade), 0.90);
                }
                case 2 -> {
                    // Ice Meteor: erupting multi-faceted arctic crystal spire cluster + frost mist!
                    emitGroundSprite(sp, TEX_ICE_CIRCLE, g, r, meteor.sequence() * 0.9, COL_WHITE, COL_ICE_HOT, (int) (238 * fade), 0.85);
                    emitVerticalCrossed(sp, TEX_ARCTIC_SPIRE, frame, g, r * 0.68, 6.4, meteor.sequence(), 2, COL_WHITE, COL_ICE_HOT, (int) (250 * fade), 0.88);
                    emitFramedBillboard(sp, TEX_FROST_MIST, 4, (int) Math.floorMod(now / 90L, 4L), impact.add(0, 1.4, 0), camera, r * 0.85, COL_WHITE, COL_ICE_HOT, (int) (200 * fade), 0.55);
                }
                default -> {
                    // Dark Meteor: abyssal gravity burst + violet supernova!
                    emitGroundSprite(sp, TEX_BLACK_HOLE_DISK, g, r * 1.08, u * 2.2, COL_WHITE, COL_DARK_HOT, (int) (240 * fade), 0.88);
                    emitFramedBillboard(sp, TEX_ASTRAL_SUPERNOVA, 8, frame, impact.add(0, 1.2, 0), camera, r * 1.10, COL_DARK, COL_DARK_HOT, (int) (248 * fade), 0.95);
                    LightningBolt.bolt(sp, impact.add(0, 2.2, 0), g.add(Math.cos(meteor.sequence()) * r * 0.8, 0.3, Math.sin(meteor.sequence()) * r * 0.8), camera, (impAge / 50L) ^ meteor.sequence(), 5, 0.40, 0.072, COL_DARK_HOT, (int) (228 * fade), false);
                }
            }
            return;
        }

        // Plunging 4-Element Meteor streaking down from the Overhead Celestial Rift!
        double progress = age / (double) Math.max(1L, meta.fallMs);
        double angle = meteor.sequence() * 1.75;
        Vec3 launchPos = new Vec3(
            impact.x + Math.cos(angle) * 3.8,
            Math.max(impact.y + 13.5, skyCenter.y),
            impact.z + Math.sin(angle) * 3.8
        );
        Vec3 position = launchPos.lerp(impact.add(0, 1.0, 0), progress * (0.48 + 0.52 * progress));
        Vec3 tailDir = launchPos.subtract(impact).normalize();
        double radius = Math.clamp(meta.meteorRadius * 0.42, 1.25, 2.15);

        // Landing elemental target seal + contracting warning ring
        emitGroundSprite(sp, TEX_CHARGE_CIRCLE, impact.add(0, 0.06, 0), meta.meteorRadius * 0.82, now * 0.005, col, hot, 205, 0.82);
        EffectSprites.shockwave(sp, impact.add(0, 0.10, 0), meta.meteorRadius * 0.82 * (1.0 - 0.72 * progress), -progress * 1.6, hot, 210);

        // Bespoke elemental meteor boulder + blazing dual atmospheric trail!
        emitFramedBillboard(sp, TEX_ASTRAL_METEOR, 4, e, position, camera, radius, COL_WHITE, hot, 255, 0.78);
        EffectSprites.glow(sp, position, camera, radius * 1.95, col, 235);
        RibbonTrail.ribbon(sp, List.of(position, position.add(tailDir.scale(3.2)), position.add(tailDir.scale(7.2))), camera, radius * 0.78, col, 240);
        RibbonTrail.ribbon(sp, List.of(position, position.add(tailDir.scale(2.4)), position.add(tailDir.scale(5.2))), camera, radius * 0.36, hot, 235);
    }

    // --- 陨星幻灭 Multi-Stage Cinematic 3rd-Person Camera (with Wall-Clip Safety) ---

    private static SkillVfx.CameraPose astralStormCamera(SkillVfx.Track t, long time) {
        var mc = Minecraft.getInstance();
        if (t == null || mc == null || mc.level == null) return null;
        long age = Math.max(0L, time - t.start.startedAt());
        boolean finished = t.last.phase() == FINISH;
        long finAge = finished ? Math.max(0L, time - t.last.at()) : 0L;

        Vec3 fieldCenter = vector(t.start.position());
        var caster = mc.level.getEntity(t.start.caster());
        Vec3 casterPos = caster != null
            ? (caster instanceof Player p ? PlayerVisualAnchor.position(p) : caster.position())
            : fieldCenter.add(0, 3.5, 0);
        Vec3 dir = horizontalDir(vector(t.start.direction()));
        Vec3 side = rightOf(dir);

        // Stage 1 (0..650ms): Dramatic ascending hero frame -> Stage 2: Sweeping wide orbital panorama -> Stage 3: Supernova pull-back!
        double ascend = EffectSprites.easeOutCubic(Math.clamp(age / 650.0, 0.0, 1.0));
        double finPull = finished ? EffectSprites.easeOutCubic(Math.clamp(finAge / 550.0, 0.0, 1.0)) : 0.0;
        double orbit = Math.sin(age * 0.00055) * 3.6;

        Vec3 focus = casterPos.lerp(fieldCenter.add(0, 2.8, 0), 0.52 * ascend + 0.25 * finPull);
        double backDist = lerp(7.8, 13.6, ascend) + 3.0 * finPull;
        double height = lerp(3.4, 9.2, ascend) + 2.4 * finPull;
        Vec3 camPos = focus.subtract(dir.scale(backDist)).add(side.scale(2.8 + orbit)).add(0, height, 0);

        if (mc.player != null) {
            var wall = mc.level.clip(new ClipContext(focus, camPos, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
            if (wall.getType() != HitResult.Type.MISS) {
                camPos = wall.getLocation().add(focus.subtract(camPos).normalize().scale(0.35));
            }
        }
        Vec3 look = focus.subtract(camPos);
        float yaw = (float) Math.toDegrees(Math.atan2(-look.x, look.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(look.y, look.horizontalDistance()));
        return new SkillVfx.CameraPose(camPos, yaw, pitch);
    }

    // --- Arctic Wave Audio Sync Helpers --------------------------------------

    private static long arcticWaveAge(SkillVfx.Track track, long age) {
        return track.last.phase() == FINISH ? Math.min(age, track.last.at() - track.start.startedAt()) : age;
    }

    private static long lastArcticWave(Meta meta, long age) {
        return age < meta.firstWaveMs ? -1L : Math.min(meta.waveCount - 1L, (age - meta.firstWaveMs) / meta.waveIntervalMs);
    }

    private static void playArcticWaves(SkillVfx.Track track, long time) {
        Meta meta = METAS.get(track.start.cast());
        if (meta == null || meta.morphMode == 1) return;
        long age = time - track.start.startedAt();
        long last = lastArcticWave(meta, arcticWaveAge(track, age));
        if (last < meta.nextWaveSound) return;

        long first = Math.max(meta.nextWaveSound, Math.ceilDiv(age - meta.firstWaveMs - 100L, meta.waveIntervalMs));
        for (long wave = first; wave <= last; wave++) {
            SkillSound.playAt(track.start.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 0.65f, 1.35f);
        }
        meta.nextWaveSound = last + 1L;
    }

    // --- Reusable Dual-Layer Sprite Emitters (Zero Legacy WorldQuads) --------

    /** Ground-anchored N-plane volumetric vertical spike / flame column / sky lightning pillar (v=0 sky top, v=1 ground base). */
    private static void emitVerticalCrossed(
        List<SpriteQuad> out, Identifier texture, int frame,
        Vec3 base, double halfWidth, double height, double yawOffset, int planes,
        int baseRgb, int boostRgb, int alpha, double boostScale
    ) {
        if (halfWidth <= 0.0 || height <= 0.0 || alpha <= 4) return;
        int bRgb = pCol(baseRgb, 0.0);
        int hRgb = pHot(boostRgb, 0.35);
        float u0 = frame / 8.0f;
        float u1 = (frame + 1) / 8.0f;
        Vec3 center = base.add(0, height * 0.5, 0);
        Vec3 halfV = new Vec3(0, -height * 0.5, 0);
        int a = Math.clamp(alpha, 0, 255);
        int ba = Math.clamp((int) Math.round(a * boostScale), 0, 255);

        for (int i = 0; i < planes; i++) {
            double ang = yawOffset + i * (Math.PI / planes);
            Vec3 halfU = new Vec3(Math.cos(ang) * halfWidth, 0, Math.sin(ang) * halfWidth);
            EffectSprites.emitPlane(out, texture, Blend.TRANSLUCENT, center, halfU, halfV, u0, 0f, u1, 1f, bRgb, a);
            if (ba > 4 && i < 2) {
                EffectSprites.emitPlane(out, texture, Blend.ADDITIVE, center, halfU.scale(0.98), halfV, u0, 0f, u1, 1f, hRgb, ba);
            }
        }
    }

    /** Dual-layer single-frame ground magic circle / volcanic crater / celestial rift. */
    private static void emitGroundSprite(
        List<SpriteQuad> out, Identifier texture, Vec3 center, double radius, double rotRad,
        int baseRgb, int boostRgb, int alpha, double boostScale
    ) {
        if (radius <= 0.0 || alpha <= 4) return;
        int bRgb = pCol(baseRgb, 0.15);
        int hRgb = pHot(boostRgb, 0.50);
        int a = Math.clamp(alpha, 0, 255);
        EffectSprites.emitGround(out, texture, Blend.TRANSLUCENT, center, radius, rotRad, 0f, 0f, 1f, 1f, bRgb, a);
        int ba = Math.clamp((int) Math.round(a * boostScale), 0, 255);
        if (ba > 4) {
            EffectSprites.emitGround(out, texture, Blend.ADDITIVE, center.add(0, 0.015, 0), radius * 0.99, rotRad, 0f, 0f, 1f, 1f, hRgb, ba);
        }
    }

    /** Dual-layer multi-frame horizontal burst (lava sunburst / supernova shock). */
    private static void emitGroundFrame(
        List<SpriteQuad> out, Identifier texture, int totalFrames, int frame,
        Vec3 center, double radius, double rotRad,
        int baseRgb, int boostRgb, int alpha, double boostScale
    ) {
        if (radius <= 0.0 || alpha <= 4) return;
        int bRgb = pCol(baseRgb, 0.25);
        int hRgb = pHot(boostRgb, 0.60);
        float u0 = frame / (float) totalFrames;
        float u1 = (frame + 1) / (float) totalFrames;
        int a = Math.clamp(alpha, 0, 255);
        EffectSprites.emitGround(out, texture, Blend.TRANSLUCENT, center, radius, rotRad, u0, 0f, u1, 1f, bRgb, a);
        int ba = Math.clamp((int) Math.round(a * boostScale), 0, 255);
        if (ba > 4) {
            EffectSprites.emitGround(out, texture, Blend.ADDITIVE, center.add(0, 0.018, 0), radius * 1.02, rotRad, u0, 0f, u1, 1f, hRgb, ba);
        }
    }

    /** Tilted 3D spinning galactic accretion disk / arcane planetary ring. */
    private static void emitTiltedRing(
        List<SpriteQuad> out, Identifier texture, int totalFrames, int frame,
        Vec3 center, double radius, double spinRad, double pitchDeg, double rollDeg,
        int baseRgb, int boostRgb, int alpha
    ) {
        if (radius <= 0.0 || alpha <= 4) return;
        int bRgb = pCol(baseRgb, 0.40);
        int hRgb = pHot(boostRgb, 0.75);
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

        EffectSprites.emitPlane(out, texture, Blend.TRANSLUCENT, center, halfU, halfV, u0, 0f, u1, 1f, bRgb, (int) (a * 0.72));
        EffectSprites.emitPlane(out, texture, Blend.ADDITIVE, center, halfU.scale(1.03), halfV.scale(1.03), u0, 0f, u1, 1f, hRgb, a);
    }

    /** Upright camera-facing dual-layer billboard for horizontal sprite strips (keeps v=0 at top!). */
    private static void emitFramedBillboard(
        List<SpriteQuad> out, Identifier texture, int totalFrames, int frame,
        Vec3 center, Vec3 camera, double radius,
        int baseRgb, int boostRgb, int alpha, double boostScale
    ) {
        if (radius <= 0.0 || alpha <= 4) return;
        int bRgb = pCol(baseRgb, 0.10);
        int hRgb = pHot(boostRgb, 0.45);
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

        EffectSprites.emitPlane(out, texture, Blend.TRANSLUCENT, center, halfU, halfV, u0, 0f, u1, 1f, bRgb, a);
        int ba = Math.clamp((int) Math.round(a * boostScale), 0, 255);
        if (ba > 4) {
            EffectSprites.emitPlane(out, texture, Blend.ADDITIVE, center.add(look.scale(0.028)), halfU.scale(1.03), halfV.scale(1.03), u0, 0f, u1, 1f, hRgb, ba);
        }
    }

    // --- Pure Palette & Geometry Utilities -----------------------------------

    private static int pCol(int normalRgb, double phase) {
        return activePrism ? prismMain(activeNow, phase) : normalRgb;
    }

    private static int pHot(int normalRgb, double phase) {
        return activePrism ? prismHot(activeNow, phase) : normalRgb;
    }

    /** Iridescent 圣灵色 (Holy Spirit Prism) palette cycling luminous lavender-pearl, astral gold, sky cyan & rose. */
    private static int prismMain(long now, double phase) {
        double t = (now * 0.0022 + phase * Math.TAU) % Math.TAU;
        int r = (int) Math.round(218 + 36 * Math.sin(t));
        int g = (int) Math.round(196 + 44 * Math.sin(t + 2.094));
        int b = (int) Math.round(236 + 19 * Math.sin(t + 4.188));
        return (Math.clamp(r, 0, 255) << 16) | (Math.clamp(g, 0, 255) << 8) | Math.clamp(b, 0, 255);
    }

    private static int prismHot(long now, double phase) {
        double t = (now * 0.0030 + phase * Math.TAU) % Math.TAU;
        int r = (int) Math.round(240 + 15 * Math.sin(t));
        int g = (int) Math.round(228 + 25 * Math.sin(t + 1.8));
        int b = (int) Math.round(245 + 10 * Math.sin(t + 3.9));
        return (Math.clamp(r, 0, 255) << 16) | (Math.clamp(g, 0, 255) << 8) | Math.clamp(b, 0, 255);
    }

    private static int elemColor(int element) {
        return switch (element) {
            case 0 -> COL_LIGHT;
            case 1 -> COL_FIRE;
            case 2 -> COL_ICE;
            case 3 -> COL_DARK;
            default -> COL_PRISM;
        };
    }

    private static int elemHot(int element) {
        return switch (element) {
            case 0 -> COL_LIGHT_HOT;
            case 1 -> COL_FIRE_HOT;
            case 2 -> COL_ICE_HOT;
            case 3 -> COL_DARK_HOT;
            default -> COL_ASTRAL_GOLD;
        };
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
