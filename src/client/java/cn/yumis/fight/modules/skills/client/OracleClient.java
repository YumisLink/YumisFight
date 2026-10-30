package cn.yumis.fight.modules.skills.client;

import cn.yumis.fight.modules.skills.api.*;
import cn.yumis.fight.modules.skills.api.client.*;
import cn.yumis.fight.modules.presentation.api.client.ClientUi;
import cn.yumis.fight.modules.attributes.api.Stats;
import cn.yumis.fight.modules.equipment.api.*;
import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Oracle input and client-side hit geometry. Server-confirmed steps provide safe origins and conditional stats. */
public final class OracleClient {
    public static final OracleClient INSTANCE=new OracleClient();
    private static final Gson JSON=new Gson();
    private record Candidate(LivingEntity entity,long generation,long basis){}
    private static final class Stroke {
        SkillCast source;JsonObject pose;String predictedTarget="";Vec3 predictedOrigin,predictedDirection;AABB actorBox;float stepHeight;long retargetSent;boolean completed,resolved,replayHit,retargeting,grounded;final Map<UUID,Candidate> collisions=new HashMap<>();
    }
    private static final class Run {
        int next,active=-1;long sent,stageStart,deadline,finishSent;double moved;boolean target,completed,ended,finished;
        String session,instance,job;long generation;SkillCast cast;SkillBehaviors.Context context;
        final Map<Integer,JsonObject> replies=new HashMap<>();final Map<Integer,Stroke> strokes=new LinkedHashMap<>();
        final Map<Integer,Long> poseSent=new TreeMap<>();final Set<Integer> poseConfirmed=new HashSet<>();
    }
    private final Map<String,Run> runs=new HashMap<>();
    private OracleClient(){}
    public static void install(){
        SkillBehaviors.registerStart(OracleSkills.RIFT,(x,c,now)->{c.hitIndex=0;x.path(c,x.player().position(),x.player().position(),3,3,c.parameters.get("damage",0),.2);});
        for(String id:OracleSkills.ACTIONS)SkillBehaviors.register(id,(x,c,now,elapsed,stage,speed)->normal(x,c,now,elapsed));
        SkillBehaviors.registerStart(OracleSkills.REPLICA,INSTANCE::start);
        SkillBehaviors.register(OracleSkills.REPLICA,INSTANCE::replica);
    }
    public SkillDefinition resolve(SkillDefinition binding){
        if(!binding.bindingProxy)return binding;String id=CareerClient.INSTANCE.view.oracle.instruction;
        if(id.isEmpty()||!SkillCatalog.contains(id)){SkillsClient.current().message("当前没有待执行指令");return null;}return SkillCatalog.require(id);
    }
    public void prepareInput(SkillDefinition binding){
        var state=SkillsClient.current();var command=CareerClient.INSTANCE.view.oracle;String id=binding.bindingProxy?command.instruction:binding.id;int index=OracleSkills.ACTIONS.indexOf(id);
        if(index<0||state.progress().learned(id)<=0||!OracleRules.matches(command,SkillCatalog.require(id),state.serverNow()))return;
        var player=Minecraft.getInstance().player;
        for(int slot=0;slot<9;slot++){var gear=EquipmentItems.view(player.getInventory().getItem(slot));if(gear!=null&&gear.slot()==Slot.WEAPON&&gear.weapon().name().equals(OracleSkills.WEAPONS.get(index))&&gear.valid(state.progress(),EquipmentEligibility.allowance(player))){player.getInventory().setSelectedSlot(slot);return;}}
    }
    private void start(SkillBehaviors.Context context,SkillCast c,long now){
        var state=SkillsClient.current();var run=new Run();run.cast=c;run.context=context;run.session=state.session();run.generation=state.own().generation;run.instance=state.own().instance;run.job=state.progress().jobId();run.deadline=now+(long)(c.parameters.get("duration",OracleSkills.FULL_DURATION)*1000/c.speed)+5000;
        runs.put(c.id,run);TrainingCombat.hold(c.id,"oracle",run.deadline);
    }
    public void receive(JsonObject reply){
        String id=reply.get("id").getAsString();var run=runs.get(id);if(run==null||!valid(run))return;
        if(reply.has("cancelled")){if(SkillController.INSTANCE.cast==run.cast)SkillController.INSTANCE.cancel(false);else cancel(id);return;}
        if(reply.has("finished")){run.finished=true;if(reply.has("noFinisher")&&!run.ended&&SkillController.INSTANCE.cast==run.cast){run.ended=true;SkillController.INSTANCE.cancel(true);}disposeIfDone(run);return;}
        int step=reply.get("step").getAsInt();
        if(reply.has("retargetPending")||reply.has("retargetDenied")){var stroke=run.strokes.get(step);if(stroke!=null&&stroke.pose!=null&&stroke.pose.get("target").getAsString().equals(reply.get("retargetFrom").getAsString())){stroke.retargeting=false;if(reply.has("retargetDenied"))stroke.resolved=true;else{JsonObject context=new JsonObject();context.add("entity",stroke.pose.get("targetEntity"));SkillsClient.current().send("context",context);}}return;}
        if(reply.has("pose")){
            run.poseConfirmed.add(step);var stroke=run.strokes.get(step);
            if(stroke!=null&&reply.has("retarget")){if(!stroke.pose.get("target").getAsString().equals(reply.get("retargetFrom").getAsString()))return;stroke.retargeting=false;stroke.pose=reply;stroke.replayHit=false;stroke.source.stats=JSON.fromJson(reply.get("stats"),Stats.class);if(stroke.completed)repair(stroke);resolve(stroke);disposeIfDone(run);return;}
            if(stroke!=null&&stroke.pose==null){Vec3 approvedOrigin=vector(reply.getAsJsonArray("origin")),approvedDirection=vector(reply.getAsJsonArray("direction"));if(stroke.predictedOrigin.distanceToSqr(approvedOrigin)>1e-8||stroke.predictedDirection.distanceToSqr(approvedDirection)>1e-8)stroke.collisions.clear();stroke.pose=reply;stroke.source.stats=JSON.fromJson(reply.get("stats"),Stats.class);}
            var c=SkillController.INSTANCE.cast;
            if(c==run.cast&&run.active==step&&(stroke==null||!stroke.completed)){
                run.target=reply.get("hasTarget").getAsBoolean();c.stats=JSON.fromJson(reply.get("stats"),Stats.class);c.origin=vector(reply.getAsJsonArray("origin"));c.direction=vector(reply.getAsJsonArray("direction"));String previousWeapon=c.visualWeapon;c.visualWeapon=reply.get("weaponType").getAsString();
                var player=Minecraft.getInstance().player;
                if(stroke!=null){player.setPos(c.origin);player.move(MoverType.SELF,c.direction.scale(run.moved));player.setDeltaMovement(Vec3.ZERO);player.fallDistance=0;c.center=player.position();if(run.target)collect(stroke,c,c.origin,player.position());}
                else{player.setPos(c.origin.add(c.direction.scale(run.moved)));c.center=player.position();}xHeading(c);if(step==OracleSkills.FINISH_STEP&&!previousWeapon.equals(c.visualWeapon))run.context.visual(c,"SEGMENT");
            }
            else if(stroke!=null&&stroke.completed)repair(stroke);
            if(stroke!=null)resolve(stroke);disposeIfDone(run);return;
        }
        run.replies.put(step,reply);
    }
    private static void xHeading(SkillCast c){var p=Minecraft.getInstance().player;float yaw=(float)Math.toDegrees(Math.atan2(-c.direction.x,c.direction.z));p.setYRot(yaw);p.setYHeadRot(yaw);}
    public void clear(){for(var run:runs.values())TrainingCombat.release(run.cast.id,"oracle");runs.clear();}
    public void cancel(String id){var run=runs.remove(id);if(run!=null)TrainingCombat.release(id,"oracle");}
    private boolean valid(Run run){
        var state=SkillsClient.current();var mc=Minecraft.getInstance();return mc.player!=null&&mc.level!=null&&!state.own().dead&&run.session.equals(state.session())&&run.generation==state.own().generation&&run.instance.equals(state.own().instance)&&run.job.equals(state.progress().jobId())&&run.cast.dimension.equals(mc.level.dimension().identifier().toString());
    }
    public void tick(long now){
        for(var run:new ArrayList<>(runs.values())){
            if(!valid(run)||now>run.deadline){cancel(run.cast.id);continue;}
            for(var entry:new ArrayList<>(run.poseSent.entrySet()))if(!run.poseConfirmed.contains(entry.getKey())&&now-entry.getValue()>=250)pose(run,entry.getKey(),now);
            for(var stroke:run.strokes.values())resolve(stroke);
            if(run.ended&&!run.finished&&now-run.finishSent>=250)finish(run,now);
            disposeIfDone(run);
        }
    }
    private void disposeIfDone(Run run){if(run.ended&&run.finished&&run.strokes.values().stream().allMatch(s->s.resolved)){runs.remove(run.cast.id,run);TrainingCombat.release(run.cast.id,"oracle");}}
    private static void pose(Run run,int index,long now){JsonObject request=new JsonObject();request.addProperty("id",run.cast.id);request.addProperty("step",index);SkillsClient.current().send("oraclePose",request);run.poseSent.put(index,now);}
    private static void finish(Run run,long now){JsonObject request=new JsonObject();request.addProperty("id",run.cast.id);SkillsClient.current().send("oracleFinish",request);run.finishSent=now;}
    private static void normal(SkillBehaviors.Context x,SkillCast c,long now,long elapsed){
        String id=c.skill.id;double damage=c.parameters.get("damage",0);
        if(id.equals(OracleSkills.DOWN)&&elapsed>=300&&!c.finisher){c.finisher=true;c.hitIndex=0;x.player().swing(net.minecraft.world.InteractionHand.MAIN_HAND);x.slash(c,c.parameters.get("range",4),3,3,damage,.3,false);x.visual(c,"HIT");}
        if(id.equals(OracleSkills.SWEEP)&&elapsed>=500&&!c.finisher){c.finisher=true;c.hitIndex=0;c.sweepDegrees=145;x.slash(c,c.parameters.get("range",5),5,3,damage,.4,true);x.visual(c,"RELEASE");}
        if(id.equals(OracleSkills.THRUST)||id.equals(OracleSkills.RIFT)){
            double length=c.parameters.get("range",id.equals(OracleSkills.THRUST)?6:5),duration=id.equals(OracleSkills.THRUST)?250:350;double desired=length*Math.clamp(elapsed/duration,0,1);
            Vec3 from=x.player().position();move(x,c,desired-c.moved);c.moved=desired;c.hitIndex=0;x.path(c,from,x.player().position(),id.equals(OracleSkills.THRUST)?2.5:3,3,damage,.2);
            if(now-c.visualTrail>=80){x.visual(c,"TRAIL");c.visualTrail=now;}
        }
        if(id.equals(OracleSkills.SMILE))for(int i=c.slashes;i<2&&elapsed>=(i==0?100:300);i++){c.hit.clear();c.hitIndex=i;x.slash(c,c.parameters.get("range",4),3,3,damage*.5,.2,false);x.visual(c,"HIT");c.slashes++;}
        if(elapsed>=c.parameters.get("duration",.6)*1000)x.cancel(true);
    }
    private static SkillCast strikeSource(SkillCast c,int index){
        var source=new SkillCast();source.id=c.id;source.skill=c.skill;source.parameters=c.parameters;source.stats=c.stats;source.equipment=c.equipment;source.dimension=c.dimension;source.hitIndex=index*2;source.passiveDamage=c.passiveDamage;source.suppressEquipment=c.suppressEquipment;source.elementOverride=c.elementOverride;source.dot=c.dot;return source;
    }
    private static void repair(Stroke stroke){
        var pose=stroke.pose;if(pose==null||!pose.get("hasTarget").getAsBoolean())return;String target=pose.get("target").getAsString();UUID targetId=UUID.fromString(target);var previous=stroke.collisions.get(targetId);if(previous!=null&&previous.generation()>=0)return;
        var mc=Minecraft.getInstance();Vec3 origin=vector(pose.getAsJsonArray("origin")),direction=vector(pose.getAsJsonArray("direction"));
        if(previous==null&&stroke.predictedTarget.equals(target)&&stroke.predictedOrigin.distanceToSqr(origin)<=1e-8&&stroke.predictedDirection.distanceToSqr(direction)<=1e-8&&mc.level.getEntity(pose.get("targetEntity").getAsInt())!=null)return;
        var player=mc.player;Vec3 movement=direction.scale(8);AABB actor=stroke.actorBox.move(origin.subtract(stroke.predictedOrigin));
        Vec3 allowed=Entity.collideBoundingBox(player,movement,actor,mc.level,mc.level.getEntityCollisions(player,actor.expandTowards(movement)));
        if(stroke.grounded&&stroke.stepHeight>0&&allowed.horizontalDistanceSqr()<movement.horizontalDistanceSqr()){Vec3 stepping=movement.add(0,stroke.stepHeight,0);Vec3 climbed=Entity.collideBoundingBox(player,stepping,actor,mc.level,mc.level.getEntityCollisions(player,actor.expandTowards(stepping)));if(climbed.horizontalDistanceSqr()>allowed.horizontalDistanceSqr())allowed=climbed;}
        Vec3 start=origin.add(0,.8,0),end=origin.add(allowed).add(0,.8,0);JsonArray b=pose.getAsJsonArray("targetBox");
        AABB box=new AABB(b.get(0).getAsDouble(),b.get(1).getAsDouble(),b.get(2).getAsDouble(),b.get(3).getAsDouble(),b.get(4).getAsDouble(),b.get(5).getAsDouble());double width=3*stroke.source.parameters.get("meleeScale",1);
        stroke.replayHit=SkillGeometry.intersects(box.inflate(width/2,1.5,width/2),start,end)&&SkillController.INSTANCE.sight(start,box.getCenter());if(stroke.replayHit&&previous!=null)stroke.collisions.remove(targetId);
    }
    private static void collect(Stroke stroke,SkillCast c,Vec3 from,Vec3 to){
        if(stroke.resolved)return;double width=3*c.parameters.get("meleeScale",1),height=3;Vec3 start=from.add(0,.8,0),end=to.add(0,.8,0);var controller=SkillController.INSTANCE;
        for(var target:controller.targets(new AABB(start,end).inflate(width/2,height/2,width/2))){
            var previous=stroke.collisions.get(target.getUUID());if(previous!=null&&previous.generation()>=0||!SkillGeometry.intersects(target.getBoundingBox().inflate(width/2,height/2,width/2),start,end)||!controller.sight(start,target.getBoundingBox().getCenter()))continue;
            var state=SkillsClient.current().context(target);if(state!=null)stroke.collisions.put(target.getUUID(),new Candidate(target,state.life.generation,state.life.basis));else if(previous==null)stroke.collisions.put(target.getUUID(),new Candidate(target,-1,-1));
        }
        resolve(stroke);
    }
    private static void resolve(Stroke stroke){
        if(stroke.resolved||stroke.pose==null)return;if(stroke.retargeting){retarget(stroke);return;}String targetId=stroke.pose.get("target").getAsString();if(targetId.isEmpty()){stroke.resolved=true;return;}
        UUID id=UUID.fromString(targetId);var collision=stroke.collisions.get(id);
        if(collision==null&&stroke.replayHit){var mc=Minecraft.getInstance();var entity=mc.level==null?null:mc.level.getEntity(stroke.pose.get("targetEntity").getAsInt());if(!(entity instanceof LivingEntity target)||!target.getUUID().equals(id)){retarget(stroke);return;}collision=new Candidate(target,stroke.pose.get("targetGeneration").getAsLong(),stroke.pose.get("targetBasis").getAsLong());stroke.collisions.put(id,collision);}
        if(collision==null){
            if(stroke.completed){var mc=Minecraft.getInstance();var entity=mc.level==null?null:mc.level.getEntity(stroke.pose.get("targetEntity").getAsInt());
                if(!(entity instanceof LivingEntity target)||!target.getUUID().equals(id)||!target.isAlive()){retarget(stroke);return;}
                var known=SkillsClient.current().context(target);if(known==null)return;
                if(known.life.dead||SkillsClient.current().predicted(known.life)<=0||known.life.generation!=stroke.pose.get("targetGeneration").getAsLong()||known.life.basis!=stroke.pose.get("targetBasis").getAsLong()){retarget(stroke);return;}stroke.resolved=true;
            }return;
        }
        var target=collision.entity();var mc=Minecraft.getInstance();
        if(collision.generation()<0){if(stroke.completed)stroke.resolved=true;return;}
        if(mc.level==null)return;if(!target.isAlive()||mc.level.getEntity(target.getId())!=target||target.getId()!=stroke.pose.get("targetEntity").getAsInt()){retarget(stroke);return;}
        var state=SkillsClient.current().context(target);if(state==null)return;long generation=stroke.pose.get("targetGeneration").getAsLong(),basis=stroke.pose.get("targetBasis").getAsLong();
        if(collision.generation()>=0&&(collision.generation()!=generation||collision.basis()!=basis)){stroke.resolved=true;return;}
        if(state.life.dead||SkillsClient.current().predicted(state.life)<=0||state.life.generation!=generation||state.life.basis!=basis){retarget(stroke);return;}
        SkillController.INSTANCE.hitCast(stroke.source,target,stroke.source.parameters.get("damage",0)*.05,.4);stroke.resolved=true;
    }
    private static void retarget(Stroke stroke){
        if(!stroke.completed)return;long now=ClientUi.now();if(now-stroke.retargetSent<250)return;
        JsonObject request=new JsonObject();request.addProperty("id",stroke.source.id);request.addProperty("step",stroke.source.hitIndex/2);request.addProperty("retarget",true);request.addProperty("previousTarget",stroke.pose.get("target").getAsString());JsonArray candidates=new JsonArray();stroke.collisions.entrySet().stream().filter(entry->entry.getValue().generation()>=0).limit(128).forEach(entry->{JsonObject candidate=new JsonObject();candidate.addProperty("target",entry.getKey().toString());candidate.addProperty("generation",entry.getValue().generation());candidate.addProperty("basis",entry.getValue().basis());candidates.add(candidate);});request.add("candidates",candidates);SkillsClient.current().send("oraclePose",request);stroke.retargetSent=now;stroke.retargeting=true;
    }
    private void replica(SkillBehaviors.Context x,SkillCast c,long now,long elapsed,long stage,double speed){
        Run run=runs.get(c.id);if(run==null){x.cancel(false);return;}
        if(now>run.deadline){cancel(c.id);x.cancel(false);return;}
        int stacks=(int)c.parameters.get("replicaStacks",1),count=stacks==9?OracleSkills.FULL_STEPS:stacks;
        for(int transitions=0;transitions<=count;transitions++){
            if(run.active<0||run.completed){
                if(run.next>=count){run.ended=true;finish(run,now);x.cancel(true);disposeIfDone(run);return;}
                var ack=run.replies.remove(run.next);if(ack==null){if(run.sent==0||now-run.sent>=100)request(run,c,now);return;}
                long previousEnd=run.active<0?now:run.stageStart+(long)(OracleSkills.stepDuration(run.active)*1000/speed);int next=run.next++;
                run.active=next;run.sent=0;run.moved=0;run.completed=false;run.stageStart=next>0?previousEnd:now;run.target=ack.get("hasTarget").getAsBoolean();c.stats=JSON.fromJson(ack.get("stats"),Stats.class);c.direction=vector(ack.getAsJsonArray("direction"));c.origin=vector(ack.getAsJsonArray("origin"));c.center=c.origin;c.visualWeapon=ack.get("weaponType").getAsString();c.hit.clear();c.slashes=0;c.finisher=false;c.release=false;c.sweepDegrees=0;c.charging=next==OracleSkills.FINISH_STEP;
                if(OracleSkills.combo(next)){var stroke=new Stroke();stroke.source=strikeSource(c,next);stroke.predictedTarget=ack.get("target").getAsString();stroke.predictedOrigin=c.origin;stroke.predictedDirection=c.direction;stroke.actorBox=x.player().getBoundingBox().move(c.origin.subtract(x.player().position()));stroke.stepHeight=x.player().maxUpStep();stroke.grounded=x.player().onGround();run.strokes.put(next,stroke);}
                pose(run,next,now);x.player().setPos(c.origin);xHeading(c);x.player().swing(net.minecraft.world.InteractionHand.MAIN_HAND);x.visual(c,"SEGMENT");
                if(next==0||next==4||next==7)for(var candidate:SkillController.INSTANCE.targets(x.player().getBoundingBox().inflate(16,4,16)))SkillsClient.current().context(candidate);
            }
            int index=run.active;
            if(run.next<count&&!run.replies.containsKey(run.next)&&(now-run.stageStart)*speed>=(OracleSkills.combo(index)?0:100)&&(run.sent==0||now-run.sent>=100))request(run,c,now);
            double local=Math.min(Math.max(0,(now-run.stageStart)*speed),OracleSkills.stepDuration(index)*1000),damage=c.parameters.get("damage",0);
            if(OracleSkills.combo(index)){
                var stroke=run.strokes.get(index);
                if(run.target){double desired=8*Math.clamp(local/(OracleSkills.COMBO_INTERVAL*1000),0,1);move(x,c,desired-run.moved);run.moved=desired;collect(stroke,c,c.origin,x.player().position());}
                if(!c.finisher){c.finisher=true;x.visual(c,"HIT");}
            }else if(index==OracleSkills.FINISH_STEP){
                if(local>=600&&run.poseConfirmed.contains(index)){
                    if(run.target){double desired=8*Math.clamp((local-600)/200,0,1);move(x,c,desired-run.moved);run.moved=desired;}
                    c.hitIndex=index*2;x.path(c,c.origin,run.target?x.player().position():c.origin.add(c.direction.scale(8)),3,3,damage*.35,.4);
                    if(!c.finisher){c.finisher=true;c.charging=false;x.visual(c,"FINISH");}
                }
            }else{
                int action=OracleSkills.action(c.id.hashCode(),index);double part=damage*.0625;
                if(run.target){double desired=5*Math.clamp(local/250,0,1);move(x,c,desired-run.moved);run.moved=desired;}
                Vec3 end=run.target?x.player().position():c.origin.add(c.direction.scale(5));
                c.hitIndex=index*2+c.slashes;x.path(c,c.origin,end,action==1?2.5:3,3,action==3?part*.5:part,.2);
                if(action==3&&local>=175&&c.slashes==0){c.slashes=1;c.hit.clear();c.hitIndex=index*2+1;x.path(c,c.origin,end,3,3,part*.5,.2);x.visual(c,"HIT");}
                if(!c.finisher&&local>=100){c.finisher=true;x.visual(c,action==2?"RELEASE":"HIT");}
            }
            if(now-c.visualTrail>=80){x.visual(c,"TRAIL");c.visualTrail=now;}
            if((now-run.stageStart)*speed<OracleSkills.stepDuration(index)*1000||index==OracleSkills.FINISH_STEP&&!run.poseConfirmed.contains(index))return;
            run.completed=true;var stroke=run.strokes.get(index);if(stroke!=null){stroke.completed=true;repair(stroke);resolve(stroke);}
        }
    }
    private static void request(Run run,SkillCast c,long now){JsonObject request=new JsonObject();request.addProperty("id",c.id);request.addProperty("step",run.next);SkillsClient.current().send("oracleStep",request);run.sent=now;}
    private static void move(SkillBehaviors.Context x,SkillCast c,double amount){if(amount<=0)return;var player=x.player();player.move(MoverType.SELF,c.direction.scale(amount));player.setDeltaMovement(Vec3.ZERO);player.fallDistance=0;c.center=player.position();}
    private static Vec3 vector(JsonArray array){return new Vec3(array.get(0).getAsDouble(),array.get(1).getAsDouble(),array.get(2).getAsDouble());}
}
