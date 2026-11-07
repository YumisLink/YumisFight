package cn.yumis.fight.modules.dungeons.api;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.effect.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.*;
import net.neoforged.neoforge.event.server.*;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import java.util.*;
import cn.yumis.fight.platform.ItemData;

/** Module-owned platform registrations. */
public final class DungeonContentTypes {
    private static final String ID=ItemData.NS;
    public static final DeferredRegister<EntityType<?>> ENTITIES=DeferredRegister.create(Registries.ENTITY_TYPE,ID);
    public static final DeferredHolder<EntityType<?>,EntityType<TrainingSandbag>> TRAINING_SANDBAG=ENTITIES.register("training_sandbag",()->EntityType.Builder.<TrainingSandbag>of(TrainingSandbag::new,MobCategory.MISC).sized(.8f,2).eyeHeight(1.5f).fireImmune().clientTrackingRange(10).build(ResourceKey.create(Registries.ENTITY_TYPE,Identifier.fromNamespaceAndPath(ID,"training_sandbag"))));
    public static final DeferredItem<net.minecraft.world.item.Item> TRAINING_SANDBAG_ITEM=cn.yumis.fight.platform.ItemRegistry.ITEMS.registerItem("training_sandbag",p->new TrainingSandbagItem(p.stacksTo(16)));
    public static final DeferredHolder<EntityType<?>,EntityType<CageEntity>> CAGE=ENTITIES.register("lightning_cage",()->EntityType.Builder.<CageEntity>of(CageEntity::new,MobCategory.MISC).sized(3,2.5f).noSave().clientTrackingRange(12).build(ResourceKey.create(Registries.ENTITY_TYPE,Identifier.fromNamespaceAndPath(ID,"lightning_cage"))));
    public static final Map<String,DeferredHolder<EntityType<?>,EntityType<DungeonEnemy>>> ENEMIES=new LinkedHashMap<>();
    public static final DeferredItem<net.minecraft.world.item.Item> TREASURE_MAP=cn.yumis.fight.platform.ItemRegistry.ITEMS.registerItem("goblin_treasure_map",TreasureMapItem::new);
    public static final DeferredItem<net.minecraft.world.item.Item> WAYSTONE=cn.yumis.fight.platform.ItemRegistry.ITEMS.registerItem("waystone",WaystoneItem::new);
    public static final DeferredRegister.Blocks BLOCKS=DeferredRegister.createBlocks(ID);
    public static final DeferredBlock<WaystonePedestalBlock> WAYSTONE_PEDESTAL=BLOCKS.registerBlock("waystone_pedestal",WaystonePedestalBlock::new,p->p.strength(4,3600000).sound(SoundType.STONE).noOcclusion().pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK).lightLevel(s->s.getValue(WaystonePedestalBlock.STONE)>0?7:0));
    public static final DeferredRegister<net.minecraft.world.level.block.entity.BlockEntityType<?>> BLOCK_ENTITIES=DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE,ID);
    public static final DeferredHolder<net.minecraft.world.level.block.entity.BlockEntityType<?>,net.minecraft.world.level.block.entity.BlockEntityType<WaystonePedestalBlockEntity>> WAYSTONE_PEDESTAL_ENTITY=BLOCK_ENTITIES.register("waystone_pedestal",()->new net.minecraft.world.level.block.entity.BlockEntityType<>(WaystonePedestalBlockEntity::new,Set.of(WAYSTONE_PEDESTAL.get())));
    public static final DeferredItem<net.minecraft.world.item.Item> WAYSTONE_PEDESTAL_ITEM=cn.yumis.fight.platform.ItemRegistry.ITEMS.registerItem("waystone_pedestal",p->new net.minecraft.world.item.BlockItem(WAYSTONE_PEDESTAL.get(),p.useBlockDescriptionPrefix()));
    static {
        for(String name:List.of("goblin","goblin_thrower","goblin_captain","goblin_shaman","dragonman","wyvern","blue_dragonman","blue_wyvern","lucas","golden_king"))ENEMIES.put(name,ENTITIES.register(name,()->{
            float width=name.contains("wyvern")||name.equals("lucas")?1.1f:name.equals("goblin_captain")?1:name.equals("goblin_shaman")?1.05f:.9f;
            float height=name.contains("wyvern")?1.2f:name.equals("lucas")?2.25f:name.equals("goblin_shaman")?1.75f:name.equals("goblin_captain")?1.5f:name.startsWith("goblin")?1.25f:1.95f;
            return EntityType.Builder.<DungeonEnemy>of(DungeonEnemy::new,MobCategory.MONSTER).sized(width,height).eyeHeight(height*.8f).clientTrackingRange(12).build(ResourceKey.create(Registries.ENTITY_TYPE,Identifier.fromNamespaceAndPath(ID,name)));
        }
        ));
        for(var kind:GblEnemyKind.values())ENEMIES.put(kind.id,ENTITIES.register(kind.id,()->EntityType.Builder.<DungeonEnemy>of(DungeonEnemy::new,MobCategory.MONSTER).sized(kind.width,kind.height).eyeHeight(kind.height*.75f).clientTrackingRange(12).build(ResourceKey.create(Registries.ENTITY_TYPE,Identifier.fromNamespaceAndPath(ID,kind.id)))));
    }
    static {for(var kind:PalaceEnemyKind.values())ENEMIES.put(kind.id,ENTITIES.register(kind.id,()->EntityType.Builder.<DungeonEnemy>of(DungeonEnemy::new,MobCategory.MONSTER).sized(kind.width,kind.height).eyeHeight(kind.height*.78f).clientTrackingRange(12).build(ResourceKey.create(Registries.ENTITY_TYPE,Identifier.fromNamespaceAndPath(ID,kind.id)))));}
    public static void register(IEventBus bus){ENTITIES.register(bus);BLOCKS.register(bus);BLOCK_ENTITIES.register(bus);WaystoneActions.registerEvents();cn.yumis.fight.platform.ItemRegistry.ALL.put("waystone",WAYSTONE);cn.yumis.fight.platform.ItemRegistry.ALL.put("waystone_pedestal",WAYSTONE_PEDESTAL_ITEM);cn.yumis.fight.platform.ItemRegistry.ALL.put("goblin_treasure_map",TREASURE_MAP);cn.yumis.fight.platform.ItemRegistry.ALL.put("training_sandbag",TRAINING_SANDBAG_ITEM);bus.addListener((EntityAttributeCreationEvent e)->{e.put(TRAINING_SANDBAG.get(),TrainingSandbag.createAttributes().build());for(var type:ENEMIES.values())e.put(type.get(),DungeonEnemy.createAttributes().build());});}

    static {for(var kind:ForbiddenEnemyKind.values())ENEMIES.put(kind.id,ENTITIES.register(kind.id,()->EntityType.Builder.<DungeonEnemy>of(DungeonEnemy::new,MobCategory.MONSTER).sized(kind.width,kind.height).eyeHeight(kind.height*.75f).clientTrackingRange(12).build(ResourceKey.create(Registries.ENTITY_TYPE,Identifier.fromNamespaceAndPath(ID,kind.id)))));}
}
