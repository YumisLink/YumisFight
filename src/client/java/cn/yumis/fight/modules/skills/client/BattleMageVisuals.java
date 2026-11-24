package cn.yumis.fight.modules.skills.client;

import cn.yumis.fight.modules.presentation.api.client.ClientUi;
import cn.yumis.fight.modules.presentation.api.client.WorldQuads.Quad;
import cn.yumis.fight.modules.skills.api.*;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;
import java.util.*;

import static cn.yumis.fight.modules.skills.api.SkillVisualEvent.Phase.*;
import static java.util.Map.entry;

/**
 * Full-bright 3D world quads, orbiting 5-color faceted Chaser crystals,
 * Teana Battle Goddess golden wings & halo, spear arcs, dragon halberd beams, and skill sounds.
 */
final class BattleMageVisuals {
    private static final class Meta {
        int color = BattleMageRules.COLOR_MARTIAL;
        int mode = 0;
        int count = 1;
        int balls = 8;
        int hitIndex = 0;
        double radius = 3.5;
        double blastRadius = 5.0;
        double finisherRadius = 5.5;
        double range = 6.0;
        double width = 3.0;
        double arc = 210.0;
        boolean teana;
        boolean finisher;
        long updatedAt;
    }

    private static final Meta DEFAULT_META = new Meta();
    private static final Map<UUID, Meta> METAS = new LinkedHashMap<>();

    private static final Map<String, List<SkillSound>> SOUNDS = Map.ofEntries(
        entry(BattleMageSkills.CHASER_LAUNCHER, List.of(
            new SkillSound(START, SoundEvents.AMETHYST_BLOCK_CHIME, .45f, 1.55f),
            new SkillSound(FINISH, SoundEvents.FIREWORK_ROCKET_BLAST, .45f, 1.45f))),
        entry(BattleMageSkills.PALM_BLAST, List.of(
            new SkillSound(START, SoundEvents.PLAYER_ATTACK_SWEEP, .72f, 1.15f),
            new SkillSound(RELEASE, SoundEvents.GENERIC_EXPLODE.value(), .52f, 1.25f))),
        entry(BattleMageSkills.FULL_SWING, List.of(
            new SkillSound(START, SoundEvents.PLAYER_ATTACK_SWEEP, .82f, .88f),
            new SkillSound(RELEASE, SoundEvents.PLAYER_ATTACK_CRIT, .75f, 1.05f))),
        entry(BattleMageSkills.CHASER_FUSION, List.of(
            new SkillSound(START, SoundEvents.BEACON_POWER_SELECT, .65f, 1.25f),
            new SkillSound(FINISH, SoundEvents.GENERIC_EXPLODE.value(), .75f, 1.05f))),
        entry(BattleMageSkills.CHASER_PRESS, List.of(
            new SkillSound(START, SoundEvents.END_PORTAL_FRAME_FILL, .72f, .85f),
            new SkillSound(FINISH, SoundEvents.GENERIC_EXPLODE.value(), .95f, .72f))),
        entry(BattleMageSkills.PUNTO_WAVE, List.of(
            new SkillSound(HIT, SoundEvents.PLAYER_ATTACK_SWEEP, .35f, 1.50f),
            new SkillSound(FINISH, SoundEvents.PLAYER_ATTACK_KNOCKBACK, .80f, .92f))),
        entry(BattleMageSkills.DRAGON_STRIKE, List.of(
            new SkillSound(START, SoundEvents.BREEZE_SHOOT, .80f, .82f),
            new SkillSound(FINISH, SoundEvents.GENERIC_EXPLODE.value(), .92f, .82f))),
        entry(BattleMageSkills.DRAGON_SPEAR, List.of(
            new SkillSound(START, SoundEvents.TRIDENT_THUNDER.value(), .65f, 1.15f),
            new SkillSound(HIT, SoundEvents.FIREWORK_ROCKET_BLAST, .42f, 1.35f),
            new SkillSound(FINISH, SoundEvents.GENERIC_EXPLODE.value(), .98f, .68f))),
        entry(BattleMageSkills.TEANA_TRANSFORMATION, List.of(
            new SkillSound(START, SoundEvents.BEACON_ACTIVATE, .90f, 1.15f),
            new SkillSound(FINISH, SoundEvents.TRIDENT_THUNDER.value(), .95f, .88f)))
    );

    private BattleMageVisuals() {}

