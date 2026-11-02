package cn.yumis.fight.modules.skills.client;

import cn.yumis.fight.modules.skills.api.client.SkillsClient;
import cn.yumis.fight.modules.skills.api.client.SkillController;
import cn.yumis.fight.modules.skills.api.*;
import java.util.List;
import cn.yumis.fight.modules.character.api.JobCatalog;
import cn.yumis.fight.modules.presentation.api.client.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Status text above the bar; skill resources share a vertical gauge on the right. */
public final class CareerHud {
    private CareerHud(){}
    public static void render(GuiGraphicsExtractor g,int center,int y){
        var state=SkillsClient.current();var v=CareerClient.INSTANCE.view;long now=state.serverNow();String text="";int color=0xffc38be5;
        if(state.progress().hasJob(JobCatalog.SOUL_BENDER)&&v.field!=null)text="鬼阵 Lv."+v.field.rank+" · "+cn.yumis.fight.modules.skills.api.CareerSkills.ghosts(v.field.rank)+"鬼  "+seconds(v.field.expires-now)+"秒";
        if(state.progress().hasJob(JobCatalog.BERSERKER)){text=v.frenzy?"血之狂暴 · "+(state.predicted(state.own())<=.4?"低血强化20%":state.predicted(state.own())<=.7?"低血强化10%":"双刀开启"):"双刀关闭";color=0xffee7d88;}
        if(text.isBlank())return;var font=UiFonts.font();int width=font.width(text)+12;UiTheme.hud(g,center-width/2,y,(center+width/2)-(center-width/2),(y+14)-(y));g.text(font,text,center-width/2+6,y+3,color,true);
    }
    public static void renderGauge(GuiGraphicsExtractor g,int width,int height,long now){
        var state=SkillsClient.current();var view=CareerClient.INSTANCE.view;int x=(int)(width*.8),y=Math.max(24,(int)(height*.2));
        if(state.own().dead)return;
        if(state.progress().hasJob(JobCatalog.SUMMONER)){summons(g,width,height,state.serverNow());return;}
        if(OracleRules.rank(state.stats(),state.progress())>0)y=oracle(g,x,y,state.serverNow());
        long overdrive=state.resources().buffs.getOrDefault(SkillCatalog.OVERDRIVE.id,0L)-now;
        if(overdrive>0){timer(g,SkillCatalog.OVERDRIVE,x,y,overdrive,30000,0xfff6d16d);y+=68;}
        long kalla=view.kallaUntil-state.serverNow();if(kalla>0){timer(g,SkillCatalog.require(CareerSkills.KALLA),x,y,kalla,20000,0xffb28fe5);y+=68;}
        long eye=view.eyeUntil-state.serverNow();if(eye>0){timer(g,SkillCatalog.require(CareerSkills.EYE),x,y,eye,20000,0xffe6d19b);y+=68;}
        long herder=MageClient.INSTANCE.summonUntil-state.serverNow();if(herder>0){timer(g,MageSkills.skill(MageSkills.HERDER),x,y,herder,120000,0xff92d49b);y+=68;}
        if(state.progress().hasJob(JobCatalog.ASURA)&&SkillLearning.effective(state.stats(),state.progress(),SkillCatalog.require(CareerSkills.BRANDS))>0){
            badge(g,SkillCatalog.require(CareerSkills.BRANDS),x,y,view.brands,false);y+=54;
        }
        if(state.progress().hasJob(JobCatalog.BERSERKER)&&view.bloodUntil>0){
            badge(g,SkillCatalog.require(CareerSkills.SWORD),x,y,view.blood,false);
            String time="/ "+SkillResourceRules.BLOOD_CAP+"  "+seconds(view.bloodUntil-state.serverNow())+"秒";var font=UiFonts.font();g.text(font,time,x-font.width(time)/2,y+31,UiTheme.HUD_MUTED,true);y+=66;
        }
        var cast=SkillController.INSTANCE.cast;
        if(cast!=null&&SkillPresentation.segmented(cast.skill)){
            int remaining=(int)cast.parameters.get("slashes",3)-cast.segment;
            if(remaining>0)badge(g,cast.skill,x,y,remaining,(now-cast.stageStart)*cast.speed<SkillPresentation.segmentDelay(cast.skill));
        }
    }
    private static void summons(GuiGraphicsExtractor g,int width,int height,long now){
        var army=MageClient.INSTANCE;var font=UiFonts.font();var rows=new java.util.ArrayList<String>();
        for(String id:List.of(MageSkills.HERDER,SummonerSkills.SPIRITS,SummonerSkills.LOUISE,SummonerSkills.SANDOR,SummonerSkills.KING,SummonerSkills.KASIJAS))
            if(army.army.values().stream().anyMatch(j->j.get("skill").getAsString().equals(id)&&j.get("until").getAsLong()>now))rows.add(id);
        if(army.frenzyUntil>now)rows.add(SummonerSkills.FRENZY);if(rows.isEmpty())return;
        int panel=rows.stream().map(SkillCatalog::require).mapToInt(s->font.width(s.title)+36).max().orElse(144);
        float scale=Math.min(1,Math.min((height-96f)/(rows.size()*32),(width-16f)/panel));
        g.pose().pushMatrix();g.pose().translate(width-8,24);g.pose().scale(scale,scale);g.pose().translate(-panel,0);
        for(int i=0;i<rows.size();i++){
            String id=rows.get(i);var skill=SkillCatalog.require(id);int y=i*32;
            var entries=army.army.values().stream().filter(j->j.get("skill").getAsString().equals(id)&&j.get("until").getAsLong()>now).toList();
            long left=id.equals(SummonerSkills.FRENZY)?army.frenzyUntil-now:entries.stream().mapToLong(j->j.get("until").getAsLong()).min().orElse(now)-now;
            long duration=id.equals(SummonerSkills.KASIJAS)||id.equals(SummonerSkills.FRENZY)?20000:120000;
            UiTheme.hud(g,0,y,(panel)-(0),(y+30)-(y));g.blit(net.minecraft.resources.Identifier.fromNamespaceAndPath("yumis_dungeon_and_fight","textures/gui/skills/"+skill.icon()+".png"),2,y+3,26,y+27,0,1,0,1);
            g.text(font,skill.title,30,y+3,UiTheme.HUD_TEXT,true);g.text(font,"剩余 "+seconds(left)+" 秒"+(id.equals(SummonerSkills.SPIRITS)?" · "+entries.size()+" / 4":""),30,y+15,UiTheme.HUD_MUTED,true);
            g.fill(30,y+27,30+(int)((panel-32)*Math.clamp(left/(double)duration,0,1)),y+29,id.equals(SummonerSkills.FRENZY)?0xffee7d88:0xff92d49b);
        }
        g.pose().popMatrix();
    }
    private static int oracle(GuiGraphicsExtractor g,int x,int y,long now){
        var state=SkillsClient.current();var o=CareerClient.INSTANCE.view.oracle;var font=UiFonts.font();
        String instruction=o.instruction.isEmpty()?"指令之意 · 待命":SkillCatalog.require(o.instruction).title;
        long left=o.instruction.isEmpty()?o.nextInstruction-now:o.hermes==9?state.resources().cooldowns.getOrDefault(OracleSkills.REPLICA,0L)-cn.yumis.fight.modules.presentation.api.client.ClientUi.now():o.instructionUntil-now;
        oracleBadge(g,o.instruction.isEmpty()?"oracle_will":SkillCatalog.require(o.instruction).icon(),instruction,o.instructionPausedLeft>0?"暂停 "+seconds(o.instructionPausedLeft)+"秒":left>0?seconds(left)+"秒":"就绪",x,y,!o.instruction.isEmpty());y+=62;
        String weapon=java.util.Map.of("BLUNT","钝器","KATANA","太刀","GREATSWORD","巨剑","SHORTSWORD","短剑","LIGHTSABER","光剑").getOrDefault(o.weapon,"待指定");
        int action=OracleSkills.WEAPONS.indexOf(o.weapon);String icon=action<0?"oracle_will":SkillCatalog.require(OracleSkills.ACTIONS.get(action)).icon();
        oracleBadge(g,icon,"武器指令 · "+weapon,seconds(o.weaponUntil-now)+"秒",x,y,true);y+=62;
        oracleBadge(g,"oracle_hermes","代行-赫尔墨斯",o.hermes+" / "+OracleRules.cap(state.progress())+(o.hermes==9?" 保持":"  "+seconds(o.hermesUntil-now)+"秒"),x,y,o.hermes==9);y+=62;
        if(o.karma>0){oracleBadge(g,"oracle_karma","业",o.karma+" / 5  "+seconds(o.karmaUntil-now)+"秒",x,y,false);y+=62;}return y;
    }
    private static void oracleBadge(GuiGraphicsExtractor g,String icon,String title,String value,int x,int y,boolean glow){
        var font=UiFonts.font();if(glow){g.fill(x-19,y-19,x+19,y+19,0xffd8b459);UiTheme.hud(g,x-17,y-17,(x+17)-(x-17),(y+17)-(y-17));}
        g.blit(net.minecraft.resources.Identifier.fromNamespaceAndPath("yumis_dungeon_and_fight","textures/gui/skills/"+icon+".png"),x-16,y-16,x+16,y+16,0,1,0,1);
        g.text(font,title,x-font.width(title)/2,y+20,glow?UiTheme.HUD_GOLD:UiTheme.HUD_TEXT,true);g.text(font,value,x-font.width(value)/2,y+33,UiTheme.HUD_MUTED,true);
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
    private static void timer(GuiGraphicsExtractor g,SkillDefinition skill,int x,int y,long left,long duration,int color){
        badge(g,skill,x,y,(int)seconds(left),false);UiTheme.hud(g,x-24,y+34,(x+24)-(x-24),(y+38)-(y+34));g.fill(x-24,y+34,x-24+(int)(48*Math.clamp(left/(double)duration,0,1)),y+38,color);var font=UiFonts.font();g.text(font,"剩余秒",x-font.width("剩余秒")/2,y+43,UiTheme.HUD_MUTED,true);
    }
    private static long seconds(long millis){return Math.max(0,(millis+999)/1000);}
}
