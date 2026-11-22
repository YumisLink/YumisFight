package cn.yumis.fight.modules.quests.client;

import cn.yumis.fight.modules.equipment.api.Rarity;
import cn.yumis.fight.modules.equipment.api.client.MythicDisplay;
import cn.yumis.fight.modules.quests.api.NpcTutorial;
import cn.yumis.fight.modules.presentation.api.RichText;
import cn.yumis.fight.modules.presentation.api.client.*;
import java.util.ArrayList;
import java.util.List;

/** The I-menu task bar: special, main and commission tabs on the left. */
public final class QuestsPage {
    enum QuestType {
        SIDE(Rarity.COMMON),
        BEGINNER(Rarity.LEGENDARY),
        MAIN(Rarity.EPIC),
        CAREER(Rarity.MYTHIC);

        private final List<String> titleColors;

        QuestType(Rarity rarity) {
            titleColors = rarity == Rarity.MYTHIC
                    ? List.of("#%06x".formatted(MythicDisplay.GOLD), "#%06x".formatted(MythicDisplay.PINK))
                    : List.of("#%06x".formatted(rarity.color()));
        }
    }

    private record Tab(String id,String title,String detail){}

    private static final List<Tab> TABS=List.of(
            new Tab("special","特殊","转职 · 觉醒"),
            new Tab("main","主线","伊蕾娜指引"),
            new Tab("commission","委托",null));
    private static String selected="main";
    private static java.lang.ref.WeakReference<PlayerPageSurface> open=new java.lang.ref.WeakReference<>(null);
    private QuestsPage(){}
    static void refresh(){var page=open.get();if(page!=null&&page.screen()==net.minecraft.client.Minecraft.getInstance().gui.screen())page.rebuild();}

    static boolean questHeader(PlayerPageSurface page, String id, String title, QuestType type) {
        boolean collapsed = ClientSettings.questCollapsed(id);
        String suffix = collapsed ? "  ▲" : "  ▼";
        var button = page.button(title + suffix, 0, page.cursor(), page.contentWidth() - 24, () -> {
            ClientSettings.toggleQuestCollapsed(id);
            page.rebuild();
        });
        if (button instanceof ThemedButton themed) {
            var label = new RichText(List.of(new RichText.Span(title, type.titleColors)));
            themed.setLabelWithSuffix(label, suffix);
        }
        page.cursor(page.cursor() + button.getHeight() + 4);
        return !collapsed;
    }

    private static void select(String id){
        selected=id;
        var page=open.get();
        if(page!=null)page.rebuild();
    }

    public static void render(PlayerPageSurface page){
        open=new java.lang.ref.WeakReference<>(page);
        renderTabs(page);
        switch(selected){
            case "special" -> {AwakeningQuestClient.render(page);PromotionQuestClient.render(page);}
            case "commission" -> CommissionClient.render(page);
            default -> renderTutorial(page);
        }
    }

    private static void renderTabs(PlayerPageSurface page){
        var rows=new ArrayList<PlayerPageSurface.SidebarRow>();
        for(var tab:TABS){
            String detail=tab.detail()!=null?tab.detail():tab.id().equals("commission")?CommissionClient.headline():"";
            rows.add(new PlayerPageSurface.SidebarRow(tab.title(),detail,selected.equals(tab.id()),()->select(tab.id()),null));
        }
        if(page.hasSidebar())page.sidebarRows("任务类型",List.copyOf(rows));
        else{
            int y=page.cursor();
            int width=Math.max(80,(page.contentWidth()-24)/TABS.size());
            for(int i=0;i<TABS.size();i++){
                var tab=TABS.get(i);
                String detail=tab.detail()!=null?tab.detail():tab.id().equals("commission")?CommissionClient.headline():"";
                var button=page.button((selected.equals(tab.id())?"› ":"")+tab.title()+" · "+detail,i*width,y,width-2,()->select(tab.id()));
                button.active=!selected.equals(tab.id());
            }
            page.cursor(y+24);page.gap();
        }
    }

    private static void renderTutorial(PlayerPageSurface page){
        if (!questHeader(page, "elaina_tutorial", "伊蕾娜的旅途指引", QuestType.BEGINNER)) return;
        var state=TutorialClient.state();var definition=NpcTutorial.definition();
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
