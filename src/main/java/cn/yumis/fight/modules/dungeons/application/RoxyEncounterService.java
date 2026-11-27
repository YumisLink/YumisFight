package cn.yumis.fight.modules.dungeons.application;

import cn.yumis.fight.foundation.Journal;
import cn.yumis.fight.modules.combat.api.CombatContentTypes;
import cn.yumis.fight.modules.dungeons.api.AwakeningTower;
import cn.yumis.fight.modules.dungeons.api.Dungeon;
import cn.yumis.fight.modules.dungeons.api.DungeonContentTypes;
import cn.yumis.fight.modules.dungeons.api.DungeonEnemy;
import cn.yumis.fight.modules.dungeons.api.DungeonPorts;
import cn.yumis.fight.modules.dungeons.api.RitualMechanismEntity;
import cn.yumis.fight.modules.dungeons.api.Target;
import cn.yumis.fight.modules.dungeons.content.AwakeningTowerLayout;
import cn.yumis.fight.modules.dungeons.content.SluiceTowerRoxy;
import cn.yumis.fight.modules.dungeons.content.SluiceTowerRoxy.SkillId;
import cn.yumis.fight.modules.presentation.api.ModText;
import cn.yumis.fight.network.Network;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/**
 * 水闸塔第 20 层专属首领「洛琪希」服务端双阶段状态机、12 秒《豪雷积雨云》破核转场与 7 招水雷调度服务。
 * <p>
 * 权威与同步边界：
 * - 服务端仅维护洛琪希实体、双管血复活转场、3 枚可破坏「水魔导核心」机制实体、招式编排与时间轴；
 * - 暴雨天象、水圣弹幕轨迹、台风眼安全区与全部玩家受击均由客户端权威判定，服务器不发送高频位置流（<= 4Hz）。
 */
public final class RoxyEncounterService {
    private static final Gson JSON = new GsonBuilder().disableHtmlEscaping().create();

    public enum Phase {
        PHASE_1,
        TRANSITION_CHANT,
        PHASE_2
    }

    static final class ActiveCore {
        final String coreKey;
        final UUID entityId;
        final int numericId;
        final Vec3 pos;

        ActiveCore(String coreKey, UUID entityId, int numericId, Vec3 pos) {
            this.coreKey = coreKey;
            this.entityId = entityId;
            this.numericId = numericId;
            this.pos = pos;
        }
    }

    static final class ActiveCast {
        final SkillId skill;
        long startAt, nextAt, endAt;
        long windupMs, activeMs, auxMs;
        int step, totalSteps, subPhase;
        double startAngle, sweepSign;
        Vec3 from = Vec3.ZERO, to = Vec3.ZERO, anchor = Vec3.ZERO, direction = new Vec3(0, 0, 1);

        ActiveCast(SkillId skill) {
            this.skill = skill;
        }
    }

    public static final class State {
        public UUID bossId;
        public String instanceId;
        public int cell;
        public Phase phase = Phase.PHASE_1;
        public boolean stormActive;
        public long transitionStartAt, transitionEndAt;
        public long nextTransitionBoltAt;
        public boolean coresSpawned;
        public int brokenCores;
        public long vulnerableUntil;
        public Vec3 transitionCenter = Vec3.ZERO;
        public long busyUntil;
        public long lastServerTick = -1L;
        public int sequence;
        public String lastSkill = "";
        public final Map<String, Long> cooldownUntil = new HashMap<>();
        final List<ActiveCore> activeCores = new ArrayList<>();
        final Set<UUID> syncedStormPlayers = new HashSet<>();
        ActiveCast activeCast;

        public boolean isPhase2() {
            return phase == Phase.PHASE_2;
        }
    }

    public record TransitionReward(double shieldFraction, double cooldownScale, boolean vulnerable) {}

    private final DungeonService owner;
    private final Journal journal;
    private final MinecraftServer server;
    private final DungeonPorts.Players players;
    private final DungeonPorts.Targets targets;
    private final Random random = new Random();
    private final Map<String, State> states = new LinkedHashMap<>();

    RoxyEncounterService(DungeonService owner, Journal journal, MinecraftServer server,
                         DungeonPorts.Players players, DungeonPorts.Targets targets) {
        this.owner = owner;
        this.journal = journal;
        this.server = server;
        this.players = players;
        this.targets = targets;
    }

