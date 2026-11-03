package cn.yumis.fight.mixin;

import cn.yumis.fight.modules.skills.client.DemonHunterClient;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Local vision contribution only: never replaces, refreshes or removes actual potion instances. */
@Mixin(LivingEntity.class)
public abstract class DemonVisionMixin {
    @Inject(method="hasEffect",at=@At("RETURN"),cancellable=true)
    private void ydnf$visionPresent(Holder<MobEffect> effect,CallbackInfoReturnable<Boolean> ci){if(!ci.getReturnValue()&&effect==MobEffects.NIGHT_VISION&&(Object)this==Minecraft.getInstance().player&&DemonHunterClient.sight())ci.setReturnValue(true);}
    @Inject(method="getEffect",at=@At("RETURN"),cancellable=true)
    private void ydnf$visionSource(Holder<MobEffect> effect,CallbackInfoReturnable<MobEffectInstance> ci){if(effect==MobEffects.NIGHT_VISION&&(Object)this==Minecraft.getInstance().player&&DemonHunterClient.sight()){var actual=ci.getReturnValue();if(actual==null||actual.getDuration()>=0&&actual.getDuration()<6000)ci.setReturnValue(new MobEffectInstance(MobEffects.NIGHT_VISION,6000,actual==null?0:actual.getAmplifier(),false,false,false));}}
}
