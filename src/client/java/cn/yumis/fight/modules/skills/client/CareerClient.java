package cn.yumis.fight.modules.skills.client;

import cn.yumis.fight.modules.skills.api.*;
import cn.yumis.fight.modules.skills.api.client.*;
import cn.yumis.fight.modules.attributes.api.*;
import cn.yumis.fight.modules.character.api.*;
import cn.yumis.fight.modules.combat.api.Life;
import cn.yumis.fight.modules.combat.api.ProjectileSpawn;
import cn.yumis.fight.modules.presentation.api.client.ClientUi;
import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import java.util.*;

/** Client-side career geometry and effect clocks; all results use the normal receipt path. */
public final class CareerClient {
    private static final Gson JSON=new Gson();
    public static final CareerClient INSTANCE=new CareerClient();
    public CareerState view=new CareerState();
    private final Map<String,Long> pending=new HashMap<>();
    private final Map<String,Integer> pendingSlots=new HashMap<>();
    private final Set<String> released=new HashSet<>();
    private final Map<String,SkillCast> sources=new HashMap<>();
    private final Map<String,Long> fieldTicks=new HashMap<>(),freezes=new HashMap<>();
    private final Map<UUID,Flame> flames=new HashMap<>();
    private final List<Orb> orbs=new ArrayList<>();
    private final List<CareerWave> waves=new ArrayList<>();
    private final List<FireBall> fireBalls=new ArrayList<>();
    private final Map<String,OrbClock> orbClocks=new HashMap<>();
    private final List<FlameBolt> bolts=new ArrayList<>();
    private final AsuraClient asura=new AsuraClient();
    private String session="",job="",dimension="",instance="";
    private long generation=-1;
    private static final class Flame {LivingEntity target;SkillCast source;int stacks,sequence;long expires,next,started;String visualId;}
    private static final class Orb {SkillCast source;LivingEntity target;Vec3 position;double remaining=10;long targetGeneration,next,lastKeepalive,visualStarted;int index,visualSequence,visualEpoch;String visualId;}
    private static final class OrbClock {long next,lastServer;}
    private static final class FireBall {SkillCast source;FireWavePath path;Vec3 position;long next,lastSend;double progress;boolean waiting,confirmed,explosionShown;final Set<UUID> captured=new LinkedHashSet<>(),impactTargets=new LinkedHashSet<>();}
    private static final class CareerWave {
        SkillCast source;Vec3 position;double travelled;long next;
        final Map<UUID,Integer> hits=new HashMap<>();
        final Map<UUID,Long> nextHit=new HashMap<>();
    }
    private static final class FlameBolt {SkillCast source;Vec3 origin,position,direction;double remaining,range;long next,started;String id;int sequence;}
    private CareerClient(){}
    private SkillsClient state(){return SkillsClient.current();}
    public void clear(){asura.clear();OracleClient.INSTANCE.clear();for(var source:sources.values()){SkillController.INSTANCE.stopVisual(source);TrainingCombat.release(source.id,"persistent");TrainingCombat.actionFinished(source.id);}for(var orb:orbs){orbVisual(orb,"CANCEL");TrainingCombat.release(orb.source.id,"orb");}for(var wave:waves)TrainingCombat.release(wave.source.id,"wave");for(var bolt:bolts)TrainingCombat.release(bolt.source.id,bolt.id);for(var mark:flames.values()){attachmentVisual(mark,"CANCEL");TrainingCombat.release(mark.source.id,"flame/"+mark.target.getUUID());}for(var ball:fireBalls)TrainingCombat.release(ball.source.id,"fire");fireBalls.clear();orbClocks.clear();pending.clear();pendingSlots.clear();released.clear();sources.clear();fieldTicks.clear();freezes.clear();flames.clear();orbs.clear();waves.clear();bolts.clear();view=new CareerState();}
    public void receive(JsonObject data){
        CareerState next=JSON.fromJson(data,CareerState.class);
        if(!state().session().equals(next.session)||next.generation!=state().own().generation)return;
        for(var reduction:next.reductions.entrySet())if(reduction.getValue()>view.reductions.getOrDefault(reduction.getKey(),0L))SkillReductions.resources(reduction.getKey(),state().resources());
        if(view.field!=null&&(next.field==null||!next.field.id.equals(view.field.id))){TrainingCombat.release(view.field.id,"persistent");var old=sources.get(view.field.id);if(old!=null)SkillController.INSTANCE.stopVisual(old);}
        if(view.kallaUntil>0&&next.kallaUntil==0)for(var source:sources.values())if(source.skill.id.equals(CareerSkills.KALLA)&&!source.terminal)TrainingCombat.release(source.id,"persistent");
        if(view.eyeUntil>0&&next.eyeUntil==0)for(var source:sources.values())if(source.skill.id.equals(CareerSkills.EYE)&&!source.terminal)TrainingCombat.release(source.id,"persistent");
        for(var source:sources.values())if(!source.terminal&&(
            source.skill.id.equals(CareerSkills.KALLA)&&view.kallaUntil>0&&next.kallaUntil==0||
            source.skill.id.equals(CareerSkills.EYE)&&view.eyeUntil>0&&next.eyeUntil==0||
            source.skill.id.equals(CareerSkills.SWORD)&&view.bloodUntil>0&&next.bloodUntil==0))SkillController.INSTANCE.stopVisual(source);
        view=next;
    }
    public void rejected(JsonObject data){String id=data.get("id").getAsString();pending.remove(id);pendingSlots.remove(id);released.remove(id);state().message(data.get("error").getAsString());}
    public void released(int slot){pendingSlots.forEach((id,value)->{if(value>=0&&value%8==slot)released.add(id);});}
    public boolean waiting(){return !pending.isEmpty();}
    public void request(SkillDefinition skill,int slot){
        if(waiting())return;var p=Minecraft.getInstance().player;if(p==null)return;
        int rank=SkillLearning.effective(state().stats(),state().progress(),skill);if(rank<=0||skill.passive())return;
        state().upload();JsonObject j=new JsonObject();String id=UUID.randomUUID().toString();j.addProperty("id",id);j.addProperty("skill",skill.id);j.addProperty("slot",slot);j.addProperty("session",state().session());j.addProperty("generation",state().own().generation);pending.put(id,ClientUi.now());pendingSlots.put(id,slot);state().send(CareerSkills.contains(skill.id)?"careerCast":"skillCast",j);
    }
    public void started(JsonObject ack){
        String id=ack.get("id").getAsString();if(!pending.containsKey(id))return;pending.remove(id);pendingSlots.remove(id);boolean wasReleased=released.remove(id);
        if(!state().session().equals(ack.get("session").getAsString())||state().own().generation!=ack.get("generation").getAsLong())return;
        long now=ClientUi.now(),serverNow=ack.get("serverNow").getAsLong(),offset=now-serverNow;
        var incoming=JSON.fromJson(ack.get("resources"),cn.yumis.fight.modules.combat.api.Runtime.class);var runtime=state().resources();
        runtime.mp=incoming.mp;runtime.controlEpoch=incoming.controlEpoch;runtime.recordedAt=now;
        runtime.equipmentBuffs=incoming.equipmentBuffs;for(var buff:runtime.equipmentBuffs.values())buff.expires+=offset;
        runtime.cooldowns.clear();incoming.cooldowns.forEach((key,value)->runtime.cooldowns.put(key,value+offset));
        Life life=JSON.fromJson(ack.get("life"),Life.class);if(life.revision>=state().own().revision){state().own().q=life.q;state().own().revision=life.revision;}
        if(ack.has("instant")&&ack.get("instant").getAsBoolean()){Effects.spark(Minecraft.getInstance().player.position().add(0,1,0),0xb4f2ff);return;}
        SkillCast c=new SkillCast();c.id=id;c.skill=SkillCatalog.require(ack.get("skill").getAsString());c.slot=ack.get("slot").getAsInt();c.terminal=ack.get("terminal").getAsBoolean();
        Map<String,Double> parameters=new LinkedHashMap<>();ack.getAsJsonObject("parameters").entrySet().forEach(e->parameters.put(e.getKey(),e.getValue().getAsDouble()));
        c.parameters=new SkillParameters.Snapshot(c.skill,ack.get("rank").getAsInt(),parameters);
        c.equipment=ack.has("equipmentEffects")?JSON.fromJson(ack.get("equipmentEffects"),cn.yumis.fight.modules.equipment.api.EquipmentEffects.Snapshot.class):state().resources().equipment;c.stats=JSON.fromJson(ack.get("stats"),Stats.class);c.weapon=c.stats.weaponInstance;c.speed=Math.max(.01,c.stats.get(Stat.CAST_SPEED));c.origin=vector(ack.getAsJsonArray("origin"));c.center=vector(ack.getAsJsonArray("center"));c.direction=vector(ack.getAsJsonArray("direction"));c.start=c.stageStart=now;c.dimension=Minecraft.getInstance().level.dimension().identifier().toString();c.charging=SkillPresentation.charging(c.skill);
        c.release=c.charging&&wasReleased;sources.put(id,c);SkillController.INSTANCE.begin(c);
    }
    public Stats snapshot(Stats base){var p=Minecraft.getInstance().player;long now=state().serverNow();return p==null?base:CareerRules.current(base,state().progress(),view,state().predicted(state().own()),view.red(p.getX(),p.getY(),p.getZ(),now),view.ownField(p.getX(),p.getY(),p.getZ(),now),now);}
    public boolean frenzy(){return view.frenzy&&state().progress().hasJob(JobCatalog.BERSERKER);}
    public boolean eye(){return view.eyeUntil>state().serverNow();}
    public int visualColor(Stats stats,int fallback){return stats.modifiers.getOrDefault("careerEye",0d)>0?CareerRules.EYE_COLOR:fallback;}
    public int basicColor(Stats stats){if(stats.modifiers.getOrDefault("careerEye",0d)>0)return CareerRules.EYE_COLOR;if(stats.modifiers.getOrDefault("careerFrenzy",0d)>0)return 0xe63951;return SkillLearning.effective(stats,state().progress(),SkillCatalog.require(CareerSkills.KEIGA))>0?0x8e56d9:0xb4f2ff;}
    public boolean invulnerable(SkillCast c){if(c==null)return false;if(c.skill.id.equals(CareerSkills.PHANTOM))return (ClientUi.now()-c.start)*c.speed<=250;return c.terminal&&(c.skill.id.equals(CareerSkills.EYE)||c.skill.id.equals(CareerSkills.SWORD));}
    void eyeStarted(SkillCast cast,long now){
        for(var old:sources.values())if(!old.id.equals(cast.id)&&old.skill.id.equals(CareerSkills.EYE)&&!old.terminal)TrainingCombat.release(old.id,"persistent");
        TrainingCombat.hold(cast.id,"persistent",now+20000);
    }
    public void cancel(SkillCast c){if(c==null)return;OracleClient.INSTANCE.cancel(c.id);TrainingCombat.release(c.id,"persistent");JsonObject j=new JsonObject();j.addProperty("id",c.id);state().send("careerCancel",j);sources.remove(c.id);}
    public void decorate(SkillCast source,LivingEntity target,JsonObject extra){
        if(source==null)return;
        if(!source.eyeGear.isEmpty()){extra.addProperty("eyeGear",source.eyeGear);extra.add("eyePoint",JSON.toJsonTree(new double[]{source.center.x,source.center.y,source.center.z}));}
        if(MageClient.decorate(source,extra))return;
        if(CareerSkills.contains(source.skill.id)){extra.addProperty("careerCast",source.id);extra.addProperty("careerIndex",source.hitIndex);if(source.skill.id.equals(CareerSkills.ORB))extra.addProperty("orbAt",source.scheduledHitAt);if(source.controlSeconds>0){String key=source.id+"/"+target.getUUID();long now=ClientUi.now();if(!source.skill.id.equals(CareerSkills.FIELD)||freezes.getOrDefault(key,0L)<=now){extra.addProperty("careerControl",source.controlSeconds);freezes.put(key,now+3000);}}}
        else extra.addProperty("sourceCast",source.id);
    }
    public void orb(SkillCast c,SkillBehaviors.Context context){Orb orb=new Orb();orb.source=c;orb.position=c.origin.add(0,.8,0);orb.remaining=c.parameters.get("range",10);orb.next=ClientUi.now();orb.visualId=c.id;orb.visualStarted=c.start;orb.visualSequence=c.visualSequence;orbs.add(orb);c.center=orb.position.add(0,-.8,0);TrainingCombat.hold(c.id,"orb",ClientUi.now()+12000);orbVisual(orb,"RELEASE");}
    private void orbVisual(Orb orb,String phase){
        var p=Minecraft.getInstance().player;if(p==null)return;long now=ClientUi.now();
        if(!phase.equals("CANCEL")&&!phase.equals("FINISH")&&now-orb.visualStarted>=30000){orbVisual(orb,"CANCEL");orb.visualId=UUID.nameUUIDFromBytes((orb.source.id+"/orb/"+(++orb.visualEpoch)).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();orb.visualStarted=now;orb.visualSequence=-1;orbVisual(orb,"START");phase="RELEASE";}
        JsonObject j=new JsonObject();j.addProperty("caster",p.getStringUUID());j.addProperty("session",state().session());j.addProperty("generation",state().own().generation);j.addProperty("id",orb.visualId);j.addProperty("kind",CareerSkills.ORB);j.addProperty("phase",phase);j.addProperty("sequence",++orb.visualSequence);j.addProperty("seed",orb.visualId.hashCode());j.addProperty("startedAt",state().serverNow()-(now-orb.visualStarted));j.addProperty("at",state().serverNow());j.addProperty("duration",12000);j.addProperty("cosmeticOnly",true);j.addProperty("color",0xc9bbeb);
        j.addProperty("x",orb.position.x);j.addProperty("y",orb.position.y);j.addProperty("z",orb.position.z);j.addProperty("px",orb.position.x);j.addProperty("py",orb.position.y);j.addProperty("pz",orb.position.z);j.addProperty("dx",orb.source.direction.x);j.addProperty("dy",orb.source.direction.y);j.addProperty("dz",orb.source.direction.z);
        JsonObject motion=SkillController.motion(orb.target==null?orb.remaining:0,orb.target==null?orb.remaining/8*1000:0,false);
        if(orb.target!=null){motion.addProperty("target",orb.target.getStringUUID());motion.addProperty("targetEntity",orb.target.getId());}j.add("motion",motion);Effects.receive(j);
        if(orb.target!=null&&TrainingCombat.context(orb.target)!=null){j=j.deepCopy();j.getAsJsonObject("motion").remove("target");j.getAsJsonObject("motion").remove("targetEntity");}state().send("visual",j);
    }
    public void wave(SkillCast c,SkillBehaviors.Context context){
        if(c.skill.id.equals(CareerSkills.FIRE)){FireBall ball=new FireBall();ball.source=c;ball.path=FireWavePath.from(c.parameters);ball.position=ball.path.origin();ball.next=ClientUi.now()+50;fireBalls.add(ball);c.center=ball.position;TrainingCombat.hold(c.id,"fire",ClientUi.now()+5000);context.visual(c,"RELEASE");return;}
        CareerWave wave=new CareerWave();wave.source=c;wave.position=c.origin.add(0,1.2,0);wave.next=ClientUi.now();c.center=wave.position;waves.add(wave);TrainingCombat.hold(c.id,"wave",ClientUi.now()+5000);context.visual(c,"RELEASE");
    }
    public void fireImpact(JsonObject ack){
        if(!state().session().equals(ack.get("session").getAsString())||state().own().generation!=ack.get("generation").getAsLong())return;
        for(var ball:fireBalls)if(ball.source.id.equals(ack.get("id").getAsString())){ball.position=new Vec3(ack.get("x").getAsDouble(),ack.get("y").getAsDouble(),ack.get("z").getAsDouble());ack.getAsJsonArray("captured").forEach(id->ball.captured.add(UUID.fromString(id.getAsString())));ball.waiting=true;ball.confirmed=true;break;}
    }
    public boolean basic(Stats stats,String cycle,JsonObject content){
        if(asura.basic(cycle,content,sources))return true;
        long now=state().serverNow();if(view.kallaUntil<=now)return false;
        SkillCast source=sources.values().stream().filter(c->c.skill.id.equals(CareerSkills.KALLA)&&!c.terminal).max(Comparator.comparingLong(c->c.start)).orElse(null);if(source==null)return false;
        if(!content.has("kallaProjectile"))return false;var launch=content.getAsJsonObject("kallaProjectile");FlameBolt bolt=new FlameBolt();bolt.source=source;bolt.origin=bolt.position=ProjectileSpawn.vector(launch.getAsJsonArray("origin"));bolt.direction=ProjectileSpawn.vector(launch.getAsJsonArray("direction"));bolt.remaining=bolt.range=launch.get("range").getAsDouble();bolt.started=launch.get("at").getAsLong();bolt.next=ClientUi.now()-Math.clamp(state().serverNow()-bolt.started,0,2000);bolt.id=UUID.nameUUIDFromBytes((cycle+"/kalla").getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();bolts.add(bolt);TrainingCombat.hold(source.id,bolt.id,ClientUi.now()+2000);flameVisual(bolt,"START");return false;
    }
    private void flameVisual(FlameBolt b,String phase){var p=Minecraft.getInstance().player;if(p==null)return;JsonObject j=new JsonObject();j.addProperty("caster",p.getStringUUID());j.addProperty("session",state().session());j.addProperty("generation",state().own().generation);j.addProperty("id",b.id);j.addProperty("kind",CareerSkills.KALLA);j.addProperty("phase",phase);j.addProperty("sequence",b.sequence++);j.addProperty("seed",b.id.hashCode());j.addProperty("startedAt",b.started);j.addProperty("at",state().serverNow());j.addProperty("duration",2000);j.addProperty("color",0x8f43d0);j.addProperty("x",b.origin.x);j.addProperty("y",b.origin.y);j.addProperty("z",b.origin.z);j.addProperty("px",b.position.x);j.addProperty("py",b.position.y);j.addProperty("pz",b.position.z);j.addProperty("dx",b.direction.x);j.addProperty("dy",b.direction.y);j.addProperty("dz",b.direction.z);if(phase.equals("START")){var motion=SkillController.motion(b.range,1000d*b.range/15,false);motion.addProperty("at",b.started);j.add("motion",motion);}Effects.receive(j);state().send("visual",j);}
    private void flame(LivingEntity target,SkillCast source){
        var mark=flames.get(target.getUUID());boolean first=mark==null||!mark.source.id.equals(source.id);
        if(first){if(mark!=null){attachmentVisual(mark,"CANCEL");TrainingCombat.release(mark.source.id,"flame/"+target.getUUID());}mark=new Flame();mark.target=target;mark.source=source;mark.next=ClientUi.now()+500;mark.started=ClientUi.now();mark.visualId=UUID.nameUUIDFromBytes((source.id+"/flame/"+target.getUUID()).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();flames.put(target.getUUID(),mark);}
        mark.stacks=Math.min(3,mark.stacks+1);mark.expires=ClientUi.now()+3000;TrainingCombat.hold(source.id,"flame/"+target.getUUID(),mark.expires+50);attachmentVisual(mark,first?"START":"SEGMENT");
    }
    private void attachmentVisual(Flame mark,String phase){
        var p=Minecraft.getInstance().player;if(p==null)return;Vec3 at=mark.target.getBoundingBox().getCenter();JsonObject j=new JsonObject();j.addProperty("caster",p.getStringUUID());j.addProperty("session",state().session());j.addProperty("generation",state().own().generation);j.addProperty("id",mark.visualId);j.addProperty("kind",CareerSkills.KALLA);j.addProperty("phase",phase);j.addProperty("sequence",mark.sequence++);j.addProperty("seed",mark.visualId.hashCode());j.addProperty("startedAt",state().serverNow()-(ClientUi.now()-mark.started));j.addProperty("at",state().serverNow());j.addProperty("duration",Math.min(40000,mark.expires-mark.started));j.addProperty("cosmeticOnly",true);j.addProperty("terminal",true);j.addProperty("charge",mark.stacks/3d);j.addProperty("color",0x772daa);j.addProperty("x",at.x);j.addProperty("y",at.y);j.addProperty("z",at.z);j.addProperty("px",at.x);j.addProperty("py",at.y);j.addProperty("pz",at.z);j.addProperty("dx",0);j.addProperty("dy",0);j.addProperty("dz",1);
        var motion=SkillController.motion(0,0,false);motion.addProperty("target",mark.target.getStringUUID());motion.addProperty("targetEntity",mark.target.getId());j.add("motion",motion);Effects.receive(j);
        if(TrainingCombat.context(mark.target)!=null){j=j.deepCopy();j.getAsJsonObject("motion").remove("target");j.getAsJsonObject("motion").remove("targetEntity");}state().send("visual",j);
    }
    public Vec3 firstTarget(SkillCast c,double length,double width){var p=Minecraft.getInstance().player;LivingEntity closest=null;double min=length+1;for(var e:SkillController.INSTANCE.targets(new AABB(c.origin,c.origin.add(c.direction.scale(length))).inflate(width/2,2,width/2))){Vec3 delta=e.position().subtract(c.origin);double forward=delta.dot(c.direction);if(forward>=0&&forward<min&&SkillController.INSTANCE.sight(c.origin.add(0,1,0),e.getBoundingBox().getCenter())){closest=e;min=forward;}}return closest==null?c.origin.add(c.direction.scale(length)):closest.position();}
    public void tick(SkillBehaviors.Context context){
        var mc=Minecraft.getInstance();var p=mc.player;if(p==null||mc.level==null)return;
        String currentDimension=mc.level.dimension().identifier().toString();
        if(!session.equals(state().session())||generation!=state().own().generation||!job.equals(state().progress().jobId())||!dimension.equals(currentDimension)||!instance.equals(state().own().instance)||state().own().dead){clear();session=state().session();generation=state().own().generation;job=state().progress().jobId();dimension=currentDimension;instance=state().own().instance;if(!state().own().dead)state().send("careerState",new JsonObject());}
        long now=ClientUi.now(),wall=state().serverNow();
        OracleClient.INSTANCE.tick(now);
        for(var source:sources.values()){
            if(source.skill.id.equals(CareerSkills.FIELD)&&(now-source.start>=12000||view.field!=null&&view.field.id.equals(source.id)&&view.field.expires<=wall))TrainingCombat.release(source.id,"persistent");
            if(source.skill.id.equals(CareerSkills.KALLA)&&!source.terminal&&(now-source.start>=20000||view.kallaUntil>0&&view.kallaUntil<=wall))TrainingCombat.release(source.id,"persistent");
            if(source.skill.id.equals(CareerSkills.EYE)&&!source.terminal&&(now-source.start>=20000||view.eyeUntil>0&&view.eyeUntil<=wall))TrainingCombat.release(source.id,"persistent");
        }
        cn.yumis.fight.modules.equipment.api.EquipmentStats.applyMovement(p,SkillController.INSTANCE.snapshot());
        pending.entrySet().removeIf(e->{if(now-e.getValue()>5000){pendingSlots.remove(e.getKey());released.remove(e.getKey());state().message("技能施放确认超时，请重试");return true;}return false;});
        if(view.field!=null&&view.field.expires>wall){SkillCast c=sources.get(view.field.id);if(c!=null){long next=fieldTicks.getOrDefault(c.id,c.start+1000);while(now>=next&&next<c.start+12001){int index=(int)((next-c.start)/1000)-1;c.center=new Vec3(view.field.x,view.field.y,view.field.z);c.dot=true;c.hit.clear();if(view.field.rank>=8){c.hitIndex=index;c.elementOverride=2;c.controlSeconds=.5;context.area(c,5,c.parameters.get("iceDot",0),false);}if(view.field.rank>=15){c.hit.clear();c.hitIndex=16+index;c.elementOverride=3;c.controlSeconds=0;context.area(c,5,c.parameters.get("darkDot",0),false);}next+=1000;}fieldTicks.put(c.id,next);}}
        for(var bolt:new ArrayList<>(bolts)){while(now>=bolt.next&&bolt.remaining>0){double step=Math.min(.75,bolt.remaining);Vec3 from=bolt.position,to=from.add(bolt.direction.scale(step));if(!SkillController.INSTANCE.sight(from,to)){bolt.remaining=0;break;}bolt.position=to;bolt.remaining-=step;bolt.next+=50;var hit=SkillController.INSTANCE.targets(new AABB(from,to).inflate(.35)).stream().findFirst().orElse(null);if(hit!=null){flame(hit,bolt.source);bolt.remaining=0;}}if(bolt.remaining<=0){flameVisual(bolt,"FINISH");TrainingCombat.release(bolt.source.id,bolt.id);bolts.remove(bolt);}}
        for(var mark:new ArrayList<>(flames.values())){if(!mark.target.isAlive()||mark.expires<now){attachmentVisual(mark,"CANCEL");TrainingCombat.release(mark.source.id,"flame/"+mark.target.getUUID());flames.remove(mark.target.getUUID());continue;}while(mark.next<=now&&mark.next<=mark.expires){SkillCast c=mark.source;c.dot=true;c.elementOverride=3;c.controlSeconds=0;c.hitIndex=64+(int)((mark.next-c.start)/500);context.hit(c,mark.target,c.parameters.get("flameDot",0)*mark.stacks);mark.next+=500;}}
        for(var orb:new ArrayList<>(orbs)){
            SkillCast c=orb.source;
            TrainingCombat.touch(c.id);
            if(now-orb.lastKeepalive>=1000){JsonObject keep=new JsonObject();keep.addProperty("id",c.id);state().send("orbKeepalive",keep);orb.lastKeepalive=now;orbVisual(orb,"SEGMENT");}
            while(orb.target==null&&orb.remaining>0&&now>=orb.next){
                Vec3 from=orb.position,next=from.add(c.direction.scale(Math.min(.4,orb.remaining)));orb.next+=50;
                if(!SkillController.INSTANCE.sight(from,next)){orb.remaining=0;break;}
                orb.position=next;orb.remaining-=.4;
                var target=SkillController.INSTANCE.targets(new AABB(from,next).inflate(.8)).stream().filter(t->SkillController.INSTANCE.sight(from,t.getBoundingBox().getCenter())).min(Comparator.comparingDouble(t->from.distanceToSqr(t.getBoundingBox().getCenter()))).orElse(null);
                if(target!=null){var data=state().context(target);if(data!=null&&!data.life.dead){orb.target=target;orb.targetGeneration=data.life.generation;orb.next=now+Math.max(1,(long)(c.parameters.get("interval",.5)*1000));orbVisual(orb,"SEGMENT");}}
            }
            if(orb.target!=null){
                var data=state().context(orb.target);
                if(!orb.target.isAlive()||data==null||data.life.dead||data.life.generation!=orb.targetGeneration){c.center=orb.position.add(0,-.8,0);orbVisual(orb,"FINISH");TrainingCombat.release(c.id,"orb");orbs.remove(orb);continue;}
                orb.position=orb.target.getBoundingBox().getCenter();
                int count=(int)c.parameters.get("slashes",CareerSkills.orbHits(1));long interval=Math.max(1,(long)(c.parameters.get("interval",.5)*1000));
                // Shared target clocks below select only one eligible orb every 350 ms.
                if(orb.index>=count){c.center=orb.position.add(0,-.8,0);orbVisual(orb,"FINISH");TrainingCombat.release(c.id,"orb");orbs.remove(orb);continue;}
            }else if(orb.remaining<=0){c.center=orb.position.add(0,-.8,0);orbVisual(orb,"FINISH");TrainingCombat.release(c.id,"orb");orbs.remove(orb);continue;}
            c.center=orb.position.add(0,-.8,0);if(now-c.visualTrail>=100){context.visual(c,"TRAIL");c.visualTrail=now;}
        }
        for(var orb:orbs.stream().filter(o->o.target!=null&&o.index<o.source.parameters.get("slashes",0)&&o.next<=now).sorted(Comparator.comparingLong((Orb o)->o.next).thenComparing(o->o.source.id)).toList()){
            String key=orb.target.getStringUUID()+"/"+orb.targetGeneration;OrbClock clock=orbClocks.computeIfAbsent(key,k->new OrbClock());
            if(now<clock.next)continue;
            SkillCast c=orb.source;c.hitIndex=orb.index;c.scheduledHitAt=Math.max(clock.lastServer+SkillResourceRules.ORB_INTERVAL,state().serverNow());
            if(context.hit(c,orb.target,c.parameters.get("damage",0))){orb.index++;orb.next=now+SkillResourceRules.ORB_INTERVAL;clock.next=orb.next;clock.lastServer=c.scheduledHitAt;}
        }
        orbClocks.entrySet().removeIf(entry->entry.getValue().next+5000<now);
        asura.tick(context,sources.values().stream().filter(c->c.skill.id.equals(CareerSkills.EYE)&&!c.terminal).max(Comparator.comparingLong(c->c.start)).orElse(null),view.eyeUntil);
        for(var ball:new ArrayList<>(fireBalls))advanceFire(ball,context,now);
        for(var wave:new ArrayList<>(waves))advance(wave,context,now);
        if(!waiting()&&SkillController.INSTANCE.cast==null&&!p.hasEffect(cn.yumis.fight.modules.combat.api.CombatContentTypes.STUN)){
            if(view.eyeUntil>0&&view.eyeUntil<=wall)request(SkillCatalog.require(CareerSkills.EYE),-1);
            else if(view.bloodUntil>0&&view.bloodUntil<=wall)request(SkillCatalog.require(CareerSkills.SWORD),-1);
        }
        sources.values().removeIf(c->orbs.stream().noneMatch(o->o.source==c)&&fireBalls.stream().noneMatch(ball->ball.source==c)&&!(c.skill.id.equals(CareerSkills.SWORD)&&!c.terminal&&view.bloodUntil>0)&&now-c.start>Math.max(45000,(long)(c.parameters.get("duration",1)*1000)+5000));
    }
    private void advanceFire(FireBall ball,SkillBehaviors.Context context,long now){
        SkillCast c=ball.source;TrainingCombat.touch(c.id);var mc=Minecraft.getInstance();
        if(now-c.start>10000){context.visual(c,"CANCEL");TrainingCombat.release(c.id,"fire");cancel(c);fireBalls.remove(ball);return;}
        if(ball.confirmed){
            c.center=ball.position;c.hitIndex=0;
            if(!ball.explosionShown){
                for(var target:SkillController.INSTANCE.targets(new AABB(c.center,c.center).inflate(2.5,4,2.5)))if(target.getBoundingBox().getCenter().distanceToSqr(c.center)<=Math.pow(2.5+target.getBbWidth(),2)+4&&SkillController.INSTANCE.sight(c.center,target.getBoundingBox().getCenter()))ball.impactTargets.add(target.getUUID());
                ball.impactTargets.addAll(ball.captured);ball.explosionShown=true;context.visual(c,"FINISH");
            }
            for(UUID id:new ArrayList<>(ball.impactTargets)){
                var entity=mc.level.getEntity(id);if(!(entity instanceof LivingEntity target)||!target.isAlive()){ball.impactTargets.remove(id);continue;}
                var known=state().context(target);if(known==null)continue;if(known.life.dead||context.hit(c,target,c.parameters.get("damage",0)))ball.impactTargets.remove(id);
            }
            if(ball.impactTargets.isEmpty()){TrainingCombat.release(c.id,"fire");fireBalls.remove(ball);}return;
        }
        if(ball.waiting){if(now-ball.lastSend>=1000)sendFire(ball);return;}
        while(now>=ball.next&&!ball.waiting){
            double progress=Math.min(1,(ball.next-c.start)/ball.path.millis());Vec3 from=ball.position,to=ball.path.point(progress);ball.next+=50;
            var wall=mc.level.clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,mc.player));
            boolean blocked=wall.getType()!=HitResult.Type.MISS;
            if(blocked)to=wall.getLocation().subtract(to.subtract(from).normalize().scale(.05));
            for(var target:SkillController.INSTANCE.targets(new AABB(from,to).inflate(2.5))){var data=state().context(target);if(data!=null&&!data.kind.equals("BOSS")&&target.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE)<1&&SkillController.INSTANCE.sight(to,target.getBoundingBox().getCenter()))ball.captured.add(target.getUUID());}
            ball.position=to;ball.progress=progress;c.center=to;sendFire(ball);
            if(blocked||progress>=1)ball.waiting=true;
        }
    }
    private void sendFire(FireBall ball){JsonObject request=new JsonObject();request.addProperty("id",ball.source.id);request.addProperty("progress",ball.progress);state().send("fireAdvance",request);ball.lastSend=ClientUi.now();}
    private void advance(CareerWave wave,SkillBehaviors.Context context,long now){
        TrainingCombat.touch(wave.source.id);SkillCast c=wave.source;double range=c.parameters.get("range",12),width=2+2*c.moved;
        while(waves.contains(wave)&&now>=wave.next){
            Vec3 from=wave.position,next=from.add(c.direction.scale(Math.min(.75,range-wave.travelled)));long step=wave.next;wave.next+=50;
            if(!SkillController.INSTANCE.sight(from.add(0,.2,0),next.add(0,.2,0))){finish(wave,context);break;}
            wave.position=next;wave.travelled=Math.min(range,wave.travelled+.75);c.center=next;
            for(var target:SkillController.INSTANCE.targets(new AABB(from,next).inflate(width/2,width/2,width/2))){
                int index=wave.hits.getOrDefault(target.getUUID(),0);if(index>=3||step<wave.nextHit.getOrDefault(target.getUUID(),0L)||!SkillController.INSTANCE.sight(from,target.getBoundingBox().getCenter()))continue;
                c.hitIndex=index;double coefficient=c.parameters.get("damage",0)*(1+2*c.moved)/3;
                if(context.hit(c,target,coefficient)){wave.hits.put(target.getUUID(),index+1);wave.nextHit.put(target.getUUID(),step+50);}
            }
            if(wave.travelled>=range){finish(wave,context);break;}
        }
    }
    private void finish(CareerWave wave,SkillBehaviors.Context context){SkillCast c=wave.source;c.center=wave.position;context.visual(c,"CANCEL");TrainingCombat.release(c.id,"wave");waves.remove(wave);}
    private static Vec3 vector(JsonArray a){return new Vec3(a.get(0).getAsDouble(),a.get(1).getAsDouble(),a.get(2).getAsDouble());}
}
