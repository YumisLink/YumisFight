package cn.yumis.fight.modules.dungeons.application;

import cn.yumis.fight.foundation.Journal;
import cn.yumis.fight.modules.combat.api.CombatContentTypes;
import cn.yumis.fight.modules.dungeons.api.Dungeon;
import cn.yumis.fight.modules.dungeons.api.DungeonEnemy;
import cn.yumis.fight.modules.dungeons.api.DungeonPorts;
import cn.yumis.fight.modules.dungeons.api.Target;
import cn.yumis.fight.modules.dungeons.content.AwakeningTowerLayout;
import cn.yumis.fight.modules.dungeons.content.ForgeTowerSwordMasters;
import cn.yumis.fight.modules.dungeons.content.ForgeTowerSwordMasters.Member;
import cn.yumis.fight.modules.dungeons.content.ForgeTowerSwordMasters.SkillId;
import cn.yumis.fight.modules.dungeons.content.ForgeTowerSwordMasters.SlashSegment;
import cn.yumis.fight.modules.presentation.api.ModText;
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
 * 锻炉塔第 20 层专属首领「阿拉德四剑圣」服务端三阶段遭遇、协同令牌调度与全套 16 招 + 3 合击状态机。
 * <p>
 * 遵循权威划分与同步约束：
 * - 服务端维护四位剑圣实体生命周期、P1/P2 40% 锁血退场、P3 全员返场与双主攻+双策应令牌调度；
 * - 阿甘左大风车位置同步严格 >= 250ms（4Hz 红线），其余预警与斩线单次发包由客户端确定性演进；
 * - 玩家命中、击退、牵引与受击结算完全由本机客户端权威执行。
 */
public final class SwordMastersEncounterService {
    private static final Gson JSON = new GsonBuilder().disableHtmlEscaping().create();

    public enum Phase {
        P1_BWANGA_BAAN,
        TRANSITION_P1_P2,
        P2_AGANZO_SIRAN,
        TRANSITION_P2_P3,
        P3_ALL_FOUR
    }

    public enum ComboKind {
        DUO_P1_BWANGA_BAAN,
        DUO_P2_AGANZO_SIRAN,
        QUAD_P3_ALL_FOUR
    }

    static final class MemberCast {
        final SkillId skill;
        long startAt, nextAt, endAt, auxAt;
        int step, totalSteps, subPhase;
        double baseAngle;
        long seed;
        long lastMoveSyncAt;
        String hazardId = "";
        Vec3 from = Vec3.ZERO, to = Vec3.ZERO, anchor = Vec3.ZERO, mid = Vec3.ZERO;
        Vec3 direction = new Vec3(0, 0, 1);

        MemberCast(SkillId skill) {
            this.skill = skill;
        }
    }

    public static final class MemberSlot {
        public final Member member;
        public UUID entityId;
        public boolean spawned;
        public boolean retreated;
        public boolean dead;
        public boolean awakeningDone;
        public long busyUntil;
        public long lightMeleeUntil;
        public String lastRegularSkill = "";
        public final Map<String, Long> cooldownUntil = new HashMap<>();
        MemberCast activeCast;

        public MemberSlot(Member member) {
            this.member = member;
        }

        public boolean activeOnField() {
            return spawned && !retreated && !dead && entityId != null;
        }

        public boolean castingMain() {
            return activeCast != null;
        }

        public boolean castingAwakening() {
            return activeCast != null && activeCast.skill.awakening;
        }
    }

    static final class ComboCast {
        final ComboKind kind;
        final List<Member> actors;
        long startAt, nextAt, endAt;
        int stage;
        boolean exhaustSent;
        Vec3 center;

        ComboCast(ComboKind kind, List<Member> actors, Vec3 center, long startAt) {
            this.kind = kind;
            this.actors = List.copyOf(actors);
            this.center = center;
            this.startAt = startAt;
            this.nextAt = startAt;
        }
    }

    public static final class State {
        public String instanceId;
        public int cell;
        public Phase phase = Phase.P1_BWANGA_BAAN;
        public long phaseStartAt;
        public long transitionUntil;
        public long lastServerTick = -1L;
        public int sequence;
        public long duo1CooldownUntil;
        public long duo2CooldownUntil;
        public long quadNextAt;
        public boolean duo1TriggeredOnce;
        public boolean duo2TriggeredOnce;
        public boolean quadTriggeredOnce;
        public final EnumMap<Member, MemberSlot> slots = new EnumMap<>(Member.class);
        ComboCast activeCombo;

        public State() {
            for (Member m : Member.values()) {
                slots.put(m, new MemberSlot(m));
            }
        }

        public MemberSlot slot(Member member) {
            return slots.get(member);
        }
    }

    private final DungeonService owner;
    private final Journal journal;
    private final MinecraftServer server;
    private final DungeonPorts.Players players;
    private final DungeonPorts.Targets targets;
    private final Random random = new Random();
    private final Map<String, State> states = new LinkedHashMap<>();

    SwordMastersEncounterService(DungeonService owner, Journal journal, MinecraftServer server,
                                 DungeonPorts.Players players, DungeonPorts.Targets targets) {
        this.owner = owner;
        this.journal = journal;
        this.server = server;
        this.players = players;
        this.targets = targets;
    }

    /** 20 Hz 高频推进，由 ServerApplication.tick 驱动。 */
    void tickAll(long now) {
        if (states.isEmpty()) return;
        for (String instanceId : new ArrayList<>(states.keySet())) {
            State s = states.get(instanceId);
            if (s == null) continue;
            Dungeon d = owner.byId(instanceId);
            if (d == null || d.status.equals("CLOSED") || d.tower == null || !d.tower.fighting()) {
                continue;
            }
            if (isCleared(d)) continue;
            List<ServerPlayer> fighters = fightersFor(d);
            try {
                tickEncounter(d, s, fighters, now);
            } catch (Exception failure) {
                server.sendSystemMessage(ModText.of("锻炉塔四剑圣首领更新失败：" + failure.getMessage()));
            }
        }
    }

    /** TowerEncounterService 的 20 层清场判定：必须进入 P3 且四位剑圣全部被击败。 */
    boolean isCleared(Dungeon dungeon) {
        if (dungeon == null) return false;
        State s = states.get(dungeon.id.toString());
        if (s == null) return false;
        if (s.phase != Phase.P3_ALL_FOUR) return false;
        for (Member m : Member.values()) {
            MemberSlot slot = s.slot(m);
            if (slot.entityId == null) return false;
            if (!slot.dead && !owner.defeated(slot.entityId)) return false;
        }
        return true;
    }

    /** 由 DungeonService 的 enemyBrains 入口触发（任意一位剑圣被唤醒时初始化并驱动一次）。 */
    void brain(Dungeon d, DungeonEnemy e, List<ServerPlayer> fighters, long now) throws Exception {
        String instanceId = d.id.toString();
        State s = states.get(instanceId);
        Member member = Member.find(e.appearance());
        if (member == null) return;
        if (s == null) {
            s = initEncounter(d, e, member, now);
            states.put(instanceId, s);
        } else {
            MemberSlot slot = s.slot(member);
            if (slot.entityId == null) {
                slot.entityId = e.getUUID();
                slot.spawned = true;
            }
        }
        tickEncounter(d, s, fighters, now);
    }

    private State initEncounter(Dungeon d, DungeonEnemy leadEntity, Member leadMember, long now) throws Exception {
        State s = new State();
        s.instanceId = d.id.toString();
        s.cell = d.cell;
        s.phase = Phase.P1_BWANGA_BAAN;
        s.phaseStartAt = now;

        Vec3 center = AwakeningTowerLayout.guard(d.cell);
        Vec3 bwangaPos = AwakeningTowerLayout.ground(owner.level(), d.cell, center.add(-4.5, 0, -3.0), center);
        Vec3 baanPos = AwakeningTowerLayout.ground(owner.level(), d.cell, center.add(4.5, 0, -3.0), center);

        MemberSlot leadSlot = s.slot(leadMember);
        leadSlot.entityId = leadEntity.getUUID();
        leadSlot.spawned = true;
        leadSlot.busyUntil = now + 1_100L;
        if (leadMember == Member.BWANGA) {
            leadEntity.setPos(bwangaPos.x, bwangaPos.y, bwangaPos.z);
            leadEntity.setDeltaMovement(Vec3.ZERO);
        }
        Target leadTarget = targets.target(leadEntity);
        leadTarget.healthFloor = ForgeTowerSwordMasters.RETREAT_HEALTH_FLOOR;
        leadTarget.damageImmune = false;
        commitTarget(d, leadEntity, leadTarget);

        // 生成 P1 搭档：巴恩·巴休特
        if (leadMember != Member.BAAN) {
            spawnMember(d, s, Member.BAAN, baanPos, ForgeTowerSwordMasters.RETREAT_HEALTH_FLOOR, now + 1_400L);
        } else {
            spawnMember(d, s, Member.BWANGA, bwangaPos, ForgeTowerSwordMasters.RETREAT_HEALTH_FLOOR, now + 1_400L);
        }

        broadcastBanner(s.instanceId, "阿拉德四剑圣 · 第一阵：布万加 & 巴恩", 3_600L, 0xffd28a);
        return s;
    }

    private DungeonEnemy spawnMember(Dungeon d, State s, Member member, Vec3 pos, double healthFloor, long busyUntil) throws Exception {
        Vec3 safe = AwakeningTowerLayout.ground(owner.level(), d.cell, pos, AwakeningTowerLayout.guard(d.cell));
        DungeonEnemy entity = owner.spawn(d, member.id, false, true, safe.x, safe.y, safe.z, target -> {
            target.healthFloor = healthFloor;
            target.damageImmune = false;
        });
        MemberSlot slot = s.slot(member);
        slot.entityId = entity.getUUID();
        slot.spawned = true;
        slot.retreated = false;
        slot.dead = false;
        slot.busyUntil = busyUntil;
        slot.activeCast = null;
        return entity;
    }

    private void tickEncounter(Dungeon d, State s, List<ServerPlayer> fighters, long now) throws Exception {
        long currentTick = server.getTickCount();
        if (s.lastServerTick == currentTick) return;
        s.lastServerTick = currentTick;

        // 同步各剑圣死亡状态
        for (Member m : Member.values()) {
            MemberSlot slot = s.slot(m);
            if (slot.entityId != null && !slot.dead) {
                Entity raw = owner.level().getEntity(slot.entityId);
                if (owner.defeated(slot.entityId) || (raw instanceof DungeonEnemy de && !de.isAlive())) {
                    slot.dead = true;
                    slot.activeCast = null;
                }
            }
        }

        // 转阶段过渡演出
        if (s.phase == Phase.TRANSITION_P1_P2) {
            keepRetreatedOnLedge(s);
            if (now >= s.transitionUntil) {
                beginPhase2(d, s, now);
            }
            return;
        }
        if (s.phase == Phase.TRANSITION_P2_P3) {
            keepRetreatedOnLedge(s);
            if (now >= s.transitionUntil) {
                beginPhase3(d, s, now);
            }
            return;
        }

        // 检查 P1/P2 40% 锁血退场
        if (s.phase == Phase.P1_BWANGA_BAAN) {
            checkRetreat(d, s, Member.BWANGA, now);
            checkRetreat(d, s, Member.BAAN, now);
            if (s.slot(Member.BWANGA).retreated && s.slot(Member.BAAN).retreated) {
                startTransitionP1ToP2(s, now);
                return;
            }
        } else if (s.phase == Phase.P2_AGANZO_SIRAN) {
            checkRetreat(d, s, Member.AGANZO, now);
            checkRetreat(d, s, Member.SIRAN, now);
            if (s.slot(Member.AGANZO).retreated && s.slot(Member.SIRAN).retreated) {
                startTransitionP2ToP3(s, now);
                return;
            }
        }

        keepRetreatedOnLedge(s);

        // 推进正在进行的双人 / 四人合击技
        if (s.activeCombo != null) {
            tickCombo(d, s, fighters, now);
            return;
        }

        if (fighters.isEmpty()) return;

        // 检查是否触发合击技
        if (tryStartCombo(d, s, fighters, now)) {
            return;
        }

        // 推进每名在场剑圣的独立动作与 AI 调度
        for (Member m : Member.values()) {
            MemberSlot slot = s.slot(m);
            if (!slot.activeOnField()) continue;
            DungeonEnemy entity = resolveEnemy(slot);
            if (entity == null) continue;
            Target target = targets.target(entity);
            if (target.life.dead) {
                slot.dead = true;
                slot.activeCast = null;
                continue;
            }
            if (slot.activeCast != null) {
                tickMemberCast(d, s, slot, entity, target, fighters, now);
            }
        }

        // 空闲剑圣选招与走位
        for (Member m : Member.values()) {
            MemberSlot slot = s.slot(m);
            if (!slot.activeOnField() || slot.activeCast != null) continue;
            DungeonEnemy entity = resolveEnemy(slot);
            if (entity == null) continue;
            Target target = targets.target(entity);
            if (target.life.dead) continue;
            tickIdleMember(d, s, slot, entity, target, fighters, now);
        }
    }

