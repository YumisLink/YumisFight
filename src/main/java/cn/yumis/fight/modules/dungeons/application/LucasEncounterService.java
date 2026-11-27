package cn.yumis.fight.modules.dungeons.application;

import cn.yumis.fight.modules.combat.api.CombatContentTypes;
import cn.yumis.fight.modules.dungeons.api.CageEntity;
import cn.yumis.fight.modules.dungeons.api.Dungeon;
import cn.yumis.fight.modules.dungeons.api.DungeonContentTypes;
import cn.yumis.fight.modules.dungeons.api.DungeonEnemy;
import cn.yumis.fight.modules.dungeons.api.DungeonPorts;
import cn.yumis.fight.modules.dungeons.api.Target;
import cn.yumis.fight.modules.dungeons.content.AwakeningTowerLayout;
import cn.yumis.fight.modules.dungeons.content.DragonTower;
import cn.yumis.fight.modules.dungeons.content.DragonTowerLucas;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/**
 * 天空城 · 龙人之塔首领「鲁卡斯」服务端三阶段状态机、可破雷霆枪牢与幻影协同控制器。
 * <p>
 * 设计约定：
 * - 主线 Boss 绝不锁血（不截断血量、不挂转阶段无敌）；跨越 65% / 30% 阈值即刻释放霸体转阶段技与横幅；
 * - 统一挂入 DungeonService.enemyBrains，自动服务龙人之塔、藏宝图塔主题与觉醒塔随机 Boss 层；
 * - 几何预警与命中时间轴单次下发，由客户端本机权威判定与渲染，不触发高频服务端同步。
 */
public final class LucasEncounterService {
    private static final Gson JSON = new GsonBuilder().disableHtmlEscaping().create();

    private enum MotionKind { CHARGE, JUMP_SLAM }

    private static final class Motion {
        final UUID actorId;
        final MotionKind kind;
        final Vec3 from;
        final Vec3 to;
        final long startAt;
        final long endAt;
        final double peakHeight;

        Motion(UUID actorId, MotionKind kind, Vec3 from, Vec3 to, long startAt, long endAt, double peakHeight) {
            this.actorId = actorId;
            this.kind = kind;
            this.from = from;
            this.to = to;
            this.startAt = startAt;
            this.endAt = endAt;
            this.peakHeight = peakHeight;
        }
    }

    private record Scheduled(long at, Runnable task) {}

    private static final class ThunderCage {
        final String id;
        final String instance;
        final UUID bossId;
        final Vec3 anchor;
        final long spawnAt;
        final long expireAt;
        final double radius;
        final double coefficient;
        boolean spawned;
        int hits;
        final List<CageEntity> pieces = new ArrayList<>();
        final Set<String> operations = new HashSet<>();

        ThunderCage(String id, String instance, UUID bossId, Vec3 anchor, long spawnAt, long expireAt, double radius, double coefficient) {
            this.id = id;
            this.instance = instance;
            this.bossId = bossId;
            this.anchor = anchor;
            this.spawnAt = spawnAt;
            this.expireAt = expireAt;
            this.radius = radius;
            this.coefficient = coefficient;
        }
    }

    static final class BossState {
        final UUID bossId;
        final String instance;
        int stage = 1;
        boolean phase2Triggered;
        boolean phase3Triggered;
        UUID cloneId;
        long busyUntil;
        long cloneBusyUntil;
        String lastSkill = "";
        final Map<String, Long> cooldowns = new HashMap<>();
        final List<Scheduled> queue = new ArrayList<>();
        final Random random = new Random();

        BossState(UUID bossId, String instance) {
            this.bossId = bossId;
            this.instance = instance;
        }
    }

    private final DungeonService owner;
    private final DungeonPorts.Players playersAccess;
    private final DungeonPorts.Targets targets;
    private final Map<UUID, BossState> bosses = new HashMap<>();
    private final Map<UUID, Motion> motions = new HashMap<>();
    private final Map<String, ThunderCage> cages = new LinkedHashMap<>();

    LucasEncounterService(DungeonService owner, DungeonPorts.Players playersAccess, DungeonPorts.Targets targets) {
        this.owner = owner;
        this.playersAccess = playersAccess;
        this.targets = targets;
    }

    void brain(Dungeon dungeon, DungeonEnemy boss, List<ServerPlayer> players, long now) {
        Target target = targets.target(boss);
        if (target == null || target.life.dead) return;
        BossState state = bosses.computeIfAbsent(boss.getUUID(), id -> new BossState(id, boss.instance()));

        // 控制打断非霸体动作，但允许已排期的独立雷枪/电牢结算继续
        if (CombatContentTypes.controlled(boss)) {
            stopMotion(boss);
            return;
        }

        // 检查阶段阈值（主线 Boss 绝不锁血，跨阈值立刻释放转阶段技能与横幅）
        double hp = target.life.q;
        if (!state.phase3Triggered && hp <= DragonTowerLucas.phase3Threshold()) {
            state.phase2Triggered = true;
            state.phase3Triggered = true;
            state.stage = 3;
            triggerPhase3Awakening(dungeon, boss, target, state, players, now);
            return;
        }
        if (!state.phase2Triggered && hp <= DragonTowerLucas.phase2Threshold()) {
            state.phase2Triggered = true;
            state.stage = 2;
            triggerPhase2Transition(dungeon, boss, target, state, players, now);
            return;
        }

        ServerPlayer focus = nearestAlive(boss, players);
        if (focus == null) {
            boss.getNavigation().stop();
            return;
        }
        faceToward(boss, focus.position());

        if (now < state.busyUntil) {
            if (!motions.containsKey(boss.getUUID())) boss.getNavigation().stop();
            return;
        }

        double dist = boss.distanceTo(focus);
        if (DragonTowerEncounterService.handles(dungeon) && owner.dragonTower.pursue(dungeon, boss, focus)) {
            // 仍在跨层追击途中，不提前放原地招式
            return;
        }
        if (dist > 4.2) boss.getNavigation().moveTo(focus, 1.08);
        else boss.getNavigation().stop();

        String chosen = selectSkill(state, dist, now);
        if (chosen == null) return;
        state.lastSkill = chosen;
        switch (chosen) {
            case "CROWN_TEMPEST" -> castCrownTempest(dungeon, boss, target, state, focus, now);
            case "JUMP_SLAM" -> castJumpSlam(dungeon, boss, target, state, focus, now);
            case "SKY_SPEAR_RAIN" -> castSkySpearRain(dungeon, boss, target, state, players, now, false);
            case "THUNDER_CAGE" -> castThunderCage(dungeon, boss, target, state, focus, now);
            case "DRAGON_CHARGE" -> castDragonCharge(dungeon, boss, target, state, focus, now);
            case "THUNDER_DISCHARGE" -> castThunderDischarge(boss, target, state, now);
            case "SPEAR_COMBO" -> castSpearCombo(boss, target, state, focus, now, false);
            default -> {}
        }
    }

