package cn.yumis.fight.modules.skills.client;

import cn.yumis.fight.modules.presentation.api.client.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** The same bar renders forward chanting and reverse channel progress; content supplies the fraction. */
final class SkillCastBar {
    private SkillCastBar(){}
    static void render(GuiGraphicsExtractor g,int width,int height,String label,double progress,int color){
        var player=Minecraft.getInstance().player;
        int extra=player==null?0:(player.isUnderWater()||player.getAirSupply()<player.getMaxAirSupply()?10:0)+(player.isPassenger()?10:0);
        int w=Math.min(170,width-16),x=(width-w)/2,y=Math.max(6,height-162-extra);
        UiTheme.hud(g,x,y,w,23);g.fill(x+4,y+17,x+w-4,y+20,0x80485870);g.fill(x+4,y+17,x+4+(int)((w-8)*Math.clamp(progress,0,1)),y+20,color);
        g.text(UiFonts.font(),label,x+(w-UiFonts.font().width(label))/2,y+4,UiTheme.HUD_TEXT,true);
    }
}
