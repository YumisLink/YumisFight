package cn.yumis.fight.modules.skills.client;

import cn.yumis.fight.modules.attributes.api.Stat;
import cn.yumis.fight.modules.attributes.api.Stats;
import cn.yumis.fight.modules.character.api.JobCatalog;
import cn.yumis.fight.modules.character.api.Progress;
import cn.yumis.fight.modules.combat.api.CombatContentTypes;
import cn.yumis.fight.modules.dungeons.api.client.SealBossState;
import cn.yumis.fight.modules.equipment.api.EquipmentStats;
import cn.yumis.fight.modules.presentation.api.client.ClientUi;
import cn.yumis.fight.modules.skills.api.*;
import cn.yumis.fight.modules.skills.api.client.SkillController;
import cn.yumis.fight.modules.skills.api.client.SkillsClient;
import cn.yumis.fight.modules.skills.api.client.TrainingCombat;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import java.util.*;

/**
 * Owning-client runtime for Battle Mage:
 * - 0-GCD overlay Chaser Launcher + auto-fire-on-hit + random 5-color Chaser queue & auto-chaser
 * - Martial cancel chain (Dragon Fang / Palm Blast / Full Swing / Punto Wave -> cancel targets)
 * - Niu 3-hit basic combo & Teana golden spear basic attacks
 * - All 9 active skills (incl. morphMode = 0/1/2) & Teana Transformation + 2nd-press Apostle Spear finisher
 */
public final class BattleMageClient {
    private static final long MOTION_SYNC_INTERVAL_MS = 250L;
    private static final long STATE_SYNC_INTERVAL_MS = 250L;

    private static final class ChaserOrb {
        SkillCast cast;
        String visualId;
        int colorType;
        boolean teana;
        double coeff, radius, speed;
        Vec3 pos, dir;
        UUID targetUuid;
        long start, lastTick, expiresAt;
        int sequence;
    }

    private static final class DashState {
        SkillCast cast;
        Vec3 origin, dir, endpoint;
        double distance, width, blastRadius, knockback;
        long start, dashMs, impactAt;
        boolean returnToOrigin, returned, impacted, isDragonStrike, isTeana;
        int mode, dashesLeft, dashIndex, sequence;
        final Set<UUID> pathHit = new HashSet<>();
    }

    private static final class DelayedBlast {
        SkillCast cast;
        Vec3 center, dir;
        double radius, pullRadius, controlSeconds, coeff, waveCoeff;
        int[] elements;
        int color, mode, sequence;
        boolean isPress, isFusion, isFullSwing, isDragonFangSecond;
        long impactAt;
    }

    public static final class PuntoState {
        public SkillCast cast;
        public Vec3 dir;
        public double range, width, finisherRadius, stepSpeed;
        public long start, intervalMs, nextHitAt, endAt;
        public int totalHits, delivered, chaserEvery, mode, sequence;
        public boolean finishing;
    }

    public static final class DragonSpearState {
        public SkillCast cast;
        public Vec3 origin, dir, tipPos;
        public double range, width, blastRadius, holdSeconds;
        public long start, intervalMs, nextBallAt, finishAt, holdUntil;
        public int totalBalls, delivered, mode, sequence;
        public boolean thrusted, detached, finished;
        public final List<UUID> held = new ArrayList<>();
    }

    public static final class TeanaFinisherState {
        public SkillCast cast;
        public Vec3 origin, dir, targetPos;
        public double range, radius;
        public long start, impactAt, endAt;
        public boolean impacted, keepTransform;
        public int mode, sequence;
    }

    private static final class ChargePool {
        int max, available;
        long rechargeMillis, nextRechargeAt;
    }

    private static final List<ChaserOrb> orbs = new ArrayList<>();
    private static final List<DashState> dashes = new ArrayList<>();
    private static final List<DelayedBlast> blasts = new ArrayList<>();
    private static PuntoState activePunto;
    private static DragonSpearState activeSpear;
    private static final List<DragonSpearState> detachedSpears = new ArrayList<>();
    private static TeanaFinisherState activeTeanaFinisher;

    private static final Map<String, ChargePool> chargePools = new HashMap<>();
    private static final Map<UUID, Vec3> pendingMotions = new LinkedHashMap<>();
    private static long lastMotionSendAt = 0L;
    private static long lastStatePublishAt = 0L;
    private static boolean pendingStatePublish = false;

    // Recent martial/basic target lock for 0-GCD Chaser Launcher
    private static UUID recentHitTarget;
    private static long recentHitUntil;

    // Martial cancel window
    private static long cancelWindowUntil;
    private static String cancelSourceSkill = "";
    private static boolean cancelBonusConsumedThisCast;

    // Niu 3-hit basic combo step (0, 1, 2)
    private static int basicStep = 0;
    private static long basicComboResetAt = 0L;

    // Invulnerability window during Teana activation burst
    private static long teanaActivationInvulnUntil = 0L;

    private static final Random RNG = new Random();

    private BattleMageClient() {}

    private static SkillsClient state() { return SkillsClient.current(); }
    private static LocalPlayer player() { return Minecraft.getInstance().player; }
    private static BattleMageState bmState() { return CareerClient.INSTANCE.view.battleMage; }

    public static boolean isBattleMage() {
        return state().ready() && state().progress().hasJob(JobCatalog.BATTLE_MAGE);
    }

    static void installHooks() {
        SkillCastHooks.onRequest(SkillCastHooks.Stage.BEFORE_REQUEST, req -> {
            if (!beforeRequest(req.skill)) req.rejected = true;
        });
        SkillCastHooks.onRequest(SkillCastHooks.Stage.PREPARE_STATS, req -> {
            if (!req.progress.hasJob(JobCatalog.BATTLE_MAGE)) return;
            long now = req.now;
            if (cancelWindowUntil > now && BattleMageRules.isCancelTarget(req.skill.id) && !cancelSourceSkill.equals(req.skill.id)) {
                cancelBonusConsumedThisCast = true;
            } else {
                cancelBonusConsumedThisCast = false;
            }
        });
        SkillCastHooks.onRequest(SkillCastHooks.Stage.RESOLVE_PARAMETERS, req -> {
            if (!req.progress.hasJob(JobCatalog.BATTLE_MAGE)) return;
            if (cancelBonusConsumedThisCast) {
                double bonus = req.parameters.get("cancelBonusMut", 0.05);
                if (bonus > 0) {
                    req.prepared = req.prepared.copy();
                    req.prepared.modifier("final").mut += bonus;
                }
                cancelWindowUntil = 0L;
                cancelSourceSkill = "";
                cancelBonusConsumedThisCast = false;
                if (activePunto != null) {
                    cancelPunto(false);
                }
            }
        });
        SkillCastHooks.onHit(hit -> {
            if (!isBattleMage() || hit.target() == null) return;
            long now = ClientUi.now();
            long wall = state().serverNow();
            var s = bmState();
            var stats = hit.stats();
            var progress = state().progress();

            // Record Battle Groove combo
            BattleMageRules.onHitCombo(s, 1, stats, progress, wall);

            // Track recent hit target for Chaser Launcher lock-on (except when hit itself was Chaser Launcher)
            boolean isChaserHit = hit.source() != null && BattleMageSkills.CHASER_LAUNCHER.equals(hit.source().skill.id);
            if (!isChaserHit) {
                var lp = BattleMageRules.launcherParams(stats, progress);
                long lockMs = Math.max(200L, Math.round(lp.get("lockWindowSeconds", 1.2) * 1000.0));
                recentHitTarget = hit.target().getUUID();
                recentHitUntil = now + lockMs;

                // Auto-fire Chaser on hit (morphMode = 2 or equipment parameter)
                if (lp.get("autoFireOnHit", 0.0) >= 1.0 && s.chaserCount(wall) > 0) {
                    tryFireChaserInternal(true);
                }
            }

            // Base Dragon Fang linkage for Battle Mage: generate chasers & open cancel window
            if (hit.source() != null && MageSkills.FANG.equals(hit.source().skill.id)) {
                int gen = Math.max(1, (int) Math.round(hit.source().parameters.get("chaserGen", 1.0)));
                BattleMageRules.generateChasers(s, gen, stats, progress, wall, RNG);
                openCancelWindow(MageSkills.FANG, hit.source().parameters.get("cancelWindowSeconds", 0.45));
            }
            schedulePublish();
        });
    }

    public static void install() {
        MageClient.register(BattleMageSkills.CHASER_LAUNCHER, BattleMageClient::startChaserLauncherFallback);
        MageClient.registerAction(BattleMageSkills.PALM_BLAST, BattleMageClient::startPalmBlast);
        MageClient.registerAction(BattleMageSkills.FULL_SWING, BattleMageClient::startFullSwing);
        MageClient.register(BattleMageSkills.CHASER_FUSION, BattleMageClient::startChaserFusion);
        MageClient.register(BattleMageSkills.CHASER_PRESS, BattleMageClient::startChaserPress);
        MageClient.registerAction(BattleMageSkills.PUNTO_WAVE, BattleMageClient::startPuntoWave);
        MageClient.registerAction(BattleMageSkills.DRAGON_STRIKE, BattleMageClient::startDragonStrike);
        MageClient.registerAction(BattleMageSkills.DRAGON_SPEAR, BattleMageClient::startDragonSpear);
        MageClient.registerAction(BattleMageSkills.TEANA_TRANSFORMATION, BattleMageClient::startTeana);
    }

    // --- State queries for SkillController / ClientBootstrap / CareerClient ---

    public static boolean invulnerable() {
        long now = ClientUi.now();
        return now < teanaActivationInvulnUntil || activeTeanaFinisher != null;
    }

    public static boolean blocksMovement() {
        return activeTeanaFinisher != null
            || (activeSpear != null && !activeSpear.detached && !activeSpear.finished)
            || (!dashes.isEmpty());
    }

    public static boolean blocksBasic() {
        return activePunto != null || (activeSpear != null && !activeSpear.detached)
            || activeTeanaFinisher != null || !dashes.isEmpty() || BattleMageChargeClient.active();
    }

