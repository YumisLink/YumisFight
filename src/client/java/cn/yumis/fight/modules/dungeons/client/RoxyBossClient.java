package cn.yumis.fight.modules.dungeons.client;

import cn.yumis.fight.modules.combat.api.Life;
import cn.yumis.fight.modules.dungeons.api.Target;
import cn.yumis.fight.modules.dungeons.api.client.SluiceBossState;
import cn.yumis.fight.modules.dungeons.content.SluiceTowerRoxy;
import cn.yumis.fight.modules.presentation.api.client.CameraShake;
import cn.yumis.fight.modules.presentation.api.client.ClientUi;
import cn.yumis.fight.modules.presentation.api.client.UiFonts;
import cn.yumis.fight.modules.presentation.api.client.UiTheme;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.*;

/**
 * 水闸塔第 20 层专属首领「洛琪希」本机客户端权威控制器与电影级 HUD 演出器：
 * - 模拟水圣弹幕温和弧线追踪与水镜径向水箭连续碰撞判定（单次施放命中上限保护）；
 * - 结算高压水刃、激流喷涌、双层潮汐环、水镜绽放、豪雷落瀑、转场落雷与落地冲击波；
 * - 驱动 12 秒《豪雷积雨云》转场逐句咏唱、3 枚水魔导核心光束连线与破核反馈；
 * - 驱动二阶段《天穹雷狱·台风眼》移动晴空风暴眼安全区判定与眼外天雷劈击；
 * - 管理客户端隔离真暴雨暗天强度、惊雷瞬时微闪光、渐变电影大标题横幅与轻度暴雨边缘氛围；
 * - 离场、换层、死亡或断线时彻底清理所有状态与天象。
 */
public final class RoxyBossClient {
    private static final Gson JSON = new Gson();

    public interface Callbacks {
        Life own();
        void bossContact(JsonObject hitPacket);
    }

    static final class WaterProjectile {
        final String id;
        final String castKey;
        final String instance;
        final Target boss;
        final int bossEntityId;
        final boolean backAttached;
        final int slotIndex;
        final int slotTotal;
        final int maxHitsPerCast;
        final long startAt;
        final long launchAt;
        final long endAt;
        final double speed;
        final double turnDegPerSec;
        final double hitRadius;
        final double coefficient;
        final int element;
        final int color;

        Vec3 spawnPos;
        Vec3 pos;
        Vec3 prevPos;
        Vec3 dir;
        long lastSimAt;
        boolean launched;
        boolean consumed;

        WaterProjectile(String id, String castKey, String instance, Target boss, int bossEntityId,
                        boolean backAttached, int slotIndex, int slotTotal, int maxHitsPerCast,
                        Vec3 spawnPos, Vec3 initialDir,
                        long startAt, long launchAt, long endAt,
                        double speed, double turnDegPerSec, double hitRadius,
                        double coefficient, int element, int color) {
            this.id = id;
            this.castKey = castKey;
            this.instance = instance;
            this.boss = boss;
            this.bossEntityId = bossEntityId;
            this.backAttached = backAttached;
            this.slotIndex = slotIndex;
            this.slotTotal = slotTotal;
            this.maxHitsPerCast = maxHitsPerCast;
            this.spawnPos = spawnPos;
            this.pos = spawnPos;
            this.prevPos = spawnPos;
            this.dir = initialDir.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : initialDir.normalize();
            this.startAt = startAt;
            this.launchAt = launchAt;
            this.endAt = endAt;
            this.speed = speed;
            this.turnDegPerSec = turnDegPerSec;
            this.hitRadius = hitRadius;
            this.coefficient = coefficient;
            this.element = element;
            this.color = color;
            this.lastSimAt = launchAt;
        }
    }

    static final class AreaHazard {
        final String id;
        final String instance;
        final String type;
        final Target boss;
        final int bossEntityId;
        final Vec3 from;
        final Vec3 to;
        final double radius;
        final double auxRadius;
        final double speed;
        final long startAt;
        final long warnUntil;
        final long endAt;
        final long lifeMs;
        final double coefficient;
        final int element;
        final boolean magic;
        final int count;
        final double baseAngle;
        final int color;

        boolean struck;
        boolean soundPlayed;
        boolean spawnedBolts;

        AreaHazard(String id, String instance, String type, Target boss, int bossEntityId,
                   Vec3 from, Vec3 to, double radius, double auxRadius, double speed,
                   long startAt, long warnUntil, long endAt, long lifeMs,
                   double coefficient, int element, boolean magic,
                   int count, double baseAngle, int color) {
            this.id = id;
            this.instance = instance;
            this.type = type;
            this.boss = boss;
            this.bossEntityId = bossEntityId;
            this.from = from;
            this.to = to;
            this.radius = radius;
            this.auxRadius = auxRadius;
            this.speed = speed;
            this.startAt = startAt;
            this.warnUntil = warnUntil;
            this.endAt = endAt;
            this.lifeMs = lifeMs;
            this.coefficient = coefficient;
            this.element = element;
            this.magic = magic;
            this.count = count;
            this.baseAngle = baseAngle;
            this.color = color;
        }
    }

    static final class TransitionVisual {
        final int bossEntityId;
        final Vec3 center;
        final long startAt;
        final long endAt;
        final int coreTotal;
        final List<Vec3> pools;
        int brokenCores;
        int lastChantStep = -1;

        TransitionVisual(int bossEntityId, Vec3 center, long startAt, long endAt, int coreTotal, List<Vec3> pools) {
            this.bossEntityId = bossEntityId;
            this.center = center;
            this.startAt = startAt;
            this.endAt = endAt;
            this.coreTotal = coreTotal;
            this.pools = pools;
        }
    }

    static final class HydroCoreVisual {
        final String key;
        final int entityId;
        final Vec3 pos;
        final long spawnAt;
        final long endAt;
        boolean broken;
        long brokenAt;

        HydroCoreVisual(String key, int entityId, Vec3 pos, long spawnAt, long endAt) {
            this.key = key;
            this.entityId = entityId;
            this.pos = pos;
            this.spawnAt = spawnAt;
            this.endAt = endAt;
        }
    }

