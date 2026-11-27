package cn.yumis.fight.modules.dungeons.client;

import cn.yumis.fight.modules.combat.api.Life;
import cn.yumis.fight.modules.dungeons.api.Target;
import cn.yumis.fight.modules.dungeons.content.OrreryTowerGilgamesh;
import cn.yumis.fight.modules.presentation.api.client.ClientUi;
import cn.yumis.fight.modules.presentation.api.client.UiFonts;
import cn.yumis.fight.modules.presentation.api.client.UiTheme;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
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
 * 星仪塔第 20 层专属首领「吉尔伽美什（英雄王）」本机客户端权威控制器：
 * - 模拟王之财宝 3D 武器投射弹幕（背后弧阵齐射、360° 全方位环绕围杀）与连续碰撞判定；
 * - 结算宝具楔阵（坠落 + 5s 倒计时引爆，击碎自动拆除）、斥退刃环（含向外击退）、天穹剑雨、
 *   维摩那同心环带与高爆光球、乖离剑空间撕裂斩（风压 + 二次塌陷）及终焉裂空余波；
 * - 管理「天之锁」纯控制束缚（锁定移动输入但保留全部技能与普攻，击碎锚点挣脱，超时处决）；
 * - 判定「天地乖离开辟之星」律令版星柱护幕躲避与 1% 濒死终焉 DPS Check 团灭；
 * - 离场、换层、死亡或断线时彻底清理所有残留状态、天之锁束缚与赤金天象。
 */
public final class GilgameshBossClient {
    private static final Gson JSON = new Gson();

    public interface Callbacks {
        Life own();
        void bossContact(JsonObject hitPacket);
        void ratioDamage(double ratio, JsonObject boss);
        void instantDefeat(JsonObject boss);
    }

    static final class GateProjectile {
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
        final double hitRadius;
        final double coefficient;
        final int element;
        final boolean magic;
        final int weaponIndex;
        final int color;

        Vec3 gatePos;
        Vec3 pos;
        Vec3 prevPos;
        Vec3 dir;
        long lastSimAt;
        boolean launched;
        boolean consumed;

        GateProjectile(String id, String castKey, String instance, Target boss, int bossEntityId,
                       boolean backAttached, int slotIndex, int slotTotal, int maxHitsPerCast,
                       Vec3 gatePos, Vec3 initialDir,
                       long startAt, long launchAt, long endAt,
                       double speed, double hitRadius, double coefficient, int element, boolean magic,
                       int weaponIndex, int color) {
            this.id = id;
            this.castKey = castKey;
            this.instance = instance;
            this.boss = boss;
            this.bossEntityId = bossEntityId;
            this.backAttached = backAttached;
            this.slotIndex = slotIndex;
            this.slotTotal = slotTotal;
            this.maxHitsPerCast = maxHitsPerCast;
            this.gatePos = gatePos;
            this.pos = gatePos;
            this.prevPos = gatePos;
            this.dir = initialDir.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : initialDir.normalize();
            this.startAt = startAt;
            this.launchAt = launchAt;
            this.endAt = endAt;
            this.speed = speed;
            this.hitRadius = hitRadius;
            this.coefficient = coefficient;
            this.element = element;
            this.magic = magic;
            this.weaponIndex = weaponIndex;
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
        final long startAt;
        final long warnUntil;
        final long endAt;
        final long intervalMs;
        final long lifeMs;
        final double coefficient;
        final int element;
        final boolean magic;
        final int color;

        boolean struck;
        boolean secondaryStruck;
        boolean soundPlayed;
        boolean secondarySoundPlayed;

        AreaHazard(String id, String instance, String type, Target boss, int bossEntityId,
                   Vec3 from, Vec3 to, double radius, double auxRadius,
                   long startAt, long warnUntil, long endAt, long intervalMs, long lifeMs,
                   double coefficient, int element, boolean magic, int color) {
            this.id = id;
            this.instance = instance;
            this.type = type;
            this.boss = boss;
            this.bossEntityId = bossEntityId;
            this.from = from;
            this.to = to;
            this.radius = radius;
            this.auxRadius = auxRadius;
            this.startAt = startAt;
            this.warnUntil = warnUntil;
            this.endAt = endAt;
            this.intervalMs = intervalMs;
            this.lifeMs = lifeMs;
            this.coefficient = coefficient;
            this.element = element;
            this.magic = magic;
            this.color = color;
        }
    }

    static final class VimanaVisual {
        final Vec3 center;
        final double startAngle;
        final long startAt;
        final long endAt;
        Vec3 pos;
        Vec3 interpFrom;
        long interpAt;