    private void checkRetreat(Dungeon d, State s, Member member, long now) throws Exception {
        MemberSlot slot = s.slot(member);
        if (!slot.activeOnField()) return;
        DungeonEnemy entity = resolveEnemy(slot);
        if (entity == null) return;
        Target target = targets.target(entity);
        if (target.life.q > ForgeTowerSwordMasters.RETREAT_HEALTH_FLOOR + 0.002) return;

        // 触发 40% 锁血收剑退场
        slot.retreated = true;
        slot.activeCast = null;
        if (s.activeCombo != null && s.activeCombo.actors.contains(member)) {
            s.activeCombo = null;
        }
        target.healthFloor = ForgeTowerSwordMasters.RETREAT_HEALTH_FLOOR;
        target.damageImmune = true;
        target.damageTakenMultiplier = 1.0;
        commitTarget(d, entity, target);

        Vec3 ledge = AwakeningTowerLayout.ground(owner.level(), s.cell,
            ForgeTowerSwordMasters.retreatPosition(s.cell, member), AwakeningTowerLayout.guard(s.cell));
        entity.getNavigation().stop();
        entity.setNoGravity(false);
        entity.setPos(ledge.x, ledge.y, ledge.z);
        entity.setDeltaMovement(Vec3.ZERO);
        facePosition(entity, AwakeningTowerLayout.guard(s.cell));
        entity.play("retreat_watch", 1_200L);

        broadcastBanner(s.instanceId, member.displayName + " 收剑暂退，静候终局！", 2_000L, member.themeColor);
    }

    private void keepRetreatedOnLedge(State s) {
        Vec3 center = AwakeningTowerLayout.guard(s.cell);
        for (Member m : Member.values()) {
            MemberSlot slot = s.slot(m);
            if (!slot.retreated || slot.dead || slot.entityId == null) continue;
            DungeonEnemy e = resolveEnemy(slot);
            if (e == null) continue;
            Vec3 ledge = AwakeningTowerLayout.ground(owner.level(), s.cell,
                ForgeTowerSwordMasters.retreatPosition(s.cell, m), center);
            if (e.position().distanceToSqr(ledge) > 1.5) {
                e.setPos(ledge.x, ledge.y, ledge.z);
                e.setDeltaMovement(Vec3.ZERO);
            }
            e.getNavigation().stop();
            facePosition(e, center);
        }
    }

    private void startTransitionP1ToP2(State s, long now) {
        s.phase = Phase.TRANSITION_P1_P2;
        s.activeCombo = null;
        s.transitionUntil = now + ForgeTowerSwordMasters.TRANSITION_P1_P2_MS;
        broadcastEvent(s.instanceId, Map.of("kind", "CLEAR_HAZARDS", "instance", s.instanceId));
        broadcastBanner(s.instanceId, "剑意更迭 · 第二阵：阿甘左 & 西岚 登场！", 2_600L, 0x99ccff);
    }

    private void beginPhase2(Dungeon d, State s, long now) throws Exception {
        s.phase = Phase.P2_AGANZO_SIRAN;
        s.phaseStartAt = now;
        Vec3 center = AwakeningTowerLayout.guard(s.cell);
        spawnMember(d, s, Member.AGANZO, center.add(-5.0, 0, -4.0), ForgeTowerSwordMasters.RETREAT_HEALTH_FLOOR, now + 1_000L);
        spawnMember(d, s, Member.SIRAN, center.add(5.0, 0, -4.0), ForgeTowerSwordMasters.RETREAT_HEALTH_FLOOR, now + 1_300L);
    }

    private void startTransitionP2ToP3(State s, long now) {
        s.phase = Phase.TRANSITION_P2_P3;
        s.activeCombo = null;
        s.transitionUntil = now + ForgeTowerSwordMasters.TRANSITION_P2_P3_MS;
        broadcastEvent(s.instanceId, Map.of("kind", "CLEAR_HAZARDS", "instance", s.instanceId));
        broadcastBanner(s.instanceId, "终局降临 · 阿拉德四剑圣 全员齐聚！", 3_000L, 0xffe07d);
    }

    private void beginPhase3(Dungeon d, State s, long now) throws Exception {
        s.phase = Phase.P3_ALL_FOUR;
        s.phaseStartAt = now;
        s.quadNextAt = now + ForgeTowerSwordMasters.QUAD_COMBO_FIRST_DELAY_MS;
        Vec3 center = AwakeningTowerLayout.guard(s.cell);

        int idx = 0;
        for (Member m : Member.values()) {
            MemberSlot slot = s.slot(m);
            DungeonEnemy entity = resolveEnemy(slot);
            if (entity == null) {
                entity = spawnMember(d, s, m, ForgeTowerSwordMasters.quadStartPosition(s.cell, m), 0.0, now + 1_200L + idx * 300L);
            }
            slot.retreated = false;
            slot.dead = false;
            slot.activeCast = null;
            slot.busyUntil = now + 1_200L + idx * 350L;
            // 错开 P3 个人觉醒初始冷却，避免立刻冲突
            SkillId awk = ForgeTowerSwordMasters.awakeningOf(m);
            slot.cooldownUntil.put(awk.id, now + 14_000L + idx * 6_500L);

            Vec3 spot = AwakeningTowerLayout.ground(owner.level(), s.cell,
                ForgeTowerSwordMasters.quadStartPosition(s.cell, m), center);
            entity.setNoGravity(false);
            entity.setPos(spot.x, spot.y, spot.z);
            entity.setDeltaMovement(Vec3.ZERO);
            facePosition(entity, center);
            entity.play("p3_return", 900L);

            Target t = targets.target(entity);
            t.healthFloor = 0.0;
            t.damageImmune = false;
            t.damageTakenMultiplier = 1.0;
            if (t.life.q > ForgeTowerSwordMasters.RETREAT_HEALTH_FLOOR + 0.01) {
                t.life.q = ForgeTowerSwordMasters.RETREAT_HEALTH_FLOOR;
                t.life.revision++;
            }
            commitTarget(d, entity, t);
            idx++;
        }
    }

    // ==================== 静态纯函数调度器（供无头测试直接验证） ====================

    public static int livingCount(State s) {
        int count = 0;
        for (Member m : Member.values()) {
            if (!s.slot(m).dead) count++;
        }
        return count;
    }

    public static int deadCount(State s) {
        return Member.values().length - livingCount(s);
    }

    public static int activeMainCasters(State s) {
        int count = 0;
        for (Member m : Member.values()) {
            if (s.slot(m).castingMain()) count++;
        }
        return count;
    }

    public static boolean canAcquireMainToken(State s, Member member) {
        if (s.activeCombo != null) return false;
        if (s.phase != Phase.P3_ALL_FOUR) return true;
        return activeMainCasters(s) < ForgeTowerSwordMasters.MAX_P3_MAIN_CASTERS;
    }

    public static boolean canStartAwakening(State s) {
        if (s.activeCombo != null) return false;
        int active = 0;
        for (Member m : Member.values()) {
            if (s.slot(m).castingAwakening()) active++;
        }
        return active < ForgeTowerSwordMasters.MAX_ACTIVE_AWAKENINGS;
    }

    public static List<Member> activeQuadStages(State s) {
        List<Member> stages = new ArrayList<>(4);
        for (Member m : Member.values()) {
            if (s.slot(m).activeOnField()) stages.add(m);
        }
        return stages;
    }

    public static SkillId chooseRegularSkill(MemberSlot slot, long now, Random rng) {
        List<SkillId> candidates = new ArrayList<>(3);
        for (SkillId skill : ForgeTowerSwordMasters.regularSkills(slot.member)) {
            if (slot.cooldownUntil.getOrDefault(skill.id, 0L) <= now) {
                candidates.add(skill);
            }
        }
        if (candidates.isEmpty()) return null;
        if (candidates.size() > 1) {
            candidates.removeIf(sk -> sk.id.equals(slot.lastRegularSkill));
        }
        return candidates.get(rng.nextInt(candidates.size()));
    }

    // ==================== 空闲游走与选招 ====================

    private void tickIdleMember(Dungeon d, State s, MemberSlot slot, DungeonEnemy e, Target t,
                                List<ServerPlayer> fighters, long now) {
        if (CombatContentTypes.controlled(e)) {
            e.getNavigation().stop();
            return;
        }
        ServerPlayer nearest = nearestFighter(e, fighters);
        if (nearest == null) return;
        facePosition(e, nearest.position());

        double dist = e.distanceTo(nearest);
        boolean hasToken = canAcquireMainToken(s, slot.member);

        // 追击或策应绕圈走位
        double desiredRange = hasToken ? 4.8 : 9.5;
        if (!e.isNoGravity()) {
            if (dist > desiredRange + 1.2) {
                e.getNavigation().moveTo(nearest, hasToken ? 1.18 : 1.02);
            } else if (!hasToken && dist < 5.0) {
                // 策应剑圣保持压迫但适当拉开，同时若贴脸则顺手轻斩一刀
                e.getNavigation().stop();
                if (now >= slot.lightMeleeUntil && now >= slot.busyUntil && dist <= ForgeTowerSwordMasters.LIGHT_MELEE_RANGE) {
                    emitLightMelee(d, s, slot, e, t, nearest, now);
                }
            } else {
                e.getNavigation().stop();
            }
        }

        if (now < slot.busyUntil) return;

        // 1. 优先判定个人觉醒技（P1/P2 首次跌破 70% 必放；P3 长 CD 循环；全场互斥最多 1 人）
        SkillId awakening = ForgeTowerSwordMasters.awakeningOf(slot.member);
        boolean shouldAwaken = false;
        if (s.phase == Phase.P3_ALL_FOUR) {
            shouldAwaken = slot.cooldownUntil.getOrDefault(awakening.id, 0L) <= now;
        } else if (!slot.awakeningDone && t.life.q <= ForgeTowerSwordMasters.AWAKENING_TRIGGER_HP) {
            shouldAwaken = true;
        }

        if (shouldAwaken && canAcquireMainToken(s, slot.member) && canStartAwakening(s)) {
            slot.awakeningDone = true;
            long cd = ForgeTowerSwordMasters.scaledCooldown(awakening.cooldownMs, deadCount(s), s.phase == Phase.P3_ALL_FOUR);
            slot.cooldownUntil.put(awakening.id, now + cd);
            startMemberSkill(d, s, slot, e, t, awakening, fighters, now);
            return;
        }

        // 2. 申请主攻令牌释放常规技
        if (!hasToken) return;
        SkillId regular = chooseRegularSkill(slot, now, random);
        if (regular == null) return;
        slot.lastRegularSkill = regular.id;
        long cd = ForgeTowerSwordMasters.scaledCooldown(regular.cooldownMs, deadCount(s), s.phase == Phase.P3_ALL_FOUR);
        slot.cooldownUntil.put(regular.id, now + cd);
        startMemberSkill(d, s, slot, e, t, regular, fighters, now);
    }

    private void emitLightMelee(Dungeon d, State s, MemberSlot slot, DungeonEnemy e, Target t,
                                ServerPlayer targetPlayer, long now) {
        Target.ActionTiming timing = t.actionTiming(now);
        long windup = timing.windup(ForgeTowerSwordMasters.LIGHT_MELEE_WINDUP_MS);
        long active = timing.active(ForgeTowerSwordMasters.LIGHT_MELEE_ACTIVE_MS);
        Vec3 dir = horizontalDirection(e.position(), targetPlayer.position());
        Vec3 origin = groundAt(s.cell, e.position());
        slot.lightMeleeUntil = now + ForgeTowerSwordMasters.LIGHT_MELEE_CD_MS;
        e.play("light_slash", windup + active);
        sendHazard(s.instanceId, slot.member.id + "/light/" + (++s.sequence), "FAN_SLASH", t,
            origin, origin.add(dir.scale(ForgeTowerSwordMasters.LIGHT_MELEE_RANGE)),
            ForgeTowerSwordMasters.LIGHT_MELEE_RANGE, 0.0, 0.0, 55.0,
            now, now + windup, now + windup + active, 0L,
            ForgeTowerSwordMasters.COEFF_LIGHT_MELEE, 0.0, slot.member.themeColor);
    }

    // ==================== 16 项个人技能起手 ====================

