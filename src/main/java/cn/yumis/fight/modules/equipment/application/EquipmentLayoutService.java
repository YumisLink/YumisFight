package cn.yumis.fight.modules.equipment.application;
import cn.yumis.fight.modules.equipment.api.EquipmentLayout;
import cn.yumis.fight.modules.inventory.api.MenuHooks;

import static cn.yumis.fight.modules.equipment.api.EquipmentJson.JSON;
import com.google.gson.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import java.util.*;
public final class EquipmentLayoutService {
    private final cn.yumis.fight.foundation.Journal journal;
    private final cn.yumis.fight.modules.inventory.api.InventoryOperations inventory;
    private final java.util.function.Consumer<ServerPlayer> profile;
    public EquipmentLayoutService(cn.yumis.fight.foundation.Journal journal,cn.yumis.fight.modules.inventory.api.InventoryOperations inventory,java.util.function.Consumer<ServerPlayer> profile){this.journal=journal;this.inventory=inventory;this.profile=profile;}

    public Map<String,Integer> bonus(ServerPlayer player){
        JsonElement value=journal.get("slotBonus/"+player.getUUID());
        Map<String,Integer> out=new HashMap<>();
        if(value.isJsonObject())for(var e:value.getAsJsonObject().entrySet())out.put(e.getKey(),e.getValue().getAsInt());
        return out;
    }
    public void reconcile(ServerPlayer player,Map<String,Integer> bonus){
        var extra=MenuHooks.extra(player);
        List<String> desired=EquipmentLayout.current().slots(bonus);
        if(extra.groups.equals(desired))return;
        Map<String,ArrayDeque<ItemStack>> saved=new HashMap<>();
        for(int i=0;i<extra.getContainerSize();i++)saved.computeIfAbsent(extra.groups.get(i),key->new ArrayDeque<>()).add(extra.getItem(i).copy());
        JsonObject intent=new JsonObject();
        intent.add("layout",JSON.toJsonTree(desired));
        intent.add("bonus",JSON.toJsonTree(bonus));
        Map<String,JsonElement> changes=new HashMap<>();
        changes.put("slotBonus/"+player.getUUID(),JSON.toJsonTree(bonus));
        inventory.transact(player,"layout/"+UUID.randomUUID(),intent,()->{
            extra.clearContent();extra.layout(desired);for(int i=0;i<desired.size();i++){
                ArrayDeque<ItemStack> group=saved.get(desired.get(i));if(group!=null&&!group.isEmpty())extra.setItem(i,group.removeFirst());
            }
            JsonElement old=journal.get("equipmentWaiting/"+player.getUUID());JsonArray waiting=old.isJsonArray()?old.getAsJsonArray():new JsonArray();for(var group:saved.values())for(ItemStack stack:group)if(!stack.isEmpty())waiting.add(inventory.encode(player,stack));changes.put("equipmentWaiting/"+player.getUUID(),waiting);
        }
        ,changes);
    }
    public int change(ServerPlayer player,String group,int count){
        EquipmentLayout.current().group(group);
        Map<String,Integer> bonus=bonus(player);
        int next=Math.addExact(bonus.getOrDefault(group,0),count);
        if(next<0)throw new IllegalArgumentException("不能减少数据包基础槽");
        bonus.put(group,next);
        EquipmentLayout.current().slots(bonus);
        player.closeContainer();
        reconcile(player,bonus);
        profile.accept(player);
        return next;
    }
}
