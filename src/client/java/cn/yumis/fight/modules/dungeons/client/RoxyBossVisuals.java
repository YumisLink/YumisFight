package cn.yumis.fight.modules.dungeons.client;

import cn.yumis.fight.modules.dungeons.client.RoxyBossClient.AreaHazard;
import cn.yumis.fight.modules.dungeons.client.RoxyBossClient.HydroCoreVisual;
import cn.yumis.fight.modules.dungeons.client.RoxyBossClient.OutsideZapVisual;
import cn.yumis.fight.modules.dungeons.client.RoxyBossClient.TransitionVisual;
import cn.yumis.fight.modules.dungeons.client.RoxyBossClient.TyphoonEyeVisual;
import cn.yumis.fight.modules.dungeons.client.RoxyBossClient.WaterProjectile;
import cn.yumis.fight.modules.dungeons.content.SluiceTowerRoxy;
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
 * 水闸塔第 20 层专属首领「洛琪希」世界空间史诗特效渲染器：
 * - 天顶巨型《豪雷积雨云》旋转双层雷云大法阵、风暴云环与高空跃动雷弧；
 * - 12 秒转场四角蓄水池冲天四道螺旋水龙柱直贯天穹 + 3 枚水魔导核心能量导流光束与碎裂冲击波；
 * - 二阶段大招《天穹雷狱·台风眼》巨型外圈旋转水龙卷风壁 + 平滑移动晴空通天光柱安全区 + 眼外天雷直劈；
 * - 洛琪希二阶段周身环绕魔导水环与杖尖蓝宝石星芒辉光；
 * - 全 7 招水雷法术的清晰高对比度预警与华丽水雷打击特效。
 */
public final class RoxyBossVisuals {
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final ContextKey<Frame> FRAME =
        new ContextKey<>(Identifier.fromNamespaceAndPath("yumis_dungeon_and_fight", "roxy_boss"));

    private record Frame(List<Quad> quads, List<SpriteQuad> sprites, boolean fade) {}
    private static final java.util.Set<String> shaken = new java.util.HashSet<>();

    private RoxyBossVisuals() {}

    public static void register() {
        var events = NeoForge.EVENT_BUS;
        events.addListener(RoxyBossVisuals::extract);
        events.addListener(RoxyBossVisuals::submit);
    }

