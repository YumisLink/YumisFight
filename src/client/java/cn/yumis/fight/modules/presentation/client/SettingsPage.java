package cn.yumis.fight.modules.presentation.client;
import cn.yumis.fight.modules.mining.api.VeinMining;
import cn.yumis.fight.modules.presentation.api.client.*;
import java.util.List;
import java.util.Locale;
public final class SettingsPage {
    private SettingsPage(){}
    public static void render(PlayerPageSurface page){
        String section=page.remember("settings.section","general");
        if(section.equals("keys")){section="general";page.rememberValue("settings.section",section);}
        page.navigation(List.of(new PlayerPageSurface.Navigation("general","通用界面"),new PlayerPageSurface.Navigation("combat","战斗显示"),new PlayerPageSurface.Navigation("machines","机器与背包"),new PlayerPageSurface.Navigation("mining","采矿")),section,id->{page.rememberValue("settings.section",id);page.rebuild();});
        switch(section){
            case "general"->{
                page.line("通用界面",UiTheme.GOLD);page.gap();
                page.rowButton("物品品质外框颜色："+(ClientSettings.itemQualityFrames?"开":"关"),()->{ClientSettings.itemQualityFrames=!ClientSettings.itemQualityFrames;ClientSettings.save();page.rebuild();});
                page.rowButton("统一主题："+ClientSettings.themeName(ClientSettings.globalTheme),()->{ClientSettings.toggleGlobalTheme();page.rebuild();});
                page.rowButton("I面板跟随统一主题："+(ClientSettings.menuFollowsTheme?"开":"关"),()->{ClientSettings.follow(ThemeDomain.MENU,!ClientSettings.menuFollowsTheme);page.rebuild();});
                if(!ClientSettings.menuFollowsTheme)page.rowButton("I面板主题："+ClientSettings.themeName(ClientSettings.menuTheme),()->{ClientSettings.toggleDomain(ThemeDomain.MENU);page.rebuild();});
                page.rowButton("面板大小："+ClientSettings.panel,()->{ClientSettings.panel=switch(ClientSettings.panel){case "small"->"medium";case "medium"->"large";default->"small";};ClientSettings.save();page.resize();});
            }
            case "combat"->{
                page.line("战斗显示",UiTheme.GOLD);page.gap();
                page.rowButton("第一人称近身特效淡化："+(ClientSettings.nearEffectFade?"开":"关"),()->{ClientSettings.nearEffectFade=!ClientSettings.nearEffectFade;ClientSettings.save();page.rebuild();});
                page.rowSlider(0, ClientSettings.MAX_NEAR_EFFECT_FADE_DISTANCE_STEPS, ClientSettings.nearEffectFadeDistanceSteps,
                    value -> String.format(Locale.ROOT,"近身特效淡化距离：%.2f 格",value/100d), value -> {
                        ClientSettings.nearEffectFadeDistanceSteps = value;
                        ClientSettings.save();
                    });
                page.rowSlider(1, ClientSettings.MAX_DAMAGE_SCALE, ClientSettings.damageScale,
                    value -> "本人伤害数字倍率："+value+" 倍", value -> {
                        ClientSettings.damageScale = value;
                        ClientSettings.save();
                    });
                page.rowButton("目标框位置："+ClientSettings.targetPosition,()->{ClientSettings.targetPosition=ClientSettings.targetPosition.equals("top_center")?"top_left":"top_center";ClientSettings.save();page.rebuild();});
                int targetMax = Math.clamp((page.windowWidth()-12)*2/ClientSettings.TARGET_FRAME_WIDTH, 2, 4);
                int targetStep = (int) (ClientSettings.targetScale*2);
                page.rowSlider(2, targetMax, targetStep <= targetMax ? targetStep : 2,
                    value -> "目标框倍率："+(value*50)+"%", value -> {
                        ClientSettings.targetScale = value/2.0;
                        ClientSettings.save();
                    });
                page.rowButton("敌人血条近大远小："+(ClientSettings.enemyHealthPerspective?"开":"关"),()->{
                    ClientSettings.enemyHealthPerspective=!ClientSettings.enemyHealthPerspective;ClientSettings.save();page.rebuild();
                });
                page.rowButton("攻击中立生物："+(ClientSettings.attackNeutralMobs?"开":"关"),()->{
                    ClientSettings.attackNeutralMobs=!ClientSettings.attackNeutralMobs;ClientSettings.save();page.rebuild();
                });
                page.rowSlider(1, ClientSettings.MAX_ENEMY_HEALTH_DISTANCE, ClientSettings.enemyHealthDistance,
                    value -> "敌人血条显示距离："+value+" 格", value -> {
                        ClientSettings.enemyHealthDistance = value;
                        ClientSettings.save();
                    });
            }
            case "machines"->{
                page.line("机器与背包",UiTheme.GOLD);page.gap();
                page.rowButton("跟随统一主题："+(ClientSettings.machineFollowsTheme?"开":"关"),()->{ClientSettings.follow(ThemeDomain.MACHINE,!ClientSettings.machineFollowsTheme);page.rebuild();});
                if(!ClientSettings.machineFollowsTheme)page.rowButton("面板主题："+ClientSettings.themeName(ClientSettings.machineTheme),()->{ClientSettings.toggleDomain(ThemeDomain.MACHINE);page.rebuild();});
                page.line("工业设备、制作器、配方、结构、概率和次元背包共用此设置。",UiTheme.MUTED);
                page.line("取消跟随时保留当前样式。",UiTheme.MUTED);
            }
            case "mining"->{
                page.line("连锁采矿",UiTheme.GOLD);page.gap();
                page.rowSlider(1, VeinMining.MAX_LIMIT, ClientSettings.veinMiningLimit,
                    value -> "连锁数量（含起始方块）："+value, value -> {
                        ClientSettings.veinMiningLimit = value;
                        ClientSettings.save();
                    });
            }
            default->throw new IllegalArgumentException("未知设置分组");
        }
    }
}
