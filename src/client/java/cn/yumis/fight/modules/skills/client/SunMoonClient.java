package cn.yumis.fight.modules.skills.client;

import cn.yumis.fight.modules.attributes.api.Stat;
import cn.yumis.fight.modules.attributes.api.Stats;
import cn.yumis.fight.modules.character.api.JobCatalog;
import cn.yumis.fight.modules.combat.api.CombatContentTypes;
import cn.yumis.fight.modules.combat.api.ElementAccumulation;
import cn.yumis.fight.modules.dungeons.api.TowerBattleCard;
import cn.yumis.fight.modules.dungeons.api.client.SealBossState;
import cn.yumis.fight.modules.equipment.api.EquipmentStats;
import cn.yumis.fight.modules.presentation.api.client.ClientUi;
import cn.yumis.fight.modules.skills.api.*;
import cn.yumis.fight.modules.skills.api.client.*;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.*;

import java.util.*;

/** Sun and moon contacts execute on the owner alongside their existing visual choreography. */
public final class SunMoonClient {
    private static final class Area {
        SkillCast cast;
        String visual;
        Vec3 point;
        LivingEntity target;
        long generation, next;
        int index, sequence;
        boolean pulled;
    }

    private static final class EmberZone {
        SkillCast cast;
        String visual;
        Vec3 point;
        double radius, tickCoeff;
        long endAt, nextTickAt, intervalMs;
        int sequence;
    }

    private static final class MoonEcho {
        SkillCast cast;
        String visual;
        Vec3 pos, velocity;
        LivingEntity target;
        long targetGen, lastTick, expiresAt;
        double radius, coeff;
        int sequence;
    }

    private static final List<Area> areas = new ArrayList<>();
    private static final List<EmberZone> embers = new ArrayList<>();
    private static final List<MoonEcho> echoes = new ArrayList<>();
    private static final Map<UUID, long[]> resistShredUntil = new HashMap<>();

    private SunMoonClient() {}

    static void installHooks() {
        SkillCastHooks.onRequest(SkillCastHooks.Stage.PREPARE_STATS, req -> {
            if (!SunMoonSkills.contains(req.skill.id)) return;
            prepareSunMoonFlags(req);
            req.prepared = SunMoonRules.forCast(req.prepared, req.skill);
        });
        SkillCastHooks.onRequest(SkillCastHooks.Stage.POST_PAY_STATS, req -> {
            if (!SunMoonSkills.contains(req.skill.id)) return;
            prepareSunMoonFlags(req);
            req.prepared = SunMoonRules.forCast(req.prepared, req.skill);
        });
        SkillCastHooks.onRequest(SkillCastHooks.Stage.RESOLVE_PARAMETERS, req -> {
            if (req.skill.id.equals(SunMoonSkills.FLARE) && req.prepared.modifiers.getOrDefault("sunMoonSupernova", 0d) > 0) {
                var values = new LinkedHashMap<>(req.parameters.values());
                double baseRadius = values.getOrDefault("radius", 4.0);
                values.put("radius", baseRadius * (1 + TowerBattleCard.SUNMOON_FLARE_SUPERNOVA.ratio("range")));
                req.parameters = new SkillParameters.Snapshot(req.skill, req.parameters.rank(), values);
            }
        });
        SkillCastHooks.onRequest(SkillCastHooks.Stage.COMMIT_STATE, req -> {
            if (!SunMoonSkills.contains(req.skill.id)) return;
            req.nextView.sunMoon = SunMoonRules.paid(req.nextView.sunMoon, req.skill, req.prepared);
            if (req.skill.id.equals(SunMoonSkills.FLARE)
                && req.prepared.modifiers.getOrDefault("sunMoonSupernova", 0d) > 0
                && !req.resources.training) {
                req.resources.mp = 0;
            }
            if (req.skill.id.equals(SunMoonSkills.REFLUX) && TowerBattleCard.SUNMOON_REFLUX_LEYLINES.held(req.prepared)) {
                var p = Minecraft.getInstance().player;
                if (p != null && p.level() != null) {
                    req.nextView.sunMoon.leylineX = p.getX();
                    req.nextView.sunMoon.leylineY = p.getY();
                    req.nextView.sunMoon.leylineZ = p.getZ();
                    req.nextView.sunMoon.leylineDimension = p.level().dimension().identifier().toString();
                    req.nextView.sunMoon.leylineInstance = SkillsClient.current().own().instance;
                    req.nextView.sunMoon.leylineUntil = req.wall + TowerBattleCard.SUNMOON_REFLUX_LEYLINES.millis("duration");
                }
            }
            if (req.skill.id.equals(SunMoonSkills.PARADOX) && TowerBattleCard.SUNMOON_PARADOX_LOOP.held(req.prepared)) {
                double maxMp = req.prepared.get(Stat.MP);
                if (maxMp > 0) {
                    req.resources.mp = Math.min(maxMp, req.resources.mp + maxMp * TowerBattleCard.SUNMOON_PARADOX_LOOP.ratio("mana"));
                }
            }
        });
    }