    private static void extract(ExtractLevelRenderStateEvent event) {
        var quads = new ArrayList<Quad>();
        var sprites = new ArrayList<SpriteQuad>();
        var mc = Minecraft.getInstance();
        Vec3 camera = event.getRenderState().cameraRenderState == null ? Vec3.ZERO : event.getRenderState().cameraRenderState.pos;
        if (mc.level != null && mc.player != null && ClientUi.current().ready()) {
            long now = ClientUi.current().serverNow();
            shaken.retainAll(RoxyBossClient.areas.keySet());

            // 1. 天顶巨型《豪雷积雨云》奇观（转场渐显 -> 二阶段常驻 -> 台风眼期间下压）
            if (RoxyBossClient.stormStrength > 0.05 && RoxyBossClient.stormCenter.lengthSqr() > 1.0) {
                boolean typhoonLowered = RoxyBossClient.typhoonEye != null && now >= RoxyBossClient.typhoonEye.startAt;
                drawCumulonimbusSky(quads, sprites, camera, RoxyBossClient.stormCenter, RoxyBossClient.stormStrength, typhoonLowered, now);
            }

            // 2. 12 秒《豪雷积雨云》转场奇观（四角水池冲天四道水龙柱 + 升空咏唱大阵）
            TransitionVisual trans = RoxyBossClient.transitionVisual;
            if (trans != null && now >= trans.startAt && now <= trans.endAt + 350L) {
                drawTransitionSpectacle(quads, sprites, camera, trans, now);
            }

            // 3. 3 枚水魔导核心及其向高空洛琪希灌注的能量光柱
            if (!RoxyBossClient.hydroCores.isEmpty()) {
                Vec3 bossPos = trans != null
                    ? trans.center.add(0, SluiceTowerRoxy.transitionHeightOffset(now - trans.startAt) + 1.2, 0)
                    : RoxyBossClient.resolveBossPos(RoxyBossClient.activeBossEntityId, RoxyBossClient.stormCenter).add(0, 1.5, 0);
                for (HydroCoreVisual core : RoxyBossClient.hydroCores.values()) {
                    drawHydroCore(quads, sprites, camera, core, bossPos, now);
                }
            }

            // 4. 二阶段洛琪希周身魔导水雷光环
            if (RoxyBossClient.isStormActive() && RoxyBossClient.activeBossEntityId >= 0) {
                Vec3 bPos = RoxyBossClient.resolveBossPos(RoxyBossClient.activeBossEntityId, Vec3.ZERO);
                if (bPos.lengthSqr() > 1.0) {
                    drawBossStormAura(quads, sprites, camera, bPos, now);
                }
            }

            // 5. 二阶段终极大招《天穹雷狱·台风眼》外圈巨型水龙卷 + 晴空通天安全眼
            TyphoonEyeVisual eye = RoxyBossClient.typhoonEye;
            if (eye != null && now >= eye.startAt && now <= eye.endAt + 350L) {
                drawTyphoonEye(quads, sprites, camera, eye, now);
            }

            // 6. 眼外天雷劈击玩家即时视觉
            for (OutsideZapVisual zap : RoxyBossClient.outsideZaps) {
                if (now >= zap.startAt && now <= zap.endAt) {
                    double p = Math.clamp((now - zap.startAt) / (double) Math.max(1L, zap.endAt - zap.startAt), 0.0, 0.999);
                    double fade = 1.0 - p;
                    LightningBolt.bolt(spOr(sprites), zap.pos.add(0, 18.0, 0), zap.pos.add(0, 0.2, 0), camera,
                        (now / 40L) ^ (long) zap.pos.x, 12, 0.85, 0.18 * fade + 0.05, 0x9cf2ff, (int) (255 * fade), true);
                    EffectSprites.impactBurst(sprites, zap.pos.add(0, 0.4, 0), camera, 2.2, p, 0x80e8ff, (int) (235 * fade), false);
                }
            }

            // 7. 所有地面/空间招式区域特效
            for (AreaHazard a : RoxyBossClient.areas.values()) {
                if (now < a.startAt || now > a.endAt + 350L) continue;
                drawArea(quads, sprites, camera, a, now);
            }

            // 8. 水圣弹幕与水镜水箭
            for (WaterProjectile p : RoxyBossClient.projectiles.values()) {
                if (p.consumed || now < p.startAt || now > p.endAt) continue;
                drawWaterProjectile(quads, sprites, camera, p, now);
            }
        }
        event.getRenderState().setRenderData(FRAME, new Frame(List.copyOf(quads), List.copyOf(sprites), NearEffectRendering.enabled(event.getCamera())));
    }

    private static List<SpriteQuad> spOr(List<SpriteQuad> sp) {
        return sp;
    }

