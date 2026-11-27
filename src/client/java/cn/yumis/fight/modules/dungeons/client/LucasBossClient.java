package cn.yumis.fight.modules.dungeons.client;

import cn.yumis.fight.modules.combat.api.Life;
import cn.yumis.fight.modules.dungeons.api.Target;
import cn.yumis.fight.modules.dungeons.content.DragonTowerLucas;
import cn.yumis.fight.modules.presentation.api.client.ClientUi;
import cn.yumis.fight.modules.presentation.api.client.UiFonts;
import cn.yumis.fight.modules.presentation.api.client.UiTheme;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.*;

/**
 * 天空城 · 龙人之塔首领「鲁卡斯」客户端本机权威控制器：
 * - 接收服务端下发的 lucasBossEvent 与 lucasBossHazard；
 * - 维护本机受击几何判定、轻击退、可破雷霆枪牢受击反馈与 HUD 阶段/大招横幅；
 * - 按幂等命中 ID 汇报战斗回执，离场/换世界/死亡时自动清空残留。
 */
public final class LucasBossClient {
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
        final Vec3 from;
        final Vec3 to;
        final double radius;
        final double width;
        final double auxRadius;
        final double slashAngle;
        final long startAt;
        final long warnUntil;
        final long endAt;
        final double coefficient;
        final double knockback;
        final boolean magic;
        final int element;
        final int color;
        long previousAt;
        int cageHits;
        boolean struck;
        boolean soundPlayed;

