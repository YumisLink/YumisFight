package cn.yumis.fight.modules.dungeons.client;

import cn.yumis.fight.modules.dungeons.api.DungeonEnemy;
import cn.yumis.fight.modules.dungeons.api.TreasureMapItem;
import cn.yumis.fight.modules.presentation.api.client.CameraShake;
import cn.yumis.fight.modules.presentation.api.client.ClientUi;
import cn.yumis.fight.modules.presentation.api.client.EffectSprites;
import cn.yumis.fight.modules.presentation.api.client.EffectSprites.SpriteQuad;
import cn.yumis.fight.modules.presentation.api.client.NearEffectRendering;
import cn.yumis.fight.modules.presentation.api.client.WorldQuads;
import cn.yumis.fight.modules.presentation.api.client.WorldQuads.Quad;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.common.NeoForge;

import static cn.yumis.fight.modules.dungeons.client.EnemyEffectGeometry.ring;

/**
 * World-space visuals for Treasure Hunt:
 * - Overworld 32-block golden vertical beacon & 10-block dig ring when holding an opened map;
 * - Secret realm dual theme-preview portal arches & floating theme labels;
 * - 2.2s Rainbow Force-Open Rescue shockwave sequence;
 * - Escape Golden Goblin golden shimmer aura.
 */
public final class TreasureHuntVisuals {
    private static final int[] RAINBOW = {0xFF4B5C, 0xFF9F43, 0xFFE66D, 0x55E6C1, 0x54A0FF, 0x7E57C2, 0xFF79C6};
    private static final ContextKey<Frame> FRAME =
        new ContextKey<>(Identifier.fromNamespaceAndPath("yumis_dungeon_and_fight", "treasure_hunt_visuals"));

    private record PortalPreview(int cell, String westTitle, int westColor, String eastTitle, int eastColor, int passPercent) {}
    private record RescueFx(Vec3 pos, String title, long startAt, long openAt) {}
    private record Billboard(Vec3 pos, float scale, String text, int color) {}
    private record Frame(List<Quad> quads, List<SpriteQuad> sprites, List<Billboard> labels, boolean fade) {}

    private static PortalPreview portals;
    private static RescueFx rescueFx;
    private static boolean rescueShaken;
    private static boolean rescueSoundPlayed;

    private TreasureHuntVisuals() {}

    public static void register() {
        var bus = NeoForge.EVENT_BUS;
        bus.addListener(TreasureHuntVisuals::extract);
        bus.addListener(TreasureHuntVisuals::submit);
    }

    public static void clear() {
        portals = null;
        rescueFx = null;
        rescueShaken = false;
        rescueSoundPlayed = false;
    }

    static void receivePortals(JsonObject pkt) {
        if (pkt == null || !pkt.has("active") || !pkt.get("active").getAsBoolean()) {
            portals = null;
            return;
        }
        portals = new PortalPreview(
            pkt.get("cell").getAsInt(),
            pkt.get("westTitle").getAsString(),
            pkt.get("westColor").getAsInt(),
            pkt.get("eastTitle").getAsString(),
            pkt.get("eastColor").getAsInt(),
            pkt.get("passPercent").getAsInt()
        );
    }

    static void receivePortalFx(JsonObject pkt) {
        if (pkt == null || !"RAINBOW_RESCUE".equals(pkt.has("kind") ? pkt.get("kind").getAsString() : "")) return;
        Vec3 pos = new Vec3(pkt.get("x").getAsDouble(), pkt.get("y").getAsDouble(), pkt.get("z").getAsDouble());
        String title = pkt.has("title") ? pkt.get("title").getAsString() : "";
        long startAt = pkt.get("startAt").getAsLong();
        long openAt = pkt.get("openAt").getAsLong();
        rescueFx = new RescueFx(pos, title, startAt, openAt);
        rescueShaken = false;
        rescueSoundPlayed = false;
        var mc = Minecraft.getInstance();
        if (mc.level != null && mc.player != null) {
            mc.level.playLocalSound(pos.x, pos.y + 1.5, pos.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.9f, 0.72f, false);
        }
    }

