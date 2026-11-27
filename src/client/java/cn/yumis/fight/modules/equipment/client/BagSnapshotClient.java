package cn.yumis.fight.modules.equipment.client;

import cn.yumis.fight.modules.equipment.api.EnhanceMenu;
import cn.yumis.fight.modules.equipment.api.FabricatorMenu;
import cn.yumis.fight.modules.equipment.api.HoradricCubeMenu;
import cn.yumis.fight.modules.inventory.api.CategoryBags;
import cn.yumis.fight.modules.inventory.api.MenuHooks;
import cn.yumis.fight.modules.inventory.api.InventoryEdits;
import cn.yumis.fight.modules.inventory.api.StorageRules;
import cn.yumis.fight.modules.presentation.api.client.ClientUi;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Read-only dimensional bag slot snapshot for EnhanceMenu and FabricatorMenu (mirrors ShopClient). */
public final class BagSnapshotClient {
    private static List<ItemStack> bag = List.of();
    private static int bagMenu = -1;
    private static int nextPart;
    private static boolean ready;
    private static JsonObject categoryItems;

    private BagSnapshotClient() {}

    public static void clear() {
        bag = List.of();
        bagMenu = -1;
        nextPart = 0;
        ready = false;
        categoryItems = null;
    }

    public static void receive(JsonObject value) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || !(mc.player.containerMenu instanceof EnhanceMenu || mc.player.containerMenu instanceof FabricatorMenu || mc.player.containerMenu instanceof HoradricCubeMenu)) return;
        if (!ClientUi.current().session().equals(value.get("session").getAsString())) return;
        int containerId = mc.player.containerMenu.containerId;
        if (value.get("menu").getAsInt() != containerId) return;
        int part = value.get("part").getAsInt();
        if (part == 0) {
            bagMenu = containerId;
            nextPart = 0;
            ready = false;
            bag = new ArrayList<>(Collections.nCopies(StorageRules.capacity(value.get("tier").getAsInt()), ItemStack.EMPTY));
            categoryItems=value.getAsJsonObject("categories").deepCopy();
            categoryItems.add(CategoryBags.Kind.GEMS.id,new JsonObject());
            categoryItems.add(CategoryBags.Kind.ESSENCES.id,new JsonObject());
        }
        if (bagMenu != containerId || part != nextPart++) {
            ready = false;
            return;
        }
        for (var raw : value.getAsJsonArray("rows")) {
            var row = raw.getAsJsonObject();
            int slot = row.get("slot").getAsInt();
            if(row.has("area")){
                categoryItems.getAsJsonObject(row.get("area").getAsString()).add(Integer.toString(slot),row.get("item"));
                continue;
            }
            if (slot < 0 || slot >= bag.size()) {
                ready = false;
                return;
            }
            bag.set(slot, InventoryEdits.decode(mc.player, row.get("item")));
        }
        ready = value.get("done").getAsBoolean();
        if(ready)MenuHooks.extra(mc.player).categories.load(mc.player,categoryItems);
    }

    public static List<ItemStack> items(AbstractContainerMenu menu) {
        return ready && menu != null && menu.containerId == bagMenu ? bag : List.of();
    }
}