    void cloneBrain(Dungeon dungeon, DungeonEnemy clone, List<ServerPlayer> players, long now) {
        Target ct = targets.target(clone);
        if (ct == null || ct.life.dead) return;
        if (CombatContentTypes.controlled(clone)) {
            stopMotion(clone);
            return;
        }
        String parentRaw = clone.getPersistentData().getStringOr("ydnf_parent", "");
        BossState parentState = null;
        if (!parentRaw.isEmpty()) {
            try {
                parentState = bosses.get(UUID.fromString(parentRaw));
            } catch (IllegalArgumentException ignored) {}
        }

        ServerPlayer focus = nearestAlive(clone, players);
        if (focus == null) {
            clone.getNavigation().stop();
            return;
        }
        faceToward(clone, focus.position());
        if (parentState != null && now < parentState.cloneBusyUntil) {
            if (!motions.containsKey(clone.getUUID())) clone.getNavigation().stop();
            return;
        }

        double dist = clone.distanceTo(focus);
        if (dist > 3.8) clone.getNavigation().moveTo(focus, 1.12);
        else clone.getNavigation().stop();

        // 幻影独立轻度骚扰：近身龙枪刺击（冷青特效，低系数），核心压制仍靠与本体的交叉冲锋/联动雷枪协同
        long nextPoke = parentState == null ? 0L : parentState.cooldowns.getOrDefault("CLONE_POKE", 0L);
        if (dist <= 5.2 && now >= nextPoke) {
            if (parentState != null) {
                parentState.cooldowns.put("CLONE_POKE", now + 4200L);
                parentState.cloneBusyUntil = now + 1150L;
            }
            castSpearCombo(clone, ct, parentState, focus, now, true);
        }
    }

    void tickAll(long now) {
        // 1. 驱动所有冲锋与跃空俯冲实体位移
        for (var it = motions.entrySet().iterator(); it.hasNext(); ) {
            var entry = it.next();
            Motion m = entry.getValue();
            Entity raw = owner.level().getEntity(m.actorId);
            if (!(raw instanceof DungeonEnemy actor) || !actor.isAlive()) {
                it.remove();
                continue;
            }
            double prog = Math.clamp((now - m.startAt) / (double) Math.max(1L, m.endAt - m.startAt), 0.0, 1.0);
            Vec3 pos;
            if (m.kind == MotionKind.JUMP_SLAM) {
                // 前 45% 跃起悬停，45%~82% 俯冲砸地，82%~100% 落地稳姿
                double horizProg = Math.clamp(prog / 0.82, 0.0, 1.0);
                double arcY;
                if (prog < 0.45) {
                    double up = prog / 0.45;
                    arcY = m.peakHeight * Math.sin(up * Math.PI * 0.5);
                } else if (prog < 0.82) {
                    double down = (prog - 0.45) / 0.37;
                    arcY = m.peakHeight * (1.0 - down * down);
                } else {
                    arcY = 0.0;
                }
                Vec3 flat = m.from.lerp(m.to, horizProg);
                pos = new Vec3(flat.x, flat.y + arcY, flat.z);
                actor.setNoGravity(prog < 0.82);
            } else {
                pos = m.from.lerp(m.to, prog);
            }
            actor.setPos(pos.x, pos.y, pos.z);
            actor.setDeltaMovement(Vec3.ZERO);
            actor.fallDistance = 0f;
            if (now >= m.endAt) {
                finishMotion(actor);
                it.remove();
            }
        }

        // 2. 驱动所有排期事件
        for (BossState state : new ArrayList<>(bosses.values())) {
            Entity raw = owner.level().getEntity(state.bossId);
            if (!(raw instanceof DungeonEnemy boss) || !boss.isAlive()) {
                bosses.remove(state.bossId);
                continue;
            }
            for (Scheduled item : new ArrayList<>(state.queue)) {
                if (now >= item.at()) {
                    state.queue.remove(item);
                    item.task().run();
                }
            }
        }

        // 3. 驱动雷霆枪牢实体落地与超时引爆
        for (ThunderCage cage : new ArrayList<>(cages.values())) {
            if (!cage.spawned && now >= cage.spawnAt) {
                cage.spawned = true;
                for (int side = -1; side < 4; side++) {
                    CageEntity piece = new CageEntity(DungeonContentTypes.CAGE.get(), owner.level());
                    piece.initialize(cage.id, side);
                    piece.setPos(cage.anchor.x, cage.anchor.y, cage.anchor.z);
                    owner.level().addFreshEntity(piece);
                    cage.pieces.add(piece);
                }
            }
            if (now >= cage.expireAt) {
                detonateCage(cage, now);
            }
        }
    }

    // ==================== 阶段转场（不锁血 · 霸体演出技 + 横幅） ====================

    private void triggerPhase2Transition(Dungeon dungeon, DungeonEnemy boss, Target target, BossState state, List<ServerPlayer> players, long now) {
        setKnockbackResist(boss, 1.0);
        sendBanner(boss.instance(), "鲁卡斯 · 苍穹敕雷！天穹雷枪阵展开", DragonTowerLucas.COLOR_SKY_BLUE, 3200L);
        state.queue.add(new Scheduled(now + 2200L, () -> setKnockbackResist(boss, 0.0)));
        castSkySpearRain(dungeon, boss, target, state, players, now, true);
    }

