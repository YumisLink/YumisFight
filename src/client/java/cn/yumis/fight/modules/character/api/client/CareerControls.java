package cn.yumis.fight.modules.character.api.client;
import cn.yumis.fight.modules.character.api.*;
import cn.yumis.fight.modules.presentation.api.client.*;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
/** Career choices stay inside the skill page; item confirmations use the same command port. */
public final class CareerControls {
    private CareerControls(){}
    private static void confirm(PlayerPageSurface page,String title,String text,Runnable accepted){var parent=page.screen();Minecraft.getInstance().gui.setScreen(new ThemedConfirmScreen(yes->{Minecraft.getInstance().gui.setScreen(parent);if(yes)accepted.run();},Component.literal(title),Component.literal(text),ThemeDomain.MENU));}
    private static void choose(String kind,String id){var ui=ClientUi.current();ui.upload();var request=ui.operation(kind);request.addProperty("job",id);ui.send("progress",request);}
    public static boolean selection(PlayerPageSurface page,Progress player){
        if(player.selected())return false;
        page.line("选择基础职业",UiTheme.GOLD);page.line("选定后需使用职业重置券更换，首次选择赠送对应1级武器。",UiTheme.MUTED);
        int y=page.cursor()+8,w=(page.contentWidth()-18)/2,index=0;
        for(String id:java.util.List.of(JobCatalog.BASE,JobCatalog.MAGE)){int x=index++*(w+8);var job=JobCatalog.require(id);page.card(x,y,w,112);page.icon("textures/gui/jobs/"+(id.equals(JobCatalog.MAGE)?"mage":"ghost_swordsman")+".png",x+(w-40)/2,y+8,40);page.text(job.title(),x+8,y+56,UiTheme.GOLD);page.button("选择",x+8,y+78,w-16,()->confirm(page,"选择"+job.title()+"？","基础职业仅可通过职业重置券重选。",()->choose("chooseJob",id)));}
        page.cursor(y+122);return true;
    }
    public static void footer(PlayerPageSurface page,Progress player){
        page.gap();page.line("当前职业："+JobCatalog.require(player.jobId()).title(),UiTheme.GOLD);
        if(player.awakened())page.line("觉醒："+JobCatalog.style(player.jobId()).awakening(),UiTheme.ACCENT);
        else if(!JobCatalog.basic(player.jobId()))page.line("50级后需管理员指令觉醒，获得资格后自行学习觉醒技能。",UiTheme.MUTED);
        if(player.hasJob(JobCatalog.BASE)){
            boolean marked=player.completedJobQuests.contains(JobCatalog.BASE);page.line(marked?"转职任务：已完成":"转职任务：未完成（当前由管理员设置标记）",UiTheme.MUTED);
            if(marked&&player.level>=20){for(var job:JobCatalog.all())if(job.prerequisite().equals(JobCatalog.BASE))page.rowButton("转职为"+job.title(),()->confirm(page,"转职为"+job.title()+"？","需副本外脱战，且所有技能冷却结束。",()->choose("promote",job.id())));}
            else if(player.level<20)page.line("20级解锁转职。",UiTheme.MUTED);
        }else if(player.hasJob(JobCatalog.MAGE))page.line("魔法师转职尚未开放。",UiTheme.MUTED);
    }
    public static void register(cn.yumis.fight.architecture.CommandRouter<Void,com.google.gson.JsonObject> router){
        router.register("character","careerResetConfirm",(ignored,j)->{boolean base=j.get("base").getAsBoolean();var mc=Minecraft.getInstance();var parent=mc.gui.screen();mc.gui.setScreen(new ThemedConfirmScreen(yes->{mc.gui.setScreen(parent);if(yes){var ui=ClientUi.current();ui.upload();var request=ui.operation(base?"resetProfession":"resetCareer");request.addProperty("token",j.get("token").getAsString());ui.send("progress",request);}},Component.literal(base?"使用职业重置券？":"使用转职重置券？"),Component.literal(base?"退回未选基础职业，返还全部技能SP；保留任务标记，清除觉醒。":"退回基础职业，返还转职技能SP；保留基础技能和任务标记，清除觉醒。"),ThemeDomain.MENU));});
    }
}
