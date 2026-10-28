package cn.yumis.fight.modules.economy.api;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.effect.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.*;
import net.neoforged.neoforge.event.server.*;
import java.util.*;
import cn.yumis.fight.platform.ItemData;

/** Module-owned platform registrations. */
public final class RewardContentTypes {
    private static final String ID=ItemData.NS;
    public static final DeferredRegister<EntityType<?>> ENTITIES=DeferredRegister.create(Registries.ENTITY_TYPE,ID);
    public static final DeferredHolder<EntityType<?>,EntityType<PrivateLoot>> PRIVATE_LOOT=ENTITIES.register("private_loot",()->EntityType.Builder.<PrivateLoot>of(PrivateLoot::new,MobCategory.MISC).sized(.3f,.3f).clientTrackingRange(8).updateInterval(10).build(ResourceKey.create(Registries.ENTITY_TYPE,Identifier.fromNamespaceAndPath(ID,"private_loot"))));
    public static void register(IEventBus bus){ENTITIES.register(bus);}
}
