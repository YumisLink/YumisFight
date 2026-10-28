package cn.yumis.fight.modules.presentation.client;
import cn.yumis.fight.modules.presentation.api.client.ClientSettings;
import cn.yumis.fight.modules.presentation.api.client.UiFonts;
import cn.yumis.fight.modules.presentation.api.client.UiTheme;
import cn.yumis.fight.modules.presentation.api.client.PlayerPageSurface;

public final class SettingsPage {
    private SettingsPage(){}
    public static void render(PlayerPageSurface page,java.util.function.Consumer<String> message){
        page.rowButton("面板大小："+ClientSettings.panel,()->{
            ClientSettings.panel=switch(ClientSettings.panel){
                case "small"->"medium";case "medium"->"large";default->"small";
            }
            ;ClientSettings.save();page.resize();
        }
        );
        page.rowButton("本人伤害数字倍率："+ClientSettings.damageScale,()->{
            ClientSettings.damageScale=ClientSettings.damageScale==1?2:ClientSettings.damageScale==2?4:1;ClientSettings.save();page.rebuild();
        }
        );
        page.rowButton("目标框位置："+ClientSettings.targetPosition,()->{
            ClientSettings.targetPosition=ClientSettings.targetPosition.equals("top_center")?"top_left":"top_center";ClientSettings.save();page.rebuild();
        }
        );
        page.rowButton("目标框倍率："+ClientSettings.targetScale,()->{
            double value=ClientSettings.targetScale==1?1.5:ClientSettings.targetScale==1.5?2:1;if(180*value>page.windowWidth()-16){
                message.accept("当前窗口无法容纳该倍率");return;
            }
            ClientSettings.targetScale=value;ClientSettings.save();page.rebuild();
        }
        );
        page.line("按键可在原版设置中修改。普通、Ctrl、Alt三层共24槽。");
        page.gap();
        page.line("界面字体："+UiFonts.currentName());

        page.line(UiFonts.PREVIEW);
        if(!UiFonts.status().isEmpty())page.line(UiFonts.status(),UiTheme.MUTED);
        page.line("I 返回菜单；Esc 一次关闭。聚焦输入框时保留文字输入。",UiTheme.MUTED);
    }
}
