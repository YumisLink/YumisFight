package cn.yumis.fight.mixin;

import cn.yumis.fight.modules.combat.api.NativeEffects;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.*;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(LivingEntity.class)
public abstract class NativeLivingEffectsMixin {
    @WrapMethod(method="hurtServer")
    private boolean ydnf$damageScope(ServerLevel level,DamageSource source,float damage,Operation<Boolean> original){
        var victim=(LivingEntity)(Object)this;
        int hurt=victim.hurtTime,duration=victim.hurtDuration,invulnerable=victim.invulnerableTime;
        try(var scope=NativeEffects.open(victim,source.getEntity())){
            try{return original.call(level,source,damage);}
            finally{if(scope.deferred()){victim.hurtTime=hurt;victim.hurtDuration=duration;victim.invulnerableTime=invulnerable;}}
        }
    }
    @WrapMethod(method="addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z")
    private boolean ydnf$deferEffect(MobEffectInstance effect,Entity source,Operation<Boolean> original){
        MobEffectInstance snapshot=new MobEffectInstance(effect);
        if(NativeEffects.defer((LivingEntity)(Object)this,()->original.call(snapshot,source)))return true;
        return original.call(effect,source);
    }
    @WrapMethod(method="knockback(DDDLnet/minecraft/world/damagesource/DamageSource;FZ)V")
    private void ydnf$deferKnockback(double strength,double x,double z,DamageSource source,float damage,boolean effect,Operation<Void> original){
        if(!NativeEffects.defer((LivingEntity)(Object)this,()->original.call(strength,x,z,source,damage,effect)))original.call(strength,x,z,source,damage,effect);
    }
    @WrapMethod(method="setArrowCount")
    private void ydnf$deferArrow(int count,Operation<Void> original){
        var victim=(LivingEntity)(Object)this;int delta=count-victim.getArrowCount();
        if(!NativeEffects.defer(victim,()->original.call(Math.max(0,victim.getArrowCount()+delta))))original.call(count);
    }
}
