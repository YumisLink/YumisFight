package cn.yumis.fight.modules.skills.client;

import cn.yumis.fight.modules.presentation.api.client.CameraShake;
import cn.yumis.fight.modules.presentation.api.client.EffectSprites;
import cn.yumis.fight.modules.presentation.api.client.EffectSprites.Blend;
import cn.yumis.fight.modules.presentation.api.client.EffectSprites.SpriteQuad;
import cn.yumis.fight.modules.presentation.api.client.LightningBolt;
import cn.yumis.fight.modules.presentation.api.client.RibbonTrail;
import cn.yumis.fight.modules.presentation.api.client.WorldQuads.Quad;
import cn.yumis.fight.modules.skills.api.CareerSkills;
import cn.yumis.fight.modules.skills.api.SkillVisualEvent;
import cn.yumis.fight.modules.skills.api.SkillVisualRules;
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
 * Soul Bender (鬼泣 / 弑魂) dedicated DNF-inspired VFX renderer.
 * Zero legacy untextured WorldQuads: 100% custom high-res pixel-sharp + soft-feathered sprite sheets
 * (source: tools/build_soulbender_effect_textures.py), dual-layer dark-abyssal translucent backing +
 * violet/ghost-cyan additive glow, camera-aligned ribbons, dark lightning, frozen-pose afterimages,
 * Keiga dark basic attacks, 4-ghost concentric field with iconic spirit portraits, delayed Phantom Slash
 * dimensional cut detonation, Kalla dark-flame explosions & triple finisher, and Blache swamp devouring
 * awakening with 3rd-person-only cinematic camera.
 * Pure client presentation: never grants combat authority, hitboxes, cooldowns, or movement privileges.
 */
final class SoulBenderVisuals {
    private static final Vec3 UP = new Vec3(0, 1, 0);

    // Custom pixel-art Soul Bender VFX sprite sheets (source: tools/build_soulbender_effect_textures.py)
    static final Identifier TEX_SLASH = tex("soul_slash.png");
    static final Identifier TEX_FIELD_SIGIL = tex("ghost_field_sigil.png");
    static final Identifier TEX_FIELD_INNER = tex("ghost_field_inner.png");
    static final Identifier TEX_GHOST_SPIRITS = tex("ghost_spirits.png");
    static final Identifier TEX_DEVOUR_HEAD = tex("soul_devour_head.png");
    static final Identifier TEX_TOMBSTONE = tex("tombstone_pillar.png");
    static final Identifier TEX_PHANTOM_RIFT = tex("phantom_rift.png");
    static final Identifier TEX_GHOST_FLASH = tex("ghost_flash.png");
    static final Identifier TEX_KALLA_SPIRIT = tex("kalla_spirit.png");
    static final Identifier TEX_KALLA_FIREBALL = tex("kalla_fireball.png");
    static final Identifier TEX_KALLA_EXPLOSION = tex("kalla_explosion.png");
    static final Identifier TEX_BLACHE_SWAMP = tex("blache_swamp.png");
    static final Identifier TEX_BLACHE_HEAD = tex("blache_head.png");
    static final Identifier TEX_BLACHE_HAND = tex("blache_hand.png");
    static final Identifier TEX_ABYSS_PILLAR = tex("abyss_flame_pillar.png");
    static final Identifier TEX_GHOST_MIST = tex("ghost_mist.png");
    static final Identifier TEX_MOON_ARC = tex("moonlight_arc.png");
    static final Identifier TEX_FULL_MOON = tex("full_moon_ring.png");

    // Classic DNF Remastered Dark-Violet Netherworld palette
    private static final int COL_WHITE = 0xFFFFFF;
    private static final int COL_ABYSS_DARK = 0x160728;
    private static final int COL_VIOLET = 0x9C4AF0;
    private static final int COL_LAVENDER = 0xC89CFF;
    private static final int COL_GHOST_CYAN = 0xA8ECFF;
    private static final int COL_KALLA_FLAME = 0xB44CFF;
    private static final int COL_KEIGA_BASIC = 0x8E56D9;

    // Distinct DNF 4-Ghost Field colors: Kazan (red), Bremen (green), Saya (ice cyan), Rhasa (plague violet)
    private static final int[] GHOST_COLORS = {0xEB3E4A, 0x45DF7E, 0x62DAFF, 0xB45CFF};
    private static final int[] GHOST_DARKS = {0x3A0A10, 0x0A301B, 0x0B2B44, 0x240A3C};

    private static final List<String> ALL_SKILLS = List.of(
        CareerSkills.KEIGA,
        CareerSkills.FIELD,
        CareerSkills.SOUL_SLASH,
        CareerSkills.MOONLIGHT,
        CareerSkills.TOMBSTONE,
        CareerSkills.PHANTOM,
        CareerSkills.KALLA,
        CareerSkills.BLACHE,
        SkillVisualRules.SB_HELL_WAVE,
        SkillVisualRules.SB_MOON_WAVE,
        SkillVisualRules.SB_GHOST_STEP,
        SkillVisualRules.SB_PHANTOM_RIFT
    );

    // Pure-client transient visual cues (never authoritative)
    private record DevouredField(Vec3 casterOrigin, Vec3 fieldCenter, int ghosts, long recordedAt) {}
    private static DevouredField pendingDevour;
    private static final Map<UUID, DevouredField> BLACHE_DEVOURS = new LinkedHashMap<>();
    private static final Map<UUID, Long> LAST_KEIGA_STEP_AFTERIMAGE = new HashMap<>();
    private static final Map<UUID, Integer> PHANTOM_AFTERIMAGE_STAGE = new HashMap<>();
    private static final Set<UUID> PHANTOM_BURST_CUED = new LinkedHashSet<>();
    private static final Set<UUID> BLACHE_EMERGE_CUED = new LinkedHashSet<>();

    private SoulBenderVisuals() {}

    private static Identifier tex(String name) {
        return Identifier.fromNamespaceAndPath("yumis_dungeon_and_fight", "textures/effect/soulbender/" + name);
    }

    static void clear() {
        pendingDevour = null;
        BLACHE_DEVOURS.clear();
        LAST_KEIGA_STEP_AFTERIMAGE.clear();
        PHANTOM_AFTERIMAGE_STAGE.clear();
        PHANTOM_BURST_CUED.clear();
        BLACHE_EMERGE_CUED.clear();
    }

    /** Records the local player's just-devoured Ghost Field world position so Blache visibly siphons its spirits. */
    static void recordDevouredField(Vec3 casterOrigin, Vec3 fieldCenter, int ghosts) {
        if (ghosts <= 0 || casterOrigin == null || fieldCenter == null) return;
        pendingDevour = new DevouredField(casterOrigin, fieldCenter, Math.clamp(ghosts, 1, 4),
            cn.yumis.fight.modules.presentation.api.client.ClientUi.now());
    }

    static void install() {
        for (String id : ALL_SKILLS) {
            var content = CareerSkills.content(id);
            boolean isBlache = CareerSkills.BLACHE.equals(id);
            SkillVisualRegistry.register(id, new SkillVisualRegistry.Handler() {
                @Override public boolean persistentEnd(SkillVisualEvent event) {
                    return isPersistentEnd(id, event);
                }

                @Override public boolean cinematic(SkillVisualEvent event) {
                    return isBlache;
                }

                @Override public boolean cinematicFromFirstPerson() {
                    // User preference: 3rd-person-only Blache awakening camera; 1st-person stays free.
                    return false;
                }

                @Override public boolean restoreCamera() {
                    return isBlache;
                }

                @Override public CameraType cameraType(SkillVfx.Track track, long time) {
                    return CameraType.THIRD_PERSON_BACK;
                }

                @Override public SkillVfx.CameraPose camera(SkillVfx.Track track, long time) {
                    return isBlache ? blacheCamera(track, time) : null;
                }

                @Override public int defaultColor() {
                    return content != null ? content.color() : COL_VIOLET;
                }

                @Override public long defaultDuration() {
                    return content != null ? (long) (content.skill().parameters(1).getOrDefault("duration", 1d) * 1000) : 1200L;
                }

                @Override public long finishedUntil(SkillVfx.Track track, SkillVisualEvent event) {
                    return computeFinishedUntil(id, track, event);
                }

                @Override public void tick(SkillVfx.Track track, long time) {
                    SoulBenderVisuals.tickTrack(id, track, time);
                }

                @Override public void play(SkillVfx.Track track, SkillVisualEvent event) {
                    SoulBenderVisuals.playCue(id, track, event);
                }

                @Override public void render(SkillVfx.LevelRenderContext ctx, SkillVfx.Track track, long age, long now, List<Quad> quads, Vec3 camera) {
                    SoulBenderVisuals.renderSkill(id, track, age, now, ctx.sprites(), camera);
                }
            });
        }
    }

    static boolean isPersistentEnd(String id, SkillVisualEvent event) {
        return CareerSkills.persistent(id) && !event.sweep();
    }

    static long computeFinishedUntil(String id, SkillVfx.Track track, SkillVisualEvent event) {
        if (CareerSkills.BLACHE.equals(id)) {
            return Math.max(event.at() + 1200L, track.start.startedAt() + Math.max(track.start.duration(), event.duration()) + 400L);
        }
        if (CareerSkills.PHANTOM.equals(id)) {
            return Math.max(event.at() + 650L, track.start.startedAt() + 920L);
        }
        if (CareerSkills.TOMBSTONE.equals(id)) {
            if (track.start.dual()) {
                return Math.max(event.at() + 760L, track.start.startedAt() + Math.max(2000L, track.start.duration()) + 760L);
            }
            return event.at() + 760L;
        }
        if (CareerSkills.MOONLIGHT.equals(id)) {
            return event.at() + 520L;
        }
        if (SkillVisualRules.SB_PHANTOM_RIFT.equals(id)) {
            return event.at() + 680L;
        }
        if (SkillVisualRules.SB_GHOST_STEP.equals(id)) {
            return event.at() + 580L;
        }
        if (SkillVisualRules.SB_HELL_WAVE.equals(id) || SkillVisualRules.SB_MOON_WAVE.equals(id)) {
            return event.at() + 360L;
        }
        if (CareerSkills.KALLA.equals(id)) {
            if (track.motion != null) {
                return event.at() + (event.sweep() ? 540L : 220L);
            }
            return event.at() + 620L;
        }
        if (CareerSkills.SOUL_SLASH.equals(id)) {
            return event.at() + 850L;
        }
        return event.at() + 550L;
    }

    // --- Keiga Dark-Attribute Basic Attack Interception & Rendering ----------

    static boolean isKeigaBasic(SkillVisualEvent event) {
        if (event == null || !"ATTACK".equals(event.skill()) || event.color() != COL_KEIGA_BASIC) return false;
        String owner = event.caster().toString();
        for (var actor : CareerClient.INSTANCE.view.actors) {
            if (owner.equals(actor.owner)) return actor.keiga > 0;
        }
        return true;
    }

    static void playKeigaBasicAudio(SkillVisualEvent event) {
        if (event.phase() != START) return;
        float pitch = 0.84f + (float) (new SplittableRandom(event.seed()).nextDouble() * 0.24);
        SkillSound.playAt(event.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 0.62f, pitch);
        SkillSound.playAt(event.position(), SoundEvents.SOUL_ESCAPE.value(), 0.42f, pitch + 0.16f);
        if (event.sweep()) {
            SkillSound.playAt(event.position(), SoundEvents.PLAYER_ATTACK_STRONG, 0.72f, 0.78f);
            CameraShake.impulse(vector(event.position()), 12, 0.20f, 55L, 120L);
        }
    }

    static void renderKeigaBasic(List<SpriteQuad> sp, Vec3 camera, SkillVisualEvent e, long age, long baseDuration) {
        if (e.phase() != START) return;
        Vec3 dir = horizontalDir(vector(e.direction()));
        Vec3 pos = vector(e.position()).add(dir.scale(e.forwardOffset()));
        double hScale = Math.max(0.8, e.horizontalScale());
        SplittableRandom rng = new SplittableRandom(e.seed());
        boolean flip = rng.nextBoolean();
        boolean offhandStrike = BuiltinSkillVisuals.isDualOffhandStrike(e);
        var mc = Minecraft.getInstance();
        Player caster = mc == null || mc.level == null ? null : mc.level.getPlayerByUUID(e.caster());

        if (e.sweep()) {
            var sample = BuiltinSkillVisuals.basicSlashSample(age, baseDuration);
            if (sample == null) return;
            double progress = sample.progress();
            double aScale = sample.alphaScale();
            double r = 3.55 * hScale;
            Vec3 c = pos.add(dir.scale(1.6)).add(0, 1.15, 0);
            int a1 = (int) Math.round(248 * aScale);
            int a2 = (int) Math.round(212 * aScale);
            if (a1 > 4) emitSoulSlash(sp, c, dir, flip ? -10 : 10, r, progress, flip, a1, true);
            if (a2 > 4) emitSoulSlash(sp, c.add(0, 0.16, 0), dir, flip ? 18 : -18, r * 0.88, Math.min(0.999, progress * 1.07), !flip, a2, false);
            emitGhostMist(sp, c.add(dir.scale(0.85)), camera, 1.85, e.at() + age, (int) Math.round(135 * aScale), 0);
        } else if (e.dual()) {
            var sample = BuiltinSkillVisuals.basicSlashSample(age, baseDuration);
            if (sample == null) return;
            boolean leftMainArm = caster != null && caster.getMainArm() == net.minecraft.world.entity.HumanoidArm.LEFT;
            boolean leftCut = offhandStrike ^ leftMainArm;
            double tiltMag = 26.0 + rng.nextDouble() * 14.0;
            double tilt = leftCut ? -tiltMag : tiltMag;
            boolean mirror = !leftCut;
            Vec3 c = pos.add(dir.scale(0.72)).add(0, leftCut ? 1.09 : 1.19, 0);
            double rad = (leftCut ? 2.04 : 1.98) * hScale;
            int alpha = (int) Math.round(244 * sample.alphaScale());
            if (alpha > 4) {
                emitSoulSlash(sp, c, dir, tilt, rad, sample.progress(), mirror, alpha, true);
            }
        } else {
            var sample = BuiltinSkillVisuals.basicSlashSample(age, baseDuration);
            if (sample == null) return;
            double progress = sample.progress();
            double aScale = sample.alphaScale();
            double roll = (flip ? 1 : -1) * (20.0 + rng.nextDouble() * 38.0);
            Vec3 c = pos.add(dir.scale(0.68)).add(0, 1.14, 0);
            double rad = 1.95 * hScale;
            int a1 = (int) Math.round(244 * aScale);
            int a2 = (int) Math.round(165 * aScale);
            if (a1 > 4) emitSoulSlash(sp, c, dir, roll, rad, progress, flip, a1, true);
            if (a2 > 4) emitSoulSlash(sp, c.add(0, -0.05, 0), dir, roll * 0.72, rad * 0.88, Math.min(0.999, progress * 0.94), flip, a2, false);
        }

        // Flowing dark-violet ghost-blade ribbon off animated main-hand weapon tip (suppressed in own 1st person)
        if (!offhandStrike && caster != null && !isOwnFirstPerson(e.caster()) && age < 220L) {
            var pose = Effects.VFX.playerPose(caster.getUUID(), e.at() + age);
            Vec3 tip = PlayerBoneAnchor.weaponTip(caster, pose == null ? null : pose.frame(), 1.24);
            RibbonTrail.push("keiga_atk:" + e.cast(), tip, 0.19, COL_VIOLET, 215, 185L, e.at() + age);
        }
    }

