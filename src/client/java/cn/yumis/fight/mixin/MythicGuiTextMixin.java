package cn.yumis.fight.mixin;

import cn.yumis.fight.modules.equipment.api.client.MythicDisplay;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Only explicitly marked local names use the custom drawing path; the Font engine is unchanged. */
@Mixin(GuiGraphicsExtractor.class)
public abstract class MythicGuiTextMixin {
    @Inject(method="text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;IIIZ)V",at=@At("HEAD"),cancellable=true)
    private void ydnf$sequence(Font font,FormattedCharSequence text,int x,int y,int color,boolean shadow,CallbackInfo callback){
        if(text!=null&&MythicDisplay.marked(text)){MythicDisplay.text((GuiGraphicsExtractor)(Object)this,font,text,x,y,color,shadow);callback.cancel();}
    }
    @Inject(method="text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)V",at=@At("HEAD"),cancellable=true)
    private void ydnf$component(Font font,Component component,int x,int y,int color,boolean shadow,CallbackInfo callback){
        if(component==null)return;var text=component.getVisualOrderText();
        if(MythicDisplay.marked(text)){MythicDisplay.text((GuiGraphicsExtractor)(Object)this,font,text,x,y,color,shadow);callback.cancel();}
    }
}