        Hazard(String id, String instance, String type, Target boss, Vec3 from, Vec3 to,
               double radius, double width, double auxRadius, double slashAngle,
               long startAt, long warnUntil, long endAt,
               double coefficient, double knockback, boolean magic, int element, int color) {
            this.id = id;
            this.instance = instance;
            this.type = type;
            this.boss = boss;
            this.from = from;
            this.to = to;
            this.radius = radius;
            this.width = width;
            this.auxRadius = auxRadius;
            this.slashAngle = slashAngle;
            this.startAt = startAt;
            this.warnUntil = warnUntil;
            this.endAt = endAt;
            this.coefficient = coefficient;
            this.knockback = knockback;
            this.magic = magic;
            this.element = element;
            this.color = color;
            this.previousAt = warnUntil;
        }
    }

    static final class Burst {
        final String kind;
        final Vec3 pos;
        final long startAt;
        final long endAt;
        final int color;

        Burst(String kind, Vec3 pos, long startAt, long endAt, int color) {
            this.kind = kind;
            this.pos = pos;
            this.startAt = startAt;
            this.endAt = endAt;
            this.color = color;
        }
    }

    private static Callbacks callbacks;
    private static Object world;
    private static String activeInstance = "";
    private static String bannerTitle = "";
    private static long bannerUntil = 0L;
    private static int bannerColor = 0xff6fd8ff;
    static final Map<String, Hazard> hazards = new LinkedHashMap<>();
    static final List<Burst> bursts = new ArrayList<>();

    private LucasBossClient() {}

    public static void register(Callbacks cb) {
        callbacks = Objects.requireNonNull(cb);
        NeoForge.EVENT_BUS.addListener(LucasBossClient::tick);
    }

    public static void clear() {
        hazards.clear();
        bursts.clear();
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
            bursts.clear();
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
        if ("CLEAR_ENCOUNTER_EFFECTS".equals(kind)) {
            hazards.clear();
            bursts.clear();
            bannerTitle = "";
            bannerUntil = 0L;
            return;
        }
        String instance = pkt.has("instance") ? pkt.get("instance").getAsString() : "";
        if (instance.isEmpty() || !instance.equals(callbacks.own().instance)) return;
        activeInstance = instance;

        long now = ClientUi.current().serverNow();
        switch (kind) {
            case "BANNER" -> {
                long duration = pkt.has("durationMs") ? pkt.get("durationMs").getAsLong() : 3_000L;
                bannerTitle = pkt.has("title") ? pkt.get("title").getAsString() : "";
                bannerUntil = ClientUi.now() + duration;
                int rawColor = pkt.has("color") ? pkt.get("color").getAsInt() : DragonTowerLucas.COLOR_SKY_BLUE;
                bannerColor = 0xff000000 | (rawColor & 0xffffff);
                playLocalSound(SoundEvents.TRIDENT_THUNDER.value(), 0.5f, 1.18f);
            }
            case "CANCEL_HAZARD" -> {
                if (pkt.has("id")) hazards.remove(pkt.get("id").getAsString());
            }
            case "CAGE_HIT" -> {
                String id = pkt.has("id") ? pkt.get("id").getAsString() : "";
                Hazard h = hazards.get(id);
                if (h != null && pkt.has("hits")) h.cageHits = pkt.get("hits").getAsInt();
                Vec3 pos = new Vec3(pkt.get("x").getAsDouble(), pkt.get("y").getAsDouble(), pkt.get("z").getAsDouble());
                bursts.add(new Burst("CAGE_HIT", pos, now, now + 320L, DragonTowerLucas.COLOR_SKY_BLUE));
                playAt(pos, SoundEvents.AMETHYST_BLOCK_CHIME, 0.9f, 1.35f);
            }
            case "CAGE_BROKEN" -> {
                String id = pkt.has("id") ? pkt.get("id").getAsString() : "";
                hazards.remove(id);
                Vec3 pos = new Vec3(pkt.get("x").getAsDouble(), pkt.get("y").getAsDouble(), pkt.get("z").getAsDouble());
                bursts.add(new Burst("CAGE_BROKEN", pos, now, now + 520L, DragonTowerLucas.COLOR_SAFE_GREEN));
                playAt(pos, SoundEvents.GLASS_BREAK, 0.95f, 1.15f);
            }
            case "CLONE_SUMMON" -> {
                Vec3 pos = new Vec3(pkt.get("x").getAsDouble(), pkt.get("y").getAsDouble(), pkt.get("z").getAsDouble());
                bursts.add(new Burst("CLONE_SUMMON", pos, now, now + 700L, DragonTowerLucas.COLOR_CLONE_CYAN));
                playAt(pos, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.75f, 1.3f);
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
        Target boss = JSON.fromJson(pkt.get("boss"), Target.class);
        Vec3 from = new Vec3(pkt.get("x").getAsDouble(), pkt.get("y").getAsDouble(), pkt.get("z").getAsDouble());
        Vec3 to = new Vec3(pkt.get("tx").getAsDouble(), pkt.get("ty").getAsDouble(), pkt.get("tz").getAsDouble());
        Hazard h = new Hazard(
            id, instance, pkt.get("type").getAsString(), boss, from, to,
            pkt.get("radius").getAsDouble(),
            pkt.get("width").getAsDouble(),
            pkt.has("auxRadius") ? pkt.get("auxRadius").getAsDouble() : 0.0,
            pkt.has("slashAngle") ? pkt.get("slashAngle").getAsDouble() : 0.0,
            pkt.get("startAt").getAsLong(),
            pkt.get("warnUntil").getAsLong(),
            pkt.get("endAt").getAsLong(),
            pkt.get("coefficient").getAsDouble(),
            pkt.has("knockback") ? pkt.get("knockback").getAsDouble() : 0.0,
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
        hazards.values().removeIf(h -> serverNow >= h.endAt + 480L);
        bursts.removeIf(b -> serverNow >= b.endAt);

        boolean canHit = !callbacks.own().dead && !player.isDeadOrDying() && !TowerClient.blocksCombat();

        for (Hazard h : new ArrayList<>(hazards.values())) {
            if (!h.instance.equals(callbacks.own().instance)) continue;
            if (serverNow < h.warnUntil || serverNow > h.endAt) continue;

            switch (h.type) {
                case "FAN_SLASH" -> {
                    if (!h.soundPlayed) {
                        h.soundPlayed = true;
                        playAt(h.from, SoundEvents.PLAYER_ATTACK_SWEEP, 0.85f, 0.95f);
                    }
                    if (canHit && !h.struck && h.coefficient > 0
                        && hitsFanSlash(player.position(), h.from, h.to, h.radius, h.slashAngle)) {
                        h.struck = true;
                        emitHit(h, h.id, h.coefficient, serverNow);
                        applyKnockback(player, h.from, h.knockback);
                    }
                }
                case "INSTANT_LINE" -> {
                    if (!h.soundPlayed) {
                        h.soundPlayed = true;
                        playAt(h.from.lerp(h.to, 0.5), SoundEvents.LIGHTNING_BOLT_IMPACT, 0.65f, 1.35f);
                    }
                    if (canHit && !h.struck && h.coefficient > 0
                        && hitsInstantLine(player.position(), h.from, h.to, h.width)) {
                        h.struck = true;
                        emitHit(h, h.id, h.coefficient, serverNow);
                        applyKnockback(player, h.from, h.knockback);
                    }
                }
                case "DASH_LINE" -> {
                    if (!h.soundPlayed) {
                        h.soundPlayed = true;
                        playAt(h.from, SoundEvents.TRIDENT_RIPTIDE_3.value(), 0.78f, 1.15f);
                    }
                    double prevProg = Math.clamp((h.previousAt - h.warnUntil) / (double) Math.max(1L, h.endAt - h.warnUntil), 0.0, 1.0);
                    double currProg = Math.clamp((serverNow - h.warnUntil) / (double) Math.max(1L, h.endAt - h.warnUntil), 0.0, 1.0);
                    h.previousAt = serverNow;
                    if (canHit && !h.struck && h.coefficient > 0
                        && hitsTravelingLine(player.position(), h.from, h.to, h.width, prevProg, currProg)) {
                        h.struck = true;
                        emitHit(h, h.id, h.coefficient, serverNow);
                        applyKnockback(player, h.from, h.knockback);
                    }
                }
                case "RING_WAVE" -> {
                    if (!h.soundPlayed) {
                        h.soundPlayed = true;
                        playAt(h.from, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.7f, 1.05f);
                    }
                    double prevProg = Math.clamp((h.previousAt - h.warnUntil) / (double) Math.max(1L, h.endAt - h.warnUntil), 0.0, 1.0);
                    double currProg = Math.clamp((serverNow - h.warnUntil) / (double) Math.max(1L, h.endAt - h.warnUntil), 0.0, 1.0);
                    h.previousAt = serverNow;
                    if (canHit && !h.struck && h.coefficient > 0
                        && hitsRingWave(player.position(), h.from, h.radius, h.auxRadius, h.width, prevProg, currProg)) {
                        h.struck = true;
                        emitHit(h, h.id, h.coefficient, serverNow);
                        applyKnockback(player, h.from, h.knockback);
                    }
                }
                case "SKY_SPEAR", "CAGE_DETONATE" -> {
                    if (!h.soundPlayed) {
                        h.soundPlayed = true;
                        playAt(h.from, SoundEvents.LIGHTNING_BOLT_THUNDER, "CAGE_DETONATE".equals(h.type) ? 0.9f : 0.55f, 1.25f);
                    }
                    if (canHit && !h.struck && h.coefficient > 0
                        && hitsCircle(player.position(), h.from, h.radius)) {
                        h.struck = true;
                        emitHit(h, h.id, h.coefficient, serverNow);
                        applyKnockback(player, h.from, h.knockback);
                    }
                }
                case "JUMP_SLAM_IMPACT" -> {
                    if (!h.soundPlayed) {
                        h.soundPlayed = true;
                        playAt(h.to, SoundEvents.GENERIC_EXPLODE.value(), 0.85f, 0.8f);
                        playAt(h.to, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.8f, 0.95f);
                    }
                    if (canHit && !h.struck && h.coefficient > 0
                        && hitsCircle(player.position(), h.to, h.radius)) {
                        h.struck = true;
                        emitHit(h, h.id, h.coefficient, serverNow);
                        applyKnockback(player, h.to, h.knockback);
                    }
                }
                case "SAFE_BAND_BURST" -> {
                    if (!h.soundPlayed) {
                        h.soundPlayed = true;
                        playAt(h.from, SoundEvents.LIGHTNING_BOLT_THUNDER, 0.95f, 0.88f);
                    }
                    if (canHit && !h.struck && h.coefficient > 0
                        && hitsSafeBandBurst(player.position(), h.from, h.radius, h.width, h.auxRadius)) {
                        h.struck = true;
                        emitHit(h, h.id, h.coefficient, serverNow);
                        applyKnockback(player, h.from, h.knockback);
                    }
                }
                case "CAGE_ZONE" -> {
                    if (!h.soundPlayed) {
                        h.soundPlayed = true;
                        playAt(h.from, SoundEvents.ANVIL_LAND, 0.55f, 1.45f);
                    }
                }
                default -> {}
            }
        }
    }

    // ==================== 纯几何判定（供无头验证复用） ====================

    public static boolean hitsCircle(Vec3 playerPos, Vec3 center, double radius) {
        Vec3 off = playerPos.subtract(center);
        return Math.abs(off.y) <= 3.0 && off.horizontalDistanceSqr() <= Math.pow(radius + 0.35, 2);
    }

    public static boolean hitsRingWave(Vec3 playerPos, Vec3 center, double startR, double endR,
                                       double ringWidth, double prevProg, double currProg) {
        Vec3 off = playerPos.subtract(center);
        if (Math.abs(off.y) > 2.2) return false;
        double dist = Math.hypot(off.x, off.z);
        double r0 = startR + (endR - startR) * Math.clamp(prevProg, 0.0, 1.0);
        double r1 = startR + (endR - startR) * Math.clamp(currProg, 0.0, 1.0);
        double half = ringWidth * 0.5 + 0.3;
        return dist >= Math.max(0.0, Math.min(r0, r1) - half) && dist <= Math.max(r0, r1) + half;
    }

    public static boolean hitsSafeBandBurst(Vec3 playerPos, Vec3 center, double safeMin, double safeMax, double outerMax) {
        Vec3 off = playerPos.subtract(center);
        if (Math.abs(off.y) > 3.2) return false;
        double dist = Math.hypot(off.x, off.z);
        if (dist > outerMax + 0.35) return false;
        return dist < safeMin || dist > safeMax;
    }

    public static boolean hitsInstantLine(Vec3 playerPos, Vec3 from, Vec3 to, double width) {
        Vec3 span = new Vec3(to.x - from.x, 0, to.z - from.z);
        double len = span.length();
        if (len < 1e-4) return false;
        Vec3 dir = span.scale(1.0 / len);
        Vec3 off = playerPos.subtract(from);
        if (Math.abs(off.y) > 3.0) return false;
        double along = off.x * dir.x + off.z * dir.z;
        if (along < -0.45 || along > len + 0.45) return false;
        double lateral = Math.abs(off.x * dir.z - off.z * dir.x);
        return lateral <= width * 0.5 + 0.32;
    }

    public static boolean hitsTravelingLine(Vec3 playerPos, Vec3 from, Vec3 to, double width, double prevProg, double currProg) {
        Vec3 span = new Vec3(to.x - from.x, 0, to.z - from.z);
        double range = span.length();
        if (range < 1e-4) return false;
        Vec3 dir = span.scale(1.0 / range);
        Vec3 off = playerPos.subtract(from);
        if (Math.abs(off.y) > 2.8) return false;
        double along = off.x * dir.x + off.z * dir.z;
        double minAlong = Math.min(prevProg, currProg) * range - 1.1;
        double maxAlong = Math.max(prevProg, currProg) * range + 1.1;
        if (along < minAlong || along > maxAlong || along < -0.5 || along > range + 0.8) return false;
        double lateral = Math.abs(off.x * dir.z - off.z * dir.x);
        return lateral <= width * 0.5 + 0.35;
    }

    public static boolean hitsFanSlash(Vec3 playerPos, Vec3 center, Vec3 dirTarget, double radius, double halfAngleDeg) {
        Vec3 off = playerPos.subtract(center);
        if (Math.abs(off.y) > 3.0) return false;
        double distSq = off.x * off.x + off.z * off.z;
        if (distSq > Math.pow(radius + 0.35, 2)) return false;
        if (distSq < 0.25) return true;
        Vec3 forward = new Vec3(dirTarget.x - center.x, 0, dirTarget.z - center.z);
        if (forward.lengthSqr() < 1e-6) return true;
        forward = forward.normalize();
        double dist = Math.sqrt(distSq);
        double dot = Math.clamp((off.x * forward.x + off.z * forward.z) / dist, -1.0, 1.0);
        return Math.toDegrees(Math.acos(dot)) <= halfAngleDeg;
    }

    private static void applyKnockback(net.minecraft.world.entity.player.Player player, Vec3 source, double power) {
        if (power <= 0) return;
        Vec3 away = player.position().subtract(source);
        double h = Math.hypot(away.x, away.z);
        Vec3 dir = h > 1e-4 ? new Vec3(away.x / h, 0, away.z / h) : new Vec3(0, 0, 1);
        double scale = Math.min(1.0, power * 0.13);
        player.push(dir.x * scale, 0.22, dir.z * scale);
    }

    private static void emitHit(Hazard h, String hitId, double coefficient, long serverNow) {
        JsonObject action = new JsonObject();
        action.addProperty("id", "dragon_tower_lucas/" + hitId);
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
