package cn.yumis.fight.mixin;
import cn.yumis.fight.client.ClientCombat;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(LocalPlayer.class)
public abstract class StormInputMixin {
    @Inject(method="sendPosition",at=@At("HEAD")) private void ydnf$anchor(CallbackInfo ci){
        var cast=ClientCombat.INSTANCE.cast;
        if(cast!=null&&cast.skill==cn.yumis.fight.rules.Rules.Skill.STORM){
            LocalPlayer player=(LocalPlayer)(Object)this;
            player.setPos(cast.origin);
            player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        }
    }
}
