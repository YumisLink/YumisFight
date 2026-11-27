package cn.yumis.fight.modules.skills.client;

import cn.yumis.fight.modules.presentation.api.client.CameraShake;
import cn.yumis.fight.modules.presentation.api.client.EffectSprites;
import cn.yumis.fight.modules.presentation.api.client.EffectSprites.Blend;
import cn.yumis.fight.modules.presentation.api.client.EffectSprites.SpriteQuad;
import cn.yumis.fight.modules.presentation.api.client.LightningBolt;
import cn.yumis.fight.modules.presentation.api.client.RibbonTrail;
import cn.yumis.fight.modules.presentation.api.client.WorldQuads.Quad;
import cn.yumis.fight.modules.skills.api.OracleSkills;
import cn.yumis.fight.modules.skills.api.SkillVisualEvent;
import com.google.gson.JsonObject;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.*;

import static cn.yumis.fight.modules.skills.api.SkillVisualEvent.Phase.*;
import static cn.yumis.fight.modules.skills.client.SkillVfx.*;

/**
 * Dedicated Oracle Executor (神谕代行者 / 代行者) dual-state VFX renderer.
 * Zero legacy WorldQuads: 100% custom high-res sprite sheets (source: tools/build_oracle_effect_textures.py):
 *   - Normal casts: "Black Silence" monochrome ink-black silhouette + razor cold-silver highlight
 *   - Instruction-triggered casts & Furioso-Replica Awakening: radiant Oracle Sacred Gold + deep obsidian contrast
 *   - Five bespoke skill silhouettes + multi-weapon Furioso-Replica choreography + persistent Hermes halo & stack pips.
 * Pure client presentation: never determines combat results.
 */
final class OracleSkillVisuals {
    private static final Vec3 UP = new Vec3(0, 1, 0);

    static final Identifier TEX_INK_SLASH = tex("ink_slash.png");
    static final Identifier TEX_HEAVY_CRUSH = tex("heavy_crush.png");
    static final Identifier TEX_PIERCE_LANCE = tex("pierce_lance.png");
    static final Identifier TEX_CROSS_SMILE = tex("cross_smile.png");
    static final Identifier TEX_SPACE_RIFT = tex("space_rift.png");
    static final Identifier TEX_ORACLE_SIGIL = tex("oracle_sigil.png");
    static final Identifier TEX_ORACLE_HALO = tex("oracle_halo.png");
    static final Identifier TEX_ORACLE_IMPACT = tex("oracle_impact.png");
    static final Identifier TEX_TELE_SHATTER = tex("tele_shatter.png");

    // Dual-state palettes: Monochrome Black Silence vs. Radiant Oracle Sacred Gold
    private static final int COL_INK_DARK = 0x0B0D14;
    private static final int COL_INK_SILVER = 0xDCE5F2;
    private static final int COL_INK_WHITE = 0xF5F8FF;

    private static final int COL_GOLD_DARK = 0x241705;
    private static final int COL_GOLD_MAIN = 0xF5CE67;
    private static final int COL_GOLD_HOT = 0xFFF7D6;

    // Pure-client departure points for 举刃对笑 (oracle_smile) teleport rift visuals
    private static final Map<UUID, Vec3> SMILE_DEPARTURES = new LinkedHashMap<>();

    private OracleSkillVisuals() {}

    private static Identifier tex(String name) {
        return Identifier.fromNamespaceAndPath("yumis_dungeon_and_fight", "textures/effect/oracle/" + name);
    }

    static void clear() {
        SMILE_DEPARTURES.clear();
    }

    static void install() {
        for (String id : OracleSkills.ACTIONS) SkillVisualRegistry.register(id, new Handler(false));
        SkillVisualRegistry.register(OracleSkills.REPLICA, new Handler(true));
        SkillVisualRegistry.register(OracleSkills.WILL, new Handler(false));
    }

    // --- Persistent Hermes Halo & Stack Pips (Rich Persistent State Visual) ---

    static void actors(List<SpriteQuad> sp, Vec3 camera, long serverNow, long renderNow) {
        var mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) return;

        for (var actor : CareerClient.INSTANCE.view.actors) {
            int stacks = Math.clamp(actor.oracleHermes, 0, 9);
            if (stacks <= 0) continue;
            if (stacks < 9 && actor.oracleHermesUntil > 0 && actor.oracleHermesUntil <= serverNow) continue;

            UUID ownerId;
            try {
                ownerId = UUID.fromString(actor.owner);
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            Player p = mc.level.getPlayerByUUID(ownerId);
            if (p == null || !p.isAlive()) continue;

            boolean ownFp = isOwnFirstPerson(ownerId);
            boolean ready = stacks >= 9;
            Vec3 base = PlayerVisualAnchor.position(p);
            Vec3 forward = horizontalDir(PlayerVisualAnchor.forward(p));
            Vec3 right = rightOf(forward);
            double ratio = stacks / 9.0;
            double pulse = 0.5 + 0.5 * Math.sin(renderNow * (ready ? 0.0085 : 0.0045));

            // 1. Rotating Golden Oracle Sigil underfoot (scales & brightens with Hermes stacks 1..9!)
            double footAlpha = (85.0 + 115.0 * ratio + (ready ? 30.0 * pulse : 0.0)) * (ownFp ? 0.52 : 1.0);
            emitOracleSigil(sp, base.add(0, 0.045, 0), 0.92 + 0.52 * ratio, renderNow * (ready ? 0.0024 : 0.0014), true, (int) footAlpha, ready);

            if (ownFp) continue;

            // 2. Iconic Rotating Celestial Golden Halo floating cleanly behind the Oracle's back!
            Vec3 haloCenter = base.subtract(forward.scale(0.52)).add(0, 1.38 + 0.05 * pulse, 0);
            double haloR = 0.72 + 0.42 * ratio;
            double spin = renderNow * (ready ? 0.0026 : 0.0014);
            double c = Math.cos(spin) * haloR, s = Math.sin(spin) * haloR;
            Vec3 hu = right.scale(c).add(UP.scale(s));
            Vec3 hv = UP.scale(c).subtract(right.scale(s));
            int haloAlpha = (int) (130 + 95 * ratio);
            EffectSprites.emitPlane(sp, TEX_ORACLE_HALO, Blend.TRANSLUCENT, haloCenter, hu.scale(1.03), hv.scale(1.03), 0f, 0f, 1f, 1f, COL_GOLD_DARK, (int) (haloAlpha * 0.58));
            EffectSprites.emitPlane(sp, TEX_ORACLE_HALO, Blend.ADDITIVE, haloCenter.add(forward.scale(-0.015)), hu, hv, 0f, 0f, 1f, 1f, COL_GOLD_MAIN, haloAlpha);
            if (ready) {
                EffectSprites.emitPlane(sp, TEX_ORACLE_HALO, Blend.ADDITIVE, haloCenter.add(forward.scale(-0.025)), hu.scale(0.95), hv.scale(0.95), 0f, 0f, 1f, 1f, COL_GOLD_HOT, (int) (145 + 65 * pulse));
            }

            // 3. Individual Orbiting Hermes Stack Pips (1..9 glowing golden nodes arrayed around the halo!)
            double pipOrbitR = haloR * 0.84;
            for (int i = 0; i < stacks; i++) {
                double ang = spin * 1.15 + i * (Math.TAU / 9.0) - Math.PI * 0.5;
                Vec3 pipPos = haloCenter.add(right.scale(Math.cos(ang) * pipOrbitR)).add(0, Math.sin(ang) * pipOrbitR, 0);
                double pipSize = ready ? 0.24 + 0.05 * pulse : 0.19;
                EffectSprites.glow(sp, pipPos, camera, pipSize, ready ? COL_GOLD_HOT : COL_GOLD_MAIN, ready ? 235 : 205);
            }

            // 4. Full 9-Stack Furioso-Ready Resonance Arcs across the halo
            if (ready) {
                long step = renderNow / 95L;
                int aIdx = (int) Math.floorMod(step, 9L);
                int bIdx = (aIdx + 4) % 9;
                double a0 = spin * 1.15 + aIdx * (Math.TAU / 9.0) - Math.PI * 0.5;
                double a1 = spin * 1.15 + bIdx * (Math.TAU / 9.0) - Math.PI * 0.5;
                Vec3 p0 = haloCenter.add(right.scale(Math.cos(a0) * pipOrbitR)).add(0, Math.sin(a0) * pipOrbitR, 0);
                Vec3 p1 = haloCenter.add(right.scale(Math.cos(a1) * pipOrbitR)).add(0, Math.sin(a1) * pipOrbitR, 0);
                LightningBolt.bolt(sp, p0, p1, camera, step ^ ownerId.getMostSignificantBits(), 5, 0.18, 0.036, COL_GOLD_HOT, 200, false);
            }
        }
    }

