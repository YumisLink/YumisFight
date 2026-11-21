package cn.yumis.fight.compat.modernui;

import cn.yumis.fight.modules.equipment.api.Rarity;
import cn.yumis.fight.modules.equipment.api.client.MythicDisplay;
import cn.yumis.fight.modules.equipment.api.client.PrimordialDisplay;
import cn.yumis.fight.modules.economy.api.ItemRarity;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.*;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

/** The installed 3.13.8.2 and port 3.13.8.3 share these final draw/color entry points. */
@Pseudo
@Mixin(targets="icyllis.modernui.mc.TooltipRenderer",remap=false)
public abstract class MythicBorderMixin {
    @Shadow private boolean mUseSpectrum;
    @Shadow @Final public static int[] sStrokeColor;
    @Unique private boolean ydnf$mythic;
    @Unique private boolean ydnf$primordial;
    @Inject(method="drawTooltip",at=@At("HEAD"))
    private void ydnf$tooltip(ItemStack stack,GuiGraphicsExtractor graphics,List<ClientTooltipComponent> components,int mouseX,int mouseY,Font font,int width,int height,float partialX,float partialY,ClientTooltipPositioner positioner,Identifier style,CallbackInfo callback){
        ydnf$mythic=MythicDisplay.mythic(stack)||MythicDisplay.marked(components);
        ydnf$primordial=!ydnf$mythic&&ItemRarity.rarity(stack)==Rarity.PRIMORDIAL;
    }
    @Inject(method="drawRoundedBackground",at=@At("HEAD"))
    private void ydnf$fixedGradient(CallbackInfo callback){if(ydnf$mythic||ydnf$primordial)mUseSpectrum=false;}
    @Inject(method="chooseBorderColor(I)I",at=@At("HEAD"),cancellable=true)
    private void ydnf$border(int corner,CallbackInfoReturnable<Integer> result){
        if(ydnf$mythic)result.setReturnValue((sStrokeColor[corner]&0xff000000)|(corner<2?MythicDisplay.GOLD:MythicDisplay.PINK));
        else if(ydnf$primordial)result.setReturnValue((sStrokeColor[corner]&0xff000000)|(corner<2?PrimordialDisplay.GREEN:PrimordialDisplay.BLUE));
    }
}
