package cn.yumis.fight.modules.dungeons.client;

import cn.yumis.fight.modules.combat.api.Life;
import cn.yumis.fight.modules.dungeons.api.Target;
import cn.yumis.fight.modules.dungeons.api.client.SealBossState;
import cn.yumis.fight.modules.dungeons.content.SealTowerElaina;
import cn.yumis.fight.modules.presentation.api.client.ClientUi;
import cn.yumis.fight.modules.presentation.api.client.UiFonts;
import cn.yumis.fight.modules.presentation.api.client.UiTheme;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.*;

/**
 * 封印塔第50层首领「伊蕾娜」本机客户端权威控制器：
 * - 接收并幂等执行服务端下发的阶段/技能事件（10s冰冻、100s全CD、午夜18000刻、5s回溯、999999s软狂暴封印、终局陨石）；
 * - 维护场地延迟技能物（四连冲锋、追踪星辰、三色坠球、24连日冕、南瓜爆弹、午夜陨石、终局双巨型陨石）的本机接触判定；
 * - 退场/通关/换场次/断线自动清理冰冻、午夜天象与 999999s 封印冷却。
 */
public final class ElainaBossClient {
    private static final Gson JSON = new Gson();

    public interface Callbacks {
        Life own();
        void cancelActiveForControl();
        void applyAllCooldown100s(long cooldownMs);
        void applyRewindRandomCooldown(Set<String> permaSealed, long zeroCdFallbackMs);
        String sealRandomActiveSkill(Set<String> permaSealed, long sealDurationMs);
        void clearSealedCooldowns(Set<String> permaSealed);
        void bossContact(JsonObject hitPacket);
        void finaleMeteor1Damage(double ratio, JsonObject boss);
        void finaleMeteor2Wipe(JsonObject boss);
    }

    static final class Hazard {
        final String id;
        final String instance;
        final String type;
        final Target boss;
        Vec3 from;
        Vec3 to;
        Vec3 interpFrom;
        long interpStartAt;
        long interpDurationMs = SealTowerElaina.SKILL2_SYNC_INTERVAL_MS;
        final double radius;
        final double width;
        final long startAt;
        final long warnUntil;
        final long endAt;
        final long tickMs;
        final double coefficient;
        final boolean magic;
        final int element;
        final int color;
        long previousAt;
        int lastPulse = -1;
        boolean struck;

        Hazard(String id, String instance, String type, Target boss, Vec3 from, Vec3 to,
               double radius, double width, long startAt, long warnUntil, long endAt, long tickMs,
               double coefficient, boolean magic, int element, int color) {
            this.id = id;
            this.instance = instance;
            this.type = type;
            this.boss = boss;
            this.from = from;
            this.to = to;
            this.interpFrom = from;
            this.interpStartAt = startAt;
            this.radius = radius;
            this.width = width;
            this.startAt = startAt;
            this.warnUntil = warnUntil;
            this.endAt = endAt;
            this.tickMs = tickMs;
            this.coefficient = coefficient;
            this.magic = magic;
            this.element = element;
            this.color = color;
            this.previousAt = warnUntil;
        }

        Vec3 currentPos(long serverNow) {
            if (!"TRACKING_STAR".equals(type) || interpDurationMs <= 0) return from;
            double alpha = Math.clamp((serverNow - interpStartAt) / (double) interpDurationMs, 0.0, 1.0);
            return interpFrom.lerp(from, alpha);
        }
    }

    static final class ClockReverseVisual {
        final Vec3 center;
        final long startAt, endAt;

        ClockReverseVisual(Vec3 center, long startAt, long endAt) {
            this.center = center;
            this.startAt = startAt;
            this.endAt = endAt;
        }
    }

    static final class RewindAnchorVisual {
        final Vec3 pos;
        final long startAt, endAt;

        RewindAnchorVisual(Vec3 pos, long startAt, long endAt) {
            this.pos = pos;
            this.startAt = startAt;
            this.endAt = endAt;
        }
    }

    private static Callbacks callbacks;
    private static Object world;
    private static String activeInstance = "";
    private static long freezeUntil = 0L;
    private static boolean midnightSky = false;
    private static String bannerTitle = "";
    private static long bannerUntil = 0L;
    private static int bannerColor = 0xffe0c8ff;
    private static final Set<String> permaSealedSkills = new LinkedHashSet<>();
    private static final Set<String> handledEvents = new HashSet<>();
    static final Map<String, Hazard> hazards = new LinkedHashMap<>();
    static ClockReverseVisual clockReverse;
    static final List<RewindAnchorVisual> rewindAnchors = new ArrayList<>();