    private void startMemberSkill(Dungeon d, State s, MemberSlot slot, DungeonEnemy e, Target t,
                                  SkillId skill, List<ServerPlayer> fighters, long now) {
        Target.ActionTiming timing = t.actionTiming(now);
        ServerPlayer targetPlayer = nearestFighter(e, fighters);
        if (targetPlayer == null) return;
        Vec3 origin = groundAt(s.cell, e.position());
        Vec3 dir = horizontalDirection(origin, targetPlayer.position());
        Vec3 center = AwakeningTowerLayout.guard(s.cell);
        e.getNavigation().stop();
        facePosition(e, targetPlayer.position());

        MemberCast cast = new MemberCast(skill);
        cast.startAt = now;
        cast.anchor = origin;
        cast.direction = dir;
        slot.activeCast = cast;

        switch (skill) {
            // ---------- 布万加 ----------
            case BWANGA_EARTH_SLAM -> {
                long windup = timing.windup(ForgeTowerSwordMasters.BWANGA_S1_WINDUP_MS);
                long ringMs = timing.active(ForgeTowerSwordMasters.BWANGA_S1_RING_DURATION_MS);
                cast.nextAt = now + windup;
                cast.endAt = now + windup + ringMs;
                cast.subPhase = 0;
                e.play("heavy_slam_windup", windup);
                sendHazard(s.instanceId, "bwanga_s1_slam/" + (++s.sequence), "CIRCLE", t,
                    origin, origin.add(dir), ForgeTowerSwordMasters.BWANGA_S1_SLAM_RADIUS, 0.0, 0.0, 0.0,
                    now, now + windup, now + windup + 320L, 0L,
                    ForgeTowerSwordMasters.COEFF_BWANGA_S1_SLAM, 0.0, Member.BWANGA.themeColor);
            }
            case BWANGA_BANTU_CHARGE -> {
                long windup = timing.windup(ForgeTowerSwordMasters.BWANGA_S2_WINDUP_MS);
                long dashMs = timing.active(ForgeTowerSwordMasters.BWANGA_S2_DASH_MS);
                Vec3 end = clampLineEnd(s.cell, origin, dir, ForgeTowerSwordMasters.BWANGA_S2_DASH_DIST);
                cast.from = origin;
                cast.to = end;
                cast.direction = horizontalDirection(origin, end);
                cast.nextAt = now + windup;
                cast.endAt = now + windup + dashMs;
                cast.subPhase = 0;
                facePosition(e, end);
                e.play("charge_windup", windup);
                sendHazard(s.instanceId, "bwanga_s2_dash/" + (++s.sequence), "DASH_LINE", t,
                    origin, end, origin.distanceTo(end), ForgeTowerSwordMasters.BWANGA_S2_DASH_WIDTH, 0.0, 0.0,
                    now, now + windup, now + windup + dashMs, 0L,
                    ForgeTowerSwordMasters.COEFF_BWANGA_S2_DASH, 6.5, Member.BWANGA.themeColor);
            }
            case BWANGA_BOOMERANG_HAMMER -> {
                long windup = timing.windup(ForgeTowerSwordMasters.BWANGA_S3_WINDUP_MS);
                long outMs = timing.active(ForgeTowerSwordMasters.BWANGA_S3_OUT_MS);
                long hoverMs = timing.duration(ForgeTowerSwordMasters.BWANGA_S3_HOVER_MS);
                Vec3 tip = clampLineEnd(s.cell, origin, dir, ForgeTowerSwordMasters.BWANGA_S3_RANGE);
                cast.from = origin;
                cast.to = tip;
                cast.nextAt = now + windup;
                cast.auxAt = now + windup + outMs + hoverMs;
                cast.endAt = cast.auxAt + timing.active(ForgeTowerSwordMasters.BWANGA_S3_RETURN_MS);
                cast.subPhase = 0;
                e.play("hammer_throw", windup + 260L);
                sendHazard(s.instanceId, "bwanga_s3_out/" + (++s.sequence), "DASH_LINE", t,
                    origin, tip, origin.distanceTo(tip), ForgeTowerSwordMasters.BWANGA_S3_WIDTH, 0.0, 0.0,
                    now, now + windup, now + windup + outMs, 0L,
                    ForgeTowerSwordMasters.COEFF_BWANGA_S3_OUT, 0.0, Member.BWANGA.themeColor);
            }
            case BWANGA_AWAKENING -> {
                long leapWarn = timing.windup(ForgeTowerSwordMasters.BWANGA_A_LEAP_WARN_MS);
                Vec3 land = groundAt(s.cell, center.lerp(targetPlayer.position(), 0.25));
                cast.from = origin;
                cast.to = land;
                cast.nextAt = now + leapWarn;
                cast.subPhase = 0;
                e.setNoGravity(true);
                e.play("awakening_leap", leapWarn);
                broadcastBanner(s.instanceId, "布万加 · 族长之怒·千钧震天击！", 3_800L, Member.BWANGA.themeColor);
                sendHazard(s.instanceId, "bwanga_awk_land/" + (++s.sequence), "CIRCLE", t,
                    land, land.add(0, 0, 1), ForgeTowerSwordMasters.BWANGA_A_LAND_RADIUS, 0.0, 0.0, 0.0,
                    now, now + leapWarn, now + leapWarn + 360L, 0L,
                    ForgeTowerSwordMasters.COEFF_BWANGA_A_LAND, 5.0, Member.BWANGA.themeColor);
            }

            // ---------- 巴恩 ----------
            case BAAN_TRIPLE_THRUST -> {
                long windup = timing.windup(ForgeTowerSwordMasters.BAAN_S1_WINDUP_MS);
                long stepMs = timing.active(ForgeTowerSwordMasters.BAAN_S1_STEP_MS);
                Vec3 end = clampLineEnd(s.cell, origin, dir, ForgeTowerSwordMasters.BAAN_S1_DASH_DIST);
                cast.from = origin;
                cast.to = end;
                cast.step = 0;
                cast.totalSteps = ForgeTowerSwordMasters.BAAN_S1_STRIKES;
                cast.nextAt = now + windup;
                cast.endAt = now + windup + stepMs;
                cast.subPhase = 0;
                e.play("thrust_combo", windup + stepMs);
                sendHazard(s.instanceId, "baan_s1_0/" + (++s.sequence), "DASH_LINE", t,
                    origin, end, origin.distanceTo(end), ForgeTowerSwordMasters.BAAN_S1_WIDTH, 0.0, 0.0,
                    now, now + windup, now + windup + stepMs, 0L,
                    ForgeTowerSwordMasters.COEFF_BAAN_S1[0], 0.0, Member.BAAN.themeColor);
            }
            case BAAN_HURRICANE_BLADES -> {
                long windup = timing.windup(ForgeTowerSwordMasters.BAAN_S2_WINDUP_MS);
                long wave2Delay = timing.duration(ForgeTowerSwordMasters.BAAN_S2_WAVE2_DELAY_MS);
                long travel = timing.active(ForgeTowerSwordMasters.BAAN_S2_TRAVEL_MS);
                cast.nextAt = now + windup;
                cast.auxAt = now + windup + wave2Delay;
                cast.endAt = cast.auxAt + 250L;
                cast.subPhase = 0;
                e.play("blade_fan", windup + wave2Delay);
                for (double deg : ForgeTowerSwordMasters.BAAN_S2_WAVE1_ANGLES) {
                    Vec3 dRot = rotateY(dir, Math.toRadians(deg));
                    Vec3 end = origin.add(dRot.scale(ForgeTowerSwordMasters.BAAN_S2_RANGE));
                    sendHazard(s.instanceId, "baan_s2_w1/" + (++s.sequence), "SLASH_WAVE", t,
                        origin, end, ForgeTowerSwordMasters.BAAN_S2_RANGE, ForgeTowerSwordMasters.BAAN_S2_WIDTH, 0.0, deg * 0.35,
                        now, now + windup, now + windup + travel, 0L,
                        ForgeTowerSwordMasters.COEFF_BAAN_S2, 0.0, Member.BAAN.themeColor);
                }
            }
            case BAAN_ZIGZAG_STEP -> {
                long windup = timing.windup(ForgeTowerSwordMasters.BAAN_S3_WINDUP_MS);
                long segMs = timing.active(ForgeTowerSwordMasters.BAAN_S3_SEG_MS);
                Vec3 side = new Vec3(-dir.z, 0, dir.x).scale(random.nextBoolean() ? 1.0 : -1.0);
                Vec3 d1 = dir.scale(0.65).add(side.scale(0.76)).normalize();
                Vec3 mid = clampLineEnd(s.cell, origin, d1, ForgeTowerSwordMasters.BAAN_S3_SEG_DIST);
                Vec3 d2 = horizontalDirection(mid, targetPlayer.position().add(dir.scale(2.5)));
                Vec3 end = clampLineEnd(s.cell, mid, d2, ForgeTowerSwordMasters.BAAN_S3_SEG_DIST);
                cast.from = origin;
                cast.mid = mid;
                cast.to = end;
                cast.nextAt = now + windup;
                cast.auxAt = now + windup + segMs;
                cast.endAt = cast.auxAt + segMs;
                cast.subPhase = 0;
                e.play("zigzag_step", windup + segMs * 2);
                sendHazard(s.instanceId, "baan_s3_a/" + (++s.sequence), "DASH_LINE", t,
                    origin, mid, origin.distanceTo(mid), ForgeTowerSwordMasters.BAAN_S3_WIDTH, 0.0, 0.0,
                    now, now + windup, now + windup + segMs, 0L,
                    ForgeTowerSwordMasters.COEFF_BAAN_S3_DASH, 0.0, Member.BAAN.themeColor);
                sendHazard(s.instanceId, "baan_s3_b/" + (++s.sequence), "DASH_LINE", t,
                    mid, end, mid.distanceTo(end), ForgeTowerSwordMasters.BAAN_S3_WIDTH, 0.0, 0.0,
                    now + 120L, now + windup + segMs, now + windup + segMs * 2, 0L,
                    ForgeTowerSwordMasters.COEFF_BAAN_S3_DASH, 0.0, Member.BAAN.themeColor);
            }
            case BAAN_AWAKENING -> {
                cast.anchor = groundAt(s.cell, targetPlayer.position());
                cast.baseAngle = random.nextDouble() * Math.PI * 2.0;
                cast.step = 0;
                cast.totalSteps = ForgeTowerSwordMasters.BAAN_A_WAVES;
                cast.nextAt = now;
                cast.subPhase = 0;
                broadcastBanner(s.instanceId, "巴恩 · 飓风剑狱·极风乱舞！", 4_200L, Member.BAAN.themeColor);
                fireBaanAwakeningWave(s, e, t, cast, timing, now);
            }

            // ---------- 阿甘左 ----------
            case AGANZO_GALE_CLEAVE -> {
                long windup = timing.windup(ForgeTowerSwordMasters.AGANZO_S1_WINDUP_MS);
                long travel = timing.active(ForgeTowerSwordMasters.AGANZO_S1_WAVE_TRAVEL_MS);
                cast.nextAt = now + windup;
                cast.endAt = now + windup + 300L;
                cast.subPhase = 0;
                e.play("greatsword_cleave", windup + 320L);
                sendHazard(s.instanceId, "aganzo_s1_fan/" + (++s.sequence), "FAN_SLASH", t,
                    origin, origin.add(dir.scale(ForgeTowerSwordMasters.AGANZO_S1_FAN_RADIUS)),
                    ForgeTowerSwordMasters.AGANZO_S1_FAN_RADIUS, 0.0, 0.0, ForgeTowerSwordMasters.AGANZO_S1_FAN_HALF_DEG,
                    now, now + windup, now + windup + 280L, 0L,
                    ForgeTowerSwordMasters.COEFF_AGANZO_S1_FAN, 0.0, Member.AGANZO.themeColor);
                Vec3 waveEnd = origin.add(dir.scale(ForgeTowerSwordMasters.AGANZO_S1_WAVE_RANGE));
                sendHazard(s.instanceId, "aganzo_s1_wave/" + (++s.sequence), "SLASH_WAVE", t,
                    origin, waveEnd, ForgeTowerSwordMasters.AGANZO_S1_WAVE_RANGE, ForgeTowerSwordMasters.AGANZO_S1_WAVE_WIDTH, 0.0, 0.0,
                    now + 120L, now + windup, now + windup + travel, 0L,
                    ForgeTowerSwordMasters.COEFF_AGANZO_S1_WAVE, 0.0, Member.AGANZO.themeColor);
            }
            case AGANZO_RISING_CRASH -> {
                long windup = timing.windup(ForgeTowerSwordMasters.AGANZO_S2_WINDUP_MS);
                long dashMs = timing.active(ForgeTowerSwordMasters.AGANZO_S2_DASH_MS);
                Vec3 end = clampLineEnd(s.cell, origin, dir, ForgeTowerSwordMasters.AGANZO_S2_DASH_DIST);
                cast.from = origin;
                cast.to = end;
                cast.nextAt = now + windup;
                cast.endAt = now + windup + dashMs;
                cast.subPhase = 0;
                e.play("rising_dash", windup + dashMs);
                sendHazard(s.instanceId, "aganzo_s2_dash/" + (++s.sequence), "DASH_LINE", t,
                    origin, end, origin.distanceTo(end), ForgeTowerSwordMasters.AGANZO_S2_DASH_WIDTH, 0.0, 0.0,
                    now, now + windup, now + windup + dashMs, 0L,
                    ForgeTowerSwordMasters.COEFF_AGANZO_S2_DASH, 4.5, Member.AGANZO.themeColor);
            }
            case AGANZO_WHIRL_SLASH -> {
                long windup = timing.windup(ForgeTowerSwordMasters.AGANZO_S3_WINDUP_MS);
                long spinMs = timing.active(ForgeTowerSwordMasters.AGANZO_S3_SPIN_MS);
                long tickMs = timing.duration(ForgeTowerSwordMasters.AGANZO_S3_TICK_MS);
                cast.nextAt = now + windup;
                cast.endAt = now + windup + spinMs;
                cast.subPhase = 0;
                cast.lastMoveSyncAt = now;
                cast.hazardId = "aganzo_s3_spin/" + (++s.sequence);
                e.play("whirlwind_spin", windup + spinMs);
                sendHazard(s.instanceId, cast.hazardId, "SPIN_VORTEX", t,
                    origin, origin.add(dir), ForgeTowerSwordMasters.AGANZO_S3_RADIUS, 0.0,
                    ForgeTowerSwordMasters.AGANZO_S3_PULL_OUTER, 0.0,
                    now, now + windup, now + windup + spinMs, tickMs,
                    ForgeTowerSwordMasters.COEFF_AGANZO_S3_SPIN, 0.0, Member.AGANZO.themeColor);
            }
            case AGANZO_AWAKENING -> {
                long chargeMs = timing.windup(ForgeTowerSwordMasters.AGANZO_A_CHARGE_MS);
                cast.anchor = origin;
                cast.nextAt = now + chargeMs;
                cast.subPhase = 0;
                // 蓄力期自带 50% 霸体减伤
                t.damageTakenMultiplier = ForgeTowerSwordMasters.AGANZO_A_SUPER_ARMOR_TAKEN;
                try { commitTarget(d, e, t); } catch (Exception ignored) {}
                e.play("awakening_charge", chargeMs);
                broadcastBanner(s.instanceId, "阿甘左 · 苍岚灭世·巨剑裂地式！", 4_400L, Member.AGANZO.themeColor);
                emitCrossLines(s, t, origin, 0.0, ForgeTowerSwordMasters.AGANZO_A_CROSS_HALF_LEN,
                    ForgeTowerSwordMasters.AGANZO_A_CROSS_WIDTH, now, now + chargeMs, now + chargeMs + 420L,
                    ForgeTowerSwordMasters.COEFF_AGANZO_A_CROSS, Member.AGANZO.themeColor, "aganzo_awk_plus");
            }

            // ---------- 西岚 ----------
            case SIRAN_IAI_DRAW -> {
                long windup = timing.windup(ForgeTowerSwordMasters.SIRAN_S1_WINDUP_MS);
                cast.nextAt = now + windup;
                cast.endAt = now + windup + 260L;
                cast.subPhase = 0;
                e.play("iai_sheathe", windup);
                sendHazard(s.instanceId, "siran_s1_iai/" + (++s.sequence), "FAN_SLASH", t,
                    origin, origin.add(dir.scale(ForgeTowerSwordMasters.SIRAN_S1_RADIUS)),
                    ForgeTowerSwordMasters.SIRAN_S1_RADIUS, 0.0, 0.0, ForgeTowerSwordMasters.SIRAN_S1_HALF_DEG,
                    now, now + windup, now + windup + 300L, 0L,
                    ForgeTowerSwordMasters.COEFF_SIRAN_S1, 0.0, Member.SIRAN.themeColor);
            }
            case SIRAN_TRIPLE_FLASH -> {
                cast.step = 0;
                cast.totalSteps = ForgeTowerSwordMasters.SIRAN_S2_STRIKES;
                cast.subPhase = 0;
                scheduleSiranFlashStep(s, e, t, cast, targetPlayer.position(), timing, now);
            }
            case SIRAN_ECHO_CROSS -> {
                long windup = timing.windup(ForgeTowerSwordMasters.SIRAN_S3_WINDUP_MS);
                long echoDelay = timing.duration(ForgeTowerSwordMasters.SIRAN_S3_ECHO_DELAY_MS);
                cast.nextAt = now + windup;
                cast.endAt = now + windup + echoDelay + 280L;
                cast.subPhase = 0;
                e.play("echo_cross", windup + 300L);
                for (double deg : new double[]{-18.0, 18.0}) {
                    Vec3 dRot = rotateY(dir, Math.toRadians(deg));
                    Vec3 end = origin.add(dRot.scale(ForgeTowerSwordMasters.SIRAN_S3_RANGE));
                    // 第一段斩线
                    sendHazard(s.instanceId, "siran_s3_a/" + (++s.sequence), "INSTANT_LINE", t,
                        origin, end, ForgeTowerSwordMasters.SIRAN_S3_RANGE, ForgeTowerSwordMasters.SIRAN_S3_WIDTH_FIRST, 0.0, 0.0,
                        now, now + windup, now + windup + 260L, 0L,
                        ForgeTowerSwordMasters.COEFF_SIRAN_S3_FIRST, 0.0, Member.SIRAN.themeColor);
                    // 0.85s 延迟二次斩裂（沿原轨迹，随即消散不留地板）
                    long echoAt = now + windup + echoDelay;
                    sendHazard(s.instanceId, "siran_s3_b/" + (++s.sequence), "INSTANT_LINE", t,
                        origin, end, ForgeTowerSwordMasters.SIRAN_S3_RANGE, ForgeTowerSwordMasters.SIRAN_S3_WIDTH_ECHO, 0.0, 0.0,
                        now + windup + 80L, echoAt, echoAt + 280L, 0L,
                        ForgeTowerSwordMasters.COEFF_SIRAN_S3_ECHO, 0.0, 0xffffff);
                }
            }
            case SIRAN_AWAKENING -> {
                long drawSpan = timing.windup(ForgeTowerSwordMasters.SIRAN_A_DRAW_SPAN_MS);
                long stepDet = timing.duration(ForgeTowerSwordMasters.SIRAN_A_DETONATE_STEP_MS);
                Vec3 backSpot = clampLineEnd(s.cell, origin, dir.scale(-1.0), 10.0);
                e.setPos(backSpot.x, backSpot.y, backSpot.z);
                e.setDeltaMovement(Vec3.ZERO);
                facePosition(e, center);
                cast.anchor = groundAt(s.cell, center.lerp(targetPlayer.position(), 0.35));
                cast.seed = random.nextLong();
                cast.nextAt = now + drawSpan + stepDet * ForgeTowerSwordMasters.SIRAN_A_LINE_COUNT;
                cast.subPhase = 0;
                e.play("siran_awakening", drawSpan + 1_200L);
                broadcastBanner(s.instanceId, "西岚 · 刹那芳华·时空无痕斩！", 4_400L, Member.SIRAN.themeColor);

                List<SlashSegment> lines = ForgeTowerSwordMasters.siranAwakeningLines(cast.anchor, cast.seed);
                for (int i = 0; i < lines.size(); i++) {
                    SlashSegment seg = lines.get(i);
                    long lineStart = now + (long) i * (drawSpan / Math.max(1, lines.size()));
                    long detAt = now + drawSpan + i * stepDet;
                    sendHazard(s.instanceId, "siran_awk_l" + i + "/" + (++s.sequence), "INSTANT_LINE", t,
                        seg.from(), seg.to(), seg.from().distanceTo(seg.to()), ForgeTowerSwordMasters.SIRAN_A_LINE_WIDTH, 0.0, 0.0,
                        lineStart, detAt, detAt + 300L, 0L,
                        ForgeTowerSwordMasters.COEFF_SIRAN_A_LINE, 0.0, Member.SIRAN.themeColor);
                }
            }
        }
    }

