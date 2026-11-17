package cn.yumis.fight.modules.professions.platform;
import cn.yumis.fight.modules.professions.api.HerbSpecies;
import cn.yumis.fight.modules.professions.api.PotionTier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import java.util.LinkedHashMap;
import java.util.Map;
/** 草药 → 药剂档位映射；后续药剂有自己的配方时，把这里换成数据驱动。 */
public final class AlchemyRecipes {
    private AlchemyRecipes(){}
    private static final Map<String,PotionTier> TIERS=new LinkedHashMap<>();
    static{
        register(PotionTier.COMMON,HerbSpecies.COMMON);
        register(PotionTier.RARE,HerbSpecies.RARE);
        register(PotionTier.ARTIFACT,HerbSpecies.ARTIFACT);
        register(PotionTier.LEGENDARY,HerbSpecies.LEGENDARY);
        register(PotionTier.EPIC,HerbSpecies.EPIC);
        register(PotionTier.MYTHIC,HerbSpecies.MYTHIC);
    }
    private static void register(PotionTier tier,java.util.List<String> herbs){
        for(String id:herbs)TIERS.put(id,tier);
    }
    /** 投入物品对应的药剂档位；不是草药返回 null。 */
    public static PotionTier tierOf(ItemStack stack){
        if(stack.isEmpty())return null;
        return TIERS.get(BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath());
    }
}