    private void triggerPhase3Awakening(Dungeon dungeon, DungeonEnemy boss, Target target, BossState state, List<ServerPlayer> players, long now) {
        setKnockbackResist(boss, 1.0);
        var timing = target.actionTiming(now);
        long castMs = timing.duration(2200L);
        boss.play("phantom_awakening", castMs);
        state.busyUntil = now + castMs;
        boss.getNavigation().stop();

        sendBanner(boss.instance(), "鲁卡斯 · 龙王幻影觉醒！击杀幻影以破除合击", DragonTowerLucas.COLOR_VIOLET_BOLT, 4200L);

        // 脚下爆发一道紫蓝唤醒震荡环（轻系数预警，给玩家反应与压迫感）
        long warn = timing.windup(950L);
        long ringDur = timing.active(850L);
        sendHazard(boss.instance(), target, "lucas_awaken_" + UUID.randomUUID(), "RING_WAVE",
            boss.position(), boss.position(), 1.8, 2.4, 10.5, 0.0,
            now, now + warn, now + warn + ringDur, 0L,
            0.95, 4.0, true, 0, DragonTowerLucas.COLOR_VIOLET_BOLT);

        // 在安全侧翼位置召唤实体「鲁卡斯的幻影」
        state.queue.add(new Scheduled(now + timing.windup(900L), () -> {
            if (!boss.isAlive()) return;
            spawnClone(dungeon, boss, state);
        }));
        state.queue.add(new Scheduled(now + castMs, () -> setKnockbackResist(boss, 0.0)));
    }

    private void spawnClone(Dungeon dungeon, DungeonEnemy boss, BossState state) {
        if (state.cloneId != null) {
            Entity existing = owner.level().getEntity(state.cloneId);
            if (existing != null && existing.isAlive()) return;
        }
        double angle = state.random.nextDouble() * Math.PI * 2.0;
        Vec3 desired = boss.position().add(Math.cos(angle) * 4.5, 0, Math.sin(angle) * 4.5);
        Vec3 pos = clampArenaPos(dungeon, boss.position(), desired);
        DungeonEnemy clone = owner.spawn(dungeon, DragonTowerLucas.CLONE_ID, false, true, pos.x, pos.y, pos.z, cloneTarget -> {
            cloneTarget.speciesHealth = DragonTowerLucas.cloneHealthRatio();
            cloneTarget.speciesAttack = DragonTowerLucas.cloneAttackRatio();
            cloneTarget.noRewards = true;
            cloneTarget.elementalResists = DragonTowerLucas.initialResists();
        });
        clone.getPersistentData().putString("ydnf_parent", boss.getStringUUID());
        state.cloneId = clone.getUUID();
        state.cloneBusyUntil = System.currentTimeMillis() + 900L;
        clone.play("phantom_awakening", 900L);

        JsonObject evt = new JsonObject();
        evt.addProperty("kind", "CLONE_SUMMON");
        evt.addProperty("instance", boss.instance());
        evt.addProperty("x", pos.x);
        evt.addProperty("y", pos.y);
        evt.addProperty("z", pos.z);
        owner.broadcast(boss.instance(), DragonTowerLucas.EVENT_ACTION, evt);
    }

    // ==================== 技能决策 ====================

    private String selectSkill(BossState state, double dist, long now) {
        // P3 优先考虑终结大招（若冷却就绪）
        if (state.stage >= 3 && ready(state, "CROWN_TEMPEST", now) && dist <= 22.0) {
            return "CROWN_TEMPEST";
        }
        List<String> pool = new ArrayList<>();
        List<Integer> weights = new ArrayList<>();

        if (dist <= 6.0 && ready(state, "SPEAR_COMBO", now)) {
            addCandidate(pool, weights, "SPEAR_COMBO", state.lastSkill.equals("SPEAR_COMBO") ? 20 : 42);
        }
        if (dist <= 8.5 && ready(state, "THUNDER_DISCHARGE", now)) {
            addCandidate(pool, weights, "THUNDER_DISCHARGE", 32);
        }
        if (dist >= 3.2 && dist <= 16.0 && ready(state, "DRAGON_CHARGE", now)) {
            addCandidate(pool, weights, "DRAGON_CHARGE", dist > 7.0 ? 42 : 28);
        }
        if (dist <= 16.0 && ready(state, "THUNDER_CAGE", now) && !hasActiveCage(state.bossId)) {
            addCandidate(pool, weights, "THUNDER_CAGE", 26);
        }
        if (state.stage >= 2 && dist <= 20.0 && ready(state, "SKY_SPEAR_RAIN", now)) {
            addCandidate(pool, weights, "SKY_SPEAR_RAIN", 34);
        }
        if (state.stage >= 2 && dist >= 4.0 && dist <= 20.0 && ready(state, "JUMP_SLAM", now)) {
            addCandidate(pool, weights, "JUMP_SLAM", dist > 8.0 ? 40 : 30);
        }
        if (pool.isEmpty()) return null;
        int total = 0;
        for (int w : weights) total += w;
        int roll = state.random.nextInt(total);
        for (int i = 0; i < pool.size(); i++) {
            roll -= weights.get(i);
            if (roll < 0) return pool.get(i);
        }
        return pool.getLast();
    }

    private static void addCandidate(List<String> pool, List<Integer> weights, String id, int weight) {
        pool.add(id);
        weights.add(weight);
    }

    private boolean ready(BossState state, String skill, long now) {
        return state.cooldowns.getOrDefault(skill, 0L) <= now;
    }

    private void putCooldown(BossState state, String skill, long baseMs, long now) {
        double scale = state.stage >= 3 ? DragonTowerLucas.phase3CooldownScale() : 1.0;
        state.cooldowns.put(skill, now + Math.round(baseMs * scale));
    }

    private boolean hasActiveCage(UUID bossId) {
        for (ThunderCage cage : cages.values()) {
            if (cage.bossId.equals(bossId)) return true;
        }
        return false;
    }

    // ==================== 7 招具体实现 ====================

