package cn.yumis.fight.mixin;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ServerPlayer.class)
public abstract class RespawnMixin {
    @Inject(method="restoreFrom",at=@At("RETURN")) private void ydnf$keep(ServerPlayer old,boolean all,CallbackInfo ci){
        ServerPlayer p=(ServerPlayer)(Object)this;
        p.getInventory().replaceWith(old.getInventory());
        p.experienceLevel=old.experienceLevel;
        p.experienceProgress=old.experienceProgress;
        p.totalExperience=old.totalExperience;
        p.setScore(old.getScore());
    }
}
