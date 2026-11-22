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
        MAIN(Rarity.EPIC),
        CAREER(Rarity.MYTHIC);

        private final List<String> titleColors;

        QuestType(Rarity rarity) {
            titleColors = rarity == Rarity.MYTHIC
                    ? List.of("#%06x".formatted(MythicDisplay.GOLD), "#%06x".formatted(MythicDisplay.PINK))
                    : List.of("#%06x".formatted(rarity.color()));
        }
    }

    record Tab(String id,String title,String detail,boolean standing){}

    private static final List<Tab> TABS=List.of(
            new Tab("special","特殊","转职 · 觉醒",false),
            new Tab("main","主线","伊蕾娜指引",false),
            new Tab("commission","委托",null,true));
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

    /** Tabs follow the interior headers: mythic for special, epic for main, theme gold for commissions. */
    static List<String> tabColors(String id){
        return switch(id){
            case "special"->List.of("#%06x".formatted(MythicDisplay.GOLD),"#%06x".formatted(MythicDisplay.PINK));
            case "main"->List.of("#%06x".formatted(Rarity.EPIC.color()));
            default->List.of("#%06x".formatted(UiTheme.GOLD&0xffffff));
        };
    }

    static boolean tutorialFinished(){
        var state=TutorialClient.state();
        return state.started&&NpcTutorial.current(state)==null;
    }

    static List<Tab> visibleTabs(){
        var tabs=new ArrayList<Tab>();
        for(var tab:TABS)if(tab.standing()||switch(tab.id()){
            case "special"->AwakeningQuestClient.visible()||PromotionQuestClient.visible();
            case "main"->!tutorialFinished();
            default->true;
        })tabs.add(tab);
        return tabs;
    }

    private static void select(String id){
        selected=id;
        var page=open.get();
        if(page!=null)page.rebuild();
    }

    public static void render(PlayerPageSurface page){
        open=new java.lang.ref.WeakReference<>(page);
        var tabs=visibleTabs();
        String current=tabs.isEmpty()?null:tabs.stream().anyMatch(tab->tab.id().equals(selected))?selected:tabs.get(0).id();
        renderTabs(page,tabs,current);
        if(current==null)return;
        switch(current){
            case "special" -> {AwakeningQuestClient.render(page);PromotionQuestClient.render(page);}
            case "commission" -> CommissionClient.render(page);
            default -> renderTutorial(page);
        }
    }

    private static void renderTabs(PlayerPageSurface page,List<Tab> tabs,String current){
        var rows=new ArrayList<PlayerPageSurface.SidebarRow>();
        for(var tab:tabs){
            String detail=tab.detail()!=null?tab.detail():tab.id().equals("commission")?CommissionClient.headline():"";
            rows.add(new PlayerPageSurface.SidebarRow(tab.title(),tabColors(tab.id()),detail,current.equals(tab.id()),()->select(tab.id()),null));
        }
        if(page.hasSidebar())page.sidebarRows("",List.copyOf(rows),false);
        else{
            int y=page.cursor();
            int width=Math.max(80,(page.contentWidth()-24)/Math.max(1,tabs.size()));
            for(int i=0;i<tabs.size();i++){
                var tab=tabs.get(i);
                String detail=tab.detail()!=null?tab.detail():tab.id().equals("commission")?CommissionClient.headline():"";
                boolean active=current.equals(tab.id());
                var button=page.button((active?"› ":"")+tab.title()+" · "+detail,i*width,y,width-2,()->select(tab.id()));
                button.active=!active;
                if(button instanceof ThemedButton themed)themed.setLabelWithSuffix(new RichText(List.of(new RichText.Span((active?"› ":"")+tab.title(),tabColors(tab.id())),new RichText.Span(" · "+detail,List.of())))," ");
            }
            page.cursor(y+24);page.gap();
        }
    }

    private static void renderTutorial(PlayerPageSurface page){
        if(tutorialFinished())return;
        if (!questHeader(page, "elaina_tutorial", "伊蕾娜的旅途指引", QuestType.MAIN)) return;
        var state=TutorialClient.state();var definition=NpcTutorial.definition();
        if(!state.started){page.line(TutorialClient.starting()?"正在记录旅途指引…":"与伊蕾娜交谈，听完开场后会自动接取第一课。",UiTheme.TEXT);page.line("她在新手村树洞里；未生成新手村时，会出现在世界公共出生点附近。",UiTheme.MUTED);return;}
        var step=NpcTutorial.current(state);
        if(step==null)return;
        boolean complete=TutorialClient.complete(step);page.line(step.title(),UiTheme.GOLD);page.gap();page.line(complete?"✓ 目标已完成，可以提交":"○ 当前目标",complete?UiTheme.ACCENT:UiTheme.MUTED);page.line(step.objective(),UiTheme.TEXT);page.gap();
        page.line("伊蕾娜",UiTheme.GOLD);for(var text:step.dialogue())page.line(text,UiTheme.TEXT);page.gap();
        var reward=step.reward();if(reward.experience()>0)page.line("奖励："+reward.experience()+" 经验",UiTheme.GOLD);if(reward.gold()>0)page.line("奖励："+reward.gold()+" 金币",UiTheme.GOLD);
        for(var item:reward.items())page.line("奖励："+TutorialClient.item(item).getHoverName().getString()+" ×"+item.count(),UiTheme.TEXT);
        page.gap();var submit=page.button(TutorialClient.pending()?"正在保存…":"提交并领取奖励",0,page.cursor(),page.contentWidth()-24,TutorialClient::claim);submit.active=complete&&!TutorialClient.pending()&&TutorialClient.alive();page.cursor(page.cursor()+25);
        if(step.page()!=null&&!step.page().isBlank())page.rowButton("前往"+(step.page().equals("skills")?"职业技能":"角色属性"),()->MenuInput.open(step.page()));
        else page.rowButton(step.goal().equals("craft")?"关闭面板，使用装备制作器":"关闭面板，练习技能",()->page.screen().onClose());
    }
}
