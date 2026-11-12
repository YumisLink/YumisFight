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
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.*;
import java.util.*;

public final class NpcRenderer extends MobRenderer<NpcEntity,AvatarRenderState,PlayerModel> {
    private final PlayerModel wide,slim;
    private final Map<String,PlayerSkin> skins=new HashMap<>();
    private NpcDefinitions definitions;
    public NpcRenderer(EntityRendererProvider.Context context){super(context,new PlayerModel(context.bakeLayer(ModelLayers.PLAYER),false),.45f);wide=model;slim=new PlayerModel(context.bakeLayer(ModelLayers.PLAYER_SLIM),true);}
    @Override public AvatarRenderState createRenderState(){return new AvatarRenderState();}
    @Override public void extractRenderState(NpcEntity entity,AvatarRenderState state,float partial){
        super.extractRenderState(entity,state,partial);HumanoidMobRenderer.extractHumanoidRenderState(entity,state,partial,itemModelResolver);
        if(definitions!=NpcDefinitions.current()){definitions=NpcDefinitions.current();skins.clear();}
        state.skin=skins.computeIfAbsent(entity.skin(),id->{var definition=NpcDefinitions.current().skins.get(id);return PlayerSkin.insecure(new ClientAsset.ResourceTexture(Identifier.fromNamespaceAndPath(ItemData.NS,"entity/npc/"+id)),null,null,definition!=null&&definition.slim()?PlayerModelType.SLIM:PlayerModelType.WIDE);});state.id=entity.getId();
    }
    @Override public Identifier getTextureLocation(AvatarRenderState state){return state.skin.body().texturePath();}
    @Override public void submit(AvatarRenderState state,PoseStack pose,SubmitNodeCollector collector,CameraRenderState camera){model=state.skin.model()==PlayerModelType.SLIM?slim:wide;super.submit(state,pose,collector,camera);}
}
