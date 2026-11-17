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
    private static final String[][] POTIONS={
        {"potion_life",AlchemyPotions.LIFE},
        {"potion_mana",AlchemyPotions.MANA},
        {"potion_wisdom",AlchemyPotions.WISDOM},
        {"potion_energy",AlchemyPotions.ENERGY},
        {"potion_resistance",AlchemyPotions.RESISTANCE},
        {"potion_titan",AlchemyPotions.TITAN},
        {"potion_defense",AlchemyPotions.DEFENSE},
        {"potion_giant",AlchemyPotions.GIANT},
        {"potion_life_2",AlchemyPotions.LIFE2},
        {"potion_mana_2",AlchemyPotions.MANA2},
        {"potion_wisdom_2",AlchemyPotions.WISDOM2},
        {"potion_energy_2",AlchemyPotions.ENERGY2},
        {"potion_resistance_2",AlchemyPotions.RESISTANCE2},
        {"potion_titan_2",AlchemyPotions.TITAN2},
        {"potion_defense_2",AlchemyPotions.DEFENSE2},
        {"potion_giant_2",AlchemyPotions.GIANT2},
        {"potion_life_3",AlchemyPotions.LIFE3},
        {"potion_mana_3",AlchemyPotions.MANA3},
        {"potion_wisdom_3",AlchemyPotions.WISDOM3},
        {"potion_energy_3",AlchemyPotions.ENERGY3},
        {"potion_resistance_3",AlchemyPotions.RESISTANCE3},
        {"potion_titan_3",AlchemyPotions.TITAN3},
        {"potion_defense_3",AlchemyPotions.DEFENSE3},
        {"potion_giant_3",AlchemyPotions.GIANT3},
        {"potion_life_4",AlchemyPotions.LIFE4},{"potion_mana_4",AlchemyPotions.MANA4},
        {"potion_wisdom_4",AlchemyPotions.WISDOM4},{"potion_energy_4",AlchemyPotions.ENERGY4},
        {"potion_resistance_4",AlchemyPotions.RESISTANCE4},{"potion_titan_4",AlchemyPotions.TITAN4},
        {"potion_defense_4",AlchemyPotions.DEFENSE4},{"potion_giant_4",AlchemyPotions.GIANT4},
        {"potion_life_5",AlchemyPotions.LIFE5},{"potion_mana_5",AlchemyPotions.MANA5},
        {"potion_wisdom_5",AlchemyPotions.WISDOM5},{"potion_energy_5",AlchemyPotions.ENERGY5},
        {"potion_resistance_5",AlchemyPotions.RESISTANCE5},{"potion_titan_5",AlchemyPotions.TITAN5},
        {"potion_defense_5",AlchemyPotions.DEFENSE5},{"potion_giant_5",AlchemyPotions.GIANT5},
        {"potion_life_6",AlchemyPotions.LIFE6},{"potion_mana_6",AlchemyPotions.MANA6},
        {"potion_wisdom_6",AlchemyPotions.WISDOM6},{"potion_energy_6",AlchemyPotions.ENERGY6},
        {"potion_resistance_6",AlchemyPotions.RESISTANCE6},{"potion_titan_6",AlchemyPotions.TITAN6},
        {"potion_defense_6",AlchemyPotions.DEFENSE6},{"potion_giant_6",AlchemyPotions.GIANT6}
    };
    private static final String[] BOTTLES={"emerald_bottle","amethyst_bottle","diamond_bottle"};
    public static void install(IEventBus bus){
        BLOCKS.register(bus);
        BLOCK_ENTITIES.register(bus);
        ItemRegistry.ALL.put(ITEM_ID,ItemRegistry.ITEMS.registerItem(ITEM_ID,p->new BlockItem(CAULDRON.get(),p)));
        ItemRegistry.ALL.put(WAND_ID,ItemRegistry.ITEMS.registerItem(WAND_ID,p->new AlchemyWandItem(p.durability(AlchemyWandItem.DURABILITY))));
        for(String[] entry:POTIONS)ItemRegistry.ALL.put(entry[0],ItemRegistry.ITEMS.registerItem(entry[0],p->new AlchemyPotionItem(p,entry[1])));
        for(String id:BOTTLES)ItemRegistry.ALL.put(id,ItemRegistry.ITEMS.registerItem(id,net.minecraft.world.item.Item::new));
    }
}
