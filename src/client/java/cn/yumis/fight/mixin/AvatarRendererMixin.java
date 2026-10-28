package cn.yumis.fight.mixin;
import cn.yumis.fight.modules.skills.client.Effects;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** Uses the reference's final extraction point, after native avatar state is complete. */
@Mixin(EntityRenderDispatcher.class)
public abstract class AvatarRendererMixin {
    @Inject(method="extractEntity",at=@At("RETURN"))
    private void ydnf$choreography(Entity entity,float delta,CallbackInfoReturnable<EntityRenderState> ci){
        if(entity instanceof Player player)Effects.VFX.animate(player,ci.getReturnValue());
    }
}
