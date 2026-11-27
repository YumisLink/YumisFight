package cn.yumis.fight.modules.skills.client;

import cn.yumis.fight.modules.combat.api.CombatEffect;
import cn.yumis.fight.modules.combat.api.client.DamageAnalysis;
import cn.yumis.fight.modules.dungeons.api.Target;
import cn.yumis.fight.modules.dungeons.api.TowerBattleCard;
import cn.yumis.fight.modules.dungeons.api.TowerCardEffects;
import cn.yumis.fight.modules.presentation.api.client.ClientUi;
import cn.yumis.fight.modules.presentation.api.client.EffectParticles;
import cn.yumis.fight.modules.skills.api.SkillCast;
import cn.yumis.fight.modules.skills.api.SkillCatalog;
import cn.yumis.fight.modules.skills.api.SkillPresentation;
import cn.yumis.fight.modules.skills.api.SkillVisualRules;
import cn.yumis.fight.modules.skills.api.WeaponSkills;
import cn.yumis.fight.modules.skills.api.client.SkillController;
import cn.yumis.fight.modules.skills.api.client.SkillsClient;
import cn.yumis.fight.modules.skills.api.client.TrainingCombat;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/**
 * Client-side Swordmaster mythic card runtime:
 * - Unlimited-stacking Katana Heavy Bleed DoT (coalesced per target per tick);
 * - Expanding 20-block Draw Sword arc ring wave;
 * - Rapid 3/6-wave Illusion Sword Dance crosshair barrage for Draw Sword;
 * - Homing Raging Dragon whirlwinds with synced visual tracks.
 */
public final class SwordmasterClient {
    private static final long DRAW_ARC_DURATION_MS = 750L;
    private static final double DRAW_ARC_START_RADIUS = 1.5;
    private static final double DRAW_BARRAGE_BASE_RANGE = 14.0;
    private static final double DRAW_BARRAGE_BASE_WIDTH = 4.4;
    private static final double DRAW_BARRAGE_HEIGHT = 2.8;
    private static final long WHIRLWIND_DURATION_MS = 3500L;
    private static final long WHIRLWIND_TICK_MS = 450L;
    private static final long WHIRLWIND_VISUAL_SYNC_MS = 250L;
    private static final double WHIRLWIND_SPEED = 7.0;
    private static final double WHIRLWIND_RADIUS = 2.0;
    private static final double WHIRLWIND_SEEK_RANGE = 18.0;

    private static final class HeavyBleed {
        final UUID targetId;
        final long generation;
        final String dimension, instance;
        final double fractionPerTick;
        long nextAt;
        int ticksLeft;

        HeavyBleed(UUID targetId, long generation, String dimension, String instance,
                   double fractionPerTick, long nextAt, int ticksLeft) {
            this.targetId = targetId;
            this.generation = generation;
            this.dimension = dimension;
            this.instance = instance;
            this.fractionPerTick = fractionPerTick;
            this.nextAt = nextAt;
            this.ticksLeft = ticksLeft;
        }
    }

    private static final class DrawArcWave {
        final SkillCast source;
        final Vec3 origin;
        final String dimension;
        final long generation, started;
        final double maxRadius, coefficient;
        final Set<UUID> hit = new HashSet<>();

        DrawArcWave(SkillCast source, Vec3 origin, String dimension, long generation,
                    long started, double maxRadius, double coefficient) {
            this.source = source;
            this.origin = origin;
            this.dimension = dimension;
            this.generation = generation;
            this.started = started;
            this.maxRadius = maxRadius;
            this.coefficient = coefficient;
        }
    }

    private static final class DrawVolley {
        final SkillCast source;
        final Vec3 fallbackDir;
        final String dimension;
        final long generation, intervalMs;
        final double range, width, height, coefficient;
        int remaining;
        long nextAt;

