package cn.yumis.fight.modules.inventory.api;

import net.minecraft.world.entity.player.Player;

/** Keeps the dimensional bag and a transaction view of Curios-equipped gear. */
public final class MenuHooks {
    private MenuHooks(){}
    public static ExtraInventory extra(Player player){return player.getData(cn.yumis.fight.modules.inventory.api.InventoryContentTypes.EXTRA);}
}
