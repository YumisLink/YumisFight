package cn.yumis.fight.modules.professions.api;
import java.util.*;
/** 已开放副职业的稳定身份；未设计玩法不进入操作奖励注册表。 */
public final class ProfessionCatalog {
    public record Definition(String id,String name,long operationExperience,boolean gameplayAvailable){}
    private static final Map<String,Definition> DEFINITIONS=new LinkedHashMap<>();
    static {
        register(new Definition("craftsman","工匠",60,true));
        register(new Definition("farmer","草药",10,true));
        register(new Definition("fisherman","渔夫",20,true));
        register(new Definition("cook","厨师",8,true));
        register(new Definition("alchemist","药师",15,true));
        register(new Definition("inscription","铭文",25,true));
        register(new Definition("miner","矿工",12,true));
        register(new Definition("leatherworker","制皮",15,true));
        register(new Definition("blacksmith","锻造",20,true));
        register(new Definition("mechanic","机械",30,true));
        register(new Definition("enchanter","附魔师",0,false));
    }
    private ProfessionCatalog(){}
    public static synchronized void register(Definition value){if(value.id()==null||value.id().isBlank()||value.operationExperience()<0||DEFINITIONS.putIfAbsent(value.id(),value)!=null)throw new IllegalArgumentException("Invalid or duplicate profession");}
    public static synchronized Definition require(String id){var value=DEFINITIONS.get(id);if(value==null)throw new IllegalArgumentException("无效副职业");return value;}
    public static synchronized List<Definition> all(){return List.copyOf(DEFINITIONS.values());}
    /** 已开放玩法、可进入操作奖励注册表的职业；退役定义不参与经验发放。 */
    public static synchronized List<Definition> active(){return DEFINITIONS.values().stream().filter(Definition::gameplayAvailable).toList();}
    /** 按稳定ID查询，未登记返回null；用于事件侧容错，不抛异常。 */
    public static synchronized Definition find(String id){return DEFINITIONS.get(id);}
}
