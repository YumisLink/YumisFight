package cn.yumis.fight.modules.professions.api;
import java.util.*;
/** 已开放副职业的稳定身份；未设计玩法不进入操作奖励注册表。 */
public final class ProfessionCatalog {
    public record Definition(String id,String name,long operationExperience,boolean gameplayAvailable){}
    private static final Map<String,Definition> DEFINITIONS=new LinkedHashMap<>();
    static {register(new Definition("craftsman","工匠",60,true));register(new Definition("enchanter","附魔师",0,false));register(new Definition("farmer","农夫",10,true));register(new Definition("fisherman","渔夫",20,true));}
    private ProfessionCatalog(){}
    public static synchronized void register(Definition value){if(value.id()==null||value.id().isBlank()||value.operationExperience()<0||DEFINITIONS.putIfAbsent(value.id(),value)!=null)throw new IllegalArgumentException("Invalid or duplicate profession");}
    public static synchronized Definition require(String id){var value=DEFINITIONS.get(id);if(value==null)throw new IllegalArgumentException("无效副职业");return value;}
    public static synchronized List<Definition> all(){return List.copyOf(DEFINITIONS.values());}
}

