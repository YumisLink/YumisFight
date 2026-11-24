package cn.yumis.fight.modules.skills.client;

import cn.yumis.fight.modules.character.api.Progress;
import cn.yumis.fight.modules.combat.api.ActionStatus;
import cn.yumis.fight.modules.combat.api.CombatContentTypes;
import cn.yumis.fight.modules.dungeons.api.client.SealBossState;
import cn.yumis.fight.modules.equipment.api.EquipmentItems;
import cn.yumis.fight.modules.presentation.api.client.ClientUi;
import cn.yumis.fight.modules.skills.api.*;
import cn.yumis.fight.modules.skills.api.client.*;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import java.util.*;

/**
 * Owning-client runtime for all 9 Elementalist active skills, morphMode = 0/1/2 branches,
 * local multi-charge pools, second-press manual detonate, Ice Wall DR/projectile block,
 * Lightning Strike channel aim, and Astral Storm levitation/meteors/chaotic core.
 */
public final class ElementalistClient {
    private static final long MOTION_SYNC_INTERVAL_MS = 250L;
    private static final long ASTRAL_METEOR_FALL_MS = 350L;

    public static final class IceWallState {
        public SkillCast cast;
        public Vec3 center;
        public double radius, blastRadius, slowRatio, damageReduction;
        public long start, endAt, burstAt, nextPulseAt;
        public boolean burstDone, hasPulse;
        public int blockRemaining, blockMax, sequence;
        public double pulseDot;
    }

    private static final class IceWaveState {
        SkillCast cast;
        Vec3 origin, dir;
        double range, width, travelled;
        long start, lastTick;
        int sequence;
        final Set<UUID> struck = new HashSet<>();
    }

    private static final class VortexState {
        SkillCast cast;
        Vec3 center;
        double radius;
        boolean followPlayer;
        long start, intervalMs, nextHitAt;
        int totalHits, delivered, sequence, mode;
    }

    private static final class VoidSphereState {
        SkillCast cast;
        Vec3 position, dir;
        double radius, blastRadius, speed, pushStrength, earlyKeepRatio;
        boolean stationary, manualDetonate, stopped;
        long start, endAt, intervalMs, nextTickAt;
        int totalTicks, delivered, sequence, mode;
    }

    private static final class FlamePillarState {
        SkillCast cast;
        final List<Vec3> centers = new ArrayList<>();
        double radius, controlSeconds;
        long start, impactDelayMs, visualDuration;
        int mode, nextPillar, sequence;
    }

    private static final class BusterMeteor {
        SkillCast cast;
        Vec3 center;
        double radius, shockwaveRadius, emberDot;
        long spawnAt, impactAt, shockwaveAt, emberEndAt, emberIntervalMs, nextEmberAt;
        boolean spawned, impacted, shockwaved;
        int mode, meteorIndex, emberTick, sequence;
    }

    private static final class ArcticState {
        SkillCast cast;
        Vec3 center;
        double radius, pullRadius, pullStrength, freezeSeconds;
        long start, intervalMs, nextWaveAt, finisherAt;
        int totalWaves, delivered, mode, sequence;
        boolean pierced;
    }

    public static final class BlackHoleState {
        public SkillCast cast;
        public Vec3 center;
        public double radius, blastRadius, pullRadius, pullStrength, earlyKeepRatio, volcanoDot;
        public boolean manualDetonate;
        public long start, endAt, intervalMs, nextTickAt, subHoleEndAt, nextVolcanoAt;
        public int totalTicks, delivered, mode, sequence, volcanoTick;
        public boolean detonated;
    }

    public static final class LightningChannelState {
        public SkillCast cast;
        public Vec3 reticle;
        public double radius, range, cursorSpeed, pullOnHit, channelProtection;
        public long start, endAt, intervalMs, nextBoltAt, lastTick;
        public int totalBolts, delivered, sequence;
    }

    private static final class StormCloudState {
        SkillCast cast;
        Vec3 pos;
        double radius, pullOnHit;
        long start, endAt, intervalMs, nextBoltAt;
        int totalBolts, delivered, sequence;
    }

    public static final class AstralStormState {
        public SkillCast cast;
        public Vec3 center, groundOrigin, hoverTarget;
        public double fieldRadius, meteorRadius, finisherRadius, homingBias;
        public boolean channeling, previousGravity, finished;
        public long start, endAt, intervalMs, nextMeteorAt;
        public int totalMeteors, delivered, mode, sequence;
        final List<PendingMeteor> meteors = new ArrayList<>();
    }

    private record PendingMeteor(Vec3 point, int element, long impactAt, int index) {}

    private static final class ChargePool {
        int max, available;
        long rechargeMillis, nextRechargeAt;
    }

    private static IceWallState activeIceWall;
    private static final List<IceWaveState> iceWaves = new ArrayList<>();
    private static final List<VortexState> vortices = new ArrayList<>();
    private static final List<VoidSphereState> voidSpheres = new ArrayList<>();
    private static final List<FlamePillarState> flamePillars = new ArrayList<>();
    private static final List<BusterMeteor> busterMeteors = new ArrayList<>();
    private static final List<ArcticState> arctics = new ArrayList<>();
    private static final List<BlackHoleState> blackHoles = new ArrayList<>();
    private static LightningChannelState lightningChannel;
    private static final List<StormCloudState> stormClouds = new ArrayList<>();
    private static AstralStormState astralStorm;
    private static final List<AstralStormState> detachedStorms = new ArrayList<>();

    private static final Map<String, ChargePool> chargePools = new HashMap<>();
    private static final Map<UUID, Vec3> pendingMotions = new LinkedHashMap<>();
    private static long lastMotionSendAt = 0L;
    private static final Random RNG = new Random();

    private ElementalistClient() {}

    private static SkillsClient state() { return SkillsClient.current(); }
    private static LocalPlayer player() { return Minecraft.getInstance().player; }

    static void installHooks() {
        SkillCastHooks.onRequest(SkillCastHooks.Stage.BEFORE_REQUEST, req -> {
            if (!beforeRequest(req.skill)) req.rejected = true;
        });
    }

    public static void install() {
        MageClient.register(ElementalistSkills.ICE_WALL, ElementalistClient::startIceWall);
        MageClient.register(ElementalistSkills.LIGHTNING_VORTEX, ElementalistClient::startVortex);
        MageClient.register(ElementalistSkills.VOID_SPHERE, ElementalistClient::startVoidSphere);
        MageClient.register(ElementalistSkills.FLAME_PILLAR, ElementalistClient::startFlamePillar);
        MageClient.register(ElementalistSkills.HALLOWEEN_BUSTER, ElementalistClient::startHalloweenBuster);
        MageClient.register(ElementalistSkills.ARCTIC_FEAST, ElementalistClient::startArcticFeast);
        MageClient.register(ElementalistSkills.BLACK_HOLE, ElementalistClient::startBlackHole);
        MageClient.registerAction(ElementalistSkills.LIGHTNING_STRIKE, ElementalistClient::startLightningStrike);
        MageClient.registerAction(ElementalistSkills.ASTRAL_STORM, ElementalistClient::startAstralStorm);
    }

    static boolean prepareCast(SkillCast cast, int slot, Progress progress, Runnable release) {
        if (!ElementalistRules.usesCastTime(cast.skill, progress)) return false;
        Runnable chant = () -> {
            long duration = Math.round(ElementalistRules.effectiveCastSeconds(cast.skill, cast.speed) * 1000.0);
            ChantClient.begin(cast, duration, ElementalistRules.movingCastLearned(cast.stats, progress), () -> {
                cast.origin = player().position();
                cast.center = cast.origin;
                release.run();
            });
        };
        if (ElementalistRules.isChargeable(cast.skill, progress, cast.parameters)) {
            ElementalistChargeClient.begin(cast, slot, chant);
        } else {
            chant.run();
        }
        return true;
    }

    public static boolean invulnerable() {
        return astralStorm != null && astralStorm.channeling && !astralStorm.finished;
    }

    public static boolean blocksMovement() {
        return lightningChannel != null || invulnerable();
    }

    public static boolean blocksBasic() {
        return lightningChannel != null || invulnerable();
    }

    public static IceWallState activeIceWall() {
        return activeIceWall;
    }

    public static BlackHoleState activeBlackHole() {
        for (var bh : blackHoles) {
            if (!bh.detonated) return bh;
        }
        return null;
    }

    public static LightningChannelState activeLightningChannel() {
        return lightningChannel;
    }

    public static AstralStormState activeAstralStorm() {
        return astralStorm;
    }

    public static double careerDamageTaken(double normal) {
        double taken = normal;
        if (lightningChannel != null) {
            double prot = Math.clamp(lightningChannel.channelProtection, 0.0, 0.95);
            taken = Math.min(taken, 1.0 - prot);
        }
        if (activeIceWall != null && player() != null) {
            double distSq = player().position().subtract(activeIceWall.center).horizontalDistanceSqr();
            if (distSq <= activeIceWall.radius * activeIceWall.radius) {
                double wallDr = Math.clamp(activeIceWall.damageReduction, 0.0, 0.9);
                taken *= (1.0 - wallDr);
            }
        }
        return taken;
    }

    /** Intercepts ordinary incoming enemy projectiles while standing inside Ice Wall. */
    public static boolean tryBlockProjectile(JsonObject incoming) {
        if (activeIceWall == null || activeIceWall.blockRemaining <= 0) return false;
        var p = player();
        if (p == null) return false;
        if (incoming.has("bypassInvulnerability") && incoming.get("bypassInvulnerability").getAsBoolean()) return false;
        boolean projectile = (incoming.has("projectile") && incoming.get("projectile").getAsBoolean())
            || (incoming.has("source") && Set.of("arrow", "mobProjectile", "trident", "fireball", "unattributedFireball", "witherSkull").contains(incoming.get("source").getAsString()));
        if (!projectile) return false;
        if (p.position().subtract(activeIceWall.center).horizontalDistanceSqr() > activeIceWall.radius * activeIceWall.radius) {
            return false;
        }
        activeIceWall.blockRemaining--;
        var motion = new JsonObject();
        motion.addProperty("radius", activeIceWall.radius);
        motion.addProperty("wallBlockRemaining", activeIceWall.blockRemaining);
        motion.addProperty("mode", 0);
        MageClient.INSTANCE.visual(activeIceWall.cast, activeIceWall.cast.id, "HIT",
            activeIceWall.center, Vec3.ZERO, Math.max(200L, activeIceWall.endAt - ClientUi.now()),
            ++activeIceWall.sequence, motion);
        return true;
    }

