package cn.yumis.fight.modules.skills.client;

import cn.yumis.fight.modules.presentation.api.client.ClientUi;
import cn.yumis.fight.modules.presentation.api.client.WorldQuads.Quad;
import cn.yumis.fight.modules.skills.api.*;
import com.google.gson.JsonObject;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;
import java.util.*;

import static cn.yumis.fight.modules.skills.api.SkillVisualEvent.Phase.*;
import static java.util.Map.entry;

/** Full-bright 3D world quads, orbiting 4-element rune lamps, charge targeting rings, sounds and Astral Storm camera. */
final class ElementalistVisuals {
    static final long FLAME_ERUPTION_MS = 700L;
    static final long ARCTIC_FINISH_MS = 650L;
    static final long ASTRAL_FINISH_MS = 950L;
    private static final long ICE_PILLAR_LIFETIME_MS = 460L;
    private static final long METEOR_IMPACT_MS = 520L;
    private static final SkillSound ARCTIC_WAVE_SOUND = new SkillSound(HIT, SoundEvents.AMETHYST_BLOCK_RESONATE, .65f, 1.35f);

    private record FallingMeteor(Vec3 impact, long startedAt, int color, int sequence) {}

    private static final class Meta {
        int element = -1;
        int color = ElementalistRules.COLOR_PRISM;
        int morphMode = 0;
        double radius = 3.5;
        double blastRadius = 5.0;
        double range = 12.0;
        double width = 4.0;
        double wallHeight = 2.2;
        int wallSegments = 12;
        int orbCount = 4;
        boolean follow;
        boolean stationary;
        boolean cloud;
        boolean volcano;
        boolean cinematic;
        long fallMs = 450L;
        long impactDelayMs = 200L;
        long firstWaveMs = 120L;
        long waveIntervalMs = 220L;
        int waveCount = 8;
        long nextWaveSound;
        double fieldRadius = 10.0;
        double meteorRadius = 4.2;
        double finisherRadius = 11.0;
        final List<FallingMeteor> meteors = new ArrayList<>();
        long updatedAt;
    }

    private static final Meta DEFAULT_META = new Meta();
    private static final Map<UUID, Meta> METAS = new LinkedHashMap<>();

    private static final Map<String, List<SkillSound>> SOUNDS = Map.ofEntries(
        entry(ElementalistSkills.ICE_WALL, List.of(
            new SkillSound(START, SoundEvents.GLASS_PLACE, .65f, 1.30f),
            new SkillSound(HIT, SoundEvents.AMETHYST_BLOCK_CHIME, .45f, 1.50f),
            new SkillSound(FINISH, SoundEvents.GLASS_BREAK, .75f, .90f))),
        entry(ElementalistSkills.LIGHTNING_VORTEX, List.of(
            new SkillSound(START, SoundEvents.BREEZE_SHOOT, .55f, 1.45f),
            new SkillSound(FINISH, SoundEvents.LIGHTNING_BOLT_IMPACT, .45f, 1.55f))),
        entry(ElementalistSkills.VOID_SPHERE, List.of(
            new SkillSound(START, SoundEvents.END_PORTAL_FRAME_FILL, .62f, .82f),
            new SkillSound(FINISH, SoundEvents.GENERIC_EXPLODE.value(), .55f, .95f))),
        entry(ElementalistSkills.FLAME_PILLAR, List.of(
            new SkillSound(START, SoundEvents.FIRECHARGE_USE, .58f, .95f),
            new SkillSound(HIT, SoundEvents.BLAZE_SHOOT, .72f, .85f))),
        entry(ElementalistSkills.HALLOWEEN_BUSTER, List.of(
            new SkillSound(START, SoundEvents.FIRECHARGE_USE, .70f, .72f),
            new SkillSound(HIT, SoundEvents.GENERIC_EXPLODE.value(), .85f, .82f))),
        entry(ElementalistSkills.ARCTIC_FEAST, List.of(
            ARCTIC_WAVE_SOUND,
            new SkillSound(FINISH, SoundEvents.GLASS_BREAK, .80f, .78f))),
        entry(ElementalistSkills.BLACK_HOLE, List.of(
            new SkillSound(START, SoundEvents.BEACON_ACTIVATE, .70f, .62f),
            new SkillSound(FINISH, SoundEvents.GENERIC_EXPLODE.value(), .88f, .68f))),
        entry(ElementalistSkills.LIGHTNING_STRIKE, List.of(
            new SkillSound(START, SoundEvents.BEACON_POWER_SELECT, .60f, 1.35f),
            new SkillSound(HIT, SoundEvents.LIGHTNING_BOLT_THUNDER, .72f, 1.15f))),
        entry(ElementalistSkills.ASTRAL_STORM, List.of(
            new SkillSound(START, SoundEvents.BEACON_ACTIVATE, .85f, .78f),
            new SkillSound(HIT, SoundEvents.FIREWORK_ROCKET_BLAST, .55f, .88f),
            new SkillSound(FINISH, SoundEvents.GENERIC_EXPLODE.value(), 1.0f, .62f)))
    );

