package cn.yumis.fight.client;

import cn.yumis.fight.industry.*;
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
import net.minecraft.world.phys.Vec3;

public final class IndustrialRenderer implements BlockEntityRenderer<MachineBlockEntity,IndustrialRenderer.State> {
    private final ItemModelResolver items;
    public static final class State extends BlockEntityRenderState {final ItemStackRenderState gem=new ItemStackRenderState(),icon=new ItemStackRenderState();boolean show,iconVisible;float angle,bob;}
    public IndustrialRenderer(BlockEntityRendererProvider.Context context){items=context.itemModelResolver();}
    @Override public State createRenderState(){return new State();}
    @Override public void extractRenderState(MachineBlockEntity be,State state,float partial,Vec3 camera,ModelFeatureRenderer.CrumblingOverlay breaking){
        BlockEntityRenderer.super.extractRenderState(be,state,partial,camera,breaking);state.show=be.kind().functional()||be.kind()==DeviceKind.BEACON;state.iconVisible=be.kind().functional();if(!state.show)return;
        double age=be.getLevel().getGameTime()+partial;state.angle=(float)((age%180)*2);state.bob=(float)Math.sin((age%200)*Math.PI/100)*.045f;
        items.updateForTopItem(state.gem,IndustrialContent.stack("energy_crystal_"+Math.min(10,be.tier()*2),1),ItemDisplayContext.FIXED,be.getLevel(),null,0);
        if(state.iconVisible)items.updateForTopItem(state.icon,IndustrialContent.stack(be.spec().id(),1),ItemDisplayContext.FIXED,be.getLevel(),null,0);
    }
    @Override public void submit(State state,PoseStack pose,SubmitNodeCollector nodes,CameraRenderState camera){
        if(!state.show)return;pose.pushPose();pose.translate(.5,.72+state.bob,.5);pose.mulPose(Axis.YP.rotationDegrees(state.angle));pose.scale(.8f,.8f,.8f);state.gem.submit(pose,nodes,state.lightCoords,OverlayTexture.NO_OVERLAY,0);pose.popPose();
        if(state.iconVisible){pose.pushPose();pose.translate(.5,1.16+state.bob,.5);pose.mulPose(camera.orientation);pose.scale(.3f,.3f,.3f);state.icon.submit(pose,nodes,15728880,OverlayTexture.NO_OVERLAY,0);pose.popPose();}
    }
}
