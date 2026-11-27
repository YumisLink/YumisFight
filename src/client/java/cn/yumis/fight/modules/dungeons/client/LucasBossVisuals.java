package cn.yumis.fight.modules.dungeons.client;

import cn.yumis.fight.modules.dungeons.client.LucasBossClient.Burst;
import cn.yumis.fight.modules.dungeons.client.LucasBossClient.Hazard;
import cn.yumis.fight.modules.dungeons.content.DragonTowerLucas;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static cn.yumis.fight.modules.dungeons.client.EnemyEffectGeometry.*;

/**
 * 鲁卡斯世界空间技能特效渲染器：
 * 苍蓝紫电 + 亮金雷芯 + 冷青幻影配色，复用 EffectSprites / RibbonTrail / LightningBolt / CameraShake。
 */
public final class LucasBossVisuals {
    private static final ContextKey<Frame> FRAME =
        new ContextKey<>(Identifier.fromNamespaceAndPath("yumis_dungeon_and_fight", "lucas_boss"));

    private record Frame(List<Quad> quads, List<SpriteQuad> sprites, boolean fade) {}
    private static final Set<String> shaken = new HashSet<>();

    private LucasBossVisuals() {}

    public static void register() {
        var events = NeoForge.EVENT_BUS;
        events.addListener(LucasBossVisuals::extract);
        events.addListener(LucasBossVisuals::submit);
    }