    /** 天顶巨型《豪雷积雨云》旋转涡盘：双层反旋魔法阵 + 暗蓝风暴云带 + 云层跃动雷弧。 */
    private static void drawCumulonimbusSky(List<Quad> q, List<SpriteQuad> sp, Vec3 camera,
                                            Vec3 center, double strength, boolean lowered, long now) {
        double altitude = lowered ? 16.5 : 25.0;
        Vec3 sky = center.add(0, altitude, 0);
        double pulse = 0.5 + 0.5 * Math.sin(now * 0.0032);
        int baseAlpha = (int) Math.round(175 * strength);

        // 巨大暗蓝云涡底部环带（营造积雨云压顶质感）
        annulus(q, sky.add(0, 1.4, 0), 8.0, 26.0, 0x10223c, (int) Math.round(108 * strength));
        annulus(q, sky.add(0, 0.7, 0), 14.0, 34.0, 0x0c1930, (int) Math.round(84 * strength));
        ring(q, sky.add(0, 0.2, 0), 21.0 + pulse * 1.4, 0.65, 0x4fb8f0, (int) Math.round(150 * strength));

        // 天顶双层反旋水雷大魔法阵
        EffectSprites.sigil(sp, sky, 21.5 + pulse * 1.6, now * 0.0009, 0x4cc4ff, baseAlpha, true);
        EffectSprites.sigil(sp, sky.add(0, -1.6, 0), 13.5, -now * 0.0015, 0xa8f2ff, (int) Math.round(165 * strength), false);

        // 云层内部周期跃动的苍白电弧（3 道沿云盘环绕闪烁）
        long flashBucket = now / 180L;
        for (int i = 0; i < 3; i++) {
            double a0 = ((flashBucket * 1.37 + i * 2.1) % (Math.PI * 2.0));
            double a1 = a0 + 0.95;
            Vec3 p0 = sky.add(Math.cos(a0) * 16.0, -0.5, Math.sin(a0) * 16.0);
            Vec3 p1 = sky.add(Math.cos(a1) * 19.0, -1.2, Math.sin(a1) * 19.0);
            LightningBolt.bolt(sp, p0, p1, camera, flashBucket ^ (i * 997L), 8, 1.35, 0.14, 0x9cf0ff,
                (int) Math.round(190 * strength), false);
        }
    }

    /** 12 秒转场奇观：四角水池冲天四道巨型水龙柱灌入天顶积雨云 + 升空咏唱圣环。 */
    private static void drawTransitionSpectacle(List<Quad> q, List<SpriteQuad> sp, Vec3 camera,
                                                TransitionVisual t, long now) {
        long elapsed = now - t.startAt;
        double prog = Math.clamp(elapsed / (double) Math.max(1L, t.endAt - t.startAt), 0.0, 1.0);
        double yOff = SluiceTowerRoxy.transitionHeightOffset(elapsed);
        Vec3 bossHover = t.center.add(0, yOff + 1.1, 0);

        // 地面中心扩散水系大法阵
        EffectSprites.sigil(sp, t.center.add(0, 0.09, 0), 4.8 + 7.5 * prog, now * 0.0016, 0x5ccdff, 220, true);
        if (elapsed >= SluiceTowerRoxy.TRANSITION_KNEEL_END_MS) {
            double riseProg = Math.clamp((elapsed - SluiceTowerRoxy.TRANSITION_KNEEL_END_MS) / 1_800.0, 0.0, 1.0);
            // 洛琪希脚下升空水龙卷光柱 + 环绕魔力光球
            EffectSprites.texturedBeam(sp, t.center.add(0, 0.1, 0), bossHover.add(0, 1.4, 0), camera,
                0.55 + 0.25 * prog, now, true, 0x6cd8ff, (int) (215 * riseProg));
            EffectSprites.glow(sp, bossHover, camera, 2.4 + 0.9 * prog, 0xa8f2ff, (int) (225 * riseProg));
            ring(q, bossHover.add(0, -0.7, 0), 2.8 + 0.6 * Math.sin(now * 0.006), 0.16, 0x9cf2ff, (int) (215 * riseProg));

            // 四角蓄水池冲天喷出 4 道粗壮螺旋水柱直贯天顶积雨云！
            double columnTopY = 26.0 * riseProg;
            for (int i = 0; i < t.pools.size(); i++) {
                Vec3 pool = t.pools.get(i);
                Vec3 top = pool.add(0, Math.max(1.5, columnTopY), 0);
                EffectSprites.texturedBeam(sp, pool, top, camera, 1.05 * riseProg, now + i * 90L, true, 0x4ec4ff, (int) (225 * riseProg));
                // 螺旋上升的水雾丝带包裹水柱
                List<Vec3> helix = new ArrayList<>(18);
                double spin = now * 0.006 + i * Math.PI * 0.5;
                for (int s = 0; s <= 16; s++) {
                    double f = s / 16.0;
                    double ang = spin + f * Math.PI * 4.0;
                    double rad = 1.9 + 0.7 * f;
                    helix.add(pool.add(Math.cos(ang) * rad, f * columnTopY, Math.sin(ang) * rad));
                }
                RibbonTrail.ribbon(sp, helix, camera, 0.34, 0x9ceeff, (int) (210 * riseProg));
                EffectSprites.sigil(sp, pool.add(0, 0.65, 0), 4.6, now * 0.002 + i, 0x62d4ff, (int) (200 * riseProg), false);
            }
        }
    }

