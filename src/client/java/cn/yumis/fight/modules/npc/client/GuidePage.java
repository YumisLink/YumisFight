package cn.yumis.fight.modules.npc.client;

import cn.yumis.fight.modules.npc.api.PlayGuide;
import cn.yumis.fight.modules.presentation.api.client.*;

import java.util.List;
import java.util.function.Consumer;

/** 玩法看板：只读列出当前能做的事，条目由 PlayGuide 派生，玩家自由选择，不做任何催促。 */
public final class GuidePage {
    private GuidePage(){}
    public static void render(PlayerPageSurface page,List<PlayGuide.GuideEntry> entries,Consumer<String> navigate){
        page.line("玩法看板",UiTheme.GOLD);
        page.line("这些是你现在能做的事，挑喜欢的去做；列表随等级与进度自动变化。",UiTheme.MUTED);
        page.gap();
        if(entries.isEmpty()){page.line("暂时没有可做的事。",UiTheme.TEXT);return;}
        for(var entry:entries){
            page.line(entry.title(),UiTheme.TEXT);
            page.line(entry.note(),UiTheme.MUTED);
            if(!entry.target().isBlank())page.rowButton("前往",()->navigate.accept(entry.target()));
            page.gap();
        }
    }
}
