package cn.yumis.fight.modules.professions.client;

import cn.yumis.fight.modules.presentation.api.client.UiFonts;
import cn.yumis.fight.modules.presentation.api.client.UiTheme;
import cn.yumis.fight.modules.professions.api.AlchemyPotions;
import cn.yumis.fight.platform.ItemRegistry;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** The active alchemy effect shares the skill gauge's position and scale. */
public final class AlchemyGauge {
    private AlchemyGauge(){}

    public static int render(GuiGraphicsExtractor g,long now,int x,int y){
        var timer=AlchemyClient.activeTimer();if(timer==null)return y;
        long left=timer.get("expires").getAsLong()-now;if(left<=0)return y;
        String potion=timer.get("potion").getAsString();
        var font=UiFonts.font();
        g.pose().pushMatrix();g.pose().translate(x-16,y-16);g.pose().scale(2,2);
        g.item(ItemRegistry.item(potion,1),0,0);g.pose().popMatrix();
        String count=Long.toString((left+999)/1000);int half=Math.max(9,font.width(count)+2);
        g.nextStratum();g.fill(x-half,y-11,x+half,y+11,0xB0101010);g.nextStratum();
        g.pose().pushMatrix();g.pose().translate(x,y-9);g.pose().scale(2,2);
        g.text(font,count,-font.width(count)/2,0,UiTheme.HUD_GOLD,true);g.pose().popMatrix();
        String title=Component.translatable("item.yumis_dungeon_and_fight."+potion).getString();
        g.text(font,title,x-font.width(title)/2,y+19,UiTheme.HUD_TEXT,true);
        UiTheme.hud(g,x-24,y+34,48,4);
        g.fill(x-24,y+34,x-24+(int)(48*Math.clamp(left/(double)AlchemyPotions.BUFF_MILLIS,0,1)),y+38,0xffe6c36d);
        g.text(font,"剩余秒",x-font.width("剩余秒")/2,y+43,UiTheme.HUD_MUTED,true);
        return y+68;
    }
}