    private ElementalistVisuals() {}

    static void install() {
        for (String id : ElementalistSkills.ALL) {
            boolean isStorm = ElementalistSkills.ASTRAL_STORM.equals(id);
            boolean isArctic = ElementalistSkills.ARCTIC_FEAST.equals(id);
            SkillVisualRegistry.register(id, new SkillVisualRegistry.Handler() {
                @Override public long defaultDuration() { return isStorm ? 4900L : 2600L; }
                @Override public long finishedUntil(SkillVfx.Track track, SkillVisualEvent event) {
                    if (isStorm) return event.at() + ASTRAL_FINISH_MS;
                    if (isArctic) return event.at() + ARCTIC_FINISH_MS;
                    return SkillVisualRegistry.Handler.super.finishedUntil(track, event);
                }
                @Override public int defaultColor() {
                    return ElementalistRules.elementColor(ElementalistSkills.innateElement(id));
                }
                @Override public int handOrbColor() {
                    int e = ElementalistSkills.innateElement(id);
                    return e >= 0 ? ElementalistRules.elementColor(e) : ElementalistRules.COLOR_PRISM;
                }
                @Override public boolean cinematic() { return isStorm; }
                @Override public boolean cinematicFromFirstPerson() { return isStorm; }
                @Override public boolean restoreCamera() { return isStorm; }
                @Override public CameraType cameraType(SkillVfx.Track track, long time) {
                    return CameraType.THIRD_PERSON_BACK;
                }
                @Override public SkillVfx.CameraPose camera(SkillVfx.Track track, long time) {
                    if (!isStorm) return null;
                    Meta m = METAS.get(track.start.cast());
                    if (m != null && !m.cinematic) return null;
                    var mc = Minecraft.getInstance();
                    if (mc.level == null) return null;
                    var entity = mc.level.getEntity(track.start.caster());
                    Vec3 focus = entity != null ? entity.position().add(0, 1.2, 0) : SkillVfx.vector(track.start.position()).add(0, 3.0, 0);
                    Vec3 dir = SkillVfx.vector(track.start.direction());
                    Vec3 back = new Vec3(dir.x, 0, dir.z).lengthSqr() < 1e-4 ? new Vec3(0, 0, -1) : new Vec3(-dir.x, 0, -dir.z).normalize();
                    Vec3 camPos = focus.add(back.scale(9.5)).add(0, 6.5, 0);
                    Vec3 look = focus.subtract(camPos).normalize();
                    float yaw = (float) Math.toDegrees(Math.atan2(-look.x, look.z));
                    float pitch = (float) Math.toDegrees(-Math.asin(Math.clamp(look.y, -1.0, 1.0)));
                    return new SkillVfx.CameraPose(camPos, yaw, pitch);
                }
                @Override public boolean motion(SkillVfx.Track track, JsonObject j) {
                    Meta m = METAS.computeIfAbsent(track.start.cast(), k -> new Meta());
                    m.updatedAt = ClientUi.now();
                    if (isStorm && track.last.phase() == HIT) {
                        long startedAt = track.last.at();
                        int color = j.has("color") ? j.get("color").getAsInt() : m.color;
                        m.meteors.removeIf(meteor -> startedAt - meteor.startedAt() >= m.fallMs + METEOR_IMPACT_MS);
                        if (m.meteors.size() >= 32) m.meteors.removeFirst();
                        m.meteors.add(new FallingMeteor(SkillVfx.vector(track.last.position()), startedAt, color, track.last.sequence()));
                        return true;
                    }
                    if (j.has("element")) m.element = j.get("element").getAsInt();
                    if (j.has("color")) m.color = j.get("color").getAsInt();
                    if (j.has("morphMode")) m.morphMode = j.get("morphMode").getAsInt();
                    if (j.has("radius")) m.radius = j.get("radius").getAsDouble();
                    if (j.has("blastRadius")) m.blastRadius = j.get("blastRadius").getAsDouble();
                    if (j.has("range")) m.range = j.get("range").getAsDouble();
                    if (j.has("width")) m.width = j.get("width").getAsDouble();
                    if (j.has("wallHeight")) m.wallHeight = j.get("wallHeight").getAsDouble();
                    if (j.has("wallSegments")) m.wallSegments = Math.max(6, (int) Math.round(j.get("wallSegments").getAsDouble()));
                    if (j.has("orbCount")) m.orbCount = Math.max(2, (int) Math.round(j.get("orbCount").getAsDouble()));
                    if (j.has("follow")) m.follow = j.get("follow").getAsBoolean();
                    if (j.has("stationary")) m.stationary = j.get("stationary").getAsBoolean();
                    if (j.has("cloud")) m.cloud = j.get("cloud").getAsBoolean();
                    if (j.has("volcano")) m.volcano = j.get("volcano").getAsBoolean();
                    if (j.has("cinematic")) m.cinematic = j.get("cinematic").getAsBoolean();
                    if (j.has("fallMs")) m.fallMs = Math.max(80L, j.get("fallMs").getAsLong());
                    if (j.has("impactDelayMs")) m.impactDelayMs = Math.max(50L, j.get("impactDelayMs").getAsLong());
                    if (j.has("firstWaveMs")) m.firstWaveMs = Math.max(0L, j.get("firstWaveMs").getAsLong());
                    if (j.has("waveIntervalMs")) m.waveIntervalMs = Math.max(50L, j.get("waveIntervalMs").getAsLong());
                    if (j.has("waveCount")) m.waveCount = Math.max(0, j.get("waveCount").getAsInt());
                    if (j.has("fieldRadius")) m.fieldRadius = j.get("fieldRadius").getAsDouble();
                    if (j.has("meteorRadius")) m.meteorRadius = j.get("meteorRadius").getAsDouble();
                    if (j.has("finisherRadius")) m.finisherRadius = j.get("finisherRadius").getAsDouble();
                    if (METAS.size() > 512) METAS.remove(METAS.keySet().iterator().next());
                    return true;
                }
                @Override public void play(SkillVfx.Track track, SkillVisualEvent event) {
                    SkillSound.play(event, SOUNDS.getOrDefault(id, List.of()));
                }
                @Override public void tick(SkillVfx.Track track, long time) {
                    if (isArctic) playArcticWaves(track, time);
                }
                @Override public void render(SkillVfx.LevelRenderContext ctx, SkillVfx.Track track, long age, long time, List<Quad> out, Vec3 camera) {
                    draw(id, track, age, time, out);
                }
            });
        }
    }

