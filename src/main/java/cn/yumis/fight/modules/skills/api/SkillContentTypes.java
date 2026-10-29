package cn.yumis.fight.modules.skills.api;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;
import net.minecraft.world.entity.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.*;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
public final class SkillContentTypes {
    private static final String NS="yumis_dungeon_and_fight";
    private static final DeferredRegister<EntityType<?>> ENTITIES=DeferredRegister.create(Registries.ENTITY_TYPE,NS);
    public static final DeferredHolder<EntityType<?>,EntityType<Herder>> HERDER=ENTITIES.register("herder",()->EntityType.Builder.<Herder>of(Herder::new,MobCategory.MISC).sized(.8f,1.35f).eyeHeight(1.05f).noSave().clientTrackingRange(12).build(ResourceKey.create(Registries.ENTITY_TYPE,Identifier.fromNamespaceAndPath(NS,"herder"))));
    private SkillContentTypes(){}
    public static void register(IEventBus bus){ENTITIES.register(bus);bus.addListener((EntityAttributeCreationEvent event)->event.put(HERDER.get(),Herder.attributes().build()));}
}