    // --- Handler Implementation ----------------------------------------------

    private static final class Handler implements SkillVisualRegistry.Handler {
        private final boolean replica;

        Handler(boolean replica) {
            this.replica = replica;
        }

        @Override public boolean cinematic() { return replica; }
        @Override public boolean refreshable() { return replica; }
        @Override public boolean restoreCamera() { return replica; }
        @Override public boolean blocksTurning() { return replica; }
        @Override public boolean blocksHotbar() { return replica; }
        @Override public long finishedUntil(Track track, SkillVisualEvent event) {
            return replica ? Math.max(event.at() + 900L, track.start.startedAt() + track.start.duration() + 900L) : event.at() + 560L;
        }
        @Override public CameraType cameraType(Track track, long time) { return CameraType.THIRD_PERSON_BACK; }
        @Override public int defaultColor() { return replica ? COL_GOLD_MAIN : COL_INK_SILVER; }

        @Override public boolean motion(Track track, JsonObject value) {
            if (OracleSkills.SMILE.equals(track.start.skill()) && value.has("fromX")) {
                Vec3 from = new Vec3(value.get("fromX").getAsDouble(), value.get("fromY").getAsDouble(), value.get("fromZ").getAsDouble());
                if (SMILE_DEPARTURES.size() >= 128) SMILE_DEPARTURES.remove(SMILE_DEPARTURES.keySet().iterator().next());
                SMILE_DEPARTURES.put(track.start.cast(), from);
                var mc = Minecraft.getInstance();
                Player actor = mc == null || mc.level == null ? null : mc.level.getPlayerByUUID(track.start.caster());
                if (actor != null && !isOwnFirstPerson(track.start.caster())) {
                    boolean gold = isGold(replica, track.start);
                    PlayerAfterimages.captureAt(actor, from, vector(track.start.direction()), gold ? COL_GOLD_MAIN : COL_INK_SILVER, 175, 320L, true);
                }
                return true;
            }
            return false;
        }

        @Override public void animate(Track track, LivingEntity caster, EntityRenderState raw, long time) {
            if (!(raw instanceof HumanoidRenderState state)) return;
            var event = track.last;
            Vec3 heading = vector(event.direction());
            state.bodyRot = (float) Math.toDegrees(Math.atan2(-heading.x, heading.z));
            state.yRot = 0;
            state.walkAnimationSpeed = 0;
            long stage = track.flashes.stream().filter(e -> e.phase() == SEGMENT).mapToLong(SkillVisualEvent::at).max().orElse(track.start.startedAt());
            state.attackTime = (float) Math.clamp((time - stage) * track.speed / 600, 0, 1);
            if (replica && event.sweep()) {
                state.attackTime = 0.1f;
                state.rightArmPose = HumanoidModel.ArmPose.THROW_TRIDENT;
                state.leftArmPose = HumanoidModel.ArmPose.THROW_TRIDENT;
            }
            weapon(track, caster, state);
        }

        @Override public void decorateAvatar(Track track, Player player, AvatarRenderState state, long time) {
            if (!track.released) weapon(track, player, state);
        }

        private void weapon(Track track, LivingEntity caster, HumanoidRenderState state) {
            var event = track.last;
            if (replica && OracleSkills.WEAPONS.contains(event.weaponType())) {
                var hand = cn.yumis.fight.modules.equipment.api.EquipmentItems.view(caster.getMainHandItem());
                if (hand == null || !hand.weapon().name().equals(event.weaponType())) {
                    var item = cn.yumis.fight.modules.equipment.api.EquipmentVisualItems.weaponModel("common_" + event.weaponType().toLowerCase(Locale.ROOT));
                    Minecraft.getInstance().getItemModelResolver().updateForLiving(
                        state.getMainHandItemState(), item,
                        caster.getMainArm() == HumanoidArm.LEFT ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
                        caster
                    );
                }
            }
        }

