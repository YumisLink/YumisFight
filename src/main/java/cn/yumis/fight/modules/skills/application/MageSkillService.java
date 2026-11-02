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
public final class MageSkillService implements CombatPorts.SkillEffects,SummonActions.Handler {
    private static final Gson JSON=new GsonBuilder().disableHtmlEscaping().create();
    private final MinecraftServer server;private final Journal journal;
    private final Function<ServerPlayer,CombatSession> sessions;private final Function<ServerPlayer,Progress> progress;
    private final Function<LivingEntity,Target> targets;private final BiConsumer<ServerPlayer,JsonObject> visuals;
    private final Predicate<ServerPlayer> encounterSync;
    private final Map<String,Source> sources=new HashMap<>();private final Map<UUID,Source> summons=new HashMap<>();
    private final Map<UUID,List<SummonedCreature>> prepared=new HashMap<>();private final Map<String,Attack> attacks=new HashMap<>();
    private final Map<String,Incoming> incoming=new HashMap<>();private final List<Flight> flights=new ArrayList<>();private final List<Scheduled> scheduled=new ArrayList<>();
    private final List<Pulse> pulses=new ArrayList<>();
    private final List<SummonFlight> summonFlights=new ArrayList<>();
    private final Map<UUID,Source> chants=new HashMap<>();
    private final Map<String,Function<Source,Runnable>> spellHandlers=new HashMap<>();
    private final List<LongConsumer> contentTicks=new ArrayList<>();
    private final List<Consumer<UUID>> contentClears=new ArrayList<>();
    private final Set<String> resetOnReduction=new HashSet<>();
    private Function<ServerPlayer,SunMoonState> heartState=p->new SunMoonState();
    private BiConsumer<ServerPlayer,SunMoonState> heartStateWriter=(p,s)->{};
    private Consumer<ServerPlayer> heartSync=p->{};
    public void heartStates(Function<ServerPlayer,SunMoonState> read,BiConsumer<ServerPlayer,SunMoonState> write,Consumer<ServerPlayer> sync){heartState=read;heartStateWriter=write;heartSync=sync;}
    void spell(String id,Function<Source,Runnable> release){spellHandlers.put(id,release);resetOnReduction.add(id);}
    void contentLifecycle(LongConsumer tick,Consumer<UUID> clear){contentTicks.add(tick);contentClears.add(clear);}
    public Stats castStats(ServerPlayer p,SkillDefinition skill,Stats source){return SunMoonRules.forCast(source,skill);}
    public SkillStartService.Payment payment(ServerPlayer p,SkillDefinition skill,Stats stats,JsonObject ack){
        if(skill.chantSeconds>0)ack.addProperty("chantUntil",ack.get("serverNow").getAsLong()+Math.max(1,Math.round(skill.chantSeconds*1000/Math.max(.01,stats.get(Stat.CAST_SPEED)))));
        if(!SunMoonSkills.contains(skill.id))return SkillStartService.Payment.NONE;
        SunMoonState before=heartState.apply(p).copy(),next=SunMoonRules.paid(before,skill,stats);String key="skillState/sunmoon/"+p.getUUID()+"/"+sessions.apply(p).id;
        return new SkillStartService.Payment(Map.of(key,JSON.toJsonTree(next)),Map.of(key,JSON.toJsonTree(before)),()->{heartStateWriter.accept(p,next);heartSync.accept(p);},()->{heartStateWriter.accept(p,before);heartSync.accept(p);});
    }
    public boolean chanting(ServerPlayer p){return chants.containsKey(p.getUUID());}
    public void cancelChant(ServerPlayer p,String id){var source=chants.get(p.getUUID());if(source==null||!id.isEmpty()&&!source.castId.equals(id))return;chants.remove(p.getUUID());source.cancelled=true;sources.remove(source.id);ChantRules.movement(p,false);chantEnded(p,source);}
    private void chantEnded(ServerPlayer p,Source source){Network.send(p,"chantState",Map.of("id",source.castId,"session",source.session,"generation",source.generation,"active",false,"serverNow",System.currentTimeMillis()));}
    private void released(Source source){
        if(!SunMoonSkills.contains(source.skill.id))return;var p=owner(source);String key="skillRelease/"+p.getUUID()+"/"+source.session+"/"+source.id;if(!journal.get(key).isJsonNull())return;
        boolean awakened=SkillLearning.effective(source.stats,progress.apply(p),SkillCatalog.require(SunMoonSkills.PARADOX))>0;
        SunMoonState next=SunMoonRules.released(heartState.apply(p),source.skill,awakened);try{journal.transaction(Map.of(key,JSON.toJsonTree(next),"skillState/sunmoon/"+p.getUUID()+"/"+source.session,JSON.toJsonTree(next)));}catch(java.io.IOException failure){throw new IllegalStateException("日月状态提交失败",failure);}heartStateWriter.accept(p,next);heartSync.accept(p);
    }
    private void tickChants(long now){
        for(var source:new ArrayList<>(chants.values())){var p=owner(source);if(p==null){chants.remove(source.owner);source.cancelled=true;continue;}
            var hand=cn.yumis.fight.modules.equipment.api.EquipmentItems.gear(p.getMainHandItem());
            if(!valid(source)||p.hasEffect(CombatContentTypes.STUN)||hand==null||!hand.instance.toString().equals(source.weapon)||!hand.valid(progress.apply(p),cn.yumis.fight.modules.equipment.api.EquipmentEligibility.allowance(p))||progress.apply(p).level<source.skill.unlock||SkillLearning.effective(source.stats,progress.apply(p),source.skill)<=0){cancelChant(p,source.id);continue;}
            if(now<source.chantUntil)continue;chants.remove(p.getUUID());ChantRules.movement(p,false);chantEnded(p,source);
            try{release(p,source);}catch(RuntimeException failure){source.cancelled=true;sources.remove(source.id);p.sendOverlayMessage(net.minecraft.network.chat.Component.literal(Objects.toString(failure.getMessage(),"施放失败")));}
        }
    }
    long targetGeneration(LivingEntity target){return targets.apply(target).life.generation;}
    boolean targetAlive(LivingEntity target){return target.isAlive()&&!targets.apply(target).life.dead;}
    void finished(Source source){var p=owner(source);if(p!=null)Network.send(p,"mageSourceEnd",Map.of("source",source.id));}
    private record Pulse(Source source,SummonMoves.Move move,UUID target,long targetGeneration,Vec3 origin,Vec3 aim,Stats stats,long due){}
    private static final class SummonFlight {Pulse pulse;String id;Vec3 from,to,position;long start,duration;double arc,progress;int hits;final Set<UUID> struck=new HashSet<>();}
    private record Slow(LivingEntity target,Source source,long until){}
    private final Map<UUID,Slow> slows=new HashMap<>();
    private static final net.minecraft.resources.Identifier ICE_SLOW=net.minecraft.resources.Identifier.fromNamespaceAndPath("yumis_dungeon_and_fight","louise_needle_slow");
    private final Map<UUID,UUID> preferred=new HashMap<>();
    private record Frenzy(String cast,String session,long generation,String dimension,long until,double power){}
    private final Map<UUID,Frenzy> frenzy=new HashMap<>();private final Set<UUID> following=new HashSet<>();
    private final Map<SummonedCreature,Long> retiring=new HashMap<>();
    static final class Source {
        String id,session,dimension,instance;UUID owner;long generation,expires,started,busy,lastHurt,lastTick,lastSync;int rank,level,stuck,actionIndex;SummonKind kind;String castId;long guardUntil;List<SummonMoves.Cue> cues=List.of();
        SkillDefinition skill;SkillParameters.Snapshot parameters;Stats stats,meleeStats,stoneStats;EquipmentEffects.Snapshot effects;Vec3 origin,direction,previous;SummonedCreature entity;
        double health,shield,maximum,maximumShield;SkillPreview.Damage meleeDamage,stoneDamage,tripleDamage;
        final Map<String,Long> environment=new HashMap<>();
        boolean summon(){return kind!=null;}
        boolean naturalEnd;
        String job="",weapon="";long chantUntil;boolean cancelled;
    }
    private static final class Attack {String id;Source source;UUID target;long generation,created,expires;Stats stats;double coefficient;int element=-2;boolean consumed,slow;Vec3 point;}
    private record Incoming(String id,Source source,long occurred,long expires,double damage,boolean direct){}
    private record Scheduled(Source source,long due){}
    private static final class Flight {Source source;String visual;Vec3 origin,end,position;long start;double duration;int hits,sequence;final Set<UUID> struck=new HashSet<>();}
    public MageSkillService(MinecraftServer server,Journal journal,Function<ServerPlayer,CombatSession> sessions,Function<ServerPlayer,Progress> progress,Function<LivingEntity,Target> targets,BiConsumer<ServerPlayer,JsonObject> visuals,Predicate<ServerPlayer> encounterSync){this.server=server;this.journal=journal;this.sessions=sessions;this.progress=progress;this.targets=targets;this.visuals=visuals;this.encounterSync=encounterSync;SunMoonSpellService.install(this);}
    public void register(CommandRouter<ServerPlayer,JsonObject> router){router.register("skills","herderReceive",this::receive);router.register("skills","chantCancel",(p,j)->{if(j.has("session")&&j.get("session").getAsString().equals(sessions.apply(p).id.toString()))cancelChant(p,j.get("id").getAsString());});router.register("skills","summonFollow",(p,j)->{if(!progress.apply(p).hasJob(JobCatalog.SUMMONER))throw new IllegalArgumentException("需要召唤师职业");if(!following.add(p.getUUID()))following.remove(p.getUUID());});}
    ServerPlayer owner(Source source){return server.getPlayerList().getPlayer(source.owner);}
    boolean valid(Source source){var p=owner(source);if(source.cancelled||p==null||source.expires<System.currentTimeMillis()||p.isDeadOrDying()||source.summon()&&(source.entity==null||source.entity.isRemoved()))return false;var s=sessions.apply(p);return !s.life.dead&&s.id.toString().equals(source.session)&&s.life.generation==source.generation&&s.life.instance.equals(source.instance)&&progress.apply(p).jobId().equals(source.job)&&p.level().dimension().identifier().toString().equals(source.dimension);}
    private static boolean enemy(LivingEntity e){return e.isAlive()&&(e instanceof Enemy||e instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon);}
    static boolean eligible(ServerPlayer owner,LivingEntity e){return enemy(e)&&(!(e instanceof cn.yumis.fight.modules.dungeons.api.TrainingSandbag bag)||!bag.timed()||bag.ownedBy(owner)&&!bag.finished());}
    static Vec3 closest(LivingEntity target,Vec3 point){var box=target.getBoundingBox();return new Vec3(Math.clamp(point.x,box.minX,box.maxX),Math.clamp(point.y,box.minY,box.maxY),Math.clamp(point.z,box.minZ,box.maxZ));}
    private static Vec3 vector(JsonArray a){return new Vec3(a.get(0).getAsDouble(),a.get(1).getAsDouble(),a.get(2).getAsDouble());}
    private static boolean sight(ServerPlayer p,Vec3 from,Vec3 to){return p.level().clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p)).getType()==HitResult.Type.MISS;}
    private static Vec3 safe(ServerPlayer p,SummonedCreature entity,int slot,List<Vec3> reserved){
        for(int r=1;r<=5;r++)for(int i=0;i<16;i++){double a=(i+slot)*Math.PI/8;Vec3 origin=p.position().add(Math.cos(a)*r,1,Math.sin(a)*r);var hit=p.level().clip(new ClipContext(origin,origin.add(0,-5,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));if(hit.getType()==HitResult.Type.MISS)continue;Vec3 point=hit.getLocation().add(0,.01,0);var pos=BlockPos.containing(point);if(!p.level().hasChunkAt(pos)||!p.level().getWorldBorder().isWithinBounds(pos)||reserved.stream().anyMatch(v->v.distanceToSqr(point)<2))continue;var box=entity.getBoundingBox().move(point.subtract(entity.position()));if(p.level().noCollision(box)&&!p.level().containsAnyLiquid(box))return point;}
        throw new IllegalArgumentException("附近没有足够的安全召唤空间");
    }
    public void preflight(ServerPlayer p,SkillDefinition skill){
        cancelChant(p,"");
        var list=new ArrayList<SummonedCreature>();var reserved=new ArrayList<Vec3>();for(var k:SummonerSkills.kinds(skill.id)){var entity=new SummonedCreature(SkillContentTypes.SUMMONS.get(k).get(),p.level());var point=safe(p,entity,list.size(),reserved);entity.setPos(point);reserved.add(point);list.add(entity);}if(!list.isEmpty())prepared.put(p.getUUID(),list);
    }
    private Source source(ServerPlayer p,JsonObject ack){
        Source s=new Source();s.id=s.castId=ack.get("id").getAsString();s.owner=p.getUUID();s.session=ack.get("session").getAsString();s.generation=ack.get("generation").getAsLong();s.dimension=p.level().dimension().identifier().toString();s.instance=sessions.apply(p).life.instance;s.skill=SkillCatalog.require(ack.get("skill").getAsString());s.rank=ack.get("rank").getAsInt();s.level=progress.apply(p).level;s.started=System.currentTimeMillis();s.expires=Math.max(s.started,ack.has("chantUntil")?ack.get("chantUntil").getAsLong():s.started)+20000;s.job=progress.apply(p).jobId();
        var values=new LinkedHashMap<String,Double>();ack.getAsJsonObject("parameters").entrySet().forEach(e->values.put(e.getKey(),e.getValue().getAsDouble()));s.parameters=new SkillParameters.Snapshot(s.skill,s.rank,values);s.stats=JSON.fromJson(ack.get("stats"),Stats.class);s.stats.modifiers.put("sourceLevel",(double)s.level);s.weapon=s.stats.weaponInstance;s.effects=JSON.fromJson(ack.get("equipmentEffects"),EquipmentEffects.Snapshot.class);s.origin=vector(ack.getAsJsonArray("origin"));s.direction=vector(ack.getAsJsonArray("direction"));return s;
    }
    public void accepted(ServerPlayer p,JsonObject ack){
        var skill=SkillCatalog.require(ack.get("skill").getAsString());if(!MageSkills.contains(skill.id))return;ack.addProperty("instant",true);
        if(skill.id.equals(SummonerSkills.FRENZY)){var s=source(p,ack);frenzy.put(p.getUUID(),new Frenzy(s.id,s.session,s.generation,s.dimension,s.started+20000,.20+.03*(s.rank-1)));buffState(p,s.started+20000);return;}
        var kinds=SummonerSkills.kinds(skill.id);
        if(!kinds.isEmpty()){
            var entities=prepared.remove(p.getUUID());if(entities==null||entities.size()!=kinds.size())throw new IllegalStateException("召唤预备状态缺失");var created=new ArrayList<Source>();
            try{for(int i=0;i<kinds.size();i++){
                Source s=source(p,ack);s.kind=kinds.get(i);s.started+=(long)((s.kind==SummonKind.KASIJAS?1000:400)/Math.max(.01,s.stats.get(Stat.CAST_SPEED)));s.id=UUID.randomUUID().toString();s.expires=s.started+(s.kind==SummonKind.KASIJAS?20000:120000);s.entity=entities.get(i);s.entity.owner=p.getUUID();s.entity.setCustomName(cn.yumis.fight.modules.presentation.api.ModText.of(s.kind.title+" Lv."+s.rank));
                int mastery=SkillLearning.effective(s.stats,progress.apply(p),SkillCatalog.require(SummonerSkills.MASTERY));s.stats=SummonerSkills.inherit(s.stats,s.kind,mastery);s.effects=s.effects.copy();s.effects.killRecovery=0;s.effects.heroSource="";s.effects.heroDuration=0;s.effects.goldenSource="";s.effects.imprintSource="";s.effects.manaShare=0;
                s.maximum=s.health=s.stats.get(Stat.HEALTH);s.maximumShield=s.shield=Math.max(0,s.stats.get(Stat.SHIELD));s.meleeStats=SummonRules.attack(s.stats,s.parameters.get("damage",0));s.stoneStats=SummonRules.attack(s.stats,s.parameters.get("stoneDamage",0));
                s.meleeDamage=SkillPreview.damage(progress.apply(p),s.meleeStats,skill,1,false,s.effects);s.stoneDamage=SkillPreview.damage(progress.apply(p),s.stoneStats,skill,1,false,s.effects);s.tripleDamage=s.stoneDamage.times(3);
                s.cues=SummonMoves.cues(s.kind,s.rank,120000,Math.max(.01,1+s.stats.get(Stat.ATTACK_SPEED)/100));s.previous=s.entity.position();s.entity.project(1,s.maximumShield>0?1:0);s.entity.play("spawn",Math.max(1,s.started-System.currentTimeMillis()));s.busy=s.started;if(!p.level().addFreshEntity(s.entity))throw new IllegalStateException("无法创建召唤物");created.add(s);
            }}catch(RuntimeException failure){for(var e:entities)e.discard();throw failure;}
            for(var s:created){for(var old:new ArrayList<>(summons.values()))if(old.owner.equals(p.getUUID())&&old.kind==s.kind)remove(old);sources.put(s.id,s);summons.put(s.entity.getUUID(),s);sendState(s);}return;
        }
        Source source=source(p,ack);sources.put(source.id,source);
        source.chantUntil=ack.has("chantUntil")?ack.get("chantUntil").getAsLong():0;
        if(source.chantUntil>System.currentTimeMillis()){chants.put(p.getUUID(),source);ChantRules.movement(p,true);return;}
        try{release(p,source);}catch(IllegalArgumentException failure){source.cancelled=true;sources.remove(source.id);p.sendOverlayMessage(net.minecraft.network.chat.Component.literal(failure.getMessage()));}
    }
    private void release(ServerPlayer p,Source source){
        var skill=source.skill;source.origin=p.getEyePosition();source.direction=p.getLookAngle().normalize();source.started=System.currentTimeMillis();
        var handler=spellHandlers.get(skill.id);if(handler!=null){Runnable publish=handler.apply(source);released(source);publish.run();return;}
        if(skill.id.equals(MageSkills.FANG)){source.direction=new Vec3(source.direction.x,0,source.direction.z).normalize();source.origin=p.position().add(0,.8,0);scheduled.add(new Scheduled(source,source.started+Math.max(1,(long)(200/Math.max(.01,source.stats.get(Stat.CAST_SPEED))))));visual(source,source.id,"START",source.origin,source.direction,600,0,null);}
        else startProjectile(source);
    }
    private double frenzy(Source s){var f=frenzy.get(s.owner);return f!=null&&f.session.equals(s.session)&&f.generation==s.generation&&f.dimension.equals(s.dimension)&&f.until>System.currentTimeMillis()?f.power:0;}
    private void buffState(ServerPlayer p,long until){var j=new JsonObject();j.addProperty("session",sessions.apply(p).id.toString());j.addProperty("generation",sessions.apply(p).life.generation);j.addProperty("kind","FRENZY");j.addProperty("until",until);Network.send(p,"herderState",j);}
    void startProjectile(Source source){var p=owner(source);double range=source.parameters.get("range",12);Vec3 destination=source.origin.add(source.direction.scale(range));var spawn=ProjectileSpawn.inWorld(p,source.origin,source.direction,range,.15,e->eligible(p,e));source.origin=spawn.position();launch(source,source.origin,destination);}
    private void launch(Source source,Vec3 from,Vec3 destination){
        Flight flight=new Flight();flight.source=source;flight.visual=source.id;flight.origin=flight.position=from;flight.end=destination;flight.start=System.currentTimeMillis();flight.duration=Math.max(1,from.distanceTo(destination)/source.skill.projectile.speed()*1000);flights.add(flight);
        JsonObject motion=new JsonObject();motion.addProperty("range",from.distanceTo(destination));motion.addProperty("millis",(long)Math.ceil(flight.duration));motion.addProperty("velocity",from.distanceTo(destination)/flight.duration*1000);motion.addProperty("arcHeight",0);
        visual(source,flight.visual,"START",from,destination.subtract(from).normalize(),(long)Math.ceil(flight.duration)+flight.source.skill.projectile.impactMillis(),0,motion);
    }
    void visual(Source source,String id,String phase,Vec3 point,Vec3 direction,long duration,int sequence,JsonObject motion){
        var p=owner(source);if(p==null)return;long now=System.currentTimeMillis();var j=new JsonObject();j.addProperty("caster",p.getStringUUID());j.addProperty("session",source.session);j.addProperty("generation",source.generation);j.addProperty("dimension",source.dimension);j.addProperty("instance",source.instance);j.addProperty("id",id);j.addProperty("kind",source.skill.id);j.addProperty("phase",phase);j.addProperty("sequence",sequence);j.addProperty("seed",id.hashCode());j.addProperty("startedAt",now);j.addProperty("at",now);j.addProperty("serverNow",now);j.addProperty("duration",duration);j.addProperty("cosmeticOnly",true);j.addProperty("color",SkillPresentation.color(source.skill,false));for(String axis:List.of("x","y","z")){double value=axis.equals("x")?point.x:axis.equals("y")?point.y:point.z;j.addProperty(axis,value);j.addProperty("p"+axis,value);j.addProperty("d"+axis,axis.equals("x")?direction.x:axis.equals("y")?direction.y:direction.z);}if(motion!=null)j.add("motion",motion);visuals.accept(p,j);Network.send(p,"visual",j);
    }
    private void issue(Source source,LivingEntity target,Vec3 point){issue(source,target,point,null,false);}
    private void issue(Source source,LivingEntity target,Vec3 point,Stats baked,boolean slow){issue(source,target,point,baked,slow,source.summon()?1:source.parameters.get("damage",0),-2);}
    void issue(Source source,LivingEntity target,Vec3 point,Stats baked,boolean slow,double coefficient,int element){
        if(!valid(source))return;var p=owner(source);if(!eligible(p,target))return;Target targetState=targets.apply(target);if(targetState.life.dead)return;
        var attack=new Attack();attack.id=UUID.randomUUID().toString();attack.source=source;attack.target=target.getUUID();attack.generation=targetState.life.generation;attack.created=System.currentTimeMillis();attack.slow=slow;attack.expires=attack.created+5000;attack.point=point;attack.stats=baked!=null?baked.copy():source.stats.copy();attack.coefficient=coefficient;attack.element=element;attacks.put(attack.id,attack);
        JsonObject j=new JsonObject();j.addProperty("id",attack.id);j.addProperty("source",source.id);j.addProperty("session",source.session);j.addProperty("generation",source.generation);j.addProperty("skill",source.skill.id);j.addProperty("rank",source.rank);j.addProperty("target",target.getStringUUID());j.addProperty("entity",target.getId());j.addProperty("targetGeneration",attack.generation);j.addProperty("coefficient",attack.coefficient);j.addProperty("element",attack.element);j.addProperty("expires",attack.expires);j.addProperty("sourceExpires",source.expires);j.addProperty("summon",source.summon());j.add("stats",JSON.toJsonTree(attack.stats));j.add("effects",JSON.toJsonTree(source.effects));j.add("parameters",JSON.toJsonTree(source.parameters.values()));Network.send(p,"mageAttack",j);
    }
    public void tick(){
        long now=System.currentTimeMillis();
        tickChants(now);for(var tick:contentTicks)tick.accept(now);
        tickSummonEffects(now);
        retiring.entrySet().removeIf(e->e.getKey().isRemoved());
        for(var job:new ArrayList<>(scheduled))if(job.due<=now){
            scheduled.remove(job);if(!valid(job.source))continue;var p=owner(job.source);Source s=job.source;
            for(var target:p.level().getEntitiesOfClass(LivingEntity.class,new AABB(s.origin,s.origin.add(s.direction.scale(5))).inflate(1,1.5,1),e->eligible(p,e))){var delta=target.getBoundingBox().getCenter().subtract(s.origin);double forward=delta.dot(s.direction),side=Math.abs(delta.x*s.direction.z-delta.z*s.direction.x);if(forward>=-.5&&forward<=5+target.getBbWidth()*.5&&side<=.75+target.getBbWidth()*.5&&sight(p,s.origin,target.getBoundingBox().getCenter()))issue(s,target,target.position());}
            Network.send(p,"mageSourceEnd",Map.of("source",s.id));
        }
        for(var flight:new ArrayList<>(flights)){
            Source s=flight.source;if(!valid(s)){visual(s,flight.visual,"CANCEL",flight.position,s.direction,(long)Math.ceil(flight.duration)+s.skill.projectile.impactMillis(),++flight.sequence,null);flights.remove(flight);continue;}var p=owner(s);double u=Math.clamp((now-flight.start)/(double)flight.duration,0,1);Vec3 next=flight.origin.lerp(flight.end,u);
            var wall=p.level().clip(new ClipContext(flight.position,next,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));Vec3 end=wall.getType()==HitResult.Type.MISS?next:wall.getLocation();Vec3 from=flight.position;
            var collided=p.level().getEntitiesOfClass(LivingEntity.class,new AABB(from,end).inflate(.25),e->eligible(p,e)&&!flight.struck.contains(e.getUUID())&&SkillGeometry.intersects(e.getBoundingBox().inflate(.15),from,end)).stream().sorted(Comparator.comparingDouble(e->e.distanceToSqr(from))).toList();boolean done=false;
            for(var target:collided){flight.struck.add(target.getUUID());flight.position=target.getBoundingBox().inflate(.15).clip(from,end).orElse(from);if(s.skill.projectile.explodes()){done=true;break;}issue(s,target,flight.position);if(++flight.hits>=s.skill.projectile.pierces()){done=true;break;}}
            if(!done)flight.position=end;
            if(done||wall.getType()!=HitResult.Type.MISS||u>=1){if(s.skill.projectile.explodes())for(var target:p.level().getEntitiesOfClass(LivingEntity.class,new AABB(flight.position,flight.position).inflate(s.parameters.get("radius",3)),e->eligible(p,e))){Vec3 contact=s.skill.projectile.boundingBoxBlast()?closest(target,flight.position):target.getBoundingBox().getCenter();if(contact.distanceToSqr(flight.position)<=Math.pow(s.parameters.get("radius",3),2)&&sight(p,flight.position.add(0,.1,0),contact))issue(s,target,flight.position);}visual(s,flight.visual,"FINISH",flight.position,s.direction,(long)Math.ceil(flight.duration)+flight.source.skill.projectile.impactMillis(),++flight.sequence,null);flights.remove(flight);if(!s.summon())Network.send(p,"mageSourceEnd",Map.of("source",s.id));}
        }
        for(var source:new ArrayList<>(summons.values()))if(!valid(source)||source.entity==null||source.entity.isRemoved()){if(source.entity!=null&&!source.entity.isRemoved()&&now>=source.expires)depart(source);else remove(source);}
        for(var entry:new ArrayList<>(frenzy.entrySet())){var p=server.getPlayerList().getPlayer(entry.getKey());var f=entry.getValue();if(p==null||p.isDeadOrDying()||f.until<now||!sessions.apply(p).id.toString().equals(f.session)||sessions.apply(p).life.generation!=f.generation||!p.level().dimension().identifier().toString().equals(f.dimension)){frenzy.remove(entry.getKey());if(p!=null)buffState(p,0);}}
        attacks.values().removeIf(a->a.expires<now);incoming.values().removeIf(i->i.expires<now);sources.values().removeIf(s->s.expires<now);
    }
    @Override public void tick(SummonedCreature entity){
        if(retiring.containsKey(entity)){if(System.currentTimeMillis()>=retiring.get(entity)){retiring.remove(entity);entity.discard();}return;}
        var s=summons.get(entity.getUUID());if(s!=null&&s.entity==entity&&s.expires>0&&System.currentTimeMillis()>=s.expires){depart(s);return;}if(s==null||s.entity!=entity||!valid(s)){entity.discard();return;}var p=owner(s);long now=System.currentTimeMillis();if(now<s.started)return;double dt=s.lastTick==0?0:Math.min(.25,(now-s.lastTick)/1000d);s.lastTick=now;
        if(now-s.lastHurt>=5000&&s.shield<s.maximumShield)s.shield=Math.min(s.maximumShield,s.shield+s.maximumShield*.2*dt);entity.project(s.health/s.maximum,s.maximumShield>0?s.shield/s.maximumShield:0);
        if(now>s.busy){String idle=s.guardUntil>now?"guard":"";if(!entity.move().equals(idle))entity.play(idle,s.guardUntil>now?Math.max(1,s.guardUntil-now):1000);}if(entity.distanceToSqr(p)>24*24||s.stuck>=60){try{Vec3 destination=safe(p,entity,0,List.of());entity.getNavigation().stop();entity.setPos(destination);pulses.removeIf(pulse->pulse.source==s);entity.play("",1000);s.busy=now;}catch(IllegalArgumentException ignored){}s.stuck=0;}
        LivingEntity target=null;UUID recent=preferred.get(p.getUUID());if(!following.contains(p.getUUID())&&recent!=null&&p.level().getEntity(recent) instanceof LivingEntity living&&eligible(p,living)&&living.distanceToSqr(entity)<=256)target=living;
        if(target==null&&!following.contains(p.getUUID()))target=p.level().getEntitiesOfClass(LivingEntity.class,entity.getBoundingBox().inflate(16),e->eligible(p,e)).stream().filter(e->e.distanceToSqr(entity)<=256).min(Comparator.comparingDouble(e->e.distanceToSqr(entity))).orElse(null);
        tickSummon(s,p,target,now);if(now-s.lastSync>=1000){sendState(s);s.lastSync=now;}
    }
    private void tickSummon(Source s,ServerPlayer p,LivingEntity target,long now){
        var e=s.entity;double speed=s.kind==SummonKind.KASIJAS?1:Math.max(.01,1+s.stats.get(Stat.ATTACK_SPEED)/100);
        double approach=switch(s.kind){case HERDER,FIRE,LIGHT->2;case DARK->2.5;case ICE,LOUISE,KING->10;case SANDOR,KASIJAS->3;};
        if(now>=s.busy){
            if(target!=null){e.setTarget(target);e.getLookControl().setLookAt(target);if(e.distanceToSqr(target)>approach*approach)e.getNavigation().moveTo(target,Math.max(.01,s.stats.get(Stat.MOVEMENT_SPEED)));else e.getNavigation().stop();}
            else {e.setTarget(null);if(e.distanceToSqr(p)>9)e.getNavigation().moveTo(p,Math.max(.01,s.stats.get(Stat.MOVEMENT_SPEED)));else e.getNavigation().stop();}
        }
        if(!e.getNavigation().isDone()&&s.previous!=null&&e.position().distanceToSqr(s.previous)<.0001)s.stuck++;else s.stuck=0;s.previous=e.position();
        while(s.actionIndex<s.cues.size()&&now-s.started>=s.cues.get(s.actionIndex).at()){
            var cue=s.cues.get(s.actionIndex++);
            // Missed slots do not accumulate into a lag spike burst.
            if(now-s.started-cue.at()>250)continue;
            if(cue.move().equals("falling_swords")&&(target==null||e.distanceToSqr(target)>100)){
                e.play("falling_swords",500);s.busy=now+500;summonVisual(s,UUID.randomUUID().toString(),"START",e.getEyePosition(),e.getLookAngle(),500,"falling_swords",null);continue;
            }
            if(target==null)continue;
            var move=SummonMoves.require(cue.move());
            if(s.kind==SummonKind.LOUISE&&move.id().equals("fireball")&&e.distanceToSqr(target)<=4)move=SummonMoves.require("staff");
            if(move.id().equals("ice_touch")&&e.distanceToSqr(target)>4)move=SummonMoves.require("water_bolt");
            if(move.id().equals("element_touch")&&e.distanceToSqr(target)>4)move=SummonMoves.require("king_wave");
            if(e.distanceToSqr(target)>move.range()*move.range()||!sight(p,e.getEyePosition(),target.getBoundingBox().getCenter()))continue;
            double rate=speed*(move.id().equals("bomb")&&cue.share()==1?2:1);long duration=Math.max(1,Math.round(move.duration()/rate));
            if(move.id().equals("sword_force"))s.guardUntil=now+3000;
            s.busy=now+duration;e.getNavigation().stop();e.setYRot((float)Math.toDegrees(Math.atan2(-(target.getX()-e.getX()),target.getZ()-e.getZ())));e.yBodyRot=e.getYRot();e.play(move.id(),duration);
            Vec3 origin=e.getEyePosition(),aim=target.getBoundingBox().getCenter();long generation=targets.apply(target).life.generation;
            double coefficient=s.parameters.get(cue.stone()?"stoneDamage":"damage",0)*cue.share()/move.contacts().length;
            var baked=SummonRules.attack(s.stats,coefficient);baked.modifiers.put("summonElement",(double)move.element());baked.modifier("final").mut+=frenzy(s);
            for(int contact:move.contacts())pulses.add(new Pulse(s,move,target.getUUID(),generation,origin,aim,baked.copy(),now+Math.round(contact/rate)));
            summonVisual(s,UUID.randomUUID().toString(),"START",origin,aim.subtract(origin).normalize(),duration,move.id(),null);
        }
    }
    private void tickSummonEffects(long now){
        for(var slow:new ArrayList<>(slows.values()))if(now>=slow.until||slow.target.isRemoved()||!slow.target.isAlive()){removeSlow(slow);slows.remove(slow.target.getUUID());}
        for(var pulse:new ArrayList<>(pulses)){
            if(!valid(pulse.source)){pulses.remove(pulse);continue;}if(pulse.due>now)continue;pulses.remove(pulse);
            var p=owner(pulse.source);var e=pulse.source.entity;var m=pulse.move;
            if(m.shape().equals("projectile")||m.shape().equals("meteor")){
                var f=new SummonFlight();f.pulse=pulse;f.id=UUID.randomUUID().toString();f.from=m.shape().equals("meteor")?pulse.aim.add(0,6,0):e.getEyePosition();double distance=m.pierces()>1&&m.radius()==0?m.range():Math.min(m.range(),f.from.distanceTo(pulse.aim));f.to=m.shape().equals("meteor")?pulse.aim:f.from.add(pulse.aim.subtract(f.from).normalize().scale(distance));f.position=f.from;f.start=now;f.duration=Math.max(50,Math.round(f.from.distanceTo(f.to)/m.velocity()*1000));f.arc=m.id().equals("stone")||m.id().equals("triple_throw")?.5:m.id().equals("bomb")?1:0;summonFlights.add(f);
                var motion=new JsonObject();motion.addProperty("velocity",m.velocity());motion.addProperty("range",f.from.distanceTo(f.to));motion.addProperty("millis",f.duration);motion.addProperty("arcHeight",f.arc);
                summonVisual(pulse.source,f.id,"START",f.from,f.to.subtract(f.from).normalize(),f.duration+500,m.id()+"_flight",motion);continue;
            }
            Vec3 origin=e.getEyePosition(),beforeDash=origin;Vec3 direction=pulse.aim.subtract(pulse.origin).multiply(1,0,1).normalize();
            if(m.shape().equals("dash"))for(int i=0;i<8;i++){Vec3 step=direction.scale(.25);if(!p.level().noCollision(e,e.getBoundingBox().move(step)))break;e.move(MoverType.SELF,step);}
            origin=e.getEyePosition();double reach=m.range()-(m.shape().equals("dash")?origin.distanceTo(beforeDash):0);
            if(m.shape().equals("area"))summonArea(pulse,pulse.aim,m.radius());
            else if(m.shape().equals("melee")){
                var raw=p.level().getEntity(pulse.target);if(raw instanceof LivingEntity t&&eligible(p,t)&&targets.apply(t).life.generation==pulse.targetGeneration&&t.distanceToSqr(e)<=m.range()*m.range()&&sight(p,origin,t.getBoundingBox().getCenter()))issue(pulse.source,t,t.position(),pulse.stats,m.slow());
            }else {
                if(direction.lengthSqr()<.01)direction=e.getLookAngle().multiply(1,0,1).normalize();
                if(m.shape().equals("beam")){var wall=p.level().clip(new ClipContext(origin,origin.add(direction.scale(reach)),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));if(wall.getType()!=HitResult.Type.MISS)reach=Math.min(reach,origin.distanceTo(wall.getLocation()));}
                int count=0;
                for(var t:p.level().getEntitiesOfClass(LivingEntity.class,e.getBoundingBox().inflate(m.range()),x->eligible(p,x)).stream().sorted(Comparator.comparingDouble(x->x.distanceToSqr(e))).toList()){
                    Vec3 d=t.getBoundingBox().getCenter().subtract(origin);double forward=d.dot(direction);
                    boolean inside=m.shape().equals("cone")?d.length()<=reach+t.getBbWidth()/2&&d.normalize().dot(direction)>=.5:forward>=0&&forward<=reach&&d.subtract(direction.scale(forward)).length()<=m.width()/2+t.getBbWidth()/2;
                    if(inside&&sight(p,origin,t.getBoundingBox().getCenter())){issue(pulse.source,t,t.position(),pulse.stats,m.slow());if(++count>=m.pierces())break;}
                }
            }
            summonVisual(pulse.source,UUID.randomUUID().toString(),"START",m.shape().equals("area")?pulse.aim:origin,direction,500,m.id()+"_impact",null);
        }
        for(var f:new ArrayList<>(summonFlights)){
            if(!valid(f.pulse.source)){summonFlights.remove(f);continue;}var p=owner(f.pulse.source);var m=f.pulse.move;double u=Math.clamp((now-f.start)/(double)f.duration,0,1),previous=f.progress;f.progress=u;
            boolean done=false;int steps=Math.max(1,(int)Math.ceil(f.from.distanceTo(f.to)*(u-previous)/.25));
            for(int step=1;step<=steps&&!done;step++){
                double t=previous+(u-previous)*step/steps;Vec3 next=f.from.lerp(f.to,t).add(0,4*f.arc*t*(1-t),0);var wall=p.level().clip(new ClipContext(f.position,next,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));Vec3 end=wall.getType()==HitResult.Type.MISS?next:wall.getLocation(),from=f.position;
                for(var target:p.level().getEntitiesOfClass(LivingEntity.class,new AABB(from,end).inflate(m.width()),x->eligible(p,x)&&!f.struck.contains(x.getUUID())&&SkillGeometry.intersects(x.getBoundingBox().inflate(m.width()/2),from,end)).stream().sorted(Comparator.comparingDouble(x->x.distanceToSqr(from))).toList()){
                    f.position=target.getBoundingBox().inflate(m.width()/2).clip(from,end).orElse(end);f.struck.add(target.getUUID());
                    if(m.radius()>0){done=true;break;}issue(f.pulse.source,target,f.position,f.pulse.stats,m.slow());if(++f.hits>=m.pierces()){done=true;break;}
                }
                if(!done)f.position=end;done|=wall.getType()!=HitResult.Type.MISS;
            }
            if(done||u>=1){if(m.radius()>0)summonArea(f.pulse,f.position,m.radius());summonVisual(f.pulse.source,f.id,"FINISH",f.position,f.to.subtract(f.from).normalize(),f.duration+500,m.id()+"_flight",null);summonFlights.remove(f);}
        }
    }
    private void summonArea(Pulse pulse,Vec3 center,double radius){
        var p=owner(pulse.source);for(var t:p.level().getEntitiesOfClass(LivingEntity.class,new AABB(center,center).inflate(radius),x->eligible(p,x)))if(t.getBoundingBox().getCenter().distanceToSqr(center)<=radius*radius&&sight(p,center,t.getBoundingBox().getCenter()))issue(pulse.source,t,t.position(),pulse.stats,pulse.move.slow());
    }
    private void summonVisual(Source s,String id,String phase,Vec3 point,Vec3 direction,long duration,String move,JsonObject motion){
        var p=owner(s);if(p==null)return;long now=System.currentTimeMillis();var j=new JsonObject();j.addProperty("caster",p.getStringUUID());j.addProperty("session",s.session);j.addProperty("generation",s.generation);j.addProperty("dimension",s.dimension);j.addProperty("instance",s.instance);j.addProperty("id",id);j.addProperty("kind",s.skill.id);j.addProperty("summonMove",move);j.addProperty("phase",phase);j.addProperty("sequence",phase.equals("FINISH")?1:0);j.addProperty("seed",id.hashCode());j.addProperty("startedAt",now);j.addProperty("at",now);j.addProperty("serverNow",now);j.addProperty("duration",duration);j.addProperty("cosmeticOnly",true);j.addProperty("color",move.startsWith("ice")||move.startsWith("water")?0x87d9ef:move.contains("flame")||move.contains("fire")||move.contains("meteor")||move.contains("bomb")?0xf6a04b:move.startsWith("shadow")?0xa383df:move.startsWith("king")||move.startsWith("element")||move.startsWith("light")?0x91ddff:0xe7ce84);
        for(String axis:List.of("x","y","z")){double value=axis.equals("x")?point.x:axis.equals("y")?point.y:point.z;j.addProperty(axis,value);j.addProperty("p"+axis,value);j.addProperty("d"+axis,axis.equals("x")?direction.x:axis.equals("y")?direction.y:direction.z);}if(motion!=null)j.add("motion",motion);visuals.accept(p,j);Network.send(p,"visual",j);
    }
    private void applySlow(Source source,LivingEntity target,long occurred){
        long until=occurred+1500;if(until<=System.currentTimeMillis())return;
        var previous=slows.get(target.getUUID());if(previous!=null&&previous.until>=until)return;
        String kind=targets.apply(target).kind;if(kind.equals("BOSS")||kind.equals("WITHER")||target instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon||target instanceof net.minecraft.world.entity.boss.wither.WitherBoss)return;
        var attribute=target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);if(attribute==null)return;attribute.removeModifier(ICE_SLOW);attribute.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(ICE_SLOW,-.2,net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));slows.put(target.getUUID(),new Slow(target,source,until));
    }
    private void removeSlow(Slow slow){var attribute=slow.target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);if(attribute!=null)attribute.removeModifier(ICE_SLOW);}

    @Override public boolean incoming(SummonedCreature entity,ServerLevel level,DamageSource source,float points){
        Source s=summons.get(entity.getUUID());if(s==null||s.entity!=entity||!valid(s)||source.getEntity() instanceof Player||source.getEntity() instanceof SummonedCreature)return false;
        long now=System.currentTimeMillis();String kind=source.getMsgId();double damage;boolean direct=source.getEntity() instanceof LivingEntity attacker&&enemy(attacker)&&!Set.of("poison","wither","onFire","inFire","lava","thorns").contains(kind);var j=new JsonObject();
        if(direct&&source.getEntity() instanceof LivingEntity attacker&&enemy(attacker)){Target target=targets.apply(attacker);var player=owner(s);int at=target.level(progress.apply(player).level,encounterSync.test(player));double raw=target.attack(at);damage=SummonRules.incoming(s.stats,raw,false,-1,at,s.level,now<s.guardUntil?.65*.75:.65);j.addProperty("points",raw);j.addProperty("magic",false);j.addProperty("element",-1);j.addProperty("attackerLevel",at);j.addProperty("ownerLevel",s.level);j.addProperty("protection",now<s.guardUntil?.65*.75:.65);}
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
        double shield=Math.max(0,s.shield-damage),health=Math.max(0,s.health-Math.max(0,damage-s.shield));try{journal.begin();journal.put(key,Map.of("source",s.id,"health",health,"shield",shield));journal.finish();}catch(Exception failure){journal.abort();throw new IllegalStateException(failure);}incoming.remove(root);s.health=health;s.shield=shield;if(!dodge&&damage>0)s.lastHurt=Math.max(s.lastHurt,hit.occurred);if(health<=0)retire(s);else {if(System.currentTimeMillis()>s.busy){s.entity.play("hit",300);s.busy=System.currentTimeMillis()+300;}entityFeedback(s);sendState(s);}
    }
    private void entityFeedback(Source s){s.entity.project(s.health/s.maximum,s.maximumShield>0?s.shield/s.maximumShield:0);}
    private void sendState(Source s){var p=owner(s);if(p!=null){var view=JSON.toJsonTree(Map.of("session",s.session,"generation",s.generation,"source",s.id,"until",s.expires,"health",s.health,"maximum",s.maximum,"shield",s.shield,"maximumShield",s.maximumShield)).getAsJsonObject();view.addProperty("kind",s.kind.name());view.addProperty("skill",s.skill.id);view.addProperty("rank",s.rank);view.add("damage",JSON.toJsonTree(Map.of("melee",s.meleeDamage,"stone",s.stoneDamage,"triple",s.tripleDamage)));Network.send(p,"herderState",view);}}
    public void clear(ServerPlayer p){cancelChant(p,"");ChantRules.movement(p,false);prepared.remove(p.getUUID());for(var clear:contentClears)clear.accept(p.getUUID());for(var s:new ArrayList<>(sources.values()))if(s.owner.equals(p.getUUID())&&!s.summon()){s.cancelled=true;sources.remove(s.id);}clear(p.getUUID());}
    public void reduced(ServerPlayer p,String skill){if(resetOnReduction.contains(skill)){cancelChant(p,"");for(var clear:contentClears)clear.accept(p.getUUID());for(var source:new ArrayList<>(sources.values()))if(source.owner.equals(p.getUUID())&&spellHandlers.containsKey(source.skill.id))source.cancelled=true;}if(skill.equals(SummonerSkills.MASTERY)){clear(p);return;}if(skill.equals(SummonerSkills.FRENZY)){frenzy.remove(p.getUUID());buffState(p,0);}for(var s:new ArrayList<>(summons.values()))if(s.owner.equals(p.getUUID())&&s.skill.id.equals(skill))remove(s);}
    public void cancelAccepted(ServerPlayer p,String id){cancelChant(p,id);for(var s:new ArrayList<>(sources.values()))if(id.equals(s.castId)){if(s.summon())remove(s);else sources.remove(s.id);}var f=frenzy.get(p.getUUID());if(f!=null&&f.cast.equals(id)){frenzy.remove(p.getUUID());buffState(p,0);}prepared.remove(p.getUUID());}
    private void retire(Source s){retiring.put(s.entity,System.currentTimeMillis()+1800);s.entity.play("death",1200);s.entity.getNavigation().stop();remove(s);}
    private void depart(Source s){s.naturalEnd=true;retiring.put(s.entity,System.currentTimeMillis()+800);s.entity.play("retreat",800);s.entity.getNavigation().stop();remove(s);}
    private void clear(UUID owner){for(var slow:new ArrayList<>(slows.values()))if(slow.source.owner.equals(owner)){removeSlow(slow);slows.remove(slow.target.getUUID());}for(var s:new ArrayList<>(summons.values()))if(s.owner.equals(owner))remove(s);frenzy.remove(owner);following.remove(owner);for(var e:new ArrayList<>(retiring.keySet()))if(owner.equals(e.owner)){retiring.remove(e);e.discard();}var p=server.getPlayerList().getPlayer(owner);if(p!=null)buffState(p,0);}
    private void remove(Source s){for(var slow:new ArrayList<>(slows.values()))if(slow.source==s){removeSlow(slow);slows.remove(slow.target.getUUID());}pulses.removeIf(x->x.source==s);summonFlights.removeIf(x->x.pulse.source==s);s.expires=0;summons.remove(s.entity.getUUID());sources.remove(s.id);if(!retiring.containsKey(s.entity))s.entity.discard();var p=owner(s);if(p!=null){var j=new JsonObject();j.addProperty("session",s.session);j.addProperty("generation",s.generation);j.addProperty("source",s.id);j.addProperty("kind",s.kind.name());j.addProperty("skill",s.skill.id);j.addProperty("until",0);Network.send(p,"herderState",j);}}
    private Attack attack(ServerPlayer p,JsonObject j){if(!j.has("mageAttack"))return null;Attack a=attacks.get(j.get("mageAttack").getAsString());return a!=null&&a.source.owner.equals(p.getUUID())?a:null;}
    /** Natural expiry stops new attacks; a contact already issued keeps its existing bounded receipt window. */
    private boolean receiptSourceValid(Attack a){
        if(valid(a.source))return true;if(!a.source.naturalEnd)return false;var p=owner(a.source);if(p==null||p.isDeadOrDying())return false;var session=sessions.apply(p);
        return !session.life.dead&&session.id.toString().equals(a.source.session)&&session.life.generation==a.source.generation&&session.life.instance.equals(a.source.instance)&&p.level().dimension().identifier().toString().equals(a.source.dimension);
    }
    @Override public Stats sourceStats(ServerPlayer p,JsonObject j){Attack a=attack(p,j);return a==null?null:a.stats;}
    @Override public EquipmentEffects.Snapshot sourceEffects(ServerPlayer p,JsonObject j){Attack a=attack(p,j);return a==null?null:a.source.effects;}
    @Override public boolean direct(ServerPlayer p,JsonObject j){Attack a=attack(p,j);return a==null||!a.source.summon();}
    @Override public long occurredAt(ServerPlayer p,JsonObject j){Attack a=attack(p,j);return a==null?-1:a.created;}
    @Override public void reconcile(ServerPlayer p,cn.yumis.fight.modules.combat.api.Runtime previous,cn.yumis.fight.modules.combat.api.Runtime incoming){var state=heartState.apply(p);if(state.heart==1&&state.stacks>0)incoming.mp=Math.min(previous.mp,incoming.mp);}
    @Override public void validate(ServerPlayer p,LivingEntity target,JsonObject j){
        if(!j.has("mageAttack"))return;Attack a=attack(p,j);if(a==null||a.consumed||a.created>System.currentTimeMillis()||a.expires<System.currentTimeMillis()||!receiptSourceValid(a)||!a.target.equals(target.getUUID())||a.generation!=j.get("generation").getAsLong()||!a.source.id.equals(j.get("mageSource").getAsString()))throw new IllegalArgumentException("魔法师攻击来源、目标或阶段已失效");
        if(!j.has("mageElement")||j.get("mageElement").getAsInt()!=a.element||!j.has("mageCoefficient")||!Double.isFinite(j.get("mageCoefficient").getAsDouble())||Math.abs(j.get("mageCoefficient").getAsDouble()-a.coefficient)>Math.max(1e-10,Math.abs(a.coefficient)*1e-10))throw new IllegalArgumentException("魔法攻击段的元素或系数不匹配");
    }
    @Override public CombatPorts.Commit prepare(ServerPlayer p,LivingEntity target,JsonObject j,Receipt receipt,Life actor){
        Attack a=attack(p,j);if(!receipt.accepted())return CombatPorts.Commit.NONE;
        return new CombatPorts.Commit(){public void publish(){if(a!=null){a.consumed=true;if(a.slow&&receipt.actual()>0)applySlow(a.source,target,a.created);}if(target!=p&&receipt.actual()>0&&enemy(target)){preferred.put(p.getUUID(),target.getUUID());if(a!=null&&a.source.summon()&&!a.source.entity.isRemoved()&&target instanceof Mob mob&&!targets.apply(target).kind.equals("BOSS")&&!targets.apply(target).kind.equals("WITHER")){mob.setLastHurtByMob(a.source.entity);mob.setTarget(a.source.entity);}}}};
    }
}
