package cn.yumis.fight.modules.mapgeneration.client;

import cn.yumis.fight.modules.mapgeneration.platform.*;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.*;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.*;

/** The held item, placed shard and floating shard all use the same baked model. */
public final class FloatingShardRenderer implements BlockEntityRenderer<FloatingShardBlockEntity,FloatingShardRenderer.State> {
    private static final BlockDisplayContext DISPLAY=BlockDisplayContext.create();
    private final BlockModelResolver models;
    public static final class State extends BlockEntityRenderState {final BlockModelRenderState model=new BlockModelRenderState();double bob;}
    public FloatingShardRenderer(BlockEntityRendererProvider.Context context){models=context.blockModelResolver();}
    @Override public State createRenderState(){return new State();}
    @Override public void extractRenderState(FloatingShardBlockEntity block,State state,float partial,Vec3 camera,ModelFeatureRenderer.CrumblingOverlay breaking){
        BlockEntityRenderer.super.extractRenderState(block,state,partial,camera,breaking);
        state.bob=block.getBlockState().getValue(FloatingShardBlock.FLOATING)&&block.getLevel()!=null?FloatingShardBlock.bob(block.getLevel().getGameTime()+partial,block.getBlockPos()):0;
        models.update(state.model,block.getBlockState(),DISPLAY);
    }
    @Override public void submit(State state,PoseStack pose,SubmitNodeCollector nodes,CameraRenderState camera){
        pose.pushPose();pose.translate(0,state.bob,0);state.model.submitMultiLayer(pose,nodes,state.lightCoords,OverlayTexture.NO_OVERLAY,0);pose.popPose();
    }
    @Override public AABB getRenderBoundingBox(FloatingShardBlockEntity block){return new AABB(block.getBlockPos()).inflate(.05,FloatingShardBlock.HEIGHT+FloatingShardBlock.AMPLITUDE,.05);}
}