    /** 1. 龙枪连突：一段大扇形横扫 + 二段直线穿雷突刺 */
    private void castSpearCombo(DungeonEnemy actor, Target target, BossState state, ServerPlayer focus, long now, boolean cloneMode) {
        var timing = target.actionTiming(now);
        long w1 = timing.windup(DragonTowerLucas.moveWindup("SPEAR_COMBO", 450L));
        long a1 = timing.active(DragonTowerLucas.moveActive("SPEAR_COMBO", 220L));
        long total = timing.duration(DragonTowerLucas.moveDuration("SPEAR_COMBO", 1450L));
        double fanRange = DragonTowerLucas.moveRange("SPEAR_COMBO", 4.8);
        double coeff1 = DragonTowerLucas.moveCoeff("SPEAR_COMBO", 0.72) * (cloneMode ? 0.8 : 1.0);

        if (!cloneMode && state != null) {
            putCooldown(state, "SPEAR_COMBO", DragonTowerLucas.moveCooldown("SPEAR_COMBO", 3200L), now);
            state.busyUntil = now + total;
        }
        actor.play("spear_combo", total);
        Vec3 from = actor.position();
        Vec3 dir = horizontalDir(from, focus.position());
        int color = cloneMode ? DragonTowerLucas.COLOR_CLONE_CYAN : DragonTowerLucas.COLOR_SKY_BLUE;

        sendHazard(actor.instance(), target, "lucas_sweep_" + UUID.randomUUID(), "FAN_SLASH",
            from, from.add(dir.scale(fanRange)), fanRange, 0.0, 0.0, 72.0,
            now, now + w1, now + w1 + a1, 0L,
            coeff1, 1.5, false, -1, color);

        if (!cloneMode && state != null) {
            long thrustAt = now + timing.duration(560L);
            state.queue.add(new Scheduled(thrustAt, () -> {
                if (!actor.isAlive() || CombatContentTypes.controlled(actor)) return;
                long fire = System.currentTimeMillis();
                var t2 = target.actionTiming(fire);
                long w2 = t2.windup(DragonTowerLucas.moveWindup("SPEAR_COMBO_THRUST", 350L));
                long a2 = t2.active(DragonTowerLucas.moveActive("SPEAR_COMBO_THRUST", 220L));
                double thrustRange = DragonTowerLucas.moveRange("SPEAR_COMBO_THRUST", 7.5);
                double coeff2 = DragonTowerLucas.moveCoeff("SPEAR_COMBO_THRUST", 0.90);
                Vec3 origin = actor.position();
                Vec3 aimDir = focus.isAlive() ? horizontalDir(origin, focus.position()) : dir;
                faceToward(actor, origin.add(aimDir.scale(4.0)));
                sendHazard(actor.instance(), target, "lucas_thrust_" + UUID.randomUUID(), "INSTANT_LINE",
                    origin, origin.add(aimDir.scale(thrustRange)), thrustRange, 2.1, 0.0, 0.0,
                    fire, fire + w2, fire + w2 + a2, 0L,
                    coeff2, 2.0, true, 0, DragonTowerLucas.COLOR_VIOLET_BOLT);
            }));
        }
    }

    /** 2. 破空冲锋（P3 幻影存活时触发冷青色交叉合击线） */
    private void castDragonCharge(Dungeon dungeon, DungeonEnemy boss, Target target, BossState state, ServerPlayer focus, long now) {
        var timing = target.actionTiming(now);
        long windup = timing.windup(DragonTowerLucas.moveWindup("DRAGON_CHARGE", 700L));
        long active = timing.active(DragonTowerLucas.moveActive("DRAGON_CHARGE", 520L));
        long total = timing.duration(DragonTowerLucas.moveDuration("DRAGON_CHARGE", 1700L));
        double range = DragonTowerLucas.moveRange("DRAGON_CHARGE", 13.0);
        double width = DragonTowerLucas.param("chargeWidth", 2.8);
        double coeff = DragonTowerLucas.moveCoeff("DRAGON_CHARGE", 1.25);

        putCooldown(state, "DRAGON_CHARGE", DragonTowerLucas.moveCooldown("DRAGON_CHARGE", 7500L), now);
        state.busyUntil = now + total;
        boss.play("dragon_charge", total);
        boss.getNavigation().stop();

        Vec3 from = boss.position();
        Vec3 dir = horizontalDir(from, focus.position());
        Vec3 to = clampArenaPos(dungeon, from, from.add(dir.scale(range)));
        faceToward(boss, to);

        sendHazard(boss.instance(), target, "lucas_charge_" + UUID.randomUUID(), "DASH_LINE",
            from, to, from.distanceTo(to), width, 0.0, 0.0,
            now, now + windup, now + windup + active, 0L,
            coeff, 4.5, false, 0, DragonTowerLucas.COLOR_SKY_BLUE);

        setKnockbackResist(boss, 1.0);
        state.queue.add(new Scheduled(now + windup, () -> {
            if (!boss.isAlive() || CombatContentTypes.controlled(boss)) {
                setKnockbackResist(boss, 0.0);
                return;
            }
            long start = System.currentTimeMillis();
            motions.put(boss.getUUID(), new Motion(boss.getUUID(), MotionKind.CHARGE, boss.position(), to, start, start + active, 0.0));
        }));

        // 协同：若幻影存活，立刻从侧翼拉出冷青色交叉冲锋线穿过目标位置！
        DungeonEnemy clone = activeClone(state);
        if (clone != null) {
            Target ct = targets.target(clone);
            if (ct != null && !ct.life.dead) {
                Vec3 side = new Vec3(-dir.z, 0, dir.x);
                Vec3 pivot = focus.position();
                Vec3 cloneFrom = clampArenaPos(dungeon, clone.position(), pivot.add(side.scale(7.5)));
                Vec3 cloneTo = clampArenaPos(dungeon, cloneFrom, pivot.subtract(side.scale(7.5)));
                clone.setPos(cloneFrom.x, cloneFrom.y, cloneFrom.z);
                faceToward(clone, cloneTo);
                clone.play("dragon_charge", total);
                state.cloneBusyUntil = now + total;
                sendHazard(boss.instance(), ct, "lucas_clone_charge_" + UUID.randomUUID(), "DASH_LINE",
                    cloneFrom, cloneTo, cloneFrom.distanceTo(cloneTo), width * 0.88, 0.0, 0.0,
                    now, now + windup, now + windup + active, 0L,
                    coeff * 0.85, 3.0, true, 0, DragonTowerLucas.COLOR_CLONE_CYAN);
                state.queue.add(new Scheduled(now + windup, () -> {
                    if (!clone.isAlive() || CombatContentTypes.controlled(clone)) return;
                    long start = System.currentTimeMillis();
                    motions.put(clone.getUUID(), new Motion(clone.getUUID(), MotionKind.CHARGE, clone.position(), cloneTo, start, start + active, 0.0));
                }));
            }
        }
    }

