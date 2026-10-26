package cn.yumis.fight.mixin;
import cn.yumis.fight.client.ClientBootstrap;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(KeyboardHandler.class)
public abstract class KeyboardMixin {
    @Inject(method="keyPress",at=@At("HEAD"),cancellable=true)
    private void ydnf$key(long handle,int action,KeyEvent event,CallbackInfo ci){
        if(ClientBootstrap.key(event,action))ci.cancel();
    }
}
