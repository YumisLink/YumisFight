package cn.yumis.fight.mixin;
import net.minecraft.world.inventory.Slot;
import cn.yumis.fight.bootstrap.ClientBootstrap;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.inventory.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(CreativeModeInventoryScreen.class)
public abstract class CreativeScreenMixin {
    @Shadow private static CreativeModeTab selectedTab;
    @Inject(method="slotClicked",at=@At("HEAD"),cancellable=true)
    private void ydnf$click(Slot slot,int slotId,int button,ContainerInput input,CallbackInfo ci){
        if(slot!=null&&selectedTab.getType()==CreativeModeTab.Type.INVENTORY&&!(slot instanceof CreativeSlotAccessor))return;
        if(slot instanceof CreativeSlotAccessor wrapped)slotId=wrapped.ydnf$target().index;
        if(selectedTab.getType()==CreativeModeTab.Type.INVENTORY&&slotId<46){
            ClientBootstrap.inventoryClick(slotId,button,input);
            ci.cancel();
        }
    }
}
