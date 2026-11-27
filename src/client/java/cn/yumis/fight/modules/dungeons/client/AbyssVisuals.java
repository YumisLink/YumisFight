package cn.yumis.fight.modules.dungeons.client;

import cn.yumis.fight.modules.dungeons.api.AbyssAttack;
import cn.yumis.fight.modules.dungeons.api.AbyssParty;
import cn.yumis.fight.modules.dungeons.api.DungeonEnemy;
import cn.yumis.fight.modules.dungeons.api.Target;
import cn.yumis.fight.modules.presentation.api.client.CameraShake;
import cn.yumis.fight.modules.presentation.api.client.ClientUi;
import cn.yumis.fight.modules.presentation.api.client.EffectParticles;
import cn.yumis.fight.modules.presentation.api.client.EffectSprites;
import cn.yumis.fight.modules.presentation.api.client.LightningBolt;
import cn.yumis.fight.modules.presentation.api.client.NearEffectRendering;
import cn.yumis.fight.modules.presentation.api.client.RibbonTrail;
import cn.yumis.fight.modules.presentation.api.client.WorldQuads;
import cn.yumis.fight.modules.presentation.api.client.WorldQuads.Quad;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static cn.yumis.fight.modules.dungeons.client.EnemyEffectGeometry.*;

/** Recipients resolve contact against the exact published ground warnings. */
public final class AbyssVisuals {
    private static final Gson JSON = new Gson();
    private static final ContextKey<Frame> FRAME =
        new ContextKey<>(Identifier.fromNamespaceAndPath("yumis_dungeon_and_fight", "abyss_party"));
    private static final Map<UUID, Cue> cues = new LinkedHashMap<>();
    private static final Map<String, Flash> flashes = new LinkedHashMap<>();
    private static final java.util.Random RANDOM = new java.util.Random();
    private static final long EPIC_FLASH = 6000, MYTHIC_FLASH = 9000;
    private static final int EPIC_GOLD = cn.yumis.fight.modules.equipment.api.Rarity.EPIC.color();
    private static final int MYTHIC_GOLD = cn.yumis.fight.modules.equipment.api.Rarity.MYTHIC.color(), MYTHIC_ACCENT = 0xfff6d8;
    private static final Map<UUID, Long> cancelled = new LinkedHashMap<>();
    private static final Set<String> shook = new HashSet<>();
    private static Supplier<String> instance = () -> "";
    private static Consumer<JsonObject> hit = ignored -> {};
    private static Object world;
    private static String room = "";
    private static long lastBoneHit;

    private record Frame(List<Quad> quads, List<EffectSprites.SpriteQuad> sprites, boolean fade) {}

    private static final class Cue {
        final AbyssAttack attack;
        final Target source;
        int pulse = -1;
        long previous;

        Cue(AbyssAttack attack, Target source) {
            this.attack = attack;
            this.source = source;
            previous = attack.release();
        }
    }

    /** Committed abyss flash fact; one coalesced cue per landing spot, mythic upgrades a running epic show. */
    private static final class Flash {
        final Vec3 position;
        final long start;
        boolean mythic;
        long end;
        long particle;

        Flash(Vec3 position, boolean mythic, long start, long end) {
            this.position = position;
            this.mythic = mythic;
            this.start = start;
            this.end = end;
            particle = start;
        }
    }

    public static void register(Supplier<String> current) {
        instance = current;
        var events = NeoForge.EVENT_BUS;
        events.addListener(AbyssVisuals::tick);
        events.addListener(AbyssVisuals::extract);
        events.addListener(AbyssVisuals::submit);
    }

    static void receive(JsonObject packet, Consumer<JsonObject> contact) {
        syncInstance();
        hit = contact;
        var attack = JSON.fromJson(packet.get("attack"), AbyssAttack.class);
        var source = JSON.fromJson(packet.get("source"), Target.class);
        if (source.instance.equals(room) && !cancelled.containsKey(attack.id()))
            cues.putIfAbsent(attack.id(), new Cue(attack, source));
    }

