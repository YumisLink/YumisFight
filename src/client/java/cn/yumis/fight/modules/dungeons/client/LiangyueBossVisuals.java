package cn.yumis.fight.modules.dungeons.client;

import cn.yumis.fight.modules.character.api.JobCatalog;
import cn.yumis.fight.modules.dungeons.client.LiangyueBossClient.Hazard;
import cn.yumis.fight.modules.dungeons.content.TheaterTowerLiangyue;
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
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Quaternionf;

import java.util.*;

import static cn.yumis.fight.modules.dungeons.client.EnemyEffectGeometry.*;

/**
 * 剧场塔第 20 层首领「神剑-梁月的幻影」世界空间特效渲染器：
 * - 大范围 AOE 直线剑气（预警通道 + 推进月牙剑波）；
 * - 一段猛龙断空斩 20 格突进龙影轨迹 + 三道追击旋转风刃龙卷；
 * - 幻影剑舞近身剑阵与多段追踪剑气；
 * - 剑圣 1 觉「极鬼剑术·暴风式」24 把 3D 神剑阵、穿梭斩击带与终结巨斩。
 */
public final class LiangyueBossVisuals {
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final ContextKey<Frame> FRAME =
        new ContextKey<>(Identifier.fromNamespaceAndPath("yumis_dungeon_and_fight", "liangyue_boss"));

    private record StormSword(ItemStackRenderState model, Vec3 position, float yaw, float tiltDeg, float azimuthDeg) {}
    private record Frame(List<Quad> quads, List<SpriteQuad> sprites, List<StormSword> swords, boolean fade) {}
    private static final Set<String> shaken = new HashSet<>();

    private static final Map<String, ItemStack[]> stormStacks = new HashMap<>();
    private static final Map<String, ItemStackRenderState[]> stormModels = new HashMap<>();
    private static final Map<String, float[][]> stormTilts = new HashMap<>();

    private LiangyueBossVisuals() {}

    public static void register() {
        var events = NeoForge.EVENT_BUS;
        events.addListener(LiangyueBossVisuals::extract);
        events.addListener(LiangyueBossVisuals::submit);
    }

    private static void extract(ExtractLevelRenderStateEvent event) {
        var quads = new ArrayList<Quad>();
        var sprites = new ArrayList<SpriteQuad>();
        var swords = new ArrayList<StormSword>();
        var mc = Minecraft.getInstance();
        Vec3 camera = event.getRenderState().cameraRenderState == null ? Vec3.ZERO : event.getRenderState().cameraRenderState.pos;
        if (mc.level != null && mc.player != null && ClientUi.current().ready()) {
            long now = ClientUi.current().serverNow();
            stormStacks.keySet().retainAll(LiangyueBossClient.hazards.keySet());
            stormModels.keySet().retainAll(LiangyueBossClient.hazards.keySet());
            stormTilts.keySet().retainAll(LiangyueBossClient.hazards.keySet());
            shaken.retainAll(LiangyueBossClient.hazards.keySet());
            for (Hazard h : LiangyueBossClient.hazards.values()) {
                if (now < h.startAt || now > h.endAt + 350L) continue;
                drawHazard(quads, sprites, swords, camera, h, now);
            }
        }
        event.getRenderState().setRenderData(FRAME, new Frame(List.copyOf(quads), List.copyOf(sprites), List.copyOf(swords), NearEffectRendering.enabled(event.getCamera())));
    }

