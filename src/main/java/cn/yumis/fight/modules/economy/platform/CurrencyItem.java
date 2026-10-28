package cn.yumis.fight.modules.economy.platform;
import cn.yumis.fight.modules.presentation.api.ModText;
import cn.yumis.fight.modules.equipment.api.Rarity;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
/** Styles default and payload names alike, including stacks from existing saves. */
public final class CurrencyItem extends Item {
    private final Rarity rarity;
    public CurrencyItem(Properties properties,Rarity rarity){
        super(properties);
        this.rarity=rarity;
    }
    @Override public Component getName(ItemStack stack){
        return ModText.decorate(super.getName(stack)).withStyle(style->style.withColor(rarity.color()).withItalic(false));
    }
}
