package cn.yumis.fight.modules.skills.client;

import cn.yumis.fight.modules.combat.api.ActionStatus;
import cn.yumis.fight.modules.dungeons.api.TowerBattleCard;
import cn.yumis.fight.modules.dungeons.api.TowerCardEffects;
import cn.yumis.fight.modules.presentation.api.client.ClientUi;
import cn.yumis.fight.modules.skills.api.*;
import cn.yumis.fight.modules.skills.api.client.SkillController;
import cn.yumis.fight.modules.skills.api.client.SkillsClient;
import cn.yumis.fight.modules.skills.api.client.TrainingCombat;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Client-side Soul Bender mythic card runtime:
 * - Detached Tombstone storm (冥碑脱手, compatible with 碑林倾泻 20-round storm);
 * - Piercing 3-hit Hell Slash wave (鬼斩·炼狱);
 * - Piercing dark-moon crescent waves for Moonlight Slash (圆月剑气);
 * - Delayed dimensional rift explosions for Phantom Slash (次元断空);
 * - Keiga Ghost Step evasion invulnerability + AoE shadow counter-slash (鬼影步).
 */
public final class SoulBenderClient {
    private static final double HELL_WAVE_SPEED = 16.0;
    private static final double HELL_WAVE_WIDTH = 3.2;
    private static final double HELL_WAVE_HEIGHT = 2.8;
    private static final double MOON_WAVE_SPEED = 18.0;
    private static final double MOON_WAVE_HEIGHT = 2.6;
    private static final int RIFT_HIT_INDEX_BASE = 48;

    private static final class DetachedTombstone {
        final SkillCast cast;
        final String visualId, dimension;
        final long generation, started, intervalMs;
        final Vec3 center;
        final double radius, damage;
        final int totalRounds;
        int fired, sequence;
        long nextAt;

        DetachedTombstone(SkillCast cast, String visualId, String dimension, long generation, long started,
                          Vec3 center, double radius, double damage, int totalRounds, long intervalMs) {
            this.cast = cast;
            this.visualId = visualId;
            this.dimension = dimension;
            this.generation = generation;
            this.started = started;
            this.center = center;
            this.radius = radius;
            this.damage = damage;
            this.totalRounds = totalRounds;
            this.intervalMs = intervalMs;
            this.nextAt = started + intervalMs;
        }
    }

    private static final class PiercingWave {
        final SkillCast cast;
        final String visualId, visualKind, dimension;
        final long generation, started;
        final Vec3 origin, direction;
        final double range, speed, width, height, coefficient;
        final int maxHitsPerTarget;
        final boolean fullMoon;
        Vec3 position;
        double travelled;
        long nextStepAt;
        int sequence;
        final Map<UUID, Integer> hits = new HashMap<>();
        final Map<UUID, Long> nextHitAt = new HashMap<>();

        PiercingWave(SkillCast cast, String visualId, String visualKind, String dimension, long generation,
                     long started, Vec3 origin, Vec3 direction, double range, double speed, double width,
                     double height, double coefficient, int maxHitsPerTarget, boolean fullMoon) {
            this.cast = cast;
            this.visualId = visualId;
            this.visualKind = visualKind;
            this.dimension = dimension;
            this.generation = generation;
            this.started = started;
            this.origin = origin;
            this.position = origin;
            this.direction = direction;
            this.range = range;
            this.speed = speed;
            this.width = width;
            this.height = height;
            this.coefficient = coefficient;
            this.maxHitsPerTarget = maxHitsPerTarget;
            this.fullMoon = fullMoon;
            this.nextStepAt = started;
        }
    }

    private static final class PendingRift {
        final SkillCast cast;
        final String visualId, dimension;
        final UUID targetId;
        final long generation, started, explodeAt;
        final double radius, coefficient;
        Vec3 position;
        int sequence;

        PendingRift(SkillCast cast, String visualId, String dimension, UUID targetId, long generation,
                    long started, long explodeAt, Vec3 position, double radius, double coefficient) {
            this.cast = cast;
            this.visualId = visualId;
            this.dimension = dimension;
            this.targetId = targetId;
            this.generation = generation;
            this.started = started;
            this.explodeAt = explodeAt;
            this.position = position;
            this.radius = radius;
            this.coefficient = coefficient;
        }
    }

