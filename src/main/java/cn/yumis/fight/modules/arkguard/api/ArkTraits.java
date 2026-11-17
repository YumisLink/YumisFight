package cn.yumis.fight.modules.arkguard.api;

import java.util.*;

/** Pure roster/trait rules shared by the owner's preview and the world actors. */
public final class ArkTraits {
    private ArkTraits(){}
    public static Map<String,Integer> counts(ArkRoster roster,ArkRules rules){
        var counts=new LinkedHashMap<String,Integer>();var unique=new HashSet<String>();
        for(var card:roster.cards)if(card.deployed&&!card.held&&unique.add(card.unit))
            for(String trait:rules.unit(card.unit).traits())counts.merge(trait,1,Integer::sum);
        boolean beasts=counts.getOrDefault("beast",0)>0,witches=counts.getOrDefault("witch_garden",0)>0;
        if(beasts&&witches){counts.put("beast",0);counts.put("witch_garden",0);}
        else if(beasts&&beast(roster,rules).equals("ark_benares"))
            counts.replaceAll((id,value)->id.equals("beast")||value==0?value:value+1);
        return counts;
    }
    public static String beast(ArkRoster roster,ArkRules rules){
        boolean witch=roster.cards.stream().anyMatch(c->c.deployed&&!c.held&&rules.unit(c.unit).has("witch_garden"));
        if(witch)return "";
        return roster.cards.stream().filter(c->c.deployed&&!c.held&&rules.unit(c.unit).has("beast"))
            .min(Comparator.comparingLong(c->c.placedAt)).map(c->c.unit).orElse("");
    }
    public static int tier(String id,Map<String,Integer> counts,ArkRules rules){
        int count=counts.getOrDefault(id,0),tier=0;
        for(int threshold:rules.trait(id).thresholds())if(count>=threshold)tier++;
        return tier;
    }
    public static boolean active(String id,Map<String,Integer> counts,ArkRules rules){return tier(id,counts,rules)>0;}
    public static List<String> present(ArkRoster roster,ArkRules rules){
        var result=new LinkedHashSet<String>();
        for(var card:roster.cards)if(card.deployed&&!card.held)result.addAll(rules.unit(card.unit).traits());
        return List.copyOf(result);
    }
    public static boolean brute(ArkRules.Unit unit,Map<String,Integer> counts,Map<String,Integer> layers,ArkRules rules){
        return active("brute",counts,rules)&&(unit.has("brute")||layers.getOrDefault("brute",0)>120&&unit.range()<=3);
    }
    public static boolean precise(ArkRules.Unit unit,Map<String,Integer> counts,Map<String,Integer> layers,ArkRules rules){
        return active("precision",counts,rules)&&(unit.has("precision")||layers.getOrDefault("precision",0)>=120&&unit.range()>6);
    }
    public static double range(ArkRules.Unit unit,Map<String,Integer> counts,Map<String,Integer> layers,ArkRules rules){
        return unit.range()+(precise(unit,counts,layers,rules)?1+.01*layers.getOrDefault("precision",0):0);
    }
    public static double longevity(Map<String,Integer> counts,ArkRules rules){return switch(tier("longevity",counts,rules)){case 1->.3;case 2->.5;case 3->1;default->0;};}
    public static List<String> details(String id,Map<String,Integer> counts,Map<String,Integer> layers,ArkRules rules){
        int l=layers.getOrDefault(id,0),tier=tier(id,counts,rules);
        var rows=new ArrayList<String>();
        switch(id){
            case "fortune"->rows.add("下一准备阶段额外收入："+Math.min(3,counts.getOrDefault(id,0))+"方舟币");
            case "arcane"->{rows.add("当前附伤："+ArkRules.number(l>120?25+.25*l:10+.1*l)+"%");if(l>120)rows.add("忽略魔法防御："+ArkRules.number(40+.01*l)+"%");}
            case "brute"->{rows.add("攻击提高："+(30+l)+"%");if(l>120)rows.add("余震："+ArkRules.number(10+.01*l)+"%主伤害");}
            case "longevity"->rows.add("核心生命／双塔伤害提高："+ArkRules.number(longevity(counts,rules)*100)+"%");
            case "precision"->rows.add("攻击范围增加："+ArkRules.number(1+.01*l)+"格");
            case "chronology"->rows.add("攻速提高："+(10+l)+"%");
            case "victoria"->rows.add("单角色情绪上限："+(300+l)+"层");
            case "kalien"->rows.add("攻击提高："+ArkRules.number(5+.5*l)+"%");
            case "white_church"->{rows.add("攻击提高："+(tier>=3?100:tier==2?50:20)+"%");rows.add("光伤倍率："+(tier>=3?150+2*l:125+l)+"%");}
            case "winter"->{rows.add("冻结积累提高："+(100+5*l)+"%");if(l>=40)rows.add("对冻结目标伤害倍率："+(220+5*l)+"%");}
            case "guardian"->rows.add("两防提高："+(30+2*l)+"%");
            case "flame_lord"->{rows.add("攻击提高："+(25+l)+"%");rows.add("每0.5秒灼燃："+(tier>=3?100000+500L*l:tier==2?50000+250L*l:30000+200L*l));}
            default->{}
        }
        rows.addAll(rules.trait(id).description());return rows;
    }
}
