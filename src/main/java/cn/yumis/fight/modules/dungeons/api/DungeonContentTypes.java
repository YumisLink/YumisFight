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
    public static final DeferredHolder<EntityType<?>, EntityType<CastleEnemy>> CASTLE_KNIGHT = castleEnemy("victoria_knight", false);
    public static final DeferredHolder<EntityType<?>, EntityType<Marker>> CASTLE_KNIGHT_RESPAWN = ENTITIES.register("victoria_knight_respawn",
        () -> EntityType.Builder.<Marker>of(CastleKnightRespawn::new, MobCategory.MISC)
            .sized(0, 0).noSummon().noLootTable().clientTrackingRange(0)
            .build(ResourceKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(ID, "victoria_knight_respawn"))));
    public static final DeferredHolder<EntityType<?>, EntityType<CastleEnemy>> KARL = castleEnemy("karl", true);
    public static final DeferredHolder<EntityType<?>, EntityType<KarlPhantomSword>> KARL_PHANTOM_SWORD = ENTITIES.register("karl_phantom_sword",
        () -> EntityType.Builder.<KarlPhantomSword>of(KarlPhantomSword::new, MobCategory.MISC)
            .sized(.25f, .25f).noSave().noSummon().clientTrackingRange(8).updateInterval(2)
            .build(ResourceKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(ID, "karl_phantom_sword"))));
    public static final Map<String,DeferredHolder<EntityType<?>,EntityType<DungeonEnemy>>> ENEMIES=new LinkedHashMap<>();
    /** Temporary ritual mechanisms; the run owns their lifecycle and they never join ordinary encounter clears. */
    public static final Map<String,DeferredHolder<EntityType<?>,EntityType<RitualMechanismEntity>>> MECHANISMS=new LinkedHashMap<>();
    public static final DeferredItem<net.minecraft.world.item.Item> TREASURE_MAP=cn.yumis.fight.platform.ItemRegistry.ITEMS.registerItem("goblin_treasure_map",TreasureMapItem::new);
    public static final DeferredItem<net.minecraft.world.item.Item> WAYSTONE=cn.yumis.fight.platform.ItemRegistry.ITEMS.registerItem("waystone",WaystoneItem::new);
    public static final DeferredRegister.Blocks BLOCKS=DeferredRegister.createBlocks(ID);
    public static final DeferredBlock<AwakeningTowerStele> AWAKENING_TOWER_STELE=BLOCKS.registerBlock("awakening_tower_stele",AwakeningTowerStele::new,p->p.strength(-1,3600000).sound(SoundType.STONE).noOcclusion().pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK).lightLevel(s->s.getValue(AwakeningTowerStele.HALF)==net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER?5:0));
    public static final DeferredBlock<WaystonePedestalBlock> WAYSTONE_PEDESTAL=BLOCKS.registerBlock("waystone_pedestal",WaystonePedestalBlock::new,p->p.strength(4,3600000).sound(SoundType.STONE).noOcclusion().pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK).lightLevel(s->s.getValue(WaystonePedestalBlock.STONE)>0?7:0));
    public static final DeferredBlock<KarlSummoningAltarBlock> KARL_ALTAR = BLOCKS.registerBlock(KarlSummoningAltarBlock.ID,
        KarlSummoningAltarBlock::new, p -> p.strength(-1, 3600000).sound(SoundType.DEEPSLATE).noOcclusion()
            .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK).lightLevel(state -> 7));
    public static final DeferredRegister<net.minecraft.world.level.block.entity.BlockEntityType<?>> BLOCK_ENTITIES=DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE,ID);
    public static final DeferredHolder<net.minecraft.world.level.block.entity.BlockEntityType<?>,net.minecraft.world.level.block.entity.BlockEntityType<WaystonePedestalBlockEntity>> WAYSTONE_PEDESTAL_ENTITY=BLOCK_ENTITIES.register("waystone_pedestal",()->new net.minecraft.world.level.block.entity.BlockEntityType<>(WaystonePedestalBlockEntity::new,Set.of(WAYSTONE_PEDESTAL.get())));
    public static final DeferredHolder<net.minecraft.world.level.block.entity.BlockEntityType<?>,net.minecraft.world.level.block.entity.BlockEntityType<KarlSummoningAltarBlockEntity>> KARL_ALTAR_ENTITY =
        BLOCK_ENTITIES.register(KarlSummoningAltarBlock.ID, () -> new net.minecraft.world.level.block.entity.BlockEntityType<>(KarlSummoningAltarBlockEntity::new, Set.of(KARL_ALTAR.get())));
    public static final DeferredItem<net.minecraft.world.item.Item> WAYSTONE_PEDESTAL_ITEM=cn.yumis.fight.platform.ItemRegistry.ITEMS.registerItem("waystone_pedestal",p->new net.minecraft.world.item.BlockItem(WAYSTONE_PEDESTAL.get(),p.useBlockDescriptionPrefix()));
    public static final DeferredItem<net.minecraft.world.item.Item> KARL_ALTAR_ITEM = cn.yumis.fight.platform.ItemRegistry.ITEMS.registerItem(
        KarlSummoningAltarBlock.ID, p -> new net.minecraft.world.item.BlockItem(KARL_ALTAR.get(), p.useBlockDescriptionPrefix()));
    public static final DeferredItem<net.minecraft.world.item.Item> KARL_TOKEN = cn.yumis.fight.platform.ItemRegistry.ITEMS.registerItem("karl_token",
        p -> new net.minecraft.world.item.Item(p.rarity(net.minecraft.world.item.Rarity.UNCOMMON)));
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
    private static DeferredHolder<EntityType<?>, EntityType<CastleEnemy>> castleEnemy(String name, boolean karl) {
        return ENTITIES.register(name, () -> EntityType.Builder.<CastleEnemy>of((type, level) -> new CastleEnemy(type, level, karl), MobCategory.MONSTER)
            .sized(.6f, 1.95f).eyeHeight(1.74f).notInPeaceful().noLootTable().clientTrackingRange(10)
            .build(ResourceKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(ID, name))));
    }

    public static void register(IEventBus bus){ENTITIES.register(bus);BLOCKS.register(bus);BLOCK_ENTITIES.register(bus);WaystoneActions.registerEvents();cn.yumis.fight.platform.ItemRegistry.ALL.put("waystone",WAYSTONE);cn.yumis.fight.platform.ItemRegistry.ALL.put("waystone_pedestal",WAYSTONE_PEDESTAL_ITEM);cn.yumis.fight.platform.ItemRegistry.ALL.put("goblin_treasure_map",TREASURE_MAP);cn.yumis.fight.platform.ItemRegistry.ALL.put("training_sandbag",TRAINING_SANDBAG_ITEM);cn.yumis.fight.platform.ItemRegistry.ALL.put(KarlSummoningAltarBlock.ID,KARL_ALTAR_ITEM);cn.yumis.fight.platform.ItemRegistry.ALL.put("karl_token",KARL_TOKEN);
        bus.addListener((EntityAttributeCreationEvent e)->{
            e.put(TRAINING_SANDBAG.get(), TrainingSandbag.createAttributes().build());
            e.put(CASTLE_KNIGHT.get(), CastleEnemy.createAttributes().build());
            e.put(KARL.get(), CastleEnemy.createAttributes().build());
            for(var type:ENEMIES.values())e.put(type.get(),DungeonEnemy.createAttributes().build());
            for(var type:MECHANISMS.values())e.put(type.get(),DungeonEnemy.createAttributes().build());
        });
    }

    static {for(var kind:ForbiddenEnemyKind.values())ENEMIES.put(kind.id,ENTITIES.register(kind.id,()->EntityType.Builder.<DungeonEnemy>of(DungeonEnemy::new,MobCategory.MONSTER).sized(kind.width,kind.height).eyeHeight(kind.height*.75f).clientTrackingRange(12).build(ResourceKey.create(Registries.ENTITY_TYPE,Identifier.fromNamespaceAndPath(ID,kind.id)))));}
    static {for(var kind:SkasaEnemyKind.values())ENEMIES.put(kind.id,ENTITIES.register(kind.id,()->EntityType.Builder.<DungeonEnemy>of(DungeonEnemy::new,MobCategory.MONSTER).sized(kind.width,kind.height).eyeHeight(kind.height*.75f).clientTrackingRange(16).build(ResourceKey.create(Registries.ENTITY_TYPE,Identifier.fromNamespaceAndPath(ID,kind.id)))));}
    static {for(var kind:DarkCityEnemyKind.values())ENEMIES.put(kind.id,ENTITIES.register(kind.id,()->EntityType.Builder.<DungeonEnemy>of(DungeonEnemy::new,MobCategory.MONSTER).sized(kind.width,kind.height).eyeHeight(kind.height*.75f).clientTrackingRange(14).build(ResourceKey.create(Registries.ENTITY_TYPE,Identifier.fromNamespaceAndPath(ID,kind.id)))));}
    static {for(var kind:ClassicEnemyKind.values())ENEMIES.put(kind.id,ENTITIES.register(kind.id,()->EntityType.Builder.<DungeonEnemy>of(DungeonEnemy::new,MobCategory.MONSTER).sized(kind.width,kind.height).eyeHeight(kind.height*.75f).clientTrackingRange(kind==ClassicEnemyKind.TIME_RIFT_YOUNG_SKASA?18:14).build(ResourceKey.create(Registries.ENTITY_TYPE,Identifier.fromNamespaceAndPath(ID,kind.id)))));}
    static {for(var kind:AltarBossKind.values())ENEMIES.put(kind.id,ENTITIES.register(kind.id,()->EntityType.Builder.<DungeonEnemy>of((type,level)->new RitualBossEntity(type,level),MobCategory.MISC).sized(kind.width,kind.height).eyeHeight(kind.height*.75f).clientTrackingRange(16).fireImmune().build(ResourceKey.create(Registries.ENTITY_TYPE,Identifier.fromNamespaceAndPath(ID,kind.id)))));}
    static {
        var sizes=List.of(
            new java.util.AbstractMap.SimpleEntry<>("ritual_fake_branch",new float[]{.6f,1f}),
            new java.util.AbstractMap.SimpleEntry<>("ritual_hedge",new float[]{1.2f,1.6f}),
            new java.util.AbstractMap.SimpleEntry<>("ritual_root_knot",new float[]{.8f,.7f}),
            new java.util.AbstractMap.SimpleEntry<>("ritual_sprout",new float[]{.5f,1.2f}),
            new java.util.AbstractMap.SimpleEntry<>("ritual_spring",new float[]{.5f,1.5f}),
            new java.util.AbstractMap.SimpleEntry<>("ritual_eye_coin",new float[]{.45f,.45f}),
            new java.util.AbstractMap.SimpleEntry<>("ritual_pull_line",new float[]{.4f,.4f}),
            new java.util.AbstractMap.SimpleEntry<>("ritual_anti_magic",new float[]{3.5f,.8f}),
            new java.util.AbstractMap.SimpleEntry<>("ritual_resonance",new float[]{2.5f,1.8f}),
            new java.util.AbstractMap.SimpleEntry<>("ritual_broken_watch",new float[]{.7f,.6f}),
            new java.util.AbstractMap.SimpleEntry<>("ritual_empty_pan",new float[]{1.6f,1.6f}),
            new java.util.AbstractMap.SimpleEntry<>("ritual_campfire",new float[]{.9f,.9f}),
            new java.util.AbstractMap.SimpleEntry<>("ritual_stele",new float[]{.7f,2.2f}),
            new java.util.AbstractMap.SimpleEntry<>("ritual_confessional",new float[]{1.4f,2.6f}),
            new java.util.AbstractMap.SimpleEntry<>("ritual_shard",new float[]{.5f,.9f})
        );
        for(var entry:sizes)MECHANISMS.put(entry.getKey(),ENTITIES.register(entry.getKey(),()->EntityType.Builder.<RitualMechanismEntity>of(RitualMechanismEntity::new,MobCategory.MISC).sized(entry.getValue()[0],entry.getValue()[1]).eyeHeight(entry.getValue()[1]*.75f).clientTrackingRange(10).build(ResourceKey.create(Registries.ENTITY_TYPE,Identifier.fromNamespaceAndPath(ID,entry.getKey())))));
    }
}