    // --- Persistent Actor Visuals: Step-Fusion Keiga Mist & Kalla Dark-Flame Reaper ---

    static void actors(List<SpriteQuad> sp, Vec3 camera, long serverNow, long renderNow) {
        var mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) return;

        for (var actor : CareerClient.INSTANCE.view.actors) {
            boolean hasKeiga = actor.keiga > 0;
            boolean hasKalla = actor.kallaUntil > serverNow;
            if (!hasKeiga && !hasKalla) continue;

            UUID ownerId;
            try {
                ownerId = UUID.fromString(actor.owner);
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            Player p = mc.level.getPlayerByUUID(ownerId);
            if (p == null || !p.isAlive()) continue;

            boolean ownFp = isOwnFirstPerson(ownerId);
            Vec3 base = PlayerVisualAnchor.position(p);
            Vec3 forward = horizontalDir(PlayerVisualAnchor.forward(p));
            Vec3 right = rightOf(forward);
            double horizSpeedSq = p.getDeltaMovement().horizontalDistanceSqr();

            // 1. 残影之凯贾 (Keiga): Subtle dark-violet shadow foot mist + drifting filaments + brisk-move afterimages
            if (hasKeiga) {
                double fpScale = ownFp ? 0.34 : 1.0;
                int mistCount = ownFp ? 2 : 3;
                for (int i = 0; i < mistCount; i++) {
                    double ang = renderNow * 0.0018 + i * (Math.TAU / mistCount);
                    double rise = (renderNow * 0.00075 + i * 0.33) % 1.0;
                    double rad = (ownFp ? 0.85 : 0.60) + 0.12 * Math.sin(ang * 2.0) - 0.14 * rise;
                    Vec3 mp = base.add(Math.cos(ang) * rad, 0.14 + rise * (ownFp ? 0.45 : 1.15), Math.sin(ang) * rad);
                    int ma = (int) Math.round((hasKalla ? 135 : 95) * fpScale * Math.sin(rise * Math.PI));
                    emitGhostMist(sp, mp, camera, 0.56 + 0.12 * rise, renderNow + i * 210L, ma, i);
                }

                // Subtle ground shadow ring beneath Soul Bender
                double pulse = 0.5 + 0.5 * Math.sin(renderNow * 0.0045);
                EffectSprites.emitGround(sp, TEX_FIELD_INNER, Blend.TRANSLUCENT, base.add(0, 0.035, 0),
                    0.88 + 0.08 * pulse, -renderNow * 0.0011, 0f, 0f, 1f, 1f, COL_ABYSS_DARK, (int) (78 * fpScale));
                EffectSprites.emitGround(sp, TEX_FIELD_INNER, Blend.ADDITIVE, base.add(0, 0.045, 0),
                    0.85 + 0.08 * pulse, -renderNow * 0.0011, 0f, 0f, 1f, 1f, hasKalla ? COL_KALLA_FLAME : COL_VIOLET, (int) ((hasKalla ? 105 : 68) * fpScale));

                // Brisk movement / sprinting leaves ghostly violet Keiga shadow afterimages (3rd person only)
                if (!ownFp && horizSpeedSq > 0.020) {
                    long last = LAST_KEIGA_STEP_AFTERIMAGE.getOrDefault(ownerId, 0L);
                    if (renderNow - last >= 290L) {
                        LAST_KEIGA_STEP_AFTERIMAGE.put(ownerId, renderNow);
                        PlayerAfterimages.capture(p, hasKalla ? COL_KALLA_FLAME : COL_VIOLET, 118, 280L, true);
                    }
                }
            }

            // 2. 冥炎之卡洛 (Kalla 20s): Hovering hooded dark-flame reaper behind back + 3 orbiting dark-flame orbs!
            if (hasKalla && !ownFp) {
                double bob = Math.sin(renderNow * 0.0038) * 0.12;
                Vec3 spiritPos = base.subtract(forward.scale(0.82)).add(0, 1.56 + bob, 0);
                int sFrame = (int) Math.floorMod(renderNow / 135L, 4L);
                emitUprightFramed(sp, TEX_KALLA_SPIRIT, 4, sFrame, spiritPos, camera, 1.12, 1.24, 215, true, COL_KALLA_FLAME, 0.62);

                // 3 orbiting blazing Kalla dark-flame fireballs bobbing around shoulders & cloak
                for (int i = 0; i < 3; i++) {
                    double orbitAng = renderNow * 0.0028 + i * (Math.TAU / 3.0);
                    double orbR = 0.78 + 0.08 * Math.sin(renderNow * 0.005 + i);
                    Vec3 orbPos = base.subtract(forward.scale(0.28))
                        .add(right.scale(Math.cos(orbitAng) * orbR))
                        .add(forward.scale(Math.sin(orbitAng) * orbR * 0.45))
                        .add(0, 1.28 + 0.16 * Math.sin(orbitAng * 1.5 + i), 0);
                    int fFrame = (int) Math.floorMod((renderNow / 70L) + i * 3L, 8L);
                    emitFramedBillboard(sp, TEX_KALLA_FIREBALL, 8, fFrame, orbPos, camera, 0.34, 232, true, COL_KALLA_FLAME, 0.70);
                    if (horizSpeedSq > 0.002) {
                        RibbonTrail.push("kalla_orb:" + ownerId + ":" + i, orbPos, 0.11, COL_KALLA_FLAME, 185, 190L, renderNow);
                    }
                }
            }
        }
    }

    // --- Track Ticks (Delayed Phantom Detonation & Blache Emergence Cues) ----

    private static void tickTrack(String id, SkillVfx.Track t, long now) {
        long age = Math.max(0L, (long) ((now - t.start.startedAt()) * t.speed));
        var mc = Minecraft.getInstance();
        Player actor = mc == null || mc.level == null ? null : mc.level.getPlayerByUUID(t.start.caster());

        if (CareerSkills.PHANTOM.equals(id)) {
            // Capture 3 crisp dark-violet afterimages along the 0..250ms 8-block dash (3rd person)
            if (actor != null && !isOwnFirstPerson(t.start.caster()) && age < 255L) {
                int stage = PHANTOM_AFTERIMAGE_STAGE.getOrDefault(t.start.cast(), 0);
                long[] marks = {55L, 125L, 195L};
                if (stage < marks.length && age >= marks[stage]) {
                    PHANTOM_AFTERIMAGE_STAGE.put(t.start.cast(), stage + 1);
                    PlayerAfterimages.capture(actor, stage == 2 ? COL_GHOST_CYAN : COL_VIOLET, 175, 420L, true);
                }
            }
            // Classic DNF delayed dimensional iaido cut burst right after dash settles (~275ms)!
            if (age >= 275L && PHANTOM_BURST_CUED.add(t.start.cast())) {
                if (PHANTOM_BURST_CUED.size() > 256) PHANTOM_BURST_CUED.remove(PHANTOM_BURST_CUED.iterator().next());
                PHANTOM_AFTERIMAGE_STAGE.remove(t.start.cast());
                Vec3 from = vector(t.start.origin());
                Vec3 to = vector(t.last.position());
                Vec3 mid = from.lerp(to, 0.5);
                SkillSound.playAt(mid.x, mid.y + 1.0, mid.z, SoundEvents.GLASS_BREAK, 0.82f, 0.86f);
                SkillSound.playAt(mid.x, mid.y + 1.0, mid.z, SoundEvents.PLAYER_ATTACK_CRIT, 0.90f, 1.32f);
                SkillSound.playAt(mid.x, mid.y + 1.0, mid.z, SoundEvents.TRIDENT_THUNDER.value(), 0.56f, 1.28f);
                CameraShake.impulse(mid, 16, 0.34f, 80L, 175L);
            }
        } else if (CareerSkills.BLACHE.equals(id)) {
            if (age >= 1120L && BLACHE_EMERGE_CUED.add(t.start.cast())) {
                if (BLACHE_EMERGE_CUED.size() > 128) BLACHE_EMERGE_CUED.remove(BLACHE_EMERGE_CUED.iterator().next());
                Vec3 p = vector(t.start.origin());
                SkillSound.playAt(p.x, p.y + 1.5, p.z, SoundEvents.WARDEN_ROAR, 0.72f, 0.68f);
            }
        }
    }

    // --- Layered Audio & Impact Cues -----------------------------------------

