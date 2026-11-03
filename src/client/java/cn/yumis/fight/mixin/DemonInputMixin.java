package cn.yumis.fight.mixin;

import cn.yumis.fight.modules.skills.client.DemonHunterClient;
import net.minecraft.client.player.*;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public abstract class DemonInputMixin {
    @Inject(method="tick",at=@At("RETURN"))
    private void ydnf$approvedMovement(CallbackInfo ci){if(DemonHunterClient.blocksMovement()){var input=(ClientInput)(Object)this;input.keyPresses=Input.EMPTY;((InputAccessor)input).ydnf$move(Vec2.ZERO);}}
}