    /** 20 Hz 推进：由 ServerApplication.tick 驱动，确保 12s 升空咏唱、水镜折跃与台风眼节奏精确平滑。 */
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
                server.sendSystemMessage(ModText.of("水闸塔洛琪希首领更新失败：" + failure.getMessage()));
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
            broadcastEvent(instanceId, Map.of(
                "kind", "INTRO_TITLE",
                "instance", instanceId,
                "durationMs", 3_800L
            ));
        }
        long currentTick = server.getTickCount();
        if (s.lastServerTick == currentTick) return;
        s.lastServerTick = currentTick;
        Target t = targets.target(e);
        if (t.life.dead) return;

        // 重连或后入参战者同步二阶段暴雨天象
        if (s.stormActive) {
            for (ServerPlayer p : fighters) {
                if (s.syncedStormPlayers.add(p.getUUID())) {
                    sendStormState(p, instanceId, true, s.transitionCenter);
                }
            }
        }

        // 1. 全破水核后的 3s 魔力反噬虚弱到期恢复
        if (s.vulnerableUntil > 0L && now >= s.vulnerableUntil) {
            s.vulnerableUntil = 0L;
            if (Math.abs(t.damageTakenMultiplier - 1.0) > 1e-6) {
                t.damageTakenMultiplier = 1.0;
                commitTarget(d, e, t);
            }
        }

        // 2. 检测一管血归零 -> 立即触发 12 秒《豪雷积雨云》转场破核博弈
        if (s.phase == Phase.PHASE_1 && t.encounterLives == 1 && t.damageImmune) {
            beginStormTransition(d, e, t, s, now);
            return;
        }

        // 3. 推进 12 秒《豪雷积雨云》转场与 3 枚水魔导核心博弈
        if (s.phase == Phase.TRANSITION_CHANT) {
            tickStormTransition(d, e, t, s, fighters, now);
            return;
        }

        // 4. 推进当前主动施法
        if (s.activeCast != null) {
            tickActiveCast(d, e, t, s, fighters, now);
            return;
        }

        if (fighters.isEmpty()) return;

        // 虚弱喘息或受控制期间原地等待
        if (now < s.vulnerableUntil || CombatContentTypes.controlled(e)) {
            e.getNavigation().stop();
            return;
        }

        // 5. 空闲站位：大魔术师保持中远距离从容踱步，始终面向最近玩家
        ServerPlayer nearest = nearestFighter(e, fighters);
        if (nearest != null) {
            facePosition(e, nearest.position());
            double dist = e.distanceTo(nearest);
            if (!e.isNoGravity() && dist > 13.5) {
                e.getNavigation().moveTo(nearest, 0.96);
            } else {
                e.getNavigation().stop();
            }
        }

        if (now < s.busyUntil) return;
        double nearestDist = nearest == null ? Double.POSITIVE_INFINITY : e.distanceTo(nearest);
        SkillId chosen = chooseNextSkill(s, nearestDist, now, random);
        if (chosen == null) return;
        s.lastSkill = chosen.id;
        long baseCd = chosen.cooldownMs;
        if (s.isPhase2()) {
            baseCd = Math.round(baseCd * SluiceTowerRoxy.phase2CooldownScale(s.brokenCores));
        }
        // 冷却按原定义计算，不受冰缓倍率影响
        s.cooldownUntil.put(chosen.id, now + baseCd);
        startSkill(d, e, t, s, chosen, fighters, now);
    }

    private State initState(Dungeon d, DungeonEnemy e, long now) {
        State s = new State();
        s.bossId = e.getUUID();
        s.instanceId = d.id.toString();
        s.cell = d.cell;
        s.phase = Phase.PHASE_1;
        s.transitionCenter = AwakeningTowerLayout.guard(d.cell);
        s.busyUntil = now + 1_300L;
        return s;
    }

    /** 纯函数选招入口：一阶段屏蔽二阶段雷暴技能；近身适度偏好水镜折跃；避免连续复读同一技能。 */
    public static SkillId chooseNextSkill(State s, double nearestPlayerDist, long now, Random rng) {
        // 二阶段大招冷却就绪优先释放，保证史诗《天穹雷狱·台风眼》稳定出场
        if (s.isPhase2() && s.cooldownUntil.getOrDefault(SkillId.TYPHOON_EYE.id, 0L) <= now) {
            return SkillId.TYPHOON_EYE;
        }
        // 玩家过于贴身时优先考虑水镜折跃拉开法师身位
        if (nearestPlayerDist < 5.5
            && !SkillId.MIRROR_BLINK.id.equals(s.lastSkill)
            && s.cooldownUntil.getOrDefault(SkillId.MIRROR_BLINK.id, 0L) <= now
            && rng.nextDouble() < 0.65) {
            return SkillId.MIRROR_BLINK;
        }
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

    /** 纯函数评估 12 秒转场破核结果，便于无头测试验证。 */
    public static TransitionReward evaluateCoreTransitionReward(int brokenCores) {
        int clamped = Math.clamp(brokenCores, 0, SluiceTowerRoxy.TRANSITION_CORE_COUNT);
        double shield = SluiceTowerRoxy.remainingCoreShield(clamped);
        double cdScale = SluiceTowerRoxy.phase2CooldownScale(clamped);
        boolean vulnerable = clamped >= SluiceTowerRoxy.TRANSITION_CORE_COUNT;
        return new TransitionReward(shield, cdScale, vulnerable);
    }

    private void beginStormTransition(Dungeon d, DungeonEnemy e, Target t, State s, long now) throws Exception {
        String instanceId = d.id.toString();
        s.phase = Phase.TRANSITION_CHANT;
        s.activeCast = null;
        s.coresSpawned = false;
        s.brokenCores = 0;
        s.stormActive = true;
        s.transitionStartAt = now;
        s.transitionEndAt = now + SluiceTowerRoxy.TRANSITION_TOTAL_MS;
        s.nextTransitionBoltAt = now + SluiceTowerRoxy.TRANSITION_RISE_END_MS + 600L;
        s.transitionCenter = AwakeningTowerLayout.guard(d.cell);
        s.syncedStormPlayers.clear();
        for (ServerPlayer p : fightersFor(d)) {
            s.syncedStormPlayers.add(p.getUUID());
        }

        e.getNavigation().stop();
        e.setNoGravity(false);
        e.setPos(s.transitionCenter.x, s.transitionCenter.y, s.transitionCenter.z);
        e.setDeltaMovement(Vec3.ZERO);
        e.play("cumulonimbus_chant", SluiceTowerRoxy.TRANSITION_TOTAL_MS);

        t.damageImmune = true;
        t.encounterReviveUntil = s.transitionEndAt;
        commitTarget(d, e, t);

        broadcastEvent(instanceId, Map.of("kind", "CLEAR_HAZARDS", "instance", instanceId));
        JsonObject ev = new JsonObject();
        ev.addProperty("kind", "PHASE_TRANSITION");
        ev.addProperty("instance", instanceId);
        ev.addProperty("bossEntityId", e.getId());
        ev.addProperty("x", s.transitionCenter.x);
        ev.addProperty("y", s.transitionCenter.y);
        ev.addProperty("z", s.transitionCenter.z);
        ev.addProperty("startAt", now);
        ev.addProperty("durationMs", SluiceTowerRoxy.TRANSITION_TOTAL_MS);
        ev.addProperty("coreTotal", SluiceTowerRoxy.TRANSITION_CORE_COUNT);
        ev.add("pools", encodePositions(SluiceTowerRoxy.cornerPoolCenters(d.cell)));
        owner.broadcast(instanceId, "sluiceBossEvent", ev);
    }

    private void tickStormTransition(Dungeon d, DungeonEnemy e, Target t, State s,
                                     List<ServerPlayer> fighters, long now) throws Exception {
        String instanceId = d.id.toString();
        long elapsed = now - s.transitionStartAt;
        double yOff = SluiceTowerRoxy.transitionHeightOffset(elapsed);
        boolean airborne = yOff > 0.05;
        e.setNoGravity(airborne);
        e.setPos(s.transitionCenter.x, s.transitionCenter.y + yOff, s.transitionCenter.z);
        e.setDeltaMovement(Vec3.ZERO);
        e.resetFallDistance();
        ServerPlayer nearest = nearestFighter(e, fighters);
        if (nearest != null) facePosition(e, nearest.position());

        // 1. 2.2s 刷出 3 枚可破坏「水魔导核心」
        if (!s.coresSpawned && elapsed >= SluiceTowerRoxy.TRANSITION_CORE_SPAWN_MS) {
            s.coresSpawned = true;
            spawnHydroCores(d, s, now);
        }

        // 2. 巡检存活水核状态：击破即刻广播反馈
        if (s.coresSpawned && !s.activeCores.isEmpty()) {
            ServerLevel level = owner.level();
            Iterator<ActiveCore> it = s.activeCores.iterator();
            while (it.hasNext()) {
                ActiveCore core = it.next();
                Entity raw = level.getEntity(core.entityId);
                boolean broken = !(raw instanceof RitualMechanismEntity mech) || !mech.isAlive() || isTargetDead(mech);
                if (broken) {
                    it.remove();
                    discardMechanism(level, core.entityId);
                    s.brokenCores++;
                    broadcastEvent(instanceId, Map.of(
                        "kind", "HYDRO_CORE_BROKEN",
                        "instance", instanceId,
                        "coreKey", core.coreKey,
                        "broken", s.brokenCores,
                        "total", SluiceTowerRoxy.TRANSITION_CORE_COUNT,
                        "x", core.pos.x,
                        "y", core.pos.y,
                        "z", core.pos.z
                    ));
                }
            }
        }

        // 3. 转场期间稀疏天雷逼迫走位（10.0s 前停刷，留足落地反应空间）
        if (!fighters.isEmpty() && now >= s.nextTransitionBoltAt
            && now + SluiceTowerRoxy.TRANSITION_BOLT_WARNING_MS + 400L < s.transitionEndAt) {
            s.nextTransitionBoltAt = now + SluiceTowerRoxy.TRANSITION_BOLT_INTERVAL_MS;
            List<Vec3> drops = pickSpreadSpots(owner.level(), d.cell, s.transitionCenter, fighters,
                SluiceTowerRoxy.TRANSITION_BOLT_COUNT, 4.5, random);
            for (int i = 0; i < drops.size(); i++) {
                Vec3 pt = drops.get(i);
                long stagger = i * 160L;
                String hid = instanceId + "/trans_bolt/" + (++s.sequence);
                owner.broadcast(instanceId, "sluiceBossHazard", hazardPacket(
                    instanceId, hid, "TRANSITION_BOLT", t, e.getId(),
                    pt, pt,
                    SluiceTowerRoxy.TRANSITION_BOLT_RADIUS, 0.0, 0.0,
                    now + stagger, now + stagger + SluiceTowerRoxy.TRANSITION_BOLT_WARNING_MS,
                    now + stagger + SluiceTowerRoxy.TRANSITION_BOLT_WARNING_MS + 420L, 0L,
                    SluiceTowerRoxy.COEFF_TRANSITION_BOLT, SluiceTowerRoxy.ELEMENT_LIGHT, true,
                    0, 0L, 0.0, 0x8beeff
                ));
            }
        }

        if (now < s.transitionEndAt) return;

        // 4. 12.0s 转场收束：清理残留核心，应用破核奖励并正式进入二阶段！
        clearHydroCores(s);
        TransitionReward reward = evaluateCoreTransitionReward(s.brokenCores);

        landToGround(e, d.cell, s.transitionCenter);
        t.life.q = 1.0;
        t.life.dead = false;
        t.life.revision++;
        t.damageImmune = false;
        t.encounterReviveUntil = 0L;
        t.speciesAttack = SluiceTowerRoxy.ATTACK_GRADE.multiplier() * SluiceTowerRoxy.PHASE2_ATTACK_MULTIPLIER;
        t.shield = reward.shieldFraction();
        if (reward.vulnerable()) {
            t.damageTakenMultiplier = SluiceTowerRoxy.ALL_CORES_BROKEN_DAMAGE_TAKEN;
            s.vulnerableUntil = now + SluiceTowerRoxy.ALL_CORES_BROKEN_VULNERABLE_MS;
            e.play("storm_exhausted", SluiceTowerRoxy.ALL_CORES_BROKEN_VULNERABLE_MS);
        } else {
            t.damageTakenMultiplier = 1.0;
            s.vulnerableUntil = 0L;
        }
        e.setHealth(e.getMaxHealth());
        commitTarget(d, e, t);

        // 落地雷暴冲击波（全破时半径略缩，未全破时全力爆发）
        double waveRadius = reward.vulnerable()
            ? SluiceTowerRoxy.TRANSITION_LAND_RADIUS * 0.72
            : SluiceTowerRoxy.TRANSITION_LAND_RADIUS;
        String hid = instanceId + "/storm_land/" + (++s.sequence);
        owner.broadcast(instanceId, "sluiceBossHazard", hazardPacket(
            instanceId, hid, "TRANSITION_LAND_WAVE", t, e.getId(),
            s.transitionCenter, s.transitionCenter,
            waveRadius, 0.0, 0.0,
            now, now, now + 680L, 0L,
            SluiceTowerRoxy.COEFF_TRANSITION_LAND, SluiceTowerRoxy.ELEMENT_LIGHT, true,
            0, 0L, 0.0, 0x72e2ff
        ));

        JsonObject p2 = new JsonObject();
        p2.addProperty("kind", "PHASE2_ENTERED");
        p2.addProperty("instance", instanceId);
        p2.addProperty("brokenCores", s.brokenCores);
        p2.addProperty("totalCores", SluiceTowerRoxy.TRANSITION_CORE_COUNT);
        p2.addProperty("shieldFraction", reward.shieldFraction());
        p2.addProperty("vulnerableMs", reward.vulnerable() ? SluiceTowerRoxy.ALL_CORES_BROKEN_VULNERABLE_MS : 0L);
        p2.addProperty("x", s.transitionCenter.x);
        p2.addProperty("y", s.transitionCenter.y);
        p2.addProperty("z", s.transitionCenter.z);
        owner.broadcast(instanceId, "sluiceBossEvent", p2);

        s.phase = Phase.PHASE_2;
        s.lastSkill = "";
        s.cooldownUntil.clear();
        // 二阶段开场预留 6.5 秒暖机再进入首次《天穹雷狱·台风眼》，避免落地瞬间压迫过密
        s.cooldownUntil.put(SkillId.TYPHOON_EYE.id, now + 6_500L);
        s.busyUntil = reward.vulnerable()
            ? now + SluiceTowerRoxy.ALL_CORES_BROKEN_VULNERABLE_MS
            : now + 1_100L;
    }

    private void spawnHydroCores(Dungeon d, State s, long now) {
        ServerLevel level = owner.level();
        String instanceId = d.id.toString();
        double baseAngle = random.nextDouble() * Math.PI * 2.0;
        List<Vec3> rawSpots = SluiceTowerRoxy.hydroCorePositions(d.cell, baseAngle);
        JsonArray arr = new JsonArray();
        int partySize = Math.max(1, d.roster.size());
        for (int i = 0; i < rawSpots.size(); i++) {
            Vec3 pos = safeGround(level, d.cell, rawSpots.get(i), s.transitionCenter);
            RitualMechanismEntity core = spawnMechanism(
                level, d, SluiceTowerRoxy.HYDRO_CORE_MECHANISM_ID, "水魔导核心",
                pos, SluiceTowerRoxy.CORE_SPECIES_HEALTH, partySize
            );
            if (core == null) continue;
            String key = instanceId + "/core/" + (++s.sequence);
            s.activeCores.add(new ActiveCore(key, core.getUUID(), core.getId(), pos));
            JsonObject o = new JsonObject();
            o.addProperty("key", key);
            o.addProperty("entityId", core.getId());
            o.addProperty("x", pos.x);
            o.addProperty("y", pos.y);
            o.addProperty("z", pos.z);
            arr.add(o);
        }
        JsonObject ev = new JsonObject();
        ev.addProperty("kind", "HYDRO_CORES_SPAWNED");
        ev.addProperty("instance", instanceId);
        ev.addProperty("spawnAt", now);
        ev.addProperty("endAt", s.transitionEndAt);
        ev.add("cores", arr);
        owner.broadcast(instanceId, "sluiceBossEvent", ev);
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
            case WATER_BARRAGE -> {
                int count = SluiceTowerRoxy.barrageCount(phase2);
                long windupMs = timing.windup(SluiceTowerRoxy.SKILL1_WINDUP_MS);
                long intervalMs = timing.duration(SluiceTowerRoxy.SKILL1_LAUNCH_INTERVAL_MS);
                long volleyMs = Math.max(1L, count) * intervalMs;

                ActiveCast cast = new ActiveCast(skill);
                cast.startAt = now;
                cast.endAt = now + windupMs + volleyMs;
                s.activeCast = cast;

                e.getNavigation().stop();
                facePosition(e, targetPlayer.position());
                e.play("water_barrage", windupMs + volleyMs);

                String hid = instanceId + "/barrage/" + (++s.sequence);
                owner.broadcast(instanceId, "sluiceBossHazard", hazardPacket(
                    instanceId, hid, "WATER_BARRAGE", t, e.getId(),
                    e.position(), targetPlayer.position(),
                    SluiceTowerRoxy.SKILL1_HIT_RADIUS, SluiceTowerRoxy.SKILL1_TURN_DEG_PER_SEC, SluiceTowerRoxy.SKILL1_SPEED,
                    now, now + windupMs, now + windupMs + volleyMs + SluiceTowerRoxy.SKILL1_LIFE_MS, intervalMs,
                    SluiceTowerRoxy.COEFF_WATER_BARRAGE, SluiceTowerRoxy.ELEMENT_ICE, true,
                    count, SluiceTowerRoxy.SKILL1_LIFE_MS, phase2 ? 1.0 : 0.0, 0x58c8ff
                ));
            }
            case AQUA_BLADE -> {
                long windupMs = timing.windup(SluiceTowerRoxy.SKILL2_WINDUP_MS);
                long travelMs = timing.active(SluiceTowerRoxy.SKILL2_TRAVEL_MS);

                ActiveCast cast = new ActiveCast(skill);
                cast.startAt = now;
                cast.endAt = now + windupMs + travelMs;
                s.activeCast = cast;

                e.getNavigation().stop();
                facePosition(e, targetPlayer.position());
                e.play("aqua_blade", windupMs + travelMs);

                double[] angles = phase2 ? SluiceTowerRoxy.SKILL2_P2_ANGLES_DEG : new double[]{0.0};
                Vec3 origin = safeGround(owner.level(), d.cell, e.position(), e.position());
                for (double ang : angles) {
                    Vec3 bladeDir = rotateHorizontal(dir, ang);
                    Vec3 dest = origin.add(bladeDir.scale(SluiceTowerRoxy.SKILL2_RANGE));
                    String hid = instanceId + "/blade/" + (++s.sequence);
                    owner.broadcast(instanceId, "sluiceBossHazard", hazardPacket(
                        instanceId, hid, "AQUA_BLADE", t, e.getId(),
                        origin, dest,
                        SluiceTowerRoxy.SKILL2_WIDTH, 0.0, 0.0,
                        now, now + windupMs, now + windupMs + travelMs, 0L,
                        SluiceTowerRoxy.COEFF_AQUA_BLADE, SluiceTowerRoxy.ELEMENT_ICE, true,
                        phase2 ? 1 : 0, travelMs, ang, phase2 ? 0x6ce2ff : 0x4cb8ff
                    ));
                }
            }
            case GEYSER_ERUPTION -> {
                long castMs = timing.windup(SluiceTowerRoxy.SKILL3_CAST_MS);
                long waveInterval = timing.duration(SluiceTowerRoxy.SKILL3_WAVE_INTERVAL_MS);

                ActiveCast cast = new ActiveCast(skill);
                cast.startAt = now;
                cast.step = 0;
                cast.totalSteps = SluiceTowerRoxy.SKILL3_WAVES;
                cast.nextAt = now;
                cast.auxMs = waveInterval;
                cast.endAt = now + Math.max(castMs, waveInterval * Math.max(1, cast.totalSteps - 1) + 450L);
                s.activeCast = cast;

                e.getNavigation().stop();
                facePosition(e, targetPlayer.position());
                e.play("geyser_eruption", cast.endAt - now);
                emitGeyserWave(d, e, t, s, fighters, timing, now, phase2);
                cast.step = 1;
                cast.nextAt = now + waveInterval;
            }
            case MIRROR_BLINK -> {
                long windupMs = timing.windup(SluiceTowerRoxy.SKILL4_WINDUP_MS);
                long blinkMs = timing.active(SluiceTowerRoxy.SKILL4_BLINK_MS);
                Vec3 from = safeGround(owner.level(), d.cell, e.position(), e.position());
                Vec3 away = horizontalDirection(targetPlayer.position(), from);
                Vec3 rawDest = from.add(away.scale(SluiceTowerRoxy.SKILL4_DISTANCE));
                // 避免贴墙角无法拉开距离：若离中心过远则顺势斜向回拉中央
                Vec3 center = AwakeningTowerLayout.guard(d.cell);
                if (rawDest.subtract(center).horizontalDistance() > 28.0) {
                    Vec3 toCenter = horizontalDirection(from, center);
                    rawDest = from.add(rotateHorizontal(toCenter, random.nextBoolean() ? 35.0 : -35.0)
                        .scale(SluiceTowerRoxy.SKILL4_DISTANCE));
                }
                Vec3 dest = safeGround(owner.level(), d.cell, rawDest, center);

                ActiveCast cast = new ActiveCast(skill);
                cast.startAt = now;
                cast.nextAt = now + windupMs;
                cast.endAt = now + windupMs + blinkMs;
                cast.from = from;
                cast.to = dest;
                cast.windupMs = windupMs;
                cast.activeMs = blinkMs;
                s.activeCast = cast;

                e.getNavigation().stop();
                facePosition(e, targetPlayer.position());
                e.play("mirror_blink", windupMs + blinkMs);

                long mirrorDetonateAt = now + windupMs + timing.windup(SluiceTowerRoxy.SKILL4_MIRROR_DELAY_MS);
                int bolts = SluiceTowerRoxy.mirrorBoltCount(phase2);
                String hid = instanceId + "/mirror/" + (++s.sequence);
                owner.broadcast(instanceId, "sluiceBossHazard", hazardPacket(
                    instanceId, hid, "MIRROR_TRAP", t, e.getId(),
                    from, dest,
                    SluiceTowerRoxy.SKILL4_MIRROR_RADIUS, SluiceTowerRoxy.COEFF_MIRROR_BOLT, SluiceTowerRoxy.SKILL4_BOLT_SPEED,
                    now + windupMs, mirrorDetonateAt, mirrorDetonateAt + SluiceTowerRoxy.SKILL4_BOLT_LIFE_MS, 0L,
                    SluiceTowerRoxy.COEFF_MIRROR_BURST, SluiceTowerRoxy.ELEMENT_ICE, true,
                    bolts, SluiceTowerRoxy.SKILL4_BOLT_LIFE_MS, random.nextDouble() * Math.PI * 2.0, 0x80e6ff
                ));
            }
            case TIDAL_RINGS -> {
                long windupMs = timing.windup(SluiceTowerRoxy.SKILL5_WINDUP_MS);
                long gapMs = timing.duration(SluiceTowerRoxy.SKILL5_RING_GAP_MS);
                Vec3 origin = safeGround(owner.level(), d.cell, e.position(), e.position());

                ActiveCast cast = new ActiveCast(skill);
                cast.startAt = now;
                cast.endAt = now + windupMs + gapMs + (phase2 ? gapMs : 350L);
                s.activeCast = cast;

                e.getNavigation().stop();
                facePosition(e, targetPlayer.position());
                e.play("tidal_rings", cast.endAt - now);

                // 第一段：内圈水环（迫使玩家先向外撤）
                String h1 = instanceId + "/tidal_inner/" + (++s.sequence);
                owner.broadcast(instanceId, "sluiceBossHazard", hazardPacket(
                    instanceId, h1, "TIDAL_RING", t, e.getId(),
                    origin, origin,
                    SluiceTowerRoxy.SKILL5_INNER_RADIUS, 0.0, 0.0,
                    now, now + windupMs, now + windupMs + 480L, 0L,
                    SluiceTowerRoxy.COEFF_TIDAL_INNER, SluiceTowerRoxy.ELEMENT_ICE, true,
                    0, 0L, 0.0, 0x4ec4ff
                ));
                // 第二段：外圈潮汐环带（迫使玩家及时进内圈）
                long outerWarnStart = now + Math.round(windupMs * 0.45);
                long outerHitAt = now + windupMs + gapMs;
                String h2 = instanceId + "/tidal_outer/" + (++s.sequence);
                owner.broadcast(instanceId, "sluiceBossHazard", hazardPacket(
                    instanceId, h2, "TIDAL_RING", t, e.getId(),
                    origin, origin,
                    SluiceTowerRoxy.SKILL5_OUTER_MAX_RADIUS, SluiceTowerRoxy.SKILL5_OUTER_MIN_RADIUS, 0.0,
                    outerWarnStart, outerHitAt, outerHitAt + 520L, 0L,
                    SluiceTowerRoxy.COEFF_TIDAL_OUTER, SluiceTowerRoxy.ELEMENT_ICE, true,
                    1, 0L, 0.0, 0x76d8ff
                ));
                // 二阶段追加第三段：迅捷雷水回涌内环
                if (phase2) {
                    long thirdWarnStart = outerHitAt - 200L;
                    long thirdHitAt = outerHitAt + gapMs;
                    String h3 = instanceId + "/tidal_return/" + (++s.sequence);
                    owner.broadcast(instanceId, "sluiceBossHazard", hazardPacket(
                        instanceId, h3, "TIDAL_RING", t, e.getId(),
                        origin, origin,
                        SluiceTowerRoxy.SKILL5_INNER_RADIUS, 0.0, 0.0,
                        thirdWarnStart, thirdHitAt, thirdHitAt + 480L, 0L,
                        SluiceTowerRoxy.COEFF_TIDAL_INNER, SluiceTowerRoxy.ELEMENT_LIGHT, true,
                        2, 0L, 0.0, 0x9cf0ff
                    ));
                }
            }
            case THUNDER_CASCADE -> {
                long castMs = timing.windup(SluiceTowerRoxy.SKILL6_CAST_MS);
                long waveInterval = timing.duration(SluiceTowerRoxy.SKILL6_WAVE_INTERVAL_MS);

                ActiveCast cast = new ActiveCast(skill);
                cast.startAt = now;
                cast.step = 0;
                cast.totalSteps = SluiceTowerRoxy.SKILL6_WAVES;
                cast.auxMs = waveInterval;
                cast.endAt = now + Math.max(castMs, waveInterval * Math.max(1, cast.totalSteps - 1) + 550L);
                s.activeCast = cast;

                e.getNavigation().stop();
                facePosition(e, targetPlayer.position());
                e.play("thunder_cascade", cast.endAt - now);

                emitThunderWave(d, e, t, s, fighters, timing, now);
                cast.step = 1;
                cast.nextAt = now + waveInterval;
            }
            case TYPHOON_EYE -> {
                long windupMs = timing.windup(SluiceTowerRoxy.SKILL7_WINDUP_MS);
                long durationMs = SluiceTowerRoxy.SKILL7_DURATION_MS;
                long landMs = SluiceTowerRoxy.SKILL7_LAND_MS;
                Vec3 center = AwakeningTowerLayout.guard(d.cell);
                // 台风眼起点优先靠近最近玩家所在方位，保证 1.8s 前摇内从容入眼不靠运气
                double startAngle = Math.atan2(targetPlayer.getZ() - center.z, targetPlayer.getX() - center.x);
                double sweepSign = random.nextBoolean() ? 1.0 : -1.0;

                ActiveCast cast = new ActiveCast(skill);
                cast.startAt = now;
                cast.windupMs = windupMs;
                cast.activeMs = durationMs;
                cast.auxMs = landMs;
                cast.anchor = center;
                cast.from = e.position();
                cast.startAngle = startAngle;
                cast.sweepSign = sweepSign;
                cast.nextAt = now + windupMs + SluiceTowerRoxy.SKILL7_INNER_STRIKE_INTERVAL_MS;
                cast.endAt = now + windupMs + durationMs + landMs;
                s.activeCast = cast;

                e.getNavigation().stop();
                facePosition(e, targetPlayer.position());
                e.play("typhoon_eye", cast.endAt - now);

                JsonObject ev = new JsonObject();
                ev.addProperty("kind", "TYPHOON_EYE_START");
                ev.addProperty("instance", instanceId);
                ev.addProperty("id", instanceId + "/typhoon/" + (++s.sequence));
                ev.add("boss", JSON.toJsonTree(t));
                ev.addProperty("bossEntityId", e.getId());
                ev.addProperty("x", center.x);
                ev.addProperty("y", center.y);
                ev.addProperty("z", center.z);
                ev.addProperty("cell", d.cell);
                ev.addProperty("startAt", now);
                ev.addProperty("activeAt", now + windupMs);
                ev.addProperty("endAt", now + windupMs + durationMs);
                ev.addProperty("startAngle", startAngle);
                ev.addProperty("sweepSign", sweepSign);
                ev.addProperty("eyeRadius", SluiceTowerRoxy.SKILL7_EYE_RADIUS);
                ev.addProperty("outsideTickMs", SluiceTowerRoxy.SKILL7_OUTSIDE_TICK_MS);
                ev.addProperty("outsideCoeff", SluiceTowerRoxy.COEFF_TYPHOON_OUTSIDE);
                owner.broadcast(instanceId, "sluiceBossEvent", ev);
            }
        }
    }

    private void tickActiveCast(Dungeon d, DungeonEnemy e, Target t, State s,
                                List<ServerPlayer> fighters, long now) {
        ActiveCast c = s.activeCast;
        if (c == null) return;
        String instanceId = d.id.toString();
        Target.ActionTiming timing = t.actionTiming(now);

        switch (c.skill) {
            case GEYSER_ERUPTION -> {
                if (c.step < c.totalSteps && now >= c.nextAt) {
                    emitGeyserWave(d, e, t, s, fighters, timing, now, s.isPhase2());
                    c.step++;
                    c.nextAt = now + c.auxMs;
                }
            }
            case THUNDER_CASCADE -> {
                if (c.step < c.totalSteps && now >= c.nextAt) {
                    emitThunderWave(d, e, t, s, fighters, timing, now);
                    c.step++;
                    c.nextAt = now + c.auxMs;
                }
            }
            case MIRROR_BLINK -> {
                if (now >= c.nextAt && now <= c.endAt) {
                    double prog = Math.clamp((now - c.nextAt) / (double) Math.max(1L, c.activeMs), 0.0, 1.0);
                    Vec3 pos = c.from.lerp(c.to, smooth(prog));
                    e.setPos(pos.x, pos.y + Math.sin(prog * Math.PI) * 0.85, pos.z);
                    e.setDeltaMovement(Vec3.ZERO);
                    e.resetFallDistance();
                }
            }
            case TYPHOON_EYE -> {
                long elapsed = now - c.startAt;
                long activeStart = c.windupMs;
                long activeEnd = c.windupMs + c.activeMs;
                double hoverH = SluiceTowerRoxy.SKILL7_HOVER_HEIGHT;
                if (elapsed < activeStart) {
                    double p = smooth(elapsed / (double) Math.max(1L, activeStart));
                    Vec3 pos = c.from.lerp(c.anchor.add(0, hoverH, 0), p);
                    e.setNoGravity(true);
                    e.setPos(pos.x, pos.y, pos.z);
                    e.setDeltaMovement(Vec3.ZERO);
                    e.resetFallDistance();
                } else if (elapsed < activeEnd) {
                    double bob = Math.sin((elapsed - activeStart) * 0.004) * 0.35;
                    e.setNoGravity(true);
                    e.setPos(c.anchor.x, c.anchor.y + hoverH + bob, c.anchor.z);
                    e.setDeltaMovement(Vec3.ZERO);
                    e.resetFallDistance();

                    // 台风眼行进期间每 2.4s 在眼内边缘点落 1 道小预警水雷圈，保持微走位爽感
                    if (now >= c.nextAt && now + SluiceTowerRoxy.SKILL7_INNER_STRIKE_WARNING_MS + 350L < c.startAt + activeEnd) {
                        c.nextAt = now + SluiceTowerRoxy.SKILL7_INNER_STRIKE_INTERVAL_MS;
                        long futureActiveMs = (now + SluiceTowerRoxy.SKILL7_INNER_STRIKE_WARNING_MS) - (c.startAt + activeStart);
                        Vec3 eyeFuture = SluiceTowerRoxy.typhoonEyePosition(d.cell, c.startAngle, c.sweepSign, futureActiveMs);
                        double ang = random.nextDouble() * Math.PI * 2.0;
                        double offset = 1.6 + random.nextDouble() * 1.8;
                        Vec3 strikePos = safeGround(owner.level(), d.cell,
                            eyeFuture.add(Math.cos(ang) * offset, 0, Math.sin(ang) * offset), eyeFuture);
                        String hid = instanceId + "/eye_bolt/" + (++s.sequence);
                        owner.broadcast(instanceId, "sluiceBossHazard", hazardPacket(
                            instanceId, hid, "TYPHOON_INNER_STRIKE", t, e.getId(),
                            strikePos, strikePos,
                            SluiceTowerRoxy.SKILL7_INNER_STRIKE_RADIUS, 0.0, 0.0,
                            now, now + SluiceTowerRoxy.SKILL7_INNER_STRIKE_WARNING_MS,
                            now + SluiceTowerRoxy.SKILL7_INNER_STRIKE_WARNING_MS + 420L, 0L,
                            SluiceTowerRoxy.COEFF_TYPHOON_INNER, SluiceTowerRoxy.ELEMENT_LIGHT, true,
                            0, 0L, 0.0, 0x90f0ff
                        ));
                    }
                } else {
                    if (c.subPhase == 0) {
                        c.subPhase = 1;
                        // 台风眼结束瞬间中央落地爆出一道清场雷浪，随后进入 2.0s 喘息硬直
                        String hid = instanceId + "/typhoon_end/" + (++s.sequence);
                        owner.broadcast(instanceId, "sluiceBossHazard", hazardPacket(
                            instanceId, hid, "TYPHOON_FINALE_BLAST", t, e.getId(),
                            c.anchor, c.anchor,
                            SluiceTowerRoxy.SKILL7_FINALE_RADIUS, 0.0, 0.0,
                            now, now + c.auxMs, now + c.auxMs + 550L, 0L,
                            SluiceTowerRoxy.COEFF_TYPHOON_FINALE, SluiceTowerRoxy.ELEMENT_LIGHT, true,
                            0, 0L, 0.0, 0xb8f6ff
                        ));
                    }
                    double lp = Math.clamp((elapsed - activeEnd) / (double) Math.max(1L, c.auxMs), 0.0, 1.0);
                    e.setNoGravity(lp < 0.98);
                    e.setPos(c.anchor.x, c.anchor.y + hoverH * (1.0 - lp * lp), c.anchor.z);
                    e.setDeltaMovement(Vec3.ZERO);
                    e.resetFallDistance();
                }
            }
            default -> {}
        }

        if (now < c.endAt) return;

        // 技能收尾与后摇硬直
        if (c.skill == SkillId.MIRROR_BLINK) {
            landToGround(e, d.cell, c.to);
        } else if (c.skill == SkillId.TYPHOON_EYE) {
            landToGround(e, d.cell, c.anchor);
            e.play("storm_exhausted", SluiceTowerRoxy.SKILL7_RECOVERY_MS);
        }

        long recovery = switch (c.skill) {
            case WATER_BARRAGE -> SluiceTowerRoxy.SKILL1_RECOVERY_MS;
            case AQUA_BLADE -> SluiceTowerRoxy.SKILL2_RECOVERY_MS;
            case GEYSER_ERUPTION -> SluiceTowerRoxy.SKILL3_RECOVERY_MS;
            case MIRROR_BLINK -> SluiceTowerRoxy.SKILL4_RECOVERY_MS;
            case TIDAL_RINGS -> SluiceTowerRoxy.SKILL5_RECOVERY_MS;
            case THUNDER_CASCADE -> SluiceTowerRoxy.SKILL6_RECOVERY_MS;
            case TYPHOON_EYE -> SluiceTowerRoxy.SKILL7_RECOVERY_MS;
        };
        s.activeCast = null;
        s.busyUntil = now + timing.recovery(recovery);
    }

    private void emitGeyserWave(Dungeon d, DungeonEnemy e, Target t, State s,
                                List<ServerPlayer> fighters, Target.ActionTiming timing,
                                long now, boolean phase2) {
        String instanceId = d.id.toString();
        int count = SluiceTowerRoxy.geyserCount(phase2);
        long warnMs = timing.windup(SluiceTowerRoxy.SKILL3_WARNING_MS);
        List<Vec3> spots = pickSpreadSpots(owner.level(), d.cell, e.position(), fighters, count, 5.2, random);
        for (int i = 0; i < spots.size(); i++) {
            Vec3 pt = spots.get(i);
            long stagger = i * 95L;
            String hid = instanceId + "/geyser/" + (++s.sequence);
            owner.broadcast(instanceId, "sluiceBossHazard", hazardPacket(
                instanceId, hid, "GEYSER_COLUMN", t, e.getId(),
                pt, pt,
                SluiceTowerRoxy.SKILL3_RADIUS, 0.0, 0.0,
                now + stagger, now + stagger + warnMs, now + stagger + warnMs + 560L, 0L,
                SluiceTowerRoxy.COEFF_GEYSER, SluiceTowerRoxy.ELEMENT_ICE, true,
                phase2 ? 1 : 0, 0L, 0.0, 0x4cc8ff
            ));
        }
    }

    private void emitThunderWave(Dungeon d, DungeonEnemy e, Target t, State s,
                                 List<ServerPlayer> fighters, Target.ActionTiming timing, long now) {
        String instanceId = d.id.toString();
        long warnMs = timing.windup(SluiceTowerRoxy.SKILL6_WARNING_MS);
        List<Vec3> spots = pickSpreadSpots(owner.level(), d.cell, e.position(), fighters,
            SluiceTowerRoxy.SKILL6_BOLTS_PER_WAVE, 4.8, random);
        for (int i = 0; i < spots.size(); i++) {
            Vec3 pt = spots.get(i);
            long stagger = i * 110L;
            String hid = instanceId + "/thunder/" + (++s.sequence);
            owner.broadcast(instanceId, "sluiceBossHazard", hazardPacket(
                instanceId, hid, "THUNDER_CASCADE", t, e.getId(),
                pt, pt,
                SluiceTowerRoxy.SKILL6_RADIUS, 0.0, 0.0,
                now + stagger, now + stagger + warnMs, now + stagger + warnMs + 480L, 0L,
                SluiceTowerRoxy.COEFF_THUNDER_CASCADE, SluiceTowerRoxy.ELEMENT_LIGHT, true,
                0, 0L, 0.0, 0x9ef0ff
            ));
        }
    }

    private RitualMechanismEntity spawnMechanism(ServerLevel level, Dungeon d, String kind, String title,
                                                 Vec3 pos, double speciesHealth, int partySize) {
        var holder = DungeonContentTypes.MECHANISMS.get(kind);
        if (holder == null) return null;
        RitualMechanismEntity mech = new RitualMechanismEntity(holder.get(), level);
        mech.kind(kind);
        mech.bindRun(d.id.toString());
        mech.setPos(pos.x, pos.y, pos.z);
        mech.setCustomName(ModText.of(title));
        mech.setCustomNameVisible(true);

        Target target = new Target();
        target.id = mech.getUUID();
        target.dimension = level.dimension().identifier().toString();
        target.instance = d.id.toString();
        target.life.instance = d.id.toString();
        target.kind = "NORMAL";
        target.originalLevel = AwakeningTower.LEVEL;
        target.tower = AwakeningTower.SLUICE;
        target.difficulty = d.difficulty;
        target.partySize = Math.max(1, partySize);
        target.noRewards = true;
        target.speciesHealth = speciesHealth;
        mech.configure(target);
        if (!level.addFreshEntity(mech)) return null;
        targets.target(mech);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (owner.participant(p, d.id.toString())) {
                targets.context(p, mech);
            }
        }
        return mech;
    }

    private boolean isTargetDead(RitualMechanismEntity mech) {
        try {
            return targets.target(mech).life.dead;
        } catch (Exception ignored) {
            return true;
        }
    }

    private void discardMechanism(ServerLevel level, UUID entityId) {
        Entity e = level.getEntity(entityId);
        if (e instanceof RitualMechanismEntity mech) {
            try {
                Target t = targets.target(mech);
                t.life.dead = true;
                t.life.q = 0.0;
                t.life.revision++;
                journal.put("target/" + entityId, t);
            } catch (Exception ignored) {
            }
            mech.discard();
        } else if (e != null) {
            e.discard();
        }
    }

    private void clearHydroCores(State s) {
        ServerLevel level = owner.level();
        for (ActiveCore core : s.activeCores) {
            discardMechanism(level, core.entityId);
        }
        s.activeCores.clear();
    }

    public void playerLeft(ServerPlayer p, Dungeon d) {
        if (p == null) return;
        String instanceId = d == null ? "" : d.id.toString();
        State s = states.get(instanceId);
        if (s != null) {
            s.syncedStormPlayers.remove(p.getUUID());
        }
        Network.send(p, "sluiceBossEvent", Map.of("kind", "CLEAR_ENCOUNTER_EFFECTS", "instance", instanceId));
    }

    public void cancel(UUID bossId) {
        states.values().removeIf(s -> {
            if (!bossId.equals(s.bossId)) return false;
            clearHydroCores(s);
            broadcastEvent(s.instanceId, Map.of("kind", "CLEAR_ENCOUNTER_EFFECTS", "instance", s.instanceId));
            return true;
        });
    }

    public void died(UUID bossId) {
        states.values().removeIf(s -> {
            if (!bossId.equals(s.bossId)) return false;
            clearHydroCores(s);
            broadcastEvent(s.instanceId, Map.of("kind", "DEFEAT_QUOTE", "instance", s.instanceId, "durationMs", 3_600L));
            broadcastEvent(s.instanceId, Map.of("kind", "CLEAR_ENCOUNTER_EFFECTS", "instance", s.instanceId));
            return true;
        });
    }

    public void close(Dungeon d) {
        if (d == null) return;
        State removed = states.remove(d.id.toString());
        if (removed != null) {
            clearHydroCores(removed);
            broadcastEvent(d.id.toString(), Map.of("kind", "CLEAR_ENCOUNTER_EFFECTS", "instance", d.id.toString()));
        }
    }

    private void sendStormState(ServerPlayer p, String instanceId, boolean active, Vec3 center) {
        Network.send(p, "sluiceBossEvent", Map.of(
            "kind", "STORM_STATE",
            "instance", instanceId,
            "active", active,
            "x", center.x,
            "y", center.y,
            "z", center.z
        ));
    }

    private void broadcastEvent(String instanceId, Map<String, Object> payload) {
        owner.broadcast(instanceId, "sluiceBossEvent", payload);
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

    private static List<Vec3> pickSpreadSpots(ServerLevel world, int cell, Vec3 bossPos,
                                              List<ServerPlayer> fighters, int count, double minSpacing, Random rng) {
        List<Vec3> result = new ArrayList<>(count);
        Vec3 anchor = fighters != null && !fighters.isEmpty() ? fighters.get(0).position() : bossPos;
        if (fighters != null) {
            for (ServerPlayer p : fighters) {
                if (result.size() >= count) break;
                result.add(safeGround(world, cell, p.position(), bossPos));
            }
        }
        double minSq = minSpacing * minSpacing;
        int attempts = 0;
        while (result.size() < count && attempts < count * 14) {
            attempts++;
            double angle = rng.nextDouble() * Math.PI * 2.0;
            double dist = 4.2 + rng.nextDouble() * 14.5;
            Vec3 cand = safeGround(world, cell, anchor.add(Math.cos(angle) * dist, 0, Math.sin(angle) * dist), anchor);
            boolean tooClose = false;
            for (Vec3 ex : result) {
                if (ex.subtract(cand).horizontalDistanceSqr() < minSq) {
                    tooClose = true;
                    break;
                }
            }
            if (!tooClose || attempts > count * 9) {
                result.add(cand);
            }
        }
        return result;
    }

    private static JsonArray encodePositions(List<Vec3> list) {
        JsonArray arr = new JsonArray();
        for (Vec3 v : list) {
            JsonObject o = new JsonObject();
            o.addProperty("x", v.x);
            o.addProperty("y", v.y);
            o.addProperty("z", v.z);
            arr.add(o);
        }
        return arr;
    }

    private static Vec3 safeGround(ServerLevel world, int cell, Vec3 point, Vec3 fallback) {
        Vec3 inside = AwakeningTowerLayout.insideBattle(cell, point);
        return world == null ? inside : AwakeningTowerLayout.ground(world, cell, inside, fallback);
    }

    private static Vec3 horizontalDirection(Vec3 from, Vec3 to) {
        Vec3 delta = new Vec3(to.x - from.x, 0, to.z - from.z);
        return delta.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : delta.normalize();
    }

    private static Vec3 rotateHorizontal(Vec3 dir, double angleDeg) {
        double rad = Math.toRadians(angleDeg);
        double cos = Math.cos(rad), sin = Math.sin(rad);
        return new Vec3(dir.x * cos - dir.z * sin, 0, dir.x * sin + dir.z * cos).normalize();
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

    private static double smooth(double x) {
        double c = Math.clamp(x, 0.0, 1.0);
        return c * c * (3.0 - 2.0 * c);
    }

    private JsonObject hazardPacket(String instanceId, String id, String type, Target boss, int bossEntityId,
                                    Vec3 from, Vec3 to, double radius, double auxRadius, double speed,
                                    long startAt, long warnUntil, long endAt, long intervalMs,
                                    double coefficient, int element, boolean magic,
                                    int count, long lifeMs, double baseAngle, int color) {
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
        o.addProperty("auxRadius", auxRadius);
        o.addProperty("speed", speed);
        o.addProperty("startAt", startAt);
        o.addProperty("warnUntil", warnUntil);
        o.addProperty("endAt", endAt);
        o.addProperty("intervalMs", intervalMs);
        o.addProperty("coefficient", coefficient);
        o.addProperty("element", element);
        o.addProperty("magic", magic);
        o.addProperty("count", count);
        o.addProperty("lifeMs", lifeMs);
        o.addProperty("baseAngle", baseAngle);
        o.addProperty("color", color);
        return o;
    }
}
