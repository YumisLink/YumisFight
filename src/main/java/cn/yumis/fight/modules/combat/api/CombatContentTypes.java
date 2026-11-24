package cn.yumis.fight.modules.combat.api;
import com.mojang.serialization.Codec;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.effect.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.*;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.server.*;
import java.util.*;
import cn.yumis.fight.platform.ItemData;

/** Module-owned platform registrations. */
public final class CombatContentTypes {
    private static final String ID=ItemData.NS;
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS=DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES,ID);
    public static final DeferredHolder<AttachmentType<?>,AttachmentType<String>> TARGET=ATTACHMENTS.register("target_modules_v1",()->AttachmentType.builder(()->"").serialize(Codec.STRING.fieldOf("value")).build());
    public static final DeferredRegister<MobEffect> EFFECTS=DeferredRegister.create(Registries.MOB_EFFECT,ID);
    public static final DeferredHolder<MobEffect,MobEffect> STUN=EFFECTS.register("stun",()->new MobEffect(MobEffectCategory.HARMFUL,0xe8c56a){
    }
    );
    public static final DeferredHolder<MobEffect,MobEffect> CLOUD_STEP=EFFECTS.register("cloud_step",()->new MobEffect(MobEffectCategory.BENEFICIAL,0xb9e4f4){});
    public static final DeferredHolder<MobEffect,MobEffect> FREEZE=EFFECTS.register("element_freeze",()->new MobEffect(MobEffectCategory.HARMFUL,0x83dfff){});
    /** Boss/elite freeze is not a hard control: it slows action and windup time. */
    public static final DeferredHolder<MobEffect,MobEffect> CHILL=EFFECTS.register("element_chill",()->new MobEffect(MobEffectCategory.HARMFUL,0x83dfff){});
    public static final DeferredHolder<MobEffect,MobEffect> IGNITE=EFFECTS.register("element_ignite",()->new MobEffect(MobEffectCategory.HARMFUL,0xff8a58){});
    public static final DeferredHolder<MobEffect,MobEffect> SHOCK=EFFECTS.register("element_shock",()->new MobEffect(MobEffectCategory.HARMFUL,0xffed8d){});
    public static final DeferredHolder<MobEffect,MobEffect> EROSION=EFFECTS.register("element_erosion",()->new MobEffect(MobEffectCategory.HARMFUL,0xc783ff){});
    /** Ritual lament: received healing scales to 60% for the marked window; the local client owns the instance. */
    public static final DeferredHolder<MobEffect,MobEffect> HEAL_DOWN=EFFECTS.register("ritual_heal_down",HealingReduction::new);
    private static final class HealingReduction extends MobEffect implements CombatEffect {
        HealingReduction(){super(MobEffectCategory.HARMFUL,0x9a86c8);}
        @Override public double receivedHealingScale(){return .6;}
    }
    public static final DeferredHolder<MobEffect,MobEffect> RITUAL_ROOT=EFFECTS.register("ritual_root",()->new MobEffect(MobEffectCategory.HARMFUL,0x667248){}
        .addAttributeModifier(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED,
            Identifier.fromNamespaceAndPath(ID,"ritual_root"),-1,net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    public static final DeferredHolder<MobEffect,MobEffect> RITUAL_DEBT=EFFECTS.register("ritual_debt",()->new MobEffect(MobEffectCategory.HARMFUL,0x8f6eb4){}
        .addAttributeModifier(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED,
            Identifier.fromNamespaceAndPath(ID,"ritual_debt"),-.10,net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    public static net.minecraft.core.Holder<MobEffect> elementalEffect(ElementAccumulation.Kind kind){return switch(kind){case FREEZE->FREEZE;case IGNITE->IGNITE;case SHOCK->SHOCK;case EROSION->EROSION;};}
    public static net.minecraft.core.Holder<MobEffect> elementalEffect(ElementAccumulation.Kind kind,boolean strong){return kind==ElementAccumulation.Kind.FREEZE&&strong?CHILL:elementalEffect(kind);}
    public static boolean controlled(LivingEntity entity){return entity.hasEffect(STUN)||entity.hasEffect(FREEZE);}
    public static void register(IEventBus bus){ATTACHMENTS.register(bus);EFFECTS.register(bus);}
}
