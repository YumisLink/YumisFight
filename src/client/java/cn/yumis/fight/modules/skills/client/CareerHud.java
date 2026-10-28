package cn.yumis.fight.modules.skills.client;

import cn.yumis.fight.modules.skills.api.client.SkillsClient;
import cn.yumis.fight.modules.skills.api.client.SkillController;
import cn.yumis.fight.modules.skills.api.*;
import cn.yumis.fight.modules.character.api.JobCatalog;
import cn.yumis.fight.modules.presentation.api.client.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Status text above the bar; skill resources share a vertical gauge on the right. */
public final class CareerHud {
    private CareerHud(){}
    public static void render(GuiGraphicsExtractor g,int center,int y){
        var state=SkillsClient.current();var v=CareerClient.INSTANCE.view;long now=state.serverNow();String text="";int color=0xffc38be5;
        if(state.progress().hasJob(JobCatalog.ASURA)){if(v.eyeUntil>now)text="波动眼 "+seconds(v.eyeUntil-now)+"秒";color=0xffe6d19b;}
        if(state.progress().hasJob(JobCatalog.SOUL_BENDER)){if(v.field!=null)text="鬼阵 Lv."+v.field.rank+" · "+cn.yumis.fight.modules.skills.api.CareerSkills.ghosts(v.field.rank)+"鬼  "+seconds(v.field.expires-now)+"秒";if(v.kallaUntil>now)text+="  卡洛 "+seconds(v.kallaUntil-now)+"秒";}
        if(state.progress().hasJob(JobCatalog.BERSERKER)){text=v.frenzy?"血之狂暴 · "+(state.predicted(state.own())<=.4?"低血强化20%":state.predicted(state.own())<=.7?"低血强化10%":"双刀开启"):"双刀关闭";color=0xffee7d88;}
        if(text.isBlank())return;var font=UiFonts.font();int width=font.width(text)+12;g.fill(center-width/2,y,center+width/2,y+14,UiTheme.HUD_PANEL);g.text(font,text,center-width/2+6,y+3,color,true);
    }
    public static void renderGauge(GuiGraphicsExtractor g,int width,int height,long now){
        var state=SkillsClient.current();var view=CareerClient.INSTANCE.view;int x=(int)(width*.8),y=Math.max(24,(int)(height*.2));
        if(state.progress().hasJob(JobCatalog.ASURA)&&SkillLearning.effective(state.stats(),state.progress(),SkillCatalog.require(CareerSkills.BRANDS))>0){
            badge(g,SkillCatalog.require(CareerSkills.BRANDS),x,y,view.brands,false);y+=54;
        }
        if(state.progress().hasJob(JobCatalog.BERSERKER)&&view.bloodUntil>0){
            badge(g,SkillCatalog.require(CareerSkills.SWORD),x,y,view.blood,false);
            String time=seconds(view.bloodUntil-state.serverNow())+"秒";var font=UiFonts.font();g.text(font,time,x-font.width(time)/2,y+31,UiTheme.HUD_MUTED,true);y+=66;
        }
        var cast=SkillController.INSTANCE.cast;
        if(cast!=null&&SkillPresentation.segmented(cast.skill)){
            int remaining=(int)cast.parameters.get("slashes",3)-cast.segment;
            if(remaining>0)badge(g,cast.skill,x,y,remaining,(now-cast.stageStart)*cast.speed<SkillPresentation.segmentDelay(cast.skill));
        }
    }
    private static void badge(GuiGraphicsExtractor g,SkillDefinition skill,int x,int y,int count,boolean waiting){
        var font=UiFonts.font();
        g.blit(net.minecraft.resources.Identifier.fromNamespaceAndPath("yumis_dungeon_and_fight","textures/gui/skills/"+skill.icon()+".png"),x-16,y-16,x+16,y+16,0,1,0,1);
        if(waiting)g.fill(x-16,y-16,x+16,y+16,0x70000000);
        int half=Math.max(9,font.width(Integer.toString(count))+2);g.nextStratum();g.fill(x-half,y-11,x+half,y+11,0xB0101010);g.nextStratum();
        g.pose().pushMatrix();g.pose().translate(x,y-9);g.pose().scale(2,2);
        String value=Integer.toString(count);g.text(font,value,-font.width(value)/2,0,UiTheme.HUD_GOLD,true);g.pose().popMatrix();
        String title=skill.title;g.text(font,title,x-font.width(title)/2,y+19,UiTheme.HUD_TEXT,true);
    }
    private static long seconds(long millis){return Math.max(0,(millis+999)/1000);}
}
