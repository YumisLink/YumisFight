package cn.yumis.fight.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;

/** Industrial-only beveled panels, recessed slots and teal gauges inspired by Mekanism. */
final class IndustrialUi {
    static final int TEXT=0xff353535,MUTED=0xff555555,ACCENT=0xff168789,SUCCESS=0xff277832,ERROR=0xffa42b2b,GOLD=0xff705a20,OVERLAY=0xb0000000,CARD=0xff858585;
    static void panel(GuiGraphicsExtractor g,int x,int y,int w,int h){
        g.fill(x,y,x+w,y+h,0xff252525);g.fill(x+1,y+1,x+w-1,y+h-1,0xff555555);
        g.fill(x+2,y+2,x+w-2,y+h-2,0xffc6c6c6);g.fill(x+2,y+2,x+w-2,y+3,0xffffffff);g.fill(x+2,y+3,x+3,y+h-2,0xffffffff);
        g.fill(x+3,y+h-3,x+w-2,y+h-2,0xff777777);g.fill(x+w-3,y+3,x+w-2,y+h-3,0xff777777);
    }
    static void card(GuiGraphicsExtractor g,int x,int y,int w,int h){
        g.fill(x,y,x+w,y+h,0xff373737);g.fill(x+1,y+1,x+w,y+h,0xffeeeeee);g.fill(x+1,y+1,x+w-1,y+h-1,0xff858585);
    }
    static void slot(GuiGraphicsExtractor g,int x,int y){card(g,x-1,y-1,18,18);}
    static final class IndustrialButton extends Button {
        IndustrialButton(Builder builder){super(builder);}
        @Override protected void extractContents(GuiGraphicsExtractor g,int mx,int my,float a){
            int x=getX(),y=getY(),w=getWidth(),h=getHeight();panel(g,x,y,w,h);
            if(isHoveredOrFocused()&&active)g.fill(x+3,y+3,x+w-3,y+h-3,0xffb2d1d1);
            var font=UiFonts.font();String text=font.plainSubstrByWidth(getMessage().getString(),w-6);
            g.text(font,text,x+(w-font.width(text))/2,y+(h-font.lineHeight)/2,active?TEXT:0xff8b8b8b,false);
        }
    }
}
