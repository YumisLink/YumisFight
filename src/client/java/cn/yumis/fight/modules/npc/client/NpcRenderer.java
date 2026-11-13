package cn.yumis.fight.modules.npc.client;

import cn.yumis.fight.modules.npc.api.*;
import cn.yumis.fight.platform.ItemData;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.*;
import java.util.*;

public final class NpcRenderer extends MobRenderer<NpcEntity,NpcRenderer.State,PlayerModel> {
    public static final class State extends AvatarRenderState {public boolean witchHat;}
    private final PlayerModel wide,slim;
    private net.minecraft.world.item.ItemStack witchHat;
    // Renderers can be created before item components are bound during resource loading.
    private net.minecraft.world.item.ItemStack witchHat(){
        if(witchHat==null)witchHat=cn.yumis.fight.modules.equipment.api.EquipmentItems.visualHeadwear("elaina_witch_hat");
        return witchHat;
    }
    private final Map<String,PlayerSkin> skins=new HashMap<>();
    private NpcDefinitions definitions;
    public NpcRenderer(EntityRendererProvider.Context context){super(context,new PlayerModel(context.bakeLayer(ModelLayers.PLAYER),false),.45f);wide=model;slim=new PlayerModel(context.bakeLayer(ModelLayers.PLAYER_SLIM),true);addLayer(new HatLayer(this));}
    @Override public State createRenderState(){return new State();}
    @Override public void extractRenderState(NpcEntity entity,State state,float partial){
        super.extractRenderState(entity,state,partial);HumanoidMobRenderer.extractHumanoidRenderState(entity,state,partial,itemModelResolver);
        if(definitions!=NpcDefinitions.current()){definitions=NpcDefinitions.current();skins.clear();}
        state.skin=skins.computeIfAbsent(entity.skin(),id->{var definition=NpcDefinitions.current().skins.get(id);return PlayerSkin.insecure(new ClientAsset.ResourceTexture(Identifier.fromNamespaceAndPath(ItemData.NS,"entity/npc/"+id)),null,null,definition!=null&&definition.slim()?PlayerModelType.SLIM:PlayerModelType.WIDE);});state.id=entity.getId();
        var definition=NpcInteractionComponent.query(entity);state.witchHat=definition!=null&&"witch_hat".equals(definition.headwear());
        if(state.witchHat){itemModelResolver.updateForLiving(state.headItem,witchHat(),net.minecraft.world.item.ItemDisplayContext.HEAD,entity);state.boundingBoxHeight=Math.max(state.boundingBoxHeight,3.05f*state.scale);if(state.nameTagAttachment!=null)state.nameTagAttachment=state.nameTagAttachment.add(0,state.scale,0);}
    }
    @Override public Identifier getTextureLocation(State state){return state.skin.body().texturePath();}
    @Override public void submit(State state,PoseStack pose,SubmitNodeCollector collector,CameraRenderState camera){model=state.skin.model()==PlayerModelType.SLIM?slim:wide;super.submit(state,pose,collector,camera);}
    private static final class HatLayer extends RenderLayer<State,PlayerModel> {
        HatLayer(NpcRenderer renderer){super(renderer);}
        @Override public void submit(PoseStack pose,SubmitNodeCollector collector,int light,State state,float yaw,float pitch){
            if(!state.witchHat||state.isInvisible)return;
            pose.pushPose();getParentModel().head.translateAndRotate(pose);
            net.minecraft.client.renderer.entity.layers.CustomHeadLayer.translateToHead(pose,net.minecraft.client.renderer.entity.layers.CustomHeadLayer.Transforms.DEFAULT);
            state.headItem.submit(pose,collector,light,OverlayTexture.NO_OVERLAY,0);pose.popPose();
        }
    }
}
