package cn.yumis.fight.modules.dungeons.client;

import cn.yumis.fight.modules.dungeons.client.GilgameshBossClient.AreaHazard;
import cn.yumis.fight.modules.dungeons.client.GilgameshBossClient.DecreeVisual;
import cn.yumis.fight.modules.dungeons.client.GilgameshBossClient.EnkiduBindVisual;
import cn.yumis.fight.modules.dungeons.client.GilgameshBossClient.ExecuteBeamVisual;
import cn.yumis.fight.modules.dungeons.client.GilgameshBossClient.FinaleVisual;
import cn.yumis.fight.modules.dungeons.client.GilgameshBossClient.GateProjectile;
import cn.yumis.fight.modules.dungeons.client.GilgameshBossClient.VimanaVisual;
import cn.yumis.fight.modules.equipment.api.EquipmentVisualItems;
import cn.yumis.fight.modules.presentation.api.client.CameraShake;
import cn.yumis.fight.modules.presentation.api.client.ClientUi;
import cn.yumis.fight.modules.presentation.api.client.EffectModels;
import cn.yumis.fight.modules.presentation.api.client.EffectSprites;
import cn.yumis.fight.modules.presentation.api.client.EffectSprites.SpriteQuad;
import cn.yumis.fight.modules.presentation.api.client.LightningBolt;
import cn.yumis.fight.modules.presentation.api.client.NearEffectRendering;
import cn.yumis.fight.modules.presentation.api.client.RibbonTrail;
import cn.yumis.fight.modules.presentation.api.client.WorldQuads;
import cn.yumis.fight.modules.presentation.api.client.WorldQuads.Quad;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

import static cn.yumis.fight.modules.dungeons.client.EnemyEffectGeometry.*;

/**
 * 首领「吉尔伽美什（英雄王）」世界空间特效渲染器：
 * - 王之财宝金色空间涟漪门 + 复用全套 3D 武器实体模型高速投射（武器尖端严格朝向飞行/坠落方向）；
 * - 宝具楔阵坠落武器与 5s 收缩引爆圈、近身斥退金环、天穹剑雨；
 * - 65% 转场黄金光辉之舟「维摩那」与同心圆环带轰炸；
 * - 「天之锁」螺旋黄金锁链与苍穹神枪处决光柱；
 * - 25% 赤金星裂穹顶异象、乖离剑空间撕裂斩与「天地乖离开辟之星」龙卷+星柱蓝色时空护幕；
 * - 1% 濒死终焉赤红能量球与裂空余波。
 */
public final class GilgameshBossVisuals {
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final Vec3 DOWN = new Vec3(0, -1, 0);
    private static final ContextKey<Frame> FRAME =
        new ContextKey<>(Identifier.fromNamespaceAndPath("yumis_dungeon_and_fight", "gilgamesh_boss"));

    private record FlyingWeapon(ItemStackRenderState model, Vec3 pos, Vec3 dir, float scale) {}
    private record Frame(List<Quad> quads, List<SpriteQuad> sprites, List<FlyingWeapon> weapons, boolean fade) {}
    private static final java.util.Set<String> shaken = new java.util.HashSet<>();

    private static ItemStack[] weaponCatalog;
    private static ItemStackRenderState[] weaponStates;

    private GilgameshBossVisuals() {}

    public static void register() {
        var events = NeoForge.EVENT_BUS;
        events.addListener(GilgameshBossVisuals::extract);
        events.addListener(GilgameshBossVisuals::submit);
    }

    /** 将本地 +Y 尖端朝向的 3D 武器模型旋转到目标世界方向 dir（无 X 轴镜像偏差）。 */
    static Quaternionf tipRotation(Vec3 dir) {
        if (dir == null || dir.lengthSqr() < 1e-8) {
            return new Quaternionf();
        }
        Vec3 n = dir.normalize();
        if (n.y < -0.9999) {
            return Axis.XP.rotationDegrees(180.0f);
        }
        if (n.y > 0.9999) {
            return new Quaternionf();
        }
        return new Quaternionf().rotationTo(new Vector3f(0f, 1f, 0f), new Vector3f((float) n.x, (float) n.y, (float) n.z));
    }