    private static void drawHazard(List<Quad> q, List<SpriteQuad> sp, List<StormSword> swords, Vec3 camera, Hazard h, long now) {
        boolean active = now >= h.warnUntil;
        double warnProgress = h.warnUntil <= h.startAt ? 1.0
            : Math.clamp((now - h.startAt) / (double) (h.warnUntil - h.startAt), 0.0, 1.0);
        double activeProgress = h.endAt <= h.warnUntil ? 1.0
            : Math.clamp((now - h.warnUntil) / (double) (h.endAt - h.warnUntil), 0.0, 1.0);
        double clampedActive = Math.clamp(activeProgress, 0.0, 0.999);

        switch (h.type) {
            case "SWORD_WAVE", "SWORD_WAVE_LARGE", "SWORD_WAVE_FINISHER" -> {
                Vec3 groundFrom = h.from.add(0, 0.07, 0);
                Vec3 groundTo = h.to.add(0, 0.07, 0);
                int laneAlpha = active ? (int) (78 * (1.0 - activeProgress * 0.7)) : (int) (34 + 62 * warnProgress);
                rect(q, groundFrom, groundTo, h.width, h.color, laneAlpha);
                if (!active) {
                    Vec3 fillEnd = groundFrom.lerp(groundTo, warnProgress);
                    rect(q, groundFrom.add(0, 0.01, 0), fillEnd.add(0, 0.01, 0), h.width * 0.92, 0xe8fdff, 65);
                } else {
                    if ("SWORD_WAVE_FINISHER".equals(h.type) && shaken.add(h.id)) CameraShake.impulse(h.from, 26, 0.32f, 80, 180);
                    Vec3 dir = horizontalDir(h.from, h.to);
                    Vec3 head = h.from.lerp(h.to, activeProgress);
                    double fade = Math.clamp(1.0 - Math.max(0.0, activeProgress - 0.75) * 4.0, 0.0, 1.0);
                    int alpha = (int) (225 * fade);
                    double waveRadius = h.width * 0.62;
                    double thickness = "SWORD_WAVE_FINISHER".equals(h.type) ? 0.85 : "SWORD_WAVE_LARGE".equals(h.type) ? 0.65 : 0.45;
                    swordWave(q, head.add(0, 1.05, 0), dir, h.slashAngle, waveRadius, thickness, h.color, 0xf2ffff, alpha);
                    EffectSprites.slashArc(sp, head.add(0, 1.1, 0), dir, h.slashAngle, waveRadius * 1.45, Math.clamp((activeProgress * 1.4) % 1.0, 0.15, 0.85), h.color, (int) (245 * fade));
                    RibbonTrail.push("liangyue:" + h.id, head.add(0, 1.0, 0), waveRadius * 0.35, h.color, (int) (215 * fade), 240, ClientUi.now());
                    EffectSprites.shockwave(sp, head.add(0, 0.08, 0), h.width * 0.48, activeProgress * 2.0, 0xd8fbff, (int) (175 * fade));
                }
            }
            case "DRAGON_DASH" -> {
                Vec3 groundFrom = h.from.add(0, 0.08, 0);
                Vec3 groundTo = h.to.add(0, 0.08, 0);
                rect(q, groundFrom, groundTo, h.width, h.color, active ? 95 : (int) (40 + 68 * warnProgress));
                if (!active) {
                    Vec3 fillEnd = groundFrom.lerp(groundTo, warnProgress);
                    rect(q, groundFrom.add(0, 0.01, 0), fillEnd.add(0, 0.01, 0), h.width * 0.85, 0xd8faff, 80);
                } else {
                    if (shaken.add(h.id)) CameraShake.impulse(h.from, 26, 0.32f, 90, 180);
                    Vec3 dir = horizontalDir(h.from, h.to);
                    Vec3 head = h.from.lerp(h.to, activeProgress);
                    EffectSprites.texturedBeam(sp, h.from.add(0, 1.0, 0), head.add(0, 1.0, 0), camera, 0.55, now, true, h.color, 240);
                    LightningBolt.helixArcs(sp, h.from.add(0, 1.0, 0), head.add(0, 1.0, 0), camera, now, 0.85, 2, 0xd8faff, 225);
                    dragonAura(q, head, dir, h.color, 225);
                    EffectSprites.slashArc(sp, head.add(0, 1.1, 0), dir, 0, 2.6, clampedActive, h.color, 245);
                }
            }
            case "WHIRLWIND" -> {
                Vec3 ground = h.currentPos(now).add(0, 0.06, 0);
                if (!active) {
                    EffectSprites.warningCircle(sp, ground, h.radius, warnProgress, h.color, 205);
                } else {
                    double lifeFade = Math.clamp((h.endAt - now) / 500.0, 0.0, 1.0);
                    EffectSprites.sigil(sp, ground, h.radius * 1.05, now * 0.004, h.color, (int) (200 * lifeFade), true);
                    drawWhirlwindVortex(q, sp, camera, ground, h.radius, now, h.color, lifeFade);
                }
            }
            case "DANCE_FLURRY" -> {
                Vec3 ground = h.from.add(0, 0.07, 0);
                if (!active) {
                    EffectSprites.warningCircle(sp, ground, h.radius, warnProgress, h.color, 210);
                } else {
                    EffectSprites.sigil(sp, ground, h.radius * 1.04, now * 0.0025, h.color, 205, true);
                    int phase = (int) ((now - h.warnUntil) / 140L);
                    double sub = ((now - h.warnUntil) % 140L) / 140.0;
                    for (int k = 0; k < 3; k++) {
                        double a = (phase + k * 2.1) * 1.45;
                        Vec3 slashDir = new Vec3(Math.cos(a), 0, Math.sin(a));
                        EffectSprites.slashArc(sp, ground.add(0, 1.05 + (k - 1) * 0.25, 0), slashDir, (k - 1) * 38, h.radius * 1.1, Math.clamp(sub, 0, 0.999), h.color, 235);
                    }
                }
            }
            case "STORM_AWAKENING" -> drawStormAwakening(q, sp, swords, camera, h, now);
            default -> {}
        }
    }