    public static boolean beforeRequest(SkillDefinition skill) {
        if (invulnerable()) return false;
        if (lightningChannel != null) {
            if (ElementalistSkills.LIGHTNING_STRIKE.equals(skill.id)) return false;
            cancelChannel();
            return true;
        }
        return true;
    }

    public static boolean cancelChannel() {
        if (lightningChannel == null) return false;
        var ch = lightningChannel;
        lightningChannel = null;
        PlayerPoseChannel.cancel(ch.cast);
        if (player() != null) {
            MageClient.INSTANCE.visual(ch.cast, ch.cast.id, "CANCEL", ch.reticle, Vec3.ZERO, 300L, ++ch.sequence, null);
        }
        CareerClient.INSTANCE.ended(ch.cast);
        CareerClient.INSTANCE.publish();
        return true;
    }

    public static void cancelOnScreen() {
        cancelChannel();
    }

    // --- Multi-charge pool helpers (e.g. void_sphere Morph B maxCharges = 2) ---

    private static ChargePool syncPool(String skillId, int max, long rechargeMillis, long now) {
        var pool = chargePools.computeIfAbsent(skillId, k -> {
            var p = new ChargePool();
            p.max = max;
            p.available = max;
            p.rechargeMillis = Math.max(500L, rechargeMillis);
            p.nextRechargeAt = 0L;
            return p;
        });
        pool.max = max;
        if (rechargeMillis > 0) pool.rechargeMillis = Math.max(500L, rechargeMillis);
        if (pool.available > pool.max) pool.available = pool.max;
        while (pool.available < pool.max && pool.nextRechargeAt > 0 && now >= pool.nextRechargeAt) {
            pool.available++;
            pool.nextRechargeAt = pool.available < pool.max ? pool.nextRechargeAt + pool.rechargeMillis : 0L;
        }
        return pool;
    }

    public static boolean hasCharge(SkillDefinition skill, SkillParameters.Snapshot params, long now) {
        if (skill == null || params == null) return false;
        int max = (int) Math.round(params.get("maxCharges", 1.0));
        if (max <= 1) return false;
        var pool = syncPool(skill.id, max, 0L, now);
        return pool.available > 0;
    }

    public static void onCastPaid(SkillDefinition skill, SkillParameters.Snapshot params, long cooldownMillis,
                                  long now, cn.yumis.fight.modules.combat.api.Runtime resources) {
        if (skill == null || params == null) return;
        int max = (int) Math.round(params.get("maxCharges", 1.0));
        if (max <= 1) return;
        if (resources.training || (player() != null && player().isCreative())) {
            chargePools.remove(skill.id);
            return;
        }
        var pool = syncPool(skill.id, max, cooldownMillis, now);
        if (pool.available > 0) pool.available--;
        if (pool.available < pool.max && pool.nextRechargeAt <= now) {
            pool.nextRechargeAt = now + Math.max(500L, cooldownMillis);
        }
        if (pool.available > 0) {
            resources.cooldowns.put(skill.id, now + 150L);
        } else {
            resources.cooldowns.put(skill.id, pool.nextRechargeAt);
        }
    }

    public static int remainingCharges(String skillId, int max, long now) {
        if (max <= 1) return 0;
        return syncPool(skillId, max, 0L, now).available;
    }

    public static long nextRechargeRemaining(String skillId, long now) {
        var pool = chargePools.get(skillId);
        if (pool == null || pool.available >= pool.max || pool.nextRechargeAt <= now) return 0L;
        return pool.nextRechargeAt - now;
    }

    // --- Second-press manual detonation (0 MP, 0 CD) ---

    public static boolean tryManualDetonate(String skillId) {
        if (skillId == null || skillId.isEmpty()) return false;
        var p = player();
        if (p == null || !state().ready() || state().own().dead || p.isDeadOrDying()
            || CombatContentTypes.controlled(p) || SealBossState.isFrozen()) {
            return false;
        }
        long now = ClientUi.now();
        if (ElementalistSkills.BLACK_HOLE.equals(skillId)) {
            for (var bh : blackHoles) {
                if (!bh.detonated && bh.manualDetonate) {
                    detonateBlackHole(bh, now, true);
                    return true;
                }
            }
        } else if (ElementalistSkills.VOID_SPHERE.equals(skillId)) {
            for (var it = voidSpheres.iterator(); it.hasNext(); ) {
                var vs = it.next();
                if (vs.manualDetonate) {
                    detonateVoidSphere(vs, now);
                    it.remove();
                    return true;
                }
            }
        }
        return false;
    }

    // --- Skill starters ---

    private static void startIceWall(SkillCast c) {
        var p = player();
        int mode = ElementalistRules.morphMode(c.parameters);
        long now = ClientUi.now();
        int elem = effectiveElem(c);
        if (mode == 1) {
            // 极寒推进: forward arc ice wave pushing enemies along 10 blocks
            var w = new IceWaveState();
            w.cast = c;
            w.origin = p.position();
            w.dir = horizontal(p.getLookAngle());
            w.range = Math.max(2.0, c.parameters.get("range", 10.0));
            w.width = Math.max(1.5, c.parameters.get("radius", 4.5));
            w.start = w.lastTick = now;
            iceWaves.add(w);
            TrainingCombat.hold(c.id, "elementalist", now + 2000L);
            var motion = elemMotion(elem, mode);
            motion.addProperty("range", w.range);
            motion.addProperty("width", w.width);
            MageClient.INSTANCE.visual(c, c.id, "START", w.origin, w.dir, 900L, 0, motion);
            return;
        }

        // Mode 0 (冰墙) or Mode 2 (极光雷冰壁): replace previous wall without double shatter
        if (activeIceWall != null) {
            MageClient.INSTANCE.visual(activeIceWall.cast, activeIceWall.cast.id, "CANCEL",
                activeIceWall.center, Vec3.ZERO, 200L, ++activeIceWall.sequence, null);
            CareerClient.INSTANCE.ended(activeIceWall.cast);
            activeIceWall = null;
        }
        var wall = new IceWallState();
        wall.cast = c;
        wall.center = p.position();
        wall.radius = Math.max(1.5, c.parameters.get("radius", 3.5));
        wall.blastRadius = Math.max(wall.radius, c.parameters.get("blastRadius", 4.0));
        wall.slowRatio = Math.clamp(c.parameters.get("slowRatio", 0.30), 0.0, 0.8);
        wall.damageReduction = Math.clamp(c.parameters.get("wallDamageReduction", 0.25), 0.0, 0.9);
        wall.blockMax = wall.blockRemaining = Math.max(0, (int) Math.round(c.parameters.get("wallBlockQuota", 6.0)));
        wall.pulseDot = c.parameters.get("pulseDot", 0.0);
        wall.hasPulse = wall.pulseDot > 0.0;
        long durationMs = Math.max(500L, Math.round(c.parameters.get("duration", 6.0) * 1000.0));
        wall.start = now;
        wall.burstAt = now + 150L;
        wall.endAt = now + durationMs;
        long pulseGap = Math.max(100L, Math.round(c.parameters.get("interval", 0.50) * 1000.0));
        wall.nextPulseAt = now + pulseGap;
        activeIceWall = wall;
        TrainingCombat.hold(c.id, "elementalist", wall.endAt + 500L);
        var motion = elemMotion(elem, mode);
        motion.addProperty("radius", wall.radius);
        motion.addProperty("wallHeight", c.parameters.get("wallHeight", 2.2));
        motion.addProperty("wallSegments", c.parameters.get("wallSegments", 12.0));
        MageClient.INSTANCE.visual(c, c.id, "START", wall.center, Vec3.ZERO, durationMs + 500L, 0, motion);
    }

