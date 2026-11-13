package cn.yumis.fight.modules.npc.application;

import cn.yumis.fight.architecture.CommandRouter;
import cn.yumis.fight.foundation.Journal;
import cn.yumis.fight.modules.character.api.*;
import cn.yumis.fight.modules.economy.api.RewardPorts;
import cn.yumis.fight.modules.inventory.api.*;
import cn.yumis.fight.modules.npc.api.NpcTutorial;
import com.google.gson.*;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;

/** Receives owner-authored lesson facts; inventory, growth, balances and one-time claims commit together. */
public final class TutorialService {
    private static final Gson JSON=new Gson();
    private final Journal journal;private final InventoryOperations inventory;private final CharacterPersistence characters;
    private final RewardPorts.Players players;private final RewardPorts.Balance balances;
    private final java.util.function.ToLongFunction<ServerPlayer> generation;
    public TutorialService(Journal journal,InventoryOperations inventory,CharacterPersistence characters,RewardPorts.Players players,RewardPorts.Balance balances,java.util.function.ToLongFunction<ServerPlayer> generation){this.journal=journal;this.inventory=inventory;this.characters=characters;this.players=players;this.balances=balances;this.generation=generation;}
    private static String key(UUID player){return "npcTutorial/v1/"+player;}
    public NpcTutorial.State state(UUID player){return journal.get(key(player),NpcTutorial.State.class,new NpcTutorial.State());}
    public void register(CommandRouter<ServerPlayer,JsonObject> router){router.register("npc","tutorial",this::receive);}
    private void receive(ServerPlayer player,JsonObject request){
        if(!players.session(player).toString().equals(request.get("session").getAsString()))throw new IllegalArgumentException("会话已失效");
        String operation=request.get("operation").getAsString();UUID.fromString(operation);String receipt="npcTutorial/receipt/"+player.getUUID()+"/"+operation;
        var previous=journal.get(receipt);if(!previous.isJsonNull()){
            if(!previous.equals(request))throw new IllegalArgumentException("引导操作号冲突");inventory.restore(player);players.progress(player,characters.read(player.getUUID()));players.profile(player);return;
        }
        if(generation.applyAsLong(player)!=request.get("generation").getAsLong())throw new IllegalArgumentException("生命代际已变化，请重试");
        if(!JSON.toJsonTree(state(player.getUUID())).equals(request.get("beforeTutorial")))throw new IllegalArgumentException("任务资料已更新，请重试");
        var writes=new LinkedHashMap<String,JsonElement>();writes.put(key(player.getUUID()),request.get("tutorial").deepCopy());writes.put(receipt,request.deepCopy());
        if(request.get("kind").getAsString().equals("claim")){
            String lesson=request.get("lesson").getAsString();if(!lesson.matches("[a-z0-9_]+"))throw new IllegalArgumentException("任务标识无效");
            String claim="npcTutorial/claim/v1/"+player.getUUID()+"/"+lesson;
            if(!journal.get(claim).isJsonNull())throw new IllegalArgumentException("这项任务奖励已经领取");
            writes.put(claim,new JsonPrimitive(operation));
        }
        Progress next=null;
        if(request.has("progress")){
            characters.profiles(player.getUUID()).requireCurrent(request);
            if(!JSON.toJsonTree(players.progress(player)).equals(request.get("beforeProgress")))throw new IllegalArgumentException("角色资料已更新，请重试");
            next=JSON.fromJson(request.get("progress"),Progress.class);writes.putAll(characters.writes(player.getUUID(),next));
        }
        writes.putAll(balances.receive(player,request));
        boolean committed;
        try{committed=inventory.transact(player,operation,request,()->{
            for(var raw:request.getAsJsonArray("outputs")){
                var item=inventory.decode(player,raw);if(InventoryPacking.room(player,item)>=item.getCount())inventory.requireSpace(player,item);
                else if(player.drop(item,false)==null)throw new IllegalStateException("任务奖励掉落被取消，请保留背包空位后重试");
            }
        },writes);}catch(RuntimeException failure){if(journal.get(receipt).equals(request)&&next!=null)players.progress(player,characters.read(player.getUUID()));throw failure;}
        if(!committed&&!journal.get(receipt).equals(request))throw new IllegalStateException("任务奖励尚未保存，请保留背包空位后重试");
        if(next!=null)players.progress(player,next);
        players.profile(player);
        if(committed&&request.has("goldGain"))balances.notifyGain(player,request.get("goldGain").getAsLong(),balances.gold(player),balances.revision(player));
    }
}
