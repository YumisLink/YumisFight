package cn.yumis.fight.mixin;
import cn.yumis.fight.server.ServerRuntime;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ServerPlayer.class)
public abstract class ContainerCloseMixin {
    @Unique private AbstractContainerMenu ydnf$closed;
    @Inject(method="doCloseContainer",at=@At("HEAD"))
    private void ydnf$beforeClose(CallbackInfo ci){ydnf$closed=((ServerPlayer)(Object)this).containerMenu;}
    @Inject(method="doCloseContainer",at=@At("RETURN"))
    private void ydnf$afterClose(CallbackInfo ci){
        if(ServerRuntime.ready()&&ydnf$closed!=null)ServerRuntime.get().inventory.finishMenu((ServerPlayer)(Object)this,ydnf$closed);
        ydnf$closed=null;
    }
}
