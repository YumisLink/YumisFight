package cn.yumis.fight.modules.skills.client;

import cn.yumis.fight.modules.attributes.api.Stats;
import cn.yumis.fight.modules.character.api.JobCatalog;
import cn.yumis.fight.modules.combat.api.ActionStatus;
import cn.yumis.fight.modules.combat.api.CombatContentTypes;
import cn.yumis.fight.modules.combat.api.ElementAccumulation;
import cn.yumis.fight.modules.dungeons.api.Target;
import cn.yumis.fight.modules.dungeons.api.TowerBattleCard;
import cn.yumis.fight.modules.presentation.api.client.ClientUi;
import cn.yumis.fight.modules.skills.api.*;
import cn.yumis.fight.modules.skills.api.client.SkillController;
import cn.yumis.fight.modules.skills.api.client.SkillsClient;
import cn.yumis.fight.modules.skills.api.client.TrainingCombat;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;
import java.util.*;

/**
 * Owning-client runtime for Witch (魔道学者 / 魔术师):
 * - Elytra-style broomstick low-altitude gliding with riding pose + mid-air broom dashes
 * - Improved Magic Star (bouncing Light star with solo echo)
 * - Shadow Cloak (invulnerable dark gather + burst)
 * - Giant Flyswatter (dark slam + Lucky Candy Blessing + 2~3 hopping 4-element mini-familiar puppets)
 * - Lava Potion (air-throwable Fire pool + 3 Great Success geysers)
 * - Acid Rain (air-castable Ice rain + 4 Great Success Light thunderbolts)
 * - Anti-Gravity (deployable Dark lift + slam)
 * - Riding machines: Raging Flame Furnace, drivable Mega Drill, Florae Collider, Awakening Fusion Craft
 *   all with 2nd-press Bitter Lollipop (苦涩炸炉) 85% early detonation!
 */
public final class WitchClient {
    private static final long MOTION_SYNC_INTERVAL_MS = 250L;
    private static final long LANDING_FALL_GRACE_MS = 1600L;
    private static final long TAKEOFF_GLIDE_DELAY_MS = 180L;
    private static final long GLIDE_TOGGLE_DEBOUNCE_MS = 260L;

    // --- Runtime states ---

    private static final class ImprovedStarState {
        SkillCast cast;
        String visualId;
        Vec3 pos, prevPos, dir;
        double speed, rangeLeft, blastRadius, bounceRange, soloEchoRatio;
        int bouncesLeft, bouncesDone, element, sequence;
        boolean bouncing, soloEchoPending, allowRepeat;
        UUID targetUuid;
        long start, lastTick, nextBounceAt;
        final Set<UUID> struck = new HashSet<>();
    }

    private static final class CloakState {
        SkillCast cast;
        Vec3 center, pullPoint, dir;
        double radius, pullRadius, controlSeconds;
        long start, grabAt, burstAt, invulnUntil;
        boolean grabbed;
        int element, sequence;
    }

    private static final class FlyswatterState {
        SkillCast cast;
        Vec3 center, dir;
        double radius;
        long start, impactAt;
        int element, sequence;
        boolean megaCard;
    }

    private static final class FamiliarPuppetState {
        SkillCast cast;
        String visualId;
        int element; // 0=Florae, 1=Jack, 2=Frost, 3=Pluto
        Vec3 pos, vel;
        double radius, seekRange, coeff;
        long spawnAt, activeAt, expiresAt, lastTick;
        boolean spawned, exploded;
        int sequence;
    }

    private static final class LavaPoolState {
        SkillCast cast;
        Vec3 origin, center;
        double radius, geyserRadius, slowRatio, fireResistShred;
        long start, landAt, endAt, intervalMs, nextTickAt;
        int totalTicks, delivered, geyserCount, geysersFired, element, sequence;
        boolean landed;
    }

    private static final class AcidCloudState {
        SkillCast cast;
        Vec3 center;
        double radius, boltRadius, cloudHeight;
        long start, spawnAt, endAt, intervalMs, nextTickAt, boltIntervalMs, nextBoltAt;
        int totalTicks, delivered, boltCount, boltsFired, rainElement, boltElement, sequence;
        boolean spawned;
    }

    private static final class AntiGravityState {
        SkillCast cast;
        Vec3 center;
        double radius, blastRadius, controlSeconds;
        long start, liftAt, slamAt;
        boolean lifted, gatherMode;
        int element, sequence;
    }

    public static final class MachineChannelState {
        public SkillCast cast;
        public String skillId;
        public Vec3 anchor, dir;
        public double radius, blastRadius, pullRadius, pullStrength, driveSpeedRatio;
        public double controlSeconds, bitterControlSeconds, channelProtection, bitterKeepRatio;
        public double bitterLaunchHeight, bitterDashDistance;
        public long start, windupEndAt, endAt, intervalMs, nextTickAt, minChannelMs, lastTick;
        public int totalTicks, delivered, element, sequence;
        public boolean invulnerable, drivable, autonomous, icebreaker, finished;
    }

    // --- Active runtime lists ---
    private static final List<ImprovedStarState> stars = new ArrayList<>();
    private static final List<CloakState> cloaks = new ArrayList<>();
    private static final List<FlyswatterState> swatters = new ArrayList<>();
    private static final List<FamiliarPuppetState> puppets = new ArrayList<>();
    private static final List<LavaPoolState> lavaPools = new ArrayList<>();
    private static final List<AcidCloudState> acidClouds = new ArrayList<>();
    private static final List<AntiGravityState> antiGravities = new ArrayList<>();
    private static final List<MachineChannelState> deployedMachines = new ArrayList<>();
    private static final Map<UUID, Long> fireShredUntil = new HashMap<>();
    private static final Map<UUID, Long> shatteredFreezeUntil = new HashMap<>();
    private static int shatterSequence;
    private static MachineChannelState activeMachine;

    // --- Broomstick Elytra-style glide state ---
    private static boolean broomGliding;
    private static long glideStartedAt;
    private static long airborneSinceAt;
    private static long fallProtectionUntil;
    private static int airDashesUsed;
    private static boolean lastOnGround = true;
    private static boolean lastJumpKeyDown = false;
    private static long cloakInvulnUntil;
    private static long fusionGraceInvulnUntil;
    private static Vec3 pendingMachineVelocity;

    private static final Map<UUID, Vec3> pendingMotions = new LinkedHashMap<>();
    private static long lastMotionSendAt = 0L;
    private static final Random RNG = new Random();

    private WitchClient() {}

    private static SkillsClient state() { return SkillsClient.current(); }
    private static LocalPlayer player() { return Minecraft.getInstance().player; }

    public static boolean isWitch() {
        return state().ready() && state().progress().hasJob(JobCatalog.WITCH);
    }

    public static boolean hasBroomEquipped() {
        if (!isWitch() || !SkillController.INSTANCE.validWeapon()) return false;
        return "BROOM".equals(state().stats().weaponType);
    }

    public static boolean isBroomGliding() {
        return broomGliding;
    }

    public static boolean isGlidingPlayer(UUID uuid) {
        var p = player();
        if (p != null && p.getUUID().equals(uuid)) return broomGliding;
        for (var actor : CareerClient.INSTANCE.view.actors) {
            if (actor.owner.equals(uuid.toString()) && actor.witchBroomGliding) return true;
        }
        return false;
    }

    public static String activeMachineForPlayer(UUID uuid) {
        var p = player();
        if (p != null && p.getUUID().equals(uuid)) {
            return activeMachine == null ? "" : activeMachine.skillId;
        }
        for (var actor : CareerClient.INSTANCE.view.actors) {
            if (actor.owner.equals(uuid.toString()) && actor.witchMachine != null && !actor.witchMachine.isEmpty()) {
                return actor.witchMachine;
            }
        }
        return "";
    }

    public static MachineChannelState activeMachine() {
        return activeMachine;
    }

    /** Fire resistance reduction applied by card-enhanced Lava Potion pools on enemies. */
    public static double fireResistShred(LivingEntity entity, long now) {
        if (entity == null) return 0.0;
        double shred = 0.0;
        Vec3 targetCenter = entity.getBoundingBox().getCenter();
        double width = entity.getBbWidth();
        for (var st : lavaPools) {
            if (st.landed && st.fireResistShred > shred && now <= st.endAt
                && inPool(st.center, st.radius, targetCenter, width)) {
                shred = st.fireResistShred;
            }
        }
        if (shred <= 0.0 && fireShredUntil.getOrDefault(entity.getUUID(), 0L) > now) {
            shred = TowerBattleCard.WITCH_LAVA.value("shred");
        }
        return shred;
    }

    public static boolean inPool(Vec3 center, double radius, Vec3 targetCenter, double targetWidth) {
        double dx = targetCenter.x - center.x;
        double dz = targetCenter.z - center.z;
        double dy = Math.abs(targetCenter.y - (center.y + 0.8));
        return dx * dx + dz * dz <= radius * radius + targetWidth * 0.5
            && dy <= Math.max(3.8, radius * 0.85);
    }

    static Vec3 puppetPosition(UUID visualId) {
        if (visualId == null) return null;
        String id = visualId.toString();
        for (var pup : puppets) {
            if (id.equals(pup.visualId)) return pup.pos;
        }
        return null;
    }

    public static boolean protectsFromFall() {
        return broomGliding || ClientUi.now() < fallProtectionUntil || activeMachine != null;
    }

    public static boolean invulnerable() {
        long now = ClientUi.now();
        if (now < cloakInvulnUntil || now < fusionGraceInvulnUntil) return true;
        return activeMachine != null && activeMachine.invulnerable && !activeMachine.finished;
    }

    public static boolean blocksMovement() {
        if (activeMachine == null || activeMachine.finished) return false;
        return !activeMachine.drivable;
    }

    public static boolean blocksBasic() {
        return activeMachine != null && !activeMachine.finished;
    }

    public static double careerDamageTaken(double normal) {
        if (activeMachine != null && !activeMachine.finished && !activeMachine.invulnerable) {
            double prot = Math.clamp(activeMachine.channelProtection, 0.0, 0.95);
            return Math.min(normal, 1.0 - prot);
        }
        return normal;
    }

    /** Supplies Elytra-style broom gliding velocity or Mega Drill / Fusion Craft driving velocity to DemonTravelMixin. */
    public static Vec3 movementVelocity() {
        if (pendingMachineVelocity != null) {
            Vec3 out = pendingMachineVelocity;
            pendingMachineVelocity = null;
            return out;
        }
        var p = player();
        if (p == null || !state().ready() || state().own().dead) return null;
        if (activeMachine != null && !activeMachine.finished && activeMachine.drivable) {
            return computeMachineDriveVelocity(p, activeMachine);
        }
        if (!broomGliding) return null;
        if (!canSustainBroomGlide(p)) {
            stopBroomGlide(true);
            return null;
        }
        p.resetFallDistance();
        return WitchRules.broomGlideStep(p.getDeltaMovement(), p.getLookAngle(), p.getXRot());
    }

