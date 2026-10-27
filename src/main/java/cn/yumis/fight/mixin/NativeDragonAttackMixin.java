package cn.yumis.fight.mixin;

import cn.yumis.fight.server.NativeEffects;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(EnderDragon.class)
public abstract class NativeDragonAttackMixin {
    @WrapMethod(method={"knockBack(Lnet/minecraft/server/level/ServerLevel;Ljava/util/List;)V","hurt(Lnet/minecraft/server/level/ServerLevel;Ljava/util/List;)V"})
    private void ydnf$perVictimScope(ServerLevel level,List<Entity> targets,Operation<Void> original){
        for(Entity target:targets)try(var scope=NativeEffects.open(target,(Entity)(Object)this)){original.call(level,List.of(target));}
    }
}
