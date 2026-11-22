package cn.yumis.fight.modules.quests.api;

import cn.yumis.fight.modules.dungeons.api.Difficulty;
import cn.yumis.fight.modules.dungeons.api.DungeonCatalog;
import cn.yumis.fight.modules.economy.api.CurrencyRules;
import java.util.*;

/** Wild commission contract: approved payouts, level-capped targets, one active commission per player. */
public final class Commissions {
    public static final String ACTION="commission";
    /** The definition id of the village commission agent; content stays unpublished until the owner supplies it. */
    public static final String NPC="commission_agent";

    public enum Kind {
        DUNGEON_CLEAR, DUNGEON_MATERIAL, CURRENCY, PROFESSION, HUNT, CHEST;
        public String id(){return name().toLowerCase(Locale.ROOT);}
        public static Kind of(String value){return valueOf(value.toUpperCase(Locale.ROOT));}
        public long points(){return switch(this){case DUNGEON_CLEAR->60L;case DUNGEON_MATERIAL,CURRENCY,CHEST->50L;case PROFESSION,HUNT->40L;};}
    }

    public static final int MATERIAL_COUNT=10, MATERIAL_BONUS_COUNT=2, CURRENCY_COUNT=20, HUNT_COUNT=20, CHEST_COUNT=3, BONUS_COUNT=1;
    public static final Map<String,Integer> PROFESSION_GOALS=Map.of("brew",3,"craft",5,"smelt",5,"herb",10,"harvest",10,"enchant",2,"fishing",5);
    public static final Map<String,String> PROFESSION_LABELS=Map.of("brew","酿造药水","craft","制作物品","smelt","熔炼材料","herb","镰刀采草","harvest","收获成熟作物","enchant","附魔物品","fishing","钓鱼");
    /** The fishing goal counts every rod kind as one activity. */
    public static final Set<String> FISHING_KINDS=Set.of("fishing","lava","void");

    /** Whole stored state: at most one active commission plus the finished count. */
    public record CommissionState(Active active,long done){
        public static final CommissionState EMPTY=new CommissionState(null,0);
        public CommissionState{if(done<0)throw new IllegalArgumentException("委托完成数无效");}
    }

    public record Active(String kind,String dungeon,String difficulty,String item,int count,int progress,long points,String bonus,int bonusCount){
        public Active{
            Kind parsed;
            try{parsed=Kind.of(kind);}catch(RuntimeException failure){throw new IllegalArgumentException("委托类型无效");}
            if(count<1||count>64||progress<0||progress>count||points!=parsed.points())throw new IllegalArgumentException("委托定义无效");
            switch(parsed){
                case DUNGEON_CLEAR -> {
                    if(!id(dungeon)||difficulty==null||difficulty.isBlank())throw new IllegalArgumentException("委托副本目标无效");
                    try{Difficulty.valueOf(difficulty);}catch(RuntimeException failure){throw new IllegalArgumentException("委托难度无效");}
                    if(count!=1||item==null||!item.isEmpty()||bonus==null||!bonus.isEmpty()||bonusCount!=0)throw new IllegalArgumentException("委托定义无效");
                }
                case DUNGEON_MATERIAL -> {
                    if(!id(dungeon)||difficulty!=null&&!difficulty.isBlank()||!id(item)||!id(bonus))throw new IllegalArgumentException("委托材料目标无效");
                    if(count!=MATERIAL_COUNT||bonusCount!=MATERIAL_BONUS_COUNT)throw new IllegalArgumentException("委托数量无效");
                }
                case CURRENCY -> {
                    if(!id(item)||dungeon==null||!dungeon.isEmpty()||bonus==null||!bonus.isEmpty()||bonusCount!=0||count!=CURRENCY_COUNT)throw new IllegalArgumentException("委托通货目标无效");
                }
                case PROFESSION -> {
                    if(!id(item)||dungeon==null||!dungeon.isEmpty()||bonus==null||bonus.isBlank()||bonusCount!=BONUS_COUNT)throw new IllegalArgumentException("委托活动目标无效");
                    if(!PROFESSION_GOALS.containsKey(item)||count!=PROFESSION_GOALS.get(item))throw new IllegalArgumentException("委托活动数量无效");
                }
                case HUNT -> {if(dungeon==null||!dungeon.isEmpty()||item==null||!item.isEmpty()||bonus==null||!bonus.isEmpty()||bonusCount!=0||count!=HUNT_COUNT)throw new IllegalArgumentException("委托讨伐目标无效");}
                case CHEST -> {if(dungeon==null||!dungeon.isEmpty()||item==null||!item.isEmpty()||bonus==null||!bonus.isEmpty()||bonusCount!=0||count!=CHEST_COUNT)throw new IllegalArgumentException("委托宝箱目标无效");}
            }
        }
    }

    /** Server-fact goals advance through the dungeon clear chain; the rest are owner-observed counts. */
    public static boolean serverCounted(Active active){return Kind.of(active.kind())==Kind.DUNGEON_CLEAR;}
    /** Counting goals are owner-observed and persist through progress writes; delivery goals settle at an agent. */
    public static boolean ownerCounted(Active active){var kind=Kind.of(active.kind());return kind==Kind.PROFESSION||kind==Kind.HUNT||kind==Kind.CHEST;}
    public static boolean delivered(Active active){var kind=Kind.of(active.kind());return kind==Kind.DUNGEON_MATERIAL||kind==Kind.CURRENCY;}

