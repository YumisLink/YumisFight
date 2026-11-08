package cn.yumis.fight.modules.inventory.api;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Owner-authored inventory changes. The receiver compares source facts, never gameplay rules. */
public final class InventoryEdits {
    private InventoryEdits(){}
    public static JsonElement encode(Player player,ItemStack stack){return ItemStack.OPTIONAL_CODEC.encodeStart(player.registryAccess().createSerializationContext(JsonOps.INSTANCE),stack).getOrThrow();}
    public static ItemStack decode(Player player,JsonElement value){return ItemStack.OPTIONAL_CODEC.parse(player.registryAccess().createSerializationContext(JsonOps.INSTANCE),value).getOrThrow();}
    public static JsonObject change(Player player,String area,int slot,ItemStack before,ItemStack after){var row=new JsonObject();row.addProperty("area",area);row.addProperty("slot",slot);row.add("before",encode(player,before));row.add("after",encode(player,after));return row;}
    public static ItemStack source(Player player,String area,int slot){
        return switch(area){
            case "inventory"->player.getInventory().getItem(slot);
            case "bag"->MenuHooks.extra(player).bag.getItem(slot);
            case "equipment"->MenuHooks.extra(player).getItem(slot);
            case "cursor"->player.containerMenu.getCarried();
            case "menu"->player.containerMenu.getSlot(slot).getItem();
            default->throw new IllegalArgumentException("未知库存区域");
        };
    }
    public static void apply(ServerPlayer player,InventoryOperations inventory,JsonObject request){
        var rows=request.has("inventoryChanges")?request.getAsJsonArray("inventoryChanges"):new JsonArray();
        if(!rows.isEmpty()&&request.has("menu")&&request.get("menu").getAsInt()!=player.containerMenu.containerId)throw new IllegalArgumentException("库存界面已变化");
        var seen=new HashSet<String>();
        for(var raw:rows){var row=raw.getAsJsonObject();String area=row.get("area").getAsString();int slot=row.get("slot").getAsInt();
            if(!seen.add(area+"/"+slot))throw new IllegalArgumentException("库存源重复");
            if(area.equals("menu"))SlotAccess.requireWritable(player,player.containerMenu,slot);
            if(!inventory.encode(player,source(player,area,slot)).equals(row.get("before")))throw new IllegalArgumentException("物品已变化，请刷新后重试");
        }
        for(var raw:rows){var row=raw.getAsJsonObject();String area=row.get("area").getAsString();int slot=row.get("slot").getAsInt();var stack=inventory.decode(player,row.get("after"));
            switch(area){case "inventory"->player.getInventory().setItem(slot,stack);case "bag"->MenuHooks.extra(player).bag.setItem(slot,stack);case "equipment"->MenuHooks.extra(player).setItem(slot,stack);case "cursor"->player.containerMenu.setCarried(stack);case "menu"->player.containerMenu.getSlot(slot).set(stack);default->throw new IllegalArgumentException("未知库存区域");}
        }
        if(request.has("outputs"))for(var raw:request.getAsJsonArray("outputs"))inventory.requireSpace(player,inventory.decode(player,raw));
    }
}
