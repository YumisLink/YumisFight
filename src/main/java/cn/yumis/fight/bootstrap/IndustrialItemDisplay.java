package cn.yumis.fight.bootstrap;

import cn.yumis.fight.modules.equipment.api.Rarity;
import cn.yumis.fight.modules.industry.api.IndustrialTechnology;
import cn.yumis.fight.modules.industry.platform.IndustrialContent;
import cn.yumis.fight.modules.presentation.api.ModText;
import cn.yumis.fight.platform.ItemData;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.ModifyDefaultComponentsEvent;

/** Shared display mapping for registered industrial items, never a gameplay rule. */
public final class IndustrialItemDisplay {
    private IndustrialItemDisplay(){}
    public static Rarity rarity(ItemStack stack){
        if(stack.isEmpty()||!ItemData.own(stack)||!IndustrialContent.ALL_ITEMS.containsKey(ItemData.id(stack)))return null;
        return rarity(IndustrialTechnology.itemTier(stack));
    }
    private static Rarity rarity(int tier){
        return tier>=1&&tier<=Rarity.values().length?Rarity.values()[tier-1]:null;
    }
    public static void names(ModifyDefaultComponentsEvent event){
        IndustrialContent.ALL_ITEMS.forEach((id,holder)->{
            // Default components are not bound yet; resolve the grade from the registered ID.
            var rarity=rarity(IndustrialTechnology.itemTier(IndustrialContent.id(id).toString()));
            if(rarity==null)return;
            int color=rarity.color();
            event.modify(holder.get(),(components,context,item)->components.set(DataComponents.ITEM_NAME,
                ModText.decorate(Component.translatable(item.getDescriptionId())).withStyle(style->style.withColor(color).withItalic(false))));
        });
    }
}