    public static boolean canCancelActive(SkillDefinition incoming, long now) {
        if (!isBattleMage() || incoming == null) return false;
        if (BattleMageSkills.CHASER_LAUNCHER.equals(incoming.id)) return true;
        if (cancelWindowUntil > now && BattleMageRules.isCancelTarget(incoming.id) && !cancelSourceSkill.equals(incoming.id)) {
            return true;
        }
        return activePunto != null && BattleMageRules.isCancelTarget(incoming.id) && !BattleMageSkills.PUNTO_WAVE.equals(incoming.id);
    }

    public static boolean isTeanaTerminal(String skillId, long wall) {
        return BattleMageSkills.TEANA_TRANSFORMATION.equals(skillId)
            && isBattleMage()
            && bmState().transformed(wall)
            && !bmState().teanaFinisherUsed;
    }

    public static double careerDamageTaken(double normal) {
        double taken = normal;
        if (!isBattleMage()) return taken;
        long wall = state().serverNow();
        var s = bmState();
        if (s.buffUntil != null && s.buffUntil[BattleMageState.ICE] > wall) {
            int lRank = Math.max(1, BattleMageRules.launcherRank(state().stats(), state().progress()));
            var lp = BattleMageRules.launcherParams(state().stats(), state().progress());
            double dr = BattleMageRules.iceDamageReduction(lRank, lp);
            taken *= (1.0 - dr);
        }
        if (!dashes.isEmpty()) {
            for (var d : dashes) {
                if (d.isDragonStrike && d.cast.chargeProgress >= 0.99) {
                    taken *= 0.50;
                    break;
                }
            }
        }
        return taken;
    }

    public static boolean beforeRequest(SkillDefinition skill) {
        if (invulnerable()) return false;
        if (activeSpear != null && !activeSpear.detached && !activeSpear.finished) return false;
        if (BattleMageSkills.CHASER_LAUNCHER.equals(skill.id)) {
            if (bmState().chaserCount(state().serverNow()) <= 0) {
                state().message("当前没有可用的炫纹");
                return false;
            }
        }
        if (BattleMageSkills.CHASER_FUSION.equals(skill.id)) {
            var p = player();
            if (p != null) {
                int rank = Math.max(1, SkillLearning.effective(state().stats(), state().progress(), skill));
                var params = SkillParameters.forCast(p.getUUID(), skill, rank, state().stats().parameters);
                if (bmState().chaserCount(state().serverNow()) <= 0 && params.get("autoGenerateOnEmpty", 0.0) < 1.0) {
                    state().message("炫纹融合需要至少1颗炫纹");
                    return false;
                }
            }
        }
        return true;
    }

    static boolean prepareCast(SkillCast cast, int slot, Progress progress, Runnable release) {
        if (!progress.hasJob(JobCatalog.BATTLE_MAGE)) return false;
        // Base Dragon Fang Morph A/B hook
        if (MageSkills.FANG.equals(cast.skill.id)) {
            int mode = BattleMageRules.morphMode(cast.parameters);
            if (mode == 1) {
                // Schedule rapid 2nd thrust after the 1st base thrust
                var second = new DelayedBlast();
                second.cast = cast;
                second.center = cast.origin.add(0, 0.8, 0);
                second.dir = cast.direction;
                second.radius = cast.parameters.get("range", 5.0);
                second.coeff = cast.parameters.get("damage", 0.0);
                second.isDragonFangSecond = true;
                second.impactAt = ClientUi.now() + Math.max(120L, Math.round(340.0 / cast.speed));
                blasts.add(second);
            }
            return false;
        }
        if (BattleMageRules.isChargeable(cast.skill, progress, cast.parameters)) {
            BattleMageChargeClient.begin(cast, slot, release);
            return true;
        }
        return false;
    }

    /**
     * 0-GCD overlay cast for Chaser Launcher: fires instantly during ANY martial animation,
     * basic attack, or charge without disrupting PlayerPoseChannel or SkillController.cast.
     */
    public static boolean tryOverlayChaserCast(int slot) {
        if (!isBattleMage()) return false;
        var progress = state().progress();
        if (slot < 0 || slot >= progress.slots.length) return false;
        if (!BattleMageSkills.CHASER_LAUNCHER.equals(progress.slots[slot])) return false;
        if (!SkillController.INSTANCE.validWeapon()) return false;
        if (invulnerable()) return true;
        tryFireChaserInternal(false);
        return true;
    }

    private static boolean tryFireChaserInternal(boolean silent) {
        var mc = Minecraft.getInstance();
        var p = mc.player;
        if (p == null || mc.level == null || !state().ready() || state().own().dead || p.isDeadOrDying()
            || CombatContentTypes.controlled(p) || SealBossState.isFrozen()) return false;
        long now = ClientUi.now();
        long wall = state().serverNow();
        var skill = SkillCatalog.require(BattleMageSkills.CHASER_LAUNCHER);
        var progress = state().progress();
        Stats stats = SkillController.INSTANCE.snapshot();
        int rank = SkillLearning.effective(stats, progress, skill);
        if (rank <= 0) return false;
        if (!skill.weaponAllowed(progress, stats)) {
            if (!silent) state().message("主手需要本职业可用武器");
            return false;
        }
        var s = bmState();
        if (s.chaserCount(wall) <= 0) {
            if (!silent) state().message("当前没有可用的炫纹");
            return false;
        }
        var resources = state().resources();
        if (!p.isCreative() && !resources.training && resources.cooldowns.getOrDefault(skill.id, 0L) > now) {
            return false;
        }
        var effects = EquipmentStats.projectileEffects(p, progress, p.getMainHandItem());
        var cost = SkillCosts.resolve(skill, rank, stats, effects, true);
        if (!resources.training && !p.isCreative() && resources.mp < cost.mana()) {
            if (!silent) state().message("MP不足");
            return false;
        }
        var params = WeaponSkills.skill(SkillParameters.forCast(p.getUUID(), skill, rank, stats.parameters), progress, stats);
        // Pay MP & CD directly without cancelling active martial SkillCast
        if (!resources.training && !p.isCreative()) {
            resources.mp = Math.max(0, resources.mp - cost.mana());
            resources.cooldowns.put(skill.id, now + Math.max(50L, Math.round(cost.cooldown() * 1000.0)));
        }
        resources.recordedAt = now;

        int volley = 1 + (int) Math.max(0, Math.round(params.get("extraVolleyCount", 0.0)));
        boolean teanaActive = s.transformed(wall);
        double teanaScale = stats.modifiers.getOrDefault("bmTeanaChaserScale", 1.45);
        double radiusScale = stats.modifiers.getOrDefault("bmChaserRadiusScale", 1.0) * (teanaActive ? 1.35 : 1.0);
        double radius = params.get("radius", 2.4) * radiusScale;
        double speed = Math.max(8.0, params.get("projectileSpeed", 28.0));

        var cast = new SkillCast();
        cast.id = UUID.randomUUID().toString();
        cast.skill = skill;
        cast.parameters = params;
        cast.stats = stats.copy();
        cast.stats.modifiers.put("sourceLevel", (double) progress.level);
        cast.equipment = effects;
        cast.weapon = stats.weaponInstance;
        cast.origin = p.position().add(0, 1.15, 0);
        cast.direction = p.getLookAngle().lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : p.getLookAngle().normalize();
        cast.center = cast.origin;
        cast.start = cast.stageStart = now;
        cast.speed = Math.max(0.01, stats.get(Stat.CAST_SPEED));
        cast.dimension = mc.level.dimension().identifier().toString();
        cast.session = state().session();
        cast.generation = state().own().generation;
        cast.job = progress.jobId();
        cast.instance = state().own().instance;

        LivingEntity lockedTarget = resolveRecentTarget(p, params.get("range", 16.0), now);
        int fired = 0;
        for (int i = 0; i < volley; i++) {
            var chaser = s.pollChaser(wall);
            if (chaser == null) break;
            boolean isTeanaOrb = teanaActive || chaser.teana;
            double coeff = BattleMageRules.chaserCoefficient(params, chaser.type, isTeanaOrb, teanaScale);
            // Apply buff immediately on fire so rapid chasers smoothly empower combos
            BattleMageRules.applyChaserBuff(s, chaser.type, params, wall);

            var orb = new ChaserOrb();
            orb.cast = cast;
            orb.visualId = UUID.randomUUID().toString();
            orb.colorType = chaser.type;
            orb.teana = isTeanaOrb;
            orb.coeff = coeff;
            orb.radius = radius;
            orb.speed = speed;
            double angleOffset = (i - (volley - 1) * 0.5) * 0.45;
            Vec3 side = new Vec3(-cast.direction.z, 0, cast.direction.x);
            if (side.lengthSqr() > 1e-6) side = side.normalize();
            orb.pos = p.position().add(0, 1.25 + (i % 2) * 0.2, 0).add(side.scale(angleOffset));
            orb.dir = cast.direction;
            orb.targetUuid = lockedTarget != null ? lockedTarget.getUUID() : null;
            orb.start = orb.lastTick = now;
            orb.expiresAt = now + Math.max(300L, Math.round(params.get("range", 16.0) / speed * 1000.0) + 200L);
            orbs.add(orb);

            var motion = new JsonObject();
            motion.addProperty("color", BattleMageRules.chaserColor(orb.colorType, orb.teana));
            motion.addProperty("radius", orb.radius);
            motion.addProperty("teana", orb.teana);
            motion.addProperty("chaserType", orb.colorType);
            MageClient.INSTANCE.visual(cast, orb.visualId, "START", orb.pos, orb.dir, orb.expiresAt - now + 350L, 0, motion);
            fired++;
        }
        if (fired > 0) {
            TrainingCombat.begin(cast.id, skill.title);
            schedulePublish();
            return true;
        }
        return false;
    }

    private static void startChaserLauncherFallback(SkillCast cast) {
        // Fallback if invoked via CareerClient directly (e.g. scripted cast)
        tryFireChaserInternal(true);
        MageClient.INSTANCE.finished(cast);
    }

    // --- Niu 3-Hit Basic Attack & Teana Golden Spear Basic Combo ---

