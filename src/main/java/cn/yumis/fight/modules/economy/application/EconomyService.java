package cn.yumis.fight.modules.economy.application;
import cn.yumis.fight.modules.inventory.api.*;
import cn.yumis.fight.modules.equipment.api.*;
import cn.yumis.fight.modules.economy.api.CurrencyRules;
import cn.yumis.fight.platform.ItemData;
import cn.yumis.fight.foundation.Journal;
import cn.yumis.fight.architecture.CommandRouter;
import cn.yumis.fight.network.Network;
import com.google.gson.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import java.util.*;
import java.util.function.*;
/** Purchases and complete batch sales share one inventory/WAL transaction. */
public final class EconomyService {
    private static final Gson JSON=new GsonBuilder().disableHtmlEscaping().create();
    public static final long CONTRACT_PRICE=100000,CONTRACT_DURATION=604800000;
    private final Journal journal;private final InventoryOperations inventory;private final Function<ServerPlayer,UUID> sessionId;private final Consumer<ServerPlayer> profile;private final EquipmentEntitlements entitlements;
    private record SaleItem(boolean bag,int slot,JsonElement snapshot,String equipment,int revision,long price){}
    private record Quote(String session,String token,Map<String,SaleItem> items){}
    private final Map<UUID,Quote> quotes=new HashMap<>();
    public EconomyService(Journal journal,InventoryOperations inventory,Function<ServerPlayer,UUID> sessionId,Consumer<ServerPlayer> profile,EquipmentEntitlements entitlements){this.journal=journal;this.inventory=inventory;this.sessionId=sessionId;this.profile=profile;this.entitlements=entitlements;}
    public void register(CommandRouter<ServerPlayer,JsonObject> router){router.register("economy","sell",this::sell);router.register("economy","saleOpen",this::saleOpen);router.register("economy","purchase",this::purchase);}
    public long gold(ServerPlayer p){return journal.get("gold/"+p.getUUID(),Long.class,0L);}
    public long goldRevision(ServerPlayer p){return journal.get("goldRevision/"+p.getUUID(),Long.class,0L);}
    public void goldGain(ServerPlayer p,long amount,long balance,long revision){Network.send(p,"goldGain",Map.of("session",sessionId.apply(p).toString(),"amount",amount,"balance",balance,"revision",revision));}
    private boolean replay(ServerPlayer p,JsonObject j){var old=journal.get("inventoryReceipt/"+p.getUUID()+"/"+j.get("operation").getAsString());if(old.isJsonNull())return false;if(!old.getAsJsonObject().get("intent").equals(j))throw new IllegalArgumentException("操作号冲突");inventory.restore(p);profile.accept(p);return true;}
    private void session(ServerPlayer p,JsonObject j){if(!j.has("session")||!sessionId.apply(p).toString().equals(j.get("session").getAsString())||inventory.locked(p.getUUID()))throw new IllegalArgumentException("会话已失效或库存正在处理");}
    public void purchase(ServerPlayer p,JsonObject j){
        if(replay(p,j))return;session(p,j);
        if(!j.has("product")||!j.get("product").getAsString().equals("overlord_contract"))throw new IllegalArgumentException("商品未上架");
        if(gold(p)<CONTRACT_PRICE)throw new IllegalArgumentException("金币不足，需要100000金币");
        Map<String,JsonElement> changes=new LinkedHashMap<>(entitlements.prepareContract(p.getUUID(),System.currentTimeMillis(),CONTRACT_DURATION));
        changes.put("gold/"+p.getUUID(),new JsonPrimitive(gold(p)-CONTRACT_PRICE));changes.put("goldRevision/"+p.getUUID(),new JsonPrimitive(Math.incrementExact(goldRevision(p))));
        inventory.transact(p,j.get("operation").getAsString(),j,()->{},changes);profile.accept(p);
    }
    public void saleOpen(ServerPlayer p,JsonObject j){
        session(p,j);if(p.containerMenu!=p.inventoryMenu)p.closeContainer();
        String token=UUID.randomUUID().toString();Map<String,SaleItem> items=new LinkedHashMap<>();List<JsonObject> rows=new ArrayList<>();
        for(int group=0;group<2;group++){boolean bag=group==1;Container container=bag?MenuHooks.extra(p).bag:p.getInventory();int limit=bag?MenuHooks.extra(p).bagLimit():36;
            for(int slot=0;slot<limit;slot++){
                ItemStack stack=container.getItem(slot);Gear g=EquipmentItems.gear(stack);JsonObject row=new JsonObject();String key=(bag?"bag/":"inventory/")+slot;
                row.addProperty("key",key);row.addProperty("bag",bag);row.addProperty("slot",slot);row.addProperty("empty",stack.isEmpty());
                boolean allowed=g!=null&&stack.getCount()==1&&ItemData.own(stack)&&(bag||slot!=p.getInventory().getSelectedSlot());row.addProperty("allowed",allowed);
                row.addProperty("reason",allowed?"":stack.isEmpty()?"空槽":!bag&&slot==p.getInventory().getSelectedSlot()?"当前主手不可出售":"不是可出售的装备");
                row.addProperty("name",stack.isEmpty()?"空槽":stack.getHoverName().getString());
                if(g==null&&!stack.isEmpty()){
                    // Only the visible item/name are needed; never transmit nested container contents.
                    ItemStack preview=new ItemStack(stack.getItem(),stack.getCount());
                    preview.set(net.minecraft.core.component.DataComponents.ITEM_NAME,stack.getHoverName());
                    row.add("item",inventory.encode(p,preview));
                }
                if(g!=null){
                    row.addProperty("equipment",g.instance.toString());row.addProperty("revision",g.revision);row.addProperty("price",CurrencyRules.salePrice(g));
                    Gear preview=EquipmentJson.copy(g,Gear.class);preview.baseGrowth.clear();preview.affixes.clear();preview.gems.clear();row.add("item",inventory.encode(p,EquipmentItems.gear(preview)));
                    JsonArray lines=new JsonArray();g.affixes.forEach(a->lines.add(cn.yumis.fight.modules.affixes.api.AffixText.affix(a)));row.add("lines",lines);
                    if(allowed)items.put(key,new SaleItem(bag,slot,inventory.encode(p,stack).deepCopy(),g.instance.toString(),g.revision,CurrencyRules.salePrice(g)));
                }
                rows.add(row);
            }
        }
        quotes.put(p.getUUID(),new Quote(sessionId.apply(p).toString(),token,Map.copyOf(items)));
        // Chunk business snapshots within the existing transport limit; never enlarge the transport protocol.
        List<JsonObject> chunk=new ArrayList<>();int size=0,part=0;
        for(JsonObject row:rows){int n=row.toString().length();if(size+n>110000&&!chunk.isEmpty()){sendSale(p,token,part++,false,chunk);chunk=new ArrayList<>();size=0;}chunk.add(row);size+=n;}
        sendSale(p,token,part,true,chunk);
    }
    private void sendSale(ServerPlayer p,String token,int part,boolean done,List<JsonObject> rows){Network.send(p,"saleState",Map.of("session",sessionId.apply(p).toString(),"token",token,"part",part,"done",done,"menu",p.inventoryMenu.containerId,"rows",rows));}
    public void sell(ServerPlayer p,JsonObject j){
        if(replay(p,j))return;session(p,j);Quote quote=quotes.get(p.getUUID());
        if(p.containerMenu!=p.inventoryMenu||j.get("menu").getAsInt()!=p.inventoryMenu.containerId||quote==null||!quote.session.equals(sessionId.apply(p).toString())||!quote.token.equals(j.get("token").getAsString()))throw new IllegalArgumentException("出售报价已失效，请刷新");
        try{
            JsonArray selected=j.getAsJsonArray("items");if(selected==null||selected.isEmpty()||selected.size()>452)throw new IllegalArgumentException("请选择有效装备");
            Set<String> keys=new HashSet<>();List<SaleItem> valid=new ArrayList<>();long total=0;
            for(JsonElement element:selected){String key=element.getAsString();if(!keys.add(key))throw new IllegalArgumentException("重复选择槽位");SaleItem row=quote.items.get(key);if(row==null)throw new IllegalArgumentException("装备不可出售");
                Container container=row.bag?MenuHooks.extra(p).bag:p.getInventory();ItemStack stack=container.getItem(row.slot);Gear gear=EquipmentItems.gear(stack);
                if(!row.bag&&row.slot==p.getInventory().getSelectedSlot()||gear==null||!gear.instance.toString().equals(row.equipment)||gear.revision!=row.revision||stack.getCount()!=1||!row.snapshot.equals(inventory.encode(p,stack))||CurrencyRules.salePrice(gear)!=row.price)throw new IllegalArgumentException("装备已发生变化，已刷新选择");
                total=Math.addExact(total,row.price);valid.add(row);
            }
            long income=total,balance=Math.addExact(gold(p),total),revision=Math.incrementExact(goldRevision(p));
            boolean committed=inventory.transact(p,j.get("operation").getAsString(),j,()->{for(SaleItem row:valid){Container c=row.bag?MenuHooks.extra(p).bag:p.getInventory();c.setItem(row.slot,ItemStack.EMPTY);}},Map.of("gold/"+p.getUUID(),new JsonPrimitive(balance),"goldRevision/"+p.getUUID(),new JsonPrimitive(revision)));
            profile.accept(p);if(committed)goldGain(p,income,balance,revision);saleOpen(p,j);
        }catch(RuntimeException ex){saleOpen(p,j);throw ex;}
    }
}