    /** 3. 雷电震荡环（旧放电升级：扩散 RING_WAVE + 环缘电刺） */
    private void castThunderDischarge(DungeonEnemy boss, Target target, BossState state, long now) {
        var timing = target.actionTiming(now);
        long windup = timing.windup(DragonTowerLucas.moveWindup("THUNDER_DISCHARGE", 800L));
        long active = timing.active(DragonTowerLucas.paramMs("ringDurationMs", 950L));
        long total = timing.duration(DragonTowerLucas.moveDuration("THUNDER_DISCHARGE", 1900L));
        double endR = DragonTowerLucas.param("ringEndRadius", 12.0);
        double ringW = DragonTowerLucas.param("ringWidth", 2.4);
        double coeff = DragonTowerLucas.moveCoeff("THUNDER_DISCHARGE", 1.20);

        putCooldown(state, "THUNDER_DISCHARGE", DragonTowerLucas.moveCooldown("THUNDER_DISCHARGE", 9000L), now);
        state.busyUntil = now + total;
        boss.play("thunder_discharge", total);
        boss.getNavigation().stop();

        Vec3 center = boss.position();
        sendHazard(boss.instance(), target, "lucas_ring_" + UUID.randomUUID(), "RING_WAVE",
            center, center, 1.5, ringW, endR, 0.0,
            now, now + windup, now + windup + active, 0L,
            coeff, 3.0, true, 0, DragonTowerLucas.COLOR_SKY_BLUE);
    }

    /** 4. 雷霆枪牢（保留 3 击可破实体电牢，超时降雷爆破） */
    private void castThunderCage(Dungeon dungeon, DungeonEnemy boss, Target target, BossState state, ServerPlayer focus, long now) {
        var timing = target.actionTiming(now);
        long windup = timing.windup(DragonTowerLucas.moveWindup("THUNDER_CAGE", 950L));
        long cageDur = DragonTowerLucas.cageDurationMs();
        long total = timing.duration(DragonTowerLucas.moveDuration("THUNDER_CAGE", 1500L));
        double radius = DragonTowerLucas.moveRange("THUNDER_CAGE", 1.75);
        double coeff = DragonTowerLucas.moveCoeff("THUNDER_CAGE", 1.65);

        putCooldown(state, "THUNDER_CAGE", DragonTowerLucas.moveCooldown("THUNDER_CAGE", 14000L), now);
        state.busyUntil = now + total;
        boss.play("thunder_cage", total);
        boss.getNavigation().stop();

        Vec3 anchor = clampArenaPos(dungeon, focus.position(), focus.position());
        String cageId = "lucas_cage_" + UUID.randomUUID();
        ThunderCage cage = new ThunderCage(cageId, boss.instance(), boss.getUUID(), anchor, now + windup, now + windup + cageDur, radius, coeff);
        cages.put(cageId, cage);

        // 下发枪牢预警与持续封锁特效（实际伤害在超时引爆时单独发射 CAGE_DETONATE，此处 coefficient=0）
        sendHazard(boss.instance(), target, cageId, "CAGE_ZONE",
            anchor, anchor, radius, 0.0, DragonTowerLucas.cageBreakHits(), 0.0,
            now, now + windup, now + windup + cageDur, 0L,
            0.0, 0.0, true, 0, DragonTowerLucas.COLOR_VIOLET_BOLT);
    }

    /** 5. 天穹雷枪阵（P2 转阶段 & 常规技能；P3 幻影存活时追加冷青侧翼雷枪） */
    private void castSkySpearRain(Dungeon dungeon, DungeonEnemy boss, Target target, BossState state, List<ServerPlayer> players, long now, boolean transitionBurst) {
        var timing = target.actionTiming(now);
        long warn = timing.windup(DragonTowerLucas.moveWindup("SKY_SPEAR_RAIN", 950L));
        long active = timing.active(DragonTowerLucas.moveActive("SKY_SPEAR_RAIN", 320L));
        long total = timing.duration(DragonTowerLucas.moveDuration("SKY_SPEAR_RAIN", 2100L));
        double radius = DragonTowerLucas.param("skySpearRadius", 2.5);
        double coeff = DragonTowerLucas.moveCoeff("SKY_SPEAR_RAIN", 1.15);
        int count = transitionBurst ? DragonTowerLucas.paramInt("skySpearCountTransition", 8)
            : (state.stage >= 3 ? DragonTowerLucas.paramInt("skySpearCountP3", 7) : DragonTowerLucas.paramInt("skySpearCountP2", 5));

        putCooldown(state, "SKY_SPEAR_RAIN", DragonTowerLucas.moveCooldown("SKY_SPEAR_RAIN", 11500L), now);
        state.busyUntil = now + total;
        boss.play("sky_spear_rain", total);
        boss.getNavigation().stop();

        List<ServerPlayer> alive = players.stream().filter(this::aliveParticipant).toList();
        if (alive.isEmpty()) return;

        for (int i = 0; i < count; i++) {
            long delay = i * 165L;
            final int idx = i;
            state.queue.add(new Scheduled(now + delay, () -> {
                if (!boss.isAlive()) return;
                long fire = System.currentTimeMillis();
                ServerPlayer pick = alive.get(idx % alive.size());
                Vec3 base = pick.isAlive() ? pick.position() : boss.position();
                Vec3 desired;
                if (idx == 0) {
                    desired = base;
                } else {
                    double ang = idx * 2.399963 + state.random.nextDouble() * 0.45;
                    double dist = 2.6 + state.random.nextDouble() * 5.8;
                    desired = base.add(Math.cos(ang) * dist, 0, Math.sin(ang) * dist);
                }
                Vec3 drop = clampArenaPos(dungeon, base, desired);
                sendHazard(boss.instance(), target, "lucas_spear_" + UUID.randomUUID(), "SKY_SPEAR",
                    drop, drop, radius, 0.0, 0.0, 0.0,
                    fire, fire + warn, fire + warn + active, 0L,
                    coeff, 2.0, true, 0, DragonTowerLucas.COLOR_SKY_BLUE);
            }));
        }

        // 幻影协同：追加 3 枚冷青色雷枪封锁走位
        DungeonEnemy clone = activeClone(state);
        if (clone != null) {
            Target ct = targets.target(clone);
            if (ct != null && !ct.life.dead) {
                clone.play("sky_spear_rain", total);
                state.cloneBusyUntil = now + total;
                for (int i = 0; i < 3; i++) {
                    long delay = 220L + i * 240L;
                    final int idx = i;
                    state.queue.add(new Scheduled(now + delay, () -> {
                        if (!clone.isAlive() || !boss.isAlive()) return;
                        long fire = System.currentTimeMillis();
                        ServerPlayer pick = alive.get(idx % alive.size());
                        Vec3 base = pick.isAlive() ? pick.position() : clone.position();
                        double ang = idx * (Math.PI * 2.0 / 3.0) + 0.5;
                        Vec3 drop = clampArenaPos(dungeon, base, base.add(Math.cos(ang) * 4.2, 0, Math.sin(ang) * 4.2));
                        sendHazard(boss.instance(), ct, "lucas_clone_spear_" + UUID.randomUUID(), "SKY_SPEAR",
                            drop, drop, radius * 0.92, 0.0, 0.0, 0.0,
                            fire, fire + warn, fire + warn + active, 0L,
                            coeff * 0.85, 1.5, true, 0, DragonTowerLucas.COLOR_CLONE_CYAN);
                    }));
                }
            }
        }
    }