    private static Vec3 computeMachineDriveVelocity(LocalPlayer p, MachineChannelState m) {
        var mc = Minecraft.getInstance();
        double fwd = (mc.options.keyUp.isDown() ? 1.0 : 0.0) - (mc.options.keyDown.isDown() ? 1.0 : 0.0);
        double strafe = (mc.options.keyLeft.isDown() ? 1.0 : 0.0) - (mc.options.keyRight.isDown() ? 1.0 : 0.0);
        Vec3 look = horizontal(p.getLookAngle());
        Vec3 right = new Vec3(-look.z, 0.0, look.x);
        Vec3 moveDir;
        if (Math.abs(fwd) + Math.abs(strafe) > 0.01) {
            moveDir = look.scale(fwd).add(right.scale(-strafe));
            if (moveDir.lengthSqr() > 1e-6) moveDir = moveDir.normalize();
        } else if (WitchSkills.MEGA_DRILL.equals(m.skillId)) {
            // Mega Drill automatically creeps forward gently unless player reverses
            moveDir = look.scale(0.35);
        } else {
            moveDir = Vec3.ZERO;
        }
        double baseSpeed = p.getAttributeValue(Attributes.MOVEMENT_SPEED) * 2.2 * m.driveSpeedRatio;
        double speed = Math.clamp(baseSpeed, 0.14, 0.46);
        Vec3 cur = p.getDeltaMovement();
        double vy = p.onGround() ? Math.max(-0.08, cur.y) : Math.max(-0.6, cur.y - 0.06);
        return new Vec3(moveDir.x * speed, vy, moveDir.z * speed);
    }

    /** Handles Space mid-air toggle for Elytra-style broomstick gliding and LEAP mid-air broom boost. */
    public static boolean handleKey(KeyEvent event, int action) {
        var mc = Minecraft.getInstance();
        var p = mc.player;
        if (p == null || mc.gui.screen() != null || !isWitch()) return false;
        if (action == GLFW.GLFW_PRESS && mc.options.keyJump.matches(event)) {
            long now = ClientUi.now();
            if (broomGliding) {
                if (now - glideStartedAt >= GLIDE_TOGGLE_DEBOUNCE_MS) {
                    stopBroomGlide(true);
                    return false;
                }
            } else if (canStartBroomGlide(p, now)) {
                startBroomGlide(p, now);
                return true;
            }
        }
        return false;
    }

    /** Called by CloudMovement before normal Cloud Leap so Witch holding BROOM gets Elytra-firework air boost! */
    public static boolean tryBroomAirDash(Stamina pool, long tick, LocalPlayer p, boolean exempt) {
        if (!hasBroomEquipped() || p.onGround() || activeMachine != null || p.isInWater() || p.isInLava()) return false;
        if (p.isDeadOrDying() || CombatContentTypes.controlled(p) || p.hasEffect(CombatContentTypes.RITUAL_ROOT)) return false;
        var mp = WitchRules.masteryParams(SkillController.INSTANCE.snapshot(), state().progress());
        int maxDashes = Math.max(1, (int) Math.round(mp.get("broomAirDashCount", 2.0)));
        if (airDashesUsed >= maxDashes) return false;
        if (!pool.ready(tick)) return true;
        boolean ok = pool.use(tick, exempt || cn.yumis.fight.modules.combat.api.CombatEffect.waivesMovementStamina(p));
        if (!ok) {
            state().message("体力不足（需要" + Math.round(cn.yumis.fight.modules.combat.api.CombatBalanceRules.current().sustain.staminaCost()) + "）");
            return true;
        }
        airDashesUsed++;
        long now = ClientUi.now();
        if (!broomGliding) {
            startBroomGlide(p, now);
        }
        double boost = Math.clamp(mp.get("broomDashSpeed", 1.15), 0.75, 1.65);
        Vec3 look = p.getLookAngle().normalize();
        Vec3 launch = new Vec3(look.x * boost, Math.clamp(look.y * boost + 0.16, -0.35, 0.72), look.z * boost);
        p.setDeltaMovement(launch);
        p.setOnGround(false);
        p.resetFallDistance();
        fallProtectionUntil = now + LANDING_FALL_GRACE_MS;
        p.addEffect(new MobEffectInstance(CombatContentTypes.CLOUD_STEP, Stamina.protectionTicks(), 0, false, false, true));
        SkillSound.playAt(p.getX(), p.getY(), p.getZ(), SoundEvents.FIREWORK_ROCKET_LAUNCH, 0.55f, 1.35f);
        WitchVisuals.onBroomDash(p);
        return true;
    }

    private static boolean canStartBroomGlide(LocalPlayer p, long now) {
        if (!hasBroomEquipped() || p.onGround() || p.isInWater() || p.isInLava()) return false;
        if (p.isDeadOrDying() || p.isPassenger() || p.isSleeping() || p.isSpectator() || p.getAbilities().flying) return false;
        if (CombatContentTypes.controlled(p) || p.hasEffect(CombatContentTypes.RITUAL_ROOT)) return false;
        if (activeMachine != null || SkillController.INSTANCE.locked()) return false;
        return airborneSinceAt > 0 && now - airborneSinceAt >= TAKEOFF_GLIDE_DELAY_MS;
    }

    private static boolean canSustainBroomGlide(LocalPlayer p) {
        if (!hasBroomEquipped() || p.onGround() || p.isInWater() || p.isInLava()) return false;
        if (p.isDeadOrDying() || p.isPassenger() || p.isSleeping() || p.isSpectator() || p.getAbilities().flying) return false;
        if (CombatContentTypes.controlled(p) || p.hasEffect(CombatContentTypes.RITUAL_ROOT)) return false;
        if (activeMachine != null) return false;
        if (Minecraft.getInstance().options.keyShift.isDown()) return false;
        return true;
    }

    private static void startBroomGlide(LocalPlayer p, long now) {
        broomGliding = true;
        glideStartedAt = now;
        fallProtectionUntil = now + LANDING_FALL_GRACE_MS;
        Vec3 cur = p.getDeltaMovement();
        Vec3 horizLook = horizontal(p.getLookAngle());
        double horizSpeed = Math.max(0.52, cur.horizontalDistance());
        p.setDeltaMovement(new Vec3(horizLook.x * horizSpeed, Math.max(cur.y, -0.04), horizLook.z * horizSpeed));
        p.resetFallDistance();
        p.addEffect(new MobEffectInstance(CombatContentTypes.CLOUD_STEP, Stamina.protectionTicks(), 0, false, false, true));
        CareerClient.INSTANCE.view.witch.broomGliding = true;
        CareerClient.INSTANCE.publish();
        SkillSound.playAt(p.getX(), p.getY(), p.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, 0.65f, 1.35f);
    }

    public static void stopBroomGlide(boolean grantLandingGrace) {
        if (!broomGliding) return;
        broomGliding = false;
        if (grantLandingGrace) {
            fallProtectionUntil = ClientUi.now() + LANDING_FALL_GRACE_MS;
            var p = player();
            if (p != null) {
                p.resetFallDistance();
                p.addEffect(new MobEffectInstance(CombatContentTypes.CLOUD_STEP, Stamina.protectionTicks(), 0, false, false, true));
            }
        }
        CareerClient.INSTANCE.view.witch.broomGliding = false;
        CareerClient.INSTANCE.publish();
    }

    // --- Hooks & Registration ---

    static void installHooks() {
        SkillCastHooks.onRequest(SkillCastHooks.Stage.BEFORE_REQUEST, req -> {
            if (!beforeRequest(req.skill)) req.rejected = true;
        });
        SkillCastHooks.onRequest(SkillCastHooks.Stage.PREPARE_STATS, req -> {
            if (req.progress.hasJob(JobCatalog.WITCH) && WitchSkills.contains(req.skill.id)) {
                req.prepared = WitchRules.prepareCastStats(req.prepared, req.parameters);
            }
        });
    }

    public static void install() {
        MageClient.registerAction(WitchSkills.SHADOW_CLOAK, WitchClient::startShadowCloak);
        MageClient.registerAction(WitchSkills.GIANT_FLYSWATTER, WitchClient::startGiantFlyswatter);
        MageClient.register(WitchSkills.LAVA_POTION, WitchClient::startLavaPotion);
        MageClient.register(WitchSkills.ACID_RAIN, WitchClient::startAcidRain);
        MageClient.register(WitchSkills.ANTI_GRAVITY, WitchClient::startAntiGravity);
        MageClient.registerAction(WitchSkills.RAGING_FLAME_FURNACE, WitchClient::startFurnace);
        MageClient.registerAction(WitchSkills.MEGA_DRILL, WitchClient::startMegaDrill);
        MageClient.registerAction(WitchSkills.FLORAE_COLLIDER, WitchClient::startFloraeCollider);
        MageClient.registerAction(WitchSkills.FUSION_CRAFT, WitchClient::startFusionCraft);
    }

    public static boolean beforeRequest(SkillDefinition skill) {
        if (activeMachine != null && !activeMachine.finished) {
            // Re-pressing same machine key is handled by tryBitterDetonate BEFORE request
            if (activeMachine.skillId.equals(skill.id)) return false;
            // Casting another skill while riding a non-ultimate machine triggers Bitter Lollipop cleanly or waits
            return false;
        }
        if (broomGliding && !WitchRules.isAirCastable(skill.id)) {
            stopBroomGlide(true);
        }
        return true;
    }

    /** Second-press Bitter Lollipop (苦涩炸炉): pressing the active machine skill key again detonates immediately! */
    public static boolean tryBitterDetonate(String skillId) {
        if (activeMachine == null || activeMachine.finished) return false;
        if (!activeMachine.skillId.equals(skillId)) return false;
        finishMachine(activeMachine, ClientUi.now(), true);
        return true;
    }

    public static boolean cancelChannel() {
        if (activeMachine == null || activeMachine.finished) return false;
        finishMachine(activeMachine, ClientUi.now(), true);
        return true;
    }

    public static void cancelOnScreen() {
        if (broomGliding) stopBroomGlide(true);
        if (activeMachine != null && !activeMachine.finished) {
            abortMachine(activeMachine);
        }
    }

    // --- 0. Improved Magic Star (改良魔法星弹) ---