        @Override public void play(Track track, SkillVisualEvent event) {
            boolean gold = isGold(replica, event);
            int aiColor = gold ? COL_GOLD_MAIN : COL_INK_SILVER;
            boolean ownFp = isOwnFirstPerson(event.caster());
            var mc = Minecraft.getInstance();
            Player actor = mc == null || mc.level == null ? null : mc.level.getPlayerByUUID(event.caster());
            Vec3 pos = vector(event.position());
            String skill = event.skill();

            if (event.phase() == START) {
                if (gold && !replica) {
                    // Instruction fulfilled chime!
                    SkillSound.playAt(event.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 0.62f, 1.48f);
                    SkillSound.playAt(event.position(), SoundEvents.BEACON_POWER_SELECT, 0.42f, 1.35f);
                }
                if (OracleSkills.THRUST.equals(skill)) {
                    SkillSound.playAt(event.position(), SoundEvents.SPEAR_ATTACK.value(), 0.72f, gold ? 1.15f : 1.05f);
                    SkillSound.playAt(event.position(), SoundEvents.BREEZE_SHOOT, 0.56f, 1.32f);
                    if (actor != null && !ownFp) PlayerAfterimages.capture(actor, aiColor, 175, 300L, true);
                } else if (OracleSkills.RIFT.equals(skill)) {
                    SkillSound.playAt(event.position(), SoundEvents.BREEZE_DEFLECT, 0.74f, 0.78f);
                    SkillSound.playAt(event.position(), SoundEvents.GLASS_BREAK, 0.46f, 1.42f);
                    if (actor != null && !ownFp) PlayerAfterimages.capture(actor, aiColor, 185, 330L, true);
                    CameraShake.impulse(pos, 14, 0.20f, 55L, 120L);
                } else if (OracleSkills.SMILE.equals(skill)) {
                    SkillSound.playAt(event.position(), SoundEvents.ENDERMAN_TELEPORT, 0.44f, 1.52f);
                } else if (OracleSkills.SWEEP.equals(skill)) {
                    SkillSound.playAt(event.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 0.42f, 1.15f);
                } else if (replica) {
                    SkillSound.playAt(event.position(), SoundEvents.BEACON_ACTIVATE, 0.78f, 1.18f);
                    SkillSound.playAt(event.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 0.80f, 1.32f);
                    CameraShake.impulse(pos, 20, 0.24f, 80L, 160L);
                }
                return;
            }

            if (event.phase() == SEGMENT && replica) {
                int step = (int) Math.round(event.slashAngle());
                if (OracleSkills.combo(step)) {
                    SkillSound.playAt(event.position(), SoundEvents.BREEZE_SHOOT, 0.46f, 1.25f + (step - 8) * 0.06f);
                } else if (step == OracleSkills.FINISH_STEP) {
                    SkillSound.playAt(event.position(), SoundEvents.BEACON_POWER_SELECT, 0.82f, 1.05f);
                    SkillSound.playAt(event.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 0.78f, 1.40f);
                } else {
                    SkillSound.playAt(event.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 0.42f, 1.38f);
                }
                if (actor != null) PlayerAfterimages.capture(actor, COL_GOLD_MAIN, 155, 290L, true);
                return;
            }

            if (event.phase() == HIT || event.phase() == RELEASE || event.phase() == FINISH) {
                if (OracleSkills.DOWN.equals(skill)) {
                    SkillSound.playAt(event.position(), SoundEvents.MACE_SMASH_GROUND_HEAVY, 0.88f, gold ? 0.92f : 0.82f);
                    SkillSound.playAt(event.position(), SoundEvents.PLAYER_ATTACK_STRONG, 0.70f, 0.75f);
                    CameraShake.impulse(pos, 16, 0.34f, 85L, 180L);
                } else if (OracleSkills.SWEEP.equals(skill)) {
                    SkillSound.playAt(event.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 0.92f, 0.78f);
                    SkillSound.playAt(event.position(), SoundEvents.PLAYER_ATTACK_STRONG, 0.78f, 0.82f);
                    CameraShake.impulse(pos, 18, 0.34f, 85L, 185L);
                    if (actor != null && !ownFp) PlayerAfterimages.capture(actor, aiColor, 185, 340L, true);
                } else if (OracleSkills.SMILE.equals(skill)) {
                    SkillSound.playAt(event.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 0.72f, 1.32f + (event.sequence() & 1) * 0.16f);
                    SkillSound.playAt(event.position(), SoundEvents.PLAYER_ATTACK_CRIT, 0.56f, 1.42f);
                    CameraShake.impulse(pos, 12, 0.16f, 45L, 95L);
                } else if (OracleSkills.THRUST.equals(skill) || OracleSkills.RIFT.equals(skill)) {
                    SkillSound.playAt(event.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 0.74f, 1.36f);
                    SkillSound.playAt(event.position(), SoundEvents.PLAYER_ATTACK_CRIT, 0.62f, 1.50f);
                    CameraShake.impulse(pos, 14, 0.18f, 45L, 100L);
                } else if (OracleSkills.WILL.equals(skill)) {
                    if (event.slashAngle() > 0.5) {
                        SkillSound.playAt(event.position(), SoundEvents.MACE_SMASH_GROUND, 0.72f, gold ? 1.05f : 0.92f);
                        CameraShake.impulse(pos, 14, 0.22f, 55L, 120L);
                    }
                } else if (event.phase() == FINISH) {
                    // Furioso-Replica colossal spatial-cleave detonation!
                    SkillSound.playAt(event.position(), SoundEvents.MACE_SMASH_GROUND_HEAVY, 1.0f, 0.68f);
                    SkillSound.playAt(event.position(), SoundEvents.GLASS_BREAK, 0.85f, 0.82f);
                    SkillSound.playAt(event.position(), SoundEvents.TRIDENT_THUNDER.value(), 0.78f, 1.05f);
                    CameraShake.impulse(pos, 26, 0.56f, 165L, 320L);
                    if (actor != null) PlayerAfterimages.capture(actor, COL_GOLD_HOT, 225, 480L, true);
                } else {
                    // Replica prelude / combo hit cue
                    int step = (int) Math.round(event.slashAngle());
                    float pitch = OracleSkills.combo(step) ? 1.25f + (step - 8) * 0.07f : 1.08f;
                    SkillSound.playAt(event.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 0.66f, pitch);
                    SkillSound.playAt(event.position(), SoundEvents.PLAYER_ATTACK_CRIT, 0.42f, pitch + 0.15f);
                    if (actor != null) PlayerAfterimages.capture(actor, COL_GOLD_MAIN, 155, 270L, true);
                    CameraShake.impulse(pos, 14, OracleSkills.combo(step) ? 0.14f : 0.20f, 45L, 100L);
                }
            }
        }

