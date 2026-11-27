package cn.yumis.fight.modules.dungeons.application;

import cn.yumis.fight.modules.combat.api.CombatContentTypes;
import cn.yumis.fight.modules.dungeons.api.Dungeon;
import cn.yumis.fight.modules.dungeons.api.DungeonEnemy;
import cn.yumis.fight.modules.dungeons.api.DungeonPorts;
import cn.yumis.fight.modules.dungeons.api.Target;
import cn.yumis.fight.modules.dungeons.content.AwakeningTowerLayout;
import cn.yumis.fight.modules.dungeons.content.TheaterTowerLiangyue;
import cn.yumis.fight.modules.dungeons.content.TheaterTowerLiangyue.SkillId;
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
 * 剧场塔第 20 层专属首领「神剑-梁月的幻影」服务端技能状态机与场地实体维护。
 * 遵循权威划分：服务端维护首领位移、技能调度与追击旋风轨迹（位置同步间隔 >= 250ms）；
 * 玩家受击判定与特效渲染由本机客户端权威执行。
 */
public final class LiangyueEncounterService {
    private static final Gson JSON = new GsonBuilder().disableHtmlEscaping().create();

    static final class ActiveCast {
        final SkillId skill;
        long startAt, endAt, nextAt;
        long windupMs, activeMs, auxMs;
        int step, totalSteps;
        int subPhase;
        long stormSeed;
        Vec3 from = Vec3.ZERO, to = Vec3.ZERO, anchor = Vec3.ZERO, direction = new Vec3(0, 0, 1);

        ActiveCast(SkillId skill) {
            this.skill = skill;
        }
    }

    static final class WhirlwindHazard {
        final String id;
        final int laneIndex;
        Vec3 pos;
        final long startAt, warnUntil, endAt;
        long lastMoveAt, lastBroadcastAt;

        WhirlwindHazard(String id, int laneIndex, Vec3 pos, long startAt, long warnUntil, long endAt) {
            this.id = id;
            this.laneIndex = laneIndex;
            this.pos = pos;
            this.startAt = startAt;
            this.warnUntil = warnUntil;
            this.endAt = endAt;
            this.lastMoveAt = warnUntil;
            this.lastBroadcastAt = startAt;
        }
    }

    public static final class State {
        public UUID bossId;
        public String instanceId;
        public int cell;
        public long busyUntil;
        public long lastServerTick = -1L;
        public int sequence;
        public String lastSkill = "";
        public final Map<String, Long> cooldownUntil = new HashMap<>();
        final List<WhirlwindHazard> whirlwinds = new ArrayList<>();
        ActiveCast activeCast;
    }

    private final DungeonService owner;
    private final MinecraftServer server;
    private final DungeonPorts.Players players;
    private final DungeonPorts.Targets targets;
    private final Random random = new Random();
    private final Map<String, State> states = new LinkedHashMap<>();

    LiangyueEncounterService(DungeonService owner, MinecraftServer server,
                             DungeonPorts.Players players, DungeonPorts.Targets targets) {
        this.owner = owner;
        this.server = server;
        this.players = players;
        this.targets = targets;
    }

    /** 20 Hz 高频推进：由 ServerApplication.tick 驱动，确保猛龙 20 格突进、旋风追击与暴风式穿梭平滑精准。 */
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
                server.sendSystemMessage(cn.yumis.fight.modules.presentation.api.ModText.of("剧场塔梁月首领更新失败：" + failure.getMessage()));
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

    void brain(Dungeon d, DungeonEnemy e, List<ServerPlayer> fighters, long now) {
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

        // 1. 持续推进已召唤的追击旋风
        tickWhirlwinds(d, s, fighters, now);

        // 2. 推进正在执行的技能动作
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

        // 3. 空闲时面向最近玩家并保持剑士压迫距离
        ServerPlayer nearest = nearestFighter(e, fighters);
        if (nearest != null) {
            facePosition(e, nearest.position());
            if (!e.isNoGravity() && e.distanceTo(nearest) > 5.5) {
                e.getNavigation().moveTo(nearest, 1.15);
            } else {
                e.getNavigation().stop();
            }
        }

        if (now < s.busyUntil) return;
        SkillId chosen = chooseNextSkill(s, now, random);
        if (chosen == null) return;
        s.lastSkill = chosen.id;
        // 冷却始终按技能定义原值计算，不受冰缓倍率缩放
        s.cooldownUntil.put(chosen.id, now + chosen.cooldownMs);
        startSkill(d, e, t, s, chosen, fighters, now);
    }

