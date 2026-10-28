package cn.yumis.fight.modules.professions.client;

import cn.yumis.fight.modules.professions.api.ProfessionCatalog;
import cn.yumis.fight.modules.presentation.api.client.PlayerPageSurface;
import com.google.gson.JsonObject;
import java.util.Map;

public final class ProfessionPage {
    private static final Map<String,String> ACTIONS=Map.of("craftsman","装备制作成功","farmer","收获成熟作物","fisherman","钓获");
    private ProfessionPage(){}
    public static void render(PlayerPageSurface page,JsonObject snapshot,long gold,Runnable refresh,int titleColor,int muted){
        if(!snapshot.has("professions")){page.line("正在读取副职业…");return;}
        var all=snapshot.getAsJsonObject("professions");
        for(var definition:ProfessionCatalog.all()){
            if(!all.has(definition.id()))continue;var progress=all.getAsJsonObject(definition.id());
            page.line(definition.name()+"  Lv."+progress.get("level").getAsInt()+" / 10",titleColor);
            page.line("累计经验 "+progress.get("experience").getAsString()+"    职业币 "+progress.get("coins").getAsString());
            page.line(definition.gameplayAvailable()?ACTIONS.getOrDefault(definition.id(),"有效操作")+" +"+definition.operationExperience()+"经验与职业币":"自定义附魔接口保留，暂未开放",muted);
            page.gap();
        }
        page.line("金币："+gold);page.line("TODO：矿工、药剂师、分解师。");page.rowButton("刷新",refresh);
    }
}
