package cn.yumis.fight.mixin;

import cn.yumis.fight.modules.combat.api.NativeEffects;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.phys.*;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Projectile.class)
public abstract class NativeProjectileScopeMixin {
    @WrapMethod(method="hitTargetOrDeflectSelf")
    private ProjectileDeflection ydnf$collisionScope(HitResult result,Operation<ProjectileDeflection> original){
        var projectile=(Projectile)(Object)this;
        try(var scope=NativeEffects.open(result instanceof EntityHitResult hit?hit.getEntity():null,projectile.getOwner())){return original.call(result);}
    }
}
