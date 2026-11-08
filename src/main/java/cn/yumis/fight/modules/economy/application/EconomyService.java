package cn.yumis.fight.modules.economy.application;

import cn.yumis.fight.modules.inventory.api.*;
import cn.yumis.fight.modules.equipment.api.*;
import cn.yumis.fight.modules.economy.api.CurrencyRules;
import cn.yumis.fight.foundation.Journal;
import cn.yumis.fight.architecture.CommandRouter;
import cn.yumis.fight.network.Network;
import com.google.gson.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import java.util.*;
import java.util.function.*;

/** Stores owner-authored account and inventory results; quoted snapshots are storage references. */
public final class EconomyService {
    /** Economy-owned assembly for large, frozen chest/jar results; never chooses their contents. */
    public static final class Drafts {
        private static final class Parts {String session,operation,hash;int count,next;StringBuilder text=new StringBuilder();}
        private final Map<UUID,Parts> incoming=new HashMap<>();
        public static String hash(String text){try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}}
        public void part(ServerPlayer player,JsonObject j){
            int index=j.get("part").getAsInt();if(index==0){var value=new Parts();value.session=j.get("session").getAsString();value.operation=j.get("operation").getAsString();value.hash=j.get("hash").getAsString();value.count=j.get("count").getAsInt();incoming.put(player.getUUID(),value);}
            var value=incoming.get(player.getUUID());if(value==null||index!=value.next||!value.session.equals(j.get("session").getAsString())||!value.operation.equals(j.get("operation").getAsString())||!value.hash.equals(j.get("hash").getAsString())||value.count!=j.get("count").getAsInt()||index>=value.count)throw new IllegalArgumentException("奖励结果分段已变化，请重试");value.text.append(j.get("text").getAsString());value.next++;
        }
        public JsonElement value(ServerPlayer player,JsonObject request,String field){
            if(request.has(field))return request.get(field);var value=incoming.get(player.getUUID());String expected=request.get(field+"Draft").getAsString();
            if(value==null||value.next!=value.count||!value.session.equals(request.get("session").getAsString())||!value.operation.equals(request.get("operation").getAsString())||!value.hash.equals(expected)||!hash(value.text.toString()).equals(expected))throw new IllegalArgumentException("奖励结果尚未传输完整，请重试");return JsonParser.parseString(value.text.toString());
        }
        public void forget(UUID player){incoming.remove(player);}
    }
    private final Journal journal;private final InventoryOperations inventory;private final Function<ServerPlayer,UUID> sessionId;private final Consumer<ServerPlayer> profile;private final EquipmentEntitlements entitlements;
    private record Quote(String session,String token,Map<String,JsonElement> items){}
    private final Map<UUID,Quote> quotes=new HashMap<>();
    public EconomyService(Journal journal,InventoryOperations inventory,Function<ServerPlayer,UUID> sessionId,Consumer<ServerPlayer> profile,EquipmentEntitlements entitlements){this.journal=journal;this.inventory=inventory;this.sessionId=sessionId;this.profile=profile;this.entitlements=entitlements;}
    public void register(CommandRouter<ServerPlayer,JsonObject> router){router.register("economy","sell",this::sell);router.register("economy","dismantle",this::dismantle);router.register("economy","saleOpen",this::saleOpen);router.register("economy","purchase",this::purchase);}
    public long gold(ServerPlayer p){return journal.get("gold/"+p.getUUID(),Long.class,0L);}
    public long goldRevision(ServerPlayer p){return journal.get("goldRevision/"+p.getUUID(),Long.class,0L);}
    public long points(ServerPlayer p){return journal.get(CurrencyRules.POINTS.id()+"/"+p.getUUID(),Long.class,0L);}
    public long colorlessCrystals(ServerPlayer p){return journal.get(CurrencyRules.COLORLESS_CRYSTAL.id()+"/"+p.getUUID(),Long.class,0L);}
    public long changePoints(ServerPlayer p,long amount){long balance=Math.addExact(points(p),amount);if(balance<0)throw new IllegalArgumentException("点券余额不足");var intent=new JsonObject();intent.addProperty("points",amount);inventory.transact(p,UUID.randomUUID().toString(),intent,()->{},Map.of(CurrencyRules.POINTS.id()+"/"+p.getUUID(),new JsonPrimitive(balance)));profile.accept(p);return balance;}
    public void goldGain(ServerPlayer p,long amount,long balance,long revision){Network.send(p,"goldGain",Map.of("session",sessionId.apply(p).toString(),"amount",amount,"balance",balance,"revision",revision));}
    private boolean replay(ServerPlayer p,JsonObject j){var old=journal.get("inventoryReceipt/"+p.getUUID()+"/"+j.get("operation").getAsString());if(old.isJsonNull())return false;if(!old.getAsJsonObject().get("intent").equals(j))throw new IllegalArgumentException("操作号冲突");inventory.restore(p);profile.accept(p);return true;}
    private void session(ServerPlayer p,JsonObject j){if(!j.has("session")||!sessionId.apply(p).toString().equals(j.get("session").getAsString())||inventory.locked(p.getUUID()))throw new IllegalArgumentException("会话已失效或库存正在处理");}
    private static String account(String name){return switch(name){case "gold","goldRevision"->name;case "points"->CurrencyRules.POINTS.id();case "colorlessCrystals"->CurrencyRules.COLORLESS_CRYSTAL.id();default->throw new IllegalArgumentException("未知账户");};}
    public Map<String,JsonElement> receiveBalances(ServerPlayer p,JsonObject j){
        var changes=new LinkedHashMap<String,JsonElement>();if(!j.has("balances"))return changes;
        var before=j.getAsJsonObject("beforeBalances");var after=j.getAsJsonObject("balances");if(before==null||!before.keySet().equals(after.keySet()))throw new IllegalArgumentException("账户快照不完整");
        for(var entry:after.entrySet()){String key=account(entry.getKey())+"/"+p.getUUID();if(journal.get(key,Long.class,0L)!=before.get(entry.getKey()).getAsLong())throw new IllegalArgumentException("账户余额已更新，请重试");changes.put(key,new JsonPrimitive(entry.getValue().getAsLong()));}return changes;
    }
    public void purchase(ServerPlayer p,JsonObject j){if(replay(p,j))return;session(p,j);var changes=new LinkedHashMap<>(receiveBalances(p,j));if(j.has("contractUntil"))changes.putAll(entitlements.storeContract(p.getUUID(),j.get("beforeContract").getAsLong(),j.get("contractUntil").getAsLong()));inventory.transact(p,j.get("operation").getAsString(),j,()->InventoryEdits.apply(p,inventory,j),changes);profile.accept(p);}
    public String saleOpen(ServerPlayer p,JsonObject j){
        session(p,j);if(p.containerMenu!=p.inventoryMenu)p.closeContainer();String token=UUID.randomUUID().toString();var snapshots=new LinkedHashMap<String,JsonElement>();var rows=new ArrayList<JsonObject>();
        for(int group=0;group<2;group++){boolean bag=group==1;Container container=bag?MenuHooks.extra(p).bag:p.getInventory();int limit=bag?MenuHooks.extra(p).bagLimit():36;
            for(int slot=0;slot<limit;slot++){var stack=container.getItem(slot);var row=new JsonObject();String key=(bag?"bag/":"inventory/")+slot;row.addProperty("key",key);row.addProperty("bag",bag);row.addProperty("slot",slot);row.addProperty("empty",stack.isEmpty());row.addProperty("name",stack.isEmpty()?"空槽":stack.getHoverName().getString());
                snapshots.put(key,inventory.encode(p,stack));if(EquipmentItems.view(stack)!=null){row.add("source",inventory.encode(p,stack));row.add("item",inventory.encode(p,stack));}
                else if(!stack.isEmpty()){var preview=new ItemStack(stack.getItem(),stack.getCount());preview.set(net.minecraft.core.component.DataComponents.ITEM_NAME,stack.getHoverName());row.add("item",inventory.encode(p,preview));}rows.add(row);
            }
        }
        quotes.put(p.getUUID(),new Quote(sessionId.apply(p).toString(),token,Map.copyOf(snapshots)));var chunk=new ArrayList<JsonObject>();int size=0,part=0;
        for(var row:rows){int length=row.toString().length();if(size+length>110000&&!chunk.isEmpty()){sendSale(p,token,part++,false,chunk);chunk=new ArrayList<>();size=0;}chunk.add(row);size+=length;}sendSale(p,token,part,true,chunk);return token;
    }
    private void sendSale(ServerPlayer p,String token,int part,boolean done,List<JsonObject> rows){Network.send(p,"saleState",Map.of("session",sessionId.apply(p).toString(),"token",token,"part",part,"done",done,"menu",p.inventoryMenu.containerId,"rows",rows));}
    public void sell(ServerPlayer p,JsonObject j){selection(p,j);}
    public void dismantle(ServerPlayer p,JsonObject j){selection(p,j);}
    private void selection(ServerPlayer p,JsonObject j){
        if(replay(p,j)){saleOpen(p,j);return;}session(p,j);var quote=quotes.get(p.getUUID());
        if(p.containerMenu!=p.inventoryMenu||j.get("menu").getAsInt()!=p.inventoryMenu.containerId||quote==null||!quote.session.equals(sessionId.apply(p).toString())||!quote.token.equals(j.get("token").getAsString()))throw new IllegalArgumentException("库存快照已失效，请刷新");
        try{
            for(var raw:j.getAsJsonArray("inventoryChanges")){var row=raw.getAsJsonObject();String key=row.get("area").getAsString()+"/"+row.get("slot").getAsInt();if(!Objects.equals(quote.items.get(key),row.get("before")))throw new IllegalArgumentException("源物品快照已变化");}
            var changes=receiveBalances(p,j);boolean committed=inventory.transact(p,j.get("operation").getAsString(),j,()->{InventoryEdits.apply(p,inventory,j);if(j.has("drops"))for(var raw:j.getAsJsonArray("drops"))if(p.drop(inventory.decode(p,raw),false)==null)throw new IllegalStateException("分解掉落被取消，装备已保留");},changes);
            profile.accept(p);if(committed&&j.has("goldGain"))goldGain(p,j.get("goldGain").getAsLong(),gold(p),goldRevision(p));saleOpen(p,j);
        }catch(RuntimeException failure){saleOpen(p,j);throw failure;}
    }
}
