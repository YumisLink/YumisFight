package cn.yumis.fight.mixin;
import cn.yumis.fight.bootstrap.ServerApplication;
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
        if(ServerApplication.ready()&&!ServerApplication.get().inventory.inside()){
            var runtime=ServerApplication.get();
            ServerPlayer player=(ServerPlayer)(Object)this;
            var intent=new com.google.gson.JsonObject();
            intent.addProperty("dropSelected",all);
            runtime.inventory.transact(player,"toss/"+java.util.UUID.randomUUID(),intent,()->player.drop(all),java.util.Map.of());
            ci.cancel();
        }
    }
    @Inject(method="drop(Lnet/minecraft/world/item/ItemStack;ZZ)Lnet/minecraft/world/entity/item/ItemEntity;",at=@At("RETURN"))
    private void ydnf$stage(ItemStack stack,boolean around,boolean hand,CallbackInfoReturnable<ItemEntity> cir){
        if(ServerApplication.ready()&&ServerApplication.get().inventory.inside())ServerApplication.get().inventory.stageDrop((ServerPlayer)(Object)this,cir.getReturnValue());
    }
}