    private ElainaBossClient() {}

    public static void register(Callbacks cb) {
        callbacks = Objects.requireNonNull(cb);
        SealBossState.install(ElainaBossClient::isFrozen, ElainaBossClient::isMidnightSky, permaSealedSkills::contains);
        NeoForge.EVENT_BUS.addListener(ElainaBossClient::tick);
    }

    public static boolean isFrozen() {
        return freezeUntil > 0 && ClientUi.now() < freezeUntil;
    }

    public static boolean isMidnightSky() {
        return midnightSky && !activeInstance.isEmpty();
    }

    public static void clear() {
        if (callbacks != null && !permaSealedSkills.isEmpty()) {
            callbacks.clearSealedCooldowns(permaSealedSkills);
        }
        permaSealedSkills.clear();
        handledEvents.clear();
        hazards.clear();
        rewindAnchors.clear();
        clockReverse = null;
        freezeUntil = 0L;
        midnightSky = false;
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
            clearEncounterPenalties();
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
        String instance = pkt.has("instance") ? pkt.get("instance").getAsString() : "";
        String kind = pkt.get("kind").getAsString();

        if ("CLEAR_ENCOUNTER_EFFECTS".equals(kind)) {
            clearEncounterPenalties();
            return;
        }
        if ("CLEAR_HAZARDS".equals(kind)) {
            hazards.clear();
            rewindAnchors.clear();
            return;
        }
        String ownInstance = callbacks.own().instance;
        if (instance.isEmpty() || !instance.equals(ownInstance)) return;
        activeInstance = instance;

        String eventId = pkt.has("eventId") ? pkt.get("eventId").getAsString() : "";
        if (!eventId.isEmpty() && !handledEvents.add(eventId)) return;

        long now = ClientUi.now();
        long serverNow = ClientUi.current().serverNow();

        switch (kind) {
            case "FREEZE_10S" -> {
                long duration = pkt.has("durationMs") ? pkt.get("durationMs").getAsLong() : SealTowerElaina.TRANSITION_80_DURATION_MS;
                callbacks.cancelActiveForControl();
                freezeUntil = now + duration;
                bannerTitle = "极寒禁锢 · 全员冰封";
                bannerUntil = now + duration;
                bannerColor = 0xff8ce8ff;
                ClientUi.current().message("伊蕾娜释放了极寒禁锢！全员冰冻 10 秒！");
            }
            case "ALL_COOLDOWN_100S" -> {
                long cdMs = pkt.has("cooldownMs") ? pkt.get("cooldownMs").getAsLong() : SealTowerElaina.TRANSITION_80_ALL_CD_MS;
                freezeUntil = 0L;
                callbacks.applyAllCooldown100s(cdMs);
                ClientUi.current().message("冰封解除！所有主动技能进入 100 秒冷却！");
            }
            case "MIDNIGHT" -> {
                midnightSky = pkt.get("active").getAsBoolean();
                if (midnightSky) {
                    bannerTitle = "午夜陨星降临";
                    bannerUntil = now + SealTowerElaina.TRANSITION_50_DURATION_MS;
                    bannerColor = 0xffc694ff;
                }
            }
            case "CLOCK_REVERSE" -> {
                long duration = pkt.has("durationMs") ? pkt.get("durationMs").getAsLong() : SealTowerElaina.TRANSITION_20_DURATION_MS;
                Vec3 center = new Vec3(pkt.get("x").getAsDouble(), pkt.get("y").getAsDouble(), pkt.get("z").getAsDouble());
                clockReverse = new ClockReverseVisual(center, serverNow, serverNow + duration);
                bannerTitle = "时光逆转 · 生命回溯";
                bannerUntil = now + duration;
                bannerColor = 0xffffd878;
                ClientUi.current().message("伊蕾娜唤出巨型时钟，正在逆转自身时间！");
            }
            case "REWIND_ANCHOR" -> {
                long duration = pkt.has("durationMs") ? pkt.get("durationMs").getAsLong() : SealTowerElaina.SKILL10_DELAY_MS;
                if (pkt.has("anchors")) {
                    for (JsonElement elem : pkt.getAsJsonArray("anchors")) {
                        JsonObject o = elem.getAsJsonObject();
                        rewindAnchors.add(new RewindAnchorVisual(
                            new Vec3(o.get("x").getAsDouble(), o.get("y").getAsDouble(), o.get("z").getAsDouble()),
                            serverNow, serverNow + duration
                        ));
                    }
                }
            }
            case "REWIND_COMMIT" -> {
                var player = Minecraft.getInstance().player;
                if (player != null && !callbacks.own().dead) {
                    double x = pkt.get("x").getAsDouble();
                    double y = pkt.get("y").getAsDouble();
                    double z = pkt.get("z").getAsDouble();
                    player.setPos(x, y, z);
                    player.setDeltaMovement(Vec3.ZERO);
                    callbacks.applyRewindRandomCooldown(permaSealedSkills, SealTowerElaina.SKILL10_ZERO_CD_FALLBACK_MS);
                    ClientUi.current().message("时钟回溯触发：你被拉回原位，1 个技能进入冷却！");
                }
            }
            case "SEAL_SKILL_999999S" -> {
                long duration = pkt.has("durationMs") ? pkt.get("durationMs").getAsLong() : SealTowerElaina.SKILL11_SEAL_DURATION_MS;
                String sealed = callbacks.sealRandomActiveSkill(permaSealedSkills, duration);
                if (!sealed.isEmpty()) {
                    ClientUi.current().message("时之封印：你的一项主动技能已被封禁至本场战斗结束！");
                }
            }
            case "FINALE_METEOR_1_IMPACT" -> {
                var player = Minecraft.getInstance().player;
                if (player != null && !callbacks.own().dead) {
                    Vec3 center = new Vec3(pkt.get("cx").getAsDouble(), pkt.get("cy").getAsDouble(), pkt.get("cz").getAsDouble());
                    double dist = player.position().subtract(center).horizontalDistance();
                    double ratio = SealTowerElaina.finaleMeteor1Ratio(dist);
                    callbacks.finaleMeteor1Damage(ratio, pkt.getAsJsonObject("boss"));
                }
                bannerTitle = "终局斩杀 · 击破护盾并击败伊蕾娜";
                bannerUntil = now + SealTowerElaina.FINALE_DPS_RACE_MS;
                bannerColor = 0xffff5c6c;
            }
            case "FINALE_METEOR_2_WIPE" -> {
                callbacks.finaleMeteor2Wipe(pkt.getAsJsonObject("boss"));
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
        if (pkt.has("moveOnly") && pkt.get("moveOnly").getAsBoolean()) {
            Hazard existing = hazards.get(id);
            if (existing != null) {
                long serverNow = ClientUi.current().serverNow();
                Vec3 next = new Vec3(pkt.get("x").getAsDouble(), pkt.get("y").getAsDouble(), pkt.get("z").getAsDouble());
                existing.interpFrom = existing.currentPos(serverNow);
                existing.from = next;
                existing.to = next;
                existing.interpStartAt = serverNow;
                existing.interpDurationMs = pkt.has("moveDuration")
                    ? Math.max(1L, pkt.get("moveDuration").getAsLong())
                    : SealTowerElaina.SKILL2_SYNC_INTERVAL_MS;
            }
            return;
        }
        Target boss = JSON.fromJson(pkt.get("boss"), Target.class);
        Vec3 from = new Vec3(pkt.get("x").getAsDouble(), pkt.get("y").getAsDouble(), pkt.get("z").getAsDouble());
        Vec3 to = new Vec3(pkt.get("tx").getAsDouble(), pkt.get("ty").getAsDouble(), pkt.get("tz").getAsDouble());
        String type = pkt.get("type").getAsString();
        Hazard h = new Hazard(
            id, instance, type, boss, from, to,
            pkt.get("radius").getAsDouble(),
            pkt.get("width").getAsDouble(),
            pkt.get("startAt").getAsLong(),
            pkt.get("warnUntil").getAsLong(),
            pkt.get("endAt").getAsLong(),
            pkt.get("tickMs").getAsLong(),
            pkt.get("coefficient").getAsDouble(),
            pkt.get("magic").getAsBoolean(),
            pkt.get("element").getAsInt(),
            pkt.get("color").getAsInt()
        );
        hazards.put(id, h);
        if ("METEOR_FINALE_1".equals(type)) {
            long now = ClientUi.now();
            bannerTitle = "终局巨型陨石降临 · 速往边缘规避";
            bannerUntil = now + SealTowerElaina.FINALE_METEOR_1_WARNING_MS;
            bannerColor = 0xffff7d42;
        }
    }

    private static void clearEncounterPenalties() {
        if (callbacks != null) {
            callbacks.clearSealedCooldowns(permaSealedSkills);
        }
        permaSealedSkills.clear();
        handledEvents.clear();
        hazards.clear();
        rewindAnchors.clear();
        clockReverse = null;
        freezeUntil = 0L;
        midnightSky = false;
        bannerTitle = "";
        bannerUntil = 0L;
    }

    private static void tick(ClientTickEvent.Post event) {
        if (callbacks == null) return;
        syncContext();
        var mc = Minecraft.getInstance();
        var player = mc.player;
        if (mc.level == null || player == null || !ClientUi.current().ready()) return;
        if (mc.isPaused()) return;

        long now = ClientUi.now();
        long serverNow = ClientUi.current().serverNow();

        if (freezeUntil > 0 && now >= freezeUntil) {
            freezeUntil = 0L;
        }
        if (isFrozen()) {
            player.setDeltaMovement(Vec3.ZERO);
        }

        if (clockReverse != null && serverNow >= clockReverse.endAt) {
            clockReverse = null;
        }
        rewindAnchors.removeIf(a -> serverNow >= a.endAt);
        hazards.values().removeIf(h -> serverNow >= h.endAt + 500L);

        if (callbacks.own().dead || player.isDeadOrDying() || TowerClient.blocksCombat()) return;

        for (Hazard h : new ArrayList<>(hazards.values())) {
            if (!h.instance.equals(callbacks.own().instance)) continue;
            if (h.coefficient <= 0 || serverNow < h.warnUntil || serverNow > h.endAt) continue;

            if ("WAND_CHARGE".equals(h.type)) {
                if (h.struck) continue;
                double prevProg = Math.clamp((h.previousAt - h.warnUntil) / (double) Math.max(1L, h.endAt - h.warnUntil), 0.0, 1.0);
                double currProg = Math.clamp((serverNow - h.warnUntil) / (double) Math.max(1L, h.endAt - h.warnUntil), 0.0, 1.0);
                Vec3 segA = h.from.lerp(h.to, prevProg);
                Vec3 segB = h.from.lerp(h.to, currProg);
                h.previousAt = serverNow;
                AABB box = player.getBoundingBox().inflate(h.width * 0.5, 0.8, h.width * 0.5);
                if (box.contains(segA) || box.contains(segB) || box.clip(segA, segB).isPresent()) {
                    h.struck = true;
                    emitHit(h, h.id, serverNow);
                }
            } else if ("TRACKING_STAR".equals(h.type)) {
                long stepMs = Math.max(100L, h.tickMs);
                int pulse = (int) ((serverNow - h.warnUntil) / stepMs);
                if (pulse <= h.lastPulse || pulse >= 6) continue;
                Vec3 offset = player.position().subtract(h.currentPos(serverNow));
                if (Math.abs(offset.y) <= 2.8 && offset.horizontalDistanceSqr() <= Math.pow(h.radius + 0.3, 2)) {
                    h.lastPulse = pulse;
                    emitHit(h, h.id + "/p" + pulse, serverNow);
                }
            } else {
                // 一次性爆炸物：ORB_ICE / ORB_FIRE / ORB_EARTH / CORONA / PUMPKIN / METEOR_SMALL
                if (h.struck) continue;
                Vec3 center = "PUMPKIN".equals(h.type) ? h.to : h.from;
                Vec3 offset = player.position().subtract(center);
                if (Math.abs(offset.y) <= 3.5 && offset.horizontalDistanceSqr() <= Math.pow(h.radius + 0.3, 2)) {
                    h.struck = true;
                    emitHit(h, h.id, serverNow);
                }
            }
        }
    }

    private static void emitHit(Hazard h, String hitId, long serverNow) {
        JsonObject action = new JsonObject();
        action.addProperty("id", "elaina/" + hitId);
        action.addProperty("clientContact", true);
        action.add("boss", JSON.toJsonTree(h.boss));
        action.addProperty("kind", h.type);
        action.addProperty("coefficient", h.coefficient);
        action.addProperty("magic", h.magic);
        action.addProperty("element", h.element);
        action.addProperty("occurredAt", serverNow);
        callbacks.bossContact(action);
    }

    public static void hud(GuiGraphicsExtractor g) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.gui.hud.isHidden() || mc.gui.screen() != null) return;
        long now = ClientUi.now();
        // 冰冻期间屏幕淡蓝霜膜
        if (isFrozen()) {
            g.fill(0, 0, g.guiWidth(), g.guiHeight(), 0x267AD8FF);
        }
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
