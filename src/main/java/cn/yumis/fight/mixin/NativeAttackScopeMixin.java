package cn.yumis.fight.mixin;

import cn.yumis.fight.server.NativeEffects;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(targets={"net.minecraft.world.entity.LivingEntity","net.minecraft.world.entity.Mob","net.minecraft.world.entity.animal.bee.Bee","net.minecraft.world.entity.animal.panda.Panda","net.minecraft.world.entity.animal.golem.IronGolem","net.minecraft.world.entity.monster.Creeper","net.minecraft.world.entity.monster.zombie.Zombie","net.minecraft.world.entity.monster.creaking.Creaking","net.minecraft.world.entity.monster.zombie.Husk","net.minecraft.world.entity.monster.Zoglin","net.minecraft.world.entity.monster.Ravager","net.minecraft.world.entity.monster.hoglin.Hoglin","net.minecraft.world.entity.monster.spider.CaveSpider","net.minecraft.world.entity.monster.skeleton.WitherSkeleton","net.minecraft.world.entity.monster.warden.Warden"})
public abstract class NativeAttackScopeMixin {
    @WrapMethod(method="doHurtTarget")
    private boolean ydnf$attackScope(ServerLevel level,Entity target,Operation<Boolean> original){
        try(var scope=NativeEffects.open(target,(Entity)(Object)this)){return original.call(level,target);}
    }
}