        DrawVolley(SkillCast source, Vec3 fallbackDir, String dimension, long generation,
                   int count, long intervalMs, long startAt,
                   double range, double width, double height, double coefficient) {
            this.source = source;
            this.fallbackDir = fallbackDir;
            this.dimension = dimension;
            this.generation = generation;
            this.remaining = count;
            this.intervalMs = intervalMs;
            this.nextAt = startAt;
            this.range = range;
            this.width = width;
            this.height = height;
            this.coefficient = coefficient;
        }
    }

    private static final class DragonWhirlwind {
        final String id;
        final SkillCast source;
        final String dimension, weaponType;
        final long generation, started, expires;
        final double coefficient;
        final int color;
        Vec3 pos, dir;
        UUID lockedTarget;
        long lastMoveAt, nextHitAt, nextVisualAt;
        int sequence = -1;

        DragonWhirlwind(String id, SkillCast source, Vec3 pos, Vec3 dir, String dimension,
                        long generation, long started, double coefficient, int color, String weaponType) {
            this.id = id;
            this.source = source;
            this.pos = pos;
            this.dir = dir;
            this.dimension = dimension;
            this.generation = generation;
            this.started = started;
            this.expires = started + WHIRLWIND_DURATION_MS;
            this.lastMoveAt = started;
            this.nextHitAt = started + 180L;
            this.nextVisualAt = started;
            this.coefficient = coefficient;
            this.color = color;
            this.weaponType = weaponType;
        }
    }

    private static final List<HeavyBleed> heavyBleeds = new ArrayList<>();
    private static final List<DrawArcWave> arcWaves = new ArrayList<>();
    private static final List<DrawVolley> drawVolleys = new ArrayList<>();
    private static final List<DragonWhirlwind> whirlwinds = new ArrayList<>();

    private SwordmasterClient() {}

    private static SkillsClient state() { return SkillsClient.current(); }

    public static void clear() {
        for (var w : whirlwinds) emitWhirlwindVisual(w, "CANCEL", null);
        heavyBleeds.clear();
        arcWaves.clear();
        drawVolleys.clear();
        whirlwinds.clear();
    }

    public static void applyHeavyBleed(LivingEntity entity, Target target, double currentLifeFraction) {
        if (entity == null || target == null || currentLifeFraction <= 0) return;
        double perTick = WeaponSkills.heavyBleedTickFraction(currentLifeFraction, target.boss());
        if (!(perTick > 0) || !Double.isFinite(perTick)) return;
        long now = ClientUi.now();
        heavyBleeds.add(new HeavyBleed(
            target.id, target.life.generation, target.dimension, target.instance,
            perTick, now + 1000L, WeaponSkills.heavyBleedTicks()
        ));
    }

