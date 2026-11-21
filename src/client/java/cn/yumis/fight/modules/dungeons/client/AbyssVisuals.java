package cn.yumis.fight.modules.dungeons.client;

import cn.yumis.fight.modules.dungeons.api.AbyssAttack;
import cn.yumis.fight.modules.dungeons.api.AbyssParty;
import cn.yumis.fight.modules.dungeons.api.DungeonEnemy;
import cn.yumis.fight.modules.dungeons.api.Target;
import cn.yumis.fight.modules.presentation.api.client.ClientUi;
import cn.yumis.fight.modules.presentation.api.client.NearEffectRendering;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private static final Map<UUID, Long> cancelled = new LinkedHashMap<>();
    private static Supplier<String> instance = () -> "";
    private static Consumer<JsonObject> hit = ignored -> {};
    private static Object world;
    private static String room = "";
    private static long lastBoneHit;

    private record Frame(List<Quad> quads, boolean fade) {}

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

    private static void syncInstance() {
        Object next = Minecraft.getInstance().level;
        String current = instance.get();
        if (world == next && room.equals(current)) return;
        world = next;
        room = current;
        cues.clear();
        cancelled.clear();
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
    }

    private static void extract(ExtractLevelRenderStateEvent event) {
        syncInstance();
        var quads = new ArrayList<Quad>();
        var minecraft = Minecraft.getInstance();
        if (minecraft.level != null && minecraft.player != null && ClientUi.current().ready()) {
            long now = ClientUi.current().serverNow();
            for (Cue cue : cues.values()) {
                AbyssAttack attack = cue.attack;
                if (attack.telegraph() && now >= attack.warning() && now < attack.end()) draw(quads, attack, now);
            }
            for (var raw : minecraft.level.entitiesForRendering()) {
                if (raw instanceof DungeonEnemy enemy && enemy.appearance().equals(AbyssParty.Enemy.PILLAR.id) && enemy.guarded())
                    ring(quads, enemy.position().add(0, .06, 0), 1.05, .11, 0xb49ae1, 210);
            }
        }
        event.getRenderState().setRenderData(FRAME, new Frame(List.copyOf(quads), NearEffectRendering.enabled(event.getCamera())));
    }

    private static void draw(List<Quad> quads, AbyssAttack attack, long now) {
        var origin = attack.origin().add(0, .065, 0);
        var direction = attack.direction();
        boolean active = now >= attack.release();
        int color = attack.color(), alpha = active ? 104 : 56;
        switch (attack.shape()) {
            case CIRCLE -> {
                disc(quads, origin, attack.range(), color, alpha);
                ring(quads, origin, attack.range(), .07, color, 214);
            }
            case FAN -> fan(quads, origin, direction, attack.range(), attack.width(), color, alpha);
            case LINE -> rect(quads, origin, origin.add(direction.scale(attack.range())), attack.width(), color, alpha);
            case RING -> ring(quads, origin, attack.range() - attack.width() / 2, attack.width(), color, active ? 205 : 130);
        }
        switch (attack.effect()) {
            case BONE -> {
                if (active) {
                    box(quads, origin.add(0, .57, 0), .10, .57, .10, 0xe4d6ba, 255);
                    box(quads, origin.add(0, 1.14, 0), .23, .13, .14, 0xf0e6d5, 255);
                    double pulse = .65 + (attack.range() - .65) * ((now - attack.release()) % 1000) / 1000d;
                    ring(quads, origin.add(0, .015, 0), pulse, .08, color, 178);
                }
            }
            case COLUMN -> {
                double progress = attack.release() == attack.warning() ? 1
                    : (now - attack.warning()) / (double) (attack.release() - attack.warning());
                double size = attack.range() >= 8 ? 1.6 : .31, height = attack.range() >= 8 ? 3.2 : 1.4;
                double y = Math.max(0, 1 - progress * progress) * attack.elevation();
                box(quads, origin.add(0, y + height / 2, 0), size, height / 2, size, 0xa9a9ad, 246);
                box(quads, origin.add(0, y + height - .18, 0), size * 1.12, .18, size * 1.12, 0xc9c7cc, 246);
            }
            case SUMMON, PROJECTION -> {
                Vec3 projected = origin.add(direction.scale(.7));
                int opacity = active ? 152 : 95;
                box(quads, projected.add(0, 1.02, 0), .34, .44, .22, color, opacity);
                box(quads, projected.add(0, 1.68, 0), .25, .25, .25, color, opacity);
                Vec3 side = new Vec3(-direction.z, 0, direction.x);
                for(int sign : new int[]{-1, 1}) {
                    Vec3 limb = projected.add(side.scale(sign * .50));
                    line(quads, limb.add(0, 1.38, 0), limb.add(direction.scale(.26)).add(0, .72, 0), .13, color, opacity);
                    box(quads, projected.add(side.scale(sign * .16)).add(0, .34, 0), .095, .32, .13, color, opacity);
                }
            }
            case BEAM, ORB, ICE, SLASH, WAVE, FIRE, LANDING -> {
                if (active) impact(quads, attack, origin, color, now);
            }
        }
    }

    private static void impact(List<Quad> quads, AbyssAttack attack, Vec3 origin, int color, long now) {
        double progress = attack.progress(now);
        if (attack.shape() == AbyssAttack.Shape.LINE) {
            Vec3 head = origin.add(attack.direction().scale(attack.range() * progress)).add(0, .55, 0);
            if (attack.effect() == AbyssAttack.Effect.BEAM)
                line(quads, origin.add(0, .67, 0), origin.add(attack.direction().scale(attack.range())).add(0, .67, 0),
                    attack.width() * .25, color, 218);
            else if (attack.effect() == AbyssAttack.Effect.ICE)
                box(quads, head, .22, .65, .22, color, 215);
            else
                box(quads, head, .35, .36, .35, color, 215);
        } else {
            double radius = .1 + Math.max(0, attack.range() - .1) * progress;
            ring(quads, origin.add(0, .11, 0), radius, .18, color, 200);
        }
    }

    private static void submit(SubmitCustomGeometryEvent event) {
        Frame frame = event.getLevelRenderState().getRenderData(FRAME);
        if (frame != null) WorldQuads.submit(event, frame.quads(), frame.fade());
    }

    private AbyssVisuals() {}
}
