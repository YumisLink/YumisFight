package cn.yumis.fight.modules.dungeons.client;

import cn.yumis.fight.modules.dungeons.api.DungeonContentTypes;
import cn.yumis.fight.modules.dungeons.api.WaystonePedestalBlock;
import cn.yumis.fight.modules.dungeons.api.WaystonePedestalBlockEntity;
import cn.yumis.fight.modules.equipment.api.Rarity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Locale;

/** Client-only presentation; the existing block state already carries the stone quality. */
public final class WaystonePedestalRenderer implements BlockEntityRenderer<WaystonePedestalBlockEntity,WaystonePedestalRenderer.State> {
    private static final String NS="yumis_dungeon_and_fight";
    private static final Identifier RING=Identifier.fromNamespaceAndPath(NS,"textures/block/waystone_pedestal_glow.png");
    private final ItemModelResolver items;

    public static final class State extends BlockEntityRenderState {
        final ItemStackRenderState stone=new ItemStackRenderState();
        boolean visible;
        int color;
        float angle,bob,pulse;
        Identifier glow;
    }

    public WaystonePedestalRenderer(BlockEntityRendererProvider.Context context){items=context.itemModelResolver();}
    @Override public State createRenderState(){return new State();}

    @Override public void extractRenderState(WaystonePedestalBlockEntity be,State state,float partial,Vec3 camera,ModelFeatureRenderer.CrumblingOverlay breaking){
        BlockEntityRenderer.super.extractRenderState(be,state,partial,camera,breaking);
        int quality=be.getBlockState().getValue(WaystonePedestalBlock.STONE);
        state.visible=quality>0&&be.getLevel()!=null;
        if(!state.visible)return;
        Rarity rarity=Rarity.values()[quality-1];
        String name=rarity.name().toLowerCase(Locale.ROOT);
        state.color=rarity.color();
        state.glow=Identifier.fromNamespaceAndPath(NS,"textures/block/waystone_"+name+"_glow.png");
        double time=be.getLevel().getGameTime()+partial;
        state.angle=(float)((time*.75)%360);
        state.bob=(float)(Math.sin(time*Math.PI/60)*.035);
        state.pulse=(float)((1-Math.cos(time*Math.PI/30))/2);
        var stone=new ItemStack(DungeonContentTypes.WAYSTONE.get());
        stone.set(DataComponents.CUSTOM_MODEL_DATA,new CustomModelData(List.of(),List.of(),List.of(name),List.of()));
        items.updateForTopItem(state.stone,stone,ItemDisplayContext.FIXED,be.getLevel(),null,0);
    }

    @Override public void submit(State state,PoseStack pose,SubmitNodeCollector nodes,CameraRenderState camera){
        if(!state.visible)return;
        int alpha=(int)(85+125*state.pulse);
        pose.pushPose();
        pose.translate(.5,12.25/16,.5);
        float h=.375f;
        quad(pose,nodes,RING,state.color,alpha,new float[]{-h,0,-h,0,0, -h,0,h,0,1, h,0,h,1,1, h,0,-h,1,0},0,1,0);
        pose.popPose();

        pose.pushPose();
        pose.translate(.5,1.18+state.bob,.5);
        pose.mulPose(Axis.YP.rotationDegrees(state.angle));
        pose.scale(.58f,.58f,.58f);
        state.stone.submit(pose,nodes,state.lightCoords,OverlayTexture.NO_OVERLAY,0);
        float z=.034f;
        quad(pose,nodes,state.glow,state.color,alpha,new float[]{-.5f,-.5f,z,0,1, .5f,-.5f,z,1,1, .5f,.5f,z,1,0, -.5f,.5f,z,0,0},0,0,1);
        quad(pose,nodes,state.glow,state.color,alpha,new float[]{.5f,-.5f,-z,0,1, -.5f,-.5f,-z,1,1, -.5f,.5f,-z,1,0, .5f,.5f,-z,0,0},0,0,-1);
        pose.popPose();
    }

    private static void quad(PoseStack pose,SubmitNodeCollector nodes,Identifier texture,int color,int alpha,float[] points,float nx,float ny,float nz){
        int red=(color>>16)&255,green=(color>>8)&255,blue=color&255;
        nodes.submitCustomGeometry(pose,RenderTypes.entityTranslucentEmissive(texture),(matrix,vertices)->{
            for(int i=0;i<4;i++){
                int at=i*5;
                vertices.addVertex(matrix,points[at],points[at+1],points[at+2]).setColor(red,green,blue,alpha)
                    .setUv(points[at+3],points[at+4]).setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xf000f0).setNormal(matrix,nx,ny,nz);
            }
        });
    }

    @Override public AABB getRenderBoundingBox(WaystonePedestalBlockEntity be){return new AABB(be.getBlockPos()).expandTowards(0,1,0);}
}
