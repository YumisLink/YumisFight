package cn.yumis.fight.modules.skills.client;

import cn.yumis.fight.modules.skills.api.client.SkillsClient;
import cn.yumis.fight.modules.character.api.JobCatalog;
import cn.yumis.fight.modules.presentation.api.client.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Career resources sit above the existing 24-slot bar. */
public final class CareerHud {
    private CareerHud(){}
    public static void render(GuiGraphicsExtractor g,int center,int y){
        var state=SkillsClient.current();var v=CareerClient.INSTANCE.view;long now=state.serverNow();String text="";int color=0xffc38be5;
        if(state.progress().hasJob(JobCatalog.ASURA)){text="刻印 "+v.brands+" / 5";if(v.eyeUntil>now)text+="   波动眼 "+seconds(v.eyeUntil-now)+"秒";color=0xffe6d19b;}
        if(state.progress().hasJob(JobCatalog.SOUL_BENDER)){if(v.field!=null)text="鬼阵 Lv."+v.field.rank+" · "+cn.yumis.fight.modules.skills.api.CareerSkills.ghosts(v.field.rank)+"鬼  "+seconds(v.field.expires-now)+"秒";if(v.kallaUntil>now)text+="  卡洛 "+seconds(v.kallaUntil-now)+"秒";}
        if(state.progress().hasJob(JobCatalog.BERSERKER)){text=v.frenzy?"血之狂暴 · "+(state.predicted(state.own())<=.4?"低血强化20%":state.predicted(state.own())<=.7?"低血强化10%":"双刀开启"):"双刀关闭";if(v.bloodUntil>0)text+="  血剑 "+v.blood+" / 10 · "+seconds(v.bloodUntil-now)+"秒";color=0xffee7d88;}
        if(text.isBlank())return;var font=UiFonts.font();int width=font.width(text)+12;g.fill(center-width/2,y,center+width/2,y+14,UiTheme.HUD_PANEL);g.text(font,text,center-width/2+6,y+3,color,true);
    }
    private static long seconds(long millis){return Math.max(0,(millis+999)/1000);}
}
