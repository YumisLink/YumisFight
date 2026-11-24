package cn.yumis.fight.modules.dungeons.application;

import cn.yumis.fight.foundation.Journal;
import cn.yumis.fight.modules.combat.api.ActionPhase;
import cn.yumis.fight.modules.combat.api.CombatContentTypes;
import cn.yumis.fight.modules.combat.api.StatusLeases;
import cn.yumis.fight.modules.combat.api.CombatPorts;
import cn.yumis.fight.modules.combat.api.Life;
import cn.yumis.fight.modules.combat.api.Receipt;
import cn.yumis.fight.modules.combat.api.Runtime;
import cn.yumis.fight.modules.dungeons.api.Dungeon;
import cn.yumis.fight.modules.dungeons.api.DungeonEnemy;
import cn.yumis.fight.modules.dungeons.api.DungeonPorts;
import cn.yumis.fight.modules.dungeons.api.Target;
import cn.yumis.fight.modules.dungeons.content.AwakeningTowerLayout;
import cn.yumis.fight.modules.dungeons.content.SealTowerElaina;
import cn.yumis.fight.modules.dungeons.content.SealTowerElaina.SkillId;
import cn.yumis.fight.network.Network;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/**
 * 封印塔第50层专属首领「伊蕾娜」服务端阶段状态机、技能调度、场地延迟物、技能6被动护盾与离场清理。
 * 服务器仅维护敌人实体、阶段锁血/护盾与时间轴，玩家命中、减伤与状态控制由本机客户端权威判定。
 */
public final class ElainaEncounterService implements CombatPorts.SkillEffects {
    private static final Gson JSON = new GsonBuilder().disableHtmlEscaping().create();

    public enum Phase {
        P1_OPENING,
        TRANSITION_80,
        P2_SHIELD_CORONA,
        TRANSITION_50,
        P3_MIDNIGHT,
        TRANSITION_20,
        P4_REVERSED,
        FINALE_METEOR_1,
        FINALE_DPS_RACE
    }

    static final class ActiveCast {
        final SkillId skill;
        ActionPhase phase = ActionPhase.ACTIVE;
        final StatusLeases leases = new StatusLeases();
        long startAt, endAt, nextAt;
        int step, totalSteps;
        boolean subActive; // 技能1：false=转向停顿, true=冲锋飞行中；技能5：是否已触发回血
        Vec3 from = Vec3.ZERO, to = Vec3.ZERO, anchor = Vec3.ZERO;
        double startAngle;
        UUID targetPlayer;
        int[] tripleOrder;

        ActiveCast(SkillId skill) {
            this.skill = skill;
        }
    }

    static final class TrackingStarHazard {
        final String id;
        final UUID targetPlayer;
        Vec3 pos;
        final long startAt, warnUntil, endAt;
        long lastMoveAt, lastBroadcastAt;

        TrackingStarHazard(String id, UUID targetPlayer, Vec3 pos, long startAt, long warnUntil, long endAt) {
            this.id = id;
            this.targetPlayer = targetPlayer;
            this.pos = pos;
            this.startAt = startAt;
            this.warnUntil = warnUntil;
            this.endAt = endAt;
            this.lastMoveAt = warnUntil;
            this.lastBroadcastAt = startAt;
        }
    }

    record RewindAnchor(UUID playerId, double x, double y, double z, float yaw) {}

    static final class PendingRewind {
        final String eventId;
        final long commitAt;
        final List<RewindAnchor> anchors;

        PendingRewind(String eventId, long commitAt, List<RewindAnchor> anchors) {
            this.eventId = eventId;
            this.commitAt = commitAt;
            this.anchors = anchors;
        }
    }

    public static final class State {
        public UUID bossId;
        public String instanceId;
        public int cell;
        public Phase phase = Phase.P1_OPENING;
        public boolean triggered80, triggered50, triggered20, triggeredFinale;
        public boolean passiveShieldUnlocked, midnightActive;
        public boolean finaleWipeFired;
        public long phaseUntil, busyUntil;
        public long nextRandomMeteorAt, nextTargetedMeteorAt;
        public long lastServerTick = -1L;
        public int sequence;
        public String lastSkill = "";
        public final Map<String, Long> cooldownUntil = new HashMap<>();
        final List<TrackingStarHazard> stars = new ArrayList<>();
        final List<PendingRewind> rewinds = new ArrayList<>();
        final Set<UUID> syncedMidnightPlayers = new HashSet<>();
        ActiveCast activeCast;
    }

    private final DungeonService owner;
    private final Journal journal;
    private final MinecraftServer server;
    private final DungeonPorts.Players players;
    private final DungeonPorts.Targets targets;
    private final Random random = new Random();
    private final Map<String, State> states = new LinkedHashMap<>();

    ElainaEncounterService(DungeonService owner, Journal journal, MinecraftServer server,
                           DungeonPorts.Players players, DungeonPorts.Targets targets) {
        this.owner = owner;
        this.journal = journal;
        this.server = server;
        this.players = players;
        this.targets = targets;
    }

    /** 20 Hz 高频推进：由 ServerApplication.tick 驱动，确保魔杖四连冲锋、闪现与陨石时间轴平滑精准。 */
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
                server.sendSystemMessage(cn.yumis.fight.modules.presentation.api.ModText.of("封印塔伊蕾娜首领更新失败：" + failure.getMessage()));
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

        // 重连或后进入的参战者补发午夜天象
        if (s.midnightActive) {
            for (ServerPlayer p : fighters) {
                if (s.syncedMidnightPlayers.add(p.getUUID())) {
                    sendMidnight(p, instanceId, true);
                }
            }
        }

        // 1. 推进追踪星辰与5秒时钟回溯
        tickTrackingStars(d, e, t, s, fighters, now);
        tickPendingRewinds(d, s, now);

        // 2. 检查四道单向锁血跃迁（80% / 50% / 20% / 1 HP 终局）
        if (checkPhaseTransitions(d, e, t, s, fighters, now)) {
            return;
        }

        // 3. 转阶段与终局状态机推进
        switch (s.phase) {
            case TRANSITION_80 -> {
                hoverAtCenter(e, d.cell, 86.0);
                if (now >= s.phaseUntil) {
                    broadcastEvent(instanceId, Map.of(
                        "kind", "ALL_COOLDOWN_100S",
                        "instance", instanceId,
                        "eventId", instanceId + "/cd80",
                        "cooldownMs", SealTowerElaina.TRANSITION_80_ALL_CD_MS
                    ));
                    s.passiveShieldUnlocked = true;
                    t.damageImmune = false;
                    t.healthFloor = SealTowerElaina.FLOOR_P2;
                    commitTarget(d, e, t);
                    landToGround(e, d.cell, AwakeningTowerLayout.guard(d.cell));
                    s.phase = Phase.P2_SHIELD_CORONA;
                    s.busyUntil = now + 600L;
                }
                return;
            }
            case TRANSITION_50 -> {
                hoverAtCenter(e, d.cell, 88.0);
                spawnMidnightMeteors(d, e, t, s, fighters, now);
                if (now >= s.phaseUntil) {
                    t.damageImmune = false;
                    t.healthFloor = SealTowerElaina.FLOOR_P3;
                    commitTarget(d, e, t);
                    landToGround(e, d.cell, AwakeningTowerLayout.guard(d.cell));
                    s.phase = Phase.P3_MIDNIGHT;
                    s.busyUntil = now + 600L;
                }
                return;
            }
            case TRANSITION_20 -> {
                hoverAtCenter(e, d.cell, 87.0);
                if (now >= s.phaseUntil) {
                    t.life.q = 1.0;
                    t.life.dead = false;
                    t.life.revision++;
                    t.damageImmune = false;
                    t.healthFloor = 0.0;
                    t.lethalFloorOne = true;
                    commitTarget(d, e, t);
                    e.setHealth(e.getMaxHealth());
                    landToGround(e, d.cell, AwakeningTowerLayout.guard(d.cell));
                    s.phase = Phase.P4_REVERSED;
                    s.busyUntil = now + 600L;
                }
                return;
            }
            case FINALE_METEOR_1 -> {
                hoverAtCenter(e, d.cell, 90.0);
                if (now >= s.phaseUntil) {
                    Vec3 center = AwakeningTowerLayout.guard(d.cell);
                    JsonObject impact = new JsonObject();
                    impact.addProperty("kind", "FINALE_METEOR_1_IMPACT");
                    impact.addProperty("instance", instanceId);
                    impact.addProperty("eventId", instanceId + "/finale1");
                    impact.addProperty("cx", center.x);
                    impact.addProperty("cy", center.y);
                    impact.addProperty("cz", center.z);
                    impact.add("boss", JSON.toJsonTree(t));
                    owner.broadcast(instanceId, "sealBossEvent", impact);

                    // 立刻启动第2轮30秒双巨型陨石（中心 + 每位存活玩家当前脚下）
                    scheduleFinaleWipeMeteors(d, t, s, fighters, now);
                    landToGround(e, d.cell, center);
                    t.damageImmune = false;
                    t.lethalFloorOne = false;
                    t.shield = Math.max(t.shield, SealTowerElaina.FINALE_SHIELD_FRACTION);
                    t.life.revision++;
                    commitTarget(d, e, t);
                    s.phase = Phase.FINALE_DPS_RACE;
                    s.phaseUntil = now + SealTowerElaina.FINALE_DPS_RACE_MS;
                    s.busyUntil = now + 800L;
                }
                return;
            }
            case FINALE_DPS_RACE -> {
                if (!s.finaleWipeFired && now >= s.phaseUntil) {
                    s.finaleWipeFired = true;
                    JsonObject wipe = new JsonObject();
                    wipe.addProperty("kind", "FINALE_METEOR_2_WIPE");
                    wipe.addProperty("instance", instanceId);
                    wipe.addProperty("eventId", instanceId + "/finale2");
                    wipe.add("boss", JSON.toJsonTree(t));
                    owner.broadcast(instanceId, "sealBossEvent", wipe);
                    return;
                }
                // 30 秒斩杀竞速期继续照常释放技能施压
            }
            default -> {}
        }