    static boolean tryStartImprovedStar(SkillCast c) {
        if (!isWitch()) return false;
        var p = player();
        if (p == null) return false;
        long now = ClientUi.now();
        Vec3 aim = p.getLookAngle().normalize();
        Vec3 from = p.getEyePosition();
        double range = c.parameters.get("range", 16.0);
        boolean repeatCard = TowerBattleCard.WITCH_STAR.held(c.stats);
        var spawn = cn.yumis.fight.modules.combat.api.ProjectileSpawn.inWorld(p, from, aim, range, 0.18, SkillController::canTarget);
        var s = new ImprovedStarState();
        s.cast = c;
        s.visualId = c.id;
        s.pos = s.prevPos = spawn.position();
        s.dir = aim;
        s.speed = Math.max(20.0, c.parameters.get("projectileSpeed", 26.0));
        s.rangeLeft = Math.max(16.0, range);
        s.blastRadius = Math.max(1.2, c.parameters.get("witchBlastRadius", 2.2));
        s.bounceRange = Math.max(3.0, c.parameters.get("witchBounceRange", 5.5));
        s.bouncesLeft = Math.max(1, (int) Math.round(c.parameters.get("witchBounceCount", 3.0)));
        s.soloEchoRatio = Math.clamp(c.parameters.get("witchSoloEchoRatio", 0.60), 0.1, 1.0);
        s.allowRepeat = repeatCard;
        s.element = WitchRules.effectiveElement(c.parameters, c.stats, 0);
        s.start = s.lastTick = now;
        stars.add(s);
        TrainingCombat.hold(c.id, "witch_star", now + (repeatCard ? 4800L : 3500L));
        var motion = witchMotion(s.element, 0);
        motion.addProperty("velocity", s.speed);
        motion.addProperty("range", s.rangeLeft);
        motion.addProperty("radius", s.blastRadius);
        motion.addProperty("improvedStar", true);
        MageClient.INSTANCE.visual(c, s.visualId, "START", s.pos, s.dir, repeatCard ? 3000L : 1400L, 0, motion);
        PlayerPoseChannel.release(c);
        return true;
    }

    // --- 1. Lv20 Shadow Cloak (暗影斗篷) ---

    private static void startShadowCloak(SkillCast c) {
        var p = player();
        if (p == null) return;
        long now = ClientUi.now();
        Vec3 dir = horizontal(p.getLookAngle());
        double offset = c.parameters.get("centerOffset", 2.8);
        Vec3 center = p.position().add(dir.scale(offset));
        Vec3 pullPoint = p.position().add(dir.scale(2.4));
        long invulnMs = Math.max(200L, Math.round(c.parameters.get("invulnerableMillis", 650.0)));
        long grabMs = Math.max(40L, Math.round(c.parameters.get("grabDelay", 0.12) * 1000.0 / c.speed));
        long burstMs = Math.max(grabMs + 120L, Math.round(c.parameters.get("burstDelay", 0.45) * 1000.0 / c.speed));

        cloakInvulnUntil = Math.max(cloakInvulnUntil, now + invulnMs);
        SkillController.INSTANCE.acquireStatus(c.id, ActionStatus.INVULNERABLE, now + invulnMs);

        var st = new CloakState();
        st.cast = c;
        st.center = center;
        st.pullPoint = pullPoint;
        st.dir = dir;
        st.radius = c.parameters.get("radius", 4.5);
        st.pullRadius = c.parameters.get("pullRadius", 5.0);
        st.controlSeconds = c.parameters.get("controlSeconds", 0.65);
        st.start = now;
        st.grabAt = now + grabMs;
        st.burstAt = now + burstMs;
        st.invulnUntil = now + invulnMs;
        st.element = WitchRules.effectiveElement(c.parameters, c.stats, 3);
        cloaks.add(st);
        TrainingCombat.hold(c.id, "witch_cloak", now + burstMs + 600L);

        var motion = witchMotion(st.element, 0);
        motion.addProperty("radius", st.radius);
        motion.addProperty("pullRadius", st.pullRadius);
        MageClient.INSTANCE.visual(c, c.id, "START", center, dir, burstMs + 550L, 0, motion);
    }

    // --- 2. Lv20 Giant Flyswatter (变异苍蝇拍) ---

    private static void startGiantFlyswatter(SkillCast c) {
        var p = player();
        if (p == null) return;
        long now = ClientUi.now();
        Vec3 dir = horizontal(p.getLookAngle());
        double offset = c.parameters.get("centerOffset", 2.8);
        Vec3 center = p.position().add(dir.scale(offset));
        long delayMs = Math.max(60L, Math.round(c.parameters.get("impactDelay", 0.22) * 1000.0 / c.speed));
        boolean megaCard = TowerBattleCard.WITCH_SWAT.held(c.stats);

        var st = new FlyswatterState();
        st.cast = c;
        st.center = center;
        st.dir = dir;
        st.radius = c.parameters.get("radius", 3.8);
        st.start = now;
        st.impactAt = now + delayMs;
        st.element = WitchRules.effectiveElement(c.parameters, c.stats, 3);
        st.megaCard = megaCard;
        swatters.add(st);
        TrainingCombat.hold(c.id, "witch_swat", now + delayMs + 2400L);

        if (megaCard) {
            PlayerPoseChannel.play(c, "giant_flyswatter_giant", PlayerPoseChannel.scaled(500L, c.speed), dir, true);
        }

        var motion = witchMotion(st.element, 0);
        motion.addProperty("radius", st.radius);
        MageClient.INSTANCE.visual(c, c.id, "START", center, dir, delayMs + 750L, 0, motion);
    }

    // --- 3. Lv25 Lava Potion (熔岩药瓶) ---

    private static void startLavaPotion(SkillCast c) {
        var p = player();
        if (p == null) return;
        long now = ClientUi.now();
        double range = c.parameters.get("range", 16.0);
        Vec3 target = groundTargetPoint(p, range);
        long throwMs = Math.max(80L, Math.round(c.parameters.get("throwDelay", 0.25) * 1000.0));
        long durMs = Math.max(1000L, Math.round(c.parameters.get("duration", 8.0) * 1000.0));
        long intMs = Math.max(100L, Math.round(c.parameters.get("interval", 0.50) * 1000.0));
        int ticks = Math.max(1, (int) Math.round(c.parameters.get("slashes", 16.0)));

        var st = new LavaPoolState();
        st.cast = c;
        st.origin = p.getEyePosition();
        st.center = target;
        st.radius = c.parameters.get("radius", 7.68);
        st.geyserRadius = c.parameters.get("geyserRadius", 5.12);
        st.slowRatio = c.parameters.get("slowRatio", 0.30);
        st.fireResistShred = TowerBattleCard.WITCH_LAVA.held(c.stats) ? TowerBattleCard.WITCH_LAVA.value("shred") : 0.0;
        st.start = now;
        st.landAt = now + throwMs;
        st.endAt = st.landAt + durMs;
        st.intervalMs = intMs;
        st.nextTickAt = st.landAt + intMs;
        st.totalTicks = ticks;
        st.geyserCount = Math.max(0, (int) Math.round(c.parameters.get("geyserCount", 3.0)));
        st.element = WitchRules.effectiveElement(c.parameters, c.stats, 1);
        lavaPools.add(st);
        TrainingCombat.hold(c.id, "witch_lava", st.endAt + 600L);

        Vec3 dir = target.subtract(st.origin);
        dir = dir.lengthSqr() > 1e-4 ? dir.normalize() : horizontal(p.getLookAngle());
        var motion = witchMotion(st.element, 0);
        motion.addProperty("radius", st.radius);
        motion.addProperty("geyserRadius", st.geyserRadius);
        motion.addProperty("throwMs", throwMs);
        motion.addProperty("durationMs", durMs);
        motion.addProperty("tx", target.x);
        motion.addProperty("ty", target.y);
        motion.addProperty("tz", target.z);
        MageClient.INSTANCE.visual(c, c.id, "START", st.origin, dir, throwMs + durMs + 600L, 0, motion);
    }

    // --- 4. Lv25 Acid Rain (魔道酸雨云) ---

    private static void startAcidRain(SkillCast c) {
        var p = player();
        if (p == null) return;
        long now = ClientUi.now();
        double range = c.parameters.get("range", 16.0);
        Vec3 target = groundTargetPoint(p, range);
        long spawnMs = Math.max(60L, Math.round(c.parameters.get("spawnDelay", 0.20) * 1000.0));
        long durMs = Math.max(1000L, Math.round(c.parameters.get("duration", 8.0) * 1000.0));
        long intMs = Math.max(100L, Math.round(c.parameters.get("interval", 0.50) * 1000.0));
        int bolts = Math.max(0, (int) Math.round(c.parameters.get("boltCount", 4.0)));
        long rawBoltIntMs = Math.max(300L, Math.round(c.parameters.get("boltInterval", 1.60) * 1000.0));
        long boltIntMs = TowerBattleCard.WITCH_ACID.held(c.stats) && bolts > 0
            ? Math.max(250L, Math.round(durMs / (double) (bolts + 1))) : rawBoltIntMs;

        var st = new AcidCloudState();
        st.cast = c;
        st.center = target;
        st.radius = c.parameters.get("radius", 8.32);
        st.boltRadius = c.parameters.get("boltRadius", 5.12);
        st.cloudHeight = c.parameters.get("cloudHeight", 5.4);
        st.start = now;
        st.spawnAt = now + spawnMs;
        st.endAt = st.spawnAt + durMs;
        st.intervalMs = intMs;
        st.nextTickAt = st.spawnAt + intMs;
        st.totalTicks = Math.max(1, (int) Math.round(c.parameters.get("slashes", 16.0)));
        st.boltCount = bolts;
        st.boltIntervalMs = boltIntMs;
        st.nextBoltAt = st.spawnAt + Math.round(boltIntMs * 0.94);
        st.rainElement = WitchRules.effectiveElement(c.parameters, c.stats, 2);
        st.boltElement = WitchRules.effectiveElement(c.parameters, c.stats, 0);
        acidClouds.add(st);
        TrainingCombat.hold(c.id, "witch_acid", st.endAt + 600L);

        var motion = witchMotion(st.rainElement, 0);
        motion.addProperty("radius", st.radius);
        motion.addProperty("cloudHeight", st.cloudHeight);
        motion.addProperty("durationMs", durMs);
        MageClient.INSTANCE.visual(c, c.id, "START", target, horizontal(p.getLookAngle()), spawnMs + durMs + 600L, 0, motion);
    }

    // --- 5. Lv30 Anti-Gravity (反重力装置) ---

