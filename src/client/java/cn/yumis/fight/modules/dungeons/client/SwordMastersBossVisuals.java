package cn.yumis.fight.modules.dungeons.client;

import cn.yumis.fight.modules.dungeons.client.SwordMastersBossClient.Hazard;
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
 * 锻炉塔第 20 层专属首领「阿拉德四剑圣」世界空间纯白刃战特效与红圈/斩线预警渲染器。
 */
public final class SwordMastersBossVisuals {
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final ContextKey<Frame> FRAME =
        new ContextKey<>(Identifier.fromNamespaceAndPath("yumis_dungeon_and_fight", "sword_masters_boss"));

    private record Frame(List<Quad> quads, List<SpriteQuad> sprites, boolean fade) {}
    private static final java.util.Set<String> shaken = new java.util.HashSet<>();

    private SwordMastersBossVisuals() {}

    public static void register() {
        var events = NeoForge.EVENT_BUS;
        events.addListener(SwordMastersBossVisuals::extract);
        events.addListener(SwordMastersBossVisuals::submit);
    }

    private static void extract(ExtractLevelRenderStateEvent event) {
        var quads = new ArrayList<Quad>();
        var sprites = new ArrayList<SpriteQuad>();
        var mc = Minecraft.getInstance();
        Vec3 camera = event.getRenderState().cameraRenderState == null ? Vec3.ZERO : event.getRenderState().cameraRenderState.pos;
        if (mc.level != null && mc.player != null && ClientUi.current().ready()) {
            long now = ClientUi.current().serverNow();
            shaken.retainAll(SwordMastersBossClient.hazards.keySet());
            for (Hazard h : SwordMastersBossClient.hazards.values()) {
                if (now < h.startAt || now > h.endAt + 350L) continue;
                drawHazard(quads, sprites, camera, h, now);
            }
        }
        event.getRenderState().setRenderData(FRAME, new Frame(List.copyOf(quads), List.copyOf(sprites), NearEffectRendering.enabled(event.getCamera())));
    }

