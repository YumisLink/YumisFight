package cn.yumis.fight.modules.skills.client;

import cn.yumis.fight.modules.dungeons.api.TowerBattleCard;
import cn.yumis.fight.modules.presentation.api.client.CameraShake;
import cn.yumis.fight.modules.presentation.api.client.EffectSprites;
import cn.yumis.fight.modules.presentation.api.client.EffectSprites.Blend;
import cn.yumis.fight.modules.presentation.api.client.EffectSprites.SpriteQuad;
import cn.yumis.fight.modules.presentation.api.client.LightningBolt;
import cn.yumis.fight.modules.presentation.api.client.RibbonTrail;
import cn.yumis.fight.modules.skills.api.SkillCatalog;
import cn.yumis.fight.modules.skills.api.SkillVisualEvent;
import cn.yumis.fight.modules.skills.api.SkillVisualRules;
import cn.yumis.fight.modules.skills.api.StormChoreography;
import cn.yumis.fight.modules.skills.api.client.SkillController;
import cn.yumis.fight.modules.skills.api.client.SkillsClient;
import cn.yumis.fight.modules.skills.client.SkillVfx.LevelRenderContext;
import cn.yumis.fight.modules.skills.client.SkillVfx.Sword;
import cn.yumis.fight.modules.skills.client.SkillVfx.Track;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;

import java.util.*;

import static cn.yumis.fight.modules.skills.api.SkillVisualEvent.Phase.*;
import static cn.yumis.fight.modules.skills.client.SkillVfx.vector;

/**
 * Dedicated DNF-inspired Weapon Master / Swordmaster (剑魂 / 剑圣) & Ghost Swordsman VFX renderer.
 * Zero legacy untextured WorldQuads: 100% custom high-res pixel-sharp + soft-feathered sprite sheets
 * (source: tools/build_swordmaster_effect_textures.py), dual-layer dark-silhouette translucent +
 * incandescent additive rendering, camera-aligned ribbons, crackling arcs, frozen-pose afterimages,
 * weapon-mastery differentiation, rich persistent Overdrive / Greatsword-charge visuals, and layered audio.
 * Pure client presentation: never grants combat authority, hitboxes, or physics changes.
 */
final class SwordmasterVisuals {
    private static final Vec3 UP = new Vec3(0, 1, 0);

    static final Identifier TEX_SLASH = tex("sword_slash.png");
    static final Identifier TEX_DRAW_RING = tex("draw_ring.png");
    static final Identifier TEX_IAIDO_FLASH = tex("iaido_flash.png");
    static final Identifier TEX_DRAGON_HEAD = tex("dragon_head.png");
    static final Identifier TEX_GALE_CONE = tex("gale_cone.png");
    static final Identifier TEX_RISING_ARC = tex("rising_arc.png");
    static final Identifier TEX_SWORD_WAVE = tex("sword_wave.png");
    static final Identifier TEX_GROUND_FISSURE = tex("ground_fissure.png");
    static final Identifier TEX_OVERDRIVE_AURA = tex("overdrive_aura.png");
    static final Identifier TEX_SWORD_FORMATION = tex("sword_formation.png");
    static final Identifier TEX_GHOST_CRESCENT = tex("ghost_crescent.png");

    private static final int COL_WHITE = 0xFFFFFF;
    private static final int COL_GOLD = SkillVisualEvent.GOLD;
    private static final int COL_GOLD_HOT = 0xFFF7CC;
    private static final int COL_LIGHT_FOLLOW = 0xFFF1A2;
    private static final int COL_LIGHT_SPARK = 0xD8F8FF;

    private static final Map<UUID, Long> LAST_DRAGON_AFTERIMAGE = new HashMap<>();
    private static final Map<UUID, Long> LAST_WAVE_AUDIO = new HashMap<>();
    private static final Map<UUID, Long> LAST_DANCE_SOUND = new HashMap<>();

    private SwordmasterVisuals() {}

    private static Identifier tex(String name) {
        return Identifier.fromNamespaceAndPath("yumis_dungeon_and_fight", "textures/effect/swordmaster/" + name);
    }

    static void clear() {
        LAST_DRAGON_AFTERIMAGE.clear();
        LAST_WAVE_AUDIO.clear();
        LAST_DANCE_SOUND.clear();
    }

    // --- Persistent Actor Visuals: Overdrive Blade Flame + Foot Vortex & Greatsword Charge ---

    static void actors(List<SpriteQuad> sp, Vec3 camera, long serverNow, long renderNow) {
        var mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) return;

        // 1. 破极兵刃 (Overdrive) 30s rich persistent blade flame & foot aura for all visible Swordmasters
        for (var actor : CareerClient.INSTANCE.view.actors) {
            if (actor.overdriveUntil <= serverNow) continue;
            UUID ownerId;
            try {
                ownerId = UUID.fromString(actor.owner);
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            Player p = mc.level.getPlayerByUUID(ownerId);
            if (p == null || !p.isAlive()) continue;
            renderOverdrivePersistent(sp, camera, p, renderNow);
        }

        // 2. 巨剑精通蓄力普攻 (Greatsword Charge) complete ground charge ring + blade-tip energy
        if (mc.player != null && mc.player.isAlive() && SkillController.INSTANCE.greatCharging()) {
            renderGreatswordCharge(sp, camera, mc.player, renderNow);
        }
    }

    private static void renderOverdrivePersistent(List<SpriteQuad> sp, Vec3 camera, Player p, long now) {
        Vec3 base = PlayerVisualAnchor.position(p);
        boolean ownFp = isOwnFirstPerson(p.getUUID());
        double pulse = 0.5 + 0.5 * Math.sin(now * 0.0065);

        // Golden sword-formation aura & swirling golden sword-qi flame plumes around the Swordmaster's feet
        double footAlphaScale = ownFp ? 0.42 : 1.0;
        emitFormation(sp, base.add(0, 0.04, 0), 1.32 + 0.08 * pulse, now * 0.0018, COL_GOLD, (int) (155 * footAlphaScale), !ownFp);
        int plumes = ownFp ? 2 : 4;
        for (int i = 0; i < plumes; i++) {
            double ang = now * 0.0026 + i * (Math.TAU / plumes);
            double rad = ownFp ? 0.85 : 0.72 + 0.08 * Math.sin(ang * 2.0);
            Vec3 fp = base.add(Math.cos(ang) * rad, 0.04, Math.sin(ang) * rad);
            emitAuraPlume(sp, fp, 0.34, 1.05 + 0.22 * Math.sin(now * 0.008 + i), now + i * 95L, COL_GOLD, (int) (145 * footAlphaScale), ang);
        }

        // Blazing golden sword-qi flame sheath wrapping the held sword blade (skipped right in front of 1st-person camera)
        var pose = Effects.VFX.playerPose(p.getUUID(), now);
        PlayerPoseAnimation.Frame frame = pose == null ? null : pose.frame();
        Vec3 grip = PlayerBoneAnchor.hand(p, frame, true);
        Vec3 tip = PlayerBoneAnchor.weaponTip(p, frame, 1.32);
        Vec3 bladeAxis = tip.subtract(grip);
        if (bladeAxis.lengthSqr() > 0.04 && !ownFp) {
            emitBladeAuraSheath(sp, grip, tip, 0.26, now, COL_GOLD, 215);
            EffectSprites.glow(sp, tip, camera, 0.52 + 0.10 * pulse, COL_GOLD_HOT, 205);
            var mc = Minecraft.getInstance();
            if (mc != null && mc.player != null && p.getUUID().equals(mc.player.getUUID())
                && SkillsClient.current().ready() && TowerBattleCard.SM_OVERDRIVE_LIGHT.held(SkillsClient.current().stats())) {
                LightningBolt.bolt(sp, grip, tip, camera, now / 55L, 5, 0.20, 0.042, COL_LIGHT_SPARK, 220, false);
            }
            if (p.getDeltaMovement().horizontalDistanceSqr() > 0.0015 || frame != null) {
                RibbonTrail.push("od_blade:" + p.getUUID(), tip, 0.14, COL_GOLD, 195, 190L, now);
            }
        }
    }

    private static void renderGreatswordCharge(List<SpriteQuad> sp, Vec3 camera, Player p, long now) {
        double prog = SkillController.INSTANCE.greatChargeProgress(now);
        boolean full = SkillController.INSTANCE.greatChargeFull(now);
        boolean ownFp = isOwnFirstPerson(p.getUUID());
        Vec3 base = PlayerVisualAnchor.position(p);
        Vec3 forward = horizontalDir(PlayerVisualAnchor.forward(p));
        int color = full ? COL_GOLD : SkillVisualEvent.CYAN;

        // Ground expanding charge formation + contracting energy ring
        double ringR = 1.45 + 0.75 * prog;
        emitFormation(sp, base.add(0, 0.05, 0), ringR, now * (full ? 0.0036 : 0.0020), color, full ? 225 : (int) (115 + 90 * prog), true);
        double contract = 1.0 - ((now % 420L) / 420.0);
        EffectSprites.shockwave(sp, base.add(0, 0.08, 0), ringR * (0.30 + 0.85 * contract), -now * 0.004, EffectSprites.brighten(color, 0.45), (int) ((full ? 215 : 155) * contract));

        // Gathering energy at the greatsword blade tip
        var pose = Effects.VFX.playerPose(p.getUUID(), now);
        Vec3 tip = ownFp
            ? base.add(forward.scale(1.15)).add(0, 1.15, 0)
            : PlayerBoneAnchor.weaponTip(p, pose == null ? null : pose.frame(), 1.45);
        EffectSprites.glow(sp, tip, camera, (0.45 + 0.65 * prog) * (ownFp ? 0.65 : 1.0), EffectSprites.brighten(color, 0.45), full ? 235 : 185);
        if (full) {
            double flashPhase = (now % 480L) / 480.0;
            emitIaidoFlash(sp, tip, camera, ownFp ? 0.95 : 1.45, flashPhase, COL_GOLD_HOT, 240);
            if (!ownFp) {
                Vec3 outer = tip.add(Math.cos(now * 0.015) * 0.85, 0.35 * Math.sin(now * 0.011), Math.sin(now * 0.015) * 0.85);
                LightningBolt.bolt(sp, outer, tip, camera, now / 55L, 5, 0.22, 0.042, COL_GOLD_HOT, 200, false);
            }
        }
    }

    // --- Storm Tick Choreography (24 rapid sword-to-sword slashes) -----------

    static void tick(Track t, long time) {
        if (!SkillCatalog.STORM.id.equals(t.start.skill())) return;
        long age = StormChoreography.presentationTime(time - t.start.startedAt());
        int phase = (int) Math.clamp((age - 1000) / 250, 0, 23);
        if (age >= 1000 && age < 7000 && phase > t.soundPhase) {
            t.soundPhase = phase;
            Vec3 position = local(t.start, StormChoreography.actor(t.start.seed(), age));
            // Crisp escalating metallic sword-clash + rapid cut rhythm
            SkillSound.playAt(position.x, position.y, position.z, SoundEvents.PLAYER_ATTACK_SWEEP, 0.56f, 1.08f + (phase % 4) * 0.12f);
            if ((phase & 1) == 0) {
                SkillSound.playAt(position.x, position.y, position.z, SoundEvents.PLAYER_ATTACK_CRIT, 0.32f, 1.25f + (phase % 3) * 0.14f);
            }
            var mc = Minecraft.getInstance();
            Player actor = mc == null || mc.level == null ? null : mc.level.getPlayerByUUID(t.start.caster());
            if (actor != null && (phase & 1) == 0) {
                int[] order = StormChoreography.order(t.start.seed());
                Vec3 from = local(t.start, StormChoreography.sword(order[phase]));
                Vec3 to = phase == 23 ? vector(t.start.origin()) : local(t.start, StormChoreography.sword(order[phase + 1]));
                PlayerAfterimages.captureAt(actor, position, to.subtract(from), t.start.color(), 165, 290L, true);
            }
            CameraShake.impulse(position, 16, 0.13f, 45L, 95L);
        }
    }

    // --- Layered Audio & Impact Cues -----------------------------------------