    /** 6. 跃空龙枪俯冲（P2 新增：腾空锁定俯冲砸地 + 落地扩散雷环） */
    private void castJumpSlam(Dungeon dungeon, DungeonEnemy boss, Target target, BossState state, ServerPlayer focus, long now) {
        var timing = target.actionTiming(now);
        long airMs = timing.windup(DragonTowerLucas.moveWindup("JUMP_SLAM", 1050L));
        long slamActive = timing.active(DragonTowerLucas.moveActive("JUMP_SLAM", 350L));
        long total = timing.duration(DragonTowerLucas.moveDuration("JUMP_SLAM", 2250L));
        double slamR = DragonTowerLucas.param("jumpSlamRadius", 5.0);
        double ringEndR = DragonTowerLucas.param("jumpRingEndRadius", 11.0);
        double jumpH = DragonTowerLucas.param("jumpHeight", 6.5);
        double coeff = DragonTowerLucas.moveCoeff("JUMP_SLAM", 1.38);

        putCooldown(state, "JUMP_SLAM", DragonTowerLucas.moveCooldown("JUMP_SLAM", 12500L), now);
        state.busyUntil = now + total;
        boss.play("jump_slam", total);
        boss.getNavigation().stop();

        Vec3 from = boss.position();
        Vec3 land = clampArenaPos(dungeon, from, focus.position());
        faceToward(boss, land);

        setKnockbackResist(boss, 1.0);
        motions.put(boss.getUUID(), new Motion(boss.getUUID(), MotionKind.JUMP_SLAM, from, land, now, now + Math.round(airMs / 0.82), jumpH));

        // 落地重击圆阵
        sendHazard(boss.instance(), target, "lucas_slam_" + UUID.randomUUID(), "JUMP_SLAM_IMPACT",
            from, land, slamR, 0.0, jumpH, 0.0,
            now, now + airMs, now + airMs + slamActive, 0L,
            coeff, 5.5, true, 0, DragonTowerLucas.COLOR_VIOLET_BOLT);

        // 落地瞬间再扩散一道外扩雷环
        state.queue.add(new Scheduled(now + airMs, () -> {
            if (!boss.isAlive()) return;
            long fire = System.currentTimeMillis();
            sendHazard(boss.instance(), target, "lucas_slam_ring_" + UUID.randomUUID(), "RING_WAVE",
                land, land, slamR * 0.75, 2.2, ringEndR, 0.0,
                fire, fire + 80L, fire + 860L, 0L,
                coeff * 0.72, 2.5, true, 0, DragonTowerLucas.COLOR_SKY_BLUE);
        }));
    }

