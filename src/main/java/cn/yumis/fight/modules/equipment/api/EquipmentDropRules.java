package cn.yumis.fight.modules.equipment.api;

import cn.yumis.fight.platform.ItemData;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Manual discard only; death, rewards and transactional processing do not use this rule. */
public final class EquipmentDropRules {
    private EquipmentDropRules(){}
    public static boolean blocked(Player player,ItemStack stack){
        return !player.isCreative()&&!stack.isEmpty()&&ItemData.own(stack)&&EquipmentItems.GEAR_IDS.contains(ItemData.id(stack));
    }
}