    static final class TyphoonEyeVisual {
        final String id;
        final Target boss;
        final int bossEntityId;
        final Vec3 center;
        final int cell;
        final long startAt;
        final long activeAt;
        final long endAt;
        final double startAngle;
        final double sweepSign;
        final double eyeRadius;
        final long outsideTickMs;
        final double outsideCoeff;
        long nextOutsideTickAt;
        boolean soundPlayed;

        TyphoonEyeVisual(String id, Target boss, int bossEntityId, Vec3 center, int cell,
                         long startAt, long activeAt, long endAt,
                         double startAngle, double sweepSign, double eyeRadius,
                         long outsideTickMs, double outsideCoeff) {
            this.id = id;
            this.boss = boss;
            this.bossEntityId = bossEntityId;
            this.center = center;
            this.cell = cell;
            this.startAt = startAt;
            this.activeAt = activeAt;
            this.endAt = endAt;
            this.startAngle = startAngle;
            this.sweepSign = sweepSign;
            this.eyeRadius = eyeRadius;
            this.outsideTickMs = outsideTickMs;
            this.outsideCoeff = outsideCoeff;
            this.nextOutsideTickAt = activeAt + 300L;
        }

        Vec3 currentEyeCenter(long serverNow) {
            long activeElapsed = Math.max(0L, serverNow - activeAt);
            return SluiceTowerRoxy.typhoonEyePosition(cell, startAngle, sweepSign, activeElapsed);
        }
    }

    static final class OutsideZapVisual {
        final Vec3 pos;
        final long startAt;
        final long endAt;

        OutsideZapVisual(Vec3 pos, long startAt, long endAt) {
            this.pos = pos;
            this.startAt = startAt;
            this.endAt = endAt;
        }
    }

    private static Callbacks callbacks;
    private static Object world;
    private static String activeInstance = "";

    static boolean stormActive = false;
    static double stormStrength = 0.0;
    static double thunderStrength = 0.0;
    static Vec3 stormCenter = Vec3.ZERO;
    static int activeBossEntityId = -1;

    static TransitionVisual transitionVisual;
    static TyphoonEyeVisual typhoonEye;
    static final Map<String, HydroCoreVisual> hydroCores = new LinkedHashMap<>();
    static final Map<String, WaterProjectile> projectiles = new LinkedHashMap<>();
    static final Map<String, AreaHazard> areas = new LinkedHashMap<>();
    static final List<OutsideZapVisual> outsideZaps = new ArrayList<>();
    private static final Map<String, Integer> castHitCounts = new HashMap<>();

    // 电影级大标题横幅 + 倒计时条 + 咏唱字幕 + 瞬时微闪光
    private static String cinematicMain = "";
    private static String cinematicSub = "";
    private static long cinematicStartLocal = 0L;
    private static long cinematicEndLocal = 0L;
    private static int cinematicAccent = 0xff7ce6ff;

    private static String bannerTitle = "";
    private static long bannerUntil = 0L;
    private static int bannerColor = 0xff9ceeff;

    private static String quoteText = "";
    private static long quoteUntil = 0L;

    private static long flashStartLocal = 0L;
    private static long flashDurationLocal = 220L;
    private static float flashPeakAlpha = 0.0f;
    private static long nextAmbientThunderLocal = 0L;
    private static int zapSeq = 0;

    private RoxyBossClient() {}

    public static void register(Callbacks cb) {
        callbacks = Objects.requireNonNull(cb);
        SluiceBossState.install(
            () -> activeInstance.isEmpty() ? 0.0 : stormStrength,
            () -> activeInstance.isEmpty() ? 0.0 : thunderStrength,
            () -> !activeInstance.isEmpty() && stormStrength > 0.25
        );
        NeoForge.EVENT_BUS.addListener(RoxyBossClient::tick);
    }

    public static boolean isStormActive() {
        return !activeInstance.isEmpty() && (stormActive || stormStrength > 0.15);
    }

    public static void clear() {
        activeInstance = "";
        world = null;
        stormActive = false;
        stormStrength = 0.0;
        thunderStrength = 0.0;
        stormCenter = Vec3.ZERO;
        activeBossEntityId = -1;
        transitionVisual = null;
        typhoonEye = null;
        hydroCores.clear();
        projectiles.clear();
        areas.clear();
        outsideZaps.clear();
        castHitCounts.clear();
        cinematicMain = "";
        cinematicSub = "";
        cinematicEndLocal = 0L;
        bannerTitle = "";
        bannerUntil = 0L;
        quoteText = "";
        quoteUntil = 0L;
        flashPeakAlpha = 0.0f;
        nextAmbientThunderLocal = 0L;
    }