    private static void ensureWeapons() {
        if (weaponCatalog != null) return;
        // 排除弯月双刃战刃（横向展开无单轴尖端），仅保留尖端严格沿 +Y 的 3D 武器模型
        var list = EquipmentVisualItems.weaponModels().stream()
            .filter(m -> !"warglaive".equals(m.item()))
            .toList();
        weaponCatalog = new ItemStack[list.size()];
        weaponStates = new ItemStackRenderState[list.size()];
        for (int i = 0; i < list.size(); i++) {
            weaponCatalog[i] = EquipmentVisualItems.weaponModel(list.get(i).id());
        }
    }

    private static ItemStackRenderState weaponState(int index) {
        ensureWeapons();
        int safe = Math.floorMod(index, weaponCatalog.length);
        if (weaponStates[safe] == null) {
            weaponStates[safe] = new ItemStackRenderState();
            Minecraft.getInstance().getItemModelResolver().updateForLiving(
                weaponStates[safe], weaponCatalog[safe], ItemDisplayContext.NONE, Minecraft.getInstance().player);
        }
        return weaponStates[safe];
    }

    private static void extract(ExtractLevelRenderStateEvent event) {
        var quads = new ArrayList<Quad>();
        var sprites = new ArrayList<SpriteQuad>();
        var weapons = new ArrayList<FlyingWeapon>();
        var mc = Minecraft.getInstance();
        Vec3 camera = event.getRenderState().cameraRenderState == null ? Vec3.ZERO : event.getRenderState().cameraRenderState.pos;
        if (mc.level != null && mc.player != null && ClientUi.current().ready()) {
            long now = ClientUi.current().serverNow();
            shaken.retainAll(GilgameshBossClient.areas.keySet());

            if (GilgameshBossClient.eaSkyActive && GilgameshBossClient.eaSkyCenter.lengthSqr() > 1.0) {
                drawEaSkyAnomaly(quads, sprites, GilgameshBossClient.eaSkyCenter, now);
            }
            VimanaVisual vimana = GilgameshBossClient.vimanaVisual;
            if (vimana != null && now >= vimana.startAt && now <= vimana.endAt + 300L) {
                drawVimana(quads, sprites, vimana, now);
            }
            DecreeVisual decree = GilgameshBossClient.decreeVisual;
            if (decree != null && now >= decree.startAt && now <= decree.endAt + 300L) {
                drawEnumaDecree(quads, sprites, camera, decree, now);
            }
            FinaleVisual finale = GilgameshBossClient.finaleVisual;
            if (finale != null && now >= finale.startAt && now <= finale.endAt + 300L) {
                drawFinaleCore(quads, sprites, camera, finale, now);
            }
            for (EnkiduBindVisual bind : GilgameshBossClient.enkiduBinds.values()) {
                if (now >= bind.startAt && now <= bind.expireAt + 200L) {
                    drawEnkiduBind(quads, sprites, camera, bind, now);
                }
            }
            for (ExecuteBeamVisual beam : GilgameshBossClient.executeBeams) {
                if (now >= beam.startAt && now <= beam.endAt) {
                    drawExecuteBeam(quads, sprites, camera, beam, now);
                }
            }
            for (AreaHazard a : GilgameshBossClient.areas.values()) {
                if (now < a.startAt || now > a.endAt + 350L) continue;
                drawArea(quads, sprites, weapons, camera, a, now);
            }
            for (GateProjectile p : GilgameshBossClient.projectiles.values()) {
                if (p.consumed || now < p.startAt || now > p.endAt) continue;
                drawGateProjectile(quads, sprites, weapons, camera, p, now);
            }
        }
        event.getRenderState().setRenderData(FRAME, new Frame(List.copyOf(quads), List.copyOf(sprites), List.copyOf(weapons), NearEffectRendering.enabled(event.getCamera())));
    }