    private static void extract(ExtractLevelRenderStateEvent event) {
        var quads = new ArrayList<Quad>();
        var sprites = new ArrayList<SpriteQuad>();
        var mc = Minecraft.getInstance();
        Vec3 camera = event.getRenderState().cameraRenderState == null ? Vec3.ZERO : event.getRenderState().cameraRenderState.pos;
        if (mc.level != null && mc.player != null && ClientUi.current().ready()) {
            long now = ClientUi.current().serverNow();
            shaken.retainAll(LucasBossClient.hazards.keySet());
            for (Hazard h : LucasBossClient.hazards.values()) {
                if (now < h.startAt || now > h.endAt + 420L) continue;
                drawHazard(quads, sprites, camera, h, now);
            }
            for (Burst b : LucasBossClient.bursts) {
                if (now >= b.startAt && now <= b.endAt) {
                    drawBurst(quads, sprites, camera, b, now);
                }
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
        int gold = DragonTowerLucas.COLOR_GOLD_CORE;
        int violet = DragonTowerLucas.COLOR_VIOLET_BOLT;

        switch (h.type) {
            case "FAN_SLASH" -> {
                Vec3 g = h.from.add(0, 0.08, 0);
                Vec3 dir = horizontalDir(h.from, h.to);
                double halfRad = Math.toRadians(h.slashAngle);
                if (!active) {
                    fan(q, g, dir, h.radius, halfRad, h.color, (int) (36 + 66 * warnProg));
                    fan(q, g.add(0, 0.015, 0), dir, h.radius * warnProg, halfRad, gold, 70);
                } else {
                    if (shaken.add(h.id)) CameraShake.impulse(g, 20, 0.22f, 60, 140);
                    double fade = Math.clamp(1.0 - activeProg, 0.0, 1.0);
                    fan(q, g, dir, h.radius, halfRad, gold, (int) (110 * fade));
                    EffectSprites.slashArc(sp, h.from.add(0, 1.25, 0), dir, 10.0, h.radius * 1.15, clampedActive, h.color, 250);
                    EffectSprites.slashArc(sp, h.from.add(0, 1.32, 0), dir, -8.0, h.radius * 0.92, clampedActive, gold, (int) (210 * fade));
                }
            }
            case "INSTANT_LINE" -> {
                Vec3 gFrom = h.from.add(0, 0.09, 0);
                Vec3 gTo = h.to.add(0, 0.09, 0);
                if (!active) {
                    rect(q, gFrom, gTo, h.width, h.color, (int) (36 + 68 * warnProg));
                    double coreW = Math.max(0.18, h.width * (1.0 - warnProg * 0.78));
                    rect(q, gFrom.add(0, 0.015, 0), gTo.add(0, 0.015, 0), coreW, gold, (int) (78 + 115 * warnProg));
                } else {
                    if (shaken.add(h.id)) CameraShake.impulse(gFrom.lerp(gTo, 0.5), 26, 0.34f, 80, 175);
                    double fade = Math.clamp(1.0 - activeProg, 0.0, 1.0);
                    rect(q, gFrom, gTo, h.width, h.color, (int) (135 * fade));
                    EffectSprites.texturedBeam(sp, h.from.add(0, 1.25, 0), h.to.add(0, 1.25, 0), camera, 0.65 * fade + 0.16, now, true, h.color, (int) (245 * fade));
                    LightningBolt.bolt(sp, h.from.add(0, 1.25, 0), h.to.add(0, 1.25, 0), camera, (now / 50L) ^ h.id.hashCode(), 10, 0.45, 0.10 * fade + 0.03, gold, (int) (245 * fade), true);
                }
            }
            case "DASH_LINE" -> {
                Vec3 gFrom = h.from.add(0, 0.08, 0);
                Vec3 gTo = h.to.add(0, 0.08, 0);
                int laneAlpha = active ? (int) (85 * (1.0 - activeProg * 0.65)) : (int) (38 + 68 * warnProg);
                rect(q, gFrom, gTo, h.width, h.color, laneAlpha);
                if (!active) {
                    rect(q, gFrom.add(0, 0.015, 0), gFrom.lerp(gTo, warnProg).add(0, 0.015, 0), h.width * 0.82, gold, 82);
                    EffectSprites.glow(sp, h.from.add(0, 1.2, 0), camera, 0.9 + 0.6 * warnProg, h.color, 210);
                } else {
                    if (shaken.add(h.id)) CameraShake.impulse(gFrom.lerp(gTo, 0.5), 24, 0.28f, 70, 160);
                    Vec3 head = h.from.lerp(h.to, activeProg);
                    Vec3 dir = horizontalDir(h.from, h.to);
                    double fade = Math.clamp(1.0 - Math.max(0.0, activeProg - 0.72) * 3.5, 0.0, 1.0);
                    EffectSprites.texturedBeam(sp, h.from.add(0, 1.15, 0), head.add(0, 1.15, 0), camera, 0.44, now, true, h.color, (int) (235 * fade));
                    LightningBolt.bolt(sp, h.from.add(0, 1.15, 0), head.add(0, 1.15, 0), camera, (now / 55L) ^ h.id.hashCode(), 7, 0.35, 0.08, gold, (int) (220 * fade), false);
                    EffectSprites.slashArc(sp, head.add(0, 1.2, 0), dir, 0.0, h.width * 1.05, clampedActive, h.color, (int) (245 * fade));
                    RibbonTrail.push("lucas_dash:" + h.id, head.add(0, 1.15, 0), h.width * 0.32, h.color, (int) (220 * fade), 240, ClientUi.now());
                }
            }
            case "RING_WAVE" -> {
                Vec3 g = h.from.add(0, 0.08, 0);
                if (!active) {
                    EffectSprites.sigil(sp, g, Math.max(2.2, h.radius * 1.35), now * 0.0035, h.color, (int) (120 + 95 * warnProg), true);
                    ring(q, g, h.radius, h.width * 0.45, h.color, (int) (60 + 120 * warnProg));
                    ring(q, g, h.auxRadius, 0.16, violet, 130);
                } else {
                    if (shaken.add(h.id)) CameraShake.impulse(g, 28, 0.30f, 80, 170);
                    double currR = h.radius + (h.auxRadius - h.radius) * activeProg;
                    double fade = Math.clamp(1.0 - Math.max(0.0, activeProg - 0.78) * 4.5, 0.0, 1.0);
                    EffectSprites.shockwave(sp, g.add(0, 0.12, 0), currR, activeProg * 1.5, h.color, (int) (245 * fade));
                    ring(q, g.add(0, 0.16, 0), currR, h.width * 0.38, gold, (int) (195 * fade));
                    long step = now / 65L;
                    for (int i = 0; i < 6; i++) {
                        double ang = i * Math.PI / 3.0 + step * 0.25;
                        Vec3 base = g.add(Math.cos(ang) * currR, 0.05, Math.sin(ang) * currR);
                        LightningBolt.bolt(sp, base, base.add(Math.cos(ang) * 0.4, 1.85, Math.sin(ang) * 0.4),
                            camera, h.id.hashCode() * 31L + step * 17L + i, 5, 0.28, 0.06, violet, (int) (215 * fade), false);
                    }
                }
            }
            case "CAGE_ZONE" -> {
                Vec3 g = h.from.add(0, 0.06, 0);
                double r = h.radius;
                Vec3 c0 = g.add(-r, 0, -r), c1 = g.add(r, 0, -r), c2 = g.add(r, 0, r), c3 = g.add(-r, 0, r);
                if (!active) {
                    q.add(new Quad(c0, c1, c2, c3, violet, (int) (45 + 65 * warnProg)));
                    EffectSprites.warningCircle(sp, g.add(0, 0.02, 0), r * 1.25, warnProg, h.color, 215);
                    for (Vec3 corner : new Vec3[]{c0, c1, c2, c3}) {
                        double dropY = 8.0 * (1.0 - warnProg * warnProg);
                        EffectSprites.texturedBeam(sp, corner.add(0, dropY, 0), corner.add(0, dropY + 2.8, 0), camera, 0.18, now, false, DragonTowerLucas.COLOR_SKY_BLUE, 225);
                    }
                } else {
                    int cageColor = activeProg > 0.72 ? 0xff627a : (activeProg > 0.42 ? violet : DragonTowerLucas.COLOR_SKY_BLUE);
                    double integrity = Math.clamp(1.0 - h.cageHits / Math.max(1.0, h.auxRadius), 0.35, 1.0);
                    int alpha = (int) (235 * integrity);
                    EffectSprites.warningCircle(sp, g.add(0, 0.02, 0), r * 1.22, activeProg, cageColor, 225);
                    EffectSprites.sigil(sp, g, r * 1.28, now * 0.004, cageColor, (int) (175 * integrity), false);
                    Vec3[] corners = {c0, c1, c2, c3};
                    long step = now / 70L;
                    for (int i = 0; i < 4; i++) {
                        Vec3 cur = corners[i];
                        Vec3 nxt = corners[(i + 1) % 4];
                        // 四角雷光龙枪柱
                        EffectSprites.texturedBeam(sp, cur, cur.add(0, 3.2, 0), camera, 0.22, now, true, cageColor, alpha);
                        EffectSprites.glow(sp, cur.add(0, 3.1, 0), camera, 0.65, gold, alpha);
                        // 栏杆间跃动电弧
                        LightningBolt.bolt(sp, cur.add(0, 0.95, 0), nxt.add(0, 0.95, 0), camera, step * 19L + i * 7L, 5, 0.16, 0.06, gold, alpha, false);
                        LightningBolt.bolt(sp, cur.add(0, 2.15, 0), nxt.add(0, 2.15, 0), camera, step * 23L + i * 11L, 5, 0.18, 0.06, cageColor, alpha, false);
                    }
                }
            }
            case "CAGE_DETONATE" -> {
                Vec3 g = h.from.add(0, 0.08, 0);
                if (shaken.add(h.id)) CameraShake.impulse(g, 28, 0.44f, 100, 220);
                double fade = Math.clamp(1.0 - activeProg, 0.0, 1.0);
                int a = (int) (250 * fade);
                long step = now / 55L;
                LightningBolt.bolt(sp, g.add(0, 16.0, 0), g, camera, h.id.hashCode() * 31L + step * 113L, 12, 1.25, 0.22, violet, a, true);
                LightningBolt.bolt(sp, g.add(0.4, 15.0, -0.3), g, camera, h.id.hashCode() * 17L + step * 97L, 10, 0.95, 0.14, gold, a, true);
                EffectSprites.impactBurst(sp, g.add(0, 0.12, 0), camera, h.radius * 1.45, clampedActive, violet, 245, true);
                EffectSprites.shockwave(sp, g.add(0, 0.14, 0), h.radius * 1.55 * EffectSprites.easeOutCubic(clampedActive), clampedActive * 1.5, gold, a);
                EffectSprites.glow(sp, g.add(0, 0.6, 0), camera, h.radius * 1.1 * fade, gold, a);
            }
            case "SKY_SPEAR" -> {
                Vec3 g = h.from.add(0, 0.07, 0);
                if (!active) {
                    EffectSprites.warningCircle(sp, g, h.radius, warnProg, h.color, 215);
                    EffectSprites.sigil(sp, g.add(0, 12.0, 0), 1.65, now * 0.005, h.color, (int) (205 * warnProg), false);
                    double fallY = 12.0 * (1.0 - warnProg * warnProg * warnProg);
                    EffectSprites.texturedBeam(sp, g.add(0, Math.max(0.1, fallY), 0), g.add(0, Math.max(0.1, fallY) + 3.4, 0),
                        camera, 0.26, now, false, h.color, 240);
                    EffectSprites.glow(sp, g.add(0, Math.max(0.2, fallY), 0), camera, 0.75, gold, 210);
                } else {
                    if (shaken.add(h.id)) CameraShake.impulse(g, 22, 0.24f, 65, 145);
                    double fade = Math.clamp(1.0 - activeProg, 0.0, 1.0);
                    int a = (int) (240 * fade);
                    EffectSprites.texturedBeam(sp, g, g.add(0, 4.2, 0), camera, 0.28 * fade + 0.08, now, true, h.color, a);
                    LightningBolt.bolt(sp, g.add(0, 11.0, 0), g, camera, h.id.hashCode() + (now / 60L) * 41L, 9, 0.85, 0.12, gold, a, true);
                    EffectSprites.shockwave(sp, g.add(0, 0.12, 0), h.radius * 1.2 * EffectSprites.easeOutCubic(clampedActive), clampedActive * 1.4, h.color, a);
                    EffectSprites.impactBurst(sp, g.add(0, 0.35, 0), camera, h.radius * 1.15, clampedActive, h.color, 245, true);
                }
            }
            case "JUMP_SLAM_IMPACT" -> {
                Vec3 land = h.to.add(0, 0.08, 0);
                if (!active) {
                    EffectSprites.warningCircle(sp, land, h.radius, warnProg, violet, 230);
                    EffectSprites.sigil(sp, land, h.radius * 0.88, -now * 0.004, DragonTowerLucas.COLOR_SKY_BLUE, (int) (130 + 95 * warnProg), true);
                    // 跃空俯冲预警弧线
                    Vec3 apex = h.from.lerp(h.to, 0.45).add(0, Math.max(4.5, h.auxRadius), 0);
                    Vec3 cur = quadraticBezier(h.from, apex, h.to, warnProg);
                    EffectSprites.glow(sp, cur.add(0, 1.0, 0), camera, 1.45, gold, 225);
                    RibbonTrail.push("lucas_jump:" + h.id, cur.add(0, 1.0, 0), 0.48, DragonTowerLucas.COLOR_SKY_BLUE, 230, 320, ClientUi.now());
                } else {
                    if (shaken.add(h.id)) CameraShake.impulse(land, 34, 0.48f, 115, 240);
                    double fade = Math.clamp(1.0 - activeProg, 0.0, 1.0);
                    int a = (int) (250 * fade);
                    EffectSprites.impactBurst(sp, land.add(0, 0.25, 0), camera, h.radius * 1.3, clampedActive, violet, 250, true);
                    EffectSprites.shockwave(sp, land.add(0, 0.14, 0), h.radius * 1.35 * EffectSprites.easeOutCubic(clampedActive), clampedActive * 1.6, DragonTowerLucas.COLOR_SKY_BLUE, a);
                    EffectSprites.shockwave(sp, land.add(0, 0.22, 0), h.radius * 0.95 * EffectSprites.easeOutCubic(clampedActive), -clampedActive * 1.4, gold, a);
                    long step = now / 55L;
                    for (int i = 0; i < 6; i++) {
                        double ang = i * Math.PI / 3.0 + 0.3;
                        Vec3 tip = land.add(Math.cos(ang) * h.radius * 0.95, 0.2, Math.sin(ang) * h.radius * 0.95);
                        LightningBolt.bolt(sp, land.add(0, 0.5, 0), tip, camera, h.id.hashCode() + step * 13L + i, 5, 0.35, 0.09, gold, a, false);
                    }
                }
            }
            case "SAFE_BAND_BURST" -> {
                Vec3 g = h.from.add(0, 0.07, 0);
                double safeMin = h.radius;
                double safeMax = h.width;
                double outerMax = h.auxRadius;
                double safeMid = (safeMin + safeMax) * 0.5;
                double safeW = Math.max(0.6, safeMax - safeMin);
                if (!active) {
                    disc(q, g, safeMin, violet, (int) (42 + 72 * warnProg));
                    ring(q, g, (safeMax + outerMax) * 0.5, Math.max(0.6, outerMax - safeMax), violet, (int) (38 + 68 * warnProg));
                    ring(q, g.add(0, 0.02, 0), safeMid, safeW, DragonTowerLucas.COLOR_SAFE_GREEN, (int) (68 + 75 * warnProg));
                    ring(q, g.add(0, 0.035, 0), safeMin, 0.18, 0xa8ffd2, 235);
                    ring(q, g.add(0, 0.035, 0), safeMax, 0.18, 0xa8ffd2, 235);
                    EffectSprites.sigil(sp, g.add(0, 0.04, 0), safeMin * 0.95, now * 0.004, DragonTowerLucas.COLOR_SKY_BLUE, (int) (130 + 95 * warnProg), true);
                } else {
                    if (shaken.add(h.id)) CameraShake.impulse(g, 38, 0.52f, 125, 260);
                    double fade = Math.clamp(1.0 - activeProg, 0.0, 1.0);
                    int a = (int) (245 * fade);
                    disc(q, g, safeMin, DragonTowerLucas.COLOR_SKY_BLUE, (int) (155 * fade));
                    ring(q, g, (safeMax + outerMax) * 0.5, Math.max(0.6, outerMax - safeMax), violet, (int) (145 * fade));
                    EffectSprites.shockwave(sp, g.add(0, 0.2, 0), safeMin * 1.05, clampedActive, gold, a);
                    EffectSprites.shockwave(sp, g.add(0, 0.2, 0), outerMax, -clampedActive, DragonTowerLucas.COLOR_SKY_BLUE, a);
                    long step = now / 55L;
                    // 内圈与外圈双环天雷落柱
                    LightningBolt.bolt(sp, g.add(0, 16.0, 0), g, camera, h.id.hashCode() + step * 37L, 11, 1.2, 0.2, gold, a, true);
                    double outerRingR = (safeMax + outerMax) * 0.5;
                    for (int i = 0; i < 8; i++) {
                        double ang = i * Math.PI / 4.0 + step * 0.15;
                        Vec3 p = g.add(Math.cos(ang) * outerRingR, 0, Math.sin(ang) * outerRingR);
                        LightningBolt.bolt(sp, p.add(0, 14.0, 0), p, camera, h.id.hashCode() + step * 29L + i * 7L, 9, 0.9, 0.14, violet, a, false);
                    }
                }
            }
            default -> {}
        }
    }

    private static void drawBurst(List<Quad> q, List<SpriteQuad> sp, Vec3 camera, Burst b, long now) {
        double p = Math.clamp((now - b.startAt) / (double) Math.max(1L, b.endAt - b.startAt), 0.0, 0.999);
        double fade = 1.0 - p;
        Vec3 g = b.pos.add(0, 0.08, 0);
        switch (b.kind) {
            case "CLONE_SUMMON" -> {
                EffectSprites.sigil(sp, g, 3.0, now * 0.006, b.color, (int) (230 * fade), true);
                LightningBolt.bolt(sp, g.add(0, 15.0, 0), g, camera, b.startAt + (now / 50L) * 19L, 10, 0.95, 0.18, b.color, (int) (245 * fade), true);
                EffectSprites.shockwave(sp, g.add(0, 0.15, 0), 4.2 * EffectSprites.easeOutCubic(p), p * 1.4, DragonTowerLucas.COLOR_VIOLET_BOLT, (int) (220 * fade));
            }
            case "CAGE_BROKEN" -> {
                EffectSprites.impactBurst(sp, g.add(0, 1.0, 0), camera, 3.2, p, b.color, 240, false);
                EffectSprites.shockwave(sp, g.add(0, 0.15, 0), 3.6 * EffectSprites.easeOutCubic(p), p * 1.5, DragonTowerLucas.COLOR_SKY_BLUE, (int) (220 * fade));
            }
            default -> {
                EffectSprites.impactBurst(sp, g.add(0, 1.1, 0), camera, 1.8, p, b.color, 220, false);
            }
        }
    }

    private static Vec3 quadraticBezier(Vec3 a, Vec3 b, Vec3 c, double t) {
        double u = 1.0 - t;
        return a.scale(u * u).add(b.scale(2.0 * u * t)).add(c.scale(t * t));
    }

    private static Vec3 horizontalDir(Vec3 from, Vec3 to) {
        Vec3 d = new Vec3(to.x - from.x, 0, to.z - from.z);
        return d.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : d.normalize();
    }

    private static void submit(SubmitCustomGeometryEvent event) {
        Frame frame = event.getLevelRenderState().getRenderData(FRAME);
        if (frame == null || event.getLevelRenderState().cameraRenderState == null) return;
        WorldQuads.submit(event, frame.quads(), frame.fade());
        EffectSprites.submit(event, frame.sprites(), frame.fade());
    }
}
