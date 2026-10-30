package cn.yumis.fight.modules.combat.api;

import com.google.gson.*;
import java.util.function.Predicate;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;

/** Birth-point geometry only. Each content owner supplies its existing target qualifications. */
public final class ProjectileSpawn {
    public record Start(Vec3 position,double consumedRange,double remainingRange){}
    private ProjectileSpawn(){}
    public static Start forward(Vec3 base,Vec3 direction,double originalRange,double firstCollisionDistance){
        if(!Double.isFinite(originalRange)||originalRange<0||Double.isNaN(firstCollisionDistance))throw new IllegalArgumentException("Invalid projectile start");
        double advance=direction.lengthSqr()<1e-12?0:Math.min(Math.min(.5,originalRange),Math.max(0,firstCollisionDistance-.01));
        return new Start(base.add(direction.normalize().scale(advance)),advance,originalRange-advance);
    }
    public static Start inWorld(LivingEntity actor,Vec3 base,Vec3 direction,double range,double radius,Predicate<LivingEntity> eligible){
        Vec3 end=base.add(direction.normalize().scale(Math.min(.5,range)));double first=Double.POSITIVE_INFINITY;
        var wall=actor.level().clip(new ClipContext(base,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,actor));
        if(wall.getType()!=HitResult.Type.MISS)first=base.distanceTo(wall.getLocation());
        for(var target:actor.level().getEntitiesOfClass(LivingEntity.class,new AABB(base,end).inflate(radius+.1),entity->entity!=actor&&eligible.test(entity))){
            var box=target.getBoundingBox().inflate(radius);if(box.contains(base)){first=0;break;}
            var hit=box.clip(base,end);if(hit.isPresent())first=Math.min(first,base.distanceTo(hit.get()));
        }
        return forward(base,direction,range,first);
    }
    public static JsonObject snapshot(Start start,Vec3 direction,long at){
        var result=new JsonObject();result.add("origin",array(start.position));result.add("direction",array(direction.normalize()));result.addProperty("range",start.remainingRange);result.addProperty("at",at);return result;
    }
    private static JsonArray array(Vec3 point){var result=new JsonArray();result.add(point.x);result.add(point.y);result.add(point.z);return result;}
    public static Vec3 vector(JsonArray value){return new Vec3(value.get(0).getAsDouble(),value.get(1).getAsDouble(),value.get(2).getAsDouble());}
}