    /** Called by SkillController when Battle Mage performs a basic attack swing. Returns true if custom pose was started. */
    public static boolean onBasicSwing(Stats stats, double intervalMillis) {
        if (!isBattleMage()) return false;
        var p = player();
        if (p == null) return false;
        long now = ClientUi.now();
        if (now > basicComboResetAt) basicStep = 0;
        int step = basicStep;
        basicStep = (basicStep + 1) % 3;
        basicComboResetAt = now + 1600L;

        boolean teana = bmState().transformed(state().serverNow());
        String clip = (teana ? "teana_basic_" : "bm_basic_") + (step + 1);
        long dur = Math.max(180L, Math.round(intervalMillis * 0.92));
        Vec3 aim = horizontal(p.getLookAngle());
        PlayerPoseChannel.play(null, clip, dur, aim, false);
        return true;
    }

    /** Called by SkillController after a Battle Mage basic melee/staff hit lands. */
    public static void onBasicStrikeHit(Stats stats, LivingEntity primaryTarget) {
        if (!isBattleMage() || primaryTarget == null) return;
        long wall = state().serverNow();
        var s = bmState();
        var progress = state().progress();
        var gp = BattleMageRules.grooveParams(stats, progress);
        boolean teana = s.transformed(wall);
        // basicStep was already incremented in onBasicSwing: step==0 means we just executed 3rd hit!
        boolean finisherHit = basicStep == 0;
        boolean teanaEvenHit = teana && basicStep == 2;
        double extraChance = gp.get("basicChaserChance", 0.0);
        if (finisherHit || teanaEvenHit || (extraChance > 0 && RNG.nextDouble() < extraChance)) {
            BattleMageRules.generateChasers(s, 1, stats, progress, wall, RNG);
            schedulePublish();
        }
        // Open short cancel window on basic hit so player can seamlessly cancel basic -> Full Swing / Palm Blast
        openCancelWindow("basic", 0.35);
    }

    /** Adjusts Battle Mage basic melee reach (Spear Mastery range scale & Teana 5.2 reach). */
    public static double basicReach(Stats stats, double fallback) {
        if (!isBattleMage()) return fallback;
        double reach = fallback;
        double rangeScale = stats.modifiers.getOrDefault("bmMeleeRangeScale", 1.0);
        reach *= rangeScale;
        if (stats.modifiers.getOrDefault("bmTeana", 0.0) > 0) {
            reach = Math.max(reach, 5.2);
        }
        return reach;
    }

    /** Adjusts Battle Mage basic attack interval during Teana (-25% interval). */
    public static double basicInterval(Stats stats, double baseInterval) {
        if (!isBattleMage()) return baseInterval;
        if (stats.modifiers.getOrDefault("bmTeana", 0.0) > 0) {
            return Math.max(50.0, baseInterval * 0.75);
        }
        return baseInterval;
    }

    // --- Skill 1: Palm Blast (落花掌 / 苍天击) ---

    private static void startPalmBlast(SkillCast c) {
        var p = player();
        if (p == null) { MageClient.INSTANCE.finished(c); return; }
        long now = ClientUi.now();
        long wall = state().serverNow();
        boolean teana = bmState().transformed(wall);
        int mode = BattleMageRules.morphMode(c.parameters);
        double teanaScale = teana ? c.stats.modifiers.getOrDefault("bmTeanaSkillScale", 1.30) : 1.0;

        var d = new DashState();
        d.cast = c;
        d.origin = p.position();
        d.dir = horizontal(p.getLookAngle());
        d.distance = c.parameters.get("dashDistance", 4.5) * (teana ? 1.25 : 1.0);
        d.width = c.parameters.get("radius", 2.6) * (teana ? 1.15 : 1.0);
        d.blastRadius = c.parameters.get("radius", 2.6) * (teana ? 1.25 : 1.0);
        d.knockback = c.parameters.get("knockbackDistance", 3.5);
        d.start = now;
        d.dashMs = Math.max(120L, Math.round(c.parameters.get("dashSeconds", 0.28) * 1000.0 / c.speed));
        d.impactAt = now + d.dashMs;
        d.isTeana = teana;
        d.mode = mode;
        d.dashesLeft = 1;
        dashes.add(d);

        // Apply Teana scale onto cast snapshot for consistent contact calculation
        if (teana && teanaScale != 1.0) {
            var vals = new LinkedHashMap<>(c.parameters.values());
            vals.computeIfPresent("damage", (k, v) -> v * teanaScale);
            c.parameters = new SkillParameters.Snapshot(c.skill, c.parameters.rank(), vals);
        }

        String clip = teana ? "teana_palm_blast" : "palm_blast";
        PlayerPoseChannel.play(c, clip, d.dashMs + 220L, d.dir, false);

        var motion = new JsonObject();
        motion.addProperty("range", d.distance);
        motion.addProperty("radius", d.blastRadius);
        motion.addProperty("teana", teana);
        motion.addProperty("mode", mode);
        motion.addProperty("color", teana ? BattleMageRules.COLOR_TEANA : BattleMageRules.COLOR_FIRE);
        MageClient.INSTANCE.visual(c, c.id, "START", d.origin.add(0, 0.9, 0), d.dir, d.dashMs + 420L, 0, motion);
    }

    // --- Skill 2: Full Swing (碎霸 / 天地碎霸) ---

    private static void startFullSwing(SkillCast c) {
        var p = player();
        if (p == null) { MageClient.INSTANCE.finished(c); return; }
        long now = ClientUi.now();
        long wall = state().serverNow();
        boolean teana = bmState().transformed(wall);
        int mode = BattleMageRules.morphMode(c.parameters);
        double teanaScale = teana ? c.stats.modifiers.getOrDefault("bmTeanaSkillScale", 1.30) : 1.0;
        double rangeScale = c.stats.modifiers.getOrDefault("bmMeleeRangeScale", 1.0);

        Vec3 dir = horizontal(p.getLookAngle());
        double radius = c.parameters.get("radius", 4.5) * rangeScale * (teana ? 1.35 : 1.0);

        // Morph A (闪击碎霸·终极女皇): forward leap-dash 5 blocks before full 360° slam
        if (mode == 1) {
            Vec3 end = advanceClear(p, p.position(), dir, 5.0);
            p.move(MoverType.SELF, end.subtract(p.position()));
        }

        // Morph B (炫纹碎霸·魔刃附魔): consume 1 Chaser to infuse magic crescent wave
        int[] waveElems = new int[0];
        int waveColor = teana ? BattleMageRules.COLOR_TEANA : BattleMageRules.COLOR_MARTIAL;
        double waveCoeff = c.parameters.get("chaserSlash", 0.0);
        if (c.parameters.get("consumeChaserForBoost", 0.0) >= 1.0) {
            var ch = bmState().pollChaser(wall);
            if (ch != null) {
                waveElems = BattleMageRules.chaserElements(ch.type, teana || ch.teana);
                waveColor = BattleMageRules.chaserColor(ch.type, teana || ch.teana);
                BattleMageRules.applyChaserBuff(bmState(), ch.type, BattleMageRules.launcherParams(c.stats, state().progress()), wall);
                schedulePublish();
            } else {
                waveCoeff *= 0.5;
            }
        }

        var b = new DelayedBlast();
        b.cast = c;
        b.center = p.position().add(0, 0.9, 0);
        b.dir = dir;
        b.radius = radius;
        b.coeff = c.parameters.get("damage", 0.0) * teanaScale;
        b.waveCoeff = waveCoeff;
        b.elements = waveElems;
        b.color = waveColor;
        b.mode = mode;
        b.isFullSwing = true;
        b.impactAt = now + Math.max(90L, Math.round(160.0 / c.speed));
        blasts.add(b);

        String clip = mode == 1 ? "full_swing_empress" : (teana ? "teana_full_swing" : "full_swing");
        PlayerPoseChannel.play(c, clip, Math.max(300L, Math.round((mode == 1 ? 560.0 : 420.0) / c.speed)), dir, false);

        var motion = new JsonObject();
        motion.addProperty("radius", radius);
        motion.addProperty("arc", c.parameters.get("arcDegrees", 210.0));
        motion.addProperty("teana", teana);
        motion.addProperty("mode", mode);
        motion.addProperty("color", waveColor);
        MageClient.INSTANCE.visual(c, c.id, "START", b.center, dir, 560L, 0, motion);
    }

    // --- Skill 3: Chaser Fusion (炫纹融合) ---

    private static void startChaserFusion(SkillCast c) {
        var p = player();
        if (p == null) { MageClient.INSTANCE.finished(c); return; }
        long now = ClientUi.now();
        long wall = state().serverNow();
        var s = bmState();
        int mode = BattleMageRules.morphMode(c.parameters);
        boolean teana = s.transformed(wall);

        int want = Math.max(1, (int) Math.round(c.parameters.get("consumeCount", 2.0)));
        var consumed = new ArrayList<BattleMageState.Chaser>();
        for (int i = 0; i < want; i++) {
            var ch = s.pollChaser(wall);
            if (ch != null) consumed.add(ch);
        }
        double ratio = 1.0;
        if (consumed.isEmpty()) {
            ratio = 0.55;
        } else if (consumed.size() < want) {
            ratio = 0.65;
        }
        int[] elems = BattleMageRules.unionElements(consumed, teana);
        int color = teana ? BattleMageRules.COLOR_TEANA
            : (consumed.isEmpty() ? BattleMageRules.COLOR_DARK : BattleMageRules.chaserColor(consumed.getFirst().type, false));

        Vec3 dir = horizontal(p.getLookAngle());
        Vec3 center;
        if (mode == 1) {
            var locked = resolveRecentTarget(p, 14.0, now);
            center = locked != null ? locked.position().add(0, 0.9, 0) : p.position().add(dir.scale(c.parameters.get("centerOffset", 3.2))).add(0, 0.9, 0);
        } else {
            center = p.position().add(dir.scale(c.parameters.get("centerOffset", 3.2))).add(0, 0.9, 0);
        }

        // Apply Fusion Resonance buff
        long buffMs = Math.max(1000L, Math.round(c.parameters.get("fusionBuffDuration", 15.0) * 1000.0));
        s.fusionUntil = wall + buffMs;
        s.fusionAttackBonus = BattleMageRules.fusionAttackBonus(c.parameters.rank(), c.parameters);
        s.fusionCritBonus = c.parameters.get("fusionCritBonus", 0.06);
        s.revision++;
        schedulePublish();

        var b = new DelayedBlast();
        b.cast = c;
        b.center = center;
        b.dir = dir;
        b.radius = c.parameters.get("radius", 4.2) * (teana ? 1.20 : 1.0);
        b.coeff = c.parameters.get("damage", 0.0) * ratio;
        b.elements = elems;
        b.color = color;
        b.mode = mode;
        b.isFusion = true;
        b.impactAt = now + Math.max(60L, Math.round(c.parameters.get("impactDelay", 0.25) * 1000.0 / c.speed));
        blasts.add(b);

        if (mode != 1) {
            PlayerPoseChannel.play(c, "chaser_fusion", 460L, dir, false);
        }
        var motion = new JsonObject();
        motion.addProperty("radius", b.radius);
        motion.addProperty("color", color);
        motion.addProperty("mode", mode);
        motion.addProperty("teana", teana);
        MageClient.INSTANCE.visual(c, c.id, "START", center, dir, 720L, 0, motion);
    }

