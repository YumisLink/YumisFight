package cn.yumis.fight.mixin;

import cn.yumis.fight.modules.combat.api.NativeEffects;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.cubemob.AbstractCubeMob;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(AbstractCubeMob.class)
public abstract class NativeCubeAttackMixin {
    @WrapMethod(method="dealDamage")
    private void ydnf$contactScope(LivingEntity target,Operation<Void> original){
        try(var scope=NativeEffects.open(target,(Entity)(Object)this)){original.call(target);}
    }
}
