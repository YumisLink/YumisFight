package cn.yumis.fight.modules.skills.api;
import net.minecraft.core.registries.Registries;import net.minecraft.resources.*;import net.minecraft.world.entity.*;import net.neoforged.bus.api.IEventBus;import net.neoforged.neoforge.registries.*;import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;import java.util.*;
public final class SkillContentTypes {
 private static final String NS="yumis_dungeon_and_fight";private static final DeferredRegister<EntityType<?>> ENTITIES=DeferredRegister.create(Registries.ENTITY_TYPE,NS);
 public static final Map<SummonKind,DeferredHolder<EntityType<?>,EntityType<SummonedCreature>>> SUMMONS=new EnumMap<>(SummonKind.class);
 static{for(var kind:SummonKind.values())SUMMONS.put(kind,ENTITIES.register(kind.model,()->EntityType.Builder.<SummonedCreature>of(SummonedCreature::new,MobCategory.MISC).sized((float)kind.width,(float)kind.height).eyeHeight((float)(kind.height*.8)).noSave().clientTrackingRange(12).build(ResourceKey.create(Registries.ENTITY_TYPE,Identifier.fromNamespaceAndPath(NS,kind.model)))));}
 public static SummonKind kind(EntityType<?> type){for(var e:SUMMONS.entrySet())if(e.getValue().get()==type)return e.getKey();throw new IllegalArgumentException("Unknown summon entity");}
 public static void register(IEventBus bus){ENTITIES.register(bus);bus.addListener((EntityAttributeCreationEvent e)->{for(var type:SUMMONS.values())e.put(type.get(),SummonedCreature.attributes().build());});}
 private SkillContentTypes(){}
}