    private static void extract(ExtractLevelRenderStateEvent event) {
        var quads = new ArrayList<Quad>();
        var sprites = new ArrayList<SpriteQuad>();
        var labels = new ArrayList<Billboard>();
        var mc = Minecraft.getInstance();
        var level = mc.level;
        var player = mc.player;
        Vec3 camera = event.getRenderState().cameraRenderState == null ? Vec3.ZERO : event.getRenderState().cameraRenderState.pos;
        if (level != null && player != null && ClientUi.current().ready()) {
            long now = ClientUi.current().serverNow();

            // 1. 主世界寻宝 32 格光柱 & 10 格挖掘金环
            var target = TreasureMapClient.activeTarget();
            if (target != null && level.dimension() == Level.OVERWORLD && target.distance() <= 36.0) {
                Vec3 center = new Vec3(target.x() + 0.5, target.surfaceY() + 0.08, target.z() + 0.5);
                double fade = Math.clamp((36.0 - target.distance()) / 6.0, 0.0, 1.0);
                double pulse = 0.5 + 0.5 * Math.sin(now * 0.005);
                EffectSprites.texturedBeam(sprites, center, center.add(0, 26.0, 0), camera, 0.42 + 0.12 * pulse, now, true, 0xFFE272, (int) (185 * fade));
                EffectSprites.sigil(sprites, center, 2.2 + 0.35 * pulse, now * 0.0022, 0xFFD858, (int) (210 * fade), true);
                if (target.distance() <= TreasureMapItem.DIG_RADIUS + 4.0) {
                    double ringFade = Math.clamp((TreasureMapItem.DIG_RADIUS + 4.0 - target.distance()) / 4.0, 0.0, 1.0);
                    ring(quads, center.add(0, 0.05, 0), TreasureMapItem.DIG_RADIUS, 0.22, 0xFFE678, (int) (165 * ringFade));
                    labels.add(new Billboard(center.add(0, 2.8, 0), 0.024f, "✦ 藏宝点 · 右键藏宝图挖掘", 0xFFFFEC8C));
                }
            }

            // 2. 秘境双门主题预告与光阵
            if (portals != null && DungeonActions.inDungeonDimension()) {
                double ox = portals.cell() * 512.0 + 0.5;
                drawPortalGate(quads, sprites, labels, camera, new Vec3(ox - 29.0, 80.1, 0.5), "左门 · " + portals.westTitle(), portals.westColor(), portals.passPercent(), now);
                drawPortalGate(quads, sprites, labels, camera, new Vec3(ox + 29.0, 80.1, 0.5), "右门 · " + portals.eastTitle(), portals.eastColor(), portals.passPercent(), now);
            } else if (!DungeonActions.inDungeonDimension()) {
                portals = null;
            }

            // 3. 彩虹强行破门 2.2s 演出
            if (rescueFx != null) {
                if (now > rescueFx.openAt() + 500L || !DungeonActions.inDungeonDimension()) {
                    rescueFx = null;
                } else {
                    drawRainbowRescue(quads, sprites, labels, camera, rescueFx, now);
                }
            }

            // 4. 逃跑的黄金哥布林耀眼金光环
            if (DungeonActions.inDungeonDimension()) {
                for (var mob : level.getEntitiesOfClass(DungeonEnemy.class, player.getBoundingBox().inflate(48.0))) {
                    if (mob.isAlive() && "treasure_golden_goblin".equals(mob.appearance())) {
                        Vec3 p = mob.position();
                        EffectSprites.sigil(sprites, p.add(0, 0.08, 0), 1.35, now * 0.007, 0xFFE052, 230, true);
                        EffectSprites.glow(sprites, p.add(0, 0.9, 0), camera, 1.45, 0xFFF3A6, 200);
                    }
                }
            }
        }
        event.getRenderState().setRenderData(FRAME, new Frame(List.copyOf(quads), List.copyOf(sprites), List.copyOf(labels), NearEffectRendering.enabled(event.getCamera())));
    }

