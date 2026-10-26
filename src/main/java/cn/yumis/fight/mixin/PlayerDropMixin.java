package cn.yumis.fight.mixin;
import cn.yumis.fight.server.ServerRuntime;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ServerPlayer.class)
public abstract class PlayerDropMixin {
    @Inject(method="drop(Z)V",at=@At("HEAD"),cancellable=true)
    private void ydnf$dropInventory(boolean all,CallbackInfo ci){
        if(ServerRuntime.ready()&&!ServerRuntime.get().inventory.inside()){
            var runtime=ServerRuntime.get();
            ServerPlayer player=(ServerPlayer)(Object)this;
            var intent=new com.google.gson.JsonObject();
            intent.addProperty("dropSelected",all);
            runtime.inventory.transact(player,"toss/"+java.util.UUID.randomUUID(),intent,()->player.drop(all),java.util.Map.of());
            ci.cancel();
        }
    }
    @Inject(method="drop(Lnet/minecraft/world/item/ItemStack;ZZ)Lnet/minecraft/world/entity/item/ItemEntity;",at=@At("HEAD"),cancellable=true)
    private void ydnf$stage(ItemStack stack,boolean around,boolean hand,CallbackInfoReturnable<ItemEntity> cir){
        if(ServerRuntime.ready()&&ServerRuntime.get().inventory.inside())cir.setReturnValue(ServerRuntime.get().inventory.stageDrop((ServerPlayer)(Object)this,stack));
    }
}
