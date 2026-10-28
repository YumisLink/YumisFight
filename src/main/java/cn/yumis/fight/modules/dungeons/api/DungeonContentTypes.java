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
    public static final DeferredHolder<EntityType<?>,EntityType<CageEntity>> CAGE=ENTITIES.register("lightning_cage",()->EntityType.Builder.<CageEntity>of(CageEntity::new,MobCategory.MISC).sized(3,2.5f).noSave().clientTrackingRange(12).build(ResourceKey.create(Registries.ENTITY_TYPE,Identifier.fromNamespaceAndPath(ID,"lightning_cage"))));
    public static final Map<String,DeferredHolder<EntityType<?>,EntityType<DungeonEnemy>>> ENEMIES=new LinkedHashMap<>();
    static {
        for(String name:List.of("goblin","goblin_thrower","goblin_captain","goblin_shaman","dragonman","wyvern","blue_dragonman","blue_wyvern","lucas"))ENEMIES.put(name,ENTITIES.register(name,()->{
            float width=name.contains("wyvern")||name.equals("lucas")?1.1f:name.equals("goblin_captain")?1:name.equals("goblin_shaman")?1.05f:.9f;
            float height=name.contains("wyvern")?1.2f:name.equals("lucas")?2.25f:name.equals("goblin_shaman")?1.75f:name.equals("goblin_captain")?1.5f:name.startsWith("goblin")?1.25f:1.95f;
            return EntityType.Builder.<DungeonEnemy>of(DungeonEnemy::new,MobCategory.MONSTER).sized(width,height).eyeHeight(height*.8f).clientTrackingRange(12).build(ResourceKey.create(Registries.ENTITY_TYPE,Identifier.fromNamespaceAndPath(ID,name)));
        }
        ));
    }
    public static void register(IEventBus bus){ENTITIES.register(bus);bus.addListener((EntityAttributeCreationEvent e)->{for(var type:ENEMIES.values())e.put(type.get(),DungeonEnemy.createAttributes().build());});}

}
