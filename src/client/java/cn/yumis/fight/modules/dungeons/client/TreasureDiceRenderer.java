package cn.yumis.fight.modules.dungeons.client;

import cn.yumis.fight.modules.dungeons.api.TreasureDiceEntity;
import cn.yumis.fight.modules.presentation.api.client.ClientUi;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Floating luminous golden-ivory 3D die renderer with spinning animation, face digits (1-9 / ?),
 * and overhead status billboard for the Treasure Hunt Twin Dice gamble.
 */
public final class TreasureDiceRenderer extends EntityRenderer<TreasureDiceEntity, TreasureDiceRenderer.State> {
    private static final Identifier BODY_TEX = Identifier.withDefaultNamespace("textures/block/quartz_block_side.png");
    private static final Identifier FRAME_TEX = Identifier.withDefaultNamespace("textures/block/gold_block.png");
    private final CubeModel body = new CubeModel(false);
    private final CubeModel frame = new CubeModel(true);

    public TreasureDiceRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.42f;
    }

    public static final class State extends EntityRenderState {
        int slot, face, firstValue, multiplier, rounds, lastResult;
        String phase = "";
        long rollUntil;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(TreasureDiceEntity entity, State state, float delta) {
        super.extractRenderState(entity, state, delta);
        state.slot = entity.slot();
        state.face = entity.face();
        state.firstValue = entity.firstValue();
        state.multiplier = entity.multiplier();
        state.rounds = entity.rounds();
        state.lastResult = entity.lastResult();
        state.phase = entity.phase();
        state.rollUntil = entity.rollUntil();
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        long now = ClientUi.current().serverNow();
        boolean spinning = now < state.rollUntil;
        double bob = Math.sin(now * 0.0032 + state.slot * 1.6) * 0.12;

        int bodyTint = state.lastResult < 0 ? 0xFFFF8A8A
            : state.lastResult > 0 ? 0xFFFFF1A8
            : 0xFFFFF8E6;
        int frameTint = state.lastResult < 0 ? 0xFFFF4646
            : state.lastResult > 0 ? 0xFFFFD84D
            : 0xFFF5C452;

        // 1. 骰子主体与金框
        pose.pushPose();
        pose.translate(0, 0.72 + bob, 0);
        if (spinning) {
            float spin = (now % 3600L) * 0.75f;
            pose.mulPose(Axis.YP.rotationDegrees(spin));
            pose.mulPose(Axis.XP.rotationDegrees(spin * 1.35f));
            pose.mulPose(Axis.ZP.rotationDegrees(spin * 0.65f));
        } else {
            float idleYaw = (float) ((now * 0.032 + state.slot * 90.0) % 360.0);
            pose.mulPose(Axis.YP.rotationDegrees(idleYaw));
        }

        pose.pushPose();
        pose.scale(1, -1, 1);
        collector.submitModel(body, state, pose, body.renderType(BODY_TEX), 0x00F000F0, OverlayTexture.NO_OVERLAY, bodyTint, null);
        collector.submitModel(frame, state, pose, frame.renderType(FRAME_TEX), 0x00F000F0, OverlayTexture.NO_OVERLAY, frameTint, null);
        pose.popPose();

        // 四面雕刻醒目点数（1-9 或 ?；旋转时高速翻滚数字）
        String glyph = spinning
            ? Integer.toString(1 + (int) Math.floorMod(now / 55L + state.slot * 3L, 9))
            : (state.face > 0 ? Integer.toString(state.face) : "?");
        int glyphColor = state.lastResult < 0 ? 0xFFFF4B4B
            : state.lastResult > 0 ? 0xFFFFE45C
            : (state.face > 0 ? 0xFF3E2606 : 0xFF8A5D12);
        var font = Minecraft.getInstance().font;
        var glyphSeq = Component.literal(glyph).getVisualOrderText();
        float glyphWidth = font.width(glyphSeq);
        for (int side = 0; side < 4; side++) {
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(side * 90f));
            pose.translate(0, 0.06, 0.415);
            pose.scale(0.052f, -0.052f, 0.052f);
            collector.submitText(pose, -glyphWidth / 2f, -4f, glyphSeq, false, Font.DisplayMode.NORMAL, 0x00F000F0, glyphColor, 0, 0);
            pose.popPose();
        }
        pose.popPose();

        // 2. 头顶朝向相机的状态提示标签
        String label = overheadLabel(state, spinning);
        int labelColor = state.lastResult < 0 ? 0xFFFF6666
            : state.lastResult > 0 ? 0xFFFFE56B
            : isActiveDie(state) ? 0xFFFFE899 : 0xFFDAD6CC;
        var labelSeq = Component.literal(label).getVisualOrderText();
        pose.pushPose();
        pose.translate(0, 1.65 + bob, 0);
        pose.mulPose(camera.orientation);
        pose.scale(0.022f, -0.022f, 0.022f);
        collector.submitText(pose, -font.width(labelSeq) / 2f, 0f, labelSeq, true, Font.DisplayMode.NORMAL, 0x00F000F0, labelColor, 0x66000000, 0);
        pose.popPose();
    }

    private static boolean isActiveDie(State s) {
        if ("CHOOSE_START".equals(s.phase) || "READY_DIE_1".equals(s.phase)) return s.slot == 1;
        if ("READY_DIE_2".equals(s.phase)) return s.slot == 2;
        return false;
    }

    private static String overheadLabel(State s, boolean spinning) {
        if (spinning) return "骰子 " + s.slot + " · 旋转开奖中…";
        if ("REVEAL".equals(s.phase)) {
            if (s.lastResult > 0) return "骰子 " + s.slot + " · 【" + s.face + "】  ×" + s.multiplier + " 猜中!";
            if (s.lastResult < 0) return "骰子 " + s.slot + " · 【" + s.face + "】  宝箱归零";
        }
        if ("CHOOSE_START".equals(s.phase)) {
            return s.slot == 1 ? "★ 骰子 1 · 跨区连庄（对准选择）" : "骰子 2 · 待开局";
        }
        if ("READY_DIE_1".equals(s.phase)) {
            return s.slot == 1 ? "★ 骰子 1 · 对准右键开掷 (×" + s.multiplier + ")" : "骰子 2 · 待第一颗定格";
        }
        if ("READY_DIE_2".equals(s.phase)) {
            return s.slot == 1
                ? "骰子 1 · 已定格【" + s.face + "】"
                : "★ 骰子 2 · 对准猜 >" + s.firstValue + " 或 <" + s.firstValue;
        }
        return "命运骰子 " + s.slot;
    }

    private static final class CubeModel extends EntityModel<State> {
        CubeModel(boolean frame) {
            super(layer(frame).bakeRoot());
        }

        static LayerDefinition layer(boolean frame) {
            var mesh = new MeshDefinition();
            var cubes = CubeListBuilder.create();
            if (!frame) {
                cubes.texOffs(0, 0).addBox(-6.4f, -6.4f, -6.4f, 12.8f, 12.8f, 12.8f);
            } else {
                // 12 条细金棱镶边
                float s = 6.7f, t = 1.15f;
                for (float a : new float[]{-s, s - t}) {
                    for (float b : new float[]{-s, s - t}) {
                        cubes.texOffs(0, 0).addBox(-s, a, b, s * 2, t, t);
                        cubes.texOffs(0, 0).addBox(a, -s, b, t, s * 2, t);
                        cubes.texOffs(0, 0).addBox(a, b, -s, t, t, s * 2);
                    }
                }
            }
            mesh.getRoot().addOrReplaceChild("cube", cubes, PartPose.ZERO);
            return LayerDefinition.create(mesh, 16, 16);
        }
    }
}