        // 4. 推进当前施法中的持续动作
        if (s.activeCast != null) {
            tickActiveCast(d, e, t, s, fighters, now);
            return;
        }

        if (fighters.isEmpty()) return;

        // 5. 空闲时面向最近目标并小幅逼近
        ServerPlayer nearest = nearestFighter(e, fighters);
        if (nearest != null) {
            facePosition(e, nearest.position());
            if (!e.isNoGravity() && e.distanceTo(nearest) > 6.5) {
                e.getNavigation().moveTo(nearest, 1.05);
            } else {
                e.getNavigation().stop();
            }
        }

        if (now < s.busyUntil) return;
        selectAndCastNextSkill(d, e, t, s, fighters, now);
    }

    private State initState(Dungeon d, DungeonEnemy e, long now) {
        State s = new State();
        s.bossId = e.getUUID();
        s.instanceId = d.id.toString();
        s.cell = d.cell;
        s.phase = Phase.P1_OPENING;
        s.busyUntil = now + 1_200L;
        return s;
    }

    /** 检查阶段血线跃迁（严格单向一次性触发）。 */
    boolean checkPhaseTransitions(Dungeon d, DungeonEnemy e, Target t, State s, List<ServerPlayer> fighters, long now) throws Exception {
        String instanceId = d.id.toString();
        if (!s.triggered80 && t.life.q <= SealTowerElaina.FLOOR_P1 + 1e-9) {
            s.triggered80 = true;
            interruptActiveCast(d, e, t, s, true);
            // 80% 冰冻期间清掉所有场内残余伤害物与回溯，防止玩家冰冻期被残余弹幕击杀
            s.stars.clear();
            s.rewinds.clear();
            broadcastEvent(instanceId, Map.of("kind", "CLEAR_HAZARDS", "instance", instanceId));

            t.damageImmune = true;
            t.damageTakenMultiplier = 1.0;
            commitTarget(d, e, t);
            hoverAtCenter(e, d.cell, 86.0);
            e.play("transition_80", SealTowerElaina.TRANSITION_80_DURATION_MS);
            broadcastEvent(instanceId, Map.of(
                "kind", "FREEZE_10S",
                "instance", instanceId,
                "eventId", instanceId + "/freeze80",
                "durationMs", SealTowerElaina.TRANSITION_80_DURATION_MS
            ));
            s.phase = Phase.TRANSITION_80;
            s.phaseUntil = now + SealTowerElaina.TRANSITION_80_DURATION_MS;
            return true;
        }
        if (s.triggered80 && !s.triggered50 && s.phase == Phase.P2_SHIELD_CORONA && t.life.q <= SealTowerElaina.FLOOR_P2 + 1e-9) {
            s.triggered50 = true;
            s.midnightActive = true;
            interruptActiveCast(d, e, t, s, true);

            t.damageImmune = true;
            t.damageTakenMultiplier = 1.0;
            commitTarget(d, e, t);
            hoverAtCenter(e, d.cell, 88.0);
            e.play("transition_50", SealTowerElaina.TRANSITION_50_DURATION_MS);
            s.syncedMidnightPlayers.clear();
            for (ServerPlayer p : fighters) s.syncedMidnightPlayers.add(p.getUUID());
            broadcastEvent(instanceId, Map.of(
                "kind", "MIDNIGHT",
                "instance", instanceId,
                "active", true
            ));
            s.phase = Phase.TRANSITION_50;
            s.phaseUntil = now + SealTowerElaina.TRANSITION_50_DURATION_MS;
            s.nextRandomMeteorAt = now;
            s.nextTargetedMeteorAt = now + SealTowerElaina.MIDNIGHT_TARGETED_METEOR_INTERVAL_MS;
            return true;
        }
        if (s.triggered50 && !s.triggered20 && s.phase == Phase.P3_MIDNIGHT && t.life.q <= SealTowerElaina.FLOOR_P3 + 1e-9) {
            s.triggered20 = true;
            interruptActiveCast(d, e, t, s, true);

            t.damageImmune = true;
            t.damageTakenMultiplier = 1.0;
            commitTarget(d, e, t);
            Vec3 center = AwakeningTowerLayout.guard(d.cell);
            hoverAtCenter(e, d.cell, 87.0);
            e.play("transition_20", SealTowerElaina.TRANSITION_20_DURATION_MS);
            broadcastEvent(instanceId, Map.of(
                "kind", "CLOCK_REVERSE",
                "instance", instanceId,
                "eventId", instanceId + "/clock20",
                "x", center.x,
                "y", 87.0,
                "z", center.z,
                "durationMs", SealTowerElaina.TRANSITION_20_DURATION_MS
            ));
            s.phase = Phase.TRANSITION_20;
            s.phaseUntil = now + SealTowerElaina.TRANSITION_20_DURATION_MS;
            return true;
        }
        if (s.triggered20 && !s.triggeredFinale && s.phase == Phase.P4_REVERSED
            && t.lethalFloorOne && t.life.q <= Target.LETHAL_ONE_HP_FLOOR + 1e-9) {
            s.triggeredFinale = true;
            interruptActiveCast(d, e, t, s, true);

            t.damageImmune = true;
            t.damageTakenMultiplier = 1.0;
            commitTarget(d, e, t);
            Vec3 center = AwakeningTowerLayout.guard(d.cell);
            hoverAtCenter(e, d.cell, 90.0);
            e.play("finale_meteor_1", SealTowerElaina.FINALE_METEOR_1_WARNING_MS);

            // 广播 15s 中心巨型陨石视觉预警
            JsonObject meteor1 = hazardBase(d.id.toString(), d.id + "/finale_meteor_1", "METEOR_FINALE_1", t,
                center, center, SealTowerElaina.FINALE_METEOR_1_EDGE_DISTANCE, 0,
                now, now + SealTowerElaina.FINALE_METEOR_1_WARNING_MS,
                now + SealTowerElaina.FINALE_METEOR_1_WARNING_MS + 900L,
                0, 0, true, SealTowerElaina.ELEMENT_FIRE, 0xff5a2c);
            owner.broadcast(instanceId, "sealBossHazard", meteor1);

            s.phase = Phase.FINALE_METEOR_1;
            s.phaseUntil = now + SealTowerElaina.FINALE_METEOR_1_WARNING_MS;
            return true;
        }
        return false;
    }

    /** 供无头测试直接验证阶段跃迁与锁血不变量（不依赖 Minecraft 实体）。 */
    public static Phase evaluatePurePhaseStep(State s, Target t, long now) {
        if (!s.triggered80 && t.life.q <= SealTowerElaina.FLOOR_P1 + 1e-9) {
            s.triggered80 = true;
            t.damageImmune = true;
            t.damageTakenMultiplier = 1.0;
            s.phase = Phase.TRANSITION_80;
            s.phaseUntil = now + SealTowerElaina.TRANSITION_80_DURATION_MS;
            return s.phase;
        }
        if (s.phase == Phase.TRANSITION_80 && now >= s.phaseUntil) {
            s.passiveShieldUnlocked = true;
            t.damageImmune = false;
            t.healthFloor = SealTowerElaina.FLOOR_P2;
            s.phase = Phase.P2_SHIELD_CORONA;
            return s.phase;
        }
        if (s.triggered80 && !s.triggered50 && s.phase == Phase.P2_SHIELD_CORONA && t.life.q <= SealTowerElaina.FLOOR_P2 + 1e-9) {
            s.triggered50 = true;
            s.midnightActive = true;
            t.damageImmune = true;
            t.damageTakenMultiplier = 1.0;
            s.phase = Phase.TRANSITION_50;
            s.phaseUntil = now + SealTowerElaina.TRANSITION_50_DURATION_MS;
            return s.phase;
        }
        if (s.phase == Phase.TRANSITION_50 && now >= s.phaseUntil) {
            t.damageImmune = false;
            t.healthFloor = SealTowerElaina.FLOOR_P3;
            s.phase = Phase.P3_MIDNIGHT;
            return s.phase;
        }
        if (s.triggered50 && !s.triggered20 && s.phase == Phase.P3_MIDNIGHT && t.life.q <= SealTowerElaina.FLOOR_P3 + 1e-9) {
            s.triggered20 = true;
            t.damageImmune = true;
            t.damageTakenMultiplier = 1.0;
            s.phase = Phase.TRANSITION_20;
            s.phaseUntil = now + SealTowerElaina.TRANSITION_20_DURATION_MS;
            return s.phase;
        }
        if (s.phase == Phase.TRANSITION_20 && now >= s.phaseUntil) {
            t.life.q = 1.0;
            t.life.dead = false;
            t.life.revision++;
            t.damageImmune = false;
            t.healthFloor = 0.0;
            t.lethalFloorOne = true;
            s.phase = Phase.P4_REVERSED;
            return s.phase;
        }
        if (s.triggered20 && !s.triggeredFinale && s.phase == Phase.P4_REVERSED
            && t.lethalFloorOne && t.life.q <= Target.LETHAL_ONE_HP_FLOOR + 1e-9) {
            s.triggeredFinale = true;
            t.damageImmune = true;
            t.damageTakenMultiplier = 1.0;
            s.phase = Phase.FINALE_METEOR_1;
            s.phaseUntil = now + SealTowerElaina.FINALE_METEOR_1_WARNING_MS;
            return s.phase;
        }
        if (s.phase == Phase.FINALE_METEOR_1 && now >= s.phaseUntil) {
            t.damageImmune = false;
            t.lethalFloorOne = false;
            t.shield = Math.max(t.shield, SealTowerElaina.FINALE_SHIELD_FRACTION);
            t.life.revision++;
            s.phase = Phase.FINALE_DPS_RACE;
            s.phaseUntil = now + SealTowerElaina.FINALE_DPS_RACE_MS;
            return s.phase;
        }
        if (s.phase == Phase.FINALE_DPS_RACE && !t.life.dead && !s.finaleWipeFired && now >= s.phaseUntil) {
            s.finaleWipeFired = true;
        }
        return s.phase;
    }

    private void spawnMidnightMeteors(Dungeon d, DungeonEnemy e, Target t, State s, List<ServerPlayer> fighters, long now) {
        String instanceId = d.id.toString();
        Vec3 center = AwakeningTowerLayout.guard(d.cell);
        ServerLevel world = owner.level();
        // 随机陨石：3 枚/秒
        while (now >= s.nextRandomMeteorAt && s.nextRandomMeteorAt < s.phaseUntil) {
            s.nextRandomMeteorAt += SealTowerElaina.MIDNIGHT_RANDOM_METEOR_INTERVAL_MS;
            double rx = (random.nextDouble() * 2 - 1) * 42.0;
            double rz = (random.nextDouble() * 2 - 1) * 42.0;
            Vec3 raw = center.add(rx, 0, rz);
            Vec3 landing = AwakeningTowerLayout.ground(world, d.cell, raw, AwakeningTowerLayout.insideBattle(d.cell, raw));
            String hid = instanceId + "/mm_r/" + (++s.sequence);
            long warnUntil = now + SealTowerElaina.MIDNIGHT_METEOR_WARNING_MS;
            owner.broadcast(instanceId, "sealBossHazard", hazardBase(instanceId, hid, "METEOR_SMALL", t,
                landing, landing, SealTowerElaina.MIDNIGHT_METEOR_RADIUS, 0,
                now, warnUntil, warnUntil + 350L, 0,
                SealTowerElaina.COEFF_HIGH, true, SealTowerElaina.ELEMENT_FIRE, 0xb958ff));
        }
        // 每 0.5 秒每位玩家脚下 1 枚
        while (now >= s.nextTargetedMeteorAt && s.nextTargetedMeteorAt < s.phaseUntil) {
            s.nextTargetedMeteorAt += SealTowerElaina.MIDNIGHT_TARGETED_METEOR_INTERVAL_MS;
            for (ServerPlayer fighter : fighters) {
                Vec3 landing = AwakeningTowerLayout.ground(world, d.cell, fighter.position(), fighter.position());
                String hid = instanceId + "/mm_t/" + (++s.sequence);
                long warnUntil = now + SealTowerElaina.MIDNIGHT_METEOR_WARNING_MS;
                owner.broadcast(instanceId, "sealBossHazard", hazardBase(instanceId, hid, "METEOR_SMALL", t,
                    landing, landing, SealTowerElaina.MIDNIGHT_METEOR_RADIUS, 0,
                    now, warnUntil, warnUntil + 350L, 0,
                    SealTowerElaina.COEFF_HIGH, true, SealTowerElaina.ELEMENT_FIRE, 0xff5a7a));
            }
        }
    }

    private void scheduleFinaleWipeMeteors(Dungeon d, Target t, State s, List<ServerPlayer> fighters, long now) {
        String instanceId = d.id.toString();
        Vec3 center = AwakeningTowerLayout.guard(d.cell);
        long impactAt = now + SealTowerElaina.FINALE_DPS_RACE_MS;
        owner.broadcast(instanceId, "sealBossHazard", hazardBase(instanceId, instanceId + "/finale_meteor_2_center",
            "METEOR_FINALE_2", t, center, center, 18.0, 0,
            now, impactAt, impactAt + 900L, 0, 0, true, SealTowerElaina.ELEMENT_FIRE, 0xff2842));
        for (ServerPlayer fighter : fighters) {
            Vec3 pos = AwakeningTowerLayout.ground(owner.level(), d.cell, fighter.position(), fighter.position());
            owner.broadcast(instanceId, "sealBossHazard", hazardBase(instanceId,
                instanceId + "/finale_meteor_2_p/" + fighter.getUUID(),
                "METEOR_FINALE_2", t, pos, pos, 14.0, 0,
                now, impactAt, impactAt + 900L, 0, 0, true, SealTowerElaina.ELEMENT_FIRE, 0xff2842));
        }
    }

    private void selectAndCastNextSkill(Dungeon d, DungeonEnemy e, Target t, State s, List<ServerPlayer> fighters, long now) throws Exception {
        List<SkillId> candidates = new ArrayList<>();
        // P1 技能（1~4）
        addIfReady(candidates, SkillId.WAND_CHARGE, s, now);
        addIfReady(candidates, SkillId.TRACKING_STAR, s, now);
        addIfReady(candidates, SkillId.ELEMENTAL_ORBS, s, now);
        addIfReady(candidates, SkillId.BLINK_FLIGHT, s, now);
        // P2+ 解锁技能（5、7、8、9；6为被动）
        if (s.triggered80) {
            if (t.life.q < 1.0 - 1e-6) addIfReady(candidates, SkillId.DRINK_POTION, s, now);
            addIfReady(candidates, SkillId.CORONA_BARRAGE, s, now);
            addIfReady(candidates, SkillId.ELEMENTALIST_AWAKENING, s, now);
            addIfReady(candidates, SkillId.ELEMENTALIST_TRIPLE_CAST, s, now);
        }
        // P4+ 解锁技能（10、11、12）
        if (s.triggered20) {
            addIfReady(candidates, SkillId.CLOCK_REWIND, s, now);
            addIfReady(candidates, SkillId.TIME_SEAL, s, now);
            addIfReady(candidates, SkillId.PUMPKIN_SPIN, s, now);
        }
        if (candidates.isEmpty()) return;

        // 避免连续两次释放完全相同的技能（若有其他候选）
        if (candidates.size() > 1) {
            candidates.removeIf(sk -> sk.id.equals(s.lastSkill));
        }
        SkillId chosen = candidates.get(random.nextInt(candidates.size()));
        s.lastSkill = chosen.id;
        // 冷却一律使用原始毫秒值，不按冰缓缩放
        s.cooldownUntil.put(chosen.id, now + chosen.cooldownMs);
        startSkill(d, e, t, s, chosen, fighters, now);
    }

    private static void addIfReady(List<SkillId> out, SkillId skill, State s, long now) {
        if (!skill.activeSelectable) return;
        if (s.cooldownUntil.getOrDefault(skill.id, 0L) <= now) out.add(skill);
    }

    private void startSkill(Dungeon d, DungeonEnemy e, Target t, State s, SkillId skill, List<ServerPlayer> fighters, long now) throws Exception {
        Target.ActionTiming timing = t.actionTiming(now);
        ServerPlayer targetPlayer = nearestFighter(e, fighters);
        if (targetPlayer == null) return;
        String instanceId = d.id.toString();

        switch (skill) {
            case WAND_CHARGE -> {
                ActiveCast cast = new ActiveCast(skill);
                cast.step = 0;
                cast.totalSteps = SealTowerElaina.SKILL1_SEGMENTS;
                cast.subActive = false;
                cast.startAt = now;
                cast.nextAt = now;
                cast.targetPlayer = targetPlayer.getUUID();
                s.activeCast = cast;
                e.getNavigation().stop();
                e.setNoGravity(true);
                t.damageTakenMultiplier = SealTowerElaina.SKILL1_DAMAGE_TAKEN_MULTIPLIER;
                commitTarget(d, e, t);
                e.play(skill.id, timing.duration(SealTowerElaina.SKILL1_SEGMENTS * (SealTowerElaina.SKILL1_DASH_MS + SealTowerElaina.SKILL1_PAUSE_MS)));
                beginWandChargeSegment(d, e, t, s, cast, fighters, now);
            }
            case TRACKING_STAR -> {
                Vec3 feet = AwakeningTowerLayout.ground(owner.level(), d.cell, targetPlayer.position(), targetPlayer.position());
                long warnMs = timing.windup(SealTowerElaina.SKILL2_WARNING_MS);
                long warnUntil = now + warnMs;
                long endAt = warnUntil + SealTowerElaina.SKILL2_DURATION_MS;
                String hid = instanceId + "/star/" + (++s.sequence);
                s.stars.add(new TrackingStarHazard(hid, targetPlayer.getUUID(), feet, now, warnUntil, endAt));
                owner.broadcast(instanceId, "sealBossHazard", hazardBase(instanceId, hid, "TRACKING_STAR", t,
                    feet, feet, SealTowerElaina.SKILL2_RADIUS, 0,
                    now, warnUntil, endAt, SealTowerElaina.SKILL2_TICK_MS,
                    SealTowerElaina.COEFF_LOW_DOT, true, SealTowerElaina.ELEMENT_LIGHT, 0xfff08a));
                e.play(skill.id, timing.recovery(600L));
                s.busyUntil = now + timing.recovery(600L);
            }
            case ELEMENTAL_ORBS -> {
                long castMs = timing.active(SealTowerElaina.SKILL3_CAST_MS);
                ActiveCast cast = new ActiveCast(skill);
                cast.startAt = now;
                cast.endAt = now + castMs;
                cast.nextAt = now;
                cast.step = 0;
                cast.totalSteps = SealTowerElaina.SKILL3_ORB_COUNT;
                Vec3 ground = AwakeningTowerLayout.ground(owner.level(), d.cell, e.position(), AwakeningTowerLayout.guard(d.cell));
                cast.anchor = new Vec3(ground.x, ground.y + SealTowerElaina.SKILL3_HOVER_OFFSET, ground.z);
                s.activeCast = cast;
                e.getNavigation().stop();
                e.setNoGravity(true);
                e.setPos(cast.anchor.x, cast.anchor.y, cast.anchor.z);
                e.setDeltaMovement(Vec3.ZERO);
                e.play(skill.id, castMs);
            }
            case BLINK_FLIGHT -> {
                long windup = timing.windup(SealTowerElaina.SKILL4_WINDUP_MS);
                long flight = timing.active(SealTowerElaina.SKILL4_FLIGHT_MS);
                ActiveCast cast = new ActiveCast(skill);
                cast.startAt = now;
                cast.nextAt = now + windup;
                cast.endAt = now + windup + flight;
                cast.from = e.position();
                cast.to = pickBlinkDestination(owner.level(), d.cell, e.position(), targetPlayer.position(), e.getY() > 81.5);
                s.activeCast = cast;
                e.getNavigation().stop();
                e.setNoGravity(true);
                facePosition(e, cast.to);
                e.play(skill.id, windup + flight);
            }
            case DRINK_POTION -> {
                long castMs = timing.active(SealTowerElaina.SKILL5_CAST_MS);
                ActiveCast cast = new ActiveCast(skill);
                cast.startAt = now;
                cast.nextAt = now + timing.duration(SealTowerElaina.SKILL5_HEAL_FRAME_MS);
                cast.endAt = now + castMs;
                cast.subActive = false;
                s.activeCast = cast;
                e.getNavigation().stop();
                e.play(skill.id, castMs);
            }
            case CORONA_BARRAGE -> {
                long interval = timing.duration(SealTowerElaina.SKILL7_SPAWN_INTERVAL_MS);
                ActiveCast cast = new ActiveCast(skill);
                cast.startAt = now;
                cast.nextAt = now;
                cast.step = 0;
                cast.totalSteps = SealTowerElaina.SKILL7_COUNT;
                cast.endAt = now + interval * SealTowerElaina.SKILL7_COUNT;
                cast.targetPlayer = targetPlayer.getUUID();
                s.activeCast = cast;
                e.getNavigation().stop();
                e.play(skill.id, Math.max(800L, cast.endAt - now));
            }
            case ELEMENTALIST_AWAKENING -> {
                long duration = timing.active(SealTowerElaina.SKILL8_DURATION_MS);
                ActiveCast cast = new ActiveCast(skill);
                cast.startAt = now;
                cast.endAt = now + duration;
                cast.nextAt = now;
                cast.step = 0;
                cast.totalSteps = SealTowerElaina.SKILL8_METEOR_COUNT;
                cast.targetPlayer = targetPlayer.getUUID();
                cast.anchor = AwakeningTowerLayout.ground(owner.level(), d.cell, targetPlayer.position(), targetPlayer.position());
                cast.startAngle = Math.atan2(e.getZ() - cast.anchor.z, e.getX() - cast.anchor.x);
                s.activeCast = cast;
                e.getNavigation().stop();
                e.setNoGravity(true);
                e.play(skill.id, duration);
            }
            case ELEMENTALIST_TRIPLE_CAST -> {
                long castMs = timing.active(SealTowerElaina.SKILL9_CAST_MS);
                ActiveCast cast = new ActiveCast(skill);
                cast.startAt = now;
                cast.nextAt = now;
                cast.endAt = now + castMs;
                cast.step = 0;
                cast.totalSteps = SealTowerElaina.SKILL9_COUNT;
                cast.targetPlayer = targetPlayer.getUUID();
                List<Integer> pool = new ArrayList<>(List.of(0, 1, 2, 3));
                Collections.shuffle(pool, random);
                cast.tripleOrder = new int[]{pool.get(0), pool.get(1), pool.get(2)};
                s.activeCast = cast;
                e.getNavigation().stop();
                e.play(skill.id, castMs);
            }
            case CLOCK_REWIND -> {
                List<RewindAnchor> anchors = new ArrayList<>();
                JsonArray anchorArray = new JsonArray();
                for (ServerPlayer f : fighters) {
                    anchors.add(new RewindAnchor(f.getUUID(), f.getX(), f.getY(), f.getZ(), f.getYRot()));
                    JsonObject obj = new JsonObject();
                    obj.addProperty("player", f.getStringUUID());
                    obj.addProperty("x", f.getX());
                    obj.addProperty("y", f.getY());
                    obj.addProperty("z", f.getZ());
                    anchorArray.add(obj);
                }
                String eid = instanceId + "/rewind/" + (++s.sequence);
                s.rewinds.add(new PendingRewind(eid, now + SealTowerElaina.SKILL10_DELAY_MS, anchors));
                JsonObject pkt = new JsonObject();
                pkt.addProperty("kind", "REWIND_ANCHOR");
                pkt.addProperty("instance", instanceId);
                pkt.addProperty("eventId", eid + "/anchor");
                pkt.addProperty("durationMs", SealTowerElaina.SKILL10_DELAY_MS);
                pkt.add("anchors", anchorArray);
                owner.broadcast(instanceId, "sealBossEvent", pkt);
                e.play(skill.id, timing.recovery(800L));
                s.busyUntil = now + timing.recovery(800L);
            }
            case TIME_SEAL -> {
                long castMs = timing.windup(SealTowerElaina.SKILL11_CAST_MS);
                ActiveCast cast = new ActiveCast(skill);
                cast.startAt = now;
                cast.endAt = now + castMs;
                cast.subActive = false;
                s.activeCast = cast;
                e.getNavigation().stop();
                e.play(skill.id, castMs);
            }
            case PUMPKIN_SPIN -> {
                long duration = timing.active(SealTowerElaina.SKILL12_DURATION_MS);
                ActiveCast cast = new ActiveCast(skill);
                cast.startAt = now;
                cast.endAt = now + duration;
                cast.nextAt = now;
                cast.step = 0;
                cast.totalSteps = SealTowerElaina.SKILL12_BOMB_COUNT;
                Vec3 center = AwakeningTowerLayout.guard(d.cell);
                cast.anchor = new Vec3(center.x, 80.0 + SealTowerElaina.SKILL12_HOVER_OFFSET, center.z);
                cast.startAngle = Math.atan2(e.getZ() - center.z, e.getX() - center.x);
                s.activeCast = cast;
                e.getNavigation().stop();
                e.setNoGravity(true);
                e.play(skill.id, duration);
            }
            default -> {}
        }
    }

    private void tickActiveCast(Dungeon d, DungeonEnemy e, Target t, State s, List<ServerPlayer> fighters, long now) throws Exception {
        ActiveCast cast = s.activeCast;
        if (cast == null) return;
        Target.ActionTiming timing = t.actionTiming(now);
        String instanceId = d.id.toString();

        switch (cast.skill) {
            case WAND_CHARGE -> {
                if (cast.subActive) {
                    double progress = Math.clamp((now - cast.startAt) / (double) Math.max(1L, cast.endAt - cast.startAt), 0.0, 1.0);
                    Vec3 pos = cast.from.lerp(cast.to, progress);
                    e.setPos(pos.x, pos.y, pos.z);
                    e.setDeltaMovement(Vec3.ZERO);
                    facePosition(e, cast.to);
                    if (now >= cast.endAt) {
                        cast.step++;
                        if (cast.step >= cast.totalSteps) {
                            interruptActiveCast(d, e, t, s, true);
                            s.busyUntil = now + 700L;
                            return;
                        }
                        cast.subActive = false;
                        cast.nextAt = now + timing.duration(SealTowerElaina.SKILL1_PAUSE_MS);
                    }
                } else if (now >= cast.nextAt) {
                    beginWandChargeSegment(d, e, t, s, cast, fighters, now);
                }
            }
            case ELEMENTAL_ORBS -> {
                e.setPos(cast.anchor.x, cast.anchor.y + Math.sin(now / 260.0) * 0.25, cast.anchor.z);
                e.setDeltaMovement(Vec3.ZERO);
                long interval = Math.max(50L, timing.duration(SealTowerElaina.SKILL3_SPAWN_INTERVAL_MS));
                while (cast.step < cast.totalSteps && now >= cast.nextAt) {
                    cast.step++;
                    cast.nextAt += interval;
                    if (!fighters.isEmpty()) {
                        ServerPlayer pick = fighters.get(random.nextInt(fighters.size()));
                        double angle = random.nextDouble() * Math.PI * 2;
                        double dist = random.nextDouble() * SealTowerElaina.SKILL3_SCATTER_RADIUS;
                        Vec3 raw = pick.position().add(Math.cos(angle) * dist, 0, Math.sin(angle) * dist);
                        Vec3 landing = AwakeningTowerLayout.ground(owner.level(), d.cell, raw, AwakeningTowerLayout.insideBattle(d.cell, raw));
                        int orbType = random.nextInt(3); // 0=冰, 1=火, 2=土
                        String typeName = orbType == 0 ? "ORB_ICE" : orbType == 1 ? "ORB_FIRE" : "ORB_EARTH";
                        int element = orbType == 0 ? SealTowerElaina.ELEMENT_ICE : orbType == 1 ? SealTowerElaina.ELEMENT_FIRE : SealTowerElaina.ELEMENT_NONE;
                        int color = orbType == 0 ? 0x7ce6ff : orbType == 1 ? 0xff7a3c : 0xc89342;
                        long warnUntil = now + SealTowerElaina.SKILL3_ORB_WARNING_MS;
                        String hid = instanceId + "/orb/" + (++s.sequence);
                        owner.broadcast(instanceId, "sealBossHazard", hazardBase(instanceId, hid, typeName, t,
                            landing, landing, SealTowerElaina.SKILL3_ORB_RADIUS, 0,
                            now, warnUntil, warnUntil + 400L, 0,
                            SealTowerElaina.COEFF_MEDIUM, true, element, color));
                    }
                }
                if (now >= cast.endAt && cast.step >= cast.totalSteps) {
                    interruptActiveCast(d, e, t, s, true);
                    s.busyUntil = now + 700L;
                }
            }
            case BLINK_FLIGHT -> {
                if (now >= cast.nextAt) {
                    double progress = Math.clamp((now - cast.nextAt) / (double) Math.max(1L, cast.endAt - cast.nextAt), 0.0, 1.0);
                    Vec3 pos = cast.from.lerp(cast.to, progress);
                    e.setPos(pos.x, pos.y, pos.z);
                    e.setDeltaMovement(Vec3.ZERO);
                }
                if (now >= cast.endAt) {
                    e.setPos(cast.to.x, cast.to.y, cast.to.z);
                    e.setDeltaMovement(Vec3.ZERO);
                    e.setNoGravity(cast.to.y > 81.2);
                    s.activeCast = null;
                    s.busyUntil = now + 400L;
                }
            }
            case DRINK_POTION -> {
                if (!cast.subActive && now >= cast.nextAt) {
                    cast.subActive = true;
                    t.receive(SealTowerElaina.SKILL5_HEAL_FRACTION, true);
                    commitTarget(d, e, t);
                    e.setHealth((float) Math.max(.001, e.getMaxHealth() * t.life.q));
                }
                if (now >= cast.endAt) {
                    s.activeCast = null;
                    s.busyUntil = now + 600L;
                }
            }
            case CORONA_BARRAGE -> {
                ServerPlayer target = resolveFighter(fighters, cast.targetPlayer, e);
                if (target != null) facePosition(e, target.position());
                long interval = Math.max(50L, timing.duration(SealTowerElaina.SKILL7_SPAWN_INTERVAL_MS));
                while (cast.step < cast.totalSteps && now >= cast.nextAt) {
                    cast.step++;
                    cast.nextAt += interval;
                    if (target != null) {
                        Vec3 landing = AwakeningTowerLayout.ground(owner.level(), d.cell, target.position(), target.position());
                        long warnUntil = now + SealTowerElaina.SKILL7_DELAY_MS;
                        String hid = instanceId + "/corona/" + (++s.sequence);
                        owner.broadcast(instanceId, "sealBossHazard", hazardBase(instanceId, hid, "CORONA", t,
                            landing, landing, SealTowerElaina.SKILL7_RADIUS, 0,
                            now, warnUntil, warnUntil + 450L, 0,
                            SealTowerElaina.COEFF_HIGH, true, SealTowerElaina.ELEMENT_FIRE, 0xff9438));
                    }
                }
                if (now >= cast.endAt && cast.step >= cast.totalSteps) {
                    s.activeCast = null;
                    s.busyUntil = now + 800L;
                }
            }
            case ELEMENTALIST_AWAKENING -> {
                ServerPlayer target = resolveFighter(fighters, cast.targetPlayer, e);
                Vec3 center = target != null ? target.position() : cast.anchor;
                double duration = Math.max(1.0, cast.endAt - cast.startAt);
                double progress = Math.clamp((now - cast.startAt) / duration, 0.0, 1.0);
                double orbitAngle = cast.startAngle + progress * Math.PI * 3.0;
                Vec3 orbitPos = AwakeningTowerLayout.insideBattle(d.cell, new Vec3(
                    center.x + Math.cos(orbitAngle) * SealTowerElaina.SKILL8_ORBIT_RADIUS,
                    center.y + SealTowerElaina.SKILL8_HOVER_OFFSET,
                    center.z + Math.sin(orbitAngle) * SealTowerElaina.SKILL8_ORBIT_RADIUS
                ));
                e.setPos(orbitPos.x, orbitPos.y, orbitPos.z);
                e.setDeltaMovement(Vec3.ZERO);
                facePosition(e, center);

                long interval = Math.max(50L, timing.duration(SealTowerElaina.SKILL8_SPAWN_INTERVAL_MS));
                int[] colors = {0xffe066, 0xff7b39, 0x75d8f5, 0xa864f5};
                while (cast.step < cast.totalSteps && now >= cast.nextAt) {
                    cast.step++;
                    cast.nextAt += interval;
                    double ang = random.nextDouble() * Math.PI * 2.0;
                    double r = Math.sqrt(random.nextDouble()) * SealTowerElaina.SKILL8_FIELD_RADIUS;
                    Vec3 raw = center.add(Math.cos(ang) * r, 0, Math.sin(ang) * r);
                    Vec3 landing = AwakeningTowerLayout.ground(owner.level(), d.cell, raw, AwakeningTowerLayout.insideBattle(d.cell, raw));
                    int elem = random.nextInt(4);
                    long warnUntil = now + SealTowerElaina.SKILL8_METEOR_WARNING_MS;
                    String hid = instanceId + "/el_ast/" + (++s.sequence);
                    owner.broadcast(instanceId, "sealBossHazard", hazardBase(instanceId, hid, "ELAINA_ASTRAL_METEOR", t,
                        landing, landing, SealTowerElaina.SKILL8_METEOR_RADIUS, 0,
                        now, warnUntil, warnUntil + 450L, 0,
                        SealTowerElaina.COEFF_MEDIUM, true, elem, colors[elem]));
                }
                if (now >= cast.endAt && cast.step >= cast.totalSteps) {
                    interruptActiveCast(d, e, t, s, true);
                    s.busyUntil = now + 700L;
                }
            }
            case ELEMENTALIST_TRIPLE_CAST -> {
                ServerPlayer target = resolveFighter(fighters, cast.targetPlayer, e);
                if (target != null) facePosition(e, target.position());
                long interval = Math.max(100L, timing.duration(SealTowerElaina.SKILL9_INTERVAL_MS));
                while (cast.step < cast.totalSteps && now >= cast.nextAt) {
                    int pick = cast.tripleOrder != null && cast.step < cast.tripleOrder.length ? cast.tripleOrder[cast.step] : cast.step % 4;
                    cast.step++;
                    cast.nextAt += interval;
                    if (target != null) {
                        Vec3 landing = AwakeningTowerLayout.ground(owner.level(), d.cell, target.position(), target.position());
                        String typeName;
                        double radius;
                        int elem;
                        int color;
                        switch (pick) {
                            case 0 -> { typeName = "ELAINA_TRIPLE_JACK"; radius = SealTowerElaina.SKILL9_JACK_RADIUS; elem = SealTowerElaina.ELEMENT_FIRE; color = 0xff7b39; }
                            case 1 -> { typeName = "ELAINA_TRIPLE_ARCTIC"; radius = SealTowerElaina.SKILL9_ARCTIC_RADIUS; elem = SealTowerElaina.ELEMENT_ICE; color = 0x75d8f5; }
                            case 2 -> { typeName = "ELAINA_TRIPLE_BLACKHOLE"; radius = SealTowerElaina.SKILL9_BLACKHOLE_RADIUS; elem = SealTowerElaina.ELEMENT_DARK; color = 0xa864f5; }
                            default -> { typeName = "ELAINA_TRIPLE_THUNDER"; radius = SealTowerElaina.SKILL9_THUNDER_RADIUS; elem = SealTowerElaina.ELEMENT_LIGHT; color = 0xffe066; }
                        }
                        long warnUntil = now + SealTowerElaina.SKILL9_WARNING_MS;
                        String hid = instanceId + "/el_tri/" + (++s.sequence);
                        owner.broadcast(instanceId, "sealBossHazard", hazardBase(instanceId, hid, typeName, t,
                            landing, landing, radius, 0,
                            now, warnUntil, warnUntil + 500L, 0,
                            SealTowerElaina.COEFF_HIGH, true, elem, color));
                    }
                }
                if (now >= cast.endAt && cast.step >= cast.totalSteps) {
                    s.activeCast = null;
                    s.busyUntil = now + 800L;
                }
            }
            case TIME_SEAL -> {
                if (now >= cast.endAt) {
                    String eid = instanceId + "/seal/" + (++s.sequence);
                    broadcastEvent(instanceId, Map.of(
                        "kind", "SEAL_SKILL_999999S",
                        "instance", instanceId,
                        "eventId", eid,
                        "durationMs", SealTowerElaina.SKILL11_SEAL_DURATION_MS
                    ));
                    s.activeCast = null;
                    s.busyUntil = now + 900L;
                }
            }
            case PUMPKIN_SPIN -> {
                double duration = Math.max(1.0, cast.endAt - cast.startAt);
                double progress = Math.clamp((now - cast.startAt) / duration, 0.0, 1.0);
                double orbitAngle = cast.startAngle + progress * Math.PI * 2.0;
                Vec3 orbitPos = AwakeningTowerLayout.insideBattle(d.cell, new Vec3(
                    cast.anchor.x + Math.cos(orbitAngle) * SealTowerElaina.SKILL12_ORBIT_RADIUS,
                    cast.anchor.y,
                    cast.anchor.z + Math.sin(orbitAngle) * SealTowerElaina.SKILL12_ORBIT_RADIUS
                ));
                e.setPos(orbitPos.x, orbitPos.y, orbitPos.z);
                e.setDeltaMovement(Vec3.ZERO);
                float spinYaw = (float) ((now * 0.72) % 360.0);
                e.setYRot(spinYaw);
                e.setYHeadRot(spinYaw);
                e.yBodyRot = spinYaw;

                long interval = Math.max(50L, timing.duration(SealTowerElaina.SKILL12_THROW_INTERVAL_MS));
                while (cast.step < cast.totalSteps && now >= cast.nextAt) {
                    cast.step++;
                    cast.nextAt += interval;
                    if (!fighters.isEmpty()) {
                        ServerPlayer pick = fighters.get(random.nextInt(fighters.size()));
                        double ox = (random.nextDouble() * 2 - 1) * SealTowerElaina.SKILL12_SPREAD;
                        double oz = (random.nextDouble() * 2 - 1) * SealTowerElaina.SKILL12_SPREAD;
                        Vec3 raw = pick.position().add(ox, 0, oz);
                        Vec3 landing = AwakeningTowerLayout.ground(owner.level(), d.cell, raw, AwakeningTowerLayout.insideBattle(d.cell, raw));
                        long warnUntil = now + SealTowerElaina.SKILL12_BOMB_WARNING_MS;
                        String hid = instanceId + "/pumpkin/" + (++s.sequence);
                        owner.broadcast(instanceId, "sealBossHazard", hazardBase(instanceId, hid, "PUMPKIN", t,
                            orbitPos.add(0, 0.8, 0), landing, SealTowerElaina.SKILL12_BOMB_RADIUS, 0,
                            now, warnUntil, warnUntil + 350L, 0,
                            SealTowerElaina.COEFF_MEDIUM, true, SealTowerElaina.ELEMENT_FIRE, 0xff7d26));
                    }
                }
                if (now >= cast.endAt && cast.step >= cast.totalSteps) {
                    interruptActiveCast(d, e, t, s, true);
                    s.busyUntil = now + 700L;
                }
            }
            default -> s.activeCast = null;
        }
    }

    private void beginWandChargeSegment(Dungeon d, DungeonEnemy e, Target t, State s, ActiveCast cast, List<ServerPlayer> fighters, long now) {
        ServerPlayer target = resolveFighter(fighters, cast.targetPlayer, e);
        Vec3 playerPos = target != null ? target.position() : AwakeningTowerLayout.guard(d.cell);
        Vec3 startPos = pickWandChargeStart(owner.level(), d.cell, playerPos, random);
        Vec3 delta = playerPos.subtract(startPos).multiply(1, 0, 1);
        Vec3 dir = delta.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : delta.normalize();
        double flightDist = Math.min(SealTowerElaina.SKILL1_RING_RADIUS + SealTowerElaina.SKILL1_OVERSHOOT,
            Math.max(8.0, delta.length() + SealTowerElaina.SKILL1_OVERSHOOT));
        Vec3 endRaw = startPos.add(dir.scale(flightDist));
        Vec3 endPos = AwakeningTowerLayout.insideBattle(d.cell, new Vec3(endRaw.x, playerPos.y + 0.9, endRaw.z));
        double actualDist = startPos.distanceTo(endPos);
        long baseDashMs = Math.max(300L, Math.min(SealTowerElaina.SKILL1_DASH_MS,
            Math.round(actualDist / SealTowerElaina.SKILL1_SPEED * 1000.0)));
        long dashMs = t.actionTiming(now).active(baseDashMs);

        cast.from = startPos;
        cast.to = endPos;
        cast.startAt = now;
        cast.endAt = now + dashMs;
        cast.subActive = true;

        e.setPos(startPos.x, startPos.y, startPos.z);
        e.setDeltaMovement(Vec3.ZERO);
        facePosition(e, endPos);

        String hid = d.id + "/charge/" + (++s.sequence);
        owner.broadcast(d.id.toString(), "sealBossHazard", hazardBase(d.id.toString(), hid, "WAND_CHARGE", t,
            startPos, endPos, actualDist, SealTowerElaina.SKILL1_WIDTH,
            now, now, now + dashMs, 0,
            SealTowerElaina.COEFF_MEDIUM, true, SealTowerElaina.ELEMENT_NONE, 0xd8b4ff));
    }

    private void tickTrackingStars(Dungeon d, DungeonEnemy e, Target t, State s, List<ServerPlayer> fighters, long now) {
        if (s.stars.isEmpty()) return;
        String instanceId = d.id.toString();
        Iterator<TrackingStarHazard> it = s.stars.iterator();
        while (it.hasNext()) {
            TrackingStarHazard star = it.next();
            if (now >= star.endAt) {
                it.remove();
                continue;
            }
            if (now < star.warnUntil) continue;
            long dtMs = Math.max(0L, now - star.lastMoveAt);
            star.lastMoveAt = now;
            ServerPlayer target = resolveFighter(fighters, star.targetPlayer, e);
            if (target != null && dtMs > 0) {
                Vec3 diff = target.position().subtract(star.pos).multiply(1, 0, 1);
                double dist = diff.length();
                if (dist > 0.05) {
                    double step = Math.min(dist, SealTowerElaina.SKILL2_SPEED * (dtMs / 1000.0));
                    Vec3 nextRaw = star.pos.add(diff.normalize().scale(step));
                    star.pos = AwakeningTowerLayout.ground(owner.level(), d.cell, nextRaw, AwakeningTowerLayout.insideBattle(d.cell, nextRaw));
                }
            }
            if (now - star.lastBroadcastAt >= SealTowerElaina.SKILL2_SYNC_INTERVAL_MS) {
                star.lastBroadcastAt = now;
                JsonObject move = new JsonObject();
                move.addProperty("id", star.id);
                move.addProperty("instance", instanceId);
                move.addProperty("moveOnly", true);
                move.addProperty("moveDuration", SealTowerElaina.SKILL2_SYNC_INTERVAL_MS);
                move.addProperty("x", star.pos.x);
                move.addProperty("y", star.pos.y);
                move.addProperty("z", star.pos.z);
                owner.broadcast(instanceId, "sealBossHazard", move);
            }
        }
    }

    private void tickPendingRewinds(Dungeon d, State s, long now) {
        if (s.rewinds.isEmpty()) return;
        String instanceId = d.id.toString();
        Iterator<PendingRewind> it = s.rewinds.iterator();
        while (it.hasNext()) {
            PendingRewind rewind = it.next();
            if (now < rewind.commitAt) continue;
            it.remove();
            for (RewindAnchor anchor : rewind.anchors) {
                ServerPlayer player = server.getPlayerList().getPlayer(anchor.playerId());
                if (player == null || !owner.participant(player, instanceId)
                    || player.level().dimension() != DungeonService.DIMENSION
                    || players.session(player).life.dead) {
                    continue;
                }
                player.stopRiding();
                player.setDeltaMovement(Vec3.ZERO);
                player.fallDistance = 0;
                player.teleportTo(owner.level(), anchor.x(), anchor.y(), anchor.z(),
                    Set.of(), anchor.yaw(), player.getXRot(), true);
                JsonObject pkt = new JsonObject();
                pkt.addProperty("kind", "REWIND_COMMIT");
                pkt.addProperty("instance", instanceId);
                pkt.addProperty("eventId", rewind.eventId + "/" + anchor.playerId());
                pkt.addProperty("x", anchor.x());
                pkt.addProperty("y", anchor.y());
                pkt.addProperty("z", anchor.z());
                Network.send(player, "sealBossEvent", pkt);
            }
        }
    }

    private void interruptActiveCast(Dungeon d, DungeonEnemy e, Target t, State s, boolean landIfNeeded) throws Exception {
        if (s.activeCast != null) {
            s.activeCast = null;
        }
        if (t.damageTakenMultiplier != 1.0) {
            t.damageTakenMultiplier = 1.0;
            commitTarget(d, e, t);
        }
        if (landIfNeeded && e.isNoGravity()) {
            landToGround(e, d.cell, e.position());
        }
    }

    /** 技能 1：圆周 30 格合法切入点搜索算法（受限于战场围墙时选取可用扇区内最远合法点）。 */
    public static Vec3 pickWandChargeStart(ServerLevel world, int cell, Vec3 playerPos, Random rng) {
        double desiredDist = SealTowerElaina.SKILL1_RING_RADIUS;
        int offsetSeed = rng.nextInt(16);
        Vec3 best = null;
        double bestDistSq = -1;
        for (int i = 0; i < 16; i++) {
            double angle = (offsetSeed + i) * (Math.PI / 8.0);
            Vec3 raw = playerPos.add(Math.cos(angle) * desiredDist, 1.2, Math.sin(angle) * desiredDist);
            Vec3 clamped = AwakeningTowerLayout.insideBattle(cell, raw);
            double dSq = clamped.subtract(playerPos).horizontalDistanceSqr();
            if (dSq > bestDistSq && world.noCollision(new AABB(
                clamped.x - 0.4, clamped.y, clamped.z - 0.4,
                clamped.x + 0.4, clamped.y + 1.9, clamped.z + 0.4))) {
                bestDistSq = dSq;
                best = clamped;
                if (dSq >= 28.0 * 28.0) break;
            }
        }
        return best != null ? best : AwakeningTowerLayout.guard(cell).add(0, 1.2, 0);
    }

    private Vec3 pickBlinkDestination(ServerLevel world, int cell, Vec3 current, Vec3 playerPos, boolean preferGround) {
        int seed = random.nextInt(16);
        for (int i = 0; i < 16; i++) {
            double angle = (seed + i) * (Math.PI / 8.0);
            Vec3 raw = current.add(Math.cos(angle) * SealTowerElaina.SKILL4_DISTANCE, 0, Math.sin(angle) * SealTowerElaina.SKILL4_DISTANCE);
            Vec3 clamped = AwakeningTowerLayout.insideBattle(cell, raw);
            Vec3 ground = AwakeningTowerLayout.ground(world, cell, clamped, clamped);
            if (preferGround) {
                double distToPlayer = ground.subtract(playerPos).horizontalDistance();
                if (distToPlayer >= 8.0 && distToPlayer <= 14.0
                    && world.noCollision(new AABB(ground.x - 0.4, ground.y, ground.z - 0.4, ground.x + 0.4, ground.y + 1.9, ground.z + 0.4))) {
                    return ground;
                }
            } else {
                double lift = random.nextDouble() * 4.0;
                Vec3 candidate = AwakeningTowerLayout.insideBattle(cell, ground.add(0, lift, 0));
                if (world.noCollision(new AABB(candidate.x - 0.4, candidate.y, candidate.z - 0.4, candidate.x + 0.4, candidate.y + 1.9, candidate.z + 0.4))) {
                    return candidate;
                }
            }
        }
        return AwakeningTowerLayout.ground(world, cell, AwakeningTowerLayout.guard(cell), AwakeningTowerLayout.guard(cell));
    }

    private void hoverAtCenter(DungeonEnemy e, int cell, double y) {
        Vec3 center = AwakeningTowerLayout.guard(cell);
        e.getNavigation().stop();
        e.setNoGravity(true);
        e.setPos(center.x, y, center.z);
        e.setDeltaMovement(Vec3.ZERO);
    }

    private void landToGround(DungeonEnemy e, int cell, Vec3 hint) {
        Vec3 safe = AwakeningTowerLayout.ground(owner.level(), cell, hint, AwakeningTowerLayout.guard(cell));
        e.setNoGravity(false);
        e.setPos(safe.x, safe.y, safe.z);
        e.setDeltaMovement(Vec3.ZERO);
    }

    private static void facePosition(DungeonEnemy e, Vec3 target) {
        double dx = target.x - e.getX(), dz = target.z - e.getZ();
        if (dx * dx + dz * dz < 1e-6) return;
        float yaw = (float) (Math.toDegrees(Math.atan2(-dx, dz)));
        e.setYRot(yaw);
        e.setYHeadRot(yaw);
        e.yBodyRot = yaw;
    }

    private static ServerPlayer nearestFighter(DungeonEnemy e, List<ServerPlayer> fighters) {
        return fighters.stream().min(Comparator.comparingDouble(e::distanceToSqr)).orElse(null);
    }

    private static ServerPlayer resolveFighter(List<ServerPlayer> fighters, UUID preferred, DungeonEnemy e) {
        if (preferred != null) {
            for (ServerPlayer f : fighters) if (preferred.equals(f.getUUID())) return f;
        }
        return nearestFighter(e, fighters);
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

    private void broadcastEvent(String instanceId, Map<String, Object> payload) {
        owner.broadcast(instanceId, "sealBossEvent", payload);
    }

    private void sendMidnight(ServerPlayer player, String instanceId, boolean active) {
        Network.send(player, "sealBossEvent", Map.of(
            "kind", "MIDNIGHT",
            "instance", instanceId,
            "active", active
        ));
    }

    private static JsonObject hazardBase(String instanceId, String id, String type, Target boss,
                                         Vec3 from, Vec3 to, double radius, double width,
                                         long startAt, long warnUntil, long endAt, long tickMs,
                                         double coefficient, boolean magic, int element, int color) {
        JsonObject j = new JsonObject();
        j.addProperty("id", id);
        j.addProperty("instance", instanceId);
        j.addProperty("type", type);
        j.add("boss", JSON.toJsonTree(boss));
        j.addProperty("x", from.x);
        j.addProperty("y", from.y);
        j.addProperty("z", from.z);
        j.addProperty("tx", to.x);
        j.addProperty("ty", to.y);
        j.addProperty("tz", to.z);
        j.addProperty("radius", radius);
        j.addProperty("width", width);
        j.addProperty("startAt", startAt);
        j.addProperty("warnUntil", warnUntil);
        j.addProperty("endAt", endAt);
        j.addProperty("tickMs", tickMs);
        j.addProperty("coefficient", coefficient);
        j.addProperty("magic", magic);
        j.addProperty("element", element);
        j.addProperty("color", color);
        return j;
    }

    /**
     * 技能 6（巫师护盾被动）与护盾/锁血上下文同步挂钩。
     * 1) 玩家受击回执命中（outcome == "HIT"）且来源为伊蕾娜且被动盾已解锁时，将伊蕾娜护盾补至至少 5% 最大生命。
     * 2) 玩家攻击伊蕾娜后（护盾消耗或锁血触发），发布最新 Target 上下文以同步顶部护盾条。
     */
    @Override
    public CombatPorts.Commit prepare(ServerPlayer player, LivingEntity target, JsonObject report, Receipt receipt, Life actorLife) {
        // 情况 A：玩家上报自身被伊蕾娜技能命中 -> 触发技能 6 被动护盾
        if (target == player && report.has("feedbackEntity")
            && (!report.has("heal") || !report.get("heal").getAsBoolean())
            && (!report.has("outcome") || "HIT".equals(report.get("outcome").getAsString()))) {
            UUID bossId;
            try {
                bossId = UUID.fromString(report.get("feedbackEntity").getAsString());
            } catch (IllegalArgumentException ignored) {
                return CombatPorts.Commit.NONE;
            }
            State state = findByBoss(bossId);
            if (state == null || !state.passiveShieldUnlocked) return CombatPorts.Commit.NONE;
            Entity raw = owner.level().getEntity(bossId);
            if (!(raw instanceof DungeonEnemy boss) || !boss.isAlive()) return CombatPorts.Commit.NONE;
            Target bossTarget = targets.target(boss);
            if (bossTarget.life.dead || bossTarget.shield >= SealTowerElaina.SKILL6_PASSIVE_SHIELD_FRACTION) {
                return CombatPorts.Commit.NONE;
            }
            bossTarget.shield = SealTowerElaina.SKILL6_PASSIVE_SHIELD_FRACTION;
            bossTarget.life.revision++;
            String instanceId = state.instanceId;
            return new CombatPorts.Commit() {
                @Override
                public void persist() throws Exception {
                    journal.put("target/" + bossId, bossTarget);
                }

                @Override
                public void publish() {
                    boss.setData(CombatContentTypes.TARGET, JSON.toJson(bossTarget));
                    for (ServerPlayer observer : server.getPlayerList().getPlayers()) {
                        if (owner.participant(observer, instanceId)) targets.context(observer, boss);
                    }
                }
            };
        }

        // 情况 B：玩家对伊蕾娜造成伤害，护盾/锁血/减伤状态更新后向场内同步最新 Target
        if (target instanceof DungeonEnemy boss && SealTowerElaina.ID.equals(boss.appearance()) && receipt.accepted()) {
            State state = findByBoss(boss.getUUID());
            if (state == null) return CombatPorts.Commit.NONE;
            String instanceId = state.instanceId;
            return new CombatPorts.Commit() {
                @Override
                public void publish() {
                    for (ServerPlayer observer : server.getPlayerList().getPlayers()) {
                        if (owner.participant(observer, instanceId)) targets.context(observer, boss);
                    }
                }
            };
        }

        return CombatPorts.Commit.NONE;
    }

    private State findByBoss(UUID bossId) {
        for (State s : states.values()) {
            if (bossId.equals(s.bossId)) return s;
        }
        return null;
    }

    /** 首领被击杀：立即取消第2轮双巨型陨石、清理所有场地状态与玩家身上的 999999s 软狂暴冷却封印。 */
    void died(UUID bossId) {
        State s = findByBoss(bossId);
        if (s == null) return;
        states.remove(s.instanceId);
        Dungeon d = owner.byId(s.instanceId);
        cleanupEncounter(d, s.instanceId);
    }

    void cancel(UUID bossId) {
        // 伊蕾娜免受普通眩晕打断阶段演出，但取消记录保留兜底（不销毁阶段状态）
    }

    /** 清层、团灭回第46层、通关或关闭场次时调用：彻底清理状态、投射物、午夜天象与 999999s 软狂暴冷却。 */
    void close(Dungeon d) {
        if (d == null) return;
        String instanceId = d.id.toString();
        State removed = states.remove(instanceId);
        if (removed != null || d.tower != null) {
            cleanupEncounter(d, instanceId);
        }
    }

    /** 单个玩家提前离塔/断线时执行双保险清理。 */
    void playerLeft(ServerPlayer player, Dungeon d) {
        if (d == null) return;
        String instanceId = d.id.toString();
        State s = states.get(instanceId);
        if (s != null) s.syncedMidnightPlayers.remove(player.getUUID());
        Network.send(player, "sealBossEvent", Map.of(
            "kind", "CLEAR_ENCOUNTER_EFFECTS",
            "instance", instanceId
        ));
        clearServerSealedCooldowns(player.getUUID(), System.currentTimeMillis());
    }

    private void cleanupEncounter(Dungeon d, String instanceId) {
        broadcastEvent(instanceId, Map.of("kind", "CLEAR_HAZARDS", "instance", instanceId));
        broadcastEvent(instanceId, Map.of("kind", "CLEAR_ENCOUNTER_EFFECTS", "instance", instanceId));
        long now = System.currentTimeMillis();
        if (d != null) {
            for (UUID playerId : d.roster) {
                clearServerSealedCooldowns(playerId, now);
            }
        }
    }

    /** 服务端双保险：移除超出正常上限（>300s）的伊蕾娜 999999s 软狂暴冷却，防止断线残留进永久存档。 */
    private void clearServerSealedCooldowns(UUID playerId, long now) {
        try {
            ServerPlayer online = server.getPlayerList().getPlayer(playerId);
            if (online != null) {
                Runtime runtime = players.session(online).resources;
                if (sanitizeSealedCooldowns(runtime, now)) {
                    runtime.controlEpoch++;
                    journal.put("resources/" + playerId, runtime);
                    players.profile(online);
                }
                return;
            }
            Runtime stored = journal.get("resources/" + playerId, Runtime.class, null);
            if (stored != null && sanitizeSealedCooldowns(stored, now)) {
                stored.controlEpoch++;
                journal.put("resources/" + playerId, stored);
            }
        } catch (Exception ignored) {}
    }

    /** 纯规则清理函数（便于无头测试验证）：移除剩余时间超过 300s 的软狂暴封印冷却。 */
    public static boolean sanitizeSealedCooldowns(Runtime runtime, long now) {
        if (runtime == null || runtime.cooldowns == null || runtime.cooldowns.isEmpty()) return false;
        return runtime.cooldowns.entrySet().removeIf(entry ->
            entry.getValue() != null && entry.getValue() - now > SealTowerElaina.SERVER_SEAL_CLEANUP_THRESHOLD_MS);
    }
}
