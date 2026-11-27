package cn.yumis.fight.modules.skills.api;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.*;

/** One independent multiplier; client input must not apply a second movement slowdown. */
public final class ChantRules {
    private static final Identifier SLOW=Identifier.parse("yumis_dungeon_and_fight:chant_movement");
    private ChantRules(){}
    public static void movement(LivingEntity actor,boolean active){movement(actor,active,.2);}
    public static void movement(LivingEntity actor,boolean active,double keepRatio){
        var attribute=actor.getAttribute(Attributes.MOVEMENT_SPEED);if(attribute==null)return;
        attribute.removeModifier(SLOW);
        if(active){
            double clamped=Math.clamp(keepRatio,0,1);
            if(clamped<1)attribute.addTransientModifier(new AttributeModifier(SLOW,clamped-1d,AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }
}
