package cn.yumis.fight.modules.industry.client;

import cn.yumis.fight.modules.industry.platform.*;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.*;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.*;

/** One continuous UV phase for adjoining belts, plus identity-preserving cargo handoffs. */
public final class ConveyorRenderer implements BlockEntityRenderer<ConveyorBlockEntity,ConveyorRenderer.State> {
    private static final Identifier BELT=IndustrialContent.id("textures/block/conveyor_surface.png");
    private static final Identifier METAL=Identifier.withDefaultNamespace("textures/block/anvil.png");
    public static final class State extends BlockEntityRenderState {
        final ItemStackRenderState[] items={new ItemStackRenderState(),new ItemStackRenderState()};
        final float[] scales=new float[2];final double[] heights=new double[2];
        final Vec3[] positions=new Vec3[2];int count;float phase,roller;Direction facing;ConveyorBlock.Slope slope;
    }
    private final ItemModelResolver items;
    public ConveyorRenderer(BlockEntityRendererProvider.Context context){items=context.itemModelResolver();}
    @Override public State createRenderState(){return new State();}
    @Override public void extractRenderState(ConveyorBlockEntity belt,State state,float partial,Vec3 camera,ModelFeatureRenderer.CrumblingOverlay breaking){
        BlockEntityRenderer.super.extractRenderState(belt,state,partial,camera,breaking);state.count=0;state.facing=belt.direction();state.slope=belt.getBlockState().getValue(ConveyorBlock.SLOPE);
        double time=belt.getLevel().getGameTime()+partial;state.phase=(float)((time*belt.step())%1);state.roller=(float)(-time*.8*belt.speed()%(Math.PI*2));
        var neighbor=belt.getLevel().getBlockEntity(belt.downstream());
        var onward=neighbor instanceof ConveyorBlockEntity next?next.visuals(partial):java.util.List.<ConveyorBlockEntity.Visual>of();
        for(var cargo:belt.visuals(partial)){
            // During packet skew, the downstream copy owns an already handed-off identity.
            if(cargo.progress()>=.5&&onward.stream().anyMatch(next->next.id().equals(cargo.id())))continue;
            int index=state.count++;state.positions[index]=ConveyorBlockEntity.path(cargo.entry(),belt.direction(),cargo.progress());
            items.updateForTopItem(state.items[index],cargo.stack().copyWithCount(1),ItemDisplayContext.FIXED,belt.getLevel(),null,cargo.id().hashCode());
            var bounds=state.items[index].getModelBoundingBox();
            state.scales[index]=(float)Math.min(.32,.5/Math.max(bounds.getXsize(),bounds.getYsize()));
            // -90 degrees about X maps model Z to world Y. Place its lowest point one pixel above the belt.
            state.heights[index]=ConveyorGeometry.SURFACE_HEIGHT+ConveyorConnections.lift(belt.getBlockState(),state.positions[index].x,state.positions[index].z)+ConveyorGeometry.ITEM_CLEARANCE-bounds.minZ*state.scales[index];
            state.positions[index]=state.positions[index].add(-(bounds.minX+bounds.maxX)*.5*state.scales[index],0,(bounds.minY+bounds.maxY)*.5*state.scales[index]);
        }
    }
    @Override public void submit(State state,PoseStack pose,SubmitNodeCollector nodes,CameraRenderState camera){
        pose.pushPose();pose.translate(.5,0,.5);pose.mulPose(Axis.YP.rotationDegrees(switch(state.facing){case EAST->-90;case SOUTH->180;case WEST->90;default->0;}));pose.translate(-.5,0,-.5);
        float split=1-state.phase;
        surface(pose,nodes,state.lightCoords,state.slope,0,split,state.phase,1);
        if(state.phase>0)surface(pose,nodes,state.lightCoords,state.slope,split,1,0,state.phase);
        for(float z:new float[]{.125f,.875f}){
            pose.pushPose();pose.translate(.5,ConveyorGeometry.ROLLER_HEIGHT+lift(state.slope,z),z);pose.mulPose(Axis.XP.rotation(state.roller));roller(pose,nodes,state.lightCoords);pose.popPose();
        }
        pose.popPose();
        for(int i=0;i<state.count;i++){
            Vec3 position=state.positions[i];pose.pushPose();pose.translate(position.x,state.heights[i],position.z);
            pose.mulPose(Axis.XP.rotationDegrees(-90));pose.scale(state.scales[i],state.scales[i],state.scales[i]);
            state.items[i].submit(pose,nodes,state.lightCoords,OverlayTexture.NO_OVERLAY,0);pose.popPose();
        }
    }
    private static float lift(ConveyorBlock.Slope slope,float z){return switch(slope){case UP->1-z;case DOWN->z;default->0;};}
    private static void surface(PoseStack pose,SubmitNodeCollector nodes,int light,ConveyorBlock.Slope slope,float z,float Z,float v,float V){
        if(Z-z<.00001)return;
        float top=(float)ConveyorGeometry.SURFACE_HEIGHT;
        nodes.submitCustomGeometry(pose,RenderTypes.entityCutout(BELT),(matrix,vertices)->{
            float n=slope==ConveyorBlock.Slope.FLAT?1:.70710677f,nz=slope==ConveyorBlock.Slope.UP?n:slope==ConveyorBlock.Slope.DOWN?-n:0;
            for(float[] p:new float[][]{{.0625f,top+lift(slope,z),z,0,v},{.0625f,top+lift(slope,Z),Z,0,V},{.9375f,top+lift(slope,Z),Z,1,V},{.9375f,top+lift(slope,z),z,1,v}})
                vertices.addVertex(matrix,p[0],p[1],p[2]).setColor(-1).setUv(p[3],p[4]).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(matrix,0,n,nz);
        });
    }
    private static void roller(PoseStack pose,SubmitNodeCollector nodes,int light){
        nodes.submitCustomGeometry(pose,RenderTypes.entityCutout(METAL),(matrix,vertices)->{
            for(int face=0;face<8;face++){
                double a=face*Math.PI/4,b=(face+1)*Math.PI/4;float y=(float)Math.cos(a)*.125f,z=(float)Math.sin(a)*.125f,Y=(float)Math.cos(b)*.125f,Z=(float)Math.sin(b)*.125f;
                float ny=(float)Math.cos((a+b)/2),nz=(float)Math.sin((a+b)/2);int shade=face%2==0?0xffa0a4a7:0xff666b70;
                for(float[] p:new float[][]{{-.4375f,y,z,0,0},{-.4375f,Y,Z,0,.25f},{.4375f,Y,Z,1,.25f},{.4375f,y,z,1,0}})
                    vertices.addVertex(matrix,p[0],p[1],p[2]).setColor(shade).setUv(p[3],p[4]).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(matrix,0,ny,nz);
            }
        });
    }
    @Override public AABB getRenderBoundingBox(ConveyorBlockEntity belt){return new AABB(belt.getBlockPos()).inflate(.25,1.1,.25);}
}