    private static void prepareSunMoonFlags(SkillCastHooks.Request req) {
        var p = Minecraft.getInstance().player;
        boolean inLeyline = false;
        if (p != null && p.level() != null) {
            inLeyline = SunMoonRules.inLeyline(req.currentView.sunMoon, p.getX(), p.getY(), p.getZ(),
                p.level().dimension().identifier().toString(), SkillsClient.current().own().instance, req.wall);
        }
        req.prepared.modifiers.put("sunMoonInLeyline", inLeyline ? 1d : 0d);
        boolean supernovaReady = req.skill.id.equals(SunMoonSkills.FLARE)
            && SunMoonRules.isSupernovaReady(req.prepared, req.currentView.sunMoon, req.resources.mp, req.prepared.get(Stat.MP));
        req.prepared.modifiers.put("sunMoonSupernovaReady", supernovaReady ? 1d : 0d);
    }

    /** Elemental resist shred from 极光星幕 (1=Fire, 2=Ice). */
    public static double resistShred(LivingEntity entity, int element, long now) {
        if (entity == null || (element != 1 && element != 2)) return 0.0;
        long[] timers = resistShredUntil.get(entity.getUUID());
        if (timers != null && timers[element] > now) {
            return TowerBattleCard.SUNMOON_STARFALL_AURORA.value("shred");
        }
        return 0.0;
    }

    /**
     * 0-GCD overlay cast for 炽阳火苗: fires a free, boosted-radius Sunfire at any time (even mid-chant)
     * without interrupting active chants or poses.
     */
    public static boolean tryOverlaySparkSunfire(int slot) {
        var state = SkillsClient.current();
        if (!state.ready() || !state.progress().hasJob(JobCatalog.SUN_MOON)) return false;
        var progress = state.progress();
        if (slot < 0 || slot >= progress.slots.length) return false;
        if (!SunMoonSkills.SUN.equals(progress.slots[slot])) return false;
        var view = CareerClient.INSTANCE.view;
        if (view.sunMoon.sunfireSparks <= 0) return false;
        Stats base = SkillController.INSTANCE.snapshot();
        if (!TowerBattleCard.SUNMOON_SUNFIRE_SPARK.held(base)) return false;
        if (!SkillController.INSTANCE.validWeapon()) return false;
        var mc = Minecraft.getInstance();
        var p = mc.player;
        if (p == null || mc.level == null || state.own().dead || p.isDeadOrDying()
            || CombatContentTypes.controlled(p) || SealBossState.isFrozen()) return true;
        var skill = SkillCatalog.require(SunMoonSkills.SUN);
        int rank = SkillLearning.effective(base, progress, skill);
        if (rank <= 0 || !skill.weaponAllowed(progress, base)) return true;

        long now = ClientUi.now();
        Stats prepared = base.copy();
        prepared.modifiers.put("sunMoonSparkCast", 1d);
        prepared.modifiers.put("sourceLevel", (double) progress.level);
        prepared = SunMoonRules.forCast(prepared, skill);

        var params = WeaponSkills.skill(SkillParameters.forCast(p.getUUID(), skill, rank, prepared.parameters), progress, prepared);
        var values = new LinkedHashMap<>(params.values());
        values.put("radius", values.getOrDefault("radius", 3.0) * (1 + TowerBattleCard.SUNMOON_SUNFIRE_SPARK.ratio("range")));
        params = new SkillParameters.Snapshot(skill, rank, values);

        view.sunMoon.sunfireSparks--;
        view.sunMoon.revision++;
        CareerClient.INSTANCE.publish();

        var cast = new SkillCast();
        cast.id = UUID.randomUUID().toString();
        cast.skill = skill;
        cast.slot = slot;
        cast.parameters = params;
        cast.stats = prepared;
        cast.equipment = EquipmentStats.projectileEffects(p, progress, p.getMainHandItem());
        cast.weapon = prepared.weaponInstance;
        cast.origin = p.position();
        Vec3 look = p.getLookAngle();
        cast.direction = look.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : look.normalize();
        cast.center = cast.origin;
        cast.start = cast.stageStart = now;
        cast.speed = Math.max(.01, prepared.get(Stat.CAST_SPEED));
        cast.dimension = mc.level.dimension().identifier().toString();
        cast.session = state.session();
        cast.generation = state.own().generation;
        cast.job = progress.jobId();
        cast.instance = state.own().instance;

        TrainingCombat.begin(cast.id, skill.title);
        TrainingCombat.hold(cast.id, "mage", now + 3000L);
        start(cast);
        return true;
    }

