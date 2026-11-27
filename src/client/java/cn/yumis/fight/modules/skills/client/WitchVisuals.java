package cn.yumis.fight.modules.skills.client;

import cn.yumis.fight.modules.presentation.api.client.CameraShake;
import cn.yumis.fight.modules.presentation.api.client.ClientUi;
import cn.yumis.fight.modules.presentation.api.client.EffectSprites;
import cn.yumis.fight.modules.presentation.api.client.EffectSprites.Blend;
import cn.yumis.fight.modules.presentation.api.client.EffectSprites.SpriteQuad;
import cn.yumis.fight.modules.presentation.api.client.LightningBolt;
import cn.yumis.fight.modules.presentation.api.client.RibbonTrail;
import cn.yumis.fight.modules.presentation.api.client.WorldQuads.Quad;
import cn.yumis.fight.modules.skills.api.*;
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
 * Dedicated DNF-inspired Witch / Trickster (魔道学者 / 魔术师) dual-layer VFX renderer.
 * Zero legacy untextured WorldQuads: 100% custom multi-tone pixel-sharp + soft-feathered
 * sprite sheets (source: tools/build_witch_effect_textures.py), dual-layer translucent silhouette
 * + additive luminous core rendering, Broomstick 150° wide candy-star basic sweeps, Elytra-style
 * broom gliding stardust trails & air-dash starbursts, Improved Magic Star bouncing bursts,
 * Shadow Cloak gathering shroud, Giant Flyswatter held-weapon slam + hopping 4-element mini-familiar
 * puppets, boiling Lava Potion pools & Great Success geysers, stormy Acid Rain clouds & thunderbolts,
 * Anti-Gravity sigil lift & slam, classic riding machines (Raging Flame Furnace / drivable Mega Drill /
 * Florae Collider / Awakening Fusion Craft with conditional 3rd-person camera), and Bitter Lollipop
 * comic detonation starbursts.
 * Pure client presentation: never grants combat authority, hitboxes, or physics changes.
 */
final class WitchVisuals {
    private static final Vec3 UP = new Vec3(0, 1, 0);

    static final List<String> ACTIVE_SKILLS = List.of(
        WitchSkills.SHADOW_CLOAK,
        WitchSkills.GIANT_FLYSWATTER,
        WitchSkills.LAVA_POTION,
        WitchSkills.ACID_RAIN,
        WitchSkills.ANTI_GRAVITY,
        WitchSkills.RAGING_FLAME_FURNACE,
        WitchSkills.MEGA_DRILL,
        WitchSkills.FLORAE_COLLIDER,
        WitchSkills.FUSION_CRAFT
    );

    // Custom pre-baked multi-tone Witch sprite sheets (source: tools/build_witch_effect_textures.py)
    static final Identifier TEX_BROOM_SWEEP = tex("broom_sweep.png");
    static final Identifier TEX_STARDUST_STAR = tex("stardust_star.png");
    static final Identifier TEX_SHADOW_CLOAK = tex("shadow_cloak.png");
    static final Identifier TEX_FLYSWATTER_SLAM = tex("flyswatter_slam.png");
    static final Identifier TEX_FAMILIAR_PUPPETS = tex("familiar_puppets.png");
    static final Identifier TEX_LAVA_POOL = tex("lava_pool.png");
    static final Identifier TEX_LAVA_GEYSER = tex("lava_geyser.png");
    static final Identifier TEX_ACID_CLOUD = tex("acid_cloud.png");
    static final Identifier TEX_ACID_RAIN_SHEET = tex("acid_rain_sheet.png");
    static final Identifier TEX_ANTI_GRAVITY_SIGIL = tex("anti_gravity_sigil.png");
    static final Identifier TEX_ANTI_GRAVITY_BEAM = tex("anti_gravity_beam.png");
    static final Identifier TEX_FURNACE_BODY = tex("furnace_body.png");
    static final Identifier TEX_MEGA_DRILL_BODY = tex("mega_drill_body.png");
    static final Identifier TEX_COLLIDER_BODY = tex("collider_body.png");
    static final Identifier TEX_FUSION_ROLLER = tex("fusion_roller.png");
    static final Identifier TEX_BITTER_EXPLOSION = tex("bitter_explosion.png");
    static final Identifier TEX_WITCH_SIGIL = tex("witch_sigil.png");

    // Classic DNF Witch candy-alchemy + 4-familiar palette
    private static final int COL_WHITE = 0xFFFFFF;
    private static final int COL_CANDY = WitchRules.COLOR_CANDY;
    private static final int COL_CANDY_HOT = 0xFFD8F6;
    private static final int COL_CANDY_DARK = 0x4A1868;
    private static final int COL_LIGHT = 0xFFE052;
    private static final int COL_LIGHT_HOT = 0xFFFBE0;
    private static final int COL_FIRE = 0xFF741E;
    private static final int COL_FIRE_HOT = 0xFFE27A;
    private static final int COL_ICE = 0x66D9FF;
    private static final int COL_ICE_HOT = 0xE6FAFF;
    private static final int COL_DARK = 0xA855FF;
    private static final int COL_DARK_HOT = 0xE8C8FF;
    private static final int COL_DARK_DEEP = 0x280B4A;

    private record TimedBurst(Vec3 pos, long startedAt, int element, double radius, int seed) {}
    private record DashBurst(Vec3 pos, Vec3 dir, long startedAt) {}

    private static final class Meta {
        int element = -1;
        int morphMode = 0;
        double radius = 4.5;
        double pullRadius = 5.0;
        double blastRadius = 6.2;
        double geyserRadius = 3.2;
        double cloudHeight = 4.5;
        long throwMs = 250L;
        long durationMs = 3000L;
        long liftMs = 350L;
        long slamMs = 850L;
        long windupMs = 0L;
        Vec3 target = null;
        boolean puppet;
        int puppetKind = 0;
        boolean improvedStar;
        boolean echo;
        boolean bitter;
        boolean gatherMode;
        boolean autonomous;
        boolean cinematic = true;
        int tickIndex = 0;
        int totalTicks = 14;
        long lastShatterAt = 0L;
        final List<TimedBurst> bursts = new ArrayList<>();
        long updatedAt;
    }

    private static final Meta DEFAULT_META = new Meta();
    private static final Map<UUID, Meta> METAS = new LinkedHashMap<>();
    private static final List<DashBurst> DASH_BURSTS = new ArrayList<>();

    private WitchVisuals() {}

    private static Identifier tex(String name) {
        return Identifier.fromNamespaceAndPath("yumis_dungeon_and_fight", "textures/effect/witch/" + name);
    }

    static void clear() {
        METAS.clear();
        DASH_BURSTS.clear();
    }

    static void onBroomDash(Player p) {
        if (p == null) return;
        Vec3 pos = p.position().add(0, 0.85, 0);
        Vec3 dir = p.getLookAngle().lengthSqr() > 1e-4 ? p.getLookAngle().normalize() : new Vec3(0, 0, 1);
        DASH_BURSTS.add(new DashBurst(pos, dir, ClientUi.now()));
        while (DASH_BURSTS.size() > 16) DASH_BURSTS.removeFirst();
        CameraShake.impulse(pos, 10, 0.14f, 40L, 90L);
    }

    static void install() {
        for (String id : ACTIVE_SKILLS) {
            boolean isFusion = WitchSkills.FUSION_CRAFT.equals(id);
            SkillVisualRegistry.register(id, new SkillVisualRegistry.Handler() {
                @Override public long defaultDuration() { return isFusion ? 4600L : 3200L; }

                @Override public long finishedUntil(SkillVfx.Track track, SkillVisualEvent event) {
                    Meta m = METAS.get(track.start.cast());
                    boolean bitter = m != null && m.bitter;
                    long base = computeFinishedUntil(id, event.at(), bitter);
                    return Math.min(base, event.at() + Math.max(220L, event.duration()));
                }

                @Override public int defaultColor() {
                    return elemColor(innateElement(id));
                }

                @Override public int handOrbColor() {
                    return elemColor(innateElement(id));
                }

                @Override public boolean cinematic() { return isFusion; }
                @Override public boolean cinematicFromFirstPerson() { return isFusion; }
                @Override public boolean restoreCamera() { return isFusion; }

                @Override public CameraType cameraType(SkillVfx.Track track, long time) {
                    return CameraType.THIRD_PERSON_BACK;
                }

                @Override public SkillVfx.CameraPose camera(SkillVfx.Track track, long time) {
                    if (!isFusion) return null;
                    Meta m = METAS.get(track.start.cast());
                    if (m != null && !m.cinematic) return null;
                    return fusionCraftCamera(track, time);
                }

                @Override public boolean motion(SkillVfx.Track track, JsonObject j) {
                    return recordMotion(id, track, j);
                }

                @Override public void play(SkillVfx.Track track, SkillVisualEvent event) {
                    playCue(id, track, event);
                }

                @Override public void render(SkillVfx.LevelRenderContext ctx, SkillVfx.Track track, long age, long time, List<Quad> out, Vec3 camera) {
                    renderSkill(id, track, age, time, ctx.sprites(), camera);
                }
            });
        }
    }

    // --- Package-private testable helpers (safe in headless JVM without FML/Minecraft init) ---

    static long computeFinishedUntil(String id, long eventAt) {
        return computeFinishedUntil(id, eventAt, false);
    }

    static long computeFinishedUntil(String id, long eventAt, boolean bitter) {
        if (bitter) {
            return WitchSkills.FUSION_CRAFT.equals(id) ? eventAt + 680L : eventAt + 560L;
        }
        return switch (id) {
            case WitchSkills.FUSION_CRAFT -> eventAt + 1050L;
            case WitchSkills.RAGING_FLAME_FURNACE, WitchSkills.MEGA_DRILL, WitchSkills.FLORAE_COLLIDER -> eventAt + 850L;
            case WitchSkills.ANTI_GRAVITY, WitchSkills.GIANT_FLYSWATTER -> eventAt + 720L;
            case WitchSkills.SHADOW_CLOAK -> eventAt + 220L;
            default -> eventAt + 520L;
        };
    }

    static boolean isCinematic(String id) {
        return WitchSkills.FUSION_CRAFT.equals(id);
    }

    static boolean isBroomBasic(SkillVisualEvent e) {
        return e != null && "ATTACK".equals(e.skill()) && "BROOM".equals(e.weaponType());
    }

