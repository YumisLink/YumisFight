package cn.yumis.fight.mixin;
import cn.yumis.fight.bootstrap.ServerApplication;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LivingEntity.class)
public abstract class NativeDamageMixin {
    @Redirect(method="hurtServer",at=@At(value="FIELD",target="Lnet/minecraft/world/entity/LivingEntity;invulnerableTime:I",opcode=180,ordinal=0))
    private int ydnf$collisionCooldown(LivingEntity entity){
        return ServerApplication.ready()&&ServerApplication.get().nativeHits.handles(entity)?0:entity.invulnerableTime;
    }
}
