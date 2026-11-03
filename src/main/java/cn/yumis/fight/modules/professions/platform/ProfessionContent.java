package cn.yumis.fight.modules.professions.platform;
import cn.yumis.fight.modules.professions.api.FishTier;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.Item;
import java.util.List;
import static cn.yumis.fight.platform.ItemRegistry.*;
/** 生活副职业线的材料、渔获、消耗品与鱼竿；贴图复用原版模型，不新增美术资源。 */
public final class ProfessionContent {
    private ProfessionContent(){}
    /** 纯材料物品；顺序即创造栏与页面顺序。 */
    public static final List<String> MATERIAL_IDS=List.of(
        "herb_sunleaf","herb_moonroot",
        "catch_riverfish","catch_pearl",
        "rune_blank","rune_ember",
        "ore_raw_chunk","ore_deep_shard",
        "hide_tanned","hide_reinforced",
        "ingot_billet","ingot_alloy",
        "part_gear","part_crystal_lens"
    );
    /** 菜品与药剂；效果时长单位是游戏刻。 */
    public static final List<String> CONSUMABLE_IDS=List.of(
        "dish_herb_soup","dish_grilled_fish",
        "elixir_minor_heal","elixir_deep_water"
    );
    /** 普通级鱼竿与原版一致：3 木棍 + 2 线、耐久 64；原版鱼竿本身同样可用。 */
    public static final int ROD_DURABILITY=64;
    public static final String ROD_ID="fisher_rod";
    /** 稀有级鱼竿"好运钓鱼竿"：一次钓两条，耐久 128。 */
    public static final int LUCKY_ROD_DURABILITY=128;
    public static final String LUCKY_ROD_ID="lucky_rod";
    /** 神器级鱼竿"精致的石墨鱼竿"：一次三条，可岩浆垂钓，耐久 256。 */
    public static final int GRAPHITE_ROD_DURABILITY=256;
    public static final String GRAPHITE_ROD_ID="graphite_rod";
    /** 传说级鱼竿"海潮嗜鱼竿"：一次四条、岩浆垂钓、钻石与黑曜石，耐久 512。 */
    public static final int TIDAL_ROD_DURABILITY=512;
    public static final String TIDAL_ROD_ID="tidal_rod";
    /** 史诗级鱼竿"冥河幽光鱼竿"：一次五条、岩浆垂钓、钻石/黑曜石/烈焰棒，耐久 1024。 */
    public static final int STYX_ROD_DURABILITY=1024;
    public static final String STYX_ROD_ID="styx_rod";
    /** 神话级鱼竿"远行者鱼友竿"：一次六条、岩浆与虚空垂钓、钻石/黑曜石/烈焰棒/下界之星，耐久 2048。 */
    public static final int FARER_ROD_DURABILITY=2048;
    public static final String FARER_ROD_ID="farer_rod";
    public static void register(){
        for(String id:MATERIAL_IDS)ALL.put(id,ITEMS.registerItem(id,Item::new));
        for(String id:cn.yumis.fight.modules.professions.api.FishSpecies.COMMON)ALL.put(id,ITEMS.registerItem(id,properties->new TieredFish(properties,FishTier.COMMON)));
        for(String id:cn.yumis.fight.modules.professions.api.FishSpecies.RARE)ALL.put(id,ITEMS.registerItem(id,properties->new TieredFish(properties,FishTier.RARE)));
        for(String id:cn.yumis.fight.modules.professions.api.FishSpecies.ARTIFACT_ALL)ALL.put(id,ITEMS.registerItem(id,properties->new TieredFish(properties,FishTier.ARTIFACT)));
        for(String id:cn.yumis.fight.modules.professions.api.FishSpecies.LEGENDARY_ALL)ALL.put(id,ITEMS.registerItem(id,properties->new TieredFish(properties,FishTier.LEGENDARY)));
        for(String id:cn.yumis.fight.modules.professions.api.FishSpecies.EPIC_ALL)ALL.put(id,ITEMS.registerItem(id,properties->new TieredFish(properties,FishTier.EPIC)));
        for(String id:cn.yumis.fight.modules.professions.api.FishSpecies.MYTHIC_ALL)ALL.put(id,ITEMS.registerItem(id,properties->new TieredFish(properties,FishTier.MYTHIC)));
        for(FishTier tier:FishTier.values())if(tier!=FishTier.COMMON&&tier!=FishTier.RARE&&tier!=FishTier.ARTIFACT&&tier!=FishTier.LEGENDARY&&tier!=FishTier.EPIC&&tier!=FishTier.MYTHIC)ALL.put(tier.itemId(),ITEMS.registerItem(tier.itemId(),properties->new TieredFish(properties,tier)));
        ALL.put(ROD_ID,ITEMS.registerItem(ROD_ID,properties->new FishingRodItem(properties.durability(ROD_DURABILITY))));
        ALL.put(LUCKY_ROD_ID,ITEMS.registerItem(LUCKY_ROD_ID,properties->new FishingRodItem(properties.durability(LUCKY_ROD_DURABILITY))));
        ALL.put(GRAPHITE_ROD_ID,ITEMS.registerItem(GRAPHITE_ROD_ID,properties->new GraphiteRod(properties.durability(GRAPHITE_ROD_DURABILITY))));
        ALL.put(TIDAL_ROD_ID,ITEMS.registerItem(TIDAL_ROD_ID,properties->new TidalRod(properties.durability(TIDAL_ROD_DURABILITY))));
        ALL.put(STYX_ROD_ID,ITEMS.registerItem(STYX_ROD_ID,properties->new StyxRod(properties.durability(STYX_ROD_DURABILITY))));
        ALL.put(FARER_ROD_ID,ITEMS.registerItem(FARER_ROD_ID,properties->new FarerRod(properties.durability(FARER_ROD_DURABILITY))));
        ALL.put("dish_herb_soup",ITEMS.registerItem("dish_herb_soup",p->new ProfessionConsumable(p,MobEffects.REGENERATION,100,0)));
        ALL.put("dish_grilled_fish",ITEMS.registerItem("dish_grilled_fish",p->new ProfessionConsumable(p,MobEffects.SATURATION,1,0)));
        ALL.put("elixir_minor_heal",ITEMS.registerItem("elixir_minor_heal",p->new ProfessionConsumable(p,MobEffects.REGENERATION,120,1)));
        ALL.put("elixir_deep_water",ITEMS.registerItem("elixir_deep_water",p->new ProfessionConsumable(p,MobEffects.WATER_BREATHING,3600,0)));
    }
}
