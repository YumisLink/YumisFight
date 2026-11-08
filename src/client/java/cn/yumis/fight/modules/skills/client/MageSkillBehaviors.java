package cn.yumis.fight.modules.skills.client;

import cn.yumis.fight.modules.skills.api.*;
import cn.yumis.fight.modules.skills.api.client.*;
import cn.yumis.fight.modules.presentation.api.client.ClientUi;
import cn.yumis.fight.modules.combat.api.ProjectileSpawn;
import cn.yumis.fight.modules.attributes.api.*;
import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import java.util.*;

/** Player-owned mage projectiles and the snapshots used to create server world summons. */
final class MageSkillBehaviors {
    private static final Gson JSON=new Gson();
    private static final class Flight {SkillCast cast;Vec3 origin,end,position;long at;double duration;int hits,sequence;final Set<UUID> struck=new HashSet<>();}
    private record Fang(SkillCast cast,Vec3 origin,Vec3 direction,long at){}
    private static final List<Flight> flights=new ArrayList<>();
    private static final List<Fang> fangs=new ArrayList<>();
    private static final class SummonFlight {SkillCast cast;SummonMoves.Move move;String visual;Vec3 from,to,position;long at,duration;double arc,progress;int hits,sequence;final Set<UUID> struck=new HashSet<>();}
    private static final List<SummonFlight> summonFlights=new ArrayList<>();
    private record Contact(SkillCast cast,LivingEntity target,double coefficient,long until,String hold){}
    private static final List<Contact> contacts=new ArrayList<>();
    private static final Map< String,List<Vec3>> prepared=new HashMap<>();
    private MageSkillBehaviors(){}
    static void prepare(SkillDefinition skill){var reserved=new ArrayList<Vec3>();for(var kind:SummonerSkills.kinds(skill.id))reserved.add(summonPoint(kind,reserved.size(),reserved));if(!reserved.isEmpty())prepared.put(skill.id,reserved);}
    static void install(){
        MageClient.register(MageSkills.STAR,MageSkillBehaviors::projectile);MageClient.register(MageSkills.JACK,MageSkillBehaviors::projectile);
        MageClient.register(MageSkills.FANG,c->{var p=Minecraft.getInstance().player;var direction=new Vec3(p.getLookAngle().x,0,p.getLookAngle().z).normalize();Vec3 from=p.position().add(0,.8,0);fangs.add(new Fang(c,from,direction,ClientUi.now()+Math.max(1,(long)(200/c.speed))));MageClient.INSTANCE.visual(c,c.id,"START",from,direction,600,0,null);});
        MageClient.register(MageSkills.HERDER,MageSkillBehaviors::summon);
        for(String id:SummonerSkills.ALL)if(!id.equals(SummonerSkills.MASTERY))MageClient.register(id,MageSkillBehaviors::summon);
    }
    static void projectile(SkillCast c){
        var p=Minecraft.getInstance().player;c.direction=p.getLookAngle().normalize();Vec3 from=p.getEyePosition();double range=c.parameters.get("range",12);var spawn=ProjectileSpawn.inWorld(p,from,c.direction,range,.15,MageSkillBehaviors::enemy);
        var f=new Flight();f.cast=c;f.origin=f.position=spawn.position();f.end=from.add(c.direction.scale(range));f.at=ClientUi.now();f.duration=Math.max(1,f.origin.distanceTo(f.end)/c.skill.projectile.speed()*1000);flights.add(f);
        JsonObject motion=new JsonObject();motion.addProperty("range",f.origin.distanceTo(f.end));motion.addProperty("millis",(long)Math.ceil(f.duration));motion.addProperty("velocity",c.skill.projectile.speed());motion.addProperty("arcHeight",0);
        MageClient.INSTANCE.visual(c,c.id,"START",f.origin,c.direction,(long)Math.ceil(f.duration)+c.skill.projectile.impactMillis(),0,motion);
    }
    private static boolean enemy(LivingEntity e){return e.isAlive()&&(e instanceof net.minecraft.world.entity.monster.Enemy||e instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon);}
    private static Vec3 nearest(LivingEntity e,Vec3 p){var b=e.getBoundingBox();return new Vec3(Math.clamp(p.x,b.minX,b.maxX),Math.clamp(p.y,b.minY,b.maxY),Math.clamp(p.z,b.minZ,b.maxZ));}
    static void tick(){
        var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null)return;long now=ClientUi.now();
        tickSummons(now);
        for(var fang:new ArrayList<>(fangs))if(now>=fang.at){fangs.remove(fang);var c=fang.cast;if(!MageClient.INSTANCE.valid(c))continue;for(var target:SkillController.INSTANCE.targets(new AABB(fang.origin,fang.origin.add(fang.direction.scale(5))).inflate(1,1.5,1))){var delta=target.getBoundingBox().getCenter().subtract(fang.origin);double front=delta.dot(fang.direction),side=Math.abs(delta.x*fang.direction.z-delta.z*fang.direction.x);if(front>=-.5&&front<=5+target.getBbWidth()*.5&&side<=.75+target.getBbWidth()*.5&&SkillController.INSTANCE.sight(fang.origin,target.getBoundingBox().getCenter()))MageClient.INSTANCE.issue(c,target,c.parameters.get("damage",0),-2,"","");}MageClient.INSTANCE.finished(c);}
        for(var f:new ArrayList<>(flights)){
            var c=f.cast;if(!MageClient.INSTANCE.valid(c)){flights.remove(f);continue;}
            double u=Math.clamp((now-f.at)/f.duration,0,1);Vec3 next=f.origin.lerp(f.end,u);var wall=mc.level.clip(new ClipContext(f.position,next,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,mc.player));Vec3 end=wall.getType()==HitResult.Type.MISS?next:wall.getLocation(),from=f.position;
            var collided=SkillController.INSTANCE.targets(new AABB(from,end).inflate(.25)).stream().filter(e->!f.struck.contains(e.getUUID())&&SkillGeometry.intersects(e.getBoundingBox().inflate(.15),from,end)).sorted(Comparator.comparingDouble(e->e.distanceToSqr(from))).toList();boolean done=false;
            for(var target:collided){f.struck.add(target.getUUID());f.position=target.getBoundingBox().inflate(.15).clip(from,end).orElse(from);if(c.skill.projectile.explodes()){done=true;break;}MageClient.INSTANCE.issue(c,target,c.parameters.get("damage",0),-2,"","");if(++f.hits>=c.skill.projectile.pierces()){done=true;break;}}
            if(!done)f.position=end;
            if(done||wall.getType()!=HitResult.Type.MISS||u>=1){if(c.skill.projectile.explodes())for(var target:SkillController.INSTANCE.targets(new AABB(f.position,f.position).inflate(c.parameters.get("radius",3)))){Vec3 contact=c.skill.projectile.boundingBoxBlast()?nearest(target,f.position):target.getBoundingBox().getCenter();if(contact.distanceToSqr(f.position)<=Math.pow(c.parameters.get("radius",3),2)&&SkillController.INSTANCE.sight(f.position.add(0,.1,0),contact))MageClient.INSTANCE.issue(c,target,c.parameters.get("damage",0),-2,"","");}MageClient.INSTANCE.visual(c,c.id,"FINISH",f.position,c.direction,(long)Math.ceil(f.duration)+c.skill.projectile.impactMillis(),++f.sequence,null);flights.remove(f);MageClient.INSTANCE.finished(c);}
        }
    }
    private static Vec3 summonPoint(SummonKind kind,int slot,List<Vec3> reserved){
        var p=Minecraft.getInstance().player;var entity=new SummonedCreature(SkillContentTypes.SUMMONS.get(kind).get(),p.level());
        for(int r=1;r<=5;r++)for(int i=0;i<16;i++){double angle=(i+slot)*Math.PI/8;Vec3 origin=p.position().add(Math.cos(angle)*r,1,Math.sin(angle)*r);var floor=p.level().clip(new ClipContext(origin,origin.add(0,-5,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));if(floor.getType()==HitResult.Type.MISS)continue;Vec3 point=floor.getLocation().add(0,.01,0);if(!p.level().hasChunkAt(BlockPos.containing(point))||!p.level().getWorldBorder().isWithinBounds(BlockPos.containing(point))||reserved.stream().anyMatch(v->v.distanceToSqr(point)<2))continue;var box=entity.getBoundingBox().move(point.subtract(entity.position()));if(p.level().noCollision(box)&&!p.level().containsAnyLiquid(box))return point;}
        throw new IllegalArgumentException("附近没有足够的安全召唤空间");
    }
    private static void summon(SkillCast c){
        var state=SkillsClient.current();var p=Minecraft.getInstance().player;var packet=new JsonObject();packet.addProperty("session",state.session());packet.addProperty("generation",state.own().generation);packet.addProperty("id",c.id);packet.addProperty("skill",c.skill.id);packet.addProperty("rank",c.parameters.rank());packet.addProperty("level",state.progress().level);packet.addProperty("dimension",c.dimension);packet.addProperty("instance",c.instance);packet.addProperty("job",c.job);packet.addProperty("at",state.serverNow());
        if(c.skill.id.equals(SummonerSkills.FRENZY)){MageClient.INSTANCE.frenzyUntil=state.serverNow()+20000;packet.addProperty("until",MageClient.INSTANCE.frenzyUntil);packet.addProperty("power",.20+.03*(c.parameters.rank()-1));state.send("summonFrenzy",packet);MageClient.INSTANCE.finished(c);return;}
        JsonArray actors=new JsonArray();var reserved=new ArrayList<Vec3>();var points=prepared.remove(c.skill.id);int mastery=SkillLearning.effective(c.stats,state.progress(),SkillCatalog.require(SummonerSkills.MASTERY));
        for(var kind:SummonerSkills.kinds(c.skill.id)){
            Vec3 point=points==null?summonPoint(kind,reserved.size(),reserved):points.get(reserved.size());reserved.add(point);var inherited=SummonerSkills.inherit(c.stats,kind,mastery);var effects=c.equipment.copy();effects.killRecovery=0;effects.heroSource="";effects.heroDuration=0;effects.goldenSource="";effects.imprintSource="";effects.manaShare=0;
            var actor=new JsonObject();actor.addProperty("id",UUID.randomUUID().toString());actor.addProperty("kind",kind.name());actor.add("position",JSON.toJsonTree(new double[]{point.x,point.y,point.z}));actor.add("stats",JSON.toJsonTree(inherited));actor.add("effects",JSON.toJsonTree(effects));actor.add("parameters",JSON.toJsonTree(c.parameters.values()));
            var melee=SummonRules.attack(inherited,c.parameters.get("damage",0));var stone=SummonRules.attack(inherited,c.parameters.get("stoneDamage",0));var meleeDamage=SkillPreview.damage(state.progress(),melee,c.skill,1,false,effects);var stoneDamage=SkillPreview.damage(state.progress(),stone,c.skill,1,false,effects);
            actor.add("damage",JSON.toJsonTree(Map.of("melee",meleeDamage,"stone",stoneDamage,"triple",stoneDamage.times(3))));actor.addProperty("health",inherited.get(Stat.HEALTH));actor.addProperty("shield",Math.max(0,inherited.get(Stat.SHIELD)));
            long at=state.serverNow()+(long)((kind==SummonKind.KASIJAS?1000:400)/c.speed);actor.addProperty("at",at);actor.addProperty("until",at+(kind==SummonKind.KASIJAS?20000:120000));actors.add(actor);
        }
        packet.add("actors",actors);state.send("summonSpawn",packet);MageClient.INSTANCE.finished(c);
    }
    static void action(JsonObject j){
        var state=SkillsClient.current();var mc=Minecraft.getInstance();var c=new SkillCast();c.id=c.summonSource=j.get("source").getAsString();c.session=j.get("session").getAsString();c.generation=j.get("generation").getAsLong();c.job=j.get("job").getAsString();c.dimension=j.get("dimension").getAsString();c.instance=j.get("instance").getAsString();c.skill=SkillCatalog.require(j.get("skill").getAsString());
        var values=new LinkedHashMap<String,Double>();j.getAsJsonObject("parameters").entrySet().forEach(e->values.put(e.getKey(),e.getValue().getAsDouble()));c.parameters=new SkillParameters.Snapshot(c.skill,j.get("rank").getAsInt(),values);
        var move=SummonMoves.require(j.get("move").getAsString());c.stats=SummonRules.attack(JSON.fromJson(j.get("stats"),Stats.class),c.parameters.get(j.get("stone").getAsBoolean()?"stoneDamage":"damage",0)*j.get("share").getAsDouble());c.stats.modifiers.put("summonElement",(double)move.element());c.stats.modifier("final").mut+=j.get("frenzyPower").getAsDouble();c.equipment=JSON.fromJson(j.get("effects"),cn.yumis.fight.modules.equipment.api.EquipmentEffects.Snapshot.class);c.summonSlow=move.slow();c.elementOverride=move.element();c.origin=ProjectileSpawn.vector(j.getAsJsonArray("origin"));c.center=c.origin;Vec3 aim=ProjectileSpawn.vector(j.getAsJsonArray("aim"));c.direction=ProjectileSpawn.vector(j.getAsJsonArray("axis")).multiply(1,0,1).normalize();c.start=ClientUi.now()-Math.max(0,state.serverNow()-j.get("at").getAsLong());c.summonMove=move.id()+"_impact";
        var actor=mc.level.getEntity(j.get("actorEntity").getAsInt());if(actor!=null&&actor.getStringUUID().equals(j.get("actor").getAsString()))c.origin=actor.getEyePosition();
        if(move.shape().equals("projectile")||move.shape().equals("meteor")){
            var f=new SummonFlight();f.cast=c;f.move=move;f.visual=j.get("id").getAsString();f.from=move.shape().equals("meteor")?aim.add(0,6,0):c.origin;double distance=move.pierces()>1&&move.radius()==0?move.range():Math.min(move.range(),f.from.distanceTo(aim));f.to=move.shape().equals("meteor")?aim:f.from.add(aim.subtract(f.from).normalize().scale(distance));f.position=f.from;f.at=c.start;f.duration=Math.max(50,Math.round(f.from.distanceTo(f.to)/move.velocity()*1000));f.arc=move.id().equals("stone")||move.id().equals("triple_throw")?.5:move.id().equals("bomb")?1:0;summonFlights.add(f);c.summonMove=move.id()+"_flight";
            var motion=new JsonObject();motion.addProperty("velocity",move.velocity());motion.addProperty("range",f.from.distanceTo(f.to));motion.addProperty("millis",f.duration);motion.addProperty("arcHeight",f.arc);MageClient.INSTANCE.visual(c,f.visual,"START",f.from,f.to.subtract(f.from).normalize(),f.duration+500,0,motion);TrainingCombat.hold(c.id,f.visual,ClientUi.now()+f.duration+5000);return;
        }
        double reach=j.get("reach").getAsDouble();
        if(move.shape().equals("area"))summonArea(c,aim,move.radius());
        else if(move.shape().equals("melee")){var target=mc.level.getEntity(UUID.fromString(j.get("target").getAsString()));if(target instanceof LivingEntity living){var context=state.context(living);if(context!=null&&context.life.generation==j.get("targetGeneration").getAsLong()&&living.position().distanceToSqr(c.origin.add(0,-(actor==null?1:actor.getEyeHeight()),0))<=move.range()*move.range()&&SkillController.INSTANCE.sight(c.origin,living.getBoundingBox().getCenter()))contact(c,living,1);}}
        else{
            if(c.direction.lengthSqr()<.01)c.direction=actor==null?new Vec3(0,0,1):actor.getLookAngle().multiply(1,0,1).normalize();if(move.shape().equals("beam")){var wall=mc.level.clip(new ClipContext(c.origin,c.origin.add(c.direction.scale(reach)),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,mc.player));if(wall.getType()!=HitResult.Type.MISS)reach=Math.min(reach,c.origin.distanceTo(wall.getLocation()));}
            int count=0;for(var target:SkillController.INSTANCE.targets(new AABB(c.origin,c.origin).inflate(move.range()+2)).stream().sorted(Comparator.comparingDouble(t->t.distanceToSqr(c.origin))).toList()){Vec3 delta=target.getBoundingBox().getCenter().subtract(c.origin);double forward=delta.dot(c.direction);boolean inside=move.shape().equals("cone")?delta.length()<=reach+target.getBbWidth()/2&&delta.normalize().dot(c.direction)>=.5:forward>=0&&forward<=reach&&delta.subtract(c.direction.scale(forward)).length()<=move.width()/2+target.getBbWidth()/2;if(inside&&SkillController.INSTANCE.sight(c.origin,target.getBoundingBox().getCenter())){contact(c,target,1);if(++count>=move.pierces())break;}}
        }
        MageClient.INSTANCE.visual(c,j.get("id").getAsString(),"START",move.shape().equals("area")?aim:c.origin,c.direction,500,0,null);
    }
    static boolean contact(SkillCast c,LivingEntity target,double coefficient){
        if(SkillsClient.current().context(target)!=null)return SkillController.INSTANCE.hitCast(c,target,coefficient,0);String hold="contact/"+UUID.randomUUID();long until=ClientUi.now()+5000;contacts.add(new Contact(c,target,coefficient,until,hold));TrainingCombat.hold(c.id,hold,until);return true;
    }
    private static void summonArea(SkillCast c,Vec3 center,double radius){for(var target:SkillController.INSTANCE.targets(new AABB(center,center).inflate(radius)))if(target.getBoundingBox().getCenter().distanceToSqr(center)<=radius*radius&&SkillController.INSTANCE.sight(center,target.getBoundingBox().getCenter()))contact(c,target,1);}
    private static void tickSummons(long now){
        var mc=Minecraft.getInstance();for(var pending:new ArrayList<>(contacts)){if(!MageClient.INSTANCE.valid(pending.cast)||!pending.target.isAlive()||now>pending.until){contacts.remove(pending);TrainingCombat.release(pending.cast.id,pending.hold);continue;}if(SkillsClient.current().context(pending.target)!=null){SkillController.INSTANCE.hitCast(pending.cast,pending.target,pending.coefficient,0);contacts.remove(pending);TrainingCombat.release(pending.cast.id,pending.hold);}}
        for(var f:new ArrayList<>(summonFlights)){
            if(!MageClient.INSTANCE.valid(f.cast)){summonFlights.remove(f);TrainingCombat.release(f.cast.id,f.visual);continue;}var move=f.move;double u=Math.clamp((now-f.at)/(double)f.duration,0,1),previous=f.progress;f.progress=u;boolean done=false;int steps=Math.max(1,(int)Math.ceil(f.from.distanceTo(f.to)*(u-previous)/.25));
            for(int step=1;step<=steps&&!done;step++){double t=previous+(u-previous)*step/steps;Vec3 next=f.from.lerp(f.to,t).add(0,4*f.arc*t*(1-t),0);var wall=mc.level.clip(new ClipContext(f.position,next,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,mc.player));Vec3 end=wall.getType()==HitResult.Type.MISS?next:wall.getLocation(),from=f.position;
                for(var target:SkillController.INSTANCE.targets(new AABB(from,end).inflate(move.width())).stream().filter(x->!f.struck.contains(x.getUUID())&&SkillGeometry.intersects(x.getBoundingBox().inflate(move.width()/2),from,end)).sorted(Comparator.comparingDouble(x->x.distanceToSqr(from))).toList()){f.position=target.getBoundingBox().inflate(move.width()/2).clip(from,end).orElse(end);f.struck.add(target.getUUID());if(move.radius()>0){done=true;break;}contact(f.cast,target,1);if(++f.hits>=move.pierces()){done=true;break;}}
                if(!done)f.position=end;done|=wall.getType()!=HitResult.Type.MISS;
            }
            if(done||u>=1){if(move.radius()>0)summonArea(f.cast,f.position,move.radius());MageClient.INSTANCE.visual(f.cast,f.visual,"FINISH",f.position,f.to.subtract(f.from).normalize(),f.duration+500,++f.sequence,null);summonFlights.remove(f);TrainingCombat.release(f.cast.id,f.visual);}
        }
    }
    static void cancel(String id){flights.removeIf(f->f.cast.id.equals(id));fangs.removeIf(f->f.cast.id.equals(id));}
    static void clear(){for(var contact:contacts)TrainingCombat.release(contact.cast.id,contact.hold);for(var flight:summonFlights)TrainingCombat.release(flight.cast.id,flight.visual);contacts.clear();summonFlights.clear();flights.clear();fangs.clear();prepared.clear();}
}
