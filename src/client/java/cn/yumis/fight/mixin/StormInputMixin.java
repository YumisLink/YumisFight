package cn.yumis.fight.mixin;

import cn.yumis.fight.modules.skills.api.SkillCatalog;

import cn.yumis.fight.modules.skills.api.client.SkillController;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(LocalPlayer.class)
public abstract class StormInputMixin {
    @Inject(method="sendPosition",at=@At("HEAD")) private void ydnf$anchor(CallbackInfo ci){
        var cast=SkillController.INSTANCE.cast;
        if(cast!=null&&cast.skill==cn.yumis.fight.modules.skills.api.SkillCatalog.STORM){
            LocalPlayer player=(LocalPlayer)(Object)this;
            player.setPos(cast.origin);
            player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        }
    }
}
