package cn.yumis.fight.modules.skills.client;

import cn.yumis.fight.modules.attributes.api.Stat;
import cn.yumis.fight.modules.attributes.api.Stats;
import cn.yumis.fight.modules.combat.api.Runtime;
import cn.yumis.fight.modules.dungeons.api.Target;
import cn.yumis.fight.modules.dungeons.api.TowerBattleCard;
import cn.yumis.fight.modules.presentation.api.client.ClientUi;
import cn.yumis.fight.modules.skills.api.CareerRules;
import cn.yumis.fight.modules.skills.api.CareerSkills;
import cn.yumis.fight.modules.skills.api.SkillCast;
import cn.yumis.fight.modules.skills.api.SkillVisualRules;
import cn.yumis.fight.modules.skills.api.client.SkillController;
import cn.yumis.fight.modules.skills.api.client.SkillsClient;
import cn.yumis.fight.modules.skills.api.client.TrainingCombat;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Client-side Berserker (狂战士) career mythic card runtime:
 * - 血气唤醒: accumulated blood shield on skill HP cost;
 * - 嗜血抓魂: 15s STR/damage buff + 8s blood shield on Blood Hand hit;
 * - 血魔巨爪: detached 10-block sweeping claw gathering enemies to endpoint with <50% HP execute & CD refund;
 * - 飞血巨刃 / 血刃突袭: detached 12-block piercing flying blood blade with guaranteed single/double detonation.
 */
public final class BerserkerClient {
    private static final long CLAW_SWEEP_MS = 350L;
    private static final long CLAW_STEP_MS = 50L;
    private static final double FLYING_BLADE_SPEED = 22.0;
    private static final long FLYING_BLADE_STEP_MS = 50L;
    private static final long DOUBLE_BLAST_GAP_MS = 200L;

    private static final class BloodClaw {
        final String visualId;
        final SkillCast source;
        final Vec3 origin, destination, direction;
        final double width, crushRadius, travelDistance;
        final String dimension;
        final long generation, started, crushAt;
        Vec3 pos;
        long nextStepAt;
        int visualSequence = -1;
        final Set<UUID> captured = new LinkedHashSet<>();

        BloodClaw(String visualId, SkillCast source, Vec3 origin, Vec3 destination, Vec3 direction,
                  double width, double crushRadius, double travelDistance, String dimension,
                  long generation, long started) {
            this.visualId = visualId;
            this.source = source;
            this.origin = origin;
            this.destination = destination;
            this.direction = direction;
            this.width = width;
            this.crushRadius = crushRadius;
            this.travelDistance = travelDistance;
            this.dimension = dimension;
            this.generation = generation;
            this.started = started;
            this.crushAt = started + CLAW_SWEEP_MS;
            this.pos = origin;
            this.nextStepAt = started;
        }
    }

    private static final class FlyingBlade {
        final String visualId;
        final SkillCast source;
        final Vec3 origin, endpoint, direction;
        final double width, blastRadius, travelDistance, blastDamageScale;
        final int blastCount;
        final String dimension;
        final long generation, started, travelMs;
        final Set<UUID> pierced;
        Vec3 pos;
        double travelled;
        long nextStepAt;
        boolean flightDone;
        boolean blastScheduled;
        Vec3 blastCenter;
        long nextBlastAt;
        int blastsFired;
        int visualSequence = -1;

        FlyingBlade(String visualId, SkillCast source, Vec3 origin, Vec3 endpoint, Vec3 direction,
                    double width, double blastRadius, double travelDistance, long travelMs,
                    int blastCount, double blastDamageScale, Set<UUID> pierced,
                    String dimension, long generation, long started) {
            this.visualId = visualId;
            this.source = source;
            this.origin = origin;
            this.endpoint = endpoint;
            this.direction = direction;
            this.width = width;
            this.blastRadius = blastRadius;
            this.travelDistance = travelDistance;
            this.travelMs = travelMs;
            this.blastCount = Math.max(1, blastCount);
            this.blastDamageScale = blastDamageScale;
            this.pierced = pierced;
            this.dimension = dimension;
            this.generation = generation;
            this.started = started;
            this.pos = origin;
            this.nextStepAt = started;
            this.blastCenter = endpoint;
        }