    /** Renders orbiting 4-element head lamps for all visible Elementalists + own live charge/channel rings. */
    static void actors(List<Quad> out, long time) {
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        for (var actor : CareerClient.INSTANCE.view.actors) {
            int mask = actor.elementLampsMask;
            if (mask == 0) continue;
            var player = mc.level.getPlayerByUUID(UUID.fromString(actor.owner));
            if (player == null || !player.isAlive()) continue;
            Vec3 center = PlayerVisualAnchor.position(player).add(0, player.getBbHeight() + 0.48, 0);
            boolean full4 = (mask & 0xF) == 0xF;
            int count = Integer.bitCount(mask & 0xF);
            int idx = 0;
            for (int e = 0; e < 4; e++) {
                if ((mask & (1 << e)) == 0) continue;
                double angle = time * 0.0022 + idx * Math.PI * 2.0 / Math.max(1, count);
                Vec3 pos = center.add(Math.cos(angle) * 0.52, Math.sin(angle * 2.0 + e) * 0.08, Math.sin(angle) * 0.52);
                int col = ElementalistRules.elementColor(e);
                sphere(out, pos, full4 ? 0.13 : 0.105, col, 235, 10, 5);
                sphere(out, pos, full4 ? 0.19 : 0.15, col, full4 ? 80 : 45, 10, 5);
                idx++;
            }
            if (full4) {
                ring(out, center, 0.52, 0.024, ElementalistRules.COLOR_PRISM, 110, time * 0.003);
            }
        }
        if (mc.player != null && ElementalistChargeClient.active() && ElementalistChargeClient.showsGroundRing()) {
            long now = ClientUi.now();
            double r = ElementalistChargeClient.previewRadius(now);
            if (r > 0.2) {
                Vec3 aim = ElementalistClient.aimGround(mc.player, ElementalistChargeClient.previewRange(), 5.0);
                boolean full = ElementalistChargeClient.isFull(now);
                int color = full ? 0xf5d061 : ElementalistRules.elementColor(ElementalistChargeClient.effectiveElement());
                ring(out, aim.add(0, 0.04, 0), r, full ? 0.08 : 0.05, color, full ? 210 : 155, time * 0.002);
                ring(out, aim.add(0, 0.04, 0), r * 0.45, 0.03, color, 110, -time * 0.003);
            }
        }
        var ch = ElementalistClient.activeLightningChannel();
        if (ch != null) {
            ring(out, ch.reticle.add(0, 0.05, 0), ch.radius, 0.09, ElementalistRules.COLOR_LIGHT, 215, time * 0.004);
            ring(out, ch.reticle.add(0, 0.05, 0), ch.radius * 0.55, 0.045, 0xffffff, 175, -time * 0.006);
        }
    }

