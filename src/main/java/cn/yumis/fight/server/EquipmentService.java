package cn.yumis.fight.server;
import cn.yumis.fight.game.*;
import static cn.yumis.fight.rules.Models.JSON;
import com.google.gson.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import java.util.*;
public final class EquipmentService {
    private EquipmentService(){
    }
    public static Map<String,Integer> bonus(ServerRuntime runtime,ServerPlayer player){
        JsonElement value=runtime.journal.get("slotBonus/"+player.getUUID());
        Map<String,Integer> out=new HashMap<>();
        if(value.isJsonObject())for(var e:value.getAsJsonObject().entrySet())out.put(e.getKey(),e.getValue().getAsInt());
        return out;
    }
    public static void reconcile(ServerRuntime runtime,ServerPlayer player,Map<String,Integer> bonus){
        var extra=MenuHooks.extra(player);
        List<String> desired=EquipmentLayout.CURRENT.slots(bonus);
        if(extra.groups.equals(desired))return;
        Map<String,ArrayDeque<ItemStack>> saved=new HashMap<>();
        for(int i=0;i<extra.getContainerSize();i++)saved.computeIfAbsent(extra.groups.get(i),key->new ArrayDeque<>()).add(extra.getItem(i).copy());
        JsonObject intent=new JsonObject();
        intent.add("layout",JSON.toJsonTree(desired));
        intent.add("bonus",JSON.toJsonTree(bonus));
        Map<String,JsonElement> changes=new HashMap<>();
        changes.put("slotBonus/"+player.getUUID(),JSON.toJsonTree(bonus));
        runtime.inventory.transact(player,"layout/"+UUID.randomUUID(),intent,()->{
            extra.layout(desired);for(int i=0;i<desired.size();i++){
                ArrayDeque<ItemStack> group=saved.get(desired.get(i));if(group!=null&&!group.isEmpty())extra.setItem(i,group.removeFirst());
            }
            JsonElement old=runtime.journal.get("equipmentWaiting/"+player.getUUID());JsonArray waiting=old.isJsonArray()?old.getAsJsonArray():new JsonArray();for(var group:saved.values())for(ItemStack stack:group)if(!stack.isEmpty())waiting.add(runtime.inventory.encode(player,stack));changes.put("equipmentWaiting/"+player.getUUID(),waiting);
        }
        ,changes);
    }
    public static int change(ServerRuntime runtime,ServerPlayer player,String group,int count){
        EquipmentLayout.CURRENT.group(group);
        Map<String,Integer> bonus=bonus(runtime,player);
        int next=Math.addExact(bonus.getOrDefault(group,0),count);
        if(next<0)throw new IllegalArgumentException("不能减少数据包基础槽");
        bonus.put(group,next);
        EquipmentLayout.CURRENT.slots(bonus);
        player.closeContainer();
        reconcile(runtime,player,bonus);
        runtime.profile(player);
        return next;
    }
}
