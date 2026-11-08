package cn.yumis.fight.mixin;

import cn.yumis.fight.modules.skills.client.DemonHunterClient;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

/** Only the owning client supplies fel movement velocity; native travel owns movement and collision. */
@Mixin(LivingEntity.class)
public abstract class DemonTravelMixin {
    @Inject(method="travel",at=@At("HEAD"))
    private void ydnf$pursuitVelocity(Vec3 input,CallbackInfo ci){
        if((Object)this!=Minecraft.getInstance().player)return;
        Vec3 velocity=DemonHunterClient.movementVelocity();if(velocity!=null)((LivingEntity)(Object)this).setDeltaMovement(velocity);
    }
    @Inject(method="handleOnClimbable",at=@At("RETURN"),cancellable=true)
    private void ydnf$pursuitClimbable(Vec3 input,CallbackInfoReturnable<Vec3> ci){
        if((Object)this!=Minecraft.getInstance().player)return;
        Vec3 velocity=DemonHunterClient.movementVelocity();if(velocity!=null)ci.setReturnValue(velocity);
    }
}