    private static void startAntiGravity(SkillCast c) {
        var p = player();
        if (p == null) return;
        long now = ClientUi.now();
        double range = c.parameters.get("range", 15.0);
        Vec3 target = groundTargetPoint(p, range);
        long liftMs = Math.max(120L, Math.round(c.parameters.get("liftDelay", 0.35) * 1000.0));
        long slamMs = Math.max(liftMs + 250L, Math.round(c.parameters.get("slamDelay", 0.85) * 1000.0));
        boolean gatherMode = TowerBattleCard.WITCH_ANTIG.held(c.stats);

        var st = new AntiGravityState();
        st.cast = c;
        st.center = target;
        st.radius = c.parameters.get("radius", 5.5);
        st.blastRadius = c.parameters.get("blastRadius", 6.2);
        st.controlSeconds = c.parameters.get("controlSeconds", 0.50);
        st.start = now;
        st.liftAt = now + liftMs;
        st.slamAt = now + slamMs;
        st.gatherMode = gatherMode;
        st.element = WitchRules.effectiveElement(c.parameters, c.stats, 3);
        antiGravities.add(st);
        TrainingCombat.hold(c.id, "witch_antig", st.slamAt + 700L);

        var motion = witchMotion(st.element, 0);
        motion.addProperty("radius", st.radius);
        motion.addProperty("blastRadius", st.blastRadius);
        motion.addProperty("liftMs", liftMs);
        motion.addProperty("slamMs", slamMs);
        motion.addProperty("gatherMode", st.gatherMode);
        MageClient.INSTANCE.visual(c, c.id, "START", target, horizontal(p.getLookAngle()), slamMs + 750L, 0, motion);
    }

    // --- 6..9 Riding Machines (红炉子 / 冰车 / 电塔 / 技艺融合) ---

    private static void startFurnace(SkillCast c) {
        startRidingMachine(c, 1, false, false, TowerBattleCard.WITCH_FURNACE.held(c.stats), false);
    }

    private static void startMegaDrill(SkillCast c) {
        startRidingMachine(c, 2, false, true, false, TowerBattleCard.WITCH_DRILL.held(c.stats));
    }

    private static void startFloraeCollider(SkillCast c) {
        startRidingMachine(c, 0, false, false, TowerBattleCard.WITCH_COLLIDER.held(c.stats), false);
    }

    private static void startFusionCraft(SkillCast c) {
        startRidingMachine(c, -1, true, true, false, false);
    }

    private static void startRidingMachine(SkillCast c, int innateElement, boolean invuln, boolean drivable,
                                           boolean autonomous, boolean icebreaker) {
        var p = player();
        if (p == null) return;
        stopBroomGlide(true);
        long now = ClientUi.now();
        long windupMs = Math.max(0L, Math.round(c.parameters.get("windupSeconds", 0.0) * 1000.0));
        long intMs = Math.max(80L, Math.round(c.parameters.get("interval", 0.20) * 1000.0));
        int ticks = Math.max(1, (int) Math.round(c.parameters.get("slashes", 14.0)));
        long durMs = Math.max(500L, Math.round(c.parameters.get("duration", 3.0) * 1000.0));
        if (autonomous) {
            durMs = Math.max(durMs, ticks * intMs);
        }

        Vec3 dir = horizontal(p.getLookAngle());
        var m = new MachineChannelState();
        m.cast = c;
        m.skillId = c.skill.id;
        m.dir = dir;
        m.anchor = autonomous ? safeDashEndpoint(p, dir, 1.8) : p.position();
        m.radius = c.parameters.get("radius", 7.5);
        m.blastRadius = c.parameters.get("finisherRadius", c.parameters.get("blastRadius", m.radius));
        m.pullRadius = c.parameters.get("pullRadius", 0.0);
        m.pullStrength = c.parameters.get("pullStrength", 4.5);
        m.driveSpeedRatio = c.parameters.get("driveSpeedRatio", 1.0);
        m.controlSeconds = c.parameters.get("controlSeconds", 0.0);
        m.bitterControlSeconds = c.parameters.get("bitterControlSeconds", 0.0);
        m.channelProtection = c.parameters.get("channelProtection", 0.55);
        m.bitterKeepRatio = c.parameters.get("bitterKeepRatio", 0.85);
        m.bitterLaunchHeight = c.parameters.get("bitterLaunchHeight", 0.0);
        m.bitterDashDistance = c.parameters.get("bitterDashDistance", 0.0);
        m.start = m.lastTick = now;
        m.windupEndAt = now + windupMs;
        m.endAt = now + durMs;
        m.intervalMs = intMs;
        m.nextTickAt = m.windupEndAt + Math.min(intMs, 150L);
        m.minChannelMs = Math.max(0L, Math.round(c.parameters.get("minChannelSeconds", 0.0) * 1000.0));
        m.totalTicks = ticks;
        m.element = innateElement < 0 ? WitchRules.effectiveElement(c.parameters, c.stats, -1)
            : WitchRules.effectiveElement(c.parameters, c.stats, innateElement);
        m.invulnerable = !autonomous && (invuln || c.parameters.get("invulnerable", 0.0) >= 0.5);
        m.drivable = !autonomous && drivable;
        m.autonomous = autonomous;
        m.icebreaker = icebreaker;

        if (icebreaker) {
            Stats drillStats = c.stats.copy();
            int buildElem = m.element >= 0 ? m.element : 2;
            ElementAccumulation.addConversion(drillStats, buildElem, ElementAccumulation.Kind.FREEZE,
                TowerBattleCard.WITCH_DRILL.ratio("buildup"));
            c.stats = drillStats;
        }

        if (autonomous) {
            deployedMachines.add(m);
            TrainingCombat.hold(c.id, "witch_machine_" + c.id, m.endAt + 800L);
            PlayerPoseChannel.play(c, WitchSkills.ANTI_GRAVITY, PlayerPoseChannel.scaled(360L, c.speed), m.dir, false);
            PlayerPoseChannel.release(c);
        } else {
            activeMachine = m;
            if (m.invulnerable) {
                SkillController.INSTANCE.acquireStatus(c.id, ActionStatus.INVULNERABLE, m.endAt + 400L);
            } else {
                SkillController.INSTANCE.acquireStatus(c.id, ActionStatus.STEADFAST, m.endAt + 200L);
            }
            CareerClient.INSTANCE.view.witch.activeMachine = m.skillId;
            CareerClient.INSTANCE.publish();
            TrainingCombat.hold(c.id, "witch_machine", m.endAt + 800L);
            PlayerPoseChannel.loop(c, c.skill.id, durMs + 200L, m.dir, true);
        }

        var motion = witchMotion(m.element, 0);
        motion.addProperty("radius", m.radius);
        motion.addProperty("blastRadius", m.blastRadius);
        motion.addProperty("durationMs", durMs);
        motion.addProperty("windupMs", windupMs);
        motion.addProperty("autonomous", autonomous);
        MageClient.INSTANCE.visual(c, c.id, "START", m.anchor, m.dir, durMs + 850L, 0, motion);
    }

    // --- Main Tick ---

    public static void tick() {
        var mc = Minecraft.getInstance();
        var p = mc.player;
        if (p == null || mc.level == null || !state().ready() || state().own().dead) {
            clear();
            return;
        }
        long now = ClientUi.now();
        tickBroomGlideTracking(p, now);
        tickStars(now);
        tickCloaks(now);
        tickFlyswatters(now);
        tickPuppets(now);
        tickLavaPools(now);
        tickAcidClouds(now);
        tickAntiGravities(now);
        tickActiveMachine(now);
        tickDeployedMachines(now);
        fireShredUntil.values().removeIf(until -> until <= now);
        if (shatteredFreezeUntil.size() > 256) shatteredFreezeUntil.clear();
        flushPendingMotions(now);
    }

    private static void tickBroomGlideTracking(LocalPlayer p, long now) {
        boolean onGround = p.onGround();
        if (onGround) {
            airborneSinceAt = 0L;
            airDashesUsed = 0;
            if (broomGliding) {
                stopBroomGlide(true);
            }
        } else {
            if (lastOnGround || airborneSinceAt == 0L) {
                airborneSinceAt = now;
            }
            if (broomGliding && !canSustainBroomGlide(p)) {
                stopBroomGlide(true);
            }
        }
        // Fallback edge detection for jump key in case keyPress event wasn't consumed (e.g. rebind / controller)
        boolean jumpDown = Minecraft.getInstance().options.keyJump.isDown();
        if (jumpDown && !lastJumpKeyDown && !onGround && Minecraft.getInstance().gui.screen() == null) {
            if (broomGliding && now - glideStartedAt >= GLIDE_TOGGLE_DEBOUNCE_MS) {
                stopBroomGlide(true);
            } else if (!broomGliding && canStartBroomGlide(p, now)) {
                startBroomGlide(p, now);
            }
        }
        lastJumpKeyDown = jumpDown;
        lastOnGround = onGround;
    }

