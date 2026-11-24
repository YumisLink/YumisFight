package cn.yumis.fight.modules.economy.application;

import cn.yumis.fight.architecture.CommandRouter;
import cn.yumis.fight.foundation.Journal;
import cn.yumis.fight.modules.economy.api.*;
import cn.yumis.fight.modules.inventory.api.*;
import cn.yumis.fight.platform.ItemData;
import com.google.gson.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import java.io.IOException;
import java.util.*;
import java.util.function.*;

/** Owns the current session's committed draw and its animation. */
public final class JarService {
    private static final org.slf4j.Logger LOG=com.mojang.logging.LogUtils.getLogger();
    private static final Gson JSON=new Gson();
    private record Draw(String id,String definition,String name,String dimension,long startedAt,long seed,int offset,List<JarDefinitions.Entry> entries,JsonElement reward,boolean delivered,boolean dropped){
        Draw delivered(boolean dropped){return new Draw(id,definition,name,dimension,startedAt,seed,offset,entries,reward,true,dropped);}
    }
    private final Journal journal;
    private final InventoryOperations inventory;
    private final RewardPorts.Players players;
    private final LootService loot;
    private final EconomyService.Drafts drafts;
    private final BiConsumer<ServerPlayer,JsonObject> send;
    private final Map<UUID,Long> retryAfter=new HashMap<>();
    public JarService(Journal journal,InventoryOperations inventory,RewardPorts.Players players,LootService loot,EconomyService.Drafts drafts,BiConsumer<ServerPlayer,JsonObject> send){this.journal=journal;this.inventory=inventory;this.players=players;this.loot=loot;this.drafts=drafts;this.send=send;}
    private String key(ServerPlayer player){return "jarDraw/"+player.getUUID()+"/"+players.session(player);}
    private Draw draw(ServerPlayer player){return journal.get(key(player),Draw.class,null);}
    private void receive(ServerPlayer player,JsonObject request){
        session(player,request);var prior=journal.get("inventoryReceipt/"+player.getUUID()+"/"+request.get("operation").getAsString());if(!prior.isJsonNull()){if(!prior.getAsJsonObject().get("intent").equals(request))throw new IllegalArgumentException("操作号冲突");inventory.restore(player);var saved=draw(player);if(saved!=null)state(player,saved);players.profile(player);return;}
        var value=JSON.fromJson(drafts.value(player,request,"draw"),Draw.class);var existing=draw(player);
        if(existing!=null){if(!existing.id().equals(value.id()))throw new IllegalArgumentException("已存储另一份开罐结果");state(player,existing);return;}
        boolean committed=inventory.transact(player,request.get("operation").getAsString(),request,()->InventoryEdits.apply(player,inventory,request),Map.of(key(player),JSON.toJsonTree(value)));
        if(committed){players.profile(player);state(player,value);}
    }
    private JsonObject envelope(ServerPlayer player,Draw value,String status){
        JsonObject message=new JsonObject();message.addProperty("session",players.session(player).toString());message.addProperty("status",status);message.addProperty("serverNow",System.currentTimeMillis());
        if(value!=null){message.addProperty("id",value.id());message.addProperty("name",value.name());message.addProperty("dimension",value.dimension());message.addProperty("startedAt",value.startedAt());message.addProperty("duration",JarDefinitions.DURATION_MILLIS);message.addProperty("seed",value.seed());message.addProperty("offset",value.offset());message.add("reward",value.reward());message.addProperty("dropped",value.dropped());}
        return message;
    }
    private void state(ServerPlayer player,Draw value){
        if(value.delivered()){send.accept(player,envelope(player,value,"complete"));return;}
        // Follow the existing economy business-chunk pattern; no transport limit is enlarged.
        JsonObject message=envelope(player,value,"rolling");JsonArray entries=new JsonArray();int size=0,part=0;
        for(var entry:value.entries()){
            JsonElement row=JSON.toJsonTree(entry);int length=row.toString().length();
            if(size+length>96000&&!entries.isEmpty()){
                message.add("entries",entries);message.addProperty("part",part++);message.addProperty("done",false);send.accept(player,message.deepCopy());entries=new JsonArray();size=0;
            }
            entries.add(row);size+=length;
        }
        message.add("entries",entries);message.addProperty("part",part);message.addProperty("done",true);send.accept(player,message);
    }
    private void finish(ServerPlayer player,boolean interrupted)throws IOException{
        var value=draw(player);if(value==null)return;
        if(value.delivered()){
            if(interrupted){journal.transaction(Map.of(key(player),JsonNull.INSTANCE));send.accept(player,envelope(player,value,"interrupted"));}
            return;
        }
        var changes=new LinkedHashMap<String,JsonElement>();var drops=new ArrayList<LootService.Drop>();
        var reward=inventory.decode(player,value.reward());
        int[] stored={0};
        var intent=new JsonObject();intent.addProperty("finishJar",value.id());
        boolean committed=inventory.transact(player,"jar/finish/"+value.id(),intent,()->{
            var current=draw(player);if(current==null||!current.id().equals(value.id())||current.delivered())throw new IllegalArgumentException("罐子结算状态发生变化");
            ItemStack remaining=reward.copy();
            if(ItemData.own(remaining)&&ItemData.id(remaining).equals(CurrencyRules.COLORLESS_CRYSTAL.id())){
                String balance=CurrencyRules.COLORLESS_CRYSTAL.id()+"/"+player.getUUID();changes.put(balance,new JsonPrimitive(Math.addExact(journal.get(balance,Long.class,0L),remaining.getCount())));remaining=ItemStack.EMPTY;
            }else{
                InventoryPacking.insertWithOption(inventory.bagOption(player),player,remaining);
                stored[0]=reward.getCount()-remaining.getCount();
            }
            boolean dropped=!remaining.isEmpty();
            if(dropped){var drop=loot.prepareOverflow(player,remaining);drops.add(drop);changes.put("loot/"+drop.id,JSON.toJsonTree(drop));}
            changes.put(key(player),interrupted?JsonNull.INSTANCE:JSON.toJsonTree(value.delivered(dropped)));
        },changes);
        if(committed){
            retryAfter.remove(player.getUUID());loot.publishDrops(drops);
            if(stored[0]>0)players.lootAcquired(player,reward.copyWithCount(stored[0]));
            players.profile(player);
            send.accept(player,envelope(player,value.delivered(!drops.isEmpty()),interrupted?"interrupted":"complete"));
            players.message(player,(interrupted?"开罐已提前结算：":"开罐获得：")+reward.getHoverName().getString()+(drops.isEmpty()?"":"（背包与次元包已满，已生成私人掉落）"));
        }
    }
    public void interrupt(ServerPlayer player){
        try{finish(player,true);}
        catch(Exception failure){
            retryAfter.put(player.getUUID(),0L);
            LOG.warn("玩家 {} 开罐结算失败",player.getScoreboardName(),failure);
            players.message(player,"罐子原奖品已保留，结算失败："+failure.getMessage());
        }
    }
    public void recoverLogin(ServerPlayer player){
        String prefix="jarDraw/"+player.getUUID()+"/";
        String currentKey=key(player);
        for(String pendingKey:journal.keys(prefix)){
            if(pendingKey.equals(currentKey))continue;
            Draw value=journal.get(pendingKey,Draw.class,null);
            if(value==null||value.delivered()){
                try{journal.put(pendingKey,JsonNull.INSTANCE);}catch(Exception failure){LOG.warn("清理历史已领开罐记录失败：{}",pendingKey,failure);}
                continue;
            }
            try{
                var changes=new LinkedHashMap<String,JsonElement>();
                var drops=new ArrayList<LootService.Drop>();
                var reward=inventory.decode(player,value.reward());
                int[] stored={0};
                var intent=new JsonObject();intent.addProperty("recoverJar",value.id());
                boolean committed=inventory.transact(player,"jar/recover/"+value.id(),intent,()->{
                    ItemStack remaining=reward.copy();
                    if(ItemData.own(remaining)&&ItemData.id(remaining).equals(CurrencyRules.COLORLESS_CRYSTAL.id())){
                        String balance=CurrencyRules.COLORLESS_CRYSTAL.id()+"/"+player.getUUID();changes.put(balance,new JsonPrimitive(Math.addExact(journal.get(balance,Long.class,0L),remaining.getCount())));remaining=ItemStack.EMPTY;
                    }else{
                        InventoryPacking.insertWithOption(inventory.bagOption(player),player,remaining);
                        stored[0]=reward.getCount()-remaining.getCount();
                    }
                    if(!remaining.isEmpty()){var drop=loot.prepareOverflow(player,remaining);drops.add(drop);changes.put("loot/"+drop.id,JSON.toJsonTree(drop));}
                    changes.put(pendingKey,JsonNull.INSTANCE);
                },changes);
                if(committed){
                    loot.publishDrops(drops);
                    if(stored[0]>0)players.lootAcquired(player,reward.copyWithCount(stored[0]));
                    players.message(player,"已补发上次未结算的开罐奖品："+reward.getHoverName().getString()+(drops.isEmpty()?"":"（背包与次元包已满，已生成私人掉落）"));
                }
            }catch(Exception failure){
                LOG.warn("恢复玩家 {} 历史开罐奖品失败：{}",player.getScoreboardName(),pendingKey,failure);
            }
        }
    }
    public void discard(ServerPlayer player)throws IOException{
        retryAfter.remove(player.getUUID());
        if(draw(player)!=null)journal.put(key(player),JsonNull.INSTANCE);
    }
    public void tick(Collection<ServerPlayer> online){
        long now=System.currentTimeMillis();
        for(var player:online){
            var value=draw(player);if(value==null||value.delivered()||retryAfter.getOrDefault(player.getUUID(),0L)>now)continue;
            if(!retryAfter.containsKey(player.getUUID()))continue;
            boolean interrupted=true;
            try{finish(player,interrupted);}catch(Exception failure){retryAfter.put(player.getUUID(),now+1000);players.message(player,"罐子结算暂未完成，将保留原奖品重试："+failure.getMessage());}
        }
    }
    public void register(CommandRouter<ServerPlayer,JsonObject> router){
        router.register("economy","jarStart",this::receive);
        router.register("economy","jarFinish",(player,message)->{session(player,message);var value=draw(player);if(value!=null&&value.id().equals(message.get("id").getAsString()))finish(player,false);});
        router.register("economy","jarDismiss",(player,message)->{
            session(player,message);var value=draw(player);
            if(value!=null&&value.delivered()&&value.id().equals(message.get("id").getAsString()))journal.transaction(Map.of(key(player),JsonNull.INSTANCE));
        });
    }
    private void session(ServerPlayer player,JsonObject message){if(!message.has("session")||!players.session(player).toString().equals(message.get("session").getAsString()))throw new IllegalArgumentException("会话已失效");}
    public static void commands(com.mojang.brigadier.builder.LiteralArgumentBuilder<net.minecraft.commands.CommandSourceStack> root,Supplier<JarService> service){
        var count=net.minecraft.commands.Commands.argument("count",com.mojang.brigadier.arguments.IntegerArgumentType.integer(1,64)).executes(context->{
            var player=net.minecraft.commands.arguments.EntityArgument.getPlayer(context,"player");String definition=com.mojang.brigadier.arguments.StringArgumentType.getString(context,"definition");int amount=com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context,"count");
            var value=service.get();var item=LootJarItem.create(definition,amount);var intent=new JsonObject();intent.addProperty("grantJar",definition);intent.addProperty("count",amount);
            value.inventory.transact(player,"admin-jars/"+UUID.randomUUID(),intent,()->value.inventory.requireSpace(player,item),Map.of());return amount;
        });
        var definition=net.minecraft.commands.Commands.argument("definition",com.mojang.brigadier.arguments.StringArgumentType.word()).suggests((context,builder)->{JarDefinitions.current().jars.keySet().forEach(builder::suggest);return builder.buildFuture();}).then(count);
        root.then(net.minecraft.commands.Commands.literal("jar").then(net.minecraft.commands.Commands.literal("give").requires(source->source.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER)).then(net.minecraft.commands.Commands.argument("player",net.minecraft.commands.arguments.EntityArgument.player()).then(definition))));
    }
}
