package cn.yumis.fight.modules.economy.application;

import cn.yumis.fight.architecture.CommandRouter;
import cn.yumis.fight.foundation.Journal;
import cn.yumis.fight.modules.economy.api.ShopMenu;
import cn.yumis.fight.modules.inventory.api.*;
import cn.yumis.fight.network.Network;
import com.google.gson.*;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;
import java.util.function.*;

/** Receives fixed owner-authored costs/results and enlists ordinary overflow drops in the same WAL commit. */
public final class ShopService {
    private static final Gson JSON=new Gson();
    private final Journal journal;
    private final InventoryOperations inventory;
    private final EconomyService economy;
    private final EconomyService.Drafts drafts;
    private final LootService loot;
    private final Function<ServerPlayer,UUID> session;
    private final Consumer<ServerPlayer> profile;
    public ShopService(Journal journal,InventoryOperations inventory,EconomyService economy,EconomyService.Drafts drafts,LootService loot,Function<ServerPlayer,UUID> session,Consumer<ServerPlayer> profile){this.journal=journal;this.inventory=inventory;this.economy=economy;this.drafts=drafts;this.loot=loot;this.session=session;this.profile=profile;}
    public void register(CommandRouter<ServerPlayer,JsonObject> router){router.register("economy","shopPurchase",this::purchase);router.register("economy","shopRefresh",(p,j)->{if(p.containerMenu instanceof ShopMenu)bag(p);});}
    public void open(ServerPlayer player,UUID host,String shop){ShopMenu.open(player,host,shop);bag(player);}
    private void bag(ServerPlayer p){
        if(!(p.containerMenu instanceof ShopMenu menu))return;
        var extra=MenuHooks.extra(p);var rows=new JsonArray();int size=0,part=0;
        for(int slot=0;slot<extra.bagLimit();slot++){
            var row=new JsonObject();row.addProperty("slot",slot);row.add("item",inventory.encode(p,extra.bag.getItem(slot)));int length=row.toString().length();
            if(size+length>90000&&!rows.isEmpty()){sendBag(p,menu,extra.bagTier,part++,false,rows);rows=new JsonArray();size=0;}
            rows.add(row);size+=length;
        }
        sendBag(p,menu,extra.bagTier,part,true,rows);
    }
    private void sendBag(ServerPlayer p,ShopMenu menu,int tier,int part,boolean done,JsonArray rows){
        var value=new JsonObject();value.addProperty("session",session.apply(p).toString());value.addProperty("menu",menu.containerId);value.addProperty("tier",tier);value.addProperty("part",part);value.addProperty("done",done);value.add("rows",rows);Network.send(p,"shopBag",value);
    }
    private void result(ServerPlayer p,JsonObject request,boolean success,String message){
        Network.send(p,"shopResult",Map.of("session",session.apply(p).toString(),"operation",request.get("operation").getAsString(),"success",success,"message",message));
    }
    public void purchase(ServerPlayer p,JsonObject request){
        String operation=UUID.fromString(request.get("operation").getAsString()).toString();
        var prior=journal.get("inventoryReceipt/"+p.getUUID()+"/"+operation);
        if(!prior.isJsonNull()){
            if(!prior.getAsJsonObject().get("intent").equals(request))throw new IllegalArgumentException("操作号冲突");
            inventory.restore(p);profile.accept(p);bag(p);result(p,request,true,"购买已完成");return;
        }
        try{
            if(!session.apply(p).toString().equals(request.get("session").getAsString())||inventory.locked(p.getUUID()))throw new IllegalArgumentException("会话已失效或库存正在处理");
            var changes=new LinkedHashMap<>(economy.receiveBalances(p,request));
            var mutation=request.deepCopy();
            mutation.add("inventoryChanges",drafts.value(p,request,"inventoryChanges"));
            var outputs=drafts.value(p,request,"shopOutputs").getAsJsonArray();
            var overflowDrops=new ArrayList<LootService.Drop>();
            boolean committed=inventory.transact(p,operation,request,()->{
                InventoryEdits.apply(p,inventory,mutation);
                for(var raw:outputs){
                    var stack=inventory.decode(p,raw);
                    InventoryPacking.insertWithOption(inventory.bagOption(p),p,stack);
                    if(!stack.isEmpty()){
                        var drop=loot.prepareOverflow(p,stack);
                        overflowDrops.add(drop);
                        changes.put("loot/"+drop.id,JSON.toJsonTree(drop));
                    }
                }
            },changes);
            if(!committed)throw new IllegalStateException("购买未完成，物品与费用已保留");
            if(!overflowDrops.isEmpty())loot.publishDrops(overflowDrops);
        }catch(RuntimeException failure){
            result(p,request,false,failure.getMessage()==null?"购买未完成":failure.getMessage());
            bag(p);
            throw failure;
        }
        profile.accept(p);bag(p);result(p,request,true,"购买成功");
    }
}