    // ==================== 16 项个人技能推进 ====================

    private void tickMemberCast(Dungeon d, State s, MemberSlot slot, DungeonEnemy e, Target t,
                                List<ServerPlayer> fighters, long now) {
        MemberCast cast = slot.activeCast;
        if (cast == null) return;
        Target.ActionTiming timing = t.actionTiming(now);

        switch (cast.skill) {
            case BWANGA_EARTH_SLAM -> {
                if (cast.subPhase == 0 && now >= cast.nextAt) {
                    cast.subPhase = 1;
                    long ringMs = timing.active(ForgeTowerSwordMasters.BWANGA_S1_RING_DURATION_MS);
                    e.play("heavy_slam_hit", 420L);
                    sendHazard(s.instanceId, "bwanga_s1_ring/" + (++s.sequence), "RING_WAVE", t,
                        cast.anchor, cast.anchor.add(0, 0, 1),
                        ForgeTowerSwordMasters.BWANGA_S1_SLAM_RADIUS, ForgeTowerSwordMasters.BWANGA_S1_RING_WIDTH,
                        ForgeTowerSwordMasters.BWANGA_S1_RING_END_RADIUS, 0.0,
                        now, now, now + ringMs, 0L,
                        ForgeTowerSwordMasters.COEFF_BWANGA_S1_RING, 0.0, Member.BWANGA.themeColor);
                    finishCast(slot, timing.recovery(ForgeTowerSwordMasters.BWANGA_S1_RECOVERY_MS), now);
                }
            }
            case BWANGA_BANTU_CHARGE -> {
                if (cast.subPhase == 0) {
                    facePosition(e, cast.to);
                    if (now >= cast.nextAt) {
                        cast.subPhase = 1;
                        e.setNoGravity(true);
                        e.play("charge_dash", Math.max(100L, cast.endAt - now));
                    }
                    return;
                }
                if (cast.subPhase == 1) {
                    double prog = Math.clamp((now - cast.nextAt) / (double) Math.max(1L, cast.endAt - cast.nextAt), 0.0, 1.0);
                    Vec3 p = cast.from.lerp(cast.to, prog);
                    e.setPos(p.x, p.y, p.z);
                    e.setDeltaMovement(Vec3.ZERO);
                    if (now >= cast.endAt) {
                        landToGround(e, s.cell, cast.to);
                        long sweepWarn = timing.windup(ForgeTowerSwordMasters.BWANGA_S2_SWEEP_WINDUP_MS);
                        cast.subPhase = 2;
                        cast.endAt = now + sweepWarn + 260L;
                        ServerPlayer aim = nearestFighter(e, fighters);
                        Vec3 sweepDir = aim != null ? horizontalDirection(e.position(), aim.position()) : cast.direction;
                        Vec3 pos = groundAt(s.cell, e.position());
                        e.play("heavy_sweep", sweepWarn + 260L);
                        sendHazard(s.instanceId, "bwanga_s2_sweep/" + (++s.sequence), "FAN_SLASH", t,
                            pos, pos.add(sweepDir.scale(ForgeTowerSwordMasters.BWANGA_S2_SWEEP_RADIUS)),
                            ForgeTowerSwordMasters.BWANGA_S2_SWEEP_RADIUS, 0.0, 0.0, 90.0,
                            now, now + sweepWarn, now + sweepWarn + 260L, 0L,
                            ForgeTowerSwordMasters.COEFF_BWANGA_S2_SWEEP, 0.0, Member.BWANGA.themeColor);
                    }
                    return;
                }
                if (now >= cast.endAt) {
                    finishCast(slot, timing.recovery(ForgeTowerSwordMasters.BWANGA_S2_RECOVERY_MS), now);
                }
            }
            case BWANGA_BOOMERANG_HAMMER -> {
                if (cast.subPhase == 0 && now >= cast.nextAt) {
                    cast.subPhase = 1;
                    // 去程期间小步侧移改变回程轨迹
                    Vec3 side = new Vec3(-cast.direction.z, 0, cast.direction.x).scale(random.nextBoolean() ? 4.2 : -4.2);
                    Vec3 shifted = groundAt(s.cell, cast.from.add(side));
                    e.getNavigation().moveTo(shifted.x, shifted.y, shifted.z, 1.15);
                }
                if (cast.subPhase == 1 && now >= cast.auxAt) {
                    cast.subPhase = 2;
                    e.getNavigation().stop();
                    Vec3 backPos = groundAt(s.cell, e.position());
                    long retMs = timing.active(ForgeTowerSwordMasters.BWANGA_S3_RETURN_MS);
                    cast.endAt = now + retMs;
                    sendHazard(s.instanceId, "bwanga_s3_ret/" + (++s.sequence), "DASH_LINE", t,
                        cast.to, backPos, cast.to.distanceTo(backPos), ForgeTowerSwordMasters.BWANGA_S3_WIDTH, 0.0, 0.0,
                        now - 240L, now, now + retMs, 0L,
                        ForgeTowerSwordMasters.COEFF_BWANGA_S3_RETURN, 0.0, Member.BWANGA.themeColor);
                }
                if (cast.subPhase == 2 && now >= cast.endAt) {
                    finishCast(slot, timing.recovery(ForgeTowerSwordMasters.BWANGA_S3_RECOVERY_MS), now);
                }
            }
            case BWANGA_AWAKENING -> {
                if (cast.subPhase == 0) {
                    double prog = Math.clamp((now - cast.startAt) / (double) Math.max(1L, cast.nextAt - cast.startAt), 0.0, 1.0);
                    Vec3 p = cast.from.lerp(cast.to, prog);
                    double arc = Math.sin(prog * Math.PI) * 5.2;
                    e.setPos(p.x, p.y + arc, p.z);
                    e.setDeltaMovement(Vec3.ZERO);
                    if (now >= cast.nextAt) {
                        landToGround(e, s.cell, cast.to);
                        cast.subPhase = 1;
                        long w1 = timing.active(ForgeTowerSwordMasters.BWANGA_A_WAVE1_DURATION_MS);
                        cast.nextAt = now + w1;
                        e.play("heavy_slam_hit", 450L);
                        sendHazard(s.instanceId, "bwanga_awk_w1/" + (++s.sequence), "RING_WAVE", t,
                            cast.to, cast.to.add(0, 0, 1),
                            2.5, ForgeTowerSwordMasters.BWANGA_A_RING_WIDTH, ForgeTowerSwordMasters.BWANGA_A_RING_MAX_RADIUS, 0.0,
                            now, now, now + w1, 0L,
                            ForgeTowerSwordMasters.COEFF_BWANGA_A_WAVE1, 0.0, Member.BWANGA.themeColor);
                    }
                    return;
                }
                if (cast.subPhase == 1 && now >= cast.nextAt) {
                    cast.subPhase = 2;
                    long w2 = timing.active(ForgeTowerSwordMasters.BWANGA_A_WAVE2_DURATION_MS);
                    cast.nextAt = now + w2;
                    e.play("heavy_slam_hit", 450L);
                    sendHazard(s.instanceId, "bwanga_awk_w2/" + (++s.sequence), "RING_WAVE", t,
                        cast.to, cast.to.add(0, 0, 1),
                        ForgeTowerSwordMasters.BWANGA_A_RING_MAX_RADIUS, ForgeTowerSwordMasters.BWANGA_A_RING_WIDTH, 2.5, 0.0,
                        now, now, now + w2, 0L,
                        ForgeTowerSwordMasters.COEFF_BWANGA_A_WAVE2, 0.0, 0xffd092);
                    return;
                }
                if (cast.subPhase == 2 && now >= cast.nextAt) {
                    cast.subPhase = 3;
                    long w3 = timing.windup(ForgeTowerSwordMasters.BWANGA_A_WAVE3_WARN_MS);
                    cast.endAt = now + w3 + 380L;
                    e.play("heavy_slam_windup", w3);
                    // 留出中圈安全带 [7.5, 12.5]，内外圈全爆
                    sendHazard(s.instanceId, "bwanga_awk_final/" + (++s.sequence), "SAFE_BAND_BURST", t,
                        cast.to, cast.to.add(0, 0, 1),
                        ForgeTowerSwordMasters.BWANGA_A_SAFE_BAND_MIN, ForgeTowerSwordMasters.BWANGA_A_SAFE_BAND_MAX,
                        ForgeTowerSwordMasters.BWANGA_A_RING_MAX_RADIUS + 4.0, 0.0,
                        now, now + w3, now + w3 + 380L, 0L,
                        ForgeTowerSwordMasters.COEFF_BWANGA_A_FINAL, 0.0, Member.BWANGA.themeColor);
                    return;
                }
                if (cast.subPhase == 3 && now >= cast.endAt) {
                    e.play("exhaust_stagger", ForgeTowerSwordMasters.BWANGA_A_RECOVERY_MS);
                    finishCast(slot, timing.recovery(ForgeTowerSwordMasters.BWANGA_A_RECOVERY_MS), now);
                }
            }
            case BAAN_TRIPLE_THRUST -> {
                if (cast.subPhase == 0) {
                    if (now >= cast.nextAt) {
                        cast.subPhase = 1;
                        e.setNoGravity(true);
                    }
                    return;
                }
                double prog = Math.clamp((now - cast.nextAt) / (double) Math.max(1L, cast.endAt - cast.nextAt), 0.0, 1.0);
                Vec3 p = cast.from.lerp(cast.to, prog);
                e.setPos(p.x, p.y, p.z);
                e.setDeltaMovement(Vec3.ZERO);
                if (now >= cast.endAt) {
                    landToGround(e, s.cell, cast.to);
                    cast.step++;
                    if (cast.step >= cast.totalSteps) {
                        finishCast(slot, timing.recovery(ForgeTowerSwordMasters.BAAN_S1_RECOVERY_MS), now);
                        return;
                    }
                    ServerPlayer aim = nearestFighter(e, fighters);
                    Vec3 cur = groundAt(s.cell, e.position());
                    Vec3 nextDir = aim != null ? horizontalDirection(cur, aim.position()) : cast.direction;
                    Vec3 nextEnd = clampLineEnd(s.cell, cur, nextDir, ForgeTowerSwordMasters.BAAN_S1_DASH_DIST);
                    long stepWarn = timing.windup(ForgeTowerSwordMasters.BAAN_S1_STEP_WARN_MS);
                    long stepMs = timing.active(ForgeTowerSwordMasters.BAAN_S1_STEP_MS);
                    cast.from = cur;
                    cast.to = nextEnd;
                    cast.direction = nextDir;
                    cast.nextAt = now + stepWarn;
                    cast.endAt = now + stepWarn + stepMs;
                    cast.subPhase = 0;
                    facePosition(e, nextEnd);
                    e.play("thrust_combo", stepWarn + stepMs);
                    sendHazard(s.instanceId, "baan_s1_" + cast.step + "/" + (++s.sequence), "DASH_LINE", t,
                        cur, nextEnd, cur.distanceTo(nextEnd), ForgeTowerSwordMasters.BAAN_S1_WIDTH, 0.0, 0.0,
                        now, now + stepWarn, now + stepWarn + stepMs, 0L,
                        ForgeTowerSwordMasters.COEFF_BAAN_S1[Math.min(cast.step, 2)], 0.0, Member.BAAN.themeColor);
                }
            }
            case BAAN_HURRICANE_BLADES -> {
                if (cast.subPhase == 0 && now >= cast.auxAt) {
                    cast.subPhase = 1;
                    ServerPlayer aim = nearestFighter(e, fighters);
                    Vec3 cur = groundAt(s.cell, e.position());
                    Vec3 nextDir = aim != null ? horizontalDirection(cur, aim.position()) : cast.direction;
                    long warn = timing.windup(ForgeTowerSwordMasters.BAAN_S2_WAVE2_WARN_MS);
                    long travel = timing.active(ForgeTowerSwordMasters.BAAN_S2_TRAVEL_MS);
                    for (double deg : ForgeTowerSwordMasters.BAAN_S2_WAVE2_ANGLES) {
                        Vec3 dRot = rotateY(nextDir, Math.toRadians(deg));
                        Vec3 end = cur.add(dRot.scale(ForgeTowerSwordMasters.BAAN_S2_RANGE));
                        sendHazard(s.instanceId, "baan_s2_w2/" + (++s.sequence), "SLASH_WAVE", t,
                            cur, end, ForgeTowerSwordMasters.BAAN_S2_RANGE, ForgeTowerSwordMasters.BAAN_S2_WIDTH, 0.0, deg * 0.4,
                            now, now + warn, now + warn + travel, 0L,
                            ForgeTowerSwordMasters.COEFF_BAAN_S2, 0.0, 0xa8ffe4);
                    }
                    cast.endAt = now + warn + 260L;
                }
                if (cast.subPhase == 1 && now >= cast.endAt) {
                    finishCast(slot, timing.recovery(ForgeTowerSwordMasters.BAAN_S2_RECOVERY_MS), now);
                }
            }
            case BAAN_ZIGZAG_STEP -> {
                if (cast.subPhase == 0) {
                    if (now >= cast.nextAt) {
                        cast.subPhase = 1;
                        e.setNoGravity(true);
                    }
                    return;
                }
                if (cast.subPhase == 1) {
                    double prog = Math.clamp((now - cast.nextAt) / (double) Math.max(1L, cast.auxAt - cast.nextAt), 0.0, 1.0);
                    Vec3 p = cast.from.lerp(cast.mid, prog);
                    e.setPos(p.x, p.y, p.z);
                    e.setDeltaMovement(Vec3.ZERO);
                    if (now >= cast.auxAt) cast.subPhase = 2;
                    return;
                }
                if (cast.subPhase == 2) {
                    double prog = Math.clamp((now - cast.auxAt) / (double) Math.max(1L, cast.endAt - cast.auxAt), 0.0, 1.0);
                    Vec3 p = cast.mid.lerp(cast.to, prog);
                    e.setPos(p.x, p.y, p.z);
                    e.setDeltaMovement(Vec3.ZERO);
                    if (now >= cast.endAt) {
                        landToGround(e, s.cell, cast.to);
                        cast.subPhase = 3;
                        long upperWarn = timing.windup(ForgeTowerSwordMasters.BAAN_S3_UPPER_WARN_MS);
                        cast.endAt = now + upperWarn + 240L;
                        Vec3 pos = groundAt(s.cell, e.position());
                        e.play("upper_slash", upperWarn + 240L);
                        sendHazard(s.instanceId, "baan_s3_up/" + (++s.sequence), "CIRCLE", t,
                            pos, pos.add(0, 0, 1), ForgeTowerSwordMasters.BAAN_S3_UPPER_RADIUS, 0.0, 0.0, 0.0,
                            now, now + upperWarn, now + upperWarn + 240L, 0L,
                            ForgeTowerSwordMasters.COEFF_BAAN_S3_UPPER, 0.0, Member.BAAN.themeColor);
                    }
                    return;
                }
                if (now >= cast.endAt) {
                    finishCast(slot, timing.recovery(ForgeTowerSwordMasters.BAAN_S3_RECOVERY_MS), now);
                }
            }
            case BAAN_AWAKENING -> {
                if (cast.subPhase == 0 && now >= cast.nextAt) {
                    if (cast.step < cast.totalSteps) {
                        fireBaanAwakeningWave(s, e, t, cast, timing, now);
                    } else {
                        cast.subPhase = 1;
                        long finWarn = timing.windup(ForgeTowerSwordMasters.BAAN_A_FINISHER_WARN_MS);
                        long finTravel = timing.active(ForgeTowerSwordMasters.BAAN_A_FINISHER_TRAVEL_MS);
                        Vec3 dir = new Vec3(Math.cos(cast.baseAngle), 0, Math.sin(cast.baseAngle));
                        Vec3 start = groundAt(s.cell, cast.anchor.subtract(dir.scale(ForgeTowerSwordMasters.BAAN_A_FINISHER_RANGE * 0.5)));
                        Vec3 end = groundAt(s.cell, cast.anchor.add(dir.scale(ForgeTowerSwordMasters.BAAN_A_FINISHER_RANGE * 0.5)));
                        cast.from = start;
                        cast.to = end;
                        cast.nextAt = now + finWarn;
                        cast.endAt = now + finWarn + finTravel;
                        e.setPos(start.x, start.y, start.z);
                        facePosition(e, end);
                        e.play("awakening_finisher", finWarn + finTravel);
                        sendHazard(s.instanceId, "baan_awk_fin/" + (++s.sequence), "SLASH_WAVE", t,
                            start, end, start.distanceTo(end), ForgeTowerSwordMasters.BAAN_A_FINISHER_WIDTH, 0.0, 0.0,
                            now, now + finWarn, now + finWarn + finTravel, 0L,
                            ForgeTowerSwordMasters.COEFF_BAAN_A_FINISHER, 0.0, 0xbaffea);
                    }
                    return;
                }
                if (cast.subPhase == 1) {
                    if (now >= cast.nextAt) {
                        double prog = Math.clamp((now - cast.nextAt) / (double) Math.max(1L, cast.endAt - cast.nextAt), 0.0, 1.0);
                        Vec3 p = cast.from.lerp(cast.to, prog);
                        e.setPos(p.x, p.y, p.z);
                        e.setDeltaMovement(Vec3.ZERO);
                    }
                    if (now >= cast.endAt) {
                        landToGround(e, s.cell, cast.to);
                        e.play("exhaust_stagger", ForgeTowerSwordMasters.BAAN_A_RECOVERY_MS);
                        finishCast(slot, timing.recovery(ForgeTowerSwordMasters.BAAN_A_RECOVERY_MS), now);
                    }
                }
            }
            case AGANZO_GALE_CLEAVE -> {
                if (now >= cast.endAt) {
                    finishCast(slot, timing.recovery(ForgeTowerSwordMasters.AGANZO_S1_RECOVERY_MS), now);
                }
            }
            case AGANZO_RISING_CRASH -> {
                if (cast.subPhase == 0) {
                    if (now >= cast.nextAt) {
                        cast.subPhase = 1;
                        e.setNoGravity(true);
                    }
                    return;
                }
                if (cast.subPhase == 1) {
                    double prog = Math.clamp((now - cast.nextAt) / (double) Math.max(1L, cast.endAt - cast.nextAt), 0.0, 1.0);
                    Vec3 p = cast.from.lerp(cast.to, prog);
                    e.setPos(p.x, p.y, p.z);
                    e.setDeltaMovement(Vec3.ZERO);
                    if (now >= cast.endAt) {
                        cast.subPhase = 2;
                        long slamWarn = timing.windup(ForgeTowerSwordMasters.AGANZO_S2_SLAM_WARN_MS);
                        cast.nextAt = now;
                        cast.endAt = now + slamWarn;
                        Vec3 land = groundAt(s.cell, cast.to);
                        cast.anchor = land;
                        e.play("heavy_slam_windup", slamWarn);
                        sendHazard(s.instanceId, "aganzo_s2_slam/" + (++s.sequence), "CIRCLE", t,
                            land, land.add(0, 0, 1), ForgeTowerSwordMasters.AGANZO_S2_SLAM_RADIUS, 0.0, 0.0, 0.0,
                            now, now + slamWarn, now + slamWarn + 300L, 0L,
                            ForgeTowerSwordMasters.COEFF_AGANZO_S2_SLAM, 0.0, Member.AGANZO.themeColor);
                    }
                    return;
                }
                if (cast.subPhase == 2) {
                    double prog = Math.clamp((now - cast.nextAt) / (double) Math.max(1L, cast.endAt - cast.nextAt), 0.0, 1.0);
                    double arc = Math.sin(prog * Math.PI) * 3.8;
                    e.setPos(cast.anchor.x, cast.anchor.y + arc, cast.anchor.z);
                    e.setDeltaMovement(Vec3.ZERO);
                    if (now >= cast.endAt) {
                        landToGround(e, s.cell, cast.anchor);
                        finishCast(slot, timing.recovery(ForgeTowerSwordMasters.AGANZO_S2_RECOVERY_MS), now);
                    }
                }
            }
            case AGANZO_WHIRL_SLASH -> {
                if (cast.subPhase == 0) {
                    if (now >= cast.nextAt) cast.subPhase = 1;
                    return;
                }
                if (cast.subPhase == 1) {
                    ServerPlayer nearest = nearestFighter(e, fighters);
                    if (nearest != null) {
                        e.getNavigation().moveTo(nearest, 0.82);
                    }
                    if (now - cast.lastMoveSyncAt >= ForgeTowerSwordMasters.SYNC_MIN_INTERVAL_MS) {
                        cast.lastMoveSyncAt = now;
                        Vec3 pos = groundAt(s.cell, e.position());
                        JsonObject move = new JsonObject();
                        move.addProperty("instance", s.instanceId);
                        move.addProperty("id", cast.hazardId);
                        move.addProperty("moveOnly", true);
                        move.addProperty("moveDuration", ForgeTowerSwordMasters.SYNC_MIN_INTERVAL_MS);
                        move.addProperty("x", pos.x);
                        move.addProperty("y", pos.y);
                        move.addProperty("z", pos.z);
                        owner.broadcast(s.instanceId, "forgeBossHazard", move);
                    }
                    if (now >= cast.endAt) {
                        e.getNavigation().stop();
                        cast.subPhase = 2;
                        long finWarn = timing.windup(ForgeTowerSwordMasters.AGANZO_S3_FINISHER_WARN_MS);
                        cast.endAt = now + finWarn + 260L;
                        Vec3 pos = groundAt(s.cell, e.position());
                        e.play("upper_slash", finWarn + 260L);
                        sendHazard(s.instanceId, "aganzo_s3_fin/" + (++s.sequence), "CIRCLE", t,
                            pos, pos.add(0, 0, 1), ForgeTowerSwordMasters.AGANZO_S3_FINISHER_RADIUS, 0.0, 0.0, 0.0,
                            now, now + finWarn, now + finWarn + 260L, 0L,
                            ForgeTowerSwordMasters.COEFF_AGANZO_S3_FINISHER, 0.0, 0xb8d8ff);
                    }
                    return;
                }
                if (now >= cast.endAt) {
                    finishCast(slot, timing.recovery(ForgeTowerSwordMasters.AGANZO_S3_RECOVERY_MS), now);
                }
            }
            case AGANZO_AWAKENING -> {
                if (cast.subPhase == 0 && now >= cast.nextAt) {
                    cast.subPhase = 1;
                    // 蓄力结束恢复正常承伤
                    t.damageTakenMultiplier = 1.0;
                    try { commitTarget(d, e, t); } catch (Exception ignored) {}
                    long gap = timing.windup(ForgeTowerSwordMasters.AGANZO_A_CROSS_GAP_MS);
                    cast.nextAt = now + gap;
                    e.play("greatsword_cleave", gap);
                    emitCrossLines(s, t, cast.anchor, Math.PI * 0.25, ForgeTowerSwordMasters.AGANZO_A_CROSS_HALF_LEN,
                        ForgeTowerSwordMasters.AGANZO_A_CROSS_WIDTH, now, now + gap, now + gap + 420L,
                        ForgeTowerSwordMasters.COEFF_AGANZO_A_CROSS, 0xa8d4ff, "aganzo_awk_x");
                    return;
                }
                if (cast.subPhase == 1 && now >= cast.nextAt) {
                    cast.subPhase = 2;
                    long finWarn = timing.windup(ForgeTowerSwordMasters.AGANZO_A_FINISHER_WARN_MS);
                    long travel = timing.active(ForgeTowerSwordMasters.AGANZO_A_FINISHER_TRAVEL_MS);
                    cast.endAt = now + finWarn + 350L;
                    ServerPlayer aim = nearestFighter(e, fighters);
                    Vec3 finDir = aim != null ? horizontalDirection(cast.anchor, aim.position()) : cast.direction;
                    facePosition(e, cast.anchor.add(finDir.scale(8.0)));
                    e.play("awakening_finisher", finWarn + 350L);
                    sendHazard(s.instanceId, "aganzo_awk_fin/" + (++s.sequence), "SLASH_WAVE", t,
                        cast.anchor, cast.anchor.add(finDir.scale(ForgeTowerSwordMasters.AGANZO_A_FINISHER_RANGE)),
                        ForgeTowerSwordMasters.AGANZO_A_FINISHER_RANGE, ForgeTowerSwordMasters.AGANZO_A_FINISHER_WIDTH, 0.0, 0.0,
                        now, now + finWarn, now + finWarn + travel, 0L,
                        ForgeTowerSwordMasters.COEFF_AGANZO_A_FINISHER, 0.0, 0xd8ecff);
                    return;
                }
                if (cast.subPhase == 2 && now >= cast.endAt) {
                    e.play("exhaust_stagger", ForgeTowerSwordMasters.AGANZO_A_RECOVERY_MS);
                    finishCast(slot, timing.recovery(ForgeTowerSwordMasters.AGANZO_A_RECOVERY_MS), now);
                }
            }
            case SIRAN_IAI_DRAW -> {
                if (cast.subPhase == 0 && now >= cast.nextAt) {
                    cast.subPhase = 1;
                    e.play("iai_slash", 260L);
                }
                if (now >= cast.endAt) {
                    finishCast(slot, timing.recovery(ForgeTowerSwordMasters.SIRAN_S1_RECOVERY_MS), now);
                }
            }
            case SIRAN_TRIPLE_FLASH -> {
                if (cast.subPhase == 0 && now >= cast.nextAt) {
                    cast.subPhase = 1;
                    e.setPos(cast.to.x, cast.to.y, cast.to.z);
                    e.setDeltaMovement(Vec3.ZERO);
                    facePosition(e, cast.from);
                    e.play("iai_slash", 220L);
                }
                if (cast.subPhase == 1 && now >= cast.endAt) {
                    cast.step++;
                    if (cast.step >= cast.totalSteps) {
                        finishCast(slot, timing.recovery(ForgeTowerSwordMasters.SIRAN_S2_RECOVERY_MS), now);
                        return;
                    }
                    ServerPlayer aim = nearestFighter(e, fighters);
                    Vec3 aimPos = aim != null ? aim.position() : AwakeningTowerLayout.guard(s.cell);
                    scheduleSiranFlashStep(s, e, t, cast, aimPos, timing, now);
                }
            }
            case SIRAN_ECHO_CROSS -> {
                if (now >= cast.endAt) {
                    finishCast(slot, timing.recovery(ForgeTowerSwordMasters.SIRAN_S3_RECOVERY_MS), now);
                }
            }
            case SIRAN_AWAKENING -> {
                if (cast.subPhase == 0 && now >= cast.nextAt) {
                    cast.subPhase = 1;
                    long finWarn = timing.windup(ForgeTowerSwordMasters.SIRAN_A_FINISHER_WARN_MS);
                    cast.nextAt = now + finWarn;
                    cast.endAt = now + finWarn + 350L;
                    ServerPlayer aim = nearestFighter(e, fighters);
                    Vec3 cur = groundAt(s.cell, e.position());
                    Vec3 finDir = aim != null ? horizontalDirection(cur, aim.position()) : horizontalDirection(cur, cast.anchor);
                    Vec3 end = clampLineEnd(s.cell, cur, finDir, ForgeTowerSwordMasters.SIRAN_A_FINISHER_RANGE);
                    cast.from = cur;
                    cast.to = end;
                    facePosition(e, end);
                    e.play("iai_sheathe", finWarn);
                    sendHazard(s.instanceId, "siran_awk_fin/" + (++s.sequence), "INSTANT_LINE", t,
                        cur, end, cur.distanceTo(end), ForgeTowerSwordMasters.SIRAN_A_FINISHER_WIDTH, 0.0, 0.0,
                        now, now + finWarn, now + finWarn + 320L, 0L,
                        ForgeTowerSwordMasters.COEFF_SIRAN_A_FINISHER, 0.0, 0xffffff);
                    return;
                }
                if (cast.subPhase == 1 && now >= cast.nextAt) {
                    cast.subPhase = 2;
                    e.setPos(cast.to.x, cast.to.y, cast.to.z);
                    e.setDeltaMovement(Vec3.ZERO);
                    e.play("iai_slash", 320L);
                }
                if (cast.subPhase == 2 && now >= cast.endAt) {
                    e.play("exhaust_stagger", ForgeTowerSwordMasters.SIRAN_A_RECOVERY_MS);
                    finishCast(slot, timing.recovery(ForgeTowerSwordMasters.SIRAN_A_RECOVERY_MS), now);
                }
            }
        }
    }