    private static void drawWhirlwindVortex(List<Quad> q, List<SpriteQuad> sp, Vec3 camera, Vec3 base, double radius, long now, int color, double fade) {
        double spin = now * 0.011;
        for (int tier = 0; tier < 5; tier++) {
            double frac = tier / 4.0;
            double y = 0.25 + frac * 2.8;
            double r = radius * (0.42 + frac * 0.58);
            ring(q, base.add(0, y, 0), r, 0.09, tier % 2 == 0 ? color : 0xe8ffff, (int) ((165 - tier * 15) * fade));
        }
        for (int strand = 0; strand < 3; strand++) {
            double baseAngle = spin + strand * (Math.PI * 2.0 / 3.0);
            List<Vec3> pts = new ArrayList<>(8);
            for (int seg = 0; seg <= 7; seg++) {
                double frac = seg / 7.0;
                double a = baseAngle + frac * Math.PI * 2.1;
                double r = radius * (0.38 + frac * 0.58);
                pts.add(base.add(Math.cos(a) * r, 0.15 + frac * 3.1, Math.sin(a) * r));
            }
            RibbonTrail.ribbon(sp, pts, camera, 0.24, strand == 0 ? 0xffffff : color, (int) (225 * fade));
        }
    }

    private static void drawStormAwakening(List<Quad> q, List<SpriteQuad> sp, List<StormSword> swords, Vec3 camera, Hazard h, long now) {
        Vec3 center = h.from;
        long elapsed = now - h.startAt;
        long deployMs = Math.max(1L, h.warnUntil - h.startAt);
        long slashIntervalMs = Math.max(50L, h.slashIntervalMs);
        double deploy = Math.clamp(elapsed / (double) deployMs, 0.0, 1.0);
        int color = h.color;

        if (now < h.finisherAt) {
            EffectSprites.sigil(sp, center.add(0, 0.06, 0), h.radius * Math.max(0.15, deploy), now * 0.0012, color, 220, true);
            if (now < h.warnUntil) ring(q, center.add(0, 0.09, 0), h.radius, 0.12, color, 175);
        }

        if (now < h.finisherAt) {
            ensureStormSwords(h);
            ItemStack[] stacks = stormStacks.get(h.id);
            ItemStackRenderState[] models = stormModels.get(h.id);
            float[][] tilts = stormTilts.get(h.id);
            int[] order = TheaterTowerLiangyue.stormOrder(h.seed);
            int completed = TheaterTowerLiangyue.stormCompletedSwords(elapsed, deployMs, slashIntervalMs);
            boolean[] used = new boolean[TheaterTowerLiangyue.SKILL3_SWORDS];
            for (int i = 0; i < completed && i < order.length; i++) used[order[i]] = true;

            for (int i = 0; i < TheaterTowerLiangyue.SKILL3_SWORDS; i++) {
                if (used[i]) continue;
                Vec3 local = TheaterTowerLiangyue.stormSwordOffset(i);
                double spin = (1.0 - deploy) * Math.PI * 2.0;
                double rx = local.x * Math.cos(spin) - local.z * Math.sin(spin);
                double rz = local.x * Math.sin(spin) + local.z * Math.cos(spin);
                Vec3 swordPos = center.add(rx * deploy, 2.0 + (local.y - 2.0) * deploy, rz * deploy);
                if (models[i] == null) {
                    models[i] = new ItemStackRenderState();
                    Minecraft.getInstance().getItemModelResolver().updateForLiving(
                        models[i], stacks[i], ItemDisplayContext.NONE, Minecraft.getInstance().player);
                }
                swords.add(new StormSword(models[i], swordPos, i * 30.0f, tilts[i][0], tilts[i][1]));
                ring(q, swordPos.add(0, 0.04, 0), 0.28, 0.06, color, 120);
            }
        }

        if (now >= h.warnUntil && now < h.slashEndAt) {
            int phase = (int) Math.clamp((now - h.warnUntil) / slashIntervalMs, 0, TheaterTowerLiangyue.SKILL3_SWORDS - 1);
            int[] order = TheaterTowerLiangyue.stormOrder(h.seed);
            Vec3 from = center.add(TheaterTowerLiangyue.stormSwordOffset(order[phase]));
            Vec3 to = phase == TheaterTowerLiangyue.SKILL3_SWORDS - 1
                ? center
                : center.add(TheaterTowerLiangyue.stormSwordOffset(order[phase + 1]));
            RibbonTrail.ribbon(sp, List.of(to.add(0, 1.0, 0), from.lerp(to, 0.5).add(0, 1.0, 0), from.add(0, 1.0, 0)), camera, 0.34, 0xc8faff, 235);
            Vec3 actor = center.add(TheaterTowerLiangyue.stormActorOffset(h.seed, elapsed, deployMs, slashIntervalMs));
            Vec3 travel = to.subtract(from);
            if (travel.lengthSqr() > 1e-4) {
                travel = travel.normalize();
                EffectSprites.slashArc(sp, actor.add(0, 1.0, 0), travel, (phase % 3 - 1) * 35, 2.5, ((now - h.warnUntil) % slashIntervalMs) / (double) slashIntervalMs, color, 240);
                EffectSprites.glow(sp, actor.add(0, 1.0, 0), camera, 1.0, 0xffffff, 220);
            }
        }

        if (now >= h.slashEndAt && now < h.finisherAt) {
            double finishWarn = Math.clamp((now - h.slashEndAt) / (double) Math.max(1L, h.finisherAt - h.slashEndAt), 0.0, 1.0);
            double finishR = h.width > 0 ? h.width : h.radius;
            EffectSprites.warningCircle(sp, center.add(0, 0.08, 0), finishR, finishWarn, 0x7ce6ff, 230);
        }

        if (now >= h.finisherAt) {
            if (shaken.add(h.id)) CameraShake.impulse(center, 34, 0.65f, 180, 340);
            double finishProg = Math.clamp((now - h.finisherAt) / (double) Math.max(1L, h.endAt - h.finisherAt), 0.0, 0.999);
            double fade = Math.clamp(1.0 - finishProg, 0.0, 1.0);
            double finishR = h.width > 0 ? h.width : h.radius;
            Vec3 dir = horizontalDir(h.from, h.to);
            crescent(q, center.add(0, 1.6, 0), dir, side(dir), finishR, 1.05, color, 0xffffff, (int) (200 * fade));
            EffectSprites.slashArc(sp, center.add(0, 1.6, 0), dir, 0, finishR * 1.15, finishProg, 0xffffff, 250);
            EffectSprites.slashArc(sp, center.add(0, 1.6, 0), dir.scale(-1), 0, finishR * 1.15, finishProg, color, 245);
            EffectSprites.slashArc(sp, center.add(0, 1.8, 0), dir, 62, finishR * 0.95, finishProg, 0xd8fcff, 235);
            EffectSprites.shockwave(sp, center.add(0, 0.14, 0), finishR * 1.15 * EffectSprites.easeOutCubic(finishProg), finishProg * 1.8, 0xe8ffff, (int) (245 * fade));
            EffectSprites.impactBurst(sp, center.add(0, 1.4, 0), camera, finishR * 0.95, finishProg, color, 245, false);
        }
    }

