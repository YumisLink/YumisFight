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
    private final Journal journal;
    private final InventoryOperations inventory;
    private final EconomyService economy;
    private final Function<ServerPlayer,UUID> session;
    private final Consumer<ServerPlayer> profile;
    public ShopService(Journal journal,InventoryOperations inventory,EconomyService economy,Function<ServerPlayer,UUID> session,Consumer<ServerPlayer> profile){this.journal=journal;this.inventory=inventory;this.economy=economy;this.session=session;this.profile=profile;}
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
        try{
            String operation=UUID.fromString(request.get("operation").getAsString()).toString();
            var prior=journal.get("inventoryReceipt/"+p.getUUID()+"/"+operation);
            if(!prior.isJsonNull()){
                if(!prior.getAsJsonObject().get("intent").equals(request))throw new IllegalArgumentException("操作号冲突");
                inventory.restore(p);profile.accept(p);bag(p);result(p,request,true,"购买已完成");return;
            }
            if(!session.apply(p).toString().equals(request.get("session").getAsString())||inventory.locked(p.getUUID()))throw new IllegalArgumentException("会话已失效或库存正在处理");
            var changes=new LinkedHashMap<>(economy.receiveBalances(p,request));
            boolean committed=inventory.transact(p,operation,request,()->{
                InventoryEdits.apply(p,inventory,request);
                for(var raw:request.getAsJsonArray("shopOutputs")){
                    var stack=inventory.decode(p,raw);InventoryPacking.insertRaw(p,stack);
                    if(!stack.isEmpty()&&p.drop(stack,false)==null)throw new IllegalStateException("掉落被取消，购买未扣费");
                }
            },changes);
            profile.accept(p);bag(p);result(p,request,committed,committed?"购买成功":"购买未完成，物品与费用已保留");
        }catch(RuntimeException failure){profile.accept(p);bag(p);result(p,request,false,failure.getMessage()==null?"购买未完成":failure.getMessage());}
    }
}