    static boolean isImprovedStar(UUID castId) {
        Meta m = METAS.get(castId);
        return m != null && m.improvedStar;
    }

    static void recordImprovedStar(UUID castId, JsonObject j) {
        if (castId == null || j == null) return;
        if (j.has("improvedStar") && j.get("improvedStar").getAsBoolean()) {
            Meta m = METAS.computeIfAbsent(castId, k -> new Meta());
            m.improvedStar = true;
            if (j.has("element")) m.element = j.get("element").getAsInt();
            if (j.has("radius")) m.radius = j.get("radius").getAsDouble();
            if (j.has("echo")) m.echo = j.get("echo").getAsBoolean();
            trimMetas();
        }
    }

    static boolean recordMotion(String id, SkillVfx.Track track, JsonObject j) {
        if (track == null || j == null) return false;
        Meta m = METAS.computeIfAbsent(track.start.cast(), k -> new Meta());
        m.updatedAt = track.last != null ? track.last.at() : 0L;
        if (j.has("element")) m.element = j.get("element").getAsInt();
        if (j.has("morphMode")) m.morphMode = j.get("morphMode").getAsInt();
        if (j.has("radius")) m.radius = j.get("radius").getAsDouble();
        if (j.has("pullRadius")) m.pullRadius = j.get("pullRadius").getAsDouble();
        if (j.has("blastRadius")) m.blastRadius = j.get("blastRadius").getAsDouble();
        if (j.has("geyserRadius")) m.geyserRadius = j.get("geyserRadius").getAsDouble();
        if (j.has("cloudHeight")) m.cloudHeight = j.get("cloudHeight").getAsDouble();
        if (j.has("throwMs")) m.throwMs = Math.max(40L, j.get("throwMs").getAsLong());
        if (j.has("durationMs")) m.durationMs = Math.max(200L, j.get("durationMs").getAsLong());
        if (j.has("liftMs")) m.liftMs = Math.max(60L, j.get("liftMs").getAsLong());
        if (j.has("slamMs")) m.slamMs = Math.max(120L, j.get("slamMs").getAsLong());
        if (j.has("windupMs")) m.windupMs = Math.max(0L, j.get("windupMs").getAsLong());
        if (j.has("tx") && j.has("ty") && j.has("tz")) {
            m.target = new Vec3(j.get("tx").getAsDouble(), j.get("ty").getAsDouble(), j.get("tz").getAsDouble());
        }
        if (j.has("puppet")) m.puppet = j.get("puppet").getAsBoolean();
        if (j.has("puppetKind")) m.puppetKind = Math.clamp(j.get("puppetKind").getAsInt(), 0, 3);
        if (j.has("bitter")) m.bitter = j.get("bitter").getAsBoolean();
        if (j.has("gatherMode")) m.gatherMode = j.get("gatherMode").getAsBoolean();
        if (j.has("autonomous")) m.autonomous = j.get("autonomous").getAsBoolean();
        if (j.has("cinematic")) m.cinematic = j.get("cinematic").getAsBoolean();
        if (j.has("tickIndex")) m.tickIndex = j.get("tickIndex").getAsInt();
        if (j.has("totalTicks")) m.totalTicks = Math.max(1, j.get("totalTicks").getAsInt());

        // Capture discrete geyser / lightning / icebreaker-shatter / furnace-barrage sub-bursts so each plays a full crisp animation!
        if (track.last != null && track.last.phase() == HIT) {
            boolean isShatter = j.has("shatter") && j.get("shatter").getAsBoolean();
            if (isShatter) {
                m.lastShatterAt = track.last.at();
                var mc = Minecraft.getInstance();
                if (mc != null && mc.level != null && ClientUi.now() - track.last.at() < 200L) {
                    Vec3 bp = j.has("px") && j.has("py") && j.has("pz")
                        ? new Vec3(j.get("px").getAsDouble(), j.get("py").getAsDouble(), j.get("pz").getAsDouble())
                        : vector(track.last.position());
                    SkillSound.playAt(bp.x, bp.y, bp.z, SoundEvents.GLASS_BREAK, 0.95f, 0.82f);
                    SkillSound.playAt(bp.x, bp.y, bp.z, SoundEvents.GENERIC_EXPLODE.value(), 0.74f, 1.25f);
                    CameraShake.impulse(bp, 18, 0.30f, 70L, 145L);
                }
            }
            if ((j.has("geyser") && j.get("geyser").getAsBoolean())
                || (j.has("lightning") && j.get("lightning").getAsBoolean())
                || isShatter
                || WitchSkills.RAGING_FLAME_FURNACE.equals(id)
                || WitchSkills.FLORAE_COLLIDER.equals(id)
                || WitchSkills.FUSION_CRAFT.equals(id)) {
                Vec3 bp = j.has("px") && j.has("py") && j.has("pz")
                    ? new Vec3(j.get("px").getAsDouble(), j.get("py").getAsDouble(), j.get("pz").getAsDouble())
                    : vector(track.last.position());
                double br = j.has("burstRadius") ? j.get("burstRadius").getAsDouble()
                    : (j.has("geyserRadius") ? j.get("geyserRadius").getAsDouble()
                        : (j.has("radius") ? j.get("radius").getAsDouble() : m.radius));
                int be = j.has("element") ? j.get("element").getAsInt() : m.element;
                m.bursts.removeIf(b -> track.last.at() - b.startedAt() > 950L);
                if (m.bursts.size() >= 24) m.bursts.removeFirst();
                m.bursts.add(new TimedBurst(bp, track.last.at(), be, br, track.last.sequence()));
            }
        }
        trimMetas();
        return true;
    }

    private static void trimMetas() {
        while (METAS.size() > 512) {
            METAS.remove(METAS.keySet().iterator().next());
        }
    }

    // --- Persistent Actor Visuals: Broomstick Gliding Stardust + Lucky Candy Aura ---

    static void actors(List<SpriteQuad> sp, Vec3 camera, long serverNow, long renderNow) {
        var mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) return;

        // 1. Broom Air-Dash Stardust Starbursts
        DASH_BURSTS.removeIf(b -> renderNow - b.startedAt() >= 520L);
        for (var burst : DASH_BURSTS) {
            double u = Math.clamp((renderNow - burst.startedAt()) / 520.0, 0.0, 0.999);
            int frame = Math.clamp((int) (u * 8), 0, 7);
            double fade = 1.0 - u;
            emitFramedBillboard(sp, TEX_STARDUST_STAR, 8, frame, burst.pos(), camera, 1.45 + 1.25 * u, COL_WHITE, COL_CANDY_HOT, (int) (245 * fade), 0.92);
            EffectSprites.shockwave(sp, burst.pos(), 1.85 * EffectSprites.easeOutCubic(u), u * 1.4, COL_CANDY, (int) (215 * fade));
        }