    /** 绘制 3 枚可破坏「水魔导核心」的旋转水阵、菱形光盾与直连高空洛琪希的魔力导流光束。 */
    private static void drawHydroCore(List<Quad> q, List<SpriteQuad> sp, Vec3 camera,
                                      HydroCoreVisual core, Vec3 bossHoverPos, long now) {
        Vec3 base = core.pos.add(0, 0.08, 0);
        if (core.broken) {
            long since = now - core.brokenAt;
            if (since >= 0 && since < 550L) {
                double p = Math.clamp(since / 550.0, 0.0, 0.999);
                double fade = 1.0 - p;
                EffectSprites.shockwave(sp, base, 5.2 * EffectSprites.easeOutCubic(p), p * 1.4, 0xbef6ff, (int) (245 * fade));
                EffectSprites.impactBurst(sp, core.pos.add(0, 1.1, 0), camera, 3.8, p, 0x72e2ff, (int) (240 * fade), false);
            }
            return;
        }
        double pulse = 0.5 + 0.5 * Math.sin(now * 0.010);
        Vec3 coreCenter = core.pos.add(0, 1.25 + 0.16 * pulse, 0);
        EffectSprites.sigil(sp, base, 2.25, now * 0.003, 0x68e2ff, 235, true);
        ring(q, base.add(0, 0.18, 0), 2.1, 0.16, 0xffe484, 210);
        // 耀眼棱晶发光体 + 直通高空洛琪希的魔力充能光束
        drawWaterShard(q, coreCenter.add(0, 0.95, 0), UP, 1.9, 0.52, 0x4cc4ff, 0xe8fdff, 230);
        EffectSprites.glow(sp, coreCenter, camera, 1.7 + 0.35 * pulse, 0x9cf2ff, 230);
        EffectSprites.texturedBeam(sp, coreCenter, bossHoverPos, camera, 0.24, now, true, 0x72e2ff, 225);
        LightningBolt.helixArcs(sp, coreCenter, bossHoverPos, camera, now, 0.55, 1, 0xd4fbff, 200);
    }

    /** 二阶段洛琪希周身环绕魔导水环与飘浮水雷微粒。 */
    private static void drawBossStormAura(List<Quad> q, List<SpriteQuad> sp, Vec3 camera, Vec3 bossPos, long now) {
        double spin = now * 0.0028;
        EffectSprites.sigil(sp, bossPos.add(0, 0.08, 0), 1.95, spin, 0x58ceff, 155, false);
        for (int i = 0; i < 3; i++) {
            double ang = spin * 1.6 + i * (Math.PI * 2.0 / 3.0);
            Vec3 orb = bossPos.add(Math.cos(ang) * 1.25, 1.15 + Math.sin(spin * 2.2 + i) * 0.28, Math.sin(ang) * 1.25);
            EffectSprites.glow(sp, orb, camera, 0.42, i == 0 ? 0xc8f8ff : 0x5ccdff, 210);
        }
    }

