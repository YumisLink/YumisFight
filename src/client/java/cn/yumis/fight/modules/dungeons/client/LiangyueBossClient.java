package cn.yumis.fight.modules.dungeons.client;

import cn.yumis.fight.modules.combat.api.Life;
import cn.yumis.fight.modules.dungeons.api.Target;
import cn.yumis.fight.modules.dungeons.content.TheaterTowerLiangyue;
import cn.yumis.fight.modules.presentation.api.client.ClientUi;
import cn.yumis.fight.modules.presentation.api.client.UiFonts;
import cn.yumis.fight.modules.presentation.api.client.UiTheme;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.*;

/**
 * 剧场塔第 20 层首领「神剑-梁月的幻影」本机客户端权威控制器：
 * - 接收服务端下发的直线剑气、猛龙突进、追击旋风、幻影剑舞与剑圣 1 觉暴风式；
 * - 维护旋风插值位置与全部技能几何的本机受击判定，并按幂等命中 ID 汇报战斗回执；
 * - 离场、换层、死亡或断线时自动清理所有场地残留物与横幅。
 */
public final class LiangyueBossClient {
    private static final Gson JSON = new Gson();

    public interface Callbacks {
        Life own();
        void bossContact(JsonObject hitPacket);
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
        long interpDurationMs = TheaterTowerLiangyue.SKILL2_WHIRLWIND_SYNC_INTERVAL_MS;
        final double radius;
        final double width;
        final long startAt;
        final long warnUntil;
        final long endAt;
        final long tickMs;
        final double coefficient;
        final double finisherCoefficient;
        final double slashAngle;
        final long slashEndAt;
        final long finisherAt;
        final long slashIntervalMs;
        final long seed;
        final boolean magic;
        final int element;
        final int color;
        long previousAt;
        int lastPulse = -1;
        int lastSoundPhase = -1;
        boolean struck;
        boolean finisherStruck;
        boolean launchSoundPlayed;

        Hazard(String id, String instance, String type, Target boss, Vec3 from, Vec3 to,
               double radius, double width, long startAt, long warnUntil, long endAt, long tickMs,
               double coefficient, double finisherCoefficient, double slashAngle,
               long slashEndAt, long finisherAt, long slashIntervalMs, long seed,
               boolean magic, int element, int color) {
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
            this.finisherCoefficient = finisherCoefficient;
            this.slashAngle = slashAngle;
            this.slashEndAt = slashEndAt;
            this.finisherAt = finisherAt;
            this.slashIntervalMs = slashIntervalMs;
            this.seed = seed;
            this.magic = magic;
            this.element = element;
            this.color = color;
            this.previousAt = warnUntil;
        }

        Vec3 currentPos(long serverNow) {
            if (!"WHIRLWIND".equals(type) || interpDurationMs <= 0) return from;
            double alpha = Math.clamp((serverNow - interpStartAt) / (double) interpDurationMs, 0.0, 1.0);
            return interpFrom.lerp(from, alpha);
        }
    }

    private static Callbacks callbacks;
    private static Object world;
    private static String activeInstance = "";
    private static String bannerTitle = "";
    private static long bannerUntil = 0L;
    private static int bannerColor = 0xff98f2ff;
    static final Map<String, Hazard> hazards = new LinkedHashMap<>();

    private LiangyueBossClient() {}

    public static void register(Callbacks cb) {
        callbacks = Objects.requireNonNull(cb);
        NeoForge.EVENT_BUS.addListener(LiangyueBossClient::tick);
    }

