package cn.yumis.fight.modules.dungeons.client;

import cn.yumis.fight.modules.dungeons.client.FrostnovaBossClient.AreaHazard;
import cn.yumis.fight.modules.dungeons.client.FrostnovaBossClient.ReviveVisual;
import cn.yumis.fight.modules.dungeons.client.FrostnovaBossClient.Spike;
import cn.yumis.fight.modules.dungeons.content.FrostTowerFrostnova;
import cn.yumis.fight.modules.presentation.api.client.CameraShake;
import cn.yumis.fight.modules.presentation.api.client.ClientUi;
import cn.yumis.fight.modules.presentation.api.client.EffectSprites;
import cn.yumis.fight.modules.presentation.api.client.EffectSprites.SpriteQuad;
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
 * 霜塔第 20 层首领「霜星」世界空间冰系特效渲染器：
 * - 3D 菱形冰刺（背后凝结冰晶羽翼扇阵、扇形直线冰锥、环形冰刺与螺旋弹幕）；
 * - 爆发蓄力收缩环与爆裂霜晶波；
 * - 寒霜领域六角雪阵与共振引爆冰花；
 * - 转阶段倒下→浮空汇聚双螺旋冰带→落地冲击大阵；
 * - 二阶段大招极寒星爆巨型冰锥坠落。
 */
public final class FrostnovaBossVisuals {
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final ContextKey<Frame> FRAME =
        new ContextKey<>(Identifier.fromNamespaceAndPath("yumis_dungeon_and_fight", "frostnova_boss"));

    private record Frame(List<Quad> quads, List<SpriteQuad> sprites, boolean fade) {}
    private static final java.util.Set<String> shaken = new java.util.HashSet<>();

    private FrostnovaBossVisuals() {}

    public static void register() {
        var events = NeoForge.EVENT_BUS;
        events.addListener(FrostnovaBossVisuals::extract);
        events.addListener(FrostnovaBossVisuals::submit);
    }

    private static void extract(ExtractLevelRenderStateEvent event) {
        var quads = new ArrayList<Quad>();
        var sprites = new ArrayList<SpriteQuad>();
        var mc = Minecraft.getInstance();
        Vec3 camera = event.getRenderState().cameraRenderState == null ? Vec3.ZERO : event.getRenderState().cameraRenderState.pos;
        if (mc.level != null && mc.player != null && ClientUi.current().ready()) {
            long now = ClientUi.current().serverNow();
            shaken.retainAll(FrostnovaBossClient.areas.keySet());
            ReviveVisual revive = FrostnovaBossClient.reviveVisual;
            if (revive != null && now >= revive.startAt && now <= revive.endAt + 300L) {
                drawRevive(quads, sprites, camera, revive, now);
            }
            for (AreaHazard a : FrostnovaBossClient.areas.values()) {
                if (now < a.startAt || now > a.endAt + 350L) continue;
                drawArea(quads, sprites, camera, a, now);
            }
            for (Spike s : FrostnovaBossClient.spikes.values()) {
                if (s.consumed || now < s.startAt || now > s.endAt) continue;
                drawSpike(quads, sprites, camera, s, now);
            }
        }
        event.getRenderState().setRenderData(FRAME, new Frame(List.copyOf(quads), List.copyOf(sprites), NearEffectRendering.enabled(event.getCamera())));
    }

    private static void drawSpike(List<Quad> q, List<SpriteQuad> sp, Vec3 camera, Spike s, long now) {
        boolean launched = now >= s.launchAt;
        double scale;
        int alpha;
        if (!launched) {
            double form = Math.clamp((now - s.startAt) / 350.0, 0.0, 1.0);
            scale = 0.55 + 0.45 * form;
            long untilLaunch = s.launchAt - now;
            alpha = untilLaunch < 180L ? 245 : (int) (135 + 75 * form);
        } else {
            double fade = Math.clamp((s.endAt - now) / 350.0, 0.0, 1.0);
            scale = 1.05;
            alpha = (int) (235 * fade);
        }
        if (alpha <= 5) return;

        double len = (s.phase2 ? 1.45 : 1.25) * scale;
        double rad = (s.phase2 ? 0.20 : 0.17) * scale;
        int outerColor = (!launched && s.launchAt - now < 180L) ? 0xe8fdff : s.color;
        drawIceShard(q, s.pos, s.dir, len, rad, outerColor, 0xffffff, alpha);
        EffectSprites.glow(sp, s.pos, camera, rad * 2.8, outerColor, (int) (alpha * 0.75));
        if (launched) {
            RibbonTrail.push("frostspike:" + s.id, s.pos, rad * 1.15, 0x9ceeff, (int) (alpha * 0.85), 220, ClientUi.now());
        }
    }

