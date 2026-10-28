package cn.yumis.fight.modules.character.application;
import cn.yumis.fight.modules.character.api.SkillProgression;
import cn.yumis.fight.modules.character.api.Progress;
import cn.yumis.fight.modules.character.api.JobCatalog;

import cn.yumis.fight.modules.character.api.*;
import cn.yumis.fight.foundation.Journal;
import cn.yumis.fight.architecture.CommandRouter;
import cn.yumis.fight.modules.inventory.api.InventoryOperations;
import com.google.gson.*;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;
import java.util.function.*;
import cn.yumis.fight.modules.inventory.api.InventoryItems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
/** character application entry. Dependencies are explicit ports, not the global runtime. */
public final class ProgressService {
    private static final com.google.gson.Gson JSON=new com.google.gson.GsonBuilder().disableHtmlEscaping().create();
    private static <T>T copy(T value,Class<T> type){return JSON.fromJson(JSON.toJson(value),type);}

    private final SkillProgression skills;
    private final Journal journal;
    private final InventoryOperations inventory;
    private final Function<ServerPlayer,Progress> progressOf;
    private final ToLongFunction<ServerPlayer> combatUntil;
    private final BiConsumer<ServerPlayer,Progress> update;
    private final Consumer<ServerPlayer> profile;
    private JobTransitions transitions;
    public void transitions(JobTransitions value){transitions=Objects.requireNonNull(value);}
    public ProgressService(Journal journal,InventoryOperations inventory,Function<ServerPlayer,Progress> progressOf,ToLongFunction<ServerPlayer> combatUntil,BiConsumer<ServerPlayer,Progress> update,Consumer<ServerPlayer> profile,SkillProgression skills){this.skills=skills;this.journal=journal;this.inventory=inventory;this.progressOf=progressOf;this.combatUntil=combatUntil;this.update=update;this.profile=profile;}
    public void register(CommandRouter<ServerPlayer,JsonObject> router){router.register("character","progress",this::request);}

    public void request(ServerPlayer p,JsonObject j){
        Progress current=progressOf.apply(p);
        Progress next=copy(current,Progress.class);
        String kind=j.get("kind").getAsString();
        if(!kind.equals("bind")&&System.currentTimeMillis()<combatUntil.applyAsLong(p))throw new IllegalArgumentException("请在连续3秒未造成或受到伤害后操作");
        String op=j.get("operation").getAsString(),key="progressReceipt/"+p.getUUID()+"/"+op;
        JsonElement previous=journal.get(key);
        if(!previous.isJsonNull()){
            if(!previous.equals(j))throw new IllegalArgumentException("操作号冲突");
            profile.accept(p);
            return;
        }
        String jobId=j.has("job")?j.get("job").getAsString():JobCatalog.SWORDMASTER;
        JobTransitions.Change change=null;
        switch(kind){
            case "learn","resetSkills","bind"->skills.apply(next,j);
            case "allocate"->next.allocate(j.get("stat").getAsString(),j.has("count")?j.get("count").getAsInt():1);
            case "resetAttributes"->{
                next.strength=next.intelligence=next.spirit=0;
            }
            case "sync"->next.sync=j.get("enabled").getAsBoolean();

            case "quest"->{
                if(!cn.yumis.fight.modules.character.api.JobCatalog.canAccept(next,jobId))throw new IllegalArgumentException("转职条件未满足");
                next.acceptQuest(jobId);
            }
            case "job"->{
                if(!cn.yumis.fight.modules.character.api.JobCatalog.canComplete(next,jobId))throw new IllegalArgumentException("转职任务尚未完成");
                next.promote(jobId);
                inventory.transact(p,op,j,()->{
                    for(var material:cn.yumis.fight.modules.character.api.JobCatalog.require(jobId).materials().entrySet())InventoryItems.consume(p,BuiltInRegistries.ITEM.getValue(Identifier.parse(material.getKey())),material.getValue());
                }
                ,Map.of("player/"+p.getUUID(),JSON.toJsonTree(next),key,j));
                update.accept(p,next);
                profile.accept(p);
                return;
            }
            case "reclass"->{
                if(next.hasJob(JobCatalog.BASE)||next.hasJob(jobId)||JobCatalog.require(jobId).prerequisite().isEmpty()||next.level<JobCatalog.require(jobId).minimumLevel())throw new IllegalArgumentException("当前不能重转该职业");
                if(transitions==null)throw new IllegalStateException("转职服务未装配");
                change=transitions.prepare(p,next,jobId);
                next.promote(jobId);
            }
            default->throw new IllegalArgumentException("未知进度操作");
        }
        try{
            if(kind.equals("resetSkills")&&transitions!=null)change=transitions.clear(p,next);
            journal.begin();
            journal.put("player/"+p.getUUID(),next);
            journal.put(key,j);
            if(change!=null)for(var write:change.writes().entrySet())journal.put(write.getKey(),write.getValue());
            journal.finish();
            update.accept(p,next);
            if(change!=null)change.committed().run();
            profile.accept(p);
        }
        catch(Exception e){
            journal.abort();
            throw new IllegalStateException(e);
        }
    }
}