    private void fireBaanAwakeningWave(State s, DungeonEnemy e, Target t, MemberCast cast,
                                       Target.ActionTiming timing, long now) {
        long warn = timing.windup(ForgeTowerSwordMasters.BAAN_A_WAVE_WARN_MS);
        long travel = timing.active(ForgeTowerSwordMasters.BAAN_A_WAVE_TRAVEL_MS);
        long interval = timing.duration(ForgeTowerSwordMasters.BAAN_A_WAVE_INTERVAL_MS);
        double gapAngle = cast.baseAngle + cast.step * Math.toRadians(38.0);
        double[] angles = ForgeTowerSwordMasters.hurricaneWaveAnglesRad(ForgeTowerSwordMasters.BAAN_A_BLADES_PER_WAVE, gapAngle);

        // 巴恩瞬移至外圈切线位摆出疾风架势
        Vec3 pos = groundAt(s.cell, cast.anchor.add(Math.cos(gapAngle) * ForgeTowerSwordMasters.BAAN_A_RING_RADIUS,
            0, Math.sin(gapAngle) * ForgeTowerSwordMasters.BAAN_A_RING_RADIUS));
        e.setPos(pos.x, pos.y, pos.z);
        facePosition(e, cast.anchor);
        e.play("blade_fan", warn);

        for (double a : angles) {
            Vec3 from = cast.anchor.add(Math.cos(a) * ForgeTowerSwordMasters.BAAN_A_RING_RADIUS, 0, Math.sin(a) * ForgeTowerSwordMasters.BAAN_A_RING_RADIUS);
            Vec3 to = cast.anchor.subtract(Math.cos(a) * 3.5, 0, Math.sin(a) * 3.5);
            sendHazard(s.instanceId, "baan_awk_w" + cast.step + "/" + (++s.sequence), "SLASH_WAVE", t,
                from, to, from.distanceTo(to), ForgeTowerSwordMasters.BAAN_A_BLADE_WIDTH, 0.0, 0.0,
                now, now + warn, now + warn + travel, 0L,
                ForgeTowerSwordMasters.COEFF_BAAN_A_BLADE, 0.0, Member.BAAN.themeColor);
        }
        cast.step++;
        cast.nextAt = now + interval;
    }