    private static void drawPortalGate(List<Quad> quads, List<SpriteQuad> sprites, List<Billboard> labels,
                                       Vec3 camera, Vec3 pos, String title, int color, int passPercent, long now) {
        double pulse = 0.5 + 0.5 * Math.sin(now * 0.004);
        EffectSprites.sigil(sprites, pos, 2.8 + 0.25 * pulse, now * 0.002, color, 215, true);
        EffectSprites.texturedBeam(sprites, pos, pos.add(0, 5.2, 0), camera, 0.36 + 0.06 * pulse, now, true, color, 175);
        ring(quads, pos.add(0, 0.04, 0), 2.5, 0.16, color, 180);
        labels.add(new Billboard(pos.add(0, 4.75, 0), 0.024f, title, 0xFF000000 | (color & 0xFFFFFF)));
        labels.add(new Billboard(pos.add(0, 4.25, 0), 0.018f, "通往更深处 · 通过率 " + passPercent + "%", 0xFFEBE8DE));
    }

    private static void drawRainbowRescue(List<Quad> quads, List<SpriteQuad> sprites, List<Billboard> labels,
                                          Vec3 camera, RescueFx fx, long now) {
        long dur = Math.max(1L, fx.openAt() - fx.startAt());
        double prog = Math.clamp((now - fx.startAt()) / (double) dur, 0.0, 1.0);
        Vec3 pos = fx.pos().add(0, 0.12, 0);
        if (prog < 0.35) {
            // 前 0.77s 假封闭暗紫震颤
            double f = prog / 0.35;
            EffectSprites.sigil(sprites, pos, 3.2 * (1.0 - 0.35 * f), -now * 0.008, 0x6E3B78, 220, false);
            labels.add(new Billboard(pos.add(0, 4.9, 0), 0.025f, "传送门正在封闭……？", 0xFFD99BFF));
        } else {
            // 后 1.43s 七彩爆发强行破门 + 镜头震动
            double burst = Math.clamp((prog - 0.35) / 0.65, 0.0, 0.999);
            if (!rescueShaken) {
                rescueShaken = true;
                CameraShake.impulse(pos, 48, 0.65f, 160, 380);
            }
            if (!rescueSoundPlayed) {
                rescueSoundPlayed = true;
                var mc = Minecraft.getInstance();
                if (mc.level != null) {
                    mc.level.playLocalSound(pos.x, pos.y + 1.5, pos.z, SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.95f, 1.15f, false);
                }
            }
            for (int i = 0; i < RAINBOW.length; i++) {
                double delay = i * 0.06;
                double local = Math.clamp((burst - delay) / (1.0 - delay), 0.0, 0.999);
                if (local <= 0.0) continue;
                int c = RAINBOW[i];
                double r = 2.0 + 18.0 * EffectSprites.easeOutCubic(local);
                EffectSprites.shockwave(sprites, pos.add(0, 0.1 + i * 0.18, 0), r, local * 1.4, c, (int) (235 * (1.0 - local * 0.6)));
            }
            int beamColor = RAINBOW[(int) Math.floorMod(now / 70L, RAINBOW.length)];
            EffectSprites.texturedBeam(sprites, pos, pos.add(0, 18.0, 0), camera, 0.9 + 0.6 * burst, now, true, beamColor, 240);
            labels.add(new Billboard(pos.add(0, 5.2, 0), 0.028f, "🌈 彩虹破门！强行开启【" + fx.title() + "】", 0xFF000000 | beamColor));
        }
    }

    private static void submit(SubmitCustomGeometryEvent event) {
        Frame frame = event.getLevelRenderState().getRenderData(FRAME);
        var camera = event.getLevelRenderState().cameraRenderState;
        if (frame == null || camera == null) return;
        WorldQuads.submit(event, frame.quads(), frame.fade());
        EffectSprites.submit(event, frame.sprites(), frame.fade());
        if (frame.labels().isEmpty()) return;
        var poses = event.getPoseStack();
        var font = Minecraft.getInstance().font;
        for (Billboard b : frame.labels()) {
            Vec3 rel = b.pos().subtract(camera.pos);
            poses.pushPose();
            poses.translate(rel.x, rel.y, rel.z);
            poses.mulPose(camera.orientation);
            poses.scale(b.scale(), -b.scale(), b.scale());
            var text = Component.literal(b.text()).getVisualOrderText();
            event.getSubmitNodeCollector().submitText(poses, -font.width(text) / 2f, 0f, text, true,
                Font.DisplayMode.NORMAL, 0x00F000F0, b.color(), 0x55000000, 0);
            poses.popPose();
        }
    }
}
