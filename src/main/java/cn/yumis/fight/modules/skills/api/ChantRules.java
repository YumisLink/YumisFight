package cn.yumis.fight.modules.skills.api;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.*;

/** One independent multiplier; client input must not apply a second movement slowdown. */
public final class ChantRules {
    private static final Identifier SLOW=Identifier.parse("yumis_dungeon_and_fight:chant_movement");
    private ChantRules(){}
    public static void movement(LivingEntity actor,boolean active){var attribute=actor.getAttribute(Attributes.MOVEMENT_SPEED);if(attribute==null)return;if(active){if(!attribute.hasModifier(SLOW))attribute.addTransientModifier(new AttributeModifier(SLOW,-.8,AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));}else attribute.removeModifier(SLOW);}
}