    private static void drawGateProjectile(List<Quad> q, List<SpriteQuad> sp, List<FlyingWeapon> weapons, Vec3 camera, GateProjectile p, long now) {
        boolean launched = now >= p.launchAt;
        long gateFadeEnd = p.launchAt + 450L;
        if (now <= gateFadeEnd) {
            double openProg = launched
                ? Math.clamp((gateFadeEnd - now) / 450.0, 0.0, 1.0)
                : Math.clamp((now - p.startAt) / 300.0, 0.0, 1.0);
            drawBabylonPortal(q, sp, p.gatePos, p.dir, 0.62 * (0.45 + 0.55 * openProg), now, (int) (215 * openProg));
        }

        double alphaFactor = launched ? Math.clamp((p.endAt - now) / 300.0, 0.0, 1.0) : 1.0;
        if (alphaFactor <= 0.05) return;

        double emerge = launched ? 1.0 : Math.clamp((now - p.startAt) / (double) Math.max(1L, p.launchAt - p.startAt), 0.25, 1.0);
        // 武器模型尖端沿本地 +Y（距握柄约 +1.7 格）；蓄力时尖端先探出金色涟漪门，飞行时尖端直指弹道方向
        Vec3 drawPos = launched
            ? p.pos.subtract(p.dir.scale(0.55))
            : p.gatePos.add(p.dir.scale(-0.65 + 0.85 * emerge));
        weapons.add(new FlyingWeapon(weaponState(p.weaponIndex), drawPos, p.dir, launched ? 1.15f : (float) (0.78 + 0.37 * emerge)));

        if (!launched && !p.backAttached) {
            Vec3 aimEnd = p.gatePos.add(p.dir.scale(14.5));
            line(q, p.gatePos, aimEnd, 0.04, 0xffe484, (int) (125 * emerge));
        }
        if (launched) {
            RibbonTrail.ribbon(sp, List.of(p.pos, p.pos.subtract(p.dir.scale(1.1)), p.pos.subtract(p.dir.scale(2.4))), camera, 0.16, p.color, (int) (225 * alphaFactor));
            EffectSprites.glow(sp, p.pos, camera, 0.45, 0xfff4c2, (int) (210 * alphaFactor));
        }
    }

    private static void drawBabylonPortal(List<Quad> q, List<SpriteQuad> sp, Vec3 center, Vec3 normal, double radius, long now, int alpha) {
        if (alpha <= 5 || normal.lengthSqr() < 1e-6) return;
        Vec3 n = normal.normalize();
        Vec3 ref = Math.abs(n.y) > 0.92 ? new Vec3(1, 0, 0) : UP;
        Vec3 u = n.cross(ref).normalize();
        Vec3 v = u.cross(n).normalize();
        double spin = now * 0.0045;
        double c = Math.cos(spin) * radius * 1.18, s = Math.sin(spin) * radius * 1.18;
        Vec3 ru = u.scale(c).add(v.scale(s));
        Vec3 rv = v.scale(c).subtract(u.scale(s));
        EffectSprites.emitPlane(sp, EffectSprites.MAGIC_SIGIL, EffectSprites.Blend.ADDITIVE, center, ru, rv, 0f, 0f, 1f, 1f, 0xffd858, alpha);
    }

