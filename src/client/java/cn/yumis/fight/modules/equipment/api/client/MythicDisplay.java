package cn.yumis.fight.modules.equipment.api.client;

import cn.yumis.fight.modules.equipment.api.*;
import cn.yumis.fight.platform.ItemData;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import java.util.List;

/** Local text styling only. Markers are never written into equipment or used to calculate an effect. */
public final class MythicDisplay {
    public static final int GOLD=0xffd778,PINK=0xff79c6;
    public static final Identifier TOOLTIP=Identifier.fromNamespaceAndPath(ItemData.NS,"mythic");
    private MythicDisplay(){}
    public static boolean mythic(ItemStack stack){var view=ItemData.own(stack)?EquipmentItems.view(stack):null;return view!=null&&view.rarity()==Rarity.MYTHIC;}
    public static Component mark(Component component){return component.copy().withStyle(style->marked(style)?style:style.withInsertion(MythicTextMarker.encode(style.getInsertion())));}
    private static String raw(Style style){return (Object)style instanceof MythicStyleAccess access?access.ydnf$rawInsertion():style.getInsertion();}
    private static boolean marked(Style style){return MythicTextMarker.marked(raw(style));}
    private static Style unmark(Style style){return marked(style)?style.withInsertion(MythicTextMarker.original(raw(style))):style;}
    public static boolean marked(FormattedCharSequence text){boolean[] found={false};text.accept((index,style,character)->{if(marked(style))found[0]=true;return true;});return found[0];}
    public static boolean marked(List<ClientTooltipComponent> components){for(var component:components)if(component instanceof MythicTooltipAccess access&&marked(access.ydnf$tooltipText()))return true;return false;}
    public static int color(double fraction){
        double t=Math.clamp(fraction,0,1);int r=(int)Math.round(((GOLD>>16)&255)*(1-t)+((PINK>>16)&255)*t),g=(int)Math.round(((GOLD>>8)&255)*(1-t)+((PINK>>8)&255)*t),b=(int)Math.round((GOLD&255)*(1-t)+(PINK&255)*t);return 0xff000000|r<<16|g<<8|b;
    }
    public static void frame(GuiGraphicsExtractor graphics,int x,int y,int width,int height){
        if(width<2||height<2)return;graphics.fill(x,y,x+width,y+1,color(0));graphics.fill(x,y+height-1,x+width,y+height,color(1));
        for(int row=1;row<height-1;row++){int color=color(row/(double)(height-1));graphics.fill(x,y+row,x+1,y+row+1,color);graphics.fill(x+width-1,y+row,x+width,y+row+1,color);}
    }
    /** Horizontal clips retain the font's normal layout, fallback, weight and wrapping. */
    public static void text(GuiGraphicsExtractor graphics,Font font,FormattedCharSequence text,int x,int y,int baseColor,boolean shadow){
        if(!marked(text)){graphics.text(font,text,x,y,baseColor,shadow);return;}
        if(shadow){FormattedCharSequence shade=sink->text.accept((index,style,character)->{int source=marked(style)?GOLD:style.getColor()==null?baseColor:style.getColor().getValue();return sink.accept(index,unmark(style).withColor((source&0xfcfcfc)>>2),character);});graphics.text(font,shade,x+1,y+1,0xff000000|((baseColor&0xfcfcfc)>>2),false);}
        int width=font.width(text)+8,height=Math.max(1,font.lineHeight);
        for(int row=-3;row<height+4;row++){
            int color=color(row/(double)Math.max(1,height-1));FormattedCharSequence stripe=sink->text.accept((index,style,character)->sink.accept(index,marked(style)?unmark(style).withColor(color&0xffffff):style,character));
            graphics.enableScissor(x-4,y+row,x+width,y+row+1);
            try{graphics.text(font,stripe,x,y,baseColor,false);}finally{graphics.disableScissor();}
        }
    }
}