    private static void ensureStormSwords(Hazard h) {
        if (stormStacks.containsKey(h.id)) return;
        var allowed = JobCatalog.weapons(JobCatalog.BASE);
        var all = new ArrayList<>(EquipmentVisualItems.weaponModels().stream()
            .filter(m -> allowed.contains(m.item().toUpperCase(Locale.ROOT)))
            .toList());
        if (all.isEmpty()) all.addAll(EquipmentVisualItems.weaponModels());
        SplittableRandom rng = new SplittableRandom(h.seed ^ 0x137BEA49L);
        for (int i = all.size() - 1; i > 0; i--) {
            Collections.swap(all, i, rng.nextInt(i + 1));
        }
        ItemStack[] stacks = new ItemStack[TheaterTowerLiangyue.SKILL3_SWORDS];
        float[][] tilts = new float[TheaterTowerLiangyue.SKILL3_SWORDS][2];
        for (int i = 0; i < TheaterTowerLiangyue.SKILL3_SWORDS; i++) {
            stacks[i] = EquipmentVisualItems.weaponModel(all.get(i % all.size()).id());
            tilts[i][0] = (float) rng.nextDouble(-28.0, 28.0);
            tilts[i][1] = (float) rng.nextDouble(0.0, 360.0);
        }
        stormStacks.put(h.id, stacks);
        stormModels.put(h.id, new ItemStackRenderState[TheaterTowerLiangyue.SKILL3_SWORDS]);
        stormTilts.put(h.id, tilts);
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
        Vec3 depth = (cross.lengthSqr() < 1e-6 ? UP : cross.normalize()).scale(0.09);
        int opacity = Math.clamp(alpha, 0, 255);
        int rim = Math.clamp(alpha + 25, 0, 255);
        for (int i = 0; i < 24; i++) {
            double a = -Math.PI / 2.0 + i * Math.PI / 24.0;
            double b = a + Math.PI / 24.0;
            Vec3 aa = p.add(u.scale(Math.cos(a) * radius)).add(v.scale(Math.sin(a) * radius));
            Vec3 bb = p.add(u.scale(Math.cos(b) * radius)).add(v.scale(Math.sin(b) * radius));
            Vec3 ia = aa.subtract(u.scale(thickness * Math.cos(a)));
            Vec3 ib = bb.subtract(u.scale(thickness * Math.cos(b)));
            Vec3 da = depth.scale(Math.cos(a));
            Vec3 db = depth.scale(Math.cos(b));
            out.add(new Quad(aa.add(da), bb.add(db), ib.add(db), ia.add(da), fill, opacity));
            out.add(new Quad(ia.subtract(da), ib.subtract(db), bb.subtract(db), aa.subtract(da), fill, opacity));
            out.add(new Quad(aa.subtract(da), bb.subtract(db), bb.add(db), aa.add(da), edge, rim));
            out.add(new Quad(ia.add(da), ib.add(db), ib.subtract(db), ia.subtract(da), edge, opacity));
        }
    }

