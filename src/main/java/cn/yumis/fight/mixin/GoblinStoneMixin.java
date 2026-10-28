package cn.yumis.fight.mixin;
import cn.yumis.fight.modules.dungeons.api.DungeonEnemy;
import cn.yumis.fight.bootstrap.ServerApplication;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(Snowball.class)
public abstract class GoblinStoneMixin {
    @ModifyArg(method="onHitEntity",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/Entity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)V"),index=1)
    private float ydnf$stoneDamage(float damage){
        var snowball=(Snowball)(Object)this;
        return damage==0&&ServerApplication.ready()&&snowball.getOwner() instanceof DungeonEnemy owner&&BuiltInRegistries.ENTITY_TYPE.getKey(owner.getType()).getPath().equals("goblin_thrower")?1:damage;
    }
}