    private static void draw(String id, SkillVfx.Track t, long age, long time, List<Quad> out) {
        Meta m = METAS.getOrDefault(t.start.cast(), DEFAULT_META);
        int color = m.color != 0 ? m.color : t.start.color();
        Vec3 origin = SkillVfx.vector(t.start.position());
        Vec3 point = SkillVfx.vector(t.last.position());
        Vec3 dir = SkillVfx.vector(t.start.direction());
        boolean finished = t.last.phase() == FINISH;
        long after = finished ? Math.max(0L, time - t.last.at()) : 0L;

        if (ElementalistSkills.ICE_WALL.equals(id)) {
            if (m.morphMode == 1) {
                double u = Math.clamp(age / 650.0, 0.0, 1.0);
                Vec3 head = origin.add(dir.scale(m.range * u));
                double fade = 1.0 - u * 0.7;
                ring(out, head.add(0, 0.25, 0), m.width * 0.5, 0.14, color, (int) (210 * fade), time * 0.004);
                column(out, head.add(0, 0.05, 0), m.width * 0.32, 1.9 * fade, color, (int) (145 * fade));
            } else if (!finished) {
                double grow = Math.clamp(age / 160.0, 0.15, 1.0);
                int segs = m.wallSegments;
                double h = m.wallHeight * grow;
                for (int i = 0; i < segs; i++) {
                    double a = i * Math.PI * 2.0 / segs;
                    Vec3 pillar = origin.add(Math.cos(a) * m.radius, 0.02, Math.sin(a) * m.radius);
                    column(out, pillar, 0.28, h, color, 175);
                    column(out, pillar.add(0, 0.1, 0), 0.12, h * 0.9, 0xe8fbff, 210);
                }
                ring(out, origin.add(0, 0.05, 0), m.radius, 0.08, color, 180, time * 0.001);
                ring(out, origin.add(0, h, 0), m.radius, 0.06, 0xd8f6ff, 145, -time * 0.0015);
                if (m.morphMode == 2) {
                    ring(out, origin.add(0, h * 0.5, 0), m.radius + 0.18, 0.05, ElementalistRules.COLOR_LIGHT, 175, time * 0.007);
                }
            } else {
                explosion(out, point, m.radius, color, after, 600L);
            }
            return;
        }

        if (ElementalistSkills.LIGHTNING_VORTEX.equals(id)) {
            if (m.morphMode == 1) {
                double fade = Math.clamp(1.0 - age / 650.0, 0.0, 1.0);
                Vec3 end = origin.add(dir.scale(m.range));
                beam(out, origin, end, 0.22 * fade, color, (int) (220 * fade));
                beam(out, origin, end, 0.09 * fade, 0xffffff, (int) (245 * fade));
                explosion(out, end, m.radius, color, age, 650L);
                return;
            }
            Vec3 center = origin;
            if (m.follow && Minecraft.getInstance().level != null) {
                var caster = Minecraft.getInstance().level.getEntity(t.start.caster());
                if (caster != null) center = caster.position();
            }
            double fade = finished ? Math.clamp(1.0 - after / 300.0, 0.0, 1.0) : 1.0;
            ring(out, center.add(0, 0.9, 0), m.radius * 0.72, 0.07, color, (int) (185 * fade), time * 0.009);
            for (int i = 0; i < m.orbCount; i++) {
                double a = time * 0.011 + i * Math.PI * 2.0 / m.orbCount;
                Vec3 orb = center.add(Math.cos(a) * m.radius * 0.72, 0.95 + Math.sin(a * 2.0) * 0.2, Math.sin(a) * m.radius * 0.72);
                sphere(out, orb, 0.34, color, (int) (230 * fade), 12, 6);
                sphere(out, orb, 0.18, 0xffffff, (int) (250 * fade), 8, 4);
            }
            return;
        }

        if (ElementalistSkills.VOID_SPHERE.equals(id)) {
            if (finished) {
                explosion(out, point, m.morphMode == 1 ? m.radius : m.blastRadius, color, after, 600L);
                return;
            }
            double travel = m.stationary ? 0.0 : Math.min(12.0, (age / 1000.0) * 3.0);
            Vec3 pos = origin.add(dir.scale(travel));
            sphere(out, pos, m.radius * 0.42, 0x1a0b2e, 240, 18, 9);
            sphere(out, pos, m.radius * 0.56, color, 130, 16, 8);
            ring(out, pos, m.radius * 0.95, 0.07, color, 195, time * 0.008);
            ring(out, pos, m.radius * 0.68, 0.04, 0xdfc2ff, 160, -time * 0.012);
            return;
        }

        if (ElementalistSkills.FLAME_PILLAR.equals(id)) {
            if (age < m.impactDelayMs && t.last.phase() == START) {
                double u = age / (double) Math.max(1L, m.impactDelayMs);
                ring(out, origin.add(0, 0.05, 0), m.radius, 0.08, m.morphMode == 1 ? ElementalistRules.COLOR_ICE : color, 200, time * 0.005);
                ring(out, origin.add(0, 0.05, 0), m.radius * u, 0.06, 0xffe49c, 165, -time * 0.006);
            }
            for (var event : t.flashes) {
                long eruptionAge = time - event.at();
                if (event.phase() != HIT || eruptionAge < 0 || eruptionAge >= FLAME_ERUPTION_MS) continue;
                double fade = 1.0 - eruptionAge / (double) FLAME_ERUPTION_MS;
                Vec3 base = SkillVfx.vector(event.position()).add(0, 0.02, 0);
                column(out, base, m.radius * 0.62, 6.5 * fade + 1.0, color, (int) (185 * fade));
                column(out, base, m.radius * 0.28, 7.2 * fade + 0.5, 0xfff0b5, (int) (225 * fade));
            }
            return;
        }

        if (ElementalistSkills.HALLOWEEN_BUSTER.equals(id)) {
            if (age < m.fallMs) {
                double u = Math.clamp(age / (double) Math.max(1L, m.fallMs), 0.0, 1.0);
                Vec3 startPos = origin.add(-3.0, 14.0, -2.0);
                Vec3 pos = startPos.lerp(origin.add(0, 1.1, 0), u * u);
                double r = m.morphMode == 1 ? 1.85 : 1.35;
                sphere(out, pos, r, color, 235, 18, 9);
                sphere(out, pos, r * 0.65, 0xffe588, 250, 14, 7);
                ring(out, origin.add(0, 0.05, 0), m.radius, 0.09, color, 185, time * 0.004);
            } else {
                long impAge = age - m.fallMs;
                explosion(out, point.add(0, 0.5, 0), m.radius, color, impAge, 850L);
                double waveFade = Math.clamp(1.0 - impAge / 650.0, 0.0, 1.0);
                if (waveFade > 0.02) {
                    double expand = m.radius + (m.blastRadius - m.radius) * Math.min(1.0, impAge / 280.0);
                    ring(out, point.add(0, 0.18, 0), expand, 0.20, 0xffc66e, (int) (210 * waveFade), time * 0.003);
                }
            }
            return;
        }

        if (ElementalistSkills.ARCTIC_FEAST.equals(id)) {
            double fade = finished ? Math.clamp(1.0 - after / (double) ARCTIC_FINISH_MS, 0.0, 1.0) : 1.0;
            ring(out, origin.add(0, 0.05, 0), m.radius, 0.10, color, (int) (195 * fade), time * 0.002);
            ring(out, origin.add(0, 0.05, 0), m.radius * 0.62, 0.06, 0xe2f9ff, (int) (160 * fade), -time * 0.003);
            if (m.morphMode == 1) {
                for (var event : t.flashes) {
                    if (event.phase() != HIT) continue;
                    double grow = Math.clamp((time - event.at()) / 100.0, 0.0, 1.0);
                    icePillar(out, origin.add(0, 0.05, 0), m.radius * 0.38, 7.0 * grow * fade, 0.0, color, (int) (210 * fade));
                    break;
                }
            } else {
                arcticPillars(out, origin, m, color, t.start.seed(), age, arcticWaveAge(t, age));
            }
            if (finished) explosion(out, origin.add(0, 0.6, 0), m.radius, color, after, ARCTIC_FINISH_MS);
            return;
        }

        if (ElementalistSkills.BLACK_HOLE.equals(id)) {
            if (!finished) {
                sphere(out, origin, 1.25, 0x0b0514, 250, 22, 11);
                sphere(out, origin, 1.65, color, 115, 18, 9);
                ring(out, origin, m.radius * 0.85, 0.16, color, 200, time * 0.007);
                ring(out, origin, m.radius * 1.35, 0.07, 0xd8b4ff, 130, -time * 0.005);
                if (m.volcano) {
                    ring(out, origin.add(0, -1.6, 0), m.radius * 0.9, 0.14, ElementalistRules.COLOR_FIRE, 185, time * 0.006);
                }
            } else {
                explosion(out, point, m.blastRadius, color, after, 750L);
            }
            return;
        }

        if (ElementalistSkills.LIGHTNING_STRIKE.equals(id)) {
            if (t.start.phase() == HIT) {
                double fade = Math.clamp(1.0 - age / 600.0, 0.0, 1.0);
                column(out, origin.add(0, 0.02, 0), 0.42 * fade + 0.08, 18.0, color, (int) (210 * fade));
                column(out, origin.add(0, 0.02, 0), 0.18 * fade + 0.04, 18.0, 0xffffff, (int) (250 * fade));
                ring(out, origin.add(0, 0.08, 0), m.radius * Math.min(1.0, age / 140.0 + 0.25), 0.15, color, (int) (210 * fade), time * 0.008);
            } else if (m.cloud) {
                double fade = finished ? Math.clamp(1.0 - after / 400.0, 0.0, 1.0) : 1.0;
                sphere(out, point, 1.9, 0x3b445e, (int) (185 * fade), 14, 7);
                ring(out, point, 2.5, 0.09, color, (int) (165 * fade), time * 0.005);
            }
            return;
        }

        if (ElementalistSkills.ASTRAL_STORM.equals(id)) {
            double fade = finished ? Math.clamp(1.0 - after / (double) ASTRAL_FINISH_MS, 0.0, 1.0) : 1.0;
            ring(out, origin.add(0, 0.06, 0), m.fieldRadius, 0.16, ElementalistRules.COLOR_PRISM, (int) (195 * fade), time * 0.0014);
            ring(out, origin.add(0, 0.06, 0), m.fieldRadius * 0.68, 0.09, ElementalistRules.COLOR_LIGHT, (int) (150 * fade), -time * 0.0022);
            ring(out, origin.add(0, 0.06, 0), m.fieldRadius * 0.36, 0.07, ElementalistRules.COLOR_ICE, (int) (145 * fade), time * 0.0035);
            for (var meteor : m.meteors) {
                drawMeteor(out, meteor, m, time);
            }
            if (finished) {
                explosion(out, origin.add(0, 1.4, 0), m.finisherRadius * 0.8, ElementalistRules.COLOR_PRISM, after, ASTRAL_FINISH_MS);
                for (int e = 0; e < 4; e++) {
                    double expand = m.finisherRadius * Math.min(1.0, after / 320.0 + 0.1 * e);
                    ring(out, origin.add(0, 0.25 + e * 0.35, 0), expand, 0.18, ElementalistRules.elementColor(e), (int) (200 * fade), time * 0.004 + e);
                }
            }
        }
    }