    private void scheduleSiranFlashStep(State s, DungeonEnemy e, Target t, MemberCast cast,
                                        Vec3 aimPos, Target.ActionTiming timing, long now) {
        Vec3 start = groundAt(s.cell, e.position());
        Vec3 dir = horizontalDirection(start, aimPos);
        if (cast.step > 0) {
            dir = rotateY(dir, Math.toRadians(cast.step % 2 == 1 ? 14.0 : -14.0));
        }
        Vec3 end = clampLineEnd(s.cell, start, dir, ForgeTowerSwordMasters.SIRAN_S2_DIST);
        long warn = timing.windup(cast.step == 0 ? ForgeTowerSwordMasters.SIRAN_S2_WINDUP_MS : ForgeTowerSwordMasters.SIRAN_S2_STEP_WARN_MS);
        long flash = timing.active(ForgeTowerSwordMasters.SIRAN_S2_STEP_FLASH_MS);
        cast.from = start;
        cast.to = end;
        cast.nextAt = now + warn;
        cast.endAt = now + warn + flash;
        cast.subPhase = 0;
        facePosition(e, end);
        e.play("iai_sheathe", warn);
        sendHazard(s.instanceId, "siran_s2_" + cast.step + "/" + (++s.sequence), "INSTANT_LINE", t,
            start, end, start.distanceTo(end), ForgeTowerSwordMasters.SIRAN_S2_WIDTH, 0.0, 0.0,
            now, now + warn, now + warn + 260L, 0L,
            ForgeTowerSwordMasters.COEFF_SIRAN_S2, 0.0, Member.SIRAN.themeColor);
    }