    public static void spawnDrawArcWave(SkillCast cast, double coefficient) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || cast == null) return;
        long now = ClientUi.now();
        Vec3 origin = mc.player.position();
        SkillCast detached = detachCast(cast);
        double maxRadius = WeaponSkills.drawArcWaveDistance();
        arcWaves.add(new DrawArcWave(
            detached, origin, mc.level.dimension().identifier().toString(),
            state().own().generation, now, maxRadius, coefficient
        ));
        emitArcWaveVisual(cast, origin, maxRadius, now);
    }

    public static void spawnDrawBarrage(SkillCast cast) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || cast == null) return;
        long now = ClientUi.now();
        int count = WeaponSkills.drawBarrageCount(cast.stats);
        double rangeScale = TowerBattleCard.SM_DRAW_ARC_WAVE.held(cast.stats)
            ? 1.0 + TowerBattleCard.SM_DRAW_ARC_WAVE.ratio("range")
            : 1.0;
        long intervalMs = count >= 6 ? 60L : 75L;
        double coefficient = cast.parameters.get("damage", 1.0) * WeaponSkills.drawBarrageDamageRatio();
        drawVolleys.add(new DrawVolley(
            detachCast(cast), cast.direction, mc.level.dimension().identifier().toString(),
            state().own().generation, count, intervalMs, now,
            DRAW_BARRAGE_BASE_RANGE * rangeScale, DRAW_BARRAGE_BASE_WIDTH * rangeScale,
            DRAW_BARRAGE_HEIGHT, coefficient
        ));
    }

    public static void spawnDragonWhirlwind(SkillCast cast, Vec3 origin, Vec3 dir) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || cast == null) return;
        long now = ClientUi.now();
        Vec3 flat = horizontal(dir);
        Vec3 spawnPos = origin.add(flat.scale(1.1));
        int color = CareerClient.INSTANCE.visualColor(
            cast.stats,
            SkillPresentation.color(cast.skill, state().resources().buffs.getOrDefault(SkillCatalog.OVERDRIVE.id, 0L) > now)
        );
        double coefficient = cast.parameters.get("damage", 1.0) * WeaponSkills.dragonWhirlwindDamageRatio();
        var w = new DragonWhirlwind(
            UUID.randomUUID().toString(), detachCast(cast), spawnPos, flat,
            mc.level.dimension().identifier().toString(), state().own().generation,
            now, coefficient, color, cast.stats.weaponType
        );
        whirlwinds.add(w);
        emitWhirlwindVisual(w, "START", null);
        emitWhirlwindVisual(w, "TRAIL", null);
        w.nextVisualAt = now + WHIRLWIND_VISUAL_SYNC_MS;
    }

    public static void tick() {
        var mc = Minecraft.getInstance();
        if (!state().ready() || mc.player == null || mc.level == null || state().own().dead) {
            if (!heavyBleeds.isEmpty() || !arcWaves.isEmpty() || !drawVolleys.isEmpty() || !whirlwinds.isEmpty()) clear();
            return;
        }
        long now = ClientUi.now();
        String dim = mc.level.dimension().identifier().toString();
        long gen = state().own().generation;
        tickHeavyBleeds(mc, now, dim);
        tickArcWaves(mc, now, dim, gen);
        tickDrawVolleys(mc, now, dim, gen);
        tickWhirlwinds(mc, now, dim, gen);
    }

    private static void tickHeavyBleeds(Minecraft mc, long now, String dim) {
        if (heavyBleeds.isEmpty()) return;
        Map<UUID, Double> dueFractions = new LinkedHashMap<>();
        Map<UUID, Target> dueTargets = new HashMap<>();
        Map<UUID, LivingEntity> dueEntities = new HashMap<>();

        for (var it = heavyBleeds.iterator(); it.hasNext(); ) {
            HeavyBleed b = it.next();
            Target target = state().targets().get(b.targetId);
            var raw = target == null ? null : mc.level.getEntity(target.entityId);
            if (!(raw instanceof LivingEntity living) || !SkillController.canTarget(living)
                || target.life.dead || target.life.generation != b.generation
                || !dim.equals(b.dimension) || !state().own().instance.equals(b.instance)
                || (living instanceof cn.yumis.fight.modules.dungeons.api.TrainingSandbag bag && bag.timed() && bag.finished())) {
                it.remove();
                continue;
            }
            if (now >= b.nextAt) {
                dueFractions.merge(b.targetId, b.fractionPerTick, Double::sum);
                dueTargets.putIfAbsent(b.targetId, target);
                dueEntities.putIfAbsent(b.targetId, living);
                b.ticksLeft--;
                b.nextAt += 1000L;
                if (b.ticksLeft <= 0) it.remove();
            }
        }

        if (dueFractions.isEmpty()) return;
        var sourceStats = SkillController.INSTANCE.snapshot();
        for (var entry : dueFractions.entrySet()) {
            UUID id = entry.getKey();
            Target target = dueTargets.get(id);
            LivingEntity entity = dueEntities.get(id);
            double fraction = entry.getValue();
            if (!(fraction > 0) || target == null || entity == null) continue;
            double maxHealth = target.health(target.level(state().progress().level, state().encounterSync()));
            boolean critical = TowerCardEffects.forceCritical(sourceStats, state().predicted(target.life));
            if (critical) fraction *= TowerCardEffects.criticalMultiplier(sourceStats);
            JsonObject extra = new JsonObject();
            extra.addProperty(CombatEffect.NO_TRIGGERS, true);
            DamageAnalysis.attach(extra, new DamageAnalysis.Source("towerCard/sm_heavy_bleed", "重度流血"));
            if (target.training() && !target.timedTraining) {
                double[] comps = new double[]{fraction * maxHealth, 0, 0, 0, 0};
                String root = "heavy_bleed/" + id + "/" + now;
                TrainingCombat.begin(root, "重度流血");
                TrainingCombat.report(root, "重度流血", target, comps, fraction * maxHealth, 0xd81f38, critical, extra);
                TrainingCombat.actionFinished(root);
            } else {
                state().report(target.life, fraction, false, maxHealth, 0xd81f38, critical, extra);
            }
            Vec3 center = entity.getBoundingBox().getCenter();
            for (int i = 0; i < 5; i++) {
                double ang = (now * 0.01 + i * 1.2566);
                EffectParticles.dust(center.add(Math.cos(ang) * 0.45, (i - 2) * 0.14, Math.sin(ang) * 0.45), 0xd81f38, 0.75f);
            }
        }
    }

    private static void tickArcWaves(Minecraft mc, long now, String dim, long gen) {
        if (arcWaves.isEmpty()) return;
        for (var it = arcWaves.iterator(); it.hasNext(); ) {
            DrawArcWave wave = it.next();
            if (wave.generation != gen || !wave.dimension.equals(dim)) {
                it.remove();
                continue;
            }
            double u = Math.clamp((now - wave.started) / (double) DRAW_ARC_DURATION_MS, 0.0, 1.0);
            double radius = DRAW_ARC_START_RADIUS + (wave.maxRadius - DRAW_ARC_START_RADIUS) * u;
            Vec3 eyeOrigin = wave.origin.add(0, 0.8, 0);
            AABB box = new AABB(wave.origin, wave.origin).inflate(radius + 1.5, 3.5, radius + 1.5);
            for (LivingEntity target : SkillController.INSTANCE.targets(box)) {
                if (wave.hit.contains(target.getUUID())) continue;
                Vec3 center = target.getBoundingBox().getCenter();
                double dx = center.x - wave.origin.x, dz = center.z - wave.origin.z;
                double limit = radius + target.getBbWidth() * 0.5;
                if (dx * dx + dz * dz <= limit * limit && Math.abs(center.y - eyeOrigin.y) <= 3.0
                    && SkillController.INSTANCE.sight(eyeOrigin, center)) {
                    if (SkillController.INSTANCE.hitCast(wave.source, target, wave.coefficient, 0.25)) {
                        wave.hit.add(target.getUUID());
                    }
                }
            }
            if (u >= 1.0) it.remove();
        }
    }

    private static void tickDrawVolleys(Minecraft mc, long now, String dim, long gen) {
        if (drawVolleys.isEmpty()) return;
        for (var it = drawVolleys.iterator(); it.hasNext(); ) {
            DrawVolley v = it.next();
            if (v.generation != gen || !v.dimension.equals(dim)) {
                it.remove();
                continue;
            }
            while (v.remaining > 0 && now >= v.nextAt) {
                Vec3 look = mc.player.getLookAngle();
                Vec3 aim = look.lengthSqr() < 1e-4 ? v.fallbackDir : look.normalize();
                Vec3 start = mc.player.position().add(0, 0.8, 0);
                SkillController.INSTANCE.fireDanceWave(start, aim, v.range, v.width, v.height, v.source, v.coefficient);
                v.remaining--;
                v.nextAt += v.intervalMs;
            }
            if (v.remaining <= 0) it.remove();
        }
    }

    private static void tickWhirlwinds(Minecraft mc, long now, String dim, long gen) {
        if (whirlwinds.isEmpty()) return;
        for (var it = whirlwinds.iterator(); it.hasNext(); ) {
            DragonWhirlwind w = it.next();
            if (w.generation != gen || !w.dimension.equals(dim) || now >= w.expires) {
                emitWhirlwindVisual(w, "END", null);
                it.remove();
                continue;
            }
            double dt = Math.clamp((now - w.lastMoveAt) / 1000.0, 0.0, 0.15);
            w.lastMoveAt = now;

            LivingEntity target = findWhirlwindTarget(mc, w);
            if (target != null) {
                w.lockedTarget = target.getUUID();
                Vec3 to = target.position().subtract(w.pos);
                Vec3 flat = new Vec3(to.x, 0, to.z);
                if (flat.lengthSqr() > 0.09) {
                    Vec3 desired = flat.normalize();
                    w.dir = w.dir.scale(0.55).add(desired.scale(0.45));
                    w.dir = w.dir.lengthSqr() < 1e-4 ? desired : w.dir.normalize();
                }
            } else {
                w.lockedTarget = null;
            }

            if (dt > 0) {
                Vec3 next = w.pos.add(w.dir.scale(WHIRLWIND_SPEED * dt));
                var wall = mc.level.clip(new ClipContext(w.pos.add(0, 0.6, 0), next.add(0, 0.6, 0),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
                if (wall.getType() == HitResult.Type.MISS) {
                    var floor = mc.level.clip(new ClipContext(next.add(0, 1.5, 0), next.add(0, -2.5, 0),
                        ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
                    if (floor.getType() != HitResult.Type.MISS && Math.abs(floor.getLocation().y - w.pos.y) <= 1.6) {
                        next = new Vec3(next.x, floor.getLocation().y, next.z);
                    }
                    w.pos = next;
                }
            }

            if (now >= w.nextHitAt) {
                w.nextHitAt = now + WHIRLWIND_TICK_MS;
                AABB box = new AABB(w.pos, w.pos).inflate(WHIRLWIND_RADIUS + 0.8, 3.2, WHIRLWIND_RADIUS + 0.8);
                for (LivingEntity e : SkillController.INSTANCE.targets(box)) {
                    Vec3 c = e.getBoundingBox().getCenter();
                    double dx = c.x - w.pos.x, dz = c.z - w.pos.z;
                    double reach = WHIRLWIND_RADIUS + e.getBbWidth() * 0.5;
                    if (dx * dx + dz * dz <= reach * reach && Math.abs(c.y - (w.pos.y + 1.2)) <= 2.4) {
                        SkillController.INSTANCE.hitCast(w.source, e, w.coefficient, 0.12);
                    }
                }
            }

            if (now >= w.nextVisualAt) {
                w.nextVisualAt = now + WHIRLWIND_VISUAL_SYNC_MS;
                emitWhirlwindVisual(w, "TRAIL", target);
            }
        }
    }

    private static LivingEntity findWhirlwindTarget(Minecraft mc, DragonWhirlwind w) {
        if (w.lockedTarget != null) {
            for (LivingEntity e : SkillController.INSTANCE.targets(new AABB(w.pos, w.pos).inflate(WHIRLWIND_SEEK_RANGE + 2, 5, WHIRLWIND_SEEK_RANGE + 2))) {
                if (e.getUUID().equals(w.lockedTarget)) return e;
            }
        }
        LivingEntity best = null;
        double bestDist = WHIRLWIND_SEEK_RANGE * WHIRLWIND_SEEK_RANGE;
        for (LivingEntity e : SkillController.INSTANCE.targets(new AABB(w.pos, w.pos).inflate(WHIRLWIND_SEEK_RANGE, 5, WHIRLWIND_SEEK_RANGE))) {
            double d = e.position().subtract(w.pos).horizontalDistanceSqr();
            if (d < bestDist) {
                bestDist = d;
                best = e;
            }
        }
        return best;
    }

    private static void emitArcWaveVisual(SkillCast cast, Vec3 origin, double maxRadius, long now) {
        var p = Minecraft.getInstance().player;
        if (p == null) return;
        String id = UUID.randomUUID().toString();
        int color = CareerClient.INSTANCE.visualColor(
            cast.stats,
            SkillPresentation.color(cast.skill, state().resources().buffs.getOrDefault(SkillCatalog.OVERDRIVE.id, 0L) > now)
        );
        JsonObject j = new JsonObject();
        j.addProperty("caster", p.getStringUUID());
        j.addProperty("session", state().session());
        j.addProperty("generation", state().own().generation);
        j.addProperty("id", id);
        j.addProperty("kind", SkillVisualRules.SM_DRAW_ARC);
        j.addProperty("phase", "START");
        j.addProperty("sequence", 0);
        j.addProperty("seed", id.hashCode());
        j.addProperty("startedAt", state().serverNow());
        j.addProperty("at", state().serverNow());
        j.addProperty("duration", DRAW_ARC_DURATION_MS + 120L);
        j.addProperty("cosmeticOnly", true);
        j.addProperty("color", color);
        j.addProperty("horizontalScale", Math.clamp(maxRadius / 10.0, 0.5, 16.0));
        j.addProperty("weaponType", cast.stats.weaponType);
        j.addProperty("x", origin.x); j.addProperty("y", origin.y); j.addProperty("z", origin.z);
        j.addProperty("px", origin.x); j.addProperty("py", origin.y); j.addProperty("pz", origin.z);
        j.addProperty("dx", cast.direction.x); j.addProperty("dy", 0d); j.addProperty("dz", cast.direction.z);
        Effects.receive(j);
        state().send("visual", j);
    }

    private static void emitWhirlwindVisual(DragonWhirlwind w, String phase, LivingEntity target) {
        var p = Minecraft.getInstance().player;
        if (p == null || !state().ready()) return;
        long now = ClientUi.now();
        JsonObject j = new JsonObject();
        j.addProperty("caster", p.getStringUUID());
        j.addProperty("session", state().session());
        j.addProperty("generation", state().own().generation);
        j.addProperty("id", w.id);
        j.addProperty("kind", SkillVisualRules.SM_DRAGON_WHIRLWIND);
        j.addProperty("phase", phase);
        j.addProperty("sequence", phase.equals("END") || phase.equals("CANCEL") ? 4096 : ++w.sequence);
        j.addProperty("seed", w.id.hashCode());
        j.addProperty("startedAt", state().serverNow() - Math.max(0, now - w.started));
        j.addProperty("at", state().serverNow());
        j.addProperty("duration", WHIRLWIND_DURATION_MS + 400L);
        j.addProperty("cosmeticOnly", true);
        j.addProperty("color", w.color);
        j.addProperty("horizontalScale", WHIRLWIND_RADIUS);
        j.addProperty("weaponType", w.weaponType);
        j.addProperty("x", w.pos.x); j.addProperty("y", w.pos.y); j.addProperty("z", w.pos.z);
        j.addProperty("px", w.pos.x); j.addProperty("py", w.pos.y); j.addProperty("pz", w.pos.z);
        j.addProperty("dx", w.dir.x); j.addProperty("dy", 0d); j.addProperty("dz", w.dir.z);
        if (phase.equals("START") || phase.equals("TRAIL")) {
            long rem = Math.max(250L, w.expires - now);
            double dist = WHIRLWIND_SPEED * (rem / 1000.0);
            JsonObject motion = SkillController.motion(dist, rem, true);
            if (target != null && target.isAlive()) {
                motion.addProperty("target", target.getStringUUID());
                motion.addProperty("targetEntity", target.getId());
                motion.addProperty("targetFeet", true);
            }
            j.add("motion", motion);
        }
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
        Vec3 h = new Vec3(v.x, 0, v.z);
        return h.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : h.normalize();
    }
}
