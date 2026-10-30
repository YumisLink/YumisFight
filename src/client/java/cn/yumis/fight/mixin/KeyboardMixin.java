package cn.yumis.fight.mixin;
import cn.yumis.fight.bootstrap.ClientBootstrap;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(KeyboardHandler.class)
public abstract class KeyboardMixin {
    @Inject(method="keyPress",at=@At("HEAD"),cancellable=true)
    private void ydnf$key(long handle,int action,KeyEvent event,CallbackInfo ci){
        if(action!=org.lwjgl.glfw.GLFW.GLFW_RELEASE&&cn.yumis.fight.modules.skills.client.Effects.VFX.blocksHotbar()){
            var options=net.minecraft.client.Minecraft.getInstance().options;
            if(options.keySwapOffhand.matches(event)||options.keyTogglePerspective.matches(event)){ci.cancel();return;}
            for(var key:options.keyHotbarSlots)if(key.matches(event)){ci.cancel();return;}
        }
        if(ClientBootstrap.key(event,action))ci.cancel();
    }
}
