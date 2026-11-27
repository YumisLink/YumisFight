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
import cn.yumis.fight.modules.dungeons.content.OrreryTowerGilgamesh;
import cn.yumis.fight.modules.dungeons.content.OrreryTowerGilgamesh.SkillId;
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
 * 星仪塔第 20 层专属首领「吉尔伽美什（英雄王）」服务端三阶段状态机、技能调度与场内可破坏机关服务。
 * <p>
 * 遵循权威划分与同步约束：
 * - 服务端维护首领实体、三道锁血转阶段（65% 维摩那 / 25% 拔 Ea / 1% 乖离终焉 DPS Check）、
 *   独立可破坏机关（宝具楔子、天之锁锚点）与确定性时间轴；
 * - 维摩那巡航位置周期性同步严格 >= 250ms（4Hz 红线）；
 * - 玩家命中、纯控制移动拦截、星柱护幕躲避与受击结算由本机客户端权威判定。
 */
public final class GilgameshEncounterService {
    private static final Gson JSON = new GsonBuilder().disableHtmlEscaping().create();

    public enum Phase {
        P1_ARROGANCE,
        TRANSITION_65_VIMANA,
        P2_BABYLON,
        TRANSITION_25_EA,
        P3_ENUMA_ELISH,
        FINALE_DPS_CHECK
    }

    static final class ActiveCast {
        final SkillId skill;
        long startAt, nextAt, endAt;
        long auxAt;
        int step, totalSteps, subPhase;
        double baseAngle;
        Vec3 anchor = Vec3.ZERO;
        UUID targetPlayer;
        List<Vec3> shelters = List.of();

        ActiveCast(SkillId skill) {
            this.skill = skill;
        }
    }

    static final class PendingWedge {
        final String id;
        final Vec3 pos;
        final long dropAt;

        PendingWedge(String id, Vec3 pos, long dropAt) {
            this.id = id;
            this.pos = pos;
            this.dropAt = dropAt;
        }
    }

    static final class ActiveWedge {
        final String id;
        final UUID entityId;
        final Vec3 pos;
        final long explodeAt;

        ActiveWedge(String id, UUID entityId, Vec3 pos, long explodeAt) {
            this.id = id;
            this.entityId = entityId;
            this.pos = pos;
            this.explodeAt = explodeAt;
        }
    }

    static final class PendingEnkiduStrike {
        final String id;
        final Vec3 pos;
        final long hitAt;

        PendingEnkiduStrike(String id, Vec3 pos, long hitAt) {
            this.id = id;
            this.pos = pos;
            this.hitAt = hitAt;
        }
    }

    static final class ActiveEnkiduBind {
        final String id;
        final UUID playerId;
        final UUID anchorId;
        final Vec3 anchorPos;
        final long expireAt;

        ActiveEnkiduBind(String id, UUID playerId, UUID anchorId, Vec3 anchorPos, long expireAt) {
            this.id = id;
            this.playerId = playerId;
            this.anchorId = anchorId;
            this.anchorPos = anchorPos;
            this.expireAt = expireAt;
        }
    }

    public static final class State {
        public UUID bossId;
        public String instanceId;
        public int cell;
        public Phase phase = Phase.P1_ARROGANCE;
        public boolean triggered65, triggered25, triggeredFinale;
        public boolean forceFirstDecree;
        public boolean eaSkyActive;
        public boolean finaleWipeFired;
        public boolean drawWaveFired;
        public long phaseStartAt, phaseUntil, busyUntil;
        public long nextVimanaRingAt, nextVimanaOrbAt, lastVimanaSyncAt;
        public long nextFinaleRiftAt;
        public int vimanaRingIndex;
        public double vimanaStartAngle;
        public long lastServerTick = -1L;
        public int sequence;
        public String lastSkill = "";
        public final Map<String, Long> cooldownUntil = new HashMap<>();
        final List<PendingWedge> pendingWedges = new ArrayList<>();
        final List<ActiveWedge> activeWedges = new ArrayList<>();
        final List<PendingEnkiduStrike> pendingEnkidu = new ArrayList<>();
        final List<ActiveEnkiduBind> activeBinds = new ArrayList<>();
        final Set<UUID> syncedEaSkyPlayers = new HashSet<>();
        ActiveCast activeCast;

        public int phaseTier() {
            return switch (phase) {
                case P1_ARROGANCE -> 1;
                case TRANSITION_65_VIMANA, P2_BABYLON -> 2;
                case TRANSITION_25_EA, P3_ENUMA_ELISH, FINALE_DPS_CHECK -> 3;
            };
        }
    }

    private final DungeonService owner;
    private final Journal journal;
    private final MinecraftServer server;
    private final DungeonPorts.Players players;
    private final DungeonPorts.Targets targets;
    private final Random random = new Random();
    private final Map<String, State> states = new LinkedHashMap<>();

    GilgameshEncounterService(DungeonService owner, Journal journal, MinecraftServer server,
                              DungeonPorts.Players players, DungeonPorts.Targets targets) {
        this.owner = owner;
        this.journal = journal;
        this.server = server;
        this.players = players;
        this.targets = targets;
    }