    private static void playCue(String id, SkillVfx.Track t, SkillVisualEvent e) {
        Vec3 pos = vector(e.position());
        var mc = Minecraft.getInstance();
        Player actor = mc == null || mc.level == null ? null : mc.level.getPlayerByUUID(e.caster());

        switch (id) {
            case CareerSkills.KEIGA -> {
                if (e.phase() == START) {
                    SkillSound.playAt(e.position(), SoundEvents.SOUL_ESCAPE.value(), 0.75f, 0.90f);
                }
            }
            case CareerSkills.FIELD -> {
                if (e.phase() == START) {
                    SkillSound.playAt(e.position(), SoundEvents.SOUL_ESCAPE.value(), 0.85f, 0.78f);
                    SkillSound.playAt(e.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 0.65f, 0.74f);
                    CameraShake.impulse(pos, 14, 0.16f, 55L, 120L);
                }
            }
            case CareerSkills.SOUL_SLASH -> {
                if (e.phase() == START) {
                    boolean boosted = e.charge() >= 0.5;
                    SkillSound.playAt(e.position(), SoundEvents.SOUL_ESCAPE.value(), boosted ? 1.0f : 0.85f, boosted ? 0.62f : 0.72f);
                    SkillSound.playAt(e.position(), SoundEvents.BLAZE_SHOOT, boosted ? 0.72f : 0.55f, 0.68f);
                    SkillSound.playAt(e.position(), SoundEvents.PLAYER_ATTACK_STRONG, 0.82f, 0.76f);
                    CameraShake.impulse(pos, 16, boosted ? 0.34f : 0.24f, 75L, 160L);
                }
            }
            case CareerSkills.MOONLIGHT -> {
                if (e.phase() == SEGMENT) {
                    boolean fullMoon = e.sweep();
                    int segNum = Math.max(1, (int) Math.round(e.charge() * 6.0));
                    float pitch = fullMoon ? 0.74f : 1.05f + (segNum % 3) * 0.12f;
                    SkillSound.playAt(e.position(), SoundEvents.PLAYER_ATTACK_SWEEP, fullMoon ? 0.96f : 0.78f, pitch);
                    SkillSound.playAt(e.position(), SoundEvents.AMETHYST_BLOCK_CHIME, fullMoon ? 0.85f : 0.56f, fullMoon ? 0.82f : 1.18f);
                    SkillSound.playAt(e.position(), SoundEvents.SOUL_ESCAPE.value(), fullMoon ? 0.78f : 0.45f, fullMoon ? 0.75f : 1.02f);
                    if (fullMoon) {
                        SkillSound.playAt(e.position(), SoundEvents.PLAYER_ATTACK_STRONG, 0.88f, 0.80f);
                        CameraShake.impulse(pos, 14, 0.28f, 70L, 150L);
                    } else {
                        CameraShake.impulse(pos, 10, 0.12f, 40L, 85L);
                    }
                    if (actor != null && !isOwnFirstPerson(e.caster())) {
                        PlayerAfterimages.capture(actor, fullMoon ? COL_GHOST_CYAN : COL_LAVENDER, fullMoon ? 195 : 145, fullMoon ? 380L : 240L, true);
                    }
                }
            }
            case CareerSkills.TOMBSTONE -> {
                if (e.sweep() && !e.dual()) return;
                if (e.phase() == START) {
                    SkillSound.playAt(e.position(), SoundEvents.SOUL_ESCAPE.value(), 0.88f, 0.66f);
                    SkillSound.playAt(e.position(), SoundEvents.BEACON_ACTIVATE, 0.55f, 0.62f);
                } else if (e.phase() == HIT) {
                    float pitch = 0.68f + (e.sequence() % 4) * 0.07f;
                    SkillSound.playAt(e.position(), SoundEvents.MACE_SMASH_GROUND, 0.56f, pitch);
                    SkillSound.playAt(e.position(), SoundEvents.BASALT_BREAK, 0.68f, pitch + 0.10f);
                    if ((e.sequence() & 1) == 0) {
                        CameraShake.impulse(pos, 14, 0.15f, 40L, 90L);
                    }
                }
            }
            case CareerSkills.PHANTOM -> {
                if (e.phase() == START || (e.phase() == SEGMENT && e.charge() >= 0.4)) {
                    PHANTOM_AFTERIMAGE_STAGE.put(e.cast(), 0);
                    PHANTOM_BURST_CUED.remove(e.cast());
                    // Iconic sharp iaido dash whoosh + lingering Keiga afterimage at departure point!
                    SkillSound.playAt(e.position(), SoundEvents.ENDERMAN_TELEPORT, 0.62f, 1.38f);
                    SkillSound.playAt(e.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 0.85f, 1.28f);
                    SkillSound.playAt(e.position(), SoundEvents.SOUL_ESCAPE.value(), 0.72f, 1.15f);
                    if (actor != null) {
                        PlayerAfterimages.captureAt(actor, vector(e.origin()), vector(e.direction()), COL_VIOLET, 215, 620L, true);
                    }
                    CameraShake.impulse(pos, 12, 0.18f, 50L, 110L);
                }
            }
            case SkillVisualRules.SB_HELL_WAVE -> {
                if (e.phase() == START) {
                    SkillSound.playAt(e.position(), SoundEvents.SOUL_ESCAPE.value(), 0.96f, 0.58f);
                    SkillSound.playAt(e.position(), SoundEvents.BLAZE_SHOOT, 0.78f, 0.62f);
                    CameraShake.impulse(pos, 16, 0.26f, 65L, 140L);
                }
            }
            case SkillVisualRules.SB_MOON_WAVE -> {
                if (e.phase() == START) {
                    SkillSound.playAt(e.position(), SoundEvents.AMETHYST_BLOCK_CHIME, e.sweep() ? 0.85f : 0.62f, e.sweep() ? 0.92f : 1.24f);
                    SkillSound.playAt(e.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 0.68f, 1.18f);
                }
            }
            case SkillVisualRules.SB_GHOST_STEP -> {
                if (e.phase() == START) {
                    SkillSound.playAt(e.position(), SoundEvents.ENDERMAN_TELEPORT, 0.72f, 1.22f);
                    SkillSound.playAt(e.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 0.92f, 0.94f);
                    SkillSound.playAt(e.position(), SoundEvents.SOUL_ESCAPE.value(), 0.82f, 0.85f);
                    if (actor != null && !isOwnFirstPerson(e.caster())) {
                        PlayerAfterimages.capture(actor, COL_GHOST_CYAN, 200, 420L, true);
                    }
                    CameraShake.impulse(pos, 14, 0.22f, 55L, 120L);
                }
            }
            case SkillVisualRules.SB_PHANTOM_RIFT -> {
                if (e.phase() == START) {
                    SkillSound.playAt(e.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 0.68f, 1.32f);
                } else if (e.phase() == FINISH) {
                    SkillSound.playAt(e.position(), SoundEvents.GLASS_BREAK, 0.88f, 0.82f);
                    SkillSound.playAt(e.position(), SoundEvents.TRIDENT_THUNDER.value(), 0.58f, 1.18f);
                    SkillSound.playAt(e.position(), SoundEvents.GENERIC_EXPLODE.value(), 0.52f, 1.15f);
                    CameraShake.impulse(pos, 16, 0.30f, 70L, 150L);
                }
            }
            case CareerSkills.KALLA -> {
                if (t.motion != null) {
                    if (e.phase() == START) {
                        SkillSound.playAt(e.position(), SoundEvents.BLAZE_SHOOT, 0.42f, 1.28f);
                    } else if (e.phase() == FINISH && e.sweep()) {
                        // Crisp 2-block dark-flame explosion impact audio!
                        SkillSound.playAt(e.position(), SoundEvents.FIRECHARGE_USE, 0.68f, 0.86f);
                        SkillSound.playAt(e.position(), SoundEvents.GENERIC_EXPLODE.value(), 0.46f, 1.24f);
                        SkillSound.playAt(e.position(), SoundEvents.SOUL_ESCAPE.value(), 0.55f, 0.92f);
                        CameraShake.impulse(pos, 12, 0.14f, 45L, 95L);
                    }
                    return;
                }
                if (!e.sweep()) {
                    if (e.phase() == START) {
                        SkillSound.playAt(e.position(), SoundEvents.SOUL_ESCAPE.value(), 0.92f, 0.68f);
                        SkillSound.playAt(e.position(), SoundEvents.BLAZE_SHOOT, 0.76f, 0.66f);
                        CameraShake.impulse(pos, 14, 0.22f, 70L, 150L);
                        if (actor != null && !isOwnFirstPerson(e.caster())) {
                            PlayerAfterimages.capture(actor, COL_KALLA_FLAME, 200, 400L, true);
                        }
                    }
                } else if (e.phase() == HIT) {
                    int idx = (int) t.flashes.stream().filter(f -> f.phase() == HIT).count() - 1;
                    boolean third = (idx % 3) == 2;
                    SkillSound.playAt(e.position(), SoundEvents.PLAYER_ATTACK_SWEEP, third ? 0.95f : 0.78f, third ? 0.72f : 1.02f + (idx % 3) * 0.12f);
                    SkillSound.playAt(e.position(), SoundEvents.FIRECHARGE_USE, third ? 0.85f : 0.62f, third ? 0.68f : 0.96f);
                    if (third) {
                        SkillSound.playAt(e.position(), SoundEvents.GENERIC_EXPLODE.value(), 0.72f, 0.82f);
                    }
                    CameraShake.impulse(pos, 16, third ? 0.36f : 0.18f, third ? 95L : 50L, third ? 195L : 105L);
                    if (actor != null && !isOwnFirstPerson(e.caster())) {
                        PlayerAfterimages.capture(actor, third ? COL_GHOST_CYAN : COL_KALLA_FLAME, 180, 320L, true);
                    }
                }
            }
            case CareerSkills.BLACHE -> {
                if (e.phase() == START) {
                    BLACHE_EMERGE_CUED.remove(e.cast());
                    if (isLocalCaster(e.caster()) && pendingDevour != null) {
                        long now = cn.yumis.fight.modules.presentation.api.client.ClientUi.now();
                        if (now - pendingDevour.recordedAt() < 1500L && pendingDevour.casterOrigin().distanceToSqr(vector(e.origin())) < 4.0) {
                            if (BLACHE_DEVOURS.size() >= 64) BLACHE_DEVOURS.remove(BLACHE_DEVOURS.keySet().iterator().next());
                            BLACHE_DEVOURS.put(e.cast(), pendingDevour);
                        }
                        pendingDevour = null;
                    }
                    SkillSound.playAt(e.position(), SoundEvents.BEACON_ACTIVATE, 0.90f, 0.54f);
                    SkillSound.playAt(e.position(), SoundEvents.SOUL_ESCAPE.value(), 1.0f, 0.56f);
                    CameraShake.impulse(pos, 26, 0.30f, 110L, 220L);
                } else if (e.phase() == FINISH) {
                    SkillSound.playAt(e.position(), SoundEvents.GENERIC_EXPLODE.value(), 1.0f, 0.52f);
                    SkillSound.playAt(e.position(), SoundEvents.MACE_SMASH_GROUND_HEAVY, 1.0f, 0.56f);
                    SkillSound.playAt(e.position(), SoundEvents.TRIDENT_THUNDER.value(), 0.88f, 0.62f);
                    CameraShake.impulse(pos, 32, 0.74f, 190L, 380L);
                }
            }
            default -> {}
        }
    }

    // --- Main Skill Rendering Router -----------------------------------------

    static void renderSkill(String id, SkillVfx.Track t, long age, long now, List<SpriteQuad> sp, Vec3 camera) {
        switch (id) {
            case CareerSkills.KEIGA -> renderKeigaCast(t, age, now, sp, camera);
            case CareerSkills.FIELD -> renderGhostField(t, age, now, sp, camera);
            case CareerSkills.SOUL_SLASH -> renderSoulSlash(t, age, now, sp, camera);
            case CareerSkills.MOONLIGHT -> renderMoonlightSlash(t, age, now, sp, camera);
            case CareerSkills.TOMBSTONE -> renderTombstone(t, age, now, sp, camera);
            case CareerSkills.PHANTOM -> renderPhantomSlash(t, age, now, sp, camera);
            case CareerSkills.KALLA -> renderKalla(t, age, now, sp, camera);
            case CareerSkills.BLACHE -> renderBlache(t, age, now, sp, camera);
            case SkillVisualRules.SB_HELL_WAVE -> renderHellWave(t, age, now, sp, camera);
            case SkillVisualRules.SB_MOON_WAVE -> renderMoonWave(t, age, now, sp, camera);
            case SkillVisualRules.SB_GHOST_STEP -> renderGhostStep(t, age, now, sp, camera);
            case SkillVisualRules.SB_PHANTOM_RIFT -> renderPhantomRiftBurst(t, age, now, sp, camera);
            default -> {}
        }
    }

    // --- 1. 残影之凯贾 (Keiga Learn/Pulse Burst) -----------------------------

    private static void renderKeigaCast(SkillVfx.Track t, long age, long now, List<SpriteQuad> sp, Vec3 camera) {
        if (age >= 900L) return;
        double u = Math.clamp(age / 900.0, 0.0, 0.999);
        double fade = 1.0 - u;
        Vec3 p = liveCasterPos(t.start.caster(), vector(t.start.origin()));
        emitFieldSigil(sp, TEX_FIELD_INNER, p.add(0, 0.05, 0), 1.85 * (0.55 + 0.55 * EffectSprites.easeOutCubic(u)),
            now * 0.003, COL_ABYSS_DARK, COL_VIOLET, (int) (200 * fade));
        emitGhostMist(sp, p.add(0, 0.95, 0), camera, 1.75, now, (int) (185 * fade), 0);
    }

    // --- 2. 鬼神之阵 (4-Ghost Concentric Field + 4 DNF Spirit Portraits) -----

    private static void renderGhostField(SkillVfx.Track t, long age, long now, List<SpriteQuad> sp, Vec3 camera) {
        Vec3 center = t.start.dual()
            ? liveCasterPos(t.start.caster(), vector(t.start.origin()))
            : vector(t.start.origin());
        int rank = Math.max(1, (int) Math.round(t.start.charge() * 15.0));
        int count = Math.clamp(CareerSkills.ghosts(rank, t.start.dual()), 1, 4);

        long totalDur = Math.max(1000L, t.start.duration());
        double appear = Math.clamp(age / 360.0, 0.0, 1.0);
        double endFade = age > totalDur - 450L ? Math.clamp((totalDur - age) / 450.0, 0.0, 1.0) : 1.0;
        double env = EffectSprites.easeOutCubic(appear) * endFade;
        if (env <= 0.01) return;

        // First-person comfort attenuation when standing inside the field
        boolean ownFpInside = isOwnFirstPerson(t.start.caster()) && camera.subtract(center).horizontalDistanceSqr() < 30.0;
        double spiritVis = ownFpInside ? 0.46 : 0.84;

        // Soft dark abyssal ground veil so all 4 ghost colors stay vivid even on bright terrain!
        EffectSprites.emitGround(sp, TEX_BLACHE_SWAMP, Blend.TRANSLUCENT, center.add(0, 0.025, 0),
            5.15 * (0.65 + 0.35 * appear), now * 0.00035, 0f, 0f, 1f, 1f, COL_WHITE, (int) (72 * env));

        // Layered counter-rotating concentric DNF Ghost Field sigils (1: Kazan, 2: Bremen, 3: Saya, 4: Rhasa)
        double[] radii = {5.12, 4.30, 3.38, 2.38};
        double[] speeds = {0.00072, -0.00096, 0.00125, -0.00155};
        for (int i = 0; i < count; i++) {
            Identifier tex = (i % 2 == 0) ? TEX_FIELD_SIGIL : TEX_FIELD_INNER;
            double r = radii[i] * (0.55 + 0.45 * appear);
            int alpha = (int) Math.round((i == 0 ? 198 : 178) * env);
            emitFieldSigil(sp, tex, center.add(0, 0.040 + i * 0.010, 0), r, now * speeds[i] + i * 0.7,
                GHOST_DARKS[i], GHOST_COLORS[i], alpha);
        }

        // Initial deployment shockwave flash
        if (age < 460L) {
            double su = age / 460.0;
            EffectSprites.shockwave(sp, center.add(0, 0.09, 0), 5.3 * EffectSprites.easeOutCubic(su), su * 1.6,
                GHOST_COLORS[count - 1], (int) (220 * (1.0 - su)));
        }

        // 4 Cardinal Handcrafted DNF Ghost Spirits (Kazan / Bremen / Saya / Rhasa) floating around the perimeter!
        for (int i = 0; i < count; i++) {
            double ang = i * (Math.PI * 0.5) + Math.PI * 0.25 + Math.sin(now * 0.0006 + i) * 0.08;
            double bob = 0.20 * Math.sin(now * 0.0032 + i * 1.6);
            Vec3 baseSpot = center.add(Math.cos(ang) * 3.55, 0.04, Math.sin(ang) * 3.55);
            Vec3 spiritPos = baseSpot.add(0, 1.38 + bob, 0);

            // Fade spirit gently if camera gets right next to it
            double camDist = camera.distanceTo(spiritPos);
            double proximity = Math.clamp((camDist - 1.1) / 2.2, 0.22, 1.0);
            int sAlpha = (int) Math.round(188 * env * spiritVis * proximity);

            // Ground pedestal ring under each summoned ghost
            EffectSprites.emitGround(sp, TEX_FIELD_INNER, Blend.ADDITIVE, baseSpot.add(0, 0.02, 0),
                0.88, -now * 0.0022 + i, 0f, 0f, 1f, 1f, GHOST_COLORS[i], (int) (135 * env));
            // Iconic pixel-art DNF Ghost Spirit portrait (frame 0=Kazan, 1=Bremen, 2=Saya, 3=Rhasa)
            if (sAlpha > 8) {
                emitUprightFramed(sp, TEX_GHOST_SPIRITS, 4, i, spiritPos, camera, 1.08, 1.18, sAlpha, true, GHOST_COLORS[i], 0.58);
            }
            // Subtle rising ambient mist / elemental glints matching each unlocked ghost
            double rise = ((now * 0.00085 + i * 0.27) % 1.0);
            Vec3 wispPos = baseSpot.add(Math.cos(now * 0.003 + i * 2.0) * 0.65, 0.25 + rise * 2.1, Math.sin(now * 0.003 + i * 2.0) * 0.65);
            int wAlpha = (int) Math.round(140 * env * Math.sin(rise * Math.PI) * (ownFpInside ? 0.55 : 1.0));
            if (wAlpha > 6) {
                EffectSprites.glow(sp, wispPos, camera, i == 2 ? 0.34 : 0.42, GHOST_COLORS[i], wAlpha);
            }
        }

        // Extra swirling frost crystal pulse for Saya (Lv8+) and dark plague mist for Rhasa (Lv15+)
        if (count >= 3) {
            double frostPulse = (now % 1000L) / 1000.0;
            EffectSprites.shockwave(sp, center.add(0, 0.085, 0), 5.0 * EffectSprites.easeOutCubic(frostPulse),
                frostPulse * 1.1, GHOST_COLORS[2], (int) (115 * env * (1.0 - frostPulse)));
        }
        if (count >= 4) {
            for (int k = 0; k < 3; k++) {
                double a = now * 0.0016 + k * (Math.TAU / 3.0);
                double r = 1.6 + 1.4 * Math.sin(now * 0.0011 + k * 2.1);
                Vec3 mp = center.add(Math.cos(a) * r, 0.45 + 0.35 * Math.sin(now * 0.0025 + k), Math.sin(a) * r);
                emitGhostMist(sp, mp, camera, 1.15, now + k * 230L, (int) (98 * env * (ownFpInside ? 0.45 : 1.0)), k);
            }
        }
    }

