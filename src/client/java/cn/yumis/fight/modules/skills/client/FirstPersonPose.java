package cn.yumis.fight.modules.skills.client;

import cn.yumis.fight.modules.presentation.api.client.ClientUi;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.PartNames;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.layers.PlayerItemInHandLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;

import java.util.List;

/** First-person Better-Combat style: while a skill pose document plays, the vanilla hand pass is replaced by the player's own model, so arms and weapon follow the same keyframe bones as third person. */
public final class FirstPersonPose {
    private FirstPersonPose(){
    }
    /** Set only while the camera-space clone submits, so the layer filter applies to that single pass. */
    private static boolean handsOnlyPass;
    /** True when the camera-space clone was submitted for this frame; the caller then skips the vanilla hands. */
    public static boolean submit(LocalPlayer player,float partial,PoseStack pose,SubmitNodeCollector nodes,int light){
        var mc=Minecraft.getInstance();
        // The hand pass only runs in first person; detached means a cinematic camera already shows the world model.
        if(!mc.options.getCameraType().isFirstPerson()||mc.gameRenderer.mainCamera().isDetached())return false;
        var sample=Effects.VFX.playerPose(player.getUUID(),ClientUi.now());
        if(sample==null)return false;
        // extractEntity reuses the third-person pipeline (AvatarRendererMixin feeds the pose frame, warglaive twin and witch hat), so the clone matches the world model one-to-one.
        EntityRenderDispatcher dispatcher=mc.getEntityRenderDispatcher();
        EntityRenderState state=dispatcher.extractEntity(player,partial);
        state.nameTag=null;state.scoreText=null;state.leashStates=null;
        if(!(state instanceof AvatarRenderState avatar))return false;
        if(avatar instanceof PlayerPoseCarrier carrier)carrier.ydnf$skillPose(sample.frame());
        avatar.overrideModelPartVisibility(PartNames.HEAD,false);
        avatar.overrideModelPartVisibility(PartNames.BODY,false);
        // The hand pass already uses camera space. A body yaw of 180 faces down its -Z axis.
        var aim=sample.lockedAim();
        float relativeYaw=aim==null?0:net.minecraft.util.Mth.wrapDegrees(PlayerPoseChannel.yaw(aim)-player.getViewYRot(partial));
        float relativePitch=aim==null?0:player.getViewXRot(partial)
            -(float)Math.toDegrees(Math.atan2(-aim.y,aim.horizontalDistance()));
        avatar.bodyRot=180+relativeYaw;
        avatar.yRot=avatar.xRot=0;
        pose.pushPose();
        pose.mulPose(Axis.XP.rotationDegrees(relativePitch));
        pose.translate(0,-player.getEyeHeight(),0);
        try{
            @SuppressWarnings({"unchecked","rawtypes"})
            EntityRenderer<Entity,EntityRenderState> renderer=(EntityRenderer)dispatcher.getRenderer(player);
            handsOnlyPass=true;
            try{
                renderer.submit(state,pose,nodes,null);
            }finally{
                handsOnlyPass=false;
            }
        }finally{
            pose.popPose();
        }
        return true;
    }
    /** Headwear, armor, capes and shoulder riders attach to head or body and would block the view or float; the clone pass keeps only the hand layer. */
    public static List<Object> handsOnlyLayers(List<Object> layers){
        if(!handsOnlyPass)return layers;
        for(Object layer:layers)if(layer instanceof PlayerItemInHandLayer)return List.of(layer);
        return List.of();
    }
}
