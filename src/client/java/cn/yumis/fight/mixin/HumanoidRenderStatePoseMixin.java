package cn.yumis.fight.mixin;
import cn.yumis.fight.modules.skills.client.PlayerPoseAnimation;
import cn.yumis.fight.modules.skills.client.PlayerPoseCarrier;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Lets every humanoid render state carry the skill pose sampled for the current frame. */
@Mixin(HumanoidRenderState.class)
public abstract class HumanoidRenderStatePoseMixin implements PlayerPoseCarrier {
    @Unique
    private PlayerPoseAnimation.Frame ydnf$skillPose;
    @Override
    public PlayerPoseAnimation.Frame ydnf$skillPose(){return ydnf$skillPose;}
    @Override
    public void ydnf$skillPose(PlayerPoseAnimation.Frame frame){ydnf$skillPose=frame;}
}
