package cn.yumis.fight.mixin;
import cn.yumis.fight.verification.FeedbackProbe;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(LivingEntity.class)
public abstract class FeedbackProbeMixin {
    @Inject(method="handleDamageEvent",at=@At("HEAD"))
    private void ydnf$observeDamage(DamageSource source,CallbackInfo callback){FeedbackProbe.damage((LivingEntity)(Object)this);}
    @Inject(method="animateHurt",at=@At("HEAD"))
    private void ydnf$observeDirection(float yaw,CallbackInfo callback){FeedbackProbe.direction((LivingEntity)(Object)this);}
}
