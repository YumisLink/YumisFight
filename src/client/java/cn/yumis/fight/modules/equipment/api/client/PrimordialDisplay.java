package cn.yumis.fight.modules.equipment.api.client;

import cn.yumis.fight.modules.presentation.api.RichText;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

/** Local text styling only, mirroring the industrial 太初 items' admin gradient. Markers never write back into equipment or calculate effects. */
public final class PrimordialDisplay {
    public static final int GREEN=0x64f59a,BLUE=0xa6e6ff;
    private PrimordialDisplay(){}
    public static Component mark(Component component){return component.copy().withStyle(style->marked(style)?style:style.withInsertion(RichText.GRADIENT+"64F59A/A6E6FF"));}
    private static boolean marked(Style style){String raw=style.getInsertion();return raw!=null&&raw.startsWith(RichText.GRADIENT)&&raw.endsWith("64F59A/A6E6FF");}
    private static int color(double fraction){
        double t=Math.clamp(fraction,0,1);int r=(int)Math.round(((GREEN>>16)&255)*(1-t)+((BLUE>>16)&255)*t),g=(int)Math.round(((GREEN>>8)&255)*(1-t)+((BLUE>>8)&255)*t),b=(int)Math.round((GREEN&255)*(1-t)+(BLUE&255)*t);return 0xff000000|r<<16|g<<8|b;
    }
    public static void frame(GuiGraphicsExtractor graphics,int x,int y,int width,int height){
        if(width<2||height<2)return;graphics.fill(x,y,x+width,y+1,color(0));graphics.fill(x,y+height-1,x+width,y+height,color(1));
        for(int row=1;row<height-1;row++){int c=color(row/(double)(height-1));graphics.fill(x,y+row,x+1,y+row+1,c);graphics.fill(x+width-1,y+row,x+width,y+row+1,c);}
    }
}
