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

/** World summon entities and AI. Player-owned damage and summon life arrive as client facts. */
public final class MageSkillService implements CombatPorts.SkillEffects,SummonActions.Handler {
    private static final Gson JSON=new GsonBuilder().disableHtmlEscaping().create();
    private final MinecraftServer server;private final Journal journal;
    private final Function<ServerPlayer,CombatSession> sessions;private final Function<ServerPlayer,Progress> progress;
    private final Function<LivingEntity,Target> targets;private final BiConsumer<ServerPlayer,JsonObject> visuals;private final Predicate<ServerPlayer> encounterSync;
    private final Map<String,Source> sources=new HashMap<>();private final Map<UUID,Source> summons=new HashMap<>();
    private final List<Pulse> pulses=new ArrayList<>();
    private record Pulse(Source source,SummonMoves.Move move,UUID target,long targetGeneration,Vec3 origin,Vec3 aim,double share,boolean stone,double power,long due){}
    private record Slow(LivingEntity target,UUID owner,long until){}
    private final Map<UUID,Slow> slows=new HashMap<>();
    private static final net.minecraft.resources.Identifier ICE_SLOW=net.minecraft.resources.Identifier.fromNamespaceAndPath("yumis_dungeon_and_fight","louise_needle_slow");
    private final Map<UUID,UUID> preferred=new HashMap<>();
    private record Frenzy(String session,long generation,String dimension,long until,double power){}
    private final Map<UUID,Frenzy> frenzy=new HashMap<>();private final Set<UUID> following=new HashSet<>();
    private final Map<SummonedCreature,Long> retiring=new HashMap<>();
    private static final class Source {
        String id,session,dimension,instance,job;UUID owner;long generation,expires,started,busy,lastHurt,lastSync,sequence;int rank,level,stuck,actionIndex;SummonKind kind;long guardUntil;List<SummonMoves.Cue> cues=List.of();
        SkillDefinition skill;SkillParameters.Snapshot parameters;Stats stats;EquipmentEffects.Snapshot effects;Vec3 previous;SummonedCreature entity;
        double health,shield,maximum,maximumShield;JsonObject damage;
    }
    public MageSkillService(MinecraftServer server,Journal journal,Function<ServerPlayer,CombatSession> sessions,Function<ServerPlayer,Progress> progress,Function<LivingEntity,Target> targets,BiConsumer<ServerPlayer,JsonObject> visuals,Predicate<ServerPlayer> encounterSync){this.server=server;this.journal=journal;this.sessions=sessions;this.progress=progress;this.targets=targets;this.visuals=visuals;this.encounterSync=encounterSync;}
    public void register(CommandRouter<ServerPlayer,JsonObject> router){
        router.register("skills","summonSpawn",this::spawn);router.register("skills","herderReceive",this::receive);router.register("skills","herderSnapshot",this::receive);
        router.register("skills","summonFrenzy",(p,j)->{identity(p,j);frenzy.put(p.getUUID(),new Frenzy(j.get("session").getAsString(),j.get("generation").getAsLong(),p.level().dimension().identifier().toString(),j.get("until").getAsLong(),finite(j.get("power").getAsDouble())));buffState(p,j.get("until").getAsLong());});
        router.register("skills","summonFollow",(p,j)->{identity(p,j);if(!following.add(p.getUUID()))following.remove(p.getUUID());});
        router.register("skills","summonReduce",(p,j)->{identity(p,j);reduced(p,j.get("skill").getAsString());});
    }
    private static double finite(double value){if(!Double.isFinite(value)||value<0)throw new IllegalArgumentException("召唤物状态格式无效");return value;}
    private void identity(ServerPlayer p,JsonObject j){var s=sessions.apply(p);if(!s.id.toString().equals(j.get("session").getAsString())||s.life.generation!=j.get("generation").getAsLong())throw new IllegalArgumentException("召唤物会话已失效");}
    private void spawn(ServerPlayer p,JsonObject j){
        identity(p,j);String key="clientSummon/"+p.getUUID()+"/"+sessions.apply(p).id+"/"+j.get("id").getAsString();if(!journal.get(key).isJsonNull())return;
        var created=new ArrayList<Source>();
        try{for(var element:j.getAsJsonArray("actors")){
            var a=element.getAsJsonObject();Source s=new Source();s.id=a.get("id").getAsString();s.owner=p.getUUID();s.session=j.get("session").getAsString();s.generation=j.get("generation").getAsLong();s.dimension=j.get("dimension").getAsString();s.instance=j.get("instance").getAsString();s.job=j.get("job").getAsString();
            s.skill=SkillCatalog.require(j.get("skill").getAsString());s.rank=j.get("rank").getAsInt();s.level=j.get("level").getAsInt();s.kind=SummonKind.valueOf(a.get("kind").getAsString());s.stats=JSON.fromJson(a.get("stats"),Stats.class);s.effects=JSON.fromJson(a.get("effects"),EquipmentEffects.Snapshot.class);
            var values=new LinkedHashMap<String,Double>();a.getAsJsonObject("parameters").entrySet().forEach(e->values.put(e.getKey(),e.getValue().getAsDouble()));s.parameters=new SkillParameters.Snapshot(s.skill,s.rank,values);s.damage=a.getAsJsonObject("damage").deepCopy();
            s.started=a.get("at").getAsLong();s.expires=a.get("until").getAsLong();s.health=s.maximum=finite(a.get("health").getAsDouble());s.shield=s.maximumShield=finite(a.get("shield").getAsDouble());
            s.entity=new SummonedCreature(SkillContentTypes.SUMMONS.get(s.kind).get(),p.level());s.entity.owner=p.getUUID();s.entity.setPos(vector(a.getAsJsonArray("position")));s.entity.setCustomName(cn.yumis.fight.modules.presentation.api.ModText.of(s.kind.title+" Lv."+s.rank));
            s.cues=SummonMoves.cues(s.kind,s.rank,120000,Math.max(.01,1+s.stats.get(Stat.ATTACK_SPEED)/100));s.previous=s.entity.position();entityFeedback(s);s.entity.play("spawn",Math.max(1,s.started-System.currentTimeMillis()));s.busy=s.started;created.add(s);
        }
        journal.transaction(Map.of(key,j));
        for(var s:created){for(var old:new ArrayList<>(summons.values()))if(old.owner.equals(p.getUUID())&&old.kind==s.kind)remove(old);sources.put(s.id,s);summons.put(s.entity.getUUID(),s);if(!p.level().addFreshEntity(s.entity)){sources.remove(s.id);summons.remove(s.entity.getUUID());s.entity.discard();throw new IllegalStateException("无法创建召唤物世界实体");}sendState(s);}
        }catch(Exception failure){for(var s:created){sources.remove(s.id);summons.remove(s.entity.getUUID());s.entity.discard();}if(!journal.get(key).isJsonNull())p.connection.disconnect(cn.yumis.fight.modules.presentation.api.ModText.of("召唤事实已保存，世界实体发布失败，请重连："+failure.getMessage()));throw new IllegalStateException(failure);}
    }
    private ServerPlayer owner(Source source){return server.getPlayerList().getPlayer(source.owner);}
    private boolean valid(Source source){var p=owner(source);if(p==null||source.expires<System.currentTimeMillis()||source.entity==null||source.entity.isRemoved())return false;var s=sessions.apply(p);return s.id.toString().equals(source.session)&&s.life.generation==source.generation&&s.life.instance.equals(source.instance)&&progress.apply(p).jobId().equals(source.job)&&p.level().dimension().identifier().toString().equals(source.dimension);}
    private static boolean enemy(LivingEntity e){return e.isAlive()&&(e instanceof Enemy||e instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon);}
    static boolean eligible(ServerPlayer owner,LivingEntity e){return enemy(e)&&(!(e instanceof cn.yumis.fight.modules.dungeons.api.TrainingSandbag bag)||!bag.timed()||bag.ownedBy(owner)&&!bag.finished());}
    static Vec3 closest(LivingEntity target,Vec3 point){var box=target.getBoundingBox();return new Vec3(Math.clamp(point.x,box.minX,box.maxX),Math.clamp(point.y,box.minY,box.maxY),Math.clamp(point.z,box.minZ,box.maxZ));}
    private static Vec3 vector(JsonArray a){return new Vec3(a.get(0).getAsDouble(),a.get(1).getAsDouble(),a.get(2).getAsDouble());}
    private static boolean sight(ServerPlayer p,Vec3 from,Vec3 to){return p.level().clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p)).getType()==HitResult.Type.MISS;}
    private static Vec3 safe(ServerPlayer p,SummonedCreature entity,int slot,List<Vec3> reserved){
        for(int r=1;r<=5;r++)for(int i=0;i<16;i++){double a=(i+slot)*Math.PI/8;Vec3 origin=p.position().add(Math.cos(a)*r,1,Math.sin(a)*r);var hit=p.level().clip(new ClipContext(origin,origin.add(0,-5,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));if(hit.getType()==HitResult.Type.MISS)continue;Vec3 point=hit.getLocation().add(0,.01,0);var pos=BlockPos.containing(point);if(!p.level().hasChunkAt(pos)||!p.level().getWorldBorder().isWithinBounds(pos)||reserved.stream().anyMatch(v->v.distanceToSqr(point)<2))continue;var box=entity.getBoundingBox().move(point.subtract(entity.position()));if(p.level().noCollision(box)&&!p.level().containsAnyLiquid(box))return point;}
        throw new IllegalArgumentException("附近没有足够的安全召唤空间");
    }
    private double frenzy(Source s){var f=frenzy.get(s.owner);return f!=null&&f.session.equals(s.session)&&f.generation==s.generation&&f.dimension.equals(s.dimension)&&f.until>System.currentTimeMillis()?f.power:0;}
    private void buffState(ServerPlayer p,long until){var j=new JsonObject();j.addProperty("session",sessions.apply(p).id.toString());j.addProperty("generation",sessions.apply(p).life.generation);j.addProperty("job",progress.apply(p).jobId());j.addProperty("dimension",p.level().dimension().identifier().toString());j.addProperty("instance",sessions.apply(p).life.instance);j.addProperty("kind","FRENZY");j.addProperty("until",until);Network.send(p,"herderState",j);}
    private void issue(Pulse pulse,Vec3 origin,double reach){
        Source s=pulse.source;if(!valid(s))return;long now=System.currentTimeMillis();var j=new JsonObject();
        j.addProperty("id",UUID.randomUUID().toString());j.addProperty("source",s.id);j.addProperty("session",s.session);j.addProperty("generation",s.generation);j.addProperty("job",s.job);j.addProperty("dimension",s.dimension);j.addProperty("instance",s.instance);j.addProperty("skill",s.skill.id);j.addProperty("rank",s.rank);j.addProperty("target",pulse.target.toString());j.addProperty("targetGeneration",pulse.targetGeneration);j.addProperty("move",pulse.move.id());j.addProperty("share",pulse.share);j.addProperty("stone",pulse.stone);j.addProperty("frenzyPower",pulse.power);j.addProperty("sourceExpires",s.expires);j.addProperty("at",now);j.addProperty("reach",reach);j.addProperty("actor",s.entity.getStringUUID());j.addProperty("actorEntity",s.entity.getId());j.add("origin",JSON.toJsonTree(new double[]{origin.x,origin.y,origin.z}));j.add("aim",JSON.toJsonTree(new double[]{pulse.aim.x,pulse.aim.y,pulse.aim.z}));j.add("axis",JSON.toJsonTree(new double[]{pulse.aim.x-pulse.origin.x,pulse.aim.y-pulse.origin.y,pulse.aim.z-pulse.origin.z}));j.add("stats",JSON.toJsonTree(s.stats));j.add("effects",JSON.toJsonTree(s.effects));j.add("parameters",JSON.toJsonTree(s.parameters.values()));Network.send(owner(s),"summonAction",j);
    }
    public void tick(){long now=System.currentTimeMillis();tickSummonEffects(now);retiring.entrySet().removeIf(e->e.getKey().isRemoved());for(var s:new ArrayList<>(sources.values()))if(s.expires>0&&now>=s.expires)depart(s);else if(!valid(s))remove(s);}
    @Override public void tick(SummonedCreature entity){
        if(retiring.containsKey(entity)){if(System.currentTimeMillis()>=retiring.get(entity)){retiring.remove(entity);entity.discard();}return;}
        var s=summons.get(entity.getUUID());if(s!=null&&s.entity==entity&&s.expires>0&&System.currentTimeMillis()>=s.expires){depart(s);return;}if(s==null||s.entity!=entity||!valid(s)){entity.discard();return;}var p=owner(s);long now=System.currentTimeMillis();if(now<s.started)return;
        entity.project(s.health/s.maximum,s.maximumShield>0?s.shield/s.maximumShield:0);
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
            double share=cue.share()/move.contacts().length;
            for(int contact:move.contacts())pulses.add(new Pulse(s,move,target.getUUID(),generation,origin,aim,share,cue.stone(),frenzy(s),now+Math.round(contact/rate)));
            summonVisual(s,UUID.randomUUID().toString(),"START",origin,aim.subtract(origin).normalize(),duration,move.id(),null);
        }
    }
    private void tickSummonEffects(long now){
        for(var slow:new ArrayList<>(slows.values()))if(now>=slow.until||slow.target.isRemoved()||!slow.target.isAlive()){removeSlow(slow);slows.remove(slow.target.getUUID());}
        for(var pulse:new ArrayList<>(pulses)){
            if(!valid(pulse.source)){pulses.remove(pulse);continue;}if(pulse.due>now)continue;pulses.remove(pulse);var s=pulse.source;var p=owner(s);var e=s.entity;Vec3 before=e.getEyePosition();double reach=pulse.move.range();
            if(pulse.move.shape().equals("dash")){Vec3 direction=pulse.aim.subtract(pulse.origin).multiply(1,0,1).normalize();for(int i=0;i<8;i++){Vec3 step=direction.scale(.25);if(!p.level().noCollision(e,e.getBoundingBox().move(step)))break;e.move(MoverType.SELF,step);}reach-=e.getEyePosition().distanceTo(before);}
            issue(pulse,e.getEyePosition(),reach);
        }
    }
    private void summonVisual(Source s,String id,String phase,Vec3 point,Vec3 direction,long duration,String move,JsonObject motion){
        var p=owner(s);if(p==null)return;long now=System.currentTimeMillis();var j=new JsonObject();j.addProperty("caster",p.getStringUUID());j.addProperty("session",s.session);j.addProperty("generation",s.generation);j.addProperty("job",s.job);j.addProperty("dimension",s.dimension);j.addProperty("instance",s.instance);j.addProperty("id",id);j.addProperty("kind",s.skill.id);j.addProperty("summonMove",move);j.addProperty("phase",phase);j.addProperty("sequence",phase.equals("FINISH")?1:0);j.addProperty("seed",id.hashCode());j.addProperty("startedAt",now);j.addProperty("at",now);j.addProperty("serverNow",now);j.addProperty("duration",duration);j.addProperty("cosmeticOnly",true);j.addProperty("color",move.startsWith("ice")||move.startsWith("water")?0x87d9ef:move.contains("flame")||move.contains("fire")||move.contains("meteor")||move.contains("bomb")?0xf6a04b:move.startsWith("shadow")?0xa383df:move.startsWith("king")||move.startsWith("element")||move.startsWith("light")?0x91ddff:0xe7ce84);
        for(String axis:List.of("x","y","z")){double value=axis.equals("x")?point.x:axis.equals("y")?point.y:point.z;j.addProperty(axis,value);j.addProperty("p"+axis,value);j.addProperty("d"+axis,axis.equals("x")?direction.x:axis.equals("y")?direction.y:direction.z);}if(motion!=null)j.add("motion",motion);visuals.accept(p,j);Network.send(p,"visual",j);
    }
    private void applySlow(ServerPlayer owner,LivingEntity target,long occurred){
        long until=occurred+1500;if(until<=System.currentTimeMillis())return;
        var previous=slows.get(target.getUUID());if(previous!=null&&previous.until>=until)return;
        String kind=targets.apply(target).kind;if(kind.equals("BOSS")||kind.equals("WITHER")||target instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon||target instanceof net.minecraft.world.entity.boss.wither.WitherBoss)return;
        var attribute=target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);if(attribute==null)return;attribute.removeModifier(ICE_SLOW);attribute.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(ICE_SLOW,-.2,net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));slows.put(target.getUUID(),new Slow(target,owner.getUUID(),until));
    }
    private void removeSlow(Slow slow){var attribute=slow.target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);if(attribute!=null)attribute.removeModifier(ICE_SLOW);}

    @Override public boolean incoming(SummonedCreature entity,ServerLevel level,DamageSource source,float points){
        Source s=summons.get(entity.getUUID());if(s==null||s.entity!=entity||!valid(s)||source.getEntity() instanceof Player||source.getEntity() instanceof SummonedCreature)return false;
        var j=new JsonObject();String kind=source.getMsgId();boolean direct=source.getEntity() instanceof LivingEntity a&&enemy(a)&&!Set.of("poison","wither","onFire","inFire","lava","thorns").contains(kind);
        j.addProperty("root",UUID.randomUUID().toString());j.addProperty("source",s.id);j.addProperty("session",s.session);j.addProperty("generation",s.generation);j.addProperty("job",s.job);j.addProperty("dimension",s.dimension);j.addProperty("instance",s.instance);j.addProperty("kind",kind);j.addProperty("direct",direct);j.addProperty("points",points);j.addProperty("fall",entity.fallDistance);j.addProperty("velocity",source.getDirectEntity() instanceof net.minecraft.world.entity.projectile.Projectile q?q.getDeltaMovement().length():1);j.addProperty("guard",System.currentTimeMillis()<s.guardUntil);j.addProperty("ownerLevel",s.level);j.addProperty("fireResistance",entity.hasEffect(net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE));j.addProperty("waterBreathing",entity.hasEffect(net.minecraft.world.effect.MobEffects.WATER_BREATHING));
        if(direct&&source.getEntity() instanceof LivingEntity attacker){var target=targets.apply(attacker);j.add("attacker",JSON.toJsonTree(target));j.addProperty("attackerLevel",target.level(progress.apply(owner(s)).level,encounterSync.test(owner(s))));}
        if(source.getSourcePosition()!=null)j.addProperty("distance",entity.position().distanceTo(source.getSourcePosition()));j.add("stats",JSON.toJsonTree(s.stats));Network.send(owner(s),"herderIncoming",j);return true;
    }
    private void receive(ServerPlayer p,JsonObject j){
        identity(p,j);Source s=sources.get(j.get("source").getAsString());if(s==null||!s.owner.equals(p.getUUID()))return;long seq=j.get("sequence").getAsLong();if(seq<=s.sequence)return;
        double health=finite(j.get("health").getAsDouble()),shield=finite(j.get("shield").getAsDouble());String key="clientSummonLife/"+p.getUUID()+"/"+s.session+"/"+s.id;
        try{journal.transaction(Map.of(key,j));}catch(java.io.IOException failure){throw new IllegalStateException(failure);}s.sequence=seq;s.health=health;s.shield=shield;s.lastHurt=j.get("lastHurt").getAsLong();
        if(health<=0)retire(s);else{if(j.has("hurt")&&j.get("hurt").getAsBoolean()&&System.currentTimeMillis()>s.busy){s.entity.play("hit",300);s.busy=System.currentTimeMillis()+300;}entityFeedback(s);sendState(s);}
    }
    private void entityFeedback(Source s){s.entity.project(s.maximum>0?s.health/s.maximum:0,s.maximumShield>0?s.shield/s.maximumShield:0);}
    private void sendState(Source s){var p=owner(s);if(p==null)return;var j=new JsonObject();j.addProperty("session",s.session);j.addProperty("generation",s.generation);j.addProperty("job",s.job);j.addProperty("dimension",s.dimension);j.addProperty("instance",s.instance);j.addProperty("source",s.id);j.addProperty("until",s.expires);j.addProperty("health",s.health);j.addProperty("maximum",s.maximum);j.addProperty("shield",s.shield);j.addProperty("maximumShield",s.maximumShield);j.addProperty("sequence",s.sequence);j.addProperty("lastHurt",s.lastHurt);j.addProperty("kind",s.kind.name());j.addProperty("skill",s.skill.id);j.addProperty("rank",s.rank);j.add("damage",s.damage.deepCopy());Network.send(p,"herderState",j);}
    public void clear(ServerPlayer p){clear(p.getUUID());}
    private void clear(UUID owner){for(var slow:new ArrayList<>(slows.values()))if(slow.owner.equals(owner)){removeSlow(slow);slows.remove(slow.target.getUUID());}for(var s:new ArrayList<>(summons.values()))if(s.owner.equals(owner))remove(s);frenzy.remove(owner);following.remove(owner);preferred.remove(owner);for(var e:new ArrayList<>(retiring.keySet()))if(owner.equals(e.owner)){retiring.remove(e);e.discard();}var p=server.getPlayerList().getPlayer(owner);if(p!=null)buffState(p,0);}
    private void reduced(ServerPlayer p,String skill){if(skill.equals(SummonerSkills.MASTERY)){clear(p);return;}if(skill.equals(SummonerSkills.FRENZY)){frenzy.remove(p.getUUID());buffState(p,0);}for(var s:new ArrayList<>(summons.values()))if(s.owner.equals(p.getUUID())&&s.skill.id.equals(skill))remove(s);}
    private void retire(Source s){retiring.put(s.entity,System.currentTimeMillis()+1800);s.entity.play("death",1200);s.entity.getNavigation().stop();remove(s);}
    private void depart(Source s){retiring.put(s.entity,System.currentTimeMillis()+800);s.entity.play("retreat",800);s.entity.getNavigation().stop();remove(s);}
    private void remove(Source s){pulses.removeIf(x->x.source==s);s.expires=0;summons.remove(s.entity.getUUID());sources.remove(s.id);if(!retiring.containsKey(s.entity))s.entity.discard();var p=owner(s);if(p!=null){var j=new JsonObject();j.addProperty("session",s.session);j.addProperty("generation",s.generation);j.addProperty("job",s.job);j.addProperty("dimension",s.dimension);j.addProperty("instance",s.instance);j.addProperty("source",s.id);j.addProperty("kind",s.kind.name());j.addProperty("skill",s.skill.id);j.addProperty("until",0);Network.send(p,"herderState",j);}}
    @Override public CombatPorts.Commit prepare(ServerPlayer p,LivingEntity target,JsonObject j,Receipt receipt,Life actor){
        if(!receipt.accepted()||receipt.actual()<=0||!enemy(target))return CombatPorts.Commit.NONE;Source s=j.has("summonSource")?sources.get(j.get("summonSource").getAsString()):null;
        return new CombatPorts.Commit(){public void publish(){preferred.put(p.getUUID(),target.getUUID());if(j.has("summonSlow")&&j.get("summonSlow").getAsBoolean())applySlow(p,target,System.currentTimeMillis());if(s!=null&&s.owner.equals(p.getUUID())){if(!s.entity.isRemoved()&&target instanceof Mob mob&&!targets.apply(target).kind.equals("BOSS")&&!targets.apply(target).kind.equals("WITHER")){mob.setLastHurtByMob(s.entity);mob.setTarget(s.entity);}}}};
    }
}
