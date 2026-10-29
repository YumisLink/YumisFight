package cn.yumis.fight.modules.presentation.client;
import cn.yumis.fight.modules.presentation.api.client.*;
import java.util.List;
public final class SettingsPage {
    private SettingsPage(){}
    public static void render(PlayerPageSurface page,java.util.function.Consumer<String> message){
        String section=page.remember("settings.section","general");
        page.navigation(List.of(new PlayerPageSurface.Navigation("general","通用界面"),new PlayerPageSurface.Navigation("combat","战斗显示"),new PlayerPageSurface.Navigation("machines","机器与背包"),new PlayerPageSurface.Navigation("keys","字体与按键")),section,id->{page.rememberValue("settings.section",id);page.rebuild();});
        switch(section){
            case "general"->{
                page.line("通用界面",UiTheme.GOLD);page.gap();
                page.rowButton("统一主题："+ClientSettings.themeName(ClientSettings.globalTheme),()->{ClientSettings.toggleGlobalTheme();page.rebuild();});
                page.rowButton("I面板跟随统一主题："+(ClientSettings.menuFollowsTheme?"开":"关"),()->{ClientSettings.follow(ThemeDomain.MENU,!ClientSettings.menuFollowsTheme);page.rebuild();});
                if(!ClientSettings.menuFollowsTheme)page.rowButton("I面板主题："+ClientSettings.themeName(ClientSettings.menuTheme),()->{ClientSettings.toggleDomain(ThemeDomain.MENU);page.rebuild();});
                page.rowButton("面板大小："+ClientSettings.panel,()->{ClientSettings.panel=switch(ClientSettings.panel){case "small"->"medium";case "medium"->"large";default->"small";};ClientSettings.save();page.resize();});
            }
            case "combat"->{
                page.line("战斗显示",UiTheme.GOLD);page.gap();
                page.rowButton("本人伤害数字倍率："+ClientSettings.damageScale,()->{ClientSettings.damageScale=ClientSettings.damageScale==1?2:ClientSettings.damageScale==2?4:1;ClientSettings.save();page.rebuild();});
                page.rowButton("目标框位置："+ClientSettings.targetPosition,()->{ClientSettings.targetPosition=ClientSettings.targetPosition.equals("top_center")?"top_left":"top_center";ClientSettings.save();page.rebuild();});
                page.rowButton("目标框倍率："+ClientSettings.targetScale,()->{double value=ClientSettings.targetScale==1?1.5:ClientSettings.targetScale==1.5?2:1;if(180*value>page.windowWidth()-16){message.accept("当前窗口无法容纳该倍率");return;}ClientSettings.targetScale=value;ClientSettings.save();page.rebuild();});
            }
            case "machines"->{
                page.line("机器与背包",UiTheme.GOLD);page.gap();
                page.rowButton("跟随统一主题："+(ClientSettings.machineFollowsTheme?"开":"关"),()->{ClientSettings.follow(ThemeDomain.MACHINE,!ClientSettings.machineFollowsTheme);page.rebuild();});
                if(!ClientSettings.machineFollowsTheme)page.rowButton("面板主题："+ClientSettings.themeName(ClientSettings.machineTheme),()->{ClientSettings.toggleDomain(ThemeDomain.MACHINE);page.rebuild();});
                page.line("工业设备、制作器、配方、结构、概率和次元背包共用此设置。",UiTheme.MUTED);
                page.line("取消跟随时保留当前样式。",UiTheme.MUTED);
            }
            case "keys"->{
                page.line("字体与按键",UiTheme.GOLD);page.gap();page.line("界面字体："+UiFonts.currentName());page.line(UiFonts.PREVIEW);
                if(!UiFonts.status().isEmpty())page.line(UiFonts.status(),UiTheme.MUTED);
                page.line("按键可在原版设置中修改。普通、Ctrl、Alt三层共24槽。");
                page.line("I 返回菜单；Esc 关闭；Ctrl＋】开始或停止伤害统计。",UiTheme.MUTED);
            }
            default->throw new IllegalArgumentException("未知设置分组");
        }
    }
}