    // --- 3. 噬灵鬼斩 (Soul Slash: Lunging Oni-Beast Head + Field Boost) -------

    private static void renderSoulSlash(SkillVfx.Track t, long age, long now, List<SpriteQuad> sp, Vec3 camera) {
        if (age >= 850L) return;
        double u = Math.clamp(age / 850.0, 0.0, 0.999);
        double fastU = Math.clamp(age / 480.0, 0.0, 0.999);
        double fade = 1.0 - u;
        boolean boosted = t.start.charge() >= 0.5;
        double scaleBoost = boosted ? 1.28 : 1.0;

        Vec3 origin = vector(t.start.origin()); // already at y+1.0 from SkillBehaviors
        Vec3 dir = horizontalDir(vector(t.start.direction()));
        Vec3 right = rightOf(dir);

        // Sweeping thick dark-violet spectral crescent reinforcing Ghost Slash
        if (age < 480L) {
            emitSoulSlash(sp, origin.add(dir.scale(1.2)), dir, 14.0, 3.35 * scaleBoost, fastU, false, 248, true);
            if (boosted) {
                emitSoulSlash(sp, origin.add(dir.scale(1.35)).add(0, 0.22, 0), dir, -24.0, 3.65, Math.min(0.999, fastU * 1.08), true, 225, true);
            }
        }

        // Iconic DNF 噬灵鬼斩: Ferocious horned phantom demon-beast head lunging forward & biting shut!
        double lungeDist = 0.95 + (boosted ? 3.25 : 2.55) * EffectSprites.easeOutCubic(Math.min(1.0, u * 1.35));
        Vec3 headPos = origin.add(dir.scale(lungeDist)).add(0, 0.15 + 0.18 * Math.sin(u * Math.PI), 0);
        double headScale = 1.85 * scaleBoost * (0.72 + 0.36 * Math.sin(Math.min(1.0, u * 1.2) * Math.PI * 0.72));
        int frame = Math.clamp((int) (u * 8), 0, 7);
        int headAlpha = (int) Math.round(252 * (1.0 - 0.30 * u * u));
        emitForwardBeastHead(sp, TEX_DEVOUR_HEAD, frame, headPos, dir, right, headScale, headAlpha);

        // Devouring bite crunch flash & shockwave right as jaws snap shut (frames 4-5 -> u ~ 0.42..0.72)
        if (u >= 0.38 && u < 0.84) {
            double bu = (u - 0.38) / 0.46;
            Vec3 bitePt = headPos.add(dir.scale(headScale * 0.45));
            emitGhostFlash(sp, bitePt, camera, 2.35 * scaleBoost, bu, 248);
            emitKallaExplosion(sp, bitePt, camera, 2.15 * scaleBoost, bu, false, 225);
        }

        // Ground dark-violet shockwave & own-field resonance burst!
        Vec3 ground = new Vec3(origin.x, origin.y - 0.95, origin.z).add(dir.scale(1.85));
        EffectSprites.shockwave(sp, ground, 3.4 * scaleBoost * EffectSprites.easeOutCubic(u), u * 1.7,
            boosted ? COL_GHOST_CYAN : COL_VIOLET, (int) (225 * fade));
        if (boosted) {
            emitFieldSigil(sp, TEX_FIELD_SIGIL, ground.add(0, 0.02, 0), 3.65 * (0.55 + 0.55 * EffectSprites.easeOutCubic(u)),
                now * 0.005, COL_ABYSS_DARK, COL_VIOLET, (int) (215 * fade));
            // Twin flanking dark-flame plumes & crackling jaws lightning when boosted by own Ghost Field!
            for (int sign : new int[]{-1, 1}) {
                Vec3 fp = ground.add(right.scale(sign * 1.45)).add(dir.scale(0.65));
                emitAbyssPillar(sp, fp, 0.72, 3.45, u, sign * 0.6, 225);
            }
            if (u < 0.75) {
                LightningBolt.bolt(sp, origin.add(dir.scale(0.6)), headPos.add(dir.scale(0.9)), camera,
                    t.start.seed() ^ (now / 45L), 6, 0.34, 0.062, COL_GHOST_CYAN, (int) (230 * fade), false);
            }
        }
    }

    // --- 4. 死亡墓碑 (Tombstone: Sky Rift + Angled Plunge + Planted Shatter) --

    private static void renderTombstone(SkillVfx.Track t, long age, long now, List<SpriteQuad> sp, Vec3 camera) {
        if (t.start.sweep() && !t.start.dual()) return;
        Vec3 center = vector(t.start.origin());
        long total = Math.max(2000L, t.start.duration());
        double fieldEnv = Math.clamp(age / 260.0, 0.0, 1.0) * (age > total ? Math.clamp(1.0 - (age - total) / 650.0, 0.0, 1.0) : 1.0);
        if (fieldEnv <= 0.01) return;

        // 1. Ominous overhead netherworld cloud rift (y + 5.1) + cursed 4-block ground drop-zone seal
        Vec3 skyCenter = center.add(0, 5.1, 0);
        EffectSprites.emitGround(sp, TEX_BLACHE_SWAMP, Blend.TRANSLUCENT, skyCenter, 4.45 * (0.65 + 0.35 * fieldEnv),
            -now * 0.0012, 0f, 0f, 1f, 1f, COL_WHITE, (int) (185 * fieldEnv));
        emitFieldSigil(sp, TEX_FIELD_SIGIL, skyCenter.subtract(0, 0.04, 0), 4.25, -now * 0.0022,
            COL_ABYSS_DARK, COL_VIOLET, (int) (195 * fieldEnv));
        emitFieldSigil(sp, TEX_FIELD_SIGIL, center.add(0, 0.045, 0), 4.15, now * 0.0014,
            COL_ABYSS_DARK, COL_VIOLET, (int) (185 * fieldEnv));
        emitFieldSigil(sp, TEX_FIELD_INNER, center.add(0, 0.055, 0), 3.15, -now * 0.0020,
            COL_ABYSS_DARK, COL_LAVENDER, (int) (145 * fieldEnv));

        // 2. Deterministic gothic rune tombstones plunging at a slight slant -> impaling ground -> trembling -> shattering!
        SplittableRandom rng = new SplittableRandom(t.start.seed());
        int count = Math.clamp((int) Math.round(24.0 * Math.max(1.0, t.start.horizontalScale())), 24, 56);
        for (int i = 0; i < count; i++) {
            double ang = rng.nextDouble() * Math.TAU;
            double dist = Math.sqrt(rng.nextDouble()) * 3.72;
            double yaw = rng.nextDouble() * Math.PI;
            double slantRad = Math.toRadians(10.0 + rng.nextDouble() * 9.0);
            double slantYaw = rng.nextDouble() * Math.TAU;
            double scale = 0.88 + rng.nextDouble() * 0.36;

            // Spread launches smoothly across the 10-round 2000ms channel (each stone lives 760ms!)
            long startAt = (long) ((i / (double) count) * (total - 120L));
            long localAge = age - startAt;
            if (localAge < 0L || localAge >= 760L) continue;

            Vec3 impactPos = center.add(Math.cos(ang) * dist, 0.02, Math.sin(ang) * dist);
            Vec3 tiltDir = new Vec3(Math.cos(slantYaw), 0, Math.sin(slantYaw));
            // Axis pointing UP along the slanted monolith (slightly tilted off vertical!)
            Vec3 stoneAxis = UP.scale(Math.cos(slantRad)).add(tiltDir.scale(Math.sin(slantRad))).normalize();
            double halfW = 0.56 * scale;
            double height = 2.05 * scale;

            if (localAge < 180L) {
                // Phase A: High-speed slanted descent from overhead nether rift!
                double fallU = localAge / 180.0;
                double dropH = 5.15 * (1.0 - fallU * fallU);
                Vec3 basePos = impactPos.add(stoneAxis.scale(dropH));
                emitCrossedTombstone(sp, basePos, stoneAxis, yaw, halfW, height, 0, (int) (235 * Math.min(1.0, fallU * 3.0 + 0.3)));
                // Falling dark-violet speed wake
                EffectSprites.glow(sp, basePos.add(stoneAxis.scale(height * 0.65)), camera, 0.72 * scale, COL_VIOLET, 165);
            } else if (localAge < 530L) {
                // Phase B: Impaled into ground, trembling ominously for ~0.35s with pulsing cursed runes!
                long impAge = localAge - 180L;
                double impU = impAge / 350.0;
                double tremor = (1.0 - impU) * 0.032 * Math.sin(impAge * 0.14 + i);
                Vec3 basePos = impactPos.add(tiltDir.scale(tremor)).subtract(stoneAxis.scale(0.16));
                int frame = 1 + Math.clamp((int) (impU * 2.99), 0, 2);
                emitCrossedTombstone(sp, basePos, stoneAxis, yaw, halfW, height, frame, 248);

                // Crisp ground shockwave, dark mist & brief low soul-plume on slam!
                if (impAge < 260L) {
                    double swU = impAge / 260.0;
                    double swFade = 1.0 - swU;
                    EffectSprites.shockwave(sp, impactPos.add(0, 0.06, 0), (1.35 * scale) * EffectSprites.easeOutCubic(swU),
                        i + swU * 1.4, COL_LAVENDER, (int) (225 * swFade));
                    emitGhostMist(sp, impactPos.add(0, 0.35, 0), camera, 0.95 * scale, now + i * 77L, (int) (170 * swFade), i);
                    emitAbyssPillar(sp, impactPos, 0.42 * scale, 1.55 * scale, swU, yaw, (int) (195 * swFade));
                }
            } else {
                // Phase C: Fracturing along violet seams & shattering into stone shards + ghost dust!
                double shU = (localAge - 530L) / 230.0;
                int frame = 4 + Math.clamp((int) (shU * 3.99), 0, 3);
                Vec3 basePos = impactPos.subtract(stoneAxis.scale(0.16));
                emitCrossedTombstone(sp, basePos, stoneAxis, yaw, halfW * (1.0 + 0.14 * shU), height * (1.0 + 0.08 * shU), frame, (int) (242 * (1.0 - 0.55 * shU)));
                emitGhostMist(sp, impactPos.add(0, 0.65 + shU * 0.4, 0), camera, 1.05 * scale, now + i * 91L, (int) (155 * (1.0 - shU)), i);
            }
        }
    }

    // --- 5. 鬼影闪 (Phantom Slash: Dash Hairline -> Delayed Dimensional Rift Detonation, multi-segment aware!) ---

    private static void renderPhantomSlash(SkillVfx.Track t, long age, long now, List<SpriteQuad> sp, Vec3 camera) {
        List<SkillVisualEvent> segs = t.flashes.stream().filter(f -> f.phase() == SEGMENT).toList();
        if (segs.isEmpty()) {
            renderPhantomSegment(t, t.start, age, now, sp, camera, 0);
            return;
        }
        for (int i = 0; i < segs.size(); i++) {
            SkillVisualEvent seg = segs.get(i);
            long segAge = Math.max(0L, (long) ((now - seg.at()) * t.speed));
            renderPhantomSegment(t, seg, segAge, now, sp, camera, i);
        }
    }