    static void install() {
        for (String id : BattleMageSkills.ALL) {
            SkillVisualRegistry.register(id, new SkillVisualRegistry.Handler() {
                @Override public long defaultDuration() { return BattleMageSkills.TEANA_TRANSFORMATION.equals(id) ? 2400L : 1400L; }
                @Override public int defaultColor() {
                    return switch (id) {
                        case BattleMageSkills.TEANA_TRANSFORMATION -> BattleMageRules.COLOR_TEANA;
                        case BattleMageSkills.DRAGON_SPEAR -> 0xFFB833;
                        case BattleMageSkills.CHASER_FUSION, BattleMageSkills.CHASER_PRESS -> BattleMageRules.COLOR_DARK;
                        default -> BattleMageRules.COLOR_MARTIAL;
                    };
                }
                @Override public boolean motion(SkillVfx.Track track, JsonObject j) {
                    Meta m = METAS.computeIfAbsent(track.start.cast(), k -> new Meta());
                    m.updatedAt = ClientUi.now();
                    if (j.has("color")) m.color = j.get("color").getAsInt();
                    if (j.has("mode")) m.mode = j.get("mode").getAsInt();
                    if (j.has("count")) m.count = j.get("count").getAsInt();
                    if (j.has("balls")) m.balls = j.get("balls").getAsInt();
                    if (j.has("hitIndex")) m.hitIndex = j.get("hitIndex").getAsInt();
                    if (j.has("radius")) m.radius = j.get("radius").getAsDouble();
                    if (j.has("blastRadius")) m.blastRadius = j.get("blastRadius").getAsDouble();
                    if (j.has("finisherRadius")) m.finisherRadius = j.get("finisherRadius").getAsDouble();
                    if (j.has("range")) m.range = j.get("range").getAsDouble();
                    if (j.has("width")) m.width = j.get("width").getAsDouble();
                    if (j.has("arc")) m.arc = j.get("arc").getAsDouble();
                    if (j.has("teana")) m.teana = j.get("teana").getAsBoolean();
                    if (j.has("finisher")) m.finisher = j.get("finisher").getAsBoolean();
                    return true;
                }
                @Override public void play(SkillVfx.Track track, SkillVisualEvent event) {
                    SkillSound.play(event, SOUNDS.getOrDefault(id, List.of()));
                }
                @Override public void render(SkillVfx.LevelRenderContext ctx, SkillVfx.Track track, long age, long time, List<Quad> out, Vec3 camera) {
                    draw(id, track, age, time, out);
                }
            });
        }
    }

