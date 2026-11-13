package cn.yumis.fight.modules.npc.client;

import cn.yumis.fight.modules.npc.api.NpcTutorial;
import cn.yumis.fight.modules.presentation.api.client.*;

/** A player-wide guide tracker inside the existing I menu. */
public final class TutorialPage {
    private static java.lang.ref.WeakReference<PlayerPageSurface> open=new java.lang.ref.WeakReference<>(null);
    private TutorialPage(){}
    static void refresh(){var page=open.get();if(page!=null&&page.screen()==net.minecraft.client.Minecraft.getInstance().gui.screen())page.rebuild();}
    public static void render(PlayerPageSurface page){
        open=new java.lang.ref.WeakReference<>(page);
        var state=TutorialClient.state();var definition=NpcTutorial.definition();
        page.line("伊蕾娜的旅途指引",UiTheme.GOLD);
        if(!state.started){page.line(TutorialClient.starting()?"正在记录旅途指引…":"与伊蕾娜交谈，听完开场后会自动接取第一课。",UiTheme.TEXT);page.line("她在新手村树洞里；未生成新手村时，会出现在世界公共出生点附近。",UiTheme.MUTED);return;}
        var step=NpcTutorial.current(state);
        page.line("已提交 "+state.claimed.size()+" / "+definition.steps().size()+" · 所有职业配置共用进度",UiTheme.MUTED);page.gap();
        if(step==null){for(var line:definition.completion())page.line(line,UiTheme.TEXT);page.gap();page.rowButton("前往副本",()->MenuInput.open("dungeons"));page.rowButton("查看职业技能",()->MenuInput.open("skills"));}
        else{
            boolean complete=TutorialClient.complete(step);page.line(step.title(),UiTheme.GOLD);page.gap();page.line(complete?"✓ 目标已完成，可以提交":"○ 当前目标",complete?UiTheme.ACCENT:UiTheme.MUTED);page.line(step.objective(),UiTheme.TEXT);page.gap();
            page.line("伊蕾娜",UiTheme.GOLD);for(var text:step.dialogue())page.line(text,UiTheme.TEXT);page.gap();
            var reward=step.reward();if(reward.experience()>0)page.line("奖励："+reward.experience()+" 经验",UiTheme.GOLD);if(reward.gold()>0)page.line("奖励："+reward.gold()+" 金币",UiTheme.GOLD);
            for(var item:reward.items())page.line("奖励："+TutorialClient.item(item).getHoverName().getString()+" ×"+item.count(),UiTheme.TEXT);
            page.gap();var submit=page.button(TutorialClient.pending()?"正在保存…":"提交并领取奖励",0,page.cursor(),page.contentWidth()-24,TutorialClient::claim);submit.active=complete&&!TutorialClient.pending()&&TutorialClient.alive();page.cursor(page.cursor()+25);
            if(step.page()!=null&&!step.page().isBlank())page.rowButton("前往"+(step.page().equals("skills")?"职业技能":"角色属性"),()->MenuInput.open(step.page()));
            else page.rowButton(step.goal().equals("craft")?"关闭面板，使用装备制作器":"关闭面板，练习技能",()->page.screen().onClose());
            page.line("提交后自动接下一课，不必返回伊蕾娜身边。",UiTheme.MUTED);
        }
        page.gap();page.line("旅途记录",UiTheme.GOLD);
        for(var lesson:definition.steps())page.line((state.claimed.contains(lesson.id())?"✓ ":step!=null&&step.id().equals(lesson.id())?"› ":"· ")+lesson.title().plain()+(state.claimed.contains(lesson.id())?" · 已领取":""),state.claimed.contains(lesson.id())?UiTheme.MUTED:UiTheme.TEXT);
    }
}
