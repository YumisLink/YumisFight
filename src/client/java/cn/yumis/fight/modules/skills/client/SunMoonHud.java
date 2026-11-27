package cn.yumis.fight.modules.skills.client;

import cn.yumis.fight.modules.dungeons.api.TowerBattleCard;
import cn.yumis.fight.modules.skills.api.*;
import cn.yumis.fight.modules.presentation.api.client.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import java.util.Locale;

/** Compact, duration-free heart resources; paradox uses a moving luminous dashed border. */
final class SunMoonHud {
    private SunMoonHud(){}
    static int render(GuiGraphicsExtractor g,int x,int y,long now){
        var s=CareerClient.INSTANCE.view.sunMoon;
        var state=cn.yumis.fight.modules.skills.api.client.SkillsClient.current();
        var stats=state.stats();
        int cap=SunMoonRules.stackCap(stats);
        row(g,s.heart==1?"sun_heart":s.heart==2?"moon_heart":"sun_moon_cycle",s.heart==1?"日曜心":s.heart==2?"月弦心":"日月流转",s.stacks+" / "+cap,x,y,s.heart==1?0xffffbd67:0xffb5e0ff,false,now);y+=46;
        int harmony=SunMoonRules.refluxHarmony(SkillLearning.effective(stats,state.progress(),SkillCatalog.require(SunMoonSkills.REFLUX)));
        if(TowerBattleCard.SUNMOON_REFLUX_LEYLINES.held(stats))harmony+=TowerBattleCard.SUNMOON_REFLUX_LEYLINES.count("harmony");
        row(g,"harmony","调和",s.harmony+" / "+Math.max(s.harmony,harmony),x,y,0xffd6c6ff,false,now);y+=46;
        int pCap=SunMoonRules.paradoxCap(stats);
        String paradoxText=pCap>1?(s.paradox+" / "+pCap):(s.paradox>0?"就绪":"0 / 1");
        row(g,"paradox","悖论",paradoxText,x,y,0xffffd9e2,s.paradox>0,now);y+=46;
        if(TowerBattleCard.SUNMOON_SUNFIRE_SPARK.held(stats)&&s.sunfireSparks>0){
            row(g,"sunfire","炽阳火苗",s.sunfireSparks+" / "+TowerBattleCard.SUNMOON_SUNFIRE_SPARK.count("cap"),x,y,0xffffb45c,true,now);y+=46;
        }
        if(TowerBattleCard.SUNMOON_REFLUX_LEYLINES.held(stats)&&s.leylineUntil>now){
            String sec=String.format(Locale.ROOT,"%.1f秒",(s.leylineUntil-now)/1000.0);
            row(g,"moon_reflux","星轨魔纹",sec,x,y,0xff9ee8ff,true,now);y+=46;
        }
        if(TowerBattleCard.SUNMOON_ECLIPSE.held(stats)&&s.eclipseUntil>now){
            String sec=String.format(Locale.ROOT,"%.1f秒",(s.eclipseUntil-now)/1000.0);
            row(g,"sun_moon_cycle","日蚀共鸣",sec,x,y,0xfff3c8ff,false,now);y+=46;
        }
        return y;
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
