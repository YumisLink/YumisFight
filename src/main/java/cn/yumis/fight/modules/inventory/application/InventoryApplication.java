package cn.yumis.fight.modules.inventory.application;
import cn.yumis.fight.modules.inventory.api.DimensionalMenu;

import cn.yumis.fight.modules.inventory.api.*;
import cn.yumis.fight.foundation.Journal;
import cn.yumis.fight.architecture.CommandRouter;
import cn.yumis.fight.modules.inventory.api.InventoryOperations;

import com.google.gson.*;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;
import java.util.function.BiConsumer;
import cn.yumis.fight.modules.inventory.api.InventoryPacking;
import net.minecraft.world.item.ItemStack;
/** inventory application entry. Dependencies are explicit ports, not the global runtime. */
public final class InventoryApplication {
    private final Journal journal;
    private final InventoryOperations inventory;
    private final BiConsumer<ServerPlayer,String> message;
    public InventoryApplication(Journal journal,InventoryOperations inventory,BiConsumer<ServerPlayer,String> message){this.journal=journal;this.inventory=inventory;this.message=message;}
    public void register(CommandRouter<ServerPlayer,JsonObject> router){
        router.register("inventory","bag",(p,j)->openBag(p));router.register("inventory","claim",(p,j)->claimWaiting(p));
        router.register("inventory","bagOption",(p,j)->saveBagOption(p,j));
        router.register("inventory","bagView",(p,j)->{if(p.containerMenu instanceof DimensionalMenu menu&&menu.containerId==j.get("menu").getAsInt()&&menu.stillValid(p)){List<Integer> indices=new ArrayList<>();for(var index:j.getAsJsonArray("indices"))indices.add(index.getAsInt());menu.filter(j.get("query").getAsString(),indices);menu.broadcastChanges();}});
    }

    /** 客户端上报的自动收纳偏好：按本人客户端判定保存，幂等覆盖。 */
    private void saveBagOption(ServerPlayer p,JsonObject request)throws java.io.IOException{
        journal.put("bagOption/"+p.getUUID(),BagOption.parse(request).encode());
    }

    public void openBag(ServerPlayer p){
        inventory.closeCursor(p);
        cn.yumis.fight.modules.inventory.api.DimensionalMenu.open(p);
    }
    public void claimWaiting(ServerPlayer p){
        JsonElement waiting=journal.get("equipmentWaiting/"+p.getUUID());
        if(waiting.isJsonNull())return;
        Map<String,JsonElement> changes=new HashMap<>();
        JsonObject intent=new JsonObject();
        intent.addProperty("claimWaiting",true);
        inventory.transact(p,UUID.randomUUID().toString(),intent,()->{
            JsonArray remaining=new JsonArray();for(JsonElement entry:waiting.getAsJsonArray()){
                ItemStack stack=inventory.decode(p,entry);InventoryPacking.insert(p,stack);if(!stack.isEmpty())remaining.add(inventory.encode(p,stack));
            }
            changes.put("equipmentWaiting/"+p.getUUID(),remaining);
        }
        ,changes);
        message.accept(p,"已领取可放入背包的暂存装备");
    }
}
