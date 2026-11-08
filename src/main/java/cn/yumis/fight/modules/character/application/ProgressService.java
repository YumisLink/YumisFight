package cn.yumis.fight.modules.character.application;

import cn.yumis.fight.modules.character.api.*;
import cn.yumis.fight.foundation.Journal;
import cn.yumis.fight.architecture.CommandRouter;
import cn.yumis.fight.modules.inventory.api.*;
import com.google.gson.*;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;
import java.util.function.*;

/** Stores owner-authored character results together with their inventory changes. */
public final class ProgressService {
    private static final Gson JSON=new Gson();
    private final Journal journal;private final CharacterPersistence records;private final InventoryOperations inventory;
    private final Function<ServerPlayer,Progress> progressOf;private final BiConsumer<ServerPlayer,Progress> update;private final Consumer<ServerPlayer> profile;
    private JobTransitions transitions;
    public ProgressService(Journal journal,CharacterPersistence records,InventoryOperations inventory,Function<ServerPlayer,Progress> progressOf,BiConsumer<ServerPlayer,Progress> update,Consumer<ServerPlayer> profile){this.journal=journal;this.records=records;this.inventory=inventory;this.progressOf=progressOf;this.update=update;this.profile=profile;}
    public void transitions(JobTransitions value){transitions=Objects.requireNonNull(value);}
    public void register(CommandRouter<ServerPlayer,JsonObject> router){router.register("character","progress",this::request);}
    public void markQuest(ServerPlayer p,String base){if(!JobCatalog.basic(base))throw new IllegalArgumentException("需要基础职业ID");var next=CareerProfiles.copy(progressOf.apply(p));next.unlockJobChange();persistAdmin(p,next);}
    public void awaken(ServerPlayer p){var next=CareerProfiles.copy(progressOf.apply(p));if(next.level<50||!next.selected()||JobCatalog.basic(next.jobId()))throw new IllegalArgumentException("觉醒需要50级且已经转职");next.awakened=true;persistAdmin(p,next);}
    private void persistAdmin(ServerPlayer p,Progress next){try{journal.transaction(records.writes(p.getUUID(),next));update.accept(p,next);profile.accept(p);}catch(Exception failure){throw new IllegalStateException(failure);}}
    public void request(ServerPlayer p,JsonObject j){
        String op=j.get("operation").getAsString();UUID.fromString(op);String key="character-v2/receipt/"+p.getUUID()+"/"+op;
        var prior=journal.get(key);if(!prior.isJsonNull()){if(!prior.equals(j))throw new IllegalArgumentException("操作号冲突");inventory.restore(p);update.accept(p,records.read(p.getUUID()));profile.accept(p);return;}
        var book=records.profiles(p.getUUID());book.requireCurrent(j);
        if(!JSON.toJsonTree(progressOf.apply(p)).equals(j.get("beforeProgress"))||!book.stateKey().equals(j.get("beforeProfilesKey").getAsString()))throw new IllegalArgumentException("角色资料已更新，请刷新后重试");
        var next=JSON.fromJson(j.get("progress"),Progress.class);var nextBook=book.applyDelta(j.getAsJsonObject("profiles"));
        String kind=j.get("kind").getAsString();JobTransitions.Change change=null;
        if(!book.activeId.equals(nextBook.activeId))change=transitions.switchProfile(p,next);
        else if(kind.equals("promote")||kind.equals("chooseJob"))change=transitions.prepare(p,next,next.jobId());
        else if(kind.equals("resetSkills"))change=transitions.clear(p,next);
        else if(kind.equals("unlearn"))change=transitions.reduceSkill(p,next,j.get("skill").getAsString());
        var writes=new LinkedHashMap<>(records.writes(p.getUUID(),next,nextBook));writes.put(key,j.deepCopy());if(change!=null)writes.putAll(change.writes());
        var staged=j.deepCopy();var outputs=staged.remove("outputs");var committedChange=change;
        try{inventory.transact(p,op,j,()->{
            InventoryEdits.apply(p,inventory,staged);
            if(outputs!=null)for(var raw:outputs.getAsJsonArray()){
                var item=inventory.decode(p,raw);if(InventoryPacking.room(p,item)>=item.getCount())inventory.requireSpace(p,item);
                else if(p.drop(item,false)==null)throw new IllegalStateException("奖励掉落被取消，原操作已保留");
            }
        },writes);}catch(RuntimeException failure){if(journal.get(key).equals(j)){update.accept(p,records.read(p.getUUID()));if(committedChange!=null)committedChange.committed().run();}throw failure;}
        update.accept(p,next);if(change!=null)change.committed().run();profile.accept(p);
    }
}