    private static final List<DetachedTombstone> tombstones = new ArrayList<>();
    private static final List<PiercingWave> waves = new ArrayList<>();
    private static final List<PendingRift> rifts = new ArrayList<>();
    private static final Set<String> riftSpawnedSegments = new HashSet<>();
    private static long nextGhostStepAt;
    private static int riftCounter;

    private SoulBenderClient() {}

    private static SkillsClient state() { return SkillsClient.current(); }

    public static void resetCooldowns() {
        nextGhostStepAt = 0;
    }

    public static void clear() {
        for (var t : tombstones) {
            emitTombstoneVisual(t, "CANCEL");
            TrainingCombat.release(t.cast.id, "sb_tomb");
        }
        for (var w : waves) {
            emitWaveVisual(w, "END");
            TrainingCombat.release(w.cast.id, w.visualId);
        }
        for (var r : rifts) {
            emitRiftVisual(r, "CANCEL");
            TrainingCombat.release(r.cast.id, r.visualId);
        }
        tombstones.clear();
        waves.clear();
        rifts.clear();
        riftSpawnedSegments.clear();
        nextGhostStepAt = 0;
        riftCounter = 0;
    }

    /** Starts an autonomous Tombstone storm at the cast origin (冥碑脱手). */
    public static void startDetachedTombstone(SkillCast source) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || source == null) return;
        long now = ClientUi.now();
        int rounds = (int) Math.max(1, Math.round(source.parameters.get("slashes", 10)));
        long gapMs = Math.max(50L, Math.round(source.parameters.get("interval", 0.2) * 1000.0 / Math.max(0.01, source.speed)));
        double radius = Math.max(0.5, source.parameters.get("range", 4.0));
        double damage = source.parameters.get("damage", 0.65);
        SkillCast detached = detachCast(source);
        detached.center = source.origin;
        String visualId = UUID.nameUUIDFromBytes((source.id + "/sb_tomb").getBytes(StandardCharsets.UTF_8)).toString();
        var storm = new DetachedTombstone(
            detached, visualId, mc.level.dimension().identifier().toString(), state().own().generation,
            now, source.origin, radius, damage, rounds, gapMs
        );
        tombstones.add(storm);
        TrainingCombat.hold(source.id, "sb_tomb", now + rounds * gapMs + 1000L);
        emitTombstoneVisual(storm, "START");
    }

    /** Launches a piercing 3-hit dark demonic wave from Soul Slash (鬼斩·炼狱). */
    public static void spawnHellSlashWave(SkillCast source, Vec3 direction, double fullChargeCoeff) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || source == null || fullChargeCoeff <= 0) return;
        long now = ClientUi.now();
        Vec3 dir = horizontal(direction);
        Vec3 origin = mc.player.position().add(0, 1.0, 0).add(dir.scale(0.6));
        double range = SoulBenderRules.hellSlashRange();
        double hitCoeff = SoulBenderRules.hellSlashHitCoefficient(fullChargeCoeff);
        int hits = SoulBenderRules.hellSlashHits();
        SkillCast detached = detachCast(source);
        detached.elementOverride = 3;
        String visualId = UUID.randomUUID().toString();
        var wave = new PiercingWave(
            detached, visualId, SkillVisualRules.SB_HELL_WAVE, mc.level.dimension().identifier().toString(),
            state().own().generation, now, origin, dir, range, HELL_WAVE_SPEED,
            HELL_WAVE_WIDTH, HELL_WAVE_HEIGHT, hitCoeff, hits, true
        );
        waves.add(wave);
        TrainingCombat.hold(source.id, visualId, now + (long) Math.ceil(range / HELL_WAVE_SPEED * 1000.0) + 1000L);
        emitWaveVisual(wave, "START");
        emitWaveVisual(wave, "RELEASE");
    }

    /** Launches a piercing dark-moon crescent wave for Moonlight Slash (圆月剑气). */
    public static void spawnMoonWave(SkillCast source, Vec3 direction, boolean fullMoon, double waveCoeff) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || source == null || waveCoeff <= 0) return;
        long now = ClientUi.now();
        Vec3 dir = horizontal(direction);
        Vec3 origin = mc.player.position().add(0, 1.05, 0).add(dir.scale(0.55));
        double range = Math.max(1.0, TowerBattleCard.SB_MOON_WAVE.value("range"));
        double width = fullMoon ? 4.4 : 3.2;
        SkillCast detached = detachCast(source);
        detached.elementOverride = 3;
        String visualId = UUID.randomUUID().toString();
        var wave = new PiercingWave(
            detached, visualId, SkillVisualRules.SB_MOON_WAVE, mc.level.dimension().identifier().toString(),
            state().own().generation, now, origin, dir, range, MOON_WAVE_SPEED,
            width, MOON_WAVE_HEIGHT, waveCoeff, 1, fullMoon
        );
        waves.add(wave);
        TrainingCombat.hold(source.id, visualId, now + (long) Math.ceil(range / MOON_WAVE_SPEED * 1000.0) + 1000L);
        emitWaveVisual(wave, "START");
        emitWaveVisual(wave, "RELEASE");
    }

    /** Schedules a delayed dimensional rift detonation on the first enemy hit per Phantom Slash dash (次元断空). */
    public static void schedulePhantomRift(SkillCast source, LivingEntity target) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || source == null || target == null) return;
        if (!source.skill.id.equals(CareerSkills.PHANTOM) || source.hitIndex >= RIFT_HIT_INDEX_BASE) return;
        if (!TowerBattleCard.SB_PHANTOM_RIFT.held(source.stats)) return;
        String dashKey = source.id + "#" + source.hitIndex;
        if (riftSpawnedSegments.size() > 256) riftSpawnedSegments.clear();
        if (!riftSpawnedSegments.add(dashKey)) return;
        double coeff = SoulBenderRules.phantomRiftBurstCoefficient(source.parameters);
        if (!(coeff > 0)) return;
        long now = ClientUi.now();
        long delayMs = Math.max(100L, TowerBattleCard.SB_PHANTOM_RIFT.millis("delay"));
        double radius = Math.max(0.5, TowerBattleCard.SB_PHANTOM_RIFT.value("radius"));
        SkillCast detached = detachCast(source);
        detached.hitIndex = RIFT_HIT_INDEX_BASE + Math.floorMod(riftCounter++, 64);
        detached.controlSeconds = 0;
        detached.elementOverride = 3;
        String visualId = UUID.randomUUID().toString();
        Vec3 pos = target.position();
        var rift = new PendingRift(
            detached, visualId, mc.level.dimension().identifier().toString(), target.getUUID(),
            state().own().generation, now, now + delayMs, pos, radius, coeff
        );
        rifts.add(rift);
        TrainingCombat.hold(source.id, visualId, now + delayMs + 900L);
        emitRiftVisual(rift, "START");
    }

    /** Triggered on player evasion: Keiga Ghost Step brief invulnerability + AoE shadow counter-slash (鬼影步). */
    public static void onEvade() {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !state().ready() || state().own().dead) return;
        var stats = SkillController.INSTANCE.snapshot();
        if (!TowerBattleCard.SB_GHOST_STEP.held(stats)) return;
        if (SkillLearning.effective(stats, state().progress(), SkillCatalog.require(CareerSkills.KEIGA)) <= 0) return;
        long now = ClientUi.now();
        if (now < nextGhostStepAt) return;
        nextGhostStepAt = now + Math.max(100L, TowerCardEffects.cooldownMillis(stats, TowerBattleCard.SB_GHOST_STEP.millis("cooldown")));

        long invulnMs = Math.max(50L, TowerBattleCard.SB_GHOST_STEP.millis("invuln"));
        SkillController.INSTANCE.acquireStatus("sb/ghost_step", ActionStatus.INVULNERABLE, now + invulnMs);

        Vec3 center = mc.player.position();
        double radius = Math.max(0.5, TowerBattleCard.SB_GHOST_STEP.value("radius"));
        double coeff = TowerBattleCard.SB_GHOST_STEP.ratio("damage");
        AABB box = new AABB(center, center).inflate(radius + 1.0, 3.5, radius + 1.0);
        for (LivingEntity target : SkillController.INSTANCE.targets(box)) {
            Vec3 tc = target.getBoundingBox().getCenter();
            double dx = tc.x - center.x, dz = tc.z - center.z;
            double reach = radius + target.getBbWidth() * 0.5;
            if (dx * dx + dz * dz <= reach * reach && Math.abs(tc.y - (center.y + 1.0)) <= 3.0
                && SkillController.INSTANCE.sight(center.add(0, 1.0, 0), tc)) {
                SkillController.INSTANCE.towerStrike(TowerBattleCard.SB_GHOST_STEP, target, stats, coeff, true, 3);
            }
        }
        emitGhostStepVisual(center, horizontal(mc.player.getLookAngle()), radius);
    }

    public static void tick(SkillBehaviors.Context context) {
        var mc = Minecraft.getInstance();
        if (!state().ready() || mc.player == null || mc.level == null || state().own().dead) {
            if (!tombstones.isEmpty() || !waves.isEmpty() || !rifts.isEmpty()) clear();
            return;
        }
        long now = ClientUi.now();
        String dim = mc.level.dimension().identifier().toString();
        long gen = state().own().generation;
        tickTombstones(context, now, dim, gen);
        tickWaves(context, now, dim, gen);
        tickRifts(mc, context, now, dim, gen);
    }

    private static void tickTombstones(SkillBehaviors.Context context, long now, String dim, long gen) {
        if (tombstones.isEmpty()) return;
        for (var it = tombstones.iterator(); it.hasNext(); ) {
            DetachedTombstone t = it.next();
            if (t.generation != gen || !t.dimension.equals(dim)) {
                emitTombstoneVisual(t, "CANCEL");
                TrainingCombat.release(t.cast.id, "sb_tomb");
                it.remove();
                continue;
            }
            TrainingCombat.touch(t.cast.id);
            while (t.fired < t.totalRounds && now >= t.nextAt) {
                t.cast.hit.clear();
                t.cast.center = t.center;
                t.cast.hitIndex = t.fired++;
                context.area(t.cast, t.radius, t.damage, false);
                emitTombstoneVisual(t, "HIT");
                t.nextAt += t.intervalMs;
            }
            if (t.fired >= t.totalRounds) {
                emitTombstoneVisual(t, "END");
                TrainingCombat.release(t.cast.id, "sb_tomb");
                it.remove();
            }
        }
    }

    private static void tickWaves(SkillBehaviors.Context context, long now, String dim, long gen) {
        if (waves.isEmpty()) return;
        for (var it = waves.iterator(); it.hasNext(); ) {
            PiercingWave w = it.next();
            if (w.generation != gen || !w.dimension.equals(dim)) {
                emitWaveVisual(w, "END");
                TrainingCombat.release(w.cast.id, w.visualId);
                it.remove();
                continue;
            }
            TrainingCombat.touch(w.cast.id);
            int budget = 20;
            boolean done = false;
            while (!done && now >= w.nextStepAt && budget-- > 0) {
                long stepAt = w.nextStepAt;
                w.nextStepAt += 50L;
                double stepDist = Math.min(w.speed * 0.05, Math.max(0.0, w.range - w.travelled));
                Vec3 from = w.position;
                Vec3 next = from.add(w.direction.scale(stepDist));
                if (stepDist <= 1e-4 || !SkillController.INSTANCE.sight(from.add(0, 0.2, 0), next.add(0, 0.2, 0))) {
                    done = true;
                    break;
                }
                w.position = next;
                w.travelled = Math.min(w.range, w.travelled + stepDist);
                w.cast.center = next;
                AABB box = new AABB(from, next).inflate(w.width * 0.5, w.height * 0.5, w.width * 0.5);
                for (LivingEntity target : SkillController.INSTANCE.targets(box)) {
                    int count = w.hits.getOrDefault(target.getUUID(), 0);
                    if (count >= w.maxHitsPerTarget) continue;
                    if (stepAt < w.nextHitAt.getOrDefault(target.getUUID(), 0L)) continue;
                    if (!SkillController.INSTANCE.sight(from, target.getBoundingBox().getCenter())) continue;
                    w.cast.hitIndex = 32 + count;
                    if (SkillController.INSTANCE.hitCast(w.cast, target, w.coefficient, 0.08)) {
                        w.hits.put(target.getUUID(), count + 1);
                        w.nextHitAt.put(target.getUUID(), stepAt + 50L);
                    }
                }
                if (w.travelled >= w.range - 1e-4) done = true;
            }
            if (done) {
                emitWaveVisual(w, "END");
                TrainingCombat.release(w.cast.id, w.visualId);
                it.remove();
            }
        }
    }

    private static void tickRifts(Minecraft mc, SkillBehaviors.Context context, long now, String dim, long gen) {
        if (rifts.isEmpty()) return;
        for (var it = rifts.iterator(); it.hasNext(); ) {
            PendingRift r = it.next();
            if (r.generation != gen || !r.dimension.equals(dim)) {
                emitRiftVisual(r, "CANCEL");
                TrainingCombat.release(r.cast.id, r.visualId);
                it.remove();
                continue;
            }
            TrainingCombat.touch(r.cast.id);
            var tracked = mc.level.getEntity(r.targetId);
            if (tracked instanceof LivingEntity living && living.isAlive()) {
                r.position = living.position();
            }
            if (now >= r.explodeAt) {
                r.cast.center = r.position;
                r.cast.hit.clear();
                AABB box = new AABB(r.position, r.position).inflate(r.radius + 1.0, Math.max(3.5, r.radius), r.radius + 1.0);
                for (LivingEntity target : SkillController.INSTANCE.targets(box)) {
                    Vec3 tc = target.getBoundingBox().getCenter();
                    double dx = tc.x - r.position.x, dz = tc.z - r.position.z;
                    double reach = r.radius + target.getBbWidth() * 0.5;
                    if (dx * dx + dz * dz <= reach * reach && Math.abs(tc.y - (r.position.y + 1.0)) <= Math.max(3.5, r.radius)
                        && SkillController.INSTANCE.sight(r.position.add(0, 1.0, 0), tc)) {
                        SkillController.INSTANCE.hitCast(r.cast, target, r.coefficient, 0.2);
                    }
                }
                emitRiftVisual(r, "FINISH");
                TrainingCombat.release(r.cast.id, r.visualId);
                it.remove();
            }
        }
    }

    private static void emitTombstoneVisual(DetachedTombstone t, String phase) {
        var p = Minecraft.getInstance().player;
        if (p == null || !state().ready()) return;
        long now = ClientUi.now();
        long dur = Math.max(2000L, t.totalRounds * t.intervalMs);
        JsonObject j = new JsonObject();
        j.addProperty("caster", p.getStringUUID());
        j.addProperty("session", state().session());
        j.addProperty("generation", state().own().generation);
        j.addProperty("id", t.visualId);
        j.addProperty("kind", CareerSkills.TOMBSTONE);
        j.addProperty("phase", phase);
        j.addProperty("sequence", phase.equals("END") || phase.equals("CANCEL") ? 4096 : t.sequence++);
        j.addProperty("seed", t.visualId.hashCode());
        j.addProperty("startedAt", state().serverNow() - Math.max(0, now - t.started));
        j.addProperty("at", state().serverNow());
        j.addProperty("duration", dur);
        j.addProperty("cosmeticOnly", true);
        j.addProperty("dual", true);
        j.addProperty("horizontalScale", Math.clamp(t.totalRounds / 10.0, 1.0, 4.0));
        j.addProperty("color", 0x88749e);
        j.addProperty("x", t.center.x); j.addProperty("y", t.center.y); j.addProperty("z", t.center.z);
        j.addProperty("px", t.center.x); j.addProperty("py", t.center.y); j.addProperty("pz", t.center.z);
        j.addProperty("dx", t.cast.direction.x); j.addProperty("dy", 0d); j.addProperty("dz", t.cast.direction.z);
        Effects.receive(j);
        state().send("visual", j);
    }

    private static void emitWaveVisual(PiercingWave w, String phase) {
        var p = Minecraft.getInstance().player;
        if (p == null || !state().ready()) return;
        long now = ClientUi.now();
        long travelMs = Math.max(100L, (long) Math.ceil(w.range / w.speed * 1000.0));
        JsonObject j = new JsonObject();
        j.addProperty("caster", p.getStringUUID());
        j.addProperty("session", state().session());
        j.addProperty("generation", state().own().generation);
        j.addProperty("id", w.visualId);
        j.addProperty("kind", w.visualKind);
        j.addProperty("phase", phase);
        j.addProperty("sequence", phase.equals("END") || phase.equals("CANCEL") ? 4096 : w.sequence++);
        j.addProperty("seed", w.visualId.hashCode());
        j.addProperty("startedAt", state().serverNow() - Math.max(0, now - w.started));
        j.addProperty("at", state().serverNow());
        j.addProperty("duration", travelMs + 420L);
        j.addProperty("cosmeticOnly", true);
        j.addProperty("sweep", w.fullMoon);
        j.addProperty("horizontalScale", Math.max(0.8, w.width / 3.2));
        j.addProperty("color", w.visualKind.equals(SkillVisualRules.SB_HELL_WAVE) ? 0xb56ee8 : 0xa86cf5);
        j.addProperty("x", w.origin.x); j.addProperty("y", w.origin.y); j.addProperty("z", w.origin.z);
        j.addProperty("px", w.position.x); j.addProperty("py", w.position.y); j.addProperty("pz", w.position.z);
        j.addProperty("dx", w.direction.x); j.addProperty("dy", 0d); j.addProperty("dz", w.direction.z);
        if (phase.equals("RELEASE")) {
            var motion = SkillController.motion(w.range, travelMs, false);
            motion.addProperty("at", state().serverNow());
            j.add("motion", motion);
        }
        Effects.receive(j);
        state().send("visual", j);
    }

    private static void emitRiftVisual(PendingRift r, String phase) {
        var p = Minecraft.getInstance().player;
        if (p == null || !state().ready()) return;
        long now = ClientUi.now();
        long delayMs = Math.max(100L, r.explodeAt - r.started);
        JsonObject j = new JsonObject();
        j.addProperty("caster", p.getStringUUID());
        j.addProperty("session", state().session());
        j.addProperty("generation", state().own().generation);
        j.addProperty("id", r.visualId);
        j.addProperty("kind", SkillVisualRules.SB_PHANTOM_RIFT);
        j.addProperty("phase", phase);
        j.addProperty("sequence", phase.equals("CANCEL") ? 4096 : r.sequence++);
        j.addProperty("seed", r.visualId.hashCode());
        j.addProperty("startedAt", state().serverNow() - Math.max(0, now - r.started));
        j.addProperty("at", state().serverNow());
        j.addProperty("duration", delayMs + 680L);
        j.addProperty("cosmeticOnly", true);
        j.addProperty("horizontalScale", Math.max(0.5, r.radius / 3.5));
        j.addProperty("color", 0x9e65ee);
        j.addProperty("x", r.position.x); j.addProperty("y", r.position.y); j.addProperty("z", r.position.z);
        j.addProperty("px", r.position.x); j.addProperty("py", r.position.y); j.addProperty("pz", r.position.z);
        j.addProperty("dx", r.cast.direction.x); j.addProperty("dy", 0d); j.addProperty("dz", r.cast.direction.z);
        Effects.receive(j);
        state().send("visual", j);
    }

    private static void emitGhostStepVisual(Vec3 center, Vec3 dir, double radius) {
        var p = Minecraft.getInstance().player;
        if (p == null || !state().ready()) return;
        String id = UUID.randomUUID().toString();
        long serverNow = state().serverNow();
        JsonObject j = new JsonObject();
        j.addProperty("caster", p.getStringUUID());
        j.addProperty("session", state().session());
        j.addProperty("generation", state().own().generation);
        j.addProperty("id", id);
        j.addProperty("kind", SkillVisualRules.SB_GHOST_STEP);
        j.addProperty("phase", "START");
        j.addProperty("sequence", 0);
        j.addProperty("seed", id.hashCode());
        j.addProperty("startedAt", serverNow);
        j.addProperty("at", serverNow);
        j.addProperty("duration", 580L);
        j.addProperty("cosmeticOnly", true);
        j.addProperty("horizontalScale", Math.max(0.5, radius / 4.0));
        j.addProperty("color", 0x8e56d9);
        j.addProperty("x", center.x); j.addProperty("y", center.y); j.addProperty("z", center.z);
        j.addProperty("px", center.x); j.addProperty("py", center.y); j.addProperty("pz", center.z);
        j.addProperty("dx", dir.x); j.addProperty("dy", 0d); j.addProperty("dz", dir.z);
        Effects.receive(j);
        state().send("visual", j);
    }

    private static SkillCast detachCast(SkillCast cast) {
        SkillCast copy = new SkillCast();
        copy.id = cast.id;
        copy.session = cast.session;
        copy.generation = cast.generation;
        copy.job = cast.job;
        copy.dimension = cast.dimension;
        copy.instance = cast.instance;
        copy.skill = cast.skill;
        copy.stats = cast.stats.copy();
        copy.parameters = cast.parameters;
        copy.equipment = cast.equipment == null ? null : cast.equipment.copy();
        copy.speed = cast.speed;
        copy.start = cast.start;
        copy.origin = cast.origin;
        copy.direction = cast.direction;
        copy.center = cast.center;
        return copy;
    }

    private static Vec3 horizontal(Vec3 v) {
        if (v == null) return new Vec3(0, 0, 1);
        Vec3 flat = new Vec3(v.x, 0, v.z);
        return flat.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : flat.normalize();
    }
}
