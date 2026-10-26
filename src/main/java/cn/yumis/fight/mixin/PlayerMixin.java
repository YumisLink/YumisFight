package cn.yumis.fight.mixin;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(Player.class)
public abstract class PlayerMixin {
    @Inject(method="dropEquipment",at=@At("HEAD"),cancellable=true) private void ydnf$keep(ServerLevel level,CallbackInfo ci){
        ci.cancel();
    }
    @Inject(method="getBaseExperienceReward",at=@At("HEAD"),cancellable=true) private void ydnf$keepExperience(ServerLevel level,CallbackInfoReturnable<Integer> cir){
        cir.setReturnValue(0);
    }
}
