package cn.yumis.fight.modules.professions.platform;
import cn.yumis.fight.platform.ItemData;
import cn.yumis.fight.platform.ItemRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
/** 野外草药丛、噩梦藤与相关世界生成要素的注册。 */
public final class HerbContent {
    private HerbContent(){}
    public static final DeferredRegister.Blocks BLOCKS=DeferredRegister.createBlocks(ItemData.NS);
    public static final DeferredRegister<Feature<?>> FEATURES=DeferredRegister.create(Registries.FEATURE,ItemData.NS);
    public static final DeferredBlock<? extends Block> HERB_BUSH=BLOCKS.registerBlock("herb_bush",HerbBushBlock::new,
        p->p.noCollision().instabreak().sound(SoundType.GRASS).randomTicks().noOcclusion());
    public static final DeferredBlock<? extends Block> NIGHTMARE_VINE=BLOCKS.registerBlock("nightmare_vine",NightmareVineBlock::new,
        p->p.noCollision().instabreak().sound(SoundType.GRASS).randomTicks().noOcclusion());
    public static final DeferredBlock<? extends Block> OCEAN_HERB=BLOCKS.registerBlock("ocean_herb",OceanHerbBlock::new,
        p->p.noCollision().instabreak().sound(SoundType.WET_GRASS).randomTicks().noOcclusion());
    public static final DeferredBlock<? extends Block> STORM_VINE=BLOCKS.registerBlock("storm_vine",StormVineBlock::new,
        p->p.noCollision().instabreak().sound(SoundType.GRASS).randomTicks().noOcclusion());
    public static final DeferredHolder<Feature<?>,NightmareVineFeature> NIGHTMARE_VINE_FEATURE=
        FEATURES.register("nightmare_vine",()->new NightmareVineFeature(NoneFeatureConfiguration.CODEC));
    public static final DeferredHolder<Feature<?>,AnchorHerbFeature> DEATHBLOOM_FEATURE=
        FEATURES.register("deathbloom",()->new AnchorHerbFeature(net.minecraft.world.level.block.Blocks.BONE_BLOCK,HERB_BUSH::get,NoneFeatureConfiguration.CODEC));
    public static final DeferredHolder<Feature<?>,AnchorHerbFeature> BLACKSTONE_GRASS_FEATURE=
        FEATURES.register("blackstone_grass",()->new AnchorHerbFeature(net.minecraft.world.level.block.Blocks.OBSIDIAN,HERB_BUSH::get,NoneFeatureConfiguration.CODEC));
    public static final DeferredHolder<Feature<?>,StormVineFeature> STORM_VINE_FEATURE=
        FEATURES.register("storm_vine",()->new StormVineFeature(NoneFeatureConfiguration.CODEC));
    public static final String SEED_ID="herb_seed";
    public static void register(IEventBus bus){
        BLOCKS.register(bus);
        FEATURES.register(bus);
        AlchemyContent.install(bus);
        ItemRegistry.ALL.put(SEED_ID,ItemRegistry.ITEMS.registerItem(SEED_ID,HerbSeedItem::new));
    }
}