    private static void dragonAura(List<Quad> out, Vec3 p, Vec3 dir, int color, int alpha) {
        Vec3 right = side(dir);
        for (int i = 0; i < 6; i++) {
            Vec3 a = p.subtract(dir.scale(i * 0.65)).add(right.scale(Math.sin(i * 1.6) * 0.45)).add(0, 1.05 - i * 0.08, 0);
            box(out, a, 0.28 - i * 0.03, 0.28 - i * 0.03, 0.28 - i * 0.03, color, Math.max(40, alpha - i * 28));
        }
        line(out, p.add(right.scale(0.45)).add(0, 1.05, 0), p.add(right.scale(0.85)).add(dir.scale(0.6)).add(0, 1.75, 0), 0.09, 0xd8fcff, alpha);
        line(out, p.subtract(right.scale(0.45)).add(0, 1.05, 0), p.subtract(right.scale(0.85)).add(dir.scale(0.6)).add(0, 1.75, 0), 0.09, 0xd8fcff, alpha);
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
        Vec3 camera = event.getLevelRenderState().cameraRenderState.pos;
        var pose = event.getPoseStack();
        for (StormSword sword : frame.swords()) {
            Vec3 p = sword.position();
            pose.pushPose();
            pose.translate(p.x - camera.x, p.y - camera.y + 0.65, p.z - camera.z);
            pose.mulPose(Axis.YP.rotationDegrees(sword.yaw()));
            double az = Math.toRadians(sword.azimuthDeg());
            pose.mulPose(new Quaternionf().rotationAxis((float) Math.toRadians(sword.tiltDeg()), (float) Math.cos(az), 0, (float) Math.sin(az)));
            pose.mulPose(Axis.ZP.rotationDegrees(180));
            pose.scale(1.35f, 1.35f, 1.35f);
            EffectModels.submit(sword.model(), pose, event.getSubmitNodeCollector(), 0xF000F0, OverlayTexture.NO_OVERLAY, frame.fade());
            pose.popPose();
        }
        WorldQuads.submit(event, frame.quads(), frame.fade());
        EffectSprites.submit(event, frame.sprites(), frame.fade());
    }
}