    /** 二阶段史诗循环大招《天穹雷狱·台风眼》：外圈巨型水龙卷风壁 + 晴空通天移动安全区！ */
    private static void drawTyphoonEye(List<Quad> q, List<SpriteQuad> sp, Vec3 camera,
                                       TyphoonEyeVisual eye, long now) {
        boolean active = now >= eye.activeAt;
        double warnProg = Math.clamp((now - eye.startAt) / (double) Math.max(1L, eye.activeAt - eye.startAt), 0.0, 1.0);
        double fade = now <= eye.endAt ? 1.0 : Math.clamp((eye.endAt + 350L - now) / 350.0, 0.0, 1.0);
        Vec3 eyeCenter = eye.currentEyeCenter(now);
        Vec3 eg = eyeCenter.add(0, 0.10, 0);

        // 1. 晴空移动“台风眼”安全区（极高辨识度：通天月白青金光柱 + 双层宁静水环）
        double r = eye.eyeRadius;
        EffectSprites.sigil(sp, eg, r * 1.04, now * 0.0018, 0x72f2ff, (int) (235 * fade), true);
        ring(q, eg.add(0, 0.04, 0), r, 0.24, 0xfff2b8, (int) (240 * fade));
        ring(q, eg.add(0, 1.5, 0), r * 0.94, 0.15, 0x9cf6ff, (int) (185 * fade));
        ring(q, eg.add(0, 3.2, 0), r * 0.88, 0.12, 0xd8fbff, (int) (135 * fade));
        // 破云通天晴空光柱，在暴雨中一眼定位安全区！
        EffectSprites.texturedBeam(sp, eg, eg.add(0, 25.0, 0), camera, 0.82, now, false, 0xd8fbff, (int) (185 * fade));
        EffectSprites.glow(sp, eg.add(0, 1.2, 0), camera, 2.2, 0xfff6d2, (int) (190 * fade));

        // 2. 整座水闸塔外圈巨型旋转水龙卷风壁（6 道高速盘旋水龙丝带 + 风暴底环）
        double intensity = active ? fade : warnProg * 0.65;
        double spin = now * (active ? 0.0052 : 0.0024) * (eye.sweepSign < 0 ? -1.0 : 1.0);
        for (int strand = 0; strand < 6; strand++) {
            List<Vec3> wall = new ArrayList<>(38);
            double baseAng = spin + strand * (Math.PI * 2.0 / 6.0);
            for (int seg = 0; seg <= 36; seg++) {
                double f = seg / 36.0;
                double ang = baseAng + f * Math.PI * 2.6 * (eye.sweepSign < 0 ? -1.0 : 1.0);
                double rad = 22.0 + 13.0 * f;
                double h = 0.5 + f * 18.5;
                wall.add(eye.center.add(Math.cos(ang) * rad, h, Math.sin(ang) * rad));
            }
            int col = strand % 2 == 0 ? 0x3daee8 : 0x8ae8ff;
            RibbonTrail.ribbon(sp, wall, camera, 1.05, col, (int) Math.round(175 * intensity));
        }

        // 3. 激活期间外围持续肆虐的背景巨型天雷（视觉压迫感拉满）
        if (active) {
            long tickSlot = now / 220L;
            for (int i = 0; i < 4; i++) {
                double ang = ((tickSlot * 1.9 + i * 1.57) % (Math.PI * 2.0));
                double dist = 16.0 + ((tickSlot * 7 + i * 5) % 14);
                Vec3 groundPt = eye.center.add(Math.cos(ang) * dist, 0.1, Math.sin(ang) * dist);
                // 避免背景装饰雷落在台风眼正中央混淆视线
                if (groundPt.subtract(eyeCenter).horizontalDistance() < r + 2.5) continue;
                LightningBolt.bolt(sp, groundPt.add(0, 17.5, 0), groundPt, camera,
                    tickSlot ^ (i * 313L), 10, 1.15, 0.16, 0x8ceeff, (int) (215 * fade), true);
            }
        }
    }

