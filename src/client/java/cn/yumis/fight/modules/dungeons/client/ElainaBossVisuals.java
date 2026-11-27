package cn.yumis.fight.modules.dungeons.client;

import cn.yumis.fight.modules.dungeons.client.ElainaBossClient.ClockReverseVisual;
import cn.yumis.fight.modules.dungeons.client.ElainaBossClient.Hazard;
import cn.yumis.fight.modules.dungeons.client.ElainaBossClient.RewindAnchorVisual;
import cn.yumis.fight.modules.presentation.api.client.CameraShake;
import cn.yumis.fight.modules.presentation.api.client.ClientUi;
import cn.yumis.fight.modules.presentation.api.client.EffectSprites;
import cn.yumis.fight.modules.presentation.api.client.EffectSprites.SpriteQuad;
import cn.yumis.fight.modules.presentation.api.client.LightningBolt;
import cn.yumis.fight.modules.presentation.api.client.NearEffectRendering;
import cn.yumis.fight.modules.presentation.api.client.RibbonTrail;
import cn.yumis.fight.modules.presentation.api.client.WorldQuads;
import cn.yumis.fight.modules.presentation.api.client.WorldQuads.Quad;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.ArrayList;
import java.util.List;

import static cn.yumis.fight.modules.dungeons.client.EnemyEffectGeometry.*;

/**
 * 封印塔第50层首领「伊蕾娜」世界空间特效渲染器：
 * 魔杖冲锋轨迹、追踪星辰法阵、三元素坠球、24连日冕、时钟回溯锚点、巨型逆转时钟、南瓜爆弹、午夜陨石与终局巨型陨石。
 */
public final class ElainaBossVisuals {
    private static final ContextKey<Frame> FRAME =
        new ContextKey<>(Identifier.fromNamespaceAndPath("yumis_dungeon_and_fight", "elaina_boss"));

    private record Frame(List<Quad> quads, List<SpriteQuad> sprites, boolean fade) {}
    private static final java.util.Set<String> shaken = new java.util.HashSet<>();

    private ElainaBossVisuals() {}

    public static void register() {
        var events = NeoForge.EVENT_BUS;
        events.addListener(ElainaBossVisuals::extract);
        events.addListener(ElainaBossVisuals::submit);
    }

    private static void extract(ExtractLevelRenderStateEvent event) {
        var quads = new ArrayList<Quad>();
        var sprites = new ArrayList<SpriteQuad>();
        var mc = Minecraft.getInstance();
        Vec3 camera = event.getRenderState().cameraRenderState == null ? Vec3.ZERO : event.getRenderState().cameraRenderState.pos;
        if (mc.level != null && mc.player != null && ClientUi.current().ready()) {
            long now = ClientUi.current().serverNow();
            shaken.retainAll(ElainaBossClient.hazards.keySet());

            // 1. 玩家被 80% 阶段冰冻时的脚下冰晶环与冰柱
            if (ElainaBossClient.isFrozen()) {
                Vec3 p = mc.player.position().add(0, 0.05, 0);
                ring(quads, p, 0.95, 0.14, 0x8be9ff, 210);
                box(quads, p.add(0, 0.95, 0), 0.55, 0.95, 0.55, 0x9ceeff, 82);
                EffectSprites.sigil(sprites, p.add(0, 0.02, 0), 1.35, now * 0.001, 0x8be9ff, 210, true);
            }

            // 2. 20% 阶段空中巨型逆转时钟
            ClockReverseVisual clock = ElainaBossClient.clockReverse;
            if (clock != null && now >= clock.startAt && now < clock.endAt) {
                drawGiantClock(quads, sprites, clock.center, now - clock.startAt, clock.endAt - clock.startAt);
            }

            // 3. 技能 10：时钟回溯锚点
            for (RewindAnchorVisual anchor : ElainaBossClient.rewindAnchors) {
                if (now >= anchor.startAt && now < anchor.endAt) {
                    drawRewindAnchor(quads, sprites, anchor.pos, now - anchor.startAt, anchor.endAt - anchor.startAt);
                }
            }

            // 4. 所有活跃场地技能与陨石
            for (Hazard h : ElainaBossClient.hazards.values()) {
                if (now < h.startAt || now > h.endAt + 400L) continue;
                drawHazard(quads, sprites, camera, h, now);
            }
        }
        event.getRenderState().setRenderData(FRAME, new Frame(List.copyOf(quads), List.copyOf(sprites), NearEffectRendering.enabled(event.getCamera())));
    }