    private static void drawHazard(List<Quad> q, List<SpriteQuad> sp, Vec3 camera, Hazard h, long now) {
        boolean active = now >= h.warnUntil;
        double warnProg = h.warnUntil <= h.startAt ? 1.0
            : Math.clamp((now - h.startAt) / (double) (h.warnUntil - h.startAt), 0.0, 1.0);
        double activeProg = h.endAt <= h.warnUntil ? 1.0
            : Math.clamp((now - h.warnUntil) / (double) (h.endAt - h.warnUntil), 0.0, 1.0);
        double clampedActive = Math.clamp(activeProg, 0.0, 0.999);

        switch (h.type) {
            case "CIRCLE" -> {
                Vec3 g = h.from.add(0, 0.07, 0);
                if (!active) {
                    EffectSprites.warningCircle(sp, g, h.radius, warnProg, h.color, 215);
                } else {
                    if (shaken.add(h.id)) CameraShake.impulse(g, 24, 0.30f, 70, 160);
                    double fade = Math.clamp(1.0 - activeProg, 0.0, 1.0);
                    EffectSprites.shockwave(sp, g.add(0, 0.12, 0), h.radius * 1.15 * EffectSprites.easeOutCubic(clampedActive), clampedActive * 1.3, h.color, (int) (245 * fade));
                    EffectSprites.impactBurst(sp, g.add(0, 0.4, 0), camera, h.radius * 1.1, clampedActive, h.color, 245, false);
                }
            }
            case "RING_WAVE" -> {
                Vec3 g = h.from.add(0, 0.08, 0);
                if (!active) {
                    ring(q, g, h.radius, h.width * 0.45, h.color, (int) (55 + 120 * warnProg));
                    ring(q, g, h.auxRadius, 0.14, 0xfff0d0, 140);
                } else {
                    if (shaken.add(h.id)) CameraShake.impulse(g, 28, 0.28f, 80, 160);
                    double currR = h.radius + (h.auxRadius - h.radius) * activeProg;
                    double fade = Math.clamp(1.0 - Math.max(0.0, activeProg - 0.8) * 5.0, 0.0, 1.0);
                    EffectSprites.shockwave(sp, g.add(0, 0.12, 0), currR, activeProg * 1.6, h.color, (int) (245 * fade));
                    ring(q, g.add(0, 0.16, 0), currR, h.width * 0.42, 0xffffff, (int) (185 * fade));
                }
            }
            case "SAFE_BAND_BURST" -> {
                Vec3 g = h.from.add(0, 0.07, 0);
                double safeMin = h.radius;
                double safeMax = h.width;
                double outerMax = h.auxRadius;
                double safeMid = (safeMin + safeMax) * 0.5;
                double safeW = Math.max(0.5, safeMax - safeMin);
                if (!active) {
                    disc(q, g, safeMin, 0xff6b45, (int) (38 + 65 * warnProg));
                    ring(q, g, (safeMax + outerMax) * 0.5, Math.max(0.5, outerMax - safeMax), 0xff6b45, (int) (34 + 60 * warnProg));
                    ring(q, g.add(0, 0.02, 0), safeMid, safeW, 0x4dff9e, (int) (58 + 70 * warnProg));
                    ring(q, g.add(0, 0.03, 0), safeMin, 0.16, 0x9effc8, 230);
                    ring(q, g.add(0, 0.03, 0), safeMax, 0.16, 0x9effc8, 230);
                } else {
                    if (shaken.add(h.id)) CameraShake.impulse(g, 32, 0.44f, 110, 220);
                    double fade = Math.clamp(1.0 - activeProg, 0.0, 1.0);
                    disc(q, g, safeMin, 0xffd088, (int) (155 * fade));
                    ring(q, g, (safeMax + outerMax) * 0.5, Math.max(0.5, outerMax - safeMax), 0xffa855, (int) (155 * fade));
                    EffectSprites.shockwave(sp, g.add(0, 0.18, 0), safeMin * 1.05, clampedActive, 0xffc864, (int) (240 * fade));
                    EffectSprites.shockwave(sp, g.add(0, 0.18, 0), outerMax, -clampedActive, 0xff8c42, (int) (235 * fade));
                }
            }
            case "DASH_LINE" -> {
                Vec3 gFrom = h.from.add(0, 0.08, 0);
                Vec3 gTo = h.to.add(0, 0.08, 0);
                rect(q, gFrom, gTo, h.width, h.color, active ? 85 : (int) (38 + 64 * warnProg));
                if (!active) {
                    rect(q, gFrom.add(0, 0.01, 0), gFrom.lerp(gTo, warnProg).add(0, 0.01, 0), h.width * 0.84, 0xffffff, 75);
                } else {
                    Vec3 head = h.from.lerp(h.to, activeProg);
                    Vec3 dir = horizontalDir(h.from, h.to);
                    EffectSprites.texturedBeam(sp, h.from.add(0, 1.0, 0), head.add(0, 1.0, 0), camera, 0.42, now, true, h.color, 235);
                    EffectSprites.slashArc(sp, head.add(0, 1.08, 0), dir, 0, h.width * 0.95, clampedActive, h.color, 245);
                }
            }
            case "SLASH_WAVE" -> {
                Vec3 gFrom = h.from.add(0, 0.07, 0);
                Vec3 gTo = h.to.add(0, 0.07, 0);
                int laneAlpha = active ? (int) (72 * (1.0 - activeProg * 0.7)) : (int) (34 + 62 * warnProg);
                rect(q, gFrom, gTo, h.width, h.color, laneAlpha);
                if (!active) {
                    rect(q, gFrom.add(0, 0.01, 0), gFrom.lerp(gTo, warnProg).add(0, 0.01, 0), h.width * 0.88, 0xf2faff, 68);
                } else {
                    Vec3 dir = horizontalDir(h.from, h.to);
                    Vec3 head = h.from.lerp(h.to, activeProg);
                    double fade = Math.clamp(1.0 - Math.max(0.0, activeProg - 0.75) * 4.0, 0.0, 1.0);
                    int alpha = (int) (230 * fade);
                    swordWave(q, head.add(0, 1.08, 0), dir, h.slashAngle, h.width * 0.64, 0.56, h.color, 0xffffff, alpha);
                    EffectSprites.slashArc(sp, head.add(0, 1.1, 0), dir, h.slashAngle, h.width * 0.95, Math.clamp((activeProg * 1.4) % 1.0, 0.15, 0.85), h.color, (int) (245 * fade));
                    RibbonTrail.push("smwave:" + h.id, head.add(0, 1.0, 0), h.width * 0.28, h.color, (int) (215 * fade), 220, ClientUi.now());
                }
            }
            case "INSTANT_LINE" -> {
                Vec3 gFrom = h.from.add(0, 0.09, 0);
                Vec3 gTo = h.to.add(0, 0.09, 0);
                if (!active) {
                    rect(q, gFrom, gTo, h.width, h.color, (int) (34 + 68 * warnProg));
                    double coreW = Math.max(0.18, h.width * (1.0 - warnProg * 0.75));
                    rect(q, gFrom.add(0, 0.015, 0), gTo.add(0, 0.015, 0), coreW, 0xffffff, (int) (75 + 110 * warnProg));
                } else {
                    if (shaken.add(h.id)) CameraShake.impulse(gFrom.lerp(gTo, 0.5), 26, 0.34f, 80, 170);
                    double fade = Math.clamp(1.0 - activeProg, 0.0, 1.0);
                    rect(q, gFrom, gTo, h.width, h.color, (int) (135 * fade));
                    EffectSprites.texturedBeam(sp, h.from.add(0, 1.1, 0), h.to.add(0, 1.1, 0), camera, 0.72 * fade + 0.14, now, true, h.color, (int) (250 * fade));
                    LightningBolt.bolt(sp, h.from.add(0, 1.1, 0), h.to.add(0, 1.1, 0), camera, (now / 45L) ^ h.id.hashCode(), 10, 0.42, 0.09 * fade + 0.03, 0xffffff, (int) (240 * fade), false);
                }
            }
            case "FAN_SLASH" -> {
                Vec3 g = h.from.add(0, 0.08, 0);
                Vec3 dir = horizontalDir(h.from, h.to);
                double halfRad = Math.toRadians(h.slashAngle);
                if (!active) {
                    fan(q, g, dir, h.radius, halfRad, h.color, (int) (36 + 64 * warnProg));
                    fan(q, g.add(0, 0.015, 0), dir, h.radius * warnProg, halfRad, 0xffffff, 65);
                } else {
                    if (shaken.add(h.id)) CameraShake.impulse(g, 24, 0.28f, 70, 150);
                    double fade = Math.clamp(1.0 - activeProg, 0.0, 1.0);
                    fan(q, g, dir, h.radius, halfRad, 0xffffff, (int) (125 * fade));
                    crescent(q, h.from.add(0, 1.15, 0), dir, side(dir), h.radius * 0.96, 0.72, h.color, 0xffffff, (int) (195 * fade));
                    EffectSprites.slashArc(sp, h.from.add(0, 1.18, 0), dir, 0, h.radius * 1.15, clampedActive, h.color, 250);
                }
            }
            case "SPIN_VORTEX" -> {
                Vec3 pos = h.currentPos(now);
                Vec3 g = pos.add(0, 0.07, 0);
                if (!active) {
                    EffectSprites.warningCircle(sp, g, h.radius, warnProg, h.color, 210);
                } else {
                    double fade = Math.clamp((h.endAt - now) / 350.0, 0.0, 1.0);
                    EffectSprites.sigil(sp, g, h.radius * 1.04, now * 0.005, h.color, (int) (215 * fade), true);
                    if (h.auxRadius > h.radius) {
                        ring(q, g, h.auxRadius, 0.10, h.color, (int) (85 * fade));
                    }
                    double spin = now * 0.016;
                    double sub = (now % 220L) / 220.0;
                    for (int k = 0; k < 2; k++) {
                        double a = spin + k * Math.PI;
                        Vec3 d = new Vec3(Math.cos(a), 0, Math.sin(a));
                        EffectSprites.slashArc(sp, pos.add(0, 1.15, 0), d, k == 0 ? 12 : -12, h.radius * 1.15, sub, h.color, (int) (240 * fade));
                    }
                }
            }
            default -> {}
        }
    }