    static void cancel(JsonObject packet) {
        syncInstance();
        UUID id = UUID.fromString(packet.get("id").getAsString());
        cues.remove(id);
        cancelled.put(id, ClientUi.current().serverNow() + 10000);
    }

    static void flash(JsonObject packet) {
        syncInstance();
        boolean mythic = packet.get("mythic").getAsBoolean();
        Vec3 position = new Vec3(packet.get("x").getAsDouble(), packet.get("y").getAsDouble(), packet.get("z").getAsDouble());
        long now = ClientUi.current().serverNow();
        String key = Math.round(position.x * 2) + "/" + Math.round(position.z * 2);
        var running = flashes.get(key);
        if (running != null && running.end > now) {
            if (mythic && !running.mythic) {
                running.mythic = true;
                running.end = now + MYTHIC_FLASH;
                CameraShake.impulse(position, 24, 0.32f, 120, 260);
            }
            return;
        }
        flashes.put(key, new Flash(position, mythic, now, now + (mythic ? MYTHIC_FLASH : EPIC_FLASH)));
        if (mythic) CameraShake.impulse(position, 24, 0.32f, 120, 260);
    }

    private static void syncInstance() {
        Object next = Minecraft.getInstance().level;
        String current = instance.get();
        if (world == next && room.equals(current)) return;
        world = next;
        room = current;
        cues.clear();
        cancelled.clear();
        flashes.clear();
        shook.clear();
        lastBoneHit = 0;
    }

    private static void tick(ClientTickEvent.Post event) {
        syncInstance();
        var minecraft = Minecraft.getInstance();
        var player = minecraft.player;
        if (minecraft.level == null || player == null || !ClientUi.current().ready() || player.isDeadOrDying()) return;
        long now = ClientUi.current().serverNow();
        cancelled.values().removeIf(end -> end <= now);
        cues.values().removeIf(cue -> cue.attack.end() <= now);
        flashes.values().removeIf(flash -> flash.end <= now);
        for (Cue cue : cues.values()) {
            AbyssAttack attack = cue.attack;
            if (now < attack.release() || attack.coefficient() <= 0) continue;
            long previous = Math.max(now - 125, cue.previous);
            cue.previous = now;
            if (!attack.contains(player.position())) continue;
            if (attack.travels()) {
                double along = player.position().subtract(attack.origin()).dot(attack.direction());
                if (along < attack.range() * attack.progress(previous) - .55
                    || along > attack.range() * attack.progress(now) + .55) continue;
            }
            boolean bone = attack.effect() == AbyssAttack.Effect.BONE;
            int pulse = bone ? (int) ((now - attack.release()) / 1000) : 0;
            if (pulse <= cue.pulse || bone && now - lastBoneHit < 1000) continue;
            var ray = new ClipContext(attack.origin().add(0, .75, 0), player.getEyePosition(),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player);
            if (minecraft.level.clip(ray).getType() != HitResult.Type.MISS) continue;
            cue.pulse = pulse;
            if (bone) lastBoneHit = now;
            var contact = new JsonObject();
            contact.addProperty("id", "abyss/" + attack.id() + "/" + pulse);
            contact.addProperty("clientContact", true);
            contact.add("boss", JSON.toJsonTree(cue.source));
            contact.addProperty("kind", "ABYSS");
            contact.addProperty("coefficient", attack.coefficient());
            contact.addProperty("magic", attack.magic());
            contact.addProperty("element", attack.element());
            contact.addProperty("occurredAt", now);
            hit.accept(contact);
        }
        for (Flash flash : flashes.values()) {
            if (now < flash.start || now >= flash.end) continue;
            boolean opening = now - flash.start < 1200;
            if (now < flash.particle) continue;
            flash.particle = now + (opening ? 120 : 280);
            int count = opening ? 10 : flash.mythic ? 5 : 4;
            double radius = opening ? 1.15 : .45;
            for (int i = 0; i < count; i++) {
                double angle = RANDOM.nextDouble() * Math.PI * 2;
                double height = .25 + RANDOM.nextDouble() * (flash.mythic ? 4.6 : 3.2);
                int color = flash.mythic ? (i & 1) == 0 ? MYTHIC_GOLD : MYTHIC_ACCENT : EPIC_GOLD;
                EffectParticles.dust(flash.position.add(Math.cos(angle) * radius * RANDOM.nextDouble(), height,
                    Math.sin(angle) * radius * RANDOM.nextDouble()), color, opening ? .95f : .7f);
            }
        }
    }

