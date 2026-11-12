package cn.yumis.fight.modules.npc.api;

import cn.yumis.fight.platform.ItemData;
import com.mojang.serialization.Codec;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.*;
import net.minecraft.world.entity.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.*;

public final class NpcContentTypes {
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS=DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES,ItemData.NS);
    public static final DeferredHolder<AttachmentType<?>,AttachmentType<String>> BINDING=ATTACHMENTS.register("npc_interaction",()->AttachmentType.builder(()->"").serialize(Codec.STRING.fieldOf("definition")).sync(ByteBufCodecs.STRING_UTF8).build());
    private static final DeferredRegister<EntityType<?>> ENTITIES=DeferredRegister.create(Registries.ENTITY_TYPE,ItemData.NS);
    public static final DeferredHolder<EntityType<?>,EntityType<NpcEntity>> NPC=ENTITIES.register("npc",()->EntityType.Builder.<NpcEntity>of(NpcEntity::new,MobCategory.MISC).sized(.6f,1.8f).eyeHeight(1.62f).clientTrackingRange(12).build(ResourceKey.create(Registries.ENTITY_TYPE,Identifier.fromNamespaceAndPath(ItemData.NS,"npc"))));
    private NpcContentTypes(){}
    public static void register(IEventBus bus){ATTACHMENTS.register(bus);ENTITIES.register(bus);bus.addListener((EntityAttributeCreationEvent event)->event.put(NPC.get(),NpcEntity.attributes().build()));}
}