    private void emitCrossLines(State s, Target t, Vec3 center, double angleOffsetRad, double halfLen, double width,
                                long startAt, long warnUntil, long endAt, double coeff, int color, String prefix) {
        for (int i = 0; i < 2; i++) {
            double a = angleOffsetRad + i * Math.PI * 0.5;
            Vec3 d = new Vec3(Math.cos(a), 0, Math.sin(a));
            Vec3 from = center.subtract(d.scale(halfLen));
            Vec3 to = center.add(d.scale(halfLen));
            sendHazard(s.instanceId, prefix + "_" + i + "/" + (++s.sequence), "INSTANT_LINE", t,
                from, to, halfLen * 2.0, width, 0.0, 0.0, startAt, warnUntil, endAt, 0L, coeff, 0.0, color);
        }
    }

    private void finishCast(MemberSlot slot, long recoveryMs, long now) {
        slot.activeCast = null;
        slot.busyUntil = now + recoveryMs;
    }

    // ==================== 双人合击 & P3 四剑合璧 ====================

    private boolean tryStartCombo(Dungeon d, State s, List<ServerPlayer> fighters, long now) throws Exception {
        if (s.activeCombo != null) return false;
        Vec3 center = AwakeningTowerLayout.guard(s.cell);

        if (s.phase == Phase.P1_BWANGA_BAAN) {
            MemberSlot bw = s.slot(Member.BWANGA);
            MemberSlot ba = s.slot(Member.BAAN);
            if (!bw.activeOnField() || !ba.activeOnField() || bw.castingMain() || ba.castingMain()) return false;
            if (now < s.duo1CooldownUntil) return false;
            DungeonEnemy e1 = resolveEnemy(bw), e2 = resolveEnemy(ba);
            if (e1 == null || e2 == null) return false;
            boolean trigger = !s.duo1TriggeredOnce
                ? (now - s.phaseStartAt >= ForgeTowerSwordMasters.DUO_COMBO_TRIGGER_DELAY_MS
                    || targets.target(e1).life.q <= ForgeTowerSwordMasters.DUO_COMBO_TRIGGER_HP
                    || targets.target(e2).life.q <= ForgeTowerSwordMasters.DUO_COMBO_TRIGGER_HP)
                : true;
            if (!trigger) return false;
            s.duo1TriggeredOnce = true;
            s.duo1CooldownUntil = now + ForgeTowerSwordMasters.DUO_COMBO_CD_MS;
            s.activeCombo = new ComboCast(ComboKind.DUO_P1_BWANGA_BAAN, List.of(Member.BWANGA, Member.BAAN), center, now);
            broadcastBanner(s.instanceId, "双圣合击 · 崩山飓风协奏！", 4_200L, 0xffd97a);
            return true;
        }

        if (s.phase == Phase.P2_AGANZO_SIRAN) {
            MemberSlot ag = s.slot(Member.AGANZO);
            MemberSlot si = s.slot(Member.SIRAN);
            if (!ag.activeOnField() || !si.activeOnField() || ag.castingMain() || si.castingMain()) return false;
            if (now < s.duo2CooldownUntil) return false;
            DungeonEnemy e1 = resolveEnemy(ag), e2 = resolveEnemy(si);
            if (e1 == null || e2 == null) return false;
            boolean trigger = !s.duo2TriggeredOnce
                ? (now - s.phaseStartAt >= ForgeTowerSwordMasters.DUO_COMBO_TRIGGER_DELAY_MS
                    || targets.target(e1).life.q <= ForgeTowerSwordMasters.DUO_COMBO_TRIGGER_HP
                    || targets.target(e2).life.q <= ForgeTowerSwordMasters.DUO_COMBO_TRIGGER_HP)
                : true;
            if (!trigger) return false;
            s.duo2TriggeredOnce = true;
            s.duo2CooldownUntil = now + ForgeTowerSwordMasters.DUO_COMBO_CD_MS;
            s.activeCombo = new ComboCast(ComboKind.DUO_P2_AGANZO_SIRAN, List.of(Member.AGANZO, Member.SIRAN), center, now);
            broadcastBanner(s.instanceId, "双圣合击 · 苍岚·刹那交锋！", 4_200L, 0xd8ecff);
            return true;
        }

        if (s.phase == Phase.P3_ALL_FOUR) {
            if (now < s.quadNextAt) return false;
            List<Member> living = activeQuadStages(s);
            if (living.isEmpty()) return false;
            // 等待场上无正在进行的个人觉醒，避免视觉重叠
            if (!canStartAwakening(s)) return false;
            // 取消其他常规动作，让所有存活剑圣入位
            for (Member m : living) {
                MemberSlot sl = s.slot(m);
                sl.activeCast = null;
                DungeonEnemy e = resolveEnemy(sl);
                if (e != null) {
                    Target t = targets.target(e);
                    if (Math.abs(t.damageTakenMultiplier - 1.0) > 1e-6) {
                        t.damageTakenMultiplier = 1.0;
                        commitTarget(d, e, t);
                    }
                    Vec3 pos = AwakeningTowerLayout.ground(owner.level(), s.cell,
                        ForgeTowerSwordMasters.quadComboPosition(s.cell, m), center);
                    e.getNavigation().stop();
                    e.setNoGravity(false);
                    e.setPos(pos.x, pos.y, pos.z);
                    e.setDeltaMovement(Vec3.ZERO);
                    facePosition(e, center);
                }
            }
            s.quadTriggeredOnce = true;
            s.quadNextAt = now + ForgeTowerSwordMasters.QUAD_COMBO_CD_MS;
            s.activeCombo = new ComboCast(ComboKind.QUAD_P3_ALL_FOUR, living, center, now);
            broadcastBanner(s.instanceId, "终极大招 · 四剑合璧·悲鸣绝响！", 6_500L, 0xffe484);
            return true;
        }
        return false;
    }

    private void tickCombo(Dungeon d, State s, List<ServerPlayer> fighters, long now) {
        ComboCast combo = s.activeCombo;
        if (combo == null || now < combo.nextAt) return;
        Vec3 center = combo.center;

        switch (combo.kind) {
            case DUO_P1_BWANGA_BAAN -> {
                DungeonEnemy bwE = resolveEnemy(s.slot(Member.BWANGA));
                DungeonEnemy baE = resolveEnemy(s.slot(Member.BAAN));
                if (bwE == null || baE == null) {
                    s.activeCombo = null;
                    return;
                }
                Target bwT = targets.target(bwE);
                Target baT = targets.target(baE);
                if (combo.stage == 0) {
                    // 第一段：布万加跃中砸出内圈冲击波迫使外撤
                    Vec3 mid = groundAt(s.cell, center);
                    bwE.setPos(mid.x, mid.y, mid.z);
                    bwE.play("heavy_slam_windup", ForgeTowerSwordMasters.DUO1_STAGE1_WARN_MS);
                    sendHazard(s.instanceId, "duo1_s1/" + (++s.sequence), "CIRCLE", bwT,
                        mid, mid.add(0, 0, 1), ForgeTowerSwordMasters.DUO1_STAGE1_RADIUS, 0.0, 0.0, 0.0,
                        now, now + ForgeTowerSwordMasters.DUO1_STAGE1_WARN_MS, now + ForgeTowerSwordMasters.DUO1_STAGE1_WARN_MS + 320L, 0L,
                        ForgeTowerSwordMasters.COEFF_DUO1_STAGE1, 4.0, Member.BWANGA.themeColor);
                    combo.stage = 1;
                    combo.nextAt = now + ForgeTowerSwordMasters.DUO1_STAGE1_WARN_MS - 150L;
                } else if (combo.stage == 1) {
                    // 第二段：巴恩切出 4 道斜向飓风斩线（留出外圈四角安全点）
                    baE.play("blade_fan", ForgeTowerSwordMasters.DUO1_STAGE2_WARN_MS);
                    double[][] lines = {
                        {-16, -6, 16, 10}, {-16, 10, 16, -6},
                        {-10, -16, 6, 16}, {10, -16, -6, 16}
                    };
                    for (int i = 0; i < lines.length; i++) {
                        Vec3 a = center.add(lines[i][0], 0, lines[i][1]);
                        Vec3 b = center.add(lines[i][2], 0, lines[i][3]);
                        sendHazard(s.instanceId, "duo1_s2_" + i + "/" + (++s.sequence), "INSTANT_LINE", baT,
                            a, b, a.distanceTo(b), ForgeTowerSwordMasters.DUO1_STAGE2_WIDTH, 0.0, 0.0,
                            now, now + ForgeTowerSwordMasters.DUO1_STAGE2_WARN_MS, now + ForgeTowerSwordMasters.DUO1_STAGE2_WARN_MS + 300L, 0L,
                            ForgeTowerSwordMasters.COEFF_DUO1_STAGE2, 0.0, Member.BAAN.themeColor);
                    }
                    combo.stage = 2;
                    combo.nextAt = now + ForgeTowerSwordMasters.DUO1_STAGE2_WARN_MS + 250L;
                } else if (combo.stage == 2) {
                    // 第三段：布万加由外向内收缩回荡震环（内圈半径 6 格安全）
                    bwE.play("heavy_slam_hit", 500L);
                    sendHazard(s.instanceId, "duo1_s3/" + (++s.sequence), "RING_WAVE", bwT,
                        center, center.add(0, 0, 1),
                        ForgeTowerSwordMasters.DUO1_STAGE3_START_RADIUS, ForgeTowerSwordMasters.DUO1_STAGE3_WIDTH,
                        ForgeTowerSwordMasters.DUO1_STAGE3_END_RADIUS, 0.0,
                        now, now + ForgeTowerSwordMasters.DUO1_STAGE3_WARN_MS,
                        now + ForgeTowerSwordMasters.DUO1_STAGE3_WARN_MS + ForgeTowerSwordMasters.DUO1_STAGE3_DURATION_MS, 0L,
                        ForgeTowerSwordMasters.COEFF_DUO1_STAGE3, 0.0, 0xffd97a);
                    combo.stage = 3;
                    combo.nextAt = now + ForgeTowerSwordMasters.DUO1_STAGE3_WARN_MS + ForgeTowerSwordMasters.DUO1_STAGE3_DURATION_MS;
                } else {
                    finishComboWithExhaust(s, combo, now);
                }
            }
            case DUO_P2_AGANZO_SIRAN -> {
                DungeonEnemy agE = resolveEnemy(s.slot(Member.AGANZO));
                DungeonEnemy siE = resolveEnemy(s.slot(Member.SIRAN));
                if (agE == null || siE == null) {
                    s.activeCombo = null;
                    return;
                }
                Target agT = targets.target(agE);
                Target siT = targets.target(siE);
                if (combo.stage == 0) {
                    // 第一段：阿甘左 3 道平行巨剑竖走廊（x = -12, 0, +12，切出 x=±6 两条安全通道）
                    Vec3 north = groundAt(s.cell, center.add(0, 0, -15.0));
                    agE.setPos(north.x, north.y, north.z);
                    facePosition(agE, center);
                    agE.play("greatsword_cleave", ForgeTowerSwordMasters.DUO2_STAGE1_WARN_MS);
                    for (double ox : new double[]{-12.0, 0.0, 12.0}) {
                        Vec3 a = center.add(ox, 0, -19.0);
                        Vec3 b = center.add(ox, 0, 19.0);
                        sendHazard(s.instanceId, "duo2_s1/" + (++s.sequence), "INSTANT_LINE", agT,
                            a, b, 38.0, ForgeTowerSwordMasters.DUO2_STAGE1_WIDTH, 0.0, 0.0,
                            now, now + ForgeTowerSwordMasters.DUO2_STAGE1_WARN_MS, now + ForgeTowerSwordMasters.DUO2_STAGE1_WARN_MS + 320L, 0L,
                            ForgeTowerSwordMasters.COEFF_DUO2_STAGE1, 0.0, Member.AGANZO.themeColor);
                    }
                    combo.stage = 1;
                    combo.nextAt = now + ForgeTowerSwordMasters.DUO2_STAGE1_WARN_MS + 180L;
                } else if (combo.stage == 1) {
                    // 第二段：西岚 3 道横向居合斩线（z = -11, 0, +11，留出清晰安全方格）
                    siE.play("iai_sheathe", ForgeTowerSwordMasters.DUO2_STAGE2_WARN_MS);
                    for (double oz : new double[]{-11.0, 0.0, 11.0}) {
                        Vec3 a = center.add(-19.0, 0, oz);
                        Vec3 b = center.add(19.0, 0, oz);
                        sendHazard(s.instanceId, "duo2_s2/" + (++s.sequence), "INSTANT_LINE", siT,
                            a, b, 38.0, ForgeTowerSwordMasters.DUO2_STAGE2_WIDTH, 0.0, 0.0,
                            now, now + ForgeTowerSwordMasters.DUO2_STAGE2_WARN_MS, now + ForgeTowerSwordMasters.DUO2_STAGE2_WARN_MS + 300L, 0L,
                            ForgeTowerSwordMasters.COEFF_DUO2_STAGE2, 0.0, Member.SIRAN.themeColor);
                    }
                    combo.stage = 2;
                    combo.nextAt = now + ForgeTowerSwordMasters.DUO2_STAGE2_WARN_MS + 220L;
                } else if (combo.stage == 2) {
                    // 第三段：阿甘左 + 西岚对角合斩「×」字中心爆发
                    agE.play("awakening_finisher", ForgeTowerSwordMasters.DUO2_STAGE3_WARN_MS);
                    siE.play("iai_slash", ForgeTowerSwordMasters.DUO2_STAGE3_WARN_MS);
                    emitCrossLines(s, agT, center, Math.PI * 0.25, 20.0, ForgeTowerSwordMasters.DUO2_STAGE3_WIDTH,
                        now, now + ForgeTowerSwordMasters.DUO2_STAGE3_WARN_MS, now + ForgeTowerSwordMasters.DUO2_STAGE3_WARN_MS + 380L,
                        ForgeTowerSwordMasters.COEFF_DUO2_STAGE3, 0xd8ecff, "duo2_s3_x");
                    combo.stage = 3;
                    combo.nextAt = now + ForgeTowerSwordMasters.DUO2_STAGE3_WARN_MS + 380L;
                } else {
                    finishComboWithExhaust(s, combo, now);
                }
            }
            case QUAD_P3_ALL_FOUR -> {
                if (combo.stage >= combo.actors.size()) {
                    finishComboWithExhaust(s, combo, now);
                    return;
                }
                Member actor = combo.actors.get(combo.stage);
                MemberSlot slot = s.slot(actor);
                DungeonEnemy e = slot.activeOnField() ? resolveEnemy(slot) : null;
                if (e == null) {
                    // 若该剑圣刚阵亡则直接跳过该段
                    combo.stage++;
                    combo.nextAt = now + 100L;
                    return;
                }
                Target t = targets.target(e);
                switch (actor) {
                    case BWANGA -> {
                        e.play("heavy_slam_windup", ForgeTowerSwordMasters.QUAD_STAGE1_WARN_MS);
                        sendHazard(s.instanceId, "quad_bwanga/" + (++s.sequence), "RING_WAVE", t,
                            center, center.add(0, 0, 1),
                            ForgeTowerSwordMasters.QUAD_STAGE1_START_R, 3.2, ForgeTowerSwordMasters.QUAD_STAGE1_END_R, 0.0,
                            now, now + ForgeTowerSwordMasters.QUAD_STAGE1_WARN_MS,
                            now + ForgeTowerSwordMasters.QUAD_STAGE1_WARN_MS + ForgeTowerSwordMasters.QUAD_STAGE1_RING_MS, 0L,
                            ForgeTowerSwordMasters.COEFF_QUAD_STAGE1, 0.0, Member.BWANGA.themeColor);
                        combo.nextAt = now + ForgeTowerSwordMasters.QUAD_STAGE1_WARN_MS + ForgeTowerSwordMasters.QUAD_STAGE1_RING_MS - 150L;
                    }
                    case BAAN -> {
                        e.play("blade_fan", ForgeTowerSwordMasters.QUAD_STAGE2_WARN_MS);
                        // 4 条平行双侧斜线，明确留出两条对角中央通道
                        for (double offset : new double[]{-10.0, -4.5, 4.5, 10.0}) {
                            Vec3 dir = new Vec3(1, 0, 1).normalize();
                            Vec3 perp = new Vec3(-dir.z, 0, dir.x);
                            Vec3 mid = center.add(perp.scale(offset));
                            sendHazard(s.instanceId, "quad_baan/" + (++s.sequence), "INSTANT_LINE", t,
                                mid.subtract(dir.scale(20.0)), mid.add(dir.scale(20.0)), 40.0,
                                ForgeTowerSwordMasters.QUAD_STAGE2_WIDTH, 0.0, 0.0,
                                now, now + ForgeTowerSwordMasters.QUAD_STAGE2_WARN_MS, now + ForgeTowerSwordMasters.QUAD_STAGE2_WARN_MS + 300L, 0L,
                                ForgeTowerSwordMasters.COEFF_QUAD_STAGE2, 0.0, Member.BAAN.themeColor);
                        }
                        combo.nextAt = now + ForgeTowerSwordMasters.QUAD_STAGE2_WARN_MS + 220L;
                    }
                    case AGANZO -> {
                        e.play("greatsword_cleave", ForgeTowerSwordMasters.QUAD_STAGE3_WARN_MS);
                        emitCrossLines(s, t, center, 0.0, 21.0, ForgeTowerSwordMasters.QUAD_STAGE3_WIDTH,
                            now, now + ForgeTowerSwordMasters.QUAD_STAGE3_WARN_MS, now + ForgeTowerSwordMasters.QUAD_STAGE3_WARN_MS + 360L,
                            ForgeTowerSwordMasters.COEFF_QUAD_STAGE3, Member.AGANZO.themeColor, "quad_aganzo");
                        combo.nextAt = now + ForgeTowerSwordMasters.QUAD_STAGE3_WARN_MS + 240L;
                    }
                    case SIRAN -> {
                        e.play("siran_awakening", ForgeTowerSwordMasters.QUAD_STAGE4_WARN_MS);
                        // 在四象限角落中画斜向居合终结阵，确保四角留有安全区
                        emitCrossLines(s, t, center, Math.PI * 0.25, 22.0, ForgeTowerSwordMasters.QUAD_STAGE4_WIDTH,
                            now, now + ForgeTowerSwordMasters.QUAD_STAGE4_WARN_MS, now + ForgeTowerSwordMasters.QUAD_STAGE4_WARN_MS + 420L,
                            ForgeTowerSwordMasters.COEFF_QUAD_STAGE4, 0xffffff, "quad_siran");
                        combo.nextAt = now + ForgeTowerSwordMasters.QUAD_STAGE4_WARN_MS + 420L;
                    }
                }
                combo.stage++;
            }
        }
    }

