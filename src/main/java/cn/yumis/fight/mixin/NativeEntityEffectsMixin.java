package cn.yumis.fight.mixin;

import cn.yumis.fight.server.NativeEffects;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Entity.class)
public abstract class NativeEntityEffectsMixin {
    @WrapMethod(method="igniteForSeconds")
    private void ydnf$deferIgnition(float seconds,Operation<Void> original){if(!NativeEffects.defer((Entity)(Object)this,()->original.call(seconds)))original.call(seconds);}
    @WrapMethod(method="push(DDD)V")
    private void ydnf$deferImpulse(double x,double y,double z,Operation<Void> original){if(!NativeEffects.defer((Entity)(Object)this,()->original.call(x,y,z)))original.call(x,y,z);}
}
