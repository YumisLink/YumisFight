package cn.yumis.fight.modules.mapgeneration.api;

import cn.yumis.fight.modules.mapgeneration.platform.*;
import cn.yumis.fight.platform.ItemData;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.*;
import java.util.*;

/** Placeable world dressing; registration alone never changes terrain or dungeon layouts. */
public final class WorldDecorations {
    private static final DeferredRegister.Blocks BLOCKS=DeferredRegister.createBlocks(ItemData.NS);
    private static final DeferredRegister.Items ITEMS=DeferredRegister.createItems(ItemData.NS);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES=DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE,ItemData.NS);
    private static final DeferredRegister<CreativeModeTab> TABS=DeferredRegister.create(Registries.CREATIVE_MODE_TAB,ItemData.NS);
    private static final Map<String,DeferredBlock<Block>> CONTENT=new LinkedHashMap<>();
    static {
        for(var definition:DecorationDefinitions.ENTRIES) {
            var block=BLOCKS.<Block>registerBlock(definition.id(),properties->switch(definition.form()) {
                case LEAVES->new LeafDecorationBlock(properties);
                case SCATTERED->new ScatteredDecorationBlock(definition.id(),properties);
                case PROP->new DecorationBlock(definition.id(),properties);
                case FLOATING->new FloatingShardBlock(properties);
            },properties->{
                properties.strength(definition.hardness()).sound(definition.sound()).noOcclusion();
                if(!definition.collides())properties.noCollision();
                if(definition.form()==DecorationDefinitions.Form.FLOATING)properties.dynamicShape();
                return properties;
            });
            CONTENT.put(definition.id(),block);
            if(!Set.of("gray_pebbles","dark_pebbles").contains(definition.id()))ITEMS.registerItem(definition.id(),
                    properties->definition.food()==null?new BlockItem(block.get(),properties):new EdibleDecorationItem(block.get(),properties),
                    properties->{
                        properties.useBlockDescriptionPrefix();
                        if(definition.food()!=null)properties.food(definition.food()).component(DataComponents.LORE,new ItemLore(List.of(
                                Component.translatable("item."+ItemData.NS+"."+definition.id()+".usage"))));
                        return properties;
                    });
        }
    }
    public static final DeferredItem<Item> PEBBLE=ITEMS.registerItem("pebble",properties->new PebbleItem(block("gray_pebbles"),properties.component(
            DataComponents.LORE,new ItemLore(List.of(Component.translatable("item.yumis_dungeon_and_fight.pebble.usage"))))));
    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<FloatingShardBlockEntity>> FLOATING_SHARD_ENTITY=
            BLOCK_ENTITIES.register("palace_floating_shard",()->new BlockEntityType<>(FloatingShardBlockEntity::new,Set.of(CONTENT.get("palace_floating_shard").get())));
    private static final DeferredHolder<CreativeModeTab,CreativeModeTab> TAB=TABS.register("world_decorations",()->CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.yumis_dungeon_and_fight.world_decorations"))
            .icon(()->CONTENT.get("oak_leaf_litter").get().asItem().getDefaultInstance())
            .displayItems((parameters,output)->ITEMS.getEntries().forEach(item->output.accept(item.get()))).build());

    private WorldDecorations(){}
    public static void register(IEventBus bus){BLOCKS.register(bus);ITEMS.register(bus);BLOCK_ENTITIES.register(bus);TABS.register(bus);DecorationWorldgen.register(bus);DecorationPlacement.register();}
    public static Block block(String id){return Objects.requireNonNull(CONTENT.get(id),id).get();}
    public static Map<Block,Integer> leafColors(){
        Map<Block,Integer> result=new LinkedHashMap<>();
        for(var definition:DecorationDefinitions.ENTRIES)if(definition.form()==DecorationDefinitions.Form.LEAVES)result.put(CONTENT.get(definition.id()).get(),definition.tint());
        return Collections.unmodifiableMap(result);
    }
}