    public static void clear() {
        hazards.clear();
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
            hazards.clear();
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
        if ("CLEAR_ENCOUNTER_EFFECTS".equals(kind) || "CLEAR_HAZARDS".equals(kind)) {
            hazards.clear();
            if ("CLEAR_ENCOUNTER_EFFECTS".equals(kind)) {
                bannerTitle = "";
                bannerUntil = 0L;
            }
            return;
        }
        String instance = pkt.has("instance") ? pkt.get("instance").getAsString() : "";
        if (instance.isEmpty() || !instance.equals(callbacks.own().instance)) return;
        activeInstance = instance;
        long now = ClientUi.now();
        long duration = pkt.has("durationMs") ? pkt.get("durationMs").getAsLong() : 3_000L;
        switch (kind) {
            case "STORM_BANNER" -> {
                bannerTitle = "极鬼剑术 · 暴风式";
                bannerUntil = now + duration;
                bannerColor = 0xff8ceeff;
                playLocalSound(SoundEvents.BEACON_ACTIVATE, 0.75f, 0.95f);
            }
            case "DANCE_BANNER" -> {
                bannerTitle = "幻影剑舞";
                bannerUntil = now + duration;
                bannerColor = 0xffb8f6ff;
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
                    : TheaterTowerLiangyue.SKILL2_WHIRLWIND_SYNC_INTERVAL_MS;
            }
            return;
        }

        Target boss = JSON.fromJson(pkt.get("boss"), Target.class);
        Vec3 from = new Vec3(pkt.get("x").getAsDouble(), pkt.get("y").getAsDouble(), pkt.get("z").getAsDouble());
        Vec3 to = new Vec3(pkt.get("tx").getAsDouble(), pkt.get("ty").getAsDouble(), pkt.get("tz").getAsDouble());
        Hazard h = new Hazard(
            id, instance, pkt.get("type").getAsString(), boss, from, to,
            pkt.get("radius").getAsDouble(),
            pkt.get("width").getAsDouble(),
            pkt.get("startAt").getAsLong(),
            pkt.get("warnUntil").getAsLong(),
            pkt.get("endAt").getAsLong(),
            pkt.get("tickMs").getAsLong(),
            pkt.get("coefficient").getAsDouble(),
            pkt.has("finisherCoefficient") ? pkt.get("finisherCoefficient").getAsDouble() : 0.0,
            pkt.has("slashAngle") ? pkt.get("slashAngle").getAsDouble() : 0.0,
            pkt.has("slashEndAt") ? pkt.get("slashEndAt").getAsLong() : 0L,
            pkt.has("finisherAt") ? pkt.get("finisherAt").getAsLong() : 0L,
            pkt.has("slashIntervalMs") ? pkt.get("slashIntervalMs").getAsLong() : 0L,
            pkt.has("seed") ? pkt.get("seed").getAsLong() : 0L,
            pkt.get("magic").getAsBoolean(),
            pkt.get("element").getAsInt(),
            pkt.get("color").getAsInt()
        );
        hazards.put(id, h);
    }

    private static void tick(ClientTickEvent.Post event) {
        if (callbacks == null) return;
        syncContext();
        var mc = Minecraft.getInstance();
        var player = mc.player;
        if (mc.level == null || player == null || !ClientUi.current().ready() || mc.isPaused()) return;

        long serverNow = ClientUi.current().serverNow();
        hazards.values().removeIf(h -> serverNow >= h.endAt + 500L);

        boolean canHit = !callbacks.own().dead && !player.isDeadOrDying() && !TowerClient.blocksCombat();

        for (Hazard h : new ArrayList<>(hazards.values())) {
            if (!h.instance.equals(callbacks.own().instance)) continue;
            if (serverNow < h.warnUntil || serverNow > h.endAt) continue;

            switch (h.type) {
                case "SWORD_WAVE", "SWORD_WAVE_LARGE", "SWORD_WAVE_FINISHER" -> {
                    if (!h.launchSoundPlayed) {
                        h.launchSoundPlayed = true;
                        playAt(h.from, SoundEvents.PLAYER_ATTACK_SWEEP,
                            "SWORD_WAVE_FINISHER".equals(h.type) ? 0.95f : 0.65f,
                            "SWORD_WAVE_FINISHER".equals(h.type) ? 0.75f : 1.05f);
                    }
                    double prevProg = Math.clamp((h.previousAt - h.warnUntil) / (double) Math.max(1L, h.endAt - h.warnUntil), 0.0, 1.0);
                    double currProg = Math.clamp((serverNow - h.warnUntil) / (double) Math.max(1L, h.endAt - h.warnUntil), 0.0, 1.0);
                    h.previousAt = serverNow;
                    if (canHit && !h.struck && h.coefficient > 0
                        && hitsTravelingLine(player.position(), h.from, h.to, h.width, prevProg, currProg)) {
                        h.struck = true;
                        emitHit(h, h.id, h.coefficient, serverNow);
                    }
                }
                case "DRAGON_DASH" -> {
                    if (!h.launchSoundPlayed) {
                        h.launchSoundPlayed = true;
                        playAt(h.from, SoundEvents.PLAYER_ATTACK_STRONG, 0.85f, 1.25f);
                    }
                    double prevProg = Math.clamp((h.previousAt - h.warnUntil) / (double) Math.max(1L, h.endAt - h.warnUntil), 0.0, 1.0);
                    double currProg = Math.clamp((serverNow - h.warnUntil) / (double) Math.max(1L, h.endAt - h.warnUntil), 0.0, 1.0);
                    h.previousAt = serverNow;
                    if (canHit && !h.struck && h.coefficient > 0) {
                        Vec3 segA = h.from.lerp(h.to, prevProg);
                        Vec3 segB = h.from.lerp(h.to, currProg);
                        AABB box = player.getBoundingBox().inflate(h.width * 0.5, 0.9, h.width * 0.5);
                        if (box.contains(segA) || box.contains(segB) || box.clip(segA, segB).isPresent()) {
                            h.struck = true;
                            emitHit(h, h.id, h.coefficient, serverNow);
                        }
                    }
                }
                case "WHIRLWIND" -> {
                    long stepMs = Math.max(100L, h.tickMs);
                    int pulse = (int) ((serverNow - h.warnUntil) / stepMs);
                    if (!canHit || h.coefficient <= 0 || pulse <= h.lastPulse) continue;
                    Vec3 offset = player.position().subtract(h.currentPos(serverNow));
                    if (Math.abs(offset.y) <= 3.0 && offset.horizontalDistanceSqr() <= Math.pow(h.radius + 0.35, 2)) {
                        h.lastPulse = pulse;
                        emitHit(h, h.id + "/p" + pulse, h.coefficient, serverNow);
                    }
                }
                case "DANCE_FLURRY" -> {
                    long stepMs = Math.max(100L, h.tickMs);
                    int pulse = (int) ((serverNow - h.warnUntil) / stepMs);
                    if (pulse > h.lastSoundPhase) {
                        h.lastSoundPhase = pulse;
                        playAt(h.from, SoundEvents.PLAYER_ATTACK_SWEEP, 0.45f, 1.15f + (pulse % 3) * 0.12f);
                    }
                    if (!canHit || h.coefficient <= 0 || pulse <= h.lastPulse) continue;
                    Vec3 offset = player.position().subtract(h.from);
                    if (Math.abs(offset.y) <= 3.0 && offset.horizontalDistanceSqr() <= Math.pow(h.radius + 0.35, 2)) {
                        h.lastPulse = pulse;
                        emitHit(h, h.id + "/p" + pulse, h.coefficient, serverNow);
                    }
                }
                case "STORM_AWAKENING" -> {
                    if (serverNow < h.slashEndAt) {
                        long sInterval = Math.max(50L, h.slashIntervalMs);
                        int soundPhase = (int) Math.clamp((serverNow - h.warnUntil) / sInterval, 0, TheaterTowerLiangyue.SKILL3_SWORDS - 1);
                        if (soundPhase > h.lastSoundPhase) {
                            h.lastSoundPhase = soundPhase;
                            playAt(h.from, SoundEvents.PLAYER_ATTACK_SWEEP, 0.55f, 1.10f + (soundPhase % 3) * 0.14f);
                        }
                        long stepMs = Math.max(150L, h.tickMs);
                        int pulse = (int) ((serverNow - h.warnUntil) / stepMs);
                        if (canHit && h.coefficient > 0 && pulse > h.lastPulse) {
                            Vec3 offset = player.position().subtract(h.from);
                            if (Math.abs(offset.y) <= 4.5 && offset.horizontalDistanceSqr() <= Math.pow(h.radius + 0.3, 2)) {
                                h.lastPulse = pulse;
                                emitHit(h, h.id + "/s" + pulse, h.coefficient, serverNow);
                            }
                        }
                    } else if (serverNow >= h.finisherAt && !h.finisherStruck) {
                        h.finisherStruck = true;
                        playAt(h.from, SoundEvents.PLAYER_ATTACK_STRONG, 1.0f, 0.65f);
                        if (canHit && h.finisherCoefficient > 0) {
                            Vec3 offset = player.position().subtract(h.from);
                            double finishRadius = h.width > 0 ? h.width : h.radius;
                            if (Math.abs(offset.y) <= 4.5 && offset.horizontalDistanceSqr() <= Math.pow(finishRadius + 0.35, 2)) {
                                emitHit(h, h.id + "/finisher", h.finisherCoefficient, serverNow);
                            }
                        }
                    }
                }
                default -> {}
            }
        }
    }