    private static void drawArea(List<Quad> q, List<SpriteQuad> sp, List<FlyingWeapon> weapons, Vec3 camera, AreaHazard a, long now) {
        boolean active = now >= a.warnUntil;
        double warnProg = a.warnUntil <= a.startAt ? 1.0
            : Math.clamp((now - a.startAt) / (double) (a.warnUntil - a.startAt), 0.0, 1.0);
        double activeProg = a.endAt <= a.warnUntil ? 1.0
            : Math.clamp((now - a.warnUntil) / (double) (a.endAt - a.warnUntil), 0.0, 1.0);
        double clampedActive = Math.clamp(activeProg, 0.0, 0.999);
        Vec3 g = ("VIMANA_ORB".equals(a.type) ? a.to : a.from).add(0, 0.07, 0);

        switch (a.type) {
            case "WEDGE_DROP", "SKY_RAIN" -> {
                if (!active) {
                    EffectSprites.warningCircle(sp, g, a.radius, warnProg, a.color, 215);
                    drawBabylonPortal(q, sp, g.add(0, 12.5, 0), DOWN, 1.45, now, (int) (200 * warnProg));
                    double fallY = 12.5 * (1.0 - warnProg * warnProg * warnProg);
                    double tipY = Math.max(0.05, fallY);
                    // 3D 坠落宝具：尖端严格朝下 (0, -1, 0)，从高空金色涟漪门笔直贯落至落点
                    float fallScale = "WEDGE_DROP".equals(a.type) ? 1.38f : 1.24f;
                    weapons.add(new FlyingWeapon(weaponState(a.id.hashCode()), g.add(0, tipY + 2.05, 0), DOWN, fallScale));
                    EffectSprites.texturedBeam(sp, g.add(0, tipY, 0), g.add(0, tipY + 3.2, 0), camera, 0.20, now, false, 0xffe478, 200);
                } else {
                    if (shaken.add(a.id)) CameraShake.impulse(g, 24, 0.24f, 60, 140);
                    double fade = Math.clamp(1.0 - activeProg, 0.0, 1.0);
                    EffectSprites.shockwave(sp, g.add(0, 0.12, 0), a.radius * 1.15 * EffectSprites.easeOutCubic(clampedActive), clampedActive * 1.3, 0xffe88c, (int) (240 * fade));
                    EffectSprites.impactBurst(sp, g.add(0, 0.35, 0), camera, a.radius * 1.1, clampedActive, 0xffd24c, 240, true);
                }
            }
            case "WEDGE_ARMED" -> {
                double pulse = 0.5 + 0.5 * Math.sin(now * 0.015);
                int dangerColor = warnProg > 0.72 ? 0xff4638 : 0xffb838;
                EffectSprites.warningCircle(sp, g, a.radius, warnProg, dangerColor, 225);
                EffectSprites.texturedBeam(sp, g, g.add(0, 3.4, 0), camera, 0.24 + 0.06 * pulse, now, true, 0xffe478, 225);
            }
            case "WEDGE_EXPLODE", "EA_DRAW_WAVE", "FINALE_RIFT" -> {
                if (!active) {
                    EffectSprites.warningCircle(sp, g, a.radius, warnProg, a.color, 220);
                } else {
                    if (shaken.add(a.id)) CameraShake.impulse(g, 32, 0.45f, 110, 220);
                    double fade = Math.clamp(1.0 - activeProg, 0.0, 1.0);
                    EffectSprites.shockwave(sp, g.add(0, 0.14, 0), a.radius * 1.18 * EffectSprites.easeOutCubic(clampedActive), clampedActive * 1.4, a.color, (int) (245 * fade));
                    EffectSprites.impactBurst(sp, g.add(0, 0.65, 0), camera, a.radius * 1.1, clampedActive, a.color, 245, false);
                }
            }
            case "ROYAL_REPULSE" -> {
                if (!active) {
                    EffectSprites.warningCircle(sp, g, a.radius, warnProg, a.color, 220);
                } else {
                    if (shaken.add(a.id)) CameraShake.impulse(g, 28, 0.36f, 90, 190);
                    double fade = Math.clamp(1.0 - activeProg, 0.0, 1.0);
                    EffectSprites.shockwave(sp, g.add(0, 0.35, 0), a.radius * 1.2 * EffectSprites.easeOutCubic(clampedActive), clampedActive * 1.6, 0xffe270, (int) (250 * fade));
                    EffectSprites.impactBurst(sp, g.add(0, 0.6, 0), camera, a.radius * 1.1, clampedActive, 0xfff4c2, 240, true);
                }
            }
            case "VIMANA_RING" -> {
                double inner = a.auxRadius;
                double outer = a.radius;
                if (!active) {
                    annulus(q, g, inner, outer, a.color, (int) (28 + 64 * warnProg));
                    if (inner > 0.5) ring(q, g.add(0, 0.03, 0), inner, 0.22, 0xfff2b2, 220);
                    ring(q, g.add(0, 0.03, 0), outer, 0.22, 0xfff2b2, 220);
                    double sweepR = inner + (outer - inner) * warnProg;
                    ring(q, g.add(0, 0.05, 0), sweepR, 0.18, 0xffffff, 225);
                } else {
                    if (shaken.add(a.id)) CameraShake.impulse(g, 34, 0.34f, 90, 180);
                    double fade = Math.clamp(1.0 - activeProg, 0.0, 1.0);
                    annulus(q, g, inner, outer, 0xffe67a, (int) (125 * fade));
                    EffectSprites.shockwave(sp, g.add(0, 0.35, 0), (inner + outer) * 0.5, clampedActive * 1.2, 0xfff4b8, (int) (235 * fade));
                }
            }
            case "VIMANA_ORB" -> {
                if (!active) {
                    EffectSprites.warningCircle(sp, g, a.radius, warnProg, a.color, 215);
                    Vec3 orbPos = a.from.lerp(g, warnProg);
                    box(q, orbPos, 0.55, 0.55, 0.55, 0xffe26b, 235);
                    EffectSprites.glow(sp, orbPos, camera, 1.45, 0xffe884, 230);
                    RibbonTrail.ribbon(sp, List.of(orbPos, a.from.lerp(orbPos, 0.45)), camera, 0.30, 0xffd24c, 220);
                } else {
                    if (shaken.add(a.id)) CameraShake.impulse(g, 24, 0.26f, 60, 140);
                    double fade = Math.clamp(1.0 - activeProg, 0.0, 1.0);
                    EffectSprites.shockwave(sp, g.add(0, 0.14, 0), a.radius * 1.15 * EffectSprites.easeOutCubic(clampedActive), clampedActive * 1.2, 0xffe478, (int) (240 * fade));
                    EffectSprites.impactBurst(sp, g.add(0, 0.35, 0), camera, a.radius * 1.1, clampedActive, 0xffe478, 245, false);
                }
            }
            case "ENKIDU_WARNING" -> {
                if (!active) {
                    EffectSprites.warningCircle(sp, g, a.radius, warnProg, 0xe8bd3a, 225);
                } else {
                    double fade = Math.clamp(1.0 - activeProg, 0.0, 1.0);
                    for (int i = 0; i < 4; i++) {
                        double ang = i * Math.PI * 0.5;
                        Vec3 base = g.add(Math.cos(ang) * a.radius * 0.6, 0, Math.sin(ang) * a.radius * 0.6);
                        EffectSprites.texturedBeam(sp, base, g.add(0, 2.5, 0), camera, 0.14, now, true, 0xffe066, (int) (235 * fade));
                    }
                }
            }
            case "EA_SPACE_REND" -> {
                long slashEndAt = a.warnUntil + Math.max(150L, a.lifeMs);
                long collapseAt = slashEndAt + Math.max(200L, a.intervalMs);
                Vec3 gf = a.from.add(0, 0.08, 0);
                Vec3 gt = a.to.add(0, 0.08, 0);
                if (now < a.warnUntil) {
                    rect(q, gf, gt, a.radius, 0xd82828, (int) (32 + 64 * warnProg));
                    Vec3 fillEnd = gf.lerp(gt, warnProg);
                    rect(q, gf.add(0, 0.02, 0), fillEnd.add(0, 0.02, 0), a.radius * 0.88, 0xff8a65, 95);
                } else if (now <= slashEndAt) {
                    if (shaken.add(a.id)) CameraShake.impulse(gf.lerp(gt, 0.5), 32, 0.42f, 100, 200);
                    double prog = Math.clamp((now - a.warnUntil) / (double) Math.max(1L, slashEndAt - a.warnUntil), 0.0, 1.0);
                    rect(q, gf, gt, a.radius, 0xff2c38, 120);
                    Vec3 head = gf.lerp(gt, prog);
                    EffectSprites.texturedBeam(sp, gf.add(0, 1.1, 0), head.add(0, 1.1, 0), camera, 0.52, now, true, 0xff4652, 245);
                    LightningBolt.helixArcs(sp, gf.add(0, 1.1, 0), head.add(0, 1.1, 0), camera, now, 0.72, 2, 0xffc070, 230);
                } else if (now < collapseAt) {
                    double cProg = Math.clamp((now - slashEndAt) / (double) Math.max(1L, collapseAt - slashEndAt), 0.0, 1.0);
                    rect(q, gf, gt, a.radius, 0x8a1024, (int) (55 + 75 * cProg));
                    LightningBolt.bolt(sp, gf.add(0, 0.4, 0), gt.add(0, 0.4, 0), camera, (now / 45L) ^ a.id.hashCode(), 10, 0.45, 0.09, 0xff4655, 240, true);
                } else {
                    double fade = Math.clamp((a.endAt - now) / (double) Math.max(1L, a.endAt - collapseAt), 0.0, 1.0);
                    rect(q, gf, gt, a.radius, 0xff3644, (int) (130 * fade));
                    EffectSprites.texturedBeam(sp, gf.add(0, 1.3, 0), gt.add(0, 1.3, 0), camera, 0.85 * fade, now, true, 0xff3644, (int) (245 * fade));
                    LightningBolt.helixArcs(sp, gf.add(0, 1.3, 0), gt.add(0, 1.3, 0), camera, now, 1.05, 3, 0xffc07a, (int) (235 * fade));
                }
            }
            default -> {}
        }
    }

