package cn.yumis.fight.modules.economy.api;
import cn.yumis.fight.modules.equipment.api.Rarity;
import cn.yumis.fight.modules.equipment.api.Gear;
import cn.yumis.fight.modules.equipment.api.*;
import net.minecraft.world.item.ItemStack;
import static cn.yumis.fight.platform.ItemData.*;
import static cn.yumis.fight.modules.equipment.api.EquipmentItems.gear;
public final class ItemRarity {private ItemRarity(){}
    public static Rarity rarity(ItemStack stack){
        if(stack.isEmpty()||!own(stack))return Rarity.COMMON;
        var gear=EquipmentItems.view(stack);
        if(gear!=null)return gear.rarity();
        var currency=CurrencyRules.find(id(stack));
        return currency==null?Rarity.COMMON:currency.rarity();
    }
}
