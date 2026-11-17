package cn.yumis.fight.modules.professions.client;

import cn.yumis.fight.modules.professions.api.ProfessionBonuses;
import cn.yumis.fight.modules.professions.api.ProfessionCatalog;
import cn.yumis.fight.modules.professions.api.ProfessionRules;
import cn.yumis.fight.modules.presentation.api.client.PlayerPageSurface;
import com.google.gson.JsonObject;
import java.math.BigInteger;
import java.util.Map;

/** 九条生活副职业 + 工匠的只读面板；渲染只用 line/gap/rowButton，不改网络结构。 */
public final class ProfessionPage {
    private static final Map<String,String> SOURCES=Map.ofEntries(
        Map.entry("craftsman","成功制作白装"),
        Map.entry("farmer","收获成熟作物"),
        Map.entry("fisherman","钓获"),
        Map.entry("cook","烹饪成品"),
        Map.entry("alchemist","酿造药水"),
        Map.entry("inscription","附魔台产出"),
        Map.entry("miner","开采矿石"),
        Map.entry("leatherworker","制作皮革与皮甲"),
        Map.entry("blacksmith","制作武器与护甲"),
        Map.entry("mechanic","制作工业设备与组件"),
        Map.entry("enchanter","自定义附魔接口")
    );
    private static final Map<String,String> PASSIVES=Map.ofEntries(
        Map.entry("craftsman","每件成功制作固定 60 经验"),
        Map.entry("farmer","每级 +2% 额外作物产出"),
        Map.entry("fisherman","每级 +1.5% 稀有渔获权重"),
        Map.entry("cook","每级 +2% 额外熟食产出"),
        Map.entry("alchemist","每级 +3% 药水时长"),
        Map.entry("inscription","每级 +2% 额外符文产出"),
        Map.entry("miner","每级 +2% 额外矿石产出"),
        Map.entry("leatherworker","每级 +2% 额外皮料产出"),
        Map.entry("blacksmith","每级 +2% 锻造材料返还"),
        Map.entry("mechanic","每级 +2% 额外组件产出"),
        Map.entry("enchanter","未开放，仅保留状态入口")
    );
    private ProfessionPage(){}
    public static void render(PlayerPageSurface page,JsonObject snapshot,long gold,Runnable refresh,int titleColor,int muted){
        if(!snapshot.has("professions")){page.line("正在读取副职业…");return;}
        var all=snapshot.getAsJsonObject("professions");
        int mastered=0;
        for(var definition:ProfessionCatalog.all()){
            if(!all.has(definition.id()))continue;
            var progress=all.getAsJsonObject(definition.id());
            if(progress==null||!progress.has("level")||!progress.has("experience")||!progress.has("coins"))continue;
            int level=progress.get("level").getAsInt();
            String experience=progress.get("experience").getAsString();
            if(level>=ProfessionBonuses.MAX_LEVEL)mastered++;
            String title=progress.has("title")?progress.get("title").getAsString():"";
            page.line(definition.name()+(title.isBlank()?"":"  ·  "+title)+"  Lv."+level+" / "+ProfessionBonuses.MAX_LEVEL,titleColor);
            String next=level<ProfessionBonuses.MAX_LEVEL
                ?"距下一级 "+ProfessionRules.professionThreshold(level+1).subtract(new BigInteger(experience))
                :"已满级";
            page.line("经验 "+experience+"  ·  币 "+progress.get("coins").getAsString()+"  ·  "+next,muted);
            page.line("来源："+SOURCES.getOrDefault(definition.id(),"有效操作")+"  ·  被动："+PASSIVES.getOrDefault(definition.id(),"—"),muted);
            page.gap();
        }
        page.line("已满级："+mastered+" / "+ProfessionBonuses.MAX_LEVEL+" 条线  ·  金币 "+gold,muted);
        if(mastered>=ProfessionCatalog.active().size())page.line("全职业大师 · 大地行者",titleColor);
        page.rowButton("刷新",refresh);
    }
}
