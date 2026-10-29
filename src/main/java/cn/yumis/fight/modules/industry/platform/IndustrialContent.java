package cn.yumis.fight.modules.industry.platform;
import cn.yumis.fight.platform.ItemData;
import cn.yumis.fight.modules.industry.api.IndustrialRecipe;
import cn.yumis.fight.modules.industry.api.DeviceType;
import cn.yumis.fight.modules.inventory.api.DimensionalMenu;

import net.minecraft.core.registries.*;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.*;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.fluids.*;
import net.neoforged.neoforge.registries.*;
import java.util.*;

public final class IndustrialContent {
    public static final String NS=ItemData.NS;
    public record Spec(String id,DeviceType device,int tier){
        public String title(){return "block."+NS+"."+id;}
    }
    public static final DeferredRegister.Blocks BLOCKS=DeferredRegister.createBlocks(NS);
    public static final DeferredRegister.Items ITEMS=DeferredRegister.createItems(NS);
    public static final DeferredRegister<BlockEntityType<?>> BES=DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE,NS);
    public static final DeferredRegister<MenuType<?>> MENUS=DeferredRegister.create(Registries.MENU,NS);
    public static final DeferredRegister<RecipeType<?>> TYPES=DeferredRegister.create(Registries.RECIPE_TYPE,NS);
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS=DeferredRegister.create(Registries.RECIPE_SERIALIZER,NS);
    public static final DeferredRegister<FluidType> FLUID_TYPES=DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES,NS);
    public static final DeferredRegister<Fluid> FLUIDS=DeferredRegister.create(Registries.FLUID,NS);
    public static final DeferredRegister<CreativeModeTab> TABS=DeferredRegister.create(Registries.CREATIVE_MODE_TAB,NS);
    public static final Map<String,Spec> SPECS=new LinkedHashMap<>();
    public static final Map<String,DeferredBlock<? extends Block>> ALL_BLOCKS=new LinkedHashMap<>();
    public static final Map<String,DeferredItem<? extends Item>> ALL_ITEMS=new LinkedHashMap<>();
    public static final DeferredHolder<RecipeType<?>,RecipeType<IndustrialRecipe>> RECIPE_TYPE=TYPES.register("industrial",()->RecipeType.simple(id("industrial")));
    public static final DeferredHolder<RecipeSerializer<?>,RecipeSerializer<IndustrialRecipe>> RECIPE_SERIALIZER=SERIALIZERS.register("industrial",()->new RecipeSerializer<>(IndustrialRecipe.CODEC,ByteBufCodecs.fromCodecWithRegistries(IndustrialRecipe.CODEC.codec())));
    public static final DeferredHolder<RecipeSerializer<?>,RecipeSerializer<MachineUpgradeRecipe>> MACHINE_UPGRADE_SERIALIZER=SERIALIZERS.register("machine_upgrade",()->new RecipeSerializer<>(MachineUpgradeRecipe.CODEC,MachineUpgradeRecipe.STREAM_CODEC));
    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<MachineBlockEntity>> MACHINE_BE=BES.register("industrial",()->new BlockEntityType<>(MachineBlockEntity::new,new HashSet<>(SPECS.keySet().stream().map(k->ALL_BLOCKS.get(k).get()).toList())));
    public static final DeferredHolder<MenuType<?>,MenuType<IndustrialMenu>> MACHINE_MENU=MENUS.register("industrial",()->IMenuTypeExtension.create(IndustrialMenu::new));
    public static final DeferredHolder<MenuType<?>,MenuType<DimensionalMenu>> STORAGE_MENU=MENUS.register("dimensional_storage",()->IMenuTypeExtension.create(DimensionalMenu::new));
    public static final DeferredHolder<FluidType,FluidType> COOLANT_TYPE=FLUID_TYPES.register("coolant",()->new FluidType(FluidType.Properties.create().density(1100).viscosity(1200).canConvertToSource(false)));
    public static final DeferredHolder<Fluid,BaseFlowingFluid.Source> COOLANT=FLUIDS.register("coolant",()->new BaseFlowingFluid.Source(coolantProperties()));
    public static final DeferredHolder<Fluid,BaseFlowingFluid.Flowing> FLOWING_COOLANT=FLUIDS.register("flowing_coolant",()->new BaseFlowingFluid.Flowing(coolantProperties()));
    public static final DeferredBlock<LiquidBlock> COOLANT_BLOCK=BLOCKS.registerBlock("coolant",p->new LiquidBlock(COOLANT.get(),p),p->p.noCollision().strength(100).noLootTable().liquid().replaceable());
    public static final DeferredBlock<WindGeneratorPartBlock> WIND_PART=BLOCKS.registerBlock("wind_generator_part",WindGeneratorPartBlock::new,p->p.strength(3.5f).sound(SoundType.STONE).noOcclusion().pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK));
    public static final DeferredHolder<CreativeModeTab,CreativeModeTab> TAB=TABS.register("industry",()->CreativeModeTab.builder().title(net.minecraft.network.chat.Component.literal("YD-工业")).icon(()->stack("mana_crusher_t1",1)).displayItems((p,out)->ALL_ITEMS.forEach((key,value)->{if(!key.matches("energy_crystal_(?:[6-9]|10)"))out.accept(value.get());})).build());
    static {
        for(DeviceType kind:DeviceType.values()){
            int count=kind.maximumTier()-kind.minimumTier()+1;
            for(int i=1;i<=count;i++){
                int tier=kind.minimumTier()+i-1;
                String key=kind.id+(kind.graded||kind.functional()?"_t"+tier:"");Spec spec=new Spec(key,kind,tier);SPECS.put(key,spec);
                var block=BLOCKS.registerBlock(key,p->new MachineBlock(spec,p),p->{p.strength(3.5f).sound(SoundType.METAL).noOcclusion();if(kind.currencyUpgrader())p.lightLevel(state->state.getValue(MachineBlock.ACTIVE)?7:0);if(kind.has(DeviceType.Capability.WIND))p.pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK);return p;});ALL_BLOCKS.put(key,block);ALL_ITEMS.put(key,ITEMS.registerSimpleBlockItem(block));
            }
        }
        for(int i=1;i<=10;i++)item("energy_crystal_"+i);
        for(int i=1;i<=5;i++)item("industrial_upgrade_"+i);
        for(String key:List.of("nuclear_core","rtg_core"))item(key);
        for(String metal:List.of("lead","uranium","titanium")){item("raw_"+metal);item(metal+"_ingot");block(metal+"_ore",p->new ResourceBlock(metal,p));block(metal+"_block",Block::new);}
        for(String metal:List.of("iron","copper","gold","lead","uranium","titanium"))item("crushed_"+metal);
        for(String material:List.of("terra","spacetime")){item(material+"_ingot");block(material+"_block",Block::new);}
        item("industrial_magic_stone");block("industrial_magic_stone_block",Block::new);item("sulfide");block("energy_crystal_block_2",Block::new);
        block("magic_ore",p->new ResourceBlock("magic",p));
        var grass=BLOCKS.registerBlock("magic_grass",MagicGrassBlock::new,p->p.noCollision().instabreak().sound(SoundType.GRASS).noOcclusion());ALL_BLOCKS.put("magic_grass",grass);ALL_ITEMS.put("magic_grass",ITEMS.registerSimpleBlockItem(grass));
        ALL_ITEMS.put("mana_wand",ITEMS.registerItem("mana_wand",ManaWand::new,p->p.stacksTo(1)));
        ALL_ITEMS.put("coolant_bucket",ITEMS.registerItem("coolant_bucket",TankBucketItem::new,p->p.stacksTo(1).craftRemainder(Items.BUCKET)));
    }
    private IndustrialContent(){}
    public static Identifier id(String key){return Identifier.fromNamespaceAndPath(NS,key);}
    private static BaseFlowingFluid.Properties coolantProperties(){return new BaseFlowingFluid.Properties(COOLANT_TYPE,COOLANT,FLOWING_COOLANT).bucket(()->ALL_ITEMS.get("coolant_bucket").get()).block(COOLANT_BLOCK);}
    private static void item(String key){ALL_ITEMS.put(key,ITEMS.registerSimpleItem(key));}
    private static void block(String key,java.util.function.Function<net.minecraft.world.level.block.state.BlockBehaviour.Properties,? extends Block> factory){var b=BLOCKS.registerBlock(key,factory,p->p.strength(3).requiresCorrectToolForDrops());ALL_BLOCKS.put(key,b);ALL_ITEMS.put(key,ITEMS.registerSimpleBlockItem(b));}
    public static ItemStack stack(String key,int count){return new ItemStack(ALL_ITEMS.get(key).get(),count);}
    public static Spec spec(DeviceType kind,int tier){return SPECS.get(kind.id+(kind.graded||kind.functional()?"_t"+tier:""));}
    public static int crystalTier(ItemStack stack){var key=BuiltInRegistries.ITEM.getKey(stack.getItem());if(!key.getNamespace().equals(NS)||!key.getPath().startsWith("energy_crystal_"))return 0;try{return Integer.parseInt(key.getPath().substring(15));}catch(NumberFormatException bad){return 0;}}
    public static void register(IEventBus bus){
        IndustrialEvents.register();
        BLOCKS.register(bus);ITEMS.register(bus);BES.register(bus);MENUS.register(bus);TYPES.register(bus);SERIALIZERS.register(bus);FLUID_TYPES.register(bus);FLUIDS.register(bus);TABS.register(bus);IndustrialWorldgen.register(bus);
        bus.addListener((RegisterCapabilitiesEvent e)->{e.registerBlockEntity(Capabilities.Item.BLOCK,MACHINE_BE.get(),(be,side)->be.itemsFor(side));e.registerBlockEntity(Capabilities.Fluid.BLOCK,MACHINE_BE.get(),(be,side)->be.fluidsFor(side));});
    }
}