    private static long arcticWaveAge(SkillVfx.Track track, long age) {
        return track.last.phase() == FINISH ? Math.min(age, track.last.at() - track.start.startedAt()) : age;
    }

    private static long lastArcticWave(Meta meta, long age) {
        return age < meta.firstWaveMs ? -1L : Math.min(meta.waveCount - 1L, (age - meta.firstWaveMs) / meta.waveIntervalMs);
    }

    private static void playArcticWaves(SkillVfx.Track track, long time) {
        Meta meta = METAS.get(track.start.cast());
        if (meta == null || meta.morphMode == 1) return;
        long age = time - track.start.startedAt();
        long last = lastArcticWave(meta, arcticWaveAge(track, age));
        if (last < meta.nextWaveSound) return;

        // Tracking replays should play only the waves that are still emerging.
        long first = Math.max(meta.nextWaveSound, Math.ceilDiv(age - meta.firstWaveMs - 100L, meta.waveIntervalMs));
        for (long wave = first; wave <= last; wave++) {
            SkillSound.playAt(track.start.position(), ARCTIC_WAVE_SOUND.sound(), ARCTIC_WAVE_SOUND.volume(), ARCTIC_WAVE_SOUND.pitch());
        }
        meta.nextWaveSound = last + 1L;
    }