    private static void tickStars(long now) {
        var mc = Minecraft.getInstance();
        for (var it = stars.iterator(); it.hasNext(); ) {
            var s = it.next();
            if (!MageClient.INSTANCE.valid(s.cast)) {
                TrainingCombat.release(s.cast.id, "witch_star");
                it.remove();
                continue;
            }
            double dt = Math.clamp((now - s.lastTick) / 1000.0, 0.005, 0.15);
            s.lastTick = now;

            if (s.soloEchoPending) {
                if (now >= s.nextBounceAt) {
                    LivingEntity echoTarget = findEntityByUuid(s.targetUuid);
                    Vec3 echoPos = echoTarget != null && echoTarget.isAlive()
                        ? echoTarget.getBoundingBox().getCenter() : s.pos;
                    if (echoTarget != null && echoTarget.isAlive()) {
                        MageClient.INSTANCE.issueElementalist(s.cast, echoTarget,
                            s.cast.parameters.get("damage", 4.3) * s.soloEchoRatio, s.element, 0.0, "", "echo");
                    }
                    var m = witchMotion(s.element, 0);
                    m.addProperty("radius", s.blastRadius);
                    m.addProperty("improvedStar", true);
                    m.addProperty("echo", true);
                    MageClient.INSTANCE.visual(s.cast, s.visualId, "FINISH", echoPos, s.dir, 450L, ++s.sequence, m);
                    TrainingCombat.release(s.cast.id, "witch_star");
                    MageClient.INSTANCE.finished(s.cast);
                    it.remove();
                }
                continue;
            }

            if (s.bouncing) {
                if (now < s.nextBounceAt) continue;
                LivingEntity nextTarget = findEntityByUuid(s.targetUuid);
                if (nextTarget == null || !nextTarget.isAlive()) {
                    TrainingCombat.release(s.cast.id, "witch_star");
                    MageClient.INSTANCE.finished(s.cast);
                    it.remove();
                    continue;
                }
                Vec3 hitPos = nextTarget.getBoundingBox().getCenter();
                Vec3 hopDir = hitPos.subtract(s.pos);
                s.dir = hopDir.lengthSqr() > 1e-4 ? hopDir.normalize() : s.dir;
                s.pos = hitPos;
                s.struck.add(nextTarget.getUUID());
                s.bouncesDone++;
                s.bouncesLeft--;
                double bounceCoeff = s.cast.parameters.get("damage", 4.3) * Math.max(0.55, 1.0 - 0.12 * s.bouncesDone);
                hitStarBlast(s, nextTarget, hitPos, bounceCoeff, "bounce" + s.bouncesDone);

                LivingEntity chain = s.bouncesLeft > 0 ? findNextStarTarget(s, hitPos, nextTarget) : null;
                if (chain != null) {
                    s.targetUuid = chain.getUUID();
                    s.nextBounceAt = now + 115L;
                } else {
                    var m = witchMotion(s.element, 0);
                    m.addProperty("radius", s.blastRadius);
                    m.addProperty("improvedStar", true);
                    MageClient.INSTANCE.visual(s.cast, s.visualId, "FINISH", hitPos, s.dir, 450L, ++s.sequence, m);
                    TrainingCombat.release(s.cast.id, "witch_star");
                    MageClient.INSTANCE.finished(s.cast);
                    it.remove();
                }
                continue;
            }

            double step = Math.min(s.rangeLeft, s.speed * dt);
            Vec3 next = s.pos.add(s.dir.scale(step));
            var wall = mc.level.clip(new ClipContext(s.pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
            Vec3 end = wall.getType() == HitResult.Type.MISS ? next : wall.getLocation();
            var hit = SkillController.INSTANCE.targets(new AABB(s.pos, end).inflate(0.35)).stream()
                .filter(e -> !s.struck.contains(e.getUUID()) && SkillGeometry.intersects(e.getBoundingBox().inflate(0.25), s.pos, end))
                .min(Comparator.comparingDouble(e -> e.distanceToSqr(s.pos)))
                .orElse(null);

            if (hit != null) {
                Vec3 impact = hit.getBoundingBox().getCenter();
                s.pos = impact;
                s.struck.add(hit.getUUID());
                hitStarBlast(s, hit, impact, s.cast.parameters.get("damage", 4.3), "main");
                LivingEntity chain = s.bouncesLeft > 0 ? findNextStarTarget(s, impact, hit) : null;
                if (chain != null) {
                    s.bouncing = true;
                    s.targetUuid = chain.getUUID();
                    s.nextBounceAt = now + 115L;
                } else {
                    // Solo target echo bounce so Witch's Improved Star shines vs single Boss/Sandbag too!
                    s.soloEchoPending = true;
                    s.targetUuid = hit.getUUID();
                    s.nextBounceAt = now + 165L;
                }
                continue;
            }

            s.pos = end;
            s.rangeLeft -= step;
            if (wall.getType() != HitResult.Type.MISS || s.rangeLeft <= 0.05) {
                var m = witchMotion(s.element, 0);
                m.addProperty("radius", s.blastRadius);
                m.addProperty("improvedStar", true);
                MageClient.INSTANCE.visual(s.cast, s.visualId, "FINISH", s.pos, s.dir, 400L, ++s.sequence, m);
                TrainingCombat.release(s.cast.id, "witch_star");
                MageClient.INSTANCE.finished(s.cast);
                it.remove();
            }
        }
    }

    private static LivingEntity findNextStarTarget(ImprovedStarState s, Vec3 origin, LivingEntity current) {
        if (!s.allowRepeat) return findNearestUnhit(origin, s.bounceRange, s.struck);
        var nearby = targetsInRadius(origin, s.bounceRange, 16);
        UUID currentId = current == null ? null : current.getUUID();
        LivingEntity fallbackOther = null;
        for (var e : nearby) {
            if (e.getUUID().equals(currentId)) continue;
            if (!s.struck.contains(e.getUUID())) return e;
            if (fallbackOther == null) fallbackOther = e;
        }
        if (fallbackOther != null) return fallbackOther;
        return current != null && current.isAlive() && SkillController.canTarget(current) ? current : null;
    }

    private static void hitStarBlast(ImprovedStarState s, LivingEntity primary, Vec3 center, double coeff, String group) {
        MageClient.INSTANCE.issueElementalist(s.cast, primary, coeff, s.element, 0.0, "", group);
        double splash = s.blastRadius;
        for (var e : targetsInRadius(center, splash, 8)) {
            if (e.getUUID().equals(primary.getUUID())) continue;
            MageClient.INSTANCE.issueElementalist(s.cast, e, coeff * 0.55, s.element, 0.0, "", group + "_splash");
        }
        var m = witchMotion(s.element, 0);
        m.addProperty("radius", splash);
        m.addProperty("improvedStar", true);
        long dur = Math.max(450L, s.bouncesLeft * 150L + 500L);
        MageClient.INSTANCE.visual(s.cast, s.visualId, "HIT", center, s.dir, dur, ++s.sequence, m);
    }

    private static void tickCloaks(long now) {
        for (var it = cloaks.iterator(); it.hasNext(); ) {
            var st = it.next();
            if (!MageClient.INSTANCE.valid(st.cast)) {
                SkillController.INSTANCE.releaseStatus(st.cast.id, ActionStatus.INVULNERABLE);
                TrainingCombat.release(st.cast.id, "witch_cloak");
                it.remove();
                continue;
            }
            int cap = st.pullRadius > 6.0 ? 32 : 16;
            if (!st.grabbed && now >= st.grabAt) {
                st.grabbed = true;
                double grabCoeff = st.cast.parameters.get("damage", 8.0);
                for (var e : targetsInRadius(st.center, st.pullRadius, cap)) {
                    queuePull(e, st.pullPoint, 0.85);
                    MageClient.INSTANCE.issueElementalist(st.cast, e, grabCoeff, st.element, st.controlSeconds, "", "grab");
                }
                var m = witchMotion(st.element, 0);
                m.addProperty("radius", st.radius);
                m.addProperty("pullRadius", st.pullRadius);
                MageClient.INSTANCE.visual(st.cast, st.cast.id, "HIT", st.pullPoint, st.dir, 650L, ++st.sequence, m);
            }
            if (now >= st.burstAt) {
                double burstCoeff = st.cast.parameters.get("finisher", 16.0);
                for (var e : targetsInRadius(st.pullPoint, st.radius, cap)) {
                    MageClient.INSTANCE.issueElementalist(st.cast, e, burstCoeff, st.element, 0.0, "", "burst");
                }
                var m = witchMotion(st.element, 0);
                m.addProperty("radius", st.radius);
                m.addProperty("pullRadius", st.pullRadius);
                MageClient.INSTANCE.visual(st.cast, st.cast.id, "FINISH", st.pullPoint, st.dir, 550L, ++st.sequence, m);
                PlayerPoseChannel.release(st.cast);
                TrainingCombat.release(st.cast.id, "witch_cloak");
                MageClient.INSTANCE.finished(st.cast);
                it.remove();
            }
        }
    }

    private static void tickFlyswatters(long now) {
        for (var it = swatters.iterator(); it.hasNext(); ) {
            var st = it.next();
            if (!MageClient.INSTANCE.valid(st.cast)) {
                TrainingCombat.release(st.cast.id, "witch_swat");
                it.remove();
                continue;
            }
            if (now < st.impactAt) continue;

            double coeff = st.cast.parameters.get("damage", 20.5);
            var hitList = targetsInRadius(st.center, st.radius, st.megaCard ? 24 : 14);
            int hits = 0;
            boolean killed = false;
            for (var e : hitList) {
                var ctx = state().context(e);
                double before = ctx == null ? 1.0 : state().predicted(ctx.life);
                if (MageClient.INSTANCE.issueElementalist(st.cast, e, coeff, st.element, 0.0, "swat", "swat")) {
                    hits++;
                    double after = ctx == null ? 1.0 : state().predicted(ctx.life);
                    if (before > 0.0 && after <= 0.0) killed = true;
                }
            }

            if (hits > 0) {
                // Grant/refresh 15s Lucky Candy Blessing
                var view = CareerClient.INSTANCE.view;
                view.witch = WitchRules.applyCandyBuff(view.witch, st.cast.parameters, state().serverNow());
                CareerClient.INSTANCE.publish();
            }

            if (hits > 0 || st.megaCard) {
                // Classic Giant Flyswatter random 4-element mini-familiar puppet summoning!
                int baseCount = Math.max(1, (int) Math.round(st.cast.parameters.get("summonBaseCount", 2.0)));
                int bonusCount = (killed || hits >= 3) ? Math.max(0, (int) Math.round(st.cast.parameters.get("summonBonusCount", 1.0))) : 0;
                int count = baseCount + bonusCount;
                double puppetCoeff = st.cast.parameters.get("puppetDamage", 3.0);
                double puppetRad = st.cast.parameters.get("summonRadius", 2.4);
                double seekRange = st.cast.parameters.get("summonSeekRange", 10.0);
                long lifeMs = Math.max(600L, Math.round(st.cast.parameters.get("summonLifetime", 1.8) * 1000.0));

                for (int i = 0; i < count; i++) {
                    int elem = WitchRules.effectiveElement(st.cast.parameters, st.cast.stats, RNG.nextInt(4));
                    double angle = (Math.TAU / count) * i + RNG.nextDouble() * 0.7;
                    var pup = new FamiliarPuppetState();
                    pup.cast = st.cast;
                    pup.visualId = UUID.randomUUID().toString();
                    pup.element = elem;
                    pup.pos = st.center.add(Math.cos(angle) * 0.7, 0.35, Math.sin(angle) * 0.7);
                    pup.vel = new Vec3(Math.cos(angle) * 4.2, 4.6 + RNG.nextDouble() * 1.2, Math.sin(angle) * 4.2);
                    pup.radius = puppetRad;
                    pup.seekRange = seekRange;
                    pup.coeff = puppetCoeff;
                    pup.spawnAt = now + i * 95L;
                    pup.activeAt = pup.spawnAt + 220L;
                    pup.expiresAt = pup.spawnAt + lifeMs;
                    pup.lastTick = pup.spawnAt;
                    puppets.add(pup);
                    TrainingCombat.hold(st.cast.id, "puppet_" + pup.visualId, pup.expiresAt + 350L);
                }
            }

            var m = witchMotion(st.element, 0);
            m.addProperty("radius", st.radius);
            m.addProperty("hits", hits);
            MageClient.INSTANCE.visual(st.cast, st.cast.id, "FINISH", st.center, st.dir, 650L, ++st.sequence, m);
            PlayerPoseChannel.release(st.cast);
            TrainingCombat.release(st.cast.id, "witch_swat");
            MageClient.INSTANCE.finished(st.cast);
            it.remove();
        }
    }

    private static void tickPuppets(long now) {
        for (var it = puppets.iterator(); it.hasNext(); ) {
            var pup = it.next();
            if (!MageClient.INSTANCE.valid(pup.cast)) {
                TrainingCombat.release(pup.cast.id, "puppet_" + pup.visualId);
                it.remove();
                continue;
            }
            if (now < pup.spawnAt) continue;
            if (!pup.spawned) {
                pup.spawned = true;
                pup.lastTick = now;
                var m = witchMotion(pup.element, 0);
                m.addProperty("puppet", true);
                m.addProperty("puppetKind", pup.element);
                m.addProperty("radius", pup.radius);
                MageClient.INSTANCE.visual(pup.cast, pup.visualId, "START", pup.pos, pup.vel.normalize(),
                    Math.max(500L, pup.expiresAt - now + 400L), 0, m);
            }
            double dt = Math.clamp((now - pup.lastTick) / 1000.0, 0.01, 0.12);
            pup.lastTick = now;

            LivingEntity target = findNearestTarget(pup.pos, pup.seekRange);
            if (target != null && now >= pup.activeAt) {
                Vec3 to = target.getBoundingBox().getCenter().subtract(pup.pos);
                double dist = to.length();
                if (dist <= 1.35) {
                    explodePuppet(pup, target.getBoundingBox().getCenter());
                    it.remove();
                    continue;
                }
                Vec3 desired = to.normalize().scale(9.5);
                pup.vel = new Vec3(
                    pup.vel.x + (desired.x - pup.vel.x) * Math.min(1.0, dt * 8.0),
                    pup.vel.y - 14.0 * dt,
                    pup.vel.z + (desired.z - pup.vel.z) * Math.min(1.0, dt * 8.0)
                );
                if (pup.pos.y <= target.getY() + 0.15 && pup.vel.y < 0) {
                    pup.vel = new Vec3(pup.vel.x, 4.2, pup.vel.z); // cute bouncy hop!
                }
            } else {
                pup.vel = pup.vel.add(0, -14.0 * dt, 0);
            }
            pup.pos = pup.pos.add(pup.vel.scale(dt));

            if (now >= pup.expiresAt) {
                explodePuppet(pup, pup.pos);
                it.remove();
            }
        }
    }

    private static void explodePuppet(FamiliarPuppetState pup, Vec3 at) {
        if (pup.exploded) return;
        pup.exploded = true;
        for (var e : targetsInRadius(at, pup.radius, 8)) {
            MageClient.INSTANCE.issueElementalist(pup.cast, e, pup.coeff, pup.element, 0.0, "puppet", "pup_" + pup.visualId);
        }
        var m = witchMotion(pup.element, 0);
        m.addProperty("puppet", true);
        m.addProperty("puppetKind", pup.element);
        m.addProperty("radius", pup.radius);
        MageClient.INSTANCE.visual(pup.cast, pup.visualId, "FINISH", at, Vec3.ZERO, 450L, ++pup.sequence, m);
        TrainingCombat.release(pup.cast.id, "puppet_" + pup.visualId);
    }

    private static void tickLavaPools(long now) {
        for (var it = lavaPools.iterator(); it.hasNext(); ) {
            var st = it.next();
            if (!MageClient.INSTANCE.valid(st.cast)) {
                TrainingCombat.release(st.cast.id, "witch_lava");
                it.remove();
                continue;
            }
            if (!st.landed && now >= st.landAt) {
                st.landed = true;
                var m = witchMotion(st.element, 0);
                m.addProperty("radius", st.radius);
                m.addProperty("geyserRadius", st.geyserRadius);
                MageClient.INSTANCE.visual(st.cast, st.cast.id, "RELEASE", st.center, Vec3.ZERO,
                    Math.max(400L, st.endAt - now + 450L), ++st.sequence, m);
            }
            if (!st.landed) continue;

            while (st.delivered < st.totalTicks && now >= st.nextTickAt) {
                st.delivered++;
                st.nextTickAt += st.intervalMs;
                double tickCoeff = st.cast.parameters.get("damage", 1.75);
                var targets = targetsInRadius(st.center, st.radius, st.radius > 9.0 ? 32 : 20);
                for (var e : targets) {
                    if (st.fireResistShred > 0.0) fireShredUntil.put(e.getUUID(), now + 1500L);
                    MageClient.INSTANCE.issueElementalist(st.cast, e, tickCoeff, st.element, 0.0, "pool", "lava_" + st.delivered);
                }
                // Fire 3 Great Success lava geysers evenly across pool duration (ticks 4, 8, 12)
                if (st.geysersFired < st.geyserCount) {
                    int geyserTick = Math.max(1, (st.geysersFired + 1) * st.totalTicks / (st.geyserCount + 1));
                    if (st.delivered >= geyserTick) {
                        st.geysersFired++;
                        Vec3 geyserPos = pickDensestOrCenter(targets, st.center);
                        double gCoeff = st.cast.parameters.get("geyserDamage", 6.0);
                        for (var e : targetsInRadius(geyserPos, st.geyserRadius, st.geyserRadius > 6.0 ? 24 : 14)) {
                            if (st.fireResistShred > 0.0) fireShredUntil.put(e.getUUID(), now + 1500L);
                            MageClient.INSTANCE.issueElementalist(st.cast, e, gCoeff, st.element, 0.0, "geyser", "geyser_" + st.geysersFired);
                        }
                        var gm = witchMotion(st.element, 0);
                        gm.addProperty("geyser", true);
                        gm.addProperty("radius", st.radius);
                        gm.addProperty("geyserRadius", st.geyserRadius);
                        gm.addProperty("burstRadius", st.geyserRadius);
                        gm.addProperty("px", geyserPos.x);
                        gm.addProperty("py", geyserPos.y);
                        gm.addProperty("pz", geyserPos.z);
                        MageClient.INSTANCE.visual(st.cast, st.cast.id, "HIT", geyserPos, Vec3.ZERO,
                            Math.max(600L, st.endAt - now + 450L), ++st.sequence, gm);
                    }
                }
            }
            if (st.delivered >= st.totalTicks || now >= st.endAt) {
                var m = witchMotion(st.element, 0);
                m.addProperty("radius", st.radius);
                MageClient.INSTANCE.visual(st.cast, st.cast.id, "FINISH", st.center, Vec3.ZERO, 400L, ++st.sequence, m);
                TrainingCombat.release(st.cast.id, "witch_lava");
                MageClient.INSTANCE.finished(st.cast);
                it.remove();
            }
        }
    }

    private static void tickAcidClouds(long now) {
        for (var it = acidClouds.iterator(); it.hasNext(); ) {
            var st = it.next();
            if (!MageClient.INSTANCE.valid(st.cast)) {
                TrainingCombat.release(st.cast.id, "witch_acid");
                it.remove();
                continue;
            }
            if (!st.spawned && now >= st.spawnAt) {
                st.spawned = true;
                var m = witchMotion(st.rainElement, 0);
                m.addProperty("radius", st.radius);
                m.addProperty("cloudHeight", st.cloudHeight);
                MageClient.INSTANCE.visual(st.cast, st.cast.id, "RELEASE", st.center, Vec3.ZERO,
                    Math.max(400L, st.endAt - now + 450L), ++st.sequence, m);
            }
            if (!st.spawned) continue;

            int rainCap = st.radius > 10.0 ? 36 : 20;
            int boltCap = st.boltRadius > 6.0 ? 24 : 12;
            while (st.delivered < st.totalTicks && now >= st.nextTickAt) {
                st.delivered++;
                st.nextTickAt += st.intervalMs;
                double rainCoeff = st.cast.parameters.get("damage", 1.50);
                for (var e : targetsInRadius(st.center, st.radius, rainCap)) {
                    MageClient.INSTANCE.issueElementalist(st.cast, e, rainCoeff, st.rainElement, 0.0, "rain", "rain_" + st.delivered);
                }
            }
            while (st.boltsFired < st.boltCount && now >= st.nextBoltAt) {
                st.boltsFired++;
                st.nextBoltAt += st.boltIntervalMs;
                var targets = targetsInRadius(st.center, st.radius, rainCap);
                Vec3 boltPos = pickPriorityTargetOrCenter(targets, st.center);
                double boltCoeff = st.cast.parameters.get("lightningDamage", 6.0);
                for (var e : targetsInRadius(boltPos, st.boltRadius, boltCap)) {
                    MageClient.INSTANCE.issueElementalist(st.cast, e, boltCoeff, st.boltElement, 0.0, "lightning", "bolt_" + st.boltsFired);
                }
                var bm = witchMotion(st.boltElement, 0);
                bm.addProperty("lightning", true);
                bm.addProperty("radius", st.radius);
                bm.addProperty("burstRadius", st.boltRadius);
                bm.addProperty("cloudHeight", st.cloudHeight);
                MageClient.INSTANCE.visual(st.cast, st.cast.id, "HIT", boltPos, Vec3.ZERO,
                    Math.max(500L, st.endAt - now + 450L), ++st.sequence, bm);
            }
            if ((st.delivered >= st.totalTicks && st.boltsFired >= st.boltCount) || now >= st.endAt) {
                var m = witchMotion(st.rainElement, 0);
                m.addProperty("radius", st.radius);
                MageClient.INSTANCE.visual(st.cast, st.cast.id, "FINISH", st.center, Vec3.ZERO, 400L, ++st.sequence, m);
                TrainingCombat.release(st.cast.id, "witch_acid");
                MageClient.INSTANCE.finished(st.cast);
                it.remove();
            }
        }
    }

    private static void tickAntiGravities(long now) {
        for (var it = antiGravities.iterator(); it.hasNext(); ) {
            var st = it.next();
            if (!MageClient.INSTANCE.valid(st.cast)) {
                TrainingCombat.release(st.cast.id, "witch_antig");
                it.remove();
                continue;
            }
            int cap = st.gatherMode ? 36 : 24;
            if (!st.lifted && now >= st.liftAt) {
                st.lifted = true;
                double liftCoeff = st.cast.parameters.get("damage", 18.0);
                for (var e : targetsInRadius(st.center, st.radius, cap)) {
                    if (st.gatherMode) queuePull(e, st.center, 0.85);
                    else queueMove(e, e.position().add(0, 1.5, 0));
                    MageClient.INSTANCE.issueElementalist(st.cast, e, liftCoeff, st.element, st.controlSeconds, "", "lift");
                }
                var m = witchMotion(st.element, 0);
                m.addProperty("radius", st.radius);
                m.addProperty("blastRadius", st.blastRadius);
                m.addProperty("gatherMode", st.gatherMode);
                MageClient.INSTANCE.visual(st.cast, st.cast.id, "RELEASE", st.center, Vec3.ZERO, 950L, ++st.sequence, m);
            } else if (st.gatherMode && st.lifted && now < st.slamAt) {
                for (var e : targetsInRadius(st.center, st.radius, cap)) {
                    queuePull(e, st.center, 0.55);
                }
            }
            if (now >= st.slamAt) {
                double slamCoeff = st.cast.parameters.get("finisher", 50.0);
                for (var e : targetsInRadius(st.center, st.blastRadius, cap)) {
                    if (!st.gatherMode) queueMove(e, new Vec3(e.getX(), st.center.y, e.getZ()));
                    MageClient.INSTANCE.issueElementalist(st.cast, e, slamCoeff, st.element, 0.0, "", "slam");
                }
                var m = witchMotion(st.element, 0);
                m.addProperty("radius", st.radius);
                m.addProperty("blastRadius", st.blastRadius);
                m.addProperty("gatherMode", st.gatherMode);
                MageClient.INSTANCE.visual(st.cast, st.cast.id, "FINISH", st.center, Vec3.ZERO, 650L, ++st.sequence, m);
                TrainingCombat.release(st.cast.id, "witch_antig");
                MageClient.INSTANCE.finished(st.cast);
                it.remove();
            }
        }
    }

    private static void tickActiveMachine(long now) {
        var m = activeMachine;
        if (m == null || m.finished) return;
        var p = player();
        if (p == null || !MageClient.INSTANCE.valid(m.cast) || p.isDeadOrDying()
            || (!m.invulnerable && CombatContentTypes.controlled(p))) {
            abortMachine(m);
            return;
        }
        m.dir = horizontal(p.getLookAngle());
        if (m.drivable) {
            m.anchor = p.position();
        }
        tickMachine(m, now);
    }

    private static void tickDeployedMachines(long now) {
        for (var m : new ArrayList<>(deployedMachines)) {
            if (m.finished) {
                deployedMachines.remove(m);
                continue;
            }
            if (!MageClient.INSTANCE.valid(m.cast)) {
                abortMachine(m);
                continue;
            }
            tickMachine(m, now);
        }
    }

    private static void tickMachine(MachineChannelState m, long now) {
        m.lastTick = now;
        if (now < m.windupEndAt) return;

        // Pull non-Boss enemies during Mega Drill / Fusion Craft
        if (m.pullRadius > 0.0) {
            Vec3 pullCenter = WitchSkills.MEGA_DRILL.equals(m.skillId)
                ? m.anchor.add(m.dir.scale(2.0)) : m.anchor.add(m.dir.scale(2.5));
            for (var e : targetsInRadius(pullCenter, m.pullRadius, 24)) {
                queuePull(e, pullCenter, 0.30);
            }
        }

        while (m.delivered < m.totalTicks && now >= m.nextTickAt) {
            m.delivered++;
            m.nextTickAt += m.intervalMs;
            double tickCoeff = m.cast.parameters.get("damage", 5.5);
            Vec3 hitCenter = WitchSkills.MEGA_DRILL.equals(m.skillId)
                ? m.anchor.add(m.dir.scale(1.8))
                : WitchSkills.FUSION_CRAFT.equals(m.skillId)
                    ? m.anchor.add(m.dir.scale(2.0))
                    : m.anchor;

            int tickElem = m.element;
            if (WitchSkills.FUSION_CRAFT.equals(m.skillId) && tickElem < 0) {
                // Rotate Fire(1) -> Ice(2) -> Light(0) -> Dark(3) across 16 roller hits
                int[] cycle = {1, 2, 0, 3};
                tickElem = cycle[(m.delivered - 1) % 4];
            }
            int cap = WitchSkills.FUSION_CRAFT.equals(m.skillId) ? 32 : 24;
            var hitTargets = targetsInRadius(hitCenter, m.radius, cap);
            // Also include targets overlapping player center for Mega Drill so point-blank mobs never slip behind drill nose
            if (WitchSkills.MEGA_DRILL.equals(m.skillId)) {
                var close = new LinkedHashSet<>(hitTargets);
                close.addAll(targetsInRadius(m.anchor, m.radius, cap));
                hitTargets = new ArrayList<>(close);
            }
            for (var e : hitTargets) {
                MageClient.INSTANCE.issueElementalist(m.cast, e, tickCoeff, tickElem, m.controlSeconds, "", "tick_" + m.delivered);
                if (m.icebreaker) tryIcebreakerShatter(m, e, now);
            }
            var motion = witchMotion(tickElem, 0);
            motion.addProperty("radius", m.radius);
            motion.addProperty("tickIndex", m.delivered);
            motion.addProperty("totalTicks", m.totalTicks);
            motion.addProperty("autonomous", m.autonomous);
            MageClient.INSTANCE.visual(m.cast, m.cast.id, "HIT", hitCenter, m.dir,
                Math.max(450L, m.endAt - now + 600L), ++m.sequence, motion);
        }

        if (m.delivered >= m.totalTicks || now >= m.endAt) {
            finishMachine(m, now, false);
        }
    }

    private static void tryIcebreakerShatter(MachineChannelState m, LivingEntity e, long now) {
        Target target = state().context(e);
        if (target == null || target.life.dead) return;
        long serverNow = state().serverNow();
        if (!target.life.elemental.active(ElementAccumulation.Kind.FREEZE, target.life, serverNow)) return;
        if (state().predicted(target.life) <= 0.0) return;
        long freezeUntil = target.life.elemental.until[ElementAccumulation.Kind.FREEZE.ordinal()];
        if (shatteredFreezeUntil.getOrDefault(e.getUUID(), 0L) == freezeUntil) return;
        shatteredFreezeUntil.put(e.getUUID(), freezeUntil);

        double shatterCoeff = m.cast.parameters.get("finisher", 36.0) * TowerBattleCard.WITCH_DRILL.ratio("shatter");
        int elem = m.element < 0 ? 2 : m.element;
        MageClient.INSTANCE.issueElementalist(m.cast, e, shatterCoeff, elem, 0.0, "", "shatter_" + (++shatterSequence),
            ElementAccumulation.Kind.FREEZE.id);
        target.life.elemental.endEarly(ElementAccumulation.Kind.FREEZE, serverNow);

        Vec3 pos = e.getBoundingBox().getCenter();
        var sm = witchMotion(elem, 0);
        sm.addProperty("shatter", true);
        sm.addProperty("radius", m.radius);
        sm.addProperty("burstRadius", Math.max(2.6, m.blastRadius * 0.5));
        sm.addProperty("px", pos.x);
        sm.addProperty("py", pos.y);
        sm.addProperty("pz", pos.z);
        MageClient.INSTANCE.visual(m.cast, m.cast.id, "HIT", pos, m.dir,
            Math.max(600L, m.endAt - now + 600L), ++m.sequence, sm);
    }

    private static void finishMachine(MachineChannelState m, long now, boolean bitter) {
        if (m.finished) return;
        m.finished = true;
        if (m.autonomous) {
            deployedMachines.remove(m);
            bitter = false;
        } else if (activeMachine == m) {
            activeMachine = null;
        }
        var p = player();
        Vec3 blastCenter = m.anchor;

        // Bitter Lollipop positional flourishes
        if (bitter && p != null) {
            if (m.bitterDashDistance > 0.0) {
                Vec3 to = safeDashEndpoint(p, m.dir, m.bitterDashDistance);
                blastCenter = to;
                p.setPos(to.x, to.y, to.z);
            }
            if (m.bitterLaunchHeight > 0.0) {
                Vec3 hop = m.dir.scale(-0.42).add(0, 0.62, 0);
                pendingMachineVelocity = hop;
                p.setDeltaMovement(hop);
                p.setOnGround(false);
                p.resetFallDistance();
                fallProtectionUntil = now + LANDING_FALL_GRACE_MS;
            }
        }

        int remainingTicks = Math.max(0, m.totalTicks - m.delivered);
        double tickCoeff = m.cast.parameters.get("damage", 5.5);
        double finCoeff = m.cast.parameters.get("finisher", 35.0);
        double totalFinisherCoeff = bitter
            ? WitchRules.bitterDetonateCoefficient(remainingTicks, tickCoeff, m.bitterKeepRatio, finCoeff)
            : finCoeff;
        double ctrl = bitter ? m.bitterControlSeconds : 0.0;
        int cap = WitchSkills.FUSION_CRAFT.equals(m.skillId) ? 32 : 24;

        var targets = targetsInRadius(blastCenter, m.blastRadius, cap);
        int pops = bitter ? (WitchSkills.FUSION_CRAFT.equals(m.skillId) ? 4 : 3) : 1;
        double popCoeff = totalFinisherCoeff / pops;
        if (WitchSkills.FUSION_CRAFT.equals(m.skillId) && m.element < 0) {
            for (int pop = 0; pop < pops; pop++) {
                String grp = bitter ? "bitter_" + pop : "finisher";
                for (var e : targets) {
                    MageClient.INSTANCE.issueMulti(m.cast, e, popCoeff, WitchRules.ALL_FOUR_ELEMENTS, pop == 0 ? ctrl : 0.0, "", grp);
                }
            }
            fusionGraceInvulnUntil = Math.max(fusionGraceInvulnUntil, now + 400L);
        } else {
            int finElem = m.element < 0 ? 1 : m.element;
            for (int pop = 0; pop < pops; pop++) {
                String grp = bitter ? "bitter_" + pop : "finisher";
                for (var e : targets) {
                    MageClient.INSTANCE.issueElementalist(m.cast, e, popCoeff, finElem, pop == 0 ? ctrl : 0.0, "", grp);
                }
            }
            if (m.icebreaker) {
                for (var e : targets) tryIcebreakerShatter(m, e, now);
            }
        }

        var motion = witchMotion(m.element, 0);
        motion.addProperty("blastRadius", m.blastRadius);
        motion.addProperty("bitter", bitter);
        motion.addProperty("remainingTicks", remainingTicks);
        motion.addProperty("autonomous", m.autonomous);
        long finishDur = bitter ? (WitchSkills.FUSION_CRAFT.equals(m.skillId) ? 680L : 560L)
            : (WitchSkills.FUSION_CRAFT.equals(m.skillId) ? 1050L : 850L);
        MageClient.INSTANCE.visual(m.cast, m.cast.id, "FINISH", blastCenter, m.dir, finishDur, ++m.sequence, motion);

        if (m.autonomous) {
            TrainingCombat.release(m.cast.id, "witch_machine_" + m.cast.id);
        } else {
            SkillController.INSTANCE.releaseStatus(m.cast.id, ActionStatus.INVULNERABLE);
            SkillController.INSTANCE.releaseStatus(m.cast.id, ActionStatus.STEADFAST);
            // Immediately end the looping riding pose animation so character returns to normal posture right on explosion!
            PlayerPoseChannel.cancel(m.cast);
            if (bitter && WitchSkills.FUSION_CRAFT.equals(m.skillId)) {
                try {
                    Effects.VFX.completeCamera(UUID.fromString(m.cast.id));
                } catch (IllegalArgumentException ignored) {}
            }
            CareerClient.INSTANCE.view.witch.activeMachine = "";
            CareerClient.INSTANCE.publish();
            TrainingCombat.release(m.cast.id, "witch_machine");
        }
        MageClient.INSTANCE.finished(m.cast);
    }

    private static void abortMachine(MachineChannelState m) {
        if (m == null || m.finished) return;
        m.finished = true;
        if (m.autonomous) {
            deployedMachines.remove(m);
            MageClient.INSTANCE.visual(m.cast, m.cast.id, "CANCEL", m.anchor, m.dir, 300L, ++m.sequence, null);
            TrainingCombat.release(m.cast.id, "witch_machine_" + m.cast.id);
        } else {
            if (activeMachine == m) activeMachine = null;
            SkillController.INSTANCE.releaseStatus(m.cast.id, ActionStatus.INVULNERABLE);
            SkillController.INSTANCE.releaseStatus(m.cast.id, ActionStatus.STEADFAST);
            PlayerPoseChannel.cancel(m.cast);
            MageClient.INSTANCE.visual(m.cast, m.cast.id, "CANCEL", m.anchor, m.dir, 300L, ++m.sequence, null);
            CareerClient.INSTANCE.view.witch.activeMachine = "";
            CareerClient.INSTANCE.publish();
            TrainingCombat.release(m.cast.id, "witch_machine");
        }
        MageClient.INSTANCE.finished(m.cast);
    }

    // --- Cleanup ---

    public static void cancel(String id) {
        if (activeMachine != null && activeMachine.cast.id.equals(id)) abortMachine(activeMachine);
        for (var m : new ArrayList<>(deployedMachines)) {
            if (m.cast.id.equals(id)) abortMachine(m);
        }
        stars.removeIf(s -> {
            if (s.cast.id.equals(id)) { TrainingCombat.release(id, "witch_star"); return true; }
            return false;
        });
        cloaks.removeIf(s -> {
            if (s.cast.id.equals(id)) {
                SkillController.INSTANCE.releaseStatus(id, ActionStatus.INVULNERABLE);
                TrainingCombat.release(id, "witch_cloak");
                return true;
            }
            return false;
        });
        swatters.removeIf(s -> {
            if (s.cast.id.equals(id)) { TrainingCombat.release(id, "witch_swat"); return true; }
            return false;
        });
        puppets.removeIf(s -> {
            if (s.cast.id.equals(id)) { TrainingCombat.release(id, "puppet_" + s.visualId); return true; }
            return false;
        });
        lavaPools.removeIf(s -> {
            if (s.cast.id.equals(id)) { TrainingCombat.release(id, "witch_lava"); return true; }
            return false;
        });
        acidClouds.removeIf(s -> {
            if (s.cast.id.equals(id)) { TrainingCombat.release(id, "witch_acid"); return true; }
            return false;
        });
        antiGravities.removeIf(s -> {
            if (s.cast.id.equals(id)) { TrainingCombat.release(id, "witch_antig"); return true; }
            return false;
        });
    }

    public static void clear() {
        if (activeMachine != null) abortMachine(activeMachine);
        for (var m : new ArrayList<>(deployedMachines)) abortMachine(m);
        for (var s : stars) TrainingCombat.release(s.cast.id, "witch_star");
        for (var s : cloaks) {
            SkillController.INSTANCE.releaseStatus(s.cast.id, ActionStatus.INVULNERABLE);
            TrainingCombat.release(s.cast.id, "witch_cloak");
        }
        for (var s : swatters) TrainingCombat.release(s.cast.id, "witch_swat");
        for (var s : puppets) TrainingCombat.release(s.cast.id, "puppet_" + s.visualId);
        for (var s : lavaPools) TrainingCombat.release(s.cast.id, "witch_lava");
        for (var s : acidClouds) TrainingCombat.release(s.cast.id, "witch_acid");
        for (var s : antiGravities) TrainingCombat.release(s.cast.id, "witch_antig");
        deployedMachines.clear();
        stars.clear();
        cloaks.clear();
        swatters.clear();
        puppets.clear();
        lavaPools.clear();
        acidClouds.clear();
        antiGravities.clear();
        fireShredUntil.clear();
        shatteredFreezeUntil.clear();
        pendingMotions.clear();
        broomGliding = false;
        glideStartedAt = airborneSinceAt = fallProtectionUntil = cloakInvulnUntil = fusionGraceInvulnUntil = 0L;
        airDashesUsed = 0;
        pendingMachineVelocity = null;
    }

    // --- Helpers ---

    private static Vec3 horizontal(Vec3 v) {
        Vec3 h = new Vec3(v.x, 0.0, v.z);
        return h.lengthSqr() > 1e-6 ? h.normalize() : new Vec3(0.0, 0.0, 1.0);
    }

    private static Vec3 groundTargetPoint(LocalPlayer p, double range) {
        return SkillTargeting.ground(p, range, SkillController::canTarget).point();
    }

    private static Vec3 safeDashEndpoint(LocalPlayer p, Vec3 dir, double distance) {
        var level = p.level();
        Vec3 from = p.position().add(0, 0.6, 0);
        Vec3 to = from.add(dir.scale(distance));
        var hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        Vec3 end = hit.getType() == HitResult.Type.MISS ? to : hit.getLocation().subtract(dir.scale(0.45));
        return new Vec3(end.x, p.getY(), end.z);
    }

    private static List<LivingEntity> targetsInRadius(Vec3 center, double radius, int maxTargets) {
        double r2 = radius * radius;
        var box = new AABB(center, center).inflate(radius + 1.0, Math.max(3.5, radius * 0.75), radius + 1.0);
        var list = new ArrayList<LivingEntity>();
        for (var e : SkillController.INSTANCE.targets(box)) {
            Vec3 c = e.getBoundingBox().getCenter();
            double dx = c.x - center.x, dz = c.z - center.z;
            double dy = Math.abs(c.y - (center.y + 0.8));
            if (dx * dx + dz * dz <= r2 + e.getBbWidth() * 0.5 && dy <= Math.max(3.8, radius * 0.85)) {
                list.add(e);
            }
        }
        list.sort(Comparator.comparingDouble(e -> e.distanceToSqr(center)));
        return list.size() > maxTargets ? list.subList(0, maxTargets) : list;
    }

    private static LivingEntity findNearestTarget(Vec3 origin, double radius) {
        var list = targetsInRadius(origin, radius, 1);
        return list.isEmpty() ? null : list.getFirst();
    }

    private static LivingEntity findNearestUnhit(Vec3 origin, double radius, Set<UUID> excluded) {
        for (var e : targetsInRadius(origin, radius, 12)) {
            if (!excluded.contains(e.getUUID())) return e;
        }
        return null;
    }

    private static LivingEntity findEntityByUuid(UUID uuid) {
        if (uuid == null) return null;
        var mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        var e = mc.level.getEntity(uuid);
        return e instanceof LivingEntity living ? living : null;
    }

    private static Vec3 pickDensestOrCenter(List<LivingEntity> targets, Vec3 fallback) {
        if (targets.isEmpty()) return fallback;
        LivingEntity best = targets.getFirst();
        int bestCount = -1;
        for (var a : targets) {
            int count = 0;
            for (var b : targets) {
                if (a.distanceToSqr(b) <= 12.0) count++;
            }
            if (count > bestCount) {
                bestCount = count;
                best = a;
            }
        }
        return best.position();
    }

    private static Vec3 pickPriorityTargetOrCenter(List<LivingEntity> targets, Vec3 fallback) {
        if (targets.isEmpty()) return fallback;
        LivingEntity best = targets.getFirst();
        double bestHp = -1;
        for (var e : targets) {
            var ctx = state().context(e);
            double hp = ctx == null ? e.getMaxHealth() : ctx.health(ctx.level(state().progress().level, state().encounterSync()));
            if (hp > bestHp) {
                bestHp = hp;
                best = e;
            }
        }
        return best.position();
    }

    private static void queuePull(LivingEntity target, Vec3 pullCenter, double ratio) {
        var ctx = state().context(target);
        if (ctx != null && "BOSS".equals(ctx.kind)) return;
        Vec3 cur = target.position();
        Vec3 next = new Vec3(
            cur.x + (pullCenter.x - cur.x) * ratio,
            cur.y,
            cur.z + (pullCenter.z - cur.z) * ratio
        );
        pendingMotions.put(target.getUUID(), next);
    }

    private static void queueMove(LivingEntity target, Vec3 to) {
        var ctx = state().context(target);
        if (ctx != null && "BOSS".equals(ctx.kind)) return;
        pendingMotions.put(target.getUUID(), to);
    }

    private static void flushPendingMotions(long now) {
        if (pendingMotions.isEmpty() || now - lastMotionSendAt < MOTION_SYNC_INTERVAL_MS) return;
        lastMotionSendAt = now;
        var arr = new JsonArray();
        for (var entry : pendingMotions.entrySet()) {
            var obj = new JsonObject();
            obj.addProperty("target", entry.getKey().toString());
            obj.addProperty("x", entry.getValue().x);
            obj.addProperty("y", entry.getValue().y);
            obj.addProperty("z", entry.getValue().z);
            arr.add(obj);
        }
        pendingMotions.clear();
        var packet = new JsonObject();
        packet.addProperty("session", state().session());
        packet.addProperty("generation", state().own().generation);
        packet.add("moves", arr);
        state().send("careerMotion", packet);
    }

    private static JsonObject witchMotion(int element, int morphMode) {
        var obj = new JsonObject();
        obj.addProperty("element", element);
        obj.addProperty("morphMode", morphMode);
        return obj;
    }
}
