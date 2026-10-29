package cn.yumis.fight.modules.skills.application;

import cn.yumis.fight.architecture.CommandRouter;
import cn.yumis.fight.foundation.Journal;
import cn.yumis.fight.modules.attributes.api.*;
import cn.yumis.fight.modules.character.api.*;
import cn.yumis.fight.modules.combat.api.*;
import cn.yumis.fight.modules.dungeons.api.Target;
import cn.yumis.fight.modules.equipment.api.EquipmentEffects;
import cn.yumis.fight.modules.skills.api.*;
import cn.yumis.fight.network.Network;
import com.google.gson.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import net.minecraft.core.BlockPos;
import java.util.*;
import java.util.function.*;

/** Skill-owned projectiles and summon action receipts. The owner still calculates every hit. */
public final class MageSkillService implements CombatPorts.SkillEffects,HerderActions.Handler {
    private static final Gson JSON=new GsonBuilder().disableHtmlEscaping().create();
    private final MinecraftServer server;private final Journal journal;
    private final Function<ServerPlayer,CombatSession> sessions;private final Function<ServerPlayer,Progress> progress;
    private final Function<LivingEntity,Target> targets;private final BiConsumer<ServerPlayer,JsonObject> visuals;
    private final Predicate<ServerPlayer> encounterSync;
    private final Map<String,Source> sources=new HashMap<>();private final Map<UUID,Source> summons=new HashMap<>();
    private final Map<UUID,Herder> prepared=new HashMap<>();private final Map<String,Attack> attacks=new HashMap<>();
    private final Map<String,Incoming> incoming=new HashMap<>();private final List<Flight> flights=new ArrayList<>();private final List<Scheduled> scheduled=new ArrayList<>();
    private final Map<UUID,UUID> preferred=new HashMap<>();
    private static final class Source {
        String id,session,dimension,instance;UUID owner;long generation,expires,started,nextMelee,nextStone,busy,lastHurt,lastTick,lastSync;int rank,level,stuck;
        SkillDefinition skill;SkillParameters.Snapshot parameters;Stats stats,meleeStats,stoneStats;EquipmentEffects.Snapshot effects;Vec3 origin,direction,previous;Herder entity;
        double health,shield,maximum,maximumShield;SkillPreview.Damage meleeDamage,stoneDamage,tripleDamage;
        final Map<String,Long> environment=new HashMap<>();
        boolean summon(){return skill.id.equals(MageSkills.HERDER);}
    }
    private static final class Attack {String id;Source source;UUID target;long generation,expires;Stats stats;double coefficient;boolean consumed;Vec3 point;}
    private record Incoming(String id,Source source,long occurred,long expires,double damage,boolean direct){}
    private record Scheduled(Source source,UUID target,long due,boolean stone){}
    private static final class Flight {Source source;String visual;Vec3 origin,end,position;long start,duration;boolean stone;int hits,sequence;final Set<UUID> struck=new HashSet<>();}
    public MageSkillService(MinecraftServer server,Journal journal,Function<ServerPlayer,CombatSession> sessions,Function<ServerPlayer,Progress> progress,Function<LivingEntity,Target> targets,BiConsumer<ServerPlayer,JsonObject> visuals,Predicate<ServerPlayer> encounterSync){this.server=server;this.journal=journal;this.sessions=sessions;this.progress=progress;this.targets=targets;this.visuals=visuals;this.encounterSync=encounterSync;}
    public void register(CommandRouter<ServerPlayer,JsonObject> router){router.register("skills","herderReceive",this::receive);}
    private ServerPlayer owner(Source source){return server.getPlayerList().getPlayer(source.owner);}
    private boolean valid(Source source){var p=owner(source);if(p==null||source.expires<System.currentTimeMillis()||p.isDeadOrDying()||source.summon()&&(source.entity==null||source.entity.isRemoved()))return false;var s=sessions.apply(p);return !s.life.dead&&s.id.toString().equals(source.session)&&s.life.generation==source.generation&&s.life.instance.equals(source.instance)&&p.level().dimension().identifier().toString().equals(source.dimension);}
    private static boolean enemy(LivingEntity e){return e.isAlive()&&(e instanceof Enemy||e instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon);}
    private static Vec3 vector(JsonArray a){return new Vec3(a.get(0).getAsDouble(),a.get(1).getAsDouble(),a.get(2).getAsDouble());}
    private static boolean sight(ServerPlayer p,Vec3 from,Vec3 to){return p.level().clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p)).getType()==HitResult.Type.MISS;}
    private static Vec3 safe(ServerPlayer p){
        for(int z=-2;z<=2;z++)for(int x=-2;x<=2;x++){Vec3 point=p.position().add(x,0,z);var hit=p.level().clip(new ClipContext(point.add(0,1,0),point.add(0,-3,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));if(hit.getType()==HitResult.Type.MISS)continue;point=hit.getLocation().add(0,.01,0);BlockPos block=BlockPos.containing(point);if(!p.level().hasChunkAt(block)||!p.level().getWorldBorder().isWithinBounds(block))continue;var box=new AABB(point.x-.4,point.y,point.z-.4,point.x+.4,point.y+1.35,point.z+.4);if(p.level().noCollision(box)&&!p.level().containsAnyLiquid(box))return point;}
        throw new IllegalArgumentException("附近没有可召唤赫尔德的安全地面");
    }
    public void preflight(ServerPlayer p,SkillDefinition skill){if(!skill.id.equals(MageSkills.HERDER))return;var entity=new Herder(SkillContentTypes.HERDER.get(),p.level());entity.setPos(safe(p));prepared.put(p.getUUID(),entity);}
    public void accepted(ServerPlayer p,JsonObject ack){
        var skill=SkillCatalog.require(ack.get("skill").getAsString());if(!MageSkills.contains(skill.id))return;
        Source source=new Source();source.id=ack.get("id").getAsString();source.owner=p.getUUID();source.session=ack.get("session").getAsString();source.generation=ack.get("generation").getAsLong();source.dimension=p.level().dimension().identifier().toString();source.instance=sessions.apply(p).life.instance;source.skill=skill;source.rank=ack.get("rank").getAsInt();source.level=progress.apply(p).level;source.started=System.currentTimeMillis();source.expires=source.started+(source.summon()?120000:20000);
        var values=new LinkedHashMap<String,Double>();ack.getAsJsonObject("parameters").entrySet().forEach(e->values.put(e.getKey(),e.getValue().getAsDouble()));source.parameters=new SkillParameters.Snapshot(skill,source.rank,values);source.stats=JSON.fromJson(ack.get("stats"),Stats.class);source.stats.modifiers.put("sourceLevel",(double)source.level);source.effects=JSON.fromJson(ack.get("equipmentEffects"),EquipmentEffects.Snapshot.class);source.origin=vector(ack.getAsJsonArray("origin"));source.direction=vector(ack.getAsJsonArray("direction"));sources.put(source.id,source);ack.addProperty("instant",true);
        if(source.summon()){
            source.stats=HerderRules.inherit(source.stats);source.effects=source.effects.copy();source.effects.killRecovery=0;source.effects.heroSource="";source.effects.heroDuration=0;source.effects.goldenSource="";source.effects.imprintSource="";source.effects.manaShare=0;
            source.maximum=source.health=source.stats.get(Stat.HEALTH);source.maximumShield=source.shield=Math.max(0,source.stats.get(Stat.SHIELD));source.meleeStats=HerderRules.attack(source.stats,source.parameters.get("damage",0));source.stoneStats=HerderRules.attack(source.stats,source.parameters.get("stoneDamage",0));
            source.meleeDamage=SkillPreview.damage(progress.apply(p),source.meleeStats,skill,1,false,source.effects);source.stoneDamage=SkillPreview.damage(progress.apply(p),source.stoneStats,skill,1,false,source.effects);source.tripleDamage=source.stoneDamage.times(3);
            Herder entity=prepared.remove(p.getUUID());if(entity==null)throw new IllegalStateException("召唤预备状态缺失");source.entity=entity;entity.owner=p.getUUID();entity.setCustomName(cn.yumis.fight.modules.presentation.api.ModText.of("赫尔德 Lv."+source.rank));if(!p.level().addFreshEntity(entity))throw new IllegalStateException("无法创建召唤物");clear(p);summons.put(p.getUUID(),source);source.previous=entity.position();entity.project(1,source.maximumShield>0?1:0);sendState(source);return;
        }
        if(skill.id.equals(MageSkills.FANG)){source.direction=new Vec3(source.direction.x,0,source.direction.z).normalize();source.origin=p.position().add(0,.8,0);scheduled.add(new Scheduled(source,null,source.started+Math.max(1,(long)(200/Math.max(.01,source.stats.get(Stat.CAST_SPEED)))),false));visual(source,source.id,"START",source.origin,source.direction,600,0,null);}
        else launch(source,source.origin,source.origin.add(source.direction.scale(12)),false);
    }
    private void launch(Source source,Vec3 from,Vec3 destination,boolean stone){
        Flight flight=new Flight();flight.source=source;flight.visual=stone?UUID.randomUUID().toString():source.id;flight.origin=flight.position=from;flight.end=destination;flight.start=System.currentTimeMillis();flight.duration=stone?Math.max(150,(long)(from.distanceTo(destination)/16*1000)):source.skill.id.equals(MageSkills.STAR)?600:800;flight.stone=stone;flights.add(flight);
        JsonObject motion=new JsonObject();motion.addProperty("range",from.distanceTo(destination));motion.addProperty("millis",flight.duration);motion.addProperty("velocity",from.distanceTo(destination)/flight.duration*1000);motion.addProperty("arcHeight",stone||source.skill.id.equals(MageSkills.STAR)?0:1.5);
        visual(source,flight.visual,"START",from,destination.subtract(from).normalize(),flight.duration+500,0,motion);
    }
    private void visual(Source source,String id,String phase,Vec3 point,Vec3 direction,long duration,int sequence,JsonObject motion){
        var p=owner(source);if(p==null)return;long now=System.currentTimeMillis();var j=new JsonObject();j.addProperty("caster",p.getStringUUID());j.addProperty("session",source.session);j.addProperty("generation",source.generation);j.addProperty("dimension",source.dimension);j.addProperty("instance",source.instance);j.addProperty("id",id);j.addProperty("kind",source.skill.id);j.addProperty("phase",phase);j.addProperty("sequence",sequence);j.addProperty("seed",id.hashCode());j.addProperty("startedAt",now);j.addProperty("at",now);j.addProperty("serverNow",now);j.addProperty("duration",duration);j.addProperty("cosmeticOnly",true);j.addProperty("color",source.skill.id.equals(MageSkills.JACK)?0xff973c:0xbcefff);for(String axis:List.of("x","y","z")){double value=axis.equals("x")?point.x:axis.equals("y")?point.y:point.z;j.addProperty(axis,value);j.addProperty("p"+axis,value);j.addProperty("d"+axis,axis.equals("x")?direction.x:axis.equals("y")?direction.y:direction.z);}if(motion!=null)j.add("motion",motion);visuals.accept(p,j);Network.send(p,"visual",j);
    }
    private void issue(Source source,LivingEntity target,boolean stone,Vec3 point){
        if(!valid(source)||!enemy(target))return;var p=owner(source);Target targetState=targets.apply(target);if(targetState.life.dead)return;
        var attack=new Attack();attack.id=UUID.randomUUID().toString();attack.source=source;attack.target=target.getUUID();attack.generation=targetState.life.generation;attack.expires=System.currentTimeMillis()+5000;attack.point=point;attack.stats=source.summon()?(stone?source.stoneStats:source.meleeStats):source.stats;attack.coefficient=source.summon()?1:source.parameters.get("damage",0);attacks.put(attack.id,attack);
        JsonObject j=new JsonObject();j.addProperty("id",attack.id);j.addProperty("source",source.id);j.addProperty("session",source.session);j.addProperty("generation",source.generation);j.addProperty("skill",source.skill.id);j.addProperty("rank",source.rank);j.addProperty("target",target.getStringUUID());j.addProperty("entity",target.getId());j.addProperty("targetGeneration",attack.generation);j.addProperty("coefficient",attack.coefficient);j.addProperty("expires",attack.expires);j.addProperty("sourceExpires",source.expires);j.addProperty("summon",source.summon());j.add("stats",JSON.toJsonTree(attack.stats));j.add("effects",JSON.toJsonTree(source.effects));j.add("parameters",JSON.toJsonTree(source.parameters.values()));Network.send(p,"mageAttack",j);
    }
    public void tick(){
        long now=System.currentTimeMillis();
        for(var job:new ArrayList<>(scheduled))if(job.due<=now){scheduled.remove(job);if(!valid(job.source))continue;var p=owner(job.source);if(job.source.skill.id.equals(MageSkills.FANG)){
            Source s=job.source;for(var target:p.level().getEntitiesOfClass(LivingEntity.class,new AABB(s.origin,s.origin.add(s.direction.scale(5))).inflate(1,1.5,1),MageSkillService::enemy)){var delta=target.getBoundingBox().getCenter().subtract(s.origin);double forward=delta.dot(s.direction),side=Math.abs(delta.x*s.direction.z-delta.z*s.direction.x);if(forward>=-.5&&forward<=5+target.getBbWidth()*.5&&side<=.75+target.getBbWidth()*.5&&sight(p,s.origin,target.getBoundingBox().getCenter()))issue(s,target,false,target.position());}Network.send(p,"mageSourceEnd",Map.of("source",s.id));
        }else {var target=p.level().getEntity(job.target);if(target instanceof LivingEntity living&&enemy(living)&&job.source.entity!=null&&living.distanceToSqr(job.source.entity)<=256&&sight(p,job.source.entity.getEyePosition(),living.getBoundingBox().getCenter()))launch(job.source,job.source.entity.getEyePosition(),living.getBoundingBox().getCenter(),true);}}
        for(var flight:new ArrayList<>(flights)){
            Source s=flight.source;if(!valid(s)){flights.remove(flight);continue;}var p=owner(s);double u=Math.clamp((now-flight.start)/(double)flight.duration,0,1);Vec3 next=flight.origin.lerp(flight.end,u);if(!flight.stone&&s.skill.id.equals(MageSkills.JACK))next=next.add(0,6*u*(1-u),0);
            var wall=p.level().clip(new ClipContext(flight.position,next,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));Vec3 end=wall.getType()==HitResult.Type.MISS?next:wall.getLocation();Vec3 from=flight.position;
            var collided=p.level().getEntitiesOfClass(LivingEntity.class,new AABB(from,end).inflate(.25),e->enemy(e)&&!flight.struck.contains(e.getUUID())&&e.getBoundingBox().inflate(.15).clip(from,end).isPresent()).stream().sorted(Comparator.comparingDouble(e->e.distanceToSqr(from))).toList();boolean done=false;
            for(var target:collided){flight.struck.add(target.getUUID());flight.position=target.getBoundingBox().inflate(.15).clip(from,end).orElse(target.position());if(!flight.stone&&s.skill.id.equals(MageSkills.JACK)){done=true;break;}issue(s,target,flight.stone,flight.position);if(++flight.hits>=(flight.stone?1:3)){done=true;break;}}
            if(!done)flight.position=end;
            if(done||wall.getType()!=HitResult.Type.MISS||u>=1){if(!flight.stone&&s.skill.id.equals(MageSkills.JACK))for(var target:p.level().getEntitiesOfClass(LivingEntity.class,new AABB(flight.position,flight.position).inflate(3),MageSkillService::enemy))if(target.getBoundingBox().getCenter().distanceToSqr(flight.position)<=9&&sight(p,flight.position.add(0,.1,0),target.getBoundingBox().getCenter()))issue(s,target,false,flight.position);visual(s,flight.visual,"FINISH",flight.position,s.direction,flight.duration+500,++flight.sequence,null);flights.remove(flight);if(!s.summon())Network.send(p,"mageSourceEnd",Map.of("source",s.id));}
        }
        for(var source:new ArrayList<>(summons.values()))if(!valid(source)||source.entity==null||source.entity.isRemoved())clear(source.owner);
        attacks.values().removeIf(a->a.expires<now);incoming.values().removeIf(i->i.expires<now);sources.values().removeIf(s->s.expires<now);
    }
    @Override public void tick(Herder entity){
        var s=summons.get(entity.owner);if(s==null||s.entity!=entity||!valid(s)){entity.discard();return;}var p=owner(s);long now=System.currentTimeMillis();double speed=Math.max(.01,1+s.stats.get(Stat.ATTACK_SPEED)/100);double dt=s.lastTick==0?0:Math.min(.25,(now-s.lastTick)/1000d);s.lastTick=now;
        if(now-s.lastHurt>=5000&&s.shield<s.maximumShield)s.shield=Math.min(s.maximumShield,s.shield+s.maximumShield*.2*dt);entity.project(s.health/s.maximum,s.maximumShield>0?s.shield/s.maximumShield:0);
        if(now>s.busy)entity.action(0);if(entity.distanceToSqr(p)>24*24||s.stuck>=60){try{entity.getNavigation().stop();entity.setPos(safe(p));}catch(IllegalArgumentException ignored){}s.stuck=0;}
        LivingEntity target=null;UUID recent=preferred.get(p.getUUID());if(recent!=null&&p.level().getEntity(recent) instanceof LivingEntity living&&enemy(living)&&living.distanceToSqr(entity)<=256)target=living;
        if(target==null)target=p.level().getEntitiesOfClass(LivingEntity.class,entity.getBoundingBox().inflate(16),MageSkillService::enemy).stream().filter(e->e.distanceToSqr(entity)<=256).min(Comparator.comparingDouble(e->e.distanceToSqr(entity))).orElse(null);
        if(target!=null){entity.getLookControl().setLookAt(target);entity.setTarget(target);if(now>=s.busy&&s.rank>=5&&now>=s.nextStone&&sight(p,entity.getEyePosition(),target.getBoundingBox().getCenter())){
            int count=s.rank>=10?3:1;long gap=Math.max(10,(long)(200/speed));entity.action(2);s.nextStone=now+Math.max(50,(long)(4000/speed));s.busy=now+Math.max(Math.max(50,(long)(300/speed)),count*gap);s.nextMelee=s.busy;entity.getNavigation().stop();for(int i=0;i<count;i++)scheduled.add(new Scheduled(s,target.getUUID(),now+i*gap,true));
        }else if(now>=s.busy&&entity.distanceToSqr(target)<=4&&now>=s.nextMelee&&sight(p,entity.getEyePosition(),target.getBoundingBox().getCenter())){entity.action(1);s.nextMelee=now+Math.max(50,(long)(1200/speed));s.busy=now+Math.max(50,(long)(300/speed));entity.getNavigation().stop();issue(s,target,false,target.position());}
        else if(now>=s.busy&&entity.distanceToSqr(target)>4)entity.getNavigation().moveTo(target,Math.max(.01,s.stats.get(Stat.MOVEMENT_SPEED)));
        }else {entity.setTarget(null);if(entity.distanceToSqr(p)>9)entity.getNavigation().moveTo(p,Math.max(.01,s.stats.get(Stat.MOVEMENT_SPEED)));else entity.getNavigation().stop();}
        if(!entity.getNavigation().isDone()&&s.previous!=null&&entity.position().distanceToSqr(s.previous)<.0001)s.stuck++;else s.stuck=0;s.previous=entity.position();if(now-s.lastSync>=1000){sendState(s);s.lastSync=now;}
    }
    @Override public boolean incoming(Herder entity,ServerLevel level,DamageSource source,float points){
        Source s=summons.get(entity.owner);if(s==null||s.entity!=entity||!valid(s)||source.getEntity() instanceof Player||source.getEntity() instanceof Herder)return false;
        long now=System.currentTimeMillis();String kind=source.getMsgId();double damage;boolean direct=source.getEntity() instanceof LivingEntity attacker&&enemy(attacker)&&!Set.of("poison","wither","onFire","inFire","lava","thorns").contains(kind);var j=new JsonObject();
        if(direct&&source.getEntity() instanceof LivingEntity attacker&&enemy(attacker)){Target target=targets.apply(attacker);var player=owner(s);int at=target.level(progress.apply(player).level,encounterSync.test(player));double raw=target.attack(at);damage=HerderRules.incoming(s.stats,raw,false,-1,at,s.level,.65);j.addProperty("points",raw);j.addProperty("magic",false);j.addProperty("element",-1);j.addProperty("attackerLevel",at);j.addProperty("ownerLevel",s.level);j.addProperty("protection",.65);}
        else {
            long delay=switch(kind){case "lava","inFire","onFire","drown","inWall","outOfWorld","outsideBorder"->1000;case "cactus","sweetBerryBush","hotFloor","cramming","sting"->500;case "freeze"->2000;default->0;};if(delay>0&&s.environment.getOrDefault(kind,0L)>now)return false;if(delay>0)s.environment.put(kind,now+delay);
            double fraction=switch(kind){case "lava","outOfWorld","outsideBorder"->.2;case "drown","fallingBlock","anvil","fallingStalactite"->.1;case "lightningBolt"->.25;case "cramming","magic"->.3;case "fall"->Math.max(0,(entity.fallDistance-3)*.05);case "stalagmite"->Math.max(0,(entity.fallDistance-1)*.1);case "trident"->.4;case "thorns"->Math.max(0,points)*.05;case "arrow","mobProjectile"->Math.ceil(Math.max(0,source.getDirectEntity() instanceof net.minecraft.world.entity.projectile.Projectile projectile?projectile.getDeltaMovement().length()*2:2))*.05;case "explosion","badRespawnPoint","fireworks"->{Vec3 origin=source.getSourcePosition();double f=origin==null?0:1-entity.position().distanceTo(origin)/8;yield f<=0?0:((f*f+f)*.5*7*8+1)*.05;}default->.05;};
            if(kind.equals("poison"))fraction=Math.min(fraction,Math.max(0,s.health/s.maximum-.001));if(Set.of("inFire","onFire","lava").contains(kind)&&entity.hasEffect(net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE)||kind.equals("drown")&&entity.hasEffect(net.minecraft.world.effect.MobEffects.WATER_BREATHING))fraction=0;damage=s.maximum*fraction;j.addProperty("environmentFraction",fraction);
        }
        if(!Double.isFinite(damage)||damage<=0)return false;String id=UUID.randomUUID().toString();incoming.put(id,new Incoming(id,s,now,now+5000,damage,direct));j.addProperty("root",id);j.addProperty("source",s.id);j.addProperty("session",s.session);j.addProperty("generation",s.generation);j.addProperty("direct",direct);j.add("stats",JSON.toJsonTree(s.stats));Network.send(owner(s),"herderIncoming",j);return true;
    }
    private void receive(ServerPlayer p,JsonObject j){
        String root=j.get("root").getAsString(),key="herderReceipt/"+p.getUUID()+"/"+sessions.apply(p).id+"/"+root;if(!journal.get(key).isJsonNull())return;Incoming hit=incoming.get(root);if(hit==null||hit.expires<System.currentTimeMillis()||!valid(hit.source)||!hit.source.owner.equals(p.getUUID())||!hit.source.id.equals(j.get("source").getAsString()))throw new IllegalArgumentException("召唤物承伤来源已失效");
        Source s=hit.source;boolean dodge=j.get("dodge").getAsBoolean();double damage=j.get("damage").getAsDouble();if(!Double.isFinite(damage)||dodge&&(!hit.direct||s.stats.get(Stat.EVASION)<=0||damage!=0)||!dodge&&Math.abs(damage-hit.damage)>Math.max(.000001,hit.damage*.0000001))throw new IllegalArgumentException("召唤物承伤数值无效");
        double shield=Math.max(0,s.shield-damage),health=Math.max(0,s.health-Math.max(0,damage-s.shield));try{journal.begin();journal.put(key,Map.of("source",s.id,"health",health,"shield",shield));journal.finish();}catch(Exception failure){journal.abort();throw new IllegalStateException(failure);}incoming.remove(root);s.health=health;s.shield=shield;if(!dodge&&damage>0)s.lastHurt=Math.max(s.lastHurt,hit.occurred);if(health<=0)clear(p);else {entityFeedback(s);sendState(s);}
    }
    private void entityFeedback(Source s){s.entity.project(s.health/s.maximum,s.maximumShield>0?s.shield/s.maximumShield:0);}
    private void sendState(Source s){var p=owner(s);if(p!=null){var view=JSON.toJsonTree(Map.of("session",s.session,"generation",s.generation,"source",s.id,"until",s.expires,"health",s.health,"maximum",s.maximum,"shield",s.shield,"maximumShield",s.maximumShield)).getAsJsonObject();view.addProperty("rank",s.rank);view.add("damage",JSON.toJsonTree(Map.of("melee",s.meleeDamage,"stone",s.stoneDamage,"triple",s.tripleDamage)));Network.send(p,"herderState",view);}}
    public void clear(ServerPlayer p){prepared.remove(p.getUUID());clear(p.getUUID());}
    public void reduced(ServerPlayer p,String skill){if(skill.equals(MageSkills.HERDER))clear(p);}
    public void cancelAccepted(ServerPlayer p,String id){Source source=sources.remove(id);if(source!=null){source.expires=0;if(summons.get(p.getUUID())==source)clear(p);if(source.entity!=null)source.entity.discard();}prepared.remove(p.getUUID());}
    private void clear(UUID owner){Source s=summons.remove(owner);if(s==null)return;s.expires=0;if(s.entity!=null)s.entity.discard();var p=server.getPlayerList().getPlayer(owner);if(p!=null)Network.send(p,"herderState",Map.of("session",s.session,"generation",s.generation,"source",s.id,"until",0));}
    private Attack attack(ServerPlayer p,JsonObject j){if(!j.has("mageAttack"))return null;Attack a=attacks.get(j.get("mageAttack").getAsString());return a!=null&&a.source.owner.equals(p.getUUID())?a:null;}
    @Override public Stats sourceStats(ServerPlayer p,JsonObject j){Attack a=attack(p,j);return a==null?null:a.stats;}
    @Override public EquipmentEffects.Snapshot sourceEffects(ServerPlayer p,JsonObject j){Attack a=attack(p,j);return a==null?null:a.source.effects;}
    @Override public boolean direct(ServerPlayer p,JsonObject j){Attack a=attack(p,j);return a==null||!a.source.summon();}
    @Override public void validate(ServerPlayer p,LivingEntity target,JsonObject j){
        if(!j.has("mageAttack"))return;Attack a=attack(p,j);if(a==null||a.consumed||a.expires<System.currentTimeMillis()||!valid(a.source)||!a.target.equals(target.getUUID())||a.generation!=j.get("generation").getAsLong()||!a.source.id.equals(j.get("mageSource").getAsString()))throw new IllegalArgumentException("魔法师攻击来源、目标或阶段已失效");
    }
    @Override public CombatPorts.Commit prepare(ServerPlayer p,LivingEntity target,JsonObject j,Receipt receipt,Life actor){
        Attack a=attack(p,j);if(!receipt.accepted())return CombatPorts.Commit.NONE;
        return new CombatPorts.Commit(){public void publish(){if(a!=null)a.consumed=true;if(target!=p&&receipt.actual()>0&&enemy(target)){preferred.put(p.getUUID(),target.getUUID());if(a!=null&&a.source.summon()&&target instanceof Mob mob&&!targets.apply(target).kind.equals("BOSS")&&!targets.apply(target).kind.equals("WITHER")){mob.setLastHurtByMob(a.source.entity);mob.setTarget(a.source.entity);}}}};
    }
}