        VimanaVisual(Vec3 center, double startAngle, long startAt, long endAt) {
            this.center = center;
            this.startAngle = startAngle;
            this.startAt = startAt;
            this.endAt = endAt;
            this.pos = center.add(0, OrreryTowerGilgamesh.VIMANA_HOVER_HEIGHT, 0);
            this.interpFrom = this.pos;
            this.interpAt = startAt;
        }

        Vec3 currentPos(long serverNow) {
            double alpha = Math.clamp((serverNow - interpAt) / (double) OrreryTowerGilgamesh.VIMANA_SYNC_INTERVAL_MS, 0.0, 1.0);
            return interpFrom.lerp(pos, alpha);
        }
    }

    static final class EnkiduBindVisual {
        final String bindId;
        final UUID playerId;
        final int anchorEntityId;
        final Vec3 playerAnchor;
        final Vec3 chainAnchor;
        final long startAt;
        final long expireAt;

        EnkiduBindVisual(String bindId, UUID playerId, int anchorEntityId,
                         Vec3 playerAnchor, Vec3 chainAnchor, long startAt, long expireAt) {
            this.bindId = bindId;
            this.playerId = playerId;
            this.anchorEntityId = anchorEntityId;
            this.playerAnchor = playerAnchor;
            this.chainAnchor = chainAnchor;
            this.startAt = startAt;
            this.expireAt = expireAt;
        }
    }

    static final class ExecuteBeamVisual {
        final Vec3 pos;
        final long startAt;
        final long endAt;

        ExecuteBeamVisual(Vec3 pos, long startAt, long endAt) {
            this.pos = pos;
            this.startAt = startAt;
            this.endAt = endAt;
        }
    }

    static final class DecreeVisual {
        final String id;
        final Vec3 center;
        final long startAt;
        final long revealAt;
        final long impactAt;
        final long endAt;
        final double shelterRadius;
        final List<Vec3> shelters;

        DecreeVisual(String id, Vec3 center, long startAt, long revealAt, long impactAt, long endAt,
                     double shelterRadius, List<Vec3> shelters) {
            this.id = id;
            this.center = center;
            this.startAt = startAt;
            this.revealAt = revealAt;
            this.impactAt = impactAt;
            this.endAt = endAt;
            this.shelterRadius = shelterRadius;
            this.shelters = shelters;
        }
    }

    static final class FinaleVisual {
        final Vec3 center;
        final long startAt;
        final long endAt;

        FinaleVisual(Vec3 center, long startAt, long endAt) {
            this.center = center;
            this.startAt = startAt;
            this.endAt = endAt;
        }
    }

    private static Callbacks callbacks;
    private static Object world;
    private static String activeInstance = "";
    private static String bannerTitle = "";
    private static String quoteText = "";
    private static long bannerUntil = 0L;
    private static long quoteUntil = 0L;
    private static int bannerColor = 0xffffd86b;
    private static final Set<String> handledEvents = new HashSet<>();
    private static final Map<String, Integer> castHitCounts = new HashMap<>();

    static final Map<String, GateProjectile> projectiles = new LinkedHashMap<>();
    static final Map<String, AreaHazard> areas = new LinkedHashMap<>();
    static final Map<String, EnkiduBindVisual> enkiduBinds = new LinkedHashMap<>();
    static final List<ExecuteBeamVisual> executeBeams = new ArrayList<>();
    static VimanaVisual vimanaVisual;
    static DecreeVisual decreeVisual;
    static FinaleVisual finaleVisual;
    static boolean eaSkyActive;
    static Vec3 eaSkyCenter = Vec3.ZERO;

    private GilgameshBossClient() {}

    public static void register(Callbacks cb) {
        callbacks = Objects.requireNonNull(cb);
        NeoForge.EVENT_BUS.addListener(GilgameshBossClient::tick);
    }

    /** 玩家正被「天之锁」纯控制束缚时仅锁定移动输入（不禁用任何技能或普攻）。 */
    public static boolean blocksMovement() {
        var mc = Minecraft.getInstance();
        if (mc.player == null || callbacks == null || callbacks.own().dead) return false;
        long serverNow = ClientUi.current().serverNow();
        UUID selfId = mc.player.getUUID();
        for (EnkiduBindVisual b : enkiduBinds.values()) {
            if (b.playerId.equals(selfId) && serverNow < b.expireAt) {
                return true;
            }
        }
        return false;
    }

    public static boolean isEaDrawn() {
        return eaSkyActive && !activeInstance.isEmpty();
    }