    // --- Skill 4: Chaser Press (炫纹强压) ---

    private static void startChaserPress(SkillCast c) {
        var p = player();
        if (p == null) { MageClient.INSTANCE.finished(c); return; }
        long now = ClientUi.now();
        long wall = state().serverNow();
        var s = bmState();
        int mode = BattleMageRules.morphMode(c.parameters);
        boolean teana = s.transformed(wall);

        int keepCount = mode == 1 ? 2 : 0;
        var consumed = s.drainChasers(wall, keepCount);
        int count = Math.max(1, consumed.size());
        int[] elems = BattleMageRules.unionElements(consumed, teana);
        int color = teana ? BattleMageRules.COLOR_TEANA
            : (consumed.isEmpty() ? BattleMageRules.COLOR_NEUTRAL : BattleMageRules.chaserColor(consumed.getLast().type, false));
        schedulePublish();

        double coeff = c.parameters.get("damage", 0.35) + c.parameters.get("perChaserDamage", 0.1083) * (count - 1);
        double radius = (c.parameters.get("radius", 3.5) + c.parameters.get("radiusPerChaser", 0.45) * (count - 1)) * (teana ? 1.20 : 1.0);
        Vec3 dir = horizontal(p.getLookAngle());
        Vec3 center = mode == 1
            ? p.position().add(dir.scale(6.5)).add(0, 0.4, 0)
            : p.position().add(dir.scale(2.4)).add(0, 0.2, 0);

        var b = new DelayedBlast();
        b.cast = c;
        b.center = center;
        b.dir = dir;
        b.radius = radius;
        b.pullRadius = c.parameters.get("pullRadius", 0.0);
        b.controlSeconds = c.parameters.get("controlSeconds", 0.80);
        b.coeff = coeff;
        b.elements = elems;
        b.color = color;
        b.mode = mode;
        b.isPress = true;
        b.impactAt = now + Math.max(80L, Math.round(c.parameters.get("impactDelay", 0.30) * 1000.0 / c.speed));
        blasts.add(b);

        PlayerPoseChannel.play(c, mode == 1 ? "chaser_fusion" : "chaser_press", mode == 1 ? 380L : 620L, dir, false);
        var motion = new JsonObject();
        motion.addProperty("radius", radius);
        motion.addProperty("pullRadius", b.pullRadius);
        motion.addProperty("count", count);
        motion.addProperty("color", color);
        motion.addProperty("mode", mode);
        motion.addProperty("teana", teana);
        MageClient.INSTANCE.visual(c, c.id, "START", center, dir, 950L, 0, motion);
    }

    // --- Skill 5: Punto Wave (流星闪影击 / 啦啦啦) ---

    private static void startPuntoWave(SkillCast c) {
        var p = player();
        if (p == null) { MageClient.INSTANCE.finished(c); return; }
        long now = ClientUi.now();
        int mode = BattleMageRules.morphMode(c.parameters);
        double atkSpeed = Math.max(0.4, 1.0 + c.stats.get(Stat.ATTACK_SPEED) / 100.0);
        double rangeScale = c.stats.modifiers.getOrDefault("bmMeleeRangeScale", 1.0);

        var st = new PuntoState();
        st.cast = c;
        st.dir = horizontal(p.getLookAngle());
        st.range = c.parameters.get("range", 5.5) * rangeScale;
        st.width = c.parameters.get("width", 3.2);
        st.finisherRadius = c.parameters.get("finisherRadius", 5.5) * rangeScale;
        st.stepSpeed = c.parameters.get("stepMoveSpeed", 2.0);
        st.totalHits = (int) Math.max(1, Math.round(c.parameters.get("slashes", 12.0)));
        st.intervalMs = Math.max(50L, Math.round(c.parameters.get("interval", 0.09) * 1000.0 / atkSpeed));
        st.chaserEvery = (int) Math.max(1, Math.round(c.parameters.get("chaserEveryHits", 3.0)));
        st.mode = mode;
        st.start = now;
        st.nextHitAt = now + 80L;
        st.endAt = st.nextHitAt + st.totalHits * st.intervalMs + 280L;
        activePunto = st;

        openCancelWindow(BattleMageSkills.PUNTO_WAVE, (st.endAt - now) / 1000.0);
        if (mode == 1) {
            PlayerPoseChannel.play(c, "punto_wave_triple", st.endAt - now, st.dir, false);
        } else {
            PlayerPoseChannel.loop(c, "punto_wave_loop", st.totalHits * st.intervalMs + 100L, st.dir, true);
        }

        var motion = new JsonObject();
        motion.addProperty("range", st.range);
        motion.addProperty("width", st.width);
        motion.addProperty("finisherRadius", st.finisherRadius);
        motion.addProperty("mode", mode);
        motion.addProperty("color", bmState().transformed(state().serverNow()) ? BattleMageRules.COLOR_TEANA : BattleMageRules.COLOR_MARTIAL);
        MageClient.INSTANCE.visual(c, c.id, "START", p.position().add(0, 1.0, 0), st.dir, st.endAt - now + 350L, 0, motion);
    }

    public static boolean cancelPunto(boolean playerAborted) {
        if (activePunto == null) return false;
        var st = activePunto;
        activePunto = null;
        if (playerAborted) {
            PlayerPoseChannel.cancel(st.cast);
        }
        var p = player();
        if (p != null) {
            MageClient.INSTANCE.visual(st.cast, st.cast.id, "CANCEL", p.position().add(0, 1.0, 0), st.dir, 200L, ++st.sequence, null);
        }
        MageClient.INSTANCE.finished(st.cast);
        return true;
    }

    // --- Skill 6: Dragon Strike (强袭流星打) ---

    private static void startDragonStrike(SkillCast c) {
        var p = player();
        if (p == null) { MageClient.INSTANCE.finished(c); return; }
        long now = ClientUi.now();
        int mode = BattleMageRules.morphMode(c.parameters);
        var d = new DashState();
        d.cast = c;
        d.origin = p.position();
        d.dir = horizontal(p.getLookAngle());
        d.distance = c.parameters.get("dashDistance", 9.0);
        d.width = c.parameters.get("width", 3.5);
        d.blastRadius = c.parameters.get("blastRadius", 5.0);
        d.returnToOrigin = c.parameters.get("returnToOrigin", 1.0) >= 1.0;
        d.isDragonStrike = true;
        d.mode = mode;
        d.dashesLeft = Math.max(1, (int) Math.round(c.parameters.get("dashCount", 1.0)));
        d.start = now;
        d.dashMs = Math.max(140L, Math.round(c.parameters.get("dashSeconds", 0.30) * 1000.0 / c.speed));
        d.impactAt = now + d.dashMs + 90L;
        dashes.add(d);

        PlayerPoseChannel.play(c, "dragon_strike", d.dashMs + 340L, d.dir, false);
        var motion = new JsonObject();
        motion.addProperty("range", d.distance);
        motion.addProperty("width", d.width);
        motion.addProperty("blastRadius", d.blastRadius);
        motion.addProperty("mode", mode);
        motion.addProperty("color", 0xFF5A36);
        MageClient.INSTANCE.visual(c, c.id, "START", d.origin.add(0, 1.0, 0), d.dir, d.dashMs + 650L, 0, motion);
    }

    // --- Skill 7: Dragon Spear (煌龙偃月) ---

    private static void startDragonSpear(SkillCast c) {
        var p = player();
        if (p == null) { MageClient.INSTANCE.finished(c); return; }
        long now = ClientUi.now();
        int mode = BattleMageRules.morphMode(c.parameters);
        double atkSpeed = Math.max(0.5, 1.0 + c.stats.get(Stat.ATTACK_SPEED) / 100.0);

        var st = new DragonSpearState();
        st.cast = c;
        st.origin = p.position();
        st.dir = horizontal(p.getLookAngle());
        st.range = c.parameters.get("range", 7.5);
        st.width = c.parameters.get("width", 3.2);
        st.blastRadius = c.parameters.get("blastRadius", 6.5);
        st.holdSeconds = c.parameters.get("holdSeconds", 1.4);
        st.tipPos = st.origin.add(st.dir.scale(Math.min(4.8, st.range * 0.65)));
        st.totalBalls = mode == 1 ? 0 : (int) Math.max(1, Math.round(c.parameters.get("dragonChaserCount", 8.0)));
        st.intervalMs = Math.max(50L, Math.round(c.parameters.get("interval", 0.14) * 1000.0 / atkSpeed));
        st.mode = mode;
        st.start = now;
        st.nextBallAt = now + (mode == 1 ? 260L : 340L);
        st.finishAt = mode == 1
            ? now + Math.max(280L, Math.round(420.0 / c.speed))
            : Math.max(now + 650L, st.nextBallAt + st.totalBalls * st.intervalMs + 180L);
        st.holdUntil = now + Math.round(st.holdSeconds * 1000.0);

        if (mode == 2) {
            // Morph B: after 400ms thrust, detaches dragon phantom so player is free to move
            st.detached = false;
            detachedSpears.add(st);
            PlayerPoseChannel.play(c, "dragon_spear_thrust", 460L, st.dir, false);
        } else {
            activeSpear = st;
            String clip = mode == 1 ? "dragon_spear_cleave" : "dragon_spear";
            PlayerPoseChannel.play(c, clip, st.finishAt - now + 220L, st.dir, false);
        }

        var motion = new JsonObject();
        motion.addProperty("range", st.range);
        motion.addProperty("blastRadius", st.blastRadius);
        motion.addProperty("balls", st.totalBalls);
        motion.addProperty("mode", mode);
        motion.addProperty("color", 0xFFB833);
        MageClient.INSTANCE.visual(c, c.id, "START", st.origin.add(0, 1.0, 0), st.dir, st.finishAt - now + 550L, 0, motion);
    }