    /** Triggered after any Sun-Moon skill completes release (handles 朔月伴星追射). */
    static void onSkillReleased(SkillCast c) {
        if (c == null || c.skill == null || c.skill.aspect != SkillDefinition.Aspect.SUN) return;
        if (!TowerBattleCard.SUNMOON_MOON_ECHO.held(c.stats)) return;
        var state = SkillsClient.current();
        var view = CareerClient.INSTANCE.view;
        long wall = state.serverNow();
        if (view.sunMoon.heart != 1 || view.sunMoon.stacks <= 0 || view.sunMoon.moonHaloUntil <= wall) return;
        var p = Minecraft.getInstance().player;
        if (p == null) return;

        var moonSkill = SkillCatalog.require(SunMoonSkills.MOON);
        int moonRank = Math.max(1, SkillLearning.effective(c.stats, state.progress(), moonSkill));
        var moonParams = WeaponSkills.skill(SkillParameters.forCast(p.getUUID(), moonSkill, moonRank, c.stats.parameters), state.progress(), c.stats);
        double echoCoeff = moonParams.get("damage", 0) * TowerBattleCard.SUNMOON_MOON_ECHO.ratio("echo");
        if (echoCoeff <= 0) return;

        // Inherit the active Sun Heart damage snapshot on the triggering Sun cast.
        Stats echoStats = c.stats.copy();
        if (echoStats.modifiers.getOrDefault("sunMoonDamageApplied", 0d) <= 0) {
            int cycleRank = echoStats.modifiers.getOrDefault("sunMoonCycleRank", 1d).intValue();
            double sunDmg = SunMoonRules.sunDamage(cycleRank, view.sunMoon.stacks);
            echoStats.modifier("final").mut += sunDmg;
            echoStats.modifiers.put("sunMoonDamageApplied", sunDmg);
        }
        // Clear any reverse penalty so the follow-up echo deals full damage.
        double prevPenalty = echoStats.modifiers.getOrDefault("sunMoonPenaltyApplied", 1d);
        if (prevPenalty > 0 && prevPenalty < 1.0) {
            echoStats.modifier("skillProduct").alls /= prevPenalty;
            echoStats.modifiers.put("sunMoonPenaltyApplied", 1.0);
        }

        var echoCast = new SkillCast();
        echoCast.id = UUID.randomUUID().toString();
        echoCast.skill = moonSkill;
        echoCast.parameters = moonParams;
        echoCast.stats = echoStats;
        echoCast.equipment = c.equipment;
        echoCast.weapon = c.weapon;
        echoCast.origin = p.getEyePosition().add(0, -0.2, 0);
        Vec3 aimDir = p.getLookAngle().lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : p.getLookAngle().normalize();
        echoCast.direction = aimDir;
        echoCast.center = echoCast.origin;
        long now = ClientUi.now();
        echoCast.start = echoCast.stageStart = now;
        echoCast.speed = c.speed;
        echoCast.dimension = c.dimension;
        echoCast.session = c.session;
        echoCast.generation = c.generation;
        echoCast.job = c.job;
        echoCast.instance = c.instance;

        LivingEntity target = findHomingTarget(p, 20.0);
        Vec3 dir = target != null ? target.getBoundingBox().getCenter().subtract(echoCast.origin).normalize() : aimDir;

        var echo = new MoonEcho();
        echo.cast = echoCast;
        echo.visual = UUID.randomUUID().toString();
        echo.pos = echoCast.origin;
        echo.velocity = dir.scale(24.0);
        echo.target = target;
        if (target != null) {
            var ctx = state.context(target);
            echo.targetGen = ctx == null ? -1L : ctx.life.generation;
        }
        echo.lastTick = now;
        echo.expiresAt = now + 1400L;
        echo.radius = moonParams.get("radius", 3.0);
        echo.coeff = echoCoeff;
        echoes.add(echo);

        var motion = new JsonObject();
        motion.addProperty("sunMoonRadius", echo.radius);
        motion.addProperty("velocity", 24.0);
        motion.addProperty("range", 24.0);
        motion.addProperty("millis", 1000L);
        MageClient.INSTANCE.visual(echoCast, echo.visual, "START", echo.pos, dir, 1600L, 0, motion);
    }

