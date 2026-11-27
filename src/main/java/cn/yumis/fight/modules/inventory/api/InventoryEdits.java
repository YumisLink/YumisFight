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
    public static JsonObject debitCurrency(int slot,long before,long amount){
        if(amount<=0||amount>before)throw new IllegalArgumentException("通货袋数量不足");
        var row=new JsonObject();row.addProperty("area",CategoryBags.Kind.CURRENCY.area());row.addProperty("slot",slot);
        row.addProperty("beforeAmount",before);row.addProperty("afterAmount",before-amount);return row;
    }
    public static ItemStack source(Player player,String area,int slot){
        return switch(area){
            case "inventory"->player.getInventory().getItem(slot);
            case "bag"->MenuHooks.extra(player).bag.getItem(slot);
            case "equipment"->MenuHooks.extra(player).getItem(slot);
            case "cursor"->player.containerMenu.getCarried();
            case "menu"->player.containerMenu.getSlot(slot).getItem();
            default->{
                var kind=CategoryBags.Kind.area(area);
                if(kind==null||slot<0||slot>=CategoryBags.size(kind))throw new IllegalArgumentException("未知库存区域");
                var categories=MenuHooks.extra(player).categories;
                yield categories.unlocked(kind)?categories.container(kind).getItem(slot):ItemStack.EMPTY;
            }
        };
    }
    public static void apply(ServerPlayer player,InventoryOperations inventory,JsonObject request){
        var rows=request.has("inventoryChanges")?request.getAsJsonArray("inventoryChanges"):new JsonArray();
        if(!rows.isEmpty()&&request.has("menu")&&request.get("menu").getAsInt()!=player.containerMenu.containerId)throw new IllegalArgumentException("库存界面已变化");
        var seen=new HashSet<String>();
        for(var raw:rows){var row=raw.getAsJsonObject();String area=row.get("area").getAsString();int slot=row.get("slot").getAsInt();
            if(!seen.add(area+"/"+slot))throw new IllegalArgumentException("库存源重复");
            var kind=CategoryBags.Kind.area(area);
            var categories=MenuHooks.extra(player).categories;
            if(kind!=null&&(!categories.unlocked(kind)||slot<0||slot>=CategoryBags.size(kind)))throw new IllegalArgumentException("分类背包栏位不可用");
            if(kind==CategoryBags.Kind.CURRENCY){
                long before=row.get("beforeAmount").getAsLong(),after=row.get("afterAmount").getAsLong();
                if(before!=categories.quantity(slot)||after<0||after>=before)throw new IllegalArgumentException("通货袋数量已变化，请重试");
                continue;
            }
            if(area.equals("menu"))SlotAccess.requireWritable(player,player.containerMenu,slot);
            if(!inventory.encode(player,source(player,area,slot)).equals(row.get("before")))throw new IllegalArgumentException("物品已变化，请刷新后重试");
        }
        for(var raw:rows){var row=raw.getAsJsonObject();String area=row.get("area").getAsString();int slot=row.get("slot").getAsInt();
            var kind=CategoryBags.Kind.area(area);
            var categories=MenuHooks.extra(player).categories;
            if(kind==CategoryBags.Kind.CURRENCY){categories.quantity(slot,row.get("afterAmount").getAsLong());continue;}
            var stack=inventory.decode(player,row.get("after"));
            switch(area){case "inventory"->player.getInventory().setItem(slot,stack);case "bag"->MenuHooks.extra(player).bag.setItem(slot,stack);case "equipment"->MenuHooks.extra(player).setItem(slot,stack);case "cursor"->player.containerMenu.setCarried(stack);case "menu"->player.containerMenu.getSlot(slot).set(stack);default->{
                if(kind==null||!stack.isEmpty()&&CategoryBags.kind(stack)!=kind)throw new IllegalArgumentException("分类背包物品无效");
                categories.container(kind).setItem(slot,stack);
            }}
        }
        if(request.has("outputs"))for(var raw:request.getAsJsonArray("outputs"))inventory.requireSpace(player,inventory.decode(player,raw));
    }
}
