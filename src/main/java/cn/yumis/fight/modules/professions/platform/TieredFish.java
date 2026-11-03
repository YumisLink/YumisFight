package cn.yumis.fight.modules.professions.platform;
import cn.yumis.fight.modules.presentation.api.ModText;
import cn.yumis.fight.modules.professions.api.FishTier;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
/** 按稀有度染名的鱼；与通货同一套命名装饰规则。 */
public final class TieredFish extends Item {
    private final FishTier tier;
    public TieredFish(Properties properties,FishTier tier){
        super(properties);
        this.tier=tier;
    }
    @Override public Component getName(ItemStack stack){
        return ModText.decorate(super.getName(stack)).withStyle(style->style.withColor(tier.color()).withItalic(false));
    }
}