    private static void renderPhantomSegment(SkillVfx.Track t, SkillVisualEvent seg, long segAge, long now, List<SpriteQuad> sp, Vec3 camera, int segIndex) {
        if (segAge >= 920L) return;
        double scale = Math.max(1.0, seg.horizontalScale());
        Vec3 from = vector(seg.origin());
        Vec3 dir = horizontalDir(vector(seg.direction()));
        double expectedDist = 8.0 * scale;
        Vec3 currentEnd = (seg == t.last || seg == t.start) ? vector(t.last.position()) : from.add(dir.scale(expectedDist));
        Vec3 to = from.distanceToSqr(currentEnd) > 0.25 && from.distanceToSqr(currentEnd) <= (expectedDist + 2.5) * (expectedDist + 2.5)
            ? currentEnd
            : from.add(dir.scale(Math.min(expectedDist, segAge / 250.0 * expectedDist)));
        Vec3 lineDelta = to.subtract(from);
        double dist = Math.max(0.6, lineDelta.length());
        Vec3 lineDir = lineDelta.scale(1.0 / dist);
        Vec3 mid = from.lerp(to, 0.5).add(0, 1.12, 0);

        // Departure dark ghost shroud & shockwave at origin
        if (segAge < 420L) {
            double ou = segAge / 420.0;
            EffectSprites.shockwave(sp, from.add(0, 0.09, 0), 2.35 * scale * EffectSprites.easeOutCubic(ou), ou * 1.5, COL_VIOLET, (int) (220 * (1.0 - ou)));
            emitGhostMist(sp, from.add(0, 1.05, 0), camera, 1.55 * scale, now, (int) (175 * (1.0 - ou)), segIndex);
        }

        if (segAge < 275L) {
            // Phase A (0..275ms): Razor-thin blinding hairline space-cut tracing the high-speed invincible dash!
            double dashU = Math.clamp(segAge / 275.0, 0.0, 1.0);
            int frame = segAge < 140L ? 0 : 1;
            emitPhantomRift(sp, mid, lineDir, dist * 0.54, 0.72 * scale, 12.0, frame, 245);
            emitPhantomRift(sp, mid, lineDir, dist * 0.52, 0.62 * scale, -74.0, frame, 185);
            RibbonTrail.push("phantom:" + t.start.cast() + ":" + segIndex, to.add(0, 1.08, 0), 0.26 * scale, COL_GHOST_CYAN, 235, 240L, now);
            EffectSprites.glow(sp, to.add(0, 1.12, 0), camera, 1.15 * scale * (0.65 + 0.35 * dashU), COL_GHOST_CYAN, 235);
            return;
        }

        // Phase B (275..900ms): Iconic DNF DELAYED DIMENSIONAL RIFT BURST across the entire dash path!
        double burstU = Math.clamp((segAge - 275L) / 625.0, 0.0, 0.999);
        double fade = 1.0 - burstU;
        int riftFrame = 2 + Math.clamp((int) (burstU * 5.99), 0, 5);
        double halfH = 1.55 * scale * (0.85 + 0.30 * Math.sin(burstU * Math.PI * 0.75));

        emitPhantomRift(sp, mid, lineDir, dist * 0.56, halfH, 20.0, riftFrame, (int) (255 * Math.min(1.0, fade * 1.45)));
        emitPhantomRift(sp, mid.add(0, 0.05, 0), lineDir, dist * 0.54, halfH * 0.86, -42.0, riftFrame, (int) (225 * Math.min(1.0, fade * 1.40)));

        if (burstU < 0.82) {
            LightningBolt.bolt(sp, from.add(0, 1.12, 0), to.add(0, 1.12, 0), camera,
                (t.start.seed() + segIndex * 131L) ^ (now / 42L), 10, 0.45 * scale, 0.072 * fade + 0.02, COL_GHOST_CYAN, (int) (245 * fade), true);
            LightningBolt.bolt(sp, from.add(0, 1.05, 0), to.add(0, 1.20, 0), camera,
                (t.start.seed() + 997L + segIndex * 131L) ^ (now / 52L), 8, 0.38 * scale, 0.055 * fade + 0.015, COL_VIOLET, (int) (215 * fade), false);
        }

        for (int k = 0; k < 3; k++) {
            double frac = 0.22 + k * 0.30;
            double localU = Math.clamp((burstU - k * 0.07) / 0.72, 0.0, 0.999);
            if (burstU >= k * 0.07 && localU < 0.99) {
                Vec3 fPt = from.lerp(to, frac).add(0, 1.14, 0);
                emitGhostFlash(sp, fPt, camera, (2.35 + (k == 1 ? 0.55 : 0.0)) * scale, localU, 250);
            }
        }
        if (burstU < 0.65) {
            emitSoulSlash(sp, to.add(0, 1.12, 0), lineDir, 18.0, 3.15 * scale, burstU / 0.65, false, 240, true);
        }
        EffectSprites.shockwave(sp, to.add(0, 0.10, 0), 3.2 * scale * EffectSprites.easeOutCubic(burstU), burstU * 1.8, COL_LAVENDER, (int) (220 * fade));
    }

    // --- 8. 满月斩 (Moonlight Slash: Alternating Dark-Moon Crescents + 360° Full Moon Eclipse Spin!) ---

    private static void renderMoonlightSlash(SkillVfx.Track t, long age, long now, List<SpriteQuad> sp, Vec3 camera) {
        List<SkillVisualEvent> segs = t.flashes.stream().filter(f -> f.phase() == SEGMENT).toList();
        if (segs.isEmpty()) {
            renderMoonlightSegment(t, t.start, age, now, sp, camera, 1);
            return;
        }
        for (int i = 0; i < segs.size(); i++) {
            SkillVisualEvent seg = segs.get(i);
            long localAge = Math.max(0L, (long) ((now - seg.at()) * t.speed));
            int segNum = Math.max(1, (int) Math.round(seg.charge() > 0 ? seg.charge() * 6.0 : (i + 1)));
            renderMoonlightSegment(t, seg, localAge, now, sp, camera, segNum);
        }
    }

    private static void renderMoonlightSegment(SkillVfx.Track t, SkillVisualEvent seg, long localAge, long now, List<SpriteQuad> sp, Vec3 camera, int segNum) {
        if (localAge >= 480L) return;
        boolean fullMoon = seg.sweep() || (segNum % 3 == 0);
        double dur = fullMoon ? 480.0 : 380.0;
        if (localAge >= dur) return;
        double u = Math.clamp(localAge / dur, 0.0, 0.999);
        double fade = 1.0 - u;
        Vec3 dir = horizontalDir(vector(seg.direction()));
        Vec3 pos = vector(seg.position());
        boolean flip = (segNum % 2 == 0);

        if (fullMoon) {
            // 3rd / 6th segment: Colossal 4.2-block 360° Full Moonlight Eclipse Spin!
            double ringR = 4.25 * (0.48 + 0.56 * EffectSprites.easeOutCubic(u));
            int a = (int) Math.round(252 * Math.min(1.0, fade * 1.35));
            EffectSprites.emitGround(sp, TEX_FULL_MOON, Blend.TRANSLUCENT, pos.add(0, 0.08, 0), ringR, now * 0.0085, 0f, 0f, 1f, 1f, COL_WHITE, a);
            EffectSprites.emitGround(sp, TEX_FULL_MOON, Blend.ADDITIVE, pos.add(0, 0.10, 0), ringR * 1.03, -now * 0.011, 0f, 0f, 1f, 1f, COL_LAVENDER, (int) (a * 0.85));
            EffectSprites.emitGround(sp, TEX_FULL_MOON, Blend.ADDITIVE, pos.add(0, 1.08, 0), ringR * 0.96, now * 0.014, 0f, 0f, 1f, 1f, COL_GHOST_CYAN, (int) (a * 0.72));
            // Twin opposing spinning lunar crescents around the caster
            Vec3 center = pos.add(0, 1.12, 0);
            emitMoonArc(sp, center, dir, 12.0, 4.15, u, false, 248);
            emitMoonArc(sp, center.add(0, 0.14, 0), dir.scale(-1.0), -14.0, 4.05, Math.min(0.999, u * 1.06), true, 232);
            EffectSprites.shockwave(sp, pos.add(0, 0.12, 0), 4.45 * EffectSprites.easeOutCubic(u), u * 2.2, COL_GHOST_CYAN, (int) (235 * fade));
            if (u < 0.65) {
                emitGhostFlash(sp, center.add(dir.scale(1.4)), camera, 2.25, u / 0.65, 240);
            }
        } else {
            // 1st / 2nd / 4th / 5th segment: Crisp diagonal advancing dark-moon crescent slash!
            double roll = flip ? -34.0 : 32.0;
            Vec3 center = pos.add(dir.scale(1.35)).add(0, 1.15, 0);
            emitMoonArc(sp, center, dir, roll, 3.35, u, flip, 248);
            emitSoulSlash(sp, center.add(0, -0.06, 0), dir, roll * 0.75, 2.95, Math.min(0.999, u * 1.05), flip, 185, false);
            EffectSprites.shockwave(sp, pos.add(dir.scale(1.2)).add(0, 0.08, 0), 2.8 * EffectSprites.easeOutCubic(u), u * 1.5, COL_LAVENDER, (int) (185 * fade));
            emitGhostMist(sp, center.add(dir.scale(0.8)), camera, 1.35, now + segNum * 90L, (int) (125 * fade), segNum);
        }
    }

    // --- 9. 鬼斩·炼狱 (SB_HELL_WAVE: Piercing 10-Block Demonic Soul Wave) ---

    private static void renderHellWave(SkillVfx.Track t, long age, long now, List<SpriteQuad> sp, Vec3 camera) {
        long dur = Math.max(500L, t.start.duration());
        if (age >= dur) return;
        double u = Math.clamp(age / (double) dur, 0.0, 0.999);
        double fade = age > dur - 260L ? Math.clamp((dur - age) / 260.0, 0.0, 1.0) : 1.0;
        Vec3 dir = horizontalDir(vector(t.start.direction()));
        Vec3 right = rightOf(dir);
        Vec3 pos = t.motion != null ? vector(t.last.position()) : vector(t.start.origin()).add(dir.scale(Math.min(10.0, age * 0.016)));
        int headFrame = (int) Math.floorMod(age / 70L, 8L);

        // Lunging demonic beast head spearheading twin colossal dark-violet crescents!
        emitForwardBeastHead(sp, TEX_DEVOUR_HEAD, headFrame, pos, dir, right, 1.75, (int) (245 * fade));
        emitSoulSlash(sp, pos.subtract(dir.scale(0.35)), dir, 18.0, 2.85, (u * 2.2) % 0.999, false, (int) (238 * fade), true);
        emitSoulSlash(sp, pos.subtract(dir.scale(0.35)).add(0, 0.12, 0), dir, -24.0, 2.65, ((u * 2.2) + 0.25) % 0.999, true, (int) (215 * fade), true);
        emitGhostMist(sp, pos.subtract(dir.scale(0.8)), camera, 1.65, now, (int) (155 * fade), 0);
        EffectSprites.glow(sp, pos.add(dir.scale(0.6)), camera, 1.35, COL_GHOST_CYAN, (int) (215 * fade));
        RibbonTrail.push("sb_hell:" + t.start.cast(), pos, 0.42, COL_VIOLET, (int) (225 * fade), 240L, now);
        if (fade > 0.2) {
            LightningBolt.bolt(sp, pos.subtract(dir.scale(1.6)), pos.add(dir.scale(0.9)), camera,
                t.start.seed() ^ (now / 45L), 6, 0.36, 0.06, COL_GHOST_CYAN, (int) (220 * fade), false);
        }
    }

    // --- 10. 圆月剑气 (SB_MOON_WAVE: Piercing 10-Block Lunar Crescent Wave) ---

    private static void renderMoonWave(SkillVfx.Track t, long age, long now, List<SpriteQuad> sp, Vec3 camera) {
        long dur = Math.max(450L, t.start.duration());
        if (age >= dur) return;
        double u = Math.clamp(age / (double) dur, 0.0, 0.999);
        double fade = age > dur - 220L ? Math.clamp((dur - age) / 220.0, 0.0, 1.0) : 1.0;
        boolean fullMoon = t.start.sweep();
        double scale = Math.max(0.85, t.start.horizontalScale());
        Vec3 dir = horizontalDir(vector(t.start.direction()));
        Vec3 pos = t.motion != null ? vector(t.last.position()) : vector(t.start.origin()).add(dir.scale(Math.min(10.0, age * 0.018)));
        double arcP = 0.28 + ((age % 320L) / 320.0) * 0.44;

        emitMoonArc(sp, pos, dir, fullMoon ? 8.0 : 22.0, (fullMoon ? 2.55 : 1.95) * scale, arcP, false, (int) (245 * fade));
        if (fullMoon) {
            emitMoonArc(sp, pos.add(0, 0.08, 0), dir, -14.0, 2.40 * scale, Math.min(0.999, arcP + 0.12), true, (int) (220 * fade));
            EffectSprites.emitGround(sp, TEX_FULL_MOON, Blend.ADDITIVE, pos, 2.25 * scale, now * 0.015, 0f, 0f, 1f, 1f, COL_GHOST_CYAN, (int) (185 * fade));
        }
        EffectSprites.glow(sp, pos.add(dir.scale(0.5)), camera, (fullMoon ? 1.15 : 0.85) * scale, COL_GHOST_CYAN, (int) (200 * fade));
        RibbonTrail.push("sb_moon_wave:" + t.start.cast(), pos, fullMoon ? 0.34 : 0.22, COL_LAVENDER, (int) (210 * fade), 200L, now);
    }

    // --- 11. 鬼影步反击 (SB_GHOST_STEP: 4-Block Evasion Shadow Counter-Slash) ---

    private static void renderGhostStep(SkillVfx.Track t, long age, long now, List<SpriteQuad> sp, Vec3 camera) {
        if (age >= 580L) return;
        double u = Math.clamp(age / 580.0, 0.0, 0.999);
        double fade = 1.0 - u;
        double rScale = Math.max(0.6, t.start.horizontalScale());
        Vec3 pos = vector(t.start.origin());
        Vec3 dir = horizontalDir(vector(t.start.direction()));
        double ringR = 4.1 * rScale * (0.45 + 0.58 * EffectSprites.easeOutCubic(u));

        EffectSprites.emitGround(sp, TEX_FULL_MOON, Blend.TRANSLUCENT, pos.add(0, 0.07, 0), ringR, now * 0.010, 0f, 0f, 1f, 1f, COL_WHITE, (int) (235 * fade));
        EffectSprites.emitGround(sp, TEX_FULL_MOON, Blend.ADDITIVE, pos.add(0, 0.09, 0), ringR * 1.02, -now * 0.012, 0f, 0f, 1f, 1f, COL_VIOLET, (int) (210 * fade));
        emitSoulSlash(sp, pos.add(0, 1.08, 0), dir, 14.0, 4.0 * rScale, u, false, 245, true);
        emitSoulSlash(sp, pos.add(0, 1.16, 0), dir.scale(-1.0), -18.0, 3.8 * rScale, Math.min(0.999, u * 1.08), true, 225, true);
        EffectSprites.shockwave(sp, pos.add(0, 0.12, 0), 4.3 * rScale * EffectSprites.easeOutCubic(u), u * 2.0, COL_GHOST_CYAN, (int) (230 * fade));
        if (u < 0.65) {
            emitGhostFlash(sp, pos.add(0, 1.10, 0), camera, 2.4 * rScale, u / 0.65, 240);
        }
    }

