package cn.yumis.fight.modules.industry.client;

import cn.yumis.fight.modules.industry.platform.DemonAltarBlock;
import cn.yumis.fight.modules.industry.platform.DemonAltarBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.List;
import java.util.stream.IntStream;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class DemonAltarRenderer implements BlockEntityRenderer<DemonAltarBlockEntity, DemonAltarRenderer.State> {
    public static final class State extends BlockEntityRenderState {
        final List<ItemStackRenderState> items = IntStream.rangeClosed(0, DemonAltarBlockEntity.OUTPUT_SLOT)
            .mapToObj(slot -> new ItemStackRenderState()).toList();
        float angle, bob;
    }

    private final ItemModelResolver models;

    public DemonAltarRenderer(BlockEntityRendererProvider.Context context) {
        models = context.itemModelResolver();
    }

    @Override public State createRenderState() { return new State(); }

    @Override public void extractRenderState(DemonAltarBlockEntity altar, State state, float partial, Vec3 camera,
                                             ModelFeatureRenderer.CrumblingOverlay breaking) {
        BlockEntityRenderer.super.extractRenderState(altar, state, partial, camera, breaking);
        var level = altar.getLevel();
        for (int slot = 0; slot < state.items.size(); slot++) {
            models.updateForTopItem(state.items.get(slot), altar.getItem(slot).copyWithCount(1), ItemDisplayContext.FIXED, level, null, slot);
        }
        double time = level.getGameTime() % 120 + partial;
        state.angle = (float) (time * 3);
        state.bob = (float) Math.sin(time * Math.PI / 60) * .045f;
    }

    @Override public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        for (int slot = 0; slot < DemonAltarBlockEntity.OFFERING_SLOTS; slot++) {
            var item = state.items.get(slot);
            if (item.isEmpty()) continue;
            pose.pushPose();
            pose.translate(slot % 2 == 0 ? .29 : .71, DemonAltarBlock.SURFACE + .03, slot < 2 ? .29 : .71);
            pose.mulPose(Axis.XP.rotationDegrees(90));
            pose.scale(.34f, .34f, .34f);
            item.submit(pose, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
            pose.popPose();
        }
        var result = state.items.get(DemonAltarBlockEntity.OUTPUT_SLOT);
        if (result.isEmpty()) return;
        pose.pushPose();
        pose.translate(.5, DemonAltarBlock.SURFACE + .32 + state.bob, .5);
        pose.mulPose(Axis.YP.rotationDegrees(state.angle));
        pose.scale(.55f, .55f, .55f);
        result.submit(pose, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
        pose.popPose();
    }

    @Override public AABB getRenderBoundingBox(DemonAltarBlockEntity altar) {
        return new AABB(altar.getBlockPos()).expandTowards(0, .5, 0);
    }
}
