package cn.yumis.fight.modules.professions.platform;
import cn.yumis.fight.modules.presentation.api.ModText;
import cn.yumis.fight.modules.professions.api.PotionTier;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.ItemStack;
/** 按稀有度染名的药剂；效果先用占位时长，具体配方与效果后续补充。 */
public final class TieredElixir extends ProfessionConsumable {
    private final PotionTier tier;
    public TieredElixir(Properties properties,PotionTier tier,Holder<MobEffect> effect){
        super(properties,effect,tier.duration(),tier.amplifier());
        this.tier=tier;
    }
    @Override public Component getName(ItemStack stack){
        return ModText.decorate(super.getName(stack)).withStyle(style->style.withColor(tier.color()).withItalic(false));
    }
}