    private static void drawHazard(List<Quad> q, List<SpriteQuad> sp, Vec3 camera, Hazard h, long now) {
        boolean active = now >= h.warnUntil;
        double warnProgress = h.warnUntil <= h.startAt ? 1.0
            : Math.clamp((now - h.startAt) / (double) (h.warnUntil - h.startAt), 0.0, 1.0);
        double activeProgress = h.endAt <= h.warnUntil ? 1.0
            : Math.clamp((now - h.warnUntil) / (double) (h.endAt - h.warnUntil), 0.0, 1.0);
        double clampedActive = Math.clamp(activeProgress, 0.0, 0.999);

        switch (h.type) {
            case "WAND_CHARGE" -> {
                Vec3 a = new Vec3(h.from.x, h.from.y - 0.7, h.from.z);
                Vec3 b = new Vec3(h.to.x, h.to.y - 0.7, h.to.z);
                rect(q, a, b, h.width, h.color, 90);
                Vec3 head = h.from.lerp(h.to, activeProgress);
                EffectSprites.texturedBeam(sp, h.from, head, camera, 0.34, now, true, 0xe8baff, 235);
                EffectSprites.glow(sp, head, camera, 1.15, 0xffe4ff, 230);
            }
            case "TRACKING_STAR" -> {
                Vec3 ground = h.currentPos(now).add(0, 0.06, 0);
                if (!active) {
                    EffectSprites.warningCircle(sp, ground, h.radius, warnProgress, h.color, 210);
                } else {
                    EffectSprites.sigil(sp, ground, h.radius * 1.02, now * 0.0022, 0xfff6a8, 225, true);
                    double rot = now * 0.0025;
                    drawStarTriangle(q, ground.add(0, 0.04, 0), h.radius * 0.92, rot, 0xfff6a8, 220);
                    drawStarTriangle(q, ground.add(0, 0.04, 0), h.radius * 0.92, rot + Math.PI / 3.0, 0xfff6a8, 220);
                }
            }
            case "ORB_ICE", "ORB_FIRE", "ORB_EARTH" -> {
                Vec3 ground = h.from.add(0, 0.05, 0);
                if (!active) {
                    EffectSprites.warningCircle(sp, ground, h.radius, warnProgress, h.color, 210);
                    double height = 16.0 * (1.0 - warnProgress) + 0.6;
                    Vec3 orbPos = ground.add(0, height, 0);
                    sphere(q, orbPos, 0.72, h.color, 230, 14, 7);
                    EffectSprites.glow(sp, orbPos, camera, 1.45, h.color, 230);
                    RibbonTrail.push("elaina:" + h.id, orbPos, 0.42, h.color, 215, 280, ClientUi.now());
                } else {
                    if (shaken.add(h.id)) CameraShake.impulse(ground, 22, 0.24f, 60, 140);
                    double fade = Math.clamp(1.0 - activeProgress, 0.0, 1.0);
                    EffectSprites.impactBurst(sp, ground.add(0, 0.35, 0), camera, h.radius * 1.15, clampedActive, h.color, 240, false);
                    EffectSprites.shockwave(sp, ground.add(0, 0.10, 0), h.radius * (0.25 + 0.9 * EffectSprites.easeOutCubic(clampedActive)), clampedActive * 1.2, h.color, (int) (235 * fade));
                }
            }
            case "CORONA" -> {
                Vec3 ground = h.from.add(0, 0.05, 0);
                if (!active) {
                    Vec3 center = ground.add(0, 1.2 + 2.0 * warnProgress, 0);
                    sphere(q, center, 1.85, 0xff8e32, 220, 20, 10);
                    sphere(q, center, 1.62, 0xffd96e, 245, 16, 8);
                    EffectSprites.glow(sp, center, camera, 3.2, 0xffb34d, 220);
                    EffectSprites.warningCircle(sp, ground, h.radius, warnProgress, 0xff9a3e, 215);
                } else {
                    if (shaken.add(h.id)) CameraShake.impulse(ground, 26, 0.34f, 80, 180);
                    double fade = Math.clamp(1.0 - activeProgress, 0.0, 1.0);
                    EffectSprites.impactBurst(sp, ground.add(0, 0.5, 0), camera, h.radius * 1.2, clampedActive, 0xff9c42, 245, false);
                    EffectSprites.shockwave(sp, ground.add(0, 0.10, 0), h.radius * 1.15 * EffectSprites.easeOutCubic(clampedActive), clampedActive * 1.4, 0xffc66e, (int) (240 * fade));
                }
            }
            case "PUMPKIN" -> {
                Vec3 landing = h.to.add(0, 0.05, 0);
                if (!active) {
                    EffectSprites.warningCircle(sp, landing, h.radius, warnProgress, 0xff9830, 210);
                    Vec3 arcPos = h.from.lerp(h.to, warnProgress).add(0, Math.sin(warnProgress * Math.PI) * 3.2, 0);
                    box(q, arcPos, 0.42, 0.42, 0.42, 0xff7a1a, 245);
                    box(q, arcPos.add(0, 0.46, 0), 0.10, 0.12, 0.10, 0x3b8c2a, 245);
                    RibbonTrail.push("elaina:" + h.id, arcPos, 0.32, 0xff8224, 215, 240, ClientUi.now());
                } else {
                    if (shaken.add(h.id)) CameraShake.impulse(landing, 20, 0.24f, 60, 140);
                    double fade = Math.clamp(1.0 - activeProgress, 0.0, 1.0);
                    EffectSprites.impactBurst(sp, landing.add(0, 0.3, 0), camera, h.radius * 1.1, clampedActive, 0xff7d26, 240, false);
                    EffectSprites.shockwave(sp, landing.add(0, 0.10, 0), h.radius * 1.1 * EffectSprites.easeOutCubic(clampedActive), clampedActive, 0xffb347, (int) (230 * fade));
                }
            }
            case "METEOR_SMALL" -> {
                Vec3 ground = h.from.add(0, 0.05, 0);
                if (!active) {
                    EffectSprites.warningCircle(sp, ground, h.radius, warnProgress, h.color, 215);
                    double y = 26.0 * (1.0 - warnProgress * warnProgress) + 0.6;
                    Vec3 rock = ground.add((1.0 - warnProgress) * 4.0, y, (1.0 - warnProgress) * 2.5);
                    sphere(q, rock, 0.95, h.color, 235, 12, 6);
                    EffectSprites.glow(sp, rock, camera, 1.8, 0xffc2ff, 225);
                    RibbonTrail.ribbon(sp, List.of(rock, rock.add(1.4, 4.2, 0.9)), camera, 0.55, h.color, 225);
                } else {
                    if (shaken.add(h.id)) CameraShake.impulse(ground, 24, 0.28f, 70, 160);
                    double fade = Math.clamp(1.0 - activeProgress, 0.0, 1.0);
                    EffectSprites.impactBurst(sp, ground.add(0, 0.3, 0), camera, h.radius * 1.15, clampedActive, h.color, 245, false);
                    EffectSprites.shockwave(sp, ground.add(0, 0.10, 0), h.radius * 1.15 * EffectSprites.easeOutCubic(clampedActive), clampedActive * 1.2, 0xffc2ff, (int) (235 * fade));
                }
            }
            case "METEOR_FINALE_1", "METEOR_FINALE_2" -> {
                Vec3 ground = h.from.add(0, 0.08, 0);
                boolean first = "METEOR_FINALE_1".equals(h.type);
                int coreColor = first ? 0xff5a2c : 0xff1e38;
                int ringColor = first ? 0xffc864 : 0xff647a;
                if (!active) {
                    double maxR = first ? 48.0 : h.radius;
                    EffectSprites.sigil(sp, ground, maxR, now * 0.0005, ringColor, 200, true);
                    if (first) {
                        EffectSprites.warningCircle(sp, ground.add(0, 0.02, 0), 12.0, warnProgress, 0xff2c1a, 225);
                        ring(q, ground, 24.0, 0.35, 0xff7b36, 185);
                        ring(q, ground, 48.0 * warnProgress, 0.35, 0xffe484, 210);
                    } else {
                        EffectSprites.warningCircle(sp, ground.add(0, 0.02, 0), maxR, warnProgress, 0xff182e, 225);
                    }
                    double height = 42.0 * (1.0 - warnProgress) + 3.5;
                    double sphereR = first ? 5.2 : 4.2;
                    Vec3 meteorPos = ground.add(0, height, 0);
                    sphere(q, meteorPos, sphereR, coreColor, 235, 20, 10);
                    sphere(q, meteorPos, sphereR * 0.72, 0xffe8a0, 245, 14, 7);
                    EffectSprites.glow(sp, meteorPos, camera, sphereR * 2.2, coreColor, 230);
                    LightningBolt.helixArcs(sp, meteorPos, meteorPos.add(0, 14.0, 0), camera, now, sphereR * 0.9, 3, ringColor, 215);
                } else {
                    if (shaken.add(h.id)) CameraShake.impulse(ground, 64, 0.85f, 260, 480);
                    double fade = Math.clamp(1.0 - activeProgress, 0.0, 1.0);
                    EffectSprites.shockwave(sp, ground.add(0, 0.25, 0), 48.0 * EffectSprites.easeOutCubic(clampedActive), clampedActive * 1.4, coreColor, (int) (250 * fade));
                    EffectSprites.impactBurst(sp, ground.add(0, 2.0, 0), camera, 22.0, clampedActive, ringColor, 245, false);
                }
            }
            case "ELAINA_ASTRAL_METEOR" -> {
                Vec3 ground = h.from.add(0, 0.05, 0);
                if (!active) {
                    EffectSprites.warningCircle(sp, ground, h.radius, warnProgress, h.color, 215);
                    double y = 24.0 * (1.0 - warnProgress * warnProgress) + 0.7;
                    Vec3 rock = ground.add((1.0 - warnProgress) * 3.5, y, (1.0 - warnProgress) * 2.0);
                    sphere(q, rock, 0.92, h.color, 240, 12, 6);
                    EffectSprites.glow(sp, rock, camera, 1.75, h.color, 235);
                    RibbonTrail.ribbon(sp, List.of(rock, rock.add(1.2, 4.0, 0.7)), camera, 0.48, h.color, 225);
                } else {
                    if (shaken.add(h.id)) CameraShake.impulse(ground, 24, 0.24f, 60, 140);
                    double fade = Math.clamp(1.0 - activeProgress, 0.0, 1.0);
                    EffectSprites.impactBurst(sp, ground.add(0, 0.35, 0), camera, h.radius * 1.15, clampedActive, h.color, 245, false);
                    EffectSprites.shockwave(sp, ground.add(0, 0.12, 0), h.radius * 1.15 * EffectSprites.easeOutCubic(clampedActive), clampedActive * 1.3, h.color, (int) (235 * fade));
                }
            }
            case "ELAINA_TRIPLE_JACK", "ELAINA_TRIPLE_ARCTIC", "ELAINA_TRIPLE_BLACKHOLE", "ELAINA_TRIPLE_THUNDER" -> {
                Vec3 ground = h.from.add(0, 0.06, 0);
                if (!active) {
                    EffectSprites.warningCircle(sp, ground, h.radius, warnProgress, h.color, 220);
                    if ("ELAINA_TRIPLE_JACK".equals(h.type)) {
                        double y = 18.0 * (1.0 - warnProgress * warnProgress) + 1.1;
                        Vec3 p = ground.add(0, y, 0);
                        sphere(q, p, 1.35, h.color, 235, 16, 8);
                        EffectSprites.glow(sp, p, camera, 2.5, h.color, 230);
                    } else if ("ELAINA_TRIPLE_BLACKHOLE".equals(h.type)) {
                        Vec3 core = ground.add(0, 1.8, 0);
                        sphere(q, core, 0.9 + 0.45 * warnProgress, 0x0b0514, 240, 14, 7);
                        EffectSprites.glow(sp, core, camera, 2.2, h.color, 210);
                    } else if ("ELAINA_TRIPLE_ARCTIC".equals(h.type)) {
                        EffectSprites.sigil(sp, ground.add(0, 0.03, 0), h.radius, now * 0.002, 0xc8f6ff, 220, true);
                    }
                } else {
                    if (shaken.add(h.id)) CameraShake.impulse(ground, 26, 0.34f, 80, 180);
                    double fade = Math.clamp(1.0 - activeProgress, 0.0, 1.0);
                    EffectSprites.shockwave(sp, ground.add(0, 0.12, 0), h.radius * 1.2 * EffectSprites.easeOutCubic(clampedActive), clampedActive * 1.4, h.color, (int) (240 * fade));
                    if ("ELAINA_TRIPLE_THUNDER".equals(h.type)) {
                        LightningBolt.bolt(sp, ground.add(0, 18.0, 0), ground, camera, (now / 45L) ^ h.id.hashCode(), 14, 1.1, 0.18 * fade + 0.05, h.color, (int) (255 * fade), true);
                        EffectSprites.impactBurst(sp, ground.add(0, 0.2, 0), camera, h.radius * 1.2, clampedActive, h.color, 250, true);
                    } else {
                        EffectSprites.impactBurst(sp, ground.add(0, 0.6, 0), camera, h.radius * 1.15, clampedActive, h.color, 245, false);
                    }
                }
            }
            default -> {}
        }
    }

