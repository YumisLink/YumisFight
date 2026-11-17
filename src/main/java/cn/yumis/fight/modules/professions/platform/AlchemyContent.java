package cn.yumis.fight.modules.professions.platform;
import cn.yumis.fight.modules.professions.api.AlchemyPotions;
import cn.yumis.fight.platform.ItemData;
import cn.yumis.fight.platform.ItemRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import java.util.Set;
/** 炼金锅的方块、方块实体与物品注册。 */
public final class AlchemyContent {
    private AlchemyContent(){}
    public static final DeferredRegister.Blocks BLOCKS=DeferredRegister.createBlocks(ItemData.NS);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES=DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE,ItemData.NS);
    public static final DeferredBlock<AlchemyCauldronBlock> CAULDRON=BLOCKS.registerBlock("alchemy_cauldron",AlchemyCauldronBlock::new,
        p->p.strength(3.5f).sound(SoundType.METAL).noOcclusion().lightLevel(s->s.getValue(AlchemyCauldronBlock.HEAT)?7:0));
    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<AlchemyCauldronBlockEntity>> CAULDRON_BE=
        BLOCK_ENTITIES.register("alchemy_cauldron",()->new BlockEntityType<>(AlchemyCauldronBlockEntity::new,Set.of(CAULDRON.get())));
    public static final String ITEM_ID="alchemy_cauldron";
    public static final String WAND_ID="alchemy_wand";
    private static final String[] BOTTLES={"emerald_bottle","amethyst_bottle","diamond_bottle"};
    public static void install(IEventBus bus){
        BLOCKS.register(bus);
        BLOCK_ENTITIES.register(bus);
        ItemRegistry.ALL.put(ITEM_ID,ItemRegistry.ITEMS.registerItem(ITEM_ID,p->new BlockItem(CAULDRON.get(),p)));
        ItemRegistry.ALL.put(WAND_ID,ItemRegistry.ITEMS.registerItem(WAND_ID,p->new AlchemyWandItem(p.durability(AlchemyWandItem.DURABILITY))));
        for(String id:AlchemyPotions.tiers().keySet())ItemRegistry.ALL.put(id,ItemRegistry.ITEMS.registerItem(id,p->new AlchemyPotionItem(p,id)));
        for(String id:BOTTLES)ItemRegistry.ALL.put(id,ItemRegistry.ITEMS.registerItem(id,net.minecraft.world.item.Item::new));
    }
}