    private static void extract(ExtractLevelRenderStateEvent event) {
        syncInstance();
        var quads = new ArrayList<Quad>();
        var sprites = new ArrayList<EffectSprites.SpriteQuad>();
        Vec3 camera = event.getCamera().position();
        var minecraft = Minecraft.getInstance();
        if (minecraft.level != null && minecraft.player != null && ClientUi.current().ready()) {
            long now = ClientUi.current().serverNow();
            if (shook.size() > 256) shook.clear();
            for (Cue cue : cues.values()) {
                AbyssAttack attack = cue.attack;
                if (attack.telegraph() && now >= attack.warning() && now < attack.end()) draw(quads, sprites, camera, attack, now);
            }
            for (Flash flash : flashes.values()) flashPillar(quads, sprites, camera, flash, now);
            for (var raw : minecraft.level.entitiesForRendering()) {
                if (raw instanceof DungeonEnemy enemy && enemy.appearance().equals(AbyssParty.Enemy.PILLAR.id) && enemy.guarded()) {
                    Vec3 pos = enemy.position().add(0, .06, 0);
                    ring(quads, pos, 1.05, .11, 0xb49ae1, 210);
                    EffectSprites.sigil(sprites, pos, 1.22, now * 0.0016, 0xb49ae1, 185, true);
                }
            }
        }
        event.getRenderState().setRenderData(FRAME, new Frame(List.copyOf(quads), List.copyOf(sprites), NearEffectRendering.enabled(event.getCamera())));
    }

