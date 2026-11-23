package cn.yumis.fight.mixin;
import cn.yumis.fight.modules.skills.client.PlayerPoseChannel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Applies the sampled skill pose after vanilla setup so body, armor and cape passes share the same bones. */
@Mixin(HumanoidModel.class)
public abstract class HumanoidModelPoseMixin {
    @Inject(method="setupAnim",at=@At("TAIL"))
    private void ydnf$applySkillPose(HumanoidRenderState state,CallbackInfo ci){
        PlayerPoseChannel.apply((HumanoidModel<?>)(Object)this,state);
    }
}
