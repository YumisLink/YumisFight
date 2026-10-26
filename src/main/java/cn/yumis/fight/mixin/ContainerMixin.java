package cn.yumis.fight.mixin;
import cn.yumis.fight.game.MenuHooks;
import net.minecraft.world.inventory.*;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(AbstractContainerMenu.class)
public abstract class ContainerMixin {
    @Inject(method="removed",at=@At("HEAD")) private void ydnf$close(Player player,CallbackInfo ci){
        if(player instanceof net.minecraft.server.level.ServerPlayer p&&cn.yumis.fight.server.ServerRuntime.ready()){
            var r=cn.yumis.fight.server.ServerRuntime.get();
            if(!r.inventory.inside())r.inventory.closeCursor(p);
        }
    }
    @Inject(method="clicked",at=@At("HEAD"),cancellable=true) private void ydnf$transaction(int index,int button,ContainerInput input,Player player,CallbackInfo ci){
        if(MenuHooks.click((AbstractContainerMenu)(Object)this,index,button,input,player))ci.cancel();
    }
}
