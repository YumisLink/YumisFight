package cn.yumis.fight.mixin;
import cn.yumis.fight.modules.skills.client.FirstPersonPose;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

/** While the first-person clone submits, the humanoid layer list is filtered to the hand layer only (see FirstPersonPose). */
@Mixin(LivingEntityRenderer.class)
public abstract class FirstPersonPoseLayersMixin {
    @SuppressWarnings({"rawtypes","unchecked"})
    @ModifyExpressionValue(method="submit",at=@At(value="FIELD",opcode=Opcodes.GETFIELD,target="Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;layers:Ljava/util/List;"))
    private List ydnf$handsOnlyLayers(List original){return FirstPersonPose.handsOnlyLayers(original);}
}
