package cn.yumis.fight.mixin;
import cn.yumis.fight.client.ClientBootstrap;
import cn.yumis.fight.game.ExtraInventory;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.inventory.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(CreativeModeInventoryScreen.class)
public abstract class CreativeScreenMixin {
    @Shadow private static CreativeModeTab selectedTab;
    @Inject(method="selectTab",at=@At("RETURN"))
    private void ydnf$position(CreativeModeTab tab,CallbackInfo ci){
        if(tab.getType()!=CreativeModeTab.Type.INVENTORY)return;
        var menu=((CreativeModeInventoryScreen)(Object)this).getMenu();
        for(var slot:menu.slots)if(slot.container instanceof ExtraInventory){
            int i=slot.index-46;
            ((SlotPositionAccessor)slot).ydnf$x(203+(i%2)*23);
            ((SlotPositionAccessor)slot).ydnf$y(19+((i%6)/2)*29);
        }
    }
    @Inject(method="slotClicked",at=@At("HEAD"),cancellable=true)
    private void ydnf$click(Slot slot,int slotId,int button,ContainerInput input,CallbackInfo ci){
        if(selectedTab.getType()==CreativeModeTab.Type.INVENTORY&&slotId<46+cn.yumis.fight.game.EquipmentLayout.CAPACITY){
            ClientBootstrap.inventoryClick(slotId,button,input);
            ci.cancel();
        }
    }
}