    private static void drawVimana(List<Quad> q, List<SpriteQuad> sp, VimanaVisual v, long now) {
        Vec3 p = v.currentPos(now).add(0, -0.35, 0);
        box(q, p, 2.2, 0.26, 2.2, 0xffd24c, 225);
        box(q, p.add(0, 0.28, 0), 1.35, 0.22, 1.35, 0xfff2a6, 235);
        EffectSprites.sigil(sp, p.add(0, -0.20, 0), 3.2, now * 0.0025, 0xffe478, 225, true);
        double spin = now * 0.0025;
        for (int i = 0; i < 4; i++) {
            double ang = spin + i * Math.PI * 0.5;
            Vec3 tip = p.add(Math.cos(ang) * 3.6, 0.15, Math.sin(ang) * 3.6);
            line(q, p, tip, 0.14, 0xffdf68, 215);
        }
    }

    private static void drawEnkiduBind(List<Quad> q, List<SpriteQuad> sp, Vec3 camera, EnkiduBindVisual b, long now) {
        var mc = Minecraft.getInstance();
        Vec3 playerPos = b.playerAnchor;
        if (mc.level != null) {
            Entity p = mc.level.getPlayerByUUID(b.playerId);
            if (p != null) playerPos = p.position();
        }
        Vec3 anchorPos = b.chainAnchor;
        if (mc.level != null && b.anchorEntityId >= 0) {
            Entity a = mc.level.getEntity(b.anchorEntityId);
            if (a != null && a.isAlive()) anchorPos = a.position();
        }
        double remain = Math.clamp((b.expireAt - now) / (double) Math.max(1L, b.expireAt - b.startAt), 0.0, 1.0);
        int chainColor = remain < 0.3 ? 0xff6242 : 0xffd75e;
        double spin = now * 0.006;
        for (int strand = 0; strand < 2; strand++) {
            List<Vec3> pts = new ArrayList<>(12);
            for (int seg = 0; seg <= 10; seg++) {
                double f = seg / 10.0;
                double a = spin + strand * Math.PI + f * Math.PI * 3.2;
                pts.add(playerPos.add(Math.cos(a) * 0.72, 0.2 + f * 1.75, Math.sin(a) * 0.72));
            }
            RibbonTrail.ribbon(sp, pts, camera, 0.14, chainColor, 235);
        }
        EffectSprites.texturedBeam(sp, playerPos.add(0, 1.0, 0), anchorPos.add(0, 1.0, 0), camera, 0.16, now, true, 0xffe478, 235);
        EffectSprites.sigil(sp, anchorPos.add(0, 0.08, 0), 1.35, now * 0.003, chainColor, 225, true);
    }

