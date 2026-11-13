package cn.yumis.fight.modules.industry.client;

import cn.yumis.fight.modules.industry.platform.DisplayStandBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.*;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.*;

public final class DisplayStandRenderer implements BlockEntityRenderer<DisplayStandBlockEntity,DisplayStandRenderer.State> {
    public static final class State extends BlockEntityRenderState {final ItemStackRenderState main=new ItemStackRenderState(),output=new ItemStackRenderState();boolean mainVisible,outputVisible;float angle,bob;}
    private final ItemModelResolver items;
    public DisplayStandRenderer(BlockEntityRendererProvider.Context context){items=context.itemModelResolver();}
    @Override public State createRenderState(){return new State();}
    @Override public void extractRenderState(DisplayStandBlockEntity be,State state,float partial,Vec3 camera,ModelFeatureRenderer.CrumblingOverlay breaking){
        BlockEntityRenderer.super.extractRenderState(be,state,partial,camera,breaking);
        var input=be.getItem(0);var output=be.getItem(1);var main=input.isEmpty()?output:input;
        state.mainVisible=!main.isEmpty();state.outputVisible=!input.isEmpty()&&!output.isEmpty();
        double time=be.getLevel().getGameTime()+partial;state.angle=(float)((time%240)*1.5);state.bob=(float)Math.sin(time*Math.PI/100)*.045f;
        if(state.mainVisible)items.updateForTopItem(state.main,main.copyWithCount(1),ItemDisplayContext.FIXED,be.getLevel(),null,0);
        if(state.outputVisible)items.updateForTopItem(state.output,output.copyWithCount(1),ItemDisplayContext.FIXED,be.getLevel(),null,0);
    }
    @Override public void submit(State state,PoseStack pose,SubmitNodeCollector collector,CameraRenderState camera){
        if(state.mainVisible){pose.pushPose();pose.translate(.5,.86+state.bob,.5);pose.mulPose(Axis.YP.rotationDegrees(state.angle));pose.scale(.55f,.55f,.55f);state.main.submit(pose,collector,state.lightCoords,OverlayTexture.NO_OVERLAY,0);pose.popPose();}
        if(state.outputVisible){pose.pushPose();pose.translate(.5,.515,.5);pose.mulPose(Axis.XP.rotationDegrees(90));pose.scale(.42f,.42f,.42f);state.output.submit(pose,collector,state.lightCoords,OverlayTexture.NO_OVERLAY,0);pose.popPose();}
    }
    @Override public AABB getRenderBoundingBox(DisplayStandBlockEntity be){return new AABB(be.getBlockPos()).expandTowards(0,.3,0);}
}