    /** 7. 断冠天雷暴（P3 终结技：HUD 预警横幅 + 安全环带爆发 + 十字雷斩线收尾） */
    private void castCrownTempest(Dungeon dungeon, DungeonEnemy boss, Target target, BossState state, ServerPlayer focus, long now) {
        var timing = target.actionTiming(now);
        long warn = timing.windup(DragonTowerLucas.moveWindup("CROWN_TEMPEST", 1550L));
        long active = timing.active(DragonTowerLucas.moveActive("CROWN_TEMPEST", 380L));
        long total = timing.duration(DragonTowerLucas.moveDuration("CROWN_TEMPEST", 3400L));
        double safeMin = DragonTowerLucas.param("tempestSafeMin", 5.5);
        double safeMax = DragonTowerLucas.param("tempestSafeMax", 10.0);
        double outerR = DragonTowerLucas.param("tempestOuterRadius", 18.0);
        double coeff = DragonTowerLucas.moveCoeff("CROWN_TEMPEST", 1.90);

        putCooldown(state, "CROWN_TEMPEST", DragonTowerLucas.moveCooldown("CROWN_TEMPEST", 22000L), now);
        state.busyUntil = now + total;
        boss.play("crown_tempest", total);
        boss.getNavigation().stop();
        setKnockbackResist(boss, 1.0);

        sendBanner(boss.instance(), "断冠天雷暴 · 躲入绿色安全环带！", DragonTowerLucas.COLOR_SAFE_GREEN, warn + 900L);

        Vec3 center = boss.position();
        sendHazard(boss.instance(), target, "lucas_tempest_band_" + UUID.randomUUID(), "SAFE_BAND_BURST",
            center, center, safeMin, safeMax, outerR, 0.0,
            now, now + warn, now + warn + active, 0L,
            coeff, 4.0, true, 0, DragonTowerLucas.COLOR_DANGER_VIOLET);

        // 安全环带爆发后，立刻以鲁卡斯为中心向目标方位展开十字雷斩线收尾
        state.queue.add(new Scheduled(now + warn + 120L, () -> {
            if (!boss.isAlive()) return;
            long fire = System.currentTimeMillis();
            var t2 = target.actionTiming(fire);
            long cw = t2.windup(DragonTowerLucas.moveWindup("CROWN_TEMPEST_CROSS", 750L));
            long ca = t2.active(DragonTowerLucas.moveActive("CROWN_TEMPEST_CROSS", 300L));
            double range = DragonTowerLucas.param("tempestCrossRange", 19.0);
            double width = DragonTowerLucas.param("tempestCrossWidth", 3.0);
            double crossCoeff = DragonTowerLucas.moveCoeff("CROWN_TEMPEST_CROSS", 1.55);

            Vec3 aim = focus.isAlive() ? horizontalDir(center, focus.position()) : new Vec3(0, 0, 1);
            Vec3 perp = new Vec3(-aim.z, 0, aim.x);
            sendHazard(boss.instance(), target, "lucas_tempest_x1_" + UUID.randomUUID(), "INSTANT_LINE",
                center.subtract(aim.scale(range)), center.add(aim.scale(range)), range * 2.0, width, 0.0, 0.0,
                fire, fire + cw, fire + cw + ca, 0L,
                crossCoeff, 3.0, true, 0, DragonTowerLucas.COLOR_SKY_BLUE);
            sendHazard(boss.instance(), target, "lucas_tempest_x2_" + UUID.randomUUID(), "INSTANT_LINE",
                center.subtract(perp.scale(range)), center.add(perp.scale(range)), range * 2.0, width, 0.0, 0.0,
                fire, fire + cw, fire + cw + ca, 0L,
                crossCoeff, 3.0, true, 0, DragonTowerLucas.COLOR_VIOLET_BOLT);
        }));
        state.queue.add(new Scheduled(now + total, () -> setKnockbackResist(boss, 0.0)));
    }

    // ==================== 可破雷霆枪牢交互 ====================

    boolean cageHit(ServerPlayer player, JsonObject j) {
        if (!j.has("cage")) return false;
        String id = j.get("cage").getAsString();
        ThunderCage cage = cages.get(id);
        if (cage == null) return false;
        if (!owner.participant(player, cage.instance)) return true;
        String op = player.getUUID() + "/" + playersAccess.session(player).id + "/" + j.get("operation").getAsString();
        if (!cage.operations.add(op)) return true;
        cage.hits++;
        int maxHits = DragonTowerLucas.cageBreakHits();

        JsonObject evt = new JsonObject();
        evt.addProperty("kind", cage.hits >= maxHits ? "CAGE_BROKEN" : "CAGE_HIT");
        evt.addProperty("instance", cage.instance);
        evt.addProperty("id", cage.id);
        evt.addProperty("hits", cage.hits);
        evt.addProperty("maxHits", maxHits);
        evt.addProperty("x", cage.anchor.x);
        evt.addProperty("y", cage.anchor.y);
        evt.addProperty("z", cage.anchor.z);
        owner.broadcast(cage.instance, DragonTowerLucas.EVENT_ACTION, evt);

        if (cage.hits >= maxHits) {
            removeCage(cage, true);
        }
        return true;
    }

    private void detonateCage(ThunderCage cage, long now) {
        if (!cages.containsKey(cage.id)) return;
        Entity raw = owner.level().getEntity(cage.bossId);
        if (raw instanceof DungeonEnemy boss && boss.isAlive()) {
            Target t = targets.target(boss);
            if (t != null && !t.life.dead) {
                sendHazard(cage.instance, t, "lucas_cage_boom_" + UUID.randomUUID(), "CAGE_DETONATE",
                    cage.anchor, cage.anchor, cage.radius + 0.35, 0.0, 0.0, 0.0,
                    now, now, now + 320L, 0L,
                    cage.coefficient, 3.5, true, 0, DragonTowerLucas.COLOR_VIOLET_BOLT);
            }
        }
        removeCage(cage, false);
    }

    private void removeCage(ThunderCage cage, boolean brokenByPlayer) {
        for (CageEntity piece : cage.pieces) piece.discard();
        cage.pieces.clear();
        cages.remove(cage.id);
        if (brokenByPlayer) {
            JsonObject cancel = new JsonObject();
            cancel.addProperty("kind", "CANCEL_HAZARD");
            cancel.addProperty("instance", cage.instance);
            cancel.addProperty("id", cage.id);
            owner.broadcast(cage.instance, DragonTowerLucas.EVENT_ACTION, cancel);
        }
    }

    // ==================== 生命周期清理 ====================

    void died(UUID entityId) {
        BossState main = bosses.remove(entityId);
        motions.remove(entityId);
        if (main != null) {
            for (ThunderCage cage : new ArrayList<>(cages.values())) {
                if (cage.bossId.equals(entityId)) removeCage(cage, true);
            }
            sendClear(main.instance);
            return;
        }
        // 如果是幻影被击杀，立刻解除本体合击引用并发送反馈事件
        for (BossState state : bosses.values()) {
            if (entityId.equals(state.cloneId)) {
                state.cloneId = null;
                sendBanner(state.instance, "鲁卡斯的幻影已被击破！合击压制解除", DragonTowerLucas.COLOR_CLONE_CYAN, 2600L);
                break;
            }
        }
    }

    void cancel(UUID entityId) {
        motions.remove(entityId);
        Entity raw = owner.level().getEntity(entityId);
        if (raw instanceof DungeonEnemy e) finishMotion(e);
        BossState state = bosses.get(entityId);
        if (state != null) {
            state.queue.clear();
        }
    }