    /** Renders orbiting 5-color Chaser crystals + Teana golden wings/halo for all visible Battle Mages. */
    static void actors(List<Quad> out, long time) {
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        for (var actor : CareerClient.INSTANCE.view.actors) {
            long packed = actor.battleMageChasers;
            boolean teana = actor.teanaUntil > time;
            if (packed == 0L && !teana) continue;
            var player = mc.level.getPlayerByUUID(UUID.fromString(actor.owner));
            if (player == null || !player.isAlive()) continue;
            Vec3 base = PlayerVisualAnchor.position(player);
            Vec3 backCenter = base.add(0, 1.25, 0);

            // Count packed chasers (up to 10 slots, 3 bits each)
            int count = 0;
            int[] types = new int[10];
            for (int i = 0; i < 10; i++) {
                int code = (int) ((packed >>> (i * 3)) & 0x7L);
                if (code >= 1 && code <= 5) {
                    types[count++] = code - 1;
                }
            }
            // Orbiting faceted diamond Chaser crystals in an inclined Battle Mage halo
            for (int i = 0; i < count; i++) {
                double angle = time * 0.0032 + i * Math.PI * 2.0 / Math.max(1, count);
                double rad = teana ? 0.95 : 0.82;
                double ox = Math.cos(angle) * rad;
                double oz = Math.sin(angle) * rad;
                double oy = Math.sin(angle + time * 0.001) * 0.24;
                Vec3 pos = backCenter.add(ox, oy, oz);
                int col = BattleMageRules.chaserColor(types[i], teana);
                double size = i == 0 ? 0.16 : 0.125;
                crystal(out, pos, size, col, 240, time * 0.006 + i);
                crystal(out, pos, size * 1.45, col, i == 0 ? 95 : 55, -time * 0.004 + i);
            }

            // Teana Battle Goddess golden wings, ground crest & glowing aura
            if (teana) {
                Vec3 look = new Vec3(player.getLookAngle().x, 0, player.getLookAngle().z);
                if (look.lengthSqr() < 1e-4) look = new Vec3(0, 0, 1);
                else look = look.normalize();
                Vec3 right = new Vec3(-look.z, 0, look.x);
                Vec3 wingBase = base.add(0, 1.35, 0).subtract(look.scale(0.32));
                double flutter = Math.sin(time * 0.007) * 0.12;
                for (int side : new int[]{-1, 1}) {
                    for (int f = 0; f < 3; f++) {
                        Vec3 tip = wingBase
                            .add(right.scale(side * (0.95 + f * 0.32)))
                            .add(0, 0.55 - f * 0.24 + flutter * (f + 1), 0)
                            .subtract(look.scale(0.28 + f * 0.14));
                        beam(out, wingBase.add(right.scale(side * 0.15)), tip, 0.11 - f * 0.02, BattleMageRules.COLOR_TEANA, 185);
                        beam(out, wingBase.add(right.scale(side * 0.15)), tip, 0.05, 0xffffff, 220);
                    }
                }
                ring(out, base.add(0, 0.06, 0), 1.15, 0.06, BattleMageRules.COLOR_TEANA, 170, time * 0.003);
                ring(out, base.add(0, 0.08, 0), 0.72, 0.035, 0xfff5b8, 150, -time * 0.005);
            }
        }
        // Charge dash direction telegraph for Palm Blast & Dragon Strike
        if (mc.player != null && BattleMageChargeClient.active()) {
            long now = ClientUi.now();
            double dist = BattleMageChargeClient.previewDashDistance(now);
            if (dist > 0.5) {
                Vec3 look = new Vec3(mc.player.getLookAngle().x, 0, mc.player.getLookAngle().z);
                if (look.lengthSqr() > 1e-4) {
                    look = look.normalize();
                    Vec3 from = mc.player.position().add(0, 0.12, 0);
                    Vec3 to = from.add(look.scale(dist));
                    boolean full = BattleMageChargeClient.isFull(now);
                    int col = full ? BattleMageRules.COLOR_TEANA : BattleMageRules.COLOR_FIRE;
                    beam(out, from, to, full ? 0.18 : 0.11, col, full ? 190 : 125);
                    ring(out, to, full ? 1.1 : 0.75, 0.06, col, full ? 200 : 135, time * 0.005);
                }
            }
        }
        // Periodic cleanup of stale metadata
        if (METAS.size() > 128) {
            long cutoff = ClientUi.now() - 25_000L;
            METAS.values().removeIf(m -> m.updatedAt < cutoff);
        }
    }

