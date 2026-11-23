package cn.yumis.fight.mixin;

import cn.yumis.fight.modules.skills.client.FirstPersonPose;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import org.spongepowered.asm.mixin.Mixin;

/** Replaces the vanilla first-person hands with the animated player model while a skill pose document is playing. */
@Mixin(ItemInHandRenderer.class)
public abstract class FirstPersonPoseHandMixin {
    @WrapMethod(method="submitHandsWithItems")
    private void ydnf$skillPoseHands(float partial,PoseStack pose,SubmitNodeCollector nodes,LocalPlayer player,int light,Operation<Void> original){
        if(FirstPersonPose.submit(player,partial,pose,nodes,light))return;
        original.call(partial,pose,nodes,player,light);
    }
}