    void close(Dungeon dungeon) {
        String instanceId = dungeon.id.toString();
        bosses.values().removeIf(s -> s.instance.equals(instanceId));
        for (ThunderCage cage : new ArrayList<>(cages.values())) {
            if (cage.instance.equals(instanceId)) removeCage(cage, true);
        }
        sendClear(instanceId);
    }

    // ==================== 场地边界与工具方法 ====================

    private DungeonEnemy activeClone(BossState state) {
        if (state.cloneId == null) return null;
        Entity raw = owner.level().getEntity(state.cloneId);
        if (raw instanceof DungeonEnemy clone && clone.isAlive() && !CombatContentTypes.controlled(clone)) {
            return clone;
        }
        return null;
    }

    private Vec3 clampArenaPos(Dungeon dungeon, Vec3 fallback, Vec3 desired) {
        if (dungeon.tower != null) {
            return AwakeningTowerLayout.ground(owner.level(), dungeon.cell, desired, fallback);
        }
        if (DragonTowerEncounterService.handles(dungeon)) {
            int ox = dungeon.cell * 512;
            double lx = desired.x - (ox + 0.5);
            double lz = desired.z - 0.5;
            var crown = DragonTower.floors().getLast();
            double dist = Math.hypot(lx - crown.x(), lz - crown.z());
            double maxR = Math.min(crown.rx(), crown.rz()) - 2.2;
            if (dist > maxR && dist > 1e-4) {
                double scale = maxR / dist;
                lx = crown.x() + (lx - crown.x()) * scale;
                lz = crown.z() + (lz - crown.z()) * scale;
            }
            double wx = ox + 0.5 + lx;
            double wz = 0.5 + lz;
            double wy = findSafeFloorY(wx, 80 + crown.y(), wz, fallback.y);
            return new Vec3(wx, wy, wz);
        }
        // 藏宝图或通用平坦竞技场：基于参考高度向下/向上探测最近可站立地面
        double wy = findSafeFloorY(desired.x, fallback.y, desired.z, fallback.y);
        return new Vec3(desired.x, wy, desired.z);
    }

    private double findSafeFloorY(double x, double hintY, double z, double fallbackY) {
        var level = owner.level();
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        int base = (int) Math.round(hintY);
        for (int dy = 2; dy >= -3; dy--) {
            BlockPos feet = new BlockPos(bx, base + dy, bz);
            if (level.getBlockState(feet).isAir()
                && level.getBlockState(feet.above()).isAir()
                && !level.getBlockState(feet.below()).isAir()) {
                return feet.getY();
            }
        }
        return fallbackY;
    }

    private void stopMotion(DungeonEnemy actor) {
        if (motions.remove(actor.getUUID()) != null) {
            finishMotion(actor);
        }
    }

    private void finishMotion(DungeonEnemy actor) {
        actor.setNoGravity(false);
        setKnockbackResist(actor, 0.0);
    }

    private static void setKnockbackResist(DungeonEnemy actor, double value) {
        var attr = actor.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (attr != null) attr.setBaseValue(value);
    }

    private boolean aliveParticipant(ServerPlayer p) {
        return p.isAlive() && !p.isSpectator() && !p.getAbilities().invulnerable && !playersAccess.session(p).life.dead;
    }

    private ServerPlayer nearestAlive(DungeonEnemy e, List<ServerPlayer> players) {
        return players.stream().filter(this::aliveParticipant).min(Comparator.comparingDouble(e::distanceToSqr)).orElse(null);
    }

    private static Vec3 horizontalDir(Vec3 from, Vec3 to) {
        Vec3 d = new Vec3(to.x - from.x, 0, to.z - from.z);
        return d.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : d.normalize();
    }

    private static void faceToward(DungeonEnemy actor, Vec3 target) {
        Vec3 dir = horizontalDir(actor.position(), target);
        float yaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
        actor.setYRot(yaw);
        actor.yBodyRot = yaw;
        actor.yHeadRot = yaw;
    }

    private void sendBanner(String instance, String title, int color, long durationMs) {
        JsonObject pkt = new JsonObject();
        pkt.addProperty("kind", "BANNER");
        pkt.addProperty("instance", instance);
        pkt.addProperty("title", title);
        pkt.addProperty("color", color);
        pkt.addProperty("durationMs", durationMs);
        owner.broadcast(instance, DragonTowerLucas.EVENT_ACTION, pkt);
    }

    private void sendClear(String instance) {
        JsonObject pkt = new JsonObject();
        pkt.addProperty("kind", "CLEAR_ENCOUNTER_EFFECTS");
        pkt.addProperty("instance", instance);
        owner.broadcast(instance, DragonTowerLucas.EVENT_ACTION, pkt);
    }

    private void sendHazard(String instance, Target boss, String id, String type,
                            Vec3 from, Vec3 to, double radius, double width, double auxRadius, double slashAngle,
                            long startAt, long warnUntil, long endAt, long tickMs,
                            double coefficient, double knockback, boolean magic, int element, int color) {
        JsonObject pkt = new JsonObject();
        pkt.addProperty("id", id);
        pkt.addProperty("instance", instance);
        pkt.addProperty("type", type);
        pkt.add("boss", JSON.toJsonTree(boss));
        pkt.addProperty("x", from.x);
        pkt.addProperty("y", from.y);
        pkt.addProperty("z", from.z);
        pkt.addProperty("tx", to.x);
        pkt.addProperty("ty", to.y);
        pkt.addProperty("tz", to.z);
        pkt.addProperty("radius", radius);
        pkt.addProperty("width", width);
        pkt.addProperty("auxRadius", auxRadius);
        pkt.addProperty("slashAngle", slashAngle);
        pkt.addProperty("startAt", startAt);
        pkt.addProperty("warnUntil", warnUntil);
        pkt.addProperty("endAt", endAt);
        pkt.addProperty("tickMs", tickMs);
        pkt.addProperty("coefficient", coefficient);
        pkt.addProperty("knockback", knockback);
        pkt.addProperty("magic", magic);
        pkt.addProperty("element", element);
        pkt.addProperty("color", color);
        owner.broadcast(instance, DragonTowerLucas.HAZARD_ACTION, pkt);
    }
}