    private static void draw(String id, SkillVfx.Track t, long age, long time, List<Quad> out) {
        Meta m = METAS.getOrDefault(t.start.cast(), DEFAULT_META);
        int color = m.color != 0 ? m.color : t.start.color();
        Vec3 origin = SkillVfx.vector(t.start.position());
        Vec3 point = SkillVfx.vector(t.last.position());
        Vec3 dir = SkillVfx.vector(t.start.direction());
        if (dir.lengthSqr() < 1e-4) dir = new Vec3(0, 0, 1);
        else dir = dir.normalize();
        boolean finished = t.last.phase() == FINISH;
        long after = finished ? Math.max(0L, time - t.last.at()) : 0L;

        if (BattleMageSkills.CHASER_LAUNCHER.equals(id)) {
            if (!finished) {
                crystal(out, point, m.teana ? 0.28 : 0.22, color, 245, time * 0.015);
                beam(out, point.subtract(dir.scale(1.2)), point, 0.08, color, 175);
            } else {
                explosion(out, point, m.radius, color, after, 360L);
            }
            return;
        }

        if (BattleMageSkills.PALM_BLAST.equals(id)) {
            double u = Math.clamp(age / 420.0, 0.0, 1.0);
            double fade = 1.0 - u;
            Vec3 tip = point.add(dir.scale(1.6 * u));
            beam(out, origin, tip, 0.28 * fade, color, (int) (200 * fade));
            ring(out, point, m.radius * (0.4 + 0.6 * u), 0.14, color, (int) (210 * fade), time * 0.008);
            if (m.teana) ring(out, point, m.radius * 0.7, 0.08, 0xffffff, (int) (185 * fade), -time * 0.01);
            return;
        }

        if (BattleMageSkills.FULL_SWING.equals(id)) {
            double u = Math.clamp(age / 420.0, 0.0, 1.0);
            double fade = 1.0 - u * u;
            sweepArc(out, point, dir, m.radius, m.arc, color, (int) (225 * fade), u);
            if (m.teana || m.mode == 1) {
                sweepArc(out, point.add(0, 0.25, 0), dir, m.radius * 1.15, m.arc, 0xffffff, (int) (165 * fade), u);
            }
            return;
        }

        if (BattleMageSkills.CHASER_FUSION.equals(id)) {
            if (!finished) {
                double u = Math.clamp(age / 260.0, 0.0, 1.0);
                crystal(out, point, 0.35 + 0.45 * u, color, 230, time * 0.018);
                ring(out, point, m.radius * (1.0 - 0.5 * u), 0.08, color, 175, time * 0.01);
            } else {
                explosion(out, point, m.radius, color, after, 540L);
                ring(out, point.add(0, 0.1, 0), m.radius * 1.15, 0.12, 0xffffff, (int) (190 * Math.max(0, 1.0 - after / 540.0)), -time * 0.008);
            }
            return;
        }

        if (BattleMageSkills.CHASER_PRESS.equals(id)) {
            if (!finished) {
                double u = Math.clamp(age / 300.0, 0.0, 1.0);
                ring(out, point.add(0, 0.08, 0), m.radius * u, 0.14, color, 200, time * 0.012);
                crystal(out, point.add(0, 0.9 - 0.7 * u, 0), 0.45 + 0.08 * m.count, color, 240, time * 0.02);
            } else {
                explosion(out, point, m.radius, color, after, 650L);
                double fade = Math.max(0.0, 1.0 - after / 650.0);
                column(out, point, m.radius * 0.45, 3.8 * fade, color, (int) (135 * fade));
            }
            return;
        }

        if (BattleMageSkills.PUNTO_WAVE.equals(id)) {
            if (!finished) {
                int idx = m.hitIndex;
                double lateral = ((idx % 5) - 2) * 0.42;
                double vert = ((idx % 3) - 1) * 0.25;
                Vec3 right = new Vec3(-dir.z, 0, dir.x);
                Vec3 stabFrom = point.subtract(dir.scale(m.range * 0.45)).add(right.scale(lateral)).add(0, vert, 0);
                Vec3 stabTo = stabFrom.add(dir.scale(m.range));
                beam(out, stabFrom, stabTo, 0.14, color, 220);
                beam(out, stabFrom, stabTo, 0.06, 0xffffff, 240);
            } else {
                double u = Math.clamp(after / 420.0, 0.0, 1.0);
                double fade = 1.0 - u;
                sweepArc(out, point, dir, m.finisherRadius, 240.0, color, (int) (235 * fade), u);
            }
            return;
        }

        if (BattleMageSkills.DRAGON_STRIKE.equals(id)) {
            if (!finished) {
                double u = Math.clamp(age / 320.0, 0.0, 1.0);
                Vec3 head = origin.add(dir.scale(m.range * u));
                beam(out, origin, head, 0.42, color, 215);
                beam(out, origin, head, 0.18, 0xfff0b0, 235);
                ring(out, head, 1.6, 0.12, color, 200, time * 0.015);
            } else {
                explosion(out, point, m.blastRadius, color, after, 540L);
            }
            return;
        }

        if (BattleMageSkills.DRAGON_SPEAR.equals(id)) {
            Vec3 tip = origin.add(dir.scale(Math.min(4.8, m.range * 0.65)));
            Vec3 end = origin.add(dir.scale(m.range));
            Vec3 right = new Vec3(-dir.z, 0, dir.x);
            if (right.lengthSqr() > 1e-6) right = right.normalize();
            if (!finished) {
                // Glowing Dragon Halberd shaft & oversized crescent blade
                beam(out, origin, end, 0.22, 0xFFB833, 230);
                beam(out, origin, end, 0.09, 0xFFF8D0, 250);
                Vec3 bladeBase = origin.add(dir.scale(m.range * 0.56));
                Vec3 bladeMid = origin.add(dir.scale(m.range * 0.80)).add(right.scale(1.15)).add(0, 0.35, 0);
                Vec3 bladeTip = end.add(0, 0.15, 0);
                out.add(new Quad(bladeBase, bladeMid, bladeTip, bladeBase.add(dir.scale(0.6)), 0xFFD24D, 225));
                out.add(new Quad(bladeTip, bladeMid, bladeBase, bladeBase.add(dir.scale(0.6)), 0xFFD24D, 225));
                Vec3 bladeMid2 = origin.add(dir.scale(m.range * 0.78)).add(right.scale(-0.75)).add(0, -0.20, 0);
                out.add(new Quad(bladeBase, bladeMid2, bladeTip, bladeBase.add(dir.scale(0.5)), 0xFF8C24, 195));
                out.add(new Quad(bladeTip, bladeMid2, bladeBase, bladeBase.add(dir.scale(0.5)), 0xFF8C24, 195));
                // Coiling golden dragon aura & streaming Dragon Chasers along the halberd
                for (int s = 0; s < 5; s++) {
                    double frac = ((time * 0.0022 + s * 0.20) % 1.0);
                    double ang = time * 0.012 + s * 1.4;
                    Vec3 orbPos = origin.add(dir.scale(m.range * (0.15 + 0.75 * frac)))
                        .add(right.scale(Math.cos(ang) * 0.75))
                        .add(0, Math.sin(ang) * 0.55, 0);
                    int orbCol = BattleMageRules.chaserColor(s, s % 2 == 0);
                    crystal(out, orbPos, 0.28, orbCol, 240, time * 0.018 + s);
                }
                ring(out, tip, 2.3, 0.12, color, 200, time * 0.012);
                ring(out, tip.add(0, 0.4, 0), 1.5, 0.07, 0xFFD700, 185, -time * 0.016);
            } else {
                explosion(out, point, m.blastRadius, 0xFFB833, after, 680L);
                double fade = Math.max(0.0, 1.0 - after / 680.0);
                column(out, point.subtract(0, 1.0, 0), m.blastRadius * 0.45, 7.2 * fade, BattleMageRules.COLOR_TEANA, (int) (180 * fade));
                ring(out, point.add(0, 0.2, 0), m.blastRadius * (0.6 + 0.5 * (1.0 - fade)), 0.16, 0xFF8C24, (int) (210 * fade), -after * 0.01);
            }
            return;
        }

        if (BattleMageSkills.TEANA_TRANSFORMATION.equals(id)) {
            if (m.finisher) {
                if (!finished) {
                    Vec3 tip = origin.add(dir.scale(m.range));
                    beam(out, origin, tip, 0.45, BattleMageRules.COLOR_TEANA, 210);
                    beam(out, origin, tip, 0.18, 0xffffff, 245);
                    ring(out, origin, 2.6, 0.14, BattleMageRules.COLOR_TEANA, 195, time * 0.012);
                } else {
                    explosion(out, point, m.radius, BattleMageRules.COLOR_TEANA, after, 820L);
                    double fade = Math.max(0.0, 1.0 - after / 820.0);
                    column(out, point.subtract(0, 1.1, 0), m.radius * 0.35, 9.0 * fade, 0xfff4b8, (int) (185 * fade));
                }
            } else {
                double u = Math.clamp(age / 950.0, 0.0, 1.0);
                double fade = 1.0 - u;
                column(out, origin.subtract(0, 1.0, 0), m.radius * (0.25 + 0.35 * u), 7.5 * fade, BattleMageRules.COLOR_TEANA, (int) (175 * fade));
                ring(out, origin.subtract(0, 0.85, 0), m.radius * (0.3 + 0.7 * u), 0.18, BattleMageRules.COLOR_TEANA, (int) (220 * fade), time * 0.008);
            }
        }
    }

