package cn.yumis.fight.mixin;

import cn.yumis.fight.modules.skills.client.PlayerPoseAnimation;
import cn.yumis.fight.modules.skills.client.PlayerPoseCarrier;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ItemInHandLayer.class)
public abstract class PlayerPoseHeldItemMixin {
    @WrapOperation(method="submitArmWithItem",at=@At(value="INVOKE",
        target="Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"))
    private void ydnf$heldItemPose(ItemStackRenderState item,PoseStack pose,SubmitNodeCollector nodes,int light,int overlay,
                                 int outline,Operation<Void> original,@Local(argsOnly=true) ArmedEntityRenderState state,
                                 @Local(argsOnly=true) HumanoidArm arm){
        var frame=state instanceof PlayerPoseCarrier carrier?carrier.ydnf$skillPose():null;
        int bone=PlayerPoseAnimation.HELD_ITEM;
        if(frame==null||state.mainArm!=arm||(frame.mask&(1<<bone))==0){
            original.call(item,pose,nodes,light,overlay,outline);
            return;
        }
        int offset=bone*PlayerPoseAnimation.AXES;
        pose.pushPose();
        try{
            if(frame.values[offset+3]!=0f||frame.values[offset+4]!=0f||frame.values[offset+5]!=0f){
                pose.translate(frame.values[offset+3]/16f,frame.values[offset+4]/16f,frame.values[offset+5]/16f);
            }
            pose.mulPose(Axis.XP.rotationDegrees(frame.values[offset]));
            pose.mulPose(Axis.YP.rotationDegrees(frame.values[offset+1]));
            pose.mulPose(Axis.ZP.rotationDegrees(frame.values[offset+2]));
            original.call(item,pose,nodes,light,overlay,outline);
        }finally{
            pose.popPose();
        }
    }
}