        void scheduleBlast(Vec3 center, long at) {
            if (blastScheduled) return;
            blastScheduled = true;
            blastCenter = center;
            nextBlastAt = at;
        }
    }

    private static final List<BloodClaw> claws = new ArrayList<>();
    private static final List<FlyingBlade> blades = new ArrayList<>();
    private static final Set<String> handBuffed = new HashSet<>();

    private BerserkerClient() {}

    private static SkillsClient state() { return SkillsClient.current(); }

    public static void clear() {
        for (var claw : claws) {
            emitClawVisual(claw, "CANCEL", claw.pos);
            TrainingCombat.release(claw.source.id, "blood_claw");
        }
        for (var blade : blades) {
            emitBladeVisual(blade, "CANCEL", blade.pos);
            TrainingCombat.release(blade.source.id, "flying_blade");
        }
        claws.clear();
        blades.clear();
        handBuffed.clear();
    }

    public static boolean hasCast(SkillCast cast) {
        if (cast == null) return false;
        for (var claw : claws) if (claw.source.id.equals(cast.id)) return true;
        for (var blade : blades) if (blade.source.id.equals(cast.id)) return true;
        return false;
    }

    /** 血气唤醒：每次技能消耗生命时，累加本次耗血量 400% 的血气护盾（上限 100% 最大生命，刷新 6 秒）。 */
    public static void applyBloodAwakeningShield(Runtime resources, double maxHealth, double spentHealth, long now) {
        if (resources == null || !(maxHealth > 0) || !(spentHealth > 0)) return;
        String key = "towerCard/" + TowerBattleCard.BERSERKER_BLOOD_AWAKENING.id();
        long until = resources.absorptionUntil.getOrDefault(key, 0L);
        double existing = until > now ? resources.absorption.getOrDefault(key, 0d) : 0d;
        double total = CareerRules.bloodAwakeningShieldAmount(existing, maxHealth, spentHealth);
        if (total > 0) {
            resources.absorption.put(key, total);
            resources.absorptionUntil.put(key, now + TowerBattleCard.BERSERKER_BLOOD_AWAKENING.millis("duration"));
        }
    }

    /** 嗜血抓魂：嗜魂之手命中后触发 15 秒「嗜血之力」及 15% 最大生命的 8 秒护盾（每次施放一次）。 */
    public static void onHandHit(SkillCast source) {
        if (source == null || !TowerBattleCard.BERSERKER_HAND_SIPHON.held(source.stats)) return;
        if (!handBuffed.add(source.id)) return;
        if (handBuffed.size() > 256) handBuffed.clear();
        long now = ClientUi.now();
        var resources = state().resources();
        String key = "towerCard/" + TowerBattleCard.BERSERKER_HAND_SIPHON.id();
        resources.buffs.put(key, now + TowerBattleCard.BERSERKER_HAND_SIPHON.millis("duration"));
        double maxHp = SkillController.INSTANCE.snapshot().get(Stat.HEALTH);
        if (maxHp > 0) {
            resources.absorption.put(key, maxHp * TowerBattleCard.BERSERKER_HAND_SIPHON.ratio("shield"));
            resources.absorptionUntil.put(key, now + TowerBattleCard.BERSERKER_HAND_SIPHON.millis("shieldsec"));
        }
        SkillController.INSTANCE.invalidateSnapshot();
        state().upload();
    }