    private void finishComboWithExhaust(State s, ComboCast combo, long now) {
        if (!combo.exhaustSent) {
            combo.exhaustSent = true;
            Vec3 center = combo.center;
            int idx = 0;
            for (Member m : combo.actors) {
                MemberSlot sl = s.slot(m);
                if (!sl.activeOnField()) continue;
                DungeonEnemy e = resolveEnemy(sl);
                if (e != null) {
                    Vec3 spot = groundAt(s.cell, center.add((idx - (combo.actors.size() - 1) * 0.5) * 3.2, 0, 0));
                    e.setPos(spot.x, spot.y, spot.z);
                    e.setDeltaMovement(Vec3.ZERO);
                    e.play("exhaust_stagger", ForgeTowerSwordMasters.COMBO_EXHAUST_MS);
                }
                sl.busyUntil = now + ForgeTowerSwordMasters.COMBO_EXHAUST_MS;
                idx++;
            }
            broadcastBanner(s.instanceId, "收剑破绽 · 反打窗口！", ForgeTowerSwordMasters.COMBO_EXHAUST_MS, 0x9effb2);
            combo.nextAt = now + ForgeTowerSwordMasters.COMBO_EXHAUST_MS;
            return;
        }
        s.activeCombo = null;
    }

    // ==================== 辅助方法与生命周期清理 ====================

    private void sendHazard(String instanceId, String id, String type, Target boss,
                            Vec3 from, Vec3 to, double radius, double width, double auxRadius, double slashAngle,
                            long startAt, long warnUntil, long endAt, long tickMs,
                            double coefficient, double knockback, int color) {
        JsonObject o = new JsonObject();
        o.addProperty("instance", instanceId);
        o.addProperty("id", id);
        o.addProperty("type", type);
        o.add("boss", JSON.toJsonTree(boss));
        o.addProperty("x", from.x);
        o.addProperty("y", from.y);
        o.addProperty("z", from.z);
        o.addProperty("tx", to.x);
        o.addProperty("ty", to.y);
        o.addProperty("tz", to.z);
        o.addProperty("radius", radius);
        o.addProperty("width", width);
        o.addProperty("auxRadius", auxRadius);
        o.addProperty("slashAngle", slashAngle);
        o.addProperty("startAt", startAt);
        o.addProperty("warnUntil", warnUntil);
        o.addProperty("endAt", endAt);
        o.addProperty("tickMs", tickMs);
        o.addProperty("coefficient", coefficient);
        o.addProperty("knockback", knockback);
        o.addProperty("magic", false);
        o.addProperty("element", ForgeTowerSwordMasters.ELEMENT_NONE);
        o.addProperty("color", color);
        owner.broadcast(instanceId, "forgeBossHazard", o);
    }

    private void broadcastBanner(String instanceId, String title, long durationMs, int color) {
        broadcastEvent(instanceId, Map.of(
            "kind", "BANNER",
            "instance", instanceId,
            "title", title,
            "durationMs", durationMs,
            "color", color
        ));
    }

    private void broadcastEvent(String instanceId, Map<String, Object> payload) {
        owner.broadcast(instanceId, "forgeBossEvent", payload);
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

    private DungeonEnemy resolveEnemy(MemberSlot slot) {
        if (slot == null || slot.entityId == null) return null;
        Entity raw = owner.level().getEntity(slot.entityId);
        return raw instanceof DungeonEnemy de && de.isAlive() ? de : null;
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

    private ServerPlayer nearestFighter(DungeonEnemy e, List<ServerPlayer> fighters) {
        ServerPlayer best = null;
        double bestDist = Double.POSITIVE_INFINITY;
        for (ServerPlayer p : fighters) {
            double d = p.distanceToSqr(e);
            if (d < bestDist) {
                bestDist = d;
                best = p;
            }
        }
        return best;
    }

    private Vec3 groundAt(int cell, Vec3 point) {
        return AwakeningTowerLayout.ground(owner.level(), cell, point, AwakeningTowerLayout.guard(cell));
    }

    private Vec3 clampLineEnd(int cell, Vec3 start, Vec3 dir, double maxDist) {
        ServerLevel world = owner.level();
        Vec3 best = start;
        int steps = Math.max(4, (int) Math.ceil(maxDist * 1.5));
        for (int i = 1; i <= steps; i++) {
            Vec3 candidate = start.add(dir.scale(maxDist * i / steps));
            if (!AwakeningTowerLayout.containsBattle(cell, candidate)) break;
            Vec3 ground = AwakeningTowerLayout.ground(world, cell, candidate, null);
            if (ground == null) break;
            best = ground;
        }
        return best;
    }

    private void landToGround(DungeonEnemy e, int cell, Vec3 around) {
        e.setNoGravity(false);
        Vec3 g = groundAt(cell, around);
        e.setPos(g.x, g.y, g.z);
        e.setDeltaMovement(Vec3.ZERO);
        e.resetFallDistance();
    }

    private static Vec3 horizontalDirection(Vec3 from, Vec3 to) {
        Vec3 d = new Vec3(to.x - from.x, 0, to.z - from.z);
        return d.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : d.normalize();
    }

    private static Vec3 rotateY(Vec3 v, double rad) {
        double c = Math.cos(rad), s = Math.sin(rad);
        return new Vec3(v.x * c - v.z * s, 0, v.x * s + v.z * c).normalize();
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

    void died(UUID bossId) {
        for (State s : states.values()) {
            for (Member m : Member.values()) {
                MemberSlot slot = s.slot(m);
                if (bossId.equals(slot.entityId)) {
                    slot.dead = true;
                    slot.activeCast = null;
                    int remain = livingCount(s);
                    if (remain > 0) {
                        broadcastBanner(s.instanceId, m.displayName + " 已被击败！（剩余 " + remain + " 位剑圣）", 2_200L, m.themeColor);
                    } else {
                        broadcastBanner(s.instanceId, "阿拉德四剑圣 · 试炼通关！", 3_200L, 0xffe484);
                        cleanupEncounter(s.instanceId);
                    }
                    return;
                }
            }
        }
    }

    void cancel(UUID bossId) {
        for (State s : states.values()) {
            for (Member m : Member.values()) {
                MemberSlot slot = s.slot(m);
                if (bossId.equals(slot.entityId)) {
                    slot.activeCast = null;
                    DungeonEnemy e = resolveEnemy(slot);
                    if (e != null && e.isNoGravity()) {
                        landToGround(e, s.cell, e.position());
                    }
                    return;
                }
            }
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
        Network.send(player, "forgeBossEvent", Map.of(
            "kind", "CLEAR_ENCOUNTER_EFFECTS",
            "instance", d.id.toString()
        ));
    }

    private void cleanupEncounter(String instanceId) {
        broadcastEvent(instanceId, Map.of("kind", "CLEAR_HAZARDS", "instance", instanceId));
        broadcastEvent(instanceId, Map.of("kind", "CLEAR_ENCOUNTER_EFFECTS", "instance", instanceId));
    }
}