    private static void swordWave(List<Quad> out, Vec3 tip, Vec3 direction, double slashAngle,
                                  double radius, double thickness, int fill, int edge, int alpha) {
        if (direction.lengthSqr() < 1e-6 || alpha <= 0) return;
        Vec3 right = side(direction).normalize();
        Vec3 up = direction.cross(right).normalize();
        double angle = Math.toRadians(slashAngle);
        Vec3 span = right.scale(Math.cos(angle)).add(up.scale(Math.sin(angle)));
        Vec3 forward = direction.scale(0.68);
        crescent(out, tip.subtract(forward.scale(radius)), forward, span, radius, thickness, fill, edge, alpha);
    }

    private static void crescent(List<Quad> out, Vec3 p, Vec3 u, Vec3 v, double radius, double thickness,
                                 int fill, int edge, int alpha) {
        if (alpha <= 0) return;
        Vec3 cross = u.cross(v);
        Vec3 depth = (cross.lengthSqr() < 1e-6 ? UP : cross.normalize()).scale(0.08);
        int opacity = Math.clamp(alpha, 0, 255);
        int rim = Math.clamp(alpha + 25, 0, 255);
        for (int i = 0; i < 20; i++) {
            double a = -Math.PI / 2.0 + i * Math.PI / 20.0;
            double b = a + Math.PI / 20.0;
            Vec3 aa = p.add(u.scale(Math.cos(a) * radius)).add(v.scale(Math.sin(a) * radius));
            Vec3 bb = p.add(u.scale(Math.cos(b) * radius)).add(v.scale(Math.sin(b) * radius));
            Vec3 ia = aa.subtract(u.scale(thickness * Math.cos(a)));
            Vec3 ib = bb.subtract(u.scale(thickness * Math.cos(b)));
            Vec3 da = depth.scale(Math.cos(a));
            Vec3 db = depth.scale(Math.cos(b));
            out.add(new Quad(aa.add(da), bb.add(db), ib.add(db), ia.add(da), fill, opacity));
            out.add(new Quad(ia.subtract(da), ib.subtract(db), bb.subtract(db), aa.subtract(da), fill, opacity));
            out.add(new Quad(aa.subtract(da), bb.subtract(db), bb.add(db), aa.add(da), edge, rim));
        }
    }

    private static Vec3 horizontalDir(Vec3 from, Vec3 to) {
        Vec3 d = new Vec3(to.x - from.x, 0, to.z - from.z);
        return d.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : d.normalize();
    }

    private static Vec3 side(Vec3 direction) {
        Vec3 s = new Vec3(direction.z, 0, -direction.x);
        return s.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : s.normalize();
    }

    private static void submit(SubmitCustomGeometryEvent event) {
        Frame frame = event.getLevelRenderState().getRenderData(FRAME);
        if (frame == null || event.getLevelRenderState().cameraRenderState == null) return;
        WorldQuads.submit(event, frame.quads(), frame.fade());
        EffectSprites.submit(event, frame.sprites(), frame.fade());
    }
}