    static void play(SkillVisualEvent e) {
        if (e.phase() == TRAIL) return;
        if (BerserkerVisuals.isFrenzyBasic(e)) {
            BerserkerVisuals.playFrenzyBasicAudio(e);
            return;
        }
        if (SoulBenderVisuals.isKeigaBasic(e)) {
            SoulBenderVisuals.playKeigaBasicAudio(e);
            return;
        }
        if (BattleMageVisuals.isBattleMageBasic(e)) {
            BattleMageVisuals.playBasicAudio(e);
            return;
        }
        if (WitchVisuals.isBroomBasic(e)) {
            WitchVisuals.playBroomBasicAudio(e);
            return;
        }
        String id = e.skill();
        Vec3 pos = vector(e.position());
        var mc = Minecraft.getInstance();
        Player actor = mc == null || mc.level == null ? null : mc.level.getPlayerByUUID(e.caster());
        boolean ownFp = isOwnFirstPerson(e.caster());
        String weapon = e.weaponType();

        switch (id) {
            case "ATTACK" -> {
                if (e.phase() != START) return;
                boolean offhandStrike = BuiltinSkillVisuals.isDualOffhandStrike(e);
                float basePitch = "GREATSWORD".equals(weapon) ? 0.78f : "LIGHTSABER".equals(weapon) ? (offhandStrike ? 1.42f : 1.30f) : "KATANA".equals(weapon) ? 1.22f : "BLUNT".equals(weapon) ? 0.85f : 1.08f;
                SkillSound.playAt(e.position(), SoundEvents.PLAYER_ATTACK_SWEEP, e.sweep() ? 0.78f : 0.56f, basePitch);
                if (e.sweep()) {
                    SkillSound.playAt(e.position(), SoundEvents.PLAYER_ATTACK_STRONG, 0.72f, 0.76f);
                    CameraShake.impulse(pos, 12, 0.22f, 55L, 120L);
                } else if (e.dual()) {
                    SkillSound.playAt(e.position(), SoundEvents.PLAYER_ATTACK_CRIT, 0.30f, offhandStrike ? 1.48f : 1.34f);
                }
            }
            case "MASTERY_WAVE" -> {
                if (e.phase() == START || e.phase() == RELEASE) {
                    SkillSound.playAt(e.position(), SoundEvents.BREEZE_SHOOT, 0.36f, 1.48f);
                }
            }
            case "yumis_dungeon_and_fight:ghost" -> {
                if (e.phase() == START) {
                    SkillSound.playAt(e.position(), SoundEvents.SOUL_ESCAPE.value(), 0.52f, 0.85f);
                } else if (e.phase() == RELEASE) {
                    SkillSound.playAt(e.position(), SoundEvents.PLAYER_ATTACK_STRONG, 0.80f, 0.78f);
                    SkillSound.playAt(e.position(), SoundEvents.SOUL_ESCAPE.value(), 0.76f, 0.65f);
                    CameraShake.impulse(pos, 14, 0.24f + (float) (0.10 * e.charge()), 65L, 145L);
                }
            }
            case "yumis_dungeon_and_fight:triple" -> {
                if (e.phase() == SEGMENT) {
                    float p = 1.08f + (e.sequence() % 4) * 0.11f;
                    SkillSound.playAt(e.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 0.66f, p);
                    SkillSound.playAt(e.position(), SoundEvents.BREEZE_SHOOT, 0.42f, p + 0.12f);
                    if (actor != null && !ownFp) PlayerAfterimages.capture(actor, e.color(), 155, 250L, true);
                }
            }
            case "yumis_dungeon_and_fight:wave" -> {
                if (AsuraVisuals.isEyeWave(e)) {
                    AsuraVisuals.playEyeLightWingAudio(e);
                    return;
                }
                if (e.phase() == START || e.phase() == RELEASE) {
                    long prev = LAST_WAVE_AUDIO.getOrDefault(e.caster(), 0L);
                    if (e.at() - prev < 80L) return;
                    LAST_WAVE_AUDIO.put(e.caster(), e.at());
                    SkillSound.playAt(e.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 0.65f, 0.90f);
                    SkillSound.playAt(e.position(), SoundEvents.BREEZE_SHOOT, 0.58f, 0.82f);
                    CameraShake.impulse(pos, 12, 0.14f, 45L, 95L);
                }
            }
            case "yumis_dungeon_and_fight:overdrive" -> {
                if (e.phase() == START) {
                    SkillSound.playAt(e.position(), SoundEvents.BEACON_ACTIVATE, 0.68f, 1.42f);
                    SkillSound.playAt(e.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 0.72f, 1.28f);
                    SkillSound.playAt(e.position(), SoundEvents.PLAYER_ATTACK_STRONG, 0.58f, 1.15f);
                    CameraShake.impulse(pos, 14, 0.22f, 65L, 140L);
                    if (actor != null && !ownFp) PlayerAfterimages.capture(actor, COL_GOLD, 185, 360L, true);
                }
            }
            case "yumis_dungeon_and_fight:rising" -> {
                if (e.phase() == START) {
                    SkillSound.playAt(e.position(), SoundEvents.BREEZE_JUMP, 0.72f, 0.92f);
                    SkillSound.playAt(e.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 0.62f, 0.96f);
                    if (actor != null && !ownFp) PlayerAfterimages.capture(actor, e.color(), 165, 290L, true);
                } else if (e.phase() == SEGMENT) {
                    float pitch = 1.04f + (e.sequence() % 3) * 0.12f;
                    SkillSound.playAt(e.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 0.72f, pitch);
                    SkillSound.playAt(e.position(), SoundEvents.BREEZE_SHOOT, 0.48f, pitch + 0.10f);
                    if (actor != null && !ownFp) PlayerAfterimages.capture(actor, e.color(), 170, 260L, true);
                } else if (e.phase() == HIT) {
                    SkillSound.playAt(e.position(), SoundEvents.PLAYER_ATTACK_STRONG, 0.85f, 1.02f);
                    SkillSound.playAt(e.position(), SoundEvents.BREEZE_JUMP, 0.65f, 1.15f);
                    CameraShake.impulse(pos, 14, 0.22f, 55L, 120L);
                } else if (e.phase() == RELEASE) {
                    SkillSound.playAt(e.position(), SoundEvents.PLAYER_ATTACK_STRONG, 0.90f, 0.88f);
                    SkillSound.playAt(e.position(), SoundEvents.MACE_SMASH_GROUND, 0.62f, 1.15f);
                    CameraShake.impulse(pos, 16, 0.30f, 75L, 165L);
                    if (actor != null && !ownFp) PlayerAfterimages.capture(actor, e.color(), 175, 320L, true);
                } else if (e.phase() == FINISH) {
                    SkillSound.playAt(e.position(), SoundEvents.MACE_SMASH_GROUND_HEAVY, 0.95f, 0.78f);
                    SkillSound.playAt(e.position(), SoundEvents.PLAYER_ATTACK_STRONG, 0.98f, 0.74f);
                    SkillSound.playAt(e.position(), SoundEvents.TRIDENT_THUNDER.value(), 0.56f, 1.05f);
                    CameraShake.impulse(pos, 22, 0.44f, 110L, 240L);
                    if (actor != null && !ownFp) PlayerAfterimages.capture(actor, e.color(), 200, 360L, true);
                }
            }
            case "yumis_dungeon_and_fight:meteor_rise" -> {
                if (e.phase() == HIT) {
                    SkillSound.playAt(e.position(), SoundEvents.BREEZE_JUMP, 0.66f, 1.18f);
                    SkillSound.playAt(e.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 0.68f, 1.16f);
                } else if (e.phase() == RELEASE) {
                    SkillSound.playAt(e.position(), SoundEvents.PLAYER_ATTACK_STRONG, 0.82f, 1.06f);
                    SkillSound.playAt(e.position(), SoundEvents.PLAYER_ATTACK_CRIT, 0.54f, 1.32f);
                    CameraShake.impulse(pos, 14, 0.22f, 55L, 125L);
                    if (actor != null && !ownFp) PlayerAfterimages.capture(actor, e.color(), 170, 280L, true);
                }
            }
            case "yumis_dungeon_and_fight:meteor_leap" -> {
                if (e.phase() == START || e.phase() == HIT) {
                    SkillSound.playAt(e.position(), SoundEvents.BREEZE_JUMP, 0.72f, 1.02f);
                    SkillSound.playAt(e.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 0.62f, 1.10f);
                    if (actor != null && !ownFp) PlayerAfterimages.capture(actor, e.color(), 165, 260L, true);
                } else if (e.phase() == RELEASE) {
                    SkillSound.playAt(e.position(), SoundEvents.MACE_SMASH_GROUND, 0.78f, 1.04f);
                    SkillSound.playAt(e.position(), SoundEvents.PLAYER_ATTACK_STRONG, 0.88f, 0.86f);
                    CameraShake.impulse(pos, 16, 0.30f, 70L, 160L);
                    if (actor != null && !ownFp) PlayerAfterimages.capture(actor, e.color(), 180, 300L, true);
                }
            }
            case "yumis_dungeon_and_fight:meteor_pierce" -> {
                if (e.phase() == HIT || e.phase() == RELEASE) {
                    float pitch = e.phase() == RELEASE ? 1.38f : 1.18f + (e.sequence() % 3) * 0.10f;
                    SkillSound.playAt(e.position(), SoundEvents.PLAYER_ATTACK_CRIT, 0.62f, pitch);
                    SkillSound.playAt(e.position(), SoundEvents.BREEZE_SHOOT, 0.50f, pitch + 0.08f);
                    if (e.phase() == RELEASE) {
                        CameraShake.impulse(pos, 13, 0.20f, 50L, 110L);
                        if (actor != null && !ownFp) PlayerAfterimages.capture(actor, e.color(), 165, 250L, true);
                    }
                }
            }
            case SkillVisualRules.SM_DRAW_ARC -> {
                if (e.phase() == START) {
                    SkillSound.playAt(e.position(), SoundEvents.BREEZE_SHOOT, 0.90f, 0.78f);
                    SkillSound.playAt(e.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 0.68f, 1.35f);
                    CameraShake.impulse(pos, 24, 0.26f, 80L, 180L);
                }
            }
            case SkillVisualRules.SM_DRAGON_WHIRLWIND -> {
                if (e.phase() == START) {
                    SkillSound.playAt(e.position(), SoundEvents.BREEZE_SHOOT, 0.64f, 1.18f);
                    SkillSound.playAt(e.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 0.48f, 1.24f);
                }
            }
            case "yumis_dungeon_and_fight:draw" -> {
                if (e.phase() == START) {
                    // Tense DNF iaido sheath glint whisper
                    SkillSound.playAt(e.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 0.52f, 1.65f);
                    SkillSound.playAt(e.position(), SoundEvents.ENCHANTMENT_TABLE_USE, 0.32f, 1.68f);
                } else if (e.phase() == RELEASE) {
                    // Explosive 360° Draw Sword cleave!
                    SkillSound.playAt(e.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 0.95f, 0.84f);
                    SkillSound.playAt(e.position(), SoundEvents.PLAYER_ATTACK_CRIT, 0.82f, 1.12f);
                    SkillSound.playAt(e.position(), SoundEvents.BREEZE_SHOOT, 0.72f, 0.92f);
                    CameraShake.impulse(pos, 18, 0.36f, 90L, 195L);
                    if (actor != null && !ownFp) PlayerAfterimages.capture(actor, e.color(), 195, 360L, true);
                } else if (e.phase() == HIT) {
                    // Lightsaber / Katana mastery unmistakable 2nd Draw Sword light-slash!
                    SkillSound.playAt(e.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 0.90f, 1.30f);
                    SkillSound.playAt(e.position(), SoundEvents.PLAYER_ATTACK_CRIT, 0.86f, 1.46f);
                    SkillSound.playAt(e.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 0.72f, 1.60f);
                    SkillSound.playAt(e.position(), SoundEvents.TRIDENT_THUNDER.value(), 0.40f, 1.52f);
                    CameraShake.impulse(pos, 18, 0.28f, 65L, 145L);
                    if (actor != null && !ownFp) PlayerAfterimages.capture(actor, COL_LIGHT_FOLLOW, 200, 340L, true);
                }
            }
            case "yumis_dungeon_and_fight:dragon" -> {
                if (e.phase() == SEGMENT) {
                    float pitch = 0.96f + (e.sequence() % 5) * 0.09f;
                    SkillSound.playAt(e.position(), SoundEvents.BREEZE_SHOOT, 0.78f, pitch);
                    SkillSound.playAt(e.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 0.75f, pitch + 0.14f);
                    if ("LIGHTSABER".equals(weapon)) {
                        SkillSound.playAt(e.position(), SoundEvents.TRIDENT_THUNDER.value(), 0.34f, 1.55f);
                    }
                    if (actor != null && !ownFp) PlayerAfterimages.capture(actor, e.color(), 180, 320L, true);
                    CameraShake.impulse(pos, 15, 0.21f, 55L, 120L);
                }
            }
            case "yumis_dungeon_and_fight:dance" -> {
                if (e.phase() == HIT) {
                    long lastSound = LAST_DANCE_SOUND.getOrDefault(e.caster(), 0L);
                    if (e.at() - lastSound >= 55L) {
                        LAST_DANCE_SOUND.put(e.caster(), e.at());
                        float p = 1.12f + (e.sequence() % 6) * 0.09f;
                        SkillSound.playAt(e.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 0.54f, p);
                        if ((e.sequence() & 1) == 0) {
                            SkillSound.playAt(e.position(), SoundEvents.PLAYER_ATTACK_CRIT, 0.36f, p + 0.16f);
                            if (actor != null && !ownFp) {
                                Vec3 dir = horizontalDir(vector(e.direction()));
                                Vec3 side = rightOf(dir);
                                double offset = ((e.sequence() & 2) == 0 ? 1 : -1) * 0.65;
                                PlayerAfterimages.captureAt(actor, vector(e.position()).add(side.scale(offset)), dir, e.color(), 145, 210L, true);
                            }
                            CameraShake.impulse(pos, 11, 0.10f, 35L, 75L);
                        }
                    }
                } else if (e.phase() == RELEASE) {
                    // Illusion Sword Dance giant sword-qi finisher!
                    SkillSound.playAt(e.position(), SoundEvents.PLAYER_ATTACK_STRONG, 0.92f, 0.78f);
                    SkillSound.playAt(e.position(), SoundEvents.BREEZE_SHOOT, 0.80f, 0.76f);
                    CameraShake.impulse(pos, 18, 0.34f, 85L, 185L);
                }
            }
            case "yumis_dungeon_and_fight:storm" -> {
                if (e.phase() == START) {
                    SkillSound.playAt(e.position(), SoundEvents.BEACON_ACTIVATE, 0.88f, 0.95f);
                    SkillSound.playAt(e.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 0.82f, 1.15f);
                    CameraShake.impulse(pos, 20, 0.24f, 80L, 160L);
                } else if (e.phase() == FINISH) {
                    SkillSound.playAt(e.position(), SoundEvents.MACE_SMASH_GROUND_HEAVY, 0.95f, 0.68f);
                    SkillSound.playAt(e.position(), SoundEvents.PLAYER_ATTACK_STRONG, 1.0f, 0.65f);
                    SkillSound.playAt(e.position(), SoundEvents.TRIDENT_THUNDER.value(), 0.72f, 0.95f);
                    CameraShake.impulse(pos, 26, 0.56f, 160L, 310L);
                }
            }
            default -> {
                if (e.phase() == START || e.phase() == RELEASE || e.phase() == FINISH) {
                    SoundEvent sound = e.phase() == FINISH ? SoundEvents.PLAYER_ATTACK_STRONG : SoundEvents.PLAYER_ATTACK_SWEEP;
                    SkillSound.playAt(e.position(), sound, 0.55f, e.phase() == FINISH ? 0.72f : 1.12f);
                }
            }
        }
    }