    private static LivingEntity findHomingTarget(LivingEntity player, double range) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle().normalize();
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        for (var e : SkillController.INSTANCE.targets(new AABB(eye, eye).inflate(range))) {
            Vec3 center = e.getBoundingBox().getCenter();
            double dist = center.distanceTo(eye);
            if (dist > range || !SkillController.INSTANCE.sight(eye, center)) continue;
            double dot = center.subtract(eye).normalize().dot(look);
            double score = dist * (1.5 - Math.max(0.0, dot));
            if (score < bestScore) {
                bestScore = score;
                best = e;
            }
        }
        return best;
    }

    static void install() {
        for (String id : SunMoonSkills.ALL) if (!id.equals(SunMoonSkills.CYCLE)) MageClient.register(id, SunMoonClient::start);
    }

    private static void start(SkillCast c) {
        var p = Minecraft.getInstance().player;
        String id = c.skill.id;
        if (id.equals(SunMoonSkills.MOON)) {
            MageClient.INSTANCE.projectile(c);
            return;
        }
        if (id.equals(SunMoonSkills.REFLUX)) {
            JsonObject motion = null;
            long dur = 800L;
            if (TowerBattleCard.SUNMOON_REFLUX_LEYLINES.held(c.stats)) {
                dur = TowerBattleCard.SUNMOON_REFLUX_LEYLINES.millis("duration");
                motion = new JsonObject();
                motion.addProperty("sunMoonRadius", TowerBattleCard.SUNMOON_REFLUX_LEYLINES.value("radius"));
                motion.addProperty("leylineDurationMs", dur);
            }
            MageClient.INSTANCE.visual(c, UUID.randomUUID().toString(), "START", p.position(), Vec3.ZERO, dur, 0, motion);
            MageClient.INSTANCE.finished(c);
            return;
        }
        if (id.equals(SunMoonSkills.STARFALL) && TowerBattleCard.SUNMOON_STARFALL_AURORA.held(c.stats)) {
            Stats copy = c.stats.copy();
            double buildup = TowerBattleCard.SUNMOON_STARFALL_AURORA.ratio("buildup");
            for (int e = 0; e < 4; e++) ElementAccumulation.addConversion(copy, e, ElementAccumulation.Kind.FREEZE, buildup);
            c.stats = copy;
        }
        var aim = SkillTargeting.ground(p, c.parameters.get("range", 16), SkillController::canTarget);
        var a = new Area();
        a.cast = c;
        a.visual = UUID.randomUUID().toString();
        a.point = aim.point();
        if (id.equals(SunMoonSkills.PARADOX) && aim.target() != null) {
            var target = SkillsClient.current().context(aim.target());
            if (target != null) {
                a.target = aim.target();
                a.generation = target.life.generation;
            }
        }
        long delay = (long) (c.parameters.get("impactDelay", 0) * 1000);
        a.next = ClientUi.now() + delay;
        JsonObject motion = new JsonObject();
        motion.addProperty("sunMoonRadius", c.parameters.get("radius", id.equals(SunMoonSkills.PARADOX) ? 2.5 : 3.0));
        motion.addProperty("impactDelayMs", delay);
        motion.addProperty("liftHeight", c.parameters.get("liftHeight", 2.0));
        if (id.equals(SunMoonSkills.CORONA) && TowerBattleCard.SUNMOON_CORONA_COLLAPSE.held(c.stats)) {
            motion.addProperty("noBlur", true);
        }
        if (a.target != null) {
            motion.addProperty("target", a.target.getStringUUID());
            motion.addProperty("targetEntity", a.target.getId());
            motion.addProperty("targetFeet", true);
            motion.addProperty("range", 0);
            motion.addProperty("velocity", 0);
            motion.addProperty("millis", Math.max(100L, delay));
        }
        areas.add(a);
        MageClient.INSTANCE.visual(c, a.visual, "START", a.point, p.getEyePosition().subtract(a.point), areaDuration(c), 0, motion);
    }

    private static long areaDuration(SkillCast c) {
        if (!c.skill.id.equals(SunMoonSkills.STARFALL)) return 2000L;
        long delay = (long) Math.ceil(c.parameters.get("impactDelay", .25) * 1000);
        int stars = Math.max(1, (int) c.parameters.get("slashes", 8));
        long interval = Math.max(50L, (long) Math.ceil(c.parameters.get("interval", .25) * 1000));
        return Math.max(2600L, delay + stars * interval + 650L);
    }

    static void tick() {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        long now = ClientUi.now();
        tickAreas(mc, now);
        tickEmbers(mc, now);
        tickEchoes(mc, now);
    }

    private static void tickAreas(Minecraft mc, long now) {
        for (var a : new ArrayList<>(areas)) {
            var c = a.cast;
            if (!MageClient.INSTANCE.valid(c)) {
                areas.remove(a);
                continue;
            }
            if (a.target != null) {
                var target = SkillsClient.current().context(a.target);
                if (a.target.isAlive() && target != null && !target.life.dead && target.life.generation == a.generation)
                    a.point = a.target.position();
                else a.target = null;
            }
            if (c.skill.id.equals(SunMoonSkills.CORONA) && !a.pulled && TowerBattleCard.SUNMOON_CORONA_COLLAPSE.held(c.stats)) {
                a.pulled = true;
                pullToward(a.point, c.parameters.get("radius", 4.0) * 1.15);
            }
            if (now < a.next) continue;
            if (!mc.level.hasChunkAt(BlockPos.containing(a.point))) {
                areas.remove(a);
                MageClient.INSTANCE.finished(c);
                continue;
            }
            String id = c.skill.id;
            double damage = c.parameters.get("damage", 0);
            double radius = c.parameters.get("radius", 3);
            double lift = c.parameters.get("liftHeight", 2);
            long duration = areaDuration(c);
            if (id.equals(SunMoonSkills.STARFALL)) {
                int stars = (int) c.parameters.get("slashes", 8);
                long starInterval = Math.max(50L, (long) (c.parameters.get("interval", .25) * 1000));
                boolean aurora = TowerBattleCard.SUNMOON_STARFALL_AURORA.held(c.stats);
                long debuffMs = aurora ? TowerBattleCard.SUNMOON_STARFALL_AURORA.millis("debuff") : 0L;
                while (a.index < stars && now >= a.next) {
                    hit(a.cast, a.point, radius, damage / stars, 2, aurora, now + debuffMs);
                    a.index++;
                    a.next += starInterval;
                    MageClient.INSTANCE.visual(c, a.visual, "SEGMENT", a.point, Vec3.ZERO, duration, ++a.sequence, null);
                }
                if (a.index < stars) continue;
            } else if (id.equals(SunMoonSkills.PARADOX)) {
                double ice = c.parameters.get("elementSplit", .5);
                hit(a.cast, a.point, radius, damage * ice, 2, false, 0L);
                hit(a.cast, a.point, radius, damage * (1 - ice), 1, false, 0L);
            } else {
                Vec3 center = id.equals(SunMoonSkills.CORONA) ? a.point.add(0, lift, 0) : a.point;
                hit(a.cast, center, radius, damage, 1, false, 0L);
                if (id.equals(SunMoonSkills.FLARE) && TowerBattleCard.SUNMOON_FLARE_EMBER.held(c.stats)) {
                    spawnEmberZone(c, a.point, radius, damage, now);
                }
            }
            Vec3 point = id.equals(SunMoonSkills.CORONA) ? a.point.add(0, lift, 0) : a.point;
            MageClient.INSTANCE.visual(c, a.visual, "FINISH", point, Vec3.ZERO, duration, ++a.sequence, null);
            areas.remove(a);
            MageClient.INSTANCE.finished(c);
        }
    }

    private static void spawnEmberZone(SkillCast c, Vec3 point, double radius, double baseDamage, long now) {
        long durMs = TowerBattleCard.SUNMOON_FLARE_EMBER.millis("duration");
        long intMs = Math.max(100L, TowerBattleCard.SUNMOON_FLARE_EMBER.millis("interval"));
        var z = new EmberZone();
        z.cast = c;
        z.visual = UUID.randomUUID().toString();
        z.point = point;
        z.radius = radius;
        z.tickCoeff = baseDamage * TowerBattleCard.SUNMOON_FLARE_EMBER.ratio("tick");
        z.intervalMs = intMs;
        z.endAt = now + durMs;
        z.nextTickAt = now + intMs;
        embers.add(z);
        TrainingCombat.hold(c.id, "sunmoon_ember", z.endAt + 500L);
    }

    private static void tickEmbers(Minecraft mc, long now) {
        for (var z : new ArrayList<>(embers)) {
            if (!MageClient.INSTANCE.valid(z.cast) || !mc.level.hasChunkAt(BlockPos.containing(z.point))) {
                TrainingCombat.release(z.cast.id, "sunmoon_ember");
                embers.remove(z);
                continue;
            }
            while (now >= z.nextTickAt && z.nextTickAt <= z.endAt + 30L) {
                z.nextTickAt += z.intervalMs;
                Stats live = SkillController.INSTANCE.snapshot();
                live.modifiers.put("sourceLevel", (double) SkillsClient.current().progress().level);
                // Live Sun Heart bonus, no reverse penalty on lingering embers.
                int heart = live.modifiers.getOrDefault("sunMoonHeart", 0d).intValue();
                int stacks = Math.clamp(live.modifiers.getOrDefault("sunMoonStacks", 0d).intValue(), 0, SunMoonRules.stackCap(live));
                if (heart == 1 && stacks > 0) {
                    int cycleRank = live.modifiers.getOrDefault("sunMoonCycleRank", 1d).intValue();
                    double sunDmg = SunMoonRules.sunDamage(cycleRank, stacks);
                    live.modifier("final").mut += sunDmg - live.modifiers.getOrDefault("sunMoonDamageApplied", 0d);
                    live.modifiers.put("sunMoonDamageApplied", sunDmg);
                }
                var tickCast = new SkillCast();
                tickCast.id = z.cast.id;
                tickCast.skill = z.cast.skill;
                tickCast.parameters = z.cast.parameters;
                tickCast.stats = live;
                tickCast.equipment = z.cast.equipment;
                tickCast.weapon = z.cast.weapon;
                tickCast.start = z.cast.start;
                tickCast.dimension = z.cast.dimension;
                tickCast.session = z.cast.session;
                tickCast.generation = z.cast.generation;
                tickCast.job = z.cast.job;
                tickCast.instance = z.cast.instance;
                hit(tickCast, z.point, z.radius, z.tickCoeff, 1, false, 0L);
                var motion = new JsonObject();
                motion.addProperty("sunMoonRadius", z.radius * 0.85);
                motion.addProperty("impactDelayMs", 0L);
                MageClient.INSTANCE.visual(z.cast, z.visual, "FINISH", z.point, Vec3.ZERO, 950L, ++z.sequence, motion);
            }
            if (now > z.endAt + 30L) {
                TrainingCombat.release(z.cast.id, "sunmoon_ember");
                embers.remove(z);
            }
        }
    }

    private static void tickEchoes(Minecraft mc, long now) {
        for (var e : new ArrayList<>(echoes)) {
            if (!MageClient.INSTANCE.valid(e.cast)) {
                echoes.remove(e);
                continue;
            }
            double dt = Math.clamp((now - e.lastTick) / 1000.0, 0.0, 0.1);
            e.lastTick = now;
            if (e.target != null) {
                var ctx = SkillsClient.current().context(e.target);
                if (e.target.isAlive() && ctx != null && !ctx.life.dead && ctx.life.generation == e.targetGen) {
                    Vec3 aim = e.target.getBoundingBox().getCenter().subtract(e.pos);
                    if (aim.lengthSqr() > 1e-4) e.velocity = aim.normalize().scale(24.0);
                } else {
                    e.target = null;
                }
            }
            e.pos = e.pos.add(e.velocity.scale(dt));
            boolean detonate = now >= e.expiresAt;
            if (!detonate && e.target != null && e.target.getBoundingBox().inflate(0.7).contains(e.pos)) {
                detonate = true;
            }
            if (!detonate) {
                for (var candidate : SkillController.INSTANCE.targets(new AABB(e.pos, e.pos).inflate(0.65))) {
                    if (candidate.getBoundingBox().inflate(0.45).contains(e.pos)) {
                        detonate = true;
                        break;
                    }
                }
            }
            if (detonate) {
                hit(e.cast, e.pos, e.radius, e.coeff, 2, false, 0L);
                MageClient.INSTANCE.visual(e.cast, e.visual, "FINISH", e.pos, Vec3.ZERO, 1200L, ++e.sequence, null);
                echoes.remove(e);
            }
        }
    }

    private static void pullToward(Vec3 center, double pullRadius) {
        var state = SkillsClient.current();
        var moves = new JsonArray();
        for (var target : SkillController.INSTANCE.targets(new AABB(center, center).inflate(pullRadius, 4.0, pullRadius))) {
            var known = state.context(target);
            if (known != null && "BOSS".equals(known.kind)) continue;
            if (target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) >= 1.0) continue;
            Vec3 delta = new Vec3(center.x - target.getX(), 0, center.z - target.getZ());
            double dist = delta.length();
            if (dist > 0.7 && dist <= pullRadius) {
                double step = Math.min(dist - 0.5, pullRadius * 0.65);
                Vec3 dest = target.position().add(delta.scale(step / dist));
                var m = new JsonObject();
                m.addProperty("target", target.getUUID().toString());
                m.addProperty("x", dest.x);
                m.addProperty("y", dest.y);
                m.addProperty("z", dest.z);
                moves.add(m);
            }
        }
        if (!moves.isEmpty()) {
            var packet = new JsonObject();
            packet.addProperty("session", state.session());
            packet.addProperty("generation", state.own().generation);
            packet.add("moves", moves);
            state.send("careerMotion", packet);
        }
    }

    private static void hit(SkillCast cast, Vec3 center, double radius, double coefficient, int element, boolean shredFireIce, long shredUntil) {
        for (var target : SkillController.INSTANCE.targets(new AABB(center, center).inflate(radius))) {
            var b = target.getBoundingBox();
            Vec3 near = new Vec3(Math.clamp(center.x, b.minX, b.maxX), Math.clamp(center.y, b.minY, b.maxY), Math.clamp(center.z, b.minZ, b.maxZ));
            if (near.distanceToSqr(center) <= radius * radius && SkillController.INSTANCE.sight(center.add(0, .08, 0), target.getBoundingBox().getCenter())) {
                boolean landed = MageClient.INSTANCE.issue(cast, target, coefficient, element, "", "");
                if (landed && shredFireIce && shredUntil > 0L) {
                    long[] timers = resistShredUntil.computeIfAbsent(target.getUUID(), k -> new long[4]);
                    timers[1] = Math.max(timers[1], shredUntil);
                    timers[2] = Math.max(timers[2], shredUntil);
                }
            }
        }
    }

    static void cancel(String id) {
        areas.removeIf(a -> a.cast.id.equals(id));
    }

    static void clear() {
        for (var z : embers) TrainingCombat.release(z.cast.id, "sunmoon_ember");
        areas.clear();
        embers.clear();
        echoes.clear();
        resistShredUntil.clear();
    }
}
