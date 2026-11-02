package cn.yumis.fight.modules.skills.api;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import java.util.function.Predicate;

/** Shared ground targeting: an unobstructed enemy's feet take priority over terrain. */
public final class SkillTargeting {
    public record Aim(Vec3 point,LivingEntity target){}
    private SkillTargeting(){}
    public static Aim ground(LivingEntity actor,double range,Predicate<LivingEntity> enemy){
        Vec3 from=actor.getEyePosition(),to=from.add(actor.getLookAngle().normalize().scale(range));
        var wall=actor.level().clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,actor));
        Vec3 end=wall.getType()==HitResult.Type.MISS?to:wall.getLocation();LivingEntity selected=null;double distance=from.distanceToSqr(end);
        for(var target:actor.level().getEntitiesOfClass(LivingEntity.class,new AABB(from,end).inflate(1),e->e!=actor&&enemy.test(e))){var hit=target.getBoundingBox().inflate(.1).clip(from,end);if(hit.isPresent()&&from.distanceToSqr(hit.get())<=distance){selected=target;distance=from.distanceToSqr(hit.get());}}
        if(selected!=null&&valid(actor,selected.position(),range))return new Aim(selected.position(),selected);
        Vec3 point=wall.getType()==HitResult.Type.MISS?end:end.add(actor.getLookAngle().normalize().scale(-.02));
        var floor=actor.level().clip(new ClipContext(point.add(0,.1,0),point.add(0,-range,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,actor));
        if(floor.getType()==HitResult.Type.MISS||!valid(actor,floor.getLocation(),range))throw new IllegalArgumentException("准星前方16格内没有有效地面");
        return new Aim(floor.getLocation().add(0,.01,0),null);
    }
    private static boolean valid(LivingEntity actor,Vec3 point,double range){var pos=BlockPos.containing(point);return actor.position().distanceToSqr(point)<=range*range+.01&&actor.level().hasChunkAt(pos)&&actor.level().getWorldBorder().isWithinBounds(pos);}
}
