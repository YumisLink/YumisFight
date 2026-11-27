package cn.yumis.fight.modules.dungeons.application;

import cn.yumis.fight.foundation.Journal;
import cn.yumis.fight.modules.combat.api.CombatContentTypes;
import cn.yumis.fight.modules.dungeons.api.Dungeon;
import cn.yumis.fight.modules.dungeons.api.DungeonEnemy;
import cn.yumis.fight.modules.dungeons.api.DungeonPorts;
import cn.yumis.fight.modules.dungeons.api.Target;
import cn.yumis.fight.modules.dungeons.content.AwakeningTowerLayout;
import cn.yumis.fight.modules.dungeons.content.FrostTowerFrostnova;
import cn.yumis.fight.modules.dungeons.content.FrostTowerFrostnova.SkillId;
import cn.yumis.fight.network.Network;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/**
 * 霜塔第 20 层专属首领「霜星」服务端双阶段状态机与技能调度服务。
 * <p>
 * 遵循权威划分与同步约束：
 * - 服务端维护首领实体、二阶段复活演出、技能调度与时间轴，不向客户端发送高于 4Hz 的高频位置流；
 * - 背后 24/31 枚连射冰刺与 30° 扇形冰锥由服务端一次性下发编排参数，客户端权威模拟直线轨迹与受击判定。
 */
public final class FrostnovaEncounterService {
    private static final Gson JSON = new GsonBuilder().disableHtmlEscaping().create();

    public enum Phase {
        PHASE_1,
        REVIVING,
        PHASE_2
    }

    static final class ActiveCast {
        final SkillId skill;
        long startAt, nextAt, endAt;
        long windupMs, activeMs, auxMs;
        int step, totalSteps, subPhase;
        double startAngle;
        Vec3 from = Vec3.ZERO, to = Vec3.ZERO, anchor = Vec3.ZERO, direction = new Vec3(0, 0, 1);

        ActiveCast(SkillId skill) {
            this.skill = skill;
        }
    }

    static final class FieldRecord {
        final String id;
        final long endAt;

        FieldRecord(String id, long endAt) {
            this.id = id;
            this.endAt = endAt;
        }
    }

    public static final class State {
        public UUID bossId;
        public String instanceId;
        public int cell;
        public Phase phase = Phase.PHASE_1;
        public long reviveStartAt, reviveEndAt;
        public Vec3 reviveAnchor = Vec3.ZERO;
        public long busyUntil;
        public long lastServerTick = -1L;
        public int sequence;
        public String lastSkill = "";
        public final Map<String, Long> cooldownUntil = new HashMap<>();
        final List<FieldRecord> activeFields = new ArrayList<>();
        ActiveCast activeCast;

        public boolean isPhase2() {
            return phase == Phase.PHASE_2;
        }
    }

    private final DungeonService owner;
    private final Journal journal;
    private final MinecraftServer server;
    private final DungeonPorts.Players players;
    private final DungeonPorts.Targets targets;
    private final Random random = new Random();
    private final Map<String, State> states = new LinkedHashMap<>();

    FrostnovaEncounterService(DungeonService owner, Journal journal, MinecraftServer server,
                              DungeonPorts.Players players, DungeonPorts.Targets targets) {
        this.owner = owner;
        this.journal = journal;
        this.server = server;
        this.players = players;
        this.targets = targets;
    }

    /** 20 Hz 高频推进：由 ServerApplication.tick 驱动，确保转阶段浮空落地、折跃滑退与星爆节奏平滑精准。 */
    void tickAll(long now) {
        if (states.isEmpty()) return;
        for (String instanceId : new ArrayList<>(states.keySet())) {
            State s = states.get(instanceId);
            if (s == null) continue;
            Dungeon d = owner.byId(instanceId);
            if (d == null || d.status.equals("CLOSED") || d.tower == null || !d.tower.fighting()) {
                continue;
            }
            Entity raw = owner.level().getEntity(s.bossId);
            if (!(raw instanceof DungeonEnemy boss) || !boss.isAlive() || owner.defeated(s.bossId)) {
                continue;
            }
            List<ServerPlayer> fighters = fightersFor(d);
            try {
                brain(d, boss, fighters, now);
            } catch (Exception failure) {
                server.sendSystemMessage(cn.yumis.fight.modules.presentation.api.ModText.of("霜塔霜星首领更新失败：" + failure.getMessage()));
            }
        }
    }

