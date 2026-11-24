package cn.yumis.fight.modules.inventory.api;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Menus whose output/reward slots support Shift+left-click quick intake into inventory receivers (e.g. mana bags). */
public interface QuickIntakeMenu {
    /** Returns true if Shift+clicking this slot index should route items into InventoryIntake receivers. */
    boolean allowsQuickIntake(int slotIndex);

    /** Optional custom extraction for menus with virtual/long-count slots; returns null to use default Slot.remove(). */
    default ItemStack extractForQuickIntake(ServerPlayer player, int slotIndex, int maxTake) {
        return null;
    }
}