    private State initState(Dungeon d, DungeonEnemy e, long now) {
        State s = new State();
        s.bossId = e.getUUID();
        s.instanceId = d.id.toString();
        s.cell = d.cell;
        s.busyUntil = now + 1_000L;
        return s;
    }

    /** 纯函数选招入口（便于无头测试验证 4 项技能冷却与防连发调度）。 */
    public static SkillId chooseNextSkill(State s, long now, Random rng) {
        List<SkillId> candidates = new ArrayList<>(4);
        for (SkillId skill : SkillId.values()) {
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

    private void startSkill(Dungeon d, DungeonEnemy e, Target t, State s, SkillId skill, List<ServerPlayer> fighters, long now) {
        Target.ActionTiming timing = t.actionTiming(now);
        ServerPlayer targetPlayer = nearestFighter(e, fighters);
        if (targetPlayer == null) return;
        String instanceId = d.id.toString();
        Vec3 dir = horizontalDirection(e.position(), targetPlayer.position());

        switch (skill) {
            case TRIPLE_SLASH_WAVE -> {
                long intervalMs = timing.duration(TheaterTowerLiangyue.SKILL1_SWING_INTERVAL_MS);
                ActiveCast cast = new ActiveCast(skill);
                cast.startAt = now;
                cast.nextAt = now;
                cast.step = 0;
                cast.totalSteps = TheaterTowerLiangyue.SKILL1_STRIKES;
                cast.auxMs = intervalMs;
                cast.direction = dir;
                s.activeCast = cast;
                e.getNavigation().stop();
                fireTripleSlashStrike(d, e, t, s, cast, fighters, timing, now);
            }
            case RAGING_DRAGON -> {
                long windupMs = timing.windup(TheaterTowerLiangyue.SKILL2_WINDUP_MS);
                long dashMs = timing.active(TheaterTowerLiangyue.SKILL2_DASH_MS);
                Vec3 startPos = AwakeningTowerLayout.ground(owner.level(), d.cell, e.position(), e.position());
                Vec3 endPos = computeDragonEnd(owner.level(), d.cell, startPos, dir, TheaterTowerLiangyue.SKILL2_DASH_DISTANCE);
                ActiveCast cast = new ActiveCast(skill);
                cast.startAt = now;
                cast.nextAt = now + windupMs;
                cast.endAt = now + windupMs + dashMs;
                cast.windupMs = windupMs;
                cast.activeMs = dashMs;
                cast.subPhase = 0;
                cast.from = startPos;
                cast.to = endPos;
                cast.direction = horizontalDirection(startPos, endPos);
                s.activeCast = cast;
                e.getNavigation().stop();
                facePosition(e, endPos);
                e.play("raging_dragon_windup", windupMs);

                String hid = instanceId + "/dragon/" + (++s.sequence);
                owner.broadcast(instanceId, "theaterBossHazard", hazardPacket(
                    instanceId, hid, "DRAGON_DASH", t,
                    startPos, endPos, startPos.distanceTo(endPos), TheaterTowerLiangyue.SKILL2_DASH_WIDTH,
                    now, now + windupMs, now + windupMs + dashMs, 0L,
                    TheaterTowerLiangyue.COEFF_DRAGON_DASH, 0.0, 0.0, 0L, 0L, 0L, 0L,
                    0x66dcff
                ));
            }
            case STORM_AWAKENING -> {
                long deployMs = timing.windup(TheaterTowerLiangyue.SKILL3_DEPLOY_MS);
                long slashIntervalMs = timing.duration(TheaterTowerLiangyue.SKILL3_SLASH_INTERVAL_MS);
                long slashPhaseMs = slashIntervalMs * TheaterTowerLiangyue.SKILL3_SWORDS;
                long finisherDelayMs = timing.windup(TheaterTowerLiangyue.SKILL3_FINISHER_DELAY_MS);
                long finisherLingerMs = timing.active(TheaterTowerLiangyue.SKILL3_FINISHER_LINGER_MS);
                long totalMs = deployMs + slashPhaseMs + finisherDelayMs + finisherLingerMs;
                long damageTickMs = timing.duration(TheaterTowerLiangyue.SKILL3_DAMAGE_TICK_MS);

                Vec3 rawCenter = e.position().add(dir.scale(3.0));
                Vec3 clampedCenter = clampStormCenter(d.cell, rawCenter);
                Vec3 stormCenter = AwakeningTowerLayout.ground(owner.level(), d.cell, clampedCenter, AwakeningTowerLayout.guard(d.cell));
                long seed = random.nextLong();

                ActiveCast cast = new ActiveCast(skill);
                cast.startAt = now;
                cast.endAt = now + totalMs;
                cast.windupMs = deployMs;
                cast.activeMs = slashIntervalMs;
                cast.auxMs = slashPhaseMs;
                cast.stormSeed = seed;
                cast.anchor = stormCenter;
                cast.direction = dir;
                s.activeCast = cast;

                e.getNavigation().stop();
                e.setNoGravity(true);
                e.setPos(stormCenter.x, stormCenter.y, stormCenter.z);
                e.setDeltaMovement(Vec3.ZERO);
                facePosition(e, stormCenter.add(dir.scale(6.0)));
                e.play("storm_awakening", totalMs);

                broadcastEvent(instanceId, Map.of(
                    "kind", "STORM_BANNER",
                    "instance", instanceId,
                    "durationMs", totalMs
                ));
                String hid = instanceId + "/storm/" + (++s.sequence);
                owner.broadcast(instanceId, "theaterBossHazard", hazardPacket(
                    instanceId, hid, "STORM_AWAKENING", t,
                    stormCenter, stormCenter.add(dir),
                    TheaterTowerLiangyue.SKILL3_RADIUS, TheaterTowerLiangyue.SKILL3_FINISHER_RADIUS,
                    now, now + deployMs, now + totalMs, damageTickMs,
                    TheaterTowerLiangyue.COEFF_STORM_SLASH, TheaterTowerLiangyue.COEFF_STORM_FINISHER, 0.0,
                    now + deployMs + slashPhaseMs, now + deployMs + slashPhaseMs + finisherDelayMs,
                    slashIntervalMs, seed,
                    0x7ae8ff
                ));
            }
            case PHANTOM_SWORD_DANCE -> {
                long windupMs = timing.windup(TheaterTowerLiangyue.SKILL4_WINDUP_MS);
                long flurryMs = timing.active(TheaterTowerLiangyue.SKILL4_FLURRY_MS);
                long waveIntervalMs = Math.max(150L, flurryMs / TheaterTowerLiangyue.SKILL4_WAVE_COUNT);
                long meleeTickMs = timing.duration(TheaterTowerLiangyue.SKILL4_MELEE_TICK_MS);

                ActiveCast cast = new ActiveCast(skill);
                cast.startAt = now;
                cast.nextAt = now + windupMs;
                cast.endAt = now + windupMs + flurryMs;
                cast.windupMs = windupMs;
                cast.activeMs = flurryMs;
                cast.auxMs = waveIntervalMs;
                cast.step = 0;
                cast.totalSteps = TheaterTowerLiangyue.SKILL4_WAVE_COUNT;
                cast.subPhase = 0;
                cast.direction = dir;
                s.activeCast = cast;

                e.getNavigation().stop();
                facePosition(e, targetPlayer.position());
                e.play("phantom_dance_windup", windupMs);

                broadcastEvent(instanceId, Map.of(
                    "kind", "DANCE_BANNER",
                    "instance", instanceId,
                    "durationMs", windupMs + flurryMs + timing.windup(TheaterTowerLiangyue.SKILL4_FINISHER_WARNING_MS)
                ));
                Vec3 origin = AwakeningTowerLayout.ground(owner.level(), d.cell, e.position(), e.position());
                String hid = instanceId + "/dance_flurry/" + (++s.sequence);
                owner.broadcast(instanceId, "theaterBossHazard", hazardPacket(
                    instanceId, hid, "DANCE_FLURRY", t,
                    origin, origin.add(dir),
                    TheaterTowerLiangyue.SKILL4_MELEE_RADIUS, 0.0,
                    now, now + windupMs, now + windupMs + flurryMs, meleeTickMs,
                    TheaterTowerLiangyue.COEFF_DANCE_SLASH, 0.0, 0.0, 0L, 0L, 0L, 0L,
                    0xa6f2ff
                ));
            }
        }
    }

    private void tickActiveCast(Dungeon d, DungeonEnemy e, Target t, State s, List<ServerPlayer> fighters, long now) {
        ActiveCast cast = s.activeCast;
        if (cast == null) return;
        Target.ActionTiming timing = t.actionTiming(now);

        switch (cast.skill) {
            case TRIPLE_SLASH_WAVE -> {
                if (now >= cast.nextAt) {
                    if (cast.step >= cast.totalSteps) {
                        s.activeCast = null;
                        s.busyUntil = now + timing.recovery(TheaterTowerLiangyue.SKILL1_RECOVERY_MS);
                        return;
                    }
                    fireTripleSlashStrike(d, e, t, s, cast, fighters, timing, now);
                }
            }
            case RAGING_DRAGON -> {
                if (cast.subPhase == 0) {
                    facePosition(e, cast.to);
                    if (now >= cast.nextAt) {
                        cast.subPhase = 1;
                        e.setNoGravity(true);
                        e.play("raging_dragon_dash", Math.max(100L, cast.endAt - now));
                    }
                    return;
                }
                double progress = Math.clamp((now - cast.nextAt) / (double) Math.max(1L, cast.endAt - cast.nextAt), 0.0, 1.0);
                Vec3 pos = cast.from.lerp(cast.to, progress);
                e.setPos(pos.x, pos.y + Math.sin(progress * Math.PI) * 0.35, pos.z);
                e.setDeltaMovement(Vec3.ZERO);
                facePosition(e, cast.to);
                if (now >= cast.endAt) {
                    landToGround(e, d.cell, cast.to);
                    spawnDragonWhirlwinds(d, t, s, cast.from, cast.to, timing, now);
                    ServerPlayer aimTarget = nearestFighter(e, fighters);
                    Vec3 waveDir = aimTarget != null ? horizontalDirection(e.position(), aimTarget.position()) : cast.direction;
                    facePosition(e, e.position().add(waveDir.scale(8.0)));
                    emitSwordWave(d, e, t, s, "SWORD_WAVE_LARGE", waveDir,
                        TheaterTowerLiangyue.SKILL2_WAVE_RANGE, TheaterTowerLiangyue.SKILL2_WAVE_WIDTH,
                        timing.windup(TheaterTowerLiangyue.SKILL2_WAVE_WARNING_MS),
                        timing.active(TheaterTowerLiangyue.SKILL2_WAVE_TRAVEL_MS),
                        TheaterTowerLiangyue.COEFF_DRAGON_WAVE, 0.0, 0x8ef2ff, now);
                    e.play("raging_dragon_wave", timing.recovery(TheaterTowerLiangyue.SKILL2_RECOVERY_MS));
                    s.activeCast = null;
                    s.busyUntil = now + timing.recovery(TheaterTowerLiangyue.SKILL2_RECOVERY_MS);
                }
            }
            case STORM_AWAKENING -> {
                long elapsed = now - cast.startAt;
                Vec3 offset = TheaterTowerLiangyue.stormActorOffset(cast.stormSeed, elapsed, cast.windupMs, cast.activeMs);
                Vec3 pos = cast.anchor.add(offset);
                e.setPos(pos.x, pos.y, pos.z);
                e.setDeltaMovement(Vec3.ZERO);
                if (elapsed >= cast.windupMs && elapsed < cast.windupMs + cast.auxMs) {
                    Vec3 nextOffset = TheaterTowerLiangyue.stormActorOffset(cast.stormSeed, elapsed + 60L, cast.windupMs, cast.activeMs);
                    Vec3 heading = nextOffset.subtract(offset);
                    if (heading.horizontalDistanceSqr() > 1e-4) {
                        facePosition(e, pos.add(heading));
                    }
                } else {
                    facePosition(e, cast.anchor.add(cast.direction.scale(6.0)));
                }
                if (now >= cast.endAt) {
                    landToGround(e, d.cell, cast.anchor);
                    s.activeCast = null;
                    s.busyUntil = now + timing.recovery(TheaterTowerLiangyue.SKILL3_RECOVERY_MS);
                }
            }
            case PHANTOM_SWORD_DANCE -> {
                ServerPlayer nearest = nearestFighter(e, fighters);
                if (nearest != null) {
                    cast.direction = horizontalDirection(e.position(), nearest.position());
                    facePosition(e, nearest.position());
                }
                if (cast.subPhase == 0) {
                    if (now >= cast.nextAt) {
                        cast.subPhase = 1;
                        e.play("phantom_dance_flurry", Math.max(200L, cast.endAt - now));
                    } else {
                        return;
                    }
                }
                if (cast.subPhase == 1) {
                    while (cast.step < cast.totalSteps && now >= cast.nextAt) {
                        double angle = (cast.step % 2 == 0 ? -1.0 : 1.0) * (cast.step < 2 ? 32.0 : cast.step < 4 ? 18.0 : 0.0);
                        emitSwordWave(d, e, t, s, "SWORD_WAVE", cast.direction,
                            TheaterTowerLiangyue.SKILL4_WAVE_RANGE, TheaterTowerLiangyue.SKILL4_WAVE_WIDTH,
                            timing.windup(TheaterTowerLiangyue.SKILL4_WAVE_WARNING_MS),
                            timing.active(TheaterTowerLiangyue.SKILL4_WAVE_TRAVEL_MS),
                            TheaterTowerLiangyue.COEFF_DANCE_WAVE, angle, 0xa8f0ff, now);
                        cast.step++;
                        cast.nextAt += cast.auxMs;
                    }
                    if (now >= cast.endAt && cast.step >= cast.totalSteps) {
                        emitSwordWave(d, e, t, s, "SWORD_WAVE_FINISHER", cast.direction,
                            TheaterTowerLiangyue.SKILL4_FINISHER_RANGE, TheaterTowerLiangyue.SKILL4_FINISHER_WIDTH,
                            timing.windup(TheaterTowerLiangyue.SKILL4_FINISHER_WARNING_MS),
                            timing.active(TheaterTowerLiangyue.SKILL4_FINISHER_TRAVEL_MS),
                            TheaterTowerLiangyue.COEFF_DANCE_FINISHER, 0.0, 0xd8fcff, now);
                        long recovery = timing.recovery(TheaterTowerLiangyue.SKILL4_RECOVERY_MS);
                        e.play("phantom_dance_finisher", recovery);
                        s.activeCast = null;
                        s.busyUntil = now + recovery;
                    }
                }
            }
        }
    }

    private void fireTripleSlashStrike(Dungeon d, DungeonEnemy e, Target t, State s, ActiveCast cast,
                                       List<ServerPlayer> fighters, Target.ActionTiming timing, long now) {
        ServerPlayer targetPlayer = nearestFighter(e, fighters);
        if (targetPlayer != null) {
            cast.direction = horizontalDirection(e.position(), targetPlayer.position());
            facePosition(e, targetPlayer.position());
        }
        double angle = TheaterTowerLiangyue.SKILL1_SLASH_ANGLES[Math.min(cast.step, TheaterTowerLiangyue.SKILL1_SLASH_ANGLES.length - 1)];
        emitSwordWave(d, e, t, s, "SWORD_WAVE_LARGE", cast.direction,
            TheaterTowerLiangyue.SKILL1_WAVE_RANGE, TheaterTowerLiangyue.SKILL1_WAVE_WIDTH,
            timing.windup(TheaterTowerLiangyue.SKILL1_WAVE_WARNING_MS),
            timing.active(TheaterTowerLiangyue.SKILL1_WAVE_TRAVEL_MS),
            TheaterTowerLiangyue.COEFF_SWORD_WAVE, angle, 0x7ce6ff, now);
        e.play("triple_slash_" + (cast.step + 1), cast.auxMs);
        cast.step++;
        cast.nextAt = now + cast.auxMs;
    }

    private void emitSwordWave(Dungeon d, DungeonEnemy e, Target t, State s, String type, Vec3 dir,
                               double range, double width, long warnMs, long travelMs,
                               double coeff, double slashAngle, int color, long now) {
        String instanceId = d.id.toString();
        Vec3 from = AwakeningTowerLayout.ground(owner.level(), d.cell, e.position(), e.position());
        Vec3 to = from.add(dir.scale(range));
        String hid = instanceId + "/wave/" + (++s.sequence);
        owner.broadcast(instanceId, "theaterBossHazard", hazardPacket(
            instanceId, hid, type, t,
            from, to, range, width,
            now, now + warnMs, now + warnMs + travelMs, 0L,
            coeff, 0.0, slashAngle, 0L, 0L, 0L, 0L,
            color
        ));
    }

    private void spawnDragonWhirlwinds(Dungeon d, Target t, State s, Vec3 from, Vec3 to,
                                       Target.ActionTiming timing, long now) {
        String instanceId = d.id.toString();
        ServerLevel world = owner.level();
        Vec3 forward = horizontalDirection(from, to);
        Vec3 right = new Vec3(-forward.z, 0, forward.x);
        double[] fractions = {0.25, 0.55, 0.85};
        double[] lateralOffsets = {-2.2, 2.2, 0.0};
        long warnMs = timing.windup(TheaterTowerLiangyue.SKILL2_WHIRLWIND_WARNING_MS);
        long warnUntil = now + warnMs;
        long endAt = warnUntil + TheaterTowerLiangyue.SKILL2_WHIRLWIND_DURATION_MS;

        for (int i = 0; i < TheaterTowerLiangyue.SKILL2_WHIRLWIND_COUNT; i++) {
            Vec3 raw = from.lerp(to, fractions[i]).add(right.scale(lateralOffsets[i]));
            Vec3 pos = AwakeningTowerLayout.ground(world, d.cell, raw, to);
            String hid = instanceId + "/whirlwind/" + (++s.sequence);
            s.whirlwinds.add(new WhirlwindHazard(hid, i, pos, now, warnUntil, endAt));
            owner.broadcast(instanceId, "theaterBossHazard", hazardPacket(
                instanceId, hid, "WHIRLWIND", t,
                pos, pos, TheaterTowerLiangyue.SKILL2_WHIRLWIND_RADIUS, 0.0,
                now, warnUntil, endAt, TheaterTowerLiangyue.SKILL2_WHIRLWIND_TICK_MS,
                TheaterTowerLiangyue.COEFF_WHIRLWIND_DOT, 0.0, 0.0, 0L, 0L, 0L, 0L,
                0x9ef5ff
            ));
        }
    }

    private void tickWhirlwinds(Dungeon d, State s, List<ServerPlayer> fighters, long now) {
        if (s.whirlwinds.isEmpty()) return;
        ServerLevel world = owner.level();
        String instanceId = d.id.toString();
        var it = s.whirlwinds.iterator();
        while (it.hasNext()) {
            WhirlwindHazard w = it.next();
            if (now >= w.endAt) {
                it.remove();
                continue;
            }
            if (now < w.warnUntil) continue;
            long dtMs = Math.max(0L, now - w.lastMoveAt);
            w.lastMoveAt = now;
            ServerPlayer target = nearestFighterToPoint(w.pos, fighters);
            if (target != null && dtMs > 0L) {
                // 三道旋风自带微小环绕分离角，追击同时保持清晰可辨认
                double sepAngle = (w.laneIndex * Math.PI * 2.0 / 3.0) + now * 0.0012;
                Vec3 goal = target.position().add(Math.cos(sepAngle) * 0.65, 0, Math.sin(sepAngle) * 0.65);
                Vec3 delta = new Vec3(goal.x - w.pos.x, 0, goal.z - w.pos.z);
                double dist = delta.length();
                if (dist > 0.08) {
                    double step = Math.min(dist, TheaterTowerLiangyue.SKILL2_WHIRLWIND_SPEED * (dtMs / 1000.0));
                    Vec3 next = w.pos.add(delta.scale(step / dist));
                    w.pos = AwakeningTowerLayout.ground(world, d.cell, next, w.pos);
                }
            }
            // 严格遵守服务器最多每秒 4 次（>= 250ms）同步约束
            if (now - w.lastBroadcastAt >= TheaterTowerLiangyue.SKILL2_WHIRLWIND_SYNC_INTERVAL_MS) {
                w.lastBroadcastAt = now;
                JsonObject move = new JsonObject();
                move.addProperty("instance", instanceId);
                move.addProperty("id", w.id);
                move.addProperty("moveOnly", true);
                move.addProperty("moveDuration", TheaterTowerLiangyue.SKILL2_WHIRLWIND_SYNC_INTERVAL_MS);
                move.addProperty("x", w.pos.x);
                move.addProperty("y", w.pos.y);
                move.addProperty("z", w.pos.z);
                owner.broadcast(instanceId, "theaterBossHazard", move);
            }
        }
    }

    /** 计算猛龙断空斩落点：默认沿方向直进 20 格，贴近边界时安全收束在战场可通行区域内。 */
    public static Vec3 computeDragonEnd(ServerLevel world, int cell, Vec3 start, Vec3 direction, double maxDistance) {
        Vec3 dir = direction.multiply(1, 0, 1);
        if (dir.lengthSqr() < 1e-6) dir = new Vec3(0, 0, 1);
        else dir = dir.normalize();
        Vec3 rawEnd = AwakeningTowerLayout.insideBattle(cell, start.add(dir.scale(maxDistance)));
        if (world == null) return rawEnd;
        double clampedDist = rawEnd.subtract(start).horizontalDistance();
        for (double dist = clampedDist; dist >= 1.0; dist -= 1.0) {
            Vec3 candidate = AwakeningTowerLayout.insideBattle(cell, start.add(dir.scale(dist)));
            Vec3 ground = AwakeningTowerLayout.ground(world, cell, candidate, null);
            if (ground != null) return ground;
        }
        return start;
    }

    /** 确保暴风式 24 剑阵半径完整落在剧场塔主战场内。 */
    public static Vec3 clampStormCenter(int cell, Vec3 raw) {
        double minX = cell * 512.0 - 38.0;
        double maxX = cell * 512.0 + 38.0;
        return new Vec3(
            Math.clamp(raw.x, minX, maxX),
            Math.clamp(raw.y, 78.0, 100.0),
            Math.clamp(raw.z, 12.0, 90.0)
        );
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
        return nearestFighterToPoint(e.position(), fighters);
    }

    private static ServerPlayer nearestFighterToPoint(Vec3 point, List<ServerPlayer> fighters) {
        ServerPlayer best = null;
        double bestSq = Double.POSITIVE_INFINITY;
        for (ServerPlayer f : fighters) {
            double sq = f.position().distanceToSqr(point);
            if (sq < bestSq) {
                bestSq = sq;
                best = f;
            }
        }
        return best;
    }

    private JsonObject hazardPacket(String instanceId, String id, String type, Target boss,
                                    Vec3 from, Vec3 to, double radius, double width,
                                    long startAt, long warnUntil, long endAt, long tickMs,
                                    double coefficient, double finisherCoefficient, double slashAngle,
                                    long slashEndAt, long finisherAt, long slashIntervalMs, long seed,
                                    int color) {
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
        o.addProperty("startAt", startAt);
        o.addProperty("warnUntil", warnUntil);
        o.addProperty("endAt", endAt);
        o.addProperty("tickMs", tickMs);
        o.addProperty("coefficient", coefficient);
        o.addProperty("finisherCoefficient", finisherCoefficient);
        o.addProperty("slashAngle", slashAngle);
        o.addProperty("slashEndAt", slashEndAt);
        o.addProperty("finisherAt", finisherAt);
        o.addProperty("slashIntervalMs", slashIntervalMs);
        o.addProperty("seed", seed);
        o.addProperty("magic", false);
        o.addProperty("element", TheaterTowerLiangyue.ELEMENT_NONE);
        o.addProperty("color", color);
        return o;
    }

    private void broadcastEvent(String instanceId, Map<String, Object> payload) {
        owner.broadcast(instanceId, "theaterBossEvent", payload);
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
        if (s == null) return;
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
        Network.send(player, "theaterBossEvent", Map.of(
            "kind", "CLEAR_ENCOUNTER_EFFECTS",
            "instance", d.id.toString()
        ));
    }

    private void cleanupEncounter(String instanceId) {
        broadcastEvent(instanceId, Map.of("kind", "CLEAR_HAZARDS", "instance", instanceId));
        broadcastEvent(instanceId, Map.of("kind", "CLEAR_ENCOUNTER_EFFECTS", "instance", instanceId));
    }
}