    /** 纯几何判定：沿直线推进的大范围剑气在 [prevProg, currProg] 区间内是否扫中玩家。 */
    public static boolean hitsTravelingLine(Vec3 playerPos, Vec3 from, Vec3 to, double width, double prevProg, double currProg) {
        Vec3 span = new Vec3(to.x - from.x, 0, to.z - from.z);
        double range = span.length();
        if (range < 1e-4) return false;
        Vec3 dir = span.scale(1.0 / range);
        Vec3 offset = playerPos.subtract(from);
        if (Math.abs(offset.y) > 2.8) return false;
        double along = offset.x * dir.x + offset.z * dir.z;
        double minAlong = Math.min(prevProg, currProg) * range - 1.1;
        double maxAlong = Math.max(prevProg, currProg) * range + 1.1;
        if (along < minAlong || along > maxAlong || along < -0.5 || along > range + 0.8) return false;
        double lateral = Math.abs(offset.x * dir.z - offset.z * dir.x);
        return lateral <= width * 0.5 + 0.35;
    }

    private static void emitHit(Hazard h, String hitId, double coefficient, long serverNow) {
        JsonObject action = new JsonObject();
        action.addProperty("id", "liangyue/" + hitId);
        action.addProperty("clientContact", true);
        action.add("boss", JSON.toJsonTree(h.boss));
        action.addProperty("kind", h.type);
        action.addProperty("coefficient", coefficient);
        action.addProperty("magic", h.magic);
        action.addProperty("element", h.element);
        action.addProperty("occurredAt", serverNow);
        callbacks.bossContact(action);
    }

    private static void playAt(Vec3 pos, net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
        var mc = Minecraft.getInstance();
        if (mc.level != null) {
            mc.level.playLocalSound(pos.x, pos.y + 1.0, pos.z, sound, SoundSource.HOSTILE, volume, pitch, false);
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