    private static void arcticPillars(List<Quad> out, Vec3 origin, Meta meta, int color, long seed, long age, long lastWaveAge) {
        if (!(meta.radius > 0.0)) return;
        long elapsed = age - meta.firstWaveMs;
        long first = Math.max(0L, Math.floorDiv(elapsed - ICE_PILLAR_LIFETIME_MS, meta.waveIntervalMs) + 1L);
        long last = lastArcticWave(meta, lastWaveAge);
        for (long wave = first; wave <= last; wave++) {
            long waveAge = elapsed - wave * meta.waveIntervalMs;
            double grow = Math.min(1.0, waveAge / 100.0);
            double fade = Math.min(1.0, (ICE_PILLAR_LIFETIME_MS - waveAge) / 220.0);
            var random = new SplittableRandom(seed + wave);
            for (int i = 0; i < 3; i++) {
                double radius = Math.min(meta.radius * 0.25, random.nextDouble(0.23, 0.46));
                double angle = random.nextDouble(2.0 * Math.PI);
                double distance = Math.sqrt(random.nextDouble()) * (meta.radius - radius);
                Vec3 base = origin.add(Math.cos(angle) * distance, 0.04, Math.sin(angle) * distance);
                double height = random.nextDouble(1.8, 4.3) * grow * (2.0 - grow);
                icePillar(out, base, radius, height, angle, color, (int) (215 * fade));
            }
        }
    }

