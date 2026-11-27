package cn.yumis.fight.modules.dungeons.client;

import cn.yumis.fight.modules.combat.api.ElementAccumulation;
import cn.yumis.fight.modules.combat.api.Life;
import cn.yumis.fight.modules.dungeons.api.Target;
import cn.yumis.fight.modules.dungeons.content.FrostTowerFrostnova;
import cn.yumis.fight.modules.presentation.api.client.ClientUi;
import cn.yumis.fight.modules.presentation.api.client.UiFonts;
import cn.yumis.fight.modules.presentation.api.client.UiTheme;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.*;

/**
 * 霜塔第 20 层首领「霜星」本机客户端权威控制器：
 * - 模拟背后 24/31 枚连射冰刺（每 0.1s 发射一枚直线飞行）与 30° 扇形 3/4 枚直线冰锥（不再追踪）；
 * - 结算爆发、寒霜领域（含爆发共振引爆与减速）、冰棱折跃环刺、极寒星爆与转阶段落地冲击；
 * - 命中已冻结玩家时触发「碎冰共振」增伤与音效反馈；
 * - 离场、换层、死亡或断线时彻底清理所有残留冰刺、领域与横幅。
 */
public final class FrostnovaBossClient {
    private static final Gson JSON = new Gson();

    public interface Callbacks {
        Life own();
        void bossContact(JsonObject hitPacket);
    }

    static final class Spike {
        final String id;
        final String instance;
        final Target boss;
        final int bossEntityId;
        final boolean backAttached;
        final int slotIndex;
        final int slotTotal;
        final long startAt;
        final long launchAt;
        final long endAt;
        final double speed;
        final double turnDegPerSec;
        final double hitRadius;
        final double coefficient;
        final double initialYawOffsetDeg;
        final boolean aimOnLaunch;
        final boolean phase2;
        final int color;

        Vec3 pos;
        Vec3 prevPos;
        Vec3 dir;
        long lastSimAt;
        boolean launched;
        boolean consumed;

        Spike(String id, String instance, Target boss, int bossEntityId,
              boolean backAttached, int slotIndex, int slotTotal,
              Vec3 initialPos, Vec3 initialDir,
              long startAt, long launchAt, long endAt,
              double speed, double turnDegPerSec, double hitRadius, double coefficient,
              double initialYawOffsetDeg, boolean aimOnLaunch, boolean phase2, int color) {
            this.id = id;
            this.instance = instance;
            this.boss = boss;
            this.bossEntityId = bossEntityId;
            this.backAttached = backAttached;
            this.slotIndex = slotIndex;
            this.slotTotal = slotTotal;
            this.pos = initialPos;
            this.prevPos = initialPos;
            this.dir = initialDir.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : initialDir.normalize();
            this.startAt = startAt;
            this.launchAt = launchAt;
            this.endAt = endAt;
            this.speed = speed;
            this.turnDegPerSec = turnDegPerSec;
            this.hitRadius = hitRadius;
            this.coefficient = coefficient;
            this.initialYawOffsetDeg = initialYawOffsetDeg;
            this.aimOnLaunch = aimOnLaunch;
            this.phase2 = phase2;
            this.color = color;
            this.lastSimAt = launchAt;
        }
    }

    static final class AreaHazard {
        final String id;
        final String instance;
        final String type;
        Target boss;
        final Vec3 from;
        final Vec3 to;
        final double radius;
        final long startAt;
        final long warnUntil;
        long endAt;
        final long intervalMs;
        final double coefficient;
        final boolean phase2;
        final int color;

        long detonateAt;
        double detonateCoefficient;
        boolean struck;
        boolean detonated;
        boolean soundPlayed;
        int lastPulse = -1;