    // --- Skill 8: Teana Transformation (变身：贝亚娜 + 2nd-Press Apostle Spear Finisher) ---

    private static void startTeana(SkillCast c) {
        var p = player();
        if (p == null) { MageClient.INSTANCE.finished(c); return; }
        long now = ClientUi.now();
        long wall = state().serverNow();
        var s = bmState();
        int mode = BattleMageRules.morphMode(c.parameters);
        Vec3 dir = horizontal(p.getLookAngle());

        boolean secondPress = c.terminal || (mode != 1 && s.transformed(wall) && !s.teanaFinisherUsed);
        if (secondPress || mode == 1) {
            // 2nd-press Apostle Spear Finisher (or Morph A instant burst)
            s.teanaFinisherUsed = true;
            var fin = new TeanaFinisherState();
            fin.cast = c;
            fin.origin = p.position();
            fin.dir = dir;
            fin.range = c.parameters.get("range", 10.0);
            fin.radius = c.parameters.get("finisherRadius", 8.5);
            fin.targetPos = advanceClear(p, p.position(), dir, Math.min(7.5, fin.range * 0.75));
            fin.keepTransform = mode != 1 && c.parameters.get("keepTransformAfterFinisher", 0.0) >= 1.0;
            fin.mode = mode;
            fin.start = now;
            long totalMs = Math.max(600L, Math.round(c.parameters.get("finisherDuration", 1.4) * 1000.0));
            fin.impactAt = now + Math.round(totalMs * 0.58);
            fin.endAt = now + totalMs;
            activeTeanaFinisher = fin;

            PlayerPoseChannel.play(c, "teana_finisher", totalMs, dir, false);
            var motion = new JsonObject();
            motion.addProperty("range", fin.range);
            motion.addProperty("radius", fin.radius);
            motion.addProperty("finisher", true);
            motion.addProperty("mode", mode);
            motion.addProperty("color", BattleMageRules.COLOR_TEANA);
            MageClient.INSTANCE.visual(c, c.id, "START", fin.origin.add(0, 1.1, 0), dir, totalMs + 650L, 0, motion);
            schedulePublish();
            return;
        }

        // 1st press: Enter Teana Transformation!
        long durMs = Math.max(1000L, Math.round(c.parameters.get("transformSeconds", 30.0) * 1000.0));
        s.teanaUntil = wall + durMs;
        s.teanaCast = c.id;
        s.teanaFinisherUsed = false;
        // Upgrade all existing chasers to Teana Chasers + generate 3 new Teana Chasers
        if (s.chasers != null) {
            for (var ch : s.chasers) if (ch != null) ch.teana = true;
        }
        int gen = Math.max(1, (int) Math.round(c.parameters.get("chaserGen", 3.0)));
        BattleMageRules.generateChasers(s, gen, c.stats, state().progress(), wall, RNG);
        teanaActivationInvulnUntil = now + 600L;
        s.revision++;

        // Grant activation shield absorption
        double shieldRatio = c.parameters.get("teanaShieldRatio", 0.25);
        if (shieldRatio > 0) {
            double maxHp = Math.max(1.0, c.stats.get(Stat.HEALTH));
            state().resources().absorption.put("teanaShield", maxHp * shieldRatio);
            state().resources().absorptionUntil.put("teanaShield", now + durMs);
        }

        // Immediate activation aura burst (physical + 4-element magic)
        double auraR = c.parameters.get("auraRadius", 5.5);
        Vec3 center = p.position().add(0, 1.0, 0);
        for (var target : SkillController.INSTANCE.targets(new AABB(center, center).inflate(auraR, 3.0, auraR))) {
            if (target.position().subtract(p.position()).horizontalDistanceSqr() <= auraR * auraR) {
                MageClient.INSTANCE.issueElementalist(c, target, c.parameters.get("auraPhys", 0.15), -1, 0.4, "auraPhys", "aura_phys");
                MageClient.INSTANCE.issueMulti(c, target, c.parameters.get("auraMagic", 0.15), BattleMageRules.ALL_FOUR_ELEMENTS, 0, "auraMagic", "aura_magic");
            }
        }

        PlayerPoseChannel.play(c, "teana_transformation", 760L, dir, false);
        var motion = new JsonObject();
        motion.addProperty("radius", auraR);
        motion.addProperty("finisher", false);
        motion.addProperty("mode", mode);
        motion.addProperty("color", BattleMageRules.COLOR_TEANA);
        MageClient.INSTANCE.visual(c, c.id, "START", center, dir, 1200L, 0, motion);
        CareerClient.INSTANCE.publish();
        PlayerPoseChannel.release(c);
        MageClient.INSTANCE.finished(c);
    }

    // --- Main Tick Loop ---

    public static void tick() {
        var mc = Minecraft.getInstance();
        var p = mc.player;
        if (p == null || mc.level == null || !state().ready()) return;
        if (state().own().dead || p.isDeadOrDying()) {
            clear();
            return;
        }
        long now = ClientUi.now();
        long wall = state().serverNow();

        // 1. Auto-Chaser & State Expiration (rate-limited <= 4Hz)
        if (isBattleMage()) {
            var s = bmState();
            if (s.expire(wall)) pendingStatePublish = true;
            if (SkillController.INSTANCE.validWeapon() && (state().resources().combatUntil > now || s.transformed(wall))) {
                Stats snap = SkillController.INSTANCE.snapshot();
                int gRank = BattleMageRules.grooveRank(snap, state().progress());
                if (gRank > 0) {
                    var gp = BattleMageRules.grooveParams(snap, state().progress());
                    long intervalMs = Math.round(BattleMageRules.autoChaserIntervalSeconds(gRank, gp, snap) * 1000.0);
                    if (s.nextAutoChaserAt <= 0) {
                        s.nextAutoChaserAt = wall + intervalMs;
                    } else if (wall >= s.nextAutoChaserAt) {
                        s.nextAutoChaserAt = wall + intervalMs;
                        double chance = gp.get("autoChaserChance", 1.0);
                        var lp = BattleMageRules.launcherParams(snap, state().progress());
                        int maxCap = (int) Math.max(1, lp.get("maxChasers", 7.0));
                        if (s.chaserCount(wall) < maxCap && RNG.nextDouble() <= chance) {
                            BattleMageRules.generateChasers(s, 1, snap, state().progress(), wall, RNG);
                            pendingStatePublish = true;
                        }
                    }
                }
            }
            if (pendingStatePublish && now - lastStatePublishAt >= STATE_SYNC_INTERVAL_MS) {
                pendingStatePublish = false;
                lastStatePublishAt = now;
                CareerClient.INSTANCE.publish();
            }
        }

        // 2. Tick active Chaser Launcher orbs
        tickOrbs(p, now);

        // 3. Tick active dashes (Palm Blast & Dragon Strike)
        tickDashes(p, now, wall);

        // 4. Tick delayed blasts (Full Swing / Chaser Fusion / Chaser Press / Dragon Fang Morph A)
        tickBlasts(p, now, wall);

        // 5. Tick Punto Wave
        tickPunto(p, now, wall);

        // 6. Tick Dragon Spear (active & detached)
        if (activeSpear != null && tickSpear(activeSpear, p, now, wall, false)) {
            activeSpear = null;
        }
        detachedSpears.removeIf(sp -> tickSpear(sp, p, now, wall, true));

        // 7. Tick Teana 2nd-Press Finisher
        tickTeanaFinisher(p, now, wall);

        // 8. Flush knockback/pull motions (rate-limited <= 4Hz)
        flushMotions(now);
    }

