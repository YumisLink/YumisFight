package cn.yumis.fight.modules.professions.platform;
import cn.yumis.fight.modules.presentation.api.ModText;
import cn.yumis.fight.modules.professions.api.HerbTier;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
/** 按稀有度染名的草药；与鱼同一套命名装饰规则。 */
public final class TieredHerb extends Item {
    private final HerbTier tier;
    public TieredHerb(Properties properties,HerbTier tier){
        super(properties);
        this.tier=tier;
    }
    @Override public Component getName(ItemStack stack){
        return ModText.decorate(super.getName(stack)).withStyle(style->style.withColor(tier.color()).withItalic(false));
    }
}