    private static void drawWaterProjectile(List<Quad> q, List<SpriteQuad> sp, Vec3 camera,
                                            WaterProjectile p, long now) {
        boolean launched = now >= p.launchAt;
        double scale;
        int alpha;
        if (!launched) {
            double form = Math.clamp((now - p.startAt) / 360.0, 0.0, 1.0);
            scale = 0.55 + 0.45 * form;
            alpha = (p.launchAt - now < 180L) ? 245 : (int) (145 + 75 * form);
            // 发射前背后凝结微型水法阵
            if (p.backAttached) {
                EffectSprites.glow(sp, p.spawnPos, camera, 0.65 * form, 0x80e6ff, (int) (180 * form));
            }
        } else {
            double fade = Math.clamp((p.endAt - now) / 300.0, 0.0, 1.0);
            scale = 1.08;
            alpha = (int) (235 * fade);
        }
        if (alpha <= 5) return;

        drawWaterShard(q, p.pos, p.dir, 1.45 * scale, 0.21 * scale, p.color, 0xe8fdff, alpha);
        EffectSprites.glow(sp, p.pos, camera, 0.58 * scale, 0x9cf2ff, (int) (alpha * 0.80));
        if (launched) {
            RibbonTrail.push("roxyorb:" + p.id, p.pos, 0.22 * scale, 0x6cd8ff, (int) (alpha * 0.85), 240, ClientUi.now());
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
            case "AQUA_BLADE" -> {
                Vec3 gf = a.from.add(0, 0.09, 0);
                Vec3 gt = a.to.add(0, 0.09, 0);
                if (!active) {
                    rect(q, gf, gt, a.radius, a.color, (int) (34 + 68 * warnProg));
                    Vec3 fillEnd = gf.lerp(gt, warnProg);
                    rect(q, gf.add(0, 0.02, 0), fillEnd.add(0, 0.02, 0), a.radius * 0.86, 0x9cf2ff, 110);
                } else {
                    if (shaken.add(a.id)) CameraShake.impulse(gf, 24, 0.24f, 60, 140);
                    double fade = Math.clamp(1.0 - activeProg, 0.0, 1.0);
                    Vec3 head = gf.lerp(gt, activeProg);
                    Vec3 tail = gf.lerp(gt, Math.max(0.0, activeProg - 0.22));
                    Vec3 dir = gt.subtract(gf);
                    rect(q, tail, head, a.radius, 0x62d8ff, (int) (135 * fade));
                    EffectSprites.slashArc(sp, head.add(0, 0.95, 0), dir, 0.0, a.radius * 0.72, clampedActive * 0.85, 0x8cf2ff, (int) (245 * fade));
                    EffectSprites.texturedBeam(sp, tail.add(0, 0.7, 0), head.add(0, 0.7, 0), camera, 0.36, now, false, 0xa8f6ff, (int) (220 * fade));
                }
            }
            case "GEYSER_COLUMN" -> {
                if (!active) {
                    EffectSprites.warningCircle(sp, g, a.radius, warnProg, a.color, 220);
                    ring(q, g.add(0, 0.03, 0), a.radius * (1.0 - warnProg * 0.7), 0.16, 0xd4fbff, 210);
                } else {
                    if (shaken.add(a.id)) CameraShake.impulse(g, 28, 0.34f, 90, 190);
                    double fade = Math.clamp(1.0 - activeProg, 0.0, 1.0);
                    double h = 14.5 * Math.min(1.0, activeProg * 3.5);
                    EffectSprites.texturedBeam(sp, g, g.add(0, h, 0), camera, a.radius * 0.32 * fade + 0.22, now, true, 0x5ccdff, (int) (245 * fade));
                    EffectSprites.shockwave(sp, g.add(0, 0.14, 0), a.radius * 1.18 * EffectSprites.easeOutCubic(clampedActive), clampedActive * 1.4, 0xbef6ff, (int) (245 * fade));
                    EffectSprites.impactBurst(sp, g.add(0, 0.55, 0), camera, a.radius * 1.1, clampedActive, 0x6cd8ff, 240, false);
                }
            }
            case "MIRROR_TRAP" -> {
                Vec3 mirrorCenter = a.from.add(0, 1.35, 0);
                EffectSprites.texturedBeam(sp, a.from.add(0, 1.0, 0), a.to.add(0, 1.0, 0), camera, 0.18, now, false, 0x9ceeff, active ? 55 : 165);
                if (!active) {
                    EffectSprites.warningCircle(sp, g, a.radius, warnProg, 0x86eeff, 215);
                    EffectSprites.sigil(sp, g.add(0, 0.02, 0), a.radius * 0.65, now * 0.004, 0xd8fbff, 215, true);
                    double pulse = 0.75 + 0.35 * warnProg;
                    drawWaterShard(q, mirrorCenter.add(0, 0.85 * pulse, 0), UP, 1.8 * pulse, 0.42 * pulse, 0x70e2ff, 0xffffff, 230);
                } else if (now <= a.warnUntil + 450L) {
                    if (shaken.add(a.id)) CameraShake.impulse(g, 24, 0.28f, 70, 160);
                    double burstP = Math.clamp((now - a.warnUntil) / 450.0, 0.0, 0.999);
                    double fade = 1.0 - burstP;
                    EffectSprites.shockwave(sp, g.add(0, 0.12, 0), a.radius * 1.2 * EffectSprites.easeOutCubic(burstP), burstP * 1.3, 0xffffff, (int) (245 * fade));
                    EffectSprites.impactBurst(sp, g.add(0, 0.6, 0), camera, a.radius * 1.1, burstP, 0x8ceeff, 240, false);
                }
            }
            case "TIDAL_RING" -> {
                double inner = a.auxRadius;
                double outer = a.radius;
                if (!active) {
                    if (inner <= 0.25) {
                        EffectSprites.warningCircle(sp, g, outer, warnProg, a.color, 215);
                        ring(q, g.add(0, 0.04, 0), outer * warnProg, 0.20, 0xe8fdff, 225);
                    } else {
                        annulus(q, g, inner, outer, a.color, (int) (34 + 68 * warnProg));
                        ring(q, g.add(0, 0.03, 0), inner, 0.24, 0xd8fbff, 230);
                        ring(q, g.add(0, 0.03, 0), outer, 0.24, 0xd8fbff, 230);
                        double sweep = inner + (outer - inner) * warnProg;
                        ring(q, g.add(0, 0.05, 0), sweep, 0.22, 0xffffff, 235);
                    }
                } else {
                    if (shaken.add(a.id)) CameraShake.impulse(g, 32, 0.36f, 95, 190);
                    double fade = Math.clamp(1.0 - activeProg, 0.0, 1.0);
                    if (inner <= 0.25) {
                        EffectSprites.shockwave(sp, g.add(0, 0.14, 0), outer * 1.12 * EffectSprites.easeOutCubic(clampedActive), clampedActive * 1.3, 0x8ceeff, (int) (245 * fade));
                        EffectSprites.impactBurst(sp, g.add(0, 0.45, 0), camera, outer * 0.9, clampedActive, a.color, 235, true);
                    } else {
                        annulus(q, g, inner, outer, 0x72e2ff, (int) (135 * fade));
                        EffectSprites.shockwave(sp, g.add(0, 0.25, 0), (inner + outer) * 0.5, clampedActive * 1.3, 0xd8fbff, (int) (240 * fade));
                    }
                }
            }
            case "THUNDER_CASCADE", "TRANSITION_BOLT", "TYPHOON_INNER_STRIKE" -> {
                if (!active) {
                    EffectSprites.warningCircle(sp, g, a.radius, warnProg, a.color, 225);
                    // 落雷前天空悬垂一丝引导电弧
                    if (warnProg > 0.45) {
                        LightningBolt.bolt(sp, g.add(0, 20.0, 0), g.add(0, 14.0 * (1.0 - warnProg) + 3.0, 0), camera,
                            (now / 65L) ^ a.id.hashCode(), 6, 0.45, 0.07, 0xbef6ff, 185, false);
                    }
                } else {
                    if (shaken.add(a.id)) CameraShake.impulse(g, 30, 0.38f, 95, 200);
                    double fade = Math.clamp(1.0 - activeProg, 0.0, 1.0);
                    LightningBolt.bolt(sp, g.add(0, 22.0, 0), g, camera,
                        (now / 45L) ^ a.id.hashCode(), 14, 1.05, 0.24 * fade + 0.06, 0xffffff, (int) (255 * fade), true);
                    LightningBolt.helixArcs(sp, g, g.add(0, 20.0, 0), camera, now, 1.15, 2, 0x7ce6ff, (int) (235 * fade));
                    EffectSprites.shockwave(sp, g.add(0, 0.14, 0), a.radius * 1.25 * EffectSprites.easeOutCubic(clampedActive), clampedActive * 1.5, 0x9cf2ff, (int) (250 * fade));
                    EffectSprites.impactBurst(sp, g.add(0, 0.45, 0), camera, a.radius * 1.15, clampedActive, 0xd8fbff, 245, false);
                }
            }
            case "TRANSITION_LAND_WAVE", "TYPHOON_FINALE_BLAST" -> {
                if (!active) {
                    EffectSprites.warningCircle(sp, g, a.radius, warnProg, 0x9cf4ff, 225);
                } else {
                    if (shaken.add(a.id)) CameraShake.impulse(g, 58, 0.65f, 180, 360);
                    double fade = Math.clamp(1.0 - activeProg, 0.0, 1.0);
                    double r = a.radius * (0.20 + 0.92 * EffectSprites.easeOutCubic(clampedActive));
                    LightningBolt.bolt(sp, g.add(0, 24.0, 0), g, camera,
                        (now / 45L) ^ a.id.hashCode(), 14, 1.35, 0.30 * fade + 0.07, 0xffffff, (int) (255 * fade), true);
                    EffectSprites.shockwave(sp, g.add(0, 0.18, 0), r, clampedActive * 1.6, 0x9cf4ff, (int) (250 * fade));
                    EffectSprites.shockwave(sp, g.add(0, 1.1, 0), r * 0.82, -clampedActive * 1.4, 0xffffff, (int) (220 * fade));
                    EffectSprites.impactBurst(sp, g.add(0, 0.75, 0), camera, r * 0.85, clampedActive, 0x72e2ff, 245, true);
                }
            }
            default -> {}
        }
    }