    /** 血魔巨爪：脱手向前 10 格（受范围加成缩放）唤出巨型血魔之爪聚怪捏爆。 */
    public static void spawnBloodClaw(SkillCast cast) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || cast == null) return;
        long now = ClientUi.now();
        Vec3 dir = horizontal(cast.direction);
        Vec3 origin = cast.origin;
        double rangeScale = Math.max(0.5, cast.parameters.get("range", 4.0) / 4.0);
        double widthScale = Math.max(0.5, cast.parameters.get("width", 3.0) / 3.0);
        double sweepRange = TowerBattleCard.BERSERKER_HAND_CLAW.value("range") * rangeScale;
        double sweepWidth = TowerBattleCard.BERSERKER_HAND_CLAW.value("width") * widthScale;
        double crushRadius = Math.max(3.0, sweepWidth * 0.65);

        Vec3 dest = resolveForwardPoint(mc, origin, dir, sweepRange);
        double travelDist = new Vec3(dest.x - origin.x, 0, dest.z - origin.z).length();
        SkillCast detached = detachCast(cast);
        detached.contactGroup = "berserker_hand_claw";
        detached.controlSeconds = Math.max(0.0, cast.parameters.get("controlSeconds", 0.3));
        detached.hitIndex = 0;

        String visualId = UUID.nameUUIDFromBytes((cast.id + "/blood_claw").getBytes(StandardCharsets.UTF_8)).toString();
        var claw = new BloodClaw(
            visualId, detached, origin, dest, dir, sweepWidth, crushRadius, travelDist,
            mc.level.dimension().identifier().toString(), state().own().generation, now
        );
        claws.add(claw);
        TrainingCombat.hold(cast.id, "blood_claw", now + CLAW_SWEEP_MS + 900L);
        emitClawVisual(claw, "START", dest);
    }

    /** 飞血巨刃：脱手投掷飞行 12 格的巨型血剑，穿透沿途敌人并在首个目标或终点必定触发爆炸。 */
    public static void spawnFlyingBlade(SkillCast cast, Set<UUID> sharedPierced, int blastCount, double blastDamageScale) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || cast == null) return;
        long now = ClientUi.now();
        Vec3 dir = horizontal(cast.direction);
        Vec3 origin = cast.origin;
        double maxRange = TowerBattleCard.BERSERKER_BLADE_THROW.value("range");
        double width = Math.max(1.5, cast.parameters.get("width", 3.0));
        double blastRadius = Math.max(0.5, cast.parameters.get("blastRadius", 4.0));

        Vec3 endpoint = resolveForwardPoint(mc, origin, dir, maxRange);
        double travelDist = Math.max(0.5, new Vec3(endpoint.x - origin.x, 0, endpoint.z - origin.z).length());
        long travelMs = Math.max(100L, Math.round(travelDist / FLYING_BLADE_SPEED * 1000.0));

        SkillCast detached = detachCast(cast);
        String visualId = UUID.nameUUIDFromBytes((cast.id + "/flying_blade").getBytes(StandardCharsets.UTF_8)).toString();
        var blade = new FlyingBlade(
            visualId, detached, origin, endpoint, dir, width, blastRadius, travelDist, travelMs,
            blastCount, blastDamageScale, sharedPierced == null ? new HashSet<>() : sharedPierced,
            mc.level.dimension().identifier().toString(), state().own().generation, now
        );
        blades.add(blade);
        TrainingCombat.hold(cast.id, "flying_blade", now + travelMs + blastCount * DOUBLE_BLAST_GAP_MS + 900L);
        emitBladeVisual(blade, "START", endpoint);
    }

    /** 当「血刃突袭」与「飞血巨刃」同持时，突进段先命中敌人立即锁定爆点。 */
    public static void notifyBladeDashHit(String castId, Vec3 targetPos) {
        if (castId == null || targetPos == null) return;
        long now = ClientUi.now();
        for (var blade : blades) {
            if (blade.source.id.equals(castId)) {
                blade.scheduleBlast(targetPos, now + 180L);
            }
        }
    }

    public static void tick(SkillBehaviors.Context context) {
        var mc = Minecraft.getInstance();
        if (!state().ready() || mc.player == null || mc.level == null || state().own().dead) {
            if (!claws.isEmpty() || !blades.isEmpty()) clear();
            return;
        }
        long now = ClientUi.now();
        String dim = mc.level.dimension().identifier().toString();
        long gen = state().own().generation;
        tickClaws(mc, now, dim, gen);
        tickBlades(mc, now, dim, gen);
    }

    private static void tickClaws(Minecraft mc, long now, String dim, long gen) {
        if (claws.isEmpty()) return;
        for (var it = claws.iterator(); it.hasNext(); ) {
            var claw = it.next();
            if (claw.generation != gen || !claw.dimension.equals(dim)) {
                emitClawVisual(claw, "CANCEL", claw.pos);
                TrainingCombat.release(claw.source.id, "blood_claw");
                it.remove();
                continue;
            }
            TrainingCombat.touch(claw.source.id);
            while (now >= claw.nextStepAt && claw.nextStepAt < claw.crushAt) {
                double u = Math.clamp((claw.nextStepAt - claw.started) / (double) CLAW_SWEEP_MS, 0.0, 1.0);
                Vec3 from = claw.pos;
                Vec3 next = claw.origin.lerp(claw.destination, u);
                claw.pos = next;
                captureAlongSweep(mc, claw, from, next);
                if (!claw.captured.isEmpty()) sendGatherMotion(mc, claw.captured, next);
                claw.nextStepAt += CLAW_STEP_MS;
            }
            if (now >= claw.crushAt) {
                captureAlongSweep(mc, claw, claw.pos, claw.destination);
                claw.pos = claw.destination;
                if (!claw.captured.isEmpty()) sendGatherMotion(mc, claw.captured, claw.destination);
                detonateClaw(mc, claw, now);
                TrainingCombat.release(claw.source.id, "blood_claw");
                it.remove();
            }
        }
    }

    private static void captureAlongSweep(Minecraft mc, BloodClaw claw, Vec3 from, Vec3 to) {
        double halfW = claw.width * 0.5;
        AABB box = new AABB(from, to).inflate(halfW + 0.8, 2.5, halfW + 0.8);
        Vec3 eyeFrom = from.add(0, 0.8, 0);
        for (LivingEntity e : SkillController.INSTANCE.targets(box)) {
            if (claw.captured.contains(e.getUUID())) continue;
            Target data = state().context(e);
            if (data == null || data.boss() || e.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) >= 1.0) continue;
            Vec3 center = e.getBoundingBox().getCenter();
            Vec3 rel = center.subtract(claw.origin);
            double fwd = rel.x * claw.direction.x + rel.z * claw.direction.z;
            double side = Math.abs(rel.x * claw.direction.z - rel.z * claw.direction.x);
            if (fwd >= -0.5 && fwd <= claw.travelDistance + 1.0 && side <= halfW + e.getBbWidth() * 0.5
                && SkillController.INSTANCE.sight(eyeFrom, center)) {
                claw.captured.add(e.getUUID());
            }
        }
    }

    private static void detonateClaw(Minecraft mc, BloodClaw claw, long now) {
        Vec3 center = claw.destination;
        claw.source.finisher = true;
        claw.source.center = center;
        emitClawVisual(claw, "RELEASE", center);

        Set<UUID> targets = new LinkedHashSet<>();
        List<LivingEntity> victims = new ArrayList<>();
        double r = claw.crushRadius;
        Vec3 sightOrigin = center.add(0, 1.0, 0);
        for (LivingEntity e : SkillController.INSTANCE.targets(new AABB(center, center).inflate(r + 1.0, 3.5, r + 1.0))) {
            Vec3 ec = e.getBoundingBox().getCenter();
            double dx = ec.x - center.x, dz = ec.z - center.z;
            double reach = r + e.getBbWidth() * 0.5;
            if (dx * dx + dz * dz <= reach * reach && Math.abs(ec.y - sightOrigin.y) <= 3.5
                && SkillController.INSTANCE.sight(sightOrigin, ec) && targets.add(e.getUUID())) {
                victims.add(e);
            }
        }
        for (UUID id : claw.captured) {
            if (!targets.add(id)) continue;
            var raw = mc.level.getEntity(id);
            if (raw instanceof LivingEntity e && SkillController.canTarget(e)
                && e.position().distanceToSqr(center) <= (r + 3.0) * (r + 3.0)) {
                victims.add(e);
            }
        }

        double baseCoeff = claw.source.parameters.get("damage", 4.0);
        double threshold = TowerBattleCard.BERSERKER_HAND_CLAW.ratio("threshold");
        double executeMut = TowerBattleCard.BERSERKER_HAND_CLAW.ratio("execute");
        boolean lowHpHit = false;
        for (LivingEntity victim : victims) {
            Target data = state().context(victim);
            boolean lowHp = data != null && !data.life.dead && state().predicted(data.life) < threshold;
            SkillCast hitCast = claw.source;
            if (lowHp) {
                hitCast = detachCast(claw.source);
                hitCast.contactGroup = "berserker_hand_claw";
                hitCast.controlSeconds = claw.source.controlSeconds;
                hitCast.finisher = true;
                hitCast.stats.modifier("final").mut += executeMut;
            }
            if (SkillController.INSTANCE.hitCast(hitCast, victim, baseCoeff, 0.0) && lowHp) {
                lowHpHit = true;
            }
        }

        if (lowHpHit && claw.source.cooldownMillis > 0) {
            long until = state().resources().cooldowns.getOrDefault(CareerSkills.HAND, 0L);
            if (until > now) {
                long refund = Math.round(claw.source.cooldownMillis * TowerBattleCard.BERSERKER_HAND_CLAW.ratio("refund"));
                state().resources().cooldowns.put(CareerSkills.HAND, Math.max(now, until - refund));
                state().upload();
            }
        }
    }

    private static void tickBlades(Minecraft mc, long now, String dim, long gen) {
        if (blades.isEmpty()) return;
        for (var it = blades.iterator(); it.hasNext(); ) {
            var blade = it.next();
            if (blade.generation != gen || !blade.dimension.equals(dim)) {
                emitBladeVisual(blade, "CANCEL", blade.pos);
                TrainingCombat.release(blade.source.id, "flying_blade");
                it.remove();
                continue;
            }
            TrainingCombat.touch(blade.source.id);
            int budget = 20;
            while (!blade.flightDone && now >= blade.nextStepAt && budget-- > 0) {
                double step = Math.min(FLYING_BLADE_SPEED * (FLYING_BLADE_STEP_MS / 1000.0), blade.travelDistance - blade.travelled);
                Vec3 from = blade.pos;
                Vec3 next = blade.travelled + step >= blade.travelDistance - 1e-4
                    ? blade.endpoint
                    : blade.origin.lerp(blade.endpoint, (blade.travelled + step) / blade.travelDistance);
                blade.pos = next;
                blade.travelled += step;
                blade.nextStepAt += FLYING_BLADE_STEP_MS;

                double halfW = blade.width * 0.5;
                Vec3 sightFrom = from.add(0, 1.0, 0);
                double pierceCoeff = blade.source.parameters.get("damage", 2.2);
                blade.source.finisher = false;
                blade.source.hitIndex = 0;
                for (LivingEntity e : SkillController.INSTANCE.targets(new AABB(from, next).inflate(halfW + 0.6, 2.2, halfW + 0.6))) {
                    if (blade.pierced.contains(e.getUUID())) continue;
                    Vec3 ec = e.getBoundingBox().getCenter();
                    Vec3 rel = ec.subtract(blade.origin);
                    double fwd = rel.x * blade.direction.x + rel.z * blade.direction.z;
                    double side = Math.abs(rel.x * blade.direction.z - rel.z * blade.direction.x);
                    if (fwd >= -0.5 && fwd <= blade.travelled + 1.0 && side <= halfW + e.getBbWidth() * 0.5
                        && SkillController.INSTANCE.sight(sightFrom, ec)) {
                        if (SkillController.INSTANCE.hitCast(blade.source, e, pierceCoeff, 0.0)) {
                            blade.pierced.add(e.getUUID());
                            if (!blade.blastScheduled) {
                                blade.scheduleBlast(e.position(), now);
                            }
                        }
                    }
                }
                if (blade.travelled >= blade.travelDistance - 1e-4) {
                    blade.flightDone = true;
                    if (!blade.blastScheduled) {
                        blade.scheduleBlast(blade.endpoint, now);
                    }
                }
            }

            while (blade.blastScheduled && blade.blastsFired < blade.blastCount && now >= blade.nextBlastAt) {
                detonateBlade(blade);
                blade.blastsFired++;
                blade.nextBlastAt += DOUBLE_BLAST_GAP_MS;
            }

            if (blade.flightDone && blade.blastsFired >= blade.blastCount) {
                TrainingCombat.release(blade.source.id, "flying_blade");
                it.remove();
            }
        }
    }

    private static void detonateBlade(FlyingBlade blade) {
        Vec3 center = blade.blastCenter;
        double r = blade.blastRadius;
        double coeff = blade.source.parameters.get("finisher", 6.6) * blade.blastDamageScale;
        blade.source.finisher = true;
        blade.source.center = center;
        blade.source.hitIndex = 1 + blade.blastsFired;
        emitBladeVisual(blade, "FINISH", center);

        Vec3 sightOrigin = center.add(0, 1.0, 0);
        for (LivingEntity e : SkillController.INSTANCE.targets(new AABB(center, center).inflate(r + 0.5, 4.0, r + 0.5))) {
            if (e.position().subtract(center).horizontalDistanceSqr() <= r * r
                && SkillController.INSTANCE.sight(sightOrigin, e.getBoundingBox().getCenter())) {
                SkillController.INSTANCE.hitCast(blade.source, e, coeff, 0.0);
            }
        }
    }

    private static void sendGatherMotion(Minecraft mc, Set<UUID> ids, Vec3 destination) {
        JsonObject packet = new JsonObject();
        packet.addProperty("session", state().session());
        packet.addProperty("generation", state().own().generation);
        JsonArray moves = new JsonArray();
        for (UUID id : ids) {
            var raw = mc.level.getEntity(id);
            if (!(raw instanceof LivingEntity target) || !target.isAlive()) continue;
            JsonObject move = new JsonObject();
            move.addProperty("target", id.toString());
            move.addProperty("x", destination.x);
            move.addProperty("y", destination.y);
            move.addProperty("z", destination.z);
            moves.add(move);
        }
        if (!moves.isEmpty()) {
            packet.add("moves", moves);
            state().send("careerMotion", packet);
        }
    }

    private static Vec3 resolveForwardPoint(Minecraft mc, Vec3 origin, Vec3 dir, double maxRange) {
        Vec3 eyeFrom = origin.add(0, 0.9, 0);
        Vec3 eyeTo = eyeFrom.add(dir.scale(maxRange));
        var wall = mc.level.clip(new ClipContext(eyeFrom, eyeTo, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
        Vec3 clamped = wall.getType() != HitResult.Type.MISS
            ? wall.getLocation().subtract(dir.scale(0.35))
            : origin.add(dir.scale(maxRange));
        var floor = mc.level.clip(new ClipContext(
            new Vec3(clamped.x, origin.y + 2.0, clamped.z),
            new Vec3(clamped.x, origin.y - 3.0, clamped.z),
            ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player
        ));
        double y = floor.getType() != HitResult.Type.MISS ? floor.getLocation().y : origin.y;
        return new Vec3(clamped.x, y, clamped.z);
    }

    private static void emitClawVisual(BloodClaw claw, String phase, Vec3 pos) {
        var p = Minecraft.getInstance().player;
        if (p == null || !state().ready()) return;
        long now = ClientUi.now();
        JsonObject j = new JsonObject();
        j.addProperty("caster", p.getStringUUID());
        j.addProperty("session", state().session());
        j.addProperty("generation", state().own().generation);
        j.addProperty("id", claw.visualId);
        j.addProperty("kind", SkillVisualRules.BERSERKER_BLOOD_CLAW);
        j.addProperty("phase", phase);
        j.addProperty("sequence", phase.equals("CANCEL") ? 4096 : ++claw.visualSequence);
        j.addProperty("seed", claw.visualId.hashCode());
        j.addProperty("startedAt", state().serverNow() - Math.max(0L, now - claw.started));
        j.addProperty("at", state().serverNow());
        j.addProperty("duration", CLAW_SWEEP_MS + 750L);
        j.addProperty("cosmeticOnly", true);
        j.addProperty("color", 0xc92f43);
        j.addProperty("horizontalScale", Math.clamp(claw.width / 3.0, 0.5, 4.0));
        j.addProperty("x", claw.origin.x); j.addProperty("y", claw.origin.y); j.addProperty("z", claw.origin.z);
        j.addProperty("px", pos.x); j.addProperty("py", pos.y); j.addProperty("pz", pos.z);
        j.addProperty("dx", claw.direction.x); j.addProperty("dy", 0d); j.addProperty("dz", claw.direction.z);
        if (phase.equals("START")) {
            j.add("motion", SkillController.motion(claw.travelDistance, CLAW_SWEEP_MS, false));
        }
        Effects.receive(j);
        state().send("visual", j);
    }

    private static void emitBladeVisual(FlyingBlade blade, String phase, Vec3 pos) {
        var p = Minecraft.getInstance().player;
        if (p == null || !state().ready()) return;
        long now = ClientUi.now();
        JsonObject j = new JsonObject();
        j.addProperty("caster", p.getStringUUID());
        j.addProperty("session", state().session());
        j.addProperty("generation", state().own().generation);
        j.addProperty("id", blade.visualId);
        j.addProperty("kind", SkillVisualRules.BERSERKER_FLYING_BLADE);
        j.addProperty("phase", phase);
        j.addProperty("sequence", phase.equals("CANCEL") ? 4096 : ++blade.visualSequence);
        j.addProperty("seed", blade.visualId.hashCode());
        j.addProperty("startedAt", state().serverNow() - Math.max(0L, now - blade.started));
        j.addProperty("at", state().serverNow());
        j.addProperty("duration", blade.travelMs + blade.blastCount * DOUBLE_BLAST_GAP_MS + 900L);
        j.addProperty("cosmeticOnly", true);
        j.addProperty("color", 0xf14955);
        j.addProperty("horizontalScale", Math.clamp(blade.blastRadius / 4.0, 0.5, 4.0));
        j.addProperty("x", blade.origin.x); j.addProperty("y", blade.origin.y); j.addProperty("z", blade.origin.z);
        j.addProperty("px", pos.x); j.addProperty("py", pos.y); j.addProperty("pz", pos.z);
        j.addProperty("dx", blade.direction.x); j.addProperty("dy", 0d); j.addProperty("dz", blade.direction.z);
        if (phase.equals("START")) {
            j.add("motion", SkillController.motion(blade.travelDistance, blade.travelMs, false));
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
        copy.cooldownMillis = cast.cooldownMillis;
        return copy;
    }

    private static Vec3 horizontal(Vec3 v) {
        if (v == null) return new Vec3(0, 0, 1);
        Vec3 h = new Vec3(v.x, 0, v.z);
        return h.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : h.normalize();
    }
}
