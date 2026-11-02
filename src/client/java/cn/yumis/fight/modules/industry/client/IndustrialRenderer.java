package cn.yumis.fight.modules.industry.client;
import cn.yumis.fight.modules.industry.platform.WindGeneratorPartBlock;
import cn.yumis.fight.modules.industry.api.RotorMotion;
import cn.yumis.fight.modules.industry.platform.MachineBlockEntity;
import cn.yumis.fight.modules.industry.platform.MachineBlock;
import cn.yumis.fight.modules.industry.platform.IndustrialContent;
import cn.yumis.fight.modules.industry.api.DeviceType;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.*;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.Minecraft;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.Direction;

import java.util.Map;
import java.util.WeakHashMap;

public final class IndustrialRenderer implements BlockEntityRenderer<MachineBlockEntity,IndustrialRenderer.State> {
    private final ItemModelResolver items;
    private final Map<MachineBlockEntity,RotorMotion> rotors=new WeakHashMap<>();
    public static final class State extends BlockEntityRenderState {final ItemStackRenderState gem=new ItemStackRenderState(),icon=new ItemStackRenderState();boolean show,iconVisible,wind,turbine;float angle,bob,rotorAngle,fluidFill;int fluidTint,fluidLight;TextureAtlasSprite fluidSprite;Direction facing;}
    public IndustrialRenderer(BlockEntityRendererProvider.Context context){items=context.itemModelResolver();}
    @Override public State createRenderState(){return new State();}
    @Override public void extractRenderState(MachineBlockEntity be,State state,float partial,Vec3 camera,ModelFeatureRenderer.CrumblingOverlay breaking){
        BlockEntityRenderer.super.extractRenderState(be,state,partial,camera,breaking);
        state.wind=be.device().has(DeviceType.Capability.WIND)&&WindGeneratorPartBlock.complete(be.getLevel(),be.getBlockPos());state.turbine=be.device().has(DeviceType.Capability.ROTOR);
        state.facing=be.getBlockState().getValue(MachineBlock.FACING);
        state.fluidFill=0;state.fluidSprite=null;
        if(be.device().has(DeviceType.Capability.FLUID_STORAGE)){
            var fluid=be.visibleTankFluid();
            if(!fluid.isEmpty()){
                var model=Minecraft.getInstance().getModelManager().getFluidStateModelSet().get(fluid.getFluid().defaultFluidState());
                state.fluidSprite=model.stillMaterial().sprite();state.fluidTint=model.fluidTintSource()==null?-1:model.fluidTintSource().colorAsStack(fluid);
                state.fluidFill=Math.clamp((float)((double)be.visibleTankAmount()/Math.max(1,be.visibleTankCapacity())),0,1);
                state.fluidLight=LightCoordsUtil.pack(Math.max(LightCoordsUtil.block(state.lightCoords),fluid.getFluidType().getLightLevel(fluid)),LightCoordsUtil.sky(state.lightCoords));
            }
        }
        if(state.wind||state.turbine)state.rotorAngle=rotors.computeIfAbsent(be,k->new RotorMotion()).sample(be.getLevel().getGameTime()+partial,be.getBlockState().getValue(MachineBlock.ACTIVE),state.wind?6:12);
        state.iconVisible=be.device().has(DeviceType.Capability.LOGISTICS)||be.device().has(DeviceType.Capability.FLUID_LOGISTICS)||be.device().has(DeviceType.Capability.HARVEST);state.show=state.iconVisible||be.device().has(DeviceType.Capability.STORAGE_NETWORK);if(!state.show)return;
        double age=be.getLevel().getGameTime()+partial;state.angle=(float)((age%180)*2);state.bob=(float)Math.sin((age%200)*Math.PI/100)*.045f;
        items.updateForTopItem(state.gem,IndustrialContent.stack("energy_crystal_"+Math.min(10,be.tier()*2),1),ItemDisplayContext.FIXED,be.getLevel(),null,0);
        if(state.iconVisible)items.updateForTopItem(state.icon,be.device().has(DeviceType.Capability.LOGISTICS)?new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE):be.device().has(DeviceType.Capability.FLUID_LOGISTICS)?new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.WATER_BUCKET):IndustrialContent.stack(be.spec().id(),1),ItemDisplayContext.FIXED,be.getLevel(),null,0);
    }
    @Override public void submit(State state,PoseStack pose,SubmitNodeCollector nodes,CameraRenderState camera){
        if(state.fluidFill>0&&state.fluidSprite!=null)TankFluidMesh.submit(pose,nodes,state.fluidSprite,state.fluidTint,state.fluidFill,state.fluidLight);
        if(state.wind||state.turbine){
            pose.pushPose();pose.translate(.5,0,.5);
            pose.mulPose(Axis.YP.rotationDegrees(switch(state.facing){case EAST->-90;case SOUTH->180;case WEST->90;default->0;}));
            pose.translate(0,state.wind?2.45:.57,-.31);pose.mulPose(Axis.ZP.rotationDegrees(state.rotorAngle));
            ArcaneRotorMesh.submit(state.wind,pose,nodes,state.lightCoords);pose.popPose();
        }
        if(!state.show)return;pose.pushPose();pose.translate(.5,.72+state.bob,.5);pose.mulPose(Axis.YP.rotationDegrees(state.angle));pose.scale(.8f,.8f,.8f);state.gem.submit(pose,nodes,state.lightCoords,OverlayTexture.NO_OVERLAY,0);pose.popPose();
        if(state.iconVisible){pose.pushPose();pose.translate(.5,1.16+state.bob,.5);pose.mulPose(camera.orientation);pose.scale(.3f,.3f,.3f);state.icon.submit(pose,nodes,15728880,OverlayTexture.NO_OVERLAY,0);pose.popPose();}
    }
    @Override public AABB getRenderBoundingBox(MachineBlockEntity be){return be.device().has(DeviceType.Capability.WIND)?new AABB(be.getBlockPos()).expandTowards(0,2,0).inflate(.8,.65,.8):new AABB(be.getBlockPos()).inflate(.1);}
}
