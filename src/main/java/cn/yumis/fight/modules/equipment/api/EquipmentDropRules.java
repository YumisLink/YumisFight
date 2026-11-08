package cn.yumis.fight.modules.equipment.api;

import cn.yumis.fight.platform.ItemData;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Only protects weapons from the selected-slot drop key; inventory discards remain available. */
public final class EquipmentDropRules {
    private EquipmentDropRules(){}
    public static boolean blocked(Player player,ItemStack stack){
        return !player.isCreative()&&!stack.isEmpty()&&ItemData.own(stack)
            &&java.util.Arrays.stream(Weapon.values()).anyMatch(weapon->weapon.name().equalsIgnoreCase(ItemData.id(stack)));
    }
}
