package cn.yumis.fight.modules.industry.client;

import cn.yumis.fight.modules.industry.platform.*;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.Vec3;

/** Only the shared liquid level and always-visible machine port decals are dynamic. */
public final class FluidPipeRenderer implements BlockEntityRenderer<FluidPipeBlockEntity,FluidPipeRenderer.State> {
    private static final Identifier[] PORTS={IndustrialContent.id("textures/block/fluid_pipe_input.png"),IndustrialContent.id("textures/block/fluid_pipe_extract.png")};
    public static final class State extends BlockEntityRenderState {int machines,mask,tint,fluidLight;final int[] modes=new int[6];float fill;TextureAtlasSprite fluid;}
    public FluidPipeRenderer(BlockEntityRendererProvider.Context context){}
    @Override public State createRenderState(){return new State();}
    @Override public void extractRenderState(FluidPipeBlockEntity pipe,State state,float partial,Vec3 camera,ModelFeatureRenderer.CrumblingOverlay breaking){
        BlockEntityRenderer.super.extractRenderState(pipe,state,partial,camera,breaking);state.machines=pipe.machineMask();state.mask=FluidPipeBlock.mask(pipe.getBlockState());state.fluid=null;
        for(Direction side:Direction.values())state.modes[side.ordinal()]=pipe.mode(side).ordinal();
        var fluid=pipe.displayFluid();if(fluid.isEmpty()||pipe.displayCapacity()<=0||!FluidPipeBlock.straight(state.mask))return;
        var model=Minecraft.getInstance().getModelManager().getFluidStateModelSet().get(fluid.getFluid().defaultFluidState());state.fluid=model.stillMaterial().sprite();state.tint=model.fluidTintSource()==null?-1:model.fluidTintSource().colorAsStack(fluid);
        state.fill=(float)Math.clamp(pipe.displayAmount()/(double)pipe.displayCapacity(),0,1);state.fluidLight=LightCoordsUtil.pack(Math.max(LightCoordsUtil.block(state.lightCoords),fluid.getFluidType().getLightLevel(fluid)),LightCoordsUtil.sky(state.lightCoords));
    }
    @Override public void submit(State state,PoseStack pose,SubmitNodeCollector nodes,CameraRenderState camera){
        if(state.fluid!=null&&state.fill>0){
            float low=4.2f/16,high=11.8f/16;float x=low,y=low,z=low,X=high,Y=high,Z=high;
            if(state.mask==48){x=.025f;X=.975f;}else if(state.mask==3){y=.025f;Y=.975f;}else{z=.025f;Z=.975f;}
            liquid(pose,nodes,state,x,y,z,X,y+(Y-y)*state.fill,Z);
        }
        for(Direction side:Direction.values())if((state.machines&FluidPipeBlock.bit(side))!=0&&state.modes[side.ordinal()]!=FluidPipeBlockEntity.Port.CLOSED.ordinal()){
            pose.pushPose();pose.translate(.5,.5,.5);
            switch(side){case SOUTH->pose.mulPose(Axis.YP.rotationDegrees(180));case WEST->pose.mulPose(Axis.YP.rotationDegrees(90));case EAST->pose.mulPose(Axis.YP.rotationDegrees(-90));case UP->pose.mulPose(Axis.XP.rotationDegrees(90));case DOWN->pose.mulPose(Axis.XP.rotationDegrees(-90));default->{}}
            pose.translate(-.5,-.5,-.5);marker(pose,nodes,state.modes[side.ordinal()],state.lightCoords);pose.popPose();
        }
    }
    private static void marker(PoseStack pose,SubmitNodeCollector nodes,int mode,int light){
        // The matching production-source models are fluid_pipe_port_{input,extract}.
        float a=3f/16,b=13f/16,lo=0,hi=2f/16,out=13.02f/16,in=2.98f/16;
        float[][] faces=new float[][]{
            {a,out,lo,a,out,hi,b,out,hi,b,out,lo,0,1,0},
            {b,in,lo,b,in,hi,a,in,hi,a,in,lo,0,-1,0},
            {in,a,lo,in,a,hi,in,b,hi,in,b,lo,-1,0,0},
            {out,b,lo,out,b,hi,out,a,hi,out,a,lo,1,0,0}};
        nodes.submitCustomGeometry(pose,RenderTypes.entityCutout(PORTS[mode]),(matrix,vertices)->{
            for(float[] face:faces)for(int i=0;i<4;i++)vertices.addVertex(matrix,face[i*3],face[i*3+1],face[i*3+2]).setColor(-1).setUv(i>=2?1:0,i==1||i==2?1:0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(matrix,face[12],face[13],face[14]);
        });
    }
    private static void liquid(PoseStack pose,SubmitNodeCollector nodes,State state,float x,float y,float z,float X,float Y,float Z){
        float[][] faces={
            {x,y,z,x,Y,z,X,Y,z,X,y,z,0,0,-1},{X,y,Z,X,Y,Z,x,Y,Z,x,y,Z,0,0,1},
            {x,y,Z,x,Y,Z,x,Y,z,x,y,z,-1,0,0},{X,y,z,X,Y,z,X,Y,Z,X,y,Z,1,0,0},
            {x,Y,z,x,Y,Z,X,Y,Z,X,Y,z,0,1,0},{x,y,Z,x,y,z,X,y,z,X,y,Z,0,-1,0}};
        nodes.submitCustomGeometry(pose,RenderTypes.entityTranslucent(state.fluid.atlasLocation()),(matrix,vertices)->{
            for(float[] face:faces)for(int i=0;i<4;i++)vertices.addVertex(matrix,face[i*3],face[i*3+1],face[i*3+2]).setColor(state.tint).setUv(i>=2?state.fluid.getU1():state.fluid.getU0(),i==1||i==2?state.fluid.getV0():state.fluid.getV1()).setOverlay(OverlayTexture.NO_OVERLAY).setLight(state.fluidLight).setNormal(matrix,face[12],face[13],face[14]);
        });
    }
}
