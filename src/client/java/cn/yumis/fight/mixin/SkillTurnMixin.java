package cn.yumis.fight.mixin;

import cn.yumis.fight.modules.skills.client.Effects;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Content policy suppresses user mouse deltas; scripted orientation remains available. */
@Mixin(MouseHandler.class)
public abstract class SkillTurnMixin {
    @WrapOperation(method="turnPlayer",at=@At(value="INVOKE",target="Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"))
    private void ydnf$turn(LocalPlayer player,double yaw,double pitch,Operation<Void> original){if(!Effects.VFX.blocksTurning())original.call(player,yaw,pitch);}
}
