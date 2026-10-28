package cn.yumis.fight.modules.professions.application;
import cn.yumis.fight.modules.professions.api.ProfessionCatalog;
import cn.yumis.fight.modules.professions.api.Profession;
import cn.yumis.fight.modules.professions.api.*;
import cn.yumis.fight.foundation.Journal;
import com.google.gson.*;
import java.util.*;
/** 副职业自身拥有奖励、存储键和幂等校验；不依赖全局服务器运行时。 */
public final class ProfessionService {
    private static final Gson JSON=new GsonBuilder().disableHtmlEscaping().create();
    private final Journal journal;
    public ProfessionService(Journal journal){this.journal=Objects.requireNonNull(journal);}
    public Profession get(UUID player,String id){ProfessionCatalog.require(id);return journal.get("profession/"+player+"/"+id,Profession.class,new Profession());}
    public void award(UUID player,String id,long xp,String operation,boolean administrator){
        ProfessionCatalog.require(id);
        if(xp<=0||operation==null||operation.isBlank())throw new IllegalArgumentException("无效副职业奖励");
        synchronized(journal){
            String key="professionReceipt/"+player+"/"+operation;
            JsonElement intent=JSON.toJsonTree(Map.of("profession",id,"xp",xp,"administrator",administrator));
            JsonElement previous=journal.get(key);
            if(!previous.isJsonNull()){if(!previous.equals(intent))throw new IllegalArgumentException("副职业奖励操作号冲突");return;}
            Profession next=get(player,id);next.award(xp,administrator);
            try{journal.transaction(Map.of("profession/"+player+"/"+id,JSON.toJsonTree(next),key,intent));}catch(java.io.IOException failure){throw new IllegalStateException(failure);}
        }
    }
    public Map<String,Object> snapshot(UUID player){Map<String,Object> all=new LinkedHashMap<>();for(var definition:ProfessionCatalog.all()){Profession p=get(player,definition.id());all.put(definition.id(),Map.of("level",p.level(),"experience",p.experience.toString(),"coins",p.coins.toString()));}return Collections.unmodifiableMap(all);}
}

