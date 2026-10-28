package cn.yumis.fight.modules.skills.client;
import cn.yumis.fight.modules.skills.api.client.SkillsClient;
import cn.yumis.fight.modules.attributes.api.Stats;
import cn.yumis.fight.modules.equipment.api.*;
import cn.yumis.fight.modules.presentation.api.client.ClientUi;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import java.util.*;
/** Straight, single-collision basic projectiles; all damage still uses the shared hit calculator. */
public final class StaffBolts {
    @FunctionalInterface public interface Hit {boolean apply(LivingEntity target,Stats stats,EquipmentEffects.Snapshot effects,String cycle,int slash,Vec3 point);}
    private static final class Bolt {Stats stats;EquipmentEffects.Snapshot effects;String cycle,dimension,session;int slash;long generation,last,waitUntil;Vec3 point,direction;double remaining=12;LivingEntity waiting;}
    private final List<Bolt> bolts=new ArrayList<>();
    public void clear(){bolts.clear();}
    public void fire(SkillsClient state,Stats stats,EquipmentEffects.Snapshot effects,String cycle,int slash){var mc=Minecraft.getInstance();Bolt b=new Bolt();b.stats=stats;b.effects=effects;b.cycle=cycle;b.slash=slash;b.dimension=mc.level.dimension().identifier().toString();b.session=state.session();b.generation=state.own().generation;b.point=mc.player.getEyePosition();b.direction=mc.player.getLookAngle().normalize();b.last=ClientUi.now();bolts.add(b);for(var entity:mc.level.getEntitiesOfClass(LivingEntity.class,new AABB(b.point,b.point.add(b.direction.scale(12))).inflate(2),StaffBolts::enemy))state.context(entity);}
    private static boolean enemy(LivingEntity e){return e.isAlive()&&(e instanceof Enemy||e instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon);}
    public void tick(SkillsClient state,Hit hit){var mc=Minecraft.getInstance();long now=ClientUi.now();if(mc.level==null||mc.player==null){clear();return;}
        for(Bolt b:new ArrayList<>(bolts)){
            if(state.own().dead||!b.session.equals(state.session())||b.generation!=state.own().generation||!b.dimension.equals(mc.level.dimension().identifier().toString())){bolts.remove(b);continue;}
            if(b.waiting!=null){if(!b.waiting.isAlive()||now>b.waitUntil){bolts.remove(b);continue;}if(state.context(b.waiting)!=null){hit.apply(b.waiting,b.stats,b.effects,b.cycle,b.slash,b.point);bolts.remove(b);}continue;}
            double distance=Math.min(b.remaining,20*Math.min(.25,Math.max(0,now-b.last)/1000d));b.last=now;if(distance<=0)continue;Vec3 next=b.point.add(b.direction.scale(distance));
            var block=mc.level.clip(new ClipContext(b.point,next,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,mc.player));double first=block.getType()==HitResult.Type.MISS?Double.POSITIVE_INFINITY:b.point.distanceToSqr(block.getLocation());LivingEntity target=null;Vec3 point=null;
            for(LivingEntity entity:mc.level.getEntitiesOfClass(LivingEntity.class,new AABB(b.point,next).inflate(.25),StaffBolts::enemy)){var collision=entity.getBoundingBox().inflate(.15).clip(b.point,next);if(collision.isPresent()&&b.point.distanceToSqr(collision.get())<first){first=b.point.distanceToSqr(collision.get());target=entity;point=collision.get();}}
            if(target!=null){b.point=point;Effects.spark(point,0xb4f2ff);if(state.context(target)==null){b.waiting=target;b.waitUntil=now+1000;}else{hit.apply(target,b.stats,b.effects,b.cycle,b.slash,point);bolts.remove(b);}continue;}
            if(block.getType()!=HitResult.Type.MISS){Effects.spark(block.getLocation(),0xb4f2ff);bolts.remove(b);continue;}
            b.point=next;b.remaining-=distance;Effects.spark(next,0xb4f2ff);if(b.remaining<=0)bolts.remove(b);
        }
    }
}