    private static void drawExecuteBeam(List<Quad> q, List<SpriteQuad> sp, Vec3 camera, ExecuteBeamVisual b, long now) {
        double prog = Math.clamp((now - b.startAt) / (double) Math.max(1L, b.endAt - b.startAt), 0.0, 0.999);
        double fade = 1.0 - prog;
        EffectSprites.texturedBeam(sp, b.pos.add(0, 0.1, 0), b.pos.add(0, 18.0, 0), camera, 0.75 * fade + 0.18, now, true, 0xffd858, (int) (250 * fade));
        LightningBolt.helixArcs(sp, b.pos.add(0, 0.1, 0), b.pos.add(0, 18.0, 0), camera, now, 1.05, 2, 0xffffff, (int) (235 * fade));
        EffectSprites.shockwave(sp, b.pos.add(0, 0.12, 0), 4.0 * EffectSprites.easeOutCubic(prog), prog * 1.4, 0xffe478, (int) (240 * fade));
    }

    private static void drawEaSkyAnomaly(List<Quad> q, List<SpriteQuad> sp, Vec3 center, long now) {
        Vec3 sky = center.add(0, 16.0, 0);
        double pulse = 0.5 + 0.5 * Math.sin(now * 0.004);
        EffectSprites.sigil(sp, sky, 16.5 + pulse * 1.5, now * 0.0012, 0xff3640, 185, true);
        EffectSprites.sigil(sp, sky.add(0, -1.8, 0), 11.5, -now * 0.0018, 0xffb84d, 175, false);
    }