    private static void drawGiantClock(List<Quad> q, List<SpriteQuad> sp, Vec3 center, long age, long duration) {
        double progress = Math.clamp(age / (double) Math.max(1L, duration), 0.0, 1.0);
        double radius = 6.2;
        Vec3 c = center.add(0, 2.5, 0);
        EffectSprites.sigil(sp, c, radius * 1.06, -progress * Math.PI * 2.0, 0xffdf78, 225, true);
        ring(q, c, radius, 0.32, 0xffdf78, 235);
        for (int i = 0; i < 12; i++) {
            double a = i * Math.PI / 6.0;
            Vec3 inner = c.add(Math.cos(a) * (radius * 0.82), 0, Math.sin(a) * (radius * 0.82));
            Vec3 outer = c.add(Math.cos(a) * (radius * 0.96), 0, Math.sin(a) * (radius * 0.96));
            line(q, inner, outer, 0.14, 0xfff0b0, 225);
        }
        double minuteAngle = -progress * Math.PI * 10.0;
        double hourAngle = -progress * Math.PI * 3.0;
        line(q, c, c.add(Math.cos(minuteAngle) * (radius * 0.76), 0, Math.sin(minuteAngle) * (radius * 0.76)), 0.16, 0xffffff, 245);
        line(q, c, c.add(Math.cos(hourAngle) * (radius * 0.48), 0, Math.sin(hourAngle) * (radius * 0.48)), 0.22, 0xffd058, 245);
    }