    // --- Main Skill Rendering Router -----------------------------------------

    static void render(LevelRenderContext context, Track t, long age, long time, List<cn.yumis.fight.modules.presentation.api.client.WorldQuads.Quad> quads, Vec3 camera) {
        List<SpriteQuad> sp = context.sprites();
        SkillVisualEvent event = t.start;
        String id = event.skill();

        // Staff basic bolt projectile (pure sprite glow + flash star + ribbon; zero legacy WorldQuads!)
        if ("ATTACK".equals(id) && t.motion != null) {
            double fade = t.last.phase() == FINISH ? Math.max(0, 1 - (time - t.last.at()) / 180.0) : 1.0;
            Vec3 p = vector(t.last.position());
            EffectSprites.glow(sp, p, camera, 0.62 * fade, event.color(), (int) (230 * fade));
            emitIaidoFlash(sp, p, camera, 0.52 * fade, (time % 320L) / 320.0, EffectSprites.brighten(event.color(), 0.6), (int) (220 * fade));
            RibbonTrail.push(event.cast(), p, 0.18 * fade, event.color(), (int) (215 * fade), 220L, time);
            return;
        }

        // Track animated weapon-tip ribbon during active melee blade skills (suppressed in own 1st person to keep view clean)
        var mc = Minecraft.getInstance();
        Player caster = mc == null || mc.level == null ? null : mc.level.getPlayerByUUID(event.caster());
        boolean ownFp = isOwnFirstPerson(event.caster());
        if (caster != null && !ownFp && !SkillCatalog.STORM.id.equals(id) && !SkillCatalog.OVERDRIVE.id.equals(id) && age < 900) {
            var pose = Effects.VFX.playerPose(caster.getUUID(), time);
            if (pose != null) {
                Vec3 tip = PlayerBoneAnchor.weaponTip(caster, pose.frame(), 1.22);
                RibbonTrail.push("sm_tip:" + event.cast(), tip, 0.16, event.color(), 195, 175L, time);
            }
        }

        if (SkillCatalog.STORM.id.equals(id)) {
            renderStorm(context, t, age, time, sp, camera);
            return;
        }
        if (SkillCatalog.OVERDRIVE.id.equals(id)) {
            renderOverdriveCast(t, age, time, sp, camera);
            return;
        }
        if (SkillVisualRules.SM_DRAW_ARC.equals(id)) {
            renderDrawArcWave(t, age, time, sp, camera);
            return;
        }
        if (SkillVisualRules.SM_DRAGON_WHIRLWIND.equals(id)) {
            renderDragonWhirlwind(t, age, time, sp, camera);
            return;
        }
        if (SkillCatalog.GHOST.id.equals(id) && !t.released) {
            renderGhostCharge(t, age, time, sp, camera);
        }
        if (SkillCatalog.DRAW.id.equals(id) && !t.released && age < 440) {
            renderDrawWindup(t, age, time, sp, camera);
        }
        if (SkillCatalog.DANCE.id.equals(id) && !t.released) {
            // Subtle rotating sword-formation footing ring during Illusion Sword Dance flurry
            Vec3 origin = caster != null ? PlayerVisualAnchor.position(caster) : vector(event.origin());
            emitFormation(sp, origin.add(0, 0.04, 0), 2.35, time * 0.0042, event.color(), ownFp ? 95 : 165, false);
        }

        // Flying waves: Earth Wave (地裂波动剑), Dance Finisher Wave (幻影剑舞终结剑气), Shortsword Mastery Wave (短剑剑气)
        boolean isWaveSkill = SkillCatalog.WAVE.id.equals(id)
            || ((SkillCatalog.DANCE.id.equals(id) || "MASTERY_WAVE".equals(id)) && t.released);
        if (isWaveSkill && t.last.phase() == TRAIL && time - t.last.at() < 200) {
            renderFlyingWave(t, time, sp, camera);
        }

        // Active flashes (START / SEGMENT / RELEASE / HIT)
        for (SkillVisualEvent flash : t.flashes) {
            long elapsed = time - flash.at();
            long dur = flashDuration(flash);
            if (elapsed < 0 || elapsed >= dur) continue;
            renderFlash(quads, sp, camera, flash, elapsed, dur, time);
        }
        if (t.last.phase() == TRAIL && time - t.last.at() < 180) {
            renderTrail(t, time, sp, camera);
        }
    }

    // --- Overdrive Cast Explosion (破极兵刃) ---------------------------------

    private static void renderOverdriveCast(Track t, long age, long time, List<SpriteQuad> sp, Vec3 camera) {
        if (age >= 700L) return;
        double u = Math.clamp(age / 700.0, 0.0, 0.999);
        double fade = 1.0 - u;
        Vec3 pos = liveCasterPos(t.start.caster(), vector(t.start.origin()));
        boolean ownFp = isOwnFirstPerson(t.start.caster());

        // Expanding golden sword-formation seal + twin shockwaves
        emitFormation(sp, pos.add(0, 0.05, 0), 2.65 * (0.40 + 0.72 * EffectSprites.easeOutCubic(u)), time * 0.004, COL_GOLD, (int) (230 * fade), true);
        EffectSprites.shockwave(sp, pos.add(0, 0.09, 0), 3.2 * EffectSprites.easeOutCubic(u), u * 1.6, COL_GOLD, (int) (235 * fade));
        EffectSprites.shockwave(sp, pos.add(0, 0.55, 0), 2.2 * EffectSprites.easeOutCubic(u), -u * 1.4, COL_GOLD_HOT, (int) (195 * fade));

        // Soaring golden sword-qi aura pillar (3 axial planes) & iaido blade flash
        if (!ownFp) {
            emitAuraPlume(sp, pos.add(0, 0.05, 0), 0.95 * (1.0 - 0.25 * u), 3.6 * (0.5 + 0.6 * EffectSprites.easeOutCubic(u)), time, COL_GOLD, (int) (215 * fade), time * 0.003);
            emitIaidoFlash(sp, pos.add(0, 1.35, 0), camera, 2.35, u, COL_GOLD_HOT, (int) (245 * fade));
        } else {
            Vec3 ahead = pos.add(horizontalDir(vector(t.start.direction())).scale(1.1)).add(0, 1.05, 0);
            emitIaidoFlash(sp, ahead, camera, 1.25, u, COL_GOLD_HOT, (int) (190 * fade));
        }
    }

    // --- Ghost Slash Charge (鬼斩蓄力) ---------------------------------------

    private static void renderGhostCharge(Track t, long age, long time, List<SpriteQuad> sp, Vec3 camera) {
        var mc = Minecraft.getInstance();
        Player actor = mc == null || mc.level == null ? null : mc.level.getPlayerByUUID(t.start.caster());
        Vec3 aim = PlayerVisualAnchor.chargeAim(actor, vector(t.start.direction()));
        var pose = actor == null ? null : Effects.VFX.playerPose(actor.getUUID(), time);
        boolean ownFp = isOwnFirstPerson(t.start.caster());
        Vec3 base = actor == null ? vector(t.start.origin()) : PlayerVisualAnchor.position(actor);
        Vec3 p = actor != null && pose != null && !ownFp
            ? PlayerBoneAnchor.weaponTip(actor, pose.frame(), 1.05)
            : base.add(aim.scale(1.05)).add(0, 1.15, 0);
        double charge = Math.clamp(age / 1000.0, 0.0, 1.0);
        int col = t.start.color();

        // Dark ghostly aura gathering around the blade + swirling dark-violet ground circle
        emitFormation(sp, base.add(0, 0.04, 0), 1.35 + 0.55 * charge, -time * 0.0032, col, (int) (145 + 75 * charge), true);
        emitAuraPlume(sp, p.subtract(0, 0.35, 0), 0.38 + 0.24 * charge, 0.95 + 0.55 * charge, time, col, (int) ((165 + 70 * charge) * (ownFp ? 0.65 : 1.0)), time * 0.005);
        EffectSprites.glow(sp, p, camera, (0.62 + 0.68 * charge) * (ownFp ? 0.65 : 1.0), EffectSprites.brighten(col, 0.35), 225);
        if (charge >= 0.98) {
            emitIaidoFlash(sp, p, camera, ownFp ? 0.85 : 1.35, (time % 420L) / 420.0, EffectSprites.brighten(col, 0.65), 235);
        }
    }

    // --- Draw Sword Windup (拔刀斩 0.4s 居合收刀蓄势) ------------------------

    private static void renderDrawWindup(Track t, long age, long time, List<SpriteQuad> sp, Vec3 camera) {
        double u = Math.clamp(age / 400.0, 0.0, 1.0);
        Vec3 origin = liveCasterPos(t.start.caster(), vector(t.start.origin()));
        int color = t.start.color();

        // Contracting ground wind-pressure sword formation
        emitFormation(sp, origin.add(0, 0.04, 0), 2.35 * (1.0 - 0.52 * u), u * 3.0, color, (int) (135 + 95 * u), true);
        EffectSprites.shockwave(sp, origin.add(0, 0.07, 0), 3.4 * (1.0 - 0.82 * u), -u * 2.4, EffectSprites.brighten(color, 0.45), (int) (185 * (0.4 + 0.6 * u)));
    }

    // --- Flying Sword Waves (地裂波动剑 / 剑舞终结波 / 短剑精通剑气) ---------

    private static void renderFlyingWave(Track t, long time, List<SpriteQuad> sp, Vec3 camera) {
        SkillVisualEvent start = t.start;
        String id = start.skill();
        Vec3 dir = horizontalDir(vector(t.last.direction()));
        Vec3 pos = vector(t.last.position());
        int color = start.color();
        double hScale = Math.max(0.65, start.horizontalScale());
        int frame = (int) Math.floorMod(time / 55L, 8L);

        if (SkillCatalog.WAVE.id.equals(id)) {
            if (AsuraVisuals.isEyeWave(start)) {
                AsuraVisuals.renderEyeLightWing(t, time, sp, camera);
                return;
            }
            // DNF 地裂波动剑: ground-skimming wide shattering fan wave + earth fissure wake!
            double r = 2.25 * hScale;
            Vec3 ground = pos.add(0, 0.08, 0);
            emitSwordWavePlane(sp, ground.add(0, 0.16, 0), dir, 0.0, r * 1.15, frame, color, 245, true);
            emitSwordWavePlane(sp, ground.add(0, 0.62, 0), dir, 82.0, r * 0.75, frame, EffectSprites.brighten(color, 0.35), 210, false);
            emitGroundFissure(sp, ground.subtract(dir.scale(0.65)), r * 0.90, Math.atan2(dir.z, dir.x), color, 185);
            EffectSprites.shockwave(sp, ground, r * 0.85, time * 0.008, EffectSprites.brighten(color, 0.5), 175);
            RibbonTrail.push("wave_ground:" + start.cast(), ground.add(0, 0.28, 0), 0.46, color, 205, 220L, time);
        } else if (SkillCatalog.DANCE.id.equals(id)) {
            // DNF 幻影剑舞终结巨型剑气浪: 3 coaxial crescent planes evenly spaced every 60° (0° / 60° / 120°)!
            double r = 3.45 * hScale;
            Vec3 center = pos.add(0, 1.20, 0);
            int planeCount = 3;
            for (int i = 0; i < planeCount; i++) {
                double rollDeg = i * (180.0 / planeCount);
                int planeColor = i == 0 ? color : EffectSprites.brighten(color, 0.35);
                int planeAlpha = i == 0 ? 255 : 235;
                emitSwordWavePlane(sp, center, dir, rollDeg, r, (frame + i * 3) % 8, planeColor, planeAlpha, true);
            }
            EffectSprites.shockwave(sp, pos.add(0, 0.10, 0), r * 0.80, time * 0.007, color, 190);
            RibbonTrail.push("dance_wave:" + start.cast(), center, r * 0.34, color, 230, 250L, time);
            if ("LIGHTSABER".equals(start.weaponType())) {
                LightningBolt.helixArcs(sp, center.subtract(dir.scale(2.0)), center.add(dir.scale(0.7)), camera, time, 0.85, 2, COL_LIGHT_SPARK, 215);
            }
        } else if (start.sweep()) {
            // 武器精通神话强化短剑剑气: 幻影剑舞级别巨型三面剑气浪!
            double r = 3.45 * Math.max(1.0, hScale);
            Vec3 center = pos.add(0, 1.20, 0);
            for (int i = 0; i < 3; i++) {
                double rollDeg = i * 60.0;
                int planeColor = i == 0 ? color : EffectSprites.brighten(color, 0.35);
                emitSwordWavePlane(sp, center, dir, rollDeg, r, (frame + i * 3) % 8, planeColor, i == 0 ? 255 : 235, true);
            }
            EffectSprites.shockwave(sp, pos.add(0, 0.10, 0), r * 0.80, time * 0.007, color, 190);
            RibbonTrail.push("mwave_giant:" + start.cast(), center, r * 0.34, color, 230, 240L, time);
        } else {
            // 短剑精通追加飞剑气 (MASTERY_WAVE): tilted razor crescent wave cutting through air!
            double r = 2.35 * hScale;
            Vec3 center = pos.add(0, 1.08, 0);
            emitSwordWavePlane(sp, center, dir, start.slashAngle(), r, frame, color, 245, true);
            emitSwordWavePlane(sp, center, dir, start.slashAngle() + 90.0, r * 0.60, frame, EffectSprites.brighten(color, 0.45), 175, false);
            RibbonTrail.push("mwave:" + start.cast(), center, r * 0.24, color, 200, 190L, time);
        }
    }