    private static void tickOrbs(LocalPlayer p, long now) {
        var mc = Minecraft.getInstance();
        for (var it = orbs.iterator(); it.hasNext(); ) {
            var orb = it.next();
            if (!MageClient.INSTANCE.valid(orb.cast)) {
                it.remove();
                continue;
            }
            double dt = Math.clamp((now - orb.lastTick) / 1000.0, 0.005, 0.12);
            orb.lastTick = now;

            LivingEntity target = null;
            if (orb.targetUuid != null && mc.level != null) {
                for (var cand : SkillController.INSTANCE.targets(p.getBoundingBox().inflate(22.0, 8.0, 22.0))) {
                    if (cand.getUUID().equals(orb.targetUuid)) { target = cand; break; }
                }
            }
            if (target == null) {
                // Soft homing toward closest enemy near trajectory
                double bestDist = 3.5 * 3.5;
                for (var cand : SkillController.INSTANCE.targets(new AABB(orb.pos, orb.pos).inflate(4.5, 3.0, 4.5))) {
                    double d2 = cand.getBoundingBox().getCenter().distanceToSqr(orb.pos);
                    if (d2 < bestDist) { bestDist = d2; target = cand; }
                }
            }
            if (target != null) {
                Vec3 to = target.getBoundingBox().getCenter().subtract(orb.pos);
                if (to.lengthSqr() > 1e-6) orb.dir = to.normalize();
            }
            Vec3 next = orb.pos.add(orb.dir.scale(orb.speed * dt));
            var wallHit = mc.level.clip(new ClipContext(orb.pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
            boolean explode = now >= orb.expiresAt || wallHit.getType() != HitResult.Type.MISS;
            if (target != null && target.getBoundingBox().inflate(0.55).distanceToSqr(next) < 0.85) {
                next = target.getBoundingBox().getCenter();
                explode = true;
            }
            orb.pos = wallHit.getType() != HitResult.Type.MISS ? wallHit.getLocation() : next;

            if (explode) {
                int[] elems = BattleMageRules.chaserElements(orb.colorType, orb.teana);
                double slow = orb.colorType == BattleMageState.ICE ? 0.6 : 0.0;
                for (var victim : SkillController.INSTANCE.targets(new AABB(orb.pos, orb.pos).inflate(orb.radius, 2.5, orb.radius))) {
                    if (victim.getBoundingBox().getCenter().distanceToSqr(orb.pos) <= (orb.radius + victim.getBbWidth() * 0.5) * (orb.radius + victim.getBbWidth() * 0.5)) {
                        MageClient.INSTANCE.issueMulti(orb.cast, victim, orb.coeff, elems, slow, "", "chaser_" + orb.visualId);
                    }
                }
                var motion = new JsonObject();
                motion.addProperty("radius", orb.radius);
                motion.addProperty("color", BattleMageRules.chaserColor(orb.colorType, orb.teana));
                motion.addProperty("teana", orb.teana);
                MageClient.INSTANCE.visual(orb.cast, orb.visualId, "FINISH", orb.pos, orb.dir, 360L, ++orb.sequence, motion);
                TrainingCombat.actionFinished(orb.cast.id);
                it.remove();
            }
        }
    }

    private static void tickDashes(LocalPlayer p, long now, long wall) {
        for (var it = dashes.iterator(); it.hasNext(); ) {
            var d = it.next();
            if (!MageClient.INSTANCE.valid(d.cast) || CombatContentTypes.controlled(p)) {
                PlayerPoseChannel.cancel(d.cast);
                MageClient.INSTANCE.finished(d.cast);
                it.remove();
                continue;
            }
            double u = Math.clamp((now - d.start) / (double) Math.max(1L, d.dashMs), 0.0, 1.0);
            Vec3 desired = d.origin.add(d.dir.scale(d.distance * u));
            Vec3 step = advanceClear(p, p.position(), desired.subtract(p.position()));
            p.move(MoverType.SELF, step.subtract(p.position()));

            // Path hits along dash
            var pathBox = p.getBoundingBox().inflate(d.width * 0.5, 1.2, d.width * 0.5);
            for (var target : SkillController.INSTANCE.targets(pathBox)) {
                if (d.pathHit.add(target.getUUID())) {
                    if (d.isDragonStrike) {
                        MageClient.INSTANCE.issueElementalist(d.cast, target, d.cast.parameters.get("damage", 0.40), -1, 0, "", "ds_path_" + d.dashIndex);
                        queueMotion(target, d.dir.scale(0.85));
                    } else {
                        MageClient.INSTANCE.issueElementalist(d.cast, target, d.cast.parameters.get("damage", 1.0), -1, d.isTeana ? 0.6 : 0.3, "", "palm_" + d.dashIndex);
                        if (d.knockback > 0) {
                            queueMotion(target, d.dir.scale(Math.min(1.6, d.knockback * 0.35)).add(0, d.isTeana ? 0.45 : 0.22, 0));
                        }
                    }
                }
            }

            if (u >= 1.0 && !d.impacted) {
                d.impacted = true;
                d.endpoint = p.position();
                Vec3 center = d.endpoint.add(0, 0.9, 0);
                int gen = Math.max(1, (int) Math.round(d.cast.parameters.get("chaserGen", 1.0))) + (d.isTeana ? 1 : 0);
                boolean hitAny = !d.pathHit.isEmpty();

                if (d.isDragonStrike) {
                    for (var target : SkillController.INSTANCE.targets(new AABB(center, center).inflate(d.blastRadius, 2.8, d.blastRadius))) {
                        if (target.position().subtract(d.endpoint).horizontalDistanceSqr() <= d.blastRadius * d.blastRadius) {
                            MageClient.INSTANCE.issueElementalist(d.cast, target, d.cast.parameters.get("finisher", 0.60), -1, 0.45, "", "ds_blast_" + d.dashIndex);
                            hitAny = true;
                        }
                    }
                    var motion = new JsonObject();
                    motion.addProperty("blastRadius", d.blastRadius);
                    motion.addProperty("color", 0xFF5A36);
                    motion.addProperty("mode", d.mode);
                    MageClient.INSTANCE.visual(d.cast, d.cast.id, "FINISH", center, d.dir, 520L, ++d.sequence, motion);
                } else {
                    for (var target : SkillController.INSTANCE.targets(new AABB(center, center).inflate(d.blastRadius, 2.2, d.blastRadius))) {
                        if (d.pathHit.add(target.getUUID()) && target.position().subtract(d.endpoint).horizontalDistanceSqr() <= d.blastRadius * d.blastRadius) {
                            MageClient.INSTANCE.issueElementalist(d.cast, target, d.cast.parameters.get("damage", 1.0), -1, d.isTeana ? 0.6 : 0.3, "", "palm_end_" + d.dashIndex);
                            if (d.knockback > 0) queueMotion(target, d.dir.scale(Math.min(1.6, d.knockback * 0.35)).add(0, 0.25, 0));
                            hitAny = true;
                        }
                    }
                    var motion = new JsonObject();
                    motion.addProperty("radius", d.blastRadius);
                    motion.addProperty("teana", d.isTeana);
                    motion.addProperty("color", d.isTeana ? BattleMageRules.COLOR_TEANA : BattleMageRules.COLOR_FIRE);
                    MageClient.INSTANCE.visual(d.cast, d.cast.id, "RELEASE", center, d.dir, 400L, ++d.sequence, motion);
                    openCancelWindow(BattleMageSkills.PALM_BLAST, d.cast.parameters.get("cancelWindowSeconds", 0.45));
                }

                if (hitAny) {
                    BattleMageRules.generateChasers(bmState(), gen, d.cast.stats, state().progress(), wall, RNG);
                    schedulePublish();
                }

                // Morph B Dragon Strike: second return dash!
                if (d.isDragonStrike && d.dashesLeft > 1) {
                    d.dashesLeft--;
                    d.dashIndex++;
                    d.dir = d.dir.scale(-1.0);
                    d.origin = p.position();
                    d.start = now;
                    d.impactAt = now + d.dashMs + 90L;
                    d.impacted = false;
                    d.pathHit.clear();
                    PlayerPoseChannel.play(d.cast, "dragon_strike", d.dashMs + 280L, d.dir, false);
                    continue;
                }

                // Dragon Strike return-to-origin snapback
                if (d.isDragonStrike && d.returnToOrigin && !d.returned) {
                    d.returned = true;
                    Vec3 back = advanceClear(p, p.position(), d.origin.subtract(p.position()));
                    p.move(MoverType.SELF, back.subtract(p.position()));
                }

                PlayerPoseChannel.release(d.cast);
                MageClient.INSTANCE.finished(d.cast);
                it.remove();
            }
        }
    }

    private static void tickBlasts(LocalPlayer p, long now, long wall) {
        for (var it = blasts.iterator(); it.hasNext(); ) {
            var b = it.next();
            if (now < b.impactAt) continue;
            it.remove();
            if (!MageClient.INSTANCE.valid(b.cast)) continue;

            if (b.isDragonFangSecond) {
                double fangRange = Math.max(0.5, b.cast.parameters.get("range", 5.0));
                double fangWidth = Math.max(0.5, b.cast.parameters.get("width", 1.5));
                Vec3 from = p.position().add(0, 0.8, 0);
                var bounds = new AABB(from, from.add(b.dir.scale(fangRange))).inflate(Math.max(1.0, fangWidth * 0.5 + 0.3), 1.5, Math.max(1.0, fangWidth * 0.5 + 0.3));
                for (var target : SkillController.INSTANCE.targets(bounds)) {
                    Vec3 delta = target.getBoundingBox().getCenter().subtract(from);
                    double front = delta.dot(b.dir), side = Math.abs(delta.x * b.dir.z - delta.z * b.dir.x);
                    if (front >= -0.5 && front <= fangRange + target.getBbWidth() * 0.5 && side <= fangWidth * 0.5 + target.getBbWidth() * 0.5) {
                        MageClient.INSTANCE.issueElementalist(b.cast, target, b.coeff, -1, 0.35, "", "fang_2");
                    }
                }
                continue;
            }

            if (b.isFullSwing) {
                double halfArcCos = Math.cos(Math.toRadians(Math.clamp(b.cast.parameters.get("arcDegrees", 210.0), 60.0, 360.0) * 0.5));
                boolean hitAny = false;
                for (var target : SkillController.INSTANCE.targets(new AABB(b.center, b.center).inflate(b.radius, 2.5, b.radius))) {
                    Vec3 delta = target.getBoundingBox().getCenter().subtract(b.center);
                    double hDist = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
                    if (hDist > b.radius + target.getBbWidth() * 0.5) continue;
                    if (halfArcCos > -0.99 && hDist > 0.6) {
                        double dot = (delta.x * b.dir.x + delta.z * b.dir.z) / hDist;
                        if (dot < halfArcCos) continue;
                    }
                    MageClient.INSTANCE.issueElementalist(b.cast, target, b.coeff, -1, 0.25, "phys", "swing_phys");
                    if (b.waveCoeff > 0) {
                        MageClient.INSTANCE.issueMulti(b.cast, target, b.waveCoeff, b.elements, 0, "magicWave", "swing_wave");
                    }
                    hitAny = true;
                }
                if (hitAny) {
                    int gen = Math.max(1, (int) Math.round(b.cast.parameters.get("chaserGen", 1.0)));
                    BattleMageRules.generateChasers(bmState(), gen, b.cast.stats, state().progress(), wall, RNG);
                    schedulePublish();
                }
                openCancelWindow(BattleMageSkills.FULL_SWING, b.cast.parameters.get("cancelWindowSeconds", 0.45));
                var motion = new JsonObject();
                motion.addProperty("radius", b.radius);
                motion.addProperty("color", b.color);
                motion.addProperty("mode", b.mode);
                MageClient.INSTANCE.visual(b.cast, b.cast.id, "RELEASE", b.center, b.dir, 420L, ++b.sequence, motion);
                PlayerPoseChannel.release(b.cast);
                MageClient.INSTANCE.finished(b.cast);
                continue;
            }

            if (b.isPress && b.pullRadius > 0.5) {
                for (var victim : SkillController.INSTANCE.targets(new AABB(b.center, b.center).inflate(b.pullRadius, 3.0, b.pullRadius))) {
                    Vec3 toCenter = b.center.subtract(victim.position());
                    double len = Math.sqrt(toCenter.x * toCenter.x + toCenter.z * toCenter.z);
                    if (len > 0.8 && len <= b.pullRadius) {
                        queueMotion(victim, new Vec3(toCenter.x / len, 0.08, toCenter.z / len).scale(Math.min(1.6, len * 0.45)));
                    }
                }
            }

            for (var target : SkillController.INSTANCE.targets(new AABB(b.center, b.center).inflate(b.radius, 3.0, b.radius))) {
                double distSq = target.getBoundingBox().getCenter().distanceToSqr(b.center);
                double limit = b.radius + target.getBbWidth() * 0.5;
                if (distSq <= limit * limit) {
                    MageClient.INSTANCE.issueMulti(b.cast, target, b.coeff, b.elements, b.controlSeconds, "", b.isPress ? "press" : "fusion");
                }
            }
            var motion = new JsonObject();
            motion.addProperty("radius", b.radius);
            motion.addProperty("color", b.color);
            motion.addProperty("mode", b.mode);
            MageClient.INSTANCE.visual(b.cast, b.cast.id, "FINISH", b.center, b.dir, 580L, ++b.sequence, motion);
            MageClient.INSTANCE.finished(b.cast);
        }
    }

    private static void tickPunto(LocalPlayer p, long now, long wall) {
        if (activePunto == null) return;
        var st = activePunto;
        if (!MageClient.INSTANCE.valid(st.cast) || CombatContentTypes.controlled(p)) {
            cancelPunto(true);
            return;
        }
        // Jump cancels Punto Wave cleanly just like DNF!
        if (Minecraft.getInstance().options.keyJump.isDown() && st.delivered >= 1) {
            cancelPunto(true);
            return;
        }
        if (st.mode == 2) {
            st.dir = horizontal(p.getLookAngle());
        }
        // Subtle forward stepping during thrusts
        if (!st.finishing && st.stepSpeed > 0 && Minecraft.getInstance().options.keyUp.isDown()) {
            Vec3 step = advanceClear(p, p.position(), st.dir.scale(st.stepSpeed * 0.05));
            p.move(MoverType.SELF, step.subtract(p.position()));
        }

        while (!st.finishing && st.delivered < st.totalHits && now >= st.nextHitAt) {
            st.delivered++;
            st.nextHitAt += st.intervalMs;
            Vec3 from = p.position().add(0, 0.9, 0);
            var bounds = new AABB(from, from.add(st.dir.scale(st.range))).inflate(st.width * 0.5 + 0.5, 1.8, st.width * 0.5 + 0.5);
            boolean hitAny = false;
            for (var target : SkillController.INSTANCE.targets(bounds)) {
                Vec3 delta = target.getBoundingBox().getCenter().subtract(from);
                double front = delta.dot(st.dir), side = Math.abs(delta.x * st.dir.z - delta.z * st.dir.x);
                if (front >= -0.4 && front <= st.range + target.getBbWidth() * 0.5 && side <= st.width * 0.5 + target.getBbWidth() * 0.5) {
                    MageClient.INSTANCE.issueElementalist(st.cast, target, st.cast.parameters.get("damage", 0.054), -1, 0, "", "punto_" + st.delivered);
                    hitAny = true;
                }
            }
            if (hitAny && (st.delivered % st.chaserEvery == 0)) {
                BattleMageRules.generateChasers(bmState(), 1, st.cast.stats, state().progress(), wall, RNG);
                schedulePublish();
            }
            if (st.mode == 1) {
                // Morph A: crisp 3-hit generates extra combo to rapidly stack Battle Groove
                if (hitAny) BattleMageRules.onHitCombo(bmState(), 4, st.cast.stats, state().progress(), wall);
            }
            var motion = new JsonObject();
            motion.addProperty("range", st.range);
            motion.addProperty("width", st.width);
            motion.addProperty("hitIndex", st.delivered);
            motion.addProperty("color", bmState().transformed(wall) ? BattleMageRules.COLOR_TEANA : BattleMageRules.COLOR_MARTIAL);
            MageClient.INSTANCE.visual(st.cast, st.cast.id, "HIT", from.add(st.dir.scale(st.range * 0.5)), st.dir, 240L, ++st.sequence, motion);
        }

        if (!st.finishing && st.delivered >= st.totalHits) {
            st.finishing = true;
            st.endAt = now + 150L;
            if (st.mode != 1) {
                PlayerPoseChannel.play(st.cast, "punto_wave_finish", 360L, st.dir, false);
            }
        }
        if (st.finishing && now >= st.endAt) {
            Vec3 center = p.position().add(st.dir.scale(1.8)).add(0, 0.9, 0);
            for (var target : SkillController.INSTANCE.targets(new AABB(center, center).inflate(st.finisherRadius, 2.5, st.finisherRadius))) {
                if (target.position().subtract(p.position()).horizontalDistanceSqr() <= st.finisherRadius * st.finisherRadius) {
                    MageClient.INSTANCE.issueElementalist(st.cast, target, st.cast.parameters.get("finisher", 0.35), -1, 0.35, "", "punto_fin");
                    Vec3 away = target.position().subtract(p.position());
                    if (away.horizontalDistanceSqr() > 1e-4) {
                        queueMotion(target, new Vec3(away.x, 0, away.z).normalize().scale(0.65).add(0, 0.15, 0));
                    }
                }
            }
            var motion = new JsonObject();
            motion.addProperty("finisherRadius", st.finisherRadius);
            motion.addProperty("color", bmState().transformed(wall) ? BattleMageRules.COLOR_TEANA : BattleMageRules.COLOR_MARTIAL);
            MageClient.INSTANCE.visual(st.cast, st.cast.id, "FINISH", center, st.dir, 460L, ++st.sequence, motion);
            activePunto = null;
            PlayerPoseChannel.release(st.cast);
            MageClient.INSTANCE.finished(st.cast);
        }
    }

    private static boolean tickSpear(DragonSpearState st, LocalPlayer p, long now, long wall, boolean allowDetach) {
        if (!MageClient.INSTANCE.valid(st.cast)) {
            if (!allowDetach) PlayerPoseChannel.cancel(st.cast);
            MageClient.INSTANCE.finished(st.cast);
            return true;
        }
        if (!st.thrusted && now >= st.start + 150L) {
            st.thrusted = true;
            Vec3 from = st.origin.add(0, 0.9, 0);
            var bounds = new AABB(from, from.add(st.dir.scale(st.range))).inflate(st.width * 0.5 + 0.6, 2.2, st.width * 0.5 + 0.6);
            boolean hitAny = false;
            for (var target : SkillController.INSTANCE.targets(bounds)) {
                Vec3 delta = target.getBoundingBox().getCenter().subtract(from);
                double front = delta.dot(st.dir), side = Math.abs(delta.x * st.dir.z - delta.z * st.dir.x);
                if (front >= -0.5 && front <= st.range + target.getBbWidth() * 0.5 && side <= st.width * 0.5 + target.getBbWidth() * 0.5) {
                    MageClient.INSTANCE.issueElementalist(st.cast, target, st.cast.parameters.get("thrust", 0.25), -1, st.holdSeconds, "thrust", "spear_thrust");
                    if (st.holdSeconds > 0) {
                        st.held.add(target.getUUID());
                        Vec3 toTip = st.tipPos.subtract(target.position());
                        if (toTip.horizontalDistanceSqr() > 0.25) {
                            queueMotion(target, new Vec3(toTip.x, 0.1, toTip.z).scale(0.55));
                        }
                    }
                    hitAny = true;
                }
            }
            if (hitAny) {
                int gen = Math.max(1, (int) Math.round(st.cast.parameters.get("chaserGen", 2.0)));
                BattleMageRules.generateChasers(bmState(), gen, st.cast.stats, state().progress(), wall, RNG);
                schedulePublish();
            }
            if (allowDetach) {
                st.detached = true;
                PlayerPoseChannel.release(st.cast);
            }
        }

        // Fire Dragon Chasers along spear blade (Pure Magic 4-element!)
        while (st.delivered < st.totalBalls && now >= st.nextBallAt) {
            st.delivered++;
            st.nextBallAt += st.intervalMs;
            double ballCoeff = st.cast.parameters.get("dragonChaser", 0.05);
            if (ballCoeff > 0) {
                Vec3 hitCenter = st.tipPos.add(0, 1.0, 0);
                for (var target : SkillController.INSTANCE.targets(new AABB(hitCenter, hitCenter).inflate(4.2, 3.0, 4.2))) {
                    if (target.position().subtract(st.tipPos).horizontalDistanceSqr() <= 4.2 * 4.2) {
                        MageClient.INSTANCE.issueMulti(st.cast, target, ballCoeff, BattleMageRules.ALL_FOUR_ELEMENTS, 0, "dragonChaser", "spear_ball_" + st.delivered);
                    }
                }
                var motion = new JsonObject();
                motion.addProperty("radius", 3.8);
                motion.addProperty("ballIndex", st.delivered);
                motion.addProperty("color", BattleMageRules.chaserColor(st.delivered % 5, st.delivered % 3 == 0));
                MageClient.INSTANCE.visual(st.cast, st.cast.id, "HIT", hitCenter, st.dir, 300L, ++st.sequence, motion);
            }
        }

        // Final Dragon Explosion (Physical + Magic 4-element!)
        if (!st.finished && now >= st.finishAt) {
            st.finished = true;
            Vec3 center = st.tipPos.add(0, 1.0, 0);
            double finPhys = st.cast.parameters.get("finisherPhys", 0.175);
            double finMag = st.cast.parameters.get("finisherMagic", 0.175);
            for (var target : SkillController.INSTANCE.targets(new AABB(center, center).inflate(st.blastRadius, 3.5, st.blastRadius))) {
                if (target.position().subtract(st.tipPos).horizontalDistanceSqr() <= st.blastRadius * st.blastRadius) {
                    if (finPhys > 0) MageClient.INSTANCE.issueElementalist(st.cast, target, finPhys, -1, 0.4, "finisherPhys", "spear_fin_p");
                    if (finMag > 0) MageClient.INSTANCE.issueMulti(st.cast, target, finMag, BattleMageRules.ALL_FOUR_ELEMENTS, 0, "finisherMagic", "spear_fin_m");
                }
            }
            var motion = new JsonObject();
            motion.addProperty("blastRadius", st.blastRadius);
            motion.addProperty("mode", st.mode);
            motion.addProperty("color", 0xFFB833);
            MageClient.INSTANCE.visual(st.cast, st.cast.id, "FINISH", center, st.dir, 650L, ++st.sequence, motion);
            if (!allowDetach) PlayerPoseChannel.release(st.cast);
            MageClient.INSTANCE.finished(st.cast);
            return true;
        }
        return false;
    }

    private static void tickTeanaFinisher(LocalPlayer p, long now, long wall) {
        if (activeTeanaFinisher == null) return;
        var fin = activeTeanaFinisher;
        if (!MageClient.INSTANCE.valid(fin.cast)) {
            PlayerPoseChannel.cancel(fin.cast);
            MageClient.INSTANCE.finished(fin.cast);
            activeTeanaFinisher = null;
            return;
        }
        if (!fin.impacted && now >= fin.impactAt) {
            fin.impacted = true;
            Vec3 step = advanceClear(p, p.position(), fin.targetPos.subtract(p.position()));
            p.move(MoverType.SELF, step.subtract(p.position()));
            Vec3 center = p.position().add(0, 1.1, 0);
            double fPhys = fin.cast.parameters.get("finisherPhys", 0.35);
            double fMag = fin.cast.parameters.get("finisherMagic", 0.35);
            for (var target : SkillController.INSTANCE.targets(new AABB(center, center).inflate(fin.radius, 4.0, fin.radius))) {
                if (target.position().subtract(p.position()).horizontalDistanceSqr() <= fin.radius * fin.radius) {
                    if (fPhys > 0) MageClient.INSTANCE.issueElementalist(fin.cast, target, fPhys, -1, 0.6, "finisherPhys", "teana_fin_p");
                    if (fMag > 0) MageClient.INSTANCE.issueMulti(fin.cast, target, fMag, BattleMageRules.ALL_FOUR_ELEMENTS, 0, "finisherMagic", "teana_fin_m");
                }
            }
            var motion = new JsonObject();
            motion.addProperty("radius", fin.radius);
            motion.addProperty("finisher", true);
            motion.addProperty("color", BattleMageRules.COLOR_TEANA);
            MageClient.INSTANCE.visual(fin.cast, fin.cast.id, "FINISH", center, fin.dir, 850L, ++fin.sequence, motion);
        }
        if (now >= fin.endAt) {
            if (!fin.keepTransform) {
                bmState().teanaUntil = 0;
                bmState().teanaCast = "";
                bmState().teanaFinisherUsed = false;
                bmState().revision++;
                state().resources().absorption.remove("teanaShield");
                state().resources().absorptionUntil.remove("teanaShield");
                CareerClient.INSTANCE.publish();
            }
            activeTeanaFinisher = null;
            PlayerPoseChannel.release(fin.cast);
            MageClient.INSTANCE.finished(fin.cast);
        }
    }

    // --- Multi-charge pool (same semantics as ElementalistClient) ---

    private static ChargePool syncPool(String skillId, int max, long rechargeMillis, long now) {
        var pool = chargePools.computeIfAbsent(skillId, k -> {
            var cp = new ChargePool();
            cp.max = max;
            cp.available = max;
            cp.rechargeMillis = Math.max(500L, rechargeMillis);
            return cp;
        });
        pool.max = max;
        pool.rechargeMillis = Math.max(500L, rechargeMillis);
        if (pool.available > max) pool.available = max;
        while (pool.available < pool.max && pool.nextRechargeAt > 0 && now >= pool.nextRechargeAt) {
            pool.available++;
            pool.nextRechargeAt = pool.available < pool.max ? pool.nextRechargeAt + pool.rechargeMillis : 0L;
        }
        return pool;
    }

    public static boolean hasCharge(SkillDefinition skill, SkillParameters.Snapshot params, long now) {
        if (!BattleMageSkills.contains(skill.id)) return false;
        int max = (int) Math.round(params.get("maxCharges", 1.0));
        if (max <= 1) return false;
        var pool = chargePools.get(skill.id);
        if (pool == null) return true;
        syncPool(skill.id, max, pool.rechargeMillis, now);
        return pool.available > 0;
    }

    public static void onCastPaid(SkillDefinition skill, SkillParameters.Snapshot params, long cooldownMillis, long now,
                                  cn.yumis.fight.modules.combat.api.Runtime resources) {
        if (!BattleMageSkills.contains(skill.id)) return;
        int max = (int) Math.round(params.get("maxCharges", 1.0));
        if (max <= 1) return;
        var pool = syncPool(skill.id, max, cooldownMillis, now);
        if (pool.available > 0) pool.available--;
        if (pool.nextRechargeAt <= 0 && pool.available < pool.max) {
            pool.nextRechargeAt = now + pool.rechargeMillis;
        }
        if (pool.available <= 0) {
            resources.cooldowns.put(skill.id, pool.nextRechargeAt);
        } else {
            resources.cooldowns.remove(skill.id);
        }
    }

    public static int remainingCharges(String skillId, int max, long now) {
        var pool = chargePools.get(skillId);
        if (pool == null) return max;
        return syncPool(skillId, max, pool.rechargeMillis, now).available;
    }

    public static long nextRechargeRemaining(String skillId, long now) {
        var pool = chargePools.get(skillId);
        if (pool == null || pool.available >= pool.max || pool.nextRechargeAt <= now) return 0L;
        return pool.nextRechargeAt - now;
    }

    // --- Helpers & Cleanup ---

    private static void openCancelWindow(String skillId, double seconds) {
        long ms = Math.max(100L, Math.round(seconds * 1000.0));
        cancelWindowUntil = ClientUi.now() + ms;
        cancelSourceSkill = skillId;
    }

    private static void schedulePublish() {
        pendingStatePublish = true;
    }

    private static LivingEntity resolveRecentTarget(LocalPlayer p, double maxRange, long now) {
        if (recentHitTarget == null || now > recentHitUntil) return null;
        double r2 = maxRange * maxRange;
        for (var cand : SkillController.INSTANCE.targets(p.getBoundingBox().inflate(maxRange, 6.0, maxRange))) {
            if (cand.getUUID().equals(recentHitTarget) && cand.position().distanceToSqr(p.position()) <= r2) {
                return cand;
            }
        }
        return null;
    }

    private static Vec3 horizontal(Vec3 look) {
        Vec3 h = new Vec3(look.x, 0, look.z);
        return h.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : h.normalize();
    }

    private static Vec3 advanceClear(LocalPlayer p, Vec3 from, Vec3 dir, double distance) {
        return advanceClear(p, from, dir.scale(distance));
    }

    private static Vec3 advanceClear(LocalPlayer p, Vec3 from, Vec3 delta) {
        var level = Minecraft.getInstance().level;
        if (level == null || delta.lengthSqr() < 1e-6) return from;
        Vec3 start = from.add(0, 0.6, 0);
        Vec3 target = start.add(delta);
        var hit = level.clip(new ClipContext(start, target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        if (hit.getType() != HitResult.Type.MISS) {
            Vec3 clipped = hit.getLocation().subtract(0, 0.6, 0);
            Vec3 back = delta.normalize().scale(0.45);
            return clipped.subtract(back);
        }
        return from.add(delta);
    }

    private static void queueMotion(LivingEntity target, Vec3 delta) {
        if (target == null) return;
        Vec3 v = delta.length() > 2.5 ? delta.normalize().scale(2.5) : delta;
        pendingMotions.put(target.getUUID(), target.position().add(v));
    }

    private static void flushMotions(long now) {
        if (pendingMotions.isEmpty() || now - lastMotionSendAt < MOTION_SYNC_INTERVAL_MS) return;
        lastMotionSendAt = now;
        var moves = new JsonArray();
        for (var e : pendingMotions.entrySet()) {
            var item = new JsonObject();
            item.addProperty("target", e.getKey().toString());
            item.addProperty("x", e.getValue().x);
            item.addProperty("y", e.getValue().y);
            item.addProperty("z", e.getValue().z);
            moves.add(item);
        }
        pendingMotions.clear();
        if (moves.isEmpty()) return;
        var pkt = new JsonObject();
        pkt.addProperty("session", state().session());
        pkt.addProperty("generation", state().own().generation);
        pkt.add("moves", moves);
        state().send("careerMotion", pkt);
    }

    public static PuntoState activePunto() { return activePunto; }
    public static DragonSpearState activeSpear() { return activeSpear; }
    public static TeanaFinisherState activeTeanaFinisher() { return activeTeanaFinisher; }

    public static void cancelOnScreen() {
        BattleMageChargeClient.cancel();
        cancelPunto(true);
    }

    public static boolean cancelChannel() {
        return cancelPunto(true);
    }

    public static void cancel(String castId) {
        if (activePunto != null && activePunto.cast.id.equals(castId)) cancelPunto(true);
        if (activeSpear != null && activeSpear.cast.id.equals(castId)) {
            PlayerPoseChannel.cancel(activeSpear.cast);
            activeSpear = null;
        }
        dashes.removeIf(d -> d.cast.id.equals(castId));
        blasts.removeIf(b -> b.cast.id.equals(castId));
    }

    public static void clear() {
        BattleMageChargeClient.clear();
        orbs.clear();
        dashes.clear();
        blasts.clear();
        activePunto = null;
        activeSpear = null;
        detachedSpears.clear();
        activeTeanaFinisher = null;
        chargePools.clear();
        pendingMotions.clear();
        recentHitTarget = null;
        recentHitUntil = 0L;
        cancelWindowUntil = 0L;
        cancelSourceSkill = "";
        cancelBonusConsumedThisCast = false;
        basicStep = 0;
        basicComboResetAt = 0L;
        teanaActivationInvulnUntil = 0L;
        pendingStatePublish = false;
    }
}
