package cn.yumis.fight.modules.skills.client;

import cn.yumis.fight.modules.skills.api.*;
import cn.yumis.fight.modules.skills.api.client.*;
import cn.yumis.fight.modules.attributes.api.*;
import cn.yumis.fight.modules.character.api.*;
import cn.yumis.fight.modules.combat.api.Life;
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
    private final List<FlameBolt> bolts=new ArrayList<>();
    private String session="",job="",dimension="";
    private long generation=-1;
    private static final class Flame {LivingEntity target;SkillCast source;int stacks;long expires,next;}
    private static final class Orb {SkillCast source;LivingEntity target;Vec3 position;double remaining=10;long targetGeneration,next;int index;}
    private static final class CareerWave {
        SkillCast source;Vec3 position;double travelled,floor;long next;
        final Map<UUID,Integer> hits=new HashMap<>();
        final Map<UUID,Long> nextHit=new HashMap<>();
    }
    private static final class FlameBolt {SkillCast source;Vec3 position,direction;double remaining=10;long next;}
    private CareerClient(){}
    private SkillsClient state(){return SkillsClient.current();}
    public void clear(){for(var source:sources.values())SkillController.INSTANCE.stopVisual(source);pending.clear();pendingSlots.clear();released.clear();sources.clear();fieldTicks.clear();freezes.clear();flames.clear();orbs.clear();waves.clear();bolts.clear();view=new CareerState();}
    public void receive(JsonObject data){
        CareerState next=JSON.fromJson(data,CareerState.class);
        if(!state().session().equals(next.session)||next.generation!=state().own().generation)return;
        if(view.field!=null&&(next.field==null||!next.field.id.equals(view.field.id))){var old=sources.get(view.field.id);if(old!=null)SkillController.INSTANCE.stopVisual(old);}
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
        if(c.skill==SkillCatalog.GHOST){int bonus=SkillLearning.effective(state().stats(),state().progress(),SkillCatalog.require(CareerSkills.SOUL_SLASH));if(bonus>0){var values=new LinkedHashMap<>(c.parameters.values());values.put("soulBonus",2*CareerSkills.growth(bonus));c.parameters=new SkillParameters.Snapshot(c.skill,c.parameters.rank(),values);}}
        c.equipment=ack.has("equipmentEffects")?JSON.fromJson(ack.get("equipmentEffects"),cn.yumis.fight.modules.equipment.api.EquipmentEffects.Snapshot.class):state().resources().equipment;c.stats=JSON.fromJson(ack.get("stats"),Stats.class);c.weapon=c.stats.weaponInstance;c.speed=Math.max(.01,c.stats.get(Stat.CAST_SPEED));c.origin=vector(ack.getAsJsonArray("origin"));c.center=vector(ack.getAsJsonArray("center"));c.direction=vector(ack.getAsJsonArray("direction"));c.start=c.stageStart=now;c.dimension=Minecraft.getInstance().level.dimension().identifier().toString();c.charging=SkillPresentation.charging(c.skill);
        c.release=c.charging&&wasReleased;sources.put(id,c);SkillController.INSTANCE.begin(c);
    }
    public Stats snapshot(Stats base){var p=Minecraft.getInstance().player;return p==null?base:CareerRules.current(base,state().progress(),view,state().predicted(state().own()),view.red(p.getX(),p.getY(),p.getZ(),state().serverNow()));}
    public boolean frenzy(){return view.frenzy&&state().progress().hasJob(JobCatalog.BERSERKER);}
    public boolean eye(){return view.eyeUntil>state().serverNow();}
    public int visualColor(Stats stats,int fallback){return stats.modifiers.getOrDefault("careerEye",0d)>0?CareerRules.EYE_COLOR:fallback;}
    public int basicColor(Stats stats){if(stats.modifiers.getOrDefault("careerEye",0d)>0)return CareerRules.EYE_COLOR;if(stats.modifiers.getOrDefault("careerFrenzy",0d)>0)return 0xe63951;return SkillLearning.effective(stats,state().progress(),SkillCatalog.require(CareerSkills.KEIGA))>0?0x8e56d9:0xb4f2ff;}
    public boolean invulnerable(SkillCast c){if(c==null)return false;if(c.skill.id.equals(CareerSkills.PHANTOM))return (ClientUi.now()-c.start)*c.speed<=250;return c.terminal&&(c.skill.id.equals(CareerSkills.EYE)||c.skill.id.equals(CareerSkills.SWORD));}
    public void cancel(SkillCast c){if(c==null)return;JsonObject j=new JsonObject();j.addProperty("id",c.id);state().send("careerCancel",j);sources.remove(c.id);}
    public void decorate(SkillCast source,LivingEntity target,JsonObject extra){
        if(source==null)return;
        if(CareerSkills.contains(source.skill.id)){extra.addProperty("careerCast",source.id);extra.addProperty("careerIndex",source.hitIndex);if(source.controlSeconds>0){String key=source.id+"/"+target.getUUID();long now=ClientUi.now();if(!source.skill.id.equals(CareerSkills.FIELD)||freezes.getOrDefault(key,0L)<=now){extra.addProperty("careerControl",source.controlSeconds);freezes.put(key,now+3000);}}}
        else extra.addProperty("sourceCast",source.id);
    }
    public void orb(SkillCast c){Orb orb=new Orb();orb.source=c;orb.position=c.origin.add(0,.8,0);orb.remaining=c.parameters.get("range",10);orb.next=ClientUi.now();orbs.add(orb);}
    public void wave(SkillCast c,SkillBehaviors.Context context){CareerWave wave=new CareerWave();wave.source=c;wave.floor=c.origin.y;wave.position=c.origin.add(0,c.skill.id.equals(CareerSkills.FIRE)?.35:1.2,0);wave.next=ClientUi.now();c.center=wave.position;waves.add(wave);context.visual(c,"RELEASE");}
    public void basic(Stats stats){
        long now=state().serverNow();if(view.kallaUntil<=now)return;
        SkillCast source=sources.values().stream().filter(c->c.skill.id.equals(CareerSkills.KALLA)&&!c.terminal).max(Comparator.comparingLong(c->c.start)).orElse(null);if(source==null)return;
        var p=Minecraft.getInstance().player;FlameBolt bolt=new FlameBolt();bolt.source=source;bolt.position=p.getEyePosition().add(0,-.3,0);bolt.direction=p.getLookAngle().normalize();bolt.next=ClientUi.now();bolts.add(bolt);
    }
    private void flame(LivingEntity target,SkillCast source){var mark=flames.computeIfAbsent(target.getUUID(),id->{var f=new Flame();f.target=target;f.next=ClientUi.now()+500;return f;});mark.source=source;mark.stacks=Math.min(3,mark.stacks+1);mark.expires=ClientUi.now()+3000;}
    public Vec3 firstTarget(SkillCast c,double length,double width){var p=Minecraft.getInstance().player;LivingEntity closest=null;double min=length+1;for(var e:SkillController.INSTANCE.targets(new AABB(c.origin,c.origin.add(c.direction.scale(length))).inflate(width/2,2,width/2))){Vec3 delta=e.position().subtract(c.origin);double forward=delta.dot(c.direction);if(forward>=0&&forward<min&&SkillController.INSTANCE.sight(c.origin.add(0,1,0),e.getBoundingBox().getCenter())){closest=e;min=forward;}}return closest==null?c.origin.add(c.direction.scale(length)):closest.position();}
    public void tick(SkillBehaviors.Context context){
        var mc=Minecraft.getInstance();var p=mc.player;if(p==null||mc.level==null)return;
        String currentDimension=mc.level.dimension().identifier().toString();
        if(!session.equals(state().session())||generation!=state().own().generation||!job.equals(state().progress().jobId())||!dimension.equals(currentDimension)||state().own().dead){clear();session=state().session();generation=state().own().generation;job=state().progress().jobId();dimension=currentDimension;if(!state().own().dead)state().send("careerState",new JsonObject());}
        long now=ClientUi.now(),wall=state().serverNow();
        cn.yumis.fight.modules.equipment.api.EquipmentStats.applyMovement(p,SkillController.INSTANCE.snapshot());
        pending.entrySet().removeIf(e->{if(now-e.getValue()>5000){pendingSlots.remove(e.getKey());released.remove(e.getKey());state().message("技能施放确认超时，请重试");return true;}return false;});
        if(view.field!=null&&view.field.expires>wall){SkillCast c=sources.get(view.field.id);if(c!=null){long next=fieldTicks.getOrDefault(c.id,c.start+1000);while(now>=next&&next<c.start+12001){int index=(int)((next-c.start)/1000)-1;c.center=new Vec3(view.field.x,view.field.y,view.field.z);c.dot=true;c.hit.clear();if(view.field.rank>=8){c.hitIndex=index;c.elementOverride=2;c.controlSeconds=.5;context.area(c,5,.25*CareerSkills.growth(c.parameters.rank()),false);}if(view.field.rank>=15){c.hit.clear();c.hitIndex=16+index;c.elementOverride=3;c.controlSeconds=0;context.area(c,5,.20*CareerSkills.growth(c.parameters.rank()),false);}next+=1000;}fieldTicks.put(c.id,next);}}
        for(var bolt:new ArrayList<>(bolts)){while(now>=bolt.next&&bolt.remaining>0){Vec3 from=bolt.position,to=from.add(bolt.direction.scale(.75));if(!SkillController.INSTANCE.sight(from,to)){bolt.remaining=0;break;}bolt.position=to;bolt.remaining-=.75;bolt.next+=50;var hit=SkillController.INSTANCE.targets(new AABB(from,to).inflate(.35)).stream().findFirst().orElse(null);if(hit!=null){flame(hit,bolt.source);bolt.remaining=0;}}if(bolt.remaining<=0)bolts.remove(bolt);}
        for(var mark:new ArrayList<>(flames.values())){if(!mark.target.isAlive()||mark.expires<now){flames.remove(mark.target.getUUID());continue;}while(mark.next<=now&&mark.next<=mark.expires){SkillCast c=mark.source;c.dot=true;c.elementOverride=3;c.controlSeconds=0;c.hitIndex=64+(int)((mark.next-c.start)/500);context.hit(c,mark.target,.10*mark.stacks*CareerSkills.growth(c.parameters.rank()));mark.next+=500;}}
        for(var orb:new ArrayList<>(orbs)){
            SkillCast c=orb.source;
            while(orb.target==null&&orb.remaining>0&&now>=orb.next){
                Vec3 from=orb.position,next=from.add(c.direction.scale(Math.min(.4,orb.remaining)));orb.next+=50;
                if(!SkillController.INSTANCE.sight(from,next)){orb.remaining=0;break;}
                orb.position=next;orb.remaining-=.4;
                var target=SkillController.INSTANCE.targets(new AABB(from,next).inflate(.8)).stream().filter(t->SkillController.INSTANCE.sight(from,t.getBoundingBox().getCenter())).min(Comparator.comparingDouble(t->from.distanceToSqr(t.getBoundingBox().getCenter()))).orElse(null);
                if(target!=null){var data=state().context(target);if(data!=null&&!data.life.dead){orb.target=target;orb.targetGeneration=data.life.generation;orb.next=now+Math.max(1,(long)(c.parameters.get("interval",.5)*1000));}}
            }
            if(orb.target!=null){
                var data=state().context(orb.target);
                if(!orb.target.isAlive()||data==null||data.life.dead||data.life.generation!=orb.targetGeneration){c.center=orb.position.add(0,-.8,0);context.visual(c,"FINISH");orbs.remove(orb);continue;}
                orb.position=orb.target.getBoundingBox().getCenter();
                int count=(int)c.parameters.get("slashes",CareerSkills.orbHits(1));long interval=Math.max(1,(long)(c.parameters.get("interval",.5)*1000));
                while(orb.index<count&&now>=orb.next){c.hitIndex=orb.index++;context.hit(c,orb.target,c.parameters.get("damage",0));orb.next+=interval;}
                if(orb.index>=count){c.center=orb.position.add(0,-.8,0);context.visual(c,"FINISH");orbs.remove(orb);continue;}
            }else if(orb.remaining<=0){c.center=orb.position.add(0,-.8,0);context.visual(c,"FINISH");orbs.remove(orb);continue;}
            c.center=orb.position.add(0,-.8,0);if(now-c.visualTrail>=100){context.visual(c,"TRAIL");c.visualTrail=now;}
        }
        for(var wave:new ArrayList<>(waves))advance(wave,context,now);
        if(!waiting()&&SkillController.INSTANCE.cast==null&&!p.hasEffect(cn.yumis.fight.modules.combat.api.CombatContentTypes.STUN)){
            if(view.eyeUntil>0&&view.eyeUntil<=wall)request(SkillCatalog.require(CareerSkills.EYE),-1);
            else if(view.bloodUntil>0&&view.bloodUntil<=wall)request(SkillCatalog.require(CareerSkills.SWORD),-1);
        }
        sources.values().removeIf(c->!(c.skill.id.equals(CareerSkills.SWORD)&&!c.terminal&&view.bloodUntil>0)&&now-c.start>Math.max(45000,(long)(c.parameters.get("duration",1)*1000)+5000));
    }
    private void advance(CareerWave wave,SkillBehaviors.Context context,long now){
        SkillCast c=wave.source;boolean fire=c.skill.id.equals(CareerSkills.FIRE);double range=c.parameters.get("range",fire?10:12),width=fire?5:2+2*c.moved,height=fire?2:width;
        while(waves.contains(wave)&&now>=wave.next){
            Vec3 from=wave.position,next=from.add(c.direction.scale(Math.min(.75,range-wave.travelled)));long step=wave.next;wave.next+=50;
            if(fire){
                var mc=Minecraft.getInstance();var floor=mc.level.clip(new ClipContext(next.add(0,1.2,0),next.add(0,-1.5,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,mc.player));
                if(floor.getType()==HitResult.Type.MISS||Math.abs(floor.getLocation().y-wave.floor)>1.05){finish(wave,context,false);break;}
                wave.floor=floor.getLocation().y;next=new Vec3(next.x,wave.floor+.35+.35*Math.sin((wave.travelled+.75)*Math.PI/2),next.z);
            }
            if(!SkillController.INSTANCE.sight(from.add(0,.2,0),next.add(0,.2,0))){finish(wave,context,false);break;}
            wave.position=next;wave.travelled=Math.min(range,wave.travelled+.75);c.center=next;
            for(var target:SkillController.INSTANCE.targets(new AABB(from,next).inflate(width/2,height/2,width/2))){
                int index=wave.hits.getOrDefault(target.getUUID(),0);
                if(index>=3||step<wave.nextHit.getOrDefault(target.getUUID(),0L)||!SkillController.INSTANCE.sight(from,target.getBoundingBox().getCenter()))continue;
                c.hitIndex=index;double coefficient=c.parameters.get("damage",0)*(fire?1:(1+2*c.moved)/3);
                if(context.hit(c,target,coefficient)){wave.hits.put(target.getUUID(),index+1);wave.nextHit.put(target.getUUID(),step+(fire?150:50));}
            }
            context.visual(c,"TRAIL");
            if(wave.travelled>=range){finish(wave,context,fire);break;}
        }
    }
    private void finish(CareerWave wave,SkillBehaviors.Context context,boolean explode){
        SkillCast c=wave.source;c.center=wave.position;
        if(explode){c.center=new Vec3(wave.position.x,wave.floor,wave.position.z);c.hitIndex=3;context.area(c,2.5,c.parameters.get("finisher",0),false);}
        context.visual(c,explode?"FINISH":"CANCEL");waves.remove(wave);
    }
    private static Vec3 vector(JsonArray a){return new Vec3(a.get(0).getAsDouble(),a.get(1).getAsDouble(),a.get(2).getAsDouble());}
    public void particles(java.util.List<cn.yumis.fight.modules.presentation.api.client.WorldQuads.Quad> out,long now){for(var bolt:bolts)CareerSkillVisuals.flame(out,bolt.position,.18,0x8f43d0,200);for(var mark:flames.values())if(mark.target.isAlive())for(int i=0;i<mark.stacks;i++)CareerSkillVisuals.flame(out,mark.target.position().add(Math.cos(i*2.1+now/300d)*.4,.5+i*.25,Math.sin(i*2.1+now/300d)*.4),.18,0x772daa,175);}
}