    private static void drawRewindAnchor(List<Quad> q, List<SpriteQuad> sp, Vec3 pos, long age, long duration) {
        double progress = Math.clamp(age / (double) Math.max(1L, duration), 0.0, 1.0);
        Vec3 c = pos.add(0, 0.07, 0);
        EffectSprites.sigil(sp, c, 1.25, -progress * Math.PI * 3.0, 0x7ef2ff, 220, true);
        double hand = -progress * Math.PI * 4.0;
        line(q, c, c.add(Math.cos(hand) * 0.9, 0, Math.sin(hand) * 0.9), 0.07, 0xffffff, 235);
        line(q, c, c.add(0, 1.8, 0), 0.06, 0x8beeff, 145);
    }

    private static void drawStarTriangle(List<Quad> q, Vec3 c, double r, double rot, int color, int alpha) {
        Vec3[] pts = new Vec3[3];
        for (int i = 0; i < 3; i++) {
            double a = rot + i * Math.PI * 2.0 / 3.0;
            pts[i] = c.add(Math.cos(a) * r, 0, Math.sin(a) * r);
        }
        line(q, pts[0], pts[1], 0.06, color, alpha);
        line(q, pts[1], pts[2], 0.06, color, alpha);
        line(q, pts[2], pts[0], 0.06, color, alpha);
    }