    /** 20 Hz 高频推进：由 ServerApplication.tick 驱动。 */
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
                server.sendSystemMessage(ModText.of("星仪塔吉尔伽美什首领更新失败：" + failure.getMessage()));
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
                "kind", "INTRO_QUOTE",
                "instance", instanceId,
                "durationMs", 3_600L
            ));
        }
        long currentTick = server.getTickCount();
        if (s.lastServerTick == currentTick) return;
        s.lastServerTick = currentTick;
        Target t = targets.target(e);
        if (t.life.dead) return;

        // 保持黄金甲常驻 25% 减伤不被意外改写
        if (Math.abs(t.damageTakenMultiplier - OrreryTowerGilgamesh.GOLDEN_ARMOR_DAMAGE_TAKEN) > 1e-6) {
            t.damageTakenMultiplier = OrreryTowerGilgamesh.GOLDEN_ARMOR_DAMAGE_TAKEN;
        }

        // 重连或后入参战者同步 P3 赤金星裂天象
        if (s.eaSkyActive) {
            for (ServerPlayer p : fighters) {
                if (s.syncedEaSkyPlayers.add(p.getUUID())) {
                    sendEaSky(p, instanceId, true);
                }
            }
        }

        // 1. 推进场内可破坏宝具楔子与天之锁锚点
        tickWedges(d, e, t, s, now);
        tickEnkidu(d, e, t, s, fighters, now);

        // 2. 检查三道单向锁血跃迁（65% 维摩那 / 25% 拔 Ea / 1% 终焉 DPS Check）
        if (checkPhaseTransitions(d, e, t, s, fighters, now)) {
            return;
        }

        // 3. 推进转场与终焉阶段
        switch (s.phase) {
            case TRANSITION_65_VIMANA -> {
                tickVimanaTransition(d, e, t, s, fighters, now);
                return;
            }
            case TRANSITION_25_EA -> {
                tickEaDrawTransition(d, e, t, s, now);
                return;
            }
            case FINALE_DPS_CHECK -> {
                tickFinaleDpsCheck(d, e, t, s, fighters, now);
                return;
            }
            default -> {}
        }

        // 4. 推进当前释放中的主动技能
        if (s.activeCast != null) {
            tickActiveCast(d, e, t, s, fighters, now);
            return;
        }

        if (fighters.isEmpty()) return;

        if (CombatContentTypes.controlled(e)) {
            e.getNavigation().stop();
            return;
        }

        // 5. 空闲站位：王者踱步，居高临下保持中远距离，不贴脸肉搏
        ServerPlayer nearest = nearestFighter(e, fighters);
        if (nearest != null) {
            facePosition(e, nearest.position());
            double dist = e.distanceTo(nearest);
            if (!e.isNoGravity() && dist > 14.0) {
                e.getNavigation().moveTo(nearest, 0.88);
            } else {
                e.getNavigation().stop();
            }
        }

        if (now < s.busyUntil) return;
        double nearestDist = nearest == null ? Double.POSITIVE_INFINITY : e.distanceTo(nearest);
        SkillId chosen = chooseNextSkill(s, nearestDist, now, random);
        if (chosen == null) return;
        s.lastSkill = chosen.id;
        if (chosen == SkillId.ENUMA_ELISH_DECREE) {
            s.forceFirstDecree = false;
        }
        // 冷却按原定义计算，不受冰缓影响
        s.cooldownUntil.put(chosen.id, now + chosen.cooldownMs);
        startSkill(d, e, t, s, chosen, fighters, now);
    }

    private State initState(Dungeon d, DungeonEnemy e, long now) {
        State s = new State();
        s.bossId = e.getUUID();
        s.instanceId = d.id.toString();
        s.cell = d.cell;
        s.phase = Phase.P1_ARROGANCE;
        s.busyUntil = now + 1_300L;
        return s;
    }

    /** 纯函数选招入口：支持阶段过滤、P3 首次必放律令 Ea、近身优先斥退与避免相邻连发。 */
    public static SkillId chooseNextSkill(State s, double nearestPlayerDist, long now, Random rng) {
        int tier = s.phaseTier();
        if (tier >= 3 && s.forceFirstDecree && s.cooldownUntil.getOrDefault(SkillId.ENUMA_ELISH_DECREE.id, 0L) <= now) {
            return SkillId.ENUMA_ELISH_DECREE;
        }
        if (nearestPlayerDist < OrreryTowerGilgamesh.SKILL3_TRIGGER_RANGE
            && s.cooldownUntil.getOrDefault(SkillId.ROYAL_REPULSE.id, 0L) <= now
            && !SkillId.ROYAL_REPULSE.id.equals(s.lastSkill)) {
            return SkillId.ROYAL_REPULSE;
        }
        List<SkillId> candidates = new ArrayList<>(SkillId.values().length);
        for (SkillId skill : SkillId.values()) {
            if (skill.minPhase > tier) continue;
            if (skill == SkillId.ROYAL_REPULSE && nearestPlayerDist > OrreryTowerGilgamesh.SKILL3_RADIUS + 1.5) {
                continue;
            }
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

    /** 供无头测试验证三道锁血与终焉护盾状态机不变量（不依赖 Minecraft 实体）。 */
    public static Phase evaluatePurePhaseStep(State s, Target t, long now) {
        if (!s.triggered65 && s.phase == Phase.P1_ARROGANCE && t.life.q <= OrreryTowerGilgamesh.FLOOR_P1 + 1e-9) {
            s.triggered65 = true;
            t.damageImmune = true;
            t.damageTakenMultiplier = OrreryTowerGilgamesh.GOLDEN_ARMOR_DAMAGE_TAKEN;
            s.phase = Phase.TRANSITION_65_VIMANA;
            s.phaseStartAt = now;
            s.phaseUntil = now + OrreryTowerGilgamesh.TRANSITION_65_DURATION_MS;
            return s.phase;
        }
        if (s.phase == Phase.TRANSITION_65_VIMANA && now >= s.phaseUntil) {
            t.damageImmune = false;
            t.healthFloor = OrreryTowerGilgamesh.FLOOR_P2;
            t.damageTakenMultiplier = OrreryTowerGilgamesh.GOLDEN_ARMOR_DAMAGE_TAKEN;
            s.phase = Phase.P2_BABYLON;
            return s.phase;
        }
        if (s.triggered65 && !s.triggered25 && s.phase == Phase.P2_BABYLON && t.life.q <= OrreryTowerGilgamesh.FLOOR_P2 + 1e-9) {
            s.triggered25 = true;
            s.eaSkyActive = true;
            t.damageImmune = true;
            t.damageTakenMultiplier = OrreryTowerGilgamesh.GOLDEN_ARMOR_DAMAGE_TAKEN;
            s.phase = Phase.TRANSITION_25_EA;
            s.phaseStartAt = now;
            s.phaseUntil = now + OrreryTowerGilgamesh.TRANSITION_25_DURATION_MS;
            return s.phase;
        }
        if (s.phase == Phase.TRANSITION_25_EA && now >= s.phaseUntil) {
            t.damageImmune = false;
            t.healthFloor = 0.0;
            t.lethalFloorOne = true;
            t.damageTakenMultiplier = OrreryTowerGilgamesh.GOLDEN_ARMOR_DAMAGE_TAKEN;
            s.forceFirstDecree = true;
            s.phase = Phase.P3_ENUMA_ELISH;
            return s.phase;
        }
        if (s.triggered25 && !s.triggeredFinale && s.phase == Phase.P3_ENUMA_ELISH
            && t.lethalFloorOne && t.life.q <= Target.LETHAL_ONE_HP_FLOOR + 1e-9) {
            s.triggeredFinale = true;
            t.damageImmune = false;
            t.healthFloor = 0.0;
            t.lethalFloorOne = false;
            t.life.q = Target.LETHAL_ONE_HP_FLOOR;
            t.shield = Math.max(t.shield, OrreryTowerGilgamesh.FINALE_SHIELD_FRACTION);
            t.damageTakenMultiplier = OrreryTowerGilgamesh.GOLDEN_ARMOR_DAMAGE_TAKEN;
            t.life.revision++;
            s.phase = Phase.FINALE_DPS_CHECK;
            s.phaseStartAt = now;
            s.phaseUntil = now + OrreryTowerGilgamesh.FINALE_DPS_RACE_MS;
            return s.phase;
        }
        return s.phase;
    }

    private boolean checkPhaseTransitions(Dungeon d, DungeonEnemy e, Target t, State s,
                                          List<ServerPlayer> fighters, long now) throws Exception {
        String instanceId = d.id.toString();
        // 65% 转 P2：维摩那·天球巡航（仅此一次）
        if (!s.triggered65 && s.phase == Phase.P1_ARROGANCE && t.life.q <= OrreryTowerGilgamesh.FLOOR_P1 + 1e-9) {
            s.triggered65 = true;
            s.activeCast = null;
            clearAllMechanisms(instanceId, s);
            broadcastEvent(instanceId, Map.of("kind", "CLEAR_HAZARDS", "instance", instanceId));

            t.damageImmune = true;
            t.damageTakenMultiplier = OrreryTowerGilgamesh.GOLDEN_ARMOR_DAMAGE_TAKEN;
            commitTarget(d, e, t);

            s.phase = Phase.TRANSITION_65_VIMANA;
            s.phaseStartAt = now;
            s.phaseUntil = now + OrreryTowerGilgamesh.TRANSITION_65_DURATION_MS;
            s.vimanaStartAngle = random.nextDouble() * Math.PI * 2.0;
            s.vimanaRingIndex = 0;
            s.nextVimanaRingAt = now + 900L;
            s.nextVimanaOrbAt = now + 1_100L;
            s.lastVimanaSyncAt = now;

            e.getNavigation().stop();
            e.setNoGravity(true);
            e.play("vimana_cruise", OrreryTowerGilgamesh.TRANSITION_65_DURATION_MS);

            Vec3 center = AwakeningTowerLayout.guard(d.cell);
            broadcastEvent(instanceId, Map.of(
                "kind", "VIMANA_TRANSITION",
                "instance", instanceId,
                "bossEntityId", e.getId(),
                "cx", center.x,
                "cy", center.y,
                "cz", center.z,
                "startAngle", s.vimanaStartAngle,
                "durationMs", OrreryTowerGilgamesh.TRANSITION_65_DURATION_MS
            ));
            return true;
        }

        // 25% 转 P3：拔出乖离剑·Ea
        if (s.triggered65 && !s.triggered25 && s.phase == Phase.P2_BABYLON && t.life.q <= OrreryTowerGilgamesh.FLOOR_P2 + 1e-9) {
            s.triggered25 = true;
            s.eaSkyActive = true;
            s.drawWaveFired = false;
            s.activeCast = null;
            clearAllMechanisms(instanceId, s);
            broadcastEvent(instanceId, Map.of("kind", "CLEAR_HAZARDS", "instance", instanceId));

            t.damageImmune = true;
            t.damageTakenMultiplier = OrreryTowerGilgamesh.GOLDEN_ARMOR_DAMAGE_TAKEN;
            commitTarget(d, e, t);

            Vec3 center = AwakeningTowerLayout.guard(d.cell);
            e.getNavigation().stop();
            e.setNoGravity(true);
            e.setPos(center.x, center.y, center.z);
            e.setDeltaMovement(Vec3.ZERO);
            e.play("draw_ea", OrreryTowerGilgamesh.TRANSITION_25_DURATION_MS);

            s.syncedEaSkyPlayers.clear();
            for (ServerPlayer p : fighters) s.syncedEaSkyPlayers.add(p.getUUID());

            broadcastEvent(instanceId, Map.of(
                "kind", "EA_DRAW",
                "instance", instanceId,
                "bossEntityId", e.getId(),
                "cx", center.x,
                "cy", center.y,
                "cz", center.z,
                "durationMs", OrreryTowerGilgamesh.TRANSITION_25_DURATION_MS
            ));
            s.phase = Phase.TRANSITION_25_EA;
            s.phaseStartAt = now;
            s.phaseUntil = now + OrreryTowerGilgamesh.TRANSITION_25_DURATION_MS;
            return true;
        }

        // 1% 濒死锁血：乖离终焉 DPS Check（若正在律令 Ea 蓄力中则等待该次律令结束）
        if (s.triggered25 && !s.triggeredFinale && s.phase == Phase.P3_ENUMA_ELISH
            && (s.activeCast == null || s.activeCast.skill != SkillId.ENUMA_ELISH_DECREE)
            && t.lethalFloorOne && t.life.q <= Target.LETHAL_ONE_HP_FLOOR + 1e-9) {
            s.triggeredFinale = true;
            s.activeCast = null;
            clearAllMechanisms(instanceId, s);
            broadcastEvent(instanceId, Map.of("kind", "CLEAR_HAZARDS", "instance", instanceId));

            Vec3 center = AwakeningTowerLayout.guard(d.cell);
            landToGround(e, d.cell, center);
            e.getNavigation().stop();
            e.play("finale_enuma", OrreryTowerGilgamesh.FINALE_DPS_RACE_MS);

            // 留 1 HP + 10% 乖离护盾，打破护盾瞬间即击穿斩杀发布正式死亡回执
            t.damageImmune = false;
            t.healthFloor = 0.0;
            t.lethalFloorOne = false;
            t.life.q = Target.LETHAL_ONE_HP_FLOOR;
            t.shield = Math.max(t.shield, OrreryTowerGilgamesh.FINALE_SHIELD_FRACTION);
            t.damageTakenMultiplier = OrreryTowerGilgamesh.GOLDEN_ARMOR_DAMAGE_TAKEN;
            t.life.revision++;
            commitTarget(d, e, t);

            s.phase = Phase.FINALE_DPS_CHECK;
            s.phaseStartAt = now;
            s.phaseUntil = now + OrreryTowerGilgamesh.FINALE_DPS_RACE_MS;
            s.nextFinaleRiftAt = now + 1_600L;
            s.finaleWipeFired = false;

            broadcastEvent(instanceId, Map.of(
                "kind", "FINALE_DPS_CHECK",
                "instance", instanceId,
                "bossEntityId", e.getId(),
                "cx", center.x,
                "cy", center.y,
                "cz", center.z,
                "durationMs", OrreryTowerGilgamesh.FINALE_DPS_RACE_MS
            ));
            return true;
        }
        return false;
    }

    private void tickVimanaTransition(Dungeon d, DungeonEnemy e, Target t, State s,
                                      List<ServerPlayer> fighters, long now) throws Exception {
        String instanceId = d.id.toString();
        long elapsed = now - s.phaseStartAt;
        Vec3 pos = OrreryTowerGilgamesh.vimanaOrbitPosition(d.cell, s.vimanaStartAngle, elapsed);
        e.setNoGravity(true);
        e.setPos(pos.x, pos.y, pos.z);
        e.setDeltaMovement(Vec3.ZERO);
        e.resetFallDistance();
        Vec3 center = AwakeningTowerLayout.guard(d.cell);
        facePosition(e, center);

        // 遵守服务器 <= 4Hz 同步红线：仅每 >= 250ms 广播一次维摩那位置校对
        if (now - s.lastVimanaSyncAt >= OrreryTowerGilgamesh.VIMANA_SYNC_INTERVAL_MS) {
            s.lastVimanaSyncAt = now;
            broadcastEvent(instanceId, Map.of(
                "kind", "VIMANA_POS",
                "instance", instanceId,
                "x", pos.x,
                "y", pos.y,
                "z", pos.z
            ));
        }

        // 交替轰炸星仪塔同心环带：内环(0~12) / 中环(12~24) / 外环(24~38)
        if (now >= s.nextVimanaRingAt && now + OrreryTowerGilgamesh.VIMANA_RING_WARNING_MS <= s.phaseUntil + 400L) {
            s.nextVimanaRingAt = now + OrreryTowerGilgamesh.VIMANA_RING_INTERVAL_MS;
            int band = s.vimanaRingIndex++ % 3;
            double innerR = band == 0 ? 0.0 : band == 1 ? 12.0 : 24.0;
            double outerR = band == 0 ? 12.0 : band == 1 ? 24.0 : 38.0;
            String hid = instanceId + "/vimana_ring/" + (++s.sequence);
            owner.broadcast(instanceId, "orreryBossHazard", hazardPacket(
                instanceId, hid, "VIMANA_RING", t, e.getId(),
                center, center,
                outerR, innerR, 0.0,
                now, now + OrreryTowerGilgamesh.VIMANA_RING_WARNING_MS,
                now + OrreryTowerGilgamesh.VIMANA_RING_WARNING_MS + 550L, 0L,
                OrreryTowerGilgamesh.COEFF_VIMANA_RING, OrreryTowerGilgamesh.ELEMENT_LIGHT, true,
                0, 0L, 0.0, 0xf6c95b
            ));
        }

        // 船头高爆光球点射
        if (!fighters.isEmpty() && now >= s.nextVimanaOrbAt && now + OrreryTowerGilgamesh.VIMANA_ORB_WARNING_MS <= s.phaseUntil + 200L) {
            s.nextVimanaOrbAt = now + OrreryTowerGilgamesh.VIMANA_ORB_INTERVAL_MS;
            ServerPlayer chosen = fighters.get(random.nextInt(fighters.size()));
            Vec3 targetGround = safeGround(owner.level(), d.cell, chosen.position(), center);
            String hid = instanceId + "/vimana_orb/" + (++s.sequence);
            owner.broadcast(instanceId, "orreryBossHazard", hazardPacket(
                instanceId, hid, "VIMANA_ORB", t, e.getId(),
                pos, targetGround,
                OrreryTowerGilgamesh.VIMANA_ORB_RADIUS, 0.0, 0.0,
                now, now + OrreryTowerGilgamesh.VIMANA_ORB_WARNING_MS,
                now + OrreryTowerGilgamesh.VIMANA_ORB_WARNING_MS + 480L, 0L,
                OrreryTowerGilgamesh.COEFF_VIMANA_ORB, OrreryTowerGilgamesh.ELEMENT_LIGHT, true,
                0, 0L, 0.0, 0xffd76b
            ));
        }

        if (now < s.phaseUntil) return;

        landToGround(e, d.cell, center);
        t.damageImmune = false;
        t.healthFloor = OrreryTowerGilgamesh.FLOOR_P2;
        t.damageTakenMultiplier = OrreryTowerGilgamesh.GOLDEN_ARMOR_DAMAGE_TAKEN;
        commitTarget(d, e, t);

        s.phase = Phase.P2_BABYLON;
        s.lastSkill = "";
        s.cooldownUntil.clear();
        // 落地后给天之锁留 4 秒暖机，避免落地秒锁
        s.cooldownUntil.put(SkillId.ENKIDU_CHAINS.id, now + 4_000L);
        s.busyUntil = now + 900L;

        broadcastEvent(instanceId, Map.of(
            "kind", "PHASE2_ENTERED",
            "instance", instanceId,
            "durationMs", 2_600L
        ));
    }

    private void tickEaDrawTransition(Dungeon d, DungeonEnemy e, Target t, State s, long now) throws Exception {
        String instanceId = d.id.toString();
        long elapsed = now - s.phaseStartAt;
        Vec3 center = AwakeningTowerLayout.guard(d.cell);
        double liftProg = Math.clamp(elapsed / 1_400.0, 0.0, 1.0);
        double height;
        if (elapsed < 3_000L) {
            height = OrreryTowerGilgamesh.EA_DRAW_HOVER_HEIGHT * smooth(liftProg);
        } else {
            double landProg = Math.clamp((elapsed - 3_000L) / 1_000.0, 0.0, 1.0);
            height = OrreryTowerGilgamesh.EA_DRAW_HOVER_HEIGHT * (1.0 - landProg * landProg);
        }
        e.setNoGravity(true);
        e.setPos(center.x, center.y + height, center.z);
        e.setDeltaMovement(Vec3.ZERO);
        e.resetFallDistance();

        if (!s.drawWaveFired && elapsed >= 3_200L) {
            s.drawWaveFired = true;
            String hid = instanceId + "/ea_draw_wave/" + (++s.sequence);
            owner.broadcast(instanceId, "orreryBossHazard", hazardPacket(
                instanceId, hid, "EA_DRAW_WAVE", t, e.getId(),
                center, center,
                OrreryTowerGilgamesh.EA_DRAW_WAVE_RADIUS, 0.0, 0.0,
                now, now, now + 600L, 0L,
                OrreryTowerGilgamesh.COEFF_EA_DRAW_WAVE, OrreryTowerGilgamesh.ELEMENT_NONE, false,
                0, 0L, 0.0, 0xff463a
            ));
        }

        if (now < s.phaseUntil) return;

        landToGround(e, d.cell, center);
        t.damageImmune = false;
        t.healthFloor = 0.0;
        t.lethalFloorOne = true;
        t.damageTakenMultiplier = OrreryTowerGilgamesh.GOLDEN_ARMOR_DAMAGE_TAKEN;
        commitTarget(d, e, t);

        s.phase = Phase.P3_ENUMA_ELISH;
        s.forceFirstDecree = true;
        s.lastSkill = "";
        s.cooldownUntil.clear();
        s.busyUntil = now + 800L;
    }

    private void tickFinaleDpsCheck(Dungeon d, DungeonEnemy e, Target t, State s,
                                    List<ServerPlayer> fighters, long now) {
        String instanceId = d.id.toString();
        Vec3 center = AwakeningTowerLayout.guard(d.cell);
        e.getNavigation().stop();
        ServerPlayer nearest = nearestFighter(e, fighters);
        if (nearest != null) facePosition(e, nearest.position());

        if (!s.finaleWipeFired && now >= s.phaseUntil) {
            s.finaleWipeFired = true;
            JsonObject wipe = new JsonObject();
            wipe.addProperty("kind", "FINALE_WIPE");
            wipe.addProperty("instance", instanceId);
            wipe.addProperty("eventId", instanceId + "/finale_wipe");
            wipe.add("boss", JSON.toJsonTree(t));
            owner.broadcast(instanceId, "orreryBossEvent", wipe);
            return;
        }

        // 终焉期间外围随机 3 道裂空余波干扰（纯伤害不含控制，留足输出空间）
        if (!fighters.isEmpty() && now >= s.nextFinaleRiftAt && now + OrreryTowerGilgamesh.FINALE_RIFT_WARNING_MS < s.phaseUntil) {
            s.nextFinaleRiftAt = now + OrreryTowerGilgamesh.FINALE_RIFT_INTERVAL_MS;
            for (int i = 0; i < OrreryTowerGilgamesh.FINALE_RIFT_COUNT; i++) {
                double ang = random.nextDouble() * Math.PI * 2.0;
                double dist = 4.5 + random.nextDouble() * 14.0;
                Vec3 pt = safeGround(owner.level(), d.cell, center.add(Math.cos(ang) * dist, 0, Math.sin(ang) * dist), center);
                String hid = instanceId + "/finale_rift/" + (++s.sequence);
                owner.broadcast(instanceId, "orreryBossHazard", hazardPacket(
                    instanceId, hid, "FINALE_RIFT", t, e.getId(),
                    pt, pt,
                    OrreryTowerGilgamesh.FINALE_RIFT_RADIUS, 0.0, 0.0,
                    now, now + OrreryTowerGilgamesh.FINALE_RIFT_WARNING_MS,
                    now + OrreryTowerGilgamesh.FINALE_RIFT_WARNING_MS + 450L, 0L,
                    OrreryTowerGilgamesh.COEFF_FINALE_RIFT, OrreryTowerGilgamesh.ELEMENT_NONE, false,
                    0, 0L, 0.0, 0xff3c4c
                ));
            }
        }
    }

    private void startSkill(Dungeon d, DungeonEnemy e, Target t, State s, SkillId skill,
                            List<ServerPlayer> fighters, long now) {
        Target.ActionTiming timing = t.actionTiming(now);
        ServerPlayer targetPlayer = nearestFighter(e, fighters);
        if (targetPlayer == null) return;
        String instanceId = d.id.toString();
        int tier = s.phaseTier();
        Vec3 dir = horizontalDirection(e.position(), targetPlayer.position());

        switch (skill) {
            case BABYLON_VOLLEY -> {
                int count = OrreryTowerGilgamesh.volleyCount(tier);
                long windupMs = timing.windup(OrreryTowerGilgamesh.babylonWindup(OrreryTowerGilgamesh.SKILL1_WINDUP_MS, tier));
                long intervalMs = timing.duration(OrreryTowerGilgamesh.SKILL1_LAUNCH_INTERVAL_MS);
                long volleyMs = Math.max(1L, count) * intervalMs;

                ActiveCast cast = new ActiveCast(skill);
                cast.startAt = now;
                cast.endAt = now + windupMs + volleyMs;
                s.activeCast = cast;

                e.getNavigation().stop();
                facePosition(e, targetPlayer.position());
                e.play("babylon_volley", windupMs + volleyMs);

                String hid = instanceId + "/volley/" + (++s.sequence);
                owner.broadcast(instanceId, "orreryBossHazard", hazardPacket(
                    instanceId, hid, "BABYLON_VOLLEY", t, e.getId(),
                    e.position(), targetPlayer.position(),
                    OrreryTowerGilgamesh.SKILL1_HIT_RADIUS, 0.0, OrreryTowerGilgamesh.SKILL1_SPEED,
                    now, now + windupMs, now + windupMs + volleyMs + OrreryTowerGilgamesh.SKILL1_PROJECTILE_LIFE_MS, intervalMs,
                    OrreryTowerGilgamesh.COEFF_BABYLON_SHOT, OrreryTowerGilgamesh.ELEMENT_NONE, false,
                    count, OrreryTowerGilgamesh.SKILL1_PROJECTILE_LIFE_MS, random.nextDouble() * Math.PI * 2.0, 0xffd75e
                ));
            }
            case BABYLON_WEDGE -> {
                int count = OrreryTowerGilgamesh.wedgeCount(tier);
                long castMs = timing.windup(OrreryTowerGilgamesh.babylonWindup(OrreryTowerGilgamesh.SKILL2_CAST_MS, tier));
                long warnMs = timing.windup(OrreryTowerGilgamesh.SKILL2_DROP_WARNING_MS);

                ActiveCast cast = new ActiveCast(skill);
                cast.startAt = now;
                cast.endAt = now + castMs;
                s.activeCast = cast;

                e.getNavigation().stop();
                facePosition(e, targetPlayer.position());
                e.play("babylon_wedge", castMs + 250L);

                List<Vec3> spots = pickSpreadSpots(owner.level(), d.cell, e.position(), fighters, count, 6.0, random);
                long dropAt = now + warnMs;
                for (Vec3 pt : spots) {
                    String hid = instanceId + "/wedge/" + (++s.sequence);
                    s.pendingWedges.add(new PendingWedge(hid, pt, dropAt));
                    owner.broadcast(instanceId, "orreryBossHazard", hazardPacket(
                        instanceId, hid, "WEDGE_DROP", t, e.getId(),
                        pt, pt,
                        OrreryTowerGilgamesh.SKILL2_DROP_RADIUS, OrreryTowerGilgamesh.SKILL2_EXPLODE_RADIUS, 0.0,
                        now, dropAt, dropAt + 350L, 0L,
                        OrreryTowerGilgamesh.COEFF_WEDGE_DROP, OrreryTowerGilgamesh.ELEMENT_LIGHT, true,
                        0, OrreryTowerGilgamesh.SKILL2_DETONATE_MS, 0.0, 0xffbe40
                    ));
                }
            }
            case ROYAL_REPULSE -> {
                long windupMs = timing.windup(OrreryTowerGilgamesh.SKILL3_WINDUP_MS);
                long activeMs = timing.active(OrreryTowerGilgamesh.SKILL3_ACTIVE_MS);
                Vec3 center = safeGround(owner.level(), d.cell, e.position(), e.position());

                ActiveCast cast = new ActiveCast(skill);
                cast.startAt = now;
                cast.endAt = now + windupMs + activeMs;
                s.activeCast = cast;

                e.getNavigation().stop();
                facePosition(e, targetPlayer.position());
                e.play("royal_repulse", windupMs + activeMs);

                String hid = instanceId + "/repulse/" + (++s.sequence);
                owner.broadcast(instanceId, "orreryBossHazard", hazardPacket(
                    instanceId, hid, "ROYAL_REPULSE", t, e.getId(),
                    center, center,
                    OrreryTowerGilgamesh.SKILL3_RADIUS, OrreryTowerGilgamesh.SKILL3_KNOCKBACK, 0.0,
                    now, now + windupMs, now + windupMs + activeMs, 0L,
                    OrreryTowerGilgamesh.COEFF_ROYAL_REPULSE, OrreryTowerGilgamesh.ELEMENT_LIGHT, true,
                    16, 0L, 0.0, 0xffd75e
                ));
            }
            case BABYLON_SURROUND -> {
                int count = OrreryTowerGilgamesh.surroundCount(tier);
                long windupMs = timing.windup(OrreryTowerGilgamesh.babylonWindup(OrreryTowerGilgamesh.SKILL4_WINDUP_MS, tier));
                long activeMs = timing.active(OrreryTowerGilgamesh.SKILL4_ACTIVE_MS);
                Vec3 ringCenter = safeGround(owner.level(), d.cell, targetPlayer.position(), e.position());
                double gapAngle = random.nextDouble() * Math.PI * 2.0;

                ActiveCast cast = new ActiveCast(skill);
                cast.startAt = now;
                cast.endAt = now + windupMs + activeMs;
                s.activeCast = cast;

                e.getNavigation().stop();
                facePosition(e, ringCenter);
                e.play("babylon_surround", windupMs + activeMs);

                String hid = instanceId + "/surround/" + (++s.sequence);
                owner.broadcast(instanceId, "orreryBossHazard", hazardPacket(
                    instanceId, hid, "BABYLON_SURROUND", t, e.getId(),
                    ringCenter, ringCenter,
                    OrreryTowerGilgamesh.SKILL4_RING_RADIUS, OrreryTowerGilgamesh.SKILL4_HIT_RADIUS, OrreryTowerGilgamesh.SKILL4_SPEED,
                    now, now + windupMs, now + windupMs + OrreryTowerGilgamesh.SKILL4_PROJECTILE_LIFE_MS, 0L,
                    OrreryTowerGilgamesh.COEFF_SURROUND_SHOT, OrreryTowerGilgamesh.ELEMENT_NONE, false,
                    count, OrreryTowerGilgamesh.SKILL4_PROJECTILE_LIFE_MS, gapAngle, 0xffe066
                ));
            }
            case BABYLON_SKY_RAIN -> {
                long castMs = timing.windup(OrreryTowerGilgamesh.babylonWindup(OrreryTowerGilgamesh.SKILL5_CAST_MS, tier));
                long waveIntervalMs = timing.duration(OrreryTowerGilgamesh.SKILL5_WAVE_INTERVAL_MS);

                ActiveCast cast = new ActiveCast(skill);
                cast.startAt = now;
                cast.nextAt = now + castMs;
                cast.endAt = now + castMs + waveIntervalMs * OrreryTowerGilgamesh.SKILL5_WAVES;
                cast.step = 0;
                cast.totalSteps = OrreryTowerGilgamesh.SKILL5_WAVES;
                cast.auxAt = waveIntervalMs;
                s.activeCast = cast;

                e.getNavigation().stop();
                facePosition(e, targetPlayer.position());
                e.play("babylon_sky_rain", cast.endAt - now);
            }
            case ENKIDU_CHAINS -> {
                long castMs = timing.windup(OrreryTowerGilgamesh.SKILL6_CAST_MS);
                long intervalMs = timing.duration(OrreryTowerGilgamesh.SKILL6_STRIKE_INTERVAL_MS);

                ActiveCast cast = new ActiveCast(skill);
                cast.startAt = now;
                cast.nextAt = now + castMs;
                cast.endAt = now + castMs + intervalMs * OrreryTowerGilgamesh.SKILL6_STRIKES;
                cast.step = 0;
                cast.totalSteps = OrreryTowerGilgamesh.SKILL6_STRIKES;
                cast.auxAt = intervalMs;
                cast.targetPlayer = targetPlayer.getUUID();
                s.activeCast = cast;

                e.getNavigation().stop();
                facePosition(e, targetPlayer.position());
                e.play("enkidu_chains", cast.endAt - now);

                broadcastEvent(instanceId, Map.of(
                    "kind", "ENKIDU_QUOTE",
                    "instance", instanceId,
                    "durationMs", 2_800L
                ));
            }
            case EA_SPACE_REND -> {
                long windupMs = timing.windup(OrreryTowerGilgamesh.SKILL7_WINDUP_MS);
                long travelMs = timing.active(OrreryTowerGilgamesh.SKILL7_SLASH_TRAVEL_MS);
                long collapseMs = timing.duration(OrreryTowerGilgamesh.SKILL7_COLLAPSE_DELAY_MS);
                Vec3 origin = safeGround(owner.level(), d.cell, e.position(), e.position());

                ActiveCast cast = new ActiveCast(skill);
                cast.startAt = now;
                cast.endAt = now + windupMs + travelMs;
                s.activeCast = cast;

                e.getNavigation().stop();
                facePosition(e, targetPlayer.position());
                e.play("ea_space_rend", windupMs + travelMs);

                for (double deg : OrreryTowerGilgamesh.SKILL7_FAN_ANGLES_DEG) {
                    Vec3 rayDir = rotateHorizontal(dir, deg);
                    Vec3 end = origin.add(rayDir.scale(OrreryTowerGilgamesh.SKILL7_RANGE));
                    String hid = instanceId + "/ea_rend/" + (++s.sequence);
                    owner.broadcast(instanceId, "orreryBossHazard", hazardPacket(
                        instanceId, hid, "EA_SPACE_REND", t, e.getId(),
                        origin, end,
                        OrreryTowerGilgamesh.SKILL7_WIDTH, OrreryTowerGilgamesh.COEFF_EA_COLLAPSE, 0.0,
                        now, now + windupMs, now + windupMs + travelMs + collapseMs + 420L, collapseMs,
                        OrreryTowerGilgamesh.COEFF_EA_SLASH, OrreryTowerGilgamesh.ELEMENT_NONE, false,
                        0, travelMs, deg, 0xff3b3b
                    ));
                }
            }
            case ENUMA_ELISH_DECREE -> {
                long moveMs = timing.duration(OrreryTowerGilgamesh.SKILL8_MOVE_MS);
                long chargeMs = timing.windup(OrreryTowerGilgamesh.SKILL8_CHARGE_MS);
                long sweepMs = timing.active(OrreryTowerGilgamesh.SKILL8_SWEEP_MS);
                Vec3 center = AwakeningTowerLayout.guard(d.cell);
                List<Vec3> shelters = OrreryTowerGilgamesh.pickStarShelters(d.cell, OrreryTowerGilgamesh.SKILL8_SHELTER_COUNT, random);

                ActiveCast cast = new ActiveCast(skill);
                cast.startAt = now;
                cast.nextAt = now + moveMs + chargeMs;
                cast.endAt = now + moveMs + chargeMs + sweepMs;
                cast.subPhase = 0;
                cast.anchor = center;
                cast.shelters = shelters;
                s.activeCast = cast;

                e.getNavigation().stop();
                e.setNoGravity(true);
                e.setPos(center.x, center.y + 0.5, center.z);
                e.setDeltaMovement(Vec3.ZERO);
                e.play("enuma_elish", moveMs + chargeMs + sweepMs);

                JsonObject evt = new JsonObject();
                evt.addProperty("kind", "ENUMA_DECREE_START");
                evt.addProperty("instance", instanceId);
                evt.addProperty("id", instanceId + "/decree/" + (++s.sequence));
                evt.addProperty("cx", center.x);
                evt.addProperty("cy", center.y);
                evt.addProperty("cz", center.z);
                evt.addProperty("startAt", now);
                evt.addProperty("revealAt", now + moveMs + OrreryTowerGilgamesh.SKILL8_SHELTER_REVEAL_MS);
                evt.addProperty("impactAt", cast.nextAt);
                evt.addProperty("endAt", cast.endAt);
                evt.addProperty("shelterRadius", OrreryTowerGilgamesh.SKILL8_SHELTER_RADIUS);
                evt.add("shelters", encodePositions(shelters));
                owner.broadcast(instanceId, "orreryBossEvent", evt);
            }
        }
    }

    private void tickActiveCast(Dungeon d, DungeonEnemy e, Target t, State s, List<ServerPlayer> fighters, long now) {
        ActiveCast cast = s.activeCast;
        if (cast == null) return;
        Target.ActionTiming timing = t.actionTiming(now);
        String instanceId = d.id.toString();

        switch (cast.skill) {
            case BABYLON_VOLLEY -> {
                ServerPlayer nearest = nearestFighter(e, fighters);
                if (nearest != null) facePosition(e, nearest.position());
                if (now >= cast.endAt) {
                    s.activeCast = null;
                    s.busyUntil = now + timing.recovery(OrreryTowerGilgamesh.SKILL1_RECOVERY_MS);
                }
            }
            case BABYLON_WEDGE -> {
                if (now >= cast.endAt) {
                    s.activeCast = null;
                    s.busyUntil = now + timing.recovery(OrreryTowerGilgamesh.SKILL2_RECOVERY_MS);
                }
            }
            case ROYAL_REPULSE -> {
                if (now >= cast.endAt) {
                    s.activeCast = null;
                    s.busyUntil = now + timing.recovery(OrreryTowerGilgamesh.SKILL3_RECOVERY_MS);
                }
            }
            case BABYLON_SURROUND -> {
                if (now >= cast.endAt) {
                    s.activeCast = null;
                    s.busyUntil = now + timing.recovery(OrreryTowerGilgamesh.SKILL4_RECOVERY_MS);
                }
            }
            case BABYLON_SKY_RAIN -> {
                while (cast.step < cast.totalSteps && now >= cast.nextAt) {
                    spawnSkyRainWave(d, e, t, s, fighters, cast.step, timing, now);
                    cast.step++;
                    cast.nextAt += cast.auxAt;
                }
                if (now >= cast.endAt) {
                    s.activeCast = null;
                    s.busyUntil = now + timing.recovery(OrreryTowerGilgamesh.SKILL5_RECOVERY_MS);
                }
            }
            case ENKIDU_CHAINS -> {
                while (cast.step < cast.totalSteps && now >= cast.nextAt) {
                    ServerPlayer focus = findFighter(fighters, cast.targetPlayer);
                    if (focus == null) focus = nearestFighter(e, fighters);
                    if (focus != null) {
                        facePosition(e, focus.position());
                        Vec3 pt = safeGround(owner.level(), d.cell, focus.position(), e.position());
                        long warnMs = timing.windup(OrreryTowerGilgamesh.SKILL6_WARNING_MS);
                        String hid = instanceId + "/enkidu_warn/" + (++s.sequence);
                        s.pendingEnkidu.add(new PendingEnkiduStrike(hid, pt, now + warnMs));
                        owner.broadcast(instanceId, "orreryBossHazard", hazardPacket(
                            instanceId, hid, "ENKIDU_WARNING", t, e.getId(),
                            pt, pt,
                            OrreryTowerGilgamesh.SKILL6_RADIUS, 0.0, 0.0,
                            now, now + warnMs, now + warnMs + 350L, 0L,
                            0.0, OrreryTowerGilgamesh.ELEMENT_LIGHT, true,
                            0, 0L, 0.0, 0xf2c94c
                        ));
                    }
                    cast.step++;
                    cast.nextAt += cast.auxAt;
                }
                if (now >= cast.endAt) {
                    s.activeCast = null;
                    s.busyUntil = now + timing.recovery(OrreryTowerGilgamesh.SKILL6_RECOVERY_MS);
                }
            }
            case EA_SPACE_REND -> {
                if (now >= cast.endAt) {
                    s.activeCast = null;
                    s.busyUntil = now + timing.recovery(OrreryTowerGilgamesh.SKILL7_RECOVERY_MS);
                }
            }
            case ENUMA_ELISH_DECREE -> {
                double hoverH = Math.min(4.0, Math.max(0.5, (now - cast.startAt) / 350.0));
                e.setNoGravity(true);
                e.setPos(cast.anchor.x, cast.anchor.y + hoverH, cast.anchor.z);
                e.setDeltaMovement(Vec3.ZERO);
                e.resetFallDistance();

                if (cast.subPhase == 0 && now >= cast.nextAt) {
                    cast.subPhase = 1;
                    JsonObject impact = new JsonObject();
                    impact.addProperty("kind", "ENUMA_DECREE_IMPACT");
                    impact.addProperty("instance", instanceId);
                    impact.addProperty("eventId", instanceId + "/decree_impact/" + (++s.sequence));
                    impact.addProperty("cx", cast.anchor.x);
                    impact.addProperty("cy", cast.anchor.y);
                    impact.addProperty("cz", cast.anchor.z);
                    impact.addProperty("shelterRadius", OrreryTowerGilgamesh.SKILL8_SHELTER_RADIUS);
                    impact.addProperty("outsideRatio", OrreryTowerGilgamesh.SKILL8_OUTSIDE_RATIO);
                    impact.add("shelters", encodePositions(cast.shelters));
                    impact.add("boss", JSON.toJsonTree(t));
                    owner.broadcast(instanceId, "orreryBossEvent", impact);
                }
                if (now >= cast.endAt) {
                    landToGround(e, d.cell, cast.anchor);
                    s.activeCast = null;
                    s.busyUntil = now + timing.recovery(OrreryTowerGilgamesh.SKILL8_RECOVERY_MS);
                }
            }
        }
    }

    private void spawnSkyRainWave(Dungeon d, DungeonEnemy e, Target t, State s, List<ServerPlayer> fighters,
                                  int waveIndex, Target.ActionTiming timing, long now) {
        String instanceId = d.id.toString();
        Vec3 center = AwakeningTowerLayout.guard(d.cell);
        long warnMs = timing.windup(OrreryTowerGilgamesh.SKILL5_DROP_WARNING_MS);
        List<Vec3> drops = new ArrayList<>();
        if (waveIndex == 0) {
            drops.addAll(pickSpreadSpots(owner.level(), d.cell, e.position(), fighters,
                OrreryTowerGilgamesh.SKILL5_DROPS_PER_WAVE, 4.2, random));
        } else {
            double ringR = waveIndex == 1 ? 16.0 : 25.0;
            double offset = random.nextDouble() * Math.PI * 2.0;
            for (int i = 0; i < OrreryTowerGilgamesh.SKILL5_DROPS_PER_WAVE; i++) {
                double ang = offset + i * (Math.PI * 2.0 / OrreryTowerGilgamesh.SKILL5_DROPS_PER_WAVE);
                Vec3 raw = center.add(Math.cos(ang) * ringR, 0, Math.sin(ang) * ringR);
                drops.add(safeGround(owner.level(), d.cell, raw, center));
            }
            if (!fighters.isEmpty()) {
                drops.add(safeGround(owner.level(), d.cell, fighters.get(random.nextInt(fighters.size())).position(), center));
            }
        }
        for (int i = 0; i < drops.size(); i++) {
            Vec3 pt = drops.get(i);
            long stagger = i * 65L;
            String hid = instanceId + "/sky_rain/" + (++s.sequence);
            owner.broadcast(instanceId, "orreryBossHazard", hazardPacket(
                instanceId, hid, "SKY_RAIN", t, e.getId(),
                pt, pt,
                OrreryTowerGilgamesh.SKILL5_DROP_RADIUS, 0.0, 0.0,
                now + stagger, now + stagger + warnMs, now + stagger + warnMs + 420L, 0L,
                OrreryTowerGilgamesh.COEFF_SKY_RAIN, OrreryTowerGilgamesh.ELEMENT_LIGHT, true,
                0, 0L, 0.0, 0xffd56a
            ));
        }
    }

    private void tickWedges(Dungeon d, DungeonEnemy boss, Target bossTarget, State s, long now) {
        String instanceId = d.id.toString();
        ServerLevel level = owner.level();
        // 1. 坠落倒计时到点 -> 落地生成可破坏宝具楔子实体
        Iterator<PendingWedge> pendingIt = s.pendingWedges.iterator();
        while (pendingIt.hasNext()) {
            PendingWedge pw = pendingIt.next();
            if (now < pw.dropAt) continue;
            pendingIt.remove();
            RitualMechanismEntity wedge = spawnMechanism(
                level, d, OrreryTowerGilgamesh.WEDGE_MECHANISM_ID, "宝具楔子",
                pw.pos, OrreryTowerGilgamesh.WEDGE_SPECIES_HEALTH, Math.max(1, d.roster.size())
            );
            if (wedge == null) continue;
            long explodeAt = now + OrreryTowerGilgamesh.SKILL2_DETONATE_MS;
            s.activeWedges.add(new ActiveWedge(pw.id, wedge.getUUID(), pw.pos, explodeAt));
            owner.broadcast(instanceId, "orreryBossHazard", hazardPacket(
                instanceId, pw.id + "/armed", "WEDGE_ARMED", bossTarget, wedge.getId(),
                pw.pos, pw.pos,
                OrreryTowerGilgamesh.SKILL2_EXPLODE_RADIUS, 0.0, 0.0,
                now, explodeAt, explodeAt + 450L, 0L,
                OrreryTowerGilgamesh.COEFF_WEDGE_EXPLODE, OrreryTowerGilgamesh.ELEMENT_LIGHT, true,
                0, 0L, 0.0, 0xffaa2c
            ));
        }

        // 2. 巡检已插地楔子：被玩家击碎 -> 取消爆炸；倒计时到期未拆 -> 爆炸并销毁
        Iterator<ActiveWedge> activeIt = s.activeWedges.iterator();
        while (activeIt.hasNext()) {
            ActiveWedge aw = activeIt.next();
            Entity raw = level.getEntity(aw.entityId);
            boolean broken = !(raw instanceof RitualMechanismEntity mech) || !mech.isAlive() || isTargetDead(mech);
            if (broken) {
                activeIt.remove();
                discardMechanism(level, aw.entityId);
                broadcastEvent(instanceId, Map.of(
                    "kind", "WEDGE_DISARMED",
                    "instance", instanceId,
                    "hazardId", aw.id + "/armed"
                ));
                continue;
            }
            if (now >= aw.explodeAt) {
                activeIt.remove();
                discardMechanism(level, aw.entityId);
                owner.broadcast(instanceId, "orreryBossHazard", hazardPacket(
                    instanceId, aw.id + "/explode", "WEDGE_EXPLODE", bossTarget, boss.getId(),
                    aw.pos, aw.pos,
                    OrreryTowerGilgamesh.SKILL2_EXPLODE_RADIUS, 0.0, 0.0,
                    now, now, now + 520L, 0L,
                    OrreryTowerGilgamesh.COEFF_WEDGE_EXPLODE, OrreryTowerGilgamesh.ELEMENT_LIGHT, true,
                    0, 0L, 0.0, 0xffd24c
                ));
            }
        }
    }

    private void tickEnkidu(Dungeon d, DungeonEnemy boss, Target bossTarget, State s,
                            List<ServerPlayer> fighters, long now) {
        String instanceId = d.id.toString();
        ServerLevel level = owner.level();

        // 1. 预警到点 -> 对范围内未被锁的玩家施加纯控制天之锁 + 生成可击破锚点
        Iterator<PendingEnkiduStrike> pendingIt = s.pendingEnkidu.iterator();
        while (pendingIt.hasNext()) {
            PendingEnkiduStrike strike = pendingIt.next();
            if (now < strike.hitAt) continue;
            pendingIt.remove();
            double hitRSq = Math.pow(OrreryTowerGilgamesh.SKILL6_RADIUS + 0.45, 2);
            for (ServerPlayer p : fighters) {
                if (hasActiveBind(s, p.getUUID())) continue;
                Vec3 delta = p.position().subtract(strike.pos);
                if (Math.abs(delta.y) > 3.2 || delta.horizontalDistanceSqr() > hitRSq) continue;

                // 紧贴被锁玩家身侧放置可击破的天之锁锚点，近战远程均可顺手击碎
                Vec3 toBoss = horizontalDirection(p.position(), boss.position());
                Vec3 anchorPos = safeGround(level, d.cell, p.position().add(toBoss.scale(0.85)), p.position());
                RitualMechanismEntity anchor = spawnMechanism(
                    level, d, OrreryTowerGilgamesh.ENKIDU_MECHANISM_ID, "天之锁",
                    anchorPos, OrreryTowerGilgamesh.ENKIDU_SPECIES_HEALTH, 1
                );
                if (anchor == null) continue;

                String bindId = instanceId + "/enkidu_bind/" + (++s.sequence);
                long expireAt = now + OrreryTowerGilgamesh.SKILL6_BIND_DURATION_MS;
                s.activeBinds.add(new ActiveEnkiduBind(bindId, p.getUUID(), anchor.getUUID(), p.position(), expireAt));

                JsonObject bound = new JsonObject();
                bound.addProperty("kind", "ENKIDU_BOUND");
                bound.addProperty("instance", instanceId);
                bound.addProperty("bindId", bindId);
                bound.addProperty("playerId", p.getUUID().toString());
                bound.addProperty("anchorEntityId", anchor.getId());
                bound.addProperty("x", p.getX());
                bound.addProperty("y", p.getY());
                bound.addProperty("z", p.getZ());
                bound.addProperty("ax", anchorPos.x);
                bound.addProperty("ay", anchorPos.y);
                bound.addProperty("az", anchorPos.z);
                bound.addProperty("startAt", now);
                bound.addProperty("expireAt", expireAt);
                owner.broadcast(instanceId, "orreryBossEvent", bound);
            }
        }

        // 2. 巡检已锁状态：锚点被击碎 -> 安全挣脱；超时未击碎 -> 处决！
        Iterator<ActiveEnkiduBind> bindIt = s.activeBinds.iterator();
        while (bindIt.hasNext()) {
            ActiveEnkiduBind bind = bindIt.next();
            ServerPlayer boundPlayer = findFighter(fighters, bind.playerId);
            if (boundPlayer == null) {
                bindIt.remove();
                discardMechanism(level, bind.anchorId);
                broadcastEvent(instanceId, Map.of(
                    "kind", "ENKIDU_RELEASED",
                    "instance", instanceId,
                    "bindId", bind.id,
                    "playerId", bind.playerId.toString(),
                    "broken", false
                ));
                continue;
            }
            Entity raw = level.getEntity(bind.anchorId);
            boolean broken = !(raw instanceof RitualMechanismEntity mech) || !mech.isAlive() || isTargetDead(mech);
            if (broken) {
                bindIt.remove();
                discardMechanism(level, bind.anchorId);
                broadcastEvent(instanceId, Map.of(
                    "kind", "ENKIDU_RELEASED",
                    "instance", instanceId,
                    "bindId", bind.id,
                    "playerId", bind.playerId.toString(),
                    "broken", true
                ));
                continue;
            }
            if (now >= bind.expireAt) {
                bindIt.remove();
                discardMechanism(level, bind.anchorId);
                JsonObject exec = new JsonObject();
                exec.addProperty("kind", "ENKIDU_EXECUTE");
                exec.addProperty("instance", instanceId);
                exec.addProperty("bindId", bind.id);
                exec.addProperty("eventId", bind.id + "/exec");
                exec.addProperty("playerId", bind.playerId.toString());
                exec.addProperty("x", boundPlayer.getX());
                exec.addProperty("y", boundPlayer.getY());
                exec.addProperty("z", boundPlayer.getZ());
                exec.addProperty("ratio", OrreryTowerGilgamesh.ENKIDU_EXECUTE_RATIO);
                exec.add("boss", JSON.toJsonTree(bossTarget));
                owner.broadcast(instanceId, "orreryBossEvent", exec);
            }
        }
    }

    private boolean hasActiveBind(State s, UUID playerId) {
        for (ActiveEnkiduBind b : s.activeBinds) {
            if (b.playerId.equals(playerId)) return true;
        }
        return false;
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
        target.tower = AwakeningTower.ORRERY;
        target.difficulty = d.difficulty;
        target.partySize = Math.max(1, partySize);
        target.noRewards = true;
        target.speciesHealth = speciesHealth;
        mech.configure(target);
        if (!level.addFreshEntity(mech)) {
            return null;
        }
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

    private void clearAllMechanisms(String instanceId, State s) {
        ServerLevel level = owner.level();
        s.pendingWedges.clear();
        for (ActiveWedge aw : s.activeWedges) {
            discardMechanism(level, aw.entityId);
        }
        s.activeWedges.clear();
        s.pendingEnkidu.clear();
        for (ActiveEnkiduBind bind : s.activeBinds) {
            discardMechanism(level, bind.anchorId);
            broadcastEvent(instanceId, Map.of(
                "kind", "ENKIDU_RELEASED",
                "instance", instanceId,
                "bindId", bind.id,
                "playerId", bind.playerId.toString(),
                "broken", false
            ));
        }
        s.activeBinds.clear();
    }

    public void playerLeft(ServerPlayer p, Dungeon d) {
        if (p == null) return;
        String instanceId = d == null ? "" : d.id.toString();
        State s = states.get(instanceId);
        if (s != null) {
            s.syncedEaSkyPlayers.remove(p.getUUID());
            Iterator<ActiveEnkiduBind> it = s.activeBinds.iterator();
            while (it.hasNext()) {
                ActiveEnkiduBind bind = it.next();
                if (bind.playerId.equals(p.getUUID())) {
                    it.remove();
                    discardMechanism(owner.level(), bind.anchorId);
                }
            }
        }
        Network.send(p, "orreryBossEvent", Map.of("kind", "CLEAR_ENCOUNTER_EFFECTS", "instance", instanceId));
    }

    public void cancel(UUID bossId) {
        states.values().removeIf(s -> {
            if (!bossId.equals(s.bossId)) return false;
            clearAllMechanisms(s.instanceId, s);
            broadcastEvent(s.instanceId, Map.of("kind", "CLEAR_ENCOUNTER_EFFECTS", "instance", s.instanceId));
            return true;
        });
    }

    public void died(UUID bossId) {
        states.values().removeIf(s -> {
            if (!bossId.equals(s.bossId)) return false;
            clearAllMechanisms(s.instanceId, s);
            broadcastEvent(s.instanceId, Map.of("kind", "DEFEAT_QUOTE", "instance", s.instanceId, "durationMs", 3_400L));
            broadcastEvent(s.instanceId, Map.of("kind", "CLEAR_ENCOUNTER_EFFECTS", "instance", s.instanceId));
            return true;
        });
    }

    public void close(Dungeon d) {
        if (d == null) return;
        State removed = states.remove(d.id.toString());
        if (removed != null) {
            clearAllMechanisms(removed.instanceId, removed);
            broadcastEvent(d.id.toString(), Map.of("kind", "CLEAR_ENCOUNTER_EFFECTS", "instance", d.id.toString()));
        }
    }

    private void sendEaSky(ServerPlayer p, String instanceId, boolean active) {
        Network.send(p, "orreryBossEvent", Map.of("kind", "EA_SKY", "instance", instanceId, "active", active));
    }

    private void broadcastEvent(String instanceId, Map<String, Object> payload) {
        owner.broadcast(instanceId, "orreryBossEvent", payload);
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
        while (result.size() < count && attempts < count * 12) {
            attempts++;
            double angle = rng.nextDouble() * Math.PI * 2.0;
            double dist = 4.0 + rng.nextDouble() * 16.0;
            Vec3 cand = safeGround(world, cell, anchor.add(Math.cos(angle) * dist, 0, Math.sin(angle) * dist), anchor);
            boolean tooClose = false;
            for (Vec3 ex : result) {
                if (ex.subtract(cand).horizontalDistanceSqr() < minSq) {
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

    private static ServerPlayer findFighter(List<ServerPlayer> fighters, UUID id) {
        if (id == null) return null;
        for (ServerPlayer f : fighters) {
            if (id.equals(f.getUUID())) return f;
        }
        return null;
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