    // --- 12. 次元断空裂隙 (SB_PHANTOM_RIFT: 0.6s Mark -> 3.5-Block Dimensional Detonation!) ---

    private static void renderPhantomRiftBurst(SkillVfx.Track t, long age, long now, List<SpriteQuad> sp, Vec3 camera) {
        double rScale = Math.max(0.5, t.start.horizontalScale());
        Vec3 dir = horizontalDir(vector(t.start.direction()));
        if (t.last.phase() == FINISH) {
            long finAge = Math.max(0L, now - t.last.at());
            if (finAge >= 650L) return;
            double u = Math.clamp(finAge / 650.0, 0.0, 0.999);
            double fade = 1.0 - u;
            Vec3 pos = vector(t.last.position());
            Vec3 center = pos.add(0, 1.10, 0);
            int frame = 2 + Math.clamp((int) (u * 5.99), 0, 5);
            double halfLen = 3.55 * rScale * (0.65 + 0.40 * EffectSprites.easeOutCubic(u));

            emitPhantomRift(sp, center, dir, halfLen, 1.65 * rScale, 18.0, frame, (int) (252 * Math.min(1.0, fade * 1.4)));
            emitPhantomRift(sp, center, rightOf(dir), halfLen * 0.94, 1.55 * rScale, -32.0, frame, (int) (235 * Math.min(1.0, fade * 1.35)));
            emitKallaExplosion(sp, center, camera, 3.45 * rScale, u, false, 238);
            emitGhostFlash(sp, center, camera, 3.25 * rScale, u, 248);
            EffectSprites.shockwave(sp, pos.add(0, 0.10, 0), 3.65 * rScale * EffectSprites.easeOutCubic(u), u * 2.1, COL_GHOST_CYAN, (int) (240 * fade));
            if (u < 0.75) {
                for (int i = 0; i < 3; i++) {
                    double a = i * (Math.TAU / 3.0) + u * 1.2;
                    Vec3 bEnd = center.add(Math.cos(a) * 3.2 * rScale, 0.25, Math.sin(a) * 3.2 * rScale);
                    LightningBolt.bolt(sp, center, bEnd, camera, (t.start.seed() + i * 73L) ^ (now / 40L), 6, 0.36, 0.055 * fade + 0.015, COL_GHOST_CYAN, (int) (225 * fade), false);
                }
            }
            return;
        }
        // Waiting mark (0..600ms): ominous pulsing dimensional hairline scar + contracting dark seal!
        double mu = Math.clamp(age / 600.0, 0.0, 0.999);
        Vec3 pos = vector(t.last.position());
        Vec3 center = pos.add(0, 1.10, 0);
        int markFrame = mu < 0.5 ? 0 : 1;
        emitPhantomRift(sp, center, dir, 1.85 * rScale * (0.75 + 0.35 * mu), 0.72 * rScale, 38.0, markFrame, 235);
        emitPhantomRift(sp, center, rightOf(dir), 1.65 * rScale * (0.75 + 0.35 * mu), 0.65 * rScale, -38.0, markFrame, 210);
        emitFieldSigil(sp, TEX_FIELD_INNER, pos.add(0, 0.06, 0), (3.55 - 1.65 * mu) * rScale, now * 0.008, COL_ABYSS_DARK, COL_GHOST_CYAN, 190);
        EffectSprites.glow(sp, center, camera, 0.95 + 0.45 * mu, COL_GHOST_CYAN, 210);
    }

    private static void emitMoonArc(
        List<SpriteQuad> out, Vec3 center, Vec3 forward, double rollDeg,
        double radius, double progress, boolean mirror, int alpha
    ) {
        if (progress < 0.0 || progress >= 1.0 || radius <= 0.0 || alpha <= 4) return;
        Vec3 dir = horizontalDir(forward);
        Vec3 right0 = rightOf(dir);
        double roll = Math.toRadians(rollDeg);
        Vec3 side = right0.scale(Math.cos(roll)).add(UP.scale(Math.sin(roll))).normalize();
        if (mirror) side = side.scale(-1.0);

        double scale = radius * (0.86 + 0.24 * EffectSprites.easeOutCubic(progress));
        int frame = Math.clamp((int) (progress * 8), 0, 7);
        float u0 = frame / 8.0f, u1 = (frame + 1) / 8.0f;
        int a = Math.clamp((int) Math.round(alpha * (1.0 - 0.32 * progress * progress)), 0, 255);

        Vec3 halfU = dir.scale(scale);
        Vec3 halfV = side.scale(scale);
        EffectSprites.emitPlane(out, TEX_MOON_ARC, Blend.TRANSLUCENT, center, halfU.scale(1.04), halfV.scale(1.04), u0, 0f, u1, 1f, COL_WHITE, a);
        EffectSprites.emitPlane(out, TEX_MOON_ARC, Blend.ADDITIVE, center, halfU, halfV, u0, 0f, u1, 1f, COL_GHOST_CYAN, (int) (a * 0.82));
    }

    // --- 6. 冥炎之卡洛 (Kalla Activation / 2-Block Exploding Bolt / Triple Dark-Flame Finisher) ---

    private static void renderKalla(SkillVfx.Track t, long age, long now, List<SpriteQuad> sp, Vec3 camera) {
        // Case A: Flying or Exploding Kalla Dark-Flame Projectile
        if (t.motion != null) {
            Vec3 pos = vector(t.last.position());
            if (t.last.phase() == FINISH) {
                long finAge = Math.max(0L, now - t.last.at());
                if (t.last.sweep()) {
                    // IMPACTED ENEMY OR TERRAIN -> Crisp 2-block dark-flame AoE explosion!
                    if (finAge >= 540L) return;
                    double u = Math.clamp(finAge / 540.0, 0.0, 0.999);
                    double fade = 1.0 - u;
                    double blastScale = Math.max(0.5, t.last.horizontalScale());
                    double blastR = 2.05 * blastScale;

                    emitKallaExplosion(sp, pos, camera, blastR, u, false, 252);
                    emitKallaExplosion(sp, pos.add(0, 0.06, 0), camera, blastR * 0.96, u, true, 225);
                    EffectSprites.shockwave(sp, pos.add(0, 0.08, 0), blastR * 1.12 * EffectSprites.easeOutCubic(u),
                        u * 1.8, COL_KALLA_FLAME, (int) (238 * fade));
                    if (finAge < 320L) {
                        double fu = finAge / 320.0;
                        emitGhostFlash(sp, pos, camera, blastR * 0.92, fu, 242);
                        emitAbyssPillar(sp, pos.subtract(0, 0.35, 0), 0.58 * blastScale, 2.25 * blastScale, fu, t.start.seed() * 0.01, (int) (215 * (1.0 - fu)));
                    }
                    emitGhostMist(sp, pos.add(0, 0.25, 0), camera, 1.35 * blastScale, now, (int) (165 * fade), 1);
                } else {
                    // Flew max 10 blocks in open air without hitting anything -> soft smoke fizzle
                    if (finAge >= 220L) return;
                    double u = finAge / 220.0;
                    double fade = 1.0 - u;
                    emitGhostMist(sp, pos, camera, 0.65 * (0.7 + 0.5 * u), now, (int) (145 * fade), 0);
                    EffectSprites.glow(sp, pos, camera, 0.45 * fade, COL_KALLA_FLAME, (int) (160 * fade));
                }
                return;
            }
            // In-flight swirling Kalla dark-flame fireball + thick dark-violet flame ribbon!
            int fFrame = (int) Math.floorMod(now / 52L, 8L);
            emitFramedBillboard(sp, TEX_KALLA_FIREBALL, 8, fFrame, pos, camera, 0.52, 248, true, COL_KALLA_FLAME, 0.78);
            EffectSprites.glow(sp, pos, camera, 0.72, COL_LAVENDER, 195);
            RibbonTrail.push("kalla_bolt:" + t.start.cast(), pos, 0.22, COL_KALLA_FLAME, 225, 220L, now);
            return;
        }

        // Case B: Kalla Finisher Triple Dark-Flame Sword (terminal / sweep == true)
        if (t.start.sweep()) {
            int slashIdx = 0;
            for (var ev : t.flashes) {
                if (ev.phase() != HIT) continue;
                int idx = slashIdx++;
                double u = (now - ev.at()) * t.speed / 440.0;
                if (u < 0.0 || u >= 1.0) continue;
                double fade = 1.0 - u;
                Vec3 dir = horizontalDir(vector(ev.direction()));
                Vec3 origin = vector(ev.position());
                Vec3 c = origin.add(dir.scale(1.65 + 1.25 * EffectSprites.easeOutCubic(u))).add(0, 1.15, 0);

                boolean third = (idx % 3) == 2;
                if (!third) {
                    boolean second = (idx % 3) == 1;
                    double roll = second ? 38.0 : -38.0;
                    emitSoulSlash(sp, c, dir, roll, 3.65, u, second, 252, true);
                    emitSoulSlash(sp, c.add(dir.scale(0.35)), dir, roll * 0.72, 3.25, Math.min(0.999, u * 1.10), second, 210, false);
                    emitKallaExplosion(sp, c.add(dir.scale(1.1)), camera, 1.75, u, false, 205);
                } else {
                    // 3rd Hit: Colossal X-Cross + Horizontal Black-Flame Finisher Cleave!
                    emitSoulSlash(sp, c.add(dir.scale(0.35)), dir, 4.0, 4.65, u, false, 255, true);
                    emitSoulSlash(sp, c.add(dir.scale(0.45)), dir, 86.0, 4.25, Math.min(0.999, u * 1.06), true, 245, true);
                    emitKallaExplosion(sp, origin.add(dir.scale(3.1)).add(0, 1.10, 0), camera, 3.15, u, false, 248);
                    emitGhostFlash(sp, origin.add(dir.scale(2.8)).add(0, 1.15, 0), camera, 2.85, u, 250);
                    emitAbyssPillar(sp, origin.add(dir.scale(3.2)).add(0, 0.05, 0), 0.95, 4.15, u, idx, 235);
                    EffectSprites.shockwave(sp, origin.add(dir.scale(2.6)).add(0, 0.10, 0), 4.4 * EffectSprites.easeOutCubic(u),
                        u * 1.8, COL_KALLA_FLAME, (int) (235 * fade));
                }
            }
            return;
        }

        // Case C: Kalla Activation Burst (0..550ms)
        if (age >= 580L) return;
        double u = Math.clamp(age / 580.0, 0.0, 0.999);
        double fade = 1.0 - u;
        Vec3 p = liveCasterPos(t.start.caster(), vector(t.start.origin()));
        boolean ownFp = isOwnFirstPerson(t.start.caster());

        emitFieldSigil(sp, TEX_FIELD_SIGIL, p.add(0, 0.05, 0), 2.75 * (0.45 + 0.65 * EffectSprites.easeOutCubic(u)),
            now * 0.004, COL_ABYSS_DARK, COL_KALLA_FLAME, (int) (225 * fade));
        EffectSprites.shockwave(sp, p.add(0, 0.10, 0), 3.3 * EffectSprites.easeOutCubic(u), u * 1.7, COL_KALLA_FLAME, (int) (230 * fade));
        if (!ownFp) {
            emitKallaExplosion(sp, p.add(0, 1.15, 0), camera, 2.65, u, false, 230);
        }
        for (int i = 0; i < 6; i++) {
            double a = i * (Math.TAU / 6.0) + u * 0.6;
            double r = 0.85 + 1.05 * EffectSprites.easeOutCubic(u);
            emitAbyssPillar(sp, p.add(Math.cos(a) * r, 0.04, Math.sin(a) * r), 0.55, 2.75, u, a, (int) (210 * (ownFp ? 0.55 : 1.0)));
        }
    }

    // --- 7. 第七鬼神：邪神之怖拉修 (Blache: 8-Block Abyssal Swamp + Ghost Devour + Rising Demon Jaws!) ---