    private static void sphere(List<Quad> out, Vec3 center, double radius, int color, int alpha, int slices, int bands) {
        if (alpha <= 0 || radius <= 0) return;
        int clamped = Math.clamp(alpha, 0, 255);
        for (int y = 0; y < bands; y++) {
            double a = -Math.PI / 2 + Math.PI * y / bands;
            double b = -Math.PI / 2 + Math.PI * (y + 1) / bands;
            for (int x = 0; x < slices; x++) {
                double c = Math.PI * 2 * x / slices;
                double d = Math.PI * 2 * (x + 1) / slices;
                out.add(new Quad(
                    surface(center, radius, a, c),
                    surface(center, radius, a, d),
                    surface(center, radius, b, d),
                    surface(center, radius, b, c),
                    color, clamped
                ));
            }
        }
    }

    private static Vec3 surface(Vec3 p, double r, double lat, double lon) {
        return p.add(Math.cos(lat) * Math.cos(lon) * r, Math.sin(lat) * r, Math.cos(lat) * Math.sin(lon) * r);
    }

    private static void submit(SubmitCustomGeometryEvent event) {
        Frame frame = event.getLevelRenderState().getRenderData(FRAME);
        if (frame != null) {
            WorldQuads.submit(event, frame.quads(), frame.fade());
            EffectSprites.submit(event, frame.sprites(), frame.fade());
        }
    }
}
