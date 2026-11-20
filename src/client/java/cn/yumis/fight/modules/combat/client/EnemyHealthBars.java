package cn.yumis.fight.modules.combat.client;

import cn.yumis.fight.modules.presentation.api.client.ClientSettings;
import cn.yumis.fight.modules.presentation.api.client.WorldQuads;
import cn.yumis.fight.modules.presentation.api.client.WorldQuads.Quad;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

public final class EnemyHealthBars {
    private record Label(Vec3 position, float scale, String text) {}
    private record Frame(List<Quad> quads, List<Label> labels) {}
    private static final ContextKey<Frame> FRAME = new ContextKey<>(
        Identifier.fromNamespaceAndPath("yumis_dungeon_and_fight", "enemy_health_bars"));

    private EnemyHealthBars() {}

    public static void register(Predicate<LivingEntity> excluded, ToDoubleFunction<LivingEntity> maximumHealth) {
        NeoForge.EVENT_BUS.addListener((ExtractLevelRenderStateEvent event) -> extract(event, excluded, maximumHealth));
        NeoForge.EVENT_BUS.addListener(EnemyHealthBars::submit);
    }

    private static void extract(ExtractLevelRenderStateEvent event, Predicate<LivingEntity> excluded,
                                ToDoubleFunction<LivingEntity> maximumHealth) {
        var mc = Minecraft.getInstance();
        var player = mc.player;
        if (player == null || mc.gui.hud.isHidden() || event.getLevel() != player.level()) return;

        var camera = event.getCamera();
        Vec3 left = new Vec3(camera.leftVector());
        Vec3 up = new Vec3(camera.upVector());
        double range = ClientSettings.enemyHealthDistance;
        float partial = event.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        var quads = new ArrayList<Quad>();
        var labels = new ArrayList<Label>();
        for (var enemy : event.getLevel().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(range))) {
            if (!(enemy instanceof Enemy || enemy instanceof EnderDragon) || !enemy.isAlive() || enemy.isInvisible()
                || player.distanceToSqr(enemy) > range * range || excluded.test(enemy)) continue;

            // Native entity health already carries the server's committed combat health fraction.
            double fraction = Math.clamp((double) enemy.getHealth() / enemy.getMaxHealth(), 0, 1);
            Vec3 anchor = enemy.getPosition(partial).add(0, enemy.getBbHeight() + .25, 0);
            double scale = ClientSettings.enemyHealthPerspective ? 1 : Math.max(.12, anchor.distanceTo(camera.position()) / 12);
            Vec3 horizontal = left.scale(.625 * scale);
            Vec3 vertical = up.scale(.065 * scale);
            Vec3 start = anchor.add(horizontal);
            Vec3 end = anchor.subtract(horizontal);
            Vec3 borderWidth = left.scale(.025 * scale);
            Vec3 borderHeight = up.scale(.022 * scale);
            Vec3 outerStart = start.add(borderWidth);
            Vec3 outerEnd = end.subtract(borderWidth);
            var outerHeight = vertical.add(borderHeight);
            int outline = 0x19131D;
            quads.add(barSegment(outerStart, start, outerHeight, outline));
            quads.add(barSegment(end, outerEnd, outerHeight, outline));
            Vec3 top = vertical.add(borderHeight.scale(.5));
            quads.add(barSegment(start.add(top), end.add(top), borderHeight.scale(.5), outline));
            quads.add(barSegment(start.subtract(top), end.subtract(top), borderHeight.scale(.5), outline));
            Vec3 fillEnd = start.lerp(end, fraction);
            if (fraction > 0) quads.add(barSegment(start, fillEnd, vertical, 0xBC4859));
            if (fraction < 1) quads.add(barSegment(fillEnd, end, vertical, 0x3B2534));
            double maximum = maximumHealth.applyAsDouble(enemy);
            labels.add(new Label(anchor.add(up.scale(.28 * scale)), (float)(.016 * scale),
                amount(fraction * maximum) + " / " + amount(maximum)));
        }
        event.getRenderState().setRenderData(FRAME, new Frame(quads, labels));
    }

    private static String amount(double value) {
        if (value < 10000) return Long.toString((long)Math.ceil(value));
        if (value < 1000000) return String.format(Locale.ROOT, "%.1fk", value / 1000);
        return String.format(Locale.ROOT, "%.1fm", value / 1000000);
    }

    private static Quad barSegment(Vec3 start, Vec3 end, Vec3 up, int color) {
        return new Quad(start.subtract(up), end.subtract(up), end.add(up), start.add(up), color, 255);
    }

    private static void submit(SubmitCustomGeometryEvent event) {
        var frame = event.getLevelRenderState().getRenderData(FRAME);
        var camera = event.getLevelRenderState().cameraRenderState;
        if (frame == null || camera == null) return;
        WorldQuads.submit(event, frame.quads());
        var poses = event.getPoseStack();
        var font = Minecraft.getInstance().font;
        for (var label : frame.labels()) {
            Vec3 position = label.position().subtract(camera.pos);
            poses.pushPose();
            poses.translate(position.x, position.y, position.z);
            poses.mulPose(camera.orientation);
            poses.scale(label.scale(), -label.scale(), label.scale());
            var text = Component.literal(label.text()).getVisualOrderText();
            event.getSubmitNodeCollector().submitText(poses, -font.width(text) / 2f, 0, text, true,
                Font.DisplayMode.NORMAL, 0xF000F0, 0xFFFFFFFF, 0, 0);
            poses.popPose();
        }
    }
}