    private static void drawArea(List<Quad> q, List<SpriteQuad> sp, Vec3 camera, AreaHazard a, long now) {
        boolean active = now >= a.warnUntil;
        double warnProg = a.warnUntil <= a.startAt ? 1.0
            : Math.clamp((now - a.startAt) / (double) (a.warnUntil - a.startAt), 0.0, 1.0);
        double activeProg = a.endAt <= a.warnUntil ? 1.0
            : Math.clamp((now - a.warnUntil) / (double) (a.endAt - a.warnUntil), 0.0, 1.0);
        double clampedActive = Math.clamp(activeProg, 0.0, 0.999);
        Vec3 g = a.from.add(0, 0.07, 0);

        switch (a.type) {
            case "FROST_BURST" -> {
                if (!active) {
                    EffectSprites.warningCircle(sp, g, a.radius, warnProg, a.color, 215);
                    ring(q, g.add(0, 0.02, 0), a.radius * (1.0 - warnProg * 0.85), 0.16, 0xffffff, 220);
                    drawSnowflakeWeb(q, g.add(0, 0.04, 0), a.radius * 0.92, now * 0.0012, 0xc8f6ff, (int) (130 + 95 * warnProg));
                } else {
                    if (shaken.add(a.id)) CameraShake.impulse(g, 28, a.phase2 ? 0.45f : 0.30f, 100, 220);
                    double fade = Math.clamp(1.0 - activeProg, 0.0, 1.0);
                    EffectSprites.shockwave(sp, g.add(0, 0.10, 0), a.radius * 1.15 * EffectSprites.easeOutCubic(clampedActive), clampedActive * 1.4, 0xc8f7ff, (int) (245 * fade));
                    EffectSprites.impactBurst(sp, g.add(0, 0.45, 0), camera, a.radius * 1.1, clampedActive, a.color, 245, false);
                    int rays = a.phase2 ? 12 : 8;
                    for (int i = 0; i < rays; i++) {
                        double ang = i * (Math.PI * 2.0 / rays);
                        Vec3 base = g.add(Math.cos(ang) * a.radius * (0.35 + 0.55 * activeProg), 0.1, Math.sin(ang) * a.radius * (0.35 + 0.55 * activeProg));
                        Vec3 dir = new Vec3(Math.cos(ang) * 0.45, 0.9, Math.sin(ang) * 0.45).normalize();
                        drawIceShard(q, base.add(dir.scale(1.6)), dir, 2.2, 0.26, a.color, 0xffffff, (int) (230 * fade));
                    }
                }
            }
            case "REVIVE_SHOCKWAVE" -> {
                if (shaken.add(a.id)) CameraShake.impulse(g, 36, 0.55f, 160, 300);
                double fade = Math.clamp(1.0 - activeProg, 0.0, 1.0);
                double r = a.radius * (0.18 + 0.92 * EffectSprites.easeOutCubic(clampedActive));
                EffectSprites.shockwave(sp, g.add(0, 0.12, 0), r, clampedActive * 1.6, 0xcdf8ff, (int) (250 * fade));
                EffectSprites.impactBurst(sp, g.add(0, 0.5, 0), camera, r * 0.85, clampedActive, 0x86e8ff, 240, true);
            }
            case "FROST_FIELD" -> {
                if (a.detonateAt > 0L) {
                    if (now < a.detonateAt) {
                        double detWarn = Math.clamp(1.0 - (a.detonateAt - now) / (double) FrostTowerFrostnova.SKILL4_DETONATE_DELAY_MS, 0.0, 1.0);
                        EffectSprites.warningCircle(sp, g, a.radius, detWarn, 0xd4fbff, 235);
                    } else {
                        if (shaken.add(a.id)) CameraShake.impulse(g, 24, 0.28f, 70, 160);
                        double detProg = Math.clamp((now - a.detonateAt) / 420.0, 0.0, 0.999);
                        double detFade = 1.0 - detProg;
                        EffectSprites.shockwave(sp, g.add(0, 0.15, 0), a.radius * 1.2 * EffectSprites.easeOutCubic(detProg), detProg * 1.3, 0xffffff, (int) (245 * detFade));
                        EffectSprites.impactBurst(sp, g.add(0, 0.6, 0), camera, a.radius * 1.15, detProg, 0xbef6ff, 245, false);
                        drawIceShard(q, g.add(0, 2.6, 0), UP, 3.0, 0.48, 0xbef6ff, 0xffffff, (int) (230 * detFade));
                    }
                    return;
                }
                if (!active) {
                    EffectSprites.warningCircle(sp, g, a.radius, warnProg, a.color, 200);
                } else {
                    double lifeFade = Math.clamp((a.endAt - now) / 700.0, 0.0, 1.0);
                    EffectSprites.sigil(sp, g, a.radius * 1.02, now * 0.0007, a.color, (int) (200 * lifeFade), true);
                    drawSnowflakeWeb(q, g.add(0, 0.03, 0), a.radius * 0.92, now * 0.0005, 0xe8fdff, (int) (165 * lifeFade));
                }
            }
            case "BLINK_CRYSTAL" -> {
                Vec3 core = a.from.add(0, 1.2, 0);
                EffectSprites.texturedBeam(sp, a.from.add(0, 1.0, 0), a.to.add(0, 1.0, 0), camera, 0.22, now, true, 0xb0f2ff, active ? 95 : 190);
                if (!active) {
                    double pulse = 0.7 + 0.45 * warnProg;
                    drawIceShard(q, core.add(0, 0.9 * pulse, 0), UP, 1.7 * pulse, 0.36 * pulse, a.color, 0xffffff, 225);
                    EffectSprites.warningCircle(sp, g, a.radius, warnProg, 0xd8fbff, 200);
                } else {
                    double fade = Math.clamp(1.0 - activeProg, 0.0, 1.0);
                    EffectSprites.shockwave(sp, g.add(0, 0.14, 0), 2.8 * EffectSprites.easeOutCubic(clampedActive), clampedActive, 0xffffff, (int) (235 * fade));
                }
            }
            case "NOVA_ICICLE" -> {
                if (!active) {
                    EffectSprites.warningCircle(sp, g, a.radius, warnProg, a.color, 220);
                    double fallY = 14.0 * (1.0 - warnProg * warnProg * warnProg);
                    Vec3 tipPos = a.from.add(0, Math.max(0.2, fallY), 0);
                    drawIceShard(q, tipPos, new Vec3(0, -1, 0), 3.6, 0.55, 0x9aeaff, 0xffffff, (int) (160 + 85 * warnProg));
                    EffectSprites.glow(sp, tipPos.add(0, 1.8, 0), camera, 1.8, 0xc8f6ff, 215);
                } else {
                    if (shaken.add(a.id)) CameraShake.impulse(g, 28, 0.34f, 80, 180);
                    double fade = Math.clamp(1.0 - activeProg, 0.0, 1.0);
                    EffectSprites.shockwave(sp, g.add(0, 0.12, 0), a.radius * 1.2 * EffectSprites.easeOutCubic(clampedActive), clampedActive * 1.3, 0xffffff, (int) (245 * fade));
                    EffectSprites.impactBurst(sp, g.add(0, 0.5, 0), camera, a.radius * 1.15, clampedActive, 0x9aeaff, 245, false);
                    drawIceShard(q, a.from.add(0, 0.2, 0), new Vec3(0, -1, 0), 3.2, 0.52, 0xbef5ff, 0xffffff, (int) (215 * fade));
                }
            }
            default -> {}
        }
    }

