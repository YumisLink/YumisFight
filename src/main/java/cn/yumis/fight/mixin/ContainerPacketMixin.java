package cn.yumis.fight.mixin;
import cn.yumis.fight.modules.inventory.api.DimensionalMenu;
import cn.yumis.fight.bootstrap.ServerApplication;
import com.google.gson.JsonObject;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.*;
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ContainerPacketMixin {
    @Shadow public ServerPlayer player;
    @Redirect(method="handleSelectTrade",at=@At(value="INVOKE",target="Lnet/minecraft/world/inventory/MerchantMenu;tryMoveItems(I)V"))
    private void ydnf$trade(MerchantMenu menu,int selection){
        if(!ServerApplication.ready()||ServerApplication.get().inventory.inside()){menu.tryMoveItems(selection);return;}
        JsonObject intent=new JsonObject();intent.addProperty("tradeSelection",selection);intent.addProperty("menu",menu.containerId);
        try{ServerApplication.get().inventory.transact(player,"trade-selection/"+UUID.randomUUID(),intent,()->menu.tryMoveItems(selection),Map.of());}
        catch(RuntimeException e){ServerApplication.get().message(player,e.getMessage());menu.broadcastFullState();}
    }
    @Redirect(method="handlePlaceRecipe",at=@At(value="INVOKE",target="Lnet/minecraft/world/inventory/RecipeBookMenu;handlePlacement(ZZLnet/minecraft/world/item/crafting/RecipeHolder;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/player/Inventory;)Lnet/minecraft/world/inventory/RecipeBookMenu$PostPlaceAction;"))
    private RecipeBookMenu.PostPlaceAction ydnf$recipe(RecipeBookMenu menu,boolean maximum,boolean dropping,RecipeHolder<?> recipe,ServerLevel level,Inventory inventory){
        if(!ServerApplication.ready()||ServerApplication.get().inventory.inside())return menu.handlePlacement(maximum,dropping,recipe,level,inventory);
        var result=new RecipeBookMenu.PostPlaceAction[]{RecipeBookMenu.PostPlaceAction.NOTHING};
        JsonObject intent=new JsonObject();intent.addProperty("recipe",recipe.id().identifier().toString());intent.addProperty("maximum",maximum);intent.addProperty("menu",menu.containerId);
        try{ServerApplication.get().inventory.transact(player,"recipe/"+UUID.randomUUID(),intent,()->result[0]=menu.handlePlacement(maximum,dropping,recipe,level,inventory),Map.of());}
        catch(RuntimeException e){ServerApplication.get().message(player,e.getMessage());menu.broadcastFullState();}
        return result[0];
    }
    @Redirect(method="handleContainerButtonClick",at=@At(value="INVOKE",target="Lnet/minecraft/world/inventory/AbstractContainerMenu;clickMenuButton(Lnet/minecraft/world/entity/player/Player;I)Z"))
    private boolean ydnf$button(AbstractContainerMenu menu,Player owner,int button){
        if(!ServerApplication.ready()||ServerApplication.get().inventory.inside()||menu instanceof cn.yumis.fight.modules.inventory.api.DimensionalMenu&&(button>=100&&button<200||button>=1000&&button<=1008))return menu.clickMenuButton(owner,button);
        boolean[] result={false};JsonObject intent=new JsonObject();intent.addProperty("button",button);intent.addProperty("menu",menu.containerId);
        try{ServerApplication.get().inventory.transact(player,"menu-button/"+UUID.randomUUID(),intent,()->result[0]=menu.clickMenuButton(owner,button),Map.of());}
        catch(RuntimeException e){ServerApplication.get().message(player,e.getMessage());menu.broadcastFullState();}
        return result[0];
    }

    @Redirect(method="handleSetCreativeModeSlot",at=@At(value="INVOKE",target="Lnet/minecraft/network/protocol/game/ServerboundSetCreativeModeSlotPacket;itemStack()Lnet/minecraft/world/item/ItemStack;"))
    private net.minecraft.world.item.ItemStack ydnf$creative(net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket packet){return cn.yumis.fight.modules.equipment.api.CreativeEquipment.materialize(packet.itemStack(),new java.util.Random());}
    @Inject(method="handleSetCreativeModeSlot",at=@At(value="INVOKE",target="Lnet/minecraft/world/inventory/InventoryMenu;setRemoteSlot(ILnet/minecraft/world/item/ItemStack;)V",shift=At.Shift.AFTER))
    private void ydnf$creativeSync(ServerboundSetCreativeModeSlotPacket packet,CallbackInfo callback){
        // This point is reached only after vanilla accepts the slot update.
        ItemStack generated=player.inventoryMenu.getSlot(packet.slotNum()).getItem();
        if(ItemStack.matches(generated,packet.itemStack()))return;
        // Materialization changes the template, so return only that slot; the creative cursor is client-owned.
        player.connection.send(new ClientboundContainerSetSlotPacket(player.inventoryMenu.containerId,player.inventoryMenu.incrementStateId(),packet.slotNum(),generated));
    }
}