    // --- Active Flash Events (START / SEGMENT / RELEASE / HIT) ---------------

    private static void renderFlash(
        List<cn.yumis.fight.modules.presentation.api.client.WorldQuads.Quad> quads,
        List<SpriteQuad> sp, Vec3 camera, SkillVisualEvent e, long age, long duration, long now
    ) {
        Vec3 dir = horizontalDir(vector(e.direction()));
        Vec3 right = rightOf(dir);
        Vec3 p = vector(e.position()).add(dir.scale(e.forwardOffset()));
        double progress = Math.clamp(age / (double) Math.max(1L, duration), 0.0, 0.999);
        double fade = 1.0 - progress;
        int color = e.color();
        double hScale = Math.max(0.5, e.horizontalScale());
        String weapon = e.weaponType();
        double wScale = weaponRadiusMult(weapon);

        switch (e.skill()) {
            case "ATTACK" -> {
                if (e.phase() != START) return;
                long baseDur = basicSingleDuration(e);
                if (BerserkerVisuals.isFrenzyBasic(e)) {
                    BerserkerVisuals.renderFrenzyBasic(quads, sp, camera, e, age, baseDur);
                    return;
                }
                if (SoulBenderVisuals.isKeigaBasic(e)) {
                    SoulBenderVisuals.renderKeigaBasic(sp, camera, e, age, baseDur);
                    return;
                }
                if (BattleMageVisuals.isBattleMageBasic(e)) {
                    BattleMageVisuals.renderBasic(sp, camera, e, age, baseDur);
                    return;
                }
                if (WitchVisuals.isBroomBasic(e)) {
                    WitchVisuals.renderBroomBasic(sp, camera, e, age, baseDur);
                    return;
                }
                SplittableRandom rng = new SplittableRandom(e.seed());
                boolean flip = rng.nextBoolean();
                if (e.sweep()) {
                    var sample = BuiltinSkillVisuals.basicSlashSample(age, baseDur);
                    if (sample == null) return;
                    double slashProg = sample.progress();
                    double alphaScale = sample.alphaScale();
                    // Greatsword charged cleave (matches 6.8-block actual sweep reach!)
                    double r = 6.35 * hScale;
                    Vec3 c = p.add(dir.scale(1.25)).add(0, 1.15, 0);
                    int aMain = (int) Math.round(252 * alphaScale);
                    int aSub = (int) Math.round(215 * alphaScale);
                    if (aMain > 4) emitSwordSlash(sp, c, dir, flip ? -8 : 8, r, slashProg, flip, color, aMain, true);
                    if (aSub > 4) emitSwordSlash(sp, c.add(0, 0.16, 0), dir, flip ? 16 : -16, r * 0.90, Math.min(0.999, slashProg * 1.06), !flip, EffectSprites.brighten(color, 0.45), aSub, false);
                    int aWave = (int) Math.round(215 * alphaScale);
                    int aFissure = (int) Math.round(195 * alphaScale);
                    if (aWave > 4) EffectSprites.shockwave(sp, p.add(dir.scale(1.6)).add(0, 0.08, 0), r * 0.92 * EffectSprites.easeOutCubic(progress), progress * 1.4, color, aWave);
                    if (aFissure > 4) emitGroundFissure(sp, p.add(dir.scale(2.8)).add(0, 0.04, 0), 3.35, Math.atan2(dir.z, dir.x), color, aFissure);
                } else if (e.dual()) {
                    // Alternating single lightsaber slash per dual-blade strike (matches 4.5-block reach!)
                    var sample = BuiltinSkillVisuals.basicSlashSample(age, baseDur);
                    if (sample == null) return;
                    boolean offhandStrike = BuiltinSkillVisuals.isDualOffhandStrike(e);
                    var mc = Minecraft.getInstance();
                    Player caster = mc == null || mc.level == null ? null : mc.level.getPlayerByUUID(e.caster());
                    boolean leftMainArm = caster != null && caster.getMainArm() == net.minecraft.world.entity.HumanoidArm.LEFT;
                    boolean leftCut = offhandStrike ^ leftMainArm;
                    double tiltMag = 26.0 + rng.nextDouble() * 14.0;
                    double tilt = leftCut ? -tiltMag : tiltMag;
                    boolean mirror = !leftCut;
                    Vec3 c = p.add(dir.scale(1.05)).add(0, leftCut ? 1.09 : 1.21, 0);
                    double r = (leftCut ? 2.82 : 2.75) * hScale;
                    int alpha = (int) Math.round(244 * sample.alphaScale());
                    int slashCol = offhandStrike ? EffectSprites.brighten(color, 0.30) : color;
                    if (alpha > 4) {
                        emitSwordSlash(sp, c, dir, tilt, r, sample.progress(), mirror, slashCol, alpha, true);
                    }
                    if (age < 180L) {
                        double bf = 1.0 - age / 180.0;
                        double sideSign = leftCut ? -1.0 : 1.0;
                        Vec3 a = c.add(right.scale(1.25 * sideSign)).add(dir.scale(0.85)).add(0, 0.25, 0);
                        Vec3 b = c.add(right.scale(-1.25 * sideSign)).add(dir.scale(1.30)).add(0, -0.20, 0);
                        LightningBolt.bolt(sp, a, b, camera, e.seed() ^ (now / 45L), 5, 0.22, 0.044, COL_LIGHT_SPARK, (int) (210 * bf), false);
                    }
                    if (offhandStrike && caster != null && !isOwnFirstPerson(e.caster()) && age < 220L) {
                        var pose = Effects.VFX.playerPose(caster.getUUID(), now);
                        Vec3 offHand = PlayerBoneAnchor.hand(caster, pose == null ? null : pose.frame(), false);
                        double glowScale = Math.max(0.25, 0.85 * (1.0 - age / 220.0));
                        int glowAlpha = (int) Math.round(215 * Math.min(1.0, glowScale));
                        if (glowAlpha > 4) {
                            EffectSprites.glow(sp, offHand, camera, 0.46 * glowScale, EffectSprites.brighten(color, 0.5), glowAlpha);
                        }
                        Vec3 offTip = offHand.add(dir.scale(1.15)).add(right.scale(leftMainArm ? 0.22 : -0.22));
                        RibbonTrail.push("sm_off:" + e.cast(), offTip, 0.14, EffectSprites.brighten(color, 0.45), 205, 165L, now);
                    }
                } else {
                    var sample = BuiltinSkillVisuals.basicSlashSample(age, baseDur);
                    if (sample == null) return;
                    double slashProg = sample.progress();
                    double alphaScale = sample.alphaScale();
                    // Weapon-mastery flavored basic sword slash (matches 4.5-block reach!)
                    double roll = (flip ? 1 : -1) * (18.0 + rng.nextDouble() * 44.0);
                    Vec3 c = p.add(dir.scale(0.95)).add(0, 1.14, 0);
                    double r = 2.65 * hScale * wScale;
                    int aMain = (int) Math.round(240 * alphaScale);
                    if (aMain > 4) emitSwordSlash(sp, c, dir, roll, r, slashProg, flip, color, aMain, true);
                    if ("KATANA".equals(weapon)) {
                        // Sleek katana secondary thin inner cut gleam
                        int aSub = (int) Math.round(190 * alphaScale);
                        if (aSub > 4) emitSwordSlash(sp, c.add(dir.scale(0.24)), dir, roll * 0.85, r * 1.08, Math.min(0.999, slashProg * 1.10), flip, EffectSprites.brighten(color, 0.55), aSub, false);
                    } else if ("BLUNT".equals(weapon)) {
                        int aWave = (int) Math.round(190 * alphaScale);
                        if (aWave > 4) EffectSprites.shockwave(sp, p.add(dir.scale(1.5)).add(0, 0.12, 0), 2.35 * EffectSprites.easeOutCubic(progress), progress * 1.2, color, aWave);
                    } else if ("LIGHTSABER".equals(weapon) && progress < 0.55) {
                        LightningBolt.bolt(sp, c.subtract(right.scale(0.9)), c.add(right.scale(1.0)).add(dir.scale(0.9)), camera, e.seed() ^ (now / 50L), 4, 0.22, 0.040, COL_LIGHT_SPARK, (int) (190 * fade), false);
                    }
                }
            }
            case "yumis_dungeon_and_fight:mastery" -> {
                Vec3 pt = p.add(dir.scale(0.7)).add(0, 1.08, 0);
                emitIaidoFlash(sp, pt, camera, 1.05, progress, color, (int) (230 * fade));
            }
            case "yumis_dungeon_and_fight:ghost" -> {
                if (e.phase() != RELEASE) return;
                // DNF 鬼斩: heavy ragged dark-violet spectral crescent (matches 3.8 * 1.5 = 5.7-block hitbox!)
                double r = (3.45 + 1.15 * e.charge()) * hScale;
                Vec3 c = p.add(dir.scale(1.75)).add(0, 1.22, 0);
                emitGhostCrescent(sp, c, dir, 76.0, r, progress, false, color, 254);
                emitGhostCrescent(sp, c.add(0, 0.10, 0), dir, 18.0, r * 0.88, Math.min(0.999, progress * 1.08), true, color, 200);
                EffectSprites.impactBurst(sp, p.add(dir.scale(2.65)).add(0, 1.15, 0), camera, 2.35 + 0.85 * e.charge(), progress, color, 235, false);
                EffectSprites.shockwave(sp, p.add(dir.scale(2.15)).add(0, 0.08, 0), (2.9 + 1.1 * e.charge()) * EffectSprites.easeOutCubic(progress), progress * 1.4, color, (int) (220 * fade));
            }
            case "yumis_dungeon_and_fight:triple" -> {
                if (e.phase() != SEGMENT) return;
                int seg = e.sequence();
                boolean flip = (seg & 1) == 1;
                double roll = (seg % 3 == 2) ? 74.0 : (flip ? -30.0 : 30.0);
                double r = 2.95 * hScale * wScale;
                Vec3 c = p.add(dir.scale(1.35)).add(0, 1.18, 0);
                emitSwordSlash(sp, c, dir, roll, r, progress, flip, color, 248, true);
                emitGaleCone(sp, p.add(dir.scale(1.85)).add(0, 1.08, 0), dir, 1.25, 3.25, progress, color, 220);
                if ("LIGHTSABER".equals(weapon) && progress < 0.65) {
                    LightningBolt.bolt(sp, p.add(0, 1.0, 0), p.add(dir.scale(3.1)).add(0, 1.1, 0), camera, e.seed() ^ seg ^ (now / 45L), 6, 0.34, 0.052, COL_LIGHT_SPARK, (int) (225 * fade), false);
                }
            }
            case "yumis_dungeon_and_fight:rising" -> {
                if (e.phase() == START) {
                    // Initial gale-rush launch ring
                    EffectSprites.shockwave(sp, p.add(0, 0.12, 0), (e.sweep() ? 3.2 : 2.55) * EffectSprites.easeOutCubic(progress), progress * 1.3, color, (int) (210 * fade));
                } else if (e.phase() == SEGMENT) {
                    // 破军斩龙击冲锋三连斩
                    int seg = e.sequence();
                    boolean flip = (seg & 1) == 1;
                    double r = 3.55 * hScale * wScale;
                    Vec3 c = p.add(dir.scale(1.6)).add(0, 1.18, 0);
                    emitSwordSlash(sp, c, dir, flip ? -34.0 : 34.0, r, progress, flip, color, 250, true);
                    emitGaleCone(sp, p.add(dir.scale(1.95)).add(0, 1.12, 0), dir, 1.55 * wScale, 3.85, progress, color, 225);
                } else if (e.phase() == HIT) {
                    // 破军斩龙击升龙挑击
                    double r = 4.05 * hScale * wScale;
                    Vec3 c = p.add(dir.scale(1.45)).add(0, 1.65, 0);
                    emitRisingArc(sp, c, dir, r, progress, color, 254);
                    emitAuraPlume(sp, p.add(dir.scale(1.4)).add(0, 0.05, 0), 1.35, 4.4 * (0.45 + 0.65 * EffectSprites.easeOutCubic(progress)), now, color, (int) (225 * fade), now * 0.0045);
                    emitIaidoFlash(sp, p.add(dir.scale(1.85)).add(0, 3.45, 0), camera, 2.55, progress, EffectSprites.brighten(color, 0.65), (int) (245 * fade));
                } else if (e.phase() == RELEASE) {
                    // DNF 破军升龙击上挑: towering vertical rising dragon crescent + ground fissure crater (matches 4.8x4.8 hitbox!)
                    double r = 3.65 * hScale * wScale;
                    Vec3 c = p.add(dir.scale(1.35)).add(0, 1.55, 0);
                    emitRisingArc(sp, c, dir, r, progress, color, 254);
                    emitSwordSlash(sp, c.add(0, -0.10, 0), dir, 78.0, r * 0.90, Math.min(0.999, progress * 1.08), false, EffectSprites.brighten(color, 0.45), 220, false);
                    emitAuraPlume(sp, p.add(dir.scale(1.4)).add(0, 0.05, 0), 1.25, 4.15 * (0.45 + 0.65 * EffectSprites.easeOutCubic(progress)), now, color, (int) (220 * fade), now * 0.004);
                    emitGroundFissure(sp, p.add(dir.scale(1.25)).add(0, 0.04, 0), 3.35 * wScale, Math.atan2(dir.z, dir.x), color, (int) (230 * fade));
                    EffectSprites.shockwave(sp, p.add(dir.scale(1.25)).add(0, 0.09, 0), 4.2 * wScale * EffectSprites.easeOutCubic(progress), progress * 1.6, color, (int) (240 * fade));
                    emitIaidoFlash(sp, p.add(dir.scale(1.75)).add(0, 3.25, 0), camera, 2.35, progress, EffectSprites.brighten(color, 0.65), (int) (240 * fade));
                } else if (e.phase() == FINISH) {
                    // DNF 破军斩龙击终结重劈 + 5.5格斩龙冲击波!
                    Vec3 slamCenter = p.add(dir.scale(2.8));
                    double r = 5.5 * wScale;
                    emitSwordSlash(sp, slamCenter.add(0, 1.65, 0), dir, 86.0, r * 0.95, progress, false, color, 255, true);
                    emitSwordSlash(sp, slamCenter.add(0, 1.35, 0), dir, -82.0, r * 0.88, Math.min(0.999, progress * 1.06), true, EffectSprites.brighten(color, 0.55), 235, true);
                    emitDragonHead(sp, slamCenter.add(0, 1.85 - 0.95 * progress, 0), dir, 3.15 * wScale, (int) Math.floorMod(now / 45L, 8L), color, (int) (250 * fade));
                    emitGroundFissure(sp, slamCenter.add(0, 0.05, 0), r * 1.02, Math.atan2(dir.z, dir.x), color, (int) (245 * fade));
                    EffectSprites.shockwave(sp, slamCenter.add(0, 0.10, 0), r * 1.08 * EffectSprites.easeOutCubic(progress), progress * 1.9, color, (int) (250 * fade));
                    EffectSprites.shockwave(sp, slamCenter.add(0, 0.75, 0), r * 0.82 * EffectSprites.easeOutCubic(progress), -progress * 1.5, EffectSprites.brighten(color, 0.60), (int) (215 * fade));
                    EffectSprites.impactBurst(sp, slamCenter.add(0, 1.25, 0), camera, 4.6, progress, color, 240, false);
                    emitIaidoFlash(sp, slamCenter.add(0, 1.45, 0), camera, 3.85, progress, COL_WHITE, (int) (250 * fade));
                }
            }
            case "yumis_dungeon_and_fight:meteor_rise" -> {
                if (e.phase() == HIT) {
                    double r = 3.1 * hScale * wScale;
                    emitSwordSlash(sp, p.add(dir.scale(1.25)).add(0, 1.15, 0), dir, 48.0, r, progress, false, color, 244, true);
                } else if (e.phase() == RELEASE) {
                    double r = 3.85 * hScale * wScale;
                    Vec3 c = p.add(dir.scale(1.35)).add(0, 1.75, 0);
                    emitRisingArc(sp, c, dir, r, progress, color, 255);
                    emitSwordSlash(sp, c.add(0, 0.15, 0), dir, -62.0, r * 0.86, Math.min(0.999, progress * 1.08), true, EffectSprites.brighten(color, 0.50), 225, false);
                    EffectSprites.shockwave(sp, p.add(0, 0.10, 0), 3.6 * EffectSprites.easeOutCubic(progress), progress * 1.7, color, (int) (220 * fade));
                    emitIaidoFlash(sp, p.add(dir.scale(1.75)).add(0, 3.35, 0), camera, 2.35, progress, EffectSprites.brighten(color, 0.65), (int) (240 * fade));
                }
            }
            case "yumis_dungeon_and_fight:meteor_leap" -> {
                if (e.phase() == HIT) {
                    double r = 3.0 * hScale * wScale;
                    emitSwordSlash(sp, p.add(dir.scale(1.2)).add(0, 1.35, 0), dir, -36.0, r, progress, true, color, 238, true);
                    emitGaleCone(sp, p.add(dir.scale(1.45)).add(0, 1.20, 0), dir, 1.35, 3.2, progress, color, 210);
                } else if (e.phase() == RELEASE) {
                    Vec3 land = p.add(dir.scale(1.6));
                    double r = 4.4 * wScale;
                    emitSwordSlash(sp, land.add(0, 1.35, 0), dir, 82.0, r * 0.92, progress, false, color, 252, true);
                    emitSwordSlash(sp, land.add(0, 0.95, 0), dir, 12.0, r * 0.96, Math.min(0.999, progress * 1.05), true, EffectSprites.brighten(color, 0.50), 232, false);
                    emitGroundFissure(sp, land.add(0, 0.05, 0), r * 0.96, Math.atan2(dir.z, dir.x), color, (int) (235 * fade));
                    EffectSprites.shockwave(sp, land.add(0, 0.09, 0), r * 1.04 * EffectSprites.easeOutCubic(progress), progress * 1.8, color, (int) (242 * fade));
                    emitIaidoFlash(sp, land.add(0, 1.15, 0), camera, 2.85, progress, EffectSprites.brighten(color, 0.65), (int) (245 * fade));
                }
            }
            case "yumis_dungeon_and_fight:meteor_pierce" -> {
                if (e.phase() == HIT || e.phase() == RELEASE) {
                    boolean last = e.phase() == RELEASE;
                    int idx = e.sequence();
                    double r = (last ? 4.2 : 3.4) * hScale * wScale;
                    double roll = last ? 0.0 : ((idx & 1) == 0 ? 18.0 : -18.0);
                    Vec3 tip = p.add(dir.scale(last ? 4.8 : 3.9)).add(0, 1.16, 0);
                    emitGaleCone(sp, p.add(dir.scale(last ? 3.8 : 3.0)).add(0, 1.16, 0), dir, last ? 1.45 : 1.05, last ? 5.6 : 4.5, progress, color, 248);
                    emitSwordSlash(sp, p.add(dir.scale(2.1)).add(0, 1.16, 0), dir, roll, r * 0.85, progress, (idx & 1) == 1, EffectSprites.brighten(color, 0.45), 225, true);
                    emitIaidoFlash(sp, tip, camera, last ? 2.6 : 1.85, progress, EffectSprites.brighten(color, 0.70), (int) (245 * fade));
                    if ("LIGHTSABER".equals(weapon) && progress < 0.7) {
                        LightningBolt.bolt(sp, p.add(0, 1.15, 0), tip, camera, e.seed() ^ idx ^ (now / 40L), 6, 0.26, 0.048, COL_LIGHT_SPARK, (int) (230 * fade), false);
                    }
                }
            }
            case "yumis_dungeon_and_fight:draw" -> {
                if (e.phase() == RELEASE || e.phase() == HIT) {
                    boolean followup = e.phase() == HIT;
                    if (e.dual() && !followup) {
                        // 连闪拔刀 (3/6道剑舞剑气发射起手居合闪光，无360°弧形环)
                        Vec3 center = p.add(dir.scale(1.6)).add(0, 1.18, 0);
                        double r = 3.9 * hScale * wScale;
                        emitSwordSlash(sp, center, dir, 6.0, r, progress, false, color, 250, true);
                        emitGaleCone(sp, p.add(dir.scale(2.3)).add(0, 1.18, 0), dir, 1.65, 4.2, progress, color, 232);
                        emitIaidoFlash(sp, center.add(dir.scale(0.8)), camera, 3.1, progress, COL_WHITE, (int) (252 * fade));
                        EffectSprites.shockwave(sp, p.add(0, 0.10, 0), 4.0 * EffectSprites.easeOutCubic(progress), progress * 1.6, color, (int) (225 * fade));
                    } else if (!followup) {
                        // Stage 1 (拔刀主斩): snappy 290ms 360° clockwise iaido ring matching 9.0-block radius!
                        double r = 6.35 * hScale * wScale;
                        Vec3 center = p.add(0, 1.02, 0);
                        emitDrawRing(sp, center, r, progress, 0.0, color, 255);
                        emitSwordSlash(sp, center.add(0, 0.08, 0), dir, 8.0, r * 0.96, progress, false, color, 232, true);
                        emitSwordSlash(sp, center.add(0, -0.08, 0), dir.scale(-1), -8.0, r * 0.96, progress, true, color, 216, false);
                        EffectSprites.shockwave(sp, p.add(0, 0.09, 0), r * 1.06 * EffectSprites.easeOutCubic(progress), progress * 1.8, color, (int) (235 * fade));
                        if ("BLUNT".equals(weapon) || "GREATSWORD".equals(weapon)) {
                            emitGroundFissure(sp, p.add(0, 0.04, 0), r * 0.65, e.seed() * 0.01, color, (int) (185 * fade));
                        }
                    } else {
                        // Stage 2 (光剑/太刀专属二段光斩!): visibly elevated (y+1.52), wider outer ring (1.16x), counter-sweeping
                        // blinding white-gold & electric-cyan cross-slash + 8-segment outer lightning perimeter!
                        double r = 7.25 * hScale * Math.max(1.06, wScale);
                        Vec3 center = p.add(0, 1.52, 0);
                        int stage2Col = "LIGHTSABER".equals(weapon) ? 0xE6FBFF : 0xFFF7CE;
                        emitDrawRing(sp, center, r, progress, Math.PI * 0.65 - progress * 0.45, stage2Col, 255);
                        emitSwordSlash(sp, center.add(0, 0.12, 0), dir, -18.0, r * 0.98, progress, true, COL_WHITE, 245, true);
                        emitSwordSlash(sp, center.add(0, -0.12, 0), dir.scale(-1), 18.0, r * 0.98, progress, false, COL_LIGHT_FOLLOW, 238, true);
                        for (int k = 0; k < 8; k++) {
                            double a0 = k * (Math.TAU / 8.0) - progress * 0.9;
                            double a1 = a0 + Math.TAU / 8.0;
                            Vec3 v0 = center.add(Math.cos(a0) * r * 0.90, 0.14, Math.sin(a0) * r * 0.90);
                            Vec3 v1 = center.add(Math.cos(a1) * r * 0.90, -0.14, Math.sin(a1) * r * 0.90);
                            LightningBolt.bolt(sp, v0, v1, camera, e.seed() ^ (now / 42L) ^ (k * 37L), 5, 0.42, 0.072, COL_LIGHT_SPARK, (int) (245 * fade), false);
                        }
                        EffectSprites.shockwave(sp, p.add(0, 0.14, 0), r * 1.10 * EffectSprites.easeOutCubic(progress), -progress * 2.1, stage2Col, (int) (245 * fade));
                    }
                }
            }
            case "yumis_dungeon_and_fight:dragon" -> {
                if (e.phase() != SEGMENT) return;
                int seg = e.sequence();
                boolean flip = (seg & 1) == 1;
                double r = 3.65 * hScale * wScale;
                Vec3 c = p.add(dir.scale(1.75)).add(0, 1.18, 0);
                emitSwordSlash(sp, c, dir, flip ? -28.0 : 28.0, r, progress, flip, color, 250, true);
                emitDragonHead(sp, p.add(dir.scale(2.15)).add(0, 1.24, 0), dir, 2.45 * wScale, (int) Math.floorMod(now / 55L + seg, 8L), color, (int) (248 * fade));
                emitGaleCone(sp, p.add(dir.scale(1.45)).add(0, 1.15, 0), dir, 1.80 * wScale, 4.15, progress, color, 230);
                EffectSprites.shockwave(sp, p.add(0, 0.10, 0), 3.15 * EffectSprites.easeOutCubic(progress), progress * 1.5, color, (int) (215 * fade));
            }
            case "yumis_dungeon_and_fight:wave" -> {
                if (e.phase() != START) return;
                // DNF 地裂波动剑 frame-0 ground-cleave launch burst
                double r = 3.15 * hScale * wScale;
                emitSwordSlash(sp, p.add(dir.scale(1.35)).add(0, 0.48, 0), dir, 8.0, r, progress, false, color, 246, true);
                emitGroundFissure(sp, p.add(dir.scale(1.65)).add(0, 0.04, 0), 2.55 * hScale, Math.atan2(dir.z, dir.x), color, (int) (220 * fade));
                EffectSprites.shockwave(sp, p.add(dir.scale(1.0)).add(0, 0.08, 0), 2.95 * EffectSprites.easeOutCubic(progress), progress * 1.4, color, (int) (220 * fade));
            }
            case "yumis_dungeon_and_fight:dance" -> {
                if (e.phase() == RELEASE) {
                    // Instant explosive launch swing for the Illusion Sword Dance finisher wave!
                    double r = 4.25 * hScale * wScale;
                    Vec3 c = p.add(dir.scale(1.65)).add(0, 1.22, 0);
                    emitSwordSlash(sp, c, dir, 0.0, r, progress, false, color, 255, true);
                    emitSwordSlash(sp, c.add(0, 0.16, 0), dir, 18.0, r * 0.90, Math.min(0.999, progress * 1.08), true, EffectSprites.brighten(color, 0.50), 225, false);
                    EffectSprites.shockwave(sp, p.add(0, 0.09, 0), 4.4 * EffectSprites.easeOutCubic(progress), progress * 1.6, color, (int) (240 * fade));
                    emitIaidoFlash(sp, c.add(dir.scale(1.0)), camera, 2.75, progress, EffectSprites.brighten(color, 0.65), (int) (248 * fade));
                    return;
                }
                if (e.phase() != HIT) return;
                // DNF 幻影剑舞: rapid criss-crossing multi-angle mirage slashes (matches 7.2x5.7 hitbox!)
                SplittableRandom rng = new SplittableRandom(e.seed() ^ ((long) e.sequence() * 0x9E3779B97F4A7C15L));
                boolean flip = (e.sequence() & 1) == 1;
                double roll = rng.nextDouble(-68.0, 68.0);
                double yawOff = Math.toRadians(rng.nextDouble(-24.0, 24.0));
                Vec3 sDir = dir.yRot((float) yawOff).normalize();
                double r = 3.85 * hScale * wScale;
                Vec3 c = p.add(sDir.scale(1.35 + rng.nextDouble(0.0, 0.55))).add(0, 1.08 + rng.nextDouble(-0.24, 0.36), 0);
                emitSwordSlash(sp, c, sDir, roll, r, progress, flip, color, 246, true);
                if ("KATANA".equals(weapon) || "LIGHTSABER".equals(weapon)) {
                    // Rapid mirage cross-echo blade!
                    emitSwordSlash(sp, c.add(0, 0.08, 0), sDir, -roll * 0.8, r * 0.88, Math.min(0.999, progress * 1.12), !flip, EffectSprites.brighten(color, 0.50), 200, false);
                }
                if ((e.sequence() % 3) == 0) {
                    Vec3 sparkAt = p.add(dir.scale(2.45 + rng.nextDouble(-0.5, 0.7))).add(right.scale(rng.nextDouble(-1.2, 1.2))).add(0, 1.18 + rng.nextDouble(-0.25, 0.35), 0);
                    emitIaidoFlash(sp, sparkAt, camera, 1.75, progress, EffectSprites.brighten(color, 0.65), 238);
                }
                if ("LIGHTSABER".equals(weapon) && (e.sequence() & 1) == 0 && progress < 0.65) {
                    Vec3 a = c.subtract(right.scale(1.55));
                    Vec3 b = c.add(right.scale(1.55)).add(dir.scale(0.95));
                    LightningBolt.bolt(sp, a, b, camera, e.seed() ^ e.sequence() ^ (now / 45L), 5, 0.32, 0.050, COL_LIGHT_SPARK, (int) (220 * fade), false);
                }
            }
            default -> {}
        }
    }