    private static void renderBlache(SkillVfx.Track t, long age, long now, List<SpriteQuad> sp, Vec3 camera) {
        if (age >= 2450L) return;
        Vec3 center = vector(t.start.origin());
        Vec3 dir = horizontalDir(vector(t.start.direction()));
        Vec3 right = rightOf(dir);

        int devouredGhosts = Math.clamp((int) Math.round(t.start.charge() * 4.0), 0, 4);
        DevouredField localDevour = BLACHE_DEVOURS.get(t.start.cast());
        if (localDevour != null && devouredGhosts == 0) {
            devouredGhosts = localDevour.ghosts();
        }
        double power = 1.0 + devouredGhosts * 0.095;

        // Global swamp open (0..380ms) & dissolve (1950..2450ms) envelope
        double swampOpen = EffectSprites.easeOutCubic(Math.clamp(age / 380.0, 0.0, 1.0));
        double swampClose = age > 1920L ? Math.clamp(1.0 - (age - 1920L) / 530.0, 0.0, 1.0) : 1.0;
        double swampEnv = swampOpen * swampClose;

        // 1. Colossal 8-Block Pitch-Black Abyssal Swamp + Submerged Forbidden Summoning Seals
        double swampR = 8.45 * (0.28 + 0.72 * swampOpen);
        EffectSprites.emitGround(sp, TEX_BLACHE_SWAMP, Blend.TRANSLUCENT, center.add(0, 0.035, 0),
            swampR, now * 0.00055, 0f, 0f, 1f, 1f, COL_WHITE, (int) (242 * swampEnv));
        EffectSprites.emitGround(sp, TEX_BLACHE_SWAMP, Blend.ADDITIVE, center.add(0, 0.048, 0),
            swampR * 0.98, -now * 0.00042, 0f, 0f, 1f, 1f, COL_VIOLET, (int) (135 * swampEnv));
        emitFieldSigil(sp, TEX_FIELD_SIGIL, center.add(0, 0.060, 0), swampR * 0.94, now * 0.00095,
            COL_ABYSS_DARK, COL_VIOLET, (int) (205 * swampEnv));
        emitFieldSigil(sp, TEX_FIELD_INNER, center.add(0, 0.070, 0), swampR * 0.62, -now * 0.0014,
            COL_ABYSS_DARK, COL_GHOST_CYAN, (int) (155 * swampEnv));

        // Rolling dark abyssal swamp mist around edges
        for (int i = 0; i < 6; i++) {
            double a = now * 0.0009 + i * (Math.TAU / 6.0);
            double r = 3.2 + 2.8 * Math.sin(now * 0.0012 + i * 1.4);
            Vec3 mp = center.add(Math.cos(a) * r, 0.55 + 0.4 * Math.sin(now * 0.002 + i), Math.sin(a) * r);
            emitGhostMist(sp, mp, camera, 2.25, now + i * 190L, (int) (155 * swampEnv), i);
        }

        // 2. Real Devoured Ghost Field Soul-Siphon (0..1150ms): Swallowed Red/Green/Ice/Purple spirits spiral into Blache's maw!
        if (devouredGhosts > 0 && age < 1180L) {
            double su = Math.clamp(age / 1120.0, 0.0, 1.0);
            double pull = su * su; // accelerating spiral vacuum into center!
            Vec3 srcCenter = localDevour != null ? localDevour.fieldCenter() : center;
            for (int i = 0; i < devouredGhosts; i++) {
                double baseAng = i * (Math.PI * 0.5) + Math.PI * 0.25;
                Vec3 startPt = srcCenter.add(Math.cos(baseAng) * 4.4, 1.4, Math.sin(baseAng) * 4.4);
                // Spiral twist inward into the center of Blache's swamp
                double swirlAng = baseAng + su * 4.6;
                Vec3 direct = startPt.lerp(center.add(0, 0.55, 0), pull);
                double swirlR = (1.0 - pull) * 2.4;
                Vec3 orbPos = direct.add(Math.cos(swirlAng) * swirlR, Math.sin(su * Math.PI) * 1.8, Math.sin(swirlAng) * swirlR);

                int alpha = (int) Math.round(235 * (1.0 - 0.35 * su));
                emitUprightFramed(sp, TEX_GHOST_SPIRITS, 4, i, orbPos, camera, 1.05 * (1.0 - 0.45 * su), 1.15 * (1.0 - 0.45 * su), alpha, true, GHOST_COLORS[i], 0.78);
                EffectSprites.glow(sp, orbPos, camera, 1.15 * (1.0 - 0.3 * su), GHOST_COLORS[i], alpha);
                RibbonTrail.push("blache_devour:" + t.start.cast() + ":" + i, orbPos, 0.30 * (1.0 - 0.35 * su), GHOST_COLORS[i], 235, 320L, now);
            }
            if (su > 0.72) {
                double conv = (su - 0.72) / 0.28;
                EffectSprites.glow(sp, center.add(0, 0.85, 0), camera, 2.8 * conv * power, COL_GHOST_CYAN, (int) (235 * conv));
            }
        }

        // 3. Channeling Phase (120..1260ms): 8 Clawing Abyssal Shadow Hands + Inward Suction Rings + Awakening Twin Eyes!
        if (age >= 100L && age < 1350L) {
            double hu = Math.clamp((age - 100L) / 1220.0, 0.0, 0.999);
            int handFrame = Math.clamp((int) (hu * 8), 0, 7);
            for (int i = 0; i < 8; i++) {
                double a = i * (Math.TAU / 8.0) + 0.25;
                double r = 4.6 + (i % 2) * 1.85;
                Vec3 hBase = center.add(Math.cos(a) * r, 0.04, Math.sin(a) * r);
                double hScale = (1.85 + (i % 3) * 0.42) * power;
                emitBlacheHand(sp, hBase, hScale * 0.52, hScale * 1.25, handFrame, a + Math.PI * 0.5, 225);
            }
            // Contracting inward suction waves
            double suction = 1.0 - ((age % 380L) / 380.0);
            EffectSprites.shockwave(sp, center.add(0, 0.12, 0), 8.2 * suction, -now * 0.004, COL_VIOLET, (int) (185 * suction));

            // Twin glowing eerie ghost-cyan predator eyes opening beneath the dark swamp right before eruption (620..1180ms)
            if (age >= 620L && age < 1180L) {
                double eu = Math.sin(Math.clamp((age - 620L) / 560.0, 0.0, 1.0) * Math.PI);
                Vec3 eyeCenter = center.add(dir.scale(1.6)).add(0, 1.15, 0);
                for (int sign : new int[]{-1, 1}) {
                    Vec3 ep = eyeCenter.add(right.scale(sign * 1.15));
                    EffectSprites.glow(sp, ep, camera, 1.35 * eu, COL_GHOST_CYAN, (int) (245 * eu));
                    emitGhostFlash(sp, ep, camera, 1.65 * eu, 0.35, (int) (235 * eu));
                }
            }
        }

        // 4. Emergence & Devouring Bite (1080..2400ms, FINISH hits at 1200ms!):
        //    Colossal Pixel-Art Evil God Blache Head surges out of the swamp & snaps jaws shut!
        if (age >= 1080L) {
            double eu = Math.clamp((age - 1080L) / 1320.0, 0.0, 0.999);
            double eFade = 1.0 - eu;
            int headFrame = Math.clamp((int) (eu * 8), 0, 7);

            // Rises rapidly out of the swamp (eu 0..0.28), lunges downward slightly on crunch bite (eu 0.35..0.55)!
            double riseCurve = eu < 0.30
                ? EffectSprites.easeOutCubic(eu / 0.30)
                : (eu < 0.55 ? 1.0 - 0.14 * Math.sin(((eu - 0.30) / 0.25) * Math.PI) : 1.0 - 0.35 * ((eu - 0.55) / 0.45));
            double headW = 3.55 * power;
            double headH = 3.75 * power;
            Vec3 headCenter = center.add(dir.scale(1.35)).add(0, -1.2 + (headH + 1.35) * riseCurve, 0);

            // Volumetric multi-angle towering Blache head: primary forward-facing plane + ±42° angled wings so it looks massive from ALL angles!
            emitBlacheHeadPlane(sp, headCenter, right, headW, headH, headFrame, 255);
            Vec3 sideA = right.scale(0.74).add(dir.scale(0.67)).normalize();
            Vec3 sideB = right.scale(0.74).subtract(dir.scale(0.67)).normalize();
            emitBlacheHeadPlane(sp, headCenter.add(dir.scale(-0.08)), sideA, headW * 0.94, headH * 0.96, headFrame, 215);
            emitBlacheHeadPlane(sp, headCenter.add(dir.scale(-0.08)), sideB, headW * 0.94, headH * 0.96, headFrame, 215);

            // Colossal 8-block devouring shockwaves & abyssal burst centered on FINISH (1200..2150ms)
            if (age >= 1180L) {
                double fu = Math.clamp((age - 1180L) / 1000.0, 0.0, 0.999);
                double ff = 1.0 - fu;
                EffectSprites.shockwave(sp, center.add(0, 0.14, 0), 8.8 * EffectSprites.easeOutCubic(fu), fu * 2.0, COL_VIOLET, (int) (252 * ff));
                EffectSprites.shockwave(sp, center.add(0, 1.45, 0), 7.2 * EffectSprites.easeOutCubic(fu), -fu * 1.6, COL_GHOST_CYAN, (int) (225 * ff));
                emitKallaExplosion(sp, headCenter.add(0, -0.35, 0), camera, 5.2 * power, fu, false, 238);

                // 6..10 soaring abyssal dark-flame pillars ringing the 8-block swamp (scales with devoured ghosts!)
                int pillars = 6 + devouredGhosts;
                for (int i = 0; i < pillars; i++) {
                    double a = i * (Math.TAU / pillars) + 0.35;
                    double r = 3.8 + (i % 2) * 2.6;
                    Vec3 pp = center.add(Math.cos(a) * r, 0.05, Math.sin(a) * r);
                    emitAbyssPillar(sp, pp, 1.15 * power, (5.4 + (i % 2) * 1.6) * power, fu, a, 245);
                }

                // Skyward crackling dark-violet & pale-cyan abyssal lightning bolts!
                if (fu < 0.78) {
                    int bolts = 3 + devouredGhosts;
                    for (int b = 0; b < bolts; b++) {
                        double a = b * (Math.TAU / bolts) + now * 0.002;
                        Vec3 bStart = center.add(Math.cos(a) * 5.2, 0.25, Math.sin(a) * 5.2);
                        Vec3 bEnd = headCenter.add(Math.cos(a + 0.5) * 1.8, 1.8, Math.sin(a + 0.5) * 1.8);
                        LightningBolt.bolt(sp, bStart, bEnd, camera, (now / 48L) ^ (b * 131L) ^ t.start.seed(),
                            7, 0.58, 0.088 * ff + 0.025, (b % 2 == 0) ? COL_GHOST_CYAN : COL_VIOLET, (int) (240 * ff), false);
                    }
                }
            }
            if (eFade > 0.1 && eu > 0.22 && eu < 0.62) {
                emitGhostFlash(sp, headCenter.add(0, -0.25, 0), camera, 4.6 * power, (eu - 0.22) / 0.40, 245);
            }
        }
    }

    // --- Blache 3rd-Person-Only Awakening Camera -----------------------------