    public static void clear() {
        projectiles.clear();
        areas.clear();
        enkiduBinds.clear();
        executeBeams.clear();
        castHitCounts.clear();
        handledEvents.clear();
        vimanaVisual = null;
        decreeVisual = null;
        finaleVisual = null;
        eaSkyActive = false;
        eaSkyCenter = Vec3.ZERO;
        bannerTitle = "";
        bannerUntil = 0L;
        quoteText = "";
        quoteUntil = 0L;
        activeInstance = "";
        world = null;
    }

    private static void syncContext() {
        var mc = Minecraft.getInstance();
        Object nextWorld = mc.level;
        String currentInstance = callbacks == null ? "" : callbacks.own().instance;
        if (world != nextWorld || (!activeInstance.isEmpty() && !activeInstance.equals(currentInstance))) {
            clear();
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
        if ("CLEAR_ENCOUNTER_EFFECTS".equals(kind)) {
            clear();
            return;
        }
        if ("CLEAR_HAZARDS".equals(kind)) {
            projectiles.clear();
            areas.clear();
            enkiduBinds.clear();
            castHitCounts.clear();
            return;
        }
        String instance = pkt.has("instance") ? pkt.get("instance").getAsString() : "";
        if (instance.isEmpty() || !instance.equals(callbacks.own().instance)) return;
        activeInstance = instance;

        String eventId = pkt.has("eventId") ? pkt.get("eventId").getAsString() : "";
        if (!eventId.isEmpty() && !handledEvents.add(eventId)) return;

        long now = ClientUi.now();
        long serverNow = ClientUi.current().serverNow();
        long duration = pkt.has("durationMs") ? pkt.get("durationMs").getAsLong() : 3_000L;

        switch (kind) {
            case "INTRO_QUOTE" -> {
                showQuote("吉尔伽美什：「竟敢直视本王……杂修，就在星盘之下化作尘埃吧。」", now + duration);
                playLocalSound(SoundEvents.BEACON_ACTIVATE, 0.85f, 0.85f);
            }
            case "VIMANA_TRANSITION" -> {
                Vec3 center = new Vec3(pkt.get("cx").getAsDouble(), pkt.get("cy").getAsDouble(), pkt.get("cz").getAsDouble());
                double startAngle = pkt.get("startAngle").getAsDouble();
                vimanaVisual = new VimanaVisual(center, startAngle, serverNow, serverNow + duration);
                bannerTitle = "维摩那 · 天球巡航";
                bannerUntil = now + duration;
                bannerColor = 0xffffd86b;
                showQuote("吉尔伽美什：「有点意思。就让你们见识一下天球之上的风景吧！」", now + 4_500L);
                playLocalSound(SoundEvents.BEACON_POWER_SELECT, 1.0f, 1.15f);
            }
            case "VIMANA_POS" -> {
                if (vimanaVisual != null) {
                    Vec3 next = new Vec3(pkt.get("x").getAsDouble(), pkt.get("y").getAsDouble(), pkt.get("z").getAsDouble());
                    vimanaVisual.interpFrom = vimanaVisual.currentPos(serverNow);
                    vimanaVisual.pos = next;
                    vimanaVisual.interpAt = serverNow;
                }
            }
            case "PHASE2_ENTERED" -> {
                vimanaVisual = null;
                bannerTitle = "英雄王 · 王之财宝全开";
                bannerUntil = now + duration;
                bannerColor = 0xffffe07a;
                playLocalSound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.9f, 0.95f);
            }
            case "WEDGE_DISARMED" -> {
                String hid = pkt.get("hazardId").getAsString();
                areas.remove(hid);
                playLocalSound(SoundEvents.ANVIL_DESTROY, 0.55f, 1.45f);
            }
            case "ENKIDU_QUOTE" -> {
                showQuote("吉尔伽美什：「挣扎吧，杂修——天之锁！」", now + duration);
                playLocalSound(SoundEvents.CHAIN_PLACE, 1.0f, 0.8f);
            }
            case "ENKIDU_BOUND" -> {
                String bindId = pkt.get("bindId").getAsString();
                UUID playerId = UUID.fromString(pkt.get("playerId").getAsString());
                int anchorEntityId = pkt.get("anchorEntityId").getAsInt();
                Vec3 pPos = new Vec3(pkt.get("x").getAsDouble(), pkt.get("y").getAsDouble(), pkt.get("z").getAsDouble());
                Vec3 aPos = new Vec3(pkt.get("ax").getAsDouble(), pkt.get("ay").getAsDouble(), pkt.get("az").getAsDouble());
                long startAt = pkt.get("startAt").getAsLong();
                long expireAt = pkt.get("expireAt").getAsLong();
                enkiduBinds.put(bindId, new EnkiduBindVisual(bindId, playerId, anchorEntityId, pPos, aPos, startAt, expireAt));
                var player = Minecraft.getInstance().player;
                if (player != null && player.getUUID().equals(playerId)) {
                    bannerTitle = "天之锁束缚 · 击破身旁【天之锁】挣脱！";
                    bannerUntil = now + Math.max(500L, expireAt - serverNow);
                    bannerColor = 0xffffc842;
                    playLocalSound(SoundEvents.CHAIN_BREAK, 1.0f, 0.72f);
                    ClientUi.current().message("你被【天之锁】束缚！快用技能击破身旁的【天之锁】，否则将被直接处决！");
                }
            }
            case "ENKIDU_RELEASED" -> {
                String bindId = pkt.get("bindId").getAsString();
                boolean broken = pkt.has("broken") && pkt.get("broken").getAsBoolean();
                EnkiduBindVisual removed = enkiduBinds.remove(bindId);
                var player = Minecraft.getInstance().player;
                if (removed != null && player != null && player.getUUID().equals(removed.playerId)) {
                    if (broken) {
                        bannerTitle = "天之锁已击破！";
                        bannerUntil = now + 1_400L;
                        bannerColor = 0xff8dffb2;
                        playLocalSound(SoundEvents.GLASS_BREAK, 0.9f, 1.15f);
                    }
                }
            }
            case "ENKIDU_EXECUTE" -> {
                String bindId = pkt.get("bindId").getAsString();
                enkiduBinds.remove(bindId);
                UUID playerId = UUID.fromString(pkt.get("playerId").getAsString());
                Vec3 pos = new Vec3(pkt.get("x").getAsDouble(), pkt.get("y").getAsDouble(), pkt.get("z").getAsDouble());
                executeBeams.add(new ExecuteBeamVisual(pos, serverNow, serverNow + 900L));
                playAt(pos, SoundEvents.TRIDENT_THUNDER.value(), 1.0f, 1.0f);
                var player = Minecraft.getInstance().player;
                if (player != null && player.getUUID().equals(playerId) && !callbacks.own().dead) {
                    double ratio = pkt.has("ratio") ? pkt.get("ratio").getAsDouble() : OrreryTowerGilgamesh.ENKIDU_EXECUTE_RATIO;
                    bannerTitle = "天之锁处决！";
                    bannerUntil = now + 1_600L;
                    bannerColor = 0xffff4545;
                    callbacks.ratioDamage(ratio, pkt.getAsJsonObject("boss"));
                }
            }
            case "EA_DRAW" -> {
                eaSkyActive = true;
                eaSkyCenter = new Vec3(pkt.get("cx").getAsDouble(), pkt.get("cy").getAsDouble(), pkt.get("cz").getAsDouble());
                bannerTitle = "乖离剑 · Ea 显现";
                bannerUntil = now + duration;
                bannerColor = 0xffff5c52;
                showQuote("吉尔伽美什：「竟然逼本王动用真格……作为褒奖，就赐予你们终结吧！」", now + 4_600L);
                playLocalSound(SoundEvents.WITHER_SPAWN, 0.85f, 0.85f);
            }
            case "EA_SKY" -> {
                eaSkyActive = pkt.get("active").getAsBoolean();
            }
            case "ENUMA_DECREE_START" -> {
                String id = pkt.get("id").getAsString();
                Vec3 center = new Vec3(pkt.get("cx").getAsDouble(), pkt.get("cy").getAsDouble(), pkt.get("cz").getAsDouble());
                long startAt = pkt.get("startAt").getAsLong();
                long revealAt = pkt.get("revealAt").getAsLong();
                long impactAt = pkt.get("impactAt").getAsLong();
                long endAt = pkt.get("endAt").getAsLong();
                double shelterRadius = pkt.get("shelterRadius").getAsDouble();
                List<Vec3> shelters = decodePositions(pkt.getAsJsonArray("shelters"));
                decreeVisual = new DecreeVisual(id, center, startAt, revealAt, impactAt, endAt, shelterRadius, shelters);
                bannerTitle = "天地乖离开辟之星 · 速躲入蓝色星柱护幕！";
                bannerUntil = now + Math.max(1_000L, impactAt - serverNow);
                bannerColor = 0xffff4e4e;
                showQuote("吉尔伽美什：「天地乖离开辟之星——Enuma Elish！」", now + 5_500L);
                playLocalSound(SoundEvents.WARDEN_SONIC_CHARGE, 1.0f, 0.72f);
            }
            case "ENUMA_DECREE_IMPACT" -> {
                Vec3 center = new Vec3(pkt.get("cx").getAsDouble(), pkt.get("cy").getAsDouble(), pkt.get("cz").getAsDouble());
                double shelterRadius = pkt.get("shelterRadius").getAsDouble();
                double outsideRatio = pkt.get("outsideRatio").getAsDouble();
                List<Vec3> shelters = decodePositions(pkt.getAsJsonArray("shelters"));
                playLocalSound(SoundEvents.WARDEN_SONIC_BOOM, 1.0f, 0.78f);
                playLocalSound(SoundEvents.GENERIC_EXPLODE.value(), 1.0f, 0.65f);
                var player = Minecraft.getInstance().player;
                if (player != null && !callbacks.own().dead && !TowerClient.blocksCombat()) {
                    boolean safe = OrreryTowerGilgamesh.insideAnyShelter(player.position(), shelters, shelterRadius + 0.35);
                    if (safe) {
                        bannerTitle = "星柱护幕成功抵御乖离风暴！";
                        bannerUntil = now + 1_800L;
                        bannerColor = 0xff7ce8ff;
                        playLocalSound(SoundEvents.BEACON_ACTIVATE, 0.85f, 1.35f);
                    } else {
                        bannerTitle = "未能进入星柱护幕！";
                        bannerUntil = now + 2_000L;
                        bannerColor = 0xffff3838;
                        Vec3 push = horizontalDir(center, player.position()).scale(1.65).add(0, 0.55, 0);
                        player.setDeltaMovement(push);
                        callbacks.ratioDamage(outsideRatio, pkt.getAsJsonObject("boss"));
                    }
                }
            }
            case "FINALE_DPS_CHECK" -> {
                Vec3 center = new Vec3(pkt.get("cx").getAsDouble(), pkt.get("cy").getAsDouble(), pkt.get("cz").getAsDouble());
                finaleVisual = new FinaleVisual(center, serverNow, serverNow + duration);
                bannerTitle = "乖离终焉 · 倒计时内击破护盾斩杀吉尔伽美什！";
                bannerUntil = now + duration;
                bannerColor = 0xffff3848;
                showQuote("吉尔伽美什：「不可能……本王岂会败给区区杂修！与这片星穹一同归于虚无吧！」", now + 5_000L);
                playLocalSound(SoundEvents.ENDER_DRAGON_GROWL, 0.95f, 0.85f);
            }
            case "FINALE_WIPE" -> {
                playLocalSound(SoundEvents.GENERIC_EXPLODE.value(), 1.0f, 0.55f);
                callbacks.instantDefeat(pkt.getAsJsonObject("boss"));
            }
            case "DEFEAT_QUOTE" -> {
                showQuote("吉尔伽美什：「哼……这份傲慢，本王记下了。」", now + duration);
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
        double auxRadius = pkt.get("auxRadius").getAsDouble();
        double speed = pkt.get("speed").getAsDouble();
        long startAt = pkt.get("startAt").getAsLong();
        long warnUntil = pkt.get("warnUntil").getAsLong();
        long endAt = pkt.get("endAt").getAsLong();
        long intervalMs = pkt.get("intervalMs").getAsLong();
        double coefficient = pkt.get("coefficient").getAsDouble();
        int element = pkt.get("element").getAsInt();
        boolean magic = pkt.get("magic").getAsBoolean();
        int count = pkt.get("count").getAsInt();
        long lifeMs = pkt.get("lifeMs").getAsLong();
        double baseAngle = pkt.get("baseAngle").getAsDouble();
        int color = pkt.get("color").getAsInt();

        switch (type) {
            case "BABYLON_VOLLEY" -> {
                Vec3 aimDir = horizontalDir(from, to);
                int seed = id.hashCode();
                for (int i = 0; i < count; i++) {
                    long launchAt = warnUntil + i * Math.max(25L, intervalMs);
                    Vec3 slotPos = computeBackGatePos(bossEntityId, from, aimDir, i, count);
                    String pid = id + "/v" + i;
                    projectiles.put(pid, new GateProjectile(
                        pid, id, instance, boss, bossEntityId,
                        true, i, count, OrreryTowerGilgamesh.SKILL1_MAX_HITS_PER_CAST,
                        slotPos, aimDir,
                        startAt, launchAt, launchAt + lifeMs,
                        speed, radius, coefficient, element, magic,
                        Math.floorMod(seed + i * 7, 12), color
                    ));
                }
                playAt(from, SoundEvents.AMETHYST_BLOCK_CHIME, 0.75f, 1.25f);
            }
            case "BABYLON_SURROUND" -> {
                double[] angles = OrreryTowerGilgamesh.surroundGateAnglesRad(count, baseAngle);
                int seed = id.hashCode();
                for (int i = 0; i < angles.length; i++) {
                    double ang = angles[i];
                    Vec3 gatePos = from.add(Math.cos(ang) * radius, 1.45, Math.sin(ang) * radius);
                    Vec3 inward = new Vec3(-Math.cos(ang), -0.04, -Math.sin(ang)).normalize();
                    String pid = id + "/r" + i;
                    projectiles.put(pid, new GateProjectile(
                        pid, id, instance, boss, bossEntityId,
                        false, i, angles.length, OrreryTowerGilgamesh.SKILL4_MAX_HITS_PER_CAST,
                        gatePos, inward,
                        startAt, warnUntil, warnUntil + lifeMs,
                        speed, auxRadius, coefficient, element, magic,
                        Math.floorMod(seed + i * 5, 12), color
                    ));
                }
                playAt(from, SoundEvents.BEACON_ACTIVATE, 0.85f, 1.35f);
            }
            default -> areas.put(id, new AreaHazard(
                id, instance, type, boss, bossEntityId,
                from, to, radius, auxRadius,
                startAt, warnUntil, endAt, intervalMs, lifeMs,
                coefficient, element, magic, color
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
        if (vimanaVisual != null && serverNow >= vimanaVisual.endAt + 400L) vimanaVisual = null;
        if (decreeVisual != null && serverNow >= decreeVisual.endAt + 400L) decreeVisual = null;
        if (finaleVisual != null && serverNow >= finaleVisual.endAt + 600L) finaleVisual = null;
        executeBeams.removeIf(b -> serverNow >= b.endAt + 200L);
        enkiduBinds.values().removeIf(b -> serverNow >= b.expireAt + 600L);
        projectiles.values().removeIf(p -> p.consumed || serverNow >= p.endAt + 200L);
        areas.values().removeIf(a -> serverNow >= a.endAt + 500L);

        // 天之锁纯控制：把被锁玩家限制在锚点附近（防位移逃课，但仍允许释放全部技能击破天之锁！）
        for (EnkiduBindVisual b : enkiduBinds.values()) {
            if (b.playerId.equals(player.getUUID()) && serverNow < b.expireAt && !callbacks.own().dead) {
                Vec3 delta = player.position().subtract(b.playerAnchor);
                if (delta.horizontalDistanceSqr() > 1.6 * 1.6) {
                    Vec3 clamped = b.playerAnchor.add(delta.multiply(1, 0, 1).normalize().scale(1.2));
                    player.setPos(clamped.x, player.getY(), clamped.z);
                }
                player.setDeltaMovement(0, Math.min(0, player.getDeltaMovement().y), 0);
                break;
            }
        }

        boolean canHit = !callbacks.own().dead && !player.isDeadOrDying() && !TowerClient.blocksCombat();
        Vec3 playerAim = player.position().add(0, player.getBbHeight() * 0.52, 0);

        // 1. 推进王之财宝 3D 武器投射弹幕
        for (GateProjectile p : new ArrayList<>(projectiles.values())) {
            if (p.consumed || !p.instance.equals(callbacks.own().instance)) continue;

            if (serverNow < p.launchAt) {
                if (p.backAttached) {
                    Vec3 bossPos = resolveBossPos(p.bossEntityId, p.gatePos);
                    Vec3 bossForward = resolveBossForward(p.bossEntityId, p.dir);
                    p.gatePos = computeBackGatePos(p.bossEntityId, bossPos, bossForward, p.slotIndex, p.slotTotal);
                    p.pos = p.gatePos;
                    p.prevPos = p.gatePos;
                    Vec3 aim = playerAim.subtract(p.gatePos);
                    if (aim.lengthSqr() > 1e-4) {
                        Vec3 baseDir = aim.normalize();
                        // 左右微扇形分散，避免全部重叠成一点
                        double spreadDeg = (p.slotIndex % 3 - 1) * 5.5;
                        p.dir = rotateHorizontal(baseDir, spreadDeg);
                    }
                }
                p.lastSimAt = p.launchAt;
                continue;
            }

            if (!p.launched) {
                p.launched = true;
                p.lastSimAt = p.launchAt;
                if (p.backAttached) {
                    Vec3 bossPos = resolveBossPos(p.bossEntityId, p.gatePos);
                    Vec3 bossForward = resolveBossForward(p.bossEntityId, p.dir);
                    p.gatePos = computeBackGatePos(p.bossEntityId, bossPos, bossForward, p.slotIndex, p.slotTotal);
                    p.pos = p.gatePos;
                    Vec3 aim = playerAim.subtract(p.gatePos);
                    if (aim.lengthSqr() > 1e-4) {
                        double spreadDeg = (p.slotIndex % 3 - 1) * 5.5;
                        p.dir = rotateHorizontal(aim.normalize(), spreadDeg);
                    }
                }
                p.prevPos = p.pos;
                if (p.slotIndex % 3 == 0) {
                    playAt(p.pos, SoundEvents.TRIDENT_THROW.value(), 0.45f, 1.25f + (p.slotIndex % 4) * 0.08f);
                }
            }

            long dtMs = Math.max(0L, Math.min(120L, serverNow - p.lastSimAt));
            p.lastSimAt = serverNow;
            if (dtMs <= 0L) continue;
            double dtSec = dtMs / 1000.0;

            p.prevPos = p.pos;
            p.pos = p.pos.add(p.dir.scale(p.speed * dtSec));

            if (p.pos.y < 76.0 || p.pos.y > 114.0) {
                p.consumed = true;
                continue;
            }

            if (canHit && p.coefficient > 0) {
                AABB box = player.getBoundingBox().inflate(p.hitRadius * 0.55, 0.35, p.hitRadius * 0.55);
                if (box.contains(p.prevPos) || box.contains(p.pos) || box.clip(p.prevPos, p.pos).isPresent()) {
                    p.consumed = true;
                    int already = castHitCounts.getOrDefault(p.castKey, 0);
                    if (already < p.maxHitsPerCast) {
                        castHitCounts.put(p.castKey, already + 1);
                        playAt(p.pos, SoundEvents.ANVIL_LAND, 0.35f, 1.55f);
                        emitHit(p.boss, p.id, "BABYLON_WEAPON", p.coefficient, p.element, p.magic, serverNow);
                    }
                }
            }
        }

        // 2. 推进全部区域类伤害
        for (AreaHazard a : new ArrayList<>(areas.values())) {
            if (!a.instance.equals(callbacks.own().instance)) continue;

            switch (a.type) {
                case "WEDGE_DROP", "WEDGE_EXPLODE", "SKY_RAIN", "VIMANA_ORB", "EA_DRAW_WAVE", "FINALE_RIFT" -> {
                    if (serverNow < a.warnUntil || serverNow > a.endAt) continue;
                    Vec3 hitCenter = "VIMANA_ORB".equals(a.type) ? a.to : a.from;
                    if (!a.soundPlayed) {
                        a.soundPlayed = true;
                        playAt(hitCenter, SoundEvents.GENERIC_EXPLODE.value(),
                            "WEDGE_EXPLODE".equals(a.type) ? 0.95f : 0.65f,
                            "WEDGE_DROP".equals(a.type) ? 1.25f : 0.9f);
                    }
                    if (canHit && !a.struck && a.coefficient > 0) {
                        Vec3 offset = player.position().subtract(hitCenter);
                        if (Math.abs(offset.y) <= 4.5 && offset.horizontalDistanceSqr() <= Math.pow(a.radius + 0.35, 2)) {
                            a.struck = true;
                            emitHit(a.boss, a.id, a.type, a.coefficient, a.element, a.magic, serverNow);
                            if ("EA_DRAW_WAVE".equals(a.type)) {
                                Vec3 push = horizontalDir(hitCenter, player.position()).scale(1.25).add(0, 0.35, 0);
                                player.setDeltaMovement(push);
                            }
                        }
                    }
                }
                case "ROYAL_REPULSE" -> {
                    if (serverNow < a.warnUntil || serverNow > a.endAt) continue;
                    if (!a.soundPlayed) {
                        a.soundPlayed = true;
                        playAt(a.from, SoundEvents.TRIDENT_RIPTIDE_3.value(), 0.95f, 1.15f);
                    }
                    if (canHit && !a.struck && a.coefficient > 0) {
                        Vec3 offset = player.position().subtract(a.from);
                        if (Math.abs(offset.y) <= 3.8 && offset.horizontalDistanceSqr() <= Math.pow(a.radius + 0.35, 2)) {
                            a.struck = true;
                            Vec3 push = horizontalDir(a.from, player.position()).scale(1.45).add(0, 0.36, 0);
                            player.setDeltaMovement(push);
                            emitHit(a.boss, a.id, "ROYAL_REPULSE", a.coefficient, a.element, a.magic, serverNow);
                        }
                    }
                }
                case "VIMANA_RING" -> {
                    if (serverNow < a.warnUntil || serverNow > a.endAt) continue;
                    if (!a.soundPlayed) {
                        a.soundPlayed = true;
                        playAt(a.from, SoundEvents.BEACON_DEACTIVATE, 0.95f, 1.1f);
                    }
                    if (canHit && !a.struck && a.coefficient > 0) {
                        Vec3 offset = player.position().subtract(a.from);
                        double dist = offset.horizontalDistance();
                        if (Math.abs(offset.y) <= 5.0 && dist >= Math.max(0.0, a.auxRadius - 0.25) && dist <= a.radius + 0.35) {
                            a.struck = true;
                            emitHit(a.boss, a.id, "VIMANA_RING", a.coefficient, a.element, a.magic, serverNow);
                        }
                    }
                }
                case "EA_SPACE_REND" -> {
                    long slashEndAt = a.warnUntil + Math.max(150L, a.lifeMs);
                    long collapseAt = slashEndAt + Math.max(200L, a.intervalMs);
                    // 第一段：赤红裂空风压扫过
                    if (serverNow >= a.warnUntil && serverNow <= slashEndAt) {
                        if (!a.soundPlayed) {
                            a.soundPlayed = true;
                            playAt(a.from, SoundEvents.PLAYER_ATTACK_SWEEP, 0.95f, 0.72f);
                        }
                        if (canHit && !a.struck && a.coefficient > 0 && insideCorridor(player.position(), a.from, a.to, a.radius)) {
                            a.struck = true;
                            emitHit(a.boss, a.id + "/slash", "EA_SPACE_REND", a.coefficient, a.element, a.magic, serverNow);
                        }
                    }
                    // 第二段：延迟空间塌陷爆裂
                    if (serverNow >= collapseAt && serverNow <= a.endAt) {
                        if (!a.secondarySoundPlayed) {
                            a.secondarySoundPlayed = true;
                            playAt(a.from.lerp(a.to, 0.5), SoundEvents.GENERIC_EXPLODE.value(), 0.75f, 0.75f);
                        }
                        if (canHit && !a.secondaryStruck && a.auxRadius > 0 && insideCorridor(player.position(), a.from, a.to, a.radius)) {
                            a.secondaryStruck = true;
                            emitHit(a.boss, a.id + "/collapse", "EA_SPACE_COLLAPSE", a.auxRadius, a.element, a.magic, serverNow);
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
        if (lenSq < 1e-6) return false;
        double px = point.x - start.x, pz = point.z - start.z;
        double t = Math.clamp((px * dx + pz * dz) / lenSq, 0.0, 1.0);
        double cx = px - t * dx, cz = pz - t * dz;
        double half = width * 0.5 + 0.32;
        return cx * cx + cz * cz <= half * half;
    }

    private static void emitHit(Target boss, String hitId, String kind, double coeff,
                                int element, boolean magic, long serverNow) {
        JsonObject action = new JsonObject();
        action.addProperty("id", "gilgamesh/" + hitId);
        action.addProperty("clientContact", true);
        action.add("boss", JSON.toJsonTree(boss));
        action.addProperty("kind", kind);
        action.addProperty("coefficient", coeff);
        action.addProperty("magic", magic);
        action.addProperty("element", element);
        action.addProperty("occurredAt", serverNow);
        callbacks.bossContact(action);
    }

    private static void showQuote(String text, long untilLocalMs) {
        quoteText = text;
        quoteUntil = untilLocalMs;
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

    private static Vec3 computeBackGatePos(int bossEntityId, Vec3 fallbackPos, Vec3 fallbackForward, int index, int total) {
        Vec3 base = resolveBossPos(bossEntityId, fallbackPos);
        Vec3 forward = resolveBossForward(bossEntityId, fallbackForward);
        Vec3 right = new Vec3(-forward.z, 0, forward.x);
        double[] off = OrreryTowerGilgamesh.backGateSlotOffset(index, total);
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
        var font = UiFonts.font();
        int nextY = 56;
        if (!bannerTitle.isEmpty() && now < bannerUntil) {
            double remaining = Math.max(0.0, (bannerUntil - now) / 1000.0);
            String label = String.format(Locale.ROOT, "%s  %.1fs", bannerTitle, remaining);
            int w = font.width(label) + 18;
            int x = (g.guiWidth() - w) / 2;
            UiTheme.hud(g, x, nextY, w, 18);
            g.text(font, label, x + 9, nextY + 5, bannerColor, true);
            nextY += 22;
        }
        if (!quoteText.isEmpty() && now < quoteUntil) {
            int w = font.width(quoteText) + 18;
            int x = (g.guiWidth() - w) / 2;
            UiTheme.hud(g, x, nextY, w, 16);
            g.text(font, quoteText, x + 9, nextY + 4, eaSkyActive ? 0xffffc07a : 0xfff6e29c, true);
        }
    }
}