    private static void startVortex(SkillCast c) {
        var p = player();
        int mode = ElementalistRules.morphMode(c.parameters);
        long now = ClientUi.now();
        int elem = effectiveElem(c);
        if (mode == 1) {
            // 超导电磁炮: instant piercing railgun + endpoint blast
            Vec3 from = p.getEyePosition();
            Vec3 dir = p.getLookAngle().normalize();
            double range = Math.max(2.0, c.parameters.get("range", 14.0));
            Vec3 end = from.add(dir.scale(range));
            var wallHit = p.level().clip(new ClipContext(from, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
            if (wallHit.getType() != HitResult.Type.MISS) end = wallHit.getLocation();
            double beamWidth = 1.6;
            double coeff = c.parameters.get("damage", 1.0) * Math.max(1.0, c.parameters.get("slashes", 3.0));
            var struck = new HashSet<UUID>();
            for (var target : SkillController.INSTANCE.targets(new AABB(from, end).inflate(beamWidth + 0.5))) {
                var box = target.getBoundingBox().inflate(beamWidth * 0.5);
                if ((box.clip(from, end).isPresent() || box.contains(from))
                    && SkillController.INSTANCE.sight(from, target.getBoundingBox().getCenter())) {
                    struck.add(target.getUUID());
                    MageClient.INSTANCE.issue(c, target, coeff, elem, "", "railgun");
                }
            }
            double blastR = Math.max(1.5, c.parameters.get("radius", 4.0) * 0.65);
            for (var target : SkillController.INSTANCE.targets(new AABB(end, end).inflate(blastR, 2.5, blastR))) {
                if (struck.contains(target.getUUID())) continue;
                if (target.getBoundingBox().getCenter().distanceToSqr(end) <= blastR * blastR
                    && SkillController.INSTANCE.sight(end.add(0, 0.2, 0), target.getBoundingBox().getCenter())) {
                    MageClient.INSTANCE.issue(c, target, coeff, elem, "", "railgun_end");
                }
            }
            var motion = elemMotion(elem, mode);
            motion.addProperty("range", from.distanceTo(end));
            motion.addProperty("radius", blastR);
            MageClient.INSTANCE.visual(c, c.id, "START", from, dir, 700L, 0, motion);
            MageClient.INSTANCE.finished(c);
            return;
        }

        // Mode 0 (雷旋) or Mode 2 (护体雷环)
        var v = new VortexState();
        v.cast = c;
        v.mode = mode;
        v.followPlayer = mode == 2;
        v.radius = Math.max(1.5, c.parameters.get("radius", 4.0));
        double offset = v.followPlayer ? 0.0 : c.parameters.get("centerOffset", 2.0);
        v.center = p.position().add(horizontal(p.getLookAngle()).scale(offset));
        v.totalHits = Math.max(1, (int) Math.round(c.parameters.get("slashes", 3.0)));
        v.intervalMs = Math.max(50L, Math.round(c.parameters.get("interval", 0.25) * 1000.0));
        v.start = now;
        v.nextHitAt = now;
        vortices.add(v);
        long durMs = Math.max(500L, v.totalHits * v.intervalMs + 250L);
        TrainingCombat.hold(c.id, "elementalist", now + durMs + 300L);
        var motion = elemMotion(elem, mode);
        motion.addProperty("radius", v.radius);
        motion.addProperty("orbCount", c.parameters.get("orbCount", 4.0));
        motion.addProperty("follow", v.followPlayer);
        MageClient.INSTANCE.visual(c, c.id, "START", v.center, horizontal(p.getLookAngle()), durMs, 0, motion);
    }

    private static void startVoidSphere(SkillCast c) {
        var p = player();
        int mode = ElementalistRules.morphMode(c.parameters);
        long now = ClientUi.now();
        int elem = effectiveElem(c);
        var vs = new VoidSphereState();
        vs.cast = c;
        vs.mode = mode;
        vs.dir = horizontal(p.getLookAngle());
        double sizeFraction = ElementalistRules.chargeScaleMultiplier(c.parameters, c.chargeProgress)
            / ElementalistRules.chargeScaleMultiplier(c.parameters, 1.0);
        vs.radius = Math.max(0.8 * sizeFraction, c.parameters.get("radius", 1.8 * sizeFraction));
        vs.blastRadius = Math.max(vs.radius, c.parameters.get("blastRadius", 3.8 * sizeFraction));
        vs.speed = Math.max(0.0, c.parameters.get("projectileSpeed", 3.0));
        vs.pushStrength = Math.max(0.0, c.parameters.get("pushStrength", 0.35));
        vs.manualDetonate = c.parameters.get("manualDetonate", 0.0) >= 1.0;
        vs.earlyKeepRatio = Math.clamp(c.parameters.get("earlyDetonateKeepRatio", 0.0), 0.0, 2.0);
        vs.stationary = mode == 1 || vs.speed <= 0.05;
        double startOffset = vs.stationary ? 2.5 : 1.2;
        vs.position = p.position().add(vs.dir.scale(startOffset)).add(0, 1.0, 0);
        vs.totalTicks = ElementalistRules.voidSphereTicks(c.parameters);
        vs.intervalMs = Math.max(50L, Math.round(c.parameters.get("interval", 0.25) * 1000.0));
        long durMs = Math.max(250L, Math.round(c.parameters.get("duration", 4.0) * 1000.0));
        vs.start = now;
        vs.endAt = now + durMs;
        vs.nextTickAt = now + 80L;
        voidSpheres.add(vs);
        TrainingCombat.hold(c.id, "elementalist", vs.endAt + 400L);
        var motion = elemMotion(elem, mode);
        motion.addProperty("radius", vs.radius);
        motion.addProperty("blastRadius", 2.2 * sizeFraction);
        motion.addProperty("speed", vs.speed);
        motion.addProperty("stationary", vs.stationary);
        MageClient.INSTANCE.visual(c, c.id, "START", vs.position, vs.dir, durMs + 300L, 0, motion);
    }

    private static void startFlamePillar(SkillCast c) {
        var p = player();
        int mode = ElementalistRules.morphMode(c.parameters);
        long now = ClientUi.now();
        int elem = effectiveElem(c);
        double range = Math.max(2.0, c.parameters.get("range", 14.0));
        Vec3 baseCenter = aimGround(p, range, 5.0);
        Vec3 forward = horizontal(p.getLookAngle());

        var fp = new FlamePillarState();
        fp.cast = c;
        fp.mode = mode;
        fp.radius = Math.max(1.0, c.parameters.get("radius", 3.0));
        fp.controlSeconds = Math.max(0.0, c.parameters.get("controlSeconds", 0.60));
        fp.impactDelayMs = Math.max(50L, Math.round(c.parameters.get("impactDelay", 0.20) * 1000.0));
        fp.start = now;

        int count = Math.max(1, (int) Math.round(c.parameters.get("pillarCount", 1.0)));
        double spread = Math.max(0.5, c.parameters.get("pillarSpread", 2.8));
        for (int i = 0; i < count; i++) {
            double shift = (i - (count - 1) * 0.5) * spread;
            Vec3 pt = snapGround(p, baseCenter.add(forward.scale(shift)));
            fp.centers.add(pt);
        }
        if (mode == 1 && fp.controlSeconds > 0) {
            // 霜火枷锁: immediate frost bind before main eruption
            for (var target : targetsInCylinder(fp.centers.getFirst(), fp.radius, 3.5)) {
                MageClient.INSTANCE.issueElementalist(c, target, 0.01, 2, fp.controlSeconds, "", "frost_bind");
            }
        }
        flamePillars.add(fp);
        fp.visualDuration = fp.impactDelayMs + (count - 1) * 180L + ElementalistVisuals.FLAME_ERUPTION_MS;
        TrainingCombat.hold(c.id, "elementalist", now + fp.visualDuration);
        var motion = elemMotion(elem, mode);
        motion.addProperty("radius", fp.radius);
        motion.addProperty("pillarCount", count);
        motion.addProperty("impactDelayMs", fp.impactDelayMs);
        MageClient.INSTANCE.visual(c, c.id, "START", fp.centers.getFirst(), forward, fp.visualDuration, 0, motion);
    }

    private static void startHalloweenBuster(SkillCast c) {
        var p = player();
        int mode = ElementalistRules.morphMode(c.parameters);
        long now = ClientUi.now();
        int elem = effectiveElem(c);
        double range = Math.max(2.0, c.parameters.get("range", 16.0));
        Vec3 center = aimGround(p, range, 6.0);
        int count = Math.max(1, (int) Math.round(c.parameters.get("meteorCount", 1.0)));
        long gapMs = Math.max(100L, Math.round(c.parameters.get("twinGapSeconds", 0.30) * 1000.0));
        long fallMs = Math.max(100L, Math.round(c.parameters.get("fallDelay", 0.55) * 1000.0));
        long emberDurMs = Math.max(0L, Math.round(c.parameters.get("emberDuration", 0.0) * 1000.0));
        long emberGapMs = Math.max(100L, Math.round(c.parameters.get("emberInterval", 0.50) * 1000.0));
        double radius = Math.max(1.5, c.parameters.get("radius", 4.5));
        double shockR = Math.max(radius, c.parameters.get("shockwaveRadius", 7.0));
        double emberDot = Math.max(0.0, c.parameters.get("emberDot", 0.0));
        Vec3 forward = horizontal(p.getLookAngle());

        for (int i = 0; i < count; i++) {
            var m = new BusterMeteor();
            m.cast = c;
            m.mode = mode;
            m.meteorIndex = i;
            Vec3 offset = count > 1 ? forward.scale(i == 0 ? -1.4 : 1.4) : Vec3.ZERO;
            m.center = snapGround(p, center.add(offset));
            m.radius = radius;
            m.shockwaveRadius = shockR;
            m.emberDot = emberDot;
            m.spawnAt = now + i * gapMs;
            m.impactAt = m.spawnAt + fallMs;
            m.shockwaveAt = m.impactAt + 80L;
            m.emberEndAt = emberDurMs > 0 && emberDot > 0 ? m.impactAt + emberDurMs : 0L;
            m.emberIntervalMs = emberGapMs;
            m.nextEmberAt = m.impactAt + emberGapMs;
            busterMeteors.add(m);
            if (i == 0) {
                m.spawned = true;
                var motion = elemMotion(elem, mode);
                motion.addProperty("radius", radius);
                motion.addProperty("shockwaveRadius", shockR);
                motion.addProperty("fallMs", fallMs);
                motion.addProperty("emberMs", emberDurMs);
                MageClient.INSTANCE.visual(c, c.id, "START", m.center, forward, fallMs + emberDurMs + 700L, 0, motion);
            }
        }
        TrainingCombat.hold(c.id, "elementalist", now + (count - 1) * gapMs + fallMs + emberDurMs + 900L);
    }

    private static void startArcticFeast(SkillCast c) {
        var p = player();
        int mode = ElementalistRules.morphMode(c.parameters);
        long now = ClientUi.now();
        int elem = effectiveElem(c);
        double offset = Math.max(0.0, c.parameters.get("centerOffset", 4.5));
        double range = Math.max(offset + 2.0, c.parameters.get("range", 14.0));
        Vec3 center = aimGround(p, range, offset);
        if (center.distanceTo(p.position()) > range) {
            center = snapGround(p, p.position().add(horizontal(p.getLookAngle()).scale(offset)));
        }

        var a = new ArcticState();
        a.cast = c;
        a.mode = mode;
        a.center = center;
        a.radius = Math.max(1.5, c.parameters.get("radius", 5.5));
        a.pullRadius = Math.max(0.0, c.parameters.get("pullRadius", 0.0));
        a.pullStrength = Math.max(0.0, c.parameters.get("pullStrength", 0.0));
        a.freezeSeconds = Math.max(0.0, c.parameters.get("freezeSeconds", 1.5));
        a.totalWaves = Math.max(1, (int) Math.round(c.parameters.get("slashes", 8.0)));
        a.intervalMs = Math.max(50L, Math.round(c.parameters.get("interval", 0.22) * 1000.0));
        long durMs = Math.max(300L, Math.round(c.parameters.get("duration", 2.0) * 1000.0));
        a.start = now;
        a.nextWaveAt = now + (mode == 1 ? 200L : 120L);
        a.finisherAt = now + durMs;
        arctics.add(a);
        TrainingCombat.hold(c.id, "elementalist", a.finisherAt + 500L);
        var motion = elemMotion(elem, mode);
        motion.addProperty("radius", a.radius);
        motion.addProperty("pullRadius", a.pullRadius);
        motion.addProperty("firstWaveMs", a.nextWaveAt - a.start);
        motion.addProperty("waveIntervalMs", a.intervalMs);
        motion.addProperty("waveCount", a.totalWaves);
        MageClient.INSTANCE.visual(c, c.id, "START", a.center, Vec3.ZERO, durMs + ElementalistVisuals.ARCTIC_FINISH_MS, 0, motion);
    }

    private static void startBlackHole(SkillCast c) {
        var p = player();
        int mode = ElementalistRules.morphMode(c.parameters);
        long now = ClientUi.now();
        int elem = effectiveElem(c);
        double range = Math.max(2.0, c.parameters.get("range", 16.0));
        Vec3 ground = aimGround(p, range, 6.0);

        var bh = new BlackHoleState();
        bh.cast = c;
        bh.mode = mode;
        bh.center = ground.add(0, 1.8, 0);
        bh.radius = Math.max(1.5, c.parameters.get("radius", 4.2));
        bh.blastRadius = Math.max(bh.radius, c.parameters.get("blastRadius", 6.0));
        bh.pullRadius = Math.max(bh.radius, c.parameters.get("pullRadius", 7.5));
        bh.pullStrength = Math.max(0.0, c.parameters.get("pullStrength", 4.5));
        bh.manualDetonate = c.parameters.get("manualDetonate", 1.0) >= 1.0;
        bh.earlyKeepRatio = Math.clamp(c.parameters.get("earlyDetonateKeepRatio", 0.40), 0.0, 2.0);
        bh.volcanoDot = Math.max(0.0, c.parameters.get("volcanoDot", 0.0));
        bh.totalTicks = Math.max(1, (int) Math.round(c.parameters.get("slashes", 10.0)));
        bh.intervalMs = Math.max(50L, Math.round(c.parameters.get("interval", 0.30) * 1000.0));
        long durMs = Math.max(300L, Math.round(c.parameters.get("duration", 3.0) * 1000.0));
        long subMs = Math.max(0L, Math.round(c.parameters.get("subHoleDuration", 0.0) * 1000.0));
        bh.start = now;
        bh.endAt = now + durMs;
        bh.nextTickAt = now + bh.intervalMs;
        bh.nextVolcanoAt = now + bh.intervalMs;
        blackHoles.add(bh);
        TrainingCombat.hold(c.id, "elementalist", bh.endAt + subMs + 600L);
        var motion = elemMotion(elem, mode);
        motion.addProperty("radius", bh.radius);
        motion.addProperty("pullRadius", bh.pullRadius);
        motion.addProperty("blastRadius", bh.blastRadius);
        motion.addProperty("volcano", bh.volcanoDot > 0);
        MageClient.INSTANCE.visual(c, c.id, "START", bh.center, Vec3.ZERO, durMs + subMs + 500L, 0, motion);
    }

    private static void startLightningStrike(SkillCast c) {
        var p = player();
        int mode = ElementalistRules.morphMode(c.parameters);
        long now = ClientUi.now();
        int elem = effectiveElem(c);
        double range = Math.max(4.0, c.parameters.get("range", 18.0));
        double radius = Math.max(1.5, c.parameters.get("radius", 3.5));
        double pullOnHit = Math.max(0.0, c.parameters.get("pullOnHit", 1.8));
        boolean channeling = c.parameters.get("channeling", 1.0) >= 0.5;
        Vec3 ground = aimGround(p, range, 7.0);

        if (!channeling && mode == 1) {
            // 天雷湮灭: single colossal thunderbolt
            double mergedCoeff = c.parameters.get("damage", 0.20) * Math.max(1.0, c.parameters.get("slashes", 5.0));
            dropThunderBolt(c, ground, radius, mergedCoeff, elem, pullOnHit, 0, mode);
            PlayerPoseChannel.release(c);
            MageClient.INSTANCE.finished(c);
            return;
        }
        if (!channeling) {
            // Mode 2 (怒雷巡弋) or any fire-and-forget variant: auto-seeking storm cloud
            var cloud = new StormCloudState();
            cloud.cast = c;
            cloud.pos = ground;
            cloud.radius = radius;
            cloud.pullOnHit = pullOnHit;
            cloud.totalBolts = Math.max(1, (int) Math.round(c.parameters.get("slashes", 6.0)));
            cloud.intervalMs = Math.max(100L, Math.round(c.parameters.get("interval", 0.55) * 1000.0));
            long durMs = Math.max(500L, Math.round(c.parameters.get("duration", 3.5) * 1000.0));
            cloud.start = now;
            cloud.endAt = now + durMs;
            cloud.nextBoltAt = now + 150L;
            stormClouds.add(cloud);
            TrainingCombat.hold(c.id, "elementalist", cloud.endAt + 500L);
            var motion = elemMotion(elem, mode);
            motion.addProperty("radius", radius);
            motion.addProperty("cloud", true);
            MageClient.INSTANCE.visual(c, c.id, "START", cloud.pos.add(0, 5.5, 0), Vec3.ZERO, durMs + 500L, 0, motion);
            PlayerPoseChannel.release(c);
            return;
        }

        // Mode 0: stationary crosshair-tracking channel with 65% channel protection
        if (lightningChannel != null) cancelChannel();
        var ch = new LightningChannelState();
        ch.cast = c;
        ch.reticle = ground;
        ch.radius = radius;
        ch.range = range;
        ch.cursorSpeed = Math.max(2.0, c.parameters.get("cursorSpeed", 14.0));
        ch.pullOnHit = pullOnHit;
        ch.channelProtection = Math.clamp(c.parameters.get("channelProtection", 0.65), 0.0, 0.95);
        ch.totalBolts = Math.max(1, (int) Math.round(c.parameters.get("slashes", 5.0)));
        ch.intervalMs = Math.max(80L, Math.round(c.parameters.get("interval", 0.35) * 1000.0));
        long durMs = Math.max(ch.totalBolts * ch.intervalMs, Math.round(c.parameters.get("duration", 1.75) * 1000.0));
        ch.start = ch.lastTick = now;
        ch.endAt = now + durMs;
        ch.nextBoltAt = now + 100L;
        lightningChannel = ch;
        TrainingCombat.hold(c.id, "elementalist", ch.endAt + 400L);
        PlayerPoseChannel.loop(c, ElementalistSkills.LIGHTNING_STRIKE, durMs, p.getLookAngle(), true);
        var motion = elemMotion(elem, mode);
        motion.addProperty("radius", radius);
        motion.addProperty("reticle", true);
        MageClient.INSTANCE.visual(c, c.id, "START", ch.reticle, Vec3.ZERO, durMs + 500L, 0, motion);
        CareerClient.INSTANCE.publish();
    }

    private static void startAstralStorm(SkillCast c) {
        var p = player();
        int mode = ElementalistRules.morphMode(c.parameters);
        long now = ClientUi.now();
        boolean channeling = c.parameters.get("channeling", 1.0) >= 0.5;
        double offset = Math.max(0.0, c.parameters.get("centerOffset", 4.0));
        double range = Math.max(offset + 2.0, c.parameters.get("range", 16.0));
        Vec3 center = snapGround(p, p.position().add(horizontal(p.getLookAngle()).scale(offset)));

        var st = new AstralStormState();
        st.cast = c;
        st.mode = mode;
        st.channeling = channeling;
        st.center = center;
        st.groundOrigin = p.position();
        st.fieldRadius = Math.max(3.0, c.parameters.get("fieldRadius", 10.0));
        st.meteorRadius = Math.max(1.5, c.parameters.get("meteorRadius", 4.2));
        st.finisherRadius = Math.max(st.fieldRadius, c.parameters.get("finisherRadius", 11.0));
        st.homingBias = Math.clamp(c.parameters.get("homingBias", 0.65), 0.0, 1.0);
        st.totalMeteors = Math.max(1, (int) Math.round(c.parameters.get("slashes", 16.0)));
        st.intervalMs = Math.max(50L, Math.round(c.parameters.get("interval", 0.18) * 1000.0));
        long durMs = Math.max(800L, Math.round(c.parameters.get("duration", 4.0) * 1000.0));
        st.start = now;
        st.endAt = now + durMs;
        st.nextMeteorAt = now + (channeling ? 350L : 250L);

        if (channeling) {
            if (astralStorm != null) finishAstralStorm(astralStorm, true);
            st.previousGravity = p.isNoGravity();
            Vec3 desiredHover = p.position().add(0, 3.5, 0);
            var ceiling = p.level().clip(new ClipContext(p.getEyePosition(), desiredHover.add(0, 1.8, 0),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
            st.hoverTarget = ceiling.getType() == HitResult.Type.MISS
                ? desiredHover
                : new Vec3(p.getX(), Math.max(p.getY() + 0.5, ceiling.getLocation().y - p.getBbHeight() - 0.15), p.getZ());
            p.stopFallFlying();
            SkillController.INSTANCE.acquireStatus(c.id, ActionStatus.NO_GRAVITY);
            SkillController.INSTANCE.acquireStatus(c.id, ActionStatus.INVULNERABLE);
            p.setDeltaMovement(Vec3.ZERO);
            p.fallDistance = 0;
            astralStorm = st;
            PlayerPoseChannel.loop(c, ElementalistSkills.ASTRAL_STORM, durMs, horizontal(p.getLookAngle()), false);
        } else {
            detachedStorms.add(st);
            PlayerPoseChannel.play(c, ElementalistSkills.ASTRAL_STORM, 700L, horizontal(p.getLookAngle()), false);
            PlayerPoseChannel.release(c);
        }

        TrainingCombat.hold(c.id, "elementalist", st.endAt + 900L);
        var motion = elemMotion(effectiveElem(c), mode);
        motion.addProperty("fieldRadius", st.fieldRadius);
        motion.addProperty("meteorRadius", st.meteorRadius);
        motion.addProperty("finisherRadius", st.finisherRadius);
        motion.addProperty("fallMs", ASTRAL_METEOR_FALL_MS);
        motion.addProperty("cinematic", channeling);
        MageClient.INSTANCE.visual(c, c.id, "START", st.center, horizontal(p.getLookAngle()), durMs + ElementalistVisuals.ASTRAL_FINISH_MS, 0, motion);
        if (!channeling) {
            try { Effects.VFX.completeCamera(UUID.fromString(c.id)); } catch (Exception ignored) {}
        }
        CareerClient.INSTANCE.publish();
    }

    // --- Main per-tick update ---

    public static void tick() {
        var mc = Minecraft.getInstance();
        var p = mc.player;
        if (p == null || mc.level == null || !state().ready() || state().own().dead) {
            clear();
            return;
        }
        long now = ClientUi.now();
        tickIceWall(now);
        tickIceWaves(now);
        tickVortices(now);
        tickVoidSpheres(now);
        tickFlamePillars(now);
        tickBusterMeteors(now);
        tickArctics(now);
        tickBlackHoles(now);
        tickLightningChannel(now);
        tickStormClouds(now);
        tickAstralStorms(now);
        flushMotions(now);
    }

    private static void tickIceWall(long now) {
        var wall = activeIceWall;
        if (wall == null) return;
        if (!MageClient.INSTANCE.valid(wall.cast)) {
            activeIceWall = null;
            return;
        }
        int elem = effectiveElem(wall.cast);
        int mode = ElementalistRules.morphMode(wall.cast.parameters);
        TrainingCombat.touch(wall.cast.id);

        if (!wall.burstDone && now >= wall.burstAt) {
            wall.burstDone = true;
            double coeff = wall.cast.parameters.get("damage", 0.70);
            double kb = wall.cast.parameters.get("knockbackDistance", 2.5);
            for (var target : targetsInCylinder(wall.center, wall.radius, 3.0)) {
                MageClient.INSTANCE.issue(wall.cast, target, coeff, elem, "", "burst");
                if (kb > 0 && canDisplace(target)) {
                    Vec3 out = horizontal(target.position().subtract(wall.center));
                    Vec3 dest = target.position().add(out.scale(kb));
                    sendImmediateMotion(target, dest);
                }
            }
        }
        if (wall.hasPulse && now >= wall.nextPulseAt && now < wall.endAt) {
            long gap = Math.max(100L, Math.round(wall.cast.parameters.get("interval", 0.50) * 1000.0));
            wall.nextPulseAt += gap;
            int pulseElem = wall.cast.parameters.get("dyeEnabled", 0.0) >= 1.0 ? elem : 0;
            for (var target : targetsInCylinder(wall.center, wall.radius + 0.5, 3.0)) {
                MageClient.INSTANCE.issue(wall.cast, target, wall.pulseDot, pulseElem, "pulse", "wall_pulse");
            }
        }
        // Soft slow on enemies inside wall via subtle positional damping (or vanilla visual feel)
        if (now >= wall.endAt) {
            double fin = wall.cast.parameters.get("finisher", 0.30);
            if (fin > 0) {
                for (var target : targetsInCylinder(wall.center, wall.blastRadius, 3.5)) {
                    MageClient.INSTANCE.issue(wall.cast, target, fin, elem, "", "shatter");
                }
            }
            var motion = elemMotion(elem, mode);
            motion.addProperty("radius", wall.blastRadius);
            MageClient.INSTANCE.visual(wall.cast, wall.cast.id, "FINISH", wall.center, Vec3.ZERO, 600L, ++wall.sequence, motion);
            CareerClient.INSTANCE.ended(wall.cast);
            activeIceWall = null;
        }
    }

    private static void tickIceWaves(long now) {
        for (var it = iceWaves.iterator(); it.hasNext(); ) {
            var w = it.next();
            if (!MageClient.INSTANCE.valid(w.cast)) { it.remove(); continue; }
            TrainingCombat.touch(w.cast.id);
            double dt = Math.clamp((now - w.lastTick) / 1000.0, 0.0, 0.2);
            w.lastTick = now;
            double prev = w.travelled;
            w.travelled = Math.min(w.range, w.travelled + 16.0 * dt);
            Vec3 from = w.origin.add(w.dir.scale(prev));
            Vec3 to = w.origin.add(w.dir.scale(w.travelled));
            int elem = effectiveElem(w.cast);
            double coeff = w.cast.parameters.get("damage", 1.0);
            for (var target : SkillController.INSTANCE.targets(new AABB(from, to).inflate(w.width * 0.5, 2.0, w.width * 0.5))) {
                Vec3 delta = target.position().subtract(w.origin);
                double fwd = delta.dot(w.dir);
                double side = Math.abs(delta.x * w.dir.z - delta.z * w.dir.x);
                if (fwd >= prev - 1.0 && fwd <= w.travelled + 1.0 && side <= w.width * 0.5 + target.getBbWidth() * 0.5) {
                    if (w.struck.add(target.getUUID()) && SkillController.INSTANCE.sight(to.add(0, 1.0, 0), target.getBoundingBox().getCenter())) {
                        MageClient.INSTANCE.issue(w.cast, target, coeff, elem, "", "ice_wave");
                    }
                    if (canDisplace(target)) {
                        queueMotion(target, target.position().add(w.dir.scale(1.6)));
                    }
                }
            }
            if (w.travelled >= w.range - 0.01) {
                MageClient.INSTANCE.visual(w.cast, w.cast.id, "FINISH", to, w.dir, 400L, ++w.sequence, elemMotion(elem, 1));
                CareerClient.INSTANCE.ended(w.cast);
                it.remove();
            }
        }
    }

    private static void tickVortices(long now) {
        var p = player();
        for (var it = vortices.iterator(); it.hasNext(); ) {
            var v = it.next();
            if (!MageClient.INSTANCE.valid(v.cast)) { it.remove(); continue; }
            TrainingCombat.touch(v.cast.id);
            if (v.followPlayer && p != null) v.center = p.position();
            int elem = effectiveElem(v.cast);
            while (v.delivered < v.totalHits && now >= v.nextHitAt) {
                double coeff = v.cast.parameters.get("damage", 1.0);
                for (var target : targetsInCylinder(v.center, v.radius, 3.2)) {
                    MageClient.INSTANCE.issue(v.cast, target, coeff, elem, "", "vortex/" + v.delivered);
                }
                v.delivered++;
                v.nextHitAt += v.intervalMs;
                if (v.followPlayer) {
                    // 护体雷环 refreshes Light lamp
                    var view = CareerClient.INSTANCE.view;
                    view.elementalist = ElementalistRules.onSkillReleased(view.elementalist, v.cast.skill,
                        state().progress(), state().stats(), state().serverNow());
                }
            }
            if (v.delivered >= v.totalHits) {
                MageClient.INSTANCE.visual(v.cast, v.cast.id, "FINISH", v.center, Vec3.ZERO, 300L, ++v.sequence, elemMotion(elem, v.mode));
                CareerClient.INSTANCE.ended(v.cast);
                it.remove();
            }
        }
    }

    private static void tickVoidSpheres(long now) {
        var p = player();
        for (var it = voidSpheres.iterator(); it.hasNext(); ) {
            var vs = it.next();
            if (!MageClient.INSTANCE.valid(vs.cast)) { it.remove(); continue; }
            TrainingCombat.touch(vs.cast.id);
            int elem = effectiveElem(vs.cast);
            if (!vs.stationary && !vs.stopped && p != null) {
                Vec3 nextPos = vs.position.add(vs.dir.scale(vs.speed * 0.05));
                var wall = p.level().clip(new ClipContext(vs.position, nextPos, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
                if (wall.getType() != HitResult.Type.MISS) {
                    vs.position = wall.getLocation().subtract(vs.dir.scale(0.35));
                    vs.stopped = true;
                } else {
                    vs.position = nextPos;
                }
            }
            while (vs.delivered < vs.totalTicks && now >= vs.nextTickAt && now <= vs.endAt + 50L) {
                double coeff = vs.cast.parameters.get("damage", 1.0);
                for (var target : SkillController.INSTANCE.targets(new AABB(vs.position, vs.position).inflate(vs.radius, vs.radius + 1.0, vs.radius))) {
                    if (target.getBoundingBox().getCenter().distanceToSqr(vs.position) <= Math.pow(vs.radius + target.getBbWidth() * 0.5, 2)
                        && SkillController.INSTANCE.sight(vs.position, target.getBoundingBox().getCenter())) {
                        MageClient.INSTANCE.issue(vs.cast, target, coeff, elem, "", "void/" + vs.delivered);
                        if (!vs.stationary && !vs.stopped && vs.pushStrength > 0 && canDisplace(target)) {
                            queueMotion(target, target.position().add(vs.dir.scale(vs.pushStrength)));
                        }
                    }
                }
                vs.delivered++;
                vs.nextTickAt += vs.intervalMs;
            }
            if (now >= vs.endAt) {
                if (vs.mode == 1) {
                    detonateVoidSphere(vs, now);
                } else {
                    MageClient.INSTANCE.visual(vs.cast, vs.cast.id, "FINISH", vs.position, vs.dir, 300L, ++vs.sequence, elemMotion(elem, vs.mode));
                    CareerClient.INSTANCE.ended(vs.cast);
                }
                it.remove();
            }
        }
    }

    private static void detonateVoidSphere(VoidSphereState vs, long now) {
        int elem = effectiveElem(vs.cast);
        int remaining = Math.max(0, vs.totalTicks - vs.delivered);
        double foldIn = remaining * vs.cast.parameters.get("damage", 0.0) * vs.earlyKeepRatio;
        double fin = vs.cast.parameters.get("finisher", 0.0) + foldIn;
        if (fin > 0) {
            for (var target : SkillController.INSTANCE.targets(new AABB(vs.position, vs.position).inflate(vs.blastRadius))) {
                if (target.getBoundingBox().getCenter().distanceToSqr(vs.position) <= vs.blastRadius * vs.blastRadius
                    && SkillController.INSTANCE.sight(vs.position, target.getBoundingBox().getCenter())) {
                    MageClient.INSTANCE.issue(vs.cast, target, fin, elem, "", "void_collapse");
                }
            }
        }
        var motion = elemMotion(elem, vs.mode);
        motion.addProperty("radius", vs.blastRadius);
        MageClient.INSTANCE.visual(vs.cast, vs.cast.id, "FINISH", vs.position, vs.dir, 600L, ++vs.sequence, motion);
        CareerClient.INSTANCE.ended(vs.cast);
    }

    private static void tickFlamePillars(long now) {
        for (var it = flamePillars.iterator(); it.hasNext(); ) {
            var fp = it.next();
            if (!MageClient.INSTANCE.valid(fp.cast)) { it.remove(); continue; }
            TrainingCombat.touch(fp.cast.id);
            int elem = effectiveElem(fp.cast);
            while (fp.nextPillar < fp.centers.size()) {
                long eruptAt = fp.start + fp.impactDelayMs + fp.nextPillar * 180L;
                if (now < eruptAt) break;
                Vec3 c = fp.centers.get(fp.nextPillar);
                double coeff = fp.cast.parameters.get("damage", 0.80);
                double ctrl = fp.mode == 1 ? 0.0 : fp.controlSeconds;
                for (var target : targetsInCylinder(c, fp.radius, 4.0)) {
                    MageClient.INSTANCE.issueElementalist(fp.cast, target, coeff, elem, ctrl, "", "pillar/" + fp.nextPillar);
                }
                var motion = elemMotion(elem, fp.mode);
                motion.addProperty("radius", fp.radius);
                motion.addProperty("pillarIndex", fp.nextPillar);
                MageClient.INSTANCE.visual(fp.cast, fp.cast.id, "HIT", c, Vec3.ZERO, fp.visualDuration, ++fp.sequence, motion);
                fp.nextPillar++;
            }
            if (fp.nextPillar >= fp.centers.size()) {
                CareerClient.INSTANCE.ended(fp.cast);
                it.remove();
            }
        }
    }

    private static void tickBusterMeteors(long now) {
        for (var it = busterMeteors.iterator(); it.hasNext(); ) {
            var m = it.next();
            if (!MageClient.INSTANCE.valid(m.cast)) { it.remove(); continue; }
            TrainingCombat.touch(m.cast.id);
            int elem = effectiveElem(m.cast);
            if (!m.spawned && now >= m.spawnAt) {
                m.spawned = true;
                var motion = elemMotion(elem, m.mode);
                motion.addProperty("radius", m.radius);
                motion.addProperty("shockwaveRadius", m.shockwaveRadius);
                motion.addProperty("fallMs", Math.max(100L, m.impactAt - m.spawnAt));
                motion.addProperty("meteorIndex", m.meteorIndex);
                MageClient.INSTANCE.visual(m.cast, subVisualId(m.cast.id, "m/" + m.meteorIndex), "START", m.center, Vec3.ZERO,
                    (m.impactAt - m.spawnAt) + Math.max(0L, m.emberEndAt - m.impactAt) + 700L, 0, motion);
            }
            if (!m.impacted && now >= m.impactAt) {
                m.impacted = true;
                double coeff = m.cast.parameters.get("damage", 0.70);
                for (var target : targetsInCylinder(m.center, m.radius, 4.5)) {
                    MageClient.INSTANCE.issue(m.cast, target, coeff, elem, "", "buster_core/" + m.meteorIndex);
                }
                var motion = elemMotion(elem, m.mode);
                motion.addProperty("radius", m.radius);
                motion.addProperty("shockwaveRadius", m.shockwaveRadius);
                MageClient.INSTANCE.visual(m.cast, m.meteorIndex == 0 ? m.cast.id : subVisualId(m.cast.id, "m/" + m.meteorIndex),
                    "HIT", m.center, Vec3.ZERO, Math.max(700L, m.emberEndAt - now + 400L), ++m.sequence, motion);
            }
            if (m.impacted && !m.shockwaved && now >= m.shockwaveAt) {
                m.shockwaved = true;
                double fin = m.cast.parameters.get("finisher", 0.30);
                if (fin > 0) {
                    for (var target : targetsInCylinder(m.center, m.shockwaveRadius, 4.0)) {
                        MageClient.INSTANCE.issue(m.cast, target, fin, elem, "", "buster_wave/" + m.meteorIndex);
                    }
                }
            }
            if (m.impacted && m.emberEndAt > 0 && m.emberDot > 0) {
                while (now >= m.nextEmberAt && m.nextEmberAt <= m.emberEndAt + 20L) {
                    for (var target : targetsInCylinder(m.center, m.radius, 2.5)) {
                        MageClient.INSTANCE.issue(m.cast, target, m.emberDot, elem, "ember", "buster_ember/" + m.emberTick);
                    }
                    m.emberTick++;
                    m.nextEmberAt += m.emberIntervalMs;
                }
            }
            boolean done = m.impacted && m.shockwaved && (m.emberEndAt <= 0 || now >= m.emberEndAt);
            if (done) {
                it.remove();
                boolean anyRemaining = busterMeteors.stream().anyMatch(other -> other.cast.id.equals(m.cast.id));
                if (!anyRemaining) CareerClient.INSTANCE.ended(m.cast);
            }
        }
    }

    private static void tickArctics(long now) {
        for (var it = arctics.iterator(); it.hasNext(); ) {
            var a = it.next();
            if (!MageClient.INSTANCE.valid(a.cast)) { it.remove(); continue; }
            TrainingCombat.touch(a.cast.id);
            int elem = effectiveElem(a.cast);
            long visualDuration = a.finisherAt - a.start + ElementalistVisuals.ARCTIC_FINISH_MS;
            if (a.mode == 1) {
                // 冰封王座·瞬碎: giant spire pierce at 0.20s + shatter at 0.45s
                if (!a.pierced && now >= a.nextWaveAt) {
                    a.pierced = true;
                    double pierceCoeff = a.cast.parameters.get("damage", 0.09375) * 4.0;
                    for (var target : targetsInCylinder(a.center, a.radius, 5.0)) {
                        MageClient.INSTANCE.issue(a.cast, target, pierceCoeff, elem, "", "arctic_spire");
                    }
                    MageClient.INSTANCE.visual(a.cast, a.cast.id, "HIT", a.center, Vec3.ZERO, visualDuration, ++a.sequence, elemMotion(elem, 1));
                }
                if (now >= a.finisherAt) {
                    double finCoeff = a.cast.parameters.get("finisher", 0.25) + a.cast.parameters.get("damage", 0.09375) * 4.0;
                    for (var target : targetsInCylinder(a.center, a.radius, 5.0)) {
                        MageClient.INSTANCE.issueElementalist(a.cast, target, finCoeff, elem, a.freezeSeconds, "", "arctic_shatter");
                    }
                    MageClient.INSTANCE.visual(a.cast, a.cast.id, "FINISH", a.center, Vec3.ZERO, visualDuration, ++a.sequence, elemMotion(elem, 1));
                    CareerClient.INSTANCE.ended(a.cast);
                    it.remove();
                }
                continue;
            }
            if (a.pullRadius > 0 && a.pullStrength > 0) {
                pullToward(a.center, a.pullRadius, a.pullStrength * 0.05);
            }
            while (a.delivered < a.totalWaves && now >= a.nextWaveAt) {
                double coeff = a.cast.parameters.get("damage", 0.09375);
                for (var target : targetsInCylinder(a.center, a.radius, 4.5)) {
                    MageClient.INSTANCE.issue(a.cast, target, coeff, elem, "", "arctic/" + a.delivered);
                }
                a.delivered++;
                a.nextWaveAt += a.intervalMs;
            }
            if (now >= a.finisherAt) {
                double fin = a.cast.parameters.get("finisher", 0.25);
                if (fin > 0) {
                    for (var target : targetsInCylinder(a.center, a.radius, 4.5)) {
                        MageClient.INSTANCE.issueElementalist(a.cast, target, fin, elem, a.freezeSeconds, "", "arctic_finisher");
                    }
                }
                var motion = elemMotion(elem, a.mode);
                motion.addProperty("radius", a.radius);
                MageClient.INSTANCE.visual(a.cast, a.cast.id, "FINISH", a.center, Vec3.ZERO, visualDuration, ++a.sequence, motion);
                CareerClient.INSTANCE.ended(a.cast);
                it.remove();
            }
        }
    }

    private static void tickBlackHoles(long now) {
        for (var it = blackHoles.iterator(); it.hasNext(); ) {
            var bh = it.next();
            if (!MageClient.INSTANCE.valid(bh.cast)) { it.remove(); continue; }
            TrainingCombat.touch(bh.cast.id);
            if (bh.detonated) {
                // Lingering gravity sub-hole (Morph A)
                if (now < bh.subHoleEndAt) {
                    pullToward(bh.center, bh.pullRadius * 0.75, bh.pullStrength * 0.04);
                } else {
                    CareerClient.INSTANCE.ended(bh.cast);
                    it.remove();
                }
                continue;
            }
            int elem = effectiveElem(bh.cast);
            pullToward(bh.center, bh.pullRadius, bh.pullStrength * 0.05);
            while (bh.delivered < bh.totalTicks && now >= bh.nextTickAt && now <= bh.endAt + 30L) {
                double coeff = bh.cast.parameters.get("damage", 0.06);
                for (var target : targetsInSphere(bh.center, bh.radius)) {
                    MageClient.INSTANCE.issue(bh.cast, target, coeff, elem, "", "bh/" + bh.delivered);
                }
                bh.delivered++;
                bh.nextTickAt += bh.intervalMs;
            }
            if (bh.volcanoDot > 0 && now >= bh.nextVolcanoAt && now <= bh.endAt + 30L) {
                int fireElem = bh.cast.parameters.get("dyeEnabled", 0.0) >= 1.0 ? elem : 1;
                for (var target : targetsInSphere(bh.center, bh.radius + 1.0)) {
                    MageClient.INSTANCE.issue(bh.cast, target, bh.volcanoDot, fireElem, "volcano", "bh_volcano/" + bh.volcanoTick);
                }
                bh.volcanoTick++;
                bh.nextVolcanoAt += bh.intervalMs;
            }
            if (now >= bh.endAt) {
                detonateBlackHole(bh, now, false);
                if (bh.subHoleEndAt <= now) {
                    CareerClient.INSTANCE.ended(bh.cast);
                    it.remove();
                }
            }
        }
    }

    private static void detonateBlackHole(BlackHoleState bh, long now, boolean manual) {
        if (bh.detonated) return;
        bh.detonated = true;
        int elem = effectiveElem(bh.cast);
        int remaining = Math.max(0, bh.totalTicks - bh.delivered);
        double foldIn = manual ? remaining * bh.cast.parameters.get("damage", 0.06) * bh.earlyKeepRatio : 0.0;
        double fin = bh.cast.parameters.get("finisher", 0.40) + foldIn;
        if (fin > 0) {
            for (var target : targetsInSphere(bh.center, bh.blastRadius)) {
                MageClient.INSTANCE.issue(bh.cast, target, fin, elem, "", "bh_collapse");
            }
        }
        long subMs = Math.max(0L, Math.round(bh.cast.parameters.get("subHoleDuration", 0.0) * 1000.0));
        bh.subHoleEndAt = subMs > 0 ? now + subMs : 0L;
        var motion = elemMotion(elem, bh.mode);
        motion.addProperty("blastRadius", bh.blastRadius);
        motion.addProperty("subHoleMs", subMs);
        MageClient.INSTANCE.visual(bh.cast, bh.cast.id, "FINISH", bh.center, Vec3.ZERO, Math.max(700L, subMs + 400L), ++bh.sequence, motion);
        if (manual && subMs <= 0) {
            blackHoles.remove(bh);
            CareerClient.INSTANCE.ended(bh.cast);
        }
    }

    private static void tickLightningChannel(long now) {
        var ch = lightningChannel;
        if (ch == null) return;
        var p = player();
        var hand = p == null ? null : EquipmentItems.view(p.getMainHandItem());
        if (p == null || !MageClient.INSTANCE.valid(ch.cast) || CombatContentTypes.controlled(p)
            || SealBossState.isFrozen() || hand == null || !hand.instance().toString().equals(ch.cast.weapon)) {
            cancelChannel();
            return;
        }
        TrainingCombat.touch(ch.cast.id);
        double dt = Math.clamp((now - ch.lastTick) / 1000.0, 0.0, 0.2);
        ch.lastTick = now;

        // Crosshair-following golden reticle (max cursorSpeed blocks/s, soft snap within 3.5 blocks)
        Vec3 targetAim = aimGround(p, ch.range, 7.0);
        LivingEntity snapTarget = findNearestEnemy(targetAim, 3.5);
        Vec3 goal = snapTarget != null ? snapTarget.position() : targetAim;
        Vec3 delta = goal.subtract(ch.reticle);
        double maxStep = ch.cursorSpeed * dt;
        if (delta.length() > maxStep) {
            ch.reticle = ch.reticle.add(delta.normalize().scale(maxStep));
        } else {
            ch.reticle = goal;
        }

        int elem = effectiveElem(ch.cast);
        while (ch.delivered < ch.totalBolts && now >= ch.nextBoltAt) {
            double coeff = ch.cast.parameters.get("damage", 0.20);
            dropThunderBolt(ch.cast, ch.reticle, ch.radius, coeff, elem, ch.pullOnHit, ch.delivered, 0);
            ch.delivered++;
            ch.nextBoltAt += ch.intervalMs;
        }
        if (ch.delivered >= ch.totalBolts || now >= ch.endAt) {
            lightningChannel = null;
            PlayerPoseChannel.release(ch.cast);
            CareerClient.INSTANCE.ended(ch.cast);
            CareerClient.INSTANCE.publish();
        }
    }

    private static void tickStormClouds(long now) {
        for (var it = stormClouds.iterator(); it.hasNext(); ) {
            var cloud = it.next();
            if (!MageClient.INSTANCE.valid(cloud.cast)) { it.remove(); continue; }
            TrainingCombat.touch(cloud.cast.id);
            var target = findNearestEnemy(cloud.pos, 10.0);
            if (target != null) {
                Vec3 delta = target.position().subtract(cloud.pos);
                double step = 0.45;
                cloud.pos = delta.length() > step ? cloud.pos.add(delta.normalize().scale(step)) : target.position();
            }
            int elem = effectiveElem(cloud.cast);
            while (cloud.delivered < cloud.totalBolts && now >= cloud.nextBoltAt && now <= cloud.endAt + 50L) {
                double coeff = cloud.cast.parameters.get("damage", 0.20);
                dropThunderBolt(cloud.cast, cloud.pos, cloud.radius, coeff, elem, cloud.pullOnHit, cloud.delivered, 2);
                cloud.delivered++;
                cloud.nextBoltAt += cloud.intervalMs;
            }
            if (cloud.delivered >= cloud.totalBolts || now >= cloud.endAt) {
                MageClient.INSTANCE.visual(cloud.cast, cloud.cast.id, "FINISH", cloud.pos.add(0, 5.5, 0), Vec3.ZERO, 400L, ++cloud.sequence, elemMotion(elem, 2));
                CareerClient.INSTANCE.ended(cloud.cast);
                it.remove();
            }
        }
    }

    private static void dropThunderBolt(SkillCast c, Vec3 point, double radius, double coeff,
                                        int elem, double pullOnHit, int index, int mode) {
        for (var target : targetsInCylinder(point, radius, 6.0)) {
            MageClient.INSTANCE.issue(c, target, coeff, elem, "", "thunder/" + index);
            if (pullOnHit > 0 && canDisplace(target)) {
                Vec3 toCenter = point.subtract(target.position());
                double horiz = Math.hypot(toCenter.x, toCenter.z);
                if (horiz > 0.4) {
                    double step = Math.min(pullOnHit, horiz - 0.3);
                    Vec3 dest = target.position().add(new Vec3(toCenter.x / horiz, 0, toCenter.z / horiz).scale(step));
                    sendImmediateMotion(target, dest);
                }
            }
        }
        var motion = elemMotion(elem, mode);
        motion.addProperty("radius", radius);
        motion.addProperty("boltIndex", index);
        MageClient.INSTANCE.visual(c, subVisualId(c.id, "bolt/" + index), "HIT", point, Vec3.ZERO, 650L, 0, motion);
    }

    private static void tickAstralStorms(long now) {
        if (astralStorm != null) {
            tickSingleAstralStorm(astralStorm, now);
            if (astralStorm != null && astralStorm.finished) {
                finishAstralStorm(astralStorm, false);
            }
        }
        for (var it = detachedStorms.iterator(); it.hasNext(); ) {
            var st = it.next();
            tickSingleAstralStorm(st, now);
            if (st.finished) {
                CareerClient.INSTANCE.ended(st.cast);
                it.remove();
            }
        }
    }

    private static void tickSingleAstralStorm(AstralStormState st, long now) {
        var p = player();
        if (p == null || !MageClient.INSTANCE.valid(st.cast)) {
            st.finished = true;
            return;
        }
        TrainingCombat.touch(st.cast.id);
        if (st.channeling) {
            double riseU = Math.clamp((now - st.start) / 300.0, 0.0, 1.0);
            Vec3 desired = st.groundOrigin.lerp(st.hoverTarget, riseU);
            Vec3 before = p.position();
            p.move(MoverType.SELF, desired.subtract(before));
            p.setNoGravity(true);
            p.setDeltaMovement(Vec3.ZERO);
            p.fallDistance = 0;
        }

        int dyed = ElementalistRules.effectiveElement(st.cast.parameters, st.cast.stats, -1);
        long visualDuration = st.endAt - st.start + ElementalistVisuals.ASTRAL_FINISH_MS;
        while (st.delivered < st.totalMeteors && now >= st.nextMeteorAt) {
            int elem = dyed >= 0 ? dyed : ElementalistRules.sampleMeteorElement(st.cast.stats, RNG);
            Vec3 dropPoint = pickMeteorPoint(st);
            int idx = st.delivered;
            st.meteors.add(new PendingMeteor(dropPoint, elem, now + ASTRAL_METEOR_FALL_MS, idx));
            MageClient.INSTANCE.visual(st.cast, st.cast.id, "HIT", dropPoint, Vec3.ZERO, visualDuration, ++st.sequence, elemMotion(elem, st.mode));
            st.delivered++;
            st.nextMeteorAt += st.intervalMs;
        }

        for (var mit = st.meteors.iterator(); mit.hasNext(); ) {
            var m = mit.next();
            if (now >= m.impactAt()) {
                double coeff = st.cast.parameters.get("damage", 0.040625);
                for (var target : targetsInCylinder(m.point(), st.meteorRadius, 5.0)) {
                    MageClient.INSTANCE.issue(st.cast, target, coeff, m.element(), "", "meteor/" + m.index());
                }
                mit.remove();
            }
        }

        if (now >= st.endAt) {
            double fin = st.cast.parameters.get("finisher", 0.35);
            if (fin > 0) {
                int[] finElems = dyed >= 0 ? new int[]{dyed} : new int[]{0, 1, 2, 3};
                for (var target : targetsInCylinder(st.center, st.finisherRadius, 6.0)) {
                    MageClient.INSTANCE.issueMulti(st.cast, target, fin, finElems, 0.0, "", "astral_core");
                }
            }
            var motion = elemMotion(dyed >= 0 ? dyed : -1, st.mode);
            motion.addProperty("finisherRadius", st.finisherRadius);
            MageClient.INSTANCE.visual(st.cast, st.cast.id, "FINISH", st.center, Vec3.ZERO, visualDuration, ++st.sequence, motion);
            st.finished = true;
        }
    }

    private static void finishAstralStorm(AstralStormState st, boolean cancelled) {
        if (st == astralStorm) {
            astralStorm = null;
            SkillController.INSTANCE.releaseStatusOwner(st.cast.id);
            var p = player();
            if (p != null) {
                p.setDeltaMovement(Vec3.ZERO);
                p.fallDistance = 0;
            }
            try { Effects.VFX.completeCamera(UUID.fromString(st.cast.id)); } catch (Exception ignored) {}
            if (cancelled) PlayerPoseChannel.cancel(st.cast);
            else PlayerPoseChannel.release(st.cast);
            CareerClient.INSTANCE.ended(st.cast);
            CareerClient.INSTANCE.publish();
        }
    }

    private static Vec3 pickMeteorPoint(AstralStormState st) {
        var enemies = targetsInCylinder(st.center, st.fieldRadius, 6.0);
        if (!enemies.isEmpty() && RNG.nextDouble() < st.homingBias) {
            var chosen = enemies.get(RNG.nextInt(enemies.size()));
            double jitterAngle = RNG.nextDouble() * Math.PI * 2.0;
            double jitterR = RNG.nextDouble() * 1.2;
            return chosen.position().add(Math.cos(jitterAngle) * jitterR, 0, Math.sin(jitterAngle) * jitterR);
        }
        double angle = RNG.nextDouble() * Math.PI * 2.0;
        double r = Math.sqrt(RNG.nextDouble()) * Math.max(1.0, st.fieldRadius - 1.0);
        return st.center.add(Math.cos(angle) * r, 0, Math.sin(angle) * r);
    }

    // --- Geometry, displacement & cleanup helpers ---

    private static String subVisualId(String base, String suffix) {
        return UUID.nameUUIDFromBytes((base + "/" + suffix).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
    }

    private static int effectiveElem(SkillCast c) {
        return ElementalistRules.effectiveElement(c.parameters, c.stats, ElementalistSkills.innateElement(c.skill.id));
    }

    private static JsonObject elemMotion(int element, int morphMode) {
        var motion = new JsonObject();
        motion.addProperty("element", element);
        motion.addProperty("color", ElementalistRules.elementColor(element));
        motion.addProperty("morphMode", morphMode);
        return motion;
    }

    private static Vec3 horizontal(Vec3 v) {
        Vec3 h = new Vec3(v.x, 0, v.z);
        return h.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : h.normalize();
    }

    public static Vec3 aimGround(LocalPlayer p, double range, double fallbackDist) {
        try {
            return SkillTargeting.ground(p, range, SkillController::canTarget).point();
        } catch (IllegalArgumentException ignored) {
            Vec3 fallback = p.position().add(horizontal(p.getLookAngle()).scale(Math.min(range, fallbackDist)));
            return snapGround(p, fallback);
        }
    }

    private static Vec3 snapGround(LocalPlayer p, Vec3 pt) {
        var hit = p.level().clip(new ClipContext(pt.add(0, 2.5, 0), pt.add(0, -10.0, 0),
            ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        return hit.getType() == HitResult.Type.MISS ? pt : hit.getLocation().add(0, 0.01, 0);
    }

    private static List<LivingEntity> targetsInCylinder(Vec3 center, double radius, double halfHeight) {
        var out = new ArrayList<LivingEntity>();
        double rSq = radius * radius;
        Vec3 eye = center.add(0, 1.0, 0);
        for (var e : SkillController.INSTANCE.targets(new AABB(center, center).inflate(radius + 0.5, halfHeight, radius + 0.5))) {
            Vec3 c = e.getBoundingBox().getCenter();
            double dx = c.x - center.x, dz = c.z - center.z;
            double allow = radius + e.getBbWidth() * 0.5;
            if (dx * dx + dz * dz <= allow * allow && Math.abs(c.y - center.y) <= halfHeight + e.getBbHeight() * 0.5
                && SkillController.INSTANCE.sight(eye, c)) {
                out.add(e);
            }
        }
        return out;
    }

    private static List<LivingEntity> targetsInSphere(Vec3 center, double radius) {
        var out = new ArrayList<LivingEntity>();
        for (var e : SkillController.INSTANCE.targets(new AABB(center, center).inflate(radius + 0.8))) {
            Vec3 c = e.getBoundingBox().getCenter();
            double allow = radius + e.getBbWidth() * 0.5;
            if (c.distanceToSqr(center) <= allow * allow && SkillController.INSTANCE.sight(center, c)) {
                out.add(e);
            }
        }
        return out;
    }

    private static LivingEntity findNearestEnemy(Vec3 center, double radius) {
        LivingEntity best = null;
        double bestSq = radius * radius;
        for (var e : SkillController.INSTANCE.targets(new AABB(center, center).inflate(radius, 4.0, radius))) {
            double sq = e.position().subtract(center).horizontalDistanceSqr();
            if (sq <= bestSq) {
                bestSq = sq;
                best = e;
            }
        }
        return best;
    }

    private static boolean canDisplace(LivingEntity target) {
        var known = state().context(target);
        if (known != null && "BOSS".equals(known.kind)) return false;
        return target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) < 1.0;
    }

    private static void pullToward(Vec3 center, double pullRadius, double step) {
        if (step <= 0.0 || pullRadius <= 0.0) return;
        for (var target : SkillController.INSTANCE.targets(new AABB(center, center).inflate(pullRadius, 4.0, pullRadius))) {
            if (!canDisplace(target)) continue;
            Vec3 delta = new Vec3(center.x - target.getX(), 0, center.z - target.getZ());
            double dist = delta.length();
            if (dist > 0.6 && dist <= pullRadius) {
                double move = Math.min(step, dist - 0.5);
                queueMotion(target, target.position().add(delta.scale(move / dist)));
            }
        }
    }

    private static void queueMotion(LivingEntity target, Vec3 destination) {
        pendingMotions.put(target.getUUID(), destination);
    }

    private static void sendImmediateMotion(LivingEntity target, Vec3 destination) {
        pendingMotions.put(target.getUUID(), destination);
        flushMotions(ClientUi.now() + MOTION_SYNC_INTERVAL_MS);
    }

    private static void flushMotions(long now) {
        if (pendingMotions.isEmpty()) return;
        if (now - lastMotionSendAt < MOTION_SYNC_INTERVAL_MS) return;
        lastMotionSendAt = ClientUi.now();
        var moves = new JsonArray();
        for (var entry : pendingMotions.entrySet()) {
            var m = new JsonObject();
            m.addProperty("target", entry.getKey().toString());
            m.addProperty("x", entry.getValue().x);
            m.addProperty("y", entry.getValue().y);
            m.addProperty("z", entry.getValue().z);
            moves.add(m);
        }
        pendingMotions.clear();
        if (moves.isEmpty()) return;
        var packet = new JsonObject();
        packet.addProperty("session", state().session());
        packet.addProperty("generation", state().own().generation);
        packet.add("moves", moves);
        state().send("careerMotion", packet);
    }

    public static void cancel(String id) {
        if (activeIceWall != null && activeIceWall.cast.id.equals(id)) activeIceWall = null;
        iceWaves.removeIf(w -> w.cast.id.equals(id));
        vortices.removeIf(v -> v.cast.id.equals(id));
        voidSpheres.removeIf(v -> v.cast.id.equals(id));
        flamePillars.removeIf(f -> f.cast.id.equals(id));
        busterMeteors.removeIf(m -> m.cast.id.equals(id));
        arctics.removeIf(a -> a.cast.id.equals(id));
        blackHoles.removeIf(b -> b.cast.id.equals(id));
        stormClouds.removeIf(s -> s.cast.id.equals(id));
        detachedStorms.removeIf(s -> s.cast.id.equals(id));
        if (lightningChannel != null && lightningChannel.cast.id.equals(id)) cancelChannel();
        if (astralStorm != null && astralStorm.cast.id.equals(id)) finishAstralStorm(astralStorm, true);
    }

    public static void clear() {
        if (astralStorm != null) finishAstralStorm(astralStorm, true);
        if (lightningChannel != null) {
            PlayerPoseChannel.cancel(lightningChannel.cast);
            lightningChannel = null;
        }
        activeIceWall = null;
        iceWaves.clear();
        vortices.clear();
        voidSpheres.clear();
        flamePillars.clear();
        busterMeteors.clear();
        arctics.clear();
        blackHoles.clear();
        stormClouds.clear();
        detachedStorms.clear();
        chargePools.clear();
        pendingMotions.clear();
    }
}