    // --- Primitive WorldQuad builders ---

    private static void crystal(List<Quad> out, Vec3 center, double r, int rgb, int alpha, double rot) {
        if (alpha <= 0 || r <= 0) return;
        int a = Math.clamp(alpha, 0, 255);
        int col = rgb & 0xffffff;
        Vec3 top = center.add(0, r * 1.35, 0);
        Vec3 bot = center.add(0, -r * 1.35, 0);
        Vec3[] mid = new Vec3[4];
        for (int i = 0; i < 4; i++) {
            double ang = rot + i * Math.PI * 0.5;
            mid[i] = center.add(Math.cos(ang) * r, 0, Math.sin(ang) * r);
        }
        for (int i = 0; i < 4; i++) {
            Vec3 p0 = mid[i], p1 = mid[(i + 1) % 4];
            out.add(new Quad(p0, p1, top, top, col, a));
            out.add(new Quad(p1, p0, bot, bot, col, a));
        }
    }

    private static void sweepArc(List<Quad> out, Vec3 center, Vec3 dir, double radius, double arcDeg, int rgb, int alpha, double progress) {
        if (alpha <= 0 || radius <= 0) return;
        int a = Math.clamp(alpha, 0, 255);
        int col = rgb & 0xffffff;
        double baseYaw = Math.atan2(dir.z, dir.x);
        double half = Math.toRadians(Math.clamp(arcDeg, 60.0, 360.0) * 0.5);
        int segs = 18;
        double inner = radius * 0.38;
        double outer = radius * (0.72 + 0.28 * Math.sin(progress * Math.PI * 0.5));
        for (int i = 0; i < segs; i++) {
            double a0 = baseYaw - half + (2.0 * half) * i / segs;
            double a1 = baseYaw - half + (2.0 * half) * (i + 1) / segs;
            Vec3 p0 = center.add(Math.cos(a0) * inner, 0, Math.sin(a0) * inner);
            Vec3 p1 = center.add(Math.cos(a1) * inner, 0, Math.sin(a1) * inner);
            Vec3 p2 = center.add(Math.cos(a1) * outer, 0.08, Math.sin(a1) * outer);
            Vec3 p3 = center.add(Math.cos(a0) * outer, 0.08, Math.sin(a0) * outer);
            out.add(new Quad(p0, p1, p2, p3, col, a));
            out.add(new Quad(p3, p2, p1, p0, col, a));
        }
    }