    private static boolean id(String value){return value!=null&&value.matches("[a-z0-9_]+");}

    /** Checks the drawn commission against the same facts the server will check on receive. */
    public static void validate(Active active,int level){
        var kind=Kind.of(active.kind());
        switch(kind){
            case DUNGEON_CLEAR, DUNGEON_MATERIAL -> {
                var dungeon=DungeonCatalog.current().dungeon(active.dungeon());
                if(dungeon.level()>Math.max(1,level))throw new IllegalArgumentException("委托副本超过角色等级");
                if(kind==Kind.DUNGEON_MATERIAL){
                    if(!dungeon.materials().contains(active.item()))throw new IllegalArgumentException("委托材料不属于该副本");
                    if(!dungeon.materials().contains(active.bonus()))throw new IllegalArgumentException("委托附加奖励无效");
                }
            }
            case CURRENCY -> {
                var currency=CurrencyRules.find(active.item());
                if(currency==null||!currency.naturalDrop())throw new IllegalArgumentException("委托通货不在自然掉落池");
            }
            case PROFESSION -> {
                var bonus=CurrencyRules.find(active.bonus());
                if(bonus==null||!bonus.naturalDrop())throw new IllegalArgumentException("委托附加奖励无效");
            }
            case HUNT, CHEST -> {}
        }
    }

    /** Level-capped eligible dungeons; the commission never points above the player's own level. */
    public static List<DungeonCatalog.DungeonDef> dungeons(int level){
        return DungeonCatalog.current().dungeons.stream().filter(dungeon->dungeon.available()&&dungeon.level()<=Math.max(1,level)).toList();
    }

    /** The owner's machine draws the commission; the server only validates the result. */
    public static Active draw(int level,Random random){
        var kinds=new ArrayList<>(List.of(Kind.values()));
        if(dungeons(level).isEmpty())kinds.removeAll(List.of(Kind.DUNGEON_CLEAR,Kind.DUNGEON_MATERIAL));
        var kind=kinds.get(random.nextInt(kinds.size()));
        return switch(kind){
            case DUNGEON_CLEAR -> {var dungeon=pick(dungeons(level),random);yield new Active(kind.id(),dungeon.id(),Difficulty.values()[random.nextInt(Difficulty.values().length)].name(),"",1,0,kind.points(),"",0);}
            case DUNGEON_MATERIAL -> {
                var dungeon=pick(dungeons(level),random);var materials=dungeon.materials();
                var item=materials.get(random.nextInt(materials.size()));
                var bonus=item;while(materials.size()>1&&bonus.equals(item))bonus=materials.get(random.nextInt(materials.size()));
                yield new Active(kind.id(),dungeon.id(),"",item,MATERIAL_COUNT,0,kind.points(),bonus,MATERIAL_BONUS_COUNT);
            }
            case CURRENCY -> {var currency=pick(CurrencyRules.DROP_POOL,random);yield new Active(kind.id(),"","",currency.id(),CURRENCY_COUNT,0,kind.points(),"",0);}
            case PROFESSION -> {
                var keys=new ArrayList<>(PROFESSION_GOALS.keySet());
                var item=keys.get(random.nextInt(keys.size()));
                var bonus=pick(CurrencyRules.DROP_POOL,random).id();
                yield new Active(kind.id(),"","",item,PROFESSION_GOALS.get(item),0,kind.points(),bonus,BONUS_COUNT);
            }
            case HUNT -> new Active(kind.id(),"","","",HUNT_COUNT,0,kind.points(),"",0);
            case CHEST -> new Active(kind.id(),"","","",CHEST_COUNT,0,kind.points(),"",0);
        };
    }

    private static <T> T pick(List<T> values,Random random){return values.get(random.nextInt(values.size()));}

    public static String title(Active active){
        return switch(Kind.of(active.kind())){
            case DUNGEON_CLEAR -> "副本讨伐委托";
            case DUNGEON_MATERIAL -> "材料收集委托";
            case CURRENCY -> "通货收集委托";
            case PROFESSION -> "副职业委托";
            case HUNT -> "野外讨伐委托";
            case CHEST -> "寻宝开箱委托";
        };
    }

    /** Display text for the task card; names come from the same catalogs the server validates against. */
    public static String objective(Active active){
        return switch(Kind.of(active.kind())){
            case DUNGEON_CLEAR -> "通关"+Difficulty.valueOf(active.difficulty()).title()+"难度的《"+DungeonCatalog.current().dungeon(active.dungeon()).name()+"》（"+DungeonCatalog.current().dungeon(active.dungeon()).level()+"级），完成后自动结算。";
            case DUNGEON_MATERIAL -> "上交"+materialName(active.item())+" ×"+active.count()+"（"+DungeonCatalog.current().dungeon(active.dungeon()).name()+"掉落），在任意委托NPC处交付。";
            case CURRENCY -> "收集通货"+active.item()+" ×"+active.count()+"，在任意委托NPC处交付。";
            case PROFESSION -> PROFESSION_LABELS.get(active.item())+" ×"+active.count()+"次，完成后自动结算。";
            case HUNT -> "讨伐野外敌怪 ×"+active.count()+"只，完成后自动结算。";
            case CHEST -> "开启宝物箱 ×"+active.count()+"个，完成后自动结算。";
        };
    }

    private static String materialName(String item){
        for(var material:DungeonCatalog.current().dungeons)if(material.materials().contains(item))return material.name()+" · "+item;
        return item;
    }
}
