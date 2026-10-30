package cn.yumis.fight.mixin;

import cn.yumis.fight.modules.equipment.api.client.MythicDisplay;
import cn.yumis.fight.modules.equipment.api.client.MythicTooltipAccess;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTextTooltip;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientTextTooltip.class)
public abstract class MythicTooltipTextMixin implements MythicTooltipAccess {
    @Shadow @Final private FormattedCharSequence text;
    @Override public FormattedCharSequence ydnf$tooltipText(){return text;}
    @Inject(method="extractText",at=@At("HEAD"),cancellable=true)
    private void ydnf$gradient(GuiGraphicsExtractor graphics,Font font,int x,int y,CallbackInfo callback){
        if(MythicDisplay.marked(text)){MythicDisplay.text(graphics,font,text,x,y,0xffffffff,true);callback.cancel();}
    }
}
