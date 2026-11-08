package cn.yumis.fight.modules.professions.application;

import cn.yumis.fight.modules.professions.api.*;
import cn.yumis.fight.modules.inventory.api.*;
import cn.yumis.fight.foundation.Journal;
import cn.yumis.fight.architecture.CommandRouter;
import cn.yumis.fight.network.Network;
import com.google.gson.*;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;
import java.util.function.*;

/** Persists owner-authored profession facts. Explicit administrator grants remain administration. */
public final class ProfessionService {
    private static final Gson JSON=new GsonBuilder().disableHtmlEscaping().create();
    private final Journal journal;
    public ProfessionService(Journal journal){this.journal=Objects.requireNonNull(journal);}
    public Profession get(UUID player,String id){ProfessionCatalog.require(id);return journal.get("profession/"+player+"/"+id,Profession.class,new Profession());}
    public void awardAdmin(UUID player,String id,long xp,String operation){
        ProfessionCatalog.require(id);if(xp<=0||operation==null||operation.isBlank())throw new IllegalArgumentException("无效副职业奖励");
        String key="professionReceipt/"+player+"/"+operation;JsonElement intent=JSON.toJsonTree(Map.of("profession",id,"xp",xp));
        var previous=journal.get(key);if(!previous.isJsonNull()){if(!previous.equals(intent))throw new IllegalArgumentException("副职业奖励操作号冲突");return;}
        var next=get(player,id);next.award(xp,true);next.grantTitle(id);try{journal.transaction(Map.of("profession/"+player+"/"+id,JSON.toJsonTree(next),key,intent));}catch(java.io.IOException failure){throw new IllegalStateException(failure);}
    }
    public void register(CommandRouter<ServerPlayer,JsonObject> router,InventoryOperations inventory,Consumer<ServerPlayer> profile,Function<ServerPlayer,UUID> session){
        CommandRouter.Handler<ServerPlayer,JsonObject> receive=(player,j)->{
            if(!session.apply(player).toString().equals(j.get("session").getAsString()))throw new IllegalArgumentException("会话已失效");
            var previous=journal.get("inventoryReceipt/"+player.getUUID()+"/"+j.get("operation").getAsString());var changes=new LinkedHashMap<String,JsonElement>();
            if(previous.isJsonNull()&&j.has("professions"))for(var entry:j.getAsJsonObject("professions").entrySet()){String id=entry.getKey();var row=entry.getValue().getAsJsonObject();if(!JSON.toJsonTree(get(player.getUUID(),id)).equals(row.get("before")))throw new IllegalArgumentException("副职业资料已更新，请重试");changes.put("profession/"+player.getUUID()+"/"+id,row.get("after").deepCopy());}
            if(previous.isJsonNull()&&j.has("effects")){
                var stored=effects(player.getUUID());if(j.get("effectsSequence").getAsLong()!=stored.get("revision").getAsLong()+1)throw new IllegalArgumentException("药剂效果资料已更新，请重试");
                var value=new JsonObject();value.addProperty("revision",j.get("effectsSequence").getAsLong());value.add("effects",j.get("effects").deepCopy());changes.put("professionEffects/"+player.getUUID(),value);
            }
            boolean committed=inventory.transact(player,j.get("operation").getAsString(),j,()->{InventoryEdits.apply(player,inventory,j);if(j.has("drops"))for(var raw:j.getAsJsonArray("drops")){var drop=player.drop(inventory.decode(player,raw),false);if(drop==null)throw new IllegalStateException("生活奖励掉落被取消");if(j.has("dropAt")){var point=j.getAsJsonArray("dropAt");drop.setPos(point.get(0).getAsDouble(),point.get(1).getAsDouble(),point.get(2).getAsDouble());drop.setNoPickUpDelay();}}},changes);
            if(committed&&j.has("effectState")){var event=new JsonObject();event.add("operation",j.get("operation"));event.add("effectState",j.get("effectState"));event.add("effectsSequence",j.get("effectsSequence"));if(j.has("foodLevel")){player.getFoodData().setFoodLevel(j.get("foodLevel").getAsInt());player.getFoodData().setSaturation(j.get("foodSaturation").getAsFloat());event.add("foodLevel",j.get("foodLevel"));event.add("foodSaturation",j.get("foodSaturation"));}Network.send(player,"professionApplied",event);}profile.accept(player);
        };
        router.register("professions","professionResult",receive);router.register("professions","professionUse",receive);
        router.register("professions","professionEffects",(player,j)->{if(session.apply(player).toString().equals(j.get("session").getAsString())){var stored=effects(player.getUUID());if(stored.get("revision").getAsLong()==j.get("effectsSequence").getAsLong()){stored.add("effects",j.get("effects").deepCopy());journal.put("professionEffects/"+player.getUUID(),stored);}}});
    }
    public JsonObject effects(UUID player){var value=journal.get("professionEffects/"+player);if(!value.isJsonNull())return value.getAsJsonObject().deepCopy();var empty=new JsonObject();empty.addProperty("revision",0);empty.add("effects",new JsonArray());return empty;}
    public Map<String,Object> snapshot(UUID player){Map<String,Object> all=new LinkedHashMap<>();for(var definition:ProfessionCatalog.all()){var p=get(player,definition.id());all.put(definition.id(),Map.of("experience",p.experience.toString(),"coins",p.coins.toString(),"title",p.title==null?"":p.title));}return Collections.unmodifiableMap(all);}
}
