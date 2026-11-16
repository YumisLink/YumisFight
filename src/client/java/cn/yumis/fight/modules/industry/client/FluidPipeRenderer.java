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
import static cn.yumis.fight.modules.industry.platform.FluidPipeGeometry.*;

/** Shared liquid, extraction collars and machine port decals follow the synchronized ports. */
public final class FluidPipeRenderer implements BlockEntityRenderer<FluidPipeBlockEntity,FluidPipeRenderer.State> {
    private static final Identifier[] PORTS={IndustrialContent.id("textures/block/fluid_pipe_input.png"),IndustrialContent.id("textures/block/fluid_pipe_extract.png")};
    private static final Identifier[] MATERIALS={IndustrialContent.id("textures/block/fluid_pipe_12x14_t1.png"),IndustrialContent.id("textures/block/fluid_pipe_12x14_t2.png"),IndustrialContent.id("textures/block/fluid_pipe_12x14_t3.png")};
    public static final class State extends BlockEntityRenderState {int machines,mask,extract,grade,tint,fluidLight;final int[] modes=new int[6];float fill;TextureAtlasSprite fluid;}
    public FluidPipeRenderer(BlockEntityRendererProvider.Context context){}
    @Override public State createRenderState(){return new State();}
    @Override public void extractRenderState(FluidPipeBlockEntity pipe,State state,float partial,Vec3 camera,ModelFeatureRenderer.CrumblingOverlay breaking){
        BlockEntityRenderer.super.extractRenderState(pipe,state,partial,camera,breaking);state.machines=pipe.machineMask();state.mask=FluidPipeBlock.mask(pipe.getBlockState());state.fluid=null;
        state.extract=pipe.extractionMask();state.grade=((FluidPipeBlock)pipe.getBlockState().getBlock()).grade;
        for(Direction side:Direction.values())state.modes[side.ordinal()]=pipe.mode(side).ordinal();
        var fluid=pipe.displayFluid();if(fluid.isEmpty()||pipe.displayCapacity()<=0)return;
        var model=Minecraft.getInstance().getModelManager().getFluidStateModelSet().get(fluid.getFluid().defaultFluidState());state.fluid=model.stillMaterial().sprite();state.tint=model.fluidTintSource()==null?-1:model.fluidTintSource().colorAsStack(fluid);
        state.fill=(float)Math.clamp(pipe.displayAmount()/(double)pipe.displayCapacity(),0,1);state.fluidLight=LightCoordsUtil.pack(Math.max(LightCoordsUtil.block(state.lightCoords),fluid.getFluidType().getLightLevel(fluid)),LightCoordsUtil.sky(state.lightCoords));
    }
    @Override public void submit(State state,PoseStack pose,SubmitNodeCollector nodes,CameraRenderState camera){
        if(state.fluid!=null&&state.fill>0){
            liquid(pose,nodes,state);
        }
        for(Direction side:Direction.values())if((state.machines&state.mask&FluidPipeBlock.bit(side))!=0&&state.modes[side.ordinal()]!=FluidPipeBlockEntity.Port.CLOSED.ordinal()){
            pose.pushPose();pose.translate(.5,.5,.5);
            switch(side){case SOUTH->pose.mulPose(Axis.YP.rotationDegrees(180));case WEST->pose.mulPose(Axis.YP.rotationDegrees(90));case EAST->pose.mulPose(Axis.YP.rotationDegrees(-90));case UP->pose.mulPose(Axis.XP.rotationDegrees(90));case DOWN->pose.mulPose(Axis.XP.rotationDegrees(-90));default->{}}
            pose.translate(-.5,-.5,-.5);
            if((state.extract&FluidPipeBlock.bit(side))!=0)collar(pose,nodes,state.grade,state.lightCoords);
            marker(pose,nodes,state.modes[side.ordinal()],state.lightCoords);pose.popPose();
        }
    }
    private static void collar(PoseStack pose,SubmitNodeCollector nodes,int grade,int light){
        nodes.submitCustomGeometry(pose,RenderTypes.entityCutout(MATERIALS[grade-1]),(matrix,vertices)->{
            for(float[] quad:EXTRACT_COLLAR)for(int i=0;i<4;i++)vertices.addVertex(matrix,quad[i*5],quad[i*5+1],quad[i*5+2]).setColor(-1).setUv(quad[i*5+3],quad[i*5+4]).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(matrix,quad[20],quad[21],quad[22]);
        });
    }
    private static void marker(PoseStack pose,SubmitNodeCollector nodes,int mode,int light){
        // The matching production-source models are fluid_pipe_port_{input,extract}.
        int min=mode==FluidPipeBlockEntity.Port.EXTRACT.ordinal()?RIM_MIN:BODY_MIN,max=mode==FluidPipeBlockEntity.Port.EXTRACT.ordinal()?RIM_MAX:BODY_MAX;
        float a=min/16f,b=max/16f,lo=0,hi=PORT_DEPTH/16f,out=(max+.02f)/16,in=(min-.02f)/16;
        float[][] faces=new float[][]{
            {a,out,lo,a,out,hi,b,out,hi,b,out,lo,0,1,0},
            {b,in,lo,b,in,hi,a,in,hi,a,in,lo,0,-1,0},
            {in,a,lo,in,a,hi,in,b,hi,in,b,lo,-1,0,0},
            {out,b,lo,out,b,hi,out,a,hi,out,a,lo,1,0,0}};
        nodes.submitCustomGeometry(pose,RenderTypes.entityCutout(PORTS[mode]),(matrix,vertices)->{
            for(float[] face:faces)for(int i=0;i<4;i++)vertices.addVertex(matrix,face[i*3],face[i*3+1],face[i*3+2]).setColor(-1).setUv(i>=2?1:0,i==1||i==2?1:0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(matrix,face[12],face[13],face[14]);
        });
    }
    private static void liquid(PoseStack pose,SubmitNodeCollector nodes,State state){
        // The production model has .4px glass walls; leave another .1px of clearance.
        float low=(BODY_MIN+WALL+.1f)/16,high=(BODY_MAX-WALL-.1f)/16;
        float bottom=(state.mask&FluidPipeBlock.bit(Direction.DOWN))!=0?.025f:low;
        float top=(state.mask&FluidPipeBlock.bit(Direction.UP))!=0?.975f:high;
        float level=bottom+(top-bottom)*state.fill;
        java.util.List<float[]> volumes=new java.util.ArrayList<>();
        int open=state.mask;
        if(level<=high)open&=~FluidPipeBlock.bit(Direction.UP);
        if(level>low)volumes.add(new float[]{low,low,low,high,Math.min(level,high),high,open});
        for(Direction side:Direction.values())if((state.mask&FluidPipeBlock.bit(side))!=0){
            float x=low,y=low,z=low,X=high,Y=high,Z=high;
            switch(side){case DOWN->{y=.025f;Y=low;}case UP->{y=high;Y=.975f;}case NORTH->{z=.025f;Z=low;}case SOUTH->{z=high;Z=.975f;}case WEST->{x=.025f;X=low;}case EAST->{x=high;X=.975f;}}
            Y=Math.min(Y,level);if(Y<=y)continue;
            int armOpen=FluidPipeBlock.bit(side.getOpposite());
            if(side==Direction.DOWN&&level<=low)armOpen=0;
            volumes.add(new float[]{x,y,z,X,Y,Z,armOpen});
        }
        nodes.submitCustomGeometry(pose,RenderTypes.entityTranslucent(state.fluid.atlasLocation()),(matrix,vertices)->{
            for(float[] volume:volumes){
                float[][] faces=liquidFaces(volume);
                for(int side=0;side<faces.length;side++)if(((int)volume[6]&(1<<side))==0){
                    float[] face=faces[side];
                    for(int i=0;i<4;i++)vertices.addVertex(matrix,face[i*3],face[i*3+1],face[i*3+2]).setColor(state.tint).setUv(i>=2?state.fluid.getU1():state.fluid.getU0(),i==1||i==2?state.fluid.getV0():state.fluid.getV1()).setOverlay(OverlayTexture.NO_OVERLAY).setLight(state.fluidLight).setNormal(matrix,face[12],face[13],face[14]);
                }
            }
        });
    }
    private static float[][] liquidFaces(float[] volume){
        float x=volume[0],y=volume[1],z=volume[2],X=volume[3],Y=volume[4],Z=volume[5];
        // Direction ordinal order matches the saved connection mask; shared faces stay hidden.
        return new float[][]{
            {x,y,Z,x,y,z,X,y,z,X,y,Z,0,-1,0},{x,Y,z,x,Y,Z,X,Y,Z,X,Y,z,0,1,0},
            {x,y,z,x,Y,z,X,Y,z,X,y,z,0,0,-1},{X,y,Z,X,Y,Z,x,Y,Z,x,y,Z,0,0,1},
            {x,y,Z,x,Y,Z,x,Y,z,x,y,z,-1,0,0},{X,y,z,X,Y,z,X,Y,Z,X,y,Z,1,0,0}};
    }
}