    // --- Dash Trails (三段斩 / 破军冲撞 / 猛龙咆哮龙影) ----------------------

    private static void renderTrail(Track t, long now, List<SpriteQuad> sp, Vec3 camera) {
        SkillVisualEvent e = t.last;
        String id = e.skill();
        Vec3 p = vector(e.position());
        Vec3 dir = horizontalDir(vector(e.direction()));
        int color = e.color();
        String weapon = e.weaponType();
        double wScale = weaponRadiusMult(weapon);

        if (SkillCatalog.DRAGON.id.equals(id)) {
            // DNF 猛龙断空斩: Roaring Dragon Head + Spiraling Hurricane Cone + Crackling Wind Lightning!
            int dragonFrame = (int) Math.floorMod(now / 48L, 8L);
            Vec3 headPos = p.add(dir.scale(1.15)).add(0, 1.24, 0);
            emitDragonHead(sp, headPos, dir, 2.55 * wScale, dragonFrame, color, 252);
            emitGaleCone(sp, p.add(dir.scale(0.45)).add(0, 1.16, 0), dir, 1.85 * wScale, 4.35, (now % 260L) / 260.0, color, 230);
            RibbonTrail.push("dragon_core:" + e.cast(), p.add(0, 1.16, 0), 0.66 * wScale, color, 236, 270L, now);
            int arcs = "LIGHTSABER".equals(weapon) ? 3 : 2;
            LightningBolt.helixArcs(sp, p.subtract(dir.scale(2.8)).add(0, 1.16, 0), headPos, camera, now, 0.92 * wScale, arcs, EffectSprites.brighten(color, 0.45), 225);

            // Staggered dash afterimage every 110ms
            long lastAi = LAST_DRAGON_AFTERIMAGE.getOrDefault(e.cast(), 0L);
            if (now - lastAi >= 110L && !isOwnFirstPerson(e.caster())) {
                LAST_DRAGON_AFTERIMAGE.put(e.cast(), now);
                var mc = Minecraft.getInstance();
                Player actor = mc == null || mc.level == null ? null : mc.level.getPlayerByUUID(e.caster());
                if (actor != null) PlayerAfterimages.captureAt(actor, p, dir, color, 160, 260L, true);
            }
        } else if (SkillCatalog.RISING.id.equals(id) && !t.released) {
            if (e.sweep()) {
                int dragonFrame = (int) Math.floorMod(now / 48L, 8L);
                emitDragonHead(sp, p.add(dir.scale(1.1)).add(0, 1.24, 0), dir, 2.4 * wScale, dragonFrame, color, 248);
                emitGaleCone(sp, p.add(dir.scale(0.8)).add(0, 1.15, 0), dir, 1.75 * wScale, 4.0, (now % 240L) / 240.0, color, 236);
                RibbonTrail.push("rising_slay:" + e.cast(), p.add(0, 1.14, 0), 0.56 * wScale, color, 235, 250L, now);
            } else {
                // DNF 破军升龙击冲撞期: forward wind-shield gale cone!
                Vec3 nose = p.add(dir.scale(0.95)).add(0, 1.12, 0);
                emitGaleCone(sp, nose, dir, 1.52 * wScale, 3.45, (now % 240L) / 240.0, color, 240);
                RibbonTrail.push("rising_dash:" + e.cast(), p.add(0, 1.08, 0), 0.42, color, 220, 220L, now);
            }
        } else if (SkillCatalog.METEOR_LEAP.id.equals(id) && !t.released) {
            emitGaleCone(sp, p.add(dir.scale(0.85)).add(0, 1.15, 0), dir, 1.25 * wScale, 3.0, (now % 220L) / 220.0, color, 225);
            RibbonTrail.push("meteor_leap:" + e.cast(), p.add(0, 1.15, 0), 0.40, color, 225, 220L, now);
        } else if (SkillCatalog.TRIPLE.id.equals(id)) {
            RibbonTrail.push("triple_dash:" + e.cast(), p.add(0, 1.08, 0), 0.34, color, 215, 200L, now);
        }
    }

