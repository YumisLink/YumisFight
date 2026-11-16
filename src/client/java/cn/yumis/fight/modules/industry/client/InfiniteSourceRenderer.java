package cn.yumis.fight.modules.industry.client;

import cn.yumis.fight.modules.industry.platform.InfiniteSourceBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.*;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;

/** The item is repeated on all four signs; a configured liquid tank always appears full. */
public final class InfiniteSourceRenderer implements BlockEntityRenderer<InfiniteSourceBlockEntity,InfiniteSourceRenderer.State> {
    public static final class State extends BlockEntityRenderState {
        final ItemStackRenderState item=new ItemStackRenderState();
        boolean showItem;
        TextureAtlasSprite fluid;
        int tint,fluidLight;
    }
    private final ItemModelResolver items;
    public InfiniteSourceRenderer(BlockEntityRendererProvider.Context context){items=context.itemModelResolver();}
    @Override public State createRenderState(){return new State();}
    @Override public void extractRenderState(InfiniteSourceBlockEntity source,State state,float partial,Vec3 camera,ModelFeatureRenderer.CrumblingOverlay breaking){
        BlockEntityRenderer.super.extractRenderState(source,state,partial,camera,breaking);
        state.showItem=false;state.fluid=null;
        if(source.liquid()){
            var fluid=source.sampleFluid();if(fluid.isEmpty())return;
            var model=Minecraft.getInstance().getModelManager().getFluidStateModelSet().get(fluid.getFluid().defaultFluidState());
            state.fluid=model.stillMaterial().sprite();state.tint=model.fluidTintSource()==null?-1:model.fluidTintSource().colorAsStack(fluid);
            state.fluidLight=LightCoordsUtil.pack(Math.max(LightCoordsUtil.block(state.lightCoords),fluid.getFluidType().getLightLevel(fluid)),LightCoordsUtil.sky(state.lightCoords));
        }else{
            var item=source.sampleItem();state.showItem=!item.isEmpty();
            if(state.showItem)items.updateForTopItem(state.item,item,ItemDisplayContext.FIXED,source.getLevel(),null,0);
        }
    }
    @Override public void submit(State state,PoseStack pose,SubmitNodeCollector nodes,CameraRenderState camera){
        if(state.fluid!=null)TankFluidMesh.submit(pose,nodes,state.fluid,state.tint,1,state.fluidLight);
        if(!state.showItem)return;
        for(int side=0;side<4;side++){
            pose.pushPose();pose.translate(.5,.59,.5);pose.mulPose(Axis.YP.rotationDegrees(side*90));pose.translate(0,0,.506);
            pose.scale(.24f,.24f,.045f);state.item.submit(pose,nodes,state.lightCoords,OverlayTexture.NO_OVERLAY,0);pose.popPose();
        }
    }
}