    private List<ServerPlayer> fightersFor(Dungeon d) {
        var list = new ArrayList<ServerPlayer>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.level().dimension() == DungeonService.DIMENSION && owner.participant(p, d.id.toString())
                && !players.session(p).life.dead && d.tower != null && d.tower.hasSelected(p.getUUID())) {
                list.add(p);
            }
        }
        return list;
    }

    void brain(Dungeon d, DungeonEnemy e, List<ServerPlayer> fighters, long now) throws Exception {
        String instanceId = d.id.toString();
        State s = states.get(instanceId);
        if (s == null || !e.getUUID().equals(s.bossId)) {
            s = initState(d, e, now);
            states.put(instanceId, s);
        }
        long currentTick = server.getTickCount();
        if (s.lastServerTick == currentTick) return;
        s.lastServerTick = currentTick;
        Target t = targets.target(e);
        if (t.life.dead) return;

        s.activeFields.removeIf(f -> now >= f.endAt);

        // 1. 检测一阶段归零触发的二阶段转阶段复活（倒下 -> 身体先飞起来 -> 空中站起来 -> 落地强化）
        if (s.phase == Phase.PHASE_1 && t.encounterLives == 1 && t.damageImmune) {
            beginPhaseTransition(d, e, t, s, now);
            return;
        }

        // 2. 推进转阶段演出（全程无敌，不响应其他动作）
        if (s.phase == Phase.REVIVING) {
            tickPhaseTransition(d, e, t, s, now);
            return;
        }

        // 3. 推进当前施法中的动作
        if (s.activeCast != null) {
            tickActiveCast(d, e, t, s, fighters, now);
            return;
        }

        if (fighters.isEmpty()) return;

        // 未释放技能时受控制效果暂停行动
        if (CombatContentTypes.controlled(e)) {
            e.getNavigation().stop();
            return;
        }

        // 4. 空闲时面向最近玩家，保持冰系法师中距离站位
        ServerPlayer nearest = nearestFighter(e, fighters);
        if (nearest != null) {
            facePosition(e, nearest.position());
            double dist = e.distanceTo(nearest);
            if (!e.isNoGravity() && dist > 11.0) {
                e.getNavigation().moveTo(nearest, 1.02);
            } else {
                e.getNavigation().stop();
            }
        }

        if (now < s.busyUntil) return;
        SkillId chosen = chooseNextSkill(s, now, random);
        if (chosen == null) return;
        s.lastSkill = chosen.id;
        // 冷却按原定义计算，不受冰缓倍率缩放
        s.cooldownUntil.put(chosen.id, now + chosen.cooldownMs);
        startSkill(d, e, t, s, chosen, fighters, now);
    }

    private State initState(Dungeon d, DungeonEnemy e, long now) {
        State s = new State();
        s.bossId = e.getUUID();
        s.instanceId = d.id.toString();
        s.cell = d.cell;
        s.phase = Phase.PHASE_1;
        s.busyUntil = now + 1_100L;
        return s;
    }

    /** 纯函数选招入口：一阶段屏蔽二阶段专属大招，二阶段解锁全技能，并避免相邻连发同招。 */
    public static SkillId chooseNextSkill(State s, long now, Random rng) {
        List<SkillId> candidates = new ArrayList<>(SkillId.values().length);
        for (SkillId skill : SkillId.values()) {
            if (skill.phase2Only && !s.isPhase2()) continue;
            if (s.cooldownUntil.getOrDefault(skill.id, 0L) <= now) {
                candidates.add(skill);
            }
        }
        if (candidates.isEmpty()) return null;
        if (candidates.size() > 1) {
            candidates.removeIf(sk -> sk.id.equals(s.lastSkill));
        }
        return candidates.get(rng.nextInt(candidates.size()));
    }

    private void beginPhaseTransition(Dungeon d, DungeonEnemy e, Target t, State s, long now) throws Exception {
        String instanceId = d.id.toString();
        s.phase = Phase.REVIVING;
        s.activeCast = null;
        s.activeFields.clear();
        s.reviveStartAt = now;
        s.reviveEndAt = now + FrostTowerFrostnova.REVIVE_TOTAL_MS;
        s.reviveAnchor = AwakeningTowerLayout.ground(owner.level(), d.cell, e.position(), AwakeningTowerLayout.guard(d.cell));

        e.getNavigation().stop();
        e.setNoGravity(false);
        e.setPos(s.reviveAnchor.x, s.reviveAnchor.y, s.reviveAnchor.z);
        e.setDeltaMovement(Vec3.ZERO);
        e.play("phase_revive", FrostTowerFrostnova.REVIVE_TOTAL_MS);

        t.damageImmune = true;
        t.encounterReviveUntil = s.reviveEndAt;
        commitTarget(d, e, t);

        broadcastEvent(instanceId, Map.of("kind", "CLEAR_HAZARDS", "instance", instanceId));
        broadcastEvent(instanceId, Map.of(
            "kind", "PHASE_TRANSITION",
            "instance", instanceId,
            "bossEntityId", e.getId(),
            "x", s.reviveAnchor.x,
            "y", s.reviveAnchor.y,
            "z", s.reviveAnchor.z,
            "durationMs", FrostTowerFrostnova.REVIVE_TOTAL_MS
        ));
    }

    private void tickPhaseTransition(Dungeon d, DungeonEnemy e, Target t, State s, long now) throws Exception {
        long elapsed = now - s.reviveStartAt;
        double yOffset = FrostTowerFrostnova.reviveHeightOffset(elapsed);
        boolean airborne = yOffset > 0.01;
        e.setNoGravity(airborne);
        e.setPos(s.reviveAnchor.x, s.reviveAnchor.y + yOffset, s.reviveAnchor.z);
        e.setDeltaMovement(Vec3.ZERO);
        e.resetFallDistance();

        if (now < s.reviveEndAt) return;

        // 落地完成：正式进入二阶段，回满生命，攻击 +50%，所有冰刺 +30%，爆发范围 +30%
        landToGround(e, d.cell, s.reviveAnchor);
        t.life.q = 1.0;
        t.life.dead = false;
        t.life.revision++;
        t.damageImmune = false;
        t.encounterReviveUntil = 0L;
        t.speciesAttack = FrostTowerFrostnova.ATTACK_GRADE.multiplier() * FrostTowerFrostnova.PHASE2_ATTACK_MULTIPLIER;
        e.setHealth(e.getMaxHealth());
        commitTarget(d, e, t);

        String instanceId = d.id.toString();
        String hid = instanceId + "/revive_wave/" + (++s.sequence);
        owner.broadcast(instanceId, "frostBossHazard", hazardPacket(
            instanceId, hid, "REVIVE_SHOCKWAVE", t, e.getId(),
            s.reviveAnchor, s.reviveAnchor,
            FrostTowerFrostnova.REVIVE_SHOCKWAVE_RADIUS, 0.0,
            now, now, now + 650L, 0L,
            FrostTowerFrostnova.COEFF_REVIVE_SHOCKWAVE, 0, 0L, 0.0, 0.0, 0L, 0.0,
            true, 0xc8f4ff
        ));
        broadcastEvent(instanceId, Map.of(
            "kind", "PHASE2_ENTERED",
            "instance", instanceId,
            "durationMs", 3_000L
        ));

        s.phase = Phase.PHASE_2;
        s.lastSkill = "";
        s.cooldownUntil.clear();
        // 二阶段落地后给极寒星爆预留短暂暖机窗口，不至于落地瞬间直接开大
        s.cooldownUntil.put(SkillId.WINTER_NOVA.id, now + 7_000L);
        s.busyUntil = now + 900L;
    }

    private void startSkill(Dungeon d, DungeonEnemy e, Target t, State s, SkillId skill,
                            List<ServerPlayer> fighters, long now) {
        Target.ActionTiming timing = t.actionTiming(now);
        ServerPlayer targetPlayer = nearestFighter(e, fighters);
        if (targetPlayer == null) return;
        String instanceId = d.id.toString();
        boolean phase2 = s.isPhase2();
        Vec3 dir = horizontalDirection(e.position(), targetPlayer.position());

        switch (skill) {
            case BACK_BARRAGE -> {
                int count = FrostTowerFrostnova.spikeCount(FrostTowerFrostnova.SKILL1_BASE_SPIKES, phase2);
                long windupMs = timing.windup(FrostTowerFrostnova.SKILL1_WINDUP_MS);
                long intervalMs = timing.duration(FrostTowerFrostnova.SKILL1_LAUNCH_INTERVAL_MS);
                long volleyMs = Math.max(1L, count) * intervalMs;
                long endAt = now + windupMs + volleyMs;

                ActiveCast cast = new ActiveCast(skill);
                cast.startAt = now;
                cast.nextAt = now + windupMs;
                cast.endAt = endAt;
                cast.windupMs = windupMs;
                cast.activeMs = volleyMs;
                s.activeCast = cast;

                e.getNavigation().stop();
                facePosition(e, targetPlayer.position());
                e.play("back_barrage", windupMs + volleyMs);

                broadcastEvent(instanceId, Map.of(
                    "kind", "BARRAGE_BANNER",
                    "instance", instanceId,
                    "phase2", phase2,
                    "count", count,
                    "durationMs", windupMs + volleyMs
                ));

                String hid = instanceId + "/barrage/" + (++s.sequence);
                owner.broadcast(instanceId, "frostBossHazard", hazardPacket(
                    instanceId, hid, "BACK_BARRAGE", t, e.getId(),
                    e.position(), targetPlayer.position(),
                    FrostTowerFrostnova.SKILL1_HIT_RADIUS, FrostTowerFrostnova.SKILL1_SPEED,
                    now, now + windupMs, endAt + FrostTowerFrostnova.SKILL1_SPIKE_LIFE_MS, intervalMs,
                    FrostTowerFrostnova.COEFF_BACK_SPIKE, count, FrostTowerFrostnova.SKILL1_SPIKE_LIFE_MS,
                    0.0, FrostTowerFrostnova.SKILL1_TURN_DEG_PER_SEC, 0L, 0.0,
                    phase2, 0x8ce8ff
                ));
            }
            case HOMING_FAN -> {
                int count = FrostTowerFrostnova.spikeCount(FrostTowerFrostnova.SKILL2_BASE_SPIKES, phase2);
                long windupMs = timing.windup(FrostTowerFrostnova.SKILL2_WINDUP_MS);

                ActiveCast cast = new ActiveCast(skill);
                cast.startAt = now;
                cast.endAt = now + windupMs;
                cast.direction = dir;
                s.activeCast = cast;

                e.getNavigation().stop();
                facePosition(e, targetPlayer.position());
                e.play("homing_fan", windupMs + 300L);

                Vec3 origin = e.position().add(0, 1.35, 0);
                String hid = instanceId + "/homing_fan/" + (++s.sequence);
                owner.broadcast(instanceId, "frostBossHazard", hazardPacket(
                    instanceId, hid, "HOMING_FAN", t, e.getId(),
                    origin, origin.add(dir.scale(10.0)),
                    FrostTowerFrostnova.SKILL2_HIT_RADIUS, FrostTowerFrostnova.SKILL2_SPEED,
                    now, now + windupMs, now + windupMs + FrostTowerFrostnova.SKILL2_SPIKE_LIFE_MS, 0L,
                    FrostTowerFrostnova.COEFF_HOMING_SPIKE, count, FrostTowerFrostnova.SKILL2_SPIKE_LIFE_MS,
                    FrostTowerFrostnova.SKILL2_TOTAL_SPREAD_DEG, FrostTowerFrostnova.SKILL2_TURN_DEG_PER_SEC, 0L, 0.0,
                    phase2, 0x70dcff
                ));
            }
            case FROST_BURST -> {
                long windupMs = timing.windup(FrostTowerFrostnova.SKILL3_WINDUP_MS);
                long activeMs = timing.active(FrostTowerFrostnova.SKILL3_ACTIVE_MS);
                double radius = FrostTowerFrostnova.burstRadius(FrostTowerFrostnova.SKILL3_BASE_RADIUS, phase2);
                Vec3 center = AwakeningTowerLayout.ground(owner.level(), d.cell, e.position(), e.position());

                ActiveCast cast = new ActiveCast(skill);
                cast.startAt = now;
                cast.nextAt = now + windupMs;
                cast.endAt = now + windupMs + activeMs;
                cast.subPhase = 0;
                cast.anchor = center;
                s.activeCast = cast;

                e.getNavigation().stop();
                facePosition(e, targetPlayer.position());
                e.play("frost_burst", windupMs + activeMs);

                String hid = instanceId + "/burst/" + (++s.sequence);
                owner.broadcast(instanceId, "frostBossHazard", hazardPacket(
                    instanceId, hid, "FROST_BURST", t, e.getId(),
                    center, center,
                    radius, 0.0,
                    now, now + windupMs, now + windupMs + activeMs, 0L,
                    FrostTowerFrostnova.COEFF_FROST_BURST, 0, 0L, 0.0, 0.0, 0L, 0.0,
                    phase2, 0xa8f0ff
                ));
            }
            case FROST_FIELD -> {
                long castMs = timing.windup(FrostTowerFrostnova.SKILL4_CAST_MS);
                long warnMs = timing.windup(FrostTowerFrostnova.SKILL4_WARNING_MS);
                double radius = FrostTowerFrostnova.burstRadius(FrostTowerFrostnova.SKILL4_BASE_RADIUS, phase2);
                int count = phase2 ? FrostTowerFrostnova.SKILL4_PHASE2_FIELDS : FrostTowerFrostnova.SKILL4_BASE_FIELDS;

                ActiveCast cast = new ActiveCast(skill);
                cast.startAt = now;
                cast.endAt = now + castMs;
                s.activeCast = cast;

                e.getNavigation().stop();
                facePosition(e, targetPlayer.position());
                e.play("frost_field", castMs + 250L);

                List<Vec3> centers = pickFieldCenters(owner.level(), d.cell, e.position(), fighters, count, radius, random);
                long warnUntil = now + warnMs;
                long fieldEndAt = warnUntil + FrostTowerFrostnova.SKILL4_DURATION_MS;
                for (Vec3 pos : centers) {
                    String hid = instanceId + "/field/" + (++s.sequence);
                    s.activeFields.add(new FieldRecord(hid, fieldEndAt));
                    owner.broadcast(instanceId, "frostBossHazard", hazardPacket(
                        instanceId, hid, "FROST_FIELD", t, e.getId(),
                        pos, pos,
                        radius, 0.0,
                        now, warnUntil, fieldEndAt, FrostTowerFrostnova.SKILL4_TICK_MS,
                        FrostTowerFrostnova.COEFF_FIELD_TICK, 0, 0L, 0.0, 0.0, 0L, 0.0,
                        phase2, 0x6cd6ff
                    ));
                }
            }
            case ICE_BLINK -> {
                long windupMs = timing.windup(FrostTowerFrostnova.SKILL5_WINDUP_MS);
                long blinkMs = timing.active(FrostTowerFrostnova.SKILL5_BLINK_MS);
                Vec3 startPos = AwakeningTowerLayout.ground(owner.level(), d.cell, e.position(), e.position());
                Vec3 awayDir = dir.scale(-1.0);
                Vec3 endPos = computeBlinkTarget(owner.level(), d.cell, startPos, awayDir, FrostTowerFrostnova.SKILL5_DISTANCE, random);
                int spikeCount = FrostTowerFrostnova.spikeCount(FrostTowerFrostnova.SKILL5_BASE_SPIKES, phase2);

                ActiveCast cast = new ActiveCast(skill);
                cast.startAt = now;
                cast.nextAt = now + windupMs;
                cast.endAt = now + windupMs + blinkMs;
                cast.subPhase = 0;
                cast.from = startPos;
                cast.to = endPos;
                s.activeCast = cast;

                e.getNavigation().stop();
                facePosition(e, targetPlayer.position());
                e.play("ice_blink", windupMs + blinkMs);

                long crystalWarnUntil = now + windupMs + timing.windup(FrostTowerFrostnova.SKILL5_CRYSTAL_DELAY_MS);
                String hid = instanceId + "/blink_crystal/" + (++s.sequence);
                owner.broadcast(instanceId, "frostBossHazard", hazardPacket(
                    instanceId, hid, "BLINK_CRYSTAL", t, e.getId(),
                    startPos, endPos,
                    FrostTowerFrostnova.SKILL5_HIT_RADIUS, FrostTowerFrostnova.SKILL5_SPIKE_SPEED,
                    now, crystalWarnUntil, crystalWarnUntil + FrostTowerFrostnova.SKILL5_SPIKE_LIFE_MS, 0L,
                    FrostTowerFrostnova.COEFF_BLINK_SPIKE, spikeCount, FrostTowerFrostnova.SKILL5_SPIKE_LIFE_MS,
                    360.0, 0.0, 0L, random.nextDouble() * Math.PI * 2.0,
                    phase2, 0x9cf0ff
                ));
            }
            case WINTER_NOVA -> {
                long windupMs = timing.windup(FrostTowerFrostnova.SKILL6_WINDUP_MS);
                long waveIntervalMs = timing.duration(FrostTowerFrostnova.SKILL6_WAVE_INTERVAL_MS);
                long landMs = timing.duration(FrostTowerFrostnova.SKILL6_LAND_MS);
                long waveSpanMs = waveIntervalMs * FrostTowerFrostnova.SKILL6_WAVES;
                Vec3 groundAnchor = AwakeningTowerLayout.ground(owner.level(), d.cell, e.position(), AwakeningTowerLayout.guard(d.cell));

                ActiveCast cast = new ActiveCast(skill);
                cast.startAt = now;
                cast.nextAt = now + windupMs;
                cast.endAt = now + windupMs + waveSpanMs + landMs;
                cast.windupMs = windupMs;
                cast.auxMs = waveIntervalMs;
                cast.step = 0;
                cast.totalSteps = FrostTowerFrostnova.SKILL6_WAVES;
                cast.subPhase = 0;
                cast.anchor = groundAnchor;
                cast.startAngle = random.nextDouble() * Math.PI * 2.0;
                s.activeCast = cast;

                e.getNavigation().stop();
                e.setNoGravity(true);
                facePosition(e, targetPlayer.position());
                e.play("winter_nova", windupMs + waveSpanMs + landMs);

                broadcastEvent(instanceId, Map.of(
                    "kind", "NOVA_BANNER",
                    "instance", instanceId,
                    "durationMs", windupMs + waveSpanMs
                ));

                // 蓄力结束时脚下先触发一次二阶段大范围爆发
                double burstR = FrostTowerFrostnova.burstRadius(FrostTowerFrostnova.SKILL3_BASE_RADIUS, true);
                String burstId = instanceId + "/nova_burst/" + (++s.sequence);
                owner.broadcast(instanceId, "frostBossHazard", hazardPacket(
                    instanceId, burstId, "FROST_BURST", t, e.getId(),
                    groundAnchor, groundAnchor,
                    burstR, 0.0,
                    now, now + windupMs, now + windupMs + 600L, 0L,
                    FrostTowerFrostnova.COEFF_NOVA_BURST, 0, 0L, 0.0, 0.0, 0L, 0.0,
                    true, 0xc2f6ff
                ));
            }
        }
    }

    private void tickActiveCast(Dungeon d, DungeonEnemy e, Target t, State s, List<ServerPlayer> fighters, long now) {
        ActiveCast cast = s.activeCast;
        if (cast == null) return;
        Target.ActionTiming timing = t.actionTiming(now);
        String instanceId = d.id.toString();

        switch (cast.skill) {
            case BACK_BARRAGE -> {
                ServerPlayer nearest = nearestFighter(e, fighters);
                if (nearest != null) facePosition(e, nearest.position());
                if (now >= cast.endAt) {
                    s.activeCast = null;
                    s.busyUntil = now + timing.recovery(FrostTowerFrostnova.SKILL1_RECOVERY_MS);
                }
            }
            case HOMING_FAN -> {
                ServerPlayer nearest = nearestFighter(e, fighters);
                if (nearest != null) facePosition(e, nearest.position());
                if (now >= cast.endAt) {
                    s.activeCast = null;
                    s.busyUntil = now + timing.recovery(FrostTowerFrostnova.SKILL2_RECOVERY_MS);
                }
            }
            case FROST_BURST -> {
                if (cast.subPhase == 0 && now >= cast.nextAt) {
                    cast.subPhase = 1;
                    // 共振引爆场上所有现存寒霜领域
                    if (!s.activeFields.isEmpty()) {
                        JsonObject detonate = new JsonObject();
                        detonate.addProperty("kind", "DETONATE_FIELDS");
                        detonate.addProperty("instance", instanceId);
                        detonate.addProperty("detonateAt", now + FrostTowerFrostnova.SKILL4_DETONATE_DELAY_MS);
                        detonate.addProperty("coefficient", FrostTowerFrostnova.COEFF_FIELD_DETONATE);
                        detonate.add("boss", JSON.toJsonTree(t));
                        owner.broadcast(instanceId, "frostBossEvent", detonate);
                        s.activeFields.clear();
                    }
                }
                if (now >= cast.endAt) {
                    s.activeCast = null;
                    s.busyUntil = now + timing.recovery(FrostTowerFrostnova.SKILL3_RECOVERY_MS);
                }
            }
            case FROST_FIELD -> {
                if (now >= cast.endAt) {
                    s.activeCast = null;
                    s.busyUntil = now + timing.recovery(FrostTowerFrostnova.SKILL4_RECOVERY_MS);
                }
            }
            case ICE_BLINK -> {
                if (cast.subPhase == 0) {
                    if (now >= cast.nextAt) {
                        cast.subPhase = 1;
                        e.setNoGravity(true);
                    }
                    return;
                }
                double progress = Math.clamp((now - cast.nextAt) / (double) Math.max(1L, cast.endAt - cast.nextAt), 0.0, 1.0);
                Vec3 pos = cast.from.lerp(cast.to, progress);
                e.setPos(pos.x, pos.y + Math.sin(progress * Math.PI) * 0.45, pos.z);
                e.setDeltaMovement(Vec3.ZERO);
                ServerPlayer nearest = nearestFighter(e, fighters);
                if (nearest != null) facePosition(e, nearest.position());
                if (now >= cast.endAt) {
                    landToGround(e, d.cell, cast.to);
                    s.activeCast = null;
                    s.busyUntil = now + timing.recovery(FrostTowerFrostnova.SKILL5_RECOVERY_MS);
                }
            }
            case WINTER_NOVA -> {
                long elapsed = now - cast.startAt;
                long waveSpanMs = cast.auxMs * cast.totalSteps;
                long landStart = cast.startAt + cast.windupMs + waveSpanMs;
                double height;
                if (elapsed < cast.windupMs) {
                    double tRise = Math.clamp(elapsed / (double) Math.max(1L, cast.windupMs * 0.65), 0.0, 1.0);
                    height = FrostTowerFrostnova.SKILL6_HOVER_HEIGHT * (tRise * tRise * (3.0 - 2.0 * tRise));
                } else if (now < landStart) {
                    height = FrostTowerFrostnova.SKILL6_HOVER_HEIGHT + Math.sin((now - cast.startAt) * 0.006) * 0.2;
                } else {
                    double tLand = Math.clamp((now - landStart) / (double) Math.max(1L, cast.endAt - landStart), 0.0, 1.0);
                    height = FrostTowerFrostnova.SKILL6_HOVER_HEIGHT * (1.0 - tLand * tLand);
                }
                e.setNoGravity(true);
                e.setPos(cast.anchor.x, cast.anchor.y + height, cast.anchor.z);
                e.setDeltaMovement(Vec3.ZERO);

                ServerPlayer nearest = nearestFighter(e, fighters);
                if (nearest != null) facePosition(e, nearest.position());

                // 蓄力完成时同时召唤 8 道延迟坠落巨型冰锥 + 开始第 1 波螺旋冰刺
                if (cast.subPhase == 0 && now >= cast.nextAt) {
                    cast.subPhase = 1;
                    spawnNovaIcicles(d, e, t, s, fighters, timing, now);
                }
                if (cast.subPhase == 1) {
                    while (cast.step < cast.totalSteps && now >= cast.nextAt) {
                        int perWave = FrostTowerFrostnova.spikeCount(FrostTowerFrostnova.SKILL6_BASE_SPIKES_PER_WAVE, true);
                        double waveAngle = cast.startAngle + cast.step * (Math.PI / perWave);
                        Vec3 origin = cast.anchor.add(0, FrostTowerFrostnova.SKILL6_HOVER_HEIGHT + 1.0, 0);
                        String hid = instanceId + "/nova_spiral/" + (++s.sequence);
                        owner.broadcast(instanceId, "frostBossHazard", hazardPacket(
                            instanceId, hid, "NOVA_SPIRAL", t, e.getId(),
                            origin, origin,
                            FrostTowerFrostnova.SKILL1_HIT_RADIUS, FrostTowerFrostnova.SKILL6_SPIKE_SPEED,
                            now, now + 175L, now + 175L + FrostTowerFrostnova.SKILL6_SPIKE_LIFE_MS, 0L,
                            FrostTowerFrostnova.COEFF_NOVA_SPIKE, perWave, FrostTowerFrostnova.SKILL6_SPIKE_LIFE_MS,
                            360.0, FrostTowerFrostnova.SKILL6_SPIKE_TURN_DEG_PER_SEC, 0L, waveAngle,
                            true, 0xa2f2ff
                        ));
                        cast.step++;
                        cast.nextAt += cast.auxMs;
                    }
                }
                if (now >= cast.endAt) {
                    landToGround(e, d.cell, cast.anchor);
                    s.activeCast = null;
                    s.busyUntil = now + timing.recovery(FrostTowerFrostnova.SKILL6_RECOVERY_MS);
                }
            }
        }
    }

    private void spawnNovaIcicles(Dungeon d, DungeonEnemy e, Target t, State s, List<ServerPlayer> fighters,
                                  Target.ActionTiming timing, long now) {
        String instanceId = d.id.toString();
        long warnMs = timing.windup(FrostTowerFrostnova.SKILL6_ICICLE_WARNING_MS);
        List<Vec3> drops = pickFieldCenters(owner.level(), d.cell, e.position(), fighters,
            FrostTowerFrostnova.SKILL6_ICICLE_COUNT, FrostTowerFrostnova.SKILL6_ICICLE_RADIUS, random);
        for (int i = 0; i < drops.size(); i++) {
            Vec3 pos = drops.get(i);
            long stagger = i * 90L;
            String hid = instanceId + "/nova_icicle/" + (++s.sequence);
            owner.broadcast(instanceId, "frostBossHazard", hazardPacket(
                instanceId, hid, "NOVA_ICICLE", t, e.getId(),
                pos, pos,
                FrostTowerFrostnova.SKILL6_ICICLE_RADIUS, 0.0,
                now + stagger, now + stagger + warnMs, now + stagger + warnMs + 480L, 0L,
                FrostTowerFrostnova.COEFF_NOVA_ICICLE, 0, 0L, 0.0, 0.0, 0L, 0.0,
                true, 0xd4fbff
            ));
        }
    }

    /** 在战场内选择分布均匀的冰阵/冰锥落点：首点锁定玩家位置，其余围绕交战区散开且尽量互不重叠。 */
    public static List<Vec3> pickFieldCenters(ServerLevel world, int cell, Vec3 bossPos,
                                              List<ServerPlayer> fighters, int count, double radius, Random rng) {
        List<Vec3> result = new ArrayList<>(count);
        Vec3 anchor = fighters != null && !fighters.isEmpty() ? fighters.get(0).position() : bossPos;
        if (fighters != null) {
            for (ServerPlayer p : fighters) {
                if (result.size() >= count) break;
                Vec3 pt = safeGround(world, cell, p.position(), bossPos);
                result.add(pt);
            }
        }
        double minSpacingSq = Math.pow(radius * 1.45, 2);
        int attempts = 0;
        while (result.size() < count && attempts < count * 12) {
            attempts++;
            double angle = rng.nextDouble() * Math.PI * 2.0;
            double dist = radius * 1.2 + rng.nextDouble() * 14.0;
            Vec3 raw = anchor.add(Math.cos(angle) * dist, 0, Math.sin(angle) * dist);
            Vec3 cand = safeGround(world, cell, raw, anchor);
            boolean tooClose = false;
            for (Vec3 existing : result) {
                if (existing.horizontalDistanceSqr() >= 0 && existing.subtract(cand).horizontalDistanceSqr() < minSpacingSq) {
                    tooClose = true;
                    break;
                }
            }
            if (!tooClose || attempts > count * 8) {
                result.add(cand);
            }
        }
        return result;
    }

    /** 计算冰棱折跃落点：优先向远离玩家方向滑退，贴近边界时旋转多角度寻找开阔落点。 */
    public static Vec3 computeBlinkTarget(ServerLevel world, int cell, Vec3 start, Vec3 preferredDir,
                                          double distance, Random rng) {
        Vec3 baseDir = preferredDir.multiply(1, 0, 1);
        if (baseDir.lengthSqr() < 1e-6) baseDir = new Vec3(0, 0, 1);
        else baseDir = baseDir.normalize();

        double[] candidateAngles = {0.0, 40.0, -40.0, 85.0, -85.0, 130.0, -130.0};
        Vec3 best = start;
        double bestDistSq = 0.0;
        for (double deg : candidateAngles) {
            double rad = Math.toRadians(deg + (rng.nextDouble() - 0.5) * 12.0);
            Vec3 dir = new Vec3(
                baseDir.x * Math.cos(rad) - baseDir.z * Math.sin(rad),
                0,
                baseDir.x * Math.sin(rad) + baseDir.z * Math.cos(rad)
            );
            Vec3 clamped = AwakeningTowerLayout.insideBattle(cell, start.add(dir.scale(distance)));
            Vec3 grounded = safeGround(world, cell, clamped, start);
            double distSq = grounded.subtract(start).horizontalDistanceSqr();
            if (distSq > bestDistSq) {
                bestDistSq = distSq;
                best = grounded;
            }
            if (distSq >= Math.pow(distance * 0.72, 2)) {
                return grounded;
            }
        }
        return best;
    }

    private static Vec3 safeGround(ServerLevel world, int cell, Vec3 point, Vec3 fallback) {
        Vec3 inside = AwakeningTowerLayout.insideBattle(cell, point);
        return world == null ? inside : AwakeningTowerLayout.ground(world, cell, inside, fallback);
    }

    private void commitTarget(Dungeon d, DungeonEnemy e, Target t) throws Exception {
        journal.put("target/" + e.getUUID(), t);
        String encoded = JSON.toJson(t);
        e.setData(CombatContentTypes.TARGET, encoded);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (owner.participant(p, d.id.toString())) {
                targets.context(p, e);
            }
        }
    }

    private void landToGround(DungeonEnemy e, int cell, Vec3 around) {
        e.setNoGravity(false);
        Vec3 ground = AwakeningTowerLayout.ground(owner.level(), cell, around, AwakeningTowerLayout.guard(cell));
        e.setPos(ground.x, ground.y, ground.z);
        e.setDeltaMovement(Vec3.ZERO);
        e.resetFallDistance();
    }

    private static Vec3 horizontalDirection(Vec3 from, Vec3 to) {
        Vec3 delta = new Vec3(to.x - from.x, 0, to.z - from.z);
        return delta.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : delta.normalize();
    }

    private static void facePosition(DungeonEnemy e, Vec3 target) {
        double dx = target.x - e.getX();
        double dz = target.z - e.getZ();
        if (dx * dx + dz * dz < 1e-6) return;
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        e.setYRot(yaw);
        e.yBodyRot = yaw;
        e.setYHeadRot(yaw);
    }

    private static ServerPlayer nearestFighter(DungeonEnemy e, List<ServerPlayer> fighters) {
        ServerPlayer best = null;
        double bestSq = Double.POSITIVE_INFINITY;
        for (ServerPlayer f : fighters) {
            double sq = f.position().distanceToSqr(e.position());
            if (sq < bestSq) {
                bestSq = sq;
                best = f;
            }
        }
        return best;
    }

    private JsonObject hazardPacket(String instanceId, String id, String type, Target boss, int bossEntityId,
                                    Vec3 from, Vec3 to, double radius, double speed,
                                    long startAt, long warnUntil, long endAt, long intervalMs,
                                    double coefficient, int count, long spikeLifeMs,
                                    double spreadDeg, double turnDegPerSec, long auxTime, double baseAngle,
                                    boolean phase2, int color) {
        JsonObject o = new JsonObject();
        o.addProperty("instance", instanceId);
        o.addProperty("id", id);
        o.addProperty("type", type);
        o.add("boss", JSON.toJsonTree(boss));
        o.addProperty("bossEntityId", bossEntityId);
        o.addProperty("x", from.x);
        o.addProperty("y", from.y);
        o.addProperty("z", from.z);
        o.addProperty("tx", to.x);
        o.addProperty("ty", to.y);
        o.addProperty("tz", to.z);
        o.addProperty("radius", radius);
        o.addProperty("speed", speed);
        o.addProperty("startAt", startAt);
        o.addProperty("warnUntil", warnUntil);
        o.addProperty("endAt", endAt);
        o.addProperty("intervalMs", intervalMs);
        o.addProperty("coefficient", coefficient);
        o.addProperty("count", count);
        o.addProperty("spikeLifeMs", spikeLifeMs);
        o.addProperty("spreadDeg", spreadDeg);
        o.addProperty("turnDegPerSec", turnDegPerSec);
        o.addProperty("auxTime", auxTime);
        o.addProperty("baseAngle", baseAngle);
        o.addProperty("phase2", phase2);
        o.addProperty("magic", true);
        o.addProperty("element", FrostTowerFrostnova.ELEMENT_ICE);
        o.addProperty("color", color);
        return o;
    }

    private void broadcastEvent(String instanceId, Map<String, Object> payload) {
        owner.broadcast(instanceId, "frostBossEvent", payload);
    }

    private State findByBoss(UUID bossId) {
        for (State s : states.values()) {
            if (bossId.equals(s.bossId)) return s;
        }
        return null;
    }

    void died(UUID bossId) {
        State s = findByBoss(bossId);
        if (s == null) return;
        states.remove(s.instanceId);
        cleanupEncounter(s.instanceId);
    }

    void cancel(UUID bossId) {
        State s = findByBoss(bossId);
        if (s == null || s.phase == Phase.REVIVING) return;
        Entity raw = owner.level().getEntity(bossId);
        if (raw instanceof DungeonEnemy boss && boss.isNoGravity()) {
            landToGround(boss, s.cell, boss.position());
        }
    }

    void close(Dungeon d) {
        if (d == null) return;
        String instanceId = d.id.toString();
        State removed = states.remove(instanceId);
        if (removed != null || d.tower != null) {
            cleanupEncounter(instanceId);
        }
    }

    void playerLeft(ServerPlayer player, Dungeon d) {
        if (d == null) return;
        Network.send(player, "frostBossEvent", Map.of(
            "kind", "CLEAR_ENCOUNTER_EFFECTS",
            "instance", d.id.toString()
        ));
    }

    private void cleanupEncounter(String instanceId) {
        broadcastEvent(instanceId, Map.of("kind", "CLEAR_HAZARDS", "instance", instanceId));
        broadcastEvent(instanceId, Map.of("kind", "CLEAR_ENCOUNTER_EFFECTS", "instance", instanceId));
    }
}