    private static void ring(List<Quad> out, Vec3 center, double radius, double width, int rgb, int alpha, double phase) {
        if (alpha <= 0 || radius <= 0) return;
        int a = Math.clamp(alpha, 0, 255);
        int col = rgb & 0xffffff;
        int segs = 24;
        double inner = Math.max(0.02, radius - width * 0.5), outer = radius + width * 0.5;
        for (int i = 0; i < segs; i++) {
            double a0 = phase + i * Math.PI * 2.0 / segs, a1 = phase + (i + 1) * Math.PI * 2.0 / segs;
            Vec3 p0 = center.add(Math.cos(a0) * inner, 0, Math.sin(a0) * inner);
            Vec3 p1 = center.add(Math.cos(a1) * inner, 0, Math.sin(a1) * inner);
            Vec3 p2 = center.add(Math.cos(a1) * outer, 0, Math.sin(a1) * outer);
            Vec3 p3 = center.add(Math.cos(a0) * outer, 0, Math.sin(a0) * outer);
            out.add(new Quad(p0, p1, p2, p3, col, a));
            out.add(new Quad(p3, p2, p1, p0, col, a));
        }
    }

    private static void column(List<Quad> out, Vec3 base, double radius, double height, int rgb, int alpha) {
        if (alpha <= 0 || radius <= 0 || height <= 0) return;
        int a = Math.clamp(alpha, 0, 255);
        int col = rgb & 0xffffff;
        int sides = 10;
        for (int i = 0; i < sides; i++) {
            double a0 = i * Math.PI * 2.0 / sides, a1 = (i + 1) * Math.PI * 2.0 / sides;
            Vec3 b0 = base.add(Math.cos(a0) * radius, 0, Math.sin(a0) * radius);
            Vec3 b1 = base.add(Math.cos(a1) * radius, 0, Math.sin(a1) * radius);
            out.add(new Quad(b0, b1, b1.add(0, height, 0), b0.add(0, height, 0), col, a));
        }
    }

    private static void beam(List<Quad> out, Vec3 from, Vec3 to, double halfWidth, int rgb, int alpha) {
        if (alpha <= 0 || halfWidth <= 0) return;
        Vec3 delta = to.subtract(from);
        if (delta.lengthSqr() < 1e-6) return;
        Vec3 up = Math.abs(delta.normalize().y) > 0.95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 right = delta.cross(up).normalize().scale(halfWidth);
        Vec3 perp = delta.cross(right).normalize().scale(halfWidth);
        int a = Math.clamp(alpha, 0, 255);
        int col = rgb & 0xffffff;
        out.add(new Quad(from.subtract(right), from.add(right), to.add(right), to.subtract(right), col, a));
        out.add(new Quad(from.subtract(perp), from.add(perp), to.add(perp), to.subtract(perp), col, a));
    }

    private static void explosion(List<Quad> out, Vec3 center, double maxRadius, int rgb, long after, long duration) {
        double u = Math.clamp(after / (double) Math.max(1L, duration), 0.0, 1.0);
        double fade = 1.0 - u;
        if (fade <= 0.01) return;
        double r = maxRadius * (0.3 + 0.7 * Math.sin(u * Math.PI * 0.5));
        ring(out, center, r, 0.18, rgb, (int) (215 * fade), after * 0.006);
        ring(out, center.add(0, 0.35, 0), r * 0.65, 0.10, 0xffffff, (int) (200 * fade * fade), -after * 0.009);
        crystal(out, center, r * 0.42, rgb, (int) (180 * fade), after * 0.012);
    }
}