    private static void icePillar(List<Quad> out, Vec3 base, double radius, double height, double rotation, int color, int alpha) {
        if (radius <= 0 || height <= 0 || alpha <= 0) return;
        Vec3 shoulder = base.add(0, height * 0.74, 0);
        Vec3 tip = base.add(radius * 0.22, height, -radius * 0.16);
        for (int i = 0; i < 6; i++) {
            double a = rotation + i * Math.PI / 3.0;
            double b = a + Math.PI / 3.0;
            Vec3 left = new Vec3(Math.cos(a) * radius, 0, Math.sin(a) * radius);
            Vec3 right = new Vec3(Math.cos(b) * radius, 0, Math.sin(b) * radius);
            Vec3 upperLeft = shoulder.add(left.scale(0.7));
            Vec3 upperRight = shoulder.add(right.scale(0.7));
            out.add(new Quad(base.add(left), upperLeft, upperRight, base.add(right), i % 3 == 0 ? 0xc5f2ff : color, alpha));
            out.add(new Quad(upperRight, upperLeft, tip, tip, i % 2 == 0 ? 0xe4faff : color, alpha));
        }
    }

    private static void drawMeteor(List<Quad> out, FallingMeteor meteor, Meta meta, long time) {
        long age = time - meteor.startedAt();
        if (age < 0 || age >= meta.fallMs + METEOR_IMPACT_MS) return;
        Vec3 impact = meteor.impact();
        int color = meteor.color();
        if (age >= meta.fallMs) {
            explosion(out, impact.add(0, 0.4, 0), meta.meteorRadius * 0.85, color, age - meta.fallMs, METEOR_IMPACT_MS);
            return;
        }

        double progress = age / (double) meta.fallMs;
        double angle = meteor.sequence() * 1.7;
        Vec3 launchOffset = new Vec3(Math.cos(angle) * 2.8, 12.0, Math.sin(angle) * 2.8);
        Vec3 tail = launchOffset.normalize();
        Vec3 position = impact.add(0, 0.85, 0).add(launchOffset.scale(1.0 - progress * (0.65 + 0.35 * progress)));
        double radius = Math.clamp(meta.meteorRadius * 0.27, 0.65, 1.5);
        ring(out, impact.add(0, 0.05, 0), meta.meteorRadius * 0.6, 0.07, color, 150, time * 0.006);
        beam(out, position, position.add(tail.scale(5.0)), radius * 0.26, color, 170);
        for (int i = 1; i <= 3; i++) {
            double drift = angle + i * 2.4 + age * 0.02;
            Vec3 fragment = position.add(tail.scale(1.3 * i)).add(Math.cos(drift) * 0.24, 0, Math.sin(drift) * 0.24);
            sphere(out, fragment, radius * (0.59 - i * 0.12), color, 180 - i * 35, 6, 3);
        }
        meteorRock(out, position, radius, color, angle + age * 0.014);
    }

    private static void meteorRock(List<Quad> out, Vec3 center, double radius, int color, double rotation) {
        Vec3 top = center.add(-radius * 0.14, radius * 0.78, radius * 0.21);
        Vec3 bottom = center.add(radius * 0.21, -radius * 0.88, -radius * 0.16);
        Vec3[] rim = new Vec3[7];
        for (int i = 0; i < rim.length; i++) {
            double angle = rotation + i * 2.0 * Math.PI / rim.length;
            double width = radius * (0.89 + 0.14 * Math.cos(2.4 * i));
            rim[i] = center.add(Math.cos(angle) * width, radius * 0.25 * Math.sin(2.0 * i), Math.sin(angle) * width);
        }
        for (int i = 0; i < rim.length; i++) {
            Vec3 left = rim[i];
            Vec3 right = rim[(i + 1) % rim.length];
            out.add(new Quad(right, left, top, top, i % 3 == 0 ? color : 0x453a49, 255));
            out.add(new Quad(left, right, bottom, bottom, i % 3 == 1 ? color : 0x292532, 255));
        }
    }

    // --- Primitive WorldQuad builders ---