    private static void draw(List<Quad> quads, List<EffectSprites.SpriteQuad> sprites, Vec3 camera, AbyssAttack attack, long now) {
        var origin = attack.origin().add(0, .065, 0);
        var direction = attack.direction();
        boolean active = now >= attack.release();
        int color = attack.color(), alpha = active ? 104 : 56;
        double warnP = attack.release() <= attack.warning() ? 1 : Math.clamp((now - attack.warning()) / (double) (attack.release() - attack.warning()), 0, 1);
        switch (attack.shape()) {
            case CIRCLE -> {
                disc(quads, origin, attack.range(), color, alpha);
                ring(quads, origin, attack.range(), .07, color, 214);
                if (!active) EffectSprites.warningCircle(sprites, origin.add(0, .01, 0), attack.range(), warnP, color, 170);
                else EffectSprites.sigil(sprites, origin.add(0, .015, 0), attack.range(), now * 0.002, color, 165, false);
            }
            case FAN -> fan(quads, origin, direction, attack.range(), attack.width(), color, alpha);
            case LINE -> rect(quads, origin, origin.add(direction.scale(attack.range())), attack.width(), color, alpha);
            case RING -> {
                ring(quads, origin, attack.range() - attack.width() / 2, attack.width(), color, active ? 205 : 130);
                if (active) EffectSprites.shockwave(sprites, origin.add(0, .02, 0), attack.range(), now * 0.002, color, 200);
            }
        }
        switch (attack.effect()) {
            case BONE -> {
                if (active) {
                    box(quads, origin.add(0, .57, 0), .10, .57, .10, 0xe4d6ba, 255);
                    box(quads, origin.add(0, 1.14, 0), .23, .13, .14, 0xf0e6d5, 255);
                    double cycle = ((now - attack.release()) % 1000) / 1000d;
                    double pulse = .65 + (attack.range() - .65) * cycle;
                    ring(quads, origin.add(0, .015, 0), pulse, .08, color, 178);
                    EffectSprites.shockwave(sprites, origin.add(0, .03, 0), pulse, cycle * 1.2, color, (int) (195 * (1 - cycle)));
                }
            }
            case COLUMN -> {
                double progress = attack.release() == attack.warning() ? 1
                    : Math.clamp((now - attack.warning()) / (double) (attack.release() - attack.warning()), 0, 1);
                double size = attack.range() >= 8 ? 1.6 : .31, height = attack.range() >= 8 ? 3.2 : 1.4;
                double y = Math.max(0, 1 - progress * progress) * attack.elevation();
                box(quads, origin.add(0, y + height / 2, 0), size, height / 2, size, 0xa9a9ad, 246);
                box(quads, origin.add(0, y + height - .18, 0), size * 1.12, .18, size * 1.12, 0xc9c7cc, 246);
                if (progress >= 0.92) {
                    double impP = Math.clamp((now - attack.release()) / 380d, 0, 0.99);
                    EffectSprites.shockwave(sprites, origin.add(0, .04, 0), attack.range() * (.3 + .8 * impP), impP * 1.1, color, (int) (225 * (1 - impP)));
                    EffectSprites.impactBurst(sprites, origin.add(0, .08, 0), camera, Math.min(4.5, attack.range() * .85), impP, 0xe0d8ff, 225, true);
                    if (shook.add(attack.id() + "#col")) CameraShake.impulse(origin, Math.max(16, attack.range() * 3), attack.range() >= 8 ? 0.38f : 0.24f, 100, 210);
                }
            }
            case SUMMON, PROJECTION -> {
                Vec3 projected = origin.add(direction.scale(.7));
                int opacity = active ? 152 : 95;
                box(quads, projected.add(0, 1.02, 0), .34, .44, .22, color, opacity);
                box(quads, projected.add(0, 1.68, 0), .25, .25, .25, color, opacity);
                Vec3 side = new Vec3(-direction.z, 0, direction.x);
                for (int sign : new int[]{-1, 1}) {
                    Vec3 limb = projected.add(side.scale(sign * .50));
                    line(quads, limb.add(0, 1.38, 0), limb.add(direction.scale(.26)).add(0, .72, 0), .13, color, opacity);
                    box(quads, projected.add(side.scale(sign * .16)).add(0, .34, 0), .095, .32, .13, color, opacity);
                }
                EffectSprites.sigil(sprites, projected.add(0, .02, 0), 1.35, now * 0.0022, color, active ? 195 : 135, true);
                EffectSprites.glow(sprites, projected.add(0, 1.1, 0), camera, .75, color, active ? 175 : 115);
            }
            case BEAM, ORB, ICE, SLASH, WAVE, FIRE, LANDING -> {
                if (active) impact(quads, sprites, camera, attack, origin, color, now);
            }
        }
    }