    private static void drawRevive(List<Quad> q, List<SpriteQuad> sp, Vec3 camera, ReviveVisual revive, long now) {
        long elapsed = now - revive.startAt;
        double prog = Math.clamp(elapsed / (double) Math.max(1L, revive.endAt - revive.startAt), 0.0, 1.0);
        Vec3 g = revive.anchor.add(0, 0.08, 0);
        double yOff = FrostTowerFrostnova.reviveHeightOffset(elapsed);
        double ringRadius = 3.2 + 7.2 * prog;

        EffectSprites.sigil(sp, g, ringRadius, now * 0.0014, 0x9ceeff, 225, true);
        drawSnowflakeWeb(q, g.add(0, 0.03, 0), ringRadius * 0.9, now * 0.0018, 0xe8fdff, 200);

        if (elapsed >= FrostTowerFrostnova.REVIVE_FALL_END_MS) {
            double spin = now * 0.009;
            for (int strand = 0; strand < 2; strand++) {
                double baseAngle = spin + strand * Math.PI;
                List<Vec3> pts = new ArrayList<>(12);
                for (int seg = 0; seg <= 10; seg++) {
                    double f = seg / 10.0;
                    double a = baseAngle + f * Math.PI * 2.4;
                    double r = 2.6 * (1.0 - f * 0.55);
                    double y = 0.2 + f * (yOff + 2.2);
                    pts.add(revive.anchor.add(Math.cos(a) * r, y, Math.sin(a) * r));
                }
                RibbonTrail.ribbon(sp, pts, camera, 0.24, strand == 0 ? 0xa8f2ff : 0xffffff, 235);
            }
            EffectSprites.glow(sp, revive.anchor.add(0, yOff + 1.0, 0), camera, 2.2, 0xcff8ff, 225);
        }
    }

