package cn.yumis.fight.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Only suppress damage tilt. Death, walking and skill camera choreography keep their own paths. */
@Mixin(GameRenderer.class)
public abstract class HurtCameraMixin {
    @ModifyExpressionValue(method="bobHurt",at=@At(value="FIELD",target="Lnet/minecraft/client/renderer/state/OptionsRenderState;damageTiltStrength:D"))
    private double ydnf$hurtTilt(double original){return 0;}
}
