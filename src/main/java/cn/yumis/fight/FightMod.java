package cn.yumis.fight;
import cn.yumis.fight.game.*;
import cn.yumis.fight.network.Network;
import cn.yumis.fight.server.ServerRuntime;
import com.mojang.serialization.Codec;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraft.world.effect.*;
import net.neoforged.fml.common.Mod;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.*;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.server.*;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import java.util.*;
@Mod(GameItems.NS)
public final class FightMod {
    public static final String ID=GameItems.NS;
    public static final DeferredRegister.Blocks BLOCKS=DeferredRegister.createBlocks(ID);
    public static final DeferredBlock<FabricatorBlock> FABRICATOR=BLOCKS.registerBlock("equipment_fabricator",FabricatorBlock::new,p->p.strength(3).sound(SoundType.METAL));
    public static final DeferredRegister<MenuType<?>> MENUS=DeferredRegister.create(Registries.MENU,ID);
    public static final DeferredHolder<MenuType<?>,MenuType<FabricatorMenu>> FABRICATOR_MENU=MENUS.register("fabricator",()->new MenuType<>(FabricatorMenu::new,FeatureFlags.DEFAULT_FLAGS));
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS=DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES,ID);
    public static final com.mojang.serialization.MapCodec<String> STATE_CODEC=Codec.either(Codec.STRING,Codec.STRING.listOf()).xmap(value->value.map(java.util.function.Function.identity(),parts->String.join("",parts)),(String value)->{
        if(value.length()<=8000)return com.mojang.datafixers.util.Either.left(value);
        List<String> parts=new ArrayList<>();for(int i=0;i<value.length();i+=8000)parts.add(value.substring(i,Math.min(i+8000,value.length())));
        return com.mojang.datafixers.util.Either.right(parts);
    }).fieldOf("value");
    public static final DeferredHolder<AttachmentType<?>,AttachmentType<String>> STATE=ATTACHMENTS.register("state",()->AttachmentType.builder(()->"").serialize(STATE_CODEC).copyOnDeath().build());
    public static final DeferredHolder<AttachmentType<?>,AttachmentType<String>> TARGET=ATTACHMENTS.register("target",()->AttachmentType.builder(()->"").serialize(Codec.STRING.fieldOf("value")).build());
    public static final DeferredHolder<AttachmentType<?>,AttachmentType<ExtraInventory>> EXTRA=ATTACHMENTS.register("extra_container",()->AttachmentType.builder(holder->new ExtraInventory((net.minecraft.world.entity.player.Player)holder)).build());
    public static final DeferredRegister<EntityType<?>> ENTITIES=DeferredRegister.create(Registries.ENTITY_TYPE,ID);
    public static final DeferredHolder<EntityType<?>,EntityType<CageEntity>> CAGE=ENTITIES.register("lightning_cage",()->EntityType.Builder.<CageEntity>of(CageEntity::new,MobCategory.MISC).sized(3,2.5f).noSave().clientTrackingRange(12).build(ResourceKey.create(Registries.ENTITY_TYPE,Identifier.fromNamespaceAndPath(ID,"lightning_cage"))));
    public static final Map<String,DeferredHolder<EntityType<?>,EntityType<DungeonEnemy>>> ENEMIES=new LinkedHashMap<>();
    public static final DeferredHolder<EntityType<?>,EntityType<PrivateLoot>> PRIVATE_LOOT=ENTITIES.register("private_loot",()->EntityType.Builder.<PrivateLoot>of(PrivateLoot::new,MobCategory.MISC).sized(.3f,.3f).clientTrackingRange(8).updateInterval(10).build(ResourceKey.create(Registries.ENTITY_TYPE,Identifier.fromNamespaceAndPath(ID,"private_loot"))));
    public static final DeferredRegister<MobEffect> EFFECTS=DeferredRegister.create(Registries.MOB_EFFECT,ID);
    public static final DeferredHolder<MobEffect,MobEffect> STUN=EFFECTS.register("stun",()->new MobEffect(MobEffectCategory.HARMFUL,0xe8c56a){
    }
    );
    public static final DeferredHolder<MobEffect,MobEffect> CLOUD_STEP=EFFECTS.register("cloud_step",()->new MobEffect(MobEffectCategory.BENEFICIAL,0xb9e4f4){});
    static {
        for(String name:List.of("goblin","goblin_thrower","goblin_captain","goblin_shaman","dragonman","wyvern","blue_dragonman","blue_wyvern","lucas"))ENEMIES.put(name,ENTITIES.register(name,()->{
            float width=name.contains("wyvern")||name.equals("lucas")?1.1f:name.equals("goblin_captain")?1:name.equals("goblin_shaman")?1.05f:.9f;
            float height=name.contains("wyvern")?1.2f:name.equals("lucas")?2.25f:name.equals("goblin_shaman")?1.75f:name.equals("goblin_captain")?1.5f:name.startsWith("goblin")?1.25f:1.95f;
            return EntityType.Builder.<DungeonEnemy>of(DungeonEnemy::new,MobCategory.MONSTER).sized(width,height).eyeHeight(height*.8f).clientTrackingRange(12).build(ResourceKey.create(Registries.ENTITY_TYPE,Identifier.fromNamespaceAndPath(ID,name)));
        }
        ));
    }
    public FightMod(IEventBus bus){
        cn.yumis.fight.industry.IndustrialContent.register(bus);
        GameItems.ITEMS.registerSimpleBlockItem(FABRICATOR);
        GameItems.ITEMS.register(bus);
        BLOCKS.register(bus);
        MENUS.register(bus);
        CreativeCatalog.TABS.register(bus);
        ATTACHMENTS.register(bus);
        ENTITIES.register(bus);
        EFFECTS.register(bus);
        bus.addListener(Network::register);
        bus.addListener((EntityAttributeCreationEvent e)->{
            for(var type:ENEMIES.values())e.put(type.get(),DungeonEnemy.createAttributes().build());
        }
        );
        try{
            var f=RangedAttribute.class.getDeclaredField("maxValue");
            f.setAccessible(true);
            f.setDouble(Attributes.MAX_HEALTH.value(),1_000_000);
        }
        catch(Exception e){
            throw new IllegalStateException("无法扩大生命属性容量",e);
        }
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent e)->ServerRuntime.start(e.getServer()));
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent e)->ServerRuntime.stop());
        ServerRuntime.registerEvents();
    }
}