    /** 绘制晶莹四棱水枪/魔导结晶尖刺（尖端位于 tip）。 */
    private static void drawWaterShard(List<Quad> out, Vec3 tip, Vec3 direction, double length, double radius,
                                       int outerColor, int coreColor, int alpha) {
        if (alpha <= 0 || direction.lengthSqr() < 1e-6) return;
        Vec3 fwd = direction.normalize();
        Vec3 ref = Math.abs(fwd.y) > 0.92 ? new Vec3(1, 0, 0) : UP;
        Vec3 right = fwd.cross(ref).normalize().scale(radius);
        Vec3 up = right.cross(fwd).normalize().scale(radius);

        Vec3 mid = tip.subtract(fwd.scale(length * 0.70));
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
            out.add(new Quad(tip, a, b, tip, i % 2 == 0 ? outerColor : coreColor, coreAlpha));
            out.add(new Quad(tail, b, a, tail, outerColor, (int) (alpha * 0.85)));
        }
    }

    /** 绘制水平同心圆环带（供潮汐环与天顶积雨云环带复用）。 */
    private static void annulus(List<Quad> out, Vec3 center, double innerR, double outerR, int color, int alpha) {
        if (alpha <= 0 || outerR <= innerR) return;
        int segs = 40;
        for (int i = 0; i < segs; i++) {
            double a0 = i * (Math.PI * 2.0 / segs);
            double a1 = (i + 1) * (Math.PI * 2.0 / segs);
            Vec3 i0 = center.add(Math.cos(a0) * innerR, 0, Math.sin(a0) * innerR);
            Vec3 i1 = center.add(Math.cos(a1) * innerR, 0, Math.sin(a1) * innerR);
            Vec3 o0 = center.add(Math.cos(a0) * outerR, 0, Math.sin(a0) * outerR);
            Vec3 o1 = center.add(Math.cos(a1) * outerR, 0, Math.sin(a1) * outerR);
            out.add(new Quad(o0, o1, i1, i0, color, alpha));
        }
    }

    private static void submit(SubmitCustomGeometryEvent event) {
        Frame frame = event.getLevelRenderState().getRenderData(FRAME);
        if (frame == null) return;
        WorldQuads.submit(event, frame.quads(), frame.fade());
        EffectSprites.submit(event, frame.sprites(), frame.fade());
    }
}