    /** 绘制沿 dir 朝向尖锐拉长的 3D 四棱冰晶刺（尖端位于 tip）。 */
    private static void drawIceShard(List<Quad> out, Vec3 tip, Vec3 direction, double length, double radius,
                                     int outerColor, int coreColor, int alpha) {
        if (alpha <= 0 || direction.lengthSqr() < 1e-6) return;
        Vec3 fwd = direction.normalize();
        Vec3 ref = Math.abs(fwd.y) > 0.92 ? new Vec3(1, 0, 0) : UP;
        Vec3 right = fwd.cross(ref).normalize().scale(radius);
        Vec3 up = right.cross(fwd).normalize().scale(radius);

        Vec3 mid = tip.subtract(fwd.scale(length * 0.72));
        Vec3 tail = tip.subtract(fwd.scale(length));
        Vec3[] ring = {
            mid.add(right),
            mid.add(up),
            mid.subtract(right),
            mid.subtract(up)
        };
        int coreAlpha = Math.clamp(alpha + 20, 0, 255);
        for (int i = 0; i < 4; i++) {
            Vec3 a = ring[i];
            Vec3 b = ring[(i + 1) % 4];
            // 前锥面（尖端）
            out.add(new Quad(tip, a, b, tip, i % 2 == 0 ? outerColor : coreColor, coreAlpha));
            // 后锥面（尾端）
            out.add(new Quad(tail, b, a, tail, outerColor, (int) (alpha * 0.85)));
        }
    }

    /** 绘制地面六角雪花冰阵纹路。 */
    private static void drawSnowflakeWeb(List<Quad> out, Vec3 center, double radius, double angleOffset, int color, int alpha) {
        for (int i = 0; i < 6; i++) {
            double a = angleOffset + i * Math.PI / 3.0;
            Vec3 end = center.add(Math.cos(a) * radius, 0, Math.sin(a) * radius);
            line(out, center, end, 0.08, color, alpha);
            // 外侧雪花分叉
            Vec3 branchBase = center.add(Math.cos(a) * radius * 0.62, 0, Math.sin(a) * radius * 0.62);
            for (double side : new double[]{-0.55, 0.55}) {
                double ba = a + side;
                Vec3 branchEnd = branchBase.add(Math.cos(ba) * radius * 0.26, 0, Math.sin(ba) * radius * 0.26);
                line(out, branchBase, branchEnd, 0.06, 0xffffff, alpha);
            }
        }
    }

    private static void submit(SubmitCustomGeometryEvent event) {
        Frame frame = event.getLevelRenderState().getRenderData(FRAME);
        if (frame == null || event.getLevelRenderState().cameraRenderState == null) return;
        WorldQuads.submit(event, frame.quads(), frame.fade());
        EffectSprites.submit(event, frame.sprites(), frame.fade());
    }
}
