package cn.yumis.fight.modules.economy.api;

import cn.yumis.fight.modules.equipment.api.Rarity;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.EnumSet;
import java.util.List;

/** 本机自动出售匹配：一条规则选择若干稀有度加一个等级比较，命中任一规则即出售该装备。 */
public record AutoSellRule(EnumSet<Rarity> rarities,Comparison comparison,int level){
    public enum Comparison{GREATER,LESS,ANY}

    public static final int LEVEL_MIN=1,LEVEL_MAX=85,RULE_LIMIT=8;
    public static final AutoSellRule DEFAULT=new AutoSellRule(EnumSet.of(Rarity.COMMON),Comparison.ANY,LEVEL_MIN);

    public AutoSellRule{
        rarities=rarities==null?EnumSet.noneOf(Rarity.class):EnumSet.copyOf(rarities);
        comparison=comparison==null?Comparison.ANY:comparison;
        level=Math.max(LEVEL_MIN,Math.min(LEVEL_MAX,level));
    }

    public boolean matches(Rarity rarity,int gearLevel){
        if(!rarities.contains(rarity))return false;
        return switch(comparison){case ANY->true;case GREATER->gearLevel>level;case LESS->gearLevel<level;};
    }
    public static boolean matches(List<AutoSellRule> rules,Rarity rarity,int gearLevel){
        for(var rule:rules)if(rule!=null&&rule.matches(rarity,gearLevel))return true;
        return false;
    }
    public AutoSellRule withComparison(Comparison next){return new AutoSellRule(rarities,next,level);}
    public AutoSellRule withLevel(int next){return new AutoSellRule(rarities,comparison,next);}
    public AutoSellRule withRarity(Rarity rarity,boolean selected){
        var next=EnumSet.copyOf(rarities);
        if(selected)next.add(rarity);else next.remove(rarity);
        return new AutoSellRule(next,comparison,level);
    }
    public static Comparison cycle(Comparison current){return switch(current){case GREATER->Comparison.LESS;case LESS->Comparison.ANY;case ANY->Comparison.GREATER;};}
    public JsonElement encode(){
        var root=new JsonObject();var names=new JsonArray();
        for(var rarity:rarities)names.add(rarity.name());
        root.add("rarities",names);root.addProperty("comparison",comparison.name());root.addProperty("level",level);
        return root;
    }
    /** 本机持久化的宽容解码；损坏条目返回 null，由调用方丢弃。 */
    public static AutoSellRule decode(JsonElement raw){
        try{
            var root=raw.getAsJsonObject();
            var rarities=EnumSet.noneOf(Rarity.class);
            for(var name:root.getAsJsonArray("rarities"))rarities.add(Rarity.valueOf(name.getAsString()));
            return new AutoSellRule(rarities,Comparison.valueOf(root.get("comparison").getAsString()),root.get("level").getAsInt());
        }catch(RuntimeException invalid){return null;}
    }
}