        AreaHazard(String id, String instance, String type, Target boss, Vec3 from, Vec3 to,
                   double radius, long startAt, long warnUntil, long endAt, long intervalMs,
                   double coefficient, boolean phase2, int color) {
            this.id = id;
            this.instance = instance;
            this.type = type;
            this.boss = boss;
            this.from = from;
            this.to = to;
            this.radius = radius;
            this.startAt = startAt;
            this.warnUntil = warnUntil;
            this.endAt = endAt;
            this.intervalMs = intervalMs;
            this.coefficient = coefficient;
            this.phase2 = phase2;
            this.color = color;
        }
    }

    static final class ReviveVisual {
        final Vec3 anchor;
        final long startAt;
        final long endAt;

        ReviveVisual(Vec3 anchor, long startAt, long endAt) {
            this.anchor = anchor;
            this.startAt = startAt;
            this.endAt = endAt;
        }
    }

    private static Callbacks callbacks;
    private static Object world;
    private static String activeInstance = "";
    private static String bannerTitle = "";
    private static long bannerUntil = 0L;
    private static int bannerColor = 0xff9eeeff;

    static final Map<String, Spike> spikes = new LinkedHashMap<>();
    static final Map<String, AreaHazard> areas = new LinkedHashMap<>();
    static ReviveVisual reviveVisual;

    private FrostnovaBossClient() {}

    public static void register(Callbacks cb) {
        callbacks = Objects.requireNonNull(cb);
        NeoForge.EVENT_BUS.addListener(FrostnovaBossClient::tick);
    }

    public static void clear() {
        spikes.clear();
        areas.clear();
        reviveVisual = null;
        bannerTitle = "";
        bannerUntil = 0L;
        activeInstance = "";
        world = null;
    }

    private static void syncContext() {
        var mc = Minecraft.getInstance();
        Object nextWorld = mc.level;
        String currentInstance = callbacks == null ? "" : callbacks.own().instance;
        if (world != nextWorld || (!activeInstance.isEmpty() && !activeInstance.equals(currentInstance))) {
            spikes.clear();
            areas.clear();
            reviveVisual = null;
            bannerTitle = "";
            bannerUntil = 0L;
            activeInstance = currentInstance;
            world = nextWorld;
        } else if (activeInstance.isEmpty() && !currentInstance.isEmpty()) {
            activeInstance = currentInstance;
            world = nextWorld;
        }
    }

