package cn.yumis.fight.modules.party.client;
import cn.yumis.fight.modules.party.api.Team;
import cn.yumis.fight.modules.presentation.api.client.ClientSettings;
import cn.yumis.fight.modules.presentation.api.client.WorldQuads;
import cn.yumis.fight.modules.presentation.api.client.WorldQuads.Quad;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;

/** 队友头顶血条：只渲染本队在线成员，绿色与敌人血条区分；数据来自既有 life 同步，不新增网络负载。 */
public final class PartyHealthBars {
    private record Frame(List<Quad> quads) {}
    private static final ContextKey<Frame> FRAME = new ContextKey<>(
        Identifier.fromNamespaceAndPath("yumis_dungeon_and_fight", "party_health_bars"));

    private PartyHealthBars() {}

    public static void register(Supplier<Team> party, ToDoubleFunction<Player> fraction) {
        NeoForge.EVENT_BUS.addListener((ExtractLevelRenderStateEvent event) -> extract(event, party, fraction));
        NeoForge.EVENT_BUS.addListener(PartyHealthBars::submit);
    }

    private static void extract(ExtractLevelRenderStateEvent event, Supplier<Team> party, ToDoubleFunction<Player> fraction) {
        var mc = Minecraft.getInstance();
        var player = mc.player;
        if (player == null || mc.gui.hud.isHidden() || event.getLevel() != player.level()) return;
        Team team = party.get();
        var quads = new ArrayList<Quad>();
        if (team != null && !team.members.isEmpty()) {
            var camera = event.getCamera();
            Vec3 left = new Vec3(camera.leftVector());
            Vec3 up = new Vec3(camera.upVector());
            double range = ClientSettings.enemyHealthDistance;
            float partial = event.getDeltaTracker().getGameTimeDeltaPartialTick(false);
            for (var mate : event.getLevel().getEntitiesOfClass(Player.class, player.getBoundingBox().inflate(range))) {
                if (mate == player || !mate.isAlive() || mate.isInvisible()
                    || !team.members.contains(mate.getUUID()) || player.distanceToSqr(mate) > range * range) continue;
                double value = Math.clamp(fraction.applyAsDouble(mate), 0, 1);
                Vec3 anchor = mate.getPosition(partial).add(0, mate.getBbHeight() + .25, 0);
                double scale = ClientSettings.enemyHealthPerspective ? 1 : Math.max(.12, anchor.distanceTo(camera.position()) / 12);
                Vec3 horizontal = left.scale(.625 * scale);
                Vec3 vertical = up.scale(.065 * scale);
                Vec3 start = anchor.add(horizontal);
                Vec3 end = anchor.subtract(horizontal);
                Vec3 borderWidth = left.scale(.025 * scale);
                Vec3 borderHeight = up.scale(.022 * scale);
                var outerHeight = vertical.add(borderHeight);
                int outline = 0x19131D;
                quads.add(segment(start.add(borderWidth), start, outerHeight, outline));
                quads.add(segment(end, end.subtract(borderWidth), outerHeight, outline));
                Vec3 top = vertical.add(borderHeight.scale(.5));
                quads.add(segment(start.add(top), end.add(top), borderHeight.scale(.5), outline));
                quads.add(segment(start.subtract(top), end.subtract(top), borderHeight.scale(.5), outline));
                Vec3 fillEnd = start.lerp(end, value);
                if (value > 0) quads.add(segment(start, fillEnd, vertical, 0x71d68f));
                if (value < 1) quads.add(segment(fillEnd, end, vertical, 0x2A3F2E));
            }
        }
        event.getRenderState().setRenderData(FRAME, new Frame(quads));
    }

    private static Quad segment(Vec3 start, Vec3 end, Vec3 up, int color) {
        return new Quad(start.subtract(up), end.subtract(up), end.add(up), start.add(up), color, 255);
    }

    private static void submit(SubmitCustomGeometryEvent event) {
        var frame = event.getLevelRenderState().getRenderData(FRAME);
        if (frame == null || event.getLevelRenderState().cameraRenderState == null) return;
        WorldQuads.submit(event, frame.quads());
    }
}