    // --- 极意拔刀 20格弧形扩大剑气 (SM_DRAW_ARC) -----------------------------

    private static void renderDrawArcWave(Track t, long age, long time, List<SpriteQuad> sp, Vec3 camera) {
        long dur = Math.max(1L, t.start.duration() - 120L);
        if (age < 0 || age > dur + 120L) return;
        double u = Math.clamp(age / (double) dur, 0.0, 0.999);
        double fade = age <= dur ? (1.0 - 0.35 * u * u) : Math.clamp(1.0 - (age - dur) / 120.0, 0.0, 1.0);
        if (fade <= 0.02) return;
        Vec3 origin = vector(t.start.origin());
        double maxR = Math.max(6.0, t.start.horizontalScale() * 10.0);
        double curR = 1.5 + (maxR - 1.5) * u;
        int color = t.start.color();
        int frame = (int) Math.floorMod(time / 55L, 8L);

        emitDrawRing(sp, origin.add(0, 1.02, 0), curR, Math.clamp(u * 0.92, 0.0, 0.99), u * 1.4, color, (int) (245 * fade));
        EffectSprites.shockwave(sp, origin.add(0, 0.14, 0), curR * 1.03, u * 2.2, EffectSprites.brighten(color, 0.45), (int) (230 * fade));
        // 8 radial expanding crescent sword-qi blades surfing along the 20-block expanding perimeter!
        for (int i = 0; i < 8; i++) {
            double ang = i * (Math.TAU / 8.0) + u * 0.65;
            Vec3 radial = new Vec3(Math.cos(ang), 0, Math.sin(ang));
            Vec3 pos = origin.add(radial.scale(curR * 0.96)).add(0, 1.05, 0);
            double waveSize = 2.4 + 1.8 * u;
            emitSwordWavePlane(sp, pos, radial, 0.0, waveSize, (frame + i) % 8, i % 2 == 0 ? color : EffectSprites.brighten(color, 0.40), (int) (235 * fade), true);
        }
    }

    // --- 飓风猛龙追踪旋风 (SM_DRAGON_WHIRLWIND) ------------------------------

    private static void renderDragonWhirlwind(Track t, long age, long time, List<SpriteQuad> sp, Vec3 camera) {
        long dur = Math.max(500L, t.start.duration() - 400L);
        double lifeFade = age > dur - 450L ? Math.clamp((dur - age) / 450.0, 0.0, 1.0) : Math.clamp(age / 160.0, 0.0, 1.0);
        if (lifeFade <= 0.02) return;
        Vec3 base = vector(t.last.position());
        double r = Math.max(1.2, t.start.horizontalScale());
        int color = t.start.color();
        double spin = time * 0.012;

        emitFormation(sp, base.add(0, 0.06, 0), r * 1.08, spin * 0.5, color, (int) (200 * lifeFade), true);
        EffectSprites.shockwave(sp, base.add(0, 0.12, 0), r * (0.75 + 0.35 * ((time % 380L) / 380.0)), spin, EffectSprites.brighten(color, 0.40), (int) (185 * lifeFade));
        emitAuraPlume(sp, base.add(0, 0.05, 0), r * 0.55, 3.2, time, color, (int) (195 * lifeFade), spin);

        // 3 swirling tornado ribbon helixes + rotating blade rings
        for (int strand = 0; strand < 3; strand++) {
            double a0 = spin + strand * (Math.TAU / 3.0);
            List<Vec3> pts = new ArrayList<>(8);
            for (int seg = 0; seg <= 7; seg++) {
                double f = seg / 7.0;
                double ang = a0 + f * Math.TAU * 1.15;
                double rad = r * (0.36 + 0.62 * f);
                pts.add(base.add(Math.cos(ang) * rad, 0.18 + f * 3.1, Math.sin(ang) * rad));
            }
            RibbonTrail.ribbon(sp, pts, camera, 0.24, strand == 0 ? EffectSprites.brighten(color, 0.65) : color, (int) (230 * lifeFade));
        }
        Vec3 slashDir = new Vec3(Math.cos(spin * 1.4), 0, Math.sin(spin * 1.4));
        emitSwordSlash(sp, base.add(0, 1.35, 0), slashDir, 12.0, r * 1.15, (time % 260L) / 260.0, false, color, (int) (220 * lifeFade), true);
        if ("LIGHTSABER".equals(t.start.weaponType())) {
            LightningBolt.helixArcs(sp, base.add(0, 0.25, 0), base.add(0, 3.0, 0), camera, time, r * 0.75, 2, COL_LIGHT_SPARK, (int) (210 * lifeFade));
        }
    }

    // --- 极鬼剑术·暴风式 (Tempest / Storm Awakening — Pure Custom Sprites + Filtered Ghost Swordsman 3D Models!) ---