    public static void onEvent(JsonObject packet) {
        if (callbacks == null || packet == null || !packet.has("kind")) return;
        syncContext();
        String kind = packet.get("kind").getAsString();
        String instance = packet.has("instance") ? packet.get("instance").getAsString() : "";
        long localNow = ClientUi.now();
        long serverNow = ClientUi.current().serverNow();

        switch (kind) {
            case "INTRO_TITLE" -> {
                if (!instance.isEmpty()) activeInstance = instance;
                showCinematicTitle("水圣魔术师 · 洛琪希", "WATER SAINT — ROXY MIGURDIA", 0xff7ce6ff, 3_600L);
                showQuote("「请多指教。作为水闸塔的守关者，我会全力以赴。」", localNow + 3_800L);
                playLocalSound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.75f, 1.15f);
            }
            case "PHASE_TRANSITION" -> {
                if (!instance.isEmpty()) activeInstance = instance;
                int bossId = packet.has("bossEntityId") ? packet.get("bossEntityId").getAsInt() : -1;
                activeBossEntityId = bossId;
                Vec3 center = new Vec3(packet.get("x").getAsDouble(), packet.get("y").getAsDouble(), packet.get("z").getAsDouble());
                long startAt = packet.has("startAt") ? packet.get("startAt").getAsLong() : serverNow;
                long dur = packet.has("durationMs") ? packet.get("durationMs").getAsLong() : SluiceTowerRoxy.TRANSITION_TOTAL_MS;
                int coreTotal = packet.has("coreTotal") ? packet.get("coreTotal").getAsInt() : SluiceTowerRoxy.TRANSITION_CORE_COUNT;
                List<Vec3> pools = decodePositions(packet.getAsJsonArray("pools"));

                stormActive = true;
                stormCenter = center;
                transitionVisual = new TransitionVisual(bossId, center, startAt, startAt + dur, coreTotal, pools);
                hydroCores.clear();
                typhoonEye = null;

                showCinematicTitle("水王级魔术 · 豪雷积雨云", "CUMULONIMBUS — STORM OF THE WATER KING", 0xff8ceeff, 4_200L);
                showBanner("击碎水魔导核心以削弱《豪雷积雨云》 (剩余 " + coreTotal + "/" + coreTotal + ")", localNow + dur, 0xff92f0ff);
                showQuote("「……看来必须拿出真本事了。」", localNow + 2_600L);
                triggerLightningFlash(0.45f);
                playLocalSound(SoundEvents.LIGHTNING_BOLT_THUNDER, 0.85f, 0.82f);
                CameraShake.impulse(center, 60, 0.45f, 180, 360);
            }
            case "HYDRO_CORES_SPAWNED" -> {
                if (!instance.isEmpty()) activeInstance = instance;
                long spawnAt = packet.has("spawnAt") ? packet.get("spawnAt").getAsLong() : serverNow;
                long endAt = packet.has("endAt") ? packet.get("endAt").getAsLong() : serverNow + 9_800L;
                hydroCores.clear();
                if (packet.has("cores") && packet.get("cores").isJsonArray()) {
                    for (JsonElement elem : packet.getAsJsonArray("cores")) {
                        JsonObject o = elem.getAsJsonObject();
                        String key = o.get("key").getAsString();
                        int eid = o.get("entityId").getAsInt();
                        Vec3 pos = new Vec3(o.get("x").getAsDouble(), o.get("y").getAsDouble(), o.get("z").getAsDouble());
                        hydroCores.put(key, new HydroCoreVisual(key, eid, pos, spawnAt, endAt));
                    }
                }
                playLocalSound(SoundEvents.BEACON_ACTIVATE, 0.90f, 1.35f);
            }
            case "HYDRO_CORE_BROKEN" -> {
                String key = packet.has("coreKey") ? packet.get("coreKey").getAsString() : "";
                int broken = packet.has("broken") ? packet.get("broken").getAsInt() : 0;
                int total = packet.has("total") ? packet.get("total").getAsInt() : SluiceTowerRoxy.TRANSITION_CORE_COUNT;
                HydroCoreVisual hv = hydroCores.get(key);
                if (hv != null) {
                    hv.broken = true;
                    hv.brokenAt = serverNow;
                }
                if (transitionVisual != null) {
                    transitionVisual.brokenCores = broken;
                    int remain = Math.max(0, total - broken);
                    if (remain > 0) {
                        bannerTitle = "击碎水魔导核心以削弱《豪雷积雨云》 (剩余 " + remain + "/" + total + ")";
                    } else {
                        bannerTitle = "水魔导核心全数击碎！咏唱魔力发生反噬";
                        bannerColor = 0xffffe28a;
                    }
                }
                Vec3 pos = packet.has("x")
                    ? new Vec3(packet.get("x").getAsDouble(), packet.get("y").getAsDouble(), packet.get("z").getAsDouble())
                    : stormCenter;
                playAt(pos, SoundEvents.GLASS_BREAK, 1.0f, 1.25f);
                playAt(pos, SoundEvents.AMETHYST_CLUSTER_BREAK, 1.0f, 1.15f);
                CameraShake.impulse(pos, 32, 0.30f, 90, 180);
            }
            case "PHASE2_ENTERED" -> {
                if (!instance.isEmpty()) activeInstance = instance;
                stormActive = true;
                stormStrength = 1.0;
                thunderStrength = 0.85;
                if (packet.has("x")) {
                    stormCenter = new Vec3(packet.get("x").getAsDouble(), packet.get("y").getAsDouble(), packet.get("z").getAsDouble());
                }
                int broken = packet.has("brokenCores") ? packet.get("brokenCores").getAsInt() : 0;
                int total = packet.has("totalCores") ? packet.get("totalCores").getAsInt() : SluiceTowerRoxy.TRANSITION_CORE_COUNT;
                long vulnMs = packet.has("vulnerableMs") ? packet.get("vulnerableMs").getAsLong() : 0L;
                transitionVisual = null;
                hydroCores.clear();
                triggerLightningFlash(0.85f);
                CameraShake.impulse(stormCenter, 64, 0.72f, 220, 420);
                playLocalSound(SoundEvents.LIGHTNING_BOLT_THUNDER, 1.15f, 0.78f);
                playLocalSound(SoundEvents.TRIDENT_THUNDER.value(), 0.95f, 0.92f);

                if (vulnMs > 0L) {
                    showBanner("核心全破！洛琪希魔力反噬（易伤窗口）", localNow + vulnMs, 0xffffe28a);
                    showQuote("「唔……共鸣核心被全数打碎了！魔力回流……」", localNow + 3_400L);
                } else {
                    int shieldPct = (int) Math.round((packet.has("shieldFraction") ? packet.get("shieldFraction").getAsDouble() : 0.0) * 100.0);
                    showBanner("豪雷积雨云降临（击破 " + broken + "/" + total + " 核 · 水幕护盾 " + shieldPct + "%）", localNow + 3_500L, 0xff8ceeff);
                    showQuote("「在暴雨与雷霆之中，请展现你的觉悟吧！」", localNow + 3_500L);
                }
            }
            case "STORM_STATE" -> {
                if (!instance.isEmpty()) activeInstance = instance;
                boolean active = packet.has("active") && packet.get("active").getAsBoolean();
                stormActive = active;
                if (active) {
                    stormStrength = 1.0;
                    thunderStrength = 0.85;
                    if (packet.has("x")) {
                        stormCenter = new Vec3(packet.get("x").getAsDouble(), packet.get("y").getAsDouble(), packet.get("z").getAsDouble());
                    }
                } else {
                    stormStrength = 0.0;
                    thunderStrength = 0.0;
                }
            }
            case "TYPHOON_EYE_START" -> {
                if (!instance.isEmpty()) activeInstance = instance;
                Target boss = JSON.fromJson(packet.get("boss"), Target.class);
                int bossId = packet.has("bossEntityId") ? packet.get("bossEntityId").getAsInt() : -1;
                activeBossEntityId = bossId;
                Vec3 center = new Vec3(packet.get("x").getAsDouble(), packet.get("y").getAsDouble(), packet.get("z").getAsDouble());
                int cell = packet.has("cell") ? packet.get("cell").getAsInt() : 0;
                long startAt = packet.get("startAt").getAsLong();
                long activeAt = packet.get("activeAt").getAsLong();
                long endAt = packet.get("endAt").getAsLong();
                double startAngle = packet.get("startAngle").getAsDouble();
                double sweepSign = packet.get("sweepSign").getAsDouble();
                double eyeRadius = packet.get("eyeRadius").getAsDouble();
                long outsideTickMs = packet.get("outsideTickMs").getAsLong();
                double outsideCoeff = packet.get("outsideCoeff").getAsDouble();
                String id = packet.get("id").getAsString();

                stormActive = true;
                stormCenter = center;
                typhoonEye = new TyphoonEyeVisual(id, boss, bossId, center, cell, startAt, activeAt, endAt,
                    startAngle, sweepSign, eyeRadius, outsideTickMs, outsideCoeff);
                long totalDur = Math.max(1_000L, endAt - startAt);
                showCinematicTitle("水王奥义 · 天穹雷狱", "EYE OF THE MAELSTROM", 0xffa6f4ff, 3_000L);
                showBanner("紧跟晴空台风眼安全区", localNow + totalDur, 0xff9cf4ff);
                showQuote("「暴风与雷霆啊，化作吞噬万物的漩涡吧！」", localNow + 3_200L);
                triggerLightningFlash(0.65f);
                playLocalSound(SoundEvents.LIGHTNING_BOLT_THUNDER, 1.05f, 0.72f);
                CameraShake.impulse(center, 60, 0.50f, 180, 360);
            }
            case "CLEAR_HAZARDS" -> {
                projectiles.clear();
                areas.clear();
                outsideZaps.clear();
                castHitCounts.clear();
                typhoonEye = null;
            }
            case "DEFEAT_QUOTE" -> {
                long dur = packet.has("durationMs") ? packet.get("durationMs").getAsLong() : 3_600L;
                showCinematicTitle("暴雨止息", "THE STORM SUBSIDES", 0xffd2f6ff, 3_000L);
                showQuote("「真是精彩的战斗……看来，连我的《豪雷积雨云》也被你超越了呢。」", localNow + dur);
                playLocalSound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.80f, 1.05f);
            }
            case "CLEAR_ENCOUNTER_EFFECTS" -> {
                stormActive = false;
                stormStrength = 0.0;
                thunderStrength = 0.0;
                stormCenter = Vec3.ZERO;
                activeBossEntityId = -1;
                transitionVisual = null;
                typhoonEye = null;
                hydroCores.clear();
                projectiles.clear();
                areas.clear();
                outsideZaps.clear();
                castHitCounts.clear();
                bannerTitle = "";
                bannerUntil = 0L;
            }
            default -> {}
        }
    }

    public static void onHazard(JsonObject packet) {
        if (callbacks == null || packet == null || !packet.has("id")) return;
        syncContext();
        String instance = packet.has("instance") ? packet.get("instance").getAsString() : "";
        if (!instance.isEmpty()) activeInstance = instance;

        String id = packet.get("id").getAsString();
        String type = packet.get("type").getAsString();
        Target boss = JSON.fromJson(packet.get("boss"), Target.class);
        int bossEntityId = packet.has("bossEntityId") ? packet.get("bossEntityId").getAsInt() : -1;
        if (bossEntityId >= 0) activeBossEntityId = bossEntityId;
        Vec3 from = new Vec3(packet.get("x").getAsDouble(), packet.get("y").getAsDouble(), packet.get("z").getAsDouble());
        Vec3 to = new Vec3(packet.get("tx").getAsDouble(), packet.get("ty").getAsDouble(), packet.get("tz").getAsDouble());
        double radius = packet.get("radius").getAsDouble();
        double auxRadius = packet.has("auxRadius") ? packet.get("auxRadius").getAsDouble() : 0.0;
        double speed = packet.has("speed") ? packet.get("speed").getAsDouble() : 0.0;
        long startAt = packet.get("startAt").getAsLong();
        long warnUntil = packet.get("warnUntil").getAsLong();
        long endAt = packet.get("endAt").getAsLong();
        long intervalMs = packet.has("intervalMs") ? packet.get("intervalMs").getAsLong() : 0L;
        double coeff = packet.has("coefficient") ? packet.get("coefficient").getAsDouble() : 0.0;
        int element = packet.has("element") ? packet.get("element").getAsInt() : SluiceTowerRoxy.ELEMENT_ICE;
        boolean magic = !packet.has("magic") || packet.get("magic").getAsBoolean();
        int count = packet.has("count") ? packet.get("count").getAsInt() : 0;
        long lifeMs = packet.has("lifeMs") ? packet.get("lifeMs").getAsLong() : 0L;
        double baseAngle = packet.has("baseAngle") ? packet.get("baseAngle").getAsDouble() : 0.0;
        int color = packet.has("color") ? packet.get("color").getAsInt() : 0x58c8ff;

        if ("WATER_BARRAGE".equals(type)) {
            int total = Math.max(1, count);
            long stepMs = Math.max(40L, intervalMs);
            Vec3 fallbackFwd = horizontalDir(from, to);
            for (int i = 0; i < total; i++) {
                long launchAt = warnUntil + i * stepMs;
                long expireAt = launchAt + Math.max(600L, lifeMs);
                Vec3 slotPos = computeBackOrbPos(bossEntityId, from, fallbackFwd, i, total);
                String pid = id + "#" + i;
                projectiles.put(pid, new WaterProjectile(
                    pid, id, instance, boss, bossEntityId, true, i, total, SluiceTowerRoxy.SKILL1_MAX_HITS_PER_CAST,
                    slotPos, fallbackFwd, startAt, launchAt, expireAt,
                    speed, auxRadius, radius, coeff, element, color
                ));
            }
            playAt(from, SoundEvents.PLAYER_SPLASH, 0.80f, 1.30f);
            return;
        }

        areas.put(id, new AreaHazard(
            id, instance, type, boss, bossEntityId, from, to, radius, auxRadius, speed,
            startAt, warnUntil, endAt, lifeMs, coeff, element, magic, count, baseAngle, color
        ));
    }

    private static void syncContext() {
        var mc = Minecraft.getInstance();
        Object currentWorld = mc.level;
        Life own = callbacks == null ? null : callbacks.own();
        String ownInstance = own == null ? "" : own.instance;
        if (currentWorld != world || (!activeInstance.isEmpty() && !activeInstance.equals(ownInstance))) {
            clear();
            world = currentWorld;
        }
    }

    private static void tick(ClientTickEvent.Post event) {
        if (callbacks == null) return;
        syncContext();
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !ClientUi.current().ready()) return;

        long serverNow = ClientUi.current().serverNow();
        long localNow = ClientUi.now();
        Life own = callbacks.own();
        boolean canHit = own != null && !own.dead && !activeInstance.isEmpty() && activeInstance.equals(own.instance);

        // 1. 推进 12s《豪雷积雨云》转场逐句咏唱台词与渐进暴雨天象强度
        if (transitionVisual != null) {
            long elapsed = serverNow - transitionVisual.startAt;
            if (elapsed >= SluiceTowerRoxy.TRANSITION_KNEEL_END_MS) {
                double ramp = Math.clamp((elapsed - SluiceTowerRoxy.TRANSITION_KNEEL_END_MS) / 3_500.0, 0.0, 1.0);
                stormStrength = Math.max(stormStrength, ramp);
                thunderStrength = Math.max(thunderStrength, ramp * 0.85);
            }
            int step = elapsed < 2_200L ? 0 : elapsed < 5_200L ? 1 : elapsed < 8_200L ? 2 : 3;
            if (step != transitionVisual.lastChantStep) {
                transitionVisual.lastChantStep = step;
                switch (step) {
                    case 1 -> {
                        showQuote("「伟大之水的精灵啊，请倾听吾之祈愿——」", localNow + 3_000L);
                        playLocalSound(SoundEvents.WATER_AMBIENT, 1.0f, 0.85f);
                    }
                    case 2 -> {
                        showQuote("「汇聚四海之潮，凝结苍穹雷云，降下涤荡万物之暴雨！」", localNow + 3_000L);
                        triggerLightningFlash(0.42f);
                        playLocalSound(SoundEvents.LIGHTNING_BOLT_THUNDER, 0.90f, 0.88f);
                    }
                    case 3 -> {
                        showQuote("「水王级魔术——豪雷积雨云！！」", localNow + 3_800L);
                        triggerLightningFlash(0.60f);
                        playLocalSound(SoundEvents.LIGHTNING_BOLT_THUNDER, 1.05f, 0.78f);
                    }
                    default -> {}
                }
            }
            if (serverNow > transitionVisual.endAt + 600L) {
                transitionVisual = null;
            }
        } else if (stormActive) {
            stormStrength = Math.min(1.0, stormStrength + 0.05);
            double targetThunder = typhoonEye != null ? 1.0 : 0.85;
            thunderStrength = thunderStrength + (targetThunder - thunderStrength) * 0.12;
        } else {
            stormStrength = Math.max(0.0, stormStrength - 0.08);
            thunderStrength = Math.max(0.0, thunderStrength - 0.08);
        }

        // 二阶段暴雨环境偶发远处闷雷与闪电闪光（增加史诗临场感）
        if (stormActive && stormStrength > 0.7 && localNow >= nextAmbientThunderLocal) {
            nextAmbientThunderLocal = localNow + 4_500L + (Math.abs((int) (localNow * 31L)) % 3_500);
            if (mc.level != null) mc.level.setSkyFlashTime(2);
            playLocalSound(SoundEvents.LIGHTNING_BOLT_THUNDER, 0.42f, 0.72f + ((localNow % 5) * 0.04f));
        }

        // 2. 清理过期的水核与眼外天雷视觉
        hydroCores.values().removeIf(hc -> serverNow > hc.endAt + 600L);
        outsideZaps.removeIf(z -> serverNow > z.endAt);

        // 3. 推进二阶段大招《天穹雷狱·台风眼》安全区生存判定
        if (typhoonEye != null) {
            if (!typhoonEye.soundPlayed && serverNow >= typhoonEye.activeAt) {
                typhoonEye.soundPlayed = true;
                triggerLightningFlash(0.55f);
                playLocalSound(SoundEvents.TRIDENT_RIPTIDE_3.value(), 0.95f, 0.72f);
            }
            if (canHit && serverNow >= typhoonEye.activeAt && serverNow <= typhoonEye.endAt) {
                if (serverNow >= typhoonEye.nextOutsideTickAt) {
                    typhoonEye.nextOutsideTickAt = serverNow + Math.max(300L, typhoonEye.outsideTickMs);
                    Vec3 eyePos = typhoonEye.currentEyeCenter(serverNow);
                    if (!SluiceTowerRoxy.insideTyphoonEye(mc.player.position(), eyePos, typhoonEye.eyeRadius)) {
                        Vec3 pPos = mc.player.position();
                        outsideZaps.add(new OutsideZapVisual(pPos, serverNow, serverNow + 360L));
                        triggerLightningFlash(0.45f);
                        playAt(pPos, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.85f, 1.05f);
                        CameraShake.impulse(pPos, 18, 0.30f, 60, 140);
                        emitHit(typhoonEye.boss, typhoonEye.id + "/outside/" + (++zapSeq), "TYPHOON_OUTSIDE_BOLT",
                            typhoonEye.outsideCoeff, SluiceTowerRoxy.ELEMENT_LIGHT, true, serverNow);
                    }
                }
            }
            if (serverNow > typhoonEye.endAt + 500L) {
                typhoonEye = null;
            }
        }

        // 4. 推进水圣弹幕与水镜水箭
        tickProjectiles(mc.player, serverNow, canHit);

        // 5. 推进全部地面与空间区域判定
        tickAreas(mc.player, serverNow, canHit);
    }

    private static void tickProjectiles(net.minecraft.world.entity.player.Player player, long serverNow, boolean canHit) {
        AABB playerBox = player.getBoundingBox();
        Vec3 playerCenter = player.position().add(0, player.getBbHeight() * 0.55, 0);

        Iterator<WaterProjectile> it = projectiles.values().iterator();
        while (it.hasNext()) {
            WaterProjectile p = it.next();
            if (p.consumed || serverNow > p.endAt + 200L) {
                it.remove();
                continue;
            }
            if (serverNow < p.launchAt) {
                if (p.backAttached) {
                    Vec3 fwd = resolveBossForward(p.bossEntityId, p.dir);
                    p.spawnPos = computeBackOrbPos(p.bossEntityId, p.spawnPos, fwd, p.slotIndex, p.slotTotal);
                    p.pos = p.spawnPos;
                    p.prevPos = p.spawnPos;
                    Vec3 aim = playerCenter.subtract(p.spawnPos);
                    if (aim.lengthSqr() > 1e-6) p.dir = aim.normalize();
                }
                p.lastSimAt = p.launchAt;
                continue;
            }
            if (!p.launched) {
                p.launched = true;
                p.pos = p.spawnPos;
                p.prevPos = p.spawnPos;
                if (p.backAttached) {
                    Vec3 aim = playerCenter.subtract(p.spawnPos);
                    if (aim.lengthSqr() > 1e-6) p.dir = aim.normalize();
                }
                p.lastSimAt = p.launchAt;
                playAt(p.pos, SoundEvents.TRIDENT_THROW.value(), 0.42f, 1.32f);
            }

            long dtMs = Math.clamp(serverNow - p.lastSimAt, 0L, 120L);
            if (dtMs <= 0L) continue;
            double dt = dtMs / 1000.0;
            p.lastSimAt = serverNow;
            p.prevPos = p.pos;
            if (p.turnDegPerSec > 0.1 && serverNow - p.launchAt < 1_600L) {
                Vec3 toPlayer = playerCenter.subtract(p.pos);
                p.dir = SluiceTowerRoxy.stepHomingDirection(p.dir, toPlayer, p.turnDegPerSec, dt);
            }
            p.pos = p.pos.add(p.dir.scale(p.speed * dt));

            if (canHit && !p.consumed) {
                AABB hitBox = playerBox.inflate(p.hitRadius);
                if (hitBox.contains(p.pos) || hitBox.clip(p.prevPos, p.pos).isPresent()) {
                    p.consumed = true;
                    int hitCount = castHitCounts.getOrDefault(p.castKey, 0);
                    if (p.maxHitsPerCast <= 0 || hitCount < p.maxHitsPerCast) {
                        castHitCounts.put(p.castKey, hitCount + 1);
                        emitHit(p.boss, p.id, "WATER_ORB", p.coefficient, p.element, true, serverNow);
                    }
                    it.remove();
                }
            }
        }
    }

    private static void tickAreas(net.minecraft.world.entity.player.Player player, long serverNow, boolean canHit) {
        Iterator<AreaHazard> it = areas.values().iterator();
        while (it.hasNext()) {
            AreaHazard a = it.next();
            if (serverNow > a.endAt + 450L) {
                it.remove();
                continue;
            }
            switch (a.type) {
                case "AQUA_BLADE" -> {
                    if (serverNow < a.warnUntil || serverNow > a.endAt) continue;
                    if (!a.soundPlayed) {
                        a.soundPlayed = true;
                        playAt(a.from, SoundEvents.TRIDENT_RIPTIDE_2.value(), 0.85f, 1.15f);
                    }
                    if (canHit && !a.struck && a.coefficient > 0) {
                        double prog = Math.clamp((serverNow - a.warnUntil) / (double) Math.max(1L, a.endAt - a.warnUntil), 0.0, 1.0);
                        Vec3 front = a.from.lerp(a.to, prog);
                        Vec3 back = a.from.lerp(a.to, Math.max(0.0, prog - 0.24));
                        if (insideCorridor(player.position(), back, front, a.radius)) {
                            a.struck = true;
                            emitHit(a.boss, a.id, "AQUA_BLADE", a.coefficient, a.element, a.magic, serverNow);
                        }
                    }
                }
                case "GEYSER_COLUMN", "THUNDER_CASCADE", "TRANSITION_BOLT", "TYPHOON_INNER_STRIKE",
                     "TRANSITION_LAND_WAVE", "TYPHOON_FINALE_BLAST" -> {
                    if (serverNow < a.warnUntil || serverNow > a.endAt) continue;
                    if (!a.soundPlayed) {
                        a.soundPlayed = true;
                        if ("GEYSER_COLUMN".equals(a.type)) {
                            playAt(a.from, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, 0.95f, 0.82f);
                            playAt(a.from, SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT, 0.90f, 1.1f);
                        } else {
                            playAt(a.from, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.90f, 0.92f);
                            if ("THUNDER_CASCADE".equals(a.type) || "TYPHOON_FINALE_BLAST".equals(a.type)) {
                                triggerLightningFlash(0.32f);
                            }
                        }
                    }
                    if (canHit && !a.struck && a.coefficient > 0) {
                        Vec3 offset = player.position().subtract(a.from);
                        double r = a.radius + 0.35;
                        if (Math.abs(offset.y) <= 5.5 && offset.horizontalDistanceSqr() <= r * r) {
                            a.struck = true;
                            emitHit(a.boss, a.id, a.type, a.coefficient, a.element, a.magic, serverNow);
                        }
                    }
                }
                case "MIRROR_TRAP" -> {
                    if (serverNow < a.warnUntil) continue;
                    if (!a.spawnedBolts) {
                        a.spawnedBolts = true;
                        playAt(a.from, SoundEvents.GLASS_BREAK, 0.95f, 1.35f);
                        playAt(a.from, SoundEvents.PLAYER_SPLASH, 0.85f, 1.15f);
                        int bolts = Math.max(4, a.count);
                        Vec3 origin = a.from.add(0, 1.05, 0);
                        for (int i = 0; i < bolts; i++) {
                            double ang = a.baseAngle + i * (Math.PI * 2.0 / bolts);
                            Vec3 dir = new Vec3(Math.cos(ang), 0.0, Math.sin(ang));
                            String pid = a.id + "/bolt#" + i;
                            projectiles.put(pid, new WaterProjectile(
                                pid, a.id + "/bolts", a.instance, a.boss, a.bossEntityId,
                                false, i, bolts, 2,
                                origin, dir, a.warnUntil, a.warnUntil, a.warnUntil + Math.max(800L, a.lifeMs),
                                a.speed, 0.0, 0.72, a.auxRadius, SluiceTowerRoxy.ELEMENT_ICE, 0x8cf0ff
                            ));
                        }
                    }
                    if (canHit && !a.struck && serverNow <= a.warnUntil + 380L && a.coefficient > 0) {
                        Vec3 offset = player.position().subtract(a.from);
                        double r = a.radius + 0.35;
                        if (Math.abs(offset.y) <= 4.5 && offset.horizontalDistanceSqr() <= r * r) {
                            a.struck = true;
                            emitHit(a.boss, a.id + "/burst", "MIRROR_BURST", a.coefficient, a.element, a.magic, serverNow);
                        }
                    }
                }
                case "TIDAL_RING" -> {
                    if (serverNow < a.warnUntil || serverNow > a.endAt) continue;
                    if (!a.soundPlayed) {
                        a.soundPlayed = true;
                        playAt(a.from, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, 0.95f, 0.95f);
                    }
                    if (canHit && !a.struck && a.coefficient > 0) {
                        Vec3 offset = player.position().subtract(a.from);
                        double dist = offset.horizontalDistance();
                        if (Math.abs(offset.y) <= 5.0
                            && dist >= Math.max(0.0, a.auxRadius - 0.25)
                            && dist <= a.radius + 0.35) {
                            a.struck = true;
                            emitHit(a.boss, a.id, "TIDAL_RING", a.coefficient, a.element, a.magic, serverNow);
                        }
                    }
                }
                default -> {}
            }
        }
    }

    private static boolean insideCorridor(Vec3 point, Vec3 start, Vec3 end, double width) {
        if (Math.abs(point.y - start.y) > 4.2) return false;
        double dx = end.x - start.x, dz = end.z - start.z;
        double lenSq = dx * dx + dz * dz;
        if (lenSq < 1e-6) {
            double r = width * 0.5 + 0.35;
            double ox = point.x - start.x, oz = point.z - start.z;
            return ox * ox + oz * oz <= r * r;
        }
        double px = point.x - start.x, pz = point.z - start.z;
        double t = Math.clamp((px * dx + pz * dz) / lenSq, 0.0, 1.0);
        double cx = px - t * dx, cz = pz - t * dz;
        double half = width * 0.5 + 0.32;
        return cx * cx + cz * cz <= half * half;
    }

    private static void emitHit(Target boss, String hitId, String kind, double coeff,
                                int element, boolean magic, long serverNow) {
        JsonObject action = new JsonObject();
        action.addProperty("id", "roxy/" + hitId);
        action.addProperty("clientContact", true);
        action.add("boss", JSON.toJsonTree(boss));
        action.addProperty("kind", kind);
        action.addProperty("coefficient", coeff);
        action.addProperty("magic", magic);
        action.addProperty("element", element);
        action.addProperty("occurredAt", serverNow);
        callbacks.bossContact(action);
    }

    private static void showCinematicTitle(String mainTitle, String subTitle, int accentRgb, long durationMs) {
        long now = ClientUi.now();
        cinematicMain = mainTitle;
        cinematicSub = subTitle;
        cinematicAccent = accentRgb;
        cinematicStartLocal = now;
        cinematicEndLocal = now + Math.max(1_200L, durationMs);
    }

    private static void showBanner(String title, long untilLocalMs, int color) {
        bannerTitle = title;
        bannerUntil = untilLocalMs;
        bannerColor = color;
    }

    private static void showQuote(String text, long untilLocalMs) {
        quoteText = text;
        quoteUntil = untilLocalMs;
    }

    static void triggerLightningFlash(float peakIntensity) {
        long now = ClientUi.now();
        flashStartLocal = now;
        flashDurationLocal = 220L;
        flashPeakAlpha = Math.clamp(peakIntensity, 0.0f, 1.0f);
        var mc = Minecraft.getInstance();
        if (mc.level != null) {
            mc.level.setSkyFlashTime(2);
        }
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

    private static Vec3 computeBackOrbPos(int bossEntityId, Vec3 fallbackPos, Vec3 fallbackForward, int index, int total) {
        Vec3 base = resolveBossPos(bossEntityId, fallbackPos);
        Vec3 forward = resolveBossForward(bossEntityId, fallbackForward);
        Vec3 right = new Vec3(-forward.z, 0, forward.x);
        double[] off = SluiceTowerRoxy.backOrbSlotOffset(index, total);
        return base.add(right.scale(off[0])).add(0, off[1], 0).subtract(forward.scale(off[2]));
    }

    private static List<Vec3> decodePositions(com.google.gson.JsonArray arr) {
        List<Vec3> out = new ArrayList<>();
        if (arr == null) return out;
        for (JsonElement elem : arr) {
            JsonObject o = elem.getAsJsonObject();
            out.add(new Vec3(o.get("x").getAsDouble(), o.get("y").getAsDouble(), o.get("z").getAsDouble()));
        }
        return out;
    }

    private static Vec3 horizontalDir(Vec3 from, Vec3 to) {
        Vec3 d = new Vec3(to.x - from.x, 0, to.z - from.z);
        return d.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : d.normalize();
    }

    private static void playAt(Vec3 pos, SoundEvent sound, float volume, float pitch) {
        var mc = Minecraft.getInstance();
        if (mc.level != null) {
            mc.level.playLocalSound(pos.x, pos.y + 0.8, pos.z, sound, SoundSource.HOSTILE, volume, pitch, false);
        }
    }

    private static void playLocalSound(SoundEvent sound, float volume, float pitch) {
        var mc = Minecraft.getInstance();
        if (mc.level != null && mc.player != null) {
            mc.level.playLocalSound(mc.player.getX(), mc.player.getY(), mc.player.getZ(), sound, SoundSource.HOSTILE, volume, pitch, false);
        }
    }

    /** 电影级全屏氛围 + 惊雷微闪光 + 渐变大标题横幅 + 倒计时与咏唱字幕。 */
    public static void hud(GuiGraphicsExtractor g) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.gui.hud.isHidden() || mc.gui.screen() != null) return;
        long now = ClientUi.now();
        int sw = g.guiWidth();
        int sh = g.guiHeight();

        // 1. 轻度暴雨边缘氛围（仅在视野外缘叠加柔和深海蓝暗角，绝不遮挡中心战斗区）
        if (!activeInstance.isEmpty() && stormStrength > 0.05) {
            int bandH = Math.max(18, sh / 10);
            int topAlpha = (int) Math.round(42 * stormStrength);
            int botAlpha = (int) Math.round(36 * stormStrength);
            int topColor = (topAlpha << 24) | 0x0a1c34;
            int botColor = (botAlpha << 24) | 0x08162a;
            g.fillGradient(0, 0, sw, bandH, topColor, 0x000a1c34);
            g.fillGradient(0, sh - bandH, sw, sh, 0x0008162a, botColor);
            // 左右外侧 10% 极轻雨丝微光（提升暴风雨浸入感）
            int sideW = Math.max(16, sw / 12);
            int streakAlpha = (int) Math.round(38 * stormStrength);
            int streakColor = (streakAlpha << 24) | 0x9ce8ff;
            for (int i = 0; i < 6; i++) {
                int rx = (int) (((i * 47L + (now / 18L)) % sideW));
                int ry = (int) (((i * 97L + (now / 4L)) % (sh + 24)) - 12);
                g.fill(rx, ry, rx + 1, ry + 11, streakColor);
                int rx2 = sw - 1 - ((int) (((i * 53L + (now / 16L)) % sideW)));
                int ry2 = (int) (((i * 83L + (now / 3L)) % (sh + 24)) - 12);
                g.fill(rx2, ry2, rx2 + 1, ry2 + 11, streakColor);
            }
        }

        // 2. 惊雷瞬时微闪光（220ms 柔和指数衰减，最大透明度 <= 54，绝不刺眼）
        if (flashPeakAlpha > 0.01f && now >= flashStartLocal && now < flashStartLocal + flashDurationLocal) {
            float u = 1.0f - (now - flashStartLocal) / (float) Math.max(1L, flashDurationLocal);
            int a = (int) Math.round(54.0f * flashPeakAlpha * u * u);
            if (a > 1) {
                g.fill(0, 0, sw, sh, (a << 24) | 0xdcf6ff);
            }
        }

        var font = UiFonts.font();
        int nextY = 50;

        // 3. 精致渐变电影大标题横幅（登场 / 豪雷积雨云 / 天穹雷狱 / 暴雨止息）
        if (!cinematicMain.isEmpty() && now >= cinematicStartLocal && now < cinematicEndLocal) {
            long total = Math.max(1L, cinematicEndLocal - cinematicStartLocal);
            long elapsed = now - cinematicStartLocal;
            float fadeIn = Math.clamp(elapsed / 380.0f, 0.0f, 1.0f);
            float fadeOut = Math.clamp((cinematicEndLocal - now) / 550.0f, 0.0f, 1.0f);
            float alpha = Math.min(fadeIn, fadeOut);
            if (alpha > 0.02f) {
                int mainW = font.width(cinematicMain);
                int subW = font.width(cinematicSub);
                int cardW = Math.max(210, Math.max(mainW, subW) + 56);
                int cardH = cinematicSub.isEmpty() ? 24 : 34;
                int x = (sw - cardW) / 2;
                int bgA = (int) Math.round(168 * alpha);
                int lineA = (int) Math.round(225 * alpha);
                int txtA = Math.max(8, (int) Math.round(255 * alpha));
                int subA = Math.max(8, (int) Math.round(210 * alpha));

                g.fillGradient(x, nextY, x + cardW, nextY + cardH,
                    (bgA << 24) | 0x0b192e, (bgA << 24) | 0x102542);
                int lineRgb = cinematicAccent & 0x00ffffff;
                g.fill(x + 12, nextY, x + cardW - 12, nextY + 1, (lineA << 24) | lineRgb);
                g.fill(x + 12, nextY + cardH - 1, x + cardW - 12, nextY + cardH, (lineA << 24) | lineRgb);

                g.text(font, cinematicMain, (sw - mainW) / 2, nextY + 6, (txtA << 24) | lineRgb, true);
                if (!cinematicSub.isEmpty()) {
                    g.text(font, cinematicSub, (sw - subW) / 2, nextY + 19, (subA << 24) | 0xd6eeff, true);
                }
                nextY += cardH + 6;
            }
        }

        // 4. 机制倒计时横幅（破核计数 / 台风眼倒计时）
        if (!bannerTitle.isEmpty() && now < bannerUntil) {
            double remaining = Math.max(0.0, (bannerUntil - now) / 1000.0);
            String label = String.format(Locale.ROOT, "%s  %.1fs", bannerTitle, remaining);
            int w = font.width(label) + 18;
            int x = (sw - w) / 2;
            UiTheme.hud(g, x, nextY, w, 18);
            g.text(font, label, x + 9, nextY + 5, bannerColor, true);
            nextY += 22;
        }

        // 5. 逐句咏唱台词字幕条
        if (!quoteText.isEmpty() && now < quoteUntil) {
            int w = font.width(quoteText) + 18;
            int x = (sw - w) / 2;
            UiTheme.hud(g, x, nextY, w, 16);
            g.text(font, quoteText, x + 9, nextY + 4, stormActive ? 0xffbceeff : 0xffe4f4ff, true);
        }
    }
}
