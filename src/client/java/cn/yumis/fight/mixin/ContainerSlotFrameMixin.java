package cn.yumis.fight.mixin;

import cn.yumis.fight.modules.economy.api.ItemRarity;
import cn.yumis.fight.modules.economy.api.client.RarityFrames;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerScreen.class)
public abstract class ContainerSlotFrameMixin {
    @Inject(method="renderSlotContents",at=@At(value="INVOKE",target="Lnet/minecraft/client/gui/GuiGraphicsExtractor;itemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V"))
    private void ydnf$frameBeforeDecorations(GuiGraphicsExtractor graphics,ItemStack stack,Slot slot,String count,CallbackInfo ci){
        if(ItemRarity.decorated(stack))RarityFrames.draw(graphics,stack,slot.x,slot.y,false);
    }
}
