package cn.yumis.fight.modules.dungeons.client;

import cn.yumis.fight.modules.dungeons.api.DungeonEnemy;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** A fixed stone pillar assembled from the game's existing gray masonry. */
public final class AbyssPillarRenderer extends EntityRenderer<DungeonEnemy, AbyssPillarRenderer.State> {
    public static final class State extends EntityRenderState {
        final ItemStackRenderState stone = new ItemStackRenderState();
        final ItemStackRenderState carved = new ItemStackRenderState();
    }

    private final ItemModelResolver models;

    public AbyssPillarRenderer(EntityRendererProvider.Context context) {
        super(context);
        models = context.getItemModelResolver();
        shadowRadius = .52f;
    }

    @Override public State createRenderState() { return new State(); }

    @Override public void extractRenderState(DungeonEnemy entity, State state, float partial) {
        super.extractRenderState(entity, state, partial);
        models.updateForTopItem(state.stone, new ItemStack(Items.POLISHED_ANDESITE), ItemDisplayContext.FIXED,
            entity.level(), null, entity.getId());
        models.updateForTopItem(state.carved, new ItemStack(Items.CHISELED_STONE_BRICKS), ItemDisplayContext.FIXED,
            entity.level(), null, entity.getId());
    }

    @Override public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        block(state.carved, state, pose, collector, .28f, 1.15f, .56f);
        block(state.stone, state, pose, collector, 1.52f, .94f, 1.94f);
        block(state.carved, state, pose, collector, 2.72f, 1.10f, .46f);
        block(state.stone, state, pose, collector, 3.05f, .88f, .20f);
        super.submit(state, pose, collector, camera);
    }

    private static void block(ItemStackRenderState model, State state, PoseStack pose, SubmitNodeCollector collector,
                              float center, float width, float height) {
        pose.pushPose();
        pose.translate(0, center, 0);
        // The fixed-display block transform uses the eight-pixel item scale.
        pose.scale(width * 2, height * 2, width * 2);
        model.submit(pose, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
        pose.popPose();
    }
}