    private static void sphere(List<Quad> out, Vec3 center, double r, int rgb, int alpha, int slices, int stacks) {
        if (alpha <= 0 || r <= 0) return;
        int aClamped = Math.clamp(alpha, 0, 255);
        int col = rgb & 0xffffff;
        for (int s = 0; s < stacks; s++) {
            double p0 = Math.PI * (s / (double) stacks - 0.5), p1 = Math.PI * ((s + 1) / (double) stacks - 0.5);
            double y0 = Math.sin(p0) * r, y1 = Math.sin(p1) * r, r0 = Math.cos(p0) * r, r1 = Math.cos(p1) * r;
            for (int i = 0; i < slices; i++) {
                double a0 = i * Math.PI * 2.0 / slices, a1 = (i + 1) * Math.PI * 2.0 / slices;
                out.add(new Quad(
                    center.add(Math.cos(a0) * r0, y0, Math.sin(a0) * r0),
                    center.add(Math.cos(a1) * r0, y0, Math.sin(a1) * r0),
                    center.add(Math.cos(a1) * r1, y1, Math.sin(a1) * r1),
                    center.add(Math.cos(a0) * r1, y1, Math.sin(a0) * r1),
                    col, aClamped));
            }
        }
    }

    private static void ring(List<Quad> out, Vec3 center, double radius, double width, int rgb, int alpha, double phase) {
        if (alpha <= 0 || radius <= 0) return;
        int aClamped = Math.clamp(alpha, 0, 255);
        int col = rgb & 0xffffff;
        int segs = 28;
        double inner = Math.max(0.02, radius - width * 0.5), outer = radius + width * 0.5;
        for (int i = 0; i < segs; i++) {
            double a0 = phase + i * Math.PI * 2.0 / segs, a1 = phase + (i + 1) * Math.PI * 2.0 / segs;
            Vec3 p0 = center.add(Math.cos(a0) * inner, 0, Math.sin(a0) * inner);
            Vec3 p1 = center.add(Math.cos(a1) * inner, 0, Math.sin(a1) * inner);
            Vec3 p2 = center.add(Math.cos(a1) * outer, 0, Math.sin(a1) * outer);
            Vec3 p3 = center.add(Math.cos(a0) * outer, 0, Math.sin(a0) * outer);
            out.add(new Quad(p0, p1, p2, p3, col, aClamped));
            out.add(new Quad(p3, p2, p1, p0, col, aClamped));
        }
    }

    private static void column(List<Quad> out, Vec3 base, double radius, double height, int rgb, int alpha) {
        if (alpha <= 0 || radius <= 0 || height <= 0) return;
        int aClamped = Math.clamp(alpha, 0, 255);
        int col = rgb & 0xffffff;
        int sides = 10;
        for (int i = 0; i < sides; i++) {
            double a0 = i * Math.PI * 2.0 / sides, a1 = (i + 1) * Math.PI * 2.0 / sides;
            Vec3 b0 = base.add(Math.cos(a0) * radius, 0, Math.sin(a0) * radius);
            Vec3 b1 = base.add(Math.cos(a1) * radius, 0, Math.sin(a1) * radius);
            out.add(new Quad(b0, b1, b1.add(0, height, 0), b0.add(0, height, 0), col, aClamped));
        }
    }

    private static void beam(List<Quad> out, Vec3 from, Vec3 to, double halfWidth, int rgb, int alpha) {
        if (alpha <= 0 || halfWidth <= 0) return;
        Vec3 delta = to.subtract(from);
        if (delta.lengthSqr() < 1e-6) return;
        Vec3 up = Math.abs(delta.normalize().y) > 0.95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 right = delta.cross(up).normalize().scale(halfWidth);
        Vec3 perp = delta.cross(right).normalize().scale(halfWidth);
        int aClamped = Math.clamp(alpha, 0, 255);
        int col = rgb & 0xffffff;
        out.add(new Quad(from.subtract(right), from.add(right), to.add(right), to.subtract(right), col, aClamped));
        out.add(new Quad(from.subtract(perp), from.add(perp), to.add(perp), to.subtract(perp), col, aClamped));
    }

    private static void explosion(List<Quad> out, Vec3 center, double maxRadius, int rgb, long after, long duration) {
        double u = Math.clamp(after / (double) Math.max(1L, duration), 0.0, 1.0);
        double fade = 1.0 - u;
        if (fade <= 0.01) return;
        double r = maxRadius * (0.3 + 0.7 * Math.sin(u * Math.PI * 0.5));
        sphere(out, center, r * 0.65, rgb, (int) (185 * fade), 18, 9);
        sphere(out, center, r * 0.38, 0xffffff, (int) (220 * fade * fade), 12, 6);
        ring(out, center, r, 0.16, rgb, (int) (200 * fade), after * 0.005);
    }
}
