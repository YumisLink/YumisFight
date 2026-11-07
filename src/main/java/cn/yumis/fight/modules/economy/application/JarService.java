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

/** Owns one durable draw per player; animation never chooses or regenerates an award. */
public final class JarService {
    private static final Gson JSON=new Gson();
    private record Draw(String id,String definition,String name,String dimension,long startedAt,long seed,int offset,List<JarDefinitions.Entry> entries,JsonElement reward,boolean delivered,boolean dropped){
        Draw delivered(boolean dropped){return new Draw(id,definition,name,dimension,startedAt,seed,offset,entries,reward,true,dropped);}
    }
    private final Journal journal;
    private final InventoryOperations inventory;
    private final RewardPorts.Players players;
    private final LootService loot;
    private final Random random;
    private final Function<ServerPlayer,String> eligibility;
    private final BiConsumer<ServerPlayer,JsonObject> send;
    private final Map<UUID,Long> retryAfter=new HashMap<>();
    public JarService(Journal journal,InventoryOperations inventory,RewardPorts.Players players,LootService loot,Random random,Function<ServerPlayer,String> eligibility,BiConsumer<ServerPlayer,JsonObject> send){
        this.journal=journal;this.inventory=inventory;this.players=players;this.loot=loot;this.random=random;this.eligibility=eligibility;this.send=send;
    }
    private static String key(ServerPlayer player){return "jarDraw/"+player.getUUID();}
    private Draw draw(ServerPlayer player){return journal.get(key(player),Draw.class,null);}
    public void open(ServerPlayer player,InteractionHand hand){
        try{
            var existing=draw(player);
            if(existing!=null){state(player,existing);return;}
            if(inventory.locked(player.getUUID()))throw new IllegalArgumentException("背包事务处理中");
            String reason=eligibility.apply(player);if(!reason.isEmpty())throw new IllegalArgumentException(reason);
            var source=player.getItemInHand(hand);String definition=LootJarItem.definition(source);
            var rule=JarDefinitions.current().require(definition);
            if(player.containerMenu!=player.inventoryMenu)player.closeContainer();
            ItemStack before=player.getItemInHand(hand).copy();
            String id=UUID.randomUUID().toString();
            var chosen=JarDefinitions.draw(rule,random);
            var reward=JarDefinitions.create(chosen,random,"jar/"+definition+"/"+id);
            var value=new Draw(id,definition,rule.name(),player.level().dimension().identifier().toString(),System.currentTimeMillis(),random.nextLong(),random.nextInt(7)-3,rule.entries().stream().filter(e->e.weight()>0).toList(),inventory.encode(player,reward),false,false);
            JsonObject intent=new JsonObject();intent.addProperty("openJar",id);intent.addProperty("definition",definition);intent.addProperty("hand",hand.name());intent.add("source",inventory.encode(player,before));
            boolean committed=inventory.transact(player,"jar/start/"+id,intent,()->{
                if(draw(player)!=null||!ItemStack.matches(before,player.getItemInHand(hand)))throw new IllegalArgumentException("手中罐子发生变化");
                player.getItemInHand(hand).shrink(1);
            },Map.of(key(player),JSON.toJsonTree(value)));
            if(committed){players.profile(player);state(player,value);}
        }catch(Exception failure){players.message(player,failure.getMessage());}
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
        var intent=new JsonObject();intent.addProperty("finishJar",value.id());
        boolean committed=inventory.transact(player,"jar/finish/"+value.id(),intent,()->{
            var current=draw(player);if(current==null||!current.id().equals(value.id())||current.delivered())throw new IllegalArgumentException("罐子结算状态发生变化");
            ItemStack remaining=inventory.decode(player,value.reward());
            if(ItemData.own(remaining)&&ItemData.id(remaining).equals(CurrencyRules.COLORLESS_CRYSTAL.id())){
                String balance=CurrencyRules.COLORLESS_CRYSTAL.id()+"/"+player.getUUID();changes.put(balance,new JsonPrimitive(Math.addExact(journal.get(balance,Long.class,0L),remaining.getCount())));remaining=ItemStack.EMPTY;
            }else InventoryPacking.insert(player,remaining);
            boolean dropped=!remaining.isEmpty();
            if(dropped){var drop=loot.manual(player,remaining,player.position());drops.add(drop);changes.put("loot/"+drop.id,JSON.toJsonTree(drop));}
            changes.put(key(player),interrupted?JsonNull.INSTANCE:JSON.toJsonTree(value.delivered(dropped)));
        },changes);
        if(committed){
            retryAfter.remove(player.getUUID());loot.publishDrops(drops);players.profile(player);
            send.accept(player,envelope(player,value.delivered(!drops.isEmpty()),interrupted?"interrupted":"complete"));
            players.message(player,(interrupted?"开罐已提前结算：":"开罐获得：")+inventory.decode(player,value.reward()).getHoverName().getString()+(drops.isEmpty()?"":"（背包已满，已生成公共掉落）"));
        }
    }
    /** Login is after inventory recovery. A surviving record denotes a crash, never a fresh draw. */
    public void recover(ServerPlayer player)throws IOException{finish(player,true);}
    public void interrupt(ServerPlayer player){
        try{finish(player,true);}catch(Exception failure){retryAfter.put(player.getUUID(),0L);players.message(player,"罐子原奖品已保留，结算失败："+failure.getMessage());}
    }
    public void tick(Collection<ServerPlayer> online){
        long now=System.currentTimeMillis();
        for(var player:online){
            var value=draw(player);if(value==null||value.delivered()||retryAfter.getOrDefault(player.getUUID(),0L)>now)continue;
            boolean interrupted=player.isDeadOrDying()||!player.level().dimension().identifier().toString().equals(value.dimension());
            if(!interrupted&&now-value.startedAt()<JarDefinitions.DURATION_MILLIS)continue;
            try{finish(player,interrupted);}catch(Exception failure){retryAfter.put(player.getUUID(),now+1000);players.message(player,"罐子结算暂未完成，将保留原奖品重试："+failure.getMessage());}
        }
    }
    public void register(CommandRouter<ServerPlayer,JsonObject> router){
        router.register("economy","jarStatus",(player,message)->{
            session(player,message);var value=draw(player);
            if(value==null)send.accept(player,envelope(player,null,"idle"));
            else if(!value.delivered()&&System.currentTimeMillis()-value.startedAt()>=JarDefinitions.DURATION_MILLIS)finish(player,false);
            else state(player,value);
        });
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
