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
        page.line("选择基础职业",UiTheme.GOLD);page.line(player.starterGranted?"新配置不重复赠送武器；基础职业和转职选定后不可更换。":"首次选择赠送对应1级武器；基础职业和转职选定后不可更换。",UiTheme.MUTED);
        int columns=page.contentWidth()>=400?3:page.contentWidth()>=260?2:1,base=page.cursor()+8,w=(page.contentWidth()-24)/columns-8,index=0;
        for(var job:JobCatalog.all()){
            if(!JobCatalog.basic(job.id()))continue;
            String id=job.id();int x=(index%columns)*(w+8),y=base+(index/columns)*122;index++;
            String icon=id.equals(JobCatalog.DEMON_HUNTER)?"textures/gui/skills/metamorphosis.png":"textures/gui/jobs/"+(id.equals(JobCatalog.MAGE)?"mage":"ghost_swordsman")+".png";
            page.card(x,y,w,112);page.icon(icon,x+(w-40)/2,y+8,40);page.text(job.title(),x+8,y+56,UiTheme.GOLD);
            page.button("选择",x+8,y+78,w-16,()->confirm(page,"选择"+job.title()+"？","这套配置的基础职业选定后不可更换。",()->choose("chooseJob",id)));
        }
        page.cursor(base+((index+columns-1)/columns)*122);return true;
    }
    public static void footer(PlayerPageSurface page,Progress player){
        page.gap();page.line("当前职业："+JobCatalog.require(player.jobId()).title(),UiTheme.GOLD);
        if(player.awakened())page.line("觉醒资格：全角色已解锁；对应觉醒技能仍需50级自行学习。",UiTheme.ACCENT);
        else if(!JobCatalog.basic(player.jobId()))page.line("50级可使用一次性觉醒券，获得资格后自行学习觉醒技能。",UiTheme.MUTED);
        if(JobCatalog.basic(player.jobId())){
            boolean marked=player.jobChangeUnlocked();page.line(marked?"通用转职资格：已解锁":"通用转职资格：未解锁",UiTheme.MUTED);
            if(marked&&player.level>=20){for(var job:JobCatalog.all())if(job.prerequisite().equals(player.baseJob())){boolean occupied=CareerProfilesClient.occupied(job.id());var button=page.button((occupied?"已有配置：":"转职为")+job.title(),0,page.cursor(),page.contentWidth()-24,()->confirm(page,"转职为"+job.title()+"？","转职选定后不可更换；需副本外脱战且所有技能冷却结束。",()->choose("promote",job.id())));button.active=!occupied;page.cursor(page.cursor()+24);}}
            else if(player.level<20)page.line("20级解锁转职。",UiTheme.MUTED);
        }
    }
    public static void register(cn.yumis.fight.architecture.CommandRouter<Void,com.google.gson.JsonObject> router){
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.player.ItemTooltipEvent event)->{var stack=event.getItemStack();if(!cn.yumis.fight.platform.ItemData.own(stack))return;String id=cn.yumis.fight.platform.ItemData.id(stack);if(id.equals(CharacterItems.PROFILE_EXPANSION))event.getToolTip().add(Component.literal("职业配置容量永久增加1；解锁后新增栏位可直接使用。需副本、训练外脱战，无施法且全部冷却结束。"));if(id.equals(CharacterItems.LEVEL_ONE)||id.equals(CharacterItems.LEVEL_TEN)||id.equals(CharacterItems.LEVEL_MAX))event.getToolTip().add(Component.literal((id.equals(CharacterItems.LEVEL_MAX)?"直升当前满级（"+CharacterRules.MAX_LEVEL+"级），等级上限开放后自动跟随。":id.equals(CharacterItems.LEVEL_TEN)?"当前配置提升10级，最多升至满级，消耗整张券。":"当前配置提升1级，最多升至满级。")+"经验清零，正常补发属性点和SP；满级不消耗。需副本、训练外脱战，无施法且全部冷却结束。"));if(id.equals(CharacterItems.CAREER_UNLOCK))event.getToolTip().add(Component.literal("20级可用，无职业前置；永久解锁所有职业的转职资格，已有资格不消耗。"));if(id.equals(CharacterItems.AWAKENING))event.getToolTip().add(Component.literal("50级可用，无职业前置；全角色配置共享觉醒资格；每套仍需50级自行学习觉醒技能。"));});
        router.register("character","characterTicketConfirm",(ignored,j)->{
            var mc=Minecraft.getInstance();var parent=mc.gui.screen();String kind=j.get("kind").getAsString();
            mc.gui.setScreen(new ThemedConfirmScreen(yes->{mc.gui.setScreen(parent);if(yes){var ui=ClientUi.current();ui.upload();var request=ui.operation(kind);request.add("token",j.get("token"));request.add("profileId",j.get("profileId"));request.add("profileRevision",j.get("profileRevision"));ui.send("progress",request);}},Component.literal(kind.equals("expandProfile")?"拓展职业配置容量？":"使用升级券？"),Component.literal(j.get("text").getAsString()),ThemeDomain.MENU));
        });
    }
}
