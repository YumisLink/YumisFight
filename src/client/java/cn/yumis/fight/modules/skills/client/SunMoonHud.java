package cn.yumis.fight.modules.skills.client;

import cn.yumis.fight.modules.skills.api.*;
import cn.yumis.fight.modules.presentation.api.client.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/** Compact, duration-free heart resources; paradox uses a moving luminous dashed border. */
final class SunMoonHud {
    private SunMoonHud(){}
    static int render(GuiGraphicsExtractor g,int x,int y,long now){
        var s=CareerClient.INSTANCE.view.sunMoon;
        row(g,s.heart==1?"sun_heart":s.heart==2?"moon_heart":"sun_moon_cycle",s.heart==1?"日曜心":s.heart==2?"月弦心":"日月流转",s.stacks+" / 3",x,y,s.heart==1?0xffffbd67:0xffb5e0ff,false,now);y+=46;
        row(g,"harmony","调和",s.harmony+" / 3",x,y,0xffd6c6ff,false,now);y+=46;
        row(g,"paradox","悖论",s.paradox>0?"就绪":"0 / 1",x,y,0xffffd9e2,s.paradox>0,now);return y+46;
    }
    private static void row(GuiGraphicsExtractor g,String icon,String title,String value,int x,int y,int color,boolean ready,long now){
        UiTheme.hud(g,x-19,y-18,38,38);g.blit(Identifier.fromNamespaceAndPath("yumis_dungeon_and_fight","textures/gui/skills/"+icon+".png"),x-16,y-15,x+16,y+17,0,1,0,1);
        var font=UiFonts.font();g.text(font,title,x+25,y-12,color,true);g.text(font,value,x+25,y+2,UiTheme.HUD_TEXT,true);
        if(ready)dashed(g,x-20,y-19,40,40,now);
    }
    static void dashed(GuiGraphicsExtractor g,int x,int y,int w,int h,long now){
        int length=2*(w+h),offset=(int)Math.floorMod(now/35,length);
        for(int n=0;n<length;n++)if(Math.floorMod(n-offset,12)<7){int px,py;if(n<w){px=x+n;py=y;}else if(n<w+h){px=x+w;py=y+n-w;}else if(n<2*w+h){px=x+2*w+h-n;py=y+h;}else{px=x;py=y+length-n;}g.fill(px-1,py-1,px+2,py+2,0x60ffd7fb);g.fill(px,py,px+1,py+1,0xffffe3ff);}
    }
}