    private static void drawEnumaDecree(List<Quad> q, List<SpriteQuad> sp, Vec3 camera, DecreeVisual d, long now) {
        Vec3 c = d.center;
        if (now < d.impactAt) {
            double prog = Math.clamp((now - d.startAt) / (double) Math.max(1L, d.impactAt - d.startAt), 0.0, 1.0);
            for (int tier = 0; tier < 6; tier++) {
                double y = 0.8 + tier * 2.2;
                double r = (2.2 + tier * 1.1) * (0.65 + 0.55 * prog);
                ring(q, c.add(0, y, 0), r, 0.32, tier % 2 == 0 ? 0xff2c38 : 0xffb84d, (int) (165 + 75 * prog));
            }
            EffectSprites.texturedBeam(sp, c.add(0, 0.4, 0), c.add(0, 20.0, 0), camera, 0.9 + 1.1 * prog, now, true, 0xff303c, 240);
            LightningBolt.helixArcs(sp, c.add(0, 0.4, 0), c.add(0, 20.0, 0), camera, now, 2.2 + 1.2 * prog, 3, 0xffb84d, 235);
            EffectSprites.shockwave(sp, c.add(0, 0.12, 0), 42.0 * (1.0 - prog * 0.72), -prog * 2.0, 0xff3038, 210);
        } else {
            if (shaken.add("enuma:" + d.startAt)) CameraShake.impulse(c, 64, 0.85f, 260, 480);
            double sweepProg = Math.clamp((now - d.impactAt) / (double) Math.max(1L, d.endAt - d.impactAt), 0.0, 0.999);
            double fade = 1.0 - sweepProg;
            double r = 46.0 * (0.15 + 0.85 * EffectSprites.easeOutCubic(sweepProg));
            EffectSprites.shockwave(sp, c.add(0, 1.2, 0), r, sweepProg * 1.6, 0xff2e3a, (int) (250 * fade));
            EffectSprites.shockwave(sp, c.add(0, 3.0, 0), r * 0.90, -sweepProg * 1.3, 0xffc864, (int) (235 * fade));
            EffectSprites.impactBurst(sp, c.add(0, 2.5, 0), camera, 24.0, sweepProg, 0xff3a46, 250, false);
        }

        if (now >= d.revealAt) {
            double shelterFade = now < d.impactAt ? 1.0
                : Math.clamp((d.endAt - now) / (double) Math.max(1L, d.endAt - d.impactAt), 0.0, 1.0);
            for (Vec3 s : d.shelters) {
                Vec3 sg = s.add(0, 0.10, 0);
                EffectSprites.sigil(sp, sg, d.shelterRadius * 1.04, now * 0.002, 0x5cd2ff, (int) (235 * shelterFade), true);
                ring(q, sg.add(0, 1.6, 0), d.shelterRadius * 0.88, 0.18, 0x7ce4ff, (int) (205 * shelterFade));
                EffectSprites.texturedBeam(sp, sg, sg.add(0, 22.0, 0), camera, 0.55, now, true, 0x86eaff, (int) (220 * shelterFade));
            }
        }
    }

    private static void drawFinaleCore(List<Quad> q, List<SpriteQuad> sp, Vec3 camera, FinaleVisual f, long now) {
        double prog = Math.clamp((now - f.startAt) / (double) Math.max(1L, f.endAt - f.startAt), 0.0, 1.0);
        Vec3 c = f.center;
        double pulse = 0.5 + 0.5 * Math.sin(now * (0.012 + 0.018 * prog));
        EffectSprites.sigil(sp, c.add(0, 0.10, 0), 5.2 + 1.4 * pulse, now * 0.003, 0xff3644, 235, true);
        EffectSprites.texturedBeam(sp, c.add(0, 0.2, 0), c.add(0, 16.0, 0), camera, 0.5 + 0.6 * prog, now, true, 0xff3846, 225);
        LightningBolt.helixArcs(sp, c.add(0, 0.2, 0), c.add(0, 16.0, 0), camera, now, 1.8, 2, 0xffc85e, 225);
    }

    /** 绘制同心圆环带（对齐星仪塔地面铜环）。 */
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
        if (frame == null || event.getLevelRenderState().cameraRenderState == null) return;
        Vec3 camera = event.getLevelRenderState().cameraRenderState.pos;
        var pose = event.getPoseStack();
        for (FlyingWeapon w : frame.weapons()) {
            Vec3 p = w.pos();
            pose.pushPose();
            pose.translate(p.x - camera.x, p.y - camera.y, p.z - camera.z);
            pose.mulPose(tipRotation(w.dir()));
            pose.scale(w.scale(), w.scale(), w.scale());
            EffectModels.submit(w.model(), pose, event.getSubmitNodeCollector(), 0xF000F0, OverlayTexture.NO_OVERLAY, frame.fade());
            pose.popPose();
        }
        WorldQuads.submit(event, frame.quads(), frame.fade());
        EffectSprites.submit(event, frame.sprites(), frame.fade());
    }
}