    private static void impact(List<Quad> quads, List<EffectSprites.SpriteQuad> sprites, Vec3 camera, AbyssAttack attack, Vec3 origin, int color, long now) {
        double progress = Math.clamp(attack.progress(now), 0, 0.99);
        if (attack.shape() == AbyssAttack.Shape.LINE) {
            Vec3 head = origin.add(attack.direction().scale(attack.range() * progress)).add(0, .55, 0);
            if (attack.effect() == AbyssAttack.Effect.BEAM) {
                Vec3 from = origin.add(0, .67, 0);
                Vec3 to = origin.add(attack.direction().scale(attack.range())).add(0, .67, 0);
                line(quads, from, to, attack.width() * .25, color, 218);
                EffectSprites.texturedBeam(sprites, from, to, camera, Math.max(.26, attack.width() * .28), now, true, color, 230);
                LightningBolt.helixArcs(sprites, from, to, camera, now, Math.max(.24, attack.width() * .25), 2, color, 185);
            } else if (attack.effect() == AbyssAttack.Effect.ICE) {
                box(quads, head, .22, .65, .22, color, 215);
                RibbonTrail.push("abyss:" + attack.id(), head, .26, color, 205, 240, ClientUi.now());
                EffectSprites.glow(sprites, head, camera, .6, color, 210);
            } else {
                box(quads, head, .35, .36, .35, color, 215);
                RibbonTrail.push("abyss:" + attack.id(), head, .28, color, 210, 240, ClientUi.now());
                EffectSprites.glow(sprites, head, camera, .68, color, 215);
            }
        } else if (attack.effect() == AbyssAttack.Effect.SLASH) {
            EffectSprites.slashArc(sprites, origin.add(0, .95, 0), attack.direction(), 10, attack.range(), progress, color, 225);
        } else {
            double radius = .1 + Math.max(0, attack.range() - .1) * progress;
            ring(quads, origin.add(0, .11, 0), radius, .18, color, 200);
            EffectSprites.shockwave(sprites, origin.add(0, .04, 0), radius, progress * 1.2, color, (int) (220 * (1 - progress * .75)));
            EffectSprites.impactBurst(sprites, origin.add(0, .08, 0), camera, Math.min(5.0, attack.range() * .85), progress, color, 215, true);
            if (attack.effect() == AbyssAttack.Effect.LANDING && shook.add(attack.id() + "#land")) {
                CameraShake.impulse(origin, Math.max(18, attack.range() * 3.5), 0.32f, 100, 200);
            }
        }
    }

    private static void submit(SubmitCustomGeometryEvent event) {
        Frame frame = event.getLevelRenderState().getRenderData(FRAME);
        if (frame != null) {
            WorldQuads.submit(event, frame.quads(), frame.fade());
            EffectSprites.submit(event, frame.sprites(), frame.fade());
        }
    }

    /** Ground rings, arcane sigil and sky beam for the committed abyss flash; landed items keep their rarity beams. */
    private static void flashPillar(List<Quad> quads, List<EffectSprites.SpriteQuad> sprites, Vec3 camera, Flash flash, long now) {
        if (now < flash.start || now >= flash.end) return;
        double fade = Math.min(1, (flash.end - now) / 1000d);
        Vec3 origin = flash.position.add(0, .03, 0);
        int color = flash.mythic ? MYTHIC_GOLD : EPIC_GOLD;
        int alpha = (int) ((flash.mythic ? 214 : 190) * fade);
        double cycle = ((now - flash.start) % 1100) / 1100d;
        double pulse = .55 + (flash.mythic ? 2.1 : 1.5) * cycle;
        ring(quads, origin, pulse, .10, color, (int) (170 * fade));
        ring(quads, origin, .78, .14, color, alpha);
        if (flash.mythic)
            ring(quads, origin, 1.05 + .45 * Math.sin((now - flash.start) * Math.PI / 900), .12, MYTHIC_ACCENT, (int) (150 * fade));

        double pillarHeight = flash.mythic ? 11.0 : 7.5;
        EffectSprites.sigil(sprites, origin.add(0, .02, 0), flash.mythic ? 1.85 : 1.35, now * 0.0018, color, (int) (215 * fade), true);
        EffectSprites.shockwave(sprites, origin.add(0, .04, 0), pulse, cycle * 1.4, color, (int) (200 * fade * (1 - cycle)));
        EffectSprites.texturedBeam(sprites, origin, origin.add(0, pillarHeight, 0), camera, flash.mythic ? .44 : .28, now, flash.mythic, color, (int) (205 * fade));
        EffectSprites.glow(sprites, origin.add(0, .55, 0), camera, flash.mythic ? 1.45 : 1.0, flash.mythic ? MYTHIC_ACCENT : color, (int) (220 * fade));
        if (flash.mythic) {
            LightningBolt.helixArcs(sprites, origin.add(0, .2, 0), origin.add(0, pillarHeight * .85, 0), camera, now, .72, 2, MYTHIC_ACCENT, (int) (175 * fade));
        }
    }

    private AbyssVisuals() {}
}
