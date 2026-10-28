package cn.yumis.fight.mixin;
import cn.yumis.fight.bootstrap.ServerApplication;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
@Mixin(EnderDragon.class)
public abstract class DragonCrystalMixin {
    @Redirect(method="checkCrystals",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/boss/enderdragon/EnderDragon;setHealth(F)V"))
    private void ydnf$worldHealing(EnderDragon dragon,float amount){
        if(!cn.yumis.fight.bootstrap.ServerApplication.ready())dragon.setHealth(amount);
    }
}