    public static void onEvent(JsonObject pkt) {
        if (callbacks == null) return;
        syncContext();
        String kind = pkt.get("kind").getAsString();
        if ("CLEAR_HAZARDS".equals(kind) || "CLEAR_ENCOUNTER_EFFECTS".equals(kind)) {
            spikes.clear();
            areas.clear();
            if ("CLEAR_ENCOUNTER_EFFECTS".equals(kind)) {
                reviveVisual = null;
                bannerTitle = "";
                bannerUntil = 0L;
            }
            return;
        }
        String instance = pkt.has("instance") ? pkt.get("instance").getAsString() : "";
        if (instance.isEmpty() || !instance.equals(callbacks.own().instance)) return;
        activeInstance = instance;

        long now = ClientUi.now();
        long serverNow = ClientUi.current().serverNow();
        long duration = pkt.has("durationMs") ? pkt.get("durationMs").getAsLong() : 3_000L;

        switch (kind) {
            case "PHASE_TRANSITION" -> {
                Vec3 anchor = new Vec3(pkt.get("x").getAsDouble(), pkt.get("y").getAsDouble(), pkt.get("z").getAsDouble());
                reviveVisual = new ReviveVisual(anchor, serverNow, serverNow + duration);
                bannerTitle = "霜星 · 极寒苏醒";
                bannerUntil = now + duration;
                bannerColor = 0xffc4f6ff;
                playLocalSound(SoundEvents.BEACON_DEACTIVATE, 0.9f, 0.65f);
            }
            case "PHASE2_ENTERED" -> {
                reviveVisual = null;
                bannerTitle = "霜星 · 凛冬降临（二阶段）";
                bannerUntil = now + duration;
                bannerColor = 0xffe4fcff;
                playLocalSound(SoundEvents.BEACON_POWER_SELECT, 0.95f, 0.85f);
            }
            case "BARRAGE_BANNER" -> {
                int count = pkt.has("count") ? pkt.get("count").getAsInt() : 24;
                bannerTitle = "寒冰箭幕 · " + count + " 枚冰刺";
                bannerUntil = now + duration;
                bannerColor = 0xff98ecff;
                playLocalSound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.85f, 1.15f);
            }
            case "NOVA_BANNER" -> {
                bannerTitle = "极寒星爆";
                bannerUntil = now + duration;
                bannerColor = 0xffd4fbff;
                playLocalSound(SoundEvents.BEACON_ACTIVATE, 0.9f, 1.1f);
            }
            case "DETONATE_FIELDS" -> {
                long detonateAt = pkt.get("detonateAt").getAsLong();
                double coeff = pkt.get("coefficient").getAsDouble();
                Target boss = JSON.fromJson(pkt.get("boss"), Target.class);
                for (AreaHazard a : areas.values()) {
                    if ("FROST_FIELD".equals(a.type) && !a.detonated && a.detonateAt == 0L && serverNow < a.endAt) {
                        a.detonateAt = detonateAt;
                        a.detonateCoefficient = coeff;
                        a.boss = boss;
                        a.endAt = Math.min(a.endAt, detonateAt + 420L);
                    }
                }
            }
            default -> {}
        }
    }

    public static void onHazard(JsonObject pkt) {
        if (callbacks == null) return;
        syncContext();
        String instance = pkt.has("instance") ? pkt.get("instance").getAsString() : "";
        if (instance.isEmpty() || !instance.equals(callbacks.own().instance)) return;
        activeInstance = instance;

        String id = pkt.get("id").getAsString();
        String type = pkt.get("type").getAsString();
        Target boss = JSON.fromJson(pkt.get("boss"), Target.class);
        int bossEntityId = pkt.has("bossEntityId") ? pkt.get("bossEntityId").getAsInt() : -1;
        Vec3 from = new Vec3(pkt.get("x").getAsDouble(), pkt.get("y").getAsDouble(), pkt.get("z").getAsDouble());
        Vec3 to = new Vec3(pkt.get("tx").getAsDouble(), pkt.get("ty").getAsDouble(), pkt.get("tz").getAsDouble());
        double radius = pkt.get("radius").getAsDouble();
        double speed = pkt.get("speed").getAsDouble();
        long startAt = pkt.get("startAt").getAsLong();
        long warnUntil = pkt.get("warnUntil").getAsLong();
        long endAt = pkt.get("endAt").getAsLong();
        long intervalMs = pkt.get("intervalMs").getAsLong();
        double coefficient = pkt.get("coefficient").getAsDouble();
        int count = pkt.get("count").getAsInt();
        long spikeLifeMs = pkt.get("spikeLifeMs").getAsLong();
        double spreadDeg = pkt.get("spreadDeg").getAsDouble();
        double turnDegPerSec = pkt.get("turnDegPerSec").getAsDouble();
        double baseAngle = pkt.get("baseAngle").getAsDouble();
        boolean phase2 = pkt.has("phase2") && pkt.get("phase2").getAsBoolean();
        int color = pkt.get("color").getAsInt();

        switch (type) {
            case "BACK_BARRAGE" -> {
                Vec3 aimDir = horizontalDir(from, to);
                for (int i = 0; i < count; i++) {
                    long launchAt = warnUntil + i * Math.max(20L, intervalMs);
                    Vec3 slotPos = computeBackSlotPos(bossEntityId, from, aimDir, i, count);
                    String sid = id + "/s" + i;
                    spikes.put(sid, new Spike(
                        sid, instance, boss, bossEntityId,
                        true, i, count,
                        slotPos, aimDir,
                        startAt, launchAt, launchAt + spikeLifeMs,
                        speed, turnDegPerSec, radius, coefficient,
                        0.0, true, phase2, color
                    ));
                }
            }
            case "HOMING_FAN" -> {
                Vec3 baseDir = horizontalDir(from, to);
                for (int i = 0; i < count; i++) {
                    double yawOffset = FrostTowerFrostnova.fanAngleDeg(i, count, spreadDeg);
                    Vec3 dir = rotateHorizontal(baseDir, yawOffset);
                    Vec3 spawnPos = from.add(dir.scale(0.8));
                    String sid = id + "/f" + i;
                    spikes.put(sid, new Spike(
                        sid, instance, boss, bossEntityId,
                        false, i, count,
                        spawnPos, dir,
                        startAt, warnUntil, warnUntil + spikeLifeMs,
                        speed, turnDegPerSec, radius, coefficient,
                        yawOffset, true, phase2, color
                    ));
                }
            }
            case "BLINK_CRYSTAL" -> {
                // 原位冰晶视觉本体 + 延时后环形齐射冰刺
                areas.put(id, new AreaHazard(
                    id, instance, "BLINK_CRYSTAL", boss, from, to,
                    1.6, startAt, warnUntil, warnUntil + 380L, 0L,
                    0.0, phase2, color
                ));
                Vec3 crystalOrigin = from.add(0, 1.2, 0);
                for (int i = 0; i < count; i++) {
                    double angle = baseAngle + i * (Math.PI * 2.0 / Math.max(1, count));
                    Vec3 dir = new Vec3(Math.cos(angle), 0, Math.sin(angle));
                    String sid = id + "/r" + i;
                    spikes.put(sid, new Spike(
                        sid, instance, boss, bossEntityId,
                        false, i, count,
                        crystalOrigin.add(dir.scale(0.55)), dir,
                        startAt, warnUntil, warnUntil + spikeLifeMs,
                        speed, 0.0, radius, coefficient,
                        0.0, false, phase2, color
                    ));
                }
            }
            case "NOVA_SPIRAL" -> {
                for (int i = 0; i < count; i++) {
                    double angle = baseAngle + i * (Math.PI * 2.0 / Math.max(1, count));
                    Vec3 dir = new Vec3(Math.cos(angle), -0.18, Math.sin(angle)).normalize();
                    String sid = id + "/n" + i;
                    spikes.put(sid, new Spike(
                        sid, instance, boss, bossEntityId,
                        false, i, count,
                        from.add(dir.scale(1.1)), dir,
                        startAt, warnUntil, warnUntil + spikeLifeMs,
                        speed, turnDegPerSec, radius, coefficient,
                        0.0, false, phase2, color
                    ));
                }
            }
            default -> areas.put(id, new AreaHazard(
                id, instance, type, boss, from, to,
                radius, startAt, warnUntil, endAt, intervalMs,
                coefficient, phase2, color
            ));
        }
    }

    private static void tick(ClientTickEvent.Post event) {
        if (callbacks == null) return;
        syncContext();
        var mc = Minecraft.getInstance();
        var player = mc.player;
        if (mc.level == null || player == null || !ClientUi.current().ready() || mc.isPaused()) return;

        long serverNow = ClientUi.current().serverNow();
        if (reviveVisual != null && serverNow >= reviveVisual.endAt + 400L) {
            reviveVisual = null;
        }
        spikes.values().removeIf(s -> s.consumed || serverNow >= s.endAt + 200L);
        areas.values().removeIf(a -> serverNow >= a.endAt + 500L);

        boolean canHit = !callbacks.own().dead && !player.isDeadOrDying() && !TowerClient.blocksCombat();
        Vec3 playerAim = player.position().add(0, player.getBbHeight() * 0.55, 0);

        // 1. 推进全部冰刺（背后凝结 -> 定时离弦 -> 直线飞行 -> 连续线段受击判定）
        for (Spike s : new ArrayList<>(spikes.values())) {
            if (s.consumed || !s.instance.equals(callbacks.own().instance)) continue;

            if (serverNow < s.launchAt) {
                if (s.backAttached) {
                    Vec3 bossPos = resolveBossPos(s.bossEntityId, s.pos);
                    Vec3 bossForward = resolveBossForward(s.bossEntityId, s.dir);
                    s.pos = computeBackSlotPos(s.bossEntityId, bossPos, bossForward, s.slotIndex, s.slotTotal);
                    s.prevPos = s.pos;
                    Vec3 aim = playerAim.subtract(s.pos);
                    if (aim.lengthSqr() > 1e-4) s.dir = aim.normalize();
                } else if (s.aimOnLaunch) {
                    Vec3 bossPos = resolveBossPos(s.bossEntityId, s.pos);
                    Vec3 baseDir = horizontalDir(bossPos, player.position());
                    Vec3 dir = rotateHorizontal(baseDir, s.initialYawOffsetDeg);
                    s.pos = bossPos.add(0, 1.35, 0).add(dir.scale(0.85));
                    s.prevPos = s.pos;
                    s.dir = dir;
                }
                s.lastSimAt = s.launchAt;
                continue;
            }

            if (!s.launched) {
                s.launched = true;
                s.lastSimAt = s.launchAt;
                if (s.backAttached) {
                    Vec3 bossPos = resolveBossPos(s.bossEntityId, s.pos);
                    Vec3 bossForward = resolveBossForward(s.bossEntityId, s.dir);
                    s.pos = computeBackSlotPos(s.bossEntityId, bossPos, bossForward, s.slotIndex, s.slotTotal);
                    Vec3 aim = playerAim.subtract(s.pos);
                    if (aim.lengthSqr() > 1e-4) s.dir = aim.normalize();
                    if (s.slotIndex % 2 == 0) {
                        playAt(s.pos, SoundEvents.AMETHYST_CLUSTER_BREAK, 0.35f, 1.35f + (s.slotIndex % 5) * 0.06f);
                    }
                } else if (s.aimOnLaunch) {
                    Vec3 bossPos = resolveBossPos(s.bossEntityId, s.pos);
                    Vec3 baseDir = horizontalDir(bossPos, player.position());
                    s.dir = rotateHorizontal(baseDir, s.initialYawOffsetDeg);
                    s.pos = bossPos.add(0, 1.35, 0).add(s.dir.scale(0.85));
                    if (s.slotIndex == 0) {
                        playAt(s.pos, SoundEvents.TRIDENT_THROW.value(), 0.7f, 1.35f);
                    }
                } else if (s.slotIndex == 0) {
                    playAt(s.pos, SoundEvents.GLASS_BREAK, 0.55f, 1.25f);
                }
                s.prevPos = s.pos;
            }

            long dtMs = Math.max(0L, Math.min(120L, serverNow - s.lastSimAt));
            s.lastSimAt = serverNow;
            if (dtMs <= 0L) continue;
            double dtSec = dtMs / 1000.0;

            s.prevPos = s.pos;
            if (s.turnDegPerSec > 0.0) {
                Vec3 toPlayer = playerAim.subtract(s.pos);
                s.dir = FrostTowerFrostnova.stepHomingDirection(s.dir, toPlayer, s.turnDegPerSec, dtSec);
            }
            s.pos = s.pos.add(s.dir.scale(s.speed * dtSec));

            // 坠地或飞离战场高度时消散
            if (s.pos.y < 76.0 || s.pos.y > 114.0) {
                s.consumed = true;
                continue;
            }

            if (canHit && s.coefficient > 0) {
                AABB box = player.getBoundingBox().inflate(s.hitRadius * 0.52, 0.35, s.hitRadius * 0.52);
                if (box.contains(s.prevPos) || box.contains(s.pos) || box.clip(s.prevPos, s.pos).isPresent()) {
                    s.consumed = true;
                    playAt(s.pos, SoundEvents.GLASS_BREAK, 0.55f, 1.35f);
                    emitHit(s.boss, s.id, "ICE_SPIKE", s.coefficient, serverNow, false);
                }
            }
        }

        // 2. 推进全部区域类伤害（爆发、落地冲击、寒霜领域与共振引爆、巨型冰锥）
        for (AreaHazard a : new ArrayList<>(areas.values())) {
            if (!a.instance.equals(callbacks.own().instance)) continue;

            switch (a.type) {
                case "FROST_BURST", "REVIVE_SHOCKWAVE", "NOVA_ICICLE" -> {
                    if (serverNow < a.warnUntil || serverNow > a.endAt) continue;
                    if (!a.soundPlayed) {
                        a.soundPlayed = true;
                        playAt(a.from, SoundEvents.GENERIC_EXPLODE.value(),
                            "FROST_BURST".equals(a.type) ? 0.85f : 0.7f,
                            "NOVA_ICICLE".equals(a.type) ? 1.35f : 0.85f);
                        playAt(a.from, SoundEvents.GLASS_BREAK, 0.9f, 0.8f);
                    }
                    if (canHit && !a.struck && a.coefficient > 0) {
                        Vec3 offset = player.position().subtract(a.from);
                        double maxY = "NOVA_ICICLE".equals(a.type) ? 5.0 : 3.8;
                        if (Math.abs(offset.y) <= maxY && offset.horizontalDistanceSqr() <= Math.pow(a.radius + 0.35, 2)) {
                            a.struck = true;
                            emitHit(a.boss, a.id, a.type, a.coefficient, serverNow, false);
                        }
                    }
                }
                case "FROST_FIELD" -> {
                    if (a.detonateAt > 0L && serverNow >= a.detonateAt) {
                        if (!a.detonated) {
                            a.detonated = true;
                            playAt(a.from, SoundEvents.GLASS_BREAK, 0.85f, 0.95f);
                            if (canHit && a.detonateCoefficient > 0) {
                                Vec3 offset = player.position().subtract(a.from);
                                if (Math.abs(offset.y) <= 3.5 && offset.horizontalDistanceSqr() <= Math.pow(a.radius + 0.35, 2)) {
                                    emitHit(a.boss, a.id + "/detonate", "FROST_FIELD_DETONATE", a.detonateCoefficient, serverNow, true);
                                }
                            }
                        }
                        continue;
                    }
                    if (serverNow < a.warnUntil || serverNow > a.endAt) continue;
                    long stepMs = Math.max(150L, a.intervalMs);
                    int pulse = (int) ((serverNow - a.warnUntil) / stepMs);
                    if (!canHit || a.coefficient <= 0 || pulse <= a.lastPulse) continue;
                    Vec3 offset = player.position().subtract(a.from);
                    if (Math.abs(offset.y) <= 2.8 && offset.horizontalDistanceSqr() <= Math.pow(a.radius + 0.25, 2)) {
                        a.lastPulse = pulse;
                        emitHit(a.boss, a.id + "/p" + pulse, "FROST_FIELD", a.coefficient, serverNow, true);
                    }
                }
                default -> {}
            }
        }
    }

    private static void emitHit(Target boss, String hitId, String kind, double baseCoeff, long serverNow, boolean slow) {
        double finalCoeff = baseCoeff;
        // 碎冰共振：若玩家正处于冻结硬控，触发 1.35 倍碎冰增伤并立刻解除冻结
        Life own = callbacks.own();
        long localNow = ClientUi.now();
        if (own != null && ElementAccumulation.controls(own, localNow)) {
            finalCoeff *= FrostTowerFrostnova.SHATTER_BONUS_MULTIPLIER;
            own.elemental.endEarly(ElementAccumulation.Kind.FREEZE, localNow);
            playLocalSound(SoundEvents.GLASS_BREAK, 1.0f, 0.72f);
        }

        JsonObject action = new JsonObject();
        action.addProperty("id", "frostnova/" + hitId);
        action.addProperty("clientContact", true);
        action.add("boss", JSON.toJsonTree(boss));
        action.addProperty("kind", kind);
        action.addProperty("coefficient", finalCoeff);
        action.addProperty("magic", true);
        action.addProperty("element", FrostTowerFrostnova.ELEMENT_ICE);
        action.addProperty("occurredAt", serverNow);
        if (slow) {
            action.addProperty("ritualSlowTicks", 30);
            action.addProperty("ritualSlowAmplifier", 1);
        }
        callbacks.bossContact(action);
    }

    static Vec3 resolveBossPos(int entityId, Vec3 fallback) {
        var mc = Minecraft.getInstance();
        if (mc.level != null && entityId >= 0) {
            Entity e = mc.level.getEntity(entityId);
            if (e != null && e.isAlive()) return e.position();
        }
        return fallback;
    }

    static Vec3 resolveBossForward(int entityId, Vec3 fallback) {
        var mc = Minecraft.getInstance();
        if (mc.level != null && entityId >= 0) {
            Entity e = mc.level.getEntity(entityId);
            if (e != null && e.isAlive()) {
                double rad = Math.toRadians(e.getYRot());
                return new Vec3(-Math.sin(rad), 0, Math.cos(rad)).normalize();
            }
        }
        return fallback.multiply(1, 0, 1).lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : fallback.multiply(1, 0, 1).normalize();
    }

    private static Vec3 computeBackSlotPos(int bossEntityId, Vec3 fallbackPos, Vec3 fallbackForward, int index, int total) {
        Vec3 base = resolveBossPos(bossEntityId, fallbackPos);
        Vec3 forward = resolveBossForward(bossEntityId, fallbackForward);
        Vec3 right = new Vec3(-forward.z, 0, forward.x);
        double[] off = FrostTowerFrostnova.backSpikeSlotOffset(index, total);
        return base.add(right.scale(off[0])).add(0, off[1], 0).subtract(forward.scale(off[2]));
    }

    private static Vec3 horizontalDir(Vec3 from, Vec3 to) {
        Vec3 d = new Vec3(to.x - from.x, 0, to.z - from.z);
        return d.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : d.normalize();
    }

    private static Vec3 rotateHorizontal(Vec3 dir, double angleDeg) {
        double rad = Math.toRadians(angleDeg);
        double cos = Math.cos(rad), sin = Math.sin(rad);
        return new Vec3(dir.x * cos - dir.z * sin, dir.y, dir.x * sin + dir.z * cos).normalize();
    }

    private static void playAt(Vec3 pos, net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
        var mc = Minecraft.getInstance();
        if (mc.level != null) {
            mc.level.playLocalSound(pos.x, pos.y + 0.8, pos.z, sound, SoundSource.HOSTILE, volume, pitch, false);
        }
    }

    private static void playLocalSound(net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
        var mc = Minecraft.getInstance();
        if (mc.level != null && mc.player != null) {
            mc.level.playLocalSound(mc.player.getX(), mc.player.getY(), mc.player.getZ(), sound, SoundSource.HOSTILE, volume, pitch, false);
        }
    }

    public static void hud(GuiGraphicsExtractor g) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.gui.hud.isHidden() || mc.gui.screen() != null) return;
        long now = ClientUi.now();
        if (!bannerTitle.isEmpty() && now < bannerUntil) {
            double remaining = Math.max(0.0, (bannerUntil - now) / 1000.0);
            String label = String.format(Locale.ROOT, "%s  %.1fs", bannerTitle, remaining);
            var font = UiFonts.font();
            int w = font.width(label) + 18;
            int x = (g.guiWidth() - w) / 2;
            int y = 56;
            UiTheme.hud(g, x, y, w, 18);
            g.text(font, label, x + 9, y + 5, bannerColor, true);
        }
    }
}