    private static void renderStorm(LevelRenderContext context, Track t, long rawAge, long time, List<SpriteQuad> sp, Vec3 camera) {
        long age = StormChoreography.presentationTime(rawAge);
        SkillVisualEvent e = t.start;
        Vec3 dir = horizontalDir(vector(e.direction()));
        Vec3 center = vector(e.origin()).add(dir.scale(StormChoreography.CENTER_OFFSET));
        int color = e.color();
        double deploy = Math.clamp((age - 200) / 800.0, 0.0, 1.0);
        double easeDeploy = EffectSprites.easeOutCubic(deploy);

        // 1. Deployment Gathering Burst (0..1000ms)
        if (age < 1000) {
            double u = Math.clamp(age / 1000.0, 0.0, 0.999);
            EffectSprites.glow(sp, center.add(0, 2.0, 0), camera, 1.65 + easeDeploy * 1.1, color, 235);
            emitIaidoFlash(sp, center.add(0, 2.0, 0), camera, 2.8 * (0.4 + 0.8 * Math.sin(u * Math.PI)), u, EffectSprites.brighten(color, 0.65), 245);
            EffectSprites.shockwave(sp, center.add(0, 0.09, 0), 5.6 * easeDeploy, u * 1.5, color, (int) (220 * (1.0 - 0.5 * u)));
        }

        // 2. Grand 256x256 Sword-Array Formation Magic Circle on Arena Ground (matches 6.5-block damage radius!)
        int sigilAlpha = age >= 7200 ? (int) (225 * Math.clamp((8000 - age) / 800.0, 0.0, 1.0)) : 225;
        if (deploy > 0.04 && sigilAlpha > 4) {
            emitFormation(sp, center.add(0, 0.05, 0), 6.85 * easeDeploy, age / 1300.0, color, sigilAlpha, true);
            emitFormation(sp, center.add(0, 0.07, 0), 4.45 * easeDeploy, -age / 950.0, EffectSprites.brighten(color, 0.45), (int) (sigilAlpha * 0.65), false);
        }

        // 3. 24 Ghost Swordsman 3D Weapon Models + Individual Sword Seal Plumes (Zero warglaives/staves!)
        if (age < 7200) {
            var mc = Minecraft.getInstance();
            if (mc != null && mc.player != null) ensureSwords(t);
            int[] order = StormChoreography.order(e.seed());
            boolean[] used = new boolean[24];
            for (int i = 0; i < StormChoreography.completed(age); i++) used[order[i]] = true;
            for (int i = 0; i < 24; i++) {
                if (used[i]) continue;
                var point = StormChoreography.sword(i);
                double spin = (1.0 - deploy) * Math.TAU;
                double xx = point.x() * Math.cos(spin) - (point.z() - 3.0) * Math.sin(spin);
                double zz = point.x() * Math.sin(spin) + (point.z() - 3.0) * Math.cos(spin);
                Vec3 p = local(e, new StormChoreography.Point(xx * deploy, 2.0 + (point.y() - 2.0) * deploy, zz * deploy + 3.0));
                if (age < 200) {
                    EffectSprites.glow(sp, p.add(0, 0.65, 0), camera, 0.52, color, 200);
                    continue;
                }
                if (mc != null && mc.player != null && t.models != null && t.swords != null) {
                    var model = t.models[i];
                    if (model == null) {
                        model = new ItemStackRenderState();
                        mc.getItemModelResolver().updateForLiving(model, t.swords[i], ItemDisplayContext.NONE, mc.player);
                        t.models[i] = model;
                    }
                    context.swords().add(new Sword(model, p, i * 30, t.inclinations.get(i)));
                }
                // Subtle luminous seal glint at each planted/floating Ghost Swordsman blade
                EffectSprites.glow(sp, p.add(0, 0.25, 0), camera, 0.46, color, 155);
                if (deploy > 0.85 && point.y() < 0.5) {
                    emitAuraPlume(sp, p.add(0, 0.04, 0), 0.24, 0.85, time + i * 70L, color, 125, i * 0.5);
                }
            }
        }

        // 4. 24 High-Speed Sword-to-Sword Crisscross Slashes (1000..7000ms)
        if (age >= 1000 && age < 7000) {
            int phase = (int) Math.clamp((age - 1000) / 250, 0, 23);
            double sub = ((age - 1000) % 250) / 250.0;
            int[] order = StormChoreography.order(e.seed());
            Vec3 from = local(e, StormChoreography.sword(order[phase]));
            Vec3 to = phase == 23 ? vector(e.origin()) : local(e, StormChoreography.sword(order[phase + 1]));
            Vec3 actor = local(e, StormChoreography.actor(e.seed(), age));
            Vec3 travel = to.subtract(from);
            if (travel.lengthSqr() > 1e-4) travel = travel.normalize();

            // Blazing slash-web corridor connecting sword -> sword
            RibbonTrail.ribbon(sp, List.of(actor.add(0, 1.0, 0), from.lerp(actor, 0.5).add(0, 1.0, 0), from.add(0, 1.0, 0)), camera, 0.34, color, 238);
            EffectSprites.texturedBeam(sp, from.add(0, 1.0, 0), actor.add(0, 1.0, 0), camera, 0.16, time, false, EffectSprites.brighten(color, 0.50), 185);
            if ((phase & 1) == 0) {
                LightningBolt.bolt(sp, from.add(0, 1.0, 0), to.add(0, 1.0, 0), camera, (time / 45L) ^ phase, 7, 0.34, 0.048, EffectSprites.brighten(color, 0.55), 185, false);
            }

            // Razor crescent slash & spark right around the dashing Swordmaster!
            double roll = ((phase % 5) - 2) * 28.0;
            emitSwordSlash(sp, actor.add(0, 1.05, 0), travel, roll, 3.15, Math.clamp(sub, 0.0, 0.999), (phase & 1) == 1, color, 245, true);
            emitIaidoFlash(sp, actor.add(0, 1.05, 0), camera, 1.85, Math.clamp(sub, 0.0, 0.999), EffectSprites.brighten(color, 0.65), 235);
            EffectSprites.glow(sp, actor.add(0, 1.0, 0), camera, 1.15, COL_WHITE, 215);
        }

        // 5. Sky-Splitting Colossal Tempest Finale (7200..8000ms)
        if (age >= 7200) {
            double u = Math.clamp((age - 7200) / 800.0, 0.0, 0.999);
            double fade = 1.0 - u;
            Vec3 mid = center.add(0, 1.72, 0);

            emitDrawRing(sp, mid, 7.55, u, 0.25, EffectSprites.brighten(color, 0.45), 255);
            emitSwordSlash(sp, mid, dir, 0.0, 7.85, u, false, EffectSprites.brighten(color, 0.55), 255, true);
            emitSwordSlash(sp, mid.add(0, 0.22, 0), dir, 62.0, 6.95, u, true, color, 248, true);
            emitSwordSlash(sp, mid.add(0, -0.18, 0), dir.scale(-1), -58.0, 6.95, u, false, color, 238, true);
            emitIaidoFlash(sp, mid, camera, 6.2, Math.min(0.999, u * 1.25), COL_WHITE, (int) (255 * fade));
            emitGroundFissure(sp, center.add(0, 0.05, 0), 6.95 * (0.55 + 0.45 * EffectSprites.easeOutCubic(u)), e.seed() * 0.01, color, (int) (235 * fade));
            EffectSprites.shockwave(sp, center.add(0, 0.10, 0), 1.8 + 6.8 * EffectSprites.easeOutCubic(u), u * 1.9, color, (int) (248 * fade));
            EffectSprites.shockwave(sp, center.add(0, 1.50, 0), 1.4 + 5.8 * EffectSprites.easeOutCubic(u), -u * 1.5, EffectSprites.brighten(color, 0.65), (int) (220 * fade));
            EffectSprites.impactBurst(sp, mid, camera, 6.2, u, color, 242, false);
        }
    }

    private static void ensureSwords(Track t) {
        if (t.swords != null) return;
        t.swords = GhostSwordsmanWeaponVisuals.choose(t.start.seed());
        t.models = new ItemStackRenderState[24];
        t.inclinations = GhostSwordsmanWeaponVisuals.inclinations(t.start.seed());
    }

    // --- Custom Dual-Layer Sprite Sheet Emitters -----------------------------

    /** Sweeping DNF blade crescent facing `forward`, tilted by `rollDeg`, with dark-backing + additive core. */
    private static void emitSwordSlash(
        List<SpriteQuad> out, Vec3 center, Vec3 forward, double rollDeg,
        double radius, double progress, boolean mirror, int rgb, int alpha, boolean doubleLayer
    ) {
        if (progress < 0.0 || progress >= 1.0 || radius <= 0.0 || alpha <= 0) return;
        Vec3 dir = horizontalDir(forward);
        Vec3 right0 = rightOf(dir);
        double roll = Math.toRadians(rollDeg);
        Vec3 side = right0.scale(Math.cos(roll)).add(UP.scale(Math.sin(roll))).normalize();
        if (mirror) side = side.scale(-1.0);

        double scale = radius * (0.85 + 0.25 * EffectSprites.easeOutCubic(progress));
        int frame = Math.clamp((int) (progress * 8), 0, 7);
        float u0 = frame / 8.0f, u1 = (frame + 1) / 8.0f;
        int a = Math.clamp((int) Math.round(alpha * (1.0 - 0.36 * progress * progress)), 0, 255);

        Vec3 halfU = dir.scale(scale);
        Vec3 halfV = side.scale(scale);
        if (doubleLayer) {
            EffectSprites.emitPlane(out, TEX_SLASH, Blend.TRANSLUCENT, center, halfU.scale(1.03), halfV.scale(1.03), u0, 0f, u1, 1f, backingColor(rgb), (int) (a * 0.62));
        }
        EffectSprites.emitPlane(out, TEX_SLASH, Blend.ADDITIVE, center, halfU, halfV, u0, 0f, u1, 1f, rgb, a);
        EffectSprites.emitPlane(out, TEX_SLASH, Blend.ADDITIVE, center, halfU.scale(0.95), halfV.scale(0.95), u0, 0f, u1, 1f, EffectSprites.brighten(rgb, 0.62), (int) (a * 0.68));
    }

    /** Full 360-degree DNF Draw Sword horizontal ring slash. */
    private static void emitDrawRing(List<SpriteQuad> out, Vec3 center, double radius, double progress, double rotOffset, int rgb, int alpha) {
        if (progress < 0.0 || progress >= 1.0 || radius <= 0.0 || alpha <= 0) return;
        int frame = Math.clamp((int) (progress * 8), 0, 7);
        float u0 = frame / 8.0f, u1 = (frame + 1) / 8.0f;
        double scale = radius * (0.82 + 0.26 * EffectSprites.easeOutCubic(progress));
        int a = Math.clamp((int) Math.round(alpha * (1.0 - 0.35 * progress * progress)), 0, 255);

        EffectSprites.emitGround(out, TEX_DRAW_RING, Blend.TRANSLUCENT, center, scale * 1.03, rotOffset, u0, 0f, u1, 1f, backingColor(rgb), (int) (a * 0.66));
        EffectSprites.emitGround(out, TEX_DRAW_RING, Blend.ADDITIVE, center.add(0, 0.02, 0), scale, rotOffset, u0, 0f, u1, 1f, rgb, a);
        EffectSprites.emitGround(out, TEX_DRAW_RING, Blend.ADDITIVE, center.add(0, 0.04, 0), scale * 0.96, rotOffset, u0, 0f, u1, 1f, EffectSprites.brighten(rgb, 0.68), (int) (a * 0.70));
    }

    /** DNF 鬼斩 sinister ragged spectral flame crescent. */
    private static void emitGhostCrescent(
        List<SpriteQuad> out, Vec3 center, Vec3 forward, double rollDeg,
        double radius, double progress, boolean mirror, int rgb, int alpha
    ) {
        if (progress < 0.0 || progress >= 1.0 || radius <= 0.0 || alpha <= 0) return;
        Vec3 dir = horizontalDir(forward);
        Vec3 right0 = rightOf(dir);
        double roll = Math.toRadians(rollDeg);
        Vec3 side = right0.scale(Math.cos(roll)).add(UP.scale(Math.sin(roll))).normalize();
        if (mirror) side = side.scale(-1.0);

        double scale = radius * (0.84 + 0.24 * EffectSprites.easeOutCubic(progress));
        int frame = Math.clamp((int) (progress * 8), 0, 7);
        float u0 = frame / 8.0f, u1 = (frame + 1) / 8.0f;
        int a = Math.clamp((int) Math.round(alpha * (1.0 - 0.38 * progress * progress)), 0, 255);

        Vec3 halfU = dir.scale(scale);
        Vec3 halfV = side.scale(scale);
        // Heavy abyssal dark-violet ghost aura backing + bright lavender spectral core!
        EffectSprites.emitPlane(out, TEX_GHOST_CRESCENT, Blend.TRANSLUCENT, center, halfU.scale(1.06), halfV.scale(1.06), u0, 0f, u1, 1f, 0x160726, (int) (a * 0.88));
        EffectSprites.emitPlane(out, TEX_GHOST_CRESCENT, Blend.ADDITIVE, center, halfU, halfV, u0, 0f, u1, 1f, rgb, a);
        EffectSprites.emitPlane(out, TEX_GHOST_CRESCENT, Blend.ADDITIVE, center, halfU.scale(0.94), halfV.scale(0.94), u0, 0f, u1, 1f, EffectSprites.brighten(rgb, 0.58), (int) (a * 0.62));
    }

    /** Towering vertical rising dragon uppercut crescent (破军升龙击). */
    private static void emitRisingArc(List<SpriteQuad> out, Vec3 center, Vec3 forward, double radius, double progress, int rgb, int alpha) {
        if (progress < 0.0 || progress >= 1.0 || radius <= 0.0 || alpha <= 0) return;
        Vec3 dir = horizontalDir(forward);
        double scale = radius * (0.85 + 0.25 * EffectSprites.easeOutCubic(progress));
        int frame = Math.clamp((int) (progress * 8), 0, 7);
        float u0 = frame / 8.0f, u1 = (frame + 1) / 8.0f;
        int a = Math.clamp((int) Math.round(alpha * (1.0 - 0.36 * progress * progress)), 0, 255);

        // In rising_arc.png: +qx is forward (+dir), -qy is sky (+UP) -> halfV points DOWN (-UP)
        Vec3 halfU = dir.scale(scale);
        Vec3 halfV = UP.scale(-scale);
        EffectSprites.emitPlane(out, TEX_RISING_ARC, Blend.TRANSLUCENT, center, halfU.scale(1.04), halfV.scale(1.04), u0, 0f, u1, 1f, backingColor(rgb), (int) (a * 0.68));
        EffectSprites.emitPlane(out, TEX_RISING_ARC, Blend.ADDITIVE, center, halfU, halfV, u0, 0f, u1, 1f, rgb, a);
        EffectSprites.emitPlane(out, TEX_RISING_ARC, Blend.ADDITIVE, center, halfU.scale(0.95), halfV.scale(0.95), u0, 0f, u1, 1f, EffectSprites.brighten(rgb, 0.65), (int) (a * 0.68));
    }

