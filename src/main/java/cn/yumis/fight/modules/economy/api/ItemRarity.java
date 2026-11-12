package cn.yumis.fight.modules.economy.api;
import cn.yumis.fight.modules.equipment.api.Rarity;
import cn.yumis.fight.modules.equipment.api.Gear;
import cn.yumis.fight.modules.equipment.api.*;
import net.minecraft.world.item.ItemStack;
import static cn.yumis.fight.platform.ItemData.*;
import static cn.yumis.fight.modules.equipment.api.EquipmentItems.gear;
public final class ItemRarity {private ItemRarity(){}
    private static java.util.function.Function<ItemStack,Rarity> extra=stack->null;
    public static void external(java.util.function.Function<ItemStack,Rarity> resolver){extra=java.util.Objects.requireNonNull(resolver);}
    public static boolean decorated(ItemStack stack){return !stack.isEmpty()&&(extra.apply(stack)!=null||ShopDefinitions.listed(stack)||own(stack)&&(EquipmentItems.view(stack)!=null||CurrencyRules.find(id(stack))!=null));}
    public static Rarity rarity(ItemStack stack){
        if(!stack.isEmpty()){var material=ShopDefinitions.materialRarity(stack);if(material!=null)return material;var external=extra.apply(stack);if(external!=null)return external;}
        if(stack.isEmpty()||!own(stack))return Rarity.COMMON;
        var gear=EquipmentItems.view(stack);
        if(gear!=null)return gear.rarity();
        var currency=CurrencyRules.find(id(stack));
        return currency==null?Rarity.COMMON:currency.rarity();
    }
}
