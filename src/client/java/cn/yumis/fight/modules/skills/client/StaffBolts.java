package cn.yumis.fight.modules.skills.client;
import cn.yumis.fight.modules.skills.api.client.SkillsClient;
import cn.yumis.fight.modules.skills.api.client.SkillController;
import cn.yumis.fight.modules.skills.api.client.TrainingCombat;
import com.google.gson.JsonObject;
import cn.yumis.fight.modules.attributes.api.Stats;
import cn.yumis.fight.modules.equipment.api.*;
import cn.yumis.fight.modules.presentation.api.client.ClientUi;
import cn.yumis.fight.modules.combat.api.ProjectileSpawn;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import java.util.*;
/** Straight, single-collision basic projectiles; all damage still uses the shared hit calculator. */
public final class StaffBolts {
    @FunctionalInterface public interface Hit {boolean apply(LivingEntity target,Stats stats,EquipmentEffects.Snapshot effects,String cycle,int slash,Vec3 point);}
    private static final class Bolt {Stats stats;EquipmentEffects.Snapshot effects;String cycle,dimension,session,id;int slash,sequence;long generation,last,started,waitUntil;Vec3 origin,point,direction;double remaining,range;LivingEntity waiting;}
    private final List<Bolt> bolts=new ArrayList<>();
    public void clear(){for(var b:bolts)TrainingCombat.release(b.cycle,"staff/"+b.slash);bolts.clear();}
    public void fire(SkillsClient state,Stats stats,EquipmentEffects.Snapshot effects,String cycle,int slash,JsonObject content){var mc=Minecraft.getInstance();if(!content.has("staffProjectile"))return;var launch=content.getAsJsonObject("staffProjectile");Bolt b=new Bolt();b.stats=stats;b.effects=effects;b.cycle=cycle;b.slash=slash;b.dimension=mc.level.dimension().identifier().toString();b.session=state.session();b.generation=state.own().generation;b.origin=b.point=ProjectileSpawn.vector(launch.getAsJsonArray("origin"));b.direction=ProjectileSpawn.vector(launch.getAsJsonArray("direction"));b.remaining=b.range=launch.get("range").getAsDouble();b.started=launch.get("at").getAsLong();b.last=ClientUi.now()-Math.max(0,state.serverNow()-b.started);b.id=UUID.nameUUIDFromBytes((cycle+"/staff/"+slash).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();bolts.add(b);TrainingCombat.hold(cycle,"staff/"+slash,ClientUi.now()+2000);visual(state,b,"START");for(var entity:mc.level.getEntitiesOfClass(LivingEntity.class,new AABB(b.point,b.point.add(b.direction.scale(b.range))).inflate(2),StaffBolts::enemy))state.context(entity);}
    private void finish(SkillsClient state,Bolt b,String phase){TrainingCombat.release(b.cycle,"staff/"+b.slash);visual(state,b,phase);bolts.remove(b);}
    private void visual(SkillsClient state,Bolt b,String phase){var mc=Minecraft.getInstance();if(mc.player==null)return;JsonObject j=new JsonObject();j.addProperty("caster",mc.player.getStringUUID());j.addProperty("session",b.session);j.addProperty("generation",b.generation);j.addProperty("id",b.id);j.addProperty("kind","ATTACK");j.addProperty("phase",phase);j.addProperty("sequence",b.sequence++);j.addProperty("seed",b.id.hashCode());j.addProperty("startedAt",b.started);j.addProperty("at",state.serverNow());j.addProperty("duration",2000);j.addProperty("cosmeticOnly",true);j.addProperty("color",0xb4f2ff);j.addProperty("x",b.origin.x);j.addProperty("y",b.origin.y);j.addProperty("z",b.origin.z);j.addProperty("px",b.point.x);j.addProperty("py",b.point.y);j.addProperty("pz",b.point.z);j.addProperty("dx",b.direction.x);j.addProperty("dy",b.direction.y);j.addProperty("dz",b.direction.z);if(phase.equals("START")){var motion=SkillController.motion(b.range,b.range/20*1000,false);motion.addProperty("at",b.started);j.add("motion",motion);}Effects.receive(j);state.send("visual",j);}
    private static boolean enemy(LivingEntity e){return e.isAlive()&&(e instanceof Enemy||e instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon);}
    public void tick(SkillsClient state,Hit hit){var mc=Minecraft.getInstance();long now=ClientUi.now();if(mc.level==null||mc.player==null){clear();return;}
        for(Bolt b:new ArrayList<>(bolts)){
            TrainingCombat.touch(b.cycle);
            if(state.own().dead||!b.session.equals(state.session())||b.generation!=state.own().generation||!b.dimension.equals(mc.level.dimension().identifier().toString())){finish(state,b,"CANCEL");continue;}
            if(b.waiting!=null){if(!b.waiting.isAlive()||now>b.waitUntil){finish(state,b,"CANCEL");continue;}if(state.context(b.waiting)!=null){hit.apply(b.waiting,b.stats,b.effects,b.cycle,b.slash,b.point);finish(state,b,"FINISH");}continue;}
            double distance=Math.min(b.remaining,20*Math.max(0,now-b.last)/1000d);b.last=now;if(distance<=0)continue;Vec3 next=b.point.add(b.direction.scale(distance));
            var block=mc.level.clip(new ClipContext(b.point,next,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,mc.player));double first=block.getType()==HitResult.Type.MISS?Double.POSITIVE_INFINITY:b.point.distanceToSqr(block.getLocation());LivingEntity target=null;Vec3 point=null;
            for(LivingEntity entity:mc.level.getEntitiesOfClass(LivingEntity.class,new AABB(b.point,next).inflate(.25),StaffBolts::enemy)){var box=entity.getBoundingBox().inflate(.15);Vec3 collision=box.contains(b.point)?b.point:box.clip(b.point,next).orElse(null);if(collision!=null&&b.point.distanceToSqr(collision)<first){first=b.point.distanceToSqr(collision);target=entity;point=collision;}}
            if(target!=null){b.point=point;if(state.context(target)==null){b.waiting=target;b.waitUntil=now+1000;}else{hit.apply(target,b.stats,b.effects,b.cycle,b.slash,point);finish(state,b,"FINISH");}continue;}
            if(block.getType()!=HitResult.Type.MISS){b.point=block.getLocation();finish(state,b,"FINISH");continue;}
            b.point=next;b.remaining-=distance;if(b.remaining<=0)finish(state,b,"FINISH");
        }
    }
}