    private static SkillVfx.CameraPose blacheCamera(SkillVfx.Track t, long time) {
        var mc = Minecraft.getInstance();
        if (t == null || mc == null || mc.level == null) return null;
        long age = Math.max(0L, time - t.start.startedAt());
        double u = Math.clamp(age / 2000.0, 0.0, 1.0);
        Vec3 origin = vector(t.start.origin());
        Vec3 dir = horizontalDir(vector(t.start.direction()));
        Vec3 side = rightOf(dir);

        // Elevated wide framing showcasing the full 8-block swamp, ghost devour spiral & colossal rising Blache jaws
        Vec3 target = origin.add(dir.scale(1.4)).add(0, 2.2 + 1.2 * EffectSprites.easeOutCubic(Math.min(1.0, u * 1.3)), 0);
        double backDist = 11.2 - 1.0 * Math.sin(u * Math.PI * 0.5);
        double sideDist = 4.4 + 1.6 * u;
        double height = 6.8 + 0.6 * Math.sin(u * Math.PI);
        Vec3 camPos = origin.subtract(dir.scale(backDist)).add(side.scale(sideDist)).add(0, height, 0);

        if (mc.player != null) {
            var wall = mc.level.clip(new ClipContext(target, camPos, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
            if (wall.getType() != HitResult.Type.MISS) {
                camPos = wall.getLocation().add(target.subtract(camPos).normalize().scale(0.35));
            }
        }
        Vec3 look = target.subtract(camPos);
        float yaw = (float) Math.toDegrees(Math.atan2(-look.x, look.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(look.y, look.horizontalDistance()));
        return new SkillVfx.CameraPose(camPos, yaw, pitch);
    }

    // --- Dedicated Dual-Layer Sprite Sheet Emitters --------------------------

    private static void emitSoulSlash(
        List<SpriteQuad> out, Vec3 center, Vec3 forward, double rollDeg,
        double radius, double progress, boolean mirror, int alpha, boolean additiveBoost
    ) {
        if (progress < 0.0 || progress >= 1.0 || radius <= 0.0 || alpha <= 4) return;
        Vec3 dir = horizontalDir(forward);
        Vec3 right0 = rightOf(dir);
        double roll = Math.toRadians(rollDeg);
        Vec3 side = right0.scale(Math.cos(roll)).add(UP.scale(Math.sin(roll))).normalize();
        if (mirror) side = side.scale(-1.0);

        double scale = radius * (0.86 + 0.24 * EffectSprites.easeOutCubic(progress));
        int frame = Math.clamp((int) (progress * 8), 0, 7);
        float u0 = frame / 8.0f, u1 = (frame + 1) / 8.0f;
        int a = Math.clamp((int) Math.round(alpha * (1.0 - 0.34 * progress * progress)), 0, 255);

        Vec3 halfU = dir.scale(scale);
        Vec3 halfV = side.scale(scale);
        EffectSprites.emitPlane(out, TEX_SLASH, Blend.TRANSLUCENT, center, halfU.scale(1.04), halfV.scale(1.04), u0, 0f, u1, 1f, COL_WHITE, a);
        if (additiveBoost) {
            EffectSprites.emitPlane(out, TEX_SLASH, Blend.ADDITIVE, center, halfU, halfV, u0, 0f, u1, 1f, COL_LAVENDER, (int) (a * 0.78));
        }
    }

    private static void emitFieldSigil(
        List<SpriteQuad> out, Identifier texture, Vec3 center, double radius, double rotRad,
        int darkRgb, int brightRgb, int alpha
    ) {
        if (radius <= 0.0 || alpha <= 4) return;
        int a = Math.clamp(alpha, 0, 255);
        EffectSprites.emitGround(out, texture, Blend.TRANSLUCENT, center, radius * 1.02, -rotRad * 0.45, 0f, 0f, 1f, 1f, darkRgb, (int) (a * 0.72));
        EffectSprites.emitGround(out, texture, Blend.ADDITIVE, center.add(0, 0.010, 0), radius, rotRad, 0f, 0f, 1f, 1f, brightRgb, a);
    }

    private static void emitForwardBeastHead(
        List<SpriteQuad> out, Identifier texture, int frame, Vec3 center, Vec3 forward, Vec3 right,
        double scale, int alpha
    ) {
        if (scale <= 0.0 || alpha <= 4) return;
        float u0 = frame / 8.0f, u1 = (frame + 1) / 8.0f;
        int a = Math.clamp(alpha, 0, 255);
        // In soul_devour_head.png, +qx faces forward (+dir), -qy is up (+UP -> halfV = -UP * scale)
        Vec3 halfU = forward.scale(scale);
        Vec3 halfVVert = UP.scale(-scale);
        EffectSprites.emitPlane(out, texture, Blend.TRANSLUCENT, center, halfU, halfVVert, u0, 0f, u1, 1f, COL_WHITE, a);
        EffectSprites.emitPlane(out, texture, Blend.ADDITIVE, center, halfU.scale(1.03), halfVVert.scale(1.03), u0, 0f, u1, 1f, COL_LAVENDER, (int) (a * 0.72));
        // Secondary tilted cross-plane so the devouring head looks volumetric from above and front!
        Vec3 halfVDiag1 = UP.scale(-0.55 * scale).add(right.scale(0.83 * scale));
        Vec3 halfVDiag2 = UP.scale(-0.55 * scale).add(right.scale(-0.83 * scale));
        EffectSprites.emitPlane(out, texture, Blend.TRANSLUCENT, center, halfU, halfVDiag1, u0, 0f, u1, 1f, COL_WHITE, (int) (a * 0.78));
        EffectSprites.emitPlane(out, texture, Blend.TRANSLUCENT, center, halfU, halfVDiag2, u0, 0f, u1, 1f, COL_WHITE, (int) (a * 0.78));
        EffectSprites.emitPlane(out, texture, Blend.ADDITIVE, center, halfU.scale(1.02), halfVDiag1, u0, 0f, u1, 1f, COL_VIOLET, (int) (a * 0.50));
    }

    /** Two orthogonal crossed vertical planes around `stoneAxis` so every tombstone looks thick & chiseled from every angle! */
    private static void emitCrossedTombstone(
        List<SpriteQuad> out, Vec3 basePos, Vec3 stoneAxis, double yaw,
        double halfW, double height, int frame, int alpha
    ) {
        if (halfW <= 0.0 || height <= 0.0 || alpha <= 4) return;
        float u0 = frame / 8.0f, u1 = (frame + 1) / 8.0f;
        int a = Math.clamp(alpha, 0, 255);

        Vec3 axis = stoneAxis.normalize();
        Vec3 ref = Math.abs(axis.y) > 0.96 ? new Vec3(1, 0, 0) : UP;
        Vec3 orthoA = axis.cross(ref).normalize();
        Vec3 orthoB = axis.cross(orthoA).normalize();
        double c = Math.cos(yaw), s = Math.sin(yaw);
        Vec3 uA = orthoA.scale(c).add(orthoB.scale(s)).scale(halfW);
        Vec3 uB = orthoB.scale(c).subtract(orthoA.scale(s)).scale(halfW);

        // v=0 is top (+axis), v=1 is base (-axis) -> halfV = -axis * (height * 0.5)
        Vec3 center = basePos.add(axis.scale(height * 0.5));
        Vec3 halfV = axis.scale(-height * 0.5);

        EffectSprites.emitPlane(out, TEX_TOMBSTONE, Blend.TRANSLUCENT, center, uA, halfV, u0, 0f, u1, 1f, COL_WHITE, a);
        EffectSprites.emitPlane(out, TEX_TOMBSTONE, Blend.TRANSLUCENT, center, uB, halfV, u0, 0f, u1, 1f, COL_WHITE, a);
        // Subtle additive violet rune pop pass
        EffectSprites.emitPlane(out, TEX_TOMBSTONE, Blend.ADDITIVE, center, uA.scale(1.02), halfV, u0, 0f, u1, 1f, COL_LAVENDER, (int) (a * 0.42));
        EffectSprites.emitPlane(out, TEX_TOMBSTONE, Blend.ADDITIVE, center, uB.scale(1.02), halfV, u0, 0f, u1, 1f, COL_LAVENDER, (int) (a * 0.42));
    }

    private static void emitPhantomRift(
        List<SpriteQuad> out, Vec3 center, Vec3 forward,
        double halfLen, double halfH, double rollDeg, int frame, int alpha
    ) {
        if (halfLen <= 0.0 || halfH <= 0.0 || alpha <= 4) return;
        Vec3 dir = horizontalDir(forward);
        Vec3 right = rightOf(dir);
        double roll = Math.toRadians(rollDeg);
        Vec3 vAxis = UP.scale(Math.cos(roll)).add(right.scale(Math.sin(roll))).normalize();
        float u0 = frame / 8.0f, u1 = (frame + 1) / 8.0f;
        int a = Math.clamp(alpha, 0, 255);

        Vec3 halfU = dir.scale(halfLen);
        Vec3 halfV = vAxis.scale(-halfH);
        EffectSprites.emitPlane(out, TEX_PHANTOM_RIFT, Blend.TRANSLUCENT, center, halfU, halfV.scale(1.04), u0, 0f, u1, 1f, COL_WHITE, a);
        EffectSprites.emitPlane(out, TEX_PHANTOM_RIFT, Blend.ADDITIVE, center, halfU, halfV, u0, 0f, u1, 1f, COL_LAVENDER, (int) (a * 0.85));
    }

    private static void emitGhostFlash(List<SpriteQuad> out, Vec3 center, Vec3 camera, double radius, double progress, int alpha) {
        if (progress < 0.0 || progress >= 1.0 || radius <= 0.0 || alpha <= 4) return;
        int frame = Math.clamp((int) (progress * 8), 0, 7);
        int a = Math.clamp((int) Math.round(alpha * (1.0 - 0.35 * progress * progress)), 0, 255);
        emitFramedBillboard(out, TEX_GHOST_FLASH, 8, frame, center, camera, radius, a, true, COL_GHOST_CYAN, 0.82);
    }

    private static void emitKallaExplosion(
        List<SpriteQuad> out, Vec3 center, Vec3 camera, double radius, double progress, boolean groundFlat, int alpha
    ) {
        if (progress < 0.0 || progress >= 1.0 || radius <= 0.0 || alpha <= 4) return;
        int frame = Math.clamp((int) (progress * 8), 0, 7);
        float u0 = frame / 8.0f, u1 = (frame + 1) / 8.0f;
        double scale = radius * (0.64 + 0.50 * EffectSprites.easeOutCubic(progress));
        int a = Math.clamp((int) Math.round(alpha * (1.0 - 0.38 * progress * progress)), 0, 255);

        if (groundFlat) {
            EffectSprites.emitGround(out, TEX_KALLA_EXPLOSION, Blend.TRANSLUCENT, center, scale, progress * 0.6, u0, 0f, u1, 1f, COL_WHITE, a);
            EffectSprites.emitGround(out, TEX_KALLA_EXPLOSION, Blend.ADDITIVE, center.add(0, 0.018, 0), scale * 0.96, progress * 0.6, u0, 0f, u1, 1f, COL_KALLA_FLAME, (int) (a * 0.78));
        } else {
            emitFramedBillboard(out, TEX_KALLA_EXPLOSION, 8, frame, center, camera, scale, a, true, COL_KALLA_FLAME, 0.78);
        }
    }

    private static void emitBlacheHeadPlane(
        List<SpriteQuad> out, Vec3 center, Vec3 horizAxis, double halfW, double halfH, int frame, int alpha
    ) {
        if (halfW <= 0.0 || halfH <= 0.0 || alpha <= 4) return;
        float u0 = frame / 8.0f, u1 = (frame + 1) / 8.0f;
        int a = Math.clamp(alpha, 0, 255);
        Vec3 halfU = horizAxis.scale(halfW);
        Vec3 halfV = UP.scale(-halfH);
        EffectSprites.emitPlane(out, TEX_BLACHE_HEAD, Blend.TRANSLUCENT, center, halfU, halfV, u0, 0f, u1, 1f, COL_WHITE, a);
        EffectSprites.emitPlane(out, TEX_BLACHE_HEAD, Blend.ADDITIVE, center, halfU.scale(1.02), halfV.scale(1.02), u0, 0f, u1, 1f, COL_LAVENDER, (int) (a * 0.62));
    }

    private static void emitBlacheHand(
        List<SpriteQuad> out, Vec3 base, double halfW, double height, int frame, double yaw, int alpha
    ) {
        if (halfW <= 0.0 || height <= 0.0 || alpha <= 4) return;
        float u0 = frame / 8.0f, u1 = (frame + 1) / 8.0f;
        int a = Math.clamp(alpha, 0, 255);
        Vec3 center = base.add(0, height * 0.5, 0);
        Vec3 halfV = UP.scale(-height * 0.5);
        Vec3 u1Vec = new Vec3(Math.cos(yaw) * halfW, 0, Math.sin(yaw) * halfW);
        Vec3 u2Vec = new Vec3(-Math.sin(yaw) * halfW * 0.85, 0, Math.cos(yaw) * halfW * 0.85);
        EffectSprites.emitPlane(out, TEX_BLACHE_HAND, Blend.TRANSLUCENT, center, u1Vec, halfV, u0, 0f, u1, 1f, COL_WHITE, a);
        EffectSprites.emitPlane(out, TEX_BLACHE_HAND, Blend.TRANSLUCENT, center, u2Vec, halfV, u0, 0f, u1, 1f, COL_WHITE, (int) (a * 0.72));
        EffectSprites.emitPlane(out, TEX_BLACHE_HAND, Blend.ADDITIVE, center, u1Vec.scale(1.03), halfV, u0, 0f, u1, 1f, COL_VIOLET, (int) (a * 0.55));
    }

    private static void emitAbyssPillar(
        List<SpriteQuad> out, Vec3 base, double halfW, double height, double progress, double yawOffset, int alpha
    ) {
        if (progress < 0.0 || progress >= 1.0 || halfW <= 0.0 || height <= 0.0 || alpha <= 4) return;
        int frame = Math.clamp((int) (progress * 8), 0, 7);
        float u0 = frame / 8.0f, u1 = (frame + 1) / 8.0f;
        Vec3 center = base.add(0, height * 0.5, 0);
        Vec3 halfV = UP.scale(-height * 0.5);
        int a = Math.clamp(alpha, 0, 255);

        for (int i = 0; i < 3; i++) {
            double ang = yawOffset + i * (Math.PI / 3.0);
            Vec3 halfU = new Vec3(Math.cos(ang) * halfW, 0, Math.sin(ang) * halfW);
            EffectSprites.emitPlane(out, TEX_ABYSS_PILLAR, Blend.TRANSLUCENT, center, halfU, halfV, u0, 0f, u1, 1f, COL_WHITE, a);
            if (i < 2) {
                EffectSprites.emitPlane(out, TEX_ABYSS_PILLAR, Blend.ADDITIVE, center, halfU.scale(0.92), halfV, u0, 0f, u1, 1f, COL_LAVENDER, (int) (a * 0.65));
            }
        }
    }

    private static void emitGhostMist(List<SpriteQuad> out, Vec3 center, Vec3 camera, double radius, long timeMs, int alpha, int seedOffset) {
        if (radius <= 0.0 || alpha <= 4) return;
        int frame = (int) Math.floorMod((timeMs / 120L) + seedOffset, 4L);
        emitFramedBillboard(out, TEX_GHOST_MIST, 4, frame, center, camera, radius, Math.clamp(alpha, 0, 255), false, COL_VIOLET, 0.0);
    }

    /** Cylindrical upright camera-facing billboard (stays vertical so standing spirits never tilt flat when looked down at!). */
    private static void emitUprightFramed(
        List<SpriteQuad> out, Identifier texture, int totalFrames, int frame,
        Vec3 center, Vec3 camera, double halfW, double halfH, int alpha,
        boolean additiveBoost, int addRgb, double addFactor
    ) {
        if (halfW <= 0.0 || halfH <= 0.0 || alpha <= 4) return;
        float u0 = frame / (float) totalFrames, u1 = (frame + 1) / (float) totalFrames;
        Vec3 lookH = new Vec3(camera.x - center.x, 0, camera.z - center.z);
        if (lookH.lengthSqr() < 1e-6) lookH = new Vec3(0, 0, 1);
        else lookH = lookH.normalize();
        Vec3 right = lookH.cross(UP).normalize();
        Vec3 halfU = right.scale(halfW);
        Vec3 halfV = UP.scale(-halfH);
        int a = Math.clamp(alpha, 0, 255);
        EffectSprites.emitPlane(out, texture, Blend.TRANSLUCENT, center, halfU, halfV, u0, 0f, u1, 1f, COL_WHITE, a);
        if (additiveBoost) {
            EffectSprites.emitPlane(out, texture, Blend.ADDITIVE, center.add(lookH.scale(0.025)), halfU.scale(1.03), halfV.scale(1.03), u0, 0f, u1, 1f, addRgb, (int) (a * addFactor));
        }
    }

    /** Full camera-facing billboard for multi-frame strips (v=0 stays UP). */
    private static void emitFramedBillboard(
        List<SpriteQuad> out, Identifier texture, int totalFrames, int frame,
        Vec3 center, Vec3 camera, double radius, int alpha,
        boolean additiveBoost, int addRgb, double addFactor
    ) {
        if (radius <= 0.0 || alpha <= 4) return;
        float u0 = frame / (float) totalFrames, u1 = (frame + 1) / (float) totalFrames;
        Vec3 look = camera.subtract(center);
        if (look.lengthSqr() < 1e-6) look = new Vec3(0, 0, 1);
        else look = look.normalize();
        Vec3 right = look.cross(Math.abs(look.y) > 0.95 ? new Vec3(1, 0, 0) : UP).normalize();
        Vec3 up = right.cross(look).normalize();
        Vec3 halfU = right.scale(radius);
        Vec3 halfV = up.scale(-radius);
        int a = Math.clamp(alpha, 0, 255);
        EffectSprites.emitPlane(out, texture, Blend.TRANSLUCENT, center, halfU, halfV, u0, 0f, u1, 1f, COL_WHITE, a);
        if (additiveBoost) {
            EffectSprites.emitPlane(out, texture, Blend.ADDITIVE, center.add(look.scale(0.025)), halfU.scale(1.04), halfV.scale(1.04), u0, 0f, u1, 1f, addRgb, (int) (a * addFactor));
        }
    }

    // --- Pure Math & Lookup Utilities ----------------------------------------

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
