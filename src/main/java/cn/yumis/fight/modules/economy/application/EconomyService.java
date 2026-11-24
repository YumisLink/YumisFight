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
import java.util.*;
import java.util.function.*;

/** Stores owner-authored account and inventory results; quoted snapshots are storage references. */
public final class EconomyService {
    /** Reassembles large, frozen fields of the player's current personal operation. */
    public static final class Drafts {
        private static final class Parts {
            final String hash;
            final int count;
            final StringBuilder text=new StringBuilder();
            int next;
            Parts(String hash,int count){this.hash=hash;this.count=count;}
        }
        private record Draft(String session,String operation,Map<String,Parts> fields){
            boolean matches(JsonObject packet){
                return session.equals(packet.get("session").getAsString())&&operation.equals(packet.get("operation").getAsString());
            }
        }
        private final Map<UUID,Draft> incoming=new HashMap<>();
        public static String hash(String text){try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}}
        public void part(ServerPlayer player,JsonObject j){
            int index=j.get("part").getAsInt(),count=j.get("count").getAsInt();
            if(index<0||index>=count)throw new IllegalArgumentException("奖励结果分段已变化，请重试");
            String field=j.has("field")?j.get("field").getAsString():"";
            String hash=j.get("hash").getAsString();
            UUID id=player.getUUID();
            var draft=incoming.get(id);
            if(index==0){
                if(draft==null||!draft.matches(j)){
                    draft=new Draft(j.get("session").getAsString(),j.get("operation").getAsString(),new HashMap<>());
                    incoming.put(id,draft);
                }
                draft.fields().put(field,new Parts(hash,count));
            }
            var value=draft!=null&&draft.matches(j)?draft.fields().get(field):null;
            if(value==null||index!=value.next||!value.hash.equals(hash)||value.count!=count)throw new IllegalArgumentException("奖励结果分段已变化，请重试");
            value.text.append(j.get("text").getAsString());
            value.next++;
        }
        public JsonElement value(ServerPlayer player,JsonObject request,String field){
            if(request.has(field))return request.get(field);
            var draft=incoming.get(player.getUUID());
            var expected=request.get(field+"Draft");
            var value=draft!=null&&draft.matches(request)?draft.fields().getOrDefault(field,draft.fields().get("")):null;
            if(value==null||expected==null||value.next!=value.count||!value.hash.equals(expected.getAsString()))throw new IllegalArgumentException("奖励结果尚未传输完整，请重试");
            String text=value.text.toString();
            if(!hash(text).equals(value.hash))throw new IllegalArgumentException("奖励结果尚未传输完整，请重试");
            return JsonParser.parseString(text);
        }
        public void forget(UUID player){incoming.remove(player);}
    }
    private static final Gson JSON=new Gson();
    private final Journal journal;private final InventoryOperations inventory;private final Function<ServerPlayer,UUID> sessionId;private final Consumer<ServerPlayer> profile;private final EquipmentEntitlements entitlements;private final Drafts drafts;private final LootService loot;
    private record Quote(String session,String token,Map<String,JsonElement> items){}
    private final Map<UUID,Quote> quotes=new HashMap<>();
    private final Map<UUID,Quote> automaticQuotes=new HashMap<>();
    public EconomyService(Journal journal,InventoryOperations inventory,Function<ServerPlayer,UUID> sessionId,Consumer<ServerPlayer> profile,EquipmentEntitlements entitlements,Drafts drafts,LootService loot){this.journal=journal;this.inventory=inventory;this.sessionId=sessionId;this.profile=profile;this.entitlements=entitlements;this.drafts=drafts;this.loot=loot;}
    public void register(CommandRouter<ServerPlayer,JsonObject> router){router.register("economy","sell",this::sell);router.register("economy","dismantle",this::dismantle);router.register("economy","saleOpen",this::saleOpen);router.register("economy","purchase",this::purchase);}
    public void forget(UUID player){quotes.remove(player);automaticQuotes.remove(player);}
    public long gold(ServerPlayer p){return journal.get("gold/"+p.getUUID(),Long.class,0L);}
    public long goldRevision(ServerPlayer p){return journal.get("goldRevision/"+p.getUUID(),Long.class,0L);}
    public long points(ServerPlayer p){return journal.get(CurrencyRules.POINTS.id()+"/"+p.getUUID(),Long.class,0L);}
    public long colorlessCrystals(ServerPlayer p){return journal.get(CurrencyRules.COLORLESS_CRYSTAL.id()+"/"+p.getUUID(),Long.class,0L);}
    public long changePoints(ServerPlayer p,long amount){long balance=Math.addExact(points(p),amount);if(balance<0)throw new IllegalArgumentException("点券余额不足");var intent=new JsonObject();intent.addProperty("points",amount);inventory.transact(p,UUID.randomUUID().toString(),intent,()->{},Map.of(CurrencyRules.POINTS.id()+"/"+p.getUUID(),new JsonPrimitive(balance)));profile.accept(p);return balance;}
    public long changeGold(ServerPlayer p,long amount){long balance=Math.max(0,Math.addExact(gold(p),amount));var intent=new JsonObject();intent.addProperty("gold",amount);inventory.transact(p,UUID.randomUUID().toString(),intent,()->{},Map.of("gold/"+p.getUUID(),new JsonPrimitive(balance),"goldRevision/"+p.getUUID(),new JsonPrimitive(Math.incrementExact(goldRevision(p)))));profile.accept(p);return balance;}
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
    private static boolean automatic(JsonObject request){return request.has("automatic")&&request.get("automatic").getAsBoolean();}
    public String saleOpen(ServerPlayer p,JsonObject j){
        session(p,j);
        boolean automatic=automatic(j);
        if(p.containerMenu!=p.inventoryMenu){if(automatic)return "";p.closeContainer();}
        String token=UUID.randomUUID().toString();
        String request=automatic?j.get("operation").getAsString():null;
        var snapshots=new LinkedHashMap<String,JsonElement>();var rows=new ArrayList<JsonObject>();
        for(int group=0;group<2;group++){boolean bag=group==1;Container container=bag?MenuHooks.extra(p).bag:p.getInventory();int limit=bag?MenuHooks.extra(p).bagLimit():36;
            for(int slot=0;slot<limit;slot++){var stack=container.getItem(slot);var row=new JsonObject();String key=(bag?"bag/":"inventory/")+slot;row.addProperty("key",key);row.addProperty("bag",bag);row.addProperty("slot",slot);row.addProperty("empty",stack.isEmpty());row.addProperty("name",stack.isEmpty()?"空槽":stack.getHoverName().getString());
                var source=inventory.encode(p,stack);snapshots.put(key,source);
                if(!stack.isEmpty())row.add("source",source);
                rows.add(row);
            }
        }
        (automatic?automaticQuotes:quotes).put(p.getUUID(),new Quote(sessionId.apply(p).toString(),token,Map.copyOf(snapshots)));var parts=new ArrayList<JsonObject>();
        for(var row:rows){String value=row.toString();if(value.length()<=64000){parts.add(row);continue;}int index=0;
            for(int from=0;from<value.length();){int to=Math.min(value.length(),from+16000);if(to<value.length()&&Character.isHighSurrogate(value.charAt(to-1)))to--;var fragment=new JsonObject();fragment.addProperty("fragment",value.substring(from,to));fragment.addProperty("index",index++);fragment.addProperty("last",to==value.length());parts.add(fragment);from=to;}}
        var chunk=new ArrayList<JsonObject>();int size=0,part=0;
        for(var row:parts){int length=row.toString().length();if(size+length>110000&&!chunk.isEmpty()){sendSale(p,token,request,part++,false,chunk);chunk=new ArrayList<>();size=0;}chunk.add(row);size+=length;}sendSale(p,token,request,part,true,chunk);return token;
    }
    private void sendSale(ServerPlayer p,String token,String request,int part,boolean done,List<JsonObject> rows){
        var state=new JsonObject();
        state.addProperty("session",sessionId.apply(p).toString());
        state.addProperty("token",token);
        state.addProperty("part",part);
        state.addProperty("done",done);
        state.addProperty("menu",p.inventoryMenu.containerId);
        if(request!=null)state.addProperty("request",request);
        var values=new JsonArray();
        rows.forEach(values::add);
        state.add("rows",values);
        Network.send(p,"saleState",state);
    }
    public void sell(ServerPlayer p,JsonObject j){selection(p,j);}
    public void dismantle(ServerPlayer p,JsonObject j){selection(p,j);}
    private void selection(ServerPlayer p,JsonObject j){
        boolean automatic=automatic(j);
        if(replay(p,j)){if(!automatic)saleOpen(p,j);return;}
        session(p,j);var quote=(automatic?automaticQuotes:quotes).get(p.getUUID());
        if(p.containerMenu!=p.inventoryMenu||j.get("menu").getAsInt()!=p.inventoryMenu.containerId||quote==null||!quote.session.equals(sessionId.apply(p).toString())||!quote.token.equals(j.get("token").getAsString()))throw new IllegalArgumentException("库存快照已失效，请刷新");
        try{
            var edits=drafts.value(p,j,"inventoryChanges").getAsJsonArray();var mutation=j.deepCopy();mutation.add("inventoryChanges",edits);
            for(var raw:edits){var row=raw.getAsJsonObject();String key=row.get("area").getAsString()+"/"+row.get("slot").getAsInt();if(!Objects.equals(quote.items.get(key),row.get("before")))throw new IllegalArgumentException("源物品快照已变化");}
            var changes=new LinkedHashMap<>(receiveBalances(p,j));
            var overflowDrops=new ArrayList<LootService.Drop>();
            boolean committed=inventory.transact(p,j.get("operation").getAsString(),j,()->{
                InventoryEdits.apply(p,inventory,mutation);
                if(j.has("drops"))for(var raw:j.getAsJsonArray("drops")){
                    var stack=inventory.decode(p,raw);
                    InventoryPacking.insertWithOption(inventory.bagOption(p),p,stack);
                    if(!stack.isEmpty()){
                        var drop=loot.prepareOverflow(p,stack);
                        overflowDrops.add(drop);
                        changes.put("loot/"+drop.id,JSON.toJsonTree(drop));
                    }
                }
            },changes);
            if(committed&&!overflowDrops.isEmpty())loot.publishDrops(overflowDrops);
            profile.accept(p);if(committed&&j.has("goldGain"))goldGain(p,j.get("goldGain").getAsLong(),gold(p),goldRevision(p));if(!automatic)saleOpen(p,j);
        }catch(RuntimeException failure){if(!automatic)saleOpen(p,j);throw failure;}
    }
}