    /** Roaring DNF Raging Dragon head on crossed vertical + horizontal planes for true 3D readability! */
    private static void emitDragonHead(List<SpriteQuad> out, Vec3 center, Vec3 forward, double scale, int frame, int rgb, int alpha) {
        if (scale <= 0.0 || alpha <= 4) return;
        Vec3 dir = horizontalDir(forward);
        Vec3 right = rightOf(dir);
        float u0 = (frame & 7) / 8.0f, u1 = ((frame & 7) + 1) / 8.0f;
        int a = Math.clamp(alpha, 0, 255);

        // Side-profile vertical dragon plane (+halfU = forward snout, -halfV = top horns)
        Vec3 halfU = dir.scale(scale);
        Vec3 halfV = UP.scale(-scale * 0.82);
        EffectSprites.emitPlane(out, TEX_DRAGON_HEAD, Blend.TRANSLUCENT, center, halfU.scale(1.05), halfV.scale(1.05), u0, 0f, u1, 1f, backingColor(rgb), (int) (a * 0.72));
        EffectSprites.emitPlane(out, TEX_DRAGON_HEAD, Blend.ADDITIVE, center, halfU, halfV, u0, 0f, u1, 1f, rgb, a);
        EffectSprites.emitPlane(out, TEX_DRAGON_HEAD, Blend.ADDITIVE, center, halfU.scale(0.94), halfV.scale(0.94), u0, 0f, u1, 1f, EffectSprites.brighten(rgb, 0.65), (int) (a * 0.62));

        // Top-down horizontal dragon mane/jaw silhouette so top/overhead cameras also see the dragon clearly!
        Vec3 hSide = right.scale(scale * 0.78);
        EffectSprites.emitPlane(out, TEX_DRAGON_HEAD, Blend.TRANSLUCENT, center, halfU.scale(1.02), hSide, u0, 0f, u1, 1f, backingColor(rgb), (int) (a * 0.52));
        EffectSprites.emitPlane(out, TEX_DRAGON_HEAD, Blend.ADDITIVE, center, halfU, hSide, u0, 0f, u1, 1f, rgb, (int) (a * 0.82));
    }

    /** 3D Cross-plane aerodynamic forward wind-break cone (破军冲撞 / 猛龙 / 三段斩). */
    private static void emitGaleCone(
        List<SpriteQuad> out, Vec3 noseCenter, Vec3 forward,
        double halfWidth, double length, double progress, int rgb, int alpha
    ) {
        if (progress < 0.0 || progress >= 1.0 || halfWidth <= 0.0 || length <= 0.0 || alpha <= 4) return;
        Vec3 dir = horizontalDir(forward);
        Vec3 right = rightOf(dir);
        int frame = Math.clamp((int) (progress * 8), 0, 7);
        float u0 = frame / 8.0f, u1 = (frame + 1) / 8.0f;
        int a = Math.clamp((int) Math.round(alpha * (1.0 - 0.35 * progress * progress)), 0, 255);

        // v=0 is spear tip (+dir), v=1 is wide trailing skirt (-dir) -> halfV = -dir * (length * 0.5)
        Vec3 center = noseCenter.subtract(dir.scale(length * 0.35));
        Vec3 halfV = dir.scale(-length * 0.5);
        for (int i = 0; i < 2; i++) {
            Vec3 halfU = (i == 0 ? right : UP).scale(halfWidth);
            EffectSprites.emitPlane(out, TEX_GALE_CONE, Blend.TRANSLUCENT, center, halfU.scale(1.04), halfV, u0, 0f, u1, 1f, backingColor(rgb), (int) (a * 0.52));
            EffectSprites.emitPlane(out, TEX_GALE_CONE, Blend.ADDITIVE, center, halfU, halfV, u0, 0f, u1, 1f, rgb, a);
        }
    }

    /** Flying crescent sword-qi wave plane facing `forward`, tilted by `rollDeg`. */
    private static void emitSwordWavePlane(
        List<SpriteQuad> out, Vec3 center, Vec3 forward, double rollDeg,
        double radius, int frame, int rgb, int alpha, boolean doubleLayer
    ) {
        if (radius <= 0.0 || alpha <= 4) return;
        Vec3 dir = horizontalDir(forward);
        Vec3 right0 = rightOf(dir);
        double roll = Math.toRadians(rollDeg);
        Vec3 side = right0.scale(Math.cos(roll)).add(UP.scale(Math.sin(roll))).normalize();
        float u0 = (frame & 7) / 8.0f, u1 = ((frame & 7) + 1) / 8.0f;
        int a = Math.clamp(alpha, 0, 255);

        // In sword_wave.png: bow faces +qx (+dir), wings span +-qy (+-side)
        Vec3 halfU = dir.scale(radius);
        Vec3 halfV = side.scale(radius);
        if (doubleLayer) {
            EffectSprites.emitPlane(out, TEX_SWORD_WAVE, Blend.TRANSLUCENT, center, halfU.scale(1.04), halfV.scale(1.04), u0, 0f, u1, 1f, backingColor(rgb), (int) (a * 0.64));
        }
        EffectSprites.emitPlane(out, TEX_SWORD_WAVE, Blend.ADDITIVE, center, halfU, halfV, u0, 0f, u1, 1f, rgb, a);
        EffectSprites.emitPlane(out, TEX_SWORD_WAVE, Blend.ADDITIVE, center, halfU.scale(0.94), halfV.scale(0.94), u0, 0f, u1, 1f, EffectSprites.brighten(rgb, 0.65), (int) (a * 0.65));
    }

    /** Blade-hugging looping golden Overdrive sword-qi sheath along grip -> tip. */
    private static void emitBladeAuraSheath(List<SpriteQuad> out, Vec3 grip, Vec3 tip, double halfWidth, long timeMs, int rgb, int alpha) {
        Vec3 delta = tip.subtract(grip);
        double len = delta.length();
        if (len < 0.1 || alpha <= 4) return;
        Vec3 axis = delta.scale(1.0 / len);
        Vec3 ref = Math.abs(axis.y) > 0.92 ? new Vec3(1, 0, 0) : UP;
        Vec3 u = axis.cross(ref).normalize();
        Vec3 v = axis.cross(u).normalize();

        int frame = (int) Math.floorMod(timeMs / 80L, 4L);
        float u0 = frame / 4.0f, u1 = (frame + 1) / 4.0f;
        Vec3 center = grip.lerp(tip, 0.54);
        // v=0 is sharp flame tip (+axis), v=1 is hilt base (-axis) -> halfV = -axis * (len * 0.56)
        Vec3 halfV = axis.scale(-len * 0.56);
        int a = Math.clamp(alpha, 0, 255);
        for (int i = 0; i < 2; i++) {
            Vec3 halfU = (i == 0 ? u : v).scale(halfWidth);
            EffectSprites.emitPlane(out, TEX_OVERDRIVE_AURA, Blend.TRANSLUCENT, center, halfU.scale(1.06), halfV, u0, 0f, u1, 1f, backingColor(rgb), (int) (a * 0.45));
            EffectSprites.emitPlane(out, TEX_OVERDRIVE_AURA, Blend.ADDITIVE, center, halfU, halfV, u0, 0f, u1, 1f, rgb, a);
        }
    }

    /** 2-cross-plane rising sword-qi aura plume anchored at `base`. */
    private static void emitAuraPlume(List<SpriteQuad> out, Vec3 base, double halfWidth, double height, long timeMs, int rgb, int alpha, double yawOffset) {
        if (halfWidth <= 0.0 || height <= 0.0 || alpha <= 4) return;
        int frame = (int) Math.floorMod(timeMs / 85L, 4L);
        float u0 = frame / 4.0f, u1 = (frame + 1) / 4.0f;
        Vec3 center = base.add(0, height * 0.5, 0);
        Vec3 halfV = new Vec3(0, -height * 0.5, 0);
        int a = Math.clamp(alpha, 0, 255);
        for (int i = 0; i < 2; i++) {
            double ang = yawOffset + i * (Math.PI * 0.5);
            Vec3 halfU = new Vec3(Math.cos(ang) * halfWidth, 0, Math.sin(ang) * halfWidth);
            EffectSprites.emitPlane(out, TEX_OVERDRIVE_AURA, Blend.ADDITIVE, center, halfU, halfV, u0, 0f, u1, 1f, rgb, a);
        }
    }

    /** 256x256 Swordmaster Sword-Array formation magic circle on ground. */
    private static void emitFormation(List<SpriteQuad> out, Vec3 center, double radius, double rotRad, int rgb, int alpha, boolean doubleLayer) {
        if (radius <= 0.0 || alpha <= 4) return;
        int a = Math.clamp(alpha, 0, 255);
        if (doubleLayer) {
            EffectSprites.emitGround(out, TEX_SWORD_FORMATION, Blend.TRANSLUCENT, center, radius * 1.03, -rotRad * 0.45, 0f, 0f, 1f, 1f, backingColor(rgb), (int) (a * 0.56));
        }
        EffectSprites.emitGround(out, TEX_SWORD_FORMATION, Blend.ADDITIVE, center.add(0, 0.014, 0), radius, rotRad, 0f, 0f, 1f, 1f, rgb, a);
    }

    /** Ground fracture decal for Rising Dragon, Greatsword cleave, Earth Wave & Storm Finale. */
    private static void emitGroundFissure(List<SpriteQuad> out, Vec3 center, double radius, double rotRad, int rgb, int alpha) {
        if (radius <= 0.0 || alpha <= 4) return;
        int a = Math.clamp(alpha, 0, 255);
        EffectSprites.emitGround(out, TEX_GROUND_FISSURE, Blend.TRANSLUCENT, center, radius * 1.04, rotRad, 0f, 0f, 1f, 1f, backingColor(rgb), (int) (a * 0.72));
        EffectSprites.emitGround(out, TEX_GROUND_FISSURE, Blend.ADDITIVE, center.add(0, 0.016, 0), radius, rotRad, 0f, 0f, 1f, 1f, EffectSprites.brighten(rgb, 0.35), (int) (a * 0.78));
    }

    /** Crisp 4-point DNF iaido horizontal blade glint billboard. */
    private static void emitIaidoFlash(List<SpriteQuad> out, Vec3 center, Vec3 camera, double radius, double progress, int rgb, int alpha) {
        if (progress < 0.0 || progress >= 1.0 || radius <= 0.0 || alpha <= 4) return;
        int frame = Math.clamp((int) (progress * 8), 0, 7);
        float u0 = frame / 8.0f, u1 = (frame + 1) / 8.0f;
        Vec3 look = camera.subtract(center);
        if (look.lengthSqr() < 1e-6) look = new Vec3(0, 0, 1);
        else look = look.normalize();
        Vec3 right = look.cross(Math.abs(look.y) > 0.95 ? new Vec3(1, 0, 0) : UP).normalize();
        Vec3 up = right.cross(look).normalize();
        Vec3 halfU = right.scale(radius);
        Vec3 halfV = up.scale(-radius);
        int a = Math.clamp(alpha, 0, 255);
        EffectSprites.emitPlane(out, TEX_IAIDO_FLASH, Blend.ADDITIVE, center, halfU, halfV, u0, 0f, u1, 1f, rgb, a);
        EffectSprites.emitPlane(out, TEX_IAIDO_FLASH, Blend.ADDITIVE, center.add(look.scale(0.02)), halfU.scale(0.85), halfV.scale(0.85), u0, 0f, u1, 1f, COL_WHITE, (int) (a * 0.75));
    }

    // --- Pure Math & Helper Utilities ----------------------------------------

    private static double weaponRadiusMult(String weapon) {
        if (weapon == null) return 1.0;
        return switch (weapon) {
            case "GREATSWORD" -> 1.18;
            case "KATANA" -> 1.12;
            case "LIGHTSABER" -> 1.06;
            case "BLUNT" -> 1.05;
            default -> 1.0;
        };
    }

    private static int backingColor(int rgb) {
        int r = (rgb >> 16) & 255;
        int g = (rgb >> 8) & 255;
        int b = rgb & 255;
        // Shift into a rich deep shadow hue (keeps strong daytime contrast without muddying color)
        int br = Math.clamp((int) Math.round(r * 0.24), 8, 92);
        int bg = Math.clamp((int) Math.round(g * 0.24), 10, 92);
        int bb = Math.clamp((int) Math.round(b * 0.30), 14, 110);
        return (br << 16) | (bg << 8) | bb;
    }

    static long basicSingleDuration(SkillVisualEvent event) {
        long cap = event.sweep() ? 440L : 360L;
        long floor = event.sweep() ? 320L : 260L;
        long base = event.duration() > 0 ? event.duration() : cap;
        return Math.clamp(base, floor, cap);
    }

    private static long flashDuration(SkillVisualEvent event) {
        if ("ATTACK".equals(event.skill())) {
            return basicSingleDuration(event);
        }
        if (SkillCatalog.DANCE.id.equals(event.skill())) return 240L;
        if (SkillCatalog.DRAW.id.equals(event.skill())) return event.phase() == HIT ? 460L : 290L;
        if (SkillCatalog.RISING.id.equals(event.skill())) return event.phase() == FINISH ? 560L : event.phase() == SEGMENT ? 260L : 460L;
        if (SkillCatalog.METEOR_RISE.id.equals(event.skill()) || SkillCatalog.METEOR_LEAP.id.equals(event.skill())) return 380L;
        if (SkillCatalog.METEOR_PIERCE.id.equals(event.skill())) return 240L;
        return 420L;
    }

    private static Vec3 local(SkillVisualEvent e, StormChoreography.Point point) {
        Vec3 dir = horizontalDir(vector(e.direction()));
        Vec3 side = new Vec3(dir.z, 0, -dir.x);
        return vector(e.origin()).add(side.scale(point.x())).add(0, point.y(), 0).add(dir.scale(point.z()));
    }

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
}