        @Override public void render(LevelRenderContext context, Track track, long age, long time, List<Quad> out, Vec3 camera) {
            List<SpriteQuad> sp = context.sprites();
            SkillVisualEvent start = track.start;
            SkillVisualEvent event = track.last;
            String skill = start.skill();
            boolean gold = isGold(replica, start);
            Vec3 pos = vector(event.position());
            Vec3 dir = horizontalDir(vector(event.direction()));

            // Instruction-Triggered Prescript Seal Burst right at cast START (monochrome -> gold feedback!)
            if (!replica && gold && age >= 0 && age < 460L) {
                double iu = age / 460.0;
                double fade = 1.0 - iu;
                Vec3 origin = vector(start.origin());
                emitOracleSigil(sp, origin.add(0, 0.05, 0), 2.55 * (0.50 + 0.62 * EffectSprites.easeOutCubic(iu)), time * 0.004, true, (int) (225 * fade), true);
                emitTeleShatter(sp, origin.add(0, 1.15, 0), camera, 1.85, iu, true, (int) (220 * fade));
            }

            // 1. 沉重横批 (oracle_sweep) 0.5s Charge Rune Wheel
            double hScale = Math.max(1.0, start.horizontalScale());
            if (!replica && OracleSkills.SWEEP.equals(skill) && !track.released) {
                double cu = Math.clamp(age / 500.0, 0.0, 1.0);
                emitOracleSigil(sp, pos.add(0, 0.05, 0), (1.65 + 1.75 * cu) * Math.min(1.35, hScale), time * 0.0036, gold, (int) (145 + 95 * cu), true);
                EffectSprites.shockwave(sp, pos.add(0, 0.08, 0), 4.2 * (1.0 - 0.72 * cu), -time * 0.005, gold ? COL_GOLD_MAIN : COL_INK_SILVER, (int) (190 * (0.35 + 0.65 * cu)));
                var mc = Minecraft.getInstance();
                Player caster = mc == null || mc.level == null ? null : mc.level.getPlayerByUUID(start.caster());
                if (caster != null && !isOwnFirstPerson(start.caster())) {
                    var pose = Effects.VFX.playerPose(caster.getUUID(), time);
                    Vec3 tip = PlayerBoneAnchor.weaponTip(caster, pose == null ? null : pose.frame(), 1.35);
                    EffectSprites.glow(sp, tip, camera, 0.72 + 0.60 * cu, gold ? COL_GOLD_HOT : COL_INK_WHITE, 230);
                }
            }

            // 2. 重量下压 (oracle_down) brief overhead pressure telegraph (0..300ms)
            if (!replica && OracleSkills.DOWN.equals(skill) && age < 300L) {
                double du = Math.clamp(age / 300.0, 0.0, 1.0);
                emitOracleSigil(sp, pos.add(dir.scale(2.1 * hScale)).add(0, 0.05, 0), 2.1 * hScale * (0.65 + 0.45 * du), -time * 0.004, gold, (int) (155 * du), false);
            }

            // 3. 举刃对笑 (oracle_smile) Teleport Departure & Arrival Portal Rifts!
            if (!replica && OracleSkills.SMILE.equals(skill) && age < 520L) {
                double su = Math.clamp(age / 520.0, 0.0, 0.999);
                Vec3 dest = vector(start.origin());
                emitTeleShatter(sp, dest.add(0, 1.15, 0), camera, 2.15, su, gold, 240);
                Vec3 from = SMILE_DEPARTURES.get(start.cast());
                if (from != null && from.distanceToSqr(dest) > 0.35) {
                    emitTeleShatter(sp, from.add(0, 1.15, 0), camera, 2.05, su, gold, 225);
                    double lineFade = Math.clamp(1.0 - age / 340.0, 0.0, 1.0);
                    if (lineFade > 0.04) {
                        RibbonTrail.ribbon(sp, List.of(dest.add(0, 1.10, 0), from.lerp(dest, 0.5).add(0, 1.10, 0), from.add(0, 1.10, 0)), camera, 0.28 * lineFade, gold ? COL_GOLD_MAIN : COL_INK_SILVER, (int) (230 * lineFade));
                    }
                }
            }

            // 4. 直线突刺 (oracle_thrust) & 空间斩裂 (oracle_rift) Continuous Dash Effects!
            double rangeScale = Math.max(1.0, hScale / 1.5);
            boolean hasHitFlash = track.flashes.stream().anyMatch(f -> f.phase() == HIT);
            if (!replica && OracleSkills.THRUST.equals(skill) && age < 460L && !hasHitFlash) {
                double tu = Math.clamp(age / 460.0, 0.0, 0.999);
                Vec3 origin = vector(start.origin());
                double dashDist = 7.0 * rangeScale;
                Vec3 head = event.phase() == TRAIL ? pos : origin.add(dir.scale(Math.min(dashDist, age / 250.0 * dashDist)));
                emitPierceLance(sp, head.add(dir.scale(1.35 * rangeScale)).add(0, 1.08, 0), dir, 1.55 * hScale, 6.2 * rangeScale, tu, gold, 252);
                EffectSprites.shockwave(sp, origin.add(0, 0.12, 0), 2.8 * rangeScale * EffectSprites.easeOutCubic(tu), tu * 1.4, gold ? COL_GOLD_MAIN : COL_INK_SILVER, (int) (210 * (1.0 - tu)));
                RibbonTrail.push("oracle_thrust:" + start.cast(), head.add(0, 1.08, 0), 0.34 * hScale, gold ? COL_GOLD_MAIN : COL_INK_SILVER, 230, 230L, time);
                if (tu < 0.7) {
                    EffectSprites.glow(sp, head.add(dir.scale(1.9 * rangeScale)).add(0, 1.08, 0), camera, 1.35 * rangeScale * (1.0 - tu), gold ? COL_GOLD_HOT : COL_INK_WHITE, 240);
                }
            }
            if (!replica && OracleSkills.RIFT.equals(skill) && age < 720L && !hasHitFlash) {
                double ru = Math.clamp(age / 720.0, 0.0, 0.999);
                Vec3 origin = vector(start.origin());
                Vec3 dashEnd = origin.add(dir.scale(6.2 * rangeScale));
                Vec3 mid = origin.lerp(dashEnd, 0.5).add(0, 1.18, 0);
                // Reality-slicing jagged dimensional fissure suspended along the dash path!
                emitSpaceRift(sp, mid, dir, 4.25 * rangeScale, 2.25 * rangeScale, 22.0, ru, gold, 255);
                emitSpaceRift(sp, mid.add(0, 0.08, 0), dir, 3.95 * rangeScale, 1.85 * rangeScale, -36.0, ru, gold, 215);
                double fade = 1.0 - ru;
                LightningBolt.bolt(sp, origin.add(0, 1.14, 0), dashEnd.add(0, 1.14, 0), camera, start.seed() ^ (time / 48L), 10, 0.52, 0.078, gold ? COL_GOLD_HOT : COL_INK_WHITE, (int) (242 * fade), true);
                if (event.phase() == TRAIL) {
                    RibbonTrail.push("oracle_rift:" + start.cast(), pos.add(0, 1.12, 0), 0.42 * hScale, gold ? COL_GOLD_MAIN : COL_INK_SILVER, 232, 240L, time);
                }
            }

            // 5. Furioso-Replica Grand Arena Seal & Charging Pose Glow
            if (replica) {
                Vec3 arenaCenter = vector(start.origin());
                emitOracleSigil(sp, arenaCenter.add(0, 0.05, 0), 6.85, time * 0.0014, true, 210, true);
                emitOracleHaloGround(sp, arenaCenter.add(0, 0.07, 0), 4.85, -time * 0.0022, 175);
                if (event.sweep()) {
                    // Finisher windup pose (600ms before final 9-block cleave!)
                    emitOracleSigil(sp, pos.add(0, 0.06, 0), 3.65, time * 0.0055, true, 240, true);
                    double c = 1.0 - ((time % 320L) / 320.0);
                    EffectSprites.shockwave(sp, pos.add(0, 0.12, 0), 4.8 * c, -time * 0.006, COL_GOLD_HOT, (int) (225 * c));
                    EffectSprites.glow(sp, pos.add(0, 1.25, 0), camera, 1.85, COL_GOLD_HOT, 240);
                }
            }

            // 6. Active Flash Events (Bespoke Normal Skill Hits + Replica 17-Step Choreography!)
            for (SkillVisualEvent flash : track.flashes) {
                long elapsed = time - flash.at();
                long dur = flashDuration(replica, flash);
                if (elapsed < 0 || elapsed >= dur) continue;
                double u = Math.clamp(elapsed / (double) Math.max(1L, dur), 0.0, 0.999);
                renderFlash(sp, camera, track, flash, u, time);
            }

            // Replica trail ribbon during rapid combo & finisher dashes
            if (replica && event.phase() == TRAIL) {
                RibbonTrail.push("replica_dash:" + start.cast(), pos.add(0, 1.08, 0), 0.30, COL_GOLD_MAIN, 235, 260L, time);
            }
        }