        // 2. Gliding Witches (stardust broom ribbon + twinkling tail stars) & Lucky Candy Blessing aura
        Set<UUID> seen = new HashSet<>();
        for (var actor : CareerClient.INSTANCE.view.actors) {
            UUID id;
            try { id = UUID.fromString(actor.owner); } catch (IllegalArgumentException ignored) { continue; }
            Player p = mc.level.getPlayerByUUID(id);
            if (p == null || !p.isAlive()) continue;
            seen.add(id);
            boolean gliding = actor.witchBroomGliding || (mc.player != null && mc.player.getUUID().equals(id) && WitchClient.isBroomGliding());
            boolean candy = actor.witchCandyUntil > serverNow;
            if (gliding) renderBroomGlideTrail(sp, camera, p, renderNow);
            if (candy) renderCandyAura(sp, camera, p, renderNow);
        }
        // Local player fallback before first server actor echo
        if (mc.player != null && mc.player.isAlive() && !seen.contains(mc.player.getUUID())) {
            if (WitchClient.isBroomGliding()) renderBroomGlideTrail(sp, camera, mc.player, renderNow);
            if (CareerClient.INSTANCE.view.witch.candyBuffUntil > serverNow) renderCandyAura(sp, camera, mc.player, renderNow);
        }
    }

    private static void renderBroomGlideTrail(List<SpriteQuad> sp, Vec3 camera, Player p, long now) {
        Vec3 pos = PlayerVisualAnchor.position(p);
        Vec3 vel = p.getDeltaMovement();
        Vec3 fwd = vel.lengthSqr() > 0.01 ? vel.normalize() : horizontalDir(PlayerVisualAnchor.forward(p));
        Vec3 tail = pos.add(0, 0.42, 0).subtract(fwd.scale(0.85));
        boolean ownFp = isOwnFirstPerson(p.getUUID());
        RibbonTrail.push("witch_broom:" + p.getUUID(), tail, ownFp ? 0.14 : 0.24, COL_CANDY, ownFp ? 120 : 215, 290L, now);
        if (!ownFp) {
            int frame = (int) Math.floorMod(now / 70L, 8L);
            emitFramedBillboard(sp, TEX_STARDUST_STAR, 8, frame, tail, camera, 0.56, COL_WHITE, COL_LIGHT_HOT, 210, 0.85);
            EffectSprites.glow(sp, tail, camera, 0.92, COL_CANDY, 165);
        }
    }

    private static void renderCandyAura(List<SpriteQuad> sp, Vec3 camera, Player p, long now) {
        boolean ownFp = isOwnFirstPerson(p.getUUID());
        Vec3 base = PlayerVisualAnchor.position(p);
        double pulse = 0.5 + 0.5 * Math.sin(now * 0.006);
        int alpha = ownFp ? 70 : 145;
        emitGroundSprite(sp, TEX_WITCH_SIGIL, base.add(0, 0.04, 0), 1.25 + 0.08 * pulse, now * 0.0022, COL_CANDY, COL_CANDY_HOT, alpha, 0.72);
        if (!ownFp) {
            for (int i = 0; i < 2; i++) {
                double ang = now * 0.0042 + i * Math.PI;
                Vec3 spPos = base.add(Math.cos(ang) * 0.78, 0.95 + 0.22 * Math.sin(now * 0.006 + i * 2.0), Math.sin(ang) * 0.78);
                emitFramedBillboard(sp, TEX_STARDUST_STAR, 8, (int) Math.floorMod(now / 85L + i * 3L, 8L), spPos, camera, 0.26, COL_WHITE, COL_CANDY_HOT, 185, 0.80);
            }
        }
    }

    // --- Broom Basic Attack (150° Wide Pure-Magic Stardust Sweep) ------------

    static void playBroomBasicAudio(SkillVisualEvent e) {
        if (e.phase() != START) return;
        float pitch = 1.15f + (Math.abs(e.seed()) % 4) * 0.09f;
        SkillSound.playAt(e.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 0.62f, pitch);
        SkillSound.playAt(e.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 0.48f, pitch + 0.22f);
    }

    static void renderBroomBasic(List<SpriteQuad> sp, Vec3 camera, SkillVisualEvent e, long age, long baseDur) {
        var sample = BuiltinSkillVisuals.basicSlashSample(age, baseDur);
        if (sample == null) return;
        double progress = sample.progress();
        double alphaScale = sample.alphaScale();
        Vec3 p = vector(e.position());
        Vec3 dir = horizontalDir(vector(e.direction()));
        Vec3 right = rightOf(dir);
        SplittableRandom rng = new SplittableRandom(e.seed());
        boolean flip = rng.nextBoolean();
        double roll = (flip ? 1 : -1) * (8.0 + rng.nextDouble() * 14.0);
        double hScale = Math.max(0.95, e.horizontalScale());

        // Matches 4.5-block reach, 150° wide candy-star magic broom cleave!
        Vec3 center = p.add(dir.scale(1.15)).add(0, 1.10, 0);
        double radius = 3.35 * hScale;
        int alpha = Math.clamp((int) Math.round(245 * alphaScale), 0, 255);
        emitBroomSweep(sp, center, dir, roll, radius, progress, flip, COL_CANDY, alpha);
        emitBroomSweep(sp, center.add(0, 0.09, 0), dir, -roll * 0.5, radius * 0.90, Math.min(0.999, progress * 1.08), !flip, COL_LIGHT_HOT, (int) (alpha * 0.65));

        // 3 Twinkling stardust stars flung along the 150° outer rim!
        for (int i = -1; i <= 1; i++) {
            double ang = i * 0.85 + (flip ? -1 : 1) * (progress - 0.5) * 0.7;
            Vec3 rimDir = dir.scale(Math.cos(ang)).add(right.scale(Math.sin(ang)));
            Vec3 starPos = p.add(rimDir.scale(2.4 + 1.8 * progress)).add(0, 1.15 + 0.16 * i, 0);
            int sFrame = Math.clamp((int) (progress * 8 + Math.abs(i)), 0, 7);
            emitFramedBillboard(sp, TEX_STARDUST_STAR, 8, sFrame, starPos, camera, 0.42 * alphaScale, COL_WHITE, COL_LIGHT_HOT, (int) (220 * alphaScale), 0.90);
        }
    }

    // --- Improved Magic Star (改良魔法星弹) ---------------------------------

    static void playImprovedStar(SkillVisualEvent e) {
        if (e.phase() == START) {
            SkillSound.playAt(e.position(), SoundEvents.FIREWORK_ROCKET_LAUNCH, 0.52f, 1.55f);
            SkillSound.playAt(e.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 0.56f, 1.48f);
        } else if (e.phase() == HIT || e.phase() == FINISH) {
            SkillSound.playAt(e.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 0.65f, 1.52f);
            SkillSound.playAt(e.position(), SoundEvents.TRIDENT_THUNDER.value(), 0.34f, 1.65f);
            CameraShake.impulse(vector(e.position()), 12, 0.16f, 45L, 95L);
        }
    }

    static void renderImprovedStar(SkillVfx.Track t, long age, long time, List<SpriteQuad> sp, Vec3 camera) {
        Meta m = METAS.getOrDefault(t.start.cast(), DEFAULT_META);
        int elem = m.element >= 0 ? m.element : 0;
        int col = elemColor(elem);
        int hot = elemHot(elem);
        Vec3 pos = vector(t.last.position());
        boolean finished = t.last.phase() == FINISH;

        if (!finished) {
            int frame = (int) Math.floorMod(time / 55L, 8L);
            emitFramedBillboard(sp, TEX_STARDUST_STAR, 8, frame, pos, camera, 0.78, COL_WHITE, hot, 250, 0.92);
            EffectSprites.glow(sp, pos, camera, 1.25, col, 225);
            RibbonTrail.push("wstar:" + t.start.cast(), pos, 0.24, col, 225, 260L, time);
        }

        // Render every bounce impact & solo-echo blast recorded in flashes!
        for (var flash : t.flashes) {
            if (flash.phase() != HIT && flash.phase() != FINISH) continue;
            long fa = time - flash.at();
            if (fa < 0 || fa >= 420L) continue;
            double u = Math.clamp(fa / 420.0, 0.0, 0.999);
            double fade = 1.0 - u;
            int frame = Math.clamp((int) (u * 8), 0, 7);
            Vec3 fp = vector(flash.position());
            double r = Math.max(1.8, m.radius);
            emitFramedBillboard(sp, TEX_STARDUST_STAR, 8, frame, fp, camera, r * (0.65 + 0.55 * EffectSprites.easeOutCubic(u)), COL_WHITE, hot, (int) (245 * fade), 0.95);
            EffectSprites.shockwave(sp, fp, r * 1.05 * EffectSprites.easeOutCubic(u), u * 1.6, hot, (int) (235 * fade));
            EffectSprites.glow(sp, fp, camera, r * 0.95 * fade, col, (int) (215 * fade));
        }
    }

    // --- Layered Audio & Camera Shake Choreography --------------------------

    private static void playCue(String id, SkillVfx.Track track, SkillVisualEvent e) {
        Vec3 pos = vector(e.position());
        Meta m = METAS.getOrDefault(track.start.cast(), DEFAULT_META);

        switch (id) {
            case WitchSkills.SHADOW_CLOAK -> {
                if (e.phase() == START) {
                    SkillSound.playAt(e.position(), SoundEvents.END_PORTAL_FRAME_FILL, 0.60f, 0.92f);
                    SkillSound.playAt(e.position(), SoundEvents.BREEZE_SHOOT, 0.50f, 0.78f);
                } else if (e.phase() == HIT) {
                    SkillSound.playAt(e.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 0.52f, 0.76f);
                } else if (e.phase() == FINISH) {
                    SkillSound.playAt(e.position(), SoundEvents.BREEZE_SHOOT, 0.42f, 1.18f);
                }
            }
            case WitchSkills.GIANT_FLYSWATTER -> {
                if (m.puppet) {
                    if (e.phase() == START) {
                        SkillSound.playAt(e.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 0.52f, 1.55f);
                    } else if (e.phase() == FINISH) {
                        SkillSound.playAt(e.position(), SoundEvents.GENERIC_EXPLODE.value(), 0.56f, 1.42f);
                    }
                    return;
                }
                if (e.phase() == START) {
                    SkillSound.playAt(e.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 0.78f, 0.82f);
                } else if (e.phase() == FINISH) {
                    SkillSound.playAt(e.position(), SoundEvents.MACE_SMASH_GROUND, 0.88f, 1.12f);
                    SkillSound.playAt(e.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 0.65f, 1.38f);
                    CameraShake.impulse(pos, 16, 0.32f, 80L, 175L);
                }
            }
            case WitchSkills.LAVA_POTION -> {
                if (e.phase() == START) {
                    SkillSound.playAt(e.position(), SoundEvents.FIRECHARGE_USE, 0.66f, 1.25f);
                } else if (e.phase() == RELEASE) {
                    SkillSound.playAt(e.position(), SoundEvents.GLASS_BREAK, 0.88f, 0.96f);
                    SkillSound.playAt(e.position(), SoundEvents.BLAZE_SHOOT, 0.72f, 0.86f);
                    CameraShake.impulse(pos, 16, 0.22f, 60L, 130L);
                } else if (e.phase() == HIT) {
                    SkillSound.playAt(e.position(), SoundEvents.BLAZE_SHOOT, 0.82f, 0.78f);
                    SkillSound.playAt(e.position(), SoundEvents.GENERIC_EXPLODE.value(), 0.62f, 0.92f);
                    CameraShake.impulse(pos, 18, 0.28f, 70L, 155L);
                }
            }
            case WitchSkills.ACID_RAIN -> {
                if (e.phase() == START || e.phase() == RELEASE) {
                    SkillSound.playAt(e.position(), SoundEvents.PLAYER_HURT_FREEZE, 0.65f, 1.18f);
                    SkillSound.playAt(e.position(), SoundEvents.BREEZE_SHOOT, 0.62f, 0.92f);
                } else if (e.phase() == HIT) {
                    SkillSound.playAt(e.position(), SoundEvents.LIGHTNING_BOLT_IMPACT, 0.72f, 1.32f);
                    SkillSound.playAt(e.position(), SoundEvents.TRIDENT_THUNDER.value(), 0.48f, 1.38f);
                    CameraShake.impulse(pos, 18, 0.26f, 65L, 145L);
                }
            }
            case WitchSkills.ANTI_GRAVITY -> {
                if (e.phase() == START) {
                    SkillSound.playAt(e.position(), SoundEvents.BEACON_ACTIVATE, 0.72f, 0.84f);
                } else if (e.phase() == RELEASE) {
                    SkillSound.playAt(e.position(), SoundEvents.END_PORTAL_FRAME_FILL, 0.82f, 1.12f);
                    CameraShake.impulse(pos, 18, 0.24f, 65L, 140L);
                } else if (e.phase() == FINISH) {
                    SkillSound.playAt(e.position(), SoundEvents.MACE_SMASH_GROUND_HEAVY, 0.96f, 0.74f);
                    SkillSound.playAt(e.position(), SoundEvents.GENERIC_EXPLODE.value(), 0.84f, 0.82f);
                    CameraShake.impulse(pos, 24, 0.46f, 110L, 240L);
                }
            }
            case WitchSkills.RAGING_FLAME_FURNACE -> {
                if (e.phase() == START) {
                    SkillSound.playAt(e.position(), SoundEvents.FIRECHARGE_USE, 0.82f, 0.78f);
                    SkillSound.playAt(e.position(), SoundEvents.BEACON_ACTIVATE, 0.64f, 0.90f);
                } else if (e.phase() == HIT) {
                    if ((e.sequence() & 1) == 0) {
                        SkillSound.playAt(e.position(), SoundEvents.BLAZE_SHOOT, 0.68f, 0.88f + (e.sequence() % 3) * 0.12f);
                        CameraShake.impulse(pos, 16, 0.16f, 45L, 100L);
                    }
                } else if (e.phase() == FINISH) {
                    playMachineFinishAudio(e, m.bitter, 0.78f);
                }
            }
            case WitchSkills.MEGA_DRILL -> {
                if (e.phase() == START) {
                    SkillSound.playAt(e.position(), SoundEvents.GLASS_PLACE, 0.85f, 0.88f);
                    SkillSound.playAt(e.position(), SoundEvents.PLAYER_HURT_FREEZE, 0.78f, 0.92f);
                } else if (e.phase() == HIT) {
                    if ((e.sequence() & 1) == 0) {
                        SkillSound.playAt(e.position(), SoundEvents.GLASS_BREAK, 0.56f, 1.18f + (e.sequence() % 3) * 0.10f);
                        CameraShake.impulse(pos, 16, 0.18f, 45L, 95L);
                    }
                } else if (e.phase() == FINISH) {
                    playMachineFinishAudio(e, m.bitter, 0.95f);
                }
            }
            case WitchSkills.FLORAE_COLLIDER -> {
                if (e.phase() == START) {
                    SkillSound.playAt(e.position(), SoundEvents.BEACON_ACTIVATE, 0.82f, 1.32f);
                    SkillSound.playAt(e.position(), SoundEvents.TRIDENT_THUNDER.value(), 0.60f, 1.36f);
                } else if (e.phase() == HIT) {
                    if ((e.sequence() & 1) == 0) {
                        SkillSound.playAt(e.position(), SoundEvents.LIGHTNING_BOLT_IMPACT, 0.54f, 1.48f);
                        CameraShake.impulse(pos, 18, 0.16f, 45L, 95L);
                    }
                } else if (e.phase() == FINISH) {
                    playMachineFinishAudio(e, m.bitter, 1.24f);
                }
            }
            case WitchSkills.FUSION_CRAFT -> {
                if (e.phase() == START) {
                    SkillSound.playAt(e.position(), SoundEvents.BEACON_ACTIVATE, 0.95f, 1.05f);
                    SkillSound.playAt(e.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 0.88f, 1.18f);
                    CameraShake.impulse(pos, 24, 0.34f, 100L, 210L);
                } else if (e.phase() == HIT) {
                    if ((e.sequence() & 1) == 0) {
                        SkillSound.playAt(e.position(), SoundEvents.MACE_SMASH_GROUND, 0.62f, 0.85f + (e.sequence() % 4) * 0.12f);
                        CameraShake.impulse(pos, 22, 0.22f, 55L, 120L);
                    }
                } else if (e.phase() == FINISH) {
                    SkillSound.playAt(e.position(), SoundEvents.GENERIC_EXPLODE.value(), 1.0f, 0.68f);
                    SkillSound.playAt(e.position(), SoundEvents.MACE_SMASH_GROUND_HEAVY, 0.96f, 0.72f);
                    SkillSound.playAt(e.position(), SoundEvents.TRIDENT_THUNDER.value(), 0.78f, 1.08f);
                    CameraShake.impulse(pos, 32, 0.60f, 160L, 320L);
                }
            }
        }
    }

    private static void playMachineFinishAudio(SkillVisualEvent e, boolean bitter, float pitch) {
        Vec3 pos = vector(e.position());
        SkillSound.playAt(e.position(), SoundEvents.GENERIC_EXPLODE.value(), bitter ? 0.98f : 0.86f, pitch);
        if (bitter) {
            SkillSound.playAt(e.position(), SoundEvents.MACE_SMASH_GROUND_HEAVY, 0.86f, 1.05f);
            SkillSound.playAt(e.position(), SoundEvents.FIREWORK_ROCKET_LAUNCH, 0.72f, 1.25f);
            CameraShake.impulse(pos, 26, 0.48f, 120L, 245L);
        } else {
            CameraShake.impulse(pos, 22, 0.36f, 95L, 195L);
        }
    }

    // --- Main Skill Rendering Router ----------------------------------------

    static void renderSkill(String id, SkillVfx.Track t, long age, long time, List<SpriteQuad> sp, Vec3 camera) {
        if (t == null || sp == null || camera == null) return;
        Meta m = METAS.getOrDefault(t.start.cast(), DEFAULT_META);
        switch (id) {
            case WitchSkills.SHADOW_CLOAK -> renderShadowCloak(t, m, age, time, sp, camera);
            case WitchSkills.GIANT_FLYSWATTER -> {
                if (m.puppet) renderFamiliarPuppet(t, m, age, time, sp, camera);
                else renderGiantFlyswatter(t, m, age, time, sp, camera);
            }
            case WitchSkills.LAVA_POTION -> renderLavaPotion(t, m, age, time, sp, camera);
            case WitchSkills.ACID_RAIN -> renderAcidRain(t, m, age, time, sp, camera);
            case WitchSkills.ANTI_GRAVITY -> renderAntiGravity(t, m, age, time, sp, camera);
            case WitchSkills.RAGING_FLAME_FURNACE -> renderFurnace(t, m, age, time, sp, camera);
            case WitchSkills.MEGA_DRILL -> renderMegaDrill(t, m, age, time, sp, camera);
            case WitchSkills.FLORAE_COLLIDER -> renderFloraeCollider(t, m, age, time, sp, camera);
            case WitchSkills.FUSION_CRAFT -> renderFusionCraft(t, m, age, time, sp, camera);
            default -> {}
        }
    }

    // --- 1. Lv20 Shadow Cloak (暗影斗篷: 地面向中心聚拢后消散，无爆炸/无震屏) ---

    private static void renderShadowCloak(SkillVfx.Track t, Meta m, long age, long now, List<SpriteQuad> sp, Vec3 camera) {
        int elem = m.element >= 0 ? m.element : 3;
        int col = elemColor(elem);
        int hot = elemHot(elem);
        Vec3 center = vector(t.last.position());
        double outerR = Math.max(4.5, Math.max(m.radius, m.pullRadius));
        boolean finished = t.last.phase() == FINISH;

        if (!finished) {
            double u = Math.clamp(age / 460.0, 0.0, 0.999);
            double ease = EffectSprites.easeOutCubic(u);
            int frame = Math.clamp((int) (u * 4), 0, 3);
            double pullR = outerR * (1.0 - 0.72 * ease);
            int alpha = (int) Math.round(235 * (0.85 + 0.15 * (1.0 - u)));
            // Pure ground-plane inward pull toward center
            emitGroundFrame(sp, TEX_SHADOW_CLOAK, 8, frame, center.add(0, 0.10, 0), pullR, now * 0.0055, COL_WHITE, col, alpha, 0.78);
            emitGroundSprite(sp, TEX_ANTI_GRAVITY_SIGIL, center.add(0, 0.05, 0), pullR * 0.95, -now * 0.0045, COL_DARK_DEEP, col, (int) (205 * (1.0 - 0.25 * u)), 0.72);
            EffectSprites.shockwave(sp, center.add(0, 0.12, 0), pullR * 1.04, -u * 2.2, hot, (int) (210 * (1.0 - 0.2 * u)));
        } else {
            // Snappy inward collapse and clean fade-out (no explosion, no billboard blast!)
            long fa = Math.max(0L, now - t.last.at());
            double u = Math.clamp(fa / 220.0, 0.0, 1.0);
            double fade = 1.0 - u;
            if (fade <= 0.02) return;
            double collapseR = Math.max(0.35, outerR * 0.30 * fade);
            emitGroundFrame(sp, TEX_SHADOW_CLOAK, 8, 3, center.add(0, 0.10, 0), collapseR, -u * 1.6, COL_WHITE, col, (int) (200 * fade), 0.70);
            emitGroundSprite(sp, TEX_ANTI_GRAVITY_SIGIL, center.add(0, 0.05, 0), collapseR * 0.9, u * 1.8, COL_DARK_DEEP, hot, (int) (175 * fade), 0.65);
        }
    }

    // --- 2. Lv20 Giant Flyswatter (变异苍蝇拍) & Hopping 4-Element Puppets ---

    private static void renderGiantFlyswatter(SkillVfx.Track t, Meta m, long age, long now, List<SpriteQuad> sp, Vec3 camera) {
        Vec3 center = vector(t.last.position());
        double r = Math.max(3.2, m.radius);
        boolean finished = t.last.phase() == FINISH;

        if (!finished) {
            double u = Math.clamp(age / 240.0, 0.0, 1.0);
            emitGroundSprite(sp, TEX_WITCH_SIGIL, center.add(0, 0.06, 0), r * (0.55 + 0.45 * u), now * 0.006, COL_CANDY, COL_CANDY_HOT, (int) (195 * u), 0.82);
        } else {
            long fa = Math.max(0L, now - t.last.at());
            double u = Math.clamp(fa / 720.0, 0.0, 0.999);
            double fade = 1.0 - u;
            int frame = Math.clamp((int) (u * 8), 0, 7);
            double slamR = r * (0.85 + 0.30 * EffectSprites.easeOutCubic(u));
            emitGroundFrame(sp, TEX_FLYSWATTER_SLAM, 8, frame, center.add(0, 0.12, 0), slamR, 0.0, COL_WHITE, COL_CANDY_HOT, (int) (255 * fade), 0.92);
            emitFramedBillboard(sp, TEX_FLYSWATTER_SLAM, 8, frame, center.add(0, 1.15, 0), camera, slamR * 0.72, COL_WHITE, COL_CANDY_HOT, (int) (235 * fade), 0.88);
            EffectSprites.shockwave(sp, center.add(0, 0.10, 0), slamR * 1.12, u * 1.5, COL_CANDY_HOT, (int) (240 * fade));
            EffectSprites.glow(sp, center.add(0, 1.0, 0), camera, slamR * 0.85 * fade, COL_CANDY, (int) (215 * fade));
        }
    }

    private static void renderFamiliarPuppet(SkillVfx.Track t, Meta m, long age, long now, List<SpriteQuad> sp, Vec3 camera) {
        int kind = Math.clamp(m.puppetKind, 0, 3);
        int col = elemColor(kind);
        int hot = elemHot(kind);
        boolean finished = t.last.phase() == FINISH;

        if (!finished) {
            Vec3 live = WitchClient.puppetPosition(t.start.cast());
            Vec3 pos = live != null ? live : vector(t.last.position()).add(0, Math.abs(Math.sin(now * 0.011 + t.start.seed())) * 0.55, 0);
            double bob = 0.92 + 0.12 * Math.sin(now * 0.018);
            emitFramedBillboard(sp, TEX_FAMILIAR_PUPPETS, 4, kind, pos.add(0, 0.45, 0), camera, 0.62 * bob, COL_WHITE, hot, 255, 0.55);
            EffectSprites.glow(sp, pos.add(0, 0.45, 0), camera, 0.92, col, 175);
            RibbonTrail.push("wpup:" + t.start.cast(), pos.add(0, 0.40, 0), 0.18, col, 205, 220L, now);
        } else {
            long fa = Math.max(0L, now - t.last.at());
            double u = Math.clamp(fa / 480.0, 0.0, 0.999);
            double fade = 1.0 - u;
            int frame = Math.clamp((int) (u * 8), 0, 7);
            Vec3 at = vector(t.last.position());
            double r = Math.max(1.8, m.radius);
            emitFramedBillboard(sp, TEX_BITTER_EXPLOSION, 8, frame, at.add(0, 0.75, 0), camera, r * (0.65 + 0.50 * EffectSprites.easeOutCubic(u)), col, hot, (int) (248 * fade), 0.92);
            EffectSprites.shockwave(sp, at.add(0, 0.10, 0), r * 1.06 * EffectSprites.easeOutCubic(u), u * 1.8, hot, (int) (230 * fade));
            EffectSprites.glow(sp, at.add(0, 0.75, 0), camera, r * fade, hot, (int) (215 * fade));
        }
    }

    // --- 3. Lv25 Lava Potion (熔岩药瓶) -------------------------------------

    private static void renderLavaPotion(SkillVfx.Track t, Meta m, long age, long now, List<SpriteQuad> sp, Vec3 camera) {
        int elem = m.element >= 0 ? m.element : 1;
        int col = elemColor(elem);
        int hot = elemHot(elem);
        Vec3 origin = vector(t.start.origin());
        Vec3 target = m.target != null ? m.target : vector(t.last.position());
        double r = Math.max(3.8, m.radius);
        boolean landed = t.last.phase() != START || age >= m.throwMs;
        boolean finished = t.last.phase() == FINISH;

        if (!landed) {
            // Arcing glowing flask from caster to target!
            double u = Math.clamp(age / (double) Math.max(1L, m.throwMs), 0.0, 1.0);
            Vec3 pos = origin.lerp(target, u).add(0, 4.0 * 2.4 * u * (1.0 - u), 0);
            emitFramedBillboard(sp, TEX_STARDUST_STAR, 8, (int) Math.floorMod(now / 50L, 8L), pos, camera, 0.65, COL_FIRE, COL_FIRE_HOT, 245, 0.90);
            RibbonTrail.push("wlava:" + t.start.cast(), pos, 0.22, col, 225, 220L, now);
            emitGroundSprite(sp, TEX_WITCH_SIGIL, target.add(0, 0.05, 0), r * (0.4 + 0.6 * u), now * 0.005, col, hot, 175, 0.75);
            return;
        }

        double fade = finished ? Math.clamp(1.0 - (now - t.last.at()) / 500.0, 0.0, 1.0) : 1.0;
        double pulse = 0.96 + 0.05 * Math.sin(now * 0.007);

        // Boiling molten magma pool matching full +60% radius (7.68 blocks!)
        emitGroundSprite(sp, TEX_LAVA_POOL, target.add(0, 0.06, 0), r * 1.05 * pulse, now * 0.0007, COL_WHITE, hot, (int) (242 * fade), 0.68);
        emitGroundSprite(sp, TEX_WITCH_SIGIL, target.add(0, 0.09, 0), r * 0.90, -now * 0.0014, col, hot, (int) (145 * fade), 0.75);

        // Ambient simmering perimeter magma flares distributed across the expanded 7.68-block pool
        if (!finished) {
            for (int i = 0; i < 5; i++) {
                double ang = now * 0.0016 + i * (Math.TAU / 5.0);
                double rad = r * (0.36 + 0.34 * Math.sin(now * 0.003 + i * 2.1));
                Vec3 bp = target.add(Math.cos(ang) * rad, 0.08, Math.sin(ang) * rad);
                int f = (int) Math.floorMod(now / 95L + i * 3L, 8L);
                emitVerticalCrossed(sp, TEX_LAVA_GEYSER, 8, f, bp, 0.88, 2.25, ang, 2, COL_WHITE, hot, 165, 0.75);
            }
        }

        // 3 Great Success High-Pressure Lava Geyser Eruptions (5.12-block radius!)
        for (var g : m.bursts) {
            long ga = now - g.startedAt();
            if (ga < 0 || ga >= 780L) continue;
            double gu = Math.clamp(ga / 780.0, 0.0, 0.999);
            double gFade = 1.0 - gu;
            int gFrame = Math.clamp((int) (gu * 8), 0, 7);
            double gr = Math.max(3.2, Math.max(g.radius(), m.geyserRadius));
            emitVerticalCrossed(sp, TEX_LAVA_GEYSER, 8, gFrame, g.pos().add(0, 0.05, 0), gr * 0.74, 7.2 * (0.45 + 0.65 * EffectSprites.easeOutCubic(gu)), g.seed() * 0.8, 3, COL_WHITE, hot, (int) (252 * gFade), 0.92);
            EffectSprites.shockwave(sp, g.pos().add(0, 0.12, 0), gr * 1.12 * EffectSprites.easeOutCubic(gu), gu * 1.5, hot, (int) (240 * gFade));
            EffectSprites.glow(sp, g.pos().add(0, 2.6, 0), camera, gr * 1.1 * gFade, hot, (int) (220 * gFade));
        }
    }

    // --- 4. Lv25 Acid Rain (魔道酸雨云) -------------------------------------

    private static void renderAcidRain(SkillVfx.Track t, Meta m, long age, long now, List<SpriteQuad> sp, Vec3 camera) {
        Vec3 center = vector(t.start.origin());
        double r = Math.max(5.2, m.radius);
        double cloudH = Math.max(4.5, m.cloudHeight);
        boolean finished = t.last.phase() == FINISH;
        double fade = finished ? Math.clamp(1.0 - (now - t.last.at()) / 500.0, 0.0, 1.0)
            : Math.clamp(age / 240.0, 0.25, 1.0);
        int cloudFrame = (int) Math.floorMod(now / 150L, 4L);
        int rainFrame = (int) Math.floorMod(now / 75L, 4L);

        // Ground chilled acid puddle sigil showing exact +60% coverage (8.32-block radius!)
        emitGroundSprite(sp, TEX_WITCH_SIGIL, center.add(0, 0.05, 0), r * 1.02, now * 0.0015, COL_ICE, COL_ICE_HOT, (int) (185 * fade), 0.72);

        // 7-Puff Expansive Dark-Blue Storm Cloud Canopy overhead at y + cloudHeight!
        Vec3 cloudCenter = center.add(0, cloudH, 0);
        emitFramedBillboard(sp, TEX_ACID_CLOUD, 4, cloudFrame, cloudCenter, camera, r * 0.62, COL_WHITE, COL_ICE_HOT, (int) (240 * fade), 0.42);
        for (int i = 0; i < 6; i++) {
            double ang = i * (Math.TAU / 6.0) + now * 0.0005;
            Vec3 cp = cloudCenter.add(Math.cos(ang) * r * 0.48, 0.26 * Math.sin(now * 0.003 + i), Math.sin(ang) * r * 0.48);
            emitFramedBillboard(sp, TEX_ACID_CLOUD, 4, (cloudFrame + i) & 3, cp, camera, r * 0.46, COL_WHITE, COL_ICE, (int) (225 * fade), 0.36);
        }

        // 4 Crossed scrolling translucent acid-rain curtains pouring across full 8.32-block radius!
        emitVerticalCrossed(sp, TEX_ACID_RAIN_SHEET, 4, rainFrame, center.add(0, 0.04, 0), r * 0.98, cloudH, now * 0.0004, 4, COL_WHITE, COL_ICE_HOT, (int) (215 * fade), 0.65);

        // 4 Great Success Cloud-to-Ground Light Thunderbolts (5.12-block radius!)
        for (var b : m.bursts) {
            long ba = now - b.startedAt();
            if (ba < 0 || ba >= 520L) continue;
            double bu = Math.clamp(ba / 520.0, 0.0, 0.999);
            double bFade = 1.0 - bu;
            Vec3 strike = b.pos();
            Vec3 top = new Vec3(strike.x, center.y + cloudH, strike.z);
            double br = Math.max(3.2, b.radius());
            LightningBolt.bolt(sp, top, strike.add(0, 0.1, 0), camera, b.seed() ^ (ba / 45L), 7, 0.68, 0.16 * bFade + 0.04, COL_LIGHT_HOT, (int) (252 * bFade), true);
            EffectSprites.shockwave(sp, strike.add(0, 0.12, 0), br * 1.12 * EffectSprites.easeOutCubic(bu), bu * 1.6, COL_LIGHT_HOT, (int) (245 * bFade));
            EffectSprites.glow(sp, strike.add(0, 0.9, 0), camera, br * 0.95 * bFade, COL_LIGHT, (int) (235 * bFade));
        }
    }

    // --- 5. Lv30 Anti-Gravity (反重力装置) ----------------------------------

    private static void renderAntiGravity(SkillVfx.Track t, Meta m, long age, long now, List<SpriteQuad> sp, Vec3 camera) {
        int elem = m.element >= 0 ? m.element : 3;
        int col = elemColor(elem);
        int hot = elemHot(elem);
        Vec3 center = vector(t.start.origin());
        double r = Math.max(4.5, m.radius);
        double blastR = m.gatherMode ? Math.max(3.5, m.blastRadius) : Math.max(r, m.blastRadius);
        boolean lifted = t.last.phase() == RELEASE || age >= m.liftMs;
        boolean finished = t.last.phase() == FINISH;

        if (!finished) {
            double open = Math.clamp(age / (double) Math.max(1L, m.liftMs), 0.0, 1.0);
            emitGroundSprite(sp, TEX_ANTI_GRAVITY_SIGIL, center.add(0, 0.06, 0), r * (0.45 + 0.60 * EffectSprites.easeOutCubic(open)), now * 0.0045, COL_WHITE, col, 245, 0.88);
            if (m.gatherMode) {
                // Large-range inward gravity vortex pulling enemies toward center detonation zone!
                emitGroundSprite(sp, TEX_WITCH_SIGIL, center.add(0, 0.09, 0), blastR * 0.98, -now * 0.004, COL_DARK_DEEP, hot, 195, 0.80);
                for (int k = 0; k < 2; k++) {
                    double pullPhase = 1.0 - (((now + k * 210L) % 420L) / 420.0);
                    EffectSprites.shockwave(sp, center.add(0, 0.16 + k * 0.35, 0), r * (0.20 + 0.82 * pullPhase), -now * 0.007 + k, hot, (int) (235 * pullPhase));
                }
                if (lifted) {
                    EffectSprites.glow(sp, center.add(0, 1.1, 0), camera, blastR * 0.72, col, 210);
                }
            } else if (lifted) {
                double liftU = Math.clamp((age - m.liftMs) / (double) Math.max(120L, m.slamMs - m.liftMs), 0.0, 0.999);
                int frame = Math.clamp((int) (liftU * 5), 0, 4);
                emitVerticalCrossed(sp, TEX_ANTI_GRAVITY_BEAM, 8, frame, center.add(0, 0.06, 0), r * 0.86, 6.2, now * 0.003, 3, COL_WHITE, hot, 240, 0.88);
                // Rising concentric gravity rings
                for (int k = 0; k < 2; k++) {
                    double ry = ((now * 0.008 + k * 2.8) % 5.5) + 0.3;
                    EffectSprites.shockwave(sp, center.add(0, ry, 0), r * 0.92, -now * 0.005 + k, hot, 210);
                }
            } else {
                EffectSprites.shockwave(sp, center.add(0, 0.12, 0), r * (1.0 - 0.6 * open), -now * 0.006, hot, 215);
            }
        } else {
            long fa = Math.max(0L, now - t.last.at());
            double u = Math.clamp(fa / 720.0, 0.0, 0.999);
            double fade = 1.0 - u;
            int frame = Math.clamp(4 + (int) (u * 4), 4, 7);
            emitGroundSprite(sp, TEX_ANTI_GRAVITY_SIGIL, center.add(0, 0.08, 0), blastR * 1.08, -u * 1.2, COL_WHITE, hot, (int) (252 * fade), 0.95);
            emitVerticalCrossed(sp, TEX_ANTI_GRAVITY_BEAM, 8, frame, center.add(0, 0.06, 0), blastR * 0.82, 7.2 * fade + 1.2, now * 0.004, 3, COL_WHITE, hot, (int) (245 * fade), 0.92);
            emitFramedBillboard(sp, TEX_BITTER_EXPLOSION, 8, Math.clamp((int) (u * 8), 0, 7), center.add(0, 1.5, 0), camera, blastR * 0.78, col, hot, (int) (242 * fade), 0.90);
            EffectSprites.shockwave(sp, center.add(0, 0.15, 0), blastR * 1.15 * EffectSprites.easeOutCubic(u), u * 2.0, hot, (int) (250 * fade));
            EffectSprites.shockwave(sp, center.add(0, 0.65, 0), blastR * 0.85 * EffectSprites.easeOutCubic(u), -u * 1.6, col, (int) (220 * fade));
        }
    }

    // --- 6. Lv35 Raging Flame Furnace (爆炎加热炉 / 红炉子) -------------------

    private static void renderFurnace(SkillVfx.Track t, Meta m, long age, long now, List<SpriteQuad> sp, Vec3 camera) {
        int elem = m.element >= 0 ? m.element : 1;
        int col = elemColor(elem);
        int hot = elemHot(elem);
        Vec3 base = machineAnchor(t, m);
        double r = Math.max(5.5, m.radius);
        double blastR = Math.max(5.5, m.blastRadius);
        boolean finished = t.last.phase() == FINISH;

        if (!finished) {
            int frame = (int) Math.floorMod(now / 110L, 4L);
            // Boiling molten arena circle showing 7.5-block barrage coverage!
            emitGroundSprite(sp, TEX_LAVA_POOL, base.add(0, 0.06, 0), r * 1.02, now * 0.0012, COL_WHITE, hot, 205, 0.65);
            emitGroundSprite(sp, TEX_WITCH_SIGIL, base.add(0, 0.09, 0), r * 0.72, -now * 0.0028, col, hot, 185, 0.82);

            // Iconic Red Cast-Iron Furnace body rendered as crossed 3D upright planes right beneath rider!
            emitVerticalCrossed(sp, TEX_FURNACE_BODY, 4, frame, base.add(0, 0.04, 0), 1.65, 3.25, Math.atan2(vector(t.start.direction()).z, vector(t.start.direction()).x), 2, COL_WHITE, hot, 252, 0.45);
            EffectSprites.glow(sp, base.add(0, 2.75, 0), camera, 2.2, hot, 210);
            if (m.autonomous) {
                // Orbiting Jack Familiar operating the autonomous furnace!
                double ang = now * 0.0052;
                Vec3 famPos = base.add(Math.cos(ang) * 1.85, 2.25 + 0.35 * Math.sin(now * 0.011), Math.sin(ang) * 1.85);
                emitFramedBillboard(sp, TEX_FAMILIAR_PUPPETS, 4, 1, famPos, camera, 0.72, COL_WHITE, COL_FIRE_HOT, 255, 0.65);
            }

            // Great Success cascading lava fireballs raining across the 7.5-block perimeter!
            for (var b : m.bursts) {
                long ba = now - b.startedAt();
                if (ba < 0 || ba >= 580L) continue;
                double bu = Math.clamp(ba / 580.0, 0.0, 0.999);
                for (int k = 0; k < 3; k++) {
                    double ang = b.seed() * 1.37 + k * (Math.TAU / 3.0);
                    double dist = r * (0.42 + 0.52 * ((b.seed() + k * 7) % 5) / 4.0);
                    Vec3 land = base.add(Math.cos(ang) * dist, 0.12, Math.sin(ang) * dist);
                    if (bu < 0.55) {
                        double fu = bu / 0.55;
                        Vec3 arcPos = base.add(0, 3.0, 0).lerp(land, fu).add(0, 3.8 * 4.0 * fu * (1.0 - fu) * 0.35, 0);
                        emitFramedBillboard(sp, TEX_STARDUST_STAR, 8, (int) (fu * 7), arcPos, camera, 0.75, COL_FIRE, COL_FIRE_HOT, 245, 0.90);
                    } else {
                        double eu = (bu - 0.55) / 0.45;
                        int gf = Math.clamp((int) (eu * 8), 0, 7);
                        emitVerticalCrossed(sp, TEX_LAVA_GEYSER, 8, gf, land, 1.15, 3.2, ang, 2, COL_WHITE, hot, (int) (235 * (1.0 - eu)), 0.88);
                    }
                }
            }
        } else {
            renderMachineFinisher(t, m, now, sp, camera, blastR, col, hot, true);
        }
    }

    // --- 7. Lv40 Mega Drill (冰霜钻孔车 / 冰车) -----------------------------

    private static void renderMegaDrill(SkillVfx.Track t, Meta m, long age, long now, List<SpriteQuad> sp, Vec3 camera) {
        int elem = m.element >= 0 ? m.element : 2;
        int col = elemColor(elem);
        int hot = elemHot(elem);
        Vec3 base = machineAnchor(t, m);
        Vec3 dir = machineDir(t, m);
        double r = Math.max(4.2, m.radius);
        double blastR = Math.max(5.5, m.blastRadius);
        boolean finished = t.last.phase() == FINISH;

        if (!finished) {
            int frame = (int) Math.floorMod(now / 65L, 4L);
            Vec3 nose = base.add(dir.scale(1.8));
            // Swirling arctic suction vortex pulling mobs toward the spinning drill nose!
            emitGroundSprite(sp, TEX_WITCH_SIGIL, nose.add(0, 0.07, 0), r * 1.05, now * 0.0085, col, hot, 225, 0.86);
            double pullPhase = 1.0 - ((now % 450L) / 450.0);
            EffectSprites.shockwave(sp, nose.add(0, 0.14, 0), (r + 1.8) * (0.28 + 0.75 * pullPhase), -now * 0.007, hot, (int) (220 * pullPhase));

            // Spinning Frost Auger Drill Car body
            emitVerticalCrossed(sp, TEX_MEGA_DRILL_BODY, 4, frame, base.add(dir.scale(0.85)).add(0, 0.05, 0), 1.85, 3.2, Math.atan2(dir.z, dir.x), 2, COL_WHITE, hot, 255, 0.55);
            // Icy blizzard sparks spraying off the drill blades
            EffectSprites.glow(sp, nose.add(0, 1.3, 0), camera, 2.4, hot, 215);
            RibbonTrail.push("wdrill:" + t.start.cast(), base.add(0, 0.55, 0), 0.72, col, 190, 320L, now);
        } else {
            renderMachineFinisher(t, m, now, sp, camera, blastR, col, hot, false);
        }

        // Icebreaker shatter bursts on frozen enemies!
        for (var b : m.bursts) {
            long ba = now - b.startedAt();
            if (ba < 0 || ba >= 580L) continue;
            double bu = Math.clamp(ba / 580.0, 0.0, 0.999);
            double bFade = 1.0 - bu;
            int bFrame = Math.clamp((int) (bu * 8), 0, 7);
            double br = Math.max(2.6, b.radius());
            emitFramedBillboard(sp, TEX_BITTER_EXPLOSION, 8, bFrame, b.pos(), camera, br * (0.75 + 0.55 * EffectSprites.easeOutCubic(bu)), COL_ICE, COL_ICE_HOT, (int) (255 * bFade), 0.96);
            EffectSprites.shockwave(sp, b.pos().add(0, -0.5, 0), br * 1.15 * EffectSprites.easeOutCubic(bu), bu * 2.2, COL_ICE_HOT, (int) (250 * bFade));
            EffectSprites.glow(sp, b.pos(), camera, br * 1.05 * bFade, COL_ICE_HOT, (int) (235 * bFade));
        }
    }

    // --- 8. Lv45 Florae Collider (电鳗碰撞机 / 电塔) -------------------------

    private static void renderFloraeCollider(SkillVfx.Track t, Meta m, long age, long now, List<SpriteQuad> sp, Vec3 camera) {
        int elem = m.element >= 0 ? m.element : 0;
        int col = elemColor(elem);
        int hot = elemHot(elem);
        Vec3 base = machineAnchor(t, m);
        double r = Math.max(6.5, m.radius);
        double blastR = Math.max(6.5, m.blastRadius);
        boolean finished = t.last.phase() == FINISH;

        if (!finished) {
            int frame = (int) Math.floorMod(now / 85L, 4L);
            // Huge 8.5-block high-voltage lightning cage perimeter!
            emitGroundSprite(sp, TEX_WITCH_SIGIL, base.add(0, 0.06, 0), r * 1.02, now * 0.0026, col, hot, 225, 0.90);
            EffectSprites.shockwave(sp, base.add(0, 0.14, 0), r * (0.88 + 0.12 * Math.sin(now * 0.012)), -now * 0.004, hot, 225);

            // Central Tesla Coil Tower body
            emitVerticalCrossed(sp, TEX_COLLIDER_BODY, 4, frame, base.add(0, 0.04, 0), 1.65, 3.65, now * 0.0012, 2, COL_WHITE, hot, 255, 0.55);
            Vec3 orb = base.add(0, 3.35, 0);
            EffectSprites.glow(sp, orb, camera, 2.6, hot, 240);
            if (m.autonomous) {
                // Orbiting Florae Familiar operating the autonomous collider!
                double ang = now * 0.0056;
                Vec3 famPos = base.add(Math.cos(ang) * 1.9, 2.6 + 0.35 * Math.sin(now * 0.012), Math.sin(ang) * 1.9);
                emitFramedBillboard(sp, TEX_FAMILIAR_PUPPETS, 4, 0, famPos, camera, 0.72, COL_WHITE, COL_LIGHT_HOT, 255, 0.65);
            }

            // 6 Crackling radial high-voltage lightning bolts forming the Florae Cage!
            for (int i = 0; i < 6; i++) {
                double ang = i * (Math.TAU / 6.0) + now * 0.0022;
                Vec3 edge = base.add(Math.cos(ang) * r * 0.92, 0.45 + 0.65 * Math.sin(now * 0.01 + i), Math.sin(ang) * r * 0.92);
                LightningBolt.bolt(sp, orb, edge, camera, t.start.seed() ^ (now / 52L) ^ (i * 53L), 6, 0.58, 0.088, hot, 238, false);
                if ((i & 1) == 0) {
                    double nextAng = (i + 1) * (Math.TAU / 6.0) + now * 0.0022;
                    Vec3 nextEdge = base.add(Math.cos(nextAng) * r * 0.92, 0.65, Math.sin(nextAng) * r * 0.92);
                    LightningBolt.bolt(sp, edge, nextEdge, camera, t.start.seed() ^ (now / 65L) ^ (i * 97L), 5, 0.42, 0.062, col, 205, false);
                }
            }
        } else {
            renderMachineFinisher(t, m, now, sp, camera, blastR, col, hot, false);
        }
    }

    // --- 9. Lv50 Awakening Fusion Craft (技艺融合) --------------------------

    private static void renderFusionCraft(SkillVfx.Track t, Meta m, long age, long now, List<SpriteQuad> sp, Vec3 camera) {
        Vec3 base = machineAnchor(t, m);
        Vec3 dir = machineDir(t, m);
        double r = Math.max(7.5, m.radius);
        double blastR = Math.max(9.0, m.blastRadius);
        boolean finished = t.last.phase() == FINISH;

        if (!finished) {
            int frame = (int) Math.floorMod(now / 70L, 4L);
            // Grand candy-alchemy arena sigil matching full 8.5-block roller devastation zone
            emitGroundSprite(sp, TEX_WITCH_SIGIL, base.add(0, 0.06, 0), r * 1.04, now * 0.0034, COL_CANDY, COL_CANDY_HOT, 240, 0.90);
            emitGroundSprite(sp, TEX_ANTI_GRAVITY_SIGIL, base.add(0, 0.10, 0), r * 0.68, -now * 0.0052, COL_DARK, COL_LIGHT_HOT, 215, 0.85);

            // Colossal 4-Familiar Fusion Roller Machine
            Vec3 machinePos = base.add(dir.scale(1.2));
            emitVerticalCrossed(sp, TEX_FUSION_ROLLER, 4, frame, machinePos.add(0, 0.05, 0), 2.55, 4.4, Math.atan2(dir.z, dir.x), 2, COL_WHITE, COL_CANDY_HOT, 255, 0.50);
            EffectSprites.glow(sp, machinePos.add(0, 2.5, 0), camera, 3.6, COL_CANDY_HOT, 225);

            // 4 Orbiting Familiar Mascots (Florae / Jack / Frost / Pluto) cheering & powering the roller!
            for (int e = 0; e < 4; e++) {
                double ang = now * 0.0046 + e * (Math.PI * 0.5);
                Vec3 orbPos = base.add(Math.cos(ang) * 3.4, 2.6 + 0.55 * Math.sin(now * 0.009 + e * 1.6), Math.sin(ang) * 3.4);
                emitFramedBillboard(sp, TEX_FAMILIAR_PUPPETS, 4, e, orbPos, camera, 0.88, COL_WHITE, elemHot(e), 255, 0.60);
                LightningBolt.bolt(sp, orbPos, machinePos.add(0, 2.2, 0), camera, t.start.seed() ^ (now / 55L) ^ (e * 71L), 5, 0.36, 0.068, elemHot(e), 225, false);
            }

            // Rotating 4-element shockwave ripples matching every roller hit!
            int elemIdx = Math.floorMod(m.tickIndex, 4);
            double waveU = (now % 340L) / 340.0;
            EffectSprites.shockwave(sp, base.add(0, 0.16, 0), r * 1.05 * EffectSprites.easeOutCubic(waveU), now * 0.005, elemHot(elemIdx), (int) (245 * (1.0 - waveU)));
        } else {
            // Grand 4-Element Fusion Craft Nuclear Supernova Finale!
            long fa = Math.max(0L, now - t.last.at());
            double finDur = m.bitter ? 680.0 : 1050.0;
            double u = Math.clamp(fa / finDur, 0.0, 0.999);
            double fade = 1.0 - u;
            int frame = Math.clamp((int) (u * 8), 0, 7);
            Vec3 center = vector(t.last.position());

            emitGroundFrame(sp, TEX_BITTER_EXPLOSION, 8, frame, center.add(0, 0.18, 0), blastR * 1.15, u * 1.4, COL_WHITE, COL_CANDY_HOT, (int) (255 * fade), 0.98);
            emitFramedBillboard(sp, TEX_BITTER_EXPLOSION, 8, frame, center.add(0, 2.4, 0), camera, blastR * 0.92, COL_CANDY, COL_LIGHT_HOT, (int) (255 * fade), 0.96);
            emitVerticalCrossed(sp, TEX_LAVA_GEYSER, 8, frame, center.add(0, 0.08, 0), 3.6, 12.5, now * 0.003, 3, COL_WHITE, COL_CANDY_HOT, (int) (240 * fade), 0.92);
            emitVerticalCrossed(sp, TEX_ANTI_GRAVITY_BEAM, 8, frame, center.add(0, 0.08, 0), 4.4, 10.0, -now * 0.0025, 3, COL_WHITE, COL_DARK_HOT, (int) (225 * fade), 0.88);

            // 4 Staggered Elemental Shockwaves (Light / Fire / Ice / Dark)
            for (int e = 0; e < 4; e++) {
                double ex = blastR * Math.min(1.18, EffectSprites.easeOutCubic(Math.min(1.0, fa / 520.0 + 0.09 * e)));
                EffectSprites.shockwave(sp, center.add(0, 0.22 + e * 0.95, 0), ex, (e % 2 == 0 ? 1 : -1) * (now * 0.005 + e), elemHot(e), (int) (250 * fade));
            }
            // 8 Radial Elemental Thunder Discharges
            for (int i = 0; i < 8; i++) {
                int e = i & 3;
                double ang = i * (Math.TAU / 8.0) + u * 0.35;
                Vec3 edge = center.add(Math.cos(ang) * blastR * 0.95, 0.5 + 1.2 * Math.sin(i * 1.7), Math.sin(ang) * blastR * 0.95);
                LightningBolt.bolt(sp, center.add(0, 2.4, 0), edge, camera, t.start.seed() ^ (fa / 48L) ^ (i * 131L), 7, 0.72, 0.095, elemHot(e), (int) (242 * fade), false);
            }
        }
    }

    private static void renderMachineFinisher(
        SkillVfx.Track t, Meta m, long now, List<SpriteQuad> sp, Vec3 camera,
        double blastR, int col, int hot, boolean magmaColumn
    ) {
        long fa = Math.max(0L, now - t.last.at());
        double finDur = m.bitter ? 560.0 : 850.0;
        double u = Math.clamp(fa / finDur, 0.0, 0.999);
        double fade = 1.0 - u;
        int frame = Math.clamp((int) (u * 8), 0, 7);
        Vec3 center = vector(t.last.position());
        double scale = m.bitter ? 1.16 : 1.0;

        emitGroundFrame(sp, TEX_BITTER_EXPLOSION, 8, frame, center.add(0, 0.16, 0), blastR * scale, u * 1.3, m.bitter ? COL_CANDY : col, hot, (int) (255 * fade), 0.95);
        emitFramedBillboard(sp, TEX_BITTER_EXPLOSION, 8, frame, center.add(0, 1.85, 0), camera, blastR * 0.78 * scale, COL_WHITE, m.bitter ? COL_CANDY_HOT : hot, (int) (252 * fade), 0.94);
        if (magmaColumn) {
            emitVerticalCrossed(sp, TEX_LAVA_GEYSER, 8, frame, center.add(0, 0.06, 0), blastR * 0.52, 8.2, now * 0.0025, 3, COL_WHITE, hot, (int) (242 * fade), 0.92);
        }
        EffectSprites.shockwave(sp, center.add(0, 0.14, 0), blastR * 1.15 * scale * EffectSprites.easeOutCubic(u), u * 2.0, hot, (int) (248 * fade));
        EffectSprites.glow(sp, center.add(0, 1.8, 0), camera, blastR * 0.95 * fade, m.bitter ? COL_CANDY : col, (int) (235 * fade));
    }

    // --- Fusion Craft Awakening Conditional 3rd-Person Camera ---------------

    private static SkillVfx.CameraPose fusionCraftCamera(SkillVfx.Track t, long time) {
        var mc = Minecraft.getInstance();
        if (t == null || mc == null || mc.level == null) return null;
        long age = Math.max(0L, time - t.start.startedAt());
        boolean finished = t.last.phase() == FINISH;
        long finAge = finished ? Math.max(0L, time - t.last.at()) : 0L;

        Vec3 anchor = machineAnchor(t);
        Vec3 dir = machineDir(t);
        Vec3 side = rightOf(dir);

        double open = EffectSprites.easeOutCubic(Math.clamp(age / 550.0, 0.0, 1.0));
        double finPull = finished ? EffectSprites.easeOutCubic(Math.clamp(finAge / 480.0, 0.0, 1.0)) : 0.0;
        double orbit = Math.sin(age * 0.00075) * 2.8;

        Vec3 focus = anchor.add(0, 2.0, 0);
        double backDist = lerp(6.5, 11.2, open) + 2.8 * finPull;
        double height = lerp(2.8, 6.8, open) + 2.0 * finPull;
        Vec3 camPos = focus.subtract(dir.scale(backDist)).add(side.scale(2.4 + orbit)).add(0, height, 0);

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

    // --- Reusable Dual-Layer Sprite Emitters (Strictly Validated UVs!) ------

    private static void emitBroomSweep(
        List<SpriteQuad> out, Vec3 center, Vec3 forward, double rollDeg,
        double radius, double progress, boolean mirror, int rgb, int alpha
    ) {
        if (progress < 0.0 || progress >= 1.0 || radius <= 0.0 || alpha <= 4) return;
        Vec3 dir = horizontalDir(forward);
        Vec3 right0 = rightOf(dir);
        double roll = Math.toRadians(rollDeg);
        Vec3 side = right0.scale(Math.cos(roll)).add(UP.scale(Math.sin(roll))).normalize();
        if (mirror) side = side.scale(-1.0);

        double scale = radius * (0.85 + 0.24 * EffectSprites.easeOutCubic(progress));
        int frame = Math.clamp((int) (progress * 8), 0, 7);
        float u0 = frame / 8.0f, u1 = (frame + 1) / 8.0f;
        int a = Math.clamp((int) Math.round(alpha * (1.0 - 0.36 * progress * progress)), 0, 255);

        Vec3 halfU = dir.scale(scale);
        Vec3 halfV = side.scale(scale);
        EffectSprites.emitPlane(out, TEX_BROOM_SWEEP, Blend.TRANSLUCENT, center, halfU.scale(1.04), halfV.scale(1.04), u0, 0f, u1, 1f, COL_CANDY_DARK, (int) (a * 0.72));
        EffectSprites.emitPlane(out, TEX_BROOM_SWEEP, Blend.ADDITIVE, center, halfU, halfV, u0, 0f, u1, 1f, rgb, a);
        EffectSprites.emitPlane(out, TEX_BROOM_SWEEP, Blend.ADDITIVE, center, halfU.scale(0.95), halfV.scale(0.95), u0, 0f, u1, 1f, EffectSprites.brighten(rgb, 0.55), (int) (a * 0.65));
    }

    /** Ground-anchored N-plane volumetric vertical column / machine body (v=0 sky top, v=1 ground base -> halfV = -height/2!). */
    private static void emitVerticalCrossed(
        List<SpriteQuad> out, Identifier texture, int totalFrames, int frame,
        Vec3 base, double halfWidth, double height, double yawOffset, int planes,
        int baseRgb, int boostRgb, int alpha, double boostScale
    ) {
        if (halfWidth <= 0.0 || height <= 0.0 || alpha <= 4) return;
        int f = Math.clamp(frame, 0, totalFrames - 1);
        float u0 = f / (float) totalFrames;
        float u1 = (f + 1) / (float) totalFrames;
        Vec3 center = base.add(0, height * 0.5, 0);
        Vec3 halfV = new Vec3(0, -height * 0.5, 0);
        int a = Math.clamp(alpha, 0, 255);
        int ba = Math.clamp((int) Math.round(a * boostScale), 0, 255);

        for (int i = 0; i < planes; i++) {
            double ang = yawOffset + i * (Math.PI / planes);
            Vec3 halfU = new Vec3(Math.cos(ang) * halfWidth, 0, Math.sin(ang) * halfWidth);
            EffectSprites.emitPlane(out, texture, Blend.TRANSLUCENT, center, halfU, halfV, u0, 0f, u1, 1f, baseRgb, a);
            if (ba > 4 && i < 2) {
                EffectSprites.emitPlane(out, texture, Blend.ADDITIVE, center, halfU.scale(0.98), halfV, u0, 0f, u1, 1f, boostRgb, ba);
            }
        }
    }

    private static void emitGroundSprite(
        List<SpriteQuad> out, Identifier texture, Vec3 center, double radius, double rotRad,
        int baseRgb, int boostRgb, int alpha, double boostScale
    ) {
        if (radius <= 0.0 || alpha <= 4) return;
        int a = Math.clamp(alpha, 0, 255);
        EffectSprites.emitGround(out, texture, Blend.TRANSLUCENT, center, radius, rotRad, 0f, 0f, 1f, 1f, baseRgb, a);
        int ba = Math.clamp((int) Math.round(a * boostScale), 0, 255);
        if (ba > 4) {
            EffectSprites.emitGround(out, texture, Blend.ADDITIVE, center.add(0, 0.016, 0), radius * 0.99, rotRad, 0f, 0f, 1f, 1f, boostRgb, ba);
        }
    }

    private static void emitGroundFrame(
        List<SpriteQuad> out, Identifier texture, int totalFrames, int frame,
        Vec3 center, double radius, double rotRad,
        int baseRgb, int boostRgb, int alpha, double boostScale
    ) {
        if (radius <= 0.0 || alpha <= 4) return;
        int f = Math.clamp(frame, 0, totalFrames - 1);
        float u0 = f / (float) totalFrames;
        float u1 = (f + 1) / (float) totalFrames;
        int a = Math.clamp(alpha, 0, 255);
        EffectSprites.emitGround(out, texture, Blend.TRANSLUCENT, center, radius, rotRad, u0, 0f, u1, 1f, baseRgb, a);
        int ba = Math.clamp((int) Math.round(a * boostScale), 0, 255);
        if (ba > 4) {
            EffectSprites.emitGround(out, texture, Blend.ADDITIVE, center.add(0, 0.018, 0), radius * 1.02, rotRad, u0, 0f, u1, 1f, boostRgb, ba);
        }
    }

    /** Upright camera-facing dual-layer billboard (v=0 top -> halfV = up.scale(-radius)!). */
    private static void emitFramedBillboard(
        List<SpriteQuad> out, Identifier texture, int totalFrames, int frame,
        Vec3 center, Vec3 camera, double radius,
        int baseRgb, int boostRgb, int alpha, double boostScale
    ) {
        if (radius <= 0.0 || alpha <= 4) return;
        int f = Math.clamp(frame, 0, totalFrames - 1);
        float u0 = f / (float) totalFrames;
        float u1 = (f + 1) / (float) totalFrames;
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
            EffectSprites.emitPlane(out, texture, Blend.ADDITIVE, center.add(look.scale(0.028)), halfU.scale(1.03), halfV.scale(1.03), u0, 0f, u1, 1f, boostRgb, ba);
        }
    }

    // --- Pure Palette & Geometry Utilities ----------------------------------

    private static Vec3 machineAnchor(SkillVfx.Track t) {
        return machineAnchor(t, METAS.getOrDefault(t.start.cast(), DEFAULT_META));
    }

    private static Vec3 machineAnchor(SkillVfx.Track t, Meta m) {
        if (m != null && m.autonomous) {
            return vector(t.start.origin());
        }
        var mc = Minecraft.getInstance();
        if (mc != null && mc.level != null) {
            Player caster = mc.level.getPlayerByUUID(t.start.caster());
            if (caster != null && caster.isAlive()) {
                return PlayerVisualAnchor.position(caster);
            }
        }
        return vector(t.last.position());
    }

    private static Vec3 machineDir(SkillVfx.Track t) {
        return machineDir(t, METAS.getOrDefault(t.start.cast(), DEFAULT_META));
    }

    private static Vec3 machineDir(SkillVfx.Track t, Meta m) {
        if (m != null && m.autonomous) {
            return horizontalDir(vector(t.start.direction()));
        }
        var mc = Minecraft.getInstance();
        if (mc != null && mc.level != null) {
            Player caster = mc.level.getPlayerByUUID(t.start.caster());
            if (caster != null && caster.isAlive()) {
                return horizontalDir(PlayerVisualAnchor.forward(caster));
            }
        }
        return horizontalDir(vector(t.last.direction()));
    }

    private static int innateElement(String id) {
        return switch (id) {
            case WitchSkills.LAVA_POTION, WitchSkills.RAGING_FLAME_FURNACE -> 1;
            case WitchSkills.ACID_RAIN, WitchSkills.MEGA_DRILL -> 2;
            case WitchSkills.FLORAE_COLLIDER -> 0;
            case WitchSkills.SHADOW_CLOAK, WitchSkills.GIANT_FLYSWATTER, WitchSkills.ANTI_GRAVITY -> 3;
            default -> -1;
        };
    }

    private static int elemColor(int element) {
        return switch (element) {
            case 0 -> COL_LIGHT;
            case 1 -> COL_FIRE;
            case 2 -> COL_ICE;
            case 3 -> COL_DARK;
            default -> COL_CANDY;
        };
    }

    private static int elemHot(int element) {
        return switch (element) {
            case 0 -> COL_LIGHT_HOT;
            case 1 -> COL_FIRE_HOT;
            case 2 -> COL_ICE_HOT;
            case 3 -> COL_DARK_HOT;
            default -> COL_CANDY_HOT;
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