        @Override public CameraPose camera(Track track, long time) {
            if (!replica) return null;
            var mc = Minecraft.getInstance();
            if (mc.player == null || mc.level == null) return null;
            Vec3 look = vector(track.start.direction()), origin = vector(track.start.origin()), side = new Vec3(-look.z, 0, look.x);
            Vec3 target = origin.add(look.scale(3)).add(0, 2, 0), point = origin.subtract(look.scale(10)).add(side.scale(5)).add(0, 7, 0);
            var wall = mc.level.clip(new ClipContext(target, point, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
            if (wall.getType() != HitResult.Type.MISS) point = wall.getLocation().add(look.scale(0.2));
            Vec3 delta = target.subtract(point);
            float yaw = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
            float pitch = (float) -Math.toDegrees(Math.atan2(delta.y, delta.horizontalDistance()));
            return new CameraPose(point, yaw, pitch);
        }
    }

    // --- Per-Flash Choreography (5 Bespoke Skills + Furioso-Replica) ---------

    private static void renderFlash(List<SpriteQuad> sp, Vec3 camera, Track track, SkillVisualEvent flash, double u, long time) {
        boolean replica = OracleSkills.REPLICA.equals(track.start.skill());
        boolean gold = isGold(replica, flash);
        Vec3 pos = vector(flash.position());
        Vec3 forward = horizontalDir(vector(flash.direction()));
        Vec3 center = pos.add(0, 1.12, 0);
        double fade = 1.0 - u;
        String skill = flash.skill();
        double hScale = Math.max(1.0, flash.horizontalScale());

        if (!replica) {
            if (flash.phase() == START) return;
            switch (skill) {
                case OracleSkills.DOWN -> renderDownHit(sp, camera, pos, forward, u, gold, hScale);
                case OracleSkills.SWEEP -> renderSweepRelease(sp, camera, pos, forward, u, gold, hScale);
                case OracleSkills.SMILE -> renderSmileHit(sp, camera, pos, forward, u, gold, flash.sequence(), hScale);
                case OracleSkills.THRUST -> {
                    emitPierceLance(sp, pos.add(forward.scale(3.8)).add(0, 1.12, 0), forward, 1.65 * hScale, 6.8, u, gold, 250);
                    emitInkSlash(sp, center.add(forward.scale(2.8)), forward, 18.0, 3.4 * hScale, u, true, gold, 235);
                }
                case OracleSkills.RIFT -> {
                    Vec3 mid = pos.add(forward.scale(3.0)).add(0, 1.18, 0);
                    emitSpaceRift(sp, mid, forward, 4.2, 2.1, -24.0, u, gold, 252);
                    emitInkSlash(sp, mid, forward, -16.0, 3.8 * hScale, u, false, gold, 238);
                    LightningBolt.bolt(sp, pos.add(0, 1.14, 0), pos.add(forward.scale(6.0)).add(0, 1.14, 0), camera, track.start.seed() ^ (time / 45L), 9, 0.44, 0.072, gold ? COL_GOLD_HOT : COL_INK_WHITE, (int) (235 * fade), true);
                }
                case OracleSkills.WILL -> {
                    double radius = Math.max(1.5, flash.horizontalScale());
                    emitOracleImpact(sp, pos.add(0, 0.07, 0), camera, radius * 1.15, u, gold, true, 235);
                    EffectSprites.shockwave(sp, pos.add(0, 0.12, 0), radius * 1.12 * EffectSprites.easeOutCubic(u), u * 1.8, gold ? COL_GOLD_MAIN : COL_INK_SILVER, (int) (235 * fade));
                    emitOracleSigil(sp, pos.add(0, 0.05, 0), radius * 0.92, time * 0.005, gold, (int) (185 * fade), false);
                }
                default -> {}
            }
            return;
        }

        // --- Furioso-Replica (Sacred Gold) ---
        int step = (int) Math.round(flash.slashAngle());
        if (flash.phase() == SEGMENT) {
            // Crisp weapon-swap / teleport arrival diamond crest on every Replica step!
            emitTeleShatter(sp, center, camera, step == OracleSkills.FINISH_STEP ? 3.25 : 2.05, u, true, 240);
            return;
        }
        if (flash.phase() == FINISH) {
            // Step 16 Finisher: Colossal 9-Block Space-Splitting Sacred Gold Cleave!
            Vec3 slashMid = pos.add(forward.scale(3.6)).add(0, 1.32, 0);
            emitSpaceRift(sp, slashMid, forward, 6.45, 3.15, 14.0, u, true, 255);
            emitSpaceRift(sp, slashMid.add(0, 0.18, 0), forward, 5.95, 2.75, -28.0, u, true, 235);
            emitInkSlash(sp, center.add(forward.scale(1.8)), forward, 6.0, 6.45 * hScale, u, false, true, 255);
            emitInkSlash(sp, center.add(forward.scale(1.8)).add(0, 0.28, 0), forward, -18.0, 5.85 * hScale, Math.min(0.999, u * 1.08), true, true, 238);
            emitCrossSmile(sp, slashMid, camera, 5.85, Math.min(0.999, u * 1.2), true, 252);
            emitOracleImpact(sp, slashMid, camera, 6.45, u, true, false, 248);
            EffectSprites.shockwave(sp, pos.add(0, 0.10, 0), 8.4 * EffectSprites.easeOutCubic(u), u * 1.9, COL_GOLD_MAIN, (int) (252 * fade));
            EffectSprites.shockwave(sp, pos.add(0, 1.45, 0), 6.8 * EffectSprites.easeOutCubic(u), -u * 1.5, COL_GOLD_HOT, (int) (225 * fade));
            LightningBolt.bolt(sp, pos.add(0, 1.15, 0), pos.add(forward.scale(9.0)).add(0, 1.28, 0), camera, (time / 42L) ^ track.start.seed(), 13, 0.75, 0.11 * fade + 0.035, COL_GOLD_HOT, (int) (252 * fade), true);
            return;
        }

        if (flash.phase() == HIT || flash.phase() == RELEASE) {
            if (OracleSkills.combo(step)) {
                // Steps 8..15: 8 High-Speed Crisscross Golden Dimensional Slash Web Lines (8.5 blocks!)
                Vec3 from = vector(flash.origin());
                Vec3 to = from.add(forward.scale(8.5));
                Vec3 mid = from.lerp(to, 0.5).add(0, 1.18 + ((step % 3) - 1) * 0.28, 0);
                double tilt = ((step % 5) - 2) * 26.0;
                emitSpaceRift(sp, mid, forward, 4.85, 1.55, tilt, u, true, 248);
                emitInkSlash(sp, center.add(forward.scale(1.5)), forward, tilt, 3.65 * hScale, u, (step & 1) == 1, true, 245);
                emitCrossSmile(sp, mid, camera, 2.85, u, true, 240);
                LightningBolt.bolt(sp, from.add(0, 1.12, 0), to.add(0, 1.12, 0), camera, (time / 45L) ^ step, 9, 0.42, 0.066, COL_GOLD_HOT, (int) (240 * fade), false);
            } else {
                // Steps 0..7 Prelude: Replay exact signature VFX for the weapon/action mimicked!
                String w = flash.weaponType();
                switch (w) {
                    case "BLUNT" -> renderDownHit(sp, camera, pos, forward, u, true, hScale);
                    case "KATANA" -> {
                        emitPierceLance(sp, center.add(forward.scale(2.8)), forward, 1.50 * hScale, 5.6, u, true, 248);
                        EffectSprites.shockwave(sp, pos.add(0, 0.10, 0), 2.8 * EffectSprites.easeOutCubic(u), u * 1.3, COL_GOLD_MAIN, (int) (210 * fade));
                    }
                    case "GREATSWORD" -> renderSweepRelease(sp, camera, pos, forward, u, true, hScale * 0.92);
                    case "SHORTSWORD" -> renderSmileHit(sp, camera, pos, forward, u, true, flash.sequence(), hScale);
                    case "LIGHTSABER" -> {
                        Vec3 mid = pos.add(forward.scale(3.0)).add(0, 1.18, 0);
                        emitSpaceRift(sp, mid, forward, 3.95, 1.95, 20.0, u, true, 250);
                        LightningBolt.bolt(sp, pos.add(0, 1.12, 0), pos.add(forward.scale(5.8)).add(0, 1.12, 0), camera, (time / 45L) ^ step, 9, 0.42, 0.070, COL_GOLD_HOT, (int) (240 * fade), true);
                    }
                    default -> emitInkSlash(sp, center.add(forward.scale(1.3)), forward, 0.0, 3.45 * hScale, u, false, true, 245);
                }
            }
        }
    }

    private static void renderDownHit(List<SpriteQuad> sp, Vec3 camera, Vec3 pos, Vec3 forward, double u, boolean gold, double scale) {
        double fade = 1.0 - u;
        Vec3 impactPt = pos.add(forward.scale(2.25 * scale));
        // Heavy vertical crushing slam + shattering prescript starburst on ground (matches 4.8x3.6 * meleeScale!)
        emitHeavyCrush(sp, pos.add(forward.scale(1.55 * scale)).add(0, 1.48 * Math.min(1.25, scale), 0), forward, 4.15 * scale, u, gold, 254);
        emitOracleImpact(sp, impactPt.add(0, 0.06, 0), camera, 4.35 * scale, u, gold, true, 248);
        EffectSprites.shockwave(sp, impactPt.add(0, 0.10, 0), 4.65 * scale * EffectSprites.easeOutCubic(u), u * 1.5, gold ? COL_GOLD_MAIN : COL_INK_SILVER, (int) (240 * fade));
    }

    private static void renderSweepRelease(List<SpriteQuad> sp, Vec3 camera, Vec3 pos, Vec3 forward, double u, boolean gold, double scale) {
        double fade = 1.0 - u;
        Vec3 c = pos.add(forward.scale(1.65 * scale)).add(0, 1.18, 0);
        // Colossal 145° calligraphic heavy sweep + echoing wide shock-arc (matches 5.8 * meleeScale = 8.7 blocks!)
        emitInkSlash(sp, c, forward, 0.0, 6.05 * scale, u, false, gold, 255);
        emitInkSlash(sp, c.add(0, 0.18, 0), forward, 14.0, 5.45 * scale, Math.min(0.999, u * 1.08), true, gold, 225);
        emitOracleImpact(sp, c.add(forward.scale(1.75 * scale)), camera, 3.75 * scale, u, gold, false, 230);
        EffectSprites.shockwave(sp, pos.add(0, 0.09, 0), 6.20 * scale * EffectSprites.easeOutCubic(u), u * 1.6, gold ? COL_GOLD_MAIN : COL_INK_SILVER, (int) (240 * fade));
    }

    private static void renderSmileHit(List<SpriteQuad> sp, Vec3 camera, Vec3 pos, Vec3 forward, double u, boolean gold, int seq, double scale) {
        boolean second = (seq & 1) == 0;
        Vec3 c = pos.add(forward.scale(1.65 * scale)).add(0, 1.20, 0);
        double roll = second ? -44.0 : 44.0;
        emitInkSlash(sp, c, forward, roll, 3.85 * scale, u, second, gold, 250);
        // Signature X-cross twin dagger flash blooms right across the prey (matches 4.8x3.6 * meleeScale!)
        emitCrossSmile(sp, pos.add(forward.scale(2.15 * scale)).add(0, 1.20, 0), camera, (second ? 3.85 : 3.05) * scale, u, gold, 252);
    }

    // --- Custom Dual-State Sprite Sheet Emitters -----------------------------

    private static void emitInkSlash(
        List<SpriteQuad> out, Vec3 center, Vec3 forward, double rollDeg,
        double radius, double progress, boolean mirror, boolean gold, int alpha
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
        int a = Math.clamp((int) Math.round(alpha * (1.0 - 0.36 * progress * progress)), 0, 255);

        Vec3 halfU = dir.scale(scale);
        Vec3 halfV = side.scale(scale);
        // Layer 1: Bold dark calligraphic ink / deep obsidian-gold body!
        EffectSprites.emitPlane(out, TEX_INK_SLASH, Blend.TRANSLUCENT, center, halfU.scale(1.06), halfV.scale(1.06), u0, 0f, u1, 1f, gold ? COL_GOLD_DARK : COL_INK_DARK, (int) (a * (gold ? 0.78 : 0.92)));
        // Layer 2: Crisp cold-silver or radiant Sacred Gold luminous edge!
        EffectSprites.emitPlane(out, TEX_INK_SLASH, Blend.ADDITIVE, center, halfU, halfV, u0, 0f, u1, 1f, gold ? COL_GOLD_MAIN : COL_INK_SILVER, (int) (a * (gold ? 0.96 : 0.80)));
        EffectSprites.emitPlane(out, TEX_INK_SLASH, Blend.ADDITIVE, center, halfU.scale(0.95), halfV.scale(0.95), u0, 0f, u1, 1f, gold ? COL_GOLD_HOT : COL_INK_WHITE, (int) (a * (gold ? 0.72 : 0.56)));
    }

    private static void emitHeavyCrush(List<SpriteQuad> out, Vec3 center, Vec3 forward, double radius, double progress, boolean gold, int alpha) {
        if (progress < 0.0 || progress >= 1.0 || radius <= 0.0 || alpha <= 4) return;
        Vec3 dir = horizontalDir(forward);
        double scale = radius * (0.88 + 0.20 * EffectSprites.easeOutCubic(progress));
        int frame = Math.clamp((int) (progress * 8), 0, 7);
        float u0 = frame / 8.0f, u1 = (frame + 1) / 8.0f;
        int a = Math.clamp((int) Math.round(alpha * (1.0 - 0.34 * progress * progress)), 0, 255);

        // In heavy_crush.png: -qy is sky top, +qy is ground smash -> halfV = -UP * scale
        Vec3 halfU = dir.scale(scale);
        Vec3 halfV = UP.scale(-scale);
        EffectSprites.emitPlane(out, TEX_HEAVY_CRUSH, Blend.TRANSLUCENT, center, halfU.scale(1.05), halfV.scale(1.05), u0, 0f, u1, 1f, gold ? COL_GOLD_DARK : COL_INK_DARK, (int) (a * (gold ? 0.80 : 0.94)));
        EffectSprites.emitPlane(out, TEX_HEAVY_CRUSH, Blend.ADDITIVE, center, halfU, halfV, u0, 0f, u1, 1f, gold ? COL_GOLD_MAIN : COL_INK_SILVER, (int) (a * (gold ? 0.96 : 0.82)));
        EffectSprites.emitPlane(out, TEX_HEAVY_CRUSH, Blend.ADDITIVE, center, halfU.scale(0.95), halfV.scale(0.95), u0, 0f, u1, 1f, gold ? COL_GOLD_HOT : COL_INK_WHITE, (int) (a * 0.65));
    }

    private static void emitPierceLance(
        List<SpriteQuad> out, Vec3 tipPos, Vec3 forward,
        double halfWidth, double length, double progress, boolean gold, int alpha
    ) {
        if (progress < 0.0 || progress >= 1.0 || halfWidth <= 0.0 || length <= 0.0 || alpha <= 4) return;
        Vec3 dir = horizontalDir(forward);
        Vec3 right = rightOf(dir);
        int frame = Math.clamp((int) (progress * 8), 0, 7);
        float u0 = frame / 8.0f, u1 = (frame + 1) / 8.0f;
        int a = Math.clamp((int) Math.round(alpha * (1.0 - 0.36 * progress * progress)), 0, 255);

        Vec3 center = tipPos.subtract(dir.scale(length * 0.45));
        Vec3 halfV = dir.scale(-length * 0.5);
        for (int i = 0; i < 2; i++) {
            Vec3 halfU = (i == 0 ? right : UP).scale(halfWidth);
            EffectSprites.emitPlane(out, TEX_PIERCE_LANCE, Blend.TRANSLUCENT, center, halfU.scale(1.06), halfV, u0, 0f, u1, 1f, gold ? COL_GOLD_DARK : COL_INK_DARK, (int) (a * (gold ? 0.72 : 0.90)));
            EffectSprites.emitPlane(out, TEX_PIERCE_LANCE, Blend.ADDITIVE, center, halfU, halfV, u0, 0f, u1, 1f, gold ? COL_GOLD_MAIN : COL_INK_SILVER, (int) (a * (gold ? 0.96 : 0.84)));
            EffectSprites.emitPlane(out, TEX_PIERCE_LANCE, Blend.ADDITIVE, center, halfU.scale(0.85), halfV, u0, 0f, u1, 1f, gold ? COL_GOLD_HOT : COL_INK_WHITE, (int) (a * 0.65));
        }
    }

    private static void emitSpaceRift(
        List<SpriteQuad> out, Vec3 center, Vec3 forward,
        double halfLen, double halfHeight, double rollDeg, double progress, boolean gold, int alpha
    ) {
        if (progress < 0.0 || progress >= 1.0 || halfLen <= 0.0 || halfHeight <= 0.0 || alpha <= 4) return;
        Vec3 dir = horizontalDir(forward);
        Vec3 right = rightOf(dir);
        double roll = Math.toRadians(rollDeg);
        Vec3 vAxis = UP.scale(Math.cos(roll)).add(right.scale(Math.sin(roll))).normalize();

        int frame = Math.clamp((int) (progress * 8), 0, 7);
        float u0 = frame / 8.0f, u1 = (frame + 1) / 8.0f;
        int a = Math.clamp((int) Math.round(alpha * (1.0 - 0.32 * progress * progress)), 0, 255);

        Vec3 halfU = dir.scale(halfLen);
        Vec3 halfV = vAxis.scale(-halfHeight);
        // Pitch-black dimensional void tear interior (TRANSLUCENT) + luminous rim fracture (ADDITIVE)!
        EffectSprites.emitPlane(out, TEX_SPACE_RIFT, Blend.TRANSLUCENT, center, halfU.scale(1.02), halfV.scale(1.06), u0, 0f, u1, 1f, 0x05060A, (int) (a * 0.96));
        EffectSprites.emitPlane(out, TEX_SPACE_RIFT, Blend.ADDITIVE, center, halfU, halfV, u0, 0f, u1, 1f, gold ? COL_GOLD_MAIN : COL_INK_SILVER, (int) (a * (gold ? 0.95 : 0.85)));
        EffectSprites.emitPlane(out, TEX_SPACE_RIFT, Blend.ADDITIVE, center, halfU.scale(0.98), halfV.scale(0.94), u0, 0f, u1, 1f, gold ? COL_GOLD_HOT : COL_INK_WHITE, (int) (a * 0.62));
    }

    private static void emitCrossSmile(List<SpriteQuad> out, Vec3 center, Vec3 camera, double radius, double progress, boolean gold, int alpha) {
        if (progress < 0.0 || progress >= 1.0 || radius <= 0.0 || alpha <= 4) return;
        int frame = Math.clamp((int) (progress * 8), 0, 7);
        double scale = radius * (0.82 + 0.28 * EffectSprites.easeOutCubic(progress));
        int a = Math.clamp((int) Math.round(alpha * (1.0 - 0.36 * progress * progress)), 0, 255);
        emitFramedBillboard(out, TEX_CROSS_SMILE, 8, frame, center, camera, scale, gold, a);
    }

    private static void emitOracleImpact(List<SpriteQuad> out, Vec3 center, Vec3 camera, double radius, double progress, boolean gold, boolean groundFlat, int alpha) {
        if (progress < 0.0 || progress >= 1.0 || radius <= 0.0 || alpha <= 4) return;
        int frame = Math.clamp((int) (progress * 8), 0, 7);
        float u0 = frame / 8.0f, u1 = (frame + 1) / 8.0f;
        double scale = radius * (0.62 + 0.50 * EffectSprites.easeOutCubic(progress));
        int a = Math.clamp((int) Math.round(alpha * (1.0 - 0.38 * progress * progress)), 0, 255);
        if (groundFlat) {
            EffectSprites.emitGround(out, TEX_ORACLE_IMPACT, Blend.TRANSLUCENT, center, scale * 1.04, progress * 0.5, u0, 0f, u1, 1f, gold ? COL_GOLD_DARK : COL_INK_DARK, (int) (a * (gold ? 0.72 : 0.88)));
            EffectSprites.emitGround(out, TEX_ORACLE_IMPACT, Blend.ADDITIVE, center.add(0, 0.018, 0), scale, progress * 0.5, u0, 0f, u1, 1f, gold ? COL_GOLD_MAIN : COL_INK_SILVER, (int) (a * 0.90));
        } else {
            emitFramedBillboard(out, TEX_ORACLE_IMPACT, 8, frame, center, camera, scale, gold, a);
        }
    }

    private static void emitTeleShatter(List<SpriteQuad> out, Vec3 center, Vec3 camera, double radius, double progress, boolean gold, int alpha) {
        if (progress < 0.0 || progress >= 1.0 || radius <= 0.0 || alpha <= 4) return;
        int frame = Math.clamp((int) (progress * 8), 0, 7);
        int a = Math.clamp((int) Math.round(alpha * (1.0 - 0.38 * progress * progress)), 0, 255);
        emitFramedBillboard(out, TEX_TELE_SHATTER, 8, frame, center, camera, radius, gold, a);
    }

    private static void emitOracleSigil(List<SpriteQuad> out, Vec3 center, double radius, double rotRad, boolean gold, int alpha, boolean doubleLayer) {
        if (radius <= 0.0 || alpha <= 4) return;
        int a = Math.clamp(alpha, 0, 255);
        if (doubleLayer) {
            EffectSprites.emitGround(out, TEX_ORACLE_SIGIL, Blend.TRANSLUCENT, center, radius * 1.03, -rotRad * 0.45, 0f, 0f, 1f, 1f, gold ? COL_GOLD_DARK : COL_INK_DARK, (int) (a * (gold ? 0.62 : 0.80)));
        }
        EffectSprites.emitGround(out, TEX_ORACLE_SIGIL, Blend.ADDITIVE, center.add(0, 0.014, 0), radius, rotRad, 0f, 0f, 1f, 1f, gold ? COL_GOLD_MAIN : COL_INK_SILVER, a);
    }

    private static void emitOracleHaloGround(List<SpriteQuad> out, Vec3 center, double radius, double rotRad, int alpha) {
        if (radius <= 0.0 || alpha <= 4) return;
        EffectSprites.emitGround(out, TEX_ORACLE_HALO, Blend.ADDITIVE, center, radius, rotRad, 0f, 0f, 1f, 1f, COL_GOLD_MAIN, Math.clamp(alpha, 0, 255));
    }

    private static void emitFramedBillboard(
        List<SpriteQuad> out, Identifier texture, int totalFrames, int frame,
        Vec3 center, Vec3 camera, double radius, boolean gold, int alpha
    ) {
        if (radius <= 0.0 || alpha <= 2) return;
        float u0 = frame / (float) totalFrames;
        float u1 = (frame + 1) / (float) totalFrames;
        Vec3 look = camera.subtract(center);
        if (look.lengthSqr() < 1e-6) look = new Vec3(0, 0, 1);
        else look = look.normalize();
        Vec3 right = look.cross(Math.abs(look.y) > 0.95 ? new Vec3(1, 0, 0) : UP).normalize();
        Vec3 up = right.cross(look).normalize();
        Vec3 halfU = right.scale(radius);
        Vec3 halfV = up.scale(-radius);

        EffectSprites.emitPlane(out, texture, Blend.TRANSLUCENT, center, halfU.scale(1.05), halfV.scale(1.05), u0, 0f, u1, 1f, gold ? COL_GOLD_DARK : COL_INK_DARK, (int) (alpha * (gold ? 0.72 : 0.88)));
        EffectSprites.emitPlane(out, texture, Blend.ADDITIVE, center.add(look.scale(0.025)), halfU, halfV, u0, 0f, u1, 1f, gold ? COL_GOLD_MAIN : COL_INK_SILVER, (int) (alpha * (gold ? 0.96 : 0.82)));
        EffectSprites.emitPlane(out, texture, Blend.ADDITIVE, center.add(look.scale(0.04)), halfU.scale(0.92), halfV.scale(0.92), u0, 0f, u1, 1f, gold ? COL_GOLD_HOT : COL_INK_WHITE, (int) (alpha * 0.62));
    }

    // --- Pure Helpers --------------------------------------------------------

    private static boolean isGold(boolean replica, SkillVisualEvent event) {
        if (replica || OracleSkills.REPLICA.equals(event.skill())) return true;
        return event.sweep() || event.color() == COL_GOLD_MAIN || event.color() == 0xf2cb72;
    }

    private static long flashDuration(boolean replica, SkillVisualEvent flash) {
        if (flash.phase() == FINISH) return 760L;
        if (flash.phase() == SEGMENT) return 260L;
        if (replica && OracleSkills.combo((int) Math.round(flash.slashAngle()))) return 320L;
        return 420L;
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
